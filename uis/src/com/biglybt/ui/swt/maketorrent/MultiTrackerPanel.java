/*
 * File : ModePanel.java Created : 30 sept. 2003 01:51:05 By : Olivier
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
public class MultiTrackerPanel extends AbstractWizardPanel<NewTorrentWizard> implements TrackerEditorListener{

  private Combo configList;
  private Tree configDetails;

  private Button btnNew;
  private Button btnEdit;
  private Button btnDelete;

  public MultiTrackerPanel(NewTorrentWizard wizard, AbstractWizardPanel<NewTorrentWizard> previous) {
    super(wizard, previous);
  }

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#show()
	 */
  @Override
  public void show() {
    wizard.setTitle(MessageText.getString("wizard.multitracker.title"));
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
    // Multi-Tracker Configuration

    final Label labelTitle = new Label(panel,SWT.NULL);
    Messages.setLanguageText(labelTitle, "wizard.multitracker.configuration");
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
        updateTrackers();
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
        List group = new ArrayList();
        List tracker = new ArrayList();
        tracker.add(wizard.getTrackerURL());
        group.add(tracker);
        new MultiTrackerEditor(wizard.getWizardWindow(), null,group,MultiTrackerPanel.this);
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
        Map multiTrackers = TrackersUtil.getInstance().getMultiTrackers();
        new MultiTrackerEditor( wizard.getWizardWindow(),selected,(List)multiTrackers.get(selected),MultiTrackerPanel.this);
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
        TrackersUtil.getInstance().removeMultiTracker(selected);
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

    refreshList(((NewTorrentWizard)wizard).multiTrackerConfig);
    refreshDetails();
    setEditDeleteEnable();
}

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#getNextPanel()
	 */
  @Override
  public IWizardPanel getNextPanel() {

	if(((NewTorrentWizard) wizard).useWebSeed){
	     return new WebSeedPanel((NewTorrentWizard) wizard, this);
	}

	return(((NewTorrentWizard)wizard).getNextPanelForMode( this ));
  }


  @Override
  public boolean isNextEnabled() {
    return true;
  }

  void refreshDetails() {
    configDetails.removeAll();
    List trackers = ((NewTorrentWizard) wizard).trackers;
    Iterator iter = trackers.iterator();
    while(iter.hasNext()) {
        List trackerGroup = (List) iter.next();
        TreeItem itemRoot = new TreeItem(configDetails,SWT.NULL);
        Messages.setLanguageText(itemRoot, "wizard.multitracker.group");
        Iterator iter2 = trackerGroup.iterator();
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
  public void trackersChanged(String oldName, String newName, List trackers) {
    TrackersUtil util = TrackersUtil.getInstance();
    if(oldName != null && !oldName.equals(newName))
      util.removeMultiTracker(oldName);
    util.addMultiTracker(newName,trackers);
    refreshList(newName);
    refreshDetails();
    setEditDeleteEnable();
  }

  private void refreshList(String toBeSelected) {
    Map multiTrackers = TrackersUtil.getInstance().getMultiTrackers();
    configList.removeAll();
    Iterator iter = multiTrackers.keySet().iterator();
    while(iter.hasNext()) {
      configList.add((String)iter.next());
    }
    int selection = configList.indexOf(toBeSelected);
    if(selection != -1) {
      configList.select(selection);
    } else if(configList.getItemCount() > 0) {
      configList.select(0);
    }
    updateTrackers();
  }

  private void updateTrackers() {
    int selection = configList.getSelectionIndex();
    if(selection == -1) {
      List group = new ArrayList();
      List tracker = new ArrayList();
      tracker.add(wizard.getTrackerURL());
      group.add(tracker);
      ((NewTorrentWizard)wizard).trackers = group;
      ((NewTorrentWizard)wizard).multiTrackerConfig = "";
      setNext();
      return;
    }
    String selected = configList.getItem(selection);
    ((NewTorrentWizard)wizard).multiTrackerConfig = selected;
    Map multiTrackers = TrackersUtil.getInstance().getMultiTrackers();
    ((NewTorrentWizard)wizard).trackers = (List) multiTrackers.get(selected);
    setNext();
  }

  private void setNext() {
    String trackerUrl = wizard.getTrackerURL();
    List groups = ((NewTorrentWizard)wizard).trackers;
    Iterator iterGroups = groups.iterator();
    while(iterGroups.hasNext()) {
      List trackers = (List) iterGroups.next();
      Iterator iterTrackers = trackers.iterator();
      while(iterTrackers.hasNext()) {
        String tracker = (String) iterTrackers.next();
        if(trackerUrl.equals(tracker))
        {
          wizard.setNextEnabled(true);
          wizard.setErrorMessage("");
          return;
        }
      }
    }
    wizard.setNextEnabled(false);
    wizard.setErrorMessage(MessageText.getString("wizard.multitracker.noannounce"));

  }
}
