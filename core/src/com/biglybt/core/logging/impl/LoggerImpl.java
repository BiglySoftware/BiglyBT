/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package com.biglybt.core.logging.impl;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.AEDiagnosticsLogger;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;

/**
 * Logging tool
 *
 * @author TuxPaper
 * @since 2.3.0.7
 */

public class LoggerImpl {
	private static final int MAXHISTORY = 256;

	static final boolean bLogToStdOut = System.getProperty(SystemProperties.SYSPROP_LOG_STDOUT) != null;

	boolean bEventLoggingEnabled = false;

	private PrintStream psOldOut = null;

	private PrintStream psOldErr = null;

	private PrintStream psOut;

	private PrintStream psErr;

	private final List logListeners = new ArrayList();

	private AEDiagnosticsLogger alertLogger;

	private final List alertListeners = new ArrayList();

	private final List alertHistory = new ArrayList();

	private boolean logToStdErrAllowed = true;

	/**
	 * Initializes the Logger and sets up a file logger.
	 */
	public LoggerImpl() {
		doRedirects();
	}

	/**
	 * Must be seperate from constructor, because the code may call a Logger.*
	 * method, which requires a loggerImpl to be not null.
	 *
	 */
	public void init() {
		// temporarily set to true, to log any errors between now and setting
		// bEnabled properly.
		bEventLoggingEnabled = true;

		// Shorten from COConfigurationManager To make code more readable
		final ConfigurationManager config = ConfigurationManager.getInstance();

		boolean overrideLog = System.getProperty(SystemProperties.SYSPROP_OVERRIDELOG) != null;
		if (overrideLog) {
			bEventLoggingEnabled = true;
		} else {
			bEventLoggingEnabled = config.getBooleanParameter("Logger.Enabled");

			config.addParameterListener("Logger.Enabled", new ParameterListener() {
				@Override
				public void parameterChanged(String parameterName) {
					bEventLoggingEnabled = config.getBooleanParameter("Logger.Enabled");
				}
			});
		}
	}

	/**
	 * Set up stdout/stderr redirects
	 */
	public void doRedirects() {
		try {
			if (System.out != psOut) {
				if (psOldOut == null)
					psOldOut = System.out;

				psOut = new PrintStream(new RedirectorStream(psOldOut, LogIDs.STDOUT,
						LogEvent.LT_INFORMATION));

				System.setOut(psOut);
			}

			if (System.err != psErr) {
				if (psOldErr == null)
					psOldErr = System.err;

				psErr = new PrintStream(new RedirectorStream(psOldErr, LogIDs.STDERR,
						LogEvent.LT_ERROR));

				System.setErr(psErr);
			}

		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
	}

	public boolean isEnabled() {
		return bEventLoggingEnabled;
	}

	/**
	 * Redirects any incoming text to the logger
	 */
	private class RedirectorStream extends OutputStream {
		protected final PrintStream ps;

		protected final StringBuffer buffer = new StringBuffer(1024);

		protected final LogIDs logID;

		protected final int logType;

		protected RedirectorStream(PrintStream _ps, LogIDs _logID, int _logType) {
			ps = _ps;
			logType = _logType;
			logID = _logID;
		}

		@Override
		public void write(int data) {
			char c = (char) data;

			if (c == '\n') {
				if (!bLogToStdOut) {
					ps.println(buffer);
				}
				log(new LogEvent(logID, logType, buffer.toString()));
				buffer.setLength(0);
			} else if (c != '\r') {
				buffer.append(c);
			}
		}

		@Override
		public void write(byte b[], int off, int len) {
			for (int i = off; i < off + len; i++) {
				int d = b[i];
				if (d < 0)
					d += 256;
				write(d);
			}
		}
	}

	// Log Event Functions
	// ===================

	/**
	 * Log an event
	 *
	 * @param event
	 *            event to log
	 */
	public void log(LogEvent event) {

		/**
		 * This highlights bits of code which log, but don't bother
		 * to check whether logging is enabled or not.
		 */
		//if (!bEventLoggingEnabled) {
		//	new Exception("No logging check done!").printStackTrace(psOldErr);
		//}

		/* ever wondered where a log is coming from? turn on log-to-file and breakpoint here
		if ( event.text.startsWith( "Added Listener")){
			int n=0;
		}
		*/

		if (bLogToStdOut && psOldOut != null)
			psOldOut.println(event.text);

		if (event.entryType == LogEvent.LT_ERROR) {
			if ( AEDiagnostics.isStartupComplete()){
					// more recursive horrors here if we try and log too early
				try{
					Debug.outDiagLoggerOnly("[" + event.logID + "] " + event.text);
				}catch( Throwable e ){
				}
			}
			if (logToStdErrAllowed && psOldErr != null && event.logID != LogIDs.STDERR) {
				psOldErr.println("[" + event.logID + "] " + event.text);
			}
		}
		if (bEventLoggingEnabled)
			for (int i = 0; i < logListeners.size(); i++) {
				try {
					Object listener = logListeners.get(i);
					if (listener instanceof ILogEventListener)
						((ILogEventListener) listener).log(event);
				} catch (Throwable e) {
					if (logToStdErrAllowed && psOldErr != null) {
						psOldErr.println("Error while logging: " + e.getMessage());
						e.printStackTrace(psOldErr);
					}
				}
			}

		// Write error to stderr, which will eventually get back here
		if (event.err != null && event.entryType == LogEvent.LT_ERROR){
			Debug.printStackTrace(event.err);
		}
	}

	public void logTextResource(LogEvent event) {
		event.text = MessageText.getString(event.text);
		log(event);
	}

	public void logTextResource(LogEvent event, String params[]) {
		event.text = MessageText.getString(event.text, params);
		log(event);
	}

	public void addListener(ILogEventListener aListener) {
		logListeners.add(aListener);
	}

	public void removeListener(ILogEventListener aListener) {
		logListeners.remove(aListener);
	}

	// Log Alert Functions
	// ===================

	public void log(LogAlert alert) {
		String logText = "Alert:" + alert.entryType + ":" + alert.text;

		// Log all Alerts as Events
		LogEvent alertEvent = new LogEvent(LogIDs.ALERT, alert.entryType,
				logText);
		alertEvent.err = alert.err;
		Logger.log(alertEvent);

		synchronized (this) {
			if (alertLogger == null) {
				alertLogger = AEDiagnostics.getLogger("alerts");
			}
		}

		Throwable error = alert.getError();

		if ( error != null ){

			logText += " (" + Debug.getNestedExceptionMessageAndStack( error ) + ")";
		}

		alertLogger.log(logText);

		alertHistory.add(alert);

		if (alertHistory.size() > MAXHISTORY)
			alertHistory.remove(0);

		for (int i = 0; i < alertListeners.size(); i++) {
			try {
				Object listener = alertListeners.get(i);
				if (listener instanceof ILogAlertListener)
					((ILogAlertListener) listener).alertRaised(alert);
			} catch (Throwable f) {
				if (psOldErr != null) {
					psOldErr.println("Error while alerting: " + f.getMessage());
					f.printStackTrace(psOldErr);
				}
			}
		}
	}

	public void logTextResource(LogAlert alert) {
		alert.text = MessageText.getString(alert.text);
		log(alert);
	}

	public void logTextResource(LogAlert alert, String params[]) {
		alert.text = MessageText.getString(alert.text, params);
		log(alert);
	}

	public void addListener(ILogAlertListener l) {
		alertListeners.add(l);

		for (int i = 0; i < alertHistory.size(); i++) {
			LogAlert alert = (LogAlert) alertHistory.get(i);
			l.alertRaised(alert);
		}
	}

	public void removeListener(ILogAlertListener l) {
		alertListeners.remove(l);
	}

	public PrintStream getOldStdErr() {
		return psOldErr;
	}

	public void allowLoggingToStdErr(boolean allowed) {
		this.logToStdErrAllowed = allowed;
	}
}
