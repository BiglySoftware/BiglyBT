/*
 * File    : LoggerImpl.java
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

package com.biglybt.pifimpl.local.logging;

/**
 * @author parg
 *
 */

import java.util.*;

import com.biglybt.core.logging.ILogAlertListener;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.impl.FileLogging;
import com.biglybt.core.logging.impl.FileLoggingAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.*;

public class
LoggerImpl
	implements Logger
{
	private PluginInterface	pi;

	private List		channels 			 = new ArrayList();
	private Map			alert_listeners_map	 = new HashMap();
	private Map			alert_listeners_map2 = new HashMap();

	public
	LoggerImpl(
		PluginInterface	_pi )
	{
		pi	= _pi;
	}

	@Override
	public PluginInterface
	getPluginInterface()
	{
		return( pi );
	}

	@Override
	public LoggerChannel
	getChannel(
		String		name )
	{
		LoggerChannel	channel = new LoggerChannelImpl( this, name, false, false );

		channels.add( channel );

		return( channel );
	}

	@Override
	public LoggerChannel
	getTimeStampedChannel(
		String		name )
	{
		LoggerChannel	channel = new LoggerChannelImpl( this, name, true, false );

		channels.add( channel );

		return( channel );
	}

	@Override
	public LoggerChannel
	getNullChannel(
		String		name )
	{
		LoggerChannel	channel = new LoggerChannelImpl( this, name, true, true );

		channels.add( channel );

		return( channel );
	}

	@Override
	public LoggerChannel[]
	getChannels()
	{
		LoggerChannel[]	res = new LoggerChannel[channels.size()];

		channels.toArray( res );

		return( res );
	}

	@Override
	public void
	addAlertListener(
		final LoggerAlertListener		listener )
	{

		ILogAlertListener lg_listener = new ILogAlertListener() {
			@Override
			public void alertRaised(LogAlert alert) {
				if (alert.err == null) {
					int type;

					if (alert.entryType == LogAlert.AT_INFORMATION) {
						type = LoggerChannel.LT_INFORMATION;
					} else if (alert.entryType == LogAlert.AT_WARNING) {
						type = LoggerChannel.LT_WARNING;
					} else {
						type = LoggerChannel.LT_ERROR;
					}

					listener.alertLogged(type, alert.text, alert.repeatable);

				} else
					listener.alertLogged(alert.text, alert.err, alert.repeatable);
			}

		};

		alert_listeners_map.put( listener, lg_listener );

		com.biglybt.core.logging.Logger.addListener( lg_listener );
	}

	@Override
	public void
	removeAlertListener(
		LoggerAlertListener		listener )
	{
		ILogAlertListener	lg_listener = (ILogAlertListener)alert_listeners_map.remove( listener );

		if ( lg_listener != null ){

			com.biglybt.core.logging.Logger.removeListener( lg_listener );
		}
	}

	@Override
	public void addAlertListener(final LogAlertListener listener) {
		ILogAlertListener lg_listener = new ILogAlertListener() {
			private HashSet set = new HashSet();
			@Override
			public void alertRaised(LogAlert alert) {
				if (!alert.repeatable) {
					if (set.contains(alert.text)) {return;}
					set.add(alert.text);
				}
				listener.alertRaised(alert);
			}
		};
		alert_listeners_map2.put(listener, lg_listener);
		com.biglybt.core.logging.Logger.addListener(lg_listener);
	}

	@Override
	public void removeAlertListener(LogAlertListener listener) {
		ILogAlertListener lg_listener = (ILogAlertListener)alert_listeners_map2.remove(listener);
		if (lg_listener != null){
			com.biglybt.core.logging.Logger.removeListener(lg_listener);
		}
	}

	@Override
	public void addFileLoggingListener(final FileLoggerAdapter listener) {
		FileLogging fileLogging = com.biglybt.core.logging.Logger.getFileLoggingInstance();
		if (fileLogging == null)
			return;

		fileLogging.addListener(new PluginFileLoggerAdapater(fileLogging, listener));
	}

	@Override
	public void removeFileLoggingListener(FileLoggerAdapter listener) {
		FileLogging fileLogging = com.biglybt.core.logging.Logger.getFileLoggingInstance();
		if (fileLogging == null)
			return;

		// find listener and remove
		Object[] listeners = fileLogging.getListeners().toArray();
		for (int i = 0; i < listeners.length; i++) {
			if (listeners[i] instanceof PluginFileLoggerAdapater) {
				PluginFileLoggerAdapater l = (PluginFileLoggerAdapater) listeners[i];
				if (l.listener == listener) {
					fileLogging.removeListener(l);
				}
			}
		}
	}

	private static class PluginFileLoggerAdapater extends FileLoggingAdapter {
		public FileLoggerAdapter listener;

		public PluginFileLoggerAdapater(FileLogging fileLogging, FileLoggerAdapter listener) {
			fileLogging.addListener(this);
			this.listener = listener;
		}

		@Override
		public boolean logToFile(LogEvent event, StringBuffer lineOut) {
			return listener.logToFile(lineOut);
		}
	}
}
