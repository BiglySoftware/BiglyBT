/*
 * Created on Aug 6, 2008
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
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.subs.*;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.pif.utils.search.SearchProvider;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;

public class
SubscriptionSchedulerImpl
	implements SubscriptionScheduler, SubscriptionManagerListener
{
	private static final Object			SCHEDULER_NEXT_SCAN_KEY 			= new Object();
	private static final Object			SCHEDULER_FAILED_SCAN_CONSEC_KEY 	= new Object();
	private static final Object			SCHEDULER_FAILED_SCAN_TIME_KEY 		= new Object();

	private static final int			FAIL_INIT_DELAY		= 10*60*1000;
	private static final int			FAIL_MAX_DELAY		= 8*60*60*1000;

	private SubscriptionManagerImpl		manager;

	private Map<Subscription, List<AESemaphore>>	active_subscription_downloaders = new IdentityHashMap<>();

	private Map<String,Long>	rate_limit_map = new HashMap<>();

	private Set<String>	active_result_downloaders		= new HashSet<>();

	private ThreadPool	result_downloader = new ThreadPool( "SubscriptionDownloader", 5, true );

	private boolean		schedulng_permitted;

	private TimerEvent	schedule_event;
	private boolean		schedule_in_progress;
	private long		last_schedule;

	private String		last_sched_str;

	protected
	SubscriptionSchedulerImpl(
		SubscriptionManagerImpl		_manager )
	{
		manager	= _manager;

		manager.addListener( this );

		DelayedTask delayed_task = UtilitiesImpl.addDelayedTask( "Subscriptions Scheduler",
			new Runnable()
			{
				@Override
				public void
				run()
				{
					synchronized( SubscriptionSchedulerImpl.this ){

						schedulng_permitted	= true;
					}

					calculateSchedule();
				}
			});

		delayed_task.queue();
	}

	@Override
	public void
	downloadAsync(
		Subscription 	subs,
		boolean 		is_auto )

		throws SubscriptionException
	{
		download(
			subs,
			is_auto,
			new SubscriptionDownloadListener()
			{
				@Override
				public void
				complete(
					Subscription		subs )
				{
				}

				@Override
				public void
				failed(
					Subscription			subs,
					SubscriptionException	error )
				{
					log( "Async download of " + subs.getName() + " failed", error );
				}
			});
	}

	@Override
	public void
	download(
		final Subscription 					subs,
		final boolean						is_auto,
		final SubscriptionDownloadListener 	listener )
	{
		new AEThread2( "SS:download", true )
		{
			@Override
			public void
			run()
			{
				try{
					download( subs, is_auto );

					listener.complete( subs );

				}catch( SubscriptionException e ){

					listener.failed( subs, e );

				}catch( Throwable e ){

					listener.failed( subs, new SubscriptionException( "Download failed", e ));
				}
			}
		}.start();
	}

	@Override
	public boolean
	download(
		Subscription 	subs,
		boolean			is_auto )

		throws SubscriptionException
	{
		if ( manager.isClosing()){
			
			throw( new SubscriptionException( "Not downloading, application is closing" ));
		}
		
		SubscriptionDownloader 	downloader;
		AESemaphore				sem;

		String rate_limits = manager.getRateLimits().trim();

		synchronized( active_subscription_downloaders ){

			if ( rate_limits.length() > 0 ){

				try{
					Engine engine = subs.getEngine();

					if ( engine instanceof WebEngine ){

						String url_str = ((WebEngine)engine).getSearchUrl( true );

						String host = new URL( url_str ).getHost();

						String[] bits = rate_limits.split( "," );

						for ( String bit: bits ){

							String[] temp = bit.trim().split( "=" );

							if ( temp.length == 2 ){

								String 	lhs = temp[0].trim();

								if ( lhs.equals( host )){

									int mins = Integer.parseInt( temp[1].trim());

									if ( mins > 0 ){

										long	now = SystemTime.getMonotonousTime();

										Long last = rate_limit_map.get( host );

										if ( last != null && now - last < mins*60*1000 ){

											if ( is_auto ){

												return( false );

											}else{

												throw( new SubscriptionException( "Rate limiting prevents download from " + host ));
											}
										}

										rate_limit_map.put( host, now );
									}
								}
							}
						}
					}
				}catch( SubscriptionException e ){

					throw( e );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}

			List<AESemaphore>	waiting = active_subscription_downloaders.get( subs );

			if ( waiting != null ){

				sem = new AESemaphore( "SS:waiter" );

				waiting.add( sem );

				downloader = null;
				
			}else{

				sem = null;
				
				active_subscription_downloaders.put( subs, new ArrayList<>());
				
				downloader = new SubscriptionDownloader(manager, (SubscriptionImpl)subs );
			}
		}

			// either downloader or sem is non-null
		
		try{
			if ( downloader != null ){

				downloader.download();

			}else{

				sem.reserve();
			}

			return( true );

		}finally{

				// only release waiters and fire event if this thread did the actual download
			
			if ( downloader != null ){

				try{	
					((SubscriptionImpl)subs).fireDownloaded();

				}catch( Throwable e ){
					
					Debug.out( e );
				}
				
				synchronized( active_subscription_downloaders ){
	
					List<AESemaphore> waiting = active_subscription_downloaders.remove( subs );
	
					if ( waiting != null ){
	
						for ( AESemaphore waiter: waiting ){
							
							waiter.release();
						}
					}
				}
			}
		}
	}

	@Override
	public void
	download(
		final Subscription			subs,
		final SubscriptionResult	original_result )
	{
		String download_link = original_result.getDownloadLink();

		if ( download_link == null ){

			log( subs.getName() + ": can't download " + original_result.getID() + " as no direct download link available" );

			return;
		}

		final String	key = subs.getID() + ":" + original_result.getID();
		final String	dl	= download_link;

		synchronized( active_result_downloaders ){

			if ( active_result_downloaders.contains( key )){

				return;
			}

			log( subs.getName() + ": queued result for download - " + original_result.getID() + "/" + download_link );

			active_result_downloaders.add( key );

			result_downloader.run(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						boolean	success = false;

						SubscriptionResult result = null;
						
							// need to fix up to the latest history due to the lazy nature of things :(
						try{

							result = subs.getHistory().getResult( original_result.getID());

							if ( result == null ){

								log( subs.getName() + ": result has been deleted - " + original_result.getID());

								success = true;

							}else if ( result.getRead()){

								log( subs.getName() + ": result already marked as read, skipping - " + result.getID());

								success = true;

							}else{

								boolean	retry = true;

								boolean	use_ref			= subs.getHistory().getDownloadWithReferer();

								boolean tried_ref_switch = false;

								while( retry ){

									retry = false;

									try{
										TorrentUtils.setTLSDescription( "Subscription: " + subs.getName());

										URL original_url = new URL(dl);

										PluginProxy plugin_proxy 	= null;

										if ( dl.startsWith( "tor:" )){

											String target_resource = dl.substring( 4 );

											original_url = new URL( target_resource );

											Map<String,Object>	options = new HashMap<>();

											options.put( AEProxyFactory.PO_PEER_NETWORKS, new String[]{ AENetworkClassifier.AT_TOR });

											plugin_proxy =
												AEProxyFactory.getPluginProxy(
													"Subscription result download of '" + target_resource + "'",
													original_url,
													options,
													true );

											if ( plugin_proxy == null ){

												throw( new Exception( "No Tor plugin proxy available for '" + dl + "'" ));
											}
										}

										URL			current_url 	= plugin_proxy==null?original_url:plugin_proxy.getURL();

										Torrent torrent = null;

										try{

											while( true ){

												try{
													ResourceDownloaderFactory rdf = StaticUtilities.getResourceDownloaderFactory();

													ResourceDownloader url_rd = rdf.create( current_url, plugin_proxy==null?null:plugin_proxy.getProxy());

													if ( plugin_proxy != null ){

														url_rd.setProperty( "URL_HOST", plugin_proxy.getURLHostRewrite() + (current_url.getPort()==-1?"":(":" + current_url.getPort())));
													}

													String referer = use_ref?subs.getReferer():null;

													UrlUtils.setBrowserHeaders( url_rd, referer );

													Engine engine = subs.getEngine();

													if ( engine instanceof WebEngine ){

														WebEngine we = (WebEngine)engine;

														if ( we.isNeedsAuth()){

															String cookies = we.getCookies();

															if ( cookies != null && cookies.length() > 0 ){

																url_rd.setProperty( "URL_Cookie", cookies );
															}
														}
													}

													ResourceDownloader mr_rd = rdf.getMetaRefreshDownloader( url_rd );

													InputStream is = mr_rd.download();

													torrent = new TorrentImpl( TOTorrentFactory.deserialiseFromBEncodedInputStream( is ));

													break;

												}catch( Throwable e ){

													if ( plugin_proxy == null ){

														Map<String,Object>	options = new HashMap<>();
														
														options.put( AEProxyFactory.PO_PREFERRED_PROXY_TYPE, "HTTP" );

														plugin_proxy = AEProxyFactory.getPluginProxy( "Subscription result download", original_url, options );

														if ( plugin_proxy != null ){

															current_url = plugin_proxy.getURL();

															continue;
														}
													}

													throw( e );
												}
											}
										}finally{

											if ( plugin_proxy != null ){

												plugin_proxy.setOK( torrent != null);
											}

										}

										byte[] hash = torrent.getHash();

										// PlatformTorrentUtils.setContentTitle(torrent, torr );

										DownloadManager dm = PluginInitializer.getDefaultInterface().getDownloadManager();

										Download	download;

											// if we're assigning a tag/networks then we need to add it stopped in case the tag has any pre-start actions (e.g. set initial save location)
											// this is because the assignments are done in SubscriptionManagerImpl on the download(willbe)added event

										boolean	stop_override = subs.getTagID() >= 0 || subs.getHistory().getDownloadNetworks() != null;


										boolean auto_start = manager.shouldAutoStart( torrent );

										manager.addPrepareTrigger( hash, new Subscription[]{ subs }, new SubscriptionResult[]{ result } );

										try{
											File data_location 	= null;
											File torrent_location	= null;
											
											if ( manager.getAddHashDirs()){
												
												String torrent_name = FileUtil.convertOSSpecificChars( torrent.getName(), false );
												
												String hash_str = ByteFormatter.encodeString( hash ).substring( 0, 8 );
												
												String data_dir = COConfigurationManager.getStringParameter("Default save path");

												if ( data_dir != null && !data_dir.isEmpty()){
													
													data_location = FileUtil.newFile( data_dir, torrent_name + "_" + hash_str );
												}
												
												if ( COConfigurationManager.getBooleanParameter("Save Torrent Files")){

											      	String torrent_dir = COConfigurationManager.getDirectoryParameter("General_sDefaultTorrent_Directory");

											      	if ( torrent_dir != null && !torrent_dir.isEmpty()){

											      		torrent_location = FileUtil.newFile( torrent_dir, torrent_name + "_" + hash_str + ".torrent" );
											      		
											      		try{
													    	torrent.writeToFile( torrent_location );
													    	
											      		}catch( Throwable e ){
											      			
											      			Debug.out( e );
											      			
											      			torrent_location = null;
											      		}
											      	}
												}
											}
										    
										    
											if ( auto_start && !stop_override ){

												download = dm.addDownload( torrent, torrent_location, data_location );

											}else{

												download = dm.addDownloadStopped( torrent, torrent_location, data_location );
											}
										}finally{

											manager.removePrepareTrigger( hash );
										}

										log( subs.getName() + ": added download " + download.getName()+ ": auto-start=" + auto_start );

											// maybe remove this as should be actioned in the trigger?

										manager.prepareDownload(download, new Subscription[]{ subs }, new SubscriptionResult[]{ result });

										subs.addAssociation( hash );

										if ( auto_start && stop_override ){

											download.restart();
										}

										result.setRead( true );

										success = true;

										if ( tried_ref_switch ){

											subs.getHistory().setDownloadWithReferer( use_ref );
										}
									}catch( Throwable e ){

										log( subs.getName() + ": Failed to download result " + dl, e );

										if ( e instanceof TOTorrentException && !tried_ref_switch ){

											use_ref 			= !use_ref;

											tried_ref_switch	= true;

											retry				= true;

											log( subs.getName() + ": Retrying " + (use_ref?"with referer":"without referer" ));
										}
									}finally{

										TorrentUtils.setTLSDescription( null );
									}
								}
							}
						}finally{

							try{
								if ( result != null && !success ){

									if ( dl.startsWith( "azplug:" ) || dl.startsWith( "chat:" )){

											// whatever the outcome these have been handled async

										result.setRead( true );

									}else{

										int rad = manager.getAutoDownloadMarkReadAfterDays();

										if ( rad > 0 ){

											long rad_millis = rad*24*60*60*1000L;

											long	time_found = result.getTimeFound();

											// log( "found=" + new SimpleDateFormat().format(new Date( time_found)) + ", ago=" + ((SystemTime.getCurrentTime()-time_found )) + ", rad=" + rad_millis );

											if ( time_found > 0 && time_found + rad_millis < SystemTime.getCurrentTime()){

												log( subs.getName() + ": result expired, marking as read - " + result.getID());

												result.setRead( true );
											}
										}
									}
								}
							}catch( Throwable e ){

								Debug.out( e );
								
							}finally{

								synchronized( active_result_downloaders ){
	
									active_result_downloaders.remove( key );
								}
	
								calculateSchedule();
							}
						}
					}
				});
		}
	}

	protected void
	calculateSchedule()
	{
		synchronized( this ){

			if ( !schedulng_permitted ){

				return;
			}

			if ( schedule_in_progress ){

				return;
			}
		}
		
		Subscription[]	subs = manager.getSubscriptions( true );

		synchronized( this ){

			if ( !schedulng_permitted ){

				return;
			}

			if ( schedule_in_progress ){

				return;
			}

			long	next_ready_time = Long.MAX_VALUE;

			Subscription	next_ready_subs = null;

			for (int i=0;i<subs.length;i++){

				Subscription sub = subs[i];

				SubscriptionHistory history = sub.getHistory();

				if ( !history.isEnabled()){

					continue;
				}

				long	next_scan = getNextScan( sub );

				sub.setUserData( SCHEDULER_NEXT_SCAN_KEY, new Long( next_scan ));

				if ( next_scan < next_ready_time ){

					next_ready_time = next_scan;

					next_ready_subs = sub;
				}
			}

			long	 old_when = 0;

			if ( schedule_event != null ){

				old_when = schedule_event.getWhen();

				schedule_event.cancel();

				schedule_event = null;
			}

			if ( next_ready_time < Long.MAX_VALUE ){

				long	now = SystemTime.getCurrentTime();

				if ( 	now < last_schedule ||
						now - last_schedule < 30*1000 ){

					if ( next_ready_time - now < 30*1000 ){

						next_ready_time = now + 30*1000;
					}
				}

				if ( next_ready_time < now ){

					next_ready_time = now;
				}

				String sched_str =
						"Calculate : " +
						"old_time=" + (old_when==0?"none":new SimpleDateFormat().format(new Date(old_when))) +
						", new_time=" + new SimpleDateFormat().format(new Date(next_ready_time)) +
						", next_sub=" + next_ready_subs.getName();

				if ( last_sched_str == null || !sched_str.equals( last_sched_str )){

					last_sched_str = sched_str;

					log( sched_str );
				}

				schedule_event = SimpleTimer.addEvent(
					"SS:Scheduler",
					next_ready_time,
					new TimerEventPerformer()
					{
						@Override
						public void
						perform(
							TimerEvent event )
						{
							synchronized( SubscriptionSchedulerImpl.this ){

								if ( schedule_in_progress ){

									return;
								}

								schedule_in_progress = true;

								last_schedule = SystemTime.getCurrentTime();

								schedule_event = null;
							}

							new AEThread2( "SS:Sched", true )
							{
								@Override
								public void
								run()
								{
									try{
										schedule();

									}finally{

										synchronized( SubscriptionSchedulerImpl.this ){

											schedule_in_progress = false;
										}

										calculateSchedule();
									}
								}
							}.start();
						}
					});
			}
		}
	}

	protected void
	schedule()
	{
		Subscription[]	subs = manager.getSubscriptions( true );

		long now = SystemTime.getCurrentTime();

		subs = subs.clone();

		synchronized( this ){

			Arrays.sort(
				subs,
				new Comparator<Subscription>()
				{
					@Override
					public int
					compare(
						Subscription s1,
						Subscription s2)
					{
						Long	l1 = (Long)s1.getUserData( SCHEDULER_NEXT_SCAN_KEY );
						Long	l2 = (Long)s2.getUserData( SCHEDULER_NEXT_SCAN_KEY );

						if ( l1 == l2 ){

							return( 0 );

						}else if ( l1 == null ){

							return( 1 );

						}else if ( l2 == null ){

							return( -1 );

						}else{

							long diff = l1 - l2;

							if ( diff < 0 ){

								return( -1 )
										;
							}else if ( diff > 0 ){

								return( 1 );
								
							}else{

								return( 0 );
							}
						}
					}
				});
		}

		for (int i=0;i<subs.length;i++){

			if ( manager.isClosing()){
				
				return;
			}
			
			Subscription sub = subs[i];

			SubscriptionHistory history = sub.getHistory();

			if ( !history.isEnabled()){

				continue;
			}

			synchronized( this ){

				Long	scan_due = (Long)sub.getUserData( SCHEDULER_NEXT_SCAN_KEY );

				if ( scan_due == null ){

					continue;
				}

				long diff = now - scan_due.longValue();

				if ( diff < -10*1000 ){

					continue;
				}

				sub.setUserData( SCHEDULER_NEXT_SCAN_KEY, null );
			}

			long	last_scan = history.getLastScanTime();

			boolean	download_attempted = true;

			try{
				download_attempted = download( sub, true );

			}catch( Throwable e ){

			}finally{

				if ( download_attempted ){

					long	new_last_scan = history.getLastScanTime();

					if ( new_last_scan == last_scan ){

						scanFailed( sub );

					}else{

						scanSuccess( sub );
					}
				}
			}
		}
	}

	protected long
	getNextScan(
		Subscription		sub )
	{
		SubscriptionHistory	history = sub.getHistory();

		Long fail_count = (Long)sub.getUserData( SCHEDULER_FAILED_SCAN_CONSEC_KEY );

		if ( fail_count != null ){

			long 	fail_time = ((Long)sub.getUserData( SCHEDULER_FAILED_SCAN_TIME_KEY )).longValue();

			long	fails = fail_count.longValue();

			long	backoff = FAIL_INIT_DELAY;

			for (int i=1;i<fails;i++){

				backoff <<= 1;

				if ( backoff > FAIL_MAX_DELAY ){

					backoff = FAIL_MAX_DELAY;

					break;
				}
			}

			return( fail_time + backoff );
		}

		return( history.getNextScanTime() );
	}

	@Override
	public long 
	getNextUpdateTime(
		Subscription subs)
	{
		if ( subs.isSubscriptionTemplate() || subs.isSearchTemplate()){
			
			return( 0 );
		}
		
		Long next = (Long)subs.getUserData( SCHEDULER_NEXT_SCAN_KEY );
		
		if ( next != null ){
			
			return( next );
		}
	
		return( getNextScan( subs ));
	}
	
	protected void
	scanSuccess(
		Subscription		sub )
	{
		sub.setUserData( SCHEDULER_FAILED_SCAN_CONSEC_KEY, null );
	}

	protected void
	scanFailed(
		Subscription		sub )
	{
		sub.setUserData( SCHEDULER_FAILED_SCAN_TIME_KEY, new Long( SystemTime.getCurrentTime()));

		Long fail_count = (Long)sub.getUserData( SCHEDULER_FAILED_SCAN_CONSEC_KEY );

		if ( fail_count == null ){

			fail_count = new Long(1);

		}else{

			fail_count = new Long(fail_count.longValue()+1);
		}

		sub.setUserData( SCHEDULER_FAILED_SCAN_CONSEC_KEY, fail_count );
	}

	protected void
	log(
		String		str )
	{
		manager.log( "Scheduler: " + str );
	}

	protected void
	log(
		String		str,
		Throwable 	e )
	{
		manager.log( "Scheduler: " + str, e );
	}

	@Override
	public void
	subscriptionAdded(
		Subscription		subscription )
	{
		calculateSchedule();
	}

	@Override
	public void
	subscriptionChanged(
		Subscription		subscription,
		int					reason )
	{
		calculateSchedule();
	}

	@Override
	public void
	subscriptionSelected(
		Subscription subscription )
	{
	}

	@Override
	public void
	subscriptionRemoved(
		Subscription		subscription )
	{
		calculateSchedule();
	}

	@Override
	public void
	associationsChanged(
		byte[]				association_hash )
	{
	}

	@Override
	public void
	subscriptionRequested(
		URL					url,
		Map<String,Object>	options )
	{
	}

	public void
	subscriptionRequested(
		SearchProvider sp,
		Map<String, Object> properties)

		throws SubscriptionException
	{
	}
}
