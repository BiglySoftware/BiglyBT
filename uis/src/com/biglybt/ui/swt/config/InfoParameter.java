/*
 * Created on 9 juil. 2003
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
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;

/**
 * @author parg
 *
 */
public class
InfoParameter
	extends Parameter
{
	private String 				name;
	private BufferedLabel	 	label;

	public InfoParameter(Composite composite,String name) {
		this(composite, name, COConfigurationManager.getStringParameter(name));
	}

	public InfoParameter(Composite composite,final String name, String defaultValue ) {
		super(name);
		this.name = name;
		this.label = new BufferedLabel(composite, SWT.NULL);
		String value = COConfigurationManager.getStringParameter(name, defaultValue);
		label.setText(value);
	}


	public void setValue(final String value) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (label == null || label.isDisposed()
						|| label.getText().equals(value)) {
					return;
				}
				label.setText(value);
			}
		});

		if (!COConfigurationManager.getStringParameter(name).equals(value)) {
			COConfigurationManager.setParameter(name, value);
		}
	}

	@Override
	public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
		label.setLayoutData(layoutData);
	}
	public String getValue() {
		return label.getText();
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.IParameter#getControl()
	 */
	@Override
	public Control getControl() {
		return label.getControl();
	}

	@Override
	public void setValue(Object value) {
		if (value instanceof String) {
			setValue((String)value);
		}
	}

	@Override
	public Object getValueObject() {
		return COConfigurationManager.getStringParameter(name);
	}
}
