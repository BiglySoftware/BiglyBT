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
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.torrent.TOTorrent;

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
