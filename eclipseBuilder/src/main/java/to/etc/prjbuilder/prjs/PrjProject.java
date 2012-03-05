package to.etc.prjbuilder.prjs;

import java.util.*;

import to.etc.prjbuilder.*;
import to.etc.prjbuilder.util.*;

public class PrjProject {
	private ProjectConfig m_config;

	private String m_name;

	private String m_mainModule;

	private String m_pristine;

	private String m_privateKey;

	private String m_passPhrase;

	private String m_patchHost;

	private String m_patchUser;

	private Map<String, PrjVersion> m_versionMap = new HashMap<String, PrjVersion>();

	PrjProject(ProjectConfig projectConfig, String name) {
		m_config = projectConfig;
		m_name = name;
		initialize();
	}

	public ProjectConfig getConfig() {
		return m_config;
	}

	public String getName() {
		return m_name;
	}

	public String getPrivateKey() {
		return m_privateKey;
	}

	public String getPatchHost() {
		return m_patchHost;
	}

	public String getPatchUser() {
		return m_patchUser;
	}

	public String getPassPhrase() {
		return m_passPhrase;
	}

	public String	getParameter(String name) {
		return m_config.getParameter(getName() + "." + name);
	}

	private void initialize() {
		m_mainModule = getParameter("mainmodule");
		if(m_mainModule == null)
			throw new ConfigException("Missing 'mainmodule' for project " + getName());
		m_pristine = getParameter("pristine");
		if(m_pristine == null)
			m_pristine = "pristine-";
		m_patchHost = getParameter("patchhost");
		if(null == m_patchHost)
			throw new ConfigException("Missing 'patchhost' for project " + getName());
		m_patchUser = getParameter("patchuser");
		if(null == m_patchUser)
			throw new ConfigException("Missing 'patchuser' for project " + getName());
		m_privateKey = getParameter("privatekey");
		if(null == m_privateKey)
			throw new ConfigException("Missing 'privatekey' for project " + getName());
		m_passPhrase = getParameter("passphrase");
		if(null == m_privateKey)
			throw new ConfigException("Missing 'passphrase' for project " + getName());

		String vers = getParameter("versions");
		if(vers == null)
			throw new ConfigException("Missing 'versions' for project " + getName());
		StringTokenizer st = new StringTokenizer(vers, " ,\t");
		while(st.hasMoreElements()) {
			String ver = st.nextToken();
			appendVersion(ver);
		}

		//-- Handle all merge-from lists
		for(PrjVersion pv : m_versionMap.values()) {
			String merges = pv.getParameter("merges");
			if(merges == null)
				continue;
			st = new StringTokenizer(merges, " \t,");
			while(st.hasMoreTokens()) {
				String ver = st.nextToken().trim().toLowerCase();
				ver = BuildUtil.normalizeVersion(ver);

				PrjVersion fromver = m_versionMap.get(ver);
				if(fromver == null) {
					throw new ConfigException("Error in 'merges' property for " + pv + ": the merge-from version '" + ver + "' does not exist");
				}
				pv.addMerge(fromver);
			}
		}
	}

	private void appendVersion(String ver) {
		ver = ver.trim().toLowerCase();
		ver = BuildUtil.normalizeVersion(ver);
		PrjVersion pv = new PrjVersion(this, ver);
		m_versionMap.put(ver, pv);
	}

	public PrjVersion findVersion(String name) {
		name = name.trim().toLowerCase();
		name = BuildUtil.normalizeVersion(name);
		return m_versionMap.get(name);
	}
}
