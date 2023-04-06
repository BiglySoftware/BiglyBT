/*
 * File    : TRHostTorrentPublishImpl.java
 * Created : 12-Nov-2003
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

package com.biglybt.core.tracker.host.impl;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerScraperFactory;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.tracker.host.*;
import com.biglybt.core.tracker.server.TRTrackerServerTorrent;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;

public class
TRHostTorrentPublishImpl
	implements TRHostTorrent
{
	private final TRHostImpl		host;
	private TOTorrent		torrent;

	private final long			date_added;

	private static final int				status	= TS_PUBLISHED;
	private boolean			persistent;

	private int					seed_count;
	private int					peer_count;
	private TRHostPeer[]		peers = new TRHostPeer[0];

	private List				listeners_cow		= new ArrayList();
	private final List				removal_listeners	= new ArrayList();

	private HashMap data;

	protected final AEMonitor this_mon 	= new AEMonitor( "TRHostTorrentPublish" );

	protected
	TRHostTorrentPublishImpl(
		TRHostImpl		_host,
		TOTorrent		_torrent,
		long			_date_added )
	{
		host		= _host;
		torrent		= _torrent;
		date_added	= _date_added;
	}

	@Override
	public void
	start()
	{
	}

	@Override
	public void
	stop()
	{
	}

	@Override
	public void
	remove()

		throws TRHostTorrentRemovalVetoException
	{
		try{
			this_mon.enter();

			canBeRemoved();

			host.remove( this );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public boolean
	canBeRemoved()

		throws TRHostTorrentRemovalVetoException
	{
		for (int i=0;i<removal_listeners.size();i++){

			((TRHostTorrentWillBeRemovedListener)removal_listeners.get(i)).torrentWillBeRemoved( this );
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

	public void
	setPersistent(
		boolean		_persistent )
	{
		persistent	= _persistent;
	}

	@Override
	public boolean
	isPassive()
	{
		return( false );
	}

	@Override
	public void
	setPassive(
		boolean		passive )
	{
	}

	@Override
	public boolean 
	isExternal()
	{
		return( false );
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
		TOTorrent	t )
	{
		torrent	= t;
	}

	@Override
	public TRTrackerServerTorrent
	getTrackerTorrent()
	{
		return( null );
	}

	@Override
	public int
	getPort()
	{
		return( -1 );
	}

	@Override
	public TRHostPeer[]
	getPeers()
	{
		try{
			this_mon.enter();

			return( peers );
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public long
	getAnnounceCount()
	{
		return( 0 );
	}

	@Override
	public long
	getAverageAnnounceCount()
	{
		return( 0 );
	}

	@Override
	public long
	getScrapeCount()
	{
		return( 0 );
	}

	@Override
	public long
	getAverageScrapeCount()
	{
		return( 0 );
	}

	@Override
	public long
	getCompletedCount()
	{
		return( 0 );
	}

	protected void
	updateStats()
	{
		TRTrackerScraperResponse resp = null;

		TRTrackerAnnouncer tc = host.getTrackerClient( this );

		if ( tc != null ){

			resp = TRTrackerScraperFactory.getSingleton().scrape( tc );
		}

		if ( resp == null ){

			resp = TRTrackerScraperFactory.getSingleton().scrape( torrent );
		}

		try{
			this_mon.enter();

			if ( resp != null && resp.isValid()){

				peer_count 	= resp.getPeers();
				seed_count	= resp.getSeeds();

				peers = new TRHostPeer[ peer_count + seed_count ];

				for (int i=0;i<peers.length;i++){

					peers[i] = new TRHostPeerPublishImpl( i<seed_count );
				}
			}else{

				peers = new TRHostPeer[0];
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public int
	getSeedCount()
	{
		return( seed_count );
	}

	@Override
	public int
	getLeecherCount()
	{
		return( peer_count );
	}

	@Override
	public int
	getBadNATCount()
	{
		return( 0 );
	}

	@Override
	public long
	getTotalUploaded()
	{
		return( 0 );
	}

	@Override
	public long
	getTotalDownloaded()
	{
		return( 0 );
	}

	@Override
	public long
	getTotalLeft()
	{
		return( 0 );
	}

	@Override
	public long
	getAverageUploaded()
	{
		return( 0 );
	}

	@Override
	public long
	getAverageDownloaded()
	{
		return( 0 );
	}

	@Override
	public long
	getTotalBytesIn()
	{
		return( 0 );
	}

	@Override
	public long
	getTotalBytesOut()
	{
		return( 0 );
	}

	@Override
	public long
	getAverageBytesIn()
	{
		return( 0 );
	}

	@Override
	public long
	getAverageBytesOut()
	{
		return( 0 );
	}

	@Override
	public void
	disableReplyCaching()
	{
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
