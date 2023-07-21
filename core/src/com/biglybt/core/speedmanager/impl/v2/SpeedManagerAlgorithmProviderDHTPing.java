package com.biglybt.core.speedmanager.impl.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.biglybt.core.CoreException;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.speedmanager.SpeedManagerPingSource;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProvider;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProviderAdapter;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.dht.DHTPlugin;

/*
 * Created on May 14, 2007
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
 * This class implements an Vivaldi strategy for adjusting the upload limit.
 * It will every 30 seconds calculate the distance to the center of the
 * Vivaldi structure and if it determines that it is going away from it will
 * lower the limit. If it getting closer it will increase the upload limit.
 */

public class SpeedManagerAlgorithmProviderDHTPing
        implements SpeedManagerAlgorithmProvider, COConfigurationListener
{

    private final SpeedManagerAlgorithmProviderAdapter adapter;
    private PluginInterface dhtPlugin;


    private long timeSinceLastUpdate;

    //metric values for DHT Ping times and Vivaldi
    private static int metricGoodResult = 100;
    private static int metricGoodTolerance = 300;
    private static int metricBadResult = 1300;
    private static int metricBadTolerance = 300;

    private int consecutiveUpticks=0;
    private int consecutiveDownticks=0;

    //SpeedLimitMonitor
    private final SpeedLimitMonitor limitMonitor;

    //variables for display and vivaldi.
    private int lastMetricValue;

    //use for DHT ping.
    private static int numIntervalsBetweenCal = 2;
    private static boolean skipIntervalAfterAdjustment = true;

    private List pingTimeList = new ArrayList(); //<Integer>
    private boolean hadAdjustmentLastInterval = false;
    private int intervalCount = 0;

    //for managing ping sources.
    final PingSourceManager pingSourceManager = new PingSourceManager();

    int sessionMaxUploadRate = 0;




    SpeedManagerAlgorithmProviderDHTPing(SpeedManagerAlgorithmProviderAdapter _adapter){

        adapter = _adapter;

        SpeedManagerLogger.setAdapter( "dht", adapter );

        limitMonitor = new SpeedLimitMonitor( adapter.getSpeedManager());

        COConfigurationManager.addListener( this );

        SMInstance.init( _adapter );

        try{
            dhtPlugin = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );
        }catch(CoreException ace){
            log("Warning: Core was not initialized on startup.");
        }

        if( dhtPlugin==null ){
            //Need to log this condition if it ever happens!!
            log(" Error: failed to get DHT Plugin ");
        }//if

        limitMonitor.initPingSpaceMap(metricGoodResult+metricGoodTolerance,metricBadResult-metricBadTolerance);
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

            metricGoodResult =COConfigurationManager.getIntParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DHT_GOOD_SET_POINT);
            metricGoodTolerance =COConfigurationManager.getIntParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DHT_GOOD_TOLERANCE);
            metricBadResult =COConfigurationManager.getIntParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DHT_BAD_SET_POINT);
            metricBadTolerance =COConfigurationManager.getIntParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DHT_BAD_TOLERANCE);

            skipIntervalAfterAdjustment=COConfigurationManager.getBooleanParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_WAIT_AFTER_ADJUST);
            numIntervalsBetweenCal=COConfigurationManager.getIntParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_INTERVALS_BETWEEN_ADJUST);

            limitMonitor.initPingSpaceMap(metricGoodResult+metricGoodTolerance,metricBadResult-metricBadTolerance);

            SpeedManagerLogger.trace("..DHTPing - configurationSaved called.");

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

        log("curr-data: curr-down-rate : curr-down-limit : down-capacity : down-bandwidth-mode : down-limit-mode : curr-up-rate : curr-up-limit : up-capacity : upload-bandwidth-mode : upload-limit-mode : transfer-mode");

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
        StringBuilder sb = new StringBuilder("curr-data-v:"+downRate+":"+currDownLimit+":");
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
                pingTimeList.add( new Integer( sources[i].getPingTime() ) );
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

        //use the DHT ping times instead.
        if ( calculateMediaDHTPingTime() ){
            return;
        }

        log("metric:"+ lastMetricValue);
        logLimitStatus();

        //update the metric data
        limitMonitor.addToPingMapData(lastMetricValue);

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
     * DHT Ping data is one of the metrics used. Calculate it here.
     * @return - true if should exit early from the calculate method.
     */
    private boolean calculateMediaDHTPingTime() {
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
        Collections.sort( pingTimeList );

        //if we don't have any pings, then either the connection is lost or very bad network congestion.
        //force an adjustment down.
        if( pingTimeList.size()==0 ){
            lastMetricValue =10000;  //This is a high value to force an adjusment down.
        }else{
            int medianIndex = pingTimeList.size()/2;

            Integer medianPingTime = (Integer) pingTimeList.get(medianIndex);
            lastMetricValue = medianPingTime.intValue();
        }

        //we have now consumed this data. reset the counters.
        intervalCount=0;
        pingTimeList = new ArrayList();
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


    /**
     * Determined by the vivaldi value and the number of consecutive calculations
     * with the same result.
     * @param currMetricValue -
     * @return -
     */
    private float determineSignalStrength(int currMetricValue){

        //determine if this is an up-tick (+1), down-tick (-1) or neutral (0).
        float signal=0.0f;
        if( currMetricValue< metricGoodResult){
            //strong up signal.
            signal=1.0f;
            consecutiveUpticks++;
            consecutiveDownticks=0;
        }
        else if( currMetricValue < (metricGoodResult + metricGoodTolerance)){
            //weak up signal.
            signal = (float)(currMetricValue- metricGoodResult)/ metricGoodTolerance;

            consecutiveUpticks++;
            consecutiveDownticks=0;
        }
        else if( currMetricValue > metricBadResult){
            //strong down signal
            signal = -1.0f;
            consecutiveUpticks=0;
            consecutiveDownticks++;
        }
        else if( currMetricValue > (metricBadResult - metricBadTolerance) ){
            //weak down signal
            consecutiveUpticks=0;
            consecutiveDownticks++;

            int lowerBound= metricBadResult - metricBadTolerance;
            signal = ((float)(currMetricValue-lowerBound)) / metricBadTolerance;
            signal -= 1.0f;
        }
        else{
            //This is a neutral signal.
        }

        log("consecutive:"+consecutiveUpticks+":"+consecutiveDownticks);

        return signal;
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

        //decrease the signal strength if bandwidth usage is only in MED use.
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
        return lastMetricValue;

    }//getIdlePingMillis

    @Override
    public int getCurrentPingMillis() {
        return 0;
    }

    @Override
    public int getMaxPingMillis() {
        return 912;  //Currently a fixed number to be sure of algorightm.
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
