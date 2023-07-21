package com.biglybt.core.speedmanager.impl.v2;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.speedmanager.SpeedManagerPingSource;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.average.Average;

/*
 * Created on May 31, 2007
 * Created by Alan Snyder
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */


/**
 * This class manage cycling though the PingSources. It keep track of PingSource stats and
 * applies rules on if/when to cycle though a ping-source.
 *
 * #1) If the slowest ping-source is 10x the the best for a 1 min average. kick it.
 * #2) If a ping-source is slower then two combined sources (2x) for a 5 min average. then kick it.
 * #3) Every 30 minutes kick the slowest ping source and request a new one. Just to keep things fresh.
 *
 * Also maintain logic do determine if a new source is better then the previous one. (To determine
 * if these rules lead to good data.)
 *
 */
public class PingSourceManager
{
    //
    private final Map pingAverages = new HashMap(); //<Source,PingSourceStats>
    private long lastPingRemoval=0;
    private static final long TIME_BETWEEN_BAD_PING_REMOVALS = 2 * 60 * 1000; // two minutes.
    private static final long TIME_BETWEEN_SLOW_PING_REMOVALS = 5 * 60 * 1000;// fifteen minutes.
    private static final long TIME_BETWEEN_FORCED_CYCLE_REMOVALS = 30 * 60 * 1000;// thirty minutes.


    /**
     * Determine if we should drop any ping sources.
     * Sort them, if one significantly higher then the other two. then drop it.
     * @param sources - SpeedManagerPingSource[] inputs
     */
    public void checkPingSources(SpeedManagerPingSource[] sources){

        //if the long term average of one source is 10 the lowest and twice a large as the
        //two lowest then drop the highest at the moment. Also, don't force sources to
        //drop to frequently.

        //no sources.
        if( sources==null ){
            return;
        }

        //if we have only two sources then don't do this test.
        if( sources.length<3 ){
            return;
        }

        //Test for a very bad ping source. i.e. slowest source is 10x slower then the fastest,
        if( checkForBadPing(sources) ){
            return;
        }

        //Test for slower then average source. i.e. slowest source is 3x media.
        if( checkForSlowSource(sources) ){
            return;
        }

        //Even if everything is going well then force a change every 30 minutes.
        forcePingSourceChange(sources);

    }//checkPingSources


    /**
     * If one ping source is twice the fastest then replace it. Otherwise reset the timer.
     * @param sources -
     * @return - true is a source has been changed.
     */
    private boolean forcePingSourceChange(SpeedManagerPingSource[] sources){

        //We only apply this rule if nothing has been removed in the past 30 minutes.
        long currTime = SystemTime.getCurrentTime();
        if( currTime<lastPingRemoval+ TIME_BETWEEN_FORCED_CYCLE_REMOVALS){
            return false;
        }

        if(sources.length<3){
            return false;
        }

        //just find the slowest ping-source and remove it.
        SpeedManagerPingSource slowestSource = null;
        double slowestPing = 0.0;
        double fastestPing = 10000.0;

        int len = sources.length;
        for(int i=0; i<len; i++){
            PingSourceStats pss = (PingSourceStats) pingAverages.get(sources[i]);
            Average ave = pss.getHistory();
            double pingTime = ave.getAverage();

            //find slowest
            if( pingTime>slowestPing ){
                slowestPing = pingTime;
                slowestSource=sources[i];
            }

            //find sped of fastest.
            if( pingTime<fastestPing ){
                fastestPing = pingTime;
            }

        }//for

        //regardless of result, resetTimer the timer.
        resetTimer();
        //only replace the slowest if it is twice the fastest.
        if( slowestPing > 2*fastestPing ){
            if(slowestSource!=null){
                slowestSource.destroy();
                return true;
            }
        }

        return false;
    }//forcePingSourceChange

    /**
     * A slow source is something that is 2x the slower then the two fastest.
     * @param sources -
     * @return - true is a source has been removed.
     */
    private boolean checkForSlowSource(SpeedManagerPingSource[] sources){

        //We only apply this rule if nothing has been removed in the past 15 minutes.
        long currTime = SystemTime.getCurrentTime();
        if( currTime<lastPingRemoval+ TIME_BETWEEN_SLOW_PING_REMOVALS){
            return false;
        }

        SpeedManagerPingSource slowestSource = null;
        if( sources.length<3 ){
            return false;
        }

        double fastA = 10000.0;
        double fastB = 10000.0;
        double slowest = 0.0;
        int len = sources.length;
        for(int i=0; i<len; i++){
            PingSourceStats pss = (PingSourceStats) pingAverages.get(sources[i]);
            Average ave = pss.getHistory();
            double pingTime = ave.getAverage();

            //determine fastest or second fastest.
            if(pingTime<fastA){
                fastB=fastA;
                fastA=pingTime;
            }else if(pingTime<fastB){
                fastB=pingTime;
            }

            //determine slowest.
            if(pingTime>slowest){
                slowest = pingTime;
                slowestSource = sources[i];
                resetTimer();
            }
        }//for

        double sumFastest = fastA+fastB;

        boolean removedSource = false;
        if( sumFastest*2 < slowest ){
            //destroy this source. It is a bit too slow.
            if(slowestSource!=null){
                slowestSource.destroy();
                SpeedManagerLogger.log("dropping ping source: "+slowestSource.getAddress()+" for being 2x slower then two fastest.");
                removedSource = true;
                resetTimer();
            }
        }//if

        return removedSource;
    }//checkForSlowSource

    /**
     * If the slowest ping in 10x the fastest then remove it.
     * @param sources -
     * @return - true is a source has been removed.
     */
    private boolean checkForBadPing(SpeedManagerPingSource[] sources) {

        //if we just recently removed a ping source then wait.
        long currTime = SystemTime.getCurrentTime();
        if( currTime<lastPingRemoval+ TIME_BETWEEN_BAD_PING_REMOVALS){
            return false;
        }

        double highestLongTermPing=0.0;
        SpeedManagerPingSource highestSource=null;
        double lowestLongTermPing=10000.0;

        int len = sources.length;
        for(int i=0; i<len; i++){
            PingSourceStats pss = (PingSourceStats) pingAverages.get(sources[i]);

            if ( pss == null ){
            	continue;
            }
            Average a = pss.getLongTermAve();
            double avePingTime = a.getAverage();

            //is this a new highest value?
            if( avePingTime>highestLongTermPing ){
                highestLongTermPing = avePingTime;
                highestSource = sources[i];
            }

            //is this a new lowest value?
            if( avePingTime<lowestLongTermPing ){
                lowestLongTermPing = avePingTime;
            }
        }//for

        boolean removedSource = false;
        //if the highest value is 8x the lowest then find another source.
        if( lowestLongTermPing*8 < highestLongTermPing ){
            //remove the slow source we will get a new one to replace it.
            if( highestSource!=null ){
                SpeedManagerLogger.log("dropping ping source: "+highestSource.getAddress()+" for being 8x greater then min source.");
                highestSource.destroy();
                removedSource = true;
                resetTimer();
            }
        }//if

        return removedSource;
    }


    public void pingSourceFound(SpeedManagerPingSource source, boolean is_replacement){
        PingSourceStats pss = new PingSourceStats(source);
        pingAverages.put(source,pss);
    }

    public void pingSourceFailed(SpeedManagerPingSource source) {
        if( pingAverages.remove(source)==null){
            SpeedManagerLogger.log("didn't find source: "+source.getAddress().getHostName());
        }
    }

    public void addPingTime(SpeedManagerPingSource source){

        if(source==null){
            return;
        }

        PingSourceStats pss = (PingSourceStats) pingAverages.get(source);

        if(pss==null){
            pingSourceFound(source,false);
            pss = (PingSourceStats) pingAverages.get(source);
            SpeedManagerLogger.trace("added new source from addPingTime.");
        }

        int pingTime = source.getPingTime();
        if(pingTime>0){
            pss.addPingTime( source.getPingTime() );
        }

    }//addPingTime

    /**
     * After a ping-source has been removed, need to resetTimer the timer.
     */
    private void resetTimer(){
        lastPingRemoval = SystemTime.getCurrentTime();
    }

}
