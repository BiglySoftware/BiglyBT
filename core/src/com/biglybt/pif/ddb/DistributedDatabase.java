/*
 * Created on 18-Feb-2005
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

package com.biglybt.pif.ddb;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import com.biglybt.plugin.dht.DHTPluginInterface;

/**
 * @author parg
 *
 */

public interface
DistributedDatabase
{
	public static final int	OP_NONE				= 0x00000000;
	public static final int	OP_EXHAUSTIVE_READ	= 0x00000001;
	public static final int	OP_PRIORITY_HIGH	= 0x00000002;

		// diversification types

	public static final byte	DT_NONE			= 1;
	public static final byte	DT_FREQUENCY	= 2;
	public static final byte	DT_SIZE			= 3;

		// dht types



	public boolean
	isAvailable();

	public boolean
	isInitialized();

	public boolean
	isExtendedUseAllowed();

	public String
	getNetwork();

	public DHTPluginInterface
	getDHTPlugin();

	public DistributedDatabaseContact
	getLocalContact();

	public DistributedDatabaseKey
	createKey(
		Object			key )

		throws DistributedDatabaseException;

	public DistributedDatabaseKey
	createKey(
		Object			key,
		String			description )

		throws DistributedDatabaseException;


	public DistributedDatabaseValue
	createValue(
		Object			value )

		throws DistributedDatabaseException;

	public DistributedDatabaseContact
	importContact(
		InetSocketAddress				address )

		throws DistributedDatabaseException;

	public DistributedDatabaseContact
	importContact(
		InetSocketAddress				address,
		byte							protocol_version )

		throws DistributedDatabaseException;

	public DistributedDatabaseContact
	importContact(
		InetSocketAddress				address,
		byte							protocol_version,
		int								preferred_dht )

		throws DistributedDatabaseException;

	public DistributedDatabaseContact
	importContact(
		Map<String,Object>				map )

		throws DistributedDatabaseException;

	public void
	write(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		DistributedDatabaseValue		value )

		throws DistributedDatabaseException;

	public void
	write(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		DistributedDatabaseValue[]		values )

		throws DistributedDatabaseException;

	public void
	read(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		long							timeout )

		throws DistributedDatabaseException;

	public void
	read(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		long							timeout,
		int								options )

		throws DistributedDatabaseException;

	public void
	readKeyStats(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		long							timeout )

		throws DistributedDatabaseException;

		/**
		 * Get all locally held (direct+indirect) values for a key
		 * @param key
		 * @return
		 */

	public List<DistributedDatabaseValue>
	getValues(
		DistributedDatabaseKey			key )

		throws DistributedDatabaseException;

	public void
	delete(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key )

		throws DistributedDatabaseException;

	public void
	delete(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		DistributedDatabaseContact[]	targets )

		throws DistributedDatabaseException;

	public void
	addTransferHandler(
		DistributedDatabaseTransferType		type,
		DistributedDatabaseTransferHandler	handler )

		throws DistributedDatabaseException;

	public DistributedDatabaseTransferType
	getStandardTransferType(
		int		standard_type )

		throws DistributedDatabaseException;

	public void
	addListener(
		DistributedDatabaseListener		l );

	public void
	removeListener(
		DistributedDatabaseListener		l );
}
