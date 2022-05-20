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

import java.net.URL;
import java.util.*;

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.messaging.MessageStreamEncoder;
import com.biglybt.pif.network.*;
import com.biglybt.pif.peers.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.utils.Monitor;
import com.biglybt.pif.utils.PooledByteBuffer;


public class
ExternalSeedPeer
	implements Peer, ExternalSeedReaderListener
{
	private ExternalSeedPlugin		plugin;

	private Download				download;
	private PeerManager				manager;
	private ConnectionStub			connection_stub;
	private PeerStats				stats;
	private Map						user_data;

	private	ExternalSeedReader		reader;

	private int						state;

	private byte[]					peer_id;
	private boolean[]				available;
	private boolean					availabilityAdded;
	private long					snubbed;
	private boolean					is_optimistic;

	private Monitor					connection_mon;
	private boolean					peer_added;

	private List<PeerReadRequest>					request_list = new ArrayList<>();

	private CopyOnWriteList<PeerListener2>			listeners;
	private Monitor					listeners_mon;

	private boolean					doing_allocations;

	private final ESConnection	connection = new ESConnection();


	protected
	ExternalSeedPeer(
		ExternalSeedPlugin		_plugin,
		Download				_download,
		ExternalSeedReader		_reader )
	{
		plugin		= _plugin;
		download	= _download;
		reader		= _reader;

		connection_mon	= plugin.getPluginInterface().getUtilities().getMonitor();

		Torrent	torrent = reader.getTorrent();

		available	= new boolean[(int)torrent.getPieceCount()];

		Arrays.fill( available, true );

		peer_id	= new byte[20];

		new Random().nextBytes( peer_id );

		peer_id[0]='E';
		peer_id[1]='x';
		peer_id[2]='t';
		peer_id[3]=' ';

		listeners = new CopyOnWriteList();
		listeners_mon	= plugin.getPluginInterface().getUtilities().getMonitor();

		_reader.addListener( this );
	}

	protected boolean
	sameAs(
		ExternalSeedPeer	other )
	{
		return( reader.sameAs( other.reader ));
	}

	protected void
	setManager(
		PeerManager	_manager )
	{
		setState(Peer.CONNECTING);

		try{
			connection_mon.enter();

			manager	= _manager;

			if ( manager == null ){

				stats = null;

			}else{

				stats = manager.createPeerStats( this );
			}

			checkConnection();

		}finally{

			connection_mon.exit();
		}
	}

	@Override
	public PeerManager
	getManager()
	{
		return( manager );
	}

	protected Download
	getDownload()
	{
		return( download );
	}

	@Override
	public boolean 
	isMyPeer()
	{
		return false;
	}

	@Override
	public void
	bindConnection(
		ConnectionStub		stub )
	{
		connection_stub	= stub;
	}

	protected ExternalSeedReader
	getReader()
	{
		return( reader );
	}

	protected void
	setState(
		int newState )
	{
		state	= newState;

		fireEvent( PeerEvent.ET_STATE_CHANGED, new Integer( newState ));
	}

	protected boolean
	checkConnection()
	{
		boolean	state_changed = false;

		try{
			connection_mon.enter();

			boolean	active = reader.checkActivation( manager, this );

			if ( manager != null && active != peer_added ){

				state_changed	= true;

				boolean	peer_was_added	= peer_added;

				peer_added	= active;

				if ( active ){

					addPeer();

				}else{

					if ( peer_was_added ){

						removePeer();
					}
				}
			}
		}finally{

			connection_mon.exit();
		}

		return( state_changed );
	}

	protected void
	addPeer()
	{
		setState(Peer.HANDSHAKING);

		manager.addPeer( this );

			// we can get synchronously disconnected - e.g. IP filter rules

		if ( peer_added ){

			setState(Peer.TRANSFERING);

			try{
				listeners_mon.enter();

				if ( availabilityAdded ){

					Debug.out( "availabililty already added" );

				}else{

					availabilityAdded	= true;

					fireEvent( PeerEvent.ET_ADD_AVAILABILITY, getAvailable());
				}

			}finally{

				listeners_mon.exit();
			}
		}
	}

	protected void
	removePeer()
	{
		setState(Peer.CLOSING);

		try{
			listeners_mon.enter();

			if ( availabilityAdded ){

				availabilityAdded	= false;

				fireEvent( PeerEvent.ET_REMOVE_AVAILABILITY, getAvailable());
			}
		}finally{

			listeners_mon.exit();
		}

		manager.removePeer( this );

		setState( Peer.DISCONNECTED );
	}

	@Override
	public void
	requestComplete(
		PeerReadRequest		request,
		PooledByteBuffer	data )
	{
		PeerManager	man = manager;

		if ( request.isCancelled() || man == null ){

			data.returnToPool();

		}else{

			try{
				man.requestComplete( request, data, this );

				// moved to the rate-limiting code for more accurate stats
				// stats.received( request.getLength());

			}catch( Throwable e ){

				data.returnToPool();

				e.printStackTrace();
			}
		}
	}

	@Override
	public void
	requestCancelled(
		PeerReadRequest		request )
	{
		PeerManager	man = manager;

		if ( man != null ){

			man.requestCancelled( request, this );
		}
	}

	@Override
	public void
	requestFailed(
		PeerReadRequest		request )
	{
		PeerManager	man = manager;

		if ( man != null ){

			man.requestCancelled( request, this );

			try{
				connection_mon.enter();

				if ( peer_added ){

					plugin.log( reader.getName() + " failed - " + reader.getStatus() + ", permanent = " + reader.isPermanentlyUnavailable());

					peer_added	= false;

					removePeer();
				}
			}finally{

				connection_mon.exit();
			}

			if ( reader.isTransient() && reader.isPermanentlyUnavailable()){

				plugin.removePeer( this );
			}
		}
	}

	@Override
	public int
	getState()
	{
		return state;
	}

	@Override
	public byte[]
	getId()
	{
		return( peer_id );
	}

	public URL
	getURL()
	{
		return( reader.getURL());
	}

	@Override
	public String
	getIp()
	{
		return( reader.getIP());
	}

	@Override
	public int
	getTCPListenPort()
	{
		return( 0 );
	}

	@Override
	public int
	getUDPListenPort()
	{
		return( 0 );
	}

	@Override
	public int
	getUDPNonDataListenPort()
	{
		return( 0 );
	}

	@Override
	public int
	getPort()
	{
		return( reader.getPort());
	}

	@Override
	public boolean isLANLocal() {
		return false;	// for the moment, could be smarter
	}

	@Override
	public void resetLANLocalStatus(){		
	}
	
	@Override
	public final boolean[]
	getAvailable()
	{
		return( available );
	}

	@Override
	public final boolean
	isPieceAvailable(
		int pieceNumber )
	{
		return( true );
	}

	@Override
	public boolean
	isTransferAvailable()
	{
		return( reader.isActive());
	}

	@Override
	public boolean isDownloadPossible()
	{
		return peer_added &&reader.isActive();
	}

	@Override
	public boolean
	isChoked()
	{
		return( false );
	}

	@Override
	public boolean
	isChoking()
	{
		return( false );
	}

	@Override
	public boolean
	isInterested()
	{
		return( false );
	}

	@Override
	public boolean
	isInteresting()
	{
		return( true );
	}

	@Override
	public boolean
	isSeed()
	{
		return( true );
	}

	@Override
	public boolean
	isSnubbed()
	{
		if ( snubbed != 0 ){

				// mindless snubbing control - if we have no outstanding requests then we
				// drop the snubbed status :)

			if ( reader.getRequestCount() == 0 ){

				snubbed = 0;
			}
		}

		return( snubbed != 0 );
	}

	@Override
	public long
	getSnubbedTime()
	{
		if ( !isSnubbed()){

			return 0;
		}

		final long now = plugin.getPluginInterface().getUtilities().getCurrentSystemTime();

		if ( now < snubbed ){

			snubbed = now - 26;	// odds are ...
		}

		return now - snubbed;
	}

	@Override
	public void
	setSnubbed(
		boolean b)
	{
		if (!b){

			snubbed = 0;

		}else if ( snubbed == 0 ){

			snubbed = plugin.getPluginInterface().getUtilities().getCurrentSystemTime();
		}
	}

	@Override
	public boolean
	isOptimisticUnchoke()
	{
		return( is_optimistic );
	}

	@Override
	public void
	setOptimisticUnchoke(
		boolean _is_optimistic )
	{
		is_optimistic	= _is_optimistic;
	}

	@Override
	public PeerStats
	getStats()
	{
		return( stats );
	}

	@Override
	public boolean
	isIncoming()
	{
		return( false );
	}

	@Override
	public int
	getPercentDoneInThousandNotation()
	{
		return( 1000 );
	}

	@Override
	public String
	getClient()
	{
		return( reader.getName());
	}

	@Override
	public List<PeerReadRequest>
	getExpiredRequests()
	{
		return( reader.getExpiredRequests());

	}

	@Override
	public List<PeerReadRequest>
	getRequests()
	{
		List<PeerReadRequest> requests = reader.getRequests();

		if ( request_list.size() > 0 ){

			try{
				requests.addAll( request_list );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( requests );
	}

	@Override
	public int
	getMaximumNumberOfRequests()
	{
		return( reader.getMaximumNumberOfRequests());
	}

	@Override
	public int
	getNumberOfRequests()
	{
		return( reader.getRequestCount() + request_list.size());
	}

	@Override
	public int[]
	getPriorityOffsets()
	{

		return( reader.getPriorityOffsets());
	}

	@Override
	public boolean
	requestAllocationStarts(
		int[]	base_priorities )
	{
		if ( doing_allocations ){

			Debug.out( "recursive allocations" );
		}

		doing_allocations	= true;

		if ( request_list.size() != 0 ){

			Debug.out( "req list must be empty" );
		}

		PeerManager	pm = manager;

		if ( pm != null ){

			reader.calculatePriorityOffsets( pm, base_priorities );
		}

		return( true );
	}

	@Override
	public void
	requestAllocationComplete()
	{
		reader.addRequests( request_list );

		request_list.clear();

		doing_allocations	= false;
	}

	@Override
	public boolean
	addRequest(
		PeerReadRequest	request )
	{
		if ( !doing_allocations ){

			Debug.out( "request added when not in allocation phase" );
		}

		if ( !request_list.contains( request )){

			request_list.add( request );

			snubbed = 0;
		}

		return( true );
	}

	@Override
	public void
	cancelRequest(
		PeerReadRequest	request )
	{
		reader.cancelRequest( request );
	}

	@Override
	public void
	close(
		String 		reason,
		boolean 	closedOnError,
		boolean 	attemptReconnect )
	{
		boolean	peer_was_added;

		try{
			connection_mon.enter();

			peer_was_added	= peer_added;

			reader.cancelAllRequests();

			reader.deactivate( reason );

			peer_added	= false;

			try{
				listeners_mon.enter();

				if ( availabilityAdded ){

					availabilityAdded	= false;

					fireEvent( PeerEvent.ET_REMOVE_AVAILABILITY, getAvailable());
				}
			}finally{

				listeners_mon.exit();
			}
		}finally{

			connection_mon.exit();
		}

		if ( peer_was_added ){

			manager.removePeer( this );
		}

		setState( Peer.DISCONNECTED );

		if ( reader.isTransient()){

			plugin.removePeer( this );
		}
	}

	public void
	remove()
	{
		plugin.removePeer( this );
	}

	@Override
	public void
	addListener(
		PeerListener2	listener )
	{
		try{
			listeners_mon.enter();

			listeners.add(listener);

		}finally{

			listeners_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		PeerListener2 listener )
	{
		try{
			listeners_mon.enter();

			listeners.remove(listener);

		}finally{

			listeners_mon.exit();
		}
	}

	protected void
	fireEvent(
		final int		type,
		final Object	data )
	{
		try{
			listeners_mon.enter();

			List<PeerListener2>	ref = listeners.getList();

			for (int i =0; i <ref.size(); i++){

				try{
					PeerListener2	 listener = ref.get(i);

					listener.eventOccurred(
						new PeerEvent()
						{
							@Override
							public int
							getType()
							{
								return( type );
							}

							@Override
							public Object
							getData()
							{
								return( data );
							}
						});
				}catch( Throwable e ){

					e.printStackTrace();
				}
			}
		}finally{

			listeners_mon.exit();
		}
	}
	@Override
	public Connection
	getConnection()
	{
		return( connection );
	}


	@Override
	public boolean
	supportsMessaging()
	{
		return( false );
	}

	@Override
	public Message[]
	getSupportedMessages()
	{
		return( new Message[0] );
	}

	@Override
	public int
	readBytes(
		int	max )
	{
		int	res = reader.readBytes( max );

		if ( res > 0 ){

			stats.received( res );
		}

		return( res );
	}

	@Override
	public int
	writeBytes(
		int	max )
	{
		throw( new RuntimeException( "Not supported" ));
	}

	@Override
	public void
	addRateLimiter(
		RateLimiter		limiter,
		boolean			is_upload )
	{
		if ( connection_stub != null ){

			connection_stub.addRateLimiter( limiter, is_upload );

		}else{

			Debug.out( "connection not bound" );
		}
	}

	@Override
	public void
	removeRateLimiter(
		RateLimiter		limiter,
		boolean			is_upload )
	{
		if ( connection_stub != null ){

			connection_stub.removeRateLimiter( limiter, is_upload );

		}else{

			Debug.out( "connection not bound" );
		}
	}

	@Override
	public RateLimiter[]
	getRateLimiters(
		boolean	is_upload )
	{
		if ( connection_stub != null ){

			return( connection_stub.getRateLimiters( is_upload ));

		}else{

			Debug.out( "connection not bound" );

			return( new RateLimiter[0] );
		}
	}

	@Override
	public int[]
	getCurrentIncomingRequestProgress()
	{
		return( reader.getCurrentIncomingRequestProgress());
	}

	@Override
	public int[]
	getOutgoingRequestedPieceNumbers()
	{
		return( reader.getOutgoingRequestedPieceNumbers());
	}

	@Override
	public int
	getOutgoingRequestCount()
	{
		return( reader.getOutgoingRequestCount());
	}


	@Override
	public int[]
	getCurrentOutgoingRequestProgress()
	{
		return( null );
	}

	public Map
	getProperties()
	{
		return( new HashMap());
	}

	public String
	getName()
	{
		return( reader.getName());
	}

	@Override
	public void
	setUserData(
		Object		key,
		Object		value )
	{
		if ( user_data == null ){

			user_data	= new HashMap();
		}

		user_data.put( key, value );
	}

	@Override
	public Object
	getUserData(
		Object	key )
	{
		if ( key == Peer.PR_PROTOCOL ){

			return( reader.getURL().getProtocol().toUpperCase());

		}else if ( key == Peer.PR_PROTOCOL_QUALIFIER ){

			return( reader.getType());
		}

		if ( user_data == null ){

			return( null );
		}

		return( user_data.get( key ));
	}

	@Override
	public byte[] getHandshakeReservedBytes() {
		return null;
	}

	@Override
	public boolean isPriorityConnection() {
		return false;
	}

	@Override
	public void setPriorityConnection(boolean is_priority) {
	}

	private class
	ESConnection
		implements Connection
	{
		private OutgoingMessageQueue out_q =
			new	OutgoingMessageQueue()
			{
				@Override
				public void setEncoder(MessageStreamEncoder encoder ){}

				@Override
				public void sendMessage(Message message ){}

				@Override
				public void registerListener(OutgoingMessageQueueListener listener ){}

				@Override
				public void deregisterListener(OutgoingMessageQueueListener listener ){}

				@Override
				public void notifyOfExternalSend(Message message ){}

				@Override
				public int[] getCurrentMessageProgress(){ return( null );}

				@Override
				public int getDataQueuedBytes(){ return( 0 ); }

				@Override
				public int getProtocolQueuedBytes(){ return( 0 ); }

				@Override
				public boolean isBlocked(){ return( false ); }
			};

		private IncomingMessageQueue in_q =
			new	IncomingMessageQueue()
			{
				@Override
				public void registerListener(IncomingMessageQueueListener listener ){}

				@Override
				public void registerPriorityListener(IncomingMessageQueueListener listener ){}

				@Override
				public void deregisterListener(IncomingMessageQueueListener listener ){}

				@Override
				public void notifyOfExternalReceive(Message message ){}

				@Override
				public int[] getCurrentMessageProgress(){ return( ExternalSeedPeer.this.getCurrentIncomingRequestProgress()); }
			};

		@Override
		public void
		connect(
			ConnectionListener listener )
		{
		}

		@Override
		public void
		close()
		{
			Debug.out( "hmm" );
		}

		@Override
		public OutgoingMessageQueue
		getOutgoingMessageQueue()
		{
			return( out_q );
		}

		@Override
		public IncomingMessageQueue
		getIncomingMessageQueue()
		{
			return( in_q );
		}

		@Override
		public void
		startMessageProcessing()
		{
		}

		@Override
		public Transport
		getTransport()
		{
			return( null );
		}

		@Override
		public boolean
		isIncoming()
		{
			return( false );
		}

		@Override
		public String
		getString()
		{
			return( "External Seed" );
		}
	}
}
