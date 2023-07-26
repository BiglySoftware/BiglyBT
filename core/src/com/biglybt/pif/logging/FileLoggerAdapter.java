/*
 * Created on Mar 3, 2006 11:39:23 AM
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
package com.biglybt.pif.logging;

/**
 * Listener(s) for FileLogging
 *
 */
public class FileLoggerAdapter {
	/**
	 * Called when we are about to log an event to file.
	 *
	 * @param lineOut line to be written to file (modifiable)
	 * @return true-ok to log to file; false-skip logging to file
	 */
	public boolean logToFile(StringBuffer lineOut) {
		return true;
	}
}
