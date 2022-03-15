/*
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

package com.biglybt.core.torrent.impl;

import com.biglybt.core.util.FileUtil;

import java.io.File;

/**
 * Class to store the file list of a Torrent.  Used to populate table and
 * store user's choices
 * <P>
 * This was copied out of the UI code, and still contains some crap code
 */
public class TorrentOpenFileOptions
{
	/** relative path + full file name as specified by the torrent */
	/** @todo: getter/setters */
	public String orgFullName;

	/** @todo: getter/setters */
	private String orgFileName;

	/** @todo: getter/setters */
	public final long lSize;

	/** Whether to download this file.  Probably should be switched to the DND state variable */
	private boolean toDownload;
	private int		priority;
	private boolean	priority_auto;

	private String destFileName;
	private String destPathName;

	private boolean	didManualRename;

	/** @todo: getter/setters */
	private final int iIndex;

	/** @todo: getter/setters */
	public boolean isValid;

	/** @todo: getter/setters */
	public final TorrentOpenOptions parent;


	/**
	 * Init
	 *
	 * @param parent
	 * @param torrentFile
	 * @param iIndex
	 */
	public
	TorrentOpenFileOptions(
		TorrentOpenOptions 	parent,
		int 				iIndex,
		String				orgFullName,
		String				orgFileName,
		long				lSize,
		boolean				wanted )
	{
		this.parent 		= parent;
		this.iIndex 		= iIndex;
		this.orgFullName	= orgFullName;
		this.orgFileName	= orgFileName;

		this.lSize 	= lSize;

		setToDownload( wanted );

		isValid = true;
	}

	public TorrentOpenOptions
	getTorrentOptions()
	{
		return( parent );
	}
	
	public int
	getIndex()
	{
		return( iIndex );
	}

	public String
	getOriginalFileName()
	{
		return( orgFileName );
	}
	
	public void
	setOriginalFileName(
		String str )
	{
		orgFileName = str;
	}
	
	public void setFullDestName(String newFullName)
	{
		if(newFullName == null)
		{
			setDestPathName(null);
			setDestFileName(null,true);
			return;
		}

		File newPath = FileUtil.newFile(newFullName);
		setDestPathName(newPath.getParent());
		setDestFileName(newPath.getName(),true);
	}

	public void setDestPathName(String newPath)
	{
		if(parent.getTorrent().isSimpleTorrent())
			parent.setParentDir(newPath);
		else
			destPathName = newPath;
	}

	public void setDestFileName (String newFileName, boolean manualRename )
	{
		if(orgFileName.equals(newFileName)){
			destFileName = null;
			didManualRename = false;
		}else{
			destFileName = newFileName;
			didManualRename = manualRename;
		}
	}

	public String getDestPathName() {
		if (destPathName != null)
			return destPathName;

		if (parent.getTorrent().isSimpleTorrent())
			return parent.getParentDir();

		return FileUtil.newFile(parent.getDataDir(), orgFullName).getParent();
	}

	public boolean
	isManualRename()
	{
		return( didManualRename );
	}

	public String getDestFileName() {
		return destFileName == null ? orgFileName : destFileName;
	}

	public File getDestFileFullName() {
		String path = getDestPathName();
		String file = getDestFileName();
		return FileUtil.newFile(path,file);
	}

	public boolean okToDisable() {
		return /* lSize >= MIN_NODOWNLOAD_SIZE	|| */parent.okToDisableAll();
	}

	public File
	getInitialLink()
	{
		return( parent.getInitialLinkage( iIndex ));
	}

	public boolean isLinked()
	{
		return destFileName != null || destPathName != null;
	}

	public boolean isToDownload() {
		return toDownload;
	}

	public void setToDownload(boolean toDownload) {
		this.toDownload = toDownload;
		parent.fileDownloadStateChanged(this, toDownload);
	}

	public int
	getPriority()
	{
		return( priority );
	}

	public boolean
	isPriorityAuto()
	{
		return( priority_auto );
	}
	
	public void
	setPriority(
		int		_priority,
		boolean	_auto )
	{
		priority_auto = _auto;
		
		priority = _priority;
		
		parent.filePriorityStateChanged(this, _priority);
	}
}
