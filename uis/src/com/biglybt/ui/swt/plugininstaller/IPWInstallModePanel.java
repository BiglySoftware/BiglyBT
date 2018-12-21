/*
 * Created on 29 nov. 2004
 * Created by Olivier Chalouhi
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
package com.biglybt.ui.swt.plugininstaller;

import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.wizard.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;


/**
 * @author Olivier Chalouhi
 *
 */
public class IPWInstallModePanel extends AbstractWizardPanel<InstallPluginWizard> {
  
  private static final int MODE_USER   = 0;
  private static final int MODE_SHARED = 1;
  
  public 
  IPWInstallModePanel(
	InstallPluginWizard 					wizard, 
	IWizardPanel<InstallPluginWizard> 		previous ) 
  {
	super(wizard, previous);
  }


  public void 
  show() 
  {
	wizard.setTitle(MessageText.getString("installPluginsWizard.installMode.title"));
	wizard.setErrorMessage("");
	
	Composite rootPanel = wizard.getPanel();
	GridLayout layout = new GridLayout();
	layout.numColumns = 1;
	rootPanel.setLayout(layout);

	Composite panel = new Composite(rootPanel, SWT.NULL);
	GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
	panel.setLayoutData(gridData);
	layout = new GridLayout();
	layout.numColumns = 1;
	panel.setLayout(layout);

		// default is PER_USER installation (changed 2013/02/14 by parg)
	
	wizard.shared = false;

	Button bSharedMode = new Button(panel,SWT.RADIO);
	Messages.setLanguageText(bSharedMode,"installPluginsWizard.installMode.shared");
	bSharedMode.setData("mode",new Integer(MODE_SHARED));
	bSharedMode.setSelection(wizard.shared);
	GridData data = new GridData(GridData.FILL_VERTICAL);
	data.verticalAlignment = GridData.VERTICAL_ALIGN_END;
	bSharedMode.setLayoutData(data);
	
	
	Button bUserMode = new Button(panel,SWT.RADIO);
	Messages.setLanguageText(bUserMode,"installPluginsWizard.installMode.user");
	bUserMode.setData("mode",new Integer(MODE_USER));
	bUserMode.setSelection(!wizard.shared);
	data = new GridData(GridData.FILL_VERTICAL);
	data.verticalAlignment = GridData.VERTICAL_ALIGN_BEGINNING;
	bUserMode.setLayoutData(data);
	
	
	Listener modeListener = new Listener() {
	  public void handleEvent(Event e) {
	    wizard.shared = ((Integer) e.widget.getData("mode")).intValue() == MODE_SHARED;
	  }
	};

	bUserMode.addListener(SWT.Selection,modeListener);
	bSharedMode.addListener(SWT.Selection,modeListener);
  }
	
	public boolean 
	isFinishEnabled() 
	{
	   return( true );
	}
	
	public IWizardPanel<InstallPluginWizard> getFinishPanel() {
	    return new IPWFinishPanel(wizard,this);
	}
}
