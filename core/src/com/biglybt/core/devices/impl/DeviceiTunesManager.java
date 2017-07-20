/*
 * Created on Feb 10, 2009
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

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.pif.*;

public class
DeviceiTunesManager
{
	private DeviceManagerImpl		device_manager;

	private DeviceiTunes			itunes_device;

	protected
	DeviceiTunesManager(
		DeviceManagerImpl		_dm )
	{
		device_manager = _dm;

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				init(core);
			}
		});
	}

	void
	init(
			Core core )
	{

		final PluginManager pm = core.getPluginManager();

		final PluginInterface default_pi = pm.getDefaultPluginInterface();

		default_pi.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{
					default_pi.addEventListener(
						new PluginEventListener()
						{
							@Override
							public void
							handleEvent(
								PluginEvent ev )
							{
								int	type = ev.getType();

								if ( type == PluginEvent.PEV_PLUGIN_OPERATIONAL ){

									pluginAdded((PluginInterface)ev.getValue());
								}
								if ( type == PluginEvent.PEV_PLUGIN_NOT_OPERATIONAL ){

									pluginRemoved((PluginInterface)ev.getValue());
								}
							}
						});

					PluginInterface[] plugins = pm.getPlugins();

					for ( PluginInterface pi: plugins ){

						if ( pi.getPluginState().isOperational()){

							pluginAdded( pi );
						}
					}
				}

				@Override
				public void
				closedownInitiated()
				{
				}

				@Override
				public void
				closedownComplete()
				{
				}
			});
	}

	protected void
	pluginAdded(
		PluginInterface		pi )
	{
		if ( pi.getPluginState().isBuiltIn()){

			return;
		}

		String plugin_id = pi.getPluginID();

		if ( plugin_id.equals( "azitunes" )){

			DeviceiTunes new_device;

			synchronized( this ){

				if ( itunes_device == null ){

					itunes_device = new_device = new DeviceiTunes( device_manager, pi );

				}else{

					return;
				}
			}

			device_manager.addDevice( new_device, false );
		}
	}

	protected void
	pluginRemoved(
		PluginInterface		pi )
	{
		String plugin_id = pi.getPluginID();

		if ( plugin_id.equals( "azitunes" )){

			DeviceiTunes existing_device;

			synchronized( this ){

				if ( itunes_device != null ){

					existing_device = itunes_device;

					itunes_device = null;

				}else{

					return;
				}
			}

			existing_device.remove();
		}
	}
}
