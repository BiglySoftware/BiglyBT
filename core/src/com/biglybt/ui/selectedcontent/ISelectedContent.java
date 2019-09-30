/*
 * Created on Jun 9, 2008
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.selectedcontent;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.torrent.TOTorrent;

/**
 * @author TuxPaper
 * @created Jun 9, 2008
 *
 */
public interface ISelectedContent
{

	public String getHash();

	public void setHash(String hash);

	public DownloadManager getDownloadManager();

	public int getFileIndex();

	public void setDownloadManager(DownloadManager dm);

	public TOTorrent getTorrent();

	public void setTorrent( TOTorrent torrent );

	public String getDisplayName();

	public void setDisplayName(String displayName);

	/**
	 * @since 3.1.1.1
	 */
	public DownloadUrlInfo getDownloadInfo();

	public boolean
	sameAs( ISelectedContent other );
	
	public default String
	getString()
	{
		String str = "";
		
		String hash = getHash();
		
		if ( hash != null ){
			str += (str.isEmpty()?"":", ") + "hash=" + hash;
		}
		
		String dn = getDisplayName();
		
		if ( dn != null ){
			str += (str.isEmpty()?"":", ") + "name=" + dn;
		}
		
		DownloadManager dm = getDownloadManager();
		
		if ( dm != null ){
			String n = dm.getDisplayName();
			if ( dn == null || !dn.equals( n )){
				str += (str.isEmpty()?"":", ") + "dm=" + dm;
			}
		}
		
		TOTorrent to = getTorrent();
		
		if ( to != null && dm == null ){
			str += (str.isEmpty()?"":", ") + "to=" + new String( to.getName());
		}
			
		int idx = getFileIndex();
		
		if ( idx >= 0 ){
			str += (str.isEmpty()?"":", ") + "index=" + idx;
		}
		
		DownloadUrlInfo url = getDownloadInfo();
		
		if ( url != null ){
			str += (str.isEmpty()?"":", ") + "url=" + url.getDownloadURL();
		}
		
		return( str );
	}
}