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

package com.biglybt.ui.swt.config;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

/**
 * This class inherits from the DirectoryParameter class and appears the same,
 * except for the dialog box that it opens.
 *
 * The reason for this is not because it is logically an extension of the
 * DirectoryParameter class, but because it's better to leave the existing code
 * where it is (for the sake of browsing CVS history).
 */

public class FileParameter extends DirectoryParameter {

	protected String[] extension_list;

	public FileParameter(Composite pluginGroup, String name, String defaultValue, String[] extension_list) {
		super(pluginGroup, name, defaultValue);
		this.extension_list = extension_list;
	}

	/**
	 * We don't have a better resource for this at the moment.
	 */
	@Override
	protected String getBrowseImageResource() {
		return "openFolderButton";
	}

	@Override
	protected String openDialog(Shell shell, String old_value) {
        FileDialog dialog = new FileDialog(shell, SWT.APPLICATION_MODAL);
        dialog.setFilterPath(old_value);
        if (this.extension_list != null) {
        	dialog.setFilterExtensions(this.extension_list);
        }
        return dialog.open();
	}
}
