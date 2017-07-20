/*
 * Created on 14-Jun-2004
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

package com.biglybt.net.upnp.impl;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.*;
import java.util.*;

import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.core.util.*;
import com.biglybt.net.upnp.*;
import com.biglybt.net.upnp.impl.device.UPnPDeviceImpl;
import com.biglybt.net.upnp.impl.device.UPnPRootDeviceImpl;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocument;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentException;

public class
UPnPImpl
	extends 	ResourceDownloaderAdapter
	implements 	UPnP, SSDPIGDListener
{
	public static final String	NL	= "\r\n";

	private static UPnPImpl	singleton;
	private static AEMonitor	class_mon 	= new AEMonitor( "UPnP:class" );

	public static UPnP
	getSingleton(
		UPnPAdapter adapter,
		String[]		selected_interfaces )

		throws UPnPException
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton = new UPnPImpl( adapter, selected_interfaces );
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	private UPnPAdapter				adapter;
	private SSDPIGD					ssdp;

	private Map<String,UPnPRootDeviceImpl>			root_locations	= new HashMap<>();

	private List		log_listeners		= new ArrayList();
	private List		log_history			= new ArrayList();
	private List		log_alert_history	= new ArrayList();

	private List<UPnPListener>	rd_listeners		= new ArrayList<>();
	private AEMonitor			rd_listeners_mon 	= new AEMonitor( "UPnP:L" );

	private int		http_calls_ok	= 0;
	private int		direct_calls_ok	= 0;

	private int		trace_index		= 0;

	private AsyncDispatcher		async_dispatcher = new AsyncDispatcher();

	private ThreadPool	device_dispatcher	 = new ThreadPool("UPnPDispatcher", 1, true );
	private Set			device_dispatcher_pending	= new HashSet();

	private Map<String,long[]>	failed_urls = new HashMap<>();

	protected AEMonitor	this_mon 	= new AEMonitor( "UPnP" );

	protected
	UPnPImpl(
		UPnPAdapter		_adapter,
		String[]		_selected_interfaces )

		throws UPnPException
	{
		adapter	= _adapter;

		ssdp = SSDPIGDFactory.create( this, _selected_interfaces );

		ssdp.addListener(this);

		ssdp.start();
	}

	@Override
	public UPnPSSDP
	getSSDP()
	{
		return( ssdp.getSSDP());
	}

	@Override
	public void
	injectDiscoveryCache(
		Map 		cache )
	{
		try{
			String	ni_s	= new String((byte[])cache.get( "ni" ), "UTF-8" );
			String	la_s 	= new String((byte[])cache.get( "la" ), "UTF-8" );
			String	usn 	= new String((byte[])cache.get( "usn" ), "UTF-8" );
			String	loc_s 	= new String((byte[])cache.get( "loc" ), "UTF-8" );

			NetworkInterface	network_interface = NetUtils.getByName( ni_s );

			if ( network_interface == null ){

				return;
			}

			InetAddress	local_address = InetAddress.getByName( la_s );

			URL location = new URL( loc_s );

			rootDiscovered( network_interface, local_address, usn, location );

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	@Override
	public void
	rootDiscovered(
		final NetworkInterface		network_interface,
		final InetAddress			local_address,
		final String				usn,
		final URL					location )
	{

			// we need to take this operation off the main thread as it can take some time. This is a single
			// concurrency queued thread pool so things get done serially in the right order

		try{
			rd_listeners_mon.enter();

			if ( device_dispatcher_pending.contains( usn )){

				// System.out.println( "UPnP: skipping discovery of " + usn + " as already pending (queue=" + device_dispatcher_pending.size() + ")" );

				return;
			}

			if ( device_dispatcher_pending.size() > 512 ){

				Debug.out( "Device dispatcher queue is full - dropping discovery of " + usn + "/" + location );
			}

			device_dispatcher_pending.add( usn );

		}finally{

			rd_listeners_mon.exit();
		}

		device_dispatcher.run(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					final UPnPRootDeviceImpl old_root_device;

					try{
						rd_listeners_mon.enter();

						old_root_device = (UPnPRootDeviceImpl)root_locations.get( usn );

						device_dispatcher_pending.remove( usn );

					}finally{

						rd_listeners_mon.exit();
					}

					if ( old_root_device != null ){

							// we remember one route to the device - if the network interfaces change
							// we do a full reset so we don't need to deal with that here

						if ( !old_root_device.getNetworkInterface().getName().equals( network_interface.getName())){

							if ( old_root_device.addAlternativeLocation( location )){

								log( "Adding alternative location " +location + " to " + usn );
							}

							return;
						}

							// check that the device's location is the same

						if ( old_root_device.getLocation().equals( location )){

							return;
						}
					}

					if ( old_root_device != null ){

							// something changed, resetablish everything

						try{
								// not the best "atomic" code here but it'll do as the code that adds roots (this)
								// is single threaded via the dispatcher

							rd_listeners_mon.enter();

							root_locations.remove( usn );

						}finally{

							rd_listeners_mon.exit();
						}

						old_root_device.destroy( true );
					}

					List	listeners;

					try{
						rd_listeners_mon.enter();

						listeners = new ArrayList( rd_listeners );

					}finally{

						rd_listeners_mon.exit();
					}

					for (int i=0;i<listeners.size();i++){

						try{
							if ( !((UPnPListener)listeners.get(i)).deviceDiscovered( usn, location )){

								return;
							}

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}

					log( "UPnP: root discovered: usn=" + usn + ", location=" + location + ", ni=" + network_interface.getName() + ",local=" + local_address.toString() );

					try{
						UPnPRootDeviceImpl new_root_device = new UPnPRootDeviceImpl( UPnPImpl.this, network_interface, local_address, usn, location );

						try{
							rd_listeners_mon.enter();

							root_locations.put( usn, new_root_device );

							listeners = new ArrayList( rd_listeners );

						}finally{

							rd_listeners_mon.exit();
						}

						for (int i=0;i<listeners.size();i++){

							try{
								((UPnPListener)listeners.get(i)).rootDeviceFound( new_root_device );

							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}

					}catch( UPnPException e ){

						String	message = e.getMessage();

						String msg = message==null?Debug.getNestedExceptionMessageAndStack( e ):message;

						adapter.log( msg );
					}
				}
			});
	}

	@Override
	public void
	rootAlive(
		String		usn,
		URL			location )
	{
		UPnPRootDeviceImpl root_device = (UPnPRootDeviceImpl)root_locations.get( usn );

		if ( root_device == null ){

			ssdp.searchNow();
		}
	}

	@Override
	public void
	rootLost(
		final InetAddress	local_address,
		final String		usn )
	{
			// we need to take this operation off the main thread as it can take some time

		device_dispatcher.run(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					UPnPRootDeviceImpl	root_device	= null;

					try{
						rd_listeners_mon.enter();

						root_device = (UPnPRootDeviceImpl)root_locations.remove( usn );

					}finally{

						rd_listeners_mon.exit();
					}

					if ( root_device == null ){

						return;
					}

					log( "UPnP: root lost: usn=" + usn + ", location=" + root_device.getLocation() + ", ni=" + root_device.getNetworkInterface().getName() + ",local=" + root_device.getLocalAddress().toString());

					root_device.destroy( false );
				}
			});
	}

	@Override
	public void
	interfaceChanged(
		NetworkInterface	network_interface )
	{
		reset();
	}

	@Override
	public void
	search()
	{
		ssdp.searchNow();
	}

	@Override
	public void
	search(
		String[]	STs )
	{
		ssdp.searchNow( STs );
	}

	@Override
	public void
	reset()
	{
		log( "UPnP: reset" );

		List	roots;

		try{
			rd_listeners_mon.enter();

			roots = new ArrayList(root_locations.values());

			root_locations.clear();

		}finally{

			rd_listeners_mon.exit();
		}

		for (int i=0;i<roots.size();i++){

			((UPnPRootDeviceImpl)roots.get(i)).destroy( true );
		}

		ssdp.searchNow();
	}

	public SimpleXMLParserDocument
	parseXML(
		InputStream		_is )

		throws SimpleXMLParserDocumentException, IOException
	{
			// ASSUME UTF-8

		ByteArrayOutputStream		baos = null;

		try{
			baos = new ByteArrayOutputStream(1024);

			byte[]	buffer = new byte[8192];

			while(true){

				int	len = _is.read( buffer );

				if ( len <= 0 ){

					break;
				}

				baos.write( buffer, 0, len );
			}
		}finally{

			baos.close();
		}

		byte[]	bytes_in = baos.toByteArray();

		InputStream	is = new ByteArrayInputStream( bytes_in );

			// Gudy's router was returning trailing nulls which then stuffed up the
			// XML parser. Hence this code to try and strip them

		try{
			StringBuilder data = new StringBuilder(1024);

			LineNumberReader	lnr = new LineNumberReader( new InputStreamReader( is, "UTF-8" ));

			Set	ignore_map = null;

			while( true ){

				String	line = lnr.readLine();

				if ( line == null ){

					break;
				}

					// remove any obviously invalid characters - I've seen some routers generate stuff like
					// 0x18 which stuffs the xml parser with "invalid unicode character"

				for (int i=0;i<line.length();i++){

					char	c = line.charAt(i);

					if ( c < 0x20 && c != '\r' && c != '\t' ){

						data.append( ' ' );

						if ( ignore_map == null ){

							ignore_map = new HashSet();
						}

						Character	cha = new Character(c);

						if ( !ignore_map.contains( cha )){

							ignore_map.add( cha );

							adapter.trace( "    ignoring character(s) " + (int)c + " in xml response" );
						}
					}else{

						data.append( c );
					}
				}

				data.append( "\n" );
			}

			String	data_str = data.toString();

			adapter.trace( "UPnP:Response:" + data_str );

			try{
				SimpleXMLParserDocument doc = adapter.parseXML( data_str );

				return( doc );

			}catch( Throwable e ){

					// try some hacks for known errors

				if ( data_str.contains("<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">")){

					data_str = data_str.replace("<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">", "<scpd>");

					return( adapter.parseXML( data_str ));
				}

				throw( e );
			}
		}catch( Throwable e ){

			try{
				FileOutputStream	trace = new FileOutputStream( getTraceFile());

				try{
					trace.write( bytes_in );

				}finally{

					trace.close();
				}
			}catch( Throwable f ){

				adapter.log(f);
			}

			if ( e instanceof SimpleXMLParserDocumentException ){

				throw((SimpleXMLParserDocumentException)e);
			}

			throw( new SimpleXMLParserDocumentException(e ));
		}
	}

	public SimpleXMLParserDocument
	downloadXML(
		UPnPRootDeviceImpl	root,
		URL					url )

		throws UPnPException
	{
		return( downloadXMLSupport( null, url ));
	}

	public SimpleXMLParserDocument
	downloadXML(
		UPnPDeviceImpl device,
		URL				url )

		throws UPnPException
	{
		try{
				// some devices have borked relative urls, work around

			device.restoreRelativeBaseURL();

			return( downloadXMLSupport( device.getFriendlyName(), url ));

		}catch( UPnPException e ){

			device.clearRelativeBaseURL();

			return( downloadXMLSupport( device.getFriendlyName(), url ));
		}
	}

	protected SimpleXMLParserDocument
	downloadXMLSupport(
		String			friendly_name,
		URL				url )

		throws UPnPException
	{
		String url_str = url.toExternalForm();

		boolean	record_failure = true;

		try{
			TorrentUtils.setTLSDescription( "UPnP Device" + ( friendly_name==null?"":( ": " + friendly_name )));

			ResourceDownloaderFactory rdf = adapter.getResourceDownloaderFactory();

			int	retries;

			synchronized( failed_urls ){

				long[] fails = failed_urls.get( url_str );

				if ( fails == null ){

					retries = 3;

				}else{

					long	consec_fails 	= fails[0];
					long	last_fail		= fails[1];

					long	max_period	= 10*60*1000;
					long	period 		= 60*1000;

					for (int i=0;i<consec_fails;i++){

						period <<= 1;

						if ( period >= max_period ){

							period = max_period;

							break;
						}
					}

					if ( SystemTime.getMonotonousTime() - last_fail < period ){

						record_failure = false;

						throw( new UPnPException( "Download failed too recently, ignoring" ));
					}

					retries = 1;
				}
			}

			ResourceDownloader rd = rdf.getRetryDownloader( rdf.create( url, true ), retries );

			rd.addListener( this );

			InputStream	data = rd.download();

			try{

				SimpleXMLParserDocument res = parseXML( data );

				synchronized( failed_urls ){

					failed_urls.remove( url_str );
				}

				return( res );

			}finally{

				data.close();
			}
		}catch( Throwable e ){

			if ( record_failure ){

				synchronized( failed_urls ){

					if ( failed_urls.size() >= 64 ){

						failed_urls.clear();
					}

					long[] fails = failed_urls.get( url_str );

					if ( fails == null ){

						fails = new long[2];

						failed_urls.put( url_str, fails );
					}

					fails[0]++;

					fails[1] = SystemTime.getMonotonousTime();
				}

				adapter.log( "Failed to parse XML from :" + url_str + ": " + Debug.getNestedExceptionMessageAndStack(e));
			}

			if (e instanceof UPnPException ){

				throw((UPnPException)e);
			}

			throw( new UPnPException( "Root device location '" + url + "' - data read failed", e ));

		}finally{

			TorrentUtils.setTLSDescription( null );
		}
	}

	protected boolean
	forceDirect()
	{
		String	http_proxy 	= System.getProperty( "http.proxyHost" );
		String	socks_proxy = System.getProperty( "socksProxyHost" );

			// extremely unlikely we want to proxy upnp requests

		boolean force_direct = 	( http_proxy != null && http_proxy.trim().length() > 0 ) ||
								( socks_proxy != null && socks_proxy.trim().length() > 0 );

		return( force_direct );
	}



	public SimpleXMLParserDocument
	performSOAPRequest(
		UPnPService service,
		String			soap_action,
		String			request )

		throws SimpleXMLParserDocumentException, UPnPException, IOException
	{
		SimpleXMLParserDocument	res;

		if ( service.getDirectInvocations() || forceDirect()){

			res = performSOAPRequest( service, soap_action, request, false );

		}else{

			try{
				res =  performSOAPRequest( service, soap_action, request, true );

				http_calls_ok++;

			}catch( IOException e ){

				res = performSOAPRequest( service, soap_action, request, false );

				direct_calls_ok++;

				if ( direct_calls_ok == 1 ){

					log( "Invocation via http connection failed (" + e.getMessage() + ") but socket connection succeeded" );
				}
			}
		}

		return( res );
	}

	/**
	 * The use_http_connection flag is set to false sometimes to avoid using
	 * the URLConnection library for some dopey UPnP routers.
	 */
	public SimpleXMLParserDocument
	performSOAPRequest(
		UPnPService		service,
		String			soap_action,
		String			request,
		boolean			use_http_connection)

		throws SimpleXMLParserDocumentException, UPnPException, IOException
	{
		//long	start = SystemTime.getMonotonousTime();

		List<URL>	controls = service.getControlURLs();

		Throwable last_error = null;

		for ( URL control: controls ){

			boolean	good_url = true;

			try{
				adapter.trace( "UPnP:Request: -> " + control + "," + request );

				if ( use_http_connection ){

					try{
						AEProxySelectorFactory.getSelector().startNoProxy();

						TorrentUtils.setTLSDescription( "UPnP Device: " + service.getDevice().getFriendlyName());

						HttpURLConnection	con1 = (HttpURLConnection)control.openConnection();

						con1.setRequestProperty( "SOAPAction", "\""+ soap_action + "\"");

						con1.setRequestProperty( "Content-Type", "text/xml; charset=\"utf-8\"" );

						con1.setRequestProperty( "User-Agent", Constants.APP_NAME + " (UPnP/1.0)" );

						con1.setRequestMethod( "POST" );

						con1.setDoInput( true );
						con1.setDoOutput( true );

						OutputStream	os = con1.getOutputStream();

						PrintWriter	pw = new PrintWriter( new OutputStreamWriter(os, "UTF-8" ));

						pw.println( request );

						pw.flush();

						con1.connect();

						if ( con1.getResponseCode() == 405 || con1.getResponseCode() == 500 ){

								// gotta retry with M-POST method

							try{
								HttpURLConnection con2 = (HttpURLConnection)control.openConnection();

								con2.setRequestProperty( "Content-Type", "text/xml; charset=\"utf-8\"" );

								con2.setRequestMethod( "M-POST" );

								con2.setRequestProperty( "MAN", "\"http://schemas.xmlsoap.org/soap/envelope/\"; ns=01" );

								con2.setRequestProperty( "01-SOAPACTION", "\""+ soap_action + "\"");

								con2.setDoInput( true );
								con2.setDoOutput( true );

								os = con2.getOutputStream();

								pw = new PrintWriter( new OutputStreamWriter(os, "UTF-8" ));

								pw.println( request );

								pw.flush();

								con2.connect();

								return( parseXML(con2.getInputStream()));

							}catch( Throwable e ){

							}

							InputStream es = con1.getErrorStream();

							String	info = null;

							try{
								info = FileUtil.readInputStreamAsString( es, 512 );

							}catch( Throwable e ){
							}

							String error = "SOAP RPC failed: " + con1.getResponseCode() + " " + con1.getResponseMessage();

							if ( info != null ){

								error += " - " + info;
							}

							throw( new IOException ( error ));

						}else{

							return( parseXML(con1.getInputStream()));
						}
					}finally{

						TorrentUtils.setTLSDescription( null );

						AEProxySelectorFactory.getSelector().endNoProxy();
					}
				}else{
					final int CONNECT_TIMEOUT 	= 15*1000;
					final int READ_TIMEOUT		= 30*1000;

					Socket	socket = new Socket( Proxy.NO_PROXY );

					socket.connect( new InetSocketAddress( control.getHost(), control.getPort()), CONNECT_TIMEOUT );

					socket.setSoTimeout( READ_TIMEOUT );

					try{
						PrintWriter	pw = new PrintWriter(new OutputStreamWriter( socket.getOutputStream(), "UTF8" ));

						String	url_target = control.toString();

						int	p1 	= url_target.indexOf( "://" ) + 3;
						p1		= url_target.indexOf( "/", p1 );

						url_target = url_target.substring( p1 );

						pw.print( "POST " + url_target + " HTTP/1.1" + NL );
						pw.print( "Content-Type: text/xml; charset=\"utf-8\"" + NL );
						pw.print( "SOAPAction: \"" + soap_action + "\"" + NL );
						pw.print( "User-Agent: " + Constants.APP_NAME + " (UPnP/1.0)" + NL );
						pw.print( "Host: " + control.getHost() + NL );
						pw.print( "Content-Length: " + request.getBytes( "UTF8" ).length + NL );
						pw.print( "Connection: Keep-Alive" + NL );
						pw.print( "Pragma: no-cache" + NL + NL );

						pw.print( request );

						pw.flush();

						InputStream	is = HTTPUtils.decodeChunkedEncoding( socket, true );

						return( parseXML( is ));

					}finally{

						try{
							socket.close();

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}
			}catch( Throwable e ){

				last_error = e;

				good_url = false;

			}finally{

				if ( good_url ){

					service.setPreferredControlURL( control );
				}
				//System.out.println( "UPnP: invocation of " + control + "/" + soap_action + " took " + ( SystemTime.getMonotonousTime() - start ));
			}
		}

		if ( last_error == null ){

			throw( new UPnPException( "inconsistent!" ));
		}

		if ( last_error instanceof SimpleXMLParserDocumentException ){

			throw((SimpleXMLParserDocumentException)last_error);

		}else if ( last_error instanceof UPnPException ){

			throw((UPnPException)last_error);

		}else if ( last_error instanceof IOException ){

			throw((IOException)last_error);

		}else{

			throw((RuntimeException)last_error );
		}
	}

	protected File
	getTraceFile()
	{
		try{
			this_mon.enter();

			trace_index++;

			if ( trace_index == 6 ){

				trace_index = 1;
			}

			return( new File( adapter.getTraceDir(), "upnp_trace" + trace_index + ".log" ));
		}finally{

			this_mon.exit();
		}
	}

	public UPnPAdapter
	getAdapter()
	{
		return( adapter );
	}

	@Override
	public void
	reportActivity(
		ResourceDownloader	downloader,
		String				activity )
	{
		log( activity );
	}

	@Override
	public void
	failed(
		ResourceDownloader			downloader,
		ResourceDownloaderException e )
	{
		log( e );
	}

	public void
	log(
		Throwable e )
	{
		log( e.toString());
	}

	@Override
	public void
	log(
		String	str )
	{
		List	old_listeners;

		try{
			this_mon.enter();

			old_listeners = new ArrayList(log_listeners);

			log_history.add( str );

			if ( log_history.size() > 32 ){

				log_history.remove(0);
			}
		}finally{

			this_mon.exit();
		}

		for (int i=0;i<old_listeners.size();i++){

			((UPnPLogListener)old_listeners.get(i)).log( str );
		}
	}

	public void
	logAlert(
		String	str,
		boolean	error,
		int		type )
	{
		List	old_listeners;

		try{
			this_mon.enter();

			old_listeners = new ArrayList(log_listeners);

			log_alert_history.add(new Object[]{ str, Boolean.valueOf(error), new Integer( type )});

			if ( log_alert_history.size() > 32 ){

				log_alert_history.remove(0);
			}
		}finally{

			this_mon.exit();
		}

		for (int i=0;i<old_listeners.size();i++){

			((UPnPLogListener)old_listeners.get(i)).logAlert( str, error, type );
		}
	}

	@Override
	public void
	addLogListener(
		UPnPLogListener	l )
	{
		List	old_logs;
		List	old_alerts;

		try{
			this_mon.enter();

			old_logs 	= new ArrayList(log_history);
			old_alerts 	= new ArrayList(log_alert_history);

			log_listeners.add( l );
		}finally{

			this_mon.exit();
		}

		for (int i=0;i<old_logs.size();i++){

			l.log((String)old_logs.get(i));
		}

		for (int i=0;i<old_alerts.size();i++){

			Object[]	entry = (Object[])old_alerts.get(i);

			l.logAlert((String)entry[0], ((Boolean)entry[1]).booleanValue(), ((Integer)entry[2]).intValue());
		}
	}

	@Override
	public void
	removeLogListener(
		UPnPLogListener	l )
	{
		log_listeners.remove( l );
	}

	@Override
	public UPnPRootDevice[]
	getRootDevices()
	{
		try{
			this_mon.enter();

			return( root_locations.values().toArray( new UPnPRootDevice[ root_locations.size()] ));

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	addRootDeviceListener(
		final UPnPListener	l )
	{
		final List<UPnPRootDeviceImpl>	old_locations;

		try{
			this_mon.enter();

			old_locations = new ArrayList<>(root_locations.values());

			rd_listeners.add( l );

		}finally{

			this_mon.exit();
		}

		if ( old_locations.size() > 0 ){

				// if we have a misbehaving device (hanging on requests for example) then this can cause
				// logic running on the new listener to hang the calling thread (e.g. the UPnPMediaServer)
				// which can then bork its caller (e.g. PlayUtils.getMediaServerContentURL) and subsequent
				// badness (UI hang). As a general fix for this go async here

			async_dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						for (int i=0;i<old_locations.size();i++){

							UPnPRootDevice	device = (UPnPRootDevice)old_locations.get(i);

							try{

								if ( l.deviceDiscovered( device.getUSN(), device.getLocation())){

									l.rootDeviceFound(device);
								}

							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}
					}
				});
		}
	}

	@Override
	public void
	removeRootDeviceListener(
		UPnPListener	l )
	{
		try{
			this_mon.enter();

			rd_listeners.remove( l );

		}finally{

			this_mon.exit();
		}
	}

	/*
	public static void
	main(
		String[]		args )
	{
		try{
			UPnP	upnp = UPnPFactory.getSingleton(null,null);	// won't work with null ....

			upnp.addRootDeviceListener(
					new UPnPListener()
					{
						public boolean
						deviceDiscovered(
							String		USN,
							URL			location )
						{
							return( true );
						}

						public void
						rootDeviceFound(
							UPnPRootDevice		device )
						{
							try{
								processDevice( device.getDevice() );

							}catch( Throwable e ){

								e.printStackTrace();
							}
						}
					});

			upnp.addLogListener(
				new UPnPLogListener()
				{
					public void
					log(
						String	str )
					{
						System.out.println( str );
					}
					public void
					logAlert(
						String	str,
						boolean	error,
						int		type )
					{
						System.out.println( str );
					}

				});

			Thread.sleep(20000);

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	protected static void
	processDevice(
		UPnPDevice	device )

		throws UPnPException
	{
		if ( device.getDeviceType().equalsIgnoreCase("urn:schemas-upnp-org:device:WANConnectionDevice:1")){

			System.out.println( "got device");

			UPnPService[] services = device.getServices();

			for (int i=0;i<services.length;i++){

				UPnPService	s = services[i];

				if ( s.getServiceType().equalsIgnoreCase( "urn:schemas-upnp-org:service:WANIPConnection:1")){

					System.out.println( "got service" );

					UPnPAction[]	actions = s.getActions();

					for (int j=0;j<actions.length;j++){

						System.out.println( actions[j].getName());
					}

					UPnPStateVariable[]	vars = s.getStateVariables();

					for (int j=0;j<vars.length;j++){

						System.out.println( vars[j].getName());
					}

					UPnPStateVariable noe = s.getStateVariable("PortMappingNumberOfEntries");

					System.out.println( "noe = " + noe.getValue());

					UPnPWANIPConnection wan_ip = (UPnPWANIPConnection)s.getSpecificService();

					UPnPWANConnectionPortMapping[] ports = wan_ip.getPortMappings();

					wan_ip.addPortMapping( true, 7007, "Moo!" );

					UPnPAction act	= s.getAction( "GetGenericPortMappingEntry" );

					UPnPActionInvocation inv = act.getInvocation();

					inv.addArgument( "NewPortMappingIndex", "0" );

					UPnPActionArgument[] outs = inv.invoke();

					for (int j=0;j<outs.length;j++){

						System.out.println( outs[j].getName() + " = " + outs[j].getValue());
					}
				}
			}
		}else{

			UPnPDevice[]	kids = device.getSubDevices();

			for (int i=0;i<kids.length;i++){

				processDevice( kids[i] );
			}
		}
	}
	*/
}
