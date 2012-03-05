package to.etc.prjbuilder.scm;

import java.io.*;

public interface IScmHandler {
	String getScmName();

	ScmBranch getBranch(String location) throws Exception;

	ScmBranch getBranch(File location) throws Exception;

	boolean isLocalRepository(File path) throws Exception;

	boolean isBranch(ScmBranch branch) throws Exception;

	boolean isBranchWithSources(ScmBranch branch) throws Exception;

}
