/*
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
package com.biglybt.ui.swt.views;

import java.net.URL;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerScraper;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.BufferedTruncatedLabel;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.maketorrent.MultiTrackerEditor;
import com.biglybt.ui.swt.maketorrent.TrackerEditorListener;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

/**
 * aka "Primary Tracker" view in "Sources" View
 * <p>
 * This view is placed within the {@link TrackerView} even though it relies on
 * a {@link DownloadManager} datasource instead of TrackerPeerSource
 *
 */
public class ScrapeInfoView
	implements UISWTViewCoreEventListener
{
	private DownloadManager manager;

	private Composite	cParent;
	private Composite 	cScrapeInfoView;

	private BufferedTruncatedLabel tracker_status;

	private Button updateButton;

	private BufferedLabel trackerUpdateIn;

	private Menu menuTracker;

	private MenuItem itemSelect;

	private BufferedTruncatedLabel trackerUrlValue;

	private long lastRefreshSecs;

	private UISWTView swtView;

	private String getFullTitle() {
		return MessageText.getString("ScrapeInfoView.title");
	}

	private void initialize(Composite parent) {
		cParent = parent;
		Label label;
		GridData gridData;
		final Display display = parent.getDisplay();

		if (cScrapeInfoView == null || cScrapeInfoView.isDisposed()) {
			cScrapeInfoView = new Composite(parent, SWT.NONE);
		}

		gridData = new GridData(GridData.FILL_BOTH);
		cScrapeInfoView.setLayoutData(gridData);

		GridLayout layoutInfo = new GridLayout();
		layoutInfo.numColumns = 4;
		cScrapeInfoView.setLayout(layoutInfo);

		label = new Label(cScrapeInfoView, SWT.LEFT);
		Messages.setLanguageText(label, "GeneralView.label.trackerurl"); //$NON-NLS-1$
		label.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
		label.setForeground(display.getSystemColor(SWT.COLOR_LINK_FOREGROUND));
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent arg0) {
				String announce = trackerUrlValue.getText();
				if (announce != null && announce.length() != 0) {
					new Clipboard(display).setContents(new Object[] {
						announce
					}, new Transfer[] {
						TextTransfer.getInstance()
					});
				}
			}

			@Override
			public void mouseDown(MouseEvent arg0) {
				String announce = trackerUrlValue.getText();
				if (announce != null && announce.length() != 0) {
					new Clipboard(display).setContents(new Object[] {
						announce
					}, new Transfer[] {
						TextTransfer.getInstance()
					});
				}
			}
		});

		menuTracker = new Menu(parent.getShell(), SWT.POP_UP);
		itemSelect = new MenuItem(menuTracker, SWT.CASCADE);
		Messages.setLanguageText(itemSelect, "GeneralView.menu.selectTracker");
		MenuItem itemEdit = new MenuItem(menuTracker, SWT.NULL);
		Messages.setLanguageText(itemEdit, "MyTorrentsView.menu.editTracker");

		cScrapeInfoView.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				menuTracker.dispose();
			}
		});

		itemEdit.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				final TOTorrent torrent = manager.getTorrent();

				if (torrent == null) {
					return;
				}

				List<List<String>> group = TorrentUtils.announceGroupsToList(torrent);

				new MultiTrackerEditor(null,null, group, new TrackerEditorListener() {
					@Override
					public void trackersChanged(String str, String str2, List<List<String>> _group) {
						TorrentUtils.listToAnnounceGroups(_group, torrent);

						try {
							TorrentUtils.writeToFile(torrent);
						} catch (Throwable e2) {

							Debug.printStackTrace(e2);
						}

						TRTrackerAnnouncer tc = manager.getTrackerClient();

						if (tc != null) {

							tc.resetTrackerUrl(true);
						}
					}
				}, true, true );
			}
		});

		TOTorrent torrent = manager==null?null:manager.getTorrent();

		itemEdit.setEnabled( torrent != null && !TorrentUtils.isReallyPrivate( torrent ));

		final Listener menuListener = new Listener() {
			@Override
			public void handleEvent(Event e) {
				if (e.widget instanceof MenuItem) {

					String text = ((MenuItem) e.widget).getText();

					TOTorrent torrent = manager.getTorrent();

					TorrentUtils.announceGroupsSetFirst(torrent, text);

					try {
						TorrentUtils.writeToFile(torrent);

					} catch (TOTorrentException f) {

						Debug.printStackTrace(f);
					}

					TRTrackerAnnouncer tc = manager.getTrackerClient();

					if (tc != null) {

						tc.resetTrackerUrl(false);
					}
				}
			}
		};

		menuTracker.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event e) {
				Menu menuSelect = itemSelect.getMenu();
				if (menuSelect != null && !menuSelect.isDisposed()) {
					menuSelect.dispose();
				}
				if (manager == null || cScrapeInfoView == null
						|| cScrapeInfoView.isDisposed()) {
					return;
				}
				List<List<String>> groups = TorrentUtils.announceGroupsToList(manager.getTorrent());
				menuSelect = new Menu(cScrapeInfoView.getShell(), SWT.DROP_DOWN);
				itemSelect.setMenu(menuSelect);

				for (List<String> trackers : groups) {
					MenuItem menuItem = new MenuItem(menuSelect, SWT.CASCADE);
					Messages.setLanguageText(menuItem, "wizard.multitracker.group");
					Menu menu = new Menu(cScrapeInfoView.getShell(), SWT.DROP_DOWN);
					menuItem.setMenu(menu);

					for (String url : trackers) {
						MenuItem menuItemTracker = new MenuItem(menu, SWT.CASCADE);
						menuItemTracker.setText(url);
						menuItemTracker.addListener(SWT.Selection, menuListener);
					}
				}
			}
		});

		trackerUrlValue = new BufferedTruncatedLabel(cScrapeInfoView, SWT.LEFT, 70);

		trackerUrlValue.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent event) {
				if (event.button == 3
						|| (event.button == 1 && event.stateMask == SWT.CONTROL)) {
					menuTracker.setVisible(true);
				} else if (event.button == 1) {
					String url = trackerUrlValue.getText();
					if (url.startsWith("http://") || url.startsWith("https://")) {
						int pos = -1;
						if ((pos = url.indexOf("/announce")) != -1) {
							url = url.substring(0, pos + 1);
						}
						Utils.launch(url);
					}
				}
			}
		});

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		trackerUrlValue.setLayoutData(gridData);

		////////////////////////

		label = new Label(cScrapeInfoView, SWT.LEFT);
		Messages.setLanguageText(label, "GeneralView.label.tracker");
		tracker_status = new BufferedTruncatedLabel(cScrapeInfoView, SWT.LEFT, 150);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		tracker_status.setLayoutData(gridData);

		label = new Label(cScrapeInfoView, SWT.LEFT);
		Messages.setLanguageText(label, "GeneralView.label.updatein");
		trackerUpdateIn = new BufferedLabel(cScrapeInfoView, SWT.LEFT);
		gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_CENTER);
		trackerUpdateIn.setLayoutData(gridData);

		updateButton = new Button(cScrapeInfoView, SWT.PUSH);
		Messages.setLanguageText(updateButton, "GeneralView.label.trackerurlupdate");
		updateButton.setLayoutData(new GridData());
		updateButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				new AEThread2( "SIV:async" )
				{
					@Override
					public void
					run()
					{
						if ( manager.getTrackerClient() != null ){

							manager.requestTrackerAnnounce( false );

						}else{

							manager.requestTrackerScrape( true );
						}
					}
				}.start();
			}
		});

		cScrapeInfoView.layout(true);

	}

	private void refresh() {
		if (manager == null) {
			return;
		}

		long thisRefreshSecs = SystemTime.getCurrentTime() / 1000;
		if (lastRefreshSecs != thisRefreshSecs) {
			lastRefreshSecs = thisRefreshSecs;
			setTracker();
		}
	}

	private Composite getComposite() {
		return cScrapeInfoView;
	}

	private void setTracker() {
		if (cScrapeInfoView == null || cScrapeInfoView.isDisposed()) {
			return;
		}

		Display display = cScrapeInfoView.getDisplay();

		String status 	= manager.getTrackerStatus();
		int time 		= manager.getTrackerTime();

		TRTrackerAnnouncer trackerClient = manager.getTrackerClient();

		if ( trackerClient != null ){

			tracker_status.setText( trackerClient.getStatusString());

			time = trackerClient.getTimeUntilNextUpdate();

		}else{

			tracker_status.setText( status );
		}

		if (time < 0) {

			trackerUpdateIn.setText(MessageText.getString("GeneralView.label.updatein.querying"));

		} else {

			trackerUpdateIn.setText(TimeFormatter.formatColon(time));
		}

		boolean update_state;

		String trackerURL = null;

		if (trackerClient != null) {

			URL temp = trackerClient.getTrackerURL();

			if (temp != null) {

				trackerURL = temp.toString();
			}
		}

		if (trackerURL == null) {

			TOTorrent torrent = manager.getTorrent();

			if (torrent != null) {

				trackerURL = torrent.getAnnounceURL().toString();
			}
		}

		if (trackerURL != null) {

			trackerUrlValue.setText(trackerURL);

			if ((trackerURL.startsWith("http://") || trackerURL.startsWith("https://"))) {
				trackerUrlValue.setForeground(display.getSystemColor(SWT.COLOR_LINK_FOREGROUND));
				trackerUrlValue.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
				Messages.setLanguageText(trackerUrlValue.getWidget(),
						"GeneralView.label.trackerurlopen.tooltip", true);
			} else {
				trackerUrlValue.setForeground(null);
				trackerUrlValue.setCursor(null);
				Messages.setLanguageText(trackerUrlValue.getWidget(), null);
				Utils.setTT(trackerUrlValue,null);
			}
		}

		if (trackerClient != null) {

			update_state = ((SystemTime.getCurrentTime() / 1000
					- trackerClient.getLastUpdateTime() >= TRTrackerAnnouncer.REFRESH_MINIMUM_SECS));

		} else {
			TRTrackerScraperResponse sr = manager.getTrackerScrapeResponse();

			if ( sr == null ){

				update_state = true;

			}else{

				update_state = ((SystemTime.getCurrentTime()
						- sr.getScrapeStartTime() >= TRTrackerScraper.REFRESH_MINIMUM_SECS * 1000));
			}
		}

		if (updateButton.getEnabled() != update_state) {

			updateButton.setEnabled(update_state);
		}
		cScrapeInfoView.layout();
	}

	private void setDownlaodManager(DownloadManager dm) {
		if ( manager == dm ){
			return;
		}
		
		manager = dm;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if ( cScrapeInfoView != null ){
					Utils.disposeComposite(cScrapeInfoView, false);
				}
				if ( cParent != null && !cParent.isDisposed() ){

					initialize( cParent );
				}
			}
		});
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = (UISWTView)event.getData();
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        //delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
      	Object ds = swtView.getParentView().getDataSource();	// always use the parent DS as this view isn't related to selection
      	if (ds instanceof Object[] && ((Object[]) ds).length > 0) {
      		ds = ((Object[]) ds)[0];
      	}
      	if (ds instanceof DownloadManager) {
					DownloadManager dm = (DownloadManager) ds;
					setDownlaodManager(dm);
				}
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;
    }

    return true;
  }
}
