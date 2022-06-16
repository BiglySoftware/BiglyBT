/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
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
 *
 */

package com.biglybt.ui.swt;

import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.*;
import com.biglybt.core.util.*;

import com.biglybt.core.CoreLifecycleAdapter;

/**
 * @author TuxPaper
 * @created Nov 3, 2006
 *
 */
public class UIConfigDefaultsSWTv3
{
	public static void initialize(Core core) {
		ConfigurationManager config = ConfigurationManager.getInstance();

		if ("az2".equalsIgnoreCase(config.getStringParameter("ui", "az3"))) {
			return;
		}

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		boolean startAdvanced = userMode > 1;

		final ConfigurationDefaults defaults = ConfigurationDefaults.getInstance();

		defaults.addParameter("ui", "az3");


		defaults.addParameter("Auto Upload Speed Enabled", true);
		defaults.addParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS,
				startAdvanced
						? ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_ALWAYS
						: ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_MANY);

		// defaults.addParameter("Add URL Silently", true);			not used 11/30/2015 - see "Activate Window On External Download"
		// defaults.addParameter("add_torrents_silently", true);	not used 11/30/2015

		defaults.addParameter("Popup Download Finished", false);
		defaults.addParameter("Popup Download Added", false);

		defaults.addParameter("Status Area Show SR", false);
		defaults.addParameter("Status Area Show NAT", false);
		defaults.addParameter("Status Area Show IPF", false);
		defaults.addParameter("Status Area Show RIP", true);

		defaults.addParameter("Message Popup Autoclose in Seconds", 10 );

		defaults.addParameter("window.maximized", true);

		defaults.addParameter("update.autodownload", true);

		//defaults.addParameter("suppress_file_download_dialog", true);

		defaults.addParameter("auto_remove_inactive_items", false);

		defaults.addParameter("show_torrents_menu", false);

		config.removeParameter("v3.home-tab.starttab");
		defaults.addParameter("MyTorrentsView.table.style", 0);
		defaults.addParameter("v3.Show Welcome", true);

		defaults.addParameter("Library.viewmode", startAdvanced ? 1 : 0);
		defaults.addParameter("LibraryDL.viewmode", startAdvanced ? 1 : 0);
		defaults.addParameter("LibraryDL.UseDefaultIndicatorColor", false );
		defaults.addParameter("LibraryUnopened.viewmode", startAdvanced ? 1 : 0);
		defaults.addParameter("LibraryCD.viewmode", startAdvanced ? 1 : 0);
		defaults.addParameter("Library.EnableSimpleView", 1 );
		defaults.addParameter("Library.CatInSideBar", startAdvanced ? 1 : 0);
		defaults.addParameter("Library.TagInSideBar", 1 );
		defaults.addParameter("Library.TagGroupsInSideBar", true );
		defaults.addParameter("Library.ShowTabsInTorrentView", 1 );
		defaults.addParameter("list.dm.dblclick", "0");

		//=== defaults used by MainWindow
		defaults.addParameter("vista.adminquit", false);
		defaults.addParameter("Start Minimized", false);
		defaults.addParameter("Password enabled", false);
		defaults.addParameter("ToolBar.showText", true);

		defaults.addParameter("Table.extendedErase", !Constants.isWindowsXP);
		defaults.addParameter("Table.useTree", true);

		// by default, turn off some slidey warning
		// Since they are plugin configs, we need to set the default after the
		// plugin sets the default
		core.addLifecycleListener(new CoreLifecycleAdapter() {
			@Override
			public void started(Core core) {
				defaults.addParameter("Plugin.DHT.dht.warn.user", false);
				defaults.addParameter("Plugin.UPnP.upnp.alertothermappings", false);
				defaults.addParameter("Plugin.UPnP.upnp.alertdeviceproblems", false);
			}
		});
	}
}
