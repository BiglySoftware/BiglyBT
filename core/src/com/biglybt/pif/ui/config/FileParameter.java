/*
 * Created on 20-Nov-2006
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
 *
 */

package com.biglybt.pif.ui.config;

/**
 * @since 2.5.0.1
 *
 * @see com.biglybt.pif.ui.model.BasicPluginConfigModel#addFileParameter2(String, String, String, String[])
 * @see com.biglybt.pif.ui.model.BasicPluginConfigModel#addFileParameter2(String, String, String)
 */
public interface
FileParameter
	extends Parameter, ParameterWithHint
{
	public String
	getValue();

	/**
	 * Title of the dialog box shown when user clicks the browse button
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setDialogTitleKey(String key);
}
