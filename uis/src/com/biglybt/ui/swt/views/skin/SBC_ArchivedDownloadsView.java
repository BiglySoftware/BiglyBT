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
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.columns.archivedls.*;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectTextbox;
import com.biglybt.ui.swt.views.ArchivedFilesView;
import com.biglybt.ui.swt.views.ViewManagerSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.ui.swt.views.utils.ManagerUtils.ArchiveCallback;

import com.biglybt.pif.download.*;
import com.biglybt.pif.download.DownloadStub.DownloadStubFile;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;


public class SBC_ArchivedDownloadsView
	extends SkinView
	implements 	UIUpdatable, UIPluginViewToolBarListener, TableViewFilterCheck<DownloadStub>,
	TableViewSWTMenuFillListener, TableSelectionListener, DownloadStubListener
{

	private static final String TABLE_NAME = "ArchivedDownloads";
	public static final Class<DownloadStub> PLUGIN_DS_TYPE = DownloadStub.class;

	TableViewSWT<DownloadStub> tv;

	private Composite table_parent;

	private boolean columnsAdded = false;

	private boolean dm_listener_added;

	private Object datasource;

	@Override
	public Object
	skinObjectInitialShow(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		initColumns();

		new InfoBarUtil(
			skinObject,
			"archivedlsview.infobar",
			false,
			"archivedls.infobar",
			"archivedls.view.infobar" )
			{
				@Override
				public boolean
				allowShow()
				{
					return( true );
				}
			};

		return( null );
	}

	protected void
	initColumns()
	{
		synchronized (SBC_ArchivedDownloadsView.class) {

			if ( columnsAdded ){

				return;
			}

			columnsAdded = true;
		}

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.registerColumn(DownloadStub.class, ColumnArchiveDLName.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLName(column);
					}
				});

		tableManager.registerColumn(DownloadStub.class, ColumnArchiveDLSize.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLSize(column);
					}
				});

		tableManager.registerColumn(DownloadStub.class, ColumnArchiveDLFileCount.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLFileCount(column);
					}
				});

		tableManager.registerColumn(DownloadStub.class,
				ColumnArchiveDLDate.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) {
						return new ColumnDateSizer(DownloadStub.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
						};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLDate(column);
					}
				});

		tableManager.registerColumn(DownloadStub.class, ColumnArchiveDLTags.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLTags(column);
					}
				});

		tableManager.registerColumn(DownloadStub.class, ColumnArchiveDLCategory.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLCategory(column);
					}
				});

		tableManager.registerColumn(DownloadStub.class, ColumnArchiveShareRatio.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveShareRatio(column);
					}
				});
		
		tableManager.registerColumn(DownloadStub.class, ColumnArchiveDLSaveLocation.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLSaveLocation(column);
					}
				});

		tableManager.registerColumn(DownloadStub.class, ColumnArchiveDLMainTracker.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLMainTracker(column);
					}
				});

		tableManager.registerColumn(DownloadStub.class,
				ColumnArchiveDLCompDate.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) {
						return new ColumnDateSizer(DownloadStub.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
						};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnArchiveDLCompDate(column);
					}
				});
		
		tableManager.setDefaultColumnNames(TABLE_NAME,
				new String[] {
					ColumnArchiveDLName.COLUMN_ID,
					ColumnArchiveDLSize.COLUMN_ID,
					ColumnArchiveDLFileCount.COLUMN_ID,
					ColumnArchiveDLDate.COLUMN_ID,
					ColumnArchiveShareRatio.COLUMN_ID,
				});

		tableManager.setDefaultSortColumnName(TABLE_NAME, ColumnArchiveDLName.COLUMN_ID);
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

		if ( dm_listener_added ){

			PluginInitializer.getDefaultInterface().getDownloadManager().removeDownloadStubListener( this );

			dm_listener_added = false;
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

		SWTSkinObject so_list = getSkinObject( "archived-dls-list" );

		if ( so_list != null ){

			initTable((Composite)so_list.getControl());

		}else{

			System.out.println("NO archived-dls-list");

			return( null );
		}

		if ( tv == null ){

			return( null );
		}

		PluginInitializer.getDefaultInterface().getDownloadManager().addDownloadStubListener( this, true );

		dm_listener_added = true;

		return( null );
	}

	@Override
	public Object
	skinObjectDestroyed(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		if ( dm_listener_added ){

			PluginInitializer.getDefaultInterface().getDownloadManager().removeDownloadStubListener( this );

			dm_listener_added = false;
		}

		return super.skinObjectDestroyed(skinObject, params);
	}


	private void
	initTable(
		Composite control )
	{
		registerPluginViews();

		if ( tv == null ){

			tv = TableViewFactory.createTableViewSWT(PLUGIN_DS_TYPE, TABLE_NAME,
					TABLE_NAME, new TableColumnCore[0], ColumnArchiveDLName.COLUMN_ID,
					SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);

			SWTSkinObjectTextbox soFilter = (SWTSkinObjectTextbox) getSkinObject(
				"filterbox");
			if (soFilter != null) {
				tv.enableFilterCheck(soFilter.getBubbleTextBox(), this);
			}

			tv.setRowDefaultHeightEM(1);

			table_parent = new Composite(control, SWT.BORDER);

			table_parent.setLayoutData(Utils.getFilledFormData());

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
		}

		control.layout( true );
	}

	private static void registerPluginViews() {
		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		if (vm.areCoreViewsRegistered(PLUGIN_DS_TYPE)) {
			return;
		}

		vm.registerView(PLUGIN_DS_TYPE, new UISWTViewBuilderCore(
				ArchivedFilesView.MSGID_PREFIX, null, ArchivedFilesView.class));

		vm.setCoreViewsRegistered(PLUGIN_DS_TYPE);
	}

	@Override
	public boolean
	toolBarItemActivated(
		ToolBarItem item,
		long activationType,
		Object datasource)
	{
		if ( tv == null || !tv.isVisible()){

			return( false );
		}

		List<Object> datasources = tv.getSelectedDataSources();

		if ( datasources.size() > 0 ){

			List<DownloadStub>	dms = new ArrayList<>(datasources.size());

			for ( Object o: datasources ){

				dms.add((DownloadStub)o);
			}

			String id = item.getID();

			if ( id.equals("remove")) {

				TorrentUtil.removeDataSources(datasources.toArray());

			}else if ( id.equals( "startstop" ) || id.equals( "start" )){

				ManagerUtils.restoreFromArchive( dms, true, null );
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
		if ( tv == null || !tv.isVisible()){

			return;
		}

		boolean canEnable = false;

		Object[] datasources = tv.getSelectedDataSources().toArray();

		if ( datasources.length > 0 ){

			canEnable = true;
		}

		list.put( "start", canEnable ? UIToolBarItem.STATE_ENABLED : 0);
		list.put( "startstop", canEnable ? UIToolBarItem.STATE_ENABLED : 0);

		list.put( "remove", canEnable ? UIToolBarItem.STATE_ENABLED : 0);
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
		Menu 		menuThisColumn )
	{
	}

	@Override
	public void
	fillMenu(
		String 	sColumnName,
		Menu 	menu )
	{
		List<Object>	ds = tv.getSelectedDataSources();

		final List<DownloadStub>	dms = new ArrayList<>(ds.size());

		for ( Object o: ds ){

			dms.add((DownloadStub)o);
		}

		boolean	hasSelection = dms.size() > 0;

			// Explore (or open containing folder)

		final boolean use_open_containing_folder = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");

		final MenuItem itemExplore = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemExplore, "MyTorrentsView.menu."
				+ (use_open_containing_folder ? "open_parent_folder" : "explore"));

		itemExplore.addListener(SWT.Selection, new Listener() {
			@Override
			public void
			handleEvent(
				Event event)
			{
				for ( DownloadStub download: dms ){

					ManagerUtils.open( new File( download.getSavePath()), use_open_containing_folder);
				}
			}
		});

		itemExplore.setEnabled(hasSelection);

		new MenuItem( menu, SWT.SEPARATOR );

		final MenuItem itemRestore = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemRestore, "MyTorrentsView.menu.restore");

		itemRestore.addListener(
			SWT.Selection,
			new Listener()
			{
				@Override
				public void
				handleEvent(
					Event event)
				{
					ManagerUtils.restoreFromArchive( dms,false, null );
				}
			});

		itemRestore.setEnabled( hasSelection );

		final MenuItem itemRestoreAnd = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemRestoreAnd, "MyTorrentsView.menu.restore.and");

		itemRestoreAnd.addListener(
			SWT.Selection,
			new Listener()
			{
				@Override
				public void
				handleEvent(
					Event event)
				{
					ManagerUtils.restoreFromArchive(
						dms,
						false,
						new ArchiveCallback()
						{
							private List<DownloadManager>	targets = new ArrayList<>();

							@Override
							public void
							success(
								final DownloadStub source,
								final DownloadStub target)
							{
								DownloadManager dm = PluginCoreUtils.unwrap((Download)target);

								if ( dm != null ){

									targets.add( dm );
								}
							}

							@Override
							public void
							completed()
							{
								Utils.execSWTThread(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											if ( targets.size() == 0 ){

												return;
											}

											final Menu menu = new Menu( table_parent );

											DownloadManager[] dm_list = targets.toArray( new DownloadManager[ dms.size()]);

											TorrentUtil.fillTorrentMenu(
												menu, dm_list, CoreFactory.getSingleton(), true, 0, tv);

											menu.addMenuListener(
												new MenuListener() {

													@Override
													public void
													menuShown(
														MenuEvent e)
													{
													}

													@Override
													public void
													menuHidden(
														MenuEvent e)
													{
														Utils.execSWTThreadLater(
															1,
															new Runnable()
															{
																@Override
																public void
																run()
																{
																	menu.dispose();
																}
															});
													}
												});

											menu.setVisible( true );
										}
									});
							}
						});
				}
			});

		itemRestoreAnd.setEnabled( hasSelection );

		new MenuItem( menu, SWT.SEPARATOR );
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

			Object obj = rows[0].getDataSource();

		}
	}

	@Override
	public void
	downloadStubEventOccurred(
		DownloadStubEvent		event )

		throws DownloadException
	{
		int type = event.getEventType();

		List<DownloadStub> dls = event.getDownloadStubs();

		if ( type == DownloadStubEvent.DSE_STUB_ADDED ){

			tv.addDataSources( dls.toArray( new DownloadStub[dls.size()] ));

		}else if ( type == DownloadStubEvent.DSE_STUB_REMOVED ){

			tv.removeDataSources( dls.toArray( new DownloadStub[dls.size()] ));
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
	}

	@Override
	public boolean
	filterCheck(
		DownloadStub 	ds,
		String 			filter,
		boolean 		regex,
		boolean			confusable )
	{
		if ( confusable ){
		
			filter = GeneralUtils.getConfusableEquivalent(filter);
		}
		
		boolean do_files = filter.toLowerCase( Locale.US ).startsWith( "f:" );
		
		if ( do_files ){

			filter = filter.substring(2).trim();
		}
		
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

		if ( do_files ){

			DownloadStubFile[] files = ds.getStubFiles();

			Pattern pattern = RegExUtil.getCachedPattern( "archiveview:search", expr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

			boolean result = !match_result;

			boolean	try_save_path = true;

			for ( DownloadStubFile file: files ){

				String name = file.getFile().getName();

				if ( confusable ){
				
					name = GeneralUtils.getConfusableEquivalent( name );
				}
				
				if ( pattern.matcher( name ).find()){

					result = match_result;

					try_save_path = false;
					
					break;
				}
			}

			if ( try_save_path ){
				
				if ( pattern.matcher( ds.getSavePath()).find()){

					result = match_result;
				}
			}
			
			return( result );

		}else{

			String name = ds.getName();

			if ( confusable ){
			
				name = GeneralUtils.getConfusableEquivalent(name);
			}
			
			Pattern pattern = RegExUtil.getCachedPattern( "archiveview:search", expr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

			return( pattern.matcher(name).find() == match_result );
		}
	}

	@Override
	public Object
	dataSourceChanged(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		if ( params instanceof DownloadStub ){

			if (tv != null) {

				TableRowCore row = tv.getRow((DownloadStub) params);

				if ( row != null ){

					tv.setSelectedRows(new TableRowCore[] { row });
				}
			}
		}

		datasource = params;

		return( null );
	}
}
