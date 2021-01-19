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

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.FileUtil;

public interface
CoreOperationTask
{
	public String
	getName();
	
	public DownloadManager
	getDownload();
	
	public default String[]
	getAffectedFileSystems()
	{
		DownloadManager dm = getDownload();
		
		if ( dm != null ){
			
			return( FileUtil.getFileStoreNames( dm.getAbsoluteSaveLocation()));
		}
		
		return( null );
	}
	
	public default void
	run(
		CoreOperation operation )
	{
	}
	
	public ProgressCallback
	getProgressCallback();
	
	public interface
	ProgressCallback
		extends Comparable<ProgressCallback>
	{
		public int ST_NONE		= 0x0000;
		public int ST_PAUSE		= 0x0001;
		public int ST_RESUME	= 0x0002;
		public int ST_CANCEL	= 0x0004;

		public int ST_SUBTASKS	= 0x0008;
		
		public int ST_QUEUED	= 0x0010;	// differentiates between active (ST_NONE) and active but currently not scheduled

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
		
		public long
		getSize();
		
		public void
		setSize(
			long		size );
		
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
		
		public boolean
		isAutoPause();
		
		public void
		setAutoPause(
			boolean		b );
	
		public int
		getOrder();
		
		public void
		setOrder(
			int		order );
		
		public int
		getSupportedTaskStates();
		
		public int
		getTaskState();
		
		public void
		setTaskState(
			int		state );
	}
	
	public static class
	ProgressCallbackAdapter
		implements ProgressCallback
	{
		private volatile int 		thousandths;
		private volatile long		size;
		private volatile String		subtask;
		private volatile boolean	auto_pause;
		private volatile int	 	order;
		
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
		
		public long
		getSize()
		{
			return( size );
		}
		
		public void
		setSize(
			long		_size )
		{
			size	= _size;
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
		
		public boolean
		isAutoPause()
		{
			return( auto_pause );
		}
		
		public void
		setAutoPause(
			boolean		b )
		{
			auto_pause = b;
		}
		
		public int
		getOrder()
		{
			return( order );
		}
		
		public void
		setOrder(
			int		_order )
		{
			order = _order;
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
		
		public int
		getTaskState()
		{
			return( ST_NONE );
		}
		
		@Override
		public int 
		compareTo(
			ProgressCallback o)
		{
			long l = getSize() - o.getSize();
			
			if ( l < 0 ){
				return( -1 );
			}else if ( l > 0 ){
				return( 1 );
			}else{
				return( 0 );
			}
		}
	}
}
