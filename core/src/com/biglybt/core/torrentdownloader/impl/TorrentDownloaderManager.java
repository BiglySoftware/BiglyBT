/*
 * TorrentDownloaderManagerImpl.java
 *
 * Created on 2. November 2003, 04:29
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

package com.biglybt.core.torrentdownloader.impl;

import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.torrentdownloader.TorrentDownloader;
import com.biglybt.core.torrentdownloader.TorrentDownloaderCallBackInterface;
import com.biglybt.core.torrentdownloader.TorrentDownloaderFactory;

/**
 *
 * @author  Tobias Minich
 */
public class TorrentDownloaderManager{

    private static TorrentDownloaderManager man = null;

    private boolean autostart = false;
    private GlobalManager gm = null;
    
    private final List<TorrentDownloader> 	running = new ArrayList<>();
    private final List<TorrentDownloader>  	queued = new ArrayList<>();
    private final List<TorrentDownloader> 	errors = new ArrayList<>();

    public TorrentDownloaderManager() {
    }

    public static TorrentDownloaderManager getInstance() {
        if (man==null)
            man = new TorrentDownloaderManager();
        return man;
    }

    public void init(GlobalManager _gm, boolean _autostart ) {
        this.gm = _gm;
        this.autostart = _autostart;
    }

    public TorrentDownloader add(TorrentDownloader dl) {
        if (dl.getDownloadState()==TorrentDownloader.STATE_ERROR)
            this.errors.add(dl);
        else if (this.running.contains(dl) || this.queued.contains(dl)) {
            ((TorrentDownloaderImpl) dl).setDownloadState(TorrentDownloader.STATE_DUPLICATE);
            ((TorrentDownloaderImpl) dl).notifyListener();
            this.errors.add(dl);
        } else if (this.autostart) {
            dl.start();
        } else
            this.queued.add(dl);
        return dl;
    }

    public TorrentDownloader download(String url, String fileordir) {
        return add(TorrentDownloaderFactory.create(new Callback(), url, null, null, fileordir));
    }

    public TorrentDownloader download(String url) {
        return add(TorrentDownloaderFactory.create(new Callback(), url, null, null, null));
    }
    
    public TorrentDownloader downloadToLocation(String url, String save_path ) {
        return add(TorrentDownloaderFactory.create(new Callback( save_path ), url, null, null, null));
    }

	/**
	 * @param inf
	 */
	public void remove(TorrentDownloader inf) {
		if (this.running.contains(inf))
		    this.running.remove(inf);
		if (this.queued.contains(inf))
		    this.queued.remove(inf);
	}
	
	public class
	Callback
		implements TorrentDownloaderCallBackInterface
	{
		private String downloaddir;
		
		public
		Callback()
		{
	        try {
	            downloaddir = COConfigurationManager.getDirectoryParameter("Default save path");
	        } catch (Exception e) {
	            //this.error = e.getMessage();
	            downloaddir = null;
	        }
		}
		
		private
		Callback(
			String path )
		{
	        downloaddir = path;
		}
		
	    @Override
	    public void TorrentDownloaderEvent(int state, com.biglybt.core.torrentdownloader.TorrentDownloader inf) {
	        switch(state) {
	            case TorrentDownloader.STATE_START:
	                if (queued.contains(inf))
	                    queued.remove(inf);
	                if (!running.contains(inf))
	                    running.add(inf);
	                break;
	            case TorrentDownloader.STATE_FINISHED:
	                remove(inf);
	                if ((gm != null) && (downloaddir != null)) {
	                    gm.addDownloadManager(inf.getFile().getAbsolutePath(), downloaddir);
	                }
	                break;
	            case TorrentDownloader.STATE_ERROR:
	                remove(inf);
	                errors.add(inf);
	                break;
	        }
	    }
	}
}
