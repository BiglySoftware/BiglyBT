/*
 * File    : ChangeSelectionActionPerformer.java
 * Created : 10 oct. 2003 15:38:53
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Event;

/**
 * @author Olivier
 *
 */
public class ExclusiveSelectionActionPerformer implements IAdditionalActionPerformer{

  boolean selected = false;

  Button[] buttons;

  public ExclusiveSelectionActionPerformer(Button[] buttons) {
    this.buttons = buttons;
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.AdditionalActionPerformer#performAction()
   */
  @Override
  public void performAction() {
    if(buttons == null)
      return;
    if(!selected)
      return;
    for(int i = 0 ; i < buttons.length ; i++) {
      buttons[i].setSelection(false);
      buttons[i].notifyListeners(SWT.Selection,new Event());
    }
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.AdditionalActionPerformer#setIntValue(int)
   */
  @Override
  public void setIntValue(int value) {
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.AdditionalActionPerformer#setSelected(boolean)
   */
  @Override
  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.AdditionalActionPerformer#setStringValue(java.lang.String)
   */
  @Override
  public void setStringValue(String value) {
  }

}
