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
		
		public int
		getProgress();
		
		public String
		getSubTaskName();
		
		public int
		getSupportedTaskStates();
		
		public void
		setTaskState(
			int		state );
	}
}
