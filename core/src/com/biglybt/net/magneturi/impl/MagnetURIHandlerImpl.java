/*
 * Created on 03-Mar-2005
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

package com.biglybt.net.magneturi.impl;

import java.io.*;
import java.net.*;
import java.util.*;

import org.gudy.bouncycastle.util.encoders.Base64;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.core.util.png.PNG;
import com.biglybt.net.magneturi.MagnetURIHandler;
import com.biglybt.net.magneturi.MagnetURIHandlerListener;
import com.biglybt.net.magneturi.MagnetURIHandlerProgressListener;

/**
 * @author parg
 *
 */

public class
MagnetURIHandlerImpl
	extends MagnetURIHandler
{
  private static final LogIDs LOGID = LogIDs.NET;
		// see http://magnet-uri.sourceforge.net/magnet-draft-overview.txt

	private static MagnetURIHandlerImpl		singleton;

	private static AEMonitor				class_mon = new AEMonitor( "MagnetURLHandler:class" );

	private static final int				DOWNLOAD_TIMEOUT	= -1;	// use plugin default timeout

	protected static final String	NL			= "\015\012";

	private final static boolean DEBUG = false;

	public static MagnetURIHandler
	getSingleton()
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton	= new MagnetURIHandlerImpl();
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	private int		port;

	private CopyOnWriteList<MagnetURIHandlerListener>	listeners	= new CopyOnWriteList<>();

	private Map		info_map 	= new HashMap();

	private Map<String,ResourceProvider>	resources = new HashMap<>();

	protected
	MagnetURIHandlerImpl()
	{
		ServerSocket	socket	= null;

		for (int i=45100;i<=45199;i++){

			try{

			   socket = new ServerSocket(i, 50, InetAddress.getByName("127.0.0.1"));

			   port	= i;

			   break;

			}catch( Throwable e ){

			}
		}

		COConfigurationManager.setIntDefault( "magnet.uri.port", port );

		COConfigurationManager.registerExportedParameter( "magnet.port", "magnet.uri.port");

		if ( socket == null ){

			// no free sockets, not much we can do
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
						"MagnetURI: no free sockets, giving up"));

		}else{
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "MagnetURI: bound on "
						+ socket.getLocalPort()));

			final ServerSocket	f_socket = socket;

			Thread t =
				new AEThread("MagnetURIHandler")
				{
					@Override
					public void
					runSupport()
					{
						int	errors 	= 0;
						int	ok		= 0;

						while(true){

							try{

								final Socket sck = f_socket.accept();

								ok++;

								errors	= 0;

								new AEThread2( "MagnetURIHandler:processor",true)
									{
										@Override
										public void
										run()
										{
											boolean	close_socket	= true;

											try{
											        String address = sck.getInetAddress().getHostAddress();

										        if ( address.equals("localhost") || address.equals("127.0.0.1")) {

										        	BufferedReader br = new BufferedReader(new InputStreamReader(sck.getInputStream(),Constants.DEFAULT_ENCODING_CHARSET));

										        	String line = br.readLine();

										        	if (DEBUG) {
										        		System.out.println("=====");
										        		System.out.println("Traffic Class: "
										        				+ sck.getTrafficClass());
										        		System.out.println("OS: " + sck.getOutputStream());
										        		System.out.println("isBound? " + sck.isBound()
										        				+ "; isClosed=" + sck.isClosed() + "; isConn="
										        				+ sck.isConnected() + ";isIShutD "
										        				+ sck.isInputShutdown() + ";isOShutD "
										        				+ sck.isOutputShutdown());
										        		System.out.println("- - - -");
										        		System.out.println(line);

										        		while (br.ready()) {
										        			String extraline = br.readLine();
										        			System.out.println(extraline);
										        		}
										        		System.out.println("=====");
										        	}


										        	if ( line != null ){

											        	if ( line.toUpperCase().startsWith( "GET " )){

											        		Logger.log(new LogEvent(LOGID,
											        					"MagnetURIHandler: processing '" + line + "'"));

											        		line = line.substring(4);

											        		int	pos = line.lastIndexOf(' ');

											        		line = line.substring( 0, pos );

											        		close_socket = process( line, br, sck.getOutputStream() );

											        	}else{

															Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
																		"MagnetURIHandler: invalid command - '" + line
																			+ "'"));
											        	}
										        	}else{

											       		Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
											       				"MagnetURIHandler: connect from "
											       				+ "'" + address + "': no data read"));

										        	}

											   }else{

											      	Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
											      				"MagnetURIHandler: connect from "
											       				+ "invalid address '" + address + "'"));
										        }
											}catch( Throwable e ){

												if ( !(e instanceof IOException || e instanceof SocketException )){

													Debug.printStackTrace(e);
												}
											}finally{

												try{
														// leave client to close socket if not requested

													if ( close_socket ){

														sck.close();
													}

												}catch( Throwable e ){
												}
											}
										}
									}.start();


							}catch( Throwable e ){

								Debug.printStackTrace(e);

								errors++;

								if ( errors > 100 ){

									if (Logger.isEnabled()){
										Logger.log(new LogEvent(LOGID,
										"MagnetURIHandler: bailing out, too many socket errors"));
									}

									break;
								}
							}
						}
					}
				};

			t.setDaemon( true );

			t.start();
		}
	}

	@Override
	public void
	process(
		final String			get,
		final InputStream		is,
		final OutputStream		os )

		throws IOException
	{
		new AEThread2( "MagnetProcessor", true )
		{
			@Override
			public void
			run()
			{
				boolean	close = false;

				try{
					long start = SystemTime.getMonotonousTime();
					
					while( !CoreFactory.isCoreRunning()){
					
						if ( SystemTime.getMonotonousTime() - start > 60*1000 ){
						
							Debug.out( "Timeout waiting for core to start" );
							
							break;
						}
						
						try{
							Thread.sleep(1000);
							
						}catch( Throwable e ){
							
						}
					}
					
					close = process( get, new BufferedReader( new InputStreamReader( is )), os );

				}catch( Throwable e ){

					Debug.out( "Magnet processing failed", e );

				}finally{

					if ( close ){

						try{
							is.close();

						}catch( Throwable e ){

							Debug.out( e );
						}
					}

					try{
						os.flush();

					}catch( Throwable e ){

						Debug.out( e );
					}

					if ( close ){

						try{
							os.close();

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			}
		}.start();
	}

	protected boolean
	process(
		String			get,
		BufferedReader	is,
		OutputStream	os )

		throws IOException
	{
		//System.out.println( "get = " + get );

			// magnet:?xt=urn:sha1:YNCKHTQCWBTRNJIV4WNAE52SJUQCZO5C

		Map<String,String>		original_params 			= new HashMap<>();
		Map<String,String>		lc_params					= new HashMap<>();

		List<String> 	source_params	= new ArrayList<>();

		int	pos	= get.indexOf( '?' );

		String	arg_str;

		if ( pos == -1 ){

			arg_str = "";

		}else{

			arg_str = get.substring( pos+1 );

			pos = arg_str.lastIndexOf( ' ' );

			if ( pos >= 0 ){

				arg_str = arg_str.substring( 0, pos ).trim();
			}

			StringTokenizer	tok = new StringTokenizer( arg_str, "&" );

			if (DEBUG) {
				System.out.println("params:" + arg_str );
			}

			while( tok.hasMoreTokens()){

				String	arg = tok.nextToken();

				pos	= arg.indexOf( '=' );

				if ( pos == -1 ){

					String lhs = arg.trim();

					original_params.put( lhs, "" );

					lc_params.put( lhs.toLowerCase( Locale.US ), "" );

				}else{

					try{
						String	lhs 	= arg.substring( 0, pos ).trim();
						String	lc_lhs 	= lhs.toLowerCase( Locale.US );

						String	rhs = UrlUtils.decode( arg.substring( pos+1 ).trim());

						if ( lc_lhs.equals( "xt" )){

								// currently we always take the btih over btmh
							
							String lc_rhs = rhs.toLowerCase( Locale.US );
							
							if ( lc_rhs.startsWith( "urn:btih:" )){

								original_params.put( lhs, rhs );

								lc_params.put( lc_lhs, rhs );

							}else{

								String existing = lc_params.get( "xt" );

								if ( existing == null ){
									
									original_params.put( lhs, rhs );

									lc_params.put( lc_lhs, rhs );
									
								}else{
									
									String lc_existing =  existing.toLowerCase( Locale.US );
									
									if ( lc_existing.startsWith( "urn:btih:" ) || lc_existing.startsWith( "urn:btmh:" )){
										
											// keep these
									}else{

										original_params.put( lhs, rhs );

										lc_params.put( lc_lhs, rhs );
									}
								}
							}
						}else{

							original_params.put( lhs, rhs );

							lc_params.put( lc_lhs, rhs );

							if ( lc_lhs.equals( "xsource" )){

								source_params.add( rhs );
							}
						}
					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}
			}
		}


		if ( get.startsWith( "/magnet10/badge.img" )){

			for ( MagnetURIHandlerListener listener: listeners ){

				byte[]	data = listener.badge();

				if ( data != null ){

					writeReply( os, "image/gif", data );

					return( true );
				}
			}

			writeNotFound( os );

			return( true );

		}else if ( get.startsWith( "/magnet10/canHandle.img?" )){

			String urn = (String)lc_params.get( "xt" );

			if ( urn != null && ( urn.toLowerCase( Locale.US).startsWith( "urn:btih:") || urn.toLowerCase( Locale.US).startsWith( "urn:btmh:"))){

				for ( MagnetURIHandlerListener listener: listeners ){

					byte[]	data = listener.badge();

					if ( data != null ){

						writeReply( os, "image/gif", data );

						return( true );
					}
				}
			}

			writeNotFound( os );

			return( true );

		}else if ( get.startsWith( "/azversion" )){

			writeReply( os, "text/plain", Constants.BIGLYBT_VERSION );

			return( true );

		}else if ( 	get.startsWith( "/magnet10/options.js?" ) ||
					get.startsWith( "/magnet10/default.js?" )){

			String	resp = "";

			resp += getJS( "magnetOptionsPreamble" );

			resp += getJSS( "<a href=\\\"http://127.0.0.1:\"+(45100+magnetCurrentSlot)+\"/select/?\"+magnetQueryString+\"\\\" target=\\\"_blank\\\">" );
			resp += getJSS( "<img src=\\\"http://127.0.0.1:\"+(45100+magnetCurrentSlot)+\"/magnet10/badge.img\\\">" );
			resp += getJSS( "Download with " + Constants.APP_NAME );
			resp += getJSS( "</a>" );

			resp += getJS( "magnetOptionsPostamble" );

			resp += "magnetOptionsPollSuccesses++";

			writeReply( os, "application/x-javascript", resp );

			return( true );

		}else if ( get.startsWith( "/magnet10/pause" )){

			try{
				Thread.sleep( 250 );

			}catch( Throwable e ){

			}
			writeNotFound( os );

			return( true );

		}else if ( get.startsWith( "/select/" )){

			String	fail_reason = "";

			boolean	ok = false;

			String urn = (String)lc_params.get( "xt" );

			if ( urn == null ){

				fail_reason	= "xt missing";

			}else{

				String	lc_urn = urn.toLowerCase( Locale.US  );

				try{

					URL	url;

					if ( lc_urn.startsWith( "http:") || lc_urn.startsWith( "https:" )){

						url = new URL( urn );

					}else{

						url = new URL( "magnet:?xt=" + urn );
					}

					for ( MagnetURIHandlerListener listener: listeners ){

						if ( listener.download( url )){

							ok = true;

							break;
						}
					}

					if ( !ok ){

						fail_reason = "No listeners accepted the operation";
					}
				}catch( Throwable e ){

					Debug.printStackTrace(e);

					fail_reason	= Debug.getNestedExceptionMessage(e);
				}
			}

			if ( ok ){

				if ( "image".equalsIgnoreCase((String)lc_params.get( "result" ))){

					for ( MagnetURIHandlerListener listener: listeners ){

						byte[]	data = listener.badge();

						if ( data != null ){

							writeReply( os, "image/gif", data );

							return( true );
						}
					}
				}

				writeReply( os, "text/plain", "Download initiated" );

			}else{

				writeReply( os, "text/plain", "Download initiation failed: " + fail_reason );
			}

		}else if ( get.startsWith( "/download/" )){

			final PrintWriter	pw = new PrintWriter( new OutputStreamWriter( os, "UTF-8" ));

			try{
				pw.print( "HTTP/1.0 200 OK" + NL );

				pw.flush();

				String urn = (String)lc_params.get( "xt" );

				if ( 	urn == null || 
						!( 	urn.toLowerCase( Locale.US ).startsWith( "urn:sha1:") || 
							urn.toLowerCase( Locale.US ).startsWith( "urn:btih:") ||
							urn.toLowerCase( Locale.US ).startsWith( "urn:btmh:"))){
					
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
								"MagnetURIHandler: " + "invalid command - '" + get + "'"));

					throw( new IOException( "Invalid magnet URI - no urn:sha1, urn:btih or urn:btmh argument supplied." ));
				}

				String	encoded = urn.substring(9);

				List<InetSocketAddress>	sources = new ArrayList<>();

				for (int i=0;i<source_params.size();i++){

					String	source = (String)source_params.get(i);

					int	p = source.lastIndexOf(':');

					if ( p != -1 ){

						try{
							String 	host 	= source.substring(0,p);
							int		port 	= Integer.parseInt( source.substring(p+1));

								// deal with somwe borked "/ip-address;:port strings

							if ( host.startsWith( "/" )){

								host = host.substring(1);
							}

							if ( host.startsWith( "[" )){
								
									// IPv6 literal
								
								host = host.substring( 1,  host.length() - 1 );
							}
							
							InetSocketAddress	sa = new InetSocketAddress( host, port );

							sources.add( sa );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}

				final InetSocketAddress[]	s = sources.toArray( new InetSocketAddress[ sources.size()] );

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "MagnetURIHandler: download of '"
							+ encoded + "' starts (initial sources=" + s.length + ")"));

				byte[] _magnet_hash = UrlUtils.decodeTruncatedHashFromMagnetURI( encoded );

				if ( _magnet_hash == null ){

					_magnet_hash = new byte[20];	// dummy to still allow &fl links to work...

					//throw( new Exception( "Invalid info hash '" + encoded + "'" ));
				}

				final byte[] magnet_hash = _magnet_hash;

				byte[] data = null;

				String verbose_str = lc_params.get( "verbose" );

				final boolean verbose = verbose_str != null && verbose_str.equalsIgnoreCase( "true" );

				final boolean[]	cancel = { false };

				TimerEventPeriodic	keep_alive =
					SimpleTimer.addPeriodicEvent(
						"MURI:keepalive",
						5000,
						new TimerEventPerformer()
						{
							@Override
							public void
							perform(
								TimerEvent event)
							{
								pw.print( "X-KeepAlive: YEAH!" + NL );

								boolean	failed = pw.checkError();

								if ( failed ){

									synchronized( cancel ){

										cancel[0]	= true;
									}
								}
							}
						});

				try{
					final String f_arg_str = arg_str;

					final byte[][] 		f_data 	= { null };
					final Throwable[]	f_error = { null };

					final AESemaphore wait_sem = new AESemaphore( "download-waiter" );

					List<Runnable> tasks = new ArrayList<>();

					for ( final MagnetURIHandlerListener listener: listeners ){

						tasks.add(
							new Runnable()
							{
								@Override
								public void
								run()
								{
									try{
										byte[] data =
											listener.download(
												new MagnetURIHandlerProgressListener()
												{
													@Override
													public void
													reportSize(
														long	size )
													{
														pw.print( "X-Report: " + getMessageText( "torrent_size", String.valueOf( size )) + NL );

														pw.flush();
													}

													@Override
													public void
													reportActivity(
														String	str )
													{
														pw.print( "X-Report: " + str + NL );

														pw.flush();
													}

													@Override
													public void
													reportCompleteness(
														int		percent )
													{
														pw.print( "X-Report: " + getMessageText( "percent", String.valueOf(percent)) + NL );

														pw.flush();
													}

													@Override
													public boolean
													verbose()
													{
														return( verbose );
													}

													@Override
													public boolean
													cancelled()
													{
														synchronized( cancel ){

															if ( cancel[0] ){

																return( true );
															}
														}

														synchronized( f_data ){

															return( f_data[0] != null );
														}
													}
												},
												magnet_hash,
												f_arg_str,
												s,
												DOWNLOAD_TIMEOUT );

										synchronized( f_data ){

											if ( data != null ){

												if ( f_data[0] == null ){

													f_data[0] = data;
												}
											}
										}
									}catch( Throwable e ){

										synchronized( f_data ){

											f_error[0] = e;
										}

									}finally{

										wait_sem.release();
									}
								}
							});
					}

					if ( tasks.size() > 0 ){

						if ( tasks.size() == 1 ){

							tasks.get(0).run();

						}else{

							for ( final Runnable task: tasks ){

								new AEThread2( "MUH:dasync" )
								{
									@Override
									public void
									run()
									{
										task.run();
									}
								}.start();
							}

							for ( int i=0; i<tasks.size(); i++ ){

								wait_sem.reserve();
							}
						}

						synchronized( f_data ){

							data = f_data[0];

							if ( data == null ){

								if ( f_error[0] != null ){

									throw( f_error[0] );
								}
							}
						}
					}

				}finally{

					keep_alive.cancel();
				}

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "MagnetURIHandler: download of '"
							+ encoded
							+ "' completes, data "
							+ (data == null ? "not found"
									: ("found, length = " + data.length))));

				if ( data != null ){

					pw.print( "Content-Length: " + data.length + NL + NL );

					pw.flush();

					os.write( data );

					os.flush();

				}else{

						// HACK: don't change the "error:" message below, it is used by TorrentDownloader to detect this
						// condition

					pw.print( "X-Report: error: " + getMessageText( "no_sources" ) + NL );

					pw.flush();

						// pause on error

					return( !lc_params.containsKey( "pause_on_error" ));
				}
			}catch( Throwable e ){

					// don't remove the "error:" (see above)

				pw.print( "X-Report: error: " + getMessageText( "error", Debug.getNestedExceptionMessage(e)) + NL );

				pw.flush();

				// Debug.printStackTrace(e);

					// pause on error

				return( !lc_params.containsKey( "pause_on_error" ));
			}
		}else if ( get.startsWith( "/getinfo?" )){

			String name = (String)lc_params.get( "name" );

			if ( name != null ){

				Integer	info = (Integer)info_map.get( name );

				int	value = Integer.MIN_VALUE;

				if ( info != null ){

					value = info.intValue();

				}else{

					for ( MagnetURIHandlerListener listener: listeners ){

							// no idea why we copy, but let's keep doing so

						HashMap paramsCopy = new HashMap();

						paramsCopy.putAll( original_params);

						value = listener.get( name, paramsCopy );

						if ( value != Integer.MIN_VALUE ){

							break;
						}
					}
				}

				if ( value == Integer.MIN_VALUE ){

						// no value, see if we have a default

					String	def_str = (String)lc_params.get( "default" );

					if ( def_str != null ){

						try{
							value = Integer.parseInt( def_str );

						}catch( Throwable e ){

							Debug.printStackTrace( e );
						}
					}
				}else{

						// have a value, see if we have a max

					String	max_str = (String)lc_params.get( "max" );

					if ( max_str != null ){

						try{
							int	max = Integer.parseInt( max_str );

							if ( value > max ){

								value = max;
							}
						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}

				if ( value != Integer.MIN_VALUE ){

					if ( value < 0 ){

						value = 0;
					}

						// need to trim if too large

					if ( value > 1024 * 1024 ){

						value = 1024 * 1024;
					}

						// see if we need to div/mod for clients that don't support huge images
						// e.g. http://localhost:45100/getinfo?name=Plugin.azupnpav.content_port&mod=8

					int	width 	= value;
					int	height	= 1;

						// divmod -> encode div+1 as width, mod+1 as height

					String	div_mod = (String)lc_params.get( "divmod" );

					if ( div_mod != null ){

						int	n = Integer.parseInt( div_mod );

						width 	= ( value / n ) + 1;
						height	= ( value % n ) + 1;

					}else{

						String	div = (String)lc_params.get( "div" );

						if ( div != null ){

							width = value / Integer.parseInt( div );

						}else{

							String	mod = (String)lc_params.get( "mod" );

							if ( mod != null ){

								width = value % Integer.parseInt( mod );
							}
						}
					}

					String	img_type = (String)lc_params.get( "img_type" );

					if ( img_type != null && img_type.equals( "png" )){

						byte[]	data = PNG.getPNGBytesForSize( width, height );

						writeReply( os, "image/png", data );

					}else{

						ByteArrayOutputStream	baos = new ByteArrayOutputStream();

						writeImage( baos, width, height );

						byte[]	data = baos.toByteArray();

						writeReply( os, "image/bmp", data );
					}

					return( true );
				}
			}

			writeNotFound( os );

			return( true );

		}else if ( get.startsWith( "/setinfo?" )){

			String name 	= (String)lc_params.get( "name" );

			if ( name != null ){

				boolean	result = false;

				for ( MagnetURIHandlerListener listener: listeners ){

						// no idea why we copy, but let's keep on doing so

					HashMap paramsCopy = new HashMap();

					paramsCopy.putAll( original_params );

					result = listener.set( name, paramsCopy );

					if ( result ){

						break;
					}
				}

				int	width 	= result?20:10;
				int height 	= result?20:10;

				String	img_type = (String)lc_params.get( "img_type" );

				if ( img_type != null && img_type.equals( "png" )){

					byte[]	data = PNG.getPNGBytesForSize( width, height );

					writeReply( os, "image/png", data );

				}else{

					ByteArrayOutputStream	baos = new ByteArrayOutputStream();

					writeImage( baos, width, height);

					byte[]	data = baos.toByteArray();

					writeReply( os, "image/bmp", data );
				}

				return( true );
			}
		}else if ( get.equals( "/browserheaders.js" )){

			String	headers_str = "";

			while( true ){

				String	header = is.readLine();

				if ( header == null ){

					break;
				}

				header = header.trim();

				if ( header.length() == 0 ){

					break;
				}

				headers_str += (headers_str.length()==0?"":"\n") + header;
			}

			String script = "var headers = \"" + new String( Base64.encode( headers_str.getBytes( "UTF-8" ))) + "\";";


			writeReply( os, "application/x-javascript", script );

		}else if ( get.startsWith( "/resource." )){

			String rid = lc_params.get( "rid" );

			ResourceProvider provider;

			synchronized( resources ){

				provider = resources.get( rid );
			}

			if ( provider != null ){

				byte[] data = provider.getData();

				if ( data != null ){

					writeReply( os, HTTPUtils.guessContentTypeFromFileType( provider.getFileType()), data );

				}else{

					writeNotFound( os );
				}
			}else{

				writeNotFound( os );
			}
		}

		return( true );
	}

	/**
	 * @param os
	 * @param width
	 * @param height
	 *
	 * @since 3.0.2.1
	 */
	private void writeImage(OutputStream os, int width, int height) {

			// DON'T CHANGE ANY OF THIS WITHOUT (AT LEAST) CHANGING THE JWS LAUNCHER CODE
			// AS IT MANUALLY DECODES THE BMP TO DETERMINE ITS SIZE!!!!

		int rowWidth = width / 8;
		if ((rowWidth % 4) != 0) {
			rowWidth = ((rowWidth / 4) + 1) * 4;
		}
		int imageSize = rowWidth * height;
		int fileSize = 54 + imageSize;
		try {
			os.write(new byte[] {
				'B',
				'M'
			});
			write4Bytes(os, fileSize);
			write4Bytes(os, 0);
			write4Bytes(os, 54); // data pos

			write4Bytes(os, 40); // header size
			write4Bytes(os, width);
			write4Bytes(os, height);
			write4Bytes(os, (1 << 16) + 1); // 1 plane and 1 bpp color
			write4Bytes(os, 0);
			write4Bytes(os, imageSize);
			write4Bytes(os, 0);
			write4Bytes(os, 0);
			write4Bytes(os, 0);
			write4Bytes(os, 0);

			byte[] data = new byte[imageSize];
			os.write(data);

		} catch (IOException e) {
			Debug.out(e);
		}
	}

	private void write4Bytes(OutputStream os, long l) {
		try {
			os.write((int) (l & 0xFF));
			os.write((int) ((l >> 8) & 0xFF));
			os.write((int) ((l >> 16) & 0xFF));
			os.write((int) ((l >> 24) & 0xFF));
		} catch (IOException e) {
			Debug.out(e);
		}
	}

	protected String
	getMessageText(
		String	resource )
	{
		return( MessageText.getString( "MagnetURLHandler.report." + resource ));
	}

	protected String
	getMessageText(
		String	resource,
		String	param )
	{
		if ( resource.equals( "error" )){

				// changed this as getting nonsense messages prefixed with keyword 'error'

			return( param );
		}

		return( MessageText.getString( "MagnetURLHandler.report." + resource, new String[]{ param } ));
	}

	protected String
	getJS(
		String	s )
	{
		return( "document.write(" + s + ");" + NL );
	}

	protected String
	getJSS(
		String	s )
	{
		return( "document.write(\"" + s + "\");" + NL );
	}

	protected void
	writeReply(
		OutputStream		os,
		String				content_type,
		String				content )

		throws IOException
	{
		writeReply( os, content_type, content.getBytes());
	}

	protected void
	writeReply(
		OutputStream		os,
		String				content_type,
		byte[]				content )

		throws IOException
	{
		PrintWriter	pw = new PrintWriter( new OutputStreamWriter( os ));

		pw.print( "HTTP/1.1 200 OK" + NL );
		pw.print( "Cache-Control: no-cache" + NL );
		pw.print( "Pragma: no-cache" + NL );
		pw.print( "Content-Type: " + content_type + NL );
		pw.print( "Content-Length: " + content.length + NL + NL );

		pw.flush();

		os.write( content );

	}

	protected void
	writeNotFound(
		OutputStream		os )

		throws IOException
	{
		PrintWriter	pw = new PrintWriter( new OutputStreamWriter( os ));

		pw.print( "HTTP/1.0 404 Not Found" + NL + NL );

		pw.flush();
	}

	@Override
	public int
	getPort()
	{
		return( port );
	}

	@Override
	public void
	addInfo(
		String		name,
		int			info )
	{
		info_map.put( name, new Integer(info));

		Logger.log(new LogEvent(LOGID, LogEvent.LT_INFORMATION,"MagnetURIHandler: global info registered: " + name + " -> " + info ));
	}

	@Override
	public void
	addListener(
		MagnetURIHandlerListener	l )
	{
		listeners.add( l );
	}

	@Override
	public void
	removeListener(
		MagnetURIHandlerListener	l )
	{
		listeners.remove( l );
	}

	public static void
	main(
		String[]	args )
	{
		new MagnetURIHandlerImpl();

		try{
			Thread.sleep(1000000);
		}catch( Throwable e ){

		}
	}

	@Override
	public URL
	registerResource(
		ResourceProvider		provider )
	{
		try{
			String rid = URLEncoder.encode( provider.getUID(), "UTF-8" );

			synchronized( resources ){

				resources.put( rid, provider );
			}

			return( new URL( "http://127.0.0.1:" + port +  "/resource." + provider.getFileType() + "?rid=" + rid ));

		}catch( Throwable e ){

			Debug.out( e );

			return( null );
		}
	}
}
