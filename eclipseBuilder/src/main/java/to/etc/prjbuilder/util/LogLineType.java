package to.etc.prjbuilder.util;

public enum LogLineType {
	HDR,
	ERR,
	/** More important than log, below header. Used to log checkouts/updates/compiles. */
	IMP,
	LOG,
	DET,

	// Compilation/build result types.

	/** Compile error (regexp) */
	CER,

	/** Compiler warning (regexp) */
	CWA,

	/** Other compile message (all others) */
	CDE,

	/** Info on a MERGE (normal) */
	MRG,
	MGE,
}
