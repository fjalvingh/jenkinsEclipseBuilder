package to.etc.jenkins;

import java.util.*;

import org.junit.runner.*;
import org.junit.runner.notification.*;

public class JUnitListener extends RunListener {
	private List<ATest> m_testList = new ArrayList<ATest>();

	static public class ATest {
		private Description	m_description;

		private long m_duration;

		private Failure m_failure;

		private boolean m_ignored;

		public ATest(Description description) {
			m_description = description;
		}

		void setDuration(long d) {
			m_duration = d;
		}

		void setFailure(Failure failure) {
			m_failure = failure;
		}

		public void setIgnored() {
			m_ignored = true;
		}

		public Description getDescription() {
			return m_description;
		}

		public long getDuration() {
			return m_duration;
		}

		public Failure getFailure() {
			return m_failure;
		}

		public boolean isIgnored() {
			return m_ignored;
		}
	}

	private long m_ts_start;

	private ATest m_test;

	private int m_failed;

	private int m_ignored;

	private Date m_suiteStart;

	@Override
	public void testStarted(Description description) throws Exception {
		m_ts_start = System.currentTimeMillis();
		m_test = new ATest(description);
		m_testList.add(m_test);
	}

	@Override
	public void testFinished(Description description) throws Exception {
		long cts = System.currentTimeMillis();
		m_test.setDuration(cts - m_ts_start);
		m_test = null;
	}

	@Override
	public void testFailure(Failure failure) throws Exception {
		m_test.setFailure(failure);
		m_failed++;
	}

	@Override
	public void testIgnored(Description description) throws Exception {
		m_test.setIgnored();
		m_ignored++;
	}

	public int getTestCount() {
		return m_testList.size();
	}

	public int getFailedCount() {
		return m_failed;
	}

	public int getIgnoredCount() {
		return m_ignored;
	}

	public List<ATest> getTestList() {
		return Collections.unmodifiableList(m_testList);
	}

	public Date getSuiteStart() {
		return m_suiteStart;
	}

	public void reset() {
		m_testList.clear();
		m_failed = 0;
		m_ignored = 0;
		m_suiteStart = new Date();
	}
}
