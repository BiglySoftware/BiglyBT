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


package com.biglybt.core.messenger.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.messenger.PlatformMessenger;
import com.biglybt.core.messenger.PlatformMessengerException;
import com.biglybt.core.util.Debug;

public class
PlatformMetaSearchMessenger
{
	private static final int MAX_TEMPLATE_LIST		= 512;

	private static final PlatformMessengerConfig	dispatcher =
			new PlatformMessengerConfig( "searchtemplate", true );

	private static final String OP_GET_TEMPLATE					= "get-template";
	private static final String OP_GET_TEMPLATES				= "get-templates";
	private static final String OP_LIST_POPULAR_TEMPLATES 		= "list-popular";
	private static final String OP_LIST_FEATURED_TEMPLATES 		= "list-featured";
	private static final String OP_TEMPLATE_SELECTED			= "template-selected";


	public static templateDetails
	getTemplate(
		String		extension_key,
		long		template_id )

		throws PlatformMessengerException
	{
		Map parameters = getParameter( template_id );

		if ( extension_key != null ){

			parameters.put( "extension_key", extension_key );
		}

		Map reply = dispatcher.syncInvoke(	OP_GET_TEMPLATE, parameters );

		templateInfo info = getTemplateInfo( reply );

		if ( info == null ){

			throw( new PlatformMessengerException( "Invalid reply: " + reply ));
		}

		String name 		= (String)reply.get( "name" );
		String value		= (String)reply.get( "value" );
		String engine_type	= (String)reply.get( "engine_id" );

		if ( name == null || value == null || engine_type == null ){

			throw( new PlatformMessengerException( "Invalid reply; field missing: " + reply ));
		}

		int	type;

		if ( engine_type.equals( "json" )){

			type = templateDetails.ENGINE_TYPE_JSON;

		}else if ( engine_type.equals( "regexp" )){

			type = templateDetails.ENGINE_TYPE_REGEXP;

		}else{

			throw( new PlatformMessengerException( "Invalid type '" + engine_type + ": " + reply ));

		}

		return( new templateDetails( info, type, name, value ));
	}

	public static templateInfo[]
   	getTemplateDetails(
   		String		extension_key,
   		long[]		ids )

   		throws PlatformMessengerException
   	{
		if( ids.length == 0 ){

			return( new templateInfo[0]);
		}

		String	str = "";

		for (int i=0;i<ids.length;i++){

			str += (i==0?"":",") + ids[i];
		}

   		Map parameters = new HashMap();

		if ( extension_key != null ){

			parameters.put( "extension_key", extension_key );
		}

   		parameters.put( "templateIds", str );

   		Map reply = dispatcher.syncInvoke(	OP_GET_TEMPLATES, parameters );

   		return( getTemplatesInfo( reply ));
   	}

	public static templateInfo[]
	listTopPopularTemplates(
		String		extension_key,
		String		fud )

		throws PlatformMessengerException
	{
		Map parameters = new HashMap();

		if ( extension_key != null ){

			parameters.put( "extension_key", extension_key );
		}

		parameters.put( "fud", fud );

		Map reply = dispatcher.syncInvoke(	OP_LIST_POPULAR_TEMPLATES, parameters );

		return( getTemplatesInfo( reply ));
	}

	public static templateInfo[]
   	listAllPopularTemplates(
   		String		extension_key,
   		String		fud )

   		throws PlatformMessengerException
   	{
   		Map parameters = new HashMap();

		if ( extension_key != null ){

			parameters.put( "extension_key", extension_key );
		}

		parameters.put( "fud", fud );

   		parameters.put( "page-num", new Long( 1 ));
   		parameters.put( "items-per-page", new Long( MAX_TEMPLATE_LIST ));

   		Map reply = dispatcher.syncInvoke(	OP_LIST_POPULAR_TEMPLATES, parameters );

   		return( getTemplatesInfo( reply ));
   	}

	public static templateInfo[]
	listFeaturedTemplates(
		String		extension_key,
		String		fud )

		throws PlatformMessengerException
	{
		Map parameters = new HashMap();

		if ( extension_key != null ){

			parameters.put( "extension_key", extension_key );
		}

		parameters.put( "fud", fud );

		parameters.put( "page-num", new Long( 1 ));
		parameters.put( "items-per-page", new Long( MAX_TEMPLATE_LIST ));

		Map reply = dispatcher.syncInvoke(	OP_LIST_FEATURED_TEMPLATES, parameters );

		return( getTemplatesInfo( reply ));
	}

	protected static templateInfo[]
	getTemplatesInfo(
		Map		reply )
	{
		List	templates = (List)reply.get( "templates" );

		List	res = new ArrayList();

		for (int i=0;i<templates.size();i++){

			Map m = (Map)templates.get(i);

			templateInfo info = getTemplateInfo( m );

			if ( info != null ){

				res.add( info );
			}
		}

		templateInfo[] res_a = new templateInfo[ res.size()];

		res.toArray( res_a );

		return( res_a );
	}

	protected static templateInfo
  	getTemplateInfo(
  		Map		m )
	{
		Long	id 		= (Long)m.get( "id" );
		Boolean	show	= (Boolean)m.get( "show" );
		Long	date 	= (Long)m.get( "modified_dt" );

		float	rank_bias = 1;

		try{
			String	str = (String)m.get( "rank_bias" );

			if ( str != null ){

				rank_bias = Float.parseFloat( str );
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		if ( show == null ){

			show = Boolean.TRUE;
		}

		if ( id == null || show == null || date == null ){

			PlatformMessenger.debug( "field missing from template info (" + m + ")" );

		}else{

			return( new templateInfo( id.longValue(), date.longValue(), show.booleanValue(), rank_bias ));

			/*
			SimpleDateFormat format = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss.S");

			format.setTimeZone( TimeZone.getTimeZone( "UTC" ));

			try{

				long	millis = format.parse( date ).getTime();

				return( new templateInfo( id.longValue(), millis, show.booleanValue()));

			}catch( Throwable e ){

				PlatformMessenger.debug( "Invalid date received in template: " + m );
			}
			*/
		}

		return( null );
	}

	public static void
	setTemplatetSelected(
		String		extension_key,
		long		template_id,
		String		user_id,
		boolean		is_selected )

		throws PlatformMessengerException
	{
		Map	parameters = getParameter( template_id );

		if ( extension_key != null ){

			parameters.put( "extension_key", extension_key );
		}

		parameters.put( "userId", user_id );
		parameters.put( "selected", Boolean.valueOf(is_selected));

		dispatcher.syncInvoke(	OP_TEMPLATE_SELECTED, parameters );
	}

	protected static Map
	getParameter(
		long		template_id )
	{
		Map parameters = new HashMap();

		parameters.put( "templateId", new Long( template_id ));

		return( parameters );
	}

	public static class
	templateInfo
	{
		private long		id;
		private long		date;
		private boolean		visible;
		private float		rank_bias;

		protected
		templateInfo(
			long		_id,
			long		_date,
			boolean		_visible,
			float		_rank_bias )
		{
			id			= _id;
			date		= _date;
			visible		= _visible;
			rank_bias	= _rank_bias;
		}

		public long
		getId()
		{
			return( id );
		}

		public long
		getModifiedDate()
		{
			return( date );
		}

		public boolean
		isVisible()
		{
			return( visible );
		}

		public float
		getRankBias()
		{
			return( rank_bias );
		}
	}

	public static class
	templateDetails
	{
		public static final int ENGINE_TYPE_JSON	= 1;
		public static final int ENGINE_TYPE_REGEXP	= 2;

		private templateInfo		info;

		private int			type;
		private String		name;
		private String		value;

		protected
		templateDetails(
			templateInfo	_info,
			int				_type,
			String			_name,
			String			_value )
		{
			info		= _info;
			type		= _type;
			name		= _name;
			value		= _value;
		}

		public int
		getType()
		{
			return( type );
		}

		public long
		getId()
		{
			return( info.getId());
		}

		public long
		getModifiedDate()
		{
			return( info.getModifiedDate());
		}

		public float
		getRankBias()
		{
			return( info.getRankBias());
		}

		public boolean
		isVisible()
		{
			if ( name.equals( "Mininova" )){
				
				return( false );
			}
			
			return( info.isVisible());
		}

		public String
		getName()
		{
			return( name );
		}

		public String
		getValue()
		{
			return( value );
		}
	}
}
