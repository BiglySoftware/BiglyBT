/*
 * Created on 08-Jun-2004
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.internat.MessageText;

import com.biglybt.ui.swt.imageloader.ImageLoader;

/**
 * @author parg
 *
 */

public class
DirectoryParameter
	extends Parameter
{
	Control[] controls;

	StringParameter sp;

	public
	DirectoryParameter(
		final Composite pluginGroup,
		String			name,
		String			defaultValue )
	{
  	super(name);
	  	controls = new Control[2];

	    sp = new StringParameter(pluginGroup, name, defaultValue);

	    controls[0] = sp.getControl();
	    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
	    controls[0].setLayoutData(gridData);

	    Button browse = new Button(pluginGroup, SWT.PUSH);
	    ImageLoader.getInstance().setButtonImage(browse, getBrowseImageResource());
	    browse.setToolTipText(MessageText.getString("ConfigView.button.browse"));

	    browse.addListener(SWT.Selection, new Listener() {
	      @Override
	      public void handleEvent(Event event) {
	    	  String path = DirectoryParameter.this.openDialog(pluginGroup.getShell(), sp.getValue());
	    	  if (path != null) {
	    		  sp.setValue(path);
	    	  }
	      }
	    });
	    controls[1] = browse;
	  }

	@Override
	public void
	setLayoutData(
		Object layoutData)
	{
	}

	@Override
	public Control
	getControl()
	{
		return( controls[0]);
	}

	@Override
	public Control[]
	getControls()
	{
	    return controls;
	}

	protected String getBrowseImageResource() {
		return "openFolderButton";
	}

	protected String openDialog(Shell shell, String old_value) {
        DirectoryDialog dialog = new DirectoryDialog(shell, SWT.APPLICATION_MODAL);
        dialog.setFilterPath(old_value);
        return dialog.open();
	}

  @Override
  public void setValue(Object value) {
  	if (value instanceof String) {
  		sp.setValue((String)value);
  	}
  }
}
