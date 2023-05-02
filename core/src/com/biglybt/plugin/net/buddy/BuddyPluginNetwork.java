/*
 * Created on Mar 19, 2008
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


package com.biglybt.plugin.net.buddy;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;


import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.*;
import com.biglybt.core.security.CryptoHandler;
import com.biglybt.core.security.CryptoManagerFactory;
import com.biglybt.core.security.CryptoManagerKeyListener;
import com.biglybt.core.security.CryptoManagerPasswordException;

import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.core.util.protocol.azplug.AZPluginConnection;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ddb.*;

import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.messaging.MessageException;
import com.biglybt.pif.messaging.MessageManager;
import com.biglybt.pif.messaging.generic.GenericMessageConnection;
import com.biglybt.pif.messaging.generic.GenericMessageHandler;
import com.biglybt.pif.messaging.generic.GenericMessageRegistration;
import com.biglybt.pif.messaging.generic.GenericMessageStartpoint;

import com.biglybt.pif.utils.*;
import com.biglybt.pif.utils.security.SEPublicKey;
import com.biglybt.pif.utils.security.SEPublicKeyLocator;
import com.biglybt.pif.utils.security.SESecurityManager;
import com.biglybt.plugin.magnet.MagnetPlugin;
import com.biglybt.plugin.magnet.MagnetPluginProgressListener;
import com.biglybt.util.MapUtils;

public class
BuddyPluginNetwork
{
	public static final int VERSION_INITIAL	= 1;
	public static final int VERSION_CHAT	= 2;
	public static final int VERSION_CURRENT	= VERSION_CHAT;


	public static final int MT_V3_CHAT		= 1;

	public static final int MAX_MESSAGE_SIZE	= 4*1024*1024;

	public static final int	SUBSYSTEM_INTERNAL	= 0;
	public static final int	SUBSYSTEM_AZ2		= 1;
	public static final int	SUBSYSTEM_AZ3		= 2;

	protected static final int	SUBSYSTEM_MSG_TYPE_BASE	= 1024;


	protected static final int RT_INTERNAL_REQUEST_PING		= 1;
	protected static final int RT_INTERNAL_REPLY_PING		= 2;
	protected static final int RT_INTERNAL_REQUEST_CLOSE	= 3;
	protected static final int RT_INTERNAL_REPLY_CLOSE		= 4;
	protected static final int RT_INTERNAL_FRAGMENT			= 5;

	protected static final boolean TRACE = false;

	private static final int	MAX_UNAUTH_BUDDIES	= 1024;

	private static final int	BUDDY_STATUS_CHECK_PERIOD_MIN	= 3*60*1000;
	private static final int	BUDDY_STATUS_CHECK_PERIOD_INC	= 1*60*1000;

	private static final int	TIMER_PERIOD = BuddyPlugin.TIMER_PERIOD;
	
	protected static final int	STATUS_REPUBLISH_PERIOD						= 10*60*1000;
	private static final int	STATUS_REPUBLISH_PERIOD_WHEN_DIVERSIFIED	= 60*60*1000;
	
	private static final int	STATUS_REPUBLISH_TICKS		= STATUS_REPUBLISH_PERIOD/TIMER_PERIOD;

	private static final int	CHECK_YGM_PERIOD			= 5*60*1000;
	private static final int	CHECK_YGM_TICKS				= CHECK_YGM_PERIOD/TIMER_PERIOD;
	private static final int	YGM_BLOOM_LIFE_PERIOD		= 60*60*1000;
	private static final int	YGM_BLOOM_LIFE_TICKS		= YGM_BLOOM_LIFE_PERIOD/TIMER_PERIOD;

	private static final int	SAVE_CONFIG_PERIOD			= 60*1000;
	private static final int	SAVE_CONFIG_TICKS			= SAVE_CONFIG_PERIOD/TIMER_PERIOD;

	public static final int		PERSISTENT_MSG_RETRY_PERIOD		= 5*60*1000;
	private static final int	PERSISTENT_MSG_CHECK_PERIOD		= 60*1000;
	private static final int	PERSISTENT_MSG_CHECK_TICKS		= PERSISTENT_MSG_CHECK_PERIOD/TIMER_PERIOD;

	private static final int	UNAUTH_BLOOM_RECREATE		= 120*1000;
	private static final int	UNAUTH_BLOOM_CHUNK			= 1000;

	private static final int	BLOOM_CHECK_PERIOD			= UNAUTH_BLOOM_RECREATE/2;
	private static final int	BLOOM_CHECK_TICKS			= BLOOM_CHECK_PERIOD/TIMER_PERIOD;

	public static final int STREAM_CRYPTO 	= MessageManager.STREAM_ENCRYPTION_RC4_REQUIRED;
	public static final int BLOCK_CRYPTO	= SESecurityManager.BLOCK_ENCRYPTION_AES;

	//public static final int STREAM_CRYPTO 	= MessageManager.STREAM_ENCRYPTION_NONE;
	//public static final int BLOCK_CRYPTO	= SESecurityManager.BLOCK_ENCRYPTION_NONE;

	private final PluginInterface		plugin_interface;
	private final BuddyPlugin			plugin;
	private final String				target_network;
		
	private final String				config_file_name;
	
	private boolean			ready_to_publish;
	
	private CopyOnWriteList<DDBDetails>	ddb_details = new CopyOnWriteList<>();
	
	private BloomFilter		unauth_bloom;
	private long			unauth_bloom_create_time;
	private BloomFilter	ygm_unauth_bloom;

	private CopyOnWriteList<BuddyPluginBuddyRequestListener>	request_listeners	= new CopyOnWriteList<>();

	private List<BuddyPluginBuddy>	buddies 	= new ArrayList<>();

	private List<BuddyPluginBuddy>	connected_at_close;

	private Map<String,BuddyPluginBuddy>		buddies_map	= new HashMap<>();
	
	private SESecurityManager	sec_man;
	private CryptoHandler 		ecc_handler;

	private GenericMessageRegistration	msg_registration;

	private boolean		config_dirty;

	private Random	random = RandomUtils.SECURE_RANDOM;

	private BuddyPluginAZ2		az2_handler;

	private Set<BuddyPluginBuddy>			pd_preinit		= new HashSet<>();

	private List<BuddyPluginBuddy>			pd_queue 		= new ArrayList<>();
	private AESemaphore						pd_queue_sem	= new AESemaphore( "BuddyPlugin:persistDispatch");
	private AEThread2						pd_thread;


	protected
	BuddyPluginNetwork(
		PluginInterface		_plugin_interface,
		BuddyPlugin			_plugin,
		String				_target_network )
	{
		plugin_interface 		= _plugin_interface;
		plugin					= _plugin;
		target_network			= _target_network;
		
		boolean is_pub = isPublicNetwork();
					
		config_file_name = is_pub?"friends.config":"friends_a.config";
		
		sec_man 		= plugin_interface.getUtilities().getSecurityManager();
		
		ecc_handler 	= CryptoManagerFactory.getSingleton().getECCHandler( is_pub?1:2 );
		
		az2_handler = new BuddyPluginAZ2( this );
	}

	protected BuddyPlugin
	getPlugin()
	{
		return( plugin );
	}
	
	public String
	getTargetNetwork()
	{
		return( target_network );
	}
	
	public boolean
	isPublicNetwork()
	{
		return( target_network == AENetworkClassifier.AT_PUBLIC );
	}
	
	public String[]
	getDDBNetworks()
	{
		if ( isPublicNetwork()){
			
			return( new String[]{ AENetworkClassifier.AT_PUBLIC, AENetworkClassifier.AT_I2P });
			
		}else{
			
			return(  new String[]{ AENetworkClassifier.AT_I2P });
		}
	}
	
	protected void
	checkAvailable()
	
		throws BuddyPluginException
	{
		plugin.checkAvailable();
	}

	public boolean
	getPeersAreLANLocal()
	{
		return( plugin.getPeersAreLANLocal());
	}
	
	protected boolean
	startup(
		String		initial_nick,
		int			initial_status, 
		boolean		initial_enabled )
	{
		try{

			List<DistributedDatabase> ddbs = plugin_interface.getUtilities().getDistributedDatabases( getDDBNetworks());
						
			for ( DistributedDatabase ddb: ddbs ){
			
				if ( ddb.isAvailable()){
					
					DDBDetails details = new DDBDetails( ddb );
					
					ddb_details.add( details );
				
					// pick up initial values before enabling

					ddb.addListener(
						new DistributedDatabaseListener()
						{
							@Override
							public void
							event(
								DistributedDatabaseEvent event )
							{
								if ( event.getType() == DistributedDatabaseEvent.ET_LOCAL_CONTACT_CHANGED ){

									updateIP();
								}
							}
						});
				}
			}
			
			updateIP();

			updateNickName( initial_nick );

			updateOnlineStatus( initial_status );

			COConfigurationManager.addAndFireParameterListeners(
					new String[]{
						"TCP.Listen.Port",
						"TCP.Listen.Port.Enable",
						"UDP.Listen.Port",
						"UDP.Listen.Port.Enable" },
					new com.biglybt.core.config.ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							String parameterName )
						{
							updateListenPorts();
						}
					});

			CryptoManagerFactory.getSingleton().addKeyListener(
				new CryptoManagerKeyListener()
				{
					@Override
					public void
					keyChanged(
						CryptoHandler handler )
					{
						for ( DDBDetails details: ddb_details ){

							details.updateKey();
						}
					}

					@Override
					public void
					keyLockStatusChanged(
						CryptoHandler		handler )
					{
						boolean unlocked = handler.isUnlocked();

						if ( unlocked ){

							for ( DDBDetails details: ddb_details ){
								
								details.updatePublish();
							}
							
						}else{

							new AEThread2( "BuddyPlugin:disc", true )
							{
								@Override
								public void
								run()
								{
									for ( BuddyPluginBuddy buddy: getAllBuddies()){

										buddy.disconnect();
									}
								}
							}.start();
						}
					}
				});

			ready_to_publish	= true;

			setClassicEnabledInternal( initial_enabled );

			checkBuddiesAndRepublish();

			return( true );
			
		}catch( Throwable e ){

			log( null, "Initialisation failed", e );

			return( false );
		}
	}
	
	protected void
	reconnect()
	{
			// try to re-establish connection to previously connectd buddies
	
		List<BuddyPluginBuddy> buddies = getBuddies();
	
		for ( BuddyPluginBuddy buddy: buddies ){

			if ( buddy.getIP() != null && !buddy.isConnected()){
	
				log( buddy, "Attempting reconnect to " + buddy.getString());
	
				buddy.sendKeepAlive();
			}
		}
	}
	
	protected void
	setClassicEnabledInternal(
		boolean		enabled )
	{
		for ( DDBDetails details: ddb_details ){
			
			details.setEnabled( enabled );
		}
	}
	
	public int
	getOnlineStatus()
	{
		List<DDBDetails> temp = ddb_details.getList();
		
		if ( temp.size() > 0 ){
			
			return( temp.get(0).getOnlineStatus());
		}
		
		return( BuddyPlugin.STATUS_ONLINE );
	}

	protected List<String>
	getProfileInfo()
	{
		return( plugin.getProfileInfo( isPublicNetwork()));
	}
	
	protected void
	fireAdded(
		BuddyPluginBuddy	buddy )
	{
		plugin.fireAdded(buddy);
	}
	
	protected void
	fireRemoved(
		BuddyPluginBuddy	buddy )
	{
		plugin.fireRemoved(buddy);
	}
	
	protected void
	fireDetailsChanged(
		BuddyPluginBuddy	buddy )
	{
		plugin.fireDetailsChanged(buddy);
	}

	protected void
	registerMessageHandler()
	{
		try{
			addRequestListener(
				new BuddyPluginBuddyRequestListener()
				{
					@Override
					public Map
					requestReceived(
						BuddyPluginBuddy	from_buddy,
						int					subsystem,
						Map					request )

						throws BuddyPluginException
					{
						if ( subsystem == SUBSYSTEM_INTERNAL ){

							if ( !from_buddy.isAuthorised()){

								throw( new BuddyPluginException( "Unauthorised" ));
							}

							return( processInternalRequest( from_buddy, request ));
						}

						return( null );
					}

					@Override
					public void
					pendingMessages(
						BuddyPluginBuddy[]	from_buddies )
					{
					}
				});

			msg_registration =
				plugin_interface.getMessageManager().registerGenericMessageType(
					isPublicNetwork()?"AZBUDDY":"BGBUDDYA", 
					"Buddy message handler (" + target_network + ")",
					STREAM_CRYPTO,
					new GenericMessageHandler()
					{
						@Override
						public boolean
						accept(
							GenericMessageConnection	connection )

							throws MessageException
						{
							if ( !plugin.isClassicEnabled()){

								return( false );
							}

							DDBDetails	details = null;

							InetSocketAddress address = connection.getEndpoint().getNotionalAddress();
							
							final String originator = AddressUtils.getHostAddress(  address );

							String net = AENetworkClassifier.categoriseAddress( address );
							
							for ( DDBDetails d: ddb_details ){
								
								if ( d.getNetwork() == net ){
									
									details = d ;
									
									break;
								}
							}
							
							if ( details == null ){
								
								if ( TRACE ){
									
									System.out.println( "accept - no details for " + net );
								}
								
								return( false );
							}
							
							// need to check that this connection is for the correct local endpoint to prevent a
							// speculative connection over the non-mix one being treated as mix and leaking
							// info...
							
							if ( details.getNetwork() != AENetworkClassifier.AT_PUBLIC ){
								
								boolean ok = false;
								
								GenericMessageStartpoint start = connection.getStartpoint();
								
								InetSocketAddress start_address = null;
								InetSocketAddress ddb_address1	= null;
								InetSocketAddress ddb_address2	= null;
								
								if ( start != null ){
									
									start_address = start.getNotionalAddress();
									
									ddb_address1 = details.getDDB().getDHTPlugin().getConnectionOrientedEndpoint();
									
									ok = AddressUtils.sameHost( start_address, ddb_address1 );
									
									if ( !ok ){
										
										ddb_address2 = details.getDDB().getLocalContact().getAddress();

										if ( ddb_address2 != null ){
											
											ok = AddressUtils.sameHost( start_address, ddb_address2 );
										}
									}
								}
								
								if ( !ok ){
									
									if ( TRACE ){
										
										System.out.println( "accept - ddb address mismatch: " + start_address + "/" + ddb_address1 + "/" + ddb_address2 );
									}
									
									return( false );
								}
							}
							
							final DDBDetails f_details = details;
							
							if ( TRACE ){
								System.out.println( "accept " + originator );
							}

							try{
								String reason = "Friend: Incoming connection establishment (" + originator + ")";

								plugin.addRateLimiters( connection );

								connection =
										getSTSConnection(
											connection,
											reason,
											new SEPublicKeyLocator()
											{
												@Override
												public boolean
												accept(
													Object		context,
													SEPublicKey	other_key )
												{
													String	other_key_str = Base32.encode( other_key.encodeRawPublicKey());

													if ( TRACE ){
														System.out.println( "Incoming: acceptKey - " + other_key_str );
													}

													try{
														synchronized( BuddyPluginNetwork.this ){

															int	unauth_count = 0;

															for (int i=0;i<buddies.size();i++){

																BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(i);

																if ( buddy.getPublicKey().equals( other_key_str )){

																		// don't accept a second or subsequent connection for unauth buddies
																		// as they have a single chance to be processed

																	if ( !buddy.isAuthorised()){

																		log( buddy, "Incoming connection from " + originator + " failed as for unauthorised buddy" );

																		return( false );
																	}

																	buddy.incomingConnection( f_details, (GenericMessageConnection)context );

																	return( true );
																}

																if ( !buddy.isAuthorised()){

																	unauth_count++;
																}
															}
															
																// no existing authorised buddy

															if ( unauth_count < MAX_UNAUTH_BUDDIES ){

																if ( tooManyUnauthConnections( originator )){

																	log( null, "Too many recent unauthorised connections from " + originator );

																	return( false );
																}

																BuddyPluginBuddy buddy = addBuddy( other_key_str, SUBSYSTEM_AZ2, false, false );

																if ( buddy != null ){

																	buddy.incomingConnection( f_details, (GenericMessageConnection)context );

																	return( true );

																}else{

																	log( null, "Incoming connection from " + originator + " failed due to pk mismatch" );

																	return( false );
																}
															}else{
															
																log( null, "Incoming connection from " + originator + " rejected, too many unauthorised buddies" );
																
																return( false );
															}
														}

													}catch( Throwable e ){

														log( null, "Incomming connection from " + originator + " failed", e );

														return( false );
													}
												}
											});

							}catch( Throwable e ){

								connection.close();

								log( null, "Incoming connection from " + originator + " failed", e );
							}

							return( true );
						}
					});

		}catch( Throwable e ){

			log( null, "Failed to register message listener", e );
		}
	}

	protected GenericMessageConnection
	getSTSConnection(
		GenericMessageConnection	connection,
		String						reason,
		SEPublicKeyLocator			locator )
	
		throws Exception
	{	
		return( 
			sec_man.getSTSConnection(
				connection,
				sec_man.getPublicKey( SEPublicKey.KEY_TYPE_ECC_192, isPublicNetwork()?1:2, reason ),
				locator,
				reason,
				BLOCK_CRYPTO ));
	}
			
	protected boolean
	tooManyUnauthConnections(
		String	originator )
	{
		synchronized( this ){

			if ( unauth_bloom == null ){

				unauth_bloom = BloomFilterFactory.createAddRemove4Bit( UNAUTH_BLOOM_CHUNK );

				unauth_bloom_create_time	= SystemTime.getCurrentTime();
			}

			int	hit_count = unauth_bloom.add( originator.getBytes());

			if ( hit_count >= 8 ){

				// Debug.out( "Too many recent unauthorised connection attempts from " + originator );

				return( true );
			}

			return( false );
		}
	}

	protected void
	checkUnauthBloom()
	{
		synchronized( this ){

			if ( unauth_bloom != null ){

				long	now = SystemTime.getCurrentTime();

				if ( now < unauth_bloom_create_time ){

					unauth_bloom_create_time = now;

				}else if ( now - unauth_bloom_create_time > UNAUTH_BLOOM_RECREATE ){

					unauth_bloom = null;
				}
			}
		}
	}

	protected void
	checkMaxMessageSize(
		int		size )

		throws BuddyPluginException
	{
		if ( size > MAX_MESSAGE_SIZE ){

			throw( new BuddyPluginException( "Message is too large to send, limit is " + DisplayFormatters.formatByteCountToKiBEtc( MAX_MESSAGE_SIZE )));
		}
	}

	protected void
	checkPersistentDispatch()
	{
		List	buddies_copy;

		synchronized( this ){

			buddies_copy = new ArrayList( buddies );
		}

		for (int i=0;i<buddies_copy.size();i++){

			BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies_copy.get(i);

			buddy.checkPersistentDispatch();
		}
	}

	protected void
	persistentDispatchInit()
	{
		Iterator it = pd_preinit.iterator();

		while( it.hasNext()){

			persistentDispatchPending((BuddyPluginBuddy)it.next());
		}

		pd_preinit = null;
	}

	protected void
	persistentDispatchPending(
		BuddyPluginBuddy	buddy )
	{
		synchronized( pd_queue ){

			if ( plugin.getInitialisationState() == BuddyPlugin.INIT_UNKNOWN ){

				pd_preinit.add( buddy );

				return;
			}

			if ( !pd_queue.contains( buddy )){

				pd_queue.add( buddy );

				pd_queue_sem.release();

				if ( pd_thread == null ){

					pd_thread =
						new AEThread2( "BuddyPlugin:persistDispatch", true )
						{
							@Override
							public void
							run()
							{
								while( true ){

									if ( !pd_queue_sem.reserve( 30*1000 )){

										synchronized( pd_queue ){

											if ( pd_queue.isEmpty()){

												pd_thread	= null;

												break;
											}
										}
									}else{

										BuddyPluginBuddy	buddy;

										synchronized( pd_queue ){

											buddy = (BuddyPluginBuddy)pd_queue.remove(0);
										}

										buddy.persistentDispatch();
									}
								}
							}
						};

					pd_thread.start();
				}
			}
		}
	}

	protected Map
	processInternalRequest(
		BuddyPluginBuddy	from_buddy,
		Map					request )

		throws BuddyPluginException
	{
		int	type = ((Long)request.get("type")).intValue();

		if ( type == RT_INTERNAL_REQUEST_PING ){

			Map	reply = new HashMap();

			reply.put( "type", new Long( RT_INTERNAL_REPLY_PING ));

			return( reply );

		}else if ( type == RT_INTERNAL_REQUEST_CLOSE ){

			from_buddy.receivedCloseRequest( request );

			Map	reply = new HashMap();

			reply.put( "type", new Long( RT_INTERNAL_REPLY_CLOSE ));

			return( reply );

		}else{

			throw( new BuddyPluginException( "Unrecognised request type " + type ));
		}
	}

	protected void
	updateListenPorts()
	{
		for ( DDBDetails details: ddb_details ){
			
			details.updateListenPorts();
		}
	}

	protected void
	updateIP()
	{
		for ( DDBDetails details: ddb_details ){
			
			details.updateIP();
		}
	}

	protected void
	updateNickName(
		String		new_nick )
	{
		new_nick = new_nick.trim();

		if ( new_nick.length() == 0 ){

			new_nick = null;
		}
		
		for ( DDBDetails details: ddb_details ){
			
			details.updateNickName( new_nick );
		}
	}

	protected void
	updateOnlineStatus(
		int		new_status )
	{
		boolean	changed = false;

		for ( DDBDetails details: ddb_details ){
			
			if ( details.updateOnlineStatus( new_status )){
				
				changed = true;
			}
		}

		if ( changed ){

			List	buddies = getAllBuddies();

			for (int i=0;i<buddies.size();i++){

				BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(i);

				if ( buddy.isConnected()){

					buddy.sendKeepAlive();
				}
			}
		}
	}

	protected boolean
	stringsEqual(
		String	s1,
		String	s2 )
	{
		if ( s1 == null && s2 == null ){

			return( true );
		}

		if ( s1 == null || s2 == null ){

			return( false );
		}

		return( s1.equals( s2 ));
	}

	protected DDBDetails
	getDDBDetails(
		String 	net )
	{
		for ( DDBDetails details: ddb_details ){
			
			if ( net == details.getNetwork()){
				
				return( details );
			}
		}
		
		return( null );
	}
	
	protected int
	getCurrentStatusSeq(
		DDBDetails		details )
	{
		return( details.current_publish.getSequence());
	}

	protected void
	closedown()
	{
		logMessage( null, "Closing down" );

		List<BuddyPluginBuddy>	buddies = getAllBuddies();

		synchronized( this ){

			connected_at_close = new ArrayList<>();

			for ( BuddyPluginBuddy buddy: buddies ){

				if ( buddy.isConnected()){

					connected_at_close.add( buddy );
				}
			}
		}

		for ( DDBDetails details: ddb_details ){
			
			details.closedown();
		}
	}

	protected void
	setConfigDirty()
	{
		synchronized( this ){

			config_dirty = true;
		}
	}

	protected void
	loadConfig()
	{
		long	now = SystemTime.getCurrentTime();

		synchronized( this ){

			Map map = readConfig();

			List	buddies_config = (List)map.get( "friends" );

			if ( buddies_config != null ){

				if ( buddies_config.size() == 0 ){

					deleteConfig();

				}else{
					for (int i=0;i<buddies_config.size();i++){

						Object o = buddies_config.get(i);

						if ( o instanceof Map ){

							Map	details = (Map)o;

							Long	l_ct = (Long)details.get( "ct" );

							long	created_time = l_ct==null?now:l_ct.longValue();

							if ( created_time > now ){

								created_time = now;
							}

							String	key = new String((byte[])details.get( "pk" ));

							List	recent_ygm = (List)details.get( "ygm" );

							String	nick 	= decodeString((byte[])details.get( "n" ));
							
							String	my_name = decodeString((byte[])details.get( "mn" ));

							Long	l_seq = (Long)details.get( "ls" );

							int	last_seq = l_seq==null?0:l_seq.intValue();

							Long	l_lo = (Long)details.get( "lo" );

							long	last_time_online = l_lo==null?0:l_lo.longValue();

							if ( last_time_online > now ){

								last_time_online = now;
							}

							Long l_subsystem = (Long)details.get( "ss" );

							int	subsystem = l_subsystem==null?SUBSYSTEM_AZ2:l_subsystem.intValue();

							if (subsystem == SUBSYSTEM_AZ3) {
								continue;
							}

							Long l_ver = (Long)details.get("v");

							int	ver = l_ver==null?VERSION_INITIAL:l_ver.intValue();

							String	loc_cat = decodeString((byte[])details.get( "lc" ));
							String	rem_cat = decodeString((byte[])details.get( "rc" ));

							BuddyPluginBuddy buddy = new BuddyPluginBuddy( this, created_time, subsystem, true, key, nick, my_name, ver, loc_cat, rem_cat, last_seq, last_time_online, recent_ygm, false );

							byte[]	ip_bytes = (byte[])details.get( "ip" );

							if ( ip_bytes != null && ip_bytes.length > 0 ){

								try{
									InetAddress ip = InetAddress.getByAddress( ip_bytes );

									int	tcp_port = ((Long)details.get( "tcp" )).intValue();
									int	udp_port = ((Long)details.get( "udp" )).intValue();

									buddy.setCachedStatus( new InetSocketAddress( ip, 0 ), tcp_port, udp_port );

								}catch( Throwable e ){
								}
							}else{
								
								String host = MapUtils.getMapString( details, "host", null );
								
								if ( host != null ){
									
									int	tcp_port = ((Long)details.get( "tcp" )).intValue();
									int	udp_port = ((Long)details.get( "udp" )).intValue();

									buddy.setCachedStatus( InetSocketAddress.createUnresolved(host, 0), tcp_port, udp_port );
								}
							}

							logMessage( buddy, "Loaded buddy " + buddy.getString());

							buddies.add( buddy );

							buddies_map.put( key, buddy );
						}
					}
				}
			}

			int	num_buddies = buddies.size();

			for ( BuddyPluginBuddy b: buddies ){

				b.setInitialStatus( now, num_buddies );
			}
		}
	}

	protected String
	decodeString(
		byte[]		bytes )
	{
		if (  bytes == null ){

			return( null );
		}

		try{
			return( new String( bytes, "UTF8" ));

		}catch( Throwable e ){

			return( null );
		}
	}

	protected void
	saveConfig()
	{
		saveConfig( false );
	}

	protected void
	saveConfig(
		boolean	force )
	{
		synchronized( this ){

			if ( config_dirty || force ){

				List buddies_config = new ArrayList();

				for (int i=0;i<buddies.size();i++){

					BuddyPluginBuddy buddy = (BuddyPluginBuddy)buddies.get(i);

					if ( buddy.isTransient() || !buddy.isAuthorised()){

						continue;
					}

					try{
						Map	map = new HashMap();
	
						map.put( "ct", new Long( buddy.getCreatedTime()));
	
						map.put( "pk", buddy.getPublicKey());
	
						List	ygm = buddy.getYGMMarkers();
	
						if ( ygm != null ){
	
							map.put( "ygm", ygm );
						}
	
						String	nick = buddy.getNickName();
	
						if ( nick != null ){
	
							map.put( "n", nick );
						}
	
						String	my_nick = buddy.getMyName();
						
						if ( my_nick != null ){
	
							map.put( "mn", my_nick );
						}
						
						map.put( "ls", new Long( buddy.getLastStatusSeq()));
	
						map.put( "lo", new Long( buddy.getLastTimeOnline()));
	
						map.put( "ss", new Long( buddy.getSubsystem()));
	
						map.put( "v", new Long( buddy.getVersion()));
	
						if ( buddy.getLocalAuthorisedRSSTagsOrCategoriesAsString() != null ){
							map.put( "lc", buddy.getLocalAuthorisedRSSTagsOrCategoriesAsString());
						}
	
						if ( buddy.getRemoteAuthorisedRSSTagsOrCategoriesAsString() != null ){
							map.put( "rc", buddy.getRemoteAuthorisedRSSTagsOrCategoriesAsString());
						}
	
						boolean connected =
							buddy.isConnected() ||
							( connected_at_close != null && connected_at_close.contains( buddy ));
	
						if ( connected ){
	
							InetSocketAddress	isa 		= buddy.getIP();
							int					tcp_port	= buddy.getTCPPort();
							int					udp_port	= buddy.getUDPPort();
	
							if ( isa != null ){
	
								if ( isa.isUnresolved()){
									
									map.put( "host", AddressUtils.getHostAddress( isa ));
									
								}else{
									
									map.put( "ip", isa.getAddress().getAddress());
								}
								
								map.put( "tcp", new Long( tcp_port ));
								map.put( "udp", new Long( udp_port ));
							}
						}
	
						buddies_config.add( map );
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}

				Map	map = new HashMap();

				if ( buddies_config.size() > 0 ){

					map.put( "friends", buddies_config );

					writeConfig( map );

				}else{

					deleteConfig();
				}

				config_dirty = false;
			}
		}
	}

	public BuddyPluginBuddy
	addBuddy(
		String		key,
		int			subsystem )

	{
		if ( !plugin.isClassicEnabled()){

			plugin.setClassicEnabled( true, false );
		}

		return( addBuddy( key, subsystem, true, false ));
	}

	protected BuddyPluginBuddy
	addBuddy(
		String		key,
		int			subsystem,
		boolean		authorised,
		boolean		for_peek )
	{
		if ( key.length() == 0 || !verifyPublicKey( key )){

			return( null );
		}

		BuddyPluginBuddy	buddy_to_return = null;

			// buddy may be already present as unauthorised in which case we pick it up
			// and authorise it and send the added event (we don't fire added events for
			// unauthorised buddies)

		boolean	transient_changed = false;
		
		synchronized( this ){

			for (int i=0;i<buddies.size();i++){

				BuddyPluginBuddy buddy = (BuddyPluginBuddy)buddies.get(i);

				if ( buddy.getPublicKey().equals( key )){

					if ( buddy.getSubsystem() != subsystem ){

						log( buddy, "Buddy " + buddy.getString() + ": subsystem changed from " + buddy.getSubsystem() + " to " + subsystem );

						buddy.setSubsystem( subsystem );

						saveConfig( true );
					}

					if ( buddy.isTransient() && !for_peek ){
						
						buddy.setTransient( false );
						
						transient_changed = true;
						
						saveConfig( true );
					}
					
					if ( authorised && !buddy.isAuthorised()){

						log( buddy, "Buddy " + buddy.getString() + ": no authorised" );

						buddy.setAuthorised( true );

						buddy_to_return	= buddy;

					}else{

						return( buddy );
					}
				}
			}

			if ( buddy_to_return == null ){

				buddy_to_return =
					new BuddyPluginBuddy( this, SystemTime.getCurrentTime(), subsystem, authorised, key, null, null, VERSION_CURRENT, null, null, 0, 0, null, for_peek );

				buddies.add( buddy_to_return );

				buddies_map.put( key, buddy_to_return );

				if ( !authorised ){

					log( buddy_to_return, "Added unauthorised buddy: " + buddy_to_return.getString());
				}
			}

			if ( buddy_to_return.isAuthorised()){

				logMessage( buddy_to_return, "Added buddy " + buddy_to_return.getString());

				saveConfig( true );
			}
		}

		if ( transient_changed ){
			
			fireRemoved( buddy_to_return );
		}
		
		fireAdded( buddy_to_return );

		return( buddy_to_return );
	}

	public BuddyPluginBuddy
	peekBuddy(
		String		key )
	{
		if ( !plugin.isClassicEnabled()){

			if ( !plugin.setClassicEnabled( true, true )){
				
				return( null );
			}
		}
		
		return( addBuddy( key, SUBSYSTEM_AZ2, true, true ));
	}
	
	protected void
	removeBuddy(
		BuddyPluginBuddy 	buddy )
	{
		synchronized( this ){

			if ( !buddies.remove( buddy )){

				return;
			}

			buddies_map.remove( buddy.getPublicKey());

			logMessage( buddy, "Removed buddy " + buddy.getString());

			saveConfig( true );
		}

		buddy.destroy();

		fireRemoved( buddy );
	}

	protected Map
	readConfig()
	{
		File	config_file = FileUtil.newFile( plugin_interface.getUtilities().getUserDir(), config_file_name );

		return( readConfigFile( config_file ));
	}

	protected void
	writeConfig(
		Map		map )
	{
		File	config_file = FileUtil.newFile( plugin_interface.getUtilities().getUserDir(), config_file_name );

		writeConfigFile( config_file, map );
	}

	protected void
	deleteConfig()
	{
		Utilities utils = plugin_interface.getUtilities();

		File	config_file = FileUtil.newFile( utils.getUserDir(), config_file_name );

		utils.deleteResilientBEncodedFile( config_file.getParentFile(), config_file.getName(), true );

	}

	protected Map
	readConfigFile(
		File		name )
	{
		Utilities utils = plugin_interface.getUtilities();

		Map map = utils.readResilientBEncodedFile(
						name.getParentFile(), name.getName(), true );

		if ( map == null ){

			map = new HashMap();
		}

		return( map );
	}

	protected boolean
	writeConfigFile(
		File		name,
		Map			data )
	{
		Utilities utils = plugin_interface.getUtilities();

		utils.writeResilientBEncodedFile(
			name.getParentFile(), name.getName(), data, true );

		return( name.exists());
	}

	protected File
	getBuddyConfigDir()
	{
		return( FileUtil.newFile( plugin_interface.getUtilities().getUserDir(), "friends" ));
	}

	public BuddyPluginAZ2
	getAZ2Handler()
	{
		return( az2_handler );
	}

	public String
	getPublicKey()
	{
		try{
			return( Base32.encode(ecc_handler.getPublicKey( "Friend get key" )));

		}catch( Throwable e ){

			logMessage( null, "Failed to access public key", e );

			return( null );
		}
	}

	public boolean
	verifyPublicKey(
		String		key )
	{
		return( ecc_handler.verifyPublicKey( Base32.decode( key )));
	}

	protected void
	checkBuddiesAndRepublish()
	{
		updateBuddys();

		plugin_interface.getUtilities().createTimer( "Buddy checker" ).addPeriodicEvent(
			TIMER_PERIOD,
			new UTTimerEventPerformer()
			{
				int	tick_count;

				@Override
				public void
				perform(
					UTTimerEvent event )
				{
					tick_count++;

					if ( !plugin.isClassicEnabled()){

						return;
					}

					updateBuddys();

					if ( tick_count % STATUS_REPUBLISH_TICKS == 0 ){

						for ( DDBDetails details: ddb_details ){
							
							details.updatePublish();
						}
					}

					if ( tick_count % CHECK_YGM_TICKS == 0 ){

						checkMessagePending( tick_count );
					}

					if ( tick_count % BLOOM_CHECK_TICKS == 0 ){

						checkUnauthBloom();
					}

					if ( tick_count % SAVE_CONFIG_TICKS == 0 ){

						saveConfig();
					}

					if ( tick_count % PERSISTENT_MSG_CHECK_TICKS == 0 ){

						checkPersistentDispatch();
					}
				}
			});
	}

	protected void
	updateBuddys()
	{
		List	buddies_copy;

		synchronized( this ){

			buddies_copy = new ArrayList( buddies );
		}

		long	now = SystemTime.getCurrentTime();

		Random random = new Random();

		for (int i=0;i<buddies_copy.size();i++){

			BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies_copy.get(i);

			long	last_check = buddy.getLastStatusCheckTime();

			buddy.checkTimeouts();

			int	period = BUDDY_STATUS_CHECK_PERIOD_MIN + BUDDY_STATUS_CHECK_PERIOD_INC*buddies_copy.size()/5;

				// randomise a bit

			period += random.nextInt( 2*60*1000 );

				// last check may be in the future as we defer checks for seemingly inactive buddies

			if ( now - last_check > period ){

				if ( !buddy.statusCheckActive()){

					if ( buddy.isAuthorised()){

						updateBuddyStatus( buddy );
					}
				}
			}
		}

			// trim any non-authorised buddies that have gone idle

		synchronized( this ){

			for (int i=0;i<buddies_copy.size();i++){

				BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies_copy.get(i);

				if ( buddy.isIdle() && !buddy.isAuthorised()){

					removeBuddy( buddy );
				}
			}
		}
	}

	protected void
	updateBuddyStatus(
		final BuddyPluginBuddy	buddy )
	{
		if ( !buddy.statusCheckStarts()){

			return;
		}

		log( buddy, "Updating buddy status: " + buddy.getString());

		List<DDBDetails>	temp = ddb_details.getList();
		
		Runnable callback = 
			new Runnable()
			{
				private int count;
			
				@Override
				public void run(){
					
					synchronized( this ){
						
						count++;
						
						if ( count != temp.size()){
							
							return;
						}
					}
					
					buddy.statusCheckFailed();
				}
			};
			
		for ( DDBDetails details: temp ){
			
			details.updateBuddyStatus( buddy, callback );
		}
	}

	protected Map
	verifyAndExtract(
		byte[]		signed_stuff,
		byte[]		public_key )

		throws BuddyPluginException
	{
		int	signature_length = ((int)signed_stuff[0])&0xff;

		byte[]	signature 	= new byte[ signature_length ];
		byte[]	data		= new byte[ signed_stuff.length - 1 - signature_length];

		System.arraycopy( signed_stuff, 1, signature, 0, signature_length );
		System.arraycopy( signed_stuff, 1 + signature_length, data, 0, data.length );

		try{
			if ( ecc_handler.verify( public_key, data, signature )){

				return( BDecoder.decode( data ));

			}else{

				logMessage( null, "Signature verification failed" );

				return( null );
			}
		}catch( Throwable e ){

			rethrow( "Verification failed", e );

			return( null );
		}
	}

	protected byte[]
	signAndInsert(
		Map		plain_stuff,
		String	reason )

		throws BuddyPluginException
	{
		try{
			byte[] data = BEncoder.encode( plain_stuff );

			byte[] signature = ecc_handler.sign( data, reason );

			byte[]	signed_payload = new byte[ 1 + signature.length + data.length ];

			signed_payload[0] = (byte)signature.length;

			System.arraycopy( signature, 0, signed_payload, 1, signature.length );
			System.arraycopy( data, 0, signed_payload, 1 + signature.length, data.length );

			return( signed_payload );

		}catch( Throwable e ){

			rethrow( "Signing failed", e );

			return( null );
		}
	}

	public boolean
	verify(
		String				pk,
		byte[]				payload,
		byte[]				signature )

		throws BuddyPluginException
	{
		return( verify( Base32.decode( pk ), payload, signature ));
	}

	protected boolean
	verify(
		BuddyPluginBuddy	buddy,
		byte[]				payload,
		byte[]				signature )

		throws BuddyPluginException
	{
		return( verify( buddy.getRawPublicKey(), payload, signature ));
	}

	protected boolean
	verify(
		byte[]				pk,
		byte[]				payload,
		byte[]				signature )

		throws BuddyPluginException
	{
		try{

			return( ecc_handler.verify( pk, payload, signature ));

		}catch( Throwable e ){

			rethrow( "Verification failed", e );

			return( false );
		}
	}

	public byte[]
   	sign(
   		byte[]		payload )

	   	throws BuddyPluginException
	{
		try{

			return( ecc_handler.sign( payload, "Friend message signing" ));

		}catch( Throwable e ){

			rethrow( "Signing failed", e );

			return( null );
		}
	}

	protected BuddyPlugin.CryptoResult
	encrypt(
		BuddyPluginBuddy	buddy,
		byte[]				payload )

		throws BuddyPluginException
	{
		return encrypt(buddy.getPublicKey(), payload, buddy.getName());
	}

	public BuddyPlugin.CryptoResult
	encrypt(
		String				pk,
		byte[]				payload,
		String				forWho )

		throws BuddyPluginException
	{

		try{
			byte[]	hash = new byte[20];

			random.nextBytes( hash );

			Map	content = new HashMap();

			content.put( "h", hash );
			content.put( "p", payload );

			final byte[] encrypted = ecc_handler.encrypt( Base32.decode(pk), BEncoder.encode( content ), "Encrypting message for " + forWho);

			final byte[] sha1_hash = new SHA1Simple().calculateHash( hash );

			return(
				new BuddyPlugin.CryptoResult()
				{
					@Override
					public byte[]
		    		getChallenge()
					{
						return( sha1_hash );
					}

		    		@Override
				    public byte[]
		    		getPayload()
		    		{
		    			return( encrypted );
		    		}
				});

		}catch( Throwable e ){

			rethrow( "Encryption failed", e );

			return( null );
		}
	}

	protected BuddyPlugin.CryptoResult
	decrypt(
		BuddyPluginBuddy	buddy,
		byte[]				content,
		String forName)

		throws BuddyPluginException
	{

		try{
			final byte[] decrypted = ecc_handler.decrypt( buddy.getRawPublicKey(), content, "Decrypting message for " + buddy.getName());

			final Map	map = BDecoder.decode( decrypted );

			return(
				new BuddyPlugin.CryptoResult()
				{
					@Override
					public byte[]
		    		getChallenge()
					{
						return((byte[])map.get("h"));
					}

		    		@Override
				    public byte[]
		    		getPayload()
		    		{
		    			return((byte[])map.get("p"));
		    		}
				});

		}catch( Throwable e ){

			rethrow( "Decryption failed", e );

			return( null );
		}
	}

	public BuddyPlugin.CryptoResult
	decrypt(
		String				public_key,
		byte[]				content )

		throws BuddyPluginException
	{

		try{
			final byte[] decrypted = ecc_handler.decrypt( Base32.decode(public_key), content, "Decrypting message for " + public_key);

			final Map	map = BDecoder.decode( decrypted );

			return(
				new BuddyPlugin.CryptoResult()
				{
					@Override
					public byte[]
		    		getChallenge()
					{
						return((byte[])map.get("h"));
					}

		    		@Override
				    public byte[]
		    		getPayload()
		    		{
		    			return((byte[])map.get("p"));
		    		}
				});

		}catch( Throwable e ){

			rethrow( "Decryption failed", e );

			return( null );
		}
	}

	protected void
	setMessagePending(
		BuddyPluginBuddy			buddy,
		operationListener			_listener )

		throws BuddyPluginException
	{
		operationListener listener = 
			new operationListener()
			{
				boolean	fired;
				
				@Override
				public void complete(){
					synchronized( this ){
						
						if ( fired ){
							return;
						}
						
						fired = true;
					}
					_listener.complete();
				}
			};
			
		try{
			checkAvailable();

			List<DDBDetails> temp = ddb_details.getList();
			
			operationListener listener_wrapper =
				new operationListener()
				{
					int	count = 0;
				
					@Override
					public void 
					complete()
					{
						synchronized( this ){
							count++;
							
							if ( count < temp.size()){
								
								return;
							}
						}
						
						listener.complete();
					}
				};
				
			for ( DDBDetails details: temp ){
				
				details.setMessagePending( buddy, listener_wrapper );
			}

		}catch( Throwable e ){

			try{
				rethrow( "Failed to publish YGM", e );

			}finally{

				listener.complete();
			}
		}
	}

	public void
	checkMessagePending(
		int	tick_count )
	{
		log( null, "Checking YGM" );

		if ( tick_count % YGM_BLOOM_LIFE_TICKS == 0 ){

			synchronized( this ){

				ygm_unauth_bloom = null;
			}
		}

		for ( DDBDetails details: ddb_details ){
			
			details.checkMessagePending();
		}
	}

	public BuddyPluginBuddy
	getBuddyFromPublicKey(
		String		key )
	{
		synchronized( this ){

			return((BuddyPluginBuddy)buddies_map.get( key ));
		}
	}

	public PluginInterface
	getPluginInterface()
	{
		return( plugin_interface );
	}

	protected SESecurityManager
	getSecurityManager()
	{
		return( sec_man );
	}

	protected GenericMessageRegistration
	getMessageRegistration()
	{
		return( msg_registration );
	}

		/**
		 * Returns authorised buddies only
		 */

	public List<BuddyPluginBuddy>
	getBuddies()
	{
		synchronized( this ){

			List<BuddyPluginBuddy>	result = new ArrayList<>();

			for (int i=0;i<buddies.size();i++){

				BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(i);

				if ( buddy.isAuthorised() && !buddy.isTransient()){

					result.add( buddy );
				}
			}

			return( result );
		}
	}

	protected List<BuddyPluginBuddy>
	getAllBuddies()
	{
		synchronized( this ){

			return(new ArrayList<>(buddies));
		}
	}
	
	
	protected Map
	requestReceived(
		BuddyPluginBuddy		from_buddy,
		int						subsystem,
		Map						content )

		throws BuddyPluginException
	{
		List	 listeners_ref = request_listeners.getList();

		for (int i=0;i<listeners_ref.size();i++){

			try{
				Map reply = ((BuddyPluginBuddyRequestListener)listeners_ref.get(i)).requestReceived(from_buddy, subsystem, content);

				if ( reply != null ){

					return( reply );
				}
			}catch( BuddyPluginException e ){

				throw( e );

			}catch( Throwable e ){

				Debug.printStackTrace( e );

				throw( new BuddyPluginException( "Request processing failed", e ));
			}
		}

		return( null );
	}



	protected void
   	fireYGM(
   		BuddyPluginBuddy[]		from_buddies )
   	{
   		List	 listeners_ref = request_listeners.getList();

   		for (int i=0;i<listeners_ref.size();i++){

   			try{
   				((BuddyPluginBuddyRequestListener)listeners_ref.get(i)).pendingMessages( from_buddies );

   			}catch( Throwable e ){

   				Debug.printStackTrace( e );
   			}
   		}
   	}

	
	protected void
	rethrow(
		String		reason,
		Throwable	e )

		throws BuddyPluginException
	{
		logMessage( null, reason, e );

		if ( e instanceof CryptoManagerPasswordException ){


			throw( new BuddyPluginPasswordException(((CryptoManagerPasswordException)e).wasIncorrect(), reason, e ));

		}else{

			throw( new BuddyPluginException( reason, e ));
		}
	}


	protected InputStream
	handleUPRSS(
		final AZPluginConnection	connection,
		BuddyPluginBuddy			buddy,
		String						tag_or_category )

		throws IPCException
	{
		if ( buddy.getPluginNetwork() != this ){
			
			throw( new IPCException( "Plugin network mismatch" ));
		}

		if ( !buddy.isOnline( true )){

			throw( new IPCException( "Buddy isn't online" ));
		}

		Map<String,Object>	msg = new HashMap<>();

		final String if_mod 	= connection.getRequestProperty( "If-Modified-Since" );

		try{
			msg.put( "cat", tag_or_category.getBytes( "UTF-8" ));

			if ( if_mod != null ){

				msg.put( "if_mod", if_mod );
			}

			// String etag		= connection.getRequestProperty( "If-None-Match" );

		}catch( Throwable e ){

			Debug.out( e );
		}

		final Object[] 		result 		= { null };
		final AESemaphore	result_sem 	= new AESemaphore( "BuddyPlugin:rss" );

		final String	etag = buddy.getPublicKey() + "-" + tag_or_category;

		az2_handler.sendAZ2RSSMessage(
			buddy,
			msg,
			new BuddyPluginAZ2TrackerListener()
			{
				@Override
				public Map
				messageReceived(
					BuddyPluginBuddy	buddy,
					Map					message )
				{
					try{
						byte[] bytes = (byte[])message.get( "rss" );

						ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

						result[0] = bais;

						connection.setHeaderField( "ETag", etag );

						byte[] b_last_mod = (byte[])message.get( "last_mod" );

						if ( b_last_mod != null ){

							String	last_mod = new String( b_last_mod, "UTF-8" );

							connection.setHeaderField( "Last-Modified", last_mod );

							if ( if_mod != null && if_mod.equals( last_mod ) && bytes.length == 0 ){

								connection.setResponse( HttpURLConnection.HTTP_NOT_MODIFIED, "Not Modified" );
							}
						}

						result_sem.release();

					}catch( Throwable e ){

						messageFailed( buddy, e );
					}

					return( null );
				}

				@Override
				public void
				messageFailed(
					BuddyPluginBuddy	buddy,
					Throwable			cause )
				{
					result[0] = new IPCException( "Read failed", cause );

					result_sem.release();
				}
			});

		result_sem.reserve( isPublicNetwork()?120*1000:240*1000 );

		if ( result[0] == null ){

			log( buddy, "    RSS download timeout" );
			
			throw( new IPCException( "Timeout" ));

		}else if ( result[0] instanceof InputStream ){

			return((InputStream)result[0]);

		}else{

			IPCException error = (IPCException)result[0];

			log( buddy, "    RSS downloaded failed: " + Debug.getNestedExceptionMessage( error ));
		
			throw( error );
		}
	}

	protected InputStream
	handleUPTorrent(
		final AZPluginConnection	connection,
		final BuddyPluginBuddy		buddy,
		String						tag_or_category,
		final byte[]				hash )

		throws IPCException
	{
		if ( buddy.getPluginNetwork() != this ){
			
			throw( new IPCException( "Plugin network mismatch" ));
		}
		
		final long timeout = isPublicNetwork()?120*1000:240*1000;

		final Object[] 		result 		= { null };
		final AESemaphore	result_sem 	= new AESemaphore( "BuddyPlugin:upt" );

		log( buddy, "Attempting to download torrent for " + Base32.encode( hash ));

			// first try and get torrent direct from the buddy

		if ( buddy.isOnline( true )){

			try{

				Map<String,Object>	msg = new HashMap<>();

				try{
					msg.put( "cat", tag_or_category.getBytes( "UTF-8" ));

					msg.put( "hash", hash );

				}catch( Throwable e ){

					Debug.out( e );
				}

				az2_handler.sendAZ2RSSMessage(
					buddy,
					msg,
					new BuddyPluginAZ2TrackerListener()
					{
						private boolean	result_set;

						@Override
						public Map
						messageReceived(
							BuddyPluginBuddy	buddy,
							Map					message )
						{
							try{
								byte[] bytes = (byte[])message.get( "torrent" );

								log( buddy, "    torrent downloaded from buddy" );

								setResult( bytes );

							}catch( Throwable e ){

								messageFailed( buddy, e );
							}

							return( null );
						}

						@Override
						public void
						messageFailed(
							BuddyPluginBuddy	buddy,
							Throwable			cause )
						{
							setResult( new IPCException( "Read failed", cause ));
						}

						protected void
						setResult(
							Object		obj )
						{
							synchronized( result ){

								if ( result_set ){

									return;
								}

								result_set = true;

								if ( !( result[0] instanceof byte[] )){

									result[0] = obj;
								}

								result_sem.release();
							}
						}
					});
			}catch( Throwable e ){

				result[0] = new IPCException( "Buddy torrent get failed", e );

				result_sem.release();
			}
		}else{

			result[0] = new IPCException( "Buddy is offline" );

			result_sem.release();
		}

			// second try and get via magnet

		final MagnetPlugin	magnet_plugin = getMagnetPlugin();

		if ( magnet_plugin == null ){

			synchronized( result ){

				if ( result[0] == null ){

					result[0] = new IPCException( "Magnet plugin unavailable" );
				}
			}

			result_sem.release();

		}else{

			new AEThread2( "BuddyPlugin:mag", true )
			{
				private boolean result_set;

				@Override
				public void
				run()
				{
					try{

						if ( buddy.isOnline( true )){

							Thread.sleep(10*1000);
						}

						synchronized( result ){

							if ( result[0] instanceof byte[] ){

								setResult( null );

								return;
							}
						}

						byte[] torrent_data = 
							magnet_plugin.download(
								plugin.isLoggerEnabled()? 
									new MagnetPluginProgressListener()
									{
										@Override
										public void
										reportSize(
											long	size )
										{
										}
		
										@Override
										public void
										reportActivity(
											String	str )
										{
											log( buddy, "    MagnetDownload: " + str );
										}
		
										@Override
										public void
										reportCompleteness(
											int		percent )
										{
										}
		
										@Override
										public void
										reportContributor(
											InetSocketAddress	address )
										{
										}
		
										@Override
										public boolean
										verbose()
										{
											return( false );
										}
		
										@Override
										public boolean
										cancelled()
										{
											return( false );
										}
									} : null ,
									hash,
									"",
									new InetSocketAddress[0],
									Collections.emptyList(),
									Collections.emptyMap(),
									timeout,
									MagnetPlugin.FL_NONE );

						if ( torrent_data == null ){

							setResult( new IPCException( "Magnet timeout" ));

						}else{

							log( buddy, "    torrent downloaded from magnet" );

							setResult( torrent_data );
						}
					}catch( Throwable e ){

						setResult( new IPCException( "Magnet get failed", e ));
					}
				}

				protected void
				setResult(
					Object		obj )
				{
					synchronized( result ){

						if ( result_set ){

							return;
						}

						result_set = true;

						if ( obj != null ){

							if ( 	result[0] == null ||
									( obj instanceof byte[] && !( result[0] instanceof byte[] ))){

								result[0] = obj;
							}
						}

						result_sem.release();
					}
				}
			}.start();
		}

		long	start = SystemTime.getMonotonousTime();

		if ( result_sem.reserve(timeout )){

			if ( !( result[0] instanceof byte[] )){

				long	rem = timeout - ( SystemTime.getMonotonousTime() - start );

				if ( rem > 0 ){

					result_sem.reserve(rem );
				}
			}
		}

		if ( result[0] == null ){

			log( buddy, "    torrent download timeout" );

			throw( new IPCException( "Timeout" ));

		}else if ( result[0] instanceof byte[] ){

			return( new ByteArrayInputStream((byte[])result[0]));

		}else{

			IPCException error = (IPCException)result[0];

			log( buddy, "    torrent downloaded failed: " + Debug.getNestedExceptionMessage( error ));

			throw( error );
		}
	}

	protected MagnetPlugin
	getMagnetPlugin()
	{
		PluginInterface pi  = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass( MagnetPlugin.class );

		if ( pi == null ){

			return( null );
		}

		return((MagnetPlugin)pi.getPlugin());
	}
	
	protected void
	addRequestListener(
		BuddyPluginBuddyRequestListener	listener )
	{
		request_listeners.add( listener );
	}

	protected void
	removeRequestListener(
		BuddyPluginBuddyRequestListener	listener )
	{
		request_listeners.remove( listener );
	}

	protected void
	logMessage(
		BuddyPluginBuddy	buddy,
		String				str )
	{
		plugin.logMessage( buddy, str );
	}

	protected void
	logMessage(
		BuddyPluginBuddy	buddy,
		String				str,
		Throwable 			e )
	{
		plugin.logMessage( buddy, str, e );
	}

	protected void
	log(
		BuddyPluginBuddy	buddy,
		String				str )
	{
		plugin.log( buddy, str );
	}

	protected void
	log(
		BuddyPluginBuddy	buddy,
		String				str,
		Throwable 			e )
	{
		plugin.log( buddy, str, e );
	}
	
	protected class
	DDBDetails
	{
		private final DistributedDatabase	ddb;
	
		private PublishDetails	current_publish;
		private PublishDetails	latest_publish;
		private long			last_publish_start;
		private TimerEvent		republish_delay_event;
		private TimerEvent		update_ip_retry_event;

		private volatile boolean diversified;
		
		private List<DistributedDatabaseContact>	publish_write_contacts = new ArrayList<>();

		private AsyncDispatcher	publish_dispatcher = new AsyncDispatcher();

		private boolean 	ygm_active;
		private boolean		bogus_ygm_written;

		private int		status_seq;

		private byte[]	last_payload;
		
		private
		DDBDetails(
			DistributedDatabase		_ddb )
		{
			ddb = _ddb;

			while( status_seq == 0 ){

				status_seq = random.nextInt();
			}

			latest_publish = current_publish = new PublishDetails( ddb.getNetwork());
		}
		
		public DistributedDatabase
		getDDB()
		{
			return( ddb );
		}
		
		public String
		getNetwork()
		{
			return( ddb.getNetwork());
		}
		
		private void
		setEnabled(
			boolean		_enabled )
		{
			synchronized( this ){

				if ( latest_publish.isEnabled() != _enabled ){

					PublishDetails new_publish = latest_publish.getCopy();

					new_publish.setEnabled( _enabled );

					updatePublish( new_publish );
				}
			}
		}
		
		protected void
		updateListenPorts()
		{
			synchronized( this ){

				int	tcp_port;
				int udp_port;
				
				if ( latest_publish.getNetwork() == AENetworkClassifier.AT_PUBLIC ){
					
					tcp_port = COConfigurationManager.getIntParameter( "TCP.Listen.Port" );
					
					boolean	tcp_enabled = COConfigurationManager.getBooleanParameter( "TCP.Listen.Port.Enable" );
					
					udp_port = COConfigurationManager.getIntParameter("UDP.Listen.Port" );
					
					boolean	udp_enabled = COConfigurationManager.getBooleanParameter( "UDP.Listen.Port.Enable" );
		
					if ( !tcp_enabled ){
		
						tcp_port = 0;
					}
		
					if ( !udp_enabled ){
		
						udp_port = 0;
					}
				}else{
					
					tcp_port	= 6881;
					udp_port	= 0;
				}
				
				if ( 	latest_publish.getTCPPort() != tcp_port ||
						latest_publish.getUDPPort() != udp_port ){

					PublishDetails new_publish = latest_publish.getCopy();

					new_publish.setTCPPort( tcp_port );
					new_publish.setUDPPort( udp_port );

					updatePublish( new_publish );
				}
			}
		}
		
		protected void
		updateIPWithDelay()
		{
			synchronized( this ){
				
				if ( update_ip_retry_event == null ){
					
					update_ip_retry_event = 
						SimpleTimer.addEvent(
							"updateIP",
							SystemTime.getOffsetTime( 60*1000 ),
							(ev)->{
								
								synchronized( DDBDetails.this ){
									
									update_ip_retry_event = null;
								}
								
								updateIP();
							});
				}
			}
		}
		
		protected void
		updateIP()
		{
			if ( !ddb.isAvailable()){

				return;
			}

			if ( !ddb.isInitialized()){
			
				updateIPWithDelay();
				
				return;
			}
			
			InetSocketAddress[] public_ips = ddb.getDHTPlugin().getConnectionOrientedEndpoints();

			synchronized( this ){

				if ( 	latest_publish.getIPs() == null ||
						!Arrays.deepEquals( latest_publish.getIPs(), public_ips )){

					PublishDetails new_publish = latest_publish.getCopy();

					new_publish.setIPs( public_ips );

					updatePublish( new_publish );
				}
			}
		}
		
		protected void
		updateNickName(
			String		new_nick )
		{
			synchronized( this ){

				String	old_nick = latest_publish.getNickName();

				if ( !stringsEqual( new_nick, old_nick )){

					PublishDetails new_publish = latest_publish.getCopy();

					new_publish.setNickName( new_nick );

					updatePublish( new_publish );
				}
			}
		}
		
		protected boolean
		updateOnlineStatus(
			int		new_status )
		{
			boolean	changed;
			
			synchronized( this ){

				int	old_status = latest_publish.getOnlineStatus();

				changed = old_status != new_status;

				if ( changed ){

					PublishDetails new_publish = latest_publish.getCopy();

					new_publish.setOnlineStatus( new_status );

					updatePublish( new_publish );
				}
			}

			return( changed );
		}
		
		private int
		getOnlineStatus()
		{
			return( latest_publish.getOnlineStatus());
		}
		
		private void
		checkMessagePending()
		{
			synchronized( this ){
				
				if ( ygm_active ){
					
					return;
				}
				
				ygm_active = true;
			}
			
			boolean active = false;
			
			try{
				String	reason = "Friend YGM check";

				byte[] public_key = ecc_handler.getPublicKey( reason );

				DistributedDatabaseKey	key = getYGMKey( public_key, reason );

				active = true;
				
				ddb.read(
					new DistributedDatabaseListener()
					{
						private List		new_ygm_buddies = new ArrayList();
						private boolean	 	unauth_permitted = false;

						@Override
						public void
						event(
							DistributedDatabaseEvent		event )
						{
							int	type = event.getType();

							if ( type == DistributedDatabaseEvent.ET_VALUE_READ ){

								try{
									DistributedDatabaseValue value = event.getValue();

									byte[]	envelope = (byte[])value.getValue( byte[].class );

									Map	map = BDecoder.decode( envelope );

									byte[]	pk = (byte[])map.get( "pk" );

									if ( pk == null ){

										return;
									}

									String	pk_str = Base32.encode( pk );

									BuddyPluginBuddy buddy = getBuddyFromPublicKey( pk_str );

									if ( buddy == null || !buddy.isAuthorised() ){

										if ( buddy == null ){

											log( buddy, "YGM entry from unknown friend '" + pk_str + "' - ignoring" );

										}else{

											log( buddy, "YGM entry from unauthorised friend '" + pk_str + "' - ignoring" );
										}

										byte[] address = event.getContact().getAddress().getAddress().getAddress();

										synchronized( BuddyPluginNetwork.this ){

											if ( ygm_unauth_bloom == null ){

												ygm_unauth_bloom = BloomFilterFactory.createAddOnly(512);
											}

											if ( !ygm_unauth_bloom.contains( address )){

												ygm_unauth_bloom.add( address );

												unauth_permitted = true;
											}
										}
									}else{

										byte[]	signed_stuff = (byte[])map.get( "ss" );

										Map	payload = verifyAndExtract( signed_stuff, pk );

										if ( payload != null ){

											long	rand = ((Long)payload.get("r")).longValue();

											if ( buddy.addYGMMarker( rand )){

												new_ygm_buddies.add( buddy );
											}
										}
									}
								}catch( Throwable e ){

									log( null, "Read failed", e );
								}
							}else if ( 	type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ||
										type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE ){

								synchronized( DDBDetails.this ){
									
									ygm_active = false;
								}
								
								if ( new_ygm_buddies.size() > 0 || unauth_permitted ){

									BuddyPluginBuddy[] b = new BuddyPluginBuddy[new_ygm_buddies.size()];

									new_ygm_buddies.toArray( b );

									fireYGM( b );
								}
							}
						}
					},
					key,
					120*1000,
					DistributedDatabase.OP_EXHAUSTIVE_READ );

				boolean	write_bogus_ygm = false;

				synchronized( this ){

					if ( !bogus_ygm_written ){

						bogus_ygm_written = write_bogus_ygm = true;
					}
				}

				if ( write_bogus_ygm ){

					final String	reason2 = "Friend YGM write for myself";

					Map	envelope = new HashMap();

					DistributedDatabaseValue	value = ddb.createValue( BEncoder.encode( envelope ));

					logMessage( null, reason2 + " starts" );

					ddb.write(
						new DistributedDatabaseListener()
						{
							@Override
							public void
							event(
								DistributedDatabaseEvent		event )
							{
								int	type = event.getType();

								if ( type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE ){

									logMessage( null, reason2 + " complete"  );
								}
							}
						},
						key,
						value );
				}

			}catch( Throwable e ){

				logMessage( null, "YGM check failed", e );
				
			}finally{
				
				if ( !active ){
					
					synchronized( this ){
							
						ygm_active = false;
					}
				}
			}
		}
		
		protected void
		setMessagePending(
			BuddyPluginBuddy			buddy,
			operationListener			listener )

			throws BuddyPluginException
		{
			try{
				final String	reason = "Friend YGM write for " + buddy.getName();

				Map	payload = new HashMap();

				payload.put( "r", new Long( random.nextLong()));

				byte[] signed_payload = signAndInsert( payload, reason);

				Map	envelope = new HashMap();

				envelope.put( "pk", ecc_handler.getPublicKey( reason ));
				envelope.put( "ss", signed_payload );

				DistributedDatabaseValue	value = ddb.createValue( BEncoder.encode( envelope ));

				logMessage( buddy, reason + " starts: " + payload );

				DistributedDatabaseKey	key = getYGMKey( buddy.getRawPublicKey(), reason );

				ddb.write(
					new DistributedDatabaseListener()
					{
						@Override
						public void
						event(
							DistributedDatabaseEvent		event )
						{
							int	type = event.getType();

							if ( 	type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ||
									type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE ){

								logMessage( buddy, reason + " complete"  );

								listener.complete();
							}
						}
					},
					key,
					value );

			}catch( Throwable e ){

				try{
					rethrow( "Failed to publish YGM", e );

				}finally{

					listener.complete();
				}
			}
		}
		
		protected void
		updateKey()
		{
			synchronized( this ){

				PublishDetails new_publish = latest_publish.getCopy();

				new_publish.setPublicKey( null );

				last_payload = null;
				
				updatePublish( new_publish );
			}
		}

		protected void
		updatePublish()
		{
			if ( latest_publish.isEnabled()){

				updatePublish( latest_publish );
			}
		}
		
		protected void
		updatePublish(
			final PublishDetails	details )
		{
			latest_publish = details;

			if ( !ready_to_publish ){

				return;
			}

			publish_dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
							// only execute the most recent publish

						if ( publish_dispatcher.getQueueSize() > 0 ){

							return;
						}

						if ( details.isEnabled()){
							
							boolean	valid = false;
							
							InetSocketAddress[] isas = details.getIPs();
							
							if ( isas != null ){
								
								for ( InetSocketAddress isa: isas ){
									
									if ( isa == null ){
										
										continue;
									}
									
									if ( isa.isUnresolved()){
										
										String host = AddressUtils.getHostAddress( isa );
										
										if ( host != null && !host.isEmpty()){
											
											valid = true;
										}
									}else{
										
										valid = true;
									}
								}
							}
							
							if ( !valid ){
								
								updateIPWithDelay();
								
								return;
							}
						}
						
						updatePublishSupport( details );
					}
				});
		}

		protected void
		updatePublishSupport(
			PublishDetails	details )
		{
			byte[]	key_to_remove = null;

			PublishDetails	existing_details;

			boolean	log_this;

			synchronized( this ){

				log_this = !current_publish.getString().equals( details.getString());

				existing_details = current_publish;

				if ( !details.isEnabled()){

					if ( current_publish.isPublished()){

						key_to_remove	= current_publish.getPublicKey();
					}
				}else{

					if ( details.getPublicKey() == null ){

						try{
							details.setPublicKey( ecc_handler.getPublicKey( "Creating online status key" ));

						}catch( Throwable e ){

							log( null, "Failed to publish details", e );

							return;
						}
					}

					if ( current_publish.isPublished()){

						byte[]	existing_key = current_publish.getPublicKey();

						if ( !Arrays.equals( existing_key, details.getPublicKey())){

							key_to_remove = existing_key;
						}
					}
				}

				current_publish = details;
			}

			if ( key_to_remove != null ){

				log( null, "Removing old status publish: " + existing_details.getString());

				try{
					ddb.delete(
						new DistributedDatabaseListener()
						{
							@Override
							public void
							event(
								DistributedDatabaseEvent		event )
							{
							}
						},
						getStatusKey( key_to_remove, "Friend status de-registration for old key" ));

				}catch( Throwable e ){

					log( null, "Failed to remove existing publish", e );
				}
			}

			if ( details.isEnabled()){

					// ensure we have a sensible ip

				InetSocketAddress[] 	isas = details.getIPs();
				
				InetSocketAddress		isa 	= null;
				
				InetAddress				ia 	= null;
				InetAddress				ia6 	= null;

				
				if ( isas != null ){
					
					for ( InetSocketAddress a: isas ){
						
						InetAddress address = a.getAddress();
						
						if ( address == null || address instanceof Inet4Address || isas.length == 1 ){
							
							isa = a;
							ia	= address;
						}else{
							
							ia6	= address;
						}
					}
				}
				if ( ia != null && ( ia.isLoopbackAddress() || ia.isLinkLocalAddress() || ia.isSiteLocalAddress())){

					log( null, "Can't publish as ip address is invalid: " + details.getString());

					return;
				}

				details.setPublished( true );

				Map	payload = new HashMap();

				if ( details.getTCPPort() > 0 ){

					payload.put( "t", new Long(  details.getTCPPort() ));
				}

				if (  details.getUDPPort() > 0 ){

					payload.put( "u", new Long( details.getUDPPort() ));
				}
				
				if ( ia != null ){
					
					payload.put( "i", ia.getAddress());
					
				}else{
					
					String ip_str = AddressUtils.getHostAddress( isa );

					payload.put( "h", ip_str );
				}
				
				if ( ia6 != null ){
					
					if ( ia == null || !ia.equals( ia6 )){
					
						payload.put( "i6", ia6.getAddress());
					}
				}
				
				String	nick = details.getNickName();

				if ( nick != null ){

					if ( nick.length() > 32 ){

						nick = nick.substring( 0, 32 );
					}

					payload.put( "n", nick );
				}

				payload.put( "o", new Long( details.getOnlineStatus()));

				synchronized( this ){
					
					try{
						byte[] test = BEncoder.encode( payload );
					
							// remember that we republish periodically as an indicator of liveness
						
						if ( last_payload != null && Arrays.equals( last_payload, test )){
							
							long elapsed = SystemTime.getMonotonousTime() - last_publish_start;
							
							if ( elapsed < ( diversified?STATUS_REPUBLISH_PERIOD_WHEN_DIVERSIFIED:STATUS_REPUBLISH_PERIOD )){
								
								return;
							}
						}
						
						last_payload = test;
						
					}catch( Throwable e ){
						
					}
				}
				
				int	next_seq = ++status_seq;

				if ( next_seq == 0 ){

					next_seq = ++status_seq;
				}

				details.setSequence( next_seq );

				payload.put( "s", new Long( next_seq ));

				payload.put( "v", new Long( VERSION_CURRENT ));
				
				boolean	failed_to_get_key = true;

				try{
					byte[] data = BEncoder.encode( payload );

					DistributedDatabaseKey	key = getStatusKey( details.getPublicKey(), "My buddy status registration " + payload );

					byte[] signature = ecc_handler.sign( data, "Friend online status" );

					failed_to_get_key = false;

					byte[]	signed_payload = new byte[ 1 + signature.length + data.length ];

					signed_payload[0] = (byte)signature.length;

					System.arraycopy( signature, 0, signed_payload, 1, signature.length );
					System.arraycopy( data, 0, signed_payload, 1 + signature.length, data.length );

					DistributedDatabaseValue	value = ddb.createValue( signed_payload );

					final AESemaphore	sem = new AESemaphore( "BuddyPlugin:reg" );

					if ( log_this ){

						logMessage(null,  "Publishing status starts: " + details.getString());
					}

					last_publish_start = SystemTime.getMonotonousTime();

					ddb.write(
						new DistributedDatabaseListener()
						{
							private List<DistributedDatabaseContact>	write_contacts = new ArrayList<>();

							@Override
							public void
							event(
								DistributedDatabaseEvent		event )
							{
								int	type = event.getType();

								if ( type == DistributedDatabaseEvent.ET_VALUE_WRITTEN ){

									write_contacts.add( event.getContact());

								}else if ( 	type == DistributedDatabaseEvent.ET_DIVERSIFIED ){
									
									diversified = true;
									
								}else if ( 	type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ||
											type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE ){

									synchronized( publish_write_contacts ){

										publish_write_contacts.clear();

										publish_write_contacts.addAll( write_contacts );
									}

									sem.release();
								}
							}
						},
						key,
						value );

					sem.reserve();

					if ( log_this ){

						logMessage( null, "My status publish complete" );
					}
				}catch( Throwable e ){

					logMessage( null, "Failed to publish online status", e );

					if ( failed_to_get_key ){

						synchronized( this ){

							if ( republish_delay_event != null ){

								return;
							}

							if ( 	last_publish_start == 0 ||
									SystemTime.getMonotonousTime() - last_publish_start > STATUS_REPUBLISH_PERIOD ){

								log( null, "Rescheduling publish as failed to get key" );

								republish_delay_event = SimpleTimer.addEvent(
									"BuddyPlugin:republish",
									SystemTime.getCurrentTime() + 60*1000,
									new TimerEventPerformer()
									{
										@Override
										public void
										perform(
											TimerEvent event)
										{
											synchronized( BuddyPluginNetwork.this ){

												republish_delay_event = null;
											}

											if ( 	last_publish_start == 0 ||
													SystemTime.getMonotonousTime() - last_publish_start > STATUS_REPUBLISH_PERIOD ){

												if ( latest_publish.isEnabled()){

													updatePublish( latest_publish );
												}
											}
										}
									});

							}
						}
					}
				}
			}else{
				synchronized( this ){
				
					last_payload = null;
				}
			}
		}
		
		protected void
		updateBuddyStatus(
			BuddyPluginBuddy	buddy,
			Runnable			failed_callback )
		{
			try{
				final byte[]	public_key = buddy.getRawPublicKey();

				DistributedDatabaseKey	key =
					getStatusKey( public_key, "Friend status check for " + buddy.getName());

				ddb.read(
					new DistributedDatabaseListener()
					{
						private long	latest_time;
						private Map		status;
						private boolean	status_ipv4;
						private boolean	status_ipv6;
						
						@Override
						public void
						event(
							DistributedDatabaseEvent		event )
						{
							int	type = event.getType();

							if ( type == DistributedDatabaseEvent.ET_VALUE_READ ){

								try{
									DistributedDatabaseValue value = event.getValue();

									long time = value.getCreationTime();

									if ( time > latest_time ){

										byte[] signed_stuff = (byte[])value.getValue( byte[].class );

										Map	new_status = verifyAndExtract( signed_stuff, public_key );

										if ( new_status != null ){

											status = new_status;

											latest_time = time;
											
											InetSocketAddress ias = event.getContact().getAddress();
											
											InetAddress ia = ias.getAddress();
											
											if ( ia == null || ia instanceof Inet4Address ){
												
												status_ipv4 = true;
												
											}else{
												
												status_ipv6 = true;
											}
										}
									}
								}catch( Throwable e ){

									log( buddy, "Read failed", e );
								}
							}else if ( 	type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ||
										type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE ){

								if ( status == null ){

									failed_callback.run();

								}else{

									try{
										Long	l_tcp_port = (Long)status.get( "t" );
										Long	l_udp_port = (Long)status.get( "u" );

										int	tcp_port = l_tcp_port==null?0:l_tcp_port.intValue();
										int udp_port = l_udp_port==null?0:l_udp_port.intValue();

										byte[] b = (byte[])status.get("i");
										
										InetSocketAddress ias;
										
										if ( b != null ){
										
											InetAddress ip = InetAddress.getByAddress( b );
											
											ias = new InetSocketAddress( ip, 0 );
											
										}else{
											
											String host = MapUtils.getMapString( status, "h", null );
											
											ias = InetSocketAddress.createUnresolved( host, 0 );
										}
										
										if ( !status_ipv4 ){
											
												// too much of a pain to try and maintain both v4+v6 addresses for buddies
												// so override any ipv4 with any ipv6 if we've only had a reply from the
												// v6 dht. If someone is v6 only this should work out as they will
												// only have published to the v6 dht
											
											byte[] b_v6 = (byte[])status.get("i6");
											
											if ( b_v6 != null ){
												
												InetAddress ip = InetAddress.getByAddress( b_v6 );
												
												ias = new InetSocketAddress( ip, 0 );
											}
										}
										
										boolean valid = ias != null;
										
										if ( valid ){
											
											if ( ias.isUnresolved()){
												
												String host = AddressUtils.getHostAddress( ias );
												
												if ( host != null && !host.isEmpty()){
													
													valid = true;
												}
											}else{
												
												valid = true;
											}
										}
										
										if ( valid ){
											
											String	nick = decodeString((byte[])status.get( "n" ));
	
											Long	l_seq = (Long)status.get( "s" );
	
											int		seq = l_seq==null?0:l_seq.intValue();
	
											Long	l_os = (Long)status.get( "o" );
	
											int		os = l_os==null?BuddyPlugin.STATUS_ONLINE:l_os.intValue();
	
											Long	l_ver = (Long)status.get( "v" );
	
											int		ver = l_ver==null?VERSION_INITIAL:l_ver.intValue();
	
											buddy.statusCheckComplete( DDBDetails.this, latest_time, ias, tcp_port, udp_port, nick, os, seq, ver );
										}
									}catch( Throwable e ){

										failed_callback.run();

										log( buddy, "Status decode failed", e );
									}
								}
							}
						}
					},
					key,
					120*1000 );

			}catch( Throwable e ){

				failed_callback.run();

				log( buddy, "Friend status update failed: " + buddy.getString(), e );
			}
		}
		
		protected DistributedDatabaseKey
		getStatusKey(
			byte[]	public_key,
			String	reason )

			throws Exception
		{
			byte[]	key_prefix = "azbuddy:status".getBytes();

			byte[]	key_bytes = new byte[ key_prefix.length + public_key.length ];

			System.arraycopy( key_prefix, 0, key_bytes, 0, key_prefix.length );
			System.arraycopy( public_key, 0, key_bytes, key_prefix.length, public_key.length );

			DistributedDatabaseKey key = ddb.createKey( key_bytes, reason );

			return( key );
		}

		protected DistributedDatabaseKey
		getYGMKey(
			byte[]	public_key,
			String	reason )

			throws Exception
		{
			byte[]	key_prefix = "azbuddy:ygm".getBytes();

			byte[]	key_bytes = new byte[ key_prefix.length + public_key.length ];

			System.arraycopy( key_prefix, 0, key_bytes, 0, key_prefix.length );
			System.arraycopy( public_key, 0, key_bytes, key_prefix.length, public_key.length );

			DistributedDatabaseKey key = ddb.createKey( key_bytes, reason );

			return( key );
		}
		
		private void
		closedown()
		{
			boolean	restarting = CoreFactory.isCoreAvailable() ? CoreFactory.getSingleton().isRestarting() : false;

			logMessage( null, "   closing buddy connections" );

			for (int i=0;i<buddies.size();i++){

				((BuddyPluginBuddy)buddies.get(i)).sendCloseRequest( restarting );
			}

			if ( !restarting ){

				logMessage( null, "   updating online status" );

				List	contacts = new ArrayList();

				synchronized( publish_write_contacts ){

					contacts.addAll( publish_write_contacts );
				}

				byte[] key_to_remove;

				synchronized( this ){

					key_to_remove	= current_publish.getPublicKey();
				}

				if ( contacts.size() == 0 || key_to_remove == null ){

					return;
				}

				DistributedDatabaseContact[] contact_a = new DistributedDatabaseContact[contacts.size()];

				contacts.toArray( contact_a );

				try{
					ddb.delete(
						new DistributedDatabaseListener()
						{
							@Override
							public void
							event(
								DistributedDatabaseEvent		event )
							{
								if ( event.getType() == DistributedDatabaseEvent.ET_VALUE_DELETED ){

									// System.out.println( "Deleted status from " + event.getContact().getName());
								}
							}
						},
						getStatusKey( key_to_remove, "Friend status de-registration for closedown" ),
						contact_a );

				}catch( Throwable e ){

					log( null, "Failed to remove existing publish", e );
				}
			}
		}
	}
	
	private static class
	PublishDetails
		implements Cloneable
	{
		private final String		network;
		
		private byte[]				public_key;
		private InetSocketAddress[]	ips;
		private int					tcp_port;
		private int					udp_port;
		private String				nick_name;
		private int					online_status		= BuddyPlugin.STATUS_ONLINE;

		private boolean				enabled;
		private boolean				published;

		private int					sequence;

		private
		PublishDetails(
			String		_network )
		{
			network	= _network;
		}
		
		protected PublishDetails
		getCopy()
		{
			try{
				PublishDetails copy = (PublishDetails)clone();

				copy.published = false;

				return( copy );

			}catch( Throwable e ){

				return( null);
			}
		}
		
		protected String
		getNetwork()
		{
			return( network );
		}
		
		protected boolean
		isPublished()
		{
			return( published );
		}

		protected void
		setPublished(
			boolean		b )
		{
			published	= b;
		}

		protected boolean
		isEnabled()
		{
			return( enabled );
		}

		protected void
		setEnabled(
			boolean	_enabled )
		{
			enabled	= _enabled;
		}

		protected void
		setSequence(
			int		seq )
		{
			sequence = seq;
		}

		protected int
		getSequence()
		{
			return( sequence );
		}

		protected byte[]
		getPublicKey()
		{
			return( public_key );
		}

		protected void
		setPublicKey(
			byte[]		k )
		{
			public_key	= k;
		}

		protected InetSocketAddress[]
		getIPs()
		{
			return( ips );
		}

		protected void
		setIPs(
			InetSocketAddress[]	_ips )
		{
			ips	= _ips;
		}

		protected int
		getTCPPort()
		{
			return( tcp_port );
		}

		protected void
		setTCPPort(
			int		_port )
		{
			tcp_port = _port;
		}

		protected int
		getUDPPort()
		{
			return( udp_port );
		}

		protected void
		setUDPPort(
			int		_port )
		{
			udp_port = _port;
		}

		protected String
		getNickName()
		{
			return( nick_name );
		}

		protected void
		setNickName(
			String		 n )
		{
			nick_name	= n;
		}

		protected int
		getOnlineStatus()
		{
			return( online_status );
		}

		protected void
		setOnlineStatus(
			int		_status )
		{
			online_status = _status;
		}

		protected String
		getString()
		{
			String ip_str = "";
			
			InetSocketAddress[]	ips = getIPs();
			
			if ( ips == null ){
				
				ip_str = "null";
				
			}else{
				
				for ( InetSocketAddress a: ips ){
					
					ip_str += (ip_str.isEmpty()?"":"/") + a;
				}
			}
			
			return( "enabled=" + enabled + ",ip=" + ip_str + ",tcp=" + tcp_port + ",udp=" + udp_port + ",stat=" + online_status + ",key=" + (public_key==null?"<none>":Base32.encode( public_key )));
		}
	}

	protected interface
	operationListener
	{
		public void
		complete();
	}
}
