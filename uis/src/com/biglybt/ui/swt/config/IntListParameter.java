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

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;

/**
 * @author Olivier
 *
 */
public class IntListParameter extends Parameter {

  Combo list;
	private final int[] values;

	public IntListParameter(Composite composite, String configID, String[] labels,
			int[] values) {
		super(configID);
		this.values = values;
		list = new Combo(composite, SWT.SINGLE | SWT.READ_ONLY);

		if (labels.length != values.length) {
			return;
		}
		int value = COConfigurationManager.getIntParameter(configID);
		int index = findIndex(value, values);
		for (String label : labels) {
			if (Utils.isGTK) {
				list.add(label + "    "); // Pad to force avoid truncation of selected label in control
			} else {
				list.add(label);
			}
		}

		setIndex(index);

		list.addListener(SWT.Selection, e -> setIndex(list.getSelectionIndex()));

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

  	if (COConfigurationManager.getIntParameter(configID) != selected_value) {
  		COConfigurationManager.setParameter(configID, selected_value);
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
  	return COConfigurationManager.getIntParameter(configID);
  }
}
