/*
 * Created on Jul 19, 2004
 * Created by Alon Rohter
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

package com.biglybt.core.peermanager.utils;

import java.util.*;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.disk.DiskManagerReadRequestListener;
import com.biglybt.core.networkmanager.OutgoingMessageQueue;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessage;
import com.biglybt.core.peermanager.messaging.bittorrent.BTPiece;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.SystemTime;


/**
 * Front-end manager for handling requested outgoing bittorrent Piece messages.
 * Peers often make piece requests in batch, with multiple requests always
 * outstanding, all of which won't necessarily be honored (i.e. we choke them),
 * so we don't want to waste time reading in the piece data from disk ahead
 * of time for all the requests. Thus, we only want to perform read-aheads for a
 * small subset of the requested data at any given time, which is what this handler
 * does, before passing the messages onto the outgoing message queue for transmission.
 */
public class OutgoingBTPieceMessageHandler {
  private final PEPeer					peer;
  private final OutgoingMessageQueue 	outgoing_message_queue;
  private 		byte					piece_version;

  private final LinkedList<DiskManagerReadRequest>			requests 			= new LinkedList<>();
  private final ArrayList<DiskManagerReadRequest>			loading_messages 	= new ArrayList<>();
  private final HashMap<BTPiece,DiskManagerReadRequest> 	queued_messages 	= new HashMap<>();

  private final Map<Integer,int[]>							active_pieces		= new HashMap<>();
  
  private LinkedList<DiskManagerReadRequest>		recent_messages;
  private volatile long								recent_messages_last_access	= -1;
  
  private final AEMonitor	lock_mon	= new AEMonitor( "OutgoingBTPieceMessageHandler:lock");
  private boolean destroyed = false;
  private int request_read_ahead = 2;

  final OutgoingBTPieceMessageHandlerAdapter	adapter;

  /**
   * Create a new handler for outbound piece messages,
   * reading piece data from the given disk manager
   * and transmitting the messages out the given message queue.
   * @param disk_manager
   * @param outgoing_message_q
   */
  public
  OutgoingBTPieceMessageHandler(
	PEPeer									_peer,
	OutgoingMessageQueue 					_outgoing_message_q,
	OutgoingBTPieceMessageHandlerAdapter	_adapter,
	byte									_piece_version )
  {
	peer					= _peer;
    outgoing_message_queue 	= _outgoing_message_q;
    adapter					= _adapter;
    piece_version			= _piece_version;

    outgoing_message_queue.registerQueueListener( sent_message_listener );
  }

  public void
  setPieceVersion(
	byte	version )
  {
	  piece_version = version;
  }



  private final DiskManagerReadRequestListener read_req_listener = new DiskManagerReadRequestListener() {
    @Override
    public void readCompleted(DiskManagerReadRequest request, DirectByteBuffer data ) {
      try{
      	lock_mon.enter();

      	if( !loading_messages.contains( request ) || destroyed ) { //was canceled
      	  data.returnToPool();
      	  return;
      	}
      	
      	loading_messages.remove( request );

        BTPiece msg = new BTPiece( request.getPieceNumber(), request.getOffset(), data, piece_version );
        
        	// message moved from loading->queued, no need to update 'active'
        
        queued_messages.put( msg, request );

        outgoing_message_queue.addMessage( msg, true );
        
        if ( recent_messages != null ){
        	
        	long now = SystemTime.getMonotonousTime();
        	
        	if ( now - recent_messages_last_access > 60*1000 ){
        		
        		recent_messages = null;
        		
        		recent_messages_last_access = -1;
        		
        	}else{
        	
        		trimRecentMessages();
        		
        		recent_messages.add( request );
        	}
        }
      }
      finally{
      	lock_mon.exit();
      }

      outgoing_message_queue.doListenerNotifications();
    }
    
    @Override
    public void
    readFailed(
    	DiskManagerReadRequest 	request,
  		Throwable		 		cause )
    {
        try{
          	lock_mon.enter();

          	if( !loading_messages.contains( request ) || destroyed ) { //was canceled
          	  return;
          	}
          	loading_messages.remove( request );
          	
          	int piece_number = request.getPieceNumber();
          	
          	int[] active = active_pieces.get( piece_number );
          	
          	if ( active == null ){
          		
          		Debug.out("eh?" );
          		
          	}else{
          		
          		if( --active[0] == 0 ){

          			active_pieces.remove( piece_number );
          		}	  
          	}
        }finally{
          	lock_mon.exit();
        }

      	peer.sendRejectRequest( request );
    }

    @Override
    public int
    getPriority()
    {
    	return( -1 );
    }
	@Override
	public void
	requestExecuted(long bytes)
	{
		adapter.diskRequestCompleted( bytes );
	}
  };


  private final OutgoingMessageQueue.MessageQueueListener sent_message_listener = new OutgoingMessageQueue.MessageQueueListener() {
    @Override
    public boolean messageAdded(Message message ) {   return true;   }

    @Override
    public void messageSent(Message message ) {
      if( message.getID().equals( BTMessage.ID_BT_PIECE ) ) {
        try{
          lock_mon.enter();

          	// due to timing issues we can get in here with a message already removed

          DiskManagerReadRequest request = queued_messages.remove( message );

          if ( request != null ){
        	  
        	  request.setTimeSent( SystemTime.getMonotonousTime());
        	  
        	  int piece_number = request.getPieceNumber();
        			  
    		  int[] active = active_pieces.get( piece_number );
    		  
    		  if ( active == null ){
    			  
    			  Debug.out("eh?" );
    			  
    		  }else{
    			  
    			  if( --active[0] == 0 ){
    				
    				  active_pieces.remove( piece_number );
    			  }	  
    		  }
          }
        }finally{
          lock_mon.exit();
        }
        
        doReadAheadLoads();
        
        if ( recent_messages_last_access != -1 ){
        	
        	 trimRecentMessages();
        }
      }else{
      
	      if ( recent_messages_last_access != -1 ){
	    	  
	    	  try{
	              lock_mon.enter();
	
	              trimRecentMessages();
	              
	    	  }finally{
	    		  
	              lock_mon.exit();
	          }
	      }
      }
    }
    
    @Override
    public void messageQueued(Message message ) {/*nothing*/}
    @Override
    public void messageRemoved(Message message ) {/*nothing*/}
    @Override
    public void protocolBytesSent(int byte_count ) {/*ignore*/}
    @Override
    public void dataBytesSent(int byte_count ) {/*ignore*/}
    @Override
    public void flush(){}
  };

  private void
  trimRecentMessages()
  {
	  Iterator<DiskManagerReadRequest>	it = recent_messages.descendingIterator();

	  long now = SystemTime.getMonotonousTime();

	  while( it.hasNext()){

		  DiskManagerReadRequest req = it.next();

		  long sent = req.getTimeSent();

		  if ( sent < 0 || ( sent > 0 && now - sent > 5000 )){

			  it.remove();

		  }else{

			  break;
		  }
	  }
  }

  /**
   * Register a new piece data request.
   * @param piece_number
   * @param piece_offset
   * @param length
   */
  public boolean addPieceRequest( int piece_number, int piece_offset, int length ) {
	  if( destroyed )  return( false );

	  DiskManagerReadRequest dmr = peer.getManager().getDiskManager().createReadRequest( piece_number, piece_offset, length );

	  try{
		  lock_mon.enter();

		  requests.addLast( dmr );

		  int[] active = active_pieces.get( piece_number );
		  
		  if ( active == null ){
			  
			  active = new int[1];
			  
			  active_pieces.put( piece_number, active );
		  }
		  
		  active[0]++;
		  
		  	// TODO: remove once we're happy they ain't borkage
		  
		  if ( Constants.IS_CVS_VERSION ){
			  
			  if ( active_pieces.size() > requests.size() + loading_messages.size() + queued_messages.size()){
				  
				  Debug.out( "eh?" );
			  }
		  }
	  }finally{
		  lock_mon.exit();
	  }

	  doReadAheadLoads();

	  return( true );
  }


  /**
   * Remove an outstanding piece data request.
   * @param piece_number
   * @param piece_offset
   * @param length
   */
  public void removePieceRequest( int piece_number, int piece_offset, int length ) {
  	if( destroyed )  return;

    DiskManagerReadRequest dmr = peer.getManager().getDiskManager().createReadRequest( piece_number, piece_offset, length );

    boolean	entry_removed = false;

    try{
      lock_mon.enter();

      if( requests.contains( dmr ) ) {
        requests.remove( dmr );
        entry_removed = true;
        return;
      }

      if( loading_messages.contains( dmr ) ) {
        loading_messages.remove( dmr );
        entry_removed = true;
        return;
      }


      for( Iterator<Map.Entry<BTPiece,DiskManagerReadRequest>> i = queued_messages.entrySet().iterator(); i.hasNext(); ) {
    	Map.Entry<BTPiece,DiskManagerReadRequest> entry = i.next();
        if( entry.getValue().equals( dmr ) ) {  //it's already been queued
          BTPiece msg = entry.getKey();
          if( outgoing_message_queue.removeMessage( msg, true ) ) {
        	entry_removed = true;
            i.remove();
            entry.getValue().setTimeSent( -1 );
          }
          break;  //do manual listener notify
        }
      }
    }finally{

      if ( entry_removed ){
    	  
		  int[] active = active_pieces.get( piece_number );
		  
		  if ( active == null ){
			  
			  Debug.out("eh?" );
			  
		  }else{
			  
			  if( --active[0] == 0 ){
				
				  active_pieces.remove( piece_number );
			  }	  
		  }
      }
      lock_mon.exit();

      if ( entry_removed ){

   		  peer.sendRejectRequest( dmr );
      }
    }

    outgoing_message_queue.doListenerNotifications();
  }



  /**
   * Remove all outstanding piece data requests.
   */
  public void removeAllPieceRequests() {
  	if( destroyed )  return;

  	List<DiskManagerReadRequest> removed = new ArrayList<>();

  	try{
  		lock_mon.enter();

  		for( Iterator<Map.Entry<BTPiece,DiskManagerReadRequest>> i = queued_messages.entrySet().iterator(); i.hasNext(); ) {
  			Map.Entry<BTPiece,DiskManagerReadRequest> entry = i.next();
  			BTPiece msg = entry.getKey();
  			if (outgoing_message_queue.removeMessage( msg, true )){
  				removed.add( queued_messages.get( msg ));
  			}
  			entry.getValue().setTimeSent( -1 );
  		}

  		queued_messages.clear();
  		
  		removed.addAll( requests );

  		requests.clear();

  		removed.addAll( loading_messages );

  		loading_messages.clear();
  		
  		active_pieces.clear();
  	}
  	finally{
  		lock_mon.exit();
  	}

    for ( DiskManagerReadRequest request: removed ){

    	 peer.sendRejectRequest( request );
    }

    outgoing_message_queue.doListenerNotifications();
  }



  public void setRequestReadAhead( int num_to_read_ahead ) {
    request_read_ahead = num_to_read_ahead;
  }



  public void destroy() {
    try{
      lock_mon.enter();

      removeAllPieceRequests();

      destroyed = true;

      outgoing_message_queue.cancelQueueListener(sent_message_listener);
    }
    finally{
      lock_mon.exit();
    }
  }


  private void doReadAheadLoads() {
  	List<DiskManagerReadRequest>	to_submit = null;
  	try{
  		lock_mon.enter();

  		while( loading_messages.size() + queued_messages.size() < request_read_ahead && !requests.isEmpty() && !destroyed ){
  			
  			int queued_data = outgoing_message_queue.getDataQueuedBytes();
  			
  			if ( queued_data > 1024*1024 ){
  				
  				if ( queued_data > 32*1024*1024 ){
  					
  						// hard limit
  					
  					break;
  					
  				}else{
  					
  					long send_rate = peer.getStats().getDataSendRate();
  				
  						// give them 30 seconds of upload, pretty generous
  					
  					if ( send_rate*30 < queued_data ){
  						
  						break;
  					}
  				}
  			}
  			
  			DiskManagerReadRequest dmr = (DiskManagerReadRequest)requests.removeFirst();
  			
  				// moved from requests->loading - no need to update 'active'
  			
  			loading_messages.add( dmr );
  			
  			if ( to_submit == null ){
  				
  				to_submit = new ArrayList<>();
  			}
  			
  			to_submit.add( dmr );
  		}
    }finally{
    	lock_mon.exit();
    }

    if ( to_submit != null ){
    	
    	for (DiskManagerReadRequest req: to_submit ){
    		
    		peer.getManager().getAdapter().enqueueReadRequest( peer, req, read_req_listener );
    	}
    }
  }

  /**
	 * Get a list of piece numbers being requested
	 */
  
	public int[] getRequestedPieceNumbers() {
		if( destroyed )  return new int[0];

		try{
			lock_mon.enter();

			int num = active_pieces.size();
			
			int[] result = new int[num];
			
			if ( num >0 ){
				
				int pos = 0;
				
				for ( Integer pn: active_pieces.keySet() ){
					
					result[pos++] = pn;
				}
			}

			return( result );
		}finally{
			lock_mon.exit();
		}
	}
	
	public int getRequestedPieceNumberCount(){
		try{
			lock_mon.enter();

			return( active_pieces.size());

		}finally{
			lock_mon.exit();
		}
	}
	
	public DiskManagerReadRequest[]
	getRecentMessages()
	{
		try {
			lock_mon.enter();
			
			recent_messages_last_access = SystemTime.getMonotonousTime();
			
			if ( recent_messages == null ){
				
				recent_messages = new LinkedList<DiskManagerReadRequest>(); 
			}
									
			return( recent_messages.toArray( new DiskManagerReadRequest[recent_messages.size()]));
			
		} finally {
			
			lock_mon.exit();
		}
	}
	
	public int
	getRequestCount()
	{
		return( queued_messages.size()	+ loading_messages.size() + requests.size());
	}

	public boolean
	isStalledPendingLoad()
	{
		return( queued_messages.size() == 0 && loading_messages.size() > 0 );
	}
}
