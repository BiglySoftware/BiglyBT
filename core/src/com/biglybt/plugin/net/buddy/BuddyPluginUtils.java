/*
 * Created on Nov 5, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.plugin.net.buddy;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerDescriptor;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;


public class
BuddyPluginUtils
{
	private static Object					chat_lock = new Object();

	private static ChatInstance				country_chat;
	private static String[]					country_info;
	
	public static final String CK_CC	 ="BuddyPluginUtils::CC";
	
	private static ChatInstance				language_chat;
	private static String[]					language_info;
	
	public static final String CK_LANG	 ="BuddyPluginUtils::Lang";

	
	protected static void
	betaInit(
		BuddyPluginBeta	bpb )
	{
		new AEThread2( "BPU" )
		{
			public void
			run()
			{
				while( true ) {
					
						// auto-create correct country chat but only if not done previously
					
					try{					
						InetAddress ia = NetworkAdmin.getSingleton().getDefaultPublicAddress();
						
						if ( ia != null ){
							
							String[] info = PeerUtils.getCountryDetails( ia );
							
							if ( info != null ){
								
								synchronized( chat_lock ){
									
									info[0] = info[0].toUpperCase( Locale.US );
									
									String cc = info[0];
																	
									if ( country_chat == null || country_info == null || !country_info[0].equals( cc )){
										
										country_info = info;
										
										if ( country_chat != null ) {
											
											country_chat.destroy();
											
											country_chat = null;
										}
										
										String chat_key = Constants.APP_NAME + ": Country: " + cc ;
										
										country_chat = getBetaPlugin().peekChatInstance( AENetworkClassifier.AT_PUBLIC, chat_key );
										
										if ( country_chat == null ){
											
											String key = "dchat.channel.cc.done." + cc;
											
											if ( !COConfigurationManager.getBooleanParameter( key, false )){
												
												country_chat = 
													getChat( 
														AENetworkClassifier.AT_PUBLIC,
														chat_key );
												
												if ( country_chat != null ) {
													
													country_chat.setFavourite( true );
													
													COConfigurationManager.setParameter( key, true );
												}
											}
										}
										
										if ( country_chat != null ) {
											
											country_chat.setUserData( CK_CC, info[1] );
										}
									}
								}
							}
						}	
					}catch( Throwable e ){
					}
					
					try{
						synchronized( chat_lock ){
							
							Locale locale = MessageText.getCurrentLocale();
							
							String display_name = locale.getDisplayName();
							
							String lang = locale.getLanguage();
							
							String country = locale.getCountry();
							
							String variant = locale.getVariant();
							
							if ( country.length() > 0 ){
								
								lang += "_" + country;
							}
							
							if ( variant.length() > 0 ){
								
								lang += "_" + variant;
							}
							
							if ( language_chat == null || language_info == null || !language_info[0].equals( lang )){
								
								language_info = new String[]{ lang, display_name };
								
								if ( language_chat != null ) {
									
									language_chat.destroy();
									
									language_chat = null;
								}
								
								String chat_key = Constants.APP_NAME + ": Language: " + lang ;
								
								language_chat = getBetaPlugin().peekChatInstance( AENetworkClassifier.AT_PUBLIC, chat_key );
								
								if ( language_chat == null ){
									
									String key = "dchat.channel.lang.done." + lang;
									
									if ( !COConfigurationManager.getBooleanParameter( key, false )){
										
										language_chat = 
											getChat( 
												AENetworkClassifier.AT_PUBLIC,
												chat_key );
										
										if ( language_chat != null ) {
											
											language_chat.setFavourite( true );
											
											COConfigurationManager.setParameter( key, true );
										}
									}
								}
								
								if ( language_chat != null ) {
									
									language_chat.setUserData( CK_LANG, display_name );
								}
							}
						}
							
					}catch( Throwable e ){
					}
					
					try{
						
						Thread.sleep( 60*1000 );
						
					}catch( Throwable e ){
						
					}
				}
			}
		}.start();
	}
	
	public static ChatInstance
	getCountryChat()
	{
		synchronized( chat_lock ){
	
			if ( country_chat == null && country_info != null ){
				
				String chat_key = Constants.APP_NAME + ": Country: " + country_info[0] ;
				
				country_chat = 
						getChat( 
							AENetworkClassifier.AT_PUBLIC,
							chat_key );	
				
				country_chat.setUserData( CK_CC, country_info[1] );
			}
		}
		
		return( country_chat );
	}
	
	public static ChatInstance
	getLanguageChat()
	{
		synchronized( chat_lock ){
	
			if ( language_chat == null && language_info != null ){
				
				String chat_key = Constants.APP_NAME + ": Language: " + language_info[0] ;
				
				language_chat = 
						getChat( 
							AENetworkClassifier.AT_PUBLIC,
							chat_key );	
				
				language_chat.setUserData( CK_LANG, language_info[1] );
			}
		}
		
		return( language_chat );
	}
	
	public static BuddyPlugin
	getPlugin()
	{
		PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "azbuddy", true );

		if ( pi != null ){

			return((BuddyPlugin)pi.getPlugin());
		}

		return( null );
	}

	public static BuddyPluginBeta
	getBetaPlugin()
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){

			BuddyPluginBeta beta = bp.getBeta();

			if ( beta.isAvailable()){

				return( beta );
			}
		}

		return( null );
	}

	public static boolean
	isBetaChatAvailable()
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){

			return( bp.getBeta().isAvailable());
		}

		return( false );
	}

	public static boolean
	isBetaChatAnonAvailable()
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){

			return( bp.getBeta().isAvailable() && bp.getBeta().isI2PAvailable());
		}

		return( false );
	}

	public static void
	createBetaChat(
		final String				network,
		final String				key,
		final CreateChatCallback	callback )
	{
		new AEThread2( "Chat create async" )
		{
			@Override
			public void
			run()
			{
				ChatInstance	result = null;

				try{
					BuddyPlugin bp = getPlugin();

					result = bp.getBeta().getAndShowChat( network, key );

				}catch( Throwable e ){

					Debug.out( e );

				}finally{

					if ( callback != null ){

						callback.complete( result );
					}
				}
			}
		}.start();
	}

	public interface
	CreateChatCallback
	{
		public void
		complete(
			ChatInstance	chat );
	}

	public static Map<String,Object>
	peekChat(
		String		net,
		String		key )
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){

			return( bp.getBeta().peekChat( net, key ));
		}

		return( null );
	}

	public static Map<String,Object>
	peekChat(
		Download		download )
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){

			return( bp.getBeta().peekChat( download, false ));
		}

		return( null );
	}
	
	public static List<ChatInstance>
	peekChatInstances(
		Download		download )
	{	
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){
			
			return( bp.getBeta().peekChatInstances(download));
		}
		
		return( new ArrayList<ChatInstance>());
	}

	private static AsyncDispatcher peek_dispatcher = new AsyncDispatcher( "peeker" );

	public static void
	peekChatAsync(
		final String		net,
		final String		key,
		final Runnable		done )
	{
		boolean	async = false;

		try{
			if ( isBetaChatAvailable()){

				if ( net != AENetworkClassifier.AT_PUBLIC && !isBetaChatAnonAvailable()){

					return;
				}

				if ( peek_dispatcher.getQueueSize() > 200 ){

					return;
				}

				peek_dispatcher.dispatch(
					new AERunnable() {

						@Override
						public void
						runSupport()
						{
							try{
								Map<String,Object> peek_data = BuddyPluginUtils.peekChat( net, key );

								if ( peek_data != null ){

									Number	message_count 	= (Number)peek_data.get( "m" );
									Number	node_count 		= (Number)peek_data.get( "n" );

									if ( message_count != null && node_count != null ){

										if ( message_count.intValue() > 0 ){

											BuddyPluginBeta.ChatInstance chat = BuddyPluginUtils.getChat( net, key );

											if ( chat != null ){

												chat.setAutoNotify( true );
											}
										}
									}
								}
							}finally{

								done.run();
							}
						}
					});

				async = true;
			}
		}finally{

			if ( !async ){

				done.run();
			}
		}
	}

	public static List<ChatInstance>
	getChats()
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){
			
			return( bp.getBeta().getChats());
		}
		
		return( new ArrayList<>());
	}
	
	public static ChatInstance
	getChat(
		String		net,
		String		key )
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){

			try{
				return( bp.getBeta().getChat( net, key ));

			}catch( Throwable e ){

			}
		}

		return( null );
	}

	public static ChatInstance
	getChat(
		String					net,
		String					key,
		Map<String,Object>		options )
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){

			try{
				return( bp.getBeta().getChat( net, key, options ));

			}catch( Throwable e ){

			}
		}

		return( null );
	}

	public static ChatInstance
	getChat(
		Download		download )
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled()){

			return( bp.getBeta().getChat( download ));
		}

		return( null );
	}

	public static BuddyPluginViewInterface.View
	buildChatView(
		Map<String,Object>	properties,
		BuddyPluginViewInterface.ViewListener listener )
	{
		BuddyPlugin bp = getPlugin();

		if ( bp != null && bp.isBetaEnabled() && bp.getBeta().isAvailable()){

			BuddyPluginViewInterface ui = bp.getSWTUI();

			if ( ui != null ){

				return( ui.buildView( properties, listener ));
				
			}else{
				
				ViewWrapper	wrapper = new ViewWrapper( bp, properties,  listener );
				
				return( wrapper );
			}
		}else {
			
			Debug.out( "Can't build view - bp=" + bp );
		}

		return( null );
	}

	public static String
	getChatKey(
		TOTorrent		torrent )
	{
		if ( torrent == null ){

			return( null );
		}

		return( getChatKey( PluginCoreUtils.wrap( torrent )));
	}

	public static String
	getChatKey(
		Download		download )
	{
		return( getChatKey( download.getTorrent()));
	}

	public static String
	getChatKey(
		Peer		peer )
	{
		if ( peer != null ){
		
			try{
				InetAddress ia = AddressUtils.getByName( peer.getIp());
			
				String[] info = PeerUtils.getCountryDetails( ia );
			
				if ( info != null ){
								
					String cc = info[0].toUpperCase( Locale.US );
				
					String chat_key = Constants.APP_NAME + ": Country: " + cc ;
					
					return( chat_key );
				}
			}catch( Throwable e ){
			}
		}
		
		return( null );
	}
	
	public static String
	getChatKey(
		PeerDescriptor		peer )
	{
		if ( peer != null ){
		
			try{
				InetAddress ia = AddressUtils.getByName( peer.getIP());
			
				String[] info = PeerUtils.getCountryDetails( ia );
			
				if ( info != null ){
								
					String cc = info[0].toUpperCase( Locale.US );
				
					String chat_key = Constants.APP_NAME + ": Country: " + cc ;
					
					return( chat_key );
				}
			}catch( Throwable e ){
			}
		}
		
		return( null );
	}
	
	public static String
	getChatKey(
		Torrent		torrent )
	{
			// no harm in having chats for private torrents but eks is moaning about them so
			// given that they're prolly never used in reality I'm getting rid of them
		
		if ( torrent == null || torrent.isPrivate()){

			return( null );
		}

			// use torrent name here to canonicalize things in case user has renamed download display name
			// also it is more important to get a consistent string rather than something correctly localised

		String	torrent_name = null;

		try{
			TOTorrent to_torrent = PluginCoreUtils.unwrap( torrent );

			torrent_name = to_torrent.getUTF8Name();

			if ( torrent_name == null ){

				torrent_name = new String( to_torrent.getName(), "UTF-8" );
			}
		}catch( Throwable e ){

		}

		if ( torrent_name == null ){

			torrent_name = torrent.getName();
		}

		String key = "Download: " + torrent_name + " {" + ByteFormatter.encodeString( torrent.getHash()) + "}";

		return( key );
	}

	public static String
	getTrackerChatKey(
		String		url )
	{
		try{
			return( getTrackerChatKey( new URL( url )));
			
		}catch( Throwable e ){
			
			return( null );
		}
	}
	
	public static String
	getTrackerChatKey(
		URL		url )
	{
		try{
			return( "Tracker: " + DNSUtils.getInterestingHostSuffix( url.getHost().toLowerCase( Locale.US )));
			
		}catch( Throwable e ){
			
			return( null );
		}
	}
	
	private static class
	ViewWrapper
		implements BuddyPluginViewInterface.View
	{
		private Map<String,Object>						properties;
		private BuddyPluginViewInterface.ViewListener 	listener;

		private BuddyPluginViewInterface.View		delegate;
		
		private boolean			activated;
		private List<String>	drops = new ArrayList<>();
		private boolean 		destroyed;
		
		private
		ViewWrapper(
			BuddyPlugin								bp,
			Map<String,Object>						_properties,
			BuddyPluginViewInterface.ViewListener 	_listener )
		{
			properties	= _properties;
			listener	= _listener;
			
			bp.addSWTUIWaiter(
				new Runnable()
				{
					public void
					run()
					{
						BuddyPluginViewInterface ui = bp.getSWTUI();

						if ( ui != null ){

							setDelegate(ui);
						}
					}
				});
		}
		
		private void
		setDelegate(
			BuddyPluginViewInterface		ui )
		{
			synchronized( this ){
				
				if ( destroyed ){
					
					return;
				}
			
				delegate = ui.buildView( properties, listener );
				
				if ( delegate != null ){
					
					if ( activated ){
						
						delegate.activate();
					}
					
					for ( String drop: drops ){
						
						delegate.handleDrop( drop );
					}
					
					drops.clear();
					
				}else{
					
					Debug.out( "Failed to build view" );
				}
			}
		}
		
		public void
		activate()
		{
			synchronized( this ){
				
				if ( destroyed ){
					
					return;
				}
							
				if ( delegate != null ){
					
					delegate.activate();
					
				}else{
					
					activated = true;
				}
			}
		}

		public void
		handleDrop(
			String		drop )
		{
			synchronized( this ){
				
				if ( destroyed ){
					
					return;
				}
								
				if ( delegate != null ){
					
					delegate.handleDrop( drop );
					
				}else{
					
					drops.add( drop );
				}
			}
		}

		public void
		destroy()
		{
			synchronized( this ){
				
				destroyed = true;
				
				if ( delegate != null ){
					
					delegate.destroy();
				}
			}
		}
	}
}
