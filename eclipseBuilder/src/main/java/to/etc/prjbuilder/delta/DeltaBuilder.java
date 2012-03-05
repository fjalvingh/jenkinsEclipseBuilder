package to.etc.prjbuilder.delta;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import to.etc.util.*;

/**
 * <p>Builds the delta from two repository builds. For each target (that is present
 * in BOTH repositories) it creates a full deltaset where the object of the delta
 * is to create TARGET from SOURCE by applying DELTA.</p>
 * <p>The result is used to create the PATCH package as a set of fixes, per target.</p>
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jun 4, 2008
 */
public class DeltaBuilder {
	public static enum DeltaAction {
		ADD,
		DEL,
		MOD
	}

	public final static class Change {
		private String		m_type;
		private DeltaAction	m_action;
		private String		m_relpath;
		private File		m_old;
		private File		m_new;

		public Change(String type, DeltaAction action, String relpath, File old, File nw) {
			m_type = type;
			m_action = action;
			m_relpath = relpath;
			m_old = old;
			m_new = nw;
		}

		public DeltaAction getAction() {
			return m_action;
		}
		public String getRelpath() {
			return m_relpath;
		}
		public File getOld() {
			return m_old;
		}
		public File getNew() {
			return m_new;
		}
		public String getType() {
			return m_type;
		}
	}

	private List<Change>		m_list = new ArrayList<Change>();

	/**
	 * Walks the directory tree and adds delta records for all files/directories that
	 * changed.
	 * @param olddir
	 * @param newdir
	 * @throws Exception
	 */
	public void		delta(String type, File olddir, File newdir) throws Exception {
		StringBuilder	sb = new StringBuilder(256);
		delta(sb, olddir, newdir, type);
	}

	/**
	 * Compare both directories. They are supposed to exist.
	 * @param sb
	 * @param olddir
	 * @param newdir
	 */
	private void	delta(StringBuilder sb, File olddir, File newdir, String ty) throws Exception {
		File[]	oar = olddir.listFiles();
		if(oar == null)
			throw new IllegalStateException("??? Null array in "+olddir);
		File[]	nar	= newdir.listFiles();
		if(nar == null)
			throw new IllegalStateException("??? Null array in "+newdir);

		//-- Create a set of all OLD names.
		Map<String, File>		oldmap = new HashMap<String, File>();
		for(File of : oar)
			oldmap.put(of.getName(), of);

		//-- Walk all new files && dirs.
		int	len = sb.length();
		for(File newf : nar) {
			//-- Construct a relative path
			sb.setLength(len);
			if(len > 0)
				sb.append('/');
			sb.append(newf.getName());
			String	relpath = sb.toString();

			//-- Check old against new
			File	oldf = oldmap.get(newf.getName());			// Thing exists with same name?
			if(oldf == null) {
				//-- We have a NEW file or directory-> add all delta's pertaining to that;
				if(newf.isDirectory())
					handleNewDirectory(ty, newf, sb);
				else
					handleNewFile(ty, newf, relpath);
			} else {
				//-- Old and new both exist. Remove from old set always
				oldmap.remove(newf.getName());					// Drop from old set

				if(oldf.isDirectory() != newf.isDirectory())	// Changed nature? (dir-> file or file->dir?)
					handleChangeNature(ty, oldf, newf, sb);
				else if(newf.isDirectory()) {
					//-- Just handle the directory recursively.
					delta(sb, oldf, newf, ty);
				} else {
					//-- We have two files here.... Are they equal?
					if(! filesEqual(oldf, newf)) {
						m_list.add(new Change(ty, DeltaAction.MOD, relpath, oldf, newf));
					}
				}
			}
		}

		//-- Finally: all that's left in the 'oldmap' are thingies that have been deleted in NEW.
		for(File oldf : oldmap.values()) {
			//-- Construct a relative path
			sb.setLength(len);
			if(len > 0)
				sb.append('/');
			sb.append(oldf.getName());
			String	relpath = sb.toString();

			//-- Handle the delete,
			if(oldf.isDirectory()) {
				//-- Delete all other crud collected below here,
				handleDeleteDir(ty, oldf, sb);
			} else {
				m_list.add(new Change(ty, DeltaAction.DEL, relpath, oldf, null));
			}
		}
	}

	/**
	 * Called when a file changes into a directory and v.v.
	 * @param oldf
	 * @param newf
	 */
	private void	handleChangeNature(String ty, File oldf, File newf, StringBuilder sb) {
		//-- If the old thingy is a DIR then delete the dir, then add the file
		int	len = sb.length();
		if(oldf.isDirectory()) {
			handleDeleteDir(ty, oldf, sb);
			sb.setLength(len);
			handleNewFile(ty, newf, sb.toString());
		} else {
			//-- Old thingy is file, new is directory
			m_list.add(new Change(ty, DeltaAction.DEL, sb.toString(), oldf, null));
			handleNewDirectory(ty, newf, sb);
		}
	}

	private void	handleDeleteDir(String ty, File dir, StringBuilder sb) {
		int	len = sb.length();
		for(File f : dir.listFiles()) {
			//-- Construct a relative path
			sb.setLength(len);
			if(len > 0)
				sb.append('/');
			sb.append(f.getName());
			if(f.isFile())
				m_list.add(new Change(ty, DeltaAction.DEL, sb.toString(), f, null));
			else
				handleDeleteDir(ty, f, sb);
		}

		//-- Add a delete for the directory itself
		sb.setLength(len);
		m_list.add(new Change(ty, DeltaAction.DEL, sb.toString(), dir, null));
	}

	/**
	 * Check if these files are equal. We consider 'm equal if their size and date modified are
	 * equal (fast path).
	 *
	 * @param oldf
	 * @param newf
	 * @return
	 * @throws Exception
	 */
	private boolean	filesEqual(File oldf, File newf) throws Exception {
		if(oldf.length() != newf.length())					// Different lengths -> always different
			return checkJarEquality(oldf, newf);
		if(oldf.lastModified() != newf.lastModified()) {
			//-- Sizes are teh same, but date-modified changed... Compare file hashes
			byte[] oldh = FileTool.hashFile(oldf);
			byte[] newh = FileTool.hashFile(newf);
			if(Arrays.equals(oldh, newh))
				return true;
			return checkJarEquality(oldf, newf);			// Assume sheet, but check jars implicitly
		}

		//-- Size and last-modified are the same. Assume files are equal
		return true;
	}

	/**
	 *
	 * @param oldf
	 * @param newf
	 * @return
	 * @throws Exception
	 */
	private boolean	checkJarEquality(File oldf, File newf) throws Exception {
		if(! newf.getName().toLowerCase().endsWith(".jar"))	// If it's not a jar we're sure the files are not equal.
			return false;
//		long ts = System.nanoTime();
		//-- For JAR files we check the contents of the jar...
		Map<String, JarEntry> oldm = loadZip(oldf);
		Map<String, JarEntry> newm = loadZip(newf);
		if(oldm.size() != newm.size())
			return false;

		//-- #entries in jar are equal... Compare their data
		Set<String> oldset = new HashSet<String>(oldm.keySet()); // Old entries not found in new
		for(String nname : newm.keySet()) { // Walk all new names
			JarEntry olde = oldm.get(nname); // Does this exist in the old jar?
			if(olde == null)
				return false; // New jar has new file -> exit.
			oldset.remove(nname);

			JarEntry newe = newm.get(nname);
			if(newe.getSize() != olde.getSize()) // Sizes have changed
				return false;
			if(!Arrays.equals(newe.getHash(), olde.getHash()))
				return false;
		}

		//-- If there's stuff left in oldset we're not equal
		if(oldset.size() > 0)
			return false;

		return true;

	}

	/**
	 * Simply add a change for the file.
	 * @param f
	 * @param relpath
	 */
	private void	handleNewFile(String ty, File f, String relpath) {
		m_list.add(new Change(ty, DeltaAction.ADD, relpath, null, f));
	}

	/**
	 * Add a change for the directory, then add all that is below there too.
	 *
	 * @param dir
	 * @param sb
	 */
	private void	handleNewDirectory(String ty, File dir, StringBuilder sb) {
		m_list.add(new Change(ty, DeltaAction.ADD, sb.toString(), null, dir));		// Add dir,
		int len = sb.length();
		for(File f : dir.listFiles()) {
			sb.setLength(len);
			if(len > 0)
				sb.append('/');
			sb.append(f.getName());

			if(f.isDirectory()) {
				handleNewDirectory(ty, f, sb);
			} else {
				handleNewFile(ty, f, sb.toString());
			}
		}
	}

	private static class JarEntry {
		private String		m_name;
		private int			m_size;
		private long		m_lastModified;
		private byte[]		m_hash;
		public JarEntry(byte[] hash, long lastModified, String name, int size) {
			m_hash = hash;
			m_lastModified = lastModified;
			m_name = name;
			m_size = size;
		}
		public String getName() {
			return m_name;
		}
		public int getSize() {
			return m_size;
		}
		public long getLastModified() {
			return m_lastModified;
		}
		public boolean isDir() {
			return m_hash == null;
		}
		public byte[] getHash() {
			return m_hash;
		}
	}

	/**
	 * Unzips a thingy into a jar inventory.
	 * @param src
	 * @return
	 */
	private Map<String, JarEntry>	loadZip(File src) throws IOException{
//		long ts = System.nanoTime();
		ZipInputStream	zis	= null;
		Map<String, JarEntry>	map = new HashMap<String, JarEntry>();
		try {
			zis	= new ZipInputStream(new FileInputStream(src));

			ZipEntry	ze;
			while(null != (ze = zis.getNextEntry())) {
				byte[] hash = null;
				if(! ze.isDirectory()) {
					hash = FileTool.hashFile(zis);
				} else
					hash = new byte[0];
				JarEntry	je = new JarEntry(hash, ze.getTime(), ze.getName(), (int)ze.getSize());
				map.put(ze.getName(), je);
			}
//			ts = System.nanoTime() - ts;
//			System.out.println("jar inventory of "+src+" took "+StringTool.strNanoTime(ts));
			return map;
		} finally {
			try { if(zis != null) zis.close(); } catch(Exception x) {}
		}
	}

	public List<Change> getList() {
		return m_list;
	}


	public static void main(String[] args) {
		try {
			File	o = new File("/home/jal/buildfiles/vp-3.1/current-targets/VP31/viewpoint/image");
			File	n = new File("/home/jal/buildfiles/vp-3.1/new-targets/VP31/viewpoint/image");

			DeltaBuilder	db = new DeltaBuilder();
			db.delta("", o, n);
			for(Change c : db.getList())
				System.out.println("/\\: "+c.getAction()+" "+c.getRelpath());

		} catch(Exception x) {
			x.printStackTrace();
		}
	}
}
