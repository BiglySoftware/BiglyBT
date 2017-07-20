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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.UISwitcherUtil;
import com.biglybt.ui.swt.config.BooleanParameter;
import com.biglybt.ui.swt.config.ChangeSelectionActionPerformer;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

public class ConfigSectionInterfaceStart implements UISWTConfigSection {
  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_INTERFACE;
  }

	@Override
	public String configSectionGetName() {
		return "start";
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
    // "Start" Sub-Section
    // -------------------
    GridLayout layout;
    Composite cStart = new Composite(parent, SWT.NULL);

    cStart.setLayoutData(new GridData(GridData.FILL_BOTH));
    layout = new GridLayout();
    layout.numColumns = 1;
    cStart.setLayout(layout);

    int userMode = COConfigurationManager.getIntParameter("User Mode");
    boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals("az3");

    if (userMode >= 2) {
    	new BooleanParameter(cStart, "ui.startfirst", "ConfigView.label.StartUIBeforeCore");
    }
    new BooleanParameter(cStart, "Show Splash", "ConfigView.label.showsplash");
    new BooleanParameter(cStart, "update.start", "ConfigView.label.checkonstart");
    new BooleanParameter(cStart, "update.periodic", "ConfigView.label.periodiccheck");
    BooleanParameter autoDownload = new BooleanParameter(cStart, "update.autodownload", "ConfigView.section.update.autodownload");
    BooleanParameter openDialog = new BooleanParameter(cStart, "update.opendialog", "ConfigView.label.opendialog");

    autoDownload.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(
    		new Control[] { openDialog.getControl() }, true ));

    new BooleanParameter(cStart, "update.anonymous", "ConfigView.label.update.anonymous");

    new Label(cStart,SWT.NULL);
    new BooleanParameter(cStart, "Open Transfer Bar On Start", "ConfigView.label.open_transfer_bar_on_start");
    new BooleanParameter(cStart, "Start Minimized", "ConfigView.label.startminimized");

	// UI switcher window.
    Composite cUISwitcher = new Composite(cStart, SWT.NONE);
    layout = new GridLayout(2, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
		cUISwitcher.setLayout(layout);

		final Label ui_switcher_label = new Label(cUISwitcher, SWT.NULL);
		Messages.setLanguageText(ui_switcher_label, "ConfigView.label.ui_switcher");

		final Button ui_switcher_button = new Button(cUISwitcher, SWT.PUSH);
		Messages.setLanguageText(ui_switcher_button,
				"ConfigView.label.ui_switcher_button");

		ui_switcher_button.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				UISwitcherUtil.openSwitcherWindow();
			}
		});

    return cStart;
  }
}
