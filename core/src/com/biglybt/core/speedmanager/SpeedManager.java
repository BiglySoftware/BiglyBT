/*
 * Created on 16-Mar-2006
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

package com.biglybt.core.speedmanager;

import com.biglybt.core.dht.speed.DHTSpeedTester;

public interface
SpeedManager
{
	public boolean
	isAvailable();

	public void
	setEnabled(
		boolean		enabled );

	public boolean
	isEnabled();

	public String
	getASN();

	public SpeedManagerLimitEstimate
	getEstimatedUploadCapacityBytesPerSec();

		/**
		 *
		 * @param bytes_per_sec
		 * @param rating see constants above for help
		 */

	public void
	setEstimatedUploadCapacityBytesPerSec(
		int		bytes_per_sec,
		float	estimate_type );

	public SpeedManagerLimitEstimate
	getEstimatedDownloadCapacityBytesPerSec();

	public void
	setEstimatedDownloadCapacityBytesPerSec(
		int		bytes_per_sec,
		float	estimate_type );

	public void
	setSpeedTester(
		DHTSpeedTester	tester );

	public DHTSpeedTester
	getSpeedTester();

	public SpeedManagerPingSource[]
	getPingSources();

	public SpeedManagerPingMapper
	getActiveMapper();

	public SpeedManagerPingMapper[]
	getMappers();

	public void
	reset();

	public void
	addListener(
		SpeedManagerListener		l );

	public void
	removeListener(
		SpeedManagerListener		l );
}
