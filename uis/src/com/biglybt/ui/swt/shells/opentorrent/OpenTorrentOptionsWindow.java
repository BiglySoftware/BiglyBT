/* Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.shells.opentorrent;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.json.simple.JSONObject;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.content.ContentException;
import com.biglybt.core.content.RelatedAttributeLookupListener;
import com.biglybt.core.content.RelatedContentManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerAvailability;
import com.biglybt.core.download.DownloadManagerFactory;
import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.impl.TagBase;
import com.biglybt.core.tag.impl.TagTypeBase;
import com.biglybt.core.torrent.*;
import com.biglybt.core.torrent.impl.TorrentOpenFileOptions;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.torrent.impl.TorrentOpenOptions.FileListener;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginViewInterface;
import com.biglybt.ui.*;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableSelectionListener;
import com.biglybt.ui.common.table.TableViewFilterCheck;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.config.ConfigSectionFile;
import com.biglybt.ui.config.ConfigSectionInterfaceTags;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.config.IntSwtParameter;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.*;
import com.biglybt.ui.swt.maketorrent.MultiTrackerEditor;
import com.biglybt.ui.swt.maketorrent.TrackerEditorListener;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.shells.main.UIFunctionsImpl;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.subscriptions.SubscriptionListWindow;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;
import com.biglybt.ui.swt.views.TrackerAvailView;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.StandardButtonsArea;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.tableitems.mytorrents.TrackerNameItem;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.ui.swt.views.utils.TagButtonsUI;
import com.biglybt.ui.swt.widgets.TagCanvas.TagButtonTrigger;
import com.biglybt.ui.swt.widgets.TagPainter;
import com.biglybt.util.JSONUtils;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;

@SuppressWarnings({
	"unchecked",
	"rawtypes"
})
public class OpenTorrentOptionsWindow
	implements UIUpdatable
{
	private static final AtomicInteger	window_id_next = new AtomicInteger();
	
	private static final Map<HashWrapper,OpenTorrentOptionsWindow>	active_windows = new HashMap<>();

	private static TimerEventPeriodic	active_window_checker;

	private final static String PARAM_DEFSAVEPATH = "Default save path";

	private final static String[] MSGKEY_QUEUELOCATIONS = {
		"OpenTorrentWindow.addPosition.first",
		"OpenTorrentWindow.addPosition.last",
		"OpenTorrentWindow.addPosition.auto"		// only for metadata downloads
	};

	public static final String TABLEID_TORRENTS = "OpenTorrentTorrent";
	public static final String TABLEID_FILES 	= "OpenTorrentFile";


	private static final String SP_KEY = "oto:tag:initsp";


	public static void main(String[] args) {
		try{
			SWTThread.createInstance(
				new IUIIntializer() {

					@Override
					public void stopIt(boolean isForRestart) {
						// TODO Auto-generated method stub

					}

					@Override
					public void runInSWTThread() {
						// TODO Auto-generated method stub

					}

					@Override
					public void shutdownUIOnly() {

					}

					@Override
					public void run() {
						Core core = CoreFactory.create();
						core.start();

						UIConfigDefaultsSWT.initialize();

						Colors.getInstance();

						UIFunctionsImpl uiFunctions = new UIFunctionsImpl(null);
						UIFunctionsManager.setUIFunctions(uiFunctions);

						File file1 = new File("C:\\temp\\test.torrent");
						File file2 = new File("C:\\temp\\test1.torrent");

						TOTorrent torrent1 = null;
						try {
							torrent1 = TOTorrentFactory.deserialiseFromBEncodedFile(file1);
						} catch (TOTorrentException e) {
							e.printStackTrace();
						}

						TOTorrent torrent2 = null;
						try {
							torrent2 = TOTorrentFactory.deserialiseFromBEncodedFile(file2);
						} catch (TOTorrentException e) {
							e.printStackTrace();
						}

						COConfigurationManager.setParameter( ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_SEP, false );
						COConfigurationManager.setParameter( "User Mode", 2 );

						if (torrent1 != null) {
							addTorrent(	new TorrentOpenOptions(null, torrent1, false, null));
						}

						if (torrent2 != null) {
							addTorrent(	new TorrentOpenOptions(null, torrent2, false, null));
						}
					}

					@Override
					public void reportPercent(int percent) {
						// TODO Auto-generated method stub

					}

					@Override
					public void reportCurrentTask(String currentTaskString) {
						// TODO Auto-generated method stub

					}

					@Override
					public void removeListener(InitializerListener listener) {
						// TODO Auto-generated method stub

					}

					@Override
					public void initializationComplete() {
						// TODO Auto-generated method stub

					}

					@Override
					public void increaseProgress() {
						// TODO Auto-generated method stub

					}

					@Override
					public void addListener(InitializerListener listener) {
						// TODO Auto-generated method stub

					}

					@Override
					public void abortProgress() {
						// TODO Auto-generated method stub

					}
				});

		}catch( Throwable e ){
			e.printStackTrace();
		}
	}

	private static final String CONFIG_FILE = "oto.config";
	
	private static volatile boolean initialised;
	
	public static void
	initialise()
	{
		try{
			if ( FileUtil.resilientConfigFileExists( CONFIG_FILE )){
	
				Map<String,Object>	oto_map = FileUtil.readResilientConfigFile( CONFIG_FILE );
				
				if ( oto_map != null ){
					
					List<Map<String,Object>>	oto_list = (List<Map<String,Object>>)oto_map.get( "oto" );
					
					if ( oto_list != null ){
						
						for ( Map<String,Object> map: oto_list ){
							
							try{
								addTorrent( TorrentOpenOptions.importFromMap( map ));
								
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
			
		}finally{
			
			Utils.execSWTThread(()->{ initialised = true; });
		}
	}
	
	public static void
	close()
	{
		saveActiveWindows();
	}
	
	private static AsyncDispatcher dispatcher = new AsyncDispatcher();
	
	private static FrequencyLimitedDispatcher freq_disp = 
		new FrequencyLimitedDispatcher(	AERunnable.create(()->{saveActiveWindows();}), 10*1000 );
	
	private static void
	activeWindowsChanged()
	{
		if ( initialised ){
			
			dispatcher.dispatch(()->{ freq_disp.dispatch();	});
		}
	}
	
	private static void
	saveActiveWindows()
	{
		try{
			synchronized( active_windows ){
				
				List<Map<String,Object>>	oto_list = new ArrayList<>();
				
				for ( OpenTorrentOptionsWindow w: active_windows.values()){
				
					List<OpenTorrentInstance> instances = w.getInstances();
					
					for ( OpenTorrentInstance instance: instances ){
						
						TorrentOpenOptions opts = instance.getOptions();
						
						try{
							Map<String,Object> map = opts.exportToMap();
							
							oto_list.add( map );
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
				
				if ( oto_list.isEmpty()){
					
					FileUtil.deleteResilientConfigFile( CONFIG_FILE );
						
				}else{
					
					Map<String,Object>	oto_map = new HashMap<>();
					
					oto_map.put( "oto", oto_list);
	
					FileUtil.writeResilientConfigFile( CONFIG_FILE, oto_map );
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}

	private final int window_id = window_id_next.incrementAndGet();
	
	private SkinnedDialog 			dlg;
	private ImageLoader 			image_loader;
	private SWTSkinObjectSash 		sash_object;
	private StackLayout				expand_stack;
	private	Composite 				expand_stack_area;
	private StandardButtonsArea 	buttonsArea;
	private boolean 				window_initialised;

	private Button	buttonTorrentUp;
	private Button	buttonTorrentDown;
	private Button	buttonTorrentRemove;
	private Button	buttonTorrentAccept;
	
	private List<String>	images_to_dispose = new ArrayList<>();


	private TableViewSWT<OpenTorrentInstance> 	tvTorrents;
	private Label								torrents_info_label;

	private OpenTorrentInstanceListener	optionListener;

	private List<OpenTorrentInstance>	open_instances 		= new ArrayList<>();
	private List<OpenTorrentInstance>	selected_instances 	= new ArrayList<>();

	private OpenTorrentInstance			multi_selection_instance;

	protected Map<String,DiscoveredTag> listDiscoveredTags = new TreeMap<>();

	AsyncDispatcher spaceUpdateDispatcher = new AsyncDispatcher();

	public static void
	addTorrent(
		TorrentOpenOptions torrentOptions )
	{
		Utils.execSWTThread(()->{ addTorrentSupport( torrentOptions, 1 ); });
	}
		
	private static void
	addTorrentSupport(
		TorrentOpenOptions 	torrentOptions,
		int					attempt_count )
	{
		TOTorrent torrent = torrentOptions.getTorrent();
	
		HashWrapper hw;
		
		try{
			hw = torrent.getHashWrapper();
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			torrentOptions.cancel();
			
			return;
		}
		
		synchronized( active_windows ){

			OpenTorrentOptionsWindow existing = active_windows.get( hw );

			if ( existing != null ){

				if ( existing.isDisposed()){
					
					String name = new String(torrent.getName(), Constants.UTF_8 );
					
						// waiting for it to be removed
					
					if ( attempt_count > 5 ){
						
						Debug.out( "Taking a long time to dispose of existing dialog when adding '" + name + "'..." );
						
					}else if ( attempt_count > 30 ){
						
						Debug.out( "Giving up on adding '" + name + "'" );
						
						torrentOptions.cancel();
						
						return;
					}
					
					SimpleTimer.addEvent(
						"awc", SystemTime.getOffsetTime( 1000 ),
						(ev)->{
							addTorrentSupport( torrentOptions, attempt_count+1 );
						});
					
					return;
				}
				
				existing.swt_activate();

				torrentOptions.cancel();
				
				return;
			}

			boolean	separate_dialogs = COConfigurationManager.getBooleanParameter( ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_SEP );

			if ( active_window_checker == null ){

				active_window_checker =
					SimpleTimer.addPeriodicEvent(
						"awc",
						250,
						new TimerEventPerformer() {

							@Override
							public void
							perform(
								TimerEvent event )
							{
								Utils.execSWTThread(new AERunnable() {
									@Override
									public void runSupport()
									{
										synchronized( active_windows ){

											if ( active_windows.size() == 0 ){

												if ( active_window_checker != null ){

													active_window_checker.cancel();

													active_window_checker = null;
												}
											}else{

												for ( OpenTorrentOptionsWindow w: active_windows.values()){

													List<OpenTorrentInstance>	instances = w.getInstances();

													int	num_reject 	= 0;
													int num_accept	= 0;

													for ( OpenTorrentInstance inst: instances ){

														TorrentOpenOptions opts = inst.getOptions();

														int act = opts.getCompleteAction();

														if ( act == TorrentOpenOptions.CA_REJECT ){

															w.removeInstance( inst, true );

															num_reject++;

														}else if ( act == TorrentOpenOptions.CA_ACCEPT ){

															num_accept++;
														}

														if ( opts.getAndClearDirt()){

															inst.refresh();
														}
													}

													if ( num_reject >= instances.size()){

														w.cancelPressed();

													}else if ( num_accept + num_reject >= instances.size()){

														w.okPressed( false );
													}
												}
											}
										}
									}
								});
							}
						});


			}

			TorrentManagerImpl t_man = TorrentManagerImpl.getSingleton();

			if ( !separate_dialogs ){

				if ( active_windows.size() > 0 ){

					final OpenTorrentOptionsWindow reuse_window = active_windows.values().iterator().next();

					active_windows.put( hw,  reuse_window );

					t_man.optionsAdded( torrentOptions );

					reuse_window.swt_addTorrent( hw, torrentOptions );

					activeWindowsChanged();
					
					return;
				}
			}

			final OpenTorrentOptionsWindow new_window = new OpenTorrentOptionsWindow();

			active_windows.put( hw,  new_window );

			t_man.optionsAdded( torrentOptions );

			new_window.swt_addTorrent( hw, torrentOptions );
			
			activeWindowsChanged();
			
			return;
		}
	}

	private
	OpenTorrentOptionsWindow()
	{
		image_loader = SWTSkinFactory.getInstance().getImageLoader(SWTSkinFactory.getInstance().getSkinProperties());

		optionListener =
			new OpenTorrentInstanceListener()
		{
			@Override
			public void
			instanceChanged(
				OpenTorrentInstance instance )
			{
				updateInstanceInfo();
			}
		};
	}

	protected void
	swt_addTorrent(
		HashWrapper				hash,
		TorrentOpenOptions		torrentOptions )
	{
		final TorrentManagerImpl t_man = TorrentManagerImpl.getSingleton();

		try{
			if ( dlg == null ){

				dlg = new SkinnedDialog("skin3_dlg_opentorrent_options", "shell",
						SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

				final SWTSkin skin_outter = dlg.getSkin();

				SWTSkinObject so;

				if (COConfigurationManager.hasParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS, true)) {

					so = skin_outter.getSkinObject("showagain-area");

					if (so != null) {
						so.setVisible(false);
					}
				}

				SWTSkinObject soOptionsArea = skin_outter.getSkinObject("options-area");

				if ( soOptionsArea != null ){
				
					SWTSkinObjectButton  opt_config = (SWTSkinObjectButton)skin_outter.getSkinObject( "options-config" );

					opt_config.addSelectionListener(
						new SWTSkinButtonUtility.ButtonListenerAdapter()
						{
							@Override
							public void 
							pressed(SWTSkinButtonUtility buttonUtility, SWTSkinObject skinObject,
									int button, int stateMask)
							{
								UIFunctions uif = UIFunctionsManager.getUIFunctions();
								
								if ( uif != null ){
		
									JSONObject args = new JSONObject();
		
									args.put( "select", ConfigSectionFile.REFID_DEFAULT_DIR_OPTIONS);
									
									String args_str = JSONUtils.encodeToJSON( args );
									
									uif.getMDI().showEntryByID(
											MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
											"files" + args_str );
								}	
							}
						
						});
					
					SWTSkinObjectCheckbox  opt_sep_dialog = (SWTSkinObjectCheckbox)skin_outter.getSkinObject( "options-sep-dialog" );
					
					opt_sep_dialog.setChecked( COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_UI_ADDTORRENT_OPENOPTIONS_SEP ));
					
					opt_sep_dialog.addSelectionListener((skinobj,checked)->{
						
						COConfigurationManager.setParameter( ConfigKeys.File.BCFG_UI_ADDTORRENT_OPENOPTIONS_SEP, checked );
					});
					
					COConfigurationManager.addParameterListener(
						ConfigKeys.File.BCFG_UI_ADDTORRENT_OPENOPTIONS_SEP,
							new ParameterListener(){
								public void 
								parameterChanged(
									String name)
								{
									if ( opt_sep_dialog.isDisposed()){
										COConfigurationManager.removeParameterListener( name, this );
									}else{
										opt_sep_dialog.setChecked( COConfigurationManager.getBooleanParameter( name ));
									}
								}
							});
				}
				
				SWTSkinObject soButtonArea = skin_outter.getSkinObject("button-area");

				//soButtonArea.getControl().setBackground( Colors.green );

				if (soButtonArea instanceof SWTSkinObjectContainer) {
					buttonsArea = new StandardButtonsArea() {
						@Override
						protected void clicked(int intValue) {
							if (intValue == SWT.OK) {
								okPressed(false);
							}else{
								cancelPressed();
							}
						}
					};
					buttonsArea.setButtonIDs(new String[] {
						MessageText.getString("Button.ok"),
						MessageText.getString("Button.cancel")
					});
					buttonsArea.setButtonVals(new Integer[] {
						SWT.OK,
						SWT.CANCEL
					});
					buttonsArea.swt_createButtons(((SWTSkinObjectContainer) soButtonArea).getComposite());
					
					int autoCloseSecs = COConfigurationManager.getIntParameter( ConfigKeys.File.ICFG_UI_ADDTORRENT_OPENOPTIONS_AUTO_CLOSE_SECS );
					
					if ( autoCloseSecs > 0 ){
						
						BufferedLabel label = buttonsArea.getLabel();
						
						boolean[] disabled = {false};
						
						label.addMouseListener(
							new MouseAdapter(){																
								@Override
								public void mouseDown(MouseEvent e){
									disabled[0] = !disabled[0];
								}
							});
						
						AEThread2.createAndStartDaemon( "AutoClose", ()->{
							
							int	rem = autoCloseSecs;
							
							while( !label.isDisposed()){
								
								if ( disabled[0] ){
									
									Utils.execSWTThread(()->{

										if ( label.isDisposed()){
											return;
										}
										label.setForeground( Colors.dark_grey );
										
									});
								}else{
									
									int f_rem = rem;
									
									Utils.execSWTThread(()->{
										
										if ( label.isDisposed()){
											return;
										}
										
										label.setForeground( null );
										
										if ( f_rem == 0 ){
											
											okPressed( true );
											
										}else{
											
											label.setText( MessageText.getString( "label.auto.accept.in", new String[]{ TimeFormatter.format3(f_rem) }));
										}
									});
									
									if ( rem == 0 ){
										
										break;
									}
									
									rem--;
								}
								
								try{
									Thread.sleep( 1000 );
									
								}catch( Throwable e ){
									
								}
															}
						});
					}
				}

				sash_object = (SWTSkinObjectSash)skin_outter.getSkinObject("multi-sash");

				SWTSkinObjectContainer select_area = (SWTSkinObjectContainer)skin_outter.getSkinObject( "torrents-table" );

				setupTVTorrents( select_area.getComposite());

				SWTSkinObjectContainer torrents_info = (SWTSkinObjectContainer)skin_outter.getSkinObject( "torrents-info" );

				Composite info_area = torrents_info.getComposite();

				info_area.setLayout( new GridLayout());

				torrents_info_label = new Label( info_area, SWT.NULL );
				torrents_info_label.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

				sash_object.setVisible( false );
				sash_object.setAboveVisible( false );

				so = skin_outter.getSkinObject("expand-area");

				expand_stack_area = ((SWTSkinObjectContainer)so).getComposite();

				expand_stack	= new StackLayout();

				expand_stack_area.setLayout( expand_stack );

				Composite expand_area = new Composite( expand_stack_area, SWT.NULL );

				expand_area.setLayout( new FormLayout());

				expand_stack.topControl = expand_area;

				OpenTorrentInstance instance = new OpenTorrentInstance( hash, expand_area, torrentOptions, optionListener );

				addInstance( instance );

				selected_instances.add( instance );

				UIUpdaterSWT.getInstance().addUpdater(this);

				setupShowAgainOptions(skin_outter);

					/*
					 * The bring-to-front logic for torrent addition is controlled by other parts of the code so we don't
					 * want the dlg to override this behaviour (main example here is torrents passed from, say, a browser,
					 * and the user has disabled the 'show on external torrent add' feature)
					 */
				
				Runnable doOpen = ()->{
					
					boolean	separate_dialogs = COConfigurationManager.getBooleanParameter( ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_SEP );

					OpenTorrentOptionsWindow moveBelow = null;
					
					if ( separate_dialogs ){
												
						synchronized( active_windows ){
				
							for ( OpenTorrentOptionsWindow window: active_windows.values()){
		
								if ( window == this || !window.isInitialised()){
		
									continue;
								}
								
								if ( 	moveBelow == null || 
										moveBelow.window_id < window.window_id ){
									
									moveBelow = window;
								}
							}
						}
					}
					
					dlg.open("otow",false,moveBelow==null?null:moveBelow.getShell());
	
					if ( separate_dialogs ){
						
							// don't want them appearing on top of each other
						
						synchronized( active_windows ){
		
							int	num_active_windows = active_windows.size();
		
							Shell shell = dlg.getShell();
		
							if ( num_active_windows > 1 ){
		
								int	max_x = Integer.MIN_VALUE;
								int max_y = Integer.MIN_VALUE;
		
								for ( OpenTorrentOptionsWindow window: active_windows.values()){
		
									if ( window == this || !window.isInitialised()){
		
										continue;
									}
											
									Rectangle rect = window.getBounds();
		
									max_x = Math.max( max_x, rect.x );
									max_y = Math.max( max_y, rect.y );
								}
		
								if ( max_x > Integer.MIN_VALUE ){
									
									Rectangle rect = shell.getBounds();
			
									rect.x = max_x + 16;
									rect.y = max_y + 16;
			
									try{
										Utils.setShellMetricsConfigEnabled( shell, false );
									
										shell.setBounds( rect );
										
									}finally{
										
										Utils.setShellMetricsConfigEnabled( shell, true );
									}
								}
							}
		
							//String before = "disp="+shell.getDisplay().getBounds()+",shell=" + shell.getBounds();
		
							Utils.verifyShellRect( shell, true );
		
							//Debug.outNoStack( "Opening torrent options dialog: " + before + " -> " + shell.getBounds());
						}
					}
				};
				
					// annoying issue with OSX - if the dialog is opened when the main shell
					// is minimized then it doesn't appear on de-minimizing until you manually
					// select the BiglyBT window
				
				boolean wentAsync = false;
				
				Shell mainShell = UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell();

				if ( Constants.isOSX && mainShell.getMinimized()){
				
					wentAsync = true;
					
					mainShell.addShellListener( new ShellAdapter(){
						@Override
						public void shellActivated(ShellEvent e){
							mainShell.removeShellListener(this);
							if ( !dlg.isDisposed()){
								doOpen.run();
								
								window_initialised = true;
							}
						}
					});
				}else{
					
					doOpen.run();
				}
				
				dlg.addCloseListener(new SkinnedDialog.SkinnedDialogClosedListener() {
					@Override
					public void skinDialogClosed(SkinnedDialog dialog) {
						try{
							dispose();

						}finally{

							synchronized( active_windows ){

								Iterator<OpenTorrentOptionsWindow> it = active_windows.values().iterator();

								while( it.hasNext()){

									OpenTorrentOptionsWindow window = it.next();

									if ( window == OpenTorrentOptionsWindow.this ){

										it.remove();
									}
								}

								TorrentManagerImpl t_man = TorrentManagerImpl.getSingleton();

								for ( OpenTorrentInstance inst: open_instances ){

									inst.cancelPressed();
									
									t_man.optionsRemoved( inst.getOptions());
								}
							}
							
							activeWindowsChanged();
						}
					}
				});

				if ( !wentAsync ){
				
					window_initialised = true;
				}
			}else{

				Composite expand_area = new Composite( expand_stack_area, SWT.NULL );

				expand_area.setLayout( new FormLayout());

				OpenTorrentInstance instance = new OpenTorrentInstance( hash, expand_area, torrentOptions, optionListener );

				addInstance( instance );

				if ( !sash_object.isVisible()){

					sash_object.setVisible( true );

					sash_object.setAboveVisible( true );

					Utils.execSWTThreadLater(
						0,
						new Runnable()
						{
							@Override
							public void
							run()
							{
								tvTorrents.processDataSourceQueueSync();

								List<TableRowCore> rows = new ArrayList<>();

								for ( OpenTorrentInstance instance: selected_instances ){

									TableRowCore row = tvTorrents.getRow( instance );

									if ( row != null ){

										rows.add( row );
									}
								}

								if ( rows.size() > 0 ){

									tvTorrents.setSelectedRows( rows.toArray( new TableRowCore[ rows.size() ]));
								}
							}
						});

				}
			}

		}catch( Throwable e ){

			Debug.out( e );

			synchronized( active_windows ){

				active_windows.remove( hash );

				torrentOptions.cancel();
				
				t_man.optionsRemoved( torrentOptions );
			}
			
			activeWindowsChanged();
		}
	}
	
	private boolean
	isDisposed()
	{
		return( expand_stack_area.isDisposed());
	}
	
	private boolean
	isInitialised()
	{
		return( window_initialised );
	}

	private List<OpenTorrentInstance>
	getInstances()
	{
		return( new ArrayList<>( open_instances));
	}

	private OpenTorrentInstance
	getInstance(
		TorrentOpenOptions	options )
	{
		for ( OpenTorrentInstance instance: open_instances ){
			
			if ( instance.getOptions() == options ){
				
				return( instance );
			}
		}
		
		return( null );
	}
	
	private void
	cancelPressed()
	{
		for ( final OpenTorrentInstance instance: new ArrayList<>( open_instances )){
			
			instance.cancelPressed();
		}
		
		if ( dlg != null ){

			dlg.close();
		}
	}

	private void
	okPressed(
		boolean		auto )
	{
		TorrentManagerImpl t_man = TorrentManagerImpl.getSingleton();

		boolean	all_ok = true;

		AsyncDispatcher dispatcher = new AsyncDispatcher();

		for ( final OpenTorrentInstance instance: new ArrayList<>( open_instances )){

			String dataDir = instance.cmbDataDir.getText();

			if ( !instance.okPressed( dataDir, auto )){

				all_ok = false;

			}else{

					// serialise additions in correct order

				t_man.optionsAccepted( instance.getOptions());

				dispatcher.dispatch(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							instance.getOptions().addToDownloadManager();

						}
					});

				removeInstance( instance, false );
			}
		}

		if ( all_ok ){
			if (dlg != null){
				dlg.close();
			}
		}
	}

	private void setupShowAgainOptions(SWTSkin skin) {
		SWTSkinObjectCheckbox soAskLater = (SWTSkinObjectCheckbox) skin.getSkinObject("showagain-asklater");
		SWTSkinObjectCheckbox soNever = (SWTSkinObjectCheckbox) skin.getSkinObject("showagain-never");
		SWTSkinObjectCheckbox soAlways = (SWTSkinObjectCheckbox) skin.getSkinObject("showagain-always");
		SWTSkinObjectCheckbox soMany = (SWTSkinObjectCheckbox) skin.getSkinObject("showagain-manyfile");

		String showAgainMode = COConfigurationManager.getStringParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS);
		boolean hasUserChosen = COConfigurationManager.hasParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS, true);

		if ( soAskLater != null && !hasUserChosen ){
			soAskLater.addSelectionListener(new SWTSkinCheckboxListener() {
				@Override
				public void checkboxChanged(SWTSkinObjectCheckbox so, boolean checked) {
					COConfigurationManager.removeParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS );
				}
			});
			if (!hasUserChosen) {
				soAskLater.setChecked(true);
			}
		}
		
		if (soNever != null) {
			soNever.addSelectionListener(new SWTSkinCheckboxListener() {
				@Override
				public void checkboxChanged(SWTSkinObjectCheckbox so, boolean checked) {
					COConfigurationManager.setParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS, ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_NEVER);
				}
			});
			if (hasUserChosen) {
				soNever.setChecked(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_NEVER.equals(showAgainMode));
			}
		}

		if (soAlways != null) {
			soAlways.addSelectionListener(new SWTSkinCheckboxListener() {
				@Override
				public void checkboxChanged(SWTSkinObjectCheckbox so, boolean checked) {
					COConfigurationManager.setParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS, ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_ALWAYS);
				}
			});
			if (hasUserChosen) {
				soAlways.setChecked(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_ALWAYS.equals(showAgainMode));
			}
		}

		if (soMany != null) {
			soMany.addSelectionListener(new SWTSkinCheckboxListener() {
				@Override
				public void checkboxChanged(SWTSkinObjectCheckbox so, boolean checked) {
					COConfigurationManager.setParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS, ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_MANY);
				}
			});
			if (hasUserChosen) {
				soMany.setChecked(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_MANY.equals(showAgainMode));
			}
		}
	}

	private void
	setupTVTorrents(
		Composite		parent )
	{
		GridLayout layout = new GridLayout();
		layout.marginWidth = layout.marginHeight = 0;
		layout.horizontalSpacing = layout.verticalSpacing = 0;
		parent.setLayout( layout );
		GridData gd;

		// table

		Composite table_area = new Composite( parent, SWT.NULL );
		layout = new GridLayout();
		layout.marginWidth = layout.marginHeight = 0;
		layout.horizontalSpacing = layout.verticalSpacing = 0;
		table_area.setLayout( layout );
		gd = new GridData( GridData.FILL_BOTH );
		table_area.setLayoutData(gd);

			// toolbar area

		Composite button_area = new Composite( parent, SWT.NULL );
		layout = new GridLayout(6,false);
		layout.marginWidth = layout.marginHeight = 0;
		layout.horizontalSpacing = layout.verticalSpacing = 0;
		layout.marginTop = 5;
		button_area.setLayout( layout);
		gd = new GridData( GridData.FILL_HORIZONTAL );
		button_area.setLayoutData(gd);

		Label label = new Label( button_area, SWT.NULL );
		gd = new GridData( GridData.FILL_HORIZONTAL );
		label.setLayoutData(gd);

		buttonTorrentUp = new Button(button_area, SWT.PUSH);
		buttonTorrentUp.setImage( loadImage( "image.toolbar.up" ));
		Utils.setTT(buttonTorrentUp,MessageText.getString("Button.moveUp"));
		buttonTorrentUp.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				List<OpenTorrentInstance> selected = (List<OpenTorrentInstance>)(Object)tvTorrents.getSelectedDataSources();
				if ( selected.size() > 1 ){
					Collections.sort(
						selected,
						new Comparator<OpenTorrentInstance>()
						{
							@Override
							public int
							compare(
								OpenTorrentInstance o1,
								OpenTorrentInstance o2)
							{
								return( o1.getIndex() - o2.getIndex());
							}
						});
				}

				boolean modified = false;
				for ( OpenTorrentInstance instance: selected ){

					int index = instance.getIndex();
					if ( index > 0 ){
						open_instances.remove( instance );
						open_instances.add( index-1, instance );
						modified = true;
					}
				}
				if ( modified ){
					swt_updateTVTorrentButtons();

					refreshTVTorrentIndexes();
				}
			}});


		buttonTorrentDown = new Button(button_area, SWT.PUSH);
		buttonTorrentDown.setImage( loadImage( "image.toolbar.down" ));
		Utils.setTT(buttonTorrentDown,MessageText.getString("Button.moveDown"));
		buttonTorrentDown.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				List<OpenTorrentInstance> selected = (List<OpenTorrentInstance>)(Object)tvTorrents.getSelectedDataSources();
				if ( selected.size() > 1 ){
					Collections.sort(
						selected,
						new Comparator<OpenTorrentInstance>()
						{
							@Override
							public int
							compare(
								OpenTorrentInstance o1,
								OpenTorrentInstance o2)
							{
								return( o2.getIndex() - o1.getIndex());
							}
						});
				}
				boolean modified = false;
				for ( Object obj: selected ){

					OpenTorrentInstance	instance = (OpenTorrentInstance)obj;
					int index = instance.getIndex();
					if ( index < open_instances.size() - 1 ){
						open_instances.remove( instance );
						open_instances.add( index+1, instance );
						modified = true;
					}
				}

				if ( modified ){
					swt_updateTVTorrentButtons();

					refreshTVTorrentIndexes();
				}
			}});

		buttonTorrentRemove = new Button(button_area, SWT.PUSH);
		Utils.setTT(buttonTorrentRemove,MessageText.getString("OpenTorrentWindow.torrent.remove"));
		buttonTorrentRemove.setImage( loadImage( "image.toolbar.remove" ));
		buttonTorrentRemove.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				List<Object> selected = tvTorrents.getSelectedDataSources();
				for ( Object obj: selected ){

					OpenTorrentInstance	instance = (OpenTorrentInstance)obj;

					removeInstance( instance, true );
				}
			}});

		buttonTorrentAccept = new Button(button_area, SWT.PUSH);
		Utils.setTT(buttonTorrentAccept,MessageText.getString("label.accept"));
		buttonTorrentAccept.setImage( loadImage( "image.button.play" ));
		buttonTorrentAccept.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				List<OpenTorrentInstance> selected = (List<OpenTorrentInstance>)(Object)tvTorrents.getSelectedDataSources();
				
				acceptInstances( selected);
			}});

		
		buttonTorrentUp.setEnabled( false );
		buttonTorrentDown.setEnabled( false );
		buttonTorrentRemove.setEnabled( false );
		buttonTorrentAccept.setEnabled( false );

		label = new Label( button_area, SWT.NULL );
		gd = new GridData( GridData.FILL_HORIZONTAL );
		label.setLayoutData(gd);


		TableColumnManager tcm = TableColumnManager.getInstance();

		if (tcm.getDefaultColumnNames(TABLEID_TORRENTS) == null) {

			tcm.registerColumn(OpenTorrentInstance.class,
					TableColumnOTOT_Position.COLUMN_ID, new TableColumnCreationListener() {
						@Override
						public void tableColumnCreated(TableColumn column) {
							new TableColumnOTOT_Position(column);
						}
					});

			tcm.registerColumn(OpenTorrentInstance.class,
					TableColumnOTOT_Name.COLUMN_ID, new TableColumnCreationListener() {
						@Override
						public void tableColumnCreated(TableColumn column) {
							new TableColumnOTOT_Name(column);
						}
					});

			tcm.registerColumn(OpenTorrentInstance.class,
					TableColumnOTOT_Size.COLUMN_ID, new TableColumnCreationListener() {
						@Override
						public void tableColumnCreated(TableColumn column) {
							new TableColumnOTOT_Size(column);
						}
					});
			
			tcm.registerColumn(OpenTorrentInstance.class,
					TableColumnOTOT_SaveLocation.COLUMN_ID, new TableColumnCreationListener() {
						@Override
						public void tableColumnCreated(TableColumn column) {
							new TableColumnOTOT_SaveLocation(column);
						}
					});

			tcm.setDefaultColumnNames(TABLEID_TORRENTS, new String[] {

				TableColumnOTOT_Position.COLUMN_ID,
				TableColumnOTOT_Name.COLUMN_ID,
				TableColumnOTOT_Size.COLUMN_ID,
				TableColumnOTOT_SaveLocation.COLUMN_ID,
			});

			tcm.setDefaultSortColumnName(TABLEID_TORRENTS, TableColumnOTOT_Position.COLUMN_ID);
		}

		tvTorrents = TableViewFactory.createTableViewSWT(OpenTorrentInstance.class,
				TABLEID_TORRENTS, TABLEID_TORRENTS, null, "#", SWT.BORDER
						| SWT.FULL_SELECTION | SWT.MULTI );

		tvTorrents.initialize( table_area );

		tvTorrents.setRowDefaultHeightEM(1.4f);


		tvTorrents.addMenuFillListener(
			new TableViewSWTMenuFillListener()
			{
				@Override
				public void
				fillMenu(
					String 		sColumnName,
					Menu 		menu )
				{
					final List<Object> selected = tvTorrents.getSelectedDataSources();

					if ( selected.size() > 0 ){

						final List<OpenTorrentInstance> instances = new ArrayList<>( selected.size());

						final List<OpenTorrentInstance> non_simple_instances = new ArrayList<>();

						boolean can_rtlf = false;

						for ( Object o: selected ){

							OpenTorrentInstance oti = (OpenTorrentInstance)o;

							instances.add( oti );

							if ( !oti.getOptions().isSimpleTorrent()){

								non_simple_instances.add( oti );

								if ( oti.canRemoveTopLevelFolder()){

									can_rtlf = true;
								}
							}
						}

						{
							MenuItem item = new MenuItem(menu, SWT.PUSH);

							Messages.setLanguageText(item, "OpenTorrentWindow.fileList.changeDestination");

							item.addSelectionListener(
								new SelectionAdapter()
								{
									@Override
									public void
									widgetSelected(
										SelectionEvent e )
									{
										for ( Object obj: selected ){

											OpenTorrentInstance	instance = (OpenTorrentInstance)obj;

											instance.setSavePath();
										}
									}
								});
						}

						{
							MenuItem item = new MenuItem(menu, SWT.PUSH);

							Messages.setLanguageText(item, "OpenTorrentWindow.tlf.remove");

							item.addSelectionListener(
								new SelectionAdapter()
								{
									@Override
									public void
									widgetSelected(
										SelectionEvent e )
									{
										for ( Object obj: selected ){

											OpenTorrentInstance	instance = (OpenTorrentInstance)obj;

											if ( instance.canRemoveTopLevelFolder()){

												instance.removeTopLevelFolder();
											}
										}
									}
								});

							item.setEnabled( can_rtlf );
						}

						{
							MenuItem item = new MenuItem(menu, SWT.CHECK );

							 item.setData( COConfigurationManager.getBooleanParameter( "open.torrent.window.rename.on.tlf.change" ));

							 Messages.setLanguageText(item, "OpenTorrentWindow.tlf.rename");

							 item.addSelectionListener(
								 new SelectionAdapter()
								 {
									 @Override
									 public void
									 widgetSelected(
											 SelectionEvent e )
									 {
										 COConfigurationManager.setParameter(
												"open.torrent.window.rename.on.tlf.change",
												((MenuItem)e.widget).getSelection());
									 }
								 });

							item.setEnabled( non_simple_instances.size() > 0 );
						}

						new MenuItem(menu, SWT.SEPARATOR);

						MenuItem item = new MenuItem(menu, SWT.PUSH);

						Messages.setLanguageText(item, "Button.remove");

						item.addSelectionListener(
							new SelectionAdapter()
							{
								@Override
								public void
								widgetSelected(
									SelectionEvent e )
								{
									for ( Object obj: selected ){

										OpenTorrentInstance	instance = (OpenTorrentInstance)obj;

										removeInstance( instance, true );
									}
								}
							});

						new MenuItem(menu, SWT.SEPARATOR);
					}
				}


				@Override
				public void
				addThisColumnSubMenu(
					String 	sColumnName,
					Menu 	menuThisColumn)
				{
				}
			});


		tvTorrents.addSelectionListener(
			new TableSelectionListener()
			{
				@Override
				public void
				selected(
					TableRowCore[] rows_not_used )
				{
					TableRowCore[] rows = tvTorrents.getSelectedRows();

					List<OpenTorrentInstance> instances = new ArrayList<>();

					for ( TableRowCore row: rows ){

						instances.add((OpenTorrentInstance)row.getDataSource());
					}

					selectInstances( instances );

					updateButtons();
				}

				@Override
				public void mouseExit(TableRowCore row) {
				}

				@Override
				public void mouseEnter(TableRowCore row) {
				}

				@Override
				public void focusChanged(TableRowCore focus) {
				}

				@Override
				public void
				deselected(TableRowCore[] rows)
				{
					selected( rows );
				}

				private void
				updateButtons()
				{
					Utils.execSWTThread(
						new Runnable()
						{
							@Override
							public void
							run()
							{
								swt_updateTVTorrentButtons();
							}
						});
				}
				@Override
				public void defaultSelected(TableRowCore[] rows, int stateMask) {
				}

			}, false);
	}

	private void
	addInstance(
		OpenTorrentInstance		instance )
	{
		open_instances.add( instance );

		updateDialogTitle();

		instance.initialize();

		tvTorrents.addDataSources( new OpenTorrentInstance[]{ instance });

		updateInstanceInfo();

		swt_updateTVTorrentButtons();
	}

	private void
	selectInstance(
		OpenTorrentInstance		instance )
	{
		List<OpenTorrentInstance>	instances = new ArrayList<>();

		if ( instance != null ){

			instances.add( instance );
		}

		selectInstances( instances );
	}

	private void
	selectInstances(
		List<OpenTorrentInstance>		_instances )
	{
		if ( _instances.equals( selected_instances )){

			return;
		}

		final List<OpenTorrentInstance> instances = new ArrayList<>( _instances );

		Iterator<OpenTorrentInstance>	it = instances.iterator();

		while( it.hasNext()){

			if ( !open_instances.contains( it.next())){

				it.remove();
			}
		}

		if ( instances.size() == 0 ){

			if ( selected_instances.size() > 0 && open_instances.contains( selected_instances.get(0))){

				instances.add( selected_instances.get(0));

			}else if ( open_instances.size() > 0 ){

				instances.add( open_instances.get(0));
			}
		}

		selected_instances.clear();

		selected_instances.addAll( instances );

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					if ( expand_stack_area.isDisposed()){
						
						return;
					}
					
					if ( multi_selection_instance != null ){

						multi_selection_instance.getComposite().dispose();

						multi_selection_instance = null;
					}

					if ( instances.size() == 1 ){

						OpenTorrentInstance first_instance = instances.get(0);

						expand_stack.topControl = first_instance.getComposite();

						expand_stack_area.layout(true);

						if ( first_instance.tagButtonsUI != null ){
						
								// pick up visible changes from any multi-tagging changes
							
							first_instance.tagButtonsUI.updateFields( null );
						}
						
						first_instance.layout();

					}else{
						
						Composite expand_area = new Composite( expand_stack_area, SWT.NULL );

						expand_area.setLayout( new FormLayout());

						List<TorrentOpenOptions> toos = new ArrayList<>();

						for ( OpenTorrentInstance oti: instances ){

							toos.add( oti.getOptions());
						}

						multi_selection_instance = new OpenTorrentInstance( expand_area, toos, optionListener );

						multi_selection_instance.initialize();

						expand_stack.topControl = multi_selection_instance.getComposite();

						expand_stack_area.layout(true);

						multi_selection_instance.layout();
					}
				}
			});

		List<TableRowCore> rows = new ArrayList<>();

		for ( OpenTorrentInstance instance: instances ){

			TableRowCore row = tvTorrents.getRow( instance );

			if ( row != null ){

				rows.add( row );
			}
		}

		tvTorrents.setSelectedRows( rows.toArray( new TableRowCore[rows.size()]));
	}

	private void
	removeInstance(
		OpenTorrentInstance		instance,
		boolean					is_removal )
	{
		TorrentManagerImpl t_man = TorrentManagerImpl.getSingleton();

		synchronized( active_windows ){

			active_windows.remove( instance.getHash());

			t_man.optionsRemoved( instance.getOptions());
		}
		
		activeWindowsChanged();

		int index = open_instances.indexOf( instance );

		open_instances.remove( instance );

		updateDialogTitle();

		tvTorrents.removeDataSource( instance );

		instance.getComposite().dispose();

		updateInstanceInfo();

		if ( selected_instances.contains( instance ) && selected_instances.size() > 1 ){

			List<OpenTorrentInstance> temp = new ArrayList<>( selected_instances );

			temp.remove( instance );

			selectInstances( temp );

		}else{

			int	num_instances = open_instances.size();

			if ( num_instances > index ){

				selectInstance( open_instances.get( index ));

			}else if ( num_instances > 0 ){

				selectInstance( open_instances.get( num_instances-1 ));

			}else{

				selectInstance( null );
			}
		}

		swt_updateTVTorrentButtons();

		refreshTVTorrentIndexes();

		if ( is_removal ){
			
			instance.cancelPressed();
		}
		
		instance.dispose();
		
		if ( open_instances.isEmpty()){
			
			if ( dlg != null ){
				
				dlg.close();
			}
		}
	}

	private void
	acceptInstances(
		List<OpenTorrentInstance>		instances )
	{
		TorrentManagerImpl t_man = TorrentManagerImpl.getSingleton();
		
		for ( final OpenTorrentInstance instance: new ArrayList<>( instances )){

			String dataDir = instance.cmbDataDir.getText();

			if ( instance.okPressed( dataDir, false )){

					// serialise additions in correct order

				t_man.optionsAccepted( instance.getOptions());

				dispatcher.dispatch(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							instance.getOptions().addToDownloadManager();

						}
					});

				removeInstance( instance, false );
			}
		}
		
		if ( open_instances.isEmpty()){
			
			if ( dlg != null ){
				
				dlg.close();
			}
		}else{
			
			swt_updateTVTorrentButtons();
	
			refreshTVTorrentIndexes();
		}
	}
	
	private void
	updateDialogTitle()
	{
		String text;

		int num = open_instances.size();


		if ( num == 1 ){

				// use a display name consistent with core

			TorrentOpenOptions options = open_instances.get(0).getOptions();

			text = options.getDisplayName();

		}else{

			text =  MessageText.getString("label.num.torrents",new String[]{ String.valueOf( open_instances.size())});
		}

		dlg.setTitle(MessageText.getString("OpenTorrentOptions.title") + " [" + text + "]");
	}

	private void
	swt_updateTVTorrentButtons()
	{
		if ( buttonTorrentRemove.isDisposed()){
			
			return;
		}
		
		List<Object> selected = tvTorrents.getSelectedDataSources();

		buttonTorrentRemove.setEnabled( selected.size() > 0 );
		buttonTorrentAccept.setEnabled( selected.size() > 0 );

		if ( selected.size() > 0 ){

			int	min_index 	= Integer.MAX_VALUE;
			int max_index	= -1;

			for ( Object obj: selected ){

				OpenTorrentInstance instance = (OpenTorrentInstance)obj;

				int index = instance.getIndex();

				min_index = Math.min( min_index, index );
				max_index = Math.max( max_index, index );
			}

			buttonTorrentUp.setEnabled( min_index > 0 );

			buttonTorrentDown.setEnabled( max_index < open_instances.size()-1);

		}else{

			buttonTorrentUp.setEnabled( false );

			buttonTorrentDown.setEnabled( false );
		}
	}

	private void
	refreshTVTorrentIndexes()
	{
		Utils.execSWTThreadLater(
				0,
				new Runnable()
				{
					@Override
					public void
					run()
					{
						tvTorrents.columnInvalidate( "#" );

						tvTorrents.refreshTable( true );
					}
				});
	}

	private void
	updateInstanceInfo()
	{
		if ( torrents_info_label == null ){

			return;
		}

		long	total_size		= 0;
		long	selected_size 	= 0;

		for ( OpenTorrentInstance instance: open_instances ){

			total_size		+= instance.getOptions().getTorrent().getSize();
			selected_size 	+= instance.getSelectedDataSize();
		}

		String	sel_str = DisplayFormatters.formatByteCountToKiBEtc(selected_size);
		String	tot_str = DisplayFormatters.formatByteCountToKiBEtc(total_size);


		String text;

		if ( sel_str.equals( tot_str )){

			text = MessageText.getString("label.n.will.be.downloaded", new String[] { tot_str	});

		}else{

			text = MessageText.getString("OpenTorrentWindow.filesInfo", new String[] { sel_str,	tot_str	});
		}

		torrents_info_label.setText( text );
	}

	@Override
	public void
	updateUI()
	{
		if ( tvTorrents != null ){

			tvTorrents.refreshTable( false );
		}

		for( OpenTorrentInstance instance: open_instances ){

			instance.updateUI();
		}

		if ( multi_selection_instance != null ){

			multi_selection_instance.updateUI();
		}
	}

	@Override
	public String getUpdateUIName() {
		return null;
	}

	private void
	swt_activate()
	{
		Shell shell = dlg.getShell();

		if ( !shell.isDisposed()){

			//Utils.dump( shell );

			if ( !shell.isVisible()){

				shell.setVisible( true );
			}

			shell.forceActive();

				// trying to debug some weird hidden dialog issue - on second opening revalidate
				// everything to see if this fixes things

				// parg 24/09/19 - dunno if this is still an issue, reduced debug and commented out centreWindow as this obviously
				// repositions things which isn't great 
			
			shell.layout( true,  true );

			Utils.verifyShellRect( shell, true );

			//Utils.centreWindow( shell );

			//Utils.dump( shell );
		}
	}

	private Shell
	getShell()
	{
		return( dlg.getShell());
	}
	
	private Rectangle
	getBounds()
	{
		return( dlg.getShell().getBounds());
	}

	private Image
	loadImage(
		String		key )
	{
		 Image img = image_loader.getImage( key );

		 if ( img != null ){

			 images_to_dispose.add( key );
		 }

		 return( img );
	}

	private void
	unloadImage(
		String	key )
	{
		image_loader.releaseImage( key );
	}

	protected void
	dispose()
	{
		UIUpdaterSWT.getInstance().removeUpdater(this);

		for ( OpenTorrentInstance instance: open_instances ){

			instance.dispose();
		}

		for ( String key: images_to_dispose ){

			unloadImage( key );
		}

		images_to_dispose.clear();

		tvTorrents.delete();
	}

	protected class
	OpenTorrentInstance
		implements TableViewFilterCheck<TorrentOpenFileOptions>, ParameterListener
	{
		final private HashWrapper						hash;
		
		final private TorrentOpenOptions 				torrentOptions;
		final private List<TorrentOpenOptions>			torrentOptionsMulti;
		
		final private boolean							isSingleOptions;		// true -> torrentOptions is active, false -> torrentOptions is null and torrentOptionsMulti active
		
		final private OpenTorrentInstanceListener		changeListener;

		final private Composite	parent;
		final private Shell		shell;

		private SWTSkin skin;

		/* prevents loop of modifications */
		protected boolean bSkipDataDirModify = false;

		private Button btnTreeView;
		private Button btnPrivacy;
		private Button btnCheckComments;
		private Button btnCheckAvailability;
		private Button btnSwarmIt;

		private List<Button>	network_buttons = new ArrayList<>();

		private boolean cmbDataDirEnabled = true;
		private Combo cmbDataDir;
		private Button btnDataDir;
		private Button btnSearch;

		private Combo cmbQueueLocation;

		private Button btnSequentialDownload;
		
		private Combo cmbStartMode;

		private volatile boolean diskFreeInfoRefreshPending = false;

		private volatile boolean diskFreeInfoRefreshRunning = false;

		private Composite diskspaceComp;

		private long	currentSelectedDataSize;

		private Map<File,FileStatsCacheItem> fileStatCache = new HashMap<>();

		private Map<File,File> parentToRootCache = new HashMap<>();

		private SWTSkinObjectExpandItem soExpandItemFiles;

		private SWTSkinObjectExpandItem soExpandItemSaveTo;

		private SWTSkinObjectExpandItem soExpandItemTorrentInfo;

		private SWTSkinObjectText soFileAreaInfo;

		private TableViewSWT<TorrentOpenFileOptions> tvFiles;

		private Text txtSubFolder;

		private SWTSkinObjectExpandItem soStartOptionsExpandItem;

		//private SWTSkinObjectExpandItem soExpandItemPeer;

		private AtomicInteger settingToDownload = new AtomicInteger(0);

		private Button btnSelectAll;
		private Button btnMarkSelected;
		private Button btnUnmarkSelected;
		private Button btnRename;
		private Button btnRetarget;
		private Composite tagButtonsArea;

		private TagFeatureFileLocation		tag_save_location;
		
		private boolean 		treeViewDisableUpdates;
		private Set<TreeNode>	treePendingExpansions = new HashSet<>();
		private TagButtonsUI tagButtonsUI;

		private
		OpenTorrentInstance(
			HashWrapper						_hash,
			Composite						_parent,
			TorrentOpenOptions				_torrentOptions,
			OpenTorrentInstanceListener		_changeListener )
		{
			hash				= _hash;
			parent				= _parent;
			
			torrentOptions 		= _torrentOptions;
			torrentOptionsMulti	= new ArrayList<>();

			torrentOptionsMulti.add( torrentOptions );

			isSingleOptions		= true;
			
			changeListener		= _changeListener;

			shell = parent.getShell();

			COConfigurationManager.addParameterListener(
				new String[]{
					"File.Torrent.AutoSkipExtensions",
					"File.Torrent.AutoSkipFiles",
					"File.Torrent.AutoSkipFiles.RegExp",
					"File.Torrent.AutoSkipMinSizeKB",
					
					"priorityExtensions",
					"priorityExtensionsIgnoreCase",
				}, this );
				
				
			torrentOptions.addListener(new TorrentOpenOptions.FileListener() {
				@Override
				public void toDownloadChanged(TorrentOpenFileOptions fo, boolean toDownload) {
					TableRowCore row = tvFiles.getRow(fo);
					if (row != null) {
						row.invalidate(true);
						row.refresh(true);
					}
					if ( settingToDownload.get() == 0 ){
						updateFileButtons();
						updateSize();
					}
				}
				@Override
				public void priorityChanged(TorrentOpenFileOptions fo, int priority) {
					TableRowCore row = tvFiles.getRow(fo);
					if (row != null) {
						row.invalidate(true);
						row.refresh(true);
					}
				}
				@Override
				public void parentDirChanged(){
					if ( isSingleOptions && cmbDataDir != null ){
						String toText = torrentOptions.getParentDir();
						String text = cmbDataDir.getText();

						if ( !text.equals( toText )){

							cmbDataDir.setText( toText );
						}
					}
				}
				
				@Override
				public void initialTagsChanged(){
					updateStartOptionsHeader();
					buildTagButtonPanel();
				}
				
				@Override
				public void startOptionsChanged(){
					if ( isSingleOptions ){
						cmbStartMode.select(torrentOptions.getStartMode());
						cmbQueueLocation.select(torrentOptions.getQueueLocation());
						btnSequentialDownload.setSelection( torrentOptions.getSequentialDownload());
						
						updateStartOptionsHeader();
					}
				}
			});

			if ( TagManagerFactory.getTagManager().isEnabled()){

				try {
					RelatedContentManager rcm = RelatedContentManager.getSingleton();

					Map<String,Boolean> enabledNetworks = torrentOptions.getEnabledNetworks();

					List<String> networks = new ArrayList<>();

					for ( Map.Entry<String,Boolean> entry: enabledNetworks.entrySet()){

						if ( entry.getValue()){

							networks.add( entry.getKey());
						}
					}

					if ( networks.size() > 0 ){

						final String[] nets = networks.toArray( new String[networks.size()]);

						List<String>	tag_cache = TorrentUtils.getTagCache( torrentOptions.getTorrent());

						synchronized( listDiscoveredTags ){
							for ( String tag: tag_cache ){
								String lcTag = tag.toLowerCase();
								if ( !listDiscoveredTags.containsKey( lcTag )){
									listDiscoveredTags.put( lcTag, new DiscoveredTag( tag, nets));								
									torrentOptions.addSwarmTag( tag );
								}
							}
						}
						rcm.lookupAttributes(
							hash.getBytes(),
							nets,
							new RelatedAttributeLookupListener(){

								@Override
								public void lookupStart() {
								}

								@Override
								public void tagFound(String tag, String network) {

									String lcTag = tag.toLowerCase();
									synchronized( listDiscoveredTags ){
										if (listDiscoveredTags.containsKey(lcTag)) {
											return;
										}
										listDiscoveredTags.put( lcTag, new DiscoveredTag( tag, nets));
										torrentOptions.addSwarmTag( tag );
									}

									buildTagButtonPanel();
								}

								@Override
								public void lookupComplete() {
								}

								@Override
								public void lookupFailed(ContentException error) {
								}
							});
						}

				} catch (ContentException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

			}

		}

		private
		OpenTorrentInstance(
			Composite						_parent,
			List<TorrentOpenOptions>		_torrentOptionsMulti,
			OpenTorrentInstanceListener		_changeListener )
		{
			hash				= null;
			parent				= _parent;
			
			torrentOptions 		= null;
			torrentOptionsMulti = new ArrayList<>( _torrentOptionsMulti );
			
			isSingleOptions = false;
			
			changeListener		= _changeListener;

			shell = parent.getShell();
		}

		private HashWrapper
		getHash()
		{
			return( hash );
		}

		protected TorrentOpenOptions
		getOptions()
		{
			return( torrentOptions );
		}

		protected int
		getIndex()
		{
			return( open_instances.indexOf( this ));
		}

		protected Composite
		getComposite()
		{
			return( parent );
		}

		@Override
		public void 
		parameterChanged(String parameterName){
			if ( isSingleOptions ){
				
				torrentOptions.applyPriorityAndSkipConfig();
			}
		}
		
		private void
		initialize()
		{
			skin = SWTSkinFactory.getNonPersistentInstance(
						getClass().getClassLoader(),
						"com/biglybt/ui/skin", "skin3_dlg_opentorrent_options_instance.properties" );

			skin.initialize( parent, "expandview");
			
			if ( isSingleOptions ){
				SWTSkinObject so = skin.getSkinObject("filearea-table");
				if (so instanceof SWTSkinObjectContainer) {
					setupTVFiles((SWTSkinObjectContainer) so, (SWTSkinObjectTextbox)skin.getSkinObject("filearea-filter"));
				}

				so = skin.getSkinObject("filearea-buttons");
				if (so instanceof SWTSkinObjectContainer) {
					setupFileAreaButtons((SWTSkinObjectContainer) so);
				}
			}else{
				SWTSkinObjectExpandItem expInfo		= (SWTSkinObjectExpandItem)skin.getSkinObject("expanditem-torrentinfo");
								
				SWTSkinObjectExpandItem expFiles	= (SWTSkinObjectExpandItem)skin.getSkinObject("expanditem-files");
				
				SWTSkinObjectExpandItem expPeer		= (SWTSkinObjectExpandItem)skin.getSkinObject("expanditem-peer");
				
				expInfo.setText( "" );
				expFiles.setText( "" );
				//expPeer.setText( "" );
			}

			SWTSkinObject so = skin.getSkinObject("disk-space");
			if (so instanceof SWTSkinObjectContainer) {
				diskspaceComp = (Composite) so.getControl();
				GridLayout gl = new GridLayout(2, false);
				gl.marginHeight = gl.marginWidth = 0;
				diskspaceComp.setLayout(gl);
				Label l = new Label(diskspaceComp, SWT.NONE);
				l.setText( "" );	// start with this to avoid UI re-layout from moving suff as user enters text etc
			}

			if ( isSingleOptions ){
				so = skin.getSkinObject("filearea-info");
				if (so instanceof SWTSkinObjectText) {
					setupFileAreaInfo((SWTSkinObjectText) so);
				}
			}else{
				skin.getSkinProperties().addProperty( "toptions.filearea.fillheight", null );
				skin.getSkinProperties().addProperty( "toptions.filearea.fillheightmin", null );
			}
			
			so = skin.getSkinObject("start-options");
			if (so instanceof SWTSkinObjectExpandItem) {
				setupStartOptions((SWTSkinObjectExpandItem) so);
			}

			if ( isSingleOptions ){
				so = skin.getSkinObject("peer-sources");
				if (so instanceof SWTSkinObjectContainer) {
					setupPeerSourcesAndNetworkOptions((SWTSkinObjectContainer) so);
				}

				so = skin.getSkinObject("trackers");
				if (so instanceof SWTSkinObjectContainer) {
					setupTrackers((SWTSkinObjectContainer) so);
				}

				so = skin.getSkinObject("updownlimit");
				if (so instanceof SWTSkinObjectContainer) {
					setupUpDownLimitOption((SWTSkinObjectContainer) so);
				}

				so = skin.getSkinObject("ipfilter");
				if (so instanceof SWTSkinObjectContainer) {
					setupIPFilterOption((SWTSkinObjectContainer) so);
				}
			}else{
				so = skin.getSkinObject("trackers");
				if (so instanceof SWTSkinObjectContainer) {
					setupTrackers((SWTSkinObjectContainer) so);
				}
			}

			SWTSkinObject so_ta = skin.getSkinObject("saveto-textarea");
			SWTSkinObject so_b = skin.getSkinObject("saveto-browse");
			SWTSkinObject so_s = skin.getSkinObject("saveto-search");
			SWTSkinObject so_m = skin.getSkinObject("saveto-more");
			
			if (	(so_ta instanceof SWTSkinObjectContainer) &&
					(so_b instanceof SWTSkinObjectButton) &&
					(so_m instanceof SWTSkinObjectContainer)){
				
				setupSaveLocation(
					(SWTSkinObjectContainer) so_ta, 
					(SWTSkinObjectButton) so_b, 
					(SWTSkinObjectButton) so_s, 
					(SWTSkinObjectContainer)so_m );
			}

			so = skin.getSkinObject("expanditem-saveto");
			if (so instanceof SWTSkinObjectExpandItem) {
				soExpandItemSaveTo = (SWTSkinObjectExpandItem) so;
			}
			if ( isSingleOptions ){

				so = skin.getSkinObject("expanditem-files");
				if (so instanceof SWTSkinObjectExpandItem) {
					soExpandItemFiles = (SWTSkinObjectExpandItem) so;
				}

				/*
				so = skin.getSkinObject("expanditem-peer");
				if (so instanceof SWTSkinObjectExpandItem) {
					soExpandItemPeer = (SWTSkinObjectExpandItem) so;
				}
				*/

				setupInfoSection(skin);
			}
			updateStartOptionsHeader();
			
			if ( tagButtonsUI != null ){
			
				updateInitialSaveTags( tagButtonsUI.getSelectedTags(), null );
			}
			
			if ( isSingleOptions ){
				cmbDataDirChanged();
				updateSize();
			}else{

				updateDataDirCombo();
			}

			skin.layout();
			
			if ( btnDataDir != null && btnSearch != null ){
			
				Utils.makeButtonsEqualWidth( btnDataDir, btnSearch );
			}
		}

		private void
		layout()
		{
			SWTSkinObjectExpandItem so = (SWTSkinObjectExpandItem)skin.getSkinObject("expanditem-saveto");

			if ( so != null ){
				
				SWTSkinObjectExpandBar bar = (SWTSkinObjectExpandBar)so.getParent();
	
				bar.relayout();
	
				for ( SWTSkinObjectExpandItem item: bar.getChildren()){
	
					item.relayout();
				}
			}
		}

		private void
		refresh()
		{
			if ( tagButtonsArea == null || tagButtonsArea.isDisposed()){

				return;
			}

			tagButtonsUI.updateFields(null);
		}

		private void
		showTreeView()
		{
			final Shell tree_shell = ShellFactory.createShell( shell, SWT.DIALOG_TRIM | SWT.RESIZE );

			Utils.setShellIcon(tree_shell);

			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			tree_shell.setLayout(layout);

			Utils.verifyShellRect(tree_shell, true);

			TOTorrent t = torrentOptions.getTorrent();

			Composite comp = new Composite( tree_shell, SWT.NULL );
			GridData gridData = new GridData( GridData.FILL_BOTH );
			comp.setLayoutData(gridData);

			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			comp.setLayout(layout);

			TOTorrentFile[]	torrent_files = t.getFiles();

			TorrentOpenFileOptions[] files = torrentOptions.getFiles();

			char file_separator = File.separatorChar;

			final TreeNode	root = new TreeNode( null, "" );

			final Map<TorrentOpenFileOptions,TreeNode>	file_map = new HashMap<>();

			for ( TorrentOpenFileOptions file: files ){

				TreeNode node = root;

				TOTorrentFile t_file = torrent_files[file.getIndex()];

				String path = t_file.getRelativePath();

				int	pos = 0;
				int	len = path.length();

				while( true ){

					int p = path.indexOf( file_separator, pos );

					String	bit;

					if ( p == -1 ){

						bit = path.substring( pos );

					}else{

						bit = path.substring( pos, p );

						pos = p+1;
					}

					TreeNode n = node.getChild( bit );

					if ( n == null ){

						n = new TreeNode( node, bit );

						node.addChild( n );
					}

					node = n;

					if ( p == -1 || pos == len) {

						node.setFile( file );

						file_map.put( file,  node );

						break;
					}
				}
			}

			treePendingExpansions.clear();

			final Tree tree = new Tree(comp, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI | SWT.CHECK | SWT.V_SCROLL | SWT.H_SCROLL);

			gridData = new GridData(GridData.FILL_BOTH);
			tree.setLayoutData(gridData);

			tree.setHeaderVisible(true);
			tree.setLinesVisible( true );

			int[]	COL_WIDTHS = { 600, 80, 80 };

			TreeColumn column1 = new TreeColumn(tree, SWT.LEFT);
			column1.setText( MessageText.getString("TableColumn.header.name"));

			TreeColumn column2 = new TreeColumn(tree, SWT.RIGHT);
			column2.setText( MessageText.getString("TableColumn.header.size"));

			TreeColumn column3 = new TreeColumn(tree, SWT.RIGHT);
			column3.setText( MessageText.getString("SpeedView.stats.total"));

			TreeColumn[] columns = { column1, column2, column3 };

			SelectionAdapter column_listener =
				new SelectionAdapter() {

					@Override
					public void widgetSelected(SelectionEvent e) {

						TreeColumn column = (TreeColumn)e.widget;

						int	index = (Integer)column.getData( "index" );

						boolean	asc = (Boolean)column.getData( "asc" );

						asc = !asc;

						column.setData( "asc", asc );

						sortTree( tree, root, index, asc );
					}
				};

			for ( int i=0;i<columns.length;i++){

				final TreeColumn column = columns[i];

				column.setData( "asc", true );
				column.setData( "index", i );

				column.addSelectionListener( column_listener );

				final String key = "open.torrent.window.tree.col." + i;

				int	width = COConfigurationManager.getIntParameter( key, COL_WIDTHS[i] );

				column.setWidth( Math.max( 20, width ));

				column.addListener(
					SWT.Resize,
					new Listener()
					{
						@Override
						public void
						handleEvent(
							Event event)
						{
							COConfigurationManager.setParameter( key, column.getWidth());
						}
					});
			}

			tree.setData(root);

			tree.addListener(SWT.SetData, new Listener() {
				@Override
				public void handleEvent(Event event) {
					final TreeItem item = (TreeItem)event.item;
					TreeItem parentItem = item.getParentItem();

					TreeNode parent_node;

					if ( parentItem == null ){

						parent_node = root;

					}else{

						parent_node = (TreeNode)parentItem.getData();
					}


					TreeNode[] kids = parent_node.getChildren();

					TreeNode node = kids[event.index];

					item.setData( node );

					updateTreeItem( item, node );

					TreeNode[] node_kids = node.getChildren();

					if ( node_kids.length > 0 ) {

						item.setItemCount( node_kids.length );
					}
				}
			});

			tree.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (event.detail == SWT.CHECK) {

						TreeItem item = (TreeItem)event.item;

						boolean checked = item.getChecked();

						TreeNode node = (TreeNode)item.getData();

						updateNodeFromTree( tree, item, node, checked );
					}
				}});

			final Menu menu = new Menu(tree);

		    tree.setMenu(menu);

		    menu.addMenuListener(
		    	new MenuAdapter()
		    	{
		    		@Override
				    public void
		    		menuShown(
		    			MenuEvent e )
		    		{
		    			MenuItem[] menu_items = menu.getItems();

		    			for (int i = 0; i < menu_items.length; i++){

		    				menu_items[i].dispose();
		    			}

		    			boolean	has_selected	= false;
		    			boolean	has_deselected	= false;

		    			final TreeItem[] items = tree.getSelection();

		    			for ( TreeItem item: items ){
		    				if ( item.getChecked()){
		    					has_selected = true;
		    				}else{
		    					has_deselected = true;
		    				}
		    			}

		    			MenuItem select_item = new MenuItem(menu, SWT.NONE);
		    			select_item.setText( MessageText.getString( "label.select" ));

		    			select_item.addSelectionListener(
	    					new SelectionAdapter() {

								@Override
								public void widgetSelected(SelectionEvent e) {
									for ( TreeItem item: items ){

										item.setChecked( true );

										TreeNode node = (TreeNode)item.getData();

										updateNodeFromTree( tree, item, node, true );
									}
								}

							});

		    			select_item.setEnabled( has_deselected );

		    			MenuItem deselect_item = new MenuItem(menu, SWT.NONE);
		    			deselect_item.setText( MessageText.getString( "label.deselect" ));

		    			deselect_item.addSelectionListener(
	    					new SelectionAdapter() {

								@Override
								public void widgetSelected(SelectionEvent e) {
									for ( TreeItem item: items ){

										item.setChecked( false );

										TreeNode node = (TreeNode)item.getData();

										updateNodeFromTree( tree, item, node, false );
									}
								}

							});

		    			deselect_item.setEnabled( has_selected );

		    			final TreeItem[] ex_items=items.length==0?tree.getItems():items;

		    			final Set<TreeNode>	unexpanded_nodes = getUnExpandedNodes(ex_items);

		    			MenuItem expand_item = new MenuItem(menu, SWT.NONE);
		    			expand_item.setText( MessageText.getString( "label.expand.all" ));

		    			expand_item.addSelectionListener(
	    					new SelectionAdapter() {

								@Override
								public void widgetSelected(SelectionEvent e) {

									treePendingExpansions.addAll( unexpanded_nodes );

									expandItems( ex_items );
								}
							});

		    			expand_item.setEnabled( unexpanded_nodes.size() > 0 );
		    		}
		    	});

		    tree.addKeyListener(
		    	new KeyAdapter()
		    	{
		    		@Override
				    public void
		    		keyPressed(KeyEvent e)
		    		{
		    			int key = e.character;

		    			if ( key <= 26 && key > 0 ){

		    				key += 'a' - 1;
		    			}

		    			if ( e.stateMask == SWT.MOD1 ){

		    				if ( key == 'a' ){

		    					tree.selectAll();
		    				}
		    			}
		    		}
		    	});

			tree.setItemCount( root.getChildren().length );

				// line

			Label labelSeparator = new Label( comp, SWT.SEPARATOR | SWT.HORIZONTAL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			labelSeparator.setLayoutData(gridData);

				// buttons

			Composite buttonComp = new Composite( comp, SWT.NULL );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			buttonComp.setLayoutData(gridData);

			layout = new GridLayout();
			layout.numColumns = 2;
			buttonComp.setLayout(layout);

			new Label(buttonComp,SWT.NULL);

			Composite buttonArea = new Composite(buttonComp,SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			buttonArea.setLayoutData(gridData);
			GridLayout layoutButtons = new GridLayout();
			layoutButtons.numColumns = 1;
			buttonArea.setLayout(layoutButtons);

			List<Button>	buttons = new ArrayList<>();

			Button bOK = new Button(buttonArea,SWT.PUSH);
			buttons.add( bOK );

			bOK.setText(MessageText.getString("Button.ok"));
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.widthHint = 70;
			bOK.setLayoutData(gridData);
			bOK.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event e) {
					tree_shell.dispose();
				}
			});

			Utils.makeButtonsEqualWidth( buttons );

			tree_shell.setDefaultButton( bOK );

			btnTreeView.setEnabled( false );

			final FileListener	file_listener =
				new FileListener()
				{
					@Override
					public void
					toDownloadChanged(
						TorrentOpenFileOptions 	file,
						boolean 				checked )
					{
						updateNodeFromTable( tree, file_map.get( file ), checked );
					}
					@Override
					public void priorityChanged(TorrentOpenFileOptions torrentOpenFileOptions, int priority ){}
					@Override
					public void parentDirChanged(){}
					@Override
					public void initialTagsChanged(){}
			};

			torrentOptions.addListener( file_listener );

			tree_shell.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					if ( !btnTreeView.isDisposed()){

						btnTreeView.setEnabled( true );
						torrentOptions.removeListener( file_listener );
					}
				}
			});

			tree_shell.addListener(
				SWT.Resize,
				new Listener()
				{
					@Override
					public void
					handleEvent(
						Event event)
					{
						Rectangle bounds = tree_shell.getBounds();

						COConfigurationManager.setParameter( "open.torrent.window.tree.size", bounds.width + "," + bounds.height );
					}
				});

			int	shell_width		= 800;
			int shell_height	= 400;

			try{
				String str = COConfigurationManager.getStringParameter( "open.torrent.window.tree.size", "" );

				String[] bits = str.split(",");

				if ( bits.length == 2 ){
					shell_width 	= Math.max( 300, Integer.parseInt( bits[0]));
					shell_height 	= Math.max( 200, Integer.parseInt( bits[1]));
				}
			}catch( Throwable e ){
			}

			tree_shell.setSize( shell_width, shell_height );
			tree_shell.layout(true, true);

			Utils.centerWindowRelativeTo(tree_shell,shell);

			String title = torrentOptions.getDisplayName();

			Messages.setLanguageText( tree_shell, "torrent.files.title", new String[]{ title });

			tree_shell.open();
		}

		private void
		sortTree(
			final Tree			tree,
			TreeNode			root,
			final int			col_index,
			final boolean		asc )
		{
			Comparator<TreeNode>	comparator =
				new Comparator<TreeNode>() {

					@Override
					public int
					compare(
						TreeNode n1,
						TreeNode n2)
					{
						if ( !asc ){
							TreeNode temp = n1;
							n1 = n2;
							n2 = temp;
						}

						if ( col_index == 0 ){

							String	name1 = n1.getName();
							String	name2 = n2.getName();

							return( tree_comp.compare( name1, name2 ));

						}else if ( col_index == 1 || col_index == 2 ){

							long	size1 = n1.getSize();
							long	size2 = n2.getSize();

							if ( size1<0){
								size1 = -size1;
							}
							if ( size2<0){
								size2 = -size2;
							}
							long result = size1 - size2;

							if ( result == 0 ){
								return( 0 );
							}else if ( result < 0 ){
								return( -1 );
							}else{
								return( 1 );
							}
						}else{

							return( 0 );
						}
					}
				};

			getExpandedNodes( tree.getItems(), treePendingExpansions );

			tree.removeAll();

			root.sort( comparator );

			tree.setItemCount( root.getChildren().length );
		}

		private void
		getExpandedNodes(
			TreeItem[]			items,
			Set<TreeNode>		nodes )
		{
			for ( TreeItem item: items ){

				if ( item.getExpanded()){

					nodes.add((TreeNode)item.getData());
				}

				getExpandedNodes( item.getItems(), nodes );
			}
		}

		private Set<TreeNode>
		getUnExpandedNodes(
			TreeItem[]		items )
		{
			Set<TreeNode>	all_nodes = new HashSet<>();

			for ( TreeItem item: items ){

				getNodes((TreeNode)item.getData(), all_nodes, true );
			}

			Set<TreeNode>	expanded_nodes = new HashSet<>();

			getExpandedNodes( items, expanded_nodes );

			all_nodes.removeAll( expanded_nodes );

			return( all_nodes );
		}

		private void
		expandItems(
			TreeItem[]	items )
		{
			for ( TreeItem item: items ){

				item.setExpanded( true );

				expandItems( item.getItems());
			}
		}

		private void
		getNodes(
			TreeNode		node,
			Set<TreeNode>	nodes,
			boolean			parents_only )
		{
			TreeNode[] kids = node.getChildren();

			if ( parents_only && kids.length == 0 ){

				return;
			}

			nodes.add( node );

			for ( TreeNode kid: kids ){

				getNodes( kid, nodes, parents_only );
			}
		}

		private void
		updateTreeItem(
			final TreeItem			item,
			final TreeNode			node )
		{
			long	size = node.getSize();

			String abs_size_str = DisplayFormatters.formatByteCountToKiBEtc( Math.abs( size ));

			String size_str;
			String total_str;

			if ( size >= 0 ){

				size_str 	= abs_size_str;
				total_str	= "";
			}else{
				size_str	= "";
				total_str	= abs_size_str;
			}

			item.setText(
				new String[]{
					node.getName(),
					size_str,
					total_str,
				});

			item.setChecked( node.isChecked());

			item.setGrayed(  node.isGrayed());
			item.setForeground(2, Colors.dark_grey );

			if ( treePendingExpansions.contains( node )){

				Utils.execSWTThreadLater(
					1,
					new Runnable() {

						@Override
						public void run()
						{
							if ( !item.isDisposed()){

								item.setExpanded( true );

								treePendingExpansions.remove( node );
							}
						}
					});

			}
		}

		private TreeItem
		getItemForNode(
			Tree		tree,
			TreeNode	node )
		{
			List<TreeNode>	nodes = new ArrayList<>();

			nodes.add( node );

			while( true ){

				TreeNode parent = node.getParent();

				if ( parent == null ){

					break;
				}

				nodes.add( parent );

				node = parent;
			}

			TreeItem target_item = null;

			for ( int i=nodes.size()-2;i>=0;i--){

				TreeNode n = nodes.get(i);

				TreeItem[] items;

				if ( target_item==null ){

					items = tree.getItems();

				}else{

					if ( target_item.getItemCount() == 0 ){

						continue;
					}

					items = target_item.getItems();
				}

				boolean found = false;

				for ( TreeItem item: items ){

					if ( item.getData() == n ){

						target_item = item;

						found = true;

						break;
					}
				}

				if ( !found ){

					return( null );
				}
			}

			return( target_item );
		}

		private void
		updateNodeFromTree(
			Tree		tree,
			TreeItem	item,
			TreeNode	node,
			boolean		selected )
		{
			try{
				treeViewDisableUpdates = true;

				boolean	refresh_path = false;

				TorrentOpenFileOptions file = node.getFile();

				if ( file != null ){

					if ( file.isToDownload() != selected ){

						file.setToDownload( selected );

						refresh_path = true;
					}
				}else{

					item.setGrayed( false );

					List<TorrentOpenFileOptions>	files = node.getFiles();

					for ( TorrentOpenFileOptions f: files ){

						if ( f.isToDownload() != selected ){

							f.setToDownload( selected );

							refresh_path = true;
						}
					}

					if ( refresh_path ){

						updateSubTree( item.getItems());
					}
				}

				if ( refresh_path ){

					while( true ){

						item 	= item.getParentItem();

						if ( item == null ){

							break;
						}

						node	= node.getParent();

						item.setChecked( node.isChecked());

						item.setGrayed( node.isGrayed());
					}
				}
			}finally{

				treeViewDisableUpdates = false;
			}
		}

		private void
		updateSubTree(
			TreeItem[]		items )
		{
			for ( TreeItem item: items ){

				TreeNode node = (TreeNode)item.getData();

				if ( node != null ){

					boolean	checked = node.isChecked();

					if ( item.getChecked() != checked ){

						item.setChecked( checked);
					}

					boolean	grayed = node.isGrayed();

					if ( item.getGrayed() != grayed ){

						item.setGrayed( grayed);
					}

					TreeItem[] sub_items = item.getItems();

					if ( sub_items.length > 0 ){

						updateSubTree( sub_items );
					}
				}
			}
		}

		private void
		updateNodeFromTable(
			Tree					tree,
			TreeNode				node,
			boolean					selected )
		{
			if ( treeViewDisableUpdates  ){

				return;
			}

			TreeItem item = getItemForNode( tree, node );

			if ( item != null ){

				if ( item.getChecked() != selected ){

					item.setChecked( selected );

					while( true ){

						item 	= item.getParentItem();

						if ( item == null ){

							break;
						}

						node	= node.getParent();

						item.setChecked( node.isChecked());

						item.setGrayed( node.isGrayed());
					}
				}
			}else{

				while( true ){

					node = node.getParent();

					if ( node == null ){

						break;
					}

					item = getItemForNode( tree, node );

					if ( item != null ){

						while( true ){

							item.setChecked( node.isChecked());

							item.setGrayed( node.isGrayed());


							item 	= item.getParentItem();

							if ( item == null ){

								break;
							}

							node	= node.getParent();
						}

						break;
					}
				}
			}
		}

		private void
		showAvailability()
		{
			final Shell avail_shell = ShellFactory.createShell( shell, SWT.DIALOG_TRIM | SWT.RESIZE );

			Utils.setShellIcon(avail_shell);

			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			avail_shell.setLayout(layout);

			Utils.verifyShellRect(avail_shell, true);

			TOTorrent t = torrentOptions.getTorrent();

			final TrackerAvailView view = new TrackerAvailView();

			String[] enabled_peer_sources = PEPeerSource.PS_SOURCES;

			if (torrentOptions.peerSource != null) {
				List<String>	temp = new ArrayList<>(Arrays.asList(enabled_peer_sources));
				for (String peerSource : torrentOptions.peerSource.keySet()) {
					boolean enable = torrentOptions.peerSource.get(peerSource);
					if ( !enable ){
						temp.remove( peerSource );
					}
				}
				enabled_peer_sources = temp.toArray( new String[temp.size()]);
			}

			String[] enabled_networks = AENetworkClassifier.AT_NETWORKS;

			Map<String,Boolean> enabledNetworks = torrentOptions.getEnabledNetworks();

			if ( enabledNetworks != null ){
				List<String>	temp = new ArrayList<>(Arrays.asList(enabled_networks));
				for (String net : enabledNetworks.keySet()) {
					boolean enable = enabledNetworks.get(net);
					if ( !enable ){
						temp.remove( net );
					}
				}
				enabled_networks = temp.toArray( new String[temp.size()]);
			}

			final DownloadManagerAvailability availability =
				DownloadManagerFactory.getAvailability(
					t,
					torrentOptions.getTrackers( true ),
					enabled_peer_sources,
					enabled_networks );

			Composite comp = new Composite( avail_shell, SWT.NULL );
			GridData gridData = new GridData( GridData.FILL_BOTH );
			comp.setLayoutData(gridData);

			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			comp.setLayout(layout);

			view.setEnableTabViews( false );
			
			view.initialize(comp);
			
			view.dataSourceChanged( availability );

			view.viewActivated();
			view.refresh();

			final UIUpdatable viewUpdater = new UIUpdatable() {
				@Override
				public void updateUI() {
					view.refresh();
				}

				@Override
				public String getUpdateUIName() {
					return view.getFullTitle();
				}
			};

			UIUpdaterSWT.getInstance().addUpdater(viewUpdater);

				// progress

			Composite progressComp = new Composite( comp, SWT.NULL );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			progressComp.setLayoutData(gridData);

			layout = new GridLayout();
			layout.numColumns = 2;
			progressComp.setLayout(layout);

			Label progLabel = new Label(progressComp,SWT.NULL);
			progLabel.setText( MessageText.getString("label.checking.sources"));

			final Composite progBarComp = new Composite( progressComp, SWT.NULL );
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			//gridData.widthHint = 400;
			progBarComp.setLayoutData(gridData);

			//Label padLabel = new Label(progressComp,SWT.NULL);
			//gridData = new GridData(GridData.FILL_HORIZONTAL);
			//Utils.setLayoutData(padLabel, gridData);

			final StackLayout	progStackLayout = new StackLayout();

			progBarComp.setLayout( progStackLayout);

			final ProgressBar progBarIndeterminate = new ProgressBar(progBarComp, SWT.HORIZONTAL | SWT.INDETERMINATE);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			progBarIndeterminate.setLayoutData(gridData);

			final ProgressBar progBarComplete = new ProgressBar(progBarComp, SWT.HORIZONTAL );
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			progBarComplete.setLayoutData(gridData);
			progBarComplete.setMaximum( 1 );
			progBarComplete.setSelection( 1 );

			progStackLayout.topControl = progBarIndeterminate;

			new AEThread2( "ProgChecker" )
			{
				@Override
				public void
				run()
				{
					boolean	currently_updating = true;

					while( true ){

						if ( avail_shell.isDisposed()){

							return;
						}

						final boolean	updating = view.isUpdating();

						if ( updating != currently_updating ){

							currently_updating = updating;

							Utils.execSWTThread(
								new Runnable()
								{
									@Override
									public void
									run()
									{
										if ( !avail_shell.isDisposed()){

											progStackLayout.topControl = updating?progBarIndeterminate:progBarComplete;

											progBarComp.layout();
										}
									}
								});
						}

						try{
							Thread.sleep(500);

						}catch( Throwable e ){

						}
					}
				}
			}.start();

				// line

			Label labelSeparator = new Label( comp, SWT.SEPARATOR | SWT.HORIZONTAL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			labelSeparator.setLayoutData(gridData);

				// buttons

			Composite buttonComp = new Composite( comp, SWT.NULL );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			buttonComp.setLayoutData(gridData);

			layout = new GridLayout();
			layout.numColumns = 2;
			buttonComp.setLayout(layout);

			new Label(buttonComp,SWT.NULL);

			Composite buttonArea = new Composite(buttonComp,SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			buttonArea.setLayoutData(gridData);
			GridLayout layoutButtons = new GridLayout();
			layoutButtons.numColumns = 1;
			buttonArea.setLayout(layoutButtons);

			List<Button>	buttons = new ArrayList<>();

			Button bOK = new Button(buttonArea,SWT.PUSH);
			buttons.add( bOK );

			bOK.setText(MessageText.getString("Button.ok"));
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.widthHint = 70;
			bOK.setLayoutData(gridData);
			bOK.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event e) {
					avail_shell.dispose();
				}
			});

			Utils.makeButtonsEqualWidth( buttons );

			avail_shell.setDefaultButton( bOK );

			avail_shell.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					try{
						UIUpdaterSWT.getInstance().removeUpdater(viewUpdater);

						if ( !btnCheckAvailability.isDisposed()){

							btnCheckAvailability.setEnabled( true );
						}
					}finally{

						availability.destroy();
					}
				}
			});

			btnCheckAvailability.setEnabled( false );

			avail_shell.setSize( 800, 400 );
			avail_shell.layout(true, true);

			Utils.centerWindowRelativeTo(avail_shell,shell);

			String title = torrentOptions.getDisplayName();

			Messages.setLanguageText( avail_shell, "torrent.avail.title", new String[]{ title });

			avail_shell.open();
		}

		private void
		showComments()
		{
			final Shell comments_shell = ShellFactory.createShell( shell, SWT.DIALOG_TRIM | SWT.RESIZE );

			Utils.setShellIcon(comments_shell);

			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			comments_shell.setLayout(layout);

			Utils.verifyShellRect(comments_shell, true);

			TOTorrent torrent = torrentOptions.getTorrent();

			String title = torrentOptions.getDisplayName();

			String[] enabled_networks = AENetworkClassifier.AT_NETWORKS;

			Map<String,Boolean> enabledNetworks = torrentOptions.getEnabledNetworks();

			if ( enabledNetworks != null ){
				List<String>	temp = new ArrayList<>(Arrays.asList(enabled_networks));
				for (String net : enabledNetworks.keySet()) {
					boolean enable = enabledNetworks.get(net);
					if ( !enable ){
						temp.remove( net );
					}
				}
				enabled_networks = temp.toArray( new String[temp.size()]);
			}

			final String[] f_enabled_networks = enabled_networks;

			Composite comp = new Composite( comments_shell, SWT.NULL );

			GridData gridData = new GridData( GridData.FILL_BOTH );
			comp.setLayoutData(gridData);

			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			comp.setLayout(layout);

			Composite topComp = new Composite( comp, SWT.NULL );
			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			topComp.setLayout(layout);
			gridData = new GridData( GridData.FILL_BOTH );
			topComp.setLayoutData(gridData);

			String active_networks_str = "";

			for ( String net: enabled_networks ){

				active_networks_str +=
						(active_networks_str.length()==0?"":", ") +
						MessageText.getString( "ConfigView.section.connection.networks." + net );
			}

			if ( active_networks_str.length() == 0 ){

				active_networks_str = MessageText.getString("label.none");
			}

			Label info_label = new Label( topComp, SWT.WRAP );
			info_label.setText( MessageText.getString( "torrent.comments.info", new String[]{active_networks_str}));
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			gridData.horizontalIndent = 8;
			gridData.verticalIndent = 8;
			info_label.setLayoutData(gridData);

				// azrating plugin

			Group ratingComp = Utils.createSkinnedGroup( topComp, SWT.NULL );
			layout = new GridLayout();
			layout.numColumns = 1;

			ratingComp.setLayout(layout);
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			ratingComp.setLayoutData(gridData);

			ratingComp.setText( "Rating Plugin" );

			Composite ratingComp2 = new Composite( ratingComp, SWT.BORDER );
			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 4;
			layout.marginHeight = 4;
			ratingComp2.setLayout(layout);
			gridData = new GridData( GridData.FILL_BOTH );
			ratingComp2.setLayoutData(gridData);
			if ( !Utils.isDarkAppearanceNative()) {
				ratingComp2.setBackground( Colors.white );
			}

			final Label ratingText = new Label( ratingComp2, SWT.WRAP );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			gridData.heightHint=ratingText.getFont().getFontData()[0].getHeight() * 2 + 16;
			ratingText.setLayoutData(gridData);
			if ( !Utils.isDarkAppearanceNative()) {
				ratingText.setBackground( Colors.white );
			}

			final boolean[]	az_rating_in_progress = { false };

			try{
				PluginManager pm = CoreFactory.getSingleton().getPluginManager();

				PluginInterface rating_pi = pm.getPluginInterfaceByID( "azrating" );

				if ( rating_pi != null ){

					final IPCInterface ipc = rating_pi.getIPC();

					if ( ipc.canInvoke( "lookupRatingByHash", new Object[]{ new String[0], new byte[0] })){

						az_rating_in_progress[0] = true;

						ratingText.setText( MessageText.getString( "label.searching" ));

						new AEThread2( "oto:rat" )
						{
							@Override
							public void
							run()
							{	Map result = null;

								try{
									result = (Map)ipc.invoke( "lookupRatingByHash", new Object[]{ f_enabled_networks, hash.getBytes() });

								}catch( Throwable e ){

									e.printStackTrace();

								}finally{

									synchronized( az_rating_in_progress ){

										az_rating_in_progress[0] = false;
									}

									if ( !ratingText.isDisposed()){

										final Map f_result = result;

										Utils.execSWTThread(
											new Runnable()
											{
												@Override
												public void
												run()
												{
													if ( !ratingText.isDisposed()){

														String text 	= "";
														String	tooltip = "";

														if ( f_result != null ){

															List<Map> ratings = (List<Map>)f_result.get( "ratings" );

															if ( ratings != null ){

																String 			scores_str = "";
																List<String>	comments = new ArrayList<>();

																double	total_score = 0;
																int		score_num	= 0;

																for ( Map map: ratings ){

																	try{
																		int 	score	= ((Number)map.get( "score" )).intValue();

																		total_score += score;
																		score_num++;

																		scores_str += (scores_str.length()==0?"":", ") + score;

																		String comment 	= MapUtils.getMapString(map, "comment", null );

																		if ( comment != null ){

																			comment = comment.trim();

																			if ( comment.length() > 0 ){

																				comments.add( comment );
																			}
																		}
																	}catch( Throwable e ){

																	}
																}

																if ( score_num > 0 ){

																	double average = total_score/score_num;

																	text = MessageText.getString(
																				"torrent.comment.rat1",
																				new String[]{
																					DisplayFormatters.formatDecimal(average,1),
																					scores_str
																				});

																	int num_comments = comments.size();

																	if ( num_comments > 0 ){

																		text += "\n    " + MessageText.getString(
																				"torrent.comment.rat2",
																				new String[]{
																					comments.get(0) + (num_comments==1?"":"..." )
																				});

																		for ( String comment: comments ){

																			tooltip += (tooltip.length()==0?"":"\n") + comment;
																		}
																	}
																}
															}


														}

														if ( text.length()==0 ){

															text = MessageText.getString("label.none");
														}

														ratingText.setText( text );
														Utils.setTT(ratingText, tooltip );
													}
												}
											});
									}
								}
							}
						}.start();

					}else{

						ratingText.setText( "Rating Plugin needs updating" );
					}
				}else{

					ratingText.setText( MessageText.getString( "torrent.comment.azrating.install" ));
				}
			}catch( Throwable e ){

				ratingText.setText( "Rating Plugin failed: " + Debug.getNestedExceptionMessage(e));
			}

				// chat

			Group chatComp = Utils.createSkinnedGroup( topComp, SWT.NULL );
			layout = new GridLayout();
			layout.numColumns = 1;
			chatComp.setLayout(layout);
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			chatComp.setLayoutData(gridData);

			chatComp.setText( "Chat Plugin" );

			Map<String,Object>	chat_properties = new HashMap<>();

			chat_properties.put( BuddyPluginViewInterface.VP_SWT_COMPOSITE, chatComp );

			final String  chat_key = BuddyPluginUtils.getChatKey( torrent );

			BuddyPluginViewInterface.DownloadAdapter
				adapter = new BuddyPluginViewInterface.DownloadAdapter(){

					@Override
					public String[]
					getNetworks()
					{
						return( f_enabled_networks );
					}

					@Override
					public DownloadManager
					getCoreDownload()
					{
						return( null );
					}

					@Override
					public String
					getChatKey()
					{
						return( chat_key );
					}
				};

			chat_properties.put( BuddyPluginViewInterface.VP_DOWNLOAD, adapter );

			final Set<ChatInstance>	activated_chats = new HashSet<>();

			final BuddyPluginViewInterface.View chat_view =
				BuddyPluginUtils.buildChatView(
					chat_properties,
					new BuddyPluginViewInterface.ViewListener() {

						@Override
						public void
						chatActivated(
							ChatInstance chat)
						{
							synchronized( az_rating_in_progress ){

								activated_chats.add( chat );
							}
						}
					});

			if ( chat_view == null ){

				Composite chatComp2 = new Composite( chatComp, SWT.BORDER );
				layout = new GridLayout();
				layout.numColumns = 1;
				layout.marginWidth = 4;
				layout.marginHeight = 4;
				chatComp2.setLayout(layout);
				gridData = new GridData( GridData.FILL_BOTH );
				chatComp2.setLayoutData(gridData);
				chatComp2.setBackground( Colors.white );

				final Label chatText = new Label( chatComp2, SWT.WRAP );
				gridData = new GridData( GridData.FILL_HORIZONTAL );
				gridData.heightHint=ratingText.getFont().getFontData()[0].getHeight() * 2 + 16;
				chatText.setLayoutData(gridData);
				chatText.setBackground( Colors.white );

				chatText.setText( MessageText.getString( "torrent.comment.azmsgsync.install" ));
			}

				// progress

			Composite progressComp = new Composite( comp, SWT.NULL );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			progressComp.setLayoutData(gridData);

			layout = new GridLayout();
			layout.numColumns = 3;
			progressComp.setLayout(layout);

			Label progLabel = new Label(progressComp,SWT.NULL);
			progLabel.setText( MessageText.getString("label.checking.comments"));

			final Composite progBarComp = new Composite( progressComp, SWT.NULL );
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			//gridData.widthHint = 300;
			progBarComp.setLayoutData(gridData);

			//Label padLabel = new Label(progressComp,SWT.NULL);
			//gridData = new GridData(GridData.FILL_HORIZONTAL);
			//Utils.setLayoutData(padLabel, gridData);

			final StackLayout	progStackLayout = new StackLayout();

			progBarComp.setLayout( progStackLayout);

			final ProgressBar progBarIndeterminate = new ProgressBar(progBarComp, SWT.HORIZONTAL | SWT.INDETERMINATE);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			progBarIndeterminate.setLayoutData(gridData);

			final ProgressBar progBarComplete = new ProgressBar(progBarComp, SWT.HORIZONTAL );
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			progBarComplete.setLayoutData(gridData);
			progBarComplete.setMaximum( 1 );
			progBarComplete.setSelection( 1 );

			progStackLayout.topControl = progBarIndeterminate;

			new AEThread2( "ProgChecker" )
			{
				@Override
				public void
				run()
				{
					boolean	currently_updating = true;

					while( true ){

						if ( comments_shell.isDisposed()){

							return;
						}

						boolean in_progress = false;

						synchronized( az_rating_in_progress ){

							if ( az_rating_in_progress[0] ){

								in_progress = true;
							}

							for ( ChatInstance inst: activated_chats ){

								if ( inst.getIncomingSyncState() != 0 ){

									in_progress = true;
								}
							}
						}

						final boolean	updating = in_progress;

						if ( updating != currently_updating ){

							currently_updating = updating;

							Utils.execSWTThread(
								new Runnable()
								{
									@Override
									public void
									run()
									{
										if ( !comments_shell.isDisposed()){

											progStackLayout.topControl = updating?progBarIndeterminate:progBarComplete;

											progBarComp.layout();
										}
									}
								});
						}

						try{
							Thread.sleep(500);

						}catch( Throwable e ){

						}
					}
				}
			}.start();

			Button subscriptionLookup = new Button(progressComp,SWT.PUSH);
			subscriptionLookup.setText( MessageText.getString( "ConfigView.section.Subscriptions" ));
			subscriptionLookup.addSelectionListener(
				new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						new SubscriptionListWindow( comments_shell, title, hash.getBytes(), f_enabled_networks, false);
					}
				});

				// line

			Label labelSeparator = new Label( comp, SWT.SEPARATOR | SWT.HORIZONTAL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			labelSeparator.setLayoutData(gridData);

				// buttons

			Composite buttonComp = new Composite( comp, SWT.NULL );
			gridData = new GridData( GridData.FILL_HORIZONTAL );
			buttonComp.setLayoutData(gridData);

			layout = new GridLayout();
			layout.numColumns = 2;
			buttonComp.setLayout(layout);

			new Label(buttonComp,SWT.NULL);

			Composite buttonArea = new Composite(buttonComp,SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			buttonArea.setLayoutData(gridData);
			GridLayout layoutButtons = new GridLayout();
			layoutButtons.numColumns = 1;
			buttonArea.setLayout(layoutButtons);

			List<Button>	buttons = new ArrayList<>();

			Button bOK = new Button(buttonArea,SWT.PUSH);
			buttons.add( bOK );

			bOK.setText(MessageText.getString("Button.ok"));
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.widthHint = 70;
			bOK.setLayoutData(gridData);
			bOK.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event e) {
					comments_shell.dispose();
				}
			});

			Utils.makeButtonsEqualWidth( buttons );

			comments_shell.setDefaultButton( bOK );

			comments_shell.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					try{
						if ( !btnCheckComments.isDisposed()){

							btnCheckComments.setEnabled( true );
						}

						if ( chat_view != null ){

							chat_view.destroy();
						}
					}finally{


					}
				}
			});

			btnCheckComments.setEnabled( false );

			comments_shell.setSize( 600, 600 );
			comments_shell.layout(true, true);

			Utils.centerWindowRelativeTo(comments_shell,shell);

			Messages.setLanguageText( comments_shell, "torrent.comments.title", new String[]{ title });

			comments_shell.open();
		}




		private void checkSeedingMode() {
			
			for ( TorrentOpenOptions to: torrentOptionsMulti ){

				// Check for seeding
				boolean bTorrentValid = true;
	
				if (to.getStartMode() == TorrentOpenOptions.STARTMODE_SEEDING) {
					// check if all selected files exist
					TorrentOpenFileOptions[] files = to.getFiles();
					for (int j = 0; j < files.length; j++) {
						TorrentOpenFileOptions fileInfo = files[j];
						if (!fileInfo.isToDownload())
							continue;
	
						File file = fileInfo.getInitialLink();
	
						if (file == null) {
	
							file = fileInfo.getDestFileFullName();
						}
	
						if (!file.exists()) {
							fileInfo.isValid = false;
							bTorrentValid = false;
						} else if (!fileInfo.isValid) {
							fileInfo.isValid = true;
						}
					}
					
					if ( !bTorrentValid && tag_save_location != null ){
					
						if ( tag_save_location.supportsTagMoveOnComplete()){
							
							File move_loc = tag_save_location.getTagMoveOnCompleteFolder();
							
							if ( move_loc != null ){
								
								if (( tag_save_location.getTagMoveOnCompleteOptions() & TagFeatureFileLocation.FL_DATA ) != 0 ){
									
									String current = getSavePath();
									
									String move_path = move_loc.getAbsolutePath();
									
									if ( !move_path.equals( current )){
									
										to.isValid = false;
										
											// this will recursively run the check on the new location
										
										setSavePath( move_path );
									
										continue;
									}
								}
							}
						}
					}
				}
	
				to.isValid = bTorrentValid;
			}
		}

		protected void cmbDataDirChanged() {

			if (bSkipDataDirModify || cmbDataDir == null) {
				return;
			}
			String dirText = cmbDataDir.getText();

			File moc = null;
			
			for ( TorrentOpenOptions too: torrentOptionsMulti ){
				too.setParentDir( dirText);
				
				moc = too.getMoveOnComplete();
			}

			checkSeedingMode();

			if (!Constants.isOSX) { // See Eclipse Bug 292449
				File file = FileUtil.newFile( dirText );
				if (!file.isDirectory()) {
					cmbDataDir.setBackground(Colors.colorErrorBG);
					// make the error state visible
					soExpandItemSaveTo.setExpanded(true);
				} else {
					cmbDataDir.setBackground(null);
				}
				cmbDataDir.redraw();
				cmbDataDir.update();
			}

			if (soExpandItemSaveTo != null) {
				String s = MessageText.getString("OpenTorrentOptions.header.saveto",
						new String[] { dirText });
				
				if ( moc != null ){
					s += "; " + MessageText.getString( "label.move.on.comp" );
				}
				soExpandItemSaveTo.setText(s);
			}
			diskFreeInfoRefreshPending = true;
		}

		protected void setSelectedQueueLocation(int iLocation) {
			for ( TorrentOpenOptions to: torrentOptionsMulti ){
				to.setQueueLocation( iLocation );
			}

			updateStartOptionsHeader();
		}
		
		protected void setSequentalDownload(boolean seq) {
			for ( TorrentOpenOptions to: torrentOptionsMulti ){
				to.setSequentialDownload( seq );
			}

			updateStartOptionsHeader();
		}

		private void updateStartOptionsHeader() {
			if (soStartOptionsExpandItem == null) {
				return;
			}
			
			if ( isSingleOptions ){
								
				String optionText = MessageText.getString(TorrentOpenOptions.STARTMODE_KEYS[torrentOptions.getStartMode()])
						+ ", "
						+ MessageText.getString(MSGKEY_QUEUELOCATIONS[torrentOptions.getQueueLocation()]);
	
				String s = MessageText.getString("OpenTorrentOptions.header.startoptions",
						new String[] {
							optionText
						});
	
				List<Tag> initialtags = torrentOptions.getInitialTags();
	
				String tag_str = null;
				int numTags = 0;
	
				if ( initialtags.size() > 0 ){
	
					tag_str = "";
	
					for ( Tag t: initialtags ){
						if ((t instanceof DiscoveredTag)
								&& ((DiscoveredTag) t).existingTag != null) {
							continue;
						}
						numTags++;
						tag_str += (tag_str==""?"":", ") + t.getTagName( true );
					}
				}
	
				if (numTags == 0) {
					tag_str = MessageText.getString( "label.none" );
				}
	
	
				s += "        " + MessageText.getString( "OpenTorrentOptions.header.tags", new String[]{ tag_str });
	
				if ( torrentOptions.getSequentialDownload()) {
				
					s += "        " + MessageText.getString( "menu.sequential.download" );
				}
			
				soStartOptionsExpandItem.setText(s);
				
			}else{
				
				int		startMode 		= -1;
				int		queueLocation	= -1;
				int		sequential		= -1;
				
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					
					int sm = to.getStartMode();
					int ql = to.getQueueLocation();
					int s = to.getSequentialDownload()?1:0;
					
					if ( startMode == -1 ){
					
						startMode 		= sm;
						queueLocation 	= ql;
						sequential		= s;
						
					}else{
						
						if ( startMode != sm ){
							startMode	= -2;
						}
						if ( queueLocation != ql ){
							queueLocation = -2;
						}
						if (sequential != s ){
							sequential = -2;
						}
					}
				}
				
				String smText =	(startMode<0?"":MessageText.getString(TorrentOpenOptions.STARTMODE_KEYS[startMode]));
				String qlText = (queueLocation<0?"":MessageText.getString(MSGKEY_QUEUELOCATIONS[queueLocation]));
				
				String optionText = smText;
				
				if ( !qlText.isEmpty()){
				
					if ( !optionText.isEmpty()){
					
						optionText += ", ";
					}
					
					optionText += qlText;
				}
	
				String s = MessageText.getString("OpenTorrentOptions.header.startoptions",
						new String[] {
							optionText
						});
	
				if ( sequential == 1 ){
					
					s += "        " + MessageText.getString( "menu.sequential.download" );
				}
			
				soStartOptionsExpandItem.setText(s);			
			}
		}

		protected void setSelectedStartMode(int iStartID) {
			
			for ( TorrentOpenOptions to: torrentOptionsMulti ){
				to.setStartMode( iStartID );
			}
			
			checkSeedingMode();
			updateStartOptionsHeader();
		}

		private void setupFileAreaButtons(SWTSkinObjectContainer so) {

			PluginInterface	swarm_pi = null;

			try{
				if (COConfigurationManager.getBooleanParameter("rcm.overall.enabled",
						true) && CoreFactory.isCoreRunning()) {
					final PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(
							"aercm");

					if (pi != null && pi.getPluginState().isOperational()
							&& pi.getIPC().canInvoke("lookupBySize", new Object[] {
								new Long(0)
							})){

						swarm_pi = pi;
					}
				}
			}catch( Throwable e ){

			}

			Composite cButtonsArea = so.getComposite();
			GridLayout layout = new GridLayout(1,false);
			layout.marginWidth = layout.marginHeight = layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cButtonsArea.setLayout(layout);

			Composite cButtonsTop = new Composite(cButtonsArea, SWT.NULL);
			layout = new GridLayout(swarm_pi==null?6:7,false);
			layout.marginWidth = layout.marginHeight = layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cButtonsTop.setLayout(layout);
			GridData gridData = new GridData( GridData.FILL_HORIZONTAL);
			cButtonsTop.setLayoutData(gridData);


			Canvas line = new Canvas(cButtonsArea,SWT.NO_BACKGROUND);
			line.addListener(SWT.Paint, new Listener() {
				@Override
				public void handleEvent(Event e) {
					Rectangle clientArea = ((Canvas) e.widget).getClientArea();
					e.gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_NORMAL_SHADOW));
					e.gc.drawRectangle(clientArea);
					clientArea.y++;
					e.gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
					e.gc.drawRectangle(clientArea);
				}
			});
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.heightHint = 2;
			line.setLayoutData(gridData);

			Composite cButtonsBottom = new Composite(cButtonsArea, SWT.NULL);
			layout = new GridLayout(7,false);
			layout.marginWidth = layout.marginHeight = layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cButtonsBottom.setLayout(layout);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			cButtonsBottom.setLayoutData(gridData);

			List<Button>	buttons = new ArrayList<>();

			btnSelectAll = new Button(cButtonsTop, SWT.PUSH);
			buttons.add( btnSelectAll );
			Messages.setLanguageText(btnSelectAll, "Button.selectAll");
			btnSelectAll.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tvFiles.selectAll();
				}
			});

			btnMarkSelected = new Button(cButtonsTop, SWT.PUSH);
			buttons.add( btnMarkSelected );
			Messages.setLanguageText(btnMarkSelected, "Button.mark");
			btnMarkSelected.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					TorrentOpenFileOptions[] infos = tvFiles.getSelectedDataSources().toArray(new TorrentOpenFileOptions[0]);
					setToDownload( infos, true );
				}
			});

			btnUnmarkSelected = new Button(cButtonsTop, SWT.PUSH);
			buttons.add( btnUnmarkSelected );
			Messages.setLanguageText(btnUnmarkSelected, "Button.unmark");
			btnUnmarkSelected.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					TorrentOpenFileOptions[] infos = tvFiles.getSelectedDataSources().toArray(new TorrentOpenFileOptions[0]);
					setToDownload( infos, false );

				}
			});

			btnRename = new Button(cButtonsTop, SWT.PUSH);
			buttons.add( btnRename );
			Messages.setLanguageText(btnRename, "Button.rename");
			btnRename.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					TorrentOpenFileOptions[] infos = tvFiles.getSelectedDataSources().toArray(
							new TorrentOpenFileOptions[0]);
					if (infos.length > 0 ) {
						renameFilenames(infos);
					}
				}
			});

			btnRetarget = new Button(cButtonsTop, SWT.PUSH);
			buttons.add( btnRetarget );
			Messages.setLanguageText(btnRetarget, "Button.retarget");
			btnRetarget.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					TorrentOpenFileOptions[] infos = tvFiles.getSelectedDataSources().toArray(
							new TorrentOpenFileOptions[0]);
					changeFileDestination(infos, false );
				}
			});

			Label pad1 = new Label(cButtonsTop, SWT.NONE);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			pad1.setLayoutData(gridData);

			// swarm-it button

			if ( swarm_pi != null ){
				final PluginInterface f_pi = swarm_pi;

				btnSwarmIt = new Button(cButtonsTop, SWT.PUSH);
				buttons.add( btnSwarmIt );
				Messages.setLanguageText(btnSwarmIt, "Button.swarmit");

				btnSwarmIt.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						List<Object> selectedDataSources = tvFiles.getSelectedDataSources();
						for (Object ds : selectedDataSources) {
							TorrentOpenFileOptions file = (TorrentOpenFileOptions) ds;

							try{
								try{
									f_pi.getIPC().invoke(
										"lookupBySize", 
											new Object[]{
												new Long(file.lSize),
												new String[]{ AENetworkClassifier.AT_PUBLIC },
												file.getDestFileName() });
										
								}catch( Throwable e ){
								
									f_pi.getIPC().invoke(
										"lookupBySize", new Object[]{new Long(file.lSize)});
								}
							}catch (Throwable e) {

								Debug.out(e);
							}
							break;
						}
					}
				});

				btnSwarmIt.setEnabled(false);
			}

			btnTreeView = new Button(cButtonsBottom, SWT.PUSH);
			buttons.add( btnTreeView );
			Messages.setLanguageText(btnTreeView, "OpenTorrentWindow.tree.view");
			Utils.setTT(btnTreeView, MessageText.getString( "OpenTorrentWindow.tree.view.info" ));

			btnTreeView.addListener(SWT.Selection, new Listener(){
				@Override
				public void handleEvent(Event event) {
					showTreeView();
				}
			});

			Label pad2 = new Label(cButtonsBottom, SWT.NONE);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			pad2.setLayoutData(gridData);

				// local user comment
			
			Button local_comment = new Button( cButtonsBottom, SWT.PUSH );
			local_comment.setToolTipText( MessageText.getString( "TableColumn.header.comment.info" ));
			
			local_comment.setImage( ImageLoader.getInstance().getImage("no_comment"));
			
			local_comment.addListener(
				SWT.Selection,
				(e)->{
					TorrentUtil.promptUserForComment(
						torrentOptions.getUserComment(),
						(str)->{
							torrentOptions.setUserComment( str );
							
							local_comment.setImage( 
								ImageLoader.getInstance().getImage(str==null||str.isEmpty()?"no_comment":"comment"));
						});
				} );
				
			Label sep = new Label( cButtonsBottom, SWT.SEPARATOR );
			gridData = new GridData();
			gridData.heightHint = 15;
			sep.setLayoutData(gridData);
			
				// privacy add mode

			btnPrivacy = new Button(cButtonsBottom, SWT.TOGGLE);
			buttons.add( btnPrivacy );
			Messages.setLanguageText(btnPrivacy, "label.privacy");
			Utils.setTT(btnPrivacy, MessageText.getString( "OpenTorrentWindow.privacy.info" ));

			btnPrivacy.addListener(SWT.Selection, new Listener(){
				private int					saved_start_mode;
				private Map<String,Boolean>	saved_nets;

				@Override
				public void handleEvent(Event event) {
					if ( btnPrivacy.getSelection()){

						saved_nets 			= torrentOptions.getEnabledNetworks();
						saved_start_mode 	= torrentOptions.getStartMode();

						setSelectedStartMode( TorrentOpenOptions.STARTMODE_STOPPED );

						for ( String net: AENetworkClassifier.AT_NETWORKS ){

							torrentOptions.setNetworkEnabled( net, false );
						}

						updateNetworkOptions();

					}else{

						if ( saved_nets != null ){

							setSelectedStartMode( saved_start_mode );

							for ( Map.Entry<String,Boolean> entry: saved_nets.entrySet()){

								torrentOptions.setNetworkEnabled( entry.getKey(), entry.getValue());
							}
							saved_nets = null;
						}

						updateNetworkOptions();
					}
				}
			});
				// ratings etc

			btnCheckComments = new Button(cButtonsBottom, SWT.PUSH);
			buttons.add( btnCheckComments );
			Messages.setLanguageText(btnCheckComments, "label.comments");

			btnCheckComments.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					showComments();
				}
			});
				// availability button

			btnCheckAvailability = new Button(cButtonsBottom, SWT.PUSH);
			buttons.add( btnCheckAvailability );
			Messages.setLanguageText(btnCheckAvailability, "label.check.avail");

			btnCheckAvailability.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					showAvailability();
				}
			});


			Utils.makeButtonsEqualWidth( buttons );

			updateFileButtons();

		}

		private void
		setToDownload(
			TorrentOpenFileOptions[]	infos,
			boolean						download )
		{
			boolean changed = false;
			try{
				settingToDownload.incrementAndGet();

				for (TorrentOpenFileOptions info: infos ){

					if ( info.isToDownload() != download ){

						info.setToDownload( download );

						changed = true;
					}
				}
			}finally{

				settingToDownload.decrementAndGet();
			}

			if ( changed ){
				updateFileButtons();
				updateSize();
				torrentOptions.applyAutoTagging();
			}
		}

		private void setupFileAreaInfo(SWTSkinObjectText so) {
			soFileAreaInfo = so;
		}

		private void 
		setupSaveLocation(
			SWTSkinObjectContainer	soInputArea,
			SWTSkinObjectButton		soBrowseButton, 
			SWTSkinObjectButton		soSearchButton, 
			SWTSkinObjectContainer	soMoreArea) 
		{	
			cmbDataDir = new Combo(soInputArea.getComposite(), SWT.NONE);

			cmbDataDir.setLayoutData(Utils.getFilledFormData());

			cmbDataDir.addKeyListener(
				new KeyListener(){
					@Override
					public void keyReleased(KeyEvent e) {
						if ( e.keyCode == SWT.SPACE && ( e.stateMask & SWT.MOD1) != 0 ){

							e.doit = false;
						}
					}

					@Override
					public void keyPressed(KeyEvent e) {

						if ( e.keyCode == SWT.SPACE && ( e.stateMask & SWT.MOD1 ) != 0 ){

							e.doit = false;

							Menu menu = cmbDataDir.getMenu();

							if ( menu != null && !menu.isDisposed()){

								menu.dispose();
							}

							menu = new Menu( cmbDataDir );

							String current_text = cmbDataDir.getText();

							String def = COConfigurationManager.getStringParameter(PARAM_DEFSAVEPATH);

							List<String> items = new ArrayList<>( Arrays.asList( cmbDataDir.getItems()));

							if ( !items.contains( def )){

								items.add( def );
							}

							List<String>	suggestions = new ArrayList<>();

							for ( String item: items ){

								if ( item.toLowerCase(Locale.US).contains( current_text.toLowerCase( Locale.US ))){

									suggestions.add( item );
								}
							}

							if ( suggestions.size() == 0 ){

								MenuItem mi = new MenuItem( menu, SWT.PUSH );

								mi.setText( MessageText.getString( "label.no.suggestions" ));

								mi.setEnabled( false );

							}else{

								//Collections.sort( suggestions );

								for ( final String str: suggestions ){

									MenuItem mi = new MenuItem( menu, SWT.PUSH );

									String mi_str = str.replaceAll( "&", "&&" );
									
									mi.setText( mi_str );

									mi.addSelectionListener(
										new SelectionAdapter() {

											@Override
											public void
											widgetSelected(SelectionEvent e) {

												cmbDataDir.setText( str );
											}
										});
								}
							}

							cmbDataDir.setMenu( menu );

							final Point cursorLocation = Display.getCurrent().getCursorLocation();

							menu.setLocation( cursorLocation.x-10, cursorLocation.y-10 );

							menu.setVisible( true );

							Utils.execSWTThread(
								new Runnable() {

									@Override
									public void run() {
										// need to do this to get the menu item selected correctly
										Display.getCurrent().setCursorLocation(cursorLocation.x+1,cursorLocation.y);
									}
								}, true );
						}
					}
				});

			Utils.setTT(cmbDataDir, MessageText.getString( "label.ctrl.space.for.suggestion" ));

			cmbDataDir.addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					cmbDataDirChanged();
				}
			});
			cmbDataDir.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					cmbDataDirChanged();
				}
			});

			updateDataDirCombo();
			
			List<String> dirList = COConfigurationManager.getStringListParameter("saveTo_list");
			
			for (String s : dirList) {
				
				if (torrentOptions == null || !s.equals(torrentOptions.getParentDir())) {
					cmbDataDir.add(s);
				}
			}

			soBrowseButton.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
						SWTSkinObject skinObject, int stateMask) {
					String sSavePath;
					String sDefPath = cmbDataDir.getText();

					File f = FileUtil.newFile(sDefPath);
					if (sDefPath.length() > 0) {
						while (!f.exists()) {
							f = f.getParentFile();
							if (f == null) {
								f = FileUtil.newFile(sDefPath);
								break;
							}
						}
					}

					DirectoryDialog dDialog = new DirectoryDialog(cmbDataDir.getShell(),
							SWT.SYSTEM_MODAL);
					dDialog.setFilterPath(f.getAbsolutePath());
					dDialog.setMessage(MessageText.getString("MainWindow.dialog.choose.savepath_forallfiles"));
					sSavePath = dDialog.open();

					if (sSavePath != null) {
						cmbDataDir.setText(sSavePath);
					}
				}
			});
			
			soSearchButton.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
						SWTSkinObject skinObject, int stateMask) {

					ManagerUtils.locateSaveLocations( 
						torrentOptionsMulti,
						shell,
						(files)->{
							int pos = 0;
							
							for ( TorrentOpenOptions too: torrentOptionsMulti ){
							
								if ( files[pos] != null ){
									
									File dir = files[pos];
									
									if ( too.isSimpleTorrent()){
										
										too.setParentDir( dir.getAbsolutePath());
										
									}else{
																	
										String parent_dir = dir.getParentFile().getAbsolutePath();
										
										too.setParentDir( parent_dir );
										
										String sub_dir = dir.getName();
										
										if ( sub_dir.equals( too.getDefaultSubDir())){
										
											too.setSubDir( null );
											
										}else{
											
											too.setSubDir( sub_dir );
										}
									}
										// we want to use this location whatever
									
									too.setDisableAutoRename( true );
								}
								
								pos++;
								
							}
							
							updateDataDirCombo();
						});
				}
			});
			
			btnDataDir	= soBrowseButton.getButton();
			btnSearch	= soSearchButton.getButton();
			
			if ( !isSingleOptions ){
			
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					
					OpenTorrentInstance instance = getInstance( to );
					
					if ( instance.tag_save_location != null ){
					
						cmbDataDirEnabled = false;
						
						break;
					}
				}
			}
			
			cmbDataDir.setEnabled( cmbDataDirEnabled );
			btnDataDir.setEnabled( cmbDataDirEnabled );
			btnSearch.setEnabled( cmbDataDirEnabled );
			
			Composite more_outer = soMoreArea.getComposite();
			
			Composite more_comp = new Composite( more_outer, SWT.NULL );
			more_comp.setLayoutData( Utils.getFilledFormData());
			
			GridLayout more_layout= new GridLayout(4,false);
			
			Label sub_label = new Label( more_comp, SWT.NULL );
			
			sub_label.setText( MessageText.getString( "label.subfolder" ));
						
			Text sub_text = new Text( more_comp, SWT.BORDER );
			GridData grid_data = new GridData(GridData.FILL_BOTH);
			grid_data.verticalAlignment = SWT.CENTER;
			
			sub_text.setLayoutData(grid_data);
		
			if ( isSingleOptions && !torrentOptions.isSimpleTorrent()){

				txtSubFolder = sub_text;
				
				String top = FileUtil.newFile(torrentOptions.getDataDir()).getName();

				txtSubFolder.setText( top );
				
				torrentOptions.addListener((TorrentOpenOptions.ParentDirChangedListener) () -> txtSubFolder.setText(FileUtil.newFile(torrentOptions.getDataDir()).getName()) );
						
				txtSubFolder.addFocusListener(
					new FocusListener(){
						
						@Override
						public void focusLost(FocusEvent e){
							
							// remember we know  we're not a simple torrent here as not shown if so
							
							String str = txtSubFolder.getText().trim();
							
							File data_dir = FileUtil.newFile( torrentOptions.getDataDir());
							
							if ( str.isEmpty()){
								
								String top = data_dir.getName();

								txtSubFolder.setText( top );
								
							}else{
								
								File new_dir = FileUtil.newFile( data_dir.getParentFile(), str );
								
								setTopLevelFolder( new_dir, false );
							}
						}
						
						@Override
						public void focusGained(FocusEvent e){
						}
					});
				
			}else{
				
				sub_label.setEnabled( false );
				sub_text.setEnabled( false );
			}
			
			more_layout.marginTop = more_layout.marginBottom = 0;
			more_layout.marginHeight = 0;
			
			more_comp.setLayout( more_layout );
			
			Label more_label = new Label( more_comp, SWT.NULL );
			grid_data = new GridData(GridData.FILL_VERTICAL );
			grid_data.verticalAlignment = SWT.CENTER;
			more_label.setLayoutData(grid_data);

			more_label.setText( MessageText.getString( "label.more" ));
			
			Label more_icon = new Label( more_comp, SWT.NULL );
			
			Image image = ImageLoader.getInstance().getImage( "menu_down" );
			more_icon.setImage( image );
			 grid_data = new GridData();
			grid_data.widthHint=image.getBounds().width;
			grid_data.heightHint=image.getBounds().height;
			grid_data.verticalAlignment = SWT.CENTER;
			more_icon.setLayoutData(grid_data);

			final Menu more_menu = new Menu( more_comp );

			for ( Control l: new Control[]{ more_label, more_icon }){
				
				l.setCursor(more_comp.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
	
				l.setMenu( more_menu );
	
				l.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseDown(MouseEvent event) {
						try{
							Point p = more_menu.getDisplay().map( l, null, event.x, event.y );
	
							more_menu.setLocation( p );
	
							more_menu.setVisible(true);
	
						}catch( Throwable e ){
	
							Debug.out( e);
						}
					}
				});
			}
			
			final Menu moc_menu = new Menu( shell, SWT.DROP_DOWN);

			MenuItem moc_item = new MenuItem( more_menu, SWT.CASCADE);

			Messages.setLanguageText( moc_item, "label.move.on.comp" );

			moc_item.setMenu( moc_menu );

			MenuItem clear_item = new MenuItem( moc_menu, SWT.PUSH);

			Messages.setLanguageText( clear_item, "Button.clear" );
			
			clear_item.addListener(SWT.Selection, new Listener(){
				@Override
				public void handleEvent(Event arg0){
					for ( TorrentOpenOptions to: torrentOptionsMulti ){
						
						to.setMoveOnComplete( null );
					}
					
					cmbDataDirChanged();
				}});
	
			moc_menu.addMenuListener(
				new MenuListener(){
					
					@Override
					public void menuShown(MenuEvent arg0){
						boolean has_moc = false;
						
						for ( TorrentOpenOptions to: torrentOptionsMulti ){
							
							has_moc |= to.getMoveOnComplete() != null;
						}
						clear_item.setEnabled( has_moc );

					}
					
					@Override
					public void menuHidden(MenuEvent arg0){
						// TODO Auto-generated method stub
						
					}
				});
			
			MenuItem set_item = new MenuItem( moc_menu, SWT.PUSH);

			Messages.setLanguageText( set_item, "label.set" );
			
			set_item.addListener(SWT.Selection, new Listener(){
				@Override
				public void handleEvent(Event arg0){
					DirectoryDialog dd = new DirectoryDialog(shell);

					String filter_path = TorrentOpener.getFilterPathData();

					dd.setFilterPath(filter_path);

					dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

					String path = dd.open();

					if ( path != null ){

						TorrentOpener.setFilterPathData(path);

						File target = FileUtil.newFile(path);
						
						for ( TorrentOpenOptions to: torrentOptionsMulti ){
							
							to.setMoveOnComplete( target );
						}
						
						cmbDataDirChanged();
					}
				}});
			
			MenuItem rename_dn_item = new MenuItem( more_menu, SWT.PUSH);

			rename_dn_item.setText( MessageText.getString( "MyTorrentsView.menu.rename.displayed" ) + "..." );
			
			rename_dn_item.addListener( SWT.Selection, (ev)->{
				String msg_key_prefix = "MyTorrentsView.menu.rename.displayed.enter.";

				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						msg_key_prefix + "title", msg_key_prefix + "message");
				entryWindow.setPreenteredText( torrentOptions.getDisplayName(), false);
				entryWindow.maintainWhitespace( true );	// apparently users want to be able to prefix with spaces
				entryWindow.prompt(new UIInputReceiverListener() {
					@Override
					public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
						if (!entryWindow.hasSubmittedInput()) {
							return;
						}
						String value = entryWindow.getSubmittedInput();
						if (value != null && value.length() > 0) {
							torrentOptions.setDisplayName(value);
							updateDialogTitle();
						}
					}
				});
			});
			
			rename_dn_item.setEnabled( isSingleOptions );
			
			new MenuItem( more_menu, SWT.SEPARATOR );
			 
			MenuItem opt = new MenuItem( more_menu, SWT.CHECK );
			
			opt.setSelection( COConfigurationManager.getBooleanParameter( "open.torrent.window.rename.on.tlf.change" ));

			Messages.setLanguageText(opt, "OpenTorrentWindow.tlf.rename");

			opt.addSelectionListener(
					new SelectionAdapter()
					{
						@Override
						public void
						widgetSelected(
								SelectionEvent e )
						{
							COConfigurationManager.setParameter(
									"open.torrent.window.rename.on.tlf.change",
									((MenuItem)e.widget).getSelection());
						}
					});
			
			
			MenuItem save_torrent = new MenuItem( more_menu, SWT.PUSH );

			Messages.setLanguageText(save_torrent, "menu.save.torrent" );
			
			save_torrent.addListener(SWT.Selection, (ev)->{
				
				String[]	names		= new String[torrentOptionsMulti.size()];
				TOTorrent[]	torrents	= new TOTorrent[names.length];

				int pos = 0;
				
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					
					names[pos]		= to.getTorrentName() + ".torrent";
					torrents[pos]	= to.getTorrent();
					
					pos++;
				}
				
				TorrentUtil.exportTorrents( names, torrents, more_menu.getShell());
			});

		}

		private void setupStartOptions(SWTSkinObjectExpandItem so) {
			soStartOptionsExpandItem = so;
			Composite cTorrentOptions = so.getComposite();

			Composite cTorrentModes = new Composite(cTorrentOptions, SWT.NONE);
			GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
			cTorrentModes.setLayoutData(Utils.getFilledFormData());
			GridLayout layout = new GridLayout();
			layout.numColumns = 5;
			layout.marginWidth = 5;
			layout.marginHeight = 5;
			cTorrentModes.setLayout(layout);

			Label label = new Label(cTorrentModes, SWT.NONE);
			gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER);
			label.setLayoutData(gridData);
			Messages.setLanguageText(label, "OpenTorrentWindow.startMode");

			cmbStartMode = new Combo(cTorrentModes, SWT.BORDER | SWT.READ_ONLY);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			cmbStartMode.setLayoutData(gridData);
			updateStartModeCombo();
			cmbStartMode.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					setSelectedStartMode(cmbStartMode.getSelectionIndex());
				}
			});

			label = new Label(cTorrentModes, SWT.NONE);
			gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER);
			label.setLayoutData(gridData);
			Messages.setLanguageText(label, "OpenTorrentWindow.addPosition");

			cmbQueueLocation = new Combo(cTorrentModes, SWT.BORDER | SWT.READ_ONLY);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			cmbQueueLocation.setLayoutData(gridData);
			updateQueueLocationCombo();
			cmbQueueLocation.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					setSelectedQueueLocation(cmbQueueLocation.getSelectionIndex());
				}
			});

			btnSequentialDownload = new Button( cTorrentModes, SWT.CHECK );
			Messages.setLanguageText(btnSequentialDownload, "menu.sequential.download");
			gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER);
			btnSequentialDownload.setLayoutData(gridData);

			if ( Constants.isWindows ){
				btnSequentialDownload.setBackground( Colors.white );
			}
			updateSequentialDownloadButton();
			btnSequentialDownload.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					btnSequentialDownload.setGrayed( false );
					setSequentalDownload( btnSequentialDownload.getSelection());
				}
			});

			
			if ( TagManagerFactory.getTagManager().isEnabled()){
				

					// tag area

				Composite tagLeft 	= new Composite( cTorrentModes, SWT.NULL);
				tagLeft.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_CENTER ));
				Composite tagRight 	= new Composite( cTorrentModes, SWT.NULL);
				gridData = new GridData(GridData.FILL_HORIZONTAL );
				gridData.horizontalSpan = 4;
				tagRight.setLayoutData(gridData);

				layout = new GridLayout();
				layout.numColumns = 1;
				layout.marginWidth  = 0;
				layout.marginHeight = 0;
				tagLeft.setLayout(layout);

				layout = new GridLayout();
				layout.numColumns = 1;
				layout.marginWidth  = 0;
				layout.marginHeight = 0;
				tagRight.setLayout(layout);

				label = new Label(tagLeft, SWT.NONE);
				gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER);
				Messages.setLanguageText(label, "label.initial_tags");

				Composite sc = Utils.createScrolledComposite( tagRight, so.getExpandItem().getParent().getParent());

				if ( Constants.isWindows ){
					sc.setBackground( Colors.white );
					sc.setBackgroundMode( SWT.INHERIT_DEFAULT );
				}
				
				layout = new GridLayout();
				layout.numColumns = 1;
				layout.marginWidth  = 0;
				layout.marginHeight = 0;
				sc.setLayout(layout);
				
				tagButtonsArea 	= new Composite( sc, SWT.DOUBLE_BUFFERED);
						
				tagButtonsArea.setLayout(new FillLayout());
				gridData = new GridData(SWT.FILL, SWT.FILL, true, true );
				tagButtonsArea.setLayoutData( gridData);

				buildTagButtonPanel();

				Button addTag = new Button( tagLeft, SWT.NULL );
				addTag.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_CENTER ));
				addTag.setText("+");

				addTag.addListener(SWT.Selection,
						event -> TagUIUtilsV3.showCreateTagDialog(tags -> {

							if ( isSingleOptions ){
								List<Tag> initialTags = torrentOptions.getInitialTags();
	
								boolean changed = false;
	
								for (Tag tag : tags) {
									changed |= addInitialTag(initialTags, tag);
									Tag otherTag = findOtherTag(tag);
									if (otherTag != null) {
										if ((otherTag instanceof DiscoveredTag)
												&& ((DiscoveredTag) otherTag).existingTag == null) {
											((DiscoveredTag) otherTag).existingTag = tag;
										}
										changed |= addInitialTag(initialTags, otherTag);
									}
								}
	
								if (changed) {
	
									torrentOptions.setInitialTags(initialTags);
	
									updateStartOptionsHeader();
	
									tagButtonsUI.updateFields(null);
								}
							}
						}));
			}
		}

		private boolean tbp_building 		= false;
		private boolean tbp_build_pending 	= false;
		
		private void
		buildTagButtonPanel()
		{
			if (tagButtonsArea == null || tagButtonsArea.isDisposed()) {

				return;
			}

			if (!Utils.isThisThreadSWT()){
				
				Utils.execSWTThread(this::buildTagButtonPanel);
				
				return;
			}

				// we can get recursion during build which causes havoc :(
				// SWT single thread here...
			
			if ( tbp_building ){
				
				tbp_build_pending = true;
				
				return;
			}
			
			for ( int i=0;i<10;i++){
			
				try{
					tbp_building = true;
					
					buildTagButtonPanelSupport();
					
				}finally{
					
					tbp_building = false;
					
					if ( tbp_build_pending ){
						
						tbp_build_pending = false;
						
					}else{
						
						return;
					}
				}
			}
			
			Debug.out( "Tag Button Panel build abandoned, too many retries" );
		}
		
		private void buildTagButtonPanelSupport() {
				
			TagManager tm = TagManagerFactory.getTagManager();

			TagType[] tts = {
					tm.getTagType( TagType.TT_DOWNLOAD_MANUAL),
					tm.getTagType( TagType.TT_DOWNLOAD_CATEGORY) };

			boolean is_rebuild = tagButtonsArea.getData(SP_KEY) != null;

			if (is_rebuild) {

				Utils.disposeComposite(tagButtonsArea, false);

			} else {

				tagButtonsArea.setData(SP_KEY, getSavePath());

				tagButtonsUI = new TagButtonsUI();
				TagTypeListener tagTypeListener = new TagTypeListener() {
					@Override
					public void tagTypeChanged(TagType tag_type) {

					}

					@Override
					public void tagEventOccurred(TagEvent event) {
						int type = event.getEventType();
						Tag tag = event.getTag();
						if (type == TagEvent.ET_TAG_ADDED) {
							tagAdded(tag);
						} else if (type == TagEvent.ET_TAG_REMOVED) {
							tagRemoved(tag);
						}
					}

					public void tagRemoved(Tag tag) {
						if ( isSingleOptions ){
							List<Tag> initialTags = torrentOptions.getInitialTags();
							if (removeInitialTag(initialTags, tag)) {
	
								Tag otherTag = findOtherTag(tag);
								if (otherTag != null) {
									removeInitialTag(initialTags, otherTag);
								}
	
								torrentOptions.setInitialTags(initialTags);
	
								updateStartOptionsHeader();
							}
						}
						
						buildTagButtonPanel();
					}

					public void tagAdded(Tag tag) {
						buildTagButtonPanel();
					}
				};
				
				for ( TagType tt: tts ){
					tt.addTagTypeListener(tagTypeListener, false);
					tagButtonsArea.addDisposeListener(
							e -> tt.removeTagTypeListener(tagTypeListener));
				}
			}

			List<Tag> tags = new ArrayList<>();
			
			for ( TagType tt: tts ){
				tags.addAll(tt.getTags());
			}
			
			for (Iterator<Tag> iter = tags.iterator(); iter.hasNext();) {
				Tag next = iter.next();
				boolean[] auto = next.isTagAuto();
				boolean auto_add = auto[0];
				//boolean auto_rem	= auto[1];
				boolean auto_new = auto[2];
				
				if ( auto_add || auto_new ) {
					iter.remove();
					
					continue;
				}
				
				if ( !isSingleOptions ){
					
					/*
					if ( next instanceof TagFeatureFileLocation ){
					
						TagFeatureFileLocation fl = (TagFeatureFileLocation)next;
						
						if (( fl.getTagInitialSaveOptions() & TagFeatureFileLocation.FL_DATA ) != 0 ){
							
							File dir = fl.getTagInitialSaveFolder();
							
							if ( dir != null ){
							
								iter.remove();
								
								continue;
							}
						}
					}
					*/
					
					for ( TorrentOpenOptions to: torrentOptionsMulti ){
						
						if ( !to.canDeselectTag( next )){
							
							iter.remove();
							
							break;
						}
					}
				}
			}

			List<Tag> initialTagsCache;
			
			if ( isSingleOptions ){
				
				for (DiscoveredTag discoveredTag : listDiscoveredTags.values()) {
					tags.add(discoveredTag);
				}
					
				initialTagsCache = torrentOptions.getInitialTags();
				
			}else{
				
				initialTagsCache = new ArrayList<>();
				
				boolean first = true;
				
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					List<Tag> initTags = to.getInitialTags();
					if ( first ){
						initialTagsCache.addAll( initTags);
						first = false;
					}else{
						for ( Iterator<Tag> it = initialTagsCache.iterator();it.hasNext();){
							if ( !initTags.contains( it.next())){
								it.remove();
							}
						}
					}
				}
			}
			
			boolean[] building = { true };

			tagButtonsUI.buildTagGroup(tags, tagButtonsArea, false,
					new TagButtonTrigger() {
						@Override
						public void tagButtonTriggered(TagPainter painter, int stateMask,
								boolean longPress) {
							if (longPress) {
								return;
							}
							
							Tag tag = painter.getTag();
							
							boolean tagSelected = !painter.isSelected();

							if ( isSingleOptions ){
								
								List<Tag> tags = torrentOptions.getInitialTags();
																
								if ( !tagSelected ){
									
									if ( !torrentOptions.canDeselectTag( tag )){
										
										painter.setSelected( true );
										
										return;
									}
								}
								
								boolean initialTagsChanged = tagSelected ? addInitialTag(tags, tag)	: removeInitialTag(tags, tag);
								
								if ( initialTagsChanged && tagSelected ){
									
									TagGroup tg = tag.getGroupContainer();
									
									if ( tg != null ){
										
										if ( tg.isExclusive() || tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_CATEGORY ){
											
											for ( Tag t: tags.toArray( new Tag[0] )){
												
												if ( t.getGroupContainer() == tg ){
													
													if ( t != tag ){
														
														removeInitialTag( tags, t );
													}
												}
											}
										}
									}
								}
								if (initialTagsChanged) {
	
									Tag otherTag = findOtherTag(tag);
									if (otherTag != null) {
										if (tagSelected) {
											addInitialTag(tags, otherTag);
										} else {
											removeInitialTag(tags, otherTag);
										}
									}
	
									torrentOptions.setInitialTags(tags);
								}
							}else{
								
								boolean disable_sp = false;
								
								for ( TorrentOpenOptions to: torrentOptionsMulti ){
									
									List<Tag> tags = to.getInitialTags();
									
									OpenTorrentInstance instance = getInstance( to );
																		
									if (tagSelected) {
										instance.addInitialTag(tags, tag);
									} else {
										instance.removeInitialTag(tags, tag);
									}
									
									if ( tagSelected ){
										TagGroup tg = tag.getGroupContainer();
										
										if ( tg != null ){
											
											if ( tg.isExclusive() || tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_CATEGORY ){
												
												for ( Tag t: tags.toArray( new Tag[0] )){
													
													if ( t.getGroupContainer() == tg ){
														
														if ( t != tag ){
															
															instance.removeInitialTag( tags, t );
														}
													}
												}
											}
										}
									}
									
									to.setInitialTags(tags);
									
									if ( instance.tag_save_location != null ){
										
										disable_sp = true;
									}
								}
								
								setSavePathEnabled( !disable_sp );
							}
							tagButtonsUI.updateFields(null);
							updateStartOptionsHeader();
						}

						@Override
						public Boolean tagSelectedOverride(Tag tag) {
							if ( isSingleOptions ){
								List<Tag> initialTags = building[0]?initialTagsCache:torrentOptions.getInitialTags();
								if (initialTags.contains(tag)) {
									return true;
								}
							}else{
								List<Tag> initialTags;
								
								if ( building[0] ){
									initialTags = initialTagsCache;
								}else{
									initialTags = new ArrayList<>();
									
									boolean first = true;
									
									for ( TorrentOpenOptions to: torrentOptionsMulti ){
										List<Tag> initTags = to.getInitialTags();
										if ( first ){
											initialTags.addAll( initTags);
											first = false;
										}else{
											for ( Iterator<Tag> it = initialTags.iterator();it.hasNext();){
												if ( !initTags.contains( it.next())){
													it.remove();
												}
											}
										}
									}
								}
								if (initialTags.contains(tag)) {
									return true;
								}
							}
							return null;
						}
					});
			tagButtonsUI.setEnableWhenNoTaggables(true);
			
				// this causes each tag to hit the 'tagSelectedOverride' above so rather than letting it call 'getInitialTags'
				// ( which is costly when auto-tagging is enable) cache the initial tags during build
			
			tagButtonsUI.updateFields(null);
			
			building[0] = false;
			
			updateInitialSaveTags( tagButtonsUI.getSelectedTags(), null );
			
			tagButtonsArea.getParent().layout( true, true );
			
				// without this the expando sometimes get stuck with the wrong height and therefore a truncated view
			
			soStartOptionsExpandItem.getExpandItem().setHeight( soStartOptionsExpandItem.getComposite().computeSize( SWT.DEFAULT,  SWT.DEFAULT).y );
		}

		private boolean removeInitialTag(List<Tag> tags, Tag tag) {
			if ( tags.remove( tag )){
			
				if ( tag instanceof TagFeatureFileLocation ){
					
					updateInitialSaveTags( tags, (TagFeatureFileLocation)tag );
				}
			
				return( true );
				
			}else{
				return( false );
			}
		}

		private boolean addInitialTag(List<Tag> tags, Tag tag) {
	
			if (!tags.contains(tag)) {
				
				tags.add(tag);

				if ( tag instanceof TagFeatureFileLocation) {
					
					updateInitialSaveTags( tags, null );
				}
				
				return( true );
			}else{
				return( false );
			}
		}
		
		// This logic might fit better outside the UI code, such as
		// TorrentOpenOptions

		private void
		updateInitialSaveTags(
			List<Tag>					tags,
			TagFeatureFileLocation		removed )
		{
			if ( isSingleOptions ){
				
				TagFeatureFileLocation init = TagUtils.selectInitialDownloadLocation( tags );
				
				if ( init != null ){
					
					if (( init.getTagInitialSaveOptions() & TagFeatureFileLocation.FL_DATA ) == 0){
						
						init = null;
					}
				}
				
				if ( init == null ){
					
					tag_save_location = null;
					
					setSavePathEnabled( true );
					
					if ( removed != null ){
						
							// revert save location
						
						File save_loc = removed.getTagInitialSaveFolder();
	
						if ( 	save_loc != null &&
								( removed.getTagInitialSaveOptions() & TagFeatureFileLocation.FL_DATA ) != 0 &&
								getSavePath().equals( save_loc.getAbsolutePath())){
	
							String old = (String)tagButtonsArea.getData( SP_KEY );
	
							if ( old != null ){
	
								setSavePath( old );
							}
						}
					}
				}else{
					
					setSavePathEnabled( false );
	
						// must have a save folder as selected
					
					tag_save_location = init;
					
					File save_loc = init.getTagInitialSaveFolder();
	
					setSavePath( save_loc.getAbsolutePath());
				}
			}
		}

		/**
		 * If tagToFind is DiscoveredTag, returns {@link DiscoveredTag#existingTag}.
		 * Otherwise, returns DiscoveredTag with same name, if available.
		 */
		private Tag findOtherTag(Tag tagToFind) {
			if (tagToFind instanceof DiscoveredTag) {
				return ((DiscoveredTag) tagToFind).existingTag;
			}

			DiscoveredTag discoveredTag = listDiscoveredTags.get(tagToFind.getTagName(false).toLowerCase());
			if (discoveredTag == null) {
				discoveredTag = listDiscoveredTags.get(tagToFind.getTagName(true).toLowerCase());
			}
			return discoveredTag;
		}

		private void setupTVFiles(SWTSkinObjectContainer soFilesTable, SWTSkinObjectTextbox soFilesFilter ) {
			TableColumnManager tcm = TableColumnManager.getInstance();
			if (tcm.getDefaultColumnNames(TABLEID_FILES) == null) {
				tcm.registerColumn(TorrentOpenFileOptions.class,
						TableColumnOTOF_Position.COLUMN_ID,
						new TableColumnCreationListener() {
							@Override
							public void tableColumnCreated(TableColumn column) {
								new TableColumnOTOF_Position(column);
							}
						});
				tcm.registerColumn(TorrentOpenFileOptions.class,
						TableColumnOTOF_Download.COLUMN_ID,
						new TableColumnCreationListener() {
							@Override
							public void tableColumnCreated(TableColumn column) {
								new TableColumnOTOF_Download(column);
							}
						});
				tcm.registerColumn(TorrentOpenFileOptions.class,
						TableColumnOTOF_Name.COLUMN_ID, new TableColumnCreationListener() {
							@Override
							public void tableColumnCreated(TableColumn column) {
								new TableColumnOTOF_Name(column);
							}
						});
				tcm.registerColumn(TorrentOpenFileOptions.class,
						TableColumnOTOF_Size.COLUMN_ID, new TableColumnCreationListener() {
							@Override
							public void tableColumnCreated(TableColumn column) {
								new TableColumnOTOF_Size(column);
							}
						});
				tcm.registerColumn(TorrentOpenFileOptions.class,
						TableColumnOTOF_Path.COLUMN_ID, new TableColumnCreationListener() {
							@Override
							public void tableColumnCreated(TableColumn column) {
								new TableColumnOTOF_Path(column);
							}
						});
				tcm.registerColumn(TorrentOpenFileOptions.class,
						TableColumnOTOF_Ext.COLUMN_ID, new TableColumnCreationListener() {
							@Override
							public void tableColumnCreated(TableColumn column) {
								new TableColumnOTOF_Ext(column);
							}
						});

				tcm.registerColumn(TorrentOpenFileOptions.class,
						TableColumnOTOF_Priority.COLUMN_ID, new TableColumnCreationListener() {
							@Override
							public void tableColumnCreated(TableColumn column) {
								new TableColumnOTOF_Priority(column);
							}
						});

				tcm.setDefaultColumnNames(TABLEID_FILES, new String[] {
					TableColumnOTOF_Position.COLUMN_ID,
					TableColumnOTOF_Download.COLUMN_ID,
					TableColumnOTOF_Name.COLUMN_ID,
					TableColumnOTOF_Size.COLUMN_ID,
					TableColumnOTOF_Path.COLUMN_ID,
					TableColumnOTOF_Priority.COLUMN_ID
				});
				tcm.setDefaultSortColumnName(TABLEID_FILES, TableColumnOTOF_Position.COLUMN_ID);
			}

			tvFiles = TableViewFactory.createTableViewSWT(TorrentOpenFileOptions.class,
					TABLEID_FILES, TABLEID_FILES, null, "#", SWT.BORDER
							| SWT.FULL_SELECTION | SWT.MULTI);
			tvFiles.initialize(soFilesTable.getComposite());
			tvFiles.setRowDefaultHeightEM(1.4f);

			if ( torrentOptions.getFiles().length > 1 && soFilesFilter != null ){

				soFilesFilter.setVisible( true );

				BubbleTextBox bubbleTextBox = soFilesFilter.getBubbleTextBox();
				
				bubbleTextBox.setMessage(MessageText.getString("TorrentDetailsView.filter"));

				String tooltip = MessageText.getString("filter.tt.start");
				tooltip += MessageText.getString("filesview.filter.tt.line1");
				
				bubbleTextBox.setTooltip( tooltip );
				
				tvFiles.enableFilterCheck(bubbleTextBox, this);

			}else{
				if ( soFilesFilter != null ){

					soFilesFilter.setVisible( false );
				}
			}

			tvFiles.addKeyListener(new KeyListener() {

				@Override
				public void keyPressed(KeyEvent e) {
				}

				@Override
				public void keyReleased(KeyEvent e) {
					if (e.keyCode == SWT.SPACE) {
						TableRowCore focusedRow = tvFiles.getFocusedRow();
						if ( focusedRow != null ){
							TorrentOpenFileOptions tfi_focus = ((TorrentOpenFileOptions) focusedRow.getDataSource());
							boolean download = !tfi_focus.isToDownload();

							TorrentOpenFileOptions[] infos = tvFiles.getSelectedDataSources().toArray(new TorrentOpenFileOptions[0]);
							setToDownload( infos, download );
						}
					}
					if (e.keyCode == SWT.F2 && (e.stateMask & SWT.MODIFIER_MASK) == 0) {
						TorrentOpenFileOptions[] infos = tvFiles.getSelectedDataSources().toArray(
								new TorrentOpenFileOptions[0]);
						if (infos.length > 0) {
							renameFilenames(infos);
						}
						e.doit = false;
						return;
					}
				}
			});

			tvFiles.addMenuFillListener(new TableViewSWTMenuFillListener() {

				@Override
				public void fillMenu(String sColumnName, Menu menu) {
					final Shell shell = menu.getShell();
					MenuItem item;
					TableRowCore focusedRow = tvFiles.getFocusedRow();
					final TorrentOpenFileOptions[] infos = tvFiles.getSelectedDataSources().toArray(new TorrentOpenFileOptions[0]);
					final TorrentOpenFileOptions tfi_focus = ((TorrentOpenFileOptions) focusedRow.getDataSource());
					boolean download = tfi_focus.isToDownload();

					item = new MenuItem(menu, SWT.CHECK);
					Messages.setLanguageText(item, "label.download.file");
					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							TableRowCore focusedRow = tvFiles.getFocusedRow();
							TorrentOpenFileOptions tfi_focus = ((TorrentOpenFileOptions) focusedRow.getDataSource());
							boolean download = !tfi_focus.isToDownload();

							setToDownload( infos, download );
						}
					});
					item.setSelection(download);

					boolean allPriorityAuto = true;
					
					for ( TorrentOpenFileOptions info: infos ){
						
						if ( !info.isPriorityAuto()){
							
							allPriorityAuto = false;
							break;
						}
					}

						// priority

					final MenuItem itemPriority = new MenuItem(menu, SWT.CASCADE);
					Messages.setLanguageText(itemPriority, "FilesView.menu.setpriority");

					final Menu menuPriority = new Menu(shell, SWT.DROP_DOWN);
					itemPriority.setMenu(menuPriority);

					final MenuItem itemHigh = new MenuItem(menuPriority, SWT.CASCADE);

					Messages.setLanguageText(itemHigh, "FilesView.menu.setpriority.high");

					final MenuItem itemNormal = new MenuItem(menuPriority, SWT.CASCADE);

					Messages.setLanguageText(itemNormal, "FilesView.menu.setpriority.normal");

					final MenuItem itemLow = new MenuItem(menuPriority, SWT.CASCADE);

					Messages.setLanguageText(itemLow, "FileItem.low");

					final MenuItem itemNumeric = new MenuItem(menuPriority, SWT.CASCADE);

					Messages.setLanguageText(itemNumeric, "FilesView.menu.setpriority.numeric");

					final MenuItem itemNumericAuto = new MenuItem(menuPriority, SWT.CASCADE);

					Messages.setLanguageText(itemNumericAuto, "FilesView.menu.setpriority.numeric.auto");

					Listener priorityListener = new Listener() {
						@Override
						public void handleEvent(Event event) {

							Widget widget = event.widget;

							int	priority;

							if ( widget == itemHigh ){

								priority = 1;

							}else if ( widget == itemNormal ){

								priority = 0;

							}else if ( widget == itemLow ){

								priority = -1;

							}else if ( widget == itemNumeric ){

								SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
										"FilesView.dialog.priority.title",
										"FilesView.dialog.priority.text");

								entryWindow.prompt(
									new UIInputReceiverListener() {
										@Override
										public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
											if (!entryWindow.hasSubmittedInput()) {
												return;
											}
											String sReturn = entryWindow.getSubmittedInput();

											if (sReturn == null)
												return;

											int priority = 0;

											try {
												priority = Integer.valueOf(sReturn).intValue();
											} catch (NumberFormatException er) {

												Debug.out( "Invalid priority: " + sReturn );

												new MessageBoxShell(SWT.ICON_ERROR | SWT.OK,
														MessageText.getString("FilePriority.invalid.title"),
														MessageText.getString("FilePriority.invalid.text", new String[]{ sReturn })).open(null);

												return;
											}

											for (TorrentOpenFileOptions torrentFileInfo : infos) {

												torrentFileInfo.setPriority( priority, false );
											}
										}
									});

								return;

							}else if ( widget == itemNumericAuto ){

								int	next_priority = 0;

								TorrentOpenFileOptions[] all_files = torrentOptions.getFiles();

								if ( all_files.length != infos.length ){

									Set<Integer>	affected_indexes = new HashSet<>();

									for ( TorrentOpenFileOptions file: infos ){

										affected_indexes.add( file.getIndex());
									}

									for ( TorrentOpenFileOptions file: all_files ){

										if ( !( affected_indexes.contains( file.getIndex()) || !file.isToDownload())){

											next_priority = Math.max( next_priority, file.getPriority()+1);
										}
									}
								}

								next_priority += infos.length;

								for ( TorrentOpenFileOptions file: infos ){

									if ( !file.isPriorityAuto()){
									
										file.setPriority( --next_priority, false );
									}
								}

								return;

							}else{

								return;
							}

							for (TorrentOpenFileOptions torrentFileInfo : infos) {

								if ( !torrentFileInfo.isPriorityAuto()){
								
									torrentFileInfo.setPriority( priority, false );
								}
							}
						}
					};
					
					itemPriority.setEnabled( !allPriorityAuto );

					itemNumeric.addListener(SWT.Selection, priorityListener);
					itemNumericAuto.addListener(SWT.Selection, priorityListener);
					itemHigh.addListener(SWT.Selection, priorityListener);
					itemNormal.addListener(SWT.Selection, priorityListener);
					itemLow.addListener(SWT.Selection, priorityListener);

						// rename

					if (infos.length > 0 ) {
						item = new MenuItem(menu, SWT.PUSH);
						Messages.setLanguageText(item, "FilesView.menu.rename_only");
						item.addSelectionListener(new SelectionAdapter() {

							@Override
							public void widgetSelected(SelectionEvent e) {

								renameFilenames(infos);
							}
						});
					}

					item = new MenuItem(menu, SWT.PUSH);
					Messages.setLanguageText(item,
							"OpenTorrentWindow.fileList.changeDestination");
					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							changeFileDestination(infos,false);
						}
					});

					if ( infos.length > 1 && torrentOptions.getStartMode() != TorrentOpenOptions.STARTMODE_SEEDING ){
						item = new MenuItem(menu, SWT.PUSH);
						Messages.setLanguageText(item,
								"OpenTorrentWindow.fileList.changeDestination.all", new String[]{ String.valueOf( infos.length )});
						item.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								changeFileDestination(infos,true);
							}
						});
					}

					new MenuItem(menu, SWT.SEPARATOR);

					item = new MenuItem(menu, SWT.PUSH);
					Messages.setLanguageText(item, "Button.selectAll");
					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							tvFiles.selectAll();
						}
					});


					String dest_path = tfi_focus.getDestPathName();
					String parentDir = tfi_focus.parent.getParentDir();

					List<String> folder_list = new ArrayList<>();

					folder_list.add( dest_path );

					if ( dest_path.startsWith( parentDir ) && dest_path.length() > parentDir.length()){

						String relativePath = dest_path.substring( parentDir.length() + 1 );

						while ( relativePath.contains( File.separator )){

							int	pos = relativePath.lastIndexOf( File.separator );

							relativePath = relativePath.substring( 0,  pos );

							folder_list.add( parentDir + File.separator + relativePath );
						}
					}

					for ( int i=folder_list.size()-1;i>=0;i-- ){

						final String this_dest_path = folder_list.get(i);

						item = new MenuItem(menu, SWT.PUSH);
						Messages.setLanguageText(item, "menu.selectfilesinfolder", new String[] {
								this_dest_path
						});

						item.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent e) {
								TableRowCore[] rows = tvFiles.getRows();
								for (TableRowCore row : rows) {
									Object dataSource = row.getDataSource();
									if (dataSource instanceof TorrentOpenFileOptions) {
										TorrentOpenFileOptions fileOptions = (TorrentOpenFileOptions) dataSource;
										if ( fileOptions.getDestPathName().startsWith( this_dest_path )){
											row.setSelected(true);
										}
									}
								}

							}
						});
					}

					new MenuItem(menu, SWT.SEPARATOR );

					if ( !torrentOptions.isSimpleTorrent()){


						 item = new MenuItem(menu, SWT.PUSH);

						 Messages.setLanguageText(item, "OpenTorrentWindow.set.savepath");

						 item.addSelectionListener(new SelectionAdapter() {
								@Override
								public void widgetSelected(SelectionEvent e) {
									setSavePath();
								}
							});

						 item = new MenuItem(menu, SWT.PUSH);

						 Messages.setLanguageText(item, "OpenTorrentWindow.tlf.remove");

						 item.addSelectionListener(new SelectionAdapter() {
								@Override
								public void widgetSelected(SelectionEvent e) {
									removeTopLevelFolder();
								}
							});

						 item.setEnabled( canRemoveTopLevelFolder());

						 item = new MenuItem(menu, SWT.CHECK );

						 item.setSelection( COConfigurationManager.getBooleanParameter( "open.torrent.window.rename.on.tlf.change" ));

						 Messages.setLanguageText(item, "OpenTorrentWindow.tlf.rename");

						 item.addSelectionListener(
							 new SelectionAdapter()
							 {
								 @Override
								 public void
								 widgetSelected(
										 SelectionEvent e )
								 {
									 COConfigurationManager.setParameter(
											"open.torrent.window.rename.on.tlf.change",
											((MenuItem)e.widget).getSelection());
								 }
							 });
						 
						 new MenuItem(menu, SWT.SEPARATOR );
					}
					
						// auto-prioritise options
						
					item = new MenuItem(menu, SWT.PUSH);
	
					Messages.setLanguageText(item, "OpenTorrentWindow.show.priority.options");
	
					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							UIFunctions uif = UIFunctionsManager.getUIFunctions();
	
							if ( uif != null ){
	
								JSONObject args = new JSONObject();
	
								args.put( "select", ConfigSectionFile.REFID_TORRENT_ADD_AUTO_PRIORITY);
								
								String args_str = JSONUtils.encodeToJSON( args );
								
								uif.getMDI().showEntryByID(
										MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
										"files" + args_str );
							}
						}
					});

						// auto-skip options
					
					item = new MenuItem(menu, SWT.PUSH);

					Messages.setLanguageText(item, "OpenTorrentWindow.show.skip.options");

					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							UIFunctions uif = UIFunctionsManager.getUIFunctions();

							if ( uif != null ){

								JSONObject args = new JSONObject();

								args.put( "select", ConfigSectionFile.REFID_TORRENT_ADD_AUTO_SKIP);
								
								String args_str = JSONUtils.encodeToJSON( args );
								
								uif.getMDI().showEntryByID(
										MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
										"files" + args_str );
							}
						}
					});

						// auto-tag options
					
					item = new MenuItem(menu, SWT.PUSH);
					
					Messages.setLanguageText(item, "OpenTorrentWindow.show.autotag.options");

					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							UIFunctions uif = UIFunctionsManager.getUIFunctions();

							if ( uif != null ){

								JSONObject args = new JSONObject();

								args.put( "select", ConfigSectionInterfaceTags.REFID_TORRENT_ADD_AUTO_TAG);
								
								String args_str = JSONUtils.encodeToJSON( args );
								
								uif.getMDI().showEntryByID(
										MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
										ConfigSectionInterfaceTags.SECTION_ID + args_str );
							}
						}
					});

					
					 new MenuItem(menu, SWT.SEPARATOR );
				}

				@Override
				public void addThisColumnSubMenu(String sColumnName, Menu menuThisColumn) {
				}
			});

			tvFiles.addSelectionListener(new TableSelectionListener() {

				@Override
				public void selected(TableRowCore[] row) {
					updateFileButtons();
				}

				@Override
				public void mouseExit(TableRowCore row) {
				}

				@Override
				public void mouseEnter(TableRowCore row) {
				}

				@Override
				public void focusChanged(TableRowCore focus) {
				}

				@Override
				public void deselected(TableRowCore[] rows) {
					updateFileButtons();
				}

				@Override
				public void 
				defaultSelected(
					TableRowCore[]		rows, 
					int stateMask		) 
				{
					TorrentOpenFileOptions[] infos = new TorrentOpenFileOptions[rows.length];
					
					int pos = 0;
					
					for ( TableRowCore row: rows ){
						
						infos[pos++] = (TorrentOpenFileOptions)row.getDataSource();
					}
					
					
					renameFilenames( infos );
				}
			}, false);

			TorrentOpenFileOptions[] files = torrentOptions.getFiles();
			
			TOTorrent torrent = torrentOptions.getTorrent();
			
			TOTorrentFile[] torrent_files = torrent.getFiles();
			
			List<TorrentOpenFileOptions> visible_files = new ArrayList<>( files.length );
			
			for ( TorrentOpenFileOptions file: files ){
				
				if ( !torrent_files[file.getIndex()].isPadFile()){
					
					visible_files.add( file );
				}
			}
			
			tvFiles.addDataSources( visible_files.toArray( new TorrentOpenFileOptions[0] ));
		}

		@Override
		public boolean
		filterCheck(
			TorrentOpenFileOptions 	ds,
			String 					filter,
			boolean 				regex,
			boolean					confusable )
		{
			if ( filter == null || filter.length() == 0 ){

				return( true );
			}

			if ( confusable ){
			
				filter = GeneralUtils.getConfusableEquivalent(filter,true);
			}
			
			try {
				File file = ds.getDestFileFullName();
				
				boolean filter_on_path = false;
				
				if ( filter.startsWith( "p:" )){
				
					filter_on_path = true;
					
					filter = filter.substring(2);
					
				}else if ( filter.startsWith( File.separator )){
					
					filter_on_path = true;
					
					filter = filter.substring(1);
				}
				
				if ( filter.isEmpty()){
						
					return( true );
				}
				
				String name = filter_on_path?file.getAbsolutePath():file.getName();

				if ( confusable ){
				
					name = GeneralUtils.getConfusableEquivalent(name,false);
				}
				
				String s = regex ? filter : RegExUtil.splitAndQuote( filter, "\\s*[|;]\\s*" );

				boolean	match_result = true;

				if ( regex && s.startsWith( "!" )){

					s = s.substring(1);

					match_result = false;
				}

				Pattern pattern = RegExUtil.getCachedPattern( "fv:search", s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

				boolean result = pattern.matcher(name).find() == match_result;
				
				return( result );

			} catch (Exception e) {

				return true;
			}
		}

		@Override
		public void filterSet(String filter)
		{
		}

		protected void updateFileButtons() {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					TableRowCore[] rows = tvFiles.getSelectedRows();

					boolean hasRowsSelected = rows.length > 0;
					if (btnRename != null && !btnRename.isDisposed()) {
						btnRename.setEnabled(rows.length > 0 );
					}
					if (btnRetarget != null && !btnRetarget.isDisposed()) {
						btnRetarget.setEnabled(hasRowsSelected);
					}

					boolean all_marked 		= true;
					boolean all_unmarked 	= true;

					for ( TableRowCore row: rows ){
						TorrentOpenFileOptions tfi = ((TorrentOpenFileOptions) row.getDataSource());
						if ( tfi.isToDownload()){
							all_unmarked 	= false;
						}else{
							all_marked 		= false;
						}
					}

					if ( btnSelectAll != null && !btnSelectAll.isDisposed()){
						btnSelectAll.setEnabled(  rows.length < torrentOptions.getFiles().length );
					}

					if ( btnMarkSelected != null && !btnMarkSelected.isDisposed() ){
						btnMarkSelected.setEnabled( hasRowsSelected && !all_marked );
					}

					if ( btnUnmarkSelected != null && !btnUnmarkSelected.isDisposed() ){
						btnUnmarkSelected.setEnabled( hasRowsSelected && !all_unmarked );
					}

					if (btnSwarmIt != null && !btnSwarmIt.isDisposed()){
						boolean	enable=false;
						if ( rows.length == 1 ){
							TorrentOpenFileOptions tfi = ((TorrentOpenFileOptions) rows[0].getDataSource());
							enable = tfi.lSize >= 50*1024*1024;
						}
						btnSwarmIt.setEnabled(enable);
					}
				}
			});
		}

		protected void 
		renameFilenames(TorrentOpenFileOptions[] torrentFileInfos) 
		{
			renameFilenames(torrentFileInfos, 0);
		}
		
		protected void 
		renameFilenames(TorrentOpenFileOptions[] torrentFileInfos, int index)
		{
			if ( index >= torrentFileInfos.length ){
				
				return;
			}
			
			TorrentOpenFileOptions torrentFileInfo = torrentFileInfos[index];
			
			SimpleTextEntryWindow dialog = new SimpleTextEntryWindow(
					"FilesView.rename.filename.title", "FilesView.rename.filename.text");
			
			String fileName = torrentFileInfo.getOriginalFileName();
			
			dialog.setPreenteredText(fileName, false); // false -> it's not "suggested", it's a previous value
			
			int pos = fileName.lastIndexOf( '.' );

			if ( pos > 0 ){

				dialog.selectPreenteredTextRange( new int[]{ 0, pos });
			}
					
			dialog.allowEmptyInput(false);
			dialog.setRememberLocationSize( "file.rename.dialog.pos" );
			dialog.prompt(new UIInputReceiverListener() {
				@Override
				public void UIInputReceiverClosed(UIInputReceiver receiver) {
					if (shell.isDisposed() || !receiver.hasSubmittedInput()) {
						return;
					}
					String renameFilename = receiver.getSubmittedInput();
					if (renameFilename == null) {
						return;
					}
					torrentFileInfo.setDestFileName(renameFilename,true);
					TableRowCore row = tvFiles.getRow(torrentFileInfo);
					if (row != null) {
						row.invalidate(true);
						row.refresh(true);
					}
					
					Utils.execSWTThreadLater(1, ()->{
						renameFilenames( torrentFileInfos, index+1 );
					});
				}
			});
		}

		private void
		setSavePath()
		{
			if ( torrentOptions.isSimpleTorrent()){

				changeFileDestination( torrentOptions.getFiles(), false );

			}else{
				DirectoryDialog dDialog = new DirectoryDialog(shell,SWT.SYSTEM_MODAL);

				File filterPath = FileUtil.newFile( torrentOptions.getDataDir());

				if ( !filterPath.exists()){
					filterPath = filterPath.getParentFile();
				}
				dDialog.setFilterPath( filterPath.getAbsolutePath());
				dDialog.setMessage(MessageText.getString("MainWindow.dialog.choose.savepath")
						+ " (" + torrentOptions.getTorrentName() + ")");
				String sNewDir = dDialog.open();

				if (sNewDir == null){
					return;
				}

				File newDir = FileUtil.newFile(sNewDir).getAbsoluteFile();

				if ( !newDir.isDirectory()){

					if ( newDir.exists()){

						Debug.out( "new dir isn't a dir!" );

						return;

					}else if ( !newDir.mkdirs()){

						Debug.out( "Failed to create '" + newDir + "'" );

						return;
					}
				}

				setTopLevelFolder( newDir, false );
			}
		}

		private boolean
		canRemoveTopLevelFolder()
		{
			if ( torrentOptions.isSimpleTorrent()){

				return( false );

			}else{

				File oldDir = FileUtil.newFile( torrentOptions.getDataDir());

				File newDir = oldDir.getParentFile();

				File newParent  = newDir.getParentFile();

					// newParent will be null if trying to remove the top level dir when already at a file system
					// root (e.g. C:\)
					// we should of course be able to support this, but unfortunately there's lots of code in Vuze
					// in places-i-don't-want-to-change that borks if we try and do this (feel free to try...)

				return( newParent != null );
			}
		}

		private void
		removeTopLevelFolder()
		{
			if ( torrentOptions.isSimpleTorrent()){


			}else{

				File oldDir = FileUtil.newFile( torrentOptions.getDataDir());

				File newDir = oldDir.getParentFile();

				setTopLevelFolder( newDir, true );
			}
		}

		private void
		setTopLevelFolder(
			File		newDir,
			boolean		removedTop )
		{
			File newParent = newDir.getParentFile();
			
			if ( newParent == null ){

				Debug.out( "Invalid save path, parent folder is null" );

				return;
			}
			
			torrentOptions.setExplicitDataDir( newParent.getAbsolutePath(), newDir.getName(), removedTop );

			if ( COConfigurationManager.getBooleanParameter( "open.torrent.window.rename.on.tlf.change" )){

				TorrentOpenFileOptions[] files = torrentOptions.getFiles();
				
					// if only one file is left selected then use this as the new name rather than
					// the most likely less useful folder name
				
				TorrentOpenFileOptions	single_file = null;
				
				for ( TorrentOpenFileOptions file: files ){
					
					if ( file.isToDownload()){
						
						if ( single_file == null ){
							
							single_file = file;
							
						}else{
							
							single_file = null;
							
							break;
						}
					}
				}
				
				String new_name;
				
				if ( single_file == null ){
					
					new_name = newDir.getName();
					
				}else{
					
					new_name = single_file.getDestFileName();
				}
				
				torrentOptions.setManualRename( new_name );

			}else{

				torrentOptions.setManualRename( null );
			}

			updateDataDirCombo();

			cmbDataDirChanged();
		}
		
		private void changeFileDestination(TorrentOpenFileOptions[] infos, boolean allAtOnce ) {

			if ( allAtOnce && infos.length > 1 ){

					// find a common ancestor if it exists

				String current_parent = null;

				for (TorrentOpenFileOptions fileInfo : infos) {

					String dest = fileInfo.getDestPathName();

					if ( current_parent == null ){

						current_parent = dest;

					}else{

						if ( !current_parent.equals( dest )){

							char[] cp_chars = current_parent.toCharArray();
							char[] p_chars	= dest.toCharArray();

							int cp_len 	= cp_chars.length;
							int	p_len	= p_chars.length;

							int	min = Math.min( cp_len, p_len );

							int pos = 0;

							while ( pos < min && cp_chars[pos] == p_chars[pos] ){

								pos++;
							}

							if ( pos < cp_len ){

								File f = FileUtil.newFile( new String( cp_chars, 0, pos ) + "x" );

								File pf = f.getParentFile();

								if ( pf == null ){

									current_parent = "";

								}else{

									current_parent = pf.toString();
								}
							}
						}
					}
				}

				DirectoryDialog dDialog = new DirectoryDialog( shell, SWT.SYSTEM_MODAL );

				if ( current_parent.length() > 0 ){

					dDialog.setFilterPath( current_parent );
				}

				dDialog.setMessage(MessageText.getString("MainWindow.dialog.choose.savepath_forallfiles"));

				String sSavePath = dDialog.open();

				if ( sSavePath != null) {

					if ( sSavePath.endsWith( File.separator )){

						sSavePath = sSavePath.substring( 0, sSavePath.length() - 1 );
					}

					int prefix_len = current_parent.length();

					for ( TorrentOpenFileOptions fileInfo: infos ){

						String dest = fileInfo.getDestPathName();

						if ( prefix_len == 0 ){

							File f = FileUtil.newFile( dest );

							while( f.getParentFile() != null ){

								f = f.getParentFile();
							}

							dest = dest.substring( f.toString().length());

						}else{

							dest = dest.substring( prefix_len );
						}

						if ( dest.startsWith( File.separator )){

							dest = dest.substring( 1 );
						}

						if ( dest.length() > 0 ){

							fileInfo.setDestPathName( sSavePath + File.separator + dest );

						}else{

							fileInfo.setDestPathName( sSavePath );
						}
					}
				}
			}else{
				for (TorrentOpenFileOptions fileInfo : infos) {
					int style = (fileInfo.parent.getStartMode() == TorrentOpenOptions.STARTMODE_SEEDING)
							? SWT.OPEN : SWT.SAVE;
					FileDialog fDialog = new FileDialog(shell, SWT.SYSTEM_MODAL
							| style);

					String sFilterPath = fileInfo.getDestPathName();
					String sFileName = fileInfo.getOriginalFileName();

					File f = FileUtil.newFile(sFilterPath);
					if (!f.isDirectory()) {
						// Move up the tree until we have an existing path
						while (sFilterPath != null) {
							String parentPath = f.getParent();
							if (parentPath == null)
								break;

							sFilterPath = parentPath;
							f = FileUtil.newFile(sFilterPath);
							if (f.isDirectory())
								break;
						}
					}

					if (sFilterPath != null){
						fDialog.setFilterPath(sFilterPath);
					}

					fDialog.setFileName(sFileName);
					fDialog.setText(MessageText.getString("MainWindow.dialog.choose.savepath")
							+ " (" + fileInfo.orgFullName + ")");
					String sNewName = fDialog.open();

					if (sNewName == null)
						return;

					if (fileInfo.parent.getStartMode() == TorrentOpenOptions.STARTMODE_SEEDING) {
						File file = FileUtil.newFile(sNewName);
						if (file.length() == fileInfo.lSize)
							fileInfo.setFullDestName(sNewName);
						else {
							MessageBoxShell mb = new MessageBoxShell(SWT.OK,
									"OpenTorrentWindow.mb.badSize", new String[] {
										file.getName(),
										fileInfo.orgFullName
									});
							mb.setParent(shell);
							mb.open(null);
						}
					} else
						fileInfo.setFullDestName(sNewName);

				} // for i
			}

			checkSeedingMode();
			updateDataDirCombo();
			diskFreeInfoRefreshPending = true;
		}

		private void setupInfoSection(SWTSkin skin) {
			SWTSkinObject so;

			so = skin.getSkinObject("expanditem-torrentinfo");
			if (so instanceof SWTSkinObjectExpandItem) {
				soExpandItemTorrentInfo = (SWTSkinObjectExpandItem) so;
				soExpandItemTorrentInfo.setText(MessageText.getString("OpenTorrentOptions.header.torrentinfo")
						+ ": " + torrentOptions.getTorrentName());
			}

			so = skin.getSkinObject("torrentinfo-name");
			TOTorrent torrent = torrentOptions.getTorrent();

			if (so instanceof SWTSkinObjectText) {

				String hash_str = TorrentUtils.nicePrintTorrentHash( torrent );

				SWTSkinObjectText text = (SWTSkinObjectText)so;

				text.setText( torrentOptions.getTorrentName() +  (torrent==null?"":("\u00a0\u00a0\u00a0\u00a0[" + hash_str + "]")));

				if ( torrent != null ){

					Control control = text.getControl();
					
					Menu menu = new Menu( control );
					
					control.setMenu(menu);
					
					MenuItem save_torrent = new MenuItem( menu, SWT.PUSH );
					
					Messages.setLanguageText(save_torrent, "menu.save.torrent" );
					
					save_torrent.addListener(SWT.Selection, (ev)->{
						
						TorrentUtil.exportTorrent(torrentOptions.getTorrentName() + ".torrent", torrent, control.getShell());
					});
					
					new MenuItem( menu, SWT.SEPARATOR );
					
					ClipboardCopy.addCopyToClipMenu(
						menu,
						new ClipboardCopy.copyToClipProvider2() {

							@Override
							public String
							getMenuResource()
							{
								return( "menu.copy.hash.to.clipboard" );
							}

							@Override
							public String
							getText()
							{
								return( TorrentUtils.nicePrintTorrentHash( torrent, true ));
							}
						});
				}
			}


			if ( torrent != null ){
				so = skin.getSkinObject("torrentinfo-trackername");
				if (so instanceof SWTSkinObjectText) {
					((SWTSkinObjectText) so).setText(TrackerNameItem.getTrackerName(torrent) + ((torrent==null||!torrent.getPrivate())?"":(" (private)")));
				}

				so = skin.getSkinObject("torrentinfo-comment");
				if (so instanceof SWTSkinObjectText) {

					try {
						LocaleUtilDecoder decoder = LocaleTorrentUtil.getTorrentEncoding(torrent);
						String s = decoder.decodeString(torrent.getComment());
						((SWTSkinObjectText) so).setText(s);
					} catch (UnsupportedEncodingException e) {
					} catch (TOTorrentException e) {
					}
				}

				so = skin.getSkinObject("torrentinfo-createdon");
				if (so instanceof SWTSkinObjectText) {
					String creation_date = DisplayFormatters.formatDate(torrent.getCreationDate() * 1000l);
					((SWTSkinObjectText) so).setText(creation_date);
				}

				so = skin.getSkinObject("torrentinfo-encoding");
				if (so instanceof SWTSkinObjectText) {
					SWTSkinObjectText soTorrentEncoding = (SWTSkinObjectText) so;
					
					String enc_str = MessageText.getString("TorrentInfoView.torrent.encoding") + ": "
							+ getEncodingName(torrent);
					
					int tt = torrent.getTorrentType();
						
					int ett = torrent.getEffectiveTorrentType();

					String extra = "";
					
					if ( tt == TOTorrent.TT_V1_V2 ){
						
						if ( ett != tt ){
							
							extra = ", " + MessageText.getString( "label.effective" ) + " V" + (ett==TOTorrent.TT_V1?1:2);
						}
					}
					
					enc_str += "; " + MessageText.getString( "label.torrent.type" ) + ": " + MessageText.getString( "label.torrent.type." + tt ) + extra;

					soTorrentEncoding.setText( enc_str );
					
					Runnable chooseEncoding = ()->{
						try {
							LocaleTorrentUtil.getTorrentEncoding(torrent, true, true);
							torrentOptions.rebuildOriginalNames();
							setupInfoSection(skin);
							if (txtSubFolder != null) {
								String top = FileUtil.newFile(torrentOptions.getDataDir()).getName();

								txtSubFolder.setText(top);
							}

						} catch (TOTorrentException ex) {
							Debug.out(ex);
						}
					};
					
					Control control = so.getControl();
					
					if (control.getData("hasMouseL") == null) {
						control.addMouseListener(new MouseAdapter() {
							@Override
							public void mouseUp(MouseEvent e) {
								if ( e.button != 1 ){
									return;
								}

								chooseEncoding.run();
							}
						});
					
						control.setData("hasMouseL", true);
					}
					
					Menu menu = new Menu( control );
					
					control.setMenu( menu );
					
					MenuItem mi = new MenuItem( menu, SWT.PUSH );
					
					mi.setText( MessageText.getString( "LocaleUtil.title" ));
					
					mi.addListener( SWT.Selection, (ev)->chooseEncoding.run());
					
					mi = new MenuItem( menu, SWT.PUSH );
					
					mi.setText( MessageText.getString( "label.effective" ) + " -> V" + (tt==TOTorrent.TT_V1_V2?(ett==TOTorrent.TT_V1?2:1):(tt==TOTorrent.TT_V1?1:2)));
					
					mi.addListener( SWT.Selection, (ev)->{
					
						try{
							String file = TorrentUtils.getTorrentFileName( torrent );
							
							TOTorrent new_torrent = torrent.selectHybridHashType( ett==TOTorrent.TT_V1?TOTorrent.TT_V2:TOTorrent.TT_V1 );
							
							if ( file != null ){
						
								TorrentUtils.writeToFile( new_torrent, new File( file ), false );
							}
							
							torrentOptions.setTorrent( new_torrent );
							
							setupInfoSection(skin);
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					});

					mi.setEnabled( tt == TOTorrent.TT_V1_V2 );
				}

			}
		}

		private String getEncodingName(TOTorrent torrent) {
			String encoding = torrent.getAdditionalStringProperty("encoding");
			if (encoding == null) {
				LocaleUtilDecoder decoder = LocaleTorrentUtil.getTorrentEncodingIfAvailable(
						torrent);
				if (decoder != null) {
					encoding = decoder.getName();
				}
			}

			return encoding;
		}

		private void setupTrackers(SWTSkinObjectContainer so) {
			Composite parent = so.getComposite();

			Button button = new Button( parent, SWT.PUSH );
			Messages.setLanguageText( button, "label.edit.trackers" );

			if ( isSingleOptions ){
				
				TOTorrent torrent = torrentOptions.getTorrent();
	
				button.setEnabled( torrent != null && !TorrentUtils.isReallyPrivate( torrent ));
	
				button.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						List<List<String>> trackers = torrentOptions.getTrackers( false );
						new MultiTrackerEditor( shell, null, trackers, new TrackerEditorListener() {
							@Override
							public void trackersChanged(String str, String str2, List<List<String>> updatedTrackers) {
								torrentOptions.setTrackers(updatedTrackers);
							}
						}, true, true );
					}});
			}else{
				
				List<TOTorrent>				torrents = new ArrayList<>();
				
				boolean bad = false;
				
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					
					TOTorrent torrent = to.getTorrent();
					
					if ( torrent == null || TorrentUtils.isReallyPrivate( torrent )){
						
						bad = true;
						
						break;
					}
					
					torrents.add( torrent );
				}
				
				button.setEnabled( !bad && !torrents.isEmpty());
				
				if ( !bad ){
					
					button.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							List<List<List<String>>> groups = new ArrayList<>();
							
							for ( TorrentOpenOptions to: torrentOptionsMulti ){
								List<List<String>> trackers = to.getTrackers( false );
								groups.add( trackers );
							}
							final List<List<String>>	merged_trackers = TorrentUtils.getMergedTrackersFromGroups( groups );
							new MultiTrackerEditor( shell, null, merged_trackers, new TrackerEditorListener() {
								@Override
								public void trackersChanged(String str, String str2, List<List<String>> updatedTrackers) {
									for ( TorrentOpenOptions to: torrentOptionsMulti ){
										to.setTrackers(updatedTrackers);
									}
								}
							}, true, true );
						}});
				}
			}
		}

		private void setupUpDownLimitOption(SWTSkinObjectContainer so) {
			Composite parent = so.getComposite();

			parent.setLayout( new GridLayout(4, false));

			IntSwtParameter paramMaxUploadSpeed = new IntSwtParameter(parent,
					"torrentoptions.config.uploadspeed", "TableColumn.header.maxupspeed",
					"", 0, Integer.MAX_VALUE, new IntSwtParameter.ValueProcessor() {
						@Override
						public Integer getValue(IntSwtParameter p) {
							return torrentOptions.getMaxUploadSpeed();
						}

						@Override
						public boolean setValue(IntSwtParameter p, Integer value) {
							if (torrentOptions.getMaxUploadSpeed() != value) {
								torrentOptions.setMaxUploadSpeed(value);
								return true;
							}
							return false;
						}
					});
			paramMaxUploadSpeed.setSuffixLabelText(
					DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB));

			IntSwtParameter paramMaxDownloadSpeed = new IntSwtParameter(parent,
					"torrentoptions.config.downloadspeed",
					"TableColumn.header.maxdownspeed", "", 0, Integer.MAX_VALUE,
					new IntSwtParameter.ValueProcessor() {
						@Override
						public Integer getValue(IntSwtParameter p) {
							return torrentOptions.getMaxDownloadSpeed();
						}

						@Override
						public boolean setValue(IntSwtParameter p, Integer value) {
							if (torrentOptions.getMaxDownloadSpeed() != value) {
								torrentOptions.setMaxDownloadSpeed(value);
								return true;
							}
							return false;
						}
					});
			paramMaxDownloadSpeed.setSuffixLabelText(
					DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB));

		}

		private void setupIPFilterOption(SWTSkinObjectContainer so) {
			Composite parent = so.getComposite();

			parent.setLayout( new GridLayout());

			Button button = new Button(parent, SWT.CHECK | SWT.WRAP );
			if ( Constants.isWindows ){
				button.setBackground( Colors.white );
			}
			Messages.setLanguageText(button, "MyTorrentsView.menu.ipf_enable");
			GridData gd = new GridData();
			gd.verticalAlignment = SWT.CENTER;
			button.setLayoutData(gd);
			button.setSelection(!torrentOptions.disableIPFilter);

			button.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					torrentOptions.disableIPFilter = !((Button) e.widget).getSelection();
				}
			});

		}

		private void setupPeerSourcesAndNetworkOptions(SWTSkinObjectContainer so) {
			Composite parent = so.getComposite();

			Composite peer_sources_composite = new Composite(parent, SWT.NULL);

			{
				peer_sources_composite.setLayout(new RowLayout(SWT.HORIZONTAL));
				Group peer_sources_group = Utils.createSkinnedGroup(peer_sources_composite, SWT.NULL);
				Messages.setLanguageText(peer_sources_group,
						"ConfigView.section.connection.group.peersources");
				RowLayout peer_sources_layout = new RowLayout();
				peer_sources_layout.pack = true;
				peer_sources_layout.spacing = 10;
				peer_sources_group.setLayout(peer_sources_layout);

				FormData form_data = Utils.getFilledFormData();
				form_data.bottom = null;
				peer_sources_composite.setLayoutData(form_data);

				//		Label label = new Label(peer_sources_group, SWT.WRAP);
				//		Messages.setLanguageText(label,
				//				"ConfigView.section.connection.group.peersources.info");
				//		GridData gridData = new GridData();
				//		Utils.setLayoutData(label, gridData);

				for (int i = 0; i < PEPeerSource.PS_SOURCES.length; i++) {

					final String p = PEPeerSource.PS_SOURCES[i];

					String config_name = "Peer Source Selection Default." + p;
					String msg_text = "ConfigView.section.connection.peersource." + p;

					Button button = new Button(peer_sources_group, SWT.CHECK);
					if ( Constants.isWindows ){
						button.setBackground( Colors.white );
					}
					Messages.setLanguageText(button, msg_text);

					button.setSelection(COConfigurationManager.getBooleanParameter(config_name));

					button.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							torrentOptions.peerSource.put(p, ((Button)e.widget).getSelection());
						}
					});
				}
			}

				// networks

			{
				Composite network_group_parent = new Composite(parent, SWT.NULL);
				network_group_parent.setLayout(new RowLayout(SWT.HORIZONTAL));

				Group network_group = Utils.createSkinnedGroup(network_group_parent, SWT.NULL);
				Messages.setLanguageText(network_group,
						"ConfigView.section.connection.group.networks");
				RowLayout network_layout = new RowLayout();
				network_layout.pack = true;
				network_layout.spacing = 10;
				network_group.setLayout(network_layout);

				FormData form_data = Utils.getFilledFormData();
				form_data.top = new FormAttachment( peer_sources_composite );
				network_group_parent.setLayoutData(form_data);

				for (int i = 0; i < AENetworkClassifier.AT_NETWORKS.length; i++) {

					final String nn = AENetworkClassifier.AT_NETWORKS[i];

					String msg_text = "ConfigView.section.connection.networks." + nn;

					Button button = new Button(network_group, SWT.CHECK);
					if ( Constants.isWindows ){
						button.setBackground( Colors.white );
					}
					Messages.setLanguageText(button, msg_text);

					network_buttons.add( button );

					Map<String,Boolean> enabledNetworks = torrentOptions.getEnabledNetworks();

					button.setSelection(enabledNetworks.get( nn ));

					button.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							torrentOptions.setNetworkEnabled(nn, ((Button)e.widget).getSelection());
						}
					});
				}
			}
		}

		private void updateNetworkOptions() {
			if ( network_buttons.size() != AENetworkClassifier.AT_NETWORKS.length ){
				return;
			}

			Map<String,Boolean> enabledNetworks = torrentOptions.getEnabledNetworks();

			for (int i = 0; i < AENetworkClassifier.AT_NETWORKS.length; i++) {

				network_buttons.get(i).setSelection(enabledNetworks.get( AENetworkClassifier.AT_NETWORKS[i]));
			}
		}

		private void updateDataDirCombo() {

			if (cmbDataDir == null) {
				return;
			}

			try{
				bSkipDataDirModify = true;

				if ( torrentOptions == null ){

					String prev_parent = null;

					boolean not_same = false;

					for ( TorrentOpenOptions to: torrentOptionsMulti ){

						String parent = to.getParentDir();

						if ( prev_parent != null && !prev_parent.equals( parent )){

							not_same = true;

							break;
						}

						prev_parent = parent;

					}

					if ( not_same ){

						cmbDataDir.setText( COConfigurationManager.getStringParameter(PARAM_DEFSAVEPATH));

					}else{

							// prev_parent can be null when we're tearing down the dialog...

						cmbDataDir.setText( prev_parent==null?"":prev_parent );
					}
				}else{

					cmbDataDir.setText( torrentOptions.getParentDir());
				}

			}finally{

				bSkipDataDirModify = false;
			}
		}

		private void
		setSavePath(
			String	path )
		{
			if ( cmbDataDir != null ){
				cmbDataDir.setText( path );
			}
		}

		private void
		setSavePathEnabled(
			boolean		enabled )
		{
			cmbDataDirEnabled = enabled;
			
			if ( cmbDataDir != null ){
				
				cmbDataDir.setEnabled( enabled );
				btnDataDir.setEnabled( enabled );
				btnSearch.setEnabled( enabled );
			}
		}
		
		private String
		getSavePath()
		{
			if ( isSingleOptions ){
				return( torrentOptions.getParentDir());
			}else{
				return( "" );
			}
		}

		private void updateQueueLocationCombo() {
			if (cmbQueueLocation == null){
				return;
			}
			
			boolean includeAuto = true;
			if ( isSingleOptions ){
				includeAuto = torrentOptions.getAutoQueuePositionTime() > 0;
			}else{
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					if ( to.getAutoQueuePositionTime() <= 0 ){
						includeAuto = false;
						break;
					}
				}
			}
			List<String> sItemsText = new ArrayList<>();
			for (int i = 0; i < MSGKEY_QUEUELOCATIONS.length; i++) {
				if ( !includeAuto && i == TorrentOpenOptions.QUEUELOCATION_AUTO){
					continue;
				}
				String sText = MessageText.getString(MSGKEY_QUEUELOCATIONS[i]);
				sItemsText.add( sText );
			}
			cmbQueueLocation.setItems(sItemsText.toArray( new String[0]));
			if ( isSingleOptions ){
				cmbQueueLocation.select(torrentOptions.getQueueLocation());
			}else{
				int queueLocation = -1;
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					int ql = to.getQueueLocation();
					if (queueLocation == -1 ){
						queueLocation = ql;
					}else if ( queueLocation != ql ){
						queueLocation = -2;
					}
				}
				if ( queueLocation >= 0 ){
					cmbQueueLocation.select( queueLocation );
				}else{
					cmbQueueLocation.deselectAll();
				}
			}
		}
		
		private void updateSequentialDownloadButton(){
			if (btnSequentialDownload == null)
				return;

		
			if ( isSingleOptions ){
				btnSequentialDownload.setSelection( torrentOptions.getSequentialDownload());
			}else{
				int	seq = -1;
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					int s = to.getSequentialDownload()?1:0;
					if ( seq == -1 ){
						seq = s;
					}else if ( seq != s ){
						seq = -2;
					}
				}
				
				if ( seq >= 0 ){
					btnSequentialDownload.setSelection( seq==1 );
					btnSequentialDownload.setGrayed( false );
				}else{
					btnSequentialDownload.setSelection( true );
					btnSequentialDownload.setGrayed( true );
				}
			}
		}

		private void updateSize() {

			if (soFileAreaInfo == null && soExpandItemFiles == null) {
				return;
			}

			/*
			 * determine info for selected torrents only
			 */
			long totalSize = 0;
			long checkedSize = 0;
			long numToDownload = 0;

			TorrentOpenFileOptions[] dataFiles = torrentOptions.getFiles();
			for (TorrentOpenFileOptions file : dataFiles) {
				totalSize += file.lSize;

				if (file.isToDownload()) {
					checkedSize += file.lSize;
					numToDownload++;
				}
			}

			boolean	changed = checkedSize != currentSelectedDataSize;

			currentSelectedDataSize = checkedSize;

			String text;
			// build string and set label
			if (totalSize == 0) {
				text = "";
			} else if (checkedSize == totalSize) {
				text = DisplayFormatters.formatByteCountToKiBEtc(totalSize);
			} else {
				text = MessageText.getString("OpenTorrentWindow.filesInfo", new String[] {
					DisplayFormatters.formatByteCountToKiBEtc(checkedSize),
					DisplayFormatters.formatByteCountToKiBEtc(totalSize)
				});
			}

			if (soFileAreaInfo != null) {
				soFileAreaInfo.setText(text);
			}
			if (soExpandItemFiles != null) {
				String id = "OpenTorrentOptions.header.filesInfo."
						+ (numToDownload == dataFiles.length ? "all" : "some");
				soExpandItemFiles.setText(MessageText.getString(id, new String[] {
					String.valueOf(numToDownload),
					String.valueOf(dataFiles.length),
					text
				}));
			}

			diskFreeInfoRefreshPending = true;

			if ( changed ){

				changeListener.instanceChanged( this );
			}
		}

		protected long
		getSelectedDataSize()
		{
			return( currentSelectedDataSize );
		}

		private void updateStartModeCombo() {
			if (cmbStartMode == null)
				return;

			String[] sItemsText = new String[TorrentOpenOptions.STARTMODE_KEYS.length];
			for (int i = 0; i < sItemsText.length; i++) {
				String sText = MessageText.getString(TorrentOpenOptions.STARTMODE_KEYS[i]);
				sItemsText[i] = sText;
			}
			cmbStartMode.setItems(sItemsText);
			if ( isSingleOptions ){
				cmbStartMode.select(torrentOptions.getStartMode());
			}else{
				int startMode = -1;
				for ( TorrentOpenOptions to: torrentOptionsMulti ){
					int sm = to.getStartMode();
					if (startMode == -1 ){
						startMode = sm;
					}else if ( startMode != sm ){
						startMode = -2;
					}
				}
				if ( startMode >= 0 ){
					cmbStartMode.select(startMode);
				}else{
					cmbStartMode.deselectAll();
				}
			}
			
			cmbStartMode.layout(true);
		}

		public void updateUI() {
			if ( tvFiles != null ){
				tvFiles.refreshTable(false);
			}

			for ( FileStatsCacheItem item: fileStatCache.values()){
				
				item.update();
			}
			
			if (diskFreeInfoRefreshPending && !diskFreeInfoRefreshRunning
					&& FileUtil.getUsableSpaceSupported()) {
				diskFreeInfoRefreshRunning = true;
				diskFreeInfoRefreshPending = false;

				final HashSet FSroots = new HashSet(Arrays.asList(FileUtil.listRootsWithTimeout()));
				final HashMap partitions = new HashMap();

				for ( TorrentOpenOptions too: torrentOptionsMulti ){
					TorrentOpenFileOptions[] files = too.getFiles();
					for (int j = 0; j < files.length; j++) {
						TorrentOpenFileOptions file = files[j];
						if (!file.isToDownload())
							continue;

						// reduce each file to its partition root
						File root = file.getDestFileFullName().getAbsoluteFile();

						Partition part = (Partition) partitions.get(parentToRootCache.get(root.getParentFile()));

						if (part == null) {
							File next;
							while (true) {
								root = root.getParentFile();
								next = root.getParentFile();
								if (next == null)
									break;

								// bubble up until we hit an existing directory
								if (!getCachedExistsStat(root) || !root.isDirectory())
									continue;

								// check for mount points (different free space) or simple loops in the directory structure
								if (FSroots.contains(root) || root.equals(next)
										|| getCachedDirFreeSpace(next) != getCachedDirFreeSpace(root))
									break;
							}

							parentToRootCache.put(
									file.getDestFileFullName().getAbsoluteFile().getParentFile(),
									root);

							part = (Partition) partitions.get(root);

							if (part == null) {
								part = new Partition(root);
								
								partitions.put(root, part);
							}
						}

						part.bytesToConsume += file.lSize;
					}
				}

				// clear child objects
				if (diskspaceComp != null && !diskspaceComp.isDisposed()) {
					Control[] labels = diskspaceComp.getChildren();
					for (int i = 0; i < labels.length; i++)
						labels[i].dispose();

					// build labels
					Iterator it = partitions.values().iterator();
					while (it.hasNext()) {
						Partition part = (Partition) it.next();

						boolean filesTooBig = part.bytesToConsume > part.freeSpace.freeSpace;

						String s = MessageText.getString("v3.MainWindow.xofx",
								new String[] {
							DisplayFormatters.formatByteCountToKiBEtc(part.bytesToConsume),
							DisplayFormatters.formatByteCountToKiBEtc(part.freeSpace.freeSpace)
						});

						Label l;
						l = new Label(diskspaceComp, SWT.NONE);
						l.setForeground(filesTooBig ? Colors.colorError : null);
						l.setText(part.root.getPath());
						l.setLayoutData(new GridData(SWT.END, SWT.TOP, false, false));

						l = new Label(diskspaceComp, SWT.NONE);
						l.setForeground(filesTooBig ? Colors.colorError : null);
						l.setLayoutData(new GridData(SWT.END, SWT.TOP, false, false));
						l.setText(s);
					}

					// hack to force resize :(
					diskspaceComp.layout(true);
					soExpandItemSaveTo.relayout();
				}

				diskFreeInfoRefreshRunning = false;
			}
		}

		private void
		cancelPressed()
		{
			torrentOptions.cancel();
		}
		
		private boolean
		okPressed(
			String 		dataDirPassed,
			boolean		auto )
		{
			File filePassed = FileUtil.newFile( dataDirPassed );

			File fileDefSavePath = FileUtil.newFile(
					COConfigurationManager.getStringParameter(PARAM_DEFSAVEPATH));

			if (filePassed.equals(fileDefSavePath) && !fileDefSavePath.isDirectory()) {
				FileUtil.mkdirs(fileDefSavePath);
			}

			boolean isPathInvalid = dataDirPassed.length() == 0 || filePassed.isFile();
			if (!isPathInvalid && !filePassed.isDirectory()) {
				MessageBoxShell mb = new MessageBoxShell(SWT.YES | SWT.NO
						| SWT.ICON_QUESTION, "OpenTorrentWindow.mb.askCreateDir",
						new String[] {
							filePassed.toString()
						});
				mb.setParent(shell);
				mb.open(null);
				int doCreate = mb.waitUntilClosed();

				if (doCreate == SWT.YES)
					isPathInvalid = !FileUtil.mkdirs(filePassed);
				else {
					cmbDataDir.setFocus();
					return false;
				}
			}

			if (isPathInvalid) {
				MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.ICON_ERROR,
						"OpenTorrentWindow.mb.noGlobalDestDir", new String[] {
							filePassed.toString()
						});
				mb.setParent(shell);
				mb.open(null);
				cmbDataDir.setFocus();
				return false;
			}

			File torrentFile = FileUtil.newFile( torrentOptions.getTorrentFile());
			
			if ( !torrentFile.exists()){
				
					// some weird bug I'm working on that appears to be deleting the torrent file during the adding
					// process - try and recover
				
				TOTorrent torrent = torrentOptions.getTorrent();
				
				if ( torrent != null ){
					
					try{
						TorrentUtils.writeToFile( torrent, torrentFile, false );
						
						Debug.out( "Managed to re-save torrent file to '" + torrentFile.getAbsolutePath() + "'" );
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
				
				if ( !torrentFile.exists()){
					
					MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.ICON_ERROR,
							"OpenTorrentWindow.mb.noTorrentFile", new String[] {
								torrentFile.getAbsolutePath()
							});
					mb.setParent(shell);
					mb.open(null);
					return( false );
				}
			}
			
			String sExistingFiles = "";
			int iNumExistingFiles = 0;

			File torrentOptionsDataDir = FileUtil.newFile(torrentOptions.getDataDir());

			// Need to make directory now, or single file torrent will take the
			// "dest dir" as their filename.  ie:
			// 1) Add single file torrent with named "hi.exe"
			// 2) type a non-existant directory c:\test\moo
			// 3) unselect the torrent
			// 4) change the global def directory to a real one
			// 5) click ok.  "hi.exe" will be written as moo in c:\test

			if ( !torrentOptions.isSimpleTorrent()){
				torrentOptionsDataDir = torrentOptionsDataDir.getParentFile();	// for non-simple this points to the top folder in download
			}

			if (!torrentOptionsDataDir.isDirectory() && !FileUtil.mkdirs(torrentOptionsDataDir)) {
				MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.ICON_ERROR,
						"OpenTorrentWindow.mb.noDestDir", new String[] {
						torrentOptionsDataDir.toString(),
							torrentOptions.getTorrentName()
						});
				mb.setParent(shell);
				mb.open(null);
				return false;
			}

			if (!torrentOptions.isValid) {
				MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.ICON_ERROR,
						"OpenTorrentWindow.mb.notValid", new String[] {
							torrentOptions.getTorrentName()
						});
				mb.setParent(shell);
				mb.open(null);
				return false;
			}

			TorrentOpenFileOptions[] files = torrentOptions.getFiles();
			for (int j = 0; j < files.length; j++) {
				TorrentOpenFileOptions fileInfo = files[j];
				if (fileInfo.getDestFileFullName().exists()) {
					sExistingFiles += fileInfo.orgFullName + " - "
							+ torrentOptions.getTorrentName() + "\n";
					iNumExistingFiles++;
					if (iNumExistingFiles > 5) {
						// this has the potential effect of adding 5 files from the first
						// torrent and then 1 file from each of the remaining torrents
						break;
					}
				}
			}

			if (sExistingFiles.length() > 0) {
				if (iNumExistingFiles > 5) {
					sExistingFiles += MessageText.getString(
							"OpenTorrentWindow.mb.existingFiles.partialList", new String[] {
								"" + iNumExistingFiles
							}) + "\n";
				}

				MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.CANCEL
						| SWT.ICON_WARNING, "OpenTorrentWindow.mb.existingFiles",
						new String[] {
							sExistingFiles
						});
				mb.setParent(shell);
				mb.open(null);
				if (mb.waitUntilClosed() != SWT.OK) {
					return false;
				}
			}

				// update default save path(s)
			
			{
				String sDefaultPath = COConfigurationManager.getStringParameter(PARAM_DEFSAVEPATH);
	
				String newSavePath;
	
				if ( torrentOptions.isExplicitDataDir()){
	
						// multi-file torrent with a new sub-dir means we want the default save location
						// to be the parent, not the sub-dir
					
					newSavePath = torrentOptions.getDataDir();
	
					if ( torrentOptions.getSubDir() != null && !torrentOptions.isSimpleTorrent()){
						
							// except for in the case where the user explicitly removed the top level as in 
							// this case the user is trying to flatten the save space out and the target dir
							// wil be where they want subsequent things to go as well (this is explicit request
							// from a user and a regression due to the previous change here to save parent)
						
						if ( !torrentOptions.isRemovedTopLevel()){
						
							newSavePath = FileUtil.newFile( newSavePath ).getParent();
						}
					}
				}else{
	
					newSavePath = torrentOptions.getParentDir();
				}			
	
				int	 limit = COConfigurationManager.getIntParameter( "saveTo_list.max_entries" );

				if ( limit >= 0 ){

					List<String> oldDirList = COConfigurationManager.getStringListParameter("saveTo_list");
					
					newSavePath = FileUtil.newFile( newSavePath ).getAbsolutePath();
					
					LinkedList<String>	newDirList	= new LinkedList<>();
	
					newDirList.add( newSavePath );
					
					Set<String>	existing = new HashSet<>();
					
					existing.addAll( newDirList );
					
					for ( String entry: oldDirList ){
						
						if ( !existing.contains( entry )){
							
							existing.add( entry );
							
							newDirList.add( entry );
						}
					}
					
					if ( limit > 0 ){
						
						while( newDirList.size() > limit ){
							
							newDirList.removeLast();
						}
					}

					if ( !oldDirList.equals( newDirList )){
						
						COConfigurationManager.setParameter("saveTo_list", newDirList );
						
						COConfigurationManager.save();
					}
				}

				if (COConfigurationManager.getBooleanParameter("DefaultDir.AutoUpdate")){
					
					COConfigurationManager.setParameter( PARAM_DEFSAVEPATH, newSavePath );
				}
			}

			TagManager tagManager = TagManagerFactory.getTagManager();
			TagType tagType = tagManager.getTagType(TagType.TT_DOWNLOAD_MANUAL);
			List<Tag> initialTags = torrentOptions.getInitialTags();
			boolean initialTagsChanged = false;
			for (ListIterator<Tag> iter = initialTags.listIterator(); iter.hasNext(); ) {
				Tag tag = iter.next();
				if (tag instanceof DiscoveredTag) {
					initialTagsChanged = true;
					iter.remove();
					if (((DiscoveredTag) tag).existingTag == null) {
						try {
							Tag newTag = tagType.createTag(tag.getTagName(), true);
							newTag.setPublic(true);
							iter.add(newTag);
						} catch (TagException e) {
						}
					}
				}
			}
			
			if ( auto ){
				
				try{
					String autoTagName = MessageText.getString( "tag.auto.accepted" );
					
					Tag autoTag = tagType.getTag( autoTagName, true );
					
					if ( autoTag == null ){
						autoTag = tagType.createTag( autoTagName, true );
						autoTag.setPublic(false);
					}
					
					if ( !initialTags.contains( autoTag )){
						initialTags.add( autoTag );
						initialTagsChanged = true;
					}
				}catch( TagException e ){
				}
			}
			
			if (initialTagsChanged) {
				torrentOptions.setInitialTags(initialTags);
			}

			torrentOptions.setCancelDisabled( true );
			
			return true;
		}

		private void
		dispose()
		{
			tvFiles.delete();
			
			COConfigurationManager.removeParameterListeners(
					new String[]{
						"File.Torrent.AutoSkipExtensions",
						"File.Torrent.AutoSkipFiles",
						"File.Torrent.AutoSkipFiles.RegExp",
						"File.Torrent.AutoSkipMinSizeKB",
						
						"priorityExtensions",
						"priorityExtensionsIgnoreCase",
					},this );
		}
		
		private FileStatsCacheItem 
		getCachedDirFreeSpace(
			File directory) 
		{
			FileStatsCacheItem item = (FileStatsCacheItem) fileStatCache.get(directory);
			if (item == null){
				fileStatCache.put(directory, item = new FileStatsCacheItem(directory));
			}
			return item;
		}

		private boolean 
		getCachedExistsStat(
			File directory) 
		{
			FileStatsCacheItem item = (FileStatsCacheItem) fileStatCache.get(directory);
			if (item == null){
				fileStatCache.put(directory, item = new FileStatsCacheItem(directory));
			}
			return item.exists;
		}

		private class 
		FileStatsCacheItem
		{
			final File		file;
			final boolean 	exists;

			volatile long freeSpace;

			long		last_update = SystemTime.getMonotonousTime();
			boolean		updating;
			
			public 
			FileStatsCacheItem(
				File f) 
			{
				file = f;
				
				exists = file.exists();
				
				if ( exists ){
					
					freeSpace = FileUtil.getUsableSpace( file );
					
				}else{
					
					freeSpace = -1;
				}
			}
			
			void
			update()
			{
				long now = SystemTime.getMonotonousTime();;
				
				synchronized( this ){
					
					if ( updating || now - last_update < 3000 ){
						
						return;
					}
					
					updating = true;
				}
				
				spaceUpdateDispatcher.dispatch(()->{
					
					try{
						//long start = SystemTime.getMonotonousTime();
						
						long space = FileUtil.getUsableSpace(file);
						
						//System.out.println( "getFreeSpace(" + file + " ) - " + (SystemTime.getMonotonousTime() - start ));
						
						long min = 1024*1024L;
						
						if ( space > 1024*1024*1024L ){
							
							min *= 10;
						}
						
						if ( Math.abs( space - freeSpace ) > min ){
							
							freeSpace = space;
							
							diskFreeInfoRefreshPending = true;
						}
					}finally{
						
						synchronized( FileStatsCacheItem.this ){
						
							updating = false;
						
							last_update = SystemTime.getMonotonousTime();
						}
					}
				});
			}
		}

		private final class 
		Partition
		{
			final FileStatsCacheItem freeSpace;

			final File root;

			long bytesToConsume = 0;

			public 
			Partition(
				File root) 
			{
				this.root = root;
				
				freeSpace = getCachedDirFreeSpace(root);
			}
		}

	}

	public interface
	OpenTorrentInstanceListener
	{
		public void
		instanceChanged(
			OpenTorrentInstance		instance );
	}

	public static Tag getExistingTag(List<Tag> initialTags, String tagName) {
		for (Tag tag : initialTags) {
			if (tagName.equalsIgnoreCase(tag.getTagName(false))
					|| tagName.equalsIgnoreCase(tag.getTagName(true))) {
				return tag;
			}
		}
		return null;
	}

	private static Comparator tree_comp = new FormattersImpl().getAlphanumericComparator( true );

	private static class
	TreeNode
	{
		private static TreeNode[]	NO_KIDS = {};

		private final TreeNode		parent;
		private String				name;
		private Object				data = new TreeMap<String,TreeNode>( tree_comp );
		private long				size	= Long.MAX_VALUE;

		private
		TreeNode(
			TreeNode	_parent,
			String		_name )
		{
			parent		= _parent;
			name		= _name;
		}

		private String
		getName()
		{
			if ( data instanceof TorrentOpenFileOptions ){

					// pick up any rename that might have occurred

				return(((TorrentOpenFileOptions)data).getDestFileName());
			}

			return( name );
		}

		private TreeNode
		getParent()
		{
			return( parent );
		}

		private TreeNode
		getChild(
			String	name )
		{
			return(((TreeMap<String,TreeNode>)data).get(name));
		}

		private void
		addChild(
			TreeNode		child )
		{
			((TreeMap<String,TreeNode>)data).put( child.getName(), child );
		}

		private void
		setFile(
			TorrentOpenFileOptions file )
		{
			data = file;
		}

		private boolean
		isChecked()
		{
			if ( data instanceof TorrentOpenFileOptions ){

				return(((TorrentOpenFileOptions)data).isToDownload());
			}

			TreeNode[]	kids = getChildren();

			for ( TreeNode kid: kids ){

				if ( kid.isChecked()){

					return( true );
				}
			}

			return( false );
		}

		private boolean
		isGrayed()
		{
			if ( data instanceof TorrentOpenFileOptions ){

				return( false );
			}

			TreeNode[]	kids = getChildren();

			int	state = 0;

			for ( TreeNode kid: kids ){

				if ( kid.isGrayed()){

					return( true );
				}

				int kid_state = kid.isChecked()?1:2;

				if ( state == 0 ){

					state = kid_state;

				}else if ( state != kid_state ){

					return( true );
				}
			}

			return( false );
		}

		private TreeNode[]
		getChildren()
		{
			if ( data instanceof Map ){

				TreeMap<String,TreeNode> map = (TreeMap<String,TreeNode>)data;

				data = map.values().toArray( new TreeNode[map.size()]);
			}

			if ( data instanceof TreeNode[]){

				return( (TreeNode[])data );
			}else{

				return( NO_KIDS );
			}
		}

		private void
		sort(
			Comparator<TreeNode>		comparator )
		{
			TreeNode[]	kids =  getChildren();

			int	num_kids = kids.length;

			if ( num_kids >= 2 ){

				Arrays.sort( kids, comparator );
			}

			for ( int i=0;i<num_kids;i++){

				kids[i].sort( comparator );
			}
		}

		private TorrentOpenFileOptions
		getFile()
		{
			if ( data instanceof TorrentOpenFileOptions ){

				return((TorrentOpenFileOptions)data);

			}else{

				return( null );
			}
		}

		private List<TorrentOpenFileOptions>
		getFiles()
		{
			List<TorrentOpenFileOptions> files = new ArrayList<>( 1024 );

			getFiles( files );

			return( files );
		}

		private void
		getFiles(
			List<TorrentOpenFileOptions>		files )
		{
			if ( data instanceof TorrentOpenFileOptions ){

				files.add((TorrentOpenFileOptions)data);

			}else{

				TreeNode[] kids = getChildren();

				for ( TreeNode kid: kids ){

					kid.getFiles( files );
				}
			}
		}

		private long
		getSize()
		{
			if ( size != Long.MAX_VALUE ){

				return( size );
			}

			if ( data instanceof TorrentOpenFileOptions ){

				size = ((TorrentOpenFileOptions)data).lSize;

			}else{

				size = 0;

				TreeNode[] kids = getChildren();

				for ( TreeNode kid: kids ){

					size += Math.abs( kid.getSize());
				}

				size = -size;
			}

			return( size );
		}
	}

	private static class DiscoveredTag
		extends TagBase
	{
		private static final AtomicInteger tag_ids = new AtomicInteger();
		final private String name;
		final private String[] networks;
		private Tag existingTag;

		private DiscoveredTag(String _name, String[] _networks) {
			super(new TagTypeDiscovery(), tag_ids.incrementAndGet(), _name);
			name = _name;
			networks = _networks;
			setGroup(MessageText.getString("tag.discovery.view.heading"));
			setImageID("image.sidebar.rcm");

			TagManager tm =TagManagerFactory.getTagManager();
			
			TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );
			List<Tag> tags = tt.getTags();
			existingTag = getExistingTag(tags, _name);

			if ( networks != null && networks.length > 0 ){
				boolean boring = false;
				String nets_str = "";

				for ( String net: networks ){
					if ( net == AENetworkClassifier.AT_PUBLIC ){
						boring = true;
						break;
					}
					nets_str += (nets_str.length()==0?"":"/") + net;
				}

				if ( !boring && nets_str.length() > 0 ){

					setDescription("[" + nets_str + "]");
				}
			}
			
			if ( !TagUtils.isInternalTagName( name )){
				TagType tt_swarm = tm.getTagType( TagType.TT_SWARM_TAG );
				Tag st = tt_swarm.getTag( name, true );
				if ( st == null ){
					try{
						tt_swarm.createTag( name, true );
					}catch( Throwable e ){
						Debug.out( e );
					}
				}
			}
		}

		@Override
		public int getTaggableTypes() {
			return 0;
		}

		@Override
		public int getTaggedCount() {
			return 0;
		}

		@Override
		public Set<Taggable> getTagged() {
			return null;
		}

		@Override
		public boolean hasTaggable(Taggable t) {
			return false;
		}

		private static class TagTypeDiscovery
			extends TagTypeBase
		{
			private final int[] color_default = {
				0,
				80,
				80
			};

			public TagTypeDiscovery() {
				super(TagType.TT_TAG_SUGGESTION, 0,
						MessageText.getString("tagtype.discovered"));
			}

			@Override
			public List<Tag> getTags() {
				return new ArrayList<>();
			}

			@Override
			public int getTagCount(){
				return 0;
			}
			
			@Override
			public int[] getColorDefault() {
				return color_default;
			}
		}
	}
}
