/*
 * Created on May 7, 2007
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.speedmanager.SpeedManagerPingSource;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProvider;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProviderAdapter;

public class
SpeedManagerAlgorithmProviderV2
	implements SpeedManagerAlgorithmProvider
{

    private final SpeedManagerAlgorithmProviderAdapter		adapter;

    //Test algorithms below.
    private final SpeedManagerAlgorithmProvider strategy;


    //key names are below.
    public static final String SETTING_DOWNLOAD_MAX_LIMIT = "SpeedManagerAlgorithmProviderV2.setting.download.max.limit";
    public static final String SETTING_UPLOAD_MAX_LIMIT = "SpeedManagerAlgorithmProviderV2.setting.upload.max.limit";

    //temporary two names for upload/download max until we sort out which.
    public static final String SETTING_UPLOAD_LIMIT_ESTIMATE_TYPE_FROM_UI = "AutoSpeed Network Upload Speed Type (temp)";
    public static final String SETTING_DOWNLOAD_LIMIT_ESTIMATE_TYPE_FROM_UI = "AutoSpeed Network Download Speed Type (temp)";


    //sets the input source,  vivaldi, DHT ping, ICMP pint, etc ....
    public static final String SETTING_DATA_SOURCE_INPUT = "SpeedManagerAlgorithmProviderV2.source.data.input";

    //DHT ping settings.
    public static final String SETTING_DHT_GOOD_SET_POINT = "SpeedManagerAlgorithmProviderV2.setting.dht.good.setpoint";
    public static final String SETTING_DHT_GOOD_TOLERANCE = "SpeedManagerAlgorithmProviderV2.setting.dht.good.tolerance";
    public static final String SETTING_DHT_BAD_SET_POINT = "SpeedManagerAlgorithmProviderV2.setting.dht.bad.setpoint";
    public static final String SETTING_DHT_BAD_TOLERANCE = "SpeedManagerAlgorithmProviderV2.setting.dht.bad.tolerance";

    //ping factors settings.
    public static final String SETTING_WAIT_AFTER_ADJUST = "SpeedManagerAlgorithmProviderV2.setting.wait.after.adjust";
    public static final String SETTING_INTERVALS_BETWEEN_ADJUST = "SpeedManagerAlgorithmProviderV2.intervals.between.adjust";

    //enable this mode.
    public static final String SETTING_V2_BETA_ENABLED = "SpeedManagerAlgorithmProviderV2.setting.beta.enabled";


    public
	SpeedManagerAlgorithmProviderV2(
		SpeedManagerAlgorithmProviderAdapter	_adapter )
	{
		adapter	= _adapter;

		SpeedManagerLogger.setAdapter( "v2", adapter );

        //strategy = new SpeedManagerAlgorithmProviderDHTPing(_adapter);
        strategy = new SpeedManagerAlgorithmProviderPingMap(_adapter);
    }

    @Override
    public void
    destroy()
    {
    	strategy.destroy();
    }

	@Override
	public void
	reset()
	{
        strategy.reset();
    }

	@Override
	public void
	updateStats()
	{
        strategy.updateStats();
    }

	@Override
	public void
	pingSourceFound(
		SpeedManagerPingSource		source,
		boolean						is_replacement )
	{
		log( "Found ping source: " + source.getAddress());

        strategy.pingSourceFound(source,is_replacement);
    }

	@Override
	public void
	pingSourceFailed(
		SpeedManagerPingSource		source )
	{
		log( "Lost ping source: " + source.getAddress());

        strategy.pingSourceFailed(source);
    }

	@Override
	public void
	calculate(
		SpeedManagerPingSource[]	sources )
	{
		String	str = "";

		for (int i=0;i<sources.length;i++){

			str += (i==0?"":",") + sources[i].getAddress() + " -> " + sources[i].getPingTime();
		}

		log( "ping-data: " + str );


        strategy.calculate(sources);
    }

	@Override
	public int
	getIdlePingMillis()
	{
        return strategy.getIdlePingMillis();
	}

	@Override
	public int
	getCurrentPingMillis()
	{
        return strategy.getCurrentPingMillis();
	}

	@Override
	public int
	getMaxPingMillis()
	{
        return strategy.getMaxPingMillis();
	}

	@Override
	public int
	getCurrentChokeSpeed()
	{
        return strategy.getCurrentChokeSpeed();
	}

	@Override
	public int
	getMaxUploadSpeed()
	{
        return strategy.getMaxUploadSpeed();
	}

	@Override
	public boolean getAdjustsDownloadLimits() {
		// TODO Auto-generated method stub
		return false;
	}

	protected void
	log(
		String	str )
	{
        SpeedManagerLogger.log( str );
    }

}
