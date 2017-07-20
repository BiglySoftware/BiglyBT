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
package com.biglybt.ui.swt.config.generic;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;

public class GenericFloatParameter {

  Text inputField;
  float fMinValue = 0;
  float fMaxValue = -1;
  boolean allowZero = false;
	private GenericParameterAdapter adapter;
	private String name;

  public GenericFloatParameter(GenericParameterAdapter adapter,Composite composite, final String name) {
    adapter.getFloatValue( name );
    initialize(adapter,composite,name);
  }

  public GenericFloatParameter(GenericParameterAdapter adapter,Composite composite, final String name,
                        float minValue, float maxValue, boolean allowZero,
                        int digitsAfterDecimal) {
    adapter.getFloatValue( name );
    initialize(adapter,composite,name);
    fMinValue = minValue;
    fMaxValue = maxValue;
    this.allowZero = allowZero;
  }


  public void initialize(final GenericParameterAdapter adapter,Composite composite, final String name) {

    this.adapter = adapter;
		this.name = name;
		inputField = new Text(composite, SWT.BORDER | SWT.RIGHT);
    float value = adapter.getFloatValue( name );
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
          // don't inform of intermediate values as stupid to do so
          // adapter.setFloatValue(name, val);
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
              val	= fMinValue;
            }
          }
          if (val > fMaxValue) {
            if (fMaxValue > -1) {
            	inputField.setText(String.valueOf(fMaxValue));
            	val = fMaxValue;
            }
          }
          adapter.setFloatValue(name, val);
        }
        catch (Exception e) {}
      }
    });
  }

  public void
  setValue(
	final float	value )
  {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if ( !inputField.isDisposed()){
					 inputField.setText(String.valueOf(value));
				}
			}
		});
  }

  public float
  getValue()
  {
	  return( adapter.getFloatValue( name ));
  }

  public void
  refresh()
  {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if ( !inputField.isDisposed()){
					 inputField.setText(String.valueOf(adapter.getFloatValue(name)));
				}
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
