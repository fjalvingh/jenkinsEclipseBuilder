package to.etc.prjbuilder.prjs;

import java.util.*;

import to.etc.prjbuilder.*;

public class PrjVersion {
	private PrjProject m_project;

	private String		m_version;

	private String		m_master;

	private String		m_local;

	private String m_patchDir;

	private VersionPhase m_phase;

	private List<PrjVersion> m_mergeFromList = new ArrayList<PrjVersion>();

	public PrjVersion(PrjProject prjProject, String ver) {
		m_project = prjProject;
		m_version = ver;
		initialize();
	}

	private void initialize() {
		m_master = getParameter("master");
		if(m_master == null)
			throw new ConfigException("Missing 'master' repository URL for version " + this);
		try {
			String v = getParameter("phase");
			m_phase = VersionPhase.valueOf(v.toUpperCase().trim());
		} catch(Exception x) {
			throw new ConfigException("Missing or invalid 'phase' for version " + this);
		}
		m_local = getParameter("local");
		if(m_local == null)
			m_local = getProject().getName() + "-" + getVersion();
		m_patchDir = getParameter("patchdir");
	}

	public String getParameter(String name) {
		return m_project.getParameter(m_version + "." + name);
	}

	public String getVersion() {
		return m_version;
	}

	public PrjProject getProject() {
		return m_project;
	}

	public String getLocal() {
		return m_local;
	}

	public String getMaster() {
		return m_master;
	}

	public VersionPhase getPhase() {
		return m_phase;
	}

	public String getPatchDir() {
		return m_patchDir;
	}

	void addMerge(PrjVersion v) {
		m_mergeFromList.add(v);
	}

	public List<PrjVersion> getMergeFromList() {
		return m_mergeFromList;
	}

	@Override
	public String toString() {
		return m_project.getName() + "-" + getVersion();
	}
}
