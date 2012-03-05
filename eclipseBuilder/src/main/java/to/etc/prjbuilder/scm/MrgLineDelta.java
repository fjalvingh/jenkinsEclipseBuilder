package to.etc.prjbuilder.scm;

import java.io.*;
import java.util.*;

/**
 * Encapsulates a line delta: a consecutive list of changes to a set of lines, at a given
 * location in both files.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Feb 13, 2010
 */
public class MrgLineDelta implements Serializable {
	static private final long serialVersionUID = 120;

	/** The line # in the old file where this change occurs. */
	private int m_oldLine;

	/** The line # in the new file where this change occurs. */
	private int m_newLine;

	/** The content of the added lines. */
	private List<String> m_addedList = new ArrayList<String>();

	/** The content of the deleted lines. */
	private List<String> m_deletedList = new ArrayList<String>();

	public MrgLineDelta(int oldLine, int newLine) {
		m_oldLine = oldLine;
		m_newLine = newLine;
	}

	public int getOldLine() {
		return m_oldLine;
	}

	public void setOldLine(int oldLine) {
		m_oldLine = oldLine;
	}

	public int getNewLine() {
		return m_newLine;
	}

	public void setNewLine(int newLine) {
		m_newLine = newLine;
	}

	public int getAddCount() {
		return m_addedList.size();
	}

	public int getDelCount() {
		return m_deletedList.size();
	}

	public List<String> getAdded() {
		return m_addedList;
	}

	public List<String> getDeleted() {
		return m_deletedList;
	}

	public void dump(Appendable a) throws Exception {
		//-- Dump this delta-
		a.append("@@ -").append(Integer.toString(getDelCount())).append(" @old# ").append(Integer.toString(m_oldLine)).append(" +").append(Integer.toString(getAddCount())).append(" @new# ")
			.append(Integer.toString(m_newLine)).append("\n");
		for(String s : getDeleted())
			a.append("DEL ").append(s).append("\n");

		for(String s : getAdded())
			a.append("ADD ").append(s).append("\n");
	}
}
