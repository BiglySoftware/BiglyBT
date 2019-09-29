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

package com.biglybt.ui.swt.extlistener;

import java.util.Collections;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.messenger.ClientMessageContext;
import com.biglybt.core.messenger.PlatformMessenger;
import com.biglybt.core.messenger.browser.BrowserMessage;
import com.biglybt.core.messenger.browser.BrowserMessageDispatcher;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.swt.browser.listener.ConfigListener;
import com.biglybt.ui.swt.browser.listener.DisplayListener;
import com.biglybt.ui.swt.browser.listener.TorrentListener;
import com.biglybt.ui.swt.donations.DonationWindow;
import com.biglybt.ui.swt.shells.main.MainWindow;
import com.biglybt.ui.swt.utils.TorrentUIUtilsV3;
import com.biglybt.util.*;

/**
 * @author TuxPaper
 * @created Feb 7, 2008
 *
 */
public class StimulusRPC
{
	private static final String KEY_SOURCE_REF = "source-ref";
	private static MyExternalStimulusListener externalStimulusListener;

	/**
	 * Hooks some listeners
	 */
	public static void hookListeners(final Core core,
			final MainWindow mainWindow) {
		/*
		 * This code block was moved here from being in-line in MainWindow
		 */
		externalStimulusListener = new MyExternalStimulusListener(core, mainWindow);
		ExternalStimulusHandler.addListener(externalStimulusListener);
	}

	public static void unhookListeners() {
		if (externalStimulusListener != null) {
			ExternalStimulusHandler.removeListener(externalStimulusListener);
			externalStimulusListener = null;
		}
	}

	private static class MyExternalStimulusListener implements ExternalStimulusListener {
		private final Core core;
		private final MainWindow mainWindow;

		public MyExternalStimulusListener(Core core, MainWindow mainWindow) {
			this.core = core;
			this.mainWindow = mainWindow;
		}

		@Override
		public boolean receive(String name, Map values) {
			try {
				if (values == null) {
					return false;
				}

				if (!name.equals("AZMSG")) {
					return false;
				}

				Object valueObj = values.get("value");
				if (!(valueObj instanceof String)) {
					return false;
				}

				String value = (String) valueObj;

				ClientMessageContext context = PlatformMessenger.getClientMessageContext();
				if (context == null) {
					return false;
				}

				// AZMSG;x;listener-id;op-id;params
				String[] splitVal = value.split(";", 5);
				if (splitVal.length != 5) {
					return false;
				}
				String lId = splitVal[2];
				String opId = splitVal[3];
				Map decodedMap = JSONUtils.decodeJSON(splitVal[4]);
				if (decodedMap == null) {
					decodedMap = Collections.EMPTY_MAP;
				}

				if (opId.equals(TorrentListener.OP_LOAD_TORRENT)) {
					if (decodedMap.containsKey("b64")) {
						String b64 = MapUtils.getMapString(decodedMap, "b64", null);
						return TorrentListener.loadTorrentByB64(core, b64);
					} else if (decodedMap.containsKey("url")) {
						String url = MapUtils.getMapString(decodedMap, "url", null);

						boolean blocked = UrlFilter.getInstance().urlIsBlocked(url);
						// Security: Only allow torrents from whitelisted urls
						if (blocked) {
							Debug.out("stopped loading torrent URL because it's not in whitelist");
							return false;
						}

						boolean playNow = MapUtils.getMapBoolean(decodedMap, "play-now",
								false);
						boolean playPrepare = MapUtils.getMapBoolean(decodedMap,
								"play-prepare", false);
						boolean bringToFront = MapUtils.getMapBoolean(decodedMap,
								"bring-to-front", true);


						DownloadUrlInfo dlInfo = new DownloadUrlInfo(url);
						dlInfo.setReferer(MapUtils.getMapString(decodedMap, "referer",
								null));

						TorrentUIUtilsV3.loadTorrent(dlInfo, playNow, playPrepare,
								bringToFront);

						return true;
					}
				} else if (opId.equals("is-ready")) {
					// The platform needs to know when it can call open-url, and it
					// determines this by the is-ready function
					return mainWindow.isReady();
				} else if (opId.equals("is-version-ge")) {
					if (decodedMap.containsKey("version")) {
						String id = MapUtils.getMapString(decodedMap, "id", "client");
						String version = MapUtils.getMapString(decodedMap, "version", "");
						if (id.equals("client")) {
							return com.biglybt.core.util.Constants.compareVersions(
									com.biglybt.core.util.Constants.BIGLYBT_VERSION,
									version) >= 0;
						}
					}
					return false;

				} else if (opId.equals("is-active-tab")) {
					if (decodedMap.containsKey("tab")) {
						String tabID = MapUtils.getMapString(decodedMap, "tab", "");
						if (tabID.length() > 0) {
							// 3.2 TODO: Should we be checking for partial matches?
							MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
							MdiEntry entry = mdi.getCurrentEntry();
							if (entry != null) {
								return entry.getViewID().equals(tabID);
							}
						}
					}

					return false;

				} else if (ConfigListener.DEFAULT_LISTENER_ID.equals(lId)) {
					if (ConfigListener.OP_NEW_INSTALL.equals(opId)) {
						return COConfigurationManager.isNewInstall();
					} else if (ConfigListener.OP_CHECK_FOR_UPDATES.equals(opId)) {
						ConfigListener.checkForUpdates();
						return true;
					} else if (ConfigListener.OP_LOG_DIAGS.equals(opId)) {
						ConfigListener.logDiagnostics();
						return true;
					}
				} else if (DisplayListener.OP_SHOW_DONATION_WINDOW.equals(lId)) {
					DonationWindow.open(true, MapUtils.getMapString(decodedMap,
							KEY_SOURCE_REF, "SRPC"));
				}


				if (System.getProperty(
						"browser.route.all.external.stimuli.for.testing", "false").equalsIgnoreCase(
						"true")) {

					BrowserMessageDispatcher dispatcher = context.getDispatcher();
					if (dispatcher != null) {
						dispatcher.dispatch(new BrowserMessage(lId, opId, decodedMap));
					}
				} else {

					System.err.println("Unhandled external stimulus: " + value);
				}
			} catch (Exception e) {
				Debug.out(e);
			}
			return false;
		}

		@Override
		public int query(String name, Map values) {
			return (Integer.MIN_VALUE);
		}
	}
}
