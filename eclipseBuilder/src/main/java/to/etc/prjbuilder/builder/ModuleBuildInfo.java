package to.etc.prjbuilder.builder;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.maker.*;
import to.etc.prjbuilder.scm.*;
import to.etc.prjbuilder.util.*;
import to.etc.util.*;

public class ModuleBuildInfo {
	private BuilderConfiguration m_configuration;

	private final ScmBranch m_target;

	private final SourceModule m_moduleVersion;
	//
	//	private TargetModule m_prevBuild;
	//
	//	private final TargetModule	m_currBuild;

	private ModuleMaker			m_maker;

	private File				m_outputDir;

	/** When not null this module has trouble retrieving it's sources, and this contains the error. */
//	private String				m_sourcesError;

	private String				m_makerConfigError;

	/** Set when this module MUST be rebuilt. Only the first reason for the build is set. */
	private String				m_buildReason;

	private ModuleBuildStatus m_buildStatus = ModuleBuildStatus.NONE;

	private String				m_buildError;

	private final List<ModuleBuildInfo>		m_directDependencyList = new ArrayList<ModuleBuildInfo>();

	private OrderedBuildList	m_fullDependencyList;

	private boolean				m_dirty;

	/**
	 * For targets that are "fixable" by the fix package handler this defines the FixPackage generator
	 */
	private String				m_fixGenerator;

	private Properties			m_moduleProperties = new Properties();

	/**
	 * A list of references to products in <i>other</i> modules that need to be resolved
	 * when all modules have been loaded from SCM and configured.
	 */
	private List<ModuleFileRef>	m_externalRefList = new ArrayList<ModuleFileRef>();

	public ModuleBuildInfo(BuilderConfiguration c, ScmBranch target, ModuleMaker maker, File outputDir, SourceModule moduleVersion) {
		m_configuration = c;
		m_moduleVersion = moduleVersion;
		m_target = moduleVersion.getBranch();
		m_maker		= maker;
		m_outputDir = outputDir;
	}

	public BuilderConfiguration getConfiguration() {
		return m_configuration;
	}

	public String getFixGenerator() {
		return m_fixGenerator;
	}
	public void setFixGenerator(String fixGenerator) {
		m_fixGenerator = fixGenerator;
	}
	public String	getName() {
		return m_moduleVersion.getName();
	}

	public SourceModule getModuleVersion() {
		return m_moduleVersion;
	}
	public boolean isDirty() {
		return m_dirty;
	}

	public String getBuildError() {
		return m_buildError;
	}
	public void setBuildError(String buildError) {
		m_buildError = buildError;
		m_dirty = true;
	}
	public File getOutputDir() {
		return m_outputDir;
	}

	@Override
	public String toString() {
		return getModuleVersion().toString();
	}

	public File getSourceRoot() {
		return getMaker().getSourceRoot();
	}
	public String getMakerConfigError() {
		return m_makerConfigError;
	}

	public void setMakerConfigError(String makerConfigError) {
		m_makerConfigError = makerConfigError;
		m_dirty = true;
	}

	public ModuleMaker getMaker() {
	    if(m_maker == null)
	        throw new IllegalStateException("Internal: no 'maker' for module="+this);
		return m_maker;
	}

	public void addDirectDependency(ModuleBuildInfo smv) {
		m_directDependencyList.add(smv);
	}
	public List<ModuleBuildInfo> getDirectDependencyList() {
		return m_directDependencyList;
	}
	public ModuleBuildInfo	findDirectDependency(String modulename) {
		for(ModuleBuildInfo bi : getDirectDependencyList()) {
			if(bi.getModuleVersion().getName().equals(modulename))
				return bi;
		}
		return null;
	}

	public OrderedBuildList getFullDependencyList() {
		return m_fullDependencyList;
	}
	void setFullDependencyList(OrderedBuildList fullDependencyList) {
		m_fullDependencyList = fullDependencyList;
	}

	public String getBuildReason() {
		return m_buildReason;
	}

	public void setBuildReason(String buildReason) {
		if(m_buildReason == null)
			m_buildReason = buildReason;
		m_dirty = true;
	}

	//	public void setRetrievalDate(Date d) {
	//		getCurrBuild().setLastRetrieval(d);
	//		m_dirty = true;
	//	}

	public ModuleBuildInfo	checkDescendentsForBuildFailures() {
		for(ModuleBuildInfo bi : getFullDependencyList()) {
			if(bi.getBuildError() != null)
				return bi;
		}
		return null;
	}

	public void	loadProperties(File from) throws Exception {
		InputStream	is	= null;
		try {
			is	= new FileInputStream(from);
			m_moduleProperties.load(is);
		} finally {
			try { if(is !=null) is.close(); }catch(Exception x) {}
		}
	}
	public Properties getModuleProperties() {
		return m_moduleProperties;
	}
	public String	getModuleProperty(String key) {
		return getModuleProperties().getProperty(key);
	}

	public void	deleteRelativeFilesList(List<String> names) {
		for(String name : names) {
			File	f = new File(getOutputDir(), name);
			if(f.exists()) {
				if(f.isDirectory())
					FileTool.deleteDir(f);
				else
					f.delete();
			}
		}
	}

	//	public JavaVersion		getJavaSourceLevel() {
	//		return JavaProfile.getInstance().getSourceVersion(getTarget(), getModuleVersion());
	//	}
	//
	//	public JavaVersion		getJavaTargetLevel() {
	//		return JavaProfile.getInstance().getTargetVersion(getTarget(), getModuleVersion());
	//	}

	//	public String		getEncoding() {
	//		return JavaProfile.getInstance().getProperty(getTarget(), getModuleVersion(), Profile.ENCODING);
	//	}
	public boolean	getJavaDebug() {
		return true;
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Inter-module resource references.					*/
	/*--------------------------------------------------------------*/

	public void		addExternalReference(String moduleName, String fileName) {
		for(ModuleFileRef r : m_externalRefList) {
			if(r.getModule().equals(moduleName) && r.getRelpath().equals(fileName))
				return;						// Already added.
		}

		m_externalRefList.add(new ModuleFileRef(moduleName, fileName));
	}

	/**
	 * Called after all modules have been retrieved from the repository, this
	 * will resolve all external module references and turns them into actual
	 * file references. All references should be done to modules in the
	 * <i>exported</i> list of the module we depend on; if not the module we
	 * depend on can decide what to do with the reference. If it decides to add
	 * the module to it's external reference list it <i>must</i> decide to build
	 * if the new external has not been saved as a dependency.
	 *
	 * @see to.etc.saram.bld.maker.ModuleMaker#resolveExternalDependencies(to.etc.saram.executor.log.Reporter)
	 */
	public boolean resolveExternalDependencies(Reporter r) throws Exception {
		boolean error = false;
		for(ModuleFileRef fr : m_externalRefList) {
			//-- Find the module we're depending on
			ModuleBuildInfo	dbi = findDirectDependency(fr.getModule());		// Must be able to find the module,
			if(dbi == null) {
				error = true;
				r.error("Can't find module '"+fr.getModule()+"' to resolve a dependency on "+fr.getRelpath());
			} else {
				//-- Module found. Ask the module's maker for a formal reference to the thingy.
				Product	prod = dbi.getMaker().resolveExportedResource(dbi.getName(), fr.getRelpath());	// Ask the module to resolve this
				if(prod == null) {
					error = true;
					r.error("Can't find exported resource "+fr.getRelpath()+" in module '"+fr.getModule());
				} else {
					//-- Actually *got* a module-> add to my internal classpath list.
					getMaker().addResolvedExternal(fr, prod);
				}
			}
		}
		return ! error;
	}

	public ModuleBuildStatus getBuildStatus() {
		return m_buildStatus;
	}

	public void setBuildStatus(ModuleBuildStatus buildStatus) {
		m_buildStatus = buildStatus;
	}
}
