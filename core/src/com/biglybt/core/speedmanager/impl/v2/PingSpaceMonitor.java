package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.util.SystemTime;

/*
 * Created on Jun 29, 2007
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

public class PingSpaceMonitor
{

    PingSpaceMapper pingMap;
    long startTime;
    private static final long INTERVAL = 1000 * 60 * 5L; //five min interval.

    final int maxGoodPing;
    final int minBadPing;

    //ping counters
    int nBadPings,nGoodPings,nNeutralPings=0;

    //saturated mode counters
    int upNone,upLow,upMed,upHigh,upAtLimit=0;
    int downNone,downLow,downMed,downHigh,downAtLimit=0;

    TransferMode transferMode;

    //To get new limit back to application
    public static final int UPLOAD = 88;
    public static final int DOWNLOAD = 89;
    public static final int NONE = 0;

    boolean hasNewLimit=false;
    int newLimit=-2;
    int limitType=NONE;


    public PingSpaceMonitor(int _maxGoodPing,int _minBadPing, TransferMode mode)
    {
        maxGoodPing=_maxGoodPing;
        minBadPing=_minBadPing;

        reset(mode);
    }

    /**
     *
     * @param downRate -
     * @param upRate -
     */
    public void setCurrentTransferRates(int downRate, int upRate){
        pingMap.setCurrentTransferRates(downRate,upRate);
    }


    /**
     * Do a check and decide if the limits should be dropped.
     * @param lastMetricValue -
     * @param mode - the TransferMode
     * @return - true if the limits should be dropped.
     */
    public boolean addToPingMapData(int lastMetricValue,TransferMode mode){

        //reset if we are changing modes.
        if( transferMode==null ){
            transferMode=mode;
        }

        //reset everything if we change modes.
        if( !transferMode.equals(mode) ){
            reset(mode);
            transferMode = mode;
            return false;
        }

        transferMode = mode;

        if(lastMetricValue<maxGoodPing){
            nGoodPings++;
        }else if(lastMetricValue>minBadPing){
            nBadPings++;
        }else{
            nNeutralPings++;
        }

        pingMap.addMetricToMap(lastMetricValue);

        //If the interval is up, we need to either recommend new limits or reset.
        long curr = SystemTime.getCurrentTime();

        if( curr > startTime+INTERVAL ){

            boolean needLowerLimts = checkForLowerLimits();
            if( needLowerLimts ){

                //prepare the package for lowering the limit.
                if( transferMode.isConfTestingLimits() ){
                    reset(mode);
                    return false;
                }else if( transferMode.isDownloadMode() ){

                    //recommend a new downloading limit.
                    newLimit = pingMap.guessDownloadLimit();

                    SpeedManagerLogger.trace("PingSpaceMonitor -> guessDownloadLimit: newLimit="+newLimit);

                    //on cable modems uploads can be over-estimated by 3x.
                    int uploadLimitGuess = pingMap.guessUploadLimit();
                    SpeedManagerLogger.trace("PingSpaceMonitor -> guessUploadLimit: guessUploadLimit="+uploadLimitGuess);

                    //download limit cannot be less the 40k
                    if(newLimit<40960){
                        newLimit=40960;
                    }

                    hasNewLimit = true;
                    limitType = DOWNLOAD;
                    reset(mode);
                    return true;
                }else{

                    //only seeding mode is left recommend a new upload limit.
                    newLimit = pingMap.guessUploadLimit();

                    //upload limit cannot be less the 20k
                    if(newLimit<20480){
                        newLimit=20480;
                    }

                    hasNewLimit = true;
                    limitType = UPLOAD;
                    reset(mode);
                    return true;
                }
            }else{
                //No need for lower limits.
                reset(mode);
            }
        }//if

        return false;
    }


    //Simple test currently. 15% bad pings.
    private boolean checkForLowerLimits()
    {
        int totalPings = nGoodPings+nBadPings+nNeutralPings;

        float percentBad = (float)nBadPings/(float)totalPings;

        if(percentBad>0.15f){
            return true;
        }else{
            return false;
        }

    }

    public void reset(TransferMode mode){

        //log results.
        StringBuilder sb = new StringBuilder("ping-monitor:");
        sb.append("good=").append(nGoodPings).append(":");
        sb.append("bad=").append(nBadPings).append(":");
        sb.append("neutral=").append(nNeutralPings);

        SpeedManagerLogger.log( sb.toString() );

        //reset all the counters.
        nBadPings=nGoodPings=nNeutralPings=0;

        //saturated mode counters
        upNone=upLow=upMed=upHigh=upAtLimit=0;
        downNone=downLow=downMed=downHigh=downAtLimit=0;

        pingMap = new PingSpaceMapper(maxGoodPing,minBadPing);
        startTime = SystemTime.getCurrentTime();

        transferMode = mode;
    }


    /**
     * True if we have a new limit.
     * @return - true if there is a new limit.
     */
    boolean hasNewLimit(){
        return hasNewLimit;
    }

    public int getNewLimit(){
        return newLimit;
    }

    public int limitType(){
        return limitType;
    }

    /**
     * Call after getting new limits.
     */
    public void resetNewLimit(){
        hasNewLimit = false;
        newLimit = -2;
        limitType = NONE;
    }

}//class
