package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.util.SystemTime;

/*
 * Created on Jun 1, 2007
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
 * Is the application in a "download" mode? Or is it in a "seeding" mode? This is
 * used to determine up we cut back on upload bandwidth limit.
 *
 * Here is how to determine the mode. If the download rate is LOW compared to the capacity
 * for five minutes continuously then it will be considered in a SEEDING mode.
 *
 * If the download bandwidth ever goes into the MED range then it switches to DOWNLOADING
 * mode immediately.
 *
 * The application will favor DOWNLOADING mode to SEEDING mode.
 *
 */
public class TransferMode
{
    private State mode = State.DOWNLOADING;

    private long lastTimeDownloadDetected = SystemTime.getCurrentTime();

    private static final long WAIT_TIME_FOR_SEEDING_MODE = 1000 * 60;



    public TransferMode()
    {

    }


    /**
     * If the download bandwidth is ever in MED or above switch immediately to DOWNLOADING mode.
     * If th download bandwidth is LOW or less for more then 5 min, switch to SEEDING mode.
     * @param downloadBandwidth - current download status.
     */
    public void updateStatus(SaturatedMode downloadBandwidth){

        //this setting have no effect while testing the limits.
        if( isConfTestingLimits() ){
            if( mode==State.DOWNLOAD_LIMIT_SEARCH ){
                lastTimeDownloadDetected = SystemTime.getCurrentTime();
            }
            return;
        }

        if( downloadBandwidth.compareTo(SaturatedMode.LOW)<=0 ){
            //we don't seem to be downloading at the moment.
            //see if this state has persisted for more then five minutes.
            long time = SystemTime.getCurrentTime();

            if( time > lastTimeDownloadDetected+WAIT_TIME_FOR_SEEDING_MODE ){
                mode = State.SEEDING;
            }

        }else{
            //Some downloading is happening. Remove from SEEDING mode.
            mode = State.DOWNLOADING;
            lastTimeDownloadDetected = SystemTime.getCurrentTime();
        }

    }

    public String getString(){
        return mode.getString();
    }

    public State getMode(){
        return mode;
    }

    public void setMode( State newMode ){

        SpeedManagerLogger.trace( " setting transfer mode to: "+newMode.getString() );

        mode = newMode;
    }

    /**
     * Are we in downloading mode?
     * @return - boolean - true if in downloading mode. Otherwise false.
     */
    public boolean isDownloadMode(){

        return ( mode==State.DOWNLOADING );

    }//isDownloadMode

    /**
     * We have two types of limit testing. If we are doing a "confidence test" for the limits then
     * return true. This mode is putting one value at the min setting and the other at unlimited.
     * @return - true if doing a "conf test of the limits"
     */
    public boolean isConfTestingLimits(){
        return ( mode==State.DOWNLOAD_LIMIT_SEARCH || mode==State.UPLOAD_LIMIT_SEARCH );
    }


    /**
     * Java 1.4 enumeration. - Seeding mode or Downloading mode.
     */
    static class State{
        public static final State DOWNLOADING = new State("downloading");
        public static final State SEEDING = new State("seeding");
        public static final State DOWNLOAD_LIMIT_SEARCH = new State("download limit search");
        public static final State UPLOAD_LIMIT_SEARCH = new State("upload limit search");
        final String mode;

        private State(String _mode){
            mode = _mode;
        }

        public String getString(){
            return mode;
        }
    }//class State

}
