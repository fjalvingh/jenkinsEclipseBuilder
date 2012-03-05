package to.etc.prjbuilder.builder;

public enum BuildMode {
	/** Completely clean build; all data is deleted and all gets checked out anew */
	CLEAN,

	/** An update build does unconditional SCM updates then rebuilds all modules */
	UPDATE,

	/** A normal build checks for changes on SCM modules before updating or building a module. */
	NORMAL
}
