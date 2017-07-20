/*
 * Created on 19-Sep-2006
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

package com.biglybt.core.util;

public class
PausableAverage
	extends Average
{
	public static PausableAverage
	getPausableInstance(
		int 	refreshRate,
		int 	period )
	{
		if ( refreshRate < 100 ){

			return null;
		}

		if (( period * 1000 ) < refreshRate ){

			return null;
		}

		return new PausableAverage(refreshRate, period);
	}

	private long	offset;
	private long	pause_time;

	private
	PausableAverage(
		int _refreshRate,
		int _period )
	{
		super( _refreshRate, _period );
	}

	@Override
	public void
	addValue(
		long	value )
	{
		super.addValue( value );
	}

	@Override
	public long
	getAverage()
	{
		long	average = super.getAverage();

		return( average );
	}

	@Override
	protected long
	getEffectiveTime()
	{
		return( SystemTime.getCurrentTime() - offset );
	}

	public void
	pause()
	{
		if ( pause_time == 0 ){

			pause_time = SystemTime.getCurrentTime();
		}
	}

	public void
	resume()
	{
		if ( pause_time != 0 ){

			long	now = SystemTime.getCurrentTime();

			if ( now > pause_time ){

				offset += now - pause_time;
			}

			pause_time	= 0;
		}
	}
}
