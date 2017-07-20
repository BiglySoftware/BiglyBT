package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;
import com.biglybt.core.speedmanager.SpeedManagerListener;

/*
 * Created on Jul 24, 2007
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

public class SpeedLimitListener implements SpeedManagerListener {

    final SpeedLimitMonitor mon;

    public SpeedLimitListener(SpeedLimitMonitor limitMonitor){

        mon = limitMonitor;

    }

    @Override
    public void propertyChanged(int property) {

        String type="unknown";
        if( property == SpeedManagerListener.PR_ASN ){
            type = "ASN change";
            mon.readFromPersistentMap();
            mon.updateFromCOConfigManager();
            SMSearchLogger.log("ASN change.");
        }else if( property == SpeedManagerListener.PR_DOWN_CAPACITY ){
            type = "download capacity";
            SpeedManagerLimitEstimate pmEst = PingSpaceMon.getDownloadLimit();
            SpeedManagerLimitEstimate smEst = PingSpaceMon.getDownloadEstCapacity();

            SMSearchLogger.log( " download - persistent limit: "+pmEst.getString() );
            SMSearchLogger.log( " download - estimated capacity: "+smEst.getString() );

            mon.notifyDownload( smEst );
        }else if( property == SpeedManagerListener.PR_UP_CAPACITY ){
            type = "upload capacity";
            SpeedManagerLimitEstimate shortTermLimit = PingSpaceMon.getUploadLimit(false);
            SpeedManagerLimitEstimate pmEst = PingSpaceMon.getUploadLimit(true);
            SpeedManagerLimitEstimate smEst = PingSpaceMon.getUploadEstCapacity();

            SMSearchLogger.log( " upload - short term limit: "+shortTermLimit.getString() );
            SMSearchLogger.log( " upload - persistent limit: "+pmEst.getString() );
            SMSearchLogger.log( " upload - estimated capacity: "+smEst.getString() );

            mon.notifyUpload( smEst );
        }

        SpeedManagerLogger.log("Updated from SpeedManagerPingMapper property="+type);
    }

}//class
