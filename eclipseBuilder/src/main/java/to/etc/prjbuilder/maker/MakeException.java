package to.etc.prjbuilder.maker;

import to.etc.prjbuilder.builder.*;

public class MakeException extends RuntimeException {
	private SourceModule	m_smv;

	public MakeException(SourceModule smv, String message) {
		super(message);
		m_smv = smv;
	}

	public MakeException(SourceModule smv, Throwable cause) {
		super(cause);
		m_smv = smv;
	}

	public MakeException(SourceModule smv, String message, Throwable cause) {
		super(message, cause);
		m_smv = smv;
	}
	public SourceModule getSmv() {
		return m_smv;
	}
}
