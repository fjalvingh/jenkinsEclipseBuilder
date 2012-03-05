package to.etc.prjbuilder.prjs;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.*;
import to.etc.util.*;

/**
 * Handles project definition on local disk. It handles mapping of projects to their
 * separate versions for build and merge purposes.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on May 14, 2010
 */
public class ProjectConfig {
	private File m_configFile;

	private Properties m_configSource = new Properties();

	private Map<String, PrjProject> m_projectMap = new HashMap<String, PrjProject>();

	public ProjectConfig() {
	}

	public ProjectConfig(File cf) {
		m_configFile = cf;
	}

	public File getConfigFile() {
		if(m_configFile == null) {
			File home = new File(System.getProperty("user.home"));
			m_configFile = new File(home, ".projects.properties");
		}
		return m_configFile;
	}

	public void load() throws Exception {
		m_configSource = FileTool.loadProperties(m_configFile);
		m_projectMap.clear();
	}

	String getParameter(String name) {
		return m_configSource.getProperty(name);
	}

	public PrjProject findProject(String name) {
		name = name.toLowerCase().trim();
		PrjProject p = m_projectMap.get(name);
		if(p == null) {
			String s = m_configSource.getProperty(name + ".versions");
			if(s == null)
				return null;

			p = new PrjProject(this, name);
			m_projectMap.put(name, p);
		}
		return p;
	}

	public PrjVersion	getVersion(String prj, String ver) {
		PrjProject	p = findProject(prj);
		if(p == null)
			throw new ConfigException("Project '"+prj+"' is unknown");
		PrjVersion pv = p.findVersion(ver);
		if(pv == null)
			throw new ConfigException("Version '" + ver + " of project '" + prj + "' is unknown");
		return pv;
	}


}
