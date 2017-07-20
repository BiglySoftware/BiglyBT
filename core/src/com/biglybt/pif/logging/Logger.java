/*
 * File    : Logger.java
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

import com.biglybt.pif.PluginInterface;

/**
 * Logging utilities class
 *
 * @since 2.0.7.0
 */
public interface Logger {

	/**
	 * Create a normal logging channel.  Multiple calls to this method with the
	 * same name parameter results in different channels.
	 *
	 * @param name Name of LoggerChannel
	 * @return a new LoggerChannel
	 *
	 * @since 2.0.7.0
	 */
	public LoggerChannel getChannel(String name);

	/**
	 * Create a timestamped logging channel. Multiple calls to this method with
	 * the same name parameter results in different channels.
	 *
	 * @param name Name of LoggerChannel
	 * @return a new LoggerChannel
	 *
	 * @since 2.3.0.0
	 */
	public LoggerChannel getTimeStampedChannel(String name);

	/**
	 * Create a logger channel that doesn't output to the standard AZ log.
	 * Add listeners to it if output needs to be routed somewhere.
	 * Multiple calls to this method with the same name parameter results in
	 * different channels
	 *
	 * @param name Name of LoggerChannel
	 * @return a new LoggerChannel
	 *
	 * @since 2.3.0.0
	 */
	public LoggerChannel getNullChannel(String name);

	/**
	 * Retrieve all the channels that have been created for all plugins.
	 *
	 * @return Array of LoggerChannel objects
	 *
	 * @since 2.1.0.0
	 */
	public LoggerChannel[] getChannels();

	/**
	 * Retrieve the PluginInterface
	 *
	 * @return PluginInterface object
	 *
	 * @since 2.3.0.0
	 */
	public PluginInterface getPluginInterface();

	/**
	 * Add LoggerAlertListener for all alerts raised. It might be a
	 * better idea to use {@link #addAlertListener(LogAlertListener)},
	 * as it is more flexible.
	 *
	 * @param listener Listener to add
	 * @see #addAlertListener(LogAlertListener)
	 * @since 2.3.0.6
	 */
	public void addAlertListener(LoggerAlertListener listener);

	/**
	 * Remove previously added AlertListener.
	 *
	 * @param listener LoggerAlertListener to remove
	 * @since 2.3.0.6
	 */
	public void removeAlertListener(LoggerAlertListener listener);

	/**
	 * Add a listener to be informed of any alerts to be displayed to users.
	 *
	 * @since 3.1.1.1
	 */
	public void addAlertListener(LogAlertListener listener);

	/**
	 * Remove a previously added alert listener.
	 *
	 * @since 3.1.1.1
	 */
	public void removeAlertListener(LogAlertListener listener);

	public void addFileLoggingListener(FileLoggerAdapter listener);
	public void removeFileLoggingListener(FileLoggerAdapter listener);
}