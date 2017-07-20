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
package com.biglybt.ui.swt.pluginsuninstaller;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;

import com.biglybt.core.Core;
import com.biglybt.core.CoreRunningListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;
import com.biglybt.ui.swt.wizard.Wizard;


/**
 * @author Olivier Chalouhi
 *
 */
public class UIPWListPanel extends AbstractWizardPanel {

  Table pluginList;

  public
  UIPWListPanel(
	Wizard 					wizard,
	IWizardPanel 			previous )
  {
	super(wizard, previous);
  }


  @Override
  public void
  show()
  {
  	CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				_show(core);
			}
		});
  }

  private void _show(Core core) {
    wizard.setTitle(MessageText.getString("uninstallPluginsWizard.list.title"));
    wizard.setErrorMessage("");

	Composite rootPanel = wizard.getPanel();
	GridLayout layout = new GridLayout();
	layout.numColumns = 1;
	rootPanel.setLayout(layout);

	Composite panel = new Composite(rootPanel, SWT.NULL);
	GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
	Utils.setLayoutData(panel, gridData);
	layout = new GridLayout();
	layout.numColumns = 1;
	panel.setLayout(layout);

	final Label lblStatus = new Label(panel,SWT.NULL);
	Messages.setLanguageText(lblStatus,"uninstallPluginsWizard.list.loaded");

	pluginList = new Table(panel,SWT.BORDER | SWT.V_SCROLL | SWT.CHECK | SWT.FULL_SELECTION | SWT.SINGLE);
	pluginList.setHeaderVisible(true);
	GridData data = new GridData(GridData.FILL_HORIZONTAL);
	data.heightHint = 200;
	Utils.setLayoutData(pluginList, data);


	TableColumn tcName = new TableColumn(pluginList,SWT.LEFT);
	Messages.setLanguageText(tcName,"label.name");
	tcName.setWidth(Utils.adjustPXForDPI(200));

	TableColumn tcVersion = new TableColumn(pluginList,SWT.LEFT);
	Messages.setLanguageText(tcVersion,"ConfigView.pluginlist.column.version");
	tcVersion.setWidth(Utils.adjustPXForDPI(150));

    PluginInterface plugins[] = new PluginInterface[0];
    try {
      plugins = core.getPluginManager().getPluginInterfaces();

      Arrays.sort(
	      	plugins,
		  	new Comparator()
			{
	      		@Override
			      public int
				compare(
					Object o1,
					Object o2)
	      		{
	      			return(((PluginInterface)o1).getPluginName().compareTo(((PluginInterface)o2).getPluginName()));
	      		}
			});
    } catch(final Exception e) {

    	Debug.printStackTrace(e);
    }

    	// one "plugin" can have multiple interfaces. We need to group by their id

    Map	pid_map = new HashMap();

    for(int i = 0 ; i < plugins.length ; i++){

        PluginInterface plugin = plugins[i];

        String	pid   = plugin.getPluginID();

        ArrayList	pis = (ArrayList)pid_map.get( pid );

        if ( pis == null ){

        	pis = new ArrayList();

        	pid_map.put( pid, pis );
        }

        pis.add( plugin );
    }

    ArrayList[]	pid_list = new ArrayList[pid_map.size()];

    pid_map.values().toArray( pid_list );

    Arrays.sort(
    		pid_list,
		  	new Comparator()
			{
	      		@Override
			      public int
				compare(
					Object o1,
					Object o2)
	      		{
	      			ArrayList	l1 = (ArrayList)o1;
	      			ArrayList	l2 = (ArrayList)o2;
	      			return(((PluginInterface)l1.get(0)).getPluginName().compareToIgnoreCase(((PluginInterface)l2.get(0)).getPluginName()));
	      		}
			});

    for(int i = 0 ; i < pid_list.length ; i++){

      ArrayList	pis = pid_list[i];

      boolean	skip = false;

      String	display_name = "";

      for (int j=0;j<pis.size();j++){

      	PluginInterface	pi = (PluginInterface)pis.get(j);

      	if ( pi.getPluginState().isMandatory() || pi.getPluginState().isBuiltIn()){

      		skip = true;

      		break;
      	}

      	display_name += (j==0?"":",") + pi.getPluginName();
      }

      if ( skip ){

      	continue;
      }

      PluginInterface plugin = (PluginInterface)pis.get(0);

      List	selected_plugins = ((UnInstallPluginWizard)wizard).getPluginList();

      TableItem item = new TableItem(pluginList,SWT.NULL);
      item.setData(plugin);
      item.setText(0, display_name);
      item.setChecked( selected_plugins.contains( plugin ));
      String version = plugin.getPluginVersion();
      if(version == null) version = MessageText.getString("uninstallPluginsWizard.list.nullversion");
      item.setText(1,version);
    }

	pluginList.addListener(SWT.Selection,new Listener() {
	  @Override
	  public void handleEvent(Event e) {
	    updateList();
	  }
	});
  }

	@Override
	public boolean
	isFinishEnabled()
	{
		return(((UnInstallPluginWizard)wizard).getPluginList().size() > 0 );
	}

	@Override
	public IWizardPanel getFinishPanel() {
	    return new UIPWFinishPanel(wizard,this);
	}

  public void updateList() {
    ArrayList list = new ArrayList();
    TableItem[] items = pluginList.getItems();
    for(int i = 0 ; i < items.length ; i++) {
      if(items[i].getChecked())
        list.add(items[i].getData());
    }
    ((UnInstallPluginWizard)wizard).setPluginList(list);
    ((UnInstallPluginWizard)wizard).setFinishEnabled( isFinishEnabled() );

  }
}
