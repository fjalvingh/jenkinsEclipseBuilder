package to.etc.prjbuilder.builder;

import to.etc.prjbuilder.util.*;

/**
 * Filter of last resort; this logs the data at the lowest level possible.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 17, 2007
 */
public class FinalLogFilter implements MessageFilter {
	public void filterLine(MessageFilterSink r, String line) {
		if(line.trim().length() == 0)
			return;
		r.logRecord(LogLineType.CDE, line);
	}
}
