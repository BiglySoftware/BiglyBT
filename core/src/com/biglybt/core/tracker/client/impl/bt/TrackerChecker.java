/*
 * Created on 22 juil. 2003
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
package com.biglybt.core.tracker.client.impl.bt;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.tracker.client.impl.TRTrackerScraperResponseImpl;
import com.biglybt.core.util.*;

/**
 * @author Olivier
 *
 */
public class TrackerChecker implements AEDiagnosticsEvidenceGenerator, SystemTime.ChangeListener, TimerEventPerformer {
	private final static LogIDs LOGID = LogIDs.TRACKER;

	private static final Timer	tracker_timer = new Timer( "Tracker Scrape Timer", 32 );

  /** List of Trackers.
   * key = Tracker URL string
   * value = TrackerStatus object
   */
  private final HashMap       trackers;
  private final AEMonitor 	trackers_mon 	= new AEMonitor( "TrackerChecker:trackers" );

  /** TRTrackerScraperImpl object associated with this object.
   */
  private final TRTrackerBTScraperImpl    scraper;

	private long nextScrapeCheckOn;

  /** Initialize TrackerChecker.
   *
   * @note Since there is only one TRTrackerScraperImpl, there will only be one
   *       TrackerChecker instance.
   *
   */

  protected TrackerChecker(TRTrackerBTScraperImpl  _scraper) {
    scraper   = _scraper;

    trackers  = new HashMap();

    if ( !COConfigurationManager.getBooleanParameter("Tracker Client Scrape Total Disable")){

	     runScrapes();


    }

    AEDiagnostics.addWeakEvidenceGenerator( this );

    SystemTime.registerClockChangeListener( this );
  }


  /** Retrieves the last cached Scraper Response based on a TRTrackerClient's
   * current URL (announce-list entry or announce) and its torrent's hash.
   *
   * @return The cached scrape response.  Can be null.
   */
  protected
  TRTrackerScraperResponseImpl
  getHashData(
  	TRTrackerAnnouncer tracker_client)
  {
    try {
      return getHashData(tracker_client.getTrackerURL(),
                         tracker_client.getTorrent().getHashWrapper());

    } catch (TOTorrentException e) {
    	Debug.printStackTrace( e );
      return null;
    }
  }

  /** Retrieves the last cached Scraper Response based on a TOTorrent's
   * Announce URL (not announce-list) and hash.
   *
   * @return The cached scrape response.  Can be null.
   */
  protected TRTrackerScraperResponseImpl
  getHashData(
  	TOTorrent  	torrent,
	URL			target_url )
  {
    try {
      return getHashData(target_url==null?torrent.getAnnounceURL():target_url,
                         torrent.getHashWrapper());

    } catch(TOTorrentException e) {
    	Debug.printStackTrace( e );
      return null;
    }
  }

  /** Retrieves the last cached Scraper Response for the supplied tracker URL
   *  and hash. If no cache has exists for the hash, one is created.
   *
   * @return The cached scrape response.  Can be null.
   */
  protected TRTrackerScraperResponseImpl
  getHashData(
	URL trackerUrl,
    final HashWrapper hash)
  {
    // can be null when first called and url not yet set up...
    if ( trackerUrl == null ){
      return( null );
    }

    if ( trackerUrl.getHost().endsWith( ".dht" )){
    	
    	// can't scrape these (e.g. metadata downloads) !
    	
    	return( null );
    }
    
    TRTrackerScraperResponseImpl data = null;

    	// DON'T USE URL as a key in the trackers map, use the string version. If you
    	// use a URL then the "containsKey" method does a URL.equals test. This does not
    	// simply check on str equivalence, it tries to resolve the host name. this can
    	// result in significant hangs (several seconds....)

    String	url_str = trackerUrl.toString();

    TrackerStatus ts = null;

     try{
        trackers_mon.enter();

        ts = (TrackerStatus) trackers.get(url_str);

        if ( ts != null ){

	      data = ts.getHashData( hash );

	    }else{

	    	//System.out.println( "adding hash for " + trackerUrl + " : " + ByteFormatter.nicePrint(hashBytes, true));

	    	ts = new TrackerStatus(this, scraper.getScraper(),trackerUrl);

	        trackers.put(url_str, ts);

	        if( !ts.isTrackerScrapeUrlValid() ) {

		      	if (Logger.isEnabled()){
							Logger.log(new LogEvent(TorrentUtils.getDownloadManager(hash), LOGID,
									LogEvent.LT_ERROR, "Can't scrape using url '" + trackerUrl
											+ "' as it doesn't end in " + "'/announce', skipping."));
		      	}
	        }
	    }

    }finally{

        trackers_mon.exit();
    }

    	// do outside monitor to avoid deadlock situation as ts.addHash invokes
		// listeners....

    if ( data == null ){

    	 data = ts.addHash(hash);
    }

    return data;
  }

  protected TRTrackerScraperResponseImpl
  peekHashData(
  	TOTorrent  	torrent,
	URL			target_url )
  {
    try{
    	URL trackerUrl = target_url==null?torrent.getAnnounceURL():target_url;

    	if ( trackerUrl == null ){
    		return( null );
        }

        String	url_str = trackerUrl.toString();

        try{
            trackers_mon.enter();

            TrackerStatus  ts = (TrackerStatus) trackers.get(url_str);

            if ( ts != null ){

    	      return( ts.getHashData( torrent.getHashWrapper()));
            }
        }finally{

            trackers_mon.exit();
        }

    } catch(TOTorrentException e) {
    	Debug.printStackTrace( e );
    }

    return null;
  }

  /** Removes the scrape task and data associated with the TOTorrent's
   * Announce URL, announce-list data and hash.
   */
  protected void removeHash(TOTorrent torrent) {
    try{
      removeHash(torrent.getAnnounceURL().toString(), torrent.getHashWrapper());

      TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();

      for (int i=0;i<sets.length;i++){

      	URL[]	urls = sets[i].getAnnounceURLs();

      	for (int j=0;j<urls.length;j++){

      		removeHash(urls[j].toString(), torrent.getHashWrapper());
      	}
      }


    } catch (TOTorrentException e) {
    	Debug.printStackTrace( e );
    }
  }

  /** Removes the scrape task and data associated with the supplied tracker
   * URL and torrent hash.
   */
  protected void removeHash(String trackerUrl, HashWrapper hash) {

    TrackerStatus ts = (TrackerStatus) trackers.get(trackerUrl);
    if (ts != null){
      //System.out.println( "removing hash for " + trackerUrl );
      ts.removeHash(hash);
    }
  }

  /* Forced synchronous scrape of the supplied torrent.
   */
  protected void
  syncUpdate(
  	TOTorrent 	torrent,
	URL			target_url )
  {
    if (torrent == null){
      return;
    }

    try {
      HashWrapper hash = torrent.getHashWrapper();

      TrackerStatus matched_ts = null;

      try{
      	trackers_mon.enter();

        Iterator iter = trackers.values().iterator();

        while (iter.hasNext()){

          TrackerStatus ts = (TrackerStatus) iter.next();

          if ( 	target_url == null ||
          		target_url.toString().equals( ts.getTrackerURL().toString())){

	          Map hashmap = ts.getHashes();

		      try{
		    	  ts.getHashesMonitor().enter();

		          if ( hashmap.get( hash ) != null ){

		        	matched_ts	= ts;

		        	break;
		          }
		      }finally{

		    	  ts.getHashesMonitor().exit();
		      }
          }
        }
      }finally{

      	trackers_mon.exit();
      }

      if ( matched_ts != null ){

    	  matched_ts.updateSingleHash( hash, true, false );
      }
    }
    catch (Throwable e) {
      Debug.out( "scrape syncUpdate() exception", e );
    }
  }


	@Override
	public void perform(TimerEvent event) {
		runScrapes();
	}

  /** Loop indefinitely, waiting for the next scrape, and scraping.
   */

	TRTrackerBTScraperResponseImpl oldResponse;

  private void
  runScrapes()
  {
	  TRTrackerBTScraperResponseImpl nextResponseScraping = checkForNextScrape();

	  if (Logger.isEnabled() && nextResponseScraping != oldResponse && nextResponseScraping != null ) {
		  Logger.log(new LogEvent(
				  TorrentUtils.getDownloadManager(nextResponseScraping.getHash()),
				  LOGID,
				  LogEvent.LT_INFORMATION,
				  "Next scrape will be "
						  + nextResponseScraping.getURL()
						  + " in "
						  + ((nextResponseScraping.getNextScrapeStartTime() - SystemTime.getCurrentTime())/1000)
						  + " sec,type="
						  + (nextResponseScraping.getTrackerStatus().getSupportsMultipeHashScrapes()
								  ? "multi" : "single")
						  + ",active="+nextResponseScraping.getTrackerStatus().getNumActiveScrapes()));
	  }


	  long delay;

	  if (nextResponseScraping == null) {

		  delay = 60000; // nothing going on, recheck in a min

	  } else {

		  long scrape_time = nextResponseScraping.getNextScrapeStartTime();

		  long time_to_scrape = scrape_time - SystemTime.getCurrentTime()
				  + SystemTime.TIME_GRANULARITY_MILLIS;

		  if (time_to_scrape <= 0) {

			  if (nextResponseScraping.getTrackerStatus().getNumActiveScrapes() > 0) {
				  // check if done scraping every 2 seconds, if no other
				  // scrapes are scheduled.  If other scrapes are sceduled,
				  // we would have got them from checkForNextScrape()
				  delay = 2000;
			  } else {

				  try {
					  nextResponseScraping.getTrackerStatus().updateSingleHash(
							  nextResponseScraping.getHash(), false);

					  delay = 0; // pick up next scrape fairly quickly

				  } catch (Throwable e) {

					  Debug.printStackTrace(e);

					  delay = 30000;
				  }
			  }
		  } else {

			  delay = time_to_scrape;

			  if (delay > 30000) {
				  delay = 30000; // don't sleep too long in case new hashes are added etc.
			  }
		  }
	  }

	  nextScrapeCheckOn = SystemTime.getCurrentTime() + delay;
	  oldResponse = nextResponseScraping;

	  if ( !Logger.isClosingTakingTooLong()){
  			
		  tracker_timer.addEvent(nextScrapeCheckOn, this);
	  }
	}

  /** Finds the torrent that will be needing a scrape next.
   *
   */

  private TRTrackerBTScraperResponseImpl
  checkForNextScrape()
  {
		// search for the next scrape

		long earliestBlocked = Long.MAX_VALUE;
		TRTrackerBTScraperResponseImpl earliestBlockedResponse = null;
		long earliestNonBlocked = Long.MAX_VALUE;
		TRTrackerBTScraperResponseImpl earliestNonBlockedResponse = null;

		try {
			trackers_mon.enter();

			Iterator iter = trackers.values().iterator();

			while (iter.hasNext()) {

				TrackerStatus ts = (TrackerStatus) iter.next();

				if (!ts.isTrackerScrapeUrlValid()) {
					continue;
				}

				boolean hasActiveScrapes = ts.getNumActiveScrapes() > 0;

				Map hashmap = ts.getHashes();

				try {
					ts.getHashesMonitor().enter();

					Iterator iterHashes = hashmap.values().iterator();

					while (iterHashes.hasNext()) {

						TRTrackerBTScraperResponseImpl response = (TRTrackerBTScraperResponseImpl) iterHashes.next();

						if (response.getStatus() != TRTrackerScraperResponse.ST_SCRAPING) {
							long nextScrapeStartTime = response.getNextScrapeStartTime();

							if (hasActiveScrapes) {
								if (nextScrapeStartTime < earliestBlocked) {
									earliestBlocked = nextScrapeStartTime;
									earliestBlockedResponse = response;
								}
							} else {
								if (nextScrapeStartTime < earliestNonBlocked) {
									earliestNonBlocked = nextScrapeStartTime;
									earliestNonBlockedResponse = response;
								}
							}
						}
					}
				} finally {

					ts.getHashesMonitor().exit();
				}
			}
		} finally {

			trackers_mon.exit();
		}

		boolean hasEarlierBlockedScrape = earliestBlocked != Long.MAX_VALUE
				&& earliestBlocked < earliestNonBlocked;
		// If the earlist non-blocked scrape is still 2 seconds away,
		// return the blocked scrape with in hopes that it gets unblocked soon
		if (hasEarlierBlockedScrape
				&& earliestNonBlocked - SystemTime.getCurrentTime() > 2000) {
			return earliestBlockedResponse;
		} else {
			return earliestNonBlockedResponse;
		}
	}


  	@Override
	  public void
  	clockChangeDetected(
  		long	current_time,
  		long	offset )
  	{
  		if ( Math.abs( offset ) < 60*1000 ){

  			return;
  		}

	    try{
	    	trackers_mon.enter();

	    	Iterator iter = trackers.values().iterator();

	    	while (iter.hasNext()) {

	    		TrackerStatus ts = (TrackerStatus) iter.next();

	    		Map hashmap = ts.getHashes();

	    		try{
	    			ts.getHashesMonitor().enter();

	    			Iterator iterHashes = hashmap.values().iterator();

	    			while( iterHashes.hasNext() ) {

	    				TRTrackerBTScraperResponseImpl response = (TRTrackerBTScraperResponseImpl)iterHashes.next();

	    				long	time = response.getNextScrapeStartTime();

	    				if ( time > 0 ){

	    					response.setNextScrapeStartTime( time + offset );
	    				}
	    			}
	    		}finally{

	    			ts.getHashesMonitor().exit();
	    		}
	    	}
	    }finally{

	    	trackers_mon.exit();
	    }
  	}

	@Override
	public void
	clockChangeCompleted(
		long	current_time,
		long	offset )
	{
	}


	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "BTScraper - now = " + SystemTime.getCurrentTime());

		try{
			writer.indent();

		    try{
		    	trackers_mon.enter();

			    Iterator iter = trackers.entrySet().iterator();

			    while (iter.hasNext()){

			    	Map.Entry	entry = (Map.Entry)iter.next();

			        TrackerStatus 	ts = (TrackerStatus)entry.getValue();

			    	writer.println( "Tracker: " + ts.getString());

			        try{
			        	writer.indent();

			        	ts.getHashesMonitor().enter();

				        Map hashmap = 	ts.getHashes();

				        Iterator iter_hashes = hashmap.entrySet().iterator();

				        while (iter_hashes.hasNext()){

					    	Map.Entry	hash_entry = (Map.Entry)iter_hashes.next();

					    	TRTrackerBTScraperResponseImpl	response = (TRTrackerBTScraperResponseImpl)hash_entry.getValue();

					    	writer.println( response.getString());
				        }
			        }finally{

			        	ts.getHashesMonitor().exit();

			        	writer.exdent();
			        }
			    }
		    }finally{

		    	trackers_mon.exit();
		    }

		}finally{

			writer.exdent();
		}
	}

	public long getNextScrapeCheckOn() {
		return nextScrapeCheckOn;
	}
}
