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

import com.biglybt.pifimpl.local.ui.config.ParameterImpl;

/**
 * For plugins to add a generic SWT widget to the config page
 *
 * @author Allan Crooks
 *
 */
public class UISWTParameter extends BaseSwtParameter {

	public UISWTParameter(Control control, String configID) {
		super(configID);
		setMainControl(control);
	}

	public UISWTParameter(Control control, ParameterImpl pg) {
		super(null);
		setMainControl(control);
		setPluginParameter(pg);
	}

	@Override
	public Object getValue() {
		return null;
	}
}
