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
 * These are all the constants used by the ProgressReporter and related classes
 * <p>
 * These constants are in this separate interface so that classes that need to reference these
 * constants can simply implement this interface and reference them directly like REPORT_TYPE_CANCEL == [some test]
 * instead of having to fully reference them like IProgressReportConstants.REPORT_TYPE_CANCEL == [some test]</p>
 * @author knguyen
 *
 */
public interface IProgressReportConstants
{

	/**
	 * Unless specified by the user for a particular reporter all reporters have this default type
	 */
	public static final String REPORTER_TYPE_DEFAULT = "default.reporter.type";

	/**
	 * The default visibility for a <code>ProgressReporter</code>; this is the most generous
	 * visibility level in that any interested processes can see this reporter and receive it's <code>ProgressReporter.ProgressReport</code>
	 * This is used for when it makes sense to show the full information about a reporter to the user; this reporter will
	 * be seen in the progress history
	 */
	public static final int REPORTER_VISIBILITY_USER = 1;

	/**
	 * A hint to the <code>ProgressReportingManager</code> and any interested parties that the
	 * reporter and its reports are not intended to be shown (in full) to the user.  UI components
	 * displaying progress reporters and reports can use this hint to show a minimum set of values and additionally
	 * skip soliciting the user for any loopback events.
	 */
	public static final int REPORTER_VISIBILITY_SYSTEM = 2;

	//======= report types =============

	/**
	 * Default event type indicating no event
	 */
	public static final int REPORT_TYPE_INIT = 0;

	/**
	 * When {@link ProgressReporter#cancel()} is detected
	 */
	public static final int REPORT_TYPE_CANCEL = 1;

	/**
	 * When {@link ProgressReporter#setDone()} is detected
	 */
	public static final int REPORT_TYPE_DONE = 2;

	/**
	 * When {@link ProgressReporter#setIndeterminate(boolean)} is detected
	 */
	public static final int REPORT_TYPE_MODE_CHANGE = 3;

	/**
	 * When {@link ProgressReporter#setErrorMessage(String)} is detected
	 */
	public static final int REPORT_TYPE_ERROR = 4;

	/**
	 * When {@link ProgressReporter#retry()} is detected
	 */
	public static final int REPORT_TYPE_RETRY = 5;

	/**
	 * When any other property is modified
	 */
	public static final int REPORT_TYPE_PROPERTY_CHANGED = 6;

	/**
	 * When {@link ProgressReporter#dispose()} is called
	 */
	public static final int REPORT_TYPE_DISPOSED = 7;

	//========== return values for report listeners ======

	/**
	 * Default return value from a listener indicating the event has been received and processed successfully
	 */
	public static final int RETVAL_OK = 0;

	/**
	 * A return value from a listener indicating that the listener is done and is no longer interested
	 * in any subsequent event; this is a hint to the notifier so that the notifier can perform clean up
	 * operation relating to that particular listener
	 */
	public static final int RETVAL_OK_TO_DISPOSE = 1;

	//============ events from the ProgressReportingManager ====

	/**
	 * When a reporter is added to the history list
	 */
	public static final int MANAGER_EVENT_ADDED = 1;

	/**
	 * When a reporter is removed from the history list
	 */
	public static final int MANAGER_EVENT_REMOVED = 2;

	/**
	 * When a reporter that is already in the history list report an event
	 */
	public static final int MANAGER_EVENT_UPDATED = 3;


	//======== style bits for UI components=================

	/**
	 * Default style bit for no styles
	 */
	public static final int NONE = 0;

	/**
	 * Automatically closes the window when the reporter is finished;
	 * this only takes effect when there is only 1 reporter in the window
	 */
	public static final int AUTO_CLOSE = 1 << 1;

	/**
	 * Open the window as MODAL
	 */
	public static final int MODAL = 1 << 2;

	/**
	 * The reporter is the only one in a window
	 */
	public static final int STANDALONE = 1 << 3;

	public static final int BORDER = 1 << 4;

	public static final int SHOW_TOOLBAR = 1 << 5;

	//========== report message types =================

	public static final int MSG_TYPE_INFO = 1 << 1;

	public static final int MSG_TYPE_ERROR = 1 << 2;

	public static final int MSG_TYPE_LOG = 1 << 3;
}