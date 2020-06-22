/*
 * Created on 29-Nov-2004
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import com.biglybt.core.util.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderCancelledException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;

/**
 * @author parg
 *
 */
public class
ResourceDownloaderFileImpl
	extends 	ResourceDownloaderBaseImpl
{
	protected boolean					cancelled;
	protected File						file;

	protected Object					result;
	protected AESemaphore				done_sem	= new AESemaphore("RDTimeout");

	protected long						size = -2;

	public
	ResourceDownloaderFileImpl(
		ResourceDownloaderBaseImpl	_parent,
		File						_file )
	{
		super( _parent );

		file		= _file;
	}

	@Override
	public String
	getName()
	{
		return( file.toString());
	}

	@Override
	protected void
	setSize(
		long	size )
	{
	}

	@Override
	public long
	getSize()

		throws ResourceDownloaderException
	{
		String	file_str = file.toString();

		int	pos = file_str.lastIndexOf( "." );

		String	file_type;

		if ( pos != -1 ){

			file_type = file_str.substring(pos+1);

		}else{

			file_type = null;
		}

		setProperty( 	ResourceDownloader.PR_STRING_CONTENT_TYPE,
						HTTPUtils.guessContentTypeFromFileType( file_type ));

		return( FileUtil.getFileOrDirectorySize( file ));
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
	public ResourceDownloaderBaseImpl
	getClone(
		ResourceDownloaderBaseImpl	parent )
	{
		ResourceDownloaderFileImpl c = new ResourceDownloaderFileImpl( getParent(), file );

		return( c );
	}

	@Override
	public InputStream
	download()

		throws ResourceDownloaderException
	{
		asyncDownload();

		done_sem.reserve();

		if ( result instanceof ResourceDownloaderException ){

			throw((ResourceDownloaderException)result);
		}

		return((InputStream)result);
	}

	@Override
	public void
	asyncDownload()
	{
		try{
			this_mon.enter();

			if ( !cancelled ){

				informActivity( getLogIndent() + ( file.isDirectory()?"Processing: ":"Downloading: " ) + getName());

				final Object	parent_tls = TorrentUtils.getTLS();

				AEThread2 t =
					new AEThread2( "ResourceDownloaderTimeout", true )
					{
						@Override
						public void
						run()
						{
							Object	child_tls = TorrentUtils.getTLS();

							TorrentUtils.setTLS( parent_tls );

							try{

									// download of a local dir -> null inputstream

								if ( file.isDirectory()){

									completed( ResourceDownloaderFileImpl.this, null );

								}else{

									completed( ResourceDownloaderFileImpl.this, FileUtil.newFileInputStream( file ));
								}

							}catch( Throwable e ){

								failed( ResourceDownloaderFileImpl.this, new ResourceDownloaderException( ResourceDownloaderFileImpl.this, "Failed to read file", e ));

								Debug.printStackTrace( e );

							}finally{

								TorrentUtils.setTLS( child_tls );
							}
						}
					};

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

		}finally{

			this_mon.exit();
		}
	}

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
