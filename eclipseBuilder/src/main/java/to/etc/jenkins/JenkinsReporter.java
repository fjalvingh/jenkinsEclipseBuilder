package to.etc.jenkins;

import hudson.model.*;

import to.etc.prjbuilder.util.*;

public class JenkinsReporter implements Reporter {
	final private BuildListener m_listener;

	private int m_errCount;

	public JenkinsReporter(BuildListener listener) {
		m_listener = listener;
	}

	public void detail(String s) {
		logRecord(LogLineType.DET, s);
	}

	public void error(String s) {
		logRecord(LogLineType.ERR, s);
	}

	public void exception(Throwable t, String where) {
		logRecord(LogLineType.ERR, where + ": " + t);
		t.printStackTrace();
	}

	public void header(String s) {
		logRecord(LogLineType.HDR, s);
	}

	public void important(String s) {
		logRecord(LogLineType.IMP, s);
	}

	public void log(String s) {
		logRecord(LogLineType.LOG, s);
	}

	public int getErrCount() {
		return m_errCount;
	}

	public void logRecord(LogLineType t, String line) {
		m_listener.getLogger().println(t + ": " + line);

		switch(t){
			default:
				break;

			case ERR:
			case CER:
				m_errCount++;
				m_listener.error(t + ": " + line);
				break;

			case HDR:
			case IMP:
				System.out.println(t + ": " + line);
				break;
		}
	}
}
