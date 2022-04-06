/*
 * Created on May 6, 2008
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


package com.biglybt.core.metasearch.impl;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

import com.biglybt.util.MapUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.messenger.config.PlatformMetaSearchMessenger;
import com.biglybt.core.metasearch.*;
import com.biglybt.core.metasearch.impl.plugin.PluginEngine;
import com.biglybt.core.metasearch.impl.web.json.JSONEngine;
import com.biglybt.core.metasearch.impl.web.regex.RegexEngine;
import com.biglybt.core.metasearch.impl.web.rss.RSSEngine;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionManager;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.util.*;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.util.JSONUtils;


public abstract class
EngineImpl
	implements Engine
{
	private static final int DEFAULT_UPDATE_CHECK_SECS = 24*60*60;

	static boolean logging_enabled;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Logger.Enabled",
				"Logger.DebugFiles.Disable",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName)
				{
					logging_enabled = 
						COConfigurationManager.getBooleanParameter( "Logger.Enabled" ) && 
						!COConfigurationManager.getBooleanParameter( "Logger.DebugFiles.Disable" );
				}
			});
	}

	protected static EngineImpl
	importFromBEncodedMap(
		MetaSearchImpl		meta_search,
		Map					map )

		throws IOException
	{
		int	type = ((Long)map.get( "type" )).intValue();

		if ( type == Engine.ENGINE_TYPE_JSON ){

			return( JSONEngine.importFromBEncodedMap( meta_search, map ));

		}else if ( type == Engine.ENGINE_TYPE_REGEX ){

			return( RegexEngine.importFromBEncodedMap( meta_search, map ));

		}else if ( type == Engine.ENGINE_TYPE_PLUGIN ){

			return( PluginEngine.importFromBEncodedMap( meta_search, map ));

		}else if ( type == Engine.ENGINE_TYPE_RSS ){

			return( RSSEngine.importFromBEncodedMap( meta_search, map ));

		}else{

			throw( new IOException( "Unknown engine type " + type ));
		}
	}

	public static Engine
	importFromJSONString(
		MetaSearchImpl	meta_search,
		int				type,
		long			id,
		long			last_updated,
		float			rank_bias,
		String			name,
		String			content )

		throws IOException
	{
		JSONObject map = (JSONObject)JSONUtils.decodeJSON( content );

		if ( type == Engine.ENGINE_TYPE_JSON ){

			return( JSONEngine.importFromJSONString( meta_search, id, last_updated, rank_bias, name, map ));

		}else if ( type == Engine.ENGINE_TYPE_REGEX ){

			return( RegexEngine.importFromJSONString( meta_search, id, last_updated, rank_bias, name, map ));

		}else if ( type == Engine.ENGINE_TYPE_RSS ){

			return( RSSEngine.importFromJSONString( meta_search, id, last_updated, rank_bias, name, map ));

		}else{

			throw( new IOException( "Unknown engine type " + type ));
		}
	}

		// if you extend this with state pertaining to what has been downloaded then
		// make sure you extend the code in reset();

	protected static final String		LD_COOKIES				= "cookies";
	protected static final String		LD_ETAG					= "etag";
	protected static final String		LD_LAST_MODIFIED		= "last_mod";

	protected static final String		LD_LAST_UPDATE_CHECK	= "last_update_check";
	protected static final String		LD_UPDATE_CHECK_SECS	= "update_check_secs";
	protected static final String		LD_CREATED_BY_ME		= "mine";

	protected static final String		LD_AUTO_DL_SUPPORTED	= "auto_dl_supported";
	protected static final String		LD_LINK_IS_TORRENT		= "link_is_torrent";


	private MetaSearchImpl	meta_search;

	private int			type;
	private long		id;
	private long		last_updated;
	private String		name;

	private byte[]		uid;
	private int			version;

	private boolean		is_public					= true;

	private int			az_version;

	private int			selection_state				= SEL_STATE_DESELECTED;
	private boolean		selection_state_recorded	= true;

	private int			source	= ENGINE_SOURCE_UNKNOWN;

	private float		rank_bias		= 1;
	private float		preferred_count	= 0;


		// first mappings used to canonicalise names and map field to same field
		// typically used for categories (musak->music)

	private List		first_level_mapping	= new ArrayList();

		// second mappings used to generate derived field values
		// typically used to derive content_type from category (music->AUDIO)

	private List		second_level_mapping	= new ArrayList();

		// applicable to non-vuze hosted templates

	private String 		update_url;
	private int			update_check_default_secs;

	private Map			user_data;

		// manual constructor

	protected
	EngineImpl(
		MetaSearchImpl	_meta_search,
		int 			_type,
		long 			_id,
		long			_last_updated,
		float			_rank_bias,
		String 			_name )
	{
		meta_search		= _meta_search;
		type			= _type;
		id				= _id;
		last_updated	= _last_updated;
		rank_bias		= _rank_bias;
		name			= _name;

		version			= 1;
		az_version		= AZ_VERSION;

		allocateUID( id );
	}

		// bencoded constructor

	protected
	EngineImpl(
		MetaSearchImpl	_meta_search,
		Map				map )

		throws IOException
	{
		meta_search		= _meta_search;

		type			= ((Long)map.get( "type" )).intValue();

		Long l_id = (Long)map.get( "id");

		id				= l_id==null?meta_search.getManager().getLocalTemplateID():l_id.longValue();
		last_updated	= MapUtils.importLong( map, "last_updated", 0 );
		name			= MapUtils.getMapString( map, "name", null );

		selection_state	= (int) MapUtils.importLong( map, "selected", SEL_STATE_DESELECTED );

		selection_state_recorded = MapUtils.getMapBoolean(map,"select_rec", true );

		source			= (int) MapUtils.importLong( map, "source", ENGINE_SOURCE_UNKNOWN );

		rank_bias		= MapUtils.importFloatFromString( map, "rank_bias", 1.0f );
		preferred_count	= MapUtils.importFloatFromString( map, "pref_count", 0.0f );

		first_level_mapping 	= importBEncodedMappings( map, "l1_map" );
		second_level_mapping 	= importBEncodedMappings( map, "l2_map" );

		version = (int) MapUtils.importLong( map, "version", 1 );

		az_version = (int) MapUtils.importLong( map, "az_version", AZ_VERSION );

		if ( az_version > AZ_VERSION ){

			throw( new IOException( MessageText.getString("metasearch.template.version.bad", new String[]{ name })));
		}

		uid		= (byte[])map.get( "uid" );

		if ( uid == null ){

			allocateUID( id );
		}

		update_url 					= MapUtils.getMapString( map, "update_url", null );
		update_check_default_secs	= (int) MapUtils.importLong( map, "update_url_check_secs", DEFAULT_UPDATE_CHECK_SECS );
	}

		// bencoded export

	protected void
	exportToBencodedMap(
		Map			map,
		boolean		generic )

		throws IOException
	{
		map.put( "type", new Long( type ));

		MapUtils.setMapString( map, "name", name );

		map.put( "source", new Long( source ));

		exportBEncodedMappings( map, "l1_map", first_level_mapping );
		exportBEncodedMappings( map, "l2_map", second_level_mapping );

		map.put( "version", new Long( version ));
		map.put( "az_version", new Long( az_version ));

		MapUtils.exportFloatAsString( map, "rank_bias", rank_bias );

		if ( !generic ){

			map.put( "id", new Long( id ));

			map.put( "last_updated", new Long( last_updated ));

			map.put( "selected", new Long( selection_state ));

			MapUtils.exportBooleanAsLong( map, "select_rec", selection_state_recorded );

			MapUtils.exportFloatAsString( map, "pref_count", preferred_count );

			map.put( "uid", uid );
		}

		if ( update_url != null ){

			MapUtils.setMapString( map, "update_url", update_url );
		}

		if ( update_check_default_secs != DEFAULT_UPDATE_CHECK_SECS ){
			map.put( "update_url_check_secs", new Long( update_check_default_secs ));
		}
	}

		// json constructor

	protected
	EngineImpl(
		MetaSearchImpl	meta_search,
		int				type,
		long			id,
		long			last_updated,
		float			rank_bias,
		String			name,
		JSONObject		map )

		throws IOException
	{
		this( meta_search, type, id, last_updated, rank_bias, name );

		first_level_mapping 	= importJSONMappings( map, "value_map", true );
		second_level_mapping 	= importJSONMappings( map, "ctype_map", false );

		version 	= (int) MapUtils.importLong( map, "version", 1 );
		az_version 	= (int) MapUtils.importLong( map, "az_version", AZ_VERSION );

		if ( az_version > AZ_VERSION ){

			throw( new IOException( MessageText.getString( "metasearch.template.version.bad", new String[]{ name })));
		}

		String uid_str		= (String)map.get( "uid" );

		if ( uid_str == null ){

			allocateUID( id );

		}else{

			uid = Base32.decode( uid_str );
		}

		update_url 					= MapUtils.getMapString( map, "update_url", null );
		update_check_default_secs	= (int) MapUtils.importLong( map, "update_url_check_secs", DEFAULT_UPDATE_CHECK_SECS );
	}

		// json export

	protected void
	exportToJSONObject(
		JSONObject		res )

		throws IOException
	{
		exportJSONMappings( res, "value_map", first_level_mapping, true );
		exportJSONMappings( res, "ctype_map", second_level_mapping, false );

		res.put( "version", new Long( version ));
		res.put( "az_version", new Long( az_version ));

		res.put( "uid", Base32.encode( uid ));

		if ( update_url != null ){

			res.put( "update_url", update_url );
		}

		res.put( "update_url_check_secs", new Long( update_check_default_secs ));
	}

	protected List
	importJSONMappings(
		JSONObject		map,
		String			str,
		boolean			level_1 )

		throws IOException
	{
		List	result = new ArrayList();

		JSONObject	field_map = (JSONObject)map.get( str );

		if ( field_map != null ){

			Iterator	it = field_map.entrySet().iterator();

			while( it.hasNext()){

				Map.Entry	entry = (Map.Entry)it.next();

				String	key 		= (String)entry.getKey();
				List	mappings 	= (List)entry.getValue();

					// limited support for the moment:
					//		level one always maps to same field
					//		level two always maps to content type

				int	from_field 	= vuzeFieldToID( key );

				if ( from_field == -1 ){

					log( "Unrecognised remapping key '" + key + "'" );

					continue;

				}

				int	to_field	= level_1?from_field:FIELD_CONTENT_TYPE;

				List	frs_l = new ArrayList();

				for (int i=0;i<mappings.size();i++){

					JSONObject mapping = (JSONObject)mappings.get(i);

					String	from_str 	= URLDecoder.decode((String)mapping.get( level_1?"from_string":"cat_string" ),"UTF-8");

					if ( from_str == null ){

						log( "'from' value missing in " + mapping );

						continue;
					}

					from_str 	= URLDecoder.decode( from_str, "UTF-8" );

					String	to_str 		= URLDecoder.decode((String)mapping.get( level_1?"to_string":"media_type" ),"UTF-8");

					if ( to_str == null ){

						log( "'to' value missing in " + mapping );

						continue;
					}

					frs_l.add( new FieldRemapping( from_str, to_str ));
				}

				FieldRemapping[] frs = (FieldRemapping[])frs_l.toArray( new FieldRemapping[frs_l.size()]);

				result.add( new FieldRemapper( from_field, to_field, frs ));
			}
		}

		return( result );
	}

	protected void
	exportJSONMappings(
		JSONObject			res,
		String				str,
		List				l,
		boolean				level_1 )
	{
		JSONObject	field_map = new JSONObject();

		res.put( str, field_map );

		for (int i=0;i<l.size();i++){

			FieldRemapper remapper = (FieldRemapper)l.get(i);

			int	from_field	= remapper.getInField();
			//int	to_field	= remapper.getOutField();

			String from_field_str = vuzeIDToField( from_field );

			JSONArray	mappings = new JSONArray();

			field_map.put( from_field_str, mappings );

			FieldRemapping[] frs = remapper.getMappings();

			for (int j=0;j<frs.length;j++){

				FieldRemapping fr = frs[j];

				String from_str = UrlUtils.encode( fr.getMatchString());

				String to_str	= fr.getReplacement();

				JSONObject map = new JSONObject();

				mappings.add( map );

				map.put( level_1?"from_string":"cat_string", from_str );
				map.put( level_1?"to_string":"media_type", to_str );
			}
		}
	}

	protected List
	importBEncodedMappings(
		Map		map,
		String	name )

		throws IOException
	{
		List	result = new ArrayList();

		List	l = (List)map.get(name);

		if ( l != null ){

			for (int i=0;i<l.size();i++){

				Map	entry = (Map)l.get(i);

				int	from_field 	= ((Long)entry.get( "from" )).intValue();
				int	to_field 	= ((Long)entry.get( "to" )).intValue();

				List	l2 = (List)entry.get( "maps" );

				FieldRemapping[]	mappings = new FieldRemapping[ l2.size() ];

				for (int j=0;j<mappings.length;j++){

					Map	entry2 = (Map)l2.get(j);

					String	from_str 	= MapUtils.getMapString( entry2, "from", null );
					String	to_str 		= MapUtils.getMapString( entry2, "to", null );

					mappings[j] = new FieldRemapping( from_str, to_str );
				}

				result.add( new FieldRemapper( from_field, to_field, mappings ));
			}
		}

		return( result );
	}

	protected void
	exportBEncodedMappings(
		Map		map,
		String	name,
		List	mappings )

		throws IOException
	{
		List	l = new ArrayList();

		map.put( name, l );

		for ( int i=0;i<mappings.size();i++){

			FieldRemapper mapper = (FieldRemapper)mappings.get(i);

			Map m = new HashMap();

			l.add( m );

			m.put( "from", new Long( mapper.getInField()));
			m.put( "to", new Long( mapper.getOutField()));

			List l2 = new ArrayList();

			m.put( "maps", l2 );

			FieldRemapping[] frs = mapper.getMappings();

			for (int j=0;j<frs.length;j++){

				FieldRemapping fr = frs[j];

				Map m2 = new HashMap();

				l2.add( m2 );

				MapUtils.setMapString( m2, "from", fr.getMatchString());
				MapUtils.setMapString( m2, "to", fr.getReplacement());
			}
		}
	}

	@Override
	public String
	exportToJSONString()

		throws IOException
	{
		JSONObject	obj = new JSONObject();

		exportToJSONObject( obj );

		return( obj.toString());
	}

	@Override
	public int
	getAZVersion()
	{
		return( az_version );
	}

	@Override
	public int
	getVersion()
	{
		return( version );
	}

	protected void
	setVersion(
		int		_v )
	{
		version	= _v;
	}

	@Override
	public String
	getUID()
	{
		return( Base32.encode( uid ));
	}

	protected void
	setUID(
		String	str )
	{
		uid	= Base32.decode( str );
	}

	protected void
	allocateUID(
		long		id )
	{
		uid = new byte[10];

		if ( id >= 0 && id < Integer.MAX_VALUE ){

				// id is a vuze one, derive uid from it as already unique

			uid[0] 	= (byte)(id>>24);
			uid[1]	= (byte)(id>>16);
			uid[2]	= (byte)(id>>8);
			uid[3]	= (byte)id;

		}else{

			RandomUtils.nextSecureBytes( uid );

			configDirty();
		}
	}

	@Override
	public boolean
	sameLogicAs(
		Engine	other )
	{
		try{
			Map	m1 = exportToBencodedMap();
			Map	m2 = other.exportToBencodedMap();

			String[]	to_remove =
				{	"type",
					"id",
					"last_updated",
					"selected",
					"select_rec",
					"source",
					"rank_bias",
					"pref_count",
					"version",
					"az_version",
					"uid" };

			for (int i=0;i<to_remove.length;i++){

				m1.remove( to_remove[i] );
				m2.remove( to_remove[i] );
			}

			return( BEncoder.mapsAreIdentical( m1, m2 ));

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return( false );
		}
	}

	@Override
	public Result[]
	search(
		SearchParameter[] 		params,
		Map						context,
		int						desired_max_matches,
		int						absolute_max_matches,
		String					headers,
		final ResultListener	listener )

		throws SearchException
	{
		if ( context == null ){

			context = new HashMap();
		}

		try{
			final Set<Result>		results_informed 	= new HashSet<>();
			final boolean[]			complete_informed	= { false };

			ResultListener	interceptor =
				new ResultListener()
				{
					@Override
					public void
					contentReceived(
						Engine 		engine,
						String 		content )
					{
						listener.contentReceived(engine, content);
					}

					@Override
					public void
					matchFound(
						Engine engine,
						String[] fields )
					{
						listener.matchFound(engine, fields);
					}

					@Override
					public void
					resultsReceived(
						Engine engine,
						Result[] 	results )
					{
						listener.resultsReceived(engine, results);

						synchronized( results_informed ){

							results_informed.addAll( Arrays.asList( results ));
						}
					}

					@Override
					public void
					resultsComplete(
						Engine 		engine)
					{
						listener.resultsComplete(engine);

						synchronized( results_informed ){

							complete_informed[0] = true;
						}
					}

					@Override
					public void
					engineFailed(
						Engine 		engine,
						Throwable 	cause )
					{
						listener.engineFailed(engine, cause);
					}

					@Override
					public void
					engineRequiresLogin(
						Engine 		engine,
						Throwable 	cause )
					{
						listener.engineRequiresLogin(engine, cause);
					}
				};

			Result[] results = searchAndMap( params, context, desired_max_matches, absolute_max_matches, headers, listener==null?null:interceptor );

			if ( listener != null ){

				boolean			inform_complete;
				List<Result>	inform_result = new ArrayList<>();

				synchronized( results_informed ){

					for ( Result r: results ){

						if ( !results_informed.contains( r )){

							inform_result.add( r );
						}
					}

					inform_complete = !complete_informed[0];
				}

				if ( inform_result.size() > 0 ){

					listener.resultsReceived( this, inform_result.toArray( new Result[ inform_result.size()] ));
				}

				if ( inform_complete ){

					listener.resultsComplete( this );
				}
			}

			return( results );

		}catch( Throwable e ){

			if ( e instanceof SearchLoginException ){

				if ( listener != null ){

					listener.engineRequiresLogin(this, e);
				}

				throw((SearchLoginException)e);

			}else if ( e instanceof SearchException ){

				if ( listener != null ){

					listener.engineFailed( this, e);
				}

				throw((SearchException)e);

			}else{

				if ( listener != null ){

					listener.engineFailed( this, e);
				}

				throw( new SearchException( "Search failed", e ));
			}
		}
	}

	protected Result[]
	searchAndMap(
		SearchParameter[] 			params,
		Map							context,
		int							desired_max_matches,
		int							absolute_max_matches,
		String						headers,
		final ResultListener		listener )

		throws SearchException
	{
			// default values

		if ( context.get( Engine.SC_SOURCE ) == null ){

			context.put( Engine.SC_SOURCE, "search" );
		}

		Result[] results =
			 searchSupport(
					params,
					context,
					desired_max_matches,
					absolute_max_matches,
					headers,
					new ResultListener()
					{
						@Override
						public void
						contentReceived(
							Engine 		engine,
							String 		content )
						{
							if ( listener != null ){
								listener.contentReceived(engine, content);
							}
						}

						@Override
						public void
						matchFound(
							Engine 		engine,
							String[] 	fields )
						{
							if ( listener != null ){
								listener.matchFound(engine, fields);
							}
						}

						@Override
						public void
						resultsReceived(
							Engine 		engine,
							Result[] 	results)
						{
							if ( listener != null ){
								listener.resultsReceived(engine, mapResults( results ));
							}
						}

						@Override
						public void
						resultsComplete(
							Engine engine )
						{
							if ( listener != null ){
								listener.resultsComplete(engine);
							}
						}

						@Override
						public void
						engineFailed(
							Engine engine,
							Throwable cause )
						{
							log( "Search failed", cause );

							if ( listener != null ){
								listener.engineFailed(engine, cause);
							}
						}

						@Override
						public void
						engineRequiresLogin(
							Engine engine,
							Throwable cause )
						{
							log( "Search requires login", cause );

							if ( listener != null ){
								listener.engineRequiresLogin(engine, cause);
							}
						}
					});

		 return( mapResults( results ));
	}

	protected Result[]
	mapResults(
		Result[]	results )
	{
		for (int i=0;i<results.length;i++){

			Result result = results[i];

			for (int j=0;j<first_level_mapping.size();j++){

				FieldRemapper mapper = (FieldRemapper)first_level_mapping.get(j);

				mapper.remap( result );
			}

			for (int j=0;j<second_level_mapping.size();j++){

				FieldRemapper mapper = (FieldRemapper)second_level_mapping.get(j);

				mapper.remap( result );
			}
		}

		return( results );
	}

	protected abstract Result[]
	searchSupport(
		SearchParameter[] 	params,
		Map					searchContext,
		int					desired_max_matches,
		int					absolute_max_matches,
		String				headers,
		ResultListener		listener )

		throws SearchException;

	@Override
	public void
	delete()
	{
		meta_search.removeEngine( this );
	}

	protected MetaSearchImpl
	getMetaSearch()
	{
		return( meta_search );
	}

	protected int
	vuzeFieldToID(
		String	field )
	{
		for (int i=0;i<FIELD_NAMES.length;i++){

			if ( field.equalsIgnoreCase( FIELD_NAMES[i] )){

				return( FIELD_IDS[i]);
			}
		}

		return( -1 );
	}

	protected String
	vuzeIDToField(
		int		id )
	{
		for (int i=0;i<FIELD_IDS.length;i++){

			if ( id == FIELD_IDS[i] ){

				return( FIELD_NAMES[i]);
			}
		}

		return( null );
	}

	@Override
	public int
	getType()
	{
		return( type );
	}

	protected void
	setId(
		long		_id )
	{
		id	= _id;
	}

	@Override
	public long
	getId()
	{
		return id;
	}

	@Override
	public long
	getLastUpdated()
	{
		return( last_updated );
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	protected void
	setName(
		String		n )
	{
		name		= n;
	}

	@Override
	public boolean
	isActive()
	{
		int state = getSelectionState();

		return(	state != SEL_STATE_DESELECTED && state != SEL_STATE_FORCE_DESELECTED );
	}

	@Override
	public boolean
	isPublic()
	{
		return( is_public );
	}

	protected void
	setPublic(
		boolean		p )
	{
		is_public = p;
	}

	@Override
	public int
	getSelectionState()
	{
		return( selection_state );
	}

	@Override
	public void
	setSelectionState(
		int		state )
	{
		if ( state != selection_state ){

				// only record transitions to or from manual selection for non-local templates

			if ( getSource() == ENGINE_SOURCE_VUZE ){

				if ( 	state == SEL_STATE_MANUAL_SELECTED ||
						selection_state == SEL_STATE_MANUAL_SELECTED ){

					selection_state_recorded = false;

					checkSelectionStateRecorded();
				}
			}

			selection_state	= state;

			configDirty();

			meta_search.stateChanged( this );
		}
	}

	@Override
	public boolean
	isAuthenticated()
	{
		return( false );
	}

	@Override
	public void
	recordSelectionState()
	{
		selection_state_recorded = false;

		checkSelectionStateRecorded();
	}

	@Override
	public void
	checkSelectionStateRecorded()
	{
		if ( !selection_state_recorded ){

			try{
				boolean selected = selection_state != SEL_STATE_DESELECTED && selection_state != SEL_STATE_FORCE_DESELECTED;

				log( "Marking template id " + getId() + " as selected=" + selected );

				PlatformMetaSearchMessenger.setTemplatetSelected( meta_search.getManager().getExtensionKey(), getId(), "", selected);

				selection_state_recorded = true;

			}catch( Throwable e ){

				log( "Failed to record selection state", e );
			}
		}
	}

	@Override
	public int
	getSource()
	{
		return( source );
	}

	@Override
	public void
	setSource(
		int		_source )
	{
		if ( source != _source ){

			source	= _source;

			configDirty();
		}
	}

	@Override
	public float
	getRankBias()
	{
		return( rank_bias );
	}

	@Override
	public void
	setRankBias(
		float	_rank_bias )
	{
		if ( rank_bias != _rank_bias ){

			rank_bias	= _rank_bias;

			configDirty();
		}
	}

	@Override
	public void
	setPreferredDelta(
		float	 delta )
	{
		float new_pref = preferred_count + delta;

		new_pref = Math.max( 0, new_pref );
		new_pref = Math.min( 10, new_pref );

		if ( new_pref != preferred_count ){

			preferred_count = new_pref;

			configDirty();
		}
	}

	@Override
	public float
	getPreferredWeighting()
	{
		return( preferred_count );
	}

	@Override
	public float
	applyRankBias(
		float	_rank )
	{
		float rank = _rank*rank_bias;

		rank *= (1 + 0.025 * preferred_count );

		rank = Math.min( rank, 1.0f );

		rank = Math.max( rank, 0.0f );

		// System.out.println( getName() + " (" + rank_bias+"/"+preferred_count + "): " + _rank + " -> " + rank );

		return( rank );
	}

	@Override
	public boolean
	isMine()
	{
		return( getLocalBoolean( LD_CREATED_BY_ME, false ));
	}

	@Override
	public void
	setMine(
		boolean		mine )
	{
		setLocalBoolean( LD_CREATED_BY_ME, mine );
	}

	protected String
	getUpdateURL()
	{
		return( update_url );
	}

	protected void
	setUpdateURL(
		String	url )
	{
		update_url	= url;
	}

	protected int
	getUpdateCheckSecs()
	{
		long	l = getLocalLong( LD_UPDATE_CHECK_SECS, 0 );

		if ( l != 0 ){

			return((int)l);
		}

		return( update_check_default_secs );
	}

	protected void
	setDefaultUpdateCheckSecs(
		int		secs )
	{
		update_check_default_secs = secs;
	}

	protected void
	setLocalUpdateCheckSecs(
		int		secs )
	{
		setLocalLong( LD_UPDATE_CHECK_SECS, secs );
	}

	protected long
	getLastUpdateCheck()
	{
		return( getLocalLong( LD_LAST_UPDATE_CHECK, 0 ));
	}

	protected void
	setLastUpdateCheck(
		long		when )
	{
		setLocalLong( LD_LAST_UPDATE_CHECK, when );
	}

	@Override
	public int
	getAutoDownloadSupported()
	{
			// default impl based on availability of fields that permit auto-dl
			// overridden in RSS engine ( for example ) to be based on explicit
			// channel tag

		boolean ok =
			supportsField( Engine.FIELD_TORRENTLINK ) ||
			supportsField( Engine.FIELD_DOWNLOADBTNLINK );

		return( ok?AUTO_DL_SUPPORTED_YES:AUTO_DL_SUPPORTED_NO );
	}
	protected void
	configDirty()
	{
		if ( meta_search != null ){

			meta_search.configDirty();
		}
	}

	@Override
	public void
	addPotentialAssociation(
		String	key )
	{
		meta_search.addPotentialAssociation( this, key );
	}

	@Override
	public Subscription
	getSubscription()
	{
		try{
			VuzeFile vf = exportToVuzeFile( true );

			byte[] bytes = vf.exportToBytes();

			String url_str = "vuze://?body=" + new String( bytes, Constants.BYTE_ENCODING_CHARSET );

			boolean is_anon = isAnonymous();

			SubscriptionManager sub_man = SubscriptionManagerFactory.getSingleton();

			Subscription subs =
				sub_man.createSingletonRSS(
					vf.getName() + ": " + getName() + " (v" + getVersion() + ")",
					new URL( url_str ),
					Integer.MAX_VALUE,
					is_anon );

			return( subs );

		}catch( Throwable e ){

			Debug.out( e );
		}

		return( null );
	}

	@Override
	public void
	exportToVuzeFile(
		File	target )

		throws IOException
	{
		VuzeFile	vf = VuzeFileHandler.getSingleton().create();

		vf.addComponent(
			VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE,
			exportToBencodedMap());

		vf.write( target );
	}

	@Override
	public VuzeFile
	exportToVuzeFile()

		throws IOException
	{
		return( exportToVuzeFile( false ));
	}

	public VuzeFile
	exportToVuzeFile(
		boolean		generic )

		throws IOException
	{
		VuzeFile	vf = VuzeFileHandler.getSingleton().create();

		vf.addComponent(
			VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE,
			exportToBencodedMap( generic ));

		return( vf );
	}

	private String
	getLocalKey()
	{
		return( "metasearch.engine." + id + ".local" );
	}

	@Override
	public void
	reset()
	{
		synchronized( this ){

			Map map = COConfigurationManager.getMapParameter( getLocalKey(), new HashMap());

			map.remove(	LD_COOKIES );
			map.remove(	LD_ETAG );
			map.remove(	LD_LAST_MODIFIED );

			COConfigurationManager.setParameter( getLocalKey(), map );
		}
	}

	protected void
	setLocalString(
		String		key,
		String		value )
	{
		synchronized( this ){

			String	existing = getLocalString( key );

			if ( existing != null && value != null && existing.equals( value )){

				return;
			}

			Map map = COConfigurationManager.getMapParameter( getLocalKey(), new HashMap());

			try{
				MapUtils.setMapString(map, key, value);

				COConfigurationManager.setParameter( getLocalKey(), map );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected String
	getLocalString(
		String		key )
	{
		synchronized( this ){

			Map map = COConfigurationManager.getMapParameter( getLocalKey(), new HashMap());

			try{
				return( MapUtils.getMapString( map, key, null ));

			}catch( Throwable e ){

				Debug.printStackTrace( e );

				return( null );
			}
		}
	}

	protected void
	setLocalBoolean(
		String		key,
		boolean		value )
	{
		setLocalLong( key, value?1:0 );
	}

	protected boolean
	getLocalBoolean(
		String		key,
		boolean		def )
	{
		return( getLocalLong( key, def?1:0 ) == 1 );
	}

	protected void
	setLocalLong(
		String		key,
		long		value )
	{
		synchronized( this ){

			long	existing = getLocalLong( key, 0 );

			if ( existing == value ){

				return;
			}

			Map map = COConfigurationManager.getMapParameter( getLocalKey(), new HashMap());

			try{
				map.put( key, new Long( value));

				COConfigurationManager.setParameter( getLocalKey(), map );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected long
	getLocalLong(
		String		key,
		long		def )
	{
		synchronized( this ){

			Map map = COConfigurationManager.getMapParameter( getLocalKey(), new HashMap());

			try{
				return( MapUtils.importLong( map, key, def ));

			}catch( Throwable e ){

				Debug.printStackTrace( e );

				return( def );
			}
		}
	}

	protected void
	setUserData(
		Object	key,
		Object	value )
	{
		synchronized( this ){

			if ( user_data == null ){

				if ( key == null ){

					return;
				}

				user_data = new HashMap(4);
			}

			if ( key == null ){

				user_data.remove( key );

				if ( user_data.size() == 0 ){

					user_data = null;
				}
			}else{

				user_data.put( key, value );
			}
		}
	}

	protected Object
	getUserData(
		Object		key )
	{
		synchronized( this ){

			if ( user_data == null ){

				return( null );
			}

			return( user_data.get( key ));
		}
	}

	protected File
	getDebugFile()
	{
		if ( logging_enabled ){

			return( FileUtil.newFile( AEDiagnostics.getLogDir(), "MetaSearch_Engine_" + getId() + ".txt" ));

		}else{

			return( null );
		}
	}

	protected synchronized void
	debugStart()
	{
		File f = getDebugFile();

		if ( f != null ){

			f.delete();
		}
	}

	protected synchronized void
	debugLog(
		String		str )
	{
		File f = getDebugFile();

		if ( f != null ){

			PrintWriter	 pw = null;

			try{
				pw = new PrintWriter(new OutputStreamWriter( FileUtil.newFileOutputStream( f, true )));

				pw.println( str );

			}catch( Throwable e ){

			}finally{

				if ( pw != null ){

					pw.close();
				}
			}
		}
	}

	protected void
	log(
		String		str )
	{
		if ( meta_search != null ){

			meta_search.log( "Engine " + getId() + ": " + str );
		}
	}

	protected void
	log(
		String		str,
		Throwable	e )
	{
		if ( meta_search != null ){

			meta_search.log( "Engine " + getId() + ": " + str, e );
		}
	}

	@Override
	public String
	getString()
	{
		return( "id=" + getId() + ", name=" + getName() + ", source=" + ENGINE_SOURCE_STRS[getSource()] + ", selected=" + SEL_STATE_STRINGS[getSelectionState()] + ", rb=" + rank_bias + ", pref=" + preferred_count );
	}

	public String
	getString(
		boolean		full )
	{
		return( getString());
	}
}
