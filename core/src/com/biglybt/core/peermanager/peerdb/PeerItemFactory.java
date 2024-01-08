/*
 * Created on Apr 27, 2005
 * Created by Alon Rohter
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

package com.biglybt.core.peermanager.peerdb;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.util.DataSourceResolver;
import com.biglybt.core.util.StringInterner;
import com.biglybt.core.util.DataSourceResolver.DataSourceImporter;
import com.biglybt.core.util.DataSourceResolver.ExportedDataSource;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerDescriptor;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.util.MapUtils;



/**
 *
 */
public class PeerItemFactory {
  public static final byte PEER_SOURCE_TRACKER       = 0;
  public static final byte PEER_SOURCE_DHT           = 1;
  public static final byte PEER_SOURCE_PEER_EXCHANGE = 2;
  public static final byte PEER_SOURCE_PLUGIN        = 3;
  public static final byte PEER_SOURCE_INCOMING      = 4;
  public static final byte PEER_SOURCE_HOLE_PUNCH    = 5;

  public static final byte HANDSHAKE_TYPE_PLAIN  = 0;
  public static final byte HANDSHAKE_TYPE_CRYPTO = 1;

  public static final byte	CRYPTO_LEVEL_1			= 1;
  public static final byte	CRYPTO_LEVEL_2			= 2;
  public static final byte	CRYPTO_LEVEL_CURRENT	= CRYPTO_LEVEL_2;

  public static void
  initialise()
  {
	  DataSourceResolver.registerExporter( new DataSourceImporter());
  }
  
  /**
   * Create a peer item using the given peer address and port information.
   * @param address of peer
   * @param port of peer
   * @param source this peer info was obtained from
   * @return peer
   */
  public static PeerItem
  createPeerItem(
	String 	address,
	int 	tcp_port,
	byte 	source,
	byte 	handshake_type,
	int 	udp_port,
	byte 	crypto_level,
	int		up_speed )

  {
    return (PeerItem)StringInterner.internObject( new PeerItem( address, tcp_port, source, handshake_type, udp_port, crypto_level, up_speed ) );
  }

  /**
   * Create a peer item using the given peer raw byte serialization (address and port).
   * @param serialization bytes
   * @param source this peer info was obtained from
   * @return peer
   */
  public static PeerItem createPeerItem( byte[] serialization, byte source, byte handshake_type, int udp_port, String network ) throws Exception {
    return (PeerItem)StringInterner.internObject( new PeerItem( serialization, source, handshake_type, udp_port, network ) );
  }


  public static PeerDescriptor
  getDescriptor(
	PEPeer	peer )
  {
	  return( new PeerDescriptorImpl( peer ));
  }

  public static PeerDescriptor
  getDescriptor(
	Peer	peer )
  {
	  PEPeer pe_peer = PluginCoreUtils.unwrap( peer );
	  
	  if ( pe_peer != null ){
		  
		  return( getDescriptor( pe_peer ));
	  }
	  
	  return( new PeerDescriptorImpl( peer ));
  }
  
  public static class
  DataSourceImporter
  	implements DataSourceResolver.DataSourceImporter
  {
	  @Override
	  public Object
	  importDataSource(
		Map<String, Object> map)
	  {
		  return( new PeerDescriptorImpl( map ));
	  }
  }
  
  private static class
  PeerDescriptorImpl
  implements PeerDescriptor
  {
	  private final String 	ip;
	  private final int		tcp_port;
	  private final int		udp_port;
	  private final boolean	use_crypto;
	  private final String	peer_source;
	  
	  private
	  PeerDescriptorImpl(
		  Peer		peer )
	  {
		  	// this only used for plugin peer connections
		  
		  ip 			= peer.getIp();
		  tcp_port		= peer.getPort();
		  udp_port		= 0;
		  use_crypto	= false;
		  peer_source	= PEPeerSource.PS_PLUGIN;
	  }

	  private
	  PeerDescriptorImpl(
		  PEPeer		peer )
	  {
		  ip 			= peer.getIp();
		  tcp_port		= peer.getPort();
		  udp_port		= peer.getUDPListenPort();
		  
		  boolean uc = false;
		  
		  try{
			  uc	= peer.getPluginConnection().getTransport().isEncrypted();
		  }catch( Throwable e ){
		  }
		  
		  use_crypto = uc;
		  
		  peer_source	= peer.getPeerSource();
	  }


	  private
	  PeerDescriptorImpl(
		  Map<String, Object> map )
	  {
		  ip			= MapUtils.getMapString(map, "ip", "" );
		  tcp_port		= MapUtils.getMapInt(map, "tcp", 0 );
		  udp_port		= MapUtils.getMapInt(map, "udp", 0 );
		  use_crypto	= MapUtils.getMapBoolean(map, "crypto", false );
		  peer_source	= MapUtils.getMapString(map, "ps", "" );
	  }

	  public String
	  getIP()
	  {
		  return( ip );
	  }

	  public int
	  getTCPPort()
	  {
		  return( tcp_port );
	  }

	  public int
	  getUDPPort()
	  {
		  return( udp_port );
	  }

	  public boolean
	  useCrypto()
	  {
		  return( use_crypto );
	  }
	  
	  public String
	  getPeerSource()
	  {
		  return( peer_source );
	  }

	  private Map<String,Object>
	  export()
	  {
		  Map<String,Object> result = new HashMap<>();
		  
		  result.put( "ip", ip );
		  result.put( "tcp", tcp_port );
		  result.put( "udp", udp_port );
		  result.put( "crypto", use_crypto?1:0);
		  result.put( "ps", peer_source );
		  
		  return( result );
	  }
	  
	  public ExportedDataSource
	  exportDataSource()
	  {
		  Map<String,Object> map = export();

		  return( 
				  new ExportedDataSource()
				  {
					  public Class<? extends DataSourceImporter>
					  getExporter()
					  {
						  return( DataSourceImporter.class );
					  }

					  public Map<String,Object>
					  getExport()
					  {
						  return( map );
					  }
				  });
	  }
  }
}
