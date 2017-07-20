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

/**
 * A convenience Stack for tracking <code>ProgressReporter</code>s
 * <p>When a reporter is pushed onto the stack we remove any other occurrences of the same reporter so
 * that there is at most one instance of a particular reporter in the stack at any time</p>
 *
 * @author knguyen
 *
 */
class ProgressReporterStack
{
	private Stack reporterStack = new Stack();

	/**
	 * A dummy object used purely for implementing synchronized blocks
	 */
	private Object lockObject = new Object();

	/**
	 * Pushes the given reporter on top of the stack; additionally remove any other occurrence of the reporter.
	 * @param reporter
	 */
	public void push(IProgressReporter reporter) {
		if (null == reporter) {
			return;
		}
		synchronized (lockObject) {

			/*
			 * Remove the reporter from the stack if it's in there already
			 */
			if (reporterStack.contains(reporter)) {
				reporterStack.remove(reporter);
			}

			reporterStack.push(reporter);
		}
	}

	/**
	 * Returns the reporter at the top of the stack
	 * @return
	 */
	public IProgressReporter peek() {
		synchronized (lockObject) {
			if (!reporterStack.isEmpty()) {
				return (IProgressReporter) reporterStack.peek();
			}
			return null;

		}
	}

	/**
	 * Remove the given <code>ProgressReporter</code>;
	 * @return <code>true</code> if the given reporter is found; otherwise <code>false</code>
	 */
	public boolean remove(IProgressReporter reporter) {
		synchronized (lockObject) {
			if (null != reporter && reporterStack.contains(reporter)) {
				return reporterStack.remove(reporter);
			}
			return false;
		}
	}

	/**
	 * Returns whether or not the given <code>IProgressReporter</code> is already in the stack
	 * @param reporter
	 * @return
	 */
	public boolean contains(IProgressReporter reporter) {
		synchronized (lockObject) {
			return reporterStack.contains(reporter);
		}
	}

	/**
	 * Remove and return the reporter at the top of the stack
	 * @return
	 */
	public IProgressReporter pop() {
		synchronized (lockObject) {
			if (!reporterStack.isEmpty()) {
				return (IProgressReporter) reporterStack.pop();
			}
			return null;
		}
	}

	/**
	 * Trim the list by removing all inactive reporters
	 */
	public void trim() {
		synchronized (lockObject) {
			for (Iterator iterator = reporterStack.iterator(); iterator.hasNext();) {
				IProgressReporter reporter = ((IProgressReporter) iterator.next());
				if (!reporter.getProgressReport().isActive()) {
					iterator.remove();
				}
			}
		}
	}

	/**
	 * Returns a list of reporters; this list can safely be manipulated because it is not directly referencing the internal list
	 * @param onlyActive <code>true</code> to return only reporters that are still active, <code>false</code> to return all reporters
	 * @return <code>List</code>
	 */
	public List getReporters(boolean onlyActive) {
		synchronized (lockObject) {
			List reporters = new ArrayList();
			for (Iterator iterator = reporterStack.iterator(); iterator.hasNext();) {
				IProgressReporter reporter = ((IProgressReporter) iterator.next());
				if (onlyActive) {
					if (reporter.getProgressReport().isActive()) {
						reporters.add(reporter);
					}
				} else {
					reporters.add(reporter);
				}
			}
			return reporters;
		}

	}

	public int size() {
		synchronized (lockObject) {
			return reporterStack.size();
		}
	}

	/**
	 * Returns the number of reporters in the stack that are still active
	 * @return
	 */
	public int getActiveCount() {
		synchronized (lockObject) {
			int activeReporters = 0;
			for (Iterator iterator = reporterStack.iterator(); iterator.hasNext();) {
				IProgressReporter reporter = ((IProgressReporter) iterator.next());
				if (reporter.getProgressReport().isActive()) {
					activeReporters++;
				}
			}
			return activeReporters;
		}
	}

	/**
	 * Returns the number of reporters in the stack that are in error state
	 * @return
	 */
	public int getErrorCount() {
		synchronized (lockObject) {
			int reportersInErrorState = 0;
			for (Iterator iterator = reporterStack.iterator(); iterator.hasNext();) {
				IProgressReporter reporter = ((IProgressReporter) iterator.next());
				if (reporter.getProgressReport().isInErrorState()) {
					reportersInErrorState++;
				}
			}
			return reportersInErrorState;
		}
	}

	/**
	 * A convenience method for quickly determining whether more than one reporter is still active.
	 * This method can be much quicker than calling {@link #getActiveCount()} and inspecting the returned value
	 * if the number of reporters is high since we may not have to go through the entire list before getting the result
	 * @return <code>true</code> if there are at least 2 active reporters; <code>false</code> otherwise
	 */
	public boolean hasMultipleActive() {
		synchronized (lockObject) {
			int activeReporters = 0;
			for (Iterator iterator = reporterStack.iterator(); iterator.hasNext();) {
				IProgressReporter reporter = (IProgressReporter) iterator.next();
				if (reporter.getProgressReport().isActive()) {
					activeReporters++;
				}
				if (activeReporters > 1) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Get the next active reporter.
	 * <p><b>NOTE: </b> this is different from calling {@link #peek()} since the next active reporter may not be at the top of the stack</p>
	 * @return ProgressReporter the next reporter on the stack that is still active; <code>null</code> if none are active or none are found
	 */
	public IProgressReporter getNextActiveReporter() {
		synchronized (lockObject) {
			for (Iterator iterator = reporterStack.iterator(); iterator.hasNext();) {
				IProgressReporter reporter = (IProgressReporter) iterator.next();
				if (reporter.getProgressReport().isActive()) {
					return reporter;
				}
			}
		}
		return null;
	}
}