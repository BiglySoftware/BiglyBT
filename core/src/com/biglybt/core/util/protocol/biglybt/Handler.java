/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.util.protocol.biglybt;

/**
 * @author parg
 *
 */

import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.protocol.AzURLStreamHandlerSkipConnection;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.installer.PluginInstaller;
import com.biglybt.pif.installer.StandardPlugin;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pifimpl.local.PluginInitializer;

public class
Handler
	extends URLStreamHandler
	implements AzURLStreamHandlerSkipConnection
{
	@Override
	public URLConnection
	openConnection(URL u)
	{
		return new BiglyBTURLConnection(u);
	}


	@Override
	public boolean canProcessWithoutConnection(URL url, boolean processUrlNow) {

		String host = url.getHost();
		if (host.equalsIgnoreCase("install-plugin") && url.getPath().length() > 1) {
			String plugin_id = url.getPath().substring(1);

			PluginInstaller installer = CoreFactory.getSingleton().getPluginManager().getPluginInstaller();

			try {
				StandardPlugin installablePlugin = installer.getStandardPlugin(plugin_id);
				if (installablePlugin == null) {
					UIManager ui_manager = PluginInitializer.getDefaultInterface().getUIManager();

					String details = MessageText.getString(
							"plugininstall.notfound.desc", new String[] { plugin_id });

					ui_manager.showMessageBox(
							"plugininstall.notfound.title",
							"!" + details + "!",
							UIManagerEvent.MT_OK );
					return true;
				}

				installer.requestInstall(MessageText.getString("plugininstall.biglybturl"), installablePlugin);
			} catch (PluginException e) {
				UIManager ui_manager = PluginInitializer.getDefaultInterface().getUIManager();

				ui_manager.showMessageBox(
						"plugininstall.error.title",
						"!" + e.getMessage() + "!",
						UIManagerEvent.MT_OK );

				Debug.out(e);
			}


			return true;
		}
		return false;
	}
}
