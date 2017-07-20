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
public interface EnablerParameter extends Parameter {

  /**
   * enables parameter when EnablerParameter is selected.<br>
   * parameter is disabled is EnablerParameter isn't selected.
   * @param parameter the Parameter to act on
   */
  public void addEnabledOnSelection(Parameter parameter);

  /**
   * disables parameter when EnablerParameter is selected.<br>
   * parameter is enabled is EnablerParameter isn't selected.
   * @param parameter the Parameter to act on
   */
  public void addDisabledOnSelection(Parameter parameter);

}
