/*
 * Created on 16-Jun-2004
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

package com.biglybt.plugin.upnp;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.internat.MessageText;

public class
UPnPMapping
{
	public static final int	PT_DEFAULT			= 1;
	public static final int	PT_PERSISTENT		= 2;
	public static final int	PT_TRANSIENT		= 3;

	protected String		resource_name;
	protected boolean		tcp;
	protected int			port;
	protected boolean		enabled;

	protected int			persistent	= PT_DEFAULT;

	protected List			listeners	= new ArrayList();

	protected
	UPnPMapping(
		String		_resource_name,
		boolean		_tcp,
		int			_port,
		boolean		_enabled )
	{
		resource_name	= _resource_name;
		tcp				= _tcp;
		port			= _port;
		enabled			= _enabled;
	}

	public void
	setPersistent(
		int	_persistent )
	{
		persistent		= _persistent;
	}

	public int
	getPersistent()
	{
		return( persistent );
	}

	public boolean
	isTCP()
	{
		return( tcp );
	}

	public int
	getPort()
	{
		return( port );
	}

	public void
	setPort(
		int		_port )
	{
		if ( port != _port ){

			port	= _port;

			changed();
		}
	}

	public boolean
	isEnabled()
	{
		return( enabled );
	}

	public void
	setEnabled(
		boolean	_enabled )
	{
		if ( enabled != _enabled ){

			enabled	= _enabled;

			changed();
		}
	}

	public String
	getString()
	{
		return( getString(getPort()));
	}

	public String
	getString(
		int		port )
	{
		String	name;

		if ( MessageText.keyExists( resource_name )){

			name = MessageText.getString( resource_name );

		}else{

			name = resource_name;
		}

		return( name + " (" + (isTCP()?"TCP":"UDP")+"/"+port+")" );
	}

	public void
	destroy()
	{
		for (int i=0;i<listeners.size();i++){

			((UPnPMappingListener)listeners.get(i)).mappingDestroyed( this );
		}
	}

	protected void
	changed()
	{
		for (int i=0;i<listeners.size();i++){

			((UPnPMappingListener)listeners.get(i)).mappingChanged( this );
		}
	}

	public void
	addListener(
		UPnPMappingListener	l )
	{
		listeners.add(l);
	}

	public void
	removeListener(
		UPnPMappingListener	l )
	{
		listeners.remove(l);
	}
}
