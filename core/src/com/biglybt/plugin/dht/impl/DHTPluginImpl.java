/*
 * Created on 24-Jan-2005
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

package com.biglybt.plugin.dht.impl;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.dht.*;
import com.biglybt.core.dht.control.DHTControlStats;
import com.biglybt.core.dht.db.DHTDB;
import com.biglybt.core.dht.db.DHTDBStats;
import com.biglybt.core.dht.db.DHTDBValue;
import com.biglybt.core.dht.nat.DHTNATPuncher;
import com.biglybt.core.dht.nat.DHTNATPuncherAdapter;
import com.biglybt.core.dht.router.DHTRouterStats;
import com.biglybt.core.dht.transport.*;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPUtils;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginEvent;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;
import com.biglybt.plugin.dht.*;
import com.biglybt.plugin.dht.DHTPluginInterface.DHTInterface;
import com.biglybt.util.MapUtils;


/**
 * @author parg
 *
 */

public class
DHTPluginImpl
	implements DHTInterface, DHTPluginBasicInterface
{
	private static final String	SEED_ADDRESS_V4	= Constants.DHT_SEED_ADDRESS_V4;
	private static final String	SEED_ADDRESS_V6	= Constants.DHT_SEED_ADDRESS_V6;
	private static final int	SEED_PORT		= 6881;

	private static final long	MIN_ROOT_SEED_IMPORT_PERIOD	= 8*60*60*1000;


	private final DHTPlugin			dht_plugin;
	private final PluginInterface	plugin_interface;

	private int					status;
	private String				status_text;

	private ActionParameter		reseed_param;
	private BooleanParameter	warn_user_param;

	private DHT					dht;
	private int					port;
	private byte				protocol_version;
	private int					network;
	private boolean				v6;
	private DHTTransportUDP		transport;

	private DHTPluginStorageManager storage_manager;

	private long				last_root_seed_import_time;

	private LoggerChannel		log;
	private DHTLogger			dht_log;

	private int					stats_ticks;

	public
	DHTPluginImpl(
		DHTPlugin				_dht_plugin,
		PluginInterface			_plugin_interface,
		DHTNATPuncherAdapter	_nat_adapter,
		DHTPluginImplAdapter	_adapter,
		byte					_protocol_version,
		int						_network,
		boolean					_v6,
		String					_ip,
		int						_port,
		ActionParameter			_reseed,
		BooleanParameter		_warn_user_param,
		boolean					_logging,
		LoggerChannel			_log,
		DHTLogger				_dht_log )
	{
		dht_plugin			= _dht_plugin;
		plugin_interface	= _plugin_interface;
		protocol_version	= _protocol_version;
		network				= _network;
		v6					= _v6;
		port				= _port;
		reseed_param		= _reseed;
		warn_user_param		= _warn_user_param;
		log					= _log;
		dht_log				= _dht_log;

		final DHTPluginImplAdapter	adapter = _adapter;

		try{
			storage_manager = new DHTPluginStorageManager( network, dht_log, getDataDir( _network ));

			final PluginConfig conf = plugin_interface.getPluginconfig();

			int	send_delay = conf.getPluginIntParameter( "dht.senddelay", 25 );
			int	recv_delay	= conf.getPluginIntParameter( "dht.recvdelay", 10 );

			boolean	bootstrap	= conf.getPluginBooleanParameter( "dht.bootstrapnode", false );

				// start off optimistic with reachable = true

			boolean	initial_reachable	= conf.getPluginBooleanParameter( "dht.reachable." + network, true );

			transport =
				DHTTransportFactory.createUDP(
						_protocol_version,
						_network,
						_v6,
						_ip,
						storage_manager.getMostRecentAddress(),
						_port,
						3,
						1,
						5000, 	// udp timeout - tried less but a significant number of
								// premature timeouts occurred
								// reduced from 10s to 5s on 2024/03 as observing few
								// responses received after 5s
						send_delay, recv_delay,
						bootstrap,
						initial_reachable,
						dht_log );

			transport.addListener(
				new DHTTransportListener()
				{
					@Override
					public void
					localContactChanged(
						DHTTransportContact	local_contact )
					{
						storage_manager.localContactChanged( local_contact );

						if ( adapter != null ){

							adapter.localContactChanged( getLocalAddress());
						}
					}

					@Override
					public void
					resetNetworkPositions()
					{
					}

					@Override
					public void
					currentAddress(
						String		address )
					{
						storage_manager.recordCurrentAddress( address );
					}

					@Override
					public void
					reachabilityChanged(
						boolean	reacheable )
					{
					}
				});

			Properties	props = new Properties();

			/*
			System.out.println( "FRIGGED REFRESH PERIOD" );

			props.put( DHT.PR_CACHE_REPUBLISH_INTERVAL, new Integer( 5*60*1000 ));
			*/

			if ( DHTFactory.isSmallNetwork(_network)){

					// reduce network usage

				//System.out.println( "CVS DHT cache republish interval modified" );

				props.put( DHT.PR_CACHE_REPUBLISH_INTERVAL, new Integer( 1*60*60*1000 ));
			}

			dht = DHTFactory.create(
						transport,
						props,
						storage_manager,
						_nat_adapter,
						dht_log );

			plugin_interface.firePluginEvent(
				new PluginEvent()
				{
					@Override
					public int
					getType()
					{
						return( DHTPlugin.EVENT_DHT_AVAILABLE );
					}

					@Override
					public Object
					getValue()
					{
						return( dht );
					}
				});

			dht.setLogging( _logging );

			DHTTransportContact root_seed = importRootSeed();

			storage_manager.importContacts( dht );

			plugin_interface.getUtilities().createTimer( "DHTExport", true ).addPeriodicEvent(
					2*60*1000,
					new UTTimerEventPerformer()
					{
						private int tick_count = 0;

						@Override
						public void
						perform(
							UTTimerEvent		event )
						{
							tick_count++;

							if ( tick_count == 1 || tick_count % 5 == 0 ){

								checkForReSeed(false);

								storage_manager.exportContacts( dht );
							}
						}
					});

			integrateDHT( true, root_seed );

			status = DHTPlugin.STATUS_RUNNING;

			status_text = "Running";

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			log.log( "DHT integrtion fails", e );

			status_text = "DHT Integration fails: " + Debug.getNestedExceptionMessage( e );

			status	= DHTPlugin.STATUS_FAILED;
		}
	}

	@Override
	public String 
	getAENetwork()
	{
		return( dht_plugin.getNetwork());
	}
	
	@Override
	public DHTInterface[] 
	getDHTInterfaces()
	{
		return( new DHTInterface[]{ this });
	}
	
	@Override
	public boolean 
	isEnabled()
	{
		return( dht_plugin.isEnabled());
	}
	
	@Override
	public boolean 
	isInitialising()
	{
		return( dht_plugin.isInitialising());
	}
	
	@Override
	public boolean 
	isSleeping()
	{
		return( dht_plugin.isSleeping());
	}
	
	public void
	updateStats(
		int		sample_stats_ticks )
	{
		stats_ticks++;

		if ( transport != null ){

			PluginConfig conf = plugin_interface.getPluginconfig();

			boolean current_reachable = transport.isReachable();

			if ( current_reachable != conf.getPluginBooleanParameter( "dht.reachable." + network, true )){

					// reachability has changed

				conf.setPluginParameter( "dht.reachable." + network, current_reachable );

				if ( !current_reachable ){

					String msg = "If you have a router/firewall, please check that you have port " + port +
									" UDP open.\nDecentralised tracking requires this." ;

					int	warned_port = plugin_interface.getPluginconfig().getPluginIntParameter( "udp_warned_port", 0 );

					if ( warned_port == port || !warn_user_param.getValue() ){

						log.log( msg );

					}else{

						plugin_interface.getPluginconfig().setPluginParameter( "udp_warned_port", port );

						log.logAlert( LoggerChannel.LT_WARNING, msg );
					}
				}else{

					log.log( "Reachability changed for the better" );
				}
			}

			if ( stats_ticks % sample_stats_ticks == 0 ){

				logStats();
			}
		}
	}

	public int
	getStatus()
	{
		return( status );
	}

	public String
	getStatusText()
	{
		return( status_text );
	}

	public boolean
	isReachable()
	{
		return( transport.isReachable());
	}

	public void
	setLogging(
		boolean		l )
	{
		dht.setLogging( l );
	}

	public void
	tick()
	{
	}

	public int
	getPort()
	{
		return( port );
	}

	public void
	setPort(
		int	new_port )
	{
		port	= new_port;

		try{
			transport.setPort( port );

		}catch( Throwable e ){

			log.log( e );
		}
	}

	public long
	getClockSkew()
	{
		return( transport.getStats().getSkewAverage());
	}

	public void
	logStats()
	{
		DHTDBStats			d_stats	= dht.getDataBase().getStats();
		DHTControlStats		c_stats = dht.getControl().getStats();
		DHTRouterStats		r_stats = dht.getRouter().getStats();
		DHTTransportStats 	t_stats = transport.getStats();

		long[]	rs = r_stats.getStats();

		log.log( "DHT:ip=" + transport.getLocalContact().getAddress() +
					",net=" + transport.getNetwork() +
					",prot=V" + transport.getProtocolVersion()+
					",reach=" + transport.isReachable()+
					",flags=" + Integer.toString((int)(transport.getGenericFlags()&0xff), 16 ));

		log.log( 	"Router" +
					":nodes=" + rs[DHTRouterStats.ST_NODES] +
					",leaves=" + rs[DHTRouterStats.ST_LEAVES] +
					",contacts=" + rs[DHTRouterStats.ST_CONTACTS] +
					",replacement=" + rs[DHTRouterStats.ST_REPLACEMENTS] +
					",live=" + rs[DHTRouterStats.ST_CONTACTS_LIVE] +
					",unknown=" + rs[DHTRouterStats.ST_CONTACTS_UNKNOWN] +
					",failing=" + rs[DHTRouterStats.ST_CONTACTS_DEAD]);

		log.log( 	"Transport" +
					":" + t_stats.getString());

		int[]	dbv_details = d_stats.getValueDetails();

		log.log(    "Control:dht=" + c_stats.getEstimatedDHTSize() +
				   	", Database:keys=" + d_stats.getKeyCount() +
				   	",vals=" + dbv_details[DHTDBStats.VD_VALUE_COUNT]+
				   	",loc=" + dbv_details[DHTDBStats.VD_LOCAL_SIZE]+
				   	",dir=" + dbv_details[DHTDBStats.VD_DIRECT_SIZE]+
				   	",ind=" + dbv_details[DHTDBStats.VD_INDIRECT_SIZE]+
				   	",div_f=" + dbv_details[DHTDBStats.VD_DIV_FREQ]+
				   	",div_s=" + dbv_details[DHTDBStats.VD_DIV_SIZE] );

		DHTNATPuncher np = dht.getNATPuncher();

		if ( np != null ){
			log.log( "NAT: " + np.getStats());
		}
	}

	protected File
	getDataDir(
		int		network )
	{
		File	dir = FileUtil.newFile( plugin_interface.getUtilities().getUserDir(), "dht" );

		if ( network != 0 ){

			dir = FileUtil.newFile( dir, "net" + network );
		}

		FileUtil.mkdirs(dir);

		return( dir );
	}

	public void
	integrateDHT(
		boolean				first,
		DHTTransportContact	remove_afterwards )
	{
		try{
			reseed_param.setEnabled( false );

			log.log( "DHT " + (first?"":"re-") + "integration starts" );

			long	start = SystemTime.getCurrentTime();

			dht.integrate( false );

			if ( remove_afterwards != null ){

				log.log( "Removing seed " + remove_afterwards.getString());

				remove_afterwards.remove();
			}

			long	end = SystemTime.getCurrentTime();

			log.log( "DHT " + (first?"":"re-") + "integration complete: elapsed = " + (end-start));

			dht.print( false );

		}finally{

			reseed_param.setEnabled( true );
		}
	}

	public void
	checkForReSeed(
		boolean	force )
	{
		int	seed_limit = 32;

		try{

			long[]	router_stats = dht.getRouter().getStats().getStats();

			if ( router_stats[ DHTRouterStats.ST_CONTACTS_LIVE] < seed_limit || force ){

				if ( force ){

					log.log( "Reseeding" );

				}else{

					log.log( "Less than 32 live contacts, reseeding" );
				}

				int	peers_imported	= 0;

					// only try boostrapping off connected peers on the main network as it is unlikely
					// any of them are running CVS and hence the boostrap will fail

				if ( network == DHT.NW_AZ_MAIN || network == DHT.NW_AZ_MAIN_V6 ){

						// first look for peers to directly import

					Download[]	downloads = plugin_interface.getDownloadManager().getDownloads();

outer:

					for (int i=0;i<downloads.length;i++){

						Download	download = downloads[i];

						PeerManager pm = download.getPeerManager();

						if ( pm == null ){

							continue;
						}

						Peer[] 	peers = pm.getPeers();

						for (int j=0;j<peers.length;j++){

							Peer	p = peers[j];

							int	peer_udp_port = p.getUDPNonDataListenPort();

							if ( peer_udp_port != 0 ){

								boolean is_v6 = p.getIp().contains( ":" );

								if ( is_v6 == v6 ){

									String ip =  p.getIp();

									if ( AENetworkClassifier.categoriseAddress( ip ) == AENetworkClassifier.AT_PUBLIC ){

										if ( importSeed( ip, peer_udp_port ) != null ){

											peers_imported++;

											if ( peers_imported > seed_limit ){

												break outer;
											}
										}
									}
								}
							}
						}
					}

					if ( peers_imported < 16 ){

						List<InetSocketAddress> list = VersionCheckClient.getSingleton().getDHTBootstrap( network != DHT.NW_AZ_MAIN_V6 );

						for ( InetSocketAddress address: list ){

							if ( importSeed( address ) != null ){

								peers_imported++;

								if ( peers_imported > seed_limit ){

									break;
								}
							}
						}
					}
				}else if ( network == DHT.NW_BIGLYBT_MAIN ) {
					
					List<InetSocketAddress> list = VersionCheckClient.getSingleton().getDHTBootstrap( true );

					for ( InetSocketAddress address: list ){

						if ( importSeed( address ) != null ){

							peers_imported++;

							if ( peers_imported > seed_limit ){

								break;
							}
						}
					}
				}
				
				if ( peers_imported < seed_limit ){
					
					List<DHTTransportAlternativeContact> contacts = 
						DHTUDPUtils.getAlternativeContacts( 
							network == DHT.NW_AZ_MAIN_V6?DHTTransportAlternativeNetwork.AT_BIGLYBT_IPV6:DHTTransportAlternativeNetwork.AT_BIGLYBT_IPV4,
							seed_limit - peers_imported );
					
					for ( DHTTransportAlternativeContact c: contacts ){
						
						Map<String, Object> props = c.getProperties();
						
						String ip = MapUtils.getMapString( props, "a", null );
						
						int	port = (int)MapUtils.getMapLong( props, "p", 0 );
						
						if ( ip != null && port > 0 ){
							
							if ( importSeed( ip, port ) != null ){
	
								peers_imported++;
	
								if ( peers_imported > seed_limit ){
									
									break;
								}
							}
						}
					}
				}

				DHTTransportContact	root_to_remove = null;

				if ( peers_imported == 0 ){

					root_to_remove = importRootSeed();

					if ( root_to_remove != null ){

						peers_imported++;
					}
				}

				if ( peers_imported > 0 ){

					integrateDHT( false, root_to_remove );

				}else{

					log.log( "No valid peers found to reseed from" );
				}
			}

		}catch( Throwable e ){

			log.log(e);
		}
	}

	protected DHTTransportContact
	importRootSeed()
	{
		try{
			long	 now = SystemTime.getCurrentTime();

			if ( now - last_root_seed_import_time > MIN_ROOT_SEED_IMPORT_PERIOD ){

				last_root_seed_import_time	= now;

				return( importSeed( getSeedAddress()));

			}else{

				log.log( "    root seed imported too recently, ignoring" );
			}
		}catch( Throwable e ){

			log.log(e);
		}

		return( null );
	}

	public DHTTransportContact
	importSeed(
		String		ip,
		int			port )
	{
		try{
			return(	transport.importContact( checkResolve( new InetSocketAddress( ip, port )), protocol_version, true ));

		}catch( Throwable e ){

			log.log(e);

			return( null );
		}
	}

	protected DHTTransportContact
	importSeed(
		InetAddress		ia,
		int				port )

	{
		try{
			return(	transport.importContact( new InetSocketAddress( ia, port ), protocol_version, true ));

		}catch( Throwable e ){

			log.log(e);

			return( null );
		}
	}

	protected DHTTransportContact
	importSeed(
		InetSocketAddress		ia )

	{
		try{
			return(	transport.importContact( ia, protocol_version, true ));

		}catch( Throwable e ){

			log.log(e);

			return( null );
		}
	}

	protected InetSocketAddress
	getSeedAddress()
	{
		return( checkResolve( new InetSocketAddress( v6?SEED_ADDRESS_V6:SEED_ADDRESS_V4, SEED_PORT )));
	}

	private InetSocketAddress
	checkResolve(
		InetSocketAddress	isa )
	{
		if ( v6 ){

			if ( isa.isUnresolved()){

				try{
					DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();

					if ( dns_utils != null ){

						String host = dns_utils.getIPV6ByName( isa.getHostName()).getHostAddress();

						isa = new InetSocketAddress( host, isa.getPort());
					}
				}catch( Throwable e ){
				}
			}
		}

		return( isa );
	}

	public boolean
	isDiversified(
		byte[]		key )
	{
		return( dht.isDiversified( key ));
	}

	public void
	put(
		byte[]						key,
		String						description,
		byte[]						value,
		byte						flags,
		DHTPluginOperationListener	listener)
	{
		put( key, description, value, flags, true, listener );
	}

	public void
	put(
		final byte[]						key,
		final String						description,
		final byte[]						value,
		final byte							flags,
		final boolean						high_priority,
		final DHTPluginOperationListener	listener)
	{
		putEx( key, description, value, (short)(flags&0x00ff), high_priority, listener );
	}
	
	public void
	putEx(
		final byte[]						key,
		final String						description,
		final byte[]						value,
		final short							flags,
		final boolean						high_priority,
		final DHTPluginOperationListener	listener)
	{
		dht.put( 	key,
					description,
					value,
					flags,
					high_priority,
					new DHTOperationListener()
					{
						private boolean started;

						@Override
						public void
						searching(
							DHTTransportContact	contact,
							int					level,
							int					active_searches )
						{
							if ( listener != null ){

								synchronized( this ){

									if ( started ){

										return;
									}

									started = true;
								}

								listener.starts( key );
							}
						}

						@Override
						public boolean
						diversified(
							String		desc )
						{
							listener.diversified();
							
							return( true );
						}

						@Override
						public void
						found(
							DHTTransportContact	contact,
							boolean				is_closest )
						{
						}

						@Override
						public void
						read(
							DHTTransportContact	_contact,
							DHTTransportValue	_value )
						{
							Debug.out( "read operation not supported for puts" );
						}

						@Override
						public void
						wrote(
							DHTTransportContact	_contact,
							DHTTransportValue	_value )
						{
							// log.log( "Put: wrote " + _value.getString() + " to " + _contact.getString());

							if ( listener != null ){

								listener.valueWritten( new DHTPluginContactImpl(DHTPluginImpl.this, _contact ), mapValue( _value ));
							}

						}

						@Override
						public void
						complete(
							boolean				timeout )
						{
							// log.log( "Put: complete, timeout = " + timeout );

							if ( listener != null ){

								listener.complete( key, timeout );
							}
						}
					});
	}

	public DHTPluginValue
	getLocalValue(
		byte[]		key )
	{
		final DHTTransportValue	val = dht.getLocalValue( key );

		if ( val == null ){

			return( null );
		}

		return( mapValue( val ));
	}

	public List<DHTPluginValue>
	getValues()
	{
		DHTDB	db = dht.getDataBase();

		Iterator<HashWrapper>	keys = db.getKeys();

		List<DHTPluginValue>	vals = new ArrayList<>();

		while( keys.hasNext()){

			DHTDBValue val = db.getAnyValue( keys.next());

			if ( val != null ){

				vals.add( mapValue( val ));
			}
		}

		return( vals );
	}

	public List<DHTPluginValue>
	getValues(byte[] key)
	{
		List<DHTPluginValue>	vals = new ArrayList<>();

		if ( dht != null ){

			try{
				List<DHTTransportValue> values = dht.getStoredValues( key );

				for ( DHTTransportValue v: values ){

					vals.add( mapValue( v ));
				}

				return( vals );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( vals );
	}

	public void
	get(
		final byte[]								key,
		final String								description,
		final byte									flags,
		final int									max_values,
		final long									timeout,
		final boolean								exhaustive,
		final boolean								high_priority,
		final DHTPluginOperationListener			listener )
	{
		dht.get( 	key, description, flags, max_values, timeout, exhaustive, high_priority,
					new DHTOperationListener()
					{
						private boolean	started = false;

						@Override
						public void
						searching(
							DHTTransportContact	contact,
							int					level,
							int					active_searches )
						{
							if ( listener != null ){

								synchronized( this ){

									if ( started ){

										return;
									}

									started = true;
								}

								listener.starts( key );
							}
						}

						@Override
						public boolean
						diversified(
							String		desc )
						{
							if ( listener != null ){

								return( listener.diversified());
							}

							return( true );
						}

						@Override
						public void
						found(
							DHTTransportContact	contact,
							boolean				is_closest )
						{
						}

						@Override
						public void
						read(
							final DHTTransportContact	contact,
							final DHTTransportValue		value )
						{
							// log.log( "Get: read " + value.getString() + " from " + contact.getString() + ", originator = " + value.getOriginator().getString());

							if ( listener != null ){

								listener.valueRead( new DHTPluginContactImpl( DHTPluginImpl.this, value.getOriginator()), mapValue( value ));
							}
						}

						@Override
						public void
						wrote(
							final DHTTransportContact	contact,
							final DHTTransportValue		value )
						{
							// log.log( "Get: wrote " + value.getString() + " to " + contact.getString());
						}

						@Override
						public void
						complete(
							boolean				_timeout )
						{
							// log.log( "Get: complete, timeout = " + _timeout );

							if ( listener != null ){

								listener.complete( key, _timeout );
							}
						}
					});
	}

	public void
	remove(
		byte[]						key,
		String						description,
		short						flags,
		DHTPluginOperationListener	listener )
	{
		dht.remove( 	key,
						description,
						flags,
						new DHTOperationListener()
						{
							private boolean started;

							@Override
							public void
							searching(
								DHTTransportContact	contact,
								int					level,
								int					active_searches )
							{
								if ( listener != null ){

									synchronized( this ){

										if ( started ){

											return;
										}

										started = true;
									}

									listener.starts( key );
								}
							}

							@Override
							public void
							found(
								DHTTransportContact	contact,
								boolean				is_closest )
							{
							}

							@Override
							public boolean
							diversified(
								String		desc )
							{
								return( true );
							}

							@Override
							public void
							read(
								DHTTransportContact	contact,
								DHTTransportValue	value )
							{
								// log.log( "Remove: read " + value.getString() + " from " + contact.getString());
							}

							@Override
							public void
							wrote(
								DHTTransportContact	contact,
								DHTTransportValue	value )
							{
								// log.log( "Remove: wrote " + value.getString() + " to " + contact.getString());
								if ( listener != null ){

									listener.valueWritten( new DHTPluginContactImpl( DHTPluginImpl.this, contact ), mapValue( value ));
								}
							}

							@Override
							public void
							complete(
								boolean				timeout )
							{
								// log.log( "Remove: complete, timeout = " + timeout );

								if ( listener != null ){

									listener.complete( key, timeout );
								}
							}
						});
	}

	public void
	remove(
		final DHTPluginContact[]			targets,
		final byte[]						key,
		final String						description,
		final DHTPluginOperationListener	listener )
	{
		DHTTransportContact[]	t_contacts = new DHTTransportContact[ targets.length ];

		for (int i=0;i<targets.length;i++){

			t_contacts[i] = ((DHTPluginContactImpl)targets[i]).getContact();
		}

		dht.remove( 	t_contacts,
						key,
						description,
						new DHTOperationListener()
						{
							private boolean started;

							@Override
							public void
							searching(
								DHTTransportContact	contact,
								int					level,
								int					active_searches )
							{
								if ( listener != null ){

									synchronized( this ){

										if ( started ){

											return;
										}

										started = true;
									}

									listener.starts( key );
								}
							}

							@Override
							public void
							found(
								DHTTransportContact	contact,
								boolean				is_closest )
							{
							}

							@Override
							public boolean
							diversified(
								String		desc )
							{
								return( true );
							}

							@Override
							public void
							read(
								DHTTransportContact	contact,
								DHTTransportValue	value )
							{
								// log.log( "Remove: read " + value.getString() + " from " + contact.getString());
							}

							@Override
							public void
							wrote(
								DHTTransportContact	contact,
								DHTTransportValue	value )
							{
								// log.log( "Remove: wrote " + value.getString() + " to " + contact.getString());
								if ( listener != null ){

									listener.valueWritten( new DHTPluginContactImpl( DHTPluginImpl.this, contact ), mapValue( value ));
								}
							}

							@Override
							public void
							complete(
								boolean				timeout )
							{
								// log.log( "Remove: complete, timeout = " + timeout );

								if ( listener != null ){

									listener.complete( key, timeout );
								}
							}
						});
	}

	public DHTPluginContact
	getLocalAddress()
	{
		return( new DHTPluginContactImpl( this, transport.getLocalContact()));
	}

	public DHTPluginContact
	importContact(
		Map<String,Object>				map )
	{
		try{
			return( new DHTPluginContactImpl( this, transport.importContact( map)));

		}catch( DHTTransportException	e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

	public DHTPluginContact
	importContact(
		InetSocketAddress				address )
	{
		try{
			return( new DHTPluginContactImpl( this, transport.importContact( address, protocol_version, false )));

		}catch( DHTTransportException	e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

	public DHTPluginContact
	importContact(
		InetSocketAddress				address,
		byte							version )
	{
		try{
			return( new DHTPluginContactImpl( this, transport.importContact( address, version, false )));

		}catch( DHTTransportException	e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

		// direct read/write support

	private Map<DHTPluginTransferHandler,DHTTransportTransferHandler>	handler_map = new HashMap<>();

	public void
	registerHandler(
		byte[]							handler_key,
		final DHTPluginTransferHandler	handler,
		Map<String,Object>				options )
	{
		DHTTransportTransferHandler h =
			new DHTTransportTransferHandler()
			{
				@Override
				public String
				getName()
				{
					return( handler.getName());
				}

				@Override
				public byte[]
				handleRead(
					DHTTransportContact	originator,
					byte[]				key )
				{
					return( handler.handleRead( new DHTPluginContactImpl( DHTPluginImpl.this, originator ), key ));
				}

				@Override
				public byte[]
				handleWrite(
					DHTTransportContact	originator,
					byte[]				key,
					byte[]				value )
				{
					return( handler.handleWrite( new DHTPluginContactImpl( DHTPluginImpl.this, originator ), key, value ));
				}
			};

		synchronized( handler_map ){

			if ( handler_map.containsKey( handler )){

				Debug.out( "Warning: handler already exists" );
			}else{

				handler_map.put( handler, h );
			}
		}

		dht.getTransport().registerTransferHandler( handler_key, h, options );
	}

	public void
	unregisterHandler(
		byte[]							handler_key,
		final DHTPluginTransferHandler	handler )
	{
		DHTTransportTransferHandler h;

		synchronized( handler_map ){

			h = handler_map.remove( handler );
		}

		if ( h == null ){

			Debug.out( "Mapping not found for handler" );

		}else{

			try{
				getDHT().getTransport().unregisterTransferHandler( handler_key, h );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	public byte[]
	read(
		final DHTPluginProgressListener listener,
		DHTPluginContact				target,
		byte[]							handler_key,
		byte[]							key,
		long							timeout )
	{
		try{
			return( dht.getTransport().readTransfer(
					listener == null ? null :
						new DHTTransportProgressListener()
						{
							@Override
							public void
							reportSize(
								long	size )
							{
								listener.reportSize( size );
							}

							@Override
							public void
							reportActivity(
								String	str )
							{
								listener.reportActivity( str );
							}

							@Override
							public void
							reportCompleteness(
								int		percent )
							{
								listener.reportCompleteness( percent );
							}
						},
						((DHTPluginContactImpl)target).getContact(),
						handler_key,
						key,
						timeout ));

		}catch( DHTTransportException e ){

			throw( new RuntimeException( e ));
		}
	}

	public void
	write(
		final DHTPluginProgressListener	listener,
		DHTPluginContact				target,
		byte[]							handler_key,
		byte[]							key,
		byte[]							data,
		long							timeout )
	{
		try{
			dht.getTransport().writeTransfer(
					listener == null ? null :
					new DHTTransportProgressListener()
					{
						@Override
						public void
						reportSize(
							long	size )
						{
							listener.reportSize( size );
						}

						@Override
						public void
						reportActivity(
							String	str )
						{
							listener.reportActivity( str );
						}

						@Override
						public void
						reportCompleteness(
							int		percent )
						{
							listener.reportCompleteness( percent );
						}
					},
					((DHTPluginContactImpl)target).getContact(),
					handler_key,
					key,
					data,
					timeout );

		}catch( DHTTransportException e ){

			throw( new RuntimeException( e ));
		}
	}

	public byte[]
	call(
		final DHTPluginProgressListener	listener,
		DHTPluginContact				target,
		byte[]							handler_key,
		byte[]							data,
		long							timeout )
	{
		if (Logger.isClosingTakingTooLong()){
		
			throw( new RuntimeException( "Closedown taking too long" ));
		}
		
		try{
			return(
				dht.getTransport().writeReadTransfer(
					listener == null ? null :
					new DHTTransportProgressListener()
					{
						@Override
						public void
						reportSize(
							long	size )
						{
							listener.reportSize( size );
						}

						@Override
						public void
						reportActivity(
							String	str )
						{
							listener.reportActivity( str );
						}

						@Override
						public void
						reportCompleteness(
							int		percent )
						{
							listener.reportCompleteness( percent );
						}
					},
					((DHTPluginContactImpl)target).getContact(),
					handler_key,
					data,
					timeout ));

		}catch( DHTTransportException e ){

			throw( new RuntimeException( e ));
		}
	}

	public DHT
	getDHT()
	{
		return( dht );
	}

	public void
	setSuspended(
		boolean	susp )
	{
		dht.setSuspended( susp );
	}

	public void
	closedownInitiated()
	{
		storage_manager.exportContacts( dht );

		dht.destroy();
	}

	public boolean
	isRecentAddress(
		String		address )
	{
		return( storage_manager.isRecentAddress( address ));
	}

	protected DHTPluginValue
	mapValue(
		final DHTTransportValue	value )
	{
		if ( value == null ){

			return( null );
		}

		return( new DHTPluginValueImpl(value));
	}


	public DHTPluginKeyStats
	decodeStats(
		DHTPluginValue	value )
	{
		if (( value.getFlags() & DHTPlugin.FLAG_STATS) == 0 ){

			return( null );
		}

		try{
			DataInputStream	dis = new DataInputStream( new ByteArrayInputStream( value.getValue()));

			final DHTStorageKeyStats stats = storage_manager.deserialiseStats( dis );

			return(
				new DHTPluginKeyStats()
				{
					@Override
					public int
					getEntryCount()
					{
						return( stats.getEntryCount());
					}

					@Override
					public int
					getSize()
					{
						return( stats.getSize());
					}

					@Override
					public int
					getReadsPerMinute()
					{
						return( stats.getReadsPerMinute());
					}

					@Override
					public byte
					getDiversification()
					{
						return( stats.getDiversification());
					}
				});

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

	@Override
	public byte[]
	getID()
	{
		return( dht.getRouter().getID());
	}

	@Override
	public boolean
	isIPV6()
	{
		return( dht.getTransport().isIPV6());
	}

	@Override
	public int
	getNetwork()
	{
		return( dht.getTransport().getNetwork());
	}

	@Override
	public DHTPluginContact[]
	getReachableContacts()
	{
		DHTTransportContact[] contacts = dht.getTransport().getReachableContacts();

		DHTPluginContact[] result = new DHTPluginContact[contacts.length];

		for ( int i=0;i<contacts.length;i++ ){

			result[i] = new DHTPluginContactImpl( this, contacts[i] );
		}

		return( result );
	}

	@Override
	public DHTPluginContact[]
	getRecentContacts()
	{
		DHTTransportContact[] contacts = dht.getTransport().getRecentContacts();

		DHTPluginContact[] result = new DHTPluginContact[contacts.length];

		for ( int i=0;i<contacts.length;i++ ){

			result[i] = new DHTPluginContactImpl( this, contacts[i] );
		}

		return( result );
	}

	@Override
	public List<DHTPluginContact>
	getClosestContacts(
		byte[]		to_id,
		boolean		live_only )
	{
		List<DHTTransportContact> contacts = dht.getControl().getClosestKContactsList(to_id, live_only);

		List<DHTPluginContact> result = new ArrayList<>(contacts.size());

		for ( DHTTransportContact contact: contacts ){

			result.add( new DHTPluginContactImpl( this, contact ));
		}

		return( result );
	}
}
