/*
 * Created on 21-May-2004
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

import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderDelayedFactory;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener;

public class
ResourceDownloaderDelayedImpl
	extends ResourceDownloaderBaseImpl
{
	protected ResourceDownloaderDelayedFactory		factory;

	protected ResourceDownloaderBaseImpl		delegate;

	protected long		size = -2;

	protected
	ResourceDownloaderDelayedImpl(
		ResourceDownloaderBaseImpl				_parent,
		ResourceDownloaderDelayedFactory		_factory )
	{
		super( _parent );

		factory	= _factory;
	}

	protected void
	getDelegate()
	{
		try{
			this_mon.enter();

			if ( delegate == null ){

				try{
					delegate	= (ResourceDownloaderBaseImpl)factory.create();

					delegate.setParent( this );

					if ( size >= 0 ){

						delegate.setSize( size );
					}

				}catch(  ResourceDownloaderException e ){

					delegate = new ResourceDownloaderErrorImpl( this, e );
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public String
	getName()
	{
		if ( delegate == null ){

			return( "<...>" );
		}

		return( delegate.getName());
	}

	@Override
	public ResourceDownloaderBaseImpl
	getClone(
		ResourceDownloaderBaseImpl	parent )
	{
		ResourceDownloaderDelayedImpl	c = new ResourceDownloaderDelayedImpl( parent, factory );

		c.setSize( size );

		c.setProperties( this );

		return( c );
	}

	@Override
	public InputStream
	download()

		throws ResourceDownloaderException
	{
		getDelegate();

		return( delegate.download());
	}


	@Override
	public void
	asyncDownload()
	{
		getDelegate();

		delegate.asyncDownload();
	}

	@Override
	protected void
	setSize(
		long	_size )
	{
		size	= _size;

		if ( delegate != null && size >= 0){

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

		if ( delegate != null ){

			delegate.setProperty( name, value );
		}
	}

	@Override
	public long
	getSize()

		throws ResourceDownloaderException
	{
		getDelegate();

		return( delegate.getSize());
	}

	@Override
	public void
	cancel()
	{
		setCancelled();

		getDelegate();

		delegate.cancel();
	}

	@Override
	public void
	reportActivity(
		String				activity )
	{
		getDelegate();

		delegate.reportActivity( activity );
	}

	@Override
	public void
	addListener(
		ResourceDownloaderListener	l )
	{
		getDelegate();

		delegate.addListener(l);
	}

	@Override
	public void
	removeListener(
		ResourceDownloaderListener	l )
	{
		getDelegate();

		delegate.removeListener(l);
	}
}
