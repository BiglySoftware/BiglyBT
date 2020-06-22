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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.logging.*;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.SystemProperties;

/**
 * Log events to a file.
 *
 * @author TuxPaper
 */
// TODO: Filter
public class FileLogging implements ILogEventListener {
	public static final String LOG_FILE_NAME = "biglybt.log";

	public static final String BAK_FILE_NAME = "biglybt.log.bak";

	public static final LogIDs[] configurableLOGIDs = {LogIDs.STDOUT, LogIDs.ALERT, LogIDs.CORE,
			LogIDs.DISK, LogIDs.GUI, LogIDs.NET, LogIDs.NWMAN, LogIDs.PEER,
			LogIDs.PLUGIN, LogIDs.TRACKER, LogIDs.CACHE, LogIDs.PIECES };

	private static final String CFG_ENABLELOGTOFILE = "Logging Enable";

	private static boolean closing;
	private static volatile boolean closing_taking_too_long;

	protected static void
	setClosing()
	{
		synchronized( Logger.class ) {
		
			closing	= true;
		}
	}
	
	public void
	setClosingTakingTooLong()
	{
		logToFile( "Closedown is taking too long, disabling file logging\n" );
		
		closing_taking_too_long	= true;
	}
	
	private boolean bLogToFile = false;
	private boolean bLogToFileErrorPrinted = false;

	private String sLogDir = "";

	private int iLogFileMaxMB = 1;

	// List of components we don't log.
	// Array represents LogTypes (info, warning, error)
	private final ArrayList[] ignoredComponents = new ArrayList[3];

	private final ArrayList listeners = new ArrayList();

	public void initialize() {
		// Shorten from COConfigurationManager To make code more readable
		final ConfigurationManager config = ConfigurationManager.getInstance();
		boolean overrideLog = System.getProperty(SystemProperties.SYSPROP_OVERRIDELOG) != null;

		for (int i = 0; i < ignoredComponents.length; i++) {
			ignoredComponents[i] = new ArrayList();
		}

		if (!overrideLog) {
			config.addListener(new COConfigurationListener() {
				@Override
				public void configurationSaved() {
					checkLoggingConfig();
				}
			});
		}

		checkLoggingConfig();
		config.addParameterListener(CFG_ENABLELOGTOFILE, new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				FileLogging.this.reloadLogToFileParam();
			}
		});
	}

	/**
	 *
	 */
	protected void reloadLogToFileParam() {
		final ConfigurationManager config = ConfigurationManager.getInstance();
		boolean bNewLogToFile = System.getProperty(SystemProperties.SYSPROP_OVERRIDELOG) != null || config.getBooleanParameter(CFG_ENABLELOGTOFILE);
		if (bNewLogToFile != bLogToFile) {
			bLogToFile = bNewLogToFile;
			if (bLogToFile)
				Logger.addListener(this);
			else{
				Logger.removeListener(this);

				synchronized( Logger.class ){

						// close existing file

					checkAndSwapLog();
				}
			}
		}
	}

	void checkLoggingConfig() {
		try {
			// Shorten from COConfigurationManager To make code more readable
			final ConfigurationManager config = ConfigurationManager.getInstance();

			String timeStampFormat;

			boolean overrideLog = System.getProperty(SystemProperties.SYSPROP_OVERRIDELOG) != null;
			if (overrideLog) {

				// Don't set this - reloadLogToFileParam will do it.
				//bLogToFile = true;
				sLogDir = System.getProperty(SystemProperties.SYSPROP_OVERRIDELOGDIR, ".");
				iLogFileMaxMB = 2;
				timeStampFormat = "HH:mm:ss.SSS ";

				for (int i = 0; i < ignoredComponents.length; i++) {
					ignoredComponents[i].clear();
				}

				reloadLogToFileParam();
			} else {
				reloadLogToFileParam();

				sLogDir = config.getStringParameter("Logging Dir", "");

				iLogFileMaxMB = config.getIntParameter("Logging Max Size");

				timeStampFormat = config.getStringParameter("Logging Timestamp")+" ";

				for (int i = 0; i < ignoredComponents.length; i++) {
					ignoredComponents[i].clear();
					int logType = indexToLogType(i);
					for (int j = 0; j < configurableLOGIDs.length; j++) {
						if (!config.getBooleanParameter("bLog." + logType + "."
								+ configurableLOGIDs[j]))
							ignoredComponents[i].add(configurableLOGIDs[j]);
					}
				}
			}

			synchronized (Logger.class) {
				// Create the date format first *before* we do checkAndSwapLog,
				// just in case we end up invoking logToFile...
				format = new SimpleDateFormat(timeStampFormat);
				checkAndSwapLog();
			}


		} catch (Throwable t) {
			Debug.printStackTrace(t);
		}
	}

	private void logToFile(String str) {
		if (!bLogToFile || closing_taking_too_long )
			return;

		String dateStr = format.format(new Date());

		synchronized (Logger.class) {

			// exception handling is done by FileWriter
			if(logFilePrinter != null)
			{
				logFilePrinter.print(dateStr);
				logFilePrinter.print(str);
				logFilePrinter.flush();
				
				if ( closing ){
					
					try{
						logFileOS.getFD().sync();
						
					}catch( Throwable e ){
					}
				}
			}

			checkAndSwapLog();
		} // sync
	}

	private SimpleDateFormat format;
	private FileOutputStream logFileOS;
	private PrintWriter logFilePrinter;

	private void checkAndSwapLog()
	{
		if (!bLogToFile)
		{
			if(logFilePrinter != null)
			{
				logFilePrinter.close();
				logFilePrinter = null;
			}
			return;
		}


		long lMaxBytes = (iLogFileMaxMB * 1024L * 1024L) / 2;
		File logFile = FileUtil.newFile(sLogDir, LOG_FILE_NAME);

		if (logFile.length() > lMaxBytes && logFilePrinter != null)
		{
			File back_name = FileUtil.newFile(sLogDir, BAK_FILE_NAME);
			logFilePrinter.close();
			logFilePrinter = null;

			if ((!back_name.exists()) || back_name.delete()){
				if (!logFile.renameTo(back_name)){
						// rename failed, just have to trash the existing one
					logFile.delete();
				}
			}else{
					// failed to delete existing backup, just have to trash existing log
				logFile.delete();
			}
		}

		if(logFilePrinter == null)
		{
			try
			{
				logFileOS = FileUtil.newFileOutputStream( logFile, true );
				if (logFile.length() == 0) {
					// UTF-8 BOM
					logFileOS.write(new byte[] { (byte) 239, (byte) 187, (byte) 191 });
				}

				OutputStreamWriter osw = new OutputStreamWriter( logFileOS, "UTF-8" );
				
				logFilePrinter = new PrintWriter( osw );
			} catch (IOException e)
			{
				if (!bLogToFileErrorPrinted) {

					// don't just log errors, as it would cause infinite recursion
					bLogToFileErrorPrinted = true;
					Debug.out("Unable to write to log file: " + logFile);
					Debug.printStackTrace(e);

					/*
					java.io.PrintStream stderr = Logger.getOldStdErr();
					stderr.println("Unable to write to log file: " + logFile);
					e.printStackTrace(stderr);
					*/
				}

			}
		}
	}

	private int logTypeToIndex(int entryType) {
		switch (entryType) {
			case LogEvent.LT_INFORMATION:
				return 0;
			case LogEvent.LT_WARNING:
				return 1;
			case LogEvent.LT_ERROR:
				return 2;
		}
		return 0;
	}

	private int indexToLogType(int index) {
		switch (index) {
			case 0:
				return LogEvent.LT_INFORMATION;
			case 1:
				return LogEvent.LT_WARNING;
			case 2:
				return LogEvent.LT_ERROR;
		}
		return LogEvent.LT_INFORMATION;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.core.logging.ILoggerListener2#log(com.biglybt.core.logging.LogEvent)
	 */

	private final static int DEFPADDING = 100;
	private int lastWidth = DEFPADDING;
	@Override
	public void log(LogEvent event) {
		if (ignoredComponents[logTypeToIndex(event.entryType)]
				.contains(event.logID))
			return;

		StringBuffer text = new StringBuffer(event.text.length());

		text.append(event.entryType).append(" ");

		padAndAppend(text, event.logID.toString(), 8, 1);

		//text.append("|");

		if (event.relatedTo != null) {
			lastWidth = padAndAppend(text, event.text, lastWidth, 1);
			if (lastWidth > 200)
				lastWidth = 200;

			for (int i = 0; i < event.relatedTo.length; i++) {
				Object obj = event.relatedTo[i];

				if (obj == null)
					continue;

				if (i > 0)
					text.append("; ");

				if (obj instanceof LogRelation) {
					text.append(((LogRelation) obj).getRelationText());
				} else {
					text.append("RelatedTo[")
					    .append(obj.toString())
					    .append("]");
				}
			}
		} else {
			text.append(event.text);

			lastWidth = DEFPADDING;
		}

		//text.append(event.text);

		if (!event.text.endsWith("\n"))
			text.append("\r\n");

		boolean okToLog = true;
		for (Iterator iter = listeners.iterator(); iter.hasNext() && okToLog;) {
			FileLoggingAdapter listener = (FileLoggingAdapter) iter.next();
			okToLog = listener.logToFile(event, text);
		}

		logToFile(text.toString());
	}

	private int padAndAppend(StringBuffer appendTo, String s, int width, int growBy) {
		if (s == null)
			s = "null";
		appendTo.append(s);

		int sLen = s.length();
		int len = width - sLen;
		while (len <= 0)
			len += growBy;

		char[] padding = new char[len];
		if (len > 5) {
			for (int i = 0; i < len; i += 2)
				padding[i] = ' ';
			for (int i = 1; i < len; i += 2)
				padding[i] = '.';
		} else {
			for (int i = 0; i < len; i++)
				padding[i] = ' ';
		}

		appendTo.append(padding);

		return len + sLen;
	}

	public void addListener(FileLoggingAdapter listener) {
		if (!listeners.contains(listener))
			listeners.add(listener);
	}

	public void removeListener(FileLoggingAdapter listener) {
		listeners.remove(listener);
	}

	public List getListeners() {
		return listeners;
	}
}
