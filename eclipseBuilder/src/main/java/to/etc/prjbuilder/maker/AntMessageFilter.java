package to.etc.prjbuilder.maker;

import java.util.*;
import java.util.regex.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.util.*;

public class AntMessageFilter implements MessageFilter {
	private final MessageFilter		m_previous;

	private final List<REMatch>				m_errorlist = new ArrayList<REMatch>();

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

	public AntMessageFilter(MessageFilter previous) {
		m_previous = previous;

		addMatcher(LogLineType.CER, "^\\s-*\\[[^]]*\\]\\s-*\\(.+\\):\\([0-9]+\\):\\([0-9]+\\):[0-9]+:[0-9]+:");
		addMatcher(LogLineType.CER, "^\\s-*\\[[^]]*\\]\\s-*\\(.+\\):\\([0-9]+\\):");

		addMatcher(LogLineType.CWA, "^\\s*\\[[^\\]]*\\]\\s*.*:[0-9]+:\\s*warning");

		addMatcher(LogLineType.CER, "^\\s*\\[[^\\]]*\\]\\s*.*:[0-9]+:");
		addMatcher(LogLineType.CER, "^\\s*\\[javac\\]\\s*javac:");		// [javac] javac: errormessage

		addMatcher(LogLineType.CER, "^\\s*\\[jasper\\]\\s*severe:");	// jasper JSP checker/compilert.


	}
	public void	addMatcher(LogLineType t, String s) {
		Pattern	p = Pattern.compile(s);
		m_errorlist.add(new REMatch(t, p.matcher("")));
	}

	public void filterLine(MessageFilterSink r, String line) {
		String lc = line.toLowerCase();
		for(REMatch m : m_errorlist) {
			if(m.matches(lc)) {
				r.logRecord(m.getType(), line);
				return;
			}
		}
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
			, "[javac] ----------", "[javac] 543. ERROR in /home/jal/buildfiles/vp-3.1-hot/branch-work/to.etc.server/src/to/etc/server/vfs/VfsSegmentResolver.java (at line 26)" //
			, "[javac] PathSplitter ps = new PathSplitter(rpath);", "[javac] ^^^^^^^^^^^^", "[javac] PathSplitter cannot be resolved to a type" //
		};

		AntMessageFilter	f = new AntMessageFilter(null);

		for(String s: t) {
			System.out.println("input: "+s);
			for(REMatch m : f.m_errorlist) {
				if(m.matches(s))
					System.out.println(m.getType()+": ----"+m.getMatcher().group()+"----");
				else
					System.out.println("Nomatch");
			}


		}

	}
}
