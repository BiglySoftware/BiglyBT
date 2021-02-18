/*
 * Created on Jul 16, 2008
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


package com.biglybt.core.lws;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.*;
import com.biglybt.pif.download.savelocation.SaveLocationChange;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.tag.Tag;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.ddb.DDBaseImpl;
import com.biglybt.pifimpl.local.download.DownloadAnnounceResultImpl;


public class
LWSDownload
	extends LogRelation
	implements Download
{
	private final LightWeightSeed				lws;
	private final TRTrackerAnnouncer			announcer;

	final DownloadAnnounceResultImpl	announce_result;

	private final Map	user_data			= new HashMap();
	private final Map	torrent_attributes 	= new HashMap();

	private final DownloadScrapeResult	scrape_result =
		new DownloadScrapeResult()
		{
			@Override
			public Download
			getDownload()
			{
				return( LWSDownload.this );
			}

			@Override
			public int
			getResponseType()
			{
				return( announce_result.getResponseType() == DownloadAnnounceResult.RT_SUCCESS?RT_SUCCESS:RT_ERROR );
			}

			@Override
			public int
			getSeedCount()
			{
				return( announce_result.getSeedCount());
			}

			@Override
			public int
			getNonSeedCount()
			{
				int	seeds 		= getSeedCount();
				int	reported	= announce_result.getReportedPeerCount();

				int	min_peers = reported - seeds;

				int	peers = announce_result.getNonSeedCount();

				if ( peers < min_peers ){

					peers = min_peers;
				}

				return( peers );
			}

			@Override
			public long
			getScrapeStartTime()
			{
				return( 0 );
			}

			@Override
			public void
			setNextScrapeStartTime(
				long 	nextScrapeStartTime )
			{
			}

			@Override
			public long
			getNextScrapeStartTime()
			{
				return( 0 );
			}

			@Override
			public String
			getStatus()
			{
				if ( getResponseType() == RT_SUCCESS ){

					return( "OK" );

				}else{

					return( announce_result.getError());
				}
			}

			@Override
			public URL
			getURL()
			{
				return( announce_result.getURL());
			}
		};


	protected
	LWSDownload(
		LightWeightSeed			_lws,
		TRTrackerAnnouncer		_announcer )
	{
		lws				= _lws;
		announcer		= _announcer;

		announce_result = new DownloadAnnounceResultImpl( this, announcer.getLastResponse());
	}

	public LightWeightSeed
	getLWS()
	{
		return( lws );
	}

	@Override
	public int
	getState()
	{
		return( Download.ST_SEEDING );
	}

	@Override
	public int
	getSubState()
	{
		return( Download.ST_SEEDING );
	}

	@Override
	public String
	getErrorStateDetails()
	{
		return( "" );
	}

	@Override
	public void
	setFlag(
		long		flag,
		boolean		value )
	{
		notSupported();
	}

	@Override
	public boolean
	getFlag(
		long		flag )
	{
		return( flag ==  Download.FLAG_LIGHT_WEIGHT );
	}

	@Override
	public long
	getFlags()
	{
		return( Download.FLAG_LIGHT_WEIGHT );
	}

	@Override
	public SaveLocationChange
	calculateDefaultDownloadLocation()
	{
		return null;
	}

	@Override
	public Torrent
	getTorrent()
	{
		return( lws.getTorrent());
	}

	@Override
	public void
	initialize()

		throws DownloadException
	{
	}

	@Override
	public void
	start()

		throws DownloadException
	{
	}

	@Override
	public void
	startDownload(
		boolean force)
	{
	}

	@Override
	public void
	stopDownload()
	{
	}

	@Override
	public void
	stop()

		throws DownloadException
	{
	}

	@Override
	public void setStopReason(String reason) {
		setUserData( UD_KEY_STOP_REASON, reason );
	}

	@Override
	public String getStopReason() {
		return((String)getUserData( UD_KEY_STOP_REASON ));
	}

	@Override
	public void
	stopAndQueue()

		throws DownloadException
	{
	}

	@Override
	public void
	restart()

		throws DownloadException
	{
	}

	@Override
	public void
	pause()
	{
	}

	@Override
	public void
	resume()
	{
	}

	@Override
	public void
	recheckData()

		throws DownloadException
	{
	}

	@Override
	public boolean
	isStartStopLocked()
	{
		return( false );
	}


	@Override
	public boolean
	isForceStart()
	{
		return( true );
	}

	@Override
	public void
	setForceStart(
		boolean forceStart )
	{
	}

	@Override
	public boolean
	isPaused()
	{
		return( false );
	}

	@Override
	public String
	getName()
	{
		return( lws.getName());
	}

	@Override
	public String
	getTorrentFileName()
	{
		return( getName());
	}

	@Override
	public String
	getAttribute(
		TorrentAttribute		attribute )
	{
		synchronized( torrent_attributes ){

			return((String)torrent_attributes.get( attribute ));
		}
	}

	@Override
	public void
	setAttribute(
		TorrentAttribute		attribute,
		String					value )
	{
		synchronized( torrent_attributes ){

			torrent_attributes.put( attribute, value );
		}
	}

	@Override
	public String[]
	getListAttribute(
		TorrentAttribute		attribute )
	{
		TorrentManager tm = PluginInitializer.getDefaultInterface().getTorrentManager();

		if ( attribute == tm.getAttribute( TorrentAttribute.TA_NETWORKS )){

			return( new String[]{ lws.getNetwork() });

		}else if ( attribute == tm.getAttribute( TorrentAttribute.TA_PEER_SOURCES )){

			return( new String[]{ PEPeerSource.PS_DHT });
		}

		return( null );
	}

	@Override
	public void
	setListAttribute(
		TorrentAttribute 	attribute,
		String[] 			value)
	{
		notSupported();
	}

	@Override
	public void
	setMapAttribute(
		TorrentAttribute		attribute,
		Map						value )
	{
		notSupported();
	}

	@Override
	public Map
	getMapAttribute(
		TorrentAttribute		attribute )
	{
		return( null );
	}

	@Override
	public void setIntAttribute(TorrentAttribute name, int value){notSupported();}
	@Override
	public int getIntAttribute(TorrentAttribute name){ return( 0 ); }
	@Override
	public void setLongAttribute(TorrentAttribute name, long value){notSupported();}
	@Override
	public long getLongAttribute(TorrentAttribute name){ return( 0 ); }
	@Override
	public void setBooleanAttribute(TorrentAttribute name, boolean value){notSupported();}
	@Override
	public boolean getBooleanAttribute(TorrentAttribute name){ return( false ); }
	@Override
	public boolean hasAttribute(TorrentAttribute name){ return( false );}

	@Override
	public void
	addAttributeListener(
		DownloadAttributeListener l,
		TorrentAttribute attr,
		int event_type)
	{
	}

	@Override
	public void
	removeAttributeListener(
		DownloadAttributeListener l,
		TorrentAttribute attr,
		int event_type)
	{
	}

	@Override
	public String
	getCategoryName()
	{
		return( null );
	}

	@Override
	public void
	setCategory(
		String sName)
	{
		notSupported();
	}

	@Override
	public List<Tag> getTags() {
		return( Collections.emptyList());
	}

	@Override
	public void
	remove()

		throws DownloadException, DownloadRemovalVetoException
	{
		throw( new DownloadRemovalVetoException( "no way" ));
	}

	@Override
	public void
	remove(
		boolean	delete_torrent,
		boolean	delete_data )

		throws DownloadException, DownloadRemovalVetoException
	{
		throw( new DownloadRemovalVetoException( "no way" ));
	}

	@Override
	public void stopAndRemove(boolean delete_torrent, boolean delete_data)
			throws DownloadException, DownloadRemovalVetoException {
		throw (new DownloadRemovalVetoException("no way"));
	}

	@Override
	public boolean
	isRemoved()
	{
		return false;
	}

	@Override
	public int
	getPosition()
	{
		return( 0 );
	}

	@Override
	public long
	getCreationTime()
	{
		return( 0 );
	}

	@Override
	public void
	setPosition(
		int newPosition)
	{
		notSupported();
	}

	@Override
	public void
	moveUp()
	{
		notSupported();
	}

	@Override
	public void
	moveDown()
	{
		notSupported();
	}

	@Override
	public void
	moveTo(
		int		position )
	{
		notSupported();
	}

	@Override
	public boolean
	canBeRemoved()

		throws DownloadRemovalVetoException
	{
		throw( new DownloadRemovalVetoException( "no way" ));
	}

	@Override
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
		announcer.setAnnounceResult( result );
	}

	@Override
	public void
	setScrapeResult(
		DownloadScrapeResult	result )
	{
	}

	@Override
	public DownloadAnnounceResult
	getLastAnnounceResult()
	{
		announce_result.setContent(  announcer.getLastResponse());

		return( announce_result );
	}

	@Override
	public DownloadScrapeResult
	getLastScrapeResult()
	{
		announce_result.setContent(  announcer.getLastResponse());

		return( scrape_result );
	}

	@Override
	public DownloadScrapeResult
	getAggregatedScrapeResult( boolean cache )
	{
		return( getLastScrapeResult());
	}

	@Override
	public DownloadActivationEvent
	getActivationState()
	{
		return( null );
	}

	@Override
	public DownloadStats
	getStats()
	{
		return( null );
	}

    @Override
    public boolean
    isPersistent()
    {
    	return( false );
    }

  	@Override
	  public void
	setMaximumDownloadKBPerSecond(
		int		kb )
  	{
  		notSupported();
  	}

  	@Override
	  public int
	getMaximumDownloadKBPerSecond()
  	{
  		return( 0 );
  	}

	@Override
	public void
	addRateLimiter(
		RateLimiter		limiter,
		boolean			is_upload )
	{
		notSupported();
	}

	@Override
	public void
	removeRateLimiter(
		RateLimiter		limiter,
		boolean			is_upload )
	{
		notSupported();
	}

    @Override
    public int
    getUploadRateLimitBytesPerSecond()
    {
    	return( 0 );
    }

    @Override
    public void
    setUploadRateLimitBytesPerSecond(
    	int max_rate_bps )
    {
    	notSupported();
    }

    @Override
    public int
    getDownloadRateLimitBytesPerSecond()
    {
    	return 0;
    }

    @Override
    public void
    setDownloadRateLimitBytesPerSecond(
    	int max_rate_bps )
    {
    	notSupported();
    }

	@Override
	public boolean
	isComplete()
	{
		return( true );
	}

	@Override
	public boolean
	isComplete(
		boolean bIncludeDND)
	{
		return( true );
	}

	@Override
	public boolean
 	isChecking()
	{
		return( false );
	}

	@Override
	public boolean
 	isMoving()
 	{
		return( false );
 	}

  	@Override
	  public String
	getSavePath()
  	{
  		return( "" );
  	}

  	@Override
	  public void
  	moveDataFiles(
  		File	new_parent_dir )

  		throws DownloadException
  	{
  		notSupported();
  	}

  	@Override
	  public boolean
  	canMoveDataFiles()
  	{
  		return false;
  	}

  	@Override
	  public void
  	moveTorrentFile(
  		File	new_parent_dir )

  		throws DownloadException
	{
  		notSupported();
  	}

  	@Override
	  public void
  	renameDownload(
  		String name )

  		throws DownloadException
  	{
  		notSupported();
  	}

  	@Override
	  public com.biglybt.pif.peers.PeerManager
	getPeerManager()
  	{
  		return( null );
  	}

	@Override
	public com.biglybt.pif.disk.DiskManager
	getDiskManager()
	{
		return( null );
	}

	@Override
	public DiskManagerFileInfo[]
	getDiskManagerFileInfo()
	{
		return( null );
	}

	@Override
	public DiskManagerFileInfo
	getDiskManagerFileInfo(int i)
	{
		return( null );
	}

	@Override
	public int getDiskManagerFileCount()
	{
		return 0;
	}


  	@Override
	  public void
	requestTrackerAnnounce()
  	{
  	}

 	@Override
  public void
	requestTrackerAnnounce(
		boolean		immediate )
 	{
 	}

	@Override
	public void
	requestTrackerScrape(
		boolean		immediate )
	{
	}

	@Override
	public void
	addListener(
		DownloadListener	l )
	{
	}

	@Override
	public void
	removeListener(
		DownloadListener	l )
	{
	}

	@Override
	public void
	addCompletionListener(
		DownloadCompletionListener l )
	{
		notSupported();
	}

	@Override
	public void
	removeCompletionListener(
		DownloadCompletionListener l )
	{
		notSupported();
	}

	@Override
	public void
	addTrackerListener(
		DownloadTrackerListener	l )
	{
	}

	@Override
	public void
	addTrackerListener(
		DownloadTrackerListener l,
		boolean immediateTrigger)
	{
	}

	@Override
	public void
	removeTrackerListener(
		DownloadTrackerListener	l )
	{
	}

	@Override
	public void
	addDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l )
	{
		notSupported();
	}

	@Override
	public void
	removeDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l )
	{
		notSupported();
	}

	@Override
	public void
	addActivationListener(
		DownloadActivationListener		l )
	{
		notSupported();
	}

	@Override
	public void
	removeActivationListener(
		DownloadActivationListener		l )
	{
		notSupported();
	}

	@Override
	public void
	addPeerListener(
		DownloadPeerListener	l )
	{
		notSupported();
	}

	@Override
	public void
	removePeerListener(
		DownloadPeerListener	l )
	{
		notSupported();
	}


	@Override
	public SeedingRank
	getSeedingRank()
	{
		return( null );
	}

	@Override
	public void
	setSeedingRank(
		SeedingRank rank)
	{
		notSupported();
	}

	@Override
	public byte[]
	getDownloadPeerId()
	{
		return( null );
	}

	@Override
	public boolean
	isMessagingEnabled()
	{
		return( true );
	}

	@Override
	public void
	setMessagingEnabled(
		boolean enabled )
	{
	}

	@Override
	public void
	moveDataFiles(
		File 	new_parent_dir,
		String 	new_name )

		throws DownloadException
	{
		notSupported();
	}

	@Override
	public Object
	getUserData(
		Object key )
	{
		synchronized( user_data ){

			return( user_data.get( key ));
		}
	}

	@Override
	public void
	setUserData(
		Object key,
		Object data )
	{
		synchronized( user_data ){

			user_data.put( key, data );
		}
	}

	@Override
	public void
	changeLocation(
		SaveLocationChange slc )

		throws DownloadException
	{
		notSupported();
	}

	@Override
	public boolean
	isStub()
	{
		return( false );
	}

	@Override
	public boolean
	canStubbify()
	{
		return( false );
	}

	@Override
	public DownloadStub
	stubbify()

		throws DownloadException, DownloadRemovalVetoException
	{
		throw( new DownloadException( "Not Supported" ));
	}

	@Override
	public Download
	destubbify()

		throws DownloadException
	{
		throw( new DownloadException( "Not Supported" ));
	}

	@Override
	public List<DistributedDatabase>
	getDistributedDatabases()
	{
		return( DDBaseImpl.getDDBs( this ));
	}

	@Override
	public byte[]
	getTorrentHash()
	{
		return( lws.getTorrent().getHash());
	}

	@Override
	public long
	getTorrentSize()
	{
		return( lws.getTorrent().getSize());
	}

	@Override
	public DownloadStubFile[]
	getStubFiles()
	{
		notSupported();

		return( null );
	}

	protected void
	notSupported()
	{
		Debug.out( "Not Supported" );
	}

	// @see com.biglybt.pif.download.Download#getPrimaryFile()
	@Override
	public DiskManagerFileInfo getPrimaryFile() {
		return null;
	}
	
	@Override
	public String
	getRelationText()
	{
		return "Internal: '" + new String(getName()) + "'";
	}

	@Override
	public Object[]
	getQueryableInterfaces()
	{
		return new Object[] { lws };
	}
}
