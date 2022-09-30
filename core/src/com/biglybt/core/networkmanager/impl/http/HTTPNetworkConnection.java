/*
 * Created on 3 Oct 2006
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

package com.biglybt.core.networkmanager.impl.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.OutgoingMessageQueue;
import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.networkmanager.impl.RawMessageImpl;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.bittorrent.*;
import com.biglybt.core.util.*;

public abstract class
HTTPNetworkConnection
{
	protected static final LogIDs LOGID = LogIDs.NWMAN;

	private static final int	MAX_OUTSTANDING_BT_REQUESTS	= 16;

	protected static final String	NL			= "\r\n";

	private static final String	HDR_SERVER 				= "Server: " + Constants.BIGLYBT_NAME + " " + Constants.BIGLYBT_VERSION + NL;
	private static final String HDR_KEEP_ALIVE_TIMEOUT 	= "Keep-Alive: timeout=30" + NL;
	private static final String HDR_CACHE_CONTROL		= "Cache-Control: public, max-age=86400" + NL;

	private static final String	DEFAULT_CONTENT_TYPE	= HTTPUtils.guessContentTypeFromFileType(null);

	static int        max_read_block_size;

	static{

	    ParameterListener param_listener = new ParameterListener() {
	            @Override
	            public void
	            parameterChanged(
	                String  str )
	            {
	                max_read_block_size = COConfigurationManager.getIntParameter( "BT Request Max Block Size" );
	            }
	    };

	    COConfigurationManager.addAndFireParameterListener( "BT Request Max Block Size", param_listener);
	}

	private static final int	TIMEOUT_CHECK_PERIOD			= 15*1000;
	private static final int	DEAD_CONNECTION_TIMEOUT_PERIOD	= 30*1000;
	private static final int	MAX_CON_PER_ENDPOINT			= 5*1000;

	static final Map<networkConnectionKey,List<HTTPNetworkConnection>>	http_connection_map = new HashMap<>();

	static{
		SimpleTimer.addPeriodicEvent(
			"HTTPNetworkConnection:timer",
			TIMEOUT_CHECK_PERIOD,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event )
				{
					synchronized( http_connection_map ){

						boolean	 check = true;

						while( check ){

							check = false;

							Iterator<Map.Entry<networkConnectionKey,List<HTTPNetworkConnection>>>	it = http_connection_map.entrySet().iterator();

							while( it.hasNext()){

								Map.Entry<networkConnectionKey,List<HTTPNetworkConnection>>	entry = it.next();

								networkConnectionKey	key = (networkConnectionKey)entry.getKey();

								List<HTTPNetworkConnection>	connections = entry.getValue();

								/*
								String	times = "";

								for (int i=0;i<connections.size();i++){

									HTTPNetworkConnection	connection = (HTTPNetworkConnection)connections.get(i);

									times += (i==0?"":",") + connection.getTimeSinceLastActivity();
								}

								System.out.println( "HTTPNC: " + key.getName() + " -> " + connections.size() + " - " + times );
								*/

								if ( checkConnections( connections )){

										// might have a concurrent mod to the iterator

									if ( !http_connection_map.containsKey( key )){

										check	= true;

										break;
									}
								}
							}
						}
					}
				}
			});
	}

	protected static boolean
	checkConnections(
		List<HTTPNetworkConnection>	connections )
	{
		boolean	some_closed = false;

		HTTPNetworkConnection	oldest 			= null;
		long					oldest_time		= -1;

		Iterator<HTTPNetworkConnection>	it = connections.iterator();

		List<HTTPNetworkConnection>	timed_out = new ArrayList<>();

		while( it.hasNext()){

			HTTPNetworkConnection	connection = (HTTPNetworkConnection)it.next();

			long	time = connection.getTimeSinceLastActivity();

			if ( time > DEAD_CONNECTION_TIMEOUT_PERIOD ){

				if ( connection.getRequestCount() == 0 ){

					timed_out.add( connection );

					continue;
				}
			}

			if ( time > oldest_time && !connection.isClosing()){

				oldest_time		= time;

				oldest	= connection;
			}
		}

		for (int i=0;i<timed_out.size();i++){

			((HTTPNetworkConnection)timed_out.get(i)).close( "Timeout" );

			some_closed	= true;
		}

		if ( connections.size() - timed_out.size() > MAX_CON_PER_ENDPOINT ){

			oldest.close( "Too many connections from initiator");

			some_closed	= true;
		}

		return( some_closed );
	}

	private final HTTPNetworkManager	manager;
	final NetworkConnection	connection;
	final PEPeerTransport		peer;

	private final HTTPMessageDecoder	decoder;
	private final HTTPMessageEncoder	encoder;

	private boolean			sent_handshake	= false;

	private final byte[]	peer_id	= PeerUtils.createWebSeedPeerID();

	private boolean	choked	= true;

	private final List<httpRequest>		http_requests			= new ArrayList<>();
	private final List<BTRequest>			choked_requests 		= new ArrayList<>();
	private final List<pendingRequest>	outstanding_requests 	= new ArrayList<>();

	private final BitSet	piece_map	= new BitSet();

	private long	last_http_activity_time;

	private final networkConnectionKey	network_connection_key;

	private boolean	closing;
	private boolean	destroyed;

	private final String	last_modified_date;
	private String	content_type	= DEFAULT_CONTENT_TYPE;

	CopyOnWriteList<requestListener>	request_listeners = null;

	protected
	HTTPNetworkConnection(
		HTTPNetworkManager		_manager,
		NetworkConnection		_connection,
		PEPeerTransport			_peer )
	{
		manager			= _manager;
		connection		= _connection;
		peer			= _peer;

		DiskManager dm = peer.getManager().getDiskManager();

		long	last_modified = 0;

		try{
			last_modified = dm.getFiles()[0].getFile(true).lastModified();

		}catch( Throwable e ){
		}

		last_modified_date = TimeFormatter.getHTTPDate( last_modified );

		network_connection_key = new networkConnectionKey();

		last_http_activity_time	= SystemTime.getCurrentTime();

		decoder	= (HTTPMessageDecoder)connection.getIncomingMessageQueue().getDecoder();
		encoder = (HTTPMessageEncoder)connection.getOutgoingMessageQueue().getEncoder();

		synchronized( http_connection_map ){

			List<HTTPNetworkConnection>	connections = http_connection_map.get( network_connection_key );

			if ( connections == null ){

				connections = new ArrayList<>();

				http_connection_map.put( network_connection_key, connections );
			}

			connections.add( this );

			if ( connections.size() > MAX_CON_PER_ENDPOINT ){

				checkConnections( connections );
			}
		}

			// note that the decoder can synchronously call-back if is preloaded with a header
			// here...

		encoder.setConnection( this );
		decoder.setConnection( this );
	}

	protected boolean
	isSeed()
	{
		if ( ( !peer.getControl().isSeeding()) || peer.getControl().getHiddenBytes() > 0 ){

			if (Logger.isEnabled()){
				Logger.log(new LogEvent(peer,LOGID, "Download is not seeding" ));
			}

			sendAndClose( manager.getNotFound());

			return( false );
		}

		return( true );
	}

	protected void
	setContentType(
		String	ct )
	{
		content_type	= ct;
	}

	protected HTTPNetworkManager
	getManager()
	{
		return( manager );
	}

	protected NetworkConnection
	getConnection()
	{
		return( connection );
	}

	protected PEPeerTransport
	getPeer()
	{
		return( peer );
	}
	protected PEPeerControl
	getPeerControl()
	{
		return( peer.getControl());
	}

	protected RawMessage
	encodeChoke()
	{
		synchronized( outstanding_requests ){

			choked	= true;
		}

		return( null );
	}

	protected RawMessage
	encodeUnchoke()
	{
		synchronized( outstanding_requests ){

			choked	= false;

			for (int i=0;i<choked_requests.size();i++){

				decoder.addMessage((BTRequest)choked_requests.get(i));
			}

			choked_requests.clear();
		}

		return( null );
	}

	protected RawMessage
	encodeBitField()
	{
		decoder.addMessage( new BTInterested((byte)1));

		return( null );
	}

	protected void
	readWakeup()
	{
		connection.getTransport().setReadyForRead();
	}

	protected RawMessage
	encodeHandShake(
		Message	message )
	{
		return( null );
	}

	protected abstract void
	decodeHeader(
		HTTPMessageDecoder		decoder,
		String					header )

		throws IOException;

	protected String
	encodeHeader(
		httpRequest	request )
	{
		String	current_date = TimeFormatter.getHTTPDate( SystemTime.getCurrentTime());

		StringBuilder res = new StringBuilder(256);

		boolean	partial = request.isPartialContent();

		res.append( "HTTP/1.1 " );
		res.append( partial?"206 Partial Content":"200 OK" );
			res.append( NL );

		res.append( "Content-Type: " );
		res.append( content_type );
	 		res.append( NL );

		res.append( "Date: " );
		res.append( current_date );
		 	res.append( NL );

		res.append( "Last-Modified: " );
		res.append( last_modified_date );
			res.append( NL );

		res.append( HDR_CACHE_CONTROL );

			// not sure about ETag. I was going to use the torrent hash but I don't understand the link
			// between URL, range requests and ETags. Do we need to generate different ETags for each
			// webseed piece request URL or can we use the torrent hash and rely on the fact that the
			// URL changes? Are range-requests irrelevant as far as ETags go - I'd like to think so...

		res.append( HDR_SERVER );

		if ( partial ){

			long[] offsets = request.getOriginalOffsets();
			long[] lengths = request.getOriginalLengths();

			long content_length = request.getContentLength();

			if ( offsets.length == 1 && content_length > 0 ){

				res.append("Content-Range: bytes ").append(offsets[0]).append("-").append(offsets[0] + lengths[0] - 1).append("/")
					.append(content_length);
				res.append( NL );
			}
		}
		res.append( "Connection: " );
		res.append( request.keepAlive()?"Keep-Alive":"Close" );
			res.append( NL );

		if ( request.keepAlive()){

			res.append( HDR_KEEP_ALIVE_TIMEOUT );
		}

		res.append( "Content-Length: " );
		res.append( request.getTotalLength());
		res.append( NL );

		res.append( NL );

		return( res.toString());
	}

	protected void
	addRequest(
		httpRequest		request )

		throws IOException
	{
		last_http_activity_time	= SystemTime.getCurrentTime();

		PEPeerControl	control = getPeerControl();

		if ( !sent_handshake ){

			sent_handshake	= true;

			decoder.addMessage( new BTHandshake( control.getHash(), peer_id, BTHandshake.BT_RESERVED_MODE, (byte)1 ));

			byte[]	bits = new byte[(control.getPieces().length +7) /8];

			DirectByteBuffer buffer = new DirectByteBuffer( ByteBuffer.wrap( bits ));

			decoder.addMessage( new BTBitfield( buffer, (byte)1 ));
		}

		synchronized( outstanding_requests ){

			http_requests.add( request );
		}

		submitBTRequests();
	}

	protected void
	submitBTRequests()

		throws IOException
	{
		PEPeerControl	control = getPeerControl();

		long	piece_size = control.getPieceLength(0);

		synchronized( outstanding_requests ){

			while( outstanding_requests.size() < MAX_OUTSTANDING_BT_REQUESTS && http_requests.size() > 0 ){

				httpRequest	http_request = (httpRequest)http_requests.get(0);

				long[]	offsets	= http_request.getModifiableOffsets();
				long[]	lengths	= http_request.getModifiableLengths();

				int	index	= http_request.getIndex();

				long	offset 	= offsets[index];
				long	length	= lengths[index];

				int		this_piece_number 	= (int)(offset / piece_size);
				int		this_piece_size		= control.getPieceLength( this_piece_number );

				int		offset_in_piece 	= (int)( offset - ( this_piece_number * piece_size ));

				int		space_this_piece 	= this_piece_size - offset_in_piece;

				int		request_size = (int)Math.min( length, space_this_piece );

				request_size = Math.min( request_size, max_read_block_size );

				addBTRequest(
					new BTRequest(
							this_piece_number,
							offset_in_piece,
							request_size,
							(byte)1),
					http_request );

				if ( request_size == length ){

					if ( index == offsets.length - 1 ){

						http_requests.remove(0);

					}else{

						http_request.setIndex( index+1 );
					}
				}else{
					offsets[index] += request_size;
					lengths[index] -= request_size;
				}
			}
		}
	}

	protected void
	addBTRequest(
		BTRequest		request,
		httpRequest		http_request )

		throws IOException
	{
		synchronized( outstanding_requests ){

			if ( destroyed ){

				throw( new IOException( "HTTP connection destroyed" ));
			}

			outstanding_requests.add( new pendingRequest( request, http_request ));

			if ( choked ){

				if ( choked_requests.size() > 1024 ){

					Debug.out( "pending request limit exceeded" );

				}else{

					choked_requests.add( request );
				}
			}else{

				decoder.addMessage( request );
			}
		}
	}

	protected RawMessage[]
	encodePiece(
		Message		message )
	{
		last_http_activity_time	= SystemTime.getCurrentTime();

		BTPiece	piece = (BTPiece)message;

		List<pendingRequest>	ready_requests = new ArrayList<>();

		boolean	found = false;

		synchronized( outstanding_requests ){

			if ( destroyed ){

				return( new RawMessage[]{ getEmptyRawMessage( message )});
			}

			for (int i=0;i<outstanding_requests.size();i++){

				pendingRequest	req = outstanding_requests.get(i);

				if ( 	req.getPieceNumber() == piece.getPieceNumber() &&
						req.getStart() 	== piece.getPieceOffset() &&
						req.getLength() == piece.getPieceData().remaining( DirectByteBuffer.SS_NET )){

					if ( req.getBTPiece() == null ){

						req.setBTPiece( piece );

						found	= true;

						if ( i == 0 ){

							Iterator<pendingRequest>	it = outstanding_requests.iterator();

							while( it.hasNext()){

								pendingRequest r = it.next();

								BTPiece	btp = r.getBTPiece();

								if ( btp == null ){

									break;
								}

								it.remove();

								ready_requests.add( r );
							}
						}

						break;
					}
				}
			}
		}

		if ( !found ){

			Debug.out( "request not matched" );

			return( new RawMessage[]{ getEmptyRawMessage( message )});
		}

		if ( ready_requests.size() == 0 ){

			return( new RawMessage[]{ getEmptyRawMessage( message )});
		}

		try{
			submitBTRequests();

		}catch( IOException e ){

		}

		pendingRequest req	= (pendingRequest)ready_requests.get(0);

		DirectByteBuffer[]	buffers;

		httpRequest	http_request = req.getHTTPRequest();

		RawMessage[]	raw_messages = new RawMessage[ ready_requests.size()];

		for (int i=0;i<raw_messages.length;i++){

			buffers = new DirectByteBuffer[ 2 ];

			if ( !http_request.hasSentFirstReply()){

				http_request.setSentFirstReply();

				String	header = encodeHeader( http_request );

				buffers[0] = new DirectByteBuffer( ByteBuffer.wrap( header.getBytes()));

			}else{

					// we have to do this as core code assumes buffer entry 0 is protocol

				buffers[0] = new DirectByteBuffer( ByteBuffer.allocate(0));
			}

			req	= (pendingRequest)ready_requests.get(i);

			BTPiece	this_piece = req.getBTPiece();

			int	piece_number = this_piece.getPieceNumber();

			if ( !piece_map.get( piece_number )){

					// kinda crappy as it triggers on first block of piece, however better
					// than nothing

				piece_map.set( piece_number );

				decoder.addMessage( new BTHave( piece_number, (byte)1 ));
			}

			buffers[1] = this_piece.getPieceData();

			req.logQueued();

			if ( request_listeners != null ){

				Iterator<requestListener> it = request_listeners.iterator();

				while( it.hasNext()){

					((requestListener)it.next()).requestComplete( req );
				}
			}

			raw_messages[i] =
				new RawMessageImpl(
						this_piece,
						buffers,
						RawMessage.PRIORITY_HIGH,
						true,
						new Message[0] );
		}

		return( raw_messages );
	}

	protected int
	getRequestCount()
	{
		synchronized( outstanding_requests ){

			return( http_requests.size());
		}
	}

	protected boolean
	isClosing()
	{
		return( closing );
	}

	protected void
	close(
		String	reason )
	{
		closing	= true;

		peer.getControl().removePeer( peer, reason, Transport.CR_NONE );
	}

	protected void
	destroy()
	{
		synchronized( http_connection_map ){

			List<HTTPNetworkConnection>	connections = http_connection_map.get( network_connection_key );

			if ( connections != null ){

				connections.remove( this );

				if ( connections.size() == 0 ){

					http_connection_map.remove( network_connection_key );
				}
			}
		}

		synchronized( outstanding_requests ){

			destroyed	= true;

			for (int i=0;i<outstanding_requests.size();i++){

				pendingRequest	req = (pendingRequest)outstanding_requests.get(i);

				BTPiece	piece = req.getBTPiece();

				if ( piece != null ){

					piece.destroy();
				}
			}

			outstanding_requests.clear();

			for (int i=0;i<choked_requests.size();i++){

				BTRequest	req = (	BTRequest)choked_requests.get(i);

				req.destroy();
			}

			choked_requests.clear();
		}
	}

	protected long
	getTimeSinceLastActivity()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now < last_http_activity_time ){

			last_http_activity_time = now;
		}

		return( now - last_http_activity_time );
	}

	protected void
	log(
		String	str )
	{
		if (Logger.isEnabled()){
			Logger.log(new LogEvent( getPeer(),LOGID, str));
		}
	}

	protected RawMessage
	getEmptyRawMessage(
		Message	message )
	{
		return(
			new RawMessageImpl(
					message,
					new DirectByteBuffer[]{ new DirectByteBuffer( ByteBuffer.allocate(0))},
					RawMessage.PRIORITY_HIGH,
					true,
					new Message[0] ));
	}

	protected void
	sendAndClose(
		String		data )
	{
		final Message	http_message = new HTTPMessage( data );

		getConnection().getOutgoingMessageQueue().registerQueueListener(
			new OutgoingMessageQueue.MessageQueueListener()
			{
				@Override
				public boolean
				messageAdded(
					Message message )
				{
					return( true );
				}

				@Override
				public void
				messageQueued(
					Message message )
				{
				}

				@Override
				public void
				messageRemoved(
					Message message )
				{
				}

				@Override
				public void
				messageSent(
					Message message )
				{
					if ( message == http_message ){

						close( "Close after message send complete" );
					}
				}

			    @Override
			    public void
			    protocolBytesSent(
			    	int byte_count )
			    {
			    }

			    @Override
			    public void
			    dataBytesSent(
			    	int byte_count )
			    {
			    }

			    @Override
			    public void flush(){}
			});

		getConnection().getOutgoingMessageQueue().addMessage( http_message, false );
	}

	protected void
	flushRequests(
		final flushListener		l )
	{
		boolean	sync_fire = false;

		synchronized( outstanding_requests ){

			final int request_count = outstanding_requests.size();

			if ( request_count == 0 ){

				sync_fire = true;

			}else{

				if ( request_listeners == null ){

					request_listeners = new CopyOnWriteList<>();
				}

				request_listeners.add(
					new requestListener()
					{
						int	num_to_go = request_count;

						@Override
						public void
						requestComplete(
							pendingRequest r )
						{
							num_to_go--;

							if ( num_to_go == 0 ){

								request_listeners.remove( this );

								flushRequestsSupport( l );
							}
						}
					});
			}
		}

		if ( sync_fire ){

			flushRequestsSupport( l );
		}
	}

	protected void
	flushRequestsSupport(
		final flushListener		l )
	{
		OutgoingMessageQueue omq = getConnection().getOutgoingMessageQueue();

		final Message	http_message = new HTTPMessage( new byte[0] );

		omq.registerQueueListener(
			new OutgoingMessageQueue.MessageQueueListener()
			{
				@Override
				public boolean
				messageAdded(
					Message message )
				{
					return( true );
				}

				@Override
				public void
				messageQueued(
					Message message )
				{
				}

				@Override
				public void
				messageRemoved(
					Message message )
				{
				}

				@Override
				public void
				messageSent(
					Message message )
				{
					if ( message == http_message ){

						l.flushed();
					}
				}

			    @Override
			    public void
			    protocolBytesSent(
			    	int byte_count )
			    {
			    }

			    @Override
			    public void
			    dataBytesSent(
			    	int byte_count )
			    {
			    }

			    @Override
			    public void flush(){}
			});

		omq.addMessage( http_message, false );

			// if after adding the message there's no bytes on the queue then we need to trigger an
			// immediate flushed event as the queue won't get processed (0 bytes on it...)

		if ( omq.getTotalSize() == 0 ){

			l.flushed();
		}
	}

	protected static class
	httpRequest
	{
		private final long[]	orig_offsets;
		private final long[]	orig_lengths;
		private final long		content_length;
		private final boolean	partial_content;
		private final boolean	keep_alive;

		private final long[]	mod_offsets;
		private final long[]	mod_lengths;

		private int		index;
		private long	total_length;
		private boolean	sent_first_reply;

		protected
		httpRequest(
			long[]		_offsets,
			long[]		_lengths,
			long		_content_length,
			boolean		_partial_content,
			boolean		_keep_alive )
		{
			orig_offsets	= _offsets;
			orig_lengths	= _lengths;
			content_length	= _content_length;
			partial_content	= _partial_content;
			keep_alive		= _keep_alive;

			/*
			String	str ="";
			for (int i=0;i<lengths.length;i++){
				str += (i==0?"":",") +"[" + offsets[i] + "/" + lengths[i] + "]";
			}
			System.out.println( network_connection_key.getName() + ": requested " + str + ",part=" + partial_content +",ka=" + keep_alive );
			*/

			mod_offsets = orig_offsets.clone();
			mod_lengths = orig_lengths.clone();

			for (int i=0;i<orig_lengths.length;i++){

				total_length += orig_lengths[i];
			}
		}

		protected boolean
		isPartialContent()
		{
			return( partial_content );
		}

		protected long
		getContentLength()
		{
			return( content_length );
		}

		protected boolean
		hasSentFirstReply()
		{
			return( sent_first_reply );
		}

		protected void
		setSentFirstReply()
		{
			sent_first_reply	= true;
		}

		protected long[]
		getOriginalOffsets()
		{
			return( orig_offsets );
		}

		protected long[]
   		getOriginalLengths()
   		{
   			return( orig_lengths );
   		}

		protected long[]
   		getModifiableOffsets()
   		{
   			return( mod_offsets );
   		}

   		protected long[]
  		getModifiableLengths()
  		{
  			return( mod_lengths );
  		}

		protected int
		getIndex()
		{
			return( index );
		}

		protected void
		setIndex(
			int	_index )
		{
			index = _index;
		}

		protected long
		getTotalLength()
		{
			return( total_length );
		}

		protected boolean
		keepAlive()
		{
			return( keep_alive );
		}
	}

	protected interface
	flushListener
	{
		public void
		flushed();
	}

	protected interface
	requestListener
	{
		public void
		requestComplete(
			pendingRequest	request );
	}

	private static class
	pendingRequest
	{
		private final int	piece;
		private final int	start;
		private final int	length;

		private final httpRequest	http_request;

		private BTPiece	bt_piece;

		protected
		pendingRequest(
			BTRequest		_request,
			httpRequest		_http_request )
		{
			piece	= _request.getPieceNumber();
			start	= _request.getPieceOffset();
			length	= _request.getLength();

			http_request	= _http_request;

			/*
			if ( peer.getIp().equals( "64.71.5.2")){

				TimeFormatter.milliTrace(
						"http_req_create: " +
							piece + "/" + start +
							" [hr=" + http_requests.size() +
							",cr=" + choked_requests.size() +
							",or=" + outstanding_requests.size() +
							",d=" + decoder.getQueueSize() + "]" );
			}
			*/
		}

		protected int
		getPieceNumber()
		{
			return( piece );
		}

		protected int
		getStart()
		{
			return( start );
		}

		protected int
		getLength()
		{
			return( length );
		}

		protected httpRequest
		getHTTPRequest()
		{
			return( http_request );
		}

		protected BTPiece
		getBTPiece()
		{
			return( bt_piece );
		}

		protected void
		setBTPiece(
			BTPiece	_bt_piece )
		{
			bt_piece	= _bt_piece;

			/*
			if ( peer.getIp().equals( "64.71.5.2")){

				TimeFormatter.milliTrace(
					"http_req_data: " +
						piece + "/" + start +
						" [hr=" + http_requests.size() +
						",cr=" + choked_requests.size() +
						",or=" + outstanding_requests.size() +
						",d=" + decoder.getQueueSize() + "]" );
			}
			*/
		}

		protected void
		logQueued()
		{
			/*
			if ( peer.getIp().equals( "64.71.5.2")){

				TimeFormatter.milliTrace(
					"http_req_out: " +
						piece + "/" + start +
						" [hr=" + http_requests.size() +
						",cr=" + choked_requests.size() +
						",or=" + outstanding_requests.size() +
						",d=" + decoder.getQueueSize() + "]" );
			}
			*/
		}
	}

	protected class
	networkConnectionKey
	{
		public boolean
		equals(Object obj)
		{
			if ( obj instanceof networkConnectionKey ){

				networkConnectionKey	other = (networkConnectionKey)obj;

				return( Arrays.equals( getAddress(), other.getAddress()) &&
						Arrays.equals(getHash(),other.getHash()));

			}else{

				return( false );
			}
		}

		protected String
		getName()
		{
			return( peer.getControl().getDisplayName() + ": " + connection.getEndpoint().getNotionalAddress().getAddress().getHostAddress());
		}

		protected byte[]
		getAddress()
		{
			return( AddressUtils.getAddressBytes( connection.getEndpoint().getNotionalAddress()));
		}

		protected byte[]
		getHash()
		{
			return( peer.getControl().getHash());
		}

		public int
		hashCode()
		{
			return( peer.getControl().hashCode());

		}
	}
}
