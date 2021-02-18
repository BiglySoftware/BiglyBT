/*
 * File    : PRDownload.java
 * Created : 28-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.remote.download;

/**
 * @author parg
 *
 */

import java.io.File;
import java.util.List;
import java.util.Map;

import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.disk.DiskManager;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.*;
import com.biglybt.pif.download.savelocation.SaveLocationChange;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.tag.Tag;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.remote.*;
import com.biglybt.pifimpl.remote.disk.RPDiskManagerFileInfo;
import com.biglybt.pifimpl.remote.torrent.RPTorrent;


public class
RPDownload
	extends		RPObject
	implements 	Download
{
	protected transient Download		delegate;

		// don't change these field names as they are visible on XML serialisation

	public RPTorrent				torrent;
	public RPDownloadStats			stats;
	public RPDownloadAnnounceResult	announce_result;
	public RPDownloadScrapeResult	scrape_result;

	public int						position;
	public boolean					force_start;

	public static RPDownload
	create(
		Download		_delegate )
	{
		RPDownload	res =(RPDownload)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPDownload( _delegate );
		}

		return( res );
	}

	protected
	RPDownload(
		Download		_delegate )
	{
		super( _delegate );

			// torrent can be null if broken

		if ( delegate.getTorrent() != null ){

			torrent = (RPTorrent)_lookupLocal( delegate.getTorrent());

			if ( torrent == null ){

				torrent = RPTorrent.create( delegate.getTorrent());
			}
		}

		stats = (RPDownloadStats)_lookupLocal( delegate.getStats());

		if ( stats == null ){

			stats = RPDownloadStats.create( delegate.getStats());
		}

		announce_result = (RPDownloadAnnounceResult)_lookupLocal( delegate.getLastAnnounceResult());

		if ( announce_result == null ){

			announce_result = RPDownloadAnnounceResult.create( delegate.getLastAnnounceResult());
		}

		scrape_result = (RPDownloadScrapeResult)_lookupLocal( delegate.getLastScrapeResult());

		if ( scrape_result == null ){

			scrape_result = RPDownloadScrapeResult.create( delegate.getLastScrapeResult());
		}
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (Download)_delegate;

		position	= delegate.getPosition();
		force_start	= delegate.isForceStart();
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		Object res = _fixupLocal();

		if ( torrent != null ){

			torrent._setLocal();
		}

		stats._setLocal();

		announce_result._setLocal();

		scrape_result._setLocal();

		return( res );
	}

	@Override
	public void
	_setRemote(
		RPRequestDispatcher		dispatcher )
	{
		super._setRemote( dispatcher );

		if ( torrent != null ){

			torrent._setRemote( dispatcher );
		}

		stats._setRemote( dispatcher );

		announce_result._setRemote( dispatcher );

		scrape_result._setRemote( dispatcher );
	}

	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String	method = request.getMethod();

		if ( method.equals( "initialize")){

			try{
				delegate.initialize();

			}catch( DownloadException e ){

				return( new RPReply(e));
			}

			return( null );

		}else if ( method.equals( "start")){

			try{
				delegate.start();

			}catch( DownloadException e ){

				return( new RPReply(e));
			}

			return( null );

		}else if ( method.equals( "restart")){

			try{
				delegate.restart();

			}catch( DownloadException e ){

				return( new RPReply(e));
			}

			return( null );

		}else if ( method.equals( "stop")){

			try{
				delegate.stop();

			}catch( DownloadException e ){

				return( new RPReply(e));
			}

			return( null );

		}else if ( method.equals( "remove")){

			try{
				delegate.remove();

			}catch( Throwable e ){

				return( new RPReply(e));
			}

			return( null );

		}else if ( method.equals( "stopAndRemove")){

			try{
				Object[] params = request.getParams();
				delegate.stopAndRemove((Boolean) params[0], (Boolean) params[1]);

			}catch( Throwable e ){

				return( new RPReply(e));
			}

			return( null );

		}else if ( method.equals( "setForceStart[boolean]")){

			boolean	b = ((Boolean)request.getParams()[0]).booleanValue();

			delegate.setForceStart( b );

			return( null );

		}else if ( method.equals( "setPosition[int]")){

			int	p = ((Integer)request.getParams()[0]).intValue();

			delegate.setPosition( p );

			return( null );

		}else if ( method.equals( "moveUp")){

			delegate.moveUp();

			return( null );

		}else if ( method.equals( "moveDown")){

			delegate.moveDown();

			return( null );

		}else if ( method.equals( "moveTo[int]")){

			int	p = ((Integer)request.getParams()[0]).intValue();

			delegate.setPosition( p );

			return( null );

		}else if ( method.equals( "requestTrackerAnnounce")){

			delegate.requestTrackerAnnounce();

			return( null );

		}else if ( method.equals( "getDiskManagerFileInfo")){

			DiskManagerFileInfo[] info = delegate.getDiskManagerFileInfo();

			RPDiskManagerFileInfo[] rp_info = new RPDiskManagerFileInfo[info.length];

			for (int i=0;i<rp_info.length;i++){

				rp_info[i] = RPDiskManagerFileInfo.create( info[i] );
			}

			return( new RPReply( rp_info ));
		}

		throw( new RPException( "Unknown method: " + method ));
	}

		// ***************************************************

	@Override
	public int
	getState()
	{
		notSupported();

		return(0);
	}

	@Override
	public int
	getSubState()
	{
		notSupported();

		return(0);
	}

	@Override
	public String
	getErrorStateDetails()
	{
		notSupported();

		return( null );
	}

	@Override
	public boolean
	getFlag(
		long	flag )
	{
		notSupported();

		return( false );
	}

	@Override
	public long
	getFlags()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public Torrent
	getTorrent()
	{
		return( torrent );
	}


  @Override
  public byte[] getDownloadPeerId() {
    return delegate.getDownloadPeerId();
  }


  @Override
  public boolean isMessagingEnabled() {  return delegate.isMessagingEnabled();  }

  @Override
  public void setMessagingEnabled(boolean enabled ) {
    delegate.setMessagingEnabled( enabled );
  }



	@Override
	public void
	initialize()

		throws DownloadException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "initialize", null )).getResponse();

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public void
	start()

		throws DownloadException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "start", null )).getResponse();

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public void
	stop()

		throws DownloadException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "stop", null )).getResponse();

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
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
	restart()

		throws DownloadException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "restart", null )).getResponse();

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public boolean
	isStartStopLocked()
	{
		notSupported();

		return( false );
	}

	@Override
	public boolean
	isPaused()
	{
		notSupported();

		return( false );
	}

	@Override
	public void
	pause()
	{
		notSupported();
	}

	@Override
	public void
	resume()
	{
		notSupported();
	}

	@Override
	public void
	remove()

		throws DownloadException, DownloadRemovalVetoException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "remove", null )).getResponse();

		}catch( RPException e ){

			Throwable cause = e.getCause();

			if ( cause instanceof DownloadException ){

				throw((DownloadException)cause);
			}

			if ( cause instanceof DownloadRemovalVetoException ){

				throw((DownloadRemovalVetoException)cause);
			}

			throw( e );
		}
	}

	@Override
	public void stopAndRemove(boolean delete_torrent, boolean delete_data)
		throws DownloadException, DownloadRemovalVetoException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "stopAndRemove", new Object[] {
				delete_torrent, delete_data
			} )).getResponse();

		}catch( RPException e ){

			Throwable cause = e.getCause();

			if ( cause instanceof DownloadException ){

				throw((DownloadException)cause);
			}

			if ( cause instanceof DownloadRemovalVetoException ){

				throw((DownloadRemovalVetoException)cause);
			}

			throw( e );
		}
	}

	@Override
	public void
	remove(
		boolean	delete_torrent,
		boolean	delete_data )

		throws DownloadException, DownloadRemovalVetoException
	{
		notSupported();
	}

	@Override
	public boolean
	canBeRemoved()

		throws DownloadRemovalVetoException
	{
		notSupported();

		return( false );
	}

	@Override
	public DownloadAnnounceResult
	getLastAnnounceResult()
	{
		return( announce_result );
	}

	@Override
	public DownloadScrapeResult
	getLastScrapeResult()
	{
		return( scrape_result );
	}

	@Override
	public DownloadScrapeResult
	getAggregatedScrapeResult( boolean cache )
	{
		notSupported();

		return( null );
	}

	@Override
	public DownloadStats
	getStats()
	{
		return( stats );
	}

	@Override
	public void
	addListener(
		DownloadListener	l )
	{
		notSupported();
	}

	@Override
	public void
	removeListener(
		DownloadListener	l )
	{
		notSupported();
	}

	@Override
	public void
	addTrackerListener(
		DownloadTrackerListener	l )
	{
		notSupported();
	}

	@Override
	public void
	removeTrackerListener(
		DownloadTrackerListener	l )
	{
		notSupported();
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
	public int
	getPosition()
	{
		return( position );
	}

	@Override
	public boolean
	isForceStart()
	{
		return( force_start );
	}

	@Override
	public void
	setForceStart(
		boolean _force_start )
	{
		force_start	= _force_start;

		_dispatcher.dispatch( new RPRequest( this, "setForceStart[boolean]", new Object[]{Boolean.valueOf(force_start)})).getResponse();
	}

	@Override
	public void
	setPosition(
		int new_position)
	{
		_dispatcher.dispatch( new RPRequest( this, "setPosition[int]", new Object[]{new Integer(new_position )})).getResponse();
	}

	@Override
	public void
	moveUp()
	{
		_dispatcher.dispatch( new RPRequest( this, "moveUp", null)).getResponse();
	}

	@Override
	public void
	moveDown()
	{
		_dispatcher.dispatch( new RPRequest( this, "moveDown", null)).getResponse();
	}

	@Override
	public void
	moveTo(
		int		position )
	{
		_dispatcher.dispatch( new RPRequest( this, "moveTo[int]", new Object[]{new Integer(position )})).getResponse();
	}

	@Override
	public void stopAndQueue() throws DownloadException {
		notSupported();
	}

	@Override
	public void
	recheckData()

		throws DownloadException
	{
		notSupported();
	}

	@Override
	public String getName() {
		notSupported();
		return ("");
	}

	public void
	addListener(
		DownloadPeerListener	l )
	{
		notSupported(l);
	}


	public void
	removeListener(
		DownloadPeerListener	l )
	{
		notSupported(l);
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
  public String getTorrentFileName() {
 		notSupported();
		return ("");
 }

  @Override
  public String
  getAttribute(
  	TorrentAttribute		attribute )
  {
	notSupported();
	return (null);
  }

  @Override
  public void
  setAttribute(
  	TorrentAttribute		attribute,
	String					value )
  {
  	notSupported();
  }

  @Override
  public String[]
  getListAttribute(
		TorrentAttribute		attribute )
  {
	notSupported();
	return (null);
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
	notSupported();
	return( null );
  }

  @Override
  public String getCategoryName() {
 		notSupported();
		return ("");
  }

  @Override
  public void setCategory(String sName) {
 		notSupported();
  }

  @Override
  public List<Tag> getTags() {
	notSupported();
	return null;
}
  @Override
  public boolean
  isPersistent()
  {
 		notSupported();
		return false;
  }

	@Override
	public void
	setMaximumDownloadKBPerSecond(
		int		kb )
 	{
		notSupported();
 	}

  @Override
  public int getUploadRateLimitBytesPerSecond() {
    notSupported();
    return 0;
  }

  @Override
  public void setUploadRateLimitBytesPerSecond(int max_rate_bps ) {  notSupported();  }

	@Override
	public int getDownloadRateLimitBytesPerSecond() {
	   notSupported();
	    return 0;
  	}

  	@Override
	  public void setDownloadRateLimitBytesPerSecond(int max_rate_bps ) {
		notSupported();
  	}

  	@Override
	  public int
	getMaximumDownloadKBPerSecond()
  	{
  		notSupported();

  		return(0);
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
  public boolean
	isComplete()
 	{
 		notSupported();

 		return( false );
 	}

 	@Override
  public boolean
 	isComplete(boolean b)
 	{
		notSupported();

 		return( false );
 	}

	@Override
	public boolean
 	isChecking()
	{
 		notSupported();

 		return( false );
 	}

	@Override
	public boolean
 	isMoving()
 	{
		notSupported();

		return( false );
 	}

	@Override
	public PeerManager
	getPeerManager()
	{
		notSupported();

		return( null );
	}

	@Override
	public DiskManager
	getDiskManager()
	{
		notSupported();

		return( null );
	}


	@Override
	public DiskManagerFileInfo[]
	getDiskManagerFileInfo()
	{
		RPDiskManagerFileInfo[] resp = (RPDiskManagerFileInfo[])_dispatcher.dispatch(
				new RPRequest(
						this,
						"getDiskManagerFileInfo",
						null)).getResponse();

		for (int i=0;i<resp.length;i++){

			resp[i]._setRemote( _dispatcher );
		}

		return( resp );
	}

	@Override
	public DiskManagerFileInfo
	getDiskManagerFileInfo(int index)
	{
		// TODO: Make it only return the index one

		RPDiskManagerFileInfo[] resp = (RPDiskManagerFileInfo[])_dispatcher.dispatch(
				new RPRequest(
						this,
						"getDiskManagerFileInfo",
						null)).getResponse();

		if (index >= 0 && index < resp.length) {
			resp[index]._setRemote( _dispatcher );
			return resp[index];
		}

		return( null );
	}

	@Override
	public int
	getDiskManagerFileCount() {
		notSupported();
		return 0;
	}

	@Override
	public long
	getCreationTime()
	{
		notSupported();

		return( 0 );
	}

  @Override
  public SeedingRank getSeedingRank() {
		notSupported();

		return( null );
  }

 	@Override
  public String
	getSavePath()
 	{
		notSupported();

		return( null );
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
  public void moveDataFiles(File new_parent_dir, String new_name) throws DownloadException {
 		notSupported();
 	}

  	@Override
	  public void
  	moveTorrentFile(
  		File	new_parent_dir )
 	{
 		notSupported();
  	}

 	@Override
  public void
	requestTrackerAnnounce()
 	{
		_dispatcher.dispatch( new RPRequest( this, "requestTrackerAnnounce", null)).getResponse();
 	}

	@Override
	public void
	requestTrackerAnnounce(
		boolean		immediate )
	{
		notSupported();
	}

	@Override
	public void
	requestTrackerScrape(
		boolean		immediate )
	{
		notSupported();
	}

	@Override
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
		notSupported();
	}

	@Override
	public void
	setScrapeResult(
		DownloadScrapeResult	result )
	{
		notSupported();
	}

	@Override
	public DownloadActivationEvent
	getActivationState()
	{
		notSupported();

		return( null );
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

		/* (non-Javadoc)
		 * @see com.biglybt.pif.download.Download#setSeedingRank(int)
		 */
		@Override
		public void setSeedingRank(SeedingRank rank) {
			// TODO Auto-generated method stub

		}

		@Override
		public void addTrackerListener(DownloadTrackerListener l, boolean immediateTrigger) {
			notSupported();
		}

		@Override
		public void renameDownload(String new_name) {
			notSupported();
		}

	@Override
	public boolean getBooleanAttribute(TorrentAttribute ta) {notSupported(); return false;}
	@Override
	public int getIntAttribute(TorrentAttribute ta) {notSupported(); return 0;}
	@Override
	public long getLongAttribute(TorrentAttribute ta) {notSupported(); return 0L;}
	@Override
	public boolean hasAttribute(TorrentAttribute ta) {notSupported(); return false;}
	@Override
	public void setBooleanAttribute(TorrentAttribute ta, boolean value) {notSupported();}
	@Override
	public void setIntAttribute(TorrentAttribute ta, int value) {notSupported();}
	@Override
	public void setListAttribute(TorrentAttribute ta, String[] value) {notSupported();}
	@Override
	public void setLongAttribute(TorrentAttribute ta, long value) {notSupported();}
	@Override
	public void setFlag(long flag, boolean set) {notSupported();}

	@Override
	public void addAttributeListener(DownloadAttributeListener l, TorrentAttribute a, int e) {notSupported();}
	@Override
	public void removeAttributeListener(DownloadAttributeListener l, TorrentAttribute a, int e) {notSupported();}

	@Override
	public void addCompletionListener(DownloadCompletionListener l) {notSupported();}
	@Override
	public void removeCompletionListener(DownloadCompletionListener l) {notSupported();}

	@Override
	public boolean isRemoved() {notSupported();	return false; }
	@Override
	public boolean canMoveDataFiles() {notSupported(); return false;}
	@Override
	public SaveLocationChange calculateDefaultDownloadLocation() {notSupported(); return null;}

	@Override
	public Object getUserData(Object key) {
		notSupported();
		return null;
	}

	@Override
	public void setUserData(Object key, Object data) {
		notSupported();
	}

	@Override
	public void startDownload(boolean force) {notSupported();}
	@Override
	public void stopDownload() {notSupported();}
	@Override
	public void changeLocation(SaveLocationChange slc) {notSupported();}

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
		notSupported();
		return( null );
	}

	@Override
	public byte[]
	getTorrentHash()
	{
		notSupported();

		return( null );
	}

	@Override
	public long
	getTorrentSize()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public DownloadStubFile[]
	getStubFiles()
	{
		notSupported();

		return( null );
	}

	// @see com.biglybt.pif.download.Download#getPrimaryFile()
	@Override
	public DiskManagerFileInfo getPrimaryFile() {
		return getDiskManagerFileInfo(0);
	}
}
