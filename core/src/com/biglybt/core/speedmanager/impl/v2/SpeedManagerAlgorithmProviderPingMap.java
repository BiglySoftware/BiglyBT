package com.biglybt.core.speedmanager.impl.v2;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.speedmanager.SpeedManagerPingSource;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProvider;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProviderAdapter;
import com.biglybt.core.util.SystemTime;

/*
 * Created on Aug 5, 2007
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

public class SpeedManagerAlgorithmProviderPingMap
        implements SpeedManagerAlgorithmProvider, COConfigurationListener {

    private final SpeedManagerAlgorithmProviderAdapter adapter;

    private long timeSinceLastUpdate;

    private int consecutiveUpticks=0;
    private int consecutiveDownticks=0;

    //SpeedLimitMonitor
    private final SpeedLimitMonitor limitMonitor;

    //variables for display and vivaldi.
    //private int lastMetricValue;
    private float lastMetricValue;

    //use for ping.
    private static int numIntervalsBetweenCal = 2;
    private static boolean skipIntervalAfterAdjustment = true;

    private List pingTimeList = new ArrayList(); //<Float> //if we want to average variance pings.
    private boolean hadAdjustmentLastInterval = false;
    private int intervalCount = 0;

    //for managing ping sources.
    final PingSourceManager pingSourceManager = new PingSourceManager();
    //NOTE:: PingSourceManager is something that should be moved up one level!!!

    int sessionMaxUploadRate = 0;


    SpeedManagerAlgorithmProviderPingMap(SpeedManagerAlgorithmProviderAdapter _adapter){

        adapter = _adapter;

        SpeedManagerLogger.setAdapter( "pm", adapter );

        limitMonitor = new SpeedLimitMonitor( adapter.getSpeedManager());

        COConfigurationManager.addListener( this );

        SMInstance.init( _adapter );

        limitMonitor.initPingSpaceMap();
    }

    @Override
    public void
    destroy()
    {
    	COConfigurationManager.removeListener( this );
    }

    @Override
    public void
    configurationSaved(){

        try{

            limitMonitor.readFromPersistentMap();
            limitMonitor.updateFromCOConfigManager();

            skipIntervalAfterAdjustment=COConfigurationManager.getBooleanParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_WAIT_AFTER_ADJUST);
            numIntervalsBetweenCal=COConfigurationManager.getIntParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_INTERVALS_BETWEEN_ADJUST);

            limitMonitor.initPingSpaceMap();

            SpeedManagerLogger.trace("..VariancePingMap - configurationSaved called.");

        }catch( Throwable t ){
            SpeedManagerLogger.log(t.getMessage());
        }

    }//configurationSaved
    /**
     * Reset any state to start of day values
     */

    @Override
    public void reset() {
        log("reset");

        log("curr-data-m: curr-down-rate : curr-down-limit : down-capacity : down-bandwith-mode : down-limit-mode : curr-up-rate : curr-up-limit : up-capacity : upload-bandwidth-mode : upload-limit-mode : transfer-mode");

        log("new-limit:newLimit:currStep:signalStrength:multiple:currUpLimit:maxStep:uploadLimitMax:uploadLimitMin:transferMode" );

        log("consecutive:up:down");

        log("metric:value:type");

        log("user-comment:log");

        log("pin:upload-status,download-status,upload-unpin-timer,download-unpin-timer");

        log("limits:down-max:down-min:down-conf:up-max:up-min:up-conf");

        limitMonitor.resetPingSpace();
    }

    /**
     * Called periodically (see period above) to allow stats to be updated.
     */

    @Override
    public void updateStats() {

        //update some stats used in the UI.

        int currUploadLimit = adapter.getCurrentUploadLimit();
        int currDataUploadSpeed = adapter.getCurrentDataUploadSpeed();
        int currProtoUploadSpeed = adapter.getCurrentProtocolUploadSpeed();
        int upRateBitsPerSec = currDataUploadSpeed + currProtoUploadSpeed;

        int currDownLimit = adapter.getCurrentDownloadLimit();
        int downDataRate = adapter.getCurrentDataDownloadSpeed();
        int downProtoRate = adapter.getCurrentProtocolDownloadSpeed();
        int downRateBitsPerSec = downDataRate+downProtoRate;

        //update the bandwidth status
        limitMonitor.setDownloadBandwidthMode(downRateBitsPerSec,currDownLimit);
        limitMonitor.setUploadBandwidthMode(upRateBitsPerSec,currUploadLimit);

        //update the limits status.  (is it near a forced max or min?)
        limitMonitor.setDownloadLimitSettingMode(currDownLimit);
        limitMonitor.setUploadLimitSettingMode(currUploadLimit);

        limitMonitor.updateTransferMode();

        if( limitMonitor.isConfTestingLimits() ){
            limitMonitor.updateLimitTestingData(downRateBitsPerSec,upRateBitsPerSec);
        }

        //update ping maps
        limitMonitor.setCurrentTransferRates(downRateBitsPerSec,upRateBitsPerSec);

        //only for the UI.
        if( upRateBitsPerSec > sessionMaxUploadRate ){
            sessionMaxUploadRate = upRateBitsPerSec;
        }

        //"curr-data" ....
        logCurrentData(downRateBitsPerSec, currDownLimit, upRateBitsPerSec, currUploadLimit);
    }

    /**
     * log "curr-data" line to the AutoSpeed-Beta file.
     * @param downRate -
     * @param currDownLimit -
     * @param upRate -
     * @param currUploadLimit -
     */
    private void logCurrentData(int downRate, int currDownLimit, int upRate, int currUploadLimit) {
        StringBuilder sb = new StringBuilder("curr-data-m:"+downRate+":"+currDownLimit+":");
        sb.append( limitMonitor.getDownloadMaxLimit() ).append(":");
        sb.append(limitMonitor.getDownloadBandwidthMode()).append(":");
        sb.append(limitMonitor.getDownloadLimitSettingMode()).append(":");
        sb.append(upRate).append(":").append(currUploadLimit).append(":");
        sb.append( limitMonitor.getUploadMaxLimit() ).append(":");
        sb.append(limitMonitor.getUploadBandwidthMode()).append(":");
        sb.append(limitMonitor.getUploadLimitSettingMode()).append(":");
        sb.append(limitMonitor.getTransferModeAsString());

        SpeedManagerLogger.log( sb.toString() );
    }

    /**
     * Called when a new source of ping times has been found
     *
     * @param source -
     * @param is_replacement One of the initial sources or a replacement for a failed one
     */

    @Override
    public void pingSourceFound(SpeedManagerPingSource source, boolean is_replacement) {
        //We might not use ping source if the vivaldi data is available.
        log("pingSourceFound");
        //add a new ping source to the list.
        pingSourceManager.pingSourceFound(source, is_replacement);
    }

    /**
     * Ping source has failed
     *
     * @param source -
     */

    @Override
    public void pingSourceFailed(SpeedManagerPingSource source) {
        //Where does the vivaldi data for the chart come from.
        log("pingSourceFailed");

        pingSourceManager.pingSourceFailed(source);
    }


    /**
     * Called whenever a new set of ping values is available for processing
     *
     * @param sources -
     */

    @Override
    public void calculate(SpeedManagerPingSource[] sources) {

        limitMonitor.logPMDataEx();

        //Get new data to ping-source-manager.
        int len = sources.length;
        for(int i=0; i<len; i++){
            pingSourceManager.addPingTime( sources[i] );
            int pingTime = sources[i].getPingTime();

            //exclude ping-times of -1 which mess up the averages.
            if(pingTime>0){
                //pingTimeList.add( new Integer( sources[i].getPingTime() ) );
                intervalCount++;
            }//if
        }//for

        //if we are in a limit finding mode then don't even bother with this calculation.
        if( limitMonitor.isConfTestingLimits() ){

            if( limitMonitor.isConfLimitTestFinished() ){
                endLimitTesting();
                return;
            }else{
                //will increase the limit each cycle.
                SMUpdate ramp = limitMonitor.rampTestingLimit(
                                    adapter.getCurrentUploadLimit(),
                                    adapter.getCurrentDownloadLimit()
                    );
                logNewLimits( ramp );
                setNewLimits( ramp );
            }
        }//if - isConfTestingLimits


        long currTime = SystemTime.getCurrentTime();

        if( timeSinceLastUpdate==0 ){
            timeSinceLastUpdate=currTime;
        }

        //use the variance ping times instead.
        if ( calculatePingMetric() ){
            return;
        }

        log("metric:"+ lastMetricValue);
        logLimitStatus();

        //update the metric data
        //limitMonitor.addToPingMapData(lastMetricValue);

        float signalStrength = determineSignalStrength(lastMetricValue);

        //if are are NOT looking for limits and we have a signal then make an adjustment.
        if( signalStrength!=0.0f && !limitMonitor.isConfTestingLimits() ){
            hadAdjustmentLastInterval=true;

            float multiple = consectiveMultiplier();
            int currUpLimit = adapter.getCurrentUploadLimit();
            int currDownLimit = adapter.getCurrentDownloadLimit();

            limitMonitor.checkForUnpinningCondition();

            SMUpdate update = limitMonitor.modifyLimits(signalStrength,multiple,currUpLimit, currDownLimit);

            //log
            logNewLimits(update);

            //setting new
            setNewLimits( update );

        }else{
            hadAdjustmentLastInterval=false;

            //verify the limits. It is possible for the user to adjust the capacity down below the current limit, so check that condition here.
            int currUploadLimit = adapter.getCurrentUploadLimit();
            int currDownloadLimit = adapter.getCurrentDownloadLimit();
            if( !limitMonitor.areSettingsInSpec(currUploadLimit, currDownloadLimit) ){
                SMUpdate update = limitMonitor.adjustLimitsToSpec(currUploadLimit, currDownloadLimit);
                logNewLimits( update );
                setNewLimits( update );
            }

        }

        //determine if we need to drop a ping source.
        pingSourceManager.checkPingSources(sources);
    }

    private void endLimitTesting() {
        int downLimitGuess = limitMonitor.guessDownloadLimit();
        int upLimitGuess = limitMonitor.guessUploadLimit();

        SMUpdate update = limitMonitor.endLimitTesting(downLimitGuess,
                upLimitGuess );

        //print out the PingMap data to compare.
        limitMonitor.logPingMapData();

        //reset Ping Space Map for next round.
        limitMonitor.resetPingSpace();

        //log
        logNewLimits(update);
        //setting new
        setNewLimits( update );
    }


    /**
     * Log the limit status. Max, Min and Conf.
     * log("limits:down-max:down-min:down-conf:up-max:up-min:up-conf");
     */
    private void logLimitStatus(){

        StringBuilder msg = new StringBuilder();
        msg.append("limits:");
        msg.append(limitMonitor.getUploadMaxLimit()).append(":");
        msg.append(limitMonitor.getUploadMinLimit()).append(":");
        msg.append(limitMonitor.getUploadConfidence()).append(":");
        msg.append(limitMonitor.getDownloadMaxLimit()).append(":");
        msg.append(limitMonitor.getDownloadMinLimit()).append(":");
        msg.append(limitMonitor.getDownloadConfidence());

        SpeedManagerLogger.log( msg.toString() );
    }//logLimitStatus

    /**
     * Variance PingMap data is the metrics used. Calculate it here.
     * @return - true if should exit early from the calculate method.
     */
    private boolean calculatePingMetric() {
        //Don't count this data point, if we skip the next ping times after an adjustment.
        if(skipIntervalAfterAdjustment && hadAdjustmentLastInterval){
            hadAdjustmentLastInterval=false;
            pingTimeList = new ArrayList();
            intervalCount=0;
            return true;
        }

        //have we accululated enough data to make an adjustment?
        if( intervalCount < numIntervalsBetweenCal ){
            //get more data before making another calculation.
            return true;
        }

            //we have enough data. find the median ping time.
            lastMetricValue = (float)adapter.getPingMapper().getCurrentMetricRating();

            //we have now consumed this data. reset the counters.
            intervalCount=0;
            return false;
    }

    private void logNewLimits( SMUpdate update ) {
        if( update.hasNewUploadLimit ){
            int kbpsUpoadLimit = update.newUploadLimit/1024;
            log(" new up limit  : "+ kbpsUpoadLimit +" kb/s");
        }

        if( update.hasNewDownloadLimit ){
            int kpbsDownloadLimit = update.newDownloadLimit/1024;
            log(" new down limit: "+kpbsDownloadLimit+" kb/s");
        }
    }

    /**
     * Just update the limits.
     * @param update - SMUpdate
     */
    private void setNewLimits( SMUpdate update ){

        adapter.setCurrentUploadLimit( update.newUploadLimit );
        adapter.setCurrentDownloadLimit( update.newDownloadLimit );

    }


        private float determineSignalStrength(float lastMetric){
            //determine if this is an up-tick (+1), down-tick (-1) or neutral (0).

            //  range should be -1.0 (bad) to +1.0 (good)
            float signal = convertTestMetricToSignal( lastMetric );

            if( signal > 0.0f){
                consecutiveUpticks++;
                consecutiveDownticks=0;
            }else if(signal < 0.0f ){
                consecutiveUpticks=0;
                consecutiveDownticks++;
            }else{
                //This is a neutral signal.
            }

            log("consecutive:"+consecutiveUpticks+":"+consecutiveDownticks);

            return signal;
        }

    /**
     *
     * @param testMetric - float -1.0f   to   +1.0f
     * @return  signal as float with 0.0 meaning don't make an adjustment.
     */
    private float convertTestMetricToSignal( float testMetric ){

        if( testMetric>=1.0f ){
            return 1.0f;
        }
        if(testMetric<=-1.0f){
            return -1.0f;
        }

        //here we will map the neutral region to -0.5f to +0.5f   to signal = 0;
        if( testMetric>-0.5f && testMetric<0.5f ){
            return 0.0f;
        }

        //map weak up signal.
        if( testMetric > 0.0f ){
            return ( (testMetric - 0.5f) * 2.0f );
        }

        //map weak down signal
        return ( ( testMetric+0.5f ) * 2.0f );
    }


    /**
     * The longer were get the same signal the stronger it is. On upticks however we only increase the
     * rates when if the upload or download is saturated.
     *
     * @return -
     */
    private float consectiveMultiplier(){

        float multiple;

        if( consecutiveUpticks > consecutiveDownticks ){

            //Set the consecutive upticks back to zero if the bandwidth is not being used.
            if( limitMonitor.bandwidthUsageLow() ){
                consecutiveUpticks=0;
            }

            multiple = calculateUpTickMultiple(consecutiveUpticks);
        }else{
            multiple = calculateDownTickMultiple(consecutiveDownticks);
            limitMonitor.notifyOfDownSignal();
        }

        return multiple;
    }



    /**
     * Want to rise much slower then drop.
     * @param c - number of upsignals received in a row
     * @return - multiple factor.
     */
    private float calculateUpTickMultiple(int c) {

        float multiple=0.0f;

        if(c<0){
            return multiple;
        }

        switch(c){
            case 0:
            case 1:
                multiple=0.25f;
                break;
            case 2:
                multiple=0.5f;
                break;
            case 3:
                multiple=1.0f;
                break;
            case 4:
                multiple=1.25f;
                break;
            case 5:
                multiple=1.5f;
                break;
            case 6:
                multiple=1.75f;
                break;
            case 7:
                multiple=2.0f;
                break;
            case 8:
                multiple=2.25f;
                break;
            case 9:
                multiple=2.5f;
                break;
            default:
                multiple=3.0f;
        }//switch

        //decrease the signal strength if bandwith usage is only in MED use.
        if( limitMonitor.bandwidthUsageMedium() ){
            multiple /= 2.0f;
        }

        return multiple;
    }

    /**
     * Want to drop rate faster then increase.
     * @param c -
     * @return -
     */
    private float calculateDownTickMultiple(int c) {

        float multiple=0.0f;
        if(c<0){
            return multiple;
        }

        switch(c){
            case 0:
            case 1:
                multiple=0.25f;
                break;
            case 2:
                multiple=0.5f;
                break;
            case 3:
                multiple=1.0f;
                break;
            case 4:
                multiple=2.0f;
                break;
            case 5:
                multiple=3.0f;
                break;
            case 6:
                multiple=4.0f;
                break;
            case 7:
                multiple=6.0f;
                break;
            case 8:
                multiple=9.0f;
                break;
            case 9:
                multiple=15.0f;
                break;
            default:
                multiple=20.0f;
        }//switch
        return multiple;
    }


    /**
     * Various getters for interesting info shown in stats view
     *
     * @return -
     */
    @Override
    public int getIdlePingMillis() {

        //return the vivaldi time.
        return 0;//lastMetricValue;

    }//getIdlePingMillis

    @Override
    public int getCurrentPingMillis() {
        return 0;
    }

    @Override
    public int getMaxPingMillis() {
        return 910;  //Currently a fixed number to be sure of algorightm.
    }

    /**
     * Returns the current view of when choking occurs
     *
     * @return speed in bytes/sec
     */

    @Override
    public int getCurrentChokeSpeed() {
        return 0;
    }

    @Override
    public int getMaxUploadSpeed() {
        return sessionMaxUploadRate;
    }

    @Override
    public boolean getAdjustsDownloadLimits() {
    	// TODO Auto-generated method stub
    	return true;
    }
    protected void log(String str){

        SpeedManagerLogger.log(str);
    }//log


}
