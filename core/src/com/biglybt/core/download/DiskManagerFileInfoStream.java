/*
 * Created on Feb 11, 2009
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.download;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.util.*;
import com.biglybt.pif.disk.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.utils.PooledByteBufferImpl;

public class
DiskManagerFileInfoStream
	implements DiskManagerFileInfo
{
	StreamFactory		stream_factory;
	File				save_to;
	private byte[]				hash;

	private context				current_context;

	Object	lock = this;

	public
	DiskManagerFileInfoStream(
		StreamFactory		_stream_factory,
		File				_save_to )
	{
		stream_factory		= _stream_factory;
		save_to				= _save_to;

		try{
			hash		= new SHA1Simple().calculateHash( _save_to.getAbsolutePath().getBytes( "UTF-8" ));

		}catch( Throwable e ){

			Debug.out(e);
		}
	}

	public boolean
	isComplete()
	{
		synchronized( lock ){

			return( save_to.exists());
		}
	}

	public void
	reset()
	{
		synchronized( lock ){

			if ( current_context != null ){

				current_context.destroy( new Exception( "Reset" ));
			}

			save_to.delete();
		}
	}

	@Override
	public void
	setPriority(
		boolean b )
	{
	}

	@Override
	public void
	setSkipped(
		boolean b )
	{
		throw( new RuntimeException( "Not supported" ));
	}

	@Override
	public Boolean 
	isSkipping()
	{
		return( null );
	}
	
	@Override
	public int
	getNumericPriority()
	{
		return( 0 );
	}


	@Override
	public void
	setNumericPriority(
		int priority)
	{
		throw( new RuntimeException( "Not supported" ));
	}

	@Override
	public void
	setDeleted(boolean b)
	{
	}

	@Override
	public void
	setLink(
		File	link_destination,
		boolean	no_delete )
	{
		throw( new RuntimeException( "Not supported" ));
	}

	@Override
	public File
	getLink()
	{
		return( null );
	}

	@Override
	public int
	getAccessMode()
	{
		return( READ );
	}

	@Override
	public long
	getDownloaded()
	{
		return( getLength());
	}

	@Override
	public long 
	getLastModified()
	{
		return( 0 );
	}
	
	@Override
	public long
	getLength()
	{
		return( -1 );
	}

	@Override
	public File
	getFile()
	{
		return( save_to );
	}

	@Override
	public File
	getFile(
		boolean	follow_link )
	{
		return( save_to );
	}

	@Override
	public int
	getIndex()
	{
		return( 0 );
	}

	@Override
	public int
	getFirstPieceNumber()
	{
		return( 0 );
	}

	@Override
	public long
	getPieceSize()
	{
		return( 32*1024 );
	}

	@Override
	public int
	getNumPieces()
	{
		return( -1 );
	}

	@Override
	public boolean
	isPriority()
	{
		return( false );
	}

	@Override
	public boolean
	isSkipped()
	{
		return( false );
	}

	@Override
	public boolean
	isDeleted()
	{
		return( false );
	}

	@Override
	public byte[]
	getDownloadHash()

		throws DownloadException
	{
		return( hash );
	}

	@Override
	public Download
	getDownload()

         throws DownloadException
    {
		throw( new DownloadException( "Not supported" ));
    }

	@Override
	public DiskManagerChannel
	createChannel()

		throws DownloadException
	{
		try{
			synchronized( lock ){

				if ( current_context == null ){

					current_context = new context();
				}

				return( current_context.createChannel());
			}
		}catch( Throwable e ){

			throw( new DownloadException( "Channel creation failed", e ));
		}
	}

	@Override
	public DiskManagerRandomReadRequest
	createRandomReadRequest(
		long						file_offset,
		long						length,
		boolean						reverse_order,
		DiskManagerListener			listener )

		throws DownloadException
	{
		throw( new DownloadException( "Not supported" ));
	}

	protected void
	destroyed(
		context		c )
	{
		synchronized( lock ){

			if ( current_context == c ){

				current_context = null;
			}
		}

		stream_factory.destroyed( c );
	}

	protected class
	context
	{
		RandomAccessFile				raf;
		StreamFactory.StreamDetails		stream_details;

		boolean				stream_got_eof;

		private List<channel>		channels	= new ArrayList<>();

		List<AESemaphore>	waiters 	= new ArrayList<>();

		private boolean				context_destroyed;

		protected
		context()

			throws Exception
		{
			if ( save_to.exists()){

				raf = new RandomAccessFile( save_to, "r" );

				stream_got_eof = true;

			}else{

				final File	temp_file = FileUtil.newFile( save_to.getAbsolutePath() + "._tmp_" );

				raf = new RandomAccessFile( temp_file, "rw" );

				stream_details = stream_factory.getStream( this );

				final InputStream stream = stream_details.getStream();

				new AEThread2( "DMS:reader", true )
				{
					@Override
					public void
					run()
					{
						final int BUFF_SIZE = 128*1024;

						byte[]	buffer = new byte[BUFF_SIZE];

						try{
							while( true ){

								int len = stream.read( buffer );

								if ( len <= 0 ){

									if ( stream_details.hasFailed()){

										throw( new IOException( "Stream failed" ));
									}

									synchronized( lock ){

										stream_got_eof	= true;
									}

									break;
								}

								synchronized( lock ){

									raf.seek( raf.length());

									raf.write( buffer, 0, len );

									for ( AESemaphore waiter: waiters ){

										waiter.release();
									}
								}
							}
						}catch( Throwable e ){

							context.this.destroy( e );

						}finally{

							try{
								stream.close();

							}catch( Throwable e ){

							}

							Throwable failed = null;

							synchronized( lock ){

								stream_details = null;

								if ( stream_got_eof ){

									try{
										raf.close();

										save_to.delete();

										temp_file.renameTo( save_to );

										raf = new RandomAccessFile( save_to, "r" );

									}catch( Throwable e ){

										failed = e;
									}
								}
							}

							if ( failed != null ){

								context.this.destroy( failed );
							}
						}
					}
				}.start();
			}
		}

		protected int
		read(
			byte[]		buffer,
			long		offset,
			int			length )

			throws IOException
		{
			AESemaphore	sem;

			synchronized( lock ){

				if ( raf.length() > offset ){

					raf.seek( offset );

					return( raf.read( buffer, 0, length ));
				}

				if ( stream_details == null ){

					if ( stream_got_eof ){

						return( -1 );
					}

					throw( new IOException( "Premature end of stream (read)" ));
				}

				sem = new AESemaphore( "DMS:block" );

				waiters.add( sem );
			}

			try{
				sem.reserve( 1000 );

			}finally{

				synchronized( lock ){

					waiters.remove( sem );
				}
			}

			return( 0 );
		}

		protected channel
		createChannel()
		{
			synchronized( lock ){

				channel c = new channel();

				channels.add( c );

				return( c );
			}
		}

		protected void
		removeChannel(
			channel	c )
		{
			synchronized( lock ){

				channels.remove( c );

				if ( channels.size() == 0 && save_to.exists()){

					destroy( null );
				}
			}
		}

		protected void
		destroy(
			Throwable error )
		{
			if ( error != null ){

				Debug.out( error );
			}

			synchronized( lock ){

				if ( context_destroyed ){

					return;
				}

				context_destroyed = true;

				if ( channels != null ){

					List<channel> channels_copy = new ArrayList<>(channels);

					for ( channel c: channels_copy ){

						c.destroy();
					}
				}

				if ( raf != null ){

					try{
						raf.close();

					}catch( Throwable e ){
					}

					raf = null;
				}

				if ( stream_details != null ){

					try{
						stream_details.getStream().close();

					}catch( Throwable e ){

					}

					stream_details = null;
				}

				if ( error != null ){

					save_to.delete();
				}
			}

			DiskManagerFileInfoStream.this.destroyed( this );
		}

		protected class
		channel
			implements DiskManagerChannel
		{
			volatile boolean	channel_destroyed;
			volatile long		channel_position;

			@Override
			public DiskManagerRequest
			createRequest()
			{
				return( new request());
			}

			@Override
			public DiskManagerFileInfo
			getFile()
			{
				return( DiskManagerFileInfoStream.this );
			}

			@Override
			public long
			getPosition()
			{
				return( channel_position );
			}

			@Override
			public boolean
			isDestroyed()
			{
				return( channel_destroyed );
			}

			@Override
			public void
			destroy()
			{
				channel_destroyed	= true;

				removeChannel( this );
			}

			protected class
			request
				implements DiskManagerRequest
			{
				private long		offset;
				private long		length;

				private long		position;

				private int			max_read_chunk = 128*1024;

				private volatile boolean	request_cancelled;

				private CopyOnWriteList<DiskManagerListener>		listeners = new CopyOnWriteList<>();

				@Override
				public void
				setType(
					int			type )
				{
					if ( type != DiskManagerRequest.REQUEST_READ ){

						throw( new RuntimeException( "Not supported" ));
					}
				}

				@Override
				public void
				setOffset(
					long		_offset )
				{
					offset		= _offset;
				}

				@Override
				public void
				setLength(
					long		_length )
				{
						// length can be -1 here meaning 'to the end'

					length		= _length==-1?Long.MAX_VALUE:_length;
				}

				@Override
				public void
				setMaximumReadChunkSize(
					int 	size )
				{
					if ( size > 16*1024 ){

						max_read_chunk = size;
					}
				}

				@Override
				public long
				getAvailableBytes()
				{
					return( getRemaining());
				}

				@Override
				public long
				getRemaining()
				{
					return( length==Long.MAX_VALUE?length:(offset + length - position ));
				}

				@Override
				public void
				run()
				{
					try{
						byte[] buffer = new byte[max_read_chunk];

						long	rem		= length;
						long	pos 	= offset;

						while( rem > 0 ){

							if ( request_cancelled ){

								throw( new Exception( "Cancelled" ));

							}else if ( channel_destroyed ){

								throw( new Exception( "Destroyed" ));
							}

							int	chunk = (int)Math.min( rem, max_read_chunk );

							int	len = read( buffer, pos, chunk );

							if ( len == -1 ){

								if ( length == Long.MAX_VALUE ){

									break;

								}else{

									throw( new Exception( "Premature end of stream (complete)" ));
								}
							}else if ( len == 0 ){

								sendEvent( new event( pos ));

							}else{

								sendEvent( new event( new PooledByteBufferImpl( buffer, 0, len ), pos, len ));

								rem -= len;
								pos	+= len;
							}
						}
					}catch( Throwable e ){

						sendEvent( new event( e ));
					}
				}

				@Override
				public void
				cancel()
				{
					request_cancelled = true;
				}

				@Override
				public void
				setUserAgent(
					String		agent )
				{
				}

				protected void
				sendEvent(
					event		ev )
				{
					for ( DiskManagerListener l: listeners ){

						l.eventOccurred( ev );
					}
				}

				@Override
				public void
				addListener(
					DiskManagerListener	listener )
				{
					listeners.add( listener );
				}

				@Override
				public void
				removeListener(
					DiskManagerListener	listener )
				{
					listeners.remove( listener );
				}

				protected class
				event
					implements DiskManagerEvent
				{
					private int					event_type;
					private Throwable			error;
					private PooledByteBuffer	buffer;
					private long				event_offset;
					private int					event_length;

					protected
					event(
						Throwable		_error )
					{
						event_type	= DiskManagerEvent.EVENT_TYPE_FAILED;
						error		= _error;
					}

					protected
					event(
						long				_offset )
					{
						event_type		= DiskManagerEvent.EVENT_TYPE_BLOCKED;

						event_offset	= _offset;

						channel_position	= _offset;
					}

					protected
					event(
						PooledByteBuffer	_buffer,
						long				_offset,
						int					_length )
					{
						event_type		= DiskManagerEvent.EVENT_TYPE_SUCCESS;
						buffer			= _buffer;
						event_offset	= _offset;
						event_length	= _length;

						channel_position	= _offset + _length - 1;
					}

					@Override
					public int
					getType()
					{
						return( event_type );
					}

					public DiskManagerRequest
					getRequest()
					{
						return( request.this );
					}

					@Override
					public long
					getOffset()
					{
						return( event_offset );
					}

					@Override
					public int
					getLength()
					{
						return( event_length );
					}

					@Override
					public PooledByteBuffer
					getBuffer()
					{
						return( buffer );
					}

					@Override
					public Throwable
					getFailure()
					{
						return( error );
					}
				}
			}
		}
	}

	public interface
	StreamFactory
	{
		public StreamDetails
		getStream(
			Object		requester )

			throws IOException;

		public void
		destroyed(
			Object		requester );

		public interface
		StreamDetails
		{
			public InputStream
			getStream();

			public boolean
			hasFailed();
		}
	}
}
