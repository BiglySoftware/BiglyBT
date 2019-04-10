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

package com.biglybt.ui.config;

import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

import static com.biglybt.core.config.ConfigKeys.Transfer.*;

public class ConfigSectionTransferLAN
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "transfer.lan";

	public ConfigSectionTransferLAN() {
		super(SECTION_ID, ConfigSection.SECTION_TRANSFER, Parameter.MODE_ADVANCED);
	}

	@Override
	public void build() {

		setDefaultUserModeForAdd(Parameter.MODE_ADVANCED);

		BooleanParameterImpl enable_lan = new BooleanParameterImpl(
				BCFG_LAN_SPEED_ENABLED, "ConfigView.section.transfer.lan.enable");

		add(enable_lan);

		IntParameterImpl lan_max_upload = new IntParameterImpl(
				ICFG_MAX_LAN_UPLOAD_SPEED_K_BS,
				"ConfigView.section.transfer.lan.uploadrate");

		add(lan_max_upload);

		IntParameterImpl lan_max_download = new IntParameterImpl(
				ICFG_MAX_LAN_DOWNLOAD_SPEED_K_BS,
				"ConfigView.section.transfer.lan.downloadrate");
		add(lan_max_download);

		enable_lan.addEnabledOnSelection(lan_max_upload, lan_max_download);

	}
}
