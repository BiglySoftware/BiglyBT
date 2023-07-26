package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;
import com.biglybt.core.speedmanager.SpeedManagerPingMapper;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProviderAdapter;
import com.biglybt.core.util.RealTimeInfo;
import com.biglybt.core.util.SystemTime;

/*
 * Created on May 23, 2007
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
 * This class is responsible for re-adjusting the limits used by AutoSpeedV2.
 *
 * This class will keep track of the "status" (i.e. seeding, downloading)of the
 * application. It will then re-adjust the MAX limits when it thinks limits
 * are being reached.
 *
 * Here are the rules it will use.
 *
 * #1) When seeding. If the upload is AT_LIMIT for a period of time it will allow
 * that to adjust upward.
 * #2) When downloading. If the download is AT_LIMIT for a period of time it will
 * allow that to adjust upward.
 *
 * #3) When downloading, if a down-tick is detected and the upload is near a limit,
 * it will drop the upload limit to 80% of MAX_UPLOAD.
 *
 * #4) Once that limit is reached it will drop both the upload and download limits together.
 *
 * #5) Seeding mode is triggered when - download bandwidth at LOW - compared to CAPACITY for 5 minutes continuously.
 *
 * #6) Download mode is triggered when - download bandwidth reaches MEDIUM - compared to CURRENT_LIMIT for the first time.
 *
 * Rules #5 and #6 favor downloading over seeding.
 *
 */

public class SpeedLimitMonitor implements PSMonitorListener
{

    //use for home network.
    private int uploadLimitMax = SMConst.START_UPLOAD_RATE_MAX;
    private int uploadLimitMin = SMConst.calculateMinUpload( uploadLimitMax );
    private int downloadLimitMax = SMConst.START_DOWNLOAD_RATE_MAX;
    private int downloadLimitMin = SMConst.calculateMinDownload( downloadLimitMax );

    private final TransferMode transferMode = new TransferMode();

    //Upload and Download bandwidth usage modes. Compare usage to current limit.
    private SaturatedMode uploadBandwidthStatus =SaturatedMode.NONE;
    private SaturatedMode downloadBandwidthStatus =SaturatedMode.NONE;

    //Compare current limit to max limit.
    private SaturatedMode uploadLimitSettingStatus=SaturatedMode.AT_LIMIT;
    private SaturatedMode downloadLimitSettingStatus=SaturatedMode.AT_LIMIT;

    //How much confidence to we have in the current limits?
    private SpeedLimitConfidence uploadLimitConf = SpeedLimitConfidence.NONE;
    private SpeedLimitConfidence downloadLimitConf = SpeedLimitConfidence.NONE;

    private long clLastIncreaseTime =-1;
    private long clFirstBadPingTime=-1;

    private boolean currTestDone;
    private boolean beginLimitTest;
    private int highestUploadRate=0;
    private int highestDownloadRate=0;
    private int preTestUploadCapacity=5042;
    private int preTestUploadLimit=5142;
    private int preTestDownloadCapacity=5042;
    private int preTestDownloadLimit=5142;

    public static final String UPLOAD_CONF_LIMIT_SETTING="SpeedLimitMonitor.setting.upload.limit.conf";
    public static final String DOWNLOAD_CONF_LIMIT_SETTING="SpeedLimitMonitor.setting.download.limit.conf";
    public static final String UPLOAD_CHOKE_PING_COUNT="SpeedLimitMonitor.setting.choke.ping.count";
    private static final long CONF_LIMIT_TEST_LENGTH=1000*30;

    //these methods are used to see how high limits can go.
    private boolean isUploadMaxPinned=true;
    private boolean isDownloadMaxPinned=true;
    private long uploadAtLimitStartTime =SystemTime.getCurrentTime();
    private long downloadAtLimitStartTime = SystemTime.getCurrentTime();
    private int uploadChokePingCount = 1;
    private int uploadPinCounter = 0;

    private static final long TIME_AT_LIMIT_BEFORE_UNPINNING = 30 * 1000; //30 seconds.

    //which percent of the measured upload capacity to use in download and seeding mode.
    public static final String USED_UPLOAD_CAPACITY_DOWNLOAD_MODE = "SpeedLimitMonitor.setting.upload.used.download.mode";
    public static final String USED_UPLOAD_CAPACITY_SEEDING_MODE = "SpeedLimitMonitor.setting.upload.used.seeding.mode";
    private float percentUploadCapacityDownloadMode = 0.6f;

    //PingSpaceMaps for the entire session.
    PingSpaceMapper pingMapOfDownloadMode;
    PingSpaceMapper pingMapOfSeedingMode;


    boolean useVariancePingMap = false;
    SpeedManagerPingMapper transientPingMap;

    final PingSpaceMon longTermMonitor = new PingSpaceMon();

    final LimitControl slider = new LimitControlDropUploadFirst();

    final SpeedLimitListener persistentMapListener;

    public SpeedLimitMonitor(SpeedManager sm){

        //
        longTermMonitor.addListener( this );

        persistentMapListener = new SpeedLimitListener( this );

        sm.addListener( persistentMapListener );
    }


    /**
     * Splitting the limits our from other setting for SpeedManagerAlgorithmTI.
     */
    public void updateSettingsFromCOConfigManager(){
        percentUploadCapacityDownloadMode = (float)
                COConfigurationManager.getIntParameter(SpeedLimitMonitor.USED_UPLOAD_CAPACITY_DOWNLOAD_MODE, 60)/100.0f;

        slider.updateSeedSettings(percentUploadCapacityDownloadMode);
    }

    public void updateFromCOConfigManager(){

        uploadLimitMax = COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT);
        uploadLimitMin = SMConst.calculateMinUpload( uploadLimitMax );

        downloadLimitMax =COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT);
        downloadLimitMin=SMConst.calculateMinDownload( downloadLimitMax );

        uploadLimitConf = SpeedLimitConfidence.parseString(
                COConfigurationManager.getStringParameter( SpeedLimitMonitor.UPLOAD_CONF_LIMIT_SETTING ));
        downloadLimitConf = SpeedLimitConfidence.parseString(
                COConfigurationManager.getStringParameter( SpeedLimitMonitor.DOWNLOAD_CONF_LIMIT_SETTING));

        percentUploadCapacityDownloadMode = (float)
                COConfigurationManager.getIntParameter(SpeedLimitMonitor.USED_UPLOAD_CAPACITY_DOWNLOAD_MODE, 60)/100.0f;

        uploadChokePingCount = Math.min(
                    COConfigurationManager.getIntParameter(SpeedLimitMonitor.UPLOAD_CHOKE_PING_COUNT),
                    30 );

        slider.updateLimits(uploadLimitMax,uploadLimitMin,downloadLimitMax,downloadLimitMin);
        slider.updateSeedSettings(percentUploadCapacityDownloadMode);


        if( isSettingDownloadUnlimited() ){
            slider.setDownloadUnlimitedMode( true );
        }

    }//updateFromCOConfigManager

    /**
     * replaces - updateFromCOConfigManager()
     */
    public void readFromPersistentMap(){
        //get persistent mapper.
        SpeedManager sm = CoreFactory.getSingleton().getSpeedManager();

        //get upload estimate.
        SpeedManagerLimitEstimate uEst = SMConst.filterEstimate(
                                            sm.getEstimatedUploadCapacityBytesPerSec(),
                                            SMConst.START_UPLOAD_RATE_MAX );

        int upPingMapLimit = uEst.getBytesPerSec();
        if(upPingMapLimit<SMConst.START_UPLOAD_RATE_MAX){
            //will find upload limit via slow search.
            uploadLimitMax = SMConst.START_UPLOAD_RATE_MAX;
        }else{
            uploadLimitMax = upPingMapLimit;
        }
        uploadLimitMin = SMConst.calculateMinUpload( uploadLimitMax );


        //get download estimate.
        SpeedManagerLimitEstimate dEst = SMConst.filterEstimate(
                                            sm.getEstimatedDownloadCapacityBytesPerSec(),
                                            SMConst.START_DOWNLOAD_RATE_MAX );


        int downPingMapLimit = dEst.getBytesPerSec();
        if( isSettingDownloadUnlimited() ){
            slider.setDownloadUnlimitedMode(true);
        }else{
            slider.setDownloadUnlimitedMode(false);
        }

        if(downPingMapLimit<SMConst.START_DOWNLOAD_RATE_MAX){
            downloadLimitMax = SMConst.START_DOWNLOAD_RATE_MAX;
        }else{
            downloadLimitMax = downPingMapLimit;
        }
        downloadLimitMin = SMConst.calculateMinDownload( downloadLimitMax );

        uploadLimitConf = SpeedLimitConfidence.convertType( uEst.getEstimateType() );
        downloadLimitConf = SpeedLimitConfidence.convertType( dEst.getEstimateType() );

        percentUploadCapacityDownloadMode = (float)
                    COConfigurationManager.getIntParameter(SpeedLimitMonitor.USED_UPLOAD_CAPACITY_DOWNLOAD_MODE, 60)/100.0f;

        saveToCOConfiguration();
    }


    public void saveToCOConfiguration(){
        COConfigurationManager.setParameter(SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT,uploadLimitMax);
        COConfigurationManager.setParameter(SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT,downloadLimitMax);
        COConfigurationManager.setParameter(SpeedLimitMonitor.UPLOAD_CONF_LIMIT_SETTING, uploadLimitConf.getString() );
        COConfigurationManager.setParameter(SpeedLimitMonitor.DOWNLOAD_CONF_LIMIT_SETTING, downloadLimitConf.getString() );
        COConfigurationManager.setParameter(SpeedLimitMonitor.UPLOAD_CHOKE_PING_COUNT,uploadChokePingCount);
    }

    private void logPMData(int oRate, SpeedLimitConfidence oConf, int nRate, float nConf, String type){

//        SpeedManagerLogger.log("speed-limit-conf: "+type+" rate="+oRate+" conf="+oConf.getString()+"("+oConf.asEstimateType()
//                +") pm-rate="+nRate+" pm-conf="+nConf);

    }//logPMData

    public void logPMDataEx(){

        int tuploadLimitMax = COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT);
        int tdownloadLimitMax =COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT);

        //for testing.
        SpeedManager sm = CoreFactory.getSingleton().getSpeedManager();
        SpeedManagerLimitEstimate dEst = sm.getEstimatedDownloadCapacityBytesPerSec();

        int tmpDMax = dEst.getBytesPerSec();
        float tmpDMaxConf = dEst.getEstimateType();


        // for testing.
        SpeedManagerLimitEstimate uEst = sm.getEstimatedUploadCapacityBytesPerSec();
        int tmpUMax = uEst.getBytesPerSec();
        float tmpUMaxConf = uEst.getEstimateType();

        SpeedLimitConfidence tuploadLimitConf = SpeedLimitConfidence.parseString(
                COConfigurationManager.getStringParameter( SpeedLimitMonitor.UPLOAD_CONF_LIMIT_SETTING ));
        SpeedLimitConfidence tdownloadLimitConf = SpeedLimitConfidence.parseString(
                COConfigurationManager.getStringParameter( SpeedLimitMonitor.DOWNLOAD_CONF_LIMIT_SETTING));

        //
        logPMData(tuploadLimitMax, tuploadLimitConf, tmpUMax, tmpUMaxConf, "check-upload" );
        logPMData(tdownloadLimitMax, tdownloadLimitConf, tmpDMax, tmpDMaxConf, "check-download" );

    }//logPMDataEx


    /**
     * The criteria for download being unlimited is if the ConfigPanel has the
     * "download == 0 " && "type==fixed"
     * @return - true
     */
    private boolean isSettingDownloadUnlimited(){

        SpeedManagerAlgorithmProviderAdapter adpter = SMInstance.getInstance().getAdapter();

        SpeedManager sm = adpter.getSpeedManager();
        SpeedManagerLimitEstimate dEst = sm.getEstimatedDownloadCapacityBytesPerSec();

        int rate = dEst.getBytesPerSec();
        float type = dEst.getEstimateType();

        //user or plug-in want the download rate unlimited.
        if( rate==0 && type==SpeedManagerLimitEstimate.TYPE_MANUAL ){
            return true;
        }

        //start the search in unlimited mode.
        if( rate==0 && type==SpeedManagerLimitEstimate.TYPE_UNKNOWN){
            return true;
        }

        return false;
    }

    //SpeedLimitMonitorStatus
    public void setDownloadBandwidthMode(int rate, int limit){
        downloadBandwidthStatus = SaturatedMode.getSaturatedMode(rate,limit);
    }

    public void setUploadBandwidthMode(int rate, int limit){
        uploadBandwidthStatus = SaturatedMode.getSaturatedMode(rate,limit);
    }

    public void setDownloadLimitSettingMode(int currLimit){
        downloadLimitSettingStatus = SaturatedMode.getSaturatedMode(currLimit, downloadLimitMax);
    }

    public void setUploadLimitSettingMode(int currLimit){
        if( !transferMode.isDownloadMode() ){
            uploadLimitSettingStatus = SaturatedMode.getSaturatedMode(currLimit, uploadLimitMax);
        }else{
            uploadLimitSettingStatus = SaturatedMode.getSaturatedMode(currLimit, uploadLimitMax);
        }
    }

    public int getUploadMaxLimit(){
        return uploadLimitMax;
    }

    public int getDownloadMaxLimit(){
        return downloadLimitMax;
    }

    public int getUploadMinLimit(){
        return uploadLimitMin;
    }

    public int getDownloadMinLimit(){
        return downloadLimitMin;
    }

    public String getUploadConfidence(){
        return uploadLimitConf.getString();
    }

    public String getDownloadConfidence(){
        return downloadLimitConf.getString();
    }

    public SaturatedMode getDownloadBandwidthMode(){
        return downloadBandwidthStatus;
    }

    public SaturatedMode getUploadBandwidthMode(){
        return uploadBandwidthStatus;
    }

    public SaturatedMode getDownloadLimitSettingMode(){
        return downloadLimitSettingStatus;
    }

    public SaturatedMode getUploadLimitSettingMode(){
        return uploadLimitSettingStatus;
    }

    public void updateTransferMode(){

        transferMode.updateStatus( downloadBandwidthStatus );
    }

    public String getTransferModeAsString(){
        return transferMode.getString();
    }

    public TransferMode getTransferMode(){
        return transferMode;
    }


    /**
     * Are both the upload and download bandwidths usages is low?
     * Otherwise false.
     * @return -
     */
    public boolean bandwidthUsageLow(){

        if( uploadBandwidthStatus.compareTo(SaturatedMode.LOW)<=0 &&
                downloadBandwidthStatus.compareTo(SaturatedMode.LOW)<=0){

            return true;

        }

        //Either upload or download is at MEDIUM or above.
        return false;
    }

    /**
     *
     * @return -
     */
    public boolean bandwidthUsageMedium(){
        if( uploadBandwidthStatus.compareTo(SaturatedMode.MED)<=0 &&
                downloadBandwidthStatus.compareTo(SaturatedMode.MED)<=0){
            return true;
        }

        //Either upload or download is at MEDIUM or above.
        return false;
    }

    /**
     * True if both are at limits.
     * @return - true only if both the upload and download usages are at the limits.
     */
    public boolean bandwidthUsageAtLimit(){
        if( uploadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0 &&
                downloadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0){
            return true;
        }
        return false;
    }

    /**
     * True if the upload bandwidth usage is HIGH or AT_LIMIT.
     * @return -
     */
    public boolean isUploadBandwidthUsageHigh(){
        if( uploadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0 ||
                uploadBandwidthStatus.compareTo(SaturatedMode.HIGH)==0){
            return true;
        }
        return false;
    }

    public boolean isEitherLimitUnpinned(){
        return ( !isUploadMaxPinned || !isDownloadMaxPinned );
    }

    /**
     * Does the same as createNewLimit except it drops the upload rate first when in download mode.
     * @param signalStrength -
     * @param multiple -
     * @param currUpLimit -
     * @param currDownLimit -
     * @return  -
     */
    public SMUpdate modifyLimits(float signalStrength, float multiple, int currUpLimit, int currDownLimit){

        //this flag is set in a previous method.
        if( isStartLimitTestFlagSet() ){
            SpeedManagerLogger.trace("modifyLimits - startLimitTesting.");
            SMUpdate update = startLimitTesting(currUpLimit, currDownLimit);
            return checkActiveProgressiveDownloadLimit( update );
        }


        if( isEitherLimitUnpinned() ){
            SpeedManagerLogger.trace("modifyLimits - calculateNewUnpinnedLimits");
            SMUpdate update = calculateNewUnpinnedLimits(signalStrength);
            return checkActiveProgressiveDownloadLimit( update );
        }

        slider.updateLimits(uploadLimitMax,uploadLimitMin,
                downloadLimitMax,downloadLimitMin);

        slider.updateStatus(currUpLimit,uploadBandwidthStatus,
                currDownLimit, downloadBandwidthStatus,transferMode);

        SMUpdate update = slider.adjust( signalStrength*multiple );
        return checkActiveProgressiveDownloadLimit( update );
    }//modifyLimits

    /**
     * If a progressive download is currently active. Then the download limit should
     * not be allowed to go below that limit, regardless of anything else.
     * @param update -
     * @return -
     */
    private SMUpdate checkActiveProgressiveDownloadLimit( SMUpdate update ){

        //Do we have an active download limit?
        long prgDownLimit = RealTimeInfo.getProgressiveActiveBytesPerSec();

        //If the value is zero, then the no progressive download is currently active.
        if( prgDownLimit==0 ){
            return update;
        }

        //We seem to have an active progressive download. Make sure the limit does not
        //drop below that limit.
        final int MULTIPLE = 2;
        if( prgDownLimit*MULTIPLE > update.newDownloadLimit && update.newDownloadLimit!=0 )
        {
            log( "Active Progressive download in progress. Overriding limit. curr="+update.newDownloadLimit
                    +" progDownloadLimit="+prgDownLimit*MULTIPLE );

            update.newDownloadLimit = (int)prgDownLimit*MULTIPLE;
        }//if

        return update;
    }//checkActiveProgressiveDownloadLimit

    /**
     * Log debug info needed during beta period.
     */
    private void logPinningInfo() {
        StringBuilder sb = new StringBuilder("pin: ");
        if(isUploadMaxPinned){
            sb.append("ul-pinned:");
        }else{
            sb.append("ul-unpinned:");
        }
        if(isDownloadMaxPinned){
            sb.append("dl-pinned:");
        }else{
            sb.append("dl-unpinned:");
        }
        long currTime = SystemTime.getCurrentTime();
        long upWait = currTime - uploadAtLimitStartTime;
        long downWait = currTime - downloadAtLimitStartTime;
        sb.append(upWait).append(":").append(downWait);
        log( sb.toString() );
    }

    /**
     *
     * @param signalStrength -
     * @return -
     */
    public SMUpdate calculateNewUnpinnedLimits(float signalStrength){

        //first verify that is this is an up signal.
        if(signalStrength<0.0f){
            //down-tick is a signal to stop moving the files up.
            isUploadMaxPinned=true;
            isDownloadMaxPinned=true;
        }//if

        //just verify settings to make sure everything is sane before updating.
        boolean updateUpload=false;
        boolean updateDownload=false;

        if( uploadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0 &&
                uploadLimitSettingStatus.compareTo(SaturatedMode.AT_LIMIT)==0 ){
            updateUpload=true;
        }

        if( downloadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0 &&
                downloadLimitSettingStatus.compareTo(SaturatedMode.AT_LIMIT)==0 ){
            updateDownload=true;
        }

        boolean uploadChanged=false;
        boolean downloadChanged=false;


        if(updateUpload && !transferMode.isDownloadMode() ){
            //slow the upload rate the more.
            uploadPinCounter++;
            if( uploadPinCounter%(Math.ceil(Math.sqrt(uploadChokePingCount)))==0 ){
                //increase limit by calculated amount, but only if not in downloading mode.
                uploadLimitMax += calculateUnpinnedStepSize(uploadLimitMax);
                uploadChanged=true;
                COConfigurationManager.setParameter(
                        SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT, uploadLimitMax);
                COConfigurationManager.setParameter(
                        SpeedLimitMonitor.UPLOAD_CHOKE_PING_COUNT,uploadChokePingCount);
            }//if
        }
        if(updateDownload && !slider.isDownloadUnlimitedMode() ){
            //increase limit by calculated amount.
            downloadLimitMax += calculateUnpinnedStepSize(downloadLimitMax);
            downloadChanged=true;
            COConfigurationManager.setParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT, downloadLimitMax);
        }

        //apply any rules that need applied.
        //The download limit can never be less then the upload limit. (Unless zero. UNLIMITED)
        if( uploadLimitMax > downloadLimitMax){
            downloadLimitMax = uploadLimitMax;
            downloadChanged=true;
            COConfigurationManager.setParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT, downloadLimitMax);
        }

        uploadLimitMin = SMConst.calculateMinUpload( uploadLimitMax );
        downloadLimitMin = SMConst.calculateMinDownload( downloadLimitMax );

        if( slider.isDownloadUnlimitedMode() ){
            SpeedManagerLogger.trace("upload unpinned while download is unlimited.");
            return new SMUpdate(uploadLimitMax,uploadChanged, 0,false);
        }

        return new SMUpdate(uploadLimitMax,uploadChanged, downloadLimitMax,downloadChanged);
    }//calculateNewUnpinnedLimits

    /**
     * If setting is less then 100kBytes take 1 kByte steps.
     * If setting is less then 500kBytes take 5 kByte steps.
     * if setting is larger take 10 kBytes steps.
     * @param currLimitMax - current limit setting.
     * @return - set size for next change.
     */
    private int calculateUnpinnedStepSize(int currLimitMax){
        if(currLimitMax<102400){
            return 1024;
        }else if(currLimitMax<409600){
            return 1024*5;
        }else{
            return 1024*10;
        }
    }//

    /**
     * Make a decision about unpinning either the upload or download limit. This is based on the
     * time we are saturating the limit without a down-tick signal.
     */
    public void checkForUnpinningCondition(){

        long currTime = SystemTime.getCurrentTime();

        //verify the download is not unlimited.
        slider.setDownloadUnlimitedMode( isSettingDownloadUnlimited() );

        //upload usage must be at limits for a set period of time before unpinning.
        if( !uploadBandwidthStatus.equals(SaturatedMode.AT_LIMIT) ||
                !uploadLimitSettingStatus.equals(SaturatedMode.AT_LIMIT) )
        {
            //start the clock over.
            uploadAtLimitStartTime = currTime;
        }else{
            //check to see if we have been here for the time limit.
            if( uploadAtLimitStartTime+(TIME_AT_LIMIT_BEFORE_UNPINNING* uploadChokePingCount) < currTime ){

                if( isUploadConfidenceLow() ){
                    if( !transferMode.isDownloadMode() ){
                        //always slow search the upload limit.
                        isUploadMaxPinned = false;
                    }
                }else{
                    //Don't unpin the limit is we have absolute confidence in it.
                    if( !isUploadConfidenceAbsolute() ){
                        //we have been AT_LIMIT long enough. Time to un-pin the limit see if we can go higher.
                        isUploadMaxPinned = false;
                        SpeedManagerLogger.trace("unpinning the upload max limit!! #choke-pings="+uploadChokePingCount+
                            ", pin-counter="+uploadPinCounter);
                    }
                }
            }
        }

        //download usage must be at limits for a set period of time before unpinning.
        if( !downloadBandwidthStatus.equals(SaturatedMode.AT_LIMIT) ||
                !downloadLimitSettingStatus.equals(SaturatedMode.AT_LIMIT) )
        {
            //start the clock over.
            downloadAtLimitStartTime = currTime;
        }else{
            //check to see if we have been here for the time limit.
            if( downloadAtLimitStartTime+TIME_AT_LIMIT_BEFORE_UNPINNING < currTime ){

                if( isDownloadConfidenceLow() ){
                    if( transferMode.isDownloadMode() ){
                        triggerLimitTestingFlag();
                    }
                }else{
                    if( !isDownloadConfidenceAbsolute() ){
                        //we have been AT_LIMIT long enough. Time to un-pin the limit see if we can go higher.
                        isDownloadMaxPinned = false;
                        SpeedManagerLogger.trace("unpinning the download max limit!!");
                    }
                }
            }
        }

        logPinningInfo();
    }

    /**
     * If we have a down-tick signal then resetTimer all the counters for increasing the limits.
     */
    public void notifyOfDownSignal(){

        if( !isUploadMaxPinned ){
            uploadChokePingCount++;
            String msg = "pinning the upload max limit, due to downtick signal. #downtick="+ uploadChokePingCount;
            SpeedManagerLogger.trace(msg);
            SMSearchLogger.log(msg);
        }

        if( !isDownloadMaxPinned ){
            String msg = "pinning the download max limit, due to downtick signal.";
            SpeedManagerLogger.trace(msg);
            SMSearchLogger.log(msg);
        }

        resetPinSearch();
    }

    void resetPinSearch(){
        long currTime = SystemTime.getCurrentTime();

        uploadAtLimitStartTime = currTime;
        downloadAtLimitStartTime = currTime;
        isUploadMaxPinned = true;
        isDownloadMaxPinned = true;
    }

    void resetPinSearch(SpeedManagerLimitEstimate estimate){
        //chocking ping needs higher limit.
        float type = estimate.getEstimateType();
        if(type>=SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED){
            uploadChokePingCount++;
        }
        resetPinSearch();
    }

    /**
     * Return true if we are confidence testing the limits.
     * @return - SMUpdate
     */
    public boolean isConfTestingLimits(){
        return transferMode.isConfTestingLimits();
    }//

    /**
     * Determine if we have low confidence in this limit.
     * @return - true if the confidence setting is LOW or NONE. Otherwise return true.
     */
    public boolean isDownloadConfidenceLow(){
        return ( downloadLimitConf.compareTo(SpeedLimitConfidence.MED) < 0 );
    }

    public boolean isUploadConfidenceLow(){
        return ( uploadLimitConf.compareTo(SpeedLimitConfidence.MED) < 0 );
    }

    public boolean isDownloadConfidenceAbsolute(){
        return ( downloadLimitConf.compareTo(SpeedLimitConfidence.ABSOLUTE)==0 );
    }

    public boolean isUploadConfidenceAbsolute(){
        return ( uploadLimitConf.compareTo(SpeedLimitConfidence.ABSOLUTE)==0 );
    }


    /**
     *
     * @param downloadRate - currentUploadRate in bytes/sec
     * @param uploadRate - currentUploadRate in bytes/sec
     */
    public synchronized void updateLimitTestingData( int downloadRate, int uploadRate ){
        if( downloadRate>highestDownloadRate ){
            highestDownloadRate=downloadRate;
        }
        if( uploadRate>highestUploadRate){
            highestUploadRate=uploadRate;
        }

        //The exit criteria for this test is 30 seconds without an increase in the limits.
        long currTime = SystemTime.getCurrentTime();
        if( currTime > clLastIncreaseTime+CONF_LIMIT_TEST_LENGTH){
            //set the test done flag.
            currTestDone=true;
        }
        // or 30 seconds after its first bad ping.
        if( clFirstBadPingTime!=-1){
            if( currTime > clFirstBadPingTime+CONF_LIMIT_TEST_LENGTH){
                //set the test done flag.
                currTestDone=true;
            }
        }

    }//updateLimitTestingData.


    /**
     * Convert raw ping value to new metric.
     * @param lastMetric -
     */
    public void updateLimitTestingPing(int lastMetric){
        //Convert raw - pings into a rating.
        if(lastMetric>500){
            updateLimitTestingPing(-1.0f);
        }
    }

    /**
     * New metric from the PingMapper is value between -1.0 and +1.0f.
     * @param lastMetric -
     */
    public void updateLimitTestingPing(float lastMetric){
        if( lastMetric<-0.3f){
            //Setting this time is a signal to end soon.
            clFirstBadPingTime = SystemTime.getCurrentTime();
        }
    }


    /**
     * Call this method to start the limit testing.
     * @param currUploadLimit -
     * @param currDownloadLimit -
     * @return - SMUpdate
     */
    public SMUpdate startLimitTesting(int currUploadLimit, int currDownloadLimit){

        clLastIncreaseTime =SystemTime.getCurrentTime();
        clFirstBadPingTime =-1;

        highestUploadRate=0;
        highestDownloadRate=0;
        currTestDone=false;

        //reset the flag.
        beginLimitTest=false;

        //get the limits before the test, we are restoring them after the test.
        preTestUploadLimit = currUploadLimit;
        preTestDownloadLimit = currDownloadLimit;

        //configure the limits for this test. One will be at min and the other unlimited.
        SMUpdate retVal;
        if( transferMode.isDownloadMode() ){
            //test the download limit.
            retVal = new SMUpdate(uploadLimitMin,true,
                        Math.round(downloadLimitMax *1.2f),true);
            preTestDownloadCapacity = downloadLimitMax;
            transferMode.setMode( TransferMode.State.DOWNLOAD_LIMIT_SEARCH );
        }else{
            //test the upload limit.
            retVal = new SMUpdate( Math.round(uploadLimitMax *1.2f),true,
                        downloadLimitMin,true);
            preTestUploadCapacity = uploadLimitMax;
            transferMode.setMode( TransferMode.State.UPLOAD_LIMIT_SEARCH );
        }

        return retVal;
    }

    /**
     * Ramp the upload and download rates higher, so ping-times are relevant.
     * @param uploadLimit -
     * @param downloadLimit -
     * @return -
     */
    public SMUpdate rampTestingLimit(int uploadLimit, int downloadLimit){
        SMUpdate retVal;
        if( transferMode.getMode() == TransferMode.State.DOWNLOAD_LIMIT_SEARCH
                && downloadBandwidthStatus.isGreater( SaturatedMode.MED ) )
        {
            downloadLimit *= 1.1f;
            clLastIncreaseTime = SystemTime.getCurrentTime();
            retVal = new SMUpdate(uploadLimit,false,downloadLimit,true);

        }else if( transferMode.getMode() == TransferMode.State.UPLOAD_LIMIT_SEARCH
                && uploadBandwidthStatus.isGreater( SaturatedMode.MED ))
        {
            uploadLimit *= 1.1f;
            clLastIncreaseTime = SystemTime.getCurrentTime();
            retVal = new SMUpdate(uploadLimit,true,downloadLimit,false);

        }else{
            retVal = new SMUpdate(uploadLimit,false,downloadLimit,false);
            SpeedManagerLogger.trace("ERROR: rampTestLimit should only be called during limit testing. ");
        }

        return retVal;
    }//rampTestingLimit

    public void triggerLimitTestingFlag(){
        SpeedManagerLogger.trace("triggered fast limit test.");
        beginLimitTest=true;

        //if we are using a persistent PingSource then get that here.
        if( useVariancePingMap ){
            SMInstance pm = SMInstance.getInstance();
            SpeedManagerAlgorithmProviderAdapter adapter = pm.getAdapter();

            //start a new transientPingMap;
            if(transientPingMap!=null){
                transientPingMap.destroy();
            }
            transientPingMap = adapter.createTransientPingMapper();
        }

    }

    public synchronized boolean isStartLimitTestFlagSet(){
        return beginLimitTest;
    }

    public synchronized boolean isConfLimitTestFinished(){
        return currTestDone;
    }

    public synchronized SMUpdate endLimitTesting(int downloadCapacityGuess, int uploadCapacityGuess){

        SpeedManagerLogger.trace(" repalce highestDownloadRate: "+highestDownloadRate+" with "+downloadCapacityGuess);
        SpeedManagerLogger.trace(" replace highestUploadRate: "+highestUploadRate+" with "+uploadCapacityGuess);

        highestDownloadRate = downloadCapacityGuess;
        highestUploadRate = uploadCapacityGuess;

        return endLimitTesting();
    }

    /**
     * Call this method to end the limit testing.
     * @return - SMUpdate
     */
    public synchronized SMUpdate endLimitTesting(){

        SMUpdate retVal;
        //determine if the new setting is different then the old setting.
        if( transferMode.getMode()==TransferMode.State.DOWNLOAD_LIMIT_SEARCH ){

            downloadLimitConf = determineConfidenceLevel();

            //set that value.
            SpeedManagerLogger.trace("pre-upload-setting="+ preTestUploadCapacity +" up-capacity"+ uploadLimitMax
                    +" pre-download-setting="+ preTestDownloadCapacity +" down-capacity="+ downloadLimitMax);

            retVal = new SMUpdate(preTestUploadLimit,true, downloadLimitMax,true);
            //change back to original mode.
            transferMode.setMode( TransferMode.State.DOWNLOADING );

        }else if( transferMode.getMode()==TransferMode.State.UPLOAD_LIMIT_SEARCH){

            uploadLimitConf = determineConfidenceLevel();

            //set that value.
            retVal = new SMUpdate(uploadLimitMax,true, downloadLimitMax,true);
            //change back to original mode.
            transferMode.setMode( TransferMode.State.SEEDING );

        }else{
            //This is an "illegal state" make it in the logs, but try to recover by setting back to original state.
            SpeedManagerLogger.log("SpeedLimitMonitor had IllegalState during endLimitTesting.");
            retVal = new SMUpdate(preTestUploadLimit,true, preTestDownloadLimit,true);
        }

        currTestDone=true;

        //reset the counter
        uploadAtLimitStartTime = SystemTime.getCurrentTime();
        downloadAtLimitStartTime = SystemTime.getCurrentTime();

        return retVal;
    }

    /**
     * After a test is complete determine how condifent the client should be in it
     * based on how different it is from the previous result.  If the new result is within
     * 20% of the old result then give it a MED. If it is great then give it a LOW.
     * @return - what the new confidence interval should be.
     */
    public SpeedLimitConfidence determineConfidenceLevel(){
        SpeedLimitConfidence retVal=SpeedLimitConfidence.NONE;
        String settingMaxLimitName;
        //String settingMinLimitName;
        boolean isDownload;
        String settingConfidenceName;
        int preTestValue;
        int highestValue;
        if(transferMode.getMode()==TransferMode.State.DOWNLOAD_LIMIT_SEARCH){

            settingConfidenceName = DOWNLOAD_CONF_LIMIT_SETTING;
            settingMaxLimitName = SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT;
            isDownload=true;
            preTestValue = preTestDownloadCapacity;
            highestValue = highestDownloadRate;
        }else if(transferMode.getMode()==TransferMode.State.UPLOAD_LIMIT_SEARCH){

            settingConfidenceName = UPLOAD_CONF_LIMIT_SETTING;
            settingMaxLimitName = SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT;
            isDownload=false;
            preTestValue = preTestUploadCapacity;
            highestValue = highestUploadRate;
        }else{
            //
            SpeedManagerLogger.log("IllegalState in determineConfidenceLevel(). Setting level to NONE.");
            return SpeedLimitConfidence.NONE;
        }

        boolean hadChockingPing = hadChockingPing();
        float percentDiff = (float)Math.abs( highestValue-preTestValue )/(float)(Math.max(highestValue,preTestValue));
        if( percentDiff<0.15f  && hadChockingPing ){
            //Only set to medium if had both a chocking ping and two tests with similar results.
            retVal = SpeedLimitConfidence.MED;
        }else{
            retVal = SpeedLimitConfidence.LOW;
        }

        //update the values.
        COConfigurationManager.setParameter(settingConfidenceName, retVal.getString() );
        int newMaxLimitSetting = highestValue;
        COConfigurationManager.setParameter(settingMaxLimitName, newMaxLimitSetting);
        int newMinLimitSetting;
        if( isDownload ){
            newMinLimitSetting = SMConst.calculateMinDownload( newMaxLimitSetting );
        }else{
            newMinLimitSetting = SMConst.calculateMinUpload( newMaxLimitSetting );
        }

        StringBuilder sb = new StringBuilder();
        if( transferMode.getMode()==TransferMode.State.UPLOAD_LIMIT_SEARCH ){
            sb.append("new upload limits: ");
            uploadLimitMax =newMaxLimitSetting;
            uploadLimitMin=newMinLimitSetting;
            //downloadCapacity can never be less then upload capacity.
            if( downloadLimitMax < uploadLimitMax){
                downloadLimitMax = uploadLimitMax;
                COConfigurationManager.setParameter(
                        SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT, downloadLimitMax);
            }
            sb.append(uploadLimitMax);
        }else{
            sb.append("new download limits: ");
            downloadLimitMax =newMaxLimitSetting;
            downloadLimitMin=newMinLimitSetting;
            //upload capacity should never be 40x less then download.
            if( uploadLimitMax * 40 < downloadLimitMax){
                uploadLimitMax = downloadLimitMax /40;
                COConfigurationManager.setParameter(
                         SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT, uploadLimitMax);

                uploadLimitMin = SMConst.calculateMinUpload( uploadLimitMax );
            }//if
            sb.append(downloadLimitMax);
        }

        slider.updateLimits(uploadLimitMax,uploadLimitMin,downloadLimitMax,downloadLimitMin);

        SpeedManagerLogger.trace( sb.toString() );

        return retVal;
    }

    /**
     * If the user changes the line capacity settings on the configuration panel and adjustment
     * needs to occur even if the signal is NO-CHANGE-NEEDED. Test for that condition here.
     * @param currUploadLimit  - reported upload capacity from the adapter
     * @param currDownloadLimit - reported download capacity from the adapter.
     * @return - true if the "capacity" is lower then the current limit.
     */
    public boolean areSettingsInSpec(int currUploadLimit, int currDownloadLimit){

        //during a confidence level test, anything goes.
        if( isConfTestingLimits() ){
            return true;
        }

        boolean retVal = true;
        if( currUploadLimit> uploadLimitMax){
            retVal = false;
        }
        if(  currDownloadLimit> downloadLimitMax && slider.isDownloadUnlimitedMode() ){
            retVal = false;
        }
        return retVal;
    }

    private int choseBestLimit(SpeedManagerLimitEstimate estimate, int currMaxLimit, SpeedLimitConfidence currConf) {
        float type = estimate.getEstimateType();
        int estBytesPerSec = estimate.getBytesPerSec();
        int chosenLimit;

        //no estimate less then 20k accepted.
        if( (estBytesPerSec<currMaxLimit) && estBytesPerSec<20480 ){
            return currMaxLimit;
        }

        String reason="";
        if(  type==SpeedManagerLimitEstimate.TYPE_MANUAL){
            chosenLimit = estBytesPerSec;
            reason="manual";
        }else if( type==SpeedManagerLimitEstimate.TYPE_UNKNOWN){
            chosenLimit = Math.max( estBytesPerSec, currMaxLimit );
            reason="unknown";
        }else if (type==SpeedManagerLimitEstimate.TYPE_ESTIMATED ){

        	if ( estimate.getMetricRating() >= 0.0 ){

        			// things looking good, this is just a new limit estimate and shouldn't
        			// affect the actual limit in force

           		return( currMaxLimit );

        	}else{

        		chosenLimit = estBytesPerSec;
        		reason = "estimate and bad metric";
         	}

        }else{
            //chose ping mapper.
            chosenLimit = estBytesPerSec;
        }

        SpeedManagerLogger.trace("bestChosenLimit: reason="+reason+",chosenLimit="+chosenLimit);

        return chosenLimit;
    }

    /**
     * Make some choices about how usable the limits are before passing them on.
     * @param estUp -
     * @param estDown -
     */
    public void setRefLimits(SpeedManagerLimitEstimate estUp,SpeedManagerLimitEstimate estDown){

        SpeedManagerLimitEstimate up = SMConst.filterEstimate(estUp,SMConst.MIN_UPLOAD_BYTES_PER_SEC);
        int upMax = choseBestLimit(up, uploadLimitMax, uploadLimitConf);

        SpeedManagerLimitEstimate down = SMConst.filterEstimate(estDown, SMConst.MIN_DOWNLOAD_BYTES_PER_SEC);
        int downMax = choseBestLimit(down, downloadLimitMax, downloadLimitConf);

        if(downMax<upMax){
            SpeedManagerLogger.trace("down max-limit was less then up-max limit. increasing down max-limit. upMax="
                    +upMax+" downMax="+downMax);
            downMax = upMax;
        }

        setRefLimits(upMax,downMax);
    }

    public void setRefLimits(int uploadMax, int downloadMax){

        if( (uploadLimitMax!=uploadMax) && (uploadMax>0) ){
            uploadLimitMax=uploadMax;
            COConfigurationManager.setParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT, uploadLimitMax);
        }

        uploadLimitMin = SMConst.calculateMinUpload( uploadMax );

        if( (downloadLimitMax!=downloadMax) && (downloadMax>0) ){
            downloadLimitMax = downloadMax;
            COConfigurationManager.setParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT, downloadLimitMax);
        }

        downloadLimitMin = SMConst.calculateMinDownload(downloadMax);

        SpeedManagerLogger.trace("setRefLimits uploadMax="+uploadMax+" uploadLimitMax="+uploadLimitMax+", downloadMax="+downloadMax+" downloadLimitMax="+downloadLimitMax);

        slider.updateLimits(uploadLimitMax,uploadLimitMin,downloadLimitMax,downloadLimitMin);
    }

    /**
     * It is likely the user adjusted the "line speed capacity" on the configuration panel.
     * We need to adjust the current limits down to adjust.
     * @param currUploadLimit -
     * @param currDownloadLimit -
     * @return - Updates as needed.
     */
    public SMUpdate adjustLimitsToSpec(int currUploadLimit, int currDownloadLimit){

        int newUploadLimit = currUploadLimit;
        boolean uploadChanged = false;
        int newDownloadLimit = currDownloadLimit;
        boolean downloadChanged = false;

        StringBuilder reason = new StringBuilder();

        //check for the case when the line-speed capacity is below the current limit.
        if( currUploadLimit> uploadLimitMax && uploadLimitMax!=0){

            newUploadLimit = uploadLimitMax;
            uploadChanged = true;

            reason.append(" (a) upload line-speed cap below current limit. ");
        }

        if(uploadLimitMax==0){
            reason.append("** uploadLimitMax=0 (Unlimited)! ** ");
        }

        //check for the case when the min setting has been moved above the current limit.
        if( currDownloadLimit> downloadLimitMax && !slider.isDownloadUnlimitedMode() ){
            newDownloadLimit = downloadLimitMax;
            downloadChanged = true;

            reason.append(" (b) download line-speed cap below current limit. ");
        }

        //Another possibility is the min limits have been raised.
        if( currUploadLimit<uploadLimitMin ){
            newUploadLimit = uploadLimitMin;
            uploadChanged = true;

            reason.append(" (c) min upload limit raised. ");
        }

        if( currDownloadLimit<downloadLimitMin ){
            newDownloadLimit = downloadLimitMin;
            downloadChanged = true;

            reason.append(" (d)  min download limit raised. ");
        }

        SpeedManagerLogger.trace("Adjusting limits due to out of spec: new-up="+newUploadLimit
                +" new-down="+newDownloadLimit+"  reasons: "+reason.toString());

        return new SMUpdate(newUploadLimit,uploadChanged,newDownloadLimit,downloadChanged);
    }


    protected void log(String str){

        SpeedManagerLogger.log(str);
    }//log



    public void initPingSpaceMap(int maxGoodPing, int minBadPing){
        pingMapOfDownloadMode = new PingSpaceMapper(maxGoodPing,minBadPing);
        pingMapOfSeedingMode = new PingSpaceMapper(maxGoodPing,minBadPing);

        //pingMonitor = new PingSpaceMonitor(maxGoodPing,minBadPing,transferMode);

        useVariancePingMap = false;
    }

    public void initPingSpaceMap(){
        useVariancePingMap = true;


        //ToDo: remove after beta-testing - just to characterize the different methods.
//        pingMapOfDownloadMode = new PingSpaceMapper(150,500);
//        pingMapOfSeedingMode = new PingSpaceMapper(150,500);

    }

    /**
     * This is a lot of data, but is important debug info.
     * @param name -
     * @param transEst -
     * @param hadChockPing -
     * @param permEst -
     * @param downMode -
     * @param seedMode -
     */
    public void betaLogPingMapperEstimates(String name,
                                           SpeedManagerLimitEstimate transEst,
                                           boolean hadChockPing,
                                           SpeedManagerLimitEstimate permEst,
                                           PingSpaceMapper downMode,
                                           PingSpaceMapper seedMode)
    {
        StringBuilder sb = new StringBuilder("beta-ping-maps-").append(name).append(": ");

        if(transEst!=null){
            int rate = transEst.getBytesPerSec();
            float conf = transEst.getMetricRating();
            sb.append("transient-").append(rate).append("(").append(conf).append(")");
        }
        sb.append(" chockPing=").append(hadChockPing);


        if(permEst!=null){
            int rate = permEst.getBytesPerSec();
            float conf = permEst.getMetricRating();
            sb.append("; perm-").append(rate).append("(").append(conf).append(")");
        }

        if(downMode!=null){
            int rateDown = downMode.guessDownloadLimit();
            int rateUp = downMode.guessUploadLimit();
            boolean downChockPing = downMode.hadChockingPing(true);
            boolean upChockPing = downMode.hadChockingPing(false);

            sb.append("; downMode- ");
            sb.append("rateDown=").append(rateDown).append(" ");
            sb.append("rateUp=").append(rateUp).append(" ");
            sb.append("downChockPing=").append(downChockPing).append(" ");
            sb.append("upChockPing=").append(upChockPing).append(" ");
        }

        if(seedMode!=null){
            int rateDown = seedMode.guessDownloadLimit();
            int rateUp = seedMode.guessUploadLimit();
            boolean downChockPing = seedMode.hadChockingPing(true);
            boolean upChockPing = seedMode.hadChockingPing(false);

            sb.append("; seedMode- ");
            sb.append("rateDown=").append(rateDown).append(" ");
            sb.append("rateUp=").append(rateUp).append(" ");
            sb.append("downChockPing=").append(downChockPing).append(" ");
            sb.append("upChockPing=").append(upChockPing).append(" ");
        }
        SpeedManagerLogger.log( sb.toString() );
    }//betaLogPingMapperEstimates

    public int guessDownloadLimit(){

        if( !useVariancePingMap){
            return pingMapOfDownloadMode.guessDownloadLimit();
        }else{

            boolean wasChocked=true;
            SpeedManagerLimitEstimate transientEst=null;
            if(transientPingMap!=null){
                transientEst = transientPingMap.getLastBadDownloadLimit();
                if(transientEst==null){
                    wasChocked=false;
                    transientEst = transientPingMap.getEstimatedDownloadLimit(false);
                }
            }

            //NOTE: Currently just getting the persistentMap for temp logging purposes.
            SMInstance pm = SMInstance.getInstance();
            SpeedManagerAlgorithmProviderAdapter adapter = pm.getAdapter();
            SpeedManagerPingMapper persistentMap = adapter.getPingMapper();
            SpeedManagerLimitEstimate persistentEst = persistentMap.getEstimatedDownloadLimit(false);

            //log the different ping-mappers for beta.
            betaLogPingMapperEstimates("down",transientEst,wasChocked,persistentEst,pingMapOfDownloadMode,pingMapOfSeedingMode);

            if( transientEst!=null )
            {
                return choseBestLimit(transientEst,downloadLimitMax,downloadLimitConf);
            }else{
                return downloadLimitMax;
            }

        }
    }//guessDownloadLimit

    public int guessUploadLimit(){

        if( !useVariancePingMap){

            int dmUpLimitGuess = pingMapOfDownloadMode.guessUploadLimit();
            int smUpLimitGuess = pingMapOfSeedingMode.guessUploadLimit();

            return Math.max(dmUpLimitGuess,smUpLimitGuess);

        }else{

            boolean wasChocked=true;
            SpeedManagerLimitEstimate transientEst=null;
            if(transientPingMap!=null){
                transientEst = transientPingMap.getLastBadUploadLimit();
                if(transientEst==null){
                    wasChocked=false;
                    transientEst = transientPingMap.getEstimatedUploadLimit(false);
                }
            }

            //NOTE: Currently just getting the persistentMap for temp logging purposes.
            SMInstance pm = SMInstance.getInstance();
            SpeedManagerAlgorithmProviderAdapter adapter = pm.getAdapter();
            SpeedManagerPingMapper persistentMap = adapter.getPingMapper();
            SpeedManagerLimitEstimate persistentEst = persistentMap.getEstimatedUploadLimit(false);

            //log the different ping-mappers for beta.
            betaLogPingMapperEstimates("up",transientEst,wasChocked,persistentEst,pingMapOfDownloadMode,pingMapOfSeedingMode);

            if( transientEst!=null )
            {
                return choseBestLimit(transientEst,uploadLimitMax,uploadLimitConf);
            }else{
                return uploadLimitMax;
            }
        }

    }//guessUploadLimit


    /**
     * Should return true if had a recent chocking ping.
     * @return - true if
     */
    public boolean hadChockingPing(){
        if( !useVariancePingMap){

            return pingMapOfDownloadMode.hadChockingPing(true);

        }else{
            SpeedManagerPingMapper pm = SMInstance.getInstance().getAdapter().getPingMapper();

            //if either had a choking ping.
            SpeedManagerLimitEstimate dEst = pm.getEstimatedDownloadLimit(true);
            SpeedManagerLimitEstimate uEst = pm.getEstimatedUploadLimit(true);

            boolean hadChokePingUp = (uEst.getEstimateType()==SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED);
            boolean hadChokePingDown = (dEst.getEstimateType()==SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED);

            return ( hadChokePingUp || hadChokePingDown );
        }
    }//hadChockingPing

    /**
     * Just log this data until we decide if it is useful.
     */
    public void logPingMapData() {

        if( !useVariancePingMap){
            int downLimGuess = pingMapOfDownloadMode.guessDownloadLimit();
            int upLimGuess = pingMapOfDownloadMode.guessUploadLimit();
            int seedingUpLimGuess = pingMapOfSeedingMode.guessUploadLimit();

            StringBuilder sb = new StringBuilder("ping-map: ");
            sb.append(":down=").append(downLimGuess);
            sb.append(":up=").append(upLimGuess);
            sb.append(":(seed)up=").append(seedingUpLimGuess);

            SpeedManagerLogger.log( sb.toString()  );
        }else{
            SMInstance pm = SMInstance.getInstance();
            SpeedManagerAlgorithmProviderAdapter adapter = pm.getAdapter();
            SpeedManagerPingMapper persistentMap = adapter.getPingMapper();

            SpeedManagerLimitEstimate estUp = persistentMap.getEstimatedUploadLimit(false);
            SpeedManagerLimitEstimate estDown = persistentMap.getEstimatedDownloadLimit(false);

            int downLimGuess = estDown.getBytesPerSec();
            float downConf = estDown.getMetricRating();
            int upLimGuess = estUp.getBytesPerSec();
            float upConf = estUp.getMetricRating();

            String name = persistentMap.getName();

            StringBuilder sb = new StringBuilder("new-ping-map: ");
            sb.append(" name=").append(name);
            sb.append(", down=").append(downLimGuess);
            sb.append(", down-conf=").append(downConf);
            sb.append(", up=").append(upLimGuess);
            sb.append(", up-conf=").append(upConf);

            SpeedManagerLogger.log( sb.toString() );
        }
    }//logPingMapData

    public void setCurrentTransferRates(int downRate, int upRate){

        if( pingMapOfDownloadMode!=null && pingMapOfSeedingMode!=null){
            pingMapOfDownloadMode.setCurrentTransferRates(downRate,upRate);
            pingMapOfSeedingMode.setCurrentTransferRates(downRate,upRate);
        }

    }

    public void resetPingSpace(){

        if( pingMapOfDownloadMode!=null && pingMapOfSeedingMode!=null){
            pingMapOfDownloadMode.reset();
            pingMapOfSeedingMode.reset();
        }

        if(transientPingMap!=null){
            transientPingMap.destroy();
        }
    }

    public void addToPingMapData(int lastMetricValue){
        String modeStr = getTransferModeAsString();

        if(    modeStr.equalsIgnoreCase(TransferMode.State.DOWNLOADING.getString())
            || modeStr.equalsIgnoreCase(TransferMode.State.DOWNLOAD_LIMIT_SEARCH.getString())  )
        {
            //add point to map for download mode
            pingMapOfDownloadMode.addMetricToMap(lastMetricValue);

        }
        else if(     modeStr.equalsIgnoreCase(TransferMode.State.SEEDING.getString())
                  || modeStr.equalsIgnoreCase(TransferMode.State.UPLOAD_LIMIT_SEARCH.getString()) )
        {
            //add point to map for seeding mode.
            pingMapOfSeedingMode.addMetricToMap(lastMetricValue);

        }


        //if confidence limit testing, inform of bad ping.
        updateLimitTestingPing(lastMetricValue);

        longTermMonitor.updateStatus(transferMode);

    }//addToPingMapData


    @Override
    public void notifyUpload(SpeedManagerLimitEstimate estimate) {
        int bestLimit = choseBestLimit(estimate,uploadLimitMax,uploadLimitConf);

        SpeedManagerLogger.trace("notifyUpload uploadLimitMax="+uploadLimitMax);
        tempLogEstimate(estimate);

        if(bestLimit!=uploadLimitMax){
            //update COConfiguration
            SpeedManagerLogger.log("persistent PingMap changed upload limit to "+bestLimit);

            resetPinSearch(estimate);

            uploadLimitMax = bestLimit;
            COConfigurationManager.setParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT, uploadLimitMax);
        }

        uploadLimitMin = SMConst.calculateMinUpload(uploadLimitMax);
        slider.updateLimits(uploadLimitMax,uploadLimitMin,downloadLimitMax,downloadLimitMin);

        SMSearchLogger.log("new upload rate: "+uploadLimitMax);
    }

    @Override
    public void notifyDownload(SpeedManagerLimitEstimate estimate) {
        int bestLimit = choseBestLimit(estimate,downloadLimitMax,downloadLimitConf);

        SpeedManagerLogger.trace("notifyDownload downloadLimitMax="+downloadLimitMax
                +" conf="+downloadLimitConf.getString()+" ("+downloadLimitConf.asEstimateType()+")");
        tempLogEstimate(estimate);

        if(downloadLimitMax!=bestLimit){
            //update COConfiguration
            SpeedManagerLogger.log( "persistent PingMap changed download limit to "+bestLimit );
            downloadLimitMax = bestLimit;
            COConfigurationManager.setParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT, bestLimit);
        }

        downloadLimitMin = SMConst.calculateMinDownload(downloadLimitMax);
        slider.updateLimits(uploadLimitMax,uploadLimitMin,downloadLimitMax,downloadLimitMin);


        if(estimate.getBytesPerSec()!=0){
            slider.setDownloadUnlimitedMode(false);
        }else{
            slider.setDownloadUnlimitedMode(true);
        }

        SMSearchLogger.log("download "+downloadLimitMax );
    }

    private void tempLogEstimate(SpeedManagerLimitEstimate est){

        if(est==null){
            SpeedManagerLogger.trace( "notify log: SpeedManagerLimitEstimate was null" );
            return;
        }

        StringBuilder sb = new StringBuilder();
        float metric = est.getMetricRating();
        float type = est.getEstimateType();
        int rate = est.getBytesPerSec();

        String str = est.getString();

        sb.append("notify log: ").append(str);
        sb.append(" metricRating=").append(metric);
        sb.append(" rate=").append(rate);
        sb.append(" type=").append(type);

        SpeedManagerLogger.trace( sb.toString() );

    }//tempLogEstimate

}//SpeedLimitMonitor
