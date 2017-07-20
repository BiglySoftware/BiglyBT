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



/**
 * A simple listener interface used to pass a <code>ProgressReport</code> to a listener from a <code>ProgressReporter</code>
 * @author knguyen
 *
 */
public interface IProgressReporterListener
	extends IProgressReportConstants
{

	/**
	 * When an event is detected this callback method will be called so listeners can take appropriate action such as updating a UI element
	 * <p>
	 * Currently there are 2 return types recognized:
	 * <ul>
	 * <li><code>RETVAL_OK</code> - Indicates the given <code>ProgressReport</code> has been consumed successfully</li>
	 * <li><code>RETVAL_OK_TO_DISPOSE</code> - Indicates that this listener is no longer interested in any subsequent reports</li>
	 * </ul>
	 * </p>
	 *
	 * The return value of <code>RETVAL_OK_TO_DISPOSE</code> is a hint to the caller that it is now
	 * safe to remove this listener from the caller's list of listeners.  This pattern allows the caller
	 * to perform clean up operations associated with this listener, and allows the listener to initiate
	 * its own removal from within an event loop.
	 *
	 * <p> A typical life cycle of an event listener is create listener, register listener, then remove listener.
	 * To accomplish all three steps we would need a reference to the listeners at all time as can be seen in this sample: </p>
	 *
	 *
	 * <pre>
	 * ProgressReporter reporter = new ProgressReporter("I'm a reporter");
	 *
	 * // Create a concrete listener since we need to remove it later for clean up
	 * IProgressReporterListener listener = new IProgressReporterListener() {
	 * 	public int report(ProgressReport progressReport) {
	 * 		// Do some work here
	 * 		return RETVAL_OK;
	 * 	}
	 * };
	 *
	 * // Add the listener
	 * reporter.addListener(listener);
	 *
	 * // Do some more work
	 *
	 * // Then for clean up remove the listener
	 * if ([some condition] == true) {
	 * 	reporter.removeListener(listener);
	 * }
	 *
	 * </pre>
	 *
	 *
	 * Sometime it is more convenient to remove a listener from within the call to .report() but a direct
	 * attempt at removal of this listener may result in modification conflict since most likely this method is being
	 * called from within an iterator loop which does not allow concurrent modifications.
	 *
	 * To address this limitation we make use of the <code>RETVAL_OK_TO_DISPOSE</code> as a return code which will
	 * allow the caller to perform the clean up as in this modification of the example above:
	 *
	 * <pre>
	 * ...
	 *
	 * final IProgressReporterListener listener = new IProgressReporterListener() {
	 * 	public int report(ProgressReport progressReport) {
	 *
	 * 		// Do some work here
	 *
	 * 		// This would throw a ConcurrentModificationException
	 *		reporter.removeListener(listener);
	 *
	 * 		// This would work
	 * 		if ([some condition] == true) {
	 * 			return RETVAL_OK_TO_DISPOSE;
	 * 		}
	 *
	 * 		return RETVAL_OK;
	 * 	}
	 * };
	 * </pre>
	 *
	 * <p>If the decision to remove a listener is based on the information contained in the given <code>ProgressReport</code>
	 * we can implement a much simpler listener as an inner class like so:</p>
	 * <pre>
	 *
	 * ProgressReporter reporter = new ProgressReporter("I'm a reporter");
	 *
	 * // Creates an anonymous inner class using RETVAL_OK_TO_DISPOSE to initiate clean up
	 * reporter.addListener(new IProgressReporterListener() {
	 * 	public int report(ProgressReport progressReport) {
	 *
	 * 		// Do some work
	 *
	 * 		if ([some condition] == true) {
	 * 			return RETVAL_OK_TO_DISPOSE;
	 * 		}
	 * 		return RETVAL_OK;
	 *
	 * 	}}
	 * );
	 *
	 *
	 * </pre>
	 *
	 * @param progressReport
	 * @return
	 */
	public int report(IProgressReport progressReport);

}