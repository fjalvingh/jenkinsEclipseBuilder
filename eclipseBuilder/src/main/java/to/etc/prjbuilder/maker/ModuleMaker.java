package to.etc.prjbuilder.maker;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.delta.*;
import to.etc.prjbuilder.util.*;


/**
 * A ModuleBuilder is a thing which can determine:
 * <ul>
 * 	<li>How to build a module (Ant, Eclipse, old Autobuild?)</li>
 * 	<li>What dependencies a module has (from eclipse files or autobuild.properties)</li>
 * </ul>
 *
 * A single instance gets created for every SourceModuleVersion it maintains. The
 * correct implementation gets selected by a main component.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 15, 2007
 */
public interface ModuleMaker {
	/**
	 * This MUST return a valid module name always.
	 * @return
	 */
	public String			getModuleName();

	/**
	 * This MUST return a valid source root for the module.
	 * @return
	 */
	public File getSourceRoot();

	/**
	 * This must return a list of relative paths within the sourceRoot of files and directories that
	 * are used as sources for this build. It MUST contain all source directories and all settings
	 * files that are used to steer the build. Its primary use is to do the quick "source changed"
	 * check.
	 * @return
	 */
	public Set<String> getBuildSources();

	/**
	 * This returns a name for the Maker itself, i.e. "Eclipse Module Builder".
	 * @return
	 */
	public String			getName();

	public boolean initialize(Reporter r, ModuleBuildInfo bi, BuildIntentType brt) throws Exception;

	public Set<String>		getDirectDependencies() throws Exception;

	/**
	 * Returns the list of products that are <i>generated</i> by this build.
	 * @return
	 */
	public List<GeneratedProduct>	getGeneratedProductList();

	/**
	 * Get the list of products used and exported by this build. This ONLY contains
	 * the products that are <b>not</b> generated by this build.
	 * @return
	 */
	public List<Product>		getExportedProductList();

//	public List<String>			getGeneratedNames();

	public void					clean();

	/**
	 * This should return a reason string if this maker requires the target to be built. The maker
	 * usually checks for the build's artifacts to exist; if not it returns a description string. If
	 * the maker decides the current state is OK it returns null. This gets called only if the
	 * build engine has no *other* reason to build.
	 *
	 * @return
	 */
	public String			mustBeBuilt();
	public void				buildModule(Writer buildlog) throws Exception;

	public MessageFilter	getFilterChain(MessageFilter folr);

	/**
	 * Resolves an inter-module dependency where a dependee on *this* module requires a
	 * named resource from this module after it's build. If the resource is exported this
	 * must return the exported resource for use by the caller which will start to use it
	 * after the build of *this* module.
	 * If the resource is not exported but known to this module this maker should add the
	 * module to the exported list and return it if possible. If exporting the resource
	 * is not possible or allowed this must report an exception containing the reason why
	 * the object could not be exported. This will terminate the build.
	 *
	 * @param resourceName
	 * @return
	 * @throws Exception
	 */
	public Product			resolveExportedResource(String requestingProject, String resourceName) throws Exception;

	/**
	 * Resolves an unresolved external when it is discovered in a dependent module. Gets called when
	 * all modules have been loaded from SCM.
	 *
	 * @param r
	 * @param p
	 * @throws Exception
	 */
	public void				addResolvedExternal(ModuleFileRef r, Product p) throws Exception;

	public DeltaBuilder		createDelta(File olddir, File newdir) throws Exception;

	List<File> getModuleOutputPaths(boolean testonly);

	List<File> getModuleClasspath();
}
