package to.etc.prjbuilder.scm.bzr;

import java.io.*;
import java.util.*;

import to.etc.util.*;

public class BzrCommand {
	private int				m_exitCode;

	private String 			m_response;

	private File			m_currentDir;

	private List<String>	m_args = new ArrayList<String>();

	public BzrCommand(File currentDir) {
		m_currentDir = currentDir;
	}

	public BzrCommand() {}

	public void	add(String s) {
		m_args.add(s);
	}

	/**
	 * Builds a command.
	 * @param sb will be used to add the command to.
	 */
	public void bzr(String... args) {
		m_args.clear();
		add("bzr");
		for(String s: args)
			add(s);
	}
	public String getCommand() {
		StringBuilder	sb = new StringBuilder();
		for(String s: m_args) {
			if(sb.length() != 0)
				sb.append(',');
			sb.append(s);

		}
		return sb.toString();
	}

	/**
	 * Executes the command, but copies stdout to the specified stream. Used to capture binary
	 * content. The stderr stream will be copied to the result string buffer as usual.
	 * @param stdout
	 * @throws Exception
	 */
	public int execute(OutputStream stdout) throws Exception {
		//		System.out.println("bzr: running (copying-stdout) in dir " + m_currentDir.toString());
		//		System.out.println("cmd: " + getCommand());

		ProcessBuilder pb = new ProcessBuilder(m_args);
		if(m_currentDir != null)
			pb.directory(m_currentDir);

		StringBuilder iosb = new StringBuilder();
		m_exitCode = ProcessTools.runProcess(pb, stdout, iosb);
		m_response = iosb.toString();
		return m_exitCode;
	}

	/**
	 * Execute the collected command. Merge stdout and stderr into the result buffer.
	 * @return
	 */
	public int execute() throws Exception {
		//		System.out.println("bzr: running in dir "+m_currentDir.toString());
		//		System.out.println("cmd: "+getCommand());

		ProcessBuilder pb = new ProcessBuilder(m_args);
		if(m_currentDir != null)
			pb.directory(m_currentDir);

		StringBuilder iosb = new StringBuilder();
		m_exitCode = ProcessTools.runProcess(pb, iosb);
		m_response = iosb.toString();
		return m_exitCode;
	}

	public int getExitCode() {
		return m_exitCode;
	}

	public String getResponse() {
		return m_response;
	}

}
