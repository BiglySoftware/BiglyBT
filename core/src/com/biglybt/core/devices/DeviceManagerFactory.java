/*
 * Created on Jan 27, 2009
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


package com.biglybt.core.devices;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Debug;


public class
DeviceManagerFactory
{
	final private static Class<DeviceManager> impl_class;

	static{

		String impl = System.getProperty( "az.factory.devicemanager.impl", "com.biglybt.core.devices.impl.DeviceManagerImpl" );

		Class<DeviceManager> temp = null;

		if ( impl.length() > 0 ){

			try{
				temp = (Class<DeviceManager>)DeviceManagerFactory.class.getClassLoader().loadClass( impl );

			}catch( Throwable e ){

				Debug.out( "Failed to load DeviceManagerFactory class: " + impl );
			}
		}

		impl_class = temp;
	}

	private static DeviceManager	singleton;

	public static void
	preInitialise()
	{
		if ( impl_class != null ){

			try{
				impl_class.getMethod( "preInitialise" ).invoke( null, (Object[])null );

			}catch( Throwable e ){

				Debug.out( "preInitialise failed", e );
			}
		}
	}

	public static DeviceManager
	getSingleton()
	{
		synchronized( DeviceManagerFactory.class ){

			if ( singleton != null ){

				return( singleton );
			}

			if ( impl_class == null ){

				throw( new RuntimeException( "No Implementation" ));
			}

			boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals("az3");

			if ( !isAZ3 ){

					// musn't instantiate the device manager for console UI as this has unwanted side effects
					// such as enabling per-device content browse controls that end up hiding content but the
					// user has no way of changing this!

				Debug.out( "DeviceManager is only fully functional with BiglyBT (not classic) UI - some features unavailable" );

				return( null );
			}

			try{
				singleton = (DeviceManager)impl_class.getMethod( "getSingleton" ).invoke( null, (Object[])null );

				return( singleton );

			}catch( Throwable e ){

				throw( new RuntimeException( "No Implementation", e ));
			}
		}
	}
}
