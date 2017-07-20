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
package com.biglybt.core.logging;

import java.util.Date;

/**
 * Container to hold Log Event information.
 *
 * @note There are no constructors without Log ID as a parameter. This is
 *       intentional, as all log events should have a log id.
 * @author TuxPaper
 */

public class LogEvent {
	// log types
	public static final int LT_INFORMATION = 0;

	public static final int LT_WARNING = 1;

	public static final int LT_ERROR = 3;

	/** Date and Time this event occurred */
	public Date timeStamp = new Date();

	/** A list of events that this entry is related to */
	public final Object[] relatedTo;

	/** Log ID, categorizing the event */
	public final LogIDs logID;

	/** Type of entry, usually one of Event.LT_* constants */
	public final int entryType;

	/** Text of the event */
	public String text;

	/** Error related to event */
	public Throwable err = null;

	public LogEvent(Object[] relatedTo, LogIDs logID, int entryType, String text) {
		this.logID = logID;
		this.entryType = entryType;
		this.text = text;
		this.relatedTo = relatedTo;
	}

	public LogEvent(Object relatedTo, LogIDs logID, int entryType, String text) {
		this(new Object[] { relatedTo }, logID, entryType, text);
	}


	public LogEvent(LogIDs logID, int entryType, String text) {
		this(null, logID, entryType, text);
	}

	public LogEvent(Object[] relatedTo, LogIDs logID, String text) {
		this(relatedTo, logID, LT_INFORMATION, text);
	}

	public LogEvent(Object relatedTo, LogIDs logID, String text) {
		this(new Object[] { relatedTo }, logID, LT_INFORMATION, text);
	}

	public LogEvent(LogIDs logID, String text) {
		this(null, logID, LT_INFORMATION, text);
	}

	// Throwables

	public LogEvent(Object[] relatedTo, LogIDs logID, int entryType, String text, Throwable e) {
		this(relatedTo, logID, entryType, text);
		this.err = e;
	}
	public LogEvent(Object[] relatedTo, LogIDs logID, String text, Throwable e) {
		this(relatedTo, logID, LT_ERROR, text, e);
	}

	public LogEvent(Object relatedTo, LogIDs logID, String text, Throwable e) {
		this(new Object[] { relatedTo }, logID, text, e);
	}

	public LogEvent(LogIDs logID, int entryType, String text, Throwable e) {
		this(null, logID, entryType, text, e);
	}

	public LogEvent(LogIDs logID, String text, Throwable e) {
		this(null, logID, text, e);
	}
}
