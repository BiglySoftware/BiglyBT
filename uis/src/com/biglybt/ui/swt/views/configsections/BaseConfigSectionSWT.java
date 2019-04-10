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

import java.util.Map;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.swt.config.BaseSwtParameter;

/**
 * Add this interface to a class extending {@link BaseConfigSectionSWT} if you
 * need to manipulate the SWT components whent the config section is built
 */
public interface BaseConfigSectionSWT
{
	/**
	 * Adjust the configuration panel here.
	 * Please be mindful of small screen resolutions.
	 *
	 * @param parent The parent of your configuration panel
	 * @param mapParamToSwtParam 
	 *    Link non-Swt Parameter to SwtParameter.
	 *    Use {@link com.biglybt.ui.config.BaseConfigSection#getPluginParam(String)}
	 *    to find the SwtParameter with key used when adding the non-Swt parameter.
	 *    Key is usually they config key, or a manual key passed in on add()
	 */
	void configSectionCreate(Composite parent,
			Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam);
}
