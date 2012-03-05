package to.etc.prjbuilder.builder;

import java.io.*;
import java.util.*;

import to.etc.util.*;

/**
 * Persistent class storing the build's details in the build directory.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on May 8, 2010
 */
public class BuildDetails implements Serializable {
	private int m_buildNr;

	private String m_onRevision;

	private Date m_onDate;

	public BuildDetails() {}

	public int getBuildNr() {
		return m_buildNr;
	}

	public void setBuildNr(int buildNr) {
		m_buildNr = buildNr;
	}

	public String getOnRevision() {
		return m_onRevision;
	}

	public void setOnRevision(String onRevision) {
		m_onRevision = onRevision;
	}

	public Date getOnDate() {
		return m_onDate;
	}

	public void setOnDate(Date onDate) {
		m_onDate = onDate;
	}

	/**
	 * Store this instance.
	 * @param target
	 * @throws IOException
	 */
	public void store(File target) throws IOException {
		FileTool.saveSerialized(new File(target, "builddetails.ser"), this);
	}

	/**
	 * Load build details; return null on any failure.
	 * @param source
	 * @return
	 */
	static public BuildDetails	load(File source) {
		try {
			return (BuildDetails) FileTool.loadSerialized(new File(source, "builddetails.ser"));
		} catch(Exception x) {
			return null;
		}
	}

	static public void delete(File source) throws IOException {
		File f = new File(source, "builddetails.ser");
		f.delete();
		if(f.exists())
			throw new IOException("Cannot delete " + f);
	}
}
