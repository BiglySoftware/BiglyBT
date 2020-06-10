/*
 * Created on Jul 17, 2004
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

package com.biglybt.core.peermanager.messaging.bittorrent;

import com.biglybt.core.peermanager.messaging.Message;

/**
 * A bittorrent peer protocol message.
 */
public interface BTMessage extends Message {
  public static final String BT_FEATURE_ID = "BT1";

  public static final String ID_BT_CHOKE        	= "BT_CHOKE";
  public static final byte[] ID_BT_CHOKE_BYTES      = ID_BT_CHOKE.getBytes();
  public static final int SUBID_BT_CHOKE			= 0;

  public static final String ID_BT_UNCHOKE      	= "BT_UNCHOKE";
  public static final byte[] ID_BT_UNCHOKE_BYTES    = ID_BT_UNCHOKE.getBytes();
  public static final int SUBID_BT_UNCHOKE			= 1;

  public static final String ID_BT_INTERESTED   	= "BT_INTERESTED";
  public static final byte[] ID_BT_INTERESTED_BYTES = ID_BT_INTERESTED.getBytes();
  public static final int SUBID_BT_INTERESTED		= 2;

  public static final String ID_BT_UNINTERESTED 		= "BT_UNINTERESTED";
  public static final byte[] ID_BT_UNINTERESTED_BYTES 	= ID_BT_UNINTERESTED.getBytes();
  public static final int SUBID_BT_UNINTERESTED			= 3;

  public static final String ID_BT_HAVE         	= "BT_HAVE";
  public static final byte[] ID_BT_HAVE_BYTES       = ID_BT_HAVE.getBytes();
  public static final int SUBID_BT_HAVE				= 4;

  public static final String ID_BT_BITFIELD     	= "BT_BITFIELD";
  public static final byte[] ID_BT_BITFIELD_BYTES   = ID_BT_BITFIELD.getBytes();
  public static final int SUBID_BT_BITFIELD			= 5;

  public static final String ID_BT_REQUEST      	= "BT_REQUEST";
  public static final byte[] ID_BT_REQUEST_BYTES    = ID_BT_REQUEST.getBytes();
  public static final int SUBID_BT_REQUEST			= 6;

  public static final String ID_BT_PIECE        	= "BT_PIECE";
  public static final byte[] ID_BT_PIECE_BYTES      = ID_BT_PIECE.getBytes();
  public static final int SUBID_BT_PIECE			= 7;

  public static final String ID_BT_CANCEL       	= "BT_CANCEL";
  public static final byte[] ID_BT_CANCEL_BYTES     = ID_BT_CANCEL.getBytes();
  public static final int SUBID_BT_CANCEL			= 8;

  public static final String ID_BT_DHT_PORT         = "BT_DHT_PORT";
  public static final byte[] ID_BT_DHT_PORT_BYTES   = ID_BT_DHT_PORT.getBytes();
  public static final int SUBID_BT_DHT_PORT         = 9;

  public static final String ID_BT_HANDSHAKE    	= "BT_HANDSHAKE";
  public static final byte[] ID_BT_HANDSHAKE_BYTES  = ID_BT_HANDSHAKE.getBytes();
  public static final int SUBID_BT_HANDSHAKE		= 10;

  public static final String ID_BT_KEEP_ALIVE   	= "BT_KEEP_ALIVE";
  public static final byte[] ID_BT_KEEP_ALIVE_BYTES = ID_BT_KEEP_ALIVE.getBytes();
  public static final int SUBID_BT_KEEP_ALIVE		= 11;

  public static final String ID_BT_SUGGEST_PIECE   	= "BT_SUGGEST_PIECE";
  public static final byte[] ID_BT_SUGGEST_PIECE_BYTES = ID_BT_SUGGEST_PIECE.getBytes();
  public static final int SUBID_BT_SUGGEST_PIECE	= 13;

  public static final String ID_BT_HAVE_ALL   		= "BT_HAVE_ALL";
  public static final byte[] ID_BT_HAVE_ALL_BYTES 	= ID_BT_HAVE_ALL.getBytes();
  public static final int SUBID_BT_HAVE_ALL			= 14;

  public static final String ID_BT_HAVE_NONE	   	= "BT_HAVE_NONE";
  public static final byte[] ID_BT_HAVE_NONE_BYTES = ID_BT_HAVE_NONE.getBytes();
  public static final int SUBID_BT_HAVE_NONE		= 15;

  public static final String ID_BT_REJECT_REQUEST  	= "BT_REJECT_REQUEST";
  public static final byte[] ID_BT_REJECT_REQUEST_BYTES = ID_BT_REJECT_REQUEST.getBytes();
  public static final int SUBID_BT_REJECT_REQUEST	= 16;

  public static final String ID_BT_ALLOWED_FAST   	= "BT_ALLOWED_FAST";
  public static final byte[] ID_BT_ALLOWED_FAST_BYTES = ID_BT_ALLOWED_FAST.getBytes();
  public static final int SUBID_BT_ALLOWED_FAST		= 17;

  public static final String ID_BT_LT_EXT_MESSAGE        = "BT_LT_EXT_MESSAGE";
  public static final byte[] ID_BT_LT_EXT_MESSAGE_BYTES  = ID_BT_LT_EXT_MESSAGE.getBytes();
  public static final int SUBID_BT_LT_EXT_MESSAGE	= 20;
  
  public static final String ID_BT_HASH_REQUEST			= "BT_HASH_REQUEST";
  public static final byte[] ID_BT_HASH_REQUEST_BYTES	= ID_BT_HASH_REQUEST.getBytes();
  public static final int SUBID_BT_HASH_REQUEST			= 21;

  public static final String ID_BT_HASHES        		= "BT_HASHES";
  public static final byte[] ID_BT_HASHES_BYTES  		= ID_BT_HASHES.getBytes();
  public static final int SUBID_BT_HASHES				= 22;

  public static final String ID_BT_HASH_REJECT        	= "BT_HASH_REJECT";
  public static final byte[] ID_BT_HASH_REJECT_BYTES  	= ID_BT_HASH_REJECT.getBytes();
  public static final int SUBID_BT_HASH_REJECT			= 23;

  public static final int SUBID_MAX	= 23;
}
