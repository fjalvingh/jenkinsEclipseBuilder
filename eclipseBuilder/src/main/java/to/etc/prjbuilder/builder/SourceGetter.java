package to.etc.prjbuilder.builder;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.maker.*;
import to.etc.prjbuilder.scm.*;
import to.etc.prjbuilder.util.*;
import to.etc.util.*;

/**
 * The original (SVN) version of this class determined the projects to
 * compile and the repositories to checkout these projects from. The BZR
 * version gets passed an existing branch with working directory and:
 * <ul>
 * 	<li>Locates all projects that have to be build</li>
 * 	<li>Initializes the projects' makers and determines dependencies</li>
 * </ul>
 *
 * <p>The next step creates a full dependency map.
 *
 * <p>While this code runs it will store all of the ModVer's used in a
 * local table. When a run has completed succesfully (i.e. no errors of
 * any kind) this table is compared with the full ModVer table in the
 * module manager and all modules present in the Module Manager but
 * not used in this run will be deleted. This cleans stale versions.
 *
 * The same happens with TargetModules, per Target: as soon as the run
 * ends all TargetModules no longer encountered will be deleted.
 *
 * @author jal
 * Created on Feb 12, 2005
 */
public class SourceGetter {
	private BuilderConfiguration m_configuration;

	/** The root of the branch's working files. All source files start from here. */
	private final File					m_branchRoot;

	/** The root directory for the builds's results. */
	private final File					m_outputRoot;

	private final List<ModuleBuildInfo>	m_list;

	private String m_mainModuleName;

	private Reporter m_r;

	private final ScmBranch m_branch;

	/** The todo queue: all version to handle for the current target  */
	private final ArrayList<ModuleBuildInfo> m_todo_q = new ArrayList<ModuleBuildInfo>();

	/** Maps ModuleVersionIDs to their defined build info */
	private final Map<SourceModule, ModuleBuildInfo>		m_moduleMap = new HashMap<SourceModule, ModuleBuildInfo>();

	/** Maps module names to their defined build info */
	private final Map<String, ModuleBuildInfo>	m_moduleNameMap = new HashMap<String, ModuleBuildInfo>();

	private BuildMode			m_buildMode;

	private BuildIntentType m_buildIntent;

	/**
	 * After the repository discovery scan this contains all buildable modules in the repository.
	 */
	private final Map<String, ModuleMaker>		m_knownModuleMap = new HashMap<String, ModuleMaker>();

	public SourceGetter(BuilderConfiguration bc, Reporter r, File branchRoot, File outputRoot, ScmBranch t, String mainmodule, BuildMode mode, BuildIntentType intent, List<ModuleBuildInfo> list) {
		m_configuration = bc;
		m_r = r;
		m_branchRoot = branchRoot;
		m_outputRoot = outputRoot;
		m_buildMode = mode;
		m_branch	= t;
		m_list = list;
		m_buildIntent = intent;
		m_mainModuleName = mainmodule;
	}

	final private BuildIntentType getBuildIntent() {
		return m_buildIntent;
	}

	private Reporter r() {
		return m_r;
	}

	public List<ModuleBuildInfo>	getModuleList() {
		return new ArrayList<ModuleBuildInfo>(m_list);
	}

	public File	getBranchRoot() throws IOException {
		return m_branchRoot;
	}

	/**
	 * Add the specified module to the queue, if needed. If the module cannot
	 * be found this returns null, indicating that the build will fail.
	 * @param sourceModuleName
	 * @return
	 */
	private ModuleBuildInfo	queueSource(String sourceModuleName) throws Exception {
		//-- Already known?
		ModuleBuildInfo	bi = m_moduleNameMap.get(sourceModuleName);
		if(bi != null)
			return bi;

		//-- The module must exist in the branch
		ModuleMaker mm = m_knownModuleMap.get(sourceModuleName);
		if(mm == null) {
			//-- We cannot locate this module -> fail build
			return null;
		}

		//-- Try to create a ModuleBuildInfo && queue it.
		SourceModule sm = new SourceModule(m_branch, sourceModuleName);

		//		//-- Create a new build state; this however gets saved ONLY if the module needs a build.
		//		TargetModule	tm	= new TargetModule();
		//		tm.setBuildRun(m_buildRun);
		//		tm.setSourceVersion(sm);
		//		tm.setStatus(BranchModuleStatus.NONE);
		//		tm.setBranch(m_branch);

//		System.out.println("## CREATE module for version="+smv);

		//-- New location for this-module's build files,
		File	out = new File(m_outputRoot, mm.getModuleName());
		out.mkdirs();

		bi = new ModuleBuildInfo(m_configuration, m_branch, mm, out, sm);
		m_list.add(bi);				// pass to owner.
		m_todo_q.add(bi);
		m_moduleMap.put(sm, bi);
		m_moduleNameMap.put(sm.getName(), bi);

		//-- If this module contains a "puzzler.properties" file read it,
		File	pp = new File(bi.getSourceRoot(), "puzzler.properties");
		if(pp.exists()) {
			try {
				bi.loadProperties(pp);
			} catch(Exception x) {
				r().log(bi + ": can't load puzzler.properties: " + x);
			}
		}

		//-- Initialize the maker,
		try {
			mm.initialize(r(), bi, getBuildIntent());
		} catch(MakeException x) {
			r().error("Maker error: " + x.getMessage() + " in " + bi);
			bi.setMakerConfigError(x.getMessage());
			return null;
		}
		return bi;
	}

	public String	getFailedMakers() {
		StringBuilder	sb = null;

		for(ModuleBuildInfo bi : m_moduleMap.values()) {
			if(bi.getMakerConfigError() != null) {
				if(sb == null) {
					sb = new StringBuilder();
				} else
					sb.append(',');
				sb.append(bi.getModuleVersion().toString());
			}
		}
		return sb == null ? null : sb.toString();
	}

	/**
	 * The entrypoint. This walks the projects, rebuilds dependencies enz.
	 * @throws Exception
	 */
	public void run() throws Exception {
		try {
			r().header("Retrieving all modules from the branch");
			discoverModules();

			//-- Add the main module;
			ModuleBuildInfo bi = queueSource(m_mainModuleName);
			if(bi == null) {
				r().error("The main module '" + m_mainModuleName + "' of branch " + m_branch + " cannot be found.");
				return;
			}

			//-- Now, starting at the root project, discover all needed projects.
			Set<SourceModule> doneset = new HashSet<SourceModule>();
			int done = 0;
			while(m_todo_q.size() > 0) {
				bi = m_todo_q.remove(m_todo_q.size() - 1);
				if(!doneset.contains(bi.getModuleVersion())) {
					if(done++ > 999)
						throw new IllegalStateException("Internal: TODO queue does not become empty after 999 tries!!!");
					doneset.add(bi.getModuleVersion()); // Notify as "done"
					handleModVer(bi);
				}
			}
		} finally {
			//-- Safety
		}
	}

	/**
	 * Scan the entire branch, and add all modules we can find there to the known
	 * module map. To prevent excessive recursion this only travels 3 levels deep;
	 * projects below that will not be found(!).
	 */
	private void		discoverModules() throws Exception {
		long ts = System.nanoTime();
		m_knownModuleMap.clear();
		discoverModules(getBranchRoot(), 0);
		ts = System.nanoTime() - ts;
		r().log("Discovered " + m_knownModuleMap.size() + " buildable modules in this branch in " + StringTool.strNanoTime(ts));
	}

	private void	discoverModules(File dir, int depth) throws Exception {
		File[]	far = dir.listFiles();
		for(File f : far) {
			if(f.isDirectory()) {
				/*
				 * Projects are always in a dir.. Is this a makeable project?
				 */
				ModuleMaker mm = MakerRepository.findBuilder(r(), f);
				if(mm != null) {
					//-- Accept this dir as a buildable module.
					m_knownModuleMap.put(mm.getModuleName(), mm);
				} else {
					//-- Check if this directory contains other projects, if not too deep already.
					if(depth <= 2)
						discoverModules(f, depth+1);
				}
			}
		}
	}

	/**
	 * Handles the specified module. It gets checked out or updated, and after
	 * that a builder gets assigned to it and its dependencies are determined;
	 * versions for the dependencies are defined and added to the todo
	 * queue if not yet present.
	 *
	 * @param smv
	 * @throws Exception
	 */
	private void handleModVer(ModuleBuildInfo bi) throws Exception {
//		SourceModule	smv = bi.getModuleVersion();
		//-- Read direct dependencies from the maker's configuration.
		//-- Maker known. Get dependencies and add all of 'm that are not yet seen to the queue
		Set<String>	depset = bi.getMaker().getDirectDependencies();		// Module names this immediately depends on

		for(String dep : depset) {
			//			r().detail("Resolving dependency "+dep+" of "+bi);
			ModuleBuildInfo tbi = m_moduleNameMap.get(dep);
			if(tbi != null) {							// Already loaded?
				bi.addDirectDependency(tbi);
				continue;								// Skip;
			}
			tbi = queueSource(dep);						// Create/retrieve dependency
			if(tbi == null) {
				// Missing dependency. Die.
				r().error("Missing dependency: '" + dep + "' for " + bi + " not found");
				bi.setMakerConfigError("Missing dependency: '"+dep);
				return;
			}
			bi.addDirectDependency(tbi);
			//			r().log("Added autodiscovered dependency "+tbi);
		}

		//-- Check if the module changed since last build by checking a source file hash.
		File cf = new File(bi.getOutputDir(), ".sources.hash.properties");
		SourceInventory	sin	= SourceInventory.load(cf);
		long ts = System.nanoTime();
		if(sin == null) {
			//-- New thingy; create an inventory && mandatory rebuild
			bi.setBuildReason("Unknown 'old sources' state - cannot see if sources have changed");
			sin = SourceInventory.createInventory(bi.getSourceRoot(), bi.getMaker().getBuildSources());
			sin.save(cf);
		} else {
			//-- Check for changes
			List<File> changes = sin.checkForChanges(bi.getSourceRoot(), bi.getMaker().getBuildSources());
			if(changes.size() > 0) {
				for(File f : changes)
					r().detail("Rebuild needed because source " + f + " has changed");

				bi.setBuildReason("Sources have changed");
				sin.save(cf);
			} else if(m_buildMode == BuildMode.CLEAN)
				bi.setBuildReason("Clean build requested");
		}
		ts = System.nanoTime() - ts;
		//		System.out.println("... " + bi.getName() + " source changed check in " + StringTool.strNanoTime(ts) + ": " + bi.getBuildReason());

//		bi.setBuildReason("Mandatory rebuild (change checking not impl");
		if(bi.getBuildReason() != null)
			bi.getMaker().clean();
	}
}
