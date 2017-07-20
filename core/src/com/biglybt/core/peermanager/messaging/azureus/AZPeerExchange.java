/*
 * Created on Apr 30, 2004
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

package com.biglybt.core.peermanager.messaging.azureus;

import java.util.*;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessagingUtil;
import com.biglybt.core.peermanager.peerdb.PeerExchangerItem;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.peermanager.peerdb.PeerItemFactory;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.DirectByteBuffer;




/**
 * AZ peer exchange message.
 */
public class AZPeerExchange implements AZMessage, AZStylePeerExchange {
  private static final LogIDs LOGID = LogIDs.NET;

  private static final byte bss = DirectByteBuffer.SS_MSG;

  private DirectByteBuffer buffer = null;
  private String description = null;

  private final byte version;
  private final byte[] infohash;
  private final PeerItem[] peers_added;
  private final PeerItem[] peers_dropped;



  public AZPeerExchange( byte[] _infohash, PeerItem[] _peers_added, PeerItem[] _peers_dropped, byte version ) {
    this.infohash = _infohash;
    this.peers_added = _peers_added;
    this.peers_dropped = _peers_dropped;
    this.version = version;
  }



  private void insertPeers( String key_name, Map root_map, PeerItem[] peers ) {
    if( peers != null && peers.length > 0 ) {
      ArrayList raw_peers = new ArrayList();
      byte[] handshake_types = new byte[ peers.length ];
      byte[] udp_ports = new byte[peers.length*2];	// 2403 B55+
      int	num_valid_udp = 0;

      for( int i=0; i < peers.length; i++ ) {
        raw_peers.add( peers[i].getSerialization() );
        handshake_types[i] = (byte)peers[i].getHandshakeType();
        int	udp_port = peers[i].getUDPPort();
        if ( udp_port > 0 ){
        	num_valid_udp++;
           	udp_ports[i*2] = (byte)(udp_port>>8);
           	udp_ports[i*2+1] = (byte)udp_port;
        }
      }

      root_map.put( key_name, raw_peers );
      root_map.put( key_name + "_HST", handshake_types );
      if ( num_valid_udp > 0 ){
    	root_map.put( key_name + "_UDP", udp_ports );
      }
    }
  }



  private PeerItem[] extractPeers( String key_name, Map root_map ) {
    PeerItem[] return_peers = null;
    ArrayList peers = new ArrayList();

    List raw_peers = (List)root_map.get( key_name );
    if( raw_peers != null ) {
      int	peer_num = raw_peers.size();
      byte[] handshake_types = (byte[])root_map.get( key_name + "_HST" );
      byte[] udp_ports = (byte[])root_map.get( key_name + "_UDP" ); // 2403 B55+
      int pos = 0;

      if ( handshake_types != null && handshake_types.length != peer_num ){
     	  Logger.log(new LogEvent( LOGID, LogEvent.LT_WARNING,"PEX: invalid handshake types received: peers=" + peer_num + ",handshakes=" + handshake_types.length ));
       	  handshake_types = null;
      }

      if ( udp_ports != null && udp_ports.length != peer_num*2 ){
       	  Logger.log(new LogEvent( LOGID, LogEvent.LT_WARNING,"PEX: invalid udp ports received: peers=" + peer_num + ",udp_ports=" + udp_ports.length ));
    	  udp_ports = null;
      }

      for( Iterator it = raw_peers.iterator(); it.hasNext(); ) {
        byte[] full_address = (byte[])it.next();

        byte type = PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
        if( handshake_types != null ) { //only 2307+ send types
        	type = handshake_types[pos];
        }
        int	udp_port = 0;
        if ( udp_ports != null ){
        	udp_port = ((udp_ports[pos*2]<<8)&0xff00) + (udp_ports[pos*2+1]&0xff);
        }
        try{
        	PeerItem peer = PeerItemFactory.createPeerItem( full_address, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, type, udp_port, AENetworkClassifier.AT_PUBLIC );
        	peers.add( peer );
        }catch( Exception t ){
            Logger.log(new LogEvent( LOGID, LogEvent.LT_WARNING,"PEX: invalid peer received" ));
        }
        pos++;
      }
    }

    if( !peers.isEmpty() ) {
      return_peers = new PeerItem[ peers.size() ];
      peers.toArray( return_peers );
    }

    return return_peers;
  }





  public byte[] getInfoHash() {  return infohash;  }

  @Override
  public PeerItem[] getAddedPeers() {  return peers_added;  }

  @Override
  public PeerItem[] getDroppedPeers() {  return peers_dropped;  }



  @Override
  public String getID() {  return AZMessage.ID_AZ_PEER_EXCHANGE;  }
  @Override
  public byte[] getIDBytes() {  return AZMessage.ID_AZ_PEER_EXCHANGE_BYTES;  }

  @Override
  public String getFeatureID() {  return AZMessage.AZ_FEATURE_ID;  }

  @Override
  public int getFeatureSubID() { return AZMessage.SUBID_AZ_PEER_EXCHANGE;  }

  @Override
  public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }

  @Override
  public byte getVersion() { return version; }

	@Override
	public String getDescription() {
    if( description == null ) {
      int add_count = peers_added == null ? 0 : peers_added.length;
      int drop_count = peers_dropped == null ? 0 : peers_dropped.length;

      description = getID()+ " for infohash " +ByteFormatter.nicePrint( infohash, true )+ " with " +add_count+ " added and " +drop_count+ " dropped peers";
    }

    return description;
  }


  @Override
  public DirectByteBuffer[] getData() {
    if( buffer == null ) {
      Map payload_map = new HashMap();

      payload_map.put( "infohash", infohash );
      insertPeers( "added", payload_map, peers_added );
      insertPeers( "dropped", payload_map, peers_dropped );

      buffer = MessagingUtil.convertPayloadToBencodedByteStream( payload_map, DirectByteBuffer.AL_MSG_AZ_PEX );

      if( buffer.remaining( bss ) > 2000 )  System.out.println( "Generated AZPeerExchange size = " +buffer.remaining( bss )+ " bytes" );
    }

    return new DirectByteBuffer[]{ buffer };
  }


  @Override
  public Message deserialize(DirectByteBuffer data, byte version ) throws MessageException {
    if( data.remaining( bss ) > 2000 )  System.out.println( "Received PEX msg byte size = " +data.remaining( bss ) );

    Map root = MessagingUtil.convertBencodedByteStreamToPayload( data, 10, getID() );

    byte[] hash = (byte[])root.get( "infohash" );
    if( hash == null )  throw new MessageException( "hash == null" );
    if( hash.length != 20 )  throw new MessageException( "hash.length != 20: " +hash.length );

    PeerItem[] added = extractPeers( "added", root );
    PeerItem[] dropped = extractPeers( "dropped", root );

    if( added == null && dropped == null )  throw new MessageException( "[" +getID()+ "] received exchange message without any adds or drops" );

    return new AZPeerExchange( hash, added, dropped, version );
  }


  @Override
  public void destroy() {
    if( buffer != null )  buffer.returnToPool();
  }

  @Override
  public int getMaxAllowedPeersPerVolley(boolean initial, boolean added) {
	  return PeerExchangerItem.MAX_PEERS_PER_VOLLEY;
  }

}
