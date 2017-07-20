/*
 * File    : IpFilterImpl.java
 * Created : 16-Oct-2003
 * By      : Olivier
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

package com.biglybt.core.ipfilter.impl;

/**
 * @author Olivier
 *
 */

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.ipfilter.*;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tracker.protocol.PRHelpers;
import com.biglybt.core.util.*;

public class
IpFilterImpl
	implements IpFilter
{
	private static final LogIDs LOGID = LogIDs.CORE;

	private final static long BAN_IP_PERSIST_TIME	= 7*24*60*60*1000L;

	private final static int MAX_BLOCKS_TO_REMEMBER = 500;

	private static IpFilterImpl ipFilter;
	static final AEMonitor2	class_mon	= new AEMonitor2( "IpFilter:class" );

	private final IPAddressRangeManager	range_manager = new IPAddressRangeManager();

	private final Map<Integer,BannedIpImpl>			bannedIps;

    //Map ip blocked -> matching range

	private final LinkedList		ipsBlocked;

	private int num_ips_blocked 			= 0;
	private int num_ips_blocked_loggable	= 0;

	private long	last_update_time;


	final CopyOnWriteList<IPFilterListener>	listenerz = new CopyOnWriteList<>(true);

	private final CopyOnWriteList<IpFilterExternalHandler>	external_handlers = new CopyOnWriteList<>();

	final FrequencyLimitedDispatcher blockedListChangedDispatcher;

	private final IpFilterAutoLoaderImpl ipFilterAutoLoader;

	boolean	ip_filter_enabled;
	boolean	ip_filter_allow;

	private ByteArrayHashMap<String>	excluded_hashes = new ByteArrayHashMap<>();

	{

	  COConfigurationManager.addAndFireParameterListeners(
			new String[] {
				"Ip Filter Allow",
				"Ip Filter Enabled"	},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName )
				{
					ip_filter_enabled 	= COConfigurationManager.getBooleanParameter( "Ip Filter Enabled" );
					ip_filter_allow 	= COConfigurationManager.getBooleanParameter( "Ip Filter Allow" );

					if ( parameterName != null ){

						if ( parameterName.equals( "Ip Filter Enabled" )){

							for ( IPFilterListener listener: listenerz ){

								listener.IPFilterEnabledChanged( ip_filter_enabled );
							}
						}
					}
				}
			});
	}

	private IpFilterImpl()
	{
	  ipFilter = this;

	  bannedIps = new HashMap();

	  ipsBlocked = new LinkedList();

		blockedListChangedDispatcher = new FrequencyLimitedDispatcher(
				new AERunnable() {
					@Override
					public void runSupport() {
						for ( IPFilterListener listener: listenerz ){
							try {
								listener.IPBlockedListChanged(IpFilterImpl.this);
							} catch (Exception e) {
								Debug.out(e);
							}
						}
					}
				}, 10000);

		ipFilterAutoLoader = new IpFilterAutoLoaderImpl(this);

		try{
		  loadBannedIPs();

	  }catch( Throwable e ){

		  Debug.printStackTrace(e);
	  }
	  try{

	  	loadFilters(true, true);

	  }catch( Exception e ){

	  	Debug.printStackTrace( e );
	  }

	  COConfigurationManager.addParameterListener(new String[] {
			"Ip Filter Allow",
			"Ip Filter Enabled"
		}, new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				markAsUpToDate();
			}
		});
	}

	public static IpFilter getInstance() {
		try{
			class_mon.enter();

			  if(ipFilter == null) {
				ipFilter = new IpFilterImpl();
			  }
			  return ipFilter;
		}finally{

			class_mon.exit();
		}
	}

	@Override
	public File
	getFile()
	{
		return( FileUtil.getUserFile("filters.config"));
	}

	@Override
	public void
	reload()
		throws Exception
	{
		reload(true);
	}

	@Override
	public void
	reloadSync()
		throws Exception
	{
		reload(false);
	}

	public void
	reload(boolean allowAsyncDownloading)
		throws Exception
	{
		if ( COConfigurationManager.getBooleanParameter( "Ip Filter Clear On Reload" )){
			range_manager.clearAllEntries();
		}
		markAsUpToDate();
		loadFilters(allowAsyncDownloading, false);
	}

	@Override
	public void
	save()

		throws Exception
	{
		try{
			class_mon.enter();

			Map map = new HashMap();


			List filters = new ArrayList();
			map.put("ranges",filters);
			List entries = range_manager.getEntries();
			Iterator iter = entries.iterator();
			while(iter.hasNext()) {
			  IpRange range = (IpRange) iter.next();
			  if(range.isValid() && ! range.isSessionOnly()) {
				String description =  range.getDescription();
				String startIp = range.getStartIp();
				String endIp = range.getEndIp();
				Map mapRange = new HashMap();
				mapRange.put("description",description.getBytes( "UTF-8" ));
				mapRange.put("start",startIp);
				mapRange.put("end",endIp);
				filters.add(mapRange);
			  }
			}

		  	FileOutputStream fos  = null;

	    	try {

	    		//  Open the file

	    		File filtersFile = FileUtil.getUserFile("filters.config");

	    		fos = new FileOutputStream(filtersFile);

	    		fos.write(BEncoder.encode(map));

	    	}finally{

		  		if ( fos != null ){

		  			fos.close();
		  		}
	    	}
		}finally{

			class_mon.exit();
		}
	}

	private void
	loadFilters(boolean allowAsyncDownloading, boolean loadOldWhileAsyncDownloading)
		throws Exception
	{
		long startTime = System.currentTimeMillis();
		ipFilterAutoLoader.loadOtherFilters(allowAsyncDownloading, loadOldWhileAsyncDownloading);

		if (getNbRanges() > 0) {
			Logger.log(new LogEvent(LOGID, (System.currentTimeMillis() - startTime)
					+ "ms for " + getNbRanges() + ". now loading norm"));
		}

		try{
			class_mon.enter();

		  List new_ipRanges = new ArrayList(1024);

		  FileInputStream fin = null;
		  BufferedInputStream bin = null;
		  try {
			//open the file
			File filtersFile = FileUtil.getUserFile("filters.config");
			if (filtersFile.exists()) {
				fin = new FileInputStream(filtersFile);
				bin = new BufferedInputStream(fin, 16384);
				Map map = BDecoder.decode(bin);
				List list = (List) map.get("ranges");
				if (list == null) {
					return;
				}
				Iterator iter = list.listIterator();
				while(iter.hasNext()) {
				  Map range = (Map) iter.next();
				  String description =  new String((byte[])range.get("description"), "UTF-8");
				  String startIp =  new String((byte[])range.get("start"));
				  String endIp = new String((byte[])range.get("end"));

				  IpRangeImpl ipRange = new IpRangeImpl(description,startIp,endIp,false);

				  ipRange.setAddedToRangeList(true);

				  new_ipRanges.add( ipRange );
				}
			}
		  }finally{

			if ( bin != null ){
				try{
				    bin.close();
				}catch( Throwable e ){
				}
			}
			if ( fin != null ){
				try{
					fin.close();
				}catch( Throwable e ){
				}
			}


		  	Iterator	it = new_ipRanges.iterator();

		  	while( it.hasNext()){

		  		((IpRange)it.next()).checkValid();
		  	}

		  	markAsUpToDate();
		  }
		}finally{

			class_mon.exit();
		}
		Logger.log(new LogEvent(LOGID, (System.currentTimeMillis() - startTime)
				+ "ms to load all IP Filters"));
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
										LOGID, LogEvent.LT_INFORMATION,
										"Persistent ban dropped as too old : "
											+ ip + ", " + desc));
					      }
					}

					if ( !drop ){

						int	int_ip = range_manager.addressToInt( ip );

						bannedIps.put( new Integer( int_ip ), new BannedIpImpl(ip, desc, time ));
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
		if ( !COConfigurationManager.getBooleanParameter("Ip Filter Banning Persistent" )){

			return;
		}

		try{
			class_mon.enter();

			Map	map = new HashMap();

			List	ips = new ArrayList();

			Iterator	it = bannedIps.values().iterator();

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

			map.put( "ips", ips );

			FileUtil.writeResilientConfigFile( "banips.config", map );

		}catch( Throwable e ){

			Debug.printStackTrace(e);

		}finally{

			class_mon.exit();
		}
	}

  @Override
  public boolean
  isInRange(
	  String ipAddress)
  {
    return isInRange( ipAddress, "", null );
  }


	@Override
	public boolean
	isInRange(
		String 	ipAddress,
		String 	torrent_name,
		byte[]	torrent_hash )
	{
		return( isInRange( ipAddress, torrent_name, torrent_hash, true ));
	}

	@Override
	public boolean
	isInRange(
		String ipAddress,
		String torrent_name,
		byte[] torrent_hash,
		boolean	loggable )
	{
		//In all cases, block banned ip addresses

		  if(isBanned(ipAddress)){

			  return true;
		  }


		if ( !isEnabled()){

			return( false );
		}

	  	// never bounce the local machine (peer guardian has a range that includes it!)

	  if ( ipAddress.equals("127.0.0.1")){

		  return( false );
	  }

	  	// don't currently support IPv6

	  if (ipAddress.contains(":")){

		  return( false );
	  }

	  	//never block lan local addresses

	  if ( AddressUtils.isLANLocalAddress( ipAddress ) == AddressUtils.LAN_LOCAL_YES ){

	  	return false;
	  }

	  if ( torrent_hash != null ){

		  if ( excluded_hashes.containsKey( torrent_hash )){

			  return( false );
		  }
	  }

	  boolean allow = ip_filter_allow;

	  IpRange	match = (IpRange)range_manager.isInRange( ipAddress );

	  if ( match == null || allow ){

		  IpRange explict_deny = checkExternalHandlers( torrent_hash, ipAddress );

		  if ( explict_deny != null ){

			  match	= explict_deny;

			  allow = false;
		  }
	  }

	  if(match != null) {
	    if(!allow) {

	      	// don't bounce non-public addresses (we can ban them but not filter them as they have no sensible
		  	// real filter address

		  if ( AENetworkClassifier.categoriseAddress( ipAddress ) != AENetworkClassifier.AT_PUBLIC ){

			  return( false );
		  }

	      if ( addBlockedIP( new BlockedIpImpl( ipAddress, match, torrent_name, loggable), torrent_hash, loggable )){

		      if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Ip Blocked : "
								+ ipAddress + ", in range : " + match));

		      return true;

	      }else{

		      if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Ip Blocking Denied : "
							+ ipAddress + ", in range : " + match));

		      return false;
	      }
	    }

	    return false;
	  }


	  if( allow ){

		if ( AENetworkClassifier.categoriseAddress( ipAddress ) != AENetworkClassifier.AT_PUBLIC ){

		  return( false );
		}

	    if ( addBlockedIP( new BlockedIpImpl(ipAddress,null, torrent_name, loggable), torrent_hash, loggable )){

		    if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Ip Blocked : "
							+ ipAddress + ", not in any range"));

		    return true;

	    }else{

		    if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Ip Blocking Denied : "
						+ ipAddress + ", not in any range"));

		    return false;
	    }
	  }

	  return false;
	}


	@Override
	public boolean
	isInRange(
		InetAddress ipAddress,
		String 		torrent_name,
		byte[] 		torrent_hash,
		boolean		loggable )
	{
		//In all cases, block banned ip addresses

		  if(isBanned(ipAddress)){

			  return true;
		  }

		if ( !isEnabled()){

			return( false );
		}

	  	// never bounce the local machine (peer guardian has a range that includes it!)

	  if ( ipAddress.isLoopbackAddress() || ipAddress.isLinkLocalAddress() || ipAddress.isSiteLocalAddress()){

		  return( false );
	  }

	  	// don't currently support IPv6

	  if ( ipAddress instanceof Inet6Address ){

		  return( false );
	  }

	  	//never block lan local addresses

	  if ( AddressUtils.isLANLocalAddress( ipAddress ) == AddressUtils.LAN_LOCAL_YES ){

		  return false;
	  }


	  if ( torrent_hash != null ){

		  if ( excluded_hashes.containsKey( torrent_hash )){

			  return( false );
		  }
	  }

	  boolean allow = ip_filter_allow;

	  IpRange	match = (IpRange)range_manager.isInRange( ipAddress );

	  if ( match == null || allow ){

		  	// get here if
		  	// 		match -> deny and we didn't match
		  	//		match -> allow and we did match

		  IpRange explicit_deny = checkExternalHandlers( torrent_hash, ipAddress );

		  if ( explicit_deny != null ){

			  	// turn this into a denial

			  match = explicit_deny;

			  allow = false;
		  }
	  }

	  if ( match != null ){

	    if(!allow) {

	      if ( addBlockedIP( new BlockedIpImpl(ipAddress.getHostAddress(),match, torrent_name, loggable), torrent_hash, loggable )){

		      if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Ip Blocked : "
								+ ipAddress + ", in range : " + match));

		      return true;

	      }else{

		      if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Ip Blocking Denied: "
								+ ipAddress + ", in range : " + match));

		      return false;

	      }
	    }

	    return false;
	  }


	  if( allow ){

	    if ( addBlockedIP( new BlockedIpImpl(ipAddress.getHostAddress(),null, torrent_name, loggable), torrent_hash, loggable )){

		    if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Ip Blocked : "
							+ ipAddress + ", not in any range"));

		    return true;
	    }else{

		    if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Ip Blocking Denied : "
						+ ipAddress + ", not in any range"));

		    return false;
	    }
	  }

	  return false;
	}

	protected IpRange
	checkExternalHandlers(
		byte[]	torrent_hash,
		String	address )
	{
		if ( external_handlers.size() > 0 ){

			Iterator it = external_handlers.iterator();

			while( it.hasNext()){

				if (((IpFilterExternalHandler)it.next()).isBlocked( torrent_hash, address )){

					return( new IpRangeImpl( "External handler", address, address, true ));
				}
			}
		}

		return( null );
	}

	protected IpRange
	checkExternalHandlers(
		byte[]		torrent_hash,
		InetAddress	address )
	{
		if ( external_handlers.size() > 0 ){

			Iterator it = external_handlers.iterator();

			while( it.hasNext()){

				if (((IpFilterExternalHandler)it.next()).isBlocked( torrent_hash, address )){

					String	ip = address.getHostAddress();

					return( new IpRangeImpl( "External handler", ip, ip, true ));
				}
			}
		}

		return( null );
	}

	private boolean
	addBlockedIP(
		BlockedIp 	ip,
		byte[]		torrent_hash,
		boolean		loggable )
	{
		if ( torrent_hash != null ){

			for ( IPFilterListener listener: listenerz ){

				try{
					if ( !listener.canIPBeBlocked( ip.getBlockedIp(), torrent_hash )){

						return( false );
					}

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		try{
			class_mon.enter();

			ipsBlocked.addLast( ip );

			num_ips_blocked++;

			if ( loggable ){

				num_ips_blocked_loggable++;
			}

			if( ipsBlocked.size() > MAX_BLOCKS_TO_REMEMBER ) {  //only "remember" the last few blocks occurrences

				ipsBlocked.removeFirst();
			}
		}finally{

			class_mon.exit();
		}

		return( true );
	}



	private boolean
	isBanned(
		InetAddress ipAddress)
	{
	  try{
	  	class_mon.enter();

		int	address = range_manager.addressToInt( ipAddress );

		Integer	i_address = new Integer( address );

	    return( bannedIps.get(i_address) != null );

	  }finally{

	  	class_mon.exit();
	  }
	}

	private boolean
	isBanned(
		String ipAddress)
	{
	  try{
	  	class_mon.enter();

		int	address = range_manager.addressToInt( ipAddress );

		Integer	i_address = new Integer( address );

	    return( bannedIps.get(i_address) != null );

	  }finally{

	  	class_mon.exit();
	  }
	}

	@Override
	public boolean
	getInRangeAddressesAreAllowed()
	{
	  return( ip_filter_allow );
	}

	@Override
	public void
	setInRangeAddressesAreAllowed(
		boolean	b )
	{
		COConfigurationManager.setParameter("Ip Filter Allow", b );
	}

	@Override
	public IpRange[]
	getRanges()
	{
		try{
			class_mon.enter();

			List entries = range_manager.getEntries();
			IpRange[]	res = new IpRange[entries.size()];

			entries.toArray( res );

			return( res );

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public IpRange
	createRange(boolean sessionOnly)
	{
		return ( new IpRangeImpl("","","",sessionOnly));
	}

	@Override
	public void
	addRange(
		IpRange	range )
	{
		try{
			class_mon.enter();

			((IpRangeImpl)range).setAddedToRangeList(true);

				// we only allow the validity check to take effect once its added to
				// the list of all ip ranges (coz safepeer creates lots of dummy entries
				// during refresh and then never adds them...

			range.checkValid();

		}finally{

			class_mon.exit();
		}

		markAsUpToDate();
	}

	@Override
	public void
	removeRange(
		IpRange	range )
	{
		try{
			class_mon.enter();

			((IpRangeImpl)range).setAddedToRangeList( false );

			range_manager.removeRange( range );

		}finally{

			class_mon.exit();
		}

		markAsUpToDate();
	}

	@Override
	public int getNbRanges() {
		List entries = range_manager.getEntries();

	  return entries.size();
	}

	protected void
	setValidOrNot(
		IpRange		range,
		boolean			valid )
	{
		try{
			class_mon.enter();

				// this is an optimisation to deal with the way safepeer validates stuff
				// before adding it in

			if ( !range.getAddedToRangeList()){

				return;
			}

		}finally{

			class_mon.exit();
		}

		if ( valid ){

			range_manager.addRange( range );

		}else{

			range_manager.removeRange( range );
		}
	}

	@Override
	public int
	getNbIpsBlocked()
	{
	  return num_ips_blocked;
	}

	@Override
	public int
	getNbIpsBlockedAndLoggable()
	{
	  return num_ips_blocked_loggable;
	}

	@Override
	public boolean
	ban(
		String 		ipAddress,
		String		torrent_name,
		boolean		manual )
	{
		return( ban( ipAddress, torrent_name, manual, 0 ));
	}

	@Override
	public boolean
	ban(
		String 		ipAddress,
		String		torrent_name,
		boolean		manual,
		int			for_mins )
	{
			// always allow manual bans through

		if ( !manual ){

			for ( IPFilterListener listener: listenerz ){

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

		List	new_bans = new ArrayList();

		boolean temporary = for_mins > 0;

		try{
			class_mon.enter();

			int	address = range_manager.addressToInt( ipAddress );

			Integer	i_address = new Integer( address );

			if ( bannedIps.get(i_address) == null ){

				BannedIpImpl	new_ban = new BannedIpImpl( ipAddress, torrent_name, temporary );

				new_bans.add( new_ban );

				bannedIps.put( i_address, new_ban );

				if ( temporary ){

					addTemporaryBan( new_ban, for_mins );
				}
					// check for block-banning, but only for real addresses

				if ( !UnresolvableHostManager.isPseudoAddress( ipAddress )){

					long	l_address = address;

			    	if ( l_address < 0 ){

						l_address += 0x100000000L;
			     	}

					long	start 	= l_address & 0xffffff00;
					long	end		= start+256;

					int	hits = 0;

					for (long i=start;i<end;i++){

						Integer	a = new Integer((int)i);

						if ( bannedIps.get(a) != null ){

							hits++;
						}
					}

					int	hit_limit = COConfigurationManager.getIntParameter("Ip Filter Ban Block Limit");

					if ( hits >= hit_limit ){

						block_ban	= true;

						for (long i=start;i<end;i++){

							Integer	a = new Integer((int)i);

							if ( bannedIps.get(a) == null ){

								BannedIpImpl	new_block_ban = new BannedIpImpl( PRHelpers.intToAddress((int)i), torrent_name + " [block ban]", temporary );

								new_bans.add( new_block_ban );

								bannedIps.put( a, new_block_ban );

								addTemporaryBan( new_block_ban, for_mins );
							}
						}
					}
				}

				saveBannedIPs();
			}
		}finally{

			class_mon.exit();
		}

		for (int i=0;i<new_bans.size();i++){

			BannedIp entry	= (BannedIp)new_bans.get(i);

			for ( IPFilterListener listener: listenerz ){

				try{
					listener.IPBanned( entry );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
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

											unban( ip );
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

	@Override
	public BannedIp[]
	getBannedIps()
	{
		try{
			class_mon.enter();

			BannedIp[]	res = new BannedIp[bannedIps.size()];

			bannedIps.values().toArray(res);

			return( res );

		}finally{

			class_mon.exit();
		}
  	}

	@Override
	public int
	getNbBannedIps()
	{
		return( bannedIps.size());
	}

	@Override
	public void
	clearBannedIps()
	{
		try{
			class_mon.enter();

			bannedIps.clear();

			unban_map.clear();

			unban_map_reverse.clear();

			saveBannedIPs();

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public void
	unban(String ipAddress)
	{
		try{
			class_mon.enter();

			int	address = range_manager.addressToInt( ipAddress );

			Integer	i_address = new Integer( address );

			BannedIpImpl entry = bannedIps.remove(i_address);

			if ( entry != null ){

				if ( !entry.isTemporary()){

					saveBannedIPs();
				}
			}

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public void
	unban(String ipAddress, boolean block)
	{
		if ( block ){

			int	address = range_manager.addressToInt( ipAddress );

			long	l_address = address;

	    	if ( l_address < 0 ){

				l_address += 0x100000000L;
	     	}

			long	start 	= l_address & 0xffffff00;
			long	end		= start+256;

			boolean	hit = false;

			try{
				class_mon.enter();

				for (long i=start;i<end;i++){

					Integer	a = new Integer((int)i);

					if ( bannedIps.remove(a) != null ){

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

				int	address = range_manager.addressToInt( ipAddress );

				Integer	i_address = new Integer( address );

				if ( bannedIps.remove(i_address) != null ){

					saveBannedIPs();
				}

			}finally{

				class_mon.exit();
			}
		}
	}


	@Override
	public BlockedIp[]
	getBlockedIps()
	{
		try{
			class_mon.enter();

			BlockedIp[]	res = new BlockedIp[ipsBlocked.size()];

			ipsBlocked.toArray(res);

			return( res );
		}finally{

			class_mon.exit();
		}
  	}

	@Override
	public void
	clearBlockedIPs()
	{
		try{
			class_mon.enter();

			ipsBlocked.clear();

			num_ips_blocked 			= 0;
			num_ips_blocked_loggable	= 0;

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public void
	addExcludedHash(
		byte[]		hash )
	{
		synchronized( this ){

			if ( excluded_hashes.containsKey( hash )){

				return;
			}

			ByteArrayHashMap<String>	copy = new ByteArrayHashMap<>();

			for ( byte[] k : excluded_hashes.keys()){

				copy.put( k, "" );
			}

			copy.put( hash, "" );

			excluded_hashes = copy;
		}

		markAsUpToDate();

		Logger.log( new LogEvent(LOGID, "Added " + ByteFormatter.encodeString( hash ) + " to excluded set" ));

	}

	@Override
	public void
	removeExcludedHash(
		byte[]		hash )
	{
		synchronized( this ){

			if ( !excluded_hashes.containsKey( hash )){

				return;
			}

			ByteArrayHashMap<String>	copy = new ByteArrayHashMap<>();

			for ( byte[] k : excluded_hashes.keys()){

				copy.put( k, "" );
			}

			copy.remove( hash );

			excluded_hashes = copy;
		}

		markAsUpToDate();

		Logger.log( new LogEvent(LOGID, "Removed " + ByteFormatter.encodeString( hash ) + " from excluded set" ));
	}

	@Override
	public boolean
	isEnabled()
	{
		return( ip_filter_enabled );
	}

	@Override
	public void
	setEnabled(
		boolean	enabled )
	{
		COConfigurationManager.setParameter( "Ip Filter Enabled", enabled );
	}

	@Override
	public void
	markAsUpToDate()
	{
	  	last_update_time	= SystemTime.getCurrentTime();

	  	blockedListChangedDispatcher.dispatch();
	}

	@Override
	public long
	getLastUpdateTime()
	{
		return( last_update_time );
	}

	@Override
	public long
	getTotalAddressesInRange()
	{
		return( range_manager.getTotalSpan());
	}

	@Override
	public void
	addListener(
		IPFilterListener	l )
	{
		listenerz.add( l );
	}

	@Override
	public void
	removeListener(
		IPFilterListener	l )
	{
		listenerz.remove( l );
	}

	@Override
	public void
	addExternalHandler(
		IpFilterExternalHandler h )
	{
		external_handlers.add( h );
	}

	@Override
	public void
	removeExternalHandler(
		IpFilterExternalHandler h )
	{
		external_handlers.remove( h );
	}
}
