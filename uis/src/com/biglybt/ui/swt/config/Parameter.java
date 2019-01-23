/*
 * Created on 22-May-2004
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

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;

public abstract class
Parameter
	implements IParameter
{
	public final static String KEY_LABEL_ADDCOPYTOCLIPMENU = "AddCopyMenu";
	protected final String configID;
	protected Label relatedLabel;
	protected ConfigParameterAdapter config_adapter;

	protected  List	change_listeners;

	private static AEMonitor	class_mon	= new AEMonitor( "Parameter:class" );

	public Parameter(String configID) {
		this.configID = configID;
		if (configID != null) {
			config_adapter = new ConfigParameterAdapter( this, configID);
		}
	}

	public String getConfigID() {
		return configID;
	}

	public boolean
	isInitialised()
	{
		return( true );
	}

	@Override
	public Control[]
	getControls()
	{
		return( new Control[]{ getControl() });
	}

 	public void
	addChangeListener(
		ParameterChangeListener	l )
	{
 		try{
 			class_mon.enter();

	 		if ( change_listeners == null ){

	 			change_listeners = new ArrayList(1);
	 		}

	  		change_listeners.add( l );

 		}finally{

 			class_mon.exit();
 		}
	}

	public void
	removeChangeListener(
		ParameterChangeListener	l )
	{
		try{
 			class_mon.enter();

 			change_listeners.remove(l);

		}finally{

 			class_mon.exit();
 		}
	}

	public void
	setEnabled(
		boolean	enabled )
	{
		for ( Control c: getControls()){

			c.setEnabled(enabled);
		}
	}

	public boolean
	isDisposed()
	{
		return( getControl().isDisposed());
	}

	public abstract void setValue(Object value);

	public Object getValueObject() {
		return null;
	}

	public Label getRelatedLabel() {
		return relatedLabel;
	}

	public void setRelatedLabel(Label label) {
		if (relatedLabel == label) {
			return;
		}
		relatedLabel = label;
		if (label == null || label.isDisposed()) {
			return;
		}
		label.addMenuDetectListener(e -> {
			Label curLabel = (Label) e.widget;
			boolean add_copy = curLabel.getData(KEY_LABEL_ADDCOPYTOCLIPMENU) != null;
			if (configID == null && !add_copy) {
				return;
			}

			Menu menu = new Menu(curLabel);

			if (configID != null) {
				// Note: Subclasses of Parameter store a default value that may be
				//       independent from COConfigurationManager.  They really shouldn't
				//       so we don't handle those cases.
				boolean hasBeenSet = COConfigurationManager.doesParameterNonDefaultExist(
						configID);
				if (hasBeenSet) {
					Object defaultValue = COConfigurationManager.getDefault(configID);
					if (defaultValue != null) {
						MenuItem item = new MenuItem(menu, SWT.PUSH);
						Messages.setLanguageText(item, "menu.config.reset.to.default");
						if ((defaultValue instanceof String)
								|| (defaultValue instanceof Number)) {
							item.setToolTipText(defaultValue.toString());
						}
						item.addListener(SWT.Selection, event -> {
							COConfigurationManager.removeParameter(configID);
						});
					}
				}
			}

			if (add_copy) {
				ClipboardCopy.addCopyToClipMenu(menu, () -> curLabel.getText().trim());
			}

			if (menu.getItemCount() > 0) {
				menu.addListener(SWT.Hide, event -> {
					event.display.asyncExec(() -> menu.dispose());
				});
				menu.setVisible(true);
			} else {
				menu.dispose();
			}
		});
	}
}
