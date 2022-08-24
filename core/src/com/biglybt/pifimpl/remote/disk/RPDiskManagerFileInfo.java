/*
 * Created on 09-May-2005
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

package com.biglybt.pifimpl.remote.disk;

import java.io.File;

import com.biglybt.pif.disk.DiskManagerChannel;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.disk.DiskManagerListener;
import com.biglybt.pif.disk.DiskManagerRandomReadRequest;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;

public class
RPDiskManagerFileInfo
	extends		RPObject
	implements 	DiskManagerFileInfo
{
	protected transient DiskManagerFileInfo		delegate;

		// don't change these field names as they are visible on XML serialisation

	public int			access_mode;
	public long			downloaded;
	public long			length;
	public File			file;
	public int			first_piece_number;
	public int			num_pieces;
	public boolean		is_priority;
	public boolean		is_skipped;

	public static RPDiskManagerFileInfo
	create(
		DiskManagerFileInfo		_delegate )
	{
		RPDiskManagerFileInfo	res =(RPDiskManagerFileInfo)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPDiskManagerFileInfo( _delegate );
		}

		return( res );
	}

	protected
	RPDiskManagerFileInfo(
		DiskManagerFileInfo		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (DiskManagerFileInfo)_delegate;

		access_mode				= delegate.getAccessMode();
		downloaded				= delegate.getDownloaded();
		length					= delegate.getLength();
		file					= delegate.getFile();
		first_piece_number		= delegate.getFirstPieceNumber();
		num_pieces				= delegate.getNumPieces();
		is_priority				= delegate.isPriority();
		is_skipped				= delegate.isSkipped();
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		return( _fixupLocal());
	}

	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String	method = request.getMethod();

		throw( new RPException( "Unknown method: " + method ));
	}


		// ***************************************************

	@Override
	public void
	setPriority(boolean b)
	{
		notSupported();
	}

	@Override
	public void setSkipped(boolean b)
	{
		notSupported();
	}
	
	@Override
	public Boolean 
	isSkipping()
	{
		notSupported();
		
		return( null );
	}

	@Override
	public int getNumericPriority() {
		notSupported();
		return 0;
	}

	@Override
	public void
	setNumericPriority(
		int priority)
	{
		notSupported();
	}

	@Override
	public void
	setDeleted(boolean b)
	{
		notSupported();
	}
	@Override
	public boolean
	isDeleted()
	{
		notSupported();

		return( false );
	}

	@Override
	public int getAccessMode()
	{
		return( access_mode );
	}

	@Override
	public long getDownloaded()
	{
		return( downloaded );
	}
	
	
	@Override
	public long getLastModified(){
		
		notSupported();
		
		return( 0 );
	}

	@Override
	public long getLength()
	{
		return( length );
	}

	@Override
	public File getFile()
	{
		return( file );
	}

	@Override
	public File
	getFile(
		boolean	follow_link )
	{
		if ( follow_link ){

			notSupported();
		}

		return( file );
	}

	@Override
	public int getFirstPieceNumber()
	{
		return( first_piece_number );
	}

	@Override
	public long getPieceSize()
	{
		notSupported();

		return(-1);
	}

	@Override
	public int getNumPieces()
	{
		return( num_pieces );
	}

	@Override
	public boolean isPriority()
	{
		return( is_priority );
	}

	@Override
	public boolean isSkipped()
	{
		return( is_skipped );
	}

	@Override
	public int
	getIndex()
	{
		notSupported();

		return( -1 );
	}

	@Override
	public void
	setLink(
		File	link_destination,
		boolean	dont_delete )
	{
		notSupported();
	}

	@Override
	public File
	getLink()
	{
		notSupported();

		return( null );
	}

	@Override
	public byte[]
	getDownloadHash()
    {
		notSupported();

		return( null );
    }

	@Override
	public Download
	getDownload()
         throws DownloadException
    {
		notSupported();

		return( null );
    }

	@Override
	public DiskManagerChannel
	createChannel()
	{
		notSupported();

		return( null );
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
		notSupported();

		return( null );
	}
}
