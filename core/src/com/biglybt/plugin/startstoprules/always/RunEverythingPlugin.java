/*
 * Created on 05-Dec-2005
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

package com.biglybt.plugin.startstoprules.always;

import java.util.*;

import com.biglybt.pif.*;
import com.biglybt.pif.download.*;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.utils.Monitor;
import com.biglybt.pif.utils.Semaphore;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;

public class
RunEverythingPlugin
	implements Plugin, DownloadManagerListener, DownloadListener, DownloadTrackerListener
{
	private	PluginInterface		plugin_interface;
	private LoggerChannel		logger;

	private Map					downloads;
	private Monitor				downloads_mon;

	private Semaphore			work_sem;

	private volatile boolean	closing;

	public static void
	load(
		PluginInterface		_plugin_interface )
	{
	    PluginManagerDefaults defaults = PluginManager.getDefaults();

	    defaults.setDefaultPluginEnabled( PluginManagerDefaults.PID_START_STOP_RULES, false );
	}

	@Override
	public void
	initialize(
		PluginInterface	_pi )
	{
		plugin_interface	= _pi;

		logger				= plugin_interface.getLogger().getChannel( "RunEverythingSeedingRules" );

		plugin_interface.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{
				}

				@Override
				public void
				closedownInitiated()
				{
					closing	= true;
				}

				@Override
				public void
				closedownComplete()
				{
				}
			});


		downloads = new HashMap();

		downloads_mon = plugin_interface.getUtilities().getMonitor();

		work_sem	= plugin_interface.getUtilities().getSemaphore();

		plugin_interface.getDownloadManager().addListener( this );

		plugin_interface.getUtilities().createTimer("DownloadRules", true ).addPeriodicEvent(
			10000,
			new UTTimerEventPerformer()
			{
				@Override
				public void
				perform(
					UTTimerEvent event)
				{
					checkRules();
				}
			});

		plugin_interface.getUtilities().createThread(
			"DownloadRules",
			new Runnable()
			{
				@Override
				public void
				run()
				{
					processLoop();
				}
			});
	}

	@Override
	public void
	downloadAdded(
		Download	download )
	{
		log( "added: " + download.getName() + ", state = " + Download.ST_NAMES[ download.getState()]);

		downloadData	dd = new downloadData( download );

		try{
			downloads_mon.enter();

			downloads.put( download, dd );

		}finally{

			downloads_mon.exit();
		}

		download.addListener( this );

		checkRules();
	}

	@Override
	public void
	downloadRemoved(
		Download	download )
	{
		try{
			downloads_mon.enter();

			downloads.remove( download );

		}finally{

			downloads_mon.exit();
		}

		download.removeListener( this );

		checkRules();
	}

	@Override
	public void
	scrapeResult(
		DownloadScrapeResult	result )
	{
		checkRules();
	}

	@Override
	public void
	announceResult(
		DownloadAnnounceResult	result )
	{
		checkRules();
	}

	@Override
	public void
	stateChanged(
		Download		download,
		int				old_state,
		int				new_state )
	{
		log( "Rules: state change for " + download.getName() + ": " + Download.ST_NAMES[old_state] + "->" + Download.ST_NAMES[new_state]);

		checkRules();
	}

	@Override
	public void
	positionChanged(
		Download	download,
		int 		oldPosition,
		int 		newPosition )
	{
		checkRules();
	}

	protected void
	checkRules()
	{
		work_sem.release();
	}

	protected void
	processLoop()
	{
		while( !closing ){

			work_sem.reserve();

			while( work_sem.reserveIfAvailable()){
			}

			try{
				processSupport();

				Thread.sleep( 250 );

			}catch( Throwable e ){

				e.printStackTrace();
			}
		}
	}

	protected void
	processSupport()
	{
		if ( closing ){

			return;
		}

		try{
			downloads_mon.enter();

			List	dls = new ArrayList( downloads.values());

				// remove any ignored ones

			Iterator	it = dls.iterator();

			while (it.hasNext()){

				downloadData	dd = (downloadData)it.next();

				if ( dd.ignore()){

					it.remove();
				}
			}

				// execute an "initialize" on any waiting ones

			it = dls.iterator();

			while (it.hasNext()){

				downloadData	dd = (downloadData)it.next();

				if ( dd.getState() == Download.ST_WAITING ){

					it.remove();

					try{
						log( "initialising " + dd.getName());

						dd.getDownload().initialize();

					}catch( DownloadException e ){

						e.printStackTrace();
					}
				}
			}

				// execute a "start" on any READY ones

			it = dls.iterator();

			while (it.hasNext()){

				downloadData	dd = (downloadData)it.next();

				if ( dd.getState() == Download.ST_READY ){

					it.remove();

					try{
						log( "starting " + dd.getName());

						dd.getDownload().start();

					}catch( DownloadException e ){

						e.printStackTrace();
					}
				}
			}

				// start downloads

			it = dls.iterator();

			while (it.hasNext()){

				downloadData	dd  = (downloadData)it.next();

				if ( dd.getState() == Download.ST_QUEUED && !dd.isComplete()){

					try{
						it.remove();

						log( "restarting download " + dd.getName());

						dd.getDownload().restart();

					}catch( DownloadException e ){

						e.printStackTrace();
					}
				}
			}

				// start seeds

			it = dls.iterator();

			while ( it.hasNext()){

				downloadData	dd  = (downloadData)it.next();

				if ( dd.isComplete() && dd.getState() == Download.ST_QUEUED ){

					try{
						it.remove();

						log( "restarting seed " + dd.getName());

						dd.getDownload().restart();

					}catch( DownloadException e ){

						e.printStackTrace();
					}
				}
			}
		}finally{

			downloads_mon.exit();
		}
	}

	protected void
	log(
		String	str )
	{
		logger.log( str );
	}

	private static class
	downloadData
	{
		private Download		download;

		protected
		downloadData(
			Download		_download )
		{
			download	= _download;
		}

		protected Download
		getDownload()
		{
			return( download );
		}

		protected int
		getState()
		{
			return( download.getState());
		}

		protected String
		getName()
		{
			return( download.getName());
		}

		/*
		protected boolean
		isDownloading()
		{
			if ( isComplete()){

				return( false );
			}

			int	state = download.getState();

			return( 	state == Download.ST_WAITING ||
						state == Download.ST_PREPARING ||
						state == Download.ST_READY ||
						state == Download.ST_DOWNLOADING );
		}

		protected boolean
		isSeeding()
		{
			if ( !isComplete()){

				return( false );
			}

			int	state = download.getState();

			return( 	state == Download.ST_WAITING 	||
						state == Download.ST_PREPARING 	||
						state == Download.ST_READY 		||
						state == Download.ST_SEEDING );
		}
		*/

		protected boolean
		isComplete()
		{
			return( download.isComplete() );
		}

		protected boolean
		ignore()
		{
			int	state = download.getState();

			return(  	state == Download.ST_ERROR ||
						state == Download.ST_STOPPED ||
						state == Download.ST_STOPPING );
		}
	}
}
