package to.etc.prjbuilder.builder;

import to.etc.prjbuilder.scm.*;

/**
 * Handles build events from the build manager.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on May 11, 2010
 */
public interface IBuildListener {
	void branchBuildStarted(ScmBranch b, BuildMode buildMode, BuildIntentType buildIntentType);

	void branchBuildCompleted(ScmBranch b, BuildStatus status, String message) throws Exception;

	void setCurrentModule(ScmBranch b, SourceModule m) throws Exception;

	void moduleBuildStarted(ModuleBuildInfo bi);

	void moduleBuildCompleted(ModuleBuildInfo bi, ModuleBuildStatus buildStatus, String buildError);
}
