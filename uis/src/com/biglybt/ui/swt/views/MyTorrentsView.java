/*
 * Created on 30 juin 2003
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.json.simple.JSONObject;

import com.biglybt.core.Core;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tag.*;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.util.*;
import com.biglybt.core.util.TorrentUtils.PotentialTorrentDeletionListener;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableViewImpl;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.minibar.DownloadBar;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.utils.*;
import com.biglybt.ui.swt.views.configsections.ConfigSectionInterfaceTablesSWT;
import com.biglybt.ui.swt.views.piece.PieceMapView;
import com.biglybt.ui.swt.views.table.*;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.table.painted.TableRowPainted;
import com.biglybt.ui.swt.views.utils.CategoryUIUtils;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.ui.swt.widgets.TagCanvas;
import com.biglybt.ui.swt.widgets.TagCanvas.TagButtonTrigger;
import com.biglybt.util.JSONUtils;
import com.biglybt.ui.swt.widgets.TagPainter;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.pif.ui.tables.TableRowRefreshListener;
import com.biglybt.pif.ui.toolbar.UIToolBarActivationListener;

/** Displays a list of torrents in a table view.
 *
 * @author Olivier
 * @author TuxPaper
 *         2004/Apr/18: Use TableRowImpl instead of PeerRow
 *         2004/Apr/20: Remove need for tableItemToObject
 *         2004/Apr/21: extends TableView instead of IAbstractView
 *         2005/Oct/01: Column moving in SWT >= 3.1
 */
public class MyTorrentsView
       extends TableViewTab<DownloadManager>
       implements GlobalManagerListener,
                  ParameterListener,
                  DownloadManagerListener,
                  TagTypeListener,
                  TagListener,
                  KeyListener,
                  TableLifeCycleListener,
                  TableViewSWTPanelCreator,
                  TableSelectionListener,
                  TableViewSWTMenuFillListener,
                  TableRefreshListener,
                  TableViewFilterCheck.TableViewFilterCheckEx<DownloadManager>,
                  TableRowRefreshListener,
                  TableCountChangeListener,
                  TableExpansionChangeListener,
                  UIPluginViewToolBarListener,
                  PotentialTorrentDeletionListener
{
	private static final LogIDs LOGID = LogIDs.GUI;

	private static final Object KEY_DM_REMOVED_FROM_COMPLETE_TABLE_TIME = new Object();
	
	private static final AsyncDispatcher	dispatcher = new AsyncDispatcher();
	
	private static final TagManager tagManager = TagManagerFactory.getTagManager();

	
	private Core core;

  private GlobalManager globalManager;

  	// keep this listener separate class as there is confusion within the globalmanager
  	// if the same instance is registered as both a GlobalManagerListener and a GlobalManagerEventListener
  	// yes, I know

  private GlobalManagerEventListener gm_event_listener =
	  new GlobalManagerEventListener()
  	{
		@Override
		public void
		eventOccurred(
			GlobalManagerEvent event )
		{
			if ( event.getEventType() == GlobalManagerEvent.ET_REQUEST_ATTENTION ){

				DownloadManager dm = event.getDownload();

				if ( isOurDownloadManager( dm )){

					TableRowCore row = tv.getRow( dm );

					if ( row != null ){

						TableRowCore[] existing = tv.getSelectedRows();

						if ( existing != null ){

							for ( TableRowCore e: existing ){

								if ( e != row ){

									e.setSelected( false );
								}
							}
						}

						if ( !row.isSelected()){

							row.setSelected( true );
						}
						
						if ( !row.isVisible()){
								
							tv.showRow( row );
						}
					}
				}
			}
		}
  	};

  private boolean	supportsTabs;
  private Composite cTablePanel;
  private Font fontButton = null;
  protected Composite cTitleCategoriesAndTags;
  protected BubbleTextBox filterBox = null;
  private TimerEventPeriodic	txtFilterUpdateEvent;

  private String		lastSearchConstraintString;
  private TagConstraint	lastSearchConstraint;
  
  private Object	currentTagsLock = new Object();
  private Tag[]		_currentTags;
  private List<Tag>	allTags;
  private Set<Tag>	hiddenTags = new HashSet<>();	// tag-group tags filter

  	private long drag_drop_location_start = -1;
  	private TableRowCore[] drag_drop_rows = null;

	private boolean bDNDalwaysIncomplete;
	private TableViewSWT<DownloadManager> tv;
	private Composite cTableParentPanel;
	protected boolean viewActive;
	private TableSelectionListener defaultSelectedListener;

	private ViewUtils.ViewTitleExtraInfo	vtxi;
	
	private boolean	showTableTitle;
	protected boolean neverShowTagButtons;
	protected boolean neverShowCatButtons;
	private boolean	showCatButtons;
	private boolean	showTagButtons;
	
	private boolean rebuildListOnFocusGain = false;

	private Menu oldMenu;

	private boolean isCompletedOnly;
	private boolean isIncompletedOnly;

	private Class<?> forDataSourceType;

	private TagButtonTrigger buttonListener;

	protected boolean isEmptyListOnNullDS;

	private final Map<String,String>	removed_while_selected =
			new LinkedHashMap<String,String>(64,0.75f,true)
			{
				@Override
				protected boolean
				removeEldestEntry(
			   		Map.Entry<String,String> eldest)
				{
					return size() > 64;
				}
			};

	private Runnable rowRemovedRunnable = null;

	private volatile int[]	dmCounts = new int[3];
	private AtomicInteger	dmCountMutations	= new AtomicInteger(0);
	private volatile int	dmCountLast			= -1;

	final Set<TableRowCore> listRowsToRefresh = new HashSet<>();

	public MyTorrentsView( boolean supportsTabs ) {
		super("MyTorrentsView");
		this.supportsTabs = supportsTabs;
	}

	public MyTorrentsView(String propertiesPrefix, boolean supportsTabs) {
		super(propertiesPrefix);
		this.supportsTabs = supportsTabs;
	}

  /**
   * Initialize
   *
   * @param core
   * @param isSeedingView
   * @param basicItems
   */
  public
  MyTorrentsView(
  		Core				core,
  		String				tableID,
  		boolean 			isSeedingView,
  		TableColumnCore[]	basicItems,
		BubbleTextBox 		filterBox,
  		boolean				supportsTabs )
  {
		super("MyTorrentsView");
		this.filterBox = filterBox;
		filterBox.setTooltip(MessageText.getString("MyTorrentsView.filter.tooltip"));
		this.supportsTabs = supportsTabs;
		init(core, tableID, isSeedingView
				? DownloadTypeComplete.class : DownloadTypeIncomplete.class, basicItems);
  }

  // @see com.biglybt.ui.swt.views.table.impl.TableViewTab#initYourTableView()
  @Override
  public TableViewSWT<DownloadManager> initYourTableView() {
  	return tv;
  }

  // @see com.biglybt.ui.swt.views.table.impl.TableViewTab#tableViewTabInitComplete()
  @Override
  public void tableViewTabInitComplete() {
  	if (COConfigurationManager.getBooleanParameter("Library.showFancyMenu")) {
    	Composite tableComposite = tv.getComposite();
    	oldMenu = tableComposite.getMenu();
    	Menu menu = new Menu(tableComposite);
    	tableComposite.setMenu(menu);
			tableComposite.addMenuDetectListener(
					e -> menu.setData("MenuSource", e.detail));
    	menu.addMenuListener(new MenuListener() {

  			@Override
			  public void menuShown(MenuEvent e) {
				  Object oMenuSource = menu.getData("MenuSource");
				  int menuSource = (oMenuSource instanceof Number)
					  ? ((Number) oMenuSource).intValue() : SWT.MENU_MOUSE;
				  
  				if (!showMyOwnMenu(e, menuSource != SWT.MENU_KEYBOARD)) {
  					oldMenu.setVisible(true);
  				}
  			}

  			@Override
			  public void menuHidden(MenuEvent e) {
  			}
  		});
  	}
  	super.tableViewTabInitComplete();
  }

	protected boolean showMyOwnMenu(MenuEvent e, boolean isMouseEvent) {
		Display d = e.widget.getDisplay();
		if (d.isDisposed()) {
			return false;
		}

		final DownloadManager[] dms = getSelectedDownloads();

		boolean hasSelection = (dms.length > 0);

		if (!hasSelection) {
			return false;
		}
		Point cursorLocation = e.display.getCursorLocation();
		Composite tableComposite = tv.getTableComposite();
		Point pt = tableComposite.toControl(cursorLocation.x, cursorLocation.y);
		TableColumnCore column = tv.getTableColumnByOffset(pt.x);

		Point locationOnDiplay = new Point(cursorLocation.x - 5,
				cursorLocation.y - 16);
		if (!isMouseEvent) {
			TableRowPainted focusedRow = (TableRowPainted) tv.getFocusedRow();
			if (focusedRow != null) {
				Rectangle bounds = focusedRow.getDrawBounds();
				if (!bounds.contains(pt)) {
					locationOnDiplay = tableComposite.toDisplay(
							new Point(bounds.x, bounds.y + bounds.height - 1));
				}
			}
		}

		boolean isSeedingView = Download.class.equals(forDataSourceType)
				|| DownloadTypeComplete.class.equals(forDataSourceType);
		new TorrentMenuFancy(tv, isSeedingView, getComposite().getShell(), dms,
				tv.getTableID()).showMenu(locationOnDiplay, column, oldMenu);
		return true;
	}

	public TableViewSWT<DownloadManager> init(Core _core, String tableID,
			Class<?> _forDataSourceType, TableColumnCore[] basicItems) {

		forDataSourceType 	= _forDataSourceType;
		isCompletedOnly 	= forDataSourceType.equals(DownloadTypeComplete.class);
		isIncompletedOnly 	= forDataSourceType.equals(DownloadTypeIncomplete.class);

    tv = createTableView(forDataSourceType, tableID, basicItems);

    /*
     * 'Big' table has taller row height
     */
    if (getRowDefaultHeight() > 0) {
			tv.setRowDefaultHeightPX(getRowDefaultHeight());
		} else {
	    tv.setRowDefaultHeightEM(1);
		}

    core		= _core;
    this.globalManager 	= core.getGlobalManager();

    synchronized( currentTagsLock ){
	
	    if (_currentTags == null) {
				_currentTags = new Tag[] {
					CategoryManager.getCategory(Category.TYPE_ALL)
				};
	    }
    }
    
    tv.addLifeCycleListener(this);
    tv.setMainPanelCreator(this);
    tv.addSelectionListener(this, false);
    tv.addMenuFillListener(this);
    tv.addRefreshListener(this, false);
    if (tv.canHaveSubItems()) {
    	tv.addRefreshListener(this);
    	tv.addCountChangeListener(this);
    	tv.addExpansionChangeListener(this);
    }

    tv.addTableDataSourceChangedListener(new TableDataSourceChangedListener() {
			@Override
			public void tableDataSourceChanged(Object newDataSource) {
				if (newDataSource instanceof Tag[]) {
					//neverShowCatButtons = true;
					setCurrentTags((Tag[]) newDataSource);
					return;
				}

				if (newDataSource instanceof Object[]) {
					Object[] datasources = ((Object[]) newDataSource);
					Object firstDS = datasources.length > 0 ? datasources[0] : null;
					if (firstDS instanceof Tag) {
						Tag[] tags = new Tag[datasources.length];
						System.arraycopy(datasources, 0, tags, 0, datasources.length);
						setCurrentTags(tags);
						return;
					}else if ( firstDS instanceof TagWrapper ){
						Set<Tag>	tags = new HashSet<>();
						for ( Object o: datasources ){
							tags.add(((TagWrapper)o).getTag());
						}
						setCurrentTags(tags.toArray( new Tag[0] ));
					}
				}

				if ( newDataSource instanceof Tag ){
					//neverShowCatButtons = true;
					//neverShowTagButtons = true;
					
					Tag[] tag = new Tag[]{ (Tag) newDataSource };
					
					hiddenTags = new HashSet<>( Arrays.asList( tag ));
					
					setCurrentTags(tag );
				}

				if ( newDataSource instanceof TagGroup ){
					//neverShowCatButtons = true;
					TagGroup	tg = (TagGroup)newDataSource;
					setCurrentTagGroup( tg );
					
					tg.addListener(
						new TagGroupListener(){
							
							@Override
							public void tagRemoved(TagGroup group, Tag tag){
								setCurrentTagGroup( tg );
							}
							
							@Override
							public void tagAdded(TagGroup group, Tag tag){
								setCurrentTagGroup( tg );
							}
						}, false );
				}
				
				if (newDataSource == null && isEmptyListOnNullDS) {
					setCurrentTags(new Tag[] { });
				}
			}
		}, true);

    return tv;
	}

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				tableViewInitialized();
				break;
			case EVENT_TABLELIFECYCLE_DESTROYED:
				tableViewDestroyed();
				break;
		}
	}

  protected void 
  tableViewInitialized() 
	{
	  	showTableTitle = COConfigurationManager.getBooleanParameter( "Library.ShowTitle" );
	  
		tv.addKeyListener(this);
	
		createTabs();

		if (filterBox == null) {

			tv.enableFilterCheck((BubbleTextBox) null, this);

		} else {

			Composite mainWidget = filterBox.getMainWidget();
			Composite filterParent = mainWidget.getParent();

			Menu menuFilterHeader = getHeaderMenu();
			filterParent.setMenu(menuFilterHeader);
			Control[] children = filterParent.getChildren();
			for (Control control : children) {
				if (control != mainWidget) {
					control.setMenu(menuFilterHeader);
				}
			}

    	Composite comp = filterParent;
    	
    	Object x = null;
    	
    	while( comp != null ){
    		
    		x = comp.getData( "ViewUtils:ViewTitleExtraInfo" );
    		
    		if ( x != null ){
    			
    			break;
    		}
    		
    		comp = comp.getParent();
    	}
    	
		if ( x instanceof ViewUtils.ViewTitleExtraInfo ){
			
			vtxi = (ViewUtils.ViewTitleExtraInfo)x;
			
			vtxi.setCountProvider(
				tv.getComposite(),
				new ViewUtils.ViewTitleExtraInfo.CountProvider(){
					
					@Override
					public int[] getCounts(){
						
						int	mut = dmCountMutations.get();
								
						if ( mut != dmCountLast ){
							
							int active 	= 0;
							int	queued	= 0;
							
							Collection<DownloadManager> dms = tv.getDataSources();
							
							for ( DownloadManager dm: dms ){
								
								int state = dm.getState();
								
								if ( state == DownloadManager.STATE_DOWNLOADING || state == DownloadManager.STATE_SEEDING ){
									
									active++;
									
								}else if ( state == DownloadManager.STATE_QUEUED ){
									
									queued++;
								}
							}
							
							dmCounts = new int[]{ dms.size(), active, queued };
							
							dmCountLast = mut;
						}
						
						return( dmCounts );
					}
				});
		}
    }

    createDragDrop();

    dispatcher.dispatch(
    	AERunnable.create(
    		()->{
			    COConfigurationManager.addAndFireParameterListeners(new String[] {
						"DND Always In Incomplete",
						"User Mode",
						"Library.ShowTitle", 
						"Library.ShowCatButtons", 
						"Library.ShowTagButtons", 
						"Library.ShowTagButtons.CompOnly",
						"Library.ShowTagButtons.FiltersOnly",
						"Library.ShowTagButtons.ImageOverride",
						"Library.ShowTagButtons.Align",
						"Library.ShowTagButtons.Inclusive",
					}, this);
	
	
			    synchronized( currentTagsLock ){
				    if ( _currentTags != null ){
				    	for (Tag tag : _currentTags) {
				    		tag.addTagListener(this, false);
						}
				    }
			    }
			    
			    TagType ttManual = tagManager.getTagType(TagType.TT_DOWNLOAD_MANUAL);
			    TagType ttCat = tagManager.getTagType(TagType.TT_DOWNLOAD_CATEGORY);
			    ttManual.addTagTypeListener(this, false);
			    ttCat.addTagTypeListener(this, false);
	
			    TorrentUtils.addPotentialTorrentDeletionListener( this );
			    globalManager.addListener(this, false);
			    globalManager.addEventListener( gm_event_listener );
			    DownloadManager[] dms = globalManager.getDownloadManagers().toArray(new DownloadManager[0]);
			    for (int i = 0; i < dms.length; i++) {
						DownloadManager dm = dms[i];
						dm.addListener(this);
						if (!isOurDownloadManager(dm)) {
							dms[i] = null;
						}
					}
			    tv.addDataSources(dms);
			    tv.processDataSourceQueue();
			}));
	
    cTablePanel.layout();
  }

  private Menu
  getHeaderMenu()
  {
	  Composite composite = tv.getComposite();
	  Menu tableHeaderMenu = new Menu(composite.getShell(), SWT.POP_UP );

	  MenuItem	showItem = new MenuItem( tableHeaderMenu, SWT.CASCADE );
	  Messages.setLanguageText( showItem, "ConfigView.label.ui_switcher_button" );
	  Menu showMenu = new Menu( tableHeaderMenu );
	  
	  showItem.setMenu( showMenu );
	  
	  // show uptime

	  final MenuItem menuItemShowUptime = new MenuItem(showMenu, SWT.CHECK);
	  Messages.setLanguageText( menuItemShowUptime, "ConfigView.label.showuptime" );

	  menuItemShowUptime.addSelectionListener(new SelectionAdapter() {
		  @Override
		  public void widgetSelected(SelectionEvent e) {
			  COConfigurationManager.setParameter(
					  "MyTorrentsView.showuptime", menuItemShowUptime.getSelection());
		  }
	  });
	  
	  // selected download rates

	  final MenuItem menuItemShowRates = new MenuItem(showMenu, SWT.CHECK);
	  Messages.setLanguageText( menuItemShowRates, "label.show.selected.rates" );

	  menuItemShowRates.addSelectionListener(new SelectionAdapter() {
		  @Override
		  public void widgetSelected(SelectionEvent e) {
			  COConfigurationManager.setParameter(
					  "MyTorrentsView.showrates", menuItemShowRates.getSelection());
		  }
	  });
	  
	  // show title
	  
	  final MenuItem menuItemShowTitle = new MenuItem(showMenu, SWT.CHECK);
	  Messages.setLanguageText( menuItemShowTitle, "menu.show.title" );

	  menuItemShowTitle.addSelectionListener(new SelectionAdapter() {
		  @Override
		  public void widgetSelected(SelectionEvent e) {
			  COConfigurationManager.setParameter(
					  "Library.ShowTitle", menuItemShowTitle.getSelection());
		  }
	  });

	  // show category buttons

	  final MenuItem menuItemShowCatBut = new MenuItem(showMenu, SWT.CHECK);
	  Messages.setLanguageText( menuItemShowCatBut, "ConfigView.label.show.cat.but" );

	  menuItemShowCatBut.addSelectionListener(new SelectionAdapter() {
		  @Override
		  public void widgetSelected(SelectionEvent e) {
			  COConfigurationManager.setParameter(
					  "Library.ShowCatButtons", menuItemShowCatBut.getSelection());
		  }
	  });


	  // show tag buttons

	  final MenuItem menuItemShowTagBut = new MenuItem(showMenu, SWT.CHECK);
	  Messages.setLanguageText( menuItemShowTagBut, "ConfigView.label.show.tag.but" );

	  menuItemShowTagBut.addSelectionListener(new SelectionAdapter() {
		  @Override
		  public void widgetSelected(SelectionEvent e) {
			  COConfigurationManager.setParameter(
					  "Library.ShowTagButtons", menuItemShowTagBut.getSelection());
		  }
	  });

	  
	  // search
	  
	  MenuItem	searchItem = new MenuItem( tableHeaderMenu, SWT.CASCADE );
	  Messages.setLanguageText( searchItem, "LoggerView.filter" );
	  Menu searchMenu = new Menu( tableHeaderMenu );
	  
	  searchItem.setMenu( searchMenu );
	  
	  MenuItem searchActive = new MenuItem( searchMenu, SWT.PUSH );
	  Messages.setLanguageText(searchActive, "dialog.active.color" );
	  searchActive.addListener( SWT.Selection, (ev)->{
		  Shell shell = getComposite().getShell();
		  RGB res = Utils.showColorDialog( shell, Utils.getConfigColor( "table.filter.active.colour", Colors.fadedBlue ).getRGB());

		  if ( res != null ){

			  Utils.setConfigColor( "table.filter.active.colour", ColorCache.getColor( shell.getDisplay(), res ) );
		  }
	  });
	  

	  final MenuItem searchHistoryEnable = new MenuItem(searchMenu, SWT.CHECK);
	  Messages.setLanguageText( searchHistoryEnable, "label.enable.history" );

	  searchHistoryEnable.addSelectionListener(new SelectionAdapter() {
		  @Override
		  public void widgetSelected(SelectionEvent e) {
			  COConfigurationManager.setParameter(
					  "table.filter.history.enabled", searchHistoryEnable.getSelection());
		  }
	  });
	  
	  MenuItem menuEnableSimple;
	  
	  if ( Utils.isAZ3UI()){
	  
		  new MenuItem( tableHeaderMenu, SWT.SEPARATOR );
	
		  	// enable simple views
	
		  String rr = MessageText.getString( "ConfigView.section.security.restart.title" );
	
		  menuEnableSimple = new MenuItem(tableHeaderMenu, SWT.CHECK);
	
		  menuEnableSimple.setText( MessageText.getString( "ConfigView.section.style.EnableSimpleView" ) + " (" + rr + ")" );
	
		  menuEnableSimple.addSelectionListener(new SelectionAdapter() {
			  @Override
			  public void widgetSelected(SelectionEvent e) {
				  COConfigurationManager.setParameter(
						  "Library.EnableSimpleView", menuEnableSimple.getSelection());
			  }
		  });
	  }else{
		  
		  menuEnableSimple = null;
	  }
	  
	  new MenuItem( tableHeaderMenu, SWT.SEPARATOR );

	  MenuItem mi = new MenuItem( tableHeaderMenu, SWT.PUSH );

	  mi.setText( MessageText.getString( "menu.library.options"));

	  mi.addListener( SWT.Selection, (ev)->{
		  UIFunctions uif = UIFunctionsManager.getUIFunctions();

		  if ( uif != null ){

			  JSONObject args = new JSONObject();

			  args.put( "select", ConfigSectionInterfaceTablesSWT.REFID_SECTION_LIBRARY);

			  String args_str = JSONUtils.encodeToJSON( args );

			  uif.getMDI().showEntryByID(
					  MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
					  ConfigSectionInterfaceTablesSWT.SECTION_ID + args_str );
		  }
	  });


	  // hooks

	  tableHeaderMenu.addMenuListener(new MenuListener() {
		  @Override
		  public void menuShown(MenuEvent e) {
			  menuItemShowUptime.setSelection(COConfigurationManager.getBooleanParameter( "MyTorrentsView.showuptime" ));
			  menuItemShowRates.setSelection(COConfigurationManager.getBooleanParameter( "MyTorrentsView.showrates" ));
			  menuItemShowTitle.setSelection(COConfigurationManager.getBooleanParameter( "Library.ShowTitle" ));
			  menuItemShowCatBut.setSelection(COConfigurationManager.getBooleanParameter( "Library.ShowCatButtons" ));
			  menuItemShowTagBut.setSelection(COConfigurationManager.getBooleanParameter( "Library.ShowTagButtons" ));

			  menuItemShowCatBut.setEnabled( !neverShowCatButtons );
			  menuItemShowTagBut.setEnabled( !neverShowTagButtons );

			  searchHistoryEnable.setSelection(COConfigurationManager.getBooleanParameter( "table.filter.history.enabled", true ));

			  if ( menuEnableSimple != null ){
			  
				  menuEnableSimple.setSelection(COConfigurationManager.getBooleanParameter( "Library.EnableSimpleView" ));
			  }
		  }

		  @Override
		  public void menuHidden(MenuEvent e) {
		  }
	  });

	  return( tableHeaderMenu );
  }

  protected void tableViewDestroyed() {
    tv.removeKeyListener(this);

  	Utils.execSWTThread(new SWTRunnable() {
			@Override
			public void runWithDisplay(Display display) {
				if ( fontButton != null ){
					FontUtils.uncache( fontButton );
					fontButton		= null;
				}
			}
		});
  	
    dispatcher.dispatch(
       	AERunnable.create(
       		()->{
			    Object[] dms = globalManager.getDownloadManagers().toArray();
			    for (int i = 0; i < dms.length; i++) {
						DownloadManager dm = (DownloadManager) dms[i];
						dm.removeListener(this);
					}
			
			    synchronized( currentTagsLock ){
					if (_currentTags != null) {
						for (Tag tag : _currentTags) {
							tag.removeTagListener(this);
						}
					}
			    }
			    
			    TagType ttManual = tagManager.getTagType(TagType.TT_DOWNLOAD_MANUAL);
			    TagType ttCat = tagManager.getTagType(TagType.TT_DOWNLOAD_CATEGORY);
			    ttManual.removeTagTypeListener(MyTorrentsView.this);
			    ttCat.removeTagTypeListener(MyTorrentsView.this);
			    TorrentUtils.removePotentialTorrentDeletionListener( this );
			    globalManager.removeListener(this);
			    globalManager.removeEventListener( gm_event_listener );
				COConfigurationManager.removeParameterListeners(
					new String[]{
						"DND Always In Incomplete",
						"User Mode",
						"Library.ShowTitle",
						"Library.ShowCatButtons",
						"Library.ShowTagButtons",
						"Library.ShowTagButtons.CompOnly",
						"Library.ShowTagButtons.FiltersOnly",
						"Library.ShowTagButtons.ImageOverride",
						"Library.ShowTagButtons.Align",
						"Library.ShowTagButtons.Inclusive" },
					this);
       		}));
  }


  // @see com.biglybt.ui.swt.views.table.TableViewSWTPanelCreator#createTableViewPanel(org.eclipse.swt.widgets.Composite)
  @Override
  public Composite createTableViewPanel(Composite composite) {

	composite.setData( "MyTorrentsView.instance", this );
    GridData gridData;
    cTableParentPanel = new Composite(composite, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.horizontalSpacing = 0;
    layout.verticalSpacing = 0;
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    cTableParentPanel.setLayout(layout);
    if (composite.getLayout() instanceof GridLayout) {
    	cTableParentPanel.setLayoutData(new GridData(GridData.FILL_BOTH));
    }

    cTablePanel = new Composite(cTableParentPanel, SWT.NULL);

    cTablePanel.addListener(SWT.Activate, new Listener() {
		@Override
		public void handleEvent(Event event) {
			viewActive = true;
	    updateSelectedContent();
	    //refreshIconBar();
		}
	});
    cTablePanel.addListener(SWT.Deactivate, new Listener() {
		@Override
		public void handleEvent(Event event) {
			viewActive = false;
			// don't updateSelectedContent() because we may have switched
			// to a button or a text field, and we still want out content to be
			// selected
		}
	});

    gridData = new GridData(GridData.FILL_BOTH);
    cTablePanel.setLayoutData(gridData);

    layout = new GridLayout(1, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.verticalSpacing = 0;
    layout.horizontalSpacing = 0;
    cTablePanel.setLayout(layout);

    cTablePanel.layout();
    return cTablePanel;
  }

  private void createTabs() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_createTabs();
			}
		});
  }

  private void destroyTabs() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
			 	if (cTitleCategoriesAndTags != null && !cTitleCategoriesAndTags.isDisposed()) {
			  		Utils.disposeComposite(cTitleCategoriesAndTags, false);
			  	}
			}
		});
  }
  
  private void swt_createTabs() {

    boolean catButtonsDisabled = neverShowCatButtons;
    if ( !catButtonsDisabled){
    	catButtonsDisabled = !COConfigurationManager.getBooleanParameter( "Library.ShowCatButtons" );
    }

    List<Tag> tags_to_show = new ArrayList<>();

    boolean tagButtonsDisabled = neverShowTagButtons;
    if ( !tagButtonsDisabled){
    	tagButtonsDisabled = !COConfigurationManager.getBooleanParameter( "Library.ShowTagButtons" );

    	if ( !tagButtonsDisabled ){
    		if ( !isCompletedOnly ){
    			tagButtonsDisabled = COConfigurationManager.getBooleanParameter( "Library.ShowTagButtons.CompOnly" );
    		}
    	}
    }

    boolean showAll = Utils.isAZ2UI();
    if (!tagButtonsDisabled) {
    	boolean filterOnly = COConfigurationManager.getBooleanParameter( "Library.ShowTagButtons.FiltersOnly" );
    	
    	ArrayList<Tag> tagsManual = new ArrayList<>(
    			tagManager.getTagType(
    					TagType.TT_DOWNLOAD_MANUAL).getTags());
    	
    	for (Tag tag : tagsManual) {
    		
    		boolean show;
    		
    		if ( filterOnly ){
    			
    			show = tag.getFlag( Tag.FL_IS_FILTER );
    			
    		}else{
    			
    			show = showAll || tag.isVisible();
    		}
    		if ( show ){
    			
    			if (!hiddenTags.contains(tag)) {
    				
    				tags_to_show.add(tag);
    			}
    		}
    	}
    }

    if (!catButtonsDisabled) {

    	// no point in showing any cat buttons if we're in a category library view as they are
    	// exclusive and selecting any other cat button is pointless

    	boolean hideAllCats = false;

    	for ( Tag t: hiddenTags ){
    		if ( t.getTagType().getTagType() == TagType.TT_DOWNLOAD_CATEGORY ){
    			hideAllCats = true;
    		}
    	}

    	if ( !hideAllCats ){
    		ArrayList<Tag> tagsCat = new ArrayList<>(
    				tagManager.getTagType(
    						TagType.TT_DOWNLOAD_CATEGORY).getTags());
    		if (showAll) {
    			tags_to_show.addAll(tagsCat);
    		} else {
    			for (Tag tag : tagsCat) {
    				if (tag.isVisible()) {
    					tags_to_show.add(tag);
    				}
    			}
    		}
    	}
    }

    tags_to_show = TagUtils.sortTags( tags_to_show );

   	buildHeaderArea();

    if ( tags_to_show.size() > 0 || showTableTitle ){
    	
    	buildCatAndTag(tags_to_show);
    	
    } else if (cTableParentPanel != null && !cTableParentPanel.isDisposed()) {
    	
  		cTableParentPanel.layout();
  	}
  }

	private void 
	buildHeaderArea()
	{
		if ( cTitleCategoriesAndTags == null ){
			
			cTitleCategoriesAndTags = new Composite(cTableParentPanel, SWT.NONE);

			cTitleCategoriesAndTags.moveAbove(null);
			
			RowLayout rowLayout = new RowLayout();
			
			rowLayout.marginTop = 2;
			rowLayout.marginBottom = 1;
			rowLayout.marginLeft = 3;
			rowLayout.marginRight = 3;
			rowLayout.spacing = 3;
			rowLayout.wrap = true;

		    cTitleCategoriesAndTags.setLayout(rowLayout);

			Composite filterParent = filterBox == null ? null : filterBox.getMainWidget().getParent();
			if ( filterParent != null ){
					// inherit the background of the search filter - best that can be done to make things look ok
				Color background = filterParent.getBackground();
				if ( background != null ){
					cTitleCategoriesAndTags.setBackground( background );
					cTableParentPanel.setBackground( background );
				}
			}
			
			cTableParentPanel.setMenu(getHeaderMenu());

		    if ( Constants.isOSX ){

		    		/* bug on OSX whereby the table is allowing menu-detect events to fire both on the table itself and the composite it
		    		 * sits on - this results in the header-area menu appearing after a menu appears for the table itself
		    		 * Doesn't happen on 10.6.8 but observed to happen on 10.9.4
		    		 */

			    cTableParentPanel.addListener(
						SWT.MenuDetect,
						new Listener() {

							@Override
							public void
							handleEvent(
								Event event )
							{
								Display display = cTableParentPanel.getDisplay();

								Point pp_rel = display.map( null, cTableParentPanel, event.x, event.y );

								Control hit = Utils.findChild(cTableParentPanel, pp_rel.x, pp_rel.y );

								event.doit = hit == cTableParentPanel;
							}
						});
		    }

			tv.enableFilterCheck(filterBox, this);
			
		}else if ( cTitleCategoriesAndTags.isDisposed()){
			
			return;
		}else{
		  	
		  	Utils.disposeComposite(cTitleCategoriesAndTags, false);
		}
		
		int align = COConfigurationManager.getIntParameter("Library.ShowTagButtons.Align" );
		
		int	swtAlign;
		
		if ( align == 0 ){
			if ( showTableTitle ){
				swtAlign = SWT.CENTER;
			}else{
				swtAlign = SWT.RIGHT;
			}
		}else if ( align == 1 ){
			swtAlign = SWT.LEFT;
		}else if ( align == 2 ){
			swtAlign = SWT.CENTER;
		}else{
			swtAlign = SWT.RIGHT;
		}
		
		GridData gridData = new GridData(swtAlign, SWT.CENTER, true, false);
		cTitleCategoriesAndTags.setLayoutData(gridData);
	}

  /**
   * @since 3.1.1.1
	 */
	private void buildCatAndTag(List<Tag> tags) {

		if ( cTitleCategoriesAndTags == null	|| cTitleCategoriesAndTags.isDisposed()){
			return;
		}

		Label titleLab = null;
		
		if ( showTableTitle && ( isCompletedOnly || isIncompletedOnly )){
			titleLab = new Label( cTitleCategoriesAndTags, SWT.NULL );
			titleLab.setLayoutData( new RowData());
			titleLab.setText( MessageText.getString( isCompletedOnly?"MySeedersView.header":"MyTorrentsView.header" ));
			titleLab.setMenu( getHeaderMenu());	
			if ( Utils.isAZ3UI()){
				FontUtils.setBold( titleLab );
			}
			titleLab.addPaintListener((ev)->{
				Label l = (Label)ev.widget;
						
				String key = "MTV:title:fg";
				
				if ( Colors.isBlackTextReadable( ev.gc.getBackground())){
					
					Color c = (Color)l.getData( key );
					
					if ( c != null ){
						
						if ( !c.isDisposed()){
							
							l.setForeground( c );
						}
					}
					
					l.setData( key, null );
					
				}else{
					
					if ( l.getData( key ) == null ){
						
						l.setData( key, l.getForeground());
					}
					l.setForeground( Colors.white );
				}
			});
		}
		
		if (tags.size() == 0 ){
			cTableParentPanel.layout();
			return;
		}

		Label spacer = null;

		allTags = tags;

		if ( buttonListener == null) {

			buttonListener = new TagButtonTrigger() {

				@Override
				public void tagButtonTriggered(TagPainter painter, int stateMask,
						boolean longPress) {
					Tag tag = painter.getTag();
					if (longPress) {
						handleLongPress(painter, tag);
						return;
					}

					boolean add = (stateMask & SWT.MOD1) != 0;

					boolean isSelected = painter.isSelected();

					if (isSelected) {
						removeTagFromCurrent(tag);
						painter.setSelected(!isSelected);
					} else {
						if (add) {
							Category catAll = CategoryManager.getCategory(Category.TYPE_ALL);

							if (tag.equals(catAll)) {
								setCurrentTags(catAll);
							} else {
								synchronized (currentTagsLock) {
									Tag[] newTags = new Tag[_currentTags.length + 1];
									System.arraycopy(_currentTags, 0, newTags, 0,
											_currentTags.length);
									newTags[_currentTags.length] = tag;

									newTags = (Tag[]) removeFromArray(newTags, catAll);

									setCurrentTags(newTags);
								}
							}
							painter.setSelected(!isSelected);
						} else {
							setCurrentTags(tag);
							setSelection(painter.getControl().getParent());
						}
					}
				}

				private void setSelection(Composite parent) {
					if (parent == null) {
						return;
					}
					List<Tag> selectedTags = new ArrayList<>();

					Tag[] currentTags = getCurrentTags();
					if (currentTags != null) {
						selectedTags.addAll(Arrays.asList(currentTags));
					}

					Control[] controls = parent.getChildren();
					for (Control control : controls) {
						if (!(control instanceof TagCanvas)) {
							continue;
						}
						TagPainter painter = ((TagCanvas) control).getTagPainter();
						boolean selected = selectedTags.remove(painter.getTag());
						painter.setSelected(selected);
					}
				}

				private void handleLongPress(TagPainter painter, Tag tag) {
					if (tv == null) {
						return;
					}
					Object[] ds = tv.getSelectedDataSources().toArray();

					if (tag instanceof Category) {
						TorrentUtil.assignToCategory(ds, (Category) tag);
						return;
					}

					boolean doAdd = false;
					for (Object obj : ds) {

						if (obj instanceof DownloadManager) {

							DownloadManager dm = (DownloadManager) obj;

							if (!tag.hasTaggable(dm)) {
								doAdd = true;
								break;
							}
						}
					}

					boolean do_it = true;
					
					boolean[] auto = tag.isTagAuto();
					
					if ( auto.length >= 2 ){
						if ( 	( doAdd && auto[0] ) ||
								( !doAdd && auto[1] )){
									
							do_it = false;
						}
					}
					
					if ( do_it ){
						
						try{
							tag.addTaggableBatch( true );
						
							for (Object obj : ds) {
		
								if (obj instanceof DownloadManager) {
		
									DownloadManager dm = (DownloadManager) obj;
		
									if (doAdd) {
										tag.addTaggable(dm);
									} else {
										tag.removeTaggable(dm);
									}
								}
							}
						}finally{
							
							tag.addTaggableBatch( false );
						}
	
						// Quick Visual
						boolean wasSelected = painter.isSelected();
						painter.setGrayed(true);
						painter.setSelected(true);
	
						Utils.execSWTThreadLater(200, () -> {
							painter.setGrayed(false);
							painter.setSelected(wasSelected);
						});
					}
				}

				@Override
				public Boolean tagSelectedOverride(Tag tag) {
					return null;
				}
			};
		}

		TagGroup currentGroup = null;
				
		for ( final Tag tag: tags ){
			
			boolean isCat = (tag instanceof Category);

			TagGroup tg = tag.getGroupContainer();
			
			if ( tg != currentGroup && currentGroup != null && !currentGroup.getTags().isEmpty()){
								
				Divider div = new Divider( cTitleCategoriesAndTags, SWT.NULL );
				
  				RowData rd = new RowData();
  				
  				div.setLayoutData(rd);
			}

			currentGroup = tg;

			TagCanvas button = new TagCanvas(cTitleCategoriesAndTags, tag, false, true);
			TagPainter painter = button.getTagPainter();
			button.setTrigger(buttonListener);
			painter.setCompact(true,
					COConfigurationManager.getBooleanParameter(
							"Library.ShowTagButtons.ImageOverride"));

			if (isCat) {
				if (spacer == null) {
					spacer = new Label(cTitleCategoriesAndTags, SWT.NONE);
					RowData rd = new RowData();
					rd.width = 8;
					spacer.setLayoutData(rd);
					spacer.moveAbove(null);
					if ( titleLab != null ){
						titleLab.moveAbove(spacer);
					}
				}
				button.moveAbove(spacer);
			}
			
			button.addKeyListener(this);
			if ( fontButton == null) {
				fontButton = FontUtils.cache( FontUtils.getFontWithStyle(button.getFont(), SWT.NONE, 0.8f));
			}
			button.setFont(fontButton);

			if (isCurrent(tag)) {
				painter.setSelected(true);
			}

			Menu menu = new Menu( button );

			button.setMenu( menu );

			if (isCat) {
				CategoryUIUtils.setupCategoryMenu(menu, (Category) tag);
			} else {
				TagUIUtils.createSideBarMenuItemsDelayed(menu, tag);
			}
		}

		cTableParentPanel.layout(true, true);
	}

	public boolean isOurDownloadManager(DownloadManager dm) {
		if (!isInCurrentTags(dm )) {
			return false;
		}

		if (Download.class.equals(forDataSourceType)) {
			return true;
		}

		boolean bCompleted = dm.isDownloadComplete(bDNDalwaysIncomplete);
		boolean bOurs = (bCompleted && isCompletedOnly)
				|| (!bCompleted && !isCompletedOnly);

		//System.out.println("ourDM? " + tv.getTableID() + "; " + dm.getDisplayName()
		//		+ "; Complete=" + bCompleted + ";Ours=" + bOurs + ";bc"
		//		+ dm.getStats().getDownloadCompleted(false) + ";"
		//		+ dm.getStats().getDownloadCompleted(true));

		return bOurs;
	}

	@Override
	public boolean 
	filterCheck(
		DownloadManager 	dm, 
		String 				sLastSearch, 
		boolean 			bRegexSearch) 
	{
		if ( dm == null ){
			return( false );
		}
		
		boolean bOurs;
		if (sLastSearch.length() > 0) {
			
			if ( !bRegexSearch ){
				
				try{
					TagConstraint constraint = lastSearchConstraint;
					
					if ( constraint == null || !sLastSearch.equals( lastSearchConstraintString )){
						
						lastSearchConstraintString = sLastSearch;
												
						constraint = lastSearchConstraint = tagManager.compileConstraint( sLastSearch );
					}
					
					if ( constraint != null && constraint.getError() == null ){
						
						return( constraint.testConstraint( dm ));
					}
				}catch( Throwable e ){		
				}
			}
			
			try {
				String	comment = dm.getDownloadState().getUserComment();
				if ( comment == null ){
					comment = "";
				}

				String[][] name_mapping = {
					{
						"",
						dm.getDisplayName()
					},
					{
						"t:",
						"", 	// defer (index = 1)this as costly dm.getTorrent().getAnnounceURL().getHost()
					},
					{
						"st:",
						"" + dm.getState()
					},
					{
						"c:",
						comment
					},
					{
						"f:",
						"",		//defer (index = 4)
					},
					{
						"tag:",
						""
					}
				};

				Object o_name = name_mapping[0][1];

				String tmpSearch = sLastSearch;

				tmpSearch = GeneralUtils.getConfusableEquivalent( tmpSearch );

				for ( int i = 1; i < name_mapping.length; i++ ){

					if ( tmpSearch.startsWith(name_mapping[i][0])) {

						tmpSearch = tmpSearch.substring(name_mapping[i][0].length());

						if ( i == 1 ){

							List<String> names = new ArrayList<>();

							o_name = names;

							TOTorrent t = dm.getTorrent();

							if ( t != null ){

								names.add( t.getAnnounceURL().getHost());

								TOTorrentAnnounceURLSet[] sets = t.getAnnounceURLGroup().getAnnounceURLSets();

								for ( TOTorrentAnnounceURLSet set: sets ){

									URL[] urls = set.getAnnounceURLs();

									for ( URL u: urls ){

										names.add( u.getHost());
									}
								}

								try{
									byte[] hash = t.getHash();

									names.add( ByteFormatter.encodeString( hash ));
									names.add( Base32.encode( hash ));

								}catch( Throwable e ){

								}
							}
						}else if ( i == 4 ){

							List<String> names = new ArrayList<>();

							o_name = names;

							DiskManagerFileInfoSet file_set = dm.getDiskManagerFileInfoSet();

							DiskManagerFileInfo[] files = file_set.getFiles();

							for ( DiskManagerFileInfo f: files ){

								File file = f.getFile(true);

								String name = tmpSearch.contains( File.separator )?file.getAbsolutePath():file.getName();

								names.add( name );
							}
							
							names.add( dm.getSaveLocation().getAbsolutePath());		// always throw in matching against the full save path

						} else if (i == 5) {
							List<String> names = new ArrayList<String>();

							o_name = names;

							List<Tag> tags = tagManager.getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, dm );

							if ( tags.size() > 0 ){

								tags = TagUtils.sortTags( tags );

								for ( Tag t: tags ){

									String str = t.getTagName( true );
									names.add(str);

								}
							}

						}else{
							o_name = name_mapping[i][1];
						}
					}
				}

				boolean	match_result = true;

				String expr;
				
				if ( bRegexSearch ){
					
					expr = tmpSearch;
					
					if ( expr.startsWith( "!" )){
						
						expr = expr.substring(1);

						match_result = false;
					}
				}else{
					
					expr = RegExUtil.convertAndOrToExpr( tmpSearch );
				}

				Pattern pattern = RegExUtil.getCachedPattern( "tv:search", expr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

				if ( o_name instanceof String ){

					String name = (String)o_name;
					
					name = GeneralUtils.getConfusableEquivalent( name );
					
					bOurs = pattern.matcher( name ).find() == match_result;

				}else{
					List<String>	names = (List<String>)o_name;

						// match_result: true -> at least one match; false -> any fail

					bOurs = !match_result;

					for ( String name: names ){
						name = GeneralUtils.getConfusableEquivalent(name);

						if ( pattern.matcher( name ).find()){
							bOurs = match_result;
							break;
						}
					}
				}
			} catch (Exception e) {
				// Future: report PatternSyntaxException message to user.

				bOurs = true;
			}
		}else{

			bOurs = true;
		}

		return bOurs;
	}

	// @see com.biglybt.ui.swt.views.table.TableViewFilterCheck#filterSet(java.lang.String)
	@Override
	public void filterSet(final String filter) {
		Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {
					if ( vtxi != null ){

						boolean	enabled = filter.length() > 0;

						if ( enabled ){

							if ( txtFilterUpdateEvent == null ){

								txtFilterUpdateEvent =
									SimpleTimer.addPeriodicEvent(
										"MTV:updater",
										1000,
										new TimerEventPerformer()
										{
											@Override
											public void
											perform(
												TimerEvent event )
											{
												Utils.execSWTThread(
													new AERunnable()
													{
														@Override
														public void
														runSupport()
														{
															if ( txtFilterUpdateEvent != null ){

																if ( tv.isDisposed()){

																	txtFilterUpdateEvent.cancel();

																	txtFilterUpdateEvent = null;

																}else{

																	viewChanged( tv );
																}
															}
														}
													});
											}
										});
							}
						}else{

							if ( txtFilterUpdateEvent != null ){

								txtFilterUpdateEvent.cancel();

								txtFilterUpdateEvent = null;
							}
						}

						vtxi.setSearchActive( tv.getComposite(), enabled );
					}
				
			}
		});
	}

	@Override
	public void
	viewChanged(
		final TableView<DownloadManager> view )
	{
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_viewChanged(view);
			}
		});
	}

	private void swt_viewChanged(final TableView<DownloadManager> view) {

		Composite filterParent = filterBox == null ? null : filterBox.getMainWidget().getParent();
		if ( filterParent != null && !filterParent.isDisposed()){

			if ( vtxi != null){

				TableRowCore[] rows = view.getRows();

				int	active = 0;

				for ( TableRowCore row: rows ){

					DownloadManager dm = (DownloadManager)row.getDataSource( true );

					int	state = dm.getState();

					if ( state == DownloadManager.STATE_DOWNLOADING || state == DownloadManager.STATE_SEEDING ){

						active++;
					}
				}

				vtxi.searchUpdate( tv.getComposite(), rows.length, active );
			}
		}
	}

	// @see TableSelectionListener#selected(TableRowCore[])
	@Override
	public void selected(TableRowCore[] rows) {
	}

	// @see TableSelectionListener#deselected(TableRowCore[])
	@Override
	public void deselected(TableRowCore[] rows) {
	}

	@Override
	public void selectionChanged(TableRowCore[] selected_rows, TableRowCore[] deselected_rows){
		updateSelectedContent();
	  	refreshTorrentMenu();
	}
	
	// @see TableSelectionListener#focusChanged(TableRowCore)
	@Override
	public void focusChanged(TableRowCore focus) {
		updateSelectedContent();
		refreshTorrentMenu();
	}

	// @see TableSelectionListener#mouseEnter(TableRowCore)
	@Override
	public void mouseEnter(TableRowCore row) {
	}

	// @see TableSelectionListener#mouseExit(TableRowCore)
	@Override
	public void mouseExit(TableRowCore row) {
	}

	private FrequencyLimitedDispatcher refresh_limiter = new FrequencyLimitedDispatcher(
			new AERunnable() {
				@Override
				public void runSupport() {
					Utils.getOffOfSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							updateSelectedContent();
							
								// position change events
							
							columnInvalidateAfterMove();
						}
					});
				}
			}, 250 );

	{
		refresh_limiter.setSingleThreaded();
	}

	private void
	updateSelectedContentRateLimited()
	{
			// we can get a lot of these in succession when lots of rows are selected and we, for example, right-click or stop the torrents etc

		refresh_limiter.dispatch();
	}

	public void updateSelectedContent() {
		updateSelectedContent( false );
	}

	public void updateSelectedContent( boolean force ) {
		if (cTablePanel == null || cTablePanel.isDisposed()) {
			return;
		}
			// if we're not active then ignore this update as we don't want invisible components
			// updating the toolbar with their invisible selection. Note that unfortunately the
			// call we get here when activating a view does't yet have focus

		if ( !isTableFocus()){
			if ( !force ){
				return;
			}
		}
		Object[] dataSources = tv.getSelectedDataSources(true);
		List<SelectedContent> listSelected = new ArrayList<>(dataSources.length);
		for (Object ds : dataSources) {
			if (ds instanceof DownloadManager) {
				listSelected.add(new SelectedContent((DownloadManager) ds));
			} else if (ds instanceof DiskManagerFileInfo) {
				DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
				listSelected.add(new SelectedContent(fileInfo.getDownloadManager(), fileInfo.getIndex()));
			}
		}
		SelectedContent[] content = listSelected.toArray(new SelectedContent[0]);
		SelectedContentManager.changeCurrentlySelectedContent(tv.getTableID(), content, tv);
	}

	private void refreshTorrentMenu() {
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null && uiFunctions instanceof UIFunctionsSWT) {
			((UIFunctionsSWT)uiFunctions).refreshTorrentMenu();
		}
	}

	public DownloadManager[] getSelectedDownloads() {
		Object[] data_sources = tv.getSelectedDataSources().toArray();
		List<DownloadManager> list = new ArrayList<>();
		for (Object ds : data_sources) {
			if (ds instanceof DownloadManager) {
				list.add((DownloadManager) ds);
			}
		}
		return list.toArray(new DownloadManager[0]);
	}

  // @see TableSelectionListener#defaultSelected(TableRowCore[])
  @Override
  public void defaultSelected(TableRowCore[] rows, int keyMask, int origin) {
  	if (defaultSelectedListener != null) {
  		defaultSelectedListener.defaultSelected(rows, keyMask, origin );
  		return;
  	}
  	showSelectedDetails();
	}

  private void showSelectedDetails() {
		Object[] dm_sources = tv.getSelectedDataSources().toArray();
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		for (int i = 0; i < dm_sources.length; i++) {
			if (!(dm_sources[i] instanceof DownloadManager)) {
				continue;
			}
			if (uiFunctions != null) {
				uiFunctions.getMDI().showEntryByID(
						MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS,
						dm_sources[i]);
			}
		}
  }

  public void overrideDefaultSelected(TableSelectionListener defaultSelectedListener) {
		this.defaultSelectedListener = defaultSelectedListener;
  }



  @Override
	public void addThisColumnSubMenu(String sColumnName, Menu menuThisColumn) {
		switch (sColumnName) {
			case "trackername": {
				MenuItem item = new MenuItem(menuThisColumn, SWT.PUSH);
				Messages.setLanguageText(item,
						"MyTorrentsView.menu.trackername.editprefs");
				item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event e) {
						SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
								"trackername.prefs.title", "trackername.prefs.message");
						entryWindow.setPreenteredText(
								COConfigurationManager.getStringParameter(
										"mtv.trackername.pref.hosts", ""),
								true);
						entryWindow.selectPreenteredText(false);
						entryWindow.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver receiver) {
								if (!receiver.hasSubmittedInput()) {
									return;
								}

								COConfigurationManager.setParameter(
										"mtv.trackername.pref.hosts",
										receiver.getSubmittedInput().trim());
							}
						});
					}
				});

				break;
			}

			case "eta":
			case "smootheta": {
				boolean eta_absolute = COConfigurationManager.getBooleanParameter("mtv.eta.show_absolute", false);

				final MenuItem item = new MenuItem(menuThisColumn, SWT.CHECK);
				Messages.setLanguageText(item, "MyTorrentsView.menu.eta.abs");
				item.setSelection(eta_absolute);

				item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event e) {
						boolean eta_absolute = item.getSelection();
						tv.columnInvalidate("eta");
						tv.refreshTable(false);
						COConfigurationManager.setParameter("mtv.eta.show_absolute",
								eta_absolute);
					}
				});
				break;
			}

			case "ProgressETA": {
				boolean progress_eta_absolute = COConfigurationManager.getBooleanParameter("mtv.progress_eta.show_absolute", false);
				final MenuItem item = new MenuItem(menuThisColumn, SWT.CHECK);
				Messages.setLanguageText(item, "MyTorrentsView.menu.eta.abs");
				item.setSelection(progress_eta_absolute);

				item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event e) {
						boolean progress_eta_absolute = item.getSelection();
						tv.columnInvalidate("ProgressETA");
						tv.refreshTable(false);
						COConfigurationManager.setParameter(
								"mtv.progress_eta.show_absolute", progress_eta_absolute);
					}
				});
				break;
			}
			case "status": {
				
				final Menu menuSort = new Menu(menuThisColumn.getShell(),SWT.DROP_DOWN);
				
				final MenuItem itemSort = new MenuItem(menuThisColumn, SWT.CASCADE);
				
				Messages.setLanguageText(itemSort, "menu.sort.order");
				
				itemSort.setMenu(menuSort);
				
				String tid = tv.getTableID();
				
				int order = COConfigurationManager.getIntParameter( "MyTorrents.status.sortorder." + tid);
								
				MenuItem itemAlpha = new MenuItem(menuSort, SWT.RADIO);
				Messages.setLanguageText(itemAlpha, "menu.sort.alphabetic");
				
				MenuItem itemPriority = new MenuItem(menuSort, SWT.RADIO);
				Messages.setLanguageText(itemPriority, "MyTorrentsView.menu.status.prioritysort");
				
				MenuItem itemStatus = new MenuItem(menuSort, SWT.RADIO);
				Messages.setLanguageText(itemStatus, "menu.sort.status");
				
				if ( order == 0 ){
					itemAlpha.setSelection(true);
				}else if ( order == 1 ){
					itemPriority.setSelection(true);
				}else{
					itemStatus.setSelection(true);
				}
										
				Listener listener = new Listener() {
					@Override
					public void handleEvent(Event e) {
						int new_order;
						if ( itemAlpha.getSelection()){
							new_order = 0;
						}else if ( itemPriority.getSelection()){
							new_order = 1;
						}else{
							new_order = 2;
						}
						COConfigurationManager.setParameter("MyTorrents.status.sortorder." + tid, new_order );
						tv.columnInvalidate("status");
						tv.refreshTable(false);
					}
				};
				
				itemAlpha.addListener(SWT.Selection, listener );
				itemPriority.addListener(SWT.Selection, listener );
				itemStatus.addListener(SWT.Selection, listener );

				boolean change_status_fg = COConfigurationManager.getBooleanParameter("MyTorrents.status.change.fg" );

				final MenuItem item = new MenuItem(menuThisColumn, SWT.CHECK);
				Messages.setLanguageText(item, "MyTorrents.status.change.fg");
				item.setSelection(change_status_fg);

				item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event e) {
						boolean change_status_fg = item.getSelection();
						COConfigurationManager.setParameter("MyTorrents.status.change.fg", change_status_fg);
						tv.columnInvalidate("status");
						tv.refreshTable(false);
					}
				});
				
				break;
			}
		}
	}

	// @see com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener#fillMenu(java.lang.String, org.eclipse.swt.widgets.Menu)
	@Override
	public void fillMenu(String sColumnName, final Menu menu) {
		Object[] dataSources = tv.getSelectedDataSources(true);
		DownloadManager[] selecteddms = getSelectedDownloads();

		if (selecteddms.length == 0 && dataSources.length > 0) {
			
			Map<DownloadManager,List<DiskManagerFileInfo>> map = new IdentityHashMap<>();

			List<DownloadManager> dms = new ArrayList<>();

			for (Object ds : dataSources) {
				if (ds instanceof DiskManagerFileInfo) {
					DiskManagerFileInfo info = (DiskManagerFileInfo) ds;
					
					DownloadManager dm = info.getDownloadManager();
					
					List<DiskManagerFileInfo> list = map.get(dm);

					if ( list == null ){

						list = new ArrayList<>(dm.getDiskManagerFileInfoSet().nbFiles());

						map.put( dm, list );

						dms.add( dm );
					}

					list.add( info );
				}
			}
			
			DownloadManager[] manager_list = dms.toArray( new DownloadManager[ dms.size()]);

			DiskManagerFileInfo[][] files_list = new DiskManagerFileInfo[manager_list.length][];

			for ( int i=0;i<manager_list.length;i++){

				List<DiskManagerFileInfo> list =  map.get( manager_list[i] );

				files_list[i] = list.toArray( new DiskManagerFileInfo[list.size()]);
			}
			
			if ( files_list.length > 0 ){
				
				FilesViewMenuUtil.fillMenu(	tv,	sColumnName, menu, manager_list, files_list, null, false, false );
				
				return;
			}
		}

		boolean hasSelection = (selecteddms.length > 0);

		if (hasSelection) {
			boolean isSeedingView = Download.class.equals(forDataSourceType) || DownloadTypeComplete.class.equals(forDataSourceType);
			TorrentUtil.fillTorrentMenu(menu, selecteddms, core, true,
					(isSeedingView) ? 2 : 1, tv);

			// ---
			new MenuItem(menu, SWT.SEPARATOR);
		}
	}

	private void createDragDrop() {
		try {

			Transfer[] types = new Transfer[] { TextTransfer.getInstance() };

			DragSource dragSource = tv.createDragSource(DND.DROP_MOVE | DND.DROP_COPY);
			if (dragSource != null) {
				dragSource.setTransfer(types);
				dragSource.addDragListener(new DragSourceAdapter() {
					private String eventData;

					@Override
					public void dragStart(DragSourceEvent event) {
						TableRowCore[] rows = tv.getSelectedRows();
						if (rows.length != 0) {
							event.doit = true;
							//System.out.println("DragStart");
							drag_drop_location_start = getRowLocation( rows[0] );
							drag_drop_rows = rows;
						} else {
							event.doit = false;
							drag_drop_location_start = -1;
							drag_drop_rows = null;
						}

						// Build eventData here because on OSX, selection gets cleared
						// by the time dragSetData occurs
						boolean onlyDMs = true;
						StringBuilder sb = new StringBuilder();
						Object[] selectedDataSources = tv.getSelectedDataSources(true);
						for (Object ds : selectedDataSources) {
							if (ds instanceof DownloadManager) {
								DownloadManager dm = (DownloadManager) ds;
								TOTorrent torrent = dm.getTorrent();
								if (torrent != null) {
									try {
										sb.append(torrent.getHashWrapper().toBase32String());
										sb.append('\n');
									} catch (TOTorrentException e) {
									}
								}
							} else if (ds instanceof DiskManagerFileInfo) {
								DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
								DownloadManager dm = fileInfo.getDownloadManager();
								TOTorrent torrent = dm.getTorrent();
								if (torrent != null) {
									try {
										sb.append(torrent.getHashWrapper().toBase32String());
										sb.append(';');
										sb.append(fileInfo.getIndex());
										sb.append('\n');
										onlyDMs = false;
									} catch (TOTorrentException e) {
									}
								}
							}
						}

						eventData = (onlyDMs ? "DownloadManager\n" : "DiskManagerFileInfo\n") + sb.toString();
					}

					@Override
					public void dragSetData(DragSourceEvent event) {
						// System.out.println("DragSetData");
						event.data = eventData;
					}
					
					@Override
					public void dragFinished(DragSourceEvent event){
							// might be able to do this synchronously but not convinced of ordering as we 
							// need the location to remain valid until the drop is complete...
						
						Utils.execSWTThreadLater(
							10,
							()->{
								drag_drop_location_start = -1;
							});
					}
					
				});
			}

			DropTarget dropTarget = tv.createDropTarget(DND.DROP_DEFAULT | DND.DROP_MOVE
				| DND.DROP_COPY | DND.DROP_LINK | DND.DROP_TARGET_MOVE);
			if (dropTarget != null) {
				dropTarget.setTransfer(new Transfer[] {
					FixedHTMLTransfer.getInstance(),
					FixedURLTransfer.getInstance(),
					FileTransfer.getInstance(),
					TextTransfer.getInstance()
				});

				dropTarget.addDropListener(new DropTargetAdapter() {
					Point			enterPoint = null;
					long			lastScrollTime;
					int				scrollDelay;
					TableRowCore	lastScrollRow;
					
					@Override
					public void dropAccept(DropTargetEvent event) {
						event.currentDataType = FixedURLTransfer.pickBestType(event.dataTypes,
								event.currentDataType);
					}

					@Override
					public void dragEnter(DropTargetEvent event) {
						// no event.data on dragOver, use drag_drop_line_start to determine
						// if ours
					
						lastScrollTime = 0;
						
						if (drag_drop_location_start < 0) {
							if (event.detail != DND.DROP_COPY) {
								if ((event.operations & DND.DROP_LINK) > 0)
									event.detail = DND.DROP_LINK;
								else if ((event.operations & DND.DROP_COPY) > 0)
									event.detail = DND.DROP_COPY;
							}
						} else if (TextTransfer.getInstance().isSupportedType(
								event.currentDataType)) {
							event.detail = tv.getTableRowWithCursor() == null ? DND.DROP_NONE : DND.DROP_MOVE;
							event.feedback = DND.FEEDBACK_NONE; // DND.FEEDBACK_SCROLL;
							enterPoint = new Point(event.x, event.y);
						}
					}

					// @see org.eclipse.swt.dnd.DropTargetAdapter#dragLeave(org.eclipse.swt.dnd.DropTargetEvent)
					@Override
					public void dragLeave(DropTargetEvent event) {
						super.dragLeave(event);

						tv.getComposite().redraw();
					}

					@Override
					public void dragOver(DropTargetEvent event) {
						if (drag_drop_location_start >= 0) {
							/*
							if (drag_drop_rows.length > 0
									&& !(drag_drop_rows[0].getDataSource(true) instanceof DownloadManager)) {
								event.detail = DND.DROP_NONE;
								return;
							}
							*/
							TableRowCore row = tv.getTableRowWithCursor();
							if (row instanceof TableRowPainted) {
								boolean dragging_down = getRowLocation( row ) > drag_drop_location_start;
	  							Rectangle bounds = ((TableRowPainted) row).getBounds();
	  							tv.getComposite().redraw();
	  							tv.getComposite().update();
	  							Rectangle clientArea = tv.getClientArea();
	  						
	  							int y_pos = bounds.y - clientArea.y;
	  									  						
	  							if ( dragging_down ){
	  								y_pos +=bounds.height;
	  							}
	  							
		  						int scrollDirection = 0;
		  						
	  							if ( y_pos + row.getHeight()*2 >= clientArea.height ){
	  								
	  								scrollDirection = 1;
	  								
	  							}else if ( y_pos - row.getHeight()*2 <= 0 ){
	  								
	  								scrollDirection = -1;
	  							}
	  							
	  							GC gc = new GC(tv.getComposite());
	  							gc.setLineWidth(2);
	  							gc.drawLine(bounds.x, y_pos, bounds.x + bounds.width, y_pos );
	  							gc.dispose();
	  							
	  							if ( scrollDirection != 0 ){
	  								
		  							long now = SystemTime.getMonotonousTime();
		  							
		  							if ( lastScrollTime == 0 ){
		  								
		  								lastScrollTime 	= now;
		  								scrollDelay		= 400;
		  								
		  								lastScrollRow	= row;
		  								
		  							}else if ( now - lastScrollTime > scrollDelay ){
		  								
		  								lastScrollTime = now;
		  								
		  								if ( scrollDelay > 100 ){
		  									scrollDelay -= 50;
		  								}
		  								
			  							TableRowCore[] rows = tv.getRowsAndSubRows(false);
			  							
			  							for ( int i=0;i<rows.length;i++){
			  								
			  								if ( rows[i] == lastScrollRow ){
			  									
			  									if ( scrollDirection > 0 ){
			  										
				  									if ( i < rows.length - 1 ){
				  										
				  										lastScrollRow = rows[i+1];
				  													  										
				  										tv.scrollVertically( lastScrollRow.getHeight());
				  									}
			  									}else{
			  										if ( i > 0 ){
				  										
				  										lastScrollRow = rows[i-1];
				  													  										
				  										tv.scrollVertically( -lastScrollRow.getHeight());
			  										}
			  									}
			  									
			  									break;
			  								}
			  							}
		  							}
	  							}else{
	  								lastScrollTime = 0;
	  							}
							}
							event.detail = row == null ? DND.DROP_NONE : DND.DROP_MOVE;
							event.feedback = // DND.FEEDBACK_SCROLL |
									 ((enterPoint != null && enterPoint.y > event.y)
											? DND.FEEDBACK_INSERT_BEFORE : DND.FEEDBACK_INSERT_AFTER);
							return;
						}

						List<Tag> tags = DragDropUtils.getTagsFromDroppedData(DragDropUtils.getLastDraggedObject());
						if (tags.size() > 0) {
							TableRowCore row = tv.getTableRowWithCursor();
							if (row == null) {
								return;
							}
							if (row.getParentRowCore() != null) {
								row = row.getParentRowCore();
							}

							DownloadManager dm = (DownloadManager) row.getDataSource(true);
							boolean hasUntagged = false;
							for (Tag tag : tags) {
								if (!tag.hasTaggable(dm)) {
									hasUntagged = true;
									break;
								}
							}
							
							event.detail = hasUntagged ? DND.DROP_COPY : DND.DROP_DEFAULT;
						}
					}

					@Override
					public void drop(DropTargetEvent event) {
						if (!(event.data instanceof String)) {
							TorrentOpener.openDroppedTorrents(event, true);
							return;
						}
						String data = (String) event.data;
						if (data.startsWith("DiskManagerFileInfo\n")) {
							return;
						}
						List<Tag> tags = DragDropUtils.getTagsFromDroppedData(data);

						if (!data.startsWith("DownloadManager\n") && tags.size() == 0) {
							TorrentOpener.openDroppedTorrents(event, true);
							return;
						}

						TableRowCore row = tv.getRow(event);
						if (row == null)
							return;
						if (row.getParentRowCore() != null) {
							row = row.getParentRowCore();
						}

						if (tags.size() > 0) {
							DownloadManager dm = (DownloadManager) row.getDataSource(true);
							boolean hasUntagged = false;
							for (Tag tag : tags) {
								if (!tag.hasTaggable(dm)) {
									hasUntagged = true;
									break;
								}
							}
							
							boolean toTag = hasUntagged;

							for (Tag tag : tags) {
								if (toTag) {
									tag.addTaggable(dm);
								} else {
									tag.removeTaggable(dm);
								}
							}

							return;
						}

						event.detail = DND.DROP_NONE;
						// Torrent file from shell dropped
						if (drag_drop_location_start >= 0) { // event.data == null
							event.detail = DND.DROP_NONE;
							long end_location = getRowLocation( row );
							if (end_location >> 32  != drag_drop_location_start >> 32 ) {
								DownloadManager dm = (DownloadManager) row.getDataSource(true);
								moveRowsTo(drag_drop_rows, dm.getPosition());
								event.detail = DND.DROP_MOVE;
							}
							drag_drop_location_start = -1;
							drag_drop_rows = null;
						}
					}
				});
			}

		} catch (Throwable t) {
			Logger.log(new LogEvent(LOGID, "failed to init drag-n-drop", t));
		}
	}

	private long
	getRowLocation(
		TableRowCore	row )
	{
		long result = row.getIndex();
		
		if ( row.getDataSource( true ) instanceof DiskManagerFileInfo ){
			
			result = (((long)row.getParentRowCore().getIndex())<<32 ) + result;
			
		}else{
			
			result <<= 32;
		}
		
		return( result );
	}
	
  private void moveRowsTo(TableRowCore[] rows, int iNewPos) {
    if (rows == null || rows.length == 0) {
      return;
    }

    TableColumnCore[] sortColumn = tv.getSortColumns();
		boolean isSortAscending = sortColumn.length == 0
				|| sortColumn[0].isSortAscending();

    for (int i = 0; i < rows.length; i++) {
			TableRowCore row = rows[i];
      Object ds = row.getDataSource(true);
      if (!(ds instanceof DownloadManager)) {
      	continue;
      }
      DownloadManager dm = (DownloadManager) ds;
      int iOldPos = dm.getPosition();
      globalManager.moveTo(dm, iNewPos);
      if (isSortAscending) {
        if (iOldPos > iNewPos)
          iNewPos++;
      } else {
        if (iOldPos < iNewPos)
          iNewPos--;
      }
    }
  }

  // @see TableRefreshListener#tableRefresh()
  @Override
  public void tableRefresh() {
    if (tv.isDisposed())
      return;

    refreshTorrentMenu();
  }


	// @see org.eclipse.swt.events.KeyListener#keyPressed(org.eclipse.swt.events.KeyEvent)
	@Override
	public void keyPressed(KeyEvent e) {
		viewActive = true;
		int key = e.character;
		if (key <= 26 && key > 0)
			key += 'a' - 1;

		if (e.stateMask == (SWT.CTRL | SWT.SHIFT)) {
			// CTRL+SHIFT+S stop all Torrents
			if (key == 's') {
				ManagerUtils.asyncStopAll();
				e.doit = false;
				return;
			}

			// Can't capture Ctrl-PGUP/DOWN for moving up/down in chunks
			// (because those keys move through tabs), so use shift-ctrl-up/down
			if (e.keyCode == SWT.ARROW_DOWN) {
				moveSelectedTorrents(10);
				e.doit = false;
				return;
			}

			if (e.keyCode == SWT.ARROW_UP) {
				moveSelectedTorrents(-10);
				e.doit = false;
				return;
			}
		}

		if (e.stateMask == SWT.MOD1) {
			switch (key) {
				case 'a': // CTRL+A select all Torrents
					if (filterBox == null || !filterBox.isOurWidget(e.widget)) {
						tv.selectAll();
						e.doit = false;
					}
					break;
				
				case 'i': // CTRL+I Info/Details
					showSelectedDetails();
					e.doit = false;
					break;
			}

			if (!e.doit)
				return;
		}

		if (e.stateMask == SWT.CTRL) {
			switch (e.keyCode) {
				case SWT.ARROW_UP:
					moveSelectedTorrentsUp();
					e.doit = false;
					break;
				case SWT.ARROW_DOWN:
					moveSelectedTorrentsDown();
					e.doit = false;
					break;
				case SWT.HOME:
					moveSelectedTorrentsTop();
					e.doit = false;
					break;
				case SWT.END:
					moveSelectedTorrentsEnd();
					e.doit = false;
					break;
			}
			if (!e.doit)
				return;

			switch (key) {
				case 'r': // CTRL+R resume/start selected Torrents
					TorrentUtil.resumeTorrents(tv.getSelectedDataSources().toArray());
					e.doit = false;
					break;
				case 's': // CTRL+S stop selected Torrents
					Utils.getOffOfSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							TorrentUtil.stopDataSources(tv.getSelectedDataSources().toArray());
						}
					});
					e.doit = false;
					break;
			}

			if (!e.doit)
				return;
		}

		if (e.keyCode == SWT.F2 && (e.stateMask & SWT.MODIFIER_MASK) == 0) {
			FilesViewMenuUtil.rename(tv, tv.getSelectedDataSources(true), null, true, false,false);
			e.doit = false;
			return;
		}


		// DEL remove selected Torrents
		if (e.stateMask == 0 && e.keyCode == SWT.DEL && (filterBox == null
				|| !filterBox.isOurWidget(e.widget))) {
			Utils.getOffOfSWTThread(() -> TorrentUtil.removeDataSources(
					tv.getSelectedDataSources().toArray()));
			e.doit = false;
			return;
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// ignore
	}





  private void moveSelectedTorrentsDown() {
    // Don't use runForSelectDataSources to ensure the order we want
  	DownloadManager[] dms = getSelectedDownloads();
    Arrays.sort(dms, new Comparator<DownloadManager>() {
			@Override
			public int compare(DownloadManager a, DownloadManager b) {
        return a.getPosition() - b.getPosition();
			}
    });
    for (int i = dms.length - 1; i >= 0; i--) {
      DownloadManager dm = dms[i];
      if (dm.getGlobalManager().isMoveableDown(dm)) {
        dm.getGlobalManager().moveDown(dm);
      }
    }
  }

  private void moveSelectedTorrentsUp() {
    // Don't use runForSelectDataSources to ensure the order we want
  	DownloadManager[] dms = getSelectedDownloads();
    Arrays.sort(dms, new Comparator<DownloadManager>() {
    	@Override
	    public int compare(DownloadManager a, DownloadManager b) {
        return a.getPosition() - b.getPosition();
      }
    });
    for (int i = 0; i < dms.length; i++) {
      DownloadManager dm = dms[i];
      if (dm.getGlobalManager().isMoveableUp(dm)) {
        dm.getGlobalManager().moveUp(dm);
      }
    }
  }

	private void moveSelectedTorrents(int by) {
		// Don't use runForSelectDataSources to ensure the order we want
  	DownloadManager[] dms = getSelectedDownloads();
		if (dms.length <= 0)
			return;

		int[] newPositions = new int[dms.length];

		if (by < 0) {
			Arrays.sort(dms, new Comparator<DownloadManager>() {
				@Override
				public int compare(DownloadManager a, DownloadManager b) {
					return a.getPosition() - b.getPosition();
				}
			});
		} else {
			Arrays.sort(dms, new Comparator<DownloadManager>() {
				@Override
				public int compare(DownloadManager a, DownloadManager b) {
					return b.getPosition() - a.getPosition();
				}
			});
		}

		for (int i = 0; i < dms.length; i++) {
			DownloadManager dm = dms[i];
			boolean complete = dm.isDownloadComplete(false);
			int count = globalManager.downloadManagerCount(complete);
			int pos = dm.getPosition() + by;
			if (pos < i + 1)
				pos = i + 1;
			else if (pos > count - i)
				pos = count - i;

			newPositions[i] = pos;
		}

		for (int i = 0; i < dms.length; i++) {
			DownloadManager dm = dms[i];
			globalManager.moveTo(dm, newPositions[i]);
		}
	}

	private void columnInvalidateAfterMove() {
		if (tv == null) {
			return;
		}
		TableColumnCore[] sortColumn = tv.getSortColumns();
		boolean bForceSort = sortColumn.length != 0
			&& "#".equals(sortColumn[0].getName());
		tv.columnInvalidate("#");
		tv.refreshTable(bForceSort);
	}

	private void moveSelectedTorrentsTop() {
    moveSelectedTorrentsTopOrEnd(true);
  }

  private void moveSelectedTorrentsEnd() {
    moveSelectedTorrentsTopOrEnd(false);
  }

  private void moveSelectedTorrentsTopOrEnd(boolean moveToTop) {
  	DownloadManager[] dms = getSelectedDownloads();
    if (dms.length == 0)
      return;

    if(moveToTop){
      globalManager.moveTop(dms);
    }else{
      globalManager.moveEnd(dms);
    }
  }

  /**
   * @param parameterName the name of the parameter that has changed
   * @see com.biglybt.core.config.ParameterListener#parameterChanged(java.lang.String)
   */
  @Override
  public void parameterChanged(String parameterName) {
		if (parameterName == null || parameterName.equals("DND Always In Incomplete")) {
			bDNDalwaysIncomplete = COConfigurationManager.getBooleanParameter("DND Always In Incomplete");
		}

		showTableTitle = COConfigurationManager.getBooleanParameter( "Library.ShowTitle" );
		showCatButtons = COConfigurationManager.getBooleanParameter( "Library.ShowCatButtons" );
		showTagButtons = COConfigurationManager.getBooleanParameter( "Library.ShowTagButtons" );

		if ( !neverShowTagButtons ){
			
			currentTagsAny = COConfigurationManager.getBooleanParameter( "Library.ShowTagButtons.Inclusive" );
		}
		
		if (parameterName != null ){
			
			if ( 	parameterName.equals("Library.ShowTitle") ||
					parameterName.equals("Library.ShowCatButtons" ) ||
					parameterName.equals("Library.ShowTagButtons" ) ||
					parameterName.equals("Library.ShowTagButtons.FiltersOnly" ) ||
					parameterName.equals("Library.ShowTagButtons.ImageOverride" ) ||
					parameterName.equals("Library.ShowTagButtons.Align" ) ||					
					parameterName.equals("Library.ShowTagButtons.CompOnly" )){

				createTabs();
			}
			
			if ( parameterName.equals( "Library.ShowTagButtons.Inclusive" )){
			
				synchronized( currentTagsLock ){
				
					setCurrentTags(_currentTags);
				}
			}
		}
	}

	private MdiEntrySWT getActiveView() {
		TableViewSWT_TabsCommon tabsCommon = tv.getTabsCommon();
		if (tabsCommon != null) {
			return tabsCommon.getActiveSubView();
		}
		return null;
	}

	@Override
	public void refreshToolBarItems(Map<String, Long> list) {

		if (!isTableFocus()) {
			UISWTViewCore active_view = getActiveView();
			if (active_view != null) {
				UIPluginViewToolBarListener l = active_view.getToolBarListener();
				if (l != null) {
					Map<String, Long> activeViewList = new HashMap<>();
					l.refreshToolBarItems(activeViewList);
					if (activeViewList.size() > 0) {
						list.putAll(activeViewList);
						return;
					}
				}
			}
		}
  }

  @Override
  public boolean toolBarItemActivated(ToolBarItem item, long activationType, Object datasource) {
	  boolean isTableSelected = false;
	  if (tv instanceof TableViewImpl) {
	  	isTableSelected = ((TableViewImpl) tv).isTableSelected();
	  }
	  if (!isTableSelected) {
  		UISWTViewCore active_view = getActiveView();
  		if (active_view != null) {
  			UIPluginViewToolBarListener l = active_view.getToolBarListener();
  			if (l != null && l.toolBarItemActivated(item, activationType, datasource)) {
  				return true;
  			}
  		}
  		return false;
	  }

		String itemKey = item.getID();
  	if (activationType == UIToolBarActivationListener.ACTIVATIONTYPE_HELD) {
      if(itemKey.equals("up")) {
        moveSelectedTorrentsTop();
        return true;
      }
      if(itemKey.equals("down")){
        moveSelectedTorrentsEnd();
        return true;
      }
      return false;
  	}

  	if (activationType != UIToolBarActivationListener.ACTIVATIONTYPE_NORMAL) {
  		return false;
  	}
    if(itemKey.equals("top")) {
      moveSelectedTorrentsTop();
      return true;
    }
    if(itemKey.equals("bottom")){
      moveSelectedTorrentsEnd();
      return true;
    }
    if(itemKey.equals("up")) {
      moveSelectedTorrentsUp();
      return true;
    }
    if(itemKey.equals("down")){
      moveSelectedTorrentsDown();
      return true;
    }
    if(itemKey.equals("run")){
      TorrentUtil.runDataSources(tv.getSelectedDataSources().toArray());
      return true;
    }
    if(itemKey.equals("start")){
      TorrentUtil.queueDataSources(tv.getSelectedDataSources().toArray(), false);
      return true;
    }
    if(itemKey.equals("stop")){
      TorrentUtil.stopDataSources(tv.getSelectedDataSources().toArray());
      return true;
    }
    if (itemKey.equals("startstop")) {
    	TorrentUtil.stopOrStartDataSources(tv.getSelectedDataSources().toArray(), false );
    	return true;
    }
    if(itemKey.equals("remove")){
      TorrentUtil.removeDataSources(tv.getSelectedDataSources().toArray());
      return true;
    }
    return false;
  }

  // DownloadManagerListener Functions
  @Override
	public void stateChanged(DownloadManager manager, int state) {
		if (isOurDownloadManager(manager)) {
			dmCountMutations.incrementAndGet();
		}

		TableRowCore row = tv.getRow(manager);
		if (row == null) {
			return;
		}

		// Usually get stateChanged trigger for every torrent on first display
		// Queue them up, otherwise ThreadPool warns of too many
		synchronized (listRowsToRefresh) {
			if (!listRowsToRefresh.contains(row)) {
				listRowsToRefresh.add(row);
				if (listRowsToRefresh.size() > 1) {
					return;
				}
			}
		}

		Utils.getOffOfSWTThread(() -> {
			// wait between 10ms and 50ms for new state changes
			int count = listRowsToRefresh.size();
			for (int i = 0; i < 5; i++) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
				int newCount = listRowsToRefresh.size();
				if (newCount == count) {
					break;
				}
				count = newCount;
			}

			TableRowCore[] rows;
			synchronized (listRowsToRefresh) {
				rows = listRowsToRefresh.toArray(
						new TableRowCore[listRowsToRefresh.size()]);
				listRowsToRefresh.clear();
			}
			for (TableRowCore rowToRefresh : rows) {
				rowToRefresh.refresh(true);
				if (rowToRefresh.isSelected()) {
					updateSelectedContentRateLimited();
				}
			}
		});
	}

  // DownloadManagerListener
  @Override
  public void positionChanged(DownloadManager download, int oldPosition, int newPosition) {
  	if (isOurDownloadManager(download)) {
 	
			// When a torrent gets added to the top of the list, we get a
			// positionChanged for every torrent below it.  Definitely need this
	  		// rate limited
  		
  			// this will also force a column invalidate so position change is visible
  		
			updateSelectedContentRateLimited();
  	}
  }

  // DownloadManagerListener
  @Override
  public void 
  filePriorityChanged(
	DownloadManager 		download,
    DiskManagerFileInfo 	file ) 
  {
	  TableRowCore row = tv.getRow( download );

	  if ( row == null ){
		  
		  return;
	  }
	  
	  TableRowCore[] kids = row.getSubRowsWithNull();
	  
	  DiskManagerFileInfo[] files = getVisibleFiles( download );
	  
	  if ( kids.length != files.length ){
		  
		  row.setSubItems( files );
		  
	  }else{
		  
		  Set<Integer>	fileIndexes = new HashSet<>();
		  
		  for ( int i=0;i<kids.length;i++){
			
			  fileIndexes.add( files[i].getIndex());
		  }
		  
		  for ( int i=0;i<kids.length;i++){
			  
			  if (!fileIndexes.contains(((DiskManagerFileInfo)kids[i].getDataSource( true )).getIndex())){
				  
				  row.setSubItems(files);
				  
				  return;
			  }
		  }
	  }
  }
  
  // DownloadManagerListener
	@Override
	public void completionChanged(DownloadManager manager, boolean bCompleted) {
		// manager has moved lists

		if (isOurDownloadManager(manager)) {
			tv.addDataSource(manager);
		} else {

			tv.removeDataSource(manager);
		}
	}

  // DownloadManagerListener
  @Override
  public void downloadComplete(DownloadManager manager) {
  }


	@Override
	public void tagEventOccurred(TagEvent event ) {
		int	type = event.getEventType();
		Tag	tag = event.getTag();
		if ( type == TagEvent.ET_TAG_ADDED ){
			tagAdded( tag );
		}else if ( type == TagEvent.ET_TAG_MEMBERSHIP_CHANGED || type == TagEvent.ET_TAG_METADATA_CHANGED){
			tagChanged( tag );
		}else if ( type == TagEvent.ET_TAG_REMOVED ){
			tagRemoved( tag );
		}
	}

	@Override
	public void tagTypeChanged(TagType tag_type) {
	}

	public void
	tagAdded(
		Tag			tag )
	{
		createTabs();
	}

	private Set<Tag> pending_tag_changes = new HashSet<>();

	private boolean currentTagsAny = true;

	public void
	tagChanged(
		Tag			tag )
	{
		if ( neverShowTagButtons || !( showCatButtons || showTagButtons )){
			
			return;
		}
		
		int tt = tag.getTagType().getTagType();
		
		if ( tt == TagType.TT_DOWNLOAD_CATEGORY && !showCatButtons ){
			
			return;
			
		}else  if ( tt == TagType.TT_DOWNLOAD_MANUAL && !showTagButtons ){
			
			return;
		}

		
			// we can get a lot of hits here, limit tab rebuilds somewhat

		synchronized( pending_tag_changes ){

			pending_tag_changes.add( tag );
		}

		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {

				if ( allTags != null ){

					boolean create_tabs = false;
					List<Tag> ourPendingTags = new ArrayList<>();

				   	boolean filterOnly = COConfigurationManager.getBooleanParameter( "Library.ShowTagButtons.FiltersOnly" );

					synchronized( pending_tag_changes ){
						ourPendingTags.addAll(pending_tag_changes);

						for ( Tag t: pending_tag_changes ){
				
							boolean manual = t.getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL;
							
							boolean should_be_visible	= (manual&&filterOnly?t.getFlag( Tag.FL_IS_FILTER ):t.isVisible())&& !hiddenTags.contains( t );
							boolean is_visible			= allTags.contains( t );

							if ( should_be_visible != is_visible ){

								create_tabs = true;

								break;
							}
						}

						pending_tag_changes.clear();
					}

					if ( create_tabs ){

						createTabs();
						return;
					}

					if (cTitleCategoriesAndTags == null || cTitleCategoriesAndTags.isDisposed()) {
						return;
					}
					Control[] children = cTitleCategoriesAndTags.getChildren();
					for (Control child : children) {
						if (!(child instanceof TagCanvas)) {
							continue;
						}
						TagPainter painter = ((TagCanvas) child).getTagPainter();
						if (ourPendingTags.remove(painter.getTag())) {
							boolean selected = painter.isSelected();
							painter.updateState(null);
							painter.setSelected(selected);
						}
					}
				}
			}
		});
	}

	public void
	tagRemoved(
		Tag			tag )
	{
		synchronized( currentTagsLock ){
			if (_currentTags == null) {
				return;
			}
	
			removeTagFromCurrent(tag);
		}
		createTabs();
	}


	private void removeTagFromCurrent(Tag tag) {
		synchronized( currentTagsLock ){
			boolean found = false;
			for (int i = 0; i < _currentTags.length; i++) {
				Tag curTag = _currentTags[i];
				if (curTag.equals(tag)) {
					Tag[] tags;
					if (_currentTags.length == 1) {
						tags = new Tag[] {
								CategoryManager.getCategory(Category.TYPE_ALL)
						};
					} else {
						tags = new Tag[_currentTags.length - 1];
						if (i > 0) {
							System.arraycopy(_currentTags, 0, tags, 0, i);
						}
						if (tags.length - i > 0) {
							System.arraycopy(_currentTags, i + 1, tags, i, tags.length - i);
						}
					}

					setCurrentTags(tags);
					found = true;
					break;
				}
			}
	
			if (!found) {
				// always activate as deletion of this one might have
				// affected the current view
				setCurrentTags(_currentTags);
			}
		}
	}

	private Object[] removeFromArray(Object[] array, Object o) {
		for (int i = 0; i < array.length; i++) {
			Object cur = array[i];
			if (cur.equals(o)) {
				Tag[] newArray = new Tag[array.length - 1];
				if (i > 0) {
					System.arraycopy(array, 0, newArray, 0, i);
				}
				if (newArray.length - i > 0) {
					System.arraycopy(array, i + 1, newArray, i, newArray.length - i);
				}

				return newArray;
			}
		}

		return array;
	}

			// tags

	public Tag[] getCurrentTags() {
		synchronized( currentTagsLock ){
			if ( _currentTags == null ){
				return( null );
			}
			return _currentTags.clone();
		}
	}

	private void
	setCurrentTagGroup(
		TagGroup		tg )
	{
		List<Tag> tags	= tg.getTags();
		
		hiddenTags = new HashSet<>( tags );
		
		setCurrentTags( tags.toArray( new Tag[0] ));
	}
	
	protected void setCurrentTags(Tag... tags) {
		synchronized( currentTagsLock ){
			if (_currentTags != null) {
				for (Tag tag : _currentTags) {
					tag.removeTagListener(this);
				}
			}
	
			if ( !hiddenTags.isEmpty()){
				List<Tag>	temp = new ArrayList<Tag>( tags.length + hiddenTags.size());
				
				temp.addAll(hiddenTags);
				
				for ( Tag t: tags ){
					if ( !temp.contains(t)){
						temp.add(t);
					}
				}
				tags = temp.toArray(new Tag[0]);
			}
			_currentTags = tags;
			if (_currentTags != null) {
				Set<Tag> to_remove = null;
				for (Tag tag : _currentTags) {
					if ( tag.getTaggableTypes() != Taggable.TT_DOWNLOAD ){
							// hmm, not a download related tag (e.g. peer-set), remove from the set. We can get this in the
							// TagsOverview 'torrents' sub-view when peer-sets are selected in the main tag table
						if (  to_remove == null ){
							to_remove = new HashSet<>();
						}
						to_remove.add( tag );
					}else{
						tag.addTagListener(this, false);
					}
				}
				if ( to_remove != null ){
					Tag[] updated_tags = new Tag[_currentTags.length-to_remove.size()];
	
					int	pos = 0;
					for (Tag tag : _currentTags) {
						if ( !to_remove.contains( tag )){
							updated_tags[pos++] = tag;
						}
					}
					_currentTags = updated_tags;
				}
			}
		}
		
  		tv.processDataSourceQueue();
  		Object[] managers = globalManager.getDownloadManagers().toArray();
  		List<DownloadManager> listRemoves = new ArrayList<>();
  		List<DownloadManager> listAdds = new ArrayList<>();

  		for (int i = 0; i < managers.length; i++) {
  			DownloadManager dm = (DownloadManager) managers[i];

  			boolean bHave = tv.isUnfilteredDataSourceAdded(dm);
  			if (!isOurDownloadManager(dm)) {
  				if (bHave) {
  					listRemoves.add(dm);
  				}
  			} else {
  				if (!bHave) {
  					listAdds.add(dm);
  				}
  			}
  		}
  		tv.removeDataSources(listRemoves.toArray(new DownloadManager[0]));
  		tv.addDataSources(listAdds.toArray(new DownloadManager[0]));

  		tv.processDataSourceQueue();
  		//tv.refreshTable(false);
  	}

  	private boolean
  	isInCurrentTags(
  		DownloadManager		manager )
  	{
  		synchronized( currentTagsLock ){
	  		if ( _currentTags == null ){
	  			return true;
	  		}
	  			
	  		if (currentTagsAny) {
	  			if ( hiddenTags.isEmpty()){
		    		for (Tag tag : _currentTags) {
		  				if (tag.hasTaggable(manager)) {
		  					return true;
		  				}
		  			}
	
		    		return false;
	  			}else{
	  			
	  				boolean has_hidden 		= false;
	  				boolean	has_non_hidden	= false;
	  				
	  				for (Tag tag : _currentTags) {
		  				if (tag.hasTaggable(manager)) {
		  					if ( hiddenTags.contains( tag )){
		  						has_hidden = true;
		  					}else{
		  						has_non_hidden = true;
		  					}
		  				}
		  			}
	  				
	  				if ( has_hidden ){
	  					
	  					return( _currentTags.length == hiddenTags.size() || has_non_hidden);
	  				}else{
	  					
	  					return( false );
	  				}
	  			}
	  		} else {
	  			if ( hiddenTags.isEmpty()){
		    		for (Tag tag : _currentTags) {
		  				if (!tag.hasTaggable(manager)) {
	  						return false;
		  				}
		  			}
		    		return true;
	  			}else{
	  				
	  				boolean has_hidden = false;
	  				
	  				for (Tag tag : _currentTags) {
		  				if (tag.hasTaggable(manager)) {
		  					if ( hiddenTags.contains( tag )){
		  						has_hidden = true;
		  					}
		  				}else{
		  					if ( !hiddenTags.contains( tag )){
		  						return false;
		  					}
		  				}
		  			}
	  				
	  				return( has_hidden );
	  			}
	  		}
  		}
  	}

  	public boolean
  	isInCurrentTag(
  		DownloadManager 	manager )
  	{
  		return( isInCurrentTags( manager ));
  	}

	@Override
	public void
	taggableAdded(
		Tag			tag,
		Taggable	tagged )
	{
		DownloadManager	manager = (DownloadManager)tagged;

	 	if ( isOurDownloadManager(manager)){

	 		tv.addDataSource( manager );
	    }
	}

	@Override
	public void
	taggableSync(
		Tag 		tag )
	{
		// request to fully resync this tag

		Collection<DownloadManager> dataSources = tv.getDataSources();

		for ( DownloadManager dm : dataSources ){

			if ( !isOurDownloadManager(dm)){

				tv.removeDataSource(dm);
			}
		}

		for ( Taggable t: tag.getTagged()){

			DownloadManager	manager = (DownloadManager)t;

			if ( isOurDownloadManager( manager ) && !tv.dataSourceExists(manager)){

				tv.addDataSource(manager);
			}
		}
	}

	@Override
	public void
	taggableRemoved(
		Tag			tag,
		Taggable	tagged )
	{
		DownloadManager	manager = (DownloadManager)tagged;

		if ( !isOurDownloadManager(manager)){
		
			tv.removeDataSource( manager );
		}
	}

	public void
	potentialDeletionChanged(
		DownloadManager[]		old_dms,
		DownloadManager[]		new_dms )
	{
			// we need to force invocations of this to be executed in order
		
		dispatcher.dispatch(()->{
			Utils.execSWTThread(
				new Runnable()
				{
					public void
					run()
					{
						for ( DownloadManager dm: old_dms ){
							
							TableRowCore row = tv.getRow( dm );
							
							if ( row != null ){
								
								row.setRequestAttention( false );
							}
						}
						
						for ( DownloadManager dm: new_dms ){
							
							TableRowCore row = tv.getRow( dm );
							
							if ( row != null ){
								
								row.setRequestAttention( true );
							}
						}
					}
				});
		});
	}
	
  // globalmanagerlistener Functions
  // @see com.biglybt.core.global.GlobalManagerListener#downloadManagerAdded(com.biglybt.core.download.DownloadManager)
  @Override
  public void downloadManagerAdded(DownloadManager dm ) {
    dm.addListener( this );
  	if (isOurDownloadManager(dm)) {
      tv.addDataSource(dm);
    }
  }

  // @see com.biglybt.core.global.GlobalManagerListener#downloadManagerRemoved(com.biglybt.core.download.DownloadManager)
  @Override
  public void downloadManagerRemoved(DownloadManager dm ) {
    dm.removeListener( this );
    DownloadBar.close(dm);
    
    TableRowCore row = tv.getRow( dm );
    
    if ( row != null ){
    	
    	if ( row.isSelected()){
    		
    		if ( dm.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD)){
    		
    			synchronized( removed_while_selected ){
    			
    				removed_while_selected.put( dm.getInternalName(), "" );
    			}
    		}
    		
    		TableRowCore[] selected_rows = tv.getSelectedRows();
    		
    		if ( selected_rows.length == 1 && selected_rows[0] == row ){
    			
    			// this is going to remove the selection completely so select existing appropriate row
    			// logic should probably be in the table itself but for the moment fix here
    			
    			TableRowCore[] rows = tv.getRows();
    			
    			for ( int i=0;i<rows.length;i++){
    				
    				if ( rows[i] == row ){
    					
    					if ( i < rows.length - 1 ){
    						
    						tv.setSelectedRows( new TableRowCore[]{ rows[i+1 ] });
    						
    					}else if ( i > 0 ){
    						
    						tv.setSelectedRows( new TableRowCore[]{ rows[i-1 ] });
    					}
    				}
    			}
    		}
    	}
    }
    
    tv.removeDataSource(dm);
  }

  @Override
  public void destroyInitiated() {  }
  @Override
  public void destroyed() { }
  @Override
  public void seedingStatusChanged(boolean seeding_only_mode, boolean b ){}

  // End of globalmanagerlistener Functions



	// @see com.biglybt.ui.swt.views.table.impl.TableViewTab#updateLanguage()
	@Override
	public void updateLanguage() {
		super.updateLanguage();
		getComposite().layout(true, true);
	}

	public boolean isTableFocus() {
		return viewActive;
		//return tv.isTableFocus();
	}

	@Override
	public Image obfuscatedImage(final Image image) {
		return tv.obfuscatedImage(image);
	}

	/**
	 * Creates and return an <code>TableViewSWT</code>
	 * Subclasses my override to return a different TableViewSWT if needed
	 * @param basicItems
	 * @return
	 */

	protected TableViewSWT<DownloadManager>
	createTableView(
		Class<?> 			forDataSourceType,
		String	 			tableID,
		TableColumnCore[] 	basicItems )
	{
		registerPluginViews();

		int tableExtraStyle = COConfigurationManager.getIntParameter("MyTorrentsView.table.style");
		TableViewSWT<DownloadManager> table =
			TableViewFactory.createTableViewSWT(forDataSourceType, tableID,
				getPropertiesPrefix(), basicItems, "#", tableExtraStyle | SWT.MULTI
						| SWT.FULL_SELECTION | SWT.VIRTUAL | SWT.CASCADE);

			// config??

		boolean	enable_tab_views =
			supportsTabs &&
			COConfigurationManager.getBooleanParameter( "Library.ShowTabsInTorrentView" );

		table.setEnableTabViews(enable_tab_views, false);

		return( table );
	}

	public static void registerPluginViews() {
		// Registering for Download.class implicitly includes DownloadTypeIncomplete and DownloadTypeComplete

		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		if (vm.areCoreViewsRegistered(Download.class)) {
			return;
		}

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				GeneralView.MSGID_PREFIX, null, GeneralView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
			FilesView.MSGID_PREFIX, null, FilesView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
			TaggingView.MSGID_PREFIX, null, TaggingView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
			TorrentOptionsView.MSGID_PREFIX, null, TorrentOptionsView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				TrackerView.MSGID_PREFIX, null, TrackerView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				PeersView.MSGID_PREFIX, null, PeersView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				PeersGraphicView.MSGID_PREFIX, null, PeersGraphicView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				PiecesView.MSGID_PREFIX, null, PiecesView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				PieceMapView.MSGID_PREFIX, null, PieceMapView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				DownloadActivityView.MSGID_PREFIX, null, DownloadActivityView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				TorrentInfoView.MSGID_PREFIX, null, TorrentInfoView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				PrivacyView.MSGID_PREFIX, null, PrivacyView.class));

		vm.registerView(Download.class, new UISWTViewBuilderCore(
				LoggerView.MSGID_PREFIX, null, LoggerView.class));
		
		vm.registerView(Download.class, new UISWTViewBuilderCore(
				DownloadXferStatsView.MSGID_PREFIX, null, DownloadXferStatsView.class));

		vm.setCoreViewsRegistered(Download.class);
	}

	/**
	 * Returns the default row height for the table
	 * Subclasses my override to return a different height if needed; a height of -1 means use default
	 * @return
	 */
	protected int getRowDefaultHeight(){
		return -1;
	}

	// @see com.biglybt.pif.ui.tables.TableRowRefreshListener#rowRefresh(com.biglybt.pif.ui.tables.TableRow)
	@Override
	public void rowRefresh(TableRow row) {
		if (!(row instanceof TableRowCore)) {
			return;
		}

		TableRowCore rowCore = (TableRowCore) row;
		Object ds = rowCore.getDataSource(true);
		if (!(ds instanceof DownloadManager)) {
			return;
		}

		DownloadManager dm = (DownloadManager) ds;
		if (rowCore.getSubItemCount() == 0 && dm.getTorrent() != null
				&& !dm.getTorrent().isSimpleTorrent() && rowCore.isVisible()
				&& dm.getNumFileInfos() > 0) {
			
			DiskManagerFileInfo[] files = getVisibleFiles( dm );
			if ( files != null ){			
				rowCore.setSubItems(files);
			}
		}
	}

	private DiskManagerFileInfo[]
	getVisibleFiles(
		DownloadManager		dm )
	{
		DiskManagerFileInfoSet fileInfos = dm.getDiskManagerFileInfoSet();
		if (fileInfos != null) {
			DiskManagerFileInfo[] files = fileInfos.getFiles();
			boolean copied = false;
			int pos = 0;
			for (int i = 0; i < files.length; i++) {
				DiskManagerFileInfo fileInfo = files[i];
				if ( fileInfo.getTorrentFile().isPadFile()){
					continue;
				}
				if (	fileInfo.isSkipped()
						&& (fileInfo.getStorageType() == DiskManagerFileInfo.ST_COMPACT || fileInfo.getStorageType() == DiskManagerFileInfo.ST_REORDER_COMPACT)) {
					continue;
				}
				if (pos != i) {
					if ( !copied ){
							// we *MUSTN'T* modify the returned array!!!!

						DiskManagerFileInfo[] oldFiles = files;
						files = new DiskManagerFileInfo[files.length];
						System.arraycopy(oldFiles, 0, files, 0, files.length);

						copied = true;
					}

					files[pos] = files[i];
				}
				pos++;
			}
			if (pos != files.length) {
				DiskManagerFileInfo[] oldFiles = files;
				files = new DiskManagerFileInfo[pos];
				System.arraycopy(oldFiles, 0, files, 0, pos);
			}
			
			return( files );
			
		}else{
			return( null );
		}
	}
	
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		boolean b = super.eventOccurred(event);
		if (event.getType() == UISWTViewEvent.TYPE_FOCUSGAINED) {
			if (rebuildListOnFocusGain) {
				List<?> dms = globalManager.getDownloadManagers();
				List<DownloadManager> listAdds = new ArrayList<>();
				List<DownloadManager> listRemoves = new ArrayList<>();
				for (Iterator<?> iter = dms.iterator(); iter.hasNext();) {
					DownloadManager dm = (DownloadManager) iter.next();

					if (!isOurDownloadManager(dm)) {
						listRemoves.add(dm);
					} else {
						listAdds.add(dm);
					}
				}
				tv.removeDataSources(listRemoves.toArray(new DownloadManager[0]));
				tv.addDataSources(listAdds.toArray(new DownloadManager[0]));
			}
			updateSelectedContent(true);
			
				// as library views aren't disposed we can end up with a lot of them built which unfortunately
				// can result in 'no more handles' when here are lots of Tag buttons :(
			
			if ( !hiddenTags.isEmpty()){
				createTabs();
			}
		} else if (event.getType() == UISWTViewEvent.TYPE_FOCUSLOST) {
			if ( !hiddenTags.isEmpty()){
				destroyTabs();
			}
		}
		return b;
	}

	public void setRebuildListOnFocusGain(boolean rebuildListOnFocusGain) {
		this.rebuildListOnFocusGain = rebuildListOnFocusGain;
	}

	@Override
	public void 
	rowAdded(
		TableRowCore row ) 
	{
		if (row.getParentRowCore() == null) {
			DownloadManager dm = (DownloadManager) row.getDataSource(true);
			if ( dm.getDownloadState().getBooleanAttribute( DownloadManagerState.AT_FILES_EXPANDED )){
				row.setExpanded(true);
			}
			if ( isIncompletedOnly ){
					// if a download has removed from complete to incomplete then make it obvious
				Long prev_removed = (Long)dm.getUserData( KEY_DM_REMOVED_FROM_COMPLETE_TABLE_TIME );
				if ( prev_removed != null ){
					if ( SystemTime.getMonotonousTime() - prev_removed < 5000 ){						
						dm.requestAttention();
					}
				}
			}
			
			boolean	was_selected;
			
    		synchronized( removed_while_selected ){

    			was_selected = removed_while_selected.remove( dm.getInternalName()) != null;
    		}
    		
    		if ( was_selected ){
    			
    			TableRowCore[] selected = tv.getSelectedRows();
    			
    			if ( selected.length == 0 ){
    				
    				tv.setSelectedRows( new TableRowCore[]{ row });
    				
    			}else{
    				
    				TableRowCore[] new_sel = new TableRowCore[ selected.length + 1];
    				
    				System.arraycopy( selected, 0, new_sel, 0, selected.length );
    				
    				new_sel[selected.length] = row;
    				
    				tv.setSelectedRows( new_sel );
    			}
    			
    		  	updateSelectedContent();
    		  	refreshTorrentMenu();
    		}
    		
    		dmCountMutations.incrementAndGet();
		}
		//if (getRowDefaultHeight() > 0 && row.getParentRowCore() != null) {
		//	row.setHeight(20);
		//}
	}

	@Override
	public void 
	rowRemoved(TableRowCore row) 
	{
		if ( isCompletedOnly && row.getParentRowCore() == null ){
			
			DownloadManager dm = (DownloadManager)row.getDataSource( true );
			
			dm.setUserData( KEY_DM_REMOVED_FROM_COMPLETE_TABLE_TIME, SystemTime.getMonotonousTime());
		}
		
		TableRowCore[] selected = tv.getSelectedRows();

		if (selected.length > 0 && rowRemovedRunnable == null) {
			rowRemovedRunnable = () -> {
				rowRemovedRunnable = null;
				updateSelectedContent();
				refreshTorrentMenu();
			};
			Utils.execSWTThreadLater(1, rowRemovedRunnable);
		}
		
		dmCountMutations.incrementAndGet();
	}

	@Override
	public void
	rowExpanded(
		TableRowCore 	row )
	{
		if ( row.getParentRowCore() == null ){

			DownloadManager dm = (DownloadManager) row.getDataSource(true);

			dm.getDownloadState().setBooleanAttribute( DownloadManagerState.AT_FILES_EXPANDED, true );
		}
	}

	@Override
	public void
	rowCollapsed(
		TableRowCore 	row )
	{
		if ( row.getParentRowCore() == null ){

			DownloadManager dm = (DownloadManager) row.getDataSource(true);

			dm.getDownloadState().setBooleanAttribute( DownloadManagerState.AT_FILES_EXPANDED, false );
		}
	}

	public void
	collapseAll()
	{
		TableRowCore[] rows = tv.getRows();
		
		for ( TableRowCore row: rows ){
			
			if ( row.isExpanded()){
				
				row.setExpanded( false );
			}
		}
	}
	
	protected Class<?> getForDataSourceType() {
		return forDataSourceType;
	}

	private boolean isCurrent(Tag tag) {
		synchronized( currentTagsLock ){
			if (_currentTags != null) {
	  		for (Tag curTag : _currentTags) {
	  			if (tag.equals(curTag)) {
	  				return true;
	  			}
	  		}
			}
		}
		return false;
	}

	public boolean isCurrentTagsAny() {
		return currentTagsAny;
	}

	public void setCurrentTagsAny(boolean currentTagsAny) {
		if (this.currentTagsAny == currentTagsAny) {
			return;
		}
		this.currentTagsAny = currentTagsAny;
		synchronized( currentTagsLock ){
			setCurrentTags(_currentTags);
		}
	}
	
	private class 
	Divider
		extends Canvas 
		implements PaintListener
	{
		public 
		Divider(
			Composite 	parent, 
			int 		style ) 
		{
			super( parent, style | SWT.DOUBLE_BUFFERED );

			addPaintListener(this);
		}
		
		public void 
		paintControl(
			PaintEvent e) 
		{
			Rectangle clientArea = getClientArea();

			int x = clientArea.x + ( clientArea.width / 2 );
			e.gc.setAlpha(80);
			e.gc.drawLine(x, 2, x, clientArea.height - 3);
		}

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
				return new Point(0, 0);
			}
		}

		public Point computeSize(int wHint, int hHint, boolean changed, boolean realWidth) {
			
			for ( Control c: getParent().getChildren()){
				
				if ( c instanceof TagCanvas ){
					
					Point p = c.computeSize(wHint, hHint, changed);
					
					return( new Point( 5, p.y ));
				}
			}
			
			return( new Point(0,0));
		}
	}
}
