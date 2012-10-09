package to.etc.jenkins;

import hudson.*;
import hudson.model.*;

import java.io.*;
import java.net.*;
import java.util.*;

import to.etc.jenkins.EclipsePrjBuilder.*;
import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.maker.*;
import to.etc.prjbuilder.scm.*;
import to.etc.prjbuilder.scm.bzr.*;
import to.etc.util.*;

/**
 * Externally callable eclipse builder - suitable for running in a separate process.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Mar 5, 2012
 */
public class EclipseBuildCmd {
	private File m_workspace;

	private String project;

	private boolean cleanBuild;

	private File m_configFile;

	private BuilderConfiguration m_config;

	private int m_exitLevel = 20;

	public static void main(String[] args) {
		try {
			new EclipseBuildCmd().run(args);
		} catch(Exception x) {
			x.printStackTrace();
			System.exit(10);
		}
	}

	private void run(String[] args) throws Exception {


		System.exit(m_exitLevel);
	}


	/**
	 * Config: the workspace relative path.
	 * @return
	 */
	public File getWorkspace() {
		return m_workspace;
	}

	public String getProject() {
		return project;
	}

	public boolean isCleanBuild() {
		return cleanBuild;
	}

	private URL getEcjJar() {
		List<URL> all = new ArrayList<URL>();
		findURLs(all, getClass().getClassLoader());

		for(URL u : all) {
			if(u.toString().endsWith("ecj-3.7.jar")) {
				return u;
			}
		}
		return null;
	}

	/**
	 * Loads the appropriate compiler class.
	 * @return
	 * @throws Exception
	 */
	static public Class< ? > loadCompiler(URL jar, String className) {
		//-- Load the driver off the classloader.
		Class< ? > cl;
		try {
			URLClassLoader loader = new NoLoader(new URL[]{jar.toURI().toURL()}); // Deep, deep sigh.
			cl = loader.loadClass(className);
			return cl;
		} catch(Exception x) {
			throw new RuntimeException("The compiler class '" + className + "' could not be loaded from " + jar + ": " + x);
		}
	}

	private void findURLs(List<URL> all, ClassLoader classLoader) {
		if(classLoader instanceof URLClassLoader) {
			URL[] ar = ((URLClassLoader) classLoader).getURLs();
			for(URL u : ar)
				all.add(u);
		}

		if(classLoader.getParent() != null && classLoader.getParent() != classLoader)
			findURLs(all, classLoader.getParent());
	}

	private boolean perform() {
		Class< ? > compiler = null;
		URL ecjJar = getEcjJar();
		if(null != ecjJar) {
			compiler = loadCompiler(ecjJar, "org.eclipse.jdt.core.compiler.batch.BatchCompiler");
			JavaCompiler.setCompiler(new EcjJarCompiler(compiler)); // Force builder to use the version from this jar, not Tomcat's thingy.
		}

		try {
			File bdir = getWorkspace();
			if(!bdir.exists() || !bdir.isDirectory()) {
				fatalError("The workspace directory '" + m_workspace + "' does not exist (path=" + bdir + ")");
				return false;
			}

			ScmBranch br = BzrHandler.getInstance().getBranch(bdir.getAbsolutePath());
			BranchBuildHelper bbh = new BranchBuildHelper(getConfig());
			JenkinsReporter jr = new JenkinsReporter(listener);

			bbh.initialize(jr, br, this.project, "Jenkins build");
			bbh.setArtefactMode(ArtefactMode.NONE);
			bbh.setBuildIntent(BuildIntentType.NORMAL);
			bbh.setSourceRoot(bdir);
			bbh.setOutputRoot(bdir); // Normal Eclipse builds: generate BIN dirs.
			bbh.setOutputWriter(new OutputStreamWriter(listener.getLogger(), "utf-8"));

			Progress p = new Progress("FullBuild");
			bbh.fullCompile(this.cleanBuild ? BuildMode.CLEAN : BuildMode.NORMAL, p);

			if(jr.getErrCount() > 0) {
				m_exitLevel = 2; // Exitlevel 2 = compile errors.
				return false;
			}

			//-- Start running JUnit tests in module build order.
			OrderedBuildList obl = bbh.getOrderedBuildList();
			JUnitTestRunner jtr = new JUnitTestRunner(listener, bdir, obl);
			jtr.runTests();
			int utc = jtr.getFailedTestCount();
			if(utc > 0) {
				m_exitLevel = 1; // Exitlevel 1 = unstable
				System.out.println("Return UNSTABLE: " + utc);
			} else {
				m_exitLevel = 0; // 0 = success
			}
			return true;
		} catch(Exception x) {
			PrintWriter pw = listener.fatalError("Build failed: " + x);
			x.printStackTrace();
			x.printStackTrace(pw);
			m_exitLevel = 10;
			return false;
		}
	}


	private void loadConfiguration() throws Exception {
		if(m_configFile == null) {
			File home = new File(System.getProperty("user.home"));
			m_configFile = new File(home, ".prjbuilder.properties");
		}
		BuilderConfiguration bc = new BuilderConfiguration();
		bc.loadConfiguration(m_configFile);

		if(!m_configFile.exists())
			throw new IllegalArgumentException("Config file " + m_configFile + " cannot be opened");
		m_config = bc;
	}

	private BuilderConfiguration getConfig() throws Exception {
		if(m_config == null) {
			loadConfiguration();
		}
		return m_config;
	}

}
