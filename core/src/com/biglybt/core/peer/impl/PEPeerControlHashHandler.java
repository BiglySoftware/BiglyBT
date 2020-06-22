/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.peer.impl;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.disk.DiskManagerCheckRequestListener.HashListener;

public interface 
PEPeerControlHashHandler
{
	public void
	sendingRequest(
		PEPeerTransport				peer,
		DiskManagerReadRequest		request );
	
	public boolean 
	hashRequest(
		int 			piece_number, 
		HashListener 	listener);
	
	public void
	receivedHashes(
		PEPeerTransport		peer,
		byte[]				root_hash,
		int					base_layer,
		int					index,
		int					length,
		int					proof_layers,
		byte[][]			hashes );
	
	public void
	receivedHashRequest(
		PEPeerTransport		peer,
		HashesReceiver		receiver,
		byte[]				root_hash,
		int					base_layer,
		int					index,
		int					length,
		int					proof_layers );
	
	public void
	rejectedHashes(
		PEPeerTransport		peer,
		byte[]				root_hash,
		int					base_layer,
		int					index,
		int					length,
		int					proof_layers );	
	
	public void
	update();
	
	public void
	stop();
	
	public interface
	HashesReceiver
	{
		public void
		receiveResult(
			byte[][]	hashes );
	}
}
