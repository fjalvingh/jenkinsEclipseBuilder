package to.etc.prjbuilder.builder;


/**
 * Filters log input.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 17, 2007
 */
public interface MessageFilter {
	public void filterLine(MessageFilterSink r, String line);
}
