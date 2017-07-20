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

import java.util.Map;

import com.biglybt.core.messenger.browser.BrowserMessage;
import com.biglybt.core.messenger.browser.listeners.AbstractBrowserMessageListener;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.swt.donations.DonationWindow;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.util.MapUtils;

public class DisplayListener
	extends AbstractBrowserMessageListener
{

	public static final String DEFAULT_LISTENER_ID = "display";

	// Needed for Creating Search Template
	public static final String OP_COPY_TO_CLIPBOARD = "copy-text";

	public static final String OP_BRING_TO_FRONT = "bring-to-front";

	public static final String OP_SHOW_DONATION_WINDOW = "show-donation-window";

	public static final String OP_OPEN_SEARCH = "open-search";

	public DisplayListener(String id) {
		super(id);
	}

	/**
	 *
	 */
	public DisplayListener() {
		this(DEFAULT_LISTENER_ID);
	}

	@Override
	public void handleMessage(BrowserMessage message) {
		String opid = message.getOperationId();

		if (OP_COPY_TO_CLIPBOARD.equals(opid)) {
			Map decodedMap = message.getDecodedMap();
			ClipboardCopy.copyToClipBoard(MapUtils.getMapString(decodedMap, "text", ""));
		} else if (OP_BRING_TO_FRONT.equals(opid)) {
			bringToFront();
		} else if (OP_SHOW_DONATION_WINDOW.equals(opid)) {
			Map decodedMap = message.getDecodedMap();
			DonationWindow.open(true, MapUtils.getMapString(decodedMap, "source-ref",
					"RPC"));
		} else if (OP_OPEN_SEARCH.equals(opid)) {
			Map decodedMap = message.getDecodedMap();
			UIFunctions uif = UIFunctionsManager.getUIFunctions();
			if (uif != null) {
				uif.doSearch(MapUtils.getMapString(decodedMap, "search-text", ""));
			}
		} else {
			throw new IllegalArgumentException("Unknown operation: " + opid);
		}
	}

	/**
	 *
	 */
	private void bringToFront() {
		final UIFunctions functions = UIFunctionsManager.getUIFunctions();
		if (functions != null) {
			functions.bringToFront();
		}
	}

}
