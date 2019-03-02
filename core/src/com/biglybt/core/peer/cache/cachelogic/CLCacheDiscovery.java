/*
 * Created on Jan 31, 2007
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

/**
 *
 * This class handles communication with the CDP server.
 * Original code from
 * @author Matthias Scheler <mscheler@cachelogic.com>
 *
 */

package com.biglybt.core.peer.cache.cachelogic;

import java.io.File;
import java.net.*;

import com.biglybt.core.peer.cache.CacheDiscoverer;
import com.biglybt.core.peer.cache.CacheDiscovery;
import com.biglybt.core.peer.cache.CachePeer;
import com.biglybt.core.peermanager.utils.PeerClassifier;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SHA1Hasher;

public class
CLCacheDiscovery
	implements CacheDiscoverer
{
	public static final String	CDPDomainName = ".find-cache.com";
	public static final String	CDPServerName = "cls" + CDPDomainName;
	public static final int	CDPPort = 19523;
	public static final int	CDPVersion = 0;
	public static final int	CDPTimeout = 5000;

	static CDPResponse		Response;

	/* Convert an array of bytes into a its hexadecimal representation. */

	private String
	byteArrayToHex(byte[] Bytes, int Max) {
		int		Length, Index, Value;
		String	Result;

		Length = Bytes.length;
		if (Length > Max)
			Length = Max;

		Result = new String();
		for (Index = 0; Index < Length; Index++) {
			Value = Bytes[Index] & 0xff;
			if (Value < 16)
				Result += "0";
			Result += Integer.toHexString(Value);
		}
		return Result;
	}

		/* Find out the farm name via CDP. */

	private String
	lookupFarm()
	{
		if (Response != null) {

			if (Response.isStillValid()){

				return Response.getFarmID();
			}

			Response = null;
		}

		try {
			InetAddress	CDPServer;
			DatagramSocket	Socket;
			CDPQuery		Query;
			byte[]		Buffer;
			DatagramPacket	Packet;

			/* Get the IP address of the CDP servers. */
			CDPServer = InetAddress.getByName(CDPServerName);

			/* Create a UDP socket for the CDP query. */
			Socket = new DatagramSocket();
			Socket.setSoTimeout(CDPTimeout);

			/* Build the CDP query. */
			Query = new CDPQuery(Constants.AZUREUS_NAME + " " +	Constants.AZUREUS_VERSION);
			Buffer = Query.getBytes();
			Packet = new DatagramPacket(Buffer, Buffer.length, CDPServer, CDPPort);

			/* Send the query to the CDP server. */
			Socket.send(Packet);

			/* Receive the CDP response. */
			Buffer = new byte[CDPResponse.MaxSize];
			Packet.setData(Buffer);
			Socket.receive(Packet);
			if (Packet.getAddress() != CDPServer || Packet.getPort() != CDPPort)
				throw(new Exception("CDP server address mismatch on response"));

			/* Parse the CDP response. */
			Response = new CDPResponse(Packet.getData());

			/* Return the farmID from the CDP response. */
			return Response.getFarmID();
		} catch (Throwable Excpt) {

			if ( Excpt instanceof UnknownHostException ){

			}else{

				Excpt.printStackTrace();
			}

			return "default";
		}
	}

	/* Calculate publisher string for DNS query from announce URL. */

	private String
	hashAnnounceURL(
		URL	 announce_url )
	{
		/* Calculate SHA1 hash of the hostname. */

		byte[] Digest = new SHA1Hasher().calculateHash( announce_url.getHost().getBytes());

		/*
		 * Return first 32 characters of the hexadecimal representation of the
		 * SHA1 hash.
		 */

		return byteArrayToHex(Digest, 16);
	}

	/* Find a cache for a given announce URL and BitTorrent hash. */

	public InetAddress[]
	findCache(
		URL		announce_url,
		String 	hex_hash )
	{
		String		Hostname;
		InetAddress[]	Caches;

		/*
		 * Build the hostname for the DNS query:
		 * bt-<short hash>.bt-<announce hash>-<farm>.find-cache.com
		 *
		 * short hash:	first four hexadecimal digits of the BitTorrent hash
		 * announce hash:	see hashAnnounceURL()
		 * farm:		farm name returned by CDP query.
		 */
		Hostname = "bt-" + hex_hash.substring(0, 4) +
		".bt-" + hashAnnounceURL(announce_url) +
		"-" + lookupFarm() + CDPDomainName;
		// System.out.println("findCache(): " + announce_url + " " + hex_hash + " --> " +	Hostname);
		try {
			Caches = InetAddress.getAllByName(Hostname);
		} catch (UnknownHostException NoCache) {
			Caches = new InetAddress[0];
		}
		return Caches;
	}

	/* Find a cache for a given announce URL and BitTorrent hash. */

	public InetAddress[]
	findCache(
		URL	 	announce_url,
		byte[] 	hash )
	{
		return findCache(announce_url, byteArrayToHex(hash, 4));
	}

	@Override
	public CachePeer[]
	lookup(
		TOTorrent	torrent )
	{
		try{
			InetAddress[]	addresses = findCache( torrent.getAnnounceURL(), torrent.getHash());

			CachePeer[] result = new CachePeer[addresses.length];

			for (int i=0;i<addresses.length;i++){

				result[i] = new CacheDiscovery.CachePeerImpl( CachePeer.PT_CACHE_LOGIC, addresses[i], 6881 );
			}

			return( result );

		}catch( TOTorrentException e ){

			Debug.printStackTrace( e );

			return( new CachePeer[0] );
		}
	}

	@Override
	public CachePeer
	lookup(
		byte[] 			peer_id,
		InetAddress 	ip,
		int 			port )
	{
		if ( PeerClassifier.getClientDescription( peer_id, AENetworkClassifier.AT_PUBLIC ).startsWith( PeerClassifier.CACHE_LOGIC )){

			return( new CacheDiscovery.CachePeerImpl( CachePeer.PT_CACHE_LOGIC, ip, port ));
		}

		return( null );
	}

	static class
	CDPQuery
	{
		private String Client;

		/* Construct a query for a given client identifier. */
		public CDPQuery(String _Client)
		{
			Client = _Client;
		}

		/* Return the binary representation. */
		byte[] getBytes()
		{
			String	Temp;
			byte[]	Bytes;

			Temp = "@@@" + Client;
			Bytes = Temp.getBytes();
			Bytes[0] = CDPVersion;	// Version
			Bytes[1] = 0;					// Flags
			Bytes[2] = (byte)Client.length();			// Length of the client identifier

			return Bytes;
		}
	}

	/**
	 *
	 * This class parses a CDP response returned by the CDP server.
	 *
	 * @author Matthias Scheler <mscheler@cachelogic.com>
	 *
	 */
	static class
	CDPResponse
	{
		public static final int	MinSize = 7;
		public static final int	MaxSize = 262;

		/* The CDP farm ID. */
		String	farmID;
		/* This CDP response is valid until this point of time. */
		long		validUntil;

		/* Create a response object from a given binary encoded CDP message. */
		public CDPResponse(byte[] Bytes) throws Exception
		{
			int		Index;
			long	Timeout;

			if (Bytes.length < MinSize || MinSize + Bytes[6] > Bytes.length)
				throw new Exception("CDP response too short");

			if (Bytes[0] != CDPVersion)
				throw new Exception("Unsupported CDP version");

			farmID = new String();
			for (Index = 0; Index < Bytes[6]; Index++)
				farmID += (char)Bytes[MinSize + Index];

			Timeout = 0;
			for (Index = 2; Index < 6; Index++)
				Timeout = (Timeout << 8) + ((int)Bytes[Index] & 0xff);
			validUntil = System.currentTimeMillis() + Timeout * 1000;
		}

		/* Return the farm ID. */
		public String getFarmID()
		{
			return farmID;
		}

		/* Is the information in this CDP response still valid? */
		public boolean isStillValid()
		{
			return (System.currentTimeMillis() < validUntil);
		}
	}

	public static void
	main(
		String[]	args )
	{
		try{
			TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedFile( new File( "C:\\temp\\test.torrent" ));

			CachePeer[]	peers = new CLCacheDiscovery().lookup( torrent );

			System.out.println( "peers=" + peers.length );

			for (int i=0;i<peers.length;i++){

				System.out.println( "    cache: " + peers[i].getAddress() + ":" + peers[i].getPort() );
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}