package to.etc.prjbuilder.builder;

import java.util.*;

public class OrderedBuildList implements Iterable<ModuleBuildInfo> {
	private List<ModuleBuildInfo>		m_list = new ArrayList<ModuleBuildInfo>();

	public Iterator<ModuleBuildInfo> iterator() {
		return m_list.iterator();
	}

	public void	add(ModuleBuildInfo bi) {
		if(! m_list.contains(bi))
			m_list.add(bi);
	}

	public void	addAll(Iterable<ModuleBuildInfo> in) {
		for(ModuleBuildInfo bi : in)
			add(bi);
	}
	public int		size() {
		return m_list.size();
	}

	public int getCompilationCount() {
		int count = 0;
		for(ModuleBuildInfo bi : this) {
			if(bi.getBuildReason() != null) {
				count++;
			}
		}
		return count;
	}
}
