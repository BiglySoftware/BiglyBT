/*
 * Created on Feb 9, 2009
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


package com.biglybt.core.devices.impl;

import com.biglybt.core.util.Constants;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class
TranscodePipeFileSource
	extends TranscodePipe
{
	private static final String NL = "\r\n";

	private File		source_file;

	private RandomAccessFile	raf;
	private int					raf_count;

	protected
	TranscodePipeFileSource(
		File			_source_file,
		errorListener	_error_listener )

		throws IOException
	{
		super( _error_listener );

		source_file	= _source_file;
	}


	@Override
	protected void
	handleSocket(
		Socket		socket )
	{
		synchronized( this ){

			if ( destroyed ){

				try{
					socket.close();

				}catch( Throwable e ){
				}

				return;
			}

			sockets.add( socket );
		}

		try{
			String	command	= null;
			Map<String,String>		headers	= new HashMap<>();

			InputStream		is = socket.getInputStream();
			OutputStream	os = socket.getOutputStream();

			while( true ){

				String	line = "";

				while( !line.endsWith( NL )){

					byte[]	buffer = new byte[1];

					if ( is.read( buffer ) <= 0 ){

						throw( new IOException( "unexpected end of stream" ));
					}

					line += new String( buffer );
				}

				line = line.trim();

				if ( line.length() == 0 ){

					break;
				}

				if ( command == null ){

					command	= line;

				}else{

					int	pos = line.indexOf(':');

					if ( pos == -1 ){

						return;
					}

					String	lhs = line.substring(0,pos).trim().toLowerCase();
					String	rhs = line.substring(pos+1).trim();

					headers.put( lhs, rhs );
				}
			}

			boolean	head	= false;

			//System.out.println( command + ": " + headers );

			if ( command == null ){

				throw( new IOException( "no method supplied" ));

			}else if ( command.startsWith( "GET " )){

			}else if ( command.startsWith( "HEAD " )){

				head	= true;

			}else{

				throw( new IOException( "unsupported method '" + command + "'" ));
			}

			long	file_length = source_file.length();

			if ( head ){

				write( os, "HTTP/1.1 200 OK" + NL );
				write( os, "Server: " + Constants.APP_NAME + " Media Server 1.0" + NL );
				write( os, "Accept-Ranges: bytes" + NL );
				write( os, "Content-Length: " + file_length + NL );
				write( os, "Content-Range: 0-" + (file_length-1) + "/" + file_length + NL );

				os.flush();

			}else{

				String	ranges = (String)headers.get( "range" );

				long	request_start		= 0;
				long	request_length		= 0;

				boolean	request_ok = false;

				if ( ranges == null ){

					write( os, "HTTP/1.1 200 OK" + NL );
					write( os, "Server: " + Constants.APP_NAME + " Media Server 1.0" + NL );
					write( os, "Connection: close" + NL );
					write( os, "Accept-Ranges: bytes" + NL );
					write( os, "Content-Range: 0-" + (file_length-1) + "/" + file_length + NL );
					write( os, "Content-Length: " + file_length + NL + NL );

					request_length	= file_length;

					request_ok = true;

				}else{

					ranges = ranges.toLowerCase();

					if ( !ranges.startsWith("bytes=")){

						throw( new IOException( "invalid range: " + ranges ));
					}

					ranges = ranges.substring( 6 );

					StringTokenizer	tok = new StringTokenizer( ranges, "," );

					if ( tok.countTokens() != 1 ){

						throw( new IOException( "invalid range - only single supported: " + ranges ));
					}

					String	range = tok.nextToken();

					int pos	= range.indexOf('-');

					long	start;
					long	end;

					if ( pos < range.length()-1 ){

						end = Long.parseLong( range.substring(pos+1));

					}else{

						end = file_length-1;
					}

					if ( pos > 0 ){

						start = Long.parseLong( range.substring(0,pos));

					}else{
							// -500 = last 500 bytes of file

						start 	= file_length-end;
						end		= file_length-1;
					}

					request_length = ( end - start ) + 1;

						// prevent seeking too far

					if ( request_length < 0 ){

						write( os, "HTTP/1.1 416 Requested Range Not Satisfiable" + NL + NL );

					}else{

						request_start	= start;

						write(  os, "HTTP/1.1 206 Partial content" + NL );


						write(  os, "Server: " + Constants.APP_NAME + " Media Server 1.0" + NL );
						write(  os, "Connection: close" + NL );
						write(  os, "Content-Range: bytes " + start + "-" + end + "/" + file_length + NL );
						write(  os, "Content-Length: " + request_length + NL + NL );

						request_ok = true;
					}
				}

				os.flush();

				if ( request_ok ){

					handleRAF( os, request_start, request_length );
				}
			}

			synchronized( this ){

				if ( destroyed ){

					try{
						socket.close();

					}catch( Throwable e ){
					}

					try{
						is.close();

					}catch( Throwable e ){
					}

					sockets.remove( socket );

					return;
				}
			}
		}catch( Throwable e ){

			try{
				socket.close();

			}catch( Throwable f ){
			}

			synchronized( this ){

				sockets.remove( socket );
			}
		}
	}

	protected void
	write(
		OutputStream	os,
		String			str )

		throws IOException
	{
		os.write( str.getBytes());
	}

	@Override
	protected RandomAccessFile
	reserveRAF()

		throws IOException
	{
		synchronized( this ){

			if ( destroyed ){

				throw( new IOException( "destroyed" ));
			}

			if ( raf == null ){

				raf = new RandomAccessFile( source_file, "r" );
			}

			raf_count++;

			return( raf );
		}
	}

	@Override
	protected void
	releaseRAF(
		RandomAccessFile	_raf )
	{
		synchronized( this ){

			raf_count--;

			if ( raf_count == 0 ){

				try{
					raf.close();

				}catch( Throwable e ){
				}

				raf = null;
			}
		}
	}

	@Override
	protected boolean
	destroy()
	{
		if ( super.destroy()){

			if ( raf != null ){

				try{
					raf.close();

				}catch( Throwable e ){
				}

				raf = null;
			}

			return( true );
		}

		return( false );
	}
}
