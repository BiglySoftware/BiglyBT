/*
 * Created on Jul 11, 2008
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


package com.biglybt.core.subs;

import com.biglybt.core.util.Debug;


public class
SubscriptionManagerFactory
{
	final private static Class<SubscriptionManager> impl_class;

	static{

		String impl = System.getProperty( "az.factory.subscriptionmanager.impl", "com.biglybt.core.subs.impl.SubscriptionManagerImpl" );

		Class<SubscriptionManager> temp = null;

		if ( impl.length() > 0 ){

			try{
				temp = (Class<SubscriptionManager>)SubscriptionManagerFactory.class.getClassLoader().loadClass( impl );

			}catch( Throwable e ){

				Debug.out( "Failed to load SubscriptionManager class: " + impl );
			}
		}

		impl_class = temp;
	}

	private static SubscriptionManager	singleton;

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

	public static SubscriptionManager
	getSingleton()
	{
		synchronized( SubscriptionManagerFactory.class ){

			if ( singleton != null ){

				return( singleton );
			}

			if ( impl_class == null ){

				throw( new RuntimeException( "No Implementation" ));
			}

			try{
				singleton = (SubscriptionManager)impl_class.getMethod( "getSingleton", boolean.class ).invoke( null, false );

				return( singleton );

			}catch( Throwable e ){

				throw( new RuntimeException( "No Implementation", e ));
			}
		}
	}
}
