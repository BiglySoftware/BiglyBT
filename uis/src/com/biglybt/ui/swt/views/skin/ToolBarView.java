/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.skin;

import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.ISelectedVuzeFileContent;
import com.biglybt.ui.selectedcontent.SelectedContentListener;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.skin.SWTSkinObjectText;
import com.biglybt.ui.swt.utils.SWTRunnable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Control;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AERunnableBoolean;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FrequencyLimitedDispatcher;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.toolbar.*;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.pifimpl.UIToolBarItemImpl;
import com.biglybt.ui.swt.pifimpl.UIToolBarManagerCore;
import com.biglybt.ui.swt.pifimpl.UIToolBarManagerImpl;
import com.biglybt.ui.swt.pifimpl.UIToolBarManagerImpl.ToolBarManagerListener;

import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.biglybt.ui.swt.toolbar.ToolBarItemSO;
import com.biglybt.util.DLReferals;
import com.biglybt.util.PlayUtils;
import org.eclipse.swt.widgets.Display;

/**
 * @author TuxPaper
 * @created Jul 20, 2008
 */
public class ToolBarView
	extends SkinView
	implements SelectedContentListener, ToolBarManagerListener,
        ToolBarItem.ToolBarItemListener, ParameterListener
{
	private static boolean DEBUG = false;

	private static toolbarButtonListener buttonListener;
	
	private Map<UIToolBarItem, ToolBarItemSO> itemMap = new HashMap<>();

	private boolean showText = true;

	private boolean initComplete = false;
	private boolean rebuilding;
	private boolean rebuild_pending;
	
	private boolean showCalled = false;

	private ArrayList<ToolBarViewListener> listeners = new ArrayList<>(
			1);

	private UIToolBarManagerCore tbm;

	private boolean firstTimeEver = true;

	private Set<String>	visible_items = new HashSet<>();
	
	public ToolBarView() {
		tbm = (UIToolBarManagerCore) UIToolBarManagerImpl.getInstance();
	}

	private ToolBarItem createItem(ToolBarView tbv, String id, String imageid,
			String textID) {
		UIToolBarItemImpl base = new UIToolBarItemImpl(id);
		base.setImageID(imageid);
		base.setTextID(textID);
		return base;
	}

	@Override
	public Object 
	skinObjectInitialShow(SWTSkinObject skinObject, Object params) {
			
		boolean uiClassic = COConfigurationManager.getStringParameter("ui").equals( "az2");

		if (uiClassic && !"global-toolbar".equals(skinObject.getViewID())) {
			skinObject.setVisible(false);
			return null;
		}

			// walk up skins to see if toolbar explicitly disabled (for pop-out views for example)

		SWTSkinObject temp = skinObject;

		while( temp != null ){

			int visible = temp.getSkin().getSkinProperties().getIntValue( "mdientry.toolbar.visible", 1 );

			if ( visible == 0 ){

				skinObject.setVisible(false);

				return null;
			}

			temp = temp.getParent();
		}

		buttonListener = new toolbarButtonListener();


		if ( firstTimeEver ){
			
			firstTimeEver = false;
			
			if ( !uiClassic ){
				
				COConfigurationManager.addParameterListener( "IconBar.enabled", this );
			}
			
			setupToolBarItems(uiClassic);
			
			TorrentUtil.init();
		}
		
		tbm.addListener(this);

		rebuild();
		
		return( null );
	}
	
	private void
	build(
		Map<UIToolBarItem, ToolBarItemSO>	newMap )
	{
		soLastGroup = null;
		
		boolean uiClassic = COConfigurationManager.getStringParameter("ui").equals( "az2");
		
		UIToolBarItem[] items = tbm.getAllToolBarItems();
		
		removeItemListeners();
		
		visible_items.clear();
		
		COConfigurationManager.addParameterListener( "IconBar.start.stop.separate", this );
		
		boolean start_top_sep = COConfigurationManager.getBooleanParameter( "IconBar.start.stop.separate", false );
		
		for ( UIToolBarItem item: items ){
			
			String id = item.getID();
		
			String key = "IconBar.visible." + id;
			
			COConfigurationManager.addParameterListener( key , this );
			
			if ( COConfigurationManager.getBooleanParameter( key, true )){
			
				if ( start_top_sep && ( id.equals( "startstop" ))){
					
					continue;
				}
				
				if ( !start_top_sep && ( id.equals( "start" ) || id.equals( "stop" ))){
					
					continue;
				}
				
				visible_items.add( id );
			}
		}
		
		if ( uiClassic || !COConfigurationManager.getBooleanParameter( "IconBar.enabled" )) {
			
			bulkSetupItems( newMap, "classic", "toolbar.area.sitem");
		}
		
		bulkSetupItems( newMap, UIToolBarManager.GROUP_MAIN, "toolbar.area.sitem");
		
		bulkSetupItems( newMap, "views", "toolbar.area.vitem");

		String[] groupIDs = tbm.getGroupIDs();
		
		for (String groupID : groupIDs) {
			
			if (	"classic".equals(groupID) ||
					UIToolBarManager.GROUP_MAIN.equals(groupID) || 
					"views".equals(groupID)){
				
				continue;
			}
			
			bulkSetupItems( newMap, groupID, "toolbar.area.sitem");
		}

		initComplete = true;

		synchronized (listeners) {
			for (ToolBarViewListener l : listeners) {
				try {
					l.toolbarViewInitialized(this);
				} catch (Exception e) {
					Debug.out(e);
				}
			}
		}
	}

	private void
	rebuild()
	{
		synchronized( itemMap ){
			
			if ( rebuilding ){
				
				rebuild_pending = true;
				
				return;
			}
			
			rebuilding = true;
		}
		
		Utils.execSWTThread(
			()->{
				
					// tear down
				
				synchronized( itemMap ){
					
					Set<String>	groups = new HashSet<>();
					
					for ( ToolBarItemSO so: itemMap.values()){
						
						groups.add( so.getBase().getGroupID());
					}

					itemMap.clear();

					for ( String group: groups ){
						
						SWTSkinObjectContainer groupSO = peekGroupSO(group);
						
						if ( groupSO != null ){
							
							SWTSkinObject[] children = groupSO.getChildren();
							
							for (SWTSkinObject so : children) {
								
								so.dispose();
							}
							
							groupSO.dispose();
						}
					}
				}
				
				Map<UIToolBarItem, ToolBarItemSO>	newMap = new HashMap<>();
				
				try{
					skin.constructionStart();
					
					build( newMap );
					
				}finally{
					
					skin.constructionEnd();
				}
				
				Utils.relayout( soMain.getControl());
				
					// record built state
				
				synchronized( itemMap ){
					
					itemMap.putAll( newMap );
					
					rebuilding = false;

					if ( rebuild_pending ){
						
						rebuild_pending = false;
						
						Utils.getOffOfSWTThread(
							()->{
								rebuild();
							});
					}
				}
			});
	}
	
	private void setupToolBarItems(boolean uiClassic) {
		ToolBarItem item;

		{	// always add these items, whether they are shown or not is decided later
			
			// ==OPEN
			item = createItem(this, "open", "image.toolbar.open", "Button.add.torrent");
			item.setDefaultActivationListener(new UIToolBarActivationListener() {
				@Override
				public boolean toolBarItemActivated(ToolBarItem item,
				                                    long activationType, Object datasource) {
					if (activationType != ACTIVATIONTYPE_NORMAL) {
						
						Boolean result = Utils.execSWTThreadWithBool(
							"open",
							new AERunnableBoolean(){
								
								@Override
								public boolean runSupport(){
									Clipboard clipboard = new Clipboard(Display.getDefault());
									
									try{
										String text = (String) clipboard.getContents(TextTransfer.getInstance());
				
										if (text != null && text.length() <= 2048) {
											
											if ( TorrentOpener.openTorrentsFromClipboard(text)){
											
												return( true );
											}
										}
									}finally{
										
										clipboard.dispose();
									}
									
									return false;
								}
							}, 1000 );
						
						return( result != null && result );
					}
					
					UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentWindow();
					
					return true;
				}
			});
			item.setAlwaysAvailable(true);
			item.setGroupID("classic");
			tbm.addToolBarItem(item, false);

			// ==SEARCH
			item = createItem(this, "search", "search", "Button.search");
			item.setDefaultActivationListener(new UIToolBarActivationListener() {
				@Override
				public boolean toolBarItemActivated(ToolBarItem item,
				                                    long activationType, Object datasource) {
					if (activationType != ACTIVATIONTYPE_NORMAL) {
						return false;
					}
					UIFunctionsManagerSWT.getUIFunctionsSWT().promptForSearch();
					return true;
				}
			});
			item.setAlwaysAvailable(true);
			item.setGroupID("classic");
			tbm.addToolBarItem(item, false);
		}

		if (!uiClassic) {
			// ==play
			item = createItem(this, "play", "image.button.play", "iconBar.play");
			item.setDefaultActivationListener(new UIToolBarActivationListener() {
				@Override
				public boolean toolBarItemActivated(ToolBarItem item,
				                                    long activationType, Object datasource) {
					if (activationType != ACTIVATIONTYPE_NORMAL) {
						return false;
					}
					ISelectedContent[] sc = SelectedContentManager.getCurrentlySelectedContent();
					if (sc != null && sc.length > 0) {

						if (PlayUtils.canStreamDS(sc[0], sc[0].getFileIndex(),true)) {
							TorrentListViewsUtils.playOrStreamDataSource(sc[0],
									DLReferals.DL_REFERAL_TOOLBAR, true, false);
						} else {
							TorrentListViewsUtils.playOrStreamDataSource(sc[0],
									DLReferals.DL_REFERAL_TOOLBAR, false, true);
						}
					}
					return false;
				}
			});
			tbm.addToolBarItem(item, false);
			
			
		}

		// ==run
		item = createItem(this, "run", "image.toolbar.run", "iconBar.run");
		item.setDefaultActivationListener(new UIToolBarActivationListener() {
			@Override
			public boolean toolBarItemActivated(ToolBarItem item,
			                                    long activationType, Object datasource) {
				if (activationType != ACTIVATIONTYPE_NORMAL) {
					return false;
				}
				TableView tv = SelectedContentManager.getCurrentlySelectedTableView();
				Object[] ds;
				if (tv != null) {
					ds = tv.getSelectedDataSources().toArray();
				} else {
					ds = SelectedContentManager.getDMSFromSelectedContent();
				}
				if (ds != null) {
					TorrentUtil.runDataSources(ds);
					return true;
				}
				return false;
			}
		});
		tbm.addToolBarItem(item, false);
		//addToolBarItem(item, "toolbar.area.sitem", so2nd);

		if (uiClassic) {
			// ==TOP
			item = createItem(this, "top", "image.toolbar.top", "iconBar.top");
			item.setDefaultActivationListener(new UIToolBarActivationListener() {
				@Override
				public boolean toolBarItemActivated(ToolBarItem item,
				                                    long activationType, Object datasource) {
					if (activationType == ACTIVATIONTYPE_NORMAL) {
						return moveTop();
					}

					return false;
				}
			});
			tbm.addToolBarItem(item, false);
		}

		// ==UP
		item = createItem(this, "up", "image.toolbar.up", "iconBar.up");
		item.setDefaultActivationListener(new UIToolBarActivationListener() {
			@Override
			public boolean toolBarItemActivated(ToolBarItem item,
			                                    long activationType, Object datasource) {
				if (activationType == ACTIVATIONTYPE_NORMAL) {
					if (!CoreFactory.isCoreRunning()) {
						return false;
					}
					DownloadManager[] dms = SelectedContentManager.getDMSFromSelectedContent();
					if (dms != null) {
						Arrays.sort(dms, new Comparator<DownloadManager>() {
							@Override
							public int compare(DownloadManager a, DownloadManager b) {
								return a.getPosition() - b.getPosition();
							}
						});
						GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
						for (int i = 0; i < dms.length; i++) {
							DownloadManager dm = dms[i];
							if (gm.isMoveableUp(dm)) {
								gm.moveUp(dm);
							}
						}
					}
				} else if (activationType == ACTIVATIONTYPE_HELD) {
					return moveTop();
				}
				return false;
			}
		});
		tbm.addToolBarItem(item, false);

		// ==down
		item = createItem(this, "down", "image.toolbar.down", "iconBar.down");
		item.setDefaultActivationListener(new UIToolBarActivationListener() {
			@Override
			public boolean toolBarItemActivated(ToolBarItem item,
			                                    long activationType, Object datasource) {
				if (activationType == ACTIVATIONTYPE_NORMAL) {
					if (!CoreFactory.isCoreRunning()) {
						return false;
					}

					GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
					DownloadManager[] dms = SelectedContentManager.getDMSFromSelectedContent();
					if (dms != null) {
						Arrays.sort(dms, new Comparator<DownloadManager>() {
							@Override
							public int compare(DownloadManager a, DownloadManager b) {
								return b.getPosition() - a.getPosition();
							}
						});
						for (int i = 0; i < dms.length; i++) {
							DownloadManager dm = dms[i];
							if (gm.isMoveableDown(dm)) {
								gm.moveDown(dm);
							}
						}
						return true;
					}
				} else if (activationType == ACTIVATIONTYPE_HELD) {
					return moveBottom();
				}
				return false;
			}
		});
		tbm.addToolBarItem(item, false);

		if (uiClassic) {
			// ==BOTTOM
			item = createItem(this, "bottom", "image.toolbar.bottom",
					"iconBar.bottom");
			item.setDefaultActivationListener(new UIToolBarActivationListener() {
				@Override
				public boolean toolBarItemActivated(ToolBarItem item,
				                                    long activationType, Object datasource) {
					if (activationType != ACTIVATIONTYPE_NORMAL) {
						return false;
					}
					return moveBottom();
				}
			});
			tbm.addToolBarItem(item, false);
		}
	
		// ==start
		item = createItem(this, "start", "image.toolbar.startstop.start", "iconBar.start");
		item.setDefaultActivationListener(new UIToolBarActivationListener_OffSWT() {
			@Override
			public void toolBarItemActivated_OffSWT(ToolBarItem item,
					long activationType, Object datasource) {
				ISelectedContent[] selected = SelectedContentManager.getCurrentlySelectedContent();
				TorrentUtil.queueDataSources(selected,false,activationType == ACTIVATIONTYPE_HELD);
			}
		});
		
		tbm.addToolBarItem(item, false);

		// ==stop
		item = createItem(this, "stop", "image.toolbar.startstop.stop", "iconBar.stop");
		item.setDefaultActivationListener(new UIToolBarActivationListener_OffSWT(){
			@Override
			public void toolBarItemActivated_OffSWT(ToolBarItem item,
					long activationType, Object datasource) {
				ISelectedContent[] selected = SelectedContentManager.getCurrentlySelectedContent();
				TorrentUtil.stopDataSources(selected,activationType == ACTIVATIONTYPE_HELD);
			}
		});
		
		tbm.addToolBarItem(item, false);
		
		// ==startstop
		item = createItem(this, "startstop", "image.toolbar.startstop.start",
				"iconBar.startstop");
		item.setDefaultActivationListener(new UIToolBarActivationListener_OffSWT(){
			@Override
			public void toolBarItemActivated_OffSWT(ToolBarItem item,
					long activationType, Object datasource) {
				ISelectedContent[] selected = SelectedContentManager.getCurrentlySelectedContent();
				TorrentUtil.stopOrStartDataSources(selected,activationType == ACTIVATIONTYPE_HELD);
			}
		});
		tbm.addToolBarItem(item, false);

		// ==remove
		item = createItem(this, "remove", "image.toolbar.remove", "iconBar.remove");
		item.setDefaultActivationListener(new UIToolBarActivationListener_OffSWT(
				UIToolBarActivationListener.ACTIVATIONTYPE_NORMAL) {
			@Override
			public void toolBarItemActivated_OffSWT(ToolBarItem item,
					long activationType, Object datasource) {
				ISelectedContent[] selected = SelectedContentManager.getCurrentlySelectedContent();
				TorrentUtil.removeDataSources(selected);
			}
		});
		tbm.addToolBarItem(item, false);

		///////////////////////

		if ( COConfigurationManager.getBooleanParameter( "Library.EnableSimpleView" )){

			// == mode big
			item = createItem(this, "modeBig", "image.toolbar.table_large",
					"v3.iconBar.view.big");
			item.setGroupID("views");
			tbm.addToolBarItem(item, false);

			// == mode small
			item = createItem(this, "modeSmall", "image.toolbar.table_normal",
					"v3.iconBar.view.small");
			item.setGroupID("views");
			tbm.addToolBarItem(item, false);
		}
	}

	@Override
	public void parameterChanged(String parameterName){
		rebuild();
	}
	
	@Override
	public void 
	currentlySelectedContentChanged(
			ISelectedContent[] currentContent, String viewID) {
		//System.err.println("currentlySelectedContentChanged " + viewID + ";" + currentContent + ";" + getMainSkinObject() + this + " via " + Debug.getCompressedStackTrace());
		refreshCoreToolBarItems();
		UIFunctionsSWT uiFunctionsSWT = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctionsSWT != null) {
			uiFunctionsSWT.refreshTorrentMenu();
		}
	}

	@Override
	public Object 
	skinObjectShown(SWTSkinObject skinObject, Object params) {

		if (showCalled) {
			return null;
		}
				
		showCalled = true;

		Object object = super.skinObjectShown(skinObject, params);

		addActiveListeners();
		
		return object;
	}

	@Override
	public Object 
	skinObjectHidden(SWTSkinObject skinObject, Object params) {
		showCalled = false;

		removeActiveListeners();

		return super.skinObjectHidden(skinObject, params);
	}

	private void
	addActiveListeners()
	{
		ToolBarItem[] allToolBarItems = tbm.getAllSWTToolBarItems();
		for (int i = 0; i < allToolBarItems.length; i++) {
			ToolBarItem toolBarItem = allToolBarItems[i];
			toolBarItem.addToolBarItemListener(this);
			uiFieldChanged(toolBarItem);
		}

		SelectedContentManager.addCurrentlySelectedContentListener(this);
	}
	
	private void
	removeActiveListeners()
	{
		SelectedContentManager.removeCurrentlySelectedContentListener(this);

		ToolBarItem[] allToolBarItems = tbm.getAllSWTToolBarItems();
		for (int i = 0; i < allToolBarItems.length; i++) {
			ToolBarItem toolBarItem = allToolBarItems[i];
			toolBarItem.removeToolBarItemListener(this);
		}
	}
	
	private void
	removeItemListeners()
	{
		for ( String id: visible_items ){
			COConfigurationManager.removeParameterListener( "IconBar.visible." + id , this );
		}
		
		COConfigurationManager.removeParameterListener( "IconBar.start.stop.separate", this );
	}
	
	@Override
	public Object 
	skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
				
		tbm.removeListener(this);

		removeItemListeners();
		
		removeActiveListeners();
		
		COConfigurationManager.removeParameterListener( "IconBar.enabled", this );
		
		return super.skinObjectDestroyed(skinObject, params);
	}

	@Override
	public boolean triggerToolBarItem(ToolBarItem item, long activationType,
	                                  Object datasource) {
		if (!isVisible()) {
			if (DEBUG) {
				Debug.out("Trying to triggerToolBarItem when toolbar is not visible");
			}
			return false;
		}
		if (triggerViewToolBar(item, activationType, datasource)) {
			return true;
		}

		UIToolBarActivationListener defaultActivation = item.getDefaultActivationListener();
		if (defaultActivation != null) {
			return defaultActivation.toolBarItemActivated(item, activationType,
					datasource);
		}

		if (DEBUG) {
			String viewID = SelectedContentManager.getCurrentySelectedViewID();
			System.out.println("Warning: Fallback of toolbar button " + item.getID()
					+ " via " + viewID + " view");
		}

		return false;
	}

	protected boolean moveBottom() {
		if (!CoreFactory.isCoreRunning()) {
			return false;
		}

		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
		DownloadManager[] dms = SelectedContentManager.getDMSFromSelectedContent();
		if (dms != null) {
			gm.moveEnd(dms);
		}
		return true;
	}

	protected boolean moveTop() {
		if (!CoreFactory.isCoreRunning()) {
			return false;
		}
		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
		DownloadManager[] dms = SelectedContentManager.getDMSFromSelectedContent();
		if (dms != null) {
			gm.moveTop(dms);
		}
		return true;
	}

	private FrequencyLimitedDispatcher refresh_limiter = new FrequencyLimitedDispatcher(
			new AERunnable() {
				private AERunnable lock = this;

				private boolean refresh_pending;

				@Override
				public void runSupport() {
					synchronized (lock) {

						if (refresh_pending) {

							return;
						}
						refresh_pending = true;
					}

					if (DEBUG) {
						System.out.println("refreshCoreItems via "
								+ Debug.getCompressedStackTrace());
					}

					Utils.execSWTThread(new SWTRunnable() {
						@Override
						public void runNoDisplay() {
							synchronized (lock) {

								refresh_pending = false;
							}
						}

						@Override
						public void runWithDisplay(Display display) {

							synchronized (lock) {

								refresh_pending = false;
							}

							_refreshCoreToolBarItems();
						}
					});
				}
			}, 250);

	private IdentityHashMap<DownloadManager, DownloadManagerListener> dm_listener_map = new IdentityHashMap<>();

	private SWTSkinObject soLastGroup;

	public void refreshCoreToolBarItems() {
		if (DEBUG) {
			System.out.println("refreshCoreItems Start via "
					+ Debug.getCompressedStackTrace());
		}
		refresh_limiter.dispatch();
	}

	public void _refreshCoreToolBarItems() {
		if (DEBUG && !isVisible()) {
			Debug.out("Trying to refresh core toolbar items when toolbar is not visible "
					+ this + getMainSkinObject());
		}

		UIFunctionsSWT uiFunctionsSWT = UIFunctionsManagerSWT.getUIFunctionsSWT();
		MultipleDocumentInterfaceSWT mdi = uiFunctionsSWT != null ? uiFunctionsSWT.getMDISWT() : null;

		if (mdi != null) {
			UIToolBarItem[] allToolBarItems = tbm.getAllToolBarItems();
			MdiEntrySWT entry = mdi.getCurrentEntry();
			Map<String, Long> mapStates = new HashMap<>();
			if (entry != null) {
				UIToolBarEnablerBase[] enablers = entry.getToolbarEnablers();
				for (UIToolBarEnablerBase enabler : enablers) {
					if (enabler instanceof UIPluginViewToolBarListener) {
						try {
							((UIPluginViewToolBarListener) enabler).refreshToolBarItems(mapStates);
						} catch (Throwable e) {
							Debug.out(e); // don't trust them plugins
						}
					}
				}
			}

			ISelectedContent[] currentContent = SelectedContentManager.getCurrentlySelectedContent();
			//System.out.println("_refreshCoreToolBarItems(" + currentContent.length + ", " + entry + " via " + Debug.getCompressedStackTrace());

			boolean allFiles = currentContent.length > 0;
			
			synchronized (dm_listener_map) {

				Map<DownloadManager, DownloadManagerListener> copy = new IdentityHashMap<>(
						dm_listener_map);

				for (ISelectedContent content : currentContent) {

					DownloadManager dm = content.getDownloadManager();

					if (dm != null) {

						if ( content.getFileIndex() == -1 ){
							allFiles = false;
						}
						
						copy.remove(dm);

						// so in files view we can have multiple selections that map onto the SAME download manager
						// - ensure that we only add the listener once!

						if (!dm_listener_map.containsKey(dm)) {

							DownloadManagerListener l = new DownloadManagerListener() {
								@Override
								public void stateChanged(DownloadManager manager, int state) {
									refreshCoreToolBarItems();
								}

								@Override
								public void downloadComplete(DownloadManager manager) {
									refreshCoreToolBarItems();
								}

								@Override
								public void completionChanged(DownloadManager manager,
								                              boolean bCompleted) {
									refreshCoreToolBarItems();
								}

								@Override
								public void positionChanged(DownloadManager download,
								                            int oldPosition, int newPosition) {
									refreshCoreToolBarItems();
								}

								@Override
								public void filePriorityChanged(DownloadManager download,
								                                DiskManagerFileInfo file) {
									refreshCoreToolBarItems();
								}
							};

							dm.addListener(l, false);

							dm_listener_map.put(dm, l);

							//System.out.println( "Added " + dm.getDisplayName() + " - size=" + dm_listener_map.size());
						}
					}
				}

				for (Map.Entry<DownloadManager, DownloadManagerListener> e : copy.entrySet()) {

					DownloadManager dm = e.getKey();

					dm.removeListener(e.getValue());

					dm_listener_map.remove(dm);

					//System.out.println( "Removed " + dm.getDisplayName() + " - size=" + dm_listener_map.size());
				}
			}

			boolean has1Selection = currentContent.length == 1;

			boolean can_play = false;
			boolean can_stream = false;

			boolean stream_permitted = false;

			if (has1Selection) {

				if (!(currentContent[0] instanceof ISelectedVuzeFileContent)) {

					can_play = PlayUtils.canPlayDS(currentContent[0],
							currentContent[0].getFileIndex(),false);
					can_stream = PlayUtils.canStreamDS(currentContent[0],
							currentContent[0].getFileIndex(),false);

					if (can_stream) {

						stream_permitted = PlayUtils.isStreamPermitted();
					}
				}
			}

			// allow a tool-bar enabler to manually handle play/stream events

			if (mapStates.containsKey("play")) {
				can_play |= (mapStates.get("play") & UIToolBarItem.STATE_ENABLED) > 0;
			}
			if (mapStates.containsKey("stream")) {
				can_stream |= (mapStates.get("stream") & UIToolBarItem.STATE_ENABLED) > 0;
			}

			mapStates.put("play", can_play | can_stream ? UIToolBarItem.STATE_ENABLED
					: 0);

			UIToolBarItem pitem = tbm.getToolBarItem("play");

			if (pitem != null) {

				if (can_stream) {

					pitem.setImageID(stream_permitted ? "image.button.stream"
							: "image.button.pstream");
					pitem.setTextID(stream_permitted ? "iconBar.stream"
							: "iconBar.pstream");

				} else {

					pitem.setImageID("image.button.play");
					pitem.setTextID("iconBar.play");
				}
			}

			UIToolBarItem startItem = tbm.getToolBarItem("start");
			
			if ( startItem != null ){
				startItem.setTextID( allFiles? "iconBar.startFiles" : "iconBar.start" );
			}
			
			UIToolBarItem stopItem = tbm.getToolBarItem("stop");
			
			if ( stopItem != null ){
				stopItem.setTextID( allFiles? "iconBar.stopFiles" : "iconBar.stop" );
			}
		
			UIToolBarItem ssItem = tbm.getToolBarItem("startstop");
			if (ssItem != null){

				boolean shouldStopGroup = false;

					// if no selected content set then use the 'start' key to determine the start/stop
					// toolbar state (required for archived downloads)
					// alternative solution would be for the view to start updating the current selected
					// content which is a little painful

				boolean use_other_states = false;
				
				if ( currentContent.length == 0 ){
					
					use_other_states = true;

				}else{
					
					Boolean test = TorrentUtil.shouldStopGroupTest(currentContent);
					
					if ( test == null ){
						
						// no dms or files in the selected content so revert to using the existence of other
						// keys
						
						use_other_states = true;
						
					}else{
						
						shouldStopGroup = test;
					}
				}
				
				if ( use_other_states ){
					
					if ( 	( mapStates.containsKey( "start" ) && ( mapStates.get("start") & UIToolBarItem.STATE_ENABLED) > 0 ) &&
							(!mapStates.containsKey( "stop" ) || (mapStates.get("stop") & UIToolBarItem.STATE_ENABLED) == 0 )){
					
						shouldStopGroup = false;
						
					}else{
						
						shouldStopGroup = true;
					}
				}

				if ( allFiles ){
					ssItem.setTextID(shouldStopGroup ? "iconBar.stopFiles" : "iconBar.startFiles");

				}else{
					ssItem.setTextID(shouldStopGroup ? "iconBar.stop" : "iconBar.start");
				}
				
				ssItem.setImageID("image.toolbar.startstop." + (shouldStopGroup ? "stop" : "start"));

				// fallback to handle start/stop settings when no explicit selected content (e.g. for devices transcode view)

				if (currentContent.length == 0 && !mapStates.containsKey("startstop")) {

					boolean can_stop = mapStates.containsKey("stop")
							&& (mapStates.get("stop") & UIToolBarItem.STATE_ENABLED) > 0;
					boolean can_start = mapStates.containsKey("start")
							&& (mapStates.get("start") & UIToolBarItem.STATE_ENABLED) > 0;

					if (can_start && can_stop) {

						can_stop = false;
					}

					if (can_start || can_stop) {
						ssItem.setTextID(can_stop ? "iconBar.stop" : "iconBar.start");
						ssItem.setImageID("image.toolbar.startstop."
								+ (can_stop ? "stop" : "start"));

						mapStates.put("startstop", UIToolBarItem.STATE_ENABLED);
					}
				}
			}

			Map<String, Long> fallBackStates = TorrentUtil.calculateToolbarStates(currentContent, null);
			for (String key : fallBackStates.keySet()) {
				if (!mapStates.containsKey(key)) {
					mapStates.put(key, fallBackStates.get(key));
				}
			}

			final String[] TBKEYS = new String[] {
				"play",
				"run",
				"top",
				"up",
				"down",
				"bottom",
				"start",
				"stop",
				"startstop",
				"remove"
			};
			for (String key : TBKEYS) {
				if (!mapStates.containsKey(key)) {
					mapStates.put(key, 0L);
				}
			}

			for (int i = 0; i < allToolBarItems.length; i++) {
				UIToolBarItem toolBarItem = allToolBarItems[i];
				Long state = mapStates.get(toolBarItem.getID());
				if (state != null) {
					toolBarItem.setState(state);
				}
			}
		}

	}

	private boolean triggerViewToolBar(ToolBarItem item, long activationType,
			Object datasource) {
		if (DEBUG && !isVisible()) {
			Debug.out("Trying to triggerViewToolBar when toolbar is not visible");
			return false;
		}
		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();
		if (mdi != null) {
			MdiEntrySWT entry = mdi.getCurrentEntry();
			if ( entry != null ){
				UIToolBarEnablerBase[] enablers = entry.getToolbarEnablers();
				for (UIToolBarEnablerBase enabler : enablers) {
					if (enabler instanceof UIPluginViewToolBarListener) {
						if (((UIPluginViewToolBarListener) enabler).toolBarItemActivated(
								item, activationType, datasource)) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	private void bulkSetupItems(Map<UIToolBarItem, ToolBarItemSO> newMap, String groupID, String templatePrefix) {
		String[] idsByGroupAll = tbm.getToolBarIDsByGroup(groupID);
		SWTSkinObjectContainer groupSO = peekGroupSO(groupID);
		if ( groupSO != null ){
			SWTSkinObject[] children = groupSO.getChildren();
			for (SWTSkinObject so : children) {
				so.dispose();
			}
		}

		List<String> idsByGroup = new ArrayList<>();
		
		for ( String id: idsByGroupAll ){
			
			if ( visible_items.contains( id )){
				
				idsByGroup.add( id );
			}
		}
		
		int size = idsByGroup.size();

		if ( size > 0 ){
			
				// only peeked above, create now as group is required
			
			groupSO = getGroupSO(groupID);
			
			for (int i = 0; i < size; i++) {
				String itemID = idsByGroup.get(i);
				UIToolBarItem item = tbm.getToolBarItem(itemID);
				if (item instanceof ToolBarItem) {
	
	
					int position = 0;
					if (size == 1) {
						position = SWT.SINGLE;
					} else if (i == 0) {
						position = SWT.LEFT;
					} else if (i == size - 1) {
						addSeperator(groupID);
						position = SWT.RIGHT;
					} else {
						addSeperator(groupID);
					}
					createItemSO(newMap,(ToolBarItem) item, templatePrefix, position);
				}
			}
	
			addNonToolBar("toolbar.area.sitem.left2", groupID);
		}
	}

	private Control getLastControl(String groupID) {
		SWTSkinObjectContainer groupSO = getGroupSO(groupID);
		SWTSkinObject[] children = groupSO.getChildren();
		if (children == null || children.length == 0) {
			return null;
		}
		return children[children.length - 1].getControl();
	}

	private void createItemSO(Map<UIToolBarItem, ToolBarItemSO> newMap, ToolBarItem item, String templatePrefix,
			 int position) {

		ToolBarItemSO existingItemSO = newMap.get(item);
		
		if (existingItemSO != null) {
			SWTSkinObject so = existingItemSO.getSO();
			if (so != null) {
				so.dispose();
			}
		}

		String templateID = templatePrefix;
		if (position == SWT.RIGHT) {
			templateID += ".right";
		} else if (position == SWT.LEFT) {
			templateID += ".left";
		} else if (position == SWT.SINGLE) {
			templateID += ".lr";
		}

		Control attachToControl = getLastControl(item.getGroupID());
		String id = "toolbar:" + item.getID();
		SWTSkinObject so = skin.createSkinObject(id, templateID, getGroupSO(item.getGroupID()));
		if (so != null) {
			ToolBarItemSO itemSO;
			itemSO = new ToolBarItemSO((UIToolBarItemImpl) item, so);

			if (attachToControl != null) {
				FormData fd = (FormData) so.getControl().getLayoutData();
				fd.left = new FormAttachment(attachToControl);
			}

			initSO( newMap, so, itemSO);

			if (initComplete) {
				Utils.relayout(so.getControl().getParent());
			}
		}
	}

	private SWTSkinObjectContainer peekGroupSO(String groupID) {
		String soID = "toolbar-group-" + groupID;
		SWTSkinObjectContainer soGroup = (SWTSkinObjectContainer) skin.getSkinObjectByID(
				soID, soMain);
		
		return( soGroup );
	}
	
	private SWTSkinObjectContainer getGroupSO(String groupID) {
		String soID = "toolbar-group-" + groupID;
		SWTSkinObjectContainer soGroup = (SWTSkinObjectContainer) skin.getSkinObjectByID(
				soID, soMain);

		if (soGroup == null) {
			soGroup = (SWTSkinObjectContainer) skin.createSkinObject(soID,
					"toolbar.group", soMain);
			FormData fd = (FormData) soGroup.getControl().getLayoutData();
			if (soLastGroup != null) {
				fd.left = new FormAttachment(soLastGroup.getControl(), 0, SWT.RIGHT);
			} else {
				fd.left = new FormAttachment(0, 2);
			}
		}

		soLastGroup = soGroup;

		return soGroup;
	}

	private void initSO(Map<UIToolBarItem, ToolBarItemSO> newMap, SWTSkinObject so, ToolBarItemSO itemSO) {
		ToolBarItem item = itemSO.getBase();
		itemSO.setSO(so);
		String toolTip = item.getToolTip();
		if (toolTip != null) {
			so.setTooltipID("!" + toolTip + "!");
		} else {
			so.setTooltipID(item.getToolTipID());
		}
		so.setData("toolbaritem", item);
		SWTSkinButtonUtility btn = (SWTSkinButtonUtility) so.getData("btn");
		if (btn == null) {
			btn = new SWTSkinButtonUtility(so, "toolbar-item-image");
			so.setData("btn", btn);
		}
		btn.setImage(item.getImageID());
		btn.addSelectionListener(buttonListener);
		itemSO.setSkinButton(btn);

		SWTSkinObject soTitle = skin.getSkinObject("toolbar-item-title", so);
		if (soTitle instanceof SWTSkinObjectText) {
			((SWTSkinObjectText) soTitle).setTextID(item.getTextID());
			itemSO.setSkinTitle((SWTSkinObjectText) soTitle);
		}
				
		newMap.put(item, itemSO);
	}

	// @see ToolBarItem.ToolBarItemListener#uiFieldChanged(ToolBarItem)
	@Override
	public void uiFieldChanged(ToolBarItem item) {
		ToolBarItemSO itemSO;
		
		synchronized( itemMap ){
			
			itemSO = itemMap.get(item);
		}
		
		if (itemSO != null) {
			itemSO.updateUI();
		}
	}

	private void addSeperator(String groupID) {
		addSeperator("toolbar.area.sitem.sep", groupID);
	}

	private void addSeperator(String id, String groupID) {
		SWTSkinObjectContainer soGroup = getGroupSO(groupID);
		Control lastControl = getLastControl(groupID);
		SWTSkinObject so = skin.createSkinObject("toolbar_sep" + Math.random(), id,
				soGroup);
		if (so != null) {
			if (lastControl != null) {
				FormData fd = (FormData) so.getControl().getLayoutData();
				fd.left = new FormAttachment(lastControl, fd.left == null ? 0
						: fd.left.offset);
			}
		}
	}

	private void addNonToolBar(String skinid, String groupID) {
		SWTSkinObjectContainer soGroup = getGroupSO(groupID);
		Control lastControl = getLastControl(groupID);
		SWTSkinObject so = skin.createSkinObject("toolbar_d" + Math.random(),
				skinid, soGroup);
		if (so != null) {
			if (lastControl != null) {
				FormData fd = (FormData) so.getControl().getLayoutData();
				fd.left = new FormAttachment(lastControl, fd.left == null ? 0
						: fd.left.offset);
			}
		}
	}

	/**
	 * @param showText the showText to set
	 */
	public void setShowText(boolean showText) {
		this.showText = showText;
		UIToolBarItem[] allToolBarItems = tbm.getAllToolBarItems();
		for (int i = 0; i < allToolBarItems.length; i++) {
			UIToolBarItem tbi = allToolBarItems[i];
			SWTSkinObject so = ((ToolBarItemSO) tbi).getSkinButton().getSkinObject();
			SWTSkinObject soTitle = skin.getSkinObject("toolbar-item-title", so);
			if (soTitle != null) {
				soTitle.setVisible(showText);
			}
		}
	}

	/**
	 * @return the showText
	 */
	public boolean getShowText() {
		return showText;
	}

	private class toolbarButtonListener
		extends ButtonListenerAdapter
	{
		@Override
		public void pressed(SWTSkinButtonUtility buttonUtility,
		                    SWTSkinObject skinObject, int button, int stateMask) {
			if ( button != 1 ){
				return;
			}
			ToolBarItem item = (ToolBarItem) buttonUtility.getSkinObject().getData(
					"toolbaritem");
			boolean rightClick = (stateMask & (SWT.BUTTON3 | SWT.MOD4)) > 0;
			Object o = SelectedContentManager.convertSelectedContentToObject(null);
			item.triggerToolBarItem(rightClick
					? UIToolBarActivationListener.ACTIVATIONTYPE_RIGHTCLICK
					: UIToolBarActivationListener.ACTIVATIONTYPE_NORMAL, o);
		}

		@Override
		public boolean held(SWTSkinButtonUtility buttonUtility) {
			ToolBarItem item = (ToolBarItem) buttonUtility.getSkinObject().getData(
					"toolbaritem");
			buttonUtility.getSkinObject().switchSuffix("", 0, false, true);

			Object o = SelectedContentManager.convertSelectedContentToObject(null);
			boolean triggerToolBarItemHold = item.triggerToolBarItem(
					UIToolBarActivationListener.ACTIVATIONTYPE_HELD, o);
			return triggerToolBarItemHold;
		}
		
		@Override
		public void entered(SWTSkinButtonUtility buttonUtility, SWTSkinObject skinObject, int stateMask){
			
			ToolBarItem item = (ToolBarItem) buttonUtility.getSkinObject().getData("toolbaritem");
			
			ToolBarItemSO item_so;
			
			synchronized( itemMap ){
				
				item_so = itemMap.get( item );
			}

			if ( item_so != null ){
				
				if (( stateMask & SWT.CTRL ) != 0 ){
					
					String str = SelectedContentManager.getCurrentlySelectedContentDetails();
								
					item_so.setToolTip( "!" + str + "!");
					
				}else{
					
					item_so.setToolTip( null );					
				}
			}
		}
	}

	public void addListener(ToolBarViewListener l) {
		synchronized (listeners) {
			listeners.add(l);

			if (initComplete) {
				try {
					l.toolbarViewInitialized(this);
				} catch (Exception e) {
					Debug.out(e);
				}
			}
		}
	}

	public void removeListener(ToolBarViewListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	public interface ToolBarViewListener
	{
		public void toolbarViewInitialized(ToolBarView tbv);
	}

	// @see com.biglybt.ui.swt.pifimpl.UIToolBarManagerImpl.ToolBarManagerListener#toolbarItemRemoved(com.biglybt.pif.ui.toolbar.UIToolBarItem)
	@Override
	public void toolbarItemRemoved(final UIToolBarItem toolBarItem) {
		
		rebuild();
	}

	@Override
	public void toolbarItemAdded(final UIToolBarItem item) {
		if (isVisible()) {
			if (item instanceof ToolBarItem) {
				ToolBarItem toolBarItem = (ToolBarItem) item;
				toolBarItem.addToolBarItemListener(this);
			}
		}

		rebuild();
	}

	public abstract static class UIToolBarActivationListener_OffSWT
		implements UIToolBarActivationListener
	{
		private long onlyOnActivationType;

		public UIToolBarActivationListener_OffSWT(long onlyOnActivationType) {
			this.onlyOnActivationType = onlyOnActivationType;
		}

		public UIToolBarActivationListener_OffSWT() {
			onlyOnActivationType = -1;
		}

		@Override
		public final boolean toolBarItemActivated(final ToolBarItem item,
		                                          final long activationType, final Object datasource) {
			if (onlyOnActivationType >= 0 && activationType != onlyOnActivationType) {
				return false;
			}
			Utils.getOffOfSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					toolBarItemActivated_OffSWT(item, activationType, datasource);
				}
			});
			return true;
		}

		public abstract void toolBarItemActivated_OffSWT(ToolBarItem item,
				long activationType, Object datasource);
	}
}
