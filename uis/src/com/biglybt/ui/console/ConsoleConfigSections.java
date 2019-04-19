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

package com.biglybt.ui.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.biglybt.pifimpl.local.ui.config.ConfigSectionRepository;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.*;

import com.biglybt.pif.ui.config.Parameter;

public class ConsoleConfigSections
{

	private static ConsoleConfigSections instance;

	private final BaseConfigSection[] internalSections;

	public static ConsoleConfigSections getInstance() {
		synchronized (ConsoleConfigSections.class) {
			if (instance == null) {
				instance = new ConsoleConfigSections();
			}
		}
		return instance;
	}

	public ConsoleConfigSections() {
		internalSections = new BaseConfigSection[] {
			new ConfigSectionMode(),
			new ConfigSectionStartShutdown(),
			new ConfigSectionBackupRestore(),
			new ConfigSectionConnection(),
			new ConfigSectionConnectionProxy(),
			new ConfigSectionConnectionAdvanced(),
			new ConfigSectionConnectionEncryption(),
			new ConfigSectionConnectionDNS(),
			new ConfigSectionTransfer(),
			new ConfigSectionTransferAutoSpeedSelect(),
			new ConfigSectionTransferAutoSpeedClassic(),
			new ConfigSectionTransferAutoSpeedV2(),
			new ConfigSectionTransferLAN(),
			new ConfigSectionFile(),
			new ConfigSectionFileMove(),
			new ConfigSectionFileTorrents(),
			new ConfigSectionFileTorrentsDecoding(),
			new ConfigSectionFilePerformance(),
//		  new ConfigSectionInterfaceSWT(),
			new ConfigSectionInterfaceLanguage(),
//			  new ConfigSectionInterfaceStartSWT(),
//			  new ConfigSectionInterfaceDisplaySWT(),
//			  new ConfigSectionInterfaceTablesSWT(),
//			  new ConfigSectionInterfaceColorSWT(),
//			  new ConfigSectionInterfaceAlertsSWT(),
//			  new ConfigSectionInterfacePasswordSWT(),
//			  new ConfigSectionInterfaceLegacySWT(),
			new ConfigSectionIPFilter(),
			new ConfigSectionPlugins(),
			new ConfigSectionStats(),
			new ConfigSectionTracker(),
			new ConfigSectionTrackerClient(),
			new ConfigSectionTrackerServer(),
			new ConfigSectionSecurity(),
			new ConfigSectionSharing(),
			new ConfigSectionLogging()
		};
	}

	public List<BaseConfigSection> getAllConfigSections(boolean sort) {
		List<BaseConfigSection> repoList = ConfigSectionRepository.getInstance().getList();
		if (!sort) {
			repoList.addAll(0, Arrays.asList(internalSections));
			return repoList;
		}

		ArrayList<BaseConfigSection> configSections = new ArrayList<>(
				Arrays.asList(internalSections));
		// Internal Sections are in the order we want them.  
		// place ones from repository at the bottom of correct parent
		for (BaseConfigSection repoConfigSection : repoList) {
			String repoParentID = repoConfigSection.getParentSectionID();

			boolean found = false;
			for (int i = configSections.size() - 1; i >= 0; i--) {
				BaseConfigSection configSection = configSections.get(i);
				if (configSection.getConfigSectionID().equals(repoParentID)) {
					configSections.add(i + 1, repoConfigSection);
					found = true;
					break;
				}
			}
			if (!found) {
				configSections.add(repoConfigSection);
			}
		}

		return configSections;
	}

	public Parameter getParameter(String configKey) {
		List<BaseConfigSection> sections = getAllConfigSections(false);
		for (BaseConfigSection section : sections) {
			boolean needsBuild = !section.isBuilt();
			try {
				if (needsBuild) {
					section.build();
					section.postBuild();
				}

				ParameterImpl pluginParam = section.getPluginParam(configKey);
				if (pluginParam != null) {
					return pluginParam;
				}
			} finally {
				if (needsBuild) {
					section.deleteConfigSection();
				}
			}
		}
		return null;
	}
}
