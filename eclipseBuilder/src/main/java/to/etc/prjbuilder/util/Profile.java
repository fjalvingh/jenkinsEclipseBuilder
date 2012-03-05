package to.etc.prjbuilder.util;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.scm.*;

public interface Profile {
	static public final String	ENCODING	= "encoding";

	public String		getName();

	public String getProperty(ScmBranch t, SourceModule smv, String key);

}
