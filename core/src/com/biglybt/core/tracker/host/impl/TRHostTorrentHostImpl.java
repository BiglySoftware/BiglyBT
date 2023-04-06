/*
 * File    : TRHostTorrentImpl.java
 * Created : 26-Oct-2003
 * By      : stuff
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

package com.biglybt.core.tracker.host.impl;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.host.*;
import com.biglybt.core.tracker.server.TRTrackerServer;
import com.biglybt.core.tracker.server.TRTrackerServerPeer;
import com.biglybt.core.tracker.server.TRTrackerServerTorrent;
import com.biglybt.core.tracker.server.TRTrackerServerTorrentStats;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.Debug;

public class
TRHostTorrentHostImpl
	implements TRHostTorrent
{
	private final TRHostImpl					host;
	private final TRTrackerServer				server;
	private TRTrackerServerTorrent		server_torrent;
	private TOTorrent					torrent;
	private long						date_added;
	private final int							port;

	private List				listeners_cow		= new ArrayList();
	private final List				removal_listeners	= new ArrayList();

	private int					status	= TS_STOPPED;
	private boolean				persistent;
	private boolean				passive;

	private long				sos_uploaded;
	private long				sos_downloaded;
	private long				sos_bytes_in;
	private long				sos_bytes_out;
	private long				sos_announce;
	private long				sos_scrape;
	private long				sos_complete;

	private long				last_uploaded;
	private long				last_downloaded;
	private long				last_bytes_in;
	private long				last_bytes_out;
	private long				last_announce;
	private long				last_scrape;

		//average over 10 periods, update every period.

	private final Average			average_uploaded		= Average.getInstance(TRHostImpl.STATS_PERIOD_SECS*1000,TRHostImpl.STATS_PERIOD_SECS*10);
	private final Average			average_downloaded		= Average.getInstance(TRHostImpl.STATS_PERIOD_SECS*1000,TRHostImpl.STATS_PERIOD_SECS*10);
	private final Average			average_bytes_in		= Average.getInstance(TRHostImpl.STATS_PERIOD_SECS*1000,TRHostImpl.STATS_PERIOD_SECS*10);
	private final Average			average_bytes_out		= Average.getInstance(TRHostImpl.STATS_PERIOD_SECS*1000,TRHostImpl.STATS_PERIOD_SECS*10);
	private final Average			average_announce		= Average.getInstance(TRHostImpl.STATS_PERIOD_SECS*1000,TRHostImpl.STATS_PERIOD_SECS*10);
	private final Average			average_scrape			= Average.getInstance(TRHostImpl.STATS_PERIOD_SECS*1000,TRHostImpl.STATS_PERIOD_SECS*10);

	private boolean			disable_reply_caching;

	private HashMap data;

	protected final AEMonitor this_mon 	= new AEMonitor( "TRHostTorrentHost" );

	protected
	TRHostTorrentHostImpl(
		TRHostImpl		_host,
		TRTrackerServer	_server,
		TOTorrent		_torrent,
		int				_port,
		long			_date_added )
	{
		host		= _host;
		server		= _server;
		torrent		= _torrent;
		port		= _port;
		date_added	= _date_added;
	}

	@Override
	public int
	getPort()
	{
		return( port );
	}

	@Override
	public void
	start()
	{
			// there's a potential deadlock situation if we call the server while holding
			// the torrent lock, as the server then calls back to the host and we get
			// a torrent -> host monitor chain. We already have a host->torrent chain.
			// easiest solution is to delegate call to the host, which will grab the host
			// monitor and then call back out to startSupport. Hence the chain is in the
			// right direction

		host.startTorrent( this );
	}

	protected void
	startSupport()
	{
		try{
			this_mon.enter();

			// System.out.println( "TRHostTorrentHostImpl::start");

			status = TS_STARTED;

			server_torrent = server.permit( "", torrent.getHash(), true);

			if ( disable_reply_caching ){

				server_torrent.disableCaching();
			}

		}catch( Throwable e ){

			Debug.printStackTrace( e );

		}finally{

			this_mon.exit();
		}

		host.hostTorrentStateChange( this );
	}

	@Override
	public void
	stop()
	{
		host.stopTorrent( this );
	}

	protected void
	stopSupport()
	{
		try{
			this_mon.enter();

			// System.out.println( "TRHostTorrentHostImpl::stop");

			status = TS_STOPPED;

			server.deny( torrent.getHash(), true);

			TRTrackerServerTorrent		st				= server_torrent;

			TRTrackerServerTorrentStats	torrent_stats 	= st==null?null:st.getStats();

			if ( torrent_stats != null ){

				sos_uploaded	= sos_uploaded 		+ torrent_stats.getUploaded();
				sos_downloaded	= sos_downloaded 	+ torrent_stats.getDownloaded();
				sos_bytes_in	= sos_bytes_in 		+ torrent_stats.getBytesIn();
				sos_bytes_out	= sos_bytes_out 	+ torrent_stats.getBytesOut();
				sos_announce	= sos_announce		+ torrent_stats.getAnnounceCount();
				sos_scrape		= sos_scrape		+ torrent_stats.getScrapeCount();
				sos_complete	= sos_complete		+ torrent_stats.getCompletedCount();

				torrent_stats	= null;
			}

			last_uploaded		= 0;
			last_downloaded		= 0;
			last_bytes_in		= 0;
			last_bytes_out		= 0;
			last_announce		= 0;
			last_scrape			= 0;

		}catch( Throwable e ){

			Debug.printStackTrace( e );

		}finally{

			this_mon.exit();
		}

		host.hostTorrentStateChange( this );
	}

	@Override
	public void
	remove()

		throws TRHostTorrentRemovalVetoException
	{
		canBeRemoved();

		stop();

		host.remove( this );
	}

	@Override
	public boolean
	canBeRemoved()

		throws TRHostTorrentRemovalVetoException
	{
		ArrayList	listeners_copy;

		try{
			this_mon.enter();

			listeners_copy = new ArrayList( removal_listeners );

		}finally{

			this_mon.exit();
		}

		for (int i=0;i<listeners_copy.size();i++){

			((TRHostTorrentWillBeRemovedListener)listeners_copy.get(i)).torrentWillBeRemoved( this );
		}

		return( true );
	}

	@Override
	public int
	getStatus()
	{
		return( status );
	}

	@Override
	public boolean
	isPersistent()
	{
		return( persistent );
	}

	protected void
	setPersistent(
		boolean		_persistent )
	{
		persistent	= _persistent;
	}

	@Override
	public boolean
	isPassive()
	{
		return( passive );
	}

	@Override
	public boolean
	isExternal()
	{
		return( torrent instanceof TRHostExternalTorrent );
	}
	
	@Override
	public void
	setPassive(
		boolean		b )
	{
		passive	= b;
	}

	@Override
	public long
	getDateAdded()
	{
		return( date_added );
	}

	@Override
	public TOTorrent
	getTorrent()
	{
		return( torrent );
	}

	@Override
	public void
	setTorrent(
		TOTorrent		_torrent )
	{
		if ( _torrent != torrent ){

			torrent = _torrent;

			if ( torrent != null ){

				passive = false;
			}

			host.torrentUpdated( this );
		}
	}

	protected void
	setTorrentInternal(
		TOTorrent		_torrent )
	{
		torrent = _torrent;
	}

	@Override
	public TRTrackerServerTorrent
	getTrackerTorrent()
	{
		return( server_torrent );
	}

	@Override
	public TRHostPeer[]
	getPeers()
	{
		try{

			TRTrackerServerPeer[]	peers = server.getPeers( torrent.getHash());

			if ( peers != null ){

				TRHostPeer[]	res = new TRHostPeer[peers.length];

				for (int i=0;i<peers.length;i++){

					res[i] = new TRHostPeerHostImpl(peers[i]);
				}

				return( res );
			}
		}catch( TOTorrentException e ){

			Debug.printStackTrace( e );
		}

		return( new TRHostPeer[0] );
	}

	protected TRTrackerServerTorrentStats
	getStats()
	{
		TRTrackerServerTorrent	st = server_torrent;

		if ( st != null ){

			return( st.getStats());
		}

		return( null );
	}

	protected void
	setStartOfDayValues(
		long		_date_added,
		long		completed,
		long		announces,
		long		scrapes,
		long		uploaded,
		long		downloaded,
		long		bytes_in,
		long		bytes_out )
	{
		date_added			= _date_added;
		sos_complete		= completed;
		sos_announce		= announces;
		sos_scrape			= scrapes;
		sos_uploaded		= uploaded;
		sos_downloaded		= downloaded;
		sos_bytes_in		= bytes_in;
		sos_bytes_out		= bytes_out;
	}

	@Override
	public int
	getSeedCount()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( stats.getSeedCount());
		}

		return( 0 );
	}

	@Override
	public int
	getLeecherCount()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( stats.getLeecherCount());
		}

		return( 0 );
	}

	@Override
	public int
	getBadNATCount()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( stats.getBadNATPeerCount());
		}

		return( 0 );
	}

	protected void
	updateStats()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			long	current_uploaded 	= stats.getUploaded();

			long ul_diff = current_uploaded - last_uploaded;

			if ( ul_diff < 0 ){

				ul_diff = 0;
			}

			average_uploaded.addValue(ul_diff);

			last_uploaded = current_uploaded;

				// downloaded

			long	current_downloaded 	= stats.getDownloaded();

			long dl_diff = current_downloaded - last_downloaded;

			if ( dl_diff < 0 ){

				dl_diff = 0;
			}

			average_downloaded.addValue(dl_diff);

			last_downloaded = current_downloaded;

				// bytes in

			long	current_bytes_in 	= stats.getBytesIn();

			long bi_diff = current_bytes_in - last_bytes_in;

			if ( bi_diff < 0 ){

				bi_diff = 0;
			}

			average_bytes_in.addValue(bi_diff);

			last_bytes_in = current_bytes_in;

				// bytes out

			long	current_bytes_out 	= stats.getBytesOut();

			long bo_diff = current_bytes_out - last_bytes_out;

			if ( bo_diff < 0 ){

				bo_diff = 0;
			}

			average_bytes_out.addValue(bo_diff);

			last_bytes_out = current_bytes_out;

				// announce

			long	current_announce 	= stats.getAnnounceCount();

			long an_diff = current_announce - last_announce;

			if ( an_diff < 0 ){

				an_diff = 0;
			}

			average_announce.addValue(an_diff);

			last_announce = current_announce;

				// scrape

			long	current_scrape 	= stats.getScrapeCount();

			long sc_diff = current_scrape - last_scrape;

			if ( sc_diff < 0 ){

				sc_diff = 0;
			}

			average_scrape.addValue(sc_diff);

			last_scrape = current_scrape;
		}
	}

	protected TRTrackerServer
	getServer()
	{
		return( server );
	}

	@Override
	public long
	getTotalUploaded()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( sos_uploaded + stats.getUploaded());
		}

		return( sos_uploaded );
	}

	@Override
	public long
	getTotalDownloaded()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( sos_downloaded + stats.getDownloaded());
		}

		return( sos_downloaded );
	}

	@Override
	public long
	getTotalLeft()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( stats.getAmountLeft());
		}

		return( 0 );
	}

	@Override
	public long
	getTotalBytesIn()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( sos_bytes_in + stats.getBytesIn());
		}

		return( sos_bytes_in );
	}

	@Override
	public long
	getTotalBytesOut()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( sos_bytes_out + stats.getBytesOut());
		}

		return( sos_bytes_out );
	}

	@Override
	public long
	getAnnounceCount()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( sos_announce + stats.getAnnounceCount());
		}

		return( sos_announce );
	}

	@Override
	public long
	getScrapeCount()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( sos_scrape + stats.getScrapeCount());
		}

		return( sos_scrape );
	}

	@Override
	public long
	getCompletedCount()
	{
		TRTrackerServerTorrentStats	stats = getStats();

		if ( stats != null ){

			return( sos_complete + stats.getCompletedCount());
		}

		return( sos_complete );
	}

		// averages

	@Override
	public long
	getAverageBytesIn()
	{
		return( average_bytes_in.getAverage());
	}

	@Override
	public long
	getAverageBytesOut()
	{
		return( average_bytes_out.getAverage() );
	}

	@Override
	public long
	getAverageUploaded()
	{
		return( average_uploaded.getAverage() );
	}

	@Override
	public long
	getAverageDownloaded()
	{
		return( average_downloaded.getAverage() );
	}

	@Override
	public long
	getAverageAnnounceCount()
	{
		return( average_announce.getAverage());
	}

	@Override
	public long
	getAverageScrapeCount()
	{
		return( average_scrape.getAverage());
	}


	@Override
	public void
	disableReplyCaching()
	{
		TRTrackerServerTorrent	st = server_torrent;

		disable_reply_caching	= true;

		if ( st != null ){

			st.disableCaching();
		}
	}

	protected void
	preProcess(
		TRHostTorrentRequest	req )

		throws TRHostException
	{
		List	listeners_ref = listeners_cow;

		for (int i=0;i<listeners_ref.size();i++){

			try{
				((TRHostTorrentListener)listeners_ref.get(i)).preProcess(req);

			}catch( TRHostException e ){

				throw( e );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	postProcess(
		TRHostTorrentRequest	req )

		throws TRHostException
	{
		List	listeners_ref = listeners_cow;

		for (int i=0;i<listeners_ref.size();i++){

			try{
				((TRHostTorrentListener)listeners_ref.get(i)).postProcess(req);

			}catch( TRHostException e ){

				throw( e );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	addListener(
		TRHostTorrentListener	l )
	{
		try{
			this_mon.enter();

			List	new_listeners = new ArrayList( listeners_cow );

			new_listeners.add(l);

			listeners_cow	= new_listeners;

		}finally{

			this_mon.exit();
		}

		host.torrentListenerRegistered();
	}

	@Override
	public void
	removeListener(
		TRHostTorrentListener	l )
	{
		try{
			this_mon.enter();

			List	new_listeners = new ArrayList( listeners_cow );

			new_listeners.remove(l);

			listeners_cow	= new_listeners;

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	addRemovalListener(
		TRHostTorrentWillBeRemovedListener	l )
	{
		try{
			this_mon.enter();

			removal_listeners.add(l);

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeRemovalListener(
		TRHostTorrentWillBeRemovedListener	l )
	{
		try{
			this_mon.enter();

			removal_listeners.remove(l);
		}finally{

			this_mon.exit();
		}
	}

  /** To retreive arbitrary objects against this object. */
  @Override
  public Object getData (String key) {
  	if (data == null) return null;
    return data.get(key);
  }

  /** To store arbitrary objects against this object. */
  @Override
  public void setData (String key, Object value) {
  	try{
  		this_mon.enter();

	  	if (data == null) {
	  	  data = new HashMap();
	  	}
	    if (value == null) {
	      if (data.containsKey(key))
	        data.remove(key);
	    } else {
	      data.put(key, value);
	    }
  	}finally{

  		this_mon.exit();
  	}
  }
}
