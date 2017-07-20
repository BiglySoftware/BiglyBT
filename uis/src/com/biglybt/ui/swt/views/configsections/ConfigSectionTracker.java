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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

public class
ConfigSectionTracker
	implements UISWTConfigSection
{

	public ConfigSectionTracker() {
	}

  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_ROOT;
  }

	@Override
	public String configSectionGetName() {
		return "tracker";
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
    GridData gridData;
    GridLayout layout;

    // main tab set up
    Composite gMainTab = new Composite(parent, SWT.NULL);

    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gMainTab.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 3;
    gMainTab.setLayout(layout);

      // MAIN TAB DATA

    return gMainTab;
  }
}
