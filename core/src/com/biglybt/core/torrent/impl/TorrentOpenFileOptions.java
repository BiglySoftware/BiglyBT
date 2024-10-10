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

import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.StringInterner;

import java.io.File;

/**
 * Class to store the file list of a Torrent.  Used to populate table and
 * store user's choices
 * <P>
 * This was copied out of the UI code, and still contains some crap code
 */
public class TorrentOpenFileOptions
{
	private final TorrentOpenOptions torrentOptions;

	/** relative path + full file name as specified by the torrent */

	private final TOTorrentFile	tfile;
	
	private String orgParent;

	private String orgFileName;

	/** Whether to download this file.  Probably should be switched to the DND state variable */
	
	private boolean toDownload;
	private int		priority;
	private boolean	priority_auto;

	private String destFileName;
	private String destPathName;

	private boolean	didManualRename;

	private boolean isValid;

	public
	TorrentOpenFileOptions(
		TorrentOpenOptions 	parent,
		TOTorrentFile		tfile,
		boolean				wanted )
	{
		this.torrentOptions 	= parent;
		this.tfile			= tfile;
		
		setOriginalFileNames();
		
		setToDownload( wanted );

		isValid = true;
	}

	protected void
	setOriginalFileNames()
	{
			// called directly after a torrent locale change
		
		String fileStr = tfile.getRelativePath(); // translated to locale
		
		File file = FileUtil.newFile( fileStr );
		
		String parent	= file.getParent();
		
		if ( parent == null || parent.isEmpty()){
			
			orgParent = null;
			
		}else{
			
			orgParent = StringInterner.intern( parent );
		}
		
		orgFileName = file.getName();
	}
	
	public TorrentOpenOptions
	getTorrentOptions()
	{
		return( torrentOptions );
	}
	
	public int
	getIndex()
	{
		return( tfile.getIndex());
	}

	public boolean
	isValid()
	{
		return( isValid );
	}
	
	public void
	setValid(
		boolean	b )
	{
		isValid = b;
	}
	
	public long
	getSize()
	{
		return( tfile.getLength());
	}
	
	public String
	getOriginalFullName()
	{
		if ( orgParent == null ){
			
			return( orgFileName );
			
		}else{
		
			return( orgParent + File.separator + orgFileName );
		}
	}
	
	public String
	getOriginalFileName()
	{
		return( orgFileName );
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
		if(torrentOptions.getTorrent().isSimpleTorrent())
			torrentOptions.setParentDir(newPath);
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

	public String 
	getDestPathName() 
	{
		if ( destPathName != null ){
			
			return destPathName;
		}
		
		if ( torrentOptions.getTorrent().isSimpleTorrent()){
			
			return torrentOptions.getParentDir();
		}
		
		return FileUtil.newFile(torrentOptions.getDataDir(), getOriginalFullName()).getParent();
	}

	public boolean
	isManualRename()
	{
		return( didManualRename );
	}

	public String 
	getDestFileName()
	{
		return destFileName == null ? orgFileName : destFileName;
	}

	public File 
	getDestFileFullName() 
	{
		String path = getDestPathName();
		
		String file = getDestFileName();
		
		return FileUtil.newFile(path,file);
	}

	public boolean 
	okToDisable() 
	{
		return( torrentOptions.okToDisableAll());
	}

	public File
	getInitialLink()
	{
		return( torrentOptions.getInitialLinkage( getIndex()));
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
		torrentOptions.fileDownloadStateChanged(this, toDownload);
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
		
		torrentOptions.filePriorityStateChanged(this, _priority);
	}
}

