/*
 * Created on 9 sept. 2003
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
package com.biglybt.ui.swt;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.torrent.*;
import com.biglybt.core.util.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.ui.swt.components.shell.ShellFactory;

/**
 * @author Olivier
 *
 */
public class TrackerChangerWindow {
  public TrackerChangerWindow(final DownloadManager[] dms ) {
    final Shell shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM );
    shell.setText(MessageText.getString("TrackerChangerWindow.title"));
    Utils.setShellIcon(shell);
    GridLayout layout = new GridLayout();
    shell.setLayout(layout);

    Label label = new Label(shell, SWT.NONE);
    Messages.setLanguageText(label, "TrackerChangerWindow.newtracker");
    GridData gridData = new GridData();
    gridData.widthHint = 400;
    Utils.setLayoutData(label, gridData);

    final Text url = new Text(shell, SWT.BORDER);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 400;
    Utils.setLayoutData(url, gridData);
    Utils.setTextLinkFromClipboard(shell, url, false, false);

    Label labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    Utils.setLayoutData(labelSeparator, gridData);

    Composite panel = new Composite(shell, SWT.NONE);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    Utils.setLayoutData(panel, gridData);
    layout = new GridLayout();
    layout.numColumns = 3;
    panel.setLayout(layout);



    label = new Label( panel, SWT.NONE );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    Utils.setLayoutData(label, gridData );

    Button ok = new Button(panel, SWT.PUSH);
    ok.setText(MessageText.getString("Button.ok"));
    gridData = new GridData();
    gridData.widthHint = 70;
    gridData.horizontalAlignment = GridData.END;
    Utils.setLayoutData(ok, gridData);
    shell.setDefaultButton(ok);
    ok.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(Event event) {
        try {
        	String[] _urls = url.getText().split( "," );

        	List<String>	urls = new ArrayList<>();

    		for ( String url: _urls ){

    			url = url.trim();

    			if ( url.length() > 0 ){

    				try{
    					new URL( url );

    					urls.add( 0, url );

    				}catch( Throwable e ){

    					Debug.out( "Invalid URL: " + url );
    				}
    			}
    		}

        	for ( DownloadManager dm: dms ){

	        	TOTorrent	torrent = dm.getTorrent();

	        	if ( torrent != null ){

	        		for ( String url: urls ){

	        			TorrentUtils.announceGroupsInsertFirst( torrent, url );
	        		}

	        		TorrentUtils.writeToFile( torrent );

	        		TRTrackerAnnouncer announcer = dm.getTrackerClient();

	        		if ( announcer != null ){

	        			announcer.resetTrackerUrl(false);
	        		}
	        	}
        	}

        	shell.dispose();
        }
        catch (Exception e) {
        	Debug.printStackTrace( e );
        }
      }
    });

    Button cancel = new Button(panel, SWT.PUSH);
    cancel.setText(MessageText.getString("Button.cancel"));
    gridData = new GridData();
    gridData.widthHint = 70;
    gridData.horizontalAlignment = GridData.END;
    Utils.setLayoutData(cancel, gridData);
    cancel.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(Event event) {
        shell.dispose();
      }
    });

    shell.pack();
	Utils.centreWindow( shell );
    Utils.createURLDropTarget(shell, url);
    shell.open();
  }
}
