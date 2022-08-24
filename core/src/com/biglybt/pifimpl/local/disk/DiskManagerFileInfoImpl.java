/*
 * Created : 2004/May/26
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.disk;

import java.io.File;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.disk.DiskManagerChannel;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.disk.DiskManagerListener;
import com.biglybt.pif.disk.DiskManagerRandomReadRequest;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pifimpl.local.download.DownloadImpl;
import com.biglybt.pifimpl.local.download.DownloadManagerImpl;


/**
 * @author TuxPaper
 *
 */

public class
DiskManagerFileInfoImpl
	implements DiskManagerFileInfo
{
	protected DownloadImpl										download;
	protected com.biglybt.core.disk.DiskManagerFileInfo 	core;

	public
	DiskManagerFileInfoImpl(
		DownloadImpl										_download,
		com.biglybt.core.disk.DiskManagerFileInfo 	coreFileInfo )
	{
	  core 		= coreFileInfo;
	  download	= _download;
	}

	@Override
	public void setPriority(boolean b) {
	  core.setPriority(b?1:0);
	}

	@Override
	public void setSkipped(boolean b) {
	  core.setSkipped(b);
	}

	@Override
	public Boolean 
	isSkipping()
	{
		return( core.isSkipping());
	}
	
	@Override
	public int getNumericPriority() {
		return( core.getPriority());
	}

	@Override
	public void
	setNumericPriority(
		int priority)
	{
		core.setPriority( priority );
	}

	@Override
	public void
	setDeleted(boolean b)
	{
		int st = core.getStorageType();

		int	target_st;

		if ( b ){

			if ( st == com.biglybt.core.disk.DiskManagerFileInfo.ST_LINEAR ){

				target_st = com.biglybt.core.disk.DiskManagerFileInfo.ST_COMPACT;

			}else if ( st == com.biglybt.core.disk.DiskManagerFileInfo.ST_REORDER ){

				target_st = com.biglybt.core.disk.DiskManagerFileInfo.ST_REORDER_COMPACT;

			}else{

				return;
			}

		}else{

			if ( st == com.biglybt.core.disk.DiskManagerFileInfo.ST_COMPACT ){

				target_st = com.biglybt.core.disk.DiskManagerFileInfo.ST_LINEAR;

			}else if ( st == com.biglybt.core.disk.DiskManagerFileInfo.ST_REORDER_COMPACT ){

				target_st = com.biglybt.core.disk.DiskManagerFileInfo.ST_REORDER;

			}else{

				return;
			}
		}

		core.setStorageType( target_st );

	}

	@Override
	public boolean
	isDeleted()
	{
		int st = core.getStorageType();

		return( st ==  com.biglybt.core.disk.DiskManagerFileInfo.ST_COMPACT || st == com.biglybt.core.disk.DiskManagerFileInfo.ST_REORDER_COMPACT );
	}

	@Override
	public void
	setLink(
		File		link_destination,
		boolean		no_delete )
	{
		core.setLink( link_destination, no_delete );
	}

	@Override
	public File
	getLink()
	{
		return( core.getLink());
	}
	 	// get methods

	@Override
	public int getAccessMode() {
	  return core.getAccessMode();
	}

	@Override
	public long getDownloaded() {
	  return core.getDownloaded();
	}

	@Override
	public long 
	getLastModified()
	{
		return( core.getLastModified());
	}
	
	@Override
	public long getLength() {
		  return core.getLength();
		}
	@Override
	public File getFile() {
	  return core.getFile(false);
	}

	@Override
	public File
	getFile(
		boolean	follow_link )
	{
		return( core.getFile( follow_link ));
	}

	@Override
	public int getFirstPieceNumber() {
	  return core.getFirstPieceNumber();
	}

	@Override
	public long getPieceSize(){
		try{
			return getDownload().getTorrent().getPieceSize();
		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return(0);
		}
	}
	@Override
	public int getNumPieces() {
	  return core.getNbPieces();
	}

	@Override
	public boolean isPriority() {
	  return core.getPriority() != 0;
	}

	@Override
	public boolean isSkipped() {
	  return core.isSkipped();
	}

	@Override
	public int
	getIndex()
	{
		return( core.getIndex());
	}

	@Override
	public byte[] getDownloadHash()
		throws DownloadException
	{
		return( getDownload().getTorrent().getHash());
	}

	@Override
	public Download getDownload()
         throws DownloadException
    {
		if ( download != null ){

			return( download );
		}

			// not sure why this code is here as we already have the download - leaving in for the moment just in case...

		return DownloadManagerImpl.getDownloadStatic( core.getDownloadManager());
    }

	@Override
	public DiskManagerChannel
	createChannel()
	 	throws DownloadException
	{
		return( new DiskManagerChannelImpl( download, this ));
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
		return( DiskManagerRandomReadController.createRequest( download, this, file_offset, length, reverse_order, listener ));
	}


	// not visible to plugin interface
	public com.biglybt.core.disk.DiskManagerFileInfo
	getCore()
	{
		return( core );
	}
}
