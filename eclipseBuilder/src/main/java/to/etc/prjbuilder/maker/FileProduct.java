package to.etc.prjbuilder.maker;

import java.io.*;

import to.etc.prjbuilder.builder.*;

/**
 * Base for products based on files.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 17, 2007
 */
public class FileProduct implements Product {
	private String	m_fileName;

	/** The name as it is known between the builds. This may contain path indicators. */
	private String	m_name;

	private File	m_file;

	/** The name part of this resouce only, without any paths. */
	private String	m_baseName;
	private String	m_fullVersion;
	private int[]	m_version;
	private String	m_versionSuffix;
	private ModuleBuildInfo		m_buildInfo;

	public FileProduct(ModuleBuildInfo source, File f, String name) {
		m_buildInfo = source;
		m_fileName = f.getName();
		m_name	= name;
		m_file = f;
		split();
	}
	public String getName() {
		return m_name;
	}

	public ModuleBuildInfo getBuildInfo() {
		return m_buildInfo;
	}

	@Override
	public String toString() {
	    return m_fileName;
	}

//	public String getFileName() {
//		return m_fileName;
//	}

	public String	getNameWithoutPath() {
		return m_fileName;
	}
	public File getFile() {
		return m_file;
	}
	public String getBaseName() {
		return m_baseName;
	}
	public String getFullVersion() {
		return m_fullVersion;
	}
	public boolean	hasVersion() {
		return m_fullVersion.length() > 0;
	}
	public int[] getVersion() {
		return m_version;
	}
	public String getVersionSuffix() {
		return m_versionSuffix;
	}
	private void	split() {
		String	name = m_file.getName();
		int pos = name.lastIndexOf('.');				// Strip suffix,
		if(pos != -1)
			name = name.substring(0, pos);

		//-- Now we'll scan name fragments until a fragment is recognised as a version.
		int ix = 0;
		while(ix < name.length()) {
			pos = name.indexOf('-', ix);
			if(pos == -1)
				break;
			if(isVersion(name, ix, pos)) {
				//-- Version starts here.
				m_baseName	= name.substring(0, ix-1);
				m_fullVersion = name.substring(ix);
				splitVersion();
				return;
			}
			ix = pos+1;
		}
		m_fullVersion = "";
		m_baseName = name;
		m_version = new int[0];
		m_versionSuffix = "";
	}

	/**
	 * Further separate the version into a numeric version (x.x.x.x) and any suffix
	 *
	 */
	private void splitVersion() {
		int[]	var = new int[10];
		int	ix = 0;
		int	len = m_fullVersion.length();
		int		dots = 0;
		int		curv = 0;
		while(ix < len) {
			char c = m_fullVersion.charAt(ix++);
			if(c == '.') {
				if(dots>= var.length) {
					m_version = var;
					m_versionSuffix = m_fullVersion.substring(ix);
					return;
				}
				var[dots++] = curv;
				curv = 0;
			} else if(Character.isDigit(c)) {
				curv = curv * 10 + Character.getNumericValue(c);
			} else {
				//-- All others: version ends and suffix starts.
				ix--;
				break;
			}
		}
		m_version = new int[dots];
		System.arraycopy(var, 0, m_version, 0, dots);
		m_versionSuffix = m_fullVersion.substring(ix);
	}

	static private boolean isVersion(String s, int ix, int ex) {
		while(ix < ex) {
			char c = s.charAt(ix++);
			if(! Character.isDigit(c) && c != '.' && c != '_')
				return false;
		}
		return true;
	}

	/**
	 * Returns 1 if b > a.
	 * @param a
	 * @param b
	 * @return
	 */
	static public int	compareVersions(FileProduct a, FileProduct b) {
		if(a.m_version.length == 0) {
			if(b.m_version.length == 0)
				return a.m_versionSuffix.compareTo(b.m_versionSuffix);
			return 1;					// B is better
		}
		if(b.m_version.length == 0) {
			return -1;					// A is better
		}

		//-- Compare versions; use the higher one;
		for(int i = 0; i < 10; i++) {
			if(a.m_version.length <= i) {
				if(b.m_version.length <= i) {
					//-- Exact same version specifier-> return compare of suffix
					return a.m_versionSuffix.compareTo(b.m_versionSuffix);
				}
				return 1;				// B has more version -> is larger
			}
			if(b.m_version.length <= i)
				return -1;				// A has more version -> a is larger
			int res = a.m_version[i] - b.m_version[i];
			if(res != 0) {
				return res < 0 ? 1 : -1;
			}
		}
		return a.m_versionSuffix.compareTo(b.m_versionSuffix);
	}
}
