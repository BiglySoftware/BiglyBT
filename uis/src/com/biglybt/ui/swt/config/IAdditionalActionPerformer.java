/*
 * File    : AdditionalActionPerformer.java
 * Created : 10 oct. 2003 15:36:00
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

package com.biglybt.ui.swt.config;

/**
 * @author Olivier
 *
 */
public interface IAdditionalActionPerformer {

  //The action to be performed
  public void performAction();

  //Used by the calling class to set some values.
  public void setSelected(boolean selected);
  public void setIntValue(int value);
  public void setStringValue(String value);
}
