/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.peer.impl.control;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerCheckRequestListener.HashListener;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.impl.PEPeerControlHashHandler;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.torrent.TOTorrentFileHashTree;
import com.biglybt.core.torrent.TOTorrentFileHashTree.HashRequest;
import com.biglybt.core.util.ByteArrayHashMap;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

public class 
PEPeerControlHashHandlerImpl
	implements PEPeerControlHashHandler
{
	private final PEPeerControlImpl		peer_manager;
	private final TOTorrent				torrent;
	private final DiskManager			disk_manager;
	
	private final ByteArrayHashMap<TOTorrentFileHashTree>		file_map;

	private Set<PeerHashRequest>		active_requests = new LinkedHashSet<>();
	
	private Map<PEPeerTransport,List<PeerHashRequest>>	peer_requests	 = new HashMap<>();
	
	private PeerHashRequest[][]		piece_requests;
	
	private AtomicInteger	piece_hashes_received = new AtomicInteger();
	
	private boolean			save_done_on_complete;
	
	private final Set<TOTorrentFileHashTree>					incomplete_trees 		= new HashSet<>();
	private final Map<TOTorrentFileHashTree, PeerHashRequest>	incomplete_tree_reqs 	= new HashMap<>();
	
	
	public 
	PEPeerControlHashHandlerImpl(
		PEPeerControlImpl	_peer_manager,
		TOTorrent			_torrent,
		DiskManager			_disk_manager )
	{
		peer_manager	= _peer_manager;
		torrent			= _torrent;
		disk_manager	= _disk_manager;
		
		file_map = new ByteArrayHashMap<TOTorrentFileHashTree>();
		
		for ( TOTorrentFile file: torrent.getFiles()){
			
			TOTorrentFileHashTree hash_tree = file.getHashTree();
			
			if ( hash_tree != null ){
			
				file_map.put( hash_tree.getRootHash(),  hash_tree );
				
				if ( !hash_tree.isPieceLayerComplete()){
					
					incomplete_trees.add( hash_tree );
				}
			}
		}
	}
	
	@Override
	public void 
	stop()
	{
		if ( piece_hashes_received.get() > 0 ){
			
			peer_manager.getAdapter().saveTorrentState();
		}
	}
	
	@Override
	public void 
	update()
	{	
		if ( !save_done_on_complete && disk_manager.getRemaining() == 0 && piece_hashes_received.get() > 0 ){
			
			save_done_on_complete = true;
			
			peer_manager.getAdapter().saveTorrentState();
		}
				
		long now = SystemTime.getMonotonousTime();
			
		List<PeerHashRequest>	expired = new ArrayList<>();
		List<PeerHashRequest>	retry	= new ArrayList<>();
		
		synchronized( peer_requests ){
			
			{
				Iterator<PeerHashRequest>	request_it = active_requests.iterator();
				
				while( request_it.hasNext()){
					
					PeerHashRequest peer_request = request_it.next();
					
					boolean remove = false;
					
					long age = now - peer_request.getCreateTime();
					
					if ( age > 10*1000 ){
								
						expired.add( peer_request );
						
						remove = true;
						
					}else if ( age > 5*1000 ){
						
						if ( peer_request.getPeer().getPeerState() != PEPeer.TRANSFERING ){
													
							retry.add( peer_request );
							
							remove = true;
						}
					}
					
					if ( remove ){
						
						request_it.remove();
						
						PEPeerTransport peer = peer_request.getPeer();
						
						List<PeerHashRequest> peer_reqs = peer_requests.get( peer );
	
						if ( peer_reqs == null ){
							
							Debug.out( "entry not found" );
							
						}else{
							
							peer_reqs.remove( peer_request );
							
							if ( peer_reqs.isEmpty()){
								
								peer_requests.remove( peer );
							}
						}
						
						removeFromPieceRequests( peer_request );
						
						peer_request.setComplete();
						
					}else{
						
						break;
					}
				}
			}
			
			if ( incomplete_trees.isEmpty()){
				
				if ( !save_done_on_complete && piece_hashes_received.get() > 0 ){
					
					save_done_on_complete = true;
					
					peer_manager.getAdapter().saveTorrentState();
				}
			}else{
				
				byte[][] pieces = null;
	
				int[]		peer_availability 	= null;
				boolean		has_seeds			= false;
				
				Iterator<PeerHashRequest>	request_it = incomplete_tree_reqs.values().iterator();
				
				while( request_it.hasNext()){
					
					PeerHashRequest peer_request = request_it.next();
					
					if ( peer_request.isComplete()){
						
						request_it.remove();
					}
				}
				
				Iterator<TOTorrentFileHashTree> tree_it = incomplete_trees.iterator();
				
				while( tree_it.hasNext()){
					
					if ( incomplete_tree_reqs.size() >= 10 ){
						
						break;
					}
					
					TOTorrentFileHashTree tree = tree_it.next();
					
					PeerHashRequest peer_request = incomplete_tree_reqs.get( tree );
											
					if ( peer_request == null ){
						
						if ( tree.isPieceLayerComplete()){
							
							tree_it.remove();
							
						}else{
							
							if (  pieces == null ){
								
								try{
									pieces = torrent.getPieces();
									
								}catch( Throwable e ){
									
									break;
								}
							}
							
							TOTorrentFile file = tree.getFile();
							
							int start 	= file.getFirstPieceNumber();
							int end		= file.getLastPieceNumber();
							
							for ( int i=start; i<=end; i++ ){
								
								if ( pieces[i] == null ){
									
									if ( peer_availability == null ){
											
										PiecePicker piece_picker = peer_manager.getPiecePicker();
										
										if ( piece_picker.getMinAvailability() >= 1 ){
											
											has_seeds = true;
											
										}else{
										
											peer_availability = piece_picker.getAvailability();
										}
									}
									
									if ( has_seeds || peer_availability[i] >= 1 ){
									
										PeerHashRequest req = hashRequestSupport( i, null );
										
										if ( req != null ){
											
											incomplete_tree_reqs.put( tree,  req );
											
											break;
										}
									}
								}
							}
						}
					}
				}
			}
			
			System.out.println( "Active requests: " + active_requests.size() + ", peers=" + peer_requests + ", piece_req=" + piece_requests + ", incomplete tree req=" + incomplete_tree_reqs);
		}
		
		for ( PeerHashRequest peer_request: retry ){

			List<HashListener> listeners =  peer_request.getListeners();
			
			if ( listeners != null ){
				
				for ( HashListener l: listeners ){
					
					if ( !hashRequest( l.getPieceNumber(), l )){
						
						l.complete( false );
					}
				}
			}
		}
				
		for ( PeerHashRequest peer_request: expired ){
			
			List<HashListener> listeners =  peer_request.getListeners();
			
			if ( listeners != null ){
				
				for ( HashListener l: listeners ){
					
					try{
						l.complete( false );
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			}
			
		}
	}
	
	private PeerHashRequest
	request(
		PEPeerTransport		peer,
		int					piece_number,
		HashListener		listener_maybe_null )
	{
		TOTorrentFileHashTree.HashRequest hash_req;
		
		TOTorrentFile file = disk_manager.getPieceList( piece_number ).get(0).getFile().getTorrentFile();
		
		TOTorrentFileHashTree tree = file.getHashTree();

		if ( tree == null ){
			
			return( null );
		}
			
		PeerHashRequest	peer_request;
		
		synchronized( peer_requests ){
			
			if ( piece_requests != null && piece_requests[piece_number] != null ){
				
				if ( listener_maybe_null != null ){
				
					piece_requests[piece_number][0].addListener( listener_maybe_null );	// add listener to any entry, doesn't matter which
				}
				
				return( piece_requests[piece_number][0] );
			}
							
			hash_req = tree.requestPieceHash( piece_number, peer.getAvailable());
			
			if ( hash_req == null ){
				
				return( null );
			}
								
			if ( active_requests.size() > 1024 ){
					
				Debug.out( "Too many active hash requests" );
					
				return( null );
			}
				
			peer_request = new PeerHashRequest( peer, file, hash_req, listener_maybe_null );

			active_requests.add( peer_request );
			
			List<PeerHashRequest> peer_reqs = peer_requests.get( peer );
			
			if ( peer_reqs == null ){
				
				peer_reqs = new ArrayList<>();
				
				peer_requests.put( peer, peer_reqs );
			}
			
			peer_reqs.add( peer_request );
			
			if ( piece_requests == null ){
				
				piece_requests = new PeerHashRequest[torrent.getNumberOfPieces()][];
			}
			
			int	offset		= hash_req.getOffset() + file.getFirstPieceNumber();
			int length		= hash_req.getLength();
			
			PeerHashRequest[] pr = new PeerHashRequest[]{ peer_request };
			
			int pos 	= offset;
			int end		= offset + length;
			
			while( pos < end && pos < piece_requests.length ){
				
				PeerHashRequest[] existing = piece_requests[pos];

				if ( existing == null ){
					
					piece_requests[pos] = pr;		// usual case
					
				}else{
					
					int len = existing.length;
					
					PeerHashRequest[] temp = new PeerHashRequest[len + 1];
					
					for ( int i=0;i<len;i++){
						
						temp[i] = existing[i];
					}
					
					temp[len] = peer_request;
					
					piece_requests[pos] = temp;
				}
			
				pos++;
			}	
		}
			
		peer.sendHashRequest( hash_req );
			
		return( peer_request );
	}
	
	@Override
	public boolean 
	hashRequest(
		int 			piece_number, 
		HashListener 	listener)
	{
			// general request for hash, e.g. piece is about to be checked and hash still missing
			// OR re-request after peer failure
		
		return( hashRequestSupport( piece_number, listener ) != null );
	}
	

	private PeerHashRequest 
	hashRequestSupport(
		int 			piece_number, 
		HashListener 	listener)
	{
		List<PEPeer>	peers = peer_manager.getPeers();
		
		PEPeer 	best_peer 		= null;
		int		best_req_count	= Integer.MAX_VALUE;
		
		for ( PEPeer peer: peers ){
			
			if ( peer.getPeerState() != PEPeer.TRANSFERING ){
				
				continue;
			}
			
			BitFlags avail = peer.getAvailable();
			
			if ( avail != null && avail.flags[ piece_number ] ){
			
				int req_count = peer.getOutgoingRequestCount();
				
				if ( req_count == 0 ){
					
					PeerHashRequest res = request( (PEPeerTransport)peer, piece_number, listener );
					
					if ( res != null ){
						
						return( res );
					}
				}else{
					
					if ( req_count < best_req_count ){
						
						best_peer 		= peer;
						best_req_count	= req_count;
					}
				}
			}
		}
		
		if ( best_peer != null ){
			
			PeerHashRequest res = request( (PEPeerTransport)best_peer, piece_number, listener );
			
			return( res );
			
		}else{
		
			return( null );
		}
	}
	
	@Override
	public void
	sendingRequest(
		PEPeerTransport				peer,
		DiskManagerReadRequest		request )
	{
			// peer is about to send a piece request, schedule hash grab if needed
		
		int piece_number = request.getPieceNumber();
					
		try{
			if ( torrent.getPieces()[ piece_number ] == null ){
		
				request( peer, piece_number, null ); 
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	@Override
	public void 
	receivedHashes(
		PEPeerTransport peer, 
		byte[]			root_hash, 
		int 			base_layer, 
		int 			index, 
		int 			length,
		int 			proof_layers, 
		byte[][] 		hashes)
	{
		receivedOrRejectedHashes( peer, root_hash, base_layer, index, length, proof_layers, hashes );
		
		piece_hashes_received.addAndGet( length );
	}
	
	private void
	receivedOrRejectedHashes(
		PEPeerTransport peer, 
		byte[]			root_hash, 
		int 			base_layer, 
		int 			index, 
		int 			length,
		int 			proof_layers, 
		byte[][] 		hashes )		// null if rejected
	{
		try{
			TOTorrentFileHashTree tree = file_map.get( root_hash );

			if ( tree != null ){
			
				if ( hashes != null ){
				
					tree.receivedHashes( root_hash, base_layer,	index, length, proof_layers, hashes );
				}
				
				List<HashListener>	listeners = null;
				
				synchronized( peer_requests ){
					
					List<PeerHashRequest> peer_reqs = peer_requests.get( peer );

					if ( peer_reqs != null ){
						
						Iterator<PeerHashRequest>	it = peer_reqs.iterator();
						
						PeerHashRequest match = null;
						
						while( it.hasNext()){
							
							PeerHashRequest peer_request = it.next();
							
							HashRequest req = peer_request.getRequest();
							
							if ( 	Arrays.equals( root_hash, req.getRootHash()) &&
									base_layer 		== req.getBaseLayer() &&
									index			== req.getOffset() &&
									length			== req.getLength() &&
									proof_layers	== req.getProofLayers()){
								
								match = peer_request;
								
								it.remove();
								
								break;
							}
						}
						
						if ( match != null ){
							
							if ( peer_reqs.isEmpty()){
								
								peer_requests.remove( peer );
							}
							
							if ( !active_requests.remove( match )){
								
								Debug.out( "entry not found" );
							}
							
							removeFromPieceRequests( match );							
							
							match.setComplete();
							
							listeners = match.getListeners();
						}
					}
				}
				
				if ( listeners != null ){
				
					for ( HashListener l: listeners ){
						
						try{
							l.complete( hashes != null );
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	private void
	removeFromPieceRequests(
		PeerHashRequest		peer_request )
	{
		if ( active_requests.isEmpty()){
			
			piece_requests = null;
			
		}else{
			HashRequest req = peer_request.getRequest();
			
			int	offset		= req.getOffset() + peer_request.getFile().getFirstPieceNumber();
											
			int pos 	= offset;
			int end		= offset + req.getLength();
			
			while( pos < end && pos < piece_requests.length ){
				
				PeerHashRequest[] existing = piece_requests[pos];
				
				if ( existing == null ){
					
					Debug.out( "entry not found" );
					
				}else if ( existing.length == 1 ){
					
					if ( existing[0] != peer_request ){
						
						Debug.out( "entry not found" );
						
					}else{
						
						piece_requests[pos] = null;
					}
				}else{
					
					PeerHashRequest[] temp = new PeerHashRequest[existing.length-1];
					
					int		temp_pos 	= 0;
					boolean found 		= false;
					
					for ( PeerHashRequest pr: existing ){
						
						if ( pr == peer_request ){
							
							found = true;
							
						}else{
							
							if ( temp_pos == temp.length ){
								
								break;
								
							}else{
								
								temp[temp_pos++] = pr;
							}
						}
					}
					
					if ( found ){
						
						piece_requests[pos] = temp;
						
					}else{
						
						Debug.out( "entry not found" );
					}
				}
				
				pos++;
			}
		}
	}
	
	@Override
	public void 
	rejectedHashes(
		PEPeerTransport peer, 
		byte[]			root_hash, 
		int 			base_layer, 
		int 			index, 
		int 			length,
		int 			proof_layers )
	{
		receivedOrRejectedHashes( peer, root_hash, base_layer, index, length, proof_layers, null );
	}
	
	
	@Override
	public byte[][]
	receivedHashRequest(
		PEPeerTransport		peer,
		byte[]				root_hash,
		int					base_layer,
		int					index,
		int					length,
		int					proof_layers )
	{
			// TODO rate limit?
		
		try{
			TOTorrentFileHashTree tree = file_map.get( root_hash );

			if ( tree != null ){
			
				return( tree.requestHashes( root_hash, base_layer,	index, length, proof_layers ));
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		return( null );
	}
	
	private static class
	PeerHashRequest	
	{
		final PEPeerTransport		peer;
		final TOTorrentFile			torrent_file;
		final HashRequest			request;
		
		final long		time = SystemTime.getMonotonousTime();
		
		List<HashListener>		listeners;
		
		boolean	completed;
		
		private
		PeerHashRequest(
			PEPeerTransport		_peer,
			TOTorrentFile		_tf,
			HashRequest			_request,
			HashListener		_listener )
		{
			peer			= _peer;
			torrent_file	= _tf;
			request			= _request;
			
			if ( _listener != null ){
				
				listeners = new ArrayList<>(1);
				
				listeners.add( _listener );
			}
		}
		
		private long
		getCreateTime()
		{
			return( time );
		}
		
		private TOTorrentFile
		getFile()
		{
			return( torrent_file );
		}
		
		private PEPeerTransport
		getPeer()
		{
			return( peer );
		}
		
		private HashRequest
		getRequest()
		{
			return( request );
		}
		
		private void
		addListener(
			HashListener		listener )
		{
			if ( listeners == null ){
				
				listeners = new ArrayList<>(1);
			}
			
			listeners.add( listener );
		}
		
		private List<HashListener>	
		getListeners()
		{
			return( listeners );
		}
		
		private void
		setComplete()
		{
			completed = true;
		}
		
		private boolean
		isComplete()
		{
			return( completed );
		}
	}
}
