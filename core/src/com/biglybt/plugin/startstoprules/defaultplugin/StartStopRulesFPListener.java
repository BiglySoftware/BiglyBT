/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.plugin.startstoprules.defaultplugin;

import com.biglybt.pif.download.Download;

/**
 * @author TuxPaper
 * @created Jun 11, 2007
 *
 */
public class StartStopRulesFPListener
{
	/**
	 * This method should return true to force a download to be first priority. You can only use this
	 * listener to force downloads to be first priority - you can't force downloads <b>not</b> to be
	 * first priority - if you return false, then the other first priority settings and logic will be
	 * used to determine its status.
	 *
	 * Listeners will not be called for all downloads - the following checks may prevent listeners
	 * being called:
	 *   - Non persistent downloads
	 *   - STOPPED or ERROR state
	 *   - Incomplete downloads
	 *
	 * This means that listeners don't have to do these basic checks.
	 *
	 * The StringBuffer argument is intended to output debug information about why the item
	 * is (or isn't) first priority. The item may be null if debugging is not enabled. It is
	 * not mandatory to log to the buffer.
	 */
	public boolean isFirstPriority(Download download, int numSeeds, int numPeers, StringBuffer debug) {
		return false;
	}
}
