package to.etc.jenkins;

import java.net.*;

/**
 * Helper class to load JDBC drivers from a given path.
 */
final class NoLoader extends URLClassLoader {
	NoLoader(final URL[] u) {
		super(u);
	}

	@Override
	protected synchronized Class< ? > loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
		// First, check if the class has already been loaded
		Class< ? > c = findLoadedClass(name);
		//            System.out.println(name+": findLoadedClass="+c);
		if(c == null) {
			//-- Try to load by THIS loader 1st,
			try {
				c = findClass(name);
				//                    System.out.println(name+": findClass="+c);
			} catch(ClassNotFoundException x) {
				//                    System.out.println(name+": findClass exception");
			}
			if(c == null) {
				c = super.loadClass(name, resolve); // Try parent
				//                    System.out.println(name+": super.loadClass="+c);
			}
		}

		if(resolve)
			resolveClass(c);
		return c;
	}
}