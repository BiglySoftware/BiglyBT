/*
 * Created on 19 Jul 2006
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

package com.biglybt.core.disk.impl.access.impl;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerRequest;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.SystemTime;

public abstract class
DiskManagerRequestImpl
	implements DiskManagerRequest
{
	private static final LogIDs LOGID = LogIDs.DISK;

	static boolean	DEBUG;
	private static int		next_id;

	static{
		COConfigurationManager.addAndFireParameterListener(
			"diskmanager.request.debug.enable",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					DEBUG = COConfigurationManager.getBooleanParameter( name, false );
				}
			});
	}

	private long	start_time;
	private String	name;

	private boolean	error_is_fatal = true;
	
	protected abstract String
	getName();

	@Override
	public void
	requestStarts()
	{
		if ( DEBUG ){

			try{
				int	id;

				synchronized( DiskManagerRequestImpl.class ){

					id = next_id++;
				}

				name	= getName() + " [" + id + "]";

				start_time = SystemTime.getCurrentTime();

				Logger.log(new LogEvent( LOGID, "DMRequest start: " + name ));

			}catch( Throwable e ){
			}
		}
	}

	@Override
	public void
	requestEnds(
		boolean	ok )
	{
		if ( DEBUG ){

			try{
				Logger.log(new LogEvent( LOGID, "DMRequest end: " + name + ",ok=" + ok + ", time=" + ( SystemTime.getCurrentTime() - start_time )));

			}catch( Throwable e ){
			}
		}
	}
	
	@Override
	public boolean 
	getErrorIsFatal()
	{
		return( error_is_fatal );
	}
	
	public void 
	setErrorIsFatal(
		boolean b )
	{
		error_is_fatal = b;
	};
}
