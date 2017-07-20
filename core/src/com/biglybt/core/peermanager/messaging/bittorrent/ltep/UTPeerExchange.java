/*
 * Created on 18 Sep 2007
 * Created by Allan Crooks
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
package com.biglybt.core.peermanager.messaging.bittorrent.ltep;

import java.util.*;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessagingUtil;
import com.biglybt.core.peermanager.messaging.azureus.AZStylePeerExchange;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.peermanager.peerdb.PeerItemFactory;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;

/**
 * @author Allan Crooks
 *
 * Largely copied from AZPeerExchange.
 */
public class UTPeerExchange implements AZStylePeerExchange, LTMessage {

	  private static final LogIDs LOGID = LogIDs.NET;

	  private static final int IPv4_SIZE_WITH_PORT = 6;
	  private static final int IPv6_SIZE_WITH_PORT = 18;

	  private DirectByteBuffer buffer = null;
	  private String description = null;

	  private final byte version;
	  private final PeerItem[] peers_added;
	  private final PeerItem[] peersAddedNoSeeds;
	  private final PeerItem[] peers_dropped;

	  public UTPeerExchange(PeerItem[] _peers_added, PeerItem[] _peers_dropped, PeerItem[] peersAddedNoSeeds,  byte version ) {
	    this.peers_added = _peers_added;
	    this.peers_dropped = _peers_dropped;
	    this.version = version;
	    this.peersAddedNoSeeds = peersAddedNoSeeds != null ? peersAddedNoSeeds : _peers_added;
	  }

	  private void insertPeers(String key_name, Map root_map, boolean include_flags, PeerItem[] peers) {
		  if (peers == null) {return;}
		  if (peers.length == 0) {return;}

		  List v4_peers = null;
		  List v6_peers = null;
		  for (int i=0; i<peers.length; i++) {
			  if (!peers[i].isIPv4()) {
				  if (v6_peers == null) {
					  v6_peers = new ArrayList();
					  v4_peers = new ArrayList(Arrays.asList(peers).subList(0, i));
				  }
				  v6_peers.add(peers[i]);
			  }
			  else {
				  if (v4_peers != null) {
					  v4_peers.add(peers[i]);
				  }
			  }
		  }
		  if (v4_peers == null) {v4_peers = Arrays.asList(peers);}

		  insertPeers(key_name, root_map, include_flags, v4_peers, IPv4_SIZE_WITH_PORT);
		  insertPeers(key_name + "6", root_map, include_flags, v6_peers, IPv6_SIZE_WITH_PORT);
	  }

	  private void insertPeers(String key_name, Map root_map, boolean include_flags, List peers, int peer_byte_size) {
		  if (peers == null) {return;}
		  if (peers.isEmpty()) {return;}

		  byte[] raw_peers = new byte[peers.size() * peer_byte_size];
		  byte[] peer_flags = (include_flags) ? new byte[peers.size()] : null;

		  PeerItem peer;
	      for (int i=0; i<peers.size(); i++ ) {
	    	  peer = (PeerItem)peers.get(i);
	    	  byte[] serialised_peer = peer.getSerialization();
	    	  if (serialised_peer.length != peer_byte_size) {
	    		  Debug.out("invalid serialization- " + serialised_peer.length + ":" + peer_byte_size);
	    	  }
	    	  System.arraycopy(serialised_peer, 0, raw_peers, i * peer_byte_size, peer_byte_size);
	    	  if (peer_flags != null && NetworkManager.getCryptoRequired(peer.getCryptoLevel())) {
	    		  peer_flags[i] |= 0x01; // Encrypted connection.
	    	  }
	    	  // 0x02 indicates if the peer is a seed, but that's difficult to determine
	    	  // so we'll leave it.
	      } // end for

	      root_map.put(key_name, raw_peers);
	      if (peer_flags != null) {
	         root_map.put(key_name + ".f", peer_flags);
	      }
	  }

	  private List extractPeers(String key_name, Map root_map, int peer_byte_size, boolean noSeeds) {
	    ArrayList peers = new ArrayList();

	    byte[] raw_peer_data = (byte[])root_map.get(key_name);
	    if( raw_peer_data != null ) {
	    	if (raw_peer_data.length % peer_byte_size != 0) {
	    		if (Logger.isEnabled())
	    			Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "PEX (UT): peer data size not multiple of " + peer_byte_size + ": " + raw_peer_data.length));
	    	}
	      int peer_num = raw_peer_data.length / peer_byte_size;

	      byte[] flags = null;

    	  Object flags_obj = root_map.get(key_name + ".f");

    	  // For some reason, some peers send flags as longs. I haven't seen
    	  // it myself, so I don't know how to extract data from it. So we'll
    	  // just stick to byte arrays.


    	  if (flags_obj instanceof byte[]) {flags = (byte[])flags_obj;}


	      if (flags != null && flags.length != peer_num) {
	    	  if (flags.length > 0) {
	    		  if (Logger.isEnabled()) {
	    			  Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "PEX (UT): invalid peer flags: peers=" + peer_num + ", flags=" + flags.length ));
	    		  }
	    	  }
	    	  flags = null;
	      }

	      for (int i=0; i<peer_num; i++) {
	    	  byte[] full_address = new byte[peer_byte_size];
	    	  System.arraycopy(raw_peer_data, i * peer_byte_size, full_address, 0, peer_byte_size);
	    	  byte type = PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
	    	  if (flags != null && (flags[i] & 0x01) != 0)
	    		  type = PeerItemFactory.HANDSHAKE_TYPE_CRYPTO;
	    	  if (flags != null && (flags[i] & 0x02) != 0 && noSeeds)
	    		  continue;

	    	  try {
	    		  PeerItem peer = PeerItemFactory.createPeerItem(full_address, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, type, 0, AENetworkClassifier.AT_PUBLIC);
	    		  peers.add(peer);
	    	  }
	    	  catch (Exception e) {
	    		  if (Logger.isEnabled())
	    		  	Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "PEX (UT): invalid peer received"));
		      }
	      }

	    }
	    return peers;
	  }

	  public PeerItem[] getAddedPeers(boolean seeds) {return seeds ? peers_added : peersAddedNoSeeds; }
	  @Override
	  public PeerItem[] getAddedPeers() {  return peers_added;  }
	  @Override
	  public PeerItem[] getDroppedPeers() {  return peers_dropped;  }
	  @Override
	  public String getID() {  return LTMessage.ID_UT_PEX;  }
	  @Override
	  public byte[] getIDBytes() {  return LTMessage.ID_UT_PEX_BYTES;  }
	  @Override
	  public String getFeatureID() {  return LTMessage.LT_FEATURE_ID;  }
	  @Override
	  public int getFeatureSubID() { return LTMessage.SUBID_UT_PEX;  }
	  @Override
	  public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }
	  @Override
	  public byte getVersion() { return version; }

	@Override
	public String getDescription() {
	    if( description == null ) {
	      int add_count = peers_added == null ? 0 : peers_added.length;
	      int drop_count = peers_dropped == null ? 0 : peers_dropped.length;

	      description = getID().toUpperCase() + " with " +add_count+ " added and " +drop_count+ " dropped peers";
	    }

	    return description;
	  }


	  @Override
	  public DirectByteBuffer[] getData() {
	    if( buffer == null ) {
	      Map payload_map = new HashMap();
	      // bencoded_buffer = payload_map;
	      insertPeers("added", payload_map, true, peers_added );
	      insertPeers("dropped", payload_map, false, peers_dropped );
	      buffer = MessagingUtil.convertPayloadToBencodedByteStream(payload_map, DirectByteBuffer.AL_MSG_UT_PEX);
	    }

	    return new DirectByteBuffer[] {buffer};
	  }


	  @Override
	  public Message deserialize(DirectByteBuffer data, byte version ) throws MessageException {
	    Map root = MessagingUtil.convertBencodedByteStreamToPayload(data, 2, getID());
	    List added = extractPeers("added", root, IPv4_SIZE_WITH_PORT,false);
	    List addedNoSeeds = extractPeers("added", root, IPv4_SIZE_WITH_PORT,true);
	    List dropped = extractPeers("dropped", root, IPv4_SIZE_WITH_PORT,false);

	    added.addAll(extractPeers("added6", root, IPv6_SIZE_WITH_PORT,false));
	    addedNoSeeds.addAll(extractPeers("added6", root, IPv6_SIZE_WITH_PORT,true));
	    dropped.addAll(extractPeers("dropped6", root, IPv6_SIZE_WITH_PORT,false));

	    PeerItem[] addedArr = (PeerItem[])added.toArray(new PeerItem[added.size()]);
	    PeerItem[] addedNoSeedsArr = (PeerItem[])addedNoSeeds.toArray(new PeerItem[addedNoSeeds.size()]);
	    PeerItem[] droppedArr = (PeerItem[])dropped.toArray(new PeerItem[dropped.size()]);

	    return new UTPeerExchange(addedArr, droppedArr,addedNoSeedsArr, version);
	  }


	  @Override
	  public void destroy() {
	    if( buffer != null )  buffer.returnToPool();
	  }

	  /**
	   * Arbitrary value - most clients are configured to about 100 or so...
	   * We'll allow ourselves to be informed about 200 connected peers from
	   * the initial handshake, and then cap either list to about 100.
	   *
	   * These values are plucked from the air really - although I've seen PEX
	   * sizes where the added list is about 300 (sometimes), most contain a
	   * sensible number (not normally over 100).
	   *
	   * Subsequent PEX messages are relatively small too, so we'll stick to
	   * smaller limits - 50 would be probably fine, but watching some big
	   * swarms over a short period, the biggest "added" list I saw was one
	   * containing 38 peers, so it's quite possible list sizes above 50 get
	   * sent out. So 100 is a safe-ish figure.
	   *
	   * Update: uTorrent doesn't have any set limits on this, apparently it simply
	   * depends on the number of connections a peer has, which can be large for
	   * fast peers (I've seen 350 peers for example). Increased limits somewhat
	   * and added code to ignore excessive peers rather than dropping the
	   * connection
	   */
	  @Override
	  public int getMaxAllowedPeersPerVolley(boolean initial, boolean added) {
		  return (initial && added) ? 500 : 250;
	  }

	  /**** DEBUG STUFF ****/

	  /*
	  public String toString() {
		  List adds = (this.peers_added != null) ? Arrays.asList(this.peers_added) : null;
		  List drops = (this.peers_dropped != null) ? Arrays.asList(this.peers_dropped) : null;
		  return "UTPEX: " + adds + ", " + drops;
	  }

	  private Map bencoded_buffer = null;

	  public static void main(String[] args) throws Exception {
        PeerItem[] p1 = new PeerItem[] {
        	PeerItemFactory.createPeerItem("2001:0db8:85a3:08d3:1319:8a2e:0370:7334", 4096, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_PLAIN, 0, PeerItemFactory.CRYPTO_LEVEL_1, 10),
        	PeerItemFactory.createPeerItem("2001:0db8:0:0:0:8a2e:0370:7334", 128, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_CRYPTO, 0, PeerItemFactory.CRYPTO_LEVEL_CURRENT, 10),
        	PeerItemFactory.createPeerItem("1280:0:0:0:0:0:0:7334", 255, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_PLAIN, 0, PeerItemFactory.CRYPTO_LEVEL_1, 25),
		};
        PeerItem[] p2 = new PeerItem[] {
            	PeerItemFactory.createPeerItem("192.168.0.1", 6473, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_PLAIN, 16, PeerItemFactory.CRYPTO_LEVEL_1, 10),
            	PeerItemFactory.createPeerItem("127.0.0.1", 128, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_CRYPTO, 0, PeerItemFactory.CRYPTO_LEVEL_1, 10),
            	PeerItemFactory.createPeerItem("172.16.0.1", 255, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_PLAIN, 0, PeerItemFactory.CRYPTO_LEVEL_1, 25),
    	};
        PeerItem[] p3 = new PeerItem[] {
            	PeerItemFactory.createPeerItem("2001:0db8:85a3:08d3:1319:8a2e:0370:7334", 55, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_PLAIN, 0, PeerItemFactory.CRYPTO_LEVEL_1, 10),
            	PeerItemFactory.createPeerItem("127.0.0.1", 128, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_CRYPTO, 0, PeerItemFactory.CRYPTO_LEVEL_1, 10),
            	PeerItemFactory.createPeerItem("1280:0:0:0:0:0:0:7334", 255, PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE, PeerItemFactory.HANDSHAKE_TYPE_PLAIN, 0, PeerItemFactory.CRYPTO_LEVEL_1, 25),
    	};

        UTPeerExchange u1 = new UTPeerExchange(p1, p2, (byte)0);
        UTPeerExchange u2 = new UTPeerExchange(p2, p3, (byte)0);
        UTPeerExchange u3 = new UTPeerExchange(p3, p1, (byte)0);
        UTPeerExchange u4 = new UTPeerExchange(new PeerItem[0], p1, (byte)0);
        UTPeerExchange u5 = new UTPeerExchange(p1, new PeerItem[0], (byte)0);

        u1.getData();
        u2.getData();
        u3.getData();
        u4.getData();
        u5.getData();

        UTPeerExchange[] uts = new UTPeerExchange[] {null, u1, u2, u3, u4, u5};
        for (int i=1; i<6; i++) {
        	java.util.Iterator itr = uts[i].bencoded_buffer.keySet().iterator();
        	while (itr.hasNext()) {
        		String k = (String)itr.next();
        		byte[] b = (byte[])uts[i].bencoded_buffer.get(k);
        		System.out.println(k + ": " + com.biglybt.core.util.ByteFormatter.encodeString(b));
        	}
        	System.out.println('-');
        }

        System.out.println(u1.deserialize(u1.getData()[0], (byte)0));
        System.out.println(u1.deserialize(u2.getData()[0], (byte)0));
        System.out.println(u1.deserialize(u3.getData()[0], (byte)0));
        System.out.println(u1.deserialize(u4.getData()[0], (byte)0));
        System.out.println(u1.deserialize(u5.getData()[0], (byte)0));

	  }
	  */

}
