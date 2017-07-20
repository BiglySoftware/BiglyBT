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

import org.eclipse.swt.graphics.Image;

/**
 * The interface for a progress report; a progress report is a read only object containing
 * all the properties of an <code>IProgressReporter</code> at a moment in time
 * @author knguyen
 *
 */
public interface IProgressReport
{

	public IProgressReporter
	getReporter();

	/**
	 * Returns the reporter type of the reporter that created this report
	 * @return
	 */
	public String getReporterType();

	/**
	 * Returns the id of the reporter that created this report
	 * @return
	 */
	public int getReporterID();

	/**
	 * Returns the minimum amount of work to be done
	 * @return
	 */
	public int getMinimum();

	/**
	 * Returns the maximum amount of work to be done
	 * @return
	 */
	public int getMaximum();

	/**
	 * Returns the amount of work done so far
	 * @return
	 */
	public int getSelection();

	/**
	 * Returns the percentage of work done so far
	 * @return
	 */
	public int getPercentage();

	/**
	 * Returns whether the reporter is still in active state
	 * @return
	 */
	public boolean isActive();

	/**
	 * Returns whether the amount of work done so far can not be calculated accurately
	 * @return
	 */
	public boolean isIndeterminate();

	/**
	 * Returns whether the reporter is done with all its work
	 * @return
	 */
	public boolean isDone();

	/**
	 * Returns whether the amount of work done is in percentage form
	 * @return
	 */
	public boolean isPercentageInUse();

	/**
	 * Returns whether the process owning the reporter allows a cancel request
	 * @return
	 */
	public boolean isCancelAllowed();

	/**
	 * Returns whether the reporter has been canceled
	 * @return
	 */
	public boolean isCanceled();

	/**
	 * Returns whether the process owning the reporter allows a retry request
	 * @return
	 */
	public boolean isRetryAllowed();

	/**
	 * Returns whether the reporter has reported an error
	 * @return
	 */
	public boolean isInErrorState();

	/**
	 * Returns whether the reporter has been marked for disposal
	 * @return
	 */
	public boolean isDisposed();

	/**
	 * Returns the title of the reporter; this is mainly used as a window title if the reporter is shown by itself in a window
	 * @return
	 */
	public String getTitle();

	/**
	 * Returns the message for this particular report
	 * @return
	 */
	public String getMessage();

	/**
	 * Returns the detail message for this particular report
	 * @return
	 */
	public String getDetailMessage();

	/**
	 * Returns the error message (if any) for this report
	 * @return
	 */
	public String getErrorMessage();

	/**
	 * Returns the name of the reporter
	 * @return
	 */
	public String getName();

	/**
	 * Returns the image of the reporter
	 * @return
	 */
	public Image getImage();

	/**
	 * Returns the object associated with this report
	 * @return
	 */
	public Object getObjectData();

	/**
	 * Returns the type of report this is
	 * @return
	 */
	public int getReportType();

}