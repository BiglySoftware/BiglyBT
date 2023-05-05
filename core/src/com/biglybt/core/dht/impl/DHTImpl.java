/*
 * Created on 12-Jan-2005
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

package com.biglybt.core.dht.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import com.biglybt.core.dht.*;
import com.biglybt.core.dht.control.DHTControl;
import com.biglybt.core.dht.control.DHTControlAdapter;
import com.biglybt.core.dht.control.DHTControlFactory;
import com.biglybt.core.dht.db.DHTDB;
import com.biglybt.core.dht.nat.DHTNATPuncher;
import com.biglybt.core.dht.nat.DHTNATPuncherAdapter;
import com.biglybt.core.dht.nat.DHTNATPuncherFactory;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionManager;
import com.biglybt.core.dht.router.DHTRouter;
import com.biglybt.core.dht.speed.DHTSpeedTester;
import com.biglybt.core.dht.speed.DHTSpeedTesterFactory;
import com.biglybt.core.dht.transport.DHTTransport;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.DHTTransportValue;
import com.biglybt.core.util.AERunStateHandler;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;

/**
 * @author parg
 *
 */

public class
DHTImpl
	implements DHT, AERunStateHandler.RunStateChangeListener
{
	final DHTStorageAdapter		storage_adapter;
	private DHTNATPuncherAdapter	nat_adapter;
	private final DHTControl				control;
	private DHTNATPuncher			nat_puncher;
	private DHTSpeedTester			speed_tester;
	private final Properties				properties;
	private final DHTLogger				logger;

	private final CopyOnWriteList<DHTListener>	listeners = new CopyOnWriteList<>();

	private boolean	runstate_startup 	= true;
	private boolean	sleeping			= false;

	public
	DHTImpl(
		DHTTransport			_transport,
		Properties				_properties,
		DHTStorageAdapter		_storage_adapter,
		DHTNATPuncherAdapter	_nat_adapter,
		DHTLogger				_logger )
	{
		properties		= _properties;
		storage_adapter	= _storage_adapter;
		nat_adapter		= _nat_adapter;
		logger			= _logger;

		DHTNetworkPositionManager.initialise( storage_adapter );

		DHTLog.setLogger( logger );

		int		K 		= getProp( PR_CONTACTS_PER_NODE, 			DHTControl.K_DEFAULT );
		int		B 		= getProp( PR_NODE_SPLIT_FACTOR, 			DHTControl.B_DEFAULT );
		int		max_r	= getProp( PR_MAX_REPLACEMENTS_PER_NODE, 	DHTControl.MAX_REP_PER_NODE_DEFAULT );
		int		s_conc 	= getProp( PR_SEARCH_CONCURRENCY, 			DHTControl.SEARCH_CONCURRENCY_DEFAULT );
		int		l_conc 	= getProp( PR_LOOKUP_CONCURRENCY, 			DHTControl.LOOKUP_CONCURRENCY_DEFAULT );
		int		o_rep 	= getProp( PR_ORIGINAL_REPUBLISH_INTERVAL, 	DHTControl.ORIGINAL_REPUBLISH_INTERVAL_DEFAULT );
		int		c_rep 	= getProp( PR_CACHE_REPUBLISH_INTERVAL, 	DHTControl.CACHE_REPUBLISH_INTERVAL_DEFAULT );
		int		c_n 	= getProp( PR_CACHE_AT_CLOSEST_N, 			DHTControl.CACHE_AT_CLOSEST_N_DEFAULT );
		boolean	e_c 	= getProp( PR_ENCODE_KEYS, 					DHTControl.ENCODE_KEYS_DEFAULT ) == 1;
		boolean	r_p 	= getProp( PR_ENABLE_RANDOM_LOOKUP, 		DHTControl.ENABLE_RANDOM_DEFAULT ) == 1;

		control = DHTControlFactory.create(
				new DHTControlAdapter()
				{
					@Override
					public DHTStorageAdapter
					getStorageAdapter()
					{
						return( storage_adapter );
					}

					@Override
					public boolean
					isDiversified(
						byte[]		key )
					{
						if ( storage_adapter == null ){

							return( false );
						}

						return( storage_adapter.isDiversified( key ));
					}

					@Override
					public byte[][]
					diversify(
						String				description,
						DHTTransportContact	cause,
						boolean				put_operation,
						boolean				existing,
						byte[]				key,
						byte				type,
						boolean				exhaustive,
						int					max_depth )
					{
						boolean	valid;

						if ( existing ){

							valid =	 	type == DHT.DT_FREQUENCY ||
										type == DHT.DT_SIZE ||
										type == DHT.DT_NONE;
						}else{

							valid = 	type == DHT.DT_FREQUENCY ||
										type == DHT.DT_SIZE;
						}

						if ( storage_adapter != null && valid ){

							if ( existing ){

								return( storage_adapter.getExistingDiversification( key, put_operation, exhaustive, max_depth ));

							}else{

								return( storage_adapter.createNewDiversification( description, cause, key, put_operation, type, exhaustive, max_depth ));
							}
						}else{

							if ( !valid ){

								Debug.out( "Invalid diversification received: type = " + type );
							}

							if ( existing ){

								return( new byte[][]{ key });

							}else{

								return( new byte[0][] );
							}
						}
					}
				},
				_transport,
				K, B, max_r,
				s_conc, l_conc,
				o_rep, c_rep, c_n, e_c, r_p,
				logger );

		if ( nat_adapter != null ){

			nat_puncher	= DHTNATPuncherFactory.create( nat_adapter, this );
		}

		AERunStateHandler.addListener( this, true );
	}

	public
	DHTImpl(
		DHTTransport			_transport,
		DHTRouter				_router,
		DHTDB					_database,
		Properties				_properties,
		DHTStorageAdapter		_storage_adapter,
		DHTLogger				_logger )
	{
		properties		= _properties;
		storage_adapter	= _storage_adapter;
		logger			= _logger;

		DHTNetworkPositionManager.initialise( storage_adapter );

		DHTLog.setLogger( logger );

		int		K 		= getProp( PR_CONTACTS_PER_NODE, 			DHTControl.K_DEFAULT );
		int		B 		= getProp( PR_NODE_SPLIT_FACTOR, 			DHTControl.B_DEFAULT );
		int		max_r	= getProp( PR_MAX_REPLACEMENTS_PER_NODE, 	DHTControl.MAX_REP_PER_NODE_DEFAULT );
		int		s_conc 	= getProp( PR_SEARCH_CONCURRENCY, 			DHTControl.SEARCH_CONCURRENCY_DEFAULT );
		int		l_conc 	= getProp( PR_LOOKUP_CONCURRENCY, 			DHTControl.LOOKUP_CONCURRENCY_DEFAULT );
		int		o_rep 	= getProp( PR_ORIGINAL_REPUBLISH_INTERVAL, 	DHTControl.ORIGINAL_REPUBLISH_INTERVAL_DEFAULT );
		int		c_rep 	= getProp( PR_CACHE_REPUBLISH_INTERVAL, 	DHTControl.CACHE_REPUBLISH_INTERVAL_DEFAULT );
		int		c_n 	= getProp( PR_CACHE_AT_CLOSEST_N, 			DHTControl.CACHE_AT_CLOSEST_N_DEFAULT );
		boolean	e_c 	= getProp( PR_ENCODE_KEYS, 					DHTControl.ENCODE_KEYS_DEFAULT ) == 1;
		boolean	r_p 	= getProp( PR_ENABLE_RANDOM_LOOKUP, 		DHTControl.ENABLE_RANDOM_DEFAULT ) == 1;

		control = DHTControlFactory.create(
				new DHTControlAdapter()
				{
					@Override
					public DHTStorageAdapter
					getStorageAdapter()
					{
						return( storage_adapter );
					}

					@Override
					public boolean
					isDiversified(
						byte[]		key )
					{
						if ( storage_adapter == null ){

							return( false );
						}

						return( storage_adapter.isDiversified( key ));
					}

					@Override
					public byte[][]
					diversify(
						String				description,
						DHTTransportContact	cause,
						boolean				put_operation,
						boolean				existing,
						byte[]				key,
						byte				type,
						boolean				exhaustive,
						int					max_depth )
					{
						boolean	valid;

						if ( existing ){

							valid =	 	type == DHT.DT_FREQUENCY ||
										type == DHT.DT_SIZE ||
										type == DHT.DT_NONE;
						}else{

							valid = 	type == DHT.DT_FREQUENCY ||
										type == DHT.DT_SIZE;
						}

						if ( storage_adapter != null && valid ){

							if ( existing ){

								return( storage_adapter.getExistingDiversification( key, put_operation, exhaustive, max_depth ));

							}else{

								return( storage_adapter.createNewDiversification( description, cause, key, put_operation, type, exhaustive, max_depth ));
							}
						}else{

							if ( !valid ){

								Debug.out( "Invalid diversification received: type = " + type );
							}

							if ( existing ){

								return( new byte[][]{ key });

							}else{

								return( new byte[0][] );
							}
						}
					}
				},
				_transport,
				_router,
				_database,
				K, B, max_r,
				s_conc, l_conc,
				o_rep, c_rep, c_n, e_c, r_p,
				logger );
	}

	@Override
	public void
	runStateChanged(
		long run_state )
	{
		try{
			boolean	is_sleeping = AERunStateHandler.isDHTSleeping();

			if ( sleeping != is_sleeping ){

				sleeping = is_sleeping;

				if ( !runstate_startup ){

					System.out.println( "DHT sleeping=" + sleeping );
				}
			}

			control.setSleeping( sleeping );

			DHTSpeedTester old_tester = null;
			DHTSpeedTester new_tester = null;

			synchronized( this ){

				if ( sleeping ){

					if ( speed_tester != null ){

						old_tester = speed_tester;

						speed_tester = null;
					}
				}else{

					new_tester = speed_tester = DHTSpeedTesterFactory.create( this );
				}
			}

			if ( old_tester != null ){

				if ( !runstate_startup ){

					System.out.println( "    destroying speed tester" );
				}

				old_tester.destroy();
			}

			if ( new_tester != null ){

				if ( !runstate_startup ){

					System.out.println( "    creating speed tester" );
				}

				for ( DHTListener l: listeners ){

					try{
						l.speedTesterAvailable( new_tester );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}finally{

			runstate_startup = false;
		}
	}

	@Override
	public boolean
	isSleeping()
	{
		return( sleeping );
	}

	@Override
	public void
	setSuspended(
		boolean	susp )
	{
		if ( susp ){

			if ( nat_puncher != null ){

				nat_puncher.setSuspended( true );
			}

			control.setSuspended( true );

		}else{

			control.setSuspended( false );

			if ( nat_puncher != null ){

				nat_puncher.setSuspended( false );
			}
		}
	}

	protected int
	getProp(
		String		name,
		int			def )
	{
		Integer	x = (Integer)properties.get(name);

		if ( x == null ){

			properties.put( name, new Integer( def ));

			return( def );
		}

		return( x.intValue());
	}

	@Override
	public int
	getIntProperty(
		String		name )
	{
		return(((Integer)properties.get(name)).intValue());
	}

	@Override
	public boolean
	isDiversified(
		byte[]		key )
	{
		return( control.isDiversified( key ));
	}

	@Override
	public void
	put(
		byte[]					key,
		String					description,
		byte[]					value,
		short					flags,
		DHTOperationListener	listener )
	{
		control.put( key, description, value, flags, (byte)0, DHT.REP_FACT_DEFAULT, true, listener );
	}

	@Override
	public void
	put(
		byte[]					key,
		String					description,
		byte[]					value,
		short					flags,
		boolean					high_priority,
		DHTOperationListener	listener )
	{
		control.put( key, description, value, flags, (byte)0, DHT.REP_FACT_DEFAULT, high_priority, listener );
	}

	@Override
	public void
	put(
		byte[]					key,
		String					description,
		byte[]					value,
		short					flags,
		byte					life_hours,
		boolean					high_priority,
		DHTOperationListener	listener )
	{
		control.put( key, description, value, flags, life_hours, DHT.REP_FACT_DEFAULT, high_priority, listener );
	}

	@Override
	public void
	put(
		byte[]					key,
		String					description,
		byte[]					value,
		short					flags,
		byte					life_hours,
		byte					replication_control,
		boolean					high_priority,
		DHTOperationListener	listener )
	{
		control.put( key, description, value, flags, life_hours, replication_control, high_priority, listener );
	}

	@Override
	public DHTTransportValue
	getLocalValue(
		byte[]		key )
	{
		return( control.getLocalValue( key ));
	}

	@Override
	public List<DHTTransportValue>
	getStoredValues(
		byte[]		key )
	{
		return( control.getStoredValues( key ));
	}

	@Override
	public void
	get(
		byte[]					key,
		String					description,
		short					flags,
		int						max_values,
		long					timeout,
		boolean					exhaustive,
		boolean					high_priority,
		DHTOperationListener	listener )
	{
		control.get( key, description, flags, max_values, timeout, exhaustive, high_priority, listener );
	}

	@Override
	public byte[]
	remove(
		byte[]					key,
		String					description,
		DHTOperationListener	listener )
	{
		return( control.remove( key, description, (short)0, listener ));
	}
	
	@Override
	public byte[]
	remove(
		byte[]					key,
		String					description,
		short					flags,
		DHTOperationListener	listener )
	{
		return( control.remove( key, description, flags, listener ));
	}

	@Override
	public byte[]
	remove(
		DHTTransportContact[]	contacts,
		byte[]					key,
		String					description,
		DHTOperationListener	listener )
	{
		return( control.remove( contacts, key, description, listener ));
	}

	@Override
	public DHTTransport
	getTransport()
	{
		return( control.getTransport());
	}

	@Override
	public DHTRouter
	getRouter()
	{
		return( control.getRouter());
	}

	@Override
	public DHTControl
	getControl()
	{
		return( control );
	}

	@Override
	public DHTDB
	getDataBase()
	{
		return( control.getDataBase());
	}

	@Override
	public DHTNATPuncher
	getNATPuncher()
	{
		return( nat_puncher );
	}

	public DHTSpeedTester
	getSpeedTester()
	{
		return( speed_tester );
	}

	@Override
	public DHTStorageAdapter
	getStorageAdapter()
	{
		return( storage_adapter );
	}

	@Override
	public void
	integrate(
		boolean		full_wait )
	{
		control.seed( full_wait );

		if ( nat_puncher!= null ){

			nat_puncher.start();
		}
	}

	@Override
	public void
	destroy()
	{
		if ( nat_puncher != null ){

			nat_puncher.destroy();
		}

		DHTNetworkPositionManager.destroy( storage_adapter );

		AERunStateHandler.removeListener( this );

		if ( control != null ){

			control.destroy();
		}

		if ( speed_tester != null ){

			speed_tester.destroy();
		}
	}

	@Override
	public void
	exportState(
		DataOutputStream	os,
		int					max )

		throws IOException
	{
		control.exportState( os, max );
	}

	@Override
	public void
	importState(
		DataInputStream		is )

		throws IOException
	{
		control.importState( is );
	}

	@Override
	public void
	setLogging(
		boolean	on )
	{
		DHTLog.setLogging( on );
	}

	@Override
	public DHTLogger
	getLogger()
	{
		return( logger );
	}

	@Override
	public void
	print(
		boolean	full )
	{
		control.print( full );
	}

	@Override
	public void
	addListener(
		DHTListener		listener )
	{
		listeners.add( listener );

		DHTSpeedTester st = speed_tester;

		if ( st != null ){

			listener.speedTesterAvailable( st );
		}
	}

	@Override
	public void
	removeListener(
		DHTListener		listener )
	{
		listeners.remove( listener );
	}
}
