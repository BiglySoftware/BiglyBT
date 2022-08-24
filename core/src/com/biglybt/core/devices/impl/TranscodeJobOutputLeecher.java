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


package com.biglybt.core.devices.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.biglybt.core.devices.TranscodeException;
import com.biglybt.core.devices.TranscodeJob;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SHA1Simple;
import com.biglybt.pif.disk.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.utils.PooledByteBufferImpl;

public class
TranscodeJobOutputLeecher
	implements DiskManagerFileInfo
{
	TranscodeJobImpl		job;
	TranscodeFileImpl		file;

	File	save_to;
	private byte[]	hash;

	public
	TranscodeJobOutputLeecher(
		TranscodeJobImpl		_job,
		TranscodeFileImpl		_file )

		throws TranscodeException
	{
		job		= _job;
		file	= _file;

		save_to = file.getCacheFile();

		try{
			hash = new SHA1Simple().calculateHash( save_to.getAbsolutePath().getBytes( "UTF-8" ));

		}catch( Throwable e ){

			Debug.out(e);
		}
	}


	@Override
	public void
	setPriority(
		boolean b )
	{
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
	public void
	setDeleted(boolean b)
	{
	}

	@Override
	public void
	setLink(
		File	link_destination,
		boolean	dont_delete )
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
	public long getLastModified(){
		
		return save_to.lastModified();
	}
	
	@Override
	public long
	getLength()
	{
		if ( file.isComplete()){

			try{
				return( file.getTargetFile().getLength());

			}catch( Throwable e ){

				return( -1 );
			}
		}else{

			return( -1 );
		}
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
		return( new Channel());
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

	protected class
	Channel
		implements DiskManagerChannel
	{
		volatile boolean		channel_destroyed;
		volatile long			channel_position;

		private RandomAccessFile		raf;

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
			return( TranscodeJobOutputLeecher.this );
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
			synchronized( this ){

				channel_destroyed	= true;

				if ( raf != null ){

					try{
						raf.close();

					}catch( Throwable e ){
					}

					raf = null;
				}
			}
		}

		protected int
		read(
			byte[]		buffer,
			long		offset,
			int			length )

			throws IOException
		{
			synchronized( this ){

				if ( channel_destroyed ){

					throw( new IOException( "Channel destroyed" ));
				}

				if ( raf == null ){

					if ( save_to.exists()){

						raf = new RandomAccessFile( save_to, "r" );

					}else{

						int state = job.getState();

						if ( state == TranscodeJob.ST_REMOVED ){

							throw( new IOException( "Job has been removed" ));

						}else if ( 	state == TranscodeJob.ST_FAILED ||
									state == TranscodeJob.ST_CANCELLED ){

							throw( new IOException( "Job has failed or been cancelled" ));

						}else if ( 	state == TranscodeJob.ST_COMPLETE ){

							throw( new IOException( "Job is complete but file missing" ));
						}

							// fall through and return 0 read
					}
				}

				if ( raf != null ){

					if ( raf.length() > offset ){

						raf.seek( offset );

						return( raf.read( buffer, 0, length ));

					}else{

							// data not yet available or file complete

						if ( file.isComplete()){

							return( -1 );
						}
					}
				}
			}

			try{
				Thread.sleep( 500 );

			}catch( Throwable e ){

				throw( new IOException( "Interrupted" ));
			}

			return( 0 );
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

					channel_position = _offset;
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

					channel_position = _offset + _length - 1;
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
