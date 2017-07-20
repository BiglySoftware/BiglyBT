/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.player;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.installer.InstallablePlugin;
import com.biglybt.pif.installer.PluginInstallationListener;
import com.biglybt.pif.installer.PluginInstaller;
import com.biglybt.pif.installer.StandardPlugin;
import com.biglybt.pif.update.Update;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.update.UpdateCheckInstanceListener;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter;

public class PlayerInstaller {

	private PlayerInstallerListener listener;
	private PluginInstaller installer;
	private volatile UpdateCheckInstance instance;

	private boolean	cancelled;

	public PlayerInstaller() {
	}

	public void setListener(PlayerInstallerListener listener) {
		this.listener = listener;
	}

	public void
	cancel()
	{
		UpdateCheckInstance to_cancel = null;

		synchronized( this ){

			cancelled = true;

			to_cancel = instance;
		}

		if ( to_cancel != null ){

			to_cancel.cancel();
		}
	}

	public boolean install() {

		try{

			installer = CoreFactory.getSingleton().getPluginManager().getPluginInstaller();

	 		StandardPlugin sp = installer.getStandardPlugin( "azemp" );

			Map<Integer, Object> properties = new HashMap<>();

			properties.put( UpdateCheckInstance.PT_UI_STYLE, UpdateCheckInstance.PT_UI_STYLE_NONE );

			properties.put(UpdateCheckInstance.PT_UI_DISABLE_ON_SUCCESS_SLIDEY, true);

			final AESemaphore sem = new AESemaphore("emp install");
			final boolean[] result = new boolean[1];

			instance =
				installer.install(
					new InstallablePlugin[]{ sp },
					false,
					properties,
					new PluginInstallationListener() {

						@Override
						public void
						completed()
						{
							result[0] = true;
							if(listener != null) {
								listener.finished();
							}
							sem.release();
						}

						@Override
						public void
						cancelled()
						{
							result[0] = false;
							if(listener != null) {
								listener.finished();
							}
							sem.release();
						}

						@Override
						public void
						failed(
							PluginException	e )
						{
							result[0] = false;
							if(listener != null) {
								listener.finished();
							}
							sem.release();
						}
					});

			boolean kill_it;

			synchronized( this ){

				kill_it = cancelled;
			}

			if ( kill_it ){

				instance.cancel();

				return( false );
			}

			instance.addListener(
				new UpdateCheckInstanceListener() {

					@Override
					public void
					cancelled(
						UpdateCheckInstance		instance )
					{
					}

					@Override
					public void
					complete(
						UpdateCheckInstance		instance )
					{
	  					Update[] updates = instance.getUpdates();

	 					for ( final Update update: updates ){

	 						ResourceDownloader[] rds = update.getDownloaders();

	 						for ( ResourceDownloader rd: rds ){

	 							rd.addListener(
	 								new ResourceDownloaderAdapter()
	 								{
	 									@Override
									  public void
	 									reportActivity(
	 										ResourceDownloader	downloader,
	 										String				activity )
	 									{

	 									}

	 									@Override
									  public void
	 									reportPercentComplete(
	 										ResourceDownloader	downloader,
	 										int					percentage )
	 									{
	 										if(listener != null) {
	 											listener.progress(percentage);
	 										}
	 									}
	 								});
	 						}
	 					}
					}
				});

			sem.reserve();

			return result[0];

		}catch( Throwable e ){


		}
		return false;
	}

}
