package to.etc.prjbuilder.maker;


import java.io.*;
import java.util.*;

import org.w3c.dom.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.util.*;
import to.etc.util.*;
import to.etc.xml.*;

/**
 * A builder which knows how to build Eclipse projects. This exists only during a run.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 15, 2007
 */
abstract public class EclipseModuleMakerBase implements ModuleMaker {
	private final File m_sourceRootDir;
	private final String			m_moduleName;

	private ModuleBuildInfo			m_bi;
	private Reporter				m_reporter;

	private Set<String> m_buildSourceList = new HashSet<String>();

	static public class SourcePath {
		public File		file;
		public String	relpath;
		public String	output;
		public File		outputPath;

		public SourcePath(File file, String relpath, String output, File outPath) {
			this.file = file;
			this.relpath = relpath;
			this.output = output;
			this.outputPath = outPath;
		}
	}

	private final Set<String> 	    m_dirdep_map = new HashSet<String>();

	/** The list of source locations to compile, as read from the .classpath file. */
	private final List<SourcePath> 	m_sources_list = new ArrayList<SourcePath>();

	/** The list of locally-used resources (jars in the classpath) */
	private List<Product> 		m_localProductList;

	/** The list of output products as defined by the classpath exports, EXCLUDING the result of this build */
	private List<Product> 		m_outputProductList;

	/** The classpath-ordered list of products, sum of output and local lists in classpath order */
	private List<Product> 		m_classpathProductList;

	private List<GeneratedProduct> 	m_generatedProductList;

	/** This will contain all BUILD results in a format suitable for output. It is the 'image' directory below this-module's OutputRoot. */
	private File				m_imageDir;

	/** The primary target output directory, */
	private File				m_classesBinDir;
	private File 				m_buildFile;

	private final List<File> 	m_classPathList = new ArrayList<File>();

	/** If set the version to use for compilation */
	private JavaVersion			m_sourceVersion;

	private JavaVersion m_jdkVersion;

	/** If set the target output version */
	private JavaVersion			m_targetVersion;

	private String				m_encoding;

	private BuildIntentType m_buildIntent;

	public EclipseModuleMakerBase(File root, String name) {
		m_sourceRootDir = root;
		m_moduleName	= name;
	}
	public String getModuleName() {
		return m_moduleName;
	}

	public File getSourceRoot() {
		return m_sourceRootDir;
	}
	public File	getOutputRoot() {
		return m_bi.getOutputDir();
	}
	public final File	getImageDir() {
		return m_imageDir;
	}

	final public BuildIntentType getBuildIntent() {
		return m_buildIntent;
	}

	public Set<String> getBuildSources() {
		return m_buildSourceList;
	}

	private void addBuildSource(String relpath) {
		m_buildSourceList.add(relpath);
	}

	/**
	 * Gets a file from the module's root source dir. The file IS added as a source build dependency.
	 * @param path
	 * @return
	 */
	protected File getModuleFile(String path) {
		while(path.startsWith("/"))
			path = path.substring(1);
		addBuildSource(path);
		return new File(getSourceRoot(), path);
	}

	public boolean initialize(Reporter r, ModuleBuildInfo bi, BuildIntentType brt) throws Exception {
		m_bi	= bi;
		m_buildIntent = brt;
		m_reporter = r;
		m_outputProductList = new ArrayList<Product>();
		m_localProductList = new ArrayList<Product>();
		m_generatedProductList = new ArrayList<GeneratedProduct>();
		m_classpathProductList	= new ArrayList<Product>();
		m_classPathList.clear();
		m_sources_list.clear();
		m_imageDir = new File(bi.getOutputDir(), ".image");
		return true;
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Reading the Eclipse Configuration from files.		*/
	/*--------------------------------------------------------------*/
	/**
	 * Reads the Eclipse project files to make the dependency list. Returns
	 * T if a valid eclipse config was found.
	 *
	 * @throws Exception
	 */
	protected boolean getEclipseProjectDefinition() throws Exception {
		//	    log("get EclipseProjectDefinition");
		m_dirdep_map.clear();

		File pf = getModuleFile(".project");
		File cf = getModuleFile(".classpath");
		if(!pf.exists())
			throw new MakeConfigException(smv(), "Missing " + pf);
		if(!cf.exists())
			throw new MakeConfigException(smv(), "Missing " + cf);

		//-- Handle the .project file and add all modules contained herein
		Document	doc = getDoc(pf);
		Node pdn = DomTools.nodeFind(doc, "projectDescription");
		if(pdn == null)
			throw new MakeConfigException(smv(), "The eclipse project file does not contain a projectDescription");
		Node prjs = DomTools.nodeFind(pdn, "projects");
		if(prjs == null)
			throw new MakeConfigException(smv(), "The eclipse project file does not contain a projectDescription");

		//-- Get the .classpath file
		//		log("Decoding classpath dependencies");
		doc = getDoc(cf);
		Node cpn = DomTools.nodeFind(doc, "classpath");
		if(cpn == null)
			throw new MakeConfigException(smv(), "The eclipse .classpath file does not contain a 'classpath' node");
		NodeList nl = cpn.getChildNodes();
		for(int i = 0; i < nl.getLength(); i++) {
			Node cpe = nl.item(i);
			if(cpe.getNodeName().equalsIgnoreCase("classpathentry")) {
				decodeClasspathDependency(cpe);
			}
		}
		return true;
	}

	/**
	 * Reads the extra files that determine the .java version to compile
	 * against, and the source file encoding.
	 *
	 * @throws Exception
	 */
	protected void	readExtraSettings() throws Exception {
		File settings = getModuleFile(".settings"); // Adds .settings as dependency
		if(settings.exists() && settings.isDirectory()) {
			File	f = new File(settings, "org.eclipse.jdt.core.prefs");
			if(f.exists())
				decodeCompilerSettings(f);

			f	= new File(settings, "org.eclipse.core.resources.prefs");
			if(f.exists())
				decodeEncoding(f);
		}
	}

	/**
	 * Decodes the .classpath file and retrieves the source list and all
	 * project dependencies for this project. In addition, if any attached
	 * libraries have the "export" flag then they get exported as a
	 * build product.
	 * @param ctx
	 * @param n
	 * @throws Exception
	 */
	private void decodeClasspathDependency(Node n) throws Exception {
		String kind = DomTools.getNodeAttribute(n, "kind", null);
		if(kind == null)
			return;
		kind = kind.toLowerCase();
		String exps = DomTools.getNodeAttribute(n, "exported", "false");
		boolean export = exps.toLowerCase().startsWith("t");
		String path = DomTools.getNodeAttribute(n, "path", null);
		if(path == null)
			return;
		if("src".equals(kind)) {
			//-- Source entries dependency..
			String output = DomTools.strAttr(n, "output", null);
			File	outfile = null;
			if(output != null && output.trim().length() > 0) {
				outfile = new File(getImageDir(), output);
			}

			if(!path.startsWith("/")) {
				//-- This is an actual source directory. Add it.
				File f = getModuleFile(path); // Make a source location,
				if(!f.exists())
					r().log("The source location '" + f + "' as specified in the eclipse .classpath does not exist");
				addSource(path, f, output, outfile);
				return;
			}

			//-- This is a workspace-relative project specifier.
			String module = path.substring(1); // Get actual module name
			addDirectDep(module);
			return;
		}
		if("lib".equals(kind)) {
			//-- If path starts with / this refers to a lib somewhere else in the workspace..
			if(path.startsWith("/")) {
				//-- workspace reference.. Get the project referred to and add,
				int pos = path.indexOf('/', 1);			// 2nd slast '/lib-xxx-1.12.0/'
				if(pos == -1) {
					//-- Unexpected pathless - just add as dependency
					String module = path.substring(1);	// Dependent module name
					addDirectDep(module);
					return;
				}

				String module = path.substring(1, pos);	// The module..
				addDirectDep(module);			// add as dependency

				//-- Treat this as an external reference: added to the classpath but no "product"
				m_bi.addExternalReference(module, path.substring(pos+1));
				return;
			}

			//-- This-project-local ref
			File f = getModuleFile(path);
			if(!f.exists())
				r().log("The library file '" + f + "' defined in the eclipse .classpath does not exist.");
			else {
//				SourceJarProduct jp = new SourceJarProduct(f, path);
                JarProduct jp = new JarProduct(m_bi, f, path);
				if(export)
					addOutputProduct(jp);
				else
					addLocalProduct(jp);
				addClasspathProduct(jp);
			}
		}
	}


	/**
	 * This should return a reason string if this maker requires the target to be built. The maker
	 * usually checks for the build's artifacts to exist; if not it returns a description string. If
	 * the maker decides the current state is OK it returns null. This gets called only if the
	 * build engine has no *other* reason to build.
	 * The eclipse version checks for the generated artifacts of the build and returns a reason string
	 * if any of them are missing.
	 *
	 * @return
	 */
	public String mustBeBuilt() {
		StringBuilder sb = new StringBuilder();
		for(GeneratedProduct gp : m_generatedProductList) {
			if(gp instanceof FileProduct) {
				FileProduct fp = (FileProduct) gp;
				if(! fp.getFile().exists()) {
					if(sb.length() > 0)
						sb.append(',');
					else
						sb.append("Missing ");
					sb.append(fp.getName());
				}
			}
		}
		if(sb.length() > 0)
			return sb.toString();

		return null;
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	External dependency checking/resolution.			*/
	/*--------------------------------------------------------------*/

	/**
	 * Called when a module that depends on me needs one of my resources. This
	 * must resolve the resource as a product, hopefully one in my exported products
	 * list; if it's not there this checks to see if we can export the required
	 * product and if so we export it at this time. This may cause this module to
	 * be rebuilt if the earlier copy of this module had not exported the resource.
	 *
	 */
	public Product resolveExportedResource(String requestingProject, String resourceName) throws Exception {
		//-- Check exported file list.
		for(Product p : m_outputProductList) {
			if(p.getName().equals(resourceName)) {
				return p;
			}
		}

		//-- Not there. Is it in the generated products list perhaps?
		for(GeneratedProduct p : m_generatedProductList) {
			if(p.getName().equals(resourceName)) {
				return p;
			}
		}

		/*
		 * We're in trouble- the thingy does not currently exist as an exported
		 * resource. Does the thingy exist in the local resource list?
		 */
		for(Product p : m_localProductList) {
			if(p.getName().equals(resourceName)) {
				/*
				 * Gotcha. Remove from the "local" product list and add to the exported
				 * list after reporting a warning.
				 */
				m_localProductList.remove(p);			// Remove from local,
				m_outputProductList.add(p);				// Add to exported list,
				warning("Reference to unexported product "+resourceName+" from project "+requestingProject);
				return p;
			}
		}

		/*
		 * It's not even a local resource....... As a last resort try to find it literally. For
		 */
		File src = getModuleFile(resourceName);
		if(src.exists() && src.isFile()) {
			if(! resourceName.toLowerCase().endsWith(".jar")) {
				throw new Exception("Reference to non-jar resource '"+resourceName+"' by project '"+requestingProject+"'");
			}

			//-- Add this as an exported jar after a dire warning
			warning("Reference to unexported AND NON-LOCALLY USED PRODUCT "+resourceName+" from project "+requestingProject+": FIX YOUR ECLIPSE BUILD FILES!");
			JarProduct	jp = new JarProduct(m_bi, src, resourceName);
			addOutputProduct(jp);
			return jp;
		}

		return null;									// Cannot provide.
	}

	/**
	 * The dependencies I had with other modules cause callbacks here when they have been
	 * resolved (because the other module loaded). For Eclipse modules we expect JAR's
	 * as externals; the external jars are added to the internal classpath for compilation.
	 *
	 * @see to.etc.saram.bld.maker.ModuleMaker#addResolvedExternal(to.etc.saram.bld.builder.ModuleFileRef, to.etc.saram.bld.maker.Product)
	 */
	public void addResolvedExternal(ModuleFileRef r, Product p) throws Exception {
		m_classpathProductList.add(p);				// Append the product
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Building.											*/
	/*--------------------------------------------------------------*/
	/*
	 * Building works by generating an ANT buildfile for the module
	 * from the module's configuration and dependencies. It uses the
	 * .classpath file to find all sources and outputs.
	 * @returns T if sources were compiled succesfully. Returning F does not
	 * 			mean we're in error; it merely indicates that a compile was
	 * 			not necessary.
	 */
	protected boolean compileSources(Writer buildlogger) throws Exception {
		//-- 1. Are there sources to compile? If not this is a binary module and we're done.
		if(m_sources_list.size() == 0) {
//			if(getExportList().size() == 0)
//				ctx.getReporter().msg("Module "+this+" seems nonsense: it does not contain source nor does it export anything...");

			r().log(smv() + ": this module is export-only and need not be compiled.");
			//			for(Product p : m_outputProductList) {
			//				if(p instanceof GeneratedProduct) {
			//					((GeneratedProduct) p).generate(m_bi, m_reporter);
			//				}
			//			}
			return false;
		}
		long ts = System.nanoTime();
		System.out.println("Compiling "+this+" in "+getEncoding()+" encoding, -source "+getSourceLevel()+" ("+getName()+")");
		r().important("Compiling "+this+" in "+getEncoding()+" encoding, -source "+getSourceLevel()+" ("+getName()+")");
		cleanPrevious();

		//		r().detail("Creating the Eclipse build path");
		makeClassPath();
		getClassesBinDir().mkdirs();

		//		boolean ok = runAntBuilder(buildlogger);
		boolean ok = runECJBuilder(buildlogger);

		ts = System.nanoTime() - ts;
		r().detail("Compilation completed in "+StringTool.strNanoTime(ts));
		System.out.println("  compilation completed in "+StringTool.strNanoTime(ts));
		return ok;
	}

	/**
	 * Generate all of the output products if the build has completed succesfully.
	 */
	protected void	generateProducts() throws Exception {
		for(GeneratedProduct gp : m_generatedProductList)
			gp.generate(m_bi, r());
	}

	private File getClasspathFile(Product p) {
		if(p instanceof JarProduct) {
			JarProduct jp = (JarProduct) p;
			return jp.getFile();
		}
		if(p instanceof ModuleFileRef) {
			ModuleFileRef	ref = (ModuleFileRef)p;

			//-- FIXME Lookup the module; it must be a direct dependency
			for(ModuleBuildInfo bi : m_bi.getDirectDependencyList()) {
				if(bi.getModuleVersion().getName().equals(ref.getModule())) {
					//-- Gotcha...
					File	rf = new File(bi.getSourceRoot(), ref.getRelpath());
					if(! rf.exists()) {
						//-- sheet
						r().error("Missing module-reference: file "+ref.getRelpath()+" in module "+ref.getModule()+" does not exist.");
					}
					return rf;
				}
			}
			throw new IllegalStateException("Internal: cannot find BuildInfo for direct dependency!?");
		}

		return null;
	}

	private void makeClassPath() {
		m_classPathList.clear();
		for(Product p : m_classpathProductList) {
			File f = getClasspathFile(p);
			if(f != null)
				m_classPathList.add(f);
		}

		OrderedBuildList bl = m_bi.getFullDependencyList();
		for(ModuleBuildInfo tm : bl) {
			List<Product> full = new ArrayList<Product>(tm.getMaker().getExportedProductList());
			full.addAll(tm.getMaker().getGeneratedProductList());
			for(Product p : full) {
				File f = getClasspathFile(p);
				if(f != null)
					m_classPathList.add(f);
			}
		}
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	'ant' based builds.									*/
	/*--------------------------------------------------------------*/
	/**
	 *
	 * @param logger
	 * @return
	 * @throws Exception
	 */
	private boolean runAntBuilder(Writer logger) throws Exception {
		//-- We'll generate some build-related scripts into the module's directory.
		//		r().detail("Generating the builder properties");
		generateAntProperties();

		//-- Create an ant buildfile to compile the thingy.
		//		r().detail("Creating the builder's 'ant' file");
		makeAntFile();
		return runAndBasedBuilder(logger);
	}

	private void makeAntFile() throws Exception {
		File af = new File(getOutputRoot(), ".compile.xml");		// FIXME Must be in working dir, not source nor target
		af.delete();
		m_buildFile = af;
//		System.out.println("build file is " + af);
		XmlWriter xw = new XmlWriter();
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(af)));
		try {
			xw.init(pw, 0);
			pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			xw.tagnl("project", new String[]{"name", makeRealProjectName(), "default", "compile", "basedir", getSourceRoot().toString()});

//			if(m_webProd != null)
//				xw.tagonly("import", "file", "/home/jal/apache-tomcat-6.0.14/bin/catalina-tasks.xml");
//			if(m_webProd != null)
//				xw.tagonly("import", "file", "${tomcat.home}/bin/catalina-tasks.xml");

			//-- Generate an INIT section
			xw.tag("target", new String[]{"name", "init"});
            xw.append("\n");
			xw.tagonly("property", "name", "outputPath", "value", getClassesBinDir().toString());
			xw.append("\n");
            xw.tagonly("property", "name", "basePath", "value", m_bi.getSourceRoot().toString());

			xw.tagonly("delete", "dir", getClassesBinDir().toString());
			xw.tagonly("mkdir", "dir", getClassesBinDir().toString());

			//-- Generate the dependents' classpath
			xw.tag("path", new String[]{"id", "project.class.path"});

			//-- Do dependencies in reverse order,
			for(int i = m_classPathList.size(); --i >= 0;) {
				File f = m_classPathList.get(i);
				xw.tagonly("pathelement", "path", f.toString());
			}
			xw.tagendnl(); 						// end path
			xw.tagendnl(); 						// end target 'init'

			//-- Generate the 'compile' section,
			xw.tagnl("target", "name", "compile", "depends", "init");

			boolean	debug = m_bi.getJavaDebug();

			//-- Compile all sources with a specific source path
			int      numglob = 0;
            for(SourcePath srcf : m_sources_list) {
                if(srcf.output == null)
                    numglob++;
                else {
                    //-- Source with a specific output path -> write,
//                	File tgt = new File(m_sourceDir, srcf.output);			// FIXME VP cludge for applet
//                	tgt.mkdirs();
                	srcf.outputPath.mkdirs();
					//                    xw.tagnl("javac", new String[] {
					//                            "destDir", srcf.outputPath.toString(),
					//                            "classpathref", "project.class.path",
					// "nowarn", "on",
					//                            "debug", debug ? "on" : "off",
					//                            "encoding", getEncoding(),
					//                            "target", getTargetLevel().toString(),
					//                            "source", getSourceLevel().toString()
					//                    });
					generateAntCompile(xw, srcf.outputPath, debug, getEncoding(), getSourceLevel(), getTargetLevel());

                    xw.tagonly("src", "path", srcf.file.toString());

					//-- Steer warnings from the eclipse compiler..
//					xw.tagonly("compilerarg", "compiler", "org.eclipse.jdt.core.JDTCompilerAdapter", "line", "-nowarn -warn:none");
                    xw.tagendnl();

                    xw.tagnl("copy", new String[]{"todir", srcf.outputPath.toString()});
                    xw.tagnl("fileset", new String[]{"dir", srcf.file.toString()});
                    xw.tagonly("exclude", new String[]{"name", "**/*.java"});
                    xw.tagendnl();                  // end fileset
                    xw.tagendnl();                  // end copy
                }
            }

            if(numglob > 0) {
				generateAntCompile(xw, getClassesBinDir(), debug, getEncoding(), getSourceLevel(), getTargetLevel());
				//    			xw.tagnl("javac", new String[] {
				//    					"destDir", "${outputPath}",
				//    					"classpathref", "project.class.path",
				//    					"debug", debug ? "on" : "off",
				// "nowarn", "on",
				//    					"encoding", getEncoding(),
				//    					"target", getTargetLevel().toString(),
				//    					"source", getSourceLevel().toString()
				//    			});
				//				xw.tagonly("compilerarg", "compiler", "org.eclipse.jdt.core.JDTCompilerAdapter", "line", "-nowarn -warn:none");

    			//-- Generate all source paths
    			for(SourcePath srcf : m_sources_list) {
    			    if(srcf.output == null)
    			        xw.tagonly("src", "path", srcf.file.toString());
    			}
    			xw.tagendnl(); 						// End of javac
            }

			//-- Copy all other shit into output
			// TODO: Must make this a system parameter
			xw.tagnl("copy", new String[]{"todir", "${outputPath}"});
			for(SourcePath srcf : m_sources_list) {
			    if(srcf.output != null)
			        continue;
				xw.tagnl("fileset", new String[]{"dir", srcf.file.toString()});
				xw.tagonly("exclude", new String[]{"name", "**/*.java"});
				xw.tagendnl();					// end fileset
			}
			xw.tagendnl();						// end copy

//			if(m_webProd != null) {
//				File	outdir = new File(m_sourceDir, "WebSrc");
//				outdir.mkdirs();
//
//				for(PublishPath pp : m_webProd.getPublishList()) {
//					if(pp.getType() != PublishType.pptWEB)
//						continue;
//					xw.tagonly("jasper", "validateXml","false", "uriroot", pp.getSource().toString(), "webXmlFragment", "/tmp/generated.xml", "outputDir", outdir.toString());
//				}
//			}

			xw.tagendnl();						// end target 'compile'


			xw.tagendnl(); 						// End Project
		}
		finally {
			try { pw.close(); } catch(Exception x) {}
		}
	}

	private void generateAntCompile(XmlWriter xw, File output, boolean debug, String encoding, JavaVersion source, JavaVersion target) throws Exception {
		StringBuilder sb = new StringBuilder();
		for(File jar : getJdkJars(source)) {
			if(sb.length() > 0)
				sb.append(File.pathSeparator);
			sb.append(jar.toString());
		}
		String libpath = sb.toString();

		xw.tagnl("javac", new String[]{"destDir", output.toString(), //
			"bootclasspath", libpath, //
			"classpathref", "project.class.path", //
			"nowarn", "on", //
			"debug", debug ? "on" : "off", //
			"encoding", getEncoding(), //
			"target", target.toString(), //
			"source", source.toString() //
			});
		//		xw.tagonly("src", "path", srcf.toString());

		//-- Steer warnings from the eclipse compiler..
		xw.tagonly("compilerarg", "compiler", "org.eclipse.jdt.core.JDTCompilerAdapter", "line", "-nowarn -warn:none");
		//		xw.tagendnl();
	}

	private void generateAntProperties() throws Exception {
		Properties	p = new Properties();
		p.setProperty("project.version", "1.0");
		p.setProperty("bindir", new File(getImageDir(), "bin").toString());

		//-- Get all of the java outputs on this thing.
		StringBuilder sb = new StringBuilder();
		for(File f : m_classPathList) {
			if(sb.length() > 0)
				sb.append(File.pathSeparatorChar); 		// Path separator
			sb.append(f.toString());
		}
		p.setProperty("classpath", sb.toString());
		FileTool.saveProperties(new File(getOutputRoot(), "saram.project.properties"), p);
	}


	private boolean runAndBasedBuilder(Writer buildoutput) throws Exception {
		List<String> args = new ArrayList<String>();
//		String ant = "ant -Dbuild.compiler=org.eclipse.jdt.core.JDTCompilerAdapter ";		// FIXME Must come from system config
		String ant = "ant";		// FIXME Must come from system config
		args.add(ant);
		args.add("-Dbuild.compiler=org.eclipse.jdt.core.JDTCompilerAdapter");
		args.add("-f");
		args.add(m_buildFile.toString());

		//		r().detail("Starting 'ant' using: " + args);

		//-- Create the info writer files.
		ProcessBuilder pb = new ProcessBuilder(args);

		int rc = -1;
		try {
			rc = ProcessTools.runProcess(pb, buildoutput);
		} finally {
			try {
				buildoutput.close();
			} catch(Exception x) {
				r().exception(x, "Can't close build log writer");
			}
		}
		if(rc != 0) {
			r().error("The build for " + smv() + " has failed: the exitcode was " + rc);
			m_bi.setBuildError("The compilation process has failed with exitcode "+rc);
		}
		else {
			//			r().detail("The build process for '" + smv() + "' has completed succesfully.");
		}
		return rc == 0;
	}

	public MessageFilter getFilterChain(MessageFilter folr) {
		return new ECJMultilineMessageFilter(folr);
	}

	private void	decodeCompilerSettings(File f) throws Exception {
		Properties	p = FileTool.loadProperties(f);

		//-- Source compliance level (1.2, 1.3, 1.4, 1.5, 1.6)
		String	c = p.getProperty("org.eclipse.jdt.core.compiler.compliance");
		if(c != null) {
			m_sourceVersion = JavaVersion.byName(c.trim());
			m_jdkVersion = m_sourceVersion;
			checkJdkVersion();
		}

		c = p.getProperty("org.eclipse.jdt.core.compiler.codegen.targetPlatform");
		if(c != null) {
			m_targetVersion = JavaVersion.byName(c.trim());
		}
		//		m_targetVersion = JavaVersion.adjustTargetVersion()
	}

	private void checkJdkVersion() {
		if(m_bi.getConfiguration().findJdkRoot(m_sourceVersion) == null) {
			//-- Try to find a HIGHER version but report a warning
			JavaVersion next = null;
			int ix = m_sourceVersion.ordinal() + 1;
			while(ix < JavaVersion.values().length) {
				if(m_bi.getConfiguration().findJdkRoot(JavaVersion.values()[ix]) != null) {
					next = JavaVersion.values()[ix];
					break;
				}
				ix++;
			}

			if(next == null) {
				String s = "No " + m_sourceVersion + " JDK found, and no viable alternative was available";
				r().logRecord(LogLineType.ERR, s);
				throw new BuildException(s);
			}
			r().logRecord(LogLineType.IMP, "Replacing JDK " + m_sourceVersion + " with a " + next + " JDK for  " + m_bi.getName());
			m_jdkVersion = next;
		}
	}

	private void	decodeEncoding(File f) throws Exception {
		Properties	p = FileTool.loadProperties(f);
		String s = p.getProperty("encoding/<project>");
		if(s != null) {
			m_encoding = s;
		}
	}

	protected JavaVersion	getTargetLevel() {
		JavaVersion tv = m_targetVersion != null ? m_targetVersion : m_bi.getConfiguration().getDefaultTargetVersion();
		return JavaVersion.adjustTargetVersion(getSourceLevel(), tv);
	}
	protected JavaVersion	getSourceLevel() {
		if(m_sourceVersion != null)
			return m_sourceVersion;
		return m_bi.getConfiguration().getDefaultSourceVersion();
	}

	protected JavaVersion getJdkLevel() {
		if(m_jdkVersion == null) {
			m_jdkVersion = getSourceLevel();
			checkJdkVersion();
		}
		return m_jdkVersion;
	}

	protected String	getEncoding() {
		if(m_encoding!=null)
			return m_encoding;
		return m_bi.getConfiguration().getDefaultEncoding();
	}

	private List<File> getJdkJars(JavaVersion jdk) {
		File jdkroot = m_bi.getConfiguration().getJdkRoot(jdk);
		File jre = new File(jdkroot, "jre/lib");
		List<File> jars = new ArrayList<File>();
		collectJars(jars, jre);
		collectJars(jars, new File(jre, "ext"));
		File f = new File(jdkroot, "lib/tools.jar");
		jars.add(f);
		return jars;
	}

	private void collectJars(List<File> jars, File jre) {
		File[] ar = jre.listFiles();
		for(File f : ar) {
			if(f.getName().endsWith(".jar"))
				jars.add(f);
		}
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Sillyness..											*/
	/*--------------------------------------------------------------*/
	protected void   log(String s) {
//	    System.out.println(m_bi.getModuleVersion().getModule().getName()+": "+s);
	}

	protected void addSource(String relpath, File f, String output, File outpath) {
	    log("add source "+f);

	    /*
	     * Check to see if this actually contains anything to be compiled... If not the source
	     * will not get added.
	     */
	    if(! hasSourceFiles(f)) {
	    	log("The source directory "+relpath+" was not added because it's empty.");
	    	return;
	    }

		m_sources_list.add(new SourcePath(f, relpath, output, outpath));
	}

	public List<SourcePath> getSourcesList() {
		return m_sources_list;
	}


	/**
	 * Helper which checks to see if a folder has any sources.
	 * @param dir
	 * @return
	 */
	static boolean	hasSourceFiles(File dir) {
		if(! dir.exists())
			return false;
		File[] far = dir.listFiles();
		for(File sf : far) {
			if(BuildUtil.isHiddenDirectory(sf.getName()))
				continue;
			if(sf.isFile())
				return true;
			else {
				if(hasSourceFiles(sf))
					return true;
			}
		}
		return false;
	}

	protected void   addDirectDep(String v) {
	    log("add direct dependency: "+v);
	    m_dirdep_map.add(v);
	}
	protected void   addLocalProduct(Product p) {
        log("add local product: "+p);
	    m_localProductList.add(p);
	}
	protected void   addOutputProduct(Product p) {
        log("add output product: "+p);
        m_outputProductList.add(p);
		if(p instanceof JarProduct) {
			((JarProduct) p).setExported(true);
		}
    }
    protected void    addClasspathProduct(Product p) {
        log("add classpath product: "+p);
        m_classpathProductList.add(p);
    }
    protected void    addGeneratedProduct(GeneratedProduct p) {
        log("add generated product: "+p);
        m_generatedProductList.add(p);
    }
//    protected void	addGeneratedName(String name) {
//    	m_generatedNames.add(name);
//    }

	/**
	 * Return the complete results for this build.
	 * @see to.etc.saram.bld.maker.ModuleMaker#getProductList()
	 */
	public List<GeneratedProduct>	getGeneratedProductList() {
		return m_generatedProductList;
	}

	public List<Product>	getExportedProductList() {
		return m_outputProductList;
	}

	public Set<String> getDirectDependencies() throws Exception {
		return m_dirdep_map;
	}

	@Override
	public String toString() {
		return smv().toString();
	}

	public SourceModule	smv() {
		return m_bi.getModuleVersion();
	}

	public void		clean() {
		if(m_sources_list.size() > 0) {
			//-- delete bin dir
			FileTool.deleteDir(getClassesBinDir());
		}
	}

	public Document	getDoc(File f) throws Exception {
		try {
			return DomTools.getDocument(f, false);
		} catch(Exception x) {
			r().exception(x, "While loading xml document "+f);
			throw new MakeConfigException(smv(), f+": "+x);
		}
	}

	/**
	 * Uses the project's actual name to derive a target .jar name to contain the files
	 * compiled by this project.
	 *
	 * @return
	 */
	protected String makeRealProjectName() {
		String name = smv().getName();
		if(name.startsWith("lib-") || name.startsWith("bin-") || name.startsWith("lib.") || name.startsWith("bin."))
			name = name.substring(4);
		int epos = name.length();
		int ldash = epos;
		while(epos > 0) {
			char c = name.charAt(--epos);
			if(c == '-' || c == '_') {
				ldash = epos;
			} else if(!Character.isDigit(c) && c != '.')
				break;
		}
		name = name.substring(0, ldash);
		return name;
	}
	protected Reporter	r() {
		return m_reporter;
	}

	private void	warning(String s) {
		// FIXME Need impl.
	}
	protected void setClassesBinDir(File classesBinDir) {
		m_classesBinDir = classesBinDir;
	}
	protected File getClassesBinDir() {
		return m_classesBinDir;
	}
	public ModuleBuildInfo getBuildInfo() {
		return m_bi;
	}

	protected void cleanPrevious() {
		FileTool.deleteDir(getImageDir());
		getImageDir().mkdirs();
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Embedded ECJ compiler.								*/
	/*--------------------------------------------------------------*/
	/**
	 * Runs ECJ directly on the source.
	 * @param buildlogger
	 * @return
	 */
	private boolean runECJBuilder(Writer buildlogger) throws Exception {
		List<String> args = new ArrayList<String>();		// Command line for ecj

		//-- 1. Compile all sources that have the default output path
		createECJDefaultArguments(args);
		args.add("-d");
		args.add(getClassesBinDir().toString());
		for(SourcePath srcf : m_sources_list) {
			if(srcf.output == null) {
				args.add(srcf.file.toString());
			}
		}
		if(!runECJ(buildlogger, args))
			return false;

		//-- 2. Compile all sources with a separate output path FIXME Needs some kind of order applied?
		for(SourcePath srcf : m_sources_list) {
			if(srcf.output == null)
				continue;
			args.clear();
			createECJDefaultArguments(args);
			args.add("-d");
			args.add(srcf.outputPath.toString());

			args.add(srcf.file.toString());
			if(!runECJ(buildlogger, args))
				return false;
		}

		//		StringBuilder sb = new StringBuilder();
		//		int      numglob = 0;
		//        for(SourcePath srcf : m_sources_list) {
		//			if(sb.length() > 0)
		//				sb.append(':');
		//			sb.append(srcf.file.toString());
		//
		//			if(srcf.output == null) {
		//                numglob++;
		//			} else {
		//				//-- Source with a specific output path -> write special parameter
		//				srcf.outputPath.mkdirs();
		//				sb.append('[');
		//				sb.append("-d");
		//				sb.append(srcf.outputPath.toString());
		//				sb.append(']');
		//            }
		//        }
		//		String sourcepath = sb.toString();
		//		args.add("-sourcepath");
		//		args.add(sourcepath);

		//		//-- The default output path & all sources going there,

		//		for(SourcePath srcf : m_sources_list) {
		//			if(srcf.output != null) {
		//				//				args.add("-d");
		//				//				args.add(srcf.outputPath.toString());
		//				args.add(srcf.file.toString());
		//			}
		//		}

		//		//-- Handle all files to compile
		//		numglob = 0;
		//		for(SourcePath srcf : m_sources_list) {
		//			sb.setLength(0);
		//			sb.append(srcf.file.toString());
		//			if(srcf.output == null)
		//				numglob++;
		//			else {
		//				sb.append("[-d ");
		//				sb.append(srcf.outputPath.toString());
		//				sb.append(']');
		//			}
		//
		//			args.add(sb.toString());
		//		}

		//-- Copy all resources.
		for(SourcePath sp : m_sources_list) {
			if(sp.output != null)
				copyResources(sp.outputPath, sp.file);
			else
				copyResources(getClassesBinDir(), sp.file);
		}
		return true;
	}

	private boolean runECJ(Writer buildlogger, List<String> args) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		//		System.out.println("ecj compile");
		//		System.out.println(args);
		IJavaCompiler jc = JavaCompiler.getCompiler();
		boolean ok = jc.compile(args.toArray(new String[args.size()]), pw, pw);
//		boolean ok = BatchCompiler.compile(args.toArray(new String[args.size()]), pw, pw, null);
		pw.flush();
		pw.close();
		String res = sw.getBuffer().toString();
		buildlogger.append(res);
		if(!ok) {
			r().error("The ejc compilation for " + smv() + " has failed");
			m_bi.setBuildError("The ejc compilation for " + smv() + " has failed");
			System.out.println("Compile FAILED:\n>>> " + res);
			for(String s : args) {
				System.out.println("arg: " + s);
			}
			return false;
		}
		return true;
	}

	private void createECJDefaultArguments(List<String> args) {
		//-- Encoding and java versions
		args.add("-encoding");
		args.add(getEncoding());
		args.add("-source");
		args.add(getSourceLevel().toString());
		args.add("-target");
		args.add(getTargetLevel().toString());

		//-- Debug and error output
		boolean	debug = m_bi.getJavaDebug();
		if(debug)
			args.add("-g");				// All debug info
		args.add("-nowarn"); // Disable all warnings,

		//-- Create the bootclasspath, defining the JDK to compile /against/
		StringBuilder sb = new StringBuilder(8192);
		for(File jar : getJdkJars(getJdkLevel())) {
			if(sb.length() > 0)
				sb.append(File.pathSeparator);
			sb.append(jar.toString());
		}
		String bootclasspath = sb.toString();
		args.add("-bootclasspath");
		args.add(bootclasspath);

		//-- Create the classpath; do reps in reverse order
		sb.setLength(0);
		for(int i = m_classPathList.size(); --i >= 0;) {
			File f = m_classPathList.get(i);
			if(sb.length() != 0)
				sb.append(File.pathSeparator);
			sb.append(f.toString());
		}
		String classpath = sb.toString();
		args.add("-cp");
		args.add(classpath);
	}

	/**
	 * Copies an entire directory structure from src to dest. This copies the
	 * files from src into destd; it does not remove files in destd that are
	 * not in srcd. Use synchronizeDir() for that.
	 * @param destd
	 * @param srcd
	 * @throws IOException
	 */
	private void copyResources(final File destd, final File srcd) throws IOException {
		if(!srcd.exists())
			return;
		if(srcd.isFile())
			throw new IllegalStateException("Source cannot be a file!?");

		if(destd.exists() && destd.isFile())
			destd.delete();
		destd.mkdirs();

		//-- Right: on with the copy then
		File[] ar = srcd.listFiles();
		for(File sf : ar) {
			String name = sf.getName();
			File df = new File(destd, name);
			if(sf.isFile()) {
				if(df.isDirectory()) // But target is a directory?
					FileTool.deleteDir(df); // Delete it,

				//-- Is this a resource to handle?
				int pos = name.lastIndexOf('.');
				if(pos != -1) {
					String ext = name.substring(pos + 1).toLowerCase();
					if(ext.equals("java"))
						continue;
				}

				FileTool.copyFile(df, sf); // Then copy the file.
			} else if(sf.isDirectory()) {
				if(df.isFile()) // ... but target is a file now?
					df.delete(); // then delete it...
				copyResources(df, sf);
			}
		}
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	JUnit test code.									*/
	/*--------------------------------------------------------------*/

	/**
	 * Return all paths that output is generated in for /this/ module.
	 * @return
	 */
	public List<File> getModuleOutputPaths(boolean testonly) {
		List<File>	res = new ArrayList<File>();
		if(m_classesBinDir != null)
			res.add(m_classesBinDir);
		for(SourcePath sp: m_sources_list) {
			if(!testonly || (sp.relpath.contains("test"))) {
				if(sp.outputPath != null)
					res.add(sp.outputPath);
			}
		}
		return res;
	}

	public List<File> getModuleClasspath() {
		makeClassPath();
		//-- Create the classpath; do reps in reverse order
		List<File> res = new ArrayList<File>();
		for(int i = m_classPathList.size(); --i >= 0;) {
			File f = m_classPathList.get(i);
			res.add(f);
		}
		return res;
	}


}
