package to.etc.prjbuilder.scm;

public class ScmLogEntry {
	private String m_revisionID;

	private String m_revno;

	private String m_commiter;

	private String m_branchNick;

	private String m_timestamp;

	private String m_commitMessage;

	public ScmLogEntry(String revisionID, String revno, String commiter, String branchNick, String timestamp, String commitMessage) {
		m_revisionID = revisionID;
		m_revno = revno;
		m_commiter = commiter;
		m_branchNick = branchNick;
		m_timestamp = timestamp;
		m_commitMessage = commitMessage;
	}

	public String getRevisionID() {
		return m_revisionID;
	}

	public String getRevno() {
		return m_revno;
	}

	public String getCommiter() {
		return m_commiter;
	}

	public String getBranchNick() {
		return m_branchNick;
	}

	public String getTimestamp() {
		return m_timestamp;
	}

	public String getCommitMessage() {
		return m_commitMessage;
	}
}