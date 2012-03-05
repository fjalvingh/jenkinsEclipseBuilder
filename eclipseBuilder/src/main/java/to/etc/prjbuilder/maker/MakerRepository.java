package to.etc.prjbuilder.maker;

import java.io.*;
import java.util.*;

import to.etc.prjbuilder.util.*;

/**
 * Repository of project makers.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 15, 2007
 */
final public class MakerRepository {
	static private List<MakerFactory> m_fact_al = new ArrayList<MakerFactory>();

	private MakerRepository() {
	}

	static public void register(MakerFactory bf) {
		m_fact_al.add(bf);
	}

	static public ModuleMaker 	findBuilder(Reporter r, File f) throws Exception {
		Exception stored_x = null;
		for(MakerFactory bf : m_fact_al) {
			try {
				ModuleMaker b = bf.makeBuilder(r, f);
				if(b != null)
					return b;
			}
			catch(Exception x) {
				stored_x = x;
			}
		}
		if(stored_x != null)
			throw stored_x;
		return null;
	}

	static {
		register(new EclipseMakerFactory());
	}
}
