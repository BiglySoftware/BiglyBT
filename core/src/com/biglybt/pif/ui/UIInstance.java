/*
 * Created on 05-Sep-2005
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

import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;

/**
 * This interface represents a UI running on the core (e.g. the SWT UI).
 * The actual implementation of this will support UI-specific operations -
 * you need to cast this to the appropriate type to access them.
 *
 * This is to allow "native" UI plugin access - for example a plugin that
 * directly accesses SWT functionality would do it via this object (it'll be
 * an instance of com.biglybt.ui.swt.pif.UISWTInstance )
 */

public interface
UIInstance
{
	public static final String	UIT_SWT			= "swt";
	public static final String	UIT_CONSOLE		= "console";

		/**
		 * @since 1.0.0.0
		 * @return
		 */

	public String
	getUIType();

	public boolean openView(BasicPluginViewModel model);

	/**
	 * Prompts the user with a title, text, and a series of options.  The options
	 * are typically displayed as buttons.
	 *
	 * @param title
	 * @param text
	 * @param options
	 * @return Index of option chosen, -1 if cancelled or error
	 */
	public int promptUser(String title, String text, String[] options,
			int defaultOption);

	/**
	 * Creates a {@link UIInputReceiver} instance to allow a plugin to request
	 * text input from the user. Some interfaces may not allow or support the
	 * ability for a plugin to request text input from a user, in which case
	 * they will return <code>null</code> for this method.
	 */
	public UIInputReceiver getInputReceiver();

	/**
	 * Creates a {@link UIMessage} instance to allow a plugin to inform or ask the
	 * user something. Some interfaces may not allow or support the
	 * ability for a plugin to ask a user in this manner, in which case
	 * they will return <code>null</code> for this method.
	 *
	 * @since 1.0.0.0
	 */
	public UIMessage createMessage();

	public UIToolBarManager getToolBarManager();
}
