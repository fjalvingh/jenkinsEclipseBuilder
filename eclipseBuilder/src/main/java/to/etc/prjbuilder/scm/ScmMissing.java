package to.etc.prjbuilder.scm;

import java.util.*;

/**
 * Result of comparing branch A with branch B.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Nov 24, 2009
 */
public class ScmMissing {
	final private int m_missingInB;

	final private int m_missingInA;

	final private List<ScmLogEntry> m_missingInBList;

	final private List<ScmLogEntry> m_missingInAList;

	public ScmMissing(int missingInB, int missingInA, List<ScmLogEntry> missingInBList, List<ScmLogEntry> missingInAList) {
		m_missingInB = missingInB;
		m_missingInA = missingInA;
		m_missingInBList = missingInBList;
		m_missingInAList = missingInAList;
	}

	/**
	 * In the comparison of A with B, this contains the revisions of A that are missing in B, or
	 * the revisions that would be <i>added</i> to B from A if A merged into B.
	 *
	 * @return
	 */
	public int getMissingInB() {
		return m_missingInB;
	}

	public int getMissingInA() {
		return m_missingInA;
	}

	public List<ScmLogEntry> getMissingInBList() {
		return m_missingInBList;
	}

	public List<ScmLogEntry> getMissingInAList() {
		return m_missingInAList;
	}

	/**
	 * Return true if both branches contain the same version.
	 * @return
	 */
	public boolean areBranchesEqual() {
		return getMissingInA() == 0 && getMissingInB() == 0;
	}

	/**
	 * Returns T if branch A is fully contained in B. This is the case if B is not missing anything from A.
	 * @return
	 */
	public boolean isAContainedInB() {
		return getMissingInB() == 0;
	}

	public boolean isBContainedInA() {
		return getMissingInA() == 0;
	}

	/**
	 * Returns T if both branches have diverged.
	 * @return
	 */
	public boolean isDiverged() {
		return getMissingInA() > 0 && getMissingInB() > 0;
	}
}
