/*
 * Created on 21-Jan-2005
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

package com.biglybt.core.dht.transport.udp.impl;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.DHTLogger;
import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionManager;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionProvider;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionProviderListener;
import com.biglybt.core.dht.transport.*;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.DHTTransportUDPContact;
import com.biglybt.core.dht.transport.udp.impl.packethandler.*;
import com.biglybt.core.dht.transport.util.DHTTransferHandler;
import com.biglybt.core.dht.transport.util.DHTTransferHandler.Packet;
import com.biglybt.core.dht.transport.util.DHTTransportRequestCounter;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.util.*;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerFactory;

/**
 * @author parg
 *
 */

public class
DHTTransportUDPImpl
	implements DHTTransportUDP, DHTUDPRequestHandler
{
	@SuppressWarnings("CanBeFinal")
	public static boolean TEST_EXTERNAL_IP	= false;

	public static final int		MIN_ADDRESS_CHANGE_PERIOD_INIT_DEFAULT	= 5*60*1000;
	public static final int		MIN_ADDRESS_CHANGE_PERIOD_NEXT_DEFAULT	= 10*60*1000;

	public static final int		STORE_TIMEOUT_MULTIPLIER = 2;


	String				external_address;
	private int					min_address_change_period = MIN_ADDRESS_CHANGE_PERIOD_INIT_DEFAULT;

	private final byte				protocol_version;
	private final int					network;
	private final boolean				v6;
	private final String				ip_override;
	private int					port;
	private final int					max_fails_for_live;
	private final int					max_fails_for_unknown;
	private long				request_timeout;
	private long				store_timeout;
	private boolean				reachable;
	private boolean				reachable_accurate;
	private final int					dht_send_delay;
	private final int					dht_receive_delay;

	private InetAddress			explicit_bind;
	
	final DHTLogger			logger;

	DHTUDPPacketHandler			packet_handler;

	DHTTransportRequestHandler	request_handler;

	DHTTransportUDPContactImpl		local_contact;

	private long last_address_change;

	final List listeners	= new ArrayList();

	private final IpFilter	ip_filter	= IpFilterManagerFactory.getSingleton().getIPFilter();


	DHTTransportUDPStatsImpl	stats;

	private boolean		bootstrap_node	= false;

	private byte		generic_flags	= DHTTransportUDP.GF_NONE;
	private byte		generic_flags2	= VersionCheckClient.getSingleton().getDHTFlags();

	private static final int CONTACT_HISTORY_MAX 		= 32;
	private static final int CONTACT_HISTORY_PING_SIZE	= 24;

	final Map<InetSocketAddress,DHTTransportContact>	contact_history =
		new LinkedHashMap<InetSocketAddress,DHTTransportContact>(CONTACT_HISTORY_MAX,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<InetSocketAddress,DHTTransportContact> eldest)
			{
				return size() > CONTACT_HISTORY_MAX;
			}
		};

	private static final int ROUTABLE_CONTACT_HISTORY_MAX 		= 128;

	final Map<InetSocketAddress,DHTTransportContact>	routable_contact_history =
		new LinkedHashMap<InetSocketAddress,DHTTransportContact>(ROUTABLE_CONTACT_HISTORY_MAX,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<InetSocketAddress,DHTTransportContact> eldest)
			{
				return size() > ROUTABLE_CONTACT_HISTORY_MAX;
			}
		};


	private long					other_routable_total;
	private long					other_non_routable_total;
	private final MovingImmediateAverage	routeable_percentage_average = AverageFactory.MovingImmediateAverage(8);

	private static final int RECENT_REPORTS_HISTORY_MAX = 32;

	private final Map	recent_reports =
			new LinkedHashMap(RECENT_REPORTS_HISTORY_MAX,0.75f,true)
			{
				@Override
				protected boolean
				removeEldestEntry(
			   		Map.Entry eldest)
				{
					return size() > RECENT_REPORTS_HISTORY_MAX;
				}
			};


	private static final int	STATS_PERIOD		= 60*1000;
	private static final int 	STATS_DURATION_SECS	= 600;			// 10 minute average
	private static final long	STATS_INIT_PERIOD	= 15*60*1000;	// bit more than 10 mins to allow average to establish

	private long	stats_start_time	= SystemTime.getCurrentTime();
	private long	last_alien_count;
	private long	last_alien_fv_count;

	private final Average	alien_average 		= Average.getInstance(STATS_PERIOD,STATS_DURATION_SECS);
	private final Average	alien_fv_average 	= Average.getInstance(STATS_PERIOD,STATS_DURATION_SECS);

	private Random				random;

	private static final int	BAD_IP_BLOOM_FILTER_SIZE	= 32000;
	private BloomFilter			bad_ip_bloom_filter;

	private static final AEMonitor	class_mon	= new AEMonitor( "DHTTransportUDP:class" );

	final AEMonitor	this_mon	= new AEMonitor( "DHTTransportUDP" );

	private boolean		initial_address_change_deferred;
	boolean		address_changing;

	private final DHTTransferHandler xfer_handler;

	public
	DHTTransportUDPImpl(
		byte			_protocol_version,
		int				_network,
		boolean			_v6,
		String			_ip,
		String			_default_ip,
		int				_port,
		int				_max_fails_for_live,
		int				_max_fails_for_unknown,
		long			_timeout,
		int				_dht_send_delay,
		int				_dht_receive_delay,
		boolean			_bootstrap_node,
		boolean			_initial_reachability,
		DHTLogger		_logger )

		throws DHTTransportException
	{
		protocol_version		= _protocol_version;
		network					= _network;
		v6						= _v6;
		ip_override				= _ip;
		port					= _port;
		max_fails_for_live		= _max_fails_for_live;
		max_fails_for_unknown	= _max_fails_for_unknown;
		request_timeout			= _timeout;
		dht_send_delay			= _dht_send_delay;
		dht_receive_delay		= _dht_receive_delay;
		bootstrap_node			= _bootstrap_node;
		reachable				= _initial_reachability;
		logger					= _logger;

		store_timeout			= request_timeout * STORE_TIMEOUT_MULTIPLIER;

		try{
			random = RandomUtils.SECURE_RANDOM;

		}catch( Throwable e ){

			random	= new Random();

			logger.log( e );
		}

		xfer_handler =
			new DHTTransferHandler(
				new DHTTransferHandler.Adapter() {

					@Override
					public void
					sendRequest(
						DHTTransportContact 	_contact,
						Packet 					packet	)
					{
						DHTTransportUDPContactImpl	contact = (DHTTransportUDPContactImpl)_contact;

						DHTUDPPacketData	request =
								new DHTUDPPacketData(
									DHTTransportUDPImpl.this,
									packet.getConnectionId(),
									local_contact,
									contact );
						
						request.setDetails(
							packet.getPacketType(),
							packet.getTransferKey(),
							packet.getRequestKey(),
							packet.getData(),
							packet.getStartPosition(),
							packet.getLength(),
							packet.getTotalLength());

						try{
							checkAddress( contact );

							stats.dataSent( request );

							packet_handler.send(
								request,
								contact.getTransportAddress());

						}catch( Throwable e ){

						}
					}

					@Override
					public long
					getConnectionID()
					{
						return( DHTTransportUDPImpl.this.getConnectionID());
					}
				},
				DHTUDPPacketData.MAX_DATA_SIZE,
				1,
				logger);

		int last_pct = COConfigurationManager.getIntParameter( "dht.udp.net" + network + ".routeable_pct", -1 );

		if ( last_pct > 0 ){

			routeable_percentage_average.update( last_pct );
		}

		DHTUDPUtils.registerTransport( this );

		createPacketHandler();

		SimpleTimer.addPeriodicEvent(
			"DHTUDP:stats",
			STATS_PERIOD,
			new TimerEventPerformer()
			{
				private int tick_count;

				@Override
				public void
				perform(
					TimerEvent	event )
				{
					updateStats( tick_count++);

					checkAltContacts();
				}
			});

		String	default_ip = _default_ip==null?(v6?"::1":"127.0.0.1"):_default_ip;

		getExternalAddress( default_ip, logger );

		InetSocketAddress	address = new InetSocketAddress( external_address, port );

		DHTNetworkPositionManager.addProviderListener(
			new DHTNetworkPositionProviderListener()
			{
				@Override
				public void
				providerAdded(
					DHTNetworkPositionProvider		provider )
				{
					if ( local_contact != null ){

						local_contact.createNetworkPositions( true );

						try{
							this_mon.enter();

							for ( DHTTransportContact c: contact_history.values()){

								c.createNetworkPositions( false );
							}

							for ( DHTTransportContact c: routable_contact_history.values()){

								c.createNetworkPositions( false );
							}
						}finally{

							this_mon.exit();
						}

						for (int i=0;i<listeners.size();i++){

							try{
								((DHTTransportListener)listeners.get(i)).resetNetworkPositions();

							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}
					}
				}

				@Override
				public void
				providerRemoved(
					DHTNetworkPositionProvider		provider )
				{
				}
			});

		logger.log( "Initial external address: " + address );

		local_contact = new DHTTransportUDPContactImpl( true, this, address, address, protocol_version, random.nextInt(), 0, (byte)0 );
		
		if ( network == DHT.NW_AZ_MAIN || network == DHT.NW_AZ_MAIN_V6 ){
			
				// local provider for use by the plugin for xfer
			
			DHTUDPUtils.registerAlternativeNetwork( 
				new DHTTransportAlternativeNetwork(){
					
					@Override
					public int 
					getNetworkType()
					{
						return( network == DHT.NW_AZ_MAIN?DHTTransportAlternativeNetwork.AT_BIGLYBT_IPV4:DHTTransportAlternativeNetwork.AT_BIGLYBT_IPV6 );
					}
					
					@Override
					public List<DHTTransportAlternativeContact> 
					getContacts(
						int		max )
					{
						List<DHTTransportAlternativeContact>	result = new ArrayList<>( max );
						
						if ( max > 0 ){
							
							try{
								this_mon.enter();
								
								int seen_secs = (int)(SystemTime.getMonotonousTime()/1000);	// use "now" as don't know otherwise
								
								int net = getNetworkType();
								
								for ( DHTTransportContact c: routable_contact_history.values()){
	
									InetSocketAddress ia = c.getAddress();
									
									Map<String,Object> properties = new HashMap<>();
									
									properties.put( "a",  ia.getAddress().getHostAddress());
									properties.put( "p",  (long)ia.getPort());
									
									int _id;
									
									try{
										_id = Arrays.hashCode( BEncoder.encode( properties ));
										
									}catch( Throwable e ){
										
										_id = 0;
									}
									
									int id = _id;
									
									result.add( 
										new DHTTransportAlternativeContact()
										{
											public int
											getNetworkType()
											{
												return( net );
											}
	
											public int
											getVersion()
											{
												return( 1 );
											}
				
											public int
											getID()
											{
												return( id );
											}
	
											public int
											getLastAlive()
											{
												return( seen_secs );
											}
												
											public Map<String,Object>
											getProperties()
											{
												return( properties );
											}
										});
									
									max--;
									
									if ( max == 0 ){
										
										break;
									}
								}
							}finally{
	
								this_mon.exit();
							}
						}
						
						return( result );
					}
				});
		}
	}

	protected void
	createPacketHandler()

		throws DHTTransportException
	{
		DHTUDPPacketHelper.registerCodecs();

		// DHTPRUDPPacket relies on the request-handler being an instanceof THIS so watch out
		// if you change it :)

		try{
			if ( packet_handler != null && !packet_handler.isDestroyed()){

				packet_handler.destroy();
			}

			packet_handler = DHTUDPPacketHandlerFactory.getHandler( this, this );

		}catch( Throwable e ){

			throw( new DHTTransportException( "Failed to get packet handler", e ));
		}

			// limit send and receive rates. Receive rate is lower as we want a stricter limit
			// on the max speed we generate packets than those we're willing to process.

		// logger.log( "send delay = " + _dht_send_delay + ", recv = " + _dht_receive_delay );

		packet_handler.setDelays( dht_send_delay, dht_receive_delay, (int)request_timeout );

		stats_start_time	= SystemTime.getCurrentTime();

		if ( stats == null ){

			stats =  new DHTTransportUDPStatsImpl( this, protocol_version, packet_handler.getStats());

		}else{

			stats.setStats( packet_handler.getStats());
		}
	}

	@Override
	public DHTUDPRequestHandler
	getRequestHandler()
	{
		return( this );
	}

	@Override
	public DHTUDPPacketHandler
	getPacketHandler()
	{
		return( packet_handler );
	}

	@Override
	public void
	setSuspended(
		boolean			susp )
	{
		if ( susp ){

			if ( packet_handler != null ){

				packet_handler.destroy();
			}
		}else{
			if ( packet_handler == null || packet_handler.isDestroyed()){

				try{
					createPacketHandler();

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}

	protected void
	updateStats(
		int	tick_count )
	{
			// pick up latest value

		generic_flags2	= VersionCheckClient.getSingleton().getDHTFlags();

		long	alien_count	= 0;

		long[]	aliens = stats.getAliens();

		for (int i=0;i<aliens.length;i++){

			alien_count	+= aliens[i];
		}

		long	alien_fv_count = aliens[ DHTTransportStats.AT_FIND_VALUE ];

		alien_average.addValue( (alien_count-last_alien_count)*STATS_PERIOD/1000);
		alien_fv_average.addValue( (alien_fv_count-last_alien_fv_count)*STATS_PERIOD/1000);

		last_alien_count	= alien_count;
		last_alien_fv_count	= alien_fv_count;

		long	now = SystemTime.getCurrentTime();

		if ( now < 	stats_start_time ){

			stats_start_time	= now;

		}else{

				// only fiddle with the initial view of reachability when things have had
				// time to stabilise

			if ( Constants.isCVSVersion()){

				long fv_average 		= alien_fv_average.getAverage();
				long all_average 		= alien_average.getAverage();

				logger.log( "Aliens for net " + network + ": " + fv_average + "/" + all_average );
			}

			if ( now - stats_start_time > STATS_INIT_PERIOD ){

				reachable_accurate	= true;

				boolean	old_reachable	= reachable;

				if ( alien_fv_average.getAverage() > 1 ){

					reachable	= true;

				}else if ( alien_average.getAverage() > 3 ){

					reachable	= true;

				}else{

					reachable	= false;
				}

				if ( old_reachable != reachable ){

					for (int i=0;i<listeners.size();i++){

						try{
							((DHTTransportListener)listeners.get(i)).reachabilityChanged( reachable );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}
			}
		}

		int	pct = getRouteablePercentage();

		if ( pct > 0 ){

			COConfigurationManager.setParameter("dht.udp.net" + network + ".routeable_pct", pct );
		}

		// System.out.println( "routables=" + other_routable_total + ", non=" + other_non_routable_total );

		// System.out.println( "net " + network + ": aliens = " + alien_average.getAverage() + ", alien fv = " + alien_fv_average.getAverage());
	}

	protected void
	recordSkew(
		InetSocketAddress	originator_address,
		long				skew )
	{
		if ( stats != null ){

			stats.recordSkew( originator_address, skew );
		}
	}

	protected int
	getNodeStatus()
	{
		if ( bootstrap_node ){

				// bootstrap node is special case and not generally routable

			return( 0 );
		}

		if ( reachable_accurate ){

			int	status = reachable?DHTTransportUDPContactImpl.NODE_STATUS_ROUTABLE:0;

			return( status );

		}else{

			return( DHTTransportUDPContactImpl.NODE_STATUS_UNKNOWN );
		}
	}

	@Override
	public boolean
	isReachable()
	{
		return( reachable );
	}

	@Override
	public byte
	getProtocolVersion()
	{
		return( protocol_version );
	}

	@Override
	public byte
	getMinimumProtocolVersion()
	{
		int net = getNetwork();
		
		if ( net == DHT.NW_AZ_CVS ){
			
			return( DHTTransportUDP.PROTOCOL_VERSION_MIN_AZ_CVS );
			
		}else if ( net == DHT.NW_AZ_MAIN || net == DHT.NW_AZ_MAIN_V6 ){
			
			return( DHTTransportUDP.PROTOCOL_VERSION_MIN_AZ );
			
		}else{
			
			return( DHTTransportUDP.PROTOCOL_VERSION_MIN_BIGLYBT );
		}
	}

	@Override
	public int
	getPort()
	{
		return( port );
	}

	@Override
	public void
	setPort(
		int	new_port )

		throws DHTTransportException
	{
		if ( new_port == port ){

			return;
		}

		port	= new_port;

		createPacketHandler();

		setLocalContact();
	}

	@Override
	public long
	getTimeout()
	{
		return( request_timeout );
	}

	@Override
	public void
	setTimeout(
		long		timeout )
	{
		if ( request_timeout == timeout ){

			return;
		}

		request_timeout = timeout;
		store_timeout   = request_timeout * STORE_TIMEOUT_MULTIPLIER;

		packet_handler.setDelays( dht_send_delay, dht_receive_delay, (int)request_timeout );
	}
	
	@Override
	public InetAddress 
	getCurrentBindAddress()
	{
		return( packet_handler.getPacketHandler().getCurrentBindAddress());
	}
	
	@Override
	public InetAddress 
	getExplicitBindAddress()
	{
		return( packet_handler.getPacketHandler().getExplicitBindAddress());
	}
	
	@Override
	public void 
	setExplicitBindAddress(
		InetAddress		address,
		boolean			autoDelegate )
	{
		try{
			class_mon.enter();

			explicit_bind = address;
			
			if ( address != null ){
			
				external_address = address.getHostAddress();
			}
			
			packet_handler.getPacketHandler().setExplicitBindAddress( address, autoDelegate );
		
		}finally{
			
			class_mon.exit();
		}
		
		if ( address != null ){
			
			setLocalContact();
		}
	}


	@Override
	public int
	getNetwork()
	{
		return( network );
	}

	@Override
	public byte
	getGenericFlags()
	{
		return( generic_flags );
	}

	public byte
	getGenericFlags2()
	{
		return( generic_flags2 );
	}

	@Override
	public void
	setGenericFlag(
		byte		flag,
		boolean		value )
	{
		synchronized( this ){

			if ( value ){

				generic_flags |= flag;

			}else{

				generic_flags &= ~flag;
			}
		}
	}

	@Override
	public boolean
	isIPV6()
	{
		return( v6 );
	}

	public void
	testInstanceIDChange()

		throws DHTTransportException
	{
		local_contact = new DHTTransportUDPContactImpl( true, this, local_contact.getTransportAddress(), local_contact.getExternalAddress(), protocol_version, random.nextInt(), 0, (byte)0 );
	}

	public void
	testTransportIDChange()

		throws DHTTransportException
	{
		if ( external_address.equals("127.0.0.1")){

			external_address = "192.168.0.2";
		}else{

			external_address = "127.0.0.1";
		}

		InetSocketAddress	address = new InetSocketAddress( external_address, port );

		local_contact = new DHTTransportUDPContactImpl( true, this, address, address, protocol_version, local_contact.getInstanceID(), 0, (byte)0 );

		for (int i=0;i<listeners.size();i++){

			try{
				((DHTTransportListener)listeners.get(i)).localContactChanged( local_contact );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	public void
	testExternalAddressChange()
	{
		try{
			Iterator	it = contact_history.values().iterator();

			DHTTransportUDPContactImpl c1 = (DHTTransportUDPContactImpl)it.next();
			DHTTransportUDPContactImpl c2 = (DHTTransportUDPContactImpl)it.next();

			externalAddressChange( c1, c2.getExternalAddress(), true );
			//externalAddressChange( c, new InetSocketAddress( "192.168.0.7", 6881 ));

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	public void
	testNetworkAlive(
		boolean		alive )
	{
		packet_handler.testNetworkAlive( alive );
	}

	protected void
	getExternalAddress(
		String				default_address,
		final DHTLogger		log )
	{
			// class level synchronisation is for testing purposes when running multiple UDP instances
			// in the same VM

		try{
			class_mon.enter();

			String new_external_address = null;

			try{
				log.log( "Obtaining external address" );

				if ( TEST_EXTERNAL_IP ){

					new_external_address	= v6?"::1":"127.0.0.1";

					log.log( "    External IP address obtained from test data: " + new_external_address );
				}

				if ( explicit_bind != null ){
					
					new_external_address = explicit_bind.getHostAddress();
				}
				
				if ( ip_override != null ){

					new_external_address	= ip_override;

					log.log( "    External IP address explicitly overridden: " + new_external_address );
				}

				if ( new_external_address == null ){

						// First attempt is via other contacts we know about. Select three

					List	contacts;

					try{
						this_mon.enter();

						contacts = new ArrayList( contact_history.values());

					}finally{

						this_mon.exit();
					}

						// randomly select a number of entries to ping until we
						// get three replies

					String	returned_address 	= null;
					int		returned_matches	= 0;

					int		search_lim = Math.min( CONTACT_HISTORY_PING_SIZE, contacts.size());

					log.log( "    Contacts to search = " + search_lim );

					for (int i=0;i<search_lim;i++){

						DHTTransportUDPContactImpl	contact = (DHTTransportUDPContactImpl)contacts.remove(RandomUtils.nextInt(contacts.size()));

						InetSocketAddress a = askContactForExternalAddress( contact );

						if ( a != null && a.getAddress() != null ){

							String	ip = a.getAddress().getHostAddress();

							if ( returned_address == null ){

								returned_address = ip;

								log.log( "    : contact " + contact.getString() + " reported external address as '" + ip + "'" );

								returned_matches++;

							}else if ( returned_address.equals( ip )){

								returned_matches++;

								log.log( "    : contact " + contact.getString() + " also reported external address as '" + ip + "'" );

								if ( returned_matches == 3 ){

									new_external_address	= returned_address;

									log.log( "    External IP address obtained from contacts: "  + returned_address );

									break;
								}
							}else{

								log.log( "    : contact " + contact.getString() + " reported external address as '" + ip + "', abandoning due to mismatch" );

									// mismatch - give up

								break;
							}
						}else{

							log.log( "    : contact " + contact.getString() + " didn't reply" );
						}
					}

				}

				if ( new_external_address == null ){

					InetAddress public_address = logger.getPluginInterface().getUtilities().getPublicAddress( v6 );

					if ( public_address != null ){

						new_external_address = public_address.getHostAddress();

						log.log( "    External IP address obtained: " + new_external_address );
					}
				}

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}

			if ( new_external_address == null ){

				new_external_address =	default_address;

				log.log( "    External IP address defaulted:  " + new_external_address );
			}

			if ( external_address == null || !external_address.equals( new_external_address )){

				informLocalAddress( new_external_address );
			}

			external_address = new_external_address;

		}finally{

			class_mon.exit();
		}
	}

	protected void
	informLocalAddress(
		String	address )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				((DHTTransportListener)listeners.get(i)).currentAddress( address );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	externalAddressChange(
		final DHTTransportUDPContactImpl	reporter,
		final InetSocketAddress				new_address,
		boolean								force )

		throws DHTTransportException
	{
			/*
			 * A node has reported that our external address and the one he's seen a
			 * message coming from differ. Natural explanations are along the lines of
			 * 1) my address is dynamically allocated by my ISP and it has changed
			 * 2) I have multiple network interfaces
			 * 3) there's some kind of proxy going on
			 * 4) this is a DOS attempting to stuff me up
			 *
			 * We assume that our address won't change more frequently than once every
			 * 5 minutes
			 * We assume that if we can successfully obtain an external address by
			 * using the above explicit check then this is accurate
			 * Only in the case where the above check fails do we believe the address
			 * that we've been told about
			 */

		InetAddress	ia = new_address.getAddress();

		if ( ia == null ){

			Debug.out( "reported new external address '" + new_address + "' is unresolved" );

			throw( new DHTTransportException( "Address '" + new_address + "' is unresolved" ));
		}

			// dump addresses incompatible with our protocol

		if ( 	( ia instanceof Inet4Address && v6  ) ||
				( ia instanceof Inet6Address && !v6 )){

				// reduce debug spam, just return

			// throw( new DHTTransportException( "Address " + new_address + " is incompatible with protocol family for " + external_address ));

			return;
		}

		final String	new_ip = ia.getHostAddress();

		if ( new_ip.equals( external_address )){

				// probably just be a second notification of an address change, return
				// "ok to retry" as it should now work

			return;
		}

		try{
			this_mon.enter();

			long	now = SystemTime.getCurrentTime();

			if ( now - last_address_change < min_address_change_period ){

				return;
			}

			if ( contact_history.size() < CONTACT_HISTORY_MAX && !force ){

				if ( !initial_address_change_deferred ){

					initial_address_change_deferred = true;

					logger.log( "Node " + reporter.getString() + " has reported that the external IP address is '" + new_address + "': deferring new checks" );

					new DelayedEvent(
						"DHTTransportUDP:delayAC",
						30*1000,
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								try{
									externalAddressChange( reporter, new_address, true );

								}catch( Throwable e ){

								}
							}
						});
				}

				return;
			}

			logger.log( "Node " + reporter.getString() + " has reported that the external IP address is '" + new_address + "'" );

				// check for dodgy addresses that shouldn't appear as an external address!

			if ( invalidExternalAddress( ia )){

				logger.log( "     This is invalid as it is a private address." );

				return;
			}

				// another situation to ignore is where the reported address is the same as
				// the reporter (they must be seeing it via, say, socks connection on a local
				// interface

			if ( reporter.getExternalAddress().getAddress().getHostAddress().equals( new_ip )){

				logger.log( "     This is invalid as it is the same as the reporter's address." );

				return;
			}

			last_address_change	= now;

				// bump up min period for subsequent changes

			if ( min_address_change_period == MIN_ADDRESS_CHANGE_PERIOD_INIT_DEFAULT ){

				min_address_change_period = MIN_ADDRESS_CHANGE_PERIOD_NEXT_DEFAULT;
			}
		}finally{

			this_mon.exit();
		}

		final String	old_external_address = external_address;

			// we need to perform this test on a separate thread otherwise we'll block in the UDP handling
			// code because we're already running on the "process" callback from the UDP handler
			// (the test attempts to ping contacts)


		new AEThread2( "DHTTransportUDP:getAddress", true )
		{
			@Override
			public void
			run()
			{
				try{
					this_mon.enter();

					if ( address_changing ){

						return;
					}

					address_changing	= true;

				}finally{

					this_mon.exit();
				}

				try{
					getExternalAddress( new_ip, logger );

					if ( old_external_address.equals( external_address )){

							// address hasn't changed, notifier must be perceiving different address
							// due to proxy or something

						return;
					}

					setLocalContact();

				}finally{

					try{
						this_mon.enter();

						address_changing	= false;

					}finally{

						this_mon.exit();
					}
				}
			}
		}.start();
	}

	protected void
	contactAlive(
		DHTTransportUDPContactImpl	contact )
	{
		try{
			this_mon.enter();

			contact_history.put( contact.getTransportAddress(), contact );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public DHTTransportContact[]
   	getReachableContacts()
   	{
		try{
			this_mon.enter();

			Collection<DHTTransportContact> vals = routable_contact_history.values();

			DHTTransportContact[]	res = new DHTTransportContact[vals.size()];

			vals.toArray( res );

			return( res );

		}finally{

			this_mon.exit();
		}
   	}

	@Override
	public DHTTransportContact[]
   	getRecentContacts()
   	{
		try{
			this_mon.enter();

			Collection<DHTTransportContact> vals = contact_history.values();

			DHTTransportContact[]	res = new DHTTransportContact[vals.size()];

			vals.toArray( res );

			return( res );

		}finally{

			this_mon.exit();
		}
   	}

	protected void
	updateContactStatus(
		DHTTransportUDPContactImpl	contact,
		int							status,
		boolean						incoming )
	{
		try{
			this_mon.enter();

			contact.setNodeStatus( status );

			if ( contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_XFER_STATUS ){

				if ( status != DHTTransportUDPContactImpl.NODE_STATUS_UNKNOWN ){

					boolean	other_routable = (status & DHTTransportUDPContactImpl.NODE_STATUS_ROUTABLE) != 0;

						// only maintain stats on incoming requests so we get a fair sample.
						// in general we'll only get replies from routable contacts so if we
						// take this into account then everything gets skewed

					if ( other_routable ){

						if ( incoming ){

							synchronized( routeable_percentage_average ){

								other_routable_total++;
							}
						}

						routable_contact_history.put( contact.getTransportAddress(), contact );

					}else{

						if ( incoming ){

							synchronized( routeable_percentage_average ){

								other_non_routable_total++;
							}
						}
					}
				}
			}

		}finally{

			this_mon.exit();
		}
	}

	public int
	getRouteablePercentage()
	{
		synchronized( routeable_percentage_average ){

			double	average = routeable_percentage_average.getAverage();

			long	both_total = other_routable_total + other_non_routable_total;

			int	current_percent;

			if ( both_total == 0 ){

				current_percent = 0;

			}else{

				current_percent = (int)(( other_routable_total * 100 )/ both_total );
			}

			if ( both_total >= 300 ){

					// add current to average and reset

				if ( current_percent > 0 ){

					average = routeable_percentage_average.update( current_percent );

					other_routable_total = other_non_routable_total = 0;
				}
			}else if ( both_total >= 100 ){

					// if we have enough samples and no existing average then use current

				if ( average == 0 ){

					average = current_percent;

				}else{

						// factor in current percantage

					int samples = routeable_percentage_average.getSampleCount();

					if ( samples > 0 ){

						average = (( samples * average ) + current_percent ) / ( samples + 1 );
					}
				}
			}

			int result = (int)average;

			if ( result == 0 ){

					// -1 indicates we have no usable value

				result = -1;
			}

			return( result );
		}
	}

	protected boolean
	invalidExternalAddress(
		InetAddress	ia )
	{
		return(	ia.isLinkLocalAddress() ||
				ia.isLoopbackAddress() ||
				ia.isSiteLocalAddress());
	}

	protected int
	getMaxFailForLiveCount()
	{
		return( max_fails_for_live );
	}

	protected int
	getMaxFailForUnknownCount()
	{
		return( max_fails_for_unknown );
	}

	@Override
	public DHTTransportContact
	getLocalContact()
	{
		return( local_contact );
	}

	protected void
	setLocalContact()
	{
		InetSocketAddress	s_address = new InetSocketAddress( external_address, port );

		try{
			local_contact = new DHTTransportUDPContactImpl( true, DHTTransportUDPImpl.this, s_address, s_address, protocol_version, random.nextInt(), 0, (byte)0 );

			logger.log( "External address changed: " + s_address );

			Debug.out( "DHTTransport: address changed to " + s_address );

			for (int i=0;i<listeners.size();i++){

				try{
					((DHTTransportListener)listeners.get(i)).localContactChanged( local_contact );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	@Override
	public DHTTransportContact
	importContact(
		DataInputStream		is,
		boolean				is_bootstrap )

		throws IOException, DHTTransportException
	{
		DHTTransportUDPContactImpl	contact = DHTUDPUtils.deserialiseContact( this, is );

		importContact( contact, is_bootstrap );

		return( contact );
	}

	@Override
	public DHTTransportUDPContact
	importContact(
		InetSocketAddress	_address,
		byte				_protocol_version,
		boolean				is_bootstrap )

		throws DHTTransportException
	{
			// instance id of 0 means "unknown"

		DHTTransportUDPContactImpl	contact = new DHTTransportUDPContactImpl( false, this, _address, _address, _protocol_version, 0, 0, (byte)0 );

		importContact( contact, is_bootstrap );

		return( contact );
	}

	protected void
	importContact(
		DHTTransportUDPContactImpl	contact,
		boolean						is_bootstrap )
	{
		try{
			this_mon.enter();

				// consider newly imported contacts as potential contacts for IP address queries if we've
				// got space (in particular, at start of day we may be able to get an address off these if
				// they're still alive )

			if ( contact_history.size() < CONTACT_HISTORY_MAX ){

				contact_history.put( contact.getTransportAddress(), contact );
			}

		}finally{

			this_mon.exit();
		}

		request_handler.contactImported( contact, is_bootstrap );

		// logger.log( "Imported contact " + contact.getString());
	}

	public void
	exportContact(
		DHTTransportContact	contact,
		DataOutputStream	os )

		throws IOException, DHTTransportException
	{
		DHTUDPUtils.serialiseContact( os, contact );
	}

	public Map<String,Object>
	exportContactToMap(
		DHTTransportContact	contact )
	{
		Map<String,Object>		result = new HashMap<>();

		result.put( "v",contact.getProtocolVersion());

		InetSocketAddress address = contact.getExternalAddress();

		result.put( "p", address.getPort());

		InetAddress	ia = address.getAddress();

		if ( ia == null ){


			result.put( "h", address.getHostName());

		}else{

			result.put( "a", ia.getAddress());
		}

		return( result );
	}

	@Override
	public DHTTransportUDPContact
	importContact(
		Map<String,Object>		map )
	{
		int version = ((Number)map.get( "v" )).intValue();

		int port = ((Number)map.get( "p" )).intValue();

		byte[]	a = (byte[])map.get( "a" );

		InetSocketAddress address;

		try{
			if ( a == null ){

				address = InetSocketAddress.createUnresolved( new String((byte[])map.get("h"), "UTF-8" ), port );
			}else{

				address = new InetSocketAddress( InetAddress.getByAddress( a ), port );
			}

			DHTTransportUDPContactImpl contact = new DHTTransportUDPContactImpl( false, this, address, address, (byte)version, 0, 0, (byte)0 );

			importContact( contact, false );

			return( contact );

		}catch( Throwable e ){

			Debug.out( e );

			return( null );
		}
	}

	public void
	removeContact(
		DHTTransportContact	contact )
	{
		request_handler.contactRemoved( contact );
	}

	@Override
	public void
	setRequestHandler(
		DHTTransportRequestHandler	_request_handler )
	{
		request_handler = new DHTTransportRequestCounter( _request_handler, stats );
	}

	@Override
	public DHTTransportStats
	getStats()
	{
		return( stats );
	}

	//protected HashMap	port_map = new HashMap();
	//protected long		last_portmap_dump	= SystemTime.getCurrentTime();

	protected void
	checkAddress(
		DHTTransportUDPContactImpl		contact )

		throws DHTUDPPacketHandlerException
	{
		/*
		int	port = contact.getExternalAddress().getPort();

		try{
			this_mon.enter();

			int	count;

			Integer i = (Integer)port_map.get(new Integer(port));

			if ( i != null ){

				count 	= i.intValue() + 1;

			}else{

				count	= 1;
			}

			port_map.put( new Integer(port), new Integer(count));

			long	now = SystemTime.getCurrentTime();

			if ( now - last_portmap_dump > 60000 ){

				last_portmap_dump	= now;

				Iterator	it = port_map.keySet().iterator();

				Map	rev = new TreeMap();

				while( it.hasNext()){

					Integer	key = (Integer)it.next();

					Integer	val = (Integer)port_map.get(key);

					rev.put( val, key );
				}

				it = rev.keySet().iterator();

				while( it.hasNext()){

					Integer	val = (Integer)it.next();

					Integer	key = (Integer)rev.get(val);

					System.out.println( "port:" + key + "->" + val );
				}
			}

		}finally{

			this_mon.exit();
		}
		*/

		if ( ip_filter.isEnabled()){

				// don't need to synchronize access to the bloom filter as it works fine
				// without protection (especially as its add only)

			InetAddress ia = contact.getTransportAddress().getAddress();

			if ( ia != null ){

					// allow unresolved addresses through (e.g. ipv6 seed) as handled later

				byte[]	addr = ia.getAddress();

				if ( bad_ip_bloom_filter == null ){

					bad_ip_bloom_filter = BloomFilterFactory.createAddOnly( BAD_IP_BLOOM_FILTER_SIZE );

				}else{

					if ( bad_ip_bloom_filter.contains( addr )){

						throw( new DHTUDPPacketHandlerException( "IPFilter check fails (repeat)" ));
					}
				}

				if ( ip_filter.isInRange(
						contact.getTransportAddress().getAddress(), "DHT", null,
						logger.isEnabled( DHTLogger.LT_IP_FILTER ))){

						// don't let an attacker deliberately fill up our filter so we start
						// rejecting valid addresses

					if ( bad_ip_bloom_filter.getEntryCount() >= BAD_IP_BLOOM_FILTER_SIZE/10 ){

						bad_ip_bloom_filter = BloomFilterFactory.createAddOnly( BAD_IP_BLOOM_FILTER_SIZE );
					}

					bad_ip_bloom_filter.add( addr );

					throw( new DHTUDPPacketHandlerException( "IPFilter check fails" ));
				}
			}
		}
	}

	protected void
	sendPing(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		long								timeout,
		int									priority )
	{
		try{
			checkAddress( contact );

			final long	connection_id = getConnectionID();

			final DHTUDPPacketRequestPing	request =
				new DHTUDPPacketRequestPing( this, connection_id, local_contact, contact );

			requestAltContacts( request );

			stats.pingSent( request );

			requestSendRequestProcessor( contact, request );

			packet_handler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver()
				{
					@Override
					public void
					packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	from_address,
						long				elapsed_time )
					{
						try{
							if ( packet.getConnectionId() != connection_id ){

								throw( new Exception( "connection id mismatch" ));
							}

							contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());

							requestSendReplyProcessor( contact, handler, packet, elapsed_time );

							DHTUDPPacketReplyPing reply = (DHTUDPPacketReplyPing)packet;
							
							receiveAltContacts( reply );

							DHTUDPUtils.receiveUploadStats( contact, reply.getUploadStats());
							
							stats.pingOK();

							long	proc_time = packet.getProcessingTime();

							if ( proc_time > 0 ){

								elapsed_time -= proc_time;

								if ( elapsed_time < 0 ){

									elapsed_time = 0;
								}
							}

							handler.pingReply( contact, (int)elapsed_time );

						}catch( DHTUDPPacketHandlerException e ){

							error( e );

						}catch( Throwable e ){

							Debug.printStackTrace(e);

							error( new DHTUDPPacketHandlerException( "ping failed", e ));
						}
					}

					@Override
					public void
					error(
						DHTUDPPacketHandlerException	e )
					{
						stats.pingFailed();

						handler.failed( contact,e );
					}
				},
				timeout, priority );

		}catch( Throwable e ){

			stats.pingFailed();

			handler.failed( contact,e );
		}
	}

	protected void
	sendPing(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler )
	{
		sendPing( contact, handler, request_timeout, PRUDPPacketHandler.PRIORITY_MEDIUM );
	}

	protected void
	sendImmediatePing(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		long								timeout )
	{
		sendPing( contact, handler, timeout, PRUDPPacketHandler.PRIORITY_IMMEDIATE );
	}

	protected void
	sendKeyBlockRequest(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		byte[]								block_request,
		byte[]								block_signature )
	{
		try{
			checkAddress( contact );

			final long	connection_id = getConnectionID();

			final DHTUDPPacketRequestKeyBlock	request =
				new DHTUDPPacketRequestKeyBlock( this, connection_id, local_contact, contact );

			request.setKeyBlockDetails( block_request, block_signature );

			stats.keyBlockSent( request );

			request.setRandomID( contact.getRandomID());

			requestSendRequestProcessor( contact, request );

			packet_handler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver()
				{
					@Override
					public void
					packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	from_address,
						long				elapsed_time )
					{
						try{
							if ( packet.getConnectionId() != connection_id ){

								throw( new Exception( "connection id mismatch" ));
							}

							contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());

							requestSendReplyProcessor( contact, handler, packet, elapsed_time );

							stats.keyBlockOK();

							handler.keyBlockReply( contact );

						}catch( DHTUDPPacketHandlerException e ){

							error( e );

						}catch( Throwable e ){

							Debug.printStackTrace(e);

							error( new DHTUDPPacketHandlerException( "send key block failed", e ));
						}
					}

					@Override
					public void
					error(
						DHTUDPPacketHandlerException	e )
					{
						stats.keyBlockFailed();

						handler.failed( contact,e );
					}
				},
				request_timeout, PRUDPPacketHandler.PRIORITY_MEDIUM );

		}catch( Throwable e ){

			stats.keyBlockFailed();

			handler.failed( contact,e );
		}
	}

		// stats

	protected void
	sendStats(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler )
	{
		try{
			checkAddress( contact );

			final long	connection_id = getConnectionID();

			final DHTUDPPacketRequestStats	request =
				new DHTUDPPacketRequestStats( this, connection_id, local_contact, contact );

			// request.setStatsType( DHTUDPPacketRequestStats.STATS_TYPE_NP_VER2 );

			stats.statsSent( request );

			requestSendRequestProcessor( contact, request );

			packet_handler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver()
				{
					@Override
					public void
					packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	from_address,
						long				elapsed_time )
					{
						try{
							if ( packet.getConnectionId() != connection_id ){

								throw( new Exception( "connection id mismatch" ));
							}

							contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());

							requestSendReplyProcessor( contact, handler, packet, elapsed_time );

							DHTUDPPacketReplyStats	reply = (DHTUDPPacketReplyStats)packet;

							stats.statsOK();

							if ( reply.getStatsType() == DHTUDPPacketRequestStats.STATS_TYPE_ORIGINAL ){

								handler.statsReply( contact, reply.getOriginalStats());
							}else{

								// currently no handler for new stats

								System.out.println( "new stats reply:" + reply.getString());
							}

						}catch( DHTUDPPacketHandlerException e ){

							error( e );

						}catch( Throwable e ){

							Debug.printStackTrace(e);

							error( new DHTUDPPacketHandlerException( "stats failed", e ));
						}
					}

					@Override
					public void
					error(
						DHTUDPPacketHandlerException	e )
					{
						stats.statsFailed();

						handler.failed( contact, e );
					}
				},
				request_timeout, PRUDPPacketHandler.PRIORITY_LOW );

		}catch( Throwable e ){

			stats.statsFailed();

			handler.failed( contact, e );
		}
	}

		// PING for deducing external IP address

	protected InetSocketAddress
	askContactForExternalAddress(
		DHTTransportUDPContactImpl	contact )
	{
		try{
			checkAddress( contact );

			final long	connection_id = getConnectionID();

			final DHTUDPPacketRequestPing	request =
					new DHTUDPPacketRequestPing( this, connection_id, local_contact, contact );

			stats.pingSent( request );

			final AESemaphore	sem = new AESemaphore( "DHTTransUDP:extping" );

			final InetSocketAddress[]	result = new InetSocketAddress[1];

			packet_handler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver()
				{
					@Override
					public void
					packetReceived(
						DHTUDPPacketReply	_packet,
						InetSocketAddress	from_address,
						long				elapsed_time )
					{
						try{

							if ( _packet instanceof DHTUDPPacketReplyPing ){

								// ping was OK so current address is OK

								result[0] = local_contact.getExternalAddress();

							}else if ( _packet instanceof DHTUDPPacketReplyError ){

								DHTUDPPacketReplyError	packet = (DHTUDPPacketReplyError)_packet;

								if ( packet.getErrorType() == DHTUDPPacketReplyError.ET_ORIGINATOR_ADDRESS_WRONG ){

									result[0] = packet.getOriginatingAddress();
								}
							}
						}finally{

							sem.release();
						}
					}

					@Override
					public void
					error(
						DHTUDPPacketHandlerException	e )
					{
						try{
							stats.pingFailed();

						}finally{

							sem.release();
						}
					}
				},
				5000, PRUDPPacketHandler.PRIORITY_HIGH );

			sem.reserve( 5000 );

			return( result[0] );

		}catch( Throwable e ){

			stats.pingFailed();

			return( null );
		}
	}

		// STORE

	public void
	sendStore(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		byte[][]							keys,
		DHTTransportValue[][]				value_sets,
		int									priority )
	{
		final long	connection_id = getConnectionID();

		if ( false ){
			int	total_values = 0;
			for (int i=0;i<keys.length;i++){
				total_values += value_sets[i].length;
			}
			System.out.println( "store: keys = " + keys.length +", values = " + total_values );
		}

			// only report to caller the outcome of the first packet

		int	packet_count	= 0;

		try{
			checkAddress( contact );

			int		current_key_index	= 0;
			int		current_value_index	= 0;

			while( current_key_index < keys.length ){

				packet_count++;

				int	space = DHTUDPPacketHelper.PACKET_MAX_BYTES - DHTUDPPacketRequest.DHT_HEADER_SIZE;

				List	key_list	= new ArrayList();
				List	values_list	= new ArrayList();

				key_list.add( keys[current_key_index]);

				space -= ( keys[current_key_index].length + 1 );	// 1 for length marker

				values_list.add( new ArrayList());

				while( 	space > 0 &&
						current_key_index < keys.length ){

					if ( current_value_index == value_sets[current_key_index].length ){

							// all values from the current key have been processed

						current_key_index++;

						current_value_index	= 0;

						if ( key_list.size() == DHTUDPPacketRequestStore.MAX_KEYS_PER_PACKET ){

								// no more keys allowed in this packet

							break;
						}

						if ( current_key_index == keys.length ){

								// no more keys left, job done

							break;
						}

						key_list.add( keys[current_key_index]);

						space -= ( keys[current_key_index].length + 1 );	// 1 for length marker

						values_list.add( new ArrayList());
					}

					DHTTransportValue	value = value_sets[current_key_index][current_value_index];

					int	entry_size = DHTUDPUtils.DHTTRANSPORTVALUE_SIZE_WITHOUT_VALUE + value.getValue().length + 1;

					List	values = (List)values_list.get(values_list.size()-1);

					if ( 	space < entry_size ||
							values.size() == DHTUDPPacketRequestStore.MAX_VALUES_PER_KEY ){

							// no space left or we've used up our limit on the
							// number of values permitted per key

						break;
					}

					values.add( value );

					space -= entry_size;

					current_value_index++;
				}

				int	packet_entries = key_list.size();

				if ( packet_entries > 0 ){

						// if last entry has no values then ignore it

					if ( ((List)values_list.get( packet_entries-1)).size() == 0 ){

						packet_entries--;
					}
				}

				if ( packet_entries == 0 ){

					break;
				}

				byte[][]				packet_keys 		= new byte[packet_entries][];
				DHTTransportValue[][]	packet_value_sets 	= new DHTTransportValue[packet_entries][];

				//int	packet_value_count = 0;

				for (int i=0;i<packet_entries;i++){

					packet_keys[i] = (byte[])key_list.get(i);

					List	values = (List)values_list.get(i);

					packet_value_sets[i] = new DHTTransportValue[values.size()];

					for (int j=0;j<values.size();j++){

						packet_value_sets[i][j] = (DHTTransportValue)values.get(j);

						//packet_value_count++;
					}
				}

				// System.out.println( "    packet " + packet_count + ": keys = " + packet_entries + ", values = " + packet_value_count );


				final DHTUDPPacketRequestStore	request =
					new DHTUDPPacketRequestStore( this, connection_id, local_contact, contact );

				stats.storeSent( request );

				request.setRandomID( contact.getRandomID());

				request.setKeys( packet_keys );

				request.setValueSets( packet_value_sets );

				final int f_packet_count	= packet_count;

				requestSendRequestProcessor( contact, request );

				packet_handler.sendAndReceive(
					request,
					contact.getTransportAddress(),
					new DHTUDPPacketReceiver()
					{
						@Override
						public void
						packetReceived(
							DHTUDPPacketReply	packet,
							InetSocketAddress	from_address,
							long				elapsed_time )
						{
							try{
								if ( packet.getConnectionId() != connection_id ){

									throw( new Exception( "connection id mismatch: sender=" + from_address + ",packet=" + packet.getString()));
								}

								contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());						
								
								requestSendReplyProcessor( contact, handler, packet, elapsed_time );

								DHTUDPPacketReplyStore	reply = (DHTUDPPacketReplyStore)packet;

								DHTUDPUtils.receiveUploadStats( contact, reply.getUploadStats());

								stats.storeOK();

								if ( f_packet_count == 1 ){

									handler.storeReply( contact, reply.getDiversificationTypes());
								}

							}catch( DHTUDPPacketHandlerException e ){

								error( e );

							}catch( Throwable e ){

								Debug.printStackTrace(e);

								error( new DHTUDPPacketHandlerException( "store failed", e ));
							}
						}

						@Override
						public void
						error(
							DHTUDPPacketHandlerException	e )
						{
							stats.storeFailed();

							if ( f_packet_count == 1 ){

								handler.failed( contact, e );
							}
						}
					},
					store_timeout,
					priority );

			}
		}catch( Throwable e ){

			stats.storeFailed();

			if ( packet_count <= 1 ){

				handler.failed( contact, e );
			}
		}
	}

		// QUERY STORE

	public void
	sendQueryStore(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler 		handler,
		int									header_size,
		List<Object[]>						key_details )
	{
		try{
			checkAddress( contact );

			final long	connection_id = getConnectionID();

			Iterator<Object[]> it = key_details.iterator();

			byte[]				current_prefix			= null;
			Iterator<byte[]>	current_suffixes 		= null;

			List<DHTUDPPacketRequestQueryStorage> requests = new ArrayList<>();

outer:
			while( it.hasNext()){

				int	space = DHTUDPPacketRequestQueryStorage.SPACE;

				DHTUDPPacketRequestQueryStorage	request =
					new DHTUDPPacketRequestQueryStorage( this, connection_id, local_contact, contact );

				List<Object[]> packet_key_details = new ArrayList<>();

				while( space > 0 && it.hasNext()){

					if ( current_prefix == null ){

						Object[] entry = it.next();

						current_prefix = (byte[])entry[0];

						List<byte[]> l = (List<byte[]>)entry[1];

						current_suffixes = l.iterator();
					}

					if ( current_suffixes.hasNext()){

						int	min_space = header_size + 3;	// 1 byte prefix len, 2 byte num suffix

						if ( space < min_space ){

							request.setDetails( header_size, packet_key_details );

							requests.add( request );

							continue outer ;
						}

						List<byte[]> s = new ArrayList<>();

						packet_key_details.add( new Object[]{ current_prefix, s });

						int	prefix_size = current_prefix.length;
						int	suffix_size = header_size - prefix_size;

						space -= ( 3 + prefix_size );

						while( space >= suffix_size && current_suffixes.hasNext()){

							s.add( current_suffixes.next());
						}

					}else{

						current_prefix = null;
					}
				}

				if ( !it.hasNext()){

					request.setDetails( header_size, packet_key_details );

					requests.add( request );
				}
			}

			final Object[] replies = new Object[ requests.size() ];

			for ( int i=0;i<requests.size();i++){

				DHTUDPPacketRequestQueryStorage request = requests.get(i);

				final int f_i = i;

				stats.queryStoreSent( request );

				requestSendRequestProcessor( contact, request );

				packet_handler.sendAndReceive(
					request,
					contact.getTransportAddress(),
					new DHTUDPPacketReceiver()
					{
						@Override
						public void
						packetReceived(
							DHTUDPPacketReply	packet,
							InetSocketAddress	from_address,
							long				elapsed_time )
						{
							try{
								if ( packet.getConnectionId() != connection_id ){

									throw( new Exception( "connection id mismatch" ));
								}

								contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());

								requestSendReplyProcessor( contact, handler, packet, elapsed_time );

								DHTUDPPacketReplyQueryStorage	reply = (DHTUDPPacketReplyQueryStorage)packet;

									// copy out the random id in preparation for a possible subsequent
									// store operation

								contact.setRandomID( reply.getRandomID());

								stats.queryStoreOK();

								synchronized( replies ){

									replies[f_i] = reply;

									checkComplete();
								}

							}catch( DHTUDPPacketHandlerException e ){

								error( e );

							}catch( Throwable e ){

								Debug.printStackTrace(e);

								error( new DHTUDPPacketHandlerException( "queryStore failed", e ));
							}
						}

						@Override
						public void
						error(
							DHTUDPPacketHandlerException	e )
						{
							stats.queryStoreFailed();

							synchronized( replies ){

								replies[f_i] = e;

								checkComplete();
							}
						}

						protected void
						checkComplete()
						{
							DHTUDPPacketHandlerException last_error = null;

							for ( int i=0;i<replies.length;i++ ){

								Object o = replies[i];

								if ( o == null ){

									return;
								}

								if ( o instanceof DHTUDPPacketHandlerException ){

									last_error = (DHTUDPPacketHandlerException)o;
								}
							}

							if ( last_error != null ){

								handler.failed( contact, last_error );

							}else{

								if ( replies.length == 1 ){

									handler.queryStoreReply( contact, ((DHTUDPPacketReplyQueryStorage)replies[0]).getResponse());

								}else{

									List<byte[]> response = new ArrayList<>();

									for ( int i=0;i<replies.length;i++ ){

										response.addAll(((DHTUDPPacketReplyQueryStorage)replies[0]).getResponse());
									}

									handler.queryStoreReply( contact, response );
								}
							}
						}
					},
					request_timeout, PRUDPPacketHandler.PRIORITY_MEDIUM );
			}

		}catch( Throwable e ){

			stats.queryStoreFailed();

			handler.failed( contact, e );
		}
	}

		// FIND NODE

	public void
	sendFindNode(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		byte[]								nid )
	{
		try{
			checkAddress( contact );

			final long	connection_id = getConnectionID();

			final DHTUDPPacketRequestFindNode	request =
				new DHTUDPPacketRequestFindNode( this, connection_id, local_contact, contact );

			stats.findNodeSent( request );

			request.setID( nid );

			request.setNodeStatus( getNodeStatus());

			request.setEstimatedDHTSize( request_handler.getTransportEstimatedDHTSize());

			requestSendRequestProcessor( contact, request );

			packet_handler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver()
				{
					@Override
					public void
					packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	from_address,
						long				elapsed_time )
					{
						try{
							if ( packet.getConnectionId() != connection_id ){

								throw( new Exception( "connection id mismatch" ));
							}

							contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());

							requestSendReplyProcessor( contact, handler, packet, elapsed_time );

							DHTUDPPacketReplyFindNode	reply = (DHTUDPPacketReplyFindNode)packet;

								// copy out the random id in preparation for a possible subsequent
								// store operation

							contact.setRandomID( reply.getRandomID());

							updateContactStatus( contact, reply.getNodeStatus(), false );

							request_handler.setTransportEstimatedDHTSize( reply.getEstimatedDHTSize());

							stats.findNodeOK();

							DHTTransportContact[] contacts = reply.getContacts();

								// scavenge any contacts here to help bootstrap process
								// when ip wrong and no import history

							try{
								this_mon.enter();

								for (int i=0; contact_history.size() < CONTACT_HISTORY_MAX && i<contacts.length;i++){

									DHTTransportUDPContact c = (DHTTransportUDPContact)contacts[i];

										contact_history.put( c.getTransportAddress(), c );
								}
							}finally{

								this_mon.exit();
							}

							handler.findNodeReply( contact, contacts );

						}catch( DHTUDPPacketHandlerException e ){

							error( e );

						}catch( Throwable e ){

							Debug.printStackTrace(e);

							error( new DHTUDPPacketHandlerException( "findNode failed", e ));
						}
					}

					@Override
					public void
					error(
						DHTUDPPacketHandlerException	e )
					{
						stats.findNodeFailed();

						handler.failed( contact, e );
					}
				},
				request_timeout, PRUDPPacketHandler.PRIORITY_MEDIUM );

		}catch( Throwable e ){

			stats.findNodeFailed();

			handler.failed( contact, e );
		}
	}

		// FIND VALUE

	public void
	sendFindValue(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		byte[]								key,
		int									max_values,
		short								flags )
	{
		try{
			checkAddress( contact );

			final long	connection_id = getConnectionID();

			final DHTUDPPacketRequestFindValue	request =
				new DHTUDPPacketRequestFindValue( this, connection_id, local_contact, contact );

			stats.findValueSent( request );

			request.setID( key );

			request.setMaximumValues( max_values );

			request.setFlags((byte)flags );

			requestSendRequestProcessor( contact, request );

			packet_handler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver()
				{
					@Override
					public void
					packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	from_address,
						long				elapsed_time )
					{
						try{
							if ( packet.getConnectionId() != connection_id ){

								throw( new Exception( "connection id mismatch" ));
							}

							contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());

							requestSendReplyProcessor( contact, handler, packet, elapsed_time );

							DHTUDPPacketReplyFindValue	reply = (DHTUDPPacketReplyFindValue)packet;

							stats.findValueOK();

							DHTTransportValue[]	res = reply.getValues();

							if ( res != null ){

								boolean	continuation = reply.hasContinuation();

								handler.findValueReply( contact, res, reply.getDiversificationType(), continuation);

							}else{

								handler.findValueReply( contact, reply.getContacts());
							}
						}catch( DHTUDPPacketHandlerException e ){

							error( e );

						}catch( Throwable e ){

							Debug.printStackTrace(e);

							error( new DHTUDPPacketHandlerException( "findValue failed", e ));
						}
					}

					@Override
					public void
					error(
						DHTUDPPacketHandlerException	e )
					{
						stats.findValueFailed();

						handler.failed( contact, e );
					}
				},
				request_timeout, PRUDPPacketHandler.PRIORITY_HIGH );

		}catch( Throwable e ){

			if ( !(e instanceof DHTUDPPacketHandlerException )){

				stats.findValueFailed();

				handler.failed( contact, e );
			}
		}
	}

	protected DHTTransportFullStats
	getFullStats(
		DHTTransportUDPContactImpl	contact )
	{
		if ( contact == local_contact ){

			return( request_handler.statsRequest( contact ));
		}

		final DHTTransportFullStats[] res = { null };

		final AESemaphore	sem = new AESemaphore( "DHTTransportUDP:getFullStats");

		sendStats(	contact,
					new DHTTransportReplyHandlerAdapter()
					{
						@Override
						public void
						statsReply(
							DHTTransportContact 	_contact,
							DHTTransportFullStats	_stats )
						{
							res[0]	= _stats;

							sem.release();
						}

						@Override
						public void
						failed(
							DHTTransportContact 	_contact,
							Throwable				_error )
						{
							sem.release();
						}

					});

		sem.reserve();

		return( res[0] );
	}

	@Override
	public void
	registerTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler )
	{
		xfer_handler.registerTransferHandler(handler_key, handler);
	}

	@Override
	public void
	registerTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler,
		Map<String,Object>			options )
	{
		xfer_handler.registerTransferHandler( handler_key, handler, options );
	}

	@Override
	public void
	unregisterTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler )
	{
		xfer_handler.unregisterTransferHandler(handler_key, handler);
	}

	@Override
	public byte[]
	readTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handler_key,
		byte[]							key,
		long							timeout )

		throws DHTTransportException
	{
		InetAddress ia = target.getAddress().getAddress();

		if ( 	( ia instanceof Inet4Address && v6  ) ||
				( ia instanceof Inet6Address && !v6 )){

			throw( new DHTTransportException( "Incompatible address" ));
		}

		return( xfer_handler.readTransfer( listener, target, handler_key, key, timeout ));
	}

	@Override
	public void
	writeTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handler_key,
		byte[]							key,
		byte[]							data,
		long							timeout )

		throws DHTTransportException
	{
		InetAddress ia = target.getAddress().getAddress();

		if ( 	( ia instanceof Inet4Address && v6  ) ||
				( ia instanceof Inet6Address && !v6 )){

			throw( new DHTTransportException( "Incompatible address" ));
		}

		xfer_handler.writeTransfer(listener, target, handler_key, key, data, timeout);
	}

	@Override
	public byte[]
	writeReadTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handler_key,
		byte[]							data,
		long							timeout )

		throws DHTTransportException
	{
		InetAddress ia = target.getAddress().getAddress();

		if ( 	( ia instanceof Inet4Address && v6  ) ||
				( ia instanceof Inet6Address && !v6 )){

			throw( new DHTTransportException( "Incompatible address" ));
		}

		return( xfer_handler.writeReadTransfer( listener, target, handler_key, data, timeout ));
	}

	protected void
	dataRequest(
		final DHTTransportUDPContactImpl	originator,
		final DHTUDPPacketData				req )
	{
		stats.dataReceived();

		xfer_handler.receivePacket(
			originator,
			new DHTTransferHandler.Packet(
				req.getConnectionId(),
				req.getPacketType(),
				req.getTransferKey(),
				req.getRequestKey(),
				req.getData(),
				req.getStartPosition(),
				req.getLength(),
				req.getTotalLength()));
	}

	@Override
	public void
	process(
		DHTUDPPacketRequest	request,
		boolean				alien )
	{
		process( packet_handler, request, alien  );
	}

	@Override
	public void
	process(
		DHTUDPPacketHandlerStub		packet_handler_stub,
		DHTUDPPacketRequest			request,
		boolean						alien )
	{
		if ( request_handler == null ){

			logger.log( "Ignoring packet as not yet ready to process" );

			return;
		}

		try{
			stats.incomingRequestReceived( request, alien );

			InetSocketAddress	transport_address = request.getAddress();

			DHTTransportUDPContactImpl	originating_contact =
				new DHTTransportUDPContactImpl(
						false,
						this,
						transport_address,
						request.getOriginatorAddress(),
						request.getOriginatorVersion(),
						request.getOriginatorInstanceID(),
						request.getClockSkew(),
						request.getGenericFlags());

			try{
				checkAddress( originating_contact );

			}catch( DHTUDPPacketHandlerException e ){

				return;
			}

			requestReceiveRequestProcessor( originating_contact, request );

			boolean	bad_originator = !originating_contact.addressMatchesID();

				// bootstrap node returns details regardless of whether the originator ID matches
				// as the details will help the sender discover their correct ID (hopefully)

			if ( bad_originator && !bootstrap_node ){

				String	contact_string = originating_contact.getString();

				if ( recent_reports.get(contact_string) == null ){

					recent_reports.put( contact_string, "" );

					logger.log( "Node " + contact_string + " has incorrect ID, reporting it to them" );
				}

				DHTUDPPacketReplyError	reply =
					new DHTUDPPacketReplyError(
							this,
							request,
							local_contact,
							originating_contact );

				reply.setErrorType( DHTUDPPacketReplyError.ET_ORIGINATOR_ADDRESS_WRONG );

				reply.setOriginatingAddress( originating_contact.getTransportAddress());

				requestReceiveReplyProcessor( originating_contact, reply );

				packet_handler_stub.send( reply, request.getAddress());

			}else{

				if ( bad_originator ){

						// we need to patch the originator up otherwise we'll be populating our
						// routing table with crap

					originating_contact =
						new DHTTransportUDPContactImpl(
								false,
								this,
								transport_address,
								transport_address, 		// set originator address to transport
								request.getOriginatorVersion(),
								request.getOriginatorInstanceID(),
								request.getClockSkew(),
								request.getGenericFlags());

				}else{

					contactAlive( originating_contact );
				}

				if ( request instanceof DHTUDPPacketRequestPing ){

					if ( !bootstrap_node ){

						request_handler.pingRequest( originating_contact );

						DHTUDPPacketRequestPing ping = (DHTUDPPacketRequestPing)request;

						DHTUDPUtils.receiveUploadStats( originating_contact, ping.getUploadStats());

						DHTUDPPacketReplyPing	reply =
							new DHTUDPPacketReplyPing(
									this,
									ping,
									local_contact,
									originating_contact );

						sendAltContacts( ping, reply );
						
						requestReceiveReplyProcessor( originating_contact, reply );

						packet_handler_stub.send( reply, request.getAddress());
					}
				}else if ( request instanceof DHTUDPPacketRequestKeyBlock ){

					if ( !bootstrap_node ){

						DHTUDPPacketRequestKeyBlock	kb_request = (DHTUDPPacketRequestKeyBlock)request;

						originating_contact.setRandomID( kb_request.getRandomID());

						request_handler.keyBlockRequest(
								originating_contact,
								kb_request.getKeyBlockRequest(),
								kb_request.getKeyBlockSignature());

						DHTUDPPacketReplyKeyBlock	reply =
							new DHTUDPPacketReplyKeyBlock(
									this,
									kb_request,
									local_contact,
									originating_contact );

						requestReceiveReplyProcessor( originating_contact, reply );

						packet_handler_stub.send( reply, request.getAddress());
					}
				}else if ( request instanceof DHTUDPPacketRequestStats ){

					DHTUDPPacketRequestStats	stats_request = (DHTUDPPacketRequestStats)request;

					DHTUDPPacketReplyStats	reply =
						new DHTUDPPacketReplyStats(
								this,
								stats_request,
								local_contact,
								originating_contact );

					int	type = stats_request.getStatsType();

					if ( type == DHTUDPPacketRequestStats.STATS_TYPE_ORIGINAL ){

						DHTTransportFullStats	full_stats = request_handler.statsRequest( originating_contact );

						reply.setOriginalStats( full_stats );

					}else if ( type == DHTUDPPacketRequestStats.STATS_TYPE_NP_VER2 ){

						DHTNetworkPositionProvider prov = DHTNetworkPositionManager.getProvider( DHTNetworkPosition.POSITION_TYPE_VIVALDI_V2 );

						byte[]	data = new byte[0];

						if ( prov != null ){

							ByteArrayOutputStream	baos = new ByteArrayOutputStream();

							DataOutputStream	dos = new DataOutputStream( baos );

							prov.serialiseStats( dos );

							dos.flush();

							data = baos.toByteArray();
						}

						reply.setNewStats( data, DHTNetworkPosition.POSITION_TYPE_VIVALDI_V2 );

					}else{

						throw( new IOException( "Uknown stats type '" + type + "'" ));
					}

					requestReceiveReplyProcessor( originating_contact, reply );

					packet_handler_stub.send( reply, request.getAddress());

				}else if ( request instanceof DHTUDPPacketRequestStore ){

					if ( !bootstrap_node ){

						DHTUDPPacketRequestStore	store_request = (DHTUDPPacketRequestStore)request;

						originating_contact.setRandomID( store_request.getRandomID());

						DHTTransportStoreReply	res =
							request_handler.storeRequest(
								originating_contact,
								store_request.getKeys(),
								store_request.getValueSets());

						if ( res.blocked()){

							if ( originating_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS ){

								DHTUDPPacketReplyError	reply =
									new DHTUDPPacketReplyError(
										this,
										request,
										local_contact,
										originating_contact );

								reply.setErrorType( DHTUDPPacketReplyError.ET_KEY_BLOCKED );

								reply.setKeyBlockDetails( res.getBlockRequest(), res.getBlockSignature() );

								requestReceiveReplyProcessor( originating_contact, reply );

								packet_handler_stub.send( reply, request.getAddress());
							}else{

								DHTUDPPacketReplyStore	reply =
									new DHTUDPPacketReplyStore(
											this,
											store_request,
											local_contact,
											originating_contact );

								reply.setDiversificationTypes( new byte[store_request.getKeys().length] );

								requestReceiveReplyProcessor( originating_contact, reply );

								packet_handler_stub.send( reply, request.getAddress());
							}
						}else{

							DHTUDPPacketReplyStore	reply =
								new DHTUDPPacketReplyStore(
										this,
										store_request,
										local_contact,
										originating_contact );

							reply.setDiversificationTypes( res.getDiversificationTypes());

							requestReceiveReplyProcessor( originating_contact, reply );

							packet_handler_stub.send( reply, request.getAddress());
						}
					}
				}else if ( request instanceof DHTUDPPacketRequestQueryStorage ){

					DHTUDPPacketRequestQueryStorage	query_request = (DHTUDPPacketRequestQueryStorage)request;

					DHTTransportQueryStoreReply	res =
						request_handler.queryStoreRequest(
									originating_contact,
									query_request.getHeaderLength(),
									query_request.getKeys());

					DHTUDPPacketReplyQueryStorage	reply =
						new DHTUDPPacketReplyQueryStorage(
								this,
								query_request,
								local_contact,
								originating_contact );

					reply.setRandomID( originating_contact.getRandomID());

					reply.setResponse( res.getHeaderSize(), res.getEntries());

					requestReceiveReplyProcessor( originating_contact, reply );

					packet_handler_stub.send( reply, request.getAddress());

				}else if ( request instanceof DHTUDPPacketRequestFindNode ){

					DHTUDPPacketRequestFindNode	find_request = (DHTUDPPacketRequestFindNode)request;

					boolean	acceptable;

						// as a bootstrap node we only accept find-node requests for the originator's
						// ID

					if ( bootstrap_node ){

							// log( originating_contact );

							// let bad originators through to aid bootstrapping with bad IP

						acceptable = bad_originator || Arrays.equals( find_request.getID(), originating_contact.getID());

					}else{

						acceptable	= true;
					}

					if ( acceptable ){

						if ( find_request.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_MORE_NODE_STATUS ){

							updateContactStatus( originating_contact, find_request.getNodeStatus(), true );

							request_handler.setTransportEstimatedDHTSize( find_request.getEstimatedDHTSize());
						}

						DHTUDPUtils.receiveUploadStats( originating_contact, find_request.getUploadStats());

						DHTTransportContact[]	res =
							request_handler.findNodeRequest(
										originating_contact,
										find_request.getID());

						DHTUDPPacketReplyFindNode	reply =
							new DHTUDPPacketReplyFindNode(
									this,
									find_request,
									local_contact,
									originating_contact );

						reply.setRandomID( originating_contact.getRandomID());

						reply.setNodeStatus( getNodeStatus());

						reply.setEstimatedDHTSize( request_handler.getTransportEstimatedDHTSize());

						reply.setContacts( res );

						requestReceiveReplyProcessor( originating_contact, reply );

						packet_handler_stub.send( reply, request.getAddress());
					}

				}else if ( request instanceof DHTUDPPacketRequestFindValue ){

					if ( !bootstrap_node ){

						DHTUDPPacketRequestFindValue	find_request = (DHTUDPPacketRequestFindValue)request;

						DHTTransportFindValueReply res =
							request_handler.findValueRequest(
										originating_contact,
										find_request.getID(),
										find_request.getMaximumValues(),
										find_request.getFlags());

						if ( res.blocked()){

							if ( originating_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS ){

								DHTUDPPacketReplyError	reply =
									new DHTUDPPacketReplyError(
										this,
										request,
										local_contact,
										originating_contact );

								reply.setErrorType( DHTUDPPacketReplyError.ET_KEY_BLOCKED );

								reply.setKeyBlockDetails( res.getBlockedKey(), res.getBlockedSignature() );

								requestReceiveReplyProcessor( originating_contact, reply );

								packet_handler_stub.send( reply, request.getAddress());

							}else{

								DHTUDPPacketReplyFindValue	reply =
									new DHTUDPPacketReplyFindValue(
										this,
										find_request,
										local_contact,
										originating_contact );

								reply.setValues( new DHTTransportValue[0], DHT.DT_NONE, false );

								requestReceiveReplyProcessor( originating_contact, reply );

								packet_handler_stub.send( reply, request.getAddress());
							}

						}else{
							DHTUDPPacketReplyFindValue	reply =
								new DHTUDPPacketReplyFindValue(
									this,
									find_request,
									local_contact,
									originating_contact );

							if ( res.hit()){

								DHTTransportValue[]	res_values = res.getValues();

								int		max_size = DHTUDPPacketHelper.PACKET_MAX_BYTES - DHTUDPPacketReplyFindValue.DHT_FIND_VALUE_HEADER_SIZE;

								List	values 		= new ArrayList();
								int		values_size	= 0;

								int	pos = 0;

								while( pos < res_values.length ){

									DHTTransportValue	v = res_values[pos];

									int	v_len = v.getValue().length + DHTUDPPacketReplyFindValue.DHT_FIND_VALUE_TV_HEADER_SIZE;

									if ( 	values_size > 0 && // if value too big, cram it in anyway
											values_size + v_len > max_size ){

											// won't fit, send what we've got

										DHTTransportValue[]	x = new DHTTransportValue[values.size()];

										values.toArray( x );

										reply.setValues( x, res.getDiversificationType(), true );	// continuation = true

										packet_handler_stub.send( reply, request.getAddress());

										values_size	= 0;

										values		= new ArrayList();

									}else{

										values.add(v);

										values_size	+= v_len;

										pos++;
									}
								}

									// send the remaining (possible zero length) non-continuation values

								DHTTransportValue[]	x = new DHTTransportValue[values.size()];

								values.toArray( x );

								reply.setValues( x, res.getDiversificationType(), false );

								requestReceiveReplyProcessor( originating_contact, reply );

								packet_handler_stub.send( reply, request.getAddress());

							}else{

								reply.setContacts(res.getContacts());

								requestReceiveReplyProcessor( originating_contact, reply );

								packet_handler_stub.send( reply, request.getAddress());
							}
						}
					}
				}else if ( request instanceof DHTUDPPacketData ){

					if ( !bootstrap_node ){

						dataRequest(originating_contact, (DHTUDPPacketData)request );
					}
				}else{

					Debug.out( "Unexpected packet:" + request.toString());
				}
			}
		}catch( DHTUDPPacketHandlerException e ){

			// not interesting, send packet fail or something

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

		// the _state networks are populated via ping requests to other peers

	private final Map<Integer, DHTTransportAlternativeNetworkImpl>	alt_net_states 		= new HashMap<>();

		// the _providers represent a local source of contacts that are used as a primary
		// source of contacts for replying to other peers ping requests

	private volatile Map<Integer, DHTTransportAlternativeNetwork>		alt_net_providers	= new HashMap<>();

	private final Object	alt_net_providers_lock = new Object();

	{
		for ( Integer net: DHTTransportAlternativeNetwork.AT_ALL_PUB ){

			alt_net_states.put( net, new DHTTransportAlternativeNetworkImpl( net ));
		}
	}

	@Override
	public DHTTransportAlternativeNetwork
	getAlternativeNetwork(
		int		network_type )
	{
		return( alt_net_states.get( network_type ));
	}

	@Override
	public void
	registerAlternativeNetwork(
		DHTTransportAlternativeNetwork		network )
	{
		synchronized( alt_net_providers_lock ){

			Map<Integer, DHTTransportAlternativeNetwork> new_providers = new HashMap<>(alt_net_providers);

			new_providers.put( network.getNetworkType(), network );

			alt_net_providers = new_providers;
		}
	}

	@Override
	public void
	unregisterAlternativeNetwork(
		DHTTransportAlternativeNetwork		network )
	{
		synchronized( alt_net_providers_lock ){

			Map<Integer, DHTTransportAlternativeNetwork> new_providers = new HashMap<>(alt_net_providers);

			Iterator< Map.Entry<Integer, DHTTransportAlternativeNetwork>> it = new_providers.entrySet().iterator();

			while( it.hasNext()){

				if ( it.next().getValue() == network ){

					it.remove();
				}
			}

			alt_net_providers = new_providers;
		}
	}

	void
	checkAltContacts()
	{
		int	total_required = 0;

		for ( DHTTransportAlternativeNetworkImpl net: alt_net_states.values()){

			total_required += net.getRequiredContactCount( true );
		}

		if ( total_required > 0 ){

			List<DHTTransportContact> targets = new ArrayList<>(ROUTABLE_CONTACT_HISTORY_MAX);

			try{
				this_mon.enter();

				for ( DHTTransportContact contact: routable_contact_history.values()){

					if ( contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS ){

						targets.add( contact );
					}
				}

			}finally{

				this_mon.exit();
			}

			if ( targets.size() > 0 ){

				targets.get( RandomUtils.nextInt( targets.size())).sendPing(
					new DHTTransportReplyHandlerAdapter()
					{
						@Override
						public void
						pingReply(
							DHTTransportContact _contact )
						{

						}

						@Override
						public void
						failed(
							DHTTransportContact 	_contact,
							Throwable				_error )
						{

						}
					});
			}
		}
	}

	private void
	sendAltContacts(
		DHTUDPPacketRequestPing		request,
		DHTUDPPacketReplyPing		reply )
	{
		if ( request.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS ){

			int[]	alt_nets 	= request.getAltNetworks();
			int[] 	counts 		= request.getAltNetworkCounts();

			if ( alt_nets.length > 0 ){

				List<DHTTransportAlternativeContact>	alt_contacts = new ArrayList<>();

				Map<Integer, DHTTransportAlternativeNetwork> providers = alt_net_providers;

				for ( int i=0; i<alt_nets.length;i++){

					int	count = counts[i];

					if ( count == 0 ){

						continue;
					}

					int	net = alt_nets[i];

					DHTTransportAlternativeNetworkImpl local = alt_net_states.get( net );

					if ( local == null ){

						continue;
					}

					int wanted = local.getRequiredContactCount( false );

					if ( wanted > 0 ){

						DHTTransportAlternativeNetwork provider = providers.get( net );

						if ( provider != null ){

							local.addContactsForSend( provider.getContacts( wanted ));
						}
					}

						// need to limit response for large serialisations

					if ( net == DHTTransportAlternativeNetwork.AT_I2P || net == DHTTransportAlternativeNetwork.AT_TOR ){

						count = Math.min( 2, count );
					}

					alt_contacts.addAll( local.getContacts( count, true ));
				}

				if ( alt_contacts.size() > 0 ){

					reply.setAltContacts( alt_contacts );
				}
			}
		}
	}

	private void
	requestAltContacts(
		DHTUDPPacketRequestPing		request )
	{
		if ( request.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS ){

				// see if we could do with any more alt contacts

			List<int[]> wanted = null;

			for ( DHTTransportAlternativeNetworkImpl net: alt_net_states.values()){

				int req = net.getRequiredContactCount( false );

				if ( req > 0 ){

					int net_type = net.getNetworkType();

					if ( net_type == DHTTransportAlternativeNetwork.AT_I2P || net_type == DHTTransportAlternativeNetwork.AT_TOR ){

						req = Math.min( 2, req );
					}

					if ( wanted == null ){

						wanted = new ArrayList<>(alt_net_states.size());
					}

					wanted.add( new int[]{ net_type, req } );
				}
			}

			if ( wanted != null ){

				int[] networks 	= new int[wanted.size()];
				int[] counts	= new int[networks.length];

				for ( int i=0;i<networks.length;i++ ){

					int[] 	entry = wanted.get( i );

					networks[i] = entry[0];
					counts[i]	= entry[1];
				}

					// doesn't matter how many we request in total, the reply will be
					// limited to whatever the max is

				request.setAltContactRequest( networks, counts );
			}
		}
	}

	void
	receiveAltContacts(
		DHTUDPPacketReplyPing		reply )
	{
		if ( reply.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS ){

			for ( DHTTransportAlternativeContact contact: reply.getAltContacts()){

				DHTTransportAlternativeNetworkImpl net = alt_net_states.get( contact.getNetworkType());

				if ( net != null ){

					net.addContactFromReply( contact );
				}
			}
		}
	}

	protected void
	requestReceiveRequestProcessor(
		DHTTransportUDPContactImpl	contact,
		DHTUDPPacketRequest			request )
	{
		// called when request received
	}

	protected void
	requestReceiveReplyProcessor(
		DHTTransportUDPContactImpl	contact,
		DHTUDPPacketReply			reply )
	{
		// called before sending reply to request

		int	action = reply.getAction();

		if ( 	action == DHTUDPPacketHelper.ACT_REPLY_PING ||
				action == DHTUDPPacketHelper.ACT_REPLY_FIND_NODE ||
				action == DHTUDPPacketHelper.ACT_REPLY_FIND_VALUE ){

			reply.setNetworkPositions( local_contact.getNetworkPositions());
		}
	}

	protected void
	requestSendRequestProcessor(
		DHTTransportUDPContactImpl	contact,
		DHTUDPPacketRequest 		request )
	{
		// called before sending request

	}

		/**
		 * Returns false if this isn't an error reply, true if it is and a retry can be
		 * performed, throws an exception otherwise
		 * @param reply
		 * @return
		 */

	protected void
	requestSendReplyProcessor(
		DHTTransportUDPContactImpl	remote_contact,
		DHTTransportReplyHandler	handler,
		DHTUDPPacketReply			reply,
		long						elapsed_time )

		throws DHTUDPPacketHandlerException
	{
			// called after receiving reply to request

		// System.out.println( "request:" + contact.getAddress() + " = " + elapsed_time );

		DHTNetworkPosition[]	remote_nps = reply.getNetworkPositions();

		if ( remote_nps != null ){

			long	proc_time = reply.getProcessingTime();

			if ( proc_time > 0 ){

				//System.out.println( elapsed_time + "/" + proc_time );

				long rtt = elapsed_time - proc_time;

				if ( rtt < 0 ){

					rtt = 0;
				}

					// save current position of target

				remote_contact.setNetworkPositions( remote_nps );

					// update local positions

				DHTNetworkPositionManager.update( local_contact.getNetworkPositions(), remote_contact.getID(), remote_nps, (float)rtt );
			}
		}

		remote_contact.setGenericFlags( reply.getGenericFlags());

		if ( reply.getAction() == DHTUDPPacketHelper.ACT_REPLY_ERROR ){

			DHTUDPPacketReplyError	error = (DHTUDPPacketReplyError)reply;

			switch( error.getErrorType()){

				case DHTUDPPacketReplyError.ET_ORIGINATOR_ADDRESS_WRONG:
				{
					try{
						externalAddressChange( remote_contact, error.getOriginatingAddress(), false );

					}catch( DHTTransportException e ){

						Debug.printStackTrace(e);
					}

					throw( new DHTUDPPacketHandlerException( "address changed notification" ));
				}
				case DHTUDPPacketReplyError.ET_KEY_BLOCKED:
				{
					handler.keyBlockRequest( remote_contact, error.getKeyBlockRequest(), error.getKeyBlockSignature());

					contactAlive( remote_contact );

					throw( new DHTUDPPacketHandlerException( "key blocked" ));
				}
			}

			throw( new DHTUDPPacketHandlerException( "unknown error type " + error.getErrorType()));

		}else{

			contactAlive( remote_contact );
		}
	}

	protected long
	getConnectionID()
	{
			// unfortunately, to reuse the UDP port with the tracker protocol we
			// have to distinguish our connection ids by setting the MSB. This allows
			// the decode to work as there is no common header format for the request
			// and reply packets

			// note that tracker usage of UDP via this handler is only for outbound
			// messages, hence for that use a request will never be received by the
			// handler

		return( 0x8000000000000000L | random.nextLong());
	}

	@Override
	public boolean
	supportsStorage()
	{
		return( !bootstrap_node );
	}

	@Override
	public void
	addListener(
		DHTTransportListener	l )
	{
		listeners.add(l);

		if ( external_address != null ){

			l.currentAddress( external_address );
		}
	}

	@Override
	public void
	removeListener(
		DHTTransportListener	l )
	{
		listeners.remove(l);
	}


	/*
	private PrintWriter	contact_log;
	private int			contact_log_entries;
	private SimpleDateFormat	contact_log_format = new SimpleDateFormat( "dd/MM/yyyy HH:mm:ss");
	{
		contact_log_format.setTimeZone( TimeZone.getTimeZone( "UTC" ));
	}

	protected void
	log(
		DHTTransportUDPContactImpl		contact )
	{
		if ( network == DHT.NW_MAIN ){

			synchronized( this ){

				try{
					if ( contact_log == null ){

						contact_log = new PrintWriter( new FileWriter( new File( SystemProperties.getUserPath(), "contact_log" )));
					}

					contact_log_entries++;

					InetSocketAddress address = contact.getAddress();

					contact_log.println( contact_log_format.format( new Date()) + ", " + address.getAddress().getHostAddress() + ", " + address.getPort());

					if ( contact_log_entries % 1000 == 0 ){

						System.out.println( "contact-log: " + contact_log_entries );

						contact_log.flush();
					}

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}
	*/
}
