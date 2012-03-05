package to.etc.prjbuilder.util;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.scm.*;

public interface BuildReporter extends Reporter {
	public void setLogBranch(ScmBranch logTarget);
	public void setLogModule(SourceModule logModule);
}
