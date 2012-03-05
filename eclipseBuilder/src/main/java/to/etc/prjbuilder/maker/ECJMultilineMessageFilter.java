package to.etc.prjbuilder.maker;

import java.util.*;
import java.util.regex.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.util.*;

/**
 * This scans for messages in the horrible format exposed by ECJ.
 *
 * <pre>
 * ----------
 * 1. ERROR in /var/puzzler/repositories/vp-trunk/to.etc.eclipse.daogen/src/to/etc/eclipse/daogen/builder/ToggleNatureAction.java (at line 5)
 *         import org.eclipse.core.resources.IProject;
 *                ^^^^^^^^^^^
 * The import org.eclipse cannot be resolved
 * ----------
 * 2. ERROR in /var/puzzler/repositories/vp-trunk/to.etc.eclipse.daogen/src/to/etc/eclipse/daogen/builder/ToggleNatureAction.java (at line 6)
 *         import org.eclipse.core.resources.IProjectDescription;
 *                ^^^^^^^^^^^
 * The import org.eclipse cannot be resolved
 * ----------
 * 3. ERROR in /var/puzzler/repositories/vp-trunk/to.etc.eclipse.daogen/src/to/etc/eclipse/daogen/builder/ToggleNatureAction.java (at line 7)
 *         import org.eclipse.core.runtime.CoreException;
 *                ^^^^^^^^^^^
 * The import org.eclipse cannot be resolved
 * ----------
 * </pre>
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Dec 3, 2010
 */
public class ECJMultilineMessageFilter implements MessageFilter {
	private final MessageFilter		m_previous;

	private final List<REMatch>				m_errorlist = new ArrayList<REMatch>();

	/** Matches any [javac] line */
//	private Matcher m_mJavac;

	private Matcher m_mDashes;

	private Matcher m_mCarets;

	private Matcher m_mMessage;

	/** When != 0 we have just seen line n of a multiline crap message. */
	private int m_multiIndex;

	private String m_prevLine;

	/** The line of code where the error was found. */
	private String m_codeLine;

	/** The indicator carets where the error was found. */
	private String m_caretLine;

	private LogLineType m_prevType;

	private StringBuilder m_sb = new StringBuilder();

	private static enum Phase {
		/** Initial phase. This recognises normal constructs plus the "1. ERROR xxxx" construct. */
		NONE,

		/** Expecting line data phase. This expects the source line, like "import org.eclipse.core.resources.IProjectDescription;" */
		LINE,

		/** Expecting the message as a line. It skips any ^^^ and handles ---- in a special way. */
		MSG
	}

	private Phase m_phase = Phase.NONE;

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

	public ECJMultilineMessageFilter(MessageFilter previous) {
		m_previous = previous;

		addMatcher(LogLineType.CER, "^\\s-*\\[[^]]*\\]\\s-*\\(.+\\):\\([0-9]+\\):\\([0-9]+\\):[0-9]+:[0-9]+:");
		addMatcher(LogLineType.CER, "^\\s-*\\[[^]]*\\]\\s-*\\(.+\\):\\([0-9]+\\):");

		addMatcher(LogLineType.CWA, "^\\s*\\[[^\\]]*\\]\\s*.*:[0-9]+:\\s*warning");

		addMatcher(LogLineType.CER, "^\\s*\\[[^\\]]*\\]\\s*.*:[0-9]+:");
		addMatcher(LogLineType.CER, "^\\s*\\[javac\\]\\s*javac:"); // [javac] javac: errormessage, old style

		addMatcher(LogLineType.CER, "^\\s*\\[jasper\\]\\s*severe:");	// jasper JSP checker/compilert.

		//-- Multiline matcher
//		m_mJavac = Pattern.compile("^\\s*\\[[jJ][aA][vV][aA][cC]\\]\\s*(.*)").matcher(""); //

		m_mDashes = Pattern.compile("^\\s*[-]+").matcher(""); // ----------

		m_mCarets = Pattern.compile("^\\s*[\\^]+").matcher(""); // ^^^^^

		//		m_mMessage = Pattern.compile("^\\s*\\[javac\\]\\s*([0-9]+)\\.\\s*([a-z]+)\\s*in\\s*(.*)\\s*\\(at line ([0-9]+)\\)").matcher("");

		/*
		 * This recognises a line like:
		 * 1. ERROR in /var/puzzler/repositories/vp-trunk/to.etc.eclipse.daogen/src/to/etc/eclipse/daogen/builder/ToggleNatureAction.java (at line 5)
		 * and leaves the message type, file name and line number in regions.
		 */
		m_mMessage = Pattern.compile("^\\s*([0-9]+)\\.\\s*([a-zA-Z]+)\\s*[iI][nN]\\s*(.*)\\s*\\([aA][tT] line ([0-9]+).*").matcher("");
	}


	public void	addMatcher(LogLineType t, String s) {
		Pattern	p = Pattern.compile(s);
		m_errorlist.add(new REMatch(t, p.matcher("")));
	}


	/**
	 * Main method to recognise error and warning messages. This method will use state left by
	 * the <i>previous</i> call to decide what to do with the current line.
	 *
	 * @see to.etc.saram.bld.builder.MessageFilter#filterLine(to.etc.saram.bld.builder.MessageFilterSink, java.lang.String)
	 */

	public void filterLine(MessageFilterSink r, String line) {
		String lc = line.toLowerCase().trim();
		if(lc.length() == 0)
			return;

		switch(m_phase){
			default:
				throw new IllegalStateException("Phase: " + m_phase);

			case NONE:
				handleInitialLine(r, line, lc);
				return;

			case LINE:
				handleCodeLine(r, line, lc);
				return;

			case MSG:
				handleMessageLine(r, line, lc);
				return;
		}
	}

	/**
	 * Handle the message line. If a caret line is found it is stored too.
	 * @param r
	 * @param line
	 * @param lc
	 */
	private void handleMessageLine(MessageFilterSink r, String line, String lc) {
		if(m_mDashes.reset(lc).find()) {
			/*
			 * Got dashes: no code line. The thing used as code line is the message.
			 */
			m_phase = Phase.NONE;
			if(m_codeLine != null)
				m_sb.append(m_codeLine);
			else {
				m_sb.append("(filter did not locate a message)");
			}
			r.logRecord(m_prevType, m_sb.toString());
			return;
		}

		if(m_mCarets.reset(lc).find()) {
			m_caretLine = line;
			return;
		}

		//-- Current line is message. Complete error
		m_sb.append(line.trim());

		if(m_codeLine != null)
			r.logRecord(m_prevType, m_codeLine);
		if(m_caretLine != null)
			r.logRecord(m_prevType, m_caretLine.replace(' ', '\u00a0'));
		r.logRecord(m_prevType, m_sb.toString());
		m_phase = Phase.NONE;
		m_sb.setLength(0);
		m_codeLine = m_caretLine = null;
	}


	/**
	 * Handle the code line, the line of text immediately after the ERROR line. If
	 * that line is empty, dashed or careted ignore it.
	 * @param r
	 * @param line
	 * @param lc
	 */
	private void handleCodeLine(MessageFilterSink r, String line, String lc) {
		if(m_mDashes.reset(lc).find()) { // Unexpected dashes- ignore.
			m_phase = Phase.NONE;
			return;
		}
		if(m_mCarets.reset(lc).find())
			return;
		m_codeLine = line;
		m_phase = Phase.MSG;
	}


	/**
	 * Scan for the ERROR line, or any other normally-handled pattern.
	 *
	 * @param r
	 * @param line
	 * @param lc
	 */
	private void handleInitialLine(MessageFilterSink r, String line, String lc) {
		if(m_mMessage.reset(line).matches()) {
			//-- Error message gotten!! Get regions,
			String type = m_mMessage.group(2).toUpperCase(); // ERROR, WARNING et al
			String path = m_mMessage.group(3).trim(); // File name
			String lnr = m_mMessage.group(4);

			m_codeLine = null;
			m_caretLine = null;
			m_sb.setLength(0);
			m_sb.append(path).append("(").append(lnr).append(") ").append(type).append(" ");

			if("ERROR".equals(type))
				m_prevType = LogLineType.CER;
			else if("WARNING".equals(type))
				m_prevType = LogLineType.CWA;
			else
				m_prevType = LogLineType.CDE;
			m_phase = Phase.LINE;
			return;
		}
		if(m_mDashes.reset(lc).find()) { // Unexpected dashes- ignore.
			return;
		}
		if(m_mCarets.reset(lc).find())
			return;


		//-- Any other supported single line pattern?
		for(REMatch m : m_errorlist) {
			if(m.matches(lc)) {
				r.logRecord(m.getType(), line);
				return;
			}
		}
//		if(null != m_previous)
//			m_previous.filterLine(r, line);
	}


	public static void main(String[] args) {
		String[] t = new String[]{ //
			"   [javac] /tmp/buildfiles/PONG/sources/to.etc.ponger/src/to/mumble/ponger/BetweenDeliveryMethod.java:20: cannot find symbol" //
			,
			"[javac] /home/jal/buildfiles/VP/sources/bin-hibernate-3.2.3/src/org/hibernate/util/GetGeneratedKeysHelper.java:38: warning: non-varargs call of varargs method with inexact argument type for last parameter;" //
			, "----------" //
			, "543. ERROR in /home/jal/buildfiles/vp-3.1-hot/branch-work/to.etc.server/src/to/etc/server/vfs/VfsSegmentResolver.java (at line 26)" //
			, "PathSplitter ps = new PathSplitter(rpath);" //
			, "^^^^^^^^^^^^" //
			, "PathSplitter cannot be resolved to a type" //
			, "----------" //

		};

		ECJMultilineMessageFilter	f = new ECJMultilineMessageFilter(null);

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
