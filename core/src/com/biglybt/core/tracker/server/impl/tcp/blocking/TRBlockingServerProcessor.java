/*
 * Created on 02-Jan-2005
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

package com.biglybt.core.tracker.server.impl.tcp.blocking;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.proxy.AEProxyAddressMapper;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.tracker.server.TRTrackerServerException;
import com.biglybt.core.tracker.server.impl.tcp.TRTrackerServerProcessorTCP;
import com.biglybt.core.tracker.server.impl.tcp.TRTrackerServerTCP;
import com.biglybt.core.util.AETemporaryFileHandler;
import com.biglybt.core.util.Constants;

/**
 * @author parg
 *
 */

public class
TRBlockingServerProcessor
	extends TRTrackerServerProcessorTCP
{
	protected static final int KEEP_ALIVE_SOCKET_TIMEOUT				= 30*1000;

	private static final LogIDs LOGID = LogIDs.TRACKER;
	
	private static final AEProxyAddressMapper proxy_address_mapper = AEProxyFactory.getAddressMapper();

	protected final Socket				socket;


	protected int					timeout_ticks		= 1;
	protected String				current_request;


	protected
	TRBlockingServerProcessor(
		TRTrackerServerTCP		_server,
		Socket					_socket )
	{
		super( _server );

		socket	= _socket;
	}

	@Override
	public void
	runSupport()
	{
		// System.out.println( "Processor starts: " + socket.getRemoteSocketAddress());

		boolean	keep_alive = getServer().isKeepAliveEnabled();

		try{
			InputStream inputStream = socket.getInputStream();
			int available = inputStream.available();
			int bufferSize = available >= 16384 ? 16384 : 8192;
			InputStream	is = new BufferedInputStream(inputStream, bufferSize);

			while( true ){

				setTaskState( "entry" );

				try{
					if ( keep_alive ){

						socket.setSoTimeout( KEEP_ALIVE_SOCKET_TIMEOUT );

						setTimeoutsDisabled( true );

					}else{

						socket.setSoTimeout( SOCKET_TIMEOUT );
					}
				}catch ( Throwable e ){

					// e.printStackTrace();
				}

				setTaskState( "reading header" );

				try{

					byte[]	buffer		 	= new byte[16*1024];
					int		header_pos		= 0;

					while( header_pos < buffer.length ){

						int	len = is.read( buffer, header_pos, 1);

						if ( len != 1 ){

							throw( new Exception( "Premature end of stream reading header" ));
						}

						header_pos++;

						if ( 	header_pos >= 4 &&
								buffer[header_pos-4] == CR &&
								buffer[header_pos-3] == FF &&
								buffer[header_pos-2] == CR &&
								buffer[header_pos-1] == FF ){

							break;
						}
					}

					String	header = new String( buffer, 0, header_pos, Constants.BYTE_ENCODING_CHARSET );

					if ( Logger.isEnabled()){

						String	log_str = header;

						int	pos = log_str.indexOf( NL );

						if ( pos != -1 ){

							log_str = log_str.substring(0,pos);
						}

						Logger.log(new LogEvent(LOGID, "Tracker Server: received header '"
								+ log_str + "' from " + socket.getRemoteSocketAddress()));
					}

					// System.out.println( "got header:" + header );

					InputStream	post_is 	= null;
					File		post_file	= null;

					String	lowercase_header;

					boolean	head	= false;

					int	url_start;


					if ( header.startsWith( "GET " )){

						timeout_ticks		= 1;

						lowercase_header	= header.toLowerCase();
						url_start			= 4;

					}else if ( header.startsWith( "HEAD " )){

						timeout_ticks		= 1;

						lowercase_header	= header.toLowerCase();
						url_start			= 5;

						head	= true;

					}else if ( header.startsWith( "POST ")){

						timeout_ticks	= TRTrackerServerTCP.PROCESSING_POST_MULTIPLIER;

						if ( timeout_ticks == 0 ){

							setTimeoutsDisabled( true );
						}

						setTaskState( "reading content" );

						lowercase_header	= header.toLowerCase();
						url_start			= 5;

						String cl_str = getHeaderField( header, lowercase_header, "content-length:" );

						boolean chunk_read = false;
						if ( cl_str == null ){

							String transfer_encoding_str = getHeaderField(header,
									lowercase_header, "transfer-encoding: ");
							chunk_read = transfer_encoding_str != null
									&& transfer_encoding_str.equalsIgnoreCase("chunked");

							cl_str = "0";
						}

						int content_length = Integer.parseInt( cl_str );

						ByteArrayOutputStream	baos		= null;
						FileOutputStream		fos			= null;

						try{
							OutputStream	data_os;

							if ( content_length <= 256*1024 ){

								baos = new ByteArrayOutputStream();

								data_os	= baos;

							}else{

								post_file	= AETemporaryFileHandler.createTempFile();

								post_file.deleteOnExit();

								fos	= new FileOutputStream( post_file );

								data_os	= fos;
							}
							if (chunk_read) {
								while (true) {
									// Read Chunk Size and CRLF
									int chunkSize = -1;
  								while (true) {
  									int val = is.read();
  									if (val == -1) {
  										throw( new TRTrackerServerException( "premature end of input stream (chunksize)" ));
  									}
  									if (val == '\n') {
  										break;
  									}
  									if (val != '\r') {
  										if (chunkSize == -1) {
  											chunkSize = 0;
  										} else {
  											chunkSize <<= 4;
  										}
  										chunkSize += Character.digit(val, 16);
  									}
  								}
  								if (chunkSize == -1) {
  									throw( new TRTrackerServerException( "invalid chunk size" ));
  								}
  								if (chunkSize == 0) {
  									// terminating chunk, clean up last CRLF
  									boolean bad = is.read() == -1 || is.read() == -1;
  									if (bad) {
  										throw( new TRTrackerServerException( "premature end of input stream (NoTerminatingChunk)" ));
  									}
  									break;
  								}
  								// Read Chunk data
  								while (chunkSize > 0) {
  									int	len = is.read( buffer, 0, Math.min(chunkSize,buffer.length));
  									if (len < 0) {
  										throw( new TRTrackerServerException( "premature end of input stream" ));
  									}
  									data_os.write( buffer, 0, len );
  									chunkSize -= len;
  								}
  								// Cleanup Chunk Terminator CRLF
									boolean bad = is.read() == -1 || is.read() == -1;
  								if (bad) {
  									throw( new TRTrackerServerException( "premature end of input stream (NoChunkEndMarker)" ));
  								}
								}
							}

							while( content_length > 0 ){

								int	len = is.read( buffer, 0, Math.min( content_length, buffer.length ));

								if ( len < 0 ){

									throw( new TRTrackerServerException( "premature end of input stream" ));
								}

								data_os.write( buffer, 0, len );

								content_length -= len;
							}

							if ( baos != null ){

								post_is = new ByteArrayInputStream(baos.toByteArray());

							}else{

								fos.close();

								fos = null;

								post_is = new BufferedInputStream( new FileInputStream( post_file ), 256*1024 );
							}

						// System.out.println( "TRTrackerServerProcessorTCP: request data = " + baos.size());

						}finally{
							// tidy up open streams
							if ( baos != null ){
								try{
									baos.close();
								}catch( Throwable e ){
								}
							}
							if ( fos != null ){
								try{
									fos.close();
								}catch( Throwable e ){
								}
							}
								// handle failure case - we should always have a post_is here, if not then
								// we've errored so delete any temp file
							if ( post_is == null && post_file != null ){
								post_file.delete();
							}
						}
					}else{

						int	pos = header.indexOf(' ');

						if ( pos == -1 ){

							throw( new TRTrackerServerException( "header doesn't have space in right place" ));
						}

						timeout_ticks		= 1;

						lowercase_header	= header.toLowerCase();
						url_start			= pos+1;
					}

					setTaskState( "processing request" );

					current_request	= header;

					try{
						if ( post_is == null ){

								// set up a default input stream

							post_is = new ByteArrayInputStream(new byte[0]);
						}

						int	url_end = header.indexOf( " ", url_start );

						if ( url_end == -1 ){

							throw( new TRTrackerServerException( "header doesn't have space in right place" ));
						}

						String url = header.substring( url_start, url_end ).trim();

						int	nl_pos = header.indexOf( NL, url_end );

						if ( nl_pos == -1 ){

							throw( new TRTrackerServerException( "header doesn't have nl in right place" ));
						}

						String	http_ver = header.substring( url_end, nl_pos ).trim();


						String con_str = getHeaderField( header, lowercase_header, "connection:" );

						if ( con_str == null ){

							if ( http_ver.equalsIgnoreCase( "HTTP/1.0" )){

								keep_alive = false;
							}
						}else if ( con_str.equalsIgnoreCase( "close" )){

							keep_alive = false;
						}
						
						InetSocketAddress local_sa 	= (InetSocketAddress)socket.getLocalSocketAddress();
						InetSocketAddress remote_sa = (InetSocketAddress)socket.getRemoteSocketAddress();
						
						AEProxyAddressMapper.AppliedPortMapping applied_mapping = proxy_address_mapper.applyPortMapping( remote_sa.getAddress(), remote_sa.getPort());

						remote_sa = applied_mapping.getRemoteAddress();


						if ( head ){

							ByteArrayOutputStream	head_response = new ByteArrayOutputStream(4096);

							if ( !processRequest(
										header,
										lowercase_header,
										url,
										local_sa,
										remote_sa,
										false,
										keep_alive,
										post_is,
										head_response,
										null )){

								keep_alive = false;
							}

							byte[]	head_data = head_response.toByteArray();

							int	header_length = head_data.length;

							for (int i=3;i<head_data.length;i++){

								if ( 	head_data[i-3] 	== CR &&
										head_data[i-2] 	== FF &&
										head_data[i-1] 	== CR &&
										head_data[i]	== FF ){

									header_length = i+1;

									break;
								}
							}

							setTaskState( "writing head response" );

							socket.getOutputStream().write( head_data, 0, header_length );

							socket.getOutputStream().flush();

						}else{

							if( !processRequest(
										header,
										lowercase_header,
										url,
										local_sa,
										remote_sa,
										false,
										keep_alive,
										post_is,
										socket.getOutputStream(),
										null )){

								keep_alive = false;
							}
						}
					}finally{

						if ( post_is != null ){

							post_is.close();
						}

						if ( post_file != null ){

							post_file.delete();
						}
					}
				}catch( Throwable e ){

					keep_alive = false;

					 // e.printStackTrace();
				}

				if ( !keep_alive ){

					break;
				}
			}
		}catch( Throwable e ){

		}finally{

			setTaskState( "final socket close" );

			try{
				socket.close();

			}catch( Throwable e ){

				// e.printStackTrace();
			}

			// System.out.println( "Processor ends: " + socket.getRemoteSocketAddress());
		}
	}

	protected String
	getHeaderField(
		String		header,
		String		lc_header,
		String		field )
	{
		int	start = lc_header.indexOf( field );

		if ( start == -1 ){

			return( null );
		}

		int	end = header.indexOf( NL, start );

		if ( end == -1 ){

			return( null );
		}

		return( header.substring(start+field.length(),end ).trim());

	}

	@Override
	public boolean
	isActive()
	{
		try{
				// not really much use anyway as the default for keep alive timer is 2 hours or more...

			if ( !socket.getKeepAlive()){

				socket.setKeepAlive( true );
			}

		}catch( Throwable e ){
		}

		return( !socket.isClosed());
	}

	@Override
	public void
	interruptTask()
	{
		try{
			if ( areTimeoutsDisabled() ){

				// Debug.out( "External tracker request timeout ignored: state = " + getTaskState() + ", req = " + current_request  );

			}else{

				timeout_ticks--;

				if ( timeout_ticks <= 0 ){

					System.out.println( "Tracker task interrupted in state '" + getTaskState() + "' : processing time limit exceeded for " + socket.getInetAddress() );

					socket.close();
				}
			}

		}catch( Throwable e ){

			// e.printStackTrace();
		}
	}

}
