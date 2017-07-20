/*
 * File    : PRUDPPacketReplyConnect.java
 * Created : 20-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.dht.transport.udp.impl;

/**
 * @author parg
 *
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.DHTTransportException;
import com.biglybt.core.dht.transport.DHTTransportValue;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;

public class
DHTUDPPacketReplyFindValue
	extends DHTUDPPacketReply
{
	public static final int DHT_FIND_VALUE_HEADER_SIZE 		= DHTUDPPacketReply.DHT_HEADER_SIZE + 1 + 1 + 2;

	public static final int DHT_FIND_VALUE_TV_HEADER_SIZE	= DHTUDPUtils.DHTTRANSPORTVALUE_SIZE_WITHOUT_VALUE;

	private DHTTransportContact[]		contacts;
	private DHTTransportValue[]			values;

	private boolean						has_continuation;
	private byte						diversification_type	= DHT.DT_NONE;

	public
	DHTUDPPacketReplyFindValue(
		DHTTransportUDPImpl				transport,
		DHTUDPPacketRequestFindValue	request,
		DHTTransportContact				local_contact,
		DHTTransportContact				remote_contact )
	{
		super( transport, DHTUDPPacketHelper.ACT_REPLY_FIND_VALUE, request, local_contact, remote_contact );
	}

	protected
	DHTUDPPacketReplyFindValue(
		DHTUDPPacketNetworkHandler		network_handler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								trans_id )

		throws IOException
	{
		super( network_handler, originator, is, DHTUDPPacketHelper.ACT_REPLY_FIND_VALUE, trans_id );

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_DIV_AND_CONT ){

			has_continuation	= is.readBoolean();
		}

		boolean	is_value = is.readBoolean();

		if ( is_value ){

			if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_DIV_AND_CONT ){

				diversification_type	= is.readByte();
			}

			values = DHTUDPUtils.deserialiseTransportValues( this, is, 0 );

		}else{

			contacts = DHTUDPUtils.deserialiseContacts( getTransport(), is );

			if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_VIVALDI_FINDVALUE ){

				DHTUDPUtils.deserialiseVivaldi( this, is );
			}
		}
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_DIV_AND_CONT ){

			os.writeBoolean( has_continuation );
		}

		os.writeBoolean( values != null );

		if ( values == null ){

			DHTUDPUtils.serialiseContacts( os, contacts );

			if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_VIVALDI_FINDVALUE ){

				DHTUDPUtils.serialiseVivaldi( this, os );
			}
		}else{

			if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_DIV_AND_CONT ){

				os.writeByte( diversification_type );
			}

			// values returned to a caller are adjusted by - skew

			try{
				DHTUDPUtils.serialiseTransportValues( this, os, values, -getClockSkew());

			}catch( DHTTransportException e ){

				throw( new IOException( e.getMessage()));
			}
		}
	}

	@Override
	public boolean
	hasContinuation()
	{
		return( has_continuation );
	}

	protected void
	setValues(
		DHTTransportValue[]	_values,
		byte				_diversification_type,
		boolean				_has_continuation )
	{
		has_continuation		= _has_continuation;
		diversification_type	= _diversification_type;
		values					= _values;
	}

	protected DHTTransportValue[]
	getValues()
	{
		return( values );
	}

	protected byte
	getDiversificationType()
	{
		return( diversification_type );
	}

	protected void
	setContacts(
		DHTTransportContact[]	_contacts )
	{
		contacts	= _contacts;
	}

	protected DHTTransportContact[]
	getContacts()
	{
		return( contacts );
	}

	@Override
	public String
	getString()
	{
		return( super.getString() + ",contacts=" + (contacts==null?"null":(""+contacts.length )));
	}
}
