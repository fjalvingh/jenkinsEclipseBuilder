package to.etc.prjbuilder.scm;

import java.util.*;

public class MergeDetails {
	private List<MrgFileChange> m_changeList = new ArrayList<MrgFileChange>();

	public MergeDetails(List<MrgFileChange> changeList) {
		m_changeList = changeList;
	}

	public List<MrgFileChange> getChangeList() {
		return m_changeList;
	}

	public void dump(Appendable a) throws Exception {
		for(MrgFileChange fc : getChangeList())
			fc.dump(a);
	}
}
