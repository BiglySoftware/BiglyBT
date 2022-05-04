/*
 * Created on Jun 1, 2008
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

package com.biglybt.util;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.activities.ActivitiesEntry;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.*;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

/**
 * @author TuxPaper
 * @created Jun 1, 2008
 *
 */
public class PlayUtils
{
	public static final boolean COMPLETE_PLAY_ONLY = true;

		/**
		 * Access to this static is deprecated - use get/setPlayableFileExtensions. For legacy EMP we need
		 * to keep it public for the moment...
		 */

	public static final String playableFileExtensions 	= ".avi .flv .flc .mp4 .divx .h264 .mkv .mov .mp2 .m4v .mp3 .aac, .mts, .m2ts";

	private static volatile String actualPlayableFileExtensions = playableFileExtensions;


	private static Boolean hasQuickTime;

	//private static Method methodIsExternalPlayerInstalled;

	public static boolean prepareForPlay(DownloadManager dm) {
		EnhancedDownloadManager edm = DownloadManagerEnhancer.getSingleton().getEnhancedDownload(
				dm);

		if (edm != null) {

			edm.setProgressiveMode(true);

			return (true);
		}

		return (false);
	}

	public static boolean canUseEMP(DiskManagerFileInfo file ){
		return( isExternallyPlayable( file ));
	}

	public static boolean canUseEMP(TOTorrent torrent, int file_index) {

		return( canUseEMP( torrent, file_index, COMPLETE_PLAY_ONLY ));
	}

	public static boolean canUseEMP(TOTorrent torrent, int file_index, boolean complete_only ) {
		if (torrent == null) {
			return false;
		}

		return canPlayViaExternalEMP(torrent, file_index, complete_only);

	}

	private static boolean canPlay(DownloadManager dm, int file_index) {
		if (dm == null) {
			return false;
		}
		TOTorrent torrent = dm.getTorrent();
		return canUseEMP(torrent,file_index);
	}

	private static boolean canPlay(TOTorrent torrent, int file_index) {
		if (torrent == null) {
			return false;
		}
		return canUseEMP(torrent,file_index);
	}


	private static ThreadLocal<int[]>		tls_non_block_indicator	=
		new ThreadLocal<int[]>()
		{
			@Override
			public int[]
			initialValue()
			{
				return( new int[1] );
			}
		};

	public static boolean
	canPlayDS(
		Object 		ds,
		int 		file_index,
		boolean		block_for_accuracy )
	{
		/* Suport linux from 5711 + azemp 4.0.0

		if ( !( Constants.isWindows || Constants.isOSX )){

			return( false );
		}
		*/

		if (ds == null) {
			return false;
		}

		try{
			if ( !block_for_accuracy ){

				tls_non_block_indicator.get()[0]++;
			}

			if (ds instanceof com.biglybt.core.disk.DiskManagerFileInfo) {
				com.biglybt.core.disk.DiskManagerFileInfo fi = (com.biglybt.core.disk.DiskManagerFileInfo) ds;
				return canPlayDS(fi.getDownloadManager(), fi.getIndex(), block_for_accuracy);
			}

			DownloadManager dm = DataSourceUtils.getDM(ds);
			if (dm != null) {
				return canPlay(dm, file_index);
			}
			TOTorrent torrent = DataSourceUtils.getTorrent(ds);
			if (torrent != null) {
				return canPlay(torrent, file_index);
			}
			if (ds instanceof ActivitiesEntry) {
				return ((ActivitiesEntry) ds).isPlayable( block_for_accuracy );
			}

			return false;

		}finally{

			if ( !block_for_accuracy ){

				tls_non_block_indicator.get()[0]--;
			}
		}
	}

		// stream stuff

	public static boolean
	isStreamPermitted()
	{
		return( true );
	}

	private static boolean
	canStream(
		DownloadManager 	dm,
		int 				file_index )
	{
		if ( dm == null ){

			return( false );
		}

		com.biglybt.core.disk.DiskManagerFileInfo	file;

		if ( file_index == -1 ){

			file = dm.getDownloadState().getPrimaryFile();
			if (file == null) {
				com.biglybt.core.disk.DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
				if (files.length == 0) {
					return false;
				}
				file = files[0];
			}

			file_index = file.getIndex();

		}else{

			file = dm.getDiskManagerFileInfoSet().getFiles()[ file_index ];
		}

		if ( file.getDownloaded() == file.getLength()){

			return( false );
		}

		if ( !StreamManager.getSingleton().isStreamingUsable()){

			return( false );
		}

		TOTorrent torrent = dm.getTorrent();

		return( canUseEMP( torrent, file_index, false ));
	}

	public static boolean
	canStreamDS(
		Object 		ds,
		int 		file_index,
		boolean		block_for_accuracy )
	{
		/* Suport linux from 5711 + azemp 4.0.0

		if ( !( Constants.isWindows || Constants.isOSX )){

			return( false );
		}
		*/

		if ( ds == null ){

			return( false );
		}

		try {
			if (!block_for_accuracy) {

				tls_non_block_indicator.get()[0]++;
			}

			if (ds instanceof com.biglybt.core.disk.DiskManagerFileInfo) {
				com.biglybt.core.disk.DiskManagerFileInfo fi = (com.biglybt.core.disk.DiskManagerFileInfo) ds;
				return canStreamDS(fi.getDownloadManager(), fi.getIndex(), block_for_accuracy);
			}

			DownloadManager dm = DataSourceUtils.getDM(ds);

			return dm != null && (canStream(dm, file_index));

		}finally{

			if ( !block_for_accuracy ){

				tls_non_block_indicator.get()[0]--;
			}
		}
	}

	public static URL getMediaServerContentURL(DiskManagerFileInfo file) {

		//TorrentListViewsUtils.debugDCAD("enter - getMediaServerContentURL");

		PluginManager pm = CoreFactory.getSingleton().getPluginManager();
		PluginInterface pi = pm.getPluginInterfaceByID("azupnpav", false);

		if (pi == null) {
			Logger.log(new LogEvent(LogIDs.UI3, "Media server plugin not found"));
			return null;
		}

		if (!pi.getPluginState().isOperational()) {
			Logger.log(new LogEvent(LogIDs.UI3, "Media server plugin not operational"));
			return null;
		}

		try {
			if ( hasQuickTime == null ){

				UIFunctions uif = UIFunctionsManager.getUIFunctions();

				if ( uif != null ){

					hasQuickTime = uif.isProgramInstalled( ".qtl", "Quicktime" );

					try{
						pi.getIPC().invoke("setQuickTimeAvailable", new Object[] { hasQuickTime	});

					}catch( Throwable e ){

						Logger.log(new LogEvent(LogIDs.UI3, LogEvent.LT_WARNING,
								"IPC to media server plugin failed", e));
					}
				}
			}

			boolean	use_peek = tls_non_block_indicator.get()[0] > 0;

			Object url;

			if ( use_peek && pi.getIPC().canInvoke( "peekContentURL", new Object[] { file })){

				url = pi.getIPC().invoke("peekContentURL", new Object[] { file });

			}else{

				url = pi.getIPC().invoke("getContentURL", new Object[] { file });
			}

			if (url instanceof String) {
				return new URL( (String) url);
			}
		} catch (Throwable e) {
			Logger.log(new LogEvent(LogIDs.UI3, LogEvent.LT_WARNING,
					"IPC to media server plugin failed", e));
		}

		return null;
	}

	/*
	private static final boolean isExternalEMPInstalled() {
		if(!loadEmpPluginClass()) {
			return false;
		}

		if (methodIsExternalPlayerInstalled == null) {
			return false;
		}

		try {

			Object retObj = methodIsExternalPlayerInstalled.invoke(null, new Object[] {});

			if (retObj instanceof Boolean) {
				return ((Boolean) retObj).booleanValue();
			}
		} catch (Throwable e) {
			e.printStackTrace();
			if (e.getMessage() == null
					|| !e.getMessage().toLowerCase().endsWith("only")) {
				Debug.out(e);
			}
		}

		return false;

	}*/


	private static AtomicInteger dm_uid = new AtomicInteger();

	private static final Map<String,Object[]>	ext_play_cache =
		new LinkedHashMap<String,Object[]>(100,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<String,Object[]> eldest)
			{
				return size() > 100;
			}
		};

	public static boolean isExternallyPlayable(Download d, int file_index, boolean complete_only ) {

		if ( d == null ){

			return( false );
		}

		boolean use_cache = d.getState() != Download.ST_DOWNLOADING;

		String 	cache_key 	= null;
		long	now 		= 0;

		if ( use_cache ){

			Integer uid = (Integer)d.getUserData( PlayUtils.class );

			if ( uid == null ){

				uid = dm_uid.getAndIncrement();

				d.setUserData( PlayUtils.class, uid );
			}

			cache_key = uid+"/"+file_index+"/"+complete_only;

			Object[] cached;

			synchronized( ext_play_cache ){

				cached = ext_play_cache.get( cache_key );
			}

			now = SystemTime.getMonotonousTime();

			if ( cached != null ){

				if ( now - (Long)cached[0] < 60*1000 ){

					return((Boolean)cached[1]);
				}
			}
		}

		boolean result = isExternallyPlayableSupport(d, file_index, complete_only);

		if ( use_cache ){

			synchronized( ext_play_cache ){

				ext_play_cache.put( cache_key, new Object[]{ now, result });
			}
		}

		return( result );
	}

	private static boolean isExternallyPlayableSupport(Download d, int file_index, boolean complete_only ) {

		int primary_file_index = -1;

		if ( file_index == -1 ){


			DownloadManager dm = PluginCoreUtils.unwrap(d);

			if ( dm == null ) {

				return( false );
			}

			DiskManagerFileInfo file = null;
			try {
				file = PluginCoreUtils.wrap(dm.getDownloadState().getPrimaryFile());
			} catch (DownloadException e) {
				return false;
			}

			if ( file == null ){

				return( false );
			}

			if ( file.getDownloaded() != file.getLength()) {

				if ( complete_only || getMediaServerContentURL( file ) == null ){

					return( false );
				}
			}

			primary_file_index = file.getIndex();

		}else{

			DiskManagerFileInfo file = d.getDiskManagerFileInfo( file_index );

			if ( file.getDownloaded() != file.getLength()) {

				if ( complete_only || getMediaServerContentURL( file ) == null ){

					return( false );
				}
			}

			primary_file_index = file_index;
		}

		if ( primary_file_index == -1 ){

			return false;
		}

		return( isExternallyPlayable( d.getDiskManagerFileInfo()[primary_file_index] ));
	}

	public static int[]
	getExternallyPlayableFileIndexes(
			Download d,
			boolean complete_only)
	{
		int[] playableIndexes = {};
		DiskManagerFileInfo[] fileInfos = d.getDiskManagerFileInfo();
		for (int i = 0; i < fileInfos.length; i++) {
			DiskManagerFileInfo fileInfo = fileInfos[i];
			if (complete_only && fileInfo.getLength() != fileInfo.getDownloaded()) {
				continue;
			}
			if (isExternallyPlayable(fileInfo)) {
				int[] newPlayableIndexes = new int[playableIndexes.length + 1];
				System.arraycopy(playableIndexes, 0, newPlayableIndexes, 0,
						playableIndexes.length);
				newPlayableIndexes[playableIndexes.length] = i;
				playableIndexes = newPlayableIndexes;
			}
		}
		return playableIndexes;
	}

	public static com.biglybt.core.disk.DiskManagerFileInfo
	getBestPlayableFile(
		DownloadManager		download )
	{
		com.biglybt.core.disk.DiskManagerFileInfo[] files = download.getDiskManagerFileInfoSet().getFiles();
		
		String exts = getPlayableFileExtensions();
		
		long largest = -1;
		com.biglybt.core.disk.DiskManagerFileInfo best = null;
		
		for ( com.biglybt.core.disk.DiskManagerFileInfo fileInfo: files ){
			
			if ( fileInfo.isSkipped()){
				
				continue;
			}
			
			File file = fileInfo.getFile( true );
			
			String name = file.getName();
			
			int extIndex = name.lastIndexOf(".");

			if ( extIndex > -1 ){

				String ext = name.substring(extIndex);

				if ( ext != null ){

					ext = ext.toLowerCase();

					if ( exts.contains(ext)){

						long size = file.length();
						
						if ( size > largest ){
							
							largest = size;
							
							best = fileInfo;
						}
					}
				}
			}
		}
		
		return( best );
	}
	
	private static boolean
	isExternallyPlayable(
		DiskManagerFileInfo	file )
	{
		String	name = file.getFile( true ).getName();

		try{
			Download dl = file.getDownload();

			if ( dl != null ){

				String is = PluginCoreUtils.unwrap( dl ).getDownloadState().getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

				if ( is != null && name.endsWith( is )){

					name = name.substring( 0, name.length() - is.length());
				}
			}
		}catch( Throwable e ){
		}

		int extIndex = name.lastIndexOf(".");

		if ( extIndex > -1 ){

			String ext = name.substring(extIndex);

			if ( ext == null ){

				return false;
			}

			ext = ext.toLowerCase();

			if (getPlayableFileExtensions().contains(ext)){

				return true;
			}
		}

		return false;
	}

	public static boolean isExternallyPlayable(TOTorrent torrent, int file_index, boolean complete_only ) {
		if (torrent == null) {
			return false;
		}
		try {
			Download download = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getDownloadManager().getDownload(torrent.getHash());
			if (download != null) {
				return isExternallyPlayable(download, file_index, complete_only);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	private static boolean canPlayViaExternalEMP(TOTorrent torrent, int file_index, boolean complete_only ) {
		if (torrent == null) {
			return false;
		}

		return isExternallyPlayable(torrent, file_index, complete_only );
	}

	public static String
	getPlayableFileExtensions()
	{
		return( actualPlayableFileExtensions );
	}

		/**
		 * This method available for player plugins to extend playable set if needed
		 * @param str
		 */

	public static void
	setPlayableFileExtensions(
		String	str )
	{
		actualPlayableFileExtensions = str;
	}


	public static boolean
	isEMPAvailable()
	{
		PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "azemp");

		return !(pi == null || pi.getPluginState().isDisabled());
	}


	public static boolean
	playURL(
		URL url, String name )
	{
		try{
			PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "azemp");

			if ( pi == null || pi.getPluginState().isDisabled()){

				return( false );
			}

			Class<?> ewp_class = pi.getPlugin().getClass().getClassLoader().loadClass( "com.azureus.plugins.azemp.ui.swt.emp.EmbeddedPlayerWindowSWT" );

			if ( ewp_class != null ){

				Method ow = ewp_class.getMethod( "openWindow", URL.class, String.class );

				if ( ow != null ){

					ow.invoke( null, url, name );

					return( true );
				}
			}

			return( false );

		}catch( Throwable e ){

			Debug.out( e);

			return( false );
		}
	}
}
