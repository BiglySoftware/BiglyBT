/*
 * Created on Feb 1, 2007
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


package com.biglybt.core.peer.cache;

import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.download.DownloadManagerEnhancer;
import com.biglybt.core.download.EnhancedDownloadManager;
import com.biglybt.core.ipfilter.BannedIp;
import com.biglybt.core.ipfilter.IPFilterListener;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
// import com.biglybt.core.peer.cache.cachelogic.CLCacheDiscovery;

public class
CacheDiscovery
{
	private static final IpFilter ip_filter = IpFilterManagerFactory.getSingleton().getIPFilter();

	private static final CacheDiscoverer[] discoverers = {

		// No longer supported: new CLCacheDiscovery(),
	};

	private static Set<String>	cache_ips = Collections.synchronizedSet(new HashSet<String>());

	public static void
	initialise(
		final DownloadManagerEnhancer		dme )
	{

		ip_filter.addListener(
			new IPFilterListener()
			{
				@Override
				public void
				IPFilterEnabledChanged(
					boolean			is_enabled )
				{
				}

				@Override
				public boolean
				canIPBeBanned(
					String			ip )
				{
					return( canBan( ip ));
				}

				@Override
				public void
				IPBanned(
					BannedIp		ip )
				{
				}

				@Override
				public void
				IPBlockedListChanged(
					IpFilter	filter)
				{
				}

				@Override
				public boolean
				canIPBeBlocked(
					String 	ip,
					byte[] 	torrent_hash)
				{
					EnhancedDownloadManager dm = dme.getEnhancedDownload( torrent_hash );

					if ( dm == null ){

						return( true );
					}

					return( true );
				}
			});

		new AEThread2( "CacheDiscovery:ban checker", true )
			{
				@Override
				public void
				run()
				{
					BannedIp[] bans = ip_filter.getBannedIps();

					for (int i=0;i<bans.length;i++){

						String	ip = bans[i].getIp();

						if ( !canBan( ip )){

							ip_filter.unban( ip );
						}
					}
				}
			}.start();
	}

	private static boolean
	canBan(
		final String	ip )
	{
		if ( cache_ips.contains( ip )){

			return( false );
		}

		try{
			InetAddress address = HostNameToIPResolver.syncResolve( ip );

			final String host_address = address.getHostAddress();

			if ( cache_ips.contains( host_address )){

				return( false );
			}

				// reverse lookups can be very slow

			IPToHostNameResolver.addResolverRequest(
				ip,
				new IPToHostNameResolverListener()
				{
					@Override
					public void
					IPResolutionComplete(
						String 		result,
						boolean 	succeeded )
					{
						if ( Constants.isAzureusDomain( result )){

							cache_ips.add( host_address );

							ip_filter.unban( host_address, true );
						}
					}
				});

			return( true );

		}catch( Throwable e ){

			Debug.printStackTrace( e );

			return( true );
		}
	}

	public static CachePeer[]
	lookup(
		TOTorrent	torrent )
	{
		CachePeer[]	res;

		if ( discoverers.length == 0 ){

			res = new CachePeer[0];

		}else if ( discoverers.length == 1 ){

			res = discoverers[0].lookup( torrent );

		}else{

			List<CachePeer>	result = new ArrayList<>();

			for (int i=0;i<discoverers.length;i++){

				CachePeer[] peers = discoverers[i].lookup( torrent );

				Collections.addAll(result, peers);
			}

			res = result.toArray( new CachePeer[result.size()]);
		}

		for (int i=0;i<res.length;i++){

			String	ip = res[i].getAddress().getHostAddress();

			cache_ips.add( ip );

			ip_filter.unban( ip );
		}

		return( res );
	}

	public static CachePeer
	categorisePeer(
		byte[]					peer_id,
		final InetAddress		ip,
		final int				port )
	{
		for (int i=0;i<discoverers.length;i++){

			CachePeer	cp = discoverers[i].lookup( peer_id, ip, port );

			if ( cp != null ){

				return( cp );
			}
		}

		return( new CachePeerImpl( CachePeer.PT_NONE, ip, port ));
	}

	public static class
	CachePeerImpl
		implements CachePeer
	{
		private int				type;
		private InetAddress		address;
		private int				port;
		private long			create_time;
		private long			inject_time;
		private long			speed_change_time;
		private boolean			auto_reconnect	= true;

		public
		CachePeerImpl(
			int			_type,
			InetAddress	_address,
			int			_port )
		{
			type	= _type;
			address	= _address;
			port	= _port;

			create_time	= SystemTime.getCurrentTime();
		}

		@Override
		public int
		getType()
		{
			return( type );
		}

		@Override
		public InetAddress
		getAddress()
		{
			return( address );
		}

		@Override
		public int
		getPort()
		{
			return( port );
		}

		@Override
		public long
		getCreateTime(
			long	now )
		{
			if ( create_time > now ){

				create_time	= now;
			}

			return( create_time );
		}

		@Override
		public long
		getInjectTime(
			long	now )
		{
			if ( inject_time > now ){

				inject_time	= now;
			}

			return( inject_time );
		}

		@Override
		public void
		setInjectTime(
			long	time )
		{
			inject_time	= time;
		}

		@Override
		public long
		getSpeedChangeTime(
			long	now )
		{
			if ( speed_change_time > now ){

				speed_change_time	= now;
			}

			return( speed_change_time );
		}

		@Override
		public void
		setSpeedChangeTime(
			long	time )
		{
			speed_change_time	= time;
		}

		@Override
		public boolean
		getAutoReconnect()
		{
			return( auto_reconnect );
		}

		@Override
		public void
		setAutoReconnect(
			boolean		auto )
		{
			auto_reconnect	= auto;
		}

		@Override
		public boolean
		sameAs(
			CachePeer	other )
		{
			return(
					getType() == other.getType() &&
					getAddress().getHostAddress().equals( other.getAddress().getHostAddress()) &&
					getPort() == other.getPort());
		}

		@Override
		public String
		getString()
		{
			return( "type=" + getType() + ",address=" + getAddress() + ",port=" + getPort());
		}
	}
}
