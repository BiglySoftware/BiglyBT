/*
 * Created on Feb 8, 2007
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


package com.biglybt.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.magnet.MagnetPlugin;
import com.biglybt.plugin.magnet.MagnetPluginListener;

public class
ExternalStimulusHandler
{
	private static MagnetPlugin		magnet_plugin;
	private static List				pending_listeners;
	private static HashMap<ExternalStimulusListener, MagnetPluginListener> mapListeners = new HashMap<>();

	protected static void
	initialise(
		Core core )
	{
		PluginInterface pi = core.getPluginManager().getPluginInterfaceByClass( MagnetPlugin.class );

		if ( pi != null ){

			MagnetPlugin temp = (MagnetPlugin)pi.getPlugin();

			List	to_add;

			synchronized( ExternalStimulusHandler.class ){

				magnet_plugin = temp;

				to_add = pending_listeners;

				pending_listeners = null;
			}

			if ( to_add != null ){

				for (int i=0;i<to_add.size();i++){

					addListener((ExternalStimulusListener)to_add.get(i));
				}
			}
		}else{

			Debug.out( "Failed to resolve magnet plugin" );
		}

		// Debug.outNoStack( "ExternalStimulus debug" );

		addListener(
				new ExternalStimulusListener()
				{
					@Override
					public boolean
					receive(
						String name, Map values )
					{
						//System.out.println( "ExternalStimulus: " + name );
						//System.out.println("  " + (values == null ? -1 : values.size())
						//+ " Values: " + values);

						return( name.equals("ExternalStimulus.test"));
					}

					@Override
					public int
					query(
						String		name,
						Map			values )
					{
						return( Integer.MIN_VALUE );
					}
				});
	}

	public static void
	addListener(
		final ExternalStimulusListener		listener )
	{
		synchronized( ExternalStimulusHandler.class ){

			if ( magnet_plugin == null ){

				if ( pending_listeners == null ){

					pending_listeners = new ArrayList();
				}

				pending_listeners.add( listener );

				return;
			}
		}

		if ( magnet_plugin != null ){

			MagnetPluginListener magnetPluginListener = new MagnetPluginListener() {
				@Override
				public boolean
				set(
						String		name,
						Map		values )
				{
					try {
						return( listener.receive( name, values ));

					} catch (Throwable e) {

						Debug.out(e);

						return false;
					}
				}

				@Override
				public int
				get(
						String		name,
						Map			values )
				{
					try{
						return( listener.query( name, values ));

					}catch( Throwable e ){

						Debug.out( e );

						return( Integer.MIN_VALUE );
					}
				}
			};
			mapListeners.put(listener, magnetPluginListener);
			magnet_plugin.addListener(magnetPluginListener);
		}
	}

	public static void removeListener(ExternalStimulusListener listener) {
		synchronized( ExternalStimulusHandler.class ){
				if (pending_listeners != null && pending_listeners.remove( listener )) {
					return;
				}
		}

		if (magnet_plugin != null) {
			MagnetPluginListener pluginListener = mapListeners.remove(listener);
			magnet_plugin.removeListener(pluginListener);
		}

	}
}
