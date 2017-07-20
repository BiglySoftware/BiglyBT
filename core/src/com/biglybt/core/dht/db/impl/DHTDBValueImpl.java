/*
 * Created on 18-Jan-2005
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

package com.biglybt.core.dht.db.impl;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.db.DHTDBValue;
import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.DHTTransportValue;
import com.biglybt.core.util.SystemTime;

/**
 * @author parg
 *
 */

public class
DHTDBValueImpl
	implements DHTDBValue
{
	private static final byte[] ZERO_LENGTH_BYTE_ARRAY = {};

	private long				creation_time;
	private byte[]				value;
	private DHTTransportContact	originator;
	private DHTTransportContact	sender;
	private final boolean				local;
	private byte				flags;
	private final byte				life_hours;
	private final byte				rep_control;
	private int					version;

	private long				store_time;

		/**
		 * constructor for the originator of values only
		 * @param _creation_time
		 * @param _value
		 * @param _originator
		 * @param _sender
		 * @param _distance
		 * @param _flags
		 */

	protected
	DHTDBValueImpl(
		long				_creation_time,
		byte[]				_value,
		int					_version,
		DHTTransportContact	_originator,
		DHTTransportContact	_sender,
		boolean				_local,
		int					_flags,
		int					_life_hours,
		byte				_rep_control )
	{
		creation_time	= _creation_time;
		value			= _value;
		version			= _version;
		originator		= _originator;
		sender			= _sender;
		local			= _local;
		flags			= (byte)_flags;
		life_hours		= (byte)_life_hours;
		rep_control		= _rep_control;

			// we get quite a few zero length values - optimise mem usage

		if ( value != null && value.length == 0 ){

			value = ZERO_LENGTH_BYTE_ARRAY;
		}

		reset();
	}

		/**
		 * Constructor used to generate values for relaying to other contacts
		 * or receiving a value from another contact - adjusts the sender
		 * Originator, creation time, flags and value are fixed.
		 * @param _sender
		 * @param _other
		 */

	protected
	DHTDBValueImpl(
		DHTTransportContact	_sender,
		DHTTransportValue	_other,
		boolean				_local )
	{
		this( 	_other.getCreationTime(),
				_other.getValue(),
				_other.getVersion(),
				_other.getOriginator(),
				_sender,
				_local,
				_other.getFlags(),
				_other.getLifeTimeHours(),
				_other.getReplicationControl());
	}

	protected void
	reset()
	{
		store_time	= SystemTime.getCurrentTime();

			// make sure someone hasn't sent us a stupid creation time

		if ( creation_time > store_time ){

			creation_time	= store_time;
		}
	}

	@Override
	public long
	getCreationTime()
	{
		return( creation_time );
	}

	protected void
	setCreationTime()
	{
		creation_time = SystemTime.getCurrentTime();
	}

	protected void
	setStoreTime(
		long	l )
	{
		store_time	= l;
	}

	protected long
	getStoreTime()
	{
		return( store_time );
	}

	@Override
	public boolean
	isLocal()
	{
		return( local );
	}

	@Override
	public byte[]
	getValue()
	{
		return( value );
	}

	@Override
	public int
	getVersion()
	{
		return( version );
	}

	@Override
	public DHTTransportContact
	getOriginator()
	{
		return( originator);
	}

	public DHTTransportContact
	getSender()
	{
		return( sender );
	}

	@Override
	public int
	getFlags()
	{
		return( flags&0xff );
	}

	@Override
	public void
	setFlags(
		byte	_flags )
	{
		flags = _flags;
	}

	@Override
	public int
	getLifeTimeHours()
	{
		return( life_hours&0xff );
	}

	@Override
	public byte
	getReplicationControl()
	{
		return( rep_control );
	}

	@Override
	public byte
	getReplicationFactor()
	{
		return( rep_control == DHT.REP_FACT_DEFAULT?DHT.REP_FACT_DEFAULT:(byte)(rep_control&0x0f));
	}

	@Override
	public byte
	getReplicationFrequencyHours()
	{
		return( rep_control == DHT.REP_FACT_DEFAULT?DHT.REP_FACT_DEFAULT:(byte)((rep_control&0xf0)>>4));
	}

	protected void
	setOriginatorAndSender(
		DHTTransportContact	_originator )
	{
		originator	= _originator;
		sender		= _originator;
	}

	@Override
	public DHTDBValue
	getValueForRelay(
		DHTTransportContact	_sender )
	{
		return( new DHTDBValueImpl( _sender, this, local ));
	}

	public DHTDBValue
	getValueForDeletion(
		int		_version )
	{
		DHTDBValueImpl	res = new DHTDBValueImpl( originator, this, local );

		res.value = ZERO_LENGTH_BYTE_ARRAY;	// delete -> 0 length value

		res.setCreationTime();

		res.version = _version;

		return( res );
	}

	@Override
	public String
	getString()
	{
		long	now = SystemTime.getCurrentTime();

		return( DHTLog.getString( value ) + " - " + new String(value) + "{v=" + version + ",f=" +
				Integer.toHexString(flags) + ",l=" + life_hours + ",r=" + Integer.toHexString( rep_control ) + ",ca=" + (now - creation_time ) + ",sa=" + (now-store_time)+
				",se=" + sender.getString() + ",or=" + originator.getString() +"}" );
	}
}
