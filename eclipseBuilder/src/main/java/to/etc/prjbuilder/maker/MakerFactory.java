package to.etc.prjbuilder.maker;

import java.io.*;

import to.etc.prjbuilder.util.*;

/**
 * Factory class containing ways to create ModuleBuilders from a source repos
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 15, 2007
 */
public interface MakerFactory {
	/**
	 * This should inspect the module's source root directory passed and
	 * see if it knows how to build this target. If it does know how to
	 * do that it must return a builder for that module. The returned maker
	 * <i>must</i> return the module name for that module; all other info
	 * is not needed.
	 *
	 * @param f
	 * @return
	 */
	ModuleMaker makeBuilder(Reporter r, File f) throws Exception;
}
