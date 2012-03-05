package to.etc.prjbuilder.builder;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.util.*;
import to.etc.util.*;

/**
 * This is the root configuration data manager for all kinds of builds, and
 * represents basic config data as read from a properties file.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on May 11, 2010
 */
public class BuilderConfiguration {
	private Map<JavaVersion, File> m_jdkMap = new HashMap<JavaVersion, File>();

	private JavaVersion m_defaultSourceVersion = JavaVersion.JAVA_1_6;

	private JavaVersion m_defaultTargetVersion = JavaVersion.JAVA_1_6;

	private String m_defaultEncoding = "UTF-8";

	public JavaVersion getDefaultSourceVersion() {
		return m_defaultSourceVersion;
	}

	public void setDefaultSourceVersion(JavaVersion defaultSourceVersion) {
		m_defaultSourceVersion = defaultSourceVersion;
	}

	public JavaVersion getDefaultTargetVersion() {
		return m_defaultTargetVersion;
	}

	public void setDefaultTargetVersion(JavaVersion defaultTargetVersion) {
		m_defaultTargetVersion = defaultTargetVersion;
	}

	public String getDefaultEncoding() {
		return m_defaultEncoding;
	}

	public void setDefaultEncoding(String defaultEncoding) {
		m_defaultEncoding = defaultEncoding;
	}

	/**
	 *
	 * @param jdk
	 * @return
	 */
	public File getJdkRoot(JavaVersion jdk) {
		File root = m_jdkMap.get(jdk);
		if(root == null)
			throw new BuildException("No JDK for Java version " + jdk + " found");
		return root;
	}

	public Map<JavaVersion, File> getJdkMap() {
		return m_jdkMap;
	}

	public void setJdkRoot(JavaVersion v, File jdk) {
		m_jdkMap.put(v, jdk);
	}

	public Object findJdkRoot(JavaVersion version) {
		return m_jdkMap.get(version);
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Loading/creating config.							*/
	/*--------------------------------------------------------------*/
	static private final String[] WINPATHS = {"C:\\Program Files", "D:\\Program Files", "c:\\java"};

	static private final String[] LINUXPATHS = {"/usr/lib/jvm", "/usr/java", "/opt/java", "/opt"};

	/**
	 * FIXME Needs to move to class BuildConfiguration.
	 * @throws Exception
	 */
	public void loadConfiguration(File configFile) throws Exception {
		if(!configFile.exists()) {
			createConfigFile(configFile);
		}

		Properties p = FileTool.loadProperties(configFile);
		String s = p.getProperty("encoding");
		if(s != null)
			setDefaultEncoding(s.trim());

		s = p.getProperty("java.source");
		if(s != null)
			setDefaultSourceVersion(JavaVersion.byName(s.trim()));
		s = p.getProperty("java.target");
		if(s != null)
			setDefaultTargetVersion(JavaVersion.byName(s.trim()));

		for(JavaVersion jv : JavaVersion.values()) {
			s = p.getProperty("java." + jv);
			if(s != null) {
				File f = new File(s.trim());
				if(!f.exists())
					throw new IllegalArgumentException("The java." + jv + " property in " + configFile + " points to a non-existing path " + f);
				setJdkRoot(jv, f);
			}
		}
	}


	/**
	 * Try to create a config file by locating jdks and stuff.
	 *
	 * @param configFile
	 */
	private void createConfigFile(File configFile) throws Exception {
		info("Running for the first time: locating JDK's and creating a config file in " + configFile);
		Properties p = new Properties();
		p.setProperty("encoding", "utf8");
		p.setProperty("java.source", "1.6");
		p.setProperty("java.target", "1.6");

		if(File.separatorChar == '\\')
			scanJDKs(WINPATHS);
		else
			scanJDKs(LINUXPATHS);

		//-- Put all JDKs in the file
		for(JavaVersion jv : getJdkMap().keySet()) {
			File jf = getJdkRoot(jv);
			p.setProperty("java." + jv, jf.getAbsolutePath());
		}
		FileTool.saveProperties(configFile, p);
	}

	private void scanJDKs(String[] par) {
		for(String s : par) {
			File path = new File(s);
			if(path.exists() && path.isDirectory())
				scanForJdk(path, 3);
		}
	}

	private void scanForJdk(File path, int depthleft) {
		if(path == null)
			return;

		JavaVersion version = checkJdkPath(path);
		if(version != null) {
			info("  auto-discovered a " + version + " JDK at " + path);
			if(findJdkRoot(version) == null)
				setJdkRoot(version, path);
			return;
		}
		if(depthleft <= 0)
			return;

		//-- Scan deeper
		File[] far = path.listFiles();
		if(null == far)
			return;
		for(File f : far) {
			if(f.isDirectory())
				scanForJdk(f, depthleft - 1);
		}
	}

	private JavaVersion checkJdkPath(File path) {
		File java = new File(path, "bin/java");
		if(!java.exists())
			return null;
		File rt = new File(path, "lib/tools.jar");
		if(!rt.exists())
			return null;
		rt = new File(path, "jre/bin/java"); // If this is not here we have found a JRE, not a JDK
		if(!rt.exists())
			return null;

		//-- Try to determine a version.
		ProcessBuilder pb = new ProcessBuilder(java.toString(), "-version");
		StringBuilder sb = new StringBuilder();
		try {
			int rc = ProcessTools.runProcess(pb, sb);
			if(rc != 0) {
				info("  ignoring " + path + " because java -version returned return code " + rc);
				return null;
			}
			String data = sb.toString();
			LineNumberReader lnr = new LineNumberReader(new StringReader(data));
			String line;
			String version = null;
			while(null != (line = lnr.readLine())) {
				if(line.startsWith("java version")) {
					version = line.substring(12).trim();
					if(version.startsWith("\"") && version.endsWith("\""))
						version = version.substring(1, version.length() - 1);
					if(version.startsWith("'") && version.endsWith("'"))
						version = version.substring(1, version.length() - 1);
					break;
				}
			}
			if(version != null) {
				if(version.startsWith("1.2."))
					return JavaVersion.JAVA_1_2;
				else if(version.startsWith("1.3"))
					return JavaVersion.JAVA_1_3;
				else if(version.startsWith("1.4"))
					return JavaVersion.JAVA_1_4;
				else if(version.startsWith("1.5"))
					return JavaVersion.JAVA_1_5;
				else if(version.startsWith("1.6"))
					return JavaVersion.JAVA_1_6;
				else if(version.startsWith("1.7"))
					return JavaVersion.JAVA_1_7;
				else {
					info("  ignoring " + path + " because version '" + version + "' is not recognised");
					return null;
				}
			}
		} catch(Exception x) {
			System.out.println("  ignoring " + path + ": " + x);
		}
		return null;
	}

	private void info(String txt) {
		System.out.println(txt);
	}

}
