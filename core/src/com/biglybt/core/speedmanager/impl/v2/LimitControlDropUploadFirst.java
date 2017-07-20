package com.biglybt.core.speedmanager.impl.v2;

/*
 * Created on Jul 9, 2007
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

public class LimitControlDropUploadFirst implements LimitControl
{

    private float valueUp=0.5f;//number between 0.0 - 1.0
    int upMax;
    int upCurr;
    int upMin;
    SaturatedMode upUsage;

    private float valueDown=1.0f;
    int downMax;
    int downCurr;
    int downMin;
    SaturatedMode downUsage;


    TransferMode mode;

    float usedUpMaxDownloadMode=0.6f;

    boolean isDownloadUnlimited = false;

    //  Here is how the LimitControl will handle the "unlimited" case.
    // #1) Download is allowed to be unlimited.
    // #2) Upload is not allowed to be unlimited.

    // a) Only the isDownloadUlimited boolean and upCurr value - used for unlimited case.
    // b) In upload unlimited. valueDown is set to 1.0.
    // c) upMax and upMin values keep there non-zero values.



    @Override
    public void updateStatus(int currUpLimit, SaturatedMode uploadUsage,
                             int currDownLimit, SaturatedMode downloadUsage,
                             TransferMode transferMode){
        upCurr = currUpLimit;
        upUsage = uploadUsage;
        downCurr = currDownLimit;
        downUsage = downloadUsage;

        mode=transferMode;
    }

    @Override
    public void setDownloadUnlimitedMode(boolean isUnlimited) {
        isDownloadUnlimited = isUnlimited;
        if(isUnlimited){
            valueDown = 1.0f;
        }
    }

    @Override
    public boolean isDownloadUnlimitedMode(){
        return isDownloadUnlimited;
    }


    @Override
    public void updateLimits(int _upMax, int _upMin, int _downMax, int _downMin){

        //verify the limits.
        if(_upMax < SMConst.START_UPLOAD_RATE_MAX ){
            _upMax = SMConst.START_UPLOAD_RATE_MAX;
        }
        if(_downMax < SMConst.START_DOWNLOAD_RATE_MAX){
            _downMax = SMConst.START_DOWNLOAD_RATE_MAX;
        }

        if(_downMax<_upMax){
            _downMax=_upMax;
        }

        _upMin = SMConst.calculateMinUpload(_upMax);
        _downMin = SMConst.calculateMinDownload(_downMax);


        upMax = _upMax;
        upMin = _upMin;
        downMax = _downMax;
        downMin = _downMin;
    }


    private int usedUploadCapacity(){

        float usedUpMax = upMax;
        if( mode.getMode() == TransferMode.State.SEEDING ){
            usedUpMax = upMax;
        }else if( mode.getMode()==TransferMode.State.DOWNLOADING ){
            usedUpMax = upMax*usedUpMaxDownloadMode;
        }else if( mode.getMode()==TransferMode.State.DOWNLOAD_LIMIT_SEARCH ){
            usedUpMax = upMax*usedUpMaxDownloadMode;
        }else if( mode.getMode()==TransferMode.State.UPLOAD_LIMIT_SEARCH ){
            usedUpMax = upMax;
        }else{

            SpeedManagerLogger.trace("LimitControlDropUploadFirst -> unrecognized transfer mode. ");
        }

        return Math.round( usedUpMax );
    }

    @Override
    public void updateSeedSettings(float downloadModeUsed)
    {
        if( downloadModeUsed < 1.0f && downloadModeUsed > 0.1f){
            usedUpMaxDownloadMode = downloadModeUsed;
            SpeedManagerLogger.trace("LimitControlDropUploadFirst %used upload used while downloading: "+downloadModeUsed);
        }
    }

    @Override
    public SMUpdate adjust(float amount ){

        boolean increase = true;
        if( amount<0.0f ){
            increase = false;
        }

        float factor = amount/10.0f;
        int usedUpMax = usedUploadCapacity();
        float gamma = (float) usedUpMax/downMax;

        if( increase ){
            //increase download first
            if( valueDown<0.99f ){
                valueDown = calculateNewValue(valueDown,factor);
            }else{
                //only increase upload if used.
                if( upUsage==SaturatedMode.AT_LIMIT ){
                    valueUp = calculateNewValue(valueUp,gamma*0.5f*factor);
                }
            }
        }else{
            //decrease upload first
            if( valueUp > 0.01f){
                valueUp = calculateNewValue(valueUp,gamma*factor);
            }else{
                valueDown = calculateNewValue(valueDown,factor);
            }
        }

        return update();
    }//adjust

    private SMUpdate update(){
        int upLimit;
        int downLimit;

        int usedUpMax = usedUploadCapacity();


        upLimit = Math.round( ((usedUpMax-upMin)*valueUp)+upMin );

        //ToDo: remove diagnotics later.
        if( upLimit>upMax || Float.isNaN(valueUp)){
            SpeedManagerLogger.trace("Limit - should upload have an unlimited condition? Setting to usedUpMax");
            upLimit = usedUpMax;
        }

        if(isDownloadUnlimited){
            downLimit = SMConst.RATE_UNLIMITED;  //zero means to unlimit the download rate.
        }else{
            downLimit = Math.round( ((downMax-downMin)*valueDown)+downMin );
        }

        //Don't show a download limit unless it is needed.
        if( valueDown == 1.0 ){
            downLimit = SMConst.RATE_UNLIMITED; //only apply a limit to the download when needed.
        }

        //log this change.
        StringBuilder msg = new StringBuilder(" create-update: valueUp="+valueUp+",upLimit="+upLimit+",valueDown=");
        if(valueDown== 1.0 ) msg.append("_unlimited_");
        else msg.append(valueDown);
        msg.append(",downLimit=").append(downLimit).append(",upMax=").append(upMax).append(",usedUpMax=").append(usedUpMax)
          .append(",upMin=").append(upMin).append(",downMax=").append(downMax);
        msg.append(",downMin=").append(downMin).append(",transferMode=").append(mode.getString()).append(",isDownUnlimited=")
          .append(isDownloadUnlimited);

        SpeedManagerLogger.log( msg.toString() );

        return new SMUpdate(upLimit,true,downLimit,true);
    }

    private float calculateNewValue(float curr, float amount){

        if(Float.isNaN(curr)){
            SpeedManagerLogger.trace("calculateNewValue - curr=NaN");
        }
        if(Float.isNaN(amount)){
            SpeedManagerLogger.trace("calculateNewValue = amount=NaN");
        }

        curr += amount;
        if( curr > 1.0f){
            curr = 1.0f;
        }
        if( curr < 0.0f ){
            curr = 0.0f;
        }

        if(Float.isNaN(curr)){
            curr=0.0f;
        }

        return curr;
    }

}
