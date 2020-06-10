/*
 * Created on Feb 20, 2005
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

import com.biglybt.core.peermanager.messaging.Message;

/**
 * A core AZ type peer message.
 */
public interface AZMessage extends Message {
	public static final String AZ_FEATURE_ID = "AZ1";

  public static final String ID_AZ_HANDSHAKE        	= "AZ_HANDSHAKE";
  public static final byte[] ID_AZ_HANDSHAKE_BYTES      = ID_AZ_HANDSHAKE.getBytes();
  public static final int SUBID_AZ_HANDSHAKE			= 0;

  public static final String ID_AZ_PEER_EXCHANGE    	= "AZ_PEER_EXCHANGE";
  public static final byte[] ID_AZ_PEER_EXCHANGE_BYTES  = ID_AZ_PEER_EXCHANGE.getBytes();
  public static final int SUBID_AZ_PEER_EXCHANGE		= 1;

  public static final String ID_AZ_GENERIC_MAP    		= "AZ_GENERIC_MAP";
  public static final byte[] ID_AZ_GENERIC_MAP_BYTES    = ID_AZ_GENERIC_MAP.getBytes();
  public static final int SUBID_AZ_GENERIC_MAP			= 2;

  public static final String ID_AZ_REQUEST_HINT    		= "AZ_REQUEST_HINT";
  public static final byte[] ID_AZ_REQUEST_HINT_BYTES  = ID_AZ_REQUEST_HINT.getBytes();
  public static final int SUBID_AZ_REQUEST_HINT			= 3;

  public static final String ID_AZ_HAVE	    			= "AZ_HAVE";
  public static final byte[] ID_AZ_HAVE_BYTES 			= ID_AZ_HAVE.getBytes();
  public static final int SUBID_AZ_HAVE					= 4;

  public static final String ID_AZ_BAD_PIECE	    	= "AZ_BAD_PIECE";
  public static final byte[] ID_AZ_BAD_PIECE_BYTES 		= ID_AZ_BAD_PIECE.getBytes();
  public static final int SUBID_AZ_BAD_PIECE			= 5;

  public static final String ID_AZ_STAT_REQUEST	    	= "AZ_STAT_REQ";
  public static final byte[] ID_AZ_STAT_REQUEST_BYTES 	= ID_AZ_STAT_REQUEST.getBytes();
  public static final int SUBID_AZ_STAT_REQUEST			= 6;

  public static final String ID_AZ_STAT_REPLY	    	= "AZ_STAT_REP";
  public static final byte[] ID_AZ_STAT_REPLY_BYTES 	= ID_AZ_STAT_REPLY.getBytes();
  public static final int SUBID_AZ_STAT_REPLY			= 7;

  public static final String ID_AZ_METADATA	    		= "AZ_METADATA";
  public static final byte[] ID_AZ_METADATA_BYTES 		= ID_AZ_METADATA.getBytes();
  public static final int SUBID_AZ_METADATA				= 8;


  //TODO
  /*
  public static final String ID_AZ_SESSION_SYN      	= "AZ_SESSION_SYN";
  public static final byte[] ID_AZ_SESSION_SYN_BYTES    = ID_AZ_SESSION_SYN.getBytes();

  public static final String ID_AZ_SESSION_ACK      	= "AZ_SESSION_ACK";
  public static final byte[] ID_AZ_SESSION_ACK_BYTES    = ID_AZ_SESSION_ACK.getBytes();

  public static final String ID_AZ_SESSION_END      	= "AZ_SESSION_END";
  public static final byte[] ID_AZ_SESSION_END_BYTES    = ID_AZ_SESSION_END.getBytes();

  public static final String ID_AZ_SESSION_BITFIELD 		= "AZ_SESSION_BITFIELD";
  public static final byte[] ID_AZ_SESSION_BITFIELD_BYTES 	= ID_AZ_SESSION_BITFIELD.getBytes();

  public static final String ID_AZ_SESSION_CANCEL   	= "AZ_SESSION_CANCEL";
  public static final byte[] ID_AZ_SESSION_CANCEL_BYTES = ID_AZ_SESSION_CANCEL.getBytes();

  public static final String ID_AZ_SESSION_HAVE     	= "AZ_SESSION_HAVE";
  public static final byte[] ID_AZ_SESSION_HAVE_BYTES   = ID_AZ_SESSION_HAVE.getBytes();

  public static final String ID_AZ_SESSION_PIECE    	= "AZ_SESSION_PIECE";
  public static final byte[] ID_AZ_SESSION_PIECE_BYTES  = ID_AZ_SESSION_PIECE.getBytes();

  public static final String ID_AZ_SESSION_REQUEST  		= "AZ_SESSION_REQUEST";
  public static final byte[] ID_AZ_SESSION_REQUEST_BYTES  	= ID_AZ_SESSION_REQUEST.getBytes();
  */
}
