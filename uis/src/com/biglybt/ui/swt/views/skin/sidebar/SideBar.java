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

import java.util.*;
import java.util.List;

import com.biglybt.ui.swt.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.mdi.BaseMDI;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.pif.PluginUISWTSkinObject;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl.SWTViewListener;
import com.biglybt.ui.swt.shells.main.MainMDISetup;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventListenerHolder;
import com.biglybt.ui.swt.pifimpl.UISWTViewImpl;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.IViewAlwaysInitialize;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;

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

	private SWTSkinObject soSideBarList;

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

	private Composite cPluginsArea;

	private List<UISWTViewCore> pluginViews = new ArrayList<>();
	private ParameterListener configShowSideBarListener;
	private ParameterListener configRedrawListener;
	private ParameterListener configBGColorListener;
	private SWTViewListener swtViewListener;

	public SideBar() {
		super();
		AEDiagnostics.addWeakEvidenceGenerator(this);
	}

	// @see SWTSkinObjectAdapter#skinObjectCreated(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectCreated(SWTSkinObject skinObject, Object params) {
		super.skinObjectCreated(skinObject, params);

		skin = skinObject.getSkin();

		soSideBarContents = (SWTSkinObjectContainer) skin.getSkinObject("sidebar-contents");
		soSideBarList = skin.getSkinObject("sidebar-list");
		soSideBarPopout = skin.getSkinObject("sidebar-pop");

		SWTSkinObjectContainer soSideBarPluginsArea = (SWTSkinObjectContainer) skin.getSkinObject("sidebar-plugins");
		if (soSideBarPluginsArea != null) {
			Composite composite = soSideBarPluginsArea.getComposite();
			cPluginsArea = new Composite(composite, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.marginHeight = layout.marginWidth = 0;
			layout.verticalSpacing = layout.horizontalSpacing = 0;
			cPluginsArea.setLayout(layout);
			cPluginsArea.setLayoutData(Utils.getFilledFormData());
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
				// F9 is standard Seamonkey, but doesn't work on OSX
				// Command Option T is standard on OSX
				// F7 works on both
				if (event.keyCode == SWT.F9
						|| event.keyCode == SWT.F7
						|| (event.keyCode == 116 && event.stateMask == (SWT.COMMAND | SWT.ALT))) {
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
			MenuItem menuItem = menuManager.addMenuItem("sidebar._end_", "UpdateWindow.close");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
				
			menuItem.addFillListener(
				new MenuItemFillListener() {
	
					@Override
					public void menuWillBeShown(MenuItem menu, Object data) {
						SideBarEntrySWT sbe = (SideBarEntrySWT)getCurrentEntrySWT();
	
						if ( sbe != null && !sbe.isCloseable()){
							
							menu.setVisible( false );
							
						}else {

							// Always show close menu because some OSes (Gnome on Debian)
							// cover up the "x" with an animated scrollbar
							menu.setVisible( true );
						}
					}
				});
	
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = (SideBarEntrySWT)getCurrentEntrySWT();
	
					if ( sbe != null ){
						
						sbe.close( true, true );
					}
				}
			});
		}
		
		{
			MenuItem menuItem = menuManager.addMenuItem("sidebar._end_", "menu.add.to.dashboard");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
	
			menuItem.addFillListener(
				new MenuItemFillListener() {
	
					@Override
					public void menuWillBeShown(MenuItem menu, Object data) {
						SideBarEntrySWT sbe = (SideBarEntrySWT)getCurrentEntrySWT();
	
						if ( sbe != null && sbe.getId().equals(  MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD )) {
							
							menu.setVisible( false );
							
						}else {
						
							menu.setVisible( sbe != null && sbe.canBuildStandAlone());
						}
					}
				});
	
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = (SideBarEntrySWT)getCurrentEntrySWT();
	
					if ( sbe != null ){
						
						MainMDISetup.getSb_dashboard().addItem( sbe );
					}
				}
			});
		}
		
		{
			MenuItem menuItem = menuManager.addMenuItem("sidebar._end_", "menu.pop.out");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
	
			menuItem.addFillListener(
				new MenuItemFillListener() {
	
					@Override
					public void menuWillBeShown(MenuItem menu, Object data) {
						SideBarEntrySWT sbe = (SideBarEntrySWT)getCurrentEntrySWT();
	
						menu.setVisible( sbe != null && sbe.canBuildStandAlone());
					}
				});
	
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					SideBarEntrySWT sbe = (SideBarEntrySWT)getCurrentEntrySWT();
	
					if ( sbe != null ){
						SkinnedDialog skinnedDialog =
								new SkinnedDialog(
										"skin3_dlg_sidebar_popout",
										"shell",
										null,	// standalone
										SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);
	
						SWTSkin skin = skinnedDialog.getSkin();
	
						SWTSkinObjectContainer cont = sbe.buildStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ));
	
						if ( cont != null ){
	
							skinnedDialog.setTitle( sbe.getTitle());
	
							skinnedDialog.open();
	
						}else{
	
							skinnedDialog.close();
						}
					}
				}
			});
		}
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
				UISWTInstanceImpl uiSWTinstance = (UISWTInstanceImpl) UIFunctionsManagerSWT.getUIFunctionsSWT().getUISWTInstance();
				uiSWTinstance.removeSWTViewListener(swtViewListener);
			} catch (Throwable ignore) {

			}
		}

		return super.skinObjectDestroyed(skinObject, params);
	}

	private void createSideBar() {
		if (soSideBarList == null) {
			return;
		}
		Composite parent = (Composite) soSideBarList.getControl();

		tree = new Tree(parent, SWT.FULL_SELECTION | SWT.V_SCROLL
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

		tree.getVerticalBar().addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent e) {
					if ( e.detail == SWT.None ){
						SideBarEntrySWT[] sideBarEntries = getEntries( new SideBarEntrySWT[0]);
						swt_updateSideBarHitAreasY(sideBarEntries);
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
								boolean selected = getCurrentEntrySWT() == entry
										&& entry.isSelectable();

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

									boolean selected = getCurrentEntrySWT() == entry
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
							if (entry != null && entry.isSelectable()) {
								Point cursorLocation = tree.toControl(event.display.getCursorLocation());
								if (lastCloseAreaClicked != null && lastCloseAreaClicked.contains(cursorLocation.x, cursorLocation.y)) {
									return;
								}

								showEntry(entry);
							} else if (getCurrentEntrySWT() != null) {
								TreeItem topItem = tree.getTopItem();

								// prevent "jumping" in the case where selection is off screen
								// setSelection would jump the item on screen, and then
								// showItem would jump back to where the user was.
								tree.setRedraw(false);
								TreeItem ti = ((SideBarEntrySWT) getCurrentEntrySWT()).getTreeItem();
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
									MdiEntryVitalityImage[] vitalityImages = entry.getVitalityImages();
									for (int i = 0; i < vitalityImages.length; i++) {
										SideBarVitalityImageSWT vitalityImage = (SideBarVitalityImageSWT) vitalityImages[i];
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
								treeItem.dispose();
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
							} else if (getCurrentEntrySWT() != entry && Constants.isOSX) {
								// showEntry(entry);  removed as we'll get a selection event if needed
							}

							if (entry != null) {
								MdiEntryVitalityImage[] vitalityImages = entry.getVitalityImages();
								for (int i = 0; i < vitalityImages.length; i++) {
									SideBarVitalityImageSWT vitalityImage = (SideBarVitalityImageSWT) vitalityImages[i];
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
									if (!entry.isSelectable() || event.x < 20) {
										// Note: On Windows, user can expand row by clicking the invisible area where the OS twisty would be
  									MdiEntry currentEntry = getCurrentEntry();
  									if (currentEntry != null
  											&& entry.getId().equals(currentEntry.getParentID())) {
  										showEntryByID(SIDEBAR_SECTION_LIBRARY);
  									}
  									entry.setExpanded(!wasExpanded);
  									wasExpanded = !wasExpanded;
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
							
							if (currentEntry != null && entry.getId().equals(currentEntry.getParentID())){
								
								showEntryByID(SIDEBAR_SECTION_LIBRARY);
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

		// For icons
		tree.addListener(SWT.MouseUp, treeListener);
		tree.addListener(SWT.MouseDown, treeListener);

		// For cursor
		tree.addListener(SWT.MouseMove, treeListener);

		// to disable collapsing
		tree.addListener(SWT.Collapse, treeListener);


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

				Point ptMouse = tree.toControl(e.display.getCursorLocation());

				int indent = END_INDENT ? tree.getClientArea().width - 1 : 0;
				TreeItem treeItem = tree.getItem(new Point(indent, ptMouse.y));
				if (treeItem == null) {
					return;
				}
				SideBarEntrySWT entry = (SideBarEntrySWT) treeItem.getData("MdiEntry");

				fillMenu(menuTree, entry, "sidebar");

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
						
						MdiEntry entry = getCurrentEntry();
						
						if ( entry != null && entry.isCloseable()){
						
							entry.close( true, true );
						}
					}
				});
			}
		}
	}

	private void createSideBarPluginViews() {
		if (cPluginsArea == null) {
			return;
		}
		UISWTInstanceImpl uiSWTinstance = (UISWTInstanceImpl) UIFunctionsManagerSWT.getUIFunctionsSWT().getUISWTInstance();

		if (uiSWTinstance == null) {
			return;
		}

		UISWTViewEventListenerHolder[] pluginViews = uiSWTinstance.getViewListeners(UISWTInstance.VIEW_SIDEBAR_AREA);
		for (UISWTViewEventListenerHolder l : pluginViews) {
			if (l != null) {
				try {
					UISWTViewImpl view = new UISWTViewImpl(l.getViewID(), UISWTInstance.VIEW_SIDEBAR_AREA, false);
					view.setEventListener(l, true);
					addSideBarView(view, cPluginsArea);
					cPluginsArea.getParent().getParent().layout(true, true);
				} catch (Exception e) {
					e.printStackTrace();
					// skip, plugin probably specifically asked to not be added
				}
			}
		}

		swtViewListener = new SWTViewListener() {

			@Override
			public void setViewAdded(final String parent, final String id,
			                         final UISWTViewEventListener l) {
				if (!parent.equals(UISWTInstance.VIEW_SIDEBAR_AREA)) {
					return;
				}
				Utils.execSWTThread(new AERunnable() {

					@Override
					public void runSupport() {
						try {
							UISWTViewImpl view = new UISWTViewImpl(id, parent, false);
							view.setEventListener(l, true);
							addSideBarView(view, cPluginsArea);
						} catch (Exception e) {
							e.printStackTrace();
							// skip, plugin probably specifically asked to not be added
						}
					}
				});
			}

			@Override
			public void setViewRemoved(final String parent, final String id,
			                           final UISWTViewEventListener l) {
				if (!parent.equals(UISWTInstance.VIEW_SIDEBAR_AREA)) {
					return;
				}
				Utils.execSWTThread(new AERunnable() {

					@Override
					public void runSupport() {
						try {
							for (UISWTViewCore view : SideBar.this.pluginViews) {
								if (l.equals(view.getEventListener())) {
									view.closeView();
								} else {
									if (l instanceof UISWTViewEventListenerHolder) {
										UISWTViewEventListener l2 = ((UISWTViewEventListenerHolder) l).getDelegatedEventListener(view);
										if (l2 != null && l2.equals(view.getEventListener())) {
											view.closeView();
										}
									}
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
							// skip, plugin probably specifically asked to not be added
						}
					}
				});
			}
		};
		uiSWTinstance.addSWTViewListener(swtViewListener);

		cPluginsArea.getParent().getParent().layout(true, true);
	}

	private void addSideBarView(UISWTViewImpl view, Composite cPluginsArea) {
		Composite parent = new Composite(cPluginsArea, SWT.NONE);
		GridData gridData = new GridData();
		gridData.grabExcessHorizontalSpace = true;
		gridData.horizontalAlignment = SWT.FILL;
		parent.setLayoutData(gridData);
		parent.setLayout(new FormLayout());
		//parent.setBackground(ColorCache.getRandomColor());
		//cPluginsArea.setBackground(ColorCache.getRandomColor());

		view.initialize(parent);
		parent.setVisible(true);

		Control[] children = parent.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control control = children[i];
			Object ld = control.getLayoutData();
			boolean useGridLayout = ld != null && (ld instanceof GridData);
			if (useGridLayout) {
				GridLayout gridLayout = new GridLayout();
				gridLayout.horizontalSpacing = 0;
				gridLayout.marginHeight = 0;
				gridLayout.marginWidth = 0;
				gridLayout.verticalSpacing = 0;
				parent.setLayout(gridLayout);
				break;
			} else if (ld == null) {
				control.setLayoutData(Utils.getFilledFormData());
			}
		}

		pluginViews.add(view);
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

			String id = entry.getId();
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
			MdiEntry currentEntry = getCurrentEntrySWT();
			if (currentEntry != null && currentEntry.getId().equals(id)) {
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

				MdiEntryVitalityImage[] vitalityImages = entry.getVitalityImages();
				for (int i = 0; i < vitalityImages.length; i++) {
					SideBarVitalityImageSWT vitalityImage = (SideBarVitalityImageSWT) vitalityImages[i];
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
	public MdiEntry createHeader(String id, String titleID, String preferredAfterID) {
		MdiEntry oldEntry = getEntry(id);
		if (oldEntry != null) {
			return oldEntry;
		}

		SideBarEntrySWT entry = new SideBarEntrySWT(this, skin, id, null);
		entry.setSelectable(false);
		entry.setPreferredAfterID(preferredAfterID);
		entry.setTitleID(titleID);

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

		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				_setupNewEntry(entry, id, expandParent, closeable);
			}
		});
	}

	private void _setupNewEntry(SideBarEntrySWT entry, String id,
			boolean expandParent, boolean closeable) {
		
		if ( tree.isDisposed()){
			return;
		}
		
		if ( getEntry( entry.getId()) != entry ){
		
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
				createTreeItem(null, index);
				if (index >= 0) {
					index++;
				}
			}
		}
		TreeItem treeItem = createTreeItem(parentTreeItem, index);
		if (treeItem != null) {
			treeItem.setData("MdiEntry", entry);
			entry.setTreeItem(treeItem);

			triggerEntryLoadedListeners(entry);
		}
		if (GAP_BETWEEN_LEVEL_1 > 0 && parentTreeItem == null
				&& tree.getItemCount() > 1 && index == 0) {
			for (int i=0;i<GAP_BETWEEN_LEVEL_1;i++){
				createTreeItem(null, ++index);
			}
		}
	}

	private TreeItem createTreeItem(Object parentSwtItem, int index) {
		TreeItem treeItem;

		if (parentSwtItem == null) {
			parentSwtItem = tree;
		}

		if (parentSwtItem instanceof Tree) {
			Tree tree = (Tree) parentSwtItem;
			if (tree.isDisposed()) {
				return null;
			}
			if (index >= 0 && index < tree.getItemCount()) {
				treeItem = new TreeItem(tree, SWT.NONE, index);
			} else {
				treeItem = new TreeItem(tree, SWT.NONE);
			}
		} else {
			if (((TreeItem) parentSwtItem).isDisposed()) {
				return null;
			}
			if (index >= 0 && index < ((TreeItem) parentSwtItem).getItemCount()) {
				treeItem = new TreeItem((TreeItem) parentSwtItem, SWT.NONE, index);
			} else {
				treeItem = new TreeItem((TreeItem) parentSwtItem, SWT.NONE);
			}
		}

		return treeItem;
	}

	@Override
	protected void setCurrentEntry(MdiEntrySWT entry){
		
		if ( btnCloseItem != null ){
			
			btnCloseItem.setDisabled( !entry.isCloseable());
		}
		
		super.setCurrentEntry(entry);
	}
	
	@Override
	public void showEntry(MdiEntry newEntry) {
		if (tree.isDisposed()) {
			return;
		}

		if (newEntry == null || !newEntry.isSelectable()) {
			return;
		}

		final SideBarEntrySWT oldEntry = (SideBarEntrySWT) getCurrentEntrySWT();

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

		MdiEntrySWT[] entries = getEntriesSWT();
		for (MdiEntrySWT entry : entries) {
			if (entry == null) {
				continue;
			}
			if ( entry != getCurrentEntrySWT()){

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
	public MdiEntry createEntryFromEventListener(String parentEntryID, String parentViewID,
	                                             UISWTViewEventListener l, String id, boolean closeable, Object datasource, String preferredAfterID) {

		MdiEntry oldEntry = getEntry(id);
		if (oldEntry != null) {
			return oldEntry;
		}

		SideBarEntrySWT entry = new SideBarEntrySWT(this, skin, id, parentViewID);
		try {
			// hack: setEventListner will create the UISWTView.
			// We need to have the entry available for the view to use
			// if it wants

			addItem( entry );

			entry.setEventListener(l, true);
			entry.setParentID(parentEntryID);
			entry.setDatasource(datasource);
			entry.setPreferredAfterID(preferredAfterID);
			setupNewEntry(entry, id, false, closeable);


			if (l instanceof IViewAlwaysInitialize) {
				entry.build();
			}
		} catch (Exception e) {
			Debug.out(e);
			entry.close(true);
			entry = null;
		}

		return entry;
	}

	// @see BaseMDI#createEntryFromSkinRef(java.lang.String, java.lang.String, java.lang.String, java.lang.String, ViewTitleInfo, java.lang.Object, boolean, java.lang.String)
	@Override
	public MdiEntry createEntryFromSkinRef(String parentID, String id,
	                                       String configID, String title, ViewTitleInfo titleInfo, Object params,
	                                       boolean closeable, String preferredAfterID) {

		MdiEntry oldEntry = getEntry(id);
		if (oldEntry != null) {
			return oldEntry;
		}

		SideBarEntrySWT entry = new SideBarEntrySWT(this, skin, id, null);

		entry.setTitle(title);
		entry.setSkinRef(configID, params);
		if ( parentID == null || !parentID.isEmpty()){
			entry.setParentID(parentID);
		}
		entry.setViewTitleInfo(titleInfo);
		entry.setPreferredAfterID(preferredAfterID);

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

	// @see MultipleDocumentInterfaceSWT#getEntryFromSkinObject(com.biglybt.ui.swt.pif.PluginUISWTSkinObject)
	@Override
	public MdiEntrySWT getEntryFromSkinObject(
			PluginUISWTSkinObject pluginSkinObject) {
		if (pluginSkinObject instanceof SWTSkinObject) {
			Control control = ((SWTSkinObject) pluginSkinObject).getControl();
			while (control != null && !control.isDisposed()) {
				Object entry = control.getData("BaseMDIEntry");
				if (entry instanceof BaseMdiEntry) {
					BaseMdiEntry mdiEntry = (BaseMdiEntry) entry;
					return mdiEntry;
				}
				control = control.getParent();
			}
		}
		return null;
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

										if ( entry.isDisposed()){

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

		// track entry additions and selection so we can switch to previous entry when one is closed

	private Stack<SideBarEntrySWT>	stack = new Stack<>();

	@Override
	public void addItem(MdiEntry entry) {
		super.addItem( entry );
		if ( entry instanceof SideBarEntrySWT ){
			synchronized( stack ){
				stack.remove( entry );
				if ( entry.isSelectable()){
					stack.push( (SideBarEntrySWT)entry );
				}
			}
		}
	}

	@Override
	protected void
	itemSelected(MdiEntry entry ){
		super.itemSelected( entry );
		if ( entry instanceof SideBarEntrySWT ){
			synchronized( stack ){
				stack.remove( entry );
				if ( entry.isSelectable()){
					stack.push( (SideBarEntrySWT)entry );
				}
			}
		}
	}

	@Override
	public void removeItem(MdiEntry entry) {
		super.removeItem( entry );
		if (Utils.isDisplayDisposed()) {
			return;
		}
		if ( entry instanceof SideBarEntrySWT ){

			MdiEntry current = getCurrentEntry();

			SideBarEntrySWT next = null;

			synchronized( stack ){

				stack.remove( entry );

				if ( 	current == null ||
						current == entry ){

					while( !stack.isEmpty()){
						next = stack.pop();
						if ( next.isDisposed()){
							next = null;
						}else{
							break;
						}
					}
				}
			}
			if ( next != null ){
				
				showEntry( next );
				
					// for some reason if we don't force this we can get spurious other selection events that
					// cause the desired entry to be switched away from :(
				
				try{
					next.getTreeItem().getParent().setSelection( next.getTreeItem());
					
				}catch( Throwable e ){
				}
			}
		}
	}

	@Override
	public void generate(IndentWriter writer) {
		MdiEntrySWT[] entries = getEntriesSWT();
		for (MdiEntrySWT entry : entries) {
			if (entry == null) {
				continue;
			}

			if (!(entry instanceof AEDiagnosticsEvidenceGenerator)) {
				writer.println("Sidebar View (No Generator): " + entry.getId());
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
}
