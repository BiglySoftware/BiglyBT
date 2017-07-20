/*
 * Created on 22-May-2004
 * Created by Paul Gardner
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

/**
 * @author parg
 *
 */

public interface
ParameterChangeListener
{
	public void
	parameterChanged(
		Parameter	p,
		boolean		caused_internally );

	/**
	 * An int parameter is about to change.
	 * <p>
	 * Not called when parameter set via COConfigurationManager.setParameter
	 *
	 * @param p
	 * @param toValue
	 */
	public void
	intParameterChanging(Parameter p, int toValue);

	/**
	 * A boolean parameter is about to change.
	 * <p>
	 * Not called when parameter set via COConfigurationManager.setParameter
	 *
	 * @param p
	 * @param toValue
	 */
	public void
	booleanParameterChanging(Parameter p, boolean toValue);

	/**
	 * A String parameter is about to change.
	 * <p>
	 * Not called when parameter set via COConfigurationManager.setParameter
	 *
	 * @param p
	 * @param toValue
	 */
	public void
	stringParameterChanging(Parameter p, String toValue);

	/**
	 * A double/float parameter is about to change.
	 * <p>
	 * Not called when parameter set via COConfigurationManager.setParameter
	 *
	 * @param p
	 * @param toValue
	 */
	public void
	floatParameterChanging(Parameter owner, double toValue);
}
