/*
 * Created on 17-Jun-2004
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
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.biglybt.pifimpl.local.ui.config.ActionParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.widgets.ButtonWithMinWidth;

import com.biglybt.pif.ui.config.ActionParameter;

public class ButtonSwtParameter
	extends BaseSwtParameter<ButtonSwtParameter, Object>
{
	Button button;
	
	/**
	 * Make a button.
	 * <p/>
	 * When parent is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Parent is not altered
	 * @param buttonTextKey Messagebundle key text displayed in button
	 * @param labelKey Messagebundle key for the checkbox
	 */
	public ButtonSwtParameter(Composite composite, ActionParameterImpl pluginParam ){
		super(null);

		createStandardLabel(composite, pluginParam.getLabelKey());

		String button_key	= pluginParam.getActionResource();
		String image_id		= ((ActionParameter)pluginParam).getImageID();

		int width = 40;
		
		if ( image_id == null ){
			image_id = "";
		}
		
		if ( button_key.isEmpty() && !image_id.isEmpty()){
		
			ToolBar toolBar = new ToolBar( composite, SWT.FLAT );
		
			ToolItem item = new ToolItem( toolBar, SWT.PUSH );
			
			item.setImage( ImageLoader.getInstance().getImage( image_id ));
			
			item.addListener( SWT.Selection, ev->{
				
				toolBar.forceFocus();
				triggerChangeListeners(true);
			});
			
		}else{
		
			Button button = new ButtonWithMinWidth(composite, SWT.PUSH, width);
			setMainControl(button);
	
			Messages.setLanguageText(button, button_key );
	
			
			if ( !image_id.isEmpty()){
				
				button.setImage(ImageLoader.getInstance().getImage( image_id ));
			}
			
			button.addListener(SWT.Selection, event -> {
				// Force control to ensure we aren't on an un-processed parameter
				// On OSX, button doesn't automatically get focused on click, so
				// if the user edits a StringParameter, and clicks a button, the
				// StringParameter will not be saved yet..
				button.forceFocus();
				triggerChangeListeners(true);
			});
		}
		
		setPluginParameter(pluginParam);
	}

	@Override
	protected void triggerSubClassChangeListeners() {
		if (pluginParam instanceof ParameterImpl) {
			((ParameterImpl) pluginParam).fireParameterChanged();
		}
	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		if (pluginParam instanceof ActionParameter) {

			Utils.execSWTThread(() -> {
				if (button == null || button.isDisposed()) {
					return;
				}

				if (Messages.updateLanguageKey(button,
						((ActionParameter) pluginParam).getActionResource())) {
					Utils.relayout(button);
				}
			});
		}
	}
}
