/*
 * Created on 26 Jun 2006
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

package com.biglybt.core.networkmanager.impl.udp;


public class
UDPPacket
{
	public static final byte	PROTOCOL_VERSION	= 1;

	public static final byte	COMMAND_CRYPTO			= 0;
	public static final byte	COMMAND_DATA			= 1;
	public static final byte	COMMAND_ACK				= 2;
	public static final byte	COMMAND_CLOSE			= 3;
	public static final byte	COMMAND_STAT_REQUEST	= 4;
	public static final byte	COMMAND_STAT_REPLY		= 5;



	public static final byte	FLAG_NONE		= 0x00;
	public static final byte	FLAG_LAZY_ACK	= 0x01;

	private final UDPConnection		connection;
	private final int				sequence;
	private final int				alt_sequence;
	private final byte				command;
	private final byte[]			buffer;
	private final long				unack_in_sequence_count;

	private boolean auto_retransmit			= true;
	private short	sent_count;
	private short	resend_count;
	private boolean received;
	private long	send_tick_count;

	protected
	UDPPacket(
		UDPConnection	_connection,
		int[]			_sequences,
		byte			_command,
		byte[]			_buffer,
		long			_unack_in_sequence_count )
	{
		connection		= _connection;
		sequence		= _sequences[1];
		alt_sequence	= _sequences[3];
		command			= _command;
		buffer			= _buffer;

		unack_in_sequence_count	= _unack_in_sequence_count;
	}

	protected UDPConnection
	getConnection()
	{
		return( connection );
	}

	protected int
	getSequence()
	{
		return( sequence );
	}

	protected int
	getAlternativeSequence()
	{
		return( alt_sequence );
	}

	protected byte
	getCommand()
	{
		return( command );
	}

	protected byte[]
	getBuffer()
	{
		return( buffer );
	}

	protected long
	getUnAckInSequenceCount()
	{
		return( unack_in_sequence_count );
	}

	protected boolean
	isAutoRetransmit()
	{
		return( auto_retransmit );
	}

	protected void
	setAutoRetransmit(
		boolean	b  )
	{
		auto_retransmit	= b;
	}

	protected short
	sent(
		long	tick_count )
	{
		sent_count++;

		send_tick_count = tick_count;

		return( sent_count );
	}

	protected short
	getResendCount()
	{
		return( resend_count );
	}

	protected void
	resent()
	{
		resend_count++ ;
	}

	protected long
	getSendTickCount()
	{
		return( send_tick_count );
	}

	protected void
	setHasBeenReceived()
	{
		received	= true;
	}

	protected boolean
	hasBeenReceived()
	{
		return( received );
	}

	protected int
	getSentCount()
	{
		return( sent_count );
	}

	protected String
	getString()
	{
		return( "seq=" + sequence + ",type=" + command + ",retrans=" + auto_retransmit + ",sent=" + sent_count +",len=" + buffer.length );
	}
}
