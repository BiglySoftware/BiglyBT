/*
 * Created on May 6, 2008
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

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.HashWrapper;

/**
 * Represents a piece of content (torrent) that is selected
 *
 * @author TuxPaper
 * @created May 6, 2008
 *
 */
public class SelectedContent implements ISelectedContent
{
	private String hash;

	private DownloadManager dm;
	private int				file_index = -1;
	private TOTorrent		torrent;

	private String displayName;

	private DownloadUrlInfo downloadInfo;

		// add more fields and you need to amend sameAs below

	/**
	 * @param dm
	 * @throws Exception
	 */
	public SelectedContent(DownloadManager dm){
		setDownloadManager(dm);
	}

	public SelectedContent(DownloadManager dm, int _file_index ){
		setDownloadManager(dm);
		file_index = _file_index;
	}

	/**
	 *
	 */
	public SelectedContent(String hash, String displayName) {
		this.hash = hash;
		this.displayName = displayName;
	}

	/**
	 * @deprecated - at least set a display-name for debug purposes
	 */
	
	public SelectedContent() {
	}

	public SelectedContent( String dn) {
		displayName = dn;
	}
	
	// @see ISelectedContent#getHash()
	@Override
	public String getHash() {
		return hash;
	}

	// @see ISelectedContent#setHash(java.lang.String)
	@Override
	public void setHash(String hash) {
		this.hash = hash;
	}

	// @see ISelectedContent#getDM()
	@Override
	public DownloadManager getDownloadManager() {
		if (dm == null && hash != null) {
			try {
  			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
  			return gm.getDownloadManager(new HashWrapper(Base32.decode(hash)));
			} catch (Exception ignore) {

			}
		}
		return dm;
	}

	// @see ISelectedContent#setDM(com.biglybt.core.download.DownloadManager)
	@Override
	public void setDownloadManager(DownloadManager _dm) {
		dm = _dm;
		if ( dm != null ){
			setTorrent( dm.getTorrent());
			setDisplayName(dm.getDisplayName());
		}
	}

	@Override
	public int getFileIndex() {
		return file_index;
	}

	@Override
	public TOTorrent getTorrent() {
		return( torrent );
	}

	@Override
	public void setTorrent(TOTorrent _torrent) {
		torrent = _torrent;

		if ( torrent != null ){
			try {
				hash = torrent.getHashWrapper().toBase32String();
			} catch (Exception e) {
				hash = null;
			}
		}
	}

	// @see ISelectedContent#getDisplayName()
	@Override
	public String 
	getDisplayName() 
	{
		String 	str = displayName;
		
		if ( displayName == null || displayName.isEmpty()){
			
			if ( dm != null ){
			
				str = dm.getDisplayName();
				
			}else if ( torrent != null ){
				
				str = new String( torrent.getName());
				
			}else if ( downloadInfo != null ){
				
				str = downloadInfo.getDownloadURL();
			}
			
			if ( file_index >= 0 ){
				
				str += " (file=" + file_index + ")";
			}
		}
		
		return( str );
	}

	// @see ISelectedContent#setDisplayName(java.lang.String)
	@Override
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	// @see ISelectedContent#getDownloadInfo()
	@Override
	public DownloadUrlInfo getDownloadInfo() {
		return downloadInfo;
	}

	public void setDownloadInfo(DownloadUrlInfo info) {
		this.downloadInfo = info;
	}

	@Override
	public boolean
	sameAs(
		ISelectedContent _other )
	{
		if ( _other instanceof SelectedContent ){

			SelectedContent other = (SelectedContent)_other;

			if ( hash != other.hash ){

				if ( 	hash == null ||
						other.hash == null ||
						!hash.equals( other.hash )){

					return( false );
				}
			}

			if ( 	dm != other.dm ||
					torrent	!= other.torrent ||
					file_index != other.file_index ){

				return( false );
			}

			if ( displayName != other.displayName ){

				if ( 	displayName == null ||
						other.displayName == null ||
						!displayName.equals( other.displayName )){

					return( false );
				}
			}

			if ( downloadInfo != other.downloadInfo ){

				if ( 	downloadInfo == null ||
						other.downloadInfo == null ||
						!downloadInfo.sameAs( other.downloadInfo )){

					return( false );
				}
			}

			return( true );
		}

		return( false );
	}
}
