package to.etc.prjbuilder.maker;

import java.io.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.util.*;
import to.etc.util.*;

public class GeneratedJarProduct extends JarProduct implements GeneratedProduct {
	/** The inventory of files that construct this product, if applicable. Only present for products created by the build. */
	private File			m_sourceDir;

	public GeneratedJarProduct(ModuleBuildInfo source, File targetjar, String name, File srcdir) {
		super(source, targetjar, name);
		m_sourceDir = srcdir;
	}

	/**
	 * Actually generate the product after the build. This creates the actual .jar file from
	 * the path passed as source, and it creates the inventory for the jar for use by the file
	 * patcher.
	 *
	 * @see to.etc.saram.bld.maker.GeneratedProduct#generate()
	 */
	@Override
	public void generate(ModuleBuildInfo root, Reporter r) throws Exception {
		FileTool.zip(getFile(), m_sourceDir);
		//		PuzzlerUtil.zip(getFile(), m_sourceDir, 1, "Build " + root.getCurrBuild().getBuildRun().getBuildNr() + " at " + root.getCurrBuild().getBuildRun().getStartTime() + " of " + root);
	}
}
