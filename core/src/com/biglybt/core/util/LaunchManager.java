/*
 * Created on Mar 1, 2010
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.util;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;

public class
LaunchManager
{
	private static final LaunchManager	singleton = new LaunchManager();

	public static LaunchManager
	getManager()
	{
		return( singleton );
	}

	final CopyOnWriteList<LaunchController>	controllers	= new CopyOnWriteList<>();

	public void
	launchRequest(
		final LaunchTarget	target,
		final LaunchAction	action )
	{
		new AEThread2( "LaunchManager:request" )
		{
			@Override
			public void
			run()
			{
				for ( LaunchController c: controllers ){

					try{
						c.handleRequest( target );

					}catch( Throwable e ){

						action.actionDenied( e );

						return;
					}
				}

				action.actionAllowed();
			}
		}.start();
	}

	public LaunchTarget
	createTarget(
		DownloadManager		dm )
	{
		return( new LaunchTarget( dm ));
	}

	public LaunchTarget
	createTarget(
		DiskManagerFileInfo		fi )
	{
		return( new LaunchTarget( fi ));
	}

	public void
	addController(
		LaunchController	controller )
	{
		controllers.add( controller );
	}

	public void
	removeController(
		LaunchController	controller )
	{
		controllers.remove( controller );
	}

	public static class
	LaunchTarget
	{
		private final DownloadManager			dm;
		private DiskManagerFileInfo		file_info;

		private
		LaunchTarget(
			DownloadManager		_dm )
		{
			dm		= _dm;
		}

		private
		LaunchTarget(
			DiskManagerFileInfo		_file_info )
		{
			file_info	= _file_info;
			dm			= file_info.getDownloadManager();
		}

		public DownloadManager
		getDownload()
		{
			return( dm );
		}

		public DiskManagerFileInfo
		getFile()
		{
			return( file_info );
		}
	}

	public interface
	LaunchController
	{
		public void
		handleRequest(
			LaunchTarget		target )

			throws Throwable;
	}

	public interface
	LaunchAction
	{
		public void
		actionAllowed();

		public void
		actionDenied(
			Throwable			reason );

	}
}
