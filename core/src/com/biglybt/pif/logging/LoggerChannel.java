/*
 * File    : LoggerChannel.java
 * Created : 28-Dec-2003
 * By      : parg
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

package com.biglybt.pif.logging;

import java.io.File;

/**
 * Manipulation of a subsection (a channel) of the client's logging.
 *
 * A logger channel is created or retrieve via {@link Logger}.  Typically,
 * a plugin has it's own channel which it can manipulate.  All channels
 * are part of the client logging as a whole, meaning anything logged here will
 * also be fed to any functionality that operates on logging data (with
 * the exception of {@link Logger#getNullChannel(String)}).
 *
 * @since 2.0.7.0
 */
public interface LoggerChannel {
	/** Information Log Type */
	public static final int LT_INFORMATION = 1;

	/** Warning Log Type */
	public static final int LT_WARNING = 2;

	/** Error Log Type */
	public static final int LT_ERROR = 3;

	/**
	 * Returns the name of the Logger Channel
	 *
	 * @return Logger channel name
	 * @since 2.0.7.0
	 */
	public String getName();

	/**
	 * Indicates whether or not logging is enabled - use to optimise calls to
	 * the log methods that require resources to construct the message to be
	 * logged.
	 *
	 * Note that this doesn't apply to alerts - these will always be handled
	 *
	 * @return Enabled state of logging
	 *
	 * @since 2.3.0.2
	 */
	public boolean isEnabled();

	/**
	 * This causes the channel to also write to logs/<i>name</i> files in a cyclic
	 * fashion (c.f. the debug_1/2._log files)
	 *
	 * @since 2.4.0.2
	 */
	public void setDiagnostic();

	public void setDiagnostic( long max_file_size, boolean timestamp );

	/**
	 * logging to file is disabled by default in non-beta builds. This forces writing to file
	 * regardless
	 * @param force_to_file
	 * @since 4401
	 */
	public void setForce( boolean force_to_file );

	/**
	 * @since 4401
	 * @return
	 */

	public boolean getForce();

	/**
	 * Log a message of a specific type to this channel's logger
	 *
	 * @param log_type LT_* constant
	 * @param data text to log
	 *
	 * @since 2.0.7.0
	 */
	public void log(int log_type, String data);

	/**
	 * log text with implicit type {@link #LT_INFORMATION}
	 *
	 * @param data text to log
	 *
	 * @since 2.1.0.0
	 */
	public void log(String data);

	/**
	 * log an error with implicit type of {@link #LT_ERROR}
	 *
	 * @param error Throwable object to log
	 *
	 * @since 2.0.7.0
	 */
	public void log(Throwable error);

	/**
	 * log an error with implicit type of {@link #LT_ERROR}
	 *
	 * @param data text to log
	 * @param error Throwable object to log
	 *
	 * @since 2.0.7.0
	 */
	public void log(String data, Throwable error);

	/**
	 * Log a string against a list of objects
	 *
	 * @param relatedTo a list of what this log is related to (ex. Peer, Torrent,
	 *                   Download, Object)
	 * @param log_type LT_* constant
	 * @param data text to log
	 *
	 * @since 2.3.0.7
	 */
	public void log(Object[] relatedTo, int log_type, String data);

	/**
	 * Log an error against an object.
	 *
	 * @param relatedTo What this log is related to (ex. Peer, Torrent,
	 *         Download, Object, etc)
	 * @param log_type LT_* constant
	 * @param data text to log
	 *
	 * @since 2.3.0.7
	 */
	public void log(Object relatedTo, int log_type, String data);

	/**
	 * Log an error against an object.
	 *
	 * @param relatedTo What this log is related to (ex. Peer, Torrent,
	 *         Download, Object, etc)
	 * @param data text to log
	 * @param error Error that will be appended to the log entry
	 *
	 * @since 2.3.0.7
	 */

	public void log(Object relatedTo, String data, Throwable error);

	/**
	 * Log an error against a list of objects
	 *
	 * @param relatedTo a list of what this log is related to (ex. Peer, Torrent,
	 *                   Download, Object)
	 * @param data text to log
	 * @param error Error that will be appended to the log entry
	 *
	 * @since 2.3.0.7
	 */
	public void log(Object[] relatedTo, String data, Throwable error);

	/**
	 * Log an error against a list of objects with implicit type {@link #LT_INFORMATION}
	 *
	 * @param relatedTo a list of what this log is related to (ex. Peer, Torrent,
	 *                   Download, Object)
	 * @param data text to log
	 * @since 2.5.0.1
	 */
	public void log(Object[] relatedTo, String data);

	/**
	 * Log an error against an object with implicit type {@link #LT_INFORMATION}
	 *
	 * @param relatedTo What this log is related to (ex. Peer, Torrent,
	 *         Download, Object, etc)
	 * @param data text to log
	 *
	 * @since 2.5.0.1
	 */
	public void log(Object relatedTo, String data);

	/**
	 * raise an alert to the user, if UI present
	 * Note that messages shown to the user are filtered on unique message content
	 * So if you raise an identical alert the second + subsequent messages will not be
	 * shown. Thus, if you want "identical" messages to be shown, prefix them with something
	 * unique like a timestamp.
	 *
	 * @param alert_type LT_* constant
	 * @param message text to alert user with
	 *
	 * @since 2.0.8.0
	 */
	public void logAlert(int alert_type, String message);

	/**
	 * Alert the user of an error
	 *
	 * @param message text to alert user with
	 * @param e Error that will be attached to the alert
	 *
	 * @since 2.1.0.2
	 */
	public void logAlert(String message, Throwable e);

	/**
	 * Raise an alert to the user, if UI present. Subsequent, identical messages
	 * will always generate an alert (i.e. duplicates won't be filtered)
	 *
	 * @param alert_type LT_* constant
	 * @param message text to alert user with
	 *
	 * @since 2.1.0.2
	 */
	public void logAlertRepeatable(int alert_type, String message);

	/**
	 * Raise an alert to the user, if UI present. Subsequent, identical messages
	 * will always generate an alert (i.e. duplicates won't be filtered)
	 *
	 * @param message text to alert user with
	 * @param e Error that will be attached to the alert
	 *
	 * @since 2.1.0.2
	 */
	public void logAlertRepeatable(String message, Throwable e);

	/**
	 * Add a LoggerChannelListener to this LoggerChannel
	 *
	 * @param l Listener to add
	 *
	 * @since 2.0.8.0
	 */
	public void addListener(LoggerChannelListener l);

	/**
	 * Remove a reviously added LoggerChannelListener
	 *
	 * @param l Listener to remove.
	 *
	 * @since 2.0.8.0
	 */
	public void removeListener(LoggerChannelListener l);

	/**
	 * Retrieve the parent Logger object for this LoggerChannel.
	 *
	 * @return Logger object
	 *
	 * @since 2.3.0.0
	 */
	public Logger getLogger();
	
	/**
	 * retrieves the current file associated with the channel, null if none
	 * @return
	 */
	
	public File
	getCurrentFile( boolean flush );
}
