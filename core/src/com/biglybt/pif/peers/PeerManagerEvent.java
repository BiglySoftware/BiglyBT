/*
 * Created on Dec 22, 2008
 * Created by Paul Gardner
 *
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


package com.biglybt.pif.peers;

public interface
PeerManagerEvent
{
	/** Peer Added: Use getPeer */
	public static final int ET_PEER_ADDED			= 1;		// getPeer
	/** Peer Removed: Use getPeer */
	public static final int ET_PEER_REMOVED			= 2;		// getPeer
	/** Peer Discovered: getPeerDescriptor for discovery information; getPeer() returns peer that discovered it. */
	public static final int ET_PEER_DISCOVERED		= 3;		// getPeerDescriptor; opt getPeer if discovered from a Peer
	/** Peer Sent Bad Data: {@link #getPeer()}; {@link #getData()} to retrieve {@link Integer} piece number */
	public static final int ET_PEER_SENT_BAD_DATA	= 4;		// getPeer; getData -> Integer piece number

	/** Piece Activated: Use {@link #getPeer()}; {@link #getData()} returns Piece Object */
	public static final int ET_PIECE_ACTIVATED			= 5;		// opt getPeer; getData -> Piece object
	/** Piece Deactivated: Use {@link #getPeer()}; {@link #getData()} returns Piece Object */
	public static final int ET_PIECE_DEACTIVATED		= 6;		// getData -> Piece object
	/** Piece Completion Changed: Use {@link #getPeer()}; {@link #getData()} returns Piece Object */
	public static final int ET_PIECE_COMPLETION_CHANGED	= 7;		// getData -> Piece object

	public PeerManager
	getPeerManager();

	public int
	getType();

	public Peer
	getPeer();

	public PeerDescriptor
	getPeerDescriptor();

	public Object
	getData();
}
