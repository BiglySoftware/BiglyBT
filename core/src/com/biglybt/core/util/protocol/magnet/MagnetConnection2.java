/*
 * Created on 06-Mar-2005
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

package com.biglybt.core.util.protocol.magnet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.*;
import com.biglybt.core.util.protocol.URLConnectionExt;


/**
 * @author parg
 *
 */

public class
MagnetConnection2
	extends HttpURLConnection
	implements URLConnectionExt
{
	private static final String	NL			= "\r\n";

	static final LinkedList<MagnetOutputStream>		active_os = new LinkedList<>();
	private static TimerEventPeriodic					active_os_event;

	private static void
	addActiveStream(
		MagnetOutputStream		os )
	{
		synchronized( active_os ){

			active_os.add( os );

			if ( active_os.size() == 1 && active_os_event == null ){

				active_os_event =
					SimpleTimer.addPeriodicEvent(
						"mos:checker",
						30*1000,
						new TimerEventPerformer()
						{
							@Override
							public void
							perform(
								TimerEvent event )
							{
								List<MagnetOutputStream>	active;

								synchronized( active_os ){

									active = new ArrayList<>(active_os);
								}

								for ( MagnetOutputStream os: active ){

									os.timerCheck();
								}
							}
						});
			}
		}
	}

	private static void
	removeActiveStream(
		MagnetOutputStream		os )
	{
		synchronized( active_os ){

			active_os.remove( os );

			if ( active_os.size() == 0 && active_os_event != null ){

				active_os_event.cancel();

				active_os_event = null;
			}
		}
	}

	private final MagnetHandler	handler;
	private OutputStream 	output_stream;
	private InputStream 	input_stream;

	private final LinkedList<String>	status_list = new LinkedList<>();

	public
	MagnetConnection2(
		URL		_url,
		MagnetHandler		_handler )
	{
		super( _url );

		handler	= _handler;
	}

	@Override
	public String 
	getFriendlyName()
	{
		//magnet:?xt=urn:btih:MU75MCBLFOA5Y5RGFS3KCU7SNVUPJJQW&dn=BigBuckBunny&xsource=86.17
		
		try{
			String str = url.toExternalForm();
		
			Map<String,String> args = UrlUtils.decodeArgs(  str.substring( str.indexOf( '?' ) + 1 ));
			
			String name = args.get( "dn" );
			
			if ( name == null ){
				
				name = args.get( "xt" );
			}
			
			if ( name != null ){
				
				return( "magnet - " + name );
			}
		}catch( Throwable e ){
			
		}
		
		return( url.toExternalForm());
	}
	
	@Override
	public void
	connect()

		throws IOException
	{
		MagnetOutputStream 	mos = new MagnetOutputStream();
		MagnetInputStream 	mis = new MagnetInputStream( mos );

		input_stream	= mis;
		output_stream 	= mos;

		handler.process( getURL(), mos );
	}

	@Override
	public InputStream
	getInputStream()

		throws IOException
	{
		String	line = "";

		byte[]	buffer = new byte[1];

		byte[]	line_bytes		= new byte[2048];
		int		line_bytes_pos 	= 0;

		while(true){

			int	len = input_stream.read( buffer );

			if ( len == -1 ){

				break;
			}

			line += (char)buffer[0];

			line_bytes[line_bytes_pos++] = buffer[0];

			if ( line.endsWith( NL )){

				line = line.trim();

				if ( line.length() == 0 ){

					break;
				}

				if ( line.startsWith( "X-Report:")){

					line = new String( line_bytes, 0, line_bytes_pos, "UTF-8" );

					line = line.substring( 9 );

					line = line.trim();

					synchronized( status_list ){

						String str = Character.toUpperCase( line.charAt(0)) + line.substring(1);

						if ( status_list.size() == 0 ){

							status_list.addLast( str );

						}else if ( !status_list.getLast().equals( str )){

							status_list.addLast( str );
						}
					}
				}

				line			= "";
				line_bytes_pos	= 0;
			}
		}

		return( input_stream );
	}

	@Override
	public int
	getResponseCode()
	{
		return( HTTP_OK );
	}

	@Override
	public String
	getResponseMessage()
	{
		synchronized( status_list ){

			if ( status_list.size() == 0 ){

				return( "" );

			}else if ( status_list.size() == 1 ){

				return( status_list.get( 0 ));

			}else{

				return( status_list.removeFirst());
			}
		}
	}

	public List<String>
	getResponseMessages(
		boolean	error_only )
	{
		synchronized( status_list ){

			if ( error_only ){

				List<String>	response = new ArrayList<>();

				for ( String s: status_list ){

					if ( s.toLowerCase().startsWith( "error:" )){

						response.add( s );
					}
				}

				return( response );

			}else{

				return(new ArrayList<>(status_list));
			}
		}
	}

	@Override
	public boolean
	usingProxy()
	{
		return( false );
	}

	@Override
	public void
	disconnect()
	{
		try{
			if ( output_stream != null ){
			
				output_stream.close();
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		try{
			if ( input_stream != null ){
				
				input_stream.close();
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	private class
	MagnetOutputStream
		extends OutputStream
	{
		private final LinkedList<byte[]>	buffers 	= new LinkedList<>();
		private int					available;
		private final AESemaphore			buffer_sem 	= new AESemaphore( "mos:buffers" );
		private boolean				closed;

		private long				last_read	= SystemTime.getMonotonousTime();
		private int					read_active;

		private
		MagnetOutputStream()
		{
			addActiveStream( this );
		}

		private void
		timerCheck()
		{
			synchronized( buffers ){

				if ( 	closed ||
						read_active > 0 ||
						SystemTime.getMonotonousTime() - last_read < 60*1000 ){

					return;
				}
			}

			Debug.out( "Abandoning magnet download for " + MagnetConnection2.this.getURL() + " as no active reader" );

			try{
				close();

			}catch( Throwable e ){

			}
		}

		@Override
		public void
		write(
			int b )

			throws IOException
		{
			synchronized( buffers ){

	    		if ( closed ){

	    			throw( new IOException( "Connection closed" ));
	    		}

	    		buffers.addLast( new byte[]{(byte)b});

	    		available++;

	    		buffer_sem.release();
			}
		}

		@Override
		public void
		write(
			byte b[],
			int off,
			int len)

			throws IOException
		{
			synchronized( buffers ){

	    		if ( closed ){

	    			throw( new IOException( "Connection closed" ));
	    		}

	    		if ( len > 0 ){

		    		byte[]	new_b = new byte[len];

		    		System.arraycopy( b, off, new_b, 0, len );

		    		buffers.addLast( new_b );

		    		available += len;

		    		buffer_sem.release();
	    		}
			}
		}

		private int
		read()

			throws IOException
		{
			synchronized( buffers ){

				last_read = SystemTime.getMonotonousTime();

				read_active++;
			}

			try{
				buffer_sem.reserve();

			}finally{

				synchronized( buffers ){

					last_read = SystemTime.getMonotonousTime();

					read_active--;
				}
			}

			synchronized( buffers ){

	    		if ( closed && buffers.size() == 0 ){

	    			return( -1 );
	    		}

	    		byte[] b = buffers.removeFirst();

	    		if ( b.length > 1 ){

	    			for ( int i=b.length-1;i>0;i--){

	    				buffers.addFirst( new byte[]{ b[i] });

	    				buffer_sem.release();
	    			}
	    		}

	    		available--;

	    		return(((int)b[0])&0x000000ff );
			}
		}

		private int
		read(
			byte 	buffer[],
			int 	off,
			int 	len )

			throws IOException
		{
			synchronized( buffers ){

				last_read = SystemTime.getMonotonousTime();

				read_active++;
			}

			try{
				buffer_sem.reserve();

			}finally{

				synchronized( buffers ){

					last_read = SystemTime.getMonotonousTime();

					read_active--;
				}
			}

			synchronized( buffers ){

	    		int	read = 0;

	    		while( true ){

		    		if ( closed && buffers.size() == 0 ){

		    			return( read==0?-1:read );
		    		}

	    			byte[] b = buffers.removeFirst();

	    			int	b_len = b.length;

	    			if ( b_len >= len ){

	    				read += len;

	    				System.arraycopy( b, 0, buffer, off, len );

	    				if ( b_len > len ){

	    					byte[]	new_b = new byte[b_len-len];

	    					System.arraycopy( b, len, new_b, 0, new_b.length );

	    					buffers.addFirst( new_b );

	    					buffer_sem.release();
	    				}

	    				break;

	    			}else{

	    				read += b_len;

	    				System.arraycopy( b, 0, buffer, off, b_len );

	    				off += b_len;
	    				len	-= b_len;
	    			}

	    			if ( !buffer_sem.reserveIfAvailable()){

	    				break;
	    			}
	    		}

	    		available -= read;

	    		return( read );
			}
		}

	    private int
	    available()

	    	throws IOException
	    {
	    	synchronized( buffers ){

	    		if ( available > 0 ){

	    			return( available );
	    		}

	    		if ( closed ){

	    			throw( new IOException( "Connection closed" ));
	    		}

	    		return( 0 );
	    	}
	    }

		@Override
		public void
		close()

			throws IOException
		{
			synchronized( buffers ){

				if ( closed ){

					return;
				}

				closed = true;

				buffer_sem.releaseForever();
			}

			removeActiveStream( this );
		}
	}

	private static class
	MagnetInputStream
		extends InputStream
	{
		private final MagnetOutputStream out;

		private
		MagnetInputStream(
			MagnetOutputStream	_out )
		{
			out = _out;
		}

		@Override
		public int
		read()

			throws IOException
		{
			return( out.read());
		}

		@Override
		public int
		read(
			byte b[],
			int off,
			int len)

				throws IOException
		{
			return( out.read( b, off, len ));
		}

	    @Override
	    public int
	    available()

	    	throws IOException
	    {
	    	return( out.available());
	    }

		@Override
		public long
		skip(
			long n)

			throws IOException
		{
			throw( new IOException( "Not supported" ));
		}

		@Override
		public void
		close()
			throws IOException
		{
			out.close();
		}
	}

	public interface
	MagnetHandler
	{
		public void
		process(
			URL					magnet,
			OutputStream		os )

			throws IOException;
	}
}
