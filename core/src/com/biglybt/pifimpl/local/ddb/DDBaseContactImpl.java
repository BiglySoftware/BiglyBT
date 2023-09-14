/*
 * Created on 22-Feb-2005
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

package com.biglybt.pifimpl.local.ddb;

import java.net.InetSocketAddress;
import java.util.Map;

import com.biglybt.core.dht.DHT;
import com.biglybt.pif.ddb.*;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.plugin.dht.DHTPluginContact;
import com.biglybt.plugin.dht.DHTPluginOperationListener;
import com.biglybt.plugin.dht.DHTPluginValue;


/**
 * @author parg
 *
 */

public class
DDBaseContactImpl
	implements DistributedDatabaseContact
{
	private DDBaseImpl				ddb;
	private DHTPluginContact		contact;

	protected
	DDBaseContactImpl(
		DDBaseImpl				_ddb,
		DHTPluginContact		_contact )
	{
		ddb			= _ddb;
		contact		= _contact;
	}

	@Override
	public byte[]
	getID()
	{
		return( contact.getID());
	}

	@Override
	public String
	getName()
	{
		return( contact.getName());
	}

	@Override
	public int
	getVersion()
	{
		return( contact.getProtocolVersion());
	}

	@Override
	public InetSocketAddress
	getAddress()
	{
		return( contact.getAddress());
	}

	@Override
	public int
	getNetwork()
	{
		return( contact.getNetwork());
	}

	@Override
	public boolean
	isAlive(
		long		timeout )
	{
		return( contact.isAlive( timeout ));
	}

	@Override
	public void
	isAlive(
		long								timeout,
		final DistributedDatabaseListener	listener )
	{

		contact.isAlive(
			timeout,
			new DHTPluginOperationListener()
			{
				@Override
				public void
				starts(
					byte[]				key )
				{
				}

				@Override
				public boolean
				diversified()
				{
					return( true );
				}

				@Override
				public void
				valueRead(
					DHTPluginContact	originator,
					DHTPluginValue		value )
				{
				}

				@Override
				public void
				valueWritten(
					DHTPluginContact	target,
					DHTPluginValue		value )
				{
				}

				@Override
				public void
				complete(
					byte[]					key,
					final boolean			timeout_occurred )
				{
					listener.event(
						new DistributedDatabaseEvent()
						{
							@Override
							public int
							getType()
							{
								return( timeout_occurred?ET_OPERATION_TIMEOUT:ET_OPERATION_COMPLETE );
							}

							@Override
							public DistributedDatabaseKey
							getKey()
							{
								return( null );
							}

							@Override
							public DistributedDatabaseKeyStats
							getKeyStats()
							{
								return( null );
							}

							@Override
							public DistributedDatabaseValue
							getValue()
							{
								return( null );
							}

							@Override
							public DistributedDatabaseContact
							getContact()
							{
								return( DDBaseContactImpl.this );
							}
						});
				}
			});
	}

	@Override
	public boolean
	isOrHasBeenLocal()
	{
		return( contact.isOrHasBeenLocal());
	}

	@Override
	public Map<String, Object>
	exportToMap()
	{
		return( contact.exportToMap());
	}

	@Override
	public boolean
	openTunnel()
	{
		return( contact.openTunnel() != null );
	}

	@Override
	public DistributedDatabaseValue
	call(
		DistributedDatabaseProgressListener 	listener,
		DistributedDatabaseTransferType 		type,
		DistributedDatabaseValue 				data,
		long									timeout )

		throws DistributedDatabaseException
	{
		return( ddb.call( this, listener, type, data, timeout ));
	}

	@Override
	public void
	write(
		DistributedDatabaseProgressListener		listener,
		DistributedDatabaseTransferType			type,
		DistributedDatabaseKey					key,
		DistributedDatabaseValue				value,
		long									timeout )

		throws DistributedDatabaseException
	{
		ddb.write( this, listener, type, key, value, timeout );
	}

	@Override
	public DistributedDatabaseValue
	read(
		DistributedDatabaseProgressListener			listener,
		DistributedDatabaseTransferType				type,
		DistributedDatabaseKey						key,
		long										timeout )

		throws DistributedDatabaseException
	{
		return( ddb.read( this, listener, type, key, timeout ));
	}

	protected DHTPluginContact
	getContact()
	{
		return( contact );
	}
}
