/*
 * File    : TRTrackerClientFactoryImpl.java
 * Created : 04-Nov-2003
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

package com.biglybt.core.tracker.client.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerException;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerFactory;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerFactoryListener;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponsePeer;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.download.DownloadAnnounceResultPeer;

public class
TRTrackerAnnouncerFactoryImpl
{
	protected static final List<TRTrackerAnnouncerFactoryListener>	listeners 	= new ArrayList<>();
	protected static final List<TRTrackerAnnouncerImpl>				clients		= new ArrayList<>();

	protected static final AEMonitor 		class_mon 	= new AEMonitor( "TRTrackerClientFactory" );

	public static TRTrackerAnnouncer
	create(
		TOTorrent									torrent,
		TRTrackerAnnouncerFactory.DataProvider		provider,
		boolean										manual )

		throws TRTrackerAnnouncerException
	{
		TRTrackerAnnouncerImpl	client = new TRTrackerAnnouncerMuxer( torrent, provider, manual );

		if ( !manual ){

			List<TRTrackerAnnouncerFactoryListener>	listeners_copy;

			try{
				class_mon.enter();

				clients.add( client );

				listeners_copy = new ArrayList<>(listeners);

			}finally{

				class_mon.exit();
			}

			for (int i=0;i<listeners_copy.size();i++){

				try{
					listeners_copy.get(i).clientCreated( client );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		return( client );
	}

		/*
		 * At least once semantics for this one
		 */

	public static void
	addListener(
		 TRTrackerAnnouncerFactoryListener	l )
	{
		List<TRTrackerAnnouncerImpl>	clients_copy;

		try{
			class_mon.enter();

			listeners.add(l);

			clients_copy = new ArrayList<>(clients);

		}finally{

			class_mon.exit();
		}

		for (int i=0;i<clients_copy.size();i++){

			try{
				l.clientCreated(clients_copy.get(i));

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	public static void
	removeListener(
		 TRTrackerAnnouncerFactoryListener	l )
	{
		try{
			class_mon.enter();

			listeners.remove(l);

		}finally{

			class_mon.exit();
		}
	}

	public static void
	destroy(
		TRTrackerAnnouncer	client )
	{
		if ( !client.isManual()){

			List<TRTrackerAnnouncerFactoryListener>	listeners_copy;

			try{
				class_mon.enter();

				clients.remove( client );

				listeners_copy	= new ArrayList<>(listeners);

			}finally{

				class_mon.exit();
			}

			for (int i=0;i<listeners_copy.size();i++){

				try{
					listeners_copy.get(i).clientDestroyed( client );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}
	}

 	public static byte[]
	getAnonymousPeerId(
		String	my_ip,
		int		my_port )
	{
  		byte[] anon_peer_id = new byte[20];

  		// unique initial two bytes to identify this as fake

  		anon_peer_id[0] = (byte)'[';
  		anon_peer_id[1] = (byte)']';

		byte[] ip_bytes = my_ip.getBytes(Constants.DEFAULT_ENCODING_CHARSET);
		int ip_len = ip_bytes.length;

		if (ip_len > 18) {
			ip_len = 18;
		}

		System.arraycopy(ip_bytes, 0, anon_peer_id, 2, ip_len);

		int port_copy = my_port;

		for (int j = 2 + ip_len; j < 20; j++) {

			anon_peer_id[j] = (byte) (port_copy & 0xff);

			port_copy >>= 8;
		}

		return anon_peer_id;
	}
	
	public static List<TRTrackerAnnouncerResponsePeer>
	getCachedPeers(
		Map		map )
	{
		List<TRTrackerAnnouncerResponsePeer>	result = new ArrayList<>();
		
		List	peers = (List)map.get( "tracker_peers" );

		if ( peers != null ){

			for (int i=0;i<peers.size();i++){
	
				Map	peer = (Map)peers.get(i);
	
				byte[]	src_bytes = (byte[])peer.get("src");
				String	peer_source = src_bytes==null?PEPeerSource.PS_BT_TRACKER:new String(src_bytes);
				String	peer_ip_address = new String((byte[])peer.get("ip"));
				int		peer_tcp_port	= ((Long)peer.get("port")).intValue();
				byte[]	peer_peer_id	= getAnonymousPeerId( peer_ip_address, peer_tcp_port );
				Long	l_protocol		= (Long)peer.get( "prot" );
				short	protocol		= l_protocol==null?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:l_protocol.shortValue();
				Long	l_udp_port		= (Long)peer.get("udpport");
				int		peer_udp_port	= l_udp_port==null?0:l_udp_port.intValue();
				Long	l_http_port		= (Long)peer.get("httpport");
				int		peer_http_port	= l_http_port==null?0:l_http_port.intValue();
				Long	l_az_ver		= (Long)peer.get("azver");
				byte	az_ver			= l_az_ver==null?TRTrackerAnnouncer.AZ_TRACKER_VERSION_1:l_az_ver.byteValue();
	
				//System.out.println( "recovered " + ip_address + ":" + port );
	
				TRTrackerAnnouncerResponsePeerImpl	entry =
					new TRTrackerAnnouncerResponsePeerImpl(
						peer_source,
						peer_peer_id,
						peer_ip_address,
						peer_tcp_port,
						peer_udp_port,
						peer_http_port,
						protocol,
						az_ver,
						(short)0 );
				
				entry.setCached( true );
				
				result.add( entry );
			}
		}
		
		return( result );
	}
}
