/*
 * Created on 13-Jul-2004
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

package com.biglybt.pifimpl.local;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.PluginManagerArgumentHandler;
import com.biglybt.pif.PluginManagerDefaults;
import com.biglybt.pifimpl.local.launch.PluginSingleInstanceHandler;

public class
PluginManagerDefaultsImpl
	implements PluginManagerDefaults
{
	protected static  PluginManagerDefaultsImpl		singleton = new PluginManagerDefaultsImpl();

	public static PluginManagerDefaults
	getSingleton()
	{
		return( singleton );
	}

	protected List	disabled	= new ArrayList();

	@Override
	public String[]
	getDefaultPlugins()
	{
		return( PLUGIN_IDS );
	}

	@Override
	public void
	setDefaultPluginEnabled(
		String	plugin_id,
		boolean	enabled )
	{
		if ( enabled ){

			disabled.remove( plugin_id );

		}else if ( !disabled.contains( plugin_id )){

			disabled.add( plugin_id );
		}
	}

	@Override
	public boolean
	isDefaultPluginEnabled(
		String		plugin_id )
	{
		return( !disabled.contains( plugin_id));
	}

	@Override
	public void
	setApplicationName(
		String		name )
	{
		SystemProperties.setApplicationName( name );
	}

	@Override
	public String
	getApplicationName()
	{
		return( SystemProperties.getApplicationName());
	}

	@Override
	public void
	setApplicationIdentifier(
		String		id )
	{
		SystemProperties.setApplicationIdentifier( id );
	}

	@Override
	public String
	getApplicationIdentifier()
	{
		return( SystemProperties.getApplicationIdentifier());
	}

	@Override
	public void
	setApplicationEntryPoint(
		String		ep )
	{
		SystemProperties.setApplicationEntryPoint( ep );
	}

	@Override
	public String
	getApplicationEntryPoint()
	{
		return( SystemProperties.getApplicationEntryPoint());
	}

	@Override
	public void
	setSingleInstanceHandler(
		int									single_instance_port,
		PluginManagerArgumentHandler handler )
	{
		PluginSingleInstanceHandler.initialise( single_instance_port, handler );
	}

	@Override
	public boolean
	setSingleInstanceHandlerAndProcess(
		int									single_instance_port,
		PluginManagerArgumentHandler		handler,
		String[]							args )
	{
		PluginSingleInstanceHandler.initialise( single_instance_port, handler );

		return( PluginSingleInstanceHandler.initialiseAndProcess( single_instance_port, handler, args ));
	}
}
