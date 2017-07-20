/*
 * Created on Jul 3, 2007
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

public interface
SpeedManagerPingMapper
{
	public String
	getName();

	public int[][]
	getHistory();

	public SpeedManagerPingZone[]
	getZones();

	public SpeedManagerLimitEstimate
	getEstimatedUploadLimit(
		boolean		persistent );

	public SpeedManagerLimitEstimate
	getEstimatedDownloadLimit(
		boolean		persistent );

		/**
		 * +1 : good
		 * -1 : bad
		 * >-1 <+1 : relative goodness/badness
		 * @return
		 */

	public double
	getCurrentMetricRating();

	public SpeedManagerLimitEstimate
	getLastBadUploadLimit();

	public SpeedManagerLimitEstimate
	getLastBadDownloadLimit();

	public SpeedManagerLimitEstimate[]
	getBadUploadHistory();

	public SpeedManagerLimitEstimate[]
	getBadDownloadHistory();

	public boolean
	isActive();

	public void
	destroy();
}
