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

package com.biglybt.ui.swt.views.configsections;

import java.util.Map;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminSocksProxy;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.pifimpl.local.ui.config.StringParameterImpl;
import com.biglybt.ui.config.ConfigSectionConnectionProxy;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.BaseSwtParameter;

import com.biglybt.pif.ui.config.ParameterListener;

import static com.biglybt.core.config.ConfigKeys.Connection.*;

public class ConfigSectionConnectionProxySWT
	extends ConfigSectionConnectionProxy
	implements BaseConfigSectionSWT
{
	final NetworkAdminSocksProxy[] test_proxy = {
		null
	};

	public ConfigSectionConnectionProxySWT() {
		super();
		init(mapPluginParams -> Utils.execSWTThread(()->proxyTest()));
	}

	@Override
	public void configSectionCreate(Composite parent, Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam) {
		BooleanParameterImpl enableProxy = (BooleanParameterImpl) getPluginParam(
				BCFG_ENABLE_PROXY);
		BooleanParameterImpl enableSocks = (BooleanParameterImpl) getPluginParam(
				BCFG_ENABLE_SOCKS);
		StringParameterImpl pHost = (StringParameterImpl) getPluginParam(
				SCFG_PROXY_HOST);
		IntParameterImpl pPort = (IntParameterImpl) getPluginParam(SCFG_PROXY_PORT);
		StringParameterImpl pUser = (StringParameterImpl) getPluginParam(
				SCFG_PROXY_USERNAME);
		StringParameterImpl pPass = (StringParameterImpl) getPluginParam(
				SCFG_PROXY_PASSWORD);
		BooleanParameterImpl trackerDNSKill = (BooleanParameterImpl) getPluginParam(
				BCFG_PROXY_SOCKS_TRACKER_DNS_DISABLE);
		ParameterImpl paramButtonTest = getPluginParam(
				ConfigSectionConnectionProxy.ID_BTN_TEST);

		ParameterImpl[] socks_params = {
			enableProxy,
			enableSocks,
			pHost,
			pPort,
			pUser,
			pPass,
			trackerDNSKill
		};

		ParameterListener socks_adapter = param -> {
			boolean enabled = enableProxy.getValue() && enableSocks.getValue()
					&& pHost.getValue().trim().length() > 0 && pPort.getValue() > 0;

			boolean socks_enabled = enableProxy.getValue() && enableSocks.getValue();

			trackerDNSKill.setEnabled(socks_enabled);

			if (enabled) {

				try {

					NetworkAdminSocksProxy nasp = NetworkAdmin.getSingleton().createSocksProxy(
							pHost.getValue(), pPort.getValue(), pUser.getValue(),
							pPass.getValue());

					synchronized (test_proxy) {

						test_proxy[0] = nasp;
					}
				} catch (Throwable e) {

					enabled = false;
				}
			}

			if (!enabled) {

				synchronized (test_proxy) {

					test_proxy[0] = null;
				}
			}

			final boolean f_enabled = enabled;

			Utils.execSWTThread(() -> paramButtonTest.setEnabled(f_enabled));

		};
		for (ParameterImpl socks_param : socks_params) {
			socks_param.addListener(socks_adapter);
		}
		socks_adapter.parameterChanged(null); // init settings
	}

	private void proxyTest() {

		final NetworkAdminSocksProxy target;

		synchronized (test_proxy) {

			target = test_proxy[0];
		}

		if (target == null) {
			return;
		}

		final TextViewerWindow viewer = new TextViewerWindow(
				MessageText.getString("ConfigView.section.proxy.testsocks.title"), null,
				"Testing SOCKS connection to " + target.getHost() + ":"
						+ target.getPort(),
				false);

		final AESemaphore test_done = new AESemaphore("");

		new AEThread2("SOCKS test") {
			@Override
			public void run() {
				try {
					String[] vers = target.getVersionsSupported();

					String ver = "";

					for (String v : vers) {

						ver += (ver.length() == 0 ? "" : ", ") + v;
					}

					appendText(viewer,
							"\r\nConnection OK - supported version(s): " + ver);

				} catch (Throwable e) {

					appendText(viewer, "\r\n" + Debug.getNestedExceptionMessage(e));

				} finally {

					test_done.release();
				}
			}
		}.start();

		new AEThread2("SOCKS test dotter") {
			@Override
			public void run() {
				while (!test_done.reserveIfAvailable()) {

					appendText(viewer, ".");

					try {
						Thread.sleep(500);

					} catch (Throwable e) {

						break;
					}
				}
			}
		}.start();
	}

	private static void appendText(TextViewerWindow viewer, final String line) {
		Utils.execSWTThread(() -> {
			if (!viewer.isDisposed()) {

				viewer.append2(line);
			}
		});
	}
}
