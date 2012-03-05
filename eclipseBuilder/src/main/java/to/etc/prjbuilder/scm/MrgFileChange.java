package to.etc.prjbuilder.scm;

import java.io.*;
import java.util.*;

/**
 * A file change record.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Feb 3, 2010
 */
public class MrgFileChange implements Serializable {
	static private final long serialVersionUID = 120;

	/** The relative file name for the changed file. */
	private String m_name;

	private MrgChangeType m_type;

	/** If this was a rename this contains the <b>old</b> name of the file. */
	private String m_oldName;

	private boolean m_isDirectory;

	private List<MrgLineDelta> m_deltaList = new ArrayList<MrgLineDelta>();

	private int m_addedLineCount, m_deletedLineCount;

	public MrgFileChange(MrgChangeType ct, boolean isdir, String target, String original) {
		m_type = ct;
		m_isDirectory = isdir;
		m_name = target;
		m_oldName = original;
	}

	public String getName() {
		return m_name;
	}

	public void setName(String name) {
		m_name = name;
	}

	public MrgChangeType getType() {
		return m_type;
	}

	public void setType(MrgChangeType type) {
		m_type = type;
	}

	public String getOldName() {
		return m_oldName;
	}

	public void setOldName(String oldName) {
		m_oldName = oldName;
	}

	public boolean isDirectory() {
		return m_isDirectory;
	}

	public void setDirectory(boolean directory) {
		m_isDirectory = directory;
	}

	public List<MrgLineDelta> getDeltaList() {
		return m_deltaList;
	}

	public void setDeltaList(List<MrgLineDelta> deltaList) {
		m_deltaList = deltaList;
	}

	public void setLineCounts(int del, int add) {
		m_addedLineCount = add;
		m_deletedLineCount = del;
	}

	public int getAddedLineCount() {
		return m_addedLineCount;
	}

	public int getDeletedLineCount() {
		return m_deletedLineCount;
	}

	/**
	 * Dump this change to some output.
	 * @param a
	 */
	public void dump(Appendable a) throws Exception {
		a.append("=== ").append(getType().toString()).append(" ").append(getName());
		if(getOldName() != null) {
			a.append(" renamed from ").append(getOldName());
		}
		a.append(isDirectory() ? " (dir)" : " (file)");
		if(m_deletedLineCount != 0)
			a.append(" -").append(Integer.toString(m_deletedLineCount));

		if(m_addedLineCount != 0)
			a.append(" +").append(Integer.toString(m_addedLineCount));
		a.append("\n");

		for(MrgLineDelta ld: m_deltaList)
			ld.dump(a);
	}
}
