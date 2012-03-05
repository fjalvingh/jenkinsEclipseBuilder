package to.etc.prjbuilder.scm;

import java.io.*;

import to.etc.prjbuilder.scm.bzr.*;

/**
 * TEMP Represents some SCM branch
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on May 11, 2010
 */
public class ScmBranch {
	private IScmHandler m_handler;

	private String m_path;

	private File m_pathAsFile;

	public ScmBranch(IScmHandler handler, String path) {
		m_handler = handler;
		m_path = path;
	}

	public ScmBranch(BzrHandler handler, File location) {
		m_handler = handler;
		m_pathAsFile = location;
		m_path = location.getAbsolutePath();
	}

	public IScmHandler getHandler() {
		return m_handler;
	}

	public String getPath() {
		return m_path;
	}

	public File getPathAsFile() {
		if(m_pathAsFile == null) {
			m_pathAsFile = new File(getPath());
		}
		return m_pathAsFile;
	}

	@Override
	public String toString() {
		return getPath() + " (" + m_handler.getScmName() + ")";
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Delegates to Handler.								*/
	/*--------------------------------------------------------------*/
	/**
	 *
	 * @return
	 * @throws Exception
	 */
	public boolean isBranch() throws Exception {
		return m_handler.isBranch(this);
	}

	public boolean isBranchWithSources() throws Exception {
		return m_handler.isBranchWithSources(this);
	}


}
