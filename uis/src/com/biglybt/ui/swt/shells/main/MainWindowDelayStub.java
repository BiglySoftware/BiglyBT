/*
 * Created on Sep 13, 2012
 * Created by Paul Gardner
 *
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
import java.util.Map;

import com.biglybt.core.*;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.ui.UIManagerEventListener;
import com.biglybt.ui.*;
import com.biglybt.ui.common.table.impl.TableColumnImpl;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.pifimpl.*;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.util.*;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.UIExitUtilsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.*;
import com.biglybt.ui.swt.minibar.AllTransfersBar;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.systray.SystemTraySWT;

import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

public class
MainWindowDelayStub
	implements MainWindow
{
	private Display			display;
	private IUIIntializer initialiser;

	private Shell			shell;

	private Core core;
	private AESemaphore		core_sem = new AESemaphore("");

	private volatile MainWindow		main_window;

	private SystemTraySWT	swt_tray;

	private volatile UIFunctionsSWT delayed_uif = new UIFunctionsSWTImpl();

	private UIManagerEventListener ui_listener = 
			new UIManagerEventListener()
			{
				public boolean
				eventOccurred(
					UIManagerEvent	event )
				{
					boolean show = false;
					
					if ( event.getType() == UIManagerEvent.ET_HIDE_ALL ){
						
						show = !(Boolean)event.getData();
						
					}else if ( event.getType() == UIManagerEvent.ET_HIDE_ALL_TOGGLE ){
						
						show = true;
					}
					
					if ( show ){
						
						new AEThread2( "show ")
						{
							public void 
							run()
							{
								checkMainWindow();
							}
						}.start();
					}
					
					return( false );
				}
			};
			
	public
	MainWindowDelayStub(
		Core 					_core,
		Display 				_display,
		IUIIntializer			_uiInitializer )
	{
		core		= _core;
		display		= _display;
		initialiser	= _uiInitializer;

		init();

		core_sem.releaseForever();
	}

	public
	MainWindowDelayStub(
		Display 		_display,
		IUIIntializer 	_uiInitializer )
	{
		display		= _display;
		initialiser	= _uiInitializer;

		init();
	}

	private void
	init()
	{
		final AESemaphore sem = new AESemaphore( "shell:create" );

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					try{
						shell = new Shell(display, SWT.SHELL_TRIM);

						UIFunctionsManagerSWT.setUIFunctions( delayed_uif );

						boolean bEnableTray = COConfigurationManager.getBooleanParameter("Enable System Tray");

						if ( bEnableTray ){

							swt_tray = SystemTraySWT.getTray();
						}

						MainHelpers.initTransferBar();

						if ( initialiser != null ){

							initialiser.initializationComplete();

							initialiser.abortProgress();
						}

						AERunStateHandler.addListener(
							new AERunStateHandler.RunStateChangeListener()
							{
								private boolean	handled = false;

								@Override
								public void
								runStateChanged(
									long run_state )
								{
									if ( AERunStateHandler.isDelayedUI() || handled ){

										return;
									}

									handled = true;

									checkMainWindow();
								}
							}, false );
						
						UIManager ui_manager = PluginInitializer.getDefaultInterface().getUIManager();
		
						ui_manager.addUIEventListener( ui_listener );
						
					}finally{

						sem.release();
					}
				}
			});

		sem.reserve();
	}

	private void
	log(
		String	str )
	{
		Debug.out( str );
	}

	@Override
	public void
	init(
		Core _core )
	{
		core	= _core;

		core_sem.releaseForever();
	}

	@Override
	public void disposeOnlyUI() {
		System.err.println("disposingOnlyUI? " + Debug.getCompressedStackTrace());
	}


	// barp

	private interface
	Fixup
	{
		public void
		fix(
			MainWindow mw );
	}

	private interface
	Fixup2
	{
		public Object
		fix(
			MainWindow mw );
	}

	private interface
	Fixup3
	{
		public void
		fix(
			UIFunctionsSWT uif );
	}

	private interface
	Fixup4
	{
		public Object
		fix(
			UIFunctionsSWT uif );
	}

	private void
	checkMainWindow()
	{
		boolean	activated = false;

		synchronized( this ){

			if ( main_window == null ){

				UIManager ui_manager = PluginInitializer.getDefaultInterface().getUIManager();
				
				ui_manager.removeUIEventListener( ui_listener );
				
				final AESemaphore wait_sem = new AESemaphore( "cmw" );

				CoreLifecycleListener listener =
					new CoreLifecycleAdapter()
					{
						@Override
						public void
						componentCreated(
							Core core,
							CoreComponent component )
						{
							if ( component instanceof UIFunctions){

								wait_sem.release();
							}
						}
					};

				core.addLifecycleListener( listener );

				main_window = new MainWindowImpl( core, null );

				if ( !wait_sem.reserve( 30*1000 )){

					Debug.out( "Gave up waiting for UIFunction component to be created" );
				}

				activated = true;
			}
		}

		if ( activated ){

			AERunStateHandler.setResourceMode( AERunStateHandler.RS_ALL_ACTIVE );
		}
	}

	private void
	fixup(
		Fixup	f )
	{
		core_sem.reserve();

		checkMainWindow();

		f.fix( main_window );
	}

	private Object
	fixup(
		Fixup2	f )
	{
		core_sem.reserve();

		checkMainWindow();

		return( f.fix( main_window ));
	}

	private void
	fixup(
		Fixup3	f )
	{
		core_sem.reserve();

		checkMainWindow();

		UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();

		if ( uif == delayed_uif ){

			Debug.out( "eh?" );

		}else{

			f.fix( uif );
		}
	}

	private Object
	fixup(
		Fixup4	f )
	{
		core_sem.reserve();

		checkMainWindow();

		UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();

		if ( uif == delayed_uif ){

			Debug.out( "eh?" );

			return( null );

		}else{

			return( f.fix( uif ));
		}
	}

		// toot

	@Override
	public Shell
	getShell()
	{
		return( shell );
	}

	@Override
	public IMainMenu
	getMainMenu()
	{
		return((IMainMenu)fixup( new Fixup2(){
			@Override
			public Object fix(MainWindow mw){ return( mw.getMainMenu()); }}));
	}


	@Override
	public IMainStatusBar
	getMainStatusBar()
	{
		if ( main_window != null ){

			return( main_window.getMainStatusBar());
		}

		return( null );
	}

	@Override
	public boolean
	isReady()
	{
		log( "isReady" );

		return( false );
	}

	@Override
	public void
	setVisible(
		final boolean visible,
		final boolean tryTricks )
	{
		fixup( new Fixup(){
			@Override
			public void fix(MainWindow mw){ mw.setVisible( visible, tryTricks ); }});
	}

	@Override
	public UISWTInstanceImpl
	getUISWTInstanceImpl()
	{
		log( "getUISWTInstanceImpl" );

		return( null );
	}

	@Override
	public void
	setSelectedLanguageItem()
	{
		log( "setSelectedLanguageItem" );
	}

	@Override
	public boolean
	dispose(
		boolean for_restart )
	{
		if ( main_window != null ){

			return( main_window.dispose(for_restart));
		}

		log( "dispose" );

		UIExitUtilsSWT.uiShutdown();

		if ( swt_tray != null ){

			swt_tray.dispose();
		}

		try{
			AllTransfersBar transfer_bar = AllTransfersBar.getBarIfOpen(core.getGlobalManager());

			if ( transfer_bar != null ){

				transfer_bar.forceSaveLocation();
			}
		}catch( Exception ignore ){
		}

		SWTThread instance = SWTThread.getInstance();
		if (instance != null && !instance.isTerminated()) {
			Utils.getOffOfSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					SWTThread instance = SWTThread.getInstance();
					if (instance != null && !instance.isTerminated()) {
						instance.getInitializer().stopIt(for_restart);
					}
				}
			});
		}

		return true;
	}



	@Override
	public boolean
	isVisible(
		int windowElement)
	{
		log( "isVisible" );

		return( false );
	}

	@Override
	public void
	setVisible(
		int 		windowElement,
		boolean 	value )
	{
		log( "setVisible" );
	}

	@Override
	public void
	setHideAll(
		boolean hide)
	{
		if ( !hide ){
			
			fixup( new Fixup(){
				@Override
				public void fix(MainWindow mw){ mw.setHideAll( hide ); }});
		};
	}

	public boolean
	getHideAll()
	{
		return( true );
	}
	
	@Override
	public Rectangle
	getMetrics(
		int windowElement)
	{
		log( "getMetrics" );

		return( null );
	}

	private class
	UIFunctionsSWTImpl
		implements UIFunctionsSWT
	{
		@Override
		public String
		getUIType()
		{
			return( UIInstance.UIT_SWT );
		}

		@Override
		public void
		bringToFront()
		{
			fixup( new Fixup3(){
				@Override
				public void fix(UIFunctionsSWT uif){ uif.bringToFront(); }});
		}

		@Override
		public void
		bringToFront(
			final boolean tryTricks)
		{
			fixup( new Fixup3(){
				@Override
				public void fix(UIFunctionsSWT uif){ uif.bringToFront( tryTricks ); }});
		}

		@Override
		public int
		getVisibilityState()
		{
			UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();

			if ( uif != null && uif != this ){

				return( uif.getVisibilityState());
			}

			return( VS_TRAY_ONLY );
		}

		@Override
		public void
		runOnUIThread(
			final String			ui_type,
			final Runnable		runnable )
		{
			fixup( new Fixup3(){
				@Override
				public void fix(UIFunctionsSWT uif){ uif.runOnUIThread( ui_type, runnable ); }});
		}

		@Override
		public boolean
		isUIThread()
		{
			return( Utils.isSWTThread());
		}
		
		@Override
		public void
		refreshLanguage()
		{
			log( "refreshLanguage" );
		}

		@Override
		public void
		refreshIconBar()
		{
			log( "refreshIconBar" );
		}

		@Override
		public void
		setStatusText(
			String key)
		{
			log( "setStatusText" );
		}

		@Override
		public void
		setStatusText(
			int statustype,
			String key,
			UIStatusTextClickListener l)
		{
			log( "setStatusText" );
		}
		
		@Override
		public Object 
		pushStatusText(
			String key )
		{
			log( "pushStatusText" );
			
			return( "" );
		}

		@Override
		public void 
		popStatusText(
			Object 	o,
			int		reason,
			String	message )
		{
			log( "popStatusText" );
		}
		
		@Override
		public boolean
		dispose(
			boolean for_restart )
		{
			return( MainWindowDelayStub.this.dispose( for_restart ));
		}

		@Override
		public boolean
		viewURL(
			String url,
			String target,
			int w,
			int h,
			boolean allowResize,
			boolean isModal)
		{
			log( "viewURL" );

			return( false );
		}

		@Override
		public boolean
		viewURL(
			String url,
			String target,
			double wPct,
			double hPct,
			boolean allowResize,
			boolean isModal)
		{
			log( "viewURL" );

			return( false );
		}

		@Override
		public void
		viewURL(
			String url,
			String target,
			String sourceRef)
		{
			log( "viewURL" );
		}


		@Override
		public UIFunctionsUserPrompter
		getUserPrompter(
			String 		title,
			String 		text,
			String[] 	buttons,
			int 		defaultOption)
		{
			log( "getUserPrompter" );

			return( null );
		}

		@Override
		public void
		promptUser(
			String title,
			String text,
			String[] buttons,
			int defaultOption,
			String rememberID,
			String rememberText,
			boolean bRememberByDefault,
			int autoCloseInMS,
			UserPrompterResultListener l)
		{
			log( "promptUser" );
		}


		@Override
		public UIUpdater
		getUIUpdater()
		{
			return( UIUpdaterSWT.getInstance());
		}


		@Override
		public void
		doSearch(
			String searchText )
		{
			log( "doSearch" );
		}

		@Override
		public void
		doSearch(
			String searchText,
			boolean toSubscribe )
		{
			log( "doSearch" );
		}

		@Override
		public void
		installPlugin(
			String			plugin_id,
			String			resource_prefix,
			actionListener	listener )
		{
			log( "installPlugin" );
		}


		@Override
		public void
		performAction(
			final int				action_id,
			final Object			args,
			final actionListener	listener )
		{
				// auto-update restart prompt (for example)

			fixup( new Fixup3(){
				@Override
				public void fix(UIFunctionsSWT uif){ uif.performAction( action_id, args, listener ); }});
		}

		@Override
		public MultipleDocumentInterface
		getMDI()
		{
			log( "getMDI" );

			return( null );
		}


		@Override
		public void
		forceNotify(
			int iconID,
			String title,
			String text,
			String details,
			Object[] relatedObjects,
			int timeoutSecs)
		{
			log( "forceNotify" );
		}


		@Override
		public Shell
		getMainShell()
		{
			return( shell );
		}


		@Override
		public void
		closeDownloadBars()
		{
		}

		@Override
		public boolean
		isGlobalTransferBarShown()
		{
			if (!CoreFactory.isCoreRunning()) {
				return false;
			}

			return AllTransfersBar.getManager().isOpen(
					CoreFactory.getSingleton().getGlobalManager());
		}

		@Override
		public void
		showGlobalTransferBar()
		{
			AllTransfersBar.open(getMainShell());
		}

		@Override
		public void
		closeGlobalTransferBar()
		{
			AllTransfersBar.closeAllTransfersBar();
		}


		@Override
		public UISWTView[]
		getPluginViews()
		{
			log( "getPluginViews" );

			return( new UISWTView[0] );
		}


		@Override
		public void
		openPluginView(
			UISWTViewBuilderCore builder,
			boolean bSetFocus)
		{
			log( "openPluginView" );
		}

		public void
		openPluginView(
			final UISWTViewCore view,
			final String name)
		{
			log( "openPluginView" );
		}

		@Override
		public UISWTInstance
		getUISWTInstance()
		{
			log( "getUISWTInstance" );

			return( null );
		}

		@Override
		public void
		refreshTorrentMenu()
		{
			log( "refreshTorrentMenu" );
		}

		@Override
		public IMainStatusBar
		getMainStatusBar()
		{
			return( null );
		}


		@Override
		public IMainMenu
		createMainMenu(
			final Shell shell)
		{
				// OSX Vuze->About menu

			return((IMainMenu)fixup( new Fixup4(){
				@Override
				public Object fix(UIFunctionsSWT uif){ return( uif.createMainMenu( shell )); }}));
		}

		@Override
		public IMainWindow
		getMainWindow()
		{
			return( MainWindowDelayStub.this );
		}

		@Override
		public void
		closeAllDetails()
		{
			log( "closeAllDetails" );
		}


		@Override
		public boolean
		hasDetailViews()
		{
			log( "hasDetailViews" );

			return( false );
		}

		@Override
		public Shell
		showCoreWaitDlg()
		{
			return( null );
		}

		@Override
		public boolean
		isProgramInstalled(
			final String extension,
			final String name )
		{
			return((Boolean)fixup( new Fixup4(){
				@Override
				public Object fix(UIFunctionsSWT uif){ return( uif.isProgramInstalled( extension, name )); }}));
		}

		@Override
		public MultipleDocumentInterfaceSWT
		getMDISWT()
		{
			log( "getMDISWT" );

			return( null );
		}

		@Override
		public void
		promptForSearch()
		{
			log( "promptForSearch" );
		}

		@Override
		public UIToolBarManager
		getToolBarManager()
		{
			log( "getToolBarManager" );

			return( null );
		}

		@Override
		public void
		openRemotePairingWindow()
		{
			log( "openRemotePairingWindow" );
		}

		@Override
		public void
		playOrStreamDataSource(
			Object 		ds,
			String 		referal,
			boolean 	launch_already_checked,
			boolean 	complete_only )
		{
			log( "playOrStreamDataSource" );
		}

		@Override
		public void
		setHideAll(
			boolean hidden)
		{
			log( "setHideAll" );
		}

		public boolean
		getHideAll()
		{
			return( true );
		}
		
		@Override
		public void
		showErrorMessage(
			String 		keyPrefix,
			String 		details,
			String[] 	textParams)
		{
			log( "showErrorMessage" );
		}

		// @see UIFunctions#showCreateTagDialog(com.biglybt.ui.swt.views.utils.TagUIUtils.TagReturner)
		@Override
		public void showCreateTagDialog(
				TagReturner tagReturner)
		{
			log( "showAddTagDialog" );
		}

		@Override
		public boolean
		addTorrentWithOptions(
			final boolean 				force,
			final TorrentOpenOptions 	torrentOptions)
		{
			return((Boolean)fixup( new Fixup4(){
				@Override
				public Object fix(UIFunctionsSWT uif){ return( uif.addTorrentWithOptions( force, torrentOptions )); }}));
		}

		@Override
		public boolean
		addTorrentWithOptions(
			final TorrentOpenOptions 	torrentOptions,
			final Map<String,Object>	options )
		{
			return((Boolean)fixup( new Fixup4(){
				@Override
				public Object fix(UIFunctionsSWT uif){ return( uif.addTorrentWithOptions( torrentOptions, options )); }}));
		}

		@Override
		public void
		openTorrentOpenOptions(
			final Shell shell,
			final String sPathOfFilesToOpen,
			final String[] sFilesToOpen,
			final boolean defaultToStopped,
			final boolean forceOpen)
		{
			fixup( new Fixup3(){
				@Override
				public void fix(UIFunctionsSWT uif){ uif.openTorrentOpenOptions( shell, sPathOfFilesToOpen, sFilesToOpen, defaultToStopped, forceOpen);}});
		}

		@Override
		public void
		openTorrentOpenOptions(
			final Shell 				shell,
			final String 				sPathOfFilesToOpen,
			final String[] 				sFilesToOpen,
			final Map<String, Object> 	options )
		{
			fixup( new Fixup3(){
				@Override
				public void fix(UIFunctionsSWT uif){ uif.openTorrentOpenOptions( shell, sPathOfFilesToOpen, sFilesToOpen, options);}});
		}

		@Override
		public void
		openTorrentWindow()
		{
			fixup( new Fixup3(){
				@Override
				public void fix(UIFunctionsSWT uif){ uif.openTorrentWindow(); }});
		}

		@Override
		public void tableColumnAddedListeners(TableColumnImpl tableColumn, Object listeners) {
			log("tableColumnAddedListeners");
		}

		@Override
		public void copyToClipboard(String text) {
			ClipboardCopy.copyToClipBoard(text);
		}

		@Override
		public void showInExplorer(File f) {
			ManagerUtils.open(f);
		}
		
		@Override
		public void showText(String title, String content){
			log( "showText" );

		}
	}
}
