package to.etc.prjbuilder.maker;

import java.io.*;

import to.etc.prjbuilder.builder.*;
import to.etc.prjbuilder.util.*;

/**
 * The result of a java compilation and jar creation step. This contains the target-relative name of
 * the generated product.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 17, 2007
 */
public class JarProduct extends FileProduct implements GeneratedProduct {
	private boolean m_exported;

	public JarProduct(ModuleBuildInfo source, File f, String name) {
		super(source, f, name);
	}

	public void generate(ModuleBuildInfo root, Reporter r) throws Exception {
	//		if(!m_exported)
	//			return;
	//		File tgt = new File(root.getOutputDir(), getNameWithoutPath());
	//		FileTool.copyFile(tgt, getFile());
	}

	public void setExported(boolean exported) {
		m_exported = exported;
	}

	public boolean isExported() {
		return m_exported;
	}
}
