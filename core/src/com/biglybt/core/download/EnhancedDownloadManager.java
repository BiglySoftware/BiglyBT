/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.download;

import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.impl.DownloadManagerAdapter;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.peermanager.piecepicker.PieceRTAProvider;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.util.ConstantsVuze;


public class
EnhancedDownloadManager
{
	public static  int	DEFAULT_MINIMUM_INITIAL_BUFFER_SECS_FOR_ETA	= 30;

		// number of seconds of buffer required before we fall back to normal bt mode

	public static  int	MINIMUM_INITIAL_BUFFER_SECS;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"filechannel.rt.buffer.millis"
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName )
				{
					int channel_buffer_millis = COConfigurationManager.getIntParameter( "filechannel.rt.buffer.millis" );

					MINIMUM_INITIAL_BUFFER_SECS = (2 * channel_buffer_millis )/1000;
				}
			});
	}

	public static final int REACTIVATE_PROVIDER_PERIOD			= 5*1000;
	public static final int REACTIVATE_PROVIDER_PERIOD_TICKS	= REACTIVATE_PROVIDER_PERIOD/DownloadManagerEnhancer.TICK_PERIOD;

	public static final int LOG_PROG_STATS_PERIOD	= 10*1000;
	public static final int LOG_PROG_STATS_TICKS	= LOG_PROG_STATS_PERIOD/DownloadManagerEnhancer.TICK_PERIOD;

	private static final int content_stream_bps_min_increase_ratio	= 5;
	private static final int content_stream_bps_max_increase_ratio	= 8;


	DownloadManagerEnhancer		enhancer;
	DownloadManager				download_manager;

	boolean						explicit_progressive;

	volatile PiecePicker		current_piece_pickler;



	volatile boolean	progressive_active	= false;

	long	content_min_delivery_bps;

	int		minimum_initial_buffer_secs_for_eta;

	bufferETAProvider	buffer_provider	= new bufferETAProvider();

	progressiveStats	progressive_stats;


	private boolean	marked_active;
	private boolean	destroyed;

	private DownloadManagerListener dmListener;

	private EnhancedDownloadManagerFile[]	enhanced_files;





	protected
	EnhancedDownloadManager(
		DownloadManagerEnhancer		_enhancer,
		DownloadManager				_download_manager )
	{
		enhancer			= _enhancer;
		download_manager	= _download_manager;

		DiskManagerFileInfo[] files = download_manager.getDiskManagerFileInfo();

		minimum_initial_buffer_secs_for_eta = DEFAULT_MINIMUM_INITIAL_BUFFER_SECS_FOR_ETA;

		enhanced_files = new EnhancedDownloadManagerFile[files.length];

		long	offset = 0;

		for (int i=0;i<files.length;i++){

			DiskManagerFileInfo f = files[i];

			enhanced_files[i] = new EnhancedDownloadManagerFile( f, offset );

			offset += f.getLength();
		}


		int primary_index = getPrimaryFileIndex();

		EnhancedDownloadManagerFile primary_file = enhanced_files[primary_index==-1?0:primary_index];

		progressive_stats	= createProgressiveStats( download_manager, primary_file );

		download_manager.addPeerListener(
			new DownloadManagerPeerListener()
			{
       			@Override
		        public void
    			peerManagerWillBeAdded(
    				PEPeerManager	peer_manager )
       			{
       			}

				@Override
				public void
				peerManagerAdded(
					PEPeerManager	manager )
				{
					synchronized( EnhancedDownloadManager.this ){

						current_piece_pickler = manager.getPiecePicker();

						if ( progressive_active && current_piece_pickler != null ){

							buffer_provider.activate( current_piece_pickler );
						}
					}
				}

				@Override
				public void
				peerManagerRemoved(
					PEPeerManager	manager )
				{
					synchronized( EnhancedDownloadManager.this ){

						if ( progressive_active ){

							setProgressiveMode( false );
						}
					}
				}

				@Override
				public void
				peerAdded(
					PEPeer 	peer )
				{
				}

				@Override
				public void
				peerRemoved(
					PEPeer	peer )
				{
				}
			});
	}

	public int getPrimaryFileIndex() {
		DiskManagerFileInfo info = download_manager.getDownloadState().getPrimaryFile();

		if ( info == null ){
			return( -1 );
		}
		return( info.getIndex());
	}

	public void
	setExplicitProgressive(
		int		min_initial_buffer_secs,
		long	min_bps,
		int		file_index )
	{
		if ( file_index >= 0 && file_index < enhanced_files.length ){

			explicit_progressive = true;

			minimum_initial_buffer_secs_for_eta = min_initial_buffer_secs;

			content_min_delivery_bps = min_bps;

			EnhancedDownloadManagerFile primary_file = enhanced_files[file_index];

			progressive_stats	= createProgressiveStats( download_manager, primary_file );
		}
	}

	public String
	getName()
	{
		return( download_manager.getDisplayName());
	}

	public byte[]
	getHash()
	{
		TOTorrent t = download_manager.getTorrent();

		if ( t == null ){

			return( null );
		}

		try{

			return( t.getHash());

		}catch( Throwable e ){

			return( null );
		}
	}

	public EnhancedDownloadManagerFile[]
	getFiles()
	{
		return( enhanced_files );
	}

	protected long
	getTargetSpeed()
	{
		long	target_speed = progressive_active?progressive_stats.getStreamBytesPerSecondMax():content_min_delivery_bps;

		if ( target_speed < content_min_delivery_bps ){

			target_speed = content_min_delivery_bps;
		}

		return( target_speed );
	}

	protected boolean
	updateStats(
		int		tick_count )
	{
		return( updateProgressiveStats( tick_count ));
	}



	public boolean
	supportsProgressiveMode()
	{
		TOTorrent	torrent = download_manager.getTorrent();

		if ( torrent == null ){

			return( false );
		}

		return( enhancer.isProgressiveAvailable() &&
				( PlatformTorrentUtils.isContentProgressive( torrent ) || explicit_progressive ));
	}

	public void
	prepareForProgressiveMode(
		boolean		active )
	{
		enhancer.prepareForProgressiveMode( download_manager, active );
	}

	public boolean
	setProgressiveMode(
		boolean		active )
	{
		return( setProgressiveMode( active, false ));
	}

	protected boolean
	setProgressiveMode(
		boolean		active,
		boolean		switching_progressive_downloads )
	{
		TOTorrent	torrent = download_manager.getTorrent();

		DiskManagerFileInfo primaryFile = download_manager.getDownloadState().getPrimaryFile();

		if ( torrent == null || primaryFile == null ){

			return( false );
		}

		EnhancedDownloadManagerFile enhanced_file = enhanced_files[primaryFile.getIndex()];

		synchronized( this ){

			if ( progressive_active == active ){

				return( true );
			}

			if (active && !supportsProgressiveMode()) {

				Debug.out( "Attempt to set progress mode on non-progressible content - " + getName());

				return( false );
			}

			log( "Progressive mode changed to " + active );

			final GlobalManager gm = download_manager.getGlobalManager();
			if (active) {
				if (dmListener == null) {
					dmListener = new DownloadManagerAdapter() {
						@Override
						public void downloadComplete(DownloadManager manager) {
							enhancer.resume();
						}
					};
				}
				download_manager.addListener(dmListener);

				// Check existing downloading torrents and turn off any
				// existing progressive/downloading
				Object[] dms = gm.getDownloadManagers().toArray();
				for (int i = 0; i < dms.length; i++) {
					DownloadManager dmCheck = (DownloadManager) dms[i];
					if (dmCheck.equals(download_manager)) {
						continue;
					}

					if (!dmCheck.isDownloadComplete(false)) {
						int state = dmCheck.getState();
						if (state == DownloadManager.STATE_DOWNLOADING
								|| state == DownloadManager.STATE_QUEUED) {
							enhancer.pause( dmCheck );
						}
						EnhancedDownloadManager edmCheck = enhancer.getEnhancedDownload(dmCheck);
						if (edmCheck != null && edmCheck.getProgressiveMode()) {
							edmCheck.setProgressiveMode(false, true);
						}
					}
				}
				if (download_manager.isPaused()) {
					enhancer.resume( download_manager );
				}

				// Make sure download can start by moving out of stop state
				// and putting at top
				if (download_manager.getState() == DownloadManager.STATE_STOPPED) {
					download_manager.setStateWaiting();
				}

				if (download_manager.getPosition() != 1) {
					download_manager.getGlobalManager().moveTo(download_manager, 1);
				}
			} else {
				download_manager.removeListener(dmListener);
				if ( !switching_progressive_downloads ){
					enhancer.resume();
				}
			}

			progressive_active	= active;

			if ( progressive_active ){

				enhancer.progressiveActivated();
			}

			if ( current_piece_pickler != null ){

				if ( progressive_active ){

					buffer_provider.activate( current_piece_pickler );

					progressive_stats.update( 0 );

				}else{

					buffer_provider.deactivate( current_piece_pickler );

					progressive_stats = createProgressiveStats( download_manager, enhanced_file );
				}
			}else{

				progressive_stats = createProgressiveStats( download_manager, enhanced_file );
			}

			if ( !switching_progressive_downloads ){

				if ( active ){

					RealTimeInfo.setProgressiveActive(  progressive_stats.getStreamBytesPerSecondMax());

				}else{

					RealTimeInfo.setProgressiveInactive();
				}
			}
		}

		return( true );
	}


	public boolean
	getProgressiveMode()
	{
		return( progressive_active );
	}

	public long
	getProgressivePlayETA()
	{
		progressiveStats stats = getProgressiveStats();

		if ( stats == null ){

			return( Long.MAX_VALUE );
		}

		long	eta = stats.getETA();

		return( eta );
	}

	protected progressiveStats
	getProgressiveStats()
	{
		synchronized( this ){

			if ( progressive_stats == null ){

				return( null );
			}

			return( progressive_stats.getCopy());
		}
	}

	protected progressiveStats
	createProgressiveStats(
		DownloadManager					dm,
		EnhancedDownloadManagerFile		file )
	{
		return( new progressiveStatsCommon( dm, file ));
	}

	protected boolean
	updateProgressiveStats(
		int		tick_count )
	{
		if ( !progressive_active ){

			return( false );
		}

		synchronized( this ){

			if ( !progressive_active || progressive_stats == null ){

				return( false );
			}

			if ( tick_count % REACTIVATE_PROVIDER_PERIOD_TICKS == 0 ){

				PiecePicker piece_picker = current_piece_pickler;

				if ( piece_picker != null ){

					buffer_provider.checkActivation( piece_picker );
				}
			}

			progressive_stats.update( tick_count );

			long	current_max = progressive_stats.getStreamBytesPerSecondMax();

			if ( RealTimeInfo.getProgressiveActiveBytesPerSec() != current_max ){

				RealTimeInfo.setProgressiveActive( current_max );
			}
		}

		return( true );
	}

	protected void
	setRTA(
		boolean	active )
	{
		synchronized( this ){

			if ( marked_active && !active ){

				marked_active = false;

				RealTimeInfo.removeRealTimeTask();
			}

			if ( destroyed ){

				return;
			}

			if ( !marked_active && active ){

				marked_active = true;

				RealTimeInfo.addRealTimeTask();
			}
		}
	}

	public long
	getContiguousAvailableBytes(
		int						file_index,
		long					file_start_offset,
		long					stop_counting_after )
	{
		if ( file_index < 0 || file_index >= enhanced_files.length ) {

			return( -1 );
		}

		EnhancedDownloadManagerFile	efile = enhanced_files[ file_index ];

		DiskManagerFileInfo file = efile.getFile();

		DiskManager dm = download_manager.getDiskManager();

		if ( dm == null ){

			if ( file.getDownloaded() == file.getLength()){

				return( file.getLength() - file_start_offset );
			}

			return( -1 );
		}

		int	piece_size = dm.getPieceLength();

		long	start_index = efile.getByteOffestInTorrent() + file_start_offset;


		int	first_piece_index 	= (int)( start_index / piece_size );
		int	first_piece_offset	= (int)( start_index % piece_size );
		int	last_piece_index	= file.getLastPieceNumber();

		DiskManagerPiece[]	pieces = dm.getPieces();

		DiskManagerPiece	first_piece = pieces[first_piece_index];

		long	available = 0;

		if ( !first_piece.isDone()){

			boolean[] blocks = first_piece.getWritten();

			if ( blocks == null ){

				if ( first_piece.isDone()){

					available = first_piece.getLength() - first_piece_offset;
				}
			}else{

				int	piece_offset = 0;

				for (int j=0;j<blocks.length;j++){

					if ( blocks[j] ){

						int	block_size = first_piece.getBlockSize( j );

						piece_offset = piece_offset + block_size;

						if ( available == 0 ){

							if ( piece_offset > first_piece_offset ){

								available = piece_offset - first_piece_offset;
							}
						}else{

							available += block_size;
						}
					}else{

						break;
					}
				}
			}
		}else{

			available = first_piece.getLength() - first_piece_offset;

			for (int i=first_piece_index+1;i<=last_piece_index;i++){

				if ( stop_counting_after > 0 && available >= stop_counting_after ){

					break;
				}

				DiskManagerPiece piece = pieces[i];

				if ( piece.isDone()){

					available += piece.getLength();

				}else{

					boolean[] blocks = piece.getWritten();

					if ( blocks == null ){

						if ( piece.isDone()){

							available += piece.getLength();

						}else{

							break;
						}
					}else{

						for (int j=0;j<blocks.length;j++){

							if ( blocks[j] ){

								available += piece.getBlockSize( j );

							}else{

								break;
							}
						}
					}

					break;
				}
			}
		}

		long	max_available = file.getLength() - file_start_offset;

		if ( available > max_available ){

			available = max_available;
		}

		return( available );
	}


	public DownloadManager
	getDownloadManager()
	{
		return download_manager;
	}

	protected void
	destroy()
	{
		synchronized( this ){

			setRTA( false );

			destroyed = true;
		}
	}

	protected void
	log(
		String	str )
	{
		log( str, true );
	}

	protected void
	log(
		String	str,
		boolean	to_file )
	{
		log( download_manager, str, to_file );
	}

	protected void
	log(
		DownloadManager	dm,
		String			str,
		boolean			to_file )
	{
		str = dm.toString() + ": " + str;

		if ( to_file ){

			AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger("v3.Stream");

			diag_logger.log(str);
		}

		if ( ConstantsVuze.DIAG_TO_STDOUT ) {

			System.out.println(Thread.currentThread().getName() + "|"
					+ System.currentTimeMillis() + "] " + str);
		}
	}

	protected class
	bufferETAProvider
		implements PieceRTAProvider
	{
		private boolean		is_buffering	= true;

		private long[]		piece_rtas;

		private long				last_buffer_size;
		private long				last_buffer_size_time;

		private boolean		active;
		private long		last_recalc;

		protected void
		activate(
			PiecePicker		picker )
		{
			synchronized( EnhancedDownloadManager.this ){

				if ( !active ){

					log( "Activating RTA provider" );

					active = true;

					picker.addRTAProvider( this );
				}
			}
		}

		protected void
		deactivate(
			PiecePicker		picker )
		{
			synchronized( EnhancedDownloadManager.this ){

				if ( active ){

	   				log( "Deactivating RTA provider" );

	   				picker.removeRTAProvider( this );
				}

				piece_rtas	= null;

				active = false;
			}
		}

		protected void
		checkActivation(
			PiecePicker		picker )
		{
				// might need to re-enable the buffer provider if speeds change

   			if ( getProgressivePlayETA() > 0 ){

    			synchronized( EnhancedDownloadManager.this ){

    				if ( piece_rtas == null ){

    					activate( picker );
     				}
    			}
    		}
		}

		@Override
		public long[]
    	updateRTAs(
    		PiecePicker		picker )
    	{
			long	mono_now = SystemTime.getMonotonousTime();

			if ( mono_now - last_recalc < 500 ){

				return( piece_rtas );
			}

			last_recalc	= mono_now;

    		DiskManager	disk_manager = download_manager.getDiskManager();

    		progressiveStats stats = progressive_stats;

    		if ( disk_manager == null || stats == null || stats.getFile().isComplete()){

    			deactivate( picker );

    			return( null );
    		}

       		EnhancedDownloadManagerFile file = stats.getFile();

			long	abs_provider_pos = stats.getCurrentProviderPosition( true );
 			long	rel_provider_pos = stats.getCurrentProviderPosition( false );

			long	buffer_bytes = stats.getBufferBytes();

  			boolean buffering = getProgressivePlayETA() >= 0;

  			if ( buffering ){

				long	 buffer_size = getContiguousAvailableBytes( file.getIndex(), rel_provider_pos, buffer_bytes );

				if ( buffer_size == buffer_bytes ){

					buffering = false;
				}
  			}

  			if ( buffering != is_buffering ){

  				if ( buffering ){

  					log( "Switching to buffer mode" );

  				}else{

  					log( "Switching to RTA mode" );
  				}

  				is_buffering = buffering;
  			}

			long	piece_size = disk_manager.getPieceLength();

			int		start_piece = (int)( abs_provider_pos / piece_size );

			int		end_piece	= file.getFile().getLastPieceNumber();

			piece_rtas = new long[ picker.getNumberOfPieces()];

			long	now = SystemTime.getCurrentTime();

			if ( is_buffering ){

				for (int i=start_piece;i<=end_piece;i++){

						// not bothered about times here but need to use large increments to ensure
						// that pieces are picked in order even for slower peers

					piece_rtas[i] = now+(i*60000);
				}

				long	 buffer_size = getContiguousAvailableBytes( file.getIndex(), rel_provider_pos, 0 );

    			if ( last_buffer_size != buffer_size ){

    				last_buffer_size = buffer_size;

    				last_buffer_size_time = now;

    			}else{

    				if ( now < last_buffer_size_time ){

    					last_buffer_size_time = now;

    				}else{

    					long	stalled_for = now - last_buffer_size_time;

   						long	dl_speed = progressive_stats.getDownloadBytesPerSecond();

   						if ( dl_speed > 0 ){

   							long	block_time = (DiskManager.BLOCK_SIZE * 1000) / dl_speed;

   							if ( stalled_for > Math.max( 5000, 5*block_time )){

   								long	target_rta = now + block_time;

   								int	blocked_piece_index = (int)((abs_provider_pos + buffer_size ) / disk_manager.getPieceLength());

   								DiskManagerPiece[] pieces = disk_manager.getPieces();

   								if ( blocked_piece_index < pieces.length ){

   									if ( pieces[blocked_piece_index].isDone()){

   										blocked_piece_index++;

   										if ( blocked_piece_index < pieces.length ){

   											if ( pieces[blocked_piece_index].isDone()){

   												blocked_piece_index = -1;
   											}
   										}else{

   											blocked_piece_index = -1;
   										}
   									}
   								}

   								if ( blocked_piece_index >= 0 ){

   									piece_rtas[blocked_piece_index] = target_rta;

   									log( "Buffer provider: reprioritising lagging piece " + blocked_piece_index + " with rta " + block_time );
   								}
   							}
   						}
    				}
    			}
    		}else{

				long	bytes_offset = 0;

				long	max_bps = stats.getStreamBytesPerSecondMax();

				for (int i=start_piece;i<=end_piece;i++){

					piece_rtas[i] = now + ( 1000 * ( bytes_offset / max_bps ));

					bytes_offset += piece_size;

					if ( bytes_offset > buffer_bytes ){

						break;
					}
				}
    		}

    		return( piece_rtas );
    	}

    	@Override
	    public long
    	getCurrentPosition()
    	{
    		return( 0 );
    	}

      	@Override
	      public long
       	getStartTime()
       	{
       		return( 0 );
       	}

       	@Override
        public long
       	getStartPosition()
       	{
       		return( 0 );
       	}

    	@Override
	    public long
		getBlockingPosition()
		{
			return( 0 );
		}

    	@Override
	    public void
    	setBufferMillis(
			long	millis,
			long	delay_millis )
		{
		}

		@Override
		public String
		getUserAgent()
		{
			return( null );
		}
	}

	protected abstract static class
	progressiveStats
		implements Cloneable
	{
		protected abstract EnhancedDownloadManagerFile
		getFile();

		protected abstract boolean
		isProviderActive();

		protected abstract long
		getCurrentProviderPosition(
			boolean		absolute );

		protected abstract long
		getStreamBytesPerSecondMax();

		protected abstract long
		getStreamBytesPerSecondMin();

		protected abstract long
		getDownloadBytesPerSecond();

		protected abstract long
		getETA();

		public abstract long
		getBufferBytes();

		protected abstract long
		getSecondsToDownload();

		protected abstract long
		getSecondsToWatch();

		protected abstract void
		update(
			int	tick_count );

		protected progressiveStats
		getCopy()
		{
			try{
				return((progressiveStats)clone());

			}catch( CloneNotSupportedException e ){

				Debug.printStackTrace(e);

				return( null );
			}
		}

		protected String
		formatBytes(
			long	l )
		{
			return( DisplayFormatters.formatByteCountToKiBEtc( l ));
		}

		protected String
		formatSpeed(
			long	l )
		{
			return( DisplayFormatters.formatByteCountToKiBEtcPerSec( l ));
		}

	}

	protected class
	progressiveStatsCommon
		extends progressiveStats
	{
		private EnhancedDownloadManagerFile	primary_file;

		private PieceRTAProvider	current_provider;
		private String				current_user_agent;

		private long	content_stream_bps_min;
		private long	content_stream_bps_max;


		private Average		capped_download_rate_average 	= AverageFactory.MovingImmediateAverage( 10 );
		private Average		discard_rate_average 			= AverageFactory.MovingImmediateAverage( 10 );
		private long		last_discard_bytes				= download_manager.getStats().getDiscarded();

		private long		actual_bytes_to_download;
		private long		weighted_bytes_to_download;		// gives less weight to incomplete pieces

		private long		provider_life_secs;
		private long		provider_initial_position;
		private long		provider_byte_position;
		private long		provider_last_byte_position	= -1;
		private long		provider_blocking_byte_position;
		private Average		provider_speed_average	= AverageFactory.MovingImmediateAverage( 10 );

		protected
		progressiveStatsCommon(
			DownloadManager					_dm,
			EnhancedDownloadManagerFile		_primary_file )
		{
			primary_file = _primary_file;

			TOTorrent	torrent = download_manager.getTorrent();

			content_stream_bps_min = explicit_progressive?content_min_delivery_bps:PlatformTorrentUtils.getContentStreamSpeedBps( torrent );

			if ( content_stream_bps_min == 0 ){

					// hack in some test values for torrents that don't have a bps in them yet

				long	size = torrent.getSize();

				if ( size < 200*1024*1024 ){

					content_stream_bps_min = 30*1024;

				}else if ( size < 1000*1024*1024L ){

					content_stream_bps_min = 200*1024;

				}else{

					content_stream_bps_min = 400*1024;
				}
			}

				// bump it up by a bit to be conservative to deal with fluctuations, discards etc.

			content_stream_bps_min += content_stream_bps_min / content_stream_bps_min_increase_ratio;

			content_stream_bps_max = content_stream_bps_min + ( content_stream_bps_min / content_stream_bps_max_increase_ratio );

			setRTA( false );

			log( 	download_manager,
					"content_stream_bps=" + getStreamBytesPerSecondMin() +
					",primary=" + primary_file.getFile().getIndex(),
					true );
		}


		protected void
		updateCurrentProvider(
			PieceRTAProvider	provider )
		{
			long	file_start = primary_file.getByteOffestInTorrent();

			if ( current_provider != provider || provider == null ){

				current_provider 	= provider;
				current_user_agent	= null;

				provider_speed_average	= AverageFactory.MovingImmediateAverage( 10 );

				if ( current_provider == null ){

					provider_life_secs					= 0;
					provider_initial_position			= file_start;
					provider_byte_position				= file_start;
					provider_blocking_byte_position		= -1;
					provider_last_byte_position 		= -1;

				}else{

					provider_initial_position	= Math.max( file_start, current_provider.getStartPosition());

					provider_byte_position 		= provider_initial_position;
					provider_last_byte_position	= provider_initial_position;

					provider_blocking_byte_position		= current_provider.getBlockingPosition();

					provider_life_secs = ( SystemTime.getCurrentTime() - current_provider.getStartTime()) / 1000;

					if ( provider_life_secs < 0 ){

						provider_life_secs = 0;
					}
				}

				setRTA( current_provider != null );

			}else{

				provider_life_secs++;

				if ( current_user_agent == null ){

					current_user_agent = current_provider.getUserAgent();

					if ( current_user_agent != null ){

						log( "Provider user agent = " + current_user_agent );
					}
				}

				provider_byte_position			= Math.max( file_start, current_provider.getCurrentPosition());
				provider_blocking_byte_position	= current_provider.getBlockingPosition();

				long bytes_read = provider_byte_position - provider_last_byte_position;

				provider_speed_average.update( bytes_read );

				provider_last_byte_position = provider_byte_position;
			}
		}

		@Override
		protected boolean
		isProviderActive()
		{
			return( current_provider != null );
		}

		protected long
		getInitialProviderPosition()
		{
			return( provider_initial_position );
		}

		@Override
		protected long
		getCurrentProviderPosition(
			boolean		absolute )
		{
			long	res = provider_byte_position;

			if ( absolute ){

				if ( res == 0 ){

					res = primary_file.getByteOffestInTorrent();
				}
			}else{

				res -= primary_file.getByteOffestInTorrent();

				if ( res < 0 ){

					res = 0;
				}
			}

			return( res );
		}

		protected long
		getProviderLifeSecs()
		{
			return( provider_life_secs );
		}

		@Override
		protected void
		update(
			int		tick_count )
		{
			long download_rate = download_manager.getStats().getDataReceiveRate();

			capped_download_rate_average.update( download_rate );

			long	discards = download_manager.getStats().getDiscarded();

			discard_rate_average.update( discards - last_discard_bytes );

			last_discard_bytes = discards;

			DiskManager	disk_manager = download_manager.getDiskManager();

			PiecePicker	picker = current_piece_pickler;

			if ( getStreamBytesPerSecondMin() > 0 && disk_manager != null && picker != null ){

				List	providers = picker.getRTAProviders();

				long	max_cp	= 0;

				PieceRTAProvider	best_provider = null;

				for (int i=0;i<providers.size();i++){

					PieceRTAProvider	provider = (PieceRTAProvider)providers.get(i);

					if ( provider.getStartTime() > 0 ){

						long	cp = provider.getCurrentPosition();

						if ( cp >= max_cp ){

							best_provider = provider;

							max_cp	= cp;
						}
					}
				}

				updateCurrentProvider( best_provider );

				if ( best_provider != null ){

						// the file channel provider will try best-effort-RTA based which will result
						// in high discard - back it off based on how much slack we have

					long relative_pos = getCurrentProviderPosition( false );

					long buffer_bytes = getContiguousAvailableBytes( primary_file.getIndex(), relative_pos, getStreamBytesPerSecondMin() * 60 );

					long buffer_secs = buffer_bytes / getStreamBytesPerSecondMin();

						// don't be too aggresive with small buffers

					buffer_secs = Math.max( 10, buffer_secs );

					best_provider.setBufferMillis( 15*1000, buffer_secs * 1000 );
				}

				DiskManagerPiece[] pieces = disk_manager.getPieces();

				actual_bytes_to_download 	= 0;
				weighted_bytes_to_download	= 0;

				int	first_incomplete_piece = -1;

				int	piece_size = disk_manager.getPieceLength();

				int	last_piece_number = primary_file.getFile().getLastPieceNumber();

				for (int i=(int)(provider_byte_position/piece_size);i<=last_piece_number;i++){

					DiskManagerPiece piece = pieces[i];

					if ( piece.isDone()){

						continue;
					}

					if ( first_incomplete_piece == -1 ){

						first_incomplete_piece = i;
					}

					boolean[] blocks = piece.getWritten();

					int	bytes_this_piece = 0;

					if ( blocks == null ){

						bytes_this_piece = piece.getLength();

					}else{
						for (int j=0;j<blocks.length;j++){

							if ( !blocks[j] ){

								bytes_this_piece += piece.getBlockSize( j );
							}
						}
					}

					if ( bytes_this_piece > 0 ){

						actual_bytes_to_download += bytes_this_piece;

						int	diff = i - first_incomplete_piece;

						if ( diff == 0 ){

							weighted_bytes_to_download += bytes_this_piece;

						}else{

							int	weighted_bytes_done =  piece.getLength() - bytes_this_piece;

							weighted_bytes_done = ( weighted_bytes_done * ( pieces.length - i )) / (pieces.length - first_incomplete_piece);

							weighted_bytes_to_download += piece.getLength() - weighted_bytes_done;
						}
					}
				}
			}

			log( getString(), tick_count % LOG_PROG_STATS_TICKS == 0 );
		}

		@Override
		protected long
		getETA()
		{
			DiskManagerFileInfo file = primary_file.getFile();

			if ( file.getLength() == file.getDownloaded()){

				return( 0 );
			}

			long download_rate = getDownloadBytesPerSecond();

			if ( download_rate <= 0 ){

				return( Long.MAX_VALUE );
			}

			long	buffer_bytes	= getBufferBytes();

			long	buffer_done		= getContiguousAvailableBytes( file.getIndex(), getCurrentProviderPosition( false ), buffer_bytes );

			long 	rem_buffer = buffer_bytes - buffer_done;	// ok as initial dl is forced in order byte buffer-rta

			long 	rem_secs = (rem_buffer<=0)?0:(rem_buffer / download_rate);

			long	secs_to_download = getSecondsToDownload();

			long	secs_to_watch = getSecondsToWatch();

			long eta = secs_to_download - secs_to_watch;

			if ( rem_secs > eta && rem_secs > 0 ){

				eta = rem_secs;
			}

			return( eta );
		}

		@Override
		protected long
		getStreamBytesPerSecondMax()
		{
			return( content_stream_bps_max );
		}

		@Override
		protected long
		getStreamBytesPerSecondMin()
		{
			return( content_stream_bps_min );
		}

		@Override
		public long
		getBufferBytes()
		{
			long	min_dl = minimum_initial_buffer_secs_for_eta * getStreamBytesPerSecondMax();

			return( min_dl );
		}

		@Override
		protected EnhancedDownloadManagerFile
		getFile()
		{
			return( primary_file );
		}

		@Override
		protected long
		getDownloadBytesPerSecond()
		{
			long	original = (long)capped_download_rate_average.getAverage();

			long	current	= original;

			int	dl_limit = download_manager.getStats().getDownloadRateLimitBytesPerSecond();

			if ( dl_limit > 0 ){

				current = Math.min( current, dl_limit );
			}

			int global_limit = TransferSpeedValidator.getGlobalDownloadRateLimitBytesPerSecond();

			if ( global_limit > 0 ){

				current = Math.min( current, global_limit );
			}

			return( current );
		}

		@Override
		protected long
		getSecondsToDownload()
		{
			long download_rate = getDownloadBytesPerSecond();

			if ( download_rate == 0 ){

				return( Long.MAX_VALUE );
			}

			return( weighted_bytes_to_download / download_rate );
		}

		@Override
		public long
		getSecondsToWatch()
		{
			return(( primary_file.getLength() - getCurrentProviderPosition( false )) / getStreamBytesPerSecondMin());
		}

		protected String
		getString()
		{
			long	dl_rate = getDownloadBytesPerSecond();

			long	buffer_bytes	= getBufferBytes();

			long	buffer_done		= getContiguousAvailableBytes( primary_file.getIndex(), getCurrentProviderPosition( false ), buffer_bytes );

			return( "play_eta=" + getETA() + "/d=" + getSecondsToDownload() + "/w=" + getSecondsToWatch()+
					", dl_rate=" + formatSpeed(dl_rate)+ ", download_rem=" + formatBytes(weighted_bytes_to_download) + "/" + formatBytes(actual_bytes_to_download) +
					", discard_rate=" + formatSpeed((long)discard_rate_average.getAverage()) +
					", buffer: " + buffer_bytes + "/" + buffer_done +
					", prov: byte=" + formatBytes( provider_byte_position ) + " secs=" + ( provider_byte_position/getStreamBytesPerSecondMin()) + " speed=" + formatSpeed((long)provider_speed_average.getAverage()) +
					" block= " + formatBytes( provider_blocking_byte_position ));
		}
	}
}
