package to.etc.prjbuilder.util;

import java.io.*;
import java.util.*;

import to.etc.util.*;

public class SourceInventory implements Serializable {
	static private final class InvEntry implements Serializable {
		public String	relPath;
		public long		lastModified;
		public int		size;
		public byte[]	md5hash;

		public InvEntry(String relPath, int size, long lastModified, byte[] md5hash) {
			this.relPath = relPath;
			this.size = size;
			this.lastModified = lastModified;
			this.md5hash = md5hash;
		}
	}

	/** The actual inventory consisting of files indexed by their relative path. */
	private Map<String, InvEntry>	m_map;

	/*--------------------------------------------------------------*/
	/*	CODING:	Creating an initial inventory.						*/
	/*--------------------------------------------------------------*/
	/**
	 * Create an (initial) inventory of source files for a build. This handles
	 * ALL files in a project dir.
	 * @param src
	 * @return
	 */
	static public SourceInventory createInventory(File root, Set<String> paths) throws Exception {
		SourceInventory	si = new SourceInventory();
		si.initialize(root, paths);
		return si;
	}

	private void initialize(File root, Set<String> paths) throws Exception {
		long ts = System.nanoTime();
		m_map = new HashMap<String, InvEntry>(255);
		StringBuilder	sb	= new StringBuilder(128);
		for(String sub : paths) {
			sub = sub.replace('\\', '/'); // Normalize always
			if(sub.length() == 0)
				throw new IllegalStateException("Illegal empty subpath for " + root);
			File target = new File(root, sub);
			sb.setLength(0);
			sb.append(sub); // Start off the relative path
			scanFileOrDir(target, sb);
		}
		ts	= System.nanoTime() - ts;
		System.out.println(".. initial inventory of " + paths.size() + " subpaths of " + root + " took " + StringTool.strNanoTime(ts));
	}

	private void scanFileOrDir(File f, StringBuilder sb) throws Exception {
		if(f.isDirectory()) {
			traverseInitial(f, sb); // Just enter; do not store a signature for directories.
		} else if(f.exists()) {
			//-- File: add signature
			String rn = sb.toString();
			byte[] hash = FileTool.hashFile(f);
			InvEntry ie = new InvEntry(rn, (int) f.length(), f.lastModified(), hash);
			m_map.put(rn, ie);
		}
	}

	private void	traverseInitial(File src, StringBuilder sb) throws Exception {
		int	len = sb.length();
		File[]	far = src.listFiles();
		for(File f : far) {
			//-- Construct the relative path into sb
			sb.setLength(len);
			if(len > 0)
				sb.append('/');
			sb.append(f.getName());

			scanFileOrDir(f, sb);
		}
	}

	/*--------------------------------------------------------------*/
	/*	CODING:	Check/update the inventory							*/
	/*--------------------------------------------------------------*/
	/**
	 * Check if the path specified represents the same inventory. When called this returns
	 * a list of changed files but it also updates the inventory so it can be stored again.
	 *
	 * @param src
	 * @return
	 * @throws Exception
	 */
	public List<File> checkForChanges(File root, Set<String> paths) throws Exception {
		StringBuilder	sb	= new StringBuilder(128);
		Set<String>		leftset = new HashSet<String>(m_map.keySet());		// Set of all filenames in the original
		List<File>		changelist = new ArrayList<File>();

		//-- Check all subpaths
		for(String sub : paths) {
			sub = sub.replace('\\', '/'); // Normalize always
			if(sub.length() == 0)
				throw new IllegalStateException("Illegal empty subpath for " + root);
			File target = new File(root, sub);
			sb.setLength(0);
			sb.append(sub); // Start off the relative path
			traverseCheckFileOrDir(sb, leftset, changelist, target);
		}

		//-- All that's left are deleted files;
		if(leftset.size() > 0) {					// Files in last check that are not present now?
			for(String s : leftset) {
				InvEntry ie = m_map.remove(s);		// Discard from set,
				if(ie == null)
					throw new IllegalStateException("?? internal error removing old entry");
				changelist.add(new File(root, ie.relPath));
			}
		}
		return changelist;
	}

	private void traverseCheckFileOrDir(StringBuilder sb, Set<String> leftset, List<File> changelist, File f) throws Exception {
		if(f.isDirectory()) {
			traverseCheck(sb, leftset, changelist, f);
		} else if(f.exists()) {
			//-- Check signature...
			String rel = sb.toString(); // The path
			InvEntry ie = m_map.get(rel); // Do we have an entry here?
			if(ie == null) {
				//-- This file is new. Add to changed list, then update map
				String rn = sb.toString();
				byte[] hash = FileTool.hashFile(f);
				ie = new InvEntry(rn, (int) f.length(), f.lastModified(), hash);
				m_map.put(rn, ie);
				changelist.add(f);
			} else {
				leftset.remove(rel); // Seen this one

				//-- Existed before.. Compare;
				int sz = (int) f.length();
				long lm = f.lastModified();
				if(sz != ie.size || lm != ie.lastModified) {
					//-- Timestamp/size incorrect -> probable change... Check hash
					ie.lastModified = lm;
					ie.size = sz;
					byte[] hash = FileTool.hashFile(f);
					if(!cmpHash(hash, ie.md5hash)) {
						//-- File content has changed....
						ie.md5hash = hash;
						changelist.add(f);
					}
				}
			}
		}
	}

	private void	traverseCheck(StringBuilder sb, Set<String> leftset, List<File> changelist, File src) throws Exception {
		int	len = sb.length();
		File[]	far = src.listFiles();
		for(File f : far) {
			//-- Construct the relative path into sb
			sb.setLength(len);
			if(len > 0)
				sb.append('/');
			sb.append(f.getName());
			traverseCheckFileOrDir(sb, leftset, changelist, f);
		}
	}

	static private boolean cmpHash(byte[] a, byte[] b) {
		if(a.length != b.length)
			return false;
		for(int i = a.length; --i >= 0;) {
			if(a[i] != b[i])
				return false;
		}
		return true;
	}

	/**
	 * Tries to load the source inventory; returns null if it could not be found/loaded.
	 * @param src
	 * @return
	 */
	static public SourceInventory	load(File src) {
		if(! src.exists())
			return null;
		ObjectInputStream	ois = null;
		try {
			ois = new ObjectInputStream(new FileInputStream(src));
			return (SourceInventory) ois.readObject();
		} catch(Exception x) {
			return null;
		} finally {
			try { if(ois != null) ois.close(); } catch(Exception x) {}
		}
	}

	/**
	 * Saves the entire inventory.
	 * @param src
	 */
	public void	save(File src) {
		boolean ok = false;
		ObjectOutputStream	oos = null;
		try {
			oos	= new ObjectOutputStream(new FileOutputStream(src));
			oos.writeObject(this);
			oos.close();
			oos	= null;
			ok	= true;
		} catch(Exception x) {
			x.printStackTrace();
		} finally {
			try { if(oos != null) oos.close(); } catch(Exception x) {}
			try { if(! ok) src.delete(); } catch(Exception x) {}
		}
	}
}
