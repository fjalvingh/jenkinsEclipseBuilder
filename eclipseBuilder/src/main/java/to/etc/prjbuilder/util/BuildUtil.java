package to.etc.prjbuilder.util;


public class BuildUtil {
	static public boolean isHiddenDirectory(String name) {
		return name.equalsIgnoreCase(".svn") || name.equalsIgnoreCase(".bzr") || name.equalsIgnoreCase("CVS");
	}

	/**
	 * This checks and if necessary normalizes the version # passed. If not null it MUST
	 * contain a list of numbers separated by '.' without leading zeroes and without any
	 * other characters. It will allow the name "trunk" to indicate version 999.999.
	 * @param in
	 * @return
	 */
	static public String normalizeVersion(String in) {
		if(in == null)
			return in;
		in = in.trim();
		if(in.length() == 0)
			throw new RuntimeException("A version number cannot be the empty string");
		if(in.equalsIgnoreCase("trunk"))
			return in;
		StringBuilder sb = new StringBuilder(in.length());
		String[] ar = in.split("\\.");
		for(String v : ar) {
			try {
				int val = Integer.parseInt(v);
				if(sb.length() != 0)
					sb.append('.');
				sb.append(val);
			} catch(Exception x) {
				throw new RuntimeException("The version number '" + in + "' is invalid");
			}
		}
		return sb.toString();
	}

	/**
	 * Returns a version. This allows the word "trunk" and returns 999.999 as a version.
	 * @param in
	 * @return
	 */
	static public int[] decodeVersion(String in) {
		if(in.trim().equalsIgnoreCase("trunk"))
			return new int[]{999, 999};

		String[] ar = in.split("\\.");
		if(ar == null)
			ar = new String[]{in};
		int[] res = new int[ar.length];
		int ix = 0;
		for(String v : ar) {
			try {
				res[ix++] = Integer.parseInt(v);
			} catch(Exception x) {
				throw new RuntimeException("Invalid version number: " + in);
			}
		}
		return res;
	}

	static public int compareVersions(String a, String b) {
		int[] aar = decodeVersion(a);
		int[] bar = decodeVersion(b);
		return compareVersion(aar, bar);
	}

	static public int compareVersion(int[] a, int[] b) {
		if(a == null && b == null)
			return 0;
		else if(a != null && b == null)
			return -1;
		else if(a == null && b != null)
			return 1;
		if(a.length == 0 && b.length == 0)
			return 0;

		int clen = a.length; // Find common length
		if(clen > b.length)
			clen = b.length;

		//-- Common length comparison
		for(int i = 0; i < clen; i++) {
			int res = a[i] - b[i];
			if(res != 0)
				return res < 0 ? -1 : 1;
		}

		//-- Common lengths are same.
		if(a.length == b.length)
			return 0;

		//-- Treat 3.0 and 3.0.0 the same (equal versions)
		if(a.length > b.length) {
			if(isRestZero(a, clen))
				return 0;
			return 1; // a is bigger version  (3.0.0.0.1 vs 3.0)
		} else {
			if(isRestZero(b, clen))
				return 0;
			return -1; // a is bigger version  (3.0.0.0.1 vs 3.0)
		}
	}

	static private boolean isRestZero(int[] a, int ix) {
		while(ix < a.length) {
			if(a[ix++] != 0)
				return false;
		}
		return true;
	}

}
