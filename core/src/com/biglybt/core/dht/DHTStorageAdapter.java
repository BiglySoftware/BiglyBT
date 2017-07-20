/*
 * Created on 12-Mar-2005
 * Created by Paul Gardner
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

package com.biglybt.core.dht;

import java.io.DataInputStream;
import java.io.IOException;

import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.DHTTransportValue;
import com.biglybt.core.util.HashWrapper;

/**
 * @author parg
 *
 */

public interface
DHTStorageAdapter
{
	public int
	getNetwork();

		// local value operations

		/**
		 * Create a new storage key for a given key
		 * @return null if the key shouldn't be allocated (e.g.out of space)
		 */

	public DHTStorageKey
	keyCreated(
		HashWrapper		key,
		boolean			local );

	public void
	keyDeleted(
		DHTStorageKey	adapter_key );

	public void
	keyRead(
		DHTStorageKey			adapter_key,
		DHTTransportContact		contact );

	public DHTStorageKeyStats
	deserialiseStats(
		DataInputStream			is )

		throws IOException;

	public void
	valueAdded(
		DHTStorageKey		key,
		DHTTransportValue	value );

	public void
	valueUpdated(
		DHTStorageKey		key,
		DHTTransportValue	old_value,
		DHTTransportValue	new_value );

	public void
	valueDeleted(
		DHTStorageKey		key,
		DHTTransportValue	value );

		// local lookup/put operations

	public boolean
	isDiversified(
		byte[]		key );

	public byte[][]
	getExistingDiversification(
		byte[]			key,
		boolean			put_operation,
		boolean			exhaustive_get,
		int				max_depth );

	public byte[][]
	createNewDiversification(
		String				description,
		DHTTransportContact	cause,
		byte[]				key,
		boolean				put_operation,
		byte				diversification_type,
		boolean				exhaustive_get,
		int					max_depth );

	public int
	getNextValueVersions(
		int		num );

	public DHTStorageBlock
	keyBlockRequest(
		DHTTransportContact		direct_sender,
		byte[]					request,
		byte[]					signature );

	public DHTStorageBlock
	getKeyBlockDetails(
		byte[]		key );

	public DHTStorageBlock[]
	getDirectKeyBlocks();

	public byte[]
	getKeyForKeyBlock(
		byte[]	request );

	public void
	setStorageForKey(
		String	key,
		byte[]	data );

	public byte[]
	getStorageForKey(
		String	key );

	public int
	getRemoteFreqDivCount();

	public int
	getRemoteSizeDivCount();

	public int
	getKeyCount();
}
