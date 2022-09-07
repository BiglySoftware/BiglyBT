/*
 * File    : Handler.java
 * Created : 19-Jan-2004
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

package com.biglybt.core.util.protocol.wss;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;

public class
Handler
	extends URLStreamHandler
{
	private static boolean	install_prompted	= false;

	@Override
	public URLConnection
	openConnection(URL u)

		throws IOException
	{
		return( getProxy(u).openConnection());
	}

	private URL
	getProxy(
		URL		u )

		throws IOException
	{
		PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "azwebtorrent" );

		if ( pi == null ){

			installPlugin();

			throw( new IOException( "'WebTorrent Support Plugin' is required - go to 'Tools->Plugins->Installation Wizard' to install." ));
		}

		IPCInterface ipc = pi.getIPC();

		try{
			URL url = (URL)ipc.invoke( "getProxyURL", new Object[]{ u });

			return( url );

		}catch( IPCException ipce ){

			Throwable e = ipce;

			if ( e.getCause() != null ){

				e = e.getCause();
			}

			throw( new IOException( "Communication error with WebTorrent Support Plugin: " + Debug.getNestedExceptionMessage(e)));
		}
	}

	private static void
	installPlugin()
	{
		synchronized( Handler.class ){

			if ( install_prompted ){

				return;
			}

			install_prompted = true;
		}

		if ( !Constants.isJava11OrHigher ){
			
			return;
		}
		
		new AEThread2( "install::async" )
		{
			@Override
			public void
			run()
			{

				try{
					UIFunctions uif = UIFunctionsManager.getUIFunctions();

					if ( uif == null ){

						return;
					}

					String title = MessageText.getString("azwebtorrent.install");

					String text = MessageText.getString("azwebtorrent.install.text" );

					UIFunctionsUserPrompter prompter = uif.getUserPrompter(title, text, new String[] {
						MessageText.getString("Button.yes"),
						MessageText.getString("Button.no")
					}, 0);

					String remember_id = "azwebtorrent.install.remember.id";

					if ( remember_id != null ){

						prompter.setRemember(
							remember_id,
							false,
							MessageText.getString("MessageBoxWindow.nomoreprompting"));
					}

					prompter.setAutoCloseInMS(0);

					prompter.open(null);

					boolean	install = prompter.waitUntilClosed() == 0;

					if ( install == false) {
						return;
					}

					uif.installPlugin("azwebtorrent", "azwebtorrent.install",
							new UIFunctions.actionListener()
							{
								@Override public void actionComplete(Object result) {
								}
							});


				}finally{
				}
			}
		}.start();
	}
}
