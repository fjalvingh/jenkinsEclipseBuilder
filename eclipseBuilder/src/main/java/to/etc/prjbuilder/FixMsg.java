package to.etc.prjbuilder;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.scm.*;
import to.etc.util.*;

/**
 * Holds a fix message used to create the fix and it's commits.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on May 17, 2010
 */
public class FixMsg {
	static private final String[]	LINUX = {
"/usr/bin/gedit", "/usr/bin/kate", "/usr/bin/vim", "/usr/bin/vi", "/usr/bin/nano"
	};

	static private final String[]	WINDOWS = {
"c:\\windows\\notepad.exe", "c:\\windows\\system32\\notepad.exe", "c:\\Program Files\\Windows NT\\Accessories\\wordpad.exe"
	};

	private String m_title;

	private String m_message;

	private List<String> m_callList;

	public String getTitle() {
		return m_title;
	}

	public void setTitle(String title) {
		m_title = title;
	}

	public String getMessage() {
		return m_message;
	}

	public void setMessage(String message) {
		m_message = message;
	}

	public List<String> getCallList() {
		return m_callList;
	}

	public void setCallList(List<String> callList) {
		m_callList = callList;
	}

	static public File getLogMsgFile(String relname) {
		String tmp = System.getProperty("java.io.tmpdir");
		File dir = new File(tmp);
		return new File(dir, relname);
	}

	/**
	 * This uses a local editor to let the user edit a fix message.
	 * @return
	 * @throws Exception
	 */
	static public FixMsg getFixMsg(ScmLogEntry le) throws Exception {
		File old = getLogMsgFile("lastmessage");
		String msg = "";

		boolean iswindows = File.separatorChar == '\\';
		if(old.exists()) {
			msg = FileTool.readFileAsString(old, "utf-8");
		} else {
			msg = le.getCommitMessage();
			msg.replace("\r\n", "\n");
		}
		msg = lrtrim(msg);

		String crlf = iswindows ? "\r\n" : "\n";

		StringBuilder sb = new StringBuilder();
		sb.append("Title: ").append(crlf);
		sb.append("FixedCalls: ").append(crlf).append(crlf);
		if(iswindows)
			sb.append(msg.replace("\n", "\r\n"));
		else
			sb.append(msg);
		sb.append(crlf).append(crlf);
		sb.append("-------- This line and everything below it is not copied to the message ----------------").append(crlf);

		//-- Store in tempfile, then start editor
		File tmp = getLogMsgFile("prjfix.txt");
		FileTool.writeFileFromString(tmp, sb.toString(), "utf-8");

		String[] paths = iswindows ? WINDOWS : LINUX;
		File ed = null;
		for(String path : paths) {
			File t = new File(path);
			if(t.exists()) {
				ed = t;
				break;
			}
		}
		if(ed == null) {
			System.err.println("I cannot find an editor to use...");
			return null;
		}

		//-- Execute the editor and wait for the edit to complete
		ProcessBuilder pb = new ProcessBuilder(ed.getAbsolutePath(), tmp.getAbsolutePath());
		Process proc = pb.start();
		int rc = proc.waitFor();
		if(rc != 0) {
			System.err.println("Editor returned exitcode=" + rc);
			return null;
		}

		//-- Read the result && decode;
		String result = FileTool.readFileAsString(tmp, "utf-8");
		result = lrtrim(result);
		if(result.length() == 0) {
			System.err.println("Comment empty: aborted.");
			return null;
		}

		String title = null;
		String line;
		LineNumberReader lr = new LineNumberReader(new StringReader(result));
		int elines = 0;
		sb.setLength(0);
		List<String> calls = new ArrayList<String>();
		while(null != (line = lr.readLine())) {
			String ss = lrtrim(line);
			String slc = ss.toLowerCase();
			if(slc.startsWith("------")) // Delimiter found -
				break;
			else if(ss.length() == 0)
				elines++;
			else if(slc.startsWith("title:")) {
				title = lrtrim(ss.substring(6));
			} else if(slc.startsWith("fixedcalls:")) {
				StringTokenizer st = new StringTokenizer(ss.substring(11), ", \t;");
				while(st.hasMoreTokens()) {
					String tok = st.nextToken();
					try {
						tok = tok.trim();
						Long call = Long.decode(tok);
						calls.add(call.toString());
					} catch(Exception x) {
						System.err.println("Invalid call number: " + tok);
						return null;
					}
				}
			} else {
				//-- Normal text...
				if(elines > 0 && sb.length() > 0) {
					sb.append("\n");
					elines = 0;
				}

				line = rtrim(line);
				sb.append(line);
				sb.append('\n');
			}
		}

		String newmsg = sb.toString();
		if(title == null) { // || (calls.size() == 0 && newmsg.equalsIgnoreCase(msg))) {
			System.err.println("Fix message not changed - aborting");
			return null;
		}

		FixMsg fm = new FixMsg();
		fm.m_callList = calls;
		fm.m_message = newmsg;
		fm.m_title = title;
		System.out.println("Title: " + fm.m_title);
		System.out.println("calls: " + fm.m_callList);
		System.out.println("Message: " + fm.m_message);
		return fm;
	}

	static private String lrtrim(String in) {
		int sx = 0;
		int ex = in.length();
		while(sx < ex) {
			char c = in.charAt(sx);
			if(!Character.isWhitespace(c))
				break;
			sx++;
		}

		while(ex > sx) {
			char c = in.charAt(ex - 1);
			if(!Character.isWhitespace(c))
				break;
			ex--;
		}

		return in.substring(sx, ex);
	}

	static private String rtrim(String in) {
		int ex = in.length();
		while(ex > 0) {
			char c = in.charAt(ex - 1);
			if(!Character.isWhitespace(c))
				break;
			ex--;
		}

		return in.substring(0, ex);
	}


}
