package to.etc.prjbuilder.maker;

import to.etc.prjbuilder.builder.*;

public class MakeConfigException extends MakeException {
	public MakeConfigException(SourceModule smv, String message, Throwable cause) {
		super(smv, message, cause);
	}

	public MakeConfigException(SourceModule smv, String message) {
		super(smv, message);
	}

	public MakeConfigException(SourceModule smv, Throwable cause) {
		super(smv, cause);
	}
}
