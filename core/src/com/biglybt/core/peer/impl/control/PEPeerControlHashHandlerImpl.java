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

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.disk.DiskManagerReadRequestListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerCheckRequestListener.HashListener;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.impl.PEPeerControlHashHandler;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.torrent.TOTorrentFileHashTree;
import com.biglybt.core.torrent.TOTorrentFileHashTree.HashRequest;
import com.biglybt.core.torrent.TOTorrentFileHashTree.PieceTreeProvider;
import com.biglybt.core.torrent.TOTorrentFileHashTree.PieceTreeReceiver;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.ByteArrayHashMap;
import com.biglybt.core.util.ConcurrentHasher;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SHA256;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;

public class 
PEPeerControlHashHandlerImpl
	implements PEPeerControlHashHandler, PieceTreeProvider
{
	private static final Object	KEY_PEER_STATS = new Object();
	
	private final PEPeerControlImpl		peer_manager;
	private final TOTorrent				torrent;
	private final DiskManager			disk_manager;
	private final int					piece_length;
	
	private final ByteArrayHashMap<TOTorrentFileHashTree>		file_map;

	private long 						last_update	= SystemTime.getMonotonousTime();
	
	private Set<PeerHashRequest>		active_requests = new LinkedHashSet<>();
	
	private Map<PEPeerTransport,List<PeerHashRequest>>	peer_requests	 = new HashMap<>();
	
	private PeerHashRequest[][]		piece_requests;
	
	private AtomicInteger	piece_hashes_received = new AtomicInteger();
	
	private boolean			save_done_on_complete;
	
	private final Set<TOTorrentFileHashTree>					incomplete_trees 		= new HashSet<>();
	private final Map<TOTorrentFileHashTree, PeerHashRequest>	incomplete_tree_reqs 	= new HashMap<>();
	
	private final int PIECE_TREE_CACHE_MAX	= 20;
	
		// 4MB piece has 128 leaves totaling 4096 bytes, layer above 2048 etc -> total around 8k
	
	private final Map<Integer,byte[][]>	piece_tree_cache =
			new LinkedHashMap<Integer,byte[][]>(PIECE_TREE_CACHE_MAX,0.75f,true)
			{
				@Override
				protected boolean
				removeEldestEntry(
			   		Map.Entry<Integer,byte[][]> eldest)
				{
					return size() > PIECE_TREE_CACHE_MAX;
				}
			};

	private volatile long	last_piece_tree_request = -1;
		
	private final Map<Integer,PieceTreeRequest>		piece_tree_requests = new HashMap<>();
	
	public 
	PEPeerControlHashHandlerImpl(
		PEPeerControlImpl	_peer_manager,
		TOTorrent			_torrent,
		DiskManager			_disk_manager )
	{
		peer_manager	= _peer_manager;
		torrent			= _torrent;
		disk_manager	= _disk_manager;
		
		piece_length = disk_manager.getPieceLength();
		
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
		long now = SystemTime.getMonotonousTime();
		
		if ( !save_done_on_complete && disk_manager.getRemaining() == 0 && piece_hashes_received.get() > 0 ){
			
			save_done_on_complete = true;
			
			peer_manager.getAdapter().saveTorrentState();
		}
			
		if ( last_piece_tree_request > 0 && now - last_piece_tree_request > 60*1000 ){
			
			last_piece_tree_request = -1;
			
			synchronized( piece_tree_cache ){

				piece_tree_cache.clear();
			}
		}
		
		if ( now - last_update >= 30*1000 ){
			
			List<PieceTreeRequest>	expired = new ArrayList<>();
			
			synchronized( piece_tree_requests ){
				
				Iterator<PieceTreeRequest> it = piece_tree_requests.values().iterator();
				
				while( it.hasNext()){
					
					PieceTreeRequest req = it.next();
					
					if ( now - req.getCreateTime() > 30*1000 ){
						
						it.remove();
						
						expired.add( req );
					}
				}
			}
			
			for ( PieceTreeRequest req: expired ){
					
				Debug.out( "PieceTreeRequest expired, derp" );
				
				req.complete( null );
			}
		}
		
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
			
			// System.out.println( "Active requests: " + active_requests.size() + ", peers=" + peer_requests + ", piece_req=" + piece_requests + ", incomplete tree req=" + incomplete_tree_reqs);
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
		
		last_update = now;
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
								
			if ( active_requests.size() > 2048 ){
					
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
		// System.out.println( (hashes==null?"hash_reject":"hashes") + " received: " + base_layer + ", " + index + "," + length );
		
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
	public void
	receivedHashRequest(
		PEPeerTransport		peer,
		HashesReceiver		receiver,
		byte[]				root_hash,
		int					base_layer,
		int					index,
		int					length,
		int					proof_layers )
	{
		PeerStats stats = (PeerStats)peer.getUserData( KEY_PEER_STATS );

		if ( stats == null ){
			
			stats = new PeerStats( peer );
			
			peer.setUserData( KEY_PEER_STATS, stats );
		}
			
		// System.out.println( "Stats: " + stats.getString());
		
		try{
			TOTorrentFileHashTree tree = file_map.get( root_hash );

			if ( tree != null ){
			
				int	related_bytes;
				
				if ( base_layer == 0 ){
					
					related_bytes = 16*1024*length;			// leaf layer
					
				}else{
					
					related_bytes = piece_length * length;	// assume pieces layer
				}
				
				stats.hashesRequested( related_bytes );
				
					// this does rate limiting 
				
				stats.runTask( 
					()->{
						if ( tree.requestHashes( 
								this, 
								new HashesReceiverImpl( peer, receiver ), 
								root_hash, 
								base_layer, 
								index, 
								length, 
								proof_layers )){
							
								// request has been accepted and receiver will be informed of result
							
						}else{
							receiver.receiveResult( null );
						}});
				
				return;
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		receiver.receiveResult( null );
	}
	
	public void
	getPieceTree(
		PieceTreeReceiver			receiver,
		TOTorrentFileHashTree		tree,
		int 						piece_offset )
	{
		TOTorrentFile file = tree.getFile();
		
		int piece_number = file.getFirstPieceNumber() + piece_offset;
		
		if ( !disk_manager.isDone( piece_number )){
			
			receiver.receivePieceTree( piece_offset, null );
			
			return;
		}
		
		byte[][] existing;
		
		synchronized( piece_tree_cache ){
			
			existing = piece_tree_cache.get( piece_number );
		}
		
		if ( existing != null ){
			
			last_piece_tree_request = SystemTime.getMonotonousTime();
				
			receiver.receivePieceTree( piece_offset, existing );
			
			// System.out.println( "reusing hash tree for " + piece_number );
			
			return;
		}
		
		PieceTreeRequest piece_tree_request;
		
		synchronized( piece_tree_requests ){
			
			piece_tree_request = piece_tree_requests.get( piece_number );
			
			if ( piece_tree_request != null ){
				
				piece_tree_request.addListener( receiver );
				
				// System.out.println( "adding to hash tree for " + piece_number );

				return;
				
			}else{

				piece_tree_request		= new PieceTreeRequest( piece_offset, piece_number, receiver );
				
				piece_tree_requests.put( piece_number, piece_tree_request );
			}
		}
		
		PieceTreeRequest	f_piece_tree_request = piece_tree_request;
			
		// System.out.println( "building hash tree for " + piece_number );
		
		boolean	went_async = false;
		
		try{
			byte[] piece_hash = torrent.getPieces()[piece_number];
		
			int piece_size = disk_manager.getPieceLength( piece_number );
			
			PEPeerTransport peer = ((HashesReceiverImpl)receiver.getHashesReceiver()).getPeer();			
				
			PeerStats stats = (PeerStats)peer.getUserData( KEY_PEER_STATS );

			stats.pieceTreeRequest( piece_size );
			
			disk_manager.enqueueReadRequest(
					disk_manager.createReadRequest( piece_number, 0, piece_size ),
					new DiskManagerReadRequestListener()
					{
						public void
						readCompleted(
							DiskManagerReadRequest 	request,
							DirectByteBuffer 		data )
						{
							boolean async_hashing = false;
							
							try{
								ByteBuffer byte_buffer = data.getBuffer( DirectByteBuffer.SS_OTHER );
								
								DMPieceList pieceList = disk_manager.getPieceList( piece_number );
								
								DMPieceMapEntry piece_entry = pieceList.get(0);
					    							    		
									// with v2 a piece only contains > 1 file if the second file is a dummy padding file added
									// to 'make things work'

					    		if ( pieceList.size() == 2 ){

						    		int v2_piece_length = piece_entry.getLength();
						    		
						    		if ( v2_piece_length < piece_length ){
						    			
						    				// hasher will pad appropriately
						    			
						    			byte_buffer.limit( byte_buffer.position() + v2_piece_length );
						    		}
					    		}
					    		
								ConcurrentHasher hasher = ConcurrentHasher.getSingleton();
								
								hasher.addRequest( 
									byte_buffer,
									2,
									piece_size,
									file.getLength(),
									(completed_request)->{
										
										byte[][]	hashes = null;
										
										try{
											if ( Arrays.equals( completed_request.getResult(), piece_hash )){
												
												List<List<byte[]>> tree = completed_request.getHashTree();
												
												if ( tree != null ){
													
													hashes = new byte[tree.size()][];
													
													int layer_index = hashes.length - 1;
													
													for ( List<byte[]> entry: tree ){
														
														byte[] layer = new byte[ entry.size() * SHA256.DIGEST_LENGTH ];
														
														hashes[layer_index--] = layer;
														
														int layer_pos = 0;
														
														for ( byte[] hash: entry ){
															
															System.arraycopy( hash, 0, layer, layer_pos, SHA256.DIGEST_LENGTH );
															
															layer_pos += SHA256.DIGEST_LENGTH;
														}
													}
																							
													last_piece_tree_request = SystemTime.getMonotonousTime();

													synchronized( piece_tree_cache ){

														piece_tree_cache.put( piece_number, hashes );
													}
												}
											}
										}finally{
											
											data.returnToPool();

											f_piece_tree_request.complete( hashes );								

										}
									},
									false );
								
								async_hashing = true;
																
							}finally{
								
								if ( !async_hashing ){
								
									data.returnToPool();

									f_piece_tree_request.complete( null );								
								}
							}
						}
	
						public void
						readFailed(
							DiskManagerReadRequest 	request,
							Throwable		 		cause )
						{
							f_piece_tree_request.complete( null );	
						}
	
						public int
						getPriority()
						{
							return( -1 );
						}
	
						public void
						requestExecuted(
							long 	bytes )
						{
						}
					});
			
			went_async = true;
						
		}catch( Throwable e ){
			
			Debug.out( e );
			
		}finally{
			
			if ( !went_async ){
		
				piece_tree_request.complete( null );
			}
		}
	}
	
	private class
	PeerStats
	{
		private final PEPeerTransport	peer;
		
		private final Average piece_tree_data_rate 	= Average.getInstance( 1000, 10 );  //update every 1s, average over 10s
		private final Average hash_data_rate 		= Average.getInstance( 1000, 10 );  //update every 1s, average over 10s
		private final Average hash_rate 			= Average.getInstance( 1000, 10 );  //update every 1s, average over 10s

		private final LinkedList<Runnable>	tasks = new LinkedList<>();
		
		PeerStats(
			PEPeerTransport		_peer )
		{
			peer		= _peer;
		}
		
		void
		pieceTreeRequest(
			int		bytes )
		{
			piece_tree_data_rate.addValue( bytes );
		}
		
		void
		hashesRequested(
			int		bytes_related )
		{
			hash_rate.addValue( 100 );
			
			hash_data_rate.addValue( bytes_related );
		}
				
		void
		runTask(
			Runnable	task )
		{
			long hd_rate 	= hash_data_rate.getAverage();
			long pt_rate	= piece_tree_data_rate.getAverage();
			long peer_rate	= peer.getStats().getDataSendRate();
			
			boolean rate_limit = false;
			
			if ( pt_rate > 1*1024*1024 ){
				
				if ( pt_rate > peer_rate * 2 ){
					
					rate_limit = true;
				}
			}
			
			if ( hd_rate > 10*1024*1024 ){
				
				if ( hd_rate > peer_rate * 4 ){
					
					rate_limit = true;
				}
			}
			
			if ( rate_limit ){
								
				synchronized( tasks ){
					
					tasks.addLast( task );
				
					if ( tasks.size() > 1024 ){
						
						peer_manager.removePeer( peer, "Too many hash requests", Transport.CR_NONE );
						
						return;
					}
					
					// System.out.println( "rate limiting, tasks=" + tasks.size() );

					if ( tasks.size() == 1 ){
						
						SimpleTimer.addEvent(
							"Peer.hash.rl",
							SystemTime.getOffsetTime( 100 ),
							new TimerEventPerformer()
							{
								
								public void
								perform(
									TimerEvent	event )
								{
									Runnable t;
									
									synchronized( tasks ){
										
										t = tasks.removeFirst();
										
										if ( !tasks.isEmpty()){
											
											if ( peer.getPeerState() == PEPeer.DISCONNECTED ){
												
												tasks.clear();
												
											}else{
												
												SimpleTimer.addEvent(
													"Peer.hash.rl",
													SystemTime.getOffsetTime( 100 ),
													this );
											}
										}
									}
									
									t.run();
								}
							});
					}
				}
			}else{
				
				task.run();
			}
		}
		
		String
		getString()
		{
			return( "hash rate=" + (hash_rate.getAverage()/100f) + 
					", hash data=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( hash_data_rate.getAverage()) +
					", pt data=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( piece_tree_data_rate.getAverage()) +
					", peer data=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( peer.getStats().getDataSendRate()));
		}
	}
	
	private class
	PieceTreeRequest
	{
		private final int		piece_offset;
		private final int		piece_number;
		
		private final long		time = SystemTime.getMonotonousTime();
		
		private final List<PieceTreeReceiver>		listeners = new ArrayList<>();
		
		private boolean done;
		
		PieceTreeRequest(
			int						_po,
			int						_pn,
			PieceTreeReceiver		_listener )
		{
			piece_offset	= _po;
			piece_number	= _pn;
			
			listeners.add( _listener );
		}
		
		private long
		getCreateTime()
		{
			return( time );
		}
		
		private void
		addListener(
			PieceTreeReceiver		l )
		{
			synchronized( piece_tree_requests ){
				
				listeners.add( l );
			}
		}
		
		private void
		complete(
			byte[][]	piece_tree )
		{
			synchronized( piece_tree_requests ){

				if ( done ){
					
					return;
				}
				
				done = true;
				
				piece_tree_requests.remove( piece_number );
			}
		
			//System.out.println( "    piece tree request complete for " + piece_number + ", tree=" + piece_tree  );
			
			for ( PieceTreeReceiver listener: listeners ){
				
				try{
					listener.receivePieceTree( piece_offset, piece_tree );
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
	}
	
	private static class
	HashesReceiverImpl
		implements TOTorrentFileHashTree.HashesReceiver
	{
		private final PEPeerTransport		peer;
		private final HashesReceiver		receiver;
		
		HashesReceiverImpl(
			PEPeerTransport		_peer,
			HashesReceiver		_receiver )
		{
			peer		= _peer;
			receiver	= _receiver;
		}
		
		@Override
		public void 
		receiveHashes(
			byte[][] hashes)
		{
			receiver.receiveResult( hashes );
		}
		
		PEPeerTransport
		getPeer()
		{
			return( peer );
		}
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
