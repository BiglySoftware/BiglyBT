/*
 * File    : TrackerTorrentImpl.java
 * Created : 08-Dec-2003
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

package com.biglybt.pifimpl.local.tracker;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.tracker.host.*;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.*;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;

public class
TrackerTorrentImpl
	implements TrackerTorrent, TRHostTorrentListener, TRHostTorrentWillBeRemovedListener
{
	protected TRHostTorrent		host_torrent;

	protected List	listeners_cow		= new ArrayList();
	protected List	removal_listeners	= new ArrayList();

	protected AEMonitor this_mon 	= new AEMonitor( "TrackerTorrent" );

	public
	TrackerTorrentImpl(
		TRHostTorrent	_host_torrent )
	{
		host_torrent	= _host_torrent;
	}

	// not visible to plugins
	public TRHostTorrent
	getHostTorrent()
	{
		return( host_torrent );
	}

	@Override
	public void
	start()

		throws TrackerException
	{
		try{
			host_torrent.start();

		}catch( Throwable e ){

			throw( new TrackerException("Start failed", e ));
		}
	}

	@Override
	public void
	stop()

		throws TrackerException
	{
		try{
			host_torrent.stop();

		}catch( Throwable e ){

			throw( new TrackerException("Stop failed", e ));
		}
	}

	@Override
	public void
	remove()

		throws TrackerTorrentRemovalVetoException
	{
		try{
			host_torrent.remove();

		}catch( TRHostTorrentRemovalVetoException e ){

			throw( new TrackerTorrentRemovalVetoException(e.getMessage()));
		}
	}

	@Override
	public boolean
	canBeRemoved()

		throws TrackerTorrentRemovalVetoException
	{
		try{
			host_torrent.canBeRemoved();

		}catch( TRHostTorrentRemovalVetoException e ){

			throw( new TrackerTorrentRemovalVetoException(e.getMessage()));
		}

		return( true );
	}

	@Override
	public Torrent
	getTorrent()
	{
		return( new TorrentImpl( host_torrent.getTorrent()));
	}

	@Override
	public TrackerPeer[]
	getPeers()
	{
		TRHostPeer[]	peers = host_torrent.getPeers();

		TrackerPeer[]	res = new TrackerPeer[peers.length];

		for (int i=0;i<peers.length;i++){

			res[i] = new TrackerPeerImpl( peers[i]);
		}

		return( res );
	}

	@Override
	public int
	getStatus()
	{
		int	status = host_torrent.getStatus();

		switch(status){
			case TRHostTorrent.TS_STARTED:
				return( TS_STARTED );
			case TRHostTorrent.TS_STOPPED:
				return( TS_STOPPED );
			case TRHostTorrent.TS_PUBLISHED:
				return( TS_PUBLISHED );
			default:
				throw( new RuntimeException( "TrackerTorrent: status invalid"));
		}
	}

	@Override
	public long
	getTotalUploaded()
	{
		return( host_torrent.getTotalUploaded());
	}

	@Override
	public long
	getTotalDownloaded()
	{
		return( host_torrent.getTotalDownloaded());
	}

	@Override
	public long
	getAverageUploaded()
	{
		return( host_torrent.getAverageUploaded());
	}

	@Override
	public long
	getAverageDownloaded()
	{
		return( host_torrent.getAverageDownloaded());
	}

	@Override
	public long
	getTotalLeft()
	{
		return( host_torrent.getTotalLeft());
	}

	@Override
	public long
	getCompletedCount()
	{
		return( host_torrent.getCompletedCount());
	}

	@Override
	public long
	getTotalBytesIn()
	{
		return( host_torrent.getTotalBytesIn());
	}

	@Override
	public long
	getAverageBytesIn()
	{
		return( host_torrent.getAverageBytesIn());
	}

	@Override
	public long
	getTotalBytesOut()
	{
		return( host_torrent.getTotalBytesOut());
	}

	@Override
	public long
	getAverageBytesOut()
	{
		return( host_torrent.getAverageBytesOut());
	}

	@Override
	public long
	getAverageScrapeCount()
	{
		return( host_torrent.getAverageScrapeCount());
	}

	@Override
	public long
	getScrapeCount()
	{
		return( host_torrent.getScrapeCount());
	}

	@Override
	public long
	getAverageAnnounceCount()
	{
		return( host_torrent.getAverageAnnounceCount());
	}

	@Override
	public long
	getAnnounceCount()
	{
		return( host_torrent.getAnnounceCount());
	}

	@Override
	public int
	getSeedCount()
	{
		return( host_torrent.getSeedCount());
	}

	@Override
	public int
	getLeecherCount()
	{
		return( host_torrent.getLeecherCount());
	}

	@Override
	public int
	getBadNATCount()
	{
		return( host_torrent.getBadNATCount());
	}

	@Override
	public void
	disableReplyCaching()
	{
		host_torrent.disableReplyCaching();
	}

	@Override
	public boolean
	isPassive()
	{
		return( host_torrent.isPassive());
	}

	@Override
	public boolean 
	isExternal()
	{
		return( host_torrent.isExternal());
	}
	
	@Override
	public long
	getDateAdded()
	{
		return( host_torrent.getDateAdded());
	}

	@Override
	public void
	preProcess(
		TRHostTorrentRequest	request )

		throws TRHostException
	{
		List	listeners_ref = listeners_cow;

		for (int i=0;i<listeners_ref.size();i++){

			try{
				((TrackerTorrentListener)listeners_ref.get(i)).preProcess(new TrackerTorrentRequestImpl(request));

			}catch( TrackerException e ){

				throw( new TRHostException( e.getMessage(), e ));

			}catch( Throwable e ){

				throw( new TRHostException( "Pre-process fails", e ));
			}
		}
	}

	@Override
	public void
	postProcess(
		TRHostTorrentRequest	request )

		throws TRHostException
	{
		List	listeners_ref = listeners_cow;

		for (int i=0;i<listeners_ref.size();i++){

			try{
				((TrackerTorrentListener)listeners_ref.get(i)).postProcess(new TrackerTorrentRequestImpl(request));

			}catch( TrackerException e ){

				throw( new TRHostException( e.getMessage(), e ));

			}catch( Throwable e ){

				throw( new TRHostException( "Post-process fails", e ));
			}
		}
	}

	@Override
	public void
	addListener(
		TrackerTorrentListener	listener )
	{
		try{
			this_mon.enter();

			List	new_listeners = new ArrayList( listeners_cow );

			new_listeners.add( listener );

			if ( new_listeners.size() == 1 ){

				host_torrent.addListener( this );
			}

			listeners_cow = new_listeners;

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		TrackerTorrentListener	listener )
	{
		try{
			this_mon.enter();

			List	new_listeners = new ArrayList( listeners_cow );

			new_listeners.remove( listener );

			if ( new_listeners.size() == 0 ){

				host_torrent.removeListener(this);
			}

			listeners_cow = new_listeners;

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	torrentWillBeRemoved(
		TRHostTorrent	t )

		throws TRHostTorrentRemovalVetoException
	{
		for (int i=0;i<removal_listeners.size();i++){

			try{
				((TrackerTorrentWillBeRemovedListener)removal_listeners.get(i)).torrentWillBeRemoved( this );

			}catch( TrackerTorrentRemovalVetoException e ){

				throw( new TRHostTorrentRemovalVetoException( e.getMessage()));
			}
		}
	}

	@Override
	public void
	addRemovalListener(
		TrackerTorrentWillBeRemovedListener	listener )
	{
		try{
			this_mon.enter();

			removal_listeners.add( listener );

			if ( removal_listeners.size() == 1 ){

				host_torrent.addRemovalListener( this );
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeRemovalListener(
		TrackerTorrentWillBeRemovedListener	listener )
	{
		try{
			this_mon.enter();

			removal_listeners.remove( listener );

			if ( removal_listeners.size() == 0 ){

				host_torrent.removeRemovalListener(this);
			}
		}finally{

			this_mon.exit();
		}
	}

	public boolean
	equals(
		Object	other )
	{
			// as we're lazy and create new instances of this on demand we need to
			// do something sensible about equivalence

		if ( other instanceof TrackerTorrentImpl ){

			return( host_torrent == ((TrackerTorrentImpl)other).host_torrent );
		}

		return( false );
	}

	public int
	hashCode()
	{
		return( host_torrent.hashCode());
	}
}