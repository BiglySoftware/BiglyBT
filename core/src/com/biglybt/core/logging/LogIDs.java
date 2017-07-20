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

/** Enumeration of Log IDs (Component IDs) used in Logger
 *
 * @author TuxPaper
 *
 * @note Idea from http://java.sun.com/developer/Books/shiftintojava/page1.html#replaceenums
 */
public class LogIDs implements Comparable {

	private final String name;

	// Ordinal of next suit to be created
	private static int nextOrdinal = 0;

	// Assign an ordinal to this suit
	private final int ordinal = nextOrdinal++;

	private LogIDs(String name) {
		this.name = name;
	}

	public String toString() {
		return this.name;
	}

	@Override
	public int compareTo(Object o) {
		return ordinal - ((LogIDs) o).ordinal;
	}

	// LogIDs. Prefix would be redundant, since this class is the prefix

	public final static LogIDs LOGGER = new LogIDs("logger");

	public final static LogIDs NWMAN = new LogIDs("nwman");

	public final static LogIDs NET = new LogIDs("net");

	public final static LogIDs PEER = new LogIDs("peer");

	public final static LogIDs CORE = new LogIDs("core");

	public final static LogIDs DISK = new LogIDs("disk");

	public final static LogIDs PLUGIN = new LogIDs("plug");

	public final static LogIDs TRACKER = new LogIDs("tracker");

	public final static LogIDs GUI = new LogIDs("GUI");

	public final static LogIDs STDOUT = new LogIDs("stdout");

	public final static LogIDs STDERR = new LogIDs("stderr");

	public final static LogIDs ALERT = new LogIDs("alert");

	public final static LogIDs CACHE = new LogIDs("cache");

	public final static LogIDs PIECES = new LogIDs("pieces");

	public final static LogIDs UI3 = new LogIDs("UIv3");
}
