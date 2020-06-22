/*
 * File    : TRTrackerServerImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.tracker.server.impl.tcp;


import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.tracker.server.TRTrackerServerException;
import com.biglybt.core.tracker.server.TRTrackerServerListener;
import com.biglybt.core.tracker.server.TRTrackerServerListener2;
import com.biglybt.core.tracker.server.impl.TRTrackerServerImpl;
import com.biglybt.core.util.*;


public abstract class
TRTrackerServerTCP
	extends 	TRTrackerServerImpl
{
	private static final int 	THREAD_POOL_SIZE		= Math.max(1, COConfigurationManager.getIntParameter( "Tracker Max Threads" ));

	public static final long PROCESSING_GET_LIMIT			= Math.max(0, COConfigurationManager.getIntParameter( "Tracker Max GET Time" )*1000);
	public static final int  PROCESSING_POST_MULTIPLIER	=  Math.max(0, COConfigurationManager.getIntParameter( "Tracker Max POST Time Multiplier" ));

	private final boolean	ssl;
	private int		port;
	private final boolean	apply_ip_filter;

	private boolean restrict_non_blocking_requests = TRTrackerServerImpl.restrict_non_blocking_requests;

	private final ThreadPool	thread_pool;

	public
	TRTrackerServerTCP(
		String		_name,
		int			_port,
		boolean		_ssl,
		boolean		_apply_ip_filter,
		boolean		_start_up_ready )

		throws TRTrackerServerException
	{
		super( _name, _start_up_ready );

		port					= _port;
		ssl						= _ssl;
		apply_ip_filter			= _apply_ip_filter;

		thread_pool = new ThreadPool( "TrackerServer:TCP:"+port, THREAD_POOL_SIZE );

		if ( PROCESSING_GET_LIMIT > 0 ){

			thread_pool.setExecutionLimit( PROCESSING_GET_LIMIT );
		}
	}

	public void
	runProcessor(
		TRTrackerServerProcessorTCP	processor )
	{
		thread_pool.run( processor );
	}

	protected boolean
	isIPFilterEnabled()
	{
		return( apply_ip_filter );
	}

	public boolean
	getRestrictNonBlocking()
	{
		return( restrict_non_blocking_requests );
	}

	public void
	setRestrictNonBlocking(
		boolean restrict )
	{
		restrict_non_blocking_requests = restrict;
	}

	static boolean	LOG_DOS_TO_FILE	= System.getProperty(SystemProperties.SYSPROP_LOG_DOS) != null;

	protected static File		dos_log_file;

	protected static final AEMonitor class_mon 	= new AEMonitor( "TRTrackerServerTCP:class" );

	final Map	DOS_map =
		new LinkedHashMap( 1000, (float)0.75, true )
		{
			@Override
			protected boolean
			removeEldestEntry(
				Map.Entry eldest)
			{
				return( checkDOSRemove( eldest ));
			}
		};

	final List	dos_list	= new ArrayList(128);

	long	last_dos_check				= 0;
	static final long	MAX_DOS_ENTRIES				= 10000;
	static final long	MAX_DOS_RETENTION			= 10000;
	static final int		DOS_CHECK_DEAD_WOOD_COUNT	= 512;
	static final int		DOS_MIN_INTERVAL			= 1000;
	int		dos_check_count				= 0;

	protected boolean
	checkDOS(
		String		ip )

		throws UnknownHostException
	{
		InetAddress	inet_address = InetAddress.getByName(ip);

		if ( inet_address.isLoopbackAddress() || InetAddress.getLocalHost().equals( inet_address )){

			return( false);
		}

		boolean	res;

		last_dos_check = SystemTime.getCurrentTime();

		DOSEntry	entry = (DOSEntry)DOS_map.get(ip);

		if ( entry == null ){

			entry = new DOSEntry(ip);

			DOS_map.put( ip, entry );

			res	= false;

		}else{

			res = last_dos_check - entry.last_time < DOS_MIN_INTERVAL;

			if ( res && LOG_DOS_TO_FILE ){

				dos_list.add( entry );
			}

			entry.last_time = last_dos_check;
		}

			// remove dead wood

		dos_check_count++;

		if ( dos_check_count == DOS_CHECK_DEAD_WOOD_COUNT ){

			dos_check_count = 0;

			Iterator	it = DOS_map.values().iterator();

			while( it.hasNext()){

				DOSEntry	this_entry = (DOSEntry)it.next();

				if ( last_dos_check - this_entry.last_time > MAX_DOS_RETENTION ){

					it.remove();

				}else{

					break;
				}
			}

			if ( dos_list.size() > 0 ){

				try{
					class_mon.enter();

					if ( dos_log_file == null ){

						dos_log_file = FileUtil.newFile( System.getProperty("user.dir" ), "dos.log" );
					}

					PrintWriter pw = null;

					try{

						pw = new PrintWriter( new OutputStreamWriter( FileUtil.newFileOutputStream( dos_log_file, true )));

						for (int i=0;i<dos_list.size();i++){

							DOSEntry	this_entry = (DOSEntry)dos_list.get(i);

							String ts = new SimpleDateFormat("HH:mm:ss - ").format( new Date(this_entry.last_time ));

							pw.println( ts + this_entry.ip );
						}

					}catch( Throwable e ){

					}finally{

						dos_list.clear();

						if ( pw != null ){

							try{

								pw.close();

							}catch( Throwable e ){
							}
						}
					}
				}finally{

					class_mon.exit();
				}
			}
		}

		return( res );
	}

	protected boolean
	checkDOSRemove(
		Map.Entry		eldest )
	{
		boolean res = 	DOS_map.size() > MAX_DOS_ENTRIES ||
						last_dos_check - ((DOSEntry)eldest.getValue()).last_time > 	MAX_DOS_RETENTION;

		return( res );
	}

	protected class
	DOSEntry
	{
		final String		ip;
		long		last_time;

		protected
		DOSEntry(
			String		_ip )
		{
			ip			= _ip;
			last_time	= last_dos_check;
		}
	}

	@Override
	public int
	getPort()
	{
		return( port );
	}

	protected void
	setPort(
		int		_port )
	{
		port	= _port;
	}

	@Override
	public String
	getHost()
	{
		return( COConfigurationManager.getStringParameter( "Tracker IP", "" ));
	}

	@Override
	public boolean
	isSSL()
	{
		return( ssl );
	}


	protected boolean
	handleExternalRequest(
		final TRTrackerServerProcessorTCP	processor,
		final InetSocketAddress				local_address,
		final InetSocketAddress				client_address,
		final String						user,
		final String						url,
		final URL							absolute_url,
		final String						header,
		final InputStream					is,
		final OutputStream					os,
		final AsyncController				async,
		final boolean[]						keep_alive )

		throws IOException
	{
		final boolean	original_ka = keep_alive[0];

		keep_alive[0] = false;

		for ( TRTrackerServerListener listener: listeners ){

			if ( listener.handleExternalRequest( client_address, user, url, absolute_url, header, is, os, async )){

				return( true );
			}
		}

		for ( TRTrackerServerListener2 listener: listeners2 ){

			TRTrackerServerListener2.ExternalRequest request =
				new TRTrackerServerListener2.ExternalRequest()
				{
					@Override
					public InetSocketAddress
					getClientAddress()
					{
						return( client_address );
					}

					@Override
					public InetSocketAddress
					getLocalAddress()
					{
						return( local_address );
					}

					@Override
					public String
					getUser()
					{
						return( user );
					}

					@Override
					public String
					getURL()
					{
						return( url );
					}

					@Override
					public URL
					getAbsoluteURL()
					{
						return( absolute_url );
					}

					@Override
					public String
					getHeader()
					{
						return( header );
					}

					@Override
					public InputStream
					getInputStream()
					{
						return( is );
					}

					@Override
					public OutputStream
					getOutputStream()
					{
						return( os );
					}

					@Override
					public AsyncController
					getAsyncController()
					{
						return( async );
					}

					@Override
					public boolean
					canKeepAlive()
					{
						return( original_ka );
					}

					@Override
					public void
					setKeepAlive(
						boolean		ka )
					{
						keep_alive[0] = original_ka && ka;
					}

					@Override
					public boolean
					isActive()
					{
						return( processor.isActive());
					}
				};

			if ( listener.handleExternalRequest( request )){

				return( true );
			}
		}

		return( false );
	}
}
