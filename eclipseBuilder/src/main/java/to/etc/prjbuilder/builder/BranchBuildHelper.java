package to.etc.prjbuilder.builder;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.maker.*;
import to.etc.prjbuilder.scm.*;
import to.etc.prjbuilder.scm.bzr.*;
import to.etc.prjbuilder.util.*;
import to.etc.util.*;

/**
 * Helper class containing all that's needed to fully compile or recompile a branch.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Nov 9, 2009
 */
final public class BranchBuildHelper {
	private BuilderConfiguration m_configuration;

	private IBuildListener m_listener;

	private ScmBranch m_branch;

	private BuildMode m_buildMode;

	private BuildIntentType m_buildIntent = BuildIntentType.NORMAL;

	/** The root directory containing all source modules. This is usually the root of some branch. It can be set in multiple ways. */
	private File m_sourceRoot;

	/** The root for the output directory for this build (required). */
	private File m_outputRoot;

	/** After defining the build plan, this contains all modules in the tree. */
	private List<ModuleBuildInfo> m_allModules;

	/** After defining the build plan, this contains the ordered build list. */
	private OrderedBuildList m_buildList;

	private BuildStatus m_finalStatus;

	private String m_finalMessage;

	private ArtefactMode m_artefactMode = ArtefactMode.ALL;

	/** USE GETTER If already known, the revisionID */
	private String m_revisionID;

	private Reporter m_reporter;

	private Date m_startTime, m_endTime;

	private int m_buildNumber;

	private String m_mainModule;

	private Writer m_outputWriter;

	public BranchBuildHelper(BuilderConfiguration bc) {
		m_configuration = bc;
	}

	public Writer getOutputWriter() {
		return m_outputWriter;
	}

	public void setOutputWriter(Writer outputWriter) {
		m_outputWriter = outputWriter;
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Initialization and termination.						*/
	/*--------------------------------------------------------------*/
	/**
	 * Resets the state for this build helper, and starts off a build for a new branch.
	 * @param dbc
	 * @param branch
	 */
	public void initialize(Reporter b, ScmBranch branch, String mainModule, String buildReason) throws Exception {
		if(b == null)
			throw new IllegalArgumentException("Reporter cannot be null");
		m_reporter = b;
		if(branch == null)
			throw new IllegalArgumentException("Branch cannot be null");
		terminate();
		m_branch = branch;
		m_finalMessage = null;
		m_startTime = new Date();
		m_mainModule = mainModule;
	}

	/**
	 * Release all build data, and call the finish listener.
	 * @throws Exception
	 */
	public void terminate() throws Exception {
		try {
			if(m_finalStatus == BuildStatus.BUILDING) {
				m_finalStatus = BuildStatus.FAIL;
				m_finalMessage = "Build failed for an unknown reason!?";
			}
			if(m_listener != null)
				m_listener.branchBuildCompleted(getBranch(), m_finalStatus, m_finalMessage);
		} finally {
			m_sourceRoot = null;
			m_allModules = null;
			m_buildMode = null;
			m_finalStatus = BuildStatus.BUILDING;
			m_finalMessage = null;
			m_revisionID = null;
			m_buildIntent = BuildIntentType.NORMAL;
		}
	}

	public void setCompletionStatus(BuildStatus bs, String ms) {
		//		if(m_buildRun == null)
		//			throw new IllegalStateException("No run is current");
		m_finalStatus = bs;
		m_finalMessage = ms;
	}

	public void	handleException(Exception x) throws Exception {
		if(x instanceof NoBuildNeededException) {
			setCompletionStatus(BuildStatus.NOPE, "A build was not needed because no module changed");
		} else if(x instanceof BuildException) {
			setCompletionStatus(BuildStatus.FAIL, x.getMessage());
		} else {
			x.printStackTrace();
			setCompletionStatus(BuildStatus.FAIL, x.toString());
		}
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Defining the build's output.						*/
	/*--------------------------------------------------------------*/
	/**
	 * Sets the root of the output structure for the build. If the directory does not yet exist it
	 * gets created. If the directory exists it's content can be used to steer the build by skipping
	 * already-compiled modules.
	 *
	 * @param outputRoot
	 */
	public void setOutputRoot(File outputRoot) throws Exception {
		m_outputRoot = outputRoot;
		if(!m_outputRoot.exists() || !m_outputRoot.isDirectory()) {
			m_outputRoot.mkdirs();
		}
		if(!m_outputRoot.isDirectory() || !m_outputRoot.exists())
			throw new IOException("Cannot create the output root directory: " + outputRoot);
	}

	public File getOutputRoot() {
		if(m_outputRoot == null)
			throw new IllegalStateException("The output root directory, which will receive the results of the build, is not set.");
		return m_outputRoot;
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Stuff to set/get the source files.					*/
	/*--------------------------------------------------------------*/
	//	/**
	//	 * Set the source for the build as a checked-out version of the TIP of
	//	 * the branch.
	//	 *
	//	 * Checkout or update my branch.
	//	 * @return
	//	 * @throws Exception
	//	 */
	//	public File checkoutBranchTip() throws Exception {
	//		b().log("Checking out/updating TIP of branch " + m_branch.getName());
	//		BzrHandler bh = new BzrHandler();
	//		m_sourceRoot = Builder.getInstance().getBranchTipLocation(m_branch);
	//		if(m_sourceRoot.exists() && m_sourceRoot.isDirectory()) {
	//			//-- try to PULL changes into this branch
	//			if(bh.pull(b(), m_sourceRoot, m_branch.getBranchUrl())) {
	//				return m_sourceRoot;
	//			}
	//		}
	//
	//		//-- Pulling failed. Create a new branch
	//		if(bh.branch(b(), m_branch.getBranchUrl(), m_sourceRoot)) {
	//			return m_sourceRoot;
	//		}
	//		m_sourceRoot = null;
	//		throw new Exception("Cannot obtain sources - build aborted");
	//	}

	/**
	 * Returns the currently-set root for source files (usually some branch's root).
	 * @return
	 */
	public File getSourceRoot() {
		if(m_sourceRoot == null)
			throw new IllegalStateException("The build source root has not been set. I don't know where my source files to build are..");
		return m_sourceRoot;
	}

	/**
	 * Manually set the root for all sources, when the sources are coming from some constructed
	 * location.
	 * @param sourceRoot
	 */
	public void setSourceRoot(File sourceRoot) {
		m_sourceRoot = sourceRoot;
	}

	public ArtefactMode getArtefactMode() {
		return m_artefactMode;
	}

	public void setArtefactMode(ArtefactMode artefactMode) {
		m_artefactMode = artefactMode;
	}

	public BuildIntentType getBuildIntent() {
		return m_buildIntent;
	}

	public void setBuildIntent(BuildIntentType buildIntent) {
		m_buildIntent = buildIntent;
	}

	/**
	 * Return the revisionID of the branch being built. This requires source to be set. It calls the SCM
	 * handler for the branch to determine the ID.
	 * @return
	 * @throws Exception
	 */
	public String getRevisionID() throws Exception {
		if(m_revisionID == null) {
			BzrHandler bh = new BzrHandler();
			ScmLogEntry le = bh.getLogLast(getSourceRoot());
			m_revisionID = le.getRevisionID();
		}
		return m_revisionID;
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Determining the build plan.							*/
	/*--------------------------------------------------------------*/
	/**
	 * Get all modules and their state from the source directory, then create a build plan.
	 * @return
	 * @throws Exception
	 */
	public OrderedBuildList createBuildPlan(BuildMode mode) throws Exception {
		m_buildMode = mode;
		m_allModules = new ArrayList<ModuleBuildInfo>();
		SourceGetter sg = new SourceGetter(m_configuration, b(), getSourceRoot(), getOutputRoot(), getBranch(), m_mainModule, mode, getBuildIntent(), m_allModules);
		sg.run(); // Determine the contents in the source.
		String s = sg.getFailedMakers();
		if(s != null) {
			setFailedModules(getAllModules(), "Unable to build " + s + " because of errors in the build method");
			throw new BuildException("Unable to build " + s + " because of errors in the build method");
		}

		/*
		 * All makers have initialized proper and all sources have been obtained. Now
		 * resolve all inter-module dependencies (module Y that requires products generated
		 * by module X it depends on).
		 */
		resolveModuleDependencies();

		//-- Walk all module dependencies and create a build plan and full dependencies for each module
		OrderedBuildList buildorder = makeBuildPlan(getAllModules());
		b().log("Total modules is " + buildorder.size());

		//-- Determine what targets actually need to be built
		determineTargetsToBuild(buildorder);
		//		b().detail("After target build detection, state is:");
		//		dumpOrder(buildorder, 0);

		//		//-- Create the build packing list: the list of target module that will represent it.
		//		if(buildorder.getCompilationCount() > 0)
		//			createPackingList(buildorder);
		return buildorder;
	}

	private void setCurrentModule(SourceModule sm) throws Exception {
		if(m_listener != null)
			m_listener.setCurrentModule(getBranch(), sm);
	}

	/**
	 * For each module this walks their "unresolved external dependencies" list and
	 * resolves those dependencies. All resolved dependencies are always marked as
	 * "exported".
	 *
	 * @throws Exception
	 */
	private void resolveModuleDependencies() throws Exception {
		StringBuilder sb = new StringBuilder();
		for(ModuleBuildInfo bi : getAllModules()) {
			setCurrentModule(bi.getModuleVersion());
			if(!bi.resolveExternalDependencies(b())) {
				if(sb.length() > 0)
					sb.append(',');
				sb.append(bi.toString());
			}
		}
		setCurrentModule(null);

		//-- If any module failed to resolve exit,
		if(sb.length() > 0) {
			String s = sb.toString();
			setFailedModules(getAllModules(), "Unable to build " + s + " because files they depend on are missing");
			throw new BuildException("Unable to build " + s + " because files they depend on are missing");
		}

		/*
		 * Now check: all modules that are not yet marked as "to be built" would use their
		 * "old" versions of the exported files. But if the current run has *added* new files
		 * needed for export we must be sure they exist as build artifacts in the *old* run;
		 * if not we need to rebuild just to create the artifacts.
		 */
		for(ModuleBuildInfo bi : getAllModules()) {
			if(bi.getBuildReason() != null) // Already marked as "must be built"?
				continue;

			//-- Compare all exported products of the "last" run with the ones that would be produced now
			List<Product> explist = new ArrayList<Product>(bi.getMaker().getExportedProductList());
			//			TargetModule prev = bi.getPrevBuild();
			//			if(prev == null) {
			//				//-- No previous build known: must rebuild.
			//				bi.setBuildReason("No previous build is known for " + bi.getName());
			//				continue;
			//				//				throw new IllegalStateException("!?! No previous build but we're not compiling this one!?");
			//			}
			sb.setLength(0);
			for(Product p : explist) {
				//-- Check to see if external is present,
				if(p instanceof FileProduct) {
					FileProduct fp = (FileProduct) p;

					if(!fp.getFile().exists()) {
						System.out.println("Missing external: " + fp + ", " + p.getName() + ", " + p);
						if(sb.length() == 0)
							sb.append("Unexpected missing External ");
						else
							sb.append(", ");
						sb.append(p.getName());
					}
				}
			}

			if(sb.length() != 0) { // Were externals missing??
				bi.setBuildReason(sb.toString()); // Force this to rebuild
			}
		}
	}

	//	static private boolean containedIn(Product p, List<ModuleArtifact> maflist) {
	//		String n = p.getName(); // FIXME The Artifact name should not be manually derived here!!!
	//		int pos = n.lastIndexOf('/');
	//		if(pos != -1)
	//			n = n.substring(pos + 1);
	//
	//		for(ModuleArtifact maf : maflist) {
	//			if(maf.getActualName().equals(n))
	//				return true;
	//		}
	//		return false;
	//	}


	/**
	 * Walk all modules, and create full dependencies for each module. These dependencies
	 * are then used to create a build order: a list of all modules starting with the modules
	 * that must be compiled before the others. This does not actually determine that a module
	 * needs a build though, so it always returns all modules.
	 *
	 * @param in
	 * @return
	 * @throws Exception
	 */
	private OrderedBuildList makeBuildPlan(List<ModuleBuildInfo> in) throws Exception {
		OrderedBuildList res = new OrderedBuildList();
		Stack<ModuleBuildInfo> parents = new Stack<ModuleBuildInfo>();
		for(ModuleBuildInfo bi : in) {
			if(bi.getFullDependencyList() == null)
				calcDeps(bi, parents);
			res.addAll(bi.getFullDependencyList());
			res.add(bi);
		}
		m_buildList = res;
		return res;
	}

	private void calcDeps(ModuleBuildInfo bi, Stack<ModuleBuildInfo> parents) throws Exception {
		if(bi.getFullDependencyList() != null)
			return;

		//-- Need to do this one. Am I already in my parent stack?
		int ix = parents.indexOf(bi);
		if(ix >= 0) {
			//-- Circular dependency.
			StringBuilder sb = new StringBuilder();
			sb.append("Circular dependency: ");
			while(ix < parents.size()) {
				sb.append(parents.get(ix++).getModuleVersion().getName());
				sb.append("->");
			}
			sb.append(bi.getModuleVersion().getName());
			throw new MakeConfigException(bi.getModuleVersion(), sb.toString());
		}

		//-- Handle all direct dependencies for this module
		OrderedBuildList res = new OrderedBuildList();
		parents.push(bi);
		for(ModuleBuildInfo tbi2 : bi.getDirectDependencyList()) {
			calcDeps(tbi2, parents);
			res.addAll(tbi2.getFullDependencyList());
			res.add(tbi2);
		}
		parents.pop();
		bi.setFullDependencyList(res);
	}

	/**
	 * For each target we handle build time dependencies. First we lookup the
	 * previous build states for each module in the database. If the last state
	 * was unsuccesful we rebuild.
	 * If the state was succesful we check the compile date; if that is before the
	 * last checkout date we need to recompile also.
	 *
	 * @param list
	 * @throws Exception
	 */
	private void determineTargetsToBuild(OrderedBuildList list) throws Exception {
		//-- Handle basic checks per-module
		for(ModuleBuildInfo bi : list) {
			//		    System.out.println("## Check if module "+bi+" must be built....");
			checkIfModuleMustBeBuilt(bi);
		}

		//-- Handle dependencies: all parents that have a child that will be built are marked for building also
		boolean marked;
		do {
			marked = false;
			for(ModuleBuildInfo bi : list) {
				if(checkForChildrenThatAreBeingBuiltMeaningItWouldBeAGoodIdeaToBuildTheParentToo(bi))
					marked = true;
			}
		} while(marked);
	}

	private boolean checkForChildrenThatAreBeingBuiltMeaningItWouldBeAGoodIdeaToBuildTheParentToo(ModuleBuildInfo bi) {
		if(bi.getBuildReason() != null) // I'm already being built.
			return false;

		//-- Check all my full dependencies and if one is built I'm the pisang too
		for(ModuleBuildInfo tbi : bi.getFullDependencyList()) {
			if(tbi.getBuildReason() != null) {
				bi.setBuildReason("My dependency (" + tbi.getName() + ") will be rebuilt");
				return true;
			}
		}
		return false;
	}

	/**
	 * Does all per-module checks to see if this is to be built. It does not take
	 * the module's dependencies into account.
	 * @param bi
	 */
	private void checkIfModuleMustBeBuilt(ModuleBuildInfo bi) {
		if(m_buildMode == BuildMode.CLEAN) {
			bi.setBuildReason("Clean build requested");
			return;
		}

		if(bi.getBuildReason() != null) // Already know this will be built?
			return;

		//		//-- If there is no previous targetmodule stored an earlier build has gone terribly wrong..
		//		if(bi.getPrevBuild() == null) {
		//			bi.setBuildReason("No earlier build info is known");
		//			return;
		//		}
		//
		//		//-- If an earlier module was not built (it had failed earlier)..
		//		if(bi.getPrevBuild().getFailReason() != null || bi.getPrevBuild().getStatus() != BranchModuleStatus.OKAY || bi.getPrevBuild().getLastBuild() == null) {
		//			bi.setBuildReason("My previous build (build#" + bi.getPrevBuild().getBuildRun().getBuildNr() + ") has failed");
		//			return;
		//		}

		//-- If the maker has a reason to force this thing to be built
		String makerwhy = bi.getMaker().mustBeBuilt();
		if(null != makerwhy) {
			bi.setBuildReason(makerwhy);
			return;
		}

		//-- Ok: this module is fine; the earlier version will be re-used.
		if(bi.isDirty())
			throw new IllegalStateException("?? How come the new thing is dirty??");
	}

	/**
	 * Creates the list of modules that actually will be used in the build.
	 * @param list
	 */
	//	private void createPackingList(OrderedBuildList list) throws Exception {
	//		int ix = 0;
	//		for(ModuleBuildInfo bi : list) {
	//			TargetModule tm = bi.getCurrBuild();
	//			if(!bi.isDirty()) {
	//				tm = bi.getPrevBuild();
	//				if(tm == null)
	//					throw new IllegalStateException("No new target module and no previous one either!? Logic Sucks error!");
	//			} else
	//				b().getDataContext().save(tm);
	//
	//			BuildRunModule brm = new BuildRunModule(getBuildRun(), tm, ix++);
	//			b().getDataContext().save(brm);
	//		}
	//	}


	/**
	 * Return the build mode in effect, if known, as set by createBuildPlan.
	 * @return
	 */
	public BuildMode getBuildMode() {
		if(m_buildMode == null)
			throw new IllegalStateException("The build mode is (currently) unknown, it is known after createBuildPlan() is called.");
		return m_buildMode;
	}

	/**
	 * Return all discovered modules in the source tree.
	 * @return
	 */
	public List<ModuleBuildInfo> getAllModules() {
		if(m_allModules == null)
			throw new IllegalStateException("The list-of-all-modules is (currently) unknown, it is known after createBuildPlan() is called.");
		return m_allModules;
	}

	public OrderedBuildList getOrderedBuildList() {
		if(m_buildList == null)
			throw new IllegalStateException("The ordered build list (currently) unknown, it is known after createBuildPlan() is called.");
		return m_buildList;
	}

	/**
	 * Mark all modules that do not yet have a failure reason to failed with a specific reason. Called
	 * when a build cannot continue because an unrecoverable error has occured; all modules not yet done
	 * will get marked with the unrecoverable error.
	 *
	 * @param list
	 * @param why
	 */
	private void setFailedModules(List<ModuleBuildInfo> list, String why) {
		for(ModuleBuildInfo bi : list) {
			if(bi.isDirty()) {
				if(bi.getBuildError() == null)
					bi.setBuildError(why);
			}
		}
	}

	//	private void dumpOrder(OrderedBuildList l, int lvl) {
	//		StringBuilder sb = new StringBuilder();
	//		for(ModuleBuildInfo bi : l) {
	//			sb.setLength(0);
	//			for(int i = 0; i < lvl; i++)
	//				sb.append("..");
	//			sb.append(bi.getModuleVersion().toString());
	//			if(bi.getBuildReason() != null) {
	//				sb.append(": buildreason=");
	//				sb.append(bi.getBuildReason());
	//			}
	//			System.out.println(sb.toString());
	//			//			b().detail(sb.toString());
	//			//			dumpOrder(bi.getFullDependencyList(), lvl + 1);
	//		}
	//	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Compilation.										*/
	/*--------------------------------------------------------------*/

	public int compileModules(Progress subp) throws Exception {
		subp.setTotalWork(getOrderedBuildList().getCompilationCount());
		int failures = 0;
		for(ModuleBuildInfo bi : getOrderedBuildList()) {
			if(m_listener != null)
				m_listener.moduleBuildStarted(bi);

			//-- Check if any of my dependents has suffered a build failure
			ModuleBuildInfo fbi = bi.checkDescendentsForBuildFailures();
			if(fbi != null) {
				bi.setBuildError("Not built because dependencies failed to build: " + fbi.toString());
				bi.setBuildStatus(ModuleBuildStatus.NONE);
				if(m_listener != null)
					m_listener.moduleBuildCompleted(bi, bi.getBuildStatus(), bi.getBuildError());
				continue;
			}

			if(bi.getBuildReason() != null) {
				Progress cp = subp.createSubProgress("Compiling " + bi.getName(), 1);
				cp.setCompleted(0);
				compile(bi);
				cp.complete();
			}

			if(bi.getBuildError() != null) {
				failures++;
				if(bi.getBuildStatus() == ModuleBuildStatus.NONE)
					bi.setBuildStatus(ModuleBuildStatus.ERROR);
			}

			if(m_listener != null)
				m_listener.moduleBuildCompleted(bi, bi.getBuildStatus(), bi.getBuildError());
		}
		if(failures > 0) {
			throw new BuildException(failures + " modules failed to compile");
		}
		return failures;
	}

	/**
	 * Ask the assigned build thingy to do it's thing.
	 */
	private void compile(final ModuleBuildInfo bi) throws Exception {
		try {
			setCurrentModule(bi.getModuleVersion());
			//			bi.clearPreviousBuild(); // Discard the "previous build" thingy.

			//-- Create an output thingy for logging.
			StringWriter sw = new StringWriter() {
				@Override
				public void close() throws IOException {
					flushMakeLog(bi, this);
				}
			};
			Writer ow = sw;
			if(m_outputWriter != null) {
				TeeWriter tw = new TeeWriter(m_outputWriter, sw);
				ow = tw;
			}

			Date dt = new Date();

			try {
				bi.getMaker().buildModule(ow);
			} finally {
				try {
					sw.close();
				} catch(Exception x) {
					x.printStackTrace();
				}
			}
			if(bi.getBuildError() == null) {
				//-- Compiled Ok!! Set the status of the module to OK
				bi.setBuildStatus(ModuleBuildStatus.OKAY);
				//				saveArtefacts(bi);
			}
			//			bi.savePreviousBuild();
		} catch(Exception x) {
			x.printStackTrace();
			b().exception(x, "Fatal exception while compiling/making " + bi);
			bi.setBuildError(x.toString());
			b().error("Exception: " + x);
			//			bi.savePreviousBuild();
		} finally {
			try {
				setCurrentModule(null);
			} catch(Exception x) {
				x.printStackTrace(); // Mostly ignore useless.
			}
		}
	}

	void flushMakeLog(ModuleBuildInfo bi, StringWriter sw) throws IOException {
		MessageFilter filter = bi.getMaker().getFilterChain(new FinalLogFilter());

		//-- Get all filter chains from the maker, then let 'm filter.
		String all = sw.toString();
//		System.out.println(">> ");
//		System.out.println(all);
//		System.out.println("<<");

		LineNumberReader lr = new LineNumberReader(new StringReader(all));
		String in;
		while(null != (in = lr.readLine())) {
			filter.filterLine(b(), in);
		}
	}

	//	private void saveArtefacts(ModuleBuildInfo bi) throws Exception {
	//		switch(getArtefactMode()){
	//			default:
	//				throw new IllegalStateException("Unknown artefactmode: " + getArtefactMode());
	//
	//			case NONE:
	//				return;
	//			case ALL:
	//				break;
	//			case TOP:
	//				//-- Is this the top-level module?
	//				String name = bi.getModuleVersion().getName();
	//				if(!bi.getModuleVersion().getRepository().getMainModuleName().toLowerCase().contains(name.toLowerCase()))
	//					return;
	//				break;
	//		}
	//		System.out.println("Saving artefacts for " + bi + ": " + getArtefactMode());
	//
	//		List<Product> list = new ArrayList<Product>(bi.getMaker().getExportedProductList());
	//		list.addAll(bi.getMaker().getGeneratedProductList());
	//		for(Product p : list) {
	//			if(p instanceof FileProduct) {
	//				FileProduct fp = (FileProduct) p;
	//				if(!fp.getFile().exists()) {
	//					b().log("Build artifact " + fp.getNameWithoutPath() + " does not exist after the build");
	//					continue;
	//				}
	//				//				b().detail("Saving build artifact "+fp.getNameWithoutPath()+" to database");
	//				ModuleArtifact ma = new ModuleArtifact();
	//				ma.setActualName(fp.getNameWithoutPath());
	//				ma.setMimeType("application/octet-stream");
	//				ma.setModule(bi.getCurrBuild());
	//				ma.setBlob(Hibernate.createBlob(new FileInputStream(fp.getFile())));
	//				ma.setSize((int) fp.getFile().length());
	//				bi.getCurrBuild().getArtifactList().add(ma);
	//				b().getDataContext().save(ma);
	//
	//			}
	//		}
	//	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Doing a full build...								*/
	/*--------------------------------------------------------------*/

	public void fullCompile(final BuildMode mode, Progress p) throws Exception {
		m_buildMode = mode;
		p.setTotalWork(100);

		//-- Mark as starting
		if(m_listener != null)
			m_listener.branchBuildStarted(getBranch(), getBuildMode(), getBuildIntent());

		//-- Try to get the build# from the previous build, if applicable
		if(m_buildNumber == 0) {
			BuildDetails bd = BuildDetails.load(getOutputRoot());
			if(bd != null)
				m_buildNumber = bd.getBuildNr() + 1;
		}

		BuildDetails.delete(getOutputRoot()); // Clear all build details

		//-- Load all modules, resolve dependencies and create the build plan.
		Progress spro = p.createSubProgress("create build plan", 10.0);
		spro.setTotalWork(100.0);
		OrderedBuildList buildorder = createBuildPlan(m_buildMode);
		spro.complete(); // 20% complete
		b().log("Total modules is " + buildorder.size());

		//		b().getDataContext().commit();

		//-- If all builds are unnecessary quit;
		int count = buildorder.getCompilationCount();
		if(count == 0) {
			b().important("No builds necessary.");
			setCompletionStatus(BuildStatus.OKAY, "No builds needed, up-to-date");
			p.complete();
			saveBuildDetails();
			return;
		}
		b().detail("We need to build " + count + " modules.");
		for(ModuleBuildInfo bi : buildorder) {
			if(bi.getBuildReason() != null)
				b().detail(".." + bi.getModuleVersion().toString() + ": reason=" + bi.getBuildReason());
		}

		b().header("Starting compilations");
		Progress subp = p.createSubProgress(null, 80); // Rest of 80% divided between compilations
		int failures = compileModules(subp);

		if(failures > 0)
			throw new BuildException(failures + " modules failed to compile");
		setCompletionStatus(BuildStatus.OKAY, "Completed succesfully");
		saveBuildDetails();
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Private sillyness...								*/
	/*--------------------------------------------------------------*/

	private void saveBuildDetails() throws Exception {
		BuildDetails bd = new BuildDetails();
		bd.setBuildNr(getBuildNumber());
		bd.setOnDate(getStartTime());
		bd.setOnRevision(getRevisionID());
		bd.store(getOutputRoot());
	}

	private Reporter b() {
		if(m_reporter == null)
			throw new IllegalStateException("No Reporter is set.");
		return m_reporter;
	}

	/**
	 * Returns the current branch being built.
	 * @return
	 */
	public ScmBranch getBranch() {
		return m_branch;
	}

	public Date getStartTime() {
		return m_startTime;
	}

	public Date getEndTime() {
		return m_endTime;
	}

	public int getBuildNumber() {
		return m_buildNumber;
	}

	public IBuildListener getListener() {
		return m_listener;
	}

	public void setListener(IBuildListener listener) {
		m_listener = listener;
	}
}
