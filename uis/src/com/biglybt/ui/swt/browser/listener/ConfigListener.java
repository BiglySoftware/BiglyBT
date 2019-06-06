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

package com.biglybt.ui.swt.browser.listener;

import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.AEDiagnosticsLogger;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.BrowserWrapper;
import com.biglybt.ui.swt.update.UpdateMonitor;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.messenger.browser.BrowserMessage;
import com.biglybt.core.messenger.browser.listeners.AbstractBrowserMessageListener;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.util.ConstantsVuze;
import com.biglybt.util.MapUtils;
import com.biglybt.net.magneturi.MagnetURIHandler;

/**
 * @author TuxPaper
 * @created Mar 30, 2007
 *
 */
public class ConfigListener
	extends AbstractBrowserMessageListener
{
	public static final String DEFAULT_LISTENER_ID = "config";

	public static final String OP_GET_VERSION = "get-version";

	public static final String OP_NEW_INSTALL = "is-new-install";

	public static final String OP_CHECK_FOR_UPDATES = "check-for-updates";

	public static final String OP_GET_MAGNET_PORT = "get-magnet-port";

	public static final String OP_LOG_DIAGS = "log-diags";

	public static final String OP_LOG = "log";

	public ConfigListener(String id, BrowserWrapper browser) {
		super(id);
	}

	/**
	 *
	 */
	public ConfigListener(BrowserWrapper browser) {
		this(DEFAULT_LISTENER_ID, browser);
	}

	// @see com.biglybt.ui.swt.browser.msg.AbstractMessageListener#handleMessage(com.biglybt.ui.swt.browser.msg.BrowserMessage)
	@Override
	public void handleMessage(BrowserMessage message) {
		try {
			String opid = message.getOperationId();

			if (OP_GET_VERSION.equals(opid)) {
				Map decodedMap = message.getDecodedMap();
				String callback = MapUtils.getMapString(decodedMap, "callback", null);
				if (callback != null) {
					context.executeInBrowser(callback + "('" + Constants.BIGLYBT_VERSION + "')");
				} else {
					message.debug("bad or no callback param");
				}
			} else if (OP_NEW_INSTALL.equals(opid)) {
				Map decodedMap = message.getDecodedMap();
				String callback = MapUtils.getMapString(decodedMap, "callback", null);
				if (callback != null) {
					context.executeInBrowser(callback + "(" + COConfigurationManager.isNewInstall() + ")");
				} else {
					message.debug("bad or no callback param");
				}
			} else if (OP_CHECK_FOR_UPDATES.equals(opid)) {

				checkForUpdates();

			} else if (OP_GET_MAGNET_PORT.equals(opid)) {

				Map decodedMap = message.getDecodedMap();

				String callback = MapUtils.getMapString(decodedMap, "callback", null);

				if (callback != null) {

					context.executeInBrowser(callback + "('" + MagnetURIHandler.getSingleton().getPort() + "')");

				} else {

					message.debug("bad or no callback param");
				}
			}else if ( OP_LOG_DIAGS.equals( opid )){

				logDiagnostics();
			} else if ( OP_LOG.equals(opid)) {
				Map decodedMap = message.getDecodedMap();
				String loggerName = MapUtils.getMapString(decodedMap, "log-name",
						"browser");
				String text = MapUtils.getMapString(decodedMap, "text", "");

				AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger(loggerName);
				diag_logger.log(text);
				if (ConstantsVuze.DIAG_TO_STDOUT) {
					System.out.println(Thread.currentThread().getName() + "|"
							+ System.currentTimeMillis() + "] " + text);
				}
			}
		} catch (Throwable t) {
			message.debug("handle Config message", t);
		}
	}

	public static void
	logDiagnostics()
	{
		AEDiagnostics.dumpThreads();
	}

	/**
	 *
	 *
	 * @since 3.0.5.3
	 */
	public static void checkForUpdates() {
		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {
			uiFunctions.bringToFront();
		}
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				UpdateMonitor.getSingleton(core).performCheck(true, false, false, null);
			}
		});
	}
}
