/*
 * File    : StatsWriterPeriodicImpl.java
 * Created : 23-Oct-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.stats.impl;

import java.io.File;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.stats.StatsWriterPeriodic;
import com.biglybt.core.util.*;

/**
 * @author parg
 */

public class
StatsWriterPeriodicImpl
	implements StatsWriterPeriodic, COConfigurationListener, TimerEventPerformer
{
	private static final LogIDs LOGID = LogIDs.CORE;

	private static StatsWriterPeriodicImpl	singleton;

	private boolean started;

	private long			last_write_time	= 0;
	private final Core core;

	private TimerEventPeriodic event;
	private boolean			config_enabled;
	private int				config_period;
	private String			config_dir;
	private String			config_file;

	public static synchronized StatsWriterPeriodic create(Core _core) {
		synchronized (StatsWriterPeriodicImpl.class)
		{
			if (singleton == null)
			{
				singleton = new StatsWriterPeriodicImpl(_core);
			}
			return (singleton);
		}
	}

	protected
	StatsWriterPeriodicImpl(
		Core _core )
	{
		core	= _core;
	}


	@Override
	public void perform(TimerEvent event) {
		update();
	}

	protected void
	update()
	{
		try {
			writeStats();
		} catch (Throwable e)
		{
			Debug.printStackTrace(e);
		}
	}

	protected synchronized void
	readConfigValues()
	{
		config_enabled 	= COConfigurationManager.getBooleanParameter( "Stats Enable" );

		config_period	= COConfigurationManager.getIntParameter( "Stats Period" );

		config_dir		= COConfigurationManager.getStringParameter( "Stats Dir" ).trim();

		config_file		= COConfigurationManager.getStringParameter( "Stats File" ).trim();

		if(config_enabled)
		{
			long targetFrequency = 1000 * (config_period < DEFAULT_SLEEP_PERIOD ? config_period : DEFAULT_SLEEP_PERIOD);
			if(event != null && event.getFrequency() != targetFrequency)
			{
				event.cancel();
				event = null;
			}

			if(event == null)
				event = SimpleTimer.addPeriodicEvent("StatsWriter", targetFrequency, this);

		} else if(event != null)
		{
			event.cancel();
			event = null;
		}



	}

	protected void
	writeStats()
	{
		synchronized( this ){

			if ( !config_enabled ){

				return;
			}

			int	period = config_period;

			long	now = SystemTime.getMonotonousTime() /1000;


				// if we have a 1 second period then now-last-write_time will often be 0 (due to the
				// rounding of SystemTime) and the stats won't be written - hence the check against
				// (period-1). Its only

			if ( now - last_write_time < ( period - 1 ) ){

				return;
			}

			last_write_time	= now;

			try{

				File file_name = FileUtil.newFile(
						config_dir.isEmpty() ? File.separator : config_dir,
						config_file.isEmpty() ? DEFAULT_STATS_FILE_NAME : config_file);

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "Stats Logged to '" + file_name + "'"));

				new StatsWriterImpl( core ).write( file_name );

			}catch( Throwable e ){
				Logger.log(new LogEvent(LOGID, "Stats Logging fails", e));
			}
		}
	}

	@Override
	public void
	configurationSaved()
	{
			// only pick up configuration changes when saved

		readConfigValues();

		writeStats();
	}

	@Override
	public void
	start()
	{
		if(started)
			return;
		started = true;
		COConfigurationManager.addListener( this );
		configurationSaved();
	}

	@Override
	public void
	stop()
	{
		COConfigurationManager.removeListener( this );

		synchronized( this ){
			if(event != null){
				event.cancel();
				event = null;
			}
		}
	}



}
