package to.etc.prjbuilder.util;

import to.etc.prjbuilder.builder.*;

public interface Reporter extends MessageFilterSink {
	public void	header(String s);
	public void	important(String s);
	public void	error(String s);
	public void	log(String s);
	public void	detail(String s);
	public void	exception(Throwable t, String where);

	public void	logRecord(LogLineType t, String line);
}
