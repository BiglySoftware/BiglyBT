/*
 * Created on May 29, 2006 2:13:41 PM
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
package com.biglybt.ui.swt;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Display;

import com.biglybt.core.*;
import com.biglybt.core.CoreOperationTask.ProgressCallback;
import com.biglybt.core.CoreOperationTask.ProgressCallbackAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.messenger.ClientMessageContext;
import com.biglybt.core.messenger.PlatformMessenger;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginEvent;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.installer.InstallablePlugin;
import com.biglybt.pif.installer.PluginInstallerListener;
import com.biglybt.pif.installer.StandardPlugin;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;
import com.biglybt.ui.IUIIntializer;
import com.biglybt.ui.InitializerListener;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.StartServer;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.auth.AuthenticatorWindow;
import com.biglybt.ui.swt.auth.CertificateTrustWindow;
import com.biglybt.ui.swt.auth.CryptoWindow;
import com.biglybt.ui.swt.browser.listener.ConfigListener;
import com.biglybt.ui.swt.browser.listener.DisplayListener;
import com.biglybt.ui.swt.browser.listener.TorrentListener;
import com.biglybt.ui.swt.browser.listener.VuzeListener;
import com.biglybt.ui.swt.browser.msg.MessageDispatcherSWT;
import com.biglybt.ui.swt.devices.DeviceManagerUI;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.*;
import com.biglybt.ui.swt.networks.SWTNetworkSelection;
import com.biglybt.ui.swt.pifimpl.UIToolBarManagerImpl;
import com.biglybt.ui.swt.progress.ProgressReportingManager;
import com.biglybt.ui.swt.progress.ProgressWindow;
import com.biglybt.ui.swt.search.SearchUI;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.shells.main.MainWindowFactory;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinPropertiesImpl;
import com.biglybt.ui.swt.subscriptions.SubscriptionManagerUI;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.update.UpdateMonitor;
import com.biglybt.ui.swt.updater2.SWTUpdateChecker;
import com.biglybt.ui.swt.utils.*;
import com.biglybt.util.InitialisationFunctions;

/**
 * @author TuxPaper
 * @created May 29, 2006
 *
 * @notes
 * The old Initializer would store up LogEvents if the UI had the console set
 * to auto-open, and send the events to the mainwindow when it was initialized
 * This Initializer doesn't do this (yet)
 	    final ArrayList logEvents = new ArrayList();
	    ILogEventListener logListener = null;
	    if (COConfigurationManager.getBooleanParameter("Open Console", false)) {
	    	logListener = new ILogEventListener() {
					public void log(LogEvent event) {
						logEvents.add(event);
					}
	    	};
	    	Logger.addListener(logListener);
	    }
	    final ILogEventListener finalLogListener = logListener;
 *
 * The old initializer sets a semaphore when it starts loading IPFilters,
 * and on CoreListener.coreStarted would:
						IpFilterManager ipFilterManager = core.getIpFilterManager();
						if (ipFilterManager != null) {
							String s = MessageText.getString("splash.loadIpFilters");
	  					do {
	  						reportCurrentTask(s);
	  						s += ".";
	  					} while (!semFilterLoader.reserve(3000));
						}
 */
public class Initializer
	implements IUIIntializer
{
	// Whether to initialize the UI before the core has been started
	private static boolean STARTUP_UIFIRST = System.getProperty("ui.startfirst", "1").equals("1");

	// Used in debug to find out how long initialization took
	public static final long startTime = System.currentTimeMillis();
	private final CoreLifecycleAdapter coreLifecycleAdapter;

	private StartServer startServer;

	private final Core core;

	private CopyOnWriteList listeners = new CopyOnWriteList();

	private AEMonitor listeners_mon = new AEMonitor("Initializer:l");

	private int curPercent = 0;

  private AESemaphore semFilterLoader = new AESemaphore("filter loader");

	private AESemaphore init_task = new AESemaphore("delayed init");

	private MainWindowFactory.MainWindowInitStub windowInitStub;

	private static Initializer lastInitializer;

	private DeviceManagerUI deviceManagerUI;
	private SubscriptionManagerUI subscriptionManagerUI;

	/**
	 * Main Initializer.
	 * @param core
	 */
	public Initializer(final Core core, StartServer startServer) {
		this.core = core;
		this.startServer = startServer;
		lastInitializer = this;

    Thread filterLoaderThread = new AEThread("filter loader", true) {
			@Override
			public void runSupport() {
				try {
					core.getIpFilterManager().getIPFilter();
				} finally {
					semFilterLoader.releaseForever();
				}
			}
		};
		filterLoaderThread.setPriority(Thread.MIN_PRIORITY);
		filterLoaderThread.start();

		coreLifecycleAdapter = new CoreLifecycleAdapter() {
			@Override
			public void stopped(Core core) {
				SWTThread instance = SWTThread.getInstance();
				if (instance != null) {
					instance.terminate();
				}
			}
		};
		core.addLifecycleListener(coreLifecycleAdapter);

		Utils.initStatic( core );

		try {
      SWTThread.createInstance(this);
    } catch(SWTThreadAlreadyInstanciatedException e) {
    	Debug.printStackTrace( e );
    }
	}

	private void cleanupOldStuff() {
		File v3Shares = new File(SystemProperties.getUserPath(), "v3shares");
		if (v3Shares.isDirectory()) {
			FileUtil.recursiveDeleteNoCheck(v3Shares);
		}
		File dirFriends = new File(SystemProperties.getUserPath(), "friends");
		if (dirFriends.isDirectory()) {
			FileUtil.recursiveDeleteNoCheck(dirFriends);
		}
		File dirMedia = new File(SystemProperties.getUserPath(), "media");
		if (dirMedia.isDirectory()) {
			FileUtil.recursiveDeleteNoCheck(dirMedia);
		}
		deleteConfig("v3.Friends.dat");
		deleteConfig("unsentdata.config");
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(final Core core) {
				new AEThread2("cleanupOldStuff", true) {
					@Override
					public void run() {
						GlobalManager gm = core.getGlobalManager();
						List dms = gm.getDownloadManagers();
						for (Object o : dms) {
							DownloadManager dm = (DownloadManager) o;
							if (dm != null) {
								String val = PlatformTorrentUtils.getContentMapString(
										dm.getTorrent(), "Ad ID");
								if (val != null) {
									try {
										gm.removeDownloadManager(dm, true, true);
									} catch (Exception e) {
									}
								}
							}
						}
					}
				}.start();
			}
		});
	}

	private void deleteConfig(String name) {
		try {
  		File file = new File(SystemProperties.getUserPath(), name);
  		if (file.exists()) {
  			file.delete();
  		}
		} catch (Exception e) {
		}
		try {
  		File file = new File(SystemProperties.getUserPath(), name + ".bak");
  		if (file.exists()) {
  			file.delete();
  		}
		} catch (Exception e) {
		}
	}

	@Override
	public void runInSWTThread() {
		UISwitcherUtil.calcUIMode();

		try {
  		initializePlatformClientMessageContext();
		} catch (Exception e) {
			Debug.out(e);
		}
		new AEThread2("cleanupOldStuff", true) {
			@Override
			public void run() {
				cleanupOldStuff();
			}
		}.start();

		COConfigurationManager.setBooleanDefault("ui.startfirst", true);
		STARTUP_UIFIRST = STARTUP_UIFIRST
				&& COConfigurationManager.getBooleanParameter("ui.startfirst", true);

		if (!STARTUP_UIFIRST) {
			return;
		}

			// Ensure colors initialized
		
		Colors.getInstance();

		checkInstallID();

		windowInitStub = MainWindowFactory.createAsync( Display.getDefault(), this );
	}

	@Override
	public void shutdownUIOnly() {
		if (coreLifecycleAdapter != null) {
			core.removeLifecycleListener(coreLifecycleAdapter);
		}

		if (deviceManagerUI != null) {
			deviceManagerUI.dispose();
			deviceManagerUI = null;
		}

		if (subscriptionManagerUI != null) {
			// subscriptionManagerUI disposes of itself
			subscriptionManagerUI = null;
		}

		if (windowInitStub != null) {
			windowInitStub.dispose();
			windowInitStub = null;
		}

		UpdateMonitor.destroySingleton();

		UserAlerts.destroySingleton();
		UIUpdaterSWT.destroyInstance();
		ProgressReportingManager.destroyInstance();
		UIToolBarManagerImpl.destroyInstance();
		SelectedContentManager.destroyStatic();

		UIFunctionsManager.setUIFunctions(null);
		SWTSkin.disposeDefault();
		Utils.dispose();
		ImageLoader.disposeInstance();
		TorrentUIUtilsV3.disposeStatic();
		Colors.disposeInstance();
		ColorCache.dispose();
		ColorCache2.dispose();
		FontUtils.dispose();
		SWTSkinPropertiesImpl.destroyStatics();
		ProgressWindow.unregister();
	}

	/**
	 *
	 *
	 * @since 4.4.0.5
	 */
	private void checkInstallID() {
		String storedInstallID = COConfigurationManager.getStringParameter("install.id", null);
		String installID = "";
		File file = FileUtil.getApplicationFile("installer.log");
		if (file != null) {
			try {
				String s = FileUtil.readFileAsString(file, 1024);
				String[] split = s.split("[\r\n]");
				for (int i = 0; i < split.length; i++) {
					int posEquals = split[i].indexOf('=');
					if (posEquals > 0 && split[i].length() > posEquals + 1) {
						installID = split[i].substring(posEquals + 1);
					}
				}
			} catch (IOException e) {
			}
		}

		if (storedInstallID == null || !storedInstallID.equals(installID)) {
			COConfigurationManager.setParameter("install.id", installID);
		}
	}

	@Override
	public void run() {

		DelayedTask delayed_task = UtilitiesImpl.addDelayedTask( "SWT Initialisation", new Runnable()
				{
					@Override
					public void
					run()
					{
						init_task.reserve();
					}
				});

		delayed_task.queueFirst();

		// initialise the SWT locale util
		long startTime = SystemTime.getCurrentTime();

		new LocaleUtilSWT(core);

		final Display display = Utils.getDisplay();

		new UIMagnetHandler(core);

		if (!STARTUP_UIFIRST) {

				// Ensure colors initialized
			
			Colors.getInstance();
		} else {
			COConfigurationManager.setBooleanDefault("Show Splash", false);
		}

		if (COConfigurationManager.getBooleanParameter("Show Splash")) {
			display.asyncExec(new AERunnable() {
				@Override
				public void runSupport() {
					new SplashWindow(display, Initializer.this);
				}
			});
		}

		System.out.println("Locale Initializing took "
				+ (SystemTime.getCurrentTime() - startTime) + "ms");
		startTime = SystemTime.getCurrentTime();

		core.addLifecycleListener(new CoreLifecycleAdapter() {
			@Override
			public void
			componentCreated(
				Core core,
				CoreComponent component )
			{
				Initializer.this.reportPercent(curPercent + 1);


				if (component instanceof GlobalManager){

					reportCurrentTaskByKey("splash.initializePlugins");

					InitialisationFunctions.earlyInitialisation(core);

				} else if (component instanceof PluginInterface) {
					PluginInterface pi = (PluginInterface) component;
					String name = pi.getPluginName();
					String version = pi.getPluginVersion();
					
					// text says initializing, but it's actually initialized.  close enough
					String s = MessageText.getString("splash.plugin.init") + " "
							+ name + (version==null?"":(" v" + version));
					reportCurrentTask(s);
				}
			}

			// @see com.biglybt.core.CoreLifecycleAdapter#started(com.biglybt.core.Core)
			@Override
			public void started(Core core) {
				handleCoreStarted(core);
			}

			@Override
			public void stopping(Core core) {
				Alerts.stopInitiated();
			}

			@Override
			public void stopped(Core core) {
			}

			@Override
			public boolean syncInvokeRequired() {
				return (true);
			}

			@Override
			public boolean
			requiresPluginInitCompleteBeforeStartedEvent()
			{
				return( false );
			}

			@Override
			public boolean stopRequested(Core _core)
					throws CoreException {
				return handleStopRestart(false);
			}

			@Override
			public boolean restartRequested(final Core core) {
				return handleStopRestart(true);
			}

		});

		reportCurrentTaskByKey("splash.initializeCore");

		boolean uiClassic = COConfigurationManager.getStringParameter("ui").equals("az2");

  		try{
  			new SearchUI();

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		try{
			  subscriptionManagerUI = new SubscriptionManagerUI();

	  }catch( Throwable e ){

			Debug.printStackTrace(e);
		}


		if (!uiClassic){
	  		try{
	  			deviceManagerUI = new DeviceManagerUI( core );

	  		}catch( Throwable e ){

	  			Debug.printStackTrace(e);
	  		}
		}

		int					wait		= 15;
		MessageBoxShell[]	wait_shell 	= { null };
		boolean[]			abandon		= { false };
		
		while( !core.canStart( wait )){
			
			synchronized( abandon ){
				
				if ( abandon[0] ){
					
					CoreWaiterSWT.startupAbandoned();
					
					final AESemaphore sem = new AESemaphore( "waiter" );
	
					Utils.execSWTThread(
						new Runnable()
						{
							@Override
							public void
							run()
							{
								MessageBoxShell mb =
									new MessageBoxShell(
										MessageText.getString( "msgbox.force.close.title" ),
										MessageText.getString(
											"msgbox.force.close.text",
											new String[]{ core.getLockFile().getAbsolutePath() }),
										new String[]{ MessageText.getString("Button.ok") },
										0 );
	
								mb.setIconResource( "error" );
	
								mb.setModal( true );
	
								mb.open(
									new UserPrompterResultListener()
									{
	
										@Override
										public void
										prompterClosed(
											int 	result )
										{
											sem.releaseForever();
										}
									});
							}
						});
	
					sem.reserve();
	
					SESecurityManager.exitVM( 1 );
				}
			}
			
			wait = 3;
			
			boolean show_wait = false;
			
			synchronized( wait_shell ){
				
				show_wait = wait_shell[0] == null;
			}
			
			if ( show_wait ){
					
				AESemaphore sem = new AESemaphore( "wait" );
				
				Utils.execSWTThread(
					()->{
						MessageBoxShell mb;
						
						try{
							mb =
								new MessageBoxShell(
									MessageText.getString( "msgbox.startup.stall.title" ),
									MessageText.getString( "msgbox.startup.stall.text" ),
									new String[]{ MessageText.getString("Button.abort") },
									0 );

							synchronized( wait_shell ){
								
								wait_shell[0] = mb;
							}
						}finally{
							
							sem.release();
						}
							
						mb.setIconResource( "warning" );

						mb.setModal( true );

						mb.open((result)->{
						
								synchronized( abandon ){
									
									abandon[0] = true;
								}
							});
					});
				
				sem.reserve();
			}
		}
		
		synchronized( wait_shell ){
			
			if ( wait_shell[0] != null ){
				
				Utils.execSWTThread(
					()->{
						wait_shell[0].close();
					});
			}
		}

		// Other UIs could start the core before us
		if (!core.isStarted()) {
			core.start();
		} else {
			handleCoreStarted(core);
		}

		reportPercent(50);

		System.out.println("Core Initializing took "
				+ (SystemTime.getCurrentTime() - startTime) + "ms");
		startTime = SystemTime.getCurrentTime();

		reportCurrentTaskByKey("splash.initializeUIElements");

		// Ensure colors initialized
		Colors.getInstance();

		reportPercent(curPercent + 1);
		Alerts.init();

		reportPercent(curPercent + 1);
		ProgressWindow.register(core);

		reportPercent(curPercent + 1);
		new SWTNetworkSelection();

		reportPercent(curPercent + 1);
		new AuthenticatorWindow();
		new CryptoWindow();

		reportPercent(curPercent + 1);
		new CertificateTrustWindow();

		core.getPluginManager().getPluginInstaller().addListener(
				new PluginInstallerListener() {
					@Override
					public boolean installRequest(final String reason,
							final InstallablePlugin plugin)

							throws PluginException {
						if (plugin instanceof StandardPlugin) {

							Map<Integer, Object> properties = new HashMap<>();

							properties.put(UpdateCheckInstance.PT_UI_EXTRA_MESSAGE,
									reason);

							plugin.install(false, false, false, properties);

							return (true);
						} else {

							return (false);
						}
					}
				});
	}

	void handleCoreStarted(Core core) {
		boolean	main_window_will_report_complete = false;

		try {
			GlobalManager gm = core.getGlobalManager();

			InitialisationFunctions.lateInitialisation(core);
			if (gm == null) {
				return;
			}

			// Ensure colors initialized
			Colors.getInstance();

			Initializer.this.reportPercent(curPercent + 1);
			new UserAlerts(gm);

			reportCurrentTaskByKey("splash.initializeGui");

			Initializer.this.reportPercent(curPercent + 1);

			main_window_will_report_complete = true;

			if (STARTUP_UIFIRST) {
				windowInitStub.init(core);
			} else {
				MainWindowFactory.create( core, Display.getDefault(), Initializer.this );
			}

			reportCurrentTaskByKey("splash.openViews");

			SWTUpdateChecker.initialize();

			UpdateMonitor.getSingleton(core); // setup the update monitor

			//Tell listeners that all is initialized :
			Alerts.initComplete();
		}
		finally{

			if ( !main_window_will_report_complete ){
				init_task.release();
			}
		}
	}

	@Override
	public void 
	stopIt(
		boolean isForRestart )
			
		throws CoreException 
	{
		if ( core != null ){

			if ( isForRestart ){

				core.checkRestartSupported();
			}
		}
		
		try{
			if ( core != null ){

				try{
					long lStopStarted = System.currentTimeMillis();
					
					System.out.println("core.stop");
					
					AESemaphore stop_sem = new AESemaphore( "stop" );
					
					ProgressCallback	prog = 
						new ProgressCallbackAdapter()
						{
							@Override
							public int 
							getStyle()
							{
								return( STYLE_NO_CLOSE | STYLE_MINIMIZE | STYLE_MODAL );
							}
							
							@Override
							public int 
							getDelay()
							{
								return( 3*1000 );
							}
							
							public int
							getSupportedTaskStates()
							{
								return( ProgressCallback.ST_SUBTASKS | ProgressCallback.ST_MINIMIZE );
							}
							
							@Override
							public void 
							setTaskState(
								int state )
							{
								if ( state == ProgressCallback.ST_MINIMIZE ){
									
									UIFunctionsSWT functionsSWT = UIFunctionsManagerSWT.getUIFunctionsSWT();
									if (functionsSWT != null) {
										functionsSWT.getMainWindow().setVisible( IMainWindow.WINDOW_ELEMENT_ALL, false );
									}
								}
							}
						};
						
						boolean show_progress = COConfigurationManager.getBooleanParameter( "Tidy Close With Progress" );
						
						if ( show_progress ){
							
							if ( Utils.isSWTThread()){
								
								Debug.out( "Can't run closedown progress window as already on SWT thread" );
								
							}else{
	
								Utils.execSWTThread(()->{
									FileUtil.runAsTask(
										CoreOperation.OP_PROGRESS,
										new CoreOperationTask()
										{
											@Override
											public String 
											getName()
											{
												return( MessageText.getString( isForRestart?"label.restarting.app":"label.closing.app" ));
											}
		
											@Override
											public DownloadManager
											getDownload()
											{
												return( null );
											}
											
											@Override
											public void
											run(
												CoreOperation operation)
											{
												try{												
													stop_sem.reserve();
													
												}catch( Throwable e ){
		
													throw( new RuntimeException( e ));
												}
											}
		
											@Override
											public ProgressCallback 
											getProgressCallback()
											{
												return( prog );
											}
										});});
							}
					}
					
					try{
						if ( isForRestart ){							

							core.restart( prog );
							
						}else{

							core.stop( prog );
						}
						
					}finally{
						
						stop_sem.release();
					}
												
					System.out.println("core.stop done in "	+ (System.currentTimeMillis() - lStopStarted));
					
				} catch (Throwable e) {

					// don't let any failure here cause the stop operation to fail

					Debug.out(e);
				}
			}
		}finally{

				// do this later than before so we get UI updates during closedown and can see progress of stats etc
			
			UIUpdaterSWT.destroyInstance();


				// do this after closing core to minimise window when the we aren't
				// listening and therefore another client start can potentially get
				// in and screw things up

			if (startServer != null) {
				startServer.stopIt();
			}
		}


		SWTThread instance = SWTThread.getInstance();
		if (instance != null) {
			instance.terminate();
		}

	}

	// @see IUIIntializer#addListener(com.biglybt.ui.swt.mainwindow.InitializerListener)
	@Override
	public void addListener(InitializerListener listener) {
		try {
			listeners_mon.enter();

			listeners.add(listener);
		} finally {

			listeners_mon.exit();
		}
	}

	// @see IUIIntializer#removeListener(com.biglybt.ui.swt.mainwindow.InitializerListener)
	@Override
	public void removeListener(InitializerListener listener) {
		try {
			listeners_mon.enter();

			listeners.remove(listener);
		} finally {

			listeners_mon.exit();
		}
	}

	@Override
	public void reportCurrentTask(String currentTaskString) {
		try {
			listeners_mon.enter();

			Iterator iter = listeners.iterator();
			while (iter.hasNext()) {
				InitializerListener listener = (InitializerListener) iter.next();
				try {
					listener.reportCurrentTask(currentTaskString);
				} catch (Exception e) {
					// ignore
				}
			}
		} finally {

			listeners_mon.exit();
		}
	}

	private void reportCurrentTaskByKey(String key) {
		reportCurrentTask(MessageText.getString(key));
	}

	@Override
	public void increaseProgress() {
		if (curPercent < 100) {
			reportPercent(curPercent + 1);
		}
	}

	// @see IUIIntializer#abortProgress()
	@Override
	public void abortProgress() {
		reportPercent(101);
	}

	@Override
	public void reportPercent(int percent) {
		if (curPercent > percent) {
			return;
		}

		curPercent = percent;
		try {
			listeners_mon.enter();

			Iterator iter = listeners.iterator();
			while (iter.hasNext()) {
				InitializerListener listener = (InitializerListener) iter.next();
				try {
					listener.reportPercent(percent);
				} catch (Exception e) {
					// ignore
				}
			}

			if (percent > 100) {
				listeners.clear();
			}
		} finally {

			listeners_mon.exit();
		}
	}

	@Override
	public void
	initializationComplete()
	{
		core.getPluginManager().firePluginEvent( PluginEvent.PEV_INITIALISATION_UI_COMPLETES );

		// Old Initializer would delay 8500

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
			  new DelayedEvent(
					  "SWTInitComplete:delay",
					  500,
					  new AERunnable()
					  {
						  @Override
						  public void
						  runSupport()
						  {
						  	/*
						  	try {
									String captureSnapshot = new Controller().captureSnapshot(ProfilingModes.SNAPSHOT_WITH_HEAP);
									System.out.println(captureSnapshot);
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								*/
						  	//System.out.println("Release Init. Task");
							  init_task.release();
						  }
					  });
			}
		});
	}

	/**
	 *
	 *
	 * @since 3.0.5.3
	 */
	private void initializePlatformClientMessageContext() {
		ClientMessageContext clientMsgContext = PlatformMessenger.getClientMessageContext();
		if (clientMsgContext != null) {
			clientMsgContext.setMessageDispatcher(new MessageDispatcherSWT(clientMsgContext));
			clientMsgContext.addMessageListener(new TorrentListener());
			clientMsgContext.addMessageListener(new VuzeListener());
			clientMsgContext.addMessageListener(new DisplayListener());
			clientMsgContext.addMessageListener(new ConfigListener(null));
		}
	}

  public static boolean
  handleStopRestart(
  	final boolean	restart )
  {
		UIFunctionsSWT functionsSWT = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (functionsSWT != null) {
			return functionsSWT.dispose(restart);
		}

		return false;
	}

	public static Initializer getLastInitializer() {
		return lastInitializer;
	}
}
