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
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderCancelledException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener;

public class
ResourceDownloaderRetryImpl
	extends 	ResourceDownloaderBaseImpl
	implements	ResourceDownloaderListener
{
	protected ResourceDownloaderBaseImpl		delegate;
	protected int								retry_count;

	protected boolean					cancelled;
	protected ResourceDownloader		current_downloader;
	protected int						done_count;
	protected Object					result;
	protected AESemaphore				done_sem	= new AESemaphore("RDRretry");

	protected long						size	= -2;

	public
	ResourceDownloaderRetryImpl(
		ResourceDownloaderBaseImpl	_parent,
		ResourceDownloader		_delegate,
		int						_retry_count )
	{
		super( _parent );

		delegate		= (ResourceDownloaderBaseImpl)_delegate;

		delegate.setParent( this );

		retry_count		= _retry_count;
	}

	@Override
	public String
	getName()
	{
		return( delegate.getName() + ", retry=" + retry_count );
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
			for (int i=0;i<retry_count;i++){

				try{
					ResourceDownloaderBaseImpl c =  delegate.getClone( this );

					addReportListener( c );

					size = c.getSize();

					setProperties( c );

					return( size );

				}catch( ResourceDownloaderException e ){

					if ( i == retry_count - 1 ){

						throw( e );
					}
				}
			}
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
		ResourceDownloaderBaseImpl parent )
	{
		ResourceDownloaderRetryImpl c =  new ResourceDownloaderRetryImpl( parent, delegate.getClone( this ), retry_count );

		c.setSize(size);

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

			if ( done_count == retry_count || cancelled ){

				done_sem.release();

				informFailed((ResourceDownloaderException)result);

			}else{

				done_count++;

				if ( done_count > 1 ){

					informActivity( getLogIndent() + "  attempt " + done_count + " of " + retry_count );
				}

				current_downloader = delegate.getClone( this );

				current_downloader.addListener( this );

				current_downloader.asyncDownload();
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	cancel()
	{
		setCancelled();

		try{
			this_mon.enter();

			result	= new ResourceDownloaderCancelledException(  this  );

			cancelled	= true;

			informFailed((ResourceDownloaderException)result );

			done_sem.release();

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
		if ( informComplete( data )){

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

		asyncDownload();
	}
}
