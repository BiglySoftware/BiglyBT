/*
 * Created on Feb 28, 2006 2:41:30 PM
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
 */
package com.biglybt.core.logging.impl;

import com.biglybt.core.logging.LogEvent;

/**
 * Listeners for FileLogging
 *
 * @author TuxPaper
 * @created Feb 28, 2006
 *
 */
public class FileLoggingAdapter {
	/**
	 * Called when we are about to log an event to file.
	 *
	 * @param event Event being logged to file
	 * @param lineOut line to be written to file (modifiable)
	 * @return true-ok to log to file; false-skip logging to file
	 */
	public boolean logToFile(LogEvent event, StringBuffer lineOut) {
		return true;
	}
}
