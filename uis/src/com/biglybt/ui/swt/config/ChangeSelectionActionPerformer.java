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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;

/**
 * @author Olivier
 *
 */
public class ChangeSelectionActionPerformer implements IAdditionalActionPerformer{

  boolean selected = false;
  boolean reverse_sense = false;

  Control[] controls;

  public ChangeSelectionActionPerformer(Control[] controls) {
	this.controls = controls;
  }

  public ChangeSelectionActionPerformer(Control control) {
		this.controls = new Control[]{ control };
  }

  public ChangeSelectionActionPerformer(Parameter p) {
		this.controls = p.getControls();
  }
  public ChangeSelectionActionPerformer(Parameter p1, Parameter p2) {
	  this( new Parameter[]{ p1, p2 });
  }
  public ChangeSelectionActionPerformer(Parameter[] params ) {

	  List	c = new ArrayList();

	  for (int i=0;i<params.length;i++){
		  Control[] x = params[i].getControls();

		  Collections.addAll(c, x);
	  }

	  controls = new Control[c.size()];

	  c.toArray( controls );
  }

  public ChangeSelectionActionPerformer(Control[] controls, boolean _reverse_sense) {
  	this.controls = controls;
	reverse_sense = _reverse_sense;
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.AdditionalActionPerformer#performAction()
   */
  @Override
  public void performAction() {
    if(controls == null)
      return;
    controlsSetEnabled(controls, reverse_sense?!selected:selected);
  }

  private void controlsSetEnabled(Control[] controls, boolean bEnabled) {
    for(int i = 0 ; i < controls.length ; i++) {
      if (controls[i] instanceof Composite)
        controlsSetEnabled(((Composite)controls[i]).getChildren(), bEnabled);
      controls[i].setEnabled(bEnabled);
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
