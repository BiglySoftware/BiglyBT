/*
 * Created on 1 Nov 2006
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


package com.biglybt.pifimpl.local.peers;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.biglybt.core.networkmanager.*;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.peers.Peer;

public class
PeerForeignNetworkConnection
	extends NetworkConnectionHelper
{
	final private PeerForeignDelegate		delegate;
	final private Peer						peer;

	private OutgoingMessageQueue	outgoing_message_queue = new omq();
	private IncomingMessageQueue	incoming_message_queue = new imq();

	private TransportBase			transport_base	= new tp();

	protected
	PeerForeignNetworkConnection(
		PeerForeignDelegate		_delegate,
		Peer					_peer )
	{
		delegate	= _delegate;
		peer		= _peer;
	}

	@Override
	public ConnectionEndpoint
	getEndpoint()
	{
			// make up a vaguely usable endpoint

		return( new ConnectionEndpoint(new InetSocketAddress( peer.getIp(), peer.getPort())));
	}

	@Override
	public void
	notifyOfException(
		Throwable error )
	{
		Debug.printStackTrace( error );
	}

	@Override
	public OutgoingMessageQueue
	getOutgoingMessageQueue()
	{
		return( outgoing_message_queue );
	}


	@Override
	public IncomingMessageQueue
	getIncomingMessageQueue()
	{
		return( incoming_message_queue );
	}

	@Override
	public TransportBase
	getTransportBase()
	{
		return( transport_base );
	}

	@Override
	public int
	getMssSize()
	{
		return( NetworkManager.getMinMssSize());
	}

	@Override
	public boolean
	isIncoming()
	{
		return false;
	}

	@Override
	public boolean
	isLANLocal()
	{
		return( false );
	}

	@Override
	public void 
	resetLANLocalStatus()
	{
	}
	
	@Override
	public boolean 
	isClosed()
	{
		return( delegate.isClosed());
	}
	
	@Override
	public String
	getString()
	{
		String	peer_str = peer.getClass().getName();

		int	pos = peer_str.lastIndexOf('.');

		if ( pos != -1 ){

			peer_str = peer_str.substring( pos+1 );
		}

		peer_str += " " + peer.getIp() + ":" + peer.getPort();

		return( "peer=" + peer_str + ",in=" + incoming_message_queue.getCurrentMessageProgress() +
				",out=" + outgoing_message_queue.getTotalSize());
	}
	protected class
	tp
		implements TransportBase
	{
		private long	last_ready_for_read	= SystemTime.getSteppedMonotonousTime();

		@Override
		public boolean
		isReadyForWrite(
			EventWaiter waiter )
		{
			return( false );
		}

		@Override
		public long
		isReadyForRead(
			EventWaiter waiter )
		{
			long	now = SystemTime.getSteppedMonotonousTime();

			if ( peer.isTransferAvailable()){

				last_ready_for_read = now;

				return( 0 );
			}

			long	diff = now - last_ready_for_read + 1;	// make sure > 0

			return( diff );
		}

		@Override
		public boolean
		isTCP()
		{
				// we don't know (or care?)

			return( false );
		}

		@Override
		public String
		getDescription()
		{
			return( "Peer transport delegate" );
		}
	}

	protected class
	imq
		implements IncomingMessageQueue
	{
		@Override
		public void
		setDecoder(
			MessageStreamDecoder new_stream_decoder )
		{
		}

		@Override
		public MessageStreamDecoder
		getDecoder()
		{
			throw( new RuntimeException( "Not imp" ));
		}

		@Override
		public int[]
		getCurrentMessageProgress()
		{
			return( null );
		}

		@Override
		public int []
		receiveFromTransport(
			int max_bytes, boolean protocol_is_free ) throws IOException
		{
			return( new int[]{ peer.readBytes( delegate.isDownloadDisabled()?0:max_bytes ), 0 });
		}

		@Override
		public void
		notifyOfExternallyReceivedMessage(
			Message message )
		{
		}

		@Override
		public void
		resumeQueueProcessing()
		{
		}

		@Override
		public void
		registerQueueListener(
			MessageQueueListener listener )
		{
		}

		@Override
		public void
		cancelQueueListener(
			MessageQueueListener listener )
		{
		}

		@Override
		public void
		destroy()
		{
		}

	}

	protected class
	omq
		implements OutgoingMessageQueue
	{
		@Override
		public void
		setTransport(
			Transport		_transport )
		{
		}

		@Override
		public int
		getMssSize()
		{
			return( PeerForeignNetworkConnection.this.getMssSize());
		}

		@Override
		public void
		setEncoder(
			MessageStreamEncoder stream_encoder )
		{
		}

		@Override
		public MessageStreamEncoder
		getEncoder()
		{
			throw( new RuntimeException( "Not imp" ));
		}

		@Override
		public int[]
		getCurrentMessageProgress()
		{
			return( null );
		}

		@Override
		public void
		destroy()
		{
		}

		@Override
		public void flush()
		{
		}

		@Override
		public boolean
		isDestroyed()
		{
			return false;
		}

		@Override
		public int
		getTotalSize()
		{
			return( 0 );
		}

		@Override
		public int
		getDataQueuedBytes()
		{
			return( 0 );
		}

		@Override
		public int
		getProtocolQueuedBytes()
		{
			return( 0 );
		}

		@Override
		public boolean
		getPriorityBoost()
		{
			return( false );
		}

		@Override
		public void
		setPriorityBoost(
			boolean	boost )
		{
		}

		@Override
		public boolean
		isBlocked()
		{
			return( false );
		}

		@Override
		public boolean
		hasUrgentMessage()
		{
			return( false );
		}

		@Override
		public Message
		peekFirstMessage()
		{
			return null;
		}

		@Override
		public void
		addMessage(
			Message message,
			boolean manual_listener_notify )
		{
			throw( new RuntimeException( "Not imp" ));
		}

		@Override
		public void
		removeMessagesOfType(
			Message[] message_types,
			boolean manual_listener_notify )
		{
			throw( new RuntimeException( "Not imp" ));
		}

		@Override
		public boolean
		removeMessage(
			Message message,
			boolean manual_listener_notify )
		{
			throw( new RuntimeException( "Not imp" ));
		}

		@Override
		public int[]
		deliverToTransport(
			int 		max_bytes,
			boolean		protocol_is_free,
			boolean 	manual_listener_notify )

			throws IOException
		{
			throw( new RuntimeException( "Not imp" ));
		}

		@Override
		public void
		doListenerNotifications()
		{
		}

		@Override
		public void
		setTrace(
				boolean	on )
		{
		}

		@Override
		public String
		getQueueTrace()
		{
			return( "" );
		}

		@Override
		public void
		registerQueueListener(
			MessageQueueListener listener )
		{
		}

		@Override
		public void
		cancelQueueListener(
			MessageQueueListener listener )
		{
		}

		@Override
		public void
		notifyOfExternallySentMessage(
			Message message )
		{
		}
	}
}
