package to.etc.prjbuilder.maker;

import java.io.*;
import java.lang.reflect.*;

import to.etc.util.*;

public class EcjJarCompiler implements IJavaCompiler {
	final private Class< ? > m_ecjClass;

	public EcjJarCompiler(Class< ? > ecjObject) {
		m_ecjClass = ecjObject;
	}

	public boolean compile(String[] args, PrintWriter stdout, PrintWriter stderr) throws Exception {
		Method m = ClassUtil.findMethod(m_ecjClass, "compile", new Object[]{args, stdout, stderr, null});
		if(null == m)
			throw new IllegalStateException("Cannot locate compile method in " + m_ecjClass);
		try {
			Boolean b = (Boolean) m.invoke(null, args, stdout, stderr, null);
			return b.booleanValue();
		} catch(Exception x) {
			throw WrappedException.unwrap(x);
		}
	}
}
