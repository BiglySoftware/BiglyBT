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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import com.biglybt.core.security.SEPasswordListener;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.disk.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.utils.PooledByteBufferImpl;
import com.biglybt.plugin.extseed.ExternalSeedException;

public class
DiskManagerFileInfoURL
	implements DiskManagerFileInfo, SEPasswordListener
{
	URL					url;
	private byte[]				hash;

	File				file;

	private Object	lock = new Object();

	URL			redirected_url;
	int			consec_redirect_fails;

	private volatile boolean		file_cached;

	public
	DiskManagerFileInfoURL(
		URL					_url )
	{
		url	= _url;

		String url_str = url.toExternalForm();

		String id_key = "azcdid=";
		String dn_key = "azcddn=";

		int id_pos = url_str.indexOf( id_key );
		int dn_pos = url_str.indexOf( dn_key );

		int	min_pos = id_pos;
		if ( min_pos == -1 ){
			min_pos = dn_pos;
		}else{
			if ( dn_pos != -1 ){
				min_pos = Math.min( min_pos, dn_pos );
			}
		}

		if ( min_pos > 0 ){

			try{
				url = new URL( url_str.substring( 0, min_pos-1 ) );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		try{
			hash		= new SHA1Simple().calculateHash( ( "DiskManagerFileInfoURL" +  url.toExternalForm()).getBytes( "UTF-8" ));

		}catch( Throwable e ){

			Debug.out(e);
		}

		String file_name;

		if ( dn_pos != -1 ){

			String dn = url_str.substring( dn_pos + dn_key.length());

			dn_pos = dn.indexOf( '&' );

			if ( dn_pos != -1 ){

				dn = dn.substring( 0, dn_pos );
			}

			file_name = UrlUtils.decode( dn );

		}else{

			String path = url.getPath();

			int pos = path.lastIndexOf( "/" );

			if ( pos != -1 ){

				path = path.substring( pos+1 );
			}

			path = path.trim();

			if ( url_str.length() > 0 ){

				file_name = UrlUtils.decode( path );

			}else{

				file_name = Base32.encode( hash );
			}
		}

		file_name = FileUtil.convertOSSpecificChars( file_name, false );

		try{
			file = FileUtil.newFile( AETemporaryFileHandler.createTempDir(), file_name );

		}catch( Throwable e ){

			file_name += ".tmp";

			file = FileUtil.newFile( AETemporaryFileHandler.getTempDirectory(), file_name );
		}
	}

	public URL
	getURL()
	{
		return( url );
	}

	public void
	download()
	{
		synchronized( lock ){

			if ( file_cached ){

				return;
			}

			try{
				channel chan = createChannel();

				channel.request req = chan.createRequest();

				req.setAll();

				final FileOutputStream fos = FileUtil.newFileOutputStream( file );

				boolean	ok = false;

				try{
					req.addListener(
						new DiskManagerListener()
						{
							@Override
							public void
							eventOccurred(
								DiskManagerEvent	event )
							{
								if ( event.getType() == DiskManagerEvent.EVENT_TYPE_FAILED ){

									throw( new RuntimeException( event.getFailure()));
								}

								PooledByteBuffer buffer = event.getBuffer();

								if ( buffer == null ){

									throw( new RuntimeException( "eh?" ));
								}

								try{

									fos.write( buffer.toByteArray());

								}catch( IOException e ){

									throw( new RuntimeException( "Failed to write to " + file, e ));

								}finally{

									buffer.returnToPool();
								}
							}
						});

					req.run();

					ok = true;

				}finally{

					try{
						fos.close();

					}catch( Throwable e ){

						Debug.out( e );
					}

					if ( !ok ){

						file.delete();

					}else{

						file_cached = true;
					}
				}
			}catch( Throwable e ){

				Debug.out( "Failed to cache file from " + url, e );
			}
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
		File		link_destination,
		boolean		no_delete )
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
		if ( file_cached ){

			long len = file.length();

			if ( len > 0 ){

				return( len );
			}
		}

		return( -1 );
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
		boolean	follow_link )
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
	public channel
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

	@Override
	public PasswordAuthentication
	getAuthentication(
		String		realm,
		URL			tracker )
	{
		return( null );
	}

	@Override
	public void
	setAuthenticationOutcome(
		String		realm,
		URL			tracker,
		boolean		success )
	{
	}

	@Override
	public void
	clearPasswords()
	{
	}

	protected class
	channel
		implements DiskManagerChannel
	{
		volatile boolean	channel_destroyed;
		volatile long		channel_position;

		@Override
		public request
		createRequest()
		{
			return( new request());
		}

		@Override
		public DiskManagerFileInfo
		getFile()
		{
			return( DiskManagerFileInfoURL.this );
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
			private boolean		do_all_file;

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

			public void
			setAll()
			{
				do_all_file = true;
				offset		= 0;
				setLength( -1 );
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

					InputStream	is = null;

					try{
						SESecurityManager.setThreadPasswordHandler( DiskManagerFileInfoURL.this );

						// System.out.println( "Connecting to " + url + ": " + Thread.currentThread().getId());

							HttpURLConnection	connection;
							int					response;

							Set<String>	redirect_urls = new HashSet<>();

redirect_loop:
							while( true ){

								URL	target = redirected_url==null?url:redirected_url;

								for ( int ssl_loop=0; ssl_loop<2; ssl_loop++ ){

									try{
										connection = (HttpURLConnection)target.openConnection();

										if ( connection instanceof HttpsURLConnection ){

											HttpsURLConnection ssl_con = (HttpsURLConnection)connection;

												// allow for certs that contain IP addresses rather than dns names

										ssl_con.setHostnameVerifier(
											new HostnameVerifier()
											{
												@Override
												public boolean
												verify(
													String		host,
													SSLSession	session )
												{
													return( true );
												}
											});
									}

									connection.setRequestProperty( "Connection", "Keep-Alive" );

									if ( !do_all_file ){

										connection.setRequestProperty( "Range", "bytes=" + offset + "-" + (offset+length-1));
									}

									connection.setConnectTimeout( 20*1000 );

									connection.connect();

									connection.setReadTimeout( 10*1000 );

									response = connection.getResponseCode();

									if (	response == HttpURLConnection.HTTP_ACCEPTED ||
											response == HttpURLConnection.HTTP_OK ||
											response == HttpURLConnection.HTTP_PARTIAL ){

										if ( redirected_url != null ){

											consec_redirect_fails = 0;
										}

										break redirect_loop;

									}else if ( 	response == HttpURLConnection.HTTP_MOVED_TEMP ||
												response == HttpURLConnection.HTTP_MOVED_PERM ){

											// auto redirect doesn't work from http to https or vice-versa

										String	move_to = connection.getHeaderField( "location" );

										if ( move_to != null ){

											if ( redirect_urls.contains( move_to ) || redirect_urls.size() > 32 ){

												throw( new ExternalSeedException( "redirect loop" ));
											}

											redirect_urls.add( move_to );

											redirected_url = new URL( move_to );

											continue redirect_loop;
										}
									}

									if ( redirected_url == null ){

										break redirect_loop;
									}

										// try again with original URL

									consec_redirect_fails++;

									redirected_url = null;

								}catch( SSLException e ){

									if ( ssl_loop == 0 ){

										if ( SESecurityManager.installServerCertificates( target ) != null ){

												// certificate has been installed

											continue;	// retry with new certificate
										}
									}

									throw( e );
								}

									// don't need another SSL loop

								break;
							}
						}

						URL final_url = connection.getURL();

						if ( consec_redirect_fails < 10 && !url.toExternalForm().equals( final_url.toExternalForm())){

							redirected_url = final_url;
						}

						is = connection.getInputStream();

						while( rem > 0 ){

							if ( request_cancelled ){

								throw( new Exception( "Cancelled" ));

							}else if ( channel_destroyed ){

								throw( new Exception( "Destroyed" ));
							}

							int	len = is.read( buffer );

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
					}finally{

						SESecurityManager.unsetThreadPasswordHandler();

						// System.out.println( "Done to " + url + ": " + Thread.currentThread().getId() + ", outcome=" + outcome );

						if ( is != null ){

							try{
								is.close();

							}catch( Throwable e ){

							}
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
