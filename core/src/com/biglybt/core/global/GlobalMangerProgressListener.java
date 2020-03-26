/*
 * Created on 27 Jul 2006
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

package com.biglybt.core.global;

public interface
GlobalMangerProgressListener
{
	public void
	reportCurrentTask(
		String currentTask );

	public void
	reportPercent(
		int percent );
	
	public static class
	GlobalMangerProgressAdapter
		implements GlobalMangerProgressListener
	{
		private final GlobalMangerProgressListener		delegate;
		private final int	start_percent;
		private final int	range;
		
		public
		GlobalMangerProgressAdapter()
		{
			delegate		= null;
			start_percent	= -1;
			range			= -1;
		}
		
		public
		GlobalMangerProgressAdapter(
			GlobalMangerProgressListener	_delegate,
			int								_start_percent,
			int								_end_percent )
		{
			delegate		= _delegate;
			start_percent	= _start_percent;
			range			= _end_percent - _start_percent;
		}
		
		public void
		reportCurrentTask(
			String currentTask )
		{
			if ( delegate != null ){
				delegate.reportCurrentTask(currentTask);
			}
		}

		public void
		reportPercent(
			int percent )
		{
			if ( delegate != null ){
				if ( range == -1 ){
					delegate.reportPercent(percent);
				}else{
					delegate.reportPercent( start_percent + (range*percent)/100 );
				}
			}
		}
		
	}
}
