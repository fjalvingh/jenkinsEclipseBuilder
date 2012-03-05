package to.etc.prjbuilder.maker;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import org.w3c.dom.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.delta.*;
import to.etc.prjbuilder.util.*;
import to.etc.util.*;
import to.etc.xml.*;

/**
 * This creates an eclipse Web application from the wtp configuration.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jun 9, 2008
 */
public class EclipseWebMaker extends EclipseModuleMakerBase {
	static public enum PublishType {
		pptCLASS,
		pptFIX,
		pptWEB
	}

	/**
	 * Defines locations in the webapp where data has to be moved to.
	 *
	 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
	 * Created on Jun 9, 2008
	 */
	static final public class PublishPath {
		private final String		m_relTargetPath;
		private final String		m_relSourcePath;
		private final File		m_source;
		private final PublishType	m_type;

		public PublishPath(final PublishType type, final String relTargetPath, final String relSource, final File source) {
			m_type = type;
			m_relTargetPath = relTargetPath;
			m_source = source;
			m_relSourcePath = relSource;
		}
		public String getRelTargetPath() {
			return m_relTargetPath;
		}
		public File getSource() {
			return m_source;
		}
		public PublishType getType() {
			return m_type;
		}
		public String getRelSourcePath() {
			return m_relSourcePath;
		}
	}

	static private class Res {
		public ModuleBuildInfo 	buildInfo;
		public JarProduct		product;
		public Res(final ModuleBuildInfo buildInfo, final JarProduct product) {
			this.buildInfo = buildInfo;
			this.product = product;
		}
	};

	/**  The list of all paths that are to be published into the webapp. */
	private final List<PublishPath>	m_publishPath = new ArrayList<PublishPath>();

	/** A list of private jars to add to the classpath while compiling .jsp's */
	private final List<JarProduct>	m_jsplist = new ArrayList<JarProduct>();

	/** If TRUE all JSP's in the web path are translated and compiled too */
	private final boolean m_checkJSPs = false;

	/**
	 * This contains all files that are generated for jsp checking and such.
	 */
	private File				m_workDir;

	/**
	 * This set contains a list of filenames that, when they are present in a .jar file,
	 * indicate that the jar cannot be included because it defines a Sun standard.
	 */
	static private final Set<String> DUMPSET = new HashSet<String>();

	static {
		DUMPSET.add("javax/servlet/Servlet.class");
		DUMPSET.add("javax/servlet/http/HttpServlet.class");
		DUMPSET.add("javax/servlet/Filter.class");
		DUMPSET.add("oracle/jdbc/driver/OracleDriver.class");                   // Do not copy driver lib
//		DUMPSET.add("");
	}

	public EclipseWebMaker(final File root, final String name) {
		super(root, name);
	}
	public String getName() {
		return "Eclipse WebApp Builder";
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Reading WTP's app configuration.					*/
	/*--------------------------------------------------------------*/
	/**
	 * Main entry: load all webapp's data.
	 * @see to.etc.saram.bld.maker.EclipseModuleMakerBase#initialize(to.etc.saram.executor.log.Reporter, to.etc.saram.bld.builder.ModuleBuildInfo)
	 */
	@Override
	public boolean initialize(final Reporter r, final ModuleBuildInfo bi, BuildIntentType brt) throws Exception {
		super.initialize(r, bi, brt);

		if(getEclipseProjectDefinition()) {
			readExtraSettings();
			readWebAppSettings();
			finishProjectDefinition();
			return true;
		}
		return false;
	}

	/**
	 * This reads the WTP .components file to decode publishing data.
	 * @throws Exception
	 */
	protected void	readWebAppSettings() throws Exception {
		//-- Handle settings
		log("Handle webapp dependencies");
		File settings = getModuleFile(".settings");
		if(settings.exists() && settings.isDirectory()) {
			File f	= new File(settings, "org.eclipse.wst.common.component");
			if(f.exists())
				decodeComponents(f);
		}
	}

	/**
	 * Scan all OUTPUT's for source paths; if an OUTPUT is written into
	 * a WEB Pblish path we need to change it there.
	 */
	private void	scanForOutputInWebFolder() {
		for(SourcePath sp : getSourcesList()) {
			if(sp.output == null || sp.output.length() == 0)
				continue;

			//-- Does this path correspond with a publish path?
			for(PublishPath pp : m_publishPath) {
				if(pp.getType() == PublishType.pptWEB) {
					//-- Does this overlap somehow?
					if(sp.output.startsWith(pp.getRelSourcePath())) {
						int	pos = pp.getRelSourcePath().length();
						if(sp.output.length() == pp.getRelSourcePath().length() || (sp.output.length() > pp.getRelSourcePath().length() && sp.output.charAt(pos) == '/')) {
							//-- Must remap into image. First remove the common root;
							if(pos < sp.output.length())			// If not an exact match we have a slash
								pos++;
							File	out= new File(getImageDir(), sp.output.substring(pos));
							r().detail("Mapping output "+sp.output+" within the web app's folder: "+out);
							System.out.println("!!!!!!!!!! Mapping output "+sp.output+" within the web app's folder: "+out);
							sp.outputPath = out;
							out.mkdirs();
							return;
						}
					}
				}
			}
		}
	}


	protected void	finishProjectDefinition() throws Exception {
		scanForOutputInWebFolder();

		//-- If we've decoded everything then check if we'll produce Java builder output.
		if(getSourcesList().size() > 0) {
			//-- Create a classes output thing at [target]WEB-INF/classes
			File bd = new File(new File(getImageDir(), "WEB-INF"), "classes");
			bd.mkdirs();
			setClassesBinDir(bd);
		}

		/*
		 * FIXME TODO
		 * Handle creating the .war; we do not create a .jar for the classes only.
		 */
		if(getBuildIntent() == BuildIntentType.NORMAL) {
			String name = makeRealProjectName() + ".war";
			File f = new File(getOutputRoot(), name);
			GeneratedJarProduct jp = new GeneratedJarProduct(getBuildInfo(), f, name, getImageDir());
			//		m_webProd = new WebAppProduct(m_bi, new File(getOutputRoot(), name), name, pp);
			getBuildInfo().setFixGenerator("WebAppFixer");
			addGeneratedProduct(jp);
		}

		// FIXME This could do with a less dirty approach.
		//-- We generate a fixable webapp. Is there a fix source attached?
		String	fixdir = getBuildInfo().getModuleProperty("patchdirs");
		if(fixdir != null) {
			File fd = getModuleFile(fixdir.trim());
			if(fd.exists()) {
				//-- Project the fixes into a .fixes directory within the webapp. FIXME This is quite dirty, but fast.
				File	out = new File(getOutputRoot(), "fixes");
				out.mkdirs();
				FileTool.copyDir(out, fd);
			}
		}
	}

	/**
	 * Decode the piece of utter devastating SHIT that is WTP...
	 *
	 * @param f		The file containing the WTP components joke.
	 */
	private void decodeComponents(final File f) throws Exception {
		Document	doc = getDoc(f);
		Node rn = DomTools.nodeFind(doc, "project-modules");
		if(rn == null) {
			r().log("Unexpected: no project-modules in component file.");
			return;
		}

		Node wn = DomTools.nodeFind(rn, "wb-module");
		if(wn == null) {
			r().log("Unexpected: no wb-module in component file.");
			return;
		}

		//-- If there's no context-path property we're no webapp
		boolean found = false;
		NodeList	nl = wn.getChildNodes();
		for(int i = 0; i < nl.getLength(); i++) {
			Node cn = nl.item(i);
			if(cn.getNodeName().equals("property")) {
				String a = DomTools.strAttr(cn, "name", null);
				String v = DomTools.strAttr(cn, "value", null);
				if(a != null && v != null) {
					if(a.equals("context-root")) {
						found = true;
						break;
					}
				}
			}
		}
		if(! found)
			return;

		//-- Walk all children for dependent-module shit
		for(int i = 0; i < nl.getLength(); i++) {
			Node cn = nl.item(i);
			if(cn.getNodeType() != Node.ELEMENT_NODE)
				continue;
			if(cn.getNodeName().equals("wb-resource")) {
				String dpp= DomTools.strAttr(cn, "deploy-path");
				if(dpp == null) {
					r().detail("Odd: wtp wb-resource lacks deploy-path");
					continue;
				}
				String sp = DomTools.strAttr(cn, "source-path");
				if(sp == null) {
					r().detail("Odd: wtp wb-resource lacks source-path");
					continue;
				}

				//-- What kind of deploy? Is it a deploy of a source? Then use binaries
				if(! sp.startsWith("/")) {
					r().detail("Odd: wtp wb-resource source-path does not start with /");
					continue;
				}
				sp = sp.substring(1);
				found = false;
				for(SourcePath src : getSourcesList()) {
					if(src.relpath.equals(sp)) {
						found = true;
						break;
					}
				}
				if(found) {
					//-- Add classes output FIXME Should allow for >1 output path
					if(getClassesBinDir() == null) {
						File	bd = new File(new File(getImageDir(), "WEB-INF"), "classes");
						bd.mkdirs();
						setClassesBinDir(bd);
						m_publishPath.add(new PublishPath(PublishType.pptCLASS, "WEB-INF/classes", sp, getClassesBinDir()));
					}
				} else {
					//-- Normal copy
					File src = getModuleFile(sp);
					if(dpp.startsWith("/"))
						dpp = dpp.substring(1);
					m_publishPath.add(new PublishPath(PublishType.pptWEB, dpp, sp, src));
				}
			} else if(cn.getNodeName().equals("dependent-module")) {
				//-- there we have it...
				String s = DomTools.stringNode(cn, "dependency-type");
				if(! "uses".equalsIgnoreCase(s)) {
					r().detail("Odd: wtp contains unknown dependency-type " + s + ". Ah well-forget it.");
					continue;
				}

				//-- Is a uses thing. Get handle crap
				String	handle = DomTools.strAttr(cn, "handle", null);
				if(handle == null) {
					r().detail("Odd: wtp has no handle attribute!? Ah well-forget it.");
					continue;
				}
				//                      012345678
				if(! handle.startsWith("module:/")) {
					r().detail("Odd: wtp has no 'module:/' in handle " + handle + ". Ah well-forget it.");
					continue;
				}

				//-- Get the next segment to 1st slash
				int	ix = 8;
				int pos = handle.indexOf('/', ix);
				if(pos == -1) {
					r().detail("Odd: wtp has no 2nd slash after module in handle " + handle + ". Ah well-forget it.");
					continue;
				}
				String type = handle.substring(ix, pos);
				if("resource".equals(type)) {
					/*
					 * module:/resource/bin-hibernate-3.2.3/bin-hibernate-3.2.3
					 * Really useful, specifying build dependencies 2ce. Well done morons.
					 * Just get one of the thingies and add as a dependent module (if not already there).
					 */
					ix = pos+1;
					pos	= handle.indexOf('/', ix);
					if(pos == -1) {
						r().detail("Odd: wtp has no 3rd slash after resource in handle " + handle + ". Ah well-forget it.");
						continue;
					}
					String name = handle.substring(ix, pos);

					//-- 2nd part should be same??
					s	= handle.substring(pos+1);
					if(! name.equals(s))
						r().detail("Odd: wtp has different resource names " + name + " and " + s + " in handle " + handle + ". Add 1st one as dependency.");
					addDirectDep(name);
				} else if("classpath".equals(type)) {
					/*
					 * module:/classpath/lib/bin-hibernate-3.2.3/lib/xml-apis.jar: add as an external ref
					 */
					PathSplitter	ps = new PathSplitter(handle.substring(pos+1));
					if(ps.isEmpty()) {
						r().detail("Odd: wtp has nothing after /classpath/ - " + handle + ". Ah well-forget it.");
						continue;
					}

					if(! ps.getCurrent().equals("lib")) {
						r().detail("Odd: wtp missing /lib/ in classpath - "+handle+". Ah well-forget it.");
						continue;
					}
					if(! ps.next()) {
						r().detail("Odd: wtp has nothing after /lib/ - " + handle + ". Ah well-forget it.");
						continue;
					}
					String module = ps.getCurrent();
					addDirectDep(module);
					if(! ps.next()) {
						r().detail("Odd: wtp has nothing after the module name in " + handle + ". Ah well-forget it.");
						continue;
					}

					//-- Rest is external path in module;
					getBuildInfo().addExternalReference(module, ps.getCurrentAndRest());	// Add an external
				} else {
					r().detail("Odd: wtp has unknown handle location type " + type + " in handle " + handle + ". Ah well-forget it.");
					continue;
				}
			}
		}
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Obtain a valid list of all dependencies (jars)		*/
	/*--------------------------------------------------------------*/
	/**
	 * Check for j2ee stuff in a jar. If the .jar passed has an invalid
	 * .class file aboard this returns false.
	 *
	 * @param r
	 * @param p
	 * @return
	 */
	private boolean allowAdding(final JarProduct p) {
		ZipFile	zf = null;
		try {
			zf = new ZipFile(p.getFile());
			Enumeration<? extends ZipEntry> e = zf.entries();
			while(e.hasMoreElements()) {
				ZipEntry ze = e.nextElement();
				String path = ze.getName();
				if(DUMPSET.contains(path)) {
					r().log("webapp: discarding "+p.getBaseName()+" because it contains "+path);
					return false;
				}
			}
			return true;
		} catch(Exception x) {
			r().exception(x, "while scanning contents of "+p.getFile());
			return true;
		} finally {
			try { if(zf != null) zf.close(); } catch(Exception x) {}
		}
	}

	/**
	 * Locates a module's BuildInfo by module name.
	 * @param list
	 * @param name
	 * @return
	 */
	//	static private ModuleBuildInfo	findModule(final OrderedBuildList list, final String name) {
	//		for(ModuleBuildInfo bi : list) {
	//			if(bi.getName().equals(name))
	//				return bi;
	//		}
	//		return null;
	//	}

	/**
	 * This adds a .jar to the list of .jars that all represent the same .jar in the set.
	 * @param map
	 * @param bi
	 * @param jp
	 */
	static private void	addMap(final Map<String, List<Res>> map, final ModuleBuildInfo bi, final JarProduct jp) {
//	    System.out.println("addMap: "+jp.getFileName());
		List<Res> rl = map.get(jp.getBaseName());
		if(rl == null) {
			rl = new ArrayList<Res>();
			map.put(jp.getBaseName(), rl);
		}
		Res res = new Res(bi, jp);
		rl.add(res);
	}

	/**
	 *
	 * @param map
	 * @param r
	 * @param bi
	 * @param l
	 */
	static private void addProducts(final Map<String, List<Res>> map, final Reporter r, final ModuleBuildInfo bi, final List<? extends Product> l) {
		for(Product p : l) {
			if(p instanceof JarProduct) {
				JarProduct jp = (JarProduct)p;
				addMap(map, bi, jp);
//				r.detail("webapp: got product "+jp.getFileName());
			}
		}
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	The actual build process							*/
	/*--------------------------------------------------------------*/
	/**
	 * Main entrypoint, called to build everything. This version creates
	 * a webapp by creatively compiling and copying the sources.
	 */
	public void buildModule(final Writer buildlog) throws Exception {
		if(!compileSources(buildlog)) // Compile all sources to WEB-INF/classes
			return;

		copyWebFiles(); // Copy all web files to their appropriate location
		copyDependencies(); // Copy all of the build dependencies (.jar files) into WEB-INF/lib

		//-- The webapp has been constructed completely.
		//		if(m_checkJSPs)
		//			checkJSPs(buildlog);

		if(getGeneratedProductList().size() > 0) {
			long ts = System.nanoTime();
			generateProducts(); // Finally: create the .war
			ts = System.nanoTime() - ts;
			System.out.println("War file generated in " + StringTool.strNanoTime(ts));
		}
	}

	/**
	 * Handle the publishing of all files in the web path.
	 * @throws Exception
	 */
	private void copyWebFiles() throws Exception {
		long ts = System.nanoTime();
		//-- Copy the publish paths (classes, web resources) from /this/ compiled copy
		for(PublishPath p : m_publishPath) {
			if(p.getType() == PublishType.pptWEB) {
				//-- Push this into the appropriate target location.
				File target = new File(getImageDir(), p.getRelTargetPath());
				target.mkdirs();
				FileTool.copyDir(target, p.getSource());
			}
		}
		ts = System.nanoTime() - ts;
		System.out.println("WebApp's files copied to image directory in " + StringTool.strNanoTime(ts));
	}

	/**
	 * This scans all of the build time dependencies and copies their build result (the
	 * .jar files) into WEB-INF/lib. While doing this we take care not to copy .jars
	 * that contain JSR definitions (like the j2ee.jar, jsrxxx.jar for servlet API defs
	 * etc). In addition we resolve duplicate jars and select only one (and always the
	 * same one) to be present in the result.
	 */
	private void	copyDependencies() throws Exception {
		long ts = System.nanoTime();

		/*
		 * Preparation phase: determine all that has to be included by dropping all
		 * of the jars that are duplicates (selecting the most suitable copy) and
		 * all of the jars containing disallowed classes (like the jars containing
		 * the interfaces for one of the J2EE standards).
		 */
		//-- Get a list of all product jars of all dependents and map all duplicates, in order,
		ModuleBuildInfo	root = getBuildInfo();							// The root module
		Map<String, List<Res>>	map = new HashMap<String, List<Res>>();
		for(ModuleBuildInfo bi : root.getFullDependencyList()) {
//		    System.out.println("WebApp: checking products for "+bi.getName());
			addProducts(map, r(), bi, bi.getMaker().getGeneratedProductList());
			addProducts(map, r(), bi, bi.getMaker().getExportedProductList());

			//-- Handle all external references. These get translated to a JarProduct then added
//			addExternalReferences(map, r, root, bi);
		}
//		addExternalReferences(map, r, root, root);					// Add xrefs in root project because that is not part of the dependencies
		addProducts(map, r(), root, root.getMaker().getExportedProductList());

		//-- Now check for duplicate jars, scanning for version or date patters in the jar filenames.
		List<JarProduct>	resl = new ArrayList<JarProduct>(map.size()+2);		// Result list after illegal jar removal
		List<Res>	work = new ArrayList<Res>();
		for(String base : map.keySet()) {
			List<Res>	refs = map.get(base);
			if(refs.size() == 1) {
				// Easy
				r().detail("webapp: including library "+refs.get(0).product.getName()+" from module "+refs.get(0).buildInfo);
				resl.add(refs.get(0).product);
				continue;
			}

			//-- We have multiple versions. Select one,
			Res	best = null;
			work.clear();
			for(Res res : refs) {
				if(best == null) {
					best = res;
					work.add(res);
				} else {
					int cv = FileProduct.compareVersions(best.product, res.product);
					if(cv == 0) {
						//-- Have 2 versions same level..
						work.add(res);
					} else if(cv > 0) {
						//-- Current is better than last; report all skipped libs
						for(Res tr : work) {
							r().detail("webapp: ignoring duplicate library "+tr.product.getName()+" from module "+tr.buildInfo);
						}
						work.clear();
						work.add(res);
						best = res;
					} else {
						//-- We already have a better one..
						r().detail("webapp: ignoring duplicate library "+res.product.getName()+" from module "+res.buildInfo);
					}
				}
			}

			//-- Must have a single choice now
			if(work.size() > 1) {
				Res res= work.get(work.size()-1);
				r().log(work.size() + " copies of library " + base + " with same version - including the last one from " + res.buildInfo);
				resl.add(res.product);
				for(int i = work.size()-1; --i >= 0;) {
					res = work.get(i);
					r().detail("webapp: ignoring duplicate library "+res.product.getName()+" from module "+res.buildInfo);
				}
			} else {
				r().detail("webapp: including library "+best.product.getName()+" from module "+best.buildInfo);
				resl.add(best.product);
			}
		}

		/*
		 * In the generated list we may have .jars that only define standards. These are necessary for
		 * compiling but may not be published to a server (the server has it's own implementation of
		 * these). Drop those from the distribution list but add them to a private list for JSP
		 * checking.
		 */
		for(int i = resl.size(); --i >= 0;) {
			JarProduct jp = resl.get(i);
			if(! allowAdding(jp)) {
				resl.remove(i);
				m_jsplist.add(jp);					// Do add it to the list of thingies needed for the JSP check.
			}
		}

		/*
		 * Publish all files to the WEB-INF/lib dir.
		 */
		File	libf = new File(new File(getImageDir(), "WEB-INF"), "lib");
		libf.mkdirs();
		for(JarProduct jp : resl) {
			FileTool.copyFile(new File(libf, jp.getNameWithoutPath()), jp.getFile());
		}
		ts = System.nanoTime() - ts;
		System.out.println("WebApp's dependencies copied to image directory in " + StringTool.strNanoTime(ts));
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Check JSPs											*/
	/*--------------------------------------------------------------*/

	//	/**
	//	 * Locate all JSP's, then translate and compile them.
	//	 */
	//	public void	checkJSPs(final Writer buildlogger) throws Exception {
	//		//-- Create a work directory for this project
	//		File wf = new File(Builder.getInstance().getRepositoryWork(smv().getRepository()), getModuleName() + "-work");
	//		FileTool.deleteDir(wf);							// Make sure it is gone;
	//		wf.mkdirs();
	//		m_workDir = wf;
	//
	//		//-- Create an ANT file for the check.
	//		File af = makeCheckAntFile();
	//		if(af == null) {
	//			r().log("JSP checking disabled");
	//			return;
	//		}
	//		if(! runChecker(buildlogger, af))
	//			return;
	//		af	= makeCompileAntFile();
	//		runCompiler(buildlogger, af);
	//	}
	//
	//	private boolean runChecker(final Writer buildoutput, final File antfile) throws Exception {
	//		List<String> args = new ArrayList<String>();
	//		String ant = "ant";						// FIXME Must come from system config
	//		args.add(ant);
	//		args.add("-f");
	//		args.add(antfile.toString());
	//		r().detail("Starting 'ant' using: " + args);
	//
	//		//-- Create the info writer files.
	//		ProcessBuilder pb = new ProcessBuilder(args);
	//
	//		//-- Create a temp stringwriter because the stupid jspc/jasper task does not return an error on JSP failures
	//		StringWriter	tw	= new StringWriter(512*1024);
	//
	//		String	messages = "";
	//		int rc = -1;
	//		try {
	//			rc = ProcessTools.runProcess(pb, buildoutput);
	//		} finally {
	//			try {
	//				try { tw.close(); } catch(Exception x) {}
	//				messages	= tw.getBuffer().toString();	// Get ant output
	//				buildoutput.append(messages);				// Append to build writer.
	//			} catch(Exception x) {
	//				r().exception(x, "Can't write to build log writer");
	//			}
	//		}
	//		tw = null;											// Release soonest.
	//		if(rc != 0) {
	//			r().error("The JSP check for " + getBuildInfo().getName() + " has failed: the exitcode was " + rc);
	//			getBuildInfo().setBuildError("The JSP Check process has failed with exitcode "+rc);
	//			return false;
	//		}
	//
	//		//-- Check for errors in the messages.
	//		Pattern		p	= Pattern.compile("^\\s*\\[jasper\\]\\s*severe:");
	//		Matcher		m = p.matcher("");
	//		LineNumberReader	lnr = new LineNumberReader(new StringReader(messages));
	//		String line;
	//		int erc = 0;
	//		while(null != (line = lnr.readLine())) {
	//			if(m.reset(line.toLowerCase()).find())				// Does this contain an error?
	//				erc ++;
	//		}
	//		lnr.close();
	//		if(erc > 0) {
	//			r().error("The JSP check process for '" + getBuildInfo().getName() + "' reported "+erc+" errors");
	////			getBuildInfo().setBuildError("The JSP check process for '" + getBuildInfo().getName() + "' reported "+erc+" errors");
	//			return true;
	//		}
	//
	//		r().detail("The JSP check process for '" + getBuildInfo().getName() + "' has completed succesfully.");
	//		return true;
	//	}
	//
	//	private File makeCheckAntFile() throws Exception {
	//		String catalina = System.getProperty("catalina.home");
	//		if(catalina == null)
	//			return null;
	//		File chome = new File(catalina);
	//		if(!chome.exists() || !chome.isDirectory()) {
	//			r().log("The catalina.home variable point to an invalid tomcat directory: " + chome);
	//			return null;
	//		}
	//		File tasks = new File(chome, "bin/catalina-tasks.xml");
	//		if(!tasks.exists() || !tasks.isFile()) {
	//			r().log("The " + tasks + " file is missing or invalid");
	//			return null;
	//		}
	//
	//		File af = new File(m_workDir, ".jspcheck.xml");
	//		XmlWriter xw = new XmlWriter();
	//		PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(af)));
	//		try {
	//			xw.init(pw, 0);
	//			pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
	//			xw.tagnl("project", new String[]{"name", "jspcheck", "default", "check", "basedir", getImageDir().toString()});
	//
	//			xw.tagonly("import", "file", tasks.toString());
	//
	//			//-- Generate an INIT section
	//			xw.tag("target", new String[]{"name", "init"});
	//            xw.append("\n");
	//            xw.tagonly("property", "name", "basePath", "value", getImageDir().toString());
	//
	//
	//			xw.tagendnl(); 						// end target 'init'
	//
	//			//-- Generate the 'compile' section,
	//			xw.tagnl("target", "name", "check", "depends", "init");
	//			//-- Generate the classpath to use as a STRING.
	//			StringBuilder sb = new StringBuilder();
	//			for(JarProduct jp : m_jsplist) {
	//				if(sb.length() > 0)
	//					sb.append(':');
	//				sb.append(jp.getFile().toString());
	//			}
//
	//			File	out	= new File(m_workDir, "srcOut");
	//			out.mkdirs();
	//			xw.tagonly("jasper",
	//				"validateXml","false",
	//				"uriroot", getImageDir().toString(),
	//				"webXmlFragment", "/tmp/generated.xml",
	//				"failonerror", "false",
	//				"classpath", sb.toString(),
	//				"outputDir", out.toString()
	//			);
	//			xw.tagendnl();						// end target 'check'
	//			xw.tagendnl(); 						// End Project
	//			return af;
	//		}
	//		finally {
	//			try { pw.close(); } catch(Exception x) {}
//		}
	//	}
	//
	//
	//	private File makeCompileAntFile() throws Exception {
	//		File af = new File(m_workDir, ".jspcompile.xml");
	//		XmlWriter xw = new XmlWriter();
	//		PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(af)));
	//		try {
	//			xw.init(pw, 0);
	//			pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
	//			xw.tagnl("project", new String[]{"name", "jspcompile", "default", "compile", "basedir", getImageDir().toString()});
	//
	//			//-- Generate an INIT section
	//			File	classes = new File(m_workDir, "jspclasses");
	//			classes.mkdirs();
	//			xw.tag("target", new String[]{"name", "init"});
	//            xw.append("\n");
	//			xw.tagonly("property", "name", "outputPath", "value", classes.toString());
//			xw.append("\n");
	//            xw.tagonly("property", "name", "basePath", "value", getImageDir().toString());
	//			xw.tagendnl(); 						// end target 'init'
	//
	//			//-- Generate the 'compile' section,
	//			xw.tagnl("target", "name", "compile", "depends", "init");
	//
	//			xw.tagnl("javac", new String[] {
	//					"destDir", classes.toString(),
	//				"nowarn", "on",
	//					"debug", "off",
	//					"encoding", getBuildInfo().getEncoding(),
	//					"target", "1.5",
	//					"source", "1.5"
	//			});
	//			xw.tagonly("compilerarg", "compiler", "org.eclipse.jdt.core.JDTCompilerAdapter", "line", "-nowarn -warn:none");
	//	        xw.tagonly("src", "path", new File(m_workDir, "srcOut").toString());
	//			xw.tagonly("compilerarg", "compiler", "org.eclipse.jdt.core.JDTCompilerAdapter", "line", "-nowarn -warn:none");
	//	        xw.tagnl("classpath");
	//	        xw.tagonlynl("pathelement", "location", new File(getImageDir(), "WEB-INF/classes").toString());
	//
	//	        //-- Add all JSP-only jars
	//	        for(JarProduct jp : m_jsplist) {
	//		        xw.tagonlynl("pathelement", "location", jp.getFile().toString());
	//	        }
	//
	//	        xw.tagnl("fileset", "dir", new File(getImageDir(), "WEB-INF/lib").toString());
	//	        xw.tagonlynl("include", "name", "*.jar");
	//	        xw.tagendnl();						// fileset
	//
	//	        xw.tagendnl();						// classpath
	//
	//	        xw.tagendnl();						// end javac
	//
	//			xw.tagendnl();						// end target 'check'
	//			xw.tagendnl(); 						// End Project
	//			return af;
	//		}
	//		finally {
	//			try { pw.close(); } catch(Exception x) {}
	//		}
	//	}
	//
	//	private boolean runCompiler(final Writer buildoutput, final File antfile) throws Exception {
	//		List<String> args = new ArrayList<String>();
	//		String ant = "ant";						// FIXME Must come from system config
	//		args.add(ant);
	//		args.add("-f");
	//		args.add(antfile.toString());
	//
	//		r().detail("Starting 'ant' using: " + args);
	//
	//		//-- Create the info writer files.
	//		ProcessBuilder pb = new ProcessBuilder(args);
	//
	//		int rc = -1;
	//		try {
	//			rc = ProcessTools.runProcess(pb, buildoutput);
	//		} finally {
	//			try {
	//				buildoutput.close();
	//			} catch(Exception x) {
	//				r().exception(x, "Can't close build log writer");
	//			}
	//		}
	//		if(rc != 0) {
	//			r().error("The JSP java compilation for " + getBuildInfo().getName() + " has failed: the exitcode was " + rc);
	//		}
	//		else {
	//			r().detail("The JSP java compilation for " + getBuildInfo().getName() + " has completed succesfully");
	//		}
	//		return true;
	//	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Creating a delta between two builds.				*/
	/*--------------------------------------------------------------*/


	public DeltaBuilder	createDelta(final File olddir, final File newdir) throws Exception {
		DeltaBuilder	db = new DeltaBuilder();
		File	oldimg = new File(olddir, "image");
		File	newimg = new File(newdir, "image");
		db.delta("image", oldimg, newimg);

		//-- Compare the fix directories too, and add those as fix delta's
		File	oldfixes	= new File(olddir, "fixes");
		File	newfixes	= new File(newdir, "fixes");
		db.delta("fixes", oldfixes, newfixes);
		return db;
	}
}
