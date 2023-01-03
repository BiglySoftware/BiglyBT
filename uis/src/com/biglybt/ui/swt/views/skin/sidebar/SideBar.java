/*
 * Created on Jun 23, 2008
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

package com.biglybt.ui.swt.views.skin.sidebar;

import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.json.simple.JSONObject;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.FixedHTMLTransfer;
import com.biglybt.ui.swt.FixedURLTransfer;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.mdi.*;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl.SWTViewListener;
import com.biglybt.ui.swt.pifimpl.*;
import com.biglybt.ui.swt.shells.PopOutManager;
import com.biglybt.ui.swt.shells.main.MainMDISetup;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.IViewAlwaysInitialize;
import com.biglybt.ui.swt.views.QuickLinksView;
import com.biglybt.ui.swt.views.ViewManagerSWT;
import com.biglybt.ui.swt.views.configsections.ConfigSectionInterfaceDisplaySWT;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.util.JSONUtils;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.*;

/**
 * @author TuxPaper
 * @created Jun 23, 2008
 *
 */
public class SideBar
	extends BaseMDI
	implements ObfuscateImage, AEDiagnosticsEvidenceGenerator
{
	protected static final boolean END_INDENT = Constants.isUnix
			|| Constants.isWindows2000 || Constants.isWindows9598ME;

	// Need to use paint even on Cocoa, because there's cases where an area
	// will become invalidated and we don't get a paintitem :(
	private static final boolean USE_PAINT = !Constants.isWindows && !Utils.isGTK;

	// https://bugs.eclipse.org/bugs/show_bug.cgi?id=351751
	// "GTK doesn't allow you to paint over the expander arrows, this is a platform limitation we cannot work around."
	protected static final boolean USE_NATIVE_EXPANDER = Utils.isGTK;

	private static final int GAP_BETWEEN_LEVEL_1;


	static{
		GAP_BETWEEN_LEVEL_1 = Math.min( 5, Math.max( 0, COConfigurationManager.getIntParameter( "Side Bar Top Level Gap", 1 )));
	}

	protected static final int SIDEBAR_ATTENTION_PERIOD	 	= 500;
	protected static final int SIDEBAR_ATTENTION_DURATION 	= 5000;

	private SWTSkin skin;

	private SWTSkinObjectContainer soSideBarContents;

	private Tree tree;

	private Font fontHeader;

	private SWTSkinObject soSideBarPopout;

	private SWTSkinButtonUtility btnCloseItem;
	
	private SelectionListener dropDownSelectionListener;

	private DropTarget dropTarget;

	protected SideBarEntrySWT draggingOver;

	private Color fg;

	private Color bg;

	private List<SideBarEntrySWT> 	attention_seekers = new ArrayList<>();
	private TimerEventPeriodic		attention_event;

	private Composite 				cPluginsArea;
	private Utils.SashWrapper		pluginSash;
	
	private final List<UISWTViewImpl> pluginViews = new ArrayList<>();
	private ParameterListener configShowSideBarListener;
	private ParameterListener configRedrawListener;
	private ParameterListener configBGColorListener;
	private SWTViewListener swtViewListener;

	public SideBar() {
		super(null, UISWTInstance.VIEW_MAIN, null);
		setCloseableConfigFile("sidebarauto.config");
		AEDiagnostics.addWeakEvidenceGenerator(this);
	}

	@Override
	public void buildMDI(Composite parent) {
		// Sidebar only gets built against a skinobject
		Debug.out("uh oh, we didn't code a way make a Sidebar in a Composite yet");
	}

	@Override
	public void buildMDI(SWTSkinObject skinObject) {
		setMainSkinObject(skinObject);
		skinObject.addListener(this);
	}

	// @see SWTSkinObjectAdapter#skinObjectCreated(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectCreated(SWTSkinObject skinObject, Object params) {
		super.skinObjectCreated(skinObject, params);

		skin = skinObject.getSkin();

		soSideBarContents = (SWTSkinObjectContainer) skin.getSkinObject("sidebar-contents");
		soSideBarPopout = skin.getSkinObject("sidebar-pop");

		SWTSkinObjectContainer soSideBarPluginsArea = (SWTSkinObjectContainer) skin.getSkinObject("sidebar-plugins");
		
		if (soSideBarPluginsArea != null) {
			/*
			Composite composite = soSideBarPluginsArea.getComposite();
			
			cPluginsArea = new Composite(composite, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.marginHeight = layout.marginWidth = 0;
			layout.verticalSpacing = layout.horizontalSpacing = 0;
			cPluginsArea.setLayout(layout);
			cPluginsArea.setLayoutData(Utils.getFilledFormData());
			*/
			
			soSideBarPluginsArea.setVisible( false );
		}

		addGeneralMenus();

		createSideBar();

		try {
				// don't think this is required as the SideBar constructor (well SkinView) registers it

			UIUpdater updater = UIUpdaterSWT.getInstance();
			if (updater != null && !updater.isAdded(this)) {
				updater.addUpdater(this);
			}
		} catch ( Throwable  e) {
			Debug.out(e);
		}

		Display.getDefault().addFilter(SWT.KeyDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
				
				// Command Option T is standard on OSX
				// F7 works on both
				if (	event.keyCode == SWT.F7	|| 
						(event.keyCode == 116 && event.stateMask == (SWT.COMMAND | SWT.ALT))){
					
					event.doit = false;
					event.keyCode = 0;
					event.character = '\0';
					flipSideBarVisibility();
				}else if (event.keyCode == SWT.F4 && event.stateMask == SWT.CTRL ){
					MdiEntry entry = getCurrentEntry();

					if ( entry instanceof SideBarEntrySWT && entry.isCloseable()){

						((SideBarEntrySWT)entry).getTreeItem().dispose();
					}
				}
			}
		});

		
		Display.getDefault().addFilter(SWT.Traverse,(ev)->{
			
			if ( 	ev.character == '\t' &&
					(ev.stateMask & (SWT.MOD1 + SWT.SHIFT)) == SWT.MOD1 ) {

				showNext();
				
				ev.doit = false;
				
			}else  if ( 	ev.character == '\t' &&
					(ev.stateMask & (SWT.MOD1 + SWT.SHIFT)) == SWT.MOD1 + SWT.SHIFT) {

				showPrevious();
				
				ev.doit = false;
			}
		});
		
		return null;
	}

	/**
	 *
	 *
	 * @since 3.1.0.1
	 */

	private void addGeneralMenus() {

		PluginManager pm = CoreFactory.getSingleton().getPluginManager();
		PluginInterface pi = pm.getDefaultPluginInterface();
		UIManager uim = pi.getUIManager();
		MenuManager menuManager = uim.getMenuManager();
		
		{
			MenuItem menuItem = menuManager.addMenuItem("sidebar._end_", "sep");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.setStyle(MenuItem.STYLE_SEPARATOR);
		}
		
		{
			MenuItem menuItem = menuManager.addMenuItem("sidebar._end_", "UpdateWindow.close");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
				
			menuItem.addFillListener(
				new MenuItemFillListener() {
	
					@Override
					public void menuWillBeShown(MenuItem menu, Object data) {
						SideBarEntrySWT sbe = getCurrentEntry();
	
						if ( sbe != null && !sbe.isCloseable()){
							
							menu.setVisible( false );
							
						}else {

							// Always show close menu because some OSes (Gnome on Debian)
							// cover up the "x" with an animated scrollbar
							menu.setVisible( true );
						}
					}
				});
	
			menuItem.addListener((menu, target) -> closeEntry(getCurrentEntry(), true ));
		}
		
		{
			MenuItem menuParentItem = menuManager.addMenuItem("sidebar._end_", "menu.add.to");
			menuParentItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
	
			menuParentItem.setStyle( MenuItem.STYLE_MENU );
						
			String ql_res = "v3.MainWindow.menu.view.quick-links";
			
			menuParentItem.addFillListener((menu, data) -> {
				
				SideBarEntrySWT entry = getCurrentEntry();
				
				boolean visible = 
					entry != null && 
					entry.canBuildStandAlone() &&
					!entry.getViewID().equals( MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD);
			
				menu.setVisible( visible );
				
				if ( visible ){
					
					boolean ql_ok = QuickLinksView.canAddItem( this, entry );
					
					MenuItem[] items = menu.getItems();
						
					for ( MenuItem item: items ){
						
						String res = item.getResourceKey();
							
						if ( res.equals( ql_res )){
							
							item.setVisible( ql_ok );
						}
					}
				}
			});
	
			MenuItem menuItemDashBoard = menuManager.addMenuItem( menuParentItem, "label.dashboard");

			menuItemDashBoard.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = getCurrentEntry();
	
					if ( sbe != null ){
						
						MainMDISetup.getSb_dashboard().addItem( sbe );
					}
				}
			});
			
			MenuItem menuItemTopbar = menuManager.addMenuItem( menuParentItem, "label.topbar");

			menuItemTopbar.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = getCurrentEntry();
	
					if ( sbe != null ){
						
						MainMDISetup.getSb_dashboard().addItemToTopbar( sbe );
					}
				}
			});
			
			MenuItem menuItemSidebar = menuManager.addMenuItem( menuParentItem, "label.sidebar");

			menuItemSidebar.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = getCurrentEntry();
	
					if ( sbe != null ){
						
						MainMDISetup.getSb_dashboard().addItemToSidebar( sbe );
					}
				}
			});
			
			MenuItem menuItemRightbar = menuManager.addMenuItem( menuParentItem, "label.rightbar");

			menuItemRightbar.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = getCurrentEntry();
	
					if ( sbe != null ){
						
						MainMDISetup.getSb_dashboard().addItemToRightbar( sbe );
					}
				}
			});
			
			MenuItem menuItemQuickLinks = menuManager.addMenuItem( menuParentItem, ql_res );

			menuItemQuickLinks.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = getCurrentEntry();
	
					if ( sbe != null ){
						
						QuickLinksView.addItem( SideBar.this, sbe );
					}
				}
			});
		}
		
		{
			MenuItem menuParentItem = menuManager.addMenuItem( "sidebar._end_", "label.pop.out");
			menuParentItem.setStyle(MenuItem.STYLE_MENU );
			menuParentItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);

			
			menuParentItem.addFillListener(
				new MenuItemFillListener() {
	
					@Override
					public void menuWillBeShown(MenuItem menu, Object data) {
						SideBarEntrySWT sbe = getCurrentEntry();
	
						menu.setVisible( sbe != null && sbe.canBuildStandAlone());
					}
				});
	
			MenuItem menuItemIndependent = menuManager.addMenuItem( menuParentItem, "menu.independent");
			MenuItem menuItemOnTop		 = menuManager.addMenuItem( menuParentItem, "menu.on.top");

			MenuItemListener listener = new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = getCurrentEntry();
	
					if ( sbe != null ){

						popoutEntry( sbe, menu==menuItemOnTop?PopOutManager.OPT_MAP_ON_TOP:PopOutManager.OPT_MAP_NONE );
					}
				}
			};
			
			menuItemIndependent.addListener( listener );
			menuItemOnTop.addListener( listener );
		}
	}

	public boolean
	canPopoutEntry(
		MdiEntry	entry )
	{
		SideBarEntrySWT sbe = (SideBarEntrySWT)entry;
		
		return( sbe.canBuildStandAlone());
	}
	
	public boolean
	popoutEntry(
		MdiEntry			entry,
		Map<String,Object>	options )
	{
		return( PopOutManager.popOut((SideBarEntrySWT)entry, options ));
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	public void flipSideBarVisibility() {
		final SWTSkinObjectSash soSash = (SWTSkinObjectSash) skin.getSkinObject("sidebar-sash");
		if (soSash == null) {
			return;
		}
		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				boolean visible = !soSash.isAboveVisible();

				soSash.setAboveVisible( visible );
				updateSidebarVisibility();

				COConfigurationManager.setParameter( "Show Side Bar", visible );
			}
		});
	}

	private void updateSidebarVisibility() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				final SWTSkinObjectSash soSash = (SWTSkinObjectSash) skin.getSkinObject("sidebar-sash");
				if (soSash == null) {
					return;
				}
				if (soSash.isAboveVisible()) {
					if (soSideBarPopout != null) {
						Object ld = soSideBarPopout.getControl().getLayoutData();
						if (ld instanceof FormData) {
							FormData fd = (FormData) ld;
							fd.width = 0;
						}
						soSideBarPopout.setVisible(false);

						Utils.relayout(soSideBarPopout.getControl());
					}
				} else {
					if (soSideBarPopout != null) {
						Object ld = soSideBarPopout.getControl().getLayoutData();
						if (ld instanceof FormData) {
							FormData fd = (FormData) ld;
							fd.width = 36;
						}
						soSideBarPopout.setVisible(true);
						soSideBarPopout.getControl().moveAbove(null);
						Utils.relayout(soSideBarPopout.getControl());
					}
				}
			}
		});
	}

	@Override
	public boolean isVisible() {
		SWTSkinObjectSash soSash = (SWTSkinObjectSash) skin.getSkinObject("sidebar-sash");
		if (soSash == null) {
			return false;
		}
		return soSash.isAboveVisible();
	}

	// @see SkinView#showSupport(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {

		super.skinObjectInitialShow(skinObject, params);

		configShowSideBarListener = new ParameterListener() {
			@Override
			public void
			parameterChanged(
					String name) {
				boolean visible = COConfigurationManager.getBooleanParameter(name);

				if (visible != isVisible()) {

					flipSideBarVisibility();
				}
			}
		};
		
		COConfigurationManager.addParameterListener(
				"Show Side Bar",
				configShowSideBarListener);

		configRedrawListener = new ParameterListener() {
			@Override
			public void
			parameterChanged(
				String name) 
			{
				Utils.execSWTThread( new Runnable(){ public void run(){ swt_redraw(); }});
			}
		};
		
		COConfigurationManager.addParameterListener(
				new String[]{
					"Side Bar Close Position",
					"Side Bar Indent Expanders",
					"Side Bar Compact View",
					"Side Bar Hide Left Icon",
				}, configRedrawListener );
		
		updateSidebarVisibility();

		return null;
	}

	/* (non-Javadoc)
	 * @see BaseMDI#setupPluginViews()
	 */
	@Override
	protected void setupPluginViews() {
		super.setupPluginViews();
		createSideBarPluginViews();
	}

	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		try {
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			if (uiFunctions != null) {
				UIUpdater uiUpdater = uiFunctions.getUIUpdater();
				if (uiUpdater != null) {
					uiUpdater.removeUpdater(this);
				}
			}
		} catch (Exception e) {
			Debug.out(e);
		}

		COConfigurationManager.removeParameterListener( "Show Side Bar", configShowSideBarListener);
		COConfigurationManager.removeParameterListener(	"config.skin.color.sidebar.bg", configBGColorListener);
		COConfigurationManager.removeParameterListener(	"Side Bar Close Position", configBGColorListener);

		COConfigurationManager.removeParameterListener( "Side Bar Close Position", configRedrawListener );
		COConfigurationManager.removeParameterListener( "Side Bar Indent Expanders", configRedrawListener );
		COConfigurationManager.removeParameterListener( "Side Bar Compact View", configRedrawListener );
		COConfigurationManager.removeParameterListener( "Side Bar Hide Left Icon", configRedrawListener );
		
		if (swtViewListener != null) {
			try {
				ViewManagerSWT.getInstance().removeSWTViewListener(swtViewListener);
			} catch (Throwable ignore) {

			}
		}

		return super.skinObjectDestroyed(skinObject, params);
	}

	private void createSideBar() {
		
		SWTSkinObject soSideBarList = skin.getSkinObject("sidebar-list");

		if (soSideBarList == null) {
			return;
		}
		
		Composite sidebarList = (Composite) soSideBarList.getControl();
		
		pluginSash = Utils.createSashWrapper( sidebarList, "Sidebar.Plugin.SplitAt", 75 );
		
		pluginSash.setBottomVisible( false );
		
		Composite[] kids = pluginSash.getChildren();
		
	    cPluginsArea = new Composite(kids[1], SWT.NONE);
	    GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		layout.verticalSpacing = layout.horizontalSpacing = 0;
		cPluginsArea.setLayout(layout);
		cPluginsArea.setLayoutData(Utils.getFilledFormData());		
		
		
		tree = new Tree(kids[0], SWT.FULL_SELECTION | SWT.V_SCROLL
				| SWT.DOUBLE_BUFFERED | SWT.NO_SCROLL);
		tree.setHeaderVisible(false);

		new SideBarToolTips(this, tree);

		tree.setLayoutData(Utils.getFilledFormData());

		SWTSkinProperties skinProperties = skin.getSkinProperties();
		bg = skinProperties.getColor("color.sidebar.bg");
		fg = skinProperties.getColor("color.sidebar.fg");

		configBGColorListener = new ParameterListener() {

			@Override
			public void parameterChanged(String parameterName) {

				Utils.execSWTThread(
						new Runnable() {
							@Override
							public void
							run() {
								swt_updateSideBarColors();
							}
						});
			}
		};
		
		COConfigurationManager.addParameterListener( "config.skin.color.sidebar.bg", configBGColorListener );
		COConfigurationManager.addParameterListener( "Side Bar Close Position", configBGColorListener );

		tree.setBackground(bg);
		tree.setForeground(fg);

		fontHeader = FontUtils.getFontWithStyle(tree.getFont(), SWT.BOLD, 1.0f);

		// after a scroll we need to recalculate the hit areas as they will have moved!

		ScrollBar vBar = tree.getVerticalBar();
		
		vBar.setData(
			"ScrollOnMouseOver",
			(Runnable) () ->{
				int pos = vBar.getSelection();
								
				TreeItem item = getTreeItemAt( pos );
				
				if ( item != null ){
					
					tree.setTopItem( item );
				}
			});
		
			// repaint problems on mousewheel scroll on OSX
		if ( Constants.isOSX ) {
			vBar.setData(
				"ScrollOnMouseOver2",
				(Runnable) () ->{
					tree.redraw();
				});
		}
		
		vBar.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent e) {
					if ( e.detail == SWT.None ){
						SideBarEntrySWT[] sideBarEntries = getEntries( new SideBarEntrySWT[0]);
						swt_updateSideBarHitAreasY(sideBarEntries);
					}
					
					// repaint problems on mousewheel scroll on OSX
					if ( Constants.isOSX ) {
						tree.redraw();
					}
				}
			});

		Listener treeListener = new Listener() {
			TreeItem lastTopItem = null;

			boolean mouseDowned = false;

			Rectangle lastCloseAreaClicked = null;

			private boolean wasExpanded;

			@Override
			public void handleEvent(final Event event) {
				if (Utils.isDisplayDisposed()) {
					if (event.type == SWT.Dispose) {
						saveCloseables();
					}
					return;
				}
				TreeItem treeItem = (TreeItem) event.item;
				Tree tree = getTree();

				try {
					switch (event.type) {
						case SWT.MeasureItem: {
							int clientWidth = tree.getClientArea().width;
							String text = treeItem.getText(event.index);
							Point size = event.gc.textExtent(text);
							if (event.x + event.width < clientWidth) {
								event.width = size.x + event.x; // tree.getClientArea().width;
								event.x = 0;
							}

							if (Constants.isWindows) {
								event.width = clientWidth - event.x;
							}

							event.height = 20;

							break;
						}
						case SWT.PaintItem: {
							SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");
							//System.out.println("PaintItem: " + event.item + ";" + event.index + ";" + event.detail + ";" + event.getBounds() + ";" + event.gc.getClipping());
							if (entry != null) {
								boolean selected = getCurrentEntry() == entry;
									
								if (!selected) {
									event.detail &= ~SWT.SELECTED;
								} else {
									event.detail |= SWT.SELECTED;
								}
								entry.swt_paintSideBar(event);
							}
							break;
						}

						case SWT.Paint: {
							//System.out.println("Paint: " + event.getBounds() + ";" + event.detail + ";" + event.index + ";" + event.gc.getClipping());// + "  " + Debug.getCompressedStackTrace());
							if (!USE_PAINT) {
								return;
							}
							Rectangle bounds = event.getBounds();
							int indent = END_INDENT ? tree.getClientArea().width - 1 : 0;
							int y = event.y + 1;
							treeItem = tree.getItem(new Point(indent, y));

							while (treeItem != null) {
								SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");
								Rectangle itemBounds = entry == null ? null
										: entry.swt_getBounds();

								// null itemBounds is weird, the entry must be disposed. it
								// happened once, so let's check..
								if (itemBounds != null && entry != null) {
									event.item = treeItem;

									boolean selected = getCurrentEntry() == entry
											&& entry.isSelectable();
									event.detail = selected ? SWT.SELECTED : SWT.NONE;

									Rectangle newClip = bounds.intersection(itemBounds);
									//System.out.println("Paint " + id + " @ " + newClip);
									event.setBounds(newClip);
									Utils.setClipping(event.gc, newClip);


									entry.swt_paintSideBar(event);


									y = itemBounds.y + itemBounds.height + 1;
								} else {
									y += tree.getItemHeight();
								}

								if (y > bounds.y + bounds.height) {
									break;
								}
								TreeItem oldTreeItem = treeItem;
								treeItem = tree.getItem(new Point(indent, y));
								if (oldTreeItem == treeItem) {
									break;
								}
							}

							if (tree.getTopItem() != lastTopItem) {
								lastTopItem = tree.getTopItem();
								SideBarEntrySWT[] sideBarEntries = getEntries( new SideBarEntrySWT[0]);
								swt_updateSideBarHitAreasY(sideBarEntries);
							}

							break;
						}

						case SWT.EraseItem: {
							SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");
							if (entry == null) {
								event.detail = 0;
							}
							//event.detail &= ~SWT.FOREGROUND;
							//event.detail &= ~(SWT.FOREGROUND | SWT.BACKGROUND);
							event.doit = true;
							break;
						}

						case SWT.Resize: {
							tree.redraw();
							break;
						}

						case SWT.Selection: {
							if (treeItem == null) {
								return;
							}
							SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");
							if (entry != null ) {
								Point cursorLocation = tree.toControl(event.display.getCursorLocation());
								if (lastCloseAreaClicked != null && lastCloseAreaClicked.contains(cursorLocation.x, cursorLocation.y)) {
									return;
								}

								showEntry(entry);
							} else if (getCurrentEntry() != null) {
								TreeItem topItem = tree.getTopItem();

								// prevent "jumping" in the case where selection is off screen
								// setSelection would jump the item on screen, and then
								// showItem would jump back to where the user was.
								tree.setRedraw(false);
								TreeItem ti = getCurrentEntry().getTreeItem();
								if (ti != null) {
									tree.setSelection(ti);
								}

								if ( topItem != null ){	// Seen on OSX
									tree.setTopItem(topItem);
								}
								
								tree.setRedraw(true);

								event.doit = false;
							}
							break;
						}

						case SWT.MouseMove: {
							int indent = END_INDENT ? tree.getClientArea().width - 1 : 0;
							treeItem = tree.getItem(new Point(indent, event.y));
							SideBarEntrySWT entry = (SideBarEntrySWT) ((treeItem == null || treeItem.isDisposed())
									? null : treeItem.getData("MdiEntry"));

							int cursorNo = SWT.CURSOR_ARROW;
							if (treeItem != null) {
								Rectangle closeArea = (Rectangle) treeItem.getData("closeArea");
								if (closeArea != null && closeArea.contains(event.x, event.y)) {
									cursorNo = SWT.CURSOR_HAND;
								} else if (entry != null && treeItem.getItemCount() > 0) {
									cursorNo = SWT.CURSOR_HAND;
								}else if ( entry != null ) {
									List<MdiEntryVitalityImageSWT> vitalityImages = entry.getVitalityImages();
									for (MdiEntryVitalityImageSWT vitalityImage : vitalityImages) {
										if (vitalityImage == null || !vitalityImage.isVisible()) {
											continue;
										}
										Rectangle hitArea = vitalityImage.getHitArea();
										if (hitArea == null) {
											continue;
										}
										// setHitArea needs it relative to entry
										Rectangle itemBounds = entry.swt_getBounds();
										int relY = event.y - (itemBounds == null ? 0 : itemBounds.y);

										if (hitArea.contains(event.x, relY)) {
											if ( vitalityImage.hasListeners()) {
												cursorNo = SWT.CURSOR_HAND;
											}
										}
									}
								}
							}

							Cursor cursor = event.display.getSystemCursor(cursorNo);
							if (tree.getCursor() != cursor) {
								tree.setCursor(cursor);
							}

							if (treeItem != null) {
								wasExpanded = entry != null && entry.isExpanded();
							} else {
								wasExpanded = false;
							}
							break;
						}

						case SWT.MouseDown: {
							mouseDowned = true;
							lastCloseAreaClicked  = null;
							if (tree.getItemCount() == 0 || event.button != 1) {
								return;
							}
							int indent = END_INDENT ? tree.getClientArea().width - 1 : 0;
							treeItem = tree.getItem(new Point(indent, event.y));
							if (treeItem == null) {
								return;
							}
							Rectangle closeArea = (Rectangle) treeItem.getData("closeArea");
							if (closeArea != null && closeArea.contains(event.x, event.y)) {
								lastCloseAreaClicked = closeArea;
								SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");
								if (entry != null) {
									closeEntry(entry,true);
								} else {
									treeItem.dispose();
								}
								// pretend we don't have a mouse down, so we don't process a showEntry
								mouseDowned = false;
							}
							break;
						}

						case SWT.MouseUp: {
							if (!mouseDowned) {
								return;
							}
							mouseDowned = false;
							if (tree.getItemCount() == 0 || event.button != 1) {
								return;
							}
							int indent = END_INDENT ? tree.getClientArea().width - 1 : 0;
							treeItem = tree.getItem(new Point(indent, event.y));
							if (treeItem == null) {
								return;
							}
							SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");

							Rectangle closeArea = (Rectangle) treeItem.getData("closeArea");
							if (closeArea != null && closeArea.contains(event.x, event.y)) {
								//treeItem.dispose();
								return;
							} else if (getCurrentEntry() != entry && Constants.isOSX) {
								// showEntry(entry);  removed as we'll get a selection event if needed
							}

							if (entry != null) {
								List<MdiEntryVitalityImageSWT> vitalityImages = entry.getVitalityImages();
								for (MdiEntryVitalityImageSWT vitalityImage : vitalityImages) {
									if (vitalityImage == null || !vitalityImage.isVisible()) {
										continue;
									}
									Rectangle hitArea = vitalityImage.getHitArea();
									if (hitArea == null) {
										continue;
									}
									// setHitArea needs it relative to entry
									Rectangle itemBounds = entry.swt_getBounds();
									int relY = event.y - (itemBounds == null ? 0 : itemBounds.y);

									if (hitArea.contains(event.x, relY)) {
										vitalityImage.triggerClickedListeners(event.x, relY);
										return;
									}
								}

								if ( treeItem.getItemCount() > 0) {
									
									Integer rhs = (Integer)treeItem.getData( "expandoRHS" );
									
									int limit=rhs==null?20:rhs;
									
									if ( event.x < limit) {
										
											// Note: On Windows, user can expand row by clicking the invisible area where the OS twisty would be
										
										MdiEntry currentEntry = getCurrentEntry();
										
										String collapsedID = entry.getViewID();
										
										while( currentEntry != null ){
											
											String parentID = currentEntry.getParentID();
											
											if ( collapsedID.equals( parentID )) {
											
												showEntryByID( collapsedID );
												
												break;
											}
											
											currentEntry = getEntry( parentID );
										}

										entry.setExpanded(!wasExpanded);
										wasExpanded = !wasExpanded;
									}
								}
							}

							break;
						}
						case SWT.MouseDoubleClick: {
							
							int indent = END_INDENT ? tree.getClientArea().width - 1 : 0;
							treeItem = tree.getItem(new Point(indent, event.y));
							SideBarEntrySWT entry = (SideBarEntrySWT) ((treeItem == null || treeItem.isDisposed())
									? null : treeItem.getData("MdiEntry"));

							if ( entry != null ){
								
									// don't trigger popout if double click is in close area or
									// item has child and user is clicking in the expandy area 

								boolean done = false;
								
								Point cursorLocation = tree.toControl(event.display.getCursorLocation());
								
								if ( lastCloseAreaClicked == null || !lastCloseAreaClicked.contains(cursorLocation.x, cursorLocation.y)){
																		
									Integer offset = (Integer)treeItem.getData( "contentStartOffset" );
									
									if ( treeItem.getItemCount() == 0 || ( offset != null && event.x >= offset )){
										
										if ( canPopoutEntry(entry)){
										
											done = popoutEntry( entry, PopOutManager.OPT_MAP_ON_TOP );
										}
									}
								}
								
								if ( !done ){
									
									if ( treeItem.getItemCount() > 0 ){
										
										entry.setExpanded( !entry.isExpanded());
									}
								}
							}
							
							break;
						}
						case SWT.Dispose: {
							fontHeader.dispose();
							if (dropTarget != null && !dropTarget.isDisposed()) {
								dropTarget.dispose();
							}
							saveCloseables();

							break;
						}
						case SWT.Collapse: {
							SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");
							
							MdiEntry currentEntry = getCurrentEntry();
							
							String collapsedID = entry.getViewID();
							
							while( currentEntry != null ){
								
								String parentID = currentEntry.getParentID();
								
								if ( collapsedID.equals( parentID )) {
								
									showEntryByID( collapsedID );
									
									break;
								}
								
								currentEntry = getEntry( parentID );
							}
						
							break;
						}
						case SWT.KeyDown:{
						
							MdiEntry currentEntry = getCurrentEntry();
							
							if ( currentEntry != null ){
								
								if ( currentEntry.processAccelerator( event.character, event.stateMask )){
									event.doit = false;
								}
							}
							break;
						}
					}
				} catch (Exception e) {
					Debug.out(e);
				}
			}
		};
		tree.addListener(SWT.Resize, treeListener);
		if (USE_PAINT) {
			tree.addListener(SWT.Paint, treeListener);
		}
		tree.addListener(SWT.MeasureItem, treeListener);
		tree.addListener(SWT.PaintItem, treeListener);
		tree.addListener(SWT.EraseItem, treeListener);

		tree.addListener(SWT.Selection, treeListener);
		tree.addListener(SWT.Dispose, treeListener);
		tree.addListener(SWT.MouseDoubleClick, treeListener);

		// For icons
		tree.addListener(SWT.MouseUp, treeListener);
		tree.addListener(SWT.MouseDown, treeListener);

		// For cursor
		tree.addListener(SWT.MouseMove, treeListener);

		// to disable collapsing
		tree.addListener(SWT.Collapse, treeListener);
		
		// for accelerators
		
		tree.addListener(SWT.KeyDown, treeListener);


		dropTarget = new DropTarget(tree, DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
				| DND.DROP_TARGET_MOVE);
		dropTarget.setTransfer(new Transfer[] {
				FixedHTMLTransfer.getInstance(),
				FixedURLTransfer.getInstance(),
				FileTransfer.getInstance(),
				TextTransfer.getInstance(),
		});

		dropTarget.addDropListener(new DropTargetAdapter() {
			@Override
			public void dropAccept(DropTargetEvent event) {
				event.currentDataType = FixedURLTransfer.pickBestType(event.dataTypes,
						event.currentDataType);
			}

			@Override
			public void dragEnter(DropTargetEvent event) {
			}

			@Override
			public void dragOperationChanged(DropTargetEvent event) {
			}

			// @see org.eclipse.swt.dnd.DropTargetAdapter#dragOver(org.eclipse.swt.dnd.DropTargetEvent)
			@Override
			public void dragOver(DropTargetEvent event) {
				TreeItem treeItem = (event.item instanceof TreeItem)
						? (TreeItem) event.item : null;

				if (treeItem != null) {
					SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");

					draggingOver = entry;
				} else {
					draggingOver = null;
				}
				if (draggingOver == null || !draggingOver.hasDropListeners()) {

					boolean isTorrent = TorrentOpener.doesDropHaveTorrents(event);

					if (isTorrent) {
						event.detail = DND.DROP_COPY;
					} else {
						event.detail = DND.DROP_NONE;
					}
					draggingOver = null;
				} else if ((event.operations & DND.DROP_LINK) > 0)
					event.detail = DND.DROP_LINK;
				else if ((event.operations & DND.DROP_COPY) > 0)
					event.detail = DND.DROP_COPY;
				else if ((event.operations & DND.DROP_DEFAULT) > 0)
					event.detail = DND.DROP_COPY;

				if (Constants.isOSX) {
					tree.redraw();
				}

				event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL | DND.FEEDBACK_EXPAND;
			}

			// @see org.eclipse.swt.dnd.DropTargetAdapter#dragLeave(org.eclipse.swt.dnd.DropTargetEvent)
			@Override
			public void dragLeave(DropTargetEvent event) {
				draggingOver = null;
				tree.redraw();
			}

			@Override
			public void drop(DropTargetEvent event) {
				draggingOver = null;
				tree.redraw();
				if (!(event.item instanceof TreeItem)) {
					defaultDrop(event);
					return;
				}
				TreeItem treeItem = (TreeItem) event.item;

				SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");

				boolean handled = entry != null && entry.triggerDropListeners(event.data);
				if (!handled) {
					defaultDrop(event);
				}
			}
		});

		final Menu menuTree = new Menu(tree);
		tree.setMenu(menuTree);
		
		tree.addMenuDetectListener(e -> {
			menuTree.setData("MenuSource", e.detail);
		});

		menuTree.addMenuListener(new MenuListener() {
			boolean bShown = false;

			@Override
			public void menuHidden(MenuEvent e) {
				bShown = false;

				if (Constants.isOSX) {
					return;
				}

				// Must dispose in an asyncExec, otherwise SWT.Selection doesn't
				// get fired (async workaround provided by Eclipse Bug #87678)
				Utils.execSWTThreadLater(0, new AERunnable() {
					@Override
					public void runSupport() {
						if (bShown || menuTree.isDisposed()) {
							return;
						}
						Utils.disposeSWTObjects(menuTree.getItems());
					}
				});
			}

			@Override
			public void menuShown(MenuEvent e) {
				Utils.disposeSWTObjects(menuTree.getItems());

				bShown = true;

				Object oMenuSource = menuTree.getData("MenuSource");
				int menuSource = (oMenuSource instanceof Number)
						? ((Number) oMenuSource).intValue() : SWT.MENU_MOUSE;

				SideBarEntrySWT entry;
				if (menuSource != SWT.MENU_KEYBOARD) {
					Point ptMouse = tree.toControl(e.display.getCursorLocation());
	
					int indent = END_INDENT ? tree.getClientArea().width - 1 : 0;
					TreeItem treeItem = tree.getItem(new Point(indent, ptMouse.y));
					if (treeItem != null) {
					
						entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");
					}else{
						entry = null;
					}
				} else {
					entry = getCurrentEntry(); 
				}

				if ( entry != null ){
				
					fillMenu(menuTree, entry, "sidebar");
				}

				if ( entry == null || entry.getParentID() == null ){
					
					MenuBuildUtils.addSeparator( menuTree );
					
					org.eclipse.swt.widgets.MenuItem mi = new org.eclipse.swt.widgets.MenuItem( menuTree, SWT.PUSH );
					
					mi.setText( MessageText.getString( "menu.sidebar.options" ));
					
					mi.addListener( SWT.Selection, (ev)->{
						UIFunctions uif = UIFunctionsManager.getUIFunctions();

						if ( uif != null ){

							JSONObject args = new JSONObject();

							args.put( "select", ConfigSectionInterfaceDisplaySWT.REFID_SECTION_SIDEBAR);
							
							String args_str = JSONUtils.encodeToJSON( args );
							
							uif.getMDI().showEntryByID(
									MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
									ConfigSectionInterfaceDisplaySWT.SECTION_ID + args_str );
						}
					});
				}
				
				if (menuTree.getItemCount() == 0) {
					Utils.execSWTThreadLater(0, new AERunnable() {
						@Override
						public void runSupport() {
							menuTree.setVisible(false);
						}
					});
				}
			}
		});

		if (soSideBarPopout != null) {
			SWTSkinObject soDropDown = skin.getSkinObject("sidebar-dropdown");
			if (soDropDown != null) {

				final Menu menuDropDown = new Menu(soDropDown.getControl());

				menuDropDown.addMenuListener(new MenuListener() {
					boolean bShown = false;

					@Override
					public void menuHidden(MenuEvent e) {
						bShown = false;

						if (Constants.isOSX) {
							return;
						}

						// Must dispose in an asyncExec, otherwise SWT.Selection doesn't
						// get fired (async workaround provided by Eclipse Bug #87678)
						Utils.execSWTThreadLater(0, new AERunnable() {
							@Override
							public void runSupport() {
								if (bShown || menuDropDown.isDisposed()) {
									return;
								}
								Utils.disposeSWTObjects(menuDropDown.getItems());
							}
						});
					}

					@Override
					public void menuShown(MenuEvent e) {
						Utils.disposeSWTObjects(menuDropDown.getItems());

						bShown = true;

						fillDropDownMenu(menuDropDown, tree.getItems(), 0);
					}
				});

				dropDownSelectionListener = new SelectionListener() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						String id = (String) e.widget.getData("Plugin.viewID");
						showEntryByID(id);
					}

					@Override
					public void widgetDefaultSelected(SelectionEvent e) {
					}
				};

				SWTSkinButtonUtility btnDropDown = new SWTSkinButtonUtility(soDropDown);
				btnDropDown.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
					@Override
					public void pressed(SWTSkinButtonUtility buttonUtility,
					                    SWTSkinObject skinObject, int stateMask) {
						Control c = buttonUtility.getSkinObject().getControl();
						menuDropDown.setLocation(c.getDisplay().getCursorLocation());
						menuDropDown.setVisible(!menuDropDown.getVisible());
					}
				});
			}

			SWTSkinObject soExpand = skin.getSkinObject("sidebar-expand");
			if (soExpand != null) {
				SWTSkinButtonUtility btnExpand = new SWTSkinButtonUtility(soExpand);
				btnExpand.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
					@Override
					public void pressed(SWTSkinButtonUtility buttonUtility,
					                    SWTSkinObject skinObject, int stateMask) {
						flipSideBarVisibility();
					}
				});
			}
			SWTSkinObject soCloseItem = skin.getSkinObject("sidebar-closeitem");
			if (soCloseItem != null) {
				btnCloseItem = new SWTSkinButtonUtility(soCloseItem);
				btnCloseItem.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
					@Override
					public void pressed(SWTSkinButtonUtility buttonUtility,
					                    SWTSkinObject skinObject, int stateMask) {
						closeEntry(getCurrentEntry(),true);
					}
				});
			}
		}
	}

	private void createSideBarPluginViews() {
		if (cPluginsArea == null) {
			return;
		}

		List<UISWTViewBuilderCore> pluginViewBuilders = ViewManagerSWT.getInstance().getBuilders(
				UISWTInstance.VIEW_SIDEBAR_AREA);
		for (UISWTViewBuilderCore builder : pluginViewBuilders) {
			if (builder == null) {
				continue;
			}
			try {
				UISWTViewImpl view = new UISWTViewImpl(builder, false);
				view.setDestroyOnDeactivate(false);
				addSideBarView(view, cPluginsArea);
				cPluginsArea.getParent().getParent().layout(true, true);
			} catch (Exception e) {
				e.printStackTrace();
				// skip, plugin probably specifically asked to not be added
			}
		}

		swtViewListener = new SWTViewListener() {
			@Override
			public void setViewRegistered(Object forDSTypeOrViewID,
					UISWTViewBuilderCore builder) {
				if (!UISWTInstance.VIEW_SIDEBAR_AREA.equals(forDSTypeOrViewID)) {
					return;
				}
				Utils.execSWTThread(() -> {
					try {
						UISWTViewImpl view = new UISWTViewImpl(builder, false);
						view.setDestroyOnDeactivate(false);
						addSideBarView(view, cPluginsArea);
					} catch (Exception e) {
						e.printStackTrace();
						// skip, plugin probably specifically asked to not be added
					}
				});
			}

			@Override
			public void setViewDeregistered(Object forDSTypeOrViewID,
					UISWTViewBuilderCore builder) {
				if (!UISWTInstance.VIEW_SIDEBAR_AREA.equals(forDSTypeOrViewID)) {
					return;
				}
				Utils.execSWTThread(() -> {	
					
						List<UISWTViewImpl> views;
						
						synchronized( pluginViews ){
						
							views = new ArrayList<>( pluginViews );
						}
						
						for (UISWTViewImpl view : views){
							
							if (builder.equals(view.getEventListenerBuilder())) {
									
								removeSideBarView( view );
							}
						}
					});			
			}
		};
		
		ViewManagerSWT.getInstance().addSWTViewListener(swtViewListener);

		cPluginsArea.getParent().getParent().layout(true, true);
	}

	private void addSideBarView(UISWTViewImpl view, Composite cPluginsArea) {
		
		synchronized( pluginViews ){
			
			pluginViews.add(view);
			
			try {
				view.create();
				
			} catch (UISWTViewEventCancelledException e) {
				
				pluginViews.remove(view);
				
				return;
			}
	
			if ( pluginViews.size() > 0 ){
									
				pluginSash.setBottomVisible( true );
			
				/*
				SWTSkinObjectContainer soSideBarPluginsArea = (SWTSkinObjectContainer) skin.getSkinObject("sidebar-plugins");
				
				if ( !soSideBarPluginsArea.isVisible()){
				
					soSideBarPluginsArea.setVisible( true );
				}
				*/
			}
		}

			// obviously this doesn't work for > 1 plugin, needs tabs or something

		Utils.disposeComposite( cPluginsArea, false );
		
		Composite parent = new Composite(cPluginsArea, SWT.NONE);
				
		GridData gridData = new GridData( GridData.FILL_BOTH );
		
		parent.setLayoutData(gridData);

		parent.setLayout( new FormLayout());	// this works well with the initialize code
		
		view.initialize( parent );
		
		cPluginsArea.getParent().layout( true,  true );
	}

	private void removeSideBarView(UISWTViewImpl view ){
		
		try{
			view.closeView();
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
		
		synchronized( pluginViews ){
			
			pluginViews.remove(view);
		
			if ( pluginViews.isEmpty()){
									
				pluginSash.setBottomVisible( false );
				
				/*
				SWTSkinObjectContainer soSideBarPluginsArea = (SWTSkinObjectContainer) skin.getSkinObject("sidebar-plugins");
				
				if ( soSideBarPluginsArea.isVisible()){
				
					soSideBarPluginsArea.setVisible( false );
				}
				*/
			}
		}
	}
	

	/**
	 * @param event
	 */
	protected void defaultDrop(DropTargetEvent event) {
		TorrentOpener.openDroppedTorrents(event, false);
	}

	/**
	 * @param menuDropDown
	 *
	 * @since 3.1.1.1
	 */
	protected void fillDropDownMenu(Menu menuDropDown, TreeItem[] items,
			int indent) {
		String s = "";
		for (int i = 0; i < indent; i++) {
			s += "   ";
		}
		for (int i = 0; i < items.length; i++) {
			TreeItem treeItem = items[i];

			SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");
			if (entry == null) {
				continue;
			}
			org.eclipse.swt.widgets.MenuItem menuItem = new org.eclipse.swt.widgets.MenuItem(
					menuDropDown, entry.isSelectable() ? SWT.RADIO : SWT.CASCADE);

			String id = entry.getViewID();
			menuItem.setData("Plugin.viewID", id);
			ViewTitleInfo titleInfo = entry.getViewTitleInfo();
			String ind = "";
			if (titleInfo != null) {
				String o = (String) titleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_INDICATOR_TEXT);
				if (o != null) {
					ind = "  (" + o + ")";
					//ind = "\t" + o;
				}
			}
			menuItem.setText(s + entry.getTitle() + ind);
			menuItem.addSelectionListener(dropDownSelectionListener);
			MdiEntry currentEntry = getCurrentEntry();
			if (currentEntry != null && currentEntry.getViewID().equals(id)) {
				menuItem.setSelection(true);
			}

			TreeItem[] subItems = treeItem.getItems();
			if (subItems.length > 0) {
				Menu parent = menuDropDown;
				if (!entry.isSelectable()) {
					parent = new Menu(menuDropDown.getParent().getShell(), SWT.DROP_DOWN);
					menuItem.setMenu(parent);
				}


				fillDropDownMenu(parent, subItems, indent + 1);
			}
		}
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private void swt_updateSideBarHitAreasY(SideBarEntrySWT[] entries) {
		for (int x = 0; x < entries.length; x++) {
			SideBarEntrySWT entry = entries[x];
			TreeItem treeItem = entry.getTreeItem();
			if (treeItem == null || treeItem.isDisposed()) {
				continue;
			}
			Rectangle itemBounds = entry.swt_getBounds();

			if ( itemBounds != null ){
				if (entry.isCloseable()) {
					Rectangle closeArea = (Rectangle) treeItem.getData("closeArea");
					if (closeArea != null) {
						closeArea.y = itemBounds.y + (itemBounds.height - closeArea.height)
								/ 2;
					}
				}

				List<MdiEntryVitalityImageSWT> vitalityImages = entry.getVitalityImages();
				for (MdiEntryVitalityImageSWT vitalityImage : vitalityImages) {
					if (!vitalityImage.isVisible()) {
						continue;
					}
					Image image = vitalityImage.getImage();
					if (image != null) {
						Rectangle bounds = vitalityImage.getHitArea();
						if (bounds == null) {
							continue;
						}
						bounds.y = (itemBounds.height - bounds.height) / 2;
					}
				}
			}
		}
	}

	private void
	swt_updateSideBarColors()
	{
		SWTSkinProperties skinProperties = skin.getSkinProperties();

		skinProperties.clearCache();

		bg = skinProperties.getColor("color.sidebar.bg");

		tree.setBackground(bg);

		tree.redraw();

		swt_updateSideBarColors( tree.getItems());
	}

	private void
	swt_updateSideBarColors(
		TreeItem[]	items )
	{
		for ( TreeItem ti: items){

			SideBarEntrySWT entry = (SideBarEntrySWT) ti.getData("MdiEntry");

			if ( entry != null ){

				entry.updateColors();

				entry.redraw();
			}

			swt_updateSideBarColors( ti.getItems());
		}
	}
	
	private void
	swt_redraw()
	{
		tree.redraw();
		
		swt_redraw( tree.getItems());
	}
	
	private void
	swt_redraw(
		TreeItem[]	items )
	{
		tree.redraw();
		
		for ( TreeItem ti: items){

			SideBarEntrySWT entry = (SideBarEntrySWT) ti.getData("MdiEntry");

			if ( entry != null ){

				entry.updateColors();

				entry.redraw();
			}

			swt_redraw( ti.getItems());
		}
	}

	protected int indexOf(final MdiEntry entry) {
		Object o = Utils.execSWTThreadWithObject("indexOf", new AERunnableObject() {
			@Override
			public Object runSupport() {
				TreeItem treeItem = ((SideBarEntrySWT) entry).getTreeItem();
				if (treeItem == null) {
					return -1;
				}
				TreeItem parentItem = treeItem.getParentItem();
				if (parentItem != null) {
					return parentItem.indexOf(treeItem);
				}
				return tree.indexOf(treeItem);
			}
		}, 500);
		if (o instanceof Number) {
			return ((Number) o).intValue();
		}
		return -1;
	}

	@Override
	public SideBarEntrySWT createHeader(String id, String titleID, String preferredAfterID) {
		SideBarEntrySWT oldEntry = getEntry(id);
		if (oldEntry != null) {
			return oldEntry;
		}

		SideBarEntrySWT entry = new SideBarEntrySWT(this, skin, id);
		entry.setSelectable(false);
		entry.setPreferredAfterID(preferredAfterID);
		entry.setTitleID(titleID);
		entry.setParentView(getParentView());

		setupNewEntry(entry, id, true, false);

		return entry;
	}

	private void setupNewEntry(final SideBarEntrySWT entry, final String id,
			final boolean expandParent, final boolean closeable) {
		//System.out.println("createItem " + id + ";" + entry.getParentID() + ";" + Debug.getCompressedStackTrace());
		addItem( entry );

		entry.setCloseable(closeable);
		entry.setParentSkinObject(soSideBarContents);
		entry.setDestroyOnDeactivate(false);

		if (SIDEBAR_HEADER_PLUGINS.equals(entry.getParentID())
				&& entry.getImageLeftID() == null) {
			entry.setImageLeftID("image.sidebar.plugin");
		}

		activityStart();
		
		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				try{
					_setupNewEntry(entry, id, expandParent, closeable);
				}finally{
					
					activityEnd();
				}
			}
		});
	}

	private void _setupNewEntry(SideBarEntrySWT entry, String id,
			boolean expandParent, boolean closeable) {
		
		if ( tree.isDisposed()){
			return;
		}
		
		String view_id = entry.getViewID();
		
		SideBarEntrySWT existing = getEntry( view_id );
		
		if ( existing != entry ){
		
				// entry has been deleted/replaced in the meantime
			
			return;
		}
		
		MdiEntry currentEntry = entry;
		String	currentID = id;
		
			// can't make TreeItems invisible :(
		
		while( true ){
			if ( MainMDISetup.hiddenTopLevelIDs.contains( currentID )){
				return;
			}
			if ( currentEntry == null ){
				break;
			}
			currentID = currentEntry.getParentID();
			if ( currentID == null ){
				break;
			}
			currentEntry = getEntry(currentID);
		}
		
		
		String parentID = entry.getParentID();
		MdiEntry parent = getEntry(parentID);
		
		TreeItem parentTreeItem = null;
		if (parent instanceof SideBarEntrySWT) {
			SideBarEntrySWT parentSWT = (SideBarEntrySWT) parent;
			parentTreeItem = parentSWT.getTreeItem();
			if (expandParent) {
				parentTreeItem.setExpanded(true);
			}
		}
		int index = -1;
		String preferredAfterID = entry.getPreferredAfterID();
		if (preferredAfterID != null) {
			if (preferredAfterID.length() == 0) {
				index = 0;
			} else {
				boolean hack_it = preferredAfterID.startsWith( "~" );

				if ( hack_it ){

						//hack - this means preferred BEFORE ID...

					preferredAfterID = preferredAfterID.substring(1);
				}

				MdiEntry entryAbove = getEntry(preferredAfterID);
				if (entryAbove != null) {
					index = indexOf(entryAbove);
					if ( hack_it ){

					}else{
						if (index >= 0) {
							index++;
						}
					}
					//System.out.println("ENTRY " + id + " is going to go below " + entryAbove.getId() + " at " + index);
				}
			}
		}

		if (index == -1 && parent == null) {
			index = 0;
			String[] order = getPreferredOrder();
			for (int i = 0; i < order.length; i++) {
				String orderID = order[i];
				if (orderID.equals(id)) {
					break;
				}
				MdiEntry entry2 = getEntry(orderID);
				if (entry2 != null) {
					int i2 = indexOf(entry2);
					if (i2 >= 0) {
						index = i2 + 1;
					}
				}
			}
		}

		if (GAP_BETWEEN_LEVEL_1 > 0 && parentTreeItem == null
				&& tree.getItemCount() > 0 && index != 0) {
			for (int i=0;i<GAP_BETWEEN_LEVEL_1;i++){
				createTreeItem(null,null, index);
				if (index >= 0) {
					index++;
				}
			}
		}
		TreeItem treeItem = createTreeItem( entry, parentTreeItem, index);
		if (treeItem != null) {

			triggerEntryLoadedListeners(entry);
		}
		if (GAP_BETWEEN_LEVEL_1 > 0 && parentTreeItem == null
				&& tree.getItemCount() > 1 && index == 0) {
			for (int i=0;i<GAP_BETWEEN_LEVEL_1;i++){
				createTreeItem(null,null, ++index);
			}
		}
	}

	private TreeItem createTreeItem(SideBarEntrySWT entry, TreeItem parentSwtItem, int index) {
		TreeItem treeItem;

		if (parentSwtItem == null ){
		
			if (tree.isDisposed()) {
				return null;
			}
			if (index >= 0 && index < tree.getItemCount()) {
				treeItem = new TreeItem(tree, SWT.NONE, index);
			} else {
				treeItem = new TreeItem(tree, SWT.NONE);
			}
		} else {
			if (parentSwtItem.isDisposed()) {
				
				return null;
			}
			
			if (index >= 0 && index < parentSwtItem.getItemCount()) {
				treeItem = new TreeItem( parentSwtItem, SWT.NONE, index);
			} else {
				treeItem = new TreeItem( parentSwtItem, SWT.NONE);
			}
		}

		if ( entry != null ){
			
			treeItem.setData("MdiEntry", entry);
			
			entry.setTreeItem(treeItem);
			
			/*
			TreeItem[] items = parentSwtItem==null?tree.getItems():parentSwtItem.getItems();
			
			boolean update = false;
			
			for ( int i=0;i<items.length;i++){
				
				TreeItem item = items[i];
				
				if ( !update ){
					
					update = item == treeItem;
				}
				
				if ( update ){
					
					SideBarEntrySWT e = (SideBarEntrySWT) item.getData("MdiEntry");
					
					if ( e != null ){
						
						e.setPosition( i );
					}
				}
			}
			*/
		}
		
		return treeItem;
	}

	@Override
	protected void setCurrentEntry(MdiEntrySWT entry){
		
		if (entry != null && btnCloseItem != null ){
			
			btnCloseItem.setDisabled( !entry.isCloseable());
		}
		
		super.setCurrentEntry(entry);
	}
	
	@Override
	public void showEntry(MdiEntry newEntry) {
		if (tree.isDisposed()) {
			return;
		}

		if (newEntry == null  ){
			return;
		}

		final SideBarEntrySWT oldEntry = getCurrentEntry();

		//System.out.println("showEntry " + newEntry.getId() + "; was " + (oldEntry == null ? "null" : oldEntry.getId()) + " via " + Debug.getCompressedStackTrace());
		if (oldEntry == newEntry) {
			triggerSelectionListener(newEntry, newEntry);
			return;
		}

		// show new
		setCurrentEntry((MdiEntrySWT)newEntry );

		if (oldEntry != null && oldEntry != newEntry) {
			oldEntry.redraw();
		}

		if (newEntry != null) {
			((BaseMdiEntry) newEntry).show();
		}

		// hide old
		if (oldEntry != null && oldEntry != newEntry) {
			oldEntry.hide();
			oldEntry.redraw();
		}

			// as this code isn't thread safe there is a chance we end up with multiple entries visible
			// (well actually it happens fairly frequently) - this results in other views being rendered
			// during switching which is nasty - hide anything that shouldn't be visible for the moment

		MdiEntrySWT[] entries = getEntries();
		for (MdiEntrySWT entry : entries) {
			if (entry == null) {
				continue;
			}
			if ( entry != getCurrentEntry()){

				SWTSkinObject obj = ((SideBarEntrySWT)entry).getSkinObjectMaster();

				if ( obj != null && obj.isVisible()){

					entry.hide();
					entry.redraw();
				}
			}
		}

		newEntry.redraw();

		triggerSelectionListener(newEntry, oldEntry);
	}

	@Override
	public SideBarEntrySWT createEntry(UISWTViewBuilderCore builder,
			boolean closeable) {

		String id = builder.getViewID();
		SideBarEntrySWT oldEntry = getEntry(id);
		if (oldEntry != null) {
			oldEntry.setDatasource(builder.getInitialDataSource());
			return oldEntry;
		}

		SideBarEntrySWT entry = new SideBarEntrySWT(this, skin, id);
		entry.setDatasource(builder.getInitialDataSource());
		entry.setPreferredAfterID(builder.getPreferredAfterID());
		entry.setParentView(getParentView());

		try {
			// setEventListener will create the UISWTView.
			// We need to have the entry available for the view to use if it wants
			addItem( entry );

			UISWTViewEventListener l = builder.createEventListener(entry);
			entry.setEventListener(l, builder, true);
			entry.setParentEntryID(builder.getParentEntryID());

			setupNewEntry(entry, id, false, closeable);
			
			if (l instanceof IViewAlwaysInitialize) {
				entry.build();
			}
		} catch (Exception e) {
			Debug.out(e);
			closeEntry(entry,false);
			return null;
		}

		return entry;
	}

	@Override
	public SideBarEntrySWT createEntryFromSkinRef(String parentEntryID, String id,
	                                       String configID, String title, ViewTitleInfo titleInfo, Object params,
	                                       boolean closeable, String preferredAfterID) {
		SideBarEntrySWT oldEntry = getEntry(id);
		if (oldEntry != null) {
			return oldEntry;
		}

		SideBarEntrySWT entry = new SideBarEntrySWT(this, skin, id);

		entry.setTitle(title);
		entry.setSkinRef(configID, params);
		if ( parentEntryID == null || !parentEntryID.isEmpty()){
			entry.setParentEntryID(parentEntryID);
		}
		entry.setViewTitleInfo(titleInfo);
		entry.setPreferredAfterID(preferredAfterID);
		entry.setParentView(getParentView());

		setupNewEntry(entry, id, false, closeable);

		return entry;
	}

	// @see com.biglybt.ui.swt.utils.UIUpdatable#updateUI()
	@Override
	public void updateUI() {
		Object[] views = pluginViews.toArray();
		for (int i = 0; i < views.length; i++) {
			try {
				UISWTViewCore view = (UISWTViewCore) views[i];
				Composite composite = view.getComposite();
				if ( composite == null ){
					continue;
				}
				if (composite.isDisposed()) {
					pluginViews.remove(view);
					continue;
				}
				if (composite.isVisible()) {
					view.triggerEvent(UISWTViewEvent.TYPE_REFRESH, null);
				}
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		if (tree.getSelectionCount() == 0) {
			return;
		}
		super.updateUI();
	}

	@Override
	protected boolean wasEntryLoadedOnce(String id) {
		@SuppressWarnings("deprecation")
		boolean loadedOnce = COConfigurationManager.getBooleanParameter("sb.once."
				+ id, false);
		return loadedOnce;
	}

	@Override
	protected void setEntryLoadedOnce(String id) {
		COConfigurationManager.setParameter("sb.once." + id, true);
	}

	public Font getHeaderFont() {
		return fontHeader;
	}

	protected Tree getTree() {
		return tree;
	}

	protected void
	requestAttention(
		SideBarEntrySWT	entry )
	{
		synchronized( attention_seekers ){

			if ( !attention_seekers.contains( entry )){

				attention_seekers.add( entry );
			}

			if ( attention_event == null ){

				attention_event =
					SimpleTimer.addPeriodicEvent(
						"SideBar:attention",
						SIDEBAR_ATTENTION_PERIOD,
						new TimerEventPerformer()
						{
							int	tick_count = 0;

							@Override
							public void
							perform(
								TimerEvent event )
							{
								tick_count++;

								final List<SideBarEntrySWT>	repaints = new ArrayList<>();

								synchronized( attention_seekers ){

									Iterator<SideBarEntrySWT> it = attention_seekers.iterator();

									while ( it.hasNext()){

										SideBarEntrySWT entry = it.next();

										if ( entry.isEntryDisposed()){

											it.remove();

										}else{

											if ( !entry.attentionUpdate( tick_count )){

												it.remove();
											}

											repaints.add( entry );
										}
									}

									if ( attention_seekers.size() == 0 ){

										TimerEventPeriodic ev = attention_event;

										if ( ev != null ){

											ev.cancel();

											attention_event = null;
										}
									}
								}

								if ( repaints.size() > 0 ){

									Utils.execSWTThread(
										new AERunnable()
										{
											@Override
											public void
											runSupport()
											{
												for ( SideBarEntrySWT entry: repaints ){

													entry.redraw();
												}
											}
										});
								}
							}
						});

			}
		}
	}

	private TreeItem
	getTreeItemAt(
		int		pos )
	{
		TreeItem[] items = tree.getItems();
		
		return( getTreeItemAt( items, new int[]{ pos }));
	}
	
	private TreeItem
	getTreeItemAt(
		TreeItem[]	items,
		int[]		pos )
	{
		for ( TreeItem ti: items ){
			
			if ( pos[0] == 0 ){
				
				return( ti );
				
			}else{
				
				pos[0]--;
				
				if ( ti.getExpanded()){
					
					TreeItem x = getTreeItemAt( ti.getItems(), pos );
					
					if ( x != null ){
						
						return( x );
					}
				}
			}
		}
		
		return( null );
	}
	
		// track entry additions and selection so we can switch to previous entry when one is closed

	private final Stack<String> entryViewHistory 	= new Stack<>();
	private final Stack<String> entryViewFuture 	= new Stack<>();

	@Override
	public void addItem(BaseMdiEntry entry) {
		super.addItem( entry );
		// addHistory( entry ); don't want this simply on addition, wait for selection
	}

	@Override
	protected void
	itemSelected(MdiEntry entry ){
		super.itemSelected( entry );
		addHistory( entry );
	}

	@Override
	public BaseMdiEntry closeEntryByID(String id, boolean user_initiated ) {
		MdiEntry currentBeforeClose = getCurrentEntry();

		BaseMdiEntry entry = super.closeEntryByID(id, user_initiated);
		if (entry == null || Utils.isDisplayDisposed()) {
			return entry;
		}
		
		String next = null;

		synchronized(entryViewHistory){
			entryViewHistory.remove( id );
			
			if (currentBeforeClose == null) {
				return entry;
			}

			if (currentBeforeClose != null && currentBeforeClose != entry) {
				// Closing an entry while another entry is selected.
				// Skip finding next in list
				return entry;
			}

			while( !entryViewHistory.isEmpty()){
				next = entryViewHistory.pop();
				if (entryExists(next)) {
					break;
				} else {
					next = null;
				}
			}
		}
		
		if ( next == null ){
			
				// OSX doesn't select a treeitem after closing an existing one
				// Force selection
			
			next = SideBar.SIDEBAR_SECTION_LIBRARY;
		}

		showEntryByID( next );
		
		return entry;
	}

	private void
	addHistory(
		MdiEntry	entry )
	{
		synchronized(entryViewHistory){
			String id = entry.getViewID();
			if ( !entryViewHistory.remove(id)){
				if ( !entryViewFuture.contains( id )){
					entryViewFuture.clear();
				}
			}
			if ( entry.isSelectable()){
				if ( !isInitialized()){
					entryViewHistory.clear();	// don't build up a load of history cruff during startup
				}
				entryViewHistory.push( id );
			}
		}
	}
	
	private void
	showNext()
	{
		MdiEntry current = getCurrentEntry();

		String next = null;
		
		synchronized( entryViewHistory ){
			
			while( !entryViewFuture.isEmpty()){
				
				String maybe_next = entryViewFuture.pop();
												
				if ( entryExists( maybe_next )){
				
					if ( current != null && current.getViewID().equals( maybe_next )){
						
						continue;
					}
					
					next = maybe_next;
					
					entryViewHistory.push( next );  // prepare for show-next so we keep future history
					
					break;
				}
			}
		}
		
		if ( next != null ){

			showEntryByID( next );
		}
	}
	
	private void
	showPrevious()
	{
		MdiEntry current = getCurrentEntry();
		
		String next = null;

		synchronized( entryViewHistory ){
			
			boolean	future_added = false;
			
			if ( current != null && entryExists( current.getViewID()) && current.isSelectable()){
				
				String cid = current.getViewID();
				
				if ( entryViewFuture.isEmpty() || !entryViewFuture.peek().equals( cid )){
				
					entryViewFuture.push( cid );
				}
				
				future_added = true;
			}
						
			while( !entryViewHistory.isEmpty()){
				
				String maybe_next = entryViewHistory.pop();
					
				if ( current != null && current.getViewID().equals( maybe_next) ){
										
					continue;
				}
				
				if ( entryExists( maybe_next )){

					next = maybe_next;
					
					if ( !future_added ){
					
						if ( entryViewFuture.isEmpty() || !entryViewFuture.peek().equals( next )){

							entryViewFuture.push( next );
						}
					}

					entryViewHistory.push( next );	// prepare for show-next so we keep future history
					
					break;
				}
			}
		}
				
		if ( next == null ){
			
			next = SideBar.SIDEBAR_SECTION_LIBRARY;
		}

		showEntryByID( next );
	}
	
	@Override
	public void generate(IndentWriter writer) {
		MdiEntrySWT[] entries = getEntries();
		for (MdiEntrySWT entry : entries) {
			if (entry == null) {
				continue;
			}

			if (!(entry instanceof AEDiagnosticsEvidenceGenerator)) {
				writer.println("Sidebar View (No Generator): " + entry.getViewID());
				try {
					writer.indent();

					writer.println("Parent: " + entry.getParentID());
					writer.println("Title: " + entry.getTitle());
				} catch (Exception e) {

				} finally {

					writer.exdent();
				}
			}

		}
	}

	// @see com.biglybt.ui.swt.debug.ObfuscateImage#obfuscatedImage(org.eclipse.swt.graphics.Image)
	@Override
	public Image obfuscatedImage(Image image) {

		Rectangle treeBounds = tree.getBounds();
		SideBarEntrySWT[] sideBarEntries = getEntries(
				new SideBarEntrySWT[0]);
		for (SideBarEntrySWT entry : sideBarEntries) {
			Rectangle entryBounds = entry.swt_getBounds();
			if (entryBounds != null && treeBounds.intersects(entryBounds)) {
				entry.obfuscatedImage(image);
			}
		}
		return image;
	}

	@Override
	public SideBarEntrySWT getEntry(String id) {
		return (SideBarEntrySWT) super.getEntry(id);
	}

	@Override
	public SideBarEntrySWT getCurrentEntry() {
		return (SideBarEntrySWT) super.getCurrentEntry();
	}

	@Override
	public SideBarEntrySWT getEntryBySkinView(Object skinView) {
		return (SideBarEntrySWT) super.getEntryBySkinView(skinView);
	}

	@Override
	protected SideBarEntrySWT createEntryByCreationListener(String id, Map<?, ?> autoOpenInfo) {
		return (SideBarEntrySWT) super.createEntryByCreationListener(id, autoOpenInfo);
	}
	
	private List<Runnable> 	idle_pending = new ArrayList<>();	
	private int				activity_count;
	
	private void
	activityStart()
	{
		synchronized( idle_pending ){
			
			activity_count++;
		}
	}
	
	private void
	activityEnd()
	{
		List<Runnable> to_run = null;
		
		synchronized( idle_pending ){
			
			activity_count--;
			
			if ( activity_count == 0 ){
				
				if ( !idle_pending.isEmpty()){
					
					to_run = new ArrayList<>( idle_pending );
					
					idle_pending.clear();
				}
			}
		}
		
		if ( to_run != null ){
			
			List<Runnable> x = to_run;

			Runnable doit = ()->{
				for ( Runnable r: x ){
					try{
						r.run();
					}catch( Throwable e ){
						Debug.out(e);
					}
				}
			};
						
			if ( Utils.isSWTThread()){
			
				Utils.getOffOfSWTThread( doit );
				
			}else{
				
				doit.run();
			}
		}
	}
	
	@Override
	public void 
	runWhenIdle(
		Runnable r )
	{
		synchronized( idle_pending ){
			
			if ( activity_count > 0 ){
								
				idle_pending.add( r );
				
				return;
			}
		}
		
		r.run();
	}
}
