/*
 * Created on 22 Jun 2006
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

package com.biglybt.core.networkmanager.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.util.*;

public class
IncomingConnectionManager
{
	static final LogIDs LOGID = LogIDs.NWMAN;

	private static final IncomingConnectionManager	singleton = new IncomingConnectionManager();

	public static IncomingConnectionManager
	getSingleton()
	{
		return( singleton );
	}

	private volatile Map match_buffers_cow = new HashMap();	// copy-on-write
	private final AEMonitor match_buffers_mon = new AEMonitor( "IncomingConnectionManager:match" );
	private int max_match_buffer_size = 0;
	private int max_min_match_buffer_size = 0;


	private final ArrayList connections = new ArrayList();
	private final AEMonitor connections_mon = new AEMonitor( "IncomingConnectionManager:conns" );


	protected
	IncomingConnectionManager()
	{

		SimpleTimer.addPeriodicEvent(
				"IncomingConnectionManager:timeouts",
				5000,
        new TimerEventPerformer() {
          @Override
          public void perform(TimerEvent ev ) {

          	doTimeoutChecks();
          }
        }
     );
	}



	public boolean
	isEmpty()
	{
		return( match_buffers_cow.isEmpty());
	}

		// returns MatchListener,RoutingData if matched

	public Object[]
	checkForMatch(
		TransportHelper	transport,
		int				incoming_port,
		ByteBuffer 		to_check,
		boolean 		min_match )
	{
	       //remember original values for later restore
	      int orig_position = to_check.position();
	      int orig_limit = to_check.limit();

	      //rewind
	      to_check.position( 0 );

	      MatchListener listener 		= null;
	      Object		routing_data 	= null;

	      for( Iterator i = match_buffers_cow.entrySet().iterator(); i.hasNext() && !transport.isClosed(); ) {
	        Map.Entry entry = (Map.Entry)i.next();
	        NetworkManager.ByteMatcher bm = (NetworkManager.ByteMatcher)entry.getKey();
	        MatchListener this_listener = (MatchListener)entry.getValue();

	        int	specific_port = bm.getSpecificPort();

	        if ( specific_port != -1 && specific_port != incoming_port ){

	        	continue;
	        }

	        if ( min_match ){
	            if( orig_position < bm.minSize() ) {  //not enough bytes yet to compare
	  	          continue;
	  	        }

	            routing_data = bm.minMatches( transport, to_check, incoming_port );

	            if ( routing_data != null ){
	  	          listener = this_listener;
	  	          break;
	  	        }
	        }else{
		        if( orig_position < bm.matchThisSizeOrBigger() ) {  //not enough bytes yet to compare
		          continue;
		        }

		        routing_data = bm.matches( transport, to_check, incoming_port );

		        if ( routing_data != null ){
		          listener = this_listener;
		          break;
		        }
	        }
	      }

	      //restore original values in case the checks changed them
	      to_check.position( orig_position );
	      to_check.limit( orig_limit );

	      if ( listener == null ){

	    	  return( null );
	      }

	      return( new Object[]{ listener, routing_data });
	  }

	  /**
	   * Register the given byte sequence matcher to handle matching against new incoming connection
	   * initial data; i.e. the first bytes read from a connection must match in order for the given
	   * listener to be invoked.
	   * @param matcher byte filter sequence
	   * @param listener to call upon match
	   */

	public void
	registerMatchBytes(
		NetworkManager.ByteMatcher 	matcher,
		MatchListener 				listener )
	{
	    try {  match_buffers_mon.enter();

	    	if( matcher.maxSize() > max_match_buffer_size ) {
	    		max_match_buffer_size = matcher.maxSize();
	    	}

	    	if ( matcher.minSize() > max_min_match_buffer_size ){
	    		max_min_match_buffer_size = matcher.minSize();
	    	}

	    	Map	new_match_buffers = new HashMap( match_buffers_cow );

	    	new_match_buffers.put( matcher, listener );

	    	match_buffers_cow = new_match_buffers;

	    	addSharedSecrets( matcher.getDescription(), matcher.getSharedSecrets());

	    }finally {
	    	match_buffers_mon.exit();
	    }
	}


	  /**
	   * Remove the given byte sequence match from the registration list.
	   * @param to_remove byte sequence originally used to register
	   */

	public void
	deregisterMatchBytes(
		NetworkManager.ByteMatcher to_remove )
	{
	    try {  match_buffers_mon.enter();
	      Map	new_match_buffers = new HashMap( match_buffers_cow );

	      new_match_buffers.remove( to_remove );

	      if( to_remove.maxSize() == max_match_buffer_size ) { //recalc longest buffer if necessary
	        max_match_buffer_size = 0;
	        for( Iterator i = new_match_buffers.keySet().iterator(); i.hasNext(); ) {
	          NetworkManager.ByteMatcher bm = (NetworkManager.ByteMatcher)i.next();
	          if( bm.maxSize() > max_match_buffer_size ) {
	            max_match_buffer_size = bm.maxSize();
	          }
	        }
	      }

	      match_buffers_cow = new_match_buffers;

	      removeSharedSecrets( to_remove.getSharedSecrets());

	    } finally {  match_buffers_mon.exit();  }
	}

	public void
	addSharedSecrets(
		String			name,
		byte[][]		secrets )
	{
		if ( secrets != null ){

			ProtocolDecoder.addSecrets( name, secrets );
		}
	}

	public void
	removeSharedSecrets(
		byte[][]		secrets )
	{
		if ( secrets != null ){

			ProtocolDecoder.removeSecrets( secrets );
		}
	}

	public int
	getMaxMatchBufferSize()
	{
		return( max_match_buffer_size );
	}

	public int
		getMaxMinMatchBufferSize()
	{
		return( max_min_match_buffer_size );
	}





	public void
	addConnection(
		int						local_port,
		TransportHelperFilter	filter,
		Transport				new_transport )

	{
		TransportHelper	transport_helper = filter.getHelper();

		if ( isEmpty()) {  //no match registrations, just close

			if ( Logger.isEnabled()){

		    	Logger.log(new LogEvent(LOGID, "Incoming connection from [" + transport_helper.getAddress() +
		    				 "] dropped because zero routing handlers registered"));
			}

			transport_helper.close( "No routing handler" );

		    return;
		}

	   	// note that the filter may have some data internally queued in it after the crypto handshake decode
		// (in particular the BT header). However, there should be some data right behind it that will trigger
		// a read-select below, thus giving prompt access to the queued data

		final IncomingConnection ic = new IncomingConnection( filter, getMaxMatchBufferSize());

		TransportHelper.selectListener	sel_listener = new SelectorListener( local_port, new_transport );

		try{
			connections_mon.enter();

			connections.add( ic );

			transport_helper.registerForReadSelects( sel_listener, ic );

		}finally{

			connections_mon.exit();
		}

			// might be stuff queued up in the filter - force one process cycle (NAT check in particular )

		sel_listener.selectSuccess( transport_helper, ic );
	}

	protected void
	removeConnection(
		IncomingConnection 	connection,
		boolean 			close_as_well,
		String				reason )
	{

		try{
			connections_mon.enter();

			connection.filter.getHelper().cancelReadSelects();

			connections.remove( connection );   //remove from connection list

		}finally{

			connections_mon.exit();
		}

		if( close_as_well ) {

			connection.filter.getHelper().close( "Tidy close" + ( reason==null||reason.length()==0?"":(": " + reason )));
		}
	}



	protected void
	doTimeoutChecks()
	{
		try{  connections_mon.enter();

		ArrayList to_close = null;

		long now = SystemTime.getCurrentTime();

		for( int i=0; i < connections.size(); i++ ){

			IncomingConnection ic = (IncomingConnection)connections.get( i );

			TransportHelper	transport_helper = ic.filter.getHelper();

			if( ic.last_read_time > 0 ) {  //at least one read op has occured
				if( now < ic.last_read_time ) {  //time went backwards!
					ic.last_read_time = now;
				}
				else if( now - ic.last_read_time > transport_helper.getReadTimeout()) {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Incoming connection ["
								+ transport_helper.getAddress()
								+ "] forcibly timed out due to socket read inactivity ["
								+ ic.buffer.position() + " bytes read: "
								+ new String(ic.buffer.array()) + "]"));
					if( to_close == null )  to_close = new ArrayList();
					to_close.add( ic );
				}
			}
			else { //no bytes have been read yet
				if( now < ic.initial_connect_time ) {  //time went backwards!
					ic.initial_connect_time = now;
				}
				else if( now - ic.initial_connect_time > transport_helper.getConnectTimeout()) {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Incoming connection ["
								+ transport_helper.getAddress()	+ "] forcibly timed out after "
								+ "60sec due to socket inactivity"));
					if( to_close == null )  to_close = new ArrayList();
					to_close.add( ic );
				}
			}
		}

		if( to_close != null ) {
			for( int i=0; i < to_close.size(); i++ ) {
				IncomingConnection ic = (IncomingConnection)to_close.get( i );
				removeConnection( ic, true, "incoming connection routing timeout" );
			}
		}

		} finally {  connections_mon.exit();  }
	}






	protected static class
	IncomingConnection
	{
		protected final TransportHelperFilter filter;
		protected final ByteBuffer buffer;
		protected long initial_connect_time;
		protected long last_read_time = -1;

		protected
		IncomingConnection(
			TransportHelperFilter filter, int buff_size )
		{
			this.filter = filter;
			this.buffer = ByteBuffer.allocate( buff_size );
			this.initial_connect_time = SystemTime.getCurrentTime();
		}
	}


	protected class
	SelectorListener
		implements TransportHelper.selectListener
	{
		private final int				local_port;
		private final Transport		transport;

		protected
		SelectorListener(
			int						_local_port,
			Transport				_transport )
		{
			local_port	= _local_port;
			transport	= _transport;
		}

		@Override
		public boolean
		selectSuccess(
			TransportHelper transport_helper, Object attachment )
		{
			IncomingConnection	ic = (IncomingConnection)attachment;

			try {
				long bytes_read = ic.filter.read( new ByteBuffer[]{ ic.buffer }, 0, 1 );

				if( bytes_read < 0 ) {
					throw new IOException( "end of stream on socket read" );
				}

				if( bytes_read == 0 ) {
					return false;
				}

				ic.last_read_time = SystemTime.getCurrentTime();

				Object[] match_data = checkForMatch( transport_helper, local_port, ic.buffer, false );

				if( match_data == null ) {  //no match found
					if( transport_helper.isClosed() ||
						ic.buffer.position() >= getMaxMatchBufferSize()) { //we've already read in enough bytes to have compared against all potential match buffers

						ic.buffer.flip();
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,
									LogEvent.LT_WARNING,
									"Incoming stream from [" + transport_helper.getAddress()
									+ "] does not match "
									+ "any known byte pattern: "
									+ ByteFormatter.nicePrint(ic.buffer.array(), 128)));
						
						removeConnection( ic, true, "routing failed: unknown byte pattern" );
					}
				}else{
					
						//match found!
					
					ic.buffer.flip();
					
					removeConnection( ic, false, null );

					transport.setAlreadyRead( ic.buffer );

					transport.connectedInbound();

					IncomingConnectionManager.MatchListener listener = (IncomingConnectionManager.MatchListener)match_data[0];
					
					listener.connectionMatched( transport, match_data[1] );
				}
				return( true );
			}
			catch( Throwable t ) {
				try {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID,
								LogEvent.LT_WARNING,
								"Incoming connection [" + transport_helper.getAddress()
								+ "] socket read exception: "
								+ t.getMessage()));
				}
				catch( Throwable x ) {
					Debug.out( "Caught exception on incoming exception log:" );
					x.printStackTrace();
					System.out.println( "CAUSED BY:" );
					t.printStackTrace();
				}

				removeConnection( ic, true, t==null?null:Debug.getNestedExceptionMessage(t));

				return( false );
			}
		}

		//FAILURE
		@Override
		public void
		selectFailure(
			TransportHelper		transport_helper,
			Object 				attachment,
			Throwable			msg )
		{
			IncomingConnection	ic = (IncomingConnection)attachment;
			if (Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
						"Incoming connection [" + transport_helper.getAddress()
						+ "] socket select op failure: "
						+ msg.getMessage()));
			}

			removeConnection( ic, true, msg==null?null:Debug.getNestedExceptionMessage(msg));
		}
	}


	/**
	 * Listener for byte matches.
	 */

	public interface MatchListener {

		/**
		 * Currently if message crypto is on and default fallback for incoming not
		 * enabled then we would bounce incoming messages from non-crypto transports
		 * For example, NAT check
		 * This method allows auto-fallback for such transports
		 * @return
		 */

		public boolean
		autoCryptoFallback();

		/**
		 * The given socket has been accepted as matching the byte filter.
		 * @param channel matching accepted connection
		 * @param read_so_far bytes already read
		 */

		public void
		connectionMatched(
			Transport	transport,
			Object		routing_data );

	}
}
