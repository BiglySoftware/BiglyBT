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

package com.biglybt.core;

public interface
CoreOperationTask
{
	public String
	getName();
	
	public void
	run(
		CoreOperation operation );
	
	public ProgressCallback
	getProgressCallback();
	
	public interface
	ProgressCallback
	{
		public int ST_NONE		= 0x0000;
		public int ST_PAUSE		= 0x0001;
		public int ST_RESUME	= 0x0002;
		public int ST_CANCEL	= 0x0004;
		
		public int ST_SUBTASKS	= 0x0008;
		
		public int ST_BUTTONS	= ST_PAUSE | ST_RESUME | ST_CANCEL;
		
		public int STYLE_NONE		= 0x0000;
		public int STYLE_NO_CLOSE	= 0x0001;
		public int STYLE_MODAL		= 0x0002;
		
		public default int
		getStyle()
		{
			return( STYLE_NONE );
		}
		
		public int
		getProgress();
		
		public default void
		setProgress(
			int		thousandths )
		{
		}
		
		public String
		getSubTaskName();
		
		public default void
		setSubTaskName(
			String	name )
		{
		}
		
		public default int
		getDelay()
		{
			return( 0 );
		}
		
		public int
		getSupportedTaskStates();
		
		public void
		setTaskState(
			int		state );
	}
	
	public static class
	ProgressCallbackAdapter
		implements ProgressCallback
	{
		private int 	thousandths;
		private String	subtask;
		
		public int
		getProgress()
		{
			return( thousandths );
		}
		
		public void
		setProgress(
			int		_thousandths )
		{
			thousandths = _thousandths;
		}
				
		public String
		getSubTaskName()
		{
			return( subtask );
		}
			
		public void
		setSubTaskName(
			String	name )
		{
			subtask = name;
		}
		
		public int
		getSupportedTaskStates()
		{
			return( ST_NONE );
		}
		
		public void
		setTaskState(
			int		state )
		{
		}
	}
}
