package to.etc.prjbuilder.scm.bzr;

import java.io.*;
import java.util.*;

import org.w3c.dom.*;

import to.etc.lexer.*;
import to.etc.prjbuilder.scm.*;
import to.etc.prjbuilder.util.*;
import to.etc.util.*;
import to.etc.xml.*;

/**
 * Interface to handle bzr commands.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on May 28, 2008
 */
public class BzrHandler implements IScmHandler {
	static private final BzrHandler INSTANCE = new BzrHandler();

	/**
	 * Get the singleton instance.
	 * @return
	 */
	public static BzrHandler getInstance() {
		return INSTANCE;
	}


	/*--------------------------------------------------------------*/
	/*	CODING:	Simple code and repo checks							*/
	/*--------------------------------------------------------------*/
	/**
	 * Return the name of the SCM.
	 * @see to.etc.prjbuilder.scm.IScmHandler#getScmName()
	 */
	public String getScmName() {
		return "bzr";
	}

	/**
	 * Translate an input thing to a Scm representation of a branch. This only links a branch to it's
	 * SCM; it does not imply any checking.
	 * @param location
	 * @return
	 */
	public ScmBranch getBranch(String location) {
		return new ScmBranch(this, location);
	}

	public ScmBranch getBranch(File location) {
		return new ScmBranch(this, location);
	}

	public boolean isLocalRepository(File path) {
		if(!path.exists())
			return false;
		File bzr = new File(path, ".bzr/repository");
		return bzr.exists() && bzr.isDirectory();
	}

	public boolean isBranch(ScmBranch branch) {
		File bzr = new File(branch.getPathAsFile(), ".bzr/branch/format");
		return bzr.exists() && bzr.isFile();
	}

	public boolean isBranchWithSources(ScmBranch branch) {
		if(!isBranch(branch))
			return false;

		//-- Must contain files and dirs, other than just .bzr
		File[] far = branch.getPathAsFile().listFiles();
		int count = 0;
		for(File f : far) {
			if(f.getName().equals(".bzr") || f.getName().equals(".bzrmeta"))
				continue;
			if(count++ > 1)
				return true;
		}
		return false;
	}


	/*--------------------------------------------------------------*/
	/*	CODING:		*/
	/*--------------------------------------------------------------*/
	/**
	 * Create a new branch in the specified targetDir. If the dir already exists it
	 * gets destroyed before the branch is taken. If the branch fails this reports
	 * the problem and returns false.
	 *
	 * @param repos
	 * @param targetdir
	 * @return
	 * @throws Exception
	 */
	public boolean		branch(Reporter r, String repos, File targetdir) throws Exception {
		long ts = System.nanoTime();
		//-- 1. Make sure the target exists but is empty.
		if(targetdir.exists()) {
			if(targetdir.isFile()) {
				if(! targetdir.delete()) {
					r.error("Cannot delete file at branch directory location "+targetdir);
					return false;
				}
			} else {
				FileTool.deleteDir(targetdir);
				if(targetdir.exists()) {
					r.error("Cannot delete branch directory "+targetdir);
					return false;
				}
			}
		}
		File	parent = targetdir.getParentFile();
		parent.mkdirs();
		if(! parent.exists() || ! parent.isDirectory()) {
			r.error("Branch parent directory "+parent+" does not exist and cannot be created");
			return false;
		}

		BzrCommand	bc = new BzrCommand(parent);
		bc.bzr("branch", repos, targetdir.toString());

		if(0 != bc.execute()) {
			r.error("Cannot branch "+repos+" into "+targetdir+": "+bc.getResponse());
			return false;
		}
		ts = System.nanoTime() - ts;
		r.detail("branched "+repos+" in "+StringTool.strNanoTime(ts));
		return true;
	}

	/**
	 * Pull all changes from the specified branch into the local one. If the pull
	 * fails this returns false; in that case the local branch is usually invalid.
	 * @param r
	 * @param branch
	 * @param repos
	 * @return
	 * @throws Exception
	 */
	public boolean	pull(Reporter r, File branch, String repos) throws Exception {
		long ts = System.nanoTime();
		BzrCommand	bc = new BzrCommand(branch);
		bc.bzr("pull", repos);
		if(0 != bc.execute()) {
			r.logRecord(LogLineType.MGE, "Cannot pull "+repos+" into "+branch+": "+bc.getResponse());
			return false;
		}
		ts = System.nanoTime() - ts;
		r.logRecord(LogLineType.MRG, "pulled "+repos+" in "+StringTool.strNanoTime(ts));
		return true;
	}

	public boolean	push(Reporter r, File branch, String repos) throws Exception {
		long ts = System.nanoTime();
		BzrCommand	bc = new BzrCommand(branch);
		bc.bzr("push", repos);
		if(0 != bc.execute()) {
			System.out.println("bzr push FAILED: "+bc.getResponse());
			r.logRecord(LogLineType.MGE, "Cannot push "+branch+" to "+repos+": "+bc.getResponse());
			return false;
		}
		ts = System.nanoTime() - ts;
		r.logRecord(LogLineType.MRG, "pushed "+branch+" in "+StringTool.strNanoTime(ts));
		return true;
	}

	public boolean	tag(Reporter r, File branch, String tagname) throws Exception {
		long ts = System.nanoTime();
		BzrCommand	bc = new BzrCommand(branch);
		bc.bzr("tag", "--force", tagname);
		if(0 != bc.execute()) {
			System.out.println("bzr tag FAILED: "+bc.getResponse());
			r.logRecord(LogLineType.MGE, "Cannot tag "+branch+": "+bc.getResponse());
			return false;
		}
		ts = System.nanoTime() - ts;
		r.logRecord(LogLineType.MRG, "Tagged "+branch+" with "+tagname+" in "+StringTool.strNanoTime(ts));
		return true;
	}

	/**
	 *
	 * @param r
	 * @param branch
	 * @param message
	 * @throws Exception
	 */
	public void commit(Reporter r, File branch, String message) throws Exception {
		long ts = System.nanoTime();
		BzrCommand	bc = new BzrCommand(branch);
		bc.bzr("commit", "--no-plugins", "-m", message);
		if(0 != bc.execute()) {
			System.out.println("bzr commit FAILED: "+bc.getResponse());
			r.logRecord(LogLineType.MGE, "Cannot commit "+branch+": "+bc.getResponse());
			throw ScmException.create("Cannot commit on " + branch, bc.getResponse());
		}
		ts = System.nanoTime() - ts;
		r.logRecord(LogLineType.MRG, "Commited "+branch+" in "+StringTool.strNanoTime(ts));
	}

	public void applyMerge(Reporter r, File branch, File mergefile) throws Exception {
		long ts = System.nanoTime();
		BzrCommand	bc = new BzrCommand(branch);
		bc.bzr("merge", "--force", mergefile.getAbsolutePath().toString());
		if(0 != bc.execute()) {
			System.out.println("Response:"+bc.getResponse());
			r.logRecord(LogLineType.MGE, "Merge into "+branch+" failed: "+bc.getResponse());
			throw ScmException.create("Cannot merge into " + branch, bc.getResponse());
		}
		System.out.println("Response:"+bc.getResponse());
		appendLog(r, bc.getResponse());
		ts = System.nanoTime() - ts;
		r.logRecord(LogLineType.MRG, "Merge completed in "+StringTool.strNanoTime(ts));
	}

	private void	appendLog(Reporter rep, String txt) throws IOException {
		LineNumberReader	r = new LineNumberReader(new StringReader(txt));
		String line;
		while(null != (line = r.readLine())) {
			rep.logRecord(LogLineType.MRG, line);
		}
	}

	/**
	 * Determine missing count.
	 * @param brancha
	 * @param branchb
	 * @return
	 */
	public ScmMissing missing(File brancha, File branchb, boolean withlogs) throws Exception {
		/*
		 * Internal: the command is [brancha] bzr missing [branchb].
		 * The result: missing_revisions is revisions of B that are missing in A.
		 * extra_revisions: revisions of a missing in B.
		 *
		 */
		long ts = System.nanoTime();
		BzrCommand	bc = new BzrCommand(brancha);
		bc.bzr("xmlmissing", "--show-ids", branchb.getAbsoluteFile().getPath());
		int rc = bc.execute();
		String xml = bc.getResponse();
		if(rc != 0) {
			System.out.println("Response: code=" + rc + ", text=" + xml);
			throw ScmException.create("Cannot get 'xmlmissing' for " + brancha+" "+branchb, xml);
		}
		//		System.out.println("Response:"+bc.getResponse());

		//-- Workaround for xmlmissing:bug#488135: missing close tag present twice.
		int pos = xml.indexOf("</missing></missing>");
		if(pos != -1)
			xml = xml.substring(0, pos) + xml.substring(pos + 10);

		//-- This is an XML response; decode it;
		Node root = DomTools.getDocumentRoot(xml, "xmlmissing-response", false);
		if(!root.getNodeName().equalsIgnoreCase("missing"))
			throw new IllegalStateException("Missing 'missing' node");

		//-- Handle the "extra_revisions" node: missingInB
		int missingInB = 0;
		List<ScmLogEntry> missingInBList = null;
		Node extra = DomTools.nodeFind(root, "extra_revisions");
		if(extra != null) {
			missingInB = DomTools.intAttr(extra, "size");
			if(missingInB != 0)
				missingInBList = decodeLogInLogs(extra);
		}
		int missingInA = 0;
		List<ScmLogEntry> missingInAList = null;
		Node ms = DomTools.nodeFind(root, "missing_revisions");
		if(ms != null) {
			missingInA = DomTools.intAttr(ms, "size");
			if(missingInA != 0)
				missingInAList = decodeLogInLogs(ms);
		}

		ts = System.nanoTime() - ts;
		System.out.println("bzr xmlmissing completed in " + StringTool.strNanoTime(ts));
		return new ScmMissing(missingInB, missingInA, missingInBList, missingInAList);
	}

	private List<ScmLogEntry> decodeLogInLogs(Node extra) throws Exception {
		Node logs = DomTools.nodeFind(extra, "logs");
		if(logs == null)
			return Collections.EMPTY_LIST;
		return decodeLogsNode(logs);
	}

	private List<ScmLogEntry> decodeLogsNode(Node logs) throws Exception {
		NodeList ch = logs.getChildNodes();
		List<ScmLogEntry> res = new ArrayList<ScmLogEntry>();
		for(int i = 0; i < ch.getLength(); i++) {
			Node xn = ch.item(i);
			if(xn.getNodeName().equals("log")) {
				String revno = DomTools.stringNode(xn, "revno");
				String revid = DomTools.stringNode(xn, "revisionid");
				String com = DomTools.stringNode(xn, "committer");
				String br = DomTools.stringNode(xn, "branch-nick");
				String ts = DomTools.stringNode(xn, "timestamp");
				String msg = DomTools.stringNode(xn, "message");
				res.add(new ScmLogEntry(revid, revno, com, br, ts, msg));
			}
		}
		return res;
	}

	@Deprecated
	public List<ScmLogEntry> getLog(File branch, String revspec) throws Exception {
		BzrCommand bc = new BzrCommand(branch);
		bc.bzr("xmllog", "--show-ids", "-r", revspec);
		int rc = bc.execute();
		if(rc != 0) {
			System.out.println("Response: code=" + rc + ", text=" + bc.getResponse());
			throw ScmException.create("Cannot get 'xmllog' for " + branch, bc.getResponse());
		}
		//		System.out.println("Response:"+bc.getResponse());

		//-- This is an XML response; decode it;
		Node root = DomTools.getDocumentRoot(bc.getResponse(), "xmllog-response", false);
		if(!root.getNodeName().equalsIgnoreCase("logs"))
			throw new IllegalStateException("Missing 'logs' node");
		return decodeLogsNode(root);
	}

	public List<ScmLogEntry> getLog(ScmBranch branch, String revspec) throws Exception {
		BzrCommand bc = new BzrCommand();
		bc.bzr("xmllog", "--show-ids", "-r", revspec, branch.getPath());
		int rc = bc.execute();
		if(rc != 0) {
			System.out.println("Response: code=" + rc + ", text=" + bc.getResponse());
			throw ScmException.create("Cannot get 'xmllog' for " + branch, bc.getResponse());
		}
		//		System.out.println("Response:"+bc.getResponse());

		//-- This is an XML response; decode it;
		Node root = DomTools.getDocumentRoot(bc.getResponse(), "xmllog-response", false);
		if(!root.getNodeName().equalsIgnoreCase("logs"))
			throw new IllegalStateException("Missing 'logs' node");
		return decodeLogsNode(root);
	}

	/**
	 * Returns the log entry of the last log entry.
	 * @param branch
	 * @return
	 * @throws Exception
	 */
	@Deprecated
	public ScmLogEntry getLogLast(File branch) throws Exception {
		List<ScmLogEntry> l = getLog(branch, "-1..");
		if(l.size() == 0)
			return null;
		return l.get(0);
	}

	public ScmLogEntry getLogLast(ScmBranch branch) throws Exception {
		List<ScmLogEntry> l = getLog(branch, "-1..");
		if(l.size() == 0)
			return null;
		return l.get(0);
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Merge / diff handler (code reviewer).				*/
	/*--------------------------------------------------------------*/
	/**
	 * This determines the diff between two branches and creates a merge details
	 * thingy which can be used to create a review request, containing the changed
	 * lines et al.
	 * It issues a merge, then uses the produced diff to get the delta.
	 * @param into
	 * @param source
	 * @param withdiff
	 * @return
	 * @throws Exception
	 */
	public MergeDetails mergePreview(File into, File source, boolean withdiff) throws Exception {
		BzrCommand	bc = new BzrCommand(into);
		bc.bzr("merge", "--no-plugins", "--preview", source.getAbsolutePath());
		if(0 != bc.execute())
			throw new ScmException("merge --preview failed with rc=" + bc.getExitCode());

		System.out.println(bc.getResponse());
		System.out.println("====== Decode diff ====");

		//-- Start scanning the result, and split into per-file delta's.
		m_changeList = new ArrayList<MrgFileChange>();
		m_deltaLines = 0;
		m_linesAdded = 0;
		m_linesDeleted = 0;
		m_currentChange = null;
		m_currentLineDelta = null;
		m_oldLenLeft = 0;
		m_newLenLeft = 0;

		LineNumberReader lr = new LineNumberReader(new StringReader(bc.getResponse()));
		String line;
		while(null != (line = lr.readLine())) {
			if(line.length() == 0)
				continue;

			decodeLine(line);
		}
		clearFile();
		MergeDetails md = new MergeDetails(m_changeList);
		m_changeList = null;
		return md;
	}

	private List<MrgFileChange> m_changeList;

	private TextScanner m_ts = new TextScanner();

	/** If we were decoding the delta chunks this is > 0; while we're decoding a file header it is 0. */
	private int m_deltaLines;

	/** The current file change we're parsing. */
	private MrgFileChange m_currentChange;

	/** The current line delta we're adding changes lines to. */
	private MrgLineDelta m_currentLineDelta;

	/** The current calculated old- and new line numbers for the line passed. */
	private int m_oldLineNr, m_newLineNr;

	/** For diff checking, these count back after a hunk is found and must be zero @ the end of the hunk or the diff is invalid. */
	private int m_oldLenLeft, m_newLenLeft;

	private int m_linesDeleted, m_linesAdded;

	/**
	 * Called when the next header is found. If we were scanning a delta it means that the delta
	 * for that file is complete, and that the next file's delta is being defined (headers are
	 * found for the next file).
	 */
	private void clearFile() {
		if(m_deltaLines > 0) {
			if(m_currentChange != null) {
				m_changeList.add(m_currentChange);
				flushDiffSection();
				m_currentChange.setLineCounts(m_linesDeleted, m_linesAdded);
				if(m_currentChange.getType() == MrgChangeType.RENAMED && m_currentChange.getDeltaList().size() != 0)
					m_currentChange.setType(MrgChangeType.RENMOD);
			}
			m_currentChange = null;
		}
		m_deltaLines = 0;
		m_linesAdded = 0;
		m_linesDeleted = 0;
	}

	private void decodeLine(String line) throws Exception {
		if(line.startsWith("=== ")) { // A processor indicator: what is this delta about?
			m_deltaLines++;
			clearFile();

			//-- Determine the type of change
			line = line.substring(4); // Remove ===
			int ix = 0;
			MrgChangeType ct = null;
			boolean isdir = false;

			ReaderTokenizerBase rtb = new ReaderTokenizerBase("", new StringReader(line));
			int t = rtb.nextToken();
			if(t != ReaderScannerBase.T_IDENT)
				return;
			String a = rtb.getCopied();
			t = rtb.nextToken();
			if(t != ReaderScannerBase.T_IDENT)
				return;
			String b = rtb.getCopied();

			t = rtb.nextToken();
			if(t != ReaderScannerBase.T_STRING)
				return;
			String target = rtb.getCopied();
			String original = null;

			if(a.equalsIgnoreCase("added")) {
				ct = MrgChangeType.ADDED;
			} else if(a.equalsIgnoreCase("modified")) {
				ct = MrgChangeType.MODIFIED;
			} else if(a.equalsIgnoreCase("renamed")) {
				ct = MrgChangeType.RENAMED;

				//-- There must be a => then a second file name.
				t = rtb.nextToken();
				if(t != '=')
					throw new IllegalStateException("Expecting => in rename, got " + t + "/" + (char) t + " @ line: " + line);
				t = rtb.nextToken();
				if(t != '>')
					throw new IllegalStateException("Expecting => in rename, got " + t + "/" + (char) t + " @ line: " + line);

				t = rtb.nextToken();
				if(t != ReaderScannerBase.T_STRING)
					throw new IllegalStateException("Expecting string after => in rename, got " + t + "/" + (char) t + " @ line: " + line);
				original = target;
				target = rtb.getCopied();
			} else if(a.equalsIgnoreCase("removed")) {
				ct = MrgChangeType.REMOVED;
			} else
				throw new IllegalStateException("Unexpected line: " + line);

			if(b.equalsIgnoreCase("file"))
				isdir = false;
			else if(b.equalsIgnoreCase("dir") || b.equalsIgnoreCase("directory"))
				isdir = true;
			else
				throw new IllegalStateException("Expecting 'file' or 'dir' as 2nd word @ line: " + line);

			//			System.out.println("ALTER: " + ct + " " + (isdir ? " (dir) " : "(file)") + " target=" + target + ", orig=" + original);
			m_currentChange = new MrgFileChange(ct, isdir, target, original);


		} else if(line.startsWith("+++ ")) { // The target (resulting) file
			clearFile();
		} else if(line.startsWith("--- ")) { // The original file
			clearFile();
		} else if(line.startsWith("@@")) {
			m_deltaLines++;
			if(!decodeDiffSection(line)) // Handle the @@ -lnr,len +lnr,len @@
				throw new IllegalStateException("Cannot decode DIFF section: " + line);
		} else if(line.startsWith("Binary file")) {
			//-- IGNORE FOR NOW

		} else {
			m_deltaLines++;
			decodeUnidiffLine(line);
		}
	}

	/**
	 * Flush a diff section by adding it to it's parent, if it contains data at all.
	 */
	private void flushDiffSection() {
		if(m_currentLineDelta == null)
			return;
		if(m_currentChange.getDeltaList() == null)
			m_currentChange.setDeltaList(new ArrayList<MrgLineDelta>());
		m_currentChange.getDeltaList().add(m_currentLineDelta);
		m_currentLineDelta = null;
	}

	/**
	 * Decode the @@ -lnr,len +lnr,len @@ unidiff header and prepare for a new delta hunk.
	 * @param line
	 */
	private boolean decodeDiffSection(String line) {
		flushDiffSection();

		//-- We can only parse a new section if the OLD one's line count-left-counts are correct (both zero),
		if(m_oldLenLeft != 0)
			throw new IllegalStateException("The 'old' line count is wrong in the diff: " + m_oldLenLeft + " in file " + m_currentChange.getName() + " old-line " + m_oldLineNr);
		if(m_newLenLeft != 0)
			throw new IllegalStateException("The 'new' line count is wrong in the diff: " + m_newLenLeft + " in file " + m_currentChange.getName() + " new-line " + m_newLineNr);

		m_ts.setString(line);
		m_ts.skipWS();
		m_ts.skip('@');
		m_ts.skip('@');
		m_ts.skipWS();

		//-- Old line section
		if(!m_ts.skip('-'))
			return false;
		if(!m_ts.scanInt())
			return false;
		m_oldLineNr = (int) m_ts.getLastInt();
		if(!m_ts.skip(','))
			return false;
		if(!m_ts.scanInt())
			return false;
		m_oldLenLeft = (int) m_ts.getLastInt();

		//-- New line section
		m_ts.skipWS();
		if(!m_ts.skip('+'))
			return false;
		if(!m_ts.scanInt())
			return false;
		m_newLineNr = (int) m_ts.getLastInt();
		if(!m_ts.skip(','))
			return false;
		if(!m_ts.scanInt())
			return false;
		m_newLenLeft = (int) m_ts.getLastInt();
		m_ts.skipWS();
		if(!m_ts.skip('@'))
			return false;
		return true;
	}

	/**
	 * Stateful handling of a line that is assumed to be part of the change section of the unidiff..
	 * @param line
	 */
	private void decodeUnidiffLine(String line) {
		char c = line.length() == 0 ? c = ' ' : line.charAt(0);

		if(Character.isWhitespace(c)) {
			//-- Context line. Mostly ignore, but do handle line count data
			m_oldLenLeft--;
			m_newLenLeft--;
			m_oldLineNr++;
			m_newLineNr++;
			flushDiffSection(); // No longer belongs to collected lineset.
			return;
		}
		if(c == '\\')
			return;

		int ol = m_oldLineNr;
		int nl = m_newLineNr;

		//-- Add or delete. Make sure a diffchunk is current,
		if(m_currentLineDelta == null)
			m_currentLineDelta = new MrgLineDelta(m_oldLineNr, m_newLineNr);

		if(c == '+') {
			m_newLineNr++;
			m_newLenLeft--;
			m_linesAdded++;
			m_currentLineDelta.getAdded().add(line.substring(1));
		} else if(c == '-') {
			m_oldLineNr++;
			m_oldLenLeft--;
			m_linesDeleted++;
			m_currentLineDelta.getDeleted().add(line.substring(1));
		} else
			throw new IllegalStateException("Cannot decypher diff content line: it starts with '"+c+"' ("+(int)c+"): "+line);
	}

	/**
	 *
	 * @param copy
	 * @param branch
	 * @param revision
	 * @param relfile
	 */
	public void checkoutFile(File copy, File branch, String revision, String relfile) throws Exception {
		OutputStream os = new FileOutputStream(copy);
		BzrCommand bc = new BzrCommand(branch);
		boolean ok = false;
		try {
			bc.bzr("cat", "--no-plugins", "--revision", "revid:" + revision, relfile);
			if(0 != bc.execute(os))
				throw new ScmException(bc.getCommand() + " failed with rc=" + bc.getExitCode());
			os.close();
			ok = true;
		} finally {
			try {
				os.close();
			} catch(Exception x) {}
			try {
				if(!ok)
					copy.delete();
			} catch(Exception x) {}
		}
	}


	/**
	 * Returns a shallow list of changes in the specified workspace. It contains the changed files and
	 * if needed a set of conflicts; it does not do a full diff.
	 * @param branch
	 * @return
	 * @throws Exception
	 */
	public MergeDetails status(ScmBranch branch) throws Exception {
		if(!branch.isBranchWithSources())
			throw new ScmException(branch + ": no sources/not a branch");

		BzrCommand bc = new BzrCommand(branch.getPathAsFile());
		bc.bzr("xmlstatus");
		int rc = bc.execute();
		if(rc != 0) {
			System.out.println("Response: code=" + rc + ", text=" + bc.getResponse());
			throw ScmException.create("Cannot get 'xmlstatus' for " + branch, bc.getResponse());
		}
		//		System.out.println("Response:"+bc.getResponse());

		//-- This is an XML response; decode it;
		Node nd = getRoot(bc.getResponse(), "status", "xmlstatus");

		List<MrgFileChange> changelist = new ArrayList<MrgFileChange>();
		Node n = DomTools.nodeFind(nd, "modified");
		if(n != null)
			handleFileList(changelist, MrgChangeType.MODIFIED, n);

		n = DomTools.nodeFind(nd, "added");
		if(n != null)
			handleFileList(changelist, MrgChangeType.ADDED, n);

		n = DomTools.nodeFind(nd, "removed");
		if(n != null)
			handleFileList(changelist, MrgChangeType.REMOVED, n);

		n = DomTools.nodeFind(nd, "renamed");
		if(n != null)
			handleFileList(changelist, MrgChangeType.RENAMED, n);

		n = DomTools.nodeFind(nd, "unknown");
		if(n != null)
			handleFileList(changelist, MrgChangeType.UNKNOWN, n);
		return new MergeDetails(changelist);
	}

	/**
	 * Decode a bzr response as an XML message and get it's root node. If the document
	 * is an error response this throws the error as an ScmException.
	 * @param response
	 * @param string
	 * @return
	 * @throws Exception
	 */
	private Node getRoot(String response, String rootname, String command) throws Exception {
		Document doc = DomTools.getDocument(new StringReader(response), "bzr-response", false);
		Node nd = DomTools.getRootElement(doc);
		if(nd.getNodeName().equalsIgnoreCase("error")) {
			String code = DomTools.stringNode(nd, "class", "Unknown");
			String message = DomTools.stringNode(nd, "message", "Unknown error message");

			throw new ScmException("Error in bzr " + command + ": " + code + " " + message);
		}

		if(!nd.getNodeName().equalsIgnoreCase(rootname))
			throw new IllegalStateException("Expected root node " + rootname + " but got " + nd.getNodeName() + " in " + command);
		return nd;
	}

	static private MrgFileChange findByName(List<MrgFileChange> changelist, String name) {
		for(MrgFileChange fc : changelist) {
			if(fc.getName().equals(name))
				return fc;
		}
		return null;
	}

	private void handleFileList(List<MrgFileChange> changelist, MrgChangeType t, Node inn) {
		NodeList nl = inn.getChildNodes();
		for(int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if(n.getNodeName().equalsIgnoreCase("file")) {
				String name = DomTools.textFrom(n); // Relative path name
				String old = DomTools.strAttr(n, "oldpath", null);

				//-- Check if we already know this file.
				MrgFileChange ex = findByName(changelist, name);
				if(ex != null) {
					if(t == MrgChangeType.MODIFIED && ex.getType() == MrgChangeType.RENAMED) {
						ex.setType(MrgChangeType.RENMOD);
						ex.setOldName(old);
					} else if(t == MrgChangeType.RENAMED && ex.getType() == MrgChangeType.MODIFIED)
						ex.setType(MrgChangeType.RENMOD);
				} else {
					ex = new MrgFileChange(t, false, name, old);
					changelist.add(ex);
				}
			} else if(n.getNodeName().equalsIgnoreCase("directory")) {
				String name = DomTools.textFrom(n); // Relative path name
				String old = DomTools.strAttr(n, "oldpath", null);
				MrgFileChange ex = new MrgFileChange(t, true, name, old);
				changelist.add(ex);
			}
		}
	}
}
