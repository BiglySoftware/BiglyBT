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

package com.biglybt.core.dht.transport.util;

import java.net.InetSocketAddress;
import java.util.Arrays;

import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.transport.DHTTransportStats;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPPacketHelper;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPPacketRequest;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;

/**
 * @author parg
 *
 */

public abstract class
DHTTransportStatsImpl
	implements DHTTransportStats
{
	private static final int RTT_HISTORY	= 50;

	private final byte	protocol_version;

	private long[]	pings			= new long[4];
	private long[]	find_nodes		= new long[4];
	private long[]	find_values		= new long[4];
	private long[]	stores			= new long[4];
	private final long[]	stats			= new long[4];
	private long[]	data			= new long[4];
	private long[]	key_blocks		= new long[4];
	private long[]	store_queries	= new long[4];

	private long[]	aliens		= new long[7];

	private long	incoming_requests;
	private long	outgoing_requests;

	private long	incoming_version_requests;
	private final long[]	incoming_request_versions;
	private long	outgoing_version_requests;
	private final long[]	outgoing_request_versions;

	private static final int SKEW_VALUE_MAX	= 256;
	private final int[]	skew_values = new int[SKEW_VALUE_MAX];
	private int			skew_pos	= 0;
	private long		last_skew_average;
	private long		last_skew_average_time;

	private final BloomFilter	skew_originator_bloom =
		BloomFilterFactory.createRotating(
				BloomFilterFactory.createAddOnly( SKEW_VALUE_MAX*4 ),
				2 );

	private final int[]	rtt_history			= new int[RTT_HISTORY];
	private int		rtt_history_pos;

	protected
	DHTTransportStatsImpl(
		byte	_protocol_version )
	{
		protocol_version	= _protocol_version;

		incoming_request_versions = new long[protocol_version+1];
		outgoing_request_versions = new long[protocol_version+1];

		Arrays.fill( skew_values, Integer.MAX_VALUE );
	}

	protected byte
	getProtocolVersion()
	{
		return( protocol_version );
	}

	public void
	receivedRTT(
		int		rtt )
	{
		if ( rtt <= 0 || rtt > 2*60*1000 ){

			return;	// silly times
		}

		synchronized( rtt_history ){

			rtt_history[rtt_history_pos++%RTT_HISTORY]	= rtt;
		}
	}

	@Override
	public int[]
	getRTTHistory()
	{
		synchronized( rtt_history ){

				// safe for the caller to have access to the raw array

			return( rtt_history );
		}
	}

	public void
	add(
		DHTTransportStatsImpl	other )
	{
		add( pings, other.pings );
		add( find_nodes, other.find_nodes );
		add( find_values, other.find_values );
		add( stores, other.stores );
		add( stats, other.stats );
		add( data, other.data );
		add( key_blocks, other.key_blocks );
		add( store_queries, other.store_queries );
		add( aliens, other.aliens );

		incoming_requests += other.incoming_requests;
		outgoing_requests += other.outgoing_requests;
	}

	private void
	add(
		long[]	a,
		long[] 	b )
	{
		for (int i=0;i<a.length;i++){
			a[i]	+= b[i];
		}
	}

	protected void
	snapshotSupport(
		DHTTransportStatsImpl	clone )
	{
		clone.pings			= (long[])pings.clone();
		clone.find_nodes	= (long[])find_nodes.clone();
		clone.find_values	= (long[])find_values.clone();
		clone.stores		= (long[])stores.clone();
		clone.data			= (long[])data.clone();
		clone.key_blocks	= (long[])key_blocks.clone();
		clone.store_queries	= (long[])store_queries.clone();
		clone.aliens		= (long[])aliens.clone();

		clone.incoming_requests	= incoming_requests;
		clone.outgoing_requests	= outgoing_requests;
	}
		// ping

	public void
	pingSent(
		DHTUDPPacketRequest	request )
	{
		pings[STAT_SENT]++;

		outgoingRequestSent( request );
	}

	public void
	pingOK()
	{
		pings[STAT_OK]++;
	}
	public void
	pingFailed()
	{
		pings[STAT_FAILED]++;
	}
	public void
	pingReceived()
	{
		pings[STAT_RECEIVED]++;
	}

	@Override
	public long[]
	getPings()
	{
		return( pings );
	}

		// key blocks

	public void
	keyBlockSent(
		DHTUDPPacketRequest	request )
	{
		key_blocks[STAT_SENT]++;

		outgoingRequestSent( request );
	}
	public void
	keyBlockOK()
	{
		key_blocks[STAT_OK]++;
	}
	public void
	keyBlockFailed()
	{
		key_blocks[STAT_FAILED]++;
	}
	public void
	keyBlockReceived()
	{
		key_blocks[STAT_RECEIVED]++;
	}

	@Override
	public long[]
	getKeyBlocks()
	{
		return( key_blocks );
	}

		// store queries

	public void
	queryStoreSent(
		DHTUDPPacketRequest	request )
	{
		store_queries[STAT_SENT]++;

		outgoingRequestSent( request );
	}

	public void
	queryStoreOK()
	{
		store_queries[STAT_OK]++;
	}

	public void
	queryStoreFailed()
	{
		store_queries[STAT_FAILED]++;
	}

	public void
	queryStoreReceived()
	{
		store_queries[STAT_RECEIVED]++;
	}

	@Override
	public long[]
	getQueryStores()
	{
		return( store_queries );
	}

		// find node

	public void
	findNodeSent(
		DHTUDPPacketRequest	request )
	{
		find_nodes[STAT_SENT]++;

		outgoingRequestSent( request );
	}
	public void
	findNodeOK()
	{
		find_nodes[STAT_OK]++;
	}
	public void
	findNodeFailed()
	{
		find_nodes[STAT_FAILED]++;
	}
	public void
	findNodeReceived()
	{
		find_nodes[STAT_RECEIVED]++;
	}
	@Override
	public long[]
	getFindNodes()
	{
		return( find_nodes );
	}

		// find value

	public void
	findValueSent(
		DHTUDPPacketRequest	request )
	{
		find_values[STAT_SENT]++;

		outgoingRequestSent( request );
	}
	public void
	findValueOK()
	{
		find_values[STAT_OK]++;
	}
	public void
	findValueFailed()
	{
		find_values[STAT_FAILED]++;
	}
	public void
	findValueReceived()
	{
		find_values[STAT_RECEIVED]++;
	}
	@Override
	public long[]
	getFindValues()
	{
		return( find_values );
	}

		// store

	public void
	storeSent(
		DHTUDPPacketRequest	request )
	{
		stores[STAT_SENT]++;

		outgoingRequestSent( request );
	}

	public void
	storeOK()
	{
		stores[STAT_OK]++;
	}
	public void
	storeFailed()
	{
		stores[STAT_FAILED]++;
	}
	public void
	storeReceived()
	{
		stores[STAT_RECEIVED]++;
	}
	@Override
	public long[]
	getStores()
	{
		return( stores );
	}
		//stats

	public void
	statsSent(
		DHTUDPPacketRequest	request )
	{
		stats[STAT_SENT]++;

		outgoingRequestSent( request );
	}

	public void
	statsOK()
	{
		stats[STAT_OK]++;
	}
	public void
	statsFailed()
	{
		stats[STAT_FAILED]++;
	}
	public void
	statsReceived()
	{
		stats[STAT_RECEIVED]++;
	}

		//data

	public void
	dataSent(
		DHTUDPPacketRequest	request )
	{
		data[STAT_SENT]++;

		outgoingRequestSent( request );
	}

	public void
	dataOK()
	{
		data[STAT_OK]++;
	}

    public void
	dataFailed()
	{
		data[STAT_FAILED]++;
	}

	public void
	dataReceived()
	{
		data[STAT_RECEIVED]++;
	}

	@Override
	public long[]
	getData()
	{
		return( data );
	}

	protected void
	outgoingRequestSent(
		DHTUDPPacketRequest	request )
	{
		outgoing_requests++;

		if ( DHTLog.TRACE_VERSIONS && request != null ){

			byte protocol_version = request.getProtocolVersion();

			if ( protocol_version >= 0 && protocol_version < outgoing_request_versions.length ){

				outgoing_request_versions[ protocol_version ]++;

				outgoing_version_requests++;

				if ( outgoing_version_requests%100 == 0 ){

					String	str= "";

					for (int i=0;i<outgoing_request_versions.length;i++){

						long	count = outgoing_request_versions[i];

						if ( count > 0 ){

							str += (str.length()==0?"":", ") + i + "=" +  count + "[" +
										((outgoing_request_versions[i]*100)/outgoing_version_requests) + "]";
						}
					}

					System.out.println( "net " + request.getTransport().getNetwork() + ": Outgoing versions: tot = " + outgoing_requests +"/" + outgoing_version_requests + ": " + str );
				}

				if ( outgoing_version_requests%1000 == 0 ){

					for (int i=0;i<outgoing_request_versions.length;i++){

						outgoing_request_versions[i] = 0;
					}

					outgoing_version_requests	= 0;
				}
			}
		}
	}

	public void
	incomingRequestReceived(
		DHTUDPPacketRequest	request,
		boolean				alien )
	{
		incoming_requests++;

		if ( alien && request != null ){

			// System.out.println( "Alien on net " + request.getNetwork() + " - sender=" + request.getAddress());

			int	type = request.getAction();

			if ( type == DHTUDPPacketHelper.ACT_REQUEST_FIND_NODE ){

				aliens[AT_FIND_NODE]++;

			}else if ( type == DHTUDPPacketHelper.ACT_REQUEST_FIND_VALUE ){

				aliens[AT_FIND_VALUE]++;

			}else if ( type == DHTUDPPacketHelper.ACT_REQUEST_PING ){

				aliens[AT_PING]++;

			}else if ( type == DHTUDPPacketHelper.ACT_REQUEST_STATS ){

				aliens[AT_STATS]++;

			}else if ( type == DHTUDPPacketHelper.ACT_REQUEST_STORE ){

				aliens[AT_STORE]++;

			}else if ( type == DHTUDPPacketHelper.ACT_REQUEST_KEY_BLOCK ){

				aliens[AT_KEY_BLOCK]++;

			}else if ( type == DHTUDPPacketHelper.ACT_REQUEST_QUERY_STORE ){

				aliens[AT_QUERY_STORE]++;
			}
		}

		if ( DHTLog.TRACE_VERSIONS && request != null ){

			byte protocol_version = request.getProtocolVersion();

			if ( protocol_version >= 0 && protocol_version < incoming_request_versions.length ){

				incoming_request_versions[ protocol_version ]++;

				incoming_version_requests++;

				if ( incoming_version_requests%100 == 0 ){

					String	str= "";

					for (int i=0;i<incoming_request_versions.length;i++){

						long	count = incoming_request_versions[i];

						if ( count > 0 ){

							str += (str.length()==0?"":", ") + i + "=" +  count + "[" +
										((incoming_request_versions[i]*100)/incoming_version_requests) + "]";
						}
					}

					System.out.println( "net " + request.getTransport().getNetwork() + ": Incoming versions: tot = " + incoming_requests +"/" + incoming_version_requests + ": " + str );
				}

				if ( incoming_version_requests%1000 == 0 ){

					for (int i=0;i<incoming_request_versions.length;i++){

						incoming_request_versions[i] = 0;
					}

					incoming_version_requests	= 0;
				}
			}
		}
	}

	@Override
	public long[]
	getAliens()
	{
		return( aliens );
	}

	@Override
	public long
	getIncomingRequests()
	{
		return( incoming_requests );
	}

	public void
	recordSkew(
		InetSocketAddress	originator_address,
		long				skew )
	{
		byte[]	bytes = AddressUtils.getAddressBytes( originator_address );

		if ( skew_originator_bloom.contains( bytes)){

			//System.out.println( "skipping skew: " + originator_address );

			return;
		}

		skew_originator_bloom.add( bytes );

		//System.out.println( "adding skew: " + originator_address + "/" + skew );

		int	i_skew = skew<Integer.MAX_VALUE?(int)skew:(Integer.MAX_VALUE-1);

			// no sync here as not important so ensure things work ok

		int	pos = skew_pos;

		skew_values[ pos++ ] = i_skew;

		if ( pos == SKEW_VALUE_MAX ){

			pos	= 0;
		}

		skew_pos = pos;
	}

	@Override
	public long
	getSkewAverage()
	{
		long	now = SystemTime.getCurrentTime();

		if ( 	now < last_skew_average_time ||
				now - last_skew_average_time > 30000 ){

			int[]	values = (int[])skew_values.clone();

			int		pos = skew_pos;

			int		num_values;

			if ( values[pos] == Integer.MAX_VALUE ){

				num_values = pos;
			}else{

				num_values = SKEW_VALUE_MAX;
			}

			Arrays.sort( values, 0, num_values );

				// remove outliers

			int	start 	= num_values/3;
			int end		= 2*num_values/3;

			int	entries	= end - start;

			if ( entries < 5 ){

				last_skew_average = 0;

			}else{

				long	total = 0;

				for (int i=start;i<end;i++){

					total += (long)values[i];
				}

				last_skew_average 		= total / entries;
			}

			last_skew_average_time	= now;
		}

		return( last_skew_average );
	}

	@Override
	public String
	getString()
	{
		return( "ping:" + getString( pings ) + "," +
				"store:" + getString( stores ) + "," +
				"node:" + getString( find_nodes ) + "," +
				"value:" + getString( find_values ) + "," +
				"stats:" + getString( stats ) + "," +
				"data:" + getString( data ) + "," +
				"kb:" + getString( key_blocks ) + "," +
				"incoming:" + incoming_requests +"," +
				"alien:" + getString( aliens ));
	}

	protected String
	getString(
		long[]	x )
	{
		String	str = "";

		for (int i=0;i<x.length;i++){

			str += (i==0?"":",") + x[i];
		}

		return( str );
	}
}
