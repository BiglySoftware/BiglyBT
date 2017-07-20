/*
 * Created on Jul 5, 2007
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
SpeedManagerLimitEstimate
{
	public static final float TYPE_UNKNOWN = -0.1f;
	public static final float TYPE_ESTIMATED =  0.0f;
	public static final float TYPE_CHOKE_ESTIMATED =  0.5f;
	public static final float TYPE_MEASURED_MIN =  0.8f;
	public static final float TYPE_MEASURED =  0.9f;
	public static final float TYPE_MANUAL =  1.0f;

	public int
	getBytesPerSec();

		/**
		 * One of the above constants
		 * @return
		 */

	public float
	getEstimateType();

		/**
		 * For estimated limits:
		 * -1 = estimate derived from bad metrics
		 * +1 = estimate derived from good metric
		 * <1 x > -1 = relative goodness of metric
		 * @return
		 */

	public float
	getMetricRating();

	public int[][]
	getSegments();

	public long
	getWhen();

	public String
	getString();
}
