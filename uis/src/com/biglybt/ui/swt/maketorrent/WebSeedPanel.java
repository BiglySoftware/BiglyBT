/*
 * File : ModePanel.java Created : 30 sept. 2003 01:51:05 By : Olivier
 *
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */

package com.biglybt.ui.swt.maketorrent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.TrackersUtil;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

/**
 * @author Olivier
 *
 */
public class WebSeedPanel extends AbstractWizardPanel<NewTorrentWizard> implements WebSeedsEditorListener{

  private Combo configList;
  private Tree configDetails;

  private Button btnNew;
  private Button btnEdit;
  private Button btnDelete;

  public WebSeedPanel(NewTorrentWizard wizard, AbstractWizardPanel<NewTorrentWizard> previous) {
    super(wizard, previous);
  }

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#show()
	 */
  @Override
  public void show() {
    wizard.setTitle(MessageText.getString("wizard.webseed.title"));
    wizard.setCurrentInfo("");
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

    //Line :
    // Web Seed Configuration

    final Label labelTitle = new Label(panel,SWT.NULL);
    Messages.setLanguageText(labelTitle, "wizard.webseed.configuration");
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    labelTitle.setLayoutData(gridData);

    configList = new Combo(panel,SWT.READ_ONLY);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    configList.setLayoutData(gridData);
    configList.addListener(SWT.Selection,new Listener() {
      @Override
      public void handleEvent(Event e) {
        updateWebSeeds();
        refreshDetails();
      }
    });

    btnNew = new Button(panel, SWT.PUSH);
    Messages.setLanguageText(btnNew, "wizard.multitracker.new");
    gridData = new GridData();
    gridData.widthHint = 100;
    btnNew.setLayoutData(gridData);
    btnNew.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event e) {
    	Map webseeds = new HashMap();
       	webseeds.put( "getright", new ArrayList());
       	webseeds.put( "webseed", new ArrayList());
        new WebSeedsEditor(null,webseeds,WebSeedPanel.this);
      }
    });

    btnEdit = new Button(panel, SWT.PUSH);
    Messages.setLanguageText(btnEdit, "wizard.multitracker.edit");
    gridData = new GridData();
    gridData.widthHint = 100;
    btnEdit.setLayoutData(gridData);
    btnEdit.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event e) {
        int selection = configList.getSelectionIndex();
        String selected = configList.getItem(selection);
        Map webseeds = TrackersUtil.getInstance().getWebSeeds();
        new WebSeedsEditor(selected,(Map)webseeds.get(selected),WebSeedPanel.this);
      }
    });

    btnDelete = new Button(panel, SWT.PUSH);
    Messages.setLanguageText(btnDelete, "wizard.multitracker.delete");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    gridData.widthHint = 100;
    btnDelete.setLayoutData(gridData);
    btnDelete.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event e) {
        int selection = configList.getSelectionIndex();
        String selected = configList.getItem(selection);
        TrackersUtil.getInstance().removeWebSeed(selected);
        refreshList("");
        refreshDetails();
        setEditDeleteEnable();
      }
    });
    Control labelSeparator = Utils.createSkinnedLabelSeparator(panel, SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    labelSeparator.setLayoutData(gridData);

    configDetails = new Tree(panel,SWT.BORDER);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.heightHint = 150;
    gridData.horizontalSpan = 3;
    configDetails.setLayoutData(gridData);

    refreshList( wizard.webSeedConfig);
    refreshDetails();
    setEditDeleteEnable();
}

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#getNextPanel()
	 */
  @Override
  public IWizardPanel<NewTorrentWizard> getNextPanel() {
	  return( wizard.getNextPanelForMode( this ));
  }


  @Override
  public boolean isNextEnabled() {
    return true;
  }

  void refreshDetails() {
    configDetails.removeAll();
    Map webseeds = wizard.webseeds;
    Iterator iter = webseeds.entrySet().iterator();
    while(iter.hasNext()) {
        Map.Entry	entry = (Map.Entry)iter.next();
        TreeItem itemRoot = new TreeItem(configDetails,SWT.NULL);
        itemRoot.setText((String)entry.getKey());
        Iterator iter2 = ((List)entry.getValue()).iterator();
        while(iter2.hasNext()) {
          String url = (String) iter2.next();
          new TreeItem(itemRoot,SWT.NULL).setText(url);
        }
        itemRoot.setExpanded(true);
    }
  }

  void setEditDeleteEnable() {
    if(configList.getItemCount() > 0) {
      btnEdit.setEnabled(true);
      btnDelete.setEnabled(true);
    } else {
      btnEdit.setEnabled(false);
      btnDelete.setEnabled(false);
    }
  }

  @Override
  public void webSeedsChanged(String oldName, String newName, Map ws) {
    TrackersUtil util = TrackersUtil.getInstance();
    if(oldName != null && !oldName.equals(newName))
      util.removeWebSeed(oldName);
    util.addWebSeed(newName,ws);
    refreshList(newName);
    refreshDetails();
    setEditDeleteEnable();
  }

  private void refreshList(String toBeSelected) {
    Map webseeds = TrackersUtil.getInstance().getWebSeeds();
    configList.removeAll();
    Iterator iter = webseeds.keySet().iterator();
    while(iter.hasNext()) {
      configList.add((String)iter.next());
    }
    int selection = configList.indexOf(toBeSelected);
    if(selection != -1) {
      configList.select(selection);
    } else if(configList.getItemCount() > 0) {
      configList.select(0);
    }
    updateWebSeeds();
  }

  private void updateWebSeeds() {
    int selection = configList.getSelectionIndex();
    if(selection == -1) {
      ((NewTorrentWizard)wizard).webSeedConfig = "";
      ((NewTorrentWizard)wizard).webseeds = new HashMap();
      setNext();
      return;
    }
    String selected = configList.getItem(selection);
    ((NewTorrentWizard)wizard).webSeedConfig = selected;
    Map webseeds = TrackersUtil.getInstance().getWebSeeds();
    ((NewTorrentWizard)wizard).webseeds = (Map) webseeds.get(selected);
    setNext();
  }

  private void setNext() {
	  wizard.setNextEnabled(true);
	  wizard.setErrorMessage("");
  }
}
