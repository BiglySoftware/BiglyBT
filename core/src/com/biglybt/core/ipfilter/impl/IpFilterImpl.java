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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.ipfilter.*;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;

public class
IpFilterImpl
	implements IpFilter
{
	protected static final LogIDs LOGID = LogIDs.CORE;
	protected static final LogIDs LOGID_NWMAN = LogIDs.NWMAN;

	private final static int MAX_BLOCKS_TO_REMEMBER = 500;

	private static IpFilterImpl ipFilter;
	
	static final AEMonitor	class_mon	= new AEMonitor( "IpFilter:class" );

	private final IPAddressRangeManagerV4	range_manager_v4 = new IPAddressRangeManagerV4();
	private final IPAddressRangeManagerV6	range_manager_v6 = new IPAddressRangeManagerV6();

    //Map ip blocked -> matching range

	private final LinkedList		ipsBlocked;

	private int num_ips_blocked 			= 0;
	private int num_ips_blocked_loggable	= 0;

	private long	last_update_time;

	private final IPBannerImpl		ipBanner;

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

	  ipsBlocked = new LinkedList();

	  blockedListChangedDispatcher = new FrequencyLimitedDispatcher(
			  new AERunnable() {
				  
				  AsyncDispatcher disp = new AsyncDispatcher(1000);
				  
				  @Override
				  public void runSupport() {
					  disp.dispatch( AERunnable.create( ()->{
						  for ( IPFilterListener listener: listenerz ){
							  try {
								  listener.IPBlockedListChanged(IpFilterImpl.this);
							  } catch (Exception e) {
								  Debug.out(e);
							  }
						  }
					  }));
				  }
			  }, 10000);

	  ipFilterAutoLoader = new IpFilterAutoLoaderImpl(this);

	  ipBanner = new IPBannerImpl( this );
	  
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
			
			range_manager_v4.clearAllEntries();
			
			range_manager_v6.clearAllEntries();
			
			IpFilterManagerFactory.getSingleton().deleteAllDescriptions();
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
			
			List<Iterator<IpRangeImpl>> iters = 
					Arrays.asList( range_manager_v4.getEntries().iterator(), range_manager_v6.getEntries().iterator());
			
			for ( Iterator<IpRangeImpl> iter: iters ){
			
				while(iter.hasNext()){
					
					IpRangeImpl range = iter.next();
				  
				  if(range.isValid() && ! range.isSessionOnly()) {
					String description =  range.getDescription();
					String startIp = range.getStartIp();
					String endIp = range.getEndIp();
					Map mapRange = new HashMap();
					mapRange.put("description",description.getBytes( "UTF-8" ));
					mapRange.put("start",startIp);
					mapRange.put("end",endIp);
					if ( !range.isV4()){
						mapRange.put("type",2L);
					}
					filters.add(mapRange);
				  }
				}
			}

		  	FileOutputStream fos  = null;

	    	try {

	    		//  Open the file

	    		File filtersFile = FileUtil.getUserFile("filters.config");

	    		fos = FileUtil.newFileOutputStream(filtersFile);

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
				fin = FileUtil.newFileInputStream(filtersFile);
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
				  Number type = (Number)range.get( "type" );
				  
				  IpRangeImpl ipRange;
				  
				  if ( type == null || type.intValue() == 1 ){
					  
					  ipRange = new IpRangeV4Impl(description,startIp,endIp,false);
					  
				  }else{
					  
					  ipRange = new IpRangeV6Impl(description,startIp,endIp,false);
				  }

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
		  }
		}finally{

			class_mon.exit();
		}
		
	  	markAsUpToDate();

		Logger.log(new LogEvent(LOGID, (System.currentTimeMillis() - startTime)
				+ "ms to load all IP Filters"));
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

		  if( ipBanner.isBanned(ipAddress)){

			  return true;
		  }


		if ( !isEnabled()){

			return( false );
		}

	  	// never bounce the local machine (peer guardian has a range that includes it!)

	  if ( ipAddress.equals("127.0.0.1")){

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

	  InetAddress ia;
	  
	  try{
		  if ( HostNameToIPResolver.isNonDNSName(ipAddress)){
			  
			  return( false );
		  }
		  
		  ia = HostNameToIPResolver.syncResolve(ipAddress);
			
	  }catch( Throwable e ){
		  
		  Debug.out( e );
		  
		  return( false );
	  }
	  
	  IpRange	match;
	  
	  if ( ia instanceof Inet4Address ){
		  
		  match = range_manager_v4.isInRange((Inet4Address)ia );
		  
	  }else{
		  
		  match = range_manager_v6.isInRange((Inet6Address)ia );
	  }
	  
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
						Logger.log(new LogEvent(LOGID_NWMAN, LogEvent.LT_WARNING, "Ip Blocked : "
								+ ipAddress + ", in range : " + match));

		      return true;

	      }else{

		      if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID_NWMAN, LogEvent.LT_WARNING, "Ip Blocking Denied : "
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
					Logger.log(new LogEvent(LOGID_NWMAN, LogEvent.LT_WARNING, "Ip Blocked : "
							+ ipAddress + ", not in any range"));

		    return true;

	    }else{

		    if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID_NWMAN, LogEvent.LT_WARNING, "Ip Blocking Denied : "
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

		if( ipBanner.isBanned(ipAddress)){

			return true;
		}

		if ( !isEnabled()){

			return( false );
		}

	  	// never bounce the local machine (peer guardian has a range that includes it!)

	  if ( ipAddress.isLoopbackAddress() || ipAddress.isLinkLocalAddress() || ipAddress.isSiteLocalAddress()){

		  return( false );
	  }

	  	//never block lan local addresses

	  if ( AddressUtils.isLANLocalAddress( new InetSocketAddress( ipAddress, 0 )) == AddressUtils.LAN_LOCAL_YES ){

		  return false;
	  }


	  if ( torrent_hash != null ){

		  if ( excluded_hashes.containsKey( torrent_hash )){

			  return( false );
		  }
	  }

	  boolean allow = ip_filter_allow;

	  IpRange	match;
	  
	  if ( ipAddress instanceof Inet4Address ){
	  
		  match = range_manager_v4.isInRange((Inet4Address)ipAddress );

	  }else{
		  
		  match = range_manager_v6.isInRange((Inet6Address)ipAddress );
	  }
	  
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
						Logger.log(new LogEvent(LOGID_NWMAN, LogEvent.LT_WARNING, "Ip Blocked : "
								+ ipAddress + ", in range : " + match));

		      return true;

	      }else{

		      if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID_NWMAN, LogEvent.LT_WARNING, "Ip Blocking Denied: "
								+ ipAddress + ", in range : " + match));

		      return false;

	      }
	    }

	    return false;
	  }


	  if( allow ){

	    if ( addBlockedIP( new BlockedIpImpl(ipAddress.getHostAddress(),null, torrent_name, loggable), torrent_hash, loggable )){

		    if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID_NWMAN, LogEvent.LT_WARNING, "Ip Blocked : "
							+ ipAddress + ", not in any range"));

		    return true;
	    }else{

		    if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID_NWMAN, LogEvent.LT_WARNING, "Ip Blocking Denied : "
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

			try{
				InetAddress ia = HostNameToIPResolver.syncResolve(address);
				  
				return( checkExternalHandlers( torrent_hash, ia ));
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
		
		return( null );
	}

	protected IpRange
	checkExternalHandlers(
		byte[]			torrent_hash,
		InetAddress		address )
	{
		if ( external_handlers.size() > 0 ){

			Iterator it = external_handlers.iterator();

			while( it.hasNext()){

				if (((IpFilterExternalHandler)it.next()).isBlocked( torrent_hash, address )){

					String	ip = address.getHostAddress();

					if ( address instanceof Inet4Address ){
					
						return( new IpRangeV4Impl( "External handler", ip, ip, true ));
						
					}else{
						
						return( new IpRangeV6Impl( "External handler", (Inet6Address)address, true ));
					}
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

			List<IpRange> entries_v4 = range_manager_v4.getEntries();
			List<IpRange> entries_v6 = range_manager_v6.getEntries();
			
			List<IpRange>	entries = new ArrayList<>( entries_v4.size() + entries_v6.size());
			
			entries.addAll( entries_v4 );
			entries.addAll( entries_v6 );
			
			IpRange[]	res = new IpRange[entries.size()];

			entries.toArray( res );

			return( res );

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public IpRange
	createRange(
		int			addressType,
		boolean 	sessionOnly)
	{
		return ( addressType==1?new IpRangeV4Impl("","","",sessionOnly):new IpRangeV6Impl("","","",sessionOnly));
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

			IpRangeImpl r = (IpRangeImpl)range;
			
			r.setAddedToRangeList( false );

			if ( r.isV4()){
			
				range_manager_v4.removeRange((IpRangeV4Impl)range );

			}else{
				
				range_manager_v6.removeRange((IpRangeV6Impl)range );
			}
		}finally{

			class_mon.exit();
		}

		markAsUpToDate();
	}

	@Override
	public int 
	getNbRanges() 
	{
		return( range_manager_v4.getEntryCount() + range_manager_v6.getEntryCount());
	}

	protected void
	setValidOrNot(
		IpRange			range,
		boolean			valid )
	{
		IpRangeImpl r = (IpRangeImpl)range;

		try{
			class_mon.enter();

				// this is an optimisation to deal with the way safepeer validates stuff
				// before adding it in

			if ( !r.getAddedToRangeList()){

				return;
			}

		}finally{

			class_mon.exit();
		}
		
		if ( valid ){

			if ( r.isV4()){
			
				range_manager_v4.addRange((IpRangeV4Impl)range );

			}else{
				
				range_manager_v6.addRange((IpRangeV6Impl)range );
			}
		}else{

			if ( r.isV4()){
			
				range_manager_v4.removeRange((IpRangeV4Impl)range );
				
			}else{
				
				range_manager_v6.removeRange((IpRangeV6Impl)range );
			}
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

	protected void
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
	
	protected CopyOnWriteList<IPFilterListener>
	getListeners()
	{
		return( listenerz );
	}
	
		// banning methods
	
	protected void
	banListChanged()
	{
		for ( IPFilterListener listener: listenerz ){

			try{
				listener.IPBanListChanged( this );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}
	
	@Override
	public boolean 
	ban(
		String 		ipAddress, 
		String 		torrent_name, 
		boolean 	manual, 
		int 		for_mins )
	{
		return( ipBanner.ban( ipAddress, torrent_name, manual, for_mins ));
	}
	
	@Override
	public void 
	clearBannedIps()
	{
		ipBanner.clearBannedIps();
	}
	
	@Override
	public BannedIp[] 
	getBannedIps()
	{
		return( ipBanner.getBannedIps());
	}
	
	@Override
	public int 
	getNbBannedIps()
	{
		return( ipBanner.getNbBannedIps());
	}
	
	@Override
	public boolean 
	unban(
		String ipAddress )
	{
		return( ipBanner.unban(ipAddress ));
	}
	
	@Override
	public boolean 
	unban(
		String 		ipAddress, 
		boolean 	block )
	{
		return( ipBanner.unban( ipAddress, block ));
	}
}
