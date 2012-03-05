package to.etc.prjbuilder.scm;

public enum MrgChangeType {
	/** The file or directory was added (nonexistent -&gt; new) */
	ADDED

	/** The file or directory was deleted (existent -> deleted) */
	, REMOVED

	/** The file or directory was renamed */
	, RENAMED

	/** The file (bydef) was changed; it's content has changed */
	, MODIFIED

	/** The file was renamed AND modified */
	, RENMOD

	, UNKNOWN
}
