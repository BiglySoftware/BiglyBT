/*
 * Created on May 10, 2013
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


import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import com.biglybt.core.CoreFactory;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.columns.dlhistory.*;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.skin.SWTSkinObjectTextbox;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManagerEvent;
import com.biglybt.core.history.DownloadHistory;
import com.biglybt.core.history.DownloadHistoryEvent;
import com.biglybt.core.history.DownloadHistoryListener;
import com.biglybt.core.history.DownloadHistoryManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.table.utils.TableColumnFilterHelper;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

import com.biglybt.core.util.RegExUtil;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.swt.skin.SWTSkinObject;


public class SBC_DownloadHistoryView
	extends SkinView
	implements UIUpdatable, UIPluginViewToolBarListener, TableViewFilterCheck<DownloadHistory>,
	TableViewSWTMenuFillListener, TableSelectionListener, DownloadHistoryListener
{
	private static final String TABLE_NAME = "DownloadHistory";

	private static final DownloadHistoryManager dh_manager =
			(DownloadHistoryManager) CoreFactory.getSingleton().getGlobalManager().getDownloadHistoryManager();

	private TableViewSWT<DownloadHistory> tv;

	private TableColumnFilterHelper<DownloadHistory>	col_filter_helper;
	 
	private Composite table_parent;

	private boolean columnsAdded = false;

	private boolean dh_listener_added;

	private Object datasource;

	@Override
	public Object
	skinObjectInitialShow(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		initColumns();

		return( null );
	}

	protected void
	initColumns()
	{
		synchronized (SBC_DownloadHistoryView.class) {

			if ( columnsAdded ){

				return;
			}

			columnsAdded = true;
		}

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.registerColumn(DownloadHistory.class, ColumnDLHistoryName.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnDLHistoryName(column);
					}
				});

		tableManager.registerColumn(
				DownloadHistory.class,
				ColumnDLHistoryAddDate.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) {
						return new ColumnDateSizer(DownloadHistory.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
						};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnDLHistoryAddDate(column);
					}
				});

		tableManager.registerColumn(
				DownloadHistory.class,
				ColumnDLHistoryCompleteDate.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) {
						return new ColumnDateSizer(DownloadHistory.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
						};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnDLHistoryCompleteDate(column);
					}
				});

		tableManager.registerColumn(
				DownloadHistory.class,
				ColumnDLHistoryRemoveDate.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) {
						return new ColumnDateSizer(DownloadHistory.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
						};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnDLHistoryRemoveDate(column);
					}
				});

		tableManager.registerColumn(DownloadHistory.class, ColumnDLHistoryHash.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnDLHistoryHash(column);
					}
				});

		tableManager.registerColumn(DownloadHistory.class, ColumnDLHistorySize.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnDLHistorySize(column);
					}
				});

		tableManager.registerColumn(DownloadHistory.class, ColumnDLHistorySaveLocation.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnDLHistorySaveLocation(column);
					}
				});

		tableManager.registerColumn(DownloadHistory.class, ColumnDLHistoryTags.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnDLHistoryTags(column);
					}
				});

		tableManager.setDefaultColumnNames(TABLE_NAME,
				new String[] {
					ColumnDLHistoryName.COLUMN_ID,
					ColumnDLHistoryAddDate.COLUMN_ID,
					ColumnDLHistoryCompleteDate.COLUMN_ID,
					ColumnDLHistoryRemoveDate.COLUMN_ID,
				});

		tableManager.setDefaultSortColumnName(TABLE_NAME, ColumnDLHistoryName.COLUMN_ID);
	}

	@Override
	public Object
	skinObjectHidden(
		SWTSkinObject skinObject,
		Object params)
	{
		if ( tv != null ){

			tv.delete();

			tv = null;
		}

		Utils.disposeSWTObjects(new Object[] {
			table_parent,
		});

		if ( dh_listener_added ){

			dh_manager.removeListener( this );

			dh_listener_added = false;
		}

		return super.skinObjectHidden(skinObject, params);
	}

	@Override
	public Object
	skinObjectShown(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		super.skinObjectShown( skinObject, params );

		SWTSkinObject so_list = getSkinObject( "dl-history-list" );

		if ( so_list != null ){

			initTable((Composite)so_list.getControl());

		}else{

			System.out.println("NO dl-history-list");

			return( null );
		}

		if ( tv == null ){

			return( null );
		}

		if ( dh_manager != null ){

			dh_manager.addListener( this, true );

			dh_listener_added = true;
		}

		return( null );
	}

	@Override
	public Object
	skinObjectDestroyed(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		if ( dh_listener_added ){

			dh_manager.removeListener( this );

			dh_listener_added = false;
		}

		return super.skinObjectDestroyed(skinObject, params);
	}


	private void
	initTable(
		Composite control )
	{
		if ( tv == null ){

			tv = TableViewFactory.createTableViewSWT(
					DownloadHistory.class, TABLE_NAME, TABLE_NAME,
					new TableColumnCore[0],
					ColumnDLHistoryName.COLUMN_ID,
					SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);

			SWTSkinObjectTextbox soFilter = (SWTSkinObjectTextbox) getSkinObject("filterbox");
			
			if ( soFilter != null ){
				
				col_filter_helper = new TableColumnFilterHelper<DownloadHistory>( tv, "downloadhistoryview:search" );

				BubbleTextBox bubbleTextBox = soFilter.getBubbleTextBox();
				
				tv.enableFilterCheck( bubbleTextBox, this);
				
				String tooltip = MessageText.getString("filter.tt.start");
				tooltip += MessageText.getString("dlh.filter.tt.line1");
				tooltip += MessageText.getString("dlh.filter.tt.line2");
				tooltip += MessageText.getString("dlh.filter.tt.line3");
				tooltip += MessageText.getString("column.filter.tt.line1");
				tooltip += MessageText.getString("column.filter.tt.line2");
				
				bubbleTextBox.setTooltip( tooltip );	
				
				bubbleTextBox.setMessage( MessageText.getString( "Button.search2" ) );
			}

			tv.setRowDefaultHeightEM(1);

			table_parent = Utils.createSkinnedComposite(control, SWT.BORDER, Utils.getFilledFormData());

			GridLayout layout = new GridLayout();

			layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;

			table_parent.setLayout(layout);

			tv.addMenuFillListener(this);
			tv.addSelectionListener( this, false );

			tv.initialize( table_parent );

			tv.addCountChangeListener(
				new TableCountChangeListener()
				{
					@Override
					public void
					rowRemoved(
						TableRowCore row)
					{
					}

					@Override
					public void
					rowAdded(
						TableRowCore row)
					{
						if ( datasource == row.getDataSource()){

							tv.setSelectedRows(new TableRowCore[] { row });
						}
					}
				});

			if ( dh_manager == null ){

				control.setEnabled( false );
			}
		}

		control.layout( true );
	}

	@Override
	public boolean
	toolBarItemActivated(
		ToolBarItem item,
		long activationType,
		Object datasource)
	{
		if ( tv == null || !tv.isVisible() || dh_manager == null ){

			return( false );
		}

		List<Object> datasources = tv.getSelectedDataSources();

		if ( datasources.size() > 0 ){

			List<DownloadHistory>	dms = new ArrayList<>( datasources.size());

			for ( Object o: datasources ){

				dms.add((DownloadHistory)o);
			}

			String id = item.getID();

			if ( id.equals("remove")) {

				dh_manager.removeHistory( dms );

			}else if ( id.equals("startstop")) {

				for ( DownloadHistory download: dms ){

					download.setRedownloading();

					String magnet = UrlUtils.getMagnetURI( download.getTorrentHash(), download.getName(), null );

					TorrentOpener.openTorrent( magnet );
				}
			}

			return true;
		}

		return false;
	}

	@Override
	public void
	refreshToolBarItems(
		Map<String, Long> list)

	{
		if ( tv == null || !tv.isVisible() || dh_manager == null ){

			return;
		}

		boolean canEnable = false;
		boolean canStart = false;

		Object[] datasources = tv.getSelectedDataSources().toArray();

		if ( datasources.length > 0 ){

			canEnable = true;
			canStart = true;
		}

		list.put( "remove", canEnable ? UIToolBarItem.STATE_ENABLED : 0);
		list.put( "start", canStart ? UIToolBarItem.STATE_ENABLED : 0);
	}

	@Override
	public void
	updateUI()
	{
		if (tv != null) {

			tv.refreshTable(false);
		}
	}

	@Override
	public String
	getUpdateUIName()
	{
		return( TABLE_NAME );
	}

	@Override
	public void
	addThisColumnSubMenu(
		String 		columnName,
		Menu 		menu )
	{
		if ( dh_manager != null ){

			new MenuItem( menu, SWT.SEPARATOR );

			if ( dh_manager.isEnabled()){

					// reset

				MenuItem itemReset = new MenuItem(menu, SWT.PUSH);

				Messages.setLanguageText(itemReset, "label.reset.history" );

				itemReset.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						resetHistory();
					}
				});

					// disable

				MenuItem itemDisable = new MenuItem(menu, SWT.PUSH);

				Messages.setLanguageText( itemDisable, "label.disable.history" );

				itemDisable.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						dh_manager.setEnabled( false );
					}
				});

			}else{

					// enable

				MenuItem itemEnable = new MenuItem(menu, SWT.PUSH);

				Messages.setLanguageText( itemEnable, "label.enable.history" );

				itemEnable.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						dh_manager.setEnabled( true );
					}
				});
			}

			new MenuItem( menu, SWT.SEPARATOR );
		}
	}

	@Override
	public void
	fillMenu(
		String 	sColumnName,
		Menu 	menu )
	{
		if ( dh_manager != null ){

			if ( dh_manager.isEnabled()){

				List<Object>	ds = tv.getSelectedDataSources();

				final List<DownloadHistory>	dms = new ArrayList<>( ds.size());

				for ( Object o: ds ){

					dms.add((DownloadHistory)o);
				}

				boolean	hasSelection = dms.size() > 0;

					// Explore (or open containing folder)

				final boolean use_open_containing_folder = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");

				MenuItem itemExplore = new MenuItem(menu, SWT.PUSH);

				Messages.setLanguageText(itemExplore, "MyTorrentsView.menu."
						+ (use_open_containing_folder ? "open_parent_folder" : "explore"));

				itemExplore.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						for ( DownloadHistory download: dms ){

							ManagerUtils.open( new File( download.getSaveLocation()), use_open_containing_folder);
						}
					}
				});

				itemExplore.setEnabled(hasSelection);

					// redownload

				MenuItem itemRedownload = new MenuItem(menu, SWT.PUSH);

				Messages.setLanguageText(itemRedownload, "label.redownload" );

				itemRedownload.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						for ( DownloadHistory download: dms ){

							download.setRedownloading();

							String magnet = UrlUtils.getMagnetURI( download.getTorrentHash(), download.getTorrentV2Hash(), download.getName(), null );

							TorrentOpener.openTorrent( magnet );
						}
					}
				});

				itemExplore.setEnabled(hasSelection);
					// remove

				MenuItem itemRemove = new MenuItem(menu, SWT.PUSH);
				Utils.setMenuItemImage(itemRemove, "delete");

				Messages.setLanguageText( itemRemove, "MySharesView.menu.remove" );

				itemRemove.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						dh_manager.removeHistory( dms );
					}
				});

				itemRemove.setEnabled(hasSelection);

				new MenuItem( menu, SWT.SEPARATOR );

					// reset

				MenuItem itemReset = new MenuItem(menu, SWT.PUSH);

				Messages.setLanguageText(itemReset, "label.reset.history" );

				itemReset.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						resetHistory();
					}
				});

					// disable

				MenuItem itemDisable = new MenuItem(menu, SWT.PUSH);

				Messages.setLanguageText( itemDisable, "label.disable.history" );

				itemDisable.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						dh_manager.setEnabled( false );
					}
				});

				new MenuItem( menu, SWT.SEPARATOR );
			}
		}
	}

	private void
	resetHistory()
	{
		MessageBoxShell mb = new MessageBoxShell(
				MessageText.getString("downloadhistoryview.reset.title"),
				MessageText.getString("downloadhistoryview.reset.text"));

		mb.setButtons(0, new String[] {
			MessageText.getString("Button.yes"),
			MessageText.getString("Button.no"),
		}, new Integer[] { 0, 1 });

		mb.open(new UserPrompterResultListener(){
			@Override
			public void prompterClosed(int result) {
				if (result == 0) {
					dh_manager.resetHistory();
				}
			}
		});
	}

	@Override
	public void
	selected(
		TableRowCore[] row )
	{
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

		if ( uiFunctions != null ){

			uiFunctions.refreshIconBar();
		}
	}

	@Override
	public void
	deselected(
		TableRowCore[] rows )
	{
	  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

	  	if ( uiFunctions != null ){

	  		uiFunctions.refreshIconBar();
	  	}
	}

	@Override
	public void
	focusChanged(
		TableRowCore focus )
	{
	  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

	  	if ( uiFunctions != null ){

	  		uiFunctions.refreshIconBar();
	  	}
	}

	@Override
	public void
	defaultSelected(
		TableRowCore[] 	rows,
		int 			stateMask )
	{
		if ( rows.length == 1 ){
			
			DownloadHistory dh = (DownloadHistory)rows[0].getDataSource();
			
			byte[] hash = dh.getTorrentHash();
			
			DownloadManager dm = CoreFactory.getSingleton().getGlobalManager().getDownloadManager( new HashWrapper( hash ));
			
			if ( dm != null ){
				
				UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT().showEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY );
				
				dm.fireGlobalManagerEvent( GlobalManagerEvent.ET_REQUEST_ATTENTION );
			}
		}
	}

	@Override
	public void
	downloadHistoryEventOccurred(
		DownloadHistoryEvent		event )
	{
		int type = event.getEventType();

		List<DownloadHistory> dls = event.getHistory();

		if ( type == DownloadHistoryEvent.DHE_HISTORY_ADDED ){

			tv.addDataSources( dls.toArray( new DownloadHistory[dls.size()] ));

		}else if ( type == DownloadHistoryEvent.DHE_HISTORY_REMOVED ){

			tv.removeDataSources( dls.toArray( new DownloadHistory[dls.size()] ));

		}else{

			for ( DownloadHistory d: dls ){

				TableRowCore row = tv.getRow( d );

				if ( row != null ){

					row.invalidate( true );
				}
			}
		}
	}

	@Override
	public void
	mouseEnter(
		TableRowCore row )
	{
	}

	@Override
	public void
	mouseExit(
		TableRowCore row)
	{
	}

	@Override
	public void
	filterSet(
		String filter)
	{
		col_filter_helper.filterSet( filter );
	}

	@Override
	public boolean
	filterCheck(
		DownloadHistory 	ds,
		String 				filter,
		boolean 			regex,
		boolean				confusable )
	{
		if ( confusable ){
		
			filter = GeneralUtils.getConfusableEquivalent(filter,true);
		}
		
		Object o_name;

		if ( filter.startsWith( "h:" )){

			filter = filter.substring( 2 );

			List<String> names = new ArrayList<>();

			byte[] hash = ds.getTorrentHash();

			if ( hash != null ){
				
				names.add( ByteFormatter.encodeString( hash ));
	
				names.add( Base32.encode( hash ));
			}
			
			byte[] v2_hash = ds.getTorrentV2Hash();

			if ( v2_hash != null ){
				
				names.add( ByteFormatter.encodeString( v2_hash ));
	
				names.add( Base32.encode( v2_hash ));
			}
			
			o_name = names;

		}else if ( filter.startsWith( "t:" ) || filter.startsWith( "tag:" )){

			filter = filter.substring( filter.startsWith( "t:" )?2:4 );

			String[] tags = ds.getTags();
				
			List<String> names = new ArrayList<>( Arrays.asList( tags ));
				
			o_name = names;
			
		}else if ( filter.startsWith( "f:" ) || filter.startsWith( "p:" )){

			filter = filter.substring( 2 );

			o_name = ds.getSaveLocation();

		}else{

			String default_text = ds.getName();

			if ( confusable ){
				
				default_text = GeneralUtils.getConfusableEquivalent( default_text, false );
			}
			
			boolean res = col_filter_helper.filterCheck( ds, filter, regex, default_text, false );
			
			return( res );
		}

			// could replace below with col_filter_helper and some hacks sometime...
		
		boolean	match_result = true;

		String expr;
		
		if ( regex ){
			
			expr = filter;
			
			if ( expr.startsWith( "!" )){
				
				expr = expr.substring(1);

				match_result = false;
			}
		}else{
			
			expr = RegExUtil.convertAndOrToExpr( filter );
		}

		Pattern pattern = RegExUtil.getCachedPattern( "downloadhistoryview:search", expr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

		boolean bOurs;

		if ( o_name instanceof String ){

			String name = (String)o_name;
			
			if ( confusable ){
			
				name = GeneralUtils.getConfusableEquivalent( name, false );
			}
			
			bOurs = pattern.matcher( name ).find() == match_result;

		}else{

			List<String>	names = (List<String>)o_name;

				// match_result: true -> at least one match; false -> any fail

			bOurs = !match_result;

			for ( String name: names ){

				if ( confusable ){
				
					name = GeneralUtils.getConfusableEquivalent( name, false );
				}
				
				if ( pattern.matcher( name ).find()){

					bOurs = match_result;

					break;
				}
			}
		}

		return( bOurs );
	}

	@Override
	public Object
	dataSourceChanged(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		if ( params instanceof DownloadHistory ){

			if (tv != null) {

				TableRowCore row = tv.getRow((DownloadHistory) params);

				if ( row != null ){

					tv.setSelectedRows(new TableRowCore[] { row });
				}
			}
		}

		datasource = params;

		return( null );
	}
}
