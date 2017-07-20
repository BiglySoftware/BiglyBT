/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.config;

/**
 * @author TuxPaper
 * @created Nov 13, 2006
 *
 */
public class ParameterChangeAdapter implements ParameterChangeListener
{
	@Override
	public void booleanParameterChanging(Parameter p, boolean toValue) {
	}

	@Override
	public void intParameterChanging(Parameter p, int toValue) {
	}

	@Override
	public void parameterChanged(Parameter p, boolean caused_internally) {
	}

	@Override
	public void stringParameterChanging(Parameter p, String toValue) {
	}

	@Override
	public void floatParameterChanging(Parameter owner, double toValue) {
	}
}
