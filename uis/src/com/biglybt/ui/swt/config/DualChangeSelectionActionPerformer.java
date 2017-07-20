/*
 * File    : DualChangeSelectionActionPerformer.java
 * Created : 16 d�c. 2003}
 * By      : Olivier
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
package com.biglybt.ui.swt.config;

import org.eclipse.swt.widgets.Control;

/**
 * @author Olivier
 *
 */
public class DualChangeSelectionActionPerformer implements IAdditionalActionPerformer {

  ChangeSelectionActionPerformer enabler;
  ChangeSelectionActionPerformer disabler;

  public DualChangeSelectionActionPerformer(
  	Control[] controlsToEnable,
		Control[] controlsToDisable) {
    enabler = new ChangeSelectionActionPerformer(controlsToEnable,false);
    disabler = new ChangeSelectionActionPerformer(controlsToDisable,true);
  }

  @Override
  public void setIntValue(int value) {}

  @Override
  public void setStringValue(String value) {}

  @Override
  public void setSelected(boolean selected) {
    enabler.setSelected(selected);
    disabler.setSelected(selected);
  }

  @Override
  public void performAction() {
    enabler.performAction();
    disabler.performAction();
  }

}
