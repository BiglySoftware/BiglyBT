/*
 * File    : TRTrackerServerPeerImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.tracker.server.impl;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.tracker.server.TRTrackerServerPeer;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.HostNameToIPResolverListener;
import com.biglybt.core.util.SystemTime;

public class
TRTrackerServerPeerImpl
	implements TRTrackerServerPeer, TRTrackerServerSimplePeer, HostNameToIPResolverListener, TRTrackerServerNatCheckerListener
{
	private final HashWrapper	peer_id;
	private final int			key_hash_code;

	private byte[]		ip;
	private final boolean		ip_override;
	private short		tcp_port;
	private short		udp_port;
	private short		http_port;
	private byte		crypto_level;
	private byte		az_ver;
	private String		ip_str;
	private byte[]		ip_bytes;
	private byte		NAT_status	= NAT_CHECK_UNKNOWN;

	private long		timeout;

	private long		uploaded;
	private long		downloaded;
	private long		amount_left;

	private long		last_contact_time;
	private boolean		download_completed;
	private boolean		biased;

	private short				up_speed;

		// fields above are serialised when exported

	private DHTNetworkPosition	network_position;
	private Object				user_data;

	protected
	TRTrackerServerPeerImpl(
		HashWrapper			_peer_id,
		int					_key_hash_code,
		byte[]				_ip,
		boolean				_ip_override,
		int					_tcp_port,
		int					_udp_port,
		int					_http_port,
		byte				_crypto_level,
		byte				_az_ver,
		long				_last_contact_time,
		boolean				_download_completed,
		byte				_last_nat_status,
		int					_up_speed,
		DHTNetworkPosition	_network_position )
	{
		peer_id				= _peer_id;
		key_hash_code		= _key_hash_code;
		ip					= _ip;
		ip_override			= _ip_override;
		tcp_port			= (short)_tcp_port;
		udp_port			= (short)_udp_port;
		http_port			= (short)_http_port;
		crypto_level		= _crypto_level;
		az_ver				= _az_ver;
		last_contact_time	= _last_contact_time;
		download_completed	= _download_completed;
		NAT_status			= _last_nat_status;
		up_speed			= _up_speed>Short.MAX_VALUE?Short.MAX_VALUE:(short)_up_speed;
		network_position	= _network_position;

		resolveAndCheckNAT();
	}

		/**
		 * Import constructor
		 */

	protected
	TRTrackerServerPeerImpl(
		HashWrapper			_peer_id,
		int					_key_hash_code,
		byte[]				_ip,
		boolean				_ip_override,
		short				_tcp_port,
		short				_udp_port,
		short				_http_port,
		byte				_crypto_level,
		byte				_az_ver,
		String				_ip_str,
		byte[]				_ip_bytes,
		byte				_NAT_status,
		long				_timeout,
		long				_uploaded,
		long				_downloaded,
		long				_amount_left,
		long				_last_contact_time,
		boolean				_download_completed,
		boolean				_biased,
		short				_up_speed )
	{
		peer_id				= _peer_id;
		key_hash_code		= _key_hash_code;
		ip					= _ip;
		ip_override			= _ip_override;
		tcp_port			= _tcp_port;
		udp_port			= _udp_port;
		http_port			= _http_port;
		crypto_level		= _crypto_level;
		az_ver				= _az_ver;
		ip_str				= _ip_str;
		ip_bytes			= _ip_bytes;
		NAT_status			= _NAT_status;
		timeout				= _timeout;
		uploaded			= _uploaded;
		downloaded			= _downloaded;
		amount_left			= _amount_left;
		last_contact_time	= _last_contact_time;
		download_completed	= _download_completed;
		biased				= _biased;
		up_speed			= _up_speed;
	}

	protected boolean
	update(
		byte[]				_ip,
		int					_port,
		int					_udp_port,
		int					_http_port,
		byte				_crypto_level,
		byte				_az_ver,
		int					_up_speed,
		DHTNetworkPosition	_network_position )
	{
		udp_port			= (short)_udp_port;
		http_port			= (short)_http_port;
		crypto_level		= _crypto_level;
		az_ver				= _az_ver;
		up_speed			= _up_speed>Short.MAX_VALUE?Short.MAX_VALUE:(short)_up_speed;
		network_position	= _network_position;

		boolean	res	= false;

		if ( _port != getTCPPort() ){

			tcp_port	= (short)_port;

			res		= true;
		}

		if ( !Arrays.equals( _ip, ip )){

			ip			= _ip;

			res	= true;
		}

		if ( res ){

			resolveAndCheckNAT();
		}

		return( res );
	}

	@Override
	public void
	NATCheckComplete(
		boolean		ok )
	{
		if ( ok ){

			NAT_status = NAT_CHECK_OK;

		}else{

			NAT_status	= NAT_CHECK_FAILED;
		}
	}

	protected void
	setNATStatus(
		byte		status )
	{
		NAT_status	= status;
	}

	@Override
	public byte
	getNATStatus()
	{
		return( NAT_status );
	}

	protected boolean
	isNATStatusBad()
	{
		return( NAT_status == NAT_CHECK_FAILED || NAT_status == NAT_CHECK_FAILED_AND_REPORTED );
	}

	protected void
	resolveAndCheckNAT()
	{
		// default values pending resolution

		ip_str 		= new String( ip );
		ip_bytes	= null;

		HostNameToIPResolver.addResolverRequest( ip_str, this );

			// a port of 0 is taken to mean that the client can't/won't receive incoming
			// connections - tr

		if ( tcp_port == 0 ){

			NAT_status = NAT_CHECK_FAILED_AND_REPORTED;

		}else{

				// only recheck if we haven't already ascertained the state

			if ( NAT_status == NAT_CHECK_UNKNOWN ){

				NAT_status	= NAT_CHECK_INITIATED;

				if ( !TRTrackerServerNATChecker.getSingleton().addNATCheckRequest( ip_str, getTCPPort(), this )){

					NAT_status = NAT_CHECK_DISABLED;
				}
			}
		}
	}

	@Override
	public void
	hostNameResolutionComplete(
		InetAddress	address )
	{
		if ( address != null ){

			ip_str 		= address.getHostAddress();

			ip_bytes	= address.getAddress();
		}
	}

	protected long
	getLastContactTime()
	{
		return( last_contact_time );
	}

	protected boolean
	getDownloadCompleted()
	{
		return( download_completed );
	}

	protected void
	setDownloadCompleted()
	{
		download_completed	= true;
	}

	@Override
	public boolean
	isBiased()
	{
		return( biased );
	}

	@Override
	public void
	setBiased(
		boolean	_biased )
	{
		biased	= _biased;
	}

	@Override
	public HashWrapper
	getPeerId()
	{
		return( peer_id );
	}

	@Override
	public byte[]
	getPeerID()
	{
		return( peer_id.getBytes());
	}

	protected int
	getKeyHashCode()
	{
		return( key_hash_code );
	}

	@Override
	public byte[]
	getIPAsRead()
	{
		return( ip );
	}

	@Override
	public String
	getIPRaw()
	{
		return( new String(ip));
	}

		/**
		 * If asynchronous resolution of the address is required, this will return
		 * the non-resolved address until the async process completes
		 */

	@Override
	public String
	getIP()
	{
		return( ip_str );
	}

	protected boolean
	isIPOverride()
	{
		return( ip_override );
	}

		/**
		 * This will return in resolution of the address is not complete or fails
		 * @return
		 */

	@Override
	public byte[]
	getIPAddressBytes()
	{
		return( ip_bytes );
	}

	@Override
	public int
	getTCPPort()
	{
		return( tcp_port&0xffff );
	}

	@Override
	public int
	getUDPPort()
	{
		return( udp_port&0xffff );
	}

	@Override
	public int
	getHTTPPort()
	{
		return( http_port&0xffff );
	}

	@Override
	public byte
	getCryptoLevel()
	{
		return( crypto_level );
	}

	@Override
	public byte
	getAZVer()
	{
		return( az_ver );
	}

	@Override
	public int
	getUpSpeed()
	{
		return( up_speed&0xffff );
	}

	@Override
	public DHTNetworkPosition
	getNetworkPosition()
	{
		return( network_position );
	}

	protected void
	setTimeout(
		long		_now,
		long		_timeout )
	{
		last_contact_time	= _now;

		timeout				= _timeout;
	}

	protected long
	getTimeout()
	{
		return( timeout );
	}

	@Override
	public int
	getSecsToLive()
	{
		return((int)(( timeout - SystemTime.getCurrentTime())/1000 ));
	}

	protected void
	setStats(
		long		_uploaded,
		long		_downloaded,
		long		_amount_left )
	{
		uploaded	= _uploaded;
		downloaded	= _downloaded;
		amount_left	= _amount_left;
	}

	@Override
	public long
	getUploaded()
	{
		return( uploaded );
	}

	@Override
	public long
	getDownloaded()
	{
		return( downloaded );
	}

	@Override
	public long
	getAmountLeft()
	{
		return( amount_left );
	}

	@Override
	public boolean
	isSeed()
	{
		return( amount_left == 0 );
	}

	@Override
	public void
	setUserData(
		Object		key,
		Object		data )
	{
		if ( user_data == null ){

			user_data = new Object[]{ key, data };

		}else if ( user_data instanceof Object[]){

			Object[]	x = (Object[])user_data;

			if ( x[0] == key ){

				x[1] = data;

			}else{

				HashMap	map = new HashMap();

				user_data = map;

				map.put( x[0], x[1] );

				map.put( key, data );
			}
		}else{

			((Map)user_data).put( key, data );
		}
	}

	@Override
	public Object
	getUserData(
		Object		key )
	{
		if ( user_data == null ){

			return( null );

		}else if( user_data instanceof Object[]){

			Object[]	x = (Object[])user_data;

			if ( x[0] == key ){

				return( x[1] );

			}else{

				return( null );
			}
		}else{

			return(((Map)user_data).get(key));
		}
	}

	@Override
	public Map
	export()
	{
		Map map = new HashMap();

		map.put( "peer_id", 		peer_id.getBytes());
		map.put( "key_hash_code", 	new Long( key_hash_code ));
		map.put( "ip", 				ip );
		map.put( "ip_override",		new Long( ip_override?1:0 ));
		map.put( "tcp_port", 		new Long( tcp_port ));
		map.put( "udp_port", 		new Long( udp_port ));
		map.put( "http_port", 		new Long( http_port ));
		map.put( "crypto_level", 	new Long( crypto_level ));
		map.put( "az_ver", 			new Long( az_ver ));
		map.put( "ip_str", 			ip_str );
		if ( ip_bytes != null ){
			map.put( "ip_bytes", ip_bytes );
		}
		map.put( "NAT_status", 		new Long( NAT_status ));
		map.put( "timeout", 		new Long( timeout ));
		map.put( "uploaded", 		new Long( uploaded ));
		map.put( "downloaded", 		new Long( downloaded ));
		map.put( "amount_left", 	new Long( amount_left ));
		map.put( "last_contact_time", 	new Long( last_contact_time ));
		map.put( "download_completed", 	new Long( download_completed?1:0 ));
		map.put( "biased", 			new Long( biased?1:0 ));
		map.put( "up_speed", 		new Long( up_speed ));

		return( map );
	}

	public static TRTrackerServerPeerImpl
	importPeer(
		Map		map )
	{
		try{
			HashWrapper		peer_id			= new HashWrapper((byte[])map.get( "peer_id" ));
			int				key_hash_code	= ((Long)map.get( "key_hash_code" )).intValue();
			byte[]			ip				= (byte[])map.get( "ip" );
			boolean			ip_override		= ((Long)map.get( "ip_override" )).intValue()==1;
			short			tcp_port		= ((Long)map.get( "tcp_port" )).shortValue();
			short			udp_port		= ((Long)map.get( "udp_port" )).shortValue();
			short			http_port		= ((Long)map.get( "http_port" )).shortValue();
			byte			crypto_level	= ((Long)map.get( "crypto_level" )).byteValue();
			byte			az_ver			= ((Long)map.get( "az_ver" )).byteValue();
			String			ip_str			= new String( (byte[])map.get( "ip_str" ));
			byte[]			ip_bytes		= (byte[])map.get( "ip_bytes" );
			byte			NAT_status		= ((Long)map.get( "NAT_status" )).byteValue();
			long			timeout			= ((Long)map.get( "timeout" )).longValue();
			long			uploaded		= ((Long)map.get( "uploaded" )).longValue();
			long			downloaded		= ((Long)map.get( "downloaded" )).longValue();
			long			amount_left		= ((Long)map.get( "amount_left" )).longValue();
			long			last_contact_time	= ((Long)map.get( "last_contact_time" )).longValue();
			boolean			download_completed	= ((Long)map.get( "download_completed" )).intValue() == 1;
			boolean			biased			= ((Long)map.get( "biased" )).intValue() == 1;
			short			up_speed		= ((Long)map.get( "up_speed" )).shortValue();

			return(
				new TRTrackerServerPeerImpl(
						peer_id,
						key_hash_code,
						ip,
						ip_override,
						tcp_port,
						udp_port,
						http_port,
						crypto_level,
						az_ver,
						ip_str,
						ip_bytes,
						NAT_status,
						timeout,
						uploaded,
						downloaded,
						amount_left,
						last_contact_time,
						download_completed,
						biased,
						up_speed ));

		}catch( Throwable e ){

			return( null );
		}
	}

	protected String
	getString()
	{
		return( new String(ip) + ":" + getTCPPort() + "(" + new String(peer_id.getHash()) + ")" );
	}
}
