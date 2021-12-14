/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * Date: July 14, 2004
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

import java.io.UnsupportedEncodingException;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.sharing.ShareResource;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.ui.UIRuntimeException;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pifimpl.local.ui.tables.TableContextMenuItemImpl;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsManager.UIFCallback;
import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableStructureEventDispatcher;

/**
 * Table Column definition and modification routines.
 * Implements both the plugin API and the core API.
 * <P>
 * A column is defined in relation to a table.  When one column is in
 * multiple tables of different table ids, each table has it's own column
 * instance
 *
 * @author TuxPaper
 *
 * @see TableColumnManager
 */
public class TableColumnImpl
	implements TableColumnCore
{
	private static final String CFG_SORTDIRECTION 		= "config.style.table.defaultSortOrder";

	private static final String ATTRIBUTE_NAME_OVERIDE =  "tablecolumn.nameoverride";

	private static UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

	private static Comparator<String> intuitiveComparator = FormattersImpl.getAlphanumericComparator2( true );
	private static boolean	intiutiveSorting;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
			"Table.sort.intuitive",
			new ParameterListener(){
				
				@Override
				public void parameterChanged(String parameterName){
					intiutiveSorting = COConfigurationManager.getBooleanParameter( "Table.sort.intuitive"  );
				}
			});	
	}
	
	/** Internal Name/ID of the column **/
	private String sName;

	/** key of the displayed title for the column.  If null, uses default calc */
	private String sTitleLanguageKey = null;

	private int iAlignment;

	private int	iDefaultAlignment = -1;

	private int iType;

	private int iPosition;

	private int iWidth;

	private int iDefaultWidth;

	private int iInterval;

	private long lLastSortValueChange;

	private int[] foregroundColor;
	private int[] backgroundColor;
	
	/** Table the column belongs to */
	private String sTableID;

	private boolean bColumnAdded;

	private boolean bCoreDataSource;

	private TableCellInplaceEditorListener cellEditorListener;

	private ArrayList<TableCellRefreshListener> cellRefreshListeners;

	private ArrayList<TableCellAddedListener> cellAddedListeners;

	private ArrayList<TableCellDisposeListener> cellDisposeListeners;

	private ArrayList<TableCellToolTipListener> cellToolTipListeners;

	private ArrayList<TableCellMouseListener> cellMouseListeners;
	
	private ArrayList<TableCellMenuListener> cellMenuListeners;

	private ArrayList<TableCellMouseMoveListener> cellMouseMoveListeners;

	private ArrayList<TableCellVisibilityListener> cellVisibilityListeners;

	private ArrayList<TableCellClipboardListener> cellClipboardListeners;

	private ArrayList<TableColumnExtraInfoListener> columnExtraInfoListeners;

	private Map<String, List<Object>> mapOtherCellListeners;

	private int iConsecutiveErrCount;

	private ArrayList<TableContextMenuItem> menuItemsHeader;

	private ArrayList<TableContextMenuItem> menuItemsColumn;

	private boolean bObfuscateData;

	protected final AEMonitor this_mon = new AEMonitor("TableColumn");

	private boolean bSortValueLive;

	private long lStatsRefreshTotalTime;

	private long lStatsRefreshCount = 0;

	private long lStatsRefreshZeroCount = 0;

	private boolean bSortAscending;
	private boolean bDefaultSortAscending;

	private int iMinWidth = -1;

	private int iMaxWidth = -1;

	private boolean bVisible;

	private int iPreferredWidth;

	private boolean bPreferredWidthAuto = true;

	private int iPreferredWidthMax = -1;

	private boolean auto_tooltip = false;

	private Map userData;

	private boolean removed;

	private List<Class<?>> forPluginDataSourceTypes = new ArrayList<>();

	private String iconID;

	private boolean firstLoad;

	private boolean showOnlyImage;
	
	private boolean postLoadConfig;

	private boolean isDirty;

	public TableColumnImpl(String tableID, String columnID) {
		init(tableID, columnID);
	}


	private void init(String tableID,
			String columnID) {
		sTableID = tableID;
		sName = columnID;
		iType = TYPE_TEXT_ONLY;
		iWidth = 50;
		iAlignment = ALIGN_LEAD;
		bColumnAdded = false;
		bCoreDataSource = false;
		iInterval = INTERVAL_INVALID_ONLY;
		iConsecutiveErrCount = 0;
		lLastSortValueChange = 0;
		bVisible = false;
		iMinWidth = 16;
		iPosition = POSITION_INVISIBLE;
		int iSortDirection = COConfigurationManager.getIntParameter(CFG_SORTDIRECTION);
		bSortAscending = iSortDirection == 1 ? false : true;
	}

	@Override
	public void initialize(int iAlignment, int iPosition, int iWidth,
	                       int iInterval) {
		if (bColumnAdded) {
			throw (new UIRuntimeException("Can't set properties. Column '" + sName
					+ " already added"));
		}

		this.iAlignment = this.iDefaultAlignment =  iAlignment;
		setPosition(iPosition);
		this.iWidth = this.iDefaultWidth = iWidth;
		this.iMinWidth = 16;
		this.iInterval = iInterval;
	}

	@Override
	public void initialize(int iAlignment, int iPosition, int iWidth) {
		if (bColumnAdded) {
			throw (new UIRuntimeException("Can't set properties. Column '" + sName
					+ " already added"));
		}

		this.iAlignment = this.iDefaultAlignment = iAlignment;
		setPosition(iPosition);
		this.iWidth = this.iDefaultWidth = iWidth;
		this.iMinWidth = 16;
	}

	@Override
	public String getName() {
		return sName;
	}

	@Override
	public String getNameOverride()
	{
		return getUserDataString( ATTRIBUTE_NAME_OVERIDE );
	}

	@Override
	public void setNameOverride(String name )
	{
		setUserData( ATTRIBUTE_NAME_OVERIDE, name );
	}

	@Override
	public String getTableID() {
		return sTableID;
	}

	@Override
	public void setType(int type) {
		if (bColumnAdded) {
			throw (new UIRuntimeException("Can't set properties. Column '" + sName
					+ " already added"));
		}

		iType = type;
	}

	@Override
	public int getType() {
		return iType;
	}

	@Override
	public void setWidth(int realPXWidth) {
		setWidthPX(realPXWidth);
	}

	@Override
	public void setWidthPX(int width) {
		if (width == iWidth || width < 0) {
			return;
		}

		if (iMinWidth > 0 && width < iMinWidth) {
			return;
		}

		if (iMaxWidth > 0 && width > iMaxWidth) {
			if (width == iMaxWidth) {
				return;
			}
			width = iMaxWidth;
		}

		if (iMinWidth < 0) {
			iMinWidth = width;
		}

		//		if (iPreferredWidth <= 0) {
		//			iPreferredWidth = iWidth;
		//		}

		int diff = width - iWidth;
		iWidth = width;
		if (iDefaultWidth == 0) {
			iDefaultWidth = width;
		}
		
		if (diff != 0) {
			markDirty();
		}

		if (bColumnAdded && bVisible) {
			triggerColumnSizeChange(diff);
		}
	}

	@Override
	public void triggerColumnSizeChange(int diff) {
		TableStructureEventDispatcher tsed = TableStructureEventDispatcher.getInstance(sTableID);
		tsed.columnSizeChanged(this, diff);
		if (iType == TYPE_GRAPHIC) {
			invalidateCells();
		}
	}

	@Override
	public int getWidth() {
		return iWidth;
	}

	@Override
	public void setPosition(int position) {
		if (bColumnAdded) {
			throw (new UIRuntimeException("Can't set properties. Column '" + sName
					+ " already added"));
		}

		if (iPosition == POSITION_INVISIBLE && position != POSITION_INVISIBLE) {
			setVisible(true);
		}
		if (iPosition != position) {
			iPosition = position;
			markDirty();
		}
		if (position == POSITION_INVISIBLE) {
			setVisible(false);
		}
	}

	@Override
	public int getPosition() {
		return iPosition;
	}

	@Override
	public void setAlignment(int alignment) {
		/*
		if (bColumnAdded) {
			throw (new UIRuntimeException("Can't set properties. Column '" + sName
					+ " already added"));
		}
		*/

		int newAlignment;
		if (alignment == -1) {
			if (iDefaultAlignment != -1) {
				newAlignment = iDefaultAlignment;
			} else {
				return;
			}
		} else {
			newAlignment = alignment;

			if (iDefaultAlignment == -1) {
				iDefaultAlignment = alignment;
			}
		}
		
		if (newAlignment != iAlignment) {
			iAlignment = newAlignment;
			markDirty();
		}
	}

	@Override
	public int getAlignment() {
		return iAlignment;
	}

	@Override
	public int[] 
	getForegroundColor()
	{
		return( foregroundColor );
	}
	
	@Override
	public void
	setForegroundColor(
		int[]		rgb )
	{
		foregroundColor = rgb;
		
		markDirty();
	}
	
	@Override
	public int[] 
	getBackgroundColor()
	{
		return( backgroundColor );
	}
	
	@Override
	public void
	setBackgroundColor(
		int[]		rgb )
	{
		backgroundColor = rgb;
		
		markDirty();
	}
	
	@Override
	public void addCellRefreshListener(TableCellRefreshListener listener) {
		try {
			this_mon.enter();

			if (cellRefreshListeners == null) {
				cellRefreshListeners = new ArrayList<>(1);
			}

			cellRefreshListeners.add(listener);
			//System.out.println(this + " :: addCellRefreshListener " + listener + ". " + cellRefreshListeners.size());

		} finally {

			this_mon.exit();
		}
	}

	@Override
	public List<TableCellRefreshListener> getCellRefreshListeners() {
		try {
			this_mon.enter();

			if (cellRefreshListeners == null) {
				return (new ArrayList<>(0));
			}

			return (new ArrayList<>(cellRefreshListeners));

		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void removeCellRefreshListener(TableCellRefreshListener listener) {
		try {
			this_mon.enter();

			if (cellRefreshListeners == null) {
				return;
			}

			cellRefreshListeners.remove(listener);
		} finally {
			this_mon.exit();
		}
	}

	@Override
	public boolean hasCellRefreshListener() {
		return cellRefreshListeners != null && cellRefreshListeners.size() > 0;
	}

	@Override
	public void setRefreshInterval(int interval) {
		iInterval = interval;
	}

	@Override
	public int getRefreshInterval() {
		return iInterval;
	}

	@Override
	public void addCellAddedListener(TableCellAddedListener listener) {
		try {
			this_mon.enter();

			if (cellAddedListeners == null) {
				cellAddedListeners = new ArrayList<>(1);
			}

			cellAddedListeners.add(listener);

		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void addCellOtherListener(String listenerID, Object listener) {
		try {
			this_mon.enter();

			if (mapOtherCellListeners == null) {
				mapOtherCellListeners = new LightHashMap<>(1);
			}

			List<Object> list = mapOtherCellListeners.get(listenerID);
			if (list == null) {
				list = new ArrayList<>(1);
				mapOtherCellListeners.put(listenerID, list);
				list.add(listener);
			} else if (!list.contains(listener)) {
				list.add(listener);
			}

		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void removeCellOtherListener(String listenerID, Object l) {
		try {
			this_mon.enter();

  		if (mapOtherCellListeners == null) {
  			return;
  		}

  		mapOtherCellListeners.remove(listenerID);

		} finally {

			this_mon.exit();
		}

	}

	@Override
	public Object[] getCellOtherListeners(String listenerID) {
		if (mapOtherCellListeners == null) {
			return null;
		}

		List<Object> list = mapOtherCellListeners.get(listenerID);
		if (list == null) {
			return null;
		}
		return list.toArray();
	}

	// @see TableColumnCore#hasCellOtherListeners(java.lang.String)
	@Override
	public boolean hasCellOtherListeners(String listenerID) {
		return mapOtherCellListeners != null
				&& mapOtherCellListeners.get(listenerID) != null;
	}

	@Override
	public List getCellAddedListeners() {
		try {
			this_mon.enter();

			if (cellAddedListeners == null) {
				return Collections.emptyList();
			}

			return (new ArrayList(cellAddedListeners));

		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void removeCellAddedListener(TableCellAddedListener listener) {
		try {
			this_mon.enter();

			if (cellAddedListeners == null) {
				return;
			}

			cellAddedListeners.remove(listener);

		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void addCellDisposeListener(TableCellDisposeListener listener) {
		try {
			this_mon.enter();

			if (cellDisposeListeners == null) {
				cellDisposeListeners = new ArrayList<>(1);
			}

			cellDisposeListeners.add(listener);
		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void removeCellDisposeListener(TableCellDisposeListener listener) {
		try {
			this_mon.enter();

			if (cellDisposeListeners == null) {
				return;
			}

			cellDisposeListeners.remove(listener);
		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void addCellToolTipListener(TableCellToolTipListener listener) {
		try {
			this_mon.enter();

			if (cellToolTipListeners == null) {
				cellToolTipListeners = new ArrayList<>(1);
			}

			cellToolTipListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void removeCellToolTipListener(TableCellToolTipListener listener) {
		try {
			this_mon.enter();

			if (cellToolTipListeners == null) {
				return;
			}

			cellToolTipListeners.remove(listener);
		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void addCellMouseListener(TableCellMouseListener listener) {
		try {
			this_mon.enter();

			if (cellMouseListeners == null) {
				cellMouseListeners = new ArrayList<>(1);
			}

			cellMouseListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void removeCellMouseListener(TableCellMouseListener listener) {
		try {
			this_mon.enter();

			if (cellMouseListeners == null) {
				return;
			}

			cellMouseListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void addCellMenuListener(TableCellMenuListener listener) {
		try {
			this_mon.enter();

			if (cellMenuListeners == null) {
				cellMenuListeners = new ArrayList<>(1);
			}

			cellMenuListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void removeCellMenuListener(TableCellMenuListener listener) {
		try {
			this_mon.enter();

			if (cellMenuListeners == null) {
				return;
			}

			cellMenuListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}
	
	@Override
	public boolean hasCellMouseMoveListener() {
		return cellMouseMoveListeners != null && cellMouseMoveListeners.size() > 0;
	}

	public void addCellMouseMoveListener(TableCellMouseMoveListener listener) {
		try {
			this_mon.enter();

			if (cellMouseMoveListeners == null) {
				cellMouseMoveListeners = new ArrayList<>(1);
			}

			cellMouseMoveListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void removeCellMouseMoveListener(TableCellMouseMoveListener listener) {
		try {
			this_mon.enter();

			if (cellMouseMoveListeners == null) {
				return;
			}

			cellMouseMoveListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void addCellClipboardListener(TableCellClipboardListener listener) {
		try {
			this_mon.enter();

			if (cellClipboardListeners == null) {
				cellClipboardListeners = new ArrayList<>(1);
			}

			cellClipboardListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void removeCellClipboardListener(TableCellClipboardListener listener) {
		try {
			this_mon.enter();

			if (cellClipboardListeners == null) {
				return;
			}

			cellClipboardListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void addCellVisibilityListener(TableCellVisibilityListener listener) {
		try {
			this_mon.enter();

			if (cellVisibilityListeners == null) {
				cellVisibilityListeners = new ArrayList<>(1);
			}

			cellVisibilityListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void removeCellVisibilityListener(TableCellVisibilityListener listener) {
		try {
			this_mon.enter();

			if (cellVisibilityListeners == null) {
				return;
			}

			cellVisibilityListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public List<TableColumnExtraInfoListener> getColumnExtraInfoListeners() {
		try {
			this_mon.enter();

			if (columnExtraInfoListeners == null) {
				return (new ArrayList<>(0));
			}

			return (new ArrayList<>(columnExtraInfoListeners));

		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void addColumnExtraInfoListener(TableColumnExtraInfoListener listener) {
		try {
			this_mon.enter();

			if (columnExtraInfoListeners == null) {
				columnExtraInfoListeners = new ArrayList<>(1);
			}

			columnExtraInfoListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void removeColumnExtraInfoListener(TableColumnExtraInfoListener listener) {
		try {
			this_mon.enter();

			if (columnExtraInfoListeners == null) {
				return;
			}

			columnExtraInfoListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void invalidateCells() {
		TableStructureEventDispatcher tsed = TableStructureEventDispatcher.getInstance(sTableID);
		tsed.columnInvalidate(this);
	}

	@Override
	public void invalidateCell(Object data_source) {
		TableStructureEventDispatcher tsed = TableStructureEventDispatcher.getInstance(sTableID);
		tsed.cellInvalidate(this, data_source);
	}

	@Override
	public void addListeners(final Object listenerObject) {
		if (listenerObject instanceof TableCellDisposeListener) {
			addCellDisposeListener((TableCellDisposeListener) listenerObject);
		}

		if (listenerObject instanceof TableCellRefreshListener) {
			addCellRefreshListener((TableCellRefreshListener) listenerObject);
		}

		if (listenerObject instanceof TableCellToolTipListener) {
			addCellToolTipListener((TableCellToolTipListener) listenerObject);
		}

		if (listenerObject instanceof TableCellAddedListener) {
			addCellAddedListener((TableCellAddedListener) listenerObject);
		}

		if (listenerObject instanceof TableCellMouseMoveListener) {
			addCellMouseMoveListener((TableCellMouseMoveListener) listenerObject);
		}

		if (listenerObject instanceof TableCellMouseListener) {
			addCellMouseListener((TableCellMouseListener) listenerObject);
		}

		if (listenerObject instanceof TableCellMenuListener) {
			addCellMenuListener((TableCellMenuListener) listenerObject);
		}

		if (listenerObject instanceof TableCellVisibilityListener) {
			addCellVisibilityListener((TableCellVisibilityListener) listenerObject);
		}

		if (listenerObject instanceof TableColumnExtraInfoListener) {
			addColumnExtraInfoListener((TableColumnExtraInfoListener) listenerObject);
		}

		if (listenerObject instanceof TableCellClipboardListener) {
			addCellClipboardListener((TableCellClipboardListener) listenerObject);
		}

		UIFunctionsManager.execWithUIFunctions(new UIFCallback() {
			@Override
			public void run(UIFunctions functions) {
				functions.tableColumnAddedListeners(TableColumnImpl.this, listenerObject);
			}
		});
	}

	/* Start of not plugin public API functions */
	//////////////////////////////////////////////
	@Override
	public void setColumnAdded() {
		if (bColumnAdded) {
			return;
		}
		preAdd();
		bColumnAdded = true;
	}

	@Override
	public boolean getColumnAdded() {
		return bColumnAdded;
	}

	@Override
	public void setUseCoreDataSource(boolean bCoreDataSource) {
		this.bCoreDataSource = bCoreDataSource;
	}

	@Override
	public boolean getUseCoreDataSource() {
		return bCoreDataSource;
	}

	@Override
	public void invokeCellRefreshListeners(TableCell cell, boolean fastRefresh) throws Throwable {
		//System.out.println(this + " :: invokeCellRefreshListeners" + cellRefreshListeners);
		if (cellRefreshListeners == null || cell == null || cell.isDisposed()) {
			return;
		}

		Throwable firstError = null;

		//System.out.println(this + " :: invokeCellRefreshListeners" + cellRefreshListeners.size());
		for (int i = 0; i < cellRefreshListeners.size(); i++) {

			TableCellRefreshListener l = cellRefreshListeners.get(i);

			try {
				if(l instanceof TableCellLightRefreshListener)
					((TableCellLightRefreshListener)l).refresh(cell, fastRefresh);
				else
					l.refresh(cell);
			} catch (Throwable e) {

				if (firstError == null) {
					firstError = e;
				}
				Debug.printStackTrace(e);
			}
		}

		if (firstError != null) {
			throw firstError;
		}
	}

	@Override
	public void invokeCellAddedListeners(TableCell cell) {
		if (cellAddedListeners == null) {
			return;
		}
		for (int i = 0; i < cellAddedListeners.size(); i++) {

			try {
				((TableCellAddedListener) (cellAddedListeners.get(i))).cellAdded(cell);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void invokeCellDisposeListeners(TableCell cell) {
		if (cellDisposeListeners == null) {
			return;
		}
		for (int i = 0; i < cellDisposeListeners.size(); i++) {
			try {
				((TableCellDisposeListener) (cellDisposeListeners.get(i))).dispose(cell);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public String getClipboardText(TableCell cell) {
		if (cellClipboardListeners == null) {
			return null;
		}
		for (int i = 0; i < cellClipboardListeners.size(); i++) {
			try {
				String text = ((TableCellClipboardListener) (cellClipboardListeners.get(i))).getClipboardText(cell);

				if (text != null) {
					return text;
				}

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}

		return null;
	}

	@Override
	public void invokeCellToolTipListeners(TableCellCore cell, int type) {
		if (cellToolTipListeners == null) {
			return;
		}
		if (type == TableCellCore.TOOLTIPLISTENER_HOVER) {
			for (int i = 0; i < cellToolTipListeners.size(); i++) {
				try {
					((TableCellToolTipListener) (cellToolTipListeners.get(i))).cellHover(cell);
				} catch (Throwable e) {

					Debug.printStackTrace(e);
				}
			}
		} else {
			for (int i = 0; i < cellToolTipListeners.size(); i++) {

				try {
					((TableCellToolTipListener) (cellToolTipListeners.get(i))).cellHoverComplete(cell);
				} catch (Throwable e) {

					Debug.printStackTrace(e);
				}
			}
		}
	}

	@Override
	public void invokeCellMouseListeners(TableCellMouseEvent event) {
		ArrayList<?> listeners = event.eventType == TableCellMouseEvent.EVENT_MOUSEMOVE
				? cellMouseMoveListeners : this.cellMouseListeners;
		if (listeners == null) {
			return;
		}

		for (int i = 0; i < listeners.size(); i++) {
			try {
				TableCellMouseListener l = (TableCellMouseListener) (listeners.get(i));

				l.cellMouseTrigger(event);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void invokeCellMenuListeners(TableCellMenuEvent event) {
	
		ArrayList<TableCellMenuListener> listeners = cellMenuListeners;
		
		if ( listeners == null ){
			
			return;
		}
		
		for ( TableCellMenuListener listener: listeners ){
			
			if ( event.skipCoreFunctionality ){
				
				return;
			}
			
			try{
				listener.menuEventOccurred( event );

			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
	}
	
	@Override
	public void invokeCellVisibilityListeners(TableCellCore cell, int visibility) {
		if (cellVisibilityListeners == null) {
			return;
		}

		for (int i = 0; i < cellVisibilityListeners.size(); i++) {
			try {
				TableCellVisibilityListener l = (TableCellVisibilityListener) (cellVisibilityListeners.get(i));

				l.cellVisibilityChanged(cell, visibility);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void setPositionNoShift(int position) {
		//if (iPosition < 0 && position >= 0) {
		//	setVisible(true);
		//}
		if (iPosition != position) {
			iPosition = position;
			markDirty();
		}
		//if (position < 0) {
		//	setVisible(false);
		//}
	}

	@Override
	public Object getUserData(String key) {
		if(userData != null)
			return userData.get(key);
		return null;
	}

	@Override
	public String getUserDataString(String key) {
		if(userData != null) {
			Object o = userData.get(key);
			if (o instanceof String) {
				return (String) o;
			} else if (o instanceof byte[]) {
				try {
					String s = new String((byte[]) o, "utf-8");
					// write it back to the map, so we don't continually create new String objects
					userData.put(key, s);
					return( s );
				} catch (UnsupportedEncodingException e) {
				}
			}
		}
		return null;
	}

	@Override
	public void setUserData(String key, Object value) {
		if(userData == null)
			userData = new LightHashMap(2);
		userData.put(key, value);
		markDirty();
	}

	@Override
	public void removeUserData(String key) {
		if(userData == null)
			return;
		userData.remove(key);
		if(userData.size() < 1)
			userData = null;
		markDirty();
	}

	@Override
	public void
	remove()
	{
		removed = true;

		TableColumnManager.getInstance().removeColumns( new TableColumnCore[]{ this });

		TableStructureEventDispatcher tsed = TableStructureEventDispatcher.getInstance(sTableID);

		for (Class<?> cla : forPluginDataSourceTypes) {
			tsed.tableStructureChanged(true, cla);
		}
	}

	@Override
	public boolean
	isRemoved()
	{
		return( removed );
	}

	@Override
	public final void loadSettings(Map mapSettings) {
		postLoadConfig = false;
		// Format: Key = [TableID].column.[columnname]
		// Value[] = { visible, width, position, autotooltip, sortorder, userData, align }

		String itemPrefix = "Column." + sName;
		String oldItemPrefix = "Table." + sTableID + "." + sName;
		Object object = mapSettings.get(itemPrefix);
		Object[] list;
		if (object instanceof List) {
			list = ((List) object).toArray();
		} else {
			list = new String[0];
		}

		int pos = 0;
		if (list.length >= (pos + 1) && (list[pos] instanceof Number)) {
			boolean vis = ((Number) list[pos]).intValue() == 1;
			setVisible(vis);
		}

		pos++;
		if (list.length >= (pos + 1) && (list[pos] instanceof Number)) {
			int position = ((Number) list[pos]).intValue();
			setPositionNoShift(position);
		} else {
			int position = COConfigurationManager.getIntParameter(oldItemPrefix + ".position",
					iPosition);
			if (iPosition == POSITION_INVISIBLE && position != POSITION_INVISIBLE) {
				setVisible(true);
			}
			setPositionNoShift(position);
			if (position == POSITION_INVISIBLE) {
				setVisible(false);
			}
		}

		pos++;
		if (list.length >= (pos + 1) && (list[pos] instanceof Number)) {
			int width = ((Number) list[pos]).intValue();
			setWidthPX(width);
		} else {
			String key = oldItemPrefix + ".width";
			if (COConfigurationManager.hasParameter(key, true)) {
  			setWidth(COConfigurationManager.getIntParameter(key));
			}
		}

		pos++;
		if (list.length >= (pos + 1) && (list[pos] instanceof Number)) {
			boolean autoTooltip = ((Number) list[pos]).intValue() == 1;
			setAutoTooltip(autoTooltip);
		} else {
			setAutoTooltip(COConfigurationManager.getBooleanParameter(oldItemPrefix
					+ ".auto_tooltip", auto_tooltip));
		}

		pos++;
		if (list.length >= (pos + 1) && (list[pos] instanceof Number)) {
			int sortOrder = ((Number) list[pos]).intValue();
			if (sortOrder >= 0) {
				// dont call setSordOrder, since it will change lLastSortValueChange
				// which we shouldn't do if we aren't the sorting column
				bSortAscending = sortOrder == 1;
			}
		}else{
			bSortAscending = bDefaultSortAscending;
		}

		pos++;
		if (list.length >= (pos + 1) && (list[pos] instanceof Map)) {
			Map loadedUserData = (Map)list[pos];
			if (userData == null || userData.size() == 0) {
				userData = new LightHashMap(loadedUserData);
			} else {
				for (Object key : loadedUserData.keySet()) {
					userData.put(key, loadedUserData.get(key));
				}
			}
		}
		pos++;
		if (list.length >= (pos + 1) && (list[pos] instanceof Number)) {
			int align = ((Number) list[pos]).intValue();
			setAlignment( align );
		}
		pos++;
		if (list.length >= (pos + 1) && (list[pos] instanceof List)) {
			List<Number> fgList = (List<Number>)list[pos];
			if ( fgList.size() == 3 ){
				setForegroundColor( new int[]{ fgList.get(0).intValue(), fgList.get(1).intValue(), fgList.get(2).intValue()});
			}
		}
		pos++;
		if (list.length >= (pos + 1) && (list[pos] instanceof List)) {
			List<Number> bgList = (List<Number>)list[pos];
			if ( bgList.size() == 3 ){
				setBackgroundColor( new int[]{ bgList.get(0).intValue(), bgList.get(1).intValue(), bgList.get(2).intValue()});
			}
		}		
		
		firstLoad = list.length == 0;
		postConfigLoad();
	}

	public boolean isFirstLoad() {
		return firstLoad;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableColumn#postLoad()
	 */
	@Override
	public void postConfigLoad() {
		postLoadConfig = true;
	}

	public void preAdd() {}

	@Override
	public void preConfigSave() {}

	@Override
	public final void saveSettings(Map mapSettings) {
		preConfigSave();

		if (mapSettings == null) {
			mapSettings = TableColumnManager.getInstance().getTableConfigMap(sTableID);
			if (mapSettings == null) {
				return;
			}
		}
		List<Integer> fgList = new ArrayList<>();
		if ( foregroundColor != null ){
			for ( int i: foregroundColor ){
				fgList.add(i);
			}
		}
		List<Integer> bgList = new ArrayList<>();
		if ( backgroundColor != null ){
			for ( int i: backgroundColor ){
				bgList.add(i);
			}
		}
		String sItemPrefix = "Column." + sName;
		mapSettings.put(sItemPrefix, Arrays.asList(new Object[] {
			new Integer(bVisible ? 1 : 0),
			new Integer(iPosition),
			new Integer(iWidth),
			new Integer(auto_tooltip ? 1 : 0),
			new Integer(lLastSortValueChange == 0 ? -1 : (bSortAscending ? 1 : 0)),
			userData != null ? userData : Collections.EMPTY_MAP,
			new Integer(iAlignment == iDefaultAlignment ? -1 : iAlignment),
			fgList, bgList
		}));
		isDirty = false;
		// cleanup old config
		sItemPrefix = "Table." + sTableID + "." + sName;
		if (COConfigurationManager.hasParameter(sItemPrefix + ".width", true)) {
			COConfigurationManager.removeParameter(sItemPrefix + ".position");
			COConfigurationManager.removeParameter(sItemPrefix + ".width");
			COConfigurationManager.removeParameter(sItemPrefix + ".auto_tooltip");
		}
	}

	@Override
	public String getTitleLanguageKey() {
		return( getTitleLanguageKey( true ));
	}

	@Override
	public String getTitleLanguageKey(boolean with_renames ) {
		try {
			this_mon.enter();

			if ( with_renames ){
				String name_override = getNameOverride();

				if ( name_override != null ){

					return( "!" + name_override + "!" );
				}
			}

			if (sTitleLanguageKey == null) {
				if (MessageText.keyExists(sTableID + ".column." + sName)) {
					sTitleLanguageKey = sTableID + ".column." + sName;
					return sTitleLanguageKey;
				}

				String sKeyPrefix;
				// Support "Old Style" language keys, which have a prefix of TableID + "View."
				// Also, "MySeeders" is actually stored in "MyTorrents"..
				sKeyPrefix = (sTableID.equals(TableManager.TABLE_MYTORRENTS_COMPLETE)
						? TableManager.TABLE_MYTORRENTS_INCOMPLETE : sTableID)
						+ "View.";
				if (MessageText.keyExists(sKeyPrefix + sName)) {
					sTitleLanguageKey = sKeyPrefix + sName;
					return sTitleLanguageKey;
				}

				// The "all peers" view should just share the same peer columns, so reuse them.
				if (sTableID.equals(TableManager.TABLE_ALL_PEERS)) {
					sKeyPrefix = TableManager.TABLE_TORRENT_PEERS + ".column.";
					if (MessageText.keyExists(sKeyPrefix + sName)) {
						sTitleLanguageKey = sKeyPrefix + sName;
						return sTitleLanguageKey;
					}

					// Or try "PeersView".
					sKeyPrefix = "PeersView.";
					if (MessageText.keyExists(sKeyPrefix + sName)) {
						sTitleLanguageKey = sKeyPrefix + sName;
						return sTitleLanguageKey;
					}
				}
				
				if (sTableID.equals(TableManager.TABLE_ALL_PIECES)) {
					sKeyPrefix = TableManager.TABLE_TORRENT_PIECES + ".column.";
					if (MessageText.keyExists(sKeyPrefix + sName)) {
						sTitleLanguageKey = sKeyPrefix + sName;
						return sTitleLanguageKey;
					}

					sKeyPrefix = "PiecesView.";
					if (MessageText.keyExists(sKeyPrefix + sName)) {
						sTitleLanguageKey = sKeyPrefix + sName;
						return sTitleLanguageKey;
					}
				}

				// Try a generic one of "TableColumn." + columnid
				sKeyPrefix = "TableColumn.header.";
				if (MessageText.keyExists(sKeyPrefix + sName)) {
					sTitleLanguageKey = sKeyPrefix + sName;
					return sTitleLanguageKey;
				}

				sTitleLanguageKey = "!" + sName + "!";
			}
			return sTitleLanguageKey;
		} finally {

			this_mon.exit();
		}
	}

	@Override
	public int getConsecutiveErrCount() {
		return iConsecutiveErrCount;
	}

	@Override
	public void setConsecutiveErrCount(int iCount) {
		iConsecutiveErrCount = iCount;
	}

	@Override
	public void removeContextMenuItem(TableContextMenuItem menuItem) {
		if (menuItemsColumn != null) {
			menuItemsColumn.remove(menuItem);
		}
		if (menuItemsHeader != null) {
			menuItemsHeader.remove(menuItem);
		}
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#addContextMenuItem(java.lang.String, int)
	@Override
	public TableContextMenuItem addContextMenuItem(String key) {
		return addContextMenuItem(key, MENU_STYLE_COLUMN_DATA);
	}

	@Override
	public TableContextMenuItem addContextMenuItem(String key, int menuStyle) {
		ArrayList<TableContextMenuItem> menuItems;
		if (menuStyle == MENU_STYLE_COLUMN_DATA) {
			if (menuItemsColumn == null) {
				menuItemsColumn = new ArrayList<>();
			}
			menuItems = menuItemsColumn;
		} else {
			if (menuItemsHeader == null) {
				menuItemsHeader = new ArrayList<>();
			}
			menuItems = menuItemsHeader;
		}

		// Hack.. should be using our own implementation..
		TableContextMenuItemImpl item = new TableContextMenuItemImpl(null,"", key);
		menuItems.add(item);
		return item;
	}

	@Override
	public TableContextMenuItem[] getContextMenuItems(int menuStyle) {
		ArrayList<TableContextMenuItem> menuItems;
		if (menuStyle == MENU_STYLE_COLUMN_DATA) {
			menuItems = menuItemsColumn;
		} else {
			menuItems = menuItemsHeader;
		}

		if (menuItems == null) {
			return new TableContextMenuItem[0];
		}

		return menuItems.toArray(new TableContextMenuItem[0]);
	}

	@Override
	public boolean isObfuscated() {
		return bObfuscateData;
	}

	@Override
	public void setObfuscation(boolean hideData) {
		bObfuscateData = hideData;
	}

	@Override
	public long getLastSortValueChange() {
		if (bSortValueLive) {
			return SystemTime.getCurrentTime();
		}
		return lLastSortValueChange;
	}

	@Override
	public void setLastSortValueChange(long lastSortValueChange) {
		//System.out.println(getName() + "] setlastSortValueChange via " + Debug.getCompressedStackTrace());
		lLastSortValueChange = lastSortValueChange;
	}

	@Override
	public boolean isSortValueLive() {
		return bSortValueLive;
	}

	@Override
	public void setSortValueLive(boolean live) {
		//		if (live && !bSortValueLive) {
		//			System.out.println("Setting " + sTableID + ": " + sName + " to live sort value");
		//		}
		bSortValueLive = live;
	}

	@Override
	public void addRefreshTime(long ms) {
		if (ms == 0) {
			lStatsRefreshZeroCount++;
		} else {
			lStatsRefreshTotalTime += ms;
			lStatsRefreshCount++;
		}
	}

	@Override
	public void generateDiagnostics(IndentWriter writer) {
		writer.println("Column " + sTableID + ":" + sName
				+ (bSortValueLive ? " (Live Sort)" : ""));
		try {
			writer.indent();

			if (lStatsRefreshCount > 0) {
				writer.println("Avg refresh time (" + lStatsRefreshCount
						+ " samples): " + (lStatsRefreshTotalTime / lStatsRefreshCount)
						+ " (" + lStatsRefreshZeroCount
						+ " zero ms refreshes not included)");
			}
			writer.println("Listeners: refresh="
					+ getListCountString(cellRefreshListeners) + "; dispose="
					+ getListCountString(cellDisposeListeners) + "; mouse="
					+ getListCountString(cellMouseListeners) + "; mm="
					+ getListCountString(cellMouseMoveListeners) + "; vis="
					+ getListCountString(cellVisibilityListeners) + "; added="
					+ getListCountString(cellAddedListeners) + "; tooltip="
					+ getListCountString(cellToolTipListeners));

			writer.println("lLastSortValueChange=" + lLastSortValueChange);
		} catch (Exception e) {
		} finally {
			writer.exdent();
		}
	}

	private String getListCountString(List<?> l) {
		if (l == null) {
			return "-0";
		}
		return "" + l.size();
	}

	@Override
	public void setTableID(String tableID) {
		sTableID = tableID;
	}

	// @see java.util.Comparator#compare(T, T)

	/**
	 * {@inheritDoc}
	 * 
	 * @see com.biglybt.ui.common.table.TableView#sortRows(boolean)
	 * @see com.biglybt.ui.common.table.TableView#refreshTable(boolean true) 
	 */
	@Override
	public int compare(TableRowCore arg0, TableRowCore arg1) {
		TableCellCore cell0 = arg0.getTableCellCore(sName);
		TableCellCore cell1 = arg1.getTableCellCore(sName);

		Comparable c0 = (cell0 == null) ? "" : cell0.getSortValue();
		Comparable c1 = (cell1 == null) ? "" : cell1.getSortValue();

		// Put nulls and empty strings at bottom.
		boolean c0_is_null = (c0 == null || c0.equals(""));
		boolean c1_is_null = (c1 == null || c1.equals(""));
		if (c1_is_null)  {
			return (c0_is_null) ? 0 : -1;
		}
		else if (c0_is_null) {
			return 1;
		}

		try {
			boolean c0isString = c0 instanceof String;
			boolean c1isString = c1 instanceof String;
			if (c0isString && c1isString) {
				if ( intiutiveSorting ){		
					if (bSortAscending) {
						return ( intuitiveComparator.compare( (String)c0, (String)c1 ));
					}
	
					return ( intuitiveComparator.compare( (String)c1, (String)c0 ));
				}else{
					if (bSortAscending) {
						return ((String) c0).compareToIgnoreCase((String) c1);
					}
	
					return ((String) c1).compareToIgnoreCase((String) c0);
				}
			}

			int val;
			if (c0isString && !c1isString) {
				val = -1;
			} else if (c1isString && !c0isString) {
				val = 1;
			} else {
				val = c1.compareTo(c0);
			}
			return bSortAscending ? -val : val;
		} catch (ClassCastException e) {
			int c0_index = (cell0 == null) ? -999 : cell0.getTableRowCore().getIndex();
			int c1_index = (cell1 == null) ? -999 : cell1.getTableRowCore().getIndex();
			System.err.println("Can't compare " + c0.getClass().getName() + "("
					+ c0.toString() + ") from row #" + c0_index
					+ " to " + c1.getClass().getName() + "(" + c1.toString()
					+ ") from row #" + c1_index
					+ " while sorting column " + sName);
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * @param bAscending The bAscending to set.
	 */
	@Override
	public void setSortAscending(boolean bAscending) {
		if (this.bSortAscending == bAscending) {
			return;
		}
		setLastSortValueChange(SystemTime.getCurrentTime());

		this.bSortAscending = bAscending;
		markDirty();
	}

	/**
	 * @return Returns the bAscending.
	 */
	@Override
	public boolean isSortAscending() {
		return bSortAscending;
	}

	@Override
	public void setDefaultSortAscending(boolean bAscending ){
		bDefaultSortAscending = bAscending;
		if ( isFirstLoad()){
			bSortAscending = bAscending;
		}
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#getMinWidth()
	@Override
	public int getMinWidth() {
		if (iMinWidth < 0) {
			return iWidth;
		}

		return iMinWidth;
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#setMinWidth(int)
	@Override
	public void setMinWidth(int minwidth) {
		if (minwidth > iMaxWidth && iMaxWidth >= 0) {
			iMaxWidth = minwidth;
		}
		if (iPreferredWidth > 0 && iPreferredWidth < minwidth) {
			iPreferredWidth = minwidth;
		}
		iMinWidth = minwidth;
		if (iWidth < minwidth) {
			setWidthPX(minwidth);
		}
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#getMaxWidth()
	@Override
	public int getMaxWidth() {
		return iMaxWidth;
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#setMaxWidth(int)
	@Override
	public void setMaxWidth(int maxwidth) {
		if (maxwidth >= 0 && maxwidth < iMinWidth) {
			iMinWidth = maxwidth;
		}
		if (iPreferredWidth > maxwidth) {
			iPreferredWidth = maxwidth;
		}
		iMaxWidth = maxwidth;
		if (maxwidth >= 0 && iWidth > iMaxWidth) {
			setWidthPX(maxwidth);
		}
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#setWidthLimits(int, int)
	@Override
	public void setWidthLimits(int min, int max) {
		setMinWidth(min);
		setMaxWidth(max);
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#isVisible()
	@Override
	public boolean isVisible() {
		return bVisible;
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#setVisible(boolean)
	@Override
	public void setVisible(boolean visible) {
		if (bVisible == visible) {
			return;
		}

		//System.out.println("set " + sTableID + "/" + sName + " to " + visible
		//		+ " via " + Debug.getCompressedStackTrace());

		bVisible = visible;
		if (bVisible && iPosition == POSITION_INVISIBLE) {
			TableColumnCore[] allColumns = TableColumnManager.getInstance().getAllTableColumnCoreAsArray(
					null, sTableID);
			iPosition = 0;
			for (TableColumnCore tableColumnCore : allColumns) {
				if (tableColumnCore.getPosition() > iPosition) {
					iPosition = tableColumnCore.getPosition() + 1;
				}
			}
		}
		invalidateCells();
		markDirty();
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#getPreferredWidth()
	@Override
	public int getPreferredWidth() {
		return iPreferredWidth;
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#setPreferredWidthAuto(boolean)
	@Override
	public void setPreferredWidthAuto(boolean auto) {
		bPreferredWidthAuto = auto;
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#isPreferredWidthAuto()
	@Override
	public boolean isPreferredWidthAuto() {
		return bPreferredWidthAuto;
	}

    //  dead
	public void setPreferredWidthMax(int maxprefwidth) {
		iPreferredWidthMax = maxprefwidth;
		if (iPreferredWidth > iPreferredWidthMax) {
			setPreferredWidth(maxprefwidth);
		}
	}

    //  dead
	public int getPreferredWidthMax() {
		return iPreferredWidthMax;
	}

	// @see com.biglybt.pif.ui.tables.TableColumn#setPreferredWidth(int)
	@Override
	public void setPreferredWidth(int width) {
		if (iPreferredWidthMax > 0 && width > iPreferredWidthMax) {
			width = iPreferredWidthMax;
		}

		if (width < iMinWidth) {
			iPreferredWidth = iMinWidth;
		} else if (iMaxWidth > 0 && width > iMaxWidth) {
			iPreferredWidth = iMaxWidth;
		} else {
			iPreferredWidth = width;
		}
		if ( iPreferredWidth == 665 ) {
			System.out.println( "" );
		}
		// Commented out because size hasn't changed!
		//if (bColumnAdded && bVisible) {
		//	triggerColumnSizeChange();
		//}
	}

	@Override
	public void setAutoTooltip(boolean auto_tooltip) {
		if (this.auto_tooltip != auto_tooltip) {
			this.auto_tooltip = auto_tooltip;
			markDirty();
		}
	}

	@Override
	public boolean doesAutoTooltip() {
		return this.auto_tooltip;
	}

	@Override
	public void setInplaceEditorListener(TableCellInplaceEditorListener l) {
		cellEditorListener = l;
	}

	@Override
	public boolean hasInplaceEditorListener() {
		return cellEditorListener != null;
	}

	@Override
	public TableCellInplaceEditorListener getInplaceEditorListener() {
		return cellEditorListener;
	}

	public Class[] getForDataSourceTypes() {
		if (forPluginDataSourceTypes.isEmpty()) {
			// Guess forPluginDataSourceType based on tableID
			Class<?> forPluginDataSourceType = null;
			if (	TableManager.TABLE_MYTORRENTS_ALL_BIG.equals(sTableID) ||
					TableManager.TABLE_MYTORRENTS_ALL_SMALL.equals(sTableID) ||
					TableManager.TABLE_MYTORRENTS_UNOPENED.equals(sTableID) ||
					TableManager.TABLE_MYTORRENTS_UNOPENED_BIG.equals(sTableID)) {
				forPluginDataSourceType = Download.class;
			} else if (TableManager.TABLE_MYTORRENTS_INCOMPLETE_BIG.equals(sTableID)
					|| TableManager.TABLE_MYTORRENTS_INCOMPLETE.equals(sTableID)) {
				forPluginDataSourceType = DownloadTypeIncomplete.class;
			} else if (TableManager.TABLE_MYTORRENTS_COMPLETE.equals(sTableID)
					|| TableManager.TABLE_MYTORRENTS_COMPLETE_BIG.equals(sTableID)) {
				forPluginDataSourceType = DownloadTypeComplete.class;
			} else if (TableManager.TABLE_TORRENT_PEERS.equals(sTableID)) {
				forPluginDataSourceType = Peer.class;
			} else if (TableManager.TABLE_TORRENT_FILES.equals(sTableID)) {
				forPluginDataSourceType = DiskManagerFileInfo.class;
			} else if (TableManager.TABLE_MYTRACKER.equals(sTableID)) {
				forPluginDataSourceType = TrackerTorrent.class;
			} else if (TableManager.TABLE_MYSHARES.equals(sTableID)) {
				forPluginDataSourceType = ShareResource.class;
			}
			if (forPluginDataSourceType != null) {
				forPluginDataSourceTypes.add(forPluginDataSourceType);
			}
		}
		return forPluginDataSourceTypes.toArray(new Class[0]);
	}

	@Override
	public void reset() {
		if (iDefaultWidth != 0) {
			setWidthPX(iDefaultWidth);
		}
		if ( iDefaultAlignment != -1 ){
			setAlignment( iDefaultAlignment );
		}
		setNameOverride( null );
		if ( foregroundColor != null ){
			setForegroundColor( null );
		}
		if ( backgroundColor != null ){
			setBackgroundColor( null );
		}
	}

	/* (non-Javadoc)
	 * @see TableColumnCore#addDataSourceType(java.lang.Class)
	 */
	@Override
	public void addDataSourceType(Class<?> cla) {
		if (cla == null) {
			return;
		}
		if (cla == com.biglybt.core.disk.DiskManagerFileInfo.class) {
			cla = DiskManagerFileInfo.class;
		}
		forPluginDataSourceTypes.add(cla);
	}

	public void addDataSourceTypes(Class[] datasourceTypes) {
		if (datasourceTypes == null) {
			return;
		}
		for (Class cla : datasourceTypes) {
			addDataSourceType(cla);
		}
	}

	@Override
	public boolean handlesDataSourceType(Class<?> cla) {
		Class[] forPluginDataSourceTypes = getForDataSourceTypes();
		for (Class<?> forClass : forPluginDataSourceTypes) {
			if (forClass.isAssignableFrom(cla)) {
				return true;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableColumn#getForDataSourceType()
	 */
	@Override
	public Class getForDataSourceType() {
		Class[] forPluginDataSourceTypes = getForDataSourceTypes();
		return forPluginDataSourceTypes.length > 0 ? forPluginDataSourceTypes[0] : null;
	}

	@Override
	public void setIconReference(String iconID, boolean showOnlyIcon) {
		this.iconID = iconID;
		this.showOnlyImage = showOnlyIcon;
	}

	@Override
	public String getIconReference() {
		return iconID;
	}

	@Override
	public void setMinimumRequiredUserMode(int mode) {

		TableColumnInfo info = TableColumnManager.getInstance().getColumnInfo( this );

		if ( info != null ){
			byte	prof;
			if ( mode == Parameter.MODE_BEGINNER ){
				prof = TableColumnInfo.PROFICIENCY_BEGINNER;
			}else if ( mode == Parameter.MODE_INTERMEDIATE ){
				prof = TableColumnInfo.PROFICIENCY_INTERMEDIATE;
			}else{
				prof = TableColumnInfo.PROFICIENCY_ADVANCED;
			}
			info.setProficiency( prof );
		}
	}


	@Override
	public boolean showOnlyImage() {
		return showOnlyImage;
	}

	private void markDirty() {
		if (!postLoadConfig) {
			return;
		}
		isDirty = true;
	}

	@Override
	public boolean isDirty() {
		return isDirty;
	}
}
