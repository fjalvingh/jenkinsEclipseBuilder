package to.etc.jenkins;

import hudson.model.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import org.junit.runner.*;
import org.junit.runner.Result;
import org.objectweb.asm.*;

import to.etc.prjbuilder.builder.*;
import to.etc.util.*;

public class JUnitTestRunnerNonNative {
	private final BuildListener m_listener;

	private final File m_workRoot;

	private final OrderedBuildList m_buildList;

	private JUnitCore m_core;

	private TestCaseClassVisitor m_scanner;

	public JUnitTestRunnerNonNative(BuildListener listener, File workRoot, OrderedBuildList buildList) {
		m_listener = listener;
		m_workRoot = workRoot;
		m_buildList = buildList;
	}

	public void runTests() throws Exception {
		m_core = new JUnitCore();
		for(ModuleBuildInfo mbi : m_buildList) {
			checkTests(mbi);
		}

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

		m_listener.getLogger().println("testrunner: Discovered " + res + " test classes in module " + mbi.getName());
		System.out.println("Testing " + mbi.getName());

		//-- Get a classloader for the module.
		URLClassLoader ucl = createModuleClassLoader(mbi);

		List<Class< ? >> testl = new ArrayList<Class< ? >>();
		for(String cname : res) {
			try {
				Class< ? > clz = ucl.loadClass(cname);
				testl.add(clz);
			} catch(Exception x) {
				System.out.println("testrunner: cannot load " + cname + ": " + x);
				m_listener.getLogger().println("testrunner: cannot load " + cname + ": " + x);
			}
		}
		Object core = ClassUtil.loadInstance(ucl, "org.junit.runner.JUnitCore");

		if(testl.size() == 0) {
			System.out.println("testrunner: all classes failed to load.");
			m_listener.getLogger().println("testrunner: all classes failed to load.");
			return;
		}

		Method m = ClassUtil.findMethod(core.getClass(), "run", new Class< ? >[]{Class[].class});
		if(null == m)
			throw new IllegalStateException("Cannot find run method");
		System.out.println("found " + m);
		Class[] ar = testl.toArray(new Class< ? >[testl.size()]);

		Object r = m.invoke(core, (Object) ar);
		if(r instanceof Result) {
			System.out.println("Good res");
		}

		System.out.println("Rsult = " + r);


//		Result r = m_core.run(testl.toArray(new Class< ? >[testl.size()]));
//		System.out.println("testrunner: result = " + r.getFailureCount() + " failed, " + r.getRunCount() + " run, " + r.getIgnoreCount() + " ignored in "
//			+ StringTool.strDurationMillis(r.getRunTime()));
//		for(org.junit.runner.notification.Failure f : r.getFailures()) {
//			System.out.println("testrunner: " + f.getTestHeader() + "\n" + f.getMessage() + "\n" + f.getDescription() + "\n" + f.getTrace());
//		}

		throw new RuntimeException("bad");

	}

	private URLClassLoader createModuleClassLoader(ModuleBuildInfo mbi) throws Exception {
		List<URL> urls = new ArrayList<URL>();
		for(File f : mbi.getMaker().getModuleOutputPaths(false)) {
			URL u = f.toURI().toURL();
			urls.add(u);
		}

		for(File f : mbi.getMaker().getModuleClasspath()) {
			URL u = f.toURI().toURL();
			urls.add(u);
		}
		for(URL s : urls)
			System.out.println("Loader: " + s);

		URLClassLoader ucl = new URLClassLoader(urls.toArray(new URL[urls.size()]));
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


}
