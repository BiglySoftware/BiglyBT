/*
 * File    : GenericAdditionalActionPerformer.java
 * Created : 27-Nov-2003
 * By      : parg
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

/**
 * @author parg
 *
 */

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public abstract class
GenericActionPerformer
implements IAdditionalActionPerformer
{
  protected Control[] controls;

  public GenericActionPerformer(Control[] controls) {
	this.controls = controls;
  }

   @Override
   public void setIntValue(int value) {
  }

   @Override
   public void setSelected(boolean selected) {
  }

   @Override
   public void setStringValue(String value) {
  }

  public void controlsSetEnabled(Control[] controls, boolean bEnabled) {
    for(int i = 0 ; i < controls.length ; i++) {
      if (controls[i] instanceof Composite)
        controlsSetEnabled(((Composite)controls[i]).getChildren(), bEnabled);
      controls[i].setEnabled(bEnabled);
    }
  }
}