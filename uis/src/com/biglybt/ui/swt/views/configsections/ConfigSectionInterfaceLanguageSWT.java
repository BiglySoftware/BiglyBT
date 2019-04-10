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

import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.config.ConfigKeys.*;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.ConfigSectionInterfaceLanguage;
import com.biglybt.ui.swt.config.BaseSwtParameter;

public class ConfigSectionInterfaceLanguageSWT
	extends ConfigSectionInterfaceLanguage
	implements BaseConfigSectionSWT
{
	@Override
	public void configSectionCreate(Composite parent,
			Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam) {
		// Hack to get Language list to fill visible area and overflow vertically
		ParameterImpl paramLocale = getPluginParam(UI.SCFG_LOCALE);
		BaseSwtParameter swtParamLocale = mapParamToSwtParam.get(paramLocale);
		if (swtParamLocale == null) {
			return;
		}
		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.minimumHeight = 50;
		swtParamLocale.getMainControl().setLayoutData(gridData);
	}
}
