package to.etc.prjbuilder.maker;

import java.io.*;

import org.eclipse.jdt.core.compiler.batch.*;

public class EcjCompiler implements IJavaCompiler {
	public boolean compile(String[] args, PrintWriter stdout, PrintWriter stderr) throws Exception {
		return BatchCompiler.compile(args, stdout, stderr, null);
	}
}
