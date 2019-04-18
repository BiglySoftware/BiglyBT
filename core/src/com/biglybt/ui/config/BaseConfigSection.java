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

import java.util.List;
import java.util.regex.Pattern;

import com.biglybt.pifimpl.local.ui.config.ParameterImpl;

import com.biglybt.pif.ui.config.Parameter;

public interface BaseConfigSection
{
	Parameter[] getParamArray();

	ParameterImpl getPluginParam(String key);

	/**
	 * Returns section you want your configuration panel to be under.
	 * See BasicPluginConfigModel.SECTION_* constants.  To add a subsection to your own ConfigSection,
	 * return the getConfigSectionID result of your parent.<br>
	 */
	String getParentSectionID();

	/**
	 * In order for the plugin to display its section correctly, a key in the
	 * Plugin language file will need to contain
	 * <TT>ConfigView.section.<i>&lt;getConfigSectionID() result&gt;</i>=The Section name.</TT><br>
	 *
	 * @return The name of the configuration section
	 */
	String getConfigSectionID();

	/**
	 * User selected Save.
	 * All saving of non-plugin tabs have been completed, as well as
	 * saving of plugins that implement com.biglybt.pif.ui.config
	 * parameters.
	 */
	void saveConfigSection();

	/**
	 * Config view is closing
	 */
	void deleteConfigSection();

	/**
	 * Returns the minimum user mode needed for this section to be displayed.
	 * <p/>
	 * Note: Section may be visually displayed, but with a notification that
	 * modification is unavailable at current user mode.
	 *
	 * @see com.biglybt.pif.ui.config.Parameter#MODE_BEGINNER
	 * @see com.biglybt.pif.ui.config.Parameter#MODE_INTERMEDIATE
	 * @see com.biglybt.pif.ui.config.Parameter#MODE_ADVANCED
	 */
	int getMinUserMode();

	/**
	 * Indicate if additional options are available to display a hint to the users
	 *
	 * @return the highest user mode that reveals additional options (0 = Beginner, 1 = Intermediate, 2 = Advanced)
	 */
	int getMaxUserMode();

	void setRebuildRunner(ConfigSectionRebuildRunner rebuildRunner);

	/**
	 * Request the UI to rebuild this config section.  For SWT, all SWT objects
	 * will be disposed and recreated.
	 */
	void requestRebuild();

	void build();

	void postBuild();

	boolean isBuilt();

	String getSectionNameKey();

	List<Parameter> search(Pattern regex);
}
