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

package com.biglybt.ui.common.table.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.pif.ui.tables.TableRowRefreshListener;

/**
 * @author TuxPaper
 * @created Feb 6, 2007
 */
public abstract class TableViewImpl<DATASOURCETYPE>
	implements TableView<DATASOURCETYPE>, TableStructureModificationListener<DATASOURCETYPE>
{
	private final static LogIDs LOGID = LogIDs.GUI;

	private static final boolean DEBUG_SORTER = false;

	/** Helpful output when trying to debug add/removal of rows */
	public final static boolean DEBUGADDREMOVE = System.getProperty(
			"debug.swt.table.addremove", "0").equals("1");

	public static final boolean DEBUG_SELECTION = false;

	private static final String CFG_SORTDIRECTION = "config.style.table.defaultSortOrder";

	// Shorter name for ConfigManager, easier to read code
	protected static final ConfigurationManager configMan = ConfigurationManager.getInstance();

	private class
	rowSorterRunnable
		extends AERunnable
	{
		private AtomicBoolean	forceRefreshPending = new AtomicBoolean();

		@Override
		public void 
		runSupport()
		{
			sortRows( forceRefreshPending.getAndSet( false ), false );
		}
		
		public void 
		setForceRefresh(
			boolean fs)
		{
			if ( fs ){
				
				forceRefreshPending.set( true);
			}
		}
	}

	private rowSorterRunnable rowSorterRunnable = new rowSorterRunnable();
	
	private FrequencyLimitedDispatcher rowSorter = 
		new FrequencyLimitedDispatcher(rowSorterRunnable, 250 );
		
			
	/** TableID (from {@link com.biglybt.pif.ui.tables.TableManager})
	 * of the table this class is
	 * handling.  Config settings are stored with the prefix of
	 * "Table.<i>TableID</i>"
	 */
	protected String tableID;

	/** Prefix for retrieving text from the properties file (MessageText)
	 * Typically <i>TableID</i> + "View"
	 */
	protected String propertiesPrefix;

	// What type of data is stored in this table
	private final Class<?> classPluginDataSourceType;


	private boolean bReallyAddingDataSources = false;

	// PARG: replaced sortColumn_mon with rows_sync as deadlock from a pair of
	// sortColumn_mon -> rows_sync
	// rows_sync -> sortColumn_mon
	// thread paths

	// private AEMonitor sortColumn_mon = new AEMonitor("TableView:sC");

	/** Sorting functions */
	private final List<TableColumnCore> sortColumns = new ArrayList<>();

	/** TimeStamp of when last sorted all the rows was */
	private long lLastSortedOn;

	private AEMonitor listeners_mon = new AEMonitor("tablelisteners");

	private ArrayList<TableRowRefreshListener> listenersRowRefesh;

	// List of DataSourceChangedListener
	private CopyOnWriteList<TableDataSourceChangedListener> listenersDataSourceChanged = new CopyOnWriteList<>();

	private CopyOnWriteList<TableSelectionListener> listenersSelection = new CopyOnWriteList<>();

	private CopyOnWriteList<TableLifeCycleListener> listenersLifeCycle = new CopyOnWriteList<>();

	private CopyOnWriteList<TableRefreshListener> listenersRefresh = new CopyOnWriteList<>();

	private CopyOnWriteList<TableCountChangeListener> listenersCountChange = new CopyOnWriteList<>(1);

	private CopyOnWriteList<TableExpansionChangeListener> listenersExpansionChange = new CopyOnWriteList<>(1);

	private Object parentDataSource;

	private final Object rows_sync;

	/** Filtered rows in the table */
	private List<TableRowCore> sortedRows;

	/** Link DataSource to their row in the table.
	 * key = DataSource
	 * value = TableRowSWT
	 */
	private IdentityHashMap<DATASOURCETYPE, TableRowCore> mapDataSourceToRow;

	private IdentityHashMap<DATASOURCETYPE, String> listUnfilteredDataSources;

	// **** NOTE THE USE OF IdentityHashMap - we have to do this to behave reliably in the face of
	// some DATASOURCETYPEs (DownloadManagerImpl to mention no names) redefining equals/hashCode
	// if you quickly remove+add a download with the same hash this can cause borkage here unless
	// we use identity maps

	/** Queue added datasources and add them on refresh */
	private IdentityHashMap<DATASOURCETYPE, String> dataSourcesToAdd = new IdentityHashMap<>(4);

	/** Queue removed datasources and add them on refresh */
	private IdentityHashMap<DATASOURCETYPE, String> dataSourcesToRemove = new IdentityHashMap<>(4);

	private AtomicInteger	datsaSourceQueueProcessingCount = new AtomicInteger();
	
	// class used to keep filter stuff in a nice readable parcel
	public static class filter<DATASOURCETYPE>
	{

		public TimerEvent eventUpdate;

		public String text = "";

		public long lastFilterTime;

		public boolean regex = false;

		public TableViewFilterCheck<DATASOURCETYPE> checker;

		public String nextText = "";
	}

	protected filter<DATASOURCETYPE> filter;

	private DataSourceCallBackUtil.addDataSourceCallback processDataSourceQueueCallback = new DataSourceCallBackUtil.addDataSourceCallback() {
		@Override
		public void process() {
			processDataSourceQueue();
		}

		@Override
		public void debug(String str) {
			TableViewImpl.this.debug(str);
		}
	};


	/** Basic (pre-defined) Column Definitions */
	private TableColumnCore[] basicItems;

	/** All Column Definitions.  The array is not necessarily in column order */
	private TableColumnCore[] tableColumns;

	/** We need to remember the order of the columns at the time we added them
	 * in case the user drags the columns around.
	 */
	private TableColumnCore[] columnsOrdered;

	/**
	 * Up to date list of selected rows, so we can access rows without being on SWT Thread.
	 * Guaranteed to have no nulls
	 */
	private List<TableRowCore> selectedRows = new ArrayList<>(1);

	private List<Object> listSelectedCoreDataSources;

	private boolean headerVisible = true;

	private boolean menuEnabled = true;

	private boolean provideIndexesOnRemove = false;

	
	private LinkedList<HistoryEntry>	historyBefore 	= new LinkedList<>();
	private LinkedList<HistoryEntry>	historyAfter	= new LinkedList<>();	

	public TableViewImpl(Class<?> pluginDataSourceType, String _sTableID,
			String _sPropertiesPrefix, Object rows_sync,
			TableColumnCore[] _basicItems) {
		classPluginDataSourceType = pluginDataSourceType;
		propertiesPrefix = _sPropertiesPrefix;
		tableID = _sTableID;
		basicItems = _basicItems;
		mapDataSourceToRow = new IdentityHashMap<>();
		sortedRows = new ArrayList<>();
		listUnfilteredDataSources = new IdentityHashMap<>();
		this.rows_sync = rows_sync;
		initializeColumnDefs();
	}

	private void initializeColumnDefs() {
		// XXX Adding Columns only has to be done once per TableID.
		// Doing it more than once won't harm anything, but it's a waste.
		TableColumnManager tcManager = TableColumnManager.getInstance();

		if (basicItems != null) {
			if (tcManager.getTableColumnCount(tableID) != basicItems.length) {
				tcManager.addColumns(basicItems);
			}
			basicItems = null;
		}

		tableColumns = tcManager.getAllTableColumnCoreAsArray(getDataSourceType(),
				tableID);

		// fixup order
		tcManager.ensureIntegrity(classPluginDataSourceType, tableID);
	}


	@Override
	public void addSelectionListener(TableSelectionListener listener,
	                                 boolean bFireSelection) {
		listenersSelection.add(listener);
		if (bFireSelection) {
			TableRowCore[] rows = getSelectedRows();
			listener.selected(rows);
			listener.selectionChanged(new TableRowCore[0], rows);
			listener.focusChanged(getFocusedRow());
		}
	}

	// @see TableView#addTableDataSourceChangedListener(TableDataSourceChangedListener, boolean)
	@Override
	public void addTableDataSourceChangedListener(
			TableDataSourceChangedListener l, boolean trigger) {
		listenersDataSourceChanged.add(l);
		if (trigger) {
			l.tableDataSourceChanged(parentDataSource);
		}
	}

	// @see TableView#removeTableDataSourceChangedListener(TableDataSourceChangedListener)
	@Override
	public void removeTableDataSourceChangedListener(
			TableDataSourceChangedListener l) {
		listenersDataSourceChanged.remove(l);
	}

	// @see TableView#setParentDataSource(java.lang.Object)
	@Override
	public void setParentDataSource(Object newDataSource) {
		//System.out.println(getTableID()  + "] setParentDataSource " + newDataSource);
		parentDataSource = newDataSource;
		Object[] listeners = listenersDataSourceChanged.toArray();
		for (int i = 0; i < listeners.length; i++) {
			TableDataSourceChangedListener l = (TableDataSourceChangedListener) listeners[i];
			l.tableDataSourceChanged(newDataSource);
		}
	}

	@Override
	public Object getParentDataSource() {
		return parentDataSource;
	}

	/**
	 * @param selectedRows
	 */
	public void 
	triggerDefaultSelectedListeners(
		TableRowCore[] selectedRows,
		int keyMask, int origin ) 
	{
		for ( TableSelectionListener l: listenersSelection) {
			try{
				l.defaultSelected(selectedRows, keyMask, origin );
			}catch( Throwable e ){
				Debug.out( e );
			}
		}
	}

	/**
	 * @param eventType
	 */
	protected void triggerLifeCycleListener(int eventType) {
		for ( TableLifeCycleListener l: listenersLifeCycle ){
			try {
				l.tableLifeCycleEventOccurred(this, eventType, null);
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}
	
	public void triggerSelectionChangeListeners(TableRowCore[] selected_rows, TableRowCore[] deselected_rows) {
		
		for ( TableSelectionListener l: listenersSelection) {
			try{
				l.selectionChanged(selected_rows, deselected_rows);
			}catch( Throwable e ){
				Debug.out( e );
			}
		}
	}

	protected void triggerSelectionListeners(TableRowCore[] rows) {
		if (rows == null || rows.length == 0) {
			return;
		}
		for ( TableSelectionListener l: listenersSelection) {
			try{
				l.selected(rows);
			}catch( Throwable e ){
				Debug.out( e );
			}
		}
	}

	protected void triggerDeselectionListeners(TableRowCore[] rows) {
		if (rows == null) {
			return;
		}
		for ( TableSelectionListener l: listenersSelection) {
			try {
				l.deselected(rows);
			} catch (Throwable e) {
				Debug.out(e);
			}
		}
	}

	protected void triggerMouseEnterExitRow(TableRowCore row, boolean enter) {
		if (row == null) {
			return;
		}
		for ( TableSelectionListener l: listenersSelection) {
			if (enter) {
				l.mouseEnter(row);
			} else {
				l.mouseExit(row);
			}
		}
	}

	protected void triggerFocusChangedListeners(TableRowCore row) {
		for ( TableSelectionListener l: listenersSelection) {
			l.focusChanged(row);
		}
	}

	/**
	 *
	 */
	protected void triggerTableRefreshListeners() {
		for (TableRefreshListener l: listenersRefresh ){
			l.tableRefresh();
		}
	}

	// @see TableView#addLifeCycleListener(TableLifeCycleListener)
	@Override
	public void addLifeCycleListener(TableLifeCycleListener l) {
		listenersLifeCycle.add(l);
		if (!isDisposed()) {
			l.tableLifeCycleEventOccurred(this, TableLifeCycleListener.EVENT_TABLELIFECYCLE_INITIALIZED, null);
		}
	}

	// @see TableView#addRefreshListener(TableRefreshListener, boolean)
	@Override
	public void addRefreshListener(TableRefreshListener l, boolean trigger) {
		listenersRefresh.add(l);
		if (trigger) {
			l.tableRefresh();
		}
	}

	// @see TableView#addCountChangeListener(TableCountChangeListener)
	@Override
	public void addCountChangeListener(TableCountChangeListener listener) {
		listenersCountChange.add(listener);
	}

	@Override
	public void removeCountChangeListener(TableCountChangeListener listener) {
		listenersCountChange.remove(listener);
	}

	public void triggerListenerRowAdded(final TableRowCore[] rows) {
		if (listenersCountChange.size() == 0) {
			return;
		}
		getOffUIThread(new AERunnable() {
			@Override
			public void runSupport() {
				for (Iterator iter = listenersCountChange.iterator(); iter.hasNext();) {
					TableCountChangeListener l = (TableCountChangeListener) iter.next();
					for (TableRowCore row : rows) {
						l.rowAdded(row);
					}
				}
			}
		});
	}

	protected void triggerListenerRowRemoved(TableRowCore row) {
		for (Iterator iter = listenersCountChange.iterator(); iter.hasNext();) {
			TableCountChangeListener l = (TableCountChangeListener) iter.next();
			l.rowRemoved(row);
		}
	}

		// expansion

	@Override
	public void addExpansionChangeListener(TableExpansionChangeListener listener) {
		listenersExpansionChange.add(listener);
	}

	@Override
	public void removeExpansionChangeListener(TableExpansionChangeListener listener) {
		listenersExpansionChange.remove(listener);
	}

	public void invokeExpansionChangeListeners(final TableRowCore row, final boolean expanded ) {
		if (listenersExpansionChange.size() == 0) {
			return;
		}
		getOffUIThread(new AERunnable() {
			@Override
			public void runSupport() {
				for (Iterator<TableExpansionChangeListener> iter = listenersExpansionChange.iterator(); iter.hasNext();) {
					try{
						if ( expanded ){

							iter.next().rowExpanded(row);

						}else{

							iter.next().rowCollapsed(row);
						}
					}catch( Throwable e){

						Debug.out( e );
					}
				}
			}
		});
	}

		// refresh

	public void addRefreshListener(TableRowRefreshListener listener) {
		try {
			listeners_mon.enter();

			if (listenersRowRefesh == null) {
				listenersRowRefesh = new ArrayList<>(1);
			}

			listenersRowRefesh.add(listener);

		} finally {
			listeners_mon.exit();
		}
	}

	public void removeRefreshListener(TableRowRefreshListener listener) {
		try {
			listeners_mon.enter();

			if (listenersRowRefesh == null) {
				return;
			}

			listenersRowRefesh.remove(listener);

		} finally {
			listeners_mon.exit();
		}
	}

	public void invokeRefreshListeners(TableRowCore row) {
		Object[] listeners;
		try {
			listeners_mon.enter();
			if (listenersRowRefesh == null) {
				return;
			}
			listeners = listenersRowRefesh.toArray();

		} finally {
			listeners_mon.exit();
		}

		for (int i = 0; i < listeners.length; i++) {
			try {
				TableRowRefreshListener l = (TableRowRefreshListener) listeners[i];

				l.rowRefresh(row);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}


	@Override
	public void runForAllRows(TableGroupRowRunner runner) {
		runForAllRows( runner, false );
	}
	
	private void runForAllRows(TableGroupRowRunner runner, boolean doSubRows) {
		
		// put to array instead of synchronised iterator, so that runner can remove
		
		if ( doSubRows ){
			
				// only support per-row runner...
			
			TableRowCore[] rows = getRows();

			for (int i = 0; i < rows.length; i++) {
				
				runner.run(rows[i]);

				int numSubRows = rows[i].getSubItemCount();
				if (numSubRows > 0) {
					TableRowCore[] subRows = rows[i].getSubRowsRecursive(false);
					for (TableRowCore subRow : subRows) {
						if (subRow != null) {
							runner.run(subRow);
						}
					}
				}
			}
			
		}else{
			TableRowCore[] rows = getRows();
			
			if (runner.run(rows)) {
				return;
			}
	
			for (int i = 0; i < rows.length; i++) {
				runner.run(rows[i]);
			}
		}
	}

	// see common.tableview
	@Override
	public void runForAllRows(TableGroupRowVisibilityRunner runner) {
		if (isDisposed()) {
			return;
		}

		// put to array instead of synchronised iterator, so that runner can remove
		TableRowCore[] rows = getRows();

		for (int i = 0; i < rows.length; i++) {
			boolean isRowVisible = isRowVisible(rows[i]);
			runner.run(rows[i], isRowVisible);

			int numSubRows = rows[i].getSubItemCount();
			if (numSubRows > 0) {
				TableRowCore[] subRows = rows[i].getSubRowsRecursive(false);
				for (TableRowCore subRow : subRows) {
					if (subRow != null) {
						runner.run(subRow, isRowVisible(subRow));
					}
				}
			}
		}
	}


	/** For each row source that the user has selected, run the code
	 * provided by the specified parameter.
	 *
	 * @param runner Code to run for each selected row/datasource
	 */
	@Override
	public void runForSelectedRows(TableGroupRowRunner runner) {
		if (isDisposed()) {
			return;
		}

		TableRowCore[] rows;
		synchronized (rows_sync) {
			rows = selectedRows.toArray(new TableRowCore[0]);
		}
		boolean ran = runner.run(rows);
		if (!ran) {
			for (int i = 0; i < rows.length; i++) {
				TableRowCore row = rows[i];
				runner.run(row);
			}
		}
	}

	@Override
	public boolean isUnfilteredDataSourceAdded(Object ds) {
		synchronized (rows_sync) {
			return listUnfilteredDataSources.containsKey(ds);
		}
	}

	@SuppressWarnings("unchecked")
	public void refilter() {
		if (filter == null) {
			return;
		}
		if (filter.eventUpdate != null) {
			filter.eventUpdate.cancel();
			filter.text = filter.nextText;
			filter.checker.filterSet(filter.text);
		}
		filter.eventUpdate = null;

		boolean changed = false;

		synchronized (rows_sync) {
			DATASOURCETYPE[] unfilteredArray = (DATASOURCETYPE[]) listUnfilteredDataSources.keySet().toArray();
			if (DEBUGADDREMOVE) {
				debug("filter: unfilteredArray is " + unfilteredArray.length);
			}

			if (getFilterSubRows()){
				
				for (Iterator<TableRowCore> iter = sortedRows.iterator(); iter.hasNext();) {
					TableRowCore row = iter.next();
					for ( TableRowCore sr: row.getSubRowsWithNull()){
						if (sr.refilter()){
							
							changed = true;
						}
					}
				}
			}

			Collection<DATASOURCETYPE> existing = getDataSources();
			List<DATASOURCETYPE> listRemoves = new ArrayList<>();
			List<DATASOURCETYPE> listAdds = new ArrayList<>();

			for (DATASOURCETYPE dst : unfilteredArray) {
				boolean bHave = existing.contains(dst);
				boolean isOurs;
				
				try{
					isOurs = filter.checker.filterCheck(dst, filter.text,filter.regex);
					
				}catch( PatternSyntaxException e ){
					// get this with malformed filter regex, ignore
					isOurs = true;
				}	
				
				if (!isOurs) {
					if (bHave) {
						listRemoves.add(dst);
					}
				} else {
					if (!bHave) {
						listAdds.add(dst);
					}
				}
			}
			if (listRemoves.size() > 0) {
				removeDataSources((DATASOURCETYPE[]) listRemoves.toArray());
			}
			if (listAdds.size() > 0) {
				addDataSources((DATASOURCETYPE[]) listAdds.toArray(), true);
			}

			// add back the ones removeDataSources removed
			for ( DATASOURCETYPE ds: listRemoves ){
				listUnfilteredDataSources.put(ds,"");
			}
		}
		
		
		if ( changed ){
			tableMutated();
			redrawTable();
		}

		processDataSourceQueue();
	}

	public boolean
	isFiltered(
		DATASOURCETYPE	ds )
	{
		if ( filter == null ){
			return( true );
		}

		try{
			return( filter.checker.filterCheck( ds, filter.text, filter.regex ));
			
		}catch( PatternSyntaxException e ){
			// get this with malformed filter regex, ignore		
			return( false );
		}
	}

	protected abstract boolean
	getFilterSubRows();
	
	protected abstract void
	redrawTable();
	
	protected void debug(String s) {
		AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger("table");
		diag_logger.log(SystemTime.getCurrentTime() + ":" + getTableID() + ": " + s);

		System.out.println(Thread.currentThread().getName() + "."
				+ Integer.toHexString(hashCode()) + "] " + SystemTime.getCurrentTime()
				+ ": " + getTableID() + ": " + s);
	}

	private void _processDataSourceQueue() {
		boolean hasAdd;
		boolean hasRemove;
		
		try{
			datsaSourceQueueProcessingCount.incrementAndGet();

			Object[] dataSourcesAdd = null;
			Object[] dataSourcesRemove = null;

			synchronized (rows_sync) {
				if (dataSourcesToAdd.size() > 0) {
					boolean removed_something = false;
					for ( DATASOURCETYPE ds: dataSourcesToRemove.keySet()){
	
						if ( dataSourcesToAdd.remove( ds ) != null ){
	
							removed_something = true;
						}
					}
	
					if ( removed_something&& DEBUGADDREMOVE){
						debug("Saved time by not adding a row that was removed");
					}
	
					dataSourcesAdd = dataSourcesToAdd.keySet().toArray();
	
					dataSourcesToAdd.clear();
				}
	
				if (dataSourcesToRemove.size() > 0) {
					dataSourcesRemove = dataSourcesToRemove.keySet().toArray();
					if (DEBUGADDREMOVE && dataSourcesRemove.length > 1) {
						debug("Streamlining removing " + dataSourcesRemove.length + " rows");
					}
					dataSourcesToRemove.clear();
				}
			}
	
			hasAdd = dataSourcesAdd != null && dataSourcesAdd.length > 0;
			if (hasAdd) {
				reallyAddDataSources(dataSourcesAdd);
				if (DEBUGADDREMOVE && dataSourcesAdd.length > 1) {
					debug("Streamlined adding " + dataSourcesAdd.length + " rows");
				}
			}
	
			hasRemove = dataSourcesRemove != null && dataSourcesRemove.length > 0;
			if (hasRemove) {
				reallyRemoveDataSources(dataSourcesRemove);
			}
		}finally{
			
			datsaSourceQueueProcessingCount.decrementAndGet();
		}
		
		if (hasAdd || hasRemove) {
			tableMutated();
		}
	}

	@Override
	public void addDataSource(DATASOURCETYPE dataSource) {
		addDataSource(dataSource, false);
	}

	private void addDataSource(DATASOURCETYPE dataSource, boolean skipFilterCheck) {

		if (dataSource == null) {
			return;
		}

		synchronized (rows_sync) {
			listUnfilteredDataSources.put(dataSource,"");
		}
		if (DEBUGADDREMOVE) {
			debug("AddDS: " + dataSource + "; listUnfilteredDS: "
					+ listUnfilteredDataSources.size() + " via "
					+ Debug.getCompressedStackTrace(4));
		}

		try{
			if (!skipFilterCheck && filter != null
					&& !filter.checker.filterCheck(dataSource, filter.text, filter.regex)) {
				return;
			}
		}catch( PatternSyntaxException e ){
			// get this with malformed filter regex, ignore
		}
		
		if (DataSourceCallBackUtil.IMMEDIATE_ADDREMOVE_DELAY == 0) {
			reallyAddDataSources(new Object[] {
				dataSource
			});
			return;
		}

		// In order to save time, we cache entries to be added and process them
		// in a refresh cycle.  This is a huge benefit to tables that have
		// many rows being added and removed in rapid succession

		synchronized (rows_sync) {
			if ( dataSourcesToRemove.remove( dataSource ) != null ){
				// we're adding, override any pending removal
				if (DEBUGADDREMOVE) {
					debug("AddDS: Removed from toRemove.  Total Removals Queued: "
							+ dataSourcesToRemove.size());
				}
			}

			if ( dataSourcesToAdd.containsKey(dataSource)){
				// added twice.. ensure it's not in the remove list
				if (DEBUGADDREMOVE) {
					debug("AddDS: Already There.  Total Additions Queued: "
							+ dataSourcesToAdd.size());
				}
			} else {
				dataSourcesToAdd.put(dataSource, "");
				if (DEBUGADDREMOVE) {
					debug("Queued 1 dataSource to add.  Total Additions Queued: "
							+ dataSourcesToAdd.size() + "; already=" + sortedRows.size());
				}
				refreshenProcessDataSourcesTimer();
			}
		}
	}

	// see common.TableView
	@Override
	public void addDataSources(final DATASOURCETYPE dataSources[]) {
		addDataSources(dataSources, false);
	}

	private void addDataSources(final DATASOURCETYPE dataSources[],
			boolean skipFilterCheck) {

		if (dataSources == null) {
			return;
		}

		if (DEBUGADDREMOVE) {
			debug("AddDS: " + dataSources.length );
		}

		synchronized (rows_sync) {
			for (DATASOURCETYPE ds : dataSources) {
				if (ds == null) {
					continue;
				}
				listUnfilteredDataSources.put(ds, null);
			}
		}

		if (DataSourceCallBackUtil.IMMEDIATE_ADDREMOVE_DELAY == 0) {
			if (!skipFilterCheck && filter != null) {
				try{
					for (int i = 0; i < dataSources.length; i++) {
						if (!filter.checker.filterCheck(dataSources[i], filter.text,
								filter.regex)) {
							dataSources[i] = null;
						}
					}
				}catch( PatternSyntaxException e ){
					// get this with malformed filter regex, ignore
				}
			}
			reallyAddDataSources(dataSources);
			return;
		}

		// In order to save time, we cache entries to be added and process them
		// in a refresh cycle.  This is a huge benefit to tables that have
		// many rows being added and removed in rapid succession

		synchronized (rows_sync) {
			int count = 0;

			for (int i = 0; i < dataSources.length; i++) {
				DATASOURCETYPE dataSource = dataSources[i];
				if (dataSource == null) {
					continue;
				}
				
				try{
					if (!skipFilterCheck
							&& filter != null
							&& !filter.checker.filterCheck(dataSource, filter.text,
									filter.regex)) {
						continue;
					}
				}catch( PatternSyntaxException e ){
					// get this with malformed filter regex, ignore
				}
				
				dataSourcesToRemove.remove(dataSource); // may be pending removal, override

				if (dataSourcesToAdd.containsKey(dataSource)) {
				} else {
					count++;
					dataSourcesToAdd.put(dataSource, "");
				}
			}

			if (DEBUGADDREMOVE) {
				debug("Queued " + count + " of " + dataSources.length
						+ " dataSources to add.  Total Qd: " + dataSourcesToAdd.size()
						+ ";Unfiltered: " + listUnfilteredDataSources.size() +
						"; skipFilterCheck? " + skipFilterCheck + "; via "
						+ Debug.getCompressedStackTrace(5));
			}

		}

		refreshenProcessDataSourcesTimer();
	}
	
	protected boolean
	hasPendingDSChanges()
	{
		synchronized (rows_sync){
			
			if ( !dataSourcesToAdd.isEmpty() || !dataSourcesToRemove.isEmpty()){
				
				return( true );
			}
		}
		
		return( datsaSourceQueueProcessingCount.get() > 0 );
	}

	// @see TableView#dataSourceExists(java.lang.Object)
	@Override
	public boolean dataSourceExists(DATASOURCETYPE dataSource) {
		synchronized (rows_sync) {
			return mapDataSourceToRow.containsKey(dataSource) || dataSourcesToAdd.containsKey(dataSource);
		}
	}

	@Override
	public void processDataSourceQueue() {
		getOffUIThread(new AERunnable() {
			@Override
			public void runSupport() {
				_processDataSourceQueue();
			}
		});
	}

	public abstract void getOffUIThread(AERunnable runnable);

	@Override
	public void processDataSourceQueueSync() {
		_processDataSourceQueue();
	}

	/**
	 * @note bIncludeQueue can return an invalid number, such as a negative :(
	 */
	@Override
	public int size(boolean bIncludeQueue) {
		synchronized (rows_sync) {
			int size = sortedRows.size();

			if (bIncludeQueue) {
				if (dataSourcesToAdd != null) {
					size += dataSourcesToAdd.size();
				}
				if (dataSourcesToRemove != null) {
					size -= dataSourcesToRemove.size();
				}
			}
			return size;
		}
	}

	// @see TableView#getRows()
	@Override
	public TableRowCore[] getRows() {
		synchronized (rows_sync) {
			return sortedRows.toArray(new TableRowCore[0]);
		}
	}

	public TableRowCore[] getRowsAndSubRows( boolean includeHidden ) {
		synchronized (rows_sync) {
			List<TableRowCore>	result = new ArrayList<>();
			
			getRowsAndSubRows( result, sortedRows.toArray( new TableRowCore[sortedRows.size()]), includeHidden );
			
			return( result.toArray( new TableRowCore[ result.size()]));
		}
	}
	
	private void
	getRowsAndSubRows(
		List<TableRowCore>	result,
		TableRowCore[]		rows,
		boolean 			includeHidden )
	{
		for ( TableRowCore row: rows ){
			
			if ( includeHidden || !row.isHidden()){
			
				result.add( row );
			
				if ( includeHidden || row.isExpanded()){
				
					getRowsAndSubRows( result, row.getSubRowsWithNull(), includeHidden);
				}
			}
		}
	}
	
	protected boolean
	numberAllVisibleRows()
	{
		boolean changed = false;
		synchronized( rows_sync ){
			int	pos = 0;
			for ( TableRowCore row: sortedRows ){
				
				if ( row.isHidden()){
					
					continue;
				}
				
				if (row.setVisibleRowIndex( pos++ )){
					changed = true;
				}
				
				if ( row.isExpanded()){
					
					TableRowCore[] kids = row.getSubRowsWithNull();
					
					pos = numberAllVisibleRows( kids, pos );
					
					if ( pos < 0 ){
						changed = true;
						pos = -pos;
					}
				}
			}
		}
		return( changed );
	}
	
	private int
	numberAllVisibleRows(
		TableRowCore[]		rows,
		int					pos )
	{
		boolean changed = false;
		for ( TableRowCore row: rows ){
			if ( row.isHidden()){
				
				continue;
			}
			
			if (row.setVisibleRowIndex( pos++ )){
				changed = true;
			}
			
			if ( row.isExpanded()){
				
				TableRowCore[] kids = row.getSubRowsWithNull();
				
				pos = numberAllVisibleRows( kids, pos );
				
				if ( pos < 0 ){
					changed = true;
					pos = -pos;
				}
			}
		}
		
		return( changed?-pos:pos );
	}
	
	@Override
	public int[] 
	getRowAndSubRowCount()
	{
		int[]	result= { 0, 0 };
		
		synchronized (rows_sync) {
			
			getRowAndSubRowCount( sortedRows.toArray( new TableRowCore[sortedRows.size()]), result, false);
		}
		
		return( result );
	}
		
	private void
	getRowAndSubRowCount(
		TableRowCore[]	rows,
		int[]			result,
		boolean			isHidden )
	{
		for ( TableRowCore row: rows ){
			
			result[0]++;
			
			boolean	hidden = isHidden || row.isHidden();
			
			if ( !hidden ){
				
				result[1]++;
				
				if ( !row.isExpanded()){
					
					hidden = true;
				}
			}
			
			getRowAndSubRowCount( row.getSubRowsWithNull(), result, hidden );
		}
	}
	
					
	// @see TableView#getRow(java.lang.Object)
	@Override
	public TableRowCore getRow(DATASOURCETYPE dataSource) {
		synchronized (rows_sync) {
			return mapDataSourceToRow.get(dataSource);
		}
	}

	// @see TableView#getRow(int)
	@Override
	public TableRowCore getRow(int iPos) {
		synchronized (rows_sync) {
			if (iPos >= 0 && iPos < sortedRows.size()) {
				TableRowCore row = sortedRows.get(iPos);

				if (row.getIndex() != iPos) {
					row.setTableItem(iPos);
				}
				return row;
			}
		}
		return null;
	}

	public TableRowCore getRowQuick(int iPos) {
		try {
			return sortedRows.get(iPos);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public int indexOf(TableRowCore row) {
		synchronized (rows_sync) {
			return sortedRows.indexOf(row);
		}
	}

	@Override
	public int getRowCount() {
		// don't use sortedRows here, it's not always up to date
		synchronized (rows_sync) {
			return mapDataSourceToRow.size();
		}
	}

	// @see TableView#getDataSources()
	@Override
	public HashSet<DATASOURCETYPE> getDataSources() {
		synchronized (rows_sync) {
			return new HashSet<>(mapDataSourceToRow.keySet());
		}
	}

	// @see TableView#getDataSources()
	@Override
	public HashSet<DATASOURCETYPE> getDataSources(boolean include_filtered) {
		synchronized (rows_sync) {
			if ( include_filtered ){
				return new HashSet<>(listUnfilteredDataSources.keySet());

			}else{
				return new HashSet<>(mapDataSourceToRow.keySet());
			}
		}
	}

	// @see TableView#removeDataSource(java.lang.Object)
	@Override
	public void removeDataSource(final DATASOURCETYPE dataSource) {
		if (dataSource == null) {
			return;
		}

		synchronized (rows_sync) {
			listUnfilteredDataSources.remove(dataSource);
		}


		if (DEBUGADDREMOVE) {
			debug("RemDS: " + dataSource + "; listUnfilteredDS=" + listUnfilteredDataSources.size() );
		}

		if (DataSourceCallBackUtil.IMMEDIATE_ADDREMOVE_DELAY == 0) {
			reallyRemoveDataSources(new Object[] {
				dataSource
			});
			tableMutated();
			return;
		}

		synchronized (rows_sync) {
			dataSourcesToAdd.remove(dataSource); // override any pending addition
			dataSourcesToRemove.put(dataSource, "");

			if (DEBUGADDREMOVE) {
				debug("Queued 1 dataSource to remove.  Total Queued: "
						+ dataSourcesToRemove.size());
			}
		}

		refreshenProcessDataSourcesTimer();
	}

	/** Remove the specified dataSource from the table.
	 *
	 * @param dataSources data sources to be removed
	 */
	@Override
	public void removeDataSources(final DATASOURCETYPE[] dataSources) {
		if (dataSources == null || dataSources.length == 0) {
			return;
		}

		if (DEBUGADDREMOVE) {
			debug("RemDS: " + dataSources.length );
		}

		synchronized (rows_sync) {
			for ( DATASOURCETYPE ds: dataSources ){
				listUnfilteredDataSources.remove(ds);
			}
		}

		if (DataSourceCallBackUtil.IMMEDIATE_ADDREMOVE_DELAY == 0) {
			reallyRemoveDataSources(dataSources);
			tableMutated();
			return;
		}

		synchronized (rows_sync) {
			for (DATASOURCETYPE dataSource : dataSources) {
				dataSourcesToAdd.remove(dataSource); // override any pending addition
				dataSourcesToRemove.put(dataSource, "");
			}

			if (DEBUGADDREMOVE) {
				debug("Queued " + dataSources.length
						+ " dataSources to remove.  Total Qd: " + dataSourcesToRemove.size()
						+ "; Unfiltered: " + listUnfilteredDataSources.size() + " via "
						+ Debug.getCompressedStackTrace(4));
			}
		}

		refreshenProcessDataSourcesTimer();
	}

	private void refreshenProcessDataSourcesTimer() {
		if (bReallyAddingDataSources || processDataSourceQueueCallback == null) {
			// when processDataSourceQueueCallback is null, we are disposing
			return;
		}

/////////////////////////////////////////////////////////////////////////////////		if (cellEditNotifier != null) {
/////////////////////////////////////////////////////////////////////////////////			cellEditNotifier.sourcesChanged();
/////////////////////////////////////////////////////////////////////////////////		}

		boolean processQueueImmediately = DataSourceCallBackUtil.addDataSourceAggregated(processDataSourceQueueCallback);

		if (processQueueImmediately) {
			processDataSourceQueue();
		}
	}

	private void reallyAddDataSources(final Object dataSources[]) {
		// Note: We assume filterCheck has already run, and the list of dataSources
		//       all passed the filter

		if (isDisposed()) {
			return;
		}

		bReallyAddingDataSources = true;
		if (DEBUGADDREMOVE) {
			debug(">>" + " Add " + dataSources.length + " rows;");
		}

		// Create row, and add to map immediately
		synchronized (rows_sync) {
			try {

  			//long lStartTime = SystemTime.getCurrentTime();

  			for (int i = 0; i < dataSources.length; i++) {
  				Object ds = dataSources[i];
  				if (ds == null) {
  					if (DEBUGADDREMOVE) {
  						debug("-- Null DS for " + i);
  					}
  					continue;
  				}

  				if (mapDataSourceToRow.containsKey(ds)) {
  					if (DEBUGADDREMOVE) {
  						debug("-- " + i + " already added: " + ds.getClass());
  					}
  					dataSources[i] = null;
  				} else {
  					TableRowCore rowCore = createNewRow(ds);
  					mapDataSourceToRow.put((DATASOURCETYPE) ds, rowCore);
  				}
  			}
  		} catch (Exception e) {
  			Logger.log(new LogEvent(LOGID, "Error while added row to Table "
  					+ getTableID(), e));
  		}
		}

		if (DEBUGADDREMOVE) {
			debug("--" + " Add " + dataSources.length + " rows;");
		}

		addSortedDataSource(dataSources);

		bReallyAddingDataSources = false;
	}

	public abstract TableRowCore createNewRow(Object object);

	@Override
	public void delete() {
		triggerLifeCycleListener(TableLifeCycleListener.EVENT_TABLELIFECYCLE_SHUTDOWN);

		// clear listeners that should be empty by now
		listenersLifeCycle.clear();
		listenersCountChange.clear();
		listenersDataSourceChanged.clear();
		listenersExpansionChange.clear();
		listenersRefresh.clear();
		listenersSelection.clear();

		listenersRowRefesh = null;

		processDataSourceQueueCallback = null;
	}

	public Object getRowsSync() {
		return rows_sync;
	}

	@Override
	public void generate(IndentWriter writer) {
		writer.println("Diagnostics for " + this + " (" + getTableID() + ")");

		synchronized (rows_sync) {
			writer.println("DataSources scheduled to Add/Remove: "
					+ dataSourcesToAdd.size() + "/" + dataSourcesToRemove.size());

			writer.println("TableView: " + mapDataSourceToRow.size() + " datasources");
			Iterator<DATASOURCETYPE> it = mapDataSourceToRow.keySet().iterator();

			while (it.hasNext()) {

				Object key = it.next();

				writer.println("  " + key + " -> " + mapDataSourceToRow.get(key));
			}

		}
	}

	@Override
	public void removeAllTableRows() {

		ArrayList<TableRowCore> itemsToRemove;

		boolean	hadSelected;
		
		synchronized (rows_sync) {

			itemsToRemove = new ArrayList<>(mapDataSourceToRow.values());
			mapDataSourceToRow.clear();
			sortedRows.clear();

			dataSourcesToAdd.clear();
			dataSourcesToRemove.clear();

			listUnfilteredDataSources.clear();

			hadSelected = !selectedRows.isEmpty();
			
			selectedRows.clear();
			listSelectedCoreDataSources = null;

			if (DEBUGADDREMOVE) {
				debug("removeAll");
			}
		}

		if ( hadSelected ){
			
			triggerTabViewsDataSourceChanged();
		}
		
			// parg - added this to ensure resources associated with rows (e.g. graphics) are released properly
			// not sure if any of the other things that normally happen on row-removal are also required to happen here
			// e.g. triggerListenerRowRemoved(item); and uiRemoveRows(...)

		for ( TableRowCore row: itemsToRemove ){

			row.delete();
		}
	}

	@SuppressWarnings("null")
	private void reallyRemoveDataSources(final Object[] dataSources) {
		final long lStart = SystemTime.getCurrentTime();

		int rows_removed = 0;

		StringBuffer sbWillRemove = null;
		if (DEBUGADDREMOVE) {
			debug(">>> Remove rows.  Start w/" + getRowCount()
					+ "ds;"
					+ (SystemTime.getCurrentTime() - lStart) + "ms wait");

			sbWillRemove = new StringBuffer("Will soon remove row #");
		}

		ArrayList<TableRowCore> itemsToRemove = new ArrayList<>();
		ArrayList<Integer> indexesToRemove = new ArrayList<>();

		ArrayList<TableRowCore> removedWithSelection = new ArrayList<>();
		synchronized (rows_sync) {
  		for (int i = 0; i < dataSources.length; i++) {
  			if (dataSources[i] == null) {
  				continue;
  			}

  			TableRowCore item = mapDataSourceToRow.get(dataSources[i]);
  			if (item != null) {
  				if (isProvideIndexesOnRemove()) {
    				// use sortedRows position instead of item.getIndex(), because
    				// getIndex may have a wrong value (unless we fillRowGaps() which
    				// is more time consuming and we do afterwards anyway)
    				int index = sortedRows.indexOf(item);
    				indexesToRemove.add(index);
    				if (DEBUGADDREMOVE) {
    					if (i != 0) {
    						sbWillRemove.append(", ");
    					}
    					sbWillRemove.append(index);
    				}
  				}

  				itemsToRemove.add(item);
  				mapDataSourceToRow.remove(dataSources[i]);
  				triggerListenerRowRemoved(item);
  				sortedRows.remove(item);
  				if ( selectedRows.remove(item)){
  					removedWithSelection.add( item );
  				}

  				rows_removed++;
  			}
  		}
  		if (rows_removed > 0) {
  			listSelectedCoreDataSources = null;
  		}
		}

		if (DEBUGADDREMOVE) {
			debug(sbWillRemove.toString());
			debug("#itemsToRemove=" + itemsToRemove.size());
		}

		if (itemsToRemove.size() > 0) {
			uiRemoveRows(itemsToRemove.toArray(new TableRowCore[0]),
					indexesToRemove.toArray(new Integer[0]));

			// Finally, delete the rows
			for (Iterator<TableRowCore> iter = itemsToRemove.iterator(); iter.hasNext();) {
				TableRowCore row = iter.next();
				row.delete();
			}
		}
		
		if ( !removedWithSelection.isEmpty()){
			
			TableRowCore[] deselected = removedWithSelection.toArray( new TableRowCore[removedWithSelection.size()] );
			
			triggerSelectionChangeListeners( new TableRowCore[0], deselected );
			
			triggerDeselectionListeners( deselected );
			
			triggerTabViewsDataSourceChanged();
		}

		if (DEBUGADDREMOVE) {
			debug("<< Remove " + itemsToRemove.size() + " rows. now "
					+ mapDataSourceToRow.size() + "ds");
		}

	}

	public void
	tableMutated()
	{
		filter f = filter;

		if ( f != null ){
			TableViewFilterCheck<DATASOURCETYPE> checker = f.checker;

			if ( checker instanceof TableViewFilterCheck.TableViewFilterCheckEx ){

				((TableViewFilterCheck.TableViewFilterCheckEx)checker).viewChanged( this );
			}
		}
	}


	protected void fillRowGaps(boolean bForceDataRefresh) {
		_sortColumn(bForceDataRefresh, true, false);
	}

	public void 
	sortRows(
		boolean bForceDataRefresh, 
		boolean async ) 
	{
		if ( async ){
			
			rowSorterRunnable.setForceRefresh(bForceDataRefresh);
			
			rowSorter.dispatch();
			
		}else{
			
			_sortColumn(bForceDataRefresh, false, false);
		}
	}

	protected void _sortColumn(final boolean bForceDataRefresh, final boolean bFillGapsOnly,
			final boolean bFollowSelected) {
		if (isDisposed()) {
			return;
		}

		// Quick check & removal of any sort columns no longer visible. Could
		// probably be done in a much better place
		if (!sortColumns.isEmpty()) {
			for (Iterator<TableColumnCore> iter = sortColumns.iterator(); iter.hasNext();) {
				TableColumnCore sortColumn = iter.next();
				if (!sortColumn.isVisible()) {
					iter.remove();
				}
			}
		}

		long lTimeStart;
		if (DEBUG_SORTER) {
			//System.out.println(">>> Sort.. ");
			lTimeStart = System.currentTimeMillis();
		}

		int iNumMoves = 0;

		boolean needsUpdate = false;
		boolean	orderChanged = false;

		synchronized (rows_sync) {
						
			if (bForceDataRefresh && !sortColumns.isEmpty()) {
				for (TableColumnCore sortColumn : sortColumns) {
					String sColumnID = sortColumn.getName();
					for (TableRowCore row : sortedRows) {
						TableCellCore[] cells = row.getSortColumnCells(sColumnID);
						for (TableCellCore cell : cells) {
							cell.refresh(true);
						}
						TableRowCore[] subs = row.getSubRowsRecursive(true);

						for (TableRowCore sr : subs) {
							cells = sr.getSortColumnCells(sColumnID);
							for (TableCellCore cell : cells) {
								cell.refresh(true);
							}
						}
					}
				}
			}

			if (!bFillGapsOnly) {
				boolean hasSortValueChanged = false;
				for (TableColumnCore sortColumn : sortColumns) {
					if (sortColumn.getLastSortValueChange() >= lLastSortedOn) {
						hasSortValueChanged = true;
						break;
					}
				}
				if (hasSortValueChanged) {
					lLastSortedOn = SystemTime.getCurrentTime();
					if (sortColumns.size() == 1) {
						sortedRows.sort(sortColumns.get(0));
					} else {
						sortedRows.sort((o1, o2) -> {
							for (TableColumnCore sortColumn : sortColumns) {
								int compare = sortColumn.compare(o1, o2);
								if (compare != 0) {
									return compare;
								}
							}
							return 0;
						});
					}
					
					for ( TableRowCore r: sortedRows ){
						// TODO: Change to sortColumn list
						if ( r.sortSubRows(sortColumns.get(0) )){
							needsUpdate = true;
							orderChanged = true;
						}
					}
					
					if (DEBUG_SORTER) {
						long lTimeDiff = (System.currentTimeMillis() - lTimeStart);
						if (lTimeDiff >= 0) {
							debug("--- Build & Sort "
									+ sortColumns.stream().map(m -> m.getName()).collect(
											Collectors.joining(", ", "{", "}"))
									+ " took " + lTimeDiff + "ms");
						}
					}
				} else {
					if (DEBUG_SORTER) {
						debug("Skipping sort :)");
					}
				}
			}

			for (int i = 0; i < sortedRows.size(); i++) {
				TableRowCore row = sortedRows.get(i);
				boolean visible = row.isVisible();
				if (row.setTableItem(i)) {
					orderChanged=true;
					if (visible) {
						needsUpdate = true;
					}
					iNumMoves++;
				}
			}

			if (DEBUG_SORTER && iNumMoves > 0) {
				debug("Sort: numMoves= " + iNumMoves + ";needUpdate?" + needsUpdate);
			}
		}
		
		if (orderChanged) {
			tableMutated();
			
		}	
		if (needsUpdate) {
			visibleRowsChanged();
		}

		if (DEBUG_SORTER) {
			long lTimeDiff = (System.currentTimeMillis() - lTimeStart);
			if (lTimeDiff >= 500) {
				debug("<<< Sort & Assign took " + lTimeDiff + "ms with "
						+ iNumMoves + " rows (of " + sortedRows.size() + ") moved. "
						+ Debug.getCompressedStackTrace());
			}
		}
	}

	public abstract void visibleRowsChanged();

	public abstract void uiRemoveRows(TableRowCore[] rows, Integer[] rowIndexes);

	public abstract int uiGuessMaxVisibleRows();

	@Override
	public void resetLastSortedOn() {
		synchronized (rows_sync) {
			lLastSortedOn = 0;
		}
	}

	// @see TableView#getColumnCells(java.lang.String)
	@Override
	public TableCellCore[] getColumnCells(String sColumnName) {

		synchronized (rows_sync) {
			TableCellCore[] cells = new TableCellCore[sortedRows.size()];

			int i = 0;
			for (Iterator<TableRowCore> iter = sortedRows.iterator(); iter.hasNext();) {
				TableRowCore row = iter.next();
				cells[i++] = row.getTableCellCore(sColumnName);
			}

			return cells;
		}

	}

	private void addSortedDataSource(final Object dataSources[]) {

		if (isDisposed()) {
			return;
		}

		TableRowCore[] selectedRows = getSelectedRows();

		boolean bWas0Rows = getRowCount() == 0;
		try {

			if (DEBUGADDREMOVE) {
				debug("--" + " Add " + dataSources.length + " rows to SWT");
			}

			long lStartTime = SystemTime.getCurrentTime();

			final List<TableRowCore> rowsAdded = new ArrayList<>();

			// add to sortedRows list in best position.
			// We need to be in the SWT thread because the rowSorter may end up
			// calling SWT objects.
			for (int i = 0; i < dataSources.length; i++) {
				Object dataSource = dataSources[i];
				if (dataSource == null) {
					continue;
				}

				TableRowCore row;
				synchronized (rows_sync) {
					row = mapDataSourceToRow.get(dataSource);
				}
				//if ((row == null) || row.isRowDisposed() || sortedRows.indexOf(row) >= 0) {
				if (row == null || row.isRowDisposed()) {
					continue;
				}
				if (!sortColumns.isEmpty()) {
					TableCellCore[] cells = row.getSortColumnCells(null);
					for (TableCellCore cell : cells) {
						try {
							cell.invalidate();
							// refresh could have caused a thread lock if we were
							// synchronized by rows_sync
							cell.refresh(true);
						} catch (Exception e) {
							Logger.log(new LogEvent(LOGID,
									"Minor error adding a row to table " + getTableID(), e));
						}
					}
				}

				synchronized (rows_sync) {
						// check that the row item hasn't been removed in the meantime while lock 
						// not held
					
					if (row ==  mapDataSourceToRow.get( dataSource )){
						try {
							int index = 0;
							if (sortedRows.size() > 0) {
								// If we are >= to the last item, then just add it to the end
								// instead of relying on binarySearch, which may return an item
								// in the middle that also is equal.
								TableRowCore lastRow = sortedRows.get(sortedRows.size() - 1);
								// todo: use multi-sort
								if (sortColumns.isEmpty() || sortColumns.get(0).compare(row, lastRow) >= 0) {
									index = sortedRows.size();
									sortedRows.add(row);
									if (DEBUGADDREMOVE) {
										debug("Adding new row to bottom");
									}
								} else {
									index = Collections.binarySearch(sortedRows, row, sortColumns.get(0));
									if (index < 0) {
										index = -1 * index - 1; // best guess
									}
	
									if (index > sortedRows.size()) {
										index = sortedRows.size();
									}
	
									if (DEBUGADDREMOVE) {
										debug("Adding new row at position " + index + " of "
												+ (sortedRows.size() - 1));
									}
									sortedRows.add(index, row);
								}
							} else {
								if (DEBUGADDREMOVE) {
									debug("Adding new row to bottom (1st Entry)");
								}
								index = sortedRows.size();
								sortedRows.add(row);
							}
	
							rowsAdded.add(row);
	
							// XXX Don't set table item here, it will mess up selected rows
							//     handling (which is handled in fillRowGaps called later on)
							//row.setTableItem(index);
	
	
							//row.setIconSize(ptIconSize);
						} catch (Exception e) {
							e.printStackTrace();
							Logger.log(new LogEvent(LOGID, "Error adding a row to table "
									+ getTableID(), e));
							try {
								if (!sortedRows.contains(row)) {
									sortedRows.add(row);
								}
							} catch (Exception e2) {
								Debug.out(e2);
							}
						}
					}
				}
			} // for dataSources

			// NOTE: if the listener tries to do something like setSelected,
			// it will fail because we aren't done adding.
			// we should trigger after fillRowGaps()
			triggerListenerRowAdded(rowsAdded.toArray(new TableRowCore[0]));


			if (DEBUGADDREMOVE) {
				debug("Adding took " + (SystemTime.getCurrentTime() - lStartTime)
						+ "ms");
			}

		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "Error while adding row to Table "
					+ getTableID(), e));
		}
		refreshenProcessDataSourcesTimer();

		visibleRowsChanged();
		fillRowGaps(false);

		if (selectedRows.length > 0) {
			setSelectedRows(selectedRows);
		}
		if (DEBUGADDREMOVE) {
			debug("<< " + size(false));
		}

	}

	// @see TableStructureModificationListener#cellInvalidate(TableColumnCore, java.lang.Object)
	@Override
	public void cellInvalidate(TableColumnCore tableColumn,
	                           DATASOURCETYPE data_source) {
		cellInvalidate(tableColumn, data_source, true);
	}

	public void cellInvalidate(TableColumnCore tableColumn,
			final DATASOURCETYPE data_source, final boolean bMustRefresh) {
		final String sColumnName = tableColumn.getName();

		runForAllRows(new TableGroupRowRunner() {
			@Override
			public void run(TableRowCore row) {
				TableCellCore cell = row.getTableCellCore(sColumnName);
				if (cell != null && cell.getDataSource() != null
						&& cell.getDataSource().equals(data_source)) {
					cell.invalidate(bMustRefresh);
				}
			}
		});
	}

	// see common.TableView
	@Override
	public void columnInvalidate(final String sColumnName) {
		columnInvalidate(sColumnName,false);
	}

	@Override
	public void columnInvalidate(final String sColumnName, boolean mustRefresh ) {
		TableColumnCore tc = TableColumnManager.getInstance().getTableColumnCore(
				getTableID(), sColumnName);
		if (tc != null) {
			columnInvalidate(tc, tc.getType() == TableColumnCore.TYPE_TEXT_ONLY || mustRefresh );
		}
	}
	
	public void columnInvalidate(TableColumnCore tableColumn,
			final boolean bMustRefresh) {
		runForAllRows(new TableGroupRowRunner() {
			@Override
			public void run(TableRowCore row) {
				TableCellCore cell = row.getTableCellCore(tableColumn.getName());
				if (cell == null) {
					return;
				}
				cell.invalidate(bMustRefresh);
				if (bMustRefresh && hasSortColumn(tableColumn)) {
					// force immediate update to sort updates straight away
					cell.refresh(true, true, true);
				}
			}
		}, true );
		resetLastSortedOn();
		tableColumn.setLastSortValueChange(SystemTime.getCurrentTime());
	}

	// ITableStructureModificationListener
	// TableView
	@Override
	public void columnInvalidate(TableColumnCore tableColumn) {
		// We are being called from a plugin (probably), so we must refresh
		columnInvalidate(tableColumn, true);
	}

	// @see TableView#getPropertiesPrefix()
	@Override
	public String getPropertiesPrefix() {
		return propertiesPrefix;
	}


	// @see TableView#getTableID()
	@Override
	public String getTableID() {
		return tableID;
	}


	// @see TableView#getDataSourceType()
	@Override
	public Class<?> getDataSourceType() {
		return classPluginDataSourceType;
	}

	@Override
	public void tableStructureChanged(boolean columnAddedOrRemoved,
	                                  Class forPluginDataSourceType) {
		if (forPluginDataSourceType != null
				&& !forPluginDataSourceType.equals(getDataSourceType())) {
			return;
		}
		triggerLifeCycleListener(TableLifeCycleListener.EVENT_TABLELIFECYCLE_DESTROYED);

		DATASOURCETYPE[] unfilteredDS;
		synchronized (rows_sync) {
			unfilteredDS = (DATASOURCETYPE[]) listUnfilteredDataSources.keySet().toArray();
		}

		if (DEBUGADDREMOVE) {
			debug("TSC: #Unfiltered=" + unfilteredDS.length);
		}
		removeAllTableRows();
		processDataSourceQueueSync();

		boolean	orderChanged = false;
		
		if ( columnsOrdered.length > 1 && !columnAddedOrRemoved ){
			
			for ( int i=0;i<columnsOrdered.length-2;i++){
				if ( columnsOrdered[i].getPosition() > columnsOrdered[i+1].getPosition()){
					
					orderChanged = true;
					break;
				}
			}
		}
		if (columnAddedOrRemoved || orderChanged) {
			tableColumns = TableColumnManager.getInstance().getAllTableColumnCoreAsArray(
					getDataSourceType(), tableID);
			ArrayList<TableColumnCore> listVisibleColumns = new ArrayList<>();
			for (TableColumnCore column : tableColumns) {
				if (column.isVisible()) {
					listVisibleColumns.add(column);
				}
			}
			Collections.sort(listVisibleColumns, new Comparator<TableColumnCore>() {
				@Override
				public int compare(TableColumnCore o1, TableColumnCore o2) {
					if (o1 == o2) {
						return 0;
					}
					int diff = o1.getPosition() - o2.getPosition();
					return diff;
				}
			});
			columnsOrdered = listVisibleColumns.toArray(new TableColumnCore[0]);
		}

		//initializeTableColumns()

		refreshTable(false);
		triggerLifeCycleListener(TableLifeCycleListener.EVENT_TABLELIFECYCLE_INITIALIZED);

		// some implementers re-add datasource on Initialized trigger.
		// if they do, we don't have to re-add the unfiltlered (if we do, it
		// could case dups if the new datasources have different derps)
		if (listUnfilteredDataSources.size() == 0) {
			addDataSources(unfilteredDS);
		}
	}

	/* (non-Javadoc)
	 * @see TableView#getTableColumn(java.lang.String)
	 */
	@Override
	public com.biglybt.pif.ui.tables.TableColumn getTableColumn(
			String sColumnName) {
		for (int i = 0; i < tableColumns.length; i++) {
			TableColumnCore tc = tableColumns[i];
			if (tc.getName().equals(sColumnName)) {
				return tc;
			}
		}
		return null;
	}

	// @see TableView#getVisibleColumns()
	@Override
	public TableColumnCore[] getVisibleColumns() {
		return columnsOrdered;
	}

	@Override
	public TableColumnCore[] getAllColumns() {
		return tableColumns;
	}

	public void setColumnsOrdered(TableColumnCore[] columnsOrdered) {
		this.columnsOrdered = columnsOrdered;
	}

	@Override
	public boolean isColumnVisible(
			com.biglybt.pif.ui.tables.TableColumn column) {
		if (column == null) {
			return false;
		}
		return column.isVisible();
	}

	@Override
	public void refreshTable(boolean bForceSort) {
		triggerTableRefreshListeners();
	}

	/* various selected rows functions */
	/***********************************/

	public List<Object> getSelectedDataSourcesList() {
		synchronized ( rows_sync ) {
			if (listSelectedCoreDataSources != null) {
				return listSelectedCoreDataSources;
			}

			if (isDisposed() || selectedRows.size() == 0) {
				return Collections.emptyList();
			}

			final ArrayList<Object> l = new ArrayList<>(
					selectedRows.size());
			for (TableRowCore row : selectedRows) {
				if (row != null && !row.isRowDisposed()) {
					Object ds = row.getDataSource(true);
					if (ds != null) {
						l.add(ds);
					}
				}
			}

			listSelectedCoreDataSources = l;
			return l;
		}
	}

	/** Returns an array of all selected Data Sources.  Null data sources are
	 * ommitted.
	 *
	 * @return an array containing the selected data sources
	 *
	 * @TODO TuxPaper: Virtual row not created when using getSelection?
	 *                  computePossibleActions isn't being calculated right
	 *                  because of non-created rows when select user selects all
	 */
	public List<Object> getSelectedPluginDataSourcesList() {
		synchronized (rows_sync) {
  		if (isDisposed() || selectedRows.size() == 0) {
  			return Collections.emptyList();
  		}

  		final ArrayList<Object> l = new ArrayList<>(selectedRows.size());
  		for (TableRowCore row : selectedRows) {
  			if (row != null && !row.isRowDisposed()) {
  				Object ds = row.getDataSource(false);
  				if (ds != null) {
  					l.add(ds);
  				}
  			}
  		}
  		return l;
		}
	}

	/** Returns an array of all selected Data Sources.  Null data sources are
	 * ommitted.
	 *
	 * @return an array containing the selected data sources
	 *
	 **/
	// see common.TableView
	@Override
	public List<Object> getSelectedDataSources() {
		return new ArrayList<>(getSelectedDataSourcesList());
	}

	// see common.TableView
	@Override
	public Object[] getSelectedDataSources(boolean bCoreDataSource) {
		if (bCoreDataSource) {
			return getSelectedDataSourcesList().toArray();
		}
		return getSelectedPluginDataSourcesList().toArray();
	}

	/** @see TableView#getSelectedRows() */
	@Override
	public TableRowCore[] getSelectedRows() {
		synchronized (rows_sync) {
			return selectedRows.toArray(new TableRowCore[0]);
		}
	}

	// @see TableView#getSelectedRowsSize()
	@Override
	public int getSelectedRowsSize() {
		synchronized (rows_sync) {
			return selectedRows.size();
		}
	}

	/** Returns an list of all selected TableRowSWT objects.  Null data sources are
	 * ommitted.
	 *
	 * @return an list containing the selected TableRowSWT objects
	 */
	public List<TableRowCore> getSelectedRowsList() {
		synchronized (rows_sync) {
  		final ArrayList<TableRowCore> l = new ArrayList<>(
				  selectedRows.size());
  		for (TableRowCore row : selectedRows) {
  			if (row != null && !row.isRowDisposed()) {
  				l.add(row);
  			}
  		}

  		return l;
		}
	}

	@Override
	public boolean isSelected(TableRow row) {
		synchronized (rows_sync) {
			return selectedRows.contains(row);
		}
	}

	// @see TableView#getFocusedRow()
	@Override
	public TableRowCore getFocusedRow() {
		synchronized (rows_sync) {
			if (selectedRows.size() == 0) {
				return null;
			}
			return selectedRows.get(0);
		}
	}

	// @see TableView#getFirstSelectedDataSource()
	@Override
	public Object getFirstSelectedDataSource() {
		return getFirstSelectedDataSource(true);
	}

	/** Returns the first selected data sources.
	 *
	 * @return the first selected data source, or null if no data source is
	 *         selected
	 */
	public Object getFirstSelectedDataSource(boolean bCoreObject) {
		synchronized (rows_sync) {
			if (selectedRows.size() > 0) {
				return selectedRows.get(0).getDataSource(bCoreObject);
			}
		}
		return null;
	}


	/////


	/**
	 * Invalidate and refresh whole table
	 */
	public void tableInvalidate() {
		runForAllRows(new TableGroupRowVisibilityRunner() {
			@Override
			public void run(TableRowCore row, boolean bVisible) {
				row.invalidate();
				row.refresh(true, bVisible);
			}
		});
	}

	// @see TableView#getHeaderVisible()
	@Override
	public boolean getHeaderVisible() {
		return headerVisible;
	}

	// @see TableView#setHeaderVisible(boolean)
	@Override
	public void setHeaderVisible(boolean visible) {
		headerVisible  = visible;
	}

	@Override
	public boolean hasSortColumn(TableColumn column) {
		synchronized (rows_sync) {
			return sortColumns.contains(column);
		}
	}

	@Override
	public int getSortColumnCount() {
		synchronized (rows_sync) {
			return sortColumns.size();
		}
	}

	@Override
	public TableColumnCore[] getSortColumns() {
		synchronized (rows_sync) {
			return sortColumns.toArray(new TableColumnCore[0]);
		}
	}

	@Override
	public void addSortColumn(TableColumnCore column) {
		TableColumnCore[] sortColumns = getSortColumns();
		List<TableColumnCore> listNewColumns = new ArrayList<>();
		boolean alreadySortingByColumn = false;
		for (TableColumnCore existingSortColumn : sortColumns) {
			listNewColumns.add(existingSortColumn);
			if (existingSortColumn == column) {
				column.setSortAscending(!column.isSortAscending());
				alreadySortingByColumn = true;
			}
		}
		if (!alreadySortingByColumn) {
			listNewColumns.add(column);
		}
		setSortColumns(listNewColumns.toArray(new TableColumnCore[0]), false);
	}

	@Override
	public boolean setSortColumns(TableColumnCore[] newSortColumns, boolean allowOrderChange) {
		return( setSortColumns( newSortColumns, allowOrderChange, true ));
	}

	private boolean setSortColumns(TableColumnCore[] newSortColumns, boolean allowOrderChange, boolean doHistory ) {
		if (newSortColumns == null) {
			return false;
		}

			// did use sortColumn_mon

		synchronized (rows_sync) {
			TableColumnCore[] 	originalColumns 	= null;
			boolean[]			originalAscending	= null;
			
			if ( doHistory ){
				originalColumns 	= sortColumns.toArray( new TableColumnCore[sortColumns.size()]);
				originalAscending	= new boolean[originalColumns.length];

				for ( int i=0;i<originalColumns.length; i++){
					originalAscending[i] = originalColumns[i].isSortAscending();
				}
			}
			
			boolean columnsChanged = sortColumns.size() != newSortColumns.length;
			if (!columnsChanged) {
				for (int i = 0; i < sortColumns.size(); i++) {
					TableColumnCore s0 = sortColumns.get(i);
					TableColumnCore s1 = newSortColumns[i];
					if (!s0.equals(s1)) {
						columnsChanged = true;
						break;
					}
				}
			}

			String[] sortColumnNames = new String[newSortColumns.length];
			for (int i = 0; i < sortColumnNames.length; i++) {
				sortColumnNames[i] = newSortColumns[i].getName();
			}

			if (allowOrderChange) {
				if (columnsChanged) {
					sortColumns.clear();
					sortColumns.addAll(Arrays.asList(newSortColumns));

					int iSortDirection = configMan.getIntParameter(CFG_SORTDIRECTION);
					TableColumnCore sortColumn = sortColumns.get(0);
					if (iSortDirection == 0) {
						sortColumn.setSortAscending(true);
					} else if (iSortDirection == 1) {
						sortColumn.setSortAscending(false);
					} else if (iSortDirection == 2) {
						sortColumn.setSortAscending(!sortColumn.isSortAscending());
					}else{
						//same
					}

					TableColumnManager.getInstance().setSortColumnNames(tableID, sortColumnNames );
				} else {
					TableColumnCore sortColumn = sortColumns.get(0);
					sortColumn.setSortAscending(!sortColumn.isSortAscending());
				}
			} else {
				sortColumns.clear();
				sortColumns.addAll(Arrays.asList(newSortColumns));
				TableColumnManager.getInstance().setSortColumnNames(tableID, sortColumnNames );
			}
			if (columnsChanged) {
				for (TableRowCore row : sortedRows) {
					row.setSortColumn(sortColumnNames);
				}
			}
			if ( doHistory && originalColumns.length > 0 ){
				TableColumnCore[] 	newColumns 		= sortColumns.toArray( new TableColumnCore[sortColumns.size()]);
				boolean[]			newAscending	= new boolean[newColumns.length];

				for ( int i=0;i<newColumns.length; i++){
					newAscending[i] = newColumns[i].isSortAscending();
				}
				addHistory( new HistorySort( originalColumns, originalAscending, newColumns, newAscending ));
			}
			
 			uiChangeColumnIndicator();
 			resetLastSortedOn();
 			sortRows(columnsChanged,false);
			return columnsChanged;
		}
	}

	public void setRowSelected(final TableRowCore row, boolean selected, boolean trigger) {
		if (row == null || row.isRowDisposed()) {
			return;
		}
		if (isSingleSelection()) {
			setSelectedRows(new TableRowCore[] { row }, trigger);
		} else {
			boolean somethingChanged = false;
			ArrayList<TableRowCore> newSelectedRows;
			synchronized (rows_sync) {
				newSelectedRows = new ArrayList<>(selectedRows);
				if (selected) {
					if (!newSelectedRows.contains(row)) {
						newSelectedRows.add(row);
						somethingChanged = true;
					}
				} else {
					somethingChanged = newSelectedRows.remove(row);
				}
			}

			if (somethingChanged) {
				setSelectedRows(newSelectedRows.toArray(new TableRowCore[0]), trigger);
			}
		}
	}

	public void setSelectedRows(final TableRowCore[] newSelectionArray,
			final boolean trigger) {
		setSelectedRows( newSelectionArray, trigger, true);
	}
	
	private void setSelectedRows(final TableRowCore[] newSelectionArray,
			final boolean trigger, boolean updateHistory ) {
		if (isDisposed()) {
			return;
		}

		/**
		System.out.print(newSelectionArray.length + " Selected Rows: ");
		for (TableRowCore row : newSelectionArray) {
			System.out.print(indexOf(row));
			System.out.print(", ");
		}
		System.out.println(" via " + Debug.getCompressedStackTrace(4));
		/**/

		final List<TableRowCore> oldSelectionList = new ArrayList<>();

		List<TableRowCore> listNewlySelected;
		boolean somethingChanged;
		synchronized (rows_sync) {
			if (selectedRows.size() == 0 && newSelectionArray.length == 0) {
				return;
			}

			oldSelectionList.addAll(selectedRows);
			listSelectedCoreDataSources = null;
			selectedRows.clear();

			listNewlySelected = new ArrayList<>(1);

			// We'll remove items still selected from oldSelectionLeft, leaving
			// it with a list of items that need to fire the deselection event.
			for (TableRowCore row : newSelectionArray) {
				if (row == null || row.isRowDisposed()) {
					continue;
				}

				boolean existed = false;
				for (TableRowCore oldRow : oldSelectionList) {
					if (oldRow == row) {
						existed = true;
						if (!selectedRows.contains(row)) {
							selectedRows.add(row);
						}
						oldSelectionList.remove(row);
						break;
					}
				}
				if (!existed) {
					if (!selectedRows.contains(row)) {
						selectedRows.add(row);
					}
					if (!listNewlySelected.contains(row)) {
						listNewlySelected.add(row);
					}
				}
			}

			somethingChanged = listNewlySelected.size() > 0
					|| oldSelectionList.size() > 0;
					
			if ( somethingChanged && updateHistory ){
				
				addHistory( 
					new HistorySelection( 
						oldSelectionList.toArray( new TableRowCore[ oldSelectionList.size()]), 
						selectedRows.toArray( new TableRowCore[ selectedRows.size()])));
			}
			
			if (DEBUG_SELECTION) {
				System.out.println(somethingChanged + "] +"
						+ listNewlySelected.size() + "/-" + oldSelectionList.size()
						+ ";  UpdateSelectedRows via " + Debug.getCompressedStackTrace());
			}
		}

		if (somethingChanged) {
			TableRowCore[] selected 	= listNewlySelected.toArray(new TableRowCore[0]);
			TableRowCore[] deselected	= oldSelectionList.toArray(new TableRowCore[0]);
			
			uiSelectionChanged( selected, deselected );
			
			if ( trigger ){
				triggerSelectionChangeListeners( selected, deselected );
			}
		}

		if (trigger && somethingChanged) {
			if (listNewlySelected.size() > 0) {
				triggerSelectionListeners(listNewlySelected.toArray(new TableRowCore[0]));
			}
			if (oldSelectionList.size() > 0) {
				triggerDeselectionListeners(oldSelectionList.toArray(new TableRowCore[0]));
			}

			triggerTabViewsDataSourceChanged();
		}

	}
	
	protected void
	reaffirmSelection()
	{
			// we don't want an inactive table to grab back selection when something else changes
			// (e.g. a new row is added to it)
		
		if ( isTableFocused()){
			
			List<TableRowCore> oldSelectionList = new ArrayList<>();
	
			synchronized (rows_sync) {
				if (selectedRows.size() == 0 ){
					return;
				}
	
				oldSelectionList.addAll(selectedRows);
			}
			
			TableRowCore[] rows = oldSelectionList.toArray(new TableRowCore[0]);
			
			triggerSelectionChangeListeners( rows, rows );
			triggerSelectionListeners(rows);
		}
	}

	public abstract boolean isSingleSelection();

	public abstract void uiSelectionChanged(TableRowCore[] newlySelectedRows, TableRowCore[] deselectedRows);

	// @see TableView#setSelectedRows(TableRowCore[])
	@Override
	public void setSelectedRows(TableRowCore[] rows) {
		setSelectedRows(rows, true);
	}

	@Override
	public void selectAll() {
		setSelectedRows( getFilterSubRows()?getRowsAndSubRows( false ):getRows(), true);
	}

	public String getFilterText() {
		return filter == null ? "" : filter.text;
	}

	public boolean isMenuEnabled() {
		return menuEnabled;
	}

	public void setMenuEnabled(boolean menuEnabled) {
		this.menuEnabled = menuEnabled;
	}

	protected boolean isLastRow(TableRowCore row) {
		synchronized (rows_sync) {
			int size = sortedRows.size();
			return size == 0 ? false : sortedRows.get(size - 1) == row;
		}
	}

	public abstract void triggerTabViewsDataSourceChanged();

	protected abstract void uiChangeColumnIndicator();

	public boolean isProvideIndexesOnRemove() {
		return provideIndexesOnRemove;
	}

	public void setProvideIndexesOnRemove(boolean provideIndexesOnRemove) {
		this.provideIndexesOnRemove = provideIndexesOnRemove;
	}

	@Override
	public boolean isTableSelected() {
		return SelectedContentManager.getCurrentlySelectedTableView() == this;
	}
	
	protected abstract boolean
	isTableFocused();
	
	@Override
	public boolean 
	canMoveBack()
	{
		return( !historyBefore.isEmpty());
	}
	
	@Override
	public void 
	moveBack()
	{
		HistoryEntry history;
		
		synchronized( rows_sync ){
			
			if ( historyBefore.isEmpty()){
				
				return;
			}
			
			history = historyBefore.removeLast();
			
			historyAfter.addFirst( history );
		}
		
		history.applyPrev();
		
		if ( 	SelectedContentManager.getCurrentlySelectedTableView() == this &&
				( historyBefore.isEmpty() || historyAfter.size() == 1 )){
			
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			
		  	if ( uiFunctions != null ){
		  		
		  		uiFunctions.refreshIconBar();
		  	}
		}
	}
	
	@Override
	public boolean 
	canMoveForward()
	{
		return( !historyAfter.isEmpty());
	}
	
	@Override
	public void 
	moveForward()
	{
		HistoryEntry history;
		
		synchronized( rows_sync ){
			
			if ( historyAfter.isEmpty()){
				
				return;
			}
			
			history = historyAfter.removeFirst();
			
			historyBefore.addLast( history );
		}
		
		history.applyNext();
		
		if ( 	SelectedContentManager.getCurrentlySelectedTableView() == this &&
				( historyAfter.isEmpty() || historyBefore.size() == 1 )){
			
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			
		  	if ( uiFunctions != null ){
		  		
		  		uiFunctions.refreshIconBar();
		  	}
		}
	}
	
	private void
	addHistory(
		HistoryEntry		entry )
	{
		historyBefore.add( entry );
		
		if ( historyBefore.size() > 32 ){
			
			historyBefore.removeFirst();
		}
		
		boolean historyAfterWasEmpty = historyAfter.isEmpty();
		
		historyAfter.clear();
		
		if ( 	SelectedContentManager.getCurrentlySelectedTableView() == this &&
				( !historyAfterWasEmpty || historyBefore.size() == 1 )){
			
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			
		  	if ( uiFunctions != null ){
		  		
		  		uiFunctions.refreshIconBar();
		  	}
		}
	}
	
	private static interface
	HistoryEntry
	{
		void
		applyNext();
		
		void
		applyPrev();
	}
	
	private class
	HistorySelection
		implements HistoryEntry
	{
		private final TableRowCore[]	prevSel;
		private final TableRowCore[]	nextSel;
		
		HistorySelection(
			TableRowCore[]	_prev,
			TableRowCore[]	_next )
		{
			prevSel = _prev;
			nextSel	= _next;
		}
		
		public void
		applyNext()
		{
			apply( nextSel );
		}
		
		public void
		applyPrev()
		{
			apply( prevSel );
		}
		
		private void
		apply(
			TableRowCore[]	sel )
		{
			setSelectedRows( sel, true, false ) ;
			
			if ( sel.length > 0 ){
				
				TableRowCore first = sel[0];
				
				if ( !first.isVisible()){
				
					showRow( first );
				}
			}
		}
	}
	
	private class
	HistorySort
		implements HistoryEntry
	{
		private final TableColumnCore[]	prevCols;
		private final TableColumnCore[]	nextCols;
		
		private final boolean[]		prevAsc;
		private final boolean[]		nextAsc;
		
		HistorySort(
			TableColumnCore[]	pCols,
			boolean[]			pAsc,
			TableColumnCore[] 	nCols,
			boolean[]			nAsc )
		{
			prevCols		= pCols;
			prevAsc			= pAsc;
			nextCols		= nCols;
			nextAsc			= nAsc;
		}
		
		public void
		applyNext()
		{
			apply( nextCols, nextAsc );
		}
		
		public void
		applyPrev()
		{
			apply( prevCols, prevAsc );
		}
		
		private void
		apply(
			TableColumnCore[]	columns,
			boolean[]			asc )
		{
			for ( int i=0;i<columns.length;i++){
				columns[i].setSortAscending(asc[i]);
			}
			
			setSortColumns( columns, false, false );
		}
	}
}
