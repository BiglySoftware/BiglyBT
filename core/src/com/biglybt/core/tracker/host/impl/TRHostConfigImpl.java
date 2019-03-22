/*
 * File    : TRHostConfigImpl.java
 * Created : 06-Nov-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.tracker.host.impl;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.tracker.host.TRHostTorrentFinder;
import com.biglybt.core.util.*;

public class
TRHostConfigImpl
{
	public static final String	LOG_FILE_NAME				= "tracker.log";
	public static final long	BACKUP_RETENTION_PERIOD		= 7*24*60*60*1000L;

	private final TRHostImpl	host;

	private final AEMonitor 	save_lock_mon 	= new AEMonitor( "TRHostConfig:SL" );

	private final String		log_dir;

	private volatile boolean		loading	= false;
	private volatile boolean		save_outstanding	= false;

	private Map			saved_stats				= new HashMap();
	private final List		saved_stats_to_delete	= new ArrayList();

	private boolean		config_exists = true;

	private final AEMonitor this_mon 	= new AEMonitor( "TRHostConfig" );

	protected
	TRHostConfigImpl(
		TRHostImpl	_host )
	{
		host	= _host;

		log_dir = SystemProperties.getUserPath();
	}

	protected void
	loadConfig(
		TRHostTorrentFinder		finder )
	{
	   	try{
	   		this_mon.enter();

	   		loading	= true;

	   		Map	map = FileUtil.readResilientConfigFile("tracker.config");

			List torrents = (List) map.get("torrents");

			if (torrents == null){

				return;
		 	}

		 	Iterator  iter = torrents.iterator();

		 	while (iter.hasNext()) {

		   		Map t_map = (Map) iter.next();

		   		Long	persistent_l = (Long)t_map.get("persistent");

		   		boolean	persistent =  persistent_l==null || persistent_l.longValue() == 1;

		   		Long	passive_l = (Long)t_map.get("passive");

		   		boolean	passive =  passive_l!=null && passive_l.longValue() == 1;

		   		Long	dateadded_l = (Long)t_map.get("dateadded");

		   		long	date_added = dateadded_l==null?SystemTime.getCurrentTime():dateadded_l.longValue();

				byte[]	hash = (byte[])t_map.get("hash");

		   		if ( persistent ){

				 	int	state = ((Long)t_map.get("status")).intValue();

				 	if ( state == TRHostTorrent.TS_FAILED ){

				 		state = TRHostTorrent.TS_STOPPED;
				 	}

				 	TOTorrent	torrent = finder.lookupTorrent( hash );

					if ( torrent == null && passive ){

						byte[]	file_b = (byte[])t_map.get( "torrent_file" );

						if ( file_b != null ){

							try{
								File	file = new File( new String( file_b, Constants.BYTE_ENCODING_CHARSET ));

								torrent = TorrentUtils.readFromFile( file, true, true );

							}catch( Throwable e ){
								// torrent might have been deleted, don't barf out errors
								// Debug.printStackTrace( e );
							}
						}
					}

				 	if ( torrent != null ){

				 		TRHostTorrent	ht = host.addTorrent( torrent, state, true, passive, date_added );

				 		if ( ht instanceof TRHostTorrentHostImpl ){

				 			TRHostTorrentHostImpl	hth = (TRHostTorrentHostImpl)ht;

				 			recoverStats( hth, t_map );
				 		}

				 	}else{

						if ( COConfigurationManager.getBooleanParameter( "Tracker Public Enable")){

				 			host.addExternalTorrent( hash, state, date_added );
						}
				 	}
		   		}else{

		   				// store stats for later

		   			saved_stats.put( new HashWrapper( hash ), t_map );
		   		}
		   	}

	   	}catch (Exception e) {

	   		Debug.printStackTrace( e );

	   	}finally{

	   		loading	= false;

	   		this_mon.exit();
	   	}
	}

	protected void
	recoverStats(
		TRHostTorrentHostImpl	host_torrent )
	{
		try{
			HashWrapper	hash = host_torrent.getTorrent().getHashWrapper();

			Map	t_map = (Map)saved_stats.get( hash );

			if ( t_map != null ){

				recoverStats( host_torrent, t_map );

					// can't delete here due to sync problems - tag for later

				synchronized( saved_stats_to_delete ){

					saved_stats_to_delete.add( hash );
				}
			}

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	private void
	recoverStats(
		TRHostTorrentHostImpl	host_torrent,
		Map						t_map )
	{
	 	long	completed	= 0;
	 	long	announces	= 0;
	 	long	scrapes		= 0;
	 	long	total_up	= 0;
	 	long	total_down	= 0;
	 	long	bytes_in	= 0;
	 	long	bytes_out	= 0;

   		Long	dateadded_l = (Long)t_map.get("dateadded");

   		long	date_added = dateadded_l==null?SystemTime.getCurrentTime():dateadded_l.longValue();

	 	Map	s_map	= (Map)t_map.get( "stats" );

	 	if ( s_map != null ){

	 		completed 	= ((Long)s_map.get( "completed")).longValue();
	 		announces	= ((Long)s_map.get( "announces")).longValue();
	 		total_up	= ((Long)s_map.get( "uploaded")).longValue();
	 		total_down	= ((Long)s_map.get( "downloaded")).longValue();

	 		Long	scrapes_l = (Long)s_map.get( "scrapes" );
	 		if ( scrapes_l != null ){
	 			scrapes	= scrapes_l.longValue();
	 		}
	 		Long	bytes_in_l = (Long)s_map.get( "bytesin" );
	 		if ( bytes_in_l != null ){
	 			bytes_in	= bytes_in_l.longValue();
	 		}
	 		Long	bytes_out_l = (Long)s_map.get( "bytesout" );
	 		if ( bytes_out_l != null ){
	 			bytes_out	= bytes_out_l.longValue();
	 		}
	 	}

	 	host_torrent.setStartOfDayValues( date_added, completed, announces, scrapes, total_up, total_down, bytes_in, bytes_out );
	}

	protected void
	saveConfig(
		boolean		immediate )
	{
		if( loading ){

			return;
		}

		synchronized( saved_stats_to_delete ){

			if ( saved_stats_to_delete.size() > 0 ){

				Map	saved_stats_copy = new HashMap( saved_stats );

				for (int i=0;i<saved_stats_to_delete.size();i++){

					saved_stats_copy.remove( saved_stats_to_delete.get(i));
				}

				saved_stats_to_delete.clear();

				saved_stats = saved_stats_copy;
			}
		}

		if ( immediate || save_outstanding ){

			save_outstanding	= false;

			try{
			   	Map map = new HashMap();

			   	List list = new ArrayList();

			   	TRHostTorrent[]	torrents = host.getTorrents();

			   	List	stats_entries = new ArrayList();

			   	Set	added = new HashSet();

			   	for (int i = 0; i < torrents.length; i++){

			  	 	try{

						TRHostTorrent torrent = (TRHostTorrent)torrents[i];

						added.add( torrent.getTorrent().getHashWrapper());

						StringBuffer	stats_entry = new StringBuffer(2048);

						byte[]	hash 		= torrent.getTorrent().getHash();
						byte[]	name		= torrent.getTorrent().getName();
						int		status 		= torrent.getStatus();
						long	completed	= torrent.getCompletedCount();
						long	announces	= torrent.getAnnounceCount();
						long	scrapes		= torrent.getScrapeCount();
						long	uploaded	= torrent.getTotalUploaded();
						long	downloaded	= torrent.getTotalDownloaded();
						long	bytes_in	= torrent.getTotalBytesIn();
						long	bytes_out	= torrent.getTotalBytesOut();
						long	date_added	= torrent.getDateAdded();

						int	seed_count 		= torrent.getSeedCount();
						int non_seed_count	= torrent.getLeecherCount();


						Map t_map = new HashMap();

						t_map.put("persistent",new Long(torrent.isPersistent()?1:0));
						t_map.put("passive",new Long(torrent.isPassive()?1:0));

						if ( torrent.isPassive()){

							try{
								String	file = TorrentUtils.getTorrentFileName( torrent.getTorrent());

								t_map.put( "torrent_file", file.getBytes( Constants.BYTE_ENCODING_CHARSET ));

							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}

						t_map.put("hash", hash );
						t_map.put("dateadded", new Long(date_added));
						t_map.put("status", new Long(status ));

						list.add(t_map);

						Map	s_map = new HashMap();

						t_map.put( "stats", s_map );

						s_map.put( "completed", new Long(completed));
						s_map.put( "announces", new Long(announces));
						s_map.put( "scrapes", new Long(scrapes));
						s_map.put( "uploaded", new Long(uploaded));
						s_map.put( "downloaded", new Long(downloaded));
						s_map.put( "bytesin", new Long(bytes_in));
						s_map.put( "bytesout", new Long(bytes_out));


						stats_entry.append( new String(name, Constants.DEFAULT_ENCODING_CHARSET ));
						stats_entry.append(",");
						stats_entry.append( ByteFormatter.nicePrint(hash,true));
						stats_entry.append(",");
						stats_entry.append(status);
						stats_entry.append(",");
						stats_entry.append(seed_count);
						stats_entry.append(",");
						stats_entry.append(non_seed_count);
						stats_entry.append(",");
						stats_entry.append(completed);
						stats_entry.append(",");
						stats_entry.append(announces);
						stats_entry.append(",");
						stats_entry.append(scrapes);
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtc(uploaded));
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtc(downloaded));
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtcPerSec(torrent.getAverageUploaded()));
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtcPerSec(torrent.getAverageDownloaded()));
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtc( torrent.getTotalLeft()));
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtc( bytes_in ));
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtc( bytes_out ));
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtcPerSec(torrent.getAverageBytesIn()));
						stats_entry.append(",");
						stats_entry.append(DisplayFormatters.formatByteCountToKiBEtcPerSec(torrent.getAverageBytesOut()));

						stats_entry.append( "\r\n");

						stats_entries.add( stats_entry );

			  	 	}catch( TOTorrentException e ){

			  	 		Debug.printStackTrace( e );
			  	 	}
			   	}

			   		// now save any non-recovered stats for a while in case the torrent
			   		// gets re-added in the near future

			   	Iterator	it = saved_stats.keySet().iterator();

			   	long	now = SystemTime.getCurrentTime();

			   	while ( it.hasNext()){

			   		HashWrapper	hash = (HashWrapper)it.next();

			   		if ( added.contains( hash )){

			   			continue;
			   		}

			   		Map	t_map = (Map)saved_stats.get( hash );

			   		Long	backup = (Long)t_map.get("backup_time");

			   		if ( backup == null ){

			   			backup	= new Long( now );

			   			t_map.put( "backup_time", backup );
			   		}

			   		if ( now - backup.longValue() < BACKUP_RETENTION_PERIOD ){

			   			list.add( t_map );

			   			added.add( hash );
			   		}
			   	}

			   	map.put("torrents", list);

			   	try{
			   		save_lock_mon.enter();

			   		if ( torrents.length == 0 ){

			   			if ( config_exists ){

			   				FileUtil.deleteResilientConfigFile( "tracker.config" );

			   				config_exists = false;
			   			}
			   		}else{

			   			config_exists = true;

			   			FileUtil.writeResilientConfigFile( "tracker.config", map );
			   		}

					if ( 	COConfigurationManager.getBooleanParameter( "Tracker Log Enable") &&
							stats_entries.size() > 0 ){

					   	try{
					   		String timeStamp = "["+new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(new Date())+"] ";

					   		PrintWriter	pw = null;

					   		File	file_name = new File( log_dir.concat(File.separator).concat(LOG_FILE_NAME) );

					   		try{

					   			pw = new PrintWriter(new FileWriter( file_name, true ));

					   			for (int i=0;i<stats_entries.size();i++){

					   				StringBuffer	stats_entry = (StringBuffer)stats_entries.get(i);

					   				String str = timeStamp + stats_entry.toString();

					   				pw.print( str );
					   			}

					   		}catch( Throwable e ){

					   			Debug.printStackTrace( e );

					   		}finally{

					   			if ( pw != null ){

					   				try{

					   					pw.close();

					   				}catch( Throwable e ){
					   				}
					   			}
					   		}
					   	}catch( Throwable e ){
					   		Debug.printStackTrace( e );
					   	}
				   	}
			   	}finally{

			   		save_lock_mon.exit();
			   	}
			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected void
	saveRequired()
	{
		save_outstanding	= true;
	}
}
