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

import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener;

public class
ResourceDownloaderErrorImpl
	extends ResourceDownloaderBaseImpl
{
	protected ResourceDownloaderException		error;

	protected
	ResourceDownloaderErrorImpl(
		ResourceDownloaderBaseImpl	_parent,
		ResourceDownloaderException	_error )
	{
		super( _parent );

		error	= _error;
	}

	@Override
	public String
	getName()
	{
		return( "<error>:" + error.getMessage());
	}

	@Override
	public ResourceDownloaderBaseImpl
	getClone(
		ResourceDownloaderBaseImpl	parent )
	{
		return( this );
	}

	@Override
	public InputStream
	download()

		throws ResourceDownloaderException
	{
		throw( error );
	}


	@Override
	public void
	asyncDownload()
	{
	}

	@Override
	protected void
	setSize(
		long	size )
	{
	}

	@Override
	public void
	setProperty(
		String	name,
		Object	value )
	{
		setPropertySupport( name, value );
	}

	@Override
	public long
	getSize()

		throws ResourceDownloaderException
	{
		throw( error );
	}

	@Override
	public void
	cancel()
	{
		setCancelled();
	}

	@Override
	public void
	reportActivity(
		String				activity )
	{
		informActivity( activity );
	}

	@Override
	public void
	addListener(
		ResourceDownloaderListener	l )
	{
		super.addListener(l);

		informFailed( error );
	}
}
