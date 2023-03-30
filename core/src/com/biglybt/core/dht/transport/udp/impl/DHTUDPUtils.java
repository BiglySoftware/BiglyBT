/*
 * Created on 21-Jan-2005
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

package com.biglybt.core.dht.transport.udp.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionManager;
import com.biglybt.core.dht.netcoords.vivaldi.ver1.VivaldiPositionFactory;
import com.biglybt.core.dht.transport.*;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.global.GlobalManagerStats.*;

import com.biglybt.core.util.*;

/**
 * @author parg
 *
 */

public class
DHTUDPUtils
{
	public static final IOException INVALID_PROTOCOL_VERSION_EXCEPTION = new IOException( "Invalid DHT protocol version, please update " + Constants.APP_NAME );

	protected static final int	CT_UDP		= 1;

	private static final Map<String,byte[]>	node_id_history =
		new LinkedHashMap<String,byte[]>(128,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<String,byte[]> eldest)
			{
				return size() > 128;
			}
		};

	private static final SHA1Simple	hasher = new SHA1Simple();

	protected static byte[]
	getNodeID(
		InetSocketAddress	address,
		byte				protocol_version )

		throws DHTTransportException
	{
		final String	key;

		InetAddress ia = address.getAddress();

		if ( ia == null ){

			if ( address.getHostName().equals( Constants.DHT_SEED_ADDRESS_V6 )){

				key = "IPv6SeedHack";

			}else{
				// Debug.out( "Address '" + address + "' is unresolved" );

				throw( new DHTTransportException( "Address '" + address + "' is unresolved" ));
			}
		}else{


			if ( protocol_version >= DHTTransportUDP.PROTOCOL_VERSION_RESTRICT_ID3 ){

				byte[] bytes = ia.getAddress();

				if ( bytes.length == 4 ){

					//final long K0	= Long.MAX_VALUE;
					//final long K1	= Long.MAX_VALUE;

						// restrictions suggested by UoW researchers as effective at reducing Sybil opportunity but
						// not so restrictive as to impact DHT performance

					final long K2	= 2500;
					final long K3	= 50;
					final long K4	= 5;

					long	result = address.getPort() % K4;

					result = ((((long)bytes[3] << 8 ) &0x000000ff00L ) | result ) % K3;
					result = ((((long)bytes[2] << 16 )&0x0000ff0000L ) | result ) % K2;
					result = ((((long)bytes[1] << 24 )&0x00ff000000L ) | result ); // % K1;
					result = ((((long)bytes[0] << 32 )&0xff00000000L ) | result ); // % K0;

					key = String.valueOf( result );

				}else{

						// stick with existing approach for IPv6 at the moment

					key = ia.getHostAddress() + ":" + ( address.getPort() % 8 );
				}
			}else if ( protocol_version >= DHTTransportUDP.PROTOCOL_VERSION_RESTRICT_ID_PORTS2 ){

					// more draconian limit, analysis shows that of 500,000 node addresses only
					// 0.01% had >= 8 ports active. ( 1% had 2 ports, 0.1% 3)
					// Having > 1 node with the same ID doesn't actually cause too much grief

				key = ia.getHostAddress() + ":" + ( address.getPort() % 8 );

			}else if ( protocol_version >= DHTTransportUDP.PROTOCOL_VERSION_RESTRICT_ID_PORTS ){

					// limit range to around 2000 (1999 is prime)

				key = ia.getHostAddress() + ":" + ( address.getPort() % 1999 );

			}else{

				key = ia.getHostAddress() + ":" + address.getPort();
			}
		}

		synchronized( node_id_history ){

			byte[]	res = node_id_history.get( key );

			if ( res == null ){

				res = hasher.calculateHash( key.getBytes());

				node_id_history.put( key, res );
			}

			// System.out.println( "NodeID: " + address + " -> " + DHTLog.getString( res ));

			return( res );
		}
	}

	protected static byte[]
	getBogusNodeID()
	{
		byte[]	id = new byte[20];

		RandomUtils.nextBytes( id );

		return( id );
	}

	protected static void
	serialiseLength(
		DataOutputStream	os,
		int					len,
		int					max_length )

		throws IOException
	{
		if ( len > max_length ){

			throw( new IOException( "Invalid DHT data length: max=" + max_length + ",actual=" + len ));
		}

		if ( max_length < 256 ){

			os.writeByte( len );

		}else if ( max_length < 65536 ){

			os.writeShort( len );

		}else{

			os.writeInt( len );
		}
	}

	protected static int
	deserialiseLength(
		DataInputStream	is,
		int				max_length )

		throws IOException
	{
		int		len;

		if ( max_length < 256 ){

			len = is.readByte()&0xff;

		}else if ( max_length < 65536 ){

			len = is.readShort()&0xffff;

		}else{

			len = is.readInt();
		}

		if ( len > max_length ){

			throw( new IOException( "Invalid DHT data length: max=" + max_length + ",actual=" + len ));
		}

		return( len );
	}

	protected static byte[]
	deserialiseByteArray(
		DataInputStream	is,
		int				max_length )

		throws IOException
	{
		int	len = deserialiseLength( is, max_length );

		byte[] data	= new byte[len];

		is.read(data);

		return( data );
	}

	protected static void
	serialiseByteArray(
		DataOutputStream	os,
		byte[]				data,
		int					max_length )

		throws IOException
	{
		serialiseByteArray( os, data, 0, data.length, max_length );
	}

	protected static void
	serialiseByteArray(
		DataOutputStream	os,
		byte[]				data,
		int					start,
		int					length,
		int					max_length )

		throws IOException
	{
		serialiseLength( os, length, max_length );

		os.write( data, start, length );
	}

	protected static void
	serialiseByteArrayArray(
		DataOutputStream		os,
		byte[][]				data,
		int						max_length )

		throws IOException
	{
		serialiseLength(os,data.length,max_length);

		for (int i=0;i<data.length;i++){

			serialiseByteArray( os, data[i], max_length );
		}
	}

	protected static byte[][]
	deserialiseByteArrayArray(
		DataInputStream	is,
		int				max_length )

		throws IOException
	{
		int	len = deserialiseLength( is, max_length );

		byte[][] data	= new byte[len][];

		for (int i=0;i<data.length;i++){

			data[i] = deserialiseByteArray( is, max_length );
		}

		return( data );
	}

	public static final int INETSOCKETADDRESS_IPV4_SIZE	= 7;
	public static final int INETSOCKETADDRESS_IPV6_SIZE	= 19;

	protected static void
	serialiseAddress(
		DataOutputStream	os,
		InetSocketAddress	address )

		throws IOException, DHTTransportException
	{
		InetAddress	ia = address.getAddress();

		if ( ia == null ){

				// could be an unresolved dht6 seed, stick in a fake value as we are committed to serialising
				// something here

			serialiseByteArray( os, new byte[4], 16 );

			os.writeShort( 0 );

		}else{

			serialiseByteArray( os, ia.getAddress(), 16);	// 16 (Pv6) + 1 length

			os.writeShort( address.getPort());	//19
		}
	}

	protected static InetSocketAddress
	deserialiseAddress(
		DataInputStream		is )

		throws IOException
	{
		byte[]	bytes = deserialiseByteArray( is, 16 );

		int	port = is.readShort()&0xffff;

		return( new InetSocketAddress( InetAddress.getByAddress( bytes ), port ));
	}

	protected static DHTTransportValue[][]
	deserialiseTransportValuesArray(
		DHTUDPPacket			packet,
		DataInputStream			is,
		long					skew,
		int						max_length )

		throws IOException
	{
		int	len = deserialiseLength( is, max_length );

		DHTTransportValue[][] data	= new DHTTransportValue[len][];

		for (int i=0;i<data.length;i++){

			data[i] = deserialiseTransportValues( packet, is, skew );
		}

		return( data );
	}

	protected static void
	serialiseTransportValuesArray(
		DHTUDPPacket			packet,
		DataOutputStream		os,
		DHTTransportValue[][]	values,
		long					skew,
		int						max_length )

		throws IOException, DHTTransportException
	{
		serialiseLength(os,values.length,max_length);

		for (int i=0;i<values.length;i++){

			serialiseTransportValues( packet, os, values[i], skew );
		}
	}

	public static final int	DHTTRANSPORTCONTACT_SIZE	= 2 + INETSOCKETADDRESS_IPV4_SIZE;

	protected static void
	serialiseContact(
		DataOutputStream		os,
		DHTTransportContact		contact )

		throws IOException, DHTTransportException
	{
		if ( contact.getTransport() instanceof DHTTransportUDP ){

			os.writeByte( CT_UDP );		// 1

			os.writeByte( contact.getProtocolVersion());	// 2

			serialiseAddress( os, contact.getExternalAddress() );	// 2 + address

		}else{

			throw( new IOException( "Unsupported contact type:" + contact.getClass().getName()));
		}
	}

	protected static DHTTransportUDPContactImpl
	deserialiseContact(
		DHTTransportUDPImpl		transport,
		DataInputStream			is )

		throws IOException, DHTTransportException
	{
		byte	ct = is.readByte();

		if ( ct != CT_UDP ){

			throw( new IOException( "Unsupported contact type:" + ct ));
		}

		byte	version = is.readByte();

			// we don't transport instance ids around via this route as they are just
			// cached versions and not useful

		InetSocketAddress	external_address = deserialiseAddress( is );

		return( new DHTTransportUDPContactImpl( false, transport, external_address, external_address, version, 0, 0, (byte)0 ));
	}

	protected static void
	serialiseAltContact(
		DataOutputStream					os,
		DHTTransportAlternativeContact		contact )

		throws IOException, DHTTransportException
	{
		os.write((byte)contact.getNetworkType());

		os.write((byte)contact.getVersion());

		os.writeShort( contact.getAge());

		byte[] encoded = BEncoder.encode( contact.getProperties());

		serialiseByteArray( os, encoded, 65535 );
	}

	protected static DHTTransportAlternativeContactImpl
	deserialiseAltContact(
		DataInputStream			is )

		throws IOException, DHTTransportException
	{
		byte	network_type 	= is.readByte();
		byte	version		 	= is.readByte();
		short	age				= is.readShort();

		byte[]	encoded = deserialiseByteArray( is, 65535 );

		return( new DHTTransportAlternativeContactImpl( network_type, version, age, encoded ));
	}

	protected static DHTTransportValue[]
	deserialiseTransportValues(
		DHTUDPPacket			packet,
		DataInputStream			is,
		long					skew )

		throws IOException
	{
		int	len = deserialiseLength( is, 65535 );

		List	l = new ArrayList( len );

		for (int i=0;i<len;i++){

			try{

				l.add( deserialiseTransportValue( packet, is, skew ));

			}catch( DHTTransportException e ){

				Debug.printStackTrace(e);
			}
		}

		DHTTransportValue[]	res = new DHTTransportValue[l.size()];

		l.toArray( res );

		return( res );
	}

	protected static void
	serialiseTransportValues(
		DHTUDPPacket			packet,
		DataOutputStream		os,
		DHTTransportValue[]		values,
		long					skew )

		throws IOException, DHTTransportException
	{
		serialiseLength( os, values.length, 65535 );

		for (int i=0;i<values.length;i++){

			serialiseTransportValue( packet, os, values[i], skew );
		}
	}

	protected static DHTTransportValue
	deserialiseTransportValue(
		DHTUDPPacket		packet,
		DataInputStream		is,
		long				skew )

		throws IOException, DHTTransportException
	{
		final int	version;

		if ( packet.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_REMOVE_DIST_ADD_VER ){

			version = is.readInt();

			//System.out.println( "read: version = " + version );

		}else{

			version	= -1;

			// int distance = is.readInt();

			//System.out.println( "read:" + distance );
		}

		final long 	created		= is.readLong() + skew;

		// System.out.println( "    Adjusted creation time by " + skew );

		final byte[]	value_bytes = deserialiseByteArray( is, DHT.MAX_VALUE_SIZE );

		final DHTTransportContact	originator		= deserialiseContact( packet.getTransport(), is );

		final int flags	= is.readByte()&0xff;

		final int life_hours;

		if ( packet.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_LONGER_LIFE ){

			life_hours = is.readByte()&0xff;

		}else{

			life_hours = 0;
		}

		final byte rep_control;

		if ( packet.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL ){

			rep_control = is.readByte();

		}else{

			rep_control = DHT.REP_FACT_DEFAULT;
		}

		DHTTransportValue value =
			new DHTTransportValue()
			{
				@Override
				public boolean
				isLocal()
				{
					return( false );
				}

				@Override
				public long
				getCreationTime()
				{
					return( created );
				}

				@Override
				public byte[]
				getValue()
				{
					return( value_bytes );
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
					return( originator );
				}

				@Override
				public int
				getFlags()
				{
					return( flags );
				}

				@Override
				public int
				getLifeTimeHours()
				{
					return( life_hours );
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
					return( rep_control == DHT.REP_FACT_DEFAULT?DHT.REP_FACT_DEFAULT:(byte)(rep_control>>4));
				}

				@Override
				public String
				getString()
				{
					long	now = SystemTime.getCurrentTime();

					return( DHTLog.getString( value_bytes ) + " - " + new String(value_bytes) + "{v=" + version + ",f=" +
							Integer.toHexString(flags) + ",l=" + life_hours + ",r=" + Integer.toHexString(getReplicationControl()) + ",ca=" + (now - created ) + ",or=" + originator.getString() +"}" );
				}
			};

		return( value );
	}

	public static final int DHTTRANSPORTVALUE_SIZE_WITHOUT_VALUE	= 17 + DHTTRANSPORTCONTACT_SIZE;

	protected static void
	serialiseTransportValue(
		DHTUDPPacket		packet,
		DataOutputStream	os,
		DHTTransportValue	value,
		long				skew )

		throws IOException, DHTTransportException
	{
		if ( packet.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_REMOVE_DIST_ADD_VER ){

			int	version = value.getVersion();

			//System.out.println( "write: version = " + version );

			os.writeInt( version );
		}else{

			//System.out.println( "write: 0" );

			os.writeInt( 0 );
		}

			// Don't forget to change the CONSTANT above if you change the size of this!

		os.writeLong( value.getCreationTime() + skew );	// 12

		serialiseByteArray( os, value.getValue(), DHT.MAX_VALUE_SIZE );	// 12+2+X

		serialiseContact( os, value.getOriginator());	// 12 + 2+X + contact

		os.writeByte( value.getFlags());	// 13 + 2+ X + contact

		if ( packet.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_LONGER_LIFE ){

			os.writeByte( value.getLifeTimeHours()); // 14 + 2+ X + contact
		}

		if ( packet.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL ){

			os.writeByte( value.getReplicationControl()); // 15 + 2+ X + contact
		}
	}

	protected static void
	serialiseContacts(
		DataOutputStream		os,
		DHTTransportContact[]	contacts )

		throws IOException
	{
		serialiseLength( os, contacts.length, 65535 );

		for (int i=0;i<contacts.length;i++){

			try{
				serialiseContact( os, contacts[i] );

			}catch( DHTTransportException e ){

				Debug.printStackTrace(e);

					// not much we can do here to recover - shouldn't fail anyways

				throw( new IOException(e.getMessage()));
			}
		}
	}

	protected static DHTTransportContact[]
	deserialiseContacts(
		DHTTransportUDPImpl		transport,
		DataInputStream			is )

		throws IOException
	{
		int	len = deserialiseLength( is, 65535 );

		List<DHTTransportContact>	l = new ArrayList<>(len);

		for (int i=0;i<len;i++){

			try{

				DHTTransportContact contact = deserialiseContact( transport, is );

				if ( contact.getAddress().getPort() > 0 ){

					l.add( contact );
				}

			}catch( DHTTransportException e ){

				Debug.printStackTrace(e);
			}
		}

		DHTTransportContact[]	res = new DHTTransportContact[l.size()];

		l.toArray( res );

		return( res );
	}

	protected static void
	serialiseAltContacts(
		DataOutputStream					os,
		DHTTransportAlternativeContact[]	contacts )

		throws IOException
	{
		if ( contacts == null ){

			contacts = new DHTTransportAlternativeContact[0];
		}

		serialiseLength( os, contacts.length, 64 );

		for (int i=0;i<contacts.length;i++){

			try{
				serialiseAltContact( os, contacts[i] );

			}catch( DHTTransportException e ){

				Debug.printStackTrace(e);

					// not much we can do here to recover - shouldn't fail anyways

				throw( new IOException(e.getMessage()));
			}
		}
	}

	protected static DHTTransportAlternativeContact[]
	deserialiseAltContacts(
		DataInputStream			is )

		throws IOException
	{
		int	len = deserialiseLength( is, 64 );

		List<DHTTransportAlternativeContact>	l = new ArrayList<>(len);

		for (int i=0;i<len;i++){

			try{

				DHTTransportAlternativeContact contact = deserialiseAltContact( is );

				l.add( contact );

			}catch( DHTTransportException e ){

				Debug.printStackTrace(e);
			}
		}

		DHTTransportAlternativeContact[]	res = new DHTTransportAlternativeContact[l.size()];

		l.toArray( res );

		return( res );
	}

	protected static void
	serialiseAltContactRequest(
		DHTUDPPacketRequestPing		ping,
		DataOutputStream			os )

		throws IOException
	{
		int[]	nets 	= ping.getAltNetworks();
		int[]	counts	= ping.getAltNetworkCounts();

		int len = nets==null||counts==null?0:nets.length;

		serialiseLength( os, len, 16 );

		for ( int i=0;i<len;i++ ){

			os.write( nets[i] );
			os.write( counts[i] );
		}
	}

	protected static void
	deserialiseAltContactRequest(
		DHTUDPPacketRequestPing		ping,
		DataInputStream				is )

		throws IOException
	{
		int	len = deserialiseLength( is, 16 );

		int[]	nets 	= new int[len];
		int[]	counts	= new int[len];

		for ( int i=0;i<len;i++){

			nets[i] 	= is.read();
			counts[i]	= is.read();

			if ( nets[i] == -1 || counts[i] == -1 ){

				throw( new EOFException());
			}
		}

		ping.setAltContactRequest( nets, counts );
	}

	protected static void
	serialiseVivaldi(
		DHTUDPPacketReply	reply,
		DataOutputStream	os )

		throws IOException
	{
		DHTNetworkPosition[]	nps = reply.getNetworkPositions();

		if ( reply.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_GENERIC_NETPOS ){

			boolean	v1_found = false;

			for (int i=0;i<nps.length;i++){

				DHTNetworkPosition	np = nps[i];

				if ( np.getPositionType() == DHTNetworkPosition.POSITION_TYPE_VIVALDI_V1 ){

					v1_found	= true;

					break;
				}
			}

			if ( !v1_found ){

				if ( reply.getProtocolVersion() < DHTTransportUDP.PROTOCOL_VERSION_VIVALDI_OPTIONAL ){

						// need to add one in for backward compatability

					DHTNetworkPosition np = VivaldiPositionFactory.createPosition( Float.NaN );

					DHTNetworkPosition[] new_nps = new DHTNetworkPosition[nps.length+1];

					System.arraycopy( nps, 0, new_nps, 0, nps.length );

					new_nps[ nps.length ] = np;

					nps = new_nps;
				}
			}

			os.writeByte((byte)nps.length);

			for (int i=0;i<nps.length;i++){

				DHTNetworkPosition	np = nps[i];

				os.writeByte( np.getPositionType());
				os.writeByte( np.getSerialisedSize());

				np.serialise( os );
			}
		}else{

				// dead code these days

			for (int i=0;i<nps.length;i++){

				if ( nps[i].getPositionType() == DHTNetworkPosition.POSITION_TYPE_VIVALDI_V1 ){

					nps[i].serialise( os );

					return;
				}
			}

			Debug.out( "Vivaldi V1 missing" );

			throw( new IOException( "Vivaldi V1 missing" ));
		}
	}

	protected static void
	deserialiseVivaldi(
		DHTUDPPacketReply	reply,
		DataInputStream		is )

		throws IOException
	{
		DHTNetworkPosition[]	nps;

		if ( reply.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_GENERIC_NETPOS ){

			int	entries = is.readByte()&0xff;

			nps	= new DHTNetworkPosition[ entries ];

			int	skipped = 0;

			for (int i=0;i<entries;i++){

				byte	type = is.readByte();
				byte	size = is.readByte();

				DHTNetworkPosition	np = DHTNetworkPositionManager.deserialise( reply.getAddress().getAddress(), type, is );

				if ( np == null ){

					skipped++;

					for (int j=0;j<size;j++){

						is.readByte();
					}
				}else{

					nps[i] = np;
				}
			}

			if ( skipped > 0 ){

				DHTNetworkPosition[] x	= new DHTNetworkPosition[ entries - skipped ];

				int	pos = 0;

				for (int i=0;i<nps.length;i++){

					if ( nps[i] != null ){

						x[pos++] = nps[i];
					}
				}

				nps	= x;
			}
		}else{

				// dead code these days

			nps = new DHTNetworkPosition[]{ DHTNetworkPositionManager.deserialise( reply.getAddress().getAddress(), DHTNetworkPosition.POSITION_TYPE_VIVALDI_V1, is )};
		}

		reply.setNetworkPositions( nps );
	}

	protected static void
	serialiseStats(
		int						version,
		DataOutputStream		os,
		DHTTransportFullStats	stats )

		throws IOException
	{
		os.writeLong( stats.getDBValuesStored());

		os.writeLong( stats.getRouterNodes());
		os.writeLong( stats.getRouterLeaves());
		os.writeLong( stats.getRouterContacts());

		os.writeLong( stats.getTotalBytesReceived());
		os.writeLong( stats.getTotalBytesSent());
		os.writeLong( stats.getTotalPacketsReceived());
		os.writeLong( stats.getTotalPacketsSent());
		os.writeLong( stats.getTotalPingsReceived());
		os.writeLong( stats.getTotalFindNodesReceived());
		os.writeLong( stats.getTotalFindValuesReceived());
		os.writeLong( stats.getTotalStoresReceived());
		os.writeLong( stats.getAverageBytesReceived());
		os.writeLong( stats.getAverageBytesSent());
		os.writeLong( stats.getAveragePacketsReceived());
		os.writeLong( stats.getAveragePacketsSent());

		os.writeLong( stats.getIncomingRequests());

		String	azversion = stats.getVersion() + "["+version+"]";

		serialiseByteArray( os, azversion.getBytes(), 64);

		os.writeLong( stats.getRouterUptime());
		os.writeInt( stats.getRouterCount());

		if ( version >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS ){

			os.writeLong( stats.getDBKeysBlocked());
			os.writeLong( stats.getTotalKeyBlocksReceived());
		}

		if ( version >= DHTTransportUDP.PROTOCOL_VERSION_MORE_STATS ){

			os.writeLong( stats.getDBKeyCount());
			os.writeLong( stats.getDBValueCount());
			os.writeLong( stats.getDBStoreSize());
			os.writeLong( stats.getDBKeyDivFreqCount());
			os.writeLong( stats.getDBKeyDivSizeCount());
		}
	}

	protected static DHTTransportFullStats
	deserialiseStats(
		int					version,
		DataInputStream		is )

		throws IOException
	{
		final long db_values_stored				= is.readLong();

		final long router_nodes					= is.readLong();
		final long router_leaves				= is.readLong();
		final long router_contacts 				= is.readLong();

		final long total_bytes_received			= is.readLong();
		final long total_bytes_sent				= is.readLong();
		final long total_packets_received		= is.readLong();
		final long total_packets_sent			= is.readLong();
		final long total_pings_received			= is.readLong();
		final long total_find_nodes_received	= is.readLong();
		final long total_find_values_received	= is.readLong();
		final long total_stores_received		= is.readLong();
		final long average_bytes_received		= is.readLong();
		final long average_bytes_sent			= is.readLong();
		final long average_packets_received		= is.readLong();
		final long average_packets_sent			= is.readLong();

		final long incoming_requests			= is.readLong();

		final String	az_version = new String( deserialiseByteArray( is, 64 ));

		final long	router_uptime	= is.readLong();
		final int	router_count	= is.readInt();

		final long db_keys_blocked;
		final long total_key_blocks_received;

		if ( version >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS ){

			db_keys_blocked				= is.readLong();
			total_key_blocks_received	= is.readLong();
		}else{
			db_keys_blocked				= -1;
			total_key_blocks_received	= -1;
		}

		final long db_key_count;
		final long db_value_count;
		final long db_store_size;
		final long db_freq_divs;
		final long db_size_divs;

		if ( version >= DHTTransportUDP.PROTOCOL_VERSION_MORE_STATS ){

			db_key_count 	= is.readLong();
			db_value_count	= is.readLong();
			db_store_size	= is.readLong();
			db_freq_divs	= is.readLong();
			db_size_divs	= is.readLong();

		}else{

			db_key_count 	= -1;
			db_value_count	= -1;
			db_store_size	= -1;
			db_freq_divs	= -1;
			db_size_divs	= -1;
		}

		DHTTransportFullStats	res =
			new DHTTransportFullStats()
			{
				@Override
				public long
				getDBValuesStored()
				{
					return( db_values_stored );
				}

				@Override
				public long
				getDBKeysBlocked()
				{
					return( db_keys_blocked );
				}

				@Override
				public long
				getDBValueCount()
				{
					return( db_value_count );
				}

				@Override
				public long
				getDBKeyCount()
				{
					return( db_key_count );
				}

				@Override
				public long
				getDBKeyDivSizeCount()
				{
					return( db_size_divs );
				}

				@Override
				public long
				getDBKeyDivFreqCount()
				{
					return( db_freq_divs );
				}

				@Override
				public long
				getDBStoreSize()
				{
					return( db_store_size );
				}

					// Router

				@Override
				public long
				getRouterNodes()
				{
					return( router_nodes );
				}

				@Override
				public long
				getRouterLeaves()
				{
					return( router_leaves );
				}

				@Override
				public long
				getRouterContacts()
				{
					return( router_contacts );
				}

				@Override
				public long
				getRouterUptime()
				{
					return( router_uptime );
				}

				@Override
				public int
				getRouterCount()
				{
					return( router_count );
				}
				@Override
				public long
				getTotalBytesReceived()
				{
					return( total_bytes_received );
				}

				@Override
				public long
				getTotalBytesSent()
				{
					return( total_bytes_sent );
				}

				@Override
				public long
				getTotalPacketsReceived()
				{
					return( total_packets_received );
				}

				@Override
				public long
				getTotalPacketsSent()
				{
					return( total_packets_sent );
				}

				@Override
				public long
				getTotalPingsReceived()
				{
					return( total_pings_received );
				}

				@Override
				public long
				getTotalFindNodesReceived()
				{
					return( total_find_nodes_received );
				}

				@Override
				public long
				getTotalFindValuesReceived()
				{
					return( total_find_values_received );
				}

				@Override
				public long
				getTotalStoresReceived()
				{
					return( total_stores_received );
				}

				@Override
				public long
				getTotalKeyBlocksReceived()
				{
					return( total_key_blocks_received );
				}

					// averages

				@Override
				public long
				getAverageBytesReceived()
				{
					return( average_bytes_received );
				}

				@Override
				public long
				getAverageBytesSent()
				{
					return( average_bytes_sent );
				}

				@Override
				public long
				getAveragePacketsReceived()
				{
					return( average_packets_received );
				}

				@Override
				public long
				getAveragePacketsSent()
				{
					return( average_packets_sent );
				}

				@Override
				public long
				getIncomingRequests()
				{
					return( incoming_requests );
				}

				@Override
				public String
				getVersion()
				{
					return( az_version );
				}

				@Override
				public String
				getString()
				{
					return(	"transport:" +
							getTotalBytesReceived() + "," +
							getTotalBytesSent() + "," +
							getTotalPacketsReceived() + "," +
							getTotalPacketsSent() + "," +
							getTotalPingsReceived() + "," +
							getTotalFindNodesReceived() + "," +
							getTotalFindValuesReceived() + "," +
							getTotalStoresReceived() + "," +
							getTotalKeyBlocksReceived() + "," +
							getAverageBytesReceived() + "," +
							getAverageBytesSent() + "," +
							getAveragePacketsReceived() + "," +
							getAveragePacketsSent() + "," +
							getIncomingRequests() +
							",router:" +
							getRouterNodes() + "," +
							getRouterLeaves() + "," +
							getRouterContacts() +
							",database:" +
							getDBKeyCount() + ","+
							getDBValueCount() + ","+
							getDBValuesStored() + ","+
							getDBStoreSize() + ","+
							getDBKeyDivFreqCount() + ","+
							getDBKeyDivSizeCount() + ","+
							getDBKeysBlocked()+
							",version:" + getVersion()+","+
							getRouterUptime() + ","+
							getRouterCount());
					}
			};


		return( res );
	}

	private static final List<DHTTransportUDPImpl>			transports 		= new ArrayList<>();
	private static final List<DHTTransportAlternativeNetwork>	alt_networks 	= new ArrayList<>();

	protected static void
	registerTransport(
		DHTTransportUDPImpl		transport )
	{
		synchronized( transports ){

			transports.add( transport );

			for ( DHTTransportAlternativeNetwork net: alt_networks ){

				transport.registerAlternativeNetwork( net );
			}
		}
	}

	public static void
	registerAlternativeNetwork(
		DHTTransportAlternativeNetwork	net )
	{
		synchronized( transports ){

			alt_networks.add( net );

			for ( DHTTransportUDPImpl transport: transports ){

				transport.registerAlternativeNetwork( net );
			}
		}
	}

	public static DHTTransportAlternativeNetwork
	getAlternativeNetwork(
		int				type )
	{
		synchronized( transports ){

			for (DHTTransportAlternativeNetwork net: alt_networks ){
				
				if  ( net.getNetworkType() == type ){
					
					return( net );
				}
			}
		}
		
		return( null );
	}
	
	public static void
	unregisterAlternativeNetwork(
		DHTTransportAlternativeNetwork	net )
	{
		synchronized( transports ){

			alt_networks.remove( net );

			for ( DHTTransportUDPImpl transport: transports ){

				transport.unregisterAlternativeNetwork( net );
			}
		}
	}

	public static List<DHTTransportAlternativeContact>
	getAlternativeContacts(
		int			network,
		int			max )
	{
		List<DHTTransportAlternativeContact>	result_list = new ArrayList<>(max);

		if ( max > 0 ){

			TreeSet<DHTTransportAlternativeContact> result_set =
					new TreeSet<>(
							new Comparator<DHTTransportAlternativeContact>() {
								@Override
								public int
								compare(
									DHTTransportAlternativeContact o1,
									DHTTransportAlternativeContact o2)
								{
									int res = o2.getLastAlive() - o1.getLastAlive();

									if (res == 0) {

										res = o1.getID() - o2.getID();
									}

									return (res);
								}
							});

			synchronized( transports ){

					// if we have a local provider then grab stuff from here

				for ( DHTTransportAlternativeNetwork net: alt_networks ){

					if ( net.getNetworkType() == network ){

						List<DHTTransportAlternativeContact> temp = net.getContacts( max );

						if ( temp != null ){

							result_set.addAll( temp );
						}
					}
				}

					// merge in any remote contacts and then take the best ones

				for ( DHTTransportUDPImpl transport: transports ){

					DHTTransportAlternativeNetwork alt = transport.getAlternativeNetwork( network );

					if ( alt != null ){

						List<DHTTransportAlternativeContact> temp = alt.getContacts( max );

						if ( temp != null ){

							result_set.addAll( temp );
						}
					}
				}
			}

			Set<Integer>	used_ids = new HashSet<>();

			Iterator<DHTTransportAlternativeContact> it = result_set.iterator();

			while( it.hasNext() && result_list.size() < max ){

				DHTTransportAlternativeContact c = it.next();
				
				int id = c.getID();
				
				if ( used_ids.contains( id )){
					
					continue;
				}
				
				used_ids.add( id );
				
				result_list.add( c);
			}
		}

		return( result_list );
	}
	
	private static final int					MAX_CC_STATS	= 24;	// must be even
	private static final int					CALC_PERIOD		= 60*1000;
	private static volatile long				last_calc		= SystemTime.getMonotonousTime() - ( CALC_PERIOD + 1 );
	
	private static volatile CountryDetails[]	last_details_recv	= new CountryDetails[0];
	private static volatile CountryDetails[]	last_details_sent	= new CountryDetails[0];
	
	private static volatile long				last_details_recv_total;
	private static volatile long				last_details_sent_total;
	
	private static volatile GlobalManagerStats	gm_stats;
	
	//private static Average	send_freq = Average.getInstance( 1000, 10 );
	
	private static volatile long	last_upload_stats;
	
	protected static void
	serialiseUploadStats(
		int						protocol_version,
		int						packet_type,
		DataOutputStream		os )
	
		throws IOException
	{
			// full set of stats = 11 + 24*12= about 300 bytes. so limit to 3 per sec
		
		//send_freq.addValue(1);
		//System.out.println( send_freq.getAverage() + " - " + packet_type );
		
		if ( protocol_version < DHTTransportUDP.PROTOCOL_VERSION_BBT_UPLOAD_STATS ){
		
			return;
		}
		
		long	now = SystemTime.getMonotonousTime();
		
		if ( now - last_upload_stats < 333 ){
			
			os.writeByte( 0xff );	// special case meaning 'no stats'
			
			return;
		}
		
		last_upload_stats = now;
		
		if ( now - last_calc > CALC_PERIOD ){

			if ( gm_stats == null ){
				
				gm_stats = CoreFactory.getSingleton().getGlobalManager().getStats();
			}
			
			Iterator<CountryDetails>	it = gm_stats.getCountryDetails();
			
			List<CountryDetails>	recvs = new ArrayList<>(128);
			List<CountryDetails>	sents = new ArrayList<>(128);
			
			Map<CountryDetails,Long>	recv_cache = new HashMap<>();
			Map<CountryDetails,Long>	sent_cache = new HashMap<>();
				
			long	recv_total	= 0;
			long	sent_total	= 0;
			
			while( it.hasNext()){
				
				CountryDetails cd = it.next();
				
				if ( cd.getCC().isEmpty()){
					
					continue;
				}
				
				long recv = cd.getAverageReceived();
				
				if ( recv > 0 ){
			
					recv_total += recv;
					
					recvs.add( cd );
					
					recv_cache.put( cd,  recv );
				}
				
				long sent = cd.getAverageSent();
				
				if ( sent > 0 ){
			
					sent_total += sent;
					
					sents.add( cd );
					
					sent_cache.put( cd,  sent );
				}
			}
						
			CountryDetails[] recvs_a = recvs.toArray( new CountryDetails[0] );
			
			Arrays.sort(
				recvs_a,
				new Comparator<CountryDetails>(){
					@Override
					public int 
					compare(
						CountryDetails o1, 
						CountryDetails o2)
					{
						Long l1 = (Long)recv_cache.get(o1);
						Long l2 = (Long)recv_cache.get(o2);
						
						return( Long.compare( l2, l1 ));
					}
				});
				
			CountryDetails[] sents_a = sents.toArray( new CountryDetails[0] );
			
			Arrays.sort(
				sents_a,
				new Comparator<CountryDetails>(){
					@Override
					public int 
					compare(
						CountryDetails o1, 
						CountryDetails o2)
					{
						Long l1 = (Long)sent_cache.get(o1);
						Long l2 = (Long)sent_cache.get(o2);
						
						return( Long.compare( l2, l1 ));
					}
				});
			
			last_details_recv_total	= recv_total;
			last_details_sent_total	= sent_total;
			
			last_details_recv = recvs_a;
			last_details_sent = sents_a;
			
			last_calc = now;
		}
				
		CountryDetails[] details_recv = last_details_recv;
		CountryDetails[] details_sent = last_details_sent;
		
		os.writeByte( 0x02 );	// version
			
		os.writeFloat( last_details_recv_total );
		os.writeFloat( last_details_sent_total );
		
		int num_recv 	= details_recv.length;
		int num_sent	= details_sent.length;
		
		if ( num_recv + num_sent > MAX_CC_STATS ){
			
			final int half = MAX_CC_STATS/2;
						
			if ( num_recv < half ){
				
				num_sent = MAX_CC_STATS - num_recv;
				
			}else if ( num_sent < half ){
				
				num_recv = MAX_CC_STATS - num_sent;
				
			}else{
				
				num_recv = num_sent = half;
			}
		}
		
		for ( int loop=0;loop<2;loop++){
			
			CountryDetails[]	details;
			int					records;

			if ( loop == 0 ){
				details	= details_recv;
				records	= num_recv;
			}else{
				details	= details_sent;
				records	= num_sent;
			}
			
			os.writeByte((byte)records );
						
			for ( int i=0;i<records;i++){
				
				CountryDetails cd = details[i];
				
				String cc = cd.getCC();
				
				if ( cc.length() > 2 ){
					
					if ( cc.equals( AENetworkClassifier.AT_I2P )){
						
						cc = "X0";
						
					}else if ( cc.equals( AENetworkClassifier.AT_TOR )){
						
						cc = "X1";
						
					}else{
						
						cc = "X2";
					}
				}
				
				os.writeByte((byte)cc.charAt(0));
				os.writeByte((byte)cc.charAt(1));
								
				os.writeFloat( loop==0?cd.getAverageReceived():cd.getAverageSent());
			}
		}
	}
	
	protected static Object
	deserialiseUploadStats(
		DataInputStream			is )
	
		throws IOException
	{
		try{
			byte version = is.readByte();
			
			if ( version == 0 || version == 1 ){
							
				int records = (int)(is.readByte() & 0x00ff );
				
				if ( records > 0 && records <= MAX_CC_STATS ){
					
					RemoteCountryStats[]	stats = new RemoteCountryStats[records];
					
					for ( int i=0;i<records;i++){
						
						byte c1 = is.readByte();
						byte c2 = is.readByte();
						
						String cc = "" + (char)c1 + (char)c2;
						
						if ( c1 == 'X' ){
							
							if ( cc.equals( "X0" )){
							
								cc = AENetworkClassifier.AT_I2P;
								
							}else if ( cc.equals( "X1" )){
								
								cc = AENetworkClassifier.AT_TOR;
								
							}else{
								
							}
						}
						
						String f_cc = cc;
						
						long 	bytes;
						
						if ( version == 0 ){
							
							bytes = ((long)( is.readInt() & 0x00000000ffffffff ))*1024;
												
						}else if ( version == 1 ){
							
							bytes= (long)is.readFloat();
							
						}else{
							
							bytes	= 0;
						}
						
						stats[i] = 
							new RemoteCountryStats()
							{
								public String 
								getCC()
								{
									return( f_cc );
								}
							
								public long
								getAverageReceivedBytes()
								{
									return( bytes );
								}
								
								@Override
								public long 
								getAverageSentBytes()
								{
									return( 0 );
								}
							};
					}
					
					return( stats );
				
				}else{
					
					return( null );
				}
			}else if ( version == 2 ){
				
				long	total_recv	= (long)is.readFloat();
				long	total_sent	= (long)is.readFloat();
								
				Map<String,long[]>	stats_map = new HashMap<>();
				
				stats_map.put( "", new long[]{ total_recv, total_sent });
				
				for ( int loop=0;loop<2;loop++){
					
					int records = (int)(is.readByte() & 0x00ff );
					
					if ( records > 0 && records <= MAX_CC_STATS ){
												
						for ( int i=1;i<=records;i++){
							
							byte c1 = is.readByte();
							byte c2 = is.readByte();
							
							String cc = "" + (char)c1 + (char)c2;
							
							if ( c1 == 'X' ){
								
								if ( cc.equals( "X0" )){
								
									cc = AENetworkClassifier.AT_I2P;
									
								}else if ( cc.equals( "X1" )){
									
									cc = AENetworkClassifier.AT_TOR;
									
								}else{
									
								}
							}
							
							long[] values = stats_map.get( cc );
							
							if ( values == null ){
								
								values = new long[2];
								
								stats_map.put( cc, values );
							}
							
							values[loop] = (long)is.readFloat();
						}
					}
				}
				
				RemoteCountryStats[]	stats = new RemoteCountryStats[stats_map.size()];
				
				int	pos = 0;
				
				for ( Map.Entry<String,long[]> entry: stats_map.entrySet()){
					
					long[]	values 	= entry.getValue();
					String	cc		= entry.getKey();
					
					final long	recv 	= values[0];
					final long	sent	= values[1];
					
					stats[pos++] = 
						new RemoteCountryStats()
						{
							public String 
							getCC()
							{
								return( cc );
							}
						
							public long
							getAverageReceivedBytes()
							{
								return( recv );
							}
							
							@Override
							public long 
							getAverageSentBytes()
							{
								return( sent );
							}
						};
				}
				
				return( stats );
				
			}else{
				
					// note that version 0xff is used to indicate no stats have been sent
				
				return( null );
			}
		}catch( Throwable e ){
			
			if ( Constants.IS_CVS_VERSION ){
			
				Debug.out(e);
			}
			
			return( null );
		}
	}
	
	protected static void
	receiveUploadStats(
		DHTTransportUDPContactImpl	contact,
		Object						_stats )
	{		
		if ( _stats == null ){
			
			return;
		}
	
		RemoteCountryStats[]	stats = (RemoteCountryStats[])_stats;
		
		InetAddress address = contact.getTransportAddress().getAddress();
		
		if ( gm_stats == null ){
			
			gm_stats = CoreFactory.getSingleton().getGlobalManager().getStats();
		}
		
		gm_stats.receiveRemoteStats(
			new RemoteStats()
			{	
				@Override
				public RemoteCountryStats[] getStats(){
					return( stats );
				}
								
				@Override
				public InetAddress getRemoteAddress(){
					return( address );
				}
			});
	}
}
