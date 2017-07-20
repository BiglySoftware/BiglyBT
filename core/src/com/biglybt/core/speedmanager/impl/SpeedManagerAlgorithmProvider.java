/*
 * Created on May 7, 2007
 * Created by Paul Gardner
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


package com.biglybt.core.speedmanager.impl;

import com.biglybt.core.speedmanager.SpeedManagerPingSource;

public interface
SpeedManagerAlgorithmProvider
{
	public static final int	UPDATE_PERIOD_MILLIS = 1000;

		/**
		 * Reset any state to start of day values
		 */

	public void
	reset();

		/**
		 * Called periodically (see period above) to allow stats to be updated.
		 *
		 */

	public void
	updateStats();

		/**
		 * Called when a new source of ping times has been found
		 * @param source
		 * @param is_replacement	One of the initial sources or a replacement for a failed one
		 */

	public void
	pingSourceFound(
		SpeedManagerPingSource		source,
		boolean						is_replacement );

		/**
		 * Ping source has failed
		 * @param source
		 */

	public void
	pingSourceFailed(
		SpeedManagerPingSource		source );

		/**
		 * Called whenever a new set of ping values is available for processing
		 * @param sources
		 */

	public void
	calculate(
		SpeedManagerPingSource[]	sources );


		/**
		 * Various getters for interesting info shown in stats view
		 * @return
		 */

	public int
	getIdlePingMillis();

	public int
	getCurrentPingMillis();

	public int
	getMaxPingMillis();

		/**
		 * Returns the current view of when choking occurs
		 * @return speed in bytes/sec
		 */

	public int
	getCurrentChokeSpeed();

	public int
	getMaxUploadSpeed();

		/**
		 * Indicates whether or not the provider is adjusting download as well as upload limits
		 * @return
		 */

	public boolean
	getAdjustsDownloadLimits();


	public void
	destroy();
}
