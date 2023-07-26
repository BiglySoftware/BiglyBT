/*
 * Created on Mar 20, 2006 6:40:14 PM
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
 */
package com.biglybt.ui.swt.mainwindow;

import java.text.NumberFormat;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.network.ConnectionManager;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIStatusTextClickListener;
import com.biglybt.ui.common.updater.UIUpdatableAlways;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.Alerts.AlertHistoryListener;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.nat.NatTestWindow;
import com.biglybt.ui.swt.progress.*;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.GCStringPrinter.URLInfo;
import com.biglybt.ui.swt.update.UpdateWindow;
import com.biglybt.ui.swt.views.stats.StatsView;

/**
 * Moved from MainWindow and GUIUpdater
 */
public class MainStatusBar
	implements IMainStatusBar, UIUpdatableAlways
{
	/**
	 * Warning status icon identifier
	 */
	private static final String STATUS_ICON_WARN = "sb_warning";

	private static final String ID = "MainStatusBar";

	private AEMonitor this_mon = new AEMonitor(ID);

	private UpdateWindow updateWindow;

	private Composite	parent;
	private Composite 	statusBar;

	private CLabel statusText;

	private String statusTextKey = "";

	private String statusImageKey = null;

	private Image statusImage = null;

	private AZProgressBar progressBar;

	private CLabelPadding ipBlocked;

	private CLabelPadding srStatus;

	private CLabelPadding natStatus;

	private CLabelPadding dhtStatus;

	private CLabelPadding statusDown;

	private CLabelPadding statusUp;

	private Composite plugin_label_composite;

	private ArrayList<Runnable> listRunAfterInit = new ArrayList<>();

	private Display display;

	// For Refresh..
	private long last_sr_ratio = -1;

	private int last_sr_status = -1;

	private int 	lastNATstatus 	= -1;
	private String 	lastNATInfo		= "";
	
	private String lastNATimageID = null;

	private int lastDHTstatus = -1;

	private long lastDHTcount = -1;

	private NumberFormat numberFormat;

	private OverallStats overall_stats;

	private ConnectionManager connection_manager;

	private DHTPlugin dhtPlugin;

	UIFunctions uiFunctions;

	private UIStatusTextClickListener clickListener;

	//	 final int borderFlag = (Constants.isOSX) ? SWT.SHADOW_NONE : SWT.SHADOW_IN;
	private static final int borderFlag = SWT.SHADOW_NONE;

	/**
	 * Just a flag to differentiate az3 from other versions; default status bar text is handled differently between versions.
	 * Specifically speaking the Vuze UI status text is just empty whereas the Classic UI status text has an icon
	 * and the application version number.
	 */
	private boolean isAZ3 = false;

	/**
	 * Just a reference to the static <code>ProgressReportingManager</code> to make the code look cleaner instead of
	 * using <code>ProgressReportingManager.getInstance().xxx()</code> everywhere.
	 */
	private ProgressReportingManager PRManager = ProgressReportingManager.getInstance();

	/**
	 * A <code>GridData</code> for the progress bar; used to dynamically provide .widthHint to the layout manager
	 */
	private GridData progressGridData = new GridData(SWT.RIGHT, SWT.CENTER,
			false, false);

	/**
	 * A clickable image label that brings up the Progress viewer
	 */
	private CLabelPadding progressViewerImageLabel;


	private String lastSRimageID = null;

	private int last_dl_limit;

	private long last_rec_data = - 1;

	private long last_rec_prot;

	private long[] max_rec = { 0 };
	private long[] max_sent = { 0 };

	private Image imgRec;
	private Image imgSent;

	private Image	warningIcon;
	private Image	warningGreyIcon;
	private Image	infoIcon;

	private CLabelPadding statusWarnings;
	private ProgressListener progressListener;
	private Map<String, ParameterListener> mapConfigListeners = new HashMap<>();
	private AlertHistoryListener alertHistoryListener;

	/**
	 *
	 */
	public MainStatusBar() {
		numberFormat = NumberFormat.getInstance();
		// Probably need to wait for core to be running to make sure dht plugin is fully avail
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				PluginManager pm = core.getPluginManager();
				connection_manager = PluginInitializer.getDefaultInterface().getConnectionManager();
				PluginInterface dht_pi = pm.getPluginInterfaceByClass(DHTPlugin.class);
				if (dht_pi != null) {
					dhtPlugin = (DHTPlugin) dht_pi.getPlugin();
				}
			}
		});
	}

	/**
	 *
	 * @return composite holding the statusbar
	 */
	public Composite initStatusBar(final Composite _parent) {
		this.parent = _parent;
		this.display = parent.getDisplay();
		this.uiFunctions = UIFunctionsManager.getUIFunctions();
		ImageLoader imageLoader = ImageLoader.getInstance();

		FormData formData;

		Color fgColor = parent.getForeground();

		statusBar = new Composite(parent, SWT.NONE);
		statusBar.setForeground(fgColor);
		isAZ3 = "az3".equalsIgnoreCase(COConfigurationManager.getStringParameter("ui"));

		statusBar.getShell().addListener(SWT.Deiconify, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Utils.execSWTThreadLater(0, new AERunnable() {
					@Override
					public void runSupport() {
						if (!statusBar.isDisposed()) {
							statusBar.layout();
						}
					}
				});
			}
		});
		statusBar.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (uiFunctions != null) {
					UIUpdater uiUpdater = uiFunctions.getUIUpdater();
					if (uiUpdater != null) {
						uiUpdater.removeUpdater(MainStatusBar.this);
					}
				}
				PRManager.removeListener(progressListener);

				Utils.disposeSWTObjects(imgRec, imgSent);

				Utils.removeParameterListeners(mapConfigListeners);
				if (alert_flasher_event != null) {
					alert_flasher_event.cancel();
					alert_flasher_event = null;
				}

				if (alertHistoryListener != null) {
					Alerts.removeMessageHistoryListener(alertHistoryListener);
					alertHistoryListener = null;
				}
			}
		});

		GridLayout layout_status = new GridLayout();
		layout_status.numColumns = 20;
		layout_status.horizontalSpacing = 0;
		layout_status.verticalSpacing = 0;
		layout_status.marginHeight = 0;
		if (Constants.isOSX) {
			// OSX has a resize widget on the bottom right.  It's about 15px wide.
			try {
				layout_status.marginRight = 15;
			} catch (NoSuchFieldError e) {
				// Pre SWT 3.1
				layout_status.marginWidth = 15;
			}
		} else {
			layout_status.marginWidth = 0;
		}
		statusBar.setLayout(layout_status);

		//Either the Status Text
		statusText = new CLabel(statusBar, borderFlag);
		statusText.setForeground(fgColor);
		statusText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL
				| GridData.VERTICAL_ALIGN_FILL));

		addStatusBarMenu(statusText);

		GC gc = new GC(statusText);
		// add 6, because CLabel forces a 3 pixel indent
		int height = Math.max(16, gc.getFontMetrics().getHeight()) + 6;
		gc.dispose();

		formData = new FormData();
		formData.height = height;
		formData.bottom = new FormAttachment(100, 0); // 2 params for Pre SWT 3.0
		formData.left = new FormAttachment(0, 0); // 2 params for Pre SWT 3.0
		formData.right = new FormAttachment(100, 0); // 2 params for Pre SWT 3.0
		statusBar.setLayoutData(formData);

		Listener listener = new Listener() {
			@Override
			public void handleEvent(Event e) {
				if (clickListener == null) {
					if (updateWindow != null) {
						updateWindow.show();
					}
				} else {
					clickListener.UIStatusTextClicked();
				}
			}
		};

		statusText.addListener(SWT.MouseUp, listener);
		statusText.addListener(SWT.MouseDoubleClick, listener);

		// final int progressFlag = (Constants.isOSX) ? SWT.INDETERMINATE	: SWT.HORIZONTAL;
		// KN: Don't know why OSX is treated differently but this check was already here from the previous code
		if (Constants.isOSX) {
			progressBar = new AZProgressBar(statusBar, true);
		} else {
			progressBar = new AZProgressBar(statusBar, false);
		}

		progressBar.setVisible(false);
		progressGridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		progressGridData.widthHint = 5;
		progressBar.setLayoutData(progressGridData);

		// addRIP();

		/*
		 * Progress reporting window image label
		 */

		progressViewerImageLabel = new CLabelPadding(statusBar, SWT.NONE);
		// image set below after adding listener
		Utils.setTT( progressViewerImageLabel, MessageText.getString("Progress.reporting.statusbar.button.tooltip"));
		progressViewerImageLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				/*
				 * Opens the progress viewer if any of the reporters in the array is NOT already opened
				 * KN: TODO -- This is only a partial solution to minimize the occurrence of the main progress window
				 * opening more than once.  The one remaining case where multiple windows will still open is
				 * when you have one opened already... then run another process such as a torrent file download...
				 * at this point this new process is not in the already opened window so the check would
				 * allow the second window to open.
				 */
				IProgressReporter[] reporters = PRManager.getReportersArray(false);
				if (reporters.length == 0) {
					/*
					 * If there's nothing to see then open the window; the default widow will say there's nothing to see
					 * KN: calling isShowingEmpty return true is there is already a window opened showing the empty panel
					 */
					if (!ProgressReporterWindow.isShowingEmpty()) {
						ProgressReporterWindow.open(reporters,
								ProgressReporterWindow.SHOW_TOOLBAR);
					}
				} else {

					for (int i = 0; i < reporters.length; i++) {
						if (!ProgressReporterWindow.isOpened(reporters[i])) {
							ProgressReporterWindow.open(reporters,
									ProgressReporterWindow.SHOW_TOOLBAR);
							break;
						}
					}
				}
			}
		});
		progressViewerImageLabel.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				ImageLoader imageLoader = ImageLoader.getInstance();
				imageLoader.releaseImage("progress_error");
				imageLoader.releaseImage("progress_info");
				imageLoader.releaseImage("progress_viewer");
			}
		});

		this.plugin_label_composite = new Composite(statusBar, SWT.NONE);
		this.plugin_label_composite.setForeground(fgColor);
		GridLayout gridLayout = new GridLayout();
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginBottom = 0;
		gridLayout.marginTop = 0;
		gridLayout.marginLeft = 0;
		gridLayout.marginRight = 0;
		gridLayout.numColumns = 20; // Something nice and big. :)

		GridData gridData = new GridData(GridData.FILL_VERTICAL);
		gridData.heightHint = height;
		gridData.minimumHeight = height;
		plugin_label_composite.setLayout(gridLayout);
		plugin_label_composite.setLayoutData(gridData);

		srStatus = new CLabelPadding(statusBar, borderFlag);
		srStatus.setText(MessageText.getString("SpeedView.stats.ratio"));

		Utils.addAndFireParameterListener(mapConfigListeners, true,
				"Status Area Show SR", new ParameterListener() {
					@Override
					public void parameterChanged(String parameterName) {
						srStatus.setVisible(COConfigurationManager.getBooleanParameter(parameterName));
						statusBar.layout();
					}
				});

		natStatus = new CLabelPadding(statusBar, borderFlag);
		natStatus.setText("");

		final Menu natStatusMenu = new Menu(statusBar.getShell(), SWT.POP_UP);
		natStatus.setMenu( natStatusMenu );
		
		MenuItem nat_test = new MenuItem( natStatusMenu, SWT.PUSH );
		Messages.setLanguageText( nat_test, "MainWindow.menu.tools.nattest" );
		nat_test.addSelectionListener(
			new SelectionAdapter(){
				
				@Override
				public void widgetSelected(SelectionEvent arg0){
						new NatTestWindow();
				}
			});
				
		
		Utils.addAndFireParameterListener(mapConfigListeners, true,
				"Status Area Show NAT", new ParameterListener() {
					@Override
					public void parameterChanged(String parameterName) {
						natStatus.setVisible(COConfigurationManager.getBooleanParameter(parameterName));
						statusBar.layout();
					}
				});

		dhtStatus = new CLabelPadding(statusBar, borderFlag);
		dhtStatus.setText("");
		Utils.setTT( dhtStatus,MessageText.getString("MainWindow.dht.status.tooltip"));

		Utils.addAndFireParameterListener(mapConfigListeners, true,
				"Status Area Show DDB", new ParameterListener() {
					@Override
					public void parameterChanged(String parameterName) {
						dhtStatus.setVisible(COConfigurationManager.getBooleanParameter(parameterName));
						statusBar.layout();
					}
				});

		// ip filters

		ipBlocked = new CLabelPadding(statusBar, borderFlag);
		ipBlocked.setText("{} IPs:"); //$NON-NLS-1$
		Messages.setLanguageText(ipBlocked, "MainWindow.IPs.tooltip");
		ipBlocked.addListener(SWT.MouseDoubleClick, new ListenerNeedingCoreRunning() {
			@Override
			public void handleEvent(Core core, Event event) {
				BlockedIpsWindow.showBlockedIps(core, parent.getShell());
			}
		});

		final Menu menuIPFilter = new Menu(statusBar.getShell(), SWT.POP_UP);
		ipBlocked.setMenu( menuIPFilter );

		menuIPFilter.addListener(
			SWT.Show,
			new Listener()
			{
				@Override
				public void
				handleEvent(Event e)
				{
					MenuItem[] oldItems = menuIPFilter.getItems();

					for(int i = 0; i < oldItems.length; i++){

						oldItems[i].dispose();
					}

					if ( !CoreFactory.isCoreRunning()){

						return;
					}

					Core core = CoreFactory.getSingleton();

					final IpFilter ip_filter = core.getIpFilterManager().getIPFilter();

					final MenuItem ipfEnable = new MenuItem(menuIPFilter, SWT.CHECK);

					ipfEnable.setSelection( ip_filter.isEnabled());

					Messages.setLanguageText(ipfEnable, "MyTorrentsView.menu.ipf_enable");

					ipfEnable.addSelectionListener(
						new SelectionAdapter()
						{
							@Override
							public void
							widgetSelected(
								SelectionEvent e)
							{
								ip_filter.setEnabled( ipfEnable.getSelection());
							}
						});

					final MenuItem ipfOptions = new MenuItem(menuIPFilter, SWT.PUSH);

					Messages.setLanguageText(ipfOptions, "ipfilter.options");

					ipfOptions.addSelectionListener(
						new SelectionAdapter()
						{
							@Override
							public void
							widgetSelected(
								SelectionEvent e)
							{
								UIFunctions uif = UIFunctionsManager.getUIFunctions();

								if (uif != null) {

									uif.getMDI().showEntryByID(
											MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, "ipfilter");
								}
							}
						});
				}
			});

		Utils.addAndFireParameterListener(mapConfigListeners, true,
				"Status Area Show IPF", new ParameterListener() {
					@Override
					public void parameterChanged(String parameterName) {
						ipBlocked.setVisible(COConfigurationManager.getBooleanParameter(parameterName));
						statusBar.layout();
					}
				});

			// down speed


		statusDown = new CLabelPadding(statusBar, borderFlag);
		statusDown.setImage(imageLoader.getImage("down"));
		//statusDown.setText(/*MessageText.getString("ConfigView.download.abbreviated") +*/"n/a");
		Messages.setLanguageText(statusDown,
				"MainWindow.status.updowndetails.tooltip");

		Listener lStats = new Listener() {
			@Override
			public void handleEvent(Event e) {
				uiFunctions.getMDI().loadEntryByID(StatsView.VIEW_ID, true, false, "TransferStatsView");
			}
		};

		statusUp = new CLabelPadding(statusBar, borderFlag);
		statusUp.setImage(imageLoader.getImage("up"));
		//statusUp.setText(/*MessageText.getString("ConfigView.upload.abbreviated") +*/"n/a");
		Messages.setLanguageText(statusUp,
				"MainWindow.status.updowndetails.tooltip");

		statusDown.addListener(SWT.MouseDoubleClick, lStats);
		statusUp.addListener(SWT.MouseDoubleClick, lStats);

		Listener lDHT = new Listener() {
			@Override
			public void handleEvent(Event e) {
				uiFunctions.getMDI().loadEntryByID(StatsView.VIEW_ID, true, false, "DHTView");
			}
		};

		dhtStatus.addListener(SWT.MouseDoubleClick, lDHT);

		Listener lSR = new Listener() {
			@Override
			public void handleEvent(Event e) {

				uiFunctions.getMDI().loadEntryByID(StatsView.VIEW_ID, true, false, "SpeedView");

				OverallStats stats = StatsFactory.getStats();

				if (stats == null) {
					return;
				}

				long ratio = (1000 * stats.getUploadedBytes() / (stats.getDownloadedBytes() + 1));

				if (ratio < 900) {

					//Utils.launch(Constants.AZUREUS_WIKI + "Share_Ratio");
				}
			}
		};

		srStatus.addListener(SWT.MouseDoubleClick, lSR);

		Listener lNAT = new ListenerNeedingCoreRunning() {
			@Override
			public void handleEvent(Core core, Event e) {
				uiFunctions.getMDI().loadEntryByID(
						MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, true, false,
						ConfigSection.SECTION_CONNECTION);

				if (PluginInitializer.getDefaultInterface().getConnectionManager().getNATStatus() != ConnectionManager.NAT_OK) {
					Utils.launch(Wiki.NAT_PROBLEM);
				}
			}
		};

		natStatus.addListener(SWT.MouseDoubleClick, lNAT);

		boolean bSpeedMenu = COConfigurationManager.getBooleanParameter("GUI_SWT_bOldSpeedMenu");

		if (bSpeedMenu) {
			// Status Bar Menu construction
			final Menu menuUpSpeed = new Menu(statusBar.getShell(), SWT.POP_UP);
			menuUpSpeed.addListener(SWT.Show, new Listener() {
				@Override
				public void handleEvent(Event e) {
					if (!CoreFactory.isCoreRunning()) {
						return;
					}
					Core core = CoreFactory.getSingleton();
					GlobalManager globalManager = core.getGlobalManager();

					SelectableSpeedMenu.generateMenuItems(menuUpSpeed, core,
							globalManager, true);
				}
			});
			statusUp.setMenu(menuUpSpeed);
		} else {

			statusUp.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDown(MouseEvent e) {
					if (!(e.button == 3 || (e.button == 1 && e.stateMask == SWT.CONTROL))) {
						return;
					}
					Event event = new Event();
					event.type = SWT.MouseUp;
					event.widget = e.widget;
					event.stateMask = e.stateMask;
					event.button = e.button;
					e.widget.getDisplay().post(event);

					CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
						@Override
						public void coreRunning(Core core) {
							SelectableSpeedMenu.invokeSlider(statusUp, core, true);
						}
					});
				}
			});
		}

		if (bSpeedMenu) {
			final Menu menuDownSpeed = new Menu(statusBar.getShell(), SWT.POP_UP);
			menuDownSpeed.addListener(SWT.Show, new Listener() {
				@Override
				public void handleEvent(Event e) {
					if (!CoreFactory.isCoreRunning()) {
						return;
					}
					Core core = CoreFactory.getSingleton();
					GlobalManager globalManager = core.getGlobalManager();

					SelectableSpeedMenu.generateMenuItems(menuDownSpeed, core,
							globalManager, false);
				}
			});
			statusDown.setMenu(menuDownSpeed);
		} else {
			statusDown.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDown(MouseEvent e) {
					if (!(e.button == 3 || (e.button == 1 && e.stateMask == SWT.CONTROL))) {
						return;
					}
					Event event = new Event();
					event.type = SWT.MouseUp;
					event.widget = e.widget;
					event.stateMask = e.stateMask;
					event.button = e.button;
					e.widget.getDisplay().post(event);

					CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
						@Override
						public void coreRunning(Core core) {
							SelectableSpeedMenu.invokeSlider(statusDown, core, false);
						}
					});
				}
			});
		}

		statusWarnings = new CLabelPadding(statusBar, borderFlag);
		warningIcon 	= imageLoader.getImage("image.sidebar.vitality.alert");
		warningGreyIcon = imageLoader.getImage("image.sidebar.vitality.alert-gray");
		infoIcon 		= imageLoader.getImage("image.sidebar.vitality.info");
		updateStatusWarnings( null, false );
		Messages.setLanguageText(statusWarnings,
				"MainWindow.status.warning.tooltip");
		alertHistoryListener = new AlertHistoryListener() {
			@Override
			public void alertHistoryAdded(LogAlert alert) {
				updateStatusWarnings(alert, true);
			}

			@Override
			public void alertHistoryRemoved(LogAlert alert) {
				updateStatusWarnings(alert, false);
			}
		};
		Alerts.addMessageHistoryListener(alertHistoryListener);
		statusWarnings.addMouseListener(new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				if ( e.button != 1 ){
					return;
				}
				if (SystemWarningWindow.numWarningWindowsOpen > 0) {
					return;
				}
				ArrayList<LogAlert> alerts = Alerts.getUnviewedLogAlerts();
				if (alerts.size() == 0) {
					return;
				}

				Shell shell = statusWarnings.getShell();
				Rectangle bounds = statusWarnings.getClientArea();
				Point ptBottomRight = statusWarnings.toDisplay(bounds.x + bounds.width, bounds.y);
				new SystemWarningWindow(alerts.get(0), ptBottomRight, shell, 0);
			}

			@Override
			public void mouseDown(MouseEvent e) {
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
			}
		});

		Menu menuStatusWarnings = new Menu(statusBar.getShell(), SWT.POP_UP);
		statusWarnings.setMenu(menuStatusWarnings);
		final MenuItem dismissAllItem = new MenuItem(menuStatusWarnings, SWT.PUSH);
		menuStatusWarnings.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event e) {
				dismissAllItem.setEnabled(Alerts.getUnviewedLogAlerts().size() > 0 );
			}
		});

		Messages.setLanguageText(dismissAllItem, "label.dismiss.all");
		dismissAllItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				ArrayList<LogAlert> alerts = Alerts.getUnviewedLogAlerts();

				for ( LogAlert a: alerts ){

					Alerts.markAlertAsViewed( a );
				}
			}
		});
		Utils.addAndFireParameterListener(mapConfigListeners, true,
				"status.rategraphs", new ParameterListener() {
					@Override
					public void parameterChanged(String parameterName) {
						boolean doRateGraphs = COConfigurationManager.getBooleanParameter("status.rategraphs");
						if (doRateGraphs) {
							if (imgRec == null || imgRec.isDisposed()) {
  							imgRec = new Image(display, 100, 20);
  							GC gc = new GC(imgRec);
  							gc.setBackground(statusDown.getBackground());
  							gc.fillRectangle(0, 0, 100, 20);
  							gc.dispose();
  							statusDown.setBackgroundImage(imgRec);
							}

							if (imgSent == null || imgSent.isDisposed()) {
  							imgSent = new Image(display, 100, 20);
  							GC gc = new GC(imgSent);
  							gc.setBackground(statusUp.getBackground());
  							gc.fillRectangle(0, 0, 100, 20);
  							gc.dispose();
  							statusUp.setBackgroundImage(imgSent);
							}
						} else {
							statusUp.setBackgroundImage(null);
							statusDown.setBackgroundImage(null);
							Utils.disposeSWTObjects(imgRec, imgSent);
							imgRec = imgSent = null;
						}
					}
				});

		/////////

		progressListener = new ProgressListener();
		PRManager.addListener(progressListener);

		uiFunctions.getUIUpdater().addUpdater(this);

		ArrayList<Runnable> list;
		this_mon.enter();
		try {
			list = listRunAfterInit;
			listRunAfterInit = null;
		} finally {
			this_mon.exit();
		}
		for (Runnable runnable : list) {
			try {
				runnable.run();
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		statusBar.layout(true);

		return statusBar;
	}

	private TimerEventPeriodic		alert_flasher_event;
	private long					alert_flasher_event_start_time;
	private boolean					alert_flash_activate;


	protected void updateStatusWarnings( final LogAlert current_alert, final boolean current_added ) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (statusWarnings == null || statusWarnings.isDisposed()) {
					return;
				}

				if ( current_alert != null && current_added && current_alert.isNative ){
					return;
				}
				
				ArrayList<LogAlert> alerts = Alerts.getUnviewedLogAlerts();
				int count = alerts.size();

				Image icon = infoIcon;

				for ( LogAlert alert: alerts ){
					int type = alert.getType();

					if ( type == LogAlert.LT_ERROR || type == LogAlert.LT_WARNING ){

						icon = warningIcon;

						break;
					}
				}

				if ( statusWarnings.getImage() != icon ){
					statusWarnings.setImage( icon );
				}

				statusWarnings.setVisible(count > 0);
				statusWarnings.setText("" + count);
				statusWarnings.layoutNow();

				if ( current_added ){

					alert_flash_activate = true;

					if ( current_alert.getType() != LogAlert.LT_INFORMATION ){

						alert_flasher_event_start_time = SystemTime.getMonotonousTime();

						if ( alert_flasher_event == null ){

							alert_flasher_event =
								SimpleTimer.addPeriodicEvent(
									"MSB:alertFlasher",
									500,
									new TimerEventPerformer()
									{
										private long	last_tick_time = -1;

										@Override
										public void
										perform(
												final TimerEvent event )
										{
											if (Utils.isDisplayDisposed()) {
												event.cancel();
												return;
											}
											boolean ok = Utils.execSWTThread(
												new AERunnable()
												{
													@Override
													public void
													runSupport()
													{
														if (Utils.isDisplayDisposed()) {
															event.cancel();
															return;
														}
														long now = SystemTime.getMonotonousTime();

															// during init timing can go a bit askew, try
															// and prevent too-quick transitions

														if ( 	last_tick_time != -1 &&
																now - last_tick_time < 400 ){

															return;
														}

														last_tick_time = now;

															// all logic is single threaded via SWT thread...

														if (	statusWarnings == null ||
																statusWarnings.isDisposed() ||
																alert_flasher_event == null ||
																!alert_flash_activate ){

															if ( alert_flasher_event != null ){

																alert_flasher_event.cancel();

																alert_flasher_event = null;
															}

															return;
														}

														Image current_icon = statusWarnings.getImage();

														if ( 	now > alert_flasher_event_start_time + 15*1000 &&
																current_icon == warningIcon ){

															alert_flasher_event.cancel();

															alert_flasher_event = null;

															return;
														}

														Image target_icon = current_icon == warningIcon?warningGreyIcon:warningIcon;

														statusWarnings.setImage( target_icon );
													}
												});

											if (!ok) {
												event.cancel();
											}
										}
									});
						}
					}
				}else{

					alert_flash_activate = false;
				}
			}
		});
	}

	public void
	relayout()
	{
		parent.layout( true, true );
	}

	private void addFeedBack() {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						_addFeedBack();
					}
				});
			}
		});
	}

	private void _addFeedBack() {
		/*
		 * Feedback
		 *
		 */

		// only show after restart after 15 mins uptime
		OverallStats stats = StatsFactory.getStats();

		long secs_uptime = stats.getTotalUpTime();

		long last_uptime = COConfigurationManager.getLongParameter(
				"statusbar.feedback.uptime", 0);

		if (last_uptime == 0) {

			COConfigurationManager.setParameter("statusbar.feedback.uptime",
					secs_uptime);

		} else if (secs_uptime - last_uptime > 15 * 60) {

			createStatusEntry(new CLabelUpdater() {
				@Override
				public boolean update(CLabelPadding label) {
					return( false );
				}

				@Override
				public void created(CLabelPadding feedback) {
					feedback.setText(MessageText.getString("statusbar.feedback"));

					Listener feedback_listener = new Listener() {
						@Override
						public void handleEvent(Event e) {

							String url = "feedback.start?" + Utils.getWidgetBGColorURLParam()
							+ "&fromWeb=false&os.name=" + UrlUtils.encode(Constants.OSName)
							+ "&os.version="
							+ UrlUtils.encode(Constants.OSVersion)
							+ "&java.version=" + UrlUtils.encode(Constants.JAVA_VERSION);

							// Utils.launch( url );

							UIFunctionsManagerSWT.getUIFunctionsSWT().viewURL(url, null, 600,
									520, true, false);
						}
					};

					Utils.setTT(feedback,MessageText.getString("statusbar.feedback.tooltip"));
					feedback.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
					Utils.setLinkForeground(feedback);
					feedback.addListener(SWT.MouseUp, feedback_listener);
					feedback.addListener(SWT.MouseDoubleClick, feedback_listener);

					feedback.setVisible(true);
				}
			});

		}
	}

	/*
	private void addRIP() {

		if ( !COConfigurationManager.getBooleanParameter( "Status Area Show RIP" )){

			return;
		}

		createStatusEntry(new CLabelUpdater() {
			@Override
			public boolean update(CLabelPadding label) {
				return( false );
			}

			@Override
			public void created(final CLabelPadding feedback) {
				feedback.setText(MessageText.getString("respect.ip"));

				final String	url_str = MessageText.getString( "respect.ip.url" );

				Listener feedback_listener = new Listener() {
					@Override
					public void handleEvent(Event e) {

						if ( e.type == SWT.MouseUp && e.button != 1 ){
							return;
						}

						try{
							Utils.launch( new URL( url_str ));

						}catch( Throwable f ){

							Debug.out( f );
						}
					}
				};

				feedback.setData( url_str );

				Menu menu = new Menu(feedback.getShell(),SWT.POP_UP);

				feedback.setMenu( menu );

				MenuItem   item = new MenuItem( menu, SWT.NONE );

				item.setText( MessageText.getString( "sharing.progress.hide" ));

				item.addSelectionListener(
					new SelectionAdapter()
					{
						@Override
						public void
						widgetSelected(
							SelectionEvent arg0 )
						{
							COConfigurationManager.setParameter( "Status Area Show RIP", false );

							feedback.setVisible( false );

							layoutPluginComposite();
						}
					});

				//new MenuItem( menu, SWT.SEPARATOR );

				ClipboardCopy.addCopyToClipMenu( menu, url_str );

				Utils.setTT( feedback, url_str );

				feedback.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
				feedback.setForeground(Colors.blue);

				feedback.addListener(SWT.MouseUp, feedback_listener);
				feedback.addListener(SWT.MouseDoubleClick, feedback_listener);

				feedback.setVisible(true);
			}
		});
	}
	*/
	
	/**
	 * @param cSB
	 *
	 * @since 4.0.0.1
	 */
	private void addStatusBarMenu(Composite cSB) {

		Menu menu = new Menu(cSB);
		cSB.setMenu(menu);

		MenuItem itemShow = new MenuItem(menu, SWT.CASCADE);
		itemShow.setText("Show");
		Menu menuShow = new Menu(itemShow);
		itemShow.setMenu(menuShow);

		final String[] statusAreaLangs = {
			"ConfigView.section.style.status.show_sr",
			"ConfigView.section.style.status.show_nat",
			"ConfigView.section.style.status.show_ddb",
			"ConfigView.section.style.status.show_ipf",
		};
		final String[] statusAreaConfig = {
			"Status Area Show SR",
			"Status Area Show NAT",
			"Status Area Show DDB",
			"Status Area Show IPF",
		};

		for (int i = 0; i < statusAreaConfig.length; i++) {
			final String configID = statusAreaConfig[i];
			String langID = statusAreaLangs[i];

			final MenuItem item = new MenuItem(menuShow, SWT.CHECK);
			Messages.setLanguageText(item, langID);
			item.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					COConfigurationManager.setParameter(configID,
							!COConfigurationManager.getBooleanParameter(configID));
				}
			});
			menuShow.addListener(SWT.Show, new Listener() {
				@Override
				public void handleEvent(Event event) {
					item.setSelection(COConfigurationManager.getBooleanParameter(configID));
				}
			});
		}

	}

	/**
	 *
	 * @param key
	 */
	@Override
	public void setStatusText(String key) {
		this.statusTextKey = key == null ? "" : key;
		setStatusImageKey(null);
		this.clickListener = null;
		if (statusTextKey.length() == 0) { // reset
			resetStatus();
		}

		updateStatusText();
	}

	private void setStatusImageKey(String newStatusImageKey) {
		if (("" + statusImageKey).equals("" + newStatusImageKey)) {
			return;
		}
		ImageLoader imageLoader = ImageLoader.getInstance();
		if (statusImageKey != null) {
			imageLoader.releaseImage(statusImageKey);
		}
		statusImageKey = newStatusImageKey;
		if (statusImageKey != null) {
			statusImage = imageLoader.getImage(statusImageKey);
		} else {
			statusImage = null;
		}
	}

	private void resetStatus() {
		if (Constants.isCVSVersion()) {
			statusTextKey = "!{MainWindow.status.unofficialversion} ("
					+ Constants.BIGLYBT_VERSION + ")!";
			setStatusImageKey(STATUS_ICON_WARN);
		} else if (!Constants.isOSX && COConfigurationManager.getStringParameter("ui").equals("az2")) { //don't show official version numbers for OSX L&F
			statusTextKey = "!" + Constants.APP_NAME + " " + Constants.BIGLYBT_VERSION + "!";
			setStatusImageKey(null);
		}

	}

	/**
	 * @param statustype
	 * @param string
	 * @param l
	 */
	@Override
	public void setStatusText(int statustype, String string,
	                          UIStatusTextClickListener l) {
		this.statusTextKey = string == null ? "" : string;

		if (statusTextKey.length() == 0) { // reset
			resetStatus();
		}

		this.clickListener = l;
		if (statustype == UIFunctions.STATUSICON_WARNING) {
			setStatusImageKey(STATUS_ICON_WARN);
		}
		if (statustype == UIFunctions.STATUSICON_WARNING) {
			setStatusImageKey(STATUS_ICON_WARN);
		} else {
			setStatusImageKey(null);
		}

		updateStatusText();
	}

	/**
	 *
	 *
	 */
	public void updateStatusText() {
		if (display == null || display.isDisposed())
			return;
		final String text;
		if (updateWindow != null) {
			text = "MainWindow.updateavail";
		} else {
			text = this.statusTextKey;
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (statusText != null && !statusText.isDisposed()) {
					statusText.setText(MessageText.getString(text));
					statusText.setImage(statusImage);
				}
			}
		});
	}

	/**
	 *
	 *
	 */
	public void refreshStatusText() {
		if (statusText != null && !statusText.isDisposed())
			statusText.update();
	}

	/**
	 *
	 * @param updateWindow
	 */
	@Override
	public void setUpdateNeeded(UpdateWindow updateWindow) {
		this.updateWindow = updateWindow;
		if (updateWindow != null) {
			statusText.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
			statusText.setForeground(Colors.colorWarning);
			updateStatusText();
		} else {
			statusText.setCursor(null);
			statusText.setForeground(null);
			updateStatusText();
		}
	}

	// @see UIUpdatable#updateUI()

	boolean was_hidden = false;

	@Override
	public void updateUI(){
		updateUI(true);
	}

	@Override
	public void updateUI(boolean is_visible ) {
		if (statusBar.isDisposed()) {
			return;
		}

			// see if this fixes occasional issue with status bar vanishing when bringing back from taskbar/tray

		boolean is_hidden = (!is_visible) || statusBar.getDisplay().getFocusControl() == null;

		if ( is_hidden ){

			was_hidden = true;

		}else{

			if ( was_hidden ){

				statusBar.layout( true, true );

				was_hidden = false;
			}
		}

		if ( !is_visible ){

			return;
		}

		// Plugins.
		Control[] plugin_elements = this.plugin_label_composite.getChildren();
		for (int i = 0; i < plugin_elements.length; i++) {
			if (plugin_elements[i] instanceof UpdateableCLabel) {
				((UpdateableCLabel) plugin_elements[i]).checkForRefresh();
			}
		}

		if (ipBlocked.isVisible()) {
			updateIPBlocked();
		}

		if (srStatus.isVisible()) {
			updateShareRatioStatus();
		}


		if (natStatus.isVisible()) {
			updateNatStatus();
		}

		if (dhtStatus.isVisible()) {
			updateDHTStatus();
		}


		// UL/DL Status Sections
		if (CoreFactory.isCoreRunning()) {
			Core core = CoreFactory.getSingleton();
			GlobalManager gm = core.getGlobalManager();
			GlobalManagerStats stats = gm.getStats();

			int kinb = DisplayFormatters.getKinB();
			
			int dl_limit = NetworkManager.getMaxDownloadRateBPS() / kinb;
			long rec_data = stats.getDataReceiveRate();
			long rec_prot = stats.getProtocolReceiveRate();

			if (last_dl_limit != dl_limit || last_rec_data != rec_data || last_rec_prot != rec_prot) {
				last_dl_limit = dl_limit;
				last_rec_data = rec_data;
				last_rec_prot = rec_prot;

				statusDown.setText((dl_limit == 0 ? "" : "[" + dl_limit + "K] ")
						+ DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec(rec_data, rec_prot));
			}

			boolean auto_up = TransferSpeedValidator.isAutoSpeedActive(gm)
					&& TransferSpeedValidator.isAutoUploadAvailable(core);

			int ul_limit_norm = NetworkManager.getMaxUploadRateBPSNormal() / kinb;

			String seeding_only;
			if (NetworkManager.isSeedingOnlyUploadRate()) {
				int ul_limit_seed = NetworkManager.getMaxUploadRateBPSSeedingOnly() / kinb;
				if (ul_limit_seed == 0) {
					seeding_only = "+" + Constants.INFINITY_STRING + "K";
				} else {
					int diff = ul_limit_seed - ul_limit_norm;
					seeding_only = (diff >= 0 ? "+" : "") + diff + "K";
				}
			} else {
				seeding_only = "";
			}

			int sent_data = stats.getDataSendRate();
			if (imgRec != null && !imgRec.isDisposed()) {
				updateGraph(statusDown, imgRec, rec_data, max_rec);
				updateGraph(statusUp, imgSent, sent_data, max_sent);
			}


			statusUp.setText((ul_limit_norm == 0 ? "" : "[" + ul_limit_norm + "K"
					+ seeding_only + "]")
					+ (auto_up ? "* " : " ")
					+ DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec(
							sent_data, stats.getProtocolSendRate()));
		}
	}

	private void updateGraph(CLabelPadding label, Image img,
			long newVal, long[] max) {
		GC gc = new GC(img);
		try {
  		long val = newVal;
  		Rectangle bounds = img.getBounds();
  		final int padding = 2;
  		int x = bounds.width - padding - padding;
  		if (val > max[0]) {
  			int y = 20 - (int) (max[0] * 20 / val);
  			gc.setBackground(label.getBackground());
  			gc.fillRectangle(padding, 0, x, y);
  			// gc.drawImage(imgRec, 1, 0, x, 20, 0, y, x, 20 - y);
  			gc.copyArea(padding + 1, 0, x, 20, padding, y);
  			max[0] = val;
  		} else {
  			gc.copyArea(padding + 1, 0, x, 20, padding, 0);
  			// gc.drawImage(imgRec, 1, 0, x, 20, 0, 0, x, 20);
  		}
  		gc.setForeground(label.getBackground());
  		int breakPoint = 20 - (max[0] == 0 ? 0
  				: (int) (val * 20 / max[0]));
  		gc.drawLine(x, 0, x, breakPoint);
  		gc.setForeground(Colors.blues[5]);
  		gc.drawLine(x, breakPoint, x, 20);
		} finally {
  		gc.dispose();
		}
		label.redraw();
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private void updateDHTStatus() {
		if (dhtPlugin == null) {
			return;
		}
		// DHT Status Section
		int dht_status = dhtPlugin.getStatus();
		long dht_count = -1;
		//boolean	reachable = false;
		if (dht_status == DHTPlugin.STATUS_RUNNING) {
			DHT[] dhts = dhtPlugin.getDHTs();

			//reachable = dhts.length > 0 && dhts[0].getTransport().isReachable();

			//if ( reachable ){
			dht_count = dhts[0].getControl().getStats().getEstimatedDHTSize();
			//}
		}

		if (lastDHTstatus != dht_status || lastDHTcount != dht_count) {
			boolean hasImage = dhtStatus.getImage() != null;
			boolean needImage = true;
			switch (dht_status) {
				case DHTPlugin.STATUS_RUNNING:

					Utils.setTT(dhtStatus,MessageText.getString("MainWindow.dht.status.tooltip"));
					dhtStatus.setText(MessageText.getString("MainWindow.dht.status.users").replaceAll(
							"%1", numberFormat.format(dht_count)));

					/*
					if ( reachable ){
						dhtStatus.setImage(ImageRepository.getImage("greenled"));
						Utils.setTT(dhtStatus.MessageText
								.getString("MainWindow.dht.status.tooltip"));
						dhtStatus.setText(MessageText.getString("MainWindow.dht.status.users").replaceAll("%1", numberFormat.format(dht_count)));
					} else {
						dhtStatus.setImage(ImageRepository.getImage("yellowled"));
						Utils.setTT( dhtStatus,MessageText
								.getString("MainWindow.dht.status.unreachabletooltip"));
						dhtStatus.setText(MessageText
								.getString("MainWindow.dht.status.unreachable"));
					}
					*/
					break;

				case DHTPlugin.STATUS_DISABLED:
					//dhtStatus.setImage(ImageRepository.getImage("grayled"));
					dhtStatus.setText(MessageText.getString("MainWindow.dht.status.disabled"));
					break;

				case DHTPlugin.STATUS_INITALISING:
					//dhtStatus.setImage(ImageRepository.getImage("yellowled"));
					dhtStatus.setText(MessageText.getString("MainWindow.dht.status.initializing"));
					break;

				case DHTPlugin.STATUS_FAILED:
					//dhtStatus.setImage(ImageRepository.getImage("redled"));
					dhtStatus.setText(MessageText.getString("MainWindow.dht.status.failed"));
					break;

				default:
					needImage = false;
					break;
			}

			if (hasImage != needImage) {
				ImageLoader imageLoader = ImageLoader.getInstance();
				if (needImage) {
					Image img = imageLoader.getImage("sb_count");
					dhtStatus.setImage(img);
				} else {
					imageLoader.releaseImage("sb_count");
					dhtStatus.setImage(null);
				}
			}
			lastDHTstatus = dht_status;
			lastDHTcount = dht_count;
		}
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private void updateNatStatus() {
		// NAT status Section
		if (connection_manager == null) {
			return;
		}

		Object[] o_status = connection_manager.getNATStatusEx();

		int nat_status 	= (Integer)o_status[0];
		String nat_info = (String)o_status[1];
		
		if ( nat_info == null ){
			nat_info = "";
		}
		
		if (lastNATstatus != nat_status || !lastNATInfo.equals( nat_info )) {
			String imgID;
			String tooltipID;
			String statusID;

			switch (nat_status) {
				case ConnectionManager.NAT_UNKNOWN:
					imgID = "grayled";
					tooltipID = "MainWindow.nat.status.tooltip.unknown";
					statusID = "MainWindow.nat.status.unknown";
					break;

				case ConnectionManager.NAT_OK:
					imgID = "greenled";
					tooltipID = "MainWindow.nat.status.tooltip.ok";
					statusID = "MainWindow.nat.status.ok";
					break;

				case ConnectionManager.NAT_PROBABLY_OK:
					imgID = "yellowled";
					tooltipID = "MainWindow.nat.status.tooltip.probok";
					statusID = "MainWindow.nat.status.probok";
					break;

				default:
					imgID = "redled";
					tooltipID = "MainWindow.nat.status.tooltip.bad";
					statusID = "MainWindow.nat.status.bad";
					break;
			}

			if (!imgID.equals(lastNATimageID)) {
				ImageLoader imageLoader = ImageLoader.getInstance();
				natStatus.setImage(imageLoader.getImage(imgID));

				if (lastNATimageID != null) {
					imageLoader.releaseImage(lastNATimageID);
				}
				lastNATimageID = imgID;
			}

			String tt = MessageText.getString(tooltipID);
			
			tt= tt.replaceAll( " \\(TCP\\)", "" );
			
			if ( !nat_info.isEmpty() ){
				tt += "\n" + nat_info;
			}
			Utils.setTT(natStatus, tt );
			lastNATInfo		= nat_info;
			
			natStatus.setText(MessageText.getString(statusID));
			
			lastNATstatus 	= nat_status;
			
		}
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private void updateShareRatioStatus() {
		// SR status section

		if (overall_stats == null) {
			overall_stats = StatsFactory.getStats();

			if (overall_stats == null) {
				return;
			}
		}

		long ratio = (1000 * overall_stats.getUploadedBytes() / (overall_stats.getDownloadedBytes() + 1));

		int sr_status;

		if (ratio < 500) {

			sr_status = 0;

		} else if (ratio < 900) {

			sr_status = 1;

		} else {

			sr_status = 2;
		}

		if (sr_status != last_sr_status) {

			String imgID;

			switch (sr_status) {
				case 2:
					imgID = "greenled";
					break;

				case 1:
					imgID = "yellowled";
					break;

				default:
					imgID = "redled";
					break;
			}

			if (!imgID.equals(lastSRimageID)) {
				ImageLoader imageLoader = ImageLoader.getInstance();
				srStatus.setImage(imageLoader.getImage(imgID));
				if (lastSRimageID != null) {
					imageLoader.releaseImage(lastSRimageID);
				}
				lastSRimageID  = imgID;
			}

			last_sr_status = sr_status;
		}

		if (ratio != last_sr_ratio) {

			String tooltipID;

			switch (sr_status) {
				case 2:
					tooltipID = "MainWindow.sr.status.tooltip.ok";
					break;

				case 1:
					tooltipID = "MainWindow.sr.status.tooltip.poor";
					break;

				default:
					tooltipID = "MainWindow.sr.status.tooltip.bad";
					break;
			}

			String ratio_str = "";

			String partial = "" + ratio % 1000;

			while (partial.length() < 3) {

				partial = "0" + partial;
			}

			ratio_str = (ratio / 1000) + "." + partial;

			Utils.setTT( srStatus,MessageText.getString(tooltipID, new String[] {
				ratio_str
			}));

			last_sr_ratio = ratio;
		}
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private void updateIPBlocked() {
		if (!CoreFactory.isCoreRunning()) {
			return;
		}

		Core core = CoreFactory.getSingleton();

		// IP Filter Status Section
		IpFilter ip_filter = core.getIpFilterManager().getIPFilter();

		Color fg = Colors.getSystemColor( ipBlocked.getDisplay(), ip_filter.isEnabled()? SWT.COLOR_WIDGET_FOREGROUND : SWT.COLOR_WIDGET_NORMAL_SHADOW);
		
		ipBlocked.setForeground( fg );

		ipBlocked.setText("IPs: "
				+ numberFormat.format(ip_filter.getNbRanges())
				+ " - "
				+ numberFormat.format(ip_filter.getNbIpsBlockedAndLoggable())
				+ "/"
				+ numberFormat.format(ip_filter.getNbBannedIps())
				+ "/"
				+ numberFormat.format(core.getIpFilterManager().getBadIps().getNbBadIps()));

		Utils.setTT(ipBlocked,MessageText.getString("MainWindow.IPs.tooltip",
				new String[] {
					ip_filter.isEnabled()?
					DisplayFormatters.formatDateShort(ip_filter.getLastUpdateTime()):MessageText.getString( "ipfilter.disabled" )
				}));
	}

	/**
	 * @param string
	 */
	@Override
	public void setDebugInfo(String string) {
		if (statusText != null && !statusText.isDisposed())
			Utils.setTT(statusText,string);
	}

	@Override
	public boolean isMouseOver() {
		if (statusText == null || statusText.isDisposed()) {
			return false;
		}
		return statusText.getDisplay().getCursorControl() == statusText;
	}

	/**
	 * CLabel that shrinks to fit text after a specific period of time.
	 * Makes textual changes less jumpy
	 *
	 * @author TuxPaper
	 * @created Mar 21, 2006
	 *
	 */
	public class CLabelPadding
		extends Canvas implements PaintListener
	{
		private int lastWidth = 0;

		private long widthSetOn = 0;

		private static final int KEEPWIDTHFOR_MS = 30 * 1000;

		private String 	text = "";
		private String	tooltip_text;

		private boolean	hovering;

		private Image image;

		private Image bgImage;

		/**
		 * Default Constructor
		 *
		 * @param parent
		 * @param style
		 */
		public CLabelPadding(Composite parent, int style) {
			super(parent, style | SWT.DOUBLE_BUFFERED);

			GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_CENTER
					| GridData.VERTICAL_ALIGN_FILL);
			setLayoutData(gridData);
			setForeground(parent.getForeground());

			addPaintListener(this);

			addMouseTrackListener(
				new MouseTrackAdapter()
				{
					@Override
					public void mouseEnter(MouseEvent e) {
						hovering = true;
					}

					@Override
					public void mouseExit(MouseEvent e) {
						hovering = false;
					}
					@Override
					public void mouseHover(MouseEvent e) {
						String existing = CLabelPadding.super.getToolTipText();
						if ( existing == null || !existing.equals( tooltip_text )){
							if ( Utils.getTTEnabled()){
								CLabelPadding.super.setToolTipText( tooltip_text );
							}
						}
					}
				});
		}

		@Override
		public void
		setToolTipText(
			String str )
		{
			if ( str == tooltip_text ){
				return;
			}
			if ( str != null && tooltip_text != null && str.equals( tooltip_text )){
				return;
			}

			tooltip_text = str;

			if ( hovering ){

				if ( Utils.getTTEnabled()){
					super.setToolTipText( str );
				}
			}
		}

		@Override
		public String
		getToolTipText()
		{
			return( tooltip_text );
		}

		@Override
		public void paintControl(PaintEvent e) {
			Point size = getSize();
			e.gc.setAdvanced(true);
			if (bgImage != null && !bgImage.isDisposed()) {
				Rectangle bounds = bgImage.getBounds();
				if (display.getCursorControl() != this) {
					e.gc.setAlpha(100);
				}
				e.gc.drawImage(bgImage, 0, 0, bounds.width, bounds.height, 0, 2,
						size.x, size.y - 4);
				e.gc.setAlpha(255);
			}
			Rectangle clientArea = getClientArea();


			Image image = getImage();
			Rectangle imageBounds = null;
			if (image != null && !image.isDisposed()) {
				imageBounds = image.getBounds();
			}
			GCStringPrinter sp = new GCStringPrinter(e.gc, getText(), clientArea,
					true, true, SWT.CENTER);
			sp.calculateMetrics();

			if (sp.hasHitUrl()) {
				URLInfo[] hitUrlInfo = sp.getHitUrlInfo();
				for (int i = 0; i < hitUrlInfo.length; i++) {
					URLInfo info = hitUrlInfo[i];
					info.urlUnderline = true;
				}
			}

			Point textSize = sp.getCalculatedSize();

			if (imageBounds != null) {
				int pad = 2;
				int ofs = imageBounds.width + imageBounds.x;
				int xStartImage = (clientArea.width - textSize.x - ofs - pad) / 2;
				e.gc.drawImage(image, xStartImage,
						(clientArea.height / 2) - (imageBounds.height / 2));
				clientArea.x += xStartImage + ofs + pad;
				clientArea.width -= xStartImage + ofs + pad;
			} else {
				int ofs = (clientArea.width / 2) - (textSize.x / 2);
				clientArea.x += ofs;
				clientArea.width -= ofs;
			}
			sp.printString(e.gc, clientArea, SWT.LEFT);

			int x = clientArea.x + clientArea.width - 1;
			e.gc.setAlpha(20);
			e.gc.drawLine(x, 3, x, clientArea.height - 3);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.swt.custom.CLabel#computeSize(int, int, boolean)
		 */
		@Override
		public Point computeSize(int wHint, int hHint) {
			return computeSize(wHint, hHint, true);
		}

		@Override
		public Point computeSize(int wHint, int hHint, boolean changed) {
			try {
				Point pt = computeSize(wHint, hHint, changed, false);

				return pt;
			} catch (Throwable t) {
				Debug.out("Error while computing size for CLabel with text:"
						+ getText() + "; " + t.toString());
				return new Point(0, 0);
			}
		}

		// @see org.eclipse.swt.widgets.Control#computeSize(int, int)
		public Point computeSize(int wHint, int hHint, boolean changed, boolean realWidth) {
			if (!isVisible()) {
				return (new Point(0, 0));
			}

			if (wHint != SWT.DEFAULT && hHint != SWT.DEFAULT) {
				return new Point(wHint, hHint);
			}
			Point pt = new Point(wHint, hHint);

			Point lastSize = new Point(0, 0);

			Image image = getImage();
			if (image != null && !image.isDisposed()) {
				Rectangle bounds = image.getBounds();
				int ofs = bounds.width + bounds.x + 5;
				lastSize.x += ofs;
				lastSize.y = bounds.height;
			}

			GC gc = new GC(this);
			GCStringPrinter sp = new GCStringPrinter(gc, getText(), new Rectangle(0,
					0, 10000, 20), true, true, SWT.LEFT);
			sp.calculateMetrics();
			Point lastTextSize = sp.getCalculatedSize();
			gc.dispose();

			lastSize.x += lastTextSize.x + 10;
			lastSize.y = Math.max(lastSize.y, lastTextSize.y);

			if (wHint == SWT.DEFAULT) {
				pt.x = lastSize.x;
			}
			if (hHint == SWT.DEFAULT) {
				pt.y = lastSize.y;
			}

			if (!realWidth) {
	  			long now = System.currentTimeMillis();
	  			if (lastWidth > pt.x && now - widthSetOn < KEEPWIDTHFOR_MS) {
	  				pt.x = lastWidth;
	  			} else {
	  				if (lastWidth != pt.x) {
	  					lastWidth = pt.x;
	  				}
	  				widthSetOn = now;
	  			}
			}

			return pt;
		}


		public void setImage(Image image) {
			this.image = image;

			redraw();
		}

		public Image getImage() {
			return image;
		}

		@Override
		public void setBackgroundImage(Image image) {
			bgImage = image;

			redraw();
		}

		@Override
		public Image getBackgroundImage() {
			return bgImage;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			if (text == null) {
				text = "";
			}
			if (text.equals(getText())) {
				return;
			}
			this.text = text;
			int oldWidth = lastWidth;
			Point pt = computeSize(SWT.DEFAULT, SWT.DEFAULT, true, true);
			if (pt.x > oldWidth && text.length() > 0) {
				statusBar.layout();
			} else if (pt.x < oldWidth) {
				Utils.execSWTThreadLater(KEEPWIDTHFOR_MS, new AERunnable() {
					@Override
					public void runSupport() {
						if (statusBar == null || statusBar.isDisposed()) {
							return;
						}
						statusBar.layout();
					}
				});
			}
			redraw();
		}

		public void
		reset()
		{
			widthSetOn 	= 0;
			lastWidth	= 0;
		}

		public void layoutNow() {
			widthSetOn = 0;
			statusBar.layout();
		}

	}

	private class UpdateableCLabel
		extends CLabelPadding
	{

		private CLabelUpdater updater;

		public UpdateableCLabel(Composite parent, int style, CLabelUpdater updater) {
			super(parent, style);
			this.updater = updater;
		}

		private void checkForRefresh() {
			if ( updater.update(this)){
				layoutPluginComposite();
			}
		}
	}

	@Override
	public void createStatusEntry(final CLabelUpdater updater) {
		AERunnable r = new AERunnable() {
			@Override
			public void runSupport() {
				if (plugin_label_composite.isDisposed()) {
					return;
				}
				UpdateableCLabel result = new UpdateableCLabel(plugin_label_composite, borderFlag,
						updater);
				result.setLayoutData(new GridData(GridData.FILL_BOTH));
				layoutPluginComposite();
				updater.created(result);
			}
		};
		this_mon.enter();
		try {
			if (listRunAfterInit != null) {
				listRunAfterInit.add(r);
				return;
			}
		} finally {
			this_mon.exit();
		}

		Utils.execSWTThread(r);
	}

	private void
	layoutPluginComposite()
	{
		Control[] plugin_elements = this.plugin_label_composite.getChildren();
		for (int i = 0; i < plugin_elements.length; i++) {
			if (plugin_elements[i] instanceof UpdateableCLabel) {
				((UpdateableCLabel) plugin_elements[i]).reset();
			}
		}
		statusBar.layout();
	}
	// =============================================================
	// Below code are ProgressBar/Status text specific
	// =============================================================
	/**
	 * Show or hide the Progress Bar
	 * @param state
	 */
	private void showProgressBar(boolean state) {
		/*
		 * We show/hide the progress bar simply by setting the .widthHint and letting the statusBar handle the layout
		 */
		if (state && !progressBar.isVisible()) {
			progressGridData.widthHint = 100;
			progressBar.setVisible(true);
			statusBar.layout();
		} else if (!state && progressBar.isVisible()) {
			progressBar.setVisible(false);
			progressGridData.widthHint = 0;
			setStatusText( "" );
			statusBar.layout();
		}
	}


	public Rectangle getBounds() {
		if (null != statusBar) {
			return statusBar.getBounds();
		}
		return null;
	}

	// @see UIUpdatable#getUpdateUIName()
	@Override
	public String getUpdateUIName() {
		return ID;
	}

	/**
	 * Updates the display of the ProgressBar and/or the status text
	 * @param pReport the <code>ProgressReport</code> containing the information
	 * to display; can be <code>null</code> in which case the status text and progress bar will be reset to default states
	 */






	/**
	 * A listener that listens to any changes notified from the <code>ProgressReportingManager</code> and
	 * accordingly update the progress bar and/or the status text area.
	 * @author knguyen
	 *
	 */
	private class ProgressListener
		implements IProgressReportingListener
	{
		private String lastProgressImageID = null;

		private Set<IProgressReporter>	pending_updates = new HashSet<>();

		private
		ProgressListener()
		{
			Utils.execSWTThread(
				new Runnable()
				{
					@Override
					public void
					run()
					{
						swt_setProgressImage();
					}
				});
		}

		private void
		swt_updateProgressBarDisplay(
			IProgressReport pReport)
		{
			if (null == progressBar || progressBar.isDisposed()){

				return;
			}


			if (null != pReport) {
				/*
				 * Pass the values through to the progressbar
				 */
				progressBar.setMinimum(pReport.getMinimum());
				progressBar.setMaximum(pReport.getMaximum());
				progressBar.setIndeterminate(pReport.isIndeterminate());
				progressBar.setPercentage(pReport.getPercentage());
				showProgressBar(true);

				/*
				 * Update status text
				 */
				if (false) {
					statusText.setText(pReport.getName());
				} else {
					setStatusText("!" + pReport.getName() + "!");
				}
			}

			else {
				/*
				 * Since the pReport is null then reset progress display appropriately
				 */
				showProgressBar(false);

				if (false) {
					statusText.setText("");
				} else {
					setStatusText(null);
				}
			}
		}

		private void swt_setProgressImage() {

			if (progressViewerImageLabel.isDisposed()) {
				return;
			}

			String imageID;

			if (PRManager.getReporterCount(ProgressReportingManager.COUNT_ERROR) > 0) {
				imageID = "progress_error";
			} else if (PRManager.getReporterCount(ProgressReportingManager.COUNT_ALL) > 0) {
				imageID = "progress_info";
			} else {
				imageID = "progress_viewer";
			}

			if (!imageID.equals(lastProgressImageID)) {

				ImageLoader imageLoader = ImageLoader.getInstance();
				progressViewerImageLabel.setImage(imageLoader.getImage(imageID));
				if (lastProgressImageID != null) {
					imageLoader.releaseImage(lastProgressImageID);
				}
				lastProgressImageID  = imageID;
			}
		}

		@Override
		public int
		reporting(
			final int 					eventType,
			final IProgressReporter 	reporter)
		{
			if ( eventType == MANAGER_EVENT_UPDATED ){

					// reduce pointless refreshes due to multiple update events

				synchronized( pending_updates ){

					if ( pending_updates.contains( reporter )){

						return( RETVAL_OK );
					}

					pending_updates.add( reporter );
				}
			}

			Utils.execSWTThread(
				new Runnable()
				{
					@Override
					public void
					run()
					{
						swt_reporting( eventType, reporter );
					}});

			return RETVAL_OK;
		}

		private int
		swt_reporting(
			int 				eventType,
			IProgressReporter 	reporter)
		{
			synchronized( pending_updates ){

					// always remove so that the removal event also cleans up

				pending_updates.remove( reporter );
			}

			/*
			 * Show the appropriate image based on the content of the reporting manager
			 */
			swt_setProgressImage();

			if (null == reporter) {
				return RETVAL_OK;
			}

			if (MANAGER_EVENT_REMOVED == eventType) {
				swt_updateFromPrevious();
			} else if (MANAGER_EVENT_ADDED == eventType
					|| MANAGER_EVENT_UPDATED == eventType) {
				/*
				 * Get a ProgressReport to ensure all data is consistent
				 */
				IProgressReport pReport = reporter.getProgressReport();

				/*
				 * Pops up the ProgressReportingWindow to show this report if it is an error report;
				 * this is to help catch the users attention
				 */
				if (pReport.isInErrorState()) {

					if("reporterType_updater".equals(pReport.getReporterType())){
						/*
						 * Suppressing the pop-up for update-related errors
						 */
						return RETVAL_OK;
					}

					final IProgressReporter final_reporter = reporter;


					/*
					 * The new window is opened only if there is not one already showing the same reporter
					 */
					
					if ( !COConfigurationManager.getBooleanParameter( "suppress_file_download_dialog" )){
						if (!ProgressReporterWindow.isOpened(final_reporter)) {
							if ( !ProgressReporterWindow.isOpened(final_reporter)){
										ProgressReporterWindow.open(final_reporter,
												ProgressReporterWindow.NONE);
							}
						}
					}
				}

				/*
				 * If this reporter is not active then get the previous reporter that is still active and display info from that
				 */
				if (!pReport.isActive()) {
					swt_updateFromPrevious();
				} else {
					swt_update(pReport);
				}
			}

			return RETVAL_OK;
		}

		private void swt_update(final IProgressReport pReport) {

			if (null == pReport) {
				swt_updateProgressBarDisplay(null);
				return;
			}

			/*
			 * If there is at least 2 reporters still active then show the progress bar as indeterminate
			 * and display the text from the current reporter
			 */
			if (PRManager.hasMultipleActive()) {

				setStatusText("!" + pReport.getName() + "!");
				progressBar.setIndeterminate(true);
				showProgressBar(true);

			} else {
				swt_updateProgressBarDisplay(pReport);
			}
		}

		private void swt_updateFromPrevious() {
			/*
			 * Get the previous reporter that is still active
			 */
			IProgressReporter previousReporter = PRManager.getNextActiveReporter();

			/*
			 * If null then we reset the status text and the progress bar
			 */
			if (null != previousReporter) {
				swt_update(previousReporter.getProgressReport());
			} else {
				swt_update(null);
			}
		}
	}
}