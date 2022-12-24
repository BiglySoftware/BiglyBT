/*
 * TorrentDownloaderFactory.java
 *
 * Created on 2. November 2003, 03:52
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

package com.biglybt.core.torrentdownloader;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.torrentdownloader.impl.TorrentDownloaderImpl;
import com.biglybt.core.torrentdownloader.impl.TorrentDownloaderManager;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.core.util.UrlUtils;

/**
 *
 * @author  Tobias Minich
 */
public class TorrentDownloaderFactory {
  /**
   * creates and initializes a TorrentDownloader object with the specified parameters.
   * NOTE: this does not actually start the TorrentDownloader object
   * @param callback object to notify about torrent download status
   * @param url url of torrent file to download
   * @param referrer url of referrer to set as HTTP_REFERER header when requesting torrent
   * @param fileordir path to a file or directory that the actual
   *        torrent file should be saved to. if a default save directory is not specified, this will be used instead.
   * 		even if a default save directory is specified, if this parameter path refers to a file, the filename will
   * 		be used when saving the torrent
   * @return
   */
  public static TorrentDownloader
  create(
  		TorrentDownloaderCallBackInterface 	callback,
		String 								url,
		String								referrer,
		Map									request_properties,
		String 								fileordir)
  {
	  return( new TorrentDownloadRetrier( callback, url, referrer, request_properties, fileordir ));
  }

  public static void initManager(GlobalManager gm, boolean autostart ) {
    TorrentDownloaderManager.getInstance().init(gm,  autostart);
  }

  public static TorrentDownloader downloadManaged(String url, String fileordir) {
    return TorrentDownloaderManager.getInstance().download(url, fileordir);
  }

  public static TorrentDownloader downloadManaged(String url) {
    return TorrentDownloaderManager.getInstance().download(url);
  }
  
  public static TorrentDownloader downloadToLocationManaged(String url, String save_path) {
	    return TorrentDownloaderManager.getInstance().downloadToLocation(url,save_path );
	  }

  	private static class
  	TorrentDownloadRetrier
  		implements TorrentDownloader
  	{
		final private String 								url;
		final private String								referrer;
		final private Map									request_properties;
		final private String 								fileordir;

		private volatile TorrentDownloaderImpl	delegate;

		private volatile boolean	cancelled;

		private volatile boolean	sdp_set;
		private volatile String		sdp_path;
		private volatile String		sdp_file;

		private volatile boolean	dfoc_set;
		private volatile boolean	dfoc;
		private volatile boolean	irc_set;
		private volatile boolean	irc;

		private volatile String		original_error;

  		private
  		TorrentDownloadRetrier(
  			final TorrentDownloaderCallBackInterface 	_callback,
  			String 										_url,
  			String										_referrer,
  			Map											_request_properties,
  			String 										_fileordir )
  		{
  			url					= _url;
  			referrer			= _referrer;
  			request_properties	= _request_properties;
  			fileordir			= _fileordir;

  			TorrentDownloaderCallBackInterface callback			=
  				new TorrentDownloaderCallBackInterface()
  				{
  					private final TorrentDownloaderCallBackInterface	original_callback = _callback;

  					private boolean no_retry = original_callback == null;

  					private boolean	init_reported 		= false;
  					private boolean	start_reported		= false;
  					private boolean	finish_reported		= false;

  					private boolean			proxy_tried = false;
  					private PluginProxy		plugin_proxy;

  					@Override
					  public void
  					TorrentDownloaderEvent(
  						int 				state,
  						TorrentDownloader 	_delegate )
  					{
  						if ( _delegate != delegate ){

  							return;
  						}

  						if ( state == STATE_ERROR ){

  							if ( original_error == null ){

  								original_error = delegate.getError();
  							}
  						}

  						if (	plugin_proxy != null &&
  								(	state == STATE_FINISHED ||
  									state == STATE_DUPLICATE ||
  									state == STATE_CANCELLED ||
  									state == STATE_ERROR )){

  							plugin_proxy.setOK( state != STATE_ERROR );

  							plugin_proxy = null;
  						}


  						synchronized( this ){

	  						if ( state == STATE_INIT ){

	  							if ( init_reported ){

	  								return;
	  							}

	  							init_reported = true;
	  						}

	 						if ( state == STATE_START ){

	  							if ( start_reported ){

	  								return;
	  							}

	  							start_reported = true;
	  						}

	 						if ( state == STATE_FINISHED ){

	  							if ( finish_reported ){

	  								return;
	  							}

	  							finish_reported = true;
	  						}
  						}

  						if ( cancelled ){

  							no_retry = true;
  						}

  						if ( no_retry ){

  							if ( original_callback != null ){

  								original_callback.TorrentDownloaderEvent( state, TorrentDownloadRetrier.this );
  							}

  							return;
  						}

  						if ( 	state == STATE_FINISHED ||
  								state == STATE_DUPLICATE ||
  								state == STATE_CANCELLED ){

  							if ( state == STATE_FINISHED  && proxy_tried ){

  								TorrentUtils.setObtainedFrom( delegate.getFile(), url );
  							}

							if ( original_callback != null ){

  								original_callback.TorrentDownloaderEvent( state, TorrentDownloadRetrier.this );
  							}

  							no_retry = true;

  							return;
  						}

  						if ( state == STATE_ERROR ){

 							String lc_url = url.toLowerCase().trim();

  							if ( !proxy_tried ){

  								proxy_tried = true;

  								boolean	tor_hack = lc_url.startsWith( "tor:" );
  								boolean	i2p_hack = lc_url.startsWith( "i2p:" );

  	  							if ( lc_url.startsWith( "http" ) || tor_hack || i2p_hack ){

  	  								try{
  	  									URL original_url;

  	  									if ( tor_hack || i2p_hack ){

  	  										original_url = new URL( url.substring( 4 ));

  	  										Map<String,Object>	options = new HashMap<>();

  	  										options.put( AEProxyFactory.PO_PEER_NETWORKS, new String[]{ tor_hack?AENetworkClassifier.AT_TOR:AENetworkClassifier.AT_I2P });

  	  										if ( i2p_hack ){
  	  											
  												options.put( AEProxyFactory.PO_PREFERRED_PROXY_TYPE, "HTTP" );
  												options.put( AEProxyFactory.PO_FORCE_PROXY, true );
  	  										}
  	  										
		  	  								plugin_proxy =
		  	  									AEProxyFactory.getPluginProxy(
		  	  										"torrent download",
		  	  										original_url,
		  	  										options,
		  	  										true );
		  	  							}else{

		  	  								original_url = new URL( url );
		  	  							}

  	  									plugin_proxy = AEProxyFactory.getPluginProxy( "torrent download", original_url );

  	  									if ( plugin_proxy != null ){

	  	  									delegate = new TorrentDownloaderImpl();

	  	  									if ( sdp_set ){

	  	  										delegate.setDownloadPath( sdp_path, sdp_file );
	  	  									}

		  	  	  				 			if ( dfoc_set ){

		  	  	  				 				delegate.setDeleteFileOnCancel( dfoc );
		  	  	  				 			}

		  	  	  				 			if ( irc_set ){

		  	  	  				 				delegate.setIgnoreReponseCode( irc );
		  	  	  				 			}

		  	  	  				 			Map props = new HashMap();

		  	  	  				 			if ( request_properties != null ){

		  	  	  				 				props.putAll( request_properties );
		  	  	  				 			}

		  	  	  				 			props.put( "HOST", plugin_proxy.getURLHostRewrite() + (original_url.getPort()==-1?"":(":"+original_url.getPort())));

		  	  	  				  			delegate.init( this, plugin_proxy.getURL().toExternalForm(), plugin_proxy.getProxy(), referrer==null?original_url.toExternalForm():referrer, props, fileordir );

		  	  	  							delegate.start();

		  	  	  							return;
  	  									}
  	  								}catch( Throwable e ){

  	  								}
  	  							}
  							}

  							String	retry_url = null;

  							if ( lc_url.startsWith( "http" )){

  								retry_url = UrlUtils.parseTextForURL( url.substring( 5 ), true );
  							}

  							if ( retry_url != null ){

	  				 			delegate = new TorrentDownloaderImpl();

	  				 			if ( sdp_set ){

	  				 				delegate.setDownloadPath( sdp_path, sdp_file );
	  				 			}

	  				 			if ( dfoc_set ){

	  				 				delegate.setDeleteFileOnCancel( dfoc );
	  				 			}

	  				 			if ( irc_set ){

	  				 				delegate.setIgnoreReponseCode( irc );
	  				 			}

	  				  			delegate.init( this, retry_url, null, referrer, request_properties, fileordir );

	  							no_retry	= true;

	  							delegate.start();

	  							return;

  							}else{

	  							no_retry	= true;
  							}
  						}

						if ( original_callback != null ){

							original_callback.TorrentDownloaderEvent( state, TorrentDownloadRetrier.this );
						}
  					}

  				};

  			delegate = new TorrentDownloaderImpl();

  			delegate.init( callback, url, null, referrer, request_properties, fileordir );
  		}

  		@Override
		  public void
  		start()
  		{
  			delegate.start();
  		}

  		@Override
		  public void
  		cancel()
  		{
  			cancelled = true;

  			delegate.cancel();
  		}

  		@Override
		  public void
  		setDownloadPath(
  			String path,
  			String file)
  		{
  			sdp_set			= true;
 			sdp_path		= path;
  			sdp_file		= file;

  			delegate.setDownloadPath(path, file);
   		}

  		@Override
		  public int
  		getDownloadState()
  		{
  			return( delegate.getDownloadState());
  		}

  		@Override
		  public File
  		getFile()
  		{
  			return( delegate.getFile());
  		}

  		@Override
		  public int
  		getPercentDone()
  		{
  			return( delegate.getPercentDone());
  		}

  		@Override
		  public int
  		getTotalRead()
  		{
  			return( delegate.getTotalRead());
  		}

  		@Override
		  public String
  		getError()
  		{
  			if ( original_error != null ){

  				return( original_error );
  			}

  			return( delegate.getError());
  		}

  		@Override
		  public String
  		getStatus()
  		{
  			return( delegate.getStatus());
  		}

  		@Override
		  public String
  		getURL()
  		{
  			return( delegate.getURL());
  		}

  		@Override
		  public int
  		getLastReadCount()
  		{
  			return( delegate.getLastReadCount());
  		}

  		@Override
		  public byte[]
  		getLastReadBytes()
  		{
  			return( delegate.getLastReadBytes());
  		}

  		@Override
		  public boolean
  		getDeleteFileOnCancel()
  		{
  			return( delegate.getDeleteFileOnCancel());
  		}

  		@Override
		  public void
  		setDeleteFileOnCancel(
  			boolean deleteFileOnCancel )
  		{
  			dfoc_set	= true;
  			dfoc		= deleteFileOnCancel;

  			delegate.setDeleteFileOnCancel( deleteFileOnCancel );
  		}

  		@Override
		  public boolean
  		isIgnoreReponseCode()
  		{
  			return( delegate.isIgnoreReponseCode());
  		}

  		@Override
		  public void
  		setIgnoreReponseCode(
  			boolean ignoreReponseCode)
  		{
  			irc_set	= true;
  			irc		= ignoreReponseCode;

  			delegate.setIgnoreReponseCode( ignoreReponseCode );
  		}
  	}
}
