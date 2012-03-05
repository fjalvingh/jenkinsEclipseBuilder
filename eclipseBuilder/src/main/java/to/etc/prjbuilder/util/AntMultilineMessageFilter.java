package to.etc.prjbuilder.util;

import java.util.*;
import java.util.regex.*;

import to.etc.prjbuilder.builder.*;

public class AntMultilineMessageFilter implements MessageFilter {
	private final MessageFilter		m_previous;

	private final List<REMatch>				m_errorlist = new ArrayList<REMatch>();

	/** Matches any [javac] line */
	private Matcher m_mJavac;

	private Matcher m_mDashes;

	private Matcher m_mCarets;

	private Matcher m_mMessage;

	/** When != 0 we have just seen line n of a multiline crap message. */
	private int m_multiIndex;

	private String m_prevLine;

	private LogLineType m_prevType;

	private StringBuilder m_sb = new StringBuilder();

	static private class REMatch {
		private Matcher		m_matcher;
		private LogLineType	m_type;
		public REMatch(LogLineType type, Matcher matcher) {
			m_type = type;
			m_matcher = matcher;
		}

		public boolean	matches(String txt) {
			return m_matcher.reset(txt).find();
		}
		public LogLineType getType() {
			return m_type;
		}
		public Matcher getMatcher() {
			return m_matcher;
		}
	}

	public AntMultilineMessageFilter(MessageFilter previous) {
		m_previous = previous;

		addMatcher(LogLineType.CER, "^\\s-*\\[[^]]*\\]\\s-*\\(.+\\):\\([0-9]+\\):\\([0-9]+\\):[0-9]+:[0-9]+:");
		addMatcher(LogLineType.CER, "^\\s-*\\[[^]]*\\]\\s-*\\(.+\\):\\([0-9]+\\):");

		addMatcher(LogLineType.CWA, "^\\s*\\[[^\\]]*\\]\\s*.*:[0-9]+:\\s*warning");

		addMatcher(LogLineType.CER, "^\\s*\\[[^\\]]*\\]\\s*.*:[0-9]+:");
		addMatcher(LogLineType.CER, "^\\s*\\[javac\\]\\s*javac:"); // [javac] javac: errormessage, old style

		addMatcher(LogLineType.CER, "^\\s*\\[jasper\\]\\s*severe:");	// jasper JSP checker/compilert.

		//-- Multiline matcher
		m_mJavac = Pattern.compile("^\\s*\\[[jJ][aA][vV][aA][cC]\\]\\s*(.*)").matcher(""); //

		m_mDashes = Pattern.compile("^\\s*\\[javac\\]\\s*[-]+").matcher(""); // [javac] ----------

		m_mCarets = Pattern.compile("^\\s*\\[javac\\]\\s*[\\^]+").matcher(""); // [javac] ----------

		//		m_mMessage = Pattern.compile("^\\s*\\[javac\\]\\s*([0-9]+)\\.\\s*([a-z]+)\\s*in\\s*(.*)\\s*\\(at line ([0-9]+)\\)").matcher("");
		m_mMessage = Pattern.compile("^\\s*\\[[jJ][aA][vV][aA][cC]\\]\\s*([0-9]+)\\.\\s*([a-zA-Z]+)\\s*[iI][nN]\\s*(.*)\\s*\\([aA][tT] line ([0-9]+).*").matcher("");
	}


	public void	addMatcher(LogLineType t, String s) {
		Pattern	p = Pattern.compile(s);
		m_errorlist.add(new REMatch(t, p.matcher("")));
	}

	public void filterLine(MessageFilterSink r, String line) {
		String lc = line.toLowerCase();
		if(m_mCarets.reset(lc).find()) // Return caret location lines
			return;
		if(m_mDashes.reset(lc).find()) { //
			if(m_prevLine != null) {
				//-- Line with dashes means next error, previous line holds error message. Idiots.
				if(m_mJavac.reset(m_prevLine).matches() && m_sb.length() > 0) {
					//-- Got an actual message- add to buffer.
					m_sb.append(' ');
					m_sb.append(m_mJavac.group(1).trim());
					r.logRecord(m_prevType, m_sb.toString());
					m_sb.setLength(0);
					return;
				}

				//-- No previous line...
				if(m_sb.length() > 0) {
					r.logRecord(m_prevType, m_sb.toString());
					m_sb.setLength(0);
					return;
				}
				return;
			}
		}

		if(m_sb.length() > 0) {
			m_prevLine = line;
			return;
		}

		//-- Are we starting a multiline crapdoodle?
		if(m_mMessage.reset(line).matches()) {
			//-- Error message gotten!! Get regions,
			String type = m_mMessage.group(2).toUpperCase(); // ERROR, WARNING et al
			String path = m_mMessage.group(3).trim(); // File name
			String lnr = m_mMessage.group(4);

			m_sb.setLength(0);
			m_sb.append(path + "(" + lnr + ") " + type);
			//			System.out.println(path + "(" + lnr + ") " + type);

			if("ERROR".equals(type))
				m_prevType = LogLineType.CER;
			else if("WARNING".equals(type))
				m_prevType = LogLineType.CWA;
			else
				m_prevType = LogLineType.CDE;
			return;
		}

		for(REMatch m : m_errorlist) {
			if(m.matches(lc)) {
				r.logRecord(m.getType(), line);
				return;
			}
		}
		if(m_previous != null)
			m_previous.filterLine(r, line);
	}

//	static private void c(String txt) {
//		Pattern	p = Pattern.compile("^\\s*\\[[^\\]]*\\]\\s*.*:[0-9]+:");
//		Matcher	m = p.matcher(txt);
//		if(m.find())
//			System.out.println("Matches: "+txt+"\n----"+m.group()+"----");
//		else
//			System.out.println("Nomatch: "+txt);
//
//
//	}

	public static void main(String[] args) {
		String[] t = new String[]{ //
			"   [javac] /tmp/buildfiles/PONG/sources/to.etc.ponger/src/to/mumble/ponger/BetweenDeliveryMethod.java:20: cannot find symbol" //
			,
			"[javac] /home/jal/buildfiles/VP/sources/bin-hibernate-3.2.3/src/org/hibernate/util/GetGeneratedKeysHelper.java:38: warning: non-varargs call of varargs method with inexact argument type for last parameter;" //
			, "[javac] ----------" //
			, "[javac] 543. ERROR in /home/jal/buildfiles/vp-3.1-hot/branch-work/to.etc.server/src/to/etc/server/vfs/VfsSegmentResolver.java (at line 26)" //
			, "[javac] PathSplitter ps = new PathSplitter(rpath);" //
			, "[javac] ^^^^^^^^^^^^" //
			, "[javac] PathSplitter cannot be resolved to a type" //
			, "[javac] ----------" //

		};

		AntMultilineMessageFilter	f = new AntMultilineMessageFilter(null);

		if(false) {
			String s = "[javac] 543. ERROR in /home/jal/buildfiles/vp-3.1-hot/branch-work/to.etc.server/src/to/etc/server/vfs/VfsSegmentResolver.java (at line 26)";
			boolean ok = f.m_mMessage.reset(s).matches();
			System.out.println("Matches: " + ok + " " + s);
			return;
		}

		MessageFilterSink sink = new MessageFilterSink() {
			public void logRecord(LogLineType t, String line) {
				System.out.println("OUT: " + t + ": " + line);
			}
		};

		for(String s: t) {
			f.filterLine(sink, s);
		}

	}
}
