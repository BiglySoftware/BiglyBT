/*
 * Created on Mar 6, 2009
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.ui.swt.update;


import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.update.Update;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;


public class
SilentInstallUI
{
	private UpdateMonitor			monitor;
	private	UpdateCheckInstance		instance;

	private  boolean				cancelled;

	protected
	SilentInstallUI(
		UpdateMonitor			_monitor,
		UpdateCheckInstance		_instance )
	{
		monitor		= _monitor;
		instance	= _instance;

		try{
			monitor.addDecisionHandler(_instance );

			new AEThread2( "SilentInstallerUI", true )
			{
				@Override
				public void
				run()
				{
					try{
						Update[] updates = instance.getUpdates();

						for ( Update update: updates ){

							ResourceDownloader[] downloaders = update.getDownloaders();

							for ( ResourceDownloader downloader: downloaders ){

								synchronized( SilentInstallUI.this ){

									if ( cancelled ){

										return;
									}
								}

								downloader.download();
							}
						}

						boolean	restart_required = false;

						for (int i=0;i<updates.length;i++){

							if ( updates[i].getRestartRequired() == Update.RESTART_REQUIRED_YES ){

								restart_required = true;
							}
						}

						if ( restart_required ){

							monitor.handleRestart();
						}
					}catch( Throwable e ){

						Debug.out( "Install failed", e );

						instance.cancel();
					}
				}
			}.start();

		}catch( Throwable e ){

			Debug.out( e );

			instance.cancel();
		}
	}
}
