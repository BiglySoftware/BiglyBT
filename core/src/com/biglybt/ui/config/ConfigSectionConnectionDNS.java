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

import com.biglybt.core.config.ConfigKeys.*;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.HyperlinkParameterImpl;
import com.biglybt.pifimpl.local.ui.config.LabelParameterImpl;
import com.biglybt.pifimpl.local.ui.config.StringParameterImpl;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

public class ConfigSectionConnectionDNS
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "DNS";

	public ConfigSectionConnectionDNS() {
		super(SECTION_ID, ConfigSection.SECTION_CONNECTION,
				Parameter.MODE_ADVANCED);
	}

	@Override
	public void build() {

		add(new LabelParameterImpl("ConfigView.section.dns.info"));

		add(new HyperlinkParameterImpl("ConfigView.label.please.visit.here",
				Wiki.DNS));

		add(new StringParameterImpl(Connection.SCFG_DNS_ALT_SERVERS,
				"ConfigView.section.dns.alts"));

		add(new BooleanParameterImpl(Connection.BCFG_DNS_ALT_SERVERS_SOCKS_ENABLE,
				"ConfigView.section.dns.allow_socks"));
	}
}
