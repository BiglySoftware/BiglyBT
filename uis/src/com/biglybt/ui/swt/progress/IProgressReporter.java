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
 * The interface for a progress reporter
 * @author knguyen
 *
 */
public interface IProgressReporter
	extends Comparable
{

	/**
	 * Sets the type of a reporter.  This is a user defined property and no duplication check is ensured.
	 * This optional property can be used to identify particular types of reports so that report consumers
	 * have a way to ignore uninteresting report types
	 * @param reporterType
	 */
	public void setReporterType(String reporterType);

	/**
	 * Disposes any resources or listeners that this reporter has references to or otherwise is responsible for
	 * <p>Also removes it from the global <code>ProgressReportingManager</code> so that any subsequent event
	 * from this reporter is no longer forwarded </p>
	 */
	public void dispose();

	/**
	 * Returns an <code>IProgressReport</code> which is a snapshot of this reporter
	 * <p>
	 *
	 * <b>NOTE</b>: Each call to this method creates a new snapshot therefore the correct
	 * usage pattern is:</p>
	 * <pre>
	 *
	 * 	IProgressReport report = getProgressReport();
	 * 	if( report.isDone() == false ){
	 * 		// Do something
	 * 	{
	 * 	else if( report.isCanceled() == false ){
	 * 		// Do something else
	 * 	{
	 * 	...
	 *
	 * </pre>
	 * It may be tempting to use the less verbose pattern by repeatedly calling this
	 * method directly such as:
	 * <pre>
	 *
	 * 	if( getProgressReport().isDone == false ){
	 * 		// Do something
	 * 	{
	 * 	else if( getProgressReport().isCanceled == false ){
	 * 		// Do something else
	 * 	{
	 *
	 * </pre>
	 *
	 * BUT this can produce inconsistent results since each successive call to getProgressReport()
	 * is returning a different snapshot.
	 *
	 *
	 * @return
	 */
	public IProgressReport getProgressReport();

	/**
	 * Sets the <code>selection</code> to the progress reporter; this is used when a traditional min, max, selection is specified.
	 * <p><b>NOTE: </b> this selection value also synchronize the <code>percentage</code> value of this reporter</p>
	 * @param selection
	 * @param message
	 */
	public void setSelection(int selection, String message);

	/**
	 * Sets the <code>percentage</code> value to the progress reporter; this is used when a simple percentage is specified as opposed to setting min, max, and selection.
	 * <p><b>NOTE: </b> this percentage value also synchronize the <code>selection</code> value of this reporter</p>
	 * @param percentage an integer from 0-100
	 * @param message a textual message to display; <code>null</code> to leave the previous message untouched, empty String to clear any previous message
	 */
	public void setPercentage(int percentage, String message);

	/**
	 * Set this reporter into indeterminate mode; use this when an accurate ratio of amount of work done vs. total amount of work can not be calculated
	 * @param isIndeterminate
	 */
	public void setIndeterminate(boolean isIndeterminate);

	/**
	 * Indicates that the associated process is done
	 */
	public void setDone();

	/**
	 * @param min the min to set
	 */
	public void setMinimum(int min);

	/**
	 * @param max the max to set
	 */
	public void setMaximum(int max);

	/**
	 * Marks this reporter as canceled and notify any listeners about it
	 * <p><b>NOTE: </b> This is only a hint back to the processes listening to this reporter;
	 * it is up to that process to determine the correct course of action in response to this flag
	 */
	public void cancel();

	/**
	 * Notifies listener that a retry is requested
	 */
	public void retry();

	/**
	 * Sets whether the process associated with this reporter is allowed to be canceled by the user. This serves as a hint
	 * to the progress manager handling this reporter.  If set to <code>true</code> the manager may
	 * enable a UI component allowing the user to cancel the associated process if appropriate.
	 * <P>The holder of this reporter can register a listener to receive the cancel event</p>
	 * @see #addListener(IProgressReporterListener)
	 * @see #removeListener(IProgressReporterListener)
	 *
	 * @param cancelAllowed <code>true</code> to indicate that this process may solicit a <code>REPORT_TYPE_CANCEL</code> input from the user;
	 * default is <code>false</code>
	 */
	public void setCancelAllowed(boolean cancelAllowed);

	public void setCancelCloses(boolean cancelCloses);

	public boolean getCancelCloses();

	/**
	 *
	 * @param listener
	 */
	public void addListener(IProgressReporterListener listener);

	/**
	 *
	 * @param listener
	 */
	public void removeListener(IProgressReporterListener listener);

	/**
	 * @param name a textual name to identify the process this reporter represents; need not be unique (should not be used as a lookup key), and may be <code>null</code>.
	 */
	public void setName(String name);

	/**
	 * Sets the title that may be used by a UI component.
	 * A typical usage would be for the title of a progress dialog
	 * @param title the title to set
	 */
	public void setTitle(String title);

	/**
	 * Sets the image corresponding to this reporter; this image may be used by the UI to decorate this reporter
	 * <p><b>NOTE</b>: Though not strictly required (nor enforced) the image should be 32X32 pixels with transparency.
	 * If not then cropping or enlargement may be applied as required by the particular UI</p>
	 * @param image the image; may be <code>null</code>
	 */
	public void setImage(Image image);

	/**
	 * Sets an error message to this reporter; subsequently <code>isInErrorState</code> will be set to <code>true</code>
	 * @see #reInit()
	 * @param errorMessage
	 */
	public void setErrorMessage(String errorMessage);

	/**
	 * Sets the current status message for this reporter; this is typically used to display a message at each incremental update
	 * @param message a textual message
	 */
	public void setMessage(String message);

	/**
	 * Appends the detail message to this reporter.  This is typically a more verbose message that is optionally displayed by the UI.
	 * This is an optional property so UI components displaying this can decide to show nothing for it (if it's <code>null</code>)or show the regular message in its place
	 *
	 * <p>Additionally this is an appending message so that reporters can send smaller units of the message rather than
	 * having to store and send the entire (and ever increasing) messages for each update</p>
	 * @param detailMessage
	 */
	public void appendDetailMessage(String detailMessage);

	/**
	 * This is a hint to any UI components displaying this reporter
	 * to determine if the user should be prompted to retry on error
	 * @param isRetryAllowed <code>true</code> if the user should be prompted to retry when an error has occurred; <code>false</code> otherwise.  Default is <code>false</code>
	 */
	public void setRetryAllowed(boolean retryOnError);

	/**
	 * An arbitrary object reference that can be used to further identify the reporter.
	 * This object is also accessible to listeners of the reporter through {@link ProgressReporter.ProgressReport#objectData}
	 * so that it can be used to pass information to and from the listeners.
	 * @param objectData the objectData to set
	 */
	public void setObjectData(Object objectData);

	/**
	 * Returns an array of <code>IMessage</code>'s that has been generated since the reporter was created
	 * @return
	 */
	public IMessage[] getMessageHistory();
}