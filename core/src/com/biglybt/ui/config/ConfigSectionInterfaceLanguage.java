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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.ConfigurationParameterNotFoundException;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.StringListParameterImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.StringListParameter;

import static com.biglybt.core.config.ConfigKeys.UI.*;

public class ConfigSectionInterfaceLanguage
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "language";

	public ConfigSectionInterfaceLanguage() {
		super(SECTION_ID, ConfigSection.SECTION_INTERFACE);
	}

	@Override
	public void build() {

		Locale[] locales = MessageText.getLocales(true);

		List<String> listLabels = new ArrayList<>();
		List<String> listValues = new ArrayList<>();

		String curLocale;
		try {
			curLocale = ConfigurationDefaults.getInstance().getStringParameter(
					SCFG_LOCALE);
		} catch (ConfigurationParameterNotFoundException e) {
			e.printStackTrace();
			return;
		}
		boolean foundDefault = false;
		for (Locale value : locales) {
			Locale locale = value;

			locale = MessageText.getDisplaySubstitute(locale);

			String sName = locale.getDisplayName(locale);
			String sName2 = locale.getDisplayName();
			if (!sName.equals(sName2)) {
				sName += " - " + sName2;
			}
			listLabels.add(sName + " - " + locale);
			String val = locale.toString();
			listValues.add(val);
			if (val.equals(curLocale)) {
				foundDefault = true;
			}
		}

		if (!foundDefault) {
			Locale locale1;
			String[] localeStrings = curLocale.split("_", 3);
			if (localeStrings.length > 0 && localeStrings[0].length() == 2) {
				if (localeStrings.length == 3) {
					locale1 = new Locale(localeStrings[0], localeStrings[1],
							localeStrings[2]);
				} else if (localeStrings.length == 2
						&& localeStrings[1].length() == 2) {
					locale1 = new Locale(localeStrings[0], localeStrings[1]);
				} else {
					locale1 = new Locale(localeStrings[0]);
				}
			} else {
				if (localeStrings.length == 3 && localeStrings[0].length() == 0
						&& localeStrings[2].length() > 0) {
					locale1 = new Locale(localeStrings[0], localeStrings[1],
							localeStrings[2]);
				} else {
					locale1 = Locale.getDefault();
				}
			}
			Locale locale = locale1;
			String sName = locale.getDisplayName(locale);
			String sName2 = locale.getDisplayName();
			if (!sName.equals(sName2)) {
				sName += " - " + sName2;
			}
			listLabels.add(0, "System Default: " + sName + " - " + locale);
			listValues.add(0, curLocale);
		}

		StringListParameterImpl locale_param = new StringListParameterImpl(
				SCFG_LOCALE, "MainWindow.menu.language",
				listValues.toArray(new String[0]), listLabels.toArray(new String[0]));
		add(locale_param);
		locale_param.setListType(StringListParameter.TYPE_LISTBOX);

		locale_param.addListener(p -> {
			MessageText.loadBundle();
			DisplayFormatters.setUnits();
			DisplayFormatters.loadMessages();
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			if (uiFunctions != null) {
				uiFunctions.refreshLanguage();
			}
		});

		BooleanParameterImpl uc = new BooleanParameterImpl(BCFG_LANG_UPPER_CASE,
				"label.lang.upper.case");
		add(uc);

		uc.addListener(p -> {
			MessageText.loadBundle(true);
			DisplayFormatters.setUnits();
			DisplayFormatters.loadMessages();
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			if (uiFunctions != null) {
				uiFunctions.refreshLanguage();
			}
		});
	}

}
