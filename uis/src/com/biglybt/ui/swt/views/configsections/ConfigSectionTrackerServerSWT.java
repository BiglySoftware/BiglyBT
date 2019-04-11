/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.views.configsections;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.ui.config.ConfigSectionTrackerServer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.auth.CertificateCreatorWindow;
import com.biglybt.ui.swt.ipchecker.IpCheckerWizard;

public class ConfigSectionTrackerServerSWT
	extends ConfigSectionTrackerServer
{
	public ConfigSectionTrackerServerSWT() {
		init(mapPluginParams -> Utils.execSWTThread(() -> {
			IpCheckerWizard wizard = new IpCheckerWizard();
			wizard.setIpSetterCallBack(ip -> COConfigurationManager.setParameter(
					ConfigKeys.Tracker.SCFG_TRACKER_IP, ip));
		}), mapPluginParams -> new CertificateCreatorWindow());
	}
}
