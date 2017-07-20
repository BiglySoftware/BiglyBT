/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.progress;

import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.mainwindow.MainStatusBar;

import com.biglybt.core.util.CopyOnWriteList;

/**
 * A manager that aggregates and forward progress information for long running operations
 * <p> This is a non-intrusive implementation, such that, it does not directly manage any of the process; it simply receives and forwards information</p>
 * <p> The primary user of this class is the {@link MainStatusBar} where it is used to display progress information</p>
 * @author knguyen
 *
 */
public class ProgressReportingManager
	implements IProgressReportConstants
{

	private static ProgressReportingManager INSTANCE = null;
	private final ParameterListener configListenerAutoRemoveInactive;

	/**
	 * A custom stack to keep track of <code>ProgressReporter</code>
	 */
	private ProgressReporterStack progressReporters = new ProgressReporterStack();

	/**
	 * Keeps count of all <code>ProgressReporter</code> created since this session started;
	 * is used as unique ID and hashCode for each instance of <code>ProgressReporter</code>
	 *
	 */
	private int reporterCounter = Integer.MIN_VALUE;

	public static final int COUNT_ALL = 0;

	public static final int COUNT_ACTIVE = 1;

	public static final int COUNT_ERROR = 2;

	/**
	 * A <code>CopyOnWriteList</code> of <code>IProgressReportingListener</code>
	 */
	private CopyOnWriteList listeners = new CopyOnWriteList();

	/**
	 * Convenience variable tied to the parameter "auto_remove_inactive_items"
	 */
	private boolean isAutoRemove = false;

	/**
	 * Private constructor
	 */
	private ProgressReportingManager() {
		/*
		 * Set up isAutoRemove flag
		 */
		configListenerAutoRemoveInactive = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				isAutoRemove = COConfigurationManager.getBooleanParameter("auto_remove_inactive_items");
			}
		};
		COConfigurationManager.addAndFireParameterListener("auto_remove_inactive_items",
				configListenerAutoRemoveInactive);
	}

	public static final synchronized ProgressReportingManager getInstance() {
		if (null == INSTANCE) {
			INSTANCE = new ProgressReportingManager();
		}
		return INSTANCE;
	}

	public static void destroyInstance() {
		if (INSTANCE != null) {
			INSTANCE.dispose();
			INSTANCE = null;
		}
	}

	private void dispose() {
		COConfigurationManager.removeParameterListener("auto_remove_inactive_items",
				configListenerAutoRemoveInactive);
	}

	public IProgressReporter
	addReporter()
	{
		return( new ProgressReporter( this ));
	}

	public IProgressReporter
	addReporter(
		String	name )
	{
		return( new ProgressReporter( this, name ));
	}

	/**
	 * Returns the number of reporters that have sent any event to this manager and have not been removed
	 * <ul>
	 * <li><code>COUNT_ERROR</code> - count all reporters in error state</li>
	 * <li><code>COUNT_ACTIVE</code> - count all reporters that are still active</li>
	 * <li><code>COUNT_ALL</code> - count all reporters</li>
	 * </ul>
	 * @param whatToCount one of the above constants; will default to <code>COUNT_ALL</code> if the parameter is unrecognized
	 * @return
	 */
	public int getReporterCount(int whatToCount) {
		if (whatToCount == COUNT_ERROR) {
			return progressReporters.getErrorCount();
		}
		if (whatToCount == COUNT_ACTIVE) {
			return progressReporters.getActiveCount();
		}

		return progressReporters.size();
	}

	/**
	 * A convenience method for quickly determining whether more than one reporter is still active.
	 * This method can be much quicker than calling {@link #getReporterCount()} and inspecting the returned value
	 * if the number of reporters is high since we may not have to go through the entire list before getting the result
	 *
	 * @return <code>true</code> if there are at least 2 active reporters; <code>false</code> otherwise
	 */
	public boolean hasMultipleActive() {
		return progressReporters.hasMultipleActive();
	}

	/**
	 * Returns the next active reporter
	 * @return the next reporter that is still active; <code>null</code> if none are active or no reporters are found
	 */
	public IProgressReporter getNextActiveReporter() {
		return progressReporters.getNextActiveReporter();
	}

	/**
	 * Returns the current reporter, in other word, the last reporter to have reported anything
	 * @return the last reporter; <code>null</code> if none are found
	 */
	public IProgressReporter getCurrentReporter() {
		return progressReporters.peek();
	}

	/**
	 * Returns a modifiable list of <code>ProgressReporter</code>s; manipulating this list has no
	 * effect on the internal list of reporters maintained by this manager
	 *
	 * @param onlyActive <code>true</code> to filter the list to only include those reporters that are still active
	 * @return a sorted List of <code>ProgressReporter</code> where the oldest reporter would be at position 0
	 */
	public List getReporters(boolean onlyActive) {
		List reporters = progressReporters.getReporters(onlyActive);
		Collections.sort(reporters);
		return reporters;
	}

	/**
	 *
	 * Returns a modifiable array of <code>ProgressReporter</code>s; manipulating this array has no
	 * effect on the internal list of reporters maintained by this manager
	 * @param onlyActive <code>true</code> to filter the array to only include those reporters that are still active
	 * @return a sorted array of <code>ProgressReporter</code> where the oldest reporter would be at position 0
	 */
	public IProgressReporter[] getReportersArray(boolean onlyActive) {
		List rpList = progressReporters.getReporters(onlyActive);
		IProgressReporter[] array = (IProgressReporter[]) rpList.toArray(new IProgressReporter[rpList.size()]);
		Arrays.sort(array);
		return array;
	}

	/**
	 * Removes the given <code>ProgressReporter</code> from this manager.  This has the effect that
	 * any subsequent event reported by the same reporter will not be captured nor forwarded by this manager
	 * @param reporter
	 * @return
	 */
	public boolean remove(IProgressReporter reporter) {
		boolean value = progressReporters.remove(reporter);
		notifyListeners(MANAGER_EVENT_REMOVED, reporter);
		return value;
	}

	/**
	 *
	 * @param listener
	 */
	public void addListener(IProgressReportingListener listener) {
		if (null != listener && !listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	/**
	 *
	 * @param listener
	 */
	public void removeListener(IProgressReportingListener listener) {
		if (null != listener && listeners.contains(listener)) {
			listeners.remove(listener);
		}
	}

	/**
	 * Notifies listeners that the given <code>ProgressReporter</code> has been modified
	 * @param eventType
	 * @param reporter
	 */
	private void notifyListeners(int eventType, IProgressReporter reporter) {
		for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
			IProgressReportingListener listener = (IProgressReportingListener) iterator.next();
			if (null != listener) {
				try {
					listener.reporting(eventType, reporter);
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		}
	}

	/**
	 * Push this reporter on top of the stack, and notifies any listeners that a state change has occurred
	 * @param reporter
	 */
	protected void notifyManager(IProgressReporter reporter) {

		/*
		 * Update the history stack and notify listeners
		 */

		IProgressReport pReport = reporter.getProgressReport();
		if ((isAutoRemove && !pReport.isActive())
				|| pReport.isDisposed()) {
			progressReporters.remove(reporter);
			notifyListeners(MANAGER_EVENT_REMOVED, reporter);
		} else if (progressReporters.contains(reporter)) {
			progressReporters.push(reporter);
			notifyListeners(MANAGER_EVENT_UPDATED, reporter);
		} else {
			progressReporters.push(reporter);
			notifyListeners(MANAGER_EVENT_ADDED, reporter);
		}

	}

	/**
	 * Returns the next available ID that can be assigned to a {@link ProgressReporter}
	 * @return int the next available ID
	 */
	protected synchronized final int getNextAvailableID() {

		/*
		 * This is a simple brute forced way to generate unique ID's which can also be use as a unique hashcode
		 * without having to directly track all previously created/disposed ProgressReporters
		 *
		 * This is synchronized to ensure that the incrementing of reporterCounter is consistent
		 * so that each ProgressReporter is guaranteed to have a unique ID (which is also used as its hashCode)
		 *
		 * WARNING: This method is mainly intended to be used by the constructors of ProgressReporter and should not be called
		 * from anywhere else (unless you really know what you're doing); unintended repeated call to this method can exhaust
		 * the limit of the integer.  This counter starts from Integer.MIN_VALUE
		 */
		return reporterCounter++;
	}

}
