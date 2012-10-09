package to.etc.jenkins;

import javax.servlet.*;
import java.io.*;
import java.net.*;
import java.util.*;

import hudson.*;
import hudson.util.*;
import hudson.model.*;
import hudson.tasks.*;
import hudson.tasks.junit.*;
import hudson.tasks.junit.Messages;
import hudson.tasks.junit.TestResultAction.Data;
import net.sf.json.*;
import org.kohsuke.stapler.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.maker.*;
import to.etc.prjbuilder.scm.*;
import to.etc.prjbuilder.scm.bzr.*;
import to.etc.util.*;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link EclipsePrjBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class EclipsePrjBuilder extends Builder {
	private String workspace;

	private String project;

	private boolean cleanBuild;

	private File m_configFile;

	private BuilderConfiguration m_config;

	private DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers;

	private String systemProperties;

	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
	@DataBoundConstructor
	public EclipsePrjBuilder(String workspace, String project, boolean cleanBuild, String systemProperties) {
		this.workspace = workspace;
		this.project = project;
		this.cleanBuild = cleanBuild;
		this.systemProperties = systemProperties;
	}

	/**
	 * Config: the workspace relative path.
	 * @return
	 */
	public String getWorkspace() {
		return workspace;
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

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		Class< ? > compiler = null;
		URL ecjJar = getEcjJar();
		if(null != ecjJar) {
			compiler = loadCompiler(ecjJar, "org.eclipse.jdt.core.compiler.batch.BatchCompiler");
			JavaCompiler.setCompiler(new EcjJarCompiler(compiler)); // Force builder to use the version from this jar, not Tomcat's thingy.
		}

		try {
			File root = new File(build.getModuleRoot().toString());
			System.out.println("local = " + root);
			File bdir = new File(root, this.workspace);
			if(!bdir.exists() || !bdir.isDirectory()) {
				listener.fatalError("The workspace directory '" + workspace + "' does not exist (path=" + bdir + ")");
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

			System.out.println("Build type clean=" + this.cleanBuild);
			Progress p = new Progress("FullBuild");
			bbh.fullCompile(this.cleanBuild ? BuildMode.CLEAN : BuildMode.NORMAL, p);

			if(jr.getErrCount() > 0) {
				listener.finished(Result.FAILURE);
				build.setResult(Result.FAILURE);
				return false;
			}

			//-- Start running JUnit tests in module build order.
			OrderedBuildList obl = bbh.getOrderedBuildList();
			JUnitTestRunner jtr = new JUnitTestRunner(listener, bdir, obl);
			jtr.runTests();
			Result r = Result.SUCCESS;
			int utc = jtr.getFailedTestCount();
			if(utc > 0) {
				r = Result.UNSTABLE;
				listener.error(utc + " junit tests have failed");
				System.out.println("Return UNSTABLE: " + utc);
			}
			listener.finished(r);
			build.setResult(r);
			return true;
		} catch(Exception x) {
			PrintWriter pw = listener.fatalError("Build failed: " + x);
			x.printStackTrace();
			x.printStackTrace(pw);
			listener.finished(Result.FAILURE);
			build.setResult(Result.FAILURE);
			return false;
		}
	}

	private static final CheckPoint CHECKPOINT = new CheckPoint("JUnit result archiving");

	/**
	 * In progress. Working on delegating the actual parsing to the JUnitParser.
	 */
	protected TestResult parse(String expandedTestResults, AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		return new JUnitParser(true).parse(expandedTestResults, build, launcher, listener);
	}

	private void handleJunitTest(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
		final String testResults = build.getEnvironment(listener).expand("SUITE-testResult.xml");

		TestResultAction action;
		try {
			TestResult result = parse(testResults, build, launcher, listener);

			try {
				action = new TestResultAction(build, result, listener);
			} catch(NullPointerException npe) {
				throw new AbortException(Messages.JUnitResultArchiver_BadXML(testResults));
			}
			result.freeze(action);
			if(result.getPassCount() == 0 && result.getFailCount() == 0)
				throw new AbortException(Messages.JUnitResultArchiver_ResultIsEmpty());

			// TODO: Move into JUnitParser [BUG 3123310]
			List<Data> data = new ArrayList<Data>();
			if(testDataPublishers != null) {
				for(TestDataPublisher tdp : testDataPublishers) {
					Data d = tdp.getTestData(build, launcher, listener, result);
					if(d != null) {
						data.add(d);
					}
				}
			}

			action.setData(data);

			CHECKPOINT.block();

		} catch(AbortException e) {
			if(build.getResult() == Result.FAILURE)
				// most likely a build failed before it gets to the test phase.
				// don't report confusing error message.
				return;

			listener.getLogger().println(e.getMessage());
			build.setResult(Result.FAILURE);
			return;
		} catch(IOException e) {
			e.printStackTrace(listener.error("Failed to archive test reports"));
			build.setResult(Result.FAILURE);
			return;
		}

		build.getActions().add(action);
		CHECKPOINT.report();

		if(action.getResult().getFailCount() > 0)
			build.setResult(Result.UNSTABLE);

		return;
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

	public BuilderConfiguration getConfig() throws Exception {
		if(m_config == null) {
			loadConfiguration();
		}
		return m_config;
	}


	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link EclipsePrjBuilder}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *      This parameter receives the value that the user has typed.
		 * @return
		 *      Indicates the outcome of the validation. This is sent to the browser.
		 */
		public FormValidation doCheckWorkspace(@QueryParameter String value) throws IOException, ServletException {
			if(value.length() == 0)
				return FormValidation.error("Please set a workspace path");
			return FormValidation.ok();
		}

		public FormValidation doCheckProject(@QueryParameter String value) throws IOException, ServletException {
			if(value.length() == 0)
				return FormValidation.error("The project to build from the workspace is mandatory");
			return FormValidation.ok();
		}

		@Override
		public boolean isApplicable(Class< ? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return "Eclipse Project builder";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
//			useFrench = formData.getBoolean("useFrench");
			// ^Can also use req.bindJSON(this, formData);
			//  (easier when there are many fields; need set* methods for this, like setUseFrench)
			save();
			return super.configure(req, formData);
		}
	}


}
