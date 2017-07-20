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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.ColorParameter;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.ui.swt.utils.ColorCache;

public class ConfigSectionInterfaceColor implements UISWTConfigSection {
	private static final String[] sColorsToOverride = { "progressBar", "error",
			"warning", "altRow" };

	private Color[] colorsToOverride = { Colors.colorProgressBar,
			Colors.colorError, Colors.colorWarning, Colors.colorAltRow };

	private Button[] btnColorReset = new Button[sColorsToOverride.length];

	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_INTERFACE;
	}

	@Override
	public String configSectionGetName() {
		return "color";
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {
	}

	@Override
	public int maxUserMode() {
		return 0;
	}


	@Override
	public Composite configSectionCreate(final Composite parent) {
		boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals("az3");

		Label label;
		GridLayout layout;
		GridData gridData;
		Composite cSection = new Composite(parent, SWT.NULL);
		cSection.setLayoutData(new GridData(GridData.FILL_BOTH));
		layout = new GridLayout();
		layout.numColumns = 1;
		cSection.setLayout(layout);

		Composite cArea = new Composite(cSection, SWT.NULL);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 3;
		cArea.setLayout(layout);
		cArea.setLayoutData(new GridData());

		label = new Label(cArea, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.section.color");
		ColorParameter colorScheme = new ColorParameter(cArea, "Color Scheme", 0,
				128, 255);
		gridData = new GridData();
		gridData.widthHint = 50;
		colorScheme.setLayoutData(gridData);

		label = new Label(cArea, SWT.NULL);

		if ( isAZ3 ){
			Messages.setLanguageText(label, "restart.required.for.some");
		}

		Group cColorOverride = new Group(cArea, SWT.NULL);
		Messages.setLanguageText(cColorOverride,
				"ConfigView.section.style.colorOverrides");
		layout = new GridLayout();
		layout.numColumns = 3;
		cColorOverride.setLayout(layout);
		gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gridData.horizontalSpan = 3;
		cColorOverride.setLayoutData(gridData);

		for (int i = 0; i < sColorsToOverride.length; i++) {
			if (Utils.TABLE_GRIDLINE_IS_ALTERNATING_COLOR && sColorsToOverride[i].equals("altRow")) {
				continue;
			}
			String sConfigID = "Colors." + sColorsToOverride[i];
			label = new Label(cColorOverride, SWT.NULL);
			Messages.setLanguageText(label, "ConfigView.section.style.colorOverride."
					+ sColorsToOverride[i]);
			ColorParameter colorParm = new ColorParameter(cColorOverride, sConfigID,
					colorsToOverride[i].getRed(), colorsToOverride[i].getGreen(),
					colorsToOverride[i].getBlue()) {
				@Override
				public void newColorChosen(RGB newColor) {
					COConfigurationManager.setParameter(sParamName + ".override", true);
					for (int i = 0; i < sColorsToOverride.length; i++) {
						if (sParamName.equals("Colors." + sColorsToOverride[i])) {
							btnColorReset[i].setEnabled(true);
							break;
						}
					}
				}
			};
			gridData = new GridData();
			gridData.widthHint = 50;
			colorParm.setLayoutData(gridData);
			btnColorReset[i] = new Button(cColorOverride, SWT.PUSH);
			Messages.setLanguageText(btnColorReset[i],
					"ConfigView.section.style.colorOverrides.reset");
			btnColorReset[i].setEnabled(COConfigurationManager.getBooleanParameter(
					sConfigID + ".override", false));
			btnColorReset[i].setData("ColorName", sConfigID);
			btnColorReset[i].addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					Button btn = (Button) event.widget;
					String sName = (String) btn.getData("ColorName");
					if (sName != null) {
						COConfigurationManager.setParameter(sName + ".override", false);
						btn.setEnabled(false);
					}
				}
			});
		}

		if ( isAZ3 ){

			String[][]	override_keys_blocks = {
				{ "config.skin.color.sidebar.bg" },
				{ "config.skin.color.library.header" }
			};

			for ( int i=0;i<override_keys_blocks.length;i++ ){

				if ( i == 1 ){

					label = new Label(cColorOverride, SWT.NULL);
					gridData = new GridData( GridData.FILL_HORIZONTAL );
					gridData.horizontalSpan = 3;
					label.setLayoutData(gridData);

					label = new Label(cColorOverride, SWT.NULL);
					Messages.setLanguageText(label, "restart.required.for.following" );
					gridData = new GridData( GridData.FILL_HORIZONTAL );
					gridData.horizontalSpan = 3;
					label.setLayoutData(gridData);
				}

				String[] override_keys = override_keys_blocks[i];

				for ( final String key: override_keys ){

					label = new Label(cColorOverride, SWT.NULL);
					Messages.setLanguageText(label, key );
					gridData = new GridData( GridData.FILL_HORIZONTAL );
					label.setLayoutData(gridData);

					Color existing = null;

					boolean is_override = COConfigurationManager.getStringParameter( key, "" ).length() > 0;

					if ( is_override ){

						existing = ColorCache.getSchemedColor( parent.getDisplay(), key );
					}

					final Button[]	f_reset = { null };

					final ColorParameter colorParm = new ColorParameter(cColorOverride, null,
							existing==null?-1:existing.getRed(),
							existing==null?-1:existing.getGreen(),
							existing==null?-1:existing.getBlue()) {
						@Override
						public void newColorChosen(RGB newColor) {
							COConfigurationManager.setParameter( key, newColor.red+","+newColor.green+","+newColor.blue );
							f_reset[0].setEnabled( true );
						}
					};

					gridData = new GridData();
					gridData.widthHint = 50;
					colorParm.setLayoutData(gridData);

					final Button reset = f_reset[0] = new Button(cColorOverride, SWT.PUSH);
					Messages.setLanguageText(reset, "ConfigView.section.style.colorOverrides.reset");
					reset.setEnabled( is_override );
					reset.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
							reset.setEnabled( false );
							colorParm.setColor( -1, -1, -1 );
							COConfigurationManager.removeParameter( key );
						}
					});
				}
			}
		}

		return cSection;
	}
}
