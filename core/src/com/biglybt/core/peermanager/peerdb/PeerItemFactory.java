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

import com.biglybt.core.util.StringInterner;



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



}
