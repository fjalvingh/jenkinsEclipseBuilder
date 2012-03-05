package to.etc.prjbuilder.maker;

import java.io.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.delta.*;
import to.etc.prjbuilder.util.*;

public class EclipseModuleMaker extends EclipseModuleMakerBase {
	public EclipseModuleMaker(File root, String name) {
		super(root, name);
	}

	public String getName() {
		return "Eclipse Module Builder";
	}

	@Override
	public boolean initialize(Reporter r, ModuleBuildInfo bi, BuildIntentType brt) throws Exception {
		super.initialize(r, bi, brt);
		if(getEclipseProjectDefinition()) {
			readExtraSettings();
			finishProjectDefinition();
			return true;
		}
		return false;
	}

	protected void	finishProjectDefinition() throws Exception {
		//-- If we've decoded everything then check if we'll produce Java builder output.
		if(getSourcesList().size() > 0) {
			//-- Make a binary output directory to collect classes
			File bd = new File(getOutputRoot(), "bin");
			bd.mkdirs();
			setClassesBinDir(bd);
//			addGeneratedName("bin");
		}

		/*
		 * Must we make a .jar file for this project?
		 */
		if(getClassesBinDir() != null) {
			//-- Generate a .jar product as output.
			String name = makeRealProjectName() + ".jar";
			File	f = new File(getOutputRoot(), name);
			GeneratedJarProduct jp = new GeneratedJarProduct(getBuildInfo(), f, name, getClassesBinDir());
			addGeneratedProduct(jp); // Add the jar we'll generate
//			addGeneratedName(name);
		}
	}

	/**
	 * Main entrypoint.
	 * @see to.etc.saram.bld.maker.EclipseModuleMakerBase#buildModule(java.io.Writer)
	 */
	public void buildModule(Writer buildlogger) throws Exception {
		if(! compileSources(buildlogger))					// Compile sources
			return;
		generateProducts();									// Create simple .jars

	}

	public DeltaBuilder createDelta(File olddir, File newdir) throws Exception {
		throw new IllegalStateException("Don't know how to create a delta for common Eclipse modules");
	}

}
