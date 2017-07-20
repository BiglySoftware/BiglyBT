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

import java.security.Signature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.messenger.PlatformMessengerException;
import com.biglybt.core.security.CryptoECCUtils;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.ByteFormatter;

public class
PlatformSubscriptionsMessenger
{
	private static final boolean	MESSAGING_ENABLED = true;

	private static final PlatformMessengerConfig	dispatcher =
		new PlatformMessengerConfig( "subscription", false );

	private static final String OP_CREATE_SUBS					= "create-subscription";
	private static final String OP_UPDATE_SUBS					= "update-subscription";
	private static final String OP_GET_SUBS_BY_SID				= "get-subscriptions";
	private static final String OP_GET_POP_BY_SID				= "get-subscription-infos";
	private static final String OP_SET_SELECTED					= "set-selected";

	public static void
	updateSubscription(
		boolean		create,
		String		name,
		byte[]		public_key,
		byte[]		private_key,
		byte[]		sid,
		int			version,
		boolean		is_anon,
		String		content )

		throws PlatformMessengerException
	{
		String operation = create?OP_CREATE_SUBS:OP_UPDATE_SUBS;

		checkEnabled( operation );

		Map parameters = new HashMap();

		String	sid_str = Base32.encode( sid );
		String	pk_str	= Base32.encode(public_key) ;

		parameters.put( "name", name );
		parameters.put( "subscription_id", sid_str );
		parameters.put( "version_number", new Long( version ));
		parameters.put( "content", content );

		if ( create ){

			parameters.put( "public_key", pk_str );
		}

		try{
			Signature sig = CryptoECCUtils.getSignature( CryptoECCUtils.rawdataToPrivkey( private_key ));

			sig.update( ( name + pk_str + sid_str + version + content ).getBytes( "UTF-8" ));

			byte[]	sig_bytes = sig.sign();

			/*
			Signature verify = CryptoECCUtils.getSignature( CryptoECCUtils.rawdataToPubkey( public_key ));

			verify.update( ( name + pk_str + sid_str + version + content ).getBytes( "UTF-8" ));

			boolean ok = verify.verify( sig_bytes );
			*/

			parameters.put( "signature", Base32.encode( sig_bytes ));

			dispatcher.syncInvoke( operation, parameters, is_anon );

		}catch( Throwable e ){

			throw( new PlatformMessengerException( "Failed to create/update subscription", e ));
		}
	}

	public static subscriptionDetails
	getSubscriptionBySID(
		byte[]		sid,
		boolean		is_anon )

		throws PlatformMessengerException
	{
		checkEnabled( OP_GET_SUBS_BY_SID );

		Map parameters = new HashMap();

		List	sid_list = new JSONArray();

		sid_list.add( Base32.encode( sid ));

		parameters.put( "subscription_ids", sid_list);

		Map reply = dispatcher.syncInvoke(	OP_GET_SUBS_BY_SID, parameters, is_anon );

		for (int i=0;i<sid_list.size();i++){

			Map	map = (Map)reply.get((String)sid_list.get(i));

			if ( map != null ){

				subscriptionDetails details = new subscriptionDetails( map );

				return( details );
			}
		}

		throw( new PlatformMessengerException( "Unknown sid '" + ByteFormatter.encodeString(sid) + "'" ));
	}

	public static long
	getPopularityBySID(
		byte[]		sid )

		throws PlatformMessengerException
	{
		checkEnabled( OP_GET_POP_BY_SID );

		Map parameters = new HashMap();

		List	sid_list = new JSONArray();

		sid_list.add( Base32.encode( sid ));

		parameters.put( "subscription_ids", sid_list );

		Map reply = dispatcher.syncInvoke(	OP_GET_POP_BY_SID, parameters );

		for (int i=0;i<sid_list.size();i++){

			Map	map = (Map)reply.get((String)sid_list.get(i));

			if ( map != null ){

				subscriptionInfo info = new subscriptionInfo( map );

				return( info.getPopularity());
			}
		}

		return( -1 );
	}

	public static List[]
	setSelected(
		List	sids )

		throws PlatformMessengerException
	{
		checkEnabled( OP_SET_SELECTED );

		Map parameters = new HashMap();

		List	sid_list 	= new JSONArray();
		for (int i=0;i<sids.size();i++){

			sid_list.add( Base32.encode( (byte[])sids.get(i) ));
		}

		parameters.put( "subscription_ids", sid_list);

		Map reply = dispatcher.syncInvoke( OP_SET_SELECTED, parameters );

		List	versions = (List)reply.get( "version_numbers" );

		if ( versions == null ){

			// test

			versions = new ArrayList();

			for (int i=0;i<sids.size();i++){

				versions.add( new Long(1));
			}
		}

		List	popularities = (List)reply.get( "popularities" );

		if ( popularities == null ){

				// migrate

			popularities = new ArrayList();

			for (int i=0;i<sids.size();i++){

				popularities.add( new Long(-1));
			}
		}

		return( new List[]{ versions,popularities } );
	}

	protected static void
	checkEnabled(
		String		method )

		throws PlatformMessengerException
	{
		if ( !MESSAGING_ENABLED ){

			throw( new PlatformMessengerException( "messaging disabled" ));
		}
	}

	public static class
	subscriptionInfo
	{
		private Map		info;

		protected
		subscriptionInfo(
			Map		_info )
		{
			info	= _info;
		}

		public long
		getPopularity()
		{
			return(((Long)info.get( "popularity" )).intValue());
		}
	}

	public static class
	subscriptionDetails
	{
		private Map		details;

		protected
		subscriptionDetails(
			Map		_details )
		{
			details = _details;
		}

		public String
		getName()
		{
			return( getString( "name" ));
		}

		public String
		getContent()
		{
			return( getString( "content" ));
		}

		public int
		getPopularity()
		{
			Long	l_pop = (Long)details.get( "popularity" );

			if ( l_pop != null ){

				return( l_pop.intValue());
			}

			return( -1 );
		}

		protected String
		getString(
			String	key )
		{
			Object obj = details.get( key );

			if ( obj instanceof String ){

				return((String)obj);

			}else if ( obj instanceof byte[] ){

				byte[]	bytes = (byte[])obj;

				try{
					return( new String( bytes, "UTF-8" ));

				}catch( Throwable e ){

					return( new String( bytes ));
				}
			}else{

				return( null );
			}
		}
	}

	public static void
	main(
		String[]	args )
	{
		try{
			CoreFactory.create();

			String short_id = "";

			long res = getPopularityBySID( Base32.decode( short_id ));

			System.out.println( res );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
