/*
 * Created on Feb 5, 2009
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


package com.biglybt.core.devices.impl;

import java.util.Map;

import com.biglybt.core.devices.TranscodeException;
import com.biglybt.core.devices.TranscodeProfile;
import com.biglybt.core.devices.TranscodeProvider;

public class
TranscodeProfileImpl
	implements TranscodeProfile
{
	private TranscodeManagerImpl	manager;
	private int						pid;
	private String					uid;
	private String 					name;
	private Map<String,Object>		properties;

	protected
	TranscodeProfileImpl(
		TranscodeManagerImpl	_manager,
		int						_provider_id,
		String					_uid,
		String					_name,
		Map<String,Object>		_properties )
	{
		manager		= _manager;
		pid			= _provider_id;
		uid			= _uid;
		name		= _name;
		properties	= _properties;
	}

	@Override
	public String
	getUID()
	{
		return( uid );
	}

	@Override
	public String
	getName()
	{
		String displayName = (String) properties.get("display-name");
		return( displayName == null ? name : displayName );
	}

	@Override
	public TranscodeProvider
	getProvider()

		throws TranscodeException
	{
		return( manager.getProvider( pid ));
	}

	@Override
	public boolean
	isStreamable()
	{
		String	res = (String)properties.get( "streamable" );

		return( res != null && res.equalsIgnoreCase( "yes" ));
	}

	@Override
	public String
	getFileExtension()
	{
		return((String)properties.get( "file-ext" ));
	}

	@Override
	public String
	getDeviceClassification()
	{
		return((String)properties.get( "device" ));
	}

	@Override
	public String
	getDescription()
	{
		String	res = (String)properties.get( "desc" );

		return( res == null?"":res );
	}

	@Override
	public String
	getIconURL()
	{
		return((String)properties.get( "icon-url" ));
	}

	@Override
	public int
	getIconIndex()
	{
		Object o = properties.get( "icon-index" );

		if ( o instanceof Number ){

			return(((Number)o).intValue());
		}

		return( 0 );
	}
}
