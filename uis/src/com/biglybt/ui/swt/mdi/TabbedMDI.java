/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.mdi;

import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.MenuBuildUtils.MenuBuilder;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventCancelledException;
import com.biglybt.ui.swt.shells.main.MainMDISetup;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.views.IViewAlwaysInitialize;
import com.biglybt.ui.swt.views.ViewManagerSWT;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.util.DataSourceUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;

public class TabbedMDI
	extends BaseMDI
	implements TabbedMdiInterface, AEDiagnosticsEvidenceGenerator,
	ParameterListener, ObfuscateImage
{
	private static final String KEY_AUTO_CLOSE = "TabbedMDI:autoclose";
	
	private CTabFolder tabFolder;

	private LinkedList<TabbedEntry>	select_history = new LinkedList<>();

	protected boolean minimized;

	private int iFolderHeightAdj;

	private final String props_prefix;

	private int minimumCharacters = 25;

	protected boolean isMainMDI;

	private Map mapUserClosedTabs;

	private boolean maximizeVisible = false;

	private boolean minimizeVisible = false;

	private TabbedMdiMaximizeListener maximizeListener;
	private ParameterListener paramFancyTabListener;
	private Composite topRight;
	private boolean destroyEntriesOnDeactivate = true;
	private Object dataSource;
	private boolean allowSubViews = true;

	public TabbedMDI() {
		super(null, UISWTInstance.VIEW_MAIN, null);
		AEDiagnostics.addWeakEvidenceGenerator(this);
		mapUserClosedTabs = new HashMap();
		isMainMDI = true;
		// Because we want to maintain UI state of main ui, and some naughty plugins
		// like I2P force close their tab when their view is destroyed.
		destroyEntriesOnDeactivate = false;
		this.props_prefix = "sidebar";
		setCloseableConfigFile("tabsauto.config");
	}

	/**
	 * @param pluginDataSourceType Only needed if every tab is based on the same datasource, such as {@link Download}
	 * @param viewID ID used to register views against this MDI
	 * @param parent SWT Composite to place widgets on
	 * @param props_prefix 
	 *    Prefix for loading MDIs properties (open history, etc)<br/>
	 *    Also used to get registered menu items.
	 *    So, should be one of MENU_ constants in {@link MenuManager}
	 * @param parentView This MDI's parent. For example, Parent=(Torrent's Peers View), this=(MDI showing Piece Map, Files, etc)
	 * @param dataSource DataSource to pass into each new entry. If null, entry's initial datasource will be used.
	 * 
	 * @implNote 
	 * viewID and props_prefix would be the same value if it weren't for legacy plugin code.
	 * Any new MDIs should use the same value.
	 */
	public TabbedMDI(Class<?> pluginDataSourceType, String viewID,
			String props_prefix, UISWTView parentView, Object dataSource) {
		super(pluginDataSourceType, viewID, parentView);
		this.dataSource = dataSource;
		this.props_prefix = props_prefix;
		minimumCharacters = 0;
		isMainMDI = false;
		setCloseableConfigFile(null);

		String key = this.props_prefix + ".closedtabs";

		mapUserClosedTabs = COConfigurationManager.getMapParameter(key, new HashMap());
		COConfigurationManager.addWeakParameterListener(this, false, key);
		
		String key2 = props_prefix + ".tabOrder";

		List<String> pref = BDecoder.decodeStrings(COConfigurationManager.getListParameter( key2, new ArrayList<>()));
		
		setPreferredOrder( pref.toArray( new String[0] ));
	}
	
	@Override
	public void buildMDI(Composite parent) {
		Utils.execSWTThread(() -> {
			SWTSkin skin = SWTSkinFactory.getInstance();
			SWTSkinObjectTabFolder soFolder = new SWTSkinObjectTabFolder(skin,
				skin.getSkinProperties(), props_prefix, "tabfolder.fill", parent);
			setMainSkinObject(soFolder);
			soFolder.addListener(this);
			skin.addSkinObject(soFolder);
		});
	}

	@Override
	public void buildMDI(SWTSkinObject skinObject) {
		setMainSkinObject(skinObject);
		skinObject.addListener(this);
	}

	@Override
	public Object skinObjectCreated(SWTSkinObject skinObject, Object params) {
		Object o = super.skinObjectCreated(skinObject, params);
		creatMDI();
		return o;
	}

	/* (non-Javadoc)
	 * @see BaseMDI#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	 */
	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {

		saveCloseables();
		
		MdiEntry[] entries = getEntries();
		for (MdiEntry entry : entries) {
			closeEntry(entry,false);
		}

		String key = props_prefix + ".closedtabs";
		COConfigurationManager.removeWeakParameterListener(this, key);
		COConfigurationManager.removeParameterListener("GUI_SWT_bFancyTab",
				paramFancyTabListener);

		return super.skinObjectDestroyed(skinObject, params);
	}

	private void creatMDI() {
		if (soMain instanceof SWTSkinObjectTabFolder) {
			tabFolder = ((SWTSkinObjectTabFolder) soMain).getTabFolder();
		} else {
			tabFolder = new CTabFolder((Composite) soMain.getControl(), SWT.TOP
					| SWT.BORDER | SWT.CLOSE);
		}

		iFolderHeightAdj = tabFolder.computeSize(SWT.DEFAULT, 0).y;

		if (isMainMDI) {
			paramFancyTabListener = new ParameterListener() {
				@Override
				public void parameterChanged(String parameterName) {
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							boolean simple = !COConfigurationManager.getBooleanParameter("GUI_SWT_bFancyTab");
							tabFolder.setSimple(simple);
						}
					});
				}
			};
			COConfigurationManager.addAndFireParameterListener("GUI_SWT_bFancyTab",
					paramFancyTabListener);
  		tabFolder.setSimple(!COConfigurationManager.getBooleanParameter("GUI_SWT_bFancyTab"));
		} else {
			tabFolder.setSimple(true);
			tabFolder.setMaximizeVisible(maximizeVisible);
			tabFolder.setMinimizeVisible(minimizeVisible);
			tabFolder.setUnselectedCloseVisible(false);
		}

		Display display = tabFolder.getDisplay();

		float[] hsb = tabFolder.getBackground().getRGB().getHSB();
		hsb[2] *= (Constants.isOSX) ? 0.9 : 0.97;
		tabFolder.setBackground(ColorCache.getColor(display, hsb));

		hsb = tabFolder.getForeground().getRGB().getHSB();
		hsb[2] *= (Constants.isOSX) ? 1.1 : 0.03;
		tabFolder.setForeground(ColorCache.getColor(display, hsb));

		tabFolder.setSelectionBackground(new Color[] {
			Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND),
			Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND),
			Colors.getSystemColor(display, SWT.COLOR_WIDGET_BACKGROUND)
		}, new int[] {
			10,
			90
		}, true);
		tabFolder.setSelectionForeground(Colors.getSystemColor(display, SWT.COLOR_LIST_FOREGROUND));

		if (minimumCharacters > 0) {
			tabFolder.setMinimumCharacters(minimumCharacters);
		}

		// XXX TVSWT_Common had SWT.Activate too
		tabFolder.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				CTabItem item = (CTabItem)event.item;
				
				TabbedEntry entry = (TabbedEntry)item.getData("TabbedEntry");
				
				if ( !isMainMDI && !soMain.isDisposed() && item.getData( KEY_AUTO_CLOSE ) == null ){
					String id = entry.getViewID();
				
					String key = props_prefix + ".selectedTab";
	
					COConfigurationManager.setParameter( key, id );
				}
				
				showEntry(entry);
			}
		});
		
		tabFolder.addMouseMoveListener(e -> {
			MdiEntryVitalityImageSWT vitalityImage = getVitalityImageAtPos(e.x, e.y);
			String tooltip = null;
			Cursor cursor = null;

			if (vitalityImage != null && vitalityImage.isVisible()) {
				if (vitalityImage.hasListeners()) {
					cursor = e.display.getSystemCursor(SWT.CURSOR_HAND);
				}
				tooltip = vitalityImage.getToolTip();
			}

			if (tabFolder.getCursor() != cursor) {
				tabFolder.setCursor(cursor);
			}
			CTabItem item = tabFolder.getItem(new Point(e.x, e.y));
			if (item != null && item.getToolTipText() != tooltip) {
				item.setToolTipText(tooltip);
			}
		});


		// CTabFolder focuses tab on mouse down before any of our events are fired,
		// so we are unable to detect if the user clicked a spot that didn't have
		// a vitality image, but does now that the tab is selected
		// Remove their listener and add it after ours.
		Listener[] mouseDownListeners = tabFolder.getListeners(SWT.MouseDown);
		for (Listener mouseDownListener : mouseDownListeners) {
			tabFolder.removeListener(SWT.MouseDown, mouseDownListener);
		}

		Utils.addSafeMouseUpListener(tabFolder,
				e -> e.widget.setData("downedVI", getVitalityImageAtPos(e.x, e.y)),
				e -> {
					MdiEntryVitalityImageSWT vi = (MdiEntryVitalityImageSWT) e.widget.getData(
							"downedVI");
					e.widget.setData("downedVI", null);
					if (e.button != 1) {
						return;
					}

					if (vi != null && vi.hasListeners()) {
						MdiEntryVitalityImageSWT viUp = getVitalityImageAtPos(e.x, e.y);
						if (vi != viUp) {
							// Case: 
							// 1) Mouse down on VitalityImage
							// 2) Move mouse outside bounds
							// 3) Mouse up within tabFolder (up outside tabfolder already eaten)
							return;
						}
						vi.triggerClickedListeners(e.x, e.y);
						return;
					}

					if (tabFolder.getMinimized()) {
						restore();
						// If the user clicked down on the restore button, and we restore
						// before the CTabFolder does, CTabFolder will minimize us again
						// There's no way that I know of to determine if the mouse is
						// on that button!

						// one of these will tell tabFolder to cancel
						e.button = 0;
						tabFolder.notifyListeners(SWT.MouseExit, null);
					}
				});

		for (Listener mouseDownListener : mouseDownListeners) {
			tabFolder.addListener(SWT.MouseDown, mouseDownListener);
		}

		tabFolder.addListener(SWT.MouseDoubleClick, e -> {
			if (!tabFolder.getMinimized() && tabFolder.getMinimizeVisible()) {
				minimize();
			}
		});

		tabFolder.addCTabFolder2Listener(new CTabFolder2Adapter() {
			@Override
			public void minimize(CTabFolderEvent event) {
				TabbedMDI.this.minimize();
			}


			@Override
			public void maximize(CTabFolderEvent event) {
				if (maximizeListener != null) {
					maximizeListener.maximizePressed();
				}
			}


			@Override
			public void restore(CTabFolderEvent event_maybe_null) {
				TabbedMDI.this.restore();
			}


			@Override
			public void close(CTabFolderEvent event) {
				TabbedEntry entry = (TabbedEntry) event.item.getData("TabbedEntry");
				UISWTViewBuilderCore builder = entry == null ? null
						: entry.getEventListenerBuilder();

				if ( entry != null ){
					entry.setUserInitiatedClose();
				}
				
				TabbedEntry prev = getPrevious( entry );

				if ( prev != null ){
					
					showEntry( prev );
				}
				
				// since showEntry is slightly delayed, we must slightly delay
				// the closing of the entry the user clicked.  Otherwise, it would close
				// first, and the first tab would auto-select (on windows), and then
				// the "next" tab would select.
				if (props_prefix != null) {
					Utils.execSWTThreadLater(0, () -> {
						String view_id = entry.getViewID();
						String key = props_prefix + ".closedtabs";

						Map closedtabs = COConfigurationManager.getMapParameter(key,
								new HashMap());

						if (closedtabs.containsKey(view_id)) {
							return;
						}

						closedtabs.put(view_id, entry.getTitle());

						// Set location if there is none.
						if (builder != null && builder.getPreferredAfterID() == null) {
							MdiEntrySWT[] entries = getEntries();
							if (entries.length > 1 && entries[0] == entry) {
								builder.setPreferredAfterID("~" + entries[1].getViewID());
							} else {
								for (int i = 1; i < entries.length; i++) {
									MdiEntrySWT e = entries[i];
									if (e == entry) {
										builder.setPreferredAfterID(entries[i - 1].getViewID());
										break;
									}
								}
							}
						}

						// this will trigger listener which will remove the tab
						COConfigurationManager.setParameter(key, closedtabs);
					});
				}
			}
		});

		if (isMainMDI) {
  		tabFolder.getDisplay().addFilter(SWT.KeyDown, new Listener() {
  			@Override
			  public void handleEvent(Event event) {
  				if ( tabFolder.isDisposed()){
  					return;
  				}
  				// Another window has control, skip filter
  				Control focus_control = tabFolder.getDisplay().getFocusControl();
  				if (focus_control != null
  						&& focus_control.getShell() != tabFolder.getShell()) {
  					return;
  				}

  				int key = event.character;
  				if ((event.stateMask & SWT.MOD1) != 0 && event.character <= 26
  						&& event.character > 0)
  					key += 'a' - 1;

  				// ESC or CTRL+F4 closes current Tab
  				if (key == SWT.ESC
  						|| (event.keyCode == SWT.F4 && event.stateMask == SWT.CTRL)) {
					  closeEntry(getCurrentEntry(),true);
  					event.doit = false;
  				} else if (event.keyCode == SWT.F6
  						|| (event.character == SWT.TAB && (event.stateMask & SWT.CTRL) != 0)) {
  					// F6 or Ctrl-Tab selects next Tab
  					// On Windows the tab key will not reach this filter, as it is
  					// processed by the traversal TRAVERSE_TAB_NEXT.  It's unknown
  					// what other OSes do, so the code is here in case we get TAB
  					if ((event.stateMask & SWT.SHIFT) == 0) {
  						event.doit = false;
  						selectNextTab(true);
  						// Shift+F6 or Ctrl+Shift+Tab selects previous Tab
  					} else if (event.stateMask == SWT.SHIFT) {
  						selectNextTab(false);
  						event.doit = false;
  					}
  				}else{
  					if (focus_control == tabFolder ){
  						TabbedEntry current = getCurrentEntry();
  						if ( current != null ){
  							if ( current.processAccelerator( event.character, event.stateMask )){
  								event.doit = false;
  							}
  						}
  					}
  				}
  			}
  		});
		}

		tabFolder.getTabHeight();
		final Menu menu = new Menu( tabFolder );
		tabFolder.setMenu(menu);
		MenuBuildUtils.addMaintenanceListenerForMenu(menu, new MenuBuilder() {
			@Override
			public void buildMenu(Menu root_menu, MenuEvent event) {
				Point cursorLocation = event.display.getCursorLocation();
				Point ptOnControl = tabFolder.toControl(cursorLocation.x,
						cursorLocation.y);
				if (ptOnControl.y > tabFolder.getTabHeight()) {
					return;
				}

				final CTabItem item = tabFolder.getItem(
						tabFolder.toControl(cursorLocation.x, cursorLocation.y));


				if ( item == null ){

					for (Object id : mapUserClosedTabs.keySet()) {
						
						final String view_id = (String) id;

						org.eclipse.swt.widgets.MenuItem mi = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);

						String title;

						Object oTitle = mapUserClosedTabs.get(id);
						if (oTitle instanceof String && ((String) oTitle).length() > 0) {
							title = (String) oTitle;
						} else {
							title = MessageText.getString(getViewTitleID(view_id));
						}

						// can still end up with a resource key here :( not sure why we don't always use the view-id

						if ( title.contains( "." )){
							String temp = MessageText.getString( title );
							if ( temp != null && !temp.isEmpty()){
								title = temp;
							}
						}

						mi.setText(title);

						mi.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event) {
								String key = props_prefix + ".closedtabs";

								Map closedtabs = COConfigurationManager.getMapParameter(key,
										new HashMap());

								if (closedtabs.containsKey(view_id)) {

									closedtabs.remove(view_id);

									COConfigurationManager.setParameter(key, closedtabs);
								}

								if ( !isMainMDI ){

									CTabItem[] items = tabFolder.getItems();

									List<String>	ids = new ArrayList<>();

									for ( CTabItem item: items ){

										TabbedEntry e = getEntryFromTabItem( item );

										if ( e != null ){
											ids.add( e.getViewID());
										}
									}

									if ( !ids.contains( view_id )){
										ids.add( view_id );
									}

									setPreferredOrder( ids.toArray( new String[0] ));

									String key2 = props_prefix + ".tabOrder";

									COConfigurationManager.setParameter( key2, ids );

								}
								showEntryByID(view_id);
							}
						});
					}

					if ( !mapUserClosedTabs.isEmpty()){
						
						new org.eclipse.swt.widgets.MenuItem(menu, SWT.SEPARATOR);
					}

					org.eclipse.swt.widgets.MenuItem miReset = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);

					miReset.setText( MessageText.getString( "menu.reset.tabs" ));
					
					miReset.addListener( SWT.Selection, (ev)->{
												
						mapUserClosedTabs.clear();
						
						String key = props_prefix + ".closedtabs";
						
						COConfigurationManager.setParameter(key, mapUserClosedTabs );
						
						setPreferredOrder( new String[0] );
						
						String key2 = props_prefix + ".tabOrder";

						COConfigurationManager.setParameter( key2, new ArrayList<>());
						
						buildTabs();
					});
					
					new org.eclipse.swt.widgets.MenuItem(menu, SWT.SEPARATOR);
				}

				TabbedEntry entry = null;
				if (item != null) {
					entry = getEntryFromTabItem(item);


					showEntry(entry);
				}

				fillMenu(menu, entry, props_prefix);

			}
		});

		if (SWT.getVersion() > 3600) {
			TabbedMDI_Ren.setupTabFolderRenderer(this, tabFolder);
		}

		if (minimizeVisible) {
			boolean toMinimize = ConfigurationManager.getInstance().getBooleanParameter(props_prefix + ".subViews.minimized");
			setMinimized(toMinimize);
		}

		if ( !isMainMDI ){
			
			addDragDropListeners();
		}
			// Create views registered to this tab

		buildTabs();
	}

    private void addDragDropListeners() {
  
        DragSource dragSource = new DragSource( tabFolder, DND.DROP_MOVE );
                
        dragSource.setTransfer(new Transfer[] {TextTransfer.getInstance()});
        
        dragSource.addDragListener(     		
    		new DragSourceAdapter() {
    			 
                private CTabItem item;
     
                @Override
                public void dragStart(DragSourceEvent event) {
                   
                    Point cursorLocation = tabFolder.getDisplay().getCursorLocation();
                    
                    Point p = tabFolder.toControl(cursorLocation);
                    
                    item = tabFolder.getItem( p );
                    
                    event.doit = item != null;
                }
     
                @Override
                public void dragSetData(final DragSourceEvent event) {
                	
                	if ( item != null ){
                		
                		event.data = ((TabbedEntry)item.getData("TabbedEntry")).getViewID();

                	}else{

                		event.data 	= null;
                		event.doit	= false;
                	}
                }
            });
        
        DropTarget dropTarget = new DropTarget( tabFolder, DND.DROP_MOVE);
        
        dropTarget.setTransfer(new Transfer[] {TextTransfer.getInstance()});
        
        dropTarget.addDropListener(
    		new DropTargetAdapter(){
            	
                private int pos;
     
                @Override
                public void dragOver( DropTargetEvent event ){
                	                    	                        
                    Point rel = tabFolder.toControl( new Point(event.x, event.y ));
                    
                    if ( rel.y > tabFolder.getTabHeight()){
                    	
                    	 event.detail = DND.DROP_NONE;
                    	 
                    	 pos = -1;
                    	 
                    }else{
                    	
                        event.detail = DND.DROP_MOVE;

                    	CTabItem item = tabFolder.getItem( rel );


                    	if ( item == null ){

                    		pos = tabFolder.getItemCount() - 1;

                    	}else{

                    		pos = tabFolder.indexOf( item );
                    	}
                    }
                }
     
                @Override
                public void drop(final DropTargetEvent event) {
                    
                	if ( pos < 0 ){
                		
                		return;
                	}
                	
					CTabItem[] items = tabFolder.getItems();

					List<String>	ids = new ArrayList<>();
					
					for ( CTabItem x: items ){
						
						ids.add( ((TabbedEntry)x.getData("TabbedEntry")).getViewID());
					}
					
					String target_id = (String)event.data;
					
					if ( ids.indexOf( target_id ) == pos ){
						
						return;
					}
					
					ids.remove( target_id );
					
					ids.add( pos, target_id );
															
					setPreferredOrder( ids.toArray( new String[0]));
					
					String key2 = props_prefix + ".tabOrder";

					COConfigurationManager.setParameter( key2, ids );
					
					buildTabs();
                }
            });
        
        tabFolder.addListener( SWT.Dispose, (ev)->{ dragSource.dispose(); dropTarget.dispose();});
    }
    
	private void
	buildTabs()
	{
		CTabItem[] items = tabFolder.getItems();
		
		for ( CTabItem item: items ){
			
			item.setData( KEY_AUTO_CLOSE, true );
		}
		
		for ( CTabItem item: items ){
			
			item.dispose();
		}

		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		
		List<UISWTViewBuilderCore> builders = vm.getBuilders(getViewID(),getDataSourceType());
		
		for (UISWTViewBuilderCore builder : builders){
			
			try{
				createEntry(builder, true);
				
			}catch (Exception ignore) {
			}	
		}
		
		if ( !isMainMDI ){
		
			Utils.execSWTThreadLater(0,()->{

				String key = props_prefix + ".selectedTab";
				
				String id = COConfigurationManager.getStringParameter( key, null );
				
				if ( id != null ){
				
					int index = indexOf( id );
					
					if ( index != -1 ){
						
						tabFolder.setSelection( index );
						
						return;
					}
				}
				
				if ( tabFolder.getItemCount() > 0 ){
					
					tabFolder.setSelection( 0 );
				}
			});
			
		}
	}
	
	private MdiEntryVitalityImageSWT getVitalityImageAtPos(int x, int y) {
		CTabItem item = tabFolder.getItem(new Point(x, y));
		if (item == null) {
			return null;
		}
		TabbedEntry entry = getEntryFromTabItem(item);
		if (entry == null) {
			return null;
		}
		List<MdiEntryVitalityImageSWT> vitalityImages = entry.getVitalityImages();
		for (MdiEntryVitalityImageSWT vitalityImage : vitalityImages) {
			if (!vitalityImage.isVisible()) {
				continue;
			}
			Rectangle hitArea = vitalityImage.getHitArea();
			if (hitArea != null && hitArea.contains(x, y)) {
				return vitalityImage;
			}
		}

		return null;
	}

	private String
	getViewTitleID(
		String	view_id )
	{
		String history_key = "swt.ui.table.tab.view.namecache." + view_id;

		String id = COConfigurationManager.getStringParameter( history_key, "" );

		if ( id.length() == 0 ){

			String test = view_id + ".title.full";

			if ( MessageText.keyExists( test )){

				return( test );
			}

			id = "!" + view_id + "!";
		}

		return( id );
	}



	private void minimize() {
		minimized = true;

		tabFolder.setMinimized(true);
		CTabItem[] items = tabFolder.getItems();
		String tt = MessageText.getString("label.click.to.restore");
		for (int i = 0; i < items.length; i++) {
			CTabItem tabItem = items[i];
			Utils.setTT(tabItem,tt);
			Control control = tabItem.getControl();
			if (control != null && !control.isDisposed()) {
				tabItem.getControl().setVisible(false);
			}
		}

		tabFolder.getParent().notifyListeners(SWT.Resize, null);

		showEntry(null);

		ConfigurationManager configMan = ConfigurationManager.getInstance();
		configMan.setParameter(props_prefix + ".subViews.minimized", true);
	}

	private void restore() {

		minimized = false;
		tabFolder.setMinimized(false);
		CTabItem selection = tabFolder.getSelection();
		if (selection != null) {
			TabbedEntry tabbedEntry = getEntryFromTabItem(selection);

			showEntry(tabbedEntry);

			/* Already done by TabbedEntry.swt_build
			Control control = selection.getControl();
			if (control == null || control.isDisposed()) {
				selectedView.initialize(tabFolder);
				selection.setControl(selectedView.getComposite());
				control = selection.getControl();
				triggerTabViewDataSourceChanged(selectedView, tv, new Object[][] {
					null,
					null
				});
			}
			selection.getControl().setVisible(true);
			*/
			tabbedEntry.updateUI( true );
		}

		if (tabFolder.getMaximizeVisible()) {
			CTabItem[] items = tabFolder.getItems();
			String tt = MessageText.getString("label.dblclick.to.min");

			for (int i = 0; i < items.length; i++) {
				CTabItem tabItem = items[i];
				Utils.setTT(tabItem,tt);
			}
		}

		tabFolder.getParent().notifyListeners(SWT.Resize, null);

		ConfigurationManager configMan = ConfigurationManager.getInstance();
		configMan.setParameter(props_prefix + ".subViews.minimized", false);
	}



	private void selectNextTab(boolean selectNext) {
		if (tabFolder == null || tabFolder.isDisposed()) {
			return;
		}

		final int nextOrPrevious = selectNext ? 1 : -1;
		int index = tabFolder.getSelectionIndex() + nextOrPrevious;
		if (index == 0 && selectNext || index == -2 || tabFolder.getItemCount() < 2) {
			return;
		}
		if (index == tabFolder.getItemCount()) {
			index = 0;
		} else if (index < 0) {
			index = tabFolder.getItemCount() - 1;
		}

		// instead of .setSelection, use showEntry which will ensure view de/activations
		CTabItem item = tabFolder.getItem(index);
		MdiEntry entry = getEntryFromTabItem(item);

		if (entry != null) {
			showEntry(entry);
		}
	}

	@Override
	protected boolean wasEntryLoadedOnce(String id) {
		@SuppressWarnings("deprecation")
		boolean loadedOnce = COConfigurationManager.getBooleanParameter("tab.once."
				+ id, false);
		return loadedOnce;
	}

	@Override
	protected void setEntryLoadedOnce(String id) {
		COConfigurationManager.setParameter("tab.once." + id, true);
	}

	private TabbedEntry
	getPrevious(
		TabbedEntry		current )
	{
		if ( select_history.remove(current)){

			if (select_history.size() > 0) {

					// we can only do the crud below if we're on the SWT thread and this isn't
					// always the case - fixing this properly is a bit of a job so hacking it atm
					// (specifically unloading the mlDHT plugin in classic UI triggers this)
				
				if ( Utils.isSWTThread()){
					
					final TabbedEntry next = select_history.getLast();
	
					if (!next.isEntryDisposed() && next != current) {
	
						// If tabfolder's selected entry is the one we are closing,
						// CTabFolder will try to move to next CTabItem.  Disable
						// this feature by moving tabfolder's selection away from us
						CTabItem[] items = tabFolder.getItems();
						for (int i = 0; i < items.length; i++) {
							CTabItem item = items[i];
							TabbedEntry scanEntry = getEntryFromTabItem(item);
							if (scanEntry == next) {
								tabFolder.setSelection(item);
								break;
							}
						}
	
						return( next );
					}
				}
			}
		}
		
		return( null );
	}
	
	@Override
	public void showEntry(final MdiEntry _newEntry) {
		if (_newEntry == null) {
			return;
		}

		TabbedEntry newEntry = (TabbedEntry)_newEntry;
		
		select_history.remove( newEntry );

		select_history.add( newEntry );

		if ( select_history.size() > 64 ){

			select_history.removeFirst();
		}

		MdiEntry oldEntry = getCurrentEntry();
		if (newEntry == oldEntry && oldEntry != null) {
			((BaseMdiEntry) newEntry).show();
			triggerSelectionListener(newEntry, newEntry);
			return;
		}

		if (oldEntry != null) {
			oldEntry.hide();
		}

		setCurrentEntry((MdiEntrySWT)newEntry );

		if (newEntry instanceof BaseMdiEntry) {
			((BaseMdiEntry) newEntry).show();
		}

		triggerSelectionListener(newEntry, oldEntry);
	}

	private TabbedEntry createEntryFromSkinRef(String id, String configID,
			String title, ViewTitleInfo titleInfo, Object params, boolean closeable,
			String preferredAfterID) {
		TabbedEntry oldEntry = getEntry(id);
		if (oldEntry != null) {
			return oldEntry;
		}

		TabbedEntry entry = new TabbedEntry(this, skin, id);
		entry.setTitle(title);
		entry.setDatasource(dataSource);
		entry.setSkinRef(configID, params);
		entry.setViewTitleInfo(titleInfo);
		entry.setDestroyOnDeactivate(destroyEntriesOnDeactivate);
		entry.setPreferredAfterID(preferredAfterID);
		entry.setCloseable(closeable);
		entry.setParentView(getParentView());

		setupNewEntry(entry);
		return entry;
	}

	/**
	 *
	 * @param preferedAfterID Not used for Tabs
	 */
	@Override
	public TabbedEntry createEntryFromSkinRef(
			@SuppressWarnings("unused") String parentEntryID, String id,
			String configID, String title, ViewTitleInfo titleInfo, Object params,
			boolean closeable, String preferedAfterID) {
		return createEntryFromSkinRef(id, configID, title, titleInfo, params,
				closeable, preferedAfterID);
	}

	@Override
	public TabbedEntry createEntry(UISWTViewBuilderCore builder,
			boolean closeable) {

		String id = builder.getViewID();
		if (isEntryClosedByUser(id)) {
			return null;
		}
		TabbedEntry oldEntry = getEntry(id);
		if (oldEntry != null) {
			oldEntry.setDatasource(builder.getInitialDataSource());
			return oldEntry;
		}

		TabbedEntry entry = new TabbedEntry(this, skin, id);
		// Set some defaults. Must be done before entry.setEventListener, as listener
		// might want to override
		entry.setDatasource(dataSource == null ? builder.getInitialDataSource(): dataSource);
		String preferredAfterID = builder.getPreferredAfterID();
		entry.setPreferredAfterID(preferredAfterID);
		entry.setDestroyOnDeactivate(destroyEntriesOnDeactivate);
		entry.setTitle(builder.getInitialTitle());
		entry.setCloseable(closeable);
		entry.setParentView(getParentView());

		try {
			// setEventListner will create the UISWTView.
			// We need to have the entry available for the view to use if it wants
			addItem(entry);

			UISWTViewEventListener l = builder.createEventListener(entry);
			entry.setEventListener(l, builder,true);
			// Do not set ParentEntry ID -- Tabbed MDI doesn't have parents
			//entry.setParentEntryID(parentEntryID);

			setupNewEntry(entry);

			addMenus( entry, id );

			if (l instanceof IViewAlwaysInitialize) {
				entry.build();
			}
		} catch (Exception e) {
			if (!(e instanceof UISWTViewEventCancelledException)) {
				Debug.out("Can't create " + builder.getViewID(), e);
			}
			closeEntry(entry,false);
			return null;
		}

		return entry;
	}

	protected BaseMdiEntry closeEntryByID(String id, boolean userInitiated) {

		TabbedEntry existing = getEntry(id);

		TabbedEntry prev  = getPrevious( existing );
		
		BaseMdiEntry entry = super.closeEntryByID(id, userInitiated);
		
		if (entry == null || Utils.isDisplayDisposed()) {
			
			return entry;
		}
		
		if ( userInitiated && prev != null ){
		
			showEntry(prev);
		}
		
		return( entry );
	}
	
	@Override
	public void updateUI() {
		if (getMinimized() || !isVisible()) {
			return;
		}
		super.updateUI();
	}

	private boolean isEntryClosedByUser(String id) {

		if (mapUserClosedTabs.containsKey(id)) {
			return true;
		}
		// TODO Auto-generated method stub
		return false;
	}

	private void setupNewEntry(TabbedEntry entry) {
		addItem( entry );	// we have to add this here otherwise duplicates can get added
		Utils.execSWTThreadLater(0, () -> swt_setupNewEntry(entry));
	}

	private void swt_setupNewEntry(TabbedEntry entry) {
		if (tabFolder == null || tabFolder.isDisposed()) {
			return;
		}

		String viewID = entry.getViewID();
		
		if ( getEntry( viewID ) != entry ){
			
			// entry has been deleted/replaced in the meantime
		
			return;
		}
		
		int index =  -1;
		String preferredAfterID = entry.getPreferredAfterID();
		if (preferredAfterID != null) {
			if (preferredAfterID.length() == 0) {
				index = 0;
			} else {
				boolean preferBefore = preferredAfterID.startsWith( "~" );

				if ( preferBefore ){
					preferredAfterID = preferredAfterID.substring(1);
				}

				index = indexOf(preferredAfterID);
				if (!preferBefore && index >= 0) {
					index++;
				}
			}
		}

		if ( index == -1 ){
			
			String[] order = getPreferredOrder();
			for (int i = 0; i < order.length; i++) {
				String orderID = order[i];
				if (orderID.equals(viewID)) {
					
					CTabItem[] items = tabFolder.getItems();
					Map<String,Integer>	map = new HashMap<>();
					
					for ( int j=0;j<items.length;j++){
						TabbedEntry e = getEntryFromTabItem( items[j] );
						if ( e != null ){
							map.put( e.getViewID(), j );
						}
					}
					
					for ( int j=i-1;j>=0;j--){
						Integer x = map.get( order[j] );
						if ( x != null ){
							index = x+1;
							break;
						}
					}
					
					if ( index == -1 ){
						for ( int j=i+1;j<order.length;j++){
							Integer x = map.get( order[j] );
							if ( x != null ){
								index = x;
								break;
							}
						}
					}
					
					break;
				}
			}
		}

		if (index < 0 || index >= tabFolder.getItemCount()) {
			index = tabFolder.getItemCount();
		}
		CTabItem cTabItem = new CTabItem(tabFolder, SWT.NONE, index);
		cTabItem.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (tabFolder.getItemCount() == 0) {
					setCurrentEntry( null );
				}
			}
		});
		cTabItem.setData("TabbedEntry", entry);
		entry.setSwtItem(cTabItem);

		if (tabFolder.getItemCount() == 1) {
  		Utils.execSWTThreadLater(0, new AERunnable() {

  			@Override
  			public void runSupport() {
  				if (getCurrentEntry() != null || tabFolder.isDisposed()) {
  					return;
  				}
  				CTabItem selection = tabFolder.getSelection();
  				if (selection == null) {
  					if (tabFolder.getItemCount() > 0) {
  					  selection = tabFolder.getItem(0);
					  } else {
  					  return;
					  }
  				}
  				TabbedEntry entry = getEntryFromTabItem(selection);
  				showEntry(entry);
  			}
  		});
		}
	}

	private int
	indexOf(
		MdiEntry	entry )
	{
		CTabItem[] items = tabFolder.getItems();
		
		for ( int i=0;i<items.length;i++){
			if ( getEntryFromTabItem( items[i] ) == entry ){
				
				return( i );
			}
		}
		return( -1 );
	}
		
	private int
	indexOf(
		String		viewID )
	{
		CTabItem[] items = tabFolder.getItems();
		
		for ( int i=0;i<items.length;i++){
			TabbedEntry entry = getEntryFromTabItem( items[i] );
			
			if ( entry != null && entry.getViewID().equals( viewID )){
				
				return( i );
			}
		}
		return( -1 );
	}
	
	protected TabbedEntry getEntryFromTabItem(CTabItem item) {
		if (item.isDisposed()) {
			return null;
		}
		return (TabbedEntry) item.getData("TabbedEntry");
	}

	@Override
	public String getUpdateUIName() {
		String name = "MDI";
		MdiEntry entry = getCurrentEntry();
		if (entry != null) {
			name += "-" + entry.getViewID();
		}
		return name;
	}

	@Override
	public void generate(IndentWriter writer) {
		MdiEntrySWT[] entries = getEntries();
		for (MdiEntrySWT entry : entries) {
			if (entry == null) {
				continue;
			}


			if (!(entry instanceof AEDiagnosticsEvidenceGenerator)) {
				writer.println("TabbedMdi View (No Generator): " + entry.getViewID());
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

	@Override
	public TabbedEntry createHeader(String id, String title, String preferredAfterID) {
		return null;
	}

	@Override
	public CTabFolder getTabFolder() {
		return tabFolder;
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#setMaximizeVisible(boolean)
	 */
	@Override
	public void setMaximizeVisible(final boolean visible) {
		maximizeVisible = visible;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (tabFolder == null || tabFolder.isDisposed()) {
					return;
				}
				tabFolder.setMaximizeVisible(visible);
			}
		});
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#setMinimizeVisible(boolean)
	 */
	@Override
	public void setMinimizeVisible(final boolean visible) {
		minimizeVisible = visible;
		if (minimizeVisible) {
			boolean toMinimize = ConfigurationManager.getInstance().getBooleanParameter(props_prefix + ".subViews.minimized");
			setMinimized(toMinimize);
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (tabFolder == null || tabFolder.isDisposed()) {
					return;
				}
				tabFolder.setMinimizeVisible(visible);
			}
		});
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#getMinimized()
	 */
	@Override
	public boolean getMinimized() {
		return minimized;
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#setMinimized(boolean)
	 */
	@Override
	public void setMinimized(final boolean minimized) {
		this.minimized = minimized;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (tabFolder == null || tabFolder.isDisposed()) {
					return;
				}

				if (minimized) {
					minimize();
				} else {
					restore();
				}
			}
		});
	}

	@Override
	public int getFolderHeight() {
		return iFolderHeightAdj;
	}

	@Override
	public void parameterChanged(String parameterName) {
		if (isDisposed()) {
			return;
		}

		mapUserClosedTabs = COConfigurationManager.getMapParameter(parameterName, new HashMap());

		for (Object id : mapUserClosedTabs.keySet()) {
			String view_id = (String) id;
			if (entryExists(view_id)) {
				closeEntryByID(view_id);
			}
		}
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#setTabbedMdiMaximizeListener(TabbedMdiMaximizeListener)
	 */
	@Override
	public void setTabbedMdiMaximizeListener(TabbedMdiMaximizeListener l) {
		maximizeListener = l;
	}

	@Override
	public void setDestroyEntriesOnDeactivate(boolean destroyEntriesOnDeactivate) {
		this.destroyEntriesOnDeactivate = destroyEntriesOnDeactivate;
		MdiEntrySWT[] entries = getEntries();
		for (MdiEntrySWT entry : entries) {
			entry.setDestroyOnDeactivate(destroyEntriesOnDeactivate);
		}
	}

	@Override
	public void setEntriesDataSource(Object newDataSource) {
		if (DataSourceUtils.areSame(newDataSource, dataSource)) {
			return;
		}

		dataSource = newDataSource;
		MdiEntry[] entries = getEntries();
		for (MdiEntry entry : entries) {
			entry.setDatasource(dataSource);
		}
		DownloadManager[] dms = DataSourceUtils.getDMs(dataSource);
		if (maximizeListener != null) {
			setMaximizeVisible(dms.length == 1);
		}
	}

	@Override
	public void setAllowSubViews(boolean allowSubViews) {
		this.allowSubViews = allowSubViews;
	}

	@Override
	public boolean getAllowSubViews() {
		return allowSubViews;
	}

	public boolean
	isEmpty()
	{
		return( getEntries().length == 0 && ( mapUserClosedTabs == null || mapUserClosedTabs.isEmpty()));
	}
	
	// @see com.biglybt.ui.swt.debug.ObfuscateImage#obfuscatedImage(org.eclipse.swt.graphics.Image)
	@Override
	public Image obfuscatedImage(Image image) {
		MdiEntry[] entries = getEntries();
		for (MdiEntry entry : entries) {
			if (entry instanceof ObfuscateImage) {
				ObfuscateImage oi = (ObfuscateImage) entry;
				image = oi.obfuscatedImage(image);
			}
		}
		return image;
	}

	@Override
	protected MdiEntry
	createEntryByCreationListener(String id, Map<?, ?> autoOpenInfo)
	{
		final TabbedEntry result = (TabbedEntry)super.createEntryByCreationListener(id, autoOpenInfo);

		if ( result != null ){
			
			addMenus( result, id );

		}

		return( result );
	}

	private void
	addMenus(
		TabbedEntry		entry,
		String			id )
	{
		PluginManager pm = CoreFactory.getSingleton().getPluginManager();
		PluginInterface pi = pm.getDefaultPluginInterface();
		UIManager uim = pi.getUIManager();
		MenuManager menuManager = uim.getMenuManager();
		
		{
			if ( !Utils.isAZ2UI()){
				
				MenuItem menuParentItem = menuManager.addMenuItem( id + "._end_", "menu.add.to");
				menuParentItem.setStyle(MenuItem.STYLE_MENU );
				menuParentItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		
				menuParentItem.addFillListener(
					new MenuItemFillListener() {
		
						@Override
						public void menuWillBeShown(MenuItem menu, Object data) {
		
								// pick up the right target - due to the confusion of multiple tab instances registering
								// the same menu entries the original registerer may well not be the one that should receive the event,
								// rather the one specified in the event is
							
							TabbedEntry	target = entry;
							
							if ( data instanceof Object[]) {
								Object[] odata = (Object[])data;
								if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
									target = (TabbedEntry)odata[0];
								}
							}
							
							menu.setVisible(target.canBuildStandAlone());
						}
					});
		
				MenuItem menuItemDashBoard = menuManager.addMenuItem( menuParentItem, "label.dashboard");
				
				menuItemDashBoard.addListener(new MenuItemListener() {
					@Override
					public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object data ) {
							
						TabbedEntry	target = entry;
						
						if ( data instanceof Object[]) {
							Object[] odata = (Object[])data;
							if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
								target = (TabbedEntry)odata[0];
							}
						}else if ( data instanceof TabbedEntry ){
							target = (TabbedEntry)data;
						}
						
						MainMDISetup.getSb_dashboard().addItem( target );
					}
				});
				
				MenuItem menuItemSidebar = menuManager.addMenuItem( menuParentItem, "label.sidebar");
				
				menuItemSidebar.addListener(new MenuItemListener() {
					@Override
					public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object data ) {
							
						TabbedEntry	target = entry;
						
						if ( data instanceof Object[]) {
							Object[] odata = (Object[])data;
							if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
								target = (TabbedEntry)odata[0];
							}
						}else if ( data instanceof TabbedEntry ){
							target = (TabbedEntry)data;
						}
						
						MainMDISetup.getSb_dashboard().addItemToSidebar( target );
					}
				});
				
				MenuItem menuItemRightbar = menuManager.addMenuItem( menuParentItem, "label.rightbar");
				
				menuItemRightbar.addListener(new MenuItemListener() {
					@Override
					public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object data ) {
							
						TabbedEntry	target = entry;
						
						if ( data instanceof Object[]) {
							Object[] odata = (Object[])data;
							if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
								target = (TabbedEntry)odata[0];
							}
						}else if ( data instanceof TabbedEntry ){
							target = (TabbedEntry)data;
						}
						
						MainMDISetup.getSb_dashboard().addItemToRightbar( target );
					}
				});
			}
		}
		
		{
			MenuItem menuParentItem = menuManager.addMenuItem( id + "._end_", "label.pop.out");
			menuParentItem.setStyle(MenuItem.STYLE_MENU );
			menuParentItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			
			menuParentItem.addFillListener(
				new com.biglybt.pif.ui.menus.MenuItemFillListener() {

					@Override
					public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {

						TabbedEntry	target = entry;
						
						if ( data instanceof Object[]) {
							Object[] odata = (Object[])data;
							if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
								target = (TabbedEntry)odata[0];
							}
						}
						
						menu.setVisible( target.canBuildStandAlone());
					}
				});

			MenuItem menuItemIndependent = menuManager.addMenuItem( menuParentItem, "menu.independent");
			MenuItem menuItemOnTop		 = menuManager.addMenuItem( menuParentItem, "menu.on.top");
					
			com.biglybt.pif.ui.menus.MenuItemListener listener = new com.biglybt.pif.ui.menus.MenuItemListener() {
				@Override
				public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {

					TabbedEntry	target = entry;
					
					if ( data instanceof Object[]) {
						Object[] odata = (Object[])data;
						if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
							target = (TabbedEntry)odata[0];
						}
					}else if ( data instanceof TabbedEntry ){
						target = (TabbedEntry)data;
					}

					popoutEntry( target, menu==menuItemOnTop );
				}
			};
			
			menuItemIndependent.addListener( listener );
			menuItemOnTop.addListener( listener );
		}
	}
	
	public boolean
	popoutEntry(
		MdiEntry	_entry,
		boolean		onTop )
	{
		TabbedEntry	target = (TabbedEntry)_entry;
		
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						onTop?UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell():null,
						SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = target.buildStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ));

		if ( cont != null ){

			String ds_str = "";
			Object ds = target.getDatasourceCore();
			DownloadManager dm = DataSourceUtils.getDM(ds);

			if (dm != null) {
				ds_str = dm.getDisplayName();
			}

			skinnedDialog.setTitle( target.getTitle() + (ds_str.length()==0?"":(" - " + ds_str )));

			
			String metrics_id;
			
				// hack - we don't want to remember shell metrics on a per-download basis
			
			if ( target.getDatasource() instanceof Download ){
				
				metrics_id = MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS;
				
			}else{
				
				metrics_id = target.getId();
			}
		
			skinnedDialog.open( "mdi.popout:" + metrics_id, true );

			return( true );
			
		}else{

			skinnedDialog.close();
			
			return( false );
		}
	}
	
	@Override
	public void fillMenu(Menu menu, final MdiEntry entry, String menuID) {

		super.fillMenu(menu, entry, menuID);

		if ( entry != null ){
			com.biglybt.pif.ui.menus.MenuItem[] menu_items = MenuItemManager.getInstance().getAllAsArray(
					entry.getViewID() + "._end_");

			if ( menu_items.length > 0 ){

				MenuBuildUtils.addPluginMenuItems(menu_items, menu, false, true,
						new MenuBuildUtils.MenuItemPluginMenuControllerImpl(new Object[] {
							entry
						}));
			}
		}
	}


	@Override
	public String getMenuIdPrefix() {
		return props_prefix + ".";
	}

	@Override
	protected void setCurrentEntry(MdiEntrySWT entry) {
		super.setCurrentEntry(entry);

		Utils.execSWTThread(this::swt_refreshVitality);
	}

	protected void swt_refreshVitality() {
		TabbedEntry currentEntry = getCurrentEntry();
		if (currentEntry == null) {
			return;
		}
		if (topRight != null) {
			topRight.dispose();
		}

		UISWTView parentView = getParentView();
		boolean canPopOut = ((parentView instanceof UISWTViewCore)
				&& ((UISWTViewCore) parentView).canBuildStandAlone());

		List<MdiEntryVitalityImageSWT> vitalityImages = currentEntry.getVitalityImages();
		if (vitalityImages.isEmpty() && !canPopOut) {
			topRight = null;
			return;
		}
		topRight = new Composite(tabFolder, SWT.NONE);
		RowLayout layout = new RowLayout(SWT.HORIZONTAL);
		layout.marginTop = layout.marginBottom = 0;
		layout.wrap = false;
		topRight.setLayout(layout);
		for (MdiEntryVitalityImageSWT vitalityImage : vitalityImages) {
			if (!vitalityImage.isVisible() || !vitalityImage.hasListeners()
					|| !vitalityImage.getShowOutsideOfEntry()) {
				continue;
			}
			Label label = new Label(topRight, SWT.CENTER);
			Image image = ImageLoader.getInstance().getImage(
					vitalityImage.getImageID());
			label.setImage(image);
			RowData rowData = new RowData();
			rowData.width = image.getBounds().width;
			rowData.height = tabFolder.getTabHeight();
			label.setLayoutData(rowData);

			label.setCursor(label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));

			Utils.setTT(label, vitalityImage.getToolTip());

			if (vitalityImage.hasListeners()) {
				Utils.addSafeMouseUpListener(label,
						event -> vitalityImage.triggerClickedListeners(0, 0));
			}
		}

		if (canPopOut) {
			Label label = new Label(topRight, SWT.CENTER);
			Image image = ImageLoader.getInstance().getImage("popout_window");
			label.setImage(image);
			RowData rowData = new RowData();
			rowData.width = image.getBounds().width;
			rowData.height = tabFolder.getTabHeight();
			label.setLayoutData(rowData);

			label.setCursor(label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));

			Utils.setTT(label, MessageText.getString("label.pop.out"));

			Utils.addSafeMouseUpListener(label, event -> {
				// From TabbedMDI.addMenus, but there's also Sidebar.addGeneralMenus which doesn't set datasource
				SkinnedDialog skinnedDialog = new SkinnedDialog(
						"skin3_dlg_sidebar_popout", "shell", null, // standalone
						SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

				SWTSkin skin = skinnedDialog.getSkin();

				SWTSkinObjectContainer cont = ((UISWTViewCore) parentView).buildStandAlone(
						(SWTSkinObjectContainer) skin.getSkinObject("content-area"));

				if (cont != null) {

					String ds_str = "";
					Object ds = parentView.getDataSource();
					DownloadManager dm = DataSourceUtils.getDM(ds);

					if (dm != null) {
						ds_str = dm.getDisplayName();
					}

					skinnedDialog.setTitle(((UISWTViewCore) parentView).getFullTitle()
							+ (ds_str.length() == 0 ? "" : (" - " + ds_str)));

					skinnedDialog.open( "mdi.popout:" + currentEntry.getId(), true );

				} else {

					skinnedDialog.close();
				}
			});
		}

		tabFolder.setTopRight(topRight);
	}


	@Override
	public TabbedEntry getEntry(String id) {
		return (TabbedEntry) super.getEntry(id);
	}

	@Override
	public TabbedEntry getCurrentEntry() {
		return (TabbedEntry) super.getCurrentEntry();
	}

	@Override
	public TabbedEntry getEntryBySkinView(Object skinView) {
		return (TabbedEntry) super.getEntryBySkinView(skinView);
	}
	
	@Override
	public void 
	runWhenIdle(
		Runnable r )
	{
		r.run();	// nothing smart needed here maybe
	}
}
