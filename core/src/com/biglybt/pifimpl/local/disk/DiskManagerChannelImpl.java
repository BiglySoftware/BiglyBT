/*
 * Created on 29-Mar-2006
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

package com.biglybt.pifimpl.local.disk;

import java.io.IOException;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfoListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.peermanager.piecepicker.PieceRTAProvider;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;
import com.biglybt.pif.disk.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.download.DownloadImpl;
import com.biglybt.pifimpl.local.utils.PooledByteBufferImpl;

public class
DiskManagerChannelImpl
	implements DiskManagerChannel, DiskManagerFileInfoListener, DownloadManagerPeerListener, PieceRTAProvider
{
	private static int		DEFAULT_BUFFER_MILLIS;
	private static int		DEFAULT_MIN_PIECES_TO_BUFFER;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"filechannel.rt.buffer.millis",
				"filechannel.rt.buffer.pieces",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName )
				{
					DEFAULT_BUFFER_MILLIS			= COConfigurationManager.getIntParameter( "filechannel.rt.buffer.millis" );
					DEFAULT_MIN_PIECES_TO_BUFFER 	= COConfigurationManager.getIntParameter( "filechannel.rt.buffer.pieces" );
				}
			});
	}


	private static final boolean	TRACE = false;

	private static final int COMPACT_DELAY	= 32;

	private static final int MAX_READ_CHUNK_DEFAULT	= 64*1024;

	private static final Comparator<dataEntry> comparator = new
		Comparator<dataEntry>()
		{
			@Override
			public int
		   	compare(
		   		dataEntry o1,
		   		dataEntry o2)
			{
				long	offset1 = o1.getOffset();
				long	length1	= o1.getLength();

				long	offset2 = o2.getOffset();
				long	length2	= o2.getLength();


				long	res;

				if ( offset1 == offset2 ){

					res = length1 - length2;

				}else{

					res = offset1 - offset2;
				}

				if ( res == 0 ){
					return(0);
				}else if ( res < 0 ){
					return(-1);
				}else{
					return(1);
				}
			}
		};

	private static final String	channel_key = "DiskManagerChannel";
	private static int	channel_id_next;

		// hack to allow other components to be informed when channels are created

	private static CopyOnWriteList<channelCreateListener>	listeners = new CopyOnWriteList<>();

	public static void
	addListener(
		channelCreateListener		l )
	{
		listeners.add( l );
	}

	public static void
	removeListener(
		channelCreateListener		l )
	{
		listeners.remove( l );
	}

	protected static void
	reportCreated(
		DiskManagerChannel	channel )
	{
		Iterator<channelCreateListener> it = listeners.iterator();

		while( it.hasNext()){

			try{
				it.next().channelCreated( channel );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	public static interface
	channelCreateListener
	{
		public void
		channelCreated(
			DiskManagerChannel	channel );
	}

	private DownloadImpl	download;

	private com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl	plugin_file;
	private com.biglybt.core.disk.DiskManagerFileInfo					core_file;

	private Set<dataEntry>	data_written = new TreeSet<>(comparator);

	private int compact_delay	= COMPACT_DELAY;

	private List<AESemaphore>	waiters	= new ArrayList<>();

	private long	file_offset_in_torrent;
	private long	piece_size;

	private Average	byte_rate = Average.getInstance( 1000, 20 );

	private long	start_position;
	private long	start_time;

	private volatile long	current_position;

	private request	current_request;

	private long	buffer_millis_override;
	private long	buffer_delay_millis;

	private PEPeerManager	peer_manager;

	private long[]	rtas;

	private int		channel_id;

	private volatile boolean	destroyed;

	protected
	DiskManagerChannelImpl(
		DownloadImpl															_download,
		com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl		_plugin_file )

		throws DownloadException
	{
		download		= _download;
		plugin_file		= _plugin_file;

		core_file		= plugin_file.getCore();

		DownloadManager core_download = core_file.getDownloadManager();

		if ( core_download.getTorrent() == null ){

			throw( new DownloadException( "Torrent invalid" ));
		}

		if ( core_download.isDestroyed()){

			Debug.out( "Download has been removed" );

			throw( new DownloadException( "Download has been removed" ));
		}

		synchronized( DiskManagerChannelImpl.class ){

			channel_id = channel_id_next++;
		}

		TOTorrentFile	tf = core_file.getTorrentFile();

		TOTorrent 	torrent = tf.getTorrent();

		TOTorrentFile[]	tfs = torrent.getFiles();

		rtas	= new long[torrent.getNumberOfPieces()];

		core_download.addPeerListener( this );

		for (int i=0;i<core_file.getIndex();i++){

			file_offset_in_torrent += tfs[i].getLength();
		}

		piece_size	= tf.getTorrent().getPieceLength();

		core_file.addListener( this );

		reportCreated( this );
	}

	@Override
	public DiskManagerFileInfo
	getFile()
	{
		return( plugin_file );
	}

	@Override
	public DiskManagerRequest
	createRequest()
	{
		if ( core_file.getDownloaded() != core_file.getLength()){

			boolean is_paused = download.isPaused();

			if ( core_file.isSkipped() && !is_paused ){

					// don't mess with skipped state if paused

				core_file.setSkipped( false );
			}

			boolean	force_start = download.isForceStart();

			if ( !force_start ){

					// don't auto-start a paused torrent - the fact that it isn't running will be picked
					// up later when the requests blocks on data and the download is found to not
					// be running

				if ( !is_paused ){

					synchronized( download ){

						Map	dl_state = (Map)download.getDownload().getUserData( channel_key );

						if ( dl_state == null ){

							dl_state = new HashMap();

							download.getDownload().setUserData( channel_key, dl_state );
						}

						dl_state.put( ""+channel_id, "" );
					}

					download.setForceStart( true );
				}
			}
		}

		current_request = new request();

		return( current_request );
	}

	@Override
	public long
	getPosition()
	{
		return( current_position );
	}

	@Override
	public boolean
	isDestroyed()
	{
		return( destroyed );
	}

	@Override
	public void
	dataWritten(
		long	offset,
		long	length,
		Object	originator )
	{
		if ( TRACE ){
			System.out.println( "data written:" + offset + "/" + length );
		}

		dataEntry	entry = new dataEntry( offset, length );

		synchronized( data_written ){

			data_written.add( entry );

			compact_delay--;

			if ( compact_delay == 0 ){

				compact_delay	= COMPACT_DELAY;

				Iterator<dataEntry>	it = data_written.iterator();

				dataEntry	prev_e	= null;

				while( it.hasNext()){

					dataEntry	this_e = it.next();

					if ( prev_e == null ){

						prev_e = this_e;

					}else{

						long	prev_offset = prev_e.getOffset();
						long	prev_length	= prev_e.getLength();
						long	this_offset = this_e.getOffset();
						long	this_length	= this_e.getLength();

						if ( this_offset <= prev_offset + prev_length ){

							if ( TRACE ){
								System.out.println( "merging: " + prev_e.getString()  + "/" + this_e.getString());
							}

							it.remove();

							prev_e.setLength( Math.max( prev_offset + prev_length, this_offset + this_length ) - prev_offset );

							if ( TRACE ){
								System.out.println( "    -> " + prev_e.getString());
							}

						}else{

							prev_e = this_e;
						}
					}
				}
			}

			for (int i=0;i<waiters.size();i++){

				waiters.get(i).release();
			}
		}
	}

	@Override
	public void
	dataChecked(
		long	offset,
		long	length )
	{
		// System.out.println( "data checked:" + offset + "/" + length );
	}

	@Override
	public void
	peerManagerWillBeAdded(
		PEPeerManager	manager )
	{
	}

	@Override
	public void
	peerManagerAdded(
		PEPeerManager	manager )
	{
		peer_manager = manager;

		manager.getPiecePicker().addRTAProvider( this );
	}

	@Override
	public void
	peerManagerRemoved(
		PEPeerManager	manager )
	{
		peer_manager = null;

		manager.getPiecePicker().removeRTAProvider( this );
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

   	@Override
    public long[]
   	updateRTAs(
   		PiecePicker		picker )
   	{
   		long	overall_pos = current_position + file_offset_in_torrent;

   		int	first_piece = (int)( overall_pos / piece_size );

   		long	rate = byte_rate.getAverage();

   		int	buffer_millis = (int)(buffer_millis_override==0?DEFAULT_BUFFER_MILLIS:buffer_millis_override);

   		long	buffer_bytes = ( buffer_millis * rate ) / 1000;

   		int	pieces_to_buffer = (int)( buffer_bytes / piece_size );

   		if ( pieces_to_buffer < 1 ){

   			pieces_to_buffer	= 1;
   		}

   		int	millis_per_piece = buffer_millis/pieces_to_buffer;

   		if ( pieces_to_buffer < DEFAULT_MIN_PIECES_TO_BUFFER ){

   			pieces_to_buffer = DEFAULT_MIN_PIECES_TO_BUFFER;
   		}

   		//System.out.println( "rate=" + rate + ", buffer_bytes=" + buffer_bytes + ", first_piece=" + first_piece + ", pieces=" + pieces_to_buffer + ", millis_per_piece=" + millis_per_piece );

   		Arrays.fill( rtas, 0 );

   		long	now = SystemTime.getCurrentTime();

   		now += buffer_delay_millis;

   		for (int i=first_piece;i<first_piece+pieces_to_buffer&&i<rtas.length;i++){

   			rtas[i]	= now + (( i - first_piece ) * millis_per_piece );
   		}

   		return( rtas );
   	}

   	@Override
    public long
   	getStartTime()
   	{
   		return( start_time );
   	}

   	@Override
    public long
   	getStartPosition()
   	{
   		return( file_offset_in_torrent + start_position );
   	}

	@Override
	public long
	getCurrentPosition()
	{
		return( file_offset_in_torrent + current_position );
	}

	@Override
	public long
	getBlockingPosition()
	{
		request r = current_request;

		if ( r == null ){

			return( file_offset_in_torrent + current_position );
		}

		return( file_offset_in_torrent + current_position + r.getAvailableBytes());
	}

	@Override
	public void
	setBufferMillis(
		long	millis,
		long	delay_millis )
	{
		buffer_millis_override	= millis;
		buffer_delay_millis 	= delay_millis;
	}

	@Override
	public String
	getUserAgent()
	{
		request r = current_request;

		if ( r == null ){

			return( null );
		}

		return( r.getUserAgent());
	}

	@Override
	public void
	destroy()
	{
		destroyed	= true;

		core_file.removeListener( this );

		core_file.getDownloadManager().removePeerListener(this);

		core_file.close();

		if ( peer_manager != null ){

			peer_manager.getPiecePicker().removeRTAProvider( this );
		}

		boolean	stop_force_start = false;

		synchronized( download ){

			Map	dl_state = (Map)download.getDownload().getUserData( channel_key );

			if ( dl_state != null ){

				dl_state.remove( "" + channel_id );

				if ( dl_state.size() == 0 ){

					stop_force_start	= true;
				}
			}
		}

		if ( stop_force_start ){

			download.setForceStart( false );
		}
	}

	protected class
	request
		implements DiskManagerRequest
	{
		private int		request_type;
		private long	request_offset;
		private long	request_length;
		private List<DiskManagerListener>	listeners	= new ArrayList<>();

		private String	user_agent;

		private int		max_read_chunk = MAX_READ_CHUNK_DEFAULT;

		private volatile boolean	cancelled;

		AESemaphore	wait_sem = new AESemaphore( "DiskManagerChannelImpl:wait" );

		protected
		request()
		{
			start_time	= SystemTime.getCurrentTime();
		}

		@Override
		public void
		setType(
			int			_type )
		{
			request_type		= _type;
		}

		@Override
		public void
		setOffset(
			long		_offset )
		{
			request_offset	= _offset;
			start_position	= request_offset;
		}

		@Override
		public void
		setLength(
			long		_length )
		{
			if ( _length < 0 ){

				throw( new RuntimeException( "Illegal argument" ));
			}

			request_length	= _length;
		}

		@Override
		public void
		setMaximumReadChunkSize(
			int 	size )
		{
			max_read_chunk = size;
		}

		@Override
		public long
		getRemaining()
		{
			synchronized( data_written ){

				return( request_length - (current_position - request_offset ));
			}
		}

		@Override
		public void
		setUserAgent(
			String	str )
		{
			user_agent	= str;
		}

		protected String
		getUserAgent()
		{
			return( user_agent );
		}

		@Override
		public long
		getAvailableBytes()
		{
			if ( plugin_file.getDownloaded() == plugin_file.getLength()){

				return( getRemaining());
			}

			int	download_state = download.getState();

				// if the file is incomplete and the download isn't running then we don't have a view
				// of what's available or not (to do this we'd need to add stuff to access resume data)

			if ( 	download_state != Download.ST_DOWNLOADING &&
					download_state != Download.ST_SEEDING ){

				return( -1 );
			}

			synchronized( data_written ){

				Iterator<dataEntry>	it = data_written.iterator();

					// may not have been compacted to we need to aggregate contigous entry lengths

				dataEntry	last_entry 	= null;

				while( it.hasNext()){

					dataEntry	entry = it.next();

					long	entry_offset = entry.getOffset();
					long	entry_length = entry.getLength();

					if ( last_entry == null ){

						if ( entry_offset > current_position ){

							break;
						}

						if ( entry_offset <= current_position && current_position < entry_offset + entry_length ){

							last_entry = entry;
						}
					}else{

						if ( last_entry.getOffset() + last_entry.getLength() == entry.getOffset()){

							last_entry = entry;

						}else{

							break;
						}
					}
				}

				if ( last_entry == null ){

					return( 0 );

				}else{

					return( last_entry.getOffset() + last_entry.getLength() - current_position );
				}
			}
		}

		@Override
		public void
		run()
		{
			long	rem = request_length;

			long	pos = request_offset;

			long	download_not_running_time	= 0;

			try{

				while( rem > 0 && !cancelled ){

					long	len = 0;

					synchronized( data_written ){

						current_position = pos;

						Iterator<dataEntry>	it = data_written.iterator();

						while( it.hasNext()){

							dataEntry	entry = it.next();

							long	entry_offset = entry.getOffset();

							if ( entry_offset > pos ){

								break;
							}

							long	entry_length = entry.getLength();

							long	available = entry_offset + entry_length - pos;

							if ( available > 0 ){

								len = available;

								break;
							}
						}
					}

					if ( len > 0 ){

						if ( len > rem ){

							len = rem;
						}

						if ( len > max_read_chunk ){

							len = max_read_chunk;
						}

						DirectByteBuffer buffer = core_file.read( pos, (int)len );

							// might not have read the amount requested if file has been truncated

						int read = buffer.position( DirectByteBuffer.SS_EXTERNAL );

						if ( read != len ){

							throw( new IOException( "EOF: insufficient bytes read (expected=" + len + ", actual=" + read + ")" ));
						}

						inform( new event( new PooledByteBufferImpl( buffer ), pos, (int)len ));

						pos += len;

						rem -= len;

						synchronized( data_written ){

							byte_rate.addValue( len );

							current_position = pos;
						}
					}else{

						inform( new event( pos ));

						synchronized( data_written ){

							waiters.add( wait_sem );
						}

						try{

							while(!cancelled){

								if ( wait_sem.reserve( 500 )){

									break;
								}

								DownloadManager dm = core_file.getDownloadManager();

								if ( dm.isDestroyed()){

									throw( new Exception( "Download has been removed" ));

								}else if ( core_file.isSkipped()){

									throw( new Exception( "File is 'do not download'" ));

								}else{

									int	state = dm.getState();

									if ( state == DownloadManager.STATE_ERROR || state == DownloadManager.STATE_STOPPED ){

										long	now = SystemTime.getMonotonousTime();

										if ( download_not_running_time == 0 ){

											download_not_running_time = now;

										}else if ( now - download_not_running_time > 15*1000 ){

											if ( dm.isPaused()){

												throw( new Exception( "Download has been paused" ));

											}else{

												throw( new Exception( "Download has been stopped" ));
											}
										}
									}else{

										download_not_running_time = 0;
									}
								}
							}
						}finally{

							synchronized( data_written ){

								waiters.remove( wait_sem );
							}
						}
					}
				}
			}catch( Throwable e ){

				inform( e );
			}
		}

		@Override
		public void
		cancel()
		{
			cancelled	= true;

			inform( new Throwable( "Request cancelled" ));

			wait_sem.release();
		}

		protected void
		inform(
			Throwable e )
		{
			inform( new event( e ));
		}

		protected void
		inform(
			event		ev )
		{
			for (int i=0;i<listeners.size();i++){

				try{
					((DiskManagerListener)listeners.get(i)).eventOccurred( ev );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		@Override
		public void
		addListener(
			DiskManagerListener	listener )
		{
			listeners.add( listener );
		}

		@Override
		public void
		removeListener(
			DiskManagerListener	listener )
		{
			listeners.remove( listener );
		}

		protected class
		event
			implements DiskManagerEvent
		{
			private int					event_type;
			private Throwable			error;
			private PooledByteBuffer	buffer;
			private long				event_offset;
			private int					event_length;

			protected
			event(
				Throwable		_error )
			{
				event_type	= DiskManagerEvent.EVENT_TYPE_FAILED;
				error		= _error;
			}

			protected
			event(
				long				_offset )
			{
				event_type		= DiskManagerEvent.EVENT_TYPE_BLOCKED;

				event_offset	= _offset;
			}

			protected
			event(
				PooledByteBuffer	_buffer,
				long				_offset,
				int					_length )
			{
				event_type		= DiskManagerEvent.EVENT_TYPE_SUCCESS;
				buffer			= _buffer;
				event_offset	= _offset;
				event_length	= _length;
			}

			@Override
			public int
			getType()
			{
				return( event_type );
			}

			public DiskManagerRequest
			getRequest()
			{
				return( request.this );
			}

			@Override
			public long
			getOffset()
			{
				return( event_offset );
			}

			@Override
			public int
			getLength()
			{
				return( event_length );
			}

			@Override
			public PooledByteBuffer
			getBuffer()
			{
				return( buffer );
			}

			@Override
			public Throwable
			getFailure()
			{
				return( error );
			}
		}
	}

	protected static class
	dataEntry
	{
		private long	offset;
		private long	length;

		protected
		dataEntry(
			long		_offset,
			long		_length )
		{
			offset	= _offset;
			length	= _length;
		}

		protected long
		getOffset()
		{
			return( offset );
		}

		protected long
		getLength()
		{
			return( length );
		}

		protected void
		setLength(
			long	_length )
		{
			length	= _length;
		}

		protected String
		getString()
		{
			return( "offset=" + offset + ",length=" + length );
		}
	}
}
