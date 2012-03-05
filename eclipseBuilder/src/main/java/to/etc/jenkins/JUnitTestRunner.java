package to.etc.jenkins;


import hudson.model.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

import org.junit.runner.*;
import org.junit.runner.Result;
import org.junit.runner.notification.*;
import org.junit.runner.notification.Failure;
import org.objectweb.asm.*;

import to.etc.jenkins.JUnitListener.ATest;
import to.etc.prjbuilder.builder.*;
import to.etc.util.*;
import to.etc.xml.*;

/**
 * This very shoddily and quickly written class locates all JUnit test classes in all
 * projects and executes them.
 *
 * It walks all projects from low to high and executes Junit tests for each of them.
 * It locates JUnit test classes by using an asm ClassVisitor on all generated artifacts,
 * and check if the class has methods annotated with org.junit.Test. In that case that
 * class is a JUnit test class to execute.
 *
 * The classes are executed per-project. After collecting all test classes for a project
 * the code creates a specific ClassLoader for just that project and it's dependencies
 * as used by the module compiler. The ClassLoader created will not have a parent; all
 * classes loaded from it come from the output of the build. The only exception is JUnit
 * itself: it will try to remove any JUnit library from the build jars and substitute the
 * instance loaded by Jenkins itself.
 *
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Mar 3, 2012
 */
public class JUnitTestRunner {
	private final BuildListener m_listener;

	private final File m_workRoot;

	private final OrderedBuildList m_buildList;

	private JUnitCore m_core;

	private TestCaseClassVisitor m_scanner;

	private int m_failedTestCount;

	final private JUnitListener m_jtl = new JUnitListener();

	public JUnitTestRunner(BuildListener listener, File workRoot, OrderedBuildList buildList) {
		m_listener = listener;
		m_workRoot = workRoot;
		m_buildList = buildList;
	}

	public void runTests() throws Exception {
		m_core = new JUnitCore();
		m_core.addListener(new RunListener() {
			boolean m_failed;

			@Override
			public void testStarted(Description description) throws Exception {
				test("====== Test: " + description.getDisplayName() + " =====");
				m_failed = false;
			}

			@Override
			public void testFailure(Failure failure) throws Exception {
				test("Failed: " + failure.getMessage());
				test(filterTrace(failure.getException()));
				m_failed = true;
				m_failedTestCount++;
			}

			@Override
			public void testFinished(Description description) throws Exception {
				if(!m_failed)
					test("Test succesful");
			}
		});
		m_core.addListener(m_jtl);

		for(ModuleBuildInfo mbi : m_buildList) {
			checkTests(mbi);
		}

	}

	/**
	 * Remove everything below JUnit test run.
	 * @param t
	 * @return
	 */
	private String filterTrace(Throwable t) {
		StringBuilder sb = new StringBuilder();
		sb.append(t.toString()).append("\n");
		filterTrace(sb, t);

		for(;;) {
			Throwable xt = t.getCause();
			if(xt == t || xt == null)
				break;
			t = xt;

			sb.append("Caused by: ").append(t.toString()).append("\n");
			filterTrace(sb, t);
		}

		return sb.toString();
	}


	private void filterTrace(StringBuilder sb, Throwable t) {
		int eix = -1;
		StackTraceElement[] ar = t.getStackTrace();
		for(StackTraceElement se : ar) {
			String s = se.getClassName();
			if(s.startsWith("org.junit.runners")) {
				break;
			}
			eix++;
		}

		//-- Move back over all "reflect" thingies
		while(eix >= 0) {
			String s = ar[eix].getClassName();
			if(s.startsWith("sun.reflect") || s.startsWith("java.lang.reflect")) {
				eix--;
			} else
				break;
		}
		if(eix <= 0)
			eix = ar.length - 1;

		for(int i = 0; i <= eix; i++) {
			sb.append("        at " + ar[i].toString()).append("\n");
		}
	}

	private void log(String s) throws Exception {
//		System.out.println("testrunner: " + s);
		m_listener.getLogger().println("testrunner: " + s);
	}

	private void test(String s) throws Exception {
//		System.out.println("testrunner: " + s);
		m_listener.getLogger().println(s);
	}

	private void checkTests(ModuleBuildInfo mbi) throws Exception {
		List<File> outpaths = mbi.getMaker().getModuleOutputPaths(true);
		if(outpaths.size() == 0)
			return;

		//-- Locate all @Test annotated things.
		Set<String> res = new HashSet<String>();
		for(File of : outpaths) {
//			System.out.println("test: check module " + mbi.getName() + " path " + of);
			getTestClasses(res, of, "");
		}
		if(res.size() == 0)
			return;

		log("Discovered " + res + " test classes in module " + mbi.getName());

		//-- Get a classloader for the module.
		URLClassLoader ucl = createModuleClassLoader(mbi);

		List<Class< ? >> testl = new ArrayList<Class< ? >>();
		for(String cname : res) {
			try {
				Class< ? > clz = ucl.loadClass(cname);
				testl.add(clz);
			} catch(Exception x) {
				m_listener.error("testrunner: cannot load " + cname + ": " + x);
//				System.out.println("testrunner: cannot load " + cname + ": " + x);
				m_listener.getLogger().println("testrunner: cannot load " + cname + ": " + x);
			}
		}

		if(testl.size() == 0) {
			log("All classes of " + mbi.getName() + " failed to load.");
			return;
		}

		m_jtl.reset();
		Result r = m_core.run(testl.toArray(new Class< ? >[testl.size()]));
		writeXml(mbi, r);


//		System.out.println("testrunner: result = " + r.getFailureCount() + " failed, " + r.getRunCount() + " run, " + r.getIgnoreCount() + " ignored in "
//			+ StringTool.strDurationMillis(r.getRunTime()));
//		for(org.junit.runner.notification.Failure f : r.getFailures()) {
//			System.out.println("testrunner: " + f.getTestHeader() + "\n" + f.getMessage() + "\n" + f.getDescription() + "\n" + f.getTrace());
//		}

	}

	private boolean isJunit(File f) {
		return f.getName().startsWith("junit-");
	}

	/**
	 * Create a classloader for the target project. Remove any junit instances from there and use this classloader
	 * as a base.
	 * @param mbi
	 * @return
	 * @throws Exception
	 */
	private URLClassLoader createModuleClassLoader(ModuleBuildInfo mbi) throws Exception {
		List<URL> urls = new ArrayList<URL>();
		for(File f : mbi.getMaker().getModuleOutputPaths(false)) {
			if(isJunit(f))
				continue;
			URL u = f.toURI().toURL();
			urls.add(u);
		}

		for(File f : mbi.getMaker().getModuleClasspath()) {
			if(isJunit(f))
				continue;
			URL u = f.toURI().toURL();
			urls.add(u);
		}

		//-- Locate junit module in here
		URL[] myulrs = ClassUtil.findUrlsFor(getClass().getClassLoader());
		for(URL u: myulrs) {
			if(u.getPath().contains("junit-"))
				urls.add(u);
		}

//		for(URL s : urls)
//			System.out.println("Loader: " + s);

		final ClassLoader	dad = getClass().getClassLoader();
		URLClassLoader ucl = new URLClassLoader(urls.toArray(new URL[urls.size()])) {
			@Override
			public java.lang.Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if(name.startsWith("org.junit")) {
					return dad.loadClass(name);
				}
				return super.loadClass(name, resolve);
			}
		};
		return ucl;
	}


	private void getTestClasses(Set<String> result, File root, String relpath) {
		if(root.isDirectory()) {
			File[] far = root.listFiles();
			if(far == null)
				return;
			for(File f : far) {
				String path = relpath.length() == 0 ? f.getName() : relpath + "." + f.getName();
				if(f.isFile()) {
					String name = hasTestAnnotations(f);
					if(null != name) {
						//-- Has annotations.
						result.add(name);
					}
				} else {
					getTestClasses(result, f, path);
				}
			}
		}
	}

	private String hasTestAnnotations(File f) {
		if(m_scanner == null)
			m_scanner = new TestCaseClassVisitor();

		InputStream is = null;
		try {
			is = new FileInputStream(f);
			ClassReader r = new ClassReader(is);
			m_scanner.reset();
			r.accept(m_scanner, true);
		} catch(Exception x) {
		} finally {
			try {
				if(is != null)
					is.close();
			} catch(Exception x) {}
		}
		return m_scanner.isFound() ? m_scanner.getName() : null;
	}

	public int getFailedTestCount() {
		return m_failedTestCount;
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	JUnit test output xml generation					*/
	/*--------------------------------------------------------------*/

	static private final Pattern CLASSP = Pattern.compile("(.*)\\((.*)\\)");

	/**
	 *
	 * @param mbi
	 * @param r
	 */
	private void writeXml(ModuleBuildInfo mbi, Result r) throws Exception {
		File resfile = new File(mbi.getOutputDir(), "SUITE-testResult.xml");

		Writer xw = null;

		try {
			xw = new OutputStreamWriter(new FileOutputStream(resfile), "utf-8");
			XmlWriter w = new XmlWriter(xw);
			xw = w;

			w.append("<?xml version='1.0' encoding='utf-8'?>\n");
			w.tag("testsuites");

			double runtime = r.getRunTime() / 1000.0;


			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			w.tag("testsuite", "name", mbi.getName(), "tests", Integer.toString(m_jtl.getTestCount()), "failures", Integer.toString(m_jtl.getFailedCount()), "errors", "0",
				"time", String.format("%.3f", runtime), "timestamp", df.format(m_jtl.getSuiteStart()));
			w.append("\n");

			for(ATest at : m_jtl.getTestList()) {
				double dur = at.getDuration() / 1000.0;

				//-- Sign. Why on earth everone insists on storing stuff in presentation format instead of native format is a mystery to me 8-( Description contains the
				//-- required data as "method(classname)" - so we need to get that out now.
				Matcher m = CLASSP.matcher(at.getDescription().getDisplayName());
				String cn, mn;

				if(!m.matches()) {
					cn = at.getDescription().getDisplayName();
					mn = "unknown";
				} else {
					cn = m.group(2);
					mn = m.group(1);
				}

				w.tag("testcase", "classname", cn, "name", mn, "time", String.format("%.3f", dur));

				if(at.getFailure() != null) {
					w.tag("error", "message", at.getFailure().getMessage(), "type", at.getFailure().getException().getClass().getName());
					w.cdata(at.getFailure().getTrace());
					w.tagendnl();
				}

				w.tagendnl();
			}

			w.tagendnl(); // testsuite
			w.tagendnl(); // testsuites

			xw.close();
			xw = null;
		} finally {
			try {
				if(null != xw)
					xw.close();
			} catch(Exception x) {}
		}
	}
}
