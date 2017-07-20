/*
 * File    : ConfigPanelFileTorrents.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
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

package com.biglybt.ui.swt.views.configsections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.ui.swt.imageloader.ImageLoader;

import com.biglybt.pif.ui.config.ConfigSection;

public class ConfigSectionFileTorrents implements UISWTConfigSection {
  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_FILES;
  }

	@Override
	public String configSectionGetName() {
		return "torrents";
	}

  @Override
  public void configSectionSave() {
  }

  @Override
  public void configSectionDelete() {
		ImageLoader imageLoader = ImageLoader.getInstance();
		imageLoader.releaseImage("openFolderButton");
  }

	@Override
	public int maxUserMode() {
		return 0;
	}



  @Override
  public Composite configSectionCreate(final Composite parent) {

    // Sub-Section: File -> Torrent
    // ----------------------------
    Composite cTorrent = new Composite(parent, SWT.NULL);

    configSectionCreateSupport( cTorrent );

    return( cTorrent );
  }

  public void
  configSectionCreateSupport(
	final Composite cTorrent )
{
	ImageLoader imageLoader = ImageLoader.getInstance();
	Image imgOpenFolder = imageLoader.getImage("openFolderButton");

	GridData gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    Utils.setLayoutData(cTorrent, gridData);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    cTorrent.setLayout(layout);

    int userMode = COConfigurationManager.getIntParameter("User Mode");

    	// Save .Torrent files to..

    BooleanParameter saveTorrents = new BooleanParameter(cTorrent, "Save Torrent Files",
                                                         "ConfigView.label.savetorrents");

    Composite gSaveTorrents = new Composite(cTorrent, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalIndent = 25;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(gSaveTorrents, gridData);
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.numColumns = 3;
    gSaveTorrents.setLayout(layout);

    Label lSaveDir = new Label(gSaveTorrents, SWT.NULL);
    Messages.setLanguageText(lSaveDir, "ConfigView.label.savedirectory");

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    final StringParameter torrentPathParameter = new StringParameter(gSaveTorrents,
                                                                     "General_sDefaultTorrent_Directory");
    torrentPathParameter.setLayoutData(gridData);

    Button browse2 = new Button(gSaveTorrents, SWT.PUSH);
    browse2.setImage(imgOpenFolder);
    imgOpenFolder.setBackground(browse2.getBackground());
    browse2.setToolTipText(MessageText.getString("ConfigView.button.browse"));

    browse2.addListener(SWT.Selection, new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
      @Override
      public void handleEvent(Event event) {
        DirectoryDialog dialog = new DirectoryDialog(cTorrent.getShell(), SWT.APPLICATION_MODAL);
        dialog.setFilterPath(torrentPathParameter.getValue());
        dialog.setText(MessageText.getString("ConfigView.dialog.choosedefaulttorrentpath"));
        String path = dialog.open();
        if (path != null) {
          torrentPathParameter.setValue(path);
        }
      }
    });
    Utils.setLayoutData(browse2, new GridData());

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    new BooleanParameter(gSaveTorrents, "Save Torrent Backup",
                        "ConfigView.label.savetorrentbackup").setLayoutData(gridData);

    Control[] controls = new Control[]{ gSaveTorrents };
    IAdditionalActionPerformer grayPathAndButton1 = new ChangeSelectionActionPerformer(controls);
    saveTorrents.setAdditionalActionPerformer(grayPathAndButton1);

   		// Delete .Torrent files

    BooleanParameter deleteTorrents = new BooleanParameter(cTorrent, "Delete Original Torrent Files",
                                                         "ConfigView.label.deletetorrents");


    	// add stopped

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    BooleanParameter add_stopped = new BooleanParameter(
    		cTorrent,
			"Default Start Torrents Stopped",
    		"ConfigView.label.defaultstarttorrentsstopped");
    add_stopped.setLayoutData(gridData);

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    BooleanParameter stop_and_pause = new BooleanParameter(
    		cTorrent,
			"Default Start Torrents Stopped Auto Pause",
    		"ConfigView.label.defaultstarttorrentsstoppedandpause");

    stop_and_pause.setLayoutData(gridData);

    // Watch Folder
    BooleanParameter watchFolder = new BooleanParameter(cTorrent, "Watch Torrent Folder",
                                                        "ConfigView.label.watchtorrentfolder");

    Composite gWatchFolder = new Composite(cTorrent, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalIndent = 25;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(gWatchFolder, gridData);
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.numColumns = 5;
    gWatchFolder.setLayout(layout);

    int	num_folders = COConfigurationManager.getIntParameter( "Watch Torrent Folder Path Count", 1);

    for ( int i=0;i<num_folders;i++){
	    Label lImportDir = new Label(gWatchFolder, SWT.NULL);
	    Messages.setLanguageText(lImportDir, "ConfigView.label.importdirectory");

	    gridData = new GridData(GridData.FILL_HORIZONTAL);
	    final StringParameter watchFolderPathParameter =
	    	new StringParameter(gWatchFolder, "Watch Torrent Folder Path" + (i==0?"":(" " + i )), "");
	    watchFolderPathParameter.setLayoutData(gridData);

	    Button browse4 = new Button(gWatchFolder, SWT.PUSH);
	    browse4.setImage(imgOpenFolder);
	    imgOpenFolder.setBackground(browse4.getBackground());
	    browse4.setToolTipText(MessageText.getString("ConfigView.button.browse"));

	    browse4.addListener(SWT.Selection, new Listener() {
	      @Override
	      public void handleEvent(Event event) {
	        DirectoryDialog dialog = new DirectoryDialog(cTorrent.getShell(), SWT.APPLICATION_MODAL);
	        dialog.setFilterPath(watchFolderPathParameter.getValue());
	        dialog.setText(MessageText.getString("ConfigView.dialog.choosewatchtorrentfolderpath"));
	        String path = dialog.open();
	        if (path != null) {
	          watchFolderPathParameter.setValue(path);
	        }
	      }
	    });

	    Label lTag = new Label(gWatchFolder, SWT.NULL);
	    Messages.setLanguageText(lTag, "label.assign.to.tag");

	    StringParameter tagParam =
	    	new StringParameter(gWatchFolder, "Watch Torrent Folder Tag" + (i==0?"":(" " + i )), "");
	    gridData = new GridData();
	    gridData.widthHint = 60;
	    tagParam.setLayoutData(gridData);

    }

    	// add another folder
    Label addAnother = new Label(gWatchFolder, SWT.NULL);
    Messages.setLanguageText(addAnother, "ConfigView.label.addanotherfolder");

    Composite gAddButton = new Composite(gWatchFolder, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 4;
    Utils.setLayoutData(gAddButton, gridData);
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.numColumns = 2;
    gAddButton.setLayout(layout);
    Button addButton = new Button(gAddButton, SWT.PUSH);
    Messages.setLanguageText(addButton, "Button.add");

    addButton.addListener(SWT.Selection, new Listener() {
	      @Override
	      public void handleEvent(Event event) {

	    	  int num = COConfigurationManager.getIntParameter( "Watch Torrent Folder Path Count", 1);

	    	  COConfigurationManager.setParameter( "Watch Torrent Folder Path Count", num+1);

	    	  Utils.disposeComposite( cTorrent, false );

	    	  configSectionCreateSupport( cTorrent );

	    	  cTorrent.layout( true,  true );
	      }});
    Label pad = new Label(gAddButton, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pad.setLayoutData( gridData);

    	// watch interval

    Label lWatchTorrentFolderInterval = new Label(gWatchFolder, SWT.NULL);
    Messages.setLanguageText(lWatchTorrentFolderInterval, "ConfigView.label.watchtorrentfolderinterval");
    String	sec = " " + MessageText.getString("ConfigView.section.stats.seconds");
    String	min = " " + MessageText.getString("ConfigView.section.stats.minutes");
    String	hr  = " " + MessageText.getString("ConfigView.section.stats.hours");

    int	[]	watchTorrentFolderIntervalValues =
    	{ 1, 2, 3, 4, 5, 10, 30, 1*60, 2*60, 3*60, 4*60, 5*60, 10*60, 15*60, 30*60, 60*60, 2*60*60, 4*60*60, 6*60*60, 8*60*60, 12*60*60, 16*60*60, 20*60*60, 24*60*60 };

    final String watchTorrentFolderIntervalLabels[] = new String[watchTorrentFolderIntervalValues.length];

    for (int i = 0; i < watchTorrentFolderIntervalValues.length; i++) {
      int secs 	= watchTorrentFolderIntervalValues[i];
      int mins  = secs/60;
      int hrs	= mins/60;

      watchTorrentFolderIntervalLabels[i] = " " + (secs<60?(secs + sec):((hrs==0?(mins + min):(hrs + hr ))));
    }

    gridData = new GridData();
    gridData.horizontalSpan = 4;
    new IntListParameter(gWatchFolder, "Watch Torrent Folder Interval Secs",
                         watchTorrentFolderIntervalLabels,
                         watchTorrentFolderIntervalValues).setLayoutData(gridData);

    	// add stopped

    gridData = new GridData();
    gridData.horizontalSpan = 5;
    new BooleanParameter(gWatchFolder, "Start Watched Torrents Stopped",
                         "ConfigView.label.startwatchedtorrentsstopped").setLayoutData(gridData);

    controls = new Control[]{ gWatchFolder };
    watchFolder.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(controls));
  }
}
