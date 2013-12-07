package com.linfords.swing.util;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Detect Java-level deadlocks.
 * <p/>
 * Java 1.5 only supports finding monitor based deadlocks. 1.6's
 * {@link java.lang.management.ThreadMXBean} supports {@link java.util.concurrent.locks.Lock} based
 * deadlocks.
 */
public class DeadlockDetector {
	private final static AsyncPrinter aout = AsyncPrinter.defaultInstance();
	private final static DeadlockDetector INSTANCE = new DeadlockDetector();

	public static void initMonitoring() {
		INSTANCE.initTimer();
	}

	private final ThreadMXBean mbean = ManagementFactory.getThreadMXBean();

	private void analyze() {
		List<ThreadInfo> deadlocks = findDeadlocks();
		if (deadlocks.isEmpty()) {
			return;
		}
		printReport(deadlocks);
	}

	private void printReport(List<ThreadInfo> deadlocks) {
		aout.add("Deadlock detected\n=================\n");
		for (ThreadInfo thread : deadlocks) {
			aout.add(format("\"%s\":", thread.getThreadName()));
			aout.add(format("  waiting to lock Monitor of %s ",
					thread.getLockName()));
			aout.add(format("  which is held by \"%s\"",
					thread.getLockOwnerName()));
			aout.add("");
		}
	}

	private List<ThreadInfo> findDeadlocks() {
		long[] monitorDeadlockedThreads = mbean.findMonitorDeadlockedThreads();
		if (monitorDeadlockedThreads == null)
			return emptyList();
		return asList(mbean.getThreadInfo(monitorDeadlockedThreads));
	}

	/**
	 * Sets up a timer to check for hangs frequently.
	 */
	private void initTimer() {
		final long initialDelay = 1000; // Wait before beginning monitor
		final long delay = 60000;
		final boolean isDaemon = true;
		Timer timer = new Timer("DeadlockDector", isDaemon);
		timer.schedule(new ThreadDumpAnalyzer(), initialDelay, delay);
	}

	private class ThreadDumpAnalyzer extends TimerTask {
		@Override
		public void run() {
			analyze();
		}
	}
}
