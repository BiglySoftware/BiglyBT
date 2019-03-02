/*
 * Created on 30 nov. 2004
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.installer.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.mainwindow.ListenerNeedingCoreRunning;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.wizard.*;


/**
 * @author Olivier Chalouhi
 *
 */
public class IPWFilePanel extends AbstractWizardPanel<InstallPluginWizard> {
  
  String	initial_file;
	
  Text txtFile;
  boolean valid = false;
  
  public IPWFilePanel(
	InstallPluginWizard wizard,
    IWizardPanel<InstallPluginWizard> previous) 
  {
    super(wizard,previous);
  }
  
  public IPWFilePanel(
		  InstallPluginWizard wizard,
		  IWizardPanel<InstallPluginWizard> previous,
		  String		file ) 
  {
	  super(wizard,previous);
	  
	  initial_file = file;
  }
  
  public void show() {
    wizard.setTitle(MessageText.getString("installPluginsWizard.file.title"));
	wizard.setErrorMessage("");
	
	Composite rootPanel = wizard.getPanel();
	GridLayout layout = new GridLayout();
	layout.numColumns = 1;
	rootPanel.setLayout(layout);

	Composite panel = new Composite(rootPanel, SWT.NULL);
	GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
	panel.setLayoutData(gridData);
	layout = new GridLayout();
	layout.numColumns = 3;
	panel.setLayout(layout);
	
	Label label = new Label(panel,SWT.NULL);
	Messages.setLanguageText(label,"installPluginsWizard.file.file");
	
	txtFile = new Text(panel,SWT.BORDER);
	
	if ( initial_file != null ){
		txtFile.setText( initial_file );
	}
	GridData data = new GridData(GridData.FILL_HORIZONTAL);
	txtFile.setLayoutData(data);
	txtFile.addListener(SWT.Modify,new ListenerNeedingCoreRunning(){
	  public void handleEvent(Core core, Event event) {
	    checkValidFile(core);
	  }
	}
	);
	
	
	Button btnBrowse = new Button(panel,SWT.PUSH);
	Messages.setLanguageText(btnBrowse,"installPluginsWizard.file.browse");
	btnBrowse.addListener(SWT.Selection,new Listener() {
	  public void handleEvent(Event event) {
	    FileDialog fd = new FileDialog(wizard.getWizardWindow());
	    fd.setFilterExtensions(new String[] {"*.zip;*.jar;*.biglybt"});
	    fd.setFilterNames(new String[] { MessageText.getString( "label.biglybt.plugins" )});
	    String fileName = fd.open();
	    if(fileName != null) txtFile.setText(fileName);	    
	  }
	});	
	
	if ( initial_file != null ){
		CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				checkValidFile(core);
			}
		});
	}
  }
  
  private void checkValidFile(Core core) {
		String fileName = txtFile.getText();
		String error_message = null;
		try {
			File f = new File(fileName);
			if (f.isFile() && (f.getName().endsWith(".jar") || f.getName().endsWith(".zip") || f.getName().endsWith(".biglybt"))) {
				wizard.setErrorMessage("");
				wizard.setNextEnabled(true);
				List<InstallablePlugin> list = new ArrayList<InstallablePlugin>();
				InstallablePlugin plugin = core.getPluginManager().getPluginInstaller().installFromFile(f);
				list.add(plugin);
				wizard.plugins = list;
				valid = true;
				return;
			}
		} catch (PluginException e) {
			error_message = e.getMessage();
		} catch (Exception e) {
			error_message = null;
			Debug.printStackTrace(e);
		}
		valid = false;
		if (!fileName.equals("")) {
			String error_message_full;
			if (new File(fileName).isFile()) {
				error_message_full = MessageText.getString("installPluginsWizard.file.invalidfile");
			} else {
				error_message_full = MessageText.getString("installPluginsWizard.file.no_such_file");
			}
			if (error_message != null) {
				error_message_full += " (" + error_message + ")";
			}
			wizard.setErrorMessage(error_message_full);
			wizard.setNextEnabled(false);
		}
  }
  
	public boolean 
	isNextEnabled() 
	{
	   return valid;
	}
	
	public IWizardPanel<InstallPluginWizard> getNextPanel() {
	   return new IPWInstallModePanel(wizard,this);
	}
}
