package to.etc.prjbuilder;

import java.io.*;

import to.etc.prjbuilder.util.*;
import to.etc.util.*;

public class PrjBuildReporter implements Reporter {
	private File m_logFile;

	private PrintWriter m_pw;

	public PrjBuildReporter(File logFile) {
		m_logFile = logFile;
	}

	private PrintWriter pw() {
		if(m_pw == null) {
			try {
				m_pw	= new PrintWriter(new OutputStreamWriter(new FileOutputStream(m_logFile), "utf-8"));
			} catch(Exception x) {
				throw WrappedException.wrap(x);
			}
		}
		return m_pw;
	}

	public void close() throws IOException {
		if(m_pw != null) {
			m_pw.close();
			m_pw = null;
		}
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

	public void logRecord(LogLineType t, String line) {
		pw().print(t.toString());
		pw().print(": ");
		pw().println(line);
		switch(t){
			default:
				break;

			case CER:
			case ERR:
			case HDR:
			case IMP:
				System.out.println(t + ": " + line);
				break;
		}
	}
}
