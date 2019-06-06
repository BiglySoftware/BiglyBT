/*
 * File    : TRTrackerClientUtils.java
 * Created : 29-Feb-2004
 * By      : parg
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

package com.biglybt.core.tracker.util;

/**
 * @author parg
 *
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.biglybt.core.tracker.client.impl.TRTrackerAnnouncerImpl;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.core.util.*;


public class
TRTrackerUtils
{
	// author of MakeTorrent has requested we blacklist his site
	// as people keep embedding it as a tracker in torrents

	private static final String[]	BLACKLISTED_HOSTS	=
		{ "krypt.dyndns.org" };

	private static final int[]		BLACKLISTED_PORTS	=
		{ 81 };

	private static String			tracker_ip;
	private static Set<String>		tracker_ip_aliases;

	private static Map			override_map;

	private static String		bind_ip;

	private static int			ports_tcp_port;
	private static String		ports_for_url;
	private static String		ports_for_url_with_crypto;

	static final CopyOnWriteList		listeners = new CopyOnWriteList();

	private static AEThread2		listener_thread;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]
			{	"Proxy.Data.Enable",
				"Proxy.Data.SOCKS.inform",
				"TCP.Listen.Port.Override",
				"Tracker Client No Port Announce",
				"network.transport.encrypted.use.crypto.port",
				"network.transport.encrypted.require",
				"network.transport.encrypted.fallback.incoming",
				"TCP.Listen.Port",
				"UDP.Listen.Port",
				"HTTP.Data.Listen.Port",
				"HTTP.Data.Listen.Port.Override",
				"HTTP.Data.Listen.Port.Enable",
				"Tracker Client Min Announce Interval"
				},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName )
				{
		 			int tcp_port_num	= COConfigurationManager.getIntParameter( "TCP.Listen.Port" );
		 			
					String	port 				= computePortsForURL( tcp_port_num, false, true, false );
			  		String 	port_with_crypto 	= computePortsForURL( tcp_port_num, true, false, false );

			  		ports_tcp_port = tcp_port_num;
			  		
			  		if ( ports_for_url != null && ( !ports_for_url.equals( port ))){

			  			synchronized( listeners ){

			  					// back off for a bit to prevent multiple config changes from causing
			  					// multiple firings

			  				if ( listener_thread == null ){

			  					listener_thread =
			  						new AEThread2( "TRTrackerUtils:listener", true )
			  						{
			  							@Override
										  public void
			  							run()
			  							{
			  								try{
			  									Thread.sleep(30000);

			  								}catch( Throwable e ){
			  								}

			  								synchronized( listeners ){

			  									listener_thread = null;
			  								}

			  								for (Iterator it=listeners.iterator();it.hasNext();){

			  									try{
			  										((TRTrackerUtilsListener)it.next()).announceDetailsChanged();

			  									}catch( Throwable e ){

			  										Debug.printStackTrace( e );
			  									}
			  								}
			  							}
			  						};

			  					listener_thread.start();
			  				}
			  			}
			  		}

			  		ports_for_url 				= port;
			  		ports_for_url_with_crypto	= port_with_crypto;
				}
			});
	}

	private static String
	computePortsForURL(
		int			for_tcp_port,
		boolean		force_crypto,
		boolean		allow_incoming,
		boolean		disable_cryptoport )
	{
		boolean socks_peer_inform	=
			COConfigurationManager.getBooleanParameter("Proxy.Data.Enable")&&
			COConfigurationManager.getBooleanParameter("Proxy.Data.SOCKS.inform");

				// we currently don't support incoming connections when SOCKs proxying

		allow_incoming &= !COConfigurationManager.getBooleanParameter("Tracker Client No Port Announce");

 		int	tcp_port_num;
 		int	udp_port_num;

 		if ( allow_incoming ){

	  		if ( socks_peer_inform ){

	  			tcp_port_num	= 0;
	  			udp_port_num	= 0;
	  		}else{

	 			tcp_port_num	= for_tcp_port;
	 			udp_port_num	= COConfigurationManager.getIntParameter( "UDP.Listen.Port" );
	  		}

	  		String portOverride = COConfigurationManager.getStringParameter("TCP.Listen.Port.Override");

	  		if(! portOverride.equals("")) {

	  			try{
	  				tcp_port_num = Integer.parseInt( portOverride );

	  			}catch( Throwable e ){

	  				Debug.printStackTrace(e);
	  			}
	  		}
 		}else{

  			tcp_port_num	= 0;
  			udp_port_num	= 0;
 		}

 		String port = "";

 		if ( force_crypto ){

  			port += "&requirecrypto=1";

  			if ( disable_cryptoport ){
  				
  				port += "&port=" + tcp_port_num;
  				
  			}else{
  				
  				port += "&port=0&cryptoport=" + tcp_port_num;
  			}

 		}else{

	 		boolean require_crypto = COConfigurationManager.getBooleanParameter( "network.transport.encrypted.require");

	   		if ( require_crypto ){

	  			port += "&requirecrypto=1";

	  		}else{

				port += "&supportcrypto=1";
	  		}

	 		if ( 	require_crypto &&
	 				(!COConfigurationManager.getBooleanParameter( "network.transport.encrypted.fallback.incoming") ) &&
	 				COConfigurationManager.getBooleanParameter( "network.transport.encrypted.use.crypto.port" )){

	 			if ( disable_cryptoport ){
	 				
	 				port += "&port=" + tcp_port_num;
	 				
	 			}else{
	 			
	 				port += "&port=0&cryptoport=" + tcp_port_num;
	 			}
	 		}else{

	 			port += "&port=" + tcp_port_num;
	 		}

			port += "&azudp=" + udp_port_num;

	  		  	//  BitComet extension for no incoming connections

	  		if ( tcp_port_num == 0 ){

	  			port += "&hide=1";
	  		}

	  		if ( COConfigurationManager.getBooleanParameter( "HTTP.Data.Listen.Port.Enable" )){

	  			int	http_port = COConfigurationManager.getIntParameter( "HTTP.Data.Listen.Port.Override" );

	  			if ( http_port == 0 ){

	  				http_port = COConfigurationManager.getIntParameter( "HTTP.Data.Listen.Port" );
	  			}

	  			port += "&azhttp=" + http_port;
	  		}
 		}

 		return( port );
	}

	public static String
	getPublicIPOverride()
	{
	    String explicit_ips = COConfigurationManager.getStringParameter( "Override Ip", "" );

	   	if ( explicit_ips.length() > 0 ){

			StringTokenizer	tok = new StringTokenizer( explicit_ips, ";" );

			while( tok.hasMoreTokens()){

				String	this_address = tok.nextToken().trim();

				if ( this_address.length() > 0 ){

					String	cat = AENetworkClassifier.categoriseAddress( this_address );

					if ( cat == AENetworkClassifier.AT_PUBLIC ){

						return( this_address );
					}
				}
			}
	   	}

	   	return( null );
	}

	private static final Map	az_trackers = COConfigurationManager.getMapParameter( "Tracker Client AZ Instances", new HashMap());

	private static final Map	udp_probe_results = COConfigurationManager.getMapParameter( "Tracker Client UDP Probe Results", new HashMap());


	static{

		COConfigurationManager.addListener(
			new COConfigurationListener()
			{
				@Override
				public void
				configurationSaved()
				{
					readConfig();
				}
			});

		NetworkAdmin.getSingleton().addPropertyChangeListener(
		   	new NetworkAdminPropertyChangeListener()
		   	{
		   		@Override
			    public void
		   		propertyChanged(
		   			String		property )
		   		{
		   			if ( property == NetworkAdmin.PR_DEFAULT_BIND_ADDRESS ){

		   				readConfig();
		   			}
		   		}
		   	});

		readConfig();


	}

	static void
	readConfig()
	{
		tracker_ip 	= COConfigurationManager.getStringParameter("Tracker IP", "");

		tracker_ip 	= UrlUtils.expandIPV6Host( tracker_ip );

		String	aliases = COConfigurationManager.getStringParameter("Tracker IP Aliases", "");

		if ( aliases.length() > 0 ){

			tracker_ip_aliases = new HashSet<>();

			String[] bits = aliases.split(",");

			for (String b: bits ){

				b = b.trim();

				if ( b.length() > 0 ){

					tracker_ip_aliases.add( b );
				}
			}
		}else{

			tracker_ip_aliases = null;
		}

		String override_ips		= COConfigurationManager.getStringParameter("Override Ip", "");

		StringTokenizer	tok = new StringTokenizer( override_ips, ";" );

		Map	new_override_map = new HashMap();

		while( tok.hasMoreTokens()){

			String	ip = tok.nextToken().trim();

			if ( ip.length() > 0 ){

				new_override_map.put( AENetworkClassifier.categoriseAddress( ip ), ip );
			}
		}

		override_map	= new_override_map;

		InetAddress bad = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();

		if ( bad == null || bad.isAnyLocalAddress()){

			bind_ip = "";

		}else{

			bind_ip = bad.getHostAddress();
		}
	}

	public static boolean
	isHosting(
		URL		url_in )
	{
		if ( tracker_ip.length() > 0  ){

			String host = UrlUtils.expandIPV6Host(url_in.getHost());

			boolean	result = host.equalsIgnoreCase( tracker_ip );

			if ( !result && tracker_ip_aliases != null ){

				result = tracker_ip_aliases.contains( host );
			}

			return( result );

		}else{

			return( false );
		}
	}

	public static String
	getTrackerIP()
	{
		return( tracker_ip );
	}

	public static boolean
	isTrackerEnabled()
	{
		return( getAnnounceURLs().length > 0 );
	}

	public static URL[][]
	getAnnounceURLs()
	{
		String	tracker_host = COConfigurationManager.getStringParameter( "Tracker IP", "" );

		List	urls = new ArrayList();

		if ( tracker_host.length() > 0 ){

			if ( COConfigurationManager.getBooleanParameter( "Tracker Port Enable")){

				int port = COConfigurationManager.getIntParameter("Tracker Port", TRHost.DEFAULT_PORT );

				try{
					List	l = new ArrayList();

					l.add( new URL( "http://" + UrlUtils.convertIPV6Host( tracker_host ) + ":" + port + "/announce" ));

					List	ports = stringToPorts( COConfigurationManager.getStringParameter("Tracker Port Backups" ));

					for (int i=0;i<ports.size();i++){

						l.add( new URL( "http://" + UrlUtils.convertIPV6Host( tracker_host ) + ":" + ((Integer)ports.get(i)).intValue() + "/announce" ));
					}

					urls.add( l );

				}catch( MalformedURLException e ){

					Debug.printStackTrace( e );
				}
			}

			if ( COConfigurationManager.getBooleanParameter( "Tracker Port SSL Enable")){

				int port = COConfigurationManager.getIntParameter("Tracker Port SSL", TRHost.DEFAULT_PORT_SSL );

				try{
					List	l = new ArrayList();

					l.add( new URL( "https://" + UrlUtils.convertIPV6Host( tracker_host ) + ":" + port + "/announce" ));

					List	ports = stringToPorts( COConfigurationManager.getStringParameter("Tracker Port SSL Backups" ));

					for (int i=0;i<ports.size();i++){

						l.add( new URL( "https://" + UrlUtils.convertIPV6Host( tracker_host ) + ":" + ((Integer)ports.get(i)).intValue() + "/announce" ));
					}

					urls.add( l );


				}catch( MalformedURLException e ){

					Debug.printStackTrace( e );
				}
			}

			if ( COConfigurationManager.getBooleanParameter( "Tracker Port UDP Enable" )){

				int port = COConfigurationManager.getIntParameter("Tracker Port", TRHost.DEFAULT_PORT );

				boolean	auth = COConfigurationManager.getBooleanParameter( "Tracker Password Enable Torrent" );

				try{
					List	l = new ArrayList();

					l.add( new URL( "udp://" + UrlUtils.convertIPV6Host( tracker_host ) + ":" + port + "/announce" +
										(auth?"?auth":"" )));

					urls.add( l );

				}catch( MalformedURLException e ){

					Debug.printStackTrace( e );
				}
			}
		}

		URL[][]	res = new URL[urls.size()][];

		for (int i=0;i<urls.size();i++){

			List	l = (List)urls.get(i);

			URL[]	u = new URL[l.size()];

			l.toArray( u );

			res[i] = u;
		}

		return( res );
	}

	protected static List
	stringToPorts(
		String	str )
	{
		str = str.replace(',', ';' );

		StringTokenizer	tok = new StringTokenizer( str, ";" );

		List	res = new ArrayList();

		while( tok.hasMoreTokens()){

			try{
				res.add( new Integer( tok.nextToken().trim()));

			}catch( Throwable e ){

				Debug.out("Invalid port entry in '" + str + "'", e);
			}
		}

		return( res );
	}

	public static URL
	adjustURLForHosting(
		URL		url_in )
	{
		if ( isHosting( url_in )){

			String	url = url_in.getProtocol() + "://";

			if ( bind_ip.length() < 7 ){

					// TODO: this won't work in a pure IPv6 setup

				url += "127.0.0.1";

			}else{

				if ( bind_ip.contains( ":" )){

					url += "[" + bind_ip + "]";

				}else{
				      url += bind_ip;
				}
			}

			int	port = url_in.getPort();

			if ( port != -1 ){

				url += ":" + url_in.getPort();
			}

			url += url_in.getPath();

			String query = url_in.getQuery();

			if ( query != null ){

				url += "?" + query;
			}

			try{
				return( new URL( url ));

			}catch( MalformedURLException e ){

				Debug.printStackTrace( e );
			}
		}

		return( url_in );
	}

	public static String
	adjustHostFromHosting(
		String		host_in )
	{
		if ( tracker_ip.length() > 0 ){

			String	address_type = AENetworkClassifier.categoriseAddress( host_in );

			String	target_ip = (String)override_map.get( address_type );

			if ( target_ip == null ){

				target_ip	= tracker_ip;
			}

			if ( isLoopback( host_in )){

				return( target_ip );
			}
		}

		return( host_in );
	}

	public static boolean
	isLoopback(
		String	host )
	{
		return(
			host.equals( "127.0.0.1" ) ||
			host.equals( "0:0:0:0:0:0:0:1" ) || host.equals( "::1" ) ||
			host.equals( bind_ip ));
	}


	public static void
	checkForBlacklistedURLs(
		URL		url )

		throws IOException
	{
		for (int i=0;i<BLACKLISTED_HOSTS.length;i++){

 			if ( 	url.getHost().equalsIgnoreCase( BLACKLISTED_HOSTS[i] ) &&
 					url.getPort() == BLACKLISTED_PORTS[i] ){

 				throw( new IOException( "http://" + BLACKLISTED_HOSTS[i] +
 						":" + BLACKLISTED_PORTS[i] + "/ is not a tracker" ));
 			}
 		}
	}

	public static Map
	mergeResponseCache(
		Map		map1,
		Map		map2 )
	{
		return( TRTrackerAnnouncerImpl.mergeResponseCache( map1, map2 ));
	}

 	public static String
	getPortsForURL(
		int			required_tcp_port,
		boolean		disable_crypto_port )
  	{
 		if ( disable_crypto_port ){
 			
 				// lazy, no caching as uncommon option
 			
 			return( computePortsForURL( required_tcp_port, false, true, true ));
 			
 		}else{
 			
	 		if ( required_tcp_port == ports_tcp_port ){
	  		
	 			return( ports_for_url );
	 		}
	 		
	 		return( computePortsForURL( required_tcp_port, false, true, false ));
 		}
  	}

 	public static String
 	getPortsForURLFullCrypto(
 		int	required_tcp_port )
 	{
 		if ( required_tcp_port == ports_tcp_port ){
 			
 			return( ports_for_url_with_crypto );
 		}
 		
		return( computePortsForURL( required_tcp_port, true, false, false ));
 	}

 	public static boolean
 	isAZTracker(
 		URL		tracker_url )
 	{
 		String	host = tracker_url.getHost();

 		if ( Constants.isAppDomain( host )){

 			return( true );
 		}

 		synchronized( az_trackers ){

 	    	return( az_trackers.containsKey( host + ":" + tracker_url.getPort()));
 	    }
 	}

	public static void
 	setAZTracker(
 		URL		tracker_url,
 		boolean	az_tracker )
	{
		String	key = tracker_url.getHost() + ":" + tracker_url.getPort();

		synchronized( az_trackers ){

			boolean	changed = false;

			if ( az_trackers.get( key ) == null ){

				if ( az_tracker ){

					az_trackers.put( key, new Long( SystemTime.getCurrentTime()));

					changed	= true;
				}
			}else{

				if ( !az_tracker ){

					if ( az_trackers.remove( key ) != null ){

						changed = true;
					}
				}
			}

			if ( changed ){

				COConfigurationManager.setParameter( "Tracker Client AZ Instances", az_trackers );
			}
		}
	}

 	public static boolean
 	isUDPProbeOK(
 		URL		tracker_url )
 	{
 		String	host = tracker_url.getHost();

 		if ( Constants.isAppDomain( host )){

 			return( false );
 		}

 		synchronized( udp_probe_results ){

 	    	return( udp_probe_results.containsKey( host ));
 	    }
 	}

	public static void
 	setUDPProbeResult(
 		URL			tracker_url,
 		boolean		probe_ok )
	{
		String	key = tracker_url.getHost();

		synchronized( udp_probe_results ){

			boolean	changed = false;

			if ( udp_probe_results.get( key ) == null ){

				if ( probe_ok ){

						// arbitrary max size here just in case something weird happens

					if ( udp_probe_results.size() > 512 ){

						udp_probe_results.clear();
					}

					udp_probe_results.put( key, new Long( SystemTime.getCurrentTime()));

					changed	= true;
				}
			}else{

				if ( !probe_ok ){

					if ( udp_probe_results.remove( key ) != null ){

						changed = true;
					}
				}
			}

			if ( changed ){

				COConfigurationManager.setParameter( "Tracker Client UDP Probe Results", udp_probe_results );
			}
		}
	}

	public static void
	addListener(
		TRTrackerUtilsListener	l )
	{
		listeners.add( l );
	}

	public static void
	removeListener(
		TRTrackerUtilsListener	l )
	{
		listeners.remove( l );
	}
}
