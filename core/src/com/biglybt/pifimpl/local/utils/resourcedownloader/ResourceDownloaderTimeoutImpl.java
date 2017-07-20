/*
 * Created on 25-Apr-2004
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

package com.biglybt.pifimpl.local.utils.resourcedownloader;

/**
 * @author parg
 *
 */

import java.io.InputStream;

import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderCancelledException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener;

public class
ResourceDownloaderTimeoutImpl
	extends 	ResourceDownloaderBaseImpl
	implements	ResourceDownloaderListener
{
	protected ResourceDownloaderBaseImpl		delegate;

	protected int						timeout_millis;

	protected boolean						cancelled;
	protected ResourceDownloaderBaseImpl	current_downloader;

	protected Object					result;
	protected AESemaphore				done_sem	= new AESemaphore("RDTimeout");

	protected long						size = -2;

	public
	ResourceDownloaderTimeoutImpl(
		ResourceDownloaderBaseImpl	_parent,
		ResourceDownloader			_delegate,
		int							_timeout_millis )
	{
		super( _parent );

		delegate			= (ResourceDownloaderBaseImpl)_delegate;

		delegate.setParent( this );

		timeout_millis		= _timeout_millis;
	}

	@Override
	public String
	getName()
	{
		return( delegate.getName() + ": timeout=" + timeout_millis );
	}

	@Override
	public long
	getSize()

		throws ResourceDownloaderException
	{
		if ( size != -2 ){

			return( size );
		}

		try{
			ResourceDownloaderTimeoutImpl x = new ResourceDownloaderTimeoutImpl( getParent(), delegate.getClone( this ), timeout_millis );

			addReportListener( x );

			size = x.getSizeSupport();

			setProperties( x );

		}finally{

			if ( size == -2 ){

				size = -1;
			}

			setSize( size );
		}

		return( size );
	}

	@Override
	protected void
	setSize(
		long	l )
	{
		size	= l;

		if ( size >= 0 ){

			delegate.setSize( size );
		}
	}

	@Override
	public void
	setProperty(
		String	name,
		Object	value )

		throws ResourceDownloaderException
	{
		setPropertySupport( name, value );

		delegate.setProperty( name, value );
	}

	@Override
	public ResourceDownloaderBaseImpl
	getClone(
		ResourceDownloaderBaseImpl	parent )
	{
		ResourceDownloaderTimeoutImpl c = new ResourceDownloaderTimeoutImpl( getParent(), delegate.getClone( parent ), timeout_millis );

		c.setSize( size );

		c.setProperties( this );

		return( c );
	}

	@Override
	public InputStream
	download()

		throws ResourceDownloaderException
	{
		asyncDownload();

		done_sem.reserve();

		if ( result instanceof InputStream ){

			return((InputStream)result);
		}

		throw((ResourceDownloaderException)result);
	}

	@Override
	public void
	asyncDownload()
	{
		try{
			this_mon.enter();

			if ( !cancelled ){

				current_downloader = delegate.getClone( this );

				informActivity( getLogIndent() + "Downloading: " + getName());

				current_downloader.addListener( this );

				current_downloader.asyncDownload();

				Thread t = new AEThread( "ResourceDownloaderTimeout")
					{
						@Override
						public void
						runSupport()
						{
							try{
								Thread.sleep( timeout_millis );

								cancel(new ResourceDownloaderException( ResourceDownloaderTimeoutImpl.this, "Download timeout"));

							}catch( Throwable e ){

								Debug.printStackTrace( e );
							}
						}
					};

				t.setDaemon(true);

				t.start();
			}
		}finally{

			this_mon.exit();
		}
	}

	protected long
	getSizeSupport()

		throws ResourceDownloaderException
	{
		asyncGetSize();

		done_sem.reserve();

		if ( result instanceof Long ){

			return(((Long)result).longValue());
		}

		throw((ResourceDownloaderException)result);
	}

	public void
	asyncGetSize()
	{
		try{
			this_mon.enter();

			if ( !cancelled ){

				current_downloader = delegate.getClone( this );

				Thread	size_thread = new AEThread( "ResourceDownloader:size getter" )
					{
						@Override
						public void
						runSupport()
						{
							try{
								long	res = current_downloader.getSize();

								result	= new Long(res);

								setProperties( current_downloader );

								done_sem.release();

							}catch( ResourceDownloaderException e ){

								failed( current_downloader, e );
							}
						}
					};

				size_thread.setDaemon( true );

				size_thread.start();

				Thread t = new AEThread( "ResourceDownloaderTimeout")
					{
						@Override
						public void
						runSupport()
						{
							try{
								Thread.sleep( timeout_millis );

								cancel(new ResourceDownloaderException( ResourceDownloaderTimeoutImpl.this, "getSize timeout"));

							}catch( Throwable e ){

								Debug.printStackTrace( e );
							}
						}
					};

				t.setDaemon(true);

				t.start();
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	cancel()
	{
		cancel( new ResourceDownloaderCancelledException(  this  ));
	}

	protected void
	cancel(
		ResourceDownloaderException reason )
	{
		setCancelled();

		try{
			this_mon.enter();

			result	= reason;

			cancelled	= true;

			informFailed((ResourceDownloaderException)result );

			if ( current_downloader != null ){

				current_downloader.cancel();
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public boolean
	completed(
		ResourceDownloader	downloader,
		InputStream			data )
	{
		if (informComplete( data )){

			result	= data;

			done_sem.release();

			return( true );
		}

		return( false );
	}

	@Override
	public void
	failed(
		ResourceDownloader			downloader,
		ResourceDownloaderException e )
	{
		result		= e;

		done_sem.release();

		informFailed( e );
	}
}
