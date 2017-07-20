/*
 * Created on 2004/02/15
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package com.biglybt.ui.swt.config;

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import com.biglybt.core.config.*;
import com.biglybt.ui.swt.Utils;

/**
 * @author TuxPaper
 *
 */
public class RadioParameter extends Parameter{

  Button radioButton;

  List  performers  = new ArrayList();

  public RadioParameter(Composite composite, String sConfigName, int iButtonValue) {
    this(composite, sConfigName, iButtonValue, null);
  }

  public RadioParameter(Composite composite, final String sConfigName, final int iButtonValue,
                        IAdditionalActionPerformer actionPerformer) {
  	super(sConfigName);
    if ( actionPerformer != null ){
      performers.add( actionPerformer );
    }
    int iDefaultValue = COConfigurationManager.getIntParameter(sConfigName);

    radioButton = new Button(composite, SWT.RADIO);
    radioButton.setSelection(iDefaultValue == iButtonValue);
    radioButton.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event event) {
        boolean selected = radioButton.getSelection();
        if (selected)
          COConfigurationManager.setParameter(sConfigName, iButtonValue);

        if (performers.size() > 0 ) {
          for (int i = 0;i < performers.size(); i++) {
            IAdditionalActionPerformer  performer = (IAdditionalActionPerformer)performers.get(i);

            performer.setSelected(selected);
            performer.performAction();
          }
        }
      }
    });
  }

  @Override
  public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
    radioButton.setLayoutData(layoutData);
  }

  public void setAdditionalActionPerformer(IAdditionalActionPerformer actionPerformer) {
    performers.add(actionPerformer);
    boolean selected  = radioButton.getSelection();
    actionPerformer.setSelected(selected);
    actionPerformer.performAction();
  }
  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.IParameter#getControl()
   */
  @Override
  public Control getControl() {
    return radioButton;
  }

  public boolean
  isSelected()
  {
    return( radioButton.getSelection());
  }

  @Override
  public void setValue(Object value) {
  	System.err.println("NOT IMPLEMENTED");
  }
}
