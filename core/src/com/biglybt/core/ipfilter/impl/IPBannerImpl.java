/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.ipfilter.impl;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.ipfilter.BannedIp;
import com.biglybt.core.ipfilter.IPFilterListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tracker.protocol.PRHelpers;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.TimerEventPeriodic;

public class 
IPBannerImpl
{
	private final static long BAN_IP_PERSIST_TIME	= 7*24*60*60*1000L;

		// share the class lock here as I refactored this out of IPFilterImpl and I'm not sure
		// if consistent class locking is required to avoid deadlocks...
	
	static final AEMonitor	class_mon	= IpFilterImpl.class_mon;

	private final IpFilterImpl					ipFilter;
	
	private final Map<Integer,BannedIpImpl>			bannedIps_v4		= new HashMap<>();
	private final Map<InetAddress,BannedIpImpl>		bannedIps_v6		= new HashMap<>();
	private final Map<String,BannedIpImpl>			bannedIps_other		= new HashMap<>();

	
	protected
	IPBannerImpl(
		IpFilterImpl		_ipFilter )
	{
		ipFilter = _ipFilter;
				
		try{
			loadBannedIPs();

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}
	
	
	protected void
	loadBannedIPs()
	{
		if ( !COConfigurationManager.getBooleanParameter("Ip Filter Banning Persistent" )){

			return;
		}

		try{
			class_mon.enter();

			Map	map = FileUtil.readResilientConfigFile( "banips.config" );

			List	ips = (List)map.get( "ips" );

			if ( ips != null ){

				long	now = SystemTime.getCurrentTime();

				for (int i=0;i<ips.size();i++){

					Map	entry = (Map)ips.get(i);

					String	ip 		= new String((byte[])entry.get("ip"));
					String	desc 	= new String((byte[])entry.get("desc"), "UTF-8");
					Long	ltime	= (Long)entry.get("time");

					long	time = ltime.longValue();

					boolean	drop	= false;

					if ( time > now ){

						time	= now;

					}else if ( now - time >= BAN_IP_PERSIST_TIME ){

						drop	= true;

					    if (Logger.isEnabled()){

								Logger.log(
									new LogEvent(
										IpFilterImpl.LOGID, LogEvent.LT_INFORMATION,
										"Persistent ban dropped as too old : "
											+ ip + ", " + desc));
					      }
					}

					if ( !drop ){

						BannedIpImpl bip = new BannedIpImpl( ip, desc, time );
						
						Object oa = decodeAddress( ip );

						if ( oa instanceof Integer ){
						
							bannedIps_v4.put((Integer)oa, bip );
							
						}else if ( oa instanceof InetAddress ){
							
							bannedIps_v6.put((InetAddress)oa, bip );
							
						}else{
							
							bannedIps_other.put((String)oa, bip );
						}
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);

		}finally{

			class_mon.exit();
		}
	}

	protected void
	saveBannedIPs()
	{
		if ( COConfigurationManager.getBooleanParameter("Ip Filter Banning Persistent" )){

			try{
				class_mon.enter();
	
				Map	map = new HashMap();
	
				List	ips = new ArrayList();
	
				List<Iterator<BannedIpImpl>>	its = 
						Arrays.asList( 
							bannedIps_v4.values().iterator(),
							bannedIps_v6.values().iterator(),
							bannedIps_other.values().iterator() );
							
				for ( Iterator<BannedIpImpl> it: its ){
	
					while( it.hasNext()){
		
						BannedIpImpl	bip = (BannedIpImpl)it.next();
		
						if ( bip.isTemporary()){
		
							continue;
						}
		
						Map	entry = new HashMap();
		
						entry.put( "ip", bip.getIp());
						entry.put( "desc", bip.getTorrentName().getBytes( "UTF-8" ));
						entry.put( "time", new Long( bip.getBanningTime()));
		
						ips.add( entry );
					}
				}
	
				map.put( "ips", ips );
	
				FileUtil.writeResilientConfigFile( "banips.config", map );
	
			}catch( Throwable e ){
	
				Debug.printStackTrace(e);
	
			}finally{
	
				class_mon.exit();
			}
		}
	}
	
	protected boolean
	isBanned(
		InetAddress ipAddress)
	{
		Object oa = decodeAddress( ipAddress );
		
		try{
			class_mon.enter();

			if ( oa instanceof Integer ){

				return( bannedIps_v4.containsKey((Integer)oa ));

			}else{

				return( bannedIps_v6.containsKey( ipAddress ));
			}

		}finally{

			class_mon.exit();
		}
	}

	protected boolean
	isBanned(
		String ipAddress)
	{
		Object oa = decodeAddress( ipAddress );

		try{
			class_mon.enter();

			if ( oa instanceof Integer ){

				return( bannedIps_v4.containsKey((Integer)oa ));

			}else if ( oa instanceof InetAddress ){
				
				return( bannedIps_v6.containsKey((InetAddress)oa ));
				
			}else{

				return( bannedIps_other.containsKey((String)oa ));
			}
		}finally{

			class_mon.exit();
		}
	}
	
	public boolean
	ban(
		String 		ipAddress,
		String		torrent_name,
		boolean		manual,
		int			for_mins )
	{
			// always allow manual bans through

		if ( !manual ){

			for ( IPFilterListener listener: ipFilter.getListeners()){

				try{
					if ( !listener.canIPBeBanned( ipAddress )){

						return( false );
					}

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		boolean	block_ban = false;

		List<BannedIpImpl>	new_bans = new ArrayList<>();

		boolean temporary = for_mins > 0;

		Object oa = decodeAddress( ipAddress );
		
		try{
			class_mon.enter();

			if ( oa instanceof Integer ){
				
				Integer address = (Integer)oa;
			
				if ( bannedIps_v4.get( address ) == null ){

					BannedIpImpl	new_ban = new BannedIpImpl( ipAddress, torrent_name, temporary );

					new_bans.add( new_ban );

					bannedIps_v4.put( address, new_ban );

						// block banning check for v4
					
					long	l_address = address;

			    	if ( l_address < 0 ){

						l_address += 0x100000000L;
			     	}

					long	start 	= l_address & 0xffffff00;
					long	end		= start+256;

					int	hits = 0;

					for (long i=start;i<end;i++){

						Integer	a = new Integer((int)i);

						if ( bannedIps_v4.get(a) != null ){

							hits++;
						}
					}

					int	hit_limit = COConfigurationManager.getIntParameter("Ip Filter Ban Block Limit");

					if ( hits >= hit_limit ){

						block_ban	= true;

						for (long i=start;i<end;i++){

							Integer	a = new Integer((int)i);

							if ( bannedIps_v4.get(a) == null ){

								BannedIpImpl	new_block_ban = new BannedIpImpl( PRHelpers.intToAddress((int)i), torrent_name + " [block ban]", temporary );

								new_bans.add( new_block_ban );

								bannedIps_v4.put( a, new_block_ban );
							}
						}
					}
				}
			}else if ( oa instanceof InetAddress ){
				
				InetAddress	address = (InetAddress)oa;
			
				if ( bannedIps_v6.get( address ) == null ){

					BannedIpImpl	new_ban = new BannedIpImpl( ipAddress, torrent_name, temporary );

					new_bans.add( new_ban );

					bannedIps_v6.put( address, new_ban );
				}
			}else{
				
				String	address = (String)oa;
				
				if ( bannedIps_other.get( address ) == null ){

					BannedIpImpl	new_ban = new BannedIpImpl( ipAddress, torrent_name, temporary );

					new_bans.add( new_ban );

					bannedIps_other.put( address, new_ban );
				}
			}
			
			if ( !new_bans.isEmpty()){
				
				if ( temporary ){
					
					for ( BannedIpImpl ban: new_bans ){
						
						addTemporaryBan( ban, for_mins );
					}
				}
				
				saveBannedIPs();
			}
		}finally{

			class_mon.exit();
		}

		for (int i=0;i<new_bans.size();i++){

			BannedIp entry	= (BannedIp)new_bans.get(i);

			for ( IPFilterListener listener: ipFilter.getListeners() ){

				try{
					listener.IPBanned( entry );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		if ( new_bans.size() > 0 ){
			
			ipFilter.banListChanged();
		}
		
		return( block_ban );
	}

	TimerEventPeriodic		unban_timer;
	final Map<Long,List<String>>	unban_map 			= new TreeMap<>();
	final Map<String,Long>		unban_map_reverse	= new HashMap<>();


	private void
	addTemporaryBan(
		BannedIpImpl		ban,
		int					mins )
	{
			// class_mon already held on entry

		if ( unban_timer == null ){

			unban_timer =
				SimpleTimer.addPeriodicEvent(
					"Unbanner",
					30*1000,
					new TimerEventPerformer() {

						@Override
						public void
						perform(
							TimerEvent event)
						{
							List<String> to_unban = new ArrayList<>();
							
							try{
								class_mon.enter();

								long now = SystemTime.getMonotonousTime();

								Iterator<Map.Entry<Long,List<String>>> it = unban_map.entrySet().iterator();

								while( it.hasNext()){

									Map.Entry<Long,List<String>> entry = it.next();

									if ( entry.getKey() <= now ){

										it.remove();

										for ( String ip: entry.getValue()){

											unban_map_reverse.remove( ip );

											to_unban.add( ip );	// unban invokes listeners, defer until out of class_mon
										}
									}else{

										break;
									}
								}

								if ( unban_map.size() == 0 ){

									unban_timer.cancel();

									unban_timer = null;
								}
							}finally{

								class_mon.exit();
							}
							
							for ( String ip: to_unban ){
								
								unban( ip );
							}
						}
					});
		}

		String 	ip 		= ban.getIp();

		long	expiry = SystemTime.getMonotonousTime() + mins*60*1000L;

		expiry = (( expiry + 29999 ) / 30000 ) * 30000;

		Long	old_expiry = unban_map_reverse.get( ip );

		if ( old_expiry != null ){

			List<String>	list = unban_map.get( old_expiry );

			if ( list != null ){

				list.remove( ip );

				if ( list.size() == 0 ){

					unban_map.remove( old_expiry );
				}
			}
		}

		unban_map_reverse.put( ip, expiry );

		List<String>	list = unban_map.get( expiry );

		if ( list == null ){

			list = new ArrayList<>(1);

			unban_map.put( expiry, list );
		}

		list.add( ip );
	}

	public BannedIp[]
	getBannedIps()
	{
		try{
			class_mon.enter();

			List<BannedIp> ips = new ArrayList<>( getNbBannedIps());
			
			ips.addAll( bannedIps_v4.values());
			ips.addAll( bannedIps_v6.values());
			ips.addAll( bannedIps_other.values());
			
			BannedIp[]	res = new BannedIp[ips.size()];

			ips.toArray( res );

			return( res );

		}finally{

			class_mon.exit();
		}
  	}

	public int
	getNbBannedIps()
	{
		return( bannedIps_v4.size() + bannedIps_v6.size() + bannedIps_other.size());
	}

	public void
	clearBannedIps()
	{
		try{
			class_mon.enter();

			bannedIps_v4.clear();
			bannedIps_v6.clear();
			bannedIps_other.clear();

			unban_map.clear();

			unban_map_reverse.clear();

			saveBannedIPs();

		}finally{

			class_mon.exit();
		}
				
		ipFilter.banListChanged();
	}

	public boolean
	unban(
		String ipAddress )
	{
		boolean hit = false;
		
		Object oa = decodeAddress( ipAddress );

		try{			
			class_mon.enter();

			BannedIpImpl entry;
			
			if ( oa instanceof Integer ){

				entry = bannedIps_v4.remove((Integer)oa);
				
			}else if ( oa instanceof InetAddress ){

				entry = bannedIps_v6.remove((InetAddress)oa);
				
			}else{
				
				entry = bannedIps_other.remove((String)oa);
			}
			
			if ( entry != null ){

				hit = true;
				
				if ( !entry.isTemporary()){

					saveBannedIPs();
				}
			}

		}finally{

			class_mon.exit();
		}
		
		if ( hit ){
			
			ipFilter.banListChanged();
		}
		
		return( hit );
	}

	public boolean
	unban(
		String 		ipAddress, 
		boolean 	block )
	{
		boolean	hit = false;

		Object oa = decodeAddress( ipAddress );
		
			// only support block bans for v4
		
		if ( block && oa instanceof Integer ){

			int	address = (Integer)oa;

			long	l_address = address;

	    	if ( l_address < 0 ){

				l_address += 0x100000000L;
	     	}

			long	start 	= l_address & 0xffffff00;
			long	end		= start+256;

			try{
				class_mon.enter();

				for (long i=start;i<end;i++){

					Integer	a = new Integer((int)i);

					if ( bannedIps_v4.remove(a) != null ){

						hit = true;
					}
				}

				if ( hit ){

					saveBannedIPs();
				}
			}finally{

				class_mon.exit();
			}


		}else{

			try{
				class_mon.enter();

				if ( oa instanceof Integer ){
					
					hit = bannedIps_v4.remove((Integer)oa) != null;
					
				}else if ( oa instanceof InetAddress ){
					
					hit = bannedIps_v6.remove((InetAddress)oa) != null;
					
				}else{
					
					hit = bannedIps_other.remove((String)oa) != null;
				}
				
				if ( hit ){
					
					hit = true;
					
					saveBannedIPs();
				}

			}finally{

				class_mon.exit();
			}
		}
		
		if ( hit ){
			
			ipFilter.banListChanged();
		}
		
		return( hit );
	}
	
	
	private Object
	decodeAddress(
		String		address )
	{
		try{
			if ( HostNameToIPResolver.isNonDNSName( address )){
				
				return( address );
			}
			
			return( decodeAddress( HostNameToIPResolver.syncResolve(address)));

		}catch( Throwable e ){

			return( address );
		}
	}

	private Object
	decodeAddress(
		InetAddress	address )
	{
		if ( address instanceof Inet4Address ){
		
			return( PRHelpers.addressToInt( address ));
			
		}else{
			
			return( address );
		}
	}
}
