/*
 * File    : PluginPEPeerWrapper.java
 * Created : 01-Dec-2003
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

package com.biglybt.pifimpl.local.peers;

/**
 * @author parg
 *
 */

import java.util.HashMap;
import java.util.List;

import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerListener;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.network.ConnectionStub;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.peers.*;
import com.biglybt.pifimpl.local.messaging.MessageAdapter;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl.PluginLimitedRateGroup;


public class
PeerImpl
	extends LogRelation
	implements Peer
{
	protected PeerManagerImpl	manager;
	protected PEPeer			delegate;

	private HashMap<PeerListener2,PEPeerListener> peer_listeners;

	private UtilitiesImpl.PluginLimitedRateGroupListener	up_rg_listener;
	private UtilitiesImpl.PluginLimitedRateGroupListener	down_rg_listener;

	private volatile boolean closed;

		/**
		 * don't use me, use PeerManagerImpl.getPeerForPEPeer
		 * @param _delegate
		 */

	protected
	PeerImpl(
		PEPeer	_delegate )
	{
		delegate	= _delegate;

		manager = PeerManagerImpl.getPeerManager( delegate.getManager());
	}

	@Override
	public void
	bindConnection(
		ConnectionStub		stub )
	{
	}

	@Override
	public PeerManager
	getManager()
	{
		return( manager );
	}

	public PEPeer
	getDelegate()
	{
		return( delegate );
	}

  @Override
  public Connection getConnection() {
    return delegate.getPluginConnection();
  }


  @Override
  public boolean supportsMessaging() {
    return delegate.supportsMessaging();
  }


  @Override
  public Message[] getSupportedMessages() {
    com.biglybt.core.peermanager.messaging.Message[] core_msgs = delegate.getSupportedMessages();

    Message[] plug_msgs = new Message[ core_msgs.length ];

    for( int i=0; i < core_msgs.length; i++ ) {
      plug_msgs[i] = new MessageAdapter( core_msgs[i] );
    }

    return plug_msgs;
  }



	@Override
	public int
	getState()
	{
		int	state = delegate.getPeerState();

		switch( state ){

			case PEPeer.CONNECTING:
			{
				return( Peer.CONNECTING );
			}
			case PEPeer.DISCONNECTED:
			{
				return( Peer.DISCONNECTED );
			}
			case PEPeer.HANDSHAKING:
			{
				return( Peer.HANDSHAKING );
			}
			case PEPeer.TRANSFERING:
			{
				return( Peer.TRANSFERING );
			}
		}

		return( -1 );
	}

	@Override
	public boolean 
	isMyPeer()
	{
		return( delegate.isMyPeer());
	}
	
	@Override
	public byte[] getId()
	{
			// we *really* don't want a plugin to accidentally change our peerid (e.g. the Stuffer plugin did this)
			// as this screws stuff up bigtime

		byte[]	id = delegate.getId();

		if ( id == null ){

			return( new byte[0] );
		}

		byte[]	copy = new byte[id.length];

		System.arraycopy( id, 0, copy, 0, copy.length );

		return( copy );
	}

	@Override
	public String getIp()
	{
		return( delegate.getIp());
	}

	@Override
	public int getPort()
	{
		return( delegate.getPort());
	}

	@Override
	public int getTCPListenPort() {  return delegate.getTCPListenPort();  }
	@Override
	public int getUDPListenPort() {  return delegate.getUDPListenPort();  }
	@Override
	public int getUDPNonDataListenPort() { return delegate.getUDPNonDataListenPort(); }

	@Override
	public boolean isLANLocal() {
		return( delegate.isLANLocal());
	}

	@Override
	public void resetLANLocalStatus(){
		delegate.resetLANLocalStatus();
	}
	
	@Override
	public final boolean[] getAvailable()
	{
		BitFlags bf = delegate.getAvailable();
		if ( bf == null ){
			return( null );
		}
		return( bf.flags );
	}

	@Override
	public boolean isPieceAvailable(int pieceNumber)
	{
		return delegate.isPieceAvailable(pieceNumber);
	}

	@Override
	public boolean
	isTransferAvailable()
	{
		return( delegate.transferAvailable());
	}

	@Override
	public boolean isDownloadPossible()
	{
		return delegate.isDownloadPossible();
	}

	@Override
	public boolean isChoked()
	{
		return( delegate.isChokingMe());
	}

	@Override
	public boolean isChoking()
	{
		return( delegate.isChokedByMe());
	}

	@Override
	public boolean isInterested()
	{
		return( delegate.isInteresting());
	}

	@Override
	public boolean isInteresting()
	{
		return( delegate.isInterested());
	}

	@Override
	public boolean isSeed()
	{
		return( delegate.isSeed());
	}

	@Override
	public boolean isSnubbed()
	{
		return( delegate.isSnubbed());
	}

	@Override
	public long getSnubbedTime()
	{
		return delegate.getSnubbedTime();
	}

	@Override
	public void
	setSnubbed(
		boolean	b )
	{
		delegate.setSnubbed(b);
	}

	@Override
	public PeerStats getStats()
	{
		return( new PeerStatsImpl(manager, this, delegate.getStats()));
	}


	@Override
	public boolean isIncoming()
	{
		return( delegate.isIncoming());
	}

	@Override
	public int getOutgoingRequestCount() {
		return( delegate.getOutgoingRequestCount());
	}

	@Override
	public int[]
	getOutgoingRequestedPieceNumbers(){
		return( delegate.getOutgoingRequestedPieceNumbers());
	}

	@Override
	public int getPercentDoneInThousandNotation()
	{
		return( delegate.getPercentDoneInThousandNotation());
	}

	@Override
	public String getClient()
	{
		return( delegate.getClient());
	}

	@Override
	public boolean isOptimisticUnchoke()
	{
		return( delegate.isOptimisticUnchoke());
	}

	@Override
	public void setOptimisticUnchoke(boolean is_optimistic ) {
		delegate.setOptimisticUnchoke( is_optimistic );
	}

	public void
	initialize()
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public List
	getExpiredRequests()
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public List
	getRequests()
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public int
	getNumberOfRequests()
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public int
	getMaximumNumberOfRequests()
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public int[]
	getPriorityOffsets()
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public boolean
	requestAllocationStarts(
		int[]	base_priorities )
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public void
	requestAllocationComplete()
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public void
	cancelRequest(
		PeerReadRequest	request )
	{
		throw( new RuntimeException( "not supported"));
	}


	@Override
	public boolean
	addRequest(
		PeerReadRequest	request )
	{
		throw( new RuntimeException( "not supported"));
	}

	private void
	createRGListeners()
	{
		up_rg_listener =
				new UtilitiesImpl.PluginLimitedRateGroupListener()
				{
					@Override
					public void
					disabledChanged(
						PluginLimitedRateGroup		group,
						boolean						is_disabled )
					{
						if ( closed ){

							group.removeListener( this );
						}

						delegate.setUploadDisabled( group, is_disabled );
					}

					@Override
					public void
					sync(
						PluginLimitedRateGroup		group,
						boolean						is_disabled )
					{
						if ( closed ){

							group.removeListener( this );
						}
					}
				};

		down_rg_listener =
				new UtilitiesImpl.PluginLimitedRateGroupListener()
				{
					@Override
					public void
					disabledChanged(
						PluginLimitedRateGroup		group,
						boolean						is_disabled )
					{
						if ( closed ){

							group.removeListener( this );
						}

						delegate.setDownloadDisabled( group, is_disabled );
					}

					@Override
					public void
					sync(
						PluginLimitedRateGroup		group,
						boolean						is_disabled )
					{
						if ( closed ){

							group.removeListener( this );
						}
					}
				};
	}
	@Override
	public void
	addRateLimiter(
	  RateLimiter		limiter,
	  boolean			is_upload )
	{
		synchronized( this ){

			if ( closed ){

				return;
			}

			PluginLimitedRateGroup wrapped_limiter = UtilitiesImpl.wrapLimiter( limiter, true );

			if ( up_rg_listener == null ){

				createRGListeners();
			}

			if ( is_upload ){

				wrapped_limiter.addListener( up_rg_listener );

			}else{

				wrapped_limiter.addListener( down_rg_listener );
			}

			delegate.addRateLimiter( wrapped_limiter, is_upload );
		}
	}

	@Override
	public void
	removeRateLimiter(
	  RateLimiter		limiter,
	  boolean			is_upload )
	{
		synchronized( this ){

			PluginLimitedRateGroup wrapped_limiter = UtilitiesImpl.wrapLimiter( limiter, true );

			if ( up_rg_listener != null ){

				if ( is_upload ){

					wrapped_limiter.removeListener( up_rg_listener );

				}else{

					wrapped_limiter.removeListener( down_rg_listener );
				}
			}

			delegate.removeRateLimiter(wrapped_limiter, is_upload );
		}
	}

	@Override
	public RateLimiter[]
	getRateLimiters(
		boolean	is_upload )
	{
		LimitedRateGroup[] limiters = delegate.getRateLimiters( is_upload );

		RateLimiter[]	result = new RateLimiter[limiters.length];

		int	pos = 0;

		for ( LimitedRateGroup l: limiters ){

			if ( l instanceof PluginLimitedRateGroup  ){

				result[pos++] = UtilitiesImpl.unwrapLmiter((PluginLimitedRateGroup)l);
			}
		}

		if ( pos == result.length ){

			return( result );
		}

		RateLimiter[]	result_mod = new RateLimiter[pos];

		System.arraycopy( result, 0, result_mod, 0, pos );

		return( result_mod );
	}

	@Override
	public void
	close(
		String 		reason,
		int			reason_code,
		boolean 	closedOnError,
		boolean 	attemptReconnect )
	{
		manager.removePeer( this, reason, reason_code );
	}

	@Override
	public int
	readBytes(
		int	max )
	{
		throw( new RuntimeException( "not supported"));
	}

	@Override
	public int
	writeBytes(
		int	max )
	{
		throw( new RuntimeException( "not supported"));
	}

	protected void
	closed()
	{
		synchronized( this ){

			closed	= true;

			if ( up_rg_listener != null ){

					// tidy up

				LimitedRateGroup[] limiters = delegate.getRateLimiters( true );

				for ( LimitedRateGroup l: limiters ){

					if ( l instanceof PluginLimitedRateGroup  ){

						((PluginLimitedRateGroup)l).removeListener( up_rg_listener );
					}

					delegate.removeRateLimiter( l,  true );
				}

				limiters = delegate.getRateLimiters( false );

				for ( LimitedRateGroup l: limiters ){

					if ( l instanceof PluginLimitedRateGroup  ){

						((PluginLimitedRateGroup)l).removeListener( down_rg_listener );
					}

					delegate.removeRateLimiter( l,  false );
				}
			}
		}

		if ( delegate instanceof PeerForeignDelegate ){

			((PeerForeignDelegate)delegate).stop();
		}
	}

	@Override
	public int[]
	getCurrentIncomingRequestProgress()
	{
		return( delegate.getCurrentIncomingRequestProgress());
	}

	@Override
	public int[]
	getCurrentOutgoingRequestProgress()
	{
		return( delegate.getCurrentOutgoingRequestProgress());
	}


	@Override
	public void
	addListener(
		final PeerListener2	l )
	{
		PEPeerListener core_listener =
			new PEPeerListener()
			{
				@Override
				public void
				stateChanged(
					final PEPeer peer,	// seems don't need this here
					int new_state )
				{
					fireEvent( PeerEvent.ET_STATE_CHANGED, new Integer( new_state ));
				}

				@Override
				public void
				sentBadChunk(
					final PEPeer peer,	// seems don't need this here
					int piece_num,
					int total_bad_chunks )
				{
					fireEvent( PeerEvent.ET_BAD_CHUNK, new Integer[]{ new Integer(piece_num), new Integer(total_bad_chunks)});
				}

				@Override
				public void addAvailability(final PEPeer peer, BitFlags peerHavePieces)
				{
					fireEvent( PeerEvent.ET_ADD_AVAILABILITY,peerHavePieces.flags );
				}

				@Override
				public void removeAvailability(final PEPeer peer, BitFlags peerHavePieces)
				{
					fireEvent( PeerEvent.ET_REMOVE_AVAILABILITY,peerHavePieces.flags );
				}
				protected void
				fireEvent(
					final int		type,
					final Object	data )
				{
					try{
						l.eventOccurred(
							new PeerEvent()
							{
								@Override
								public int getType(){ return( type );}
								@Override
								public Object getData(){ return( data );}
							});
					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
			};

		delegate.addListener( core_listener );

		synchronized( this ){

			if ( peer_listeners == null ){

				peer_listeners = new HashMap<>();
			}

			peer_listeners.put( l, core_listener );
		}
	}


	@Override
	public void
	removeListener(
		PeerListener2	l )
	{
		PEPeerListener core_listener = null;

		synchronized( this ){

			if ( peer_listeners != null ){

				core_listener = peer_listeners.remove( l );
			}
		}

		if ( core_listener != null ) {

			delegate.removeListener( core_listener );
		}
	}

	@Override
	public boolean
	isPriorityConnection()
	{
		return( delegate.isPriorityConnection());
	}

	@Override
	public void
	setPriorityConnection(
		boolean is_priority )
	{
		delegate.setPriorityConnection ( is_priority );
	}

	@Override
	public void
	setUserData(
		Object		key,
		Object		value )
	{
		delegate.setUserData( key, value );
	}

	@Override
	public Object
	getUserData(
		Object	key )
	{
		return( delegate.getUserData( key ));
	}

		// as we don't maintain a 1-1 mapping between these and delegates make sure
		// that "equals" etc works sensibly

	public boolean
	equals(
		Object	other )
	{
		if ( other instanceof PeerImpl ){

			return( delegate == ((PeerImpl)other).delegate );
		}

		return( false );
	}

	public int
	hashCode()
	{
		return( delegate.hashCode());
	}

	/** Core use only.  This is not propogated to the plugin interface
	 *
	 * @return PEPeer object associated with the plugin Peer object
	 */
	public PEPeer getPEPeer() {
		return delegate;
	}

  // Pass LogRelation off to core objects

	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#getLogRelationText()
	 */
	@Override
	public String getRelationText() {
		return propogatedRelationText(delegate);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#getQueryableInterfaces()
	 */
	@Override
	public Object[] getQueryableInterfaces() {
		return new Object[] { delegate };
	}

	@Override
	public byte[] getHandshakeReservedBytes() {
		return delegate.getHandshakeReservedBytes();
	}
}
