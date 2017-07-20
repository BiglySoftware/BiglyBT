/*
 * TorrentDownloaderInfo.java
 *
 * Created on 2. November 2003, 01:48
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

package com.biglybt.core.torrentdownloader;

/**
 *
 * @author  Tobias Minich
 */
public interface TorrentDownloader {
    public static final int STATE_NON_INIT = -1;
    public static final int STATE_INIT = 0;
    public static final int STATE_START = 1;
    public static final int STATE_DOWNLOADING = 2;
    public static final int STATE_FINISHED = 3;
    public static final int STATE_ERROR = 4;
    public static final int STATE_DUPLICATE = 5;
    public static final int STATE_CANCELLED = 6;

    //public void init(TorrentDownloaderCallBackInterface _iface, String _url, String _file);
    /**
     * Starts the download.
     */
    public void start();
    /**
     * Cancels the download.
     */
    public void cancel();
    /**
     * Changes the path and filename to download to.
     * You can give <code>null</code> for either to leave it as is.
     * (These are initialized to either the path/filename given via
     * <code>TorrentDownloaderFactory.download(Managed)</code> or to
     * the default torrent save directory (path) and the filename the
     * server proposes (file).
     * This function does nothing after the download has been started.
     *
     * @param path The path for download.
     * @param file The file name for download.
     */
    public void setDownloadPath(String path, String file);
    /**
     * Gets the state of the TorrentDownloader.
     */
    public int getDownloadState();
    /**
     * Returns the <code>File</code> the TorrentDownloader downloads to.
     */
    public java.io.File getFile();
    /**
     * Returns the amount downloaded in per cent. Gives -1 if total size is not available.
     */
    public int getPercentDone();
	/**
	 * Returns the amount downloaded in bytes.
	 */
	public int getTotalRead();
    /**
     * Returns the error string if one occured, "Ok" otherwise.
     */
    public String getError();

    public String getStatus();

    /**
     * Returns the URL downloaded from.
     */
    public String getURL();
		/**
		 * @return
		 */
		int getLastReadCount();
		/**
		 * @return
		 */
		byte[] getLastReadBytes();
		/**
		 * @return
		 */
		boolean getDeleteFileOnCancel();
		/**
		 * @param deleteFileOnCancel
		 */
		void setDeleteFileOnCancel(boolean deleteFileOnCancel);
		/**
		 * @return
		 *
		 * @since 4.0.0.5
		 */
		boolean isIgnoreReponseCode();
		/**
		 * @param ignoreReponseCode
		 *
		 * @since 4.0.0.5
		 */
		void setIgnoreReponseCode(boolean ignoreReponseCode);
}
