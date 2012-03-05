package to.etc.prjbuilder.scm.bzr;

import java.io.*;

import to.etc.prjbuilder.scm.*;

public class Bzr {

	static public boolean isRepository(File path) {
		return BzrHandler.getInstance().isLocalRepository(path);
	}

	static public boolean isBranch(File path) throws Exception {
		ScmBranch br = BzrHandler.getInstance().getBranch(path.getAbsolutePath());
		return br.isBranch();
	}

	static public boolean isBranchWithSources(File path) throws Exception {
		ScmBranch br = BzrHandler.getInstance().getBranch(path.getAbsolutePath());
		return br.isBranchWithSources();
	}

}
