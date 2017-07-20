/*
 * File    : PluginView.java
 * Created : Oct 12, 2005
 * By      : TuxPaper
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.pif.ui;

import com.biglybt.pif.PluginInterface;

/**
 * All plugin views should inherit from this interface so that we can always
 * check to see if they are a plugin view.
 * <p>
 * Any non generic UI plugin view functions are placed here, and every UI
 * should implement them.
 *
 * @author TuxPaper
 * @since 2.3.0.5
 *
 * @see com.biglybt.ui.swt.pif.UISWTView
 */
public interface UIPluginView {
	/**
	 * Retrieve the data sources related to this view.
	 *
	 * @return dependent upon subclasses implementation
	 */
	public Object getDataSource();

	/**
	 * ID of the view
	 *
	 * @return ID of the view
	 *
	 * @since 2.3.0.6
	 */
	public String getViewID();

	/**
	 * Closes the view
	 *
	 * @since 2.3.0.6
	 */
	public void closeView();

	/**
	 * Gets the plugin interface associated with this view, null if none defined
	 *
	 * @since 4.5.1.1
	 */
	public PluginInterface getPluginInterface();

	/**
	 *
	 * @since 4.6.0.5
	 */
	public void setToolBarListener(UIPluginViewToolBarListener l);

	/**
	 * @since 4.6.0.5
	 */
	public UIPluginViewToolBarListener getToolBarListener();
}
