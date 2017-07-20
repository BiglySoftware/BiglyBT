/*
 * File    : PluginStringParameter.java
 * Created : 15 d�c. 2003}
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
package com.biglybt.ui.swt.config.plugins;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import com.biglybt.pifimpl.local.ui.config.ColorParameter;
import com.biglybt.ui.swt.Messages;

/**
 * @author Olivier
 *
 */
public class PluginColorParameter implements PluginParameterImpl {

  Control[] controls;

  public PluginColorParameter(Composite pluginGroup,ColorParameter parameter) {
    controls = new Control[2];

    controls[0] = new Label(pluginGroup,SWT.NULL);
    Messages.setLanguageText(controls[0],parameter.getLabelKey());

    com.biglybt.ui.swt.config.ColorParameter cp =
    	new com.biglybt.ui.swt.config.ColorParameter(
    	    pluginGroup,
    	    parameter.getKey(),
					parameter.getDefaultRed(),
					parameter.getDefaultGreen(),
					parameter.getDefaultBlue());
    controls[1] = cp.getControl();
    new Label(pluginGroup,SWT.NULL);
  }

  @Override
  public Control[] getControls(){
    return controls;
  }

}
