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

package com.biglybt.pifimpl.local.ui.config;

import com.biglybt.pif.ui.config.HyperlinkParameter;
import com.biglybt.pifimpl.local.PluginConfigImpl;

/**
 * @author Allan Crooks
 *
 */
public class HyperlinkParameterImpl extends LabelParameterImpl implements
		HyperlinkParameter {

	private String hyperlink;

	public HyperlinkParameterImpl(PluginConfigImpl config, String key, String label, String hyperlink) {
		super(config, key, label);
		this.hyperlink = hyperlink;
	}

	@Override
	public String getHyperlink() {
		return hyperlink;
	}

	@Override
	public void setHyperlink(String url_location) {
		this.hyperlink = url_location;

		fireParameterChanged();
	}
}
