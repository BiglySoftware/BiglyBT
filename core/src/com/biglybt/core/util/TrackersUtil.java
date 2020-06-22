/*
 * File    : TrackersUtil.java
 * Created : 7 nov. 2003 12:09:56
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

package com.biglybt.core.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

/**
 * @author Olivier
 *
 */
public class TrackersUtil {

  private List<String> trackers;
  private Map<String,List<List<String>>> multiTrackers;
  private Map<String,Map> webseeds;

  private static TrackersUtil 	instance;
  private static final AEMonitor		class_mon 	= new AEMonitor( "TrackersUtil:class" );



  private TrackersUtil() {
    trackers = new ArrayList<>();
    multiTrackers = new HashMap<>();
    webseeds = new HashMap<>();
    loadList();
  }


  public static TrackersUtil getInstance() {
  	try{
  		class_mon.enter();

  		if(instance == null)
  			instance = new TrackersUtil();
  		return instance;

  	}finally{

  		class_mon.exit();
  	}
  }

  public List<String> getTrackersList() {
    if(trackers != null)
      return new ArrayList<>(trackers);
    else
      return null;
  }

  public void addTracker(String trackerAnnounceUrl) {
    if(trackers.contains(trackerAnnounceUrl))
      return;
    trackers.add(0,trackerAnnounceUrl);
    saveList();
  }

  public void addMultiTracker(String configName, List<List<String>> groups) {
    multiTrackers.put(configName,groups);
    saveList();
  }

  public void removeMultiTracker(String configName) {
    multiTrackers.remove(configName);
    saveList();
  }

  public Map<String,List<List<String>>> getMultiTrackers() {
    return new HashMap<>(multiTrackers);
  }
  public void addWebSeed(String configName, Map ws) {
	  webseeds.put(configName,ws);
	  saveList();
  }

  public void removeWebSeed(String configName) {
	  webseeds.remove(configName);
	  saveList();
  }

  public Map<String,Map> getWebSeeds() {
	  return new HashMap<>(webseeds);
  }

  public void clearAllTrackers(boolean save) {
	  trackers = new ArrayList<>();
	  multiTrackers = new HashMap<>();
	  webseeds = new HashMap<>();
	  if (save) {saveList();}
  }

  private void loadList() {
    File fTrackers = FileUtil.getUserFile("trackers.config");
    if(fTrackers.exists() && fTrackers.isFile()) {
      FileInputStream fin = null;
      BufferedInputStream bin = null;
      try {
        fin = FileUtil.newFileInputStream(fTrackers);
        bin = new BufferedInputStream(fin, 8192);
        Map map = BDecoder.decode(bin);
        List list = (List) map.get("trackers");
        if(list != null) {
	        Iterator iter = list.iterator();
	        while(iter.hasNext()) {
	          String tracker =  new String((byte[])iter.next());
	          trackers.add(tracker);
	        }
        }
        Map mapMT = (Map) map.get("multi-trackers");
        if(mapMT != null) {
          Iterator iter = mapMT.keySet().iterator();
          while(iter.hasNext()) {
            String configName =  (String) iter.next();
            List groups = (List) mapMT.get(configName);
            List resGroups = new ArrayList(groups.size());
            Iterator iterGroups = groups.iterator();
            while(iterGroups.hasNext()) {
              List theseTrackers = (List) iterGroups.next();
              List resTrackers = new ArrayList(theseTrackers.size());
              Iterator iterTrackers = theseTrackers.iterator();
              while(iterTrackers.hasNext()) {
                String tracker = new String((byte[]) iterTrackers.next());
                resTrackers.add(tracker);
              }
              resGroups.add(resTrackers);
            }
            this.multiTrackers.put(configName,resGroups);
          }
        }
        webseeds = (Map)map.get( "webseeds" );

        if ( webseeds == null ){
        	webseeds = new HashMap();
        }else{
        	BDecoder.decodeStrings( webseeds );
        }
      } catch(Exception e) {

      	Debug.printStackTrace( e );

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
	  }
    }
  }

  private void saveList() {
    Map map = new HashMap();
    map.put("trackers",trackers);
    map.put("multi-trackers",multiTrackers);
    map.put("webseeds",webseeds );
    FileOutputStream fos = null;
    try {
      //  Open the file
      File fTrackers = FileUtil.getUserFile("trackers.config");
      fos = FileUtil.newFileOutputStream(fTrackers);
      fos.write(BEncoder.encode(map));
      fos.close();
    } catch (Exception e) {
    	Debug.printStackTrace( e );
    } finally{
		if ( fos != null ){
			try{
				fos.close();
			}catch( Throwable e ){
			}
		}
    }
  }




}
