/*
 * Created on 04-Nov-2005
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

package com.biglybt.pif.ui;

import com.biglybt.pif.PluginInterface;

public interface
UIInstanceFactory
{
		/**
		 * Some UI instances need to understand which plugin they are associated with. This method
		 * gives the opportunity to customise the UIInstance returned to a plugin so that operations
		 * on it can take the appropriate actions
		 */

	public UIInstance
	getInstance(
		PluginInterface		plugin_interface );

		/**
		 * This method will be called by the UI manager when detaching the UI to permit the action to be
		 * vetoed/any detach logic to occur. It should not be directly called by the plugin code
		 */

	public void
	detach()

		throws UIException;

	/**
	 * This method will be called by the UI manager after the UIInstance is detached.
	 */
	void dispose();

	public String
	getUIType();
}
