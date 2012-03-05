package to.etc.prjbuilder.builder;

import to.etc.prjbuilder.util.*;

public interface MessageFilterSink {
	void logRecord(LogLineType t, String line);
}
