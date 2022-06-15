/*
 * File    : ConfigPanel*.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.configsections;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ColorParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.ConfigSectionImpl;
import com.biglybt.ui.swt.ConfigKeysSWT;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.UI;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.BaseSwtParameter;
import com.biglybt.ui.swt.config.ColorSwtParameter;
import com.biglybt.ui.swt.config.SwtParameterValueProcessor;
import com.biglybt.ui.swt.utils.ColorCache;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

public class ConfigSectionInterfaceColorSWT
	extends ConfigSectionImpl
	implements BaseConfigSectionSWT
{
	private static final String[] sColorsToOverride = {
		"progressBar",
		"error",
		"warning",
		"altRow"
	};

	public static final String SECTION_ID = "color";

	public ConfigSectionInterfaceColorSWT() {
		super(SECTION_ID, ConfigSection.SECTION_INTERFACE);
	}

	@Override
	public void build() {
		setDefaultUITypesForAdd(UIInstance.UIT_SWT);

		if ( UI.canUseSystemTheme()){
			
			add( new BooleanParameterImpl( "Use System Theme", "ConfigView.section.style.usesystemtheme"));
		}

		ColorParameterImpl colorScheme = new ColorParameterImpl("Color Scheme",
				"ConfigView.section.color");
		add(colorScheme);
		colorScheme.setSuffixLabelKey("restart.required.for.some");
		
		BooleanParameterImpl gradient_sel = new BooleanParameterImpl(
				"Gradient Fill Selection", "ConfigView.section.style.gradient.selection");
		add(gradient_sel);
		
		
		
		List<Parameter> listOverride = new ArrayList<>();

		add(new BooleanParameterImpl(ConfigKeysSWT.BCFG_FORCE_GRAYSCALE,
			"ConfigView.section.style.forceGrayscale"), listOverride);
		
		for (String s : sColorsToOverride) {
			if (Utils.TABLE_GRIDLINE_IS_ALTERNATING_COLOR
					&& s.equals("altRow")) {
				continue;
			}
			String sConfigID = "Colors." + s;
			ColorParameterImpl colorParm = new ColorParameterImpl(sConfigID,
					"ConfigView.section.style.colorOverride." + s);
			add(colorParm, listOverride);
		}

		BooleanParameterImpl dark_tables = new BooleanParameterImpl(
				"Dark Table Colors", "ConfigView.section.style.dark.tables");
		add(dark_tables, listOverride);

		BooleanParameterImpl dark_misc = new BooleanParameterImpl(
				"Dark Misc Colors", "ConfigView.section.style.dark.misc");
		add(dark_misc, listOverride);

		add(new ParameterGroupImpl("ConfigView.section.style.colorOverrides",
				listOverride));
	}

	@Override
	public void configSectionCreate(Composite parent,
			Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam) {
		boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals(
				"az3");
		if (!isAZ3) {
			return;
		}

		BaseSwtParameter pg = mapParamToSwtParam.get(
				getPluginParam("ConfigView.section.style.colorOverrides"));
		if (pg == null) {
			return;
		}

		Composite cColorOverride = (Composite) pg.getMainControl();
		Display display = cColorOverride.getDisplay();

		Label label;
		GridData gridData;

		// These keys are referenced in skin properties (skin3_constants)
		String[][] override_keys_blocks = {
			{ // Doesn't require restart
				"config.skin.color.sidebar.bg"
			},
			{ // Requires Restart
				"config.skin.color.library.header"
			}
		};

		SkinColorValueProcessor skinColorValueProcessor = new SkinColorValueProcessor(
				display);

		for (int i = 0; i < override_keys_blocks.length; i++) {

			if (i == 1) {

				label = new Label(cColorOverride, SWT.NULL);
				gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = 2;
				label.setLayoutData(gridData);

				label = new Label(cColorOverride, SWT.NULL);
				Messages.setLanguageText(label, "restart.required.for.following");
				gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = 2;
				label.setLayoutData(gridData);
			}

			String[] override_keys = override_keys_blocks[i];

			for (final String key : override_keys) {
				new ColorSwtParameter(cColorOverride, key, key, null, true,
						skinColorValueProcessor);
			}
		}
	}

	/**
	 * Skin colors can be formatted as:<br>
	 * colorkey=paramkey:defaultcolor<br>
	 * paramkey is the override stored in ConfigurationManager<br>
	 */
	private static class SkinColorValueProcessor
		implements SwtParameterValueProcessor<ColorSwtParameter, int[]>
	{

		private final Display display;

		public SkinColorValueProcessor(Display display) {
			this.display = display;
		}

		@Override
		public int[] getValue(ColorSwtParameter p) {
			String key = p.getParamID();
			Color existing;

			boolean is_override = COConfigurationManager.getStringParameter(
					key).length() > 0;
			if (!is_override) {
				return getDefaultValue(p);
			}

			// getSchemedColor will return white when it can't find a key starting with "config."
			// but we just checked that above, so we are ok
			existing = ColorCache.getSchemedColor(display, key);

			return existing == null ? null : new int[] {
				existing.getRed(),
				existing.getGreen(),
				existing.getBlue()
			};
		}

		@Override
		public int[] getDefaultValue(ColorSwtParameter p) {
			// we don't have a way to get the default value from the skin, since
			// we don't even know which skin file is being used!
			return null;
		}

		@Override
		public boolean isDefaultValue(ColorSwtParameter p) {
			String key = p.getParamID();
			return COConfigurationManager.getStringParameter(key).isEmpty();
		}

		@Override
		public boolean setValue(ColorSwtParameter p, int[] value) {
			String key = p.getParamID();
			if (COConfigurationManager.setParameter(key,
					value[0] + "," + value[1] + "," + value[2])) {
				p.informChanged();
				return true;
			}
			return false;
		}

		@Override
		public boolean resetToDefault(ColorSwtParameter p) {
			String key = p.getParamID();
			if (COConfigurationManager.removeParameter(key)) {
				p.informChanged();
				return true;
			}
			return false;
		}

	}
}
