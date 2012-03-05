package to.etc.prjbuilder.builder;

public class ModuleFileRef {
	private String		m_module;

	private String		m_relpath;

	public ModuleFileRef(String module, String relpath) {
		m_module = module;
		m_relpath = relpath;
	}
	@Override
	public String toString() {
	    return "ModuleFileRef: "+m_module+", relpath="+m_relpath;
	}

	public String getModule() {
		return m_module;
	}

	public String getRelpath() {
		return m_relpath;
	}
}
