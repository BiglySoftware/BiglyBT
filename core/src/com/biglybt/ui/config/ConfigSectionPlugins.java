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

import java.io.File;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pifimpl.local.ui.config.HyperlinkParameterImpl;
import com.biglybt.pifimpl.local.ui.config.LabelParameterImpl;
import com.biglybt.pifimpl.local.ui.config.UIParameterImpl;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.UIParameterContext;

public class ConfigSectionPlugins
	extends ConfigSectionImpl
{

	private UIParameterContext paramContextPluginList;

	public ConfigSectionPlugins() {
		super(ConfigSection.SECTION_PLUGINS, ConfigSection.SECTION_ROOT);
	}

	public void init(UIParameterContext paramContextPluginList) {
		this.paramContextPluginList = paramContextPluginList;
	}

	@Override
	public void build() {

		if (!CoreFactory.isCoreRunning()) {
			add(new LabelParameterImpl("core.not.available"));
		}

		String sep = File.separator;

		File fUserPluginDir = FileUtil.getUserFile("plugins");

		String sUserPluginDir;

		try {
			sUserPluginDir = fUserPluginDir.getCanonicalPath();
		} catch (Throwable e) {
			sUserPluginDir = fUserPluginDir.toString();
		}

		if (!sUserPluginDir.endsWith(sep)) {
			sUserPluginDir += sep;
		}

		File fAppPluginDir = FileUtil.getApplicationFile("plugins");

		String sAppPluginDir;

		try {
			sAppPluginDir = fAppPluginDir.getCanonicalPath();
		} catch (Throwable e) {
			sAppPluginDir = fAppPluginDir.toString();
		}

		if (!sAppPluginDir.endsWith(sep)) {
			sAppPluginDir += sep;
		}

		add(new LabelParameterImpl("ConfigView.pluginlist.whereToPut"));

		File dirUserPlugin = FileUtil.newFile(sUserPluginDir);
		if (!(dirUserPlugin.exists() && dirUserPlugin.isDirectory())) {
			dirUserPlugin = dirUserPlugin.getParentFile();
		}
		HyperlinkParameterImpl paramUserPluginDir = new HyperlinkParameterImpl(
				"!" + sUserPluginDir.replaceAll("&", "&&") + "!",
				dirUserPlugin.getAbsolutePath());
		add(paramUserPluginDir);
		paramUserPluginDir.setIndent(1, false);

		add(new LabelParameterImpl("ConfigView.pluginlist.whereToPutOr"));

		File dirAppPlugin = FileUtil.newFile(sAppPluginDir);
		if (!(dirAppPlugin.exists() && dirAppPlugin.isDirectory())) {
			dirAppPlugin = dirAppPlugin.getParentFile();
		}
		HyperlinkParameterImpl paramAppPluginDir = new HyperlinkParameterImpl(
				"!" + sAppPluginDir.replaceAll("&", "&&") + "!",
				dirAppPlugin.getAbsolutePath());
		add(paramAppPluginDir);
		paramAppPluginDir.setIndent(1, false);

		if (paramContextPluginList != null) {
			add(new LabelParameterImpl("ConfigView.pluginlist.info"));

			add("PluginList", new UIParameterImpl(paramContextPluginList, null));
		} else {
			// could setup params for each plugin
		}

	}
}
