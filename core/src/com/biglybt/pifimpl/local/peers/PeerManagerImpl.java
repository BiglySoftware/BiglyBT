/*
 * File    : PeerManagerImpl.java
 * Created : 28-Dec-2003
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

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerListener;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.peer.*;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.disk.DiskManager;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.peers.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.disk.DiskManagerImpl;
import com.biglybt.pifimpl.local.download.DownloadManagerImpl;
import com.biglybt.pifimpl.local.utils.PooledByteBufferImpl;

public class
PeerManagerImpl
	implements PeerManager
{
	private static final String			PEPEER_DATA_KEY	= PeerManagerImpl.class.getName();
	private static final AtomicLong		PEPEER_DATA_KEY_AL = new AtomicLong();
	
	protected PEPeerManager	manager;

	protected static AEMonitor	pm_map_mon	= new AEMonitor( "PeerManager:Map" );

	public static PeerManagerImpl
	getPeerManager(
		PEPeerManager	_manager )
	{
		try{
			pm_map_mon.enter();

			PeerManagerImpl	res = (PeerManagerImpl)_manager.getData( "PluginPeerManager" );

			if ( res == null ){

				res = new PeerManagerImpl( _manager );

				_manager.setData( "PluginPeerManager", res );
			}

			return( res );
		}finally{

			pm_map_mon.exit();
		}
	}

	private Map<Peer,PeerForeignDelegate>		foreign_map		= new HashMap<>();

	private Map<PeerManagerListener2,CoreListener>	listener_map2 	= new HashMap<>();

	protected AEMonitor	this_mon	= new AEMonitor( "PeerManager" );

	private final DiskManagerPiece[]	dm_pieces;
	private final PEPiece[]				pe_pieces;
	private pieceFacade[]				piece_facades;

	private boolean	destroyed;

	protected
	PeerManagerImpl(
		PEPeerManager	_manager )
	{
		manager	= _manager;

		dm_pieces	= _manager.getDiskManager().getPieces();
		pe_pieces	= _manager.getPieces();

		manager.addListener(
			new PEPeerManagerListenerAdapter()
			{
				 @Override
				 public void
				 peerRemoved(
					PEPeerManager 	manager,
					PEPeer 			peer )
				 {
					 PeerImpl	dele = getPeerForPEPeer( peer );

					 if ( dele != null ){

						 dele.closed();
					 }
				 }

				 @Override
				 public void
				 destroyed(
					PEPeerManager	manager )
				 {
					 synchronized( foreign_map ){

						 destroyed	= true;

						 Iterator<PeerForeignDelegate> it = foreign_map.values().iterator();

						 while( it.hasNext()){

							 try{
								 it.next().stop();

							 }catch( Throwable e ){

								 Debug.printStackTrace( e );
							 }
						 }
					 }
				 }
			});
	}

	public PEPeerManager
	getDelegate()
	{
		return( manager );
	}

	@Override
	public DiskManager
	getDiskManager()
	{
		return( new DiskManagerImpl( manager.getDiskManager()));
	}

	@Override
	public PeerManagerStats
	getStats()
	{
		return(new PeerManagerStatsImpl( manager));
	}

	@Override
	public boolean
	isSeeding()
	{
		// this is the wrong thing to check for seeding..
		return( manager.getDiskManager().getRemainingExcludingDND() == 0 ); //yuck
	}

	@Override
	public boolean
	isSuperSeeding()
	{
		return( manager.isSuperSeedMode());
	}

	@Override
	public Download
	getDownload()

		throws DownloadException
	{
		return( DownloadManagerImpl.getDownloadStatic( manager.getDiskManager().getTorrent()));
	}

	@Override
	public Piece[]
	getPieces()
	{
		if ( piece_facades == null ){

			pieceFacade[]	pf = new pieceFacade[manager.getDiskManager().getNbPieces()];

			for (int i=0;i<pf.length;i++){

				pf[i] = new pieceFacade(i);
			}

			piece_facades	= pf;
		}

		return( piece_facades );
	}

	@Override
	public PeerStats
	createPeerStats(
		Peer	peer )
	{
		PEPeer	delegate = mapForeignPeer( peer );

		return( new PeerStatsImpl( this, peer, manager.createPeerStats( delegate )));
	}


	@Override
	public void
	requestComplete(
		PeerReadRequest		request,
		PooledByteBuffer 	data,
		Peer 				sender)
	{
		manager.writeBlock(
			request.getPieceNumber(),
			request.getOffset(),
			((PooledByteBufferImpl)data).getBuffer(),
			mapForeignPeer( sender ),
            false);

		PeerForeignDelegate	delegate = lookupForeignPeer( sender );

		if ( delegate != null ){

			delegate.dataReceived();
		}
	}

	@Override
	public void
	requestCancelled(
		PeerReadRequest		request,
		Peer				sender )
	{
		manager.requestCanceled((DiskManagerReadRequest)request );
	}

	protected int
	getPartitionID()
	{
		return( manager.getPartitionID());
	}

		// these are foreign peers

	@Override
	public void
	addPeer(
		Peer		peer )
	{
			// no private check here, we come through here for webseeds for example

		manager.addPeer(mapForeignPeer( peer ));
	}

	@Override
	public void
	removePeer(
		Peer		peer )
	{
		manager.removePeer(mapForeignPeer( peer ));
	}

	protected void
	removePeer(
		Peer		peer,
		String		reason )
	{
		manager.removePeer(mapForeignPeer( peer ), reason );
	}

	@Override
	public void
	addPeer(
		String 	ip_address,
		int 	tcp_port )
	{
		addPeer(
			ip_address,
			tcp_port,
			0,
			NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE ));
	}

	@Override
	public void
	addPeer(
		String 		ip_address,
		int 		tcp_port,
		boolean 	use_crypto )
	{
		addPeer( ip_address, tcp_port, 0, use_crypto );
	}

	@Override
	public void
	addPeer(
		String 		ip_address,
		int 		tcp_port,
		int			udp_port,
		boolean 	use_crypto )
	{
		addPeer( ip_address, tcp_port, udp_port, use_crypto, null );
	}

	@Override
	public void
	addPeer(
		String 		ip_address,
		int 		tcp_port,
		int			udp_port,
		boolean 	use_crypto,
		Map			user_data )
	{
		checkIfPrivate();

		if ( pluginPeerSourceEnabled()){

			manager.addPeer( ip_address, tcp_port, udp_port, use_crypto, user_data );
		}
	}

	@Override
	public void
	peerDiscovered(
		String		peer_source,
		String 		ip_address,
		int 		tcp_port,
		int			udp_port,
		boolean 	use_crypto )
	{
		checkIfPrivate();

		if ( manager.isPeerSourceEnabled( peer_source )){

			manager.peerDiscovered( peer_source, ip_address, tcp_port, udp_port, use_crypto );
		}
	}

	protected boolean
	pluginPeerSourceEnabled()
	{
		if ( manager.isPeerSourceEnabled( PEPeerSource.PS_PLUGIN )){

			return( true );

		}else{

			Debug.out( "Plugin peer source disabled for " + manager.getDisplayName());

			return( false );
		}
	}

	protected void
	checkIfPrivate()
	{
		Download dl;

		try{
			dl = getDownload();

		}catch( Throwable e ){

			// if this didn't work then nothing much else will so just fall through

			return;
		}

		Torrent t = dl.getTorrent();

		if ( t != null ){

			if (  TorrentUtils.isReallyPrivate( PluginCoreUtils.unwrap( t ))){

				throw( new RuntimeException( "Torrent is private, peer addition not permitted" ));
			}
		}
	}

	@Override
	public Peer[]
	getPeers()
	{
		List	l = manager.getPeers();

		Peer[]	res= new Peer[l.size()];

			// this is all a bit shagged as we should maintain the PEPeer -> Peer link rather
			// than continually creating new PeerImpls...

		for (int i=0;i<res.length;i++){

			res[i] = getPeerForPEPeer((PEPeer)l.get(i));
		}

		return( res );
	}

	@Override
	public Peer[]
	getPeers(
		String		address )
	{
		List	l = manager.getPeers( address );

		Peer[]	res= new Peer[l.size()];

			// this is all a bit shagged as we should maintain the PEPeer -> Peer link rather
			// than continually creating new PeerImpls...

		for (int i=0;i<res.length;i++){

			res[i] = getPeerForPEPeer((PEPeer)l.get(i));
		}

		return( res );
	}


	@Override
	public PeerDescriptor[]
  	getPendingPeers()
  	{
  		return( manager.getPendingPeers());
  	}

	@Override
	public PeerDescriptor[]
	getPendingPeers(
		String		address )
	{
		return( manager.getPendingPeers( address ));
	}

	public long
	getTimeSinceConnectionEstablished(
		Peer		peer )
	{
		if ( peer instanceof PeerImpl ){

			return(((PeerImpl)peer).getDelegate().getTimeSinceConnectionEstablished());
		}else{
			PeerForeignDelegate	delegate = lookupForeignPeer( peer );

			if ( delegate != null ){

				return( delegate.getTimeSinceConnectionEstablished());

			}else{

				return( 0 );
			}
		}
	}
	public PEPeer
	mapForeignPeer(
		Peer	_foreign )
	{
		if ( _foreign instanceof PeerImpl ){

			return(((PeerImpl)_foreign).getDelegate());
		}

		synchronized( foreign_map ){

			PeerForeignDelegate	local = foreign_map.get( _foreign );

			if ( local != null && local.isClosed()){

				foreign_map.remove( _foreign );

				local = null;
			}

			if( local == null ){

				if ( destroyed ){

					Debug.out( "Peer added to destroyed peer manager" );

					return( null );
				}

				local 	= new PeerForeignDelegate( this, _foreign );

				_foreign.setUserData( PeerManagerImpl.class, local );

				foreign_map.put( _foreign, local );
			}

			return( local );
		}
	}

	protected PeerForeignDelegate
	lookupForeignPeer(
		Peer	_foreign )
	{
		return((PeerForeignDelegate)_foreign.getUserData( PeerManagerImpl.class ));
	}

	public List<PEPeer>
	mapForeignPeers(
		Peer[]	_foreigns )
	{
		List<PEPeer>	res = new ArrayList<>();

		for (int i=0;i<_foreigns.length;i++){

			PEPeer	local = mapForeignPeer( _foreigns[i]);

				// could already be there if torrent contains two identical seeds (for whatever reason)

			if ( !res.contains( local )){

				res.add( local );
			}
		}

		return( res );
	}

	public static PeerImpl
	getPeerForPEPeer(
		PEPeer	pe_peer )
	{
		synchronized( PEPEER_DATA_KEY ){
			
			PeerImpl	peer = (PeerImpl)pe_peer.getData( PEPEER_DATA_KEY );
	
			if ( peer == null ){
	
				peer = new PeerImpl( pe_peer );
	
				pe_peer.setData( PEPEER_DATA_KEY, peer );
			}
	
			return( peer );
		}
	}

	@Override
	public int
	getUploadRateLimitBytesPerSecond()
	{
		return( manager.getUploadRateLimitBytesPerSecond());
	}

	@Override
	public int
	getDownloadRateLimitBytesPerSecond()
	{
		return( manager.getDownloadRateLimitBytesPerSecond());
	}

	@Override
	public void
	addListener(
		final PeerManagerListener2	l )
	{
		try{
			this_mon.enter();

			CoreListener core_listener = new CoreListener( l );

			listener_map2.put( l, core_listener );

			manager.addListener( core_listener );

			manager.getDiskManager().addListener( core_listener );
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		PeerManagerListener2	l )
	{
		try{
			this_mon.enter();

			CoreListener core_listener	= listener_map2.remove( l );

			if ( core_listener != null ){

				manager.removeListener( core_listener );

				manager.getDiskManager().removeListener( core_listener );
			}

		}finally{

			this_mon.exit();
		}
	}

	protected class
	pieceFacade
		implements Piece
	{
		private final int	index;

		protected
		pieceFacade(
			int		_index )
		{
			index	= _index;
		}

		@Override
		public int
		getIndex()
		{
			return( index );
		}

		@Override
		public int
		getLength()
		{
			return( dm_pieces[index].getLength());
		}

		@Override
		public boolean
		isDone()
		{
			return( dm_pieces[index].isDone());
		}

		@Override
		public boolean
		isNeeded()
		{
			return( dm_pieces[index].isNeeded());
		}

		@Override
		public boolean
		isDownloading()
		{
			return( pe_pieces[index] != null );
		}

		@Override
		public boolean
		isFullyAllocatable()
		{
			if ( pe_pieces[index] != null ){

				return( false );
			}

			return( dm_pieces[index].isInteresting());
		}

		@Override
		public int
		getAllocatableRequestCount()
		{
			PEPiece	pe_piece = pe_pieces[index];

			if ( pe_piece != null ){

				return( pe_piece.getNbUnrequested());
			}

			if ( dm_pieces[index].isInteresting() ){

				return( dm_pieces[index].getNbBlocks());
			}

			return( 0 );
		}

		@Override
		public Peer
		getReservedFor()
		{
			PEPiece piece = pe_pieces[index];

			if ( piece != null ){

				String ip = piece.getReservedBy();

				if ( ip != null ){

					List<PEPeer> peers = manager.getPeers( ip );

					if ( peers.size() > 0 ){

						return( getPeerForPEPeer( peers.get(0)));
					}
				}
			}

			return( null );
		}

		@Override
		public void
		setReservedFor(
			Peer	peer )
		{
			PEPiece piece = pe_pieces[index];

			PEPeer mapped_peer = mapForeignPeer( peer );

			if ( piece != null && mapped_peer != null ){

				piece.setReservedBy( peer.getIp());

				mapped_peer.addReservedPieceNumber( index );
			}
		}
	}

	private class
	CoreListener
		implements PEPeerManagerListener, DiskManagerListener
	{
		private final String	CL_KEY = PEPEER_DATA_KEY + "." + PEPEER_DATA_KEY_AL.incrementAndGet();
		
		private PeerManagerListener2		listener;

		private
		CoreListener(
			PeerManagerListener2		_listener )
		{
			listener	= _listener;
		}

		@Override
		public void
		peerAdded(
			PEPeerManager manager, PEPeer peer )
		{
			PeerImpl pi = getPeerForPEPeer( peer );

			boolean	fire = false;
			
			synchronized( CL_KEY ){
				
				if ( peer.getUserData( CL_KEY ) == null ){
				
					peer.setUserData( CL_KEY, 1 );
				
					fire = true;
				}
			}
			
			if ( fire ){
				
				fireEvent(
					PeerManagerEvent.ET_PEER_ADDED,
					pi,
					null,
					null );
			}
		}

		@Override
		public void
		peerRemoved(
			PEPeerManager manager,
			PEPeer peer )
		{
			PeerImpl pi = getPeerForPEPeer( peer );

			boolean	fire = false;

			synchronized( CL_KEY ){

				Integer i = (Integer)peer.getUserData( CL_KEY );
				
				if ( i != null && i == 1 ){
					
					peer.setUserData( CL_KEY, 2 );
					
					fire = true;
				}
			}
			
			if ( fire ){
				
				fireEvent(
					PeerManagerEvent.ET_PEER_REMOVED,
					pi,
					null,
					null );
			}
		}

		@Override
		public void
		peerDiscovered(
			PEPeerManager 	manager,
			PeerItem 		peer_item,
			PEPeer 			finder )
		{
			PeerImpl	pi;

			if ( finder != null ){

				pi = getPeerForPEPeer( finder );

			}else{

				pi = null;
			}

			fireEvent(
				PeerManagerEvent.ET_PEER_DISCOVERED,
				pi,
				peer_item,
				null );
		}

		@Override
		public void
		pieceAdded(
			PEPeerManager 	manager,
			PEPiece 		piece,
			PEPeer 			for_peer )
		{
			PeerImpl pi = for_peer==null?null:getPeerForPEPeer( for_peer );

			fireEvent(
					PeerManagerEvent.ET_PIECE_ACTIVATED,
					pi,
					null,
					new pieceFacade( piece.getPieceNumber()));
		}

		@Override
		public void
		pieceRemoved(
			PEPeerManager 	manager,
			PEPiece 		piece )
		{
			fireEvent(
					PeerManagerEvent.ET_PIECE_DEACTIVATED,
					null,
					null,
					new pieceFacade( piece.getPieceNumber()));
		}

		@Override
		public void 
		requestAdded(
			PEPeerManager 		manager, 
			PEPiece 			piece, 
			PEPeer				peer,
			PeerReadRequest 	request)
		{
		}
		
		@Override
		public void
		peerSentBadData(
			PEPeerManager 	manager,
			PEPeer 			peer,
			int 			pieceNumber)
		{
			PeerImpl pi = getPeerForPEPeer( peer );

			fireEvent(
				PeerManagerEvent.ET_PEER_SENT_BAD_DATA,
				pi,
				null,
				new Integer( pieceNumber ));

		}

		@Override
		public void
		pieceCorrupted(
			PEPeerManager 	manager,
			int 			piece_number)
		{
		}

			// disk manager methods

		@Override
		public void
		stateChanged(
			com.biglybt.core.disk.DiskManager	dm, 
			int 								oldState,
			int									newState )
		{
		}

		@Override
		public void
		filePriorityChanged(
			com.biglybt.core.disk.DiskManager 	dm, 
			DiskManagerFileInfo					file )
		{
		}

		@Override
		public void
		pieceDoneChanged(
			com.biglybt.core.disk.DiskManager 	dm, 
			DiskManagerPiece					piece )
		{
			fireEvent(
					PeerManagerEvent.ET_PIECE_COMPLETION_CHANGED,
					null,
					null,
					new pieceFacade( piece.getPieceNumber()));
		}

		protected void
		fireEvent(
			final int			type,
			final Peer			peer,
			final PeerItem		peer_item,
			final Object		data )
		{
			listener.eventOccurred(
				new PeerManagerEvent()
				{
					@Override
					public PeerManager
					getPeerManager()
					{
						return( PeerManagerImpl.this );
					}

					@Override
					public int
					getType()
					{
						return( type );
					}

					@Override
					public Peer
					getPeer()
					{
						return( peer );
					}

					@Override
					public PeerDescriptor
					getPeerDescriptor()
					{
						return( peer_item );
					}

					@Override
					public Object
					getData()
					{
						return( data );
					}
				});
		}


		@Override
		public void
		destroyed(
			PEPeerManager	manager )
		{
		}
	}
}
