package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;

/*
 * Created on Jul 18, 2007
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

public class SMConst
{

    //strictly a utility class.
    private SMConst(){}

    public static final int START_DOWNLOAD_RATE_MAX = 61440;
    public static final int START_UPLOAD_RATE_MAX = 30720;

    public static final int MIN_UPLOAD_BYTES_PER_SEC = 5120;
    public static final int MIN_DOWNLOAD_BYTES_PER_SEC = 20480;

    public static final int RATE_UNLIMITED = 0;


    /**
     * No limit should go below 5k bytes/sec.
     * @param rateBytesPerSec -
     * @return - "bytes/sec" rate.
     */
    public static int checkForMinUploadValue(int rateBytesPerSec){

        if( rateBytesPerSec < MIN_UPLOAD_BYTES_PER_SEC ){
            return MIN_UPLOAD_BYTES_PER_SEC;
        }
        return rateBytesPerSec;
    }

    public static int checkForMinDownloadValue(int rateBytesPerSec){
        if( rateBytesPerSec < MIN_DOWNLOAD_BYTES_PER_SEC ){
            return MIN_DOWNLOAD_BYTES_PER_SEC;
        }
        return rateBytesPerSec;
    }

    /**
     * Rule: Min value is always 10% of max, but not below 5k.
     * @param maxBytesPerSec -
     * @return - minRate.
     */
    public static int calculateMinUpload(int maxBytesPerSec){

        int min = maxBytesPerSec/10;
        return checkForMinUploadValue( min );
    }

    public static int calculateMinDownload(int maxBytesPerSec){
        int min = maxBytesPerSec/10;
        return checkForMinDownloadValue( min );
    }

    /**
     * Early in the search process the ping-mapper can give estimates that are too low due to
     * a lack of information. The starting upload and download limits is 60K/30K should not go
     * below the starting value a slow DSL lines should.
     * @param estimate - download rate estimate.
     * @param startValue - starting upload/download value.
     * @return -
     */
    public static SpeedManagerLimitEstimate filterEstimate(SpeedManagerLimitEstimate estimate, int startValue){


        int estBytesPerSec = filterLimit(estimate.getBytesPerSec(), startValue);

        return new FilteredLimitEstimate(estBytesPerSec,
                                        estimate.getEstimateType(),
                                        estimate.getMetricRating(),
                                        estimate.getWhen(),
                                        estimate.getString() );

    }//filterDownEstimate


    public static int filterLimit(int bytesPerSec, int startValue){
        int retVal = Math.max(bytesPerSec, startValue);

        // Zero is unlimited. Don't filter that value.
        if( bytesPerSec==0 ){
            return bytesPerSec;
        }

        return retVal;
    }

    static class FilteredLimitEstimate implements SpeedManagerLimitEstimate
    {
        final int bytesPerSec;
        final float type;
        final float metric;
        final long when;
        final String name;

        public FilteredLimitEstimate(int _bytesPerSec, float _type, float _metric, long _when, String _name){
            bytesPerSec = _bytesPerSec;
            type = _type;
            metric = _metric;
            when	= _when;
            name = _name;
        }

        @Override
        public int getBytesPerSec() {
            return bytesPerSec;
        }

        @Override
        public float getEstimateType() {
        	return type;
        }
        @Override
        public float getMetricRating() {
            return metric;
        }

        @Override
        public int[][] getSegments() {
            return new int[0][];
        }
        @Override
        public long getWhen() {
        	return when;
        }
        @Override
        public String getString() {
            return name;
        }
    }//static class


}
