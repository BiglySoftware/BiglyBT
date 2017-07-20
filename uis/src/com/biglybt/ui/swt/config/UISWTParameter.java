/*
 * Created on 26 May 2008
 * Created by Allan Crooks
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
 */
package com.biglybt.ui.swt.config;

import org.eclipse.swt.widgets.Control;
import com.biglybt.ui.swt.Utils;

/**
 * For plugins to add a generic SWT widget to the config page
 *
 * @author Allan Crooks
 *
 */
public class UISWTParameter extends Parameter {

	private Control control;

	public UISWTParameter(Control control, String name) {
		super(name);
		this.control = control;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.config.Parameter#setValue(java.lang.Object)
	 */
	@Override
	public void setValue(Object value) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.config.IParameter#getControl()
	 */
	@Override
	public Control getControl() {
		return this.control;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.config.IParameter#setLayoutData(java.lang.Object)
	 */
	@Override
	public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
		this.control.setLayoutData(layoutData);
	}

}
