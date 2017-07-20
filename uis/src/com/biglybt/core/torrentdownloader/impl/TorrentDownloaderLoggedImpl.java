/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.core.torrentdownloader.impl;

/**
 *
 * @author  Tobias Minich
 */
public class TorrentDownloaderLoggedImpl extends TorrentDownloaderImpl {

    @Override
    public void notifyListener() {
      super.notifyListener();
      switch(this.getDownloadState()) {
          case STATE_INIT:
            org.apache.log4j.Logger.getLogger("torrentdownloader").info("Download of '"+this.getFile().getName()+"' queued.");
            break;
          case STATE_START:
            org.apache.log4j.Logger.getLogger("torrentdownloader").info("Download of '"+this.getFile().getName()+"' started.");
            break;
          case STATE_FINISHED:
            org.apache.log4j.Logger.getLogger("torrentdownloader").info("Download of '"+this.getFile().getName()+"' finished.");
            break;
          case STATE_ERROR:
            org.apache.log4j.Logger.getLogger("torrentdownloader").error(this.getError());
            break;
          case STATE_DUPLICATE:
            org.apache.log4j.Logger.getLogger("torrentdownloader").error("Download of '"+this.getFile().getName()+"' cancelled. File is already queued or downloading.");
      }
  }

}
