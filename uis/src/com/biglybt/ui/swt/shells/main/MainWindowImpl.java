/*
 * Created on May 29, 2006 2:07:38 PM
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
package com.biglybt.ui.swt.shells.main;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.activities.ActivitiesManager;
import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationChecker;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.ui.IUIIntializer;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.skin.SkinPropertiesImpl;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.associations.AssociationChecker;
import com.biglybt.ui.swt.columns.utils.TableColumnCreatorV3;
import com.biglybt.ui.swt.components.shell.ShellManager;
import com.biglybt.ui.swt.config.wizard.ConfigureWizard;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.debug.ObfuscateShell;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.donations.DonationWindow;
import com.biglybt.ui.swt.extlistener.StimulusRPC;
import com.biglybt.ui.swt.mainwindow.*;
import com.biglybt.ui.swt.mdi.BaseMDI;
import com.biglybt.ui.swt.mdi.TabbedMDI;
import com.biglybt.ui.swt.minibar.AllTransfersBar;
import com.biglybt.ui.swt.minibar.MiniBarManager;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl;
import com.biglybt.ui.swt.search.SearchUtils;
import com.biglybt.ui.swt.sharing.progress.ProgressWindow;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.biglybt.ui.swt.speedtest.SpeedTestSelector;
import com.biglybt.ui.swt.systray.SystemTraySWT;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.utils.DragDropUtils;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.utils.SWTRunnable;
import com.biglybt.ui.swt.views.QuickLinksView;
import com.biglybt.ui.swt.views.skin.WelcomeView;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;
import com.biglybt.ui.swt.views.utils.LocProvUtils;
import com.biglybt.ui.swt.welcome.WelcomeWindow;
import com.biglybt.util.MapUtils;
import com.biglybt.util.NavigationHelper;
import com.biglybt.util.NavigationHelper.navigationListener;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;

/**
 * @author TuxPaper
 * @created May 29, 2006
 *
 *
 * TODO:
 * - MainStatusBar and sidebar components should update when:
		if (parameterName.equals("config.style.useSIUnits") || parameterName.equals("config.style.forceSIValues")) {
			updateComponents();
		}
 * - IconBarEnabler for "new" and "open"
 */
public class MainWindowImpl
	implements MainWindow, ObfuscateShell, AEDiagnosticsEvidenceGenerator,
	UIUpdatable
{

	private static final LogIDs LOGID = LogIDs.GUI;

	private Shell shell;

	private Core core;

	private final IUIIntializer uiInitializer;

	private SWTSkin skin;

	private IMainMenu menu;

	private UISWTInstanceImpl uiSWTInstanceImpl;

	private UIFunctionsImpl uiFunctions;

	private SystemTraySWT systemTraySWT;

	private boolean disposedOrDisposing;

	private DownloadManager[] dms_Startup;

	private boolean isReady = false;

	private MainStatusBar statusBar;

	private String lastShellStatus = null;

	private final boolean delayedCore;

	private TrayWindow downloadBasket;
	private ParameterListener configIconBarEnabledListener;
	private ParameterListener configShowStatusInTitleListener;
	private ParameterListener configShowDLBasketListener;
	private ParameterListener configMonitorClipboardListener;
	private MainWindowGMListener gmListener;
	private navigationListener navigationListener;
	private UISkinnableSWTListener uiSkinnableSWTListener;

	private volatile boolean	hide_all;
	
	/**
	 * Old Initializer.  Core is required to be started
	 *
	 */
	protected
	MainWindowImpl(
			Core core,
			final IUIIntializer uiInitializer)
	{
		delayedCore = false;
		this.core = core;
		this.uiInitializer = uiInitializer;
		AEDiagnostics.addWeakEvidenceGenerator(this);

		disposedOrDisposing = false;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				try {
					createWindow(uiInitializer);
				} catch (Throwable e) {
					Logger.log(new LogAlert(false, "Error Initialize MainWindow", e));
				}
				if (uiInitializer != null) {
					uiInitializer.abortProgress();
				}
			}
		});

		// When a download is added, check for new meta data and
		// un-"wait state" the rating
		GlobalManager gm = core.getGlobalManager();
		dms_Startup = gm.getDownloadManagers().toArray(new DownloadManager[0]);
		gmListener = new MainWindowGMListener();
		gm.addListener(gmListener, false);

		Alerts.addListener(new MainWindowAlertListener());
	}

	/**
	 * New Initializer.  BiglyBTCore does not need to be started.
	 * Use {@link #init(Core)} when core is available.
	 *
	 * Called for STARTUP_UIFIRST
	 *
	 * 1) Constructor
	 * 2) createWindow
	 * 3) init(core)
	 *
	 * @param uiInitializer
	 */
	protected MainWindowImpl(final IUIIntializer uiInitializer) {
		//System.out.println("MainWindow: constructor");
		delayedCore = true;
		this.uiInitializer = uiInitializer;
		AEDiagnostics.addWeakEvidenceGenerator(this);

		Utils.execSWTThread(new SWTRunnable() {
			@Override
			public void runWithDisplay(Display display) {
				//System.out.println("createWindow");
				try {
					createWindow(uiInitializer);
				} catch (Throwable e) {
					Logger.log(new LogAlert(false, "Error Initialize MainWindow", e));
				}

				Utils.readAndDispatchUntilIdle();
			}
		});
	}

	/**
	 * Called only on STARTUP_UIFIRST
	 */

	@Override
	public void init(final Core core) {
		//System.out.println("MainWindow: _init(core)");

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				//System.out.println("_init");
				_init(core);
				if (uiInitializer != null) {
					uiInitializer.abortProgress();
				}
			}
		});
		UIUpdaterSWT.getInstance().addUpdater(this);
	}

	@Override
	public void disposeOnlyUI() {
		MainMDISetup.dispose();

		isReady = false;

		UIExitUtilsSWT.uiShutdown();

		if (systemTraySWT != null) {
			systemTraySWT.dispose();
			systemTraySWT = null;
		}

		try{
				// if we go straight from a text control (say) to the File menu (say) and select
				// exit/restart then the text control doesn't get a focus-lost before the application closes.
				// This can cause confusion if the control is a config option that the user has amended and hit
				// exit/restart - the value isn't updated and therefore the change ignored. Inject an explicit traversal
				// to pick it up
			
			Display display = Display.getCurrent();
		
			if ( display != null && !display.isDisposed()){
				
				Control control = display.getFocusControl();
				
				if ( control != null ){
					
					control.traverse( SWT.TRAVERSE_TAB_NEXT );
				}
			}
		}catch( Throwable e ){
			
		}
		
		/**
		 * Explicitly force the transfer bar location to be saved (if appropriate and open).
		 *
		 * We can't rely that the normal mechanism for doing this won't fail (which it usually does)
		 * when the GUI is being disposed of.
		 */
		try {
			if ( core != null ){
				AllTransfersBar transfer_bar = AllTransfersBar.getBarIfOpen(core.getGlobalManager());
				if (transfer_bar != null) {
					transfer_bar.forceSaveLocation();
				}
			}
		} catch (Exception ignore) {
		}

	}

	/**
	 * Called only on STARTUP_UIFIRST
	 */
	private void _init(Core core) {
		//System.out.println("MainWindow: init(core)");
		this.core = core;

		disposedOrDisposing = false;

		StimulusRPC.hookListeners(core, this);

		if (uiSWTInstanceImpl == null) {
			uiSWTInstanceImpl = new UISWTInstanceImpl();
			uiSWTInstanceImpl.init(uiInitializer);
		}

		postPluginSetup(core);

		// When a download is added, check for new meta data and
		// un-"wait state" the rating
		GlobalManager gm = core.getGlobalManager();
		dms_Startup = gm.getDownloadManagers().toArray(new DownloadManager[0]);
		gmListener = new MainWindowGMListener();
		gm.addListener(gmListener, false);

		Alerts.addListener(new MainWindowAlertListener());

		core.triggerLifeCycleComponentCreated(uiFunctions);

		processStartupDMS();
	}

	private void postPluginSetup(Core core) {
		// we pass core in just as reminder that this function needs core
		if (core == null) {
			return;
		}

		//if (!Utils.isAZ2UI()) {
			ActivitiesManager.initialize(core);
		//}

		LocProvUtils.initialise( core );

		if (!Constants.isSafeMode) {

			MainHelpers.initTransferBar();

			configIconBarEnabledListener = new ParameterListener() {
				@Override
				public void parameterChanged(String parameterName) {
					setVisible(WINDOW_ELEMENT_TOOLBAR, COConfigurationManager.getBooleanParameter(parameterName));
				}
			};
			COConfigurationManager.addAndFireParameterListener("IconBar.enabled",
					configIconBarEnabledListener);
		}

		//  share progress window
		new ProgressWindow( Utils.getDisplay() );
	}

	private void processStartupDMS() {
		// must be in a new thread because we don't want to block
		// initilization or any other add listeners
		AEThread2 thread = new AEThread2("v3.mw.dmAdded", true) {
			@Override
			public void run() {
				long startTime = SystemTime.getCurrentTime();
				if (dms_Startup == null || dms_Startup.length == 0) {
					dms_Startup = null;
					return;
				}

				downloadAdded(dms_Startup, false);

				dms_Startup = null;

				System.out.println("psDMS " + (SystemTime.getCurrentTime() - startTime)
						+ "ms");
			}
		};
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	private static void downloadAdded(final DownloadManager[] dms, boolean allowDonationCheck) {
		boolean oneIsNotLowNoiseOrPersistent = false;
		for (final DownloadManager dm : dms) {
			if (dm == null) {
				continue;
			}

			DownloadManagerState dmState = dm.getDownloadState();

			final TOTorrent torrent = dm.getTorrent();
			if (torrent == null) {
				continue;
			}

			int pfi = PlatformTorrentUtils.getContentPrimaryFileIndex(torrent);

			if ( pfi >= 0 ){
				dmState.setIntAttribute( DownloadManagerState.AT_PRIMARY_FILE_IDX, pfi );
			}

			if (!oneIsNotLowNoiseOrPersistent && !dmState.getFlag(DownloadManagerState.FLAG_LOW_NOISE) && dm.isPersistent()) {
				oneIsNotLowNoiseOrPersistent = true;
			}

		}

		if (oneIsNotLowNoiseOrPersistent && allowDonationCheck) {
			DonationWindow.checkForDonationPopup();
		}
	}

	/**
	 * @param uiInitializer
	 *
	 * called in both delayedCore and !delayedCore
	 */
	private void createWindow(IUIIntializer uiInitializer) {
		//System.out.println("MainWindow: createWindow)");

		long startTime = SystemTime.getCurrentTime();

		UIFunctionsSWT existing_uif = UIFunctionsManagerSWT.getUIFunctionsSWT();

		uiFunctions = new UIFunctionsImpl(this);

		UIFunctionsManager.setUIFunctions(uiFunctions);

		Utils.disposeComposite(shell);

		increaseProgress(uiInitializer, "splash.initializeGui");

		System.out.println("UIFunctions/ImageLoad took "
				+ (SystemTime.getCurrentTime() - startTime) + "ms");
		startTime = SystemTime.getCurrentTime();

		shell = existing_uif==null?new Shell(Utils.getDisplay(), SWT.SHELL_TRIM):existing_uif.getMainShell();

		if (Constants.isWindows) {
			try {
				Class<?> ehancerClass = Class.forName("com.biglybt.ui.swt.win32.Win32UIEnhancer");
				Method method = ehancerClass.getMethod("initMainShell",
						Shell.class);
				method.invoke(null, shell);
			} catch (Exception e) {
				Debug.outNoStack(Debug.getCompressedStackTrace(e, 0, 30), true);
			}
		}

		try {
			shell.setData("class", this);
			shell.setText( UIFunctions.MAIN_WINDOW_NAME );
			Utils.setShellIcon(shell);
			Utils.linkShellMetricsToConfig(shell, "window");
			//Shell activeShell = display.getActiveShell();
			//shell.setVisible(true);
			//shell.moveBelow(activeShell);

			System.out.println("new shell took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			increaseProgress(uiInitializer, "v3.splash.initSkin");

			skin = SWTSkinFactory.getInstance();
			if (Utils.isAZ2UI()) {
  			SWTSkinProperties skinProperties = skin.getSkinProperties();
  			String skinPath = SkinPropertiesImpl.PATH_SKIN_DEFS + "skin3_classic";
  			ResourceBundle rb = ResourceBundle.getBundle(skinPath);
  			skinProperties.addResourceBundle(rb, skinPath);
			}

			/*
			 * KN: passing the skin to the uifunctions so it can be used by UIFunctionsSWT.createMenu()
			 */
			uiFunctions.setSkin(skin);

			System.out.println("new shell setup took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			initSkinListeners();

			increaseProgress(uiInitializer, "v3.splash.initSkin");
			// 0ms
			//System.out.println("skinlisteners init took " + (SystemTime.getCurrentTime() - startTime) + "ms");
			//startTime = SystemTime.getCurrentTime();

			String startID = Utils.isAZ2UI() ? "classic.shell" : "main.shell";
			skin.initialize(shell, startID, uiInitializer);

			increaseProgress(uiInitializer, "v3.splash.initSkin");
			System.out.println("skin init took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			menu = uiFunctions.createMainMenu(shell);
			shell.setData("MainMenu", menu);

			System.out.println("MainMenu init took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			increaseProgress(uiInitializer, "v3.splash.initSkin");

			skin.layout();

			// 0ms
			//System.out.println("skin layout took " + (SystemTime.getCurrentTime() - startTime) + "ms");
			//startTime = SystemTime.getCurrentTime();

			try {
				DragDropUtils.createTorrentDropTarget(shell, false);
			} catch (Throwable e) {
				Logger.log(new LogEvent(LOGID, "Drag and Drop not available", e));
			}

			shell.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					if (configIconBarEnabledListener != null) {
						COConfigurationManager.removeParameterListener("IconBar.enabled",
						configIconBarEnabledListener);
					}
					if (configShowStatusInTitleListener != null) {
						COConfigurationManager.removeParameterListener(
								"Show Status In Window Title",
								configShowStatusInTitleListener);
					}
					if (configShowDLBasketListener != null) {
						COConfigurationManager.removeParameterListener("Show Download Basket",
								configShowDLBasketListener);
					}
					if (configMonitorClipboardListener != null) {
						COConfigurationManager.removeParameterListener(
								"Monitor Clipboard For Torrents",
								configMonitorClipboardListener);
					}
					if (gmListener != null) {
						GlobalManager gm = core.getGlobalManager();
						if (gm != null) {
							gm.removeListener(gmListener);
						}
						gmListener = null;
					}

					if (uiFunctions != null) {
						uiFunctions.dispose();
					}

					if (navigationListener != null) {
						NavigationHelper.removeListener(navigationListener);
						navigationListener = null;
					}

					StimulusRPC.unhookListeners();

					UISkinnableManagerSWT skinnableManagerSWT = UISkinnableManagerSWT.getInstance();
					skinnableManagerSWT.removeSkinnableListener(MessageBoxShell.class.toString(),
							uiSkinnableSWTListener);


					// comment out: shell can dispose without us wanting core to be..
					//dispose(false, false);
				}
			});

			shell.addShellListener(new ShellAdapter() {
				@Override
				public void shellClosed(ShellEvent event) {
					if (disposedOrDisposing) {
						return;
					}
					if (	COConfigurationManager.getBooleanParameter("Close To Tray") && 
							( 	( systemTraySWT != null && COConfigurationManager.getBooleanParameter("Enable System Tray")) || 
								COConfigurationManager.getBooleanParameter("System Tray Disabled Override"))){						

						minimizeToTray(event);
						
					}else{
						
						dispose(false);
						
						event.doit = false;		// don't allow shell to close at this point, the disposal code 
												// will close it when ready to
					}
				}

				@Override
				public void shellActivated(ShellEvent e) {
					Shell shellAppModal = Utils.findFirstShellWithStyle(SWT.APPLICATION_MODAL);
					if (shellAppModal != null) {
						shellAppModal.forceActive();
					} else {
						shell.forceActive();
					}
				}

				@Override
				public void shellIconified(ShellEvent event) {
					if (disposedOrDisposing) {
						return;
					}
					
					if (	COConfigurationManager.getBooleanParameter("Minimize To Tray") && 
							( 	( systemTraySWT != null && COConfigurationManager.getBooleanParameter("Enable System Tray")) || 
								COConfigurationManager.getBooleanParameter("System Tray Disabled Override"))){						

						minimizeToTray(event);
					}
				}

				@Override
				public void shellDeiconified(ShellEvent e) {
					if (Constants.isOSX
							&& COConfigurationManager.getBooleanParameter("Password enabled")) {
						shell.setVisible(false);
						if (PasswordWindow.showPasswordWindow(Utils.getDisplay())) {
							shell.setVisible(true);
						}
					}
				}
			});

			Utils.getDisplay().addFilter(SWT.KeyDown, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if ( event.keyCode == SWT.F9 ){
						
						event.doit = false;
						event.keyCode = 0;
						event.character = '\0';
						flipVisibility( WINDOW_ELEMENT_RIGHTBAR );
						
						return;
					}
					
					// Another window has control, skip filter
					Control focus_control = event.display.getFocusControl();
					if (focus_control != null && focus_control.getShell() != shell)
						return;

					int key = event.character;
					if ((event.stateMask & SWT.MOD1) != 0 && event.character <= 26
							&& event.character > 0)
						key += 'a' - 1;

					if (key == 'l' && (event.stateMask & SWT.MOD1) != 0) {
						// Ctrl-L: Open URL
						if (core == null) {
							return;
						}
						GlobalManager gm = core.getGlobalManager();
						if (gm != null) {
							UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentWindow();
							event.doit = false;
						}

					}else if (key == 'd' && (event.stateMask & SWT.MOD1) != 0) {

							// dump
						if ( Constants.isCVSVersion()){

							Utils.dump( shell );
						}
					} else if (key == 'f'
							&& (event.stateMask & (SWT.MOD1 + SWT.SHIFT)) == SWT.MOD1
									+ SWT.SHIFT) {
						shell.setFullScreen(!shell.getFullScreen());
					}else if ( event.keyCode == SWT.F1 ){
						Utils.launch( Constants.URL_WIKI );
					}
				}
			});

			increaseProgress(uiInitializer, "v3.splash.initSkin");
			System.out.println("pre skin widgets init took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			if (core != null) {
				StimulusRPC.hookListeners(core, this);
			}

			increaseProgress(uiInitializer, "v3.splash.initSkin");
			// 0ms
			//System.out.println("hooks init took " + (SystemTime.getCurrentTime() - startTime) + "ms");
			//startTime = SystemTime.getCurrentTime();

			BaseMDI mdi = initMDI();
			System.out.println("skin widgets (1/2) init took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();
			initWidgets2( mdi );

			increaseProgress(uiInitializer, "v3.splash.initSkin");
			System.out.println("skin widgets (2/2) init took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			System.out.println("pre SWTInstance init took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			increaseProgress(uiInitializer, "v3.splash.hookPluginUI");
			startTime = SystemTime.getCurrentTime();

			TableColumnCreatorV3.initCoreColumns();

			System.out.println("Init Core Columns took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			increaseProgress(uiInitializer, "v3.splash.hookPluginUI");
			startTime = SystemTime.getCurrentTime();

			// attach the UI to plugins
			// Must be done before initializing views, since plugins may register
			// table columns and other objects
			uiSWTInstanceImpl = new UISWTInstanceImpl();
			uiSWTInstanceImpl.init(uiInitializer);

			System.out.println("SWTInstance init took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			increaseProgress(uiInitializer, "splash.initializeGui");
			startTime = SystemTime.getCurrentTime();

		} catch (Throwable t) {
			Debug.out(t);
		} finally {

			String configID = SkinConstants.VIEWID_PLUGINBAR + ".visible";
			if (!ConfigurationDefaults.getInstance().doesParameterDefaultExist(
				configID)) {
				COConfigurationManager.setBooleanDefault(configID, true);
			}
			
			setVisible(WINDOW_ELEMENT_TOPBAR,
					COConfigurationManager.getBooleanParameter(configID)
							&& COConfigurationManager.getIntParameter("User Mode") > 1);

			setVisible(WINDOW_ELEMENT_RIGHTBAR,
					COConfigurationManager.getBooleanParameter( SkinConstants.VIEWID_RIGHTBAR + ".visible" ));

			setVisible(WINDOW_ELEMENT_TOOLBAR,
					COConfigurationManager.getBooleanParameter("IconBar.enabled"));

			shell.layout(true, true);

			System.out.println("shell.layout took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			showMainWindow();

			//================

			increaseProgress(uiInitializer, "splash.initializeGui");

			System.out.println("shell.open took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			processStartupDMS();

			System.out.println("processStartupDMS took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			if (core != null) {
				postPluginSetup(core);
			}

			System.out.println("postPluginSetup init took "
					+ (SystemTime.getCurrentTime() - startTime) + "ms");
			startTime = SystemTime.getCurrentTime();

			navigationListener = new navigationListener() {
				@Override
				public void processCommand(final int type, final String[] args) {
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {

							UIFunctions uif = UIFunctionsManager.getUIFunctions();

							if (type == NavigationHelper.COMMAND_SWITCH_TO_TAB) {
								MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
								if (mdi == null) {
									return;
								}
								mdi.showEntryByID(args[0]);

								if (uif != null) {

									uif.bringToFront();
								}
							} else if (type == NavigationHelper.COMMAND_CONDITION_CHECK) {
							}
						}
					});
				}
			};
			NavigationHelper.addListener(navigationListener);

			if ( !Constants.isOSX ){

				configShowStatusInTitleListener = new ParameterListener() {
					private TimerEventPeriodic timer;
					private String old_text;
					private String my_last_text;

					@Override
					public void
					parameterChanged(
							final String name) {
						Utils.execSWTThread(
								new AERunnable() {
									@Override
									public void
									runSupport() {
										boolean enable = COConfigurationManager.getBooleanParameter(name);

										if (enable) {

											if (timer == null) {

												timer = SimpleTimer.addPeriodicEvent(
														"window.title.updater",
														1000,
														new TimerEventPerformer() {
															@Override
															public void
															perform(
																	TimerEvent event) {
																Utils.execSWTThread(
																		new AERunnable() {
																			@Override
																			public void
																			runSupport() {
																				if (shell.isDisposed()) {

																					return;
																				}

																				String current_txt = shell.getText();

																				if (current_txt != null && !current_txt.equals(my_last_text)) {

																					old_text = current_txt;
																				}

																				String txt = getCurrentTitleText();

																				if (txt != null) {

																					if (!txt.equals(current_txt)) {

																						shell.setText(txt);
																					}

																					my_last_text = txt;
																				}
																			}
																		});
															}
														});
											}
										} else {

											if (timer != null) {

												timer.cancel();

												timer = null;
											}

											if (old_text != null && !shell.isDisposed()) {

												shell.setText(old_text);
											}
										}
									}
								});
					}
				};
				COConfigurationManager.addAndFireParameterListener(
					"Show Status In Window Title",
						configShowStatusInTitleListener);
			}
		}
	}

	private String	last_eta_str = null;
	private long	last_eta;
	private int		eta_tick_count;

	private String
	getCurrentTitleText()
	{
		if ( core == null ){

			return( null );
		}

		GlobalManager gm = core.getGlobalManager();

		if ( gm == null ){

			return( null );
		}

		GlobalManagerStats stats = gm.getStats();

		int down 	= stats.getDataReceiveRate() + stats.getProtocolReceiveRate();
		int up		= stats.getDataSendRate() + stats.getProtocolSendRate();

		eta_tick_count++;

		String eta_str = last_eta_str;

		if ( 	eta_str == null ||
				last_eta < 120 ||
				eta_tick_count%10 == 0 ){

			long	min_eta = Long.MAX_VALUE;
			int		num_downloading = 0;

			List<DownloadManager> dms = gm.getDownloadManagers();

			for ( DownloadManager dm: dms ){

				if ( dm.getState() == DownloadManager.STATE_DOWNLOADING ){

					num_downloading++;

					long dm_eta = dm.getStats().getSmoothedETA();

					if ( dm_eta < min_eta ){

						min_eta = dm_eta;
					}
				}
			}

			if ( min_eta == Long.MAX_VALUE ){

				min_eta = Constants.CRAPPY_INFINITE_AS_LONG;
			}

			last_eta = min_eta;

			eta_str = last_eta_str = num_downloading==0?"":DisplayFormatters.formatETA(min_eta);
		}


		String down_str = formatRateCompact( down );
		String up_str 	= formatRateCompact( up );

		StringBuilder result = new StringBuilder( 50 );

		result.append( MessageText.getString( "ConfigView.download.abbreviated" ));
		result.append( " " );
		result.append( down_str );
		result.append( " " );
		result.append( MessageText.getString( "ConfigView.upload.abbreviated" ));
		result.append( " " );
		result.append( up_str );

		if ( eta_str.length() > 0 ){

			result.append( " " );
			result.append( MessageText.getString( "ConfigView.eta.abbreviated" ));
			result.append( " " );
			result.append( eta_str );
		}

		return( result.toString());
	}

	private String
	formatRateCompact(
		int		rate )
	{
		String str = DisplayFormatters.formatCustomRate( "title.rate", rate );

		if ( str == null ){

			str = DisplayFormatters.formatByteCountToKiBEtc( rate, false, true, 2, DisplayFormatters.UNIT_KB );

			String[] bits = str.split( " " );

			if ( bits.length == 2 ){

				String sep = String.valueOf( DisplayFormatters.getDecimalSeparator());

				String num 	= bits[0];
				String unit = bits[1];

				int	num_len = num.length();

				if ( num_len < 4 ){

					if ( !num.contains( sep )){

						num += sep;

						num_len++;
					}

					while( num_len < 4 ){

						num += "0";

						num_len++;
					}
				}else{
					if ( num_len > 4 ){

						num = num.substring( 0, 4 );

						num_len = 4;
					}
				}

				if ( num.endsWith( sep )){

					num = num.substring( 0, num_len - 1 ) + " ";
				}

				str = num + " " + unit.charAt(0);
			}
		}

		return( str );
	}

	/**
	 * @param uiInitializer
	 * @param taskKey TODO
	 *
	 * @since 3.0.4.3
	 */
	private void increaseProgress(IUIIntializer uiInitializer, String taskKey) {
		if (uiInitializer != null) {
			uiInitializer.increaseProgress();
			if (taskKey != null) {
				uiInitializer.reportCurrentTask(MessageText.getString(taskKey));
			}
		}
		// XXX Disabled because plugin update window will pop up and take control
		// 		 of the dispatch loop..
		/*
		if (Utils.isThisThreadSWT()) {
			// clean the dispatch loop so the splash screen gets updated
			int i = 1000;
			while (display.readAndDispatch() && i > 0) {
				i--;
			}
			//if (i < 999) {
			//	System.out.println("dispatched " + (1000 - i));
			//}
		}
		*/
	}

	@Override
	@SuppressWarnings("deprecation")
	public boolean dispose(final boolean for_restart ){
		if (disposedOrDisposing) {
			return true;
		}
		Boolean b = Utils.execSWTThreadWithBool("v3.MainWindow.dispose",
				new AERunnableBoolean() {
					@Override
					public boolean runSupport() {
						return _dispose(for_restart);
					}
				}, 0);
		return b == null || b;
	}

	boolean _dispose(final boolean bForRestart ) {
		if (disposedOrDisposing) {
			return true;
		}

		disposedOrDisposing = true;
		if (core != null
				&& !UIExitUtilsSWT.canClose(core.getGlobalManager(), bForRestart)) {
			disposedOrDisposing = false;
			return false;
		}

		disposeOnlyUI();

		SWTThread instance = SWTThread.getInstance();
		if (instance != null && !instance.isTerminated()) {
			Utils.getOffOfSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					SWTThread instance = SWTThread.getInstance();
					if (instance != null && !instance.isTerminated()) {
						instance.getInitializer().stopIt(bForRestart);
					}
				}
			});
		}

		return true;
	}

	private final Set<Shell>	minimized_on_hide = new HashSet<>();

	private void showMainWindow() {
		configShowDLBasketListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				configureDownloadBasket();
			}
		};
		COConfigurationManager.addAndFireParameterListener("Show Download Basket",
				configShowDLBasketListener);

		boolean isOSX = com.biglybt.core.util.Constants.isOSX;
		boolean bEnableTray = COConfigurationManager.getBooleanParameter("Enable System Tray");
		boolean bPassworded = COConfigurationManager.getBooleanParameter("Password enabled");
		boolean bStartMinimize = bEnableTray
				&& (bPassworded || COConfigurationManager.getBooleanParameter("Start Minimized"));

		SWTSkinObject soMain = skin.getSkinObject("main");
		if (soMain != null) {
			soMain.getControl().setVisible(true);
		}

		shell.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event event) {
				System.out.println("---------SHOWN AT " + SystemTime.getCurrentTime()
						+ ";" + (SystemTime.getCurrentTime() - Initializer.startTime)
						+ "ms");

					// attempt to fix occasional missing status bar on show (running async seems to fix issue for me)

				if ( statusBar != null ){
					Utils.execSWTThreadLater(
						10,
						new Runnable()
						{
							@Override
							public void
							run()
							{
								statusBar.relayout();
							}
						});
				}

					// bring back and stand-alone shells

				ShellManager.sharedManager().performForShells(
						new Listener()
						{
							@Override
							public void
							handleEvent(
								Event event)
							{
								Shell this_shell = (Shell)event.widget;

								if ( this_shell.getParent() == null && !this_shell.isVisible()){

									boolean	minimize;

									synchronized( minimized_on_hide ){

										minimize = minimized_on_hide.remove( this_shell );
									}

									this_shell.setVisible( true );

									if ( minimize ){

										this_shell.setMinimized( true );

									}else{

										this_shell.moveAbove( shell );
									}
								}
							}
						});
			}
		});

		if (!bStartMinimize) {
			shell.open();
			if (!isOSX) {
				shell.forceActive();
			}
		}


		if (delayedCore) {
			// max 5 seconds of dispatching.  We don't display.sleep here because
			// we only want to clear the backlog of SWT events, and sleep would
			// add new ones
			Display display = Utils.getDisplay();
			if (display == null) {
				return;
			}
			try{
				Utils.readAndDispatchUntilIdleFor( 5000 );
			} catch (Exception e) {
				Debug.out(e);
			}

			System.out.println("---------DONE DISPATCH AT "
  				+ SystemTime.getCurrentTime() + ";"
  				+ (SystemTime.getCurrentTime() - Initializer.startTime) + "ms");
  		if (display.isDisposed()) {
  			return;
  		}
		}

		if (bEnableTray) {

			try {
				systemTraySWT = SystemTraySWT.getTray();

			} catch (Throwable e) {

				e.printStackTrace();
				Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
						"Upgrade to SWT3.0M8 or later for system tray support."));
			}

			if (bStartMinimize) {
				minimizeToTray(null);
			}
			//Only show the password if not started minimized
			//Correct bug #878227
			else {
				if (bPassworded) {
					minimizeToTray(null);
					setVisible(true); // invokes password
				}
			}
		}

		// do this before other checks as these are blocking dialogs to force order

		if (uiInitializer != null) {

			uiInitializer.initializationComplete();
		}

		boolean	run_speed_test = false;

		if (!Utils.isAZ2UI() && !COConfigurationManager.getBooleanParameter("SpeedTest Completed")){


			if ( ConfigurationChecker.isNewInstall()){

				run_speed_test = true;

			}else if ( FeatureAvailability.triggerSpeedTestV1()){

				long	upload_limit	= COConfigurationManager.getLongParameter("Max Upload Speed KBs" );
				boolean	auto_up			= COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY );

				if ( auto_up ){

					if ( upload_limit <= 18 ){

						run_speed_test = true;
					}
				}else{

					boolean up_seed_limit	= COConfigurationManager.getBooleanParameter("enable.seedingonly.upload.rate" );

					if ( upload_limit == 0 && !up_seed_limit ){

						run_speed_test = true;
					}
				}
			}
		}


		if ( run_speed_test ){

			SpeedTestSelector.runMLABTest(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						WelcomeView.setWaitLoadingURL(false);
					}
				});
		}else{

			WelcomeView.setWaitLoadingURL(false);
		}

		if (Utils.isAZ2UI()) {
  		if (!COConfigurationManager.getBooleanParameter("Wizard Completed")) {

  			CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
  				@Override
				  public void coreRunning(Core core) {
  					new ConfigureWizard(false, ConfigureWizard.WIZARD_MODE_FULL);
  				}
  			});
  		}

			checkForWhatsNewWindow();
		}

		AssociationChecker.checkAssociations();

		// Donation stuff
		Map<?, ?> map = VersionCheckClient.getSingleton().getMostRecentVersionCheckData();
		DonationWindow.setInitialAskHours(MapUtils.getMapInt(map,
				"donations.askhrs", DonationWindow.getInitialAskHours()));

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				core.triggerLifeCycleComponentCreated(uiFunctions);
			}
		});

		System.out.println("---------READY AT " + SystemTime.getCurrentTime() + ";"
				+ (SystemTime.getCurrentTime() - Initializer.startTime) + "ms");
		isReady = true;
		//SESecurityManagerImpl.getSingleton().exitVM(0);
	}

	private void configureDownloadBasket() {
		if (COConfigurationManager.getBooleanParameter("Show Download Basket")) {
			if (downloadBasket == null) {
				downloadBasket = new TrayWindow();
				downloadBasket.setVisible(true);
			}
		} else if (downloadBasket != null) {
			downloadBasket.setVisible(false);
			downloadBasket = null;
		}
	}

	private void checkForWhatsNewWindow() {
		final String CONFIG_LASTSHOWN = "welcome.version.lastshown";

		// Config used to store int, such as 2500.  Now, it stores a string
		// getIntParameter will return default value if parameter is string (user
		// downgraded)
		// getStringParameter will bork if parameter isn't really a string

		try {
			String lastShown = "";
			boolean bIsStringParam = true;
			try {
				lastShown = COConfigurationManager.getStringParameter(CONFIG_LASTSHOWN,
						"");
			} catch (Exception e) {
				bIsStringParam = false;
			}

			if (lastShown.length() == 0) {
				// check if we have an old style version
				int latestDisplayed = COConfigurationManager.getIntParameter(
						CONFIG_LASTSHOWN, 0);
				if (latestDisplayed > 0) {
					bIsStringParam = false;
					String s = "" + latestDisplayed;
					for (int i = 0; i < s.length(); i++) {
						if (i != 0) {
							lastShown += ".";
						}
						lastShown += s.charAt(i);
					}
				}
			}

			if (Constants.compareVersions(lastShown, Constants.getBaseVersion()) < 0) {
				new WelcomeWindow(shell);
				if (!bIsStringParam) {
					// setting parameter to a different value type makes az unhappy
					COConfigurationManager.removeParameter(CONFIG_LASTSHOWN);
				}
				COConfigurationManager.setParameter(CONFIG_LASTSHOWN,
						Constants.getBaseVersion());
				COConfigurationManager.save();
			}
		} catch (Exception e) {
			Debug.out(e);
		}
	}

	@Override
	public void
	setHideAll(
		final boolean hide )
	{
		hide_all = hide;
		
		Utils.execSWTThread(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					if ( hide ){

						setVisible( false, true );

						if ( systemTraySWT != null ){

							systemTraySWT.dispose();
						}
					}else{

						setVisible( true, true );

						if ( COConfigurationManager.getBooleanParameter("Enable System Tray")) {

							systemTraySWT = SystemTraySWT.getTray();
						}
					}
				}
			});
	}

	public boolean
	getHideAll()
	{
		return( hide_all );
	}
	
	private void setVisible(final boolean visible) {
		setVisible(visible, true);
	}

	@Override
	public void setVisible(final boolean visible, final boolean tryTricks) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				boolean currentlyVisible = shell.getVisible() && !shell.getMinimized();
				if (visible && !currentlyVisible) {
					if (COConfigurationManager.getBooleanParameter("Password enabled")) {
						if (!PasswordWindow.showPasswordWindow(Utils.getDisplay())) {
							shell.setVisible(false);
							return;
						}
					}
				}

				if (!isReady && visible) {
					return;
				}

				ArrayList<Shell> wasVisibleList = null;
				boolean bHideAndShow = false;
				// temp disabled
				//tryTricks && visible && Constants.isWindows && display.getActiveShell() != shell;
				if (bHideAndShow) {
					wasVisibleList = new ArrayList<>();
					// We don't want the window to just flash and not open, so:
					// -Minimize main shell
					// -Set all shells invisible
					try {
						shell.setMinimized(true);
						Shell[] shells = shell.getDisplay().getShells();
						for (int i = 0; i < shells.length; i++) {
							if (shells[i].isVisible()) {
								wasVisibleList.add(shells[i]);
								shells[i].setVisible(false);
							}
						}
					} catch (Exception ignored) {
					}
				}

				if (visible) {
					if (shell.getMinimized()) {
						shell.setMinimized(false);
					}
					if (!currentlyVisible
							&& COConfigurationManager.getBooleanParameter("window.maximized")) {
						shell.setMaximized(true);
					}
				} else {
					// XXX hack for release.. should not access param outside Utils.linkShellMetrics
					COConfigurationManager.setParameter("window.maximized",
							shell.getMaximized());
				}

				shell.setVisible(visible);
				if (visible) {
					shell.forceActive();

					if (bHideAndShow) {
						try {
							Shell[] shells = shell.getDisplay().getShells();
							for (int i = 0; i < shells.length; i++) {
								if (shells[i] != shell) {
									if (wasVisibleList != null
											&& wasVisibleList.contains(shells[i])) {
										shells[i].setVisible(visible);
									}
									shells[i].setFocus();
								}
							}
						} catch (Exception ignored) {
						}
					}
				}

			}
		});
	}

	private void minimizeToTray(ShellEvent event) {
		//Added this test so that we can call this method with null parameter.
		if (event != null) {
			event.doit = false;
		}

		// XXX hack for release.. should not access param outside Utils.linkShellMetrics
		COConfigurationManager.setParameter("window.maximized",
				shell.getMaximized());
		shell.setVisible(false);

		ShellManager.sharedManager().performForShells(
			new Listener()
			{
				@Override
				public void
				handleEvent(
					Event event)
				{
					final Shell shell = (Shell)event.widget;

					if ( shell.getParent() == null ){

						if ( shell.getMinimized()){

							synchronized( minimized_on_hide ){

								minimized_on_hide.add( shell );

								shell.addDisposeListener(
									new DisposeListener() {

										@Override
										public void
										widgetDisposed(
											DisposeEvent e)
										{
											synchronized( minimized_on_hide ){

												minimized_on_hide.remove( shell );
											}
										}
									});
							}
						}

						shell.setVisible( false );
					}
				}
			});

		MiniBarManager.getManager().setAllVisible(true);
	}

	/**
	 * Associates every view ID that we use to a class, and creates the class
	 * on first EVENT_SHOW.
	 */
	private void initSkinListeners() {
		uiSkinnableSWTListener = new UISkinnableSWTListener() {
			@Override
			public void skinBeforeComponents(Composite composite,
			                                 Object skinnableObject, Object[] relatedObjects) {

				MessageBoxShell shell = (MessageBoxShell) skinnableObject;

				TOTorrent torrent = null;
				DownloadManager dm = (DownloadManager) LogRelationUtils.queryForClass(
						relatedObjects, DownloadManager.class);
				if (dm != null) {
					torrent = dm.getTorrent();
				} else {
					torrent = (TOTorrent) LogRelationUtils.queryForClass(
							relatedObjects, TOTorrent.class);
				}

				if (torrent != null && shell.getLeftImage() == null) {
					byte[] contentThumbnail = PlatformTorrentUtils.getContentThumbnail(torrent);
					if (contentThumbnail != null) {
						try {
							ByteArrayInputStream bis = new ByteArrayInputStream(
									contentThumbnail);
							final Image img = new Image(Display.getDefault(), bis);

							shell.setLeftImage(img);

							composite.addDisposeListener(new DisposeListener() {
								@Override
								public void widgetDisposed(DisposeEvent e) {
									if (!img.isDisposed()) {
										img.dispose();
									}
								}
							});
						} catch (Exception ignored) {

						}
					}
				}
			}

			@Override
			public void skinAfterComponents(Composite composite,
			                                Object skinnableObject, Object[] relatedObjects) {
			}
		};
		UISkinnableManagerSWT skinnableManagerSWT = UISkinnableManagerSWT.getInstance();
		skinnableManagerSWT.addSkinnableListener(MessageBoxShell.class.toString(),
				uiSkinnableSWTListener);
	}

	private BaseMDI initMDI() {
		SWTSkinObject skinObject = skin.getSkinObject(SkinConstants.VIEWID_MDI);
		if (null != skinObject) {
			BaseMDI mdi = Utils.isAZ2UI() ? new TabbedMDI() : new SideBar();
			mdi.buildMDI(skinObject);
			MainMDISetup.setupSideBar(mdi);
			return( mdi );
		}
		return( null );
	}

	private void initWidgets2(BaseMDI mdi ) {
		SWTSkinObject skinObject = skin.getSkinObject("statusbar");
		if (skinObject != null) {
			final Composite cArea = (Composite) skinObject.getControl();

			statusBar = new MainStatusBar();
			Composite composite = statusBar.initStatusBar(cArea);

			composite.setLayoutData(Utils.getFilledFormData());
		}

		skinObject = skin.getSkinObject("search-text");
		if (skinObject != null) {
			attachSearchBox(skinObject);
		}

		skinObject = skin.getSkinObject("add-torrent");
		if (skinObject instanceof SWTSkinObjectButton) {
			SWTSkinObjectButton btn = (SWTSkinObjectButton) skinObject;
			btn.addSelectionListener(new ButtonListenerAdapter() {
				// @see SWTSkinButtonUtility.ButtonListenerAdapter#pressed(SWTSkinButtonUtility, SWTSkinObject, int)
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentWindow();
				}
			});
		}

		skinObject = skin.getSkinObject("quick-links");
		
		if ( skinObject != null && mdi != null ){
			
			QuickLinksView.init( mdi, skinObject );
		}
		
		skinObject = skin.getSkinObject(SkinConstants.VIEWID_PLUGINBAR);
		if (skinObject != null) {
			Menu topbarMenu = new Menu(shell, SWT.POP_UP);

			if (COConfigurationManager.getIntParameter("User Mode") > 1) {
				MenuItem mi =
					MainMenuV3.createPluginBarMenuItem(skin, topbarMenu,
						"v3.MainWindow.menu.view." + SkinConstants.VIEWID_PLUGINBAR,
						SkinConstants.VIEWID_PLUGINBAR + ".visible",
						SkinConstants.VIEWID_PLUGINBAR );

				if ( Utils.isAZ2UI()){

						// remove any accelerator as it doesn't work on this menu and we don't have a View menu entry

					String str = mi.getText();

					int pos = str.indexOf( "\t" );

					if ( pos != -1 ){

						str = str.substring(0,pos).trim();

						mi.setText( str );
					}

					mi.setAccelerator( SWT.NULL );
				}
			}

			new MenuItem(topbarMenu, SWT.SEPARATOR);

			final MenuItem itemClipMon = new MenuItem(topbarMenu, SWT.CHECK );
			Messages.setLanguageText(itemClipMon,
					"label.monitor.clipboard");
			itemClipMon.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					COConfigurationManager.setParameter( "Monitor Clipboard For Torrents", itemClipMon.getSelection());
				}
			});

			boolean enabled = COConfigurationManager.getBooleanParameter( "Monitor Clipboard For Torrents" );
			itemClipMon.setSelection( enabled );

			configMonitorClipboardListener = new ParameterListener() {

				private volatile AEThread2 monitor_thread;

				private String last_text;

				@Override
				public void parameterChanged(String parameterName) {

					boolean enabled = COConfigurationManager.getBooleanParameter(parameterName);

					if (enabled) {

						if (monitor_thread == null) {

							final AEThread2 new_thread[] = {null};

							monitor_thread = new_thread[0] = new
									AEThread2("Clipboard Monitor") {
										@Override
										public void
										run() {
											Runnable checker =
													new Runnable() {
														@Override
														public void
														run() {
															if ( monitor_thread != new_thread[0] ){

																return;
															}

															String text = (String)ClipboardCopy.copyFromClipboard();

															if (text != null && text.length() <= 2048) {

																if (last_text == null || !last_text.equals(text)) {

																	last_text = text;

																	PluginInterface pi = ClipboardCopy.getOriginator( text );
																	
																	boolean ignore = false;
																	
																		// ignore media server content URIs
																	
																	if ( pi != null ){
																		
																		if ( pi.getPluginID().equals( "azupnpav" )){
																			
																			ignore = true;
																		}
																	}
																	
																	if ( !ignore ){
																	
																		TorrentOpener.openTorrentsFromClipboard(text);
																	}
																}
															}
														}
													};

											while (true) {

												try {

													Utils.execSWTThread(checker);

												} catch (Throwable e) {

													Debug.out(e);

												} finally {

													if (monitor_thread != new_thread[0]) {

														break;

													} else {

														try {
															Thread.sleep(500);

														} catch (Throwable e) {

															Debug.out(e);

															break;
														}
													}
												}
											}
										}
									};

							monitor_thread.start();
						}
					} else {

						monitor_thread = null;
						last_text = null;
					}
				}
			};
			COConfigurationManager.addAndFireParameterListener(
				"Monitor Clipboard For Torrents",
					configMonitorClipboardListener);

			new MenuItem(topbarMenu, SWT.SEPARATOR);

			SearchUtils.addMenus( topbarMenu );

			addMenuAndNonTextChildren((Composite) skinObject.getControl(), topbarMenu);

			skinObject = skin.getSkinObject(SkinConstants.VIEWID_TOOLBAR);
			if (skinObject != null) {
				addMenuAndNonTextChildren((Composite) skinObject.getControl(),
						topbarMenu);
			}
		}
	}

	private void addMenuAndNonTextChildren(Composite parent, Menu menu) {
		parent.setMenu(menu);

		Control[] children = parent.getChildren();
		for (Control control : children) {
			if (control instanceof Composite) {
				Composite c = (Composite) control;
				addMenuAndNonTextChildren(c, menu);
			} else if (!(control instanceof Text)) {
				control.setMenu(menu);
			}
		}
	}


	/**
	 * @param skinObject
	 */
	private void attachSearchBox(SWTSkinObject skinObject) {
		Composite cArea = (Composite) skinObject.getControl();

		boolean DARK_MODE = Utils.isDarkAppearanceNative();
		
		/* doesn't appear to be needed anymore
		 
		if (Utils.isGTK3 || DARK_MODE) {
			// TextBox on GTK3/SWT and OSX/Dark will expand box to fit it's hugeness
			// Workaround by creating a composite in a fixed height composite
			// (Yes, both are needed..)
			FormData filledFormData;
			Composite c1 = new Composite(cArea, SWT.NONE);
			filledFormData = Utils.getFilledFormData();
			filledFormData.height = 24;
			filledFormData.bottom = null;
			c1.setLayoutData(filledFormData);
			c1.setLayout(new FormLayout());

			Composite c2 = new Composite(c1, SWT.NONE);
			c2.setLayout(new FormLayout());
			filledFormData = Utils.getFilledFormData();
			c2.setLayoutData(filledFormData);
			cArea= c2;
		}
		*/
		
		final Text text = new Text(cArea, (DARK_MODE && !( Utils.isGTK3 || Constants.isWindows ))?SWT.BORDER:SWT.NONE);
		text.setMessage(MessageText.getString("v3.MainWindow.search.defaultText"));
		FormData filledFormData = Utils.getFilledFormData();
		
			// not great but as of Dec 2021 seems to be the best way to make things actually look ok...
		
		if ( Utils.isGTK3 ){	
			if ( DARK_MODE ){
				filledFormData.height = 20;
			}else {
				filledFormData.height = 19;
				filledFormData.bottom = new FormAttachment(100,-1);
			}
		}else if ( Constants.isOSX && DARK_MODE ){
			filledFormData.height = 19;
		}else if ( Constants.isWindows && DARK_MODE ){
			filledFormData.height = 22;
		}
		
		text.setLayoutData(filledFormData);

		text.setData("ObfuscateImage", new ObfuscateImage() {
			@Override
			public Image obfuscatedImage(Image image) {
				Point location = Utils.getLocationRelativeToShell(text);
				Point size = text.getSize();
				UIDebugGenerator.obfuscateArea(image, new Rectangle(
						location.x, location.y, size.x, size.y));
				return image;
			}
		});

		FontUtils.fontToWidgetHeight(text);

		text.setTextLimit(2048);	// URIs can get pretty long...

		if (Constants.isWindows) {
  		text.addListener(SWT.MouseDown, new Listener() {
  			@Override
			  public void handleEvent(Event event) {
  				if (event.count == 3) {
  					text.selectAll();
  				}
  			}
  		});
		}

		String tooltip = MessageText.getString( "v3.MainWindow.search.tooltip" );

		Utils.setTT(text, tooltip );

		SWTSkinProperties properties = skinObject.getProperties();
		Color colorSearchTextBG = properties.getColor("color.search.text.bg");
		Color colorSearchTextFG = properties.getColor("color.search.text.fg");

		if ( !Utils.isDarkAppearancePartial()) {
			if (colorSearchTextBG != null) {
				text.setBackground(colorSearchTextBG);
			}
		}

		final TextWithHistory twh = new TextWithHistory( "mainwindow.search.history", text );

		text.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e) {
				int key = e.character;

				if (e.stateMask == SWT.MOD1) {

					if (key <= 26 && key > 0) {
						key += 'a' - 1;
					}

					if (key == 'a') {
						text.selectAll();
					}
				}
			}

			@Override
			public void keyReleased(KeyEvent arg0) {
				// TODO Auto-generated method stub

			}
		});

		text.addListener(SWT.KeyDown, new Listener() {

			@Override
			public void handleEvent(Event event) {
				Text text = (Text) event.widget;
				if (event.keyCode == SWT.ESC) {
					text.setText("");
					return;
				}
				if (event.character == SWT.CR || event.keyCode == SWT.KEYPAD_CR) {
					if ( event.doit){
						String expression = text.getText();

						if ( expression.startsWith( "test://" )){
							
							runTest( expression.substring( 7  ));
							
						}else{
							uiFunctions.doSearch( expression);

							twh.addHistory( expression );
						}
					}
				}
			}
		});

		SWTSkinObject searchGo = skin.getSkinObject("search-go");
		if (searchGo != null) {
			SWTSkinButtonUtility btnGo = new SWTSkinButtonUtility(searchGo);
			btnGo.addSelectionListener(new ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					String sSearchText = text.getText().trim();
					uiFunctions.doSearch(sSearchText);

					twh.addHistory( sSearchText );
				}
			});
		}

		SWTSkinObject so = skin.getSkinObject("search-dropdown");
		if (so != null) {
			SWTSkinButtonUtility btnSearchDD = new SWTSkinButtonUtility(so);
			btnSearchDD.setTooltipID( "v3.MainWindow.search.tooltip" );
			btnSearchDD.addSelectionListener(new ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int button, int stateMask) {

					if ( button == 1 ){

						String sSearchText = text.getText().trim();

						uiFunctions.doSearch(sSearchText);

						twh.addHistory( sSearchText );
					}
				}
			});
		}
	}
	
	private void
	runTest(
		String	cmd )
	{
		UIToolBarManager tbm = uiFunctions.getToolBarManager();
		
		if ( cmd.equals( "addTBI" )){
			
			UIToolBarItem item = tbm.createToolBarItem( "mainwin:test" );
			
			item.setGroupID("players");
			
			tbm.addToolBarItem( item );
			
		}else if ( cmd.equals( "removeTBI" )){
			
			tbm.removeToolBarItem( "mainwin:test" );
			
		}else if ( cmd.equals( "removeTBI2" )){
			
			tbm.removeToolBarItem( "251290325" );
			
		}else if ( cmd.equals( "listTBI" )){
			
			UIToolBarItem[] items = tbm.getAllToolBarItems();
			
			for ( UIToolBarItem item: items ){
				
				System.out.println( item.getGroupID() + "/" + item.getID());
			}
		}
	}
	
	@Override
	public Shell
	getShell()
	{
		return( shell );
	}

	@Override
	public UISWTInstanceImpl getUISWTInstanceImpl() {
		return uiSWTInstanceImpl;
	}

	@Override
	public MainStatusBar getMainStatusBar() {
		return statusBar;
	}

	@Override
	public boolean isVisible(int windowElement) {
		switch (windowElement) {
			case WINDOW_ELEMENT_TOOLBAR: {
				SWTSkinObject skinObject = skin.getSkinObject(SkinConstants.VIEWID_TOOLBAR);
				if (skinObject != null) {
					return skinObject.isVisible();
				}
				break;
			}
			case WINDOW_ELEMENT_TOPBAR: {
				SWTSkinObject skinObject = skin.getSkinObject(SkinConstants.VIEWID_PLUGINBAR);
				if (skinObject != null) {
					return skinObject.isVisible();
				}
				break;
			}
			case WINDOW_ELEMENT_RIGHTBAR: {
				SWTSkinObject skinObject = skin.getSkinObject(SkinConstants.VIEWID_RIGHTBAR);
				if (skinObject != null) {
					return skinObject.isVisible();
				}
				break;
			}
			case WINDOW_ELEMENT_QUICK_LINKS: {
				SWTSkinObject skinObject = skin.getSkinObject(SkinConstants.VIEWID_QUICK_LINKS);
				if (skinObject != null) {
					return skinObject.isVisible();
				}
				break;
			}
			case WINDOW_ELEMENT_STATUSBAR:
				//TODO:
				break;
			case WINDOW_ELEMENT_MENU:
				//TODO:
				break;
			case WINDOW_ELEMENT_ALL:{

				return( !shell.getMinimized());
			}
		}

		return false;
	}

	@Override
	public void setVisible(int windowElement, boolean value) {
		switch (windowElement) {
			case WINDOW_ELEMENT_TOOLBAR:{
				SWTSkinUtils.setVisibility(skin, "IconBar.enabled",
						SkinConstants.VIEWID_TOOLBAR, value, true);
				break;
			}
			case WINDOW_ELEMENT_TOPBAR:{

				SWTSkinUtils.setVisibility(skin, SkinConstants.VIEWID_PLUGINBAR
						+ ".visible", SkinConstants.VIEWID_PLUGINBAR, value, true);

				break;
			}
			case WINDOW_ELEMENT_RIGHTBAR:{

				SWTSkinUtils.setVisibility(skin, SkinConstants.VIEWID_RIGHTBAR
						+ ".visible", SkinConstants.VIEWID_RIGHTBAR, value, true);

				break;
			}
			case WINDOW_ELEMENT_QUICK_LINKS:{

				SWTSkinUtils.setVisibilityRelaxed(skin, SkinConstants.VIEWID_QUICK_LINKS
						+ ".visible", SkinConstants.VIEWID_QUICK_LINKS, value, true);

				break;
			}
			case WINDOW_ELEMENT_STATUSBAR:
				//TODO:
				break;
			case WINDOW_ELEMENT_MENU:
				//TODO:
				break;
			case WINDOW_ELEMENT_ALL:{
				
				if ( !shell.isDisposed()){
					
					shell.setMinimized( true );
				}
				
				break;
			}
		}

	}

	private void
	flipVisibility(
		int windowElement )
	{
		setVisible(windowElement, !isVisible(windowElement));
	}
	
	@Override
	public Rectangle getMetrics(int windowElement) {
		switch (windowElement) {
			case WINDOW_ELEMENT_TOOLBAR:
				break;
			case WINDOW_ELEMENT_TOPBAR:{

				SWTSkinObject skinObject = skin.getSkinObject(SkinConstants.VIEWID_PLUGINBAR);
				if (skinObject != null) {
					return skinObject.getControl().getBounds();
				}

				break;
			}
			case WINDOW_ELEMENT_RIGHTBAR:{

				SWTSkinObject skinObject = skin.getSkinObject(SkinConstants.VIEWID_RIGHTBAR);
				if (skinObject != null) {
					return skinObject.getControl().getBounds();
				}

				break;
			}
			case WINDOW_ELEMENT_QUICK_LINKS:
				break;
			case WINDOW_ELEMENT_STATUSBAR:

				return statusBar.getBounds();

			case WINDOW_CLIENT_AREA:

				return shell.getClientArea();

			case WINDOW_CONTENT_DISPLAY_AREA:

				Rectangle r = getMetrics(WINDOW_CLIENT_AREA);
				r.height -= getMetrics(WINDOW_ELEMENT_TOPBAR).height;
				r.height -= getMetrics(WINDOW_ELEMENT_TOOLBAR).height;
				r.height -= getMetrics(WINDOW_ELEMENT_STATUSBAR).height;
				r.width  -= getMetrics(WINDOW_ELEMENT_RIGHTBAR).width;
				return r;

		}

		return new Rectangle(0, 0, 0, 0);
	}

	private SWTSkin getSkin() {
		return skin;
	}

	@Override
	public boolean isReady() {
		return isReady;
	}

	@Override
	public Image generateObfuscatedImage() {
		return( UIDebugGenerator.generateObfuscatedImage( shell ));
	}

	// @see com.biglybt.core.util.AEDiagnosticsEvidenceGenerator#generate(com.biglybt.core.util.IndentWriter)
	@Override
	public void generate(IndentWriter writer) {
		writer.println("SWT UI");

		try {
			writer.indent();

			TableColumnManager.getInstance().generateDiagnostics(writer);
		} finally {

			writer.exdent();
		}
	}

	@Override
	public void setSelectedLanguageItem() {
		if (!Utils.isThisThreadSWT()) {
			Utils.execSWTThread(this::setSelectedLanguageItem);
			return;
		}
		Messages.updateLanguageForControl(shell);

		if (systemTraySWT != null) {
			systemTraySWT.updateLanguage();
		}

		if (statusBar != null) {
			statusBar.refreshStatusText();
		}

		// download basket

		skin.triggerLanguageChange();

		if (statusBar != null) {
			statusBar.updateStatusText();
		}

		if (menu != null) {
			MenuFactory.updateMenuText(menu.getMenu(IMenuConstants.MENU_ID_MENU_BAR));
		}
	}

	@Override
	public IMainMenu getMainMenu() {
		return menu;
	}

	@Override
	public void updateUI() {
		//if (shell != null) {
		//	Utils.setShellIcon(shell);
		//}
	}

	@Override
	public String getUpdateUIName() {
		return "MainWindow";
	}

	private static class MainWindowGMListener implements GlobalManagerListener {

		@Override
		public void seedingStatusChanged(boolean seeding_only_mode, boolean b) {
		}

		@Override
		public void downloadManagerRemoved(DownloadManager dm) {
		}

		@Override
		public void downloadManagerAdded(final DownloadManager dm) {
			downloadAdded(new DownloadManager[] {
				dm
			}, true);
		}

		@Override
		public void destroyed() {
		}

		@Override
		public void destroyInitiated() {
		}

	}

	private static class MainWindowAlertListener implements Alerts.AlertListener {

		@Override
		public boolean allowPopup(Object[] relatedObjects, int configID) {
			DownloadManager dm = (DownloadManager) LogRelationUtils.queryForClass(
					relatedObjects, DownloadManager.class);

			return dm == null || !dm.getDownloadState().getFlag(
					DownloadManagerState.FLAG_LOW_NOISE);
		}

	}
}
