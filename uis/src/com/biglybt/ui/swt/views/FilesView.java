/*
 * Created on 17 juil. 2003
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
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoListener;
import com.biglybt.core.download.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagListener;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.TableViewFilterCheck.TableViewFilterCheckEx;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.views.file.FileInfoView;
import com.biglybt.ui.swt.views.table.*;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;
import com.biglybt.ui.swt.views.tableitems.files.*;
import com.biglybt.ui.swt.views.tableitems.mytorrents.AlertsItem;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.util.DLReferals;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.PlayUtils;

import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.pif.ui.tables.TableRowRefreshListener;


/**
 * @author Olivier
 * @author TuxPaper
 *         2004/Apr/23: extends TableView instead of IAbstractView
 */
public class FilesView
	extends TableViewTab<DiskManagerFileInfo>
	implements TableDataSourceChangedListener, TableSelectionListener,
	TableViewSWTMenuFillListener, TableRefreshListener, TableExpansionChangeListener,
	DownloadManagerStateAttributeListener, DownloadManagerListener,
	TableLifeCycleListener, TableViewFilterCheckEx<DiskManagerFileInfo>, KeyListener, ParameterListener,
	UISWTViewCoreEventListener, ViewTitleInfo
{
	private static final Object	KEY_DM_TREE_STATE = new Object();
	private static final int	ACTION_EXPAND 	= 0;
	private static final int	ACTION_COLLAPSE = 1;
	
	public static final Class<com.biglybt.pif.disk.DiskManagerFileInfo> PLUGIN_DS_TYPE = com.biglybt.pif.disk.DiskManagerFileInfo.class;
	boolean refreshing = false;

  private static TableColumnCore[] basicItems = {
    new NameItem(),
    new PathItem(),
    new PathNameItem(),
    new SizeItem(),
    new SizeBytesItem(),
    new DoneItem(),
    new PercentItem(),
    new FirstPieceItem(),
    new LastPieceItem(),
    new PieceCountItem(),
    new RemainingPiecesItem(),
    new PiecesDoneAndCountItem(),
    new ProgressGraphItem(),
    new ModeItem(),
    new PriorityItem(),
    new StorageTypeItem(),
    new FileExtensionItem(),
    new FileIndexItem(),
    new TorrentRelativePathItem(),
    new FileCRC32Item(),
    new FileMD5Item(),
    new FileSHA1Item(),
    new TorrentV2RootHashItem(),
    new FileAvailabilityItem(),
    new AlertsItem(  TableManager.TABLE_TORRENT_FILES ),
    new FileReadSpeedItem(),
    new FileWriteSpeedItem(),
    new FileETAItem(),
    new RelocatedItem(),
    new FileModifiedItem(),
    new DownloadNameItem(),
  };

  static{
	TableColumnManager tcManager = TableColumnManager.getInstance();

	tcManager.setDefaultColumnNames( TableManager.TABLE_TORRENT_FILES, basicItems );
  }

  public static final String MSGID_PREFIX = "FilesView";

  private DownloadManager[] managers = new DownloadManager[0];

  public boolean hide_dnd_files;
  public boolean tree_view;

  private volatile long selection_size;
  private volatile long selection_size_with_dnd;
  private volatile long selection_done;

  MenuItem path_item;

  TableViewSWT<DiskManagerFileInfo> tv;
	Button btnShowDND;
	Button btnTreeView;
	BufferedLabel lblHeader;

	private boolean	disableTableWhenEmpty	= true;
	private Object datasource;
	private Tag[] tags;
	private TagListener tag_listener = null;


	/**
   * Initialize
   */
	public FilesView() {
		super(MSGID_PREFIX);
	}
	
	@Override
	public TableViewSWT<DiskManagerFileInfo> initYourTableView() {
		registerPluginViews();

		tv = TableViewFactory.createTableViewSWT(PLUGIN_DS_TYPE,
				TableManager.TABLE_TORRENT_FILES, getPropertiesPrefix(), basicItems,
				"firstpiece", SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		
		tv.setExpandEnabled( true );
		
		basicItems = new TableColumnCore[0];

		tv.addTableDataSourceChangedListener(this, true);
		tv.addRefreshListener(this, true);
		tv.addSelectionListener(this, false);
		tv.addMenuFillListener(this);
		tv.addLifeCycleListener(this);
		tv.addKeyListener(this);
		tv.addExpansionChangeListener(this);

		tv.addRefreshListener( 
			new TableRowRefreshListener(){
				
				@Override
				public void 
				rowRefresh(TableRow row){
					if ( row instanceof TableRowSWT ){
						Object ds =  ((TableRowSWT)row).getDataSource( true );
		
						if ( ds instanceof FilesViewNodeInner ){
							
							((TableRowSWT)row).setFontStyle( SWT.ITALIC );
							((TableRowSWT)row).setAlpha( 220 );
						}
					}
				}
			});
		
		
		return tv;
	}

	private static void registerPluginViews() {
		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		if (vm.areCoreViewsRegistered(PLUGIN_DS_TYPE)) {
			return;
		}

		vm.registerView(PLUGIN_DS_TYPE,
				new UISWTViewBuilderCore("FileInfoView", null,
						FileInfoView.class));

		vm.setCoreViewsRegistered(PLUGIN_DS_TYPE);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.impl.TableViewTab#initComposite(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public Composite initComposite(Composite composite) {
		Composite parent = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		parent.setLayout(layout);

		Layout compositeLayout = composite.getLayout();
		if (compositeLayout instanceof GridLayout) {
			parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		} else if (compositeLayout instanceof FormLayout) {
			parent.setLayoutData(Utils.getFilledFormData());
		}

		Composite cTop = new Composite(parent, SWT.NONE);

		cTop.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		cTop.setLayout(new FormLayout());

			// hide dnd
		
		btnShowDND = new Button(cTop, SWT.CHECK);
		Messages.setLanguageText(btnShowDND, "FilesView.hide.dnd");
		btnShowDND.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				COConfigurationManager.setParameter("FilesView.hide.dnd", !hide_dnd_files);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		hide_dnd_files = COConfigurationManager.getBooleanParameter("FilesView.hide.dnd");

		btnShowDND.setSelection(hide_dnd_files);

			// tree view
		
		btnTreeView = new Button(cTop, SWT.CHECK);
		Messages.setLanguageText(btnTreeView, "OpenTorrentWindow.tree.view");
		btnTreeView.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				tree_view = btnTreeView.getSelection();
				
				COConfigurationManager.setParameter("FilesView.use.tree", tree_view);
				
					// grab before column visibility as this resets the selection :(
				
				selection_outstanding	= tv.getSelectedDataSources();

				if ( tree_view ){
					
					TableColumnSWTUtils.changeColumnVisiblity( tv, tv.getTableColumn( "name" ), true );
				}
				
				force_refresh 		= true;
				
				tableRefresh();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		tree_view = COConfigurationManager.getBooleanParameter("FilesView.use.tree");

		btnTreeView.setSelection(tree_view);
		
			// dunno why but doesnt draw on Linux with double-buffering
		
		lblHeader = new BufferedLabel(cTop, SWT.CENTER | ( Constants.isLinux?0: SWT.DOUBLE_BUFFERED));

		BubbleTextBox bubbleTextBox = new BubbleTextBox(cTop, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.SINGLE);
		Composite mainBubbleWidget = bubbleTextBox.getMainWidget();

		FormData fd = new FormData();
		fd.right = new FormAttachment(100, 0);
		fd.width = 140;
		
		bubbleTextBox.setMessageAndLayout(MessageText.getString("TorrentDetailsView.filter") , fd);

		fd = new FormData();
		fd.top = new FormAttachment(mainBubbleWidget, 10, SWT.CENTER);
		fd.left = new FormAttachment(0, 0);
		btnShowDND.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(mainBubbleWidget, 10, SWT.CENTER);
		fd.left = new FormAttachment(btnShowDND, 10);
		btnTreeView.setLayoutData(fd);
		
		fd = new FormData();
		fd.top = new FormAttachment(mainBubbleWidget, 10, SWT.CENTER);
		fd.left = new FormAttachment(btnTreeView, 10);
		fd.right = new FormAttachment(mainBubbleWidget, -10);
		lblHeader.setLayoutData(fd);

		tv.enableFilterCheck(bubbleTextBox, this, true );

		Composite tableParent = new Composite(parent, SWT.NONE);

		tableParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout gridLayout = new GridLayout();
		gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
		gridLayout.marginHeight = gridLayout.marginWidth = 0;
		tableParent.setLayout(gridLayout);

		parent.setTabList(new Control[] {tableParent, cTop});

		return tableParent;
	}

	private void
	addManagerListeners(
		DownloadManager[]		managers )
	{
		if ( managers == null ){
			return;
		}
		
		for (DownloadManager manager: managers ){
			manager.getDownloadState().addListener(this,
					DownloadManagerState.AT_FILE_LINKS2,
					DownloadManagerStateAttributeListener.WRITTEN);

			manager.addListener(this);
		}
	}
	
	private void
	removeManagerListeners(
		DownloadManager[]		managers )
	{
		if ( managers == null ){
			return;
		}

		for (DownloadManager manager: managers ){
			manager.getDownloadState().removeListener(this,
					DownloadManagerState.AT_FILE_LINKS2,
					DownloadManagerStateAttributeListener.WRITTEN);
			manager.removeListener(this);
		}
	}

  // @see TableDataSourceChangedListener#tableDataSourceChanged(java.lang.Object)
	@Override
	public void tableDataSourceChanged(Object newDataSource) {
		Tag[] newTags = DataSourceUtils.getTags(newDataSource);
		DownloadManager[] newManagers = DataSourceUtils.getDMs(newDataSource);
		
		if ( Arrays.deepEquals( newTags, tags ) && Arrays.equals( newManagers, managers )){
			
			return;
		}
		
		force_refresh = true;
		
		if (tv != null) {
	
			removeManagerListeners( managers );
		}

		if (tags != null && tags.length > 0 && tag_listener != null) {
			for (Tag tag : tags) {
				tag.removeTagListener( tag_listener);
			}
		}

		DownloadManager[] oldManagers = managers;
		managers = newManagers;
		tags = newTags;
		datasource = newDataSource;
		ViewTitleInfoManager.refreshTitleInfo(this);
		
		if (tags != null && tags.length > 0) {
			if (tag_listener == null) {
				tag_listener =
					new TagListener() {

						@Override
						public void taggableSync(Tag tag) {
						}

						@Override
						public void
						taggableRemoved(
							Tag t, Taggable tagged)
						{
							tableDataSourceChanged(datasource);
						}

						@Override
						public void
						taggableAdded(
							Tag t, Taggable tagged)
						{
							tableDataSourceChanged(datasource);
						}
					};
			}


			for (Tag tag : tags) {
				tag.addTagListener( tag_listener, false );
			}
		}

		if (tv == null) {
			return;
		}

		addManagerListeners( managers );

		if (!tv.isDisposed()) {

			if (tree_view) {
				tv.removeAllTableRows();
				current_root = null;
			} else {
				if (oldManagers.length == 1 && newManagers.length == 1) {
					tv.removeAllTableRows();
				}
				updateTable();
			}
			if ( disableTableWhenEmpty ){
				tv.setEnabled(managers.length>0);
			}
			updateHeader();
		}
	}

	// @see TableSelectionListener#deselected(TableRowCore[])
	@Override
	public void deselected(TableRowCore[] rows) {
		updateSelectedContent();
	}

	// @see TableSelectionListener#focusChanged(TableRowCore)
	@Override
	public void focusChanged(TableRowCore focus) {
	}

	// @see TableSelectionListener#selected(TableRowCore[])
	@Override
	public void selected(TableRowCore[] rows) {
		updateSelectedContent();
	}

	@Override
	public void
	stateChanged(
		DownloadManager manager,
		int		state )
	{
	}

	@Override
	public void
	downloadComplete(DownloadManager manager)
	{
	}

	@Override
	public void
	completionChanged(DownloadManager manager, boolean bCompleted)
	{
	}

	@Override
	public void
	positionChanged(DownloadManager download, int oldPosition, int newPosition)
	{
	}

	@Override
	public void
	filePriorityChanged( DownloadManager download, DiskManagerFileInfo file )
	{
		if ( hide_dnd_files ){

			tv.refilter();
		}
		
		if ( tree_view ){
			
			file = tree_file_map.get( file.getTorrentFile());
		}
		
		if ( file != null ){
			
			TableRowCore[] rows;
			
			if ( tree_view ){
				
					// have to get all rows, not just visible ones, as changed file might be collasped
					// but we still want to update parent state
				
				rows = tv.getRowsAndSubRows( true );	
				
			}else{
				
				rows = tv.getVisibleRows();
			}
			
			for ( TableRowCore row: rows ){
				
				DiskManagerFileInfo row_file = (DiskManagerFileInfo)row.getDataSource( true );
				
				if ( 	row_file == file || 
						( 	row_file.getIndex() == file.getIndex() && 
							row_file.getDownloadManager() == file.getDownloadManager())){
				
					while( row != null ){
						
						row.redraw();
					
						row = row.getParentRowCore();
					}
				}
			}
		}
	}
	
	@Override
	public void 
	fileLocationChanged(
		DownloadManager 		download, 
		DiskManagerFileInfo 	file )
	{
		if ( file == null ){
			
			tv.columnInvalidate("path", true);
			tv.columnInvalidate("pathname", true);
		
			tv.refreshTable(false);
			
		}else{
			
			TableRowCore row = tv.getRow( file );
			
			if ( row != null ){
				
				row.invalidate( true );
				
				row.refresh( true );
			}
		}
	}

	@Override
	public boolean
	filterCheck(
		DiskManagerFileInfo ds, String filter, boolean regex )
	{
		if ( hide_dnd_files ){
			
			if ( ds.isSkipped()){

				return( false );
				
			}else if ( ds instanceof FilesViewNodeInner ){
				
					// see if all kids skipped
				
				if (((FilesViewNodeInner)ds).getSkippedState() == 0 ){
					
					return( false );
				}
			}else{
				
				TOTorrentFile tf = ds.getTorrentFile();
				
				if ( tf != null && tf.isPadFile()){
					
					return( false );
				}
			}
		}

		if ( filter == null || filter.length() == 0 ){

			return( true );
		}

		if ( !tv.hasFilterControl() ){

				// view has no visible filter control so ignore any current filter as the
				// user's going to get confused...

			return( true );
		}

		if ( tree_view && ds instanceof FilesViewNodeInner ){
			
				// don't filter intermediate tree nodes
			
			return( true );
		}
		
		boolean filterOnPath = filter.startsWith("p:");
		if (filterOnPath) {
			filter = filter.substring(2);
		}
		
		try {			
			File file = ds.getFile(true);

			String name;
			
			if (filterOnPath) {
				name = file.getAbsolutePath();
			} else {
				if ( filter.startsWith( File.separator )){
					
					filter = filter.substring( 1 );
					
					if ( filter.isEmpty()){
						
						return( true );
					}
					
					name = file.getAbsolutePath();
					
				}else{
					
					name = file.getName();
				}
			}

			String s = regex ? filter : RegExUtil.splitAndQuote( filter, "\\s*[|;]\\s*" );

			boolean	match_result = true;

			if ( regex && s.startsWith( "!" )){

				s = s.substring(1);

				match_result = false;
			}

			Pattern pattern = RegExUtil.getCachedPattern( "fv:search", s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

			return( pattern.matcher(name).find() == match_result );

		} catch (Exception e) {

			return true;
		}
	}

	@Override
	public void filterSet(String filter)
	{
		// System.out.println( filter );
	}

	/* (non-Javadoc)
	 * @see TableViewFilterCheck.TableViewFilterCheckEx#viewChanged(TableView)
	 */
	@Override
	public void viewChanged(TableView<DiskManagerFileInfo> view) {
		updateHeader();
	}

	public void updateSelectedContent() {
		long	total_size 			= 0;
		long	total_dnd 			= 0;
		long	total_done			= 0;

		Object[] dataSources = tv.getSelectedDataSources(true);
		List<SelectedContent> listSelected = new ArrayList<>(dataSources.length);
		for (Object ds : dataSources) {
			if (ds instanceof DiskManagerFileInfo) {
				DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
				listSelected.add(new SelectedContent(fileInfo.getDownloadManager(),
						fileInfo.getIndex()));
				if ( fileInfo.isSkipped()){
					total_dnd += fileInfo.getLength();
				}else{
					total_size 	+= fileInfo.getLength();
					total_done	+= fileInfo.getDownloaded();
				}
			}
		}



		selection_size			= total_size;
		selection_size_with_dnd	= total_dnd + total_size;
		selection_done			= total_done;

		updateHeader();

		SelectedContent[] sc = listSelected.toArray(new SelectedContent[0]);
		SelectedContentManager.changeCurrentlySelectedContent(tv.getTableID(),
				sc, tv);
	}


	// @see TableSelectionListener#defaultSelected(TableRowCore[])
	@Override
	public void defaultSelected(TableRowCore[] rows, int stateMask, int origin ) {
		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) tv.getFirstSelectedDataSource();

		if ( fileInfo == null ){
			
			return;
		}

		if ( fileInfo instanceof FilesViewNodeInner ){
			
			FilesViewNodeInner node = (FilesViewNodeInner)fileInfo;
			
			List<FilesViewNodeInner>	nodes = Arrays.asList( node );
			
			try{
				tv.setRedrawEnabled( false );
				
				if ( node.isExpanded()){
									
					doTreeAction(nodes, ACTION_COLLAPSE, false );
					
				}else{
										
					doTreeAction(nodes, ACTION_EXPAND, false );
				}
			}finally{
				
				tv.setRedrawEnabled( true );
			}
			return;
			
		}else if ( fileInfo.getIndex() == -1 ){
			
			return;
		}

		boolean webInBrowser = COConfigurationManager.getBooleanParameter( "Library.LaunchWebsiteInBrowser" );

		if ( webInBrowser ){

			if ( ManagerUtils.browseWebsite( fileInfo )){

				return;
			}
		}

		String mode = COConfigurationManager.getStringParameter("list.dm.dblclick");

		if ( origin == 1 ){
			String enter_mode = COConfigurationManager.getStringParameter("list.dm.enteraction");
			if ( !enter_mode.equals( "-1" )){
				mode = enter_mode;
			}
		}
		
		switch (mode) {
			case "1":{

				DownloadManager dm = fileInfo.getDownloadManager();
				
				if ( dm != null ){
				
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, dm );
				}
				
				break;
			}
			case "2":

				boolean openMode = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");

				ManagerUtils.open(fileInfo, openMode);

				break;

			case "3":
			case "4":
				if (fileInfo.getAccessMode() == DiskManagerFileInfo.READ) {

					if (mode.equals("4") &&
							fileInfo.getDownloaded() == fileInfo.getLength() &&
							Utils.isQuickViewSupported(fileInfo)) {

						Utils.setQuickViewActive(fileInfo, true);

					} else {

						Utils.launch(fileInfo);
					}
				}
				break;

			case "5":
				ManagerUtils.browse(fileInfo);

				break;
				
			case "6":{

				DownloadManager dm = fileInfo.getDownloadManager();
				
				if ( dm != null ){
				
					UIFunctionsManager.getUIFunctions().getMDI().popoutEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, dm, true );
				}
				
				break;
			}
			default:

				int file_index = fileInfo.getIndex();
				DownloadManager dm = fileInfo.getDownloadManager();
				boolean canPlay = PlayUtils.canPlayDS(dm, file_index,true) || PlayUtils.canStreamDS(dm, file_index,true);

				UIFunctions uif = UIFunctionsManager.getUIFunctions();
				if (uif != null) {
					uif.playOrStreamDataSource(fileInfo, DLReferals.DL_REFERAL_PLAYDM,
							false, false);
					return;
				}

				if (fileInfo.getAccessMode() == DiskManagerFileInfo.READ) {

					Utils.launch(fileInfo);
				}
				break;
		}
	}

	private boolean
	doTreeAction(
		List<FilesViewNodeInner>		nodes,
		int								action,
		boolean							test_only )
	{
		TableRowCore[] tv_rows = tv.getRowsAndSubRows(true);
		
		Map<FilesViewNodeInner,TableRowCore>	node_to_row_map = new HashMap<>();
		
		for ( TableRowCore tv_row: tv_rows ){
			
			DiskManagerFileInfo ds_file = (DiskManagerFileInfo)tv_row.getDataSource(true);
			
			if ( ds_file instanceof FilesViewNodeInner ){
								
				node_to_row_map.put((FilesViewNodeInner)ds_file, tv_row );	
			}
		}
		
		for ( FilesViewNodeInner node: nodes ){
			
			if ( doTreeAction( node_to_row_map, node, action, true, test_only )){
				
				if ( test_only ){
					
					return( true );
				}
			}
		}
		
		return( false );
	}
	
	private boolean
	doTreeAction(
		Map<FilesViewNodeInner,TableRowCore>	node_to_row_map,
		FilesViewNodeInner						node,
		int										action,
		boolean									recursive,
		boolean									test_only )
	{
		if ( !node.isExpanded()){
			
			if ( action == ACTION_EXPAND ){
								
				if ( test_only ){
				
					return( true );
					
				}else{
					
					TableRowCore row = node_to_row_map.get( node );
					
					if ( row != null ){
					
						row.setExpanded( true );
					}
				}
			}
		}
		
		if ( recursive ){
			
			List<FilesView.FilesViewTreeNode> kids = node.getKids();
			
			for ( FilesView.FilesViewTreeNode kid: kids ){
				
				if ( kid instanceof FilesView.FilesViewNodeInner ){
			
					if ( doTreeAction(node_to_row_map, (FilesView.FilesViewNodeInner)kid, action, recursive, test_only )){
						
						if ( test_only ){
							
							return( true );
						}
					}
				}
			}
		}
		
			// must collapse all children first before this one otherwise the tree view height logic goes mental
		
		if ( node.isExpanded()){
			
			if ( action == ACTION_COLLAPSE ){
								
				if ( test_only ){
				
					return( true );
					
				}else{
					
						// must ensure all parents are expanded before collapsing 
					
					FilesViewNodeInner n = node.getParent();
					
					List<FilesViewNodeInner> path = new ArrayList<>();
						
					while( n != null ){
						
						path.add( n );
						
						n = n.getParent();
					}
					
					for ( int i=path.size()-1;i>=0;i--){
						
						n = path.get(i);
						
						if ( !n.isExpanded()){
							
							TableRowCore r = node_to_row_map.get( n );
							
							r.setExpanded( true );
						}
					}
					
					TableRowCore row = node_to_row_map.get( node );
					
					if ( row != null ){
					
						row.setExpanded( false );
					}
				}
			}
		}
		
		return( false );
	}
	
	// @see com.biglybt.ui.swt.views.TableViewSWTMenuFillListener#fillMenu(org.eclipse.swt.widgets.Menu)
	@Override
	public void fillMenu(String sColumnName, final Menu menu) {

		if ( managers.length == 0 ){

		}else if (managers.length == 1 ){

			Object[] data_sources = tv.getSelectedDataSources().toArray();

			List<DiskManagerFileInfo> files = new ArrayList<>();

			List<FilesView.FilesViewNodeInner>	inners = new ArrayList<>();
			
			List<DiskManagerFileInfo> inner_files = new ArrayList<>();
			
			Map<DiskManagerFileInfo,String>	structure_map = new HashMap<>();
			
			for ( int i=0;i<data_sources.length;i++ ){
				
				DiskManagerFileInfo file = (DiskManagerFileInfo)data_sources[i];
				
				if ( file instanceof FilesView.FilesViewNodeInner ){

					FilesView.FilesViewNodeInner inner = (FilesView.FilesViewNodeInner )file;
				
					List<DiskManagerFileInfo> temp = inner.getFiles( true );
					
					FilesView.FilesViewNodeInner parent = inner.getParent();
					
					String path = parent==null?"":parent.getPath();
					
					for ( DiskManagerFileInfo f: temp ){
						
						structure_map.put( f, path );
					}
					
					inner_files.addAll( temp );
					
					inners.add( inner );
									
				}else{
				
					files.add( file );
				}
			}

			if ( !files.isEmpty()){
				
				FilesViewMenuUtil.fillMenu(
					tv,
					sColumnName,
					menu,
					new DownloadManager[]{ managers[0] },
					new DiskManagerFileInfo[][]{ files.toArray(new DiskManagerFileInfo[files.size()]) },
					null,
					false,
					false );
				
			}else if ( !inners.isEmpty()){
								
				MenuItem mi = new MenuItem( menu, SWT.PUSH );
				
				mi.setText( MessageText.getString( "label.expand.all" ));
				
				mi.addListener(
					SWT.Selection,
					e->{
						doTreeAction( inners, ACTION_EXPAND, false );
					});
				
				mi.setEnabled( doTreeAction( inners, ACTION_EXPAND, true ));
				
				mi = new MenuItem( menu, SWT.PUSH );
				
				mi.setText( MessageText.getString( "menu.collapse.all" ));
				
				mi.addListener(
					SWT.Selection,
					e->{
						doTreeAction( inners, ACTION_COLLAPSE, false );
					});
				
				mi.setEnabled( doTreeAction( inners, ACTION_COLLAPSE, true ));
				
				new MenuItem( menu, SWT.SEPARATOR );
				
				if ( !inner_files.isEmpty()){
					
					FilesViewMenuUtil.fillMenu(
							tv,
							sColumnName,
							menu,
							new DownloadManager[]{ managers[0] },
							new DiskManagerFileInfo[][]{ inner_files.toArray(new DiskManagerFileInfo[inner_files.size()]) },
							structure_map,
							false,
							true );
									
					MenuBuildUtils.addSeparater( menu );
				}
			}
		}else{

			Object[] data_sources = tv.getSelectedDataSources().toArray();

			Map<DownloadManager,List<DiskManagerFileInfo>> map = new IdentityHashMap<>();

			List<DownloadManager> dms = new ArrayList<>();

			for ( Object ds: data_sources ){

				DiskManagerFileInfo file = (DiskManagerFileInfo)ds;

				if ( file instanceof FilesView.FilesViewNodeInner ){
					
					continue;
				}
				
				DownloadManager dm = file.getDownloadManager();

				List<DiskManagerFileInfo> list = map.get(dm);

				if ( list == null ){

					list = new ArrayList<>(dm.getDiskManagerFileInfoSet().nbFiles());

					map.put( dm, list );

					dms.add( dm );
				}

				list.add( file );
			}

			DownloadManager[] manager_list = dms.toArray( new DownloadManager[ dms.size()]);

			DiskManagerFileInfo[][] files_list = new DiskManagerFileInfo[manager_list.length][];

			for ( int i=0;i<manager_list.length;i++){

				List<DiskManagerFileInfo> list =  map.get( manager_list[i] );

				files_list[i] = list.toArray( new DiskManagerFileInfo[list.size()]);
			}

			if ( files_list.length > 0 ){
				
				FilesViewMenuUtil.fillMenu(tv, sColumnName, menu, manager_list, files_list, null, true, false );
			}
		}
	}


 
  private boolean 		force_refresh = false;
  private List<Object> 	selection_outstanding = null;
  
  @Override
  public void tableRefresh() {
  	if (refreshing)
  		return;

  	try {
	  	refreshing = true;
	    if (tv.isDisposed())
	      return;

	    updateTable();
	    
  	} finally {
  		refreshing = false;
  	}
  }

  /* SubMenu for column specific tasks.
   */
  @Override
	public void addThisColumnSubMenu(String sColumnName, Menu menuThisColumn) {
		switch (sColumnName) {
			case "path": {
				path_item = new MenuItem(menuThisColumn, SWT.CHECK);

				boolean show_full_path = COConfigurationManager.getBooleanParameter( "FilesView.show.full.path" );
				path_item.setSelection(show_full_path);

				Messages.setLanguageText(path_item, "FilesView.menu.showfullpath");

				path_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event e) {
						boolean show_full_path = path_item.getSelection();
						tv.columnInvalidate("path");
						tv.refreshTable(false);
						COConfigurationManager.setParameter("FilesView.show.full.path",
								show_full_path);
					}
				});

				break;
			}
			case "file_eta": {
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
			case "priority": {
				final MenuItem item = new MenuItem(menuThisColumn, SWT.CHECK);
				Messages.setLanguageText(item, "FilesView.hide.dnd");
				item.setSelection(hide_dnd_files);

				item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event e) {
						hide_dnd_files = item.getSelection();
						COConfigurationManager.setParameter("FilesView.hide.dnd",
								hide_dnd_files);
					}
				});
				break;
			}
		}
	}


  // Used to notify us of when we need to refresh - normally for external changes to the
  // file links.
  @Override
  public void attributeEventOccurred(DownloadManager dm, String attribute_name, int event_type) {
  	Object oIsChangingLinks = dm.getUserData("is_changing_links");
  	if ((oIsChangingLinks instanceof Boolean) && (Boolean) oIsChangingLinks) {
  		return;
  	}
	  this.force_refresh = true;
  }

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType,
			Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED: {
				createDragDrop();

				hide_dnd_files = COConfigurationManager.getBooleanParameter(
						"FilesView.hide.dnd");
				COConfigurationManager.addParameterListener("FilesView.hide.dnd", this);
				
				addManagerListeners( managers );
				
				break;
			}

			case EVENT_TABLELIFECYCLE_DESTROYED: {
				COConfigurationManager.removeParameterListener("FilesView.hide.dnd",
						this);

				removeManagerListeners( managers );
				
				break;
			}
		}
	}


  @Override
  public void tableViewTabInitComplete() {
  	updateSelectedContent();
  	super.tableViewTabInitComplete();
  }


	// @see TableSelectionListener#mouseEnter(TableRowCore)
	@Override
	public void mouseEnter(TableRowCore row) {
	}

	// @see TableSelectionListener#mouseExit(TableRowCore)
	@Override
	public void mouseExit(TableRowCore row) {
	}

	private void createDragDrop() {
		try {

			Transfer[] types = new Transfer[] { TextTransfer.getInstance(), FileTransfer.getInstance() };

			DragSource dragSource = tv.createDragSource(
					DND.DROP_COPY | DND.DROP_MOVE);
			if (dragSource != null) {
				dragSource.setTransfer(types);
				dragSource.addDragListener(new DragSourceAdapter() {
					private String eventData1;
					private String[] eventData2;

					@Override
					public void dragStart(DragSourceEvent event) {
						TableRowCore[] rows = tv.getSelectedRows();

						if (rows.length != 0 && managers.length > 0 ) {
							event.doit = true;
						} else {
							event.doit = false;
							return;
						}

						// Build eventData here because on OSX, selection gets cleared
						// by the time dragSetData occurs
						Object[] selectedDownloads = tv.getSelectedDataSources().toArray();
						eventData2 = new String[selectedDownloads.length];
						eventData1 = "DiskManagerFileInfo\n";

						for (int i = 0; i < selectedDownloads.length; i++) {
							DiskManagerFileInfo fi = (DiskManagerFileInfo) selectedDownloads[i];

							try {
								TOTorrent torrent = fi.getDownloadManager().getTorrent();
								if ( torrent != null ){
									eventData1 += torrent.getHashWrapper().toBase32String() + ";"
											+ fi.getIndex() + "\n";
								}
							} catch (Exception ignored) {
							}
							try {
								eventData2[i] = fi.getFile(true).getAbsolutePath();
  						} catch (Exception ignored) {
  						}
						}
					}



					@Override
					public void dragSetData(DragSourceEvent event) {
						if (FileTransfer.getInstance().isSupportedType(event.dataType)) {
							event.data = eventData2;
						} else {
							event.data = eventData1;
						}
					}
				});
			}
		} catch (Throwable t) {
			Logger.log(new LogEvent(LogIDs.GUI, "failed to init drag-n-drop", t));
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		boolean b = super.eventOccurred(event);
		if (event.getType() == UISWTViewEvent.TYPE_FOCUSGAINED) {
	    updateSelectedContent();
		} else if (event.getType() == UISWTViewEvent.TYPE_FOCUSLOST) {
			SelectedContentManager.clearCurrentlySelectedContent();
		}
		return b;
	}

	// @see org.eclipse.swt.events.KeyListener#keyPressed(org.eclipse.swt.events.KeyEvent)
	@Override
	public void 
	keyPressed(
		KeyEvent e) 
	{
		if ( e.keyCode == SWT.F2 && (e.stateMask & SWT.MODIFIER_MASK) == 0 ){
			
			FilesViewMenuUtil.rename(tv, tv.getSelectedDataSources(true), null, true, false,false);
			
			e.doit = false;
			
		}else{
			
			Composite table_composite = tv.getComposite();
			
			char character = e.character;
			
			if ( 	e.widget == table_composite &&
					( character == ' ' || character == '+' || character == '-' )){
		
				Object[] data_sources = tv.getSelectedDataSources().toArray();
							
				List<DiskManagerFileInfo>	files = new ArrayList<>();
				
				boolean all_skipped = true;
	
				for ( int i=0;i<data_sources.length;i++ ){
					
					DiskManagerFileInfo file = (DiskManagerFileInfo)data_sources[i];
													
					if ( file instanceof FilesViewNodeInner ){
						
						FilesViewNodeInner inner = (FilesViewNodeInner)file;
						
						inner.getFiles( files, true );
						
						if ( all_skipped ){
							
							int state = inner.getSkippedState();
													
							if ( state != 0 ){
								
								all_skipped = false;
							}
						}
					}else{
					
						files.add( file );
	
						if ( all_skipped ){
							
							if ( !file.isSkipped()){
								
								all_skipped = false;
							}
						}
					}
				}
				
				if ( character == ' ' ){
					
					FilesViewMenuUtil.setSkipped( files, !all_skipped, 2, true );
				}else{
					
					for ( DiskManagerFileInfo file: files ){
						
						int pri = file.getPriority();
						
						file.setPriority( character=='+'?pri+1:pri-1);
					}
				}
			
				e.doit = false;
			}  
		}
	}

	// @see org.eclipse.swt.events.KeyListener#keyReleased(org.eclipse.swt.events.KeyEvent)
	@Override
	public void keyReleased(KeyEvent e) {
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.config.ParameterListener#parameterChanged(java.lang.String)
	 */
	@Override
	public void parameterChanged(String parameterName) {
		if ("FilesView.hide.dnd".equals(parameterName)) {
			hide_dnd_files = COConfigurationManager.getBooleanParameter(parameterName);
			if (btnShowDND != null && !btnShowDND.isDisposed()) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (btnShowDND != null && !btnShowDND.isDisposed()) {
							btnShowDND.setSelection(hide_dnd_files);
						}
					}
				});
			}
			if (tv == null || tv.isDisposed()) {
				return;
			}
			tv.refilter();
		}
	}

	private void updateHeader() {
		if ( managers.length == 0 ){
			if (lblHeader != null) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (lblHeader == null || lblHeader.isDisposed()) {
							return;
						}
						lblHeader.setText("");
					}
				});
			}
			return;
		}

		int	total_rows		= 0;
		int	visible_rows;
		
		if ( tree_view ){
			
			int[]	nums = tv.getRowAndSubRowCount();
			
			total_rows 		= nums[0];
			visible_rows	= nums[1];
		}else{
			for ( DownloadManager manager: managers ){
				total_rows += manager.getNumFileInfos();
			}

			visible_rows = tv.getRowCount();
		}
		
		String s;
	
		s = MessageText.getString("library.unopened.header"
				+ (total_rows > 1 ? ".p" : ""), new String[] {
					String.valueOf(total_rows)
					});
		if (total_rows != visible_rows) {
			s = MessageText.getString("v3.MainWindow.xofx", new String[] {
				String.valueOf(visible_rows),
				s
			});
		}
		
		s += getSelectionText();

		final String sHeader = s;

		if (lblHeader != null) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (lblHeader == null || lblHeader.isDisposed()) {
						return;
					}
					lblHeader.setText(sHeader);
				}
			});
		}
	}

	private String
	getSelectionText()
	{
		int selection_count = tv.getSelectedRowsSize();
		if (selection_count == 0) {
			return "";
		}

		String str = ", " +
				MessageText.getString(
				"label.num_selected", new String[]{ String.valueOf( selection_count )});

		if ( selection_size_with_dnd > 0 ){

			if ( selection_size == selection_size_with_dnd ){
				
				if ( selection_size == selection_done ){
	
					str += " (" + DisplayFormatters.formatByteCountToKiBEtc( selection_size ) + ")";
					
				}else{
					
					str += " (" + DisplayFormatters.formatByteCountToKiBEtc( selection_done ) + "/" + DisplayFormatters.formatByteCountToKiBEtc( selection_size ) + ")";
	
				}
			}else{
				str += " (" + DisplayFormatters.formatByteCountToKiBEtc( selection_done ) + "/" + DisplayFormatters.formatByteCountToKiBEtc( selection_size ) + "/" + DisplayFormatters.formatByteCountToKiBEtc( selection_size_with_dnd ) + ")";

			}
		}

		return( str );
	}

	public void
	setDisableWhenEmpty(
		boolean	b )
	{
		disableTableWhenEmpty	= b;
	}
	
	public void 
	rowExpanded(
		TableRowCore row )
	{
		updateTreeExpansionState( row, true );
	}

	public void 
	rowCollapsed(
		TableRowCore row )
	{
		updateTreeExpansionState( row, false );
	}
	
	private void
	updateTreeExpansionState(
		TableRowCore	row,
		boolean			expanded )
	{
		Object ds = row.getDataSource( true );
		
		if ( ds instanceof FilesViewNodeInner ){
			
			FilesViewNodeInner node = (FilesViewNodeInner)ds;
			
			DownloadManager dm = node.getDownloadManager();
			
			if ( dm != null ){
				
				int uid = node.getUID();
				
				synchronized( KEY_DM_TREE_STATE ){
				
					Map<Integer,Boolean> map = (Map<Integer,Boolean>)dm.getUserData( KEY_DM_TREE_STATE );
					
					if ( map == null ){
						
						map = new HashMap<>();
						
						dm.setUserData( KEY_DM_TREE_STATE, map );
					}
					
					if ( expanded ){
						
						map.remove( uid );
						
					}else{
						
						map.put( uid, Boolean.TRUE );
					}
				}
			}
		}
	}
	
	private void
	updateTable()
	{
		boolean	sync = false;
		
		List<Object>	to_select;
		
		if ( selection_outstanding != null  ){
						
			sync = true;
			
			to_select = selection_outstanding;
			
			selection_outstanding = null;
			
		}else{
			
			to_select = null;
		}
				
		if ( !tree_view ){
			
			updateFlatView( sync );
			
		}else{
			
			updateTreeView( sync );
		}
		
		if ( to_select != null ){
			
			AEThread2.createAndStartDaemon(
				"TableSelector",
				()->{
			
					long	start = SystemTime.getMonotonousTime();
					
					while( tv.hasChangesPending()){
					
						if ( SystemTime.getMonotonousTime() - start > 5000 ){
							
							return;
						}
						
						try{
							Thread.sleep( 100 );
							
						}catch( Throwable e ){
							
						}
					}
					
					Utils.execSWTThread(
						()->{
							List<TableRowCore>	selected_rows = new ArrayList<>();
							
							TableRowCore[] tv_rows = tv.getRowsAndSubRows(false);
							
							Map<DiskManagerFileInfo,TableRowCore>	file_to_row_map = new HashMap<>();
							
							for ( TableRowCore tv_row: tv_rows ){
								
								DiskManagerFileInfo ds_file = (DiskManagerFileInfo)tv_row.getDataSource(true);
								
								if ( ds_file instanceof FilesViewNodeLeaf ){
									
									DiskManagerFileInfo target = ((FilesViewNodeLeaf)ds_file).getTarget();
									
									file_to_row_map.put( target, tv_row );
									
								}else if ( ds_file instanceof FilesViewNodeInner ){
											
								}else{
									
									file_to_row_map.put( ds_file, tv_row );
								}
							}
							
							for ( Object o: to_select ){
								
								TableRowCore row = null;
								
								if ( o instanceof FilesViewTreeNode ){
									
									if ( o instanceof FilesViewNodeLeaf ){
										
										row = file_to_row_map.get( ((FilesViewNodeLeaf)o).getTarget());
									}
								}else if ( o instanceof FilesViewNodeInner ){
									
								}else{
									
									row = file_to_row_map.get( o );
								}
								
								if ( row != null ){
							
									selected_rows.add( row );
								}
							}
							
							if ( !selected_rows.isEmpty()){
								
								tv.setSelectedRows( selected_rows.toArray( new TableRowCore[0]));
							
							}
						});
				});
		}
	}
	
	private FilesViewNodeInner	current_root;
	private Map<TOTorrentFile,FilesViewNodeLeaf>	tree_file_map = new IdentityHashMap<>();
	
	private void
	updateTreeView(
		boolean		sync )
	{
		int	num_managers = managers.length;
		
		boolean	wait_until_idle = false;
		
		if ( num_managers == 0 ){
			
			if ( tv.getRowCount() > 0 ){
				
				tv.removeAllTableRows();
				
				if ( sync ){
					
					tv.processDataSourceQueueSync();
					
				}else{
				
					tv.processDataSourceQueue();
				}
			}
			
			current_root = null;
			
		}else if ( num_managers == 1 ){
			
			wait_until_idle = force_refresh;
			
			DownloadManager dm =  managers[0];
			
			if ( force_refresh || current_root == null || current_root.dm != dm ||  tv.getRowCount() == 0 ){

				force_refresh = false;

				tv.removeAllTableRows();

				char file_separator = File.separatorChar;

				int uid = 0;
				
				FilesViewNodeInner root = current_root = new FilesViewNodeInner( dm, uid, dm.getDisplayName(), "", null );

				tree_file_map.clear();
				
				Map<Integer,Boolean>	expansion_state;
				
				synchronized( KEY_DM_TREE_STATE ){
					
					expansion_state = (Map<Integer,Boolean>)dm.getUserData( KEY_DM_TREE_STATE );
					
					if ( expansion_state == null ){
						
						expansion_state = new HashMap<>();
						
					}else{
						
						expansion_state = new HashMap<>( expansion_state );
					}
				}
				
				if ( expansion_state.containsKey( uid )){
					
					root.setExpanded( false );
				}
				
				uid++;
				
				DiskManagerFileInfo files[] = dm.getDiskManagerFileInfoSet().getFiles();

				for ( DiskManagerFileInfo file: files ){

					FilesViewNodeInner node = root;

					TOTorrentFile t_file = file.getTorrentFile();

					String path = t_file.getRelativePath();

					int	pos = 0;
				
					String subfolder = "";
					
					while( true ){

						int p = path.indexOf( file_separator, pos );

						String	bit;

						if ( p == -1 ){

							FilesViewNodeLeaf leaf = new FilesViewNodeLeaf( path, file, node );
							
							node.addFile( leaf );

							tree_file_map.put( t_file, leaf );
							
							break;
							
						}else{
									
							bit = path.substring( pos, p );

							pos = p+1;
						
							FilesViewNodeInner n = (FilesViewNodeInner)node.getChild( bit );

							if ( n == null ){
	
								n = new FilesViewNodeInner( dm, uid, bit, subfolder, node );
	
								if ( expansion_state.containsKey( uid )){
									
									n.setExpanded( false );
								}
								
								uid++;
								
								node.addChild( n );
							}
							
							if ( subfolder.isEmpty()){
								subfolder = bit;
							}else{
								subfolder += file_separator + bit;
							}
							node = n;
						}
					}
				}			

				tv.addDataSource( root );

				tv.processDataSourceQueueSync();
			}
		}else{
			
			if ( current_root != null ){
				
				Object[] kids = current_root.getChildDataSources();
				
				if ( kids.length != num_managers ){
					
					force_refresh = true;
					
				}else{
					
					Set<DownloadManager> kids_set = new IdentityHashSet<>();
				
					for ( Object k: kids ){
						
						kids_set.add(((FilesViewNodeInner)k).getDownloadManager());
					}
					
					for ( int i=0;i<num_managers;i++){
						
						if ( !kids_set.contains( managers[i])){
							
							force_refresh = true;
						}
					}
				}
			}
		
			if ( force_refresh || current_root == null || tv.getRowCount() == 0 ){
				
				wait_until_idle = force_refresh;
				
				current_root = new FilesViewNodeInner( null, -1,  MessageText.getString( "label.downloads" ), "", null );
				
				tree_file_map.clear();
				
				force_refresh = false;

				tv.removeAllTableRows();

				char file_separator = File.separatorChar;
				
				int num = 0;
				
				for ( DownloadManager dm: managers ){
				
					num++;
					
					int uid = 0;

					FilesViewNodeInner root =  new FilesViewNodeInner( dm, uid, "(" + num + ") " + dm.getDisplayName(), "", current_root );
	
					Map<Integer,Boolean>	expansion_state;
					
					synchronized( KEY_DM_TREE_STATE ){
						
						expansion_state = (Map<Integer,Boolean>)dm.getUserData( KEY_DM_TREE_STATE );
						
						if ( expansion_state == null ){
							
							expansion_state = new HashMap<>();
							
						}else{
							
							expansion_state = new HashMap<>( expansion_state );
						}
					}
					
					if ( expansion_state.containsKey( uid )){
						
						root.setExpanded( false );
					}
					
					uid++;
					
					current_root.addChild( root );
					
					DiskManagerFileInfo files[] = dm.getDiskManagerFileInfoSet().getFiles();

					for ( DiskManagerFileInfo file: files ){
	
						FilesViewNodeInner node = root;
	
						TOTorrentFile t_file = file.getTorrentFile();
	
						String path = t_file.getRelativePath();
	
						int	pos = 0;
					
						String subfolder = "";
						
						while( true ){
	
							int p = path.indexOf( file_separator, pos );
	
							String	bit;
	
							if ( p == -1 ){
	
								FilesViewNodeLeaf leaf = new FilesViewNodeLeaf( path, file, node );
								
								node.addFile( leaf );
	
								tree_file_map.put( t_file, leaf );
								
								break;
								
							}else{
									
								bit = path.substring( pos, p );
	
								pos = p+1;
							
								FilesViewNodeInner n = (FilesViewNodeInner)node.getChild( bit );
	
								if ( n == null ){
		
									n = new FilesViewNodeInner( dm, uid, bit, subfolder, node );
		
									if ( expansion_state.containsKey( uid )){
										
										n.setExpanded( false );
									}
									
									uid++;
									
									node.addChild( n );
								}
								
								if ( subfolder.isEmpty()){
									subfolder = bit;
								}else{
									subfolder += file_separator + bit;
								}
								
								node = n;
							}
						}
					}			
				}
				
				tv.addDataSource( current_root );

				tv.processDataSourceQueueSync();
			}
		}
		
		if ( wait_until_idle ){
			
			AEThread2.createAndStartDaemon(
					"TableSorter",
					()->{
						long	start = SystemTime.getMonotonousTime();
						
							// this has the added bonus of forcing a sort which we particularly need if the user
							// has disabled automatic resorts - we want the initial view to at least be sorted
						
						while( tv.hasChangesPending()){
						
							if ( SystemTime.getMonotonousTime() - start > 5000 ){
								
								return;
							}
							
							try{
								Thread.sleep( 100 );
								
							}catch( Throwable e ){
								
							}
						}
					});	
		}
	}
	
	private static Comparator<String> tree_comp = new FormattersImpl().getAlphanumericComparator( true );

	public interface
	FilesViewTreeNode
	{
		public String
		getName();
		
		public FilesViewNodeInner
		getParent();
		
		public List<FilesViewTreeNode>
		getKids();
		
		public int
		getDepth();
		
		public boolean
		isLeaf();
		
		public int
		getSkippedState();
		
		public void
		setSkipped(
			boolean		b );
		
		public Boolean
		isSkipping();
		
		public void
		recheck();
		
		public long
		getLength();
		
		public long
		getDownloaded();
		
		public void
		getPieceInfo(
			int[]	data );
	}

	@Override
	public void parentDataSourceChanged(Object newParentDataSource) {
		super.parentDataSourceChanged(newParentDataSource);
		tableDataSourceChanged(newParentDataSource);
	}

	@Override
	public Object getTitleInfoProperty(int propertyID) {
		if (propertyID == ViewTitleInfo.TITLE_INDICATOR_TEXT) {
			int count = 0;
			for (DownloadManager manager : managers) {
				count += manager.getNumFileInfos();
			}
			return count == 0 ? null : "" + count;
		}
		return null;
	}

	private static class
	FilesViewNodeInner
		implements DiskManagerFileInfo, FilesViewTreeNode, TableRowSWTChildController
	{
		private final DownloadManager						dm;
		private final int									uid;
		private final String								node_name;
		private final String								node_path;
		private final FilesViewNodeInner					parent;
		private final Map<String,FilesViewTreeNode>			kids = new TreeMap<>( tree_comp );
		
		private boolean				expanded	= true;
		
		private long	size;
		private int[]	pieceInfo;
		
		private
		FilesViewNodeInner(
			DownloadManager			_dm,
			int						_uid,
			String					_node_name,
			String					_node_path,
			FilesViewNodeInner		_parent )
		{
			dm			= _dm;
			uid			= _uid;
			node_name	= _node_name;
			node_path	= _node_path;
			parent		= _parent;
		}
		
		protected int
		getUID()
		{
			return( uid );
		}
		
		@Override
		public FilesViewNodeInner 
		getParent()
		{
			return( parent );
		}
		
		@Override
		public List<FilesViewTreeNode>
		getKids()
		{
			return( new ArrayList<>( kids.values()));
		}
		
		@Override
		public boolean 
		isLeaf()
		{
			return( false );
		}
		
		private FilesViewTreeNode
		getChild(
			String	name )
		{
			return( kids.get(name));
		}

		private void
		addChild(
			FilesViewNodeInner		child )
		{
			kids.put( child.getName(), child );
		}

		protected List<DiskManagerFileInfo>
		getFiles(
			boolean	recursive )
		{
			List<DiskManagerFileInfo> result = new ArrayList<>();
			
			getFiles( result, recursive );
			
			return( result );
		}
		
		private void
		getFiles(
			List<DiskManagerFileInfo>		files,
			boolean							recursive )
		{
			for ( FilesViewTreeNode kid: kids.values()){
				
				if ( kid.isLeaf()){
					
					files.add(((FilesViewNodeLeaf)kid).getTarget());
					
				}else{
					
					if ( recursive ){
						
						((FilesViewNodeInner)kid).getFiles( files, recursive );
					}
				}
			}
		}
		
		@Override
		public String
		getName()
		{
			return( node_name );
		}

		protected String
		getPath()
		{
			return( node_path );
		}
		
		@Override
		public boolean
		isExpanded()
		{
			return( expanded );
		}

		@Override
		public void
		setExpanded(
			boolean	e )
		{
			expanded = e;
		}

		@Override
		public int
		getDepth()
		{
			if ( parent == null ){
				return( 0 );
			}else{
				return( parent.getDepth() + 1 );
			}
		}
		
		private void
		addFile(
			FilesViewNodeLeaf		f )
		{
			kids.put( f.getName(), f );
		}

		@Override
		public Object[]
		getChildDataSources()
		{
			return( kids.values().toArray());
		}

		@Override
		public void
		setPriority(int p)
		{
		}

		@Override
		public void
		setSkipped(
			boolean b )
		{	
			for ( FilesViewTreeNode kid: kids.values()){
				
				kid.setSkipped( b );
			}
		}

		@Override
		public Boolean 
		isSkipping()
		{
			Boolean result = null;
			
			for ( FilesViewTreeNode kid: kids.values()){
				
				Boolean k = kid.isSkipping();
				
				if ( k == null ){
					
					if ( result != null ){
						
						return( null );
					}
				}else{
					
					if ( result == null ){
						
						result = k;
						
					}else if ( result.booleanValue() != k ){
						
						return( null );
					}
				}
			}
			
			return( result );
		}

		@Override
		public boolean
		setLink(
			File	link_destination )
		{
			return( false );
		}
		
		@Override
		public boolean exists(){
			return( false );
		}

		@Override
		public String getLastError(){
			return( "Invalid operation on tree node" );
		}
		
		@Override
		public boolean
		setLinkAtomic(File link_destination)
		{
			return( false );
		}

		@Override
		public boolean
		setLinkAtomic(File link_destination, FileUtil.ProgressListener pl )
		{
			return( false );
		}

		@Override
		public File
		getLink()
		{
			return( null );
		}

		@Override
		public boolean
		setStorageType(int type, boolean force )
		{
			return( false );
		}

		@Override
		public int
		getStorageType()
		{
			return( -1 );
		}

		@Override
		public int
		getAccessMode()
		{
			return( -1 );
		}

		@Override
		public long
		getDownloaded()
		{
			long	temp = 0;
			
			for ( FilesViewTreeNode kid: kids.values()){
				
				temp += kid.getDownloaded();
			}
			
			return( temp );
		}

		@Override
		public long
		getLastModified()
		{
			return( -1 );
		}

		@Override
		public String
		getExtension()
		{
			return( "" );
		}

		@Override
		public int
		getFirstPieceNumber()
		{
			if ( dm == null ){
				return( -1 );
			}
			
			if ( pieceInfo == null ){
				getPieceInfo();
			}
			return( pieceInfo[0] );
		}

		@Override
		public int
		getLastPieceNumber()
		{
			if ( dm == null ){
				return( -1 );
			}
			
			if ( pieceInfo == null ){
				getPieceInfo();
			}
			return( pieceInfo[1] );
		}

		private void
		getPieceInfo()
		{
			int[] temp = { Integer.MAX_VALUE, 0 };
			getPieceInfo(temp);
			pieceInfo = temp;
		}

		@Override
		public void
		getPieceInfo(
			int[]	data )
		{
			for ( FilesViewTreeNode kid: kids.values()){
				kid.getPieceInfo(data);
			}
		}

		@Override
		public int
		getNbPieces()
		{
			if ( dm == null ){
				return( -1 );
			}
			
			return( getLastPieceNumber() - getFirstPieceNumber() + 1 );
		}

		@Override
		public long
		getLength()
		{
			if ( size == 0 ){
			
				long	temp = 0;
				
				for ( FilesViewTreeNode kid: kids.values()){
					
					temp += kid.getLength();
				}
				
				size = temp;
			}
			
			return( size );
		}

		@Override
		public int
		getPriority()
		{
			return( -1 );
		}

		@Override
		public boolean
		isSkipped()
		{
			return( false );
		}

		@Override
		public int
		getSkippedState()
		{
			boolean	all_skipped 	= true;
			boolean all_not_skipped	= true;
			
			for ( FilesViewTreeNode kid: kids.values()){
				
				int	state = kid.getSkippedState();
				
				if ( state == 0 ){
					all_not_skipped = false;
				}else if ( state == 1 ){
					all_skipped = false;
				}else{
					return( 2 );
				}
			}
			
			if ( all_skipped ){
				return( 0 );
			}else if ( all_not_skipped ){
				return(  1 );
			}else{
				return( 2 );
			}
		}

		@Override
		public int
		getIndex()
		{
			return( -1 );
		}

		@Override
		public DownloadManager
		getDownloadManager()
		{
			return( dm );
		}

		@Override
		public DiskManager
		getDiskManager()
		{
			return( dm==null?null:dm.getDiskManager());
		}

		@Override
		public File
		getFile( boolean follow_link )
		{
			return( node_path.isEmpty()?new File(node_name):new File( node_path, node_name ));
		}

		@Override
		public TOTorrentFile
		getTorrentFile()
		{
			return( null );
		}

		@Override
		public DirectByteBuffer
		read(
			long	offset,
			int		length )

			throws IOException
		{
			throw( new IOException( "Not implemented" ));
		}

		@Override
		public void
		flushCache()

			throws	Exception
		{
		}

		@Override
		public int
		getReadBytesPerSecond()
		{
			return( -1 );
		}

		@Override
		public int
		getWriteBytesPerSecond()
		{
			return( -1 );
		}

		@Override
		public long
		getETA()
		{
			return( -1 );
		}
		
		@Override
		public void 
		recheck()
		{
		}

		@Override
		public void
		close()
		{}

		@Override
		public void
		addListener(
			DiskManagerFileInfoListener	listener )
		{}

		@Override
		public void
		removeListener(
			DiskManagerFileInfoListener	listener )
		{}
	}
	
	private static class
	FilesViewNodeLeaf
		implements DiskManagerFileInfo, FilesViewTreeNode
	{
		private final String					name;
		private final FilesViewNodeInner		parent;
		private final DiskManagerFileInfo		delegate;
		
		private
		FilesViewNodeLeaf(
			String				_name,
			DiskManagerFileInfo	_delegate,
			FilesViewNodeInner	_parent )
		{
			name		= _name;
			delegate	= _delegate;
			parent		= _parent;
		}
		
		protected DiskManagerFileInfo
		getTarget()
		{
			return( delegate );
		}
		
		@Override
		public String
		getName()
		{
			return( name );
		}
		
		@Override
		public FilesViewNodeInner 
		getParent()
		{
			return( parent );
		}
		
		@Override
		public List<FilesViewTreeNode>
		getKids()
		{
			return( Collections.emptyList());
		}
		
		@Override
		public boolean 
		isLeaf()
		{
			return( true );
		}

		@Override
		public int
		getDepth()
		{
			return( parent.getDepth() + 1 );
		}

		@Override
		public void
		setPriority(int p)
		{
			delegate.setPriority(p);
		}

		@Override
		public void setSkipped(boolean b)
		{	
			ManagerUtils.setFileSkipped( delegate, b );
		}

		@Override
		public Boolean 
		isSkipping()
		{
			return( delegate.isSkipping());
		}
		
		@Override
		public boolean
		setLink(
			File	link_destination )
		{
			return( delegate.setLink(link_destination));
		}

		@Override
		public String getLastError(){
			return( delegate.getLastError());
		}
		
		@Override
		public boolean
		setLinkAtomic(File link_destination)
		{
			return( delegate.setLinkAtomic(link_destination));
		}

		@Override
		public boolean
		setLinkAtomic(File link_destination, FileUtil.ProgressListener pl )
		{
			return( delegate.setLinkAtomic(link_destination, pl));
		}

		@Override
		public File
		getLink()
		{
			return( delegate.getLink());
		}

		@Override
		public boolean
		setStorageType(int type, boolean force )
		{
			return( delegate.setStorageType(type, force));
		}

		@Override
		public int
		getStorageType()
		{
			return( delegate.getStorageType());
		}

		@Override
		public int
		getAccessMode()
		{
			return( delegate.getAccessMode());
		}

		@Override
		public long
		getDownloaded()
		{
			return( delegate.getDownloaded());
		}

		@Override
		public long
		getLastModified()
		{
			return( delegate.getLastModified());
		}

		@Override
		public String
		getExtension()
		{
			return( delegate.getExtension());
		}

		@Override
		public int
		getFirstPieceNumber()
		{
			return( delegate.getFirstPieceNumber());
		}

		@Override
		public int
		getLastPieceNumber()
		{
			return( delegate.getLastPieceNumber());
		}

		@Override
		public void
		getPieceInfo(
			int[]	data )
		{
			int first 	= getFirstPieceNumber();
			int last	= getLastPieceNumber();
			
			if ( first < data[0] ){
				data[0] = first;
			}
			
			if ( last > data[1] ){
				data[1] = last;
			}
		}

		@Override
		public long
		getLength()
		{
			return( delegate.getLength());
		}

		@Override
		public int
		getNbPieces()
		{
			return( delegate.getNbPieces());
		}

		@Override
		public int
		getPriority()
		{
			return( delegate.getPriority());
		}

		@Override
		public boolean
		isSkipped()
		{
			return( delegate.isSkipped());
		}

		@Override
		public int
		getSkippedState()
		{
			if ( delegate.isSkipped()){
				return( 0 );
			}else{
				return( 1 );
			}
		}

		@Override
		public boolean exists(){
			return( delegate.exists());
		}
		  
		@Override
		public int
		getIndex()
		{
			return( delegate.getIndex());
		}

		@Override
		public DownloadManager
		getDownloadManager()
		{
			return( delegate.getDownloadManager());
		}

		@Override
		public DiskManager
		getDiskManager()
		{
			return( delegate.getDiskManager());
		}

		@Override
		public File
		getFile( boolean follow_link )
		{
			return( delegate.getFile(follow_link));
		}

		@Override
		public TOTorrentFile
		getTorrentFile()
		{
			return( delegate.getTorrentFile());
		}

		@Override
		public DirectByteBuffer
		read(
			long	offset,
			int		length )

			throws IOException
		{
			return( delegate.read(offset, length));
		}

		@Override
		public void
		flushCache()

			throws	Exception
		{
			delegate.flushCache();
		}

		@Override
		public int
		getReadBytesPerSecond()
		{
			return( delegate.getReadBytesPerSecond());
		}

		@Override
		public int
		getWriteBytesPerSecond()
		{
			return( delegate.getWriteBytesPerSecond());
		}

		@Override
		public long
		getETA()
		{
			return( delegate.getETA());
		}
		
		@Override
		public void recheck()
		{
			delegate.recheck();
		}

		@Override
		public void
		close()
		{
			delegate.close();
		}

		@Override
		public void
		addListener(
			DiskManagerFileInfoListener	listener )
		{
			delegate.addListener(listener);
		}

		@Override
		public void
		removeListener(
			DiskManagerFileInfoListener	listener )
		{
			delegate.removeListener(listener);
		}
	}
	
		
	private void
	updateFlatView(
		boolean		sync )
	{
		if (!force_refresh) {
			return;
		}
		if (!sync && Utils.isSWTThread()) {
			Utils.getOffOfSWTThread(() -> updateFlatView(false));
			return;
		}
		force_refresh = false;

		List<DiskManagerFileInfo> files = getFileInfo();

		if (files.size() == 0) {
			tv.removeAllTableRows();
		} else {
			Collection<DiskManagerFileInfo> toRemove = tv.getDataSources(true);
			// If we are removing a lot of rows, it's faster to clear and add 
			if (toRemove.size() - files.size() > 50000) {
				// todo: restore selectedRows
				tv.removeAllTableRows();
				toRemove = new HashSet<>();
			}
			List<DiskManagerFileInfo> toAdd = new ArrayList<>();
			for (DiskManagerFileInfo info : files) {
				if (toRemove.contains(info)) {
					toRemove.remove(info);
				} else {
					toAdd.add(info);
				}
			}
			tv.removeDataSources(toRemove.toArray(new DiskManagerFileInfo[0]));
			tv.addDataSources(toAdd.toArray(new DiskManagerFileInfo[0]));
			tv.tableInvalidate();

			if ( sync ){

				tv.processDataSourceQueueSync();

			}else{

				tv.processDataSourceQueue();
			}
			
			AEThread2.createAndStartDaemon(
				"TableSorter",
				()->{
					long	start = SystemTime.getMonotonousTime();
					
						// this has the added bonus of forcing a sort which we particularly need if the user
						// has disabled automatic resorts - we want the initial view to at least be sorted
					
					while( tv.hasChangesPending()){
					
						if ( SystemTime.getMonotonousTime() - start > 5000 ){
							
							return;
						}
						
						try{
							Thread.sleep( 100 );
							
						}catch( Throwable e ){
							
						}
					}
				});
		}
	}
	
	private List<DiskManagerFileInfo>
			getFileInfo()
	{
		List<DiskManagerFileInfo>	temp = new ArrayList<>();

		if (managers.length == 0 ){

			return temp;

		}else if ( managers.length == 1 ){

			DiskManagerFileInfo[] files = managers[0].getDiskManagerFileInfoSet().getFiles();
			temp.addAll(Arrays.asList(files));

		}else{


			for ( DownloadManager dm: managers ){

				temp.addAll( Arrays.asList( dm.getDiskManagerFileInfoSet().getFiles()));
			}

		}

		return( temp );
	}
	  
	private boolean doAllExist(DiskManagerFileInfo[] files) {
		for (DiskManagerFileInfo fileinfo : files) {
			if (tv.isFiltered(fileinfo)) {
				// We can't just use tv.dataSourceExists(), since it does a .equals()
				// comparison, and we want a reference comparison

				TableRowCore row = tv.getRow(fileinfo);
				if (row == null) {
					return false;
				}
				// reference comparison
				if (row.getDataSource(true) != fileinfo) {
					return false;
				}
			}
		}
		return true;
	}

}
