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

package com.biglybt.ui.swt.browser.listener;

import java.io.File;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import org.gudy.bouncycastle.util.encoders.Base64;

import com.biglybt.core.messenger.ClientMessageContext;
import com.biglybt.core.messenger.ClientMessageContext.torrentURLHandler;
import com.biglybt.core.messenger.browser.BrowserMessage;
import com.biglybt.core.messenger.browser.listeners.AbstractBrowserMessageListener;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.swt.utils.TorrentUIUtilsV3;
import com.biglybt.util.MapUtils;

public class TorrentListener
	extends AbstractBrowserMessageListener
{
	public static final String DEFAULT_LISTENER_ID = "torrent";

	public static final String OP_LOAD_TORRENT_OLD = "loadTorrent";

	public static final String OP_LOAD_TORRENT = "load-torrent";

	private ClientMessageContext.torrentURLHandler		torrentURLHandler;

	public TorrentListener(String id) {
		super(id);
	}

	public TorrentListener() {
		this(DEFAULT_LISTENER_ID);
	}

	public void
	setTorrentURLHandler(
		torrentURLHandler handler)
	{
		torrentURLHandler = handler;
	}

	public void setShell(Shell shell) {
	}

	@Override
	public void handleMessage(final BrowserMessage message) {
		String opid = message.getOperationId();
		if (OP_LOAD_TORRENT.equals(opid) || OP_LOAD_TORRENT_OLD.equals(opid)) {
			final Map decodedMap = message.getDecodedMap();
			String url = MapUtils.getMapString(decodedMap, "url", null);
			final boolean playNow = MapUtils.getMapBoolean(decodedMap, "play-now", false);
			final boolean playPrepare = MapUtils.getMapBoolean(decodedMap, "play-prepare",
					false);
			final boolean bringToFront = MapUtils.getMapBoolean(decodedMap,
					"bring-to-front", true);
			if (url != null) {
				if ( torrentURLHandler != null ){

					try{
						torrentURLHandler.handleTorrentURL( url );

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
				final DownloadUrlInfo dlInfo = new DownloadUrlInfo(url);
				dlInfo.setReferer(message.getReferer());

				CoreFactory.addCoreRunningListener(new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						TorrentUIUtilsV3.loadTorrent(dlInfo, playNow, playPrepare,
								bringToFront);
					}
				});
			} else {
				CoreFactory.addCoreRunningListener(new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						loadTorrentByB64(core, message, MapUtils.getMapString(decodedMap,
								"b64", null));
					}
				});
			}
		} else {
			throw new IllegalArgumentException("Unknown operation: " + opid);
		}
	}

	public static boolean loadTorrentByB64(Core core, String b64) {
		return loadTorrentByB64(core, null, b64);
	}

	/**
	 * @since 1.0.0.0
	 */
	private static boolean loadTorrentByB64(Core core,
			BrowserMessage message, String b64) {
		if (b64 == null) {
			return false;
		}

		byte[] decodedTorrent = Base64.decode(b64);

		File tempTorrentFile;
		try {
			tempTorrentFile = File.createTempFile("AZU", ".torrent");
			tempTorrentFile.deleteOnExit();
			String filename = tempTorrentFile.getAbsolutePath();
			FileUtil.writeBytesAsFile(filename, decodedTorrent);

			TOTorrent torrent = TorrentUtils.readFromFile(tempTorrentFile, false);
			// Security: Only allow torrents from whitelisted trackers
			if (!PlatformTorrentUtils.isPlatformTracker(torrent)) {
				Debug.out("stopped loading torrent because it's not in whitelist");
				return false;
			}

			String savePath = COConfigurationManager.getStringParameter("Default save path");
			if (savePath == null || savePath.length() == 0) {
				savePath = ".";
			}

			core.getGlobalManager().addDownloadManager(filename, savePath);
		} catch (Throwable t) {
			if (message != null) {
				message.debug("loadUrl error", t);
			} else {
				Debug.out(t);
			}
			return false;
		}
		return true;
	}
}
