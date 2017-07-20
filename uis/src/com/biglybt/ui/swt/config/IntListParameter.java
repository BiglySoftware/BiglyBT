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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import com.biglybt.core.config.*;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;

/**
 * @author Olivier
 *
 */
public class IntListParameter extends Parameter {

  Combo list;
	private final int[] values;
	private final String name;

  public IntListParameter(
                          Composite composite,
                          final String name,
                          final String labels[],
                          final int values[]) {
    this(composite, name, COConfigurationManager.getIntParameter(name), labels, values);
  }

  public IntListParameter(Composite composite, final String name,
			int defaultValue, final String labels[], final int values[]) {
		super(name);
		this.name = name;
		this.values = values;

      if(labels.length != values.length)
        return;
      int value = COConfigurationManager.getIntParameter(name,defaultValue);
      int index = findIndex(value,values);
      list = new Combo(composite,SWT.SINGLE | SWT.READ_ONLY);
      for(int i = 0 ; i < labels.length  ;i++) {
        list.add(labels[i]);
      }

      setIndex(index);

      list.addListener(SWT.Selection, new Listener() {
           @Override
           public void handleEvent(Event e) {
          	 setIndex(list.getSelectionIndex());
           }
         });

    }

  /**
	 * @param index
	 */
	protected void setIndex(final int index) {
  	int	selected_value = values[index];

  	Utils.execSWTThread(new AERunnable() {
  		@Override
		  public void runSupport() {
  			if (list == null || list.isDisposed()) {
  				return;
  			}

  	  	if (list.getSelectionIndex() != index) {
  	  		list.select(index);
  	  	}
  		}
  	});

  	if (COConfigurationManager.getIntParameter(name) != selected_value) {
  		COConfigurationManager.setParameter(name, selected_value);
  	}
	}

	private int findIndex(int value,int values[]) {
    for(int i = 0 ; i < values.length ;i++) {
      if(values[i] == value)
        return i;
    }
    return 0;
  }


  @Override
  public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
    list.setLayoutData(layoutData);
   }

  @Override
  public Control getControl() {
    return list;
  }

  @Override
  public void setValue(Object value) {
  	if (value instanceof Number) {
  		int i = ((Number)value).intValue();
      setIndex(findIndex(i, values));
  	}
  }

  @Override
  public Object getValueObject() {
  	return new Integer(COConfigurationManager.getIntParameter(name));
  }
}
