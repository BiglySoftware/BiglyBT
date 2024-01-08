/*
 * Created on Apr 17, 2007
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


package com.biglybt.pifimpl.local;

import java.io.File;
import java.io.IOException;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.tracker.server.TRTrackerServerTorrent;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.disk.DiskManager;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl;
import com.biglybt.pifimpl.local.disk.DiskManagerImpl;
import com.biglybt.pifimpl.local.download.DownloadImpl;
import com.biglybt.pifimpl.local.download.DownloadManagerImpl;
import com.biglybt.pifimpl.local.network.ConnectionImpl;
import com.biglybt.pifimpl.local.peers.PeerImpl;
import com.biglybt.pifimpl.local.peers.PeerManagerImpl;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;
import com.biglybt.pifimpl.local.tracker.TrackerTorrentImpl;

public class
PluginCoreUtils
{
	public static Torrent
	wrap(
		TOTorrent	t )
	{
		return( new TorrentImpl( t ));
	}

	public static TOTorrent
	unwrap(
		Torrent		t )
	{
		return(((TorrentImpl)t).getTorrent());
	}

	public static DiskManager
	wrap(
		com.biglybt.core.disk.DiskManager	dm )
	{
		return( new DiskManagerImpl( dm ));
	}

	public static com.biglybt.core.disk.DiskManager
	unwrap(
		DiskManager		dm )
	{
		return(((DiskManagerImpl)dm).getDiskmanager());
	}

	/**
	 * May return NULL if download not found (e.g. has been removed)
	 * @param dm
	 * @return may be null
	 */

	public static Download
	wrap(
		com.biglybt.core.download.DownloadManager	dm )
	{
		try{
			return( DownloadManagerImpl.getDownloadStatic( dm ));

		}catch( Throwable e ){

			// Debug.printStackTrace( e );

			return( null );
		}
	}

	public static NetworkConnection
	unwrap(
		Connection		connection )
	{
		if ( connection instanceof ConnectionImpl ){

			return(((ConnectionImpl)connection).getCoreConnection());
		}

		return( null );
	}

	public static Connection
	wrap(
		NetworkConnection			connection )
	{
		return( new ConnectionImpl( connection, connection.isIncoming()));
	}

	public static com.biglybt.pif.disk.DiskManagerFileInfo
	wrap(
		DiskManagerFileInfo		info )

		throws DownloadException
	{
		if ( info == null ){

			return( null );
		}

		return( new DiskManagerFileInfoImpl( DownloadManagerImpl.getDownloadStatic( info.getDownloadManager()), info ));
	}

	public static DiskManagerFileInfo
	unwrap(
		final com.biglybt.pif.disk.DiskManagerFileInfo		info )

		throws DownloadException
	{
		if ( info instanceof DiskManagerFileInfoImpl ){

			return(((DiskManagerFileInfoImpl)info).getCore());
		}

		if ( info == null ){

			return( null );
		}

		try{
			Download dl = info.getDownload();

			if ( dl != null ){

				com.biglybt.core.download.DownloadManager dm = unwrap( dl );

				return( dm.getDiskManagerFileInfo()[ info.getIndex()]);
			}
		}catch( Throwable e ){
		}

			// no underlying download, lash something up

		return(
			new DiskManagerFileInfo()
			{
				@Override
				public void
				setPriority(
					int b )
				{
					info.setNumericPriority(b);
				}

				@Override
				public void
				setSkipped(
					boolean b)
				{
					info.setSkipped(b);
				}

				@Override
				public Boolean 
				isSkipping()
				{
					return( info.isSkipping());
				}
				
				@Override
				public boolean
				setLink(
					File	link_destination,
					boolean	dont_delete )
				{
					info.setLink(link_destination,dont_delete);

					return( true );
				}

				@Override
				public boolean
				setLinkAtomic(
					File 	link_destination,
					boolean	dont_delete )
				{
					info.setLink(link_destination,dont_delete);

					return( true );
				}

				@Override
				public String getLastError(){
					return( null );
				}
				
				@Override
				public boolean
				setLinkAtomic(
					File 						link_destination,
					boolean						dont_delete,
					FileUtil.ProgressListener	pl )
				{
					info.setLink( link_destination, dont_delete );

					return( true );
				}
				
				@Override
				public File
				getLink()
				{
					return( info.getLink());
				}

				@Override
				public boolean
				setStorageType(
					int 	type,
					boolean	force )
				{
					return( false );
				}

				@Override
				public int
				getStorageType()
				{
					return( ST_LINEAR );
				}

				@Override
				public int
				getAccessMode()
				{
					return( info.getAccessMode());
				}

				@Override
				public long
				getDownloaded()
				{
					return( info.getDownloaded());
				}

				@Override
				public long getLastModified()
				{
					return( info.getLastModified());
				}
				
				@Override
				public String
				getExtension()
				{
					return( "" );
				}

				@Override
				public int
				getFirstPieceNumber()
				{
					return( info.getFirstPieceNumber());
				}

				@Override
				public int
				getLastPieceNumber()
				{
					return((int)(( info.getLength() + info.getPieceSize()-1 )/info.getPieceSize()));
				}

				@Override
				public long
				getLength()
				{
					return( info.getLength());
				}

				@Override
				public int
				getNbPieces()
				{
					return( info.getNumPieces());
				}
				
				@Override
				public boolean exists(){
					return( true );
				}

				@Override
				public int
				getPriority()
				{
					return( info.getNumericPriority());
				}

				@Override
				public boolean
				isSkipped()
				{
					return( info.isSkipped());
				}

				@Override
				public int
				getIndex()
				{
					return( info.getIndex());
				}

				@Override
				public DownloadManager
				getDownloadManager()
				{
					return( null );
				}

				@Override
				public com.biglybt.core.disk.DiskManager
				getDiskManager()
				{
					return( null );
				}

				@Override
				public File
				getFile(
					boolean follow_link )
				{
					if ( follow_link ){

						return( info.getLink());

					}else{

						return( info.getFile());
					}
				}

				@Override
				public TOTorrentFile
				getTorrentFile()
				{
					return( null );
				}

				@Override
				public DirectByteBuffer
				read(
					long	offset,
					int		length )

					throws IOException
				{
					throw( new IOException( "unsupported" ));
				}

				@Override
				public void
				flushCache()

					throws	Exception
				{
				}

				@Override
				public int
				getReadBytesPerSecond()
				{
					return( 0 );
				}

				@Override
				public int
				getWriteBytesPerSecond()
				{
					return( 0 );
				}

				@Override
				public long
				getETA()
				{
					return( -1 );
				}

				@Override
				public void recheck()
				{
				}
				
				@Override
				public void
				close()
				{
				}

				@Override
				public void
				addListener(
					DiskManagerFileInfoListener	listener )
				{
				}

				@Override
				public void
				removeListener(
					DiskManagerFileInfoListener	listener )
				{
				}
			});
	}

	public static Object
	convert(
		Object datasource,
		boolean toCore)
	{
		if (datasource instanceof Object[]) {
			Object[] array = (Object[]) datasource;
			if (array.length == 0) {
				// 3DView plugin assumes if there is an array, it has at least one entry
				// Hack so we return null when array is empty
				return null;
			}
			Object[] newArray = new Object[array.length];
			for (int i = 0; i < array.length; i++) {
				Object o = array[i];
				newArray[i] = convert(o, toCore);
			}
			return newArray;
		}

		try {
			if (toCore) {
				if (datasource instanceof com.biglybt.core.download.DownloadManager) {
					return datasource;
				}
				if (datasource instanceof DownloadImpl) {
					return ((DownloadImpl) datasource).getDownload();
				}

				if (datasource instanceof com.biglybt.core.disk.DiskManager) {
					return datasource;
				}
				if (datasource instanceof DiskManagerImpl) {
					return ((DiskManagerImpl) datasource).getDiskmanager();
				}

				if (datasource instanceof PEPeerManager) {
					return datasource;
				}
				if (datasource instanceof PeerManagerImpl) {
					return ((PeerManagerImpl) datasource).getDelegate();
				}

				if (datasource instanceof PEPeer) {
					return datasource;
				}
				if (datasource instanceof PeerImpl) {
					return ((PeerImpl)datasource).getPEPeer();
				}

				if (datasource instanceof com.biglybt.core.disk.DiskManagerFileInfo) {
					return datasource;
				}
				if (datasource instanceof com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl) {
					return ((com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl) datasource).getCore();
				}

				if (datasource instanceof TRHostTorrent) {
					return datasource;
				}
				if (datasource instanceof TrackerTorrentImpl) {
					((TrackerTorrentImpl) datasource).getHostTorrent();
				}
			} else { // to PI
				if (datasource instanceof com.biglybt.core.download.DownloadManager) {
					return wrap((com.biglybt.core.download.DownloadManager) datasource);
				}
				if (datasource instanceof DownloadImpl) {
					return datasource;
				}

				if (datasource instanceof com.biglybt.core.disk.DiskManager) {
					return wrap((com.biglybt.core.disk.DiskManager) datasource);
				}
				if (datasource instanceof DiskManagerImpl) {
					return datasource;
				}

				if (datasource instanceof PEPeerManager) {
					return wrap((PEPeerManager) datasource);
				}
				if (datasource instanceof PeerManagerImpl) {
					return datasource;
				}

				if (datasource instanceof PEPeer) {
					return PeerManagerImpl.getPeerForPEPeer((PEPeer) datasource);
				}
				if (datasource instanceof Peer) {
					return datasource;
				}

				if (datasource instanceof com.biglybt.core.disk.DiskManagerFileInfo) {
					DiskManagerFileInfo fileInfo = (com.biglybt.core.disk.DiskManagerFileInfo) datasource;
					if (fileInfo != null) {
						try {
							DownloadManager dm = fileInfo.getDownloadManager();
							return new com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl(
									dm==null?null:DownloadManagerImpl.getDownloadStatic(dm),
									fileInfo);
						} catch (DownloadException e) { /* Ignore */
						}
					}
				}
				if (datasource instanceof com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl) {
					return datasource;
				}

				if (datasource instanceof TRHostTorrent) {
					TRHostTorrent item = (TRHostTorrent) datasource;
					return new TrackerTorrentImpl(item);
				}
				if (datasource instanceof TrackerTorrentImpl) {
					return datasource;
				}
			}
		} catch (Throwable t) {
			Debug.out(t);
		}

		return datasource;
	}

	public static com.biglybt.core.download.DownloadManager
	unwrapIfPossible(
		Download		dm )
	{
			// might be a LWSDownload

		if ( dm instanceof DownloadImpl ){

			return(((DownloadImpl)dm).getDownload());

		}else{

			return( null );
		}
	}

	public static com.biglybt.core.download.DownloadManager
	unwrap(
		Download		dm )
	{
		if ( dm instanceof DownloadImpl ){

			return(((DownloadImpl)dm).getDownload());

		}else{

			Debug.out( "Can't unwrap " + dm );

			return( null );
		}
	}

	public static PeerManager
	wrap(
		PEPeerManager	pm )
	{
		return( PeerManagerImpl.getPeerManager( pm ));
	}

	public static PEPeerManager
	unwrap(
		PeerManager		pm )
	{
		return(((PeerManagerImpl)pm).getDelegate());
	}

	public static TRTrackerServerTorrent
	unwrap(
		TrackerTorrent		torrent )
	{
		return( ((TrackerTorrentImpl)torrent).getHostTorrent().getTrackerTorrent());
	}

	public static Peer
	wrap(
		PEPeer		peer )
	{
		return( PeerManagerImpl.getPeerForPEPeer( peer ));
	}
		
	public static PEPeer
	unwrap(
		Peer		peer )
	{
		if ( peer instanceof PeerImpl ){
			return(((PeerImpl)peer).getDelegate());
		}else{
			return( null );
		}
	}

	public static boolean
	isInitialisationComplete()
	{
		return( PluginInitializer.getDefaultInterface().getPluginState().isInitialisationComplete());
	}
}
