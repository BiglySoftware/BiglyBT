/*
 * File    : EnablerParameter.java
 * Created : 30 nov. 2003
 * By      : Olivier
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pif.ui.config;

/**
 * represents a parameter that is able to enable/disable other parameters.<br>
 * @author Olivier
 *
 */
public interface EnablerParameter
	extends Parameter
{

	/**
	 * disables parameter when EnablerParameter is selected.
	 *
	 * @param parametersToDisable the Parameter to act on
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void addDisabledOnSelection(Parameter... parametersToDisable);

	/**
	 * disables parameter when EnablerParameter is selected.
	 *
	 * @param parameterToDisable the Parameter to act on
	 *
	 * @since BiglyBT 1.0.0.0
	 */
	void addDisabledOnSelection(Parameter parameterToDisable);

	/**
	 * enables paramToEnable when this EnablerParameter is selected (checked).<br>
	 * paramToEnable is disabled when this EnablerParameter isn't selected (checked).
	 * <p/>
	 * Note: When this EnableParameter is disabled, paramToEnable's state will not be modified.
	 * In cases where parameter1 is enabled by its parent, and parameter1 enabled children,
	 * you must also parent.addEnabledOnSelection(children) if you want the children
	 * to be disabled when parent is unselected (unchecked).
	 *
	 * @param paramToEnable the Parameter to act on
	 *
	 * @since BiglyBT 1.0.0.0
	 */
	void addEnabledOnSelection(Parameter paramToEnable);


	/**
	 * enables paramToEnable when this EnablerParameter is selected (checked).<br>
	 * paramToEnable is disabled when this EnablerParameter isn't selected (checked).
	 * <p/>
	 * Note: When this EnableParameter is disabled, parametersToDisable's state will not be modified.
	 * In cases where parameter1 is enabled by its parent, and parameter1 enabled children,
	 * you must also parent.addEnabledOnSelection(children) if you want the children
	 * to be disabled when parent is unselected (unchecked).
	 *
	 * @param parametersToEnable the Parameter to act on
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void addEnabledOnSelection(Parameter... parametersToEnable);
}
