/*
 * File    : OverallStatsImpl.java
 * Created : 2 mars 2004
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
package com.biglybt.core.stats.transfer.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.Core;
import com.biglybt.core.CoreComponent;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.transport.DHTTransportStats;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.plugin.dht.DHTPlugin;


/**
 * @author Olivier
 *
 */
public class
OverallStatsImpl
	extends GlobalManagerAdapter
	implements OverallStats
{

  	// sizes in MB

  private static final int	STATS_PERIOD	= 60*1000;		// 1 min
  private static final int	SAVE_PERIOD		= 10*60*1000;	// 10 min
  private static final int	SAVE_TICKS		= SAVE_PERIOD / STATS_PERIOD;

  final Core core;
  final GlobalManagerStats	gm_stats;

  private DHT[] dhts;

  private long totalDownloaded;
  private long totalUploaded;
  private long totalUptime;

  private long markTime;
  private long markTotalDownloaded;
  private long markTotalUploaded;
  private long markTotalUptime;

  private long totalDHTUploaded;
  private long totalDHTDownloaded;

  private long lastDownloaded;
  private long lastUploaded;
  private long lastUptime;

  private long lastDHTUploaded;
  private long lastDHTDownloaded;


  	// separate stats

  private long totalProtocolUploaded;
  private long totalDataUploaded;
  private long totalProtocolDownloaded;
  private long totalDataDownloaded;

  private long lastProtocolUploaded;
  private long lastDataUploaded;
  private long lastProtocolDownloaded;
  private long lastDataDownloaded;

  private long[]	lastSnapshot;

  private final long session_start_time = SystemTime.getCurrentTime();

  protected final AEMonitor	this_mon	= new AEMonitor( "OverallStats" );

  private int 	tick_count;

  private Map
  load(String filename)
  {
    return( FileUtil.readResilientConfigFile( filename ));
  }

  private Map load() {
	  return( load(Constants.APP_NAME + ".statistics"));
	}

  private void
  save(String filename,
		Map	map )
  {
  	try{
  		this_mon.enter();

  		FileUtil.writeResilientConfigFile( filename, map );

  	}finally{

  		this_mon.exit();
  	}
  }

  private void save( Map map ) {
	  save(Constants.APP_NAME + ".statistics", map);
	}

  private void validateAndLoadValues(
	Map	statisticsMap ) {

    lastUptime = SystemTime.getCurrentTime() / 1000;

    Map overallMap = (Map) statisticsMap.get("all");

    totalDownloaded = getLong( overallMap, "downloaded" );
	totalUploaded = getLong( overallMap, "uploaded" );
	totalUptime = getLong( overallMap, "uptime" );

	markTime			= getLong( overallMap, "mark_time" );
	markTotalDownloaded = getLong( overallMap, "mark_downloaded" );
	markTotalUploaded 	= getLong( overallMap, "mark_uploaded" );
	markTotalUptime 	= getLong( overallMap, "mark_uptime" );

	totalDHTDownloaded = getLong( overallMap, "dht_down" );
	totalDHTUploaded = getLong( overallMap, "dht_up" );

    totalProtocolUploaded 	= getLong( overallMap, "p_uploaded" );
    totalDataUploaded 		= getLong( overallMap, "d_uploaded" );
    totalProtocolDownloaded = getLong( overallMap, "p_downloaded" );
    totalDataDownloaded 	= getLong( overallMap, "d_downloaded" );


    long	current_total_d_received 	= gm_stats.getTotalDataBytesReceived();
    long	current_total_p_received 	= gm_stats.getTotalProtocolBytesReceived();

    long	current_total_d_sent		= gm_stats.getTotalDataBytesSent();
    long	current_total_p_sent		= gm_stats.getTotalProtocolBytesSent();

	lastSnapshot =
		 new long[]{ 	totalProtocolUploaded, totalDataUploaded,
			 			totalProtocolDownloaded, totalDataDownloaded,
			 			totalDHTUploaded, totalDHTDownloaded,
			 			current_total_p_sent, current_total_d_sent,
			 			current_total_p_received, current_total_d_received,
			 			0, 0 };
  }

  protected long
  getLong(
	Map		map,
	String	name )
  {
	  if ( map == null ){
		  return( 0 );
	  }

	  Object	obj = map.get(name);

	  if (!(obj instanceof Long )){
		return(0);
	  }

	  return(((Long)obj).longValue());
  }

  public
  OverallStatsImpl(
	Core _core,
	GlobalManagerStats	_gm_stats )
  {
	core		= _core;
	gm_stats	= _gm_stats;

    Map 	stats = load();

    validateAndLoadValues(stats);

	Set	types = new HashSet();

	types.add( CoreStats.ST_XFER_UPLOADED_PROTOCOL_BYTES );
	types.add( CoreStats.ST_XFER_UPLOADED_DATA_BYTES );
	types.add( CoreStats.ST_XFER_DOWNLOADED_PROTOCOL_BYTES );
	types.add( CoreStats.ST_XFER_DOWNLOADED_DATA_BYTES );

	CoreStats.registerProvider(
		types,
		new CoreStatsProvider()
		{
			@Override
			public void
			updateStats(
				Set		types,
				Map		values )
			{
			  	try{
			  		this_mon.enter();

			  		if ( core.isStarted()){

						if ( types.contains( CoreStats.ST_XFER_UPLOADED_PROTOCOL_BYTES )){

							values.put(
								CoreStats.ST_XFER_UPLOADED_PROTOCOL_BYTES,
								new Long( totalProtocolUploaded + ( gm_stats.getTotalProtocolBytesSent() - lastProtocolUploaded )));
						}
						if ( types.contains( CoreStats.ST_XFER_UPLOADED_DATA_BYTES )){

							values.put(
								CoreStats.ST_XFER_UPLOADED_DATA_BYTES,
								new Long( totalDataUploaded + ( gm_stats.getTotalDataBytesSent() - lastDataUploaded )));
						}
						if ( types.contains( CoreStats.ST_XFER_DOWNLOADED_PROTOCOL_BYTES )){

							values.put(
								CoreStats.ST_XFER_DOWNLOADED_PROTOCOL_BYTES,
								new Long( totalProtocolDownloaded + ( gm_stats.getTotalProtocolBytesReceived() - lastProtocolDownloaded )));
						}
						if ( types.contains( CoreStats.ST_XFER_DOWNLOADED_DATA_BYTES )){

							values.put(
								CoreStats.ST_XFER_DOWNLOADED_DATA_BYTES,
								new Long( totalDataDownloaded + ( gm_stats.getTotalDataBytesReceived() - lastDataDownloaded )));
						}
			  		}
			  	}finally{

			  		this_mon.exit();
			  	}
			}
		});

    core.addLifecycleListener(
    	new CoreLifecycleAdapter()
    	{
    		@Override
		    public void
    		componentCreated(
    			Core core,
    			CoreComponent component )
    		{
    			if ( component instanceof GlobalManager ){

    				GlobalManager	gm = (GlobalManager)component;

    				gm.addListener( OverallStatsImpl.this, false );

    			    SimpleTimer.addPeriodicEvent(
    			    	"OverallStats",
    			    	STATS_PERIOD,
    			    	new TimerEventPerformer()
    			    	{
    			    		@Override
					        public void
    			    		perform(TimerEvent event)
    			    		{
    			    			updateStats( false );
    			    		}
    			    	});
    			}
    		}
    	});

  }

	@Override
	public int getAverageDownloadSpeed() {
		if(totalUptime > 1) {
      return (int)(totalDownloaded / totalUptime);
    }
    return 0;
	}

	@Override
	public int getAverageUploadSpeed() {
    if(totalUptime > 1) {
      return (int)(totalUploaded / totalUptime);
    }
    return 0;
	}

	@Override
	public long getDownloadedBytes() {
		return totalDownloaded;
	}

	@Override
	public long getUploadedBytes() {
		return totalUploaded;
	}

	@Override
	public long getTotalUpTime() {
		return totalUptime;
  }

	@Override
	public long getDownloadedBytes(boolean since_mark )
	{
		if ( since_mark ){
			if ( markTotalDownloaded > totalDownloaded ){
				markTotalDownloaded = totalDownloaded;
			}
			return( totalDownloaded - markTotalDownloaded );
		}else{
			return( totalDownloaded );
		}
	}
	@Override
	public long getUploadedBytes(boolean since_mark )
	{
		if ( since_mark ){
			if ( markTotalUploaded > totalUploaded ){
				markTotalUploaded = totalUploaded;
			}
			return( totalUploaded - markTotalUploaded );
		}else{
			return( totalUploaded );
		}
	}
	@Override
	public long getTotalUpTime(boolean since_mark ){
		if ( since_mark ){
			if ( markTotalUptime > totalUptime ){
				markTotalUptime = totalUptime;
			}
			return( totalUptime - markTotalUptime );
		}else{
			return( totalUptime );
		}
	}

	@Override
	public int getAverageDownloadSpeed(boolean since_mark ){
		if ( since_mark ){
			long	up_time = getTotalUpTime( true );
			long	down	= getDownloadedBytes( true );
			if( up_time > 1 ){
				return (int)(down / up_time);
			}
			return 0;
		}else{
			return(getAverageDownloadSpeed());
		}
	}

	@Override
	public int getAverageUploadSpeed(boolean since_mark ){
		if ( since_mark ){
			long	up_time = getTotalUpTime( true );
			long	up 		= getUploadedBytes( true );
			if( up_time > 1 ){
				return (int)(up / up_time);
			}
			return 0;
		}else{
			return(getAverageUploadSpeed());
		}
	}

	@Override
	public long
	getMarkTime()
	{
		return( markTime );
	}

	@Override
	public void
	setMark(){
		markTime				= SystemTime.getCurrentTime();
		markTotalDownloaded 	= totalDownloaded;
		markTotalUploaded 		= totalUploaded;
		markTotalUptime 		= totalUptime;
	}

	@Override
	public void
	clearMark()
	{
		markTime				= 0;
		markTotalDownloaded 	= 0;
		markTotalUploaded 		= 0;
		markTotalUptime 		= 0;
	}

  @Override
  public long getSessionUpTime() {
    return (SystemTime.getCurrentTime() - session_start_time) / 1000;
  }

  @Override
  public void destroyInitiated() {
    updateStats( true );
  }

  protected long[]
  getLastSnapshot()
  {
  	try{
  		this_mon.enter();

  		return( lastSnapshot );

 	}finally{

  		this_mon.exit();
  	}
  }
  private void updateStats( boolean force )
  {
  	try{
  		this_mon.enter();

	    long current_time = SystemTime.getCurrentTime() / 1000;

	    if ( current_time < lastUptime ) {  //time went backwards
	      lastUptime = current_time;
	      return;
	    }

	    long	current_total_d_received 	= gm_stats.getTotalDataBytesReceived();
	    long	current_total_p_received 	= gm_stats.getTotalProtocolBytesReceived();

	    long	current_total_d_sent		= gm_stats.getTotalDataBytesSent();
	    long	current_total_p_sent		= gm_stats.getTotalProtocolBytesSent();

	    long	current_total_received 	= current_total_d_received + current_total_p_received;
	    long	current_total_sent		= current_total_d_sent + current_total_p_sent;

	    	// overall totals

	    totalDownloaded +=  current_total_received - lastDownloaded;
	    lastDownloaded = current_total_received;
	    if( totalDownloaded < 0 )  totalDownloaded = 0;

	    totalUploaded +=  current_total_sent - lastUploaded;
	    lastUploaded = current_total_sent;
	    if( totalUploaded < 0 )  totalUploaded = 0;

	    	// split totals

	    totalDataDownloaded +=  current_total_d_received - lastDataDownloaded;
	    lastDataDownloaded = current_total_d_received;
	    if( totalDataDownloaded < 0 )  totalDataDownloaded = 0;

	    totalProtocolDownloaded +=  current_total_p_received - lastProtocolDownloaded;
	    lastProtocolDownloaded = current_total_p_received;
	    if( totalProtocolDownloaded < 0 )  totalProtocolDownloaded = 0;

	    totalDataUploaded +=  current_total_d_sent - lastDataUploaded;
	    lastDataUploaded = current_total_d_sent;
	    if( totalDataUploaded < 0 )  totalDataUploaded = 0;

	    totalProtocolUploaded +=  current_total_p_sent - lastProtocolUploaded;
	    lastProtocolUploaded = current_total_p_sent;
	    if( totalProtocolUploaded < 0 )  totalProtocolUploaded = 0;

	    	// DHT

	    if ( dhts == null ){

		    try{
		    	PluginManager pm = core.getPluginManager();

		    	if ( pm.isInitialized()){

			        PluginInterface dht_pi = pm.getPluginInterfaceByClass( DHTPlugin.class );

			        if ( dht_pi == null ){

			        	dhts = new DHT[0];

			        }else{

			        	DHTPlugin plugin = (DHTPlugin)dht_pi.getPlugin();

			        	if ( !plugin.isInitialising()){

				        	if ( plugin.isEnabled()){

				        		dhts = ((DHTPlugin)dht_pi.getPlugin()).getDHTs();

				        	}else{

				        		dhts = new DHT[0];
				        	}
			        	}
			        }
		    	}
		    }catch( Throwable e ){

		    	dhts = new DHT[0];
		    }
	    }

	    long current_total_dht_up 	= 0;
	    long current_total_dht_down = 0;

	    if ( dhts != null ){

		    for ( DHT dht: dhts ){

		    	DHTTransportStats stats = dht.getTransport().getStats();

		    	current_total_dht_up 	+= stats.getBytesSent();
		    	current_total_dht_down 	+= stats.getBytesReceived();
		    }
	    }

	    totalDHTUploaded +=  current_total_dht_up - lastDHTUploaded;
	    lastDHTUploaded = current_total_dht_up;
	    if( totalDHTUploaded < 0 )  totalDHTUploaded = 0;

	    totalDHTDownloaded +=  current_total_dht_down - lastDHTDownloaded;
	    lastDHTDownloaded = current_total_dht_down;
	    if( totalDHTDownloaded < 0 )  totalDHTDownloaded = 0;

	    	// TIME

	    long delta = current_time - lastUptime;

	    if( delta > 100 || delta < 0 ) { //make sure the time diff isn't borked
	      lastUptime = current_time;
	      return;
	    }

	    if( totalUptime < 0 )  totalUptime = 0;

	    totalUptime += delta;
	    lastUptime = current_time;

		lastSnapshot =
			 new long[]{ 	totalProtocolUploaded, totalDataUploaded,
				 			totalProtocolDownloaded, totalDataDownloaded,
				 			totalDHTUploaded,
				 			totalDHTDownloaded,
				 			current_total_p_sent, current_total_d_sent,
				 			current_total_p_received, current_total_d_received,
				 			current_total_dht_up,
				 			current_total_dht_down };

	    HashMap	overallMap = new HashMap();

	    overallMap.put("downloaded",new Long(totalDownloaded));
	    overallMap.put("uploaded",new Long(totalUploaded));
	    overallMap.put("uptime",new Long(totalUptime));

	    overallMap.put("mark_time",new Long(markTime));
	    overallMap.put("mark_downloaded",new Long(markTotalDownloaded));
	    overallMap.put("mark_uploaded",new Long(markTotalUploaded));
	    overallMap.put("mark_uptime",new Long(markTotalUptime));


	    overallMap.put("dht_down",new Long(totalDHTDownloaded));
	    overallMap.put("dht_up",new Long(totalDHTUploaded));

	    overallMap.put("p_uploaded",new Long(totalProtocolUploaded));
	    overallMap.put("d_uploaded",new Long(totalDataUploaded));
	    overallMap.put("p_downloaded",new Long(totalProtocolDownloaded));
	    overallMap.put("d_downloaded",new Long(totalDataDownloaded));

	    Map	map = new HashMap();

	    map.put( "all", overallMap );

	    tick_count++;

	    if ( force || tick_count % SAVE_TICKS == 0 ){

	    	save( map );
	    }
  	}finally{

  		this_mon.exit();
  	}
  }
}
