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

package com.biglybt.net.upnp.impl.ssdp;

import java.net.*;
import java.util.*;

import com.biglybt.core.util.*;
import com.biglybt.net.udp.mc.MCGroup;
import com.biglybt.net.udp.mc.MCGroupAdapter;
import com.biglybt.net.udp.mc.MCGroupFactory;
import com.biglybt.net.upnp.UPnPException;
import com.biglybt.net.upnp.UPnPSSDP;
import com.biglybt.net.upnp.UPnPSSDPAdapter;
import com.biglybt.net.upnp.UPnPSSDPListener;
import com.biglybt.pif.utils.UTTimer;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;

/**
 * @author parg
 *
 */

public class
SSDPCore
	implements UPnPSSDP, MCGroupAdapter
{
	private static final String	HTTP_VERSION	= "1.1";
	private static final String	NL				= "\r\n";


	private static Map			singletons	= new HashMap();
	private static AEMonitor	class_mon 	= new AEMonitor( "SSDPCore:class" );

	public static SSDPCore
	getSingleton(
		UPnPSSDPAdapter adapter,
		String				group_address,
		int					group_port,
		int					control_port,
		String[]			selected_interfaces )

		throws UPnPException
	{
		try{
			class_mon.enter();

			String	key = group_address + ":" + group_port + ":" + control_port;

			SSDPCore	singleton = (SSDPCore)singletons.get( key );

			if ( singleton == null ){

				singleton = new SSDPCore( adapter, group_address, group_port, control_port, selected_interfaces );

				singletons.put( key, singleton );
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	private MCGroup mc_group;

	private UPnPSSDPAdapter		adapter;
	private String				group_address_str;
	private int					group_port;

	private boolean		first_response			= true;

	private List			listeners	= new ArrayList();

	private UTTimer			timer;
	private List			timer_queue = new ArrayList();
	private long			time_event_next;

	protected AEMonitor		this_mon	= new AEMonitor( "SSDP" );

	private Set<String>		ignore_mx	= new HashSet();

	private
	SSDPCore(
		UPnPSSDPAdapter		_adapter,
		String				_group_address,
		int					_group_port,
		int					_control_port,
		String[]			_selected_interfaces )

		throws UPnPException
	{
		adapter	= _adapter;

		group_address_str	= _group_address;
		group_port			= _group_port;

		try{
			mc_group = MCGroupFactory.getSingleton( this, _group_address, group_port, _control_port, _selected_interfaces );

		}catch( Throwable e ){

			throw( new UPnPException( "Failed to initialise SSDP", e ));
		}
	}

	@Override
	public int
	getControlPort()
	{
		return( mc_group.getControlPort());
	}

	@Override
	public void
	trace(
		String	str )
	{
		adapter.log( str );
	}

	@Override
	public void
	log(
		Throwable	e )
	{
		adapter.log( e );
	}

	@Override
	public void
	notify(
		String		NT,
		String		NTS,
		String		UUID,
		String		url )
	{
		/*
		NOTIFY * HTTP/1.1
		HOST: 239.255.255.250:1900
		CACHE-CONTROL: max-age=3600
		LOCATION: http://192.168.0.1:49152/gateway.xml
		NT: urn:schemas-upnp-org:service:WANIPConnection:1
		NTS: ssdp:byebye
		SERVER: Linux/2.4.17_mvl21-malta-mips_fp_le, UPnP/1.0, Intel SDK for UPnP devices /1.2
		USN: uuid:ab5d9077-0710-4373-a4ea-5192c8781666::urn:schemas-upnp-org:service:WANIPConnection:1
		*/

		if ( url.startsWith("/")){

			url = url.substring(1);
		}

		String	str =
			"NOTIFY * HTTP/" + HTTP_VERSION + NL +
			"HOST: " + group_address_str + ":" + group_port + NL +
			"CACHE-CONTROL: max-age=3600" + NL +
			"LOCATION: http://%AZINTERFACE%:" + mc_group.getControlPort() + "/" + url + NL +
			"NT: " + NT + NL +
			"NTS: " + NTS + NL +
			"SERVER: " + getServerName() + NL +
			"USN: " + (UUID==null?"":(UUID + "::")) + NT + NL + NL;

		try{

			mc_group.sendToGroup( str );

		}catch( Throwable e ){
		}
	}

	protected String
	getServerName()
	{
		return( Constants.OSName + "/" + Constants.OSVersion + " UPnP/1.0 " +
				Constants.BIGLYBT_NAME + "/" + Constants.BIGLYBT_VERSION );
	}

	@Override
	public void
	search(
		String[]	STs )
	{
		for ( String ST: STs ){

			String	str =
				"M-SEARCH * HTTP/" + HTTP_VERSION + NL +
				"ST: " + ST + NL +
				"MX: 3" + NL +
				"MAN: \"ssdp:discover\"" + NL +
				"HOST: " + group_address_str + ":" + group_port + NL + NL;

			sendMC( str );
		}
	}

	protected void
	sendMC(
		String	str )
	{
		byte[]	data = str.getBytes();

		try{

			mc_group.sendToGroup( data );

		}catch( Throwable e ){
		}
	}

	@Override
	public void
	interfaceChanged(
		NetworkInterface	network_interface )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				((UPnPSSDPListener)listeners.get(i)).interfaceChanged(network_interface);

			}catch( Throwable e ){

				adapter.log(e);
			}
		}
	}

	@Override
	public void
	received(
		NetworkInterface		network_interface,
		InetAddress				local_address,
		final InetSocketAddress	originator,
		byte[]					packet_data,
		int						length )
	{
		String	str = new String( packet_data, 0,length );

		if ( first_response ){

			first_response	= false;

			adapter.trace( "UPnP:SSDP: first response:\n" + str );
		}

				// example notify event
			/*
			NOTIFY * HTTP/1.1
			HOST: 239.255.255.250:1900
			CACHE-CONTROL: max-age=3600
			LOCATION: http://192.168.0.1:49152/gateway.xml
			NT: urn:schemas-upnp-org:service:WANIPConnection:1
			NTS: ssdp:byebye
			SERVER: Linux/2.4.17_mvl21-malta-mips_fp_le, UPnP/1.0, Intel SDK for UPnP devices /1.2
			USN: uuid:ab5d9077-0710-4373-a4ea-5192c8781666::urn:schemas-upnp-org:service:WANIPConnection:1
			*/

		//if ( originator.getAddress().getHostAddress().equals( "192.168.0.135" )){
		//	System.out.println( originator + ":" + str );
		//}

		List<String>	lines = new ArrayList<>();

		int	pos = 0;

		while(true){

			int	p1 = str.indexOf( NL, pos );

			String	line;

			if ( p1 == -1 ){

				line = str.substring(pos);
			}else{

				line = str.substring(pos,p1);

				pos	= p1+1;
			}

			lines.add( line.trim());

			if ( p1 == -1 ){

				break;
			}
		}

		if ( lines.size() == 0 ){

			adapter.trace( "SSDP::receive packet - 0 line reply" );

			return;
		}

		String	header = (String)lines.get(0);

			// Gudy's  Root: http://192.168.0.1:5678/igd.xml, uuid:upnp-InternetGatewayDevice-1_0-12345678900001::upnp:rootdevice, upnp:rootdevice
			// Parg's  Root: http://192.168.0.1:49152/gateway.xml, uuid:824ff22b-8c7d-41c5-a131-44f534e12555::upnp:rootdevice, upnp:rootdevice

		URL		location	= null;
		String	usn			= null;
		String	nt			= null;
		String	nts			= null;
		String	st			= null;
		String	al			= null;
		String	mx			= null;
		String	server		= null;

		for (int i=1;i<lines.size();i++){

			String	line = (String)lines.get(i);

			int	c_pos = line.indexOf(":");

			if ( c_pos == -1 ){
				continue;
			}

			String	key	= line.substring( 0, c_pos ).trim().toUpperCase();
			String 	val = line.substring( c_pos+1 ).trim();

			if ( key.equals("LOCATION" )){

				try{
						// xbox throws us a '*' on bootup

					if ( !val.equals( "*" )){

						location	= new URL( val );
					}
				}catch( MalformedURLException e ){

					if ( !val.contains( "//" )){

							// seen missing protocol

						val = "http://" + val;

						try{
							location	= new URL( val );

						}catch( Throwable f ){
						}
					}

					if ( location == null ){

						adapter.log( e );
					}
				}
			}else if ( key.equals( "NT" )){

				nt	= val;

			}else if ( key.equals( "USN" )){

				usn	= val;

			}else if ( key.equals( "NTS" )){

				nts	= val;

			}else if ( key.equals( "ST" )){

				st	= val;

			}else if ( key.equals( "AL" )){

				al	= val;

			}else if ( key.equals( "MX" )){

				mx	= val;

			}else if ( key.equals( "SERVER" )){

				server = val;
			}
		}

		//if ( location != null && location.getHost().equals( "192.168.0.135")){

		//	System.out.println( str );
		//}

		if ( server != null ){

				// xbox doesn't play well with us doing MX properly, seems like the delay causes
				// it not to pick up the response, grrrrr!

			if ( server.toLowerCase().startsWith( "xbox" )){

				String host = originator.getAddress().getHostAddress();

				synchronized( ignore_mx ){

					ignore_mx.add( host );
				}
			}
		}

		if ( mx != null ){

			String host = originator.getAddress().getHostAddress();

			synchronized( ignore_mx ){

				if ( ignore_mx.contains( host )){

					mx	= null;
				}
			}
		}

		if ( header.startsWith("M-SEARCH")){

			if ( st != null ){

				/*
				HTTP/1.1 200 OK
				CACHE-CONTROL: max-age=600
				DATE: Tue, 20 Dec 2005 13:07:31 GMT
				EXT:
				LOCATION: http://192.168.1.1:2869/gatedesc.xml
				SERVER: Linux/2.4.17_mvl21-malta-mips_fp_le UPnP/1.0
				ST: upnp:rootdevice
				USN: uuid:UUID-InternetGatewayDevice-1234::upnp:rootdevice
				*/

				String[]	response = informSearch( network_interface, local_address, originator.getAddress(), st );

				if ( response != null ){

					String	UUID 	= response[0];
					String	url		= response[1];

					if ( url.startsWith("/")){
						url = url.substring(1);
					}

						// Server MUST be in this alpha-case for Xbox to work (SERVER doesn't)...

					String	data =
						"HTTP/1.1 200 OK" + NL +
						"USN: " + UUID + "::" + st + NL +
						"ST: " + st + NL +
						"EXT:" + NL +
						"Location: http://" + local_address.getHostAddress() + ":" + mc_group.getControlPort() + "/" + url + NL +
						"Server: " + Constants.APP_NAME + "/" + Constants.BIGLYBT_VERSION + " UPnP/1.0 " +Constants.APP_NAME + "/" + Constants.BIGLYBT_VERSION + NL +
						"Cache-Control: max-age=3600" + NL +
						"Date: " + TimeFormatter.getHTTPDate( SystemTime.getCurrentTime()) + NL +
						"Content-Length: 0" + NL + NL;

					final byte[]	data_bytes = data.getBytes();

					if ( timer == null ){

						timer	= adapter.createTimer( "SSDPCore:MX" );
					}

					int	delay = 0;

					if ( mx != null ){

						try{

							delay = Integer.parseInt( mx ) * 1000;

							delay = RandomUtils.nextInt( delay );

						}catch( Throwable e ){
						}
					}

					final Runnable task =
						new Runnable()
						{
							@Override
							public void
							run()
							{
								try{
									mc_group.sendToMember( originator, data_bytes );

								}catch( Throwable e ){

									adapter.log(e);
								}
							}
						};

					if ( delay == 0 ){

						task.run();

					}else{

						long	target_time = SystemTime.getCurrentTime() + delay;

						boolean	schedule_event;

						synchronized( timer_queue ){

							timer_queue.add( task );

							schedule_event = time_event_next == 0 || target_time < time_event_next;

							if ( schedule_event ){

								time_event_next = target_time;
							}
						}

						if ( schedule_event ){

							timer.addEvent(
									target_time,
									new UTTimerEventPerformer()
									{
										@Override
										public void
										perform(
											UTTimerEvent		event )
										{
												// only actually ever run of these at a time as they
												// have been seen to back up and flood the timer pool

											while( true ){

												Runnable t;

												synchronized( timer_queue ){

													if ( timer_queue.size() > 0 ){

														t = (Runnable)timer_queue.remove(0);

													}else{

														time_event_next = 0;

														return;
													}
												}

												try{
													t.run();

												}catch( Throwable e ){

													Debug.printStackTrace(e);
												}
											}
										}
									});
						}
					}
				}
			}else{

				adapter.trace( "SSDP::receive M-SEARCH - bad header:" + header );
			}
		}else if ( header.startsWith( "NOTIFY" )){

				// location is null for byebye

			if ( nt != null && nts != null ){

				informNotify( network_interface, local_address, originator.getAddress(), usn, location, nt, nts );

			}else{

				adapter.trace( "SSDP::receive NOTIFY - bad header:" + header );
			}
		}else if ( header.startsWith( "HTTP") && header.contains("200")){

			if ( location != null && st != null ){

				informResult( network_interface, local_address, originator.getAddress(), usn, location, st, al  );

			}else{

				adapter.trace( "SSDP::receive HTTP - bad header:" + header );
			}
		}else{

			adapter.trace( "SSDP::receive packet - bad header:" + header );
		}
	}


	protected void
	informResult(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		String				usn,
		URL					location,
		String				st,
		String				al )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				((UPnPSSDPListener)listeners.get(i)).receivedResult(network_interface,local_address,originator,usn,location,st,al);

			}catch( Throwable e ){

				adapter.log(e);
			}
		}
	}

	protected void
	informNotify(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		String				usn,
		URL					location,
		String				nt,
		String				nts )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				((UPnPSSDPListener)listeners.get(i)).receivedNotify(network_interface,local_address,originator,usn,location,nt,nts);

			}catch( Throwable e ){

				adapter.log(e);
			}
		}
	}

	protected String[]
	informSearch(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		String				st )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				String[]	res = ((UPnPSSDPListener)listeners.get(i)).receivedSearch(network_interface,local_address,originator,st );

				if ( res != null ){

					return( res );
				}
			}catch( Throwable e ){

				adapter.log(e);
			}
		}

		return( null );
	}

	@Override
	public void
	addListener(
		UPnPSSDPListener	l )
	{
		listeners.add( l );
	}

	@Override
	public void
	removeListener(
			UPnPSSDPListener	l )
	{
		listeners.remove(l);
	}
}
