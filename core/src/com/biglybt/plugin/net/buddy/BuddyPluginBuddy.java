/*
 * Created on Apr 1, 2008
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

import java.io.File;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionManager;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.messaging.MessageException;
import com.biglybt.pif.messaging.generic.GenericMessageConnection;
import com.biglybt.pif.messaging.generic.GenericMessageConnectionListener;
import com.biglybt.pif.messaging.generic.GenericMessageEndpoint;
import com.biglybt.pif.messaging.generic.GenericMessageRegistration;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pif.utils.security.SEPublicKey;
import com.biglybt.pif.utils.security.SEPublicKeyLocator;
import com.biglybt.plugin.net.buddy.BuddyPluginNetwork.DDBDetails;


public class
BuddyPluginBuddy
{
	private static final boolean TRACE = BuddyPluginNetwork.TRACE;

	private static final int CONNECTION_IDLE_TIMEOUT	= 5*60*1000;
	private static final int CONNECTION_KEEP_ALIVE		= 1*60*1000;

	private static final int MAX_ACTIVE_CONNECTIONS		= 5;
	private static final int MAX_QUEUED_MESSAGES		= 256;

	private static final int RT_REQUEST_DATA	= 1;

	private static final int RT_REPLY_DATA		= 2;
	private static final int RT_REPLY_ERROR		= 99;


	private BuddyPluginNetwork		plugin_network;
	
	private long			created_time;
	private int				subsystem;
	private boolean			authorised;
	private String			public_key;
	private String			nick_name;
	private String			my_name;
	private List<Long>		recent_ygm;
	private boolean			is_transient;
	
	private int				last_status_seq;

	private long				post_time;
	
	private InetSocketAddress	current_ip;
	private InetSocketAddress	latest_ipv4;
	private InetSocketAddress	latest_ipv6;
	
	private int					tcp_port;
	private int					udp_port;
	
	private int				online_status	= BuddyPlugin.STATUS_ONLINE;	// default

	private int				version		= BuddyPluginNetwork.VERSION_CHAT;	// assume everyone now supports chat

	private boolean			online;
	private long			last_time_online;

	private long			status_check_count;
	private long			last_status_check_time;

	private boolean			check_active;

	private List<buddyConnection>		connections	= new ArrayList<>();
	private List<buddyMessage>			messages	= new ArrayList<>();
	private buddyMessage				current_message;

	private int	next_connection_id;
	private int	next_message_id;

	private boolean	ygm_active;
	private boolean	ygm_pending;

	private long 	latest_ygm_time;
	private String	last_message_received;

	private Set<Long>		offline_seq_set;

	private int		message_out_count;
	private int		message_in_count;
	private int		message_out_bytes;
	private int		message_in_bytes;

	private String	received_frag_details = "";

	private BuddyPluginBuddyMessageHandler		persistent_msg_handler;

	private Map<Object,Object>		user_data = new LightHashMap<>();

	private boolean	keep_alive_outstanding;
	private volatile long	last_connect_attempt	= SystemTime.getCurrentTime();
	private volatile int	consec_connect_fails;

	private long last_auto_reconnect	= -1;

	private Object				rss_lock = new Object();

	private Set<String>			rss_local_cats;
	private Set<String>			rss_remote_cats;
	private Set<String>			rss_cats_read;

	private List<String>		profile_info;
	private boolean				profile_info_outstanding;
	private long				profile_info_last = -1;
	
	private AESemaphore			outgoing_connect_sem = new AESemaphore( "BPB:outcon", 1 );

	private volatile boolean	closing;
	private volatile boolean	destroyed;

	protected
	BuddyPluginBuddy(
		BuddyPluginNetwork	_plugin_network,
		long				_created_time,
		int					_subsystem,
		boolean				_authorised,
		String				_pk,
		String				_nick_name,
		String				_my_name,
		int					_version,
		String				_rss_local_cats,
		String				_rss_remote_cats,
		int					_last_status_seq,
		long				_last_time_online,
		List<Long>			_recent_ygm,
		boolean				_is_transient )
	{
		plugin_network		= _plugin_network;
		created_time		= _created_time;
		subsystem			= _subsystem;
		authorised			= _authorised;
		public_key 			= _pk;
		nick_name			= _nick_name;
		my_name				= _my_name;
		version				= Math.max( version, _version );
		rss_local_cats		= stringToCats( _rss_local_cats );
		rss_remote_cats		= stringToCats( _rss_remote_cats );
		last_status_seq		= _last_status_seq;
		last_time_online	= _last_time_online;
		recent_ygm			= _recent_ygm;
		is_transient		= _is_transient;
		
		persistent_msg_handler = new BuddyPluginBuddyMessageHandler( this, FileUtil.newFile(plugin_network.getBuddyConfigDir(), public_key ));
	}

	public BuddyPluginNetwork
	getPluginNetwork()
	{
		return( plugin_network );
	}
	
	public boolean
	isPublicNetwork()
	{
		return( plugin_network.isPublicNetwork());
	}
	
	protected void
	setInitialStatus(
		long	now,
		int		num_buddies )
	{
			// for inactive buddies we schedule their status checks so that on average we don't
			// do more than one check every 5 minutes

		if ( 	last_time_online == 0 &&
				now - created_time > 7*24*60*60*1000L ){

			last_status_check_time = now + RandomUtils.nextInt( 5*60*1000 * num_buddies );
		}
	}

	protected BuddyPluginNetwork
	getPlugin()
	{
		return( plugin_network );
	}

	public BuddyPluginBuddyMessageHandler
	getMessageHandler()
	{
		return( persistent_msg_handler );
	}

	protected void
	persistentDispatchPending()
	{
		plugin_network.persistentDispatchPending( this );
	}

	protected void
	checkPersistentDispatch()
	{
		persistent_msg_handler.checkPersistentDispatch();
	}

	protected void
	persistentDispatch()
	{
		persistent_msg_handler.persistentDispatch();
	}

	public Map
	readConfigFile(
		File		name )
	{
		return( plugin_network.readConfigFile( name ));
	}

	public boolean
	writeConfigFile(
		File		name,
		Map			data )
	{
		return( plugin_network.writeConfigFile( name, data ));
	}

	protected long
	getCreatedTime()
	{
		return( created_time );
	}

	public int
	getSubsystem()
	{
		return( subsystem );
	}

	protected void
	setSubsystem(
		int		_s )
	{
		subsystem = _s;
	}

	public boolean
	isAuthorised()
	{
		return( authorised );
	}

	protected void
	setAuthorised(
		boolean		_a )
	{
		authorised = _a;
	}

	public boolean
	isTransient()
	{
		return( is_transient );
	}
	
	public void
	setTransient(
		boolean		b )
	{
		is_transient = b;
	}
	
	public String
	getPublicKey()
	{
		return( public_key );
	}

	protected byte[]
	getRawPublicKey()
	{
		return( Base32.decode( public_key ));
	}

	protected String
	getShortString()
	{
		return( public_key.substring( 0, 16 ) + "..." );
	}

	public String
	getNickName()
	{
		return( nick_name );
	}

	public int
	getVersion()
	{
		return( version );
	}

	protected void
	setVersion(
		int		v )
	{
		if ( version < v ){

			version = v;

			plugin_network.fireDetailsChanged( this );
		}
	}

	public String
	getLocalAuthorisedRSSTagsOrCategoriesAsString()
	{
		synchronized( rss_lock ){

			return( catsToString( rss_local_cats ));
		}
	}

	public Set<String>
  	getLocalAuthorisedRSSTagsOrCategories()
  	{
		synchronized( rss_lock ){

			return( rss_local_cats );
		}
  	}

	public void
	addLocalAuthorisedRSSTagOrCategory(
		String	category )
	{
		category = BuddyPlugin.normaliseCat( category );

		boolean dirty;

		synchronized( rss_lock ){

			if ( rss_local_cats == null ){

				rss_local_cats = new HashSet<>();
			}

			if ( dirty = !rss_local_cats.contains( category )){

				rss_local_cats.add( category );
			}
		}

		if ( dirty ){

			plugin_network.setConfigDirty();

			plugin_network.fireDetailsChanged( this );

				// tell buddy of change

			if ( isConnected()){

				sendKeepAlive();
			}
		}
	}

	public void
	removeLocalAuthorisedRSSTagOrCategory(
		String	category )
	{
		category = BuddyPlugin.normaliseCat( category );

		boolean	dirty;

		synchronized( rss_lock ){

			if ( rss_local_cats == null ){

				return;

			}else{

				dirty = rss_local_cats.remove( category );
			}
		}

		if ( dirty ){

			plugin_network.setConfigDirty();

			plugin_network.fireDetailsChanged( this );

				// tell buddy of change

			if ( isConnected()){

				sendKeepAlive();
			}
		}
	}

	public void
	setLocalAuthorisedRSSTagsOrCategories(
		String			new_cats )
	{
		setLocalAuthorisedRSSTagsOrCategories( stringToCats( new_cats ));
	}

	public void
	setLocalAuthorisedRSSTagsOrCategories(
		Set<String>		new_cats )
	{
		BuddyPlugin.normaliseCats( new_cats );

		boolean dirty;

		synchronized( rss_lock ){

			if ( dirty = !catsIdentical( new_cats, rss_local_cats) ){

				rss_local_cats = new_cats;
			}
		}

		if ( dirty ){

			plugin_network.setConfigDirty();

			plugin_network.fireDetailsChanged( this );

				// tell buddy of change

			if ( isConnected()){

				sendKeepAlive();
			}
		}
	}

	public Set<String>
  	getRemoteAuthorisedRSSTagsOrCategories()
  	{
  		return( rss_remote_cats );
  	}

	public String
	getRemoteAuthorisedRSSTagsOrCategoriesAsString()
	{
		return( catsToString( rss_remote_cats ));
	}

	protected void
	setRemoteAuthorisedRSSTagsOrCategories(
		Set<String>		new_cats )
	{
		BuddyPlugin.normaliseCats( new_cats );

		boolean	dirty;

		synchronized( rss_lock ){

			if ( dirty = !catsIdentical( new_cats, rss_remote_cats) ){

				rss_remote_cats = new_cats;
			}
		}

		if ( dirty ){

			plugin_network.setConfigDirty();

			plugin_network.fireDetailsChanged( this );
		}
	}

	public boolean
	isLocalRSSTagOrCategoryAuthorised(
		String	category )
	{
		category = BuddyPlugin.normaliseCat( category );

		synchronized( rss_lock ){

			if ( rss_local_cats != null ){

				return( rss_local_cats.contains( category ));
			}

			return( false );
		}
	}

	public boolean
	isRemoteRSSTagOrCategoryAuthorised(
		String	category )
	{
		category = BuddyPlugin.normaliseCat( category );

		synchronized( rss_lock ){

			if ( rss_remote_cats != null ){

				return( rss_remote_cats.contains( category ));
			}

			return( false );
		}
	}

	protected void
	localRSSTagOrCategoryRead(
		String		str )
	{
		boolean dirty;

		synchronized( rss_lock ){

			if ( rss_cats_read == null ){

				rss_cats_read = new HashSet<>();
			}

			dirty = rss_cats_read.add( str );
		}

		if ( dirty ){

			// not persisted currently - plugin.setConfigDirty();

			plugin_network.fireDetailsChanged( this );
		}
	}

	public String
	getLocalReadTagsOrCategoriesAsString()
	{
		synchronized( rss_lock ){

			return( catsToString( rss_cats_read ));
		}
	}

	public URL
	getSubscriptionURL(
		String		cat )
	{
		String url = "azplug:?id=azbuddy&name=Friends&arg=";

		String arg = "pk=" + getPublicKey() + "&cat=" + cat;

		try{
			url += URLEncoder.encode( arg, "UTF-8" );

			return( new URL( url ));

		}catch( Throwable e ){

			Debug.out( e );

			return( null );
		}
	}

	public void
	subscribeToCategory(
		String	cat )

		throws BuddyPluginException
	{
		SubscriptionManager manager = SubscriptionManagerFactory.getSingleton();

		if ( manager == null ){

			throw( new BuddyPluginException( "AZ3 subsystem not available" ));
		}

		try{
			manager.subscribeToRSS(
				getName() + ": " + cat,
				getSubscriptionURL(cat),
				15,
				false,
				getPublicKey() + ":" + cat );

		}catch( Throwable e ){

			throw( new BuddyPluginException( "Failed to add subscription", e ));
		}
	}

	public boolean
	isSubscribedToCategory(
		String	cat )
	{
		SubscriptionManager manager = SubscriptionManagerFactory.getSingleton();

		if ( manager != null ){

			String u = getSubscriptionURL(cat).toExternalForm();
			
			try{
				Subscription[] subs = manager.getSubscriptions( true );
				
				for ( Subscription s: subs ){
					
					Engine engine = s.getEngine();
					
					if ( engine instanceof WebEngine ){
						
						if ( u.equals(((WebEngine)engine).getSearchUrl( true ))){
							
							return( true );
						}
					}
				}
			}catch( Throwable e ){
	
				
			}
		}
		
		return( false );
	}

	
	protected String
	catsToString(
		Set<String>	cats )
	{
		if ( cats == null || cats.size() == 0 ){

			return( null );
		}

		String	str = "";

		for (String s:cats ){

			str += (str.length()==0?"":",") + s;
		}

		return( str );
	}

	protected boolean
	catsIdentical(
		Set<String>	c1,
		Set<String>	c2 )
	{
		if ( c1 == null && c2 == null ){

			return( true );

		}else if ( c1 == null || c2 == null ){

			return( false );

		}else{

			return( c1.equals( c2 ));
		}
	}

	protected Set<String>
	stringToCats(
		String	str )
	{
		if ( str == null ){

			return( null );
		}

		String[] bits = str.split( "," );

		Set<String> res = new HashSet<>(bits.length);

		for ( String b: bits ){

			b = b.trim();

			if ( b.length() > 0 ){

				res.add( b );
			}
		}

		if ( res.size() == 0 ){

			return( null );
		}

		return( res );
	}

	public int
	getOnlineStatus()
	{
		return( online_status );
	}

	protected void
	setOnlineStatus(
		int		s )
	{
		if ( online_status != s ){

			online_status = s;

			plugin_network.fireDetailsChanged( this );
		}
	}

	public String
	getName()
	{
		String res;
		
		if ( nick_name != null ){

			res = nick_name;
			
		}else{

			res = getShortString();
		}
		
		if ( my_name == null ){
			
			return( res );
			
		}else{
			
			return( my_name + " (" + res + ")" );
		}
	}
	
	public String
	getMyName()
	{
		return( my_name );
	}
	
	public void
	setMyName(
		String		_my_name )
	{
		if ( _my_name != null ){
			
			_my_name = _my_name.trim();
			
			if ( _my_name.isEmpty()){
				
				_my_name = null;
			}
		}
		
		my_name = _my_name;
		
		plugin_network.fireDetailsChanged( this );
	}

	public void
	remove()
	{
		persistent_msg_handler.destroy();

		plugin_network.removeBuddy( this );
	}

	public InetSocketAddress
	getIP()
	{
		return( current_ip );
	}

	public InetSocketAddress
	getLatestIP(
		boolean	v4 )
	{
		return( v4?latest_ipv4:latest_ipv6 );
	}
	
	public InetSocketAddress
	getAdjustedIP()
	{
		if ( current_ip == null ){

			return( null );
		}

		if ( current_ip.isUnresolved()){
			
			return( current_ip );
		}
		
		InetSocketAddress address = new InetSocketAddress( current_ip.getAddress(), tcp_port );

		InetSocketAddress adjusted_address = AddressUtils.adjustTCPAddress( address, true );

		if ( adjusted_address != address ){

			return( adjusted_address );
		}

		address = new InetSocketAddress( current_ip.getAddress(), udp_port );

		adjusted_address = AddressUtils.adjustUDPAddress( address, true );

		if ( adjusted_address != address ){

			return( adjusted_address );
		}

		return( current_ip );
	}

	public List<InetSocketAddress>
	getAdjustedIPs()
	{
		List<InetSocketAddress>	result = new ArrayList<>();

		if ( current_ip == null ){

			return( result );
		}

		InetSocketAddress adjusted = getAdjustedIP();

		if ( adjusted == current_ip ){

			result.add( current_ip );

		}else{

			List<String> l = AddressUtils.getLANAddresses( AddressUtils.getHostAddress( adjusted ));

			for (int i=0;i<l.size();i++){

				try{
					result.add( new InetSocketAddress( InetAddress.getByName(l.get(i)),0));

				}catch( Throwable e ){

				}
			}
		}

		return( result );
	}


	public int
	getTCPPort()
	{
		return( tcp_port );
	}

	public int
	getUDPPort()
	{
		return( udp_port );
	}

	public boolean
	isOnline(
		boolean	is_connected )
	{
		if ( destroyed ){
			
			return( false );
		}
		
		boolean	connected = isConnected();

			// if we're connected then we're online whatever

		if ( connected ){

			return( true );
		}

		if ( !online ){

			return( false );
		}

		if ( is_connected ){

			return( false );

		}else{

			return( true );
		}
	}

	protected boolean
	isIdle()
	{
		synchronized( this ){

			return( connections.size() == 0 );
		}
	}

	public long
	getLastTimeOnline()
	{
		return( last_time_online );
	}

	public interface
	ProfileUpdateInformer
	{
		public void
		received(
			List<String>		profile );
	}
	
	public void
	getProfileInfo(
		ProfileUpdateInformer	informer )
	{
		synchronized( this ){
			
			if ( profile_info != null ){
				
				informer.received( profile_info );
			}
			
			long now = SystemTime.getMonotonousTime();
			
			if ( profile_info_outstanding || now - profile_info_last < 60*1000 ){
				
				return;
			}
			
			profile_info_last			= now;
			profile_info_outstanding 	= true;
		}
		
		try{
			plugin_network.getAZ2Handler().sendAZ2ProfileInfo(
				this,
				new HashMap<>(),
				new BuddyPluginAZ2TrackerListener(){
					
					@Override
					public Map<String, Object> 
					messageReceived(
						BuddyPluginBuddy 		buddy, 
						Map<String, Object> 	message)
					{
						InetSocketAddress ip = buddy.getIP();
						
						List<String> result = new ArrayList<>();

						synchronized( BuddyPluginBuddy.this ){
							
							profile_info_outstanding = false;
							
							List<String> info = BDecoder.decodeStrings((List)message.get( "props" ));							
							
							for ( String i: info ){
							
								if ( ip != null ){

									i = i.replaceAll( "(?i)\\Q${ip}\\E", AddressUtils.getHostAddress( ip ));
								}
								
								result.add( i );
							}
							
							profile_info = result;
						}
						
						informer.received( result );
						
						return( null );
					}
					
					@Override
					public void
					messageFailed(
						BuddyPluginBuddy buddy, 
						Throwable cause)
					{
						synchronized( BuddyPluginBuddy.this ){
							
							profile_info_outstanding = false;
						}
					}
				});
			
		}catch( Throwable e ){
		
			synchronized( this ){
				
				profile_info_outstanding = false;
			}
		}
	}
	
	public BuddyPlugin.CryptoResult
	encrypt(
		byte[]		payload )

		throws BuddyPluginException
	{
		return( plugin_network.encrypt( this, payload ));
	}

	public BuddyPlugin.CryptoResult
	decrypt(
		byte[]		payload )

		throws BuddyPluginException
	{
		return( plugin_network.decrypt( this, payload, getName() ));
	}

	public boolean
	verify(
		byte[]		payload,
		byte[]		signature )

		throws BuddyPluginException
	{

		return( plugin_network.verify( this, payload, signature ));
	}

	public BuddyPluginBuddyMessage
	storeMessage(
		int		type,
		Map		msg )
	{
		return( persistent_msg_handler.storeExplicitMessage( type, msg ));
	}

	public List<BuddyPluginBuddyMessage>
	retrieveMessages(
		int		type )
	{
		return( persistent_msg_handler.retrieveExplicitMessages( type ));
	}

	public void
	setMessagePending()

		throws BuddyPluginException
	{
		synchronized( this ){

			if ( ygm_active ){

				ygm_pending = true;

				return;
			}

			ygm_active = true;
		}

		plugin_network.setMessagePending(
			this,
			new BuddyPluginNetwork.operationListener()
			{
				@Override
				public void
				complete()
				{
					boolean	retry;

					synchronized( BuddyPluginBuddy.this ){

						ygm_active = false;

						retry = ygm_pending;

						ygm_pending = false;
					}

					if ( retry ){

						try{
							setMessagePending();

						}catch( BuddyPluginException e ){

							log( "Failed to send YGM", e );
						}
					}
				}
			});
	}

	public long
	getLastMessagePending()
	{
		return( latest_ygm_time );
	}

	protected boolean
	addYGMMarker(
		long		marker )
	{
		Long	l = new Long( marker );

		synchronized( this ){

			if ( recent_ygm == null ){

				recent_ygm = new ArrayList<>();
			}

			if ( recent_ygm.contains( l )){

				return( false );
			}

			recent_ygm.add( l );

			if ( recent_ygm.size() > 16 ){

				recent_ygm.remove(0);
			}

			latest_ygm_time = SystemTime.getCurrentTime();
		}

		plugin_network.setConfigDirty();

		plugin_network.fireDetailsChanged( this );

		return( true );
	}

	protected void
	setLastMessageReceived(
		String		str )
	{
		last_message_received = str;

		plugin_network.fireDetailsChanged( this );
	}

	public String
	getLastMessageReceived()
	{
		return( last_message_received==null?"":last_message_received );
	}

	protected List<Long>
	getYGMMarkers()
	{
		synchronized( this ){

			return( recent_ygm==null?null: new ArrayList<>(recent_ygm));
		}
	}

	protected int
	getLastStatusSeq()
	{
		synchronized( this ){

			return( last_status_seq );
		}
	}

	protected void
	buddyConnectionEstablished(
		boolean		outgoing )
	{
		buddyActive();
	}

	protected void
	buddyMessageSent(
		int			size,
		boolean		record_active )
	{
		message_out_count++;
		message_out_bytes += size;

		if ( record_active ){

			buddyActive();
		}
	}

	protected void
	buddyMessageReceived(
		int		size )
	{
		message_in_count++;
		message_in_bytes += size;

		received_frag_details = "";

		buddyActive();
	}

	protected void
	buddyMessageFragmentReceived(
		int		num_received,
		int		total )
	{
		received_frag_details = num_received + "/" + total;

		plugin_network.fireDetailsChanged( this );
	}

	public String
	getMessageInFragmentDetails()
	{
		return( received_frag_details );
	}

	public int
	getMessageInCount()
	{
		return( message_in_count );
	}

	public int
	getMessageOutCount()
	{
		return( message_out_count );
	}

	public int
	getBytesInCount()
	{
		return( message_in_bytes );
	}

	public int
	getBytesOutCount()
	{
		return( message_out_bytes );
	}

	public boolean
	isConnected()
	{
		boolean connected = false;

		synchronized( this ){

			for (int i=0;i<connections.size();i++){

				buddyConnection c = (buddyConnection)connections.get(i);

				if ( c.isConnected() && !c.hasFailed()){

					connected = true;
				}
			}
		}

		return( connected );
	}

	protected void
	buddyActive()
	{
		long	now = SystemTime.getCurrentTime();

		synchronized( this ){

			last_time_online			= now;
			online						= true;
		}

		persistentDispatchPending();

		plugin_network.fireDetailsChanged( this );
	}

	public void
	ping()
		throws BuddyPluginException
	{
		plugin_network.checkAvailable();

		try{
			Map	ping_request = new HashMap();

			ping_request.put( "type", new Long( BuddyPluginNetwork.RT_INTERNAL_REQUEST_PING ));

			sendMessage(
				BuddyPluginNetwork.SUBSYSTEM_INTERNAL,
				ping_request,
				60*1000,
				new BuddyPluginBuddyReplyListener()
				{
					@Override
					public void
					replyReceived(
						BuddyPluginBuddy	from_buddy,
						Map					reply )
					{
						log( "Ping reply received:" + reply );
					}

					@Override
					public void
					sendFailed(
						BuddyPluginBuddy		to_buddy,
						BuddyPluginException	cause )
					{
						log( "Ping failed to " + getString(), cause );
					}
				});

		}catch( Throwable e ){

			throw( new BuddyPluginException( "Ping failed", e ));
		}
	}

	protected void
	sendCloseRequest(
		boolean		restarting )
	{
		List	to_send = new ArrayList();

		synchronized( this ){

			closing	= true;

			for (int i=0;i<connections.size();i++){

				buddyConnection c = (buddyConnection)connections.get(i);

				if ( c.isConnected() && !c.hasFailed() && !c.isActive()){

					to_send.add( c );
				}
			}
		}

		for (int i=0;i<to_send.size();i++){

			buddyConnection c = (buddyConnection)to_send.get(i);

			try{
				Map	close_request = new HashMap();

				close_request.put( "type", new Long( BuddyPluginNetwork.RT_INTERNAL_REQUEST_CLOSE ));

				close_request.put( "r", new Long( restarting?1:0));

				close_request.put( "os", new Long( plugin_network.getCurrentStatusSeq(c.getDDBDetails())));

				final buddyMessage	message =
					new buddyMessage( BuddyPluginNetwork.SUBSYSTEM_INTERNAL, close_request, 60*1000 );

				message.setListener(
						new BuddyPluginBuddyReplyListener()
						{
							@Override
							public void
							replyReceived(
								BuddyPluginBuddy	from_buddy,
								Map					reply )
							{
								log( "Close reply received:" + reply );
							}

							@Override
							public void
							sendFailed(
								BuddyPluginBuddy		to_buddy,
								BuddyPluginException	cause )
							{
								log( "Close failed to " + getString(), cause );
							}
						});

				c.sendCloseMessage( message );

			}catch( Throwable e ){

				log( "Close request failed", e );
			}
		}
	}

	protected void
	receivedCloseRequest(
		Map		request )
	{
		List<buddyConnection>	closing = new ArrayList<>();

		synchronized( this ){

			closing.addAll( connections );
		}

		for (int i=0;i<closing.size();i++){

			((buddyConnection)closing.get(i)).remoteClosing();
		}

		try{
			boolean	restarting = ((Long)request.get( "r" )).longValue() == 1;

			if ( restarting ){

				logMessage( "restarting" );

			}else{

				logMessage( "going offline" );

				boolean	details_change = false;

				synchronized( this ){

					if ( offline_seq_set == null ){

						offline_seq_set = new HashSet<>();
					}

					offline_seq_set.add( new Long( last_status_seq ));

					offline_seq_set.add((Long)request.get( "os" ));

					if ( online ){

						online					= false;
						consec_connect_fails	= 0;

						details_change	= true;
					}
				}

				if ( details_change ){

					plugin_network.fireDetailsChanged( this );
				}
			}
		}catch( Throwable e ){

			Debug.out( "Failed to decode close request", e );
		}
	}

	public void
	sendMessage(
		final int								subsystem,
		final Map								content,
		final int								timeout_millis,
		final BuddyPluginBuddyReplyListener		listener )

		throws BuddyPluginException
	{
		plugin_network.checkAvailable();

		boolean	wait = false;

		if ( current_ip == null ){

			synchronized( this ){

				wait = check_active;
			}

			if ( !wait ){

				if ( SystemTime.getCurrentTime() - last_status_check_time > 30*1000 ){

					plugin_network.updateBuddyStatus( this );

					wait	= true;
				}
			}
		}

		if ( wait ){

			new AEThread2( "BuddyPluginBuddy:sendWait", true )
			{
				@Override
				public void
				run()
				{
					try{
						long	start = SystemTime.getCurrentTime();

						for (int i=0;i<20;i++){

							if ( current_ip != null ){

								break;
							}

							Thread.sleep( 1000 );
						}

						long	elapsed = SystemTime.getCurrentTime() - start;

						int new_tm = timeout_millis;

						if ( elapsed > 0 && timeout_millis > 0 ){

							new_tm -= elapsed;

							if ( new_tm <= 0 ){

								listener.sendFailed( BuddyPluginBuddy.this, new BuddyPluginException( "Timeout" ));

								return;
							}
						}

						sendMessageSupport( content, subsystem, new_tm, listener );

					}catch( Throwable e ){

						if ( e instanceof BuddyPluginException ){

							listener.sendFailed( BuddyPluginBuddy.this, (BuddyPluginException)e);
						}else{

							listener.sendFailed( BuddyPluginBuddy.this, new BuddyPluginException( "Send failed", e ));
						}
					}
				}
			}.start();

		}else{

			sendMessageSupport( content, subsystem, timeout_millis, listener );
		}
	}

	protected void
	sendMessageSupport(
		final Map								content,
		final int								subsystem,
		final int								timeout_millis,
		final BuddyPluginBuddyReplyListener		original_listener )

		throws BuddyPluginException
	{
		if ( isTransient()){
			
			long type = (Long)content.get( "type" );
			
			if ( subsystem != BuddyPluginNetwork.SUBSYSTEM_AZ2 || type != BuddyPluginAZ2.RT_AZ2_REQUEST_PROFILE_INFO ){
		
				throw( new BuddyPluginException( "Message " + subsystem + "/" + type + " not enabled for transient buddies" ));
			}
		}
		
		boolean too_many_messages = false;

		synchronized( this ){

			too_many_messages = messages.size() >= MAX_QUEUED_MESSAGES;
		}

		if ( too_many_messages ){

			throw( new BuddyPluginException( "Too many messages queued" ));
		}

		final buddyMessage	message = new buddyMessage( subsystem, content, timeout_millis );

		BuddyPluginBuddyReplyListener	listener_delegate =
			new BuddyPluginBuddyReplyListener()
			{
				@Override
				public void
				replyReceived(
					BuddyPluginBuddy		from_buddy,
					Map						reply )
				{
					// logMessage( "Msg " + message.getString() + " ok" );

					try{
						synchronized( BuddyPluginBuddy.this ){

							if ( current_message != message ){

								Debug.out( "Inconsistent: reply received not for current message" );
							}

							current_message = null;
						}

						original_listener.replyReceived( from_buddy, reply );

					}finally{

						dispatchMessage();
					}
				}

				@Override
				public void
				sendFailed(
					BuddyPluginBuddy		to_buddy,
					BuddyPluginException	cause )
				{
					logMessage( "Msg " + message.getString() + " failed: " + Debug.getNestedExceptionMessage( cause ));

					try{
							// only try and reconcile this failure with the current message if
							// the message has actually been sent

						boolean	was_active;

						if ( cause instanceof BuddyPluginTimeoutException ){

							was_active = ((BuddyPluginTimeoutException)cause).wasActive();

						}else{

							was_active = true;
						}

						if ( was_active ){

							synchronized( BuddyPluginBuddy.this ){

								if ( current_message != message ){

									Debug.out( "Inconsistent: error received not for current message" );
								}

								current_message = null;
							}
						}

						long	now = SystemTime.getCurrentTime();

						int	retry_count = message.getRetryCount();

						if ( retry_count < 1 && !message.timedOut( now )){

							message.setRetry();

							// logMessage( "Msg " + message.getString() + " retrying" );

							synchronized( BuddyPluginBuddy.this ){

								messages.add( 0, message );
							}
						}else{

							original_listener.sendFailed( to_buddy, cause );
						}
					}finally{

						dispatchMessage();
					}
				}
			};

		message.setListener( listener_delegate );

		int	size;

		synchronized( this ){

			messages.add( message );

			size = messages.size();
		}

		// logMessage( "Msg " + message.getString() + " added: num=" + size );

		dispatchMessage();
	}

	protected void
	dispatchMessage()
	{
		buddyConnection	bc = null;

		buddyMessage 	allocated_message 	= null;
		Throwable		failed_msg_error 	= null;

		boolean	inform_dirty	= false;

		synchronized( this ){

			if ( current_message != null || messages.size() == 0 || closing ){

				return;
			}

			allocated_message = current_message = (buddyMessage)messages.remove( 0 );

			for (int i=0;i<connections.size();i++){

				buddyConnection c = (buddyConnection)connections.get(i);

				if ( !c.hasFailed()){

					bc	= c;
				}
			}

			if ( bc == null ){

				if ( destroyed ){

					failed_msg_error = new BuddyPluginException( "Friend destroyed" );

				}else if ( connections.size() >= MAX_ACTIVE_CONNECTIONS ){

					failed_msg_error = new BuddyPluginException( "Too many active connections" );
				}
			}
		}

		if ( failed_msg_error != null ){

			allocated_message.reportFailed( failed_msg_error );

			return;
		}

		if ( bc == null ){

				// single-thread outgoing connect attempts

			try{
				outgoing_connect_sem.reserve();

				synchronized( this ){

					if ( current_message != allocated_message ){

						failed_msg_error = new BuddyPluginException( "current message no longer active" );

					}else if ( closing ){

						return;
					}

					if ( failed_msg_error == null ){

						for (int i=0;i<connections.size();i++){

							buddyConnection c = (buddyConnection)connections.get(i);

							if ( !c.hasFailed()){

								bc	= c;
							}
						}

						if ( bc == null ){

							if ( destroyed ){

								failed_msg_error = new BuddyPluginException( "Friend destroyed" );

							}else if ( connections.size() >= MAX_ACTIVE_CONNECTIONS ){

								failed_msg_error = new BuddyPluginException( "Too many active connections" );
							}
						}
					}
				}

				if ( bc == null && failed_msg_error == null ){

					try{
							// can't perform connect op while synchronized as may deadlock on password
							// acquisition

						GenericMessageConnection generic_connection = outgoingConnection();
						
						InetSocketAddress address = generic_connection.getEndpoint().getNotionalAddress();
						
						String net = AENetworkClassifier.categoriseAddress( address );
						
						DDBDetails	ddb_details =  plugin_network.getDDBDetails( net );
						
						if ( ddb_details== null ){
							
							throw( new Exception( "No ddb_details for " + net ));
							
						}
						synchronized( this ){

							if ( current_message != allocated_message ){

								failed_msg_error = new BuddyPluginException( "current message no longer active" );

								generic_connection.close();

							}else{

								bc = new buddyConnection( ddb_details, generic_connection, true );

								inform_dirty = connections.size() == 0;

								connections.add( bc );

								// logMessage( "Con " + bc.getString() + " added: num=" + connections.size() );
							}
						}
					}catch( Throwable e ){

						failed_msg_error = e;
					}
				}
			}finally{

				outgoing_connect_sem.release();
			}
		}

		if ( failed_msg_error != null ){

			allocated_message.reportFailed( failed_msg_error );

			return;
		}

		try{
			// logMessage( "Allocating msg " + allocated_message.getString() + " to con " + bc.getString());

			bc.sendMessage( allocated_message );

		}catch( BuddyPluginException e ){

			allocated_message.reportFailed( e );
		}

		if ( inform_dirty ){

			plugin_network.setConfigDirty();
		}
	}

	protected void
	removeConnection(
		buddyConnection			bc )
	{
		int	size;

		synchronized( this ){

			connections.remove( bc );

			size = connections.size();
		}

		if ( size == 0 ){

			plugin_network.setConfigDirty();
		}

		if ( size == 0 && bc.isConnected() && !bc.isClosing() && !bc.isRemoteClosing()){

				// dropped connection, kick in a keep alive

			if ( consec_connect_fails < 3 ){

				if ( consec_connect_fails == 0 ){

					long	now = SystemTime.getMonotonousTime();

					boolean do_it = false;

					synchronized( this ){

						if ( 	last_auto_reconnect == -1 ||
								now - last_auto_reconnect > 30*1000 ){

							last_auto_reconnect = now;

							do_it = true;
						}
					}

					if ( do_it ){

							// delay a bit

						new DelayedEvent(
								"BuddyPluginBuddy:recon",
								new Random().nextInt( 3000 ),
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										int	size;

										synchronized( BuddyPluginBuddy.this ){

											size = connections.size();
										}

										if ( consec_connect_fails == 0 && size == 0 ){

											log( "Attempting reconnect after dropped connection" );

											sendKeepAlive();
										}
									}
								});
					}

				}else{

					long	delay = 60*1000;

					delay <<= Math.min( 3, consec_connect_fails );

					if ( SystemTime.getCurrentTime() - last_connect_attempt >= delay ){

						sendKeepAlive();
					}
				}
			}
		}

		// logMessage( "Con " + bc.getString() + " removed: num=" + size );

			// connection failed, see if we need to attempt to re-establish

		plugin_network.fireDetailsChanged( this );

		dispatchMessage();
	}

	protected long
	getLastStatusCheckTime()
	{
		return( last_status_check_time );
	}

	protected boolean
	statusCheckActive()
	{
		synchronized( this ){

			return( check_active );
		}
	}

	protected boolean
	statusCheckStarts()
	{
		synchronized( this ){

			if ( check_active ){

				return( false );
			}

			last_status_check_time = SystemTime.getCurrentTime();

			check_active = true;
		}

		return( true );
	}

	protected void
	statusCheckFailed()
	{
		boolean	details_change = false;

		synchronized( this ){

			try{
				if ( online ){

					online					= false;
					consec_connect_fails	= 0;

					details_change	= true;
				}
			}finally{

				check_active = false;
			}
		}

		if ( details_change ){

			plugin_network.fireDetailsChanged( this );
		}
	}

	private void
	setAddress(
		InetSocketAddress	address )
	{
		if ( plugin_network.getPeersAreLANLocal()){

			AddressUtils.addExplicitLANRateLimitAddress( address );
		}
	}
	
	private boolean
	addressesEqual(
		InetSocketAddress	ias1,
		InetSocketAddress	ias2 )
	{
		if ( ias1 == null && ias2 == null ){
			
			return( true );
			
		}else if ( ias1 == null || ias2 == null ){
			
			return( false );
		}
		
		if ( ias1.isUnresolved() && ias2.isUnresolved()){
			
			return( AddressUtils.getHostAddress( ias1 ).equals( AddressUtils.getHostAddress( ias2 )));
			
		}else if ( ias1.isUnresolved() || ias2.isUnresolved()){
			
			return( false );
			
		}else{
			
			return( ias1.getAddress().equals( ias2.getAddress()));
		}
	}

	protected void
	setCachedStatus(
		InetSocketAddress		_ip,
		int						_tcp_port,
		int						_udp_port )
	{
		setAddress( _ip );

		synchronized( this ){

			if ( current_ip == null ){

				current_ip	= _ip;
				
				if ( current_ip != null && !current_ip.isUnresolved()){
				
					if ( current_ip.getAddress() instanceof Inet4Address ){
					
						latest_ipv4 = current_ip;
						
					}else if ( current_ip.getAddress() instanceof Inet6Address ){
						
						latest_ipv6 = current_ip;
					}
				}
				
				tcp_port	= _tcp_port;
				udp_port	= _udp_port;
			}
		}
	}

	protected void
	statusCheckComplete(
		DDBDetails			_ddb_details,
		long				_post_time,
		InetSocketAddress	_ias,
		int					_tcp_port,
		int					_udp_port,
		String				_nick_name,
		int					_online_status,
		int					_status_seq,
		int					_version )
	{
		boolean	details_change 	= false;
		boolean	config_dirty 	= false;

		long	now = SystemTime.getCurrentTime();

		if ( now < last_time_online ){

			last_time_online = now;
		}

		boolean is_connected = isConnected();

		synchronized( this ){

				// don't handle the public/mix-dht very well, just prioritize public over I2P and stick
				// with it else we'll trash between the two addresses and doign a better job isn't really
				// worth the effort
			
			status_check_count++;
			
			if ( status_check_count > 1 && AENetworkClassifier.categoriseAddress( _ias ) != AENetworkClassifier.AT_PUBLIC ){
				
				if ( current_ip != null && AENetworkClassifier.categoriseAddress( current_ip ) == AENetworkClassifier.AT_PUBLIC ){
				
					return;
				}
			}
			
			try{
					// do we explicitly know that this sequence number denotes an offline buddy

				if ( offline_seq_set != null ){

					if ( offline_seq_set.contains(new Long( _status_seq ))){

						return;

					}else{

						offline_seq_set = null;
					}
				}

				boolean	seq_change = _status_seq != last_status_seq;

				boolean timed_out;

					// change in sequence means we're online

				if ( seq_change ){

					last_status_seq		= _status_seq;
					last_time_online	= now;

					timed_out 			= false;
					details_change		= true;

				}else{

					timed_out =  now - last_time_online >= BuddyPluginNetwork.STATUS_REPUBLISH_PERIOD * 3 ;
				}

				if ( online ){

					if ( timed_out ){

						online					= false;
						consec_connect_fails	= 0;

						details_change	= true;
					}
				}else{

					if ( seq_change || !timed_out ){

						online			= true;
						details_change	= true;
					}
				}

				post_time	= _post_time;

				if ( 	!addressesEqual( current_ip, _ias ) ||
						tcp_port != _tcp_port ||
						udp_port != _udp_port ||
						version	 < _version ){

					setAddress( _ias );

					current_ip		= _ias;
					
					if ( current_ip != null && !current_ip.isUnresolved()){
						
						if ( current_ip.getAddress() instanceof Inet4Address ){
						
							latest_ipv4 = current_ip;
							
						}else if ( current_ip.getAddress() instanceof Inet6Address ){
							
							latest_ipv6 = current_ip;
						}
					}
					
					tcp_port		= _tcp_port;
					udp_port		= _udp_port;

					if ( version < _version ){

						version			= _version;
					}

					details_change	= true;
				}

					// if we are connected then we use the status sent over the connection
					// as it is more up to date

				if ( 	!is_connected &&
						online_status != _online_status ){

					online_status	= _online_status;

					details_change	= true;
				}

				if ( !plugin_network.stringsEqual( nick_name, _nick_name )){

					nick_name	= _nick_name;

					config_dirty	= true;
					details_change	= true;
				}
			}finally{

				check_active = false;
			}
		}

		if ( config_dirty ){

			plugin_network.setConfigDirty();
		}

		if ( details_change ){

			if ( online ){

				persistentDispatchPending();
			}

			plugin_network.fireDetailsChanged( this );
		}

		if ( !isTransient()){
		
			plugin_network.logMessage( this,  getString());
		}
	}

	protected void
	checkTimeouts()
	{
		long	now = SystemTime.getCurrentTime();

		List	failed = null;

		List	connections_to_check = null;

		boolean	messages_queued;

		synchronized( this ){

			messages_queued = messages.size() > 0;

			if ( messages_queued ){

				Iterator	it = messages.iterator();

				while( it.hasNext()){

					buddyMessage	message = (buddyMessage)it.next();

					if ( message.timedOut( now )){

						it.remove();

						if ( failed == null ){

							failed = new ArrayList();
						}

						failed.add( message );
					}
				}
			}

			if ( connections.size() > 0 ){

				connections_to_check = new ArrayList( connections );
			}
		}

		boolean	send_keep_alive = false;

		if ( connections_to_check == null ){

				// no active connections

			if ( online && current_ip != null && !messages_queued ){

					// see if we should attempt a pre-emptive connect

				if ( consec_connect_fails < 3 ){

					long	delay = 60*1000;

					delay <<= Math.min( 3, consec_connect_fails );

					send_keep_alive = now - last_connect_attempt >= delay;
				}
			}
		}else{

			for (int i=0;i<connections_to_check.size();i++){

				buddyConnection connection = (buddyConnection)connections_to_check.get(i);

				boolean closed = connection.checkTimeout( now );

				if ( 	current_ip != null &&
						!closed &&
						!messages_queued &&
						connection.isConnected() &&
						!connection.isActive()){

					if ( now - connection.getLastActive( now ) > CONNECTION_KEEP_ALIVE ){

						send_keep_alive	= true;
					}
				}
			}
		}

		if ( send_keep_alive ){

			sendKeepAlive();
		}

		if ( failed != null ){

			for (int i=0;i<failed.size();i++){

				((buddyMessage)failed.get(i)).reportFailed( new BuddyPluginTimeoutException( "Timeout", false ));
			}
		}
	}

	protected void
	sendKeepAlive()
	{
		boolean send_keep_alive = true;

		synchronized( this ){

			if ( keep_alive_outstanding ){

				send_keep_alive = false;

			}else{

				keep_alive_outstanding = true;
			}
		}

		if ( send_keep_alive ){

			try{
				Map	ping_request = new HashMap();

				ping_request.put( "type", new Long( BuddyPluginNetwork.RT_INTERNAL_REQUEST_PING ));

				sendMessageSupport(
					ping_request,
					BuddyPluginNetwork.SUBSYSTEM_INTERNAL,
					60*1000,
					new BuddyPluginBuddyReplyListener()
					{
						@Override
						public void
						replyReceived(
							BuddyPluginBuddy	from_buddy,
							Map					reply )
						{
							synchronized( BuddyPluginBuddy.this ){

								keep_alive_outstanding = false;
							}
						}

						@Override
						public void
						sendFailed(
							BuddyPluginBuddy		to_buddy,
							BuddyPluginException	cause )
						{
							synchronized( BuddyPluginBuddy.this ){

								keep_alive_outstanding = false;
							}
						}
					});

			}catch( Throwable e ){

				synchronized( this ){

					keep_alive_outstanding = false;
				}
			}
		}
	}

	public String
	getConnectionsString()
	{
		synchronized( this ){

			String	str = "";

			for (int i=0;i<connections.size();i++){

				str += (str.length()==0?"":",") + ((buddyConnection)connections.get(i)).getString( true );
			}

			return( str );
		}
	}

	public void
	disconnect()
	{
		List	to_disconnect = new ArrayList();

		synchronized( this ){

			to_disconnect.addAll( connections );
		}

		for (int i=0;i<to_disconnect.size();i++){

			((buddyConnection)to_disconnect.get(i)).disconnect();
		}
	}

	protected boolean
	isClosing()
	{
		return( closing );
	}

	protected void
	destroy()
	{
		List<buddyConnection>	to_close = new ArrayList<>();

		synchronized( this ){

			destroyed = true;

			to_close.addAll( connections );
		}

		for (int i=0;i<to_close.size();i++){

			((buddyConnection)to_close.get(i)).close();
		}

		InetSocketAddress ip = current_ip;

		if ( ip != null ){

			AddressUtils.removeExplicitLANRateLimitAddress( ip );
		}
	}

	protected void
	logMessage(
		String	str )
	{
		plugin_network.logMessage( this, getShortString() + ": " + str );
	}

	protected GenericMessageConnection
	outgoingConnection()

		throws BuddyPluginException
	{
		GenericMessageRegistration msg_registration = plugin_network.getMessageRegistration();

		if ( msg_registration == null ){

			throw( new BuddyPluginException( "Messaging system unavailable" ));
		}

		InetSocketAddress isa = getIP();

		if ( isa == null ){

			throw( new BuddyPluginException( "Friend offline (no usable IP address)" ));
		}

		InetSocketAddress	tcp_target	= null;
		InetSocketAddress	udp_target	= null;

		if ( isa.isUnresolved()){
			
			tcp_target = InetSocketAddress.createUnresolved( AddressUtils.getHostAddress( isa ), tcp_port );
			
		}else{
			
			int	tcp_port = getTCPPort();
	
			if ( tcp_port > 0 ){
	
				tcp_target = new InetSocketAddress( isa.getAddress(), tcp_port );
			}
	
			int	udp_port = getUDPPort();
	
			if ( udp_port > 0 ){
	
				udp_target = new InetSocketAddress( isa.getAddress(), udp_port );
			}
		}
		
		InetSocketAddress	notional_target = tcp_target;

		if ( notional_target == null ){

			notional_target = udp_target;
		}

		if ( notional_target == null ){

			throw( new BuddyPluginException( "Friend offline (no usable protocols)" ));
		}

		GenericMessageEndpoint	endpoint = msg_registration.createEndpoint( notional_target );

		if ( tcp_target != null ){

			endpoint.addTCP( tcp_target );
		}

		if ( udp_target != null ){

			endpoint.addUDP( udp_target );
		}

		GenericMessageConnection	con = null;

		try{
			last_connect_attempt = SystemTime.getCurrentTime();

			con = msg_registration.createConnection( endpoint );

			plugin_network.getPlugin().addRateLimiters( con );

			String reason = "Friend: Outgoing connection establishment";

			con = plugin_network.getSTSConnection(
					con,
					reason,
					new SEPublicKeyLocator()
					{
						@Override
						public boolean
						accept(
							Object			context,
							SEPublicKey		other_key )
						{
							String	other_key_str = Base32.encode( other_key.encodeRawPublicKey());

							if ( other_key_str.equals( public_key )){

								consec_connect_fails	= 0;

								return( true );

							}else{

								log( getString() + ": connection failed due to pk mismatch" );

								return( false );
							}
						}
					});
			
			con.connect(
				new GenericMessageConnection.GenericMessageConnectionPropertyHandler(){
					
					@Override
					public Object 
					getConnectionProperty(
						String property_name )
					{
						if ( property_name == AEProxyFactory.PO_PEER_NETWORKS ){

							return( plugin_network.getDDBNetworks());
						}
						
						return( null );
					}
				});

			return( con );

		}catch( Throwable e ){

			if ( con != null ){

				consec_connect_fails++;

				try{
					con.close();

				}catch( Throwable f ){

					log( "Failed to close connection", f );
				}
			}

			throw( new BuddyPluginException( "Failed to send message", e ));
		}
	}

	protected void
	incomingConnection(
		DDBDetails					_ddb_details,
		GenericMessageConnection	_connection )

		throws BuddyPluginException
	{
		addConnection( _ddb_details, _connection );
	}

	protected void
	addConnection(
		DDBDetails						_ddb_details,
		GenericMessageConnection		_connection )

		throws BuddyPluginException
	{
		//int	size;

		buddyConnection bc = new buddyConnection( _ddb_details, _connection, false );

		boolean inform_dirty = false;

		synchronized( this ){

			if ( destroyed ){

				throw( new BuddyPluginException( "Friend has been destroyed" ));
			}

			inform_dirty = connections.size() == 0;

			connections.add( bc );

			//size = connections.size();
		}

		// logMessage( "Con " + bc.getString() + " added: num=" + size );

		if ( inform_dirty ){

			plugin_network.setConfigDirty();
		}
	}

	public void
	setUserData(
		Object		key,
		Object		value )
	{
		synchronized( user_data ){

			user_data.put(key, value);
		}
	}

	public Object
	getUserData(
		Object		key )
	{
		synchronized( user_data ){

			return( user_data.get( key ));
		}
	}

	protected void
	log(
		String		str )
	{
		plugin_network.log( this, str );
	}

	protected void
	log(
		String		str,
		Throwable 	e )
	{
		plugin_network.log( this, str, e );
	}

	public String
	getString()
	{
		return( "pk=" +  getShortString() + (nick_name==null?"":(",nick=" + nick_name)) + ",ip=" + AddressUtils.getHostAddress( current_ip ) + ",tcp=" + tcp_port + ",udp=" + udp_port + ",online=" + online + ",age=" + (SystemTime.getCurrentTime() - post_time ));
	}

	protected class
	buddyMessage
	{
		private int									message_id;

		private Map									request;
		private int									subsystem;
		private BuddyPluginBuddyReplyListener		listener;
		private int									timeout_millis;

		private long					queue_time	= SystemTime.getCurrentTime();

		private boolean		timed_out;
		private int			retry_count;
		private boolean		complete;

		protected
		buddyMessage(
			int									_subsystem,
			Map									_request,
			int									_timeout )
		{
			synchronized( BuddyPluginBuddy.this ){

				message_id = next_message_id++;
			}

			request			= _request;
			subsystem		= _subsystem;
			timeout_millis	= _timeout;
		}

		protected void
		setListener(
			BuddyPluginBuddyReplyListener		_listener )
		{
			listener		= _listener;
		}

		protected int
		getRetryCount()
		{
			synchronized( this ){

				return( retry_count );
			}
		}

		protected void
		setDontRetry()
		{
			retry_count = 99;
		}

		protected void
		setRetry()
		{
			synchronized( this ){

				retry_count++;

				complete 	= false;
				timed_out 	= false;

			}
		}

		protected boolean
		timedOut(
			long	now )
		{
			if ( timed_out ){

				return( true );
			}

			if ( now < queue_time ){

				queue_time = now;

				return( false );

			}else{

				timed_out = now - queue_time >= timeout_millis;

				return( timed_out );
			}
		}

		protected Map
		getRequest()
		{
			return( request );
		}

		protected int
		getSubsystem()
		{
			return( subsystem );
		}

		protected int
		getID()
		{
			return( message_id );
		}

		protected void
		reportComplete(
			Map		reply )
		{
			synchronized( this ){

				if ( complete ){

					return;
				}

				complete = true;
			}

			try{
				listener.replyReceived(  BuddyPluginBuddy.this, reply );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		protected void
		reportFailed(
			Throwable	error )
		{
			synchronized( this ){

				if ( complete ){

					return;
				}

				complete = true;
			}

			try{
				if ( error instanceof BuddyPluginException ){

					listener.sendFailed( BuddyPluginBuddy.this, (BuddyPluginException)error );

				}else{

					listener.sendFailed(  BuddyPluginBuddy.this, new BuddyPluginException( "",  error ));
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		protected String
		getString()
		{
			return( "id=" + message_id + ",ss=" + subsystem + (retry_count==0?"":(",retry="+retry_count)));
		}
	}

	protected class
	buddyConnection
		implements fragmentHandlerReceiver
	{
		final private DDBDetails				ddb_details;
		
		private fragmentHandler					fragment_handler;
		private int								connection_id;
		private boolean							outgoing;

		private String							dir_str;


		private volatile buddyMessage	active_message;

		private volatile boolean			connected;
		private volatile boolean			closing;
		private volatile boolean			remote_closing;
		private volatile boolean			failed;

		private long			last_active	= SystemTime.getCurrentTime();

		protected
		buddyConnection(
			DDBDetails						_ddb_details,
			GenericMessageConnection		_connection,
			boolean							_outgoing )
		{
			ddb_details = _ddb_details;
			
			fragment_handler	= new fragmentHandler( _connection, this );

			outgoing	= _outgoing;

			synchronized( BuddyPluginBuddy.this ){

				connection_id = next_connection_id++;
			}

			dir_str = outgoing?"Outgoing":"Incoming";

			if ( !outgoing ){

				connected = true;
				
				buddyConnectionEstablished( false );
			}

			fragment_handler.start();
		}

		protected DDBDetails
		getDDBDetails()
		{
			return( ddb_details );
		}
		
		protected boolean
		isConnected()
		{
			return( connected );
		}

		protected boolean
		hasFailed()
		{
			return( failed );
		}

		protected boolean
		isOutgoing()
		{
			return( outgoing );
		}

		protected long
		getLastActive(
			long		now )
		{
			if ( now < last_active ){

				last_active = now;
			}

			return( last_active );
		}

		protected void
		sendMessage(
			buddyMessage	message )

			throws BuddyPluginException
		{
			BuddyPluginException	failed_error = null;

			buddyMessage	msg_to_send			= null;

			synchronized( this ){

				if ( BuddyPluginBuddy.this.isClosing()){

					throw( new BuddyPluginException( "Close in progress" ));
				}

				if ( active_message != null ){

					Debug.out( "Inconsistent: active message already set" );

					failed_error = new BuddyPluginException( "Inconsistent state" );

				}else if ( failed || closing ){

					throw( new BuddyPluginException( "Connection failed" ));

				}else{

					active_message = message;

					if ( connected ){

						msg_to_send = active_message;
					}
				}
			}

			if ( failed_error != null ){

				failed( failed_error );

				throw( failed_error );
			}

			if ( msg_to_send != null ){

				send( msg_to_send );
			}
		}

		protected void
		sendCloseMessage(
			buddyMessage	message )
		{
			boolean	ok_to_send;

			synchronized( this ){

				ok_to_send = active_message == null && connected && !failed && !closing;
			}

			if ( ok_to_send ){

				send( message );
			}
		}

		public boolean
		isActive()
		{
			return( active_message != null );
		}

		@Override
		public void
		connected()
		{
			if ( TRACE ){
				System.out.println( dir_str + " connected" );
			}

			buddyMessage	msg_to_send = null;

			synchronized( this ){

				last_active	= SystemTime.getCurrentTime();

				connected = true;

				msg_to_send = active_message;
			}

			buddyConnectionEstablished( true );

			if ( msg_to_send != null  ){

				send( msg_to_send );
			}
		}

		protected boolean
		checkTimeout(
			long	now )
		{
			buddyMessage	bm = null;

			boolean	close = false;

			synchronized( this ){

				if ( active_message != null ){

					if ( active_message.timedOut( now )){

						bm	= active_message;

						active_message	= null;
					}
				}

				if ( now < last_active ){

					last_active = now;
				}

				if ( now - last_active > CONNECTION_IDLE_TIMEOUT ){

					close	= true;
				}
			}

			if ( bm != null ){

				bm.reportFailed( new BuddyPluginTimeoutException( "Timeout", true ));
			}

			if ( close ){

				close();
			}

			return( close );
		}

		protected void
		send(
			buddyMessage		msg )
		{
			Map request = msg.getRequest();

			Map	send_map = new HashMap();

			send_map.put( "type", new Long( RT_REQUEST_DATA ));
			send_map.put( "req", request );
			send_map.put( "ss", new Long( msg.getSubsystem()));
			send_map.put( "id", new Long( msg.getID()));
			send_map.put( "oz", new Long( plugin_network.getOnlineStatus()));
			send_map.put( "v", new Long( BuddyPluginNetwork.VERSION_CURRENT ));

			String	loc_cat = getLocalAuthorisedRSSTagsOrCategoriesAsString();

			if ( loc_cat != null ){
				send_map.put( "cat", loc_cat );
			}

			try{
				// logMessage( "Sending " + msg.getString() + " to " + getString());

				fragment_handler.send( send_map, true, true );

				synchronized( this ){

					last_active	= SystemTime.getCurrentTime();
				}
			}catch( BuddyPluginException e ){

				try{
					failed( e );

				}catch( Throwable f ){

					Debug.printStackTrace(f);
				}
			}
		}

		@Override
		public void
		receive(
			Map			data_map )
		{
			synchronized( this ){

				last_active	= SystemTime.getCurrentTime();
			}

			if ( TRACE ){
				System.out.println( dir_str + " receive: " + data_map );
			}

			try{
				int	type = ((Long)data_map.get("type")).intValue();

				Long	l_os = (Long)data_map.get( "oz" );

				if ( l_os != null ){

					setOnlineStatus( l_os.intValue());
				}

				Long	l_ver = (Long)data_map.get( "v" );

				if ( l_ver != null ){

					setVersion( l_ver.intValue());
				}

				byte[]	b_rem_cat = (byte[])data_map.get( "cat" );

				if ( b_rem_cat == null ){

					setRemoteAuthorisedRSSTagsOrCategories( null );

				}else{

					setRemoteAuthorisedRSSTagsOrCategories( stringToCats( new String( b_rem_cat, "UTF-8" )));
				}

				if ( type == RT_REQUEST_DATA ){

					// logMessage( "Received type=" + type + " from " + getString());

					Long	subsystem = (Long)data_map.get( "ss" );

					Map	reply;

					int	reply_type;

					Map request = (Map)data_map.get( "req" );

					String	error = null;

					if ( request == null || subsystem == null ){

						reply	= null;

					}else{

						try{

							reply = plugin_network.requestReceived( BuddyPluginBuddy.this, subsystem.intValue(), request );

						}catch( Throwable e ){

							error = Debug.getNestedExceptionMessage(e);

							reply = null;
						}
					}

					if ( reply == null ){

						reply_type = RT_REPLY_ERROR;

						reply = new HashMap();

						reply.put( "error", error==null?"No handlers available to process request":error );

					}else{

						reply_type = RT_REPLY_DATA;
					}

					Map reply_map = new HashMap();

					reply_map.put( "ss", subsystem );
					reply_map.put( "type", new Long( reply_type ));
					reply_map.put( "id", data_map.get( "id" ) );
					reply_map.put( "oz", new Long( plugin_network.getOnlineStatus()));

					String	loc_cat = getLocalAuthorisedRSSTagsOrCategoriesAsString();

					if ( loc_cat != null ){
						reply_map.put( "cat", loc_cat );
					}

					reply_map.put( "rep", reply );

						// don't record as active here as (1) we recorded as active above when
						// receiving request (2) we may be replying to a 'closing' message and
						// we don't want the reply to mark as online

					fragment_handler.send( reply_map, false, false );

				}else if ( type == RT_REPLY_DATA || type == RT_REPLY_ERROR ){

					long	id = ((Long)data_map.get( "id" )).longValue();

					buddyMessage	bm;

					synchronized( this ){

						if ( 	active_message != null &&
								active_message.getID() == id ){

							bm = active_message;

							active_message = null;

						}else{

							bm = null;
						}
					}

					Map	reply = (Map)data_map.get( "rep" );

					if ( bm == null ){

						logMessage( "reply discarded as no matching request: " + reply );

					}else{

						if ( type == RT_REPLY_ERROR ){

							bm.setDontRetry();

							bm.reportFailed( new BuddyPluginException(new String((byte[])reply.get( "error" ))));

						}else{

							bm.reportComplete( reply );
						}
					}
				}else{

						// ignore unknown message types
				}
			}catch( Throwable e ){

				failed( e );
			}
		}

		protected void
		close()
		{
			closing = true;

			failed( new BuddyPluginException( "Closing" ));
		}

		protected boolean
		isClosing()
		{
			return( closing );
		}

		protected void
		remoteClosing()
		{
			remote_closing = true;
		}

		protected boolean
		isRemoteClosing()
		{
			return( remote_closing );
		}

		protected void
		disconnect()
		{
			fragment_handler.close();
		}

		@Override
		public void
		failed(
			Throwable 					error )
		{
			buddyMessage bm = null;

			if ( !connected && outgoing ){

				consec_connect_fails++;
			}

			synchronized( this ){

				if ( failed ){

					return;
				}

				failed = true;

				bm = active_message;

				active_message	 = null;
			}

			logMessage( "Con " + getString() + " failed: " + Debug.getNestedExceptionMessage( error ));

			try{
				if ( !closing ){

					if ( TRACE ){
						System.out.println( dir_str + " connection error:" );
					}
				}

				fragment_handler.close();

			}finally{

				removeConnection( this );

				if ( bm != null ){

					bm.reportFailed( error );
				}
			}
		}

		protected String
		getString()
		{
			return( getString( false ));
		}

		protected String
		getString(
			boolean	short_form )
		{
			if ( short_form ){

				return( fragment_handler.getString());

			}else{

				return("id=" + connection_id + ",dir=" + ( outgoing?"out":"in" ));
			}
		}
	}

	protected class
	fragmentHandler
		implements GenericMessageConnectionListener
	{
		private GenericMessageConnection	connection;
		private fragmentHandlerReceiver		receiver;

		private int	next_fragment_id	= 0;

		private fragmentAssembly	current_request_frag;
		private fragmentAssembly	current_reply_frag;

		private int					send_count;
		private int					recv_count;

		protected
		fragmentHandler(
			GenericMessageConnection	_connection,
			fragmentHandlerReceiver		_receiver )
		{
			connection	= _connection;
			receiver	= _receiver;
		}

		public void
		start()
		{
			connection.addListener( this );
		}

		@Override
		public void
		connected(
			GenericMessageConnection	connection )
		{
			receiver.connected();
		}

		@Override
		public void
		failed(
			GenericMessageConnection	connection,
			Throwable 					error )

			throws MessageException
		{
			receiver.failed( error );
		}

		protected void
		send(
			Map			data_map,
			boolean		is_request,
			boolean		record_active )

			throws BuddyPluginException
		{
			try{
				byte[] data = BEncoder.encode( data_map );

				int	data_length = data.length;

				plugin_network.checkMaxMessageSize( data_length );

				int	max_chunk = connection.getMaximumMessageSize() - 1024;

				if ( data_length > max_chunk ){

					int	fragment_id;

					synchronized( this ){

						fragment_id = next_fragment_id++;
					}

					int chunk_num = 0;

					for (int i=0;i<data_length;i+=max_chunk){

						int	end = Math.min( data_length, i + max_chunk );

						if ( end > i ){

							byte[]	chunk = new byte[ end-i ];

							System.arraycopy( data, i, chunk, 0, chunk.length );

							Map	chunk_map = new HashMap();

							chunk_map.put( "type", new Long( BuddyPluginNetwork.RT_INTERNAL_FRAGMENT ));
							chunk_map.put( "f", new Long( fragment_id ));
							chunk_map.put( "l", new Long( data_length ));
							chunk_map.put( "c", new Long( max_chunk ));
							chunk_map.put( "i", new Long( chunk_num ));
							chunk_map.put( "q", new Long( is_request?1:0 ));
							chunk_map.put( "d", chunk );

							byte[] chunk_data = BEncoder.encode( chunk_map );

							PooledByteBuffer chunk_buffer =
								plugin_network.getPluginInterface().getUtilities().allocatePooledByteBuffer( chunk_data );

							try{
								connection.send( chunk_buffer );

								chunk_buffer = null;

							}finally{

								if ( chunk_buffer != null ){

									chunk_buffer.returnToPool();
								}
							}
						}

						chunk_num++;
					}
				}else{

					PooledByteBuffer buffer =
						plugin_network.getPluginInterface().getUtilities().allocatePooledByteBuffer( data );

					try{

						connection.send( buffer );

						buffer = null;

					}finally{

						if ( buffer != null ){

							buffer.returnToPool();
						}
					}
				}

				buddyMessageSent( data.length, record_active );

				send_count++;

			}catch( Throwable e ){

				throw( new BuddyPluginException( "Send failed", e ));
			}
		}

		@Override
		public void
		receive(
			GenericMessageConnection	connection,
			PooledByteBuffer			message )

			throws MessageException
		{
			try{
					// while in unauth state we only allow a few messages. max should be 1
					// for an 'accept request' but I feel generous

				if ( recv_count >= 4 && !isAuthorised()){

					throw( new MessageException( "Too many messages received while in unauthorised state" ));
				}

				byte[]	content = message.toByteArray();

				Map	data_map = BDecoder.decode( content );

				if (((Long)data_map.get( "type" )).intValue() == BuddyPluginNetwork.RT_INTERNAL_FRAGMENT ){

					Map	chunk_map = data_map;

					int	fragment_id = ((Long)chunk_map.get( "f" )).intValue();
					int	data_length = ((Long)chunk_map.get( "l" )).intValue();
					int	chunk_size 	= ((Long)chunk_map.get( "c" )).intValue();
					int	chunk_num 	= ((Long)chunk_map.get( "i" )).intValue();

					boolean	is_request = ((Long)chunk_map.get("q")).intValue() == 1;

					byte[]	chunk_data = (byte[])chunk_map.get("d" );

					plugin_network.checkMaxMessageSize( data_length );

					fragmentAssembly assembly;

					if ( is_request ){

						if ( current_request_frag == null ){

							current_request_frag = new fragmentAssembly( fragment_id, data_length, chunk_size );
						}

						assembly = current_request_frag;

					}else{

						if ( current_reply_frag == null ){

							current_reply_frag = new fragmentAssembly( fragment_id, data_length, chunk_size );
						}

						assembly = current_reply_frag;
					}

					if ( assembly.getID() != fragment_id ){

						throw( new BuddyPluginException( "Fragment receive error: concurrent decode not supported" ));
					}

					if ( assembly.receive( chunk_num, chunk_data )){

						if ( is_request ){

							current_request_frag = null;

						}else{

							current_reply_frag = null;
						}

						buddyMessageReceived( data_length );

						recv_count++;

						receiver.receive( BDecoder.decode( assembly.getData()));

					}else{

						buddyMessageFragmentReceived( assembly.getChunksReceived(), assembly.getTotalChunks());
					}
				}else{

					buddyMessageReceived( content.length );

					recv_count++;

					receiver.receive( data_map );
				}
			}catch( Throwable e ){

				receiver.failed( e );

			}finally{

				message.returnToPool();
			}
		}

		protected void
		close()
		{
			try{

				connection.close();

			}catch( Throwable e ){

				// Debug.printStackTrace( e );

			}finally{

				receiver.failed( new Exception( "Connection closed" ));
			}
		}

		protected String
		getString()
		{
			return( connection.getType());
		}

		protected class
		fragmentAssembly
		{
			private int		id;
			private byte[]	data;
			private int		chunk_size;

			private int		num_chunks;
			private Set		chunks_received = new HashSet();

			protected
			fragmentAssembly(
				int		_id,
				int		_length,
				int		_chunk_size )
			{
				id			= _id;
				chunk_size	= _chunk_size;

				data		= new byte[_length];

				num_chunks = (_length + chunk_size - 1 )/chunk_size;
			}

			protected int
			getID()
			{
				return( id );
			}

			protected int
			getChunksReceived()
			{
				return( chunks_received.size());
			}

			protected int
			getTotalChunks()
			{
				return( num_chunks );
			}

			protected boolean
			receive(
				int		chunk_num,
				byte[]	chunk )
			{
				// System.out.println( "received chunk " + chunk_num + " of " + num_chunks );

				Integer	i = new Integer( chunk_num );

				if ( chunks_received.contains( i )){

					return( false );
				}

				chunks_received.add( i );

				System.arraycopy( chunk, 0, data, chunk_num*chunk_size, chunk.length );

				return( chunks_received.size() == num_chunks );
			}

			protected byte[]
			getData()
			{
				return( data );
			}
		}
	}

	interface
	fragmentHandlerReceiver
	{
		public void
		connected();

		public void
		receive(
			Map			data );

		public void
		failed(
			Throwable	error );
	}
}
