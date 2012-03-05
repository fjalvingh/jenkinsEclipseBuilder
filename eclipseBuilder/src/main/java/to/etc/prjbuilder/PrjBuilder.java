package to.etc.prjbuilder;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.prjs.*;
import to.etc.prjbuilder.scm.*;
import to.etc.prjbuilder.scm.bzr.*;
import to.etc.prjbuilder.util.*;
import to.etc.util.*;

/**
 * Utility class to build Eclipse web projects fast.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on May 11, 2010
 */
public class PrjBuilder {
	private BuilderConfiguration m_config;

	private ProjectConfig m_prjConfig;

	private File m_configFile;

	private File m_branchDir;

	private String m_mainModule;

	private BuildMode m_mode = BuildMode.NORMAL;

	private BuildIntentType m_intent = BuildIntentType.NORMAL;

	private PrjBuildReporter m_reporter;

	private PrjProject m_currentProject;

	public static void main(String[] args) {
		try {
			new PrjBuilder().run(args);
		} catch(Exception x) {
			x.printStackTrace();
		}
	}

	/**
	 *
	 * @param args
	 * @throws Exception
	 */
	private void run(String[] args) throws Exception {
		try {
			decodeArgs(args);
		} finally {
			closeAll();
			closeLog();
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

	public BuilderConfiguration getConfig() throws Exception {
		if(m_config == null) {
			loadConfiguration();
		}
		return m_config;
	}

	public ProjectConfig getPrjConfig() throws Exception {
		if(m_prjConfig == null) {
			getConfig();

			//-- Check if a project config is available
			Properties p = FileTool.loadProperties(m_configFile);
			if(p.getProperty("projects") == null) {
				//-- Oops- no project config in that file. Add it from the 'demo' resource
				InputStream is = getClass().getResourceAsStream("demo.properties");
				Properties newp = new Properties();
				try {
					newp.load(is);
				} finally {
					try {
						is.close();
					} catch(Exception x) {}
				}
				p.putAll(newp); // Merge sets;
				FileTool.saveProperties(m_configFile, p);
			}

			//-- Load projects config
			ProjectConfig pc = new ProjectConfig(m_configFile);
			pc.load();
			m_prjConfig = pc;
		}
		return m_prjConfig;
	}

	public Reporter getReporter() {
		if(m_reporter == null) {
			//-- Create a log file in homedir or root of c: on peanuts
			File root;
			if(File.separatorChar == '/') {
				root = new File(System.getProperty("user.home"));
			} else {
				root = new File("c:\\");
			}
			File log = new File(root, "prjbuilder.log");
			System.out.println("Output log is " + log.getAbsolutePath());
			m_reporter = new PrjBuildReporter(log);
		}
		return m_reporter;
	}

	private void closeLog() {
		if(m_reporter != null) {
			try {
				m_reporter.close();
			} catch(Exception x) {
				x.printStackTrace();
				System.err.println("FATAL: Cannot close logfile. IT WILL BE INCOMPLETE!!");
			}
			m_reporter = null;
		}
	}

	public void fatal(String s) {
		System.out.println("ERROR: " + s);
		System.exit(10);
	}

	private void closeAll() {
		try {
		} catch(Exception x) {}

	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Argument decode.									*/
	/*--------------------------------------------------------------*/
	/** Current argument index */
	private int m_ix;

	private String[] m_args;

	private void decodeArgs(String[] args) throws Exception {
		if(args.length == 0) {
			usage();
		}

		m_ix = 0;
		m_args	= args;
		while(m_ix < args.length) {
			String arg = m_args[m_ix++];
			String val = "";
			if(arg.equals("-mode")) {
				val = arg();
				m_mode = BuildMode.valueOf(val.toUpperCase());
			} else if(arg.equals("-goal")) {
				val = arg();
				m_intent = BuildIntentType.valueOf(val.toUpperCase());
			} else if(arg.equals("-build")) {
				// BUILD action must have dir and module parameter
				String[] sar = arg(2);
				File bdir = new File(sar[0]);
				if(!bdir.exists() || !bdir.isDirectory()) {
					usage("The path " + bdir + " is invalid");
				}
				commandLineBuild(bdir, sar[1]);
			} else if(arg.equals("-mone")) {
				String[] sar = arg(3);
				PrjVersion pv = getPrjConfig().getVersion(sar[0], sar[1]);
				mergeSingle(pv, sar[2]);
			} else if(arg.startsWith("-")) {
				System.out.println("Unknown option '" + arg + "'.");
				usage();
			} else {
				if(m_branchDir == null) {
					m_branchDir = new File(arg);
					if(!m_branchDir.exists() || !m_branchDir.isDirectory()) {
						System.out.println(m_branchDir + ": bad branch directory (missing, or not a directory)");
						usage();
					}
				} else if(m_mainModule == null) {
					m_mainModule = arg.trim();
				} else {
					System.out.println("Too many arguments.");
					usage();
				}
			}
		}


		//
		//		if(m_branchDir == null) {
		//			System.err.println("Need a branch directory as 1st parameter");
		//		} else if(m_mainModule == null) {
		//			System.err.println("Need a main module name as 2nd parameter");
		//		} else
		//			return;
		//		usage();
	}

	private String arg() {
		if(m_ix < m_args.length) {
			String s = m_args[m_ix];
			if(!s.startsWith("-")) {
				m_ix++;
				return s;
			}
		}
		System.out.println("Missing value for argument " + m_args[m_ix - 1]);
		usage();
		return "";
	}

	private String[] arg(int count) {
		String[] res = new String[count];
		String cmd = m_args[m_ix - 1];
		if(m_ix + count > m_args.length) {
			System.out.println("The option " + cmd + " requires " + count + " arguments");
			usage();
			return null;
		}

		int ix = 0;
		while(ix < count) {
			String s = m_args[m_ix++];
			if(s.startsWith("-")) {
				System.out.println("The option " + cmd + " requires " + count + " arguments");
				usage();
				return null;
			}
			res[ix++] = s;
		}
		return res;
	}

	private void usage(String error) {
		System.out.println("Error: " + error);
		usage();
	}

	private void usage() {
		System.out.println("Usage: PrjBuilder [options]. Options need to be set BEFORE an action option uses them!\n" + "Global options:\n" //
			+ "-mode [bldmode]: set build mode to NORMAL, CLEAN or UPDATE\n" + "-goal [type]: set the build goal to NORMAL or FIX" //
			+ "\nActions:\n" //
			+ "-build [directory] [mainmodule]: build the specified module in the directory\n" //
			+ "-mone [project] [version] [merge-branch]: merges a single change branch and creates a local fix" //
			); //


		System.exit(10);
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Command-line build request							*/
	/*--------------------------------------------------------------*/
	/**
	 * This builds the specified directory.
	 * @param bdir
	 * @param string
	 */
	private void commandLineBuild(File bdir, String main) throws Exception {
		try {
			ScmBranch br = BzrHandler.getInstance().getBranch(bdir.getAbsolutePath());
			BranchBuildHelper bbh = new BranchBuildHelper(getConfig());
			bbh.initialize(getReporter(), br, main, "Command line build request");

			bbh.setArtefactMode(ArtefactMode.NONE);
			bbh.setBuildIntent(m_intent);
			bbh.setSourceRoot(bdir);
			bbh.setOutputRoot(bdir); // Normal Eclipse builds: generate BIN dirs.
			Progress p = new Progress("FullBuild");
			bbh.fullCompile(m_mode, p);
		} catch(Exception x) {
			System.out.println("build failed: " + x);
			throw x;
		}
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Single-shot merge.									*/
	/*--------------------------------------------------------------*/
	/**
	 * Merge, build and fix a single branch. The process is as follows:
	 * <ul>
	 *	<li>Make sure the data in the specified branch is commited by running commit in there.</li>
	 *	<li>If no commit is necessary read the last commit comment and store it as the fix comment.</li>
	 *	<li>Ask for the fix comment, and use the last commit message if available as the basis.</li>
	 *	<li>Now, if the earlier commit was still needed use that fix comment as the commit comment, and commit</li>
	 *	<li>We now have a fully-commited source branch *and* we have a fix comment.</li>
	 *	<li>LOOP START: We now need the "before" image by getting and building a clean "pristine". Do a pull --overwrite and clean the tree for
	 *		the pristine copy. This should result in a clean "pristine" that is fully up-to-date with the -tip.</li>
	 *	<li>Build the pristine tree. This will become the "before" image for generating the delta.</li>
	 *	<li>Merge -pristine /into/ the source tree and if necessary commit the merge with a fixed comment.</li>
	 *	<li>The source tree will now be up-to-date with pristine AND will contain the new code.</li>
	 *	<li>Build the source tree. This generates the "after" image containing //only// the changes made in this source with respect to -pristine.</li>
	 *	<li>Merge the source tree //inside// pristine and commit with the fix message. This is the //actual// merge of the new code inside the master branch</li>
	 *	<li>Now push the pristine back to the master. IF THIS FAILS DUE TO "Branches have diverged" GOTO LOOP START. It means that someone merged something before you.</li>
	 *	<li>We have merged everything. Create the delta and from that build a fix package, and store it on the target machine.</li>
	 *	<li></li>
	 * </ul>
	 * @param pv
	 * @param string
	 */
	private void mergeSingle(PrjVersion pv, String string) throws Exception {
		if(pv.getPhase() == VersionPhase.DEVELOPMENT) {
			fatal("Branch " + pv.getLocal() + " is in DEVELOPMENT mode and does not create fixes.");
			return;
		}

		System.out.println("Merge for " + pv);
		BzrHandler bh = BzrHandler.getInstance();
		ScmBranch	b = bh.getBranch(string);
		if(! b.isBranchWithSources()) {
			fatal("Path "+string+" is not a bzr branch with sources, appearently");
			return;
		}

		//-- Must be fully commited
		MergeDetails md = bh.status(b);
		if(md.getChangeList().size() > 0) {
			fatal("Uncommited changes in " + b + ": please commit first");
			return;
		}

		//-- Get a log message from the last commited thingy, then use that to get the fix comment and stuff
		ScmLogEntry le = bh.getLogLast(b);
		if(le == null) {
			fatal("Cannot get log message from " + b);
			return;
		}

		FixMsg fm = FixMsg.getFixMsg(le);
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Full-blown merge.									*/
	/*--------------------------------------------------------------*/
	/*
	 * Main full-merge process.
	 * ------------------------
	 * This part handles a full merge and fix creation for a project. It starts at
	 * a given version and merges a fix branch in there, then it creates a fix and
	 * merges upward, also creating fixes for those branches that need it.
	 *
	 * It is a restartable process: if the process fails the code leaves a trail
	 * of it's current execution point and all data needed to restart the fix merge; the
	 * user can manually fix the problem then restart the same merge which will continue
	 * from where it left off.
	 *
	 * To prevent race conditions the code locks the project repo while running. This
	 * ensures that only a single fix merge is taking place. If the process aborts the
	 * lock is released. When the process is restarting it reclaims the lock, then determines
	 * if the state of the remote fix repo is still the same. If not then the entire
	 * build process is started from the top because others have merged stuff.
	 *
	 * All branches to use in the merge must be clean, meaning "bzr status" should return
	 * no pending changes. You must commit everything before starting the fix process.
	 *
	 * Clean (non-restarting) fixmerge:
	 * --------------------------------
	 * A clean merge starts when no old merge is pending. If a failed merge is still present
	 * the builder will restart that one. The clean merge works in separate phases:
	 *
	 * 1. 	A merge plan is calculated from the source branch to all other branches that will
	 * 		be impacted by the new code. This merge plan takes the data in the project properties
	 * 		to create an ordered merge plan.
	 * 2.	The remote repository is locked. This should prevent fix updates.
	 * 3.	Clean phase:
	 * 3.1	Walk all branches in the merge plan
	 * 3.2	For every branch's pristine version:
	 * 3.3	Pull the pristine version from the master's tip
	 * 3.4	If pristine changed or is uncompiled mark it as needs-build: this would determine the before image
	 * 3.5	Check the branch for uncommited stuff. If present abort immediately.
	 *
	 * 2.	Clean/check phase:
	 *
	 *
	 *
	 *
	 *
	 *
	 *
	 */


}
