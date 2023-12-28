/*
 * Created on Jul 13, 2006 6:15:55 PM
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

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerEvent;
import com.biglybt.core.history.DownloadHistoryManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginHTTPProxy;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.util.*;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;
import com.biglybt.ui.*;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.impl.TableColumnImpl;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryOpenListener;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.mainwindow.*;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.minibar.AllTransfersBar;
import com.biglybt.ui.swt.minibar.MiniBarManager;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UIToolBarManagerImpl;
import com.biglybt.ui.swt.plugininstall.SimplePluginInstaller;
import com.biglybt.ui.swt.progress.IProgressReporter;
import com.biglybt.ui.swt.progress.ProgressReportingManager;
import com.biglybt.ui.swt.search.SearchHandler;
import com.biglybt.ui.swt.shells.*;
import com.biglybt.ui.swt.shells.opentorrent.OpenTorrentOptionsWindow;
import com.biglybt.ui.swt.shells.opentorrent.OpenTorrentWindow;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.update.FullUpdateWindow;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;
import com.biglybt.ui.swt.utils.TorrentUIUtilsV3;
import com.biglybt.ui.swt.views.skin.*;
import com.biglybt.ui.swt.views.skin.SkinnedDialog.SkinnedDialogClosedListener;
import com.biglybt.ui.swt.views.skin.sidebar.SideBarEntrySWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

import com.biglybt.pif.download.DownloadStub;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;

/**
 * @author TuxPaper
 * @created Jul 13, 2006
 *
 */
public class UIFunctionsImpl
	implements UIFunctionsSWT
{
	private static final boolean PROXY_VIEW_URL	= false;

	private final static String MSG_ALREADY_EXISTS = "OpenTorrentWindow.mb.alreadyExists";

	private final static String MSG_ALREADY_EXISTS_NAME = MSG_ALREADY_EXISTS
			+ ".default.name";

	private final static LogIDs LOGID = LogIDs.GUI;

	private final MainWindow mainWindow;

	/**
	 * Stores the current <code>SWTSkin</code> so it can be used by {@link #createMainMenu(Shell)}
	 */
	private SWTSkin skin = null;

	protected boolean isTorrentMenuVisible;
	private final ParameterListener paramShowTorrentsMenuListener;

	/**
	 * @param window
	 */
	public UIFunctionsImpl(
			MainWindow window) {
		this.mainWindow = window;

		paramShowTorrentsMenuListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				isTorrentMenuVisible = COConfigurationManager.getBooleanParameter("show_torrents_menu");
			}
		};
		COConfigurationManager.addAndFireParameterListener(
				"show_torrents_menu", paramShowTorrentsMenuListener);
	}

	@Override
	public String
	getUIType()
	{
		return( UIInstance.UIT_SWT );
	}

	// @see UIFunctions#bringToFront()
	@Override
	public void bringToFront() {
		bringToFront(true);
	}

	// @see UIFunctions#bringToFront(boolean)
	@Override
	public void bringToFront(final boolean tryTricks) {

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				try {
					// this will force active and set !minimized after PW test
					mainWindow.setVisible(true, tryTricks);

				} catch (Exception e) {
					Logger.log(new LogEvent(LOGID, "bringToFront", e));
				}

			}
		});
	}

	@Override
	public int
	getVisibilityState()
	{
		final Shell shell = getMainShell();

		if ( shell == null ){

			return( VS_TRAY_ONLY );		// not sure about this

		}else{

			final int[] result = { VS_MINIMIZED_TO_TRAY };

			final AESemaphore sem = new AESemaphore( "getVisibilityState" );

			boolean success = Utils.execSWTThread(() -> {
				try {
					if (shell.isDisposed()) {

						result[0] = VS_TRAY_ONLY;

					} else if (!shell.isVisible()) {

						result[0] = VS_MINIMIZED_TO_TRAY;

					} else if (shell.getMinimized()) {

						result[0] = VS_MINIMIZED;

					} else {

						result[0] = VS_ACTIVE;
					}
				} finally {

					sem.release();
				}
			});

			if (success) {

				sem.reserve( 30*1000 );	// shouldn't block as if this is SWT thread code will run immediately, otherwise SWT thread should be quick
			}

			return( result[0] );
		}
	}

	// @see UIFunctionsSWT#closeDownloadBars()
	@Override
	public void closeDownloadBars() {
		try {
			Utils.execSWTThreadLater(0, new AERunnable() {
				@Override
				public void runSupport() {
					MiniBarManager.getManager().closeAll();
				}
			});

		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "closeDownloadBars", e));
		}

	}
	
	@Override
	public boolean dispose(boolean for_restart) {
		try {
			return mainWindow.dispose(for_restart );
		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "Disposing MainWindow", e));
		}
		return false;
	}

	// @see UIFunctionsSWT#getMainShell()
	@Override
	public Shell getMainShell() {
		return mainWindow == null ? null : mainWindow.getShell();
	}

	// @see UIFunctionsSWT#getPluginViews()
	@Override
	public UISWTView[] getPluginViews() {
		try {
			return new UISWTView[0];
		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "getPluginViews", e));
		}

		return new UISWTView[0];
	}

	@Override
	public void openPluginView(UISWTViewBuilderCore builder, boolean bSetFocus) {
		try {
			MultipleDocumentInterfaceSWT mdi = getMDISWT();

			if (mdi != null) {

				MdiEntry entry = mdi.createEntry(builder, true);
				if (bSetFocus) {
					mdi.showEntryByID(builder.getViewID());
				} else if (entry instanceof BaseMdiEntry) {
					// Some plugins (CVS Updater) want their view's composite initialized
					// on OpenPluginView, otherwise they won't do logic users expect
					// (like check for new snapshots).  So, enforce loading entry.
					((BaseMdiEntry) entry).build();
				}
			}
		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "openPluginView", e));
		}

	}

	// @see UIFunctions#refreshIconBar()
	@Override
	public void refreshIconBar() {
		try {
			SkinView[] tbSkinViews = SkinViewManager.getMultiByClass(ToolBarView.class);
			if (tbSkinViews != null) {
				for (SkinView skinview : tbSkinViews) {
					if (skinview instanceof ToolBarView) {
						ToolBarView tb = (ToolBarView) skinview;
  					if (tb.isVisible()) {
  						tb.refreshCoreToolBarItems();
  					}
					}
				}
			}

		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "refreshIconBar", e));
		}

	}

	// @see UIFunctions#refreshLanguage()
	@Override
	public void refreshLanguage() {
		try {
			mainWindow.setSelectedLanguageItem();

		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "refreshLanguage", e));
		}

	}

	// @see UIFunctions#setStatusText(java.lang.String)
	@Override
	public void setStatusText(final String key) {
		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				IMainStatusBar sb = getMainStatusBar();
				if ( sb != null ){
					sb.setStatusText(key);
				}
			}
		});
	}

	// @see UIFunctions#setStatusText(int, java.lang.String, UIStatusTextClickListener)
	@Override
	public void setStatusText(final int statustype,
	                          final String key,
	                          final UIStatusTextClickListener l) {
		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				IMainStatusBar sb = getMainStatusBar();
				if ( sb != null ){
					sb.setStatusText(statustype, key, l);
				}
			}
		});
	}

	@Override
	public Object 
	pushStatusText(
		String key )
	{
		IProgressReporter pReporter = ProgressReportingManager.getInstance().addReporter();
		
		pReporter.setName( key );
		
		pReporter.setTitle(MessageText.getString("fileDownloadWindow.title"));
		
		return( pReporter );
	}

	@Override
	public void 
	popStatusText(
		Object	o,
		int		reason,
		String	message )
	{
		IProgressReporter pReporter = (IProgressReporter)o;
			
		if ( reason == 0 ){
		
			if ( message != null ){
				
				pReporter.appendDetailMessage(message);
			}
			
			pReporter.setDone();
			
		}else if ( reason == 1 ){
			
			if ( message != null ){
				
				pReporter.appendDetailMessage(message);
			}

			pReporter.cancel();
			
		}else{
			
			pReporter.setErrorMessage( message!=null?message:MessageText.getString("fileDownloadWindow.state_error"));
		}
		
		pReporter.dispose();
		
		if ( reason == 1 ){
		
				// remove if cancelled as not of interest, leave for success/fail so result
				// remains visible if "auto remove" not enabled
			
			ProgressReportingManager.getInstance().remove(pReporter);
		}
	}
	
	// @see UIFunctionsSWT#getMainStatusBar()
	@Override
	public IMainStatusBar getMainStatusBar() {
		if (mainWindow == null) {
			return null;
		}
		return mainWindow.getMainStatusBar();
	}

	@Override
	public UISWTInstance getUISWTInstance() {
		UISWTInstanceImpl impl = mainWindow.getUISWTInstanceImpl();
		if (impl == null) {
			Debug.out("No uiswtinstanceimpl");
		}
		return impl;
	}

	// @see UIFunctions#viewURL(java.lang.String, java.lang.String, java.lang.String)
	@Override
	public void viewURL(String url, String target, String sourceRef) {
		viewURL(url, target, 0, 0, true, false);
	}

	@Override
	public boolean viewURL(final String url, final String target, final int w,
	                       final int h, final boolean allowResize, final boolean isModal) {

		 mainWindow.getShell().getDisplay().syncExec(new AERunnable() {
			@Override
			public void runSupport() {
				String realURL = url;
				if (target == null) {
					PluginHTTPProxy proxy = null;

					try{
						if ( PROXY_VIEW_URL ){

							proxy = AEProxyFactory.getPluginHTTPProxy( "viewURL", new URL( realURL ), true );

							realURL = proxy.proxifyURL( realURL );
						}

						BrowserWindow window = new BrowserWindow( mainWindow.getShell(), realURL,
							w, h, allowResize, isModal);

						window.waitUntilClosed();

					}catch( Throwable e ){

					}finally{

						if ( proxy != null ){

							proxy.destroy();
						}
					}
				} else {
					showURL(realURL, target);
				}
			}
		});
		return true;
	}

	@Override
	public boolean viewURL(final String url, final String target, final double w,
	                       final double h, final boolean allowResize, final boolean isModal) {

		 mainWindow.getShell().getDisplay().syncExec(new AERunnable() {
			@Override
			public void runSupport() {
				String realURL = url;
				if (target == null) {
					PluginHTTPProxy proxy = null;

					try{
						if ( PROXY_VIEW_URL ){

							proxy = AEProxyFactory.getPluginHTTPProxy( "viewURL", new URL( realURL ), true );

							realURL = proxy.proxifyURL( realURL );
						}

						BrowserWindow window = new BrowserWindow( mainWindow.getShell(), realURL,
							w, h, allowResize, isModal);
						window.waitUntilClosed();

					}catch( Throwable e ){

					}finally{

						if ( proxy != null ){

							proxy.destroy();
						}
					}
				} else {
					showURL(realURL, target);
				}
			}
		});
		return true;
	}

	/**
	 * @param url
	 * @param target
	 */

	private void showURL(final String url, String target) {

		if ("_blank".equalsIgnoreCase(target)) {
			Utils.launch(url);
			return;
		}

		if (target.startsWith("tab-")) {
			target = target.substring(4);
		}

		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();

		/*
		if (MultipleDocumentInterface.SIDEBAR_SECTION_PLUS.equals(target)) {
			mdi.showEntryByID(target);
			return;
		}
		*/

		// Note; We don't setSourceRef on ContentNetwork here like we do
		// everywhere else because the source ref should already be set
		// by the caller
		if (mdi == null || !mdi.showEntryByID(target)) {
			Utils.launch(url);
			return;
		}

		MdiEntry entry = mdi.getEntry(target);
		entry.addListener(new MdiEntryOpenListener() {

			@Override
			public void mdiEntryOpen(MdiEntry entry) {
				entry.removeListener(this);

				mainWindow.setVisible( true, true );

				if (!(entry instanceof SideBarEntrySWT)) {
					return;
				}
				SideBarEntrySWT entrySWT = (SideBarEntrySWT) entry;

				SWTSkinObjectBrowser soBrowser = SWTSkinUtils.findBrowserSO(entrySWT.getSkinObject());

				if (soBrowser != null) {
					//((SWTSkinObjectBrowser) skinObject).getBrowser().setVisible(false);
					if (url == null || url.length() == 0) {
						soBrowser.restart();
					} else {
						soBrowser.setURL(url);
					}
				}
			}
		});
	}

	// @see UIFunctions#promptUser(java.lang.String, java.lang.String, java.lang.String[], int, java.lang.String, java.lang.String, boolean, int)
	@Override
	public void promptUser(String title, String text, String[] buttons,
	                       int defaultOption, String rememberID, String rememberText,
	                       boolean rememberByDefault, int autoCloseInMS, UserPrompterResultListener l) {
		MessageBoxShell.open(getMainShell(), title, text, buttons,
				defaultOption, rememberID, rememberText, rememberByDefault,
				autoCloseInMS, l);
	}

	// @see UIFunctions#getUserPrompter(java.lang.String, java.lang.String, java.lang.String[], int)
	@Override
	public UIFunctionsUserPrompter getUserPrompter(String title, String text,
	                                               String[] buttons, int defaultOption) {

		MessageBoxShell mb = new MessageBoxShell(title, text, buttons,
				defaultOption);
		return mb;
	}

	@Override
	public boolean isGlobalTransferBarShown() {
		if (!CoreFactory.isCoreRunning()) {
			return false;
		}
		return AllTransfersBar.getManager().isOpen(
				CoreFactory.getSingleton().getGlobalManager());
	}

	@Override
	public void showGlobalTransferBar() {
		AllTransfersBar.open(getMainShell());
	}

	@Override
	public void closeGlobalTransferBar() {
		AllTransfersBar.closeAllTransfersBar();
	}

	@Override
	public void refreshTorrentMenu() {
		if (!isTorrentMenuVisible || Utils.isDisplayDisposed()) {
			return;
		}
		try {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					final MenuItem torrentItem = MenuFactory.findMenuItem(
							mainWindow.getMainMenu().getMenu(IMenuConstants.MENU_ID_MENU_BAR),
							MenuFactory.MENU_ID_TORRENT, false);

					if (null != torrentItem) {

						DownloadManager[] dms = SelectedContentManager.getDMSFromSelectedContent();

						final DownloadManager[] dm_final = dms;
						final boolean detailed_view_final = false;
						if (null == dm_final) {
							torrentItem.setEnabled(false);
						} else {
							TableView<?> tv = SelectedContentManager.getCurrentlySelectedTableView();

							torrentItem.getMenu().setData("TableView", tv);
							torrentItem.getMenu().setData("downloads", dm_final);
							torrentItem.getMenu().setData("is_detailed_view",
									Boolean.valueOf(detailed_view_final));
							torrentItem.setEnabled(true);
						}
					}
				}
			});

		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "refreshTorrentMenu", e));
		}
	}

	@Override
	public IMainMenu createMainMenu(Shell shell) {
		IMainMenu menu;
		if (Utils.isAZ2UI()) {
			menu = new MainMenuV2(shell);
		} else {
			menu = new MainMenuV3(skin, shell);
		}
		return menu;
	}

	public SWTSkin getSkin() {
		return skin;
	}

	public void setSkin(SWTSkin skin) {
		this.skin = skin;
	}

	@Override
	public IMainWindow getMainWindow() {
		return mainWindow;
	}

	// @see UIFunctions#getUIUpdater()
	@Override
	public UIUpdater getUIUpdater() {
		return UIUpdaterSWT.getInstance();
	}

	// @see UIFunctionsSWT#closeAllDetails()
	@Override
	public void closeAllDetails() {
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
		if (mdi == null) {
			return;
		}
		MdiEntry[] sideBarEntries = mdi.getEntries();
		for (MdiEntry entry : sideBarEntries) {
			String id = entry.getViewID();
			if (id.startsWith("DMDetails_")) {
				mdi.closeEntryByID(id);
			}
		}

	}

	// @see UIFunctionsSWT#hasDetailViews()
	@Override
	public boolean hasDetailViews() {
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
		if (mdi == null) {
			return false;
		}

		MdiEntry[] sideBarEntries = mdi.getEntries();
		for (int i = 0; i < sideBarEntries.length; i++) {
			MdiEntry entry = sideBarEntries[i];
			String id = entry.getViewID();
			if (id != null && id.startsWith("DMDetails_")) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void
	performAction(
		int 					action_id,
		Object 					args,
		final actionListener 	listener )
	{
		if ( action_id == ACTION_FULL_UPDATE ){

			FullUpdateWindow.handleUpdate((String)args, listener );

		}else if ( action_id == ACTION_UPDATE_RESTART_REQUEST ){

			String title = MessageText.getString("UpdateMonitor.messagebox.restart.title" );

			String text = MessageText.getString("UpdateMonitor.messagebox.restart.text" );

			bringToFront();

			boolean no_timeout = args instanceof Boolean && ((Boolean)args).booleanValue();

			int timeout = 180000;

			if ( no_timeout || !PluginInitializer.getDefaultInterface().getPluginManager().isSilentRestartEnabled()){

				timeout = -1;
			}

			MessageBoxShell messageBoxShell = new MessageBoxShell(title, text,
					new String[] {
				MessageText.getString("UpdateWindow.restart"),
				MessageText.getString("UpdateWindow.restartLater")
			}, 0);
			messageBoxShell.setAutoCloseInMS(timeout);
			messageBoxShell.setParent(getMainShell());
			messageBoxShell.setOneInstanceOf("UpdateMonitor.messagebox.");
			messageBoxShell.open( new UserPrompterResultListener() {
				@Override
				public void prompterClosed(int result) {
					listener.actionComplete(result == 0);
				}
			});
		}else{

			Debug.out( "Unknown action " + action_id );
		}
	}

	// @see UIFunctionsSWT#showCoreWaitDlg()
	@Override
	public Shell showCoreWaitDlg() {
		final SkinnedDialog closeDialog = new SkinnedDialog(
				"skin3_dlg_coreloading", "coreloading.body", SWT.TITLE | SWT.BORDER
				| SWT.APPLICATION_MODAL);

		closeDialog.setTitle(MessageText.getString("dlg.corewait.title"));
		SWTSkin skin = closeDialog.getSkin();
		SWTSkinObjectButton soButton = (SWTSkinObjectButton) skin.getSkinObject("close");

		final SWTSkinObjectText soWaitTask = (SWTSkinObjectText) skin.getSkinObject("task");

		final SWTSkinObject soWaitProgress = skin.getSkinObject("progress");
		if (soWaitProgress != null) {
			soWaitProgress.getControl().addPaintListener(new PaintListener() {
				@Override
				public void paintControl(PaintEvent e) {
					Control c = (Control) e.widget;
					Point size = c.getSize();
					e.gc.setBackground(ColorCache.getColor(e.display, "#23a7df"));
					Object data = soWaitProgress.getData("progress");
					if (data instanceof Long) {
						int waitProgress = ((Long) data).intValue();
						int breakX = size.x * waitProgress / 100;
						e.gc.fillRectangle(0, 0, breakX, size.y);
						e.gc.setBackground(ColorCache.getColor(e.display, "#cccccc"));
						e.gc.fillRectangle(breakX, 0, size.x - breakX, size.y);
					}
				}
			});
		}

		if (!CoreFactory.isCoreRunning()) {
			final Initializer initializer = Initializer.getLastInitializer();
			if (initializer != null) {
				initializer.addListener(new InitializerListener() {
					@Override
					public void reportPercent(final int percent) {
						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								if (soWaitProgress != null && !soWaitProgress.isDisposed()) {
									soWaitProgress.setData("progress", new Long(percent));
									soWaitProgress.getControl().redraw();
									soWaitProgress.getControl().update();
								}
							}
						});
						if (percent > 100) {
							initializer.removeListener(this);
						}
					}

					@Override
					public void reportCurrentTask(String currentTask) {
						if (soWaitTask != null && !soWaitTask.isDisposed()) {
							soWaitTask.setText(currentTask);
						}
					}
				});
			}
		}

		if (soButton != null) {
			soButton.addSelectionListener(new ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					closeDialog.close();
				}
			});
		}

		closeDialog.addCloseListener(new SkinnedDialogClosedListener() {
			@Override
			public void skinDialogClosed(SkinnedDialog dialog) {
			}
		});

		closeDialog.open();
		return closeDialog.getShell();
	}

	/**
	 * @param sSearchText
	 */
	//TODO : Tux Move to utils? Could you also add a "mode" or something that would be added to the url
	// eg: &subscribe_mode=true
	@Override
	public void doSearch(final String sSearchText) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				doSearch(sSearchText, false);
			}
		});
	}

	@Override
	public void
	doSearch(
		String sSearchText,
		boolean toSubscribe)
	{
		if (sSearchText.length() == 0) {
			return;
		}

		if ( checkForSpecialSearchTerm( sSearchText )){

			return;
		}

		SearchHandler.handleSearch( sSearchText, toSubscribe );
	}

	private static boolean
	checkForSpecialSearchTerm(
		String		str )
	{
		str = str.trim();

		String hit = UrlUtils.parseTextForURL( str, true, true );

		if ( hit == null ){

			try{
				File f = new File( str );

				if ( f.isFile()){

					String name = f.getName().toLowerCase();

					if ( name.endsWith( ".torrent" ) || VuzeFileHandler.isAcceptedVuzeFileName( name )){

						UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();

						if ( uif != null ){

			    			uif.openTorrentOpenOptions(
			    				null, null, new String[] { f.getAbsolutePath() },
			    				false, false);

			    			return( true );
						}
					}
				}
			}catch( Throwable e ){
			}

			return( false );
		}

		try{
				// if it is just a trivial URL (no path/query) then most unlikely to refer to
				// a torrent file so just launch the URL

			URL hit_url = new URL( hit );

			URL url;

			String protocol = hit_url.getProtocol().toLowerCase( Locale.US );
			
			if ( protocol.equals( "tor" ) || protocol.equals( "i2p")){

				url = new URL( hit.substring( 4 ));

			}else{

				url = hit_url;
			}

			String path = url.getPath();

			if (( path.length() == 0 || path.equals( "/" )) && url.getQuery() == null ){

				Utils.launch( hit_url.toExternalForm());

				return( true );
			}
		}catch( Throwable e ){
		}

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();

		new FileDownloadWindow( uiFunctions.getMainShell(), hit, null, null, true );

		return( true );
	}


	@Override
	public void promptForSearch() {
		SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow("Button.search", "search.dialog.text");
		entryWindow.prompt(new UIInputReceiverListener() {
			@Override
			public void UIInputReceiverClosed(UIInputReceiver receiver) {
				if (receiver.hasSubmittedInput()) {
					doSearch(receiver.getSubmittedInput());
				}
			}
		});
	}

	@Override
	public MultipleDocumentInterface getMDI() {
		return (MultipleDocumentInterface) SkinViewManager.getByViewID(SkinConstants.VIEWID_MDI);
	}

	@Override
	public MultipleDocumentInterfaceSWT getMDISWT() {
		return (MultipleDocumentInterfaceSWT) SkinViewManager.getByViewID(SkinConstants.VIEWID_MDI);
	}

	/**
	 *
	 * @param keyPrefix
	 * @param details may not get displayed
	 * @param textParams
	 */
	@Override
	public void showErrorMessage(final String keyPrefix,
	                             final String details, final String[] textParams) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				Shell mainShell = getMainShell();
				if (mainShell.getDisplay().getActiveShell() != null
						|| mainShell.isFocusControl()) {
					new MessageSlideShell(Display.getCurrent(), SWT.ICON_ERROR,
							keyPrefix, details, textParams, -1);
				} else {
					MessageBoxShell mb = new MessageBoxShell(SWT.OK, keyPrefix,
							textParams);
					mb.open(null);
				}
			}
		});
	}

	@Override
	public void forceNotify(final int iconID, final String title, final String text,
	                        final String details, final Object[] relatedObjects, final int timeoutSecs) {

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				int swtIconID = SWT.ICON_INFORMATION;
				switch (iconID) {
					case STATUSICON_WARNING:
						swtIconID = SWT.ICON_WARNING;
						break;

					case STATUSICON_ERROR:
						swtIconID = SWT.ICON_ERROR;
						break;
				}

				new MessageSlideShell(Utils.getDisplay(), swtIconID,
						title, text, details, relatedObjects, timeoutSecs);

			}
		});
	}

	@Override
	public void
	installPlugin(
		String 				plugin_id,
		String				resource_prefix,
		actionListener		listener )
	{
		new SimplePluginInstaller( plugin_id, resource_prefix, listener );
	}

	@Override
	public UIToolBarManager getToolBarManager() {
		return UIToolBarManagerImpl.getInstance();
	}

	@Override
	public void
	runOnUIThread(
		final String			ui_type,
		final Runnable		runnable )
	{
		if ( ui_type.equals(UIInstance.UIT_SWT) ){

			Utils.execSWTThread( runnable );

		}else{

			runnable.run();
		}
	}

	@Override
	public boolean
	isUIThread()
	{
		return( Utils.isSWTThread());
	}
	
	@Override
	public boolean
	isProgramInstalled(
		String extension,
		String name )
	{
		if ( !extension.startsWith( "." )){

			extension = "." + extension;
		}

		Program program = Program.findProgram( extension );

		return( program == null ? false:(program.getName().toLowerCase(Locale.US)
			.contains(name.toLowerCase(Locale.US))));
	}

	@Override
	public void
	openRemotePairingWindow()
	{
		RemotePairingWindow.open();
	}

	@Override
	public void
	playOrStreamDataSource(
		Object 		ds,
		String 		referal,
		boolean 	launch_already_checked,
		boolean 	complete_only )
	{
		TorrentListViewsUtils.playOrStreamDataSource( ds, referal, launch_already_checked, complete_only );
	}

	@Override
	public void
	setHideAll(
		boolean hidden )
	{
		mainWindow.setHideAll( hidden );
	}
	
	@Override
	public boolean getHideAll(){
		return( mainWindow.getHideAll());
	}

	@Override
	public boolean
	addTorrentWithOptions(
		boolean 			force,
		TorrentOpenOptions 	torrentOptions)
	{
		Map<String, Object> add_options = new HashMap<>();

		add_options.put( UIFunctions.OTO_FORCE_OPEN, force );

		return( addTorrentWithOptions( torrentOptions, add_options ));
	}

	// TODO: move to TorrentUtils, with calls to UIF when needed
	@Override
	public boolean
	addTorrentWithOptions(
		final TorrentOpenOptions 	torrentOptions,
		Map<String, Object> 		addOptions )
	{
		Boolean is_silent = (Boolean)addOptions.get( UIFunctions.OTO_SILENT );

		if ( is_silent == null ){

			is_silent = UIFunctions.OTO_SILENT_DEFAULT;
		}

		if ( CoreFactory.isCoreRunning()){

			Core core = CoreFactory.getSingleton();

			GlobalManager gm = core.getGlobalManager();

				// Check if torrent already exists in gm, and add if not

			TOTorrent torrent = torrentOptions.getTorrent();

			DownloadManager existingDownload = gm.getDownloadManager( torrent );

			if ( existingDownload != null ){

					// if existing is metadata download then we need to remove it before continuing (use case is that user has a long running metadata download and in
					// the meantime manually obtains the torrent from elsewhere and adds it
				
				if ( existingDownload.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
					
					try{
						gm.removeDownloadManager( existingDownload, true, true );
						
						existingDownload = null;
						
					}catch( Throwable e){
						
						Debug.out( e );
					}
				}
			}
				
			if ( existingDownload != null ){
				
				boolean delete_delegated = false;
				
				if ( !is_silent ){

					final String fExistingName = existingDownload.getDisplayName();
					
					final DownloadManager fExistingDownload = existingDownload;

					fExistingDownload.fireGlobalManagerEvent(GlobalManagerEvent.ET_REQUEST_ATTENTION);
					
					TOTorrent new_torrent 		= torrentOptions.getTorrent();
					TOTorrent existing_torrent	= fExistingDownload.getTorrent();
							
					boolean can_merge = TorrentUtils.canMergeAnnounceURLs( new_torrent, existing_torrent);

					boolean can_merge_private = can_merge && new_torrent.getPrivate();
					
					delete_delegated = can_merge_private;
					
					if ( can_merge_private ){
						
							// we have to modify the new, private torrent, for this to work. (can't do this if it isn't private as support for
							// hash override isn't supported in DHT, magnet xfer,...
						
						String text_base = MessageText.getString(MSG_ALREADY_EXISTS
								+ ".text", new String[] {
							":" + torrentOptions.sOriginatingLocation,
							fExistingName,
							MessageText.getString(MSG_ALREADY_EXISTS_NAME),
						});

						String text = text_base + "\n\n" + MessageText.getString("openTorrentWindow.mb.alreadyExists.add.dup");

						UIFunctionsUserPrompter promptSepDL = getUserPrompter(
							MessageText.getString(MSG_ALREADY_EXISTS + ".title"), text,
							new String[]{
								MessageText.getString("Button.yes"),
								MessageText.getString("Button.no")
							}, 1);

						promptSepDL.open(result -> {
							if ( result == 0 ){ // Yes

								try{
									File tmp_dir = AETemporaryFileHandler.getTempDirectory();
									
									byte[] hash_ov = RandomUtils.nextSecureHash();
									
									String	hash_str = ByteFormatter.encodeString( hash_ov,  0, 4 );
									
									String new_file_name;
									
									if ( torrentOptions.getTorrentFile() == null ){
										
										new_file_name = hash_str;
										
									}else{
										
										new_file_name = new File( torrentOptions.getTorrentFile()).getName();
										
										int pos = new_file_name.lastIndexOf( "." );
										
										if ( pos != -1 ){
											
											new_file_name = new_file_name.substring( 0, pos );
										}
										
										new_file_name += "_" + hash_str;
									}
									
									new_file_name += ".torrent";
									
									File new_file = new File( tmp_dir, new_file_name );
									
									byte[] existing_hash = torrent.getHash();
									
									torrent.setHashOverride( hash_ov );
									
									TorrentUtils.setOriginalHash( torrent, existing_hash );
									
									torrent.serialiseToBEncodedFile( new_file );
									
									torrentOptions.setTorrentFile( new_file.getAbsolutePath());
									
									addTorrentWithOptionsSupport( torrentOptions, addOptions, false );
									
								}catch( Throwable e ){
									
									Debug.out( e );
								}
							}else if ( existing_torrent.getPrivate()){
									
								String text1 = text_base + "\n\n" + MessageText.getString("openTorrentWindow.mb.alreadyExists.add.replace");

								UIFunctionsUserPrompter promptTrackers = getUserPrompter(
									MessageText.getString(MSG_ALREADY_EXISTS + ".title"), text1,
									new String[]{
										MessageText.getString("Button.yes"),
										MessageText.getString("Button.no")
									}, 1);

								promptTrackers.open(result1 -> {
									if ( result1 == 0 ){ // Yes
										
										List<List<String>> new_trackers = TorrentUtils.announceGroupsToList( new_torrent );
										
										TorrentUtils.listToAnnounceGroups( new_trackers, existing_torrent );
									}
									
									torrentOptions.deleteTorrent();
								});
							}else{
								
								torrentOptions.deleteTorrent();
							}
						});
														
					}else{
						
						long	existed_for = SystemTime.getCurrentTime() - fExistingDownload.getCreationTime();

						if (getVisibilityState() != VS_ACTIVE	&& !can_merge) {


								// seems we're getting some double additions (linux user reported but could be a general issue) so
								// don't warn if the matching download has been added recently

							if ( existed_for > 15*1000 ){
								
								forceNotify(STATUSICON_NONE,
										MessageText.getString(MSG_ALREADY_EXISTS + ".title"),
										MessageText.getString(MSG_ALREADY_EXISTS + ".text",
												new String[] {
													":" + torrentOptions.sOriginatingLocation, // : prefix is deliberate to disable click on ref in message as might be an unwanted action
													fExistingName,
													MessageText.getString(MSG_ALREADY_EXISTS_NAME),
								}), null, new Object[] {
									fExistingDownload
								}, -1);

							}
						} else {

							if (can_merge) {

								String text = MessageText.getString(MSG_ALREADY_EXISTS
										+ ".text", new String[] {
									":" + torrentOptions.sOriginatingLocation,
									fExistingName,
									MessageText.getString(MSG_ALREADY_EXISTS_NAME),
								});

								text += "\n\n"
										+ MessageText.getString("openTorrentWindow.mb.alreadyExists.merge");

								UIFunctionsUserPrompter promptTrackers = getUserPrompter(
									MessageText.getString(MSG_ALREADY_EXISTS + ".title"), text,
									new String[]{
										MessageText.getString("Button.yes"),
										MessageText.getString("Button.no")
									}, 0);

								promptTrackers.open(result -> {
									if (result == 0) { // Yes

										TorrentUtils.mergeAnnounceURLs(
												torrentOptions.getTorrent(),
												fExistingDownload.getTorrent());
									}
								});
							} else {

								if ( existed_for > 15*1000 ){

									TorrentUIUtilsV3.showTorrentAlreadyAdded(
										":" + torrentOptions.sOriginatingLocation,
										fExistingName );
								}
							}
						}
					}
				}

				if ( !delete_delegated ){
					
					torrentOptions.deleteTorrent();
				}
				
				return( true );

			}else{

				try{
					final DownloadStub archived = core.getPluginManager().getDefaultPluginInterface().getDownloadManager().lookupDownloadStub( torrent.getHash());

					if ( archived != null ){

						if ( is_silent ){

								// restore it for them

							archived.destubbify();

							torrentOptions.deleteTorrent();

							return( true );

						}else{

							String existingName = archived.getName();

							if (getVisibilityState() != VS_ACTIVE) {

								forceNotify(STATUSICON_NONE,
										MessageText.getString(
												"OpenTorrentWindow.mb.inArchive.title"),
										MessageText.getString(
												"OpenTorrentWindow.mb.inArchive.text", new String[] {
													existingName
								}), null, new Object[0], -1);

							}else{

								getUserPrompter(
										MessageText.getString(
												"OpenTorrentWindow.mb.inArchive.title"),
										MessageText.getString(
												"OpenTorrentWindow.mb.inArchive.text", new String[] {
													existingName
								}), new String[] {
									MessageText.getString("Button.ok"),
								}, 0).open(null);
							}

							return( true );
						}

					}
				}catch( Throwable e ){
					Debug.out( e );
				}

				if ( !is_silent ){

					try{
						DownloadHistoryManager dlm = (DownloadHistoryManager)core.getGlobalManager().getDownloadHistoryManager();

						final long[] existing = dlm.getDates( torrentOptions.getTorrent().getHash(), true);

						if ( existing != null ){

							long	redownloaded = existing[3];

							if ( SystemTime.getCurrentTime() - redownloaded > 60*10*1000 ){

								if ( getVisibilityState() != VS_TRAY_ONLY){

									forceNotify(STATUSICON_NONE,
										MessageText.getString(
											"OpenTorrentWindow.mb.inHistory.title"),
										MessageText.getString(
											"OpenTorrentWindow.mb.inHistory.text", new String[] {
												torrentOptions.getTorrentName(),
												new SimpleDateFormat().format( new Date( existing[0] ))
											}), null, new Object[0], -1);

								}
							}
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}
		
		return( addTorrentWithOptionsSupport( torrentOptions, addOptions, is_silent ));
	}
	
	private boolean
	addTorrentWithOptionsSupport(
		TorrentOpenOptions 		torrentOptions,
		Map<String, Object> 	addOptions,
		boolean					is_silent )

	{
		Boolean force = (Boolean)addOptions.get( UIFunctions.OTO_FORCE_OPEN );

		if ( force == null ){

			force = UIFunctions.OTO_FORCE_OPEN_DEFAULT;
		}

		if ( !force ){

			TOTorrent torrent = torrentOptions.getTorrent();

			boolean is_featured = torrent != null && PlatformTorrentUtils.isFeaturedContent( torrent );

			String showAgainMode = COConfigurationManager.getStringParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS);

			if (is_featured || is_silent
					|| (ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_NEVER.equals(
							showAgainMode)
							|| (ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_MANY.equals(
									showAgainMode) && torrentOptions.getFiles() != null
									&& torrentOptions.getFiles().length == 1))) {

					// we're about to silently add the download - ensure that it is going to be saved somewhere vaguely sensible
					// as the current save location is simply taken from the 'default download' config which can be blank (for example)

				boolean	looks_good = false;

				String save_loc = torrentOptions.getParentDir().trim();

				if ( save_loc.length() == 0 ){

						// blank :(

				}else if ( save_loc.startsWith( "." )){

						// relative to who knows where
				}else{

					File f = new File( save_loc );

					if ( !f.exists()){

						f.mkdirs();
					}

					if ( f.isDirectory() && FileUtil.canWriteToDirectory( f )){

						if ( !f.equals(AETemporaryFileHandler.getTempDirectory())){

							looks_good = true;
						}
					}
				}

				if ( looks_good ){

					TorrentManagerImpl t_man = TorrentManagerImpl.getSingleton();

					t_man.optionsAdded( torrentOptions );

					t_man.optionsAccepted( torrentOptions );

					boolean ok = torrentOptions.addToDownloadManager();

					t_man.optionsRemoved( torrentOptions );

					return( ok );
				}

				torrentOptions.setParentDir( "" );

				if ( is_silent ){

					return( false );

				}else{

					UIFunctionsUserPrompter mb = getUserPrompter(
						MessageText.getString(
							"OpenTorrentWindow.mb.invaliddefsave.title"),
						MessageText.getString(
							"OpenTorrentWindow.mb.invaliddefsave.text", new String[]{
								save_loc
							}), new String[]{
							MessageText.getString("Button.ok"),
						}, 0);
					mb.setIconResource("error");
					mb.open(res -> OpenTorrentOptionsWindow.addTorrent( torrentOptions ));
				}

				return( true );
			}
		}

		if ( is_silent ){

			return( false );

		}else{

			OpenTorrentOptionsWindow.addTorrent( torrentOptions );

			return( true );
		}
	}

	/* (non-Javadoc)
	 * @see UIFunctionsSWT#openTorrentOpenOptions(org.eclipse.swt.widgets.Shell, java.lang.String, java.lang.String[], boolean, boolean, boolean)
	 */
	@Override
	public void openTorrentOpenOptions(Shell shell, String sPathOfFilesToOpen,
	                                   String[] sFilesToOpen, boolean defaultToStopped, boolean forceOpen) {

		TorrentOpenOptions torrentOptions = new TorrentOpenOptions( null );
		if (defaultToStopped) {
			torrentOptions.setStartMode( TorrentOpenOptions.STARTMODE_STOPPED );
		}
		if (sFilesToOpen == null) {
			new OpenTorrentWindow(shell);
		} else {
			// with no listener, Downloader will open options window if user configured
			TorrentOpener.openTorrentsFromStrings(torrentOptions, shell,
					sPathOfFilesToOpen, sFilesToOpen, null, null, forceOpen);
		}
	}

	@Override
	public void
	openTorrentOpenOptions(
		Shell 					shell,
		String 					sPathOfFilesToOpen,
		String[] 				sFilesToOpen,
		Map<String,Object>		options )
	{
		Boolean _defaultToStopped 	= (Boolean)options.get( UIFunctions.OTO_DEFAULT_TO_STOPPED );
		boolean	defaultToStopped	= _defaultToStopped!=null?_defaultToStopped:UIFunctions.OTO_DEFAULT_TO_STOPPED_DEFAULT;

		Boolean _hideErrors		 	= (Boolean)options.get( UIFunctions.OTO_HIDE_ERRORS );
		boolean	hideErrors			= _hideErrors!=null?_hideErrors:UIFunctions.OTO_HIDE_ERRORS_DEFAULT;

		TorrentOpenOptions torrentOptions = new TorrentOpenOptions( options );
		if (defaultToStopped) {
			torrentOptions.setStartMode( TorrentOpenOptions.STARTMODE_STOPPED );
		}
		torrentOptions.setHideErrors( hideErrors );

		if (sFilesToOpen == null) {
			new OpenTorrentWindow(shell);
		} else {
			// with no listener, Downloader will open options window if user configured

			Boolean _forceOpen 	= (Boolean)options.get( UIFunctions.OTO_FORCE_OPEN );
			boolean	forceOpen	= _forceOpen!=null?_forceOpen:UIFunctions.OTO_FORCE_OPEN_DEFAULT;

			TorrentOpener.openTorrentsFromStrings(
					torrentOptions, shell,
					sPathOfFilesToOpen, sFilesToOpen, null, null, forceOpen );
		}
	}

	/* (non-Javadoc)
	 * @see UIFunctionsSWT#openTorrentWindow()
	 */
	@Override
	public void openTorrentWindow() {
		new OpenTorrentWindow(Utils.findAnyShell());
	}

	// @see UIFunctions#showCreateTagDialog()
	@Override
	public void showCreateTagDialog(TagReturner tagReturner) {
		TagUIUtilsV3.showCreateTagDialog(tagReturner);
	}

	@Override
	public void tableColumnAddedListeners(TableColumnImpl tableColumn, Object listeners) {
		if (listeners instanceof TableCellSWTPaintListener) {
				tableColumn.addCellOtherListener("SWTPaint", listeners);
		}
	}

	@Override
	public void copyToClipboard(String text) {
		ClipboardCopy.copyToClipBoard(text);
	}

	@Override
	public void showInExplorer(File f) {
		Utils.execSWTThread(() -> ManagerUtils.open(f));
	}

	@Override
	public void showText(String title, String content){
		Utils.execSWTThread(() ->{
			new TextViewerWindow( title, null, content, false  );
		});	
	}	
	
	public void dispose() {
		COConfigurationManager.removeParameterListener(
				"show_torrents_menu", paramShowTorrentsMenuListener);
	}
}
