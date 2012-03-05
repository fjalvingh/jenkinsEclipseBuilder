package to.etc.prjbuilder.maker;

import java.io.*;

import org.w3c.dom.*;

import to.etc.prjbuilder.util.*;
import to.etc.xml.*;

/**
 * Factory for autodiscovery of a given builder.
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on Jul 15, 2007
 */
public class EclipseMakerFactory implements MakerFactory {
	/**
	 * This is accepted as an eclipse builder if the directory contains both .project and .classpath files.
	 */
	public ModuleMaker makeBuilder(Reporter r, File f) throws Exception {
		File proj = new File(f, ".project");
		File clas = new File(f, ".classpath");
		if(proj.exists() && clas.exists()) {
			//-- Try to load the module name from the .project file,
			try {
				Document	doc = DomTools.getDocument(proj, false);
				Node	root	= DomTools.getRootElement(doc);
				if(! root.getNodeName().equals("projectDescription"))
					return null;
				String	name	= DomTools.stringNode(root, "name");
				if(name != null) {
					//-- Is this a WTP web app or a normal module project?
					File	facets = new File(new File(f, ".settings"), "org.eclipse.wst.common.project.facet.core.xml");
					if(facets.exists() && facets.isFile()) {
						doc = DomTools.getDocument(facets, false);		// Load facets
						root= DomTools.getRootElement(doc);
						if(containsFacet(root, "jst.web")) {
							//-- This is a webapp, not a normal app.
							return new EclipseWebMaker(f, name);
						}
					}

					//-- Default case: a normal Eclipse module.
					return new EclipseModuleMaker(f, name);
				}
			} catch(Exception x) {
				r.error("Can't load Eclipse .project file "+proj+": "+x);
			}
		}
		return null;
	}

	/**
	 * Returns T if the specified facet is present.
	 * @param root
	 * @param name
	 * @return
	 */
	private boolean	containsFacet(Node root, String name) {
		if(! root.getNodeName().equals("faceted-project"))
			return false;
		NodeList nl = root.getChildNodes();
		for(int i = nl.getLength(); --i >= 0;) {
			Node n = nl.item(i);
			if(n.getNodeName().equals("fixed") || n.getNodeName().equals("installed")) {
				//-- Facet name?
				Node fn = n.getAttributes().getNamedItem("facet");
				if(fn != null) {
					String s = fn.getNodeValue();
					if(name.equals(s.trim()))
						return true;
				}
			}
		}
		return false;
	}
}
