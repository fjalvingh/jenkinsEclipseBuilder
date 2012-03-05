package to.etc.prjbuilder.builder;

public class BuildException extends RuntimeException {
	public BuildException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public BuildException(String arg0) {
		super(arg0);
	}

	public BuildException(Throwable arg0) {
		super(arg0);
	}
}
