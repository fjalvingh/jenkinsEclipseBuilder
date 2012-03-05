package to.etc.prjbuilder.maker;

import java.io.*;

public interface IJavaCompiler {
	boolean compile(String[] args, PrintWriter stdout, PrintWriter stderr) throws Exception;
}
