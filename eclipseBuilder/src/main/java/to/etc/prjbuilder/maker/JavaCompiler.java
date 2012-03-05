package to.etc.prjbuilder.maker;

public class JavaCompiler {
	static private IJavaCompiler m_compiler = new EcjCompiler();

	public static IJavaCompiler getCompiler() {
		return m_compiler;
	}

	public static void setCompiler(IJavaCompiler compiler) {
		m_compiler = compiler;
	}
}
