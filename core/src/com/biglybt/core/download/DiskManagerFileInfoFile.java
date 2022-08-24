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

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.QTFastStartRAF;
import com.biglybt.core.util.SHA1Simple;
import com.biglybt.pif.disk.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.utils.PooledByteBufferImpl;

public class
DiskManagerFileInfoFile
	implements DiskManagerFileInfo
{
	private byte[]		hash;
	File		file;

	public
	DiskManagerFileInfoFile(
		File		_file )
	{
		file		= _file;

		try{
			hash		= new SHA1Simple().calculateHash( file.getAbsolutePath().getBytes( "UTF-8" ));

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
		return( file.lastModified());
	}
	
	@Override
	public long
	getLength()
	{
		return( file.length());
	}

	@Override
	public File
	getFile()
	{
		return( file );
	}

	@Override
	public File
	getFile(
		boolean follow_link )
	{
		return( file );
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
		long	piece_size = getPieceSize();

		return((int)(( getLength() + piece_size - 1 ) / piece_size ));
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
		return( new channel());
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
			return( DiskManagerFileInfoFile.this );
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
		}

		protected class
		request
			implements DiskManagerRequest
		{
			private long		offset;
			private long		length;

			private long		position;

			private int			max_read_chunk = 128*1024;

			private volatile boolean	cancelled;

			private String		user_agent;

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
				if ( _length < 0 ){

					throw( new RuntimeException( "Illegal argument" ));
				}

				length		= _length;
			}

			@Override
			public void
			setMaximumReadChunkSize(
				int 	size )
			{
				max_read_chunk = size;
			}

			@Override
			public void
			setUserAgent(
				String		agent )
			{
				user_agent	= agent;
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
				return( offset + length - position );
			}

			@Override
			public void
			run()
			{
				QTFastStartRAF	raf = null;

				String name = file.getName();

				int	dot_pos = name.lastIndexOf('.');

				String ext = dot_pos<0?"":name.substring(dot_pos+1);

				try{
					raf = new QTFastStartRAF( file, user_agent != null && QTFastStartRAF.isSupportedExtension( ext ));

					raf.seek( offset );

					byte[] buffer = new byte[max_read_chunk];

					long	rem		= length;
					long	pos 	= offset;

					while( rem > 0 ){

						if ( cancelled ){

							throw( new Exception( "Cancelled" ));

						}else if ( channel_destroyed ){

							throw( new Exception( "Destroyed" ));
						}

						int	chunk = (int)Math.min( rem, max_read_chunk );

						int	len = raf.read( buffer, 0, chunk );

						sendEvent( new event( new PooledByteBufferImpl( buffer, 0, len ), pos, len ));

						rem -= len;
						pos	+= len;
					}
				}catch( Throwable e ){

					sendEvent( new event( e ));

				}finally{

					if ( raf != null ){

						try{
							raf.close();

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			}

			@Override
			public void
			cancel()
			{
				cancelled = true;
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
