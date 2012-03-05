package to.etc.prjbuilder.util;

public enum JavaVersion {
	JAVA_1_2(12, "1.2"),
	JAVA_1_3(13, "1.3"),
	JAVA_1_4(14, "1.4"),
	JAVA_1_5(15, "1.5", "5"),
	JAVA_1_6(16, "1.6", "6"),
	JAVA_1_7(17, "1.7", "7");


	private String[]	m_code;
	private int			m_num;

	JavaVersion(int n, String... s) {
		m_num = n;
		m_code = s;
	}
	public String[] getCodes() {
		return m_code;
	}
	public int getNum() {
		return m_num;
	}

	static public JavaVersion	byName(String name) {
		for(JavaVersion v : JavaVersion.values()) {
			for(String c : v.m_code) {
				if(c.equalsIgnoreCase(name))
					return v;
			}
		}
		return null;
	}
	@Override
	public String toString() {
		return m_code[0];
	}

	static public JavaVersion adjustTargetVersion(JavaVersion sv, JavaVersion jv) {
		if(jv == null)
			return null;
		if(jv.getNum() > sv.getNum()) {
			jv = sv;
			return jv;
		}
		switch(sv){
			case JAVA_1_4:
			case JAVA_1_5:
				return sv; // 1.4 src only accepts 1.4 src

			case JAVA_1_6:
			case JAVA_1_7:
				if(jv.getNum() < JavaVersion.JAVA_1_5.getNum())
					return JavaVersion.JAVA_1_5;
				break;
			default:
				break;
		}

		return jv;
	}
}
