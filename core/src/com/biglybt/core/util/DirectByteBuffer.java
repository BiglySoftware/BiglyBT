/*
 * Created on Apr 21, 2004
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

package com.biglybt.core.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

//import java.util.*;

/**
 * Virtual direct byte buffer given out and tracker
 * by the buffer pool.
 */

public class
DirectByteBuffer
{
		// allocator constants

	public static final byte		AL_NONE			= 0;
	public static final byte		AL_EXTERNAL		= 1;
	public static final byte		AL_OTHER		= 2;
	public static final byte		AL_PT_READ		= 3;
	public static final byte		AL_PT_LENGTH	= 4;
	public static final byte		AL_CACHE_READ	= 5;
	public static final byte		AL_DM_READ		= 6;
	public static final byte		AL_DM_ZERO		= 7;
	public static final byte		AL_DM_CHECK		= 8;
	public static final byte		AL_BT_PIECE    	= 9;
	public static final byte		AL_CACHE_WRITE  = 10;
	public static final byte		AL_PROXY_RELAY  = 11;
	public static final byte    	AL_MSG          = 12;

    public static final byte        AL_MSG_AZ_HAND        = 13;
    public static final byte        AL_MSG_AZ_PEX         = 14;
    public static final byte        AL_MSG_BT_CANCEL      = 15;
    public static final byte        AL_MSG_BT_HAND        = 16;
    public static final byte        AL_MSG_BT_HAVE        = 17;
    public static final byte        AL_MSG_BT_PIECE       = 18;
    public static final byte        AL_MSG_BT_REQUEST     = 19;
    public static final byte        AL_MSG_BT_KEEPALIVE   = 20;
    public static final byte        AL_MSG_BT_HEADER      = 21;
    public static final byte        AL_MSG_AZ_HEADER      = 22;
    public static final byte        AL_MSG_BT_PAYLOAD     = 23;
    public static final byte        AL_MSG_AZ_PAYLOAD     = 24;
    public static final byte        AL_FILE				  = 25;
    public static final byte        AL_NET_CRYPT		  = 26;
    public static final byte        AL_MSG_LT_EXT_MESSAGE = 27;
    public static final byte        AL_MSG_LT_HANDSHAKE   = 28;
    public static final byte        AL_MSG_UT_PEX         = 29;
    public static final byte        AL_MSG_BT_DHT_PORT    		= 30;
    public static final byte        AL_MSG_BT_REJECT_REQUEST    = 31;
    public static final byte        AL_MSG_BT_SUGGEST_PIECE   	= 32;
    public static final byte        AL_MSG_BT_ALLOWED_FAST    	= 33;
    public static final byte        AL_MSG_UT_METADATA 	        = 34;
    public static final byte        AL_MSG_AZ_METADATA 	        = 35;
    public static final byte        AL_MSG_UT_UPLOAD_ONLY       = 36;
    public static final byte        AL_MSG_BT_HASH_REQUEST      = 37;
    public static final byte        AL_MSG_BT_HASHES            = 38;
    public static final byte        AL_MSG_BT_HASH_REJECT       = 39;
    public static final byte        AL_MSG_UT_HOLEPUNCH			= 40;
       

	public static final String[] AL_DESCS =
	{ "None", "Ext", "Other", "PeerRead", "PeerLen",
    "CacheRead", "DiskRead", "DiskZero", "DiskCheck",
    "BTPiece", "CacheWrite", "ProxyRelay", "Messaging",
    "AZHandshake",
    "AZPEX",
    "BTCancel",
    "BTHandshake",
    "BTHave",
    "BTPiece",
    "BTRequest",
    "BTKeepAlive",
    "BTHeader",
    "AZHeader",
    "BTPayload",
    "AZPayload",
    "File",
    "MsgCrypt",
    "LTExtMsg",
    "LTExtHandshake",
    "UTPEX",
    "BTDHTPort",
    "BTRejectRequest",
    "BTSuggestPiece",
    "BTAllowedFast",
    "UTMetaData",
    "AZMetaData",
    "UTUploadOnly",
    "BTHashRequest",
    "BTHashes",
    "BTHashReject",
    "UTHolePunch"};



		// subsystem ids

	public static final byte		SS_NONE			= 0;	// not used, required to id cycled buffers
	public static final byte		SS_EXTERNAL		= 1;
	public static final byte		SS_OTHER		= 2;
	public static final byte		SS_CACHE		= 3;
	public static final byte		SS_FILE			= 4;
	public static final byte		SS_NET			= 5;
	public static final byte		SS_BT			= 6;
	public static final byte		SS_DR			= 7;
	public static final byte		SS_DW			= 8;
	public static final byte		SS_PEER			= 9;
	public static final byte		SS_PROXY		= 10;
	public static final byte   		SS_MSG     		= 11;

	public static final String[] SS_DESCS =
	{ "None", "Ext", "Other", "Cache", "File",
    "Net", "BT", "DiskRead", "DiskWrite",
    "Peer", "Proxy", "Messaging" };

	public static final byte		OP_LIMIT			= 0;
	public static final byte		OP_LIMIT_INT		= 1;
	public static final byte		OP_POSITION			= 2;
	public static final byte		OP_POSITION_INT		= 3;
	public static final byte		OP_CLEAR			= 4;
	public static final byte		OP_FLIP				= 5;
	public static final byte		OP_REMANING			= 6;
	public static final byte		OP_CAPACITY			= 7;
	public static final byte		OP_PUT_BYTEARRAY	= 8;
	public static final byte		OP_PUT_DBB			= 9;
	public static final byte		OP_PUT_BB			= 10;
	public static final byte		OP_PUTINT			= 11;
	public static final byte		OP_PUT_BYTE			= 12;
	public static final byte		OP_GET				= 13;
	public static final byte		OP_GET_INT			= 14;
	public static final byte		OP_GET_BYTEARRAY	= 15;
	public static final byte		OP_GETINT			= 16;
	public static final byte		OP_GETINT_INT		= 17;
	public static final byte		OP_HASREMAINING		= 18;
	public static final byte		OP_READ_FC			= 19;
	public static final byte		OP_WRITE_FC			= 20;
	public static final byte		OP_READ_SC			= 21;
	public static final byte		OP_WRITE_SC			= 22;
	public static final byte		OP_GETBUFFER		= 23;
	public static final byte		OP_GETSHORT			= 24;
	public static final byte		OP_PUTSHORT			= 25;

	public static final String[]	OP_DESCS =
		{ 	"limit", 		"limit(int)", 	"position", 	"position(int)", 	"clear",
			"flip", 		"remaining", 	"capacity", 	"put(byte[])", 		"put(dbb)",
			"put(bbb)", 	"putInt", 		"put(byte)",	"get",				"get(int)",
			"get(byte[])",	"getInt",		"getInt(int",	"hasRemaining",		"read(fc)",
			"write(fc)",	"read(sc)",		"write(sc)",	"getBuffer",		"getShort",
			"putShort",
		};

	public static final byte	FL_NONE						= 0x00;
	public static final byte	FL_CONTAINS_TRANSIENT_DATA	= 0x01;

	protected static final boolean	TRACE				= AEDiagnostics.TRACE_DIRECT_BYTE_BUFFERS;
	protected static final int		TRACE_BUFFER_SIZE	= 64;		// must be even


	private ByteBuffer 				buffer;
	private DirectByteBufferPool	pool;
	private byte					allocator;
	private byte					flags;
	//private boolean                 was_returned_to_pool = false;


	//private byte[]					trace_buffer;
	//private int						trace_buffer_pos;

	public
	DirectByteBuffer(
		ByteBuffer 	_buffer )
	{
		this( AL_NONE, _buffer, null );
	}

	public
	DirectByteBuffer(
		byte					_allocator,
		ByteBuffer 				_buffer,
		DirectByteBufferPool	_pool )
	{
		if (_buffer == null) {throw new NullPointerException("buffer is null");}
		allocator	= _allocator;
		buffer 		= _buffer;
		pool		= _pool;

		if ( TRACE ){
			/*
			trace_buffer	= new byte[TRACE_BUFFER_SIZE];

			Arrays.fill(trace_buffer,(byte)0);
			*/
		}
	}

		/**
		 * constructor for reference counted version
		 * @param basis
		 */

	protected
	DirectByteBuffer(
		DirectByteBuffer		basis )
	{
		allocator	= basis.allocator;
		buffer 		= basis.buffer;
		pool		= null;

		if (buffer == null) {throw new NullPointerException("basis.buffer is null");}
	}

	public ReferenceCountedDirectByteBuffer
	getReferenceCountedBuffer()
	{
		ReferenceCountedDirectByteBuffer res = new ReferenceCountedDirectByteBuffer( this );

		return( res );
	}

	public void
	setFlag(
		byte		flag )
	{
		flags |= flag;
	}

	public boolean
	getFlag(
		byte		flag )
	{
		return((flags&flag)!=0);
	}

	protected void
	traceUsage(
		byte		subsystem,
		byte		operation )
	{
		if ( TRACE ){
			/*
			trace_buffer[trace_buffer_pos++]	= subsystem;
			trace_buffer[trace_buffer_pos++]	= operation;

			if ( trace_buffer_pos == TRACE_BUFFER_SIZE ){

				trace_buffer_pos	= 0;
			}
			*/
		}
	}

	protected String
	getTraceString()
	{
		if ( TRACE ){
			/*
			StringBuffer	sb = new StringBuffer();

			sb.append( AL_DESCS[allocator]);
			sb.append( ":" );

			boolean	wrapped	= false;

			int	start	= 0;
			int	end		= trace_buffer_pos;

			if ( trace_buffer[trace_buffer_pos] != 0 ){

				start	= trace_buffer_pos;

				wrapped	= true;
			}

			if ( end == 0 && !wrapped ){

				sb.append( "not used");

			}else{

				if ( wrapped ){

					sb.append( "*" );
				}

				int	num = 0;

				do{

					if ( num++ > 0 ){
						sb.append(",");
					}

					sb.append( SS_DESCS[trace_buffer[start++]]);
					sb.append( "/" );
					sb.append( OP_DESCS[trace_buffer[start++]]);

					if ( start == TRACE_BUFFER_SIZE ){

						start	= 0;
					}
				}while( start != end );
			}

			return( sb.toString());
			*/
		}

		return( null );
	}

	protected void
	dumpTrace(
		Throwable 	e )
	{
		if ( TRACE ){

			System.out.println( getTraceString());

			Ignore.ignore(e);
		}
	}

	protected ByteBuffer
	getBufferInternal()
	{
		return( buffer );
	}

	protected byte
	getAllocator()
	{
		return( allocator );
	}




		// **** accessor methods  ****

	public int
	limit(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_LIMIT );
		}

		return( buffer.limit());
	}

	public void
	limit(
		byte		subsystem,
		int			l )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_LIMIT_INT );
		}

		buffer.limit(l);
	}

	public int
	position(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_POSITION);
		}

	  	return( buffer.position());
	}

	public void
	position(
		byte		subsystem,
		int			l )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_POSITION_INT);
		}

		buffer.position(l);
	}

	public void
	clear(
		byte		subsystem)
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_CLEAR );
		}

		buffer.clear();
	}

	public void
	flip(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_FLIP );
		}

		buffer.flip();
	}

	public int
	remaining(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_REMANING );
		}

		return( buffer.remaining());
	}

	public int
	capacity(
		 byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_CAPACITY );
		}

		return( buffer.capacity());
	}

	public void
	put(
		byte		subsystem,
		byte[]		data )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_PUT_BYTEARRAY );
		}

		buffer.put( data );
	}

	public void
	put(
		byte		subsystem,
		byte[]		data,
		int			offset,
		int			length )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_PUT_BYTEARRAY );
		}

		buffer.put( data, offset, length );
	}

	public void
	put(
		byte				subsystem,
		DirectByteBuffer	data )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_PUT_DBB);
		}

		buffer.put( data.buffer );
	}

	public void
	put(
		byte		subsystem,
		ByteBuffer	data )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_PUT_BB);
		}

		buffer.put( data );
	}

	public void
	put(
		byte	subsystem,
		byte	data )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_PUT_BYTE );
		}

		buffer.put( data );
	}

	public void
	putShort(
		byte		subsystem,
		short		x )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_PUTSHORT);
		}

		buffer.putShort( x );
	}

	public void
	putInt(
		byte		subsystem,
		int			data )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_PUTINT);
		}

		buffer.putInt( data );
	}

	public byte
	get(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_GET);
		}

		return( buffer.get());
	}

	public byte
	get(
		byte	subsystem,
		int		x )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_GET_INT);
		}

		return( buffer.get(x));
	}

	public void
	get(
		byte		subsystem,
		byte[]		data )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_GET_BYTEARRAY);
		}

		buffer.get(data);
	}

	public short
	getShort(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_GETSHORT);
		}

		return( buffer.getShort());
	}

	public int
	getInt(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_GETINT);
		}

		return( buffer.getInt());
	}

	public int
	getInt(
		byte		subsystem,
		int			x )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_GETINT_INT );
		}

		return( buffer.getInt(x));
	}

	public boolean
	hasRemaining(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_HASREMAINING );
		}

		return( buffer.hasRemaining());
	}

	public int
	read(
		byte		subsystem,
		FileChannel	chan )

		throws IOException
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_READ_FC);
		}

		try{
			return( chan.read(buffer ));

		}catch( IllegalArgumentException e ){

			dumpTrace(e);

			throw( e );
		}
	}

	public int
	write(
		byte		subsystem,
		FileChannel	chan )

		throws IOException
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_WRITE_FC );
		}

		try{
			return( chan.write(buffer ));

		}catch( IllegalArgumentException e ){

			dumpTrace(e);

			throw( e );
		}
	}

	public int
	read(
		byte			subsystem,
		SocketChannel	chan )

		throws IOException
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_READ_SC );
		}

		try{
			return( chan.read(buffer ));

		}catch( IllegalArgumentException e ){

			dumpTrace(e);

			throw( e );
		}
	}

	public int
	write(
		byte			subsystem,
		SocketChannel	chan )

  		throws IOException
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_WRITE_SC );
		}

		try{
			return( chan.write(buffer ));

		}catch( IllegalArgumentException e ){

			dumpTrace(e);

			throw( e );
		}
	}

	public ByteBuffer
	getBuffer(
		byte		subsystem )
	{
		if ( TRACE ){

			traceUsage( subsystem, OP_GETBUFFER );
		}

		return( buffer );
	}

	public void
	returnToPool()
	{
		if ( pool != null ){

				// we can't afford to return a buffer more than once to the pool as it'll get
				// handed out twice in parallel and cause weird problems. We haven't been able
				// to totally eliminiate duplicate returnToPool calls....

			synchronized( this ){

				if ( buffer == null ){

					Debug.out( "Buffer already returned to pool");

				}else{

					pool.returnBufferSupport( this );

					buffer	= null;
					//was_returned_to_pool = true;
				}
			}
		}
	}

	public boolean
	hasBeenReturnedToPool()
	{
		if ( pool != null ){
			
			synchronized( this ){
			
				return( buffer == null );
			}
		}
		
		return( false );
	}
	
		/**
		 * Normally you should know when a buffer is/isn't free and NOT CALL THIS METHOD
		 * However, there are some error situations where the existing code doesn't correctly
		 * manage things - we know this and don't want spurious logs occuring as per the above
		 * normal method
		 */

	public void
	returnToPoolIfNotFree()
	{
		if ( pool != null ){

				// we can't afford to return a buffer more than once to the pool as it'll get
				// handed out twice in parallel and cause weird problems. We haven't been able
				// to totally eliminiate duplicate returnToPool calls....

			synchronized( this ){

				if ( buffer != null ){

					pool.returnBufferSupport( this );

					buffer	= null;
					//was_returned_to_pool = true;
				}
			}
		}
	}
}
