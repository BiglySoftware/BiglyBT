/*
 * Created on 15-Dec-2005
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.plugin.extseed;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.TrackerPeerSourceAdapter;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadManagerListener;
import com.biglybt.pif.download.DownloadManagerStats;
import com.biglybt.pif.download.DownloadPeerListener;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.ui.components.UITextField;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.utils.*;
import com.biglybt.plugin.extseed.impl.getright.ExternalSeedReaderFactoryGetRight;
import com.biglybt.plugin.extseed.impl.webseed.ExternalSeedReaderFactoryWebSeed;

public class
ExternalSeedPlugin
	implements Plugin, DownloadManagerListener
{
	private static ExternalSeedReaderFactory[]	factories = {
		new ExternalSeedReaderFactoryGetRight(),
		new ExternalSeedReaderFactoryWebSeed(),
	};

	private PluginInterface			plugin_interface;
	private DownloadManagerStats	dm_stats;

	private UITextField				status_field;
	private LoggerChannel			log;

	private 		Random	random = new Random();

	private Map		download_map	= new HashMap();
	private Monitor	download_mon;

	public static void
	load(
		PluginInterface		plugin_interface )
	{
		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		"External Seed" );
	}

	@Override
	public void
	initialize(
		PluginInterface	_plugin_interface )
	{
		plugin_interface	= _plugin_interface;

		dm_stats = plugin_interface.getDownloadManager().getStats();

		log	= plugin_interface.getLogger().getTimeStampedChannel( "External Seeds" );

		final BasicPluginViewModel	view_model =
			plugin_interface.getUIManager().createBasicPluginViewModel( "Plugin.extseed.name" );


		view_model.getActivity().setVisible( false );
		view_model.getProgress().setVisible( false );

		log.addListener(
				new LoggerChannelListener()
				{
					@Override
					public void
					messageLogged(
						int		type,
						String	content )
					{
						view_model.getLogArea().appendText( content + "\n" );
					}

					@Override
					public void
					messageLogged(
						String		str,
						Throwable	error )
					{
						if ( str.length() > 0 ){
							view_model.getLogArea().appendText( str + "\n" );
						}

						StringWriter sw = new StringWriter();

						PrintWriter	pw = new PrintWriter( sw );

						error.printStackTrace( pw );

						pw.flush();

						view_model.getLogArea().appendText( sw.toString() + "\n" );
					}
				});

		status_field = view_model.getStatus();

		setStatus( "Initialising" );

		download_mon	= plugin_interface.getUtilities().getMonitor();

		Utilities utilities = plugin_interface.getUtilities();

		final DelayedTask dt = plugin_interface.getUtilities().createDelayedTask(new Runnable()
			{
				@Override
				public void
				run()
				{
					AEThread2 t =
						new AEThread2( "ExternalSeedInitialise", true )
						{
							@Override
							public void
							run()
							{
								setStatus( "Running" );

								plugin_interface.getDownloadManager().addListener( ExternalSeedPlugin.this);
							}
						};

					t.setPriority( Thread.MIN_PRIORITY );

					t.start();

				}
			});

		dt.queue();

		UTTimer timer = utilities.createTimer("ExternalPeerScheduler", true);

		timer.addPeriodicEvent(
				5000,
				new UTTimerEventPerformer()
				{
					@Override
					public void
					perform(
						UTTimerEvent		event )
					{
						try{
							Iterator	it = download_map.values().iterator();

							while( it.hasNext()){

								List	peers = randomiseList((List)it.next());

								for (int i=0;i<peers.size();i++){

										// bail out early if the state changed for this peer
										// so one peer at a time gets a chance to activate

									if (((ExternalSeedPeer)peers.get(i)).checkConnection()){

										break;
									}
								}
							}

						}catch( Throwable e ){
							// we do this without holding the monitor as doing so causes potential
							// deadlock between download_mon and the connection's connection_mon

							// so ignore possible errors here that may be caused by concurrent
							// modification to the download_map ans associated lists. We are only
							// reading the data so errors will only be transient
						}
					}
				});

	}

	@Override
	public void
	downloadAdded(
		Download	download )
	{
		downloadAdded( download, false );
	}
	
	public void
	downloadAdded(
		Download	download,
		boolean		changed )
	{
		Torrent	torrent = download.getTorrent();

		if ( torrent == null ){

			return;
		}

		List	peers = new ArrayList();

		for (int i=0;i<factories.length;i++){

			String attributeID = "no-ext-seeds-1-" + factories[i].getClass().getSimpleName();
			
			TorrentAttribute attribute = plugin_interface.getTorrentManager().getPluginAttribute( attributeID );

			if ( !changed ){
				
				boolean noExternalSeeds = download.getBooleanAttribute(attribute);
				
				if ( noExternalSeeds ){
					
					continue;
				}
			}
			
			ExternalSeedReader[]	x = factories[i].getSeedReaders( this, download );

			if ( x.length == 0 ){
				
				download.setBooleanAttribute(attribute, true);
				
			}else{

	  			for (int j=0;j<x.length;j++){
	
	  				ExternalSeedReader	reader = x[j];
	
	  				ExternalSeedPeer	peer = new ExternalSeedPeer( this, download, reader );
	
	  				peers.add( peer );
	  			}
			}
		}

		addPeers( download, peers );
	}

	public void
	downloadChanged(
		Download	download )
	{
		downloadRemoved( download );

		downloadAdded( download, true );
	}

	public List<ExternalSeedPeer>
	addSeed(
		Download	download,
		Map			config )
	{
		Torrent	torrent = download.getTorrent();

		List<ExternalSeedPeer>	peers = new ArrayList<>();

		if ( torrent != null ){

			for (int i=0;i<factories.length;i++){

				ExternalSeedReader[]	x = factories[i].getSeedReaders( this, download, config );

				for (int j=0;j<x.length;j++){

					ExternalSeedReader	reader = x[j];

					ExternalSeedPeer	peer = new ExternalSeedPeer( this, download, reader );

					peers.add( peer );
				}
			}

			addPeers( download, peers );
		}

		return( peers );
	}

	protected void
	addPeers(
		final Download	download,
		List			_peers )
	{
		final List peers = new ArrayList();

		peers.addAll( _peers );

		if ( peers.size() > 0 ){

			boolean	add_listener = false;

			try{
				download_mon.enter();

				List	existing_peers = (List)download_map.get( download );

				if ( existing_peers == null ){

					add_listener	= true;

					existing_peers = new ArrayList();

					download_map.put( download, existing_peers );
				}

				Iterator	it = peers.iterator();

				while( it.hasNext()){

					ExternalSeedPeer	peer = (ExternalSeedPeer)it.next();

					boolean	skip = false;

					for (int j=0;j<existing_peers.size();j++){

						ExternalSeedPeer	existing_peer = (ExternalSeedPeer)existing_peers.get(j);

						if ( existing_peer.sameAs( peer )){

							skip	= true;

							break;
						}
					}

					if ( skip ){

						it.remove();

					}else{

						log( download.getName() + " found seed " + peer.getName());


						existing_peers.add( peer );
					}
				}


				setStatus( "Running: Downloads with external seeds = " + download_map.size());

			}finally{

				download_mon.exit();
			}

			if ( add_listener ){

				download.addPeerListener(
					new DownloadPeerListener()
					{
						@Override
						public void
						peerManagerAdded(
							Download		download,
							PeerManager		peer_manager )
						{
							List	existing_peers = getPeers();

							if ( existing_peers== null ){

								return;
							}

							for (int i=0;i<existing_peers.size();i++){

								ExternalSeedPeer	peer = (ExternalSeedPeer)existing_peers.get(i);

								peer.setManager( peer_manager );
							}
						}

						@Override
						public void
						peerManagerRemoved(
							Download		download,
							PeerManager		peer_manager )
						{
							List	existing_peers = getPeers();

							if ( existing_peers== null ){

								return;
							}

							for (int i=0;i<existing_peers.size();i++){

								ExternalSeedPeer	peer = (ExternalSeedPeer)existing_peers.get(i);

								peer.setManager( null );
							}
						}

						protected List
						getPeers()
						{
							List	existing_peers = null;

							try{
								download_mon.enter();

								List	temp = (List)download_map.get( download );

								if ( temp != null ){

									existing_peers = new ArrayList( temp.size());

									existing_peers.addAll( temp );
								}
							}finally{

								download_mon.exit();
							}

							return( existing_peers );
						}
					});
			}else{

					// fix up newly added peers to current peer manager

				PeerManager	existing_pm = download.getPeerManager();

				if ( existing_pm != null ){

					for (int i=0;i<peers.size();i++){

						ExternalSeedPeer	peer = (ExternalSeedPeer)peers.get(i);

						if ( peer.getManager() == null ){

							peer.setManager( existing_pm );
						}
					}
				}
			}
		}
	}

	protected void
	removePeer(
		ExternalSeedPeer	peer )
	{
		Download	download = peer.getDownload();

		try{
			download_mon.enter();

			List	existing_peers = (List)download_map.get( download );

			if ( existing_peers != null ){

				if ( existing_peers.remove( peer )){

					log( download.getName() + " removed seed " + peer.getName());
				}
			}
		}finally{

			download_mon.exit();
		}
	}

	@Override
	public void
	downloadRemoved(
		Download	download )
	{
		try{
			download_mon.enter();

			download_map.remove( download );

			setStatus( "Running: Downloads with external seeds = " + download_map.size());

		}finally{

			download_mon.exit();
		}
	}

	public ExternalSeedManualPeer[]
	getManualWebSeeds(
		Download	download )
	{
		try{
			download_mon.enter();

			List	peers = (List)download_map.get( download );

			if ( peers == null ){

				return( new ExternalSeedManualPeer[0] );
			}

			ExternalSeedManualPeer[]	result = new ExternalSeedManualPeer[peers.size()];

			for (int i=0;i<peers.size();i++){

				result[i] = new ExternalSeedManualPeer((ExternalSeedPeer)peers.get(i));
			}

			return( result );

		}finally{

			download_mon.exit();
		}
	}

	public ExternalSeedReader[]
	getManualWebSeeds(
		Torrent	torrent )
	{
		List<ExternalSeedReader>		result = new ArrayList<>();

		for (int i=0;i<factories.length;i++){

			ExternalSeedReader[] peers = factories[i].getSeedReaders( this, torrent );

			result.addAll( Arrays.asList( peers ));
		}

		return( result.toArray( new  ExternalSeedReader[result.size()]));
	}

	public TrackerPeerSource
	getTrackerPeerSource(
		final Download		download )
	{
		return(
			new TrackerPeerSourceAdapter()
			{
				private long	fixup_time;

				private ExternalSeedManualPeer[]	peers;
				private boolean						running;

				@Override
				public int
				getType()
				{
					return( TrackerPeerSource.TP_HTTP_SEED );
				}

				@Override
				public int
				getStatus()
				{
					fixup();

					if ( running ){

						return( peers.length==0? TrackerPeerSource.ST_UNAVAILABLE: TrackerPeerSource.ST_AVAILABLE );

					}else{

						return( TrackerPeerSource.ST_STOPPED );
					}
				}

				@Override
				public String
				getName()
				{
					fixup();

					if ( peers.length == 0 ){

						return( "" );
					}

					StringBuilder sb = new StringBuilder();

					for ( ExternalSeedManualPeer peer: peers ){

						if ( sb.length() > 0 ){

							sb.append( ", " );
						}

						String str = peer.getDelegate().getURL().toExternalForm();

						int pos = str.indexOf( '?' );

						if ( pos != -1 ){

							str = str.substring( 0, pos );
						}

						sb.append( str );
					}

					return( sb.toString());
				}

				@Override
				public int
				getSeedCount()
				{
					fixup();

					if ( running ){

						return( peers.length==0?-1:peers.length );

					}else{

						return( -1 );
					}
				}

				protected void
				fixup()
				{
					long	now = SystemTime.getMonotonousTime();

					if ( peers == null || now - fixup_time > 10*1000 ){

						fixup_time = now;

						peers = getManualWebSeeds(download);

						int	state = download.getState();

						running = state == Download.ST_DOWNLOADING || state == Download.ST_SEEDING;
					}
				}
			});
	}

	public TrackerPeerSource
	getTrackerPeerSource(
		final Torrent		torrent )
	{
		return(
			new TrackerPeerSourceAdapter()
			{
				private ExternalSeedReader[]	peers = getManualWebSeeds( torrent );

				@Override
				public int
				getType()
				{
					return( TrackerPeerSource.TP_HTTP_SEED );
				}

				@Override
				public int
				getStatus()
				{
					return( peers.length==0? TrackerPeerSource.ST_UNAVAILABLE: TrackerPeerSource.ST_AVAILABLE );
				}

				@Override
				public String
				getName()
				{
					if ( peers.length == 0 ){

						return( "" );
					}

					StringBuilder sb = new StringBuilder();

					for ( ExternalSeedReader peer: peers ){

						if ( sb.length() > 0 ){

							sb.append( ", " );
						}

						String str = peer.getURL().toExternalForm();

						int pos = str.indexOf( '?' );

						if ( pos != -1 ){

							str = str.substring( 0, pos );
						}

						sb.append( str );
					}

					return( sb.toString());
				}

				@Override
				public int
				getSeedCount()
				{
					return( peers.length==0?-1:peers.length );
				}
			});
	}

	public int
	getGlobalDownloadRateBytesPerSec()
	{
		return( dm_stats.getDataAndProtocolReceiveRate());
	}

	protected void
	setStatus(
		String		str )
	{
		status_field.setText( str );
	}

	public void
	log(
		String		str )
	{
		log.log( str );
	}

	public void
	log(
		String		str,
		Throwable 	e )
	{
		log.log( str, e );
	}

	public PluginInterface
	getPluginInterface()
	{
		return( plugin_interface );
	}

	protected List
	randomiseList(
		List	l )
	{
		if ( l.size() < 2 ){

			return(l);
		}

		List	new_list = new ArrayList();

		for (int i=0;i<l.size();i++){

			new_list.add( random.nextInt(new_list.size()+1), l.get(i));
		}

		return( new_list );
	}
}