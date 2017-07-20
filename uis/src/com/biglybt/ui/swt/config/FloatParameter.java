/*
 * Created on 10 juil. 2003
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.config.*;
import com.biglybt.ui.swt.Utils;

public class FloatParameter {

  Text inputField;
  float fMinValue = 0;
  float fMaxValue = -1;
  float fDefaultValue;
  int iDigitsAfterDecimal = 1;
  String sParamName;
  boolean allowZero = false;

  public FloatParameter(Composite composite, final String name) {
    fDefaultValue = COConfigurationManager.getFloatParameter(name);
    initialize(composite,name);
  }

  public FloatParameter(Composite composite, final String name,
                        float minValue, float maxValue, boolean allowZero,
                        int digitsAfterDecimal) {
    fDefaultValue = COConfigurationManager.getFloatParameter(name);
    initialize(composite,name);
    fMinValue = minValue;
    fMaxValue = maxValue;
    this.allowZero = allowZero;
    iDigitsAfterDecimal = digitsAfterDecimal;
  }


  public void initialize(Composite composite, final String name) {
    sParamName = name;

    inputField = new Text(composite, SWT.BORDER | SWT.RIGHT);
    float value = COConfigurationManager.getFloatParameter(name);
    inputField.setText(String.valueOf(value));
    inputField.addListener(SWT.Verify, new Listener() {
      @Override
      public void handleEvent(Event e) {
        String text = e.text;
        char[] chars = new char[text.length()];
        text.getChars(0, chars.length, chars, 0);
        for (int i = 0; i < chars.length; i++) {
          if ( !((chars[i] >= '0' && chars[i] <= '9') || chars[i] == '.') ) {
            e.doit = false;
            return;
          }
        }
      }
    });

    inputField.addListener(SWT.Modify, new Listener() {
      @Override
      public void handleEvent(Event event) {
        try {
          float val = Float.parseFloat(inputField.getText());
          if (val < fMinValue) {
            if (!(allowZero && val == 0)) {
            	val = fMinValue;
            }
          }
          if (val > fMaxValue) {
            if (fMaxValue > -1) {
              val = fMaxValue;
            }
          }
          COConfigurationManager.setParameter(name, val);
        }
        catch (Exception e) {}
      }
    });

    inputField.addListener(SWT.FocusOut, new Listener() {
      @Override
      public void handleEvent(Event event) {
        try {
          float val = Float.parseFloat(inputField.getText());
          if (val < fMinValue) {
            if (!(allowZero && val == 0)) {
              inputField.setText(String.valueOf(fMinValue));
              COConfigurationManager.setParameter(name, fMinValue);
            }
          }
          if (val > fMaxValue) {
            if (fMaxValue > -1) {
            	inputField.setText(String.valueOf(fMaxValue));
            	COConfigurationManager.setParameter(name, fMaxValue);
            }
          }
        }
        catch (Exception e) {}
      }
    });
  }


  public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
    inputField.setLayoutData(layoutData);
  }

  public Control
  getControl()
  {
  	return( inputField );
  }
}
