/*
 * Created on Sep 12, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.dht.transport.util;

import java.util.*;

import com.biglybt.core.dht.DHTLogger;
import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.DHTTransportException;
import com.biglybt.core.dht.transport.DHTTransportProgressListener;
import com.biglybt.core.dht.transport.DHTTransportTransferHandler;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPPacketData;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;



public class
DHTTransferHandler
{
	private static final int	TRANSFER_QUEUE_MAX			= 128;
	private static final long 	MAX_TRANSFER_QUEUE_BYTES	= 8*1024*1024;

	private static final long	WRITE_XFER_RESEND_DELAY_BASE		= 12500;
	private static final long	READ_XFER_REREQUEST_DELAY_BASE		= 5000;
	private static final long	WRITE_REPLY_TIMEOUT_BASE			= 60000;

	private final long	WRITE_XFER_RESEND_DELAY;
	private final long	READ_XFER_REREQUEST_DELAY;
	final long	WRITE_REPLY_TIMEOUT;

	private static final boolean	XFER_TRACE	= false;

	private final Map<HashWrapper,transferHandlerInterceptor> transfer_handlers 	= new HashMap<>();

	private final Map<Long,transferQueue>	read_transfers		= new HashMap<>();
	private final Map<Long,transferQueue> write_transfers		= new HashMap<>();

	private long	last_xferq_log;

	int 	active_write_queue_processor_count;
	long	total_bytes_on_transfer_queues;

	final Map<HashWrapper,Object>	call_transfers		= new HashMap<>();

	private final Adapter			adapter;
	private final int				max_data;
	private final DHTLogger			logger;

	final AEMonitor	this_mon	= new AEMonitor( "DHTTransferHandler" );

	public
	DHTTransferHandler(
		Adapter		_adapter,
		int			_max_data,
		DHTLogger	_logger )
	{
		this( _adapter, _max_data, 2, _logger );
	}

	public
	DHTTransferHandler(
		Adapter		_adapter,
		int			_max_data,
		float		_latency_indicator,
		DHTLogger	_logger )
	{
		adapter		= _adapter;
		max_data	= _max_data;
		logger		= _logger;

		WRITE_XFER_RESEND_DELAY 	= (long)( _latency_indicator*WRITE_XFER_RESEND_DELAY_BASE );
		READ_XFER_REREQUEST_DELAY	= (long)( _latency_indicator*READ_XFER_REREQUEST_DELAY_BASE );
		WRITE_REPLY_TIMEOUT			= (long)( _latency_indicator*WRITE_REPLY_TIMEOUT_BASE );
	}


	public void
	registerTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler )
	{
		registerTransferHandler( handler_key, handler, null );
	}

	public void
	registerTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler,
		Map<String,Object>			options )
	{
		if ( XFER_TRACE ){
			log( "Transfer handler (" + handler.getName() + ") registered for key '" + ByteFormatter.encodeString( handler_key ));
		}

		synchronized( transfer_handlers ){

			transferHandlerInterceptor existing =
				transfer_handlers.put(
					new HashWrapper( handler_key ),
					new transferHandlerInterceptor(
							handler, options ));

			if ( existing != null ){

				Debug.out( "Duplicate transfer handler: existing=" + existing.getName() + ", new=" + handler.getName());
			}
		}
	}

	public void
	unregisterTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler )
	{
		if ( XFER_TRACE ){
			log( "Transfer handler (" + handler.getName() + ") unregistered for key '" + ByteFormatter.encodeString( handler_key ));
		}

		synchronized( transfer_handlers ){

			transfer_handlers.remove( new HashWrapper( handler_key ));
		}
	}

	protected int
	handleTransferRequest(
		DHTTransportContact			target,
		long						connection_id,
		byte[]						transfer_key,
		byte[]						request_key,
		byte[]						data,
		int							start,
		int							length,
		boolean						write_request,
		boolean						first_packet_only )

		throws DHTTransportException
	{
		transferHandlerInterceptor	handler;

		synchronized( transfer_handlers ){

			handler = transfer_handlers.get(new HashWrapper( transfer_key ));
		}

		if ( handler == null ){

			// can get a lot of these on startup so we'll downgrade to just ignoring
			if ( XFER_TRACE ){
				log( "No transfer handler registered for key '" + ByteFormatter.encodeString(transfer_key) + "'" );
			}
			//throw( new DHTTransportException( "No transfer handler registered for " + ByteFormatter.encodeString(transfer_key) ));

			return( -1 );
		}

		if ( data == null ){

			data = handler.handleRead( target, request_key );
		}

		if ( data == null ){

			return( -1 );

		}else{

				// special case 0 length data

			if ( data.length == 0 ){

				if ( write_request ){

					sendWriteRequest(
							connection_id,
							target,
							transfer_key,
							request_key,
							data,
							0,
							0,
							0 );
				}else{

					sendReadReply(
							connection_id,
							target,
							transfer_key,
							request_key,
							data,
							0,
							0,
							0 );
				}
			}else{

				if ( start < 0 ){

					start	= 0;

				}else if ( start >= data.length ){

					log( "dataRequest: invalid start position" );

					return( data.length );
				}

				if ( length <= 0 ){

					length = data.length;

				}else if ( start + length > data.length ){

					log( "dataRequest: invalid length" );

					return( data.length );
				}

				int	end = start+length;

				while( start < end ){

					int	chunk = end - start;

					if ( chunk > max_data ){

						chunk = max_data;
					}

					if ( write_request ){

						sendWriteRequest(
								connection_id,
								target,
								transfer_key,
								request_key,
								data,
								start,
								chunk,
								data.length );

						if ( first_packet_only ){

							break;
						}
					}else{

						sendReadReply(
								connection_id,
								target,
								transfer_key,
								request_key,
								data,
								start,
								chunk,
								data.length );
					}

					start += chunk;
				}
			}

			return( data.length );
		}
	}

	public void
	receivePacket(
		final DHTTransportContact		originator,
		final Packet					req )
	{
		/*
		if ((int)(Math.random() * 4 )== 0 ){

			System.out.println("dropping request packet:" + req.getString());

			return;
		}
		*/

			// both requests and replies come through here. Currently we only support read
			// requests so we can safely use the data.length == 0 test to discriminate between
			// a request and a reply to an existing transfer

		byte	packet_type = req.getPacketType();

		if ( XFER_TRACE ){
			System.out.println( "dataRequest: originator=" + originator.getAddress() + ",packet=" + req.getString());
		}

		if ( packet_type == Packet.PT_READ_REPLY ){

			transferQueue	queue = lookupTransferQueue( read_transfers, req.getConnectionId());

				// unmatched -> drop it

			if ( queue != null ){

				queue.add( req );

			}else{

				if ( XFER_TRACE ){
					System.out.println( "Read queue not found" );
				}
			}

		}else if ( packet_type == Packet.PT_WRITE_REPLY ){

			transferQueue	queue = lookupTransferQueue( write_transfers, req.getConnectionId());

				// unmatched -> drop it

			if ( queue != null ){

				queue.add( req );

			}else{

				if ( XFER_TRACE ){
					System.out.println( "Write queue not found" );
				}
			}
		}else{

			byte[]	transfer_key = req.getTransferKey();

			if ( packet_type == Packet.PT_READ_REQUEST ){

				try{
					handleTransferRequest(
							originator,
							req.getConnectionId(),
							transfer_key,
							req.getRequestKey(),
							null,
							req.getStartPosition(),
							req.getLength(),
							false, false );

				}catch( DHTTransportException e ){

					log(e);
				}

			}else{

					// 	write request

				transferQueue	old_queue = lookupTransferQueue( read_transfers, req.getConnectionId());

				if ( old_queue != null ){

					old_queue.add( req );

				}else{

					final transferHandlerInterceptor	handler;

					synchronized( transfer_handlers ){

						handler = transfer_handlers.get(new HashWrapper( transfer_key ));
					}

					if ( handler == null ){

						// get lots of these when local endpoint removed while other's still out there...
						if ( XFER_TRACE ){
							log( "No transfer handler registered for key '" + ByteFormatter.encodeString(transfer_key) + "'" );
						}
					}else{
						try{

							int	req_total_len = req.getTotalLength();

							if (  handler.getBooleanOption( "disable_call_acks", false ) && req_total_len == req.getLength()){

								byte[] write_data = req.getData();

								if ( write_data.length != req_total_len ){

									byte[]	temp = new byte[req_total_len];

									System.arraycopy( write_data, 0, temp, 0, req_total_len );

									write_data = temp;
								}

								final byte[]	reply_data = handler.handleWrite( originator, req.getConnectionId(), req.getRequestKey(), write_data );

								if ( reply_data != null ){

									if ( reply_data.length <= max_data ){

										long write_connection_id = adapter.getConnectionID();

										sendWriteRequest(
												write_connection_id,
												originator,
												transfer_key,
												req.getRequestKey(),
												reply_data,
												0,
												reply_data.length,
												reply_data.length );
									}else{

										try{
											this_mon.enter();

											if ( active_write_queue_processor_count >= TRANSFER_QUEUE_MAX ){

												throw( new DHTTransportException( "Active write queue process thread limit exceeded" ));
											}

											active_write_queue_processor_count++;

											if ( XFER_TRACE ){
												System.out.println( "active_write_queue_processor_count=" + active_write_queue_processor_count );
											}
										}finally{

											this_mon.exit();
										}

										new AEThread2( "DHTTransportUDP:writeQueueProcessor", true )
										{
											@Override
											public void
											run()
											{
												try{

													writeTransfer(
															XFER_TRACE ? new DHTTransportProgressListenerTRACE("writeXferReply") : null,
															originator,
															req.getTransferKey(),
															req.getRequestKey(),
															reply_data,
															WRITE_REPLY_TIMEOUT );

												}catch( DHTTransportException e ){

													log( "Failed to process transfer queue: " + Debug.getNestedExceptionMessage(e));

												}finally{

													try{
														this_mon.enter();

														active_write_queue_processor_count--;

														if ( XFER_TRACE ){
															System.out.println( "active_write_queue_processor_count=" + active_write_queue_processor_count );
														}
													}finally{

														this_mon.exit();
													}
												}
											}
										}.start();

											// indicate that at least one packet has been received

										sendWriteReply(
											req.getConnectionId(),
											originator,
											req.getTransferKey(),
											req.getRequestKey(),
											req.getStartPosition(),
											req.getLength());
									}
								}
							}else{

								final transferQueue new_queue = new transferQueue( originator, read_transfers, req.getConnectionId());

									// add the initial data for this write request

								new_queue.add( req );

									// set up the queue processor

								try{
									this_mon.enter();

									if ( active_write_queue_processor_count >= TRANSFER_QUEUE_MAX ){

										new_queue.destroy();

										throw( new DHTTransportException( "Active write queue process thread limit exceeded" ));
									}

									active_write_queue_processor_count++;

									if ( XFER_TRACE ){
										System.out.println( "active_write_queue_processor_count=" + active_write_queue_processor_count );
									}
								}finally{

									this_mon.exit();
								}

								new AEThread2( "DHTTransportUDP:writeQueueProcessor", true )
									{
										@Override
										public void
										run()
										{
											try{
												byte[] write_data =
													runTransferQueue(
														new_queue,
														XFER_TRACE ? new DHTTransportProgressListenerTRACE("writeXfer") : null,
														originator,
														req.getTransferKey(),
														req.getRequestKey(),
														60000,
														false );

												if ( write_data != null ){

														// xfer complete, send ack if multi-packet xfer
														// (ack already sent below if single packet)

													if ( 	req.getStartPosition() != 0 ||
															req.getLength() != req.getTotalLength() ){

														sendWriteReply(
																req.getConnectionId(),
																originator,
																req.getTransferKey(),
																req.getRequestKey(),
																0,
																req.getTotalLength());
													}

													byte[]	reply_data = handler.handleWrite( originator, req.getConnectionId(), req.getRequestKey(), write_data );

													if ( reply_data != null ){

														writeTransfer(
																XFER_TRACE ? new DHTTransportProgressListenerTRACE("writeXferReply") : null,
																originator,
																req.getTransferKey(),
																req.getRequestKey(),
																reply_data,
																WRITE_REPLY_TIMEOUT );

													}
												}

											}catch( DHTTransportException e ){

												log( "Failed to process transfer queue: " + Debug.getNestedExceptionMessage(e));

											}finally{

												try{
													this_mon.enter();

													active_write_queue_processor_count--;

													if ( XFER_TRACE ){
														System.out.println( "active_write_queue_processor_count=" + active_write_queue_processor_count );
													}
												}finally{

													this_mon.exit();
												}
											}
										}
									}.start();

										// indicate that at least one packet has been received

								sendWriteReply(
									req.getConnectionId(),
									originator,
									req.getTransferKey(),
									req.getRequestKey(),
									req.getStartPosition(),
									req.getLength());
							}
						}catch( DHTTransportException e ){

							long now = SystemTime.getMonotonousTime();

							if ( last_xferq_log == 0 || now - last_xferq_log > 5*60*1000 ){

								last_xferq_log = now;

								log( "Failed to create transfer queue" );

								log( e );
							}
						}
					}
				}
			}
		}
	}

	public byte[]
	readTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handler_key,
		byte[]							key,
		long							timeout )

		throws DHTTransportException
	{
		long	connection_id 	= adapter.getConnectionID();

		transferQueue	transfer_queue = new transferQueue( target, read_transfers, connection_id );

		return( runTransferQueue( transfer_queue, listener, target, handler_key, key, timeout, true ));
	}

	protected byte[]
	runTransferQueue(
		transferQueue					transfer_queue,
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handler_key,
		byte[]							key,
		long							timeout,
		boolean							read_transfer )

		throws DHTTransportException
	{
		SortedSet<Packet>	packets =
				new TreeSet<>(
						new Comparator<Packet>() {
							@Override
							public int
							compare(
									Packet p1,
									Packet p2) {
								return (p1.getStartPosition() - p2.getStartPosition());
							}
						});

		int	entire_request_count = 0;

		int transfer_size 	= -1;
		int	transferred		= 0;

		String	target_name = DHTLog.getString2(target.getID());

		try{
			long	start = SystemTime.getCurrentTime();

			if ( read_transfer ){

				if ( listener != null ) {
					listener.reportActivity( getMessageText( "request_all", target_name ));
				}

				entire_request_count++;

				sendReadRequest( transfer_queue.getConnectionID(), target, handler_key, key, 0, 0 );

			}else{

					// write transfer - data already on its way, no need to request it

				entire_request_count++;
			}

			while( SystemTime.getCurrentTime() - start <= timeout ){

				Packet	reply = transfer_queue.receive( READ_XFER_REREQUEST_DELAY );

				if ( reply != null ){

					if ( listener != null && transfer_size == -1 ){

						transfer_size = reply.getTotalLength();

						listener.reportSize( transfer_size );
					}

					Iterator<Packet>	it = packets.iterator();

					boolean	duplicate = false;

					while( it.hasNext()){

						Packet	p = it.next();

							// ignore overlaps

						if (	p.getStartPosition() < reply.getStartPosition() + reply.getLength() &&
								p.getStartPosition() + p.getLength() > reply.getStartPosition()){

							duplicate	= true;

							break;
						}
					}

					if ( !duplicate ){

						if ( listener != null ) {
  						listener.reportActivity(
  								getMessageText( "received_bit",
  								new String[]{
  										String.valueOf( reply.getStartPosition()),
  										String.valueOf(reply.getStartPosition() + reply.getLength()),
  										target_name }));
						}

						transferred += reply.getLength();

						if ( listener != null ) {
							listener.reportCompleteness( transfer_size==0?100: ( 100 * transferred / transfer_size ));
						}

						packets.add( reply );

							// see if we're done

						it = packets.iterator();

						int	pos			= 0;
						int	actual_end	= -1;

						while( it.hasNext()){

							Packet	p = (Packet)it.next();

							if ( actual_end == -1 ){

								actual_end = p.getTotalLength();
							}

							if ( p.getStartPosition() != pos ){

									// missing data, give up

								break;
							}

							pos += p.getLength();

							if ( pos == actual_end ){

									// huzzah, we got the lot

								if ( listener != null ) {
									listener.reportActivity( getMessageText( "complete" ));
								}

								byte[]	result = new byte[actual_end];

								it =  packets.iterator();

								pos	= 0;

								while( it.hasNext()){

									p = (Packet)it.next();

									System.arraycopy( p.getData(), 0, result, pos, p.getLength());

									pos	+= p.getLength();
								}

								return( result );
							}
						}
					}
				}else{

						// timeout, look for missing bits

					if ( packets.size() == 0 ){

						if ( entire_request_count == 2 ){

							if ( listener != null ) {
								listener.reportActivity( getMessageText( "timeout", target_name ));
							}

							return( null );
						}

						entire_request_count++;

						if ( listener != null ) {
							listener.reportActivity( getMessageText( "rerequest_all", target_name ));
						}

						sendReadRequest( transfer_queue.getConnectionID(), target, handler_key, key, 0, 0 );

					}else{

						Iterator<Packet> it = packets.iterator();

						int	pos			= 0;
						int	actual_end	= -1;

						while( it.hasNext()){

							Packet	p = it.next();

							if ( actual_end == -1 ){

								actual_end = p.getTotalLength();
							}

							if ( p.getStartPosition() != pos ){

								if ( listener != null ) {
  								listener.reportActivity(
  										getMessageText( "rerequest_bit",
  												new String[]{
  													String.valueOf( pos ),
  													String.valueOf( p.getStartPosition()),
  													target_name }));
								}

								sendReadRequest(
										transfer_queue.getConnectionID(),
										target,
										handler_key,
										key,
										pos,
										p.getStartPosition()-pos );

							}

							pos = p.getStartPosition() + p.getLength();
						}

						if ( pos != actual_end ){

							if ( listener != null ) {
  							listener.reportActivity(
  									getMessageText( "rerequest_bit",
  											new String[]{
  												String.valueOf( pos ),
  												String.valueOf( actual_end ),
  												target_name }));
							}

							sendReadRequest(
									transfer_queue.getConnectionID(),
									target,
									handler_key,
									key,
									pos,
									actual_end - pos );
						}
					}
				}
			}

			if ( listener != null ) {
  			if ( packets.size()==0 ){

  				listener.reportActivity( getMessageText( "timeout", target_name ));

  			}else{

  				listener.reportActivity(
  						getMessageText(
  							"timeout_some",
  							new String[]{ String.valueOf( packets.size()), target_name }));

  			}
			}

			return( null );

		}finally{

			transfer_queue.destroy();
		}
	}

	public void
	writeTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handler_key,
		byte[]							key,
		byte[]							data,
		long							timeout )

		throws DHTTransportException
	{
		long	connection_id 	= adapter.getConnectionID();

		writeTransfer( listener, target, connection_id, handler_key, key, data, timeout );
	}

	private void
	writeTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		long							connection_id,
		byte[]							handler_key,
		byte[]							key,
		byte[]							data,
		long							timeout )

		throws DHTTransportException
	{
		transferQueue	transfer_queue = null;

		try{
			transfer_queue = new transferQueue( target, write_transfers, connection_id );

			boolean	ok 				= false;
			boolean	reply_received	= false;

			int		loop			= 0;
			int		total_length	= data.length;

			long	start = SystemTime.getCurrentTime();

			long	last_packet_time = 0;

			while( true ){

				long	now = SystemTime.getCurrentTime();

				if ( now < start ){

					start				= now;

					last_packet_time	= 0;

				}else{

					if ( now - start > timeout ){

						break;
					}
				}

				long	time_since_last_packet = now - last_packet_time;

				if ( time_since_last_packet >= WRITE_XFER_RESEND_DELAY ){

					if ( listener != null ) {
						listener.reportActivity( getMessageText( loop==0?"sending":"resending" ));
					}

					loop++;

					total_length =	handleTransferRequest(
												target,
												connection_id,
												handler_key,
												key,
												data,
												-1, -1,
												true,
												reply_received );	// first packet only if we've has a reply

					last_packet_time		= now;
					time_since_last_packet	= 0;
				}

				Packet packet = transfer_queue.receive( WRITE_XFER_RESEND_DELAY - time_since_last_packet );

				if ( packet != null ){

					last_packet_time	= now;

					reply_received = true;

					if ( packet.getStartPosition() == 0 && packet.getLength() == total_length ){

						ok	= true;

						break;
					}
				}
			}

			if ( ok ){

				if ( listener != null ) {
  				listener.reportCompleteness( 100 );

  				listener.reportActivity( getMessageText( "send_complete" ));
				}

			}else{

				if ( listener != null ) {
					listener.reportActivity( getMessageText( "send_timeout" ));
				}

				throw( new DHTTransportException( "Timeout" ));
			}
		}finally{

			if ( transfer_queue != null ){

				transfer_queue.destroy();
			}
		}
	}

	public byte[]
	writeReadTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							transfer_key,
		byte[]							data,
		long							timeout )

		throws DHTTransportException
	{
		transferHandlerInterceptor	handler;

		synchronized( transfer_handlers ){

			handler = transfer_handlers.get(new HashWrapper( transfer_key ));
		}

		if ( handler == null ){

			return( null );
		}

		boolean	no_acks = handler.getBooleanOption( "disable_call_acks", false ) && data.length <= max_data;

		long	connection_id 	= adapter.getConnectionID();

		byte[]	call_key = new byte[20];

		RandomUtils.SECURE_RANDOM.nextBytes( call_key );

		AESemaphore	call_sem = new AESemaphore( "DHTTransportUDP:calSem" );

		HashWrapper	wrapped_key = new HashWrapper( call_key );

		try{
			this_mon.enter();

			call_transfers.put( wrapped_key, new Object[]{ call_sem, connection_id });

		}finally{

			this_mon.exit();
		}

		boolean	removed = false;

		try{
			if ( no_acks ){

				int retry_count = 0;

				while( true ){

					long	start = SystemTime.getMonotonousTime();

					sendWriteRequest(
							connection_id,
							target,
							transfer_key,
							call_key,
							data,
							0,
							data.length,
							data.length );

					long timeout_to_use = Math.min( timeout,  WRITE_XFER_RESEND_DELAY );

					if ( call_sem.reserve( timeout_to_use )){

						try{
							this_mon.enter();

							Object	res = call_transfers.remove( wrapped_key );

							removed = true;

							if ( res instanceof byte[] ){

								return((byte[])res);
							}
						}finally{

							this_mon.exit();
						}

						break;

					}else{

						if ( retry_count > 0 ){

							break;
						}

						retry_count++;

						timeout -= SystemTime.getMonotonousTime() - start;

						if ( timeout < 1000 ){

							break;
						}
					}
				}
			}else{

				writeTransfer( listener, target, connection_id, transfer_key, call_key, data, timeout );

				if ( call_sem.reserve( timeout )){

					try{
						this_mon.enter();

						Object	res = call_transfers.remove( wrapped_key );

						removed = true;

						if ( res instanceof byte[] ){

							return((byte[])res);
						}
					}finally{

						this_mon.exit();
					}
				}
			}
		}finally{

			if ( !removed ){

				try{
					this_mon.enter();

					call_transfers.remove( wrapped_key );

				}finally{

					this_mon.exit();
				}
			}
		}

		throw( new DHTTransportException( "timeout" ));
	}

	protected transferQueue
	lookupTransferQueue(
		Map<Long,transferQueue>			transfers,
		long							id )
	{
		try{
			this_mon.enter();

			return(transfers.get(new Long(id)));

		}finally{

			this_mon.exit();
		}
	}

	protected String
	getMessageText(
		String		resource,
		String...	params )
	{
		return( MessageText.getString( "DHTTransport.report." + resource, params));
	}

	/**
	 * @author TuxPaper
	 * @created Jan 13, 2017
	 *
	 */
	private final class DHTTransportProgressListenerTRACE
		implements DHTTransportProgressListener
	{
		private String prefix;

		public DHTTransportProgressListenerTRACE(String prefix) {
			this.prefix = prefix;
		}

		@Override
		public void
		reportSize(
			long	size )
		{
			if ( XFER_TRACE ){
				System.out.println( prefix + ": size=" + size );
			}
		}

		@Override
		public void
		reportActivity(
			String	str )
		{
			if ( XFER_TRACE ){
				System.out.println( prefix + ": act=" + str );
			}
		}

		@Override
		public void
		reportCompleteness(
			int		percent )
		{
			if ( XFER_TRACE ){
				System.out.println( prefix + ": %=" + percent );
			}
		}
	}

	protected class
	transferQueue
	{
		private final DHTTransportContact				target;
		private final Map<Long,transferQueue>			transfers;

		private final long		connection_id;
		private boolean			destroyed;

		private final List<Packet>		packets	= new ArrayList<>();

		private final AESemaphore	packets_sem	= new AESemaphore("DHTUDPTransport:transferQueue");

		protected
		transferQueue(
			DHTTransportContact			_target,
			Map<Long,transferQueue>		_transfers,
			long						_connection_id )

			throws DHTTransportException
		{
			target			= _target;
			transfers		= _transfers;
			connection_id	= _connection_id;

			try{
				this_mon.enter();

				if ( transfers.size() > TRANSFER_QUEUE_MAX ){

					// Debug.out( "Transfer queue count limit exceeded" );

					throw( new DHTTransportException( "Transfer queue limit exceeded for " + target.getAddress()));
				}

				Long l_id = new Long( connection_id );

				transferQueue	existing = (transferQueue)transfers.get( l_id );

				if ( existing != null ){

					existing.destroy();
				}

				transfers.put( l_id, this );

			}finally{

				this_mon.exit();
			}
		}

		protected long
		getConnectionID()
		{
			return( connection_id );
		}

		protected void
		add(
			Packet	packet )
		{
			try{
				this_mon.enter();

				if ( destroyed ){

					return;
				}

				if ( total_bytes_on_transfer_queues > MAX_TRANSFER_QUEUE_BYTES ){

					Debug.out( "Transfer queue byte limit exceeded by " + target.getAddress());

						// just drop the packet

					return;
				}

				int	length = packet.getLength();

				total_bytes_on_transfer_queues += length;

				if ( XFER_TRACE ){
					System.out.println( "total_bytes_on_transfer_queues=" + total_bytes_on_transfer_queues );
				}

				packets.add( packet );

			}finally{

				this_mon.exit();
			}

			packets_sem.release();
		}

		protected Packet
		receive(
			long	timeout )
		{
			if ( packets_sem.reserve( timeout )){

				try{
					this_mon.enter();

					if ( destroyed ){

						return( null );
					}

					Packet packet = (Packet)packets.remove(0);

					int	length = packet.getLength();

					total_bytes_on_transfer_queues -= length;

					if ( XFER_TRACE ){
						System.out.println( "total_bytes_on_transfer_queues=" + total_bytes_on_transfer_queues );
					}

					return( packet );

				}finally{

					this_mon.exit();
				}
			}else{

				return( null );
			}
		}

		protected void
		destroy()
		{
			try{
				this_mon.enter();

				destroyed = true;

				transfers.remove( new Long( connection_id ));

				for (int i=0;i<packets.size();i++){

					Packet	packet = (Packet)packets.get(i);

					int	length = packet.getLength();

					total_bytes_on_transfer_queues -= length;

					if ( XFER_TRACE ){
						System.out.println( "total_bytes_on_transfer_queues=" + total_bytes_on_transfer_queues );
					}
				}

				packets.clear();

				packets_sem.releaseForever();

			}finally{

				this_mon.exit();
			}
		}
	}

	public void
	sendReadRequest(
		long						connection_id,
		DHTTransportContact			contact,
		byte[]						transfer_key,
		byte[]						key,
		int							start_pos,
		int							len )
	{
		if ( XFER_TRACE ){
			log( "Transfer read request: key = " + DHTLog.getFullString( key ) + ", contact = " + contact.getString());
		}

		adapter.sendRequest(
			contact,
			new Packet(
				connection_id,
				Packet.PT_READ_REQUEST,
				transfer_key,
				key,
				new byte[0],
				start_pos,
				len,
				0 ));
	}

	public void
	sendReadReply(
		long						connection_id,
		DHTTransportContact			contact,
		byte[]						transfer_key,
		byte[]						key,
		byte[]						data,
		int							start_position,
		int							length,
		int							total_length )
	{
		if ( XFER_TRACE ){
			log( "Transfer read reply: key = " + DHTLog.getFullString( key ) + ", contact = " + contact.getString());
		}

		adapter.sendRequest(
			contact,
			new Packet(
				connection_id,
				Packet.PT_READ_REPLY,
				transfer_key,
				key,
				data,
				start_position,
				length,
				total_length ));
	}

	public void
	sendWriteRequest(
		long						connection_id,
		DHTTransportContact			contact,
		byte[]						transfer_key,
		byte[]						key,
		byte[]						data,
		int							start_position,
		int							length,
		int							total_length )
	{
		if ( XFER_TRACE ){
			log( "Transfer write request: key = " + DHTLog.getFullString( key ) + ", contact = " + contact.getString());
		}

		adapter.sendRequest(
			contact,
			new Packet(
				connection_id,
				Packet.PT_WRITE_REQUEST,
				transfer_key,
				key,
				data,
				start_position,
				length,
				total_length ));
	}

	public void
	sendWriteReply(
		long						connection_id,
		DHTTransportContact			contact,
		byte[]						transfer_key,
		byte[]						key,
		int							start_position,
		int							length )
	{
		if ( XFER_TRACE ){
			log( "Transfer write reply: key = " + DHTLog.getFullString( key ) + ", contact = " + contact.getString());
		}

		adapter.sendRequest(
			contact,
			new Packet(
				connection_id,
				Packet.PT_WRITE_REPLY,
				transfer_key,
				key,
				new byte[0],
				start_position,
				length,
				0 ));
	}

	void
	log(
		String		str )
	{
		if ( XFER_TRACE ){
			System.out.println( str );
		}

		logger.log( str );
	}

	private void
	log(
		Throwable	e  )
	{
		if ( XFER_TRACE ){
			e.printStackTrace();
		}

		logger.log( e );
	}

	protected class
	transferHandlerInterceptor
		implements DHTTransportTransferHandler
	{
		private final DHTTransportTransferHandler		handler;
		private final Map<String,Object>				options;

		protected
		transferHandlerInterceptor(
			DHTTransportTransferHandler		_handler,
			Map<String,Object>				_options )
		{
			handler	= _handler;
			options	= _options;
		}

		@Override
		public String
		getName()
		{
			return( handler.getName());
		}

		public boolean
		getBooleanOption(
			String		name,
			boolean		def )
		{
			if ( options == null ){

				return( def );
			}

			Boolean b = (Boolean)options.get( name );

			return( b==null?def:b );
		}

		@Override
		public byte[]
    	handleRead(
    		DHTTransportContact		originator,
    		byte[]					key )
		{
			return( handler.handleRead( originator, key ));
		}

	   	@Override
	    public byte[]
	   	handleWrite(
	   		DHTTransportContact		originator,
	   		byte[]					key,
	   		byte[]					value )
	   	{
	   		return( handleWrite( originator, 0, key, value ));
	   	}

    	public byte[]
    	handleWrite(
    		DHTTransportContact		originator,
    		long					connection_id,
    		byte[]					key,
    		byte[]					value )
    	{
    		HashWrapper	key_wrapper = new HashWrapper( key );

    			// see if this is the response to an outstanding call

    		try{
    			this_mon.enter();

    			Object	_obj = call_transfers.get( key_wrapper );

    			if ( _obj instanceof Object[] ){

    				Object[] obj = (Object[])_obj;

    					// prevent loopback requests from returning the request as the result

    				if ((Long)obj[1] != connection_id ){

	    				AESemaphore	sem = (AESemaphore)obj[0];

	    				call_transfers.put( key_wrapper, value );

	    				sem.release();

	    				return( null );
    				}
    			}
    		}finally{

    			this_mon.exit();
    		}

    		return( handler.handleWrite( originator, key, value ));
    	}
	}

	public static class
	Packet
	{
		public static final byte		PT_READ_REQUEST		= DHTUDPPacketData.PT_READ_REQUEST;
		public static final byte		PT_READ_REPLY		= DHTUDPPacketData.PT_READ_REPLY;
		public static final byte		PT_WRITE_REQUEST	= DHTUDPPacketData.PT_WRITE_REQUEST;
		public static final byte		PT_WRITE_REPLY		= DHTUDPPacketData.PT_WRITE_REPLY;

		private final long	connection_id;
		private final byte	packet_type;
		private final byte[]	transfer_key;
		private final byte[]	key;
		private final byte[]	data;
		private final int		start_position;
		private final int		length;
		private final int		total_length;

		private int		flags;

		public
		Packet(
			long		_connection_id,
			byte		_packet_type,
			byte[]		_transfer_key,
			byte[]		_key,
			byte[]		_data,
			int			_start_position,
			int			_length,
			int			_total_length )
		{
			connection_id	= _connection_id;
			packet_type		= _packet_type;
			transfer_key	= _transfer_key;
			key				= _key;
			data			= _data;
			start_position	= _start_position;
			length			= _length;
			total_length	= _total_length;
		}

		public
		Packet(
			long		_connection_id,
			byte		_packet_type,
			byte[]		_transfer_key,
			byte[]		_key,
			byte[]		_data,
			int			_start_position,
			int			_length,
			int			_total_length,
			int			_flags )
		{
			connection_id	= _connection_id;
			packet_type		= _packet_type;
			transfer_key	= _transfer_key;
			key				= _key;
			data			= _data;
			start_position	= _start_position;
			length			= _length;
			total_length	= _total_length;
			flags			= _flags;
		}

		public long
		getConnectionId()
		{
			return( connection_id );
		}

		public byte
		getPacketType()
		{
			return( packet_type );
		}

		public byte[]
		getTransferKey()
		{
			return( transfer_key );
		}

		public byte[]
		getRequestKey()
		{
			return( key );
		}

		public byte[]
		getData()
		{
			return( data );
		}

		public int
		getStartPosition()
		{
			return( start_position );
		}

		public int
		getLength()
		{
			return( length );
		}

		public int
		getTotalLength()
		{
			return( total_length );
		}

		public int
		getFlags()
		{
			return( flags );
		}

		public String
		getString()
		{
			return( "ty=" + packet_type + ",tk=" + DHTLog.getString2( transfer_key ) + ",rk=" +
					DHTLog.getString2( key ) + ",data=" + data.length +
					",st=" + start_position + ",len=" + length + ",tot=" + total_length );
		}
	}

	public interface
	Adapter
	{
		public long
		getConnectionID();

		public void
		sendRequest(
			DHTTransportContact			contact,
			Packet						packet );

	}
}
