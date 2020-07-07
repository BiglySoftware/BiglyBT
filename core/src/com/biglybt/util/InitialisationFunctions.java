/*
 * Created on 14-Sep-2006
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

package com.biglybt.util;

import com.biglybt.core.Core;
import com.biglybt.core.content.PlatformContentDirectory;
import com.biglybt.core.content.RelatedContentManager;
/* Android: No Devices
import com.biglybt.core.devices.DeviceManagerFactory;
 */
import com.biglybt.core.download.DownloadManagerEnhancer;
import com.biglybt.core.metasearch.MetaSearchManagerFactory;
import com.biglybt.core.metasearch.MetaSearchManagerListener;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.download.DownloadWillBeAddedListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.plugin.net.buddy.BuddyPluginUI;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

public class InitialisationFunctions
{
	public static void earlyInitialisation(Core core) {

		DownloadManagerEnhancer.initialise(core);

		hookDownloadAddition();

		PlatformContentDirectory.register();

		MetaSearchManagerFactory.preInitialise();

		SubscriptionManagerFactory.preInitialise();

/* Android: No Devices
		DeviceManagerFactory.preInitialise();
 */

		NavigationHelper.initialise();

		RelatedContentManager.preInitialise( core );

		earlySWTInitialise();
	}

	private static void
	earlySWTInitialise()
	{
			// it is possible that UIF ain't available yet so try and make sure this runs sometime!

		UIFunctionsManager.execWithUIFunctions(
			new UIFunctionsManager.UIFCallback()
			{
				@Override
				public void
				run(
					UIFunctions uif )
				{
					if (uif.getUIType().equals(UIInstance.UIT_SWT) ){

						BuddyPluginUI.preInitialize();
					}
				}
			});
	}

	public static void
	lateInitialisation(
		Core core )
	{
		ExternalStimulusHandler.initialise(core);

		PluginInitializer.getDefaultInterface().getUtilities().createDelayedTask(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					MetaSearchManagerFactory.getSingleton();

					SubscriptionManagerFactory.getSingleton();

					try{
						RelatedContentManager.getSingleton();

					}catch( Throwable e ){

						Debug.out( e );
					}

					try{
						MetaSearchManagerFactory.getSingleton().addListener(
							new MetaSearchManagerListener()
							{
								@Override
								public void
								searchRequest(
									String		term )
								{
									UIFunctionsManager.getUIFunctions().doSearch( term );
								}
							});
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}).queue();
	}

	protected static void
	hookDownloadAddition()
	{
		PluginInterface pi = PluginInitializer.getDefaultInterface();

		DownloadManager	dm = pi.getDownloadManager();

			// need to get in early to ensure property present on initial announce

		dm.addDownloadWillBeAddedListener(
			new DownloadWillBeAddedListener()
			{
				@Override
				public void
				initialised(
					Download 	download )
				{
						// unfortunately the has-been-opened state is updated by the client when a user opens content
						// but is also preserved across torrent export/import (e.g. when downloaded via magnet
						// URL. So reset it here if it is found to be set

					com.biglybt.core.download.DownloadManager dm = PluginCoreUtils.unwrap( download );

					if ( PlatformTorrentUtils.getHasBeenOpened( dm )){

						PlatformTorrentUtils.setHasBeenOpened( dm, false );
					}
				}
			});

	}

}
