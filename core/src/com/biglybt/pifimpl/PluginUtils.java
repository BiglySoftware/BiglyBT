/*
 * Created on 28-Apr-2004
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

package com.biglybt.pifimpl;

/**
 * @author parg
 *
 */

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;

public class
PluginUtils
{
		/**
		 * compare two version strings of form n.n.n.n (e.g. 1.2.3.4)
		 * @param version_1
		 * @param version_2
		 * @return -ve -> version_1 lower, 0 = same, +ve -> version_1 higher
		 */

	public static int
	comparePluginVersions(
		String		version_1,
		String		version_2 )
	{
		return( Constants.compareVersions( version_1, version_2 ));
	}

	private static boolean js_plugin_installing;

	public static void
	installJavaScriptPlugin()
	{
		synchronized( PluginUtils.class ){

			if ( js_plugin_installing ){

				return;
			}

			js_plugin_installing = true;
		}

		boolean	installing = false;

		try{
			UIFunctions uif = UIFunctionsManager.getUIFunctions();

			if ( uif == null ){

				return;
			}

			if ( !Constants.isJava8OrHigher ){

				String title = MessageText.getString("azjscripter.install.fail.jver");

				String text = MessageText.getString("azjscripter.install.fail.jver.text" );

				UIFunctionsUserPrompter prompter = uif.getUserPrompter(title, text, new String[]{
					MessageText.getString("Button.ok"),
				}, 0);

				prompter.setAutoCloseInMS(0);

				prompter.open(null);
			}

			String title = MessageText.getString("azjscripter.install");

			String text = MessageText.getString("azjscripter.install.text" );

			UIFunctionsUserPrompter prompter = uif.getUserPrompter(title, text, new String[] {
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no")
			}, 0);

			String remember_id = "azjscripter.install.remember.id";

			if ( remember_id != null ){

				prompter.setRemember(
					remember_id,
					false,
					MessageText.getString("MessageBoxWindow.nomoreprompting"));
			}

			prompter.setAutoCloseInMS(0);

			prompter.open(null);

			boolean	install = prompter.waitUntilClosed() == 0;

			if ( install ){

				uif.installPlugin(
					"azjscripter",
					"azjscripter.install",
					new UIFunctions.actionListener()
					{
						@Override
						public void
						actionComplete(
							Object		result )
						{
							try{

							}finally{

								synchronized( PluginUtils.class ){

									js_plugin_installing = false;
								}
							}
						}
					});

				installing = true;

			}else{

			}

			return;

		}finally{

			if ( !installing ){

				synchronized( PluginUtils.class ){

					js_plugin_installing = false;
				}
			}
		}
	}
}
