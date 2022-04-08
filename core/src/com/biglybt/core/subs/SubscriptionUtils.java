/*
 * Created on Sep 22, 2008
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

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.history.DownloadHistoryManager;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SystemTime;

public class
SubscriptionUtils
{
	public static SubscriptionDownloadDetails[]
	getAllCachedDownloadDetails(Core core)
	{
		List<DownloadManager> 	dms 	= core.getGlobalManager().getDownloadManagers();

		List<SubscriptionDownloadDetails>	result 	= new ArrayList<>();

		SubscriptionManager sub_man = SubscriptionManagerFactory.getSingleton();

		for (int i=0;i<dms.size();i++){

			DownloadManager	dm = dms.get(i);

			TOTorrent torrent = dm.getTorrent();

			if ( torrent != null ){

				try{
					Subscription[] subs = sub_man.getKnownSubscriptions( torrent.getHash());

					if ( subs != null && subs.length > 0 ){

						if ( sub_man.hideSearchTemplates()){

							List<Subscription>	filtered = new ArrayList<>();

							for ( Subscription s: subs ){

								if ( !s.isSearchTemplate()){

									filtered.add( s );
								}
							}

							if ( filtered.size() > 0 ){

								result.add( new SubscriptionDownloadDetails( dm, filtered.toArray( new Subscription[filtered.size()] )));
							}
						}else{

							result.add( new SubscriptionDownloadDetails( dm, subs ));
						}
					}
				}catch( Throwable e ){
				}
			}
		}

		return(result.toArray( new SubscriptionDownloadDetails[result.size()]));
	}

	public static String
	getSubscriptionChatKey(
		Subscription		subs )
	{
		try{
			String key = null;

			Engine engine = subs.getEngine();

			if ( engine instanceof WebEngine ){

				WebEngine web_engine = (WebEngine)subs.getEngine();

				key = web_engine.getSearchUrl( true );

			}else{

				key = subs.getQueryKey();
			}

			if ( key != null ){

				key = "Subscription: " + key;
			}

			return( key );

		}catch( Throwable e ){

			return( null );
		}
	}

	public static void
	peekChatAsync(
		final String		net,
		final String		key,
		final Runnable		done )
	{
			// this is here to decouple subscriptions from chat

		try{
			Class<?> utils = SubscriptionUtils.class.getClassLoader().loadClass("com.biglybt.plugin.net.buddy.BuddyPluginUtils");

			if ( utils != null ){

				utils.getMethod( "peekChatAsync", String.class, String.class, Runnable.class ).invoke( null, net, key, done );
			}
		}catch( Throwable e ){

		}
	}

	private static final Object	HS_KEY = new Object();

	public static final int HS_NONE			= 0;
	public static final int HS_LIBRARY		= 1;
	public static final int HS_ARCHIVE		= 2;
	public static final int HS_HISTORY		= 3;
	public static final int HS_UNKNOWN		= 4;
	public static final int HS_FETCHING		= 5;

	private static GlobalManager 								gm;
	private static com.biglybt.pif.download.DownloadManager		dm;
	private static DownloadHistoryManager						hm;
	
	public static int
	getHashStatus(
		SubscriptionResult	result )
	{
		if ( result == null || result.isDeleted()){

			return( HS_NONE );
		}

		String hash_str = result.getAssetHash();

		if ( hash_str == null ){

			return( HS_UNKNOWN );
		}

		byte[] hash =  Base32.decode( hash_str );
		
		if ( hash == null ){

			return( HS_UNKNOWN );
		}
		
		long	now = SystemTime.getMonotonousTime();

		synchronized( HS_KEY ){

			if ( gm == null ){

				Core core = CoreFactory.getSingleton();

				gm = core.getGlobalManager();
				dm = core.getPluginManager().getDefaultPluginInterface().getDownloadManager();
				hm = (DownloadHistoryManager)gm.getDownloadHistoryManager();
			}
		}

		int hs_result;

		com.biglybt.core.download.DownloadManager dl = gm.getDownloadManager(new HashWrapper(hash));
		
		if ( dl != null ){
			
			hs_result = dl.getDownloadState().getFlag(DownloadManagerState.FLAG_METADATA_DOWNLOAD)?HS_FETCHING:HS_LIBRARY;

		}else if ( dm.lookupDownloadStub( hash ) != null ){

			hs_result = HS_ARCHIVE;

		}else if ( hm.getDates(hash) != null ){

			hs_result = HS_HISTORY;

		}else{

			hs_result = HS_NONE;
		}

		return( hs_result );
	}
	
	public static int
	getHashStatus(
		SearchSubsResultBase	result )
	{
		if ( result == null ){

			return( HS_NONE );
		}

		byte[] hash = result.getHash();

		if ( hash == null || hash.length != 20 ){

			return( HS_UNKNOWN );
		}

		long	now = SystemTime.getMonotonousTime();

		Object[] entry = (Object[])result.getUserData( HS_KEY );

		if ( entry != null ){

			long time = (Long)entry[0];

			if ( now - time < 10*1000 ){

				return((Integer)entry[1] );
			}
		}

		synchronized( HS_KEY ){

			if ( gm == null ){

				Core core = CoreFactory.getSingleton();

				gm = core.getGlobalManager();
				dm = core.getPluginManager().getDefaultPluginInterface().getDownloadManager();
				hm = (DownloadHistoryManager)gm.getDownloadHistoryManager();
			}
		}

		int hs_result;

		com.biglybt.core.download.DownloadManager dl = gm.getDownloadManager(new HashWrapper(hash));
		
		if ( dl != null ){
			
			hs_result = dl.getDownloadState().getFlag(DownloadManagerState.FLAG_METADATA_DOWNLOAD)?HS_FETCHING:HS_LIBRARY;

		}else if (dm.lookupDownloadStub( hash ) != null ){

			hs_result = HS_ARCHIVE;

		}else if ( hm.getDates(hash) != null ){

			hs_result = HS_HISTORY;

		}else{

			hs_result = HS_NONE;
		}

		result.setUserData( HS_KEY, new Object[]{ now + RandomUtils.nextInt( 2500 ), hs_result });

		return( hs_result );
	}
	
	public static class
	SubscriptionDownloadDetails
	{
		private DownloadManager		download;
		private Subscription[]		subscriptions;

		protected
		SubscriptionDownloadDetails(
			DownloadManager		dm,
			Subscription[]		subs )
		{
			download 		= dm;
			subscriptions	= subs;
		}

		public DownloadManager
		getDownload()
		{
			return( download );
		}

		public Subscription[]
		getSubscriptions()
		{
			return( subscriptions );
		}
	}
}
