/*
 * Created on Apr 8, 2009
 * Created by Paul Gardner
 *
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


package com.biglybt.ui.swt.subscriptions;

import java.io.File;
import java.net.URL;
import java.util.Map;

import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.ISelectedVuzeFileContent;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentCreator;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.AETemporaryFileHandler;
import com.biglybt.core.util.Debug;

import com.biglybt.core.subs.Subscription;
import com.biglybt.core.vuzefile.VuzeFile;

public class
SubscriptionSelectedContent
	implements ISelectedVuzeFileContent
{
	private Subscription		subs;

		// if you add more fields here be sure to amend 'sameAs' logic below

	private TOTorrent			torrent;

	protected
	SubscriptionSelectedContent(
		Subscription		_subs )
	{
		subs	= _subs;
	}

	@Override
	public String
	getDisplayName()
	{
		return( MessageText.getString( "subscriptions.column.name" ) + ": " + subs.getName());
	}

	@Override
	public String
	getHash()
	{
		return( subs.getID());
	}

	@Override
	public VuzeFile
	getVuzeFile()
	{
		try{
			return( subs.getVuzeFile());

		}catch( Throwable e ){

			Debug.out(e);
		}

		return( null );
	}

	@Override
	public TOTorrent
	getTorrent()
	{
		synchronized( this ){

			if ( torrent == null ){

					// hack alert - we embed the vuze-file into a torrent to allow it to go through
					// the normal share route, then pick it out again when the recipient 'downloads' it

				try{

					VuzeFile vf = subs.getVuzeFile();

						// if not corrupt....

					if ( vf != null ){

						File f1 = AETemporaryFileHandler.createTempFile();

						File f = new File( f1.getParent(), "Update Vuze to access this share_" + f1.getName());

						f1.delete();

						try{

							vf.write( f );

							TOTorrentCreator cr = TOTorrentFactory.createFromFileOrDirWithComputedPieceLength( f, new URL( "dht://" ));

							TOTorrent temp = cr.create();

							Map	vuze_map 	= vf.exportToMap();
							Map	torrent_map = temp.serialiseToMap();

							torrent_map.putAll( vuze_map );

							torrent = TOTorrentFactory.deserialiseFromMap( torrent_map );

						}finally{

							f.delete();
						}
					}
				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}

		return( torrent );
	}

	@Override
	public void setHash(String hash) {
	}

	@Override
	public DownloadManager getDownloadManager() {
		return null;
	}

	@Override
	public int getFileIndex() {
		return 0;
	}

	@Override
	public void setDownloadManager(DownloadManager dm) {
	}

	@Override
	public void setTorrent(TOTorrent torrent) {
	}

	@Override
	public void setDisplayName(String displayName) {
	}

	@Override
	public DownloadUrlInfo getDownloadInfo() {
		return null;
	}

	@Override
	public boolean
	sameAs(
		ISelectedContent _other )
	{
		if ( _other == this ){

			return( true );
		}

		if ( _other instanceof SubscriptionSelectedContent ){

			SubscriptionSelectedContent other = (SubscriptionSelectedContent)_other;

			return( subs == other.subs );
		}

		return( false );
	}
}
