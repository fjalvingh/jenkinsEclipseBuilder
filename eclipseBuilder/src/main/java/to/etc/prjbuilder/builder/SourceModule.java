package to.etc.prjbuilder.builder;

import to.etc.prjbuilder.scm.*;

public class SourceModule {
	private ScmBranch m_branch;

	private String m_name;

	public SourceModule(ScmBranch branch, String name) {
		m_branch = branch;
		m_name = name;
	}

	public ScmBranch getBranch() {
		return m_branch;
	}

	public String getName() {
		return m_name;
	}

	@Override
	public String toString() {
		return m_name + "@" + m_branch;
	}
}
