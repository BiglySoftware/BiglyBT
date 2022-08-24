/*
 * File    : TorrentUtils.java
 * Created : 13-Oct-2003
 * By      : stuff
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

package com.biglybt.core.util;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.torrent.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;
import com.biglybt.util.MapUtils;

public class
TorrentUtils
{
	public static final long MAX_TORRENT_FILE_SIZE = 64*1024*1024L;

	private static final String NO_VALID_URL_URL = "http://no.valid.urls.defined/announce";

	static{
		AEDiagnostics.addEvidenceGenerator(
			new AEDiagnosticsEvidenceGenerator()
			{
				@Override
				public void
				generate(
					IndentWriter writer)
				{
					writer.println( "DNS TXT Records" );

					try{
						writer.indent();

						Set<String> names = COConfigurationManager.getDefinedParameters();

						String prefix = "dns.txts.cache.";

						for ( String name: names ){

							if ( name.startsWith( prefix )){

								try{
									String tracker = new String( Base32.decode( name.substring( prefix.length())), "UTF-8" );

									String str = "";

									List<byte[]> txts = (List<byte[]>)COConfigurationManager.getListParameter( name, null );

									if ( txts != null ){

										for ( byte[] txt: txts ){

											str += (str.length()==0?"":", ") + new String( txt, "UTF-8" );
										}
									}

									writer.println( tracker + " -> [" + str + "]" );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					}finally{

						writer.exdent();
					}
				}
			});
	}

	public static final int TORRENT_FLAG_LOW_NOISE			= 0x00000001;
	public static final int TORRENT_FLAG_METADATA_TORRENT	= 0x00000002;
	public static final int TORRENT_FLAG_DISABLE_RCM		= 0x00000004;

	private static final String		TORRENT_AZ_PROP_DHT_BACKUP_ENABLE		= "dht_backup_enable";
	private static final String		TORRENT_AZ_PROP_DHT_BACKUP_REQUESTED	= "dht_backup_requested";
	private static final String		TORRENT_AZ_PROP_TORRENT_FLAGS			= "torrent_flags";
	private static final String		TORRENT_AZ_PROP_PLUGINS					= "plugins";

	public static final String		TORRENT_AZ_PROP_OBTAINED_FROM			= "obtained_from";
	public static final String		TORRENT_AZ_PROP_DISPLAY_NAME			= "display_name";
	private static final String		TORRENT_AZ_PROP_NETWORK_CACHE			= "network_cache";
	private static final String		TORRENT_AZ_PROP_TAG_CACHE				= "tag_cache";
	private static final String		TORRENT_AZ_PROP_INITIAL_TAGS			= "initial_tags";
	private static final String		TORRENT_AZ_PROP_INITIAL_METADATA		= "other_metadata";
	private static final String		TORRENT_AZ_PROP_PEER_CACHE				= "peer_cache";
	private static final String		TORRENT_AZ_PROP_PEER_CACHE_VALID		= "peer_cache_valid";
	public static final String		TORRENT_AZ_PROP_INITIAL_LINKAGE			= "initial_linkage";
	public static final String		TORRENT_AZ_PROP_INITIAL_LINKAGE2		= "initial_linkage2";
	public static final String		TORRENT_AZ_PROP_ORIGINAL_HASH			= "original_hash";
	public static final String		TORRENT_AZ_PROP_HASHTREE_STATE			= "hash_tree";
	public static final String		TORRENT_AZ_PROP_HYBRID_HASH_V2			= "hybrid_hash_v2";
	private static final String		TORRENT_AZ_PROP_V2_ROOT_HASH_CACHE		= "v2_root_hash_cache";	// used for non-v2 torrents only
	
	private static final String		MEM_ONLY_TORRENT_PATH		= "?/\\!:mem_only:!\\/?";

	private static final long		PC_MARKER = RandomUtils.nextLong();

	private static final List<byte[]>		created_torrents;
	private static final Set<HashWrapper>	created_torrents_set;

	private static final ThreadLocal<Map<String,Object>>		tls	=
		new ThreadLocal<Map<String,Object>>()
		{
			@Override
			public Map<String,Object>
			initialValue()
			{
				return(new HashMap<>());
			}
		};

	private static Set<String>		ignore_files_set;
	private static Set<String>		skip_extensions_set;
	private static Set<Object>		skip_file_set;
	
	private static boolean bSaveTorrentBackup;

	private static final CopyOnWriteList<torrentAttributeListener>			torrent_attribute_listeners 	= new CopyOnWriteList<>();
	static final CopyOnWriteList<TorrentAnnounceURLChangeListener>	torrent_url_changed_listeners 	= new CopyOnWriteList<>();

	private static final AsyncDispatcher	dispatcher = new AsyncDispatcher();

	private static boolean					DNS_HANDLING_ENABLE	= true;
	private static final boolean			TRACE_DNS 			= false;
	private static final int						DNS_HISTORY_TIMEOUT	= 4*60*60*1000;

	private static final Map<String,DNSTXTEntry>	dns_mapping = new HashMap<>();
	private static volatile int				dns_mapping_seq_count;
	private static final ThreadPool				dns_threads	= new ThreadPool( "DNS:lookups", 16, true );

	static{
		SimpleTimer.addPeriodicEvent(
			"TU:dnstimer",
			DNS_HISTORY_TIMEOUT/2,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event )
				{
					if ( DNS_HANDLING_ENABLE ){

						checkDNSTimeouts();
					}
				}
			});
	}

	static final DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();

	static {
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Save Torrent Backup",
				"Tracker DNS Records Enable",
				"Enable.Proxy" },
			new ParameterListener() {
				@Override
				public void
				parameterChanged(
					String _name)
				{
					bSaveTorrentBackup = COConfigurationManager.getBooleanParameter( "Save Torrent Backup" );

					boolean enable_proxy 	= COConfigurationManager.getBooleanParameter( "Enable.Proxy" );

					DNS_HANDLING_ENABLE = dns_utils != null && COConfigurationManager.getBooleanParameter( "Tracker DNS Records Enable" ) && !enable_proxy;
				}
			});

		created_torrents = COConfigurationManager.getListParameter( "my.created.torrents", new ArrayList());

		created_torrents_set	= new HashSet();

		Iterator	it = created_torrents.iterator();

		while( it.hasNext()){

			created_torrents_set.add( new HashWrapper((byte[])it.next()));
		}
	}
	
    static private volatile Set<String>	priority_file_exts 				= new HashSet<>();
    static private volatile boolean		priority_file_exts_ignore_case 	= false;
    
    static{
      	COConfigurationManager.addAndFireParameterListeners(
    		new String[]{
    			"priorityExtensions",
    			"priorityExtensionsIgnoreCase" },
    		new ParameterListener()
    		{
    			@Override
			    public void
    			parameterChanged(
    				String parameterName )
    			{
    				Set<String> new_exts = new HashSet<>();
    				
    				String 	extensions = COConfigurationManager.getStringParameter("priorityExtensions","");
					boolean bIgnoreCase = COConfigurationManager.getBooleanParameter("priorityExtensionsIgnoreCase");

    				if(!extensions.equals("")) {
    					StringTokenizer st = new StringTokenizer(extensions,";");
    					while(st.hasMoreTokens()) {
    						String extension = st.nextToken();
    						extension = extension.trim();
    						if(!extension.startsWith(".")){
    							extension = "." + extension;
    						}
    						
    						if ( bIgnoreCase ){
    							extension = extension.toLowerCase( Locale.US );
    						}
    						
    						new_exts.add( extension );
    					}
    				}
    				
    				priority_file_exts 				= new_exts;
    				priority_file_exts_ignore_case	= bIgnoreCase;
    			}
    		});
      	
    }

	static final AtomicLong	torrent_delete_level = new AtomicLong();
	static long			torrent_delete_time;

	private static AtomicLong announce_group_uid_next = new AtomicLong();
	
	public static long
	getAnnounceGroupUID()
	{
		return( announce_group_uid_next.incrementAndGet());
	}
	
	public static TOTorrent
	readFromFile(
		File		file,
		boolean		create_delegate )

		throws TOTorrentException
	{
		return( readFromFile( file, create_delegate, false ));
	}

		/**
		 * If you set "create_delegate" to true then you must understand that this results
		 * is piece hashes being discarded and then re-read from the torrent file if needed
		 * Therefore, if you delete the original torrent file you're going to get errors
		 * if you access the pieces after this (and they've been discarded)
		 * @param file
		 * @param create_delegate
		 * @param force_initial_discard - use to get rid of pieces immediately
		 * @return
		 * @throws TOTorrentException
		 */

	public static ExtendedTorrent
	readDelegateFromFile(
		File		file,
		boolean		force_initial_discard )

		throws TOTorrentException
	{
		return((ExtendedTorrent)readFromFile( file, true, force_initial_discard ));
	}

	public static TOTorrent
	readFromFile(
		File		file,
		boolean		create_delegate,
		boolean		force_initial_discard )

		throws TOTorrentException
	{
		TOTorrent torrent;

		try{
			torrent = TOTorrentFactory.deserialiseFromBEncodedFile(file);

				// make an immediate backup if requested and one doesn't exist

	    	if (bSaveTorrentBackup) {

	    		File torrent_file_bak = FileUtil.newFile(file.getParent(), file.getName() + ".bak");

	    		if ( !torrent_file_bak.exists()){

	    			try{
	    				torrent.serialiseToBEncodedFile(torrent_file_bak);

	    			}catch( Throwable e ){

	    				Debug.printStackTrace(e);
	    			}
	    		}
	    	}

		}catch (TOTorrentException e){

			// Debug.outNoStack( e.getMessage() );

			File torrentBackup = FileUtil.newFile(file.getParent(), file.getName() + ".bak");

			if( torrentBackup.exists()){

				torrent = TOTorrentFactory.deserialiseFromBEncodedFile(torrentBackup);

					// use the original torrent's file name so that when this gets saved
					// it writes back to the original and backups are made as required
					// - set below
			}else{

				throw e;
			}
		}

		torrent.setAdditionalStringProperty("torrent filename", file.toString());

		if ( create_delegate ){

			torrentDelegate	res = new torrentDelegate( torrent, file );

			if ( force_initial_discard ){

				res.discardPieces( SystemTime.getCurrentTime(), true );
			}

			return( res );

		}else{

			return( torrent );
		}
	}

	public static TOTorrent
	readFromBEncodedInputStream(
		InputStream		is )

		throws TOTorrentException
	{
		TOTorrent	torrent = TOTorrentFactory.deserialiseFromBEncodedInputStream( is );

			// as we've just imported this torrent we want to clear out any possible attributes that we
			// don't want such as "torrent filename"

		torrent.removeAdditionalProperties();

		return( torrent );
	}

	public static TOTorrent
	cloneTorrent(
		TOTorrent	torrent )

		throws TOTorrentException
	{
		try{
				// have to go to byte-array level when cloning otherwise nested Map entries end up being shared between
				// the original and the clone...
			
			return( TOTorrentFactory.deserialiseFromBEncodedByteArray( BEncoder.encode( torrent.serialiseToMap())));
			
		}catch( IOException e ){
			
			throw( new TOTorrentException( "Failed to clone torrent", TOTorrentException.RT_DECODE_FAILS, e ));
		}
	}
	

	public static void
	setMemoryOnly(
		TOTorrent			torrent,
		boolean				mem_only )
	{
		if ( mem_only ){

			torrent.setAdditionalStringProperty("torrent filename", MEM_ONLY_TORRENT_PATH );

		}else{

			String s = torrent.getAdditionalStringProperty("torrent filename");

			if ( s != null && s.equals( MEM_ONLY_TORRENT_PATH )){

				torrent.removeAdditionalProperty( "torrent filename" );
			}
		}
	}

	public static void
	writeToFile(
		final TOTorrent		torrent )

		throws TOTorrentException
	{
		writeToFile( torrent, false );
	}

	public static void
	writeToFile(
		TOTorrent		torrent,
		boolean			force_backup )

		throws TOTorrentException
	{
	   try{
	   		torrent.getMonitor().enter();

	    	String str = torrent.getAdditionalStringProperty("torrent filename");

	    	if ( str == null ){

	    		throw (new TOTorrentException("TorrentUtils::writeToFile: no 'torrent filename' attribute defined", TOTorrentException.RT_FILE_NOT_FOUND));
	    	}

	    	if ( str.equals( MEM_ONLY_TORRENT_PATH )){

	    		return;
	    	}

	    		// save first to temporary file as serialisation may require state to be re-read from
	    		// the existing file first and if we rename to .bak first then this aint good

    		File torrent_file_tmp = FileUtil.newFile(str + "._az");

	    	torrent.serialiseToBEncodedFile( torrent_file_tmp );

	    		// now backup if required

	    	File torrent_file = FileUtil.newFile(str);

	    	if ( 	( force_backup ||COConfigurationManager.getBooleanParameter("Save Torrent Backup")) &&
	    			torrent_file.exists()) {

	    		File torrent_file_bak = FileUtil.newFile(str + ".bak");

	    		try{

	    				// Will return false if it cannot be deleted (including if the file doesn't exist).

	    			torrent_file_bak.delete();

	    			torrent_file.renameTo(torrent_file_bak);

	    		}catch( SecurityException e){

	    			Debug.printStackTrace( e );
	    		}
	    	}

	    		// now rename the temp file to required one

	    	if ( torrent_file.exists()){

	    		torrent_file.delete();
	    	}

	    	torrent_file_tmp.renameTo( torrent_file );

	   	}finally{

	   		torrent.getMonitor().exit();
	   	}
	}

	public static void
	writeToFile(
		TOTorrent		torrent,
		File			file,
		boolean			force_backup )

		throws TOTorrentException
	{
		torrent.setAdditionalStringProperty("torrent filename", file.toString());

		writeToFile( torrent, force_backup );
	}

	public static String
	getTorrentFileName(
		TOTorrent		torrent )

		throws TOTorrentException
	{
		return( getTorrentFileName( torrent, true ));
	}
	
	public static String
	getTorrentFileName(
		TOTorrent		torrent,
		boolean			is_mandatory )

		throws TOTorrentException
	{
    	String str = torrent.getAdditionalStringProperty("torrent filename");

    	if ( str == null ){

    		if ( is_mandatory ){
    		
    			throw( new TOTorrentException("TorrentUtils::getTorrentFileName: no 'torrent filename' attribute defined", TOTorrentException.RT_FILE_NOT_FOUND));
    			
    		}else{
    			
    			return( null );
    		}
    	}

    	if ( str.equals( MEM_ONLY_TORRENT_PATH )){

    		return( null );
    	}

		return( str );
	}

	public static void
	clearTorrentFileName(
		TOTorrent		torrent )
	{
		torrent.removeAdditionalProperty( "torrent filename" );
	}
	
	public static void
	copyToFile(
		TOTorrent		torrent,
		File			file )

		throws TOTorrentException
	{
	   	torrent.serialiseToBEncodedFile(file);
	}

	public static void
	delete(
		TOTorrent 		torrent )

		throws TOTorrentException
	{
	   try{
	   		torrent.getMonitor().enter();

	    	String str = torrent.getAdditionalStringProperty("torrent filename");

	    	if ( str == null ){

	    		throw( new TOTorrentException("TorrentUtils::delete: no 'torrent filename' attribute defined", TOTorrentException.RT_FILE_NOT_FOUND));
	    	}

	    	if ( str.equals( MEM_ONLY_TORRENT_PATH )){

	    		return;
	    	}

	    	File file = FileUtil.newFile(str);

	    	if ( !file.delete()){

	    		if ( file.exists()){

	    			throw( new TOTorrentException("TorrentUtils::delete: failed to delete '" + str + "'", TOTorrentException.RT_WRITE_FAILS));
	    		}
	    	}

			 FileUtil.newFile( str + ".bak" ).delete();

	    }finally{

	    	torrent.getMonitor().exit();
	    }
	}

	public static void
	delete(
		File 		torrent_file,
		boolean		force_no_recycle )
	{
		if ( !FileUtil.deleteWithRecycle( torrent_file, force_no_recycle )){

			if ( torrent_file.exists()){

				Debug.out( "TorrentUtils::delete: failed to delete '" + torrent_file + "'" );
			}
		}

		FileUtil.newFile( torrent_file.toString() + ".bak" ).delete();
	}

	public static boolean
	move(
		File		from_torrent,
		File		to_torrent )
	{
		if ( !FileUtil.renameFile(from_torrent, to_torrent )){

			return( false );
		}

		if ( FileUtil.newFile( from_torrent.toString() + ".bak").exists()){

			FileUtil.renameFile(
				FileUtil.newFile( from_torrent.toString() + ".bak"),
				FileUtil.newFile( to_torrent.toString() + ".bak"));
		}

		return( true );
	}

	public static String
	exceptionToText(
		TOTorrentException	e )
	{
		String	errorDetail;

		int	reason = e.getReason();

		if ( reason == TOTorrentException.RT_FILE_NOT_FOUND ){

			errorDetail = MessageText.getString("DownloadManager.error.filenotfound" );

		}else if ( reason == TOTorrentException.RT_ZERO_LENGTH ){

			errorDetail = MessageText.getString("DownloadManager.error.fileempty");

		}else if ( reason == TOTorrentException.RT_TOO_BIG ){

			errorDetail = MessageText.getString("DownloadManager.error.filetoobig");

		}else if ( reason == TOTorrentException.RT_DECODE_FAILS ){

			errorDetail = MessageText.getString("DownloadManager.error.filewithouttorrentinfo" );

		}else if ( reason == TOTorrentException.RT_UNSUPPORTED_ENCODING ){

			errorDetail = MessageText.getString("DownloadManager.error.unsupportedencoding");

		}else if ( reason == TOTorrentException.RT_READ_FAILS ){

			errorDetail = MessageText.getString("DownloadManager.error.ioerror");

		}else if ( reason == TOTorrentException.RT_HASH_FAILS ){

			errorDetail = MessageText.getString("DownloadManager.error.sha1");

		}else if ( reason == TOTorrentException.RT_CANCELLED ){

			errorDetail = MessageText.getString("DownloadManager.error.operationcancancelled");

		}else{

			errorDetail = Debug.getNestedExceptionMessage(e);
		}

		String	msg = Debug.getNestedExceptionMessage(e);

		if (!errorDetail.contains(msg)){

			errorDetail += " (" + msg + ")";
		}

		return( errorDetail );
	}

	public static Set<String>
	getUniqueTrackerHosts(
		TOTorrent	torrent )
	{
		return( getUniqueTrackerHosts( torrent, false ));
	}
	
	public static Set<String>
	getUniqueTrackerHosts(
		TOTorrent	torrent,
		boolean		include_port )
	{
		Set<String>	hosts = new HashSet<>();

		if ( torrent != null ){

			URL	announce_url = torrent.getAnnounceURL();

			if ( announce_url != null ){

				String host = announce_url.getHost();

				if ( host != null ){

					host = host.toLowerCase( Locale.US );
					
					hosts.add( host);
					
					if ( include_port ){
						
						int port = announce_url.getPort();
						
						if ( port == -1 ){
							
							port = announce_url.getDefaultPort();
						}
						
						if ( port > 0 ){
							
							hosts.add( host + ":" + port );
						}
					}
				}
			}

			TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

			TOTorrentAnnounceURLSet[]	sets = group.getAnnounceURLSets();

			for ( TOTorrentAnnounceURLSet set: sets ){

				URL[]	urls = set.getAnnounceURLs();

				for ( URL u: urls ){

					String host = u.getHost();

					if ( host != null ){

						host = host.toLowerCase( Locale.US );
						
						hosts.add( host );
						
						if ( include_port ){
							
							int port = u.getPort();
							
							if ( port == -1 ){
								
								port = u.getDefaultPort();
							}
							
							if ( port > 0 ){
								
								hosts.add( host + ":" + port );
							}
						}
					}
				}
			}
		}

		return( hosts );
	}

	public static String
	announceGroupsToText(
		TOTorrent	torrent )
	{
		URL	announce_url = torrent.getAnnounceURL();

		String announce_url_str = announce_url==null?"":announce_url.toString().trim();

		TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

		TOTorrentAnnounceURLSet[]	sets = group.getAnnounceURLSets();

		if ( sets.length == 0 ){

			return( announce_url_str );

		}else{

			StringBuilder sb = new StringBuilder(1024);

			boolean	announce_found = false;

			for (int i=0;i<sets.length;i++){

				TOTorrentAnnounceURLSet	set = sets[i];

				URL[]	urls = set.getAnnounceURLs();

				if ( urls.length > 0 ){

					for (int j=0;j<urls.length;j++){

						String	str = urls[j].toString().trim();

						if ( str.equals( announce_url_str )){

							announce_found = true;
						}

						sb.append( str );
						sb.append( "\r\n" );
					}

					sb.append( "\r\n" );
				}
			}

			String result = sb.toString().trim();

			if ( !announce_found ){

				if ( announce_url_str.length() > 0 ){

					if ( result.length() == 0 ){

						result = announce_url_str;

					}else{

						result = "\r\n\r\n" + announce_url_str;
					}
				}
			}

			return( result );
		}
	}

	public static String
	announceGroupsToText(
		List<List<String>>	group )
	{
		StringBuilder sb = new StringBuilder(1024);

		for ( List<String> urls: group ){

			if ( sb.length() > 0 ){

				sb.append( "\r\n" );
			}

			for ( String str: urls ){

				sb.append( str );
				sb.append( "\r\n" );
			}
		}

		return( sb.toString().trim());
	}

	public static List<List<String>>
	announceTextToGroups(
		String	text )
	{
		List<List<String>>	groups = new ArrayList<>();

		String[]	lines = text.split( "\n" );

		List<String>	current_group = new ArrayList<>();

		Set<String>	hits = new HashSet<>();

		for( String line: lines ){

			line = line.trim();

			if ( line.length() == 0 ){

				if ( current_group.size() > 0 ){

					groups.add( current_group );

					current_group = new ArrayList<>();
				}
			}else{
				String lc_line = line.toLowerCase();

				if ( hits.contains( lc_line )){

					continue;
				}

				hits.add( lc_line );

				current_group.add( line );
			}
		}

		if ( current_group.size() > 0 ){

			groups.add( current_group );
		}

		return( groups );
	}

	public static List<List<String>>
	announceGroupsToList(
		TOTorrent	torrent )
	{
		List<List<String>>	groups = new ArrayList<>();

		TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

		TOTorrentAnnounceURLSet[]	sets = group.getAnnounceURLSets();

		if ( sets.length == 0 ){

			List<String>	s = new ArrayList<>();

			s.add( UrlUtils.getCanonicalString( torrent.getAnnounceURL()));

			groups.add(s);

		}else{

			Set<String>	all_urls = new HashSet<>();

			for (int i=0;i<sets.length;i++){

				List<String>	s = new ArrayList<>();

				TOTorrentAnnounceURLSet	set = sets[i];

				URL[]	urls = set.getAnnounceURLs();

				for (int j=0;j<urls.length;j++){

					String u = UrlUtils.getCanonicalString( urls[j] );

					s.add( u );

					all_urls.add( u );
				}

				if ( s.size() > 0 ){

					groups.add(s);
				}
			}

			String a = UrlUtils.getCanonicalString( torrent.getAnnounceURL());

			if ( !all_urls.contains( a )){

				List<String>	s = new ArrayList<>();

				s.add( a );

				groups.add( 0, s );
			}
		}

		return( groups );
	}
	
	private static List<List<String>>
	expandTrackerLists(
		List<List<String>>	groups )
	{
		List<List<String>> result = new ArrayList<>();
		
		Set<String>	tracker_lists = new HashSet<>();
		
		Set<String>	existing = new HashSet<>();
		
		for ( List<String> group: groups ){
		
			List<String>	new_group = new ArrayList<>();
			
			for ( String url: group ){
				
				if ( url.startsWith( "trackerlist:" )){
					
					tracker_lists.add( url );
					
				}else{
					
					new_group.add( url );
											
					existing.add( UrlUtils.getCanonicalString( url ));
				}
			}
			
			if ( !new_group.isEmpty()){
				
				result.add( new_group );
			}
		}
		
		if ( !tracker_lists.isEmpty()){
			
			for ( String tl: tracker_lists ){
				
				try{
					InputStream is = ResourceDownloaderFactoryImpl.getSingleton().create( new URL( tl )).download();
				
					try{
						String list = FileUtil.readInputStreamAsString( is, 32*1024, "UTF-8" );
						
						String[] bits = list.split( "\n" );
						
						for ( String bit: bits ){
							
							bit = bit.trim();
							
							if ( !bit.isEmpty()){
								
								bit = UrlUtils.getCanonicalString( bit );
								
								if ( !existing.contains( bit )){
									
									existing.add( bit );
										
									List<String> new_group = new ArrayList<>();
									
									new_group.add( bit );
									
									result.add( new_group );
								}
							}
						}					
					}finally{
						
						is.close();
					}
				}catch( Throwable e ){
					
				}
			}
		}
		
		return( result );
	}
	
		/**
		 * This method DOES NOT MODIFY THE TORRENT
		 * @param groups
		 * @param torrent
		 * @return
		 */

	public static TOTorrentAnnounceURLSet[]
	listToAnnounceSets(
		List<List<String>>		groups,
		TOTorrent				torrent )
	{
		groups = expandTrackerLists( groups );

		List<TOTorrentAnnounceURLSet> sets = new ArrayList<>();

		for ( List<String> group: groups ){

			List<URL> urls = new ArrayList<>(group.size());

			for ( String s: group ){

				try{
					urls.add( new URL( s));

				}catch( Throwable e ){
				}
			}

			if ( urls.size() > 0 ){

				sets.add( torrent.getAnnounceURLGroup().createAnnounceURLSet(urls.toArray( new URL[urls.size()])));
			}
		}

		return( sets.toArray( new TOTorrentAnnounceURLSet[sets.size()]));
	}

	public static void
	listToAnnounceGroups(
		List<List<String>>		groups,
		TOTorrent				torrent )
	{
		groups = expandTrackerLists( groups );
		
			// if the new groups no longer contain the main announce url then we replace this with the first in the groups. The primary reason for this
			// is that the DNS TXT record munging code always considers the announce-url as input to the generation of (potentially) modified URLs and if we
			// leave the announce-url there it will magically re-appear
			//

		try{
			TOTorrentAnnounceURLGroup tg = torrent.getAnnounceURLGroup();

			if ( groups.size() == 1 ){

				List	set = (List)groups.get(0);

				if ( set.size() == 1 ){

					torrent.setAnnounceURL( new URL((String)set.get(0)));

					tg.setAnnounceURLSets( new TOTorrentAnnounceURLSet[0]);

					return;
				}
			}

			String announce_url 	= torrent.getAnnounceURL().toExternalForm();

			URL	first_url		= null;

			Vector	g = new Vector();

			for (int i=0;i<groups.size();i++){

				List<String>	set = (List<String>)groups.get(i);

				URL[]	urls = new URL[set.size()];

				for (int j=0;j<set.size();j++){

					String url_str = set.get(j);

					if ( announce_url != null && url_str.equals( announce_url )){

						announce_url = null;
					}

					urls[j] = new URL((String)set.get(j));

					if ( first_url == null ){

						first_url = urls[j];
					}
				}

				if ( urls.length > 0 ){

					g.add( tg.createAnnounceURLSet( urls ));
				}
			}

			TOTorrentAnnounceURLSet[]	sets = new TOTorrentAnnounceURLSet[g.size()];

			if ( sets.length == 0 ){

					// hmm, no valid urls at all

				
				torrent.setAnnounceURL( getDecentralisedURL( torrent ));

			}else{

				if ( announce_url != null && first_url != null ){

					torrent.setAnnounceURL( first_url );
				}
			}

			g.copyInto( sets );

			tg.setAnnounceURLSets( sets );

		}catch( MalformedURLException e ){

			Debug.printStackTrace( e );
		}
	}

	public static void
	announceGroupsInsertFirst(
		TOTorrent	torrent,
		String		first_url )
	{
		try{

			announceGroupsInsertFirst( torrent, new URL( first_url ));

		}catch( MalformedURLException e ){

			Debug.printStackTrace( e );
		}
	}

	public static void
	announceGroupsInsertFirst(
		TOTorrent	torrent,
		URL			first_url )
	{
		announceGroupsInsertFirst( torrent, new URL[]{ first_url });
	}

	public static void
	announceGroupsInsertFirst(
		TOTorrent	torrent,
		URL[]		first_urls )
	{
		TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

		TOTorrentAnnounceURLSet[] sets = group.getAnnounceURLSets();

		TOTorrentAnnounceURLSet set1 = group.createAnnounceURLSet( first_urls );


		if ( sets.length > 0 ){

			TOTorrentAnnounceURLSet[]	new_sets = new TOTorrentAnnounceURLSet[sets.length+1];

			new_sets[0] = set1;

			System.arraycopy( sets, 0, new_sets, 1, sets.length );

			group.setAnnounceURLSets( new_sets );

		}else{

			TOTorrentAnnounceURLSet set2 = group.createAnnounceURLSet(new URL[]{torrent.getAnnounceURL()});

			group.setAnnounceURLSets(
				new  TOTorrentAnnounceURLSet[]{ set1, set2 });
		}
	}

	public static void
	announceGroupsInsertLast(
		TOTorrent	torrent,
		URL[]		first_urls )
	{
		TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

		TOTorrentAnnounceURLSet[] sets = group.getAnnounceURLSets();

		TOTorrentAnnounceURLSet set1 = group.createAnnounceURLSet( first_urls );


		if ( sets.length > 0 ){

			TOTorrentAnnounceURLSet[]	new_sets = new TOTorrentAnnounceURLSet[sets.length+1];

			new_sets[sets.length] = set1;

			System.arraycopy( sets, 0, new_sets, 0, sets.length );

			group.setAnnounceURLSets( new_sets );

		}else{

			TOTorrentAnnounceURLSet set2 = group.createAnnounceURLSet(new URL[]{torrent.getAnnounceURL()});

			group.setAnnounceURLSets(
				new  TOTorrentAnnounceURLSet[]{ set2, set1 });
		}
	}

	public static void
	announceGroupsSetFirst(
		TOTorrent	torrent,
		String		first_url )
	{
		List	groups = announceGroupsToList( torrent );

		boolean	found = false;

		outer:
		for (int i=0;i<groups.size();i++){

			List	set = (List)groups.get(i);

			for (int j=0;j<set.size();j++){

				if ( first_url.equals(set.get(j))){

					set.remove(j);

					set.add(0, first_url);

					groups.remove(set);

					groups.add(0,set);

					found = true;

					break outer;
				}
			}
		}

		if ( !found ){

			System.out.println( "TorrentUtils::announceGroupsSetFirst - failed to find '" + first_url + "'" );
		}

		listToAnnounceGroups( groups, torrent );
	}

	public static boolean
	announceGroupsContainsURL(
		TOTorrent	torrent,
		String		url )
	{
		List	groups = announceGroupsToList( torrent );

		for (int i=0;i<groups.size();i++){

			List	set = (List)groups.get(i);

			for (int j=0;j<set.size();j++){

				if ( url.equals(set.get(j))){

					return( true );
				}
			}
		}

		return( false );
	}

	public static boolean
	canMergeAnnounceURLs(
		TOTorrent	new_torrent,
		TOTorrent	dest_torrent )
	{
		try{
			List<List<String>>	new_groups 	= announceGroupsToList( new_torrent );
			List<List<String>> 	dest_groups = announceGroupsToList( dest_torrent );

			Set<String>	all_dest 	= new HashSet<>();

			for ( List<String> l: dest_groups ){

				all_dest.addAll( l );
			}

			for ( List<String> l: new_groups ){

				for ( String u: l ){

					List<URL> mods = applyAllDNSMods( new URL( u ));

					if ( mods != null ){

						for ( URL m: mods ){

							if ( !all_dest.contains( UrlUtils.getCanonicalString( m ))){

								return( true );
							}
						}
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		return( false );
	}

	public static boolean
	mergeAnnounceURLs(
		TOTorrent 	new_torrent,
		TOTorrent	dest_torrent )
	{
		if ( new_torrent == null || dest_torrent == null ){

			return( false);
		}

		List	new_groups 	= announceGroupsToList( new_torrent );
		List 	dest_groups = announceGroupsToList( dest_torrent );

		List	groups_to_add = new ArrayList();

		for (int i=0;i<new_groups.size();i++){

			List new_set = (List)new_groups.get(i);

			boolean	match = false;

			for (int j=0;j<dest_groups.size();j++){

				List dest_set = (List)dest_groups.get(j);

				boolean same = new_set.size() == dest_set.size();

				if ( same ){

					for (int k=0;k<new_set.size();k++){

						String new_url = (String)new_set.get(k);

						if ( !dest_set.contains(new_url)){

							same = false;

							break;
						}
					}
				}

				if ( same ){

					match = true;

					break;
				}
			}

			if ( !match ){

				groups_to_add.add( new_set );
			}
		}

		if ( groups_to_add.size() == 0 ){

			return( false );
		}

		for (int i=0;i<groups_to_add.size();i++){

			dest_groups.add(i,groups_to_add.get(i));
		}

		listToAnnounceGroups( dest_groups, dest_torrent );

		return( true );
	}

	public static List<List<String>>
	mergeAnnounceURLs(
		List<List<String>> 	base_urls,
		List<List<String>>	merge_urls )
	{
		base_urls = getClone( base_urls );
		if ( merge_urls == null ){
			return( base_urls );
		}
		Set<String> mergesSet = new HashSet<>();
		mergesSet.add( NO_VALID_URL_URL );	// this results in removal of this dummy url if present
		for ( List<String> l: merge_urls ){
			mergesSet.addAll(l);
		}
		Iterator<List<String>> it1 = base_urls.iterator();
		while( it1.hasNext()){
			List<String> l = it1.next();
			Iterator<String> it2 = l.iterator();
			while( it2.hasNext()){
				if ( mergesSet.contains( it2.next())){
					it2.remove();
				}
			}
			if ( l.isEmpty()){
				it1.remove();
			}
		}

		for (List<String> l: merge_urls ){
			if ( !l.isEmpty()){
				base_urls.add( l );
			}
		}

		return( base_urls );
	}

	public static List<List<String>>
	removeAnnounceURLs(
		List<List<String>> 	base_urls,
		List<List<String>>	remove_urls,
		boolean				use_prefix_match )
	{
		base_urls = getClone( base_urls );
		if ( remove_urls == null ){
			return( base_urls );
		}
		Set<String> removeSet = new HashSet<>();
		removeSet.add( NO_VALID_URL_URL );	// this results in removal of this dummy url if present
		for ( List<String> l: remove_urls ){
			for ( String s: l ){
				removeSet.add( UrlUtils.getCanonicalString( s.toLowerCase( Locale.US )));
			}
		}
		Iterator<List<String>> it1 = base_urls.iterator();
		while( it1.hasNext()){
			List<String> l = it1.next();
			Iterator<String> it2 = l.iterator();
			while( it2.hasNext()){
				String url = it2.next();
				if ( url.equals( NO_VALID_URL_URL )){
					it2.remove();
				}else{
					url = url.toLowerCase( Locale.US );

					url = UrlUtils.getCanonicalString( url );
					
					if ( use_prefix_match ){

						for ( String s: removeSet ){

							if ( url.startsWith( s )){

								it2.remove();
								break;
							}
						}
					}else{
						if ( removeSet.contains( url )){

							it2.remove();
						}
					}
				}
			}
			if ( l.isEmpty()){
				it1.remove();
			}
		}

		return( base_urls );
	}

	public static List<List<String>>
	removeAnnounceURLs2(
		List<List<String>> 	base_urls,
		List<String>		remove_urls,
		boolean				use_prefix_match )
	{
		if ( remove_urls == null ){
				// general semantics are to return a clone so caller can modify
			return( getClone( base_urls ) );
		}

		List<List<String>> temp = new ArrayList<>(1);

		temp.add( remove_urls );

		return( removeAnnounceURLs( base_urls, temp, use_prefix_match ));
	}

	public static List<List<String>>
	getClone(
		List<List<String>> lls )
	{
		if ( lls == null ){
			return( lls );
		}
		List<List<String>>	result = new ArrayList<>(lls.size());
		for ( List<String> l: lls ){
			result.add(new ArrayList<>(l));
		}
		return( result );
	}

	public static boolean
	areIdentical(
		List<List<String>>	ll1,
		List<List<String>>	ll2 )
	{
		if ( ll1 == null && ll2 == null ){
			return( true );
		}else if ( ll1 == null || ll2 == null ){
			return( false );
		}
		
		int	len = ll1.size();
		
		if ( len != ll2.size()){
			
			return( false );
		}
		
		for ( int i=0;i<len;i++){
			List<String> l1 = ll1.get(i);
			List<String> l2 = ll2.get(i);
			
			int l = l1.size();
			
			if ( l != l2.size()){
				
				return( false );
			}
			
			for ( int j=0;j<l;j++){
				if ( !l1.get(j).equals(l2.get(j))){
					return( false );
				}
			}
		}
		
		return( true );
	}
	
	public static boolean
	replaceAnnounceURL(
		TOTorrent		torrent,
		URL				old_url,
		URL				new_url )
	{
		boolean	found = false;

		String	old_str = old_url.toString();
		String	new_str = new_url.toString();

		List	l = announceGroupsToList( torrent );

		for (int i=0;i<l.size();i++){

			List	set = (List)l.get(i);

			for (int j=0;j<set.size();j++){

				if (((String)set.get(j)).equals(old_str)){

					found	= true;

					set.set( j, new_str );
				}
			}
		}

		if ( found ){

			listToAnnounceGroups( l, torrent );
		}

		if ( torrent.getAnnounceURL().toString().equals( old_str )){

			torrent.setAnnounceURL( new_url );

			found	= true;
		}

		if ( found ){

			try{
				writeToFile( torrent );

			}catch( Throwable e ){

				Debug.printStackTrace(e);

				return( false );
			}
		}

		return( found );
	}

	public static void
	setResumeDataTotallyIncomplete(
		DownloadManagerState	download_manager_state )
	{
		DiskManagerFactory.setResumeDataTotallyIncomplete( download_manager_state );
	}
	
	public static void
	setResumeDataCompletelyValid(
		DownloadManagerState	download_manager_state )
	{
		DiskManagerFactory.setResumeDataCompletelyValid( download_manager_state );
	}

	public static String
	getLocalisedName(
		TOTorrent		torrent )
	{
		if (torrent == null) {
			return "";
		}
		try{
			String utf8Name = torrent.getUTF8Name();
			if (utf8Name != null) {
				return utf8Name;
			}

			LocaleUtilDecoder decoder = LocaleTorrentUtil.getTorrentEncodingIfAvailable( torrent );

			if ( decoder == null ){

				return( new String(torrent.getName(),Constants.DEFAULT_ENCODING_CHARSET));
			}

			return( decoder.decodeString(torrent.getName()));

		}catch( Throwable e ){

			Debug.printStackTrace( e );

			return( new String( torrent.getName()));
		}
	}

	public static void
	setTLSTorrentHash(
		HashWrapper		hash )
	{
		tls.get().put( "hash", hash );
	}

	public static HashWrapper
	getTLSTorrentHash()
	{
		return((HashWrapper)tls.get().get( "hash" ));
	}

	public static TOTorrent
	getTLSTorrent()
	{
		HashWrapper	hash = (HashWrapper)(tls.get()).get("hash");

		if ( hash != null ){

			try{
				Core core = CoreFactory.getSingleton();

				DownloadManager dm = core.getGlobalManager().getDownloadManager( hash );

				if ( dm != null ){

					return( dm.getTorrent());
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		return( null );
	}

	public static void
	setTLSDescription(
		String		desc )
	{
		tls.get().put( "desc", desc );
	}

	public static String
	getTLSDescription()
	{
		return((String)tls.get().get( "desc" ));
	}

		/**
		 * get tls for cloning onto another thread
		 * @return
		 */

	public static Object
	getTLS()
	{
		return(new HashMap<>(tls.get()));
	}

	public static void
	setTLS(
		Object	obj )
	{
		Map<String,Object>	m = (Map<String,Object>)obj;

		Map<String,Object> tls_map = tls.get();

		tls_map.clear();

		tls_map.putAll(m);
	}

	public static URL
	getDecentralisedEmptyURL()
	{
		try{
			return( new URL( "dht://" ));

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

	public static URL
	getDecentralisedURL(
		byte[]		hash )
	{
		try{
			return( new URL( "dht://" + ByteFormatter.encodeString( hash ) + ".dht/announce" ));

		}catch( Throwable e ){

			Debug.out( e );

			return( getDecentralisedEmptyURL());
		}
	}

	public static URL
	getDecentralisedURL(
		TOTorrent	torrent )
	{
		try{
			return( new URL( "dht://" + ByteFormatter.encodeString( torrent.getHash()) + ".dht/announce" ));

		}catch( Throwable e ){

			Debug.out( e );

			return( getDecentralisedEmptyURL());
		}
	}

	public static void
	setDecentralised(
		TOTorrent	torrent )
	{
   		torrent.setAnnounceURL( getDecentralisedURL( torrent ));
	}

	public static boolean
	isDecentralised(
		TOTorrent		torrent )
	{
		if ( torrent == null ){

			return( false );
		}

		return( torrent.isDecentralised());
	}


	public static boolean
	isDecentralised(
		URL		url )
	{
		if ( url == null ){

			return( false );
		}

		return( url.getProtocol().equalsIgnoreCase( "dht" ));
	}

	public static boolean
	isDecentralised(
		String		host )
	{
		if ( host == null ){

			return( false );
		}

		return( host.endsWith( ".dht" ));
	}

	private static Map
	getAzureusProperties(
		TOTorrent	torrent )
	{
		Map	m = torrent.getAdditionalMapProperty( TOTorrent.AZUREUS_PROPERTIES );

		if ( m == null ){

			m = new HashMap();

			torrent.setAdditionalMapProperty( TOTorrent.AZUREUS_PROPERTIES, m );
		}

		return( m );
	}

	private static Map
	getAzureusPrivateProperties(
		TOTorrent	torrent )
	{
		Map	m = torrent.getAdditionalMapProperty( TOTorrent.AZUREUS_PRIVATE_PROPERTIES );

		if ( m == null ){

			m = new HashMap();

			torrent.setAdditionalMapProperty( TOTorrent.AZUREUS_PRIVATE_PROPERTIES, m );
		}

		return( m );
	}

	private static String
	getContentMapString(
		TOTorrent	torrent,
		String 		key )
	{
		Map m = getAzureusProperties( torrent );

		Object content = m.get( "Content" );

		if ( !(content instanceof Map )){

			return null;
		}

		Map mapContent = (Map)content;

		Object obj = mapContent.get(key);

		if ( obj instanceof String ){

			return (String) obj;

		}else if ( obj instanceof byte[] ){

			return new String((byte[]) obj, Constants.DEFAULT_ENCODING_CHARSET);
		}

		return null;
	}

	public static boolean
	isFeaturedContent(
		TOTorrent		torrent )
	{
		String content_type = getContentMapString( torrent, "Content Type" );

		return( content_type != null && content_type.equalsIgnoreCase( "featured" ));
	}

	public static void
	setObtainedFrom(
		File			file,
		String			str )
	{
		try{
			TOTorrent	torrent = readFromFile( file, false, false );

			setObtainedFrom( torrent, str );

			writeToFile( torrent );

		} catch (TOTorrentException e) {
			// ignore, file probably not torrent

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	public static void
	setObtainedFrom(
		TOTorrent		torrent,
		String			str )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			if ( str == null || str.trim().length() == 0 ){

				m.remove( TORRENT_AZ_PROP_OBTAINED_FROM );

			}else{

				m.put( TORRENT_AZ_PROP_OBTAINED_FROM, str.trim().getBytes( "UTF-8" ));
			}

			fireAttributeListener( torrent, TORRENT_AZ_PROP_OBTAINED_FROM, str );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	public static String
	getObtainedFrom(
		TOTorrent		torrent )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		byte[]	from = (byte[])m.get( TORRENT_AZ_PROP_OBTAINED_FROM );

		if ( from != null ){

			try{
				return( new String( from, "UTF-8" ));

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		return( null );
	}

	public static void
	setDisplayName(
		TOTorrent		torrent,
		String			str )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			if ( str == null || str.trim().length() == 0 ){

				m.remove( TORRENT_AZ_PROP_DISPLAY_NAME );

			}else{

				m.put( TORRENT_AZ_PROP_DISPLAY_NAME, str.trim().getBytes( "UTF-8" ));
			}

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	public static String
	getDisplayName(
		TOTorrent		torrent )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		byte[]	from = (byte[])m.get( TORRENT_AZ_PROP_DISPLAY_NAME );

		if ( from != null ){

			try{
				return( new String( from, "UTF-8" ));

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		return( null );
	}
	
	public static void
	setNetworkCache(
		TOTorrent		torrent,
		List<String>	networks )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			m.put( TORRENT_AZ_PROP_NETWORK_CACHE, networks );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	public static List<String>
	getNetworkCache(
		TOTorrent		torrent )
	{
		List<String>	result = new ArrayList<>();

		Map	m = getAzureusPrivateProperties( torrent );

		try{
			List l = (List)m.get( TORRENT_AZ_PROP_NETWORK_CACHE );

			if ( l != null ){

				for (Object o: l ){

					if ( o instanceof String ){

						result.add((String)o);

					}else if ( o instanceof byte[] ){

						String s = new String((byte[])o, "UTF-8" );

						for ( String x: AENetworkClassifier.AT_NETWORKS ){

							if ( s.equals( x )){

								result.add( x );

								break;
							}
						}
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		return( result );
	}

	public static void
	setTagCache(
		TOTorrent		torrent,
		List<String>	tags )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			m.put( TORRENT_AZ_PROP_TAG_CACHE, tags );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	public static List<String>
	getTagCache(
		TOTorrent		torrent )
	{
		List<String>	result = new ArrayList<>();

		Map	m = getAzureusPrivateProperties( torrent );

		try{
			List l = (List)m.get( TORRENT_AZ_PROP_TAG_CACHE );

			if ( l != null ){

				for (Object o: l ){

					if ( o instanceof String ){

						result.add((String)o);

					}else if ( o instanceof byte[] ){

						String s = new String((byte[])o, "UTF-8" );

						result.add( s );
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		return( result );
	}

	public static void
	setInitialTags(
		TOTorrent		torrent,
		List<String>	tags )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			m.put( TORRENT_AZ_PROP_INITIAL_TAGS, tags );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	public static List<String>
	getInitialTags(
		TOTorrent		torrent )
	{
		List<String>	result = new ArrayList<>();

		Map	m = getAzureusPrivateProperties( torrent );

		try{
			List l = (List)m.get( TORRENT_AZ_PROP_INITIAL_TAGS );

			if ( l != null ){

				for (Object o: l ){

					if ( o instanceof String ){

						result.add((String)o);

					}else if ( o instanceof byte[] ){

						String s = new String((byte[])o, "UTF-8" );

						result.add( s );
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		return( result );
	}
	
	public static void
	setInitialMetadata(
		TOTorrent				torrent,
		Map<String,Object>		metadata )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			m.put( TORRENT_AZ_PROP_INITIAL_METADATA, metadata );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}
	
	public static Map<String,Object>
	getInitialMetadata(
		TOTorrent		torrent )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			return( (Map<String,Object>)m.get( TORRENT_AZ_PROP_INITIAL_METADATA));

	
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		return( Collections.emptyMap());
	}
	
	public static Map<String,Object>
	getInitialMetadata(
		DownloadManager		dm,
		boolean				include_dn )
	{
		Map<String,Object>	metadata = new HashMap<>();

		try{				
			DownloadManagerState	dms = dm.getDownloadState();
			
			if ( include_dn ){
				
				String name = dms.getDisplayName();
				
				if ( name != null ){
				
					MapUtils.setMapString( metadata, "dn", name );
				}
			}
			
			String comment = dms.getUserComment();
			
			if ( comment != null && !comment.isEmpty()){
				
				MapUtils.setMapString( metadata, "comment", comment );
			}
			
				// category also manually set in MagnetPlugin when metadata extracted after dm removed from gm
			
			Category cat = dms.getCategory();
			
			if ( cat != null ){
				
				if ( cat.getType() == Category.TYPE_USER ){
					
					MapUtils.setMapString( metadata, "category", cat.getName());
				}
			}
			
			TOTorrent torrent = dm.getTorrent();
			
			if ( torrent != null ){
				
				byte[] thumbnail = PlatformTorrentUtils.getContentThumbnail( torrent );
				
				if ( thumbnail != null ){
					
					String type = PlatformTorrentUtils.getContentThumbnailType( torrent );
					
					metadata.put( "thumbnail", thumbnail );
					
					MapUtils.setMapString( metadata, "thumbnail_type", type );
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		return( metadata );
	}
	
	public static void
	setInitialMetadata(
		DownloadManager			dm,
		Map<String,Object>		metadata,
		boolean					include_dn )
	{
		try{
			if ( metadata == null || metadata.isEmpty()){
				
				return;
			}
			
			DownloadManagerState	dms = dm.getDownloadState();

			if ( include_dn ){
				
				String name = MapUtils.getMapString( metadata, "dn", null );
	
				if ( name != null ){
					
					dms.setDisplayName( name );
				}
			}
			
			String comment = MapUtils.getMapString( metadata, "comment", null );
			
			if ( comment != null ){
				
				dms.setUserComment( comment );
			}
			
			String category = MapUtils.getMapString( metadata, "category", null );
	
			if ( category != null ){
				
				Category cat = CategoryManager.getCategory( category );
				
				if ( cat != null ){
				
					dms.setCategory(cat);
				}
			}
			
			byte[] thumbnail = (byte[])metadata.get( "thumbnail" );
			
			if ( thumbnail != null ){
				
				TOTorrent torrent = dm.getTorrent();
				
				if ( torrent != null ){
					
					String type = MapUtils.getMapString( metadata, "thumbnail_type", null );
					
					PlatformTorrentUtils.setContentThumbnail( torrent, thumbnail, type );
				}
			}
						
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	
	public static void
	setOriginalHash(
		TOTorrent		torrent,
		byte[]			hash )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			m.put( TORRENT_AZ_PROP_ORIGINAL_HASH, hash );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}
	
	public static byte[]
	getOriginalHash(
		TOTorrent		torrent )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			return((byte[])m.get( TORRENT_AZ_PROP_ORIGINAL_HASH ));

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
		
		return( null );
	}
	
	public static void
	setHashTreeState(
		TOTorrent		torrent,
		Map				state )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			if ( state == null ){
				
				m.remove( TORRENT_AZ_PROP_HASHTREE_STATE );
				
			}else{
			
				m.put( TORRENT_AZ_PROP_HASHTREE_STATE, state );
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}
	
	public static Map
	getHashTreeState(
		TOTorrent		torrent )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			return(Map)m.get( TORRENT_AZ_PROP_HASHTREE_STATE );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
		
		return( null );
	}
	
	public static void
	setV2RootHashCache(
		TOTorrent		torrent,
		Map				cache )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			if ( cache == null ){
				
				m.remove( TORRENT_AZ_PROP_V2_ROOT_HASH_CACHE );
				
			}else{
			
				m.put( TORRENT_AZ_PROP_V2_ROOT_HASH_CACHE, cache );
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}
	
	public static Map
	getV2RootHashCache(
		TOTorrent		torrent )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			return(Map)m.get( TORRENT_AZ_PROP_V2_ROOT_HASH_CACHE );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
		
		return( null );
	}
	
	public static byte[]
	getHashForDisplay(
		TOTorrent		torrent )
	{
		if ( torrent == null ){
			
			return( null );
		}
		
		byte[] hash = getOriginalHash( torrent );
		
		if ( hash == null ){
			
			try{
				hash = torrent.getHash();
				
			}catch( Throwable e ){
			}
		}
		
		return( hash );
	}
	
	public static void
	setPeerCache(
		TOTorrent		torrent,
		Map				pc )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			m.put( TORRENT_AZ_PROP_PEER_CACHE, pc );

			setPeerCacheValid( torrent );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	public static void
	setPeerCacheValid(
		TOTorrent		torrent )
	{
		Map	m = getAzureusPrivateProperties( torrent );

		try{
			m.put( TORRENT_AZ_PROP_PEER_CACHE_VALID, new Long( PC_MARKER ));

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	public static Map
	getPeerCache(
		TOTorrent		torrent )
	{
		try{
			Map	m = getAzureusPrivateProperties( torrent );

			Long value = (Long)m.get( TORRENT_AZ_PROP_PEER_CACHE_VALID );

			if ( value != null && value == PC_MARKER ){

				Map	pc = (Map)m.get( TORRENT_AZ_PROP_PEER_CACHE );

				return( pc );
			}

		}catch( Throwable e ){

			Debug.out( e );
		}

		return( null );
	}

	public static void
	setFlag(
		TOTorrent		torrent,
		int				flag,
		boolean			value )
	{
		Map	m = getAzureusProperties( torrent );

		Long	flags = (Long)m.get( TORRENT_AZ_PROP_TORRENT_FLAGS );

		if ( flags == null ){

			flags = new Long(0);
		}

		m.put( TORRENT_AZ_PROP_TORRENT_FLAGS, new Long(flags.intValue() | flag ));
	}

	public static boolean
	getFlag(
		TOTorrent		torrent,
		int				flag )
	{
		Map	m = getAzureusProperties( torrent );

		Long	flags = (Long)m.get( TORRENT_AZ_PROP_TORRENT_FLAGS );

		if ( flags == null ){

			return( false );
		}

		return(( flags.intValue() & flag ) != 0 );
	}

	public static Map<Integer,File>
	getInitialLinkage(
		TOTorrent		torrent )
	{
		Map<Integer,File>	result = new HashMap<>();

		try{
			Map	pp = torrent.getAdditionalMapProperty( TOTorrent.AZUREUS_PRIVATE_PROPERTIES );

			if ( pp != null ){

				Map<String,Object> _links;

				byte[]	g_data = (byte[])pp.get( TorrentUtils.TORRENT_AZ_PROP_INITIAL_LINKAGE2 );

				if ( g_data == null ){

					_links = (Map<String,Object>)pp.get( TorrentUtils.TORRENT_AZ_PROP_INITIAL_LINKAGE );

				}else{

					_links = BDecoder.decode(new BufferedInputStream( new GZIPInputStream( new ByteArrayInputStream( g_data ))));

				}
				if ( _links != null ){//&& TorrentUtils.isCreatedTorrent( torrent )){

					Map<String,String> links = (Map<String,String>)BDecoder.decodeStrings( _links );

					for ( Map.Entry<String,String> entry: links.entrySet()){

						int		file_index 	= Integer.parseInt( entry.getKey());
						String	file		= entry.getValue();

						result.put( file_index, FileUtil.newFile( file ));
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( "Failed to read linkage map", e );
		}

		return( result );
	}

	public static void
	setPluginStringProperty(
		TOTorrent		torrent,
		String			name,
		String			value )
	{
		Map	m = getAzureusProperties( torrent );

		Object obj = m.get( TORRENT_AZ_PROP_PLUGINS );

		Map	p;

		if ( obj instanceof Map ){

			p = (Map)obj;

		}else{

			p = new HashMap();

			m.put( TORRENT_AZ_PROP_PLUGINS, p );
		}

		if ( value == null ){

			p.remove( name );

		}else{

			p.put( name, value.getBytes());
		}
	}

	public static String
	getPluginStringProperty(
		TOTorrent		torrent,
		String			name )
	{
		Map	m = getAzureusProperties( torrent );

		Object	obj = m.get( TORRENT_AZ_PROP_PLUGINS );

		if ( obj instanceof Map ){

			Map p = (Map)obj;

			obj = p.get( name );

			if ( obj instanceof byte[]){

				return( new String((byte[])obj));
			}
		}

		return( null );
	}

	public static void
	setPluginMapProperty(
		TOTorrent		torrent,
		String			name,
		Map				value )
	{
		Map	m = getAzureusProperties( torrent );

		Object obj = m.get( TORRENT_AZ_PROP_PLUGINS );

		Map	p;

		if ( obj instanceof Map ){

			p = (Map)obj;

		}else{

			p = new HashMap();

			m.put( TORRENT_AZ_PROP_PLUGINS, p );
		}

		if ( value == null ){

			p.remove( name );

		}else{

			p.put( name, value );
		}
	}

	public static Map
	getPluginMapProperty(
		TOTorrent		torrent,
		String			name )
	{
		Map	m = getAzureusProperties( torrent );

		Object	obj = m.get( TORRENT_AZ_PROP_PLUGINS );

		if ( obj instanceof Map ){

			Map p = (Map)obj;

			obj = p.get( name );

			if ( obj instanceof Map ){

				return((Map)obj);
			}
		}

		return( null );
	}

	public static void
	setDHTBackupEnabled(
		TOTorrent		torrent,
		boolean			enabled )
	{
		Map	m = getAzureusProperties( torrent );

		m.put( TORRENT_AZ_PROP_DHT_BACKUP_ENABLE, new Long(enabled?1:0));
	}

	public static boolean
	getDHTBackupEnabled(
		TOTorrent	torrent )
	{
			// missing -> true

		Map	m = getAzureusProperties( torrent );

		Object	obj = m.get( TORRENT_AZ_PROP_DHT_BACKUP_ENABLE );

		if ( obj instanceof Long ){

			return( ((Long)obj).longValue() == 1 );
		}

		return( true );
	}

	public static boolean
	isDHTBackupRequested(
		TOTorrent	torrent )
	{
			// missing -> false

		Map	m = getAzureusProperties( torrent );

		Object obj = m.get( TORRENT_AZ_PROP_DHT_BACKUP_REQUESTED );

		if ( obj instanceof Long ){

			return( ((Long)obj).longValue() == 1 );
		}

		return( false );
	}

	public static void
	setDHTBackupRequested(
		TOTorrent		torrent,
		boolean			requested )
	{
		Map	m = getAzureusProperties( torrent );

		m.put( TORRENT_AZ_PROP_DHT_BACKUP_REQUESTED, new Long(requested?1:0));
	}


	public static boolean
	isReallyPrivate(
		TOTorrent torrent)
	{
		if ( torrent == null ){

			return( false );
		}

		URL url = torrent.getAnnounceURL();

		if ( url == null ){

			TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();

			if ( sets != null && sets.length > 0 ){

				URL[] urls = sets[0].getAnnounceURLs();

				if ( urls.length > 0 ){

					url = urls[0];
				}
			}
		}

		if ( url != null ){

			if ( UrlUtils.containsPasskey( url )){

				return torrent.getPrivate();
			}
		}

		return false;
	}

	public static boolean
	getPrivate(
		TOTorrent		torrent )
	{
		if ( torrent == null ){

			return( false );
		}

		return( torrent.getPrivate());
	}

	public static void
	setPrivate(
		TOTorrent		torrent,
		boolean			_private )
	{
		if ( torrent == null ){

			return;
		}

		try{
			torrent.setPrivate( _private );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

		// skip extensions


	public static Set<String>
	getSkipExtensionsSet()
	{
		return(getSkipExtensionsSetSupport(false));
	}

	private static synchronized Set<String>
	getSkipExtensionsSetSupport(
		boolean	force )
	{
		if ( skip_extensions_set == null || force ){

			Set<String>		new_skip_set	= new HashSet<>();

			String	skip_list = COConfigurationManager.getStringParameter( "File.Torrent.AutoSkipExtensions" );

			skip_list = skip_list.replace( ',', ';' );

			if ( skip_extensions_set == null ){

					// first time - add the listener

				COConfigurationManager.addParameterListener(
					"File.Torrent.AutoSkipExtensions",
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							String parameterName)
						{
							getSkipExtensionsSetSupport( true );
						}
					});
			}

			int	pos = 0;

			while( true ){

				int	p1 = skip_list.indexOf( ";", pos );

				String	bit;

				if ( p1 == -1 ){

					bit = skip_list.substring(pos);

				}else{

					bit	= skip_list.substring( pos, p1 );

					pos	= p1+1;
				}

				String ext = bit.trim().toLowerCase(); 	// use default locale as we're dealing with local file names

				if ( ext.startsWith(".")){

					ext = ext.substring(1);
				}

				if ( ext.length() > 0 ){

					new_skip_set.add( ext );
				}

				if ( p1 == -1 ){

					break;
				}
			}

			skip_extensions_set = new_skip_set;
		}

		return( skip_extensions_set );
	}

	public static Set<Object>
	getSkipFileSet()
	{
		return(getSkipFileSetSupport(false));
	}

	private static synchronized Set<Object>
	getSkipFileSetSupport(
		boolean	force )
	{
		if ( skip_file_set == null || force ){

			Set<Object>		new_skip_set	= new HashSet<>();

			String	skip_list 	= COConfigurationManager.getStringParameter( "File.Torrent.AutoSkipFiles" );
			boolean	regexpr		= COConfigurationManager.getBooleanParameter( "File.Torrent.AutoSkipFiles.RegExp" );

			skip_list = skip_list.replace( ',', ';' );

			if ( skip_file_set == null ){

					// first time - add the listener

				COConfigurationManager.addParameterListener(
					new String[]{ "File.Torrent.AutoSkipFiles", "File.Torrent.AutoSkipFiles.RegExp" },
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							String parameterName)
						{
							getSkipFileSetSupport( true );
						}
					});
			}

			int	pos = 0;

			while( true ){

				int	p1 = skip_list.indexOf( ";", pos );

				String	bit;

				if ( p1 == -1 ){

					bit = skip_list.substring(pos);

				}else{

					bit	= skip_list.substring( pos, p1 );

					pos	= p1+1;
				}

				bit = bit.trim();
				
				if ( !bit.isEmpty()){
					
					if ( regexpr ){
						
						try{
							
							new_skip_set.add( Pattern.compile( bit, Pattern.CASE_INSENSITIVE ));
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}else{
						
						new_skip_set.add( bit.toLowerCase());	// default locale here
					}
				}

				if ( p1 == -1 ){

					break;
				}
			}

			skip_file_set = new_skip_set;
		}

		return( skip_file_set );
	}
	
	/**
	 * 
	 * @param torrent
	 * @return null if no files skipped otherwise entry for each file in torrent
	 */
	
	public static boolean[]
	getSkipFiles(
		TOTorrent	torrent )
	{
		if ( torrent == null ){
			
			return( null );
		}
		
		TOTorrentFile[] tfiles = torrent.getFiles();
	
		Set<String>	skip_extensons 	= getSkipExtensionsSet();
		Set<Object>	skip_files 		= getSkipFileSet();
	
		long	skip_min_size = COConfigurationManager.getLongParameter( "File.Torrent.AutoSkipMinSizeKB" )*1024L;
	
		boolean[]	result = new boolean[tfiles.length];
		
		boolean has_skip = false;
		
		for (int i = 0; i < tfiles.length; i++){
			
			TOTorrentFile	torrentFile = tfiles[i];
	
			boolean	wanted = true;

			if ( torrentFile.isPadFile()){
				
				wanted = false;
				
			}else{
				
				String 	orgFullName = torrentFile.getRelativePath(); // translated to locale
				String	orgFileName = FileUtil.newFile(orgFullName).getName();
				
				if ( skip_min_size > 0 && torrentFile.getLength() < skip_min_size ){
		
					wanted = false;
		
				}else if ( skip_extensons.size() > 0 ){
		
					int	pos = orgFileName.lastIndexOf( '.' );
		
					if ( pos != -1 ){
		
						String	ext = orgFileName.substring( pos+1 );
		
						wanted = !skip_extensons.contains( ext );
					}
				}
				
				if ( wanted && !skip_files.isEmpty()){
				
					if ( skip_files.contains(orgFileName.toLowerCase())){
						
						wanted = false;
						
					}else{
						
						String sep = File.separator;
						
						for ( Object o: skip_files ){
							
							if ( o instanceof Pattern ){
							
								Pattern pattern = (Pattern)o;
								
								try{
																	
									boolean full_path = pattern.toString().contains( sep.equals( "\\" )?"\\\\":sep );
									
									if ( full_path ){
										
										String target = orgFullName;
											
										if ( !target.startsWith( sep )){
											
											target = sep + target;
										}
										
										if ( pattern.matcher( target ).find()){
											
											wanted = false;
											
											break;
										}
									}else{
										
										if ( pattern.matcher( orgFileName ).matches()){
											
											wanted = false;
											
											break;
										}
									}
								}catch( Throwable e ){
									
									Debug.out( e );
								}
							}else{
								
								String match = (String)o;
								
								boolean full_path =  match.contains( sep );
								
								if ( full_path ){
									
									String target = orgFullName;
									
									if ( !target.startsWith( sep )){
										
										target = sep + target;
									}
									
									if ( target.toLowerCase().contains( match )){
										
										wanted = false;
										
										break;
									}
								}else{
									
									if ( orgFileName.toLowerCase().equals( match )){
										
										wanted = false;
										
										break;
									}
								}
							}
						}
					}
				}
			}
	
			if ( !wanted ){
				
				result[i] = true;
				
				has_skip = true;
			}
		}
		
		return( has_skip?result:null );
	}

		// ignore files

	public static Set<String>
	getIgnoreSet()
	{
		return(getIgnoreSetSupport(false));
	}

	private static synchronized Set<String>
	getIgnoreSetSupport(
		boolean	force )
	{
		if ( ignore_files_set == null || force ){

			Set<String>		new_ignore_set	= new HashSet<>();

			String	ignore_list = COConfigurationManager.getStringParameter( "File.Torrent.IgnoreFiles" );

			if ( ignore_files_set == null ){

					// first time - add the listener

				COConfigurationManager.addParameterListener(
					"File.Torrent.IgnoreFiles",
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							String parameterName)
						{
							getIgnoreSetSupport( true );
						}
					});
			}

			int	pos = 0;

			while(true){

				int	p1 = ignore_list.indexOf( ";", pos );

				String	bit;

				if ( p1 == -1 ){

					bit = ignore_list.substring(pos);

				}else{

					bit	= ignore_list.substring( pos, p1 );

					pos	= p1+1;
				}

				String trimmedBit = bit.trim();

				if ( !trimmedBit.isEmpty() ){

					new_ignore_set.add(trimmedBit.toLowerCase());	// use default locale as we're dealing with local file names
				}

				if ( p1 == -1 ){

					break;
				}
			}

			ignore_files_set = new_ignore_set;
		}

		return( ignore_files_set );
	}



		// this class exists to minimise memory requirements by discarding the piece hash values
		// when "idle"

	private static final int	PIECE_HASH_TIMEOUT	= 3*60*1000;

	static final Map	torrent_delegates = new WeakHashMap();

	static{
		SimpleTimer.addPeriodicEvent(
			"TorrentUtils:pieceDiscard",
			PIECE_HASH_TIMEOUT/2,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent	event )
				{
					long	now = SystemTime.getCurrentTime();

					synchronized( torrent_delegates ){

						Iterator it = torrent_delegates.keySet().iterator();

						while( it.hasNext()){

							((torrentDelegate)it.next()).discardPieces(now,false);
						}
					}
				}
			});
	}

	static final HashSet	torrentFluffKeyset = new HashSet(2);
	static final Map		fluffThombstone = new HashMap(1);

	/**
	 * Register keys that are used for heavyweight maps that should be discarded when the torrent is not in use
	 * Make sure these keys are only ever used for Map objects!
	 */
	public static void
	registerMapFluff(
		String[]		fluff )
	{
		synchronized (TorrentUtils.class)
		{
			Collections.addAll(torrentFluffKeyset, fluff);
		}
	}

	public interface
	ExtendedTorrent
		extends TOTorrent
	{
		public byte[][]
		peekPieces()

			throws TOTorrentException;

		public void
		setDiscardFluff(
			boolean	discard );
	}

	public static class
	torrentDelegate
		extends LogRelation
		implements ExtendedTorrent
	{
		private final TOTorrent		delegate;
		private final File			file;

		private boolean			fluff_dirty;

		private long			last_pieces_read_time	= SystemTime.getCurrentTime();

		private URL							url_mod_last_pre;
		private URL							url_mod_last_post;
		private int							url_mod_last_seq;

		private List<URL>					urlg_mod_last_pre;
		private TOTorrentAnnounceURLGroup	urlg_mod_last_post;
		private int							urlg_mod_last_seq;

		protected
		torrentDelegate(
			TOTorrent		_delegate,
			File			_file )
		{
			delegate		= _delegate;
			file			= _file;

			synchronized( torrent_delegates ){

				torrent_delegates.put( this, null );
			}
		}

		@Override
		public void
		setDiscardFluff(
			boolean	discard )
		{
			if ( discard && !torrentFluffKeyset.isEmpty() ){

				//System.out.println( "Discarded fluff for " + new String(getName()));

				try{
			   		getMonitor().enter();

			   		try{
				   			// if file is out of sync with fluff then force a write

				   		if ( fluff_dirty ){

					   		boolean[]	restored = restoreState( true, true );

					   		delegate.serialiseToBEncodedFile( file );

					   		fluff_dirty = false;

					   		if ( restored[0] ){

					   			discardPieces( SystemTime.getCurrentTime(), true );
					   		}
				   		}

				   		for(Iterator it = torrentFluffKeyset.iterator();it.hasNext();){

				   			delegate.setAdditionalMapProperty( (String)it.next(), fluffThombstone );
				   		}
			   		}catch( Throwable e ){

			   			Debug.printStackTrace( e );
			   		}
				}finally{

					getMonitor().exit();
				}
			}
		}

		@Override
		public byte[]
		getName()
		{
			return( delegate.getName());
		}

		@Override
		public int 
		getTorrentType()
		{
			return( delegate.getTorrentType());
		}
		
		@Override
		public boolean 
		isExportable()
		{
			return( delegate.isExportable());
		}
		
		@Override
		public boolean
		updateExportability(
			TOTorrent		from )
		{
			return( delegate.updateExportability( from ));
		}
		
		@Override
		public boolean
		isSimpleTorrent()
		{
			return( delegate.isSimpleTorrent());
		}

		@Override
		public byte[]
		getComment()
		{
			return( delegate.getComment());
		}

		@Override
		public void
		setComment(
			String		comment )
		{
			delegate.setComment( comment );
		}

		@Override
		public long
		getCreationDate()
		{
			return( delegate.getCreationDate());
		}

		@Override
		public void
		setCreationDate(
			long		date )
		{
			delegate.setCreationDate( date );
		}

		@Override
		public byte[]
		getCreatedBy()
		{
			return( delegate.getCreatedBy());
		}

	 	@Override
	  public void
		setCreatedBy(
			byte[]		cb )
	   	{
	  		delegate.setCreatedBy( cb );
	   	}

		@Override
		public boolean
		isCreated()
		{
			return( delegate.isCreated());
		}

		@Override
		public boolean
		isDecentralised()
		{
    		URL	url = getAnnounceURLSupport();

    		return( TorrentUtils.isDecentralised( url ));
		}

	   	@Override
	    public URL
    	getAnnounceURL()
    	{
    		URL	url = getAnnounceURLSupport();

    		int	seq = dns_mapping_seq_count;

    		if ( 	url == url_mod_last_pre &&
    				url_mod_last_post != null &&
    				seq == url_mod_last_seq ){

    			// System.out.println( "using old url: " + url + " -> " + url_mod_last_post );

    			return( url_mod_last_post );
    		}

      		url_mod_last_post 		= applyDNSMods( url );
      		url_mod_last_pre		= url;
    		url_mod_last_seq		= seq;

    		return( url_mod_last_post );
    	}

       	@Override
        public TOTorrentAnnounceURLGroup
    	getAnnounceURLGroup()
    	{
       		TOTorrentAnnounceURLGroup group = getAnnounceURLGroupSupport();

       		int	seq = dns_mapping_seq_count;

       		if ( seq == urlg_mod_last_seq && urlg_mod_last_pre != null && urlg_mod_last_post != null ){

       			boolean	match = !(urlg_mod_last_post instanceof URLGroup && ((URLGroup)urlg_mod_last_post).hasBeenModified());

       			if ( match ){

	      			TOTorrentAnnounceURLSet[]	sets = group.getAnnounceURLSets();

	      			Iterator<URL>	it = urlg_mod_last_pre.iterator();

 outer:
	      			for (int i=0;i<sets.length;i++){

	      				URL[]	urls = sets[i].getAnnounceURLs();

	   					for ( int j=0;j<urls.length;j++){

	   						if ( !it.hasNext()){

	   							match = false;

	   							break outer;
	   						}

	   						if ( it.next() != urls[j] ){

	   							match = false;

	   							break;
	   						}
	      				}
	      			}

	      			if (it.hasNext()){

	      				match = false;
	      			}
       			}

      			if ( match ){

      	    		// System.out.println( "using old urlg: " + group + " -> " + urlg_mod_last_post );

      	    		return( urlg_mod_last_post );
      			}
       		}

       		List<URL>		url_list = new ArrayList<>();

 			TOTorrentAnnounceURLSet[]	sets = group.getAnnounceURLSets();

  			for (int i=0;i<sets.length;i++){

  				URL[]	urls = sets[i].getAnnounceURLs();

				  Collections.addAll(url_list, urls);
  			}

       		urlg_mod_last_post	= applyDNSMods( getAnnounceURL(), group );
       		urlg_mod_last_pre	= url_list;
       		urlg_mod_last_seq	= seq;

       		return( urlg_mod_last_post );
    	}

		public URL
		getAnnounceURLSupport()
		{
			return( delegate.getAnnounceURL());
		}

		@Override
		public boolean
		setAnnounceURL(
			URL		url )
		{
			return delegate.setAnnounceURL( url );
		}


		public TOTorrentAnnounceURLGroup
		getAnnounceURLGroupSupport()
		{
			return( delegate.getAnnounceURLGroup());
		}

		protected void
		discardPieces(
			long		now,
			boolean		force )
		{
				// handle clock changes backwards

			if ( now < last_pieces_read_time && !force ){

				last_pieces_read_time	= now;

			}else{

				try{
					if(		( now - last_pieces_read_time > PIECE_HASH_TIMEOUT || force ) &&
							delegate.getPieces() != null ){

						try{
							getMonitor().enter();

							// System.out.println( "clearing pieces for '" + new String(getName()) + "'");

							delegate.setPieces( null );
						}finally{

							getMonitor().exit();
						}
					}
				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		@Override
		public byte[][]
		getPieces()

			throws TOTorrentException
		{
			byte[][]	res = delegate.getPieces();

			last_pieces_read_time	= SystemTime.getCurrentTime();

			if ( res == null ){

				// System.out.println( "recovering pieces for '" + new String(getName()) + "'");

				try{
			   		getMonitor().enter();

			   		restoreState( true, false );

			   		res = delegate.getPieces();

				}finally{

					getMonitor().exit();
				}
			}

			return( res );
		}

		/**
		 * monitor must be held before calling me
		 * @param do_pieces
		 * @param do_fluff
		 * @throws TOTorrentException
		 */

		protected boolean[]
		restoreState(
			boolean		do_pieces,
			boolean		do_fluff )

			throws TOTorrentException
		{
	   		boolean	had_pieces = delegate.getPieces() != null;

	   		boolean	had_fluff = true;

	   		for(Iterator it = torrentFluffKeyset.iterator();it.hasNext();){

	   			had_fluff &= delegate.getAdditionalMapProperty( (String)it.next() ) != fluffThombstone;
	   		}

	   		if ( had_pieces ){

	   			do_pieces = false;
	   		}

	   		if ( had_fluff ){

	   			do_fluff = false;
	   		}

	   		if ( do_pieces || do_fluff ){

		   		TOTorrent	temp = readFromFile( file, false );

		   		if ( do_pieces ){

		   			byte[][] res	= temp.getPieces();

		   			delegate.setPieces( res );
		   		}

		   		if ( do_fluff ){

		   			for (Iterator it = torrentFluffKeyset.iterator(); it.hasNext();){

						String fluffKey = (String) it.next();

							// only update the discarded entries as non-discarded may be out of sync
							// with the file contents

						if ( delegate.getAdditionalMapProperty( fluffKey ) == fluffThombstone ){

							delegate.setAdditionalMapProperty(fluffKey, temp.getAdditionalMapProperty(fluffKey));
						}
					}
		   		}
	   		}

	   		return( new boolean[]{ do_pieces, do_fluff });
		}

			/**
			 * peeks the pieces, will return null if they are discarded
			 * @return
			 */

		@Override
		public byte[][]
		peekPieces()

			throws TOTorrentException
		{
			return( delegate.getPieces());
		}

		@Override
		public void
		setPieces(
			byte[][]	pieces )

			throws TOTorrentException
		{
			throw( new TOTorrentException( "Unsupported Operation", TOTorrentException.RT_WRITE_FAILS ));
		}

		@Override
		public long
		getPieceLength()
		{
			return( delegate.getPieceLength());
		}

		@Override
		public int
		getNumberOfPieces()
		{
			return( delegate.getNumberOfPieces());
		}

		@Override
		public long
		getSize()
		{
			return( delegate.getSize());
		}

		@Override
		public int
		getFileCount()
		{
			return( delegate.getFileCount());
		}

		@Override
		public TOTorrentFile[]
		getFiles()
		{
			return( delegate.getFiles());
		}

		@Override
		public byte[]
		getHash()

			throws TOTorrentException
		{
			return( delegate.getHash());
		}

		@Override
		public byte[]
		getFullHash(
			int		type ) 
			throws TOTorrentException
		{
			return( delegate.getFullHash( type ));
		}
		
		@Override
		public HashWrapper
		getHashWrapper()

			throws TOTorrentException
		{
			return( delegate.getHashWrapper());
		}

		@Override
		public TOTorrent 
		selectHybridHashType(
			int type ) 
			
			throws TOTorrentException
		{
			TOTorrent result;
			
				// make sure pieces are current

			try{
		   		getMonitor().enter();
	
		   		boolean[]	restored = restoreState( true, true );
	
		   		result = delegate.selectHybridHashType(type);
	
		   		if ( restored[0] ){
	
		   			discardPieces( SystemTime.getCurrentTime(), true );
		   		}
	
		   		if ( restored[1]){
	
					for (Iterator it = torrentFluffKeyset.iterator(); it.hasNext();){
	
						delegate.setAdditionalMapProperty((String) it.next(), fluffThombstone);
					}
				}
			}finally{
	
				getMonitor().exit();
			}
			
			return( result );
		}
		
	   	@Override
	    public void
    	setHashOverride(
    		byte[] hash )

    		throws TOTorrentException
    	{
    		throw( new TOTorrentException( "Not supported", TOTorrentException.RT_HASH_FAILS ));
    	}

		@Override
		public boolean
		getPrivate()
		{
			return( delegate.getPrivate());
		}

		@Override
		public void
		setPrivate(
			boolean	_private )

			throws TOTorrentException
		{
				// don't support this as it changes teh torrent hash

			throw( new TOTorrentException( "Can't amend private attribute", TOTorrentException.RT_WRITE_FAILS ));
		}

	  	@Override
	    public String
		getSource()
	   	{
	 		return( delegate.getSource());
	   	}

		@Override
	    public void
		setSource(
			String	str )
		
			throws TOTorrentException
	   	{
			throw( new TOTorrentException( "Can't amend source attribute", TOTorrentException.RT_WRITE_FAILS ));
		}
		
		@Override
		public boolean
		hasSameHashAs(
			TOTorrent		other )
		{
			return( delegate.hasSameHashAs( other ));
		}

		@Override
		public void
		setAdditionalStringProperty(
			String		name,
			String		value )
		{
			delegate.setAdditionalStringProperty( name, value );
		}

		@Override
		public String
		getAdditionalStringProperty(
			String		name )
		{
			return( delegate.getAdditionalStringProperty( name ));
		}

		@Override
		public void
		setAdditionalByteArrayProperty(
			String		name,
			byte[]		value )
		{
			delegate.setAdditionalByteArrayProperty( name, value );
		}

		@Override
		public byte[]
		getAdditionalByteArrayProperty(
			String		name )
		{
			return( delegate.getAdditionalByteArrayProperty( name ));
		}

		@Override
		public void
		setAdditionalLongProperty(
			String		name,
			Long		value )
		{
			delegate.setAdditionalLongProperty( name, value );
		}

		@Override
		public Long
		getAdditionalLongProperty(
			String		name )
		{
			return( delegate.getAdditionalLongProperty( name ));
		}


		@Override
		public void
		setAdditionalListProperty(
			String		name,
			List		value )
		{
			delegate.setAdditionalListProperty( name, value );
		}

		@Override
		public List
		getAdditionalListProperty(
			String		name )
		{
			return( delegate.getAdditionalListProperty( name ));
		}

		@Override
		public void
		setAdditionalMapProperty(
			String		name,
			Map			value )
		{
			if ( torrentFluffKeyset.contains(name)){

				//System.out.println( "Set fluff for " + new String(getName()) + " to " + value );

				try{
					getMonitor().enter();

					delegate.setAdditionalMapProperty( name, value );

					fluff_dirty = true;

				}finally{

					getMonitor().exit();
				}
			}else{

				delegate.setAdditionalMapProperty( name, value );
			}
		}

		@Override
		public Map
		getAdditionalMapProperty(
			String		name )
		{
			if (torrentFluffKeyset.contains(name)){

				try{
					getMonitor().enter();

					Map	result = delegate.getAdditionalMapProperty( name );

					if ( result == fluffThombstone ){

						try{
							restoreState( false, true );

							Map res = delegate.getAdditionalMapProperty( name );

							//System.out.println( "Restored fluff for " + new String(getName()) + " to " + res );

							return( res );

						}catch( Throwable e ){

							Debug.out( "Property '" + name + " lost due to torrent read error", e );
						}
					}
				}finally{

					getMonitor().exit();
				}
			}

			return( delegate.getAdditionalMapProperty( name ));
		}

		@Override
		public Object getAdditionalProperty(String name) {
			if (torrentFluffKeyset.contains(name))
			{
				try
				{
					getMonitor().enter();

					Object result = delegate.getAdditionalProperty(name);
					if (result == fluffThombstone)
					{
						try
						{
							restoreState(false, true);
							Object res = delegate.getAdditionalProperty(name);
							//System.out.println( "Restored fluff for " + new String(getName()) + " to " + res );

							return (res);

						} catch (Throwable e)
						{
							Debug.out("Property '" + name + " lost due to torrent read error", e);
						}
					}
				} finally
				{
					getMonitor().exit();
				}
			}

			return delegate.getAdditionalProperty(name);
		}

		@Override
		public void
		setAdditionalProperty(
			String		name,
			Object		value )
		{
			if ( torrentFluffKeyset.contains(name)){

				//System.out.println( "Set fluff for " + new String(getName()) + " to " + value );

				try{
					getMonitor().enter();

					delegate.setAdditionalProperty( name, value );

					fluff_dirty = true;

				}finally{

					getMonitor().exit();
				}
			}else{

				delegate.setAdditionalProperty( name, value );
			}
		}

		@Override
		public void
		removeAdditionalProperty(
			String name )
		{
			if(delegate.getAdditionalProperty(name) == null)
				return;

			if ( torrentFluffKeyset.contains(name)){

				//System.out.println( "Set fluff for " + new String(getName()) + " to " + value );

				try{
					getMonitor().enter();

					delegate.removeAdditionalProperty( name );

					fluff_dirty = true;

				}finally{

					getMonitor().exit();
				}
			}else{

				delegate.removeAdditionalProperty( name );
			}
		}

		@Override
		public void
		removeAdditionalProperties()
		{
			try{
				getMonitor().enter();

				delegate.removeAdditionalProperties();

				fluff_dirty = true;

			}finally{

				getMonitor().exit();
			}
		}

		@Override
		public void
		serialiseToBEncodedFile(
			File		target_file )

			throws TOTorrentException
		{
				// make sure pieces are current

			try{
		   		getMonitor().enter();

		   		boolean[]	restored = restoreState( true, true );

		   		delegate.serialiseToBEncodedFile( target_file );

		   		if ( target_file.equals( file )){

		   			fluff_dirty = false;
		   		}

		   		if ( restored[0] ){

		   			discardPieces( SystemTime.getCurrentTime(), true );
		   		}

		   		if ( restored[1] ){

		   			for (Iterator it = torrentFluffKeyset.iterator(); it.hasNext();){

		   				delegate.setAdditionalMapProperty( (String)it.next(), fluffThombstone );
		   			}
		   		}
			}finally{

				getMonitor().exit();
			}
		}


		@Override
		public Map
		serialiseToMap()

			throws TOTorrentException
		{
				// make sure pieces are current

			try{
		   		getMonitor().enter();

		   		boolean[]	restored = restoreState( true, true );

		   		Map	result = delegate.serialiseToMap();

		   		if ( restored[0] ){

		   			discardPieces( SystemTime.getCurrentTime(), true );
		   		}

		   		if ( restored[1]){

					for (Iterator it = torrentFluffKeyset.iterator(); it.hasNext();){

						delegate.setAdditionalMapProperty((String) it.next(), fluffThombstone);
					}
				}

		   		return( result );

			}finally{

				getMonitor().exit();
			}
		}


		@Override
		public void
		serialiseToXMLFile(
			File		target_file )

		   throws TOTorrentException
		{
				// make sure pieces are current

			try{
		   		getMonitor().enter();

		   		boolean[]	restored = restoreState( true, true );

		   		delegate.serialiseToXMLFile( target_file );

		   		if ( restored[0] ){

		   			discardPieces( SystemTime.getCurrentTime(), true );
		   		}

		   		if ( restored[1]){

					for (Iterator it = torrentFluffKeyset.iterator(); it.hasNext();){

						delegate.setAdditionalMapProperty((String) it.next(), fluffThombstone);
					}
				}
			}finally{

				getMonitor().exit();
			}
		}

	 	@Override
	  public void
		addListener(
			TOTorrentListener		l )
		{
	 		delegate.addListener( l );
		}

		@Override
		public void
		removeListener(
			TOTorrentListener		l )
		{
	 		delegate.removeListener( l );
		}

		@Override
		public AEMonitor
		getMonitor()
		{
	   		return( delegate.getMonitor());
		}


		@Override
		public void
		print()
		{
			delegate.print();
		}

		@Override
		public String getRelationText() {
			if (delegate instanceof LogRelation)
				return ((LogRelation)delegate).getRelationText();
			return delegate.toString();
		}

		@Override
		public Object[] getQueryableInterfaces() {
			if (delegate instanceof LogRelation)
				return ((LogRelation)delegate).getQueryableInterfaces();
			return super.getQueryableInterfaces();
		}

		@Override
		public String getUTF8Name() {
			return delegate.getUTF8Name();
		}
	}

	/**
	 * Copy a file to the Torrent Save Directory, taking into account all the
	 * user config options related to that.
	 * <p>
	 * Also makes the directory if it doesn't exist.
	 *
	 * @param f File to copy
	 * @param persistent Whether the torrent is persistent
	 * @return File after it's been copied (may be the same as f)
	 * @throws IOException
	 */
	public static File copyTorrentFileToSaveDir(File f, boolean persistent)
			throws IOException {
		File torrentDir;
		boolean saveTorrents = persistent
				&& COConfigurationManager.getBooleanParameter("Save Torrent Files");
		if (saveTorrents)
			torrentDir = FileUtil.newFile(COConfigurationManager
					.getDirectoryParameter("General_sDefaultTorrent_Directory"));
		else
			torrentDir = FileUtil.newFile(f.getParent());

		//if the torrent is already in the completed files dir, use this
		//torrent instead of creating a new one in the default dir
		boolean moveWhenDone = COConfigurationManager.getBooleanParameter("Move Completed When Done");
		String completedDir = COConfigurationManager.getStringParameter(
				"Completed Files Directory", "");
		if (moveWhenDone && completedDir.length() > 0) {
			File cFile = FileUtil.newFile(completedDir, f.getName());
			if (cFile.exists()) {
				//set the torrentDir to the completedDir
				torrentDir = FileUtil.newFile(completedDir);
			}
		}

		FileUtil.mkdirs(torrentDir);

		File fDest = FileUtil.newFile(torrentDir, f.getName().replaceAll("%20", "."));
		if (fDest.equals(f)) {
			return f;
		}

		while (fDest.exists()) {
			fDest = FileUtil.newFile(torrentDir, "_" + fDest.getName());
		}

		fDest.createNewFile();

		if (!FileUtil.copyFile(f, fDest)) {
			throw new IOException("File copy failed");
		}

		if ( shouldDeleteTorrentFileAfterAdd( f, persistent )){

			f.delete();
		}

		return fDest;
	}

	public static boolean
	shouldDeleteTorrentFileAfterAdd(
		File		f,
		boolean		persistent )
	{
		if ( !persistent ){

			return( false );
		}

		boolean delTorrents = COConfigurationManager.getBooleanParameter("Delete Original Torrent Files");

		if ( !delTorrents ){

			return( false );
		}

		boolean saveTorrents = COConfigurationManager.getBooleanParameter("Save Torrent Files");

		if ( saveTorrents ){

			try{
				File torrentDir = FileUtil.newFile(COConfigurationManager.getDirectoryParameter("General_sDefaultTorrent_Directory"));

				if ( torrentDir.isDirectory() && torrentDir.equals( f.getParentFile())){

					return( false );
				}
			}catch( Throwable e ){

				return( false );
			}
		}
		
		File parent = f.getParentFile();

		File active_dir = FileUtil.getUserFile( "active" );

		if ( active_dir.equals( parent )){
			
			return( false );
		}
		
		File shares_dir = FileUtil.getUserFile( "shares" );

		if ( shares_dir.equals( parent ) || shares_dir.equals( parent.getParentFile())){
			
			return( false );
		}
		
		return( true );
	}

	/**
	 * Get the DownloadManager related to a torrent's hashBytes
	 *
	 * @param hashBytes
	 * @return
	 */
  public static DownloadManager getDownloadManager( HashWrapper	hash ) {
		try {
			return CoreFactory.getSingleton().getGlobalManager()
					.getDownloadManager(hash);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Deletes the given dir and all dirs underneath if empty.
	 * Don't delete default save path or completed files directory, however,
	 * allow deletion of their empty subdirectories
	 * Files defined to be ignored for the sake of torrent creation are automatically deleted
	 * For example, by default this includes thumbs.db
	 */
	public static void recursiveEmptyDirDelete(File f) {
		TorrentUtils.recursiveEmptyDirDelete(f, true);
	}

	/**
	 * Same as #recursiveEmptyDirDelete(File), except allows disabling of logging
	 * of any warnings
	 *
	 * @param f Dir to delete
	 * @param log_warnings Whether to log warning
	 */
	public static void recursiveEmptyDirDelete(File f, boolean log_warnings) {
		Set ignore_map = getIgnoreSet();

		FileUtil.recursiveEmptyDirDelete(f, ignore_map, log_warnings);
	}

	/**
	 * A nice string of a Torrent's hash
	 *
	 * @param torrent Torrent to fromat hash of
	 * @return Hash string in a nice format
	 */
	public static String nicePrintTorrentHash(TOTorrent torrent) {
		return nicePrintTorrentHash(torrent, false);
	}

	/**
	 * A nice string of a Torrent's hash
	 *
	 * @param torrent Torrent to fromat hash of
	 * @param tight No spaces between groups of numbers
	 *
	 * @return Hash string in a nice format
	 */
	public static String nicePrintTorrentHash(TOTorrent torrent, boolean tight) {
		byte[] hash;

		if (torrent == null) {

			hash = new byte[20];
		} else {
			
			hash = getHashForDisplay( torrent );

			if ( hash == null ){

				hash = new byte[20];
			}
		}

		return (ByteFormatter.nicePrint(hash, tight));
	}

	/**
	 * Runs a file through a series of test to verify if it is a torrent.
	 *
	 * @param filename File to test
	 * @return true - file is a valid torrent file
	 *
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static boolean isTorrentFile(String filename) throws FileNotFoundException, IOException {
	  File check = FileUtil.newFile(filename);
	  if (!check.exists())
	    throw new FileNotFoundException("File "+filename+" not found.");
	  if (!check.canRead())
	    throw new IOException("File "+filename+" cannot be read.");
	  if (check.isDirectory())
	    throw new FileIsADirectoryException("File "+filename+" is a directory.");
	  try {
	    TOTorrentFactory.deserialiseFromBEncodedFile(check);
	    return true;
	  } catch (Throwable e) {
	    return false;
	  }
	}

	public static void
	addCreatedTorrent(
		TOTorrent		torrent )
	{
		synchronized( created_torrents ){

			try{
				byte[]	hash = torrent.getHash();

				HashWrapper	hw = new HashWrapper( hash );

				boolean dirty = false;

				long check = COConfigurationManager.getLongParameter("my.created.torrents.check", 0  );

				COConfigurationManager.setParameter("my.created.torrents.check", check+1  );

				if ( check % 200 == 0 ){

					try{
						List<DownloadManager> dms = CoreFactory.getSingleton().getGlobalManager().getDownloadManagers();

						Set<HashWrapper> actual_hashes = new HashSet<>();

						for ( DownloadManager dm: dms ){

							TOTorrent t = dm.getTorrent();

							if ( t != null ){

								try{
									actual_hashes.add( t.getHashWrapper());

								}catch( Throwable e ){

								}
							}
						}

						Iterator<byte[]> it = created_torrents.iterator();

						int deleted = 0;

						while( it.hasNext()){

							HashWrapper	existing_hw = new HashWrapper( it.next());

							if ( !actual_hashes.contains( existing_hw ) && !existing_hw.equals( hw )){

								it.remove();

								created_torrents_set.remove( existing_hw );

								deleted++;

								dirty = true;
							}
						}
					}catch( Throwable e ){
					}
				}

				//System.out.println( "addCreated:" + new String(torrent.getName()) + "/" + ByteFormatter.encodeString( hash ));

				if ( created_torrents.size() == 0 ){

					COConfigurationManager.setParameter( "my.created.torrents", created_torrents );
				}

				if ( !created_torrents_set.contains( hw )){

					created_torrents.add( hash );

					created_torrents_set.add( hw );

					dirty = true;
				}

				if ( dirty ){

					COConfigurationManager.setDirty();
				}
			}catch( TOTorrentException e ){

			}
		}
	}

	public static void
	removeCreatedTorrent(
		TOTorrent		torrent )
	{
		synchronized( created_torrents ){

			try{
				HashWrapper	hw = torrent.getHashWrapper();

				byte[]		hash	= hw.getBytes();

				//System.out.println( "removeCreated:" + new String(torrent.getName()) + "/" + ByteFormatter.encodeString( hash ));

				Iterator<byte[]>	it = created_torrents.iterator();

				while( it.hasNext()){

					byte[]	h = it.next();

					if ( Arrays.equals( hash, h )){

						it.remove();
					}
				}

				COConfigurationManager.setDirty();

				created_torrents_set.remove( hw );

			}catch( TOTorrentException e ){

			}
		}
	}

	public static boolean
	isCreatedTorrent(
		TOTorrent		torrent )
	{
		synchronized( created_torrents ){

			try{
				HashWrapper	hw = torrent.getHashWrapper();

				boolean	res = created_torrents_set.contains( hw );

					// if we don't have a persistent record of creation, check the non-persisted version

				if ( !res ){

					res = torrent.isCreated();
				}

				// System.out.println( "isCreated:" + new String(torrent.getName()) + "/" + ByteFormatter.encodeString( hw.getBytes()) + " -> " + res );

				return( res );

			}catch( TOTorrentException e ){

				Debug.printStackTrace(e);

				return( false );

			}
		}
	}

	public static TOTorrent
	download(
		URL		url )

		throws IOException
	{
		return( download( url, 0 ));
	}

	public static TOTorrent
	download(
		URL		url,
		long	timeout )

		throws IOException
	{
		try{
			PluginProxy	plugin_proxy = null;

			try{
				if ( AENetworkClassifier.categoriseAddress( url.getHost()) != AENetworkClassifier.AT_PUBLIC ){

					plugin_proxy = AEProxyFactory.getPluginProxy( "torrent download", url );
				}

				ResourceDownloader rd;

				if ( plugin_proxy == null ){

					rd = new ResourceDownloaderFactoryImpl().create( url );

				}else{

					rd = new ResourceDownloaderFactoryImpl().create( plugin_proxy.getURL(), plugin_proxy.getProxy());

					rd.setProperty( "URL_HOST", url.getHost());
				}

				if ( timeout > 0 ){

					rd.setProperty( "URL_Connect_Timeout", timeout );
					rd.setProperty( "URL_Read_Timeout", timeout );
				}

				byte[] bytes = FileUtil.readInputStreamAsByteArray( rd.download(), BDecoder.MAX_BYTE_ARRAY_SIZE );

				return( TOTorrentFactory.deserialiseFromBEncodedByteArray( bytes ));

			}finally{

				if (plugin_proxy != null ){

					plugin_proxy.setOK( true );
				}
			}
		}catch( IOException e ){

			throw((IOException)e);

		}catch( Throwable e ){

			throw( new IOException( Debug.getNestedExceptionMessage( e )));
		}
	}

	private static void
	fireAttributeListener(
		TOTorrent		torrent,
		String			attribute,
		Object			value )
	{
		Iterator it = torrent_attribute_listeners.iterator();

		while( it.hasNext()){

			try{
				((torrentAttributeListener)it.next()).attributeSet(torrent, attribute, value);

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	public static void
	addTorrentAttributeListener(
		torrentAttributeListener	listener )
	{
		torrent_attribute_listeners.add( listener );
	}

	public static void
	removeTorrentAttributeListener(
		torrentAttributeListener	listener )
	{
		torrent_attribute_listeners.remove( listener );
	}

	public static void
	addTorrentURLChangeListener(
		TorrentAnnounceURLChangeListener	listener )
	{
		torrent_url_changed_listeners.add( listener );
	}

	public static void
	removeTorrentURLChangeListener(
		TorrentAnnounceURLChangeListener	listener )
	{
		torrent_url_changed_listeners.remove( listener );
	}



	private static final Pattern txt_pattern = Pattern.compile( "(UDP|TCP):([0-9]+)");

	public static DNSTXTEntry
	getDNSTXTEntry(
		URL		url )
	{
		if ( isDecentralised( url )){

			return( null );
		}

		String host = url.getHost();

		String	tracker_network	= AENetworkClassifier.categoriseAddress( host );

		if ( tracker_network != AENetworkClassifier.AT_PUBLIC ){

			return( null );
		}

		return( getDNSTXTEntry( host, false, null ));
	}

	private static void
	checkDNSTimeouts()
	{
		final List<String>	hosts = new ArrayList<>();

		long now = SystemTime.getMonotonousTime();

		synchronized( dns_mapping ){

			for ( Map.Entry<String,DNSTXTEntry> entry: dns_mapping.entrySet()){

				DNSTXTEntry txt_entry = entry.getValue();

				if ( now - txt_entry.getCreateTime() > DNS_HISTORY_TIMEOUT ){

					hosts.add( entry.getKey());
				}
			}
		}

		if ( hosts.size() > 0 ){

			new AEThread2( "DNS:updates" )
			{
				@Override
				public void
				run()
				{
					for ( String host: hosts ){

						getDNSTXTEntry( host, true, null );
					}
				}
			}.start();
		}
	}

	private static DNSTXTEntry
	getDNSTXTEntry(
		final String			host,
		boolean					force_update,
		final List<String>		already_got_records )
	{
		if ( TRACE_DNS ){
			System.out.println( "Getting DNS records for " + host + ", force=" + force_update + ", got=" + already_got_records );
		}

		DNSTXTEntry		txt_entry;
		DNSTXTEntry		old_txt_entry;

		boolean			is_new = false;

		synchronized( dns_mapping ){

			old_txt_entry = txt_entry = dns_mapping.get( host );

			if ( txt_entry != null && SystemTime.getMonotonousTime() - txt_entry.getCreateTime() > DNS_HISTORY_TIMEOUT ){

				force_update = true;
			}

			if ( force_update || txt_entry == null ){

				txt_entry = new DNSTXTEntry();

				dns_mapping.put( host, txt_entry );

				is_new = true;
			}
		}

		if ( is_new ){

			String _config_key = "";

			try{
				_config_key = "dns.txts.cache." + Base32.encode( host.getBytes( "UTF-8" ));

			}catch( Throwable e ){

				Debug.out( e );
			}

			final String config_key	= _config_key;

			if ( TRACE_DNS ){
				System.out.println( "Updating DNS records for " + host );
			}

			try{
				List<String> txts;

				if ( already_got_records != null ){

					txts = already_got_records;

				}else{

					final AESemaphore lookup_sem = new AESemaphore( "DU:ls" );

					final Object[]	result = { null, null };

					final DNSTXTEntry	f_txt_entry = txt_entry;

					dns_threads.run(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								try{
									List<String> txts = dns_utils.getTXTRecords( host );

									if ( TRACE_DNS ){
										System.out.println( "Actual lookup: " + host + " -> " + txts );
									}

									synchronized( result ){

										if ( result[0] == null ){

											result[1] = txts;

											return;
										}
									}

										// they gave up waiting

									try{
										List txts_cache = new ArrayList();

										for ( String str: txts ){

											txts_cache.add( str.getBytes( "UTF-8" ));
										}

										List old_txts_cache = COConfigurationManager.getListParameter( config_key, null );

										boolean	same = false;

										if ( old_txts_cache != null ){

											same = old_txts_cache.size() == txts_cache.size();

											if ( same ){

												for ( int i=0;i<old_txts_cache.size();i++){

													if ( !Arrays.equals((byte[])old_txts_cache.get(i),(byte[])txts_cache.get(i))){

														same = false;

														break;
													}
												}
											}
										}

										if ( !same ){

											COConfigurationManager.setParameter( config_key, txts_cache );

											f_txt_entry.getSemaphore().reserve();

											if ( already_got_records == null ){

												getDNSTXTEntry( host, true, txts );
											}
										}
									}catch( Throwable e ){

										Debug.out( e );
									}

								}finally{

									lookup_sem.release();
								}
							}
						});

					List txts_cache = COConfigurationManager.getListParameter( config_key, null );

						// if we have a cache and this isn't a force update and start of day then well just go with the cache

					if ( old_txt_entry != null || txts_cache == null || force_update ){

						lookup_sem.reserve( 2500 );
					}

					synchronized( result ){

						result[0] = "";

						txts = (List<String>)result[1];
					}

					try{
						if ( txts == null ){

							txts = new ArrayList<>();

							if ( txts_cache == null ){

								if ( TRACE_DNS ){
									System.out.println( "    No cache" );
								}
							}else{

								for ( Object o: txts_cache ){

									txts.add( new String((byte[])o, "UTF-8" ));
								}

								if ( TRACE_DNS ){
									System.out.println( "    Using cache: " + txts );
								}
							}
						}else{

							txts_cache = new ArrayList();

							for ( String str: txts ){

								txts_cache.add( str.getBytes( "UTF-8" ));
							}

							COConfigurationManager.setParameter( config_key, txts_cache );
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}

				boolean	found_bt = false;

				for ( String txt: txts ){

					if ( txt.startsWith( "BITTORRENT" )){

						found_bt = true;

						Matcher matcher = txt_pattern.matcher( txt.substring( 10 ));

						while( matcher.find()){

							boolean is_tcp	= matcher.group(1).startsWith( "T" );
							Integer	port	= Integer.parseInt( matcher.group(2));

							txt_entry.addPort( is_tcp, port );
						}
					}
				}

				txt_entry.setHasRecords( found_bt );

				if ( old_txt_entry == null ){

					dns_mapping_seq_count++;

					if ( TRACE_DNS ){
						System.out.println( "    New DNS override for " + host + ": " + txt_entry.getString());
					}
				}else{

					if ( !old_txt_entry.sameAs( txt_entry )){

						if ( TRACE_DNS ){
							System.out.println( "    Updated DNS override for " + host + ": " + txt_entry.getString());
						}

						dns_mapping_seq_count++;

						dispatcher.dispatch(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									for ( TorrentAnnounceURLChangeListener l: torrent_url_changed_listeners ){

										try{
											l.changed();

										}catch( Throwable e ){

											Debug.out(e );
										}
									}
								}
							});
					}
				}
			}finally{

				txt_entry.getSemaphore().releaseForever();
			}
		}

		txt_entry.getSemaphore().reserve();

		return( txt_entry );
	}

	private static URL
	applyDNSMods(
		URL		url )
	{
		if ( DNS_HANDLING_ENABLE ){

			DNSTXTEntry txt_entry = getDNSTXTEntry( url );

			if ( txt_entry != null && txt_entry.hasRecords()){

				boolean url_is_tcp 	= url.getProtocol().toLowerCase().startsWith( "http" );
				int		url_port	= url.getPort();

				if ( url_port == -1 ){

					url_port = url.getDefaultPort();
				}

				List<DNSTXTPortInfo>	ports = txt_entry.getPorts();

				if ( ports.size() == 0 ){

					return( UrlUtils.setHost( url, url.getHost() + ".disabled_by_tracker" ));

				}else{

					DNSTXTPortInfo	first_port 	= ports.get(0);

					int target_port = first_port.getPort();
					
					if ( url_port != target_port ){

						url = UrlUtils.setPort( url, target_port==url.getDefaultPort()?0:target_port );
					}

					if ( url_is_tcp == first_port.isTCP()){

						return( url );

					}else{

						return( UrlUtils.setProtocol( url, first_port.isTCP()?"http":"udp" ));
					}
				}
			}else{

				return( url );
			}
		}else{

			return( url );
		}
	}

	private static List<URL>
	applyAllDNSMods(
		URL		url )
	{
		List<URL> default_result = new ArrayList<>(1);
		
		default_result.add( url );
		
		if ( DNS_HANDLING_ENABLE ){

			DNSTXTEntry txt_entry = getDNSTXTEntry( url );

			if ( txt_entry != null && txt_entry.hasRecords()){		

				boolean url_is_tcp 	= url.getProtocol().toLowerCase().startsWith( "http" );
				int		url_port	= url.getPort();

				if ( url_port == -1 ){

					url_port = url.getDefaultPort();
				}

				List<DNSTXTPortInfo>	ports = txt_entry.getPorts();

				if ( ports.size() == 0 ){

					return( null );	// NULL here as no valid ports

				}else{

					List<URL>	result = new ArrayList<>();

					for ( DNSTXTPortInfo port: ports ){

						URL	mod_url = url;

						int target_port = port.getPort();
						
						if ( url_port != target_port ){

							mod_url = UrlUtils.setPort( mod_url, target_port==mod_url.getDefaultPort()?0:target_port );
						}

						if ( url_is_tcp != port.isTCP()){

							mod_url = UrlUtils.setProtocol( mod_url, port.isTCP()?"http":"udp" );
						}

						result.add( mod_url );
					}

					return( result );
				}
			}else{

				return( default_result );
			}
		}else{

			return( default_result );
		}
	}

	private static TOTorrentAnnounceURLGroup
	applyDNSMods(
		URL								announce_url,
		TOTorrentAnnounceURLGroup		group )
	{
		if ( DNS_HANDLING_ENABLE ){

				// linked hash map to retain set order as best as possible
			
			Map<String,Object[]>	dns_maps = new LinkedHashMap<>();

			DNSTXTEntry announce_txt_entry = getDNSTXTEntry( announce_url );

			if ( announce_txt_entry != null && announce_txt_entry.hasRecords()){

				dns_maps.put( announce_url.getHost(), new Object[]{ announce_url, announce_txt_entry, -1 });
			}

			TOTorrentAnnounceURLSet[] sets = group.getAnnounceURLSets();

			List<TOTorrentAnnounceURLSet>	mod_sets = new ArrayList<>();

			int	set_index = 0;
			
			for ( TOTorrentAnnounceURLSet set: sets ){

				URL[] urls = set.getAnnounceURLs();

				List<URL>	mod_urls = new ArrayList<>();

				for ( URL url: urls ){

					DNSTXTEntry txt_entry = getDNSTXTEntry( url );

					if ( txt_entry == null || !txt_entry.hasRecords()){

						mod_urls.add( url );

					}else{

							// remove any affected entries here, we'll add them in if needed later

						dns_maps.put( url.getHost(), new Object[]{ url, txt_entry, set_index });
					}
				}

				if ( mod_urls.size() != urls.length ){

					if ( mod_urls.size() > 0 ){

						mod_sets.add( group.createAnnounceURLSet( mod_urls.toArray( new URL[ mod_urls.size()])));
					}
				}else{

					mod_sets.add( set );
				}
				
				set_index++;
			}

			if ( dns_maps.size() > 0 ){

				for( Map.Entry<String,Object[]> entry: dns_maps.entrySet()){

					Object[] stuff		= entry.getValue();

					URL			url 	= (URL)stuff[0];
					DNSTXTEntry	dns		= (DNSTXTEntry)stuff[1];
					
					List<DNSTXTPortInfo> ports = dns.getPorts();

					if ( ports.size() > 0 ){

						List<URL>	urls = new ArrayList<>();

						for ( DNSTXTPortInfo port: ports ){

							int		url_port 	= url.getPort();
							boolean url_is_tcp 	= url.getProtocol().toLowerCase().startsWith( "http" );

							int target_port = port.getPort();
							
							if ( url_port != target_port ){

								url = UrlUtils.setPort( url, target_port==url.getDefaultPort()?0:target_port );
							}

							if ( url_is_tcp != port.isTCP()){

								url = UrlUtils.setProtocol( url, port.isTCP()?"http":"udp" );
							}

							urls.add( url );
						}

						if ( urls.size() > 0 ){

							int			index 	= (Integer)stuff[2];

							TOTorrentAnnounceURLSet mod_set = group.createAnnounceURLSet( urls.toArray( new URL[ urls.size()]));
							
							if ( index < 0 || index > mod_sets.size()){
							
								mod_sets.add( mod_set );
								
							}else{
								
								mod_sets.add( index, mod_set );
							}
						}
					}
				}

				return( new URLGroup( group, mod_sets ));

			}else{

				return( group );
			}
		}else{

			return( group );
		}
	}

	public interface
	torrentAttributeListener
	{
		public void
		attributeSet(
			TOTorrent	torrent,
			String		attribute,
			Object		value );
	}

	public interface
	TorrentAnnounceURLChangeListener
	{
		public void
		changed();
	}

	private static class
	URLGroup
		implements TOTorrentAnnounceURLGroup
	{
		private final TOTorrentAnnounceURLGroup		delegate;
		private TOTorrentAnnounceURLSet[]		sets;

		private boolean modified;

		private long uid = TorrentUtils.getAnnounceGroupUID();

		private
		URLGroup(
			TOTorrentAnnounceURLGroup		_delegate,
			List<TOTorrentAnnounceURLSet>	mod_sets )
		{
			delegate	= _delegate;

			sets = mod_sets.toArray( new TOTorrentAnnounceURLSet[mod_sets.size()]);
		}
		
		@Override
		public long 
		getUID()
		{
			return( uid );
		}
		
		@Override
		public TOTorrentAnnounceURLSet[]
       	getAnnounceURLSets()
		{
			return( sets );
		}

       	@Override
        public void
       	setAnnounceURLSets(
       		TOTorrentAnnounceURLSet[]	_sets )
       	{       		
       		modified = true;

       		sets = _sets;

       		uid = TorrentUtils.getAnnounceGroupUID();

       		delegate.setAnnounceURLSets(_sets );
       	}

       	@Override
        public TOTorrentAnnounceURLSet
       	createAnnounceURLSet(
       		URL[]	urls )
       	{
       		return( delegate.createAnnounceURLSet( urls ));
       	}

       	protected boolean
       	hasBeenModified()
       	{
       		return( modified );
       	}
	}

	public static class
	DNSTXTEntry
	{
		private final long					create_time = SystemTime.getMonotonousTime();

		private final AESemaphore				sem = new AESemaphore( "DNSTXTEntry" );

		private boolean					has_records;
		private final List<DNSTXTPortInfo>	ports = new ArrayList<>();

		private long
		getCreateTime()
		{
			return( create_time );
		}

		private AESemaphore
		getSemaphore()
		{
			return( sem );
		}

		private void
		setHasRecords(
			boolean	has )
		{
			has_records = has;
		}

		public boolean
		hasRecords()
		{
			return( has_records );
		}

		private void
		addPort(
			boolean	is_tcp,
			int		port )
		{
			ports.add( new DNSTXTPortInfo( is_tcp, port ));
		}

		private List<DNSTXTPortInfo>
		getPorts()
		{
			return( ports );
		}

		private boolean
		sameAs(
			DNSTXTEntry		other )
		{
			if ( has_records != other.has_records ){

				return( false );
			}

			if ( ports.size() != other.ports.size()){

				return( false );
			}

			for ( int i=0;i<ports.size();i++ ){

				if ( !ports.get(i).sameAs( other.ports.get(i))){

					return( false );
				}
			}

			return( true );
		}

		public String
		getString()
		{
			if ( has_records ){

				if ( ports.size() == 0 ){

					return( "Deny all" );

				}else{

					String	res = "";

					for ( DNSTXTPortInfo port: ports ){

						res += (res.length()==0?"":", ") + port.getString();
					}

					return( "Permit " + res );
				}
			}else{

				return( "No records" );
			}
		}
	}

	private static class
	DNSTXTPortInfo
	{
		private final boolean	is_tcp;
		private final int		port;

		private
		DNSTXTPortInfo(
			boolean	_is_tcp,
			int		_port )
		{
			is_tcp 	= _is_tcp;
			port	= _port;
		}

		private boolean
		sameAs(
			DNSTXTPortInfo		other )
		{
			return( is_tcp == other.is_tcp && port == other.port );
		}

		private boolean
		isTCP()
		{
			return( is_tcp );
		}

		private int
		getPort()
		{
			return( port );
		}

		private String
		getString()
		{
			return( (is_tcp?"TCP" :"UDP ") + port );
		}
	}
	
	private static CopyOnWriteList<PotentialTorrentDeletionListener>		ptd_listeners 	= new CopyOnWriteList<>();
	private static Map<DownloadManager, Integer>							ptd_map			= new IdentityHashMap<>();
	private static Map<PotentialTorrentDeletionListener,DownloadManager[]>	ptdl_last		= new HashMap<>();
	
	public static void
	startTorrentDelete(
		DownloadManager[]		dms )
	{
		torrent_delete_level.incrementAndGet();

		synchronized( ptd_map ){
			
			for ( DownloadManager dm: dms ){
				
				if ( dm == null ){
					
					continue;
				}
				
				Integer num = ptd_map.get( dm );
				
				if ( num == null ){
					
					ptd_map.put( dm, 1 );
					
				}else{
					
					ptd_map.put( dm, num + 1 );
				}
			}
			
			DownloadManager[] new_dms = ptd_map.keySet().toArray( new DownloadManager[ ptd_map.size()] );
			
			for ( PotentialTorrentDeletionListener l: ptd_listeners ){
				
				DownloadManager[] old_dms = ptdl_last.get( l );
				
				ptdl_last.put( l, new_dms );
				
				l.potentialDeletionChanged( old_dms==null?new DownloadManager[0]:old_dms, new_dms );
			}
		}
	}

	public static void
	endTorrentDelete(
		DownloadManager[]		dms )
	{
		torrent_delete_level.decrementAndGet();

		synchronized( ptd_map ){
			
			for ( DownloadManager dm: dms ){
				
				if ( dm == null ){
					
					continue;
				}

				Integer num = ptd_map.get( dm );
				
				if ( num != null ){
															
					if ( num == 1 ){
						
						ptd_map.remove( dm );
						
					}else{
					
						ptd_map.put( dm, num - 1 );
					}
				}
			}
			
			DownloadManager[] new_dms = ptd_map.keySet().toArray( new DownloadManager[ ptd_map.size()] );
			
			for ( PotentialTorrentDeletionListener l: ptd_listeners ){
				
				DownloadManager[] old_dms = ptdl_last.get( l );
				
				ptdl_last.put( l, new_dms );
				
				l.potentialDeletionChanged( old_dms==null?new DownloadManager[0]:old_dms, new_dms );
			}
		}
	}

	public static void
	runTorrentDelete(
		DownloadManager[]		dms,
		Runnable 				target )
	{
		DownloadManager[] current_dms = dms.clone();
		
		try{
			startTorrentDelete( current_dms );

			target.run();

		}finally{

			endTorrentDelete( current_dms );
		}
	}

	public interface
	PotentialTorrentDeletionListener
	{
		public void
		potentialDeletionChanged(
			DownloadManager[]		old_dms,
			DownloadManager[]		new_dms );	
	}
	
	public static void
	addPotentialTorrentDeletionListener(
		PotentialTorrentDeletionListener		l )
	{
		ptd_listeners.add( l );
	}
	
	public static void
	removePotentialTorrentDeletionListener(
		PotentialTorrentDeletionListener		l )
	{	
		ptd_listeners.remove( l );
	
		synchronized( ptd_map ){

			ptdl_last.remove( l );
		}
	}
	
	
	public static boolean
	isTorrentDeleting()
	{
		return( torrent_delete_level.get() > 0 );
	}

	public static void
	setTorrentDeleted()
	{
		synchronized( TorrentUtils.class ){

			torrent_delete_time = SystemTime.getMonotonousTime();
		}
	}

	public static long
	getMillisecondsSinceLastTorrentDelete()
	{
		synchronized( TorrentUtils.class ){

			if ( torrent_delete_time == 0 ){

				return( Long.MAX_VALUE );
			}
			return( SystemTime.getMonotonousTime() - torrent_delete_time );
		}
	}
	
	public static boolean
	propagateExportability(
		TOTorrent		source,
		File			dest )
	{
		try{
			TOTorrent target = TOTorrentFactory.deserialiseFromBEncodedFile( dest );
			
			if ( target.isExportable()){
				
				return( true );
			}
			
			if ( target.updateExportability( source )){
				
				target.serialiseToBEncodedFile( dest );
				
				return( true );
				
			}else{
				
				return( false );
			}
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( false );
		}
	}

	/**
	 * Disables DNS Handling, but doesn't save it to the config
	 */
	public static void temporarilyDisableDNSHandling() {
		DNS_HANDLING_ENABLE = false;
		dns_threads.setExecutionLimit(10);
		dns_threads.checkTimeouts();
	}

	private static Set<String>		tracker_hosts		= new HashSet<>();
	private static Set<String>		tracker_addresses	= new HashSet<>();
	
	public static boolean
	isTrackerAddress(
		TOTorrent				torrent,
		InetSocketAddress		isa )
	{
		try{
			byte[] hash = torrent.getHash();
	
			Set<String>	hosts = getUniqueTrackerHosts( torrent, false );
	
			synchronized( tracker_hosts ){
				
				for ( String host: hosts ){
					
					if ( !tracker_hosts.contains( host )){
						
						tracker_hosts.add( host );
						
						if ( AENetworkClassifier.categoriseAddress( host ) == AENetworkClassifier.AT_PUBLIC ){
							
							try{
								List<InetAddress> host_addresses;
								
								try{
									host_addresses = DNSUtils.getSingleton().getAllByName( host );
									
								}catch( Throwable e ){
									
									host_addresses = Arrays.asList( InetAddress.getAllByName( host ));
								}
								
								for ( InetAddress a: host_addresses ){
																	
									tracker_addresses.add( a.getHostAddress() );
								}
							}catch( Throwable e ){
								
							}
						}else{
							
							tracker_addresses.add( host );
						}
					}
				}
				
				if ( isa.isUnresolved()){
					
					return( tracker_addresses.contains( isa.getHostName()));
					
				}else{
					
					return( tracker_addresses.contains( isa.getAddress().getHostAddress()));
				}
			}
		}catch( Throwable e ){
			
		}
		
		return( false );
	}
	
	public static Set<String>
	getFilePriorityExtensions()
	{
		return( priority_file_exts );
	}
	
	public static boolean
	getFilePriorityExtensionsIgnoreCase()
	{
		return( priority_file_exts_ignore_case );
	}
	
	public static void
	main(
		String[]	args )
	{
		try{
			//URL url = new URL( "http://tracker.openbittorrent.com/");
			URL url = new URL( "http://inferno.demonoid.com:3413/announce");

			System.out.println( applyDNSMods( url ));

			Thread.sleep( 1000*1000 );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
