/*
 * File    : TRTrackerResponsePeerImpl.java
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

package com.biglybt.core.tracker.client.impl;


import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponsePeer;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.StringInterner;

public class
TRTrackerAnnouncerResponsePeerImpl
	implements TRTrackerAnnouncerResponsePeer, Cloneable
{
	private final String		source;
	private final byte[]		peer_id;
	private final String		address;
	private final short		tcp_port;
	private final short		udp_port;
	private final short		http_port;
	private final short		crypto;
	private final byte		az_version;
	private final short		up_speed;

	private boolean cached;
	
	public
	TRTrackerAnnouncerResponsePeerImpl(
		String		_source,
		byte[]		_peer_id,
		String		_address,
		int			_tcp_port,
		int			_udp_port,
		int			_http_port,
		short		_crypto,
		byte		_az_version,
		int			_up_speed )
	{
		source		= StringInterner.intern(_source);
		peer_id		= _peer_id;
		address		= StringInterner.intern(_address);
		tcp_port	= (short)_tcp_port;
		udp_port	= (short)_udp_port;
		http_port	= (short)_http_port;
		crypto		= _crypto;
		az_version	= _az_version;
		up_speed	= (short)_up_speed;
	}

	public TRTrackerAnnouncerResponsePeerImpl
	getClone()
	{
		try{
			return((TRTrackerAnnouncerResponsePeerImpl)clone());
			
		}catch( Throwable e ){
			
			Debug.out( "eh?" );
			
			return( null );
		}
	}
	
	@Override
	public String
	getSource()
	{
		return( source );
	}

	@Override
	public byte[]
	getPeerID()
	{
		return( peer_id );
	}

	@Override
	public String
	getAddress()
	{
		return( address );
	}

	@Override
	public int
	getPort()
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
	public short
	getProtocol()
	{
		return( crypto );
	}

	@Override
	public byte
	getAZVersion()
	{
		return( az_version );
	}

	@Override
	public int
	getUploadSpeed()
	{
		return( up_speed&0xffff );
	}

	public String
	getKey()
	{
		return( address + ":" + tcp_port );
	}

	public void 
	setCached(
		boolean	_cached)
	{
		cached = _cached;
	}
	
	@Override
	public boolean 
	isCached()
	{
		return( cached );
	}
	
	@Override
	public int
	compareTo(
		TRTrackerAnnouncerResponsePeer other )
	{
		return( getString2( this ).compareTo( getString2( other )));
	}

	private String
	getString2(
		TRTrackerAnnouncerResponsePeer	peer )
	{
		return( peer.getAddress() + ":" + peer.getPort() + ":" + peer.getHTTPPort() + ":" + peer.getUDPPort());
	}

	public String
	getString()
	{
		return( "ip=" + address +
					(tcp_port==0?"":(",tcp_port=" + getPort())) +
					(udp_port==0?"":(",udp_port=" + getUDPPort())) +
					(http_port==0?"":(",http_port=" + getHTTPPort())) +
					",prot=" + crypto +
					(up_speed==0?"":(",up=" + getUploadSpeed())) +
					",ver=" + az_version );
	}
}
