package to.etc.prjbuilder.scm;


public class ScmException extends RuntimeException {
	private String m_details;

	public ScmException(String msg) {
		super(msg);
	}

	public ScmException(String msg, String details) {
		super(msg);
		m_details = details;
	}

	public ScmException(Throwable arg0) {
		super(arg0);
	}

	public ScmException(Throwable arg1, String msg) {
		super(msg, arg1);
	}

	public String getDetails() {
		return m_details;
	}

	/**
	 * Create an SCM exception from a base message and the content of the command's output. The output
	 * is scanned and the last lines of it are added to the base message if meaningful.
	 * @param basemsg
	 * @param console
	 * @return
	 */
	static public ScmException create(String basemsg, String console) {
		if(console == null || console.trim().length() == 0)
			return new ScmException(basemsg);

		String m = console.trim();
		int ix = m.length();
		int lines = 0;
		String res = null;
		while(ix > 0) {
			int lp = m.lastIndexOf('\n', ix - 1);
			if(lp == -1) {
				//-- Reached the start. This is the last line to try.
				String s = m.substring(0, ix).trim();
				if(s.length() > 0) {
					if(res == null)
						res = s;
					else
						res = s + "\n" + s;
				}
				break;
			}

			//-- Construct the substring just found.
			String s = m.substring(lp + 1, ix).trim();
			if(s.length() > 0) {
				if(res == null)
					res = s;
				else
					res = s + "\n" + res;
				lines++;
				if(lines >= 2)
					break;
			}
			ix = lp;
		}
		if(res == null)
			return new ScmException(basemsg);
		return new ScmException(basemsg + ": " + res, console);
	}
}
