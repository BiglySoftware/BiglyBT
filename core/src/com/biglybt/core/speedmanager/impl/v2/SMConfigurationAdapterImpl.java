package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;

/*
 * Created on Jul 30, 2007
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

public class SMConfigurationAdapterImpl implements SMConfigurationAdapter
{

    public SMConfigurationAdapterImpl(){}


    @Override
    public SpeedManagerLimitEstimate getUploadLimit() {

        int upMax = COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT);

        SpeedLimitConfidence upConf = SpeedLimitConfidence.parseString(
                COConfigurationManager.getStringParameter( SpeedLimitMonitor.UPLOAD_CONF_LIMIT_SETTING ) );

        return new SMConfigLimitEstimate(upMax,upConf);
    }

    @Override
    public SpeedManagerLimitEstimate getDownloadLimit() {

        int upMax = COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT);

        SpeedLimitConfidence upConf = SpeedLimitConfidence.parseString(
                COConfigurationManager.getStringParameter( SpeedLimitMonitor.DOWNLOAD_CONF_LIMIT_SETTING ) );

        return new SMConfigLimitEstimate(upMax,upConf);
    }

    @Override
    public void setUploadLimit(SpeedManagerLimitEstimate est) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setDownloadLimit(SpeedManagerLimitEstimate est) {
        //To change body of implemented methods use File | Settings | File Templates.
    }




    //conversion routines need to be here.
    static class SMConfigLimitEstimate implements SpeedManagerLimitEstimate{

        final int bytesPerSec;
        final float limitEstimateType;

        public SMConfigLimitEstimate(int rateInBytesPerSec, SpeedLimitConfidence conf){

            bytesPerSec = rateInBytesPerSec;
            limitEstimateType = conf.asEstimateType();

        }

        @Override
        public int getBytesPerSec() {
            return bytesPerSec;
        }

        /**
         * One of the above constants
         *
         * @return
         */

        @Override
        public float getEstimateType() {
            return limitEstimateType;
        }

        /**
         * For estimated limits:
         * -1 = estimate derived from bad metrics
         * +1 = estimate derived from good metric
         * <1 x > -1 = relative goodness of metric
         *
         * @return
         */

        @Override
        public float getMetricRating() {
            return 0.0f;
        }

        /**
         * Don't call this method.
         * @return
         */
        @Override
        public int[][] getSegments() {
            return new int[0][];
        }

        @Override
        public long getWhen() {
        	return 0;
        }
        /**
         *
         * @return
         */
        @Override
        public String getString() {
            StringBuilder sb = new StringBuilder("estimate: ");
            sb.append(bytesPerSec);
            sb.append(" (").append(limitEstimateType).append(") ");

            return sb.toString();
        }
    }//class SMConfigLimitEstimate


}
