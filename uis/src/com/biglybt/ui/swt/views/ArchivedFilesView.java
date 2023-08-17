/*
 * Created on 2 juil. 2003
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.ui.common.table.TableView;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pif.download.DownloadStub;
import com.biglybt.pif.download.DownloadStub.DownloadStubFile;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableDataSourceChangedListener;
import com.biglybt.ui.common.table.TableLifeCycleListener;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.archivedfiles.NameItem;
import com.biglybt.ui.swt.views.tableitems.archivedfiles.SizeItem;
import com.biglybt.ui.swt.views.utils.ManagerUtils;




public class ArchivedFilesView
	extends TableViewTab<DownloadStubFile>
	implements TableLifeCycleListener, TableDataSourceChangedListener,
				TableViewSWTMenuFillListener
{
	private static final String TABLE_ID = "ArchivedFiles";

	private final static TableColumnCore[] basicItems = {
		new NameItem(TABLE_ID),
		new SizeItem(TABLE_ID),
	};

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TABLE_ID, basicItems );
	}

	public static final String MSGID_PREFIX = "ArchivedFilesView";

	private TableViewSWT<DownloadStubFile> tv;

	private DownloadStub	current_download;

	public
	ArchivedFilesView()
	{
		super(MSGID_PREFIX);
	}



	@Override
	public TableViewSWT<DownloadStubFile>
	initYourTableView()
	{
		tv = TableViewFactory.createTableViewSWT(
				DownloadStubFile.class,
				TABLE_ID,
				getTextPrefixID(),
				basicItems,
				basicItems[0].getName(),
				SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL );

		tv.addLifeCycleListener(this);
		tv.addMenuFillListener(this);
		tv.addTableDataSourceChangedListener(this, true);

		return( tv );
	}

	@Override
	public void
	fillMenu(
		String sColumnName, Menu menu)
	{
		List<Object>	ds = tv.getSelectedDataSources();

		final List<DownloadStubFile> files = new ArrayList<>();

		for ( Object o: ds ){

			files.add((DownloadStubFile)o);
		}

		boolean	hasSelection = files.size() > 0;

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
				for ( DownloadStubFile file: files ){

					ManagerUtils.open( new File( file.getFile().getAbsolutePath()), use_open_containing_folder);
				}
			}
		});

		itemExplore.setEnabled(hasSelection);

		new MenuItem( menu, SWT.SEPARATOR );
	}

	@Override
	public void
	addThisColumnSubMenu(
		String columnName,
		Menu menuThisColumn)
	{
	    if ( columnName.equals("name")){

	    	new MenuItem( menuThisColumn, SWT.SEPARATOR );

	    	final MenuItem path_item = new MenuItem( menuThisColumn, SWT.CHECK );

		    boolean show_full_path = COConfigurationManager.getBooleanParameter( "ArchivedFilesView.show.full.path" );
	    	path_item.setSelection( show_full_path );

	    	Messages.setLanguageText(path_item, "FilesView.menu.showfullpath");

	    	path_item.addListener(SWT.Selection, new Listener() {
	    		@Override
			    public void handleEvent(Event e) {
	    			boolean show_full_path = path_item.getSelection();
	    			tv.columnInvalidate("name");
	    			tv.refreshTable(false);
	    			COConfigurationManager.setParameter( "ArchivedFilesView.show.full.path", show_full_path );
	    		}
	    	});
	    }
	}

	@Override
	public void
	tableDataSourceChanged(
		Object ds )
	{
		if ( ds == current_download ){

			tv.setEnabled( ds != null );

			return;
		}

		boolean	enabled = true;

		if ( ds instanceof DownloadStub ){

			current_download = (DownloadStub)ds;

		}else if ( ds instanceof Object[]) {

			Object[] objs = (Object[])ds;

			if ( objs.length != 1 ){

				enabled = false;

			}else{

				DownloadStub stub = (DownloadStub)objs[0];

				if ( stub == current_download ){

					return;
				}

				current_download = stub;
			}
		}else{

			current_download = null;

			enabled = false;
		}

	  	if ( !tv.isDisposed()){

			tv.removeAllTableRows();

			tv.setEnabled( enabled );

			if ( enabled ){

				if ( current_download != null ){

					addExistingDatasources();
				}
			}
	    }
	}

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				if ( current_download != null ){

					addExistingDatasources();

				}else{

					this.tv.setEnabled( false );
				}
				break;
		}
	}

	private void
	addExistingDatasources()
	{
		if ( current_download == null || tv.isDisposed()){

			return;
		}

		DownloadStubFile[] files = current_download.getStubFiles();

		tv.addDataSources( files );

		tv.processDataSourceQueueSync();
	}
}
