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


package com.biglybt.core.subs.impl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import com.biglybt.util.MapUtils;
import org.gudy.bouncycastle.util.encoders.Base64;
import org.json.simple.JSONObject;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.lws.LightWeightSeed;
import com.biglybt.core.lws.LightWeightSeedAdapter;
import com.biglybt.core.lws.LightWeightSeedManager;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.MetaSearchManagerFactory;
import com.biglybt.core.metasearch.impl.web.rss.RSSEngine;
import com.biglybt.core.security.CryptoECCUtils;
import com.biglybt.core.subs.*;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentCreator;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.*;
import com.biglybt.core.util.DataSourceResolver.DataSourceImporter;
import com.biglybt.core.util.DataSourceResolver.ExportableDataSource;
import com.biglybt.core.util.DataSourceResolver.ExportedDataSource;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.util.JSONUtils;

public class
SubscriptionImpl
	implements Subscription, ExportableDataSource
{

	private static final int MAX_ASSOCIATIONS;

	static{
		int max_assoc = 256;

		try{
			max_assoc = Integer.parseInt( System.getProperty(SystemProperties.SYSPROP_SUBS_MAX_ASSOCIATIONS, ""+max_assoc));

		}catch( Throwable e ){
			Debug.out( e );
		}

		MAX_ASSOCIATIONS = max_assoc;
	}

	private static final int MIN_RECENT_ASSOC_TO_RETAIN		= 16;

	//private static final byte[] GENERIC_PUBLIC_KEY 		= {(byte)0x04,(byte)0xd0,(byte)0x1a,(byte)0xd9,(byte)0xb9,(byte)0x99,(byte)0xd8,(byte)0x49,(byte)0x15,(byte)0x5f,(byte)0xe9,(byte)0x6b,(byte)0x3c,(byte)0xd8,(byte)0x18,(byte)0x81,(byte)0xf7,(byte)0x92,(byte)0x15,(byte)0x3f,(byte)0x24,(byte)0xaa,(byte)0x35,(byte)0x6f,(byte)0x52,(byte)0x01,(byte)0x79,(byte)0x2e,(byte)0x93,(byte)0xf6,(byte)0xf1,(byte)0x57,(byte)0x13,(byte)0x2a,(byte)0x3c,(byte)0x31,(byte)0x66,(byte)0xa5,(byte)0x34,(byte)0x9f,(byte)0x79,(byte)0x62,(byte)0x04,(byte)0x31,(byte)0x68,(byte)0x37,(byte)0x8f,(byte)0x77,(byte)0x5c};
	// private static final byte[] GENERIC_PRIVATE_KEY 	= {(byte)0x71,(byte)0xc3,(byte)0xe8,(byte)0x6c,(byte)0x56,(byte)0xbb,(byte)0x30,(byte)0x14,(byte)0x9e,(byte)0x19,(byte)0xa5,(byte)0x3d,(byte)0xcb,(byte)0x47,(byte)0xbb,(byte)0x6d,(byte)0x57,(byte)0x57,(byte)0xd3,(byte)0x59,(byte)0xce,(byte)0x8f,(byte)0x79,(byte)0xe5};

	protected static byte[]
	intToBytes(
		int		version )
	{
		return( new byte[]{ (byte)(version>>24), (byte)(version>>16),(byte)(version>>8),(byte)version } );
	}

	protected static int
	bytesToInt(
		byte[]		bytes )
	{
		return( (bytes[0]<<24)&0xff000000 | (bytes[1] << 16)&0x00ff0000 | (bytes[2] << 8)&0x0000ff00 | bytes[3]&0x000000ff );
	}

	private SubscriptionManagerImpl		manager;

	private byte[]			public_key;
	private byte[]			private_key;

	private String			name;
	private String			name_ex;

	private int				version;
	private int				az_version;

	private boolean			is_public;		// whether or not we publish associations
	private boolean			is_anonymous;	// whether or not the subscription is anon

	private volatile int	subscription_type = -1;
	
	private Map				singleton_details;

	private byte[]			hash;
	private byte[]			sig;
	private int				sig_data_size;

	private int				add_type;
	private long			add_time;

	private boolean			is_subscribed;

	private int				highest_prompted_version;

	private byte[]			short_id;

	private String			id;

	private List<association>			associations = new ArrayList<>();

	private int				fixed_random;

	private long			popularity				= -1;

	private long			last_auto_upgrade_check	= -1;
	private boolean			published;

	private boolean			server_published;
	private boolean			server_publication_outstanding;

	private boolean			singleton_sp_attempted;
	private String			local_name;

	private LightWeightSeed	lws;
	private int				lws_skip_check;

	private boolean			destroyed;

	private Map				history_map;
	private Map				schedule_map;

	private Map				user_data = new LightHashMap();

	private final 			SubscriptionHistoryImpl	history;

	private String			referer;

	private CopyOnWriteList	listeners = new CopyOnWriteList();

	private Map				verify_cache_details;
	private boolean			verify_cache_result;

	private String			creator_ref;
	private String			category;
	private long			tag_id = -1;
	private int				view_options;
	private String			parent;
	private List<String>	depends_on;
	private String			exec_on_new_result;
	
	private AtomicLong		newest_result_time_next = new AtomicLong(1);
	private volatile long	newest_result_time_seq;
	private volatile long	newest_result_time;
	
	private AtomicLong		md_mutator = new AtomicLong( RandomUtils.nextAbsoluteLong());
	
	protected static String
	getSkeletonJSON(
		Engine		engine,
		int			check_interval_mins )
	{
		JSONObject	map = new JSONObject();

		map.put( "engine_id", new Long( engine.getId()));

		map.put( "search_term", "" );

		map.put( "filters", new HashMap());

		map.put( "options", new HashMap());

		Map schedule = new HashMap();

		schedule.put( "interval", new Long( check_interval_mins ));

		List	days = new ArrayList();

		for (int i=1;i<=7;i++){

			days.add( String.valueOf(i));
		}

		schedule.put( "days", days );

		map.put( "schedule", schedule );

		embedEngines( map, engine );

		return( JSONUtils.encodeToJSON( map ));
	}

	protected static String
	getSkeletonJSON(
		Engine		engine,
		String		term,
		String		networks,
		int			check_interval_mins )
	{
		JSONObject	map = new JSONObject();

		map.put( "engine_id", new Long( engine.getId()));

		map.put( "search_term", term );

		if ( networks != null ){

			map.put( "networks", networks );
		}

		map.put( "filters", new HashMap());

		map.put( "options", new HashMap());

		Map schedule = new HashMap();

		schedule.put( "interval", new Long( check_interval_mins ));

		List	days = new ArrayList();

		for (int i=1;i<=7;i++){

			days.add( String.valueOf(i));
		}

		schedule.put( "days", days );

		map.put( "schedule", schedule );

		embedEngines( map, engine );

		return( JSONUtils.encodeToJSON( map ));
	}


		// new subs constructor

	protected
	SubscriptionImpl(
		SubscriptionManagerImpl		_manager,
		String						_name,
		boolean						_public,
		boolean						_anonymous,
		Map							_singleton_details,
		String						_json_content,
		int							_add_type )

		throws SubscriptionException
	{
		manager	= _manager;

		history_map	= new HashMap();

		history = new SubscriptionHistoryImpl( manager, this );

		name				= _name;
		is_public			= _public;
		is_anonymous		= _anonymous;
		singleton_details	= _singleton_details;

		version				= 1;
		az_version			= AZ_VERSION;

		add_type			= _add_type;
		add_time			= SystemTime.getCurrentTime();

		is_subscribed		= true;

		try{
			KeyPair	kp = CryptoECCUtils.createKeys();

			public_key 			= CryptoECCUtils.keyToRawdata( kp.getPublic());
			private_key 		= CryptoECCUtils.keyToRawdata( kp.getPrivate());


			fixed_random	= RandomUtils.nextInt();

			init();

			String json_content = embedEngines( _json_content );

			SubscriptionBodyImpl body = new SubscriptionBodyImpl( manager, name, is_public, is_anonymous, json_content, public_key, version, az_version, singleton_details );

			syncToBody( body );

		}catch( Throwable e ){

			throw( new SubscriptionException( "Failed to create subscription", e ));
		}
	}

		// cache detail constructor

	protected
	SubscriptionImpl(
		SubscriptionManagerImpl		_manager,
		Map							map )

		throws IOException
	{
		manager	= _manager;

		fromMap( map );

		history = new SubscriptionHistoryImpl( manager, this );

		init();
	}

		// import constructor

	protected
	SubscriptionImpl(
		SubscriptionManagerImpl		_manager,
		SubscriptionBodyImpl		_body,
		int							_add_type,
		boolean						_is_subscribed )

		throws SubscriptionException
	{
		manager	= _manager;

		history_map	= new HashMap();

		history = new SubscriptionHistoryImpl( manager, this );

		syncFromBody( _body );

		add_type		= _add_type;
		add_time		= SystemTime.getCurrentTime();

		is_subscribed	= _is_subscribed;

		fixed_random	= RandomUtils.nextInt();

		init();

		syncToBody( _body );
	}

	protected void
	syncFromBody(
		SubscriptionBodyImpl	body )

		throws SubscriptionException
	{
		public_key			= body.getPublicKey();
		version				= body.getVersion();
		az_version			= body.getAZVersion();

		name				= body.getName();
		is_public			= body.isPublic();
		is_anonymous		= body.isAnonymous();
		singleton_details	= body.getSingletonDetails();

		if ( az_version > AZ_VERSION ){

			throw( new SubscriptionException( MessageText.getString( "subscription.version.bad", new String[]{ name })));
		}
	}

	protected void
	syncToBody(
		SubscriptionBodyImpl		body )

		throws SubscriptionException
	{
			// this picks up latest values of version, name + is_public from here

		body.writeVuzeFile( this );

		hash 			= body.getHash();
		sig				= body.getSig();
		sig_data_size	= body.getSigDataSize();
	}

	protected Map
	toMap()

		throws IOException
	{
		synchronized( this ){

			Map	map = new HashMap();

			map.put( "name", name.getBytes( Constants.UTF_8 ));

			map.put( "public_key", public_key );

			map.put( "version", new Long( version ));

			map.put( "az_version", new Long( az_version ));

			map.put( "is_public", new Long( is_public?1:0 ));

			map.put( "is_anonymous", new Long( is_anonymous?1:0 ));

			if ( singleton_details != null ){

				map.put( "sin_details", singleton_details );
				map.put( "spa", new Long( singleton_sp_attempted?1:0 ));
			}

			if ( local_name != null ){

				map.put( "local_name", local_name );
			}
				// body data

			map.put( "hash", hash );
			map.put( "sig", sig );
			map.put( "sig_data_size", new Long( sig_data_size ));

				// local data

			if ( private_key != null ){

				map.put( "private_key", private_key );
			}

			map.put( "add_type", new Long( add_type ));
			map.put( "add_time", new Long( add_time ));

			map.put( "subscribed", new Long( is_subscribed?1:0 ));

			map.put( "pop", new Long( popularity ));

			map.put( "rand", new Long( fixed_random ));

			map.put( "hupv", new Long( highest_prompted_version ));

			map.put( "sp", new Long( server_published?1:0 ));
			map.put( "spo", new Long( server_publication_outstanding?1:0 ));

			if ( associations.size() > 0 ){

				List	l_assoc = new ArrayList();

				map.put( "assoc", l_assoc );

				for (int i=0;i<associations.size();i++){

					association assoc = (association)associations.get(i);

					Map m = new HashMap();

					l_assoc.add( m );

					m.put( "h", assoc.getHash());
					m.put( "w", new Long( assoc.getWhen()));
				}
			}

			map.put( "history", history_map );

			if ( creator_ref != null ){

				map.put( "cref", creator_ref.getBytes( Constants.UTF_8 ));
			}

			if ( category != null ){

				map.put( "cat", category.getBytes( Constants.UTF_8 ));
			}

			if ( tag_id != -1 ){

				map.put( "tag", tag_id );
			}
			
			if ( view_options != 0 ){

				map.put( "vo", view_options );
			}

			if ( parent != null ){

				map.put( "par", parent.getBytes( Constants.UTF_8 ));
			}
			
			if ( depends_on != null ){
				
				List<byte[]> dep = new ArrayList<>();
				
				map.put( "dep", dep);
				
				for ( String s: depends_on ){
					
					dep.add( s.getBytes( Constants.UTF_8 ));
				}
			}

			if ( exec_on_new_result != null ){
				
				map.put( "eonr", exec_on_new_result.getBytes( Constants.UTF_8 ));
			}
			
			return( map );
		}
	}

	protected void
	fromMap(
		Map		map )

		throws IOException
	{
		name				= new String((byte[])map.get( "name"), Constants.UTF_8 );
		public_key			= (byte[])map.get( "public_key" );
		private_key			= (byte[])map.get( "private_key" );
		version				= ((Long)map.get( "version" )).intValue();
		az_version			= (int) MapUtils.importLong( map, "az_version", AZ_VERSION );
		is_public			= ((Long)map.get( "is_public")).intValue() == 1;
		Long anon			= (Long)map.get( "is_anonymous" );
		is_anonymous		= anon!=null&&anon==1;
		singleton_details	= (Map)map.get( "sin_details" );

		hash			= (byte[])map.get( "hash" );
		sig				= (byte[])map.get( "sig" );
		sig_data_size	= ((Long)map.get( "sig_data_size" )).intValue();

		fixed_random	= ((Long)map.get( "rand" )).intValue();

		add_type		= ((Long)map.get( "add_type" )).intValue();
		add_time		= ((Long)map.get( "add_time" )).longValue();

		is_subscribed	= ((Long)map.get( "subscribed" )).intValue()==1;

		popularity		= ((Long)map.get( "pop" )).longValue();

		highest_prompted_version = ((Long)map.get( "hupv" )).intValue();

		server_published = ((Long)map.get( "sp" )).intValue()==1;
		server_publication_outstanding = ((Long)map.get( "spo" )).intValue()==1;

		Long	l_spa = (Long)map.get( "spa" );

		if ( l_spa != null ){
			singleton_sp_attempted = l_spa.longValue()==1;
		}

		byte[]	b_local_name = (byte[])map.get( "local_name" );

		if ( b_local_name != null ){

			local_name = new String( b_local_name, Constants.UTF_8 );
		}

		List	l_assoc = (List)map.get( "assoc" );

		if ( l_assoc != null ){

			for (int i=0;i<l_assoc.size();i++){

				Map	m = (Map)l_assoc.get(i);

				byte[]		hash 	= (byte[])m.get("h");
				long		when	= ((Long)m.get( "w" )).longValue();

				associations.add( new association( hash, when ));
			}
		}

		history_map = (Map)map.get( "history" );

		if ( history_map == null ){

			history_map = new HashMap();
		}

		byte[] b_cref = (byte[])map.get( "cref" );

		if ( b_cref != null ){

			creator_ref = new String( b_cref, Constants.UTF_8 );
		}

		byte[] b_cat = (byte[])map.get( "cat" );

		if ( b_cat != null ){

			category = new String( b_cat, Constants.UTF_8 );
		}

		Long l_tag_id = (Long)map.get( "tag" );

		if ( l_tag_id != null ){

			tag_id = l_tag_id;
		}

		Long l_vo = (Long)map.get( "vo" );

		if ( l_vo != null ){

			view_options = l_vo.intValue();
		}
		
		byte[] b_parent = (byte[])map.get( "par" );

		if ( b_parent != null ){

			parent = new String( b_parent, Constants.UTF_8 );
		}
		
		List<byte[]> b_depends_on = (List<byte[]>)map.get( "dep" );
		
		if ( b_depends_on != null ){
			
			depends_on = new ArrayList<String>( b_depends_on.size());
			
			for ( byte[] b: b_depends_on ){
				
				depends_on.add( new String( b, Constants.UTF_8 ));
			}
		}
		
		byte[] b_eonr = (byte[])map.get( "eonr" );

		if ( b_eonr != null ){

			exec_on_new_result = new String( b_eonr, Constants.UTF_8 );
		}
	}

	public ExportedDataSource
	exportDataSource()
	{
		return(
			new ExportedDataSource()
			{
				public Class<? extends DataSourceImporter>
				getExporter()
				{
					return( SubscriptionManagerImpl.class );
				}
				
				public Map<String,Object>
				getExport()
				{
					Map<String,Object> map = new HashMap<>();
					
					map.put( "id", getID());
					
					map.put( "version", version );
					
					map.put( "anon", is_anonymous?1L:0L );
					
					if ( singleton_details != null ) {
						
						Map sd = new HashMap( singleton_details );
						
						byte[] key = (byte[])sd.get( "key" );
						
						if ( key instanceof byte[]) {
							
							sd.put( "key", Base32.encode( key ));
						}
						
						map.put( "singleton", sd );
					}
					
					int vo = getViewOptions();
					
					map.put( "vo", vo );
					
					SubscriptionHistory history = getHistory();
					
					map.put( "h_cm", history.getCheckFrequencyMins());
					
					String[] dl_nets = history.getDownloadNetworks();
					
					if ( dl_nets != null ) {
						
						map.put( "h_dln", Arrays.asList( dl_nets ));
					}
					
					return( map );
				}
			});
	}
	
	protected Map
	getScheduleConfig()
	{
		if ( schedule_map == null ){

			try{
				Map map = JSONUtils.decodeJSON( getJSON());

				schedule_map = (Map)map.get( "schedule" );

				if ( schedule_map == null ){

					schedule_map = new HashMap();
				}
			}catch( Throwable e ){

				log( "Failed to load schedule", e );

				schedule_map = new HashMap();
			}
		}

		return( schedule_map );
	}

	protected Map
	getHistoryConfig()
	{
		return( history_map );
	}

	protected void
	updateHistoryConfig(
		Map		_history_map,
		int		reason )
	{
		history_map = _history_map;

		fireChanged( reason );
	}

	protected void
	upgrade(
		SubscriptionBodyImpl		body )

		throws SubscriptionException
	{
			// pick up details from the body (excluding json that is maintained in body only)

		syncFromBody( body );

			// write to file

		syncToBody(body);

		fireChanged( SubscriptionListener.CR_METADATA );
	}

	protected void
	init()
	{
		short_id = SubscriptionBodyImpl.deriveShortID( public_key, singleton_details );
		id = null;
	}

	public boolean
	isSingleton()
	{
		return( singleton_details != null );
	}

	@Override
	public boolean
	isShareable()
	{
		try{
			return( getEngine().isShareable() && !isSingleton());

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return( false );
		}
	}

	@Override
	public List<Subscription>
	getDependsOn()
	{
		List<Subscription> result = new ArrayList<>();
		
		if ( depends_on != null ){
			
			for ( String id: depends_on ){
				
				try{
					Subscription sub = manager.getSubscriptionByID( id );
					
					if ( sub != null && !result.contains( sub )){
						
						result.add( sub );
					}
				}catch( Throwable e ){
					
				}
			}
		}
		
		return( result );	
	}
	
	@Override
	public void
	setDependsOn(
		List<Subscription>	subs )
	{
		if ( subs == null || subs.isEmpty()){
			
			depends_on = null;
			
		}else{
			
			depends_on = new ArrayList<String>( subs.size());
			
			for ( Subscription s: subs ){
				
				if ( s == this ){
					
					continue;
				}
				
				String id = s.getID();
				
				if ( !depends_on.contains( id )){
				
					depends_on.add( id );
				}
			}
		}
		
		manager.configDirty( this, SubscriptionListener.CR_METADATA );
		
		fireChanged( SubscriptionListener.CR_METADATA );
	}

	@Override
	public String
	getExecuteOnNewResult()
	{
		return( exec_on_new_result );	
	}
	
	@Override
	public void
	setExecuteOnNewResult(
		String		eonr )
	{
		if ( eonr == null || eonr.isEmpty()){
			
			exec_on_new_result = null;
			
		}else{
			
			exec_on_new_result = eonr;
		}
		
		manager.configDirty( this, SubscriptionListener.CR_METADATA );
		
		fireChanged( SubscriptionListener.CR_METADATA );
	}
	
	@Override
	public boolean
	isSubscriptionTemplate()
	{
		return( getSubscriptionType() == 2 );
	}
	
	private int
	getSubscriptionType()
	{
		if ( subscription_type == -1 ){

			if ( getName(false).startsWith( "Search Template:" )){
				
				subscription_type = 1;
				
			}else{
				
				subscription_type = 0;
			
				try{
					Engine engine = getEngine();
			
					if ( engine instanceof RSSEngine ){
				
						if (((RSSEngine)engine).getSearchUrl().startsWith( "subscription:?type=template" )){
							
							subscription_type = 2;
						}
					}
				}catch( Throwable e ){
		
					Debug.printStackTrace(e);
				}
			}
		}
			
		return( subscription_type );
	}
	
	@Override
	public boolean
	isSearchTemplate()
	{
		return( getSubscriptionType() == 1 );
	}

	protected Map
	getSingletonDetails()
	{
		return( singleton_details );
	}

	protected boolean
	getSingletonPublishAttempted()
	{
		return( singleton_sp_attempted );
	}

	protected void
	setSingletonPublishAttempted()
	{
		if ( !singleton_sp_attempted ){

			singleton_sp_attempted = true;

			manager.configDirty( this, SubscriptionListener.CR_METADATA );
		}
	}

	@Override
	public String
	getName()
	{
		return( getName( true ));
	}

	@Override
	public String
	getName(
		boolean	use_local )
	{
		return( local_name==null?name:local_name );
	}

	@Override
	public String
	getURI()
	{
		String str = "sub:?name=" + UrlUtils.encode(getName()) + "&id=" + Base32.encode(getShortID()) + "&v=" + getVersion();

		if ( is_anonymous ){

			str += "&a=1";
		}

		return( "azplug:?id=subscription&arg=" + UrlUtils.encode( str ));
	}

	@Override
	public void
	requestAttention()
	{
		manager.selectSubscription( this );
	}

	@Override
	public void
	setLocalName(
		String		str )
	{
		local_name = str;

		manager.configDirty( this, SubscriptionListener.CR_METADATA );

		fireChanged(SubscriptionListener.CR_METADATA);
	}

	@Override
	public void
	setName(
		String		_name )

		throws SubscriptionException
	{
		if ( !name.equals( _name )){

			boolean	ok = false;

			String	old_name 	= name;
			int		old_version	= version;

			try{
				name	= _name;

				version++;

				SubscriptionBodyImpl body = new SubscriptionBodyImpl( manager, this );

				syncToBody( body );

				versionUpdated( body, false );

				ok	= true;

			}finally{

				if ( !ok ){

					name 	= old_name;
					version	= old_version;
				}
			}

			fireChanged(SubscriptionListener.CR_METADATA);
		}
	}

	@Override
	public String
	getNameEx()
	{
		if ( name_ex == null ){

			try{
				Map map = JSONUtils.decodeJSON( getJSON());

				String	search_term	= (String)map.get( "search_term" );
				Map		filters		= (Map)map.get( "filters" );

				Engine engine = manager.getEngine( this, map, true );

				String	engine_name = engine.getNameEx();

				if ( name.startsWith( engine_name )){

					name_ex = name;

				}else if ( engine_name.startsWith( name )){

					name_ex = engine_name;

				}else{

					name_ex = name + ": " + engine.getNameEx();
				}

				if ( search_term != null && search_term.length() > 0 ){

					name_ex += ", query=" + search_term;
				}

				if ( filters != null && filters.size() > 0 ){

					name_ex += ", filters=" + new SubscriptionResultFilterImpl(this,filters).getString();
				}

			}catch( Throwable e ){

				name_ex = name + ": " + Debug.getNestedExceptionMessage(e);
			}
		}

		return( name_ex );
	}

	@Override
	public String
	getQueryKey()
	{
		try{
			Map map = JSONUtils.decodeJSON( getJSON());

			String	search_term	= (String)map.get( "search_term" );
			Map		filters		= (Map)map.get( "filters" );

			Engine engine = manager.getEngine( this, map, true );

			String	name = engine.getNameEx();

			if ( search_term != null && search_term.length() > 0 ){

				name += ", query=" + search_term;
			}

			if ( filters != null && filters.size() > 0 ){

				name += ", filters=" + new SubscriptionResultFilterImpl(this,filters).getString();
			}

			return( name );

		}catch( Throwable e ){

			return( null );
		}
	}

	@Override
	public long
	getAddTime()
	{
		return( add_time );
	}

	@Override
	public int
	getAddType()
	{
		return( add_type );
	}

	@Override
	public boolean
	isPublic()
	{
		return( is_public );
	}

	@Override
	public boolean
	isAnonymous()
	{
		return( is_anonymous );
	}

	@Override
	public void
	setPublic(
		boolean		_is_public )

		throws SubscriptionException
	{
		if ( is_public != _is_public ){

			boolean	ok = false;

			boolean	old_public	= is_public;
			int		old_version	= version;

			try{
				is_public	= _is_public;

				version++;

				SubscriptionBodyImpl body = new SubscriptionBodyImpl( manager, this );

				syncToBody( body );

				versionUpdated( body, false );

				ok = true;

			}finally{

				if ( !ok ){

					version		= old_version;
					is_public	= old_public;
				}
			}

			fireChanged(SubscriptionListener.CR_METADATA);
		}
	}

	protected boolean
	getServerPublicationOutstanding()
	{
		return( server_publication_outstanding );
	}

	protected void
	setServerPublicationOutstanding()
	{
		if ( !server_publication_outstanding ){

			server_publication_outstanding = true;

			fireChanged(SubscriptionListener.CR_METADATA);
		}
	}

	protected void
	setServerPublished()
	{
		if ( server_publication_outstanding || !server_published ){

			server_published 				= true;
			server_publication_outstanding	= false;

			fireChanged(SubscriptionListener.CR_METADATA);
		}
	}

	protected boolean
	getServerPublished()
	{
		return( server_published );
	}

	@Override
	public String
	getJSON()

		throws SubscriptionException
	{
		try{
			SubscriptionBodyImpl body = new SubscriptionBodyImpl( manager, this );

			return( body.getJSON());

		}catch( Throwable e ){

			history.setFatalError( Debug.getNestedExceptionMessage(e));

			if ( e instanceof SubscriptionException ){

				throw((SubscriptionException)e );
			}

			throw( new SubscriptionException( "Failed to read subscription", e ));
		}
	}

	@Override
	public boolean
	setJSON(
		String		_json )

		throws SubscriptionException
	{
		String json = embedEngines( _json );

		SubscriptionBodyImpl body = new SubscriptionBodyImpl( manager, this );

		String	old_json = body.getJSON();

		if ( !json.equals( old_json )){

			boolean	ok = false;

			int		old_version	= version;

			try{
				version++;

				body.setJSON( json );

				syncToBody( body );

				versionUpdated( body, true );

				referer = null;

				ok	= true;

			}finally{

				if ( !ok ){

					version	= old_version;
				}
			}

			fireChanged(SubscriptionListener.CR_METADATA);

			return( true );
		}

		return( false );
	}

	protected String
	embedEngines(
		String		json_in )
	{
			// see if we need to embed private search templates

		Map map = JSONUtils.decodeJSON( json_in );

		long 	engine_id 	= ((Long)map.get( "engine_id" )).longValue();

		String	json_out	= json_in;

		if ( engine_id >= Integer.MAX_VALUE || engine_id < 0 ){

			Engine engine = MetaSearchManagerFactory.getSingleton().getMetaSearch().getEngine( engine_id );

			if ( engine == null ){

				log( "Private search template with id '" + engine_id + "' not found!!!!" );

			}else{

				try{
					embedEngines( map, engine );

					json_out = JSONUtils.encodeToJSON( map );


					log( "Embedded private search template '" + engine.getName() + "'" );

				}catch( Throwable e ){

					log( "Failed to embed private search template", e );
				}
			}
		}

		return( json_out );
	}

	protected static void
	embedEngines(
		Map			map,
		Engine		engine )
	{
		Map	engines = new HashMap();

		map.put( "engines", engines );

		Map	engine_map = new HashMap();

		try{

			String	engine_str = new String( Base64.encode( BEncoder.encode( engine.exportToBencodedMap())), Constants.UTF_8 );

			engine_map.put( "content", engine_str );

			engines.put( String.valueOf( engine.getId()), engine_map );

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	protected Engine
	extractEngine(
		Map		json_map,
		long	id )
	{
		Map engines = (Map)json_map.get( "engines" );

		if ( engines != null ){

			Map	engine_map = (Map)engines.get( String.valueOf( id ));

			if ( engine_map != null ){

				String	engine_str = (String)engine_map.get( "content" );

				try{

					Map map = BDecoder.decode( Base64.decode( engine_str.getBytes( Constants.UTF_8 )));

					return( MetaSearchManagerFactory.getSingleton().getMetaSearch().importFromBEncodedMap(map));

				}catch( Throwable e ){

					log( "failed to import engine", e );
				}
			}
		}

		return( null );
	}

	@Override
	public Subscription
	cloneWithNewEngine(
		Engine		engine )

		throws SubscriptionException
	{
		try{
			String	json = getJSON();

			Map map = JSONUtils.decodeJSON( json );

			long	id = ((Long)map.get( "engine_id" )).longValue();

			if ( id == engine.getId()){

				embedEngines(map, engine);

				SubscriptionImpl subs = new SubscriptionImpl( manager, getName(), engine.isPublic(), isAnonymous(), null, JSONUtils.encodeToJSON(map), SubscriptionImpl.ADD_TYPE_CREATE );

				subs = manager.addSubscription( subs );

				setLocalName( getName( false ) + " (old)" );

				return( subs );

			}else{

				throw( new SubscriptionException( "Engine mismatch" ));
			}
		}catch( Throwable e ){

			throw( new SubscriptionException( "Failed to export engine", e ));
		}
	}

	@Override
	public Engine
	getEngine()

		throws SubscriptionException
	{
		return( getEngine( true ));
	}

	protected Engine
	getEngine(
		boolean		local_only )

		throws SubscriptionException
	{
		Map map = JSONUtils.decodeJSON( getJSON());

		return( manager.getEngine( this, map, local_only ));
	}

	protected void
	engineUpdated(
		Engine		engine )
	{
		try{
			String	json = getJSON();

			Map map = JSONUtils.decodeJSON( json );

			long	id = ((Long)map.get( "engine_id" )).longValue();

			if ( id == engine.getId()){

				if ( setJSON( json )){

					log( "Engine has been updated, saved" );
				}
			}
		}catch( Throwable e ){

			log( "Engine update failed", e );
		}
	}

	@Override
	public boolean
	setDetails(
		String		_name,
		boolean		_is_public,
		String		_json )

		throws SubscriptionException
	{
		_json = embedEngines( _json );

		SubscriptionBodyImpl body = new SubscriptionBodyImpl( manager, this );

		String	old_json = body.getJSON();

		boolean	json_changed = !_json.equals( old_json );

		if ( 	!_name.equals( name ) ||
				_is_public != is_public ||
				json_changed ){

			boolean	ok = false;

			String	old_name	= name;
			boolean	old_public	= is_public;
			int		old_version	= version;

			try{
				is_public	= _is_public;
				name		= _name;

				body.setJSON( _json );

				version++;

				syncToBody( body );

				versionUpdated( body, json_changed );

				ok = true;

			}finally{

				if ( !ok ){

					version		= old_version;
					is_public	= old_public;
					name		= old_name;
				}
			}

			fireChanged(SubscriptionListener.CR_METADATA);

			return( true );
		}

		return( false );
	}

	protected void
	versionUpdated(
		SubscriptionBodyImpl		body,
		boolean						json_changed )
	{
		if ( json_changed ){

			try{
				Map map = JSONUtils.decodeJSON( body.getJSON());

				schedule_map = (Map)map.get( "schedule" );

			}catch( Throwable e ){
			}
		}

		name_ex = null;

		if ( is_public ){

			manager.updatePublicSubscription( this );

			setPublished( false );

			synchronized( this ){

				for (int i=0;i<associations.size();i++){

					((association)associations.get(i)).setPublished( false );
				}
			}
		}
	}

	@Override
	public byte[]
	getPublicKey()
	{
		return( public_key );
	}

	public byte[]
	getShortID()
	{
		return( short_id );
	}

	@Override
	public String
	getID()
	{
		if (id == null) {
			id = Base32.encode(getShortID());
		}
		return( id );
	}

	protected byte[]
	getPrivateKey()
	{
		return( private_key );
	}

	protected int
	getFixedRandom()
	{
		return( fixed_random );
	}

	@Override
	public int
	getVersion()
	{
		return( version );
	}

	@Override
	public int
	getAZVersion()
	{
		return( az_version );
	}

	protected void
	setHighestUserPromptedVersion(
		int		v )
	{
		if ( v < version ){

			v  = version;
		}

		if ( highest_prompted_version != v ){

			highest_prompted_version = v;

			fireChanged(SubscriptionListener.CR_METADATA);
		}
	}

	protected int
	getHighestUserPromptedVersion()
	{
		return( highest_prompted_version );
	}

	@Override
	public int
	getHighestVersion()
	{
		return( Math.max( version, highest_prompted_version ));
	}

	@Override
	public void
	resetHighestVersion()
	{
		if ( highest_prompted_version > 0 ){

			highest_prompted_version = 0;

			fireChanged(SubscriptionListener.CR_METADATA);

			manager.checkUpgrade(this);
		}
	}

	@Override
	public boolean
	isMine()
	{
		if ( private_key == null ){

			return( false );
		}

		if ( isSingleton() && add_type != ADD_TYPE_CREATE ){

			return( false );
		}

		return( true );
	}

	@Override
	public boolean
	isUpdateable()
	{
		return( private_key != null );
	}

	@Override
	public boolean
	isSubscribed()
	{
		return( is_subscribed );
	}

	@Override
	public void
	setSubscribed(
		boolean			s )
	{
		if ( is_subscribed != s ){

			is_subscribed = s;

			if ( is_subscribed ){

				manager.setSelected( this );

			}else{

				reset();
			}

			fireChanged(SubscriptionListener.CR_METADATA);
		}
	}

	@Override
	public boolean
	isAutoDownloadSupported()
	{
		return( history.isAutoDownloadSupported());
	}

	@Override
	public void
	getPopularity(
		final SubscriptionPopularityListener	listener )

		throws SubscriptionException
	{
		new AEThread2( "subs:popwait", true )
		{
			@Override
			public void
			run()
			{
				try{
					manager.getPopularity(
						SubscriptionImpl.this,
						new SubscriptionPopularityListener()
						{
							@Override
							public void
							gotPopularity(
								long						pop )
							{
								if ( pop != popularity ){

									popularity = pop;

									fireChanged(SubscriptionListener.CR_METADATA);
								}

								listener.gotPopularity( popularity );
							}

							@Override
							public void
							failed(
								SubscriptionException		e )
							{
								if ( popularity == -1 ){

									listener.failed( new SubscriptionException( "Failed to read popularity", e ));

								}else{

									listener.gotPopularity( popularity );
								}
							}
						});

				}catch( Throwable e ){

					if ( popularity == -1 ){

						listener.failed( new SubscriptionException( "Failed to read popularity", e ));

					}else{

						listener.gotPopularity( popularity );
					}
				}
			}
		}.start();
	}

	@Override
	public long
	getCachedPopularity()
	{
		return( popularity );
	}

	protected void
	setCachedPopularity(
		long		pop )
	{
		if ( pop != popularity ){

			popularity		= pop;

			fireChanged(SubscriptionListener.CR_METADATA);
		}
	}

	@Override
	public String
	getReferer()
	{
		if ( referer == null ){

			try{
				Map map = JSONUtils.decodeJSON( getJSON());

				Engine engine = manager.getEngine( this, map, false );

				if ( engine != null ){

					referer = engine.getReferer();
				}
			}catch( Throwable e ){

				log( "Failed to get referer", e );
			}

			if ( referer == null ){

				referer = "";
			}
		}

		return( referer );
	}

	protected void
	checkPublish()
	{
		synchronized( this ){

			if ( destroyed ){

				return;
			}

				// singleton's not available for upgrade

			if ( isSingleton()){

				return;
			}

				// nothing to do for unsubscribed ones

			if ( !isSubscribed()){

				return;
			}

			if ( popularity > 100 ){

					// one off test on whether to track so we have around 100 active

				if ( lws_skip_check == 2 ){

					return;

				}else if ( lws_skip_check == 0 ){

					if ( RandomUtils.nextInt((int)(( popularity + 99 ) / 100 )) == 0 ){

						lws_skip_check = 1;

					}else{

						lws_skip_check = 2;

						return;
					}
				}
			}

			if ( hash != null ){

				boolean	create = false;

				if ( lws == null ){

					create = true;

				}else{

					if ( !Arrays.equals( lws.getHash().getBytes(), hash )){

						lws.remove();

						create = true;
					}
				}

				if ( create ){

					try{
						File original_data_location = manager.getVuzeFile( this );

						if ( original_data_location.exists()){

								// make a version based filename to avoid issues regarding multiple
								// versions

							final File	versioned_data_location = FileUtil.newFile( original_data_location.getParent(), original_data_location.getName() + "." + getVersion());

							if ( !versioned_data_location.exists()){

								if ( !FileUtil.copyFile( original_data_location, versioned_data_location )){

									throw( new Exception( "Failed to copy file to '" + versioned_data_location + "'" ));
								}
							}

							lws = LightWeightSeedManager.getSingleton().add(
									getName(),
									new HashWrapper( hash ),
									TorrentUtils.getDecentralisedEmptyURL(),
									versioned_data_location,
									isAnonymous()?AENetworkClassifier.AT_I2P:AENetworkClassifier.AT_PUBLIC,
									new LightWeightSeedAdapter()
									{
										@Override
										public TOTorrent
										getTorrent(
											byte[] 		hash,
											URL 		announce_url,
											File 		data_location)

											throws Exception
										{
											log( "Generating light-weight torrent: hash=" + ByteFormatter.encodeString( hash ));

											TOTorrentCreator creator =
												TOTorrentFactory.createFromFileOrDirWithFixedPieceLength(
														data_location,
														announce_url,
														256*1024 );

											TOTorrent t = creator.create();

											t.setHashOverride( hash );

											return( t );
										}
									});
						}

					}catch( Throwable e ){

						log( "Failed to create light-weight-seed", e );
					}
				}
			}
		}
	}

	protected synchronized boolean
	canAutoUpgradeCheck()
	{
		if ( isSingleton()){

			return( false );
		}

		long	now = SystemTime.getMonotonousTime();

		if ( last_auto_upgrade_check == -1 || now - last_auto_upgrade_check > 4*60*60*1000 ){

			last_auto_upgrade_check = now;

			return( true );
		}

		return( false );
	}

	@Override
	public void
	addAssociation(
		byte[]		hash )
	{
		if ( hash.length != 20 ){

			Debug.out( "Invalid hash: " + ByteFormatter.encodeString( hash ));

			return;
		}

		addAssociationSupport( hash, false );
	}

	protected boolean
	addAssociationSupport(
		byte[]		hash,
		boolean		internal )
	{
		synchronized( this ){

			for (int i=0;i<associations.size();i++){

				association assoc = (association)associations.get(i);

				if ( Arrays.equals( assoc.getHash(), hash )){

					return( false );
				}
			}

			associations.add( new association( hash, SystemTime.getCurrentTime()));

			if ( MAX_ASSOCIATIONS > 0 && associations.size() > MAX_ASSOCIATIONS ){

				associations.remove( RandomUtils.nextInt( MAX_ASSOCIATIONS - MIN_RECENT_ASSOC_TO_RETAIN ));
			}
		}

		if ( !internal ){

			fireChanged(SubscriptionListener.CR_METADATA);

			manager.associationAdded( this, hash);
		}

		return( true );
	}

	@Override
	public boolean
	hasAssociation(
		byte[]		hash )
	{
		synchronized( this ){

			for (int i=0;i<associations.size();i++){

				association assoc = (association)associations.get(i);

				if ( Arrays.equals( assoc.getHash(), hash )){

					return( true );
				}
			}
		}

		return( false );
	}

	@Override
	public void
	addPotentialAssociation(
		String		result_id,
		String		key )
	{
		manager.addPotentialAssociation( this, result_id, key );
	}

	@Override
	public int
	getAssociationCount()
	{
		synchronized( this ){

			return( associations.size());
		}
	}

	protected association
	getAssociationForPublish()
	{
		synchronized( this ){

			int	num_assoc = associations.size();

				// first set in order of most recent

			for (int i=num_assoc-1;i>=Math.max( 0, num_assoc-MIN_RECENT_ASSOC_TO_RETAIN);i--){

				association assoc = (association)associations.get(i);

				if ( !assoc.getPublished()){

					assoc.setPublished( true );

					return( assoc );
				}
			}

				// remaining randomised

			int	rem = associations.size() - MIN_RECENT_ASSOC_TO_RETAIN;

			if ( rem > 0 ){

				List<association> l = new ArrayList<>(associations.subList(0, rem));

				Collections.shuffle( l );

				for (int i=0;i<l.size();i++){

					association assoc = l.get(i);

					if ( !assoc.getPublished()){

						assoc.setPublished( true );

						return( assoc );
					}
				}
			}
		}

		return( null );
	}

	protected int
	getAssociationsRemainingForPublish()
	{
		synchronized( this ){

			int	result = 0;

			for ( association a: associations ){

				if ( !a.getPublished()){

					result++;
				}
			}

			return( result );
		}
	}

	protected boolean
	getPublished()
	{
		return( published );
	}

	protected void
	setPublished(
		boolean		b )
	{
		published = b;
	}

	protected int
	getVerifiedPublicationVersion(
		Map		details )
	{
			// singleton versions always 1 and each instance has separate private key so
			// verification will always fail so save to just return current version

		if ( isSingleton()){

			return( getVersion());
		}

		if ( !verifyPublicationDetails( details )){

			return( -1 );
		}

		return( getPublicationVersion( details ));
	}

	protected static int
	getPublicationVersion(
		Map		details )
	{
		return(((Long)details.get("v")).intValue());
	}

	protected byte[]
	getPublicationHash()
	{
		return( hash );
	}

	protected static byte[]
	getPublicationHash(
		Map		details )
	{
		return((byte[])details.get( "h" ));
	}

	protected static int
	getPublicationSize(
		Map		details )
	{
		return(((Long)details.get("z")).intValue());
	}

	protected Map
	getPublicationDetails()
	{
		Map	result = new HashMap();

		result.put( "v", new Long( version ));

		if ( singleton_details == null ){

			result.put( "h", hash );
			result.put( "z", new Long( sig_data_size ));
			result.put( "s", sig );

		}else{

			result.put( "x", singleton_details );
		}

		return( result );
	}

	protected boolean
	verifyPublicationDetails(
		Map		details )
	{
		synchronized( this ){

			if ( BEncoder.mapsAreIdentical( verify_cache_details, details )){

				return( verify_cache_result );
			}
		}

		byte[]	hash 	= (byte[])details.get( "h" );
		int		version	= ((Long)details.get( "v" )).intValue();
		int		size	= ((Long)details.get( "z" )).intValue();
		byte[]	sig		= (byte[])details.get( "s" );

		boolean	result = SubscriptionBodyImpl.verify( public_key, hash, version, size, sig );

		synchronized( this ){

			verify_cache_details 	= details;
			verify_cache_result		= result;
		}

		return( result );
	}

	@Override
	public void
	setCreatorRef(
		String	ref )
	{
		creator_ref = ref;

		fireChanged(SubscriptionListener.CR_METADATA);
	}

	@Override
	public String
	getCreatorRef()
	{
		return( creator_ref );
	}

	@Override
	public void
	setCategory(
		String	_category )
	{
		if ( _category == null && category == null ){

			return;
		}

		if ( _category != null && category != null && _category.equals( category )){

			return;
		}

		manager.setCategoryOnExisting( this, category, _category );

		category = _category;

		fireChanged(SubscriptionListener.CR_METADATA);
	}

	@Override
	public String
	getCategory()
	{
		return( category );
	}

	@Override
	public void
	setTagID(
		long	_tag_id )
	{

		if ( _tag_id == tag_id ){

			return;
		}

		// don't update existing download tagging at the moment
		//manager.setTagOnExisting( this, tag, _tag );

		tag_id = _tag_id;

		fireChanged(SubscriptionListener.CR_METADATA);
	}

	@Override
	public long
	getTagID()
	{
		return( tag_id );
	}

	public int
	getViewOptions()
	{
		return( view_options );
	}
	
	public void
	setViewOptions(
		int		_options )
	{

		if ( _options == view_options ){

			return;
		}

		view_options = _options;

		fireChanged(SubscriptionListener.CR_METADATA);
	}
	
	@Override
	public String
	getParent()
	{
		return( parent );
	}

	@Override
	public void
	setParent(
		String		_parent )
	{
		if ( _parent == null && parent == null ){

			return;
		}

		if ( _parent != null && parent != null && _parent.equals( parent )){

			return;
		}

		parent = _parent;

		fireChanged(SubscriptionListener.CR_METADATA);
	}

	@Override
	public long
	getMetadataMutationIndicator()
	{
		return( md_mutator.get());
	}
	
	protected void
	fireChanged(
		int		reason )
	{
		if ( reason == SubscriptionListener.CR_METADATA ){
			
			md_mutator.incrementAndGet();
		}
		
		manager.configDirty( this, reason );

		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((SubscriptionListener)it.next()).subscriptionChanged( this, reason );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	fireDownloaded()
	{

		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((SubscriptionListener)it.next()).subscriptionDownloaded( this );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	addListener(
		SubscriptionListener	l )
	{
		listeners.add( l );
	}

	@Override
	public void
	removeListener(
		SubscriptionListener	l )
	{
		listeners.remove( l );
	}

	@Override
	public SubscriptionHistoryImpl
	getHistory()
	{
		return( history );
	}

	@Override
	public SubscriptionManager
	getManager()
	{
		return( manager );
	}

	@Override
	public VuzeFile
	getVuzeFile()

		throws SubscriptionException
	{
		try{
			return( VuzeFileHandler.getSingleton().loadVuzeFile( manager.getVuzeFile( this ).getAbsolutePath()));

		}catch( Throwable e ){

			throw( new SubscriptionException( "Failed to get Vuze file", e ));
		}
	}

	@Override
	public VuzeFile
	getSearchTemplateVuzeFile()
	{
		if ( !isSearchTemplate()){

			return( null );
		}

		Object[] details = manager.getSearchTemplateVuzeFile( this );

		if ( details != null ){

			return((VuzeFile)details[0]);
		}

		return( null );
	}

	@Override
	public boolean
	isSearchTemplateImportable()
	{
		return( manager.isSearchTemplateImportable( this ));
	}

	protected void
	destroy()
	{
		LightWeightSeed l;

		synchronized( this ){

			destroyed	= true;

			l = lws;
		}

		if ( l != null ){

			l.remove();
		}
	}

	@Override
	public void
	reset()
	{
		getHistory().reset();

		try{
			getEngine().reset();

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	@Override
	public void
	remove()
	{
		destroy();

		manager.removeSubscription( this );
	}

	protected boolean
	isRemoved()
	{
		synchronized( this ){

			return( destroyed );
		}
	}

	@Override
	public SubscriptionResult[]
  	getResults(
  		boolean		include_deleted )
	{
		return( getHistory().getResults( include_deleted ));
	}

	protected void
	invalidateNewestResultTime()
	{
		newest_result_time_next.incrementAndGet();
	}
	
	public long
	getNewestResultTime()
	{
		long latest = newest_result_time;
		long seq	= newest_result_time_seq;
		
		long next = newest_result_time_next.get();
		
		if ( newest_result_time_seq == next ){
			
			return( latest );
		}
				
		latest = 0;
		
		SubscriptionResult[] results = getResults(false);
			
		for ( SubscriptionResult result : results ){
			
			if ( result.isDeleted() || result.getRead()){
				
				continue;
			}
			
			long timeFound = result.getTimeFound();
			
			if ( timeFound > latest ){
				
				latest = timeFound;
			}
		}
	
		newest_result_time		= latest;
		newest_result_time_seq	= next;
		
		return( latest );
	}
	
	@Override
	public long 
	getNextUpdateTime()
	{
		return( manager.getScheduler().getNextUpdateTime( this ));
	}
	
	@Override
	public SubscriptionResultFilterImpl
	getFilters()

		throws SubscriptionException
	{
		Map map = JSONUtils.decodeJSON( getJSON());

		Map		filters		= (Map)map.get( "filters" );

		return( new SubscriptionResultFilterImpl( this, filters ));
	}

	@Override
	public void
	setUserData(
		Object		key,
		Object		data )
	{
		synchronized( user_data ){

			if ( data == null ){

				user_data.remove( key );

			}else{

				user_data.put( key, data );
			}
		}
	}

	@Override
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
		manager.log( getString() + ": " + str );
	}

	protected void
	log(
		String		str,
		Throwable	e )
	{
		manager.log( getString() + ": " + str, e );
	}

	@Override
	public String
	getString()
	{
		return( "name=" + name +
					",sid=" + ByteFormatter.encodeString( short_id ) +
					",ver=" + version +
					",pub=" + is_public +
					",anon=" + is_anonymous +
					",mine=" + isMine() +
					",sub=" + is_subscribed +
					(is_subscribed?(",hist={" + history.getString() + "}"):"") +
					",pop=" + popularity +
					(server_publication_outstanding?",spo=true":""));
	}

	protected void
	generate(
		IndentWriter		writer )
	{
		String	engine_str;

		try{

			engine_str = "" + getEngine().getId();

		}catch( Throwable e ){

			engine_str = Debug.getNestedExceptionMessage(e);
		}

		writer.println( getString() + ": engine=" + engine_str );

		try{
			writer.indent();

			synchronized( this ){

				for (int i=0;i<associations.size();i++){

					((association)associations.get(i)).generate( writer );
				}
			}
		}finally{

			writer.exdent();
		}
	}

	protected static class
	association
	{
		private byte[]	hash;
		private long	when;
		private boolean	published;

		protected
		association(
			byte[]		_hash,
			long		_when )
		{
			hash		= _hash;
			when		= _when;
		}

		protected byte[]
		getHash()
		{
			return( hash );
		}

		protected long
		getWhen()
		{
			return( when );
		}

		protected boolean
		getPublished()
		{
			return( published );
		}

		protected void
		setPublished(
			boolean		b )
		{
			published = b;
		}

		protected String
		getString()
		{
			return( ByteFormatter.encodeString( hash ) + ", pub=" + published );
		}

		protected void
		generate(
			IndentWriter		writer )
		{
			writer.println( getString());
		}
	}
}
