/*
 * Created on 11-Nov-2006
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
package com.biglybt.ui.swt.pif;

import com.biglybt.pif.ui.UIInputReceiver;

/**
 * SWT-specific version of {@link UIInputReciever}, providing some additional
 * methods to customise the appearance of the input receiver.
 */
public interface UISWTInputReceiver extends UIInputReceiver {

	/**
	 * Indicates how wide you want the text entry window to be.
	 * @param width
	 */
	public void setWidthHint(int width);
	public void setHeightHint(int height);

	/**
	 * Indicates how many lines by default to show the user to enter input.
	 *
	 * @param line_count
	 */
	public void setLineHeight(int line_count);

	/**
	 * Indicates whether you want the preentered text to be selected
	 * (highlighted) or not. Default is <code>true</code>.
	 *
	 * @param select
	 */
	public void selectPreenteredText(boolean select);

	/**
	 * Changes the entry box to be a combo box, where the values passed
	 * are selectable.
	 *
	 * @param choices The list of options to be made available.
	 * @param default_choice Index of the option to present by default.
	 * @param allow_edit <tt>true</tt> if you still want to allow the user to enter
	 *     their own text, <tt>false</tt> if you want to restrict them to the choices
	 *     here.
	 * @since 3.0.5.3
	 */
	public void setSelectableItems(String[] choices, int default_choice, boolean allow_edit);

}
