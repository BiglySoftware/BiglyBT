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

package com.biglybt.ui.swt.views.table.painted;

import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.internat.MessageText.MessageTextListener;
import com.biglybt.core.util.*;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.table.impl.TableViewImpl;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentListener;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.TextWithHistory;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.components.BubbleTextBox.BubbleTextBoxChangeListener;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.HSLColor;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.utils.*;
import com.biglybt.ui.swt.views.table.*;
import com.biglybt.ui.swt.views.table.impl.TableTooltips;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_Common;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.tables.TableRowMouseEvent;
import com.biglybt.pif.ui.tables.TableRowMouseListener;
import com.biglybt.pifimpl.local.ui.tables.TableContextMenuItemImpl;

/**
 * A TableView implemented by painting on a canvas
 *
 * TODO:
 * Keyboard Selection
 * Cursor
 * Column move and resize past bounds
 */
public class TableViewPainted
	extends TableViewImpl<Object>
	implements ParameterListener, TableViewSWT<Object>, ObfuscateImage,
	MessageTextListener, BubbleTextBoxChangeListener
{
	private static final boolean hasGetScrollBarMode = SWT.getVersion() >= 3821;

	private static final boolean DEBUG_ROWCHANGE = false;

	private static final boolean DEBUG_WITH_SHELL = false;

	public static final boolean DIRECT_DRAW = (Constants.isOSX
			|| Constants.isUnix) && Utils.getDeviceZoom() != 100;

	private static final boolean DEBUG_REDRAW_CLIP = false;

	private static final boolean expand_enabled_default = COConfigurationManager.getBooleanParameter("Table.useTree");

	public static final String MENUKEY_IN_BLANK_AREA = "inBlankArea";

	public static final String MENUKEY_IS_HEADER = "isHeader";

	public static final String MENUKEY_COLUMN = "column";

	private Composite cTable;

	private int loopFactor;

	/** How often graphic cells get updated
	 */
	protected int graphicsUpdate = configMan.getIntParameter("Graphics Update");

	protected int reOrderDelay = configMan.getIntParameter("ReOrder Delay");

	protected boolean extendedErase = configMan.getBooleanParameter("Table.extendedErase");

	private int defaultRowHeight = 17;

	private int lineHeight;

	protected float iHeightEM = -1;

	/**
	 * Rows visible to user.  We assume this list is always up to date
	 */
	LinkedHashSet<TableRowPainted> visibleRows = new LinkedHashSet<>();

	//Object visibleRows_sync = new Object();	// got a deadlock between this and lock when separate so consolidated
	private final Object visibleRows_sync;

	/**
	 * Up to date table client area.  So far, the best places to refresh
	 * this variable are in the PaintItem event and the scrollbar's events.
	 * Typically table.getClientArea() is time consuming
	 */
	protected Rectangle clientArea;

	private boolean isVisible;

	private Shell shell;

	private Color colorLine;

	private Image canvasImage;

	private final String sDefaultSortOn;

	private TableViewSWT_Common tvSWTCommon;

	private TableViewSWT_TabsCommon tvTabsCommon;

	private TableViewSWTPanelCreator mainPanelCreator;

	private boolean isMultiSelect;

	private int columnsWidth;

	private Menu menu;

	private TableRowPainted focusedRow;

	private boolean 	enableTabViews = true;
	private boolean 	tabViewsExpandedByDefault = true;

	protected boolean isDragging;

	private Composite mainComposite;

	private Object heightChangeSync = new Object();
	private int totalHeight = 0;

	private boolean redrawTableScheduled;
	private int		redrawTableDisabled;
	
	private ScrollBar hBar;

	private ScrollBar vBar;

	private Canvas sCanvasImage;

	private boolean	filterSubRows;
	
	private boolean expandEnabled = expand_enabled_default;
	
	private AtomicInteger	mutationCount 	= new AtomicInteger(0);
	private volatile int	lastMC			= -1;

	private TableHeaderPainted header;

	private DragSource dragSource;
	private DropTarget dropTarget;
	private boolean destroying;
	private int rowMinHeight;

	private class
	RefreshTableRunnable
		extends AERunnable
	{
		private AtomicBoolean	forceSortPending = new AtomicBoolean();

		@Override
		public void runSupport() {
			__refreshTable(forceSortPending.getAndSet( false ));
		}
		public void setForceSort(boolean fs){
			if ( fs ){
				forceSortPending.set( true);
			}
		}
	}

	private RefreshTableRunnable refreshTableRunnable = new RefreshTableRunnable();

	private FrequencyLimitedDispatcher	refresh_dispatcher =
			new FrequencyLimitedDispatcher( refreshTableRunnable, 250 );

	{
		refresh_dispatcher.setSingleThreaded();
	}

	private class
	RedrawTableRunnable
		extends AERunnable
	{
		private SWTRunnable target =
			new SWTRunnable() {
				@Override
				public void runNoDisplay() {
					synchronized (TableViewPainted.this) {
						redrawTableScheduled = false;
					}
				}

				@Override
				public void runWithDisplay(Display display) {
					synchronized (TableViewPainted.this) {
						redrawTableScheduled = false;
					}

					visibleRowsChanged();

					if (DIRECT_DRAW) {
						if (cTable != null && !cTable.isDisposed()) {
							cTable.redraw();
						}
					} else {
						if (canvasImage != null && !canvasImage.isDisposed()) {
							canvasImage.dispose();
							canvasImage = null;
						}
						swt_calculateClientArea();
					}
				}
			};

		@Override
		public void runSupport() {

			Utils.execSWTThread( target );
		}
	}

	private FrequencyLimitedDispatcher	redraw_dispatcher =
			new FrequencyLimitedDispatcher( new RedrawTableRunnable(), 250 );

	{
		redraw_dispatcher.setSingleThreaded();
	}


	protected boolean isFocused;

	/**
	 * Main Initializer
	 * @param _sTableID Which table to handle (see
	 *                   {@link com.biglybt.pif.ui.tables.TableManager}).
	 *                   Config settings are stored with the prefix of
	 *                   "Table.<i>TableID</i>"
	 * @param _sTextPrefixID Prefix for retrieving text from the properties
	 *                            file (MessageText).  Typically
	 *                            <i>TableID</i> + "View"
	 * @param _basicItems Column Definitions
	 * @param _sDefaultSortOn Column name to sort on if user hasn't chosen one yet
	 * @param _iTableStyle SWT style constants used when creating the table
	 */
	public TableViewPainted(Class<?> pluginDataSourceType, String _sTableID,
			String _sTextPrefixID, TableColumnCore[] _basicItems,
			String _sDefaultSortOn, int _iTableStyle) {
		super(pluginDataSourceType, _sTableID, _sTextPrefixID, new Object(),
				_basicItems);
		visibleRows_sync = getRowsSync();
		//		boolean wantTree = (_iTableStyle & SWT.CASCADE) != 0;
		//		_iTableStyle &= ~SWT.CASCADE;
		//		if (wantTree) {
		//			useTree = COConfigurationManager.getBooleanParameter("Table.useTree")
		//		}
		//		basicItems = _basicItems;
		//		sDefaultSortOn = _sDefaultSortOn;
		//		iTableStyle = _iTableStyle | SWT.V_SCROLL | SWT.DOUBLE_BUFFERED;
		this.sDefaultSortOn = _sDefaultSortOn;
		this.isMultiSelect = (_iTableStyle & SWT.MULTI) != 0;

		// Deselect rows if user clicks on a blank spot (a spot with no row)
		tvSWTCommon = new TableViewSWT_Common(this) {
			@Override
			public void widgetSelected(SelectionEvent event) {
				//updateSelectedRows(table.getSelection(), true);
			}

			@Override
			public void mouseUp(TableRowCore clickedRow, TableCellCore cell, int button,
                                int stateMask) {
				super.mouseUp(clickedRow, cell, button, stateMask);

				if (clickedRow == null) {
					return;
				}
				if (button == 1) {
  				int keyboardModifier = (stateMask & SWT.MODIFIER_MASK);
  				if ((keyboardModifier & SWT.SHIFT) != 0) {
  					// select from focus to row
  					selectRowsTo(clickedRow);
  					return;
  				} else if (keyboardModifier == 0) {
  					setSelectedRows(new TableRowCore[] {
  						clickedRow
  					});
  					return;
  				}
				}
			}

			@Override
			public void mouseDown(TableRowSWT clickedRow, TableCellCore cell, int button,
					int stateMask) {
				if (clickedRow == null) {
					return;
				}
				int keyboardModifier = (stateMask & SWT.MODIFIER_MASK);
				if (button == 1) {
  				if ((keyboardModifier & SWT.MOD1) != 0) {
  					// control (win), alt (mac)
  					setRowSelected(clickedRow, !clickedRow.isSelected(), true);
  					return;
  				}
				} else if (button == 3) {
					if (!isSelected(clickedRow) && keyboardModifier == 0) {
						setSelectedRows(new TableRowCore[] {
							clickedRow
						});
					}
				}
				if (getSelectedRowsSize() == 0) {
					setSelectedRows(new TableRowCore[] {
						clickedRow
					});
				}
			}

			@Override
			public void keyPressed(KeyEvent event) {
				if ( event.keyCode == SWT.ESC ){
					TableViewSWTFilter<?> filter = getSWTFilter();
					if ( filter != null && filter.filterBox != null ){
						filter.filterBox.setText( "" );
					}
				}
				if ( getComposite().isDisposed()){
					return;
				}
				if (getComposite() != event.widget) {
					if ( getComposite().isVisible()){
							// we only want super to get the opportunity to consume the event if it is visible
							// the way search works we come through here multiple times for various table views (up to three
							// if simple/detailed library views have been visited) and we don't want an invisible one
							// to mark the event as 'doit=false' and prevent a visible one from processing it...
						super.keyPressed(event);
					}
					return;
				}
				boolean updateTable = false;
				if (event.keyCode == SWT.ARROW_UP) {
					TableRowCore rowToSelect = getPreviousRow(focusedRow);
					if ((event.stateMask & SWT.SHIFT) != 0) {
						if (rowToSelect != null && focusedRow != null) {
							TableRowCore[] selectedRows = getSelectedRows();
							sortRowsByVisibilityIndex( selectedRows );
							boolean select = selectedRows.length == 0
									|| selectedRows[0] == focusedRow;
//							System.out.println("i=" + selectedRows[0].getIndex() + ";"
//									+ select + ";" + focusedRow.getIndex());
							if (select) {
								rowToSelect.setSelected(select);
							} else {
								TableRowPainted rowToUnSelect = focusedRow;
								setFocusedRow(rowToSelect);
								rowToUnSelect.setSelected(false);
							}
							updateTable = true;
						}
					} else if ((event.stateMask & SWT.CONTROL) != 0) {
						// show one more topRow
						TableRowPainted firstRow = visibleRows.iterator().next();
						if (firstRow != null) {
							int hChange = 0;
							if (isRowPartiallyVisible(firstRow)) {
								hChange =  firstRow.getDrawOffset().y  - clientArea.y;
							} else {
  							TableRowCore prevRow = getPreviousRow(firstRow);
  							if (prevRow != firstRow && prevRow != null) {
  								hChange = -prevRow.getHeight();
  							}
							}
							vBar.setSelection(vBar.getSelection() + hChange);
							swt_vBarChanged();
						}
					} else {
						setSelectedRows(new TableRowCore[] {
							rowToSelect
						});
						updateTable = true;
					}
				} else if (event.keyCode == SWT.PAGE_UP) {
					TableRowCore row = focusedRow;
					TableRowPainted lastRow = getLastVisibleRow();
					int y = lastRow == null ? 0 : (clientArea.y + clientArea.height) - lastRow.getDrawOffset().y;
					while (row != null && y < clientArea.height) {
						y += row.getHeight();
						row = getPreviousRow(row);
					}
					if (row == null) {
						row = getRow(0);
					}
					if ((event.stateMask & SWT.SHIFT) != 0) {
						if ( row != null ){
							selectRowsTo(row);
						}
					} else if (event.stateMask == 0) {
  					setSelectedRows(new TableRowCore[] {
  						row
  					});
					}
					updateTable = true;
				} else if (event.keyCode == SWT.HOME) {
					if ((event.stateMask & SWT.SHIFT) != 0) {
						selectRowsTo(getRow(0));
					} else if (event.stateMask == 0) {
  					setSelectedRows(new TableRowCore[] {
  						getRow(0)
  					});
					}
					updateTable = true;
				} else if (event.keyCode == SWT.ARROW_DOWN) {
					if ((event.stateMask & SWT.CONTROL) != 0) {
						// show one less topRow
						TableRowPainted firstRow = visibleRows.iterator().next();
						if (firstRow != null) {
							int hChange = 0;
							if (isRowPartiallyVisible(firstRow)) {
								hChange = firstRow.getHeight() + (firstRow.getDrawOffset().y - clientArea.y);
							} else {
								hChange = firstRow.getHeight();
							}
							vBar.setSelection(vBar.getSelection() + hChange);
							swt_vBarChanged();
						}
					} else {
						TableRowCore rowToSelect = getNextRow(focusedRow);
						if (rowToSelect != null) {
							if ((event.stateMask & SWT.SHIFT) != 0) {
								TableRowCore[] selectedRows = getSelectedRows();
								sortRowsByVisibilityIndex( selectedRows );
								boolean select = selectedRows.length == 0
										|| selectedRows[selectedRows.length - 1] == focusedRow;
								if (select) {
									rowToSelect.setSelected(select);
								} else {
									TableRowPainted rowToUnSelect = focusedRow;
									setFocusedRow(rowToSelect);
									rowToUnSelect.setSelected(false);
								}
							} else {
								setSelectedRows(new TableRowCore[] {
										rowToSelect
								});
							}
							updateTable = true;
						}
					}
				} else if (event.keyCode == SWT.PAGE_DOWN) {
					TableRowCore row = focusedRow;
					TableRowPainted firstRow = visibleRows.size() == 0 ? null : visibleRows.iterator().next();

					int y = firstRow == null ? 0 : firstRow.getHeight() - (clientArea.y - firstRow.getDrawOffset().y);
					while (row != null && y < clientArea.height) {
						y += row.getHeight();
						TableRowCore nextRow = getNextRow(row);
						if (nextRow == null) {
							break;
						}
						row = nextRow;
					}
					if ((event.stateMask & SWT.SHIFT) != 0) {
						selectRowsTo(row);
					} else if (event.stateMask == 0) {
  					setSelectedRows(new TableRowCore[] {
  						row
  					});
					}
					updateTable = true;
				} else if (event.keyCode == SWT.END) {
					//TableRowCore lastRow = getRow(getRowCount() - 1);
					TableRowCore[] rows = getRowsAndSubRows( false );
					if ( rows.length > 0 ){
						TableRowCore lastRow = rows[rows.length-1];
						if ((event.stateMask & SWT.SHIFT) != 0) {
							selectRowsTo(lastRow);
						} else if (event.stateMask == 0) {
	  					setSelectedRows(new TableRowCore[] {
	  						lastRow
	  					});
						}
						updateTable = true;
					}
				} else if (event.keyCode == SWT.ARROW_RIGHT && event.stateMask == 0) {
					if (event.stateMask == 0 && focusedRow != null && !focusedRow.isExpanded() && canHaveSubItems()) {
						focusedRow.setExpanded(true);
					} else {
						if (hBar.isEnabled()) {
							hBar.setSelection(hBar.getSelection() + 50);
							cTable.redraw();
							updateTable = true;
						}
					}
				} else if (event.keyCode == SWT.ARROW_LEFT && event.stateMask == 0) {
					if (event.stateMask == 0 && focusedRow != null && focusedRow.isExpanded() && canHaveSubItems()) {
						focusedRow.setExpanded(false);
					} else {
						if (hBar.isEnabled()) {
							hBar.setSelection(hBar.getSelection() - 50);
							cTable.redraw();
							updateTable = true;
						}
					}
				}

				if (updateTable) {
					cTable.update();
				}
				super.keyPressed(event);
			}

			@Override
			public void keyReleased(KeyEvent e) {
				swt_calculateClientArea();
				visibleRowsChanged();

				super.keyReleased(e);
			}
		};
	}

	private void
	sortRowsByVisibilityIndex(
		TableRowCore[] 	selectedRows )
	{
		Arrays.sort(
			selectedRows,
			new Comparator<TableRowCore>(){
				@Override
				public int compare(TableRowCore o1, TableRowCore o2){
					return( o1.getVisibleRowIndex() - o2.getVisibleRowIndex());
				}
			});
	}
	
	protected boolean isRowPartiallyVisible(TableRowPainted row) {
		if (row == null) {
			return false;
		}
		Point drawOffset = row.getDrawOffset();
		int height = row.getHeight();
		return (drawOffset.y < clientArea.y && drawOffset.y + height > clientArea.y)
				|| (drawOffset.y < clientArea.y + clientArea.height && drawOffset.y
						+ height > clientArea.y + clientArea.height);
	}

	protected void selectRowsTo(TableRowCore clickedRow) {
		if (!isMultiSelect) {
			setSelectedRows(new TableRowCore[] {
				clickedRow
			});
			return;
		}
		TableRowCore[] selectedRows = getSelectedRows();
		TableRowCore firstRow = selectedRows.length > 0 ? selectedRows[0]: getRow(0);
		
		ArrayList<TableRowCore> rowsToSelect;
		
		if ( getFilterSubRows()){
			
			TableRowCore[] rows = getRowsAndSubRows( false );
			
			int startPos 	= -1;
			int endPos		= -1;
			
			for ( int i=0;i<rows.length;i++){
				TableRowCore row = rows[i];
				if ( row == firstRow ){
					startPos = i;
				}
				if ( row == clickedRow ){
					endPos = i;
				}
			}
			
			if ( startPos == -1 || endPos == -1 ){
				return;
			}
			
			boolean reverse = endPos < startPos;
			
			if ( reverse ){
				int temp = endPos;
				endPos = startPos;
				startPos = temp;
			}

			rowsToSelect = new ArrayList<>( endPos - startPos + 1);
			for ( int i=startPos;i<=endPos;i++){
				rowsToSelect.add( rows[i] );
			}
			
			if ( reverse ){
				Collections.reverse( rowsToSelect );
			}
		}else{
				// broken for full table support
			
			TableRowCore parentFirstRow = firstRow;
			while (parentFirstRow.getParentRowCore() != null) {
				parentFirstRow = parentFirstRow.getParentRowCore();
			}
			TableRowCore parentClickedRow = clickedRow;
			while (parentClickedRow.getParentRowCore() != null) {
				parentClickedRow = parentClickedRow.getParentRowCore();
			}
			int startPos;
			int endPos;
			if (parentFirstRow == parentClickedRow) {
				startPos = parentFirstRow == firstRow ? -1 : firstRow.getIndex();
				endPos = parentClickedRow == clickedRow ? -1 : clickedRow.getIndex();
			} else {
				startPos = indexOf(parentFirstRow);
				endPos = indexOf(parentClickedRow);
				if (endPos == -1 || startPos == -1) {
					return;
				}
			}
			rowsToSelect = new ArrayList<>(Arrays.asList(selectedRows));
			TableRowCore curRow = firstRow;
			
			int maxToDo = getRowAndSubRowCount()[1];
			
			do {
				if (!rowsToSelect.contains(curRow)) {
					rowsToSelect.add(curRow);
				}
				TableRowCore newRow = (startPos < endPos) ? getNextRow(curRow) : getPreviousRow(curRow);
	
					// prevent infinite loop if things go wonky (which they have been seen to do!)
				if ( newRow == curRow ){
					break;
				}else{
					curRow = newRow;
				}
	
			} while (curRow != clickedRow && curRow != null && maxToDo-- > 0 );
			if (curRow != null && !rowsToSelect.contains(curRow)) {
				rowsToSelect.add(curRow);
			}
		}
		
		setSelectedRows(rowsToSelect.toArray(new TableRowCore[0]));
		setFocusedRow(clickedRow);
	}

	protected TableRowCore getPreviousRow(TableRowCore relativeToRow) {
		
		if (relativeToRow == null) {
			return( getRow(0));
		}
		
		if ( getFilterSubRows()){
			
				// inefficient...
			
			TableRowCore[] rows = getRowsAndSubRows( false );
						
			for ( int i=rows.length-1;i>0;i--){
			
				if ( rows[i] == relativeToRow ){
					
					return( rows[i-1] );
				}
			}
			
			return( getRow(0) );
			
		}else{
				// existing logic below broken for 'full table' - can't be bothered to fix it
			TableRowCore rowToSelect = null;
			
			TableRowCore parentRow = relativeToRow.getParentRowCore();
			if (parentRow == null) {
				TableRowCore row = getRow(indexOf(relativeToRow) - 1);
				if (row != null && row.isExpanded() && row.getSubItemCount() > 0) {
					rowToSelect = row.getSubRow(row.getSubItemCount() - 1);
				} else {
					rowToSelect = row;
				}
			} else {
				int index = relativeToRow.getIndex();
				if (index > 0) {
					rowToSelect = parentRow.getSubRow(index - 1);
				} else {
					rowToSelect = parentRow;
				}
			}
	
			if  ( rowToSelect == null ){
				return( getRow(0));
			}
			return rowToSelect;
		}
	}

	protected TableRowCore getNextRow(TableRowCore relativeToRow) {
		if (relativeToRow == null) {
			return( getRow(0));
		}

		if ( getFilterSubRows()){
			
				// inefficient...
			
			TableRowCore[] rows = getRowsAndSubRows( false );
						
			for ( int i=0;i<rows.length-1;i++){
			
				if ( rows[i] == relativeToRow ){
						
					TableRowCore next = rows[i+1];
												
					return( next );
				}
			}
			
			return( rows[rows.length-1]);
			
		}else{
				// existing logic below broken for 'full table' - can't be bothered to fix it
			
			TableRowCore rowToSelect = null;
	
			if (relativeToRow.isExpanded() && relativeToRow.getSubItemCount() > 0) {
				TableRowCore[] subRowsWithNull = relativeToRow.getSubRowsWithNull();
				for (TableRowCore row : subRowsWithNull) {
					if (row != null) {
						rowToSelect = row;
						break;
					}
				}
				if (rowToSelect == null) {
					rowToSelect = getRow(relativeToRow.getIndex() + 1);
				}
			} else {
				TableRowCore parentRow = relativeToRow.getParentRowCore();
				if (parentRow != null) {
					rowToSelect = parentRow.getSubRow(relativeToRow.getIndex() + 1);
	
					if (rowToSelect == null) {
						rowToSelect = getRow(parentRow.getIndex() + 1);
					}
				} else {
					rowToSelect = getRow(relativeToRow.getIndex() + 1);
				}
			}
	
			return rowToSelect;
		}
	}

	/* (non-Javadoc)
	 * @see TableView#clipboardSelected()
	 */
	@Override
	public void clipboardSelected() {
		String sToClipboard = "";
		TableColumnCore[] visibleColumns = getVisibleColumns();
		for (int j = 0; j < visibleColumns.length; j++) {
			if (j != 0) {
				sToClipboard += "\t";
			}
			String title = MessageText.getString(visibleColumns[j].getTitleLanguageKey());
			sToClipboard += title;
		}

		TableRowCore[] rows = getSelectedRows();
		for (TableRowCore row : rows) {
			sToClipboard += "\n";
			TableRowPainted p_row = (TableRowPainted)row;
			p_row.setShown( true, true );
			p_row.refresh( true, true );
			for (int j = 0; j < visibleColumns.length; j++) {
				TableColumnCore column = visibleColumns[j];
				if (j != 0) {
					sToClipboard += "\t";
				}
				TableCellCore cell = row.getTableCellCore(column.getName());
				if (cell != null) {
					sToClipboard += cell.getClipboardText();
				}
			}
		}
		new Clipboard(getComposite().getDisplay()).setContents(new Object[] {
			sToClipboard
		}, new Transfer[] {
			TextTransfer.getInstance()
		});
	}

	/* (non-Javadoc)
	 * @see TableView#isDisposed()
	 */
	@Override
	public boolean isDisposed() {
		return destroying || cTable == null || cTable.isDisposed();
	}

	@Override 
	public TableRowCore[]
	getVisibleRows()
	{
		synchronized( visibleRows_sync ){
			
			return( visibleRows.toArray( new TableRowCore[ visibleRows.size()]));
		}
	}
	
	@Override
	public boolean hasChangesPending()
	{
		if ( hasPendingDSChanges()){
			
			return( true );
		}
		
		sortRows( true, false );
		
		if ( lastMC != mutationCount.get()){
			
			return( true );
		}
		
		return( false );
	}
	
	@Override
	public void refreshTable(final boolean bForceSort) {
		refreshTableRunnable.setForceSort(bForceSort);
		refresh_dispatcher.dispatch();
	}

	private void __refreshTable(boolean bForceSort) {
		long lStart = SystemTime.getCurrentTime();
		super.refreshTable(bForceSort);

		Utils.execSWTThread((RunnableIfDisplay) display -> {
			// call to trigger invalidation if visibility changes
			isVisible();
		});
		final boolean bDoGraphics = (loopFactor % graphicsUpdate) == 0;
		final boolean bWillSort = bForceSort || (reOrderDelay != 0)
				&& ((loopFactor % reOrderDelay) == 0);
		//System.out.println("Refresh.. WillSort? " + bWillSort);

		if (bWillSort) {
			TableColumnCore[] sortColumns = getSortColumns();
			if (bForceSort && sortColumns.length > 0) {
				resetLastSortedOn();
				for (TableColumnCore sortColumn : sortColumns) {
					sortColumn.setLastSortValueChange(SystemTime.getCurrentTime());
				}
			}
			_sortColumn(true, false, false);
		}

		runForAllRows(new TableGroupRowVisibilityRunner() {
			@Override
			public void run(TableRowCore row, boolean bVisible) {
				row.refresh(bDoGraphics, bVisible);
			}
		});
		loopFactor++;

		long diff = SystemTime.getCurrentTime() - lStart;
		if (diff > 0) {
			//debug("refreshTable took " + diff);
		}
	}

	/* (non-Javadoc)
	 * @see TableView#setEnableTabViews(boolean)
	 */
	@Override
	public void setEnableTabViews(boolean enableTabViews, boolean expandByDefault){
		this.enableTabViews = enableTabViews;
		tabViewsExpandedByDefault = expandByDefault;
	}

	@Override
	public boolean isTabViewsEnabled() {
		return enableTabViews;
	}

	@Override
	public boolean
	getTabViewsExpandedByDefault()
	{
		return( tabViewsExpandedByDefault );
	}

	/* (non-Javadoc)
	 * @see TableView#setFocus()
	 */
	@Override
	public void requestFocus( int reason ) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (isDisposed()) {
					return;
				}
				
					// if we have an active search then we don't want to grab focus
					// away from the text box due to a selected-content change
				
				if ( reason == 1 ){
					
					TableViewSWTFilter<?> filter = getSWTFilter();
					
					if ( filter != null ){ // && filter.widget.isFocusControl()){
						
						String text = filter.text;
						
						if ( text != null && !text.isEmpty()){
							
							return;
						}
					}
					
					cTable.setFocus();
					
				}else if ( reason == 2 ){
				
					if ( getSelectedRowsSize() == 0 ){
						
						TableRowCore[] rows = getRows();
						
						if ( rows.length > 0 ){
							
							setSelectedRows( new TableRowCore[]{ rows[0] });
						}
					}
					
					cTable.setFocus();
					
				}else{
					
					cTable.setFocus();
				}
			}
		});
	}

	@Override
	public void setRowDefaultHeightEM(final float lineHeight) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (cTable == null || cTable.isDisposed()) {
					iHeightEM  = lineHeight;
					//Debug.out("Could not set Row Height -- no cTable");
					return;
				}
				int fontHeightInPX = FontUtils.getFontHeightInPX(cTable.getFont());
				int height = (int) ((fontHeightInPX * lineHeight) + lineHeight);
				setRowDefaultHeightPX(height);
			}
		});
	}

	/* (non-Javadoc)
	 * @see TableView#setRowDefaultHeight(int)
	 */
	@Override
	public void setRowDefaultHeight(int iHeight) {
		setRowDefaultHeightPX(iHeight);
	}

	@Override
	public void setRowDefaultHeightPX(int iHeight) {
		if (iHeight != defaultRowHeight) {
			defaultRowHeight = iHeight;

			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (vBar != null && !vBar.isDisposed()) {
						vBar.setIncrement(defaultRowHeight);
					}
				}
			});
		}
	}

	@Override
	public int getLineHeight() {
		return lineHeight;
	}
	
	@Override
	public int getRowMinHeight() {
		return rowMinHeight;
	}

	/* (non-Javadoc)
	 * @see TableView#getRow(int, int)
	 */
	@Override
	public TableRowCore getRow(int x, int y) {
		Set<TableRowPainted> visibleRows = this.visibleRows;
		if (visibleRows.size() == 0) {
			return null;
		}
		boolean firstRow = true;
		int curY = 0;
		for (TableRowPainted row : visibleRows) {
			if (firstRow) {
				curY = row.getDrawOffset().y;
			}
			int h = row.getHeight();
			if (y >= curY && y < curY + h) {
				return row;
			}
			curY += h;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see TableView#isRowVisible(TableRowCore)
	 */
	@Override
	public boolean isRowVisible(TableRowCore row) {
		if (row == null) {
			return false;
		}
		synchronized (visibleRows_sync) {
			return visibleRows.contains(row);
		}
	}

	/* (non-Javadoc)
	 * @see TableView#getTableCellWithCursor()
	 */
	@Override
	public TableCellCore getTableCellWithCursor() {
		// TODO: Make work outside SWT?
		Point pt = cTable.getDisplay().getCursorLocation();
		pt = cTable.toControl(pt);
		return getTableCell(pt.x, clientArea.y + pt.y);
	}

	/* (non-Javadoc)
	 * @see TableView#getTableRowWithCursor()
	 */
	@Override
	public TableRowCore getTableRowWithCursor() {
		// TODO: Make work outside SWT?
		Point pt = cTable.getDisplay().getCursorLocation();
		pt = cTable.toControl(pt);
		return getTableRow(pt.x, pt.y, true);
	}

	/* (non-Javadoc)
	 * @see TableView#getRowDefaultHeight()
	 */
	@Override
	public int getRowDefaultHeight() {
		return defaultRowHeight;
	}

	/* (non-Javadoc)
	 * @see TableView#setEnabled(boolean)
	 */
	@Override
	public void setEnabled(final boolean enable) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (!isDisposed()) {
					cTable.setEnabled(enable);
					if (header != null) {
						header.setEnabled(enable);
					}
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see TableView#canHaveSubItems()
	 */
	@Override
	public boolean canHaveSubItems() {
		return true;
	}
	
	public void setExpandEnabled( boolean b )
	{
		expandEnabled = b;
	}
	
	public boolean isExpandEnabled(){
		return( expandEnabled );
	}
	

	/* (non-Javadoc)
	 * @see TableView#setHeaderVisible(boolean)
	 */
	@Override
	public void setHeaderVisible(final boolean visible) {
		super.setHeaderVisible(visible);
		if (header != null) {
			header.setHeaderVisible(visible);
		}
	}

	/* (non-Javadoc)
	 * @see TableView#getMaxItemShown()
	 */
	@Override
	public int getMaxItemShown() {
		// NOT USED
		return 0;
	}

	/* (non-Javadoc)
	 * @see TableView#setMaxItemShown(int)
	 */
	@Override
	public void setMaxItemShown(int newIndex) {
		// NOT USED
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.internat.MessageText.MessageTextListener#localeChanged(java.util.Locale, java.util.Locale)
	 */
	@Override
	public void localeChanged(Locale old_locale, Locale new_locale) {
		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				if (tvTabsCommon != null) {
					tvTabsCommon.localeChanged();
				}

				tableInvalidate();
				refreshTable(true);
				if (header != null) {
					header.redraw();
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see TableStructureModificationListener#columnOrderChanged(int[])
	 */
	@Override
	public void columnOrderChanged(int[] iPositions) {
		//TODO
	}

	/* (non-Javadoc)
	 * @see TableStructureModificationListener#columnSizeChanged(TableColumnCore, int)
	 */
	@Override
	public void columnSizeChanged(TableColumnCore tableColumn, int diff) {
		columnsWidth += diff;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (header != null) {
					header.redraw();
				}
				swt_fixupSize();
				redrawTable();
			}
		});
	}
	
	@Override
	public void sortOrderChanged(){
		TableColumnManager tcManager = TableColumnManager.getInstance();

		String[] sortColumnNames = tcManager.getDefaultSortColumnNames(tableID);
		if (sortColumnNames.length == 0) {
			sortColumnNames = new String[] { sDefaultSortOn };
		}

		TableColumnCore[] tableColumns = getAllColumns();
		
		TableColumnCore[] sortColumns = new TableColumnCore[sortColumnNames.length];
		for (int i = 0; i < sortColumnNames.length; i++) {
			String sortColumnName = sortColumnNames[i];
			TableColumnCore tc = tcManager.getTableColumnCore(tableID, sortColumnName);
			if (tc == null && tableColumns.length > 0) {
				tc = tableColumns[0];
			}
			sortColumns[i] = tc;
		}
		setSortColumns(sortColumns, false);		
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#addKeyListener(org.eclipse.swt.events.KeyListener)
	 */
	@Override
	public void addKeyListener(KeyListener listener) {
		if (tvSWTCommon == null) {
			return;
		}
		tvSWTCommon.addKeyListener(listener);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#removeKeyListener(org.eclipse.swt.events.KeyListener)
	 */
	@Override
	public void removeKeyListener(KeyListener listener) {
		if (tvSWTCommon == null) {
			return;
		}
		tvSWTCommon.removeKeyListener(listener);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#getKeyListeners()
	 */
	@Override
	public KeyListener[] getKeyListeners() {
		if (tvSWTCommon == null) {
			return new KeyListener[0];
		}
		return tvSWTCommon.getKeyListeners();
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#addMenuFillListener(com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener)
	 */
	@Override
	public void addMenuFillListener(TableViewSWTMenuFillListener l) {
		if (tvSWTCommon == null) {
			return;
		}
		tvSWTCommon.addMenuFillListener(l);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#createDragSource(int)
	 */
	@Override
	public DragSource createDragSource(int style) {
		if (dragSource != null && !dragSource.isDisposed()) {
			dragSource.dispose();
		}

		// dragSource will auto-dispose when cTable disposes
		dragSource = DragDropUtils.createDragSource(cTable, style);
		dragSource.addDragListener(new DragSourceAdapter() {
			@Override
			public void dragStart(DragSourceEvent event) {
				cTable.setCursor(null);
				TableRowCore row = getTableRow(event.x, event.y, true);
				if (row != null && !row.isSelected()) {
					setSelectedRows(new TableRowCore[] { row });
				}
				isDragging = true;
			}

			@Override
			public void dragFinished(DragSourceEvent event) {
				isDragging = false;
			}
		});
		return dragSource;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#createDropTarget(int)
	 */
	@Override
	public DropTarget createDropTarget(int style) {
		if (dropTarget != null && !dropTarget.isDisposed()) {
			dropTarget.dispose();
		}

		// dropTarget will auto-dispose when cTable disposes
		dropTarget = new DropTarget(cTable, style);
		return dropTarget;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#getComposite()
	 */
	@Override
	public Composite getComposite() {
		return cTable;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#getRow(org.eclipse.swt.dnd.DropTargetEvent)
	 */
	@Override
	public TableRowCore getRow(DropTargetEvent event) {
		//TODO
		// maybe
		Point pt = cTable.toControl(event.x, event.y);
		return getRow(pt.x, clientArea.y + pt.y);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#getRowSWT(java.lang.Object)
	 */
	@Override
	public TableRowSWT getRowSWT(Object dataSource) {
		return (TableRowSWT) getRow(dataSource);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#getTableComposite()
	 */
	@Override
	public Composite getTableComposite() {
		return cTable;
	}

	/** Creates a composite within the specified composite and sets its layout
	 * to a default FillLayout().
	 *
	 * @param composite to create your Composite under
	 * @return The newly created composite
	 */
	@Override
	public Composite createMainPanel(Composite composite) {
		TableViewSWTPanelCreator mainPanelCreator = getMainPanelCreator();
		if (mainPanelCreator != null) {
			return mainPanelCreator.createTableViewPanel(composite);
		}
		Composite panel = new Composite(composite, SWT.NO_FOCUS);
		composite.getLayout();
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		panel.setLayout(layout);

		Object parentLayout = composite.getLayout();
		if (parentLayout == null || (parentLayout instanceof GridLayout)) {
			panel.setLayoutData(new GridData(GridData.FILL_BOTH));
		}

		return panel;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#initialize(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void initialize(Composite parent) {
		initialize( null, parent );
	}
	@Override
	public void initialize(UISWTView parentView, Composite parent) {
		tvTabsCommon = new TableViewSWT_TabsCommon(parentView,this);

		shell = parent.getShell();
		mainComposite = tvTabsCommon.createSashForm(parent);
		mainComposite.setData("Name", tableID);
		mainComposite.setData("ObfuscateImage", this);
		Composite cTableComposite = tvTabsCommon.tableComposite;

		GridLayout tableLayout = new GridLayout(1, false);
		tableLayout.marginHeight = tableLayout.marginWidth = tableLayout.verticalSpacing = tableLayout.horizontalSpacing = 0;
		cTableComposite.setLayout(tableLayout);
		Layout layout = parent.getLayout();
		if (layout instanceof FormLayout) {
			FormData fd = Utils.getFilledFormData();
			cTableComposite.setLayoutData(fd);
		}

		Canvas cHeaderArea = new Canvas(cTableComposite, SWT.DOUBLE_BUFFERED);
		cHeaderArea.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		cTable = new Canvas(cTableComposite, SWT.NO_BACKGROUND | SWT.H_SCROLL | SWT.V_SCROLL);
		cTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		lineHeight = FontUtils.getFontHeightInPX(cTable.getFont());
		rowMinHeight = lineHeight;
		if (iHeightEM > 0) {
			defaultRowHeight = (int) ((rowMinHeight * iHeightEM) + iHeightEM);
			iHeightEM = -1;
		}

		// good test
		//cTable.setFont(FontUtils.getFontPercentOf(cTable.getFont(), 1.50f));
		rowMinHeight += Math.ceil(rowMinHeight * 2.0 / 16.0);
		if (defaultRowHeight < rowMinHeight) {
			defaultRowHeight = rowMinHeight;
		}

		cTable.setBackground(TablePaintedUtils.getColour(parent.getDisplay(), SWT.COLOR_LIST_BACKGROUND));

		clientArea = cTable.getClientArea();

		TableColumnCore[] tableColumns = getAllColumns();
		TableColumnCore[] tmpColumnsOrdered = new TableColumnCore[tableColumns.length];
		//Create all columns
		int columnOrderPos = 0;
		Arrays.sort(tableColumns,
				TableColumnManager.getTableColumnOrderComparator());
		for (int i = 0; i < tableColumns.length; i++) {
			int position = tableColumns[i].getPosition();
			if (position != -1 && tableColumns[i].isVisible()) {
				//table.createNewColumn(SWT.NULL);
				//System.out.println(i + "] " + tableColumns[i].getName() + ";" + position);
				tmpColumnsOrdered[columnOrderPos++] = tableColumns[i];
			}
		}
		//int numSWTColumns = table.getColumnCount();
		//int iNewLength = numSWTColumns - (bSkipFirstColumn ? 1 : 0);
		TableColumnCore[] columnsOrdered = new TableColumnCore[columnOrderPos];
		System.arraycopy(tmpColumnsOrdered, 0, columnsOrdered, 0, columnOrderPos);
		setColumnsOrdered(columnsOrdered);

		cTable.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				swt_paintComposite(e);
			}
		});

		header = new TableHeaderPainted(this, cHeaderArea);

		menu = createMenu();
		cTable.setMenu(menu);

		cTable.addControlListener(new ControlListener() {
			boolean inControlResize = false;

			@Override
			public void controlResized(ControlEvent e) {
				if (inControlResize) {
					return;
				}
				try {
					inControlResize = true;
					swt_calculateClientArea();
					swt_fixupSize();
				} finally {
					inControlResize = false;
				}
			}

			@Override
			public void controlMoved(ControlEvent e) {
			}
		});

		hBar = cTable.getHorizontalBar();
		if (hBar != null) {
			hBar.setValues(0, 0, 0, 10, 10, 100);
			hBar.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					//swt_calculateClientArea();
					if (DIRECT_DRAW) {
						swt_calculateClientArea();
						redrawTable();
					} else {
						cTable.redraw();
					}
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
		}
		vBar = cTable.getVerticalBar();
		vBar.setData("ScrollOnMouseOver", (Runnable) () -> swt_vBarChanged());
		if (vBar != null) {
			vBar.setValues(0, 0, 0, 50, getRowDefaultHeight(), 50);
			vBar.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					swt_vBarChanged();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
		}

		if (DEBUG_WITH_SHELL) {
	  		Shell shell = new Shell();
	  		sCanvasImage = new Canvas(shell, SWT.DOUBLE_BUFFERED);
	  		shell.setText(tableID);
	  		shell.setLayout(new FillLayout());
	  		sCanvasImage.addPaintListener(new PaintListener() {
	  			@Override
				  public void paintControl(PaintEvent e) {
	  				if (canvasImage == null) {
	  					return;
	  				}

	  				e.gc.drawImage(canvasImage, 0, 0);
	  				//System.out.println(System.currentTimeMillis() + "] Paint " + e.x + "x" + e.y + " " + e.width + "x" + e.height);

	  			}
	  		});
	  		shell.addDisposeListener(new DisposeListener() {
	  			@Override
				  public void widgetDisposed(DisposeEvent e) {
	  				sCanvasImage = null;
	  			}
	  		});
	  		shell.setVisible(true);
	  		forceDebugShellRefresh(null);
		}


		cTable.addMouseListener(tvSWTCommon);
		cTable.addMouseMoveListener(tvSWTCommon);
		cTable.addKeyListener(tvSWTCommon);
		cTable.addMenuDetectListener(tvSWTCommon);
		//composite.addSelectionListener(tvSWTCommon);
		
		cTable.addListener(SWT.MouseVerticalWheel, e -> {
			if  (e.stateMask == 0) {
				return;
			}
			if (e.widget != e.display.getCursorControl()) {
				return;
			}
			if ((e.stateMask & SWT.MOD1) > 0) {
				int newHeight = Math.min(
						Math.max(getRowMinHeight(), getRowDefaultHeight() - e.count), 300);
				setRowHeight(newHeight);
				e.doit = false;
			}
		});

		cTable.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				e.doit = true;
			}
		});

		cTable.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (canvasImage != null) {
					canvasImage.dispose();
					canvasImage = null;
				}
			}
		});


		SelectedContentManager.addCurrentlySelectedContentListener(new SelectedContentListener() {
			@Override
			public void currentlySelectedContentChanged(
					ISelectedContent[] currentContent, String viewID) {
				if ( cTable == null || cTable.isDisposed()){
					SelectedContentManager.removeCurrentlySelectedContentListener( this );
				}else{
					//redrawTable();
					TableRowCore[] rows = getSelectedRows();
					for (TableRowCore row : rows) {
						row.invalidate();
						redrawRow((TableRowPainted) row, false);
					}
				}
			}
		});

		cTable.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				isFocused = false;
				redrawTable();
			}

			@Override
			public void focusGained(FocusEvent e) {
				isFocused = true;
				redrawTable();
			}
		});
		isFocused = cTable.isFocusControl();

		new TableTooltips(this, cTable);

		TableColumnManager tcManager = TableColumnManager.getInstance();

		String[] sortColumnNames = tcManager.getDefaultSortColumnNames(tableID);
		if (sortColumnNames.length == 0) {
			sortColumnNames = new String[] { sDefaultSortOn };
		}

		TableColumnCore[] sortColumns = new TableColumnCore[sortColumnNames.length];
		for (int i = 0; i < sortColumnNames.length; i++) {
			String sortColumnName = sortColumnNames[i];
			TableColumnCore tc = tcManager.getTableColumnCore(tableID, sortColumnName);
			if (tc == null && tableColumns.length > 0) {
				tc = tableColumns[0];
			}
			sortColumns[i] = tc;
		}
		setSortColumns(sortColumns, false);

		Map tableConfigMap = tcManager.getTableConfigMap(tableID);
		defaultRowHeight = MapUtils.getMapInt(tableConfigMap, "RowHeight", defaultRowHeight);

		triggerLifeCycleListener(TableLifeCycleListener.EVENT_TABLELIFECYCLE_INITIALIZED);

		configMan.addParameterListener("Graphics Update", this);
		configMan.addParameterListener("ReOrder Delay", this);
		configMan.addParameterListener("Table.extendedErase", this);
		Colors.getInstance().addColorsChangedListener(this);

		// So all TableView objects of the same TableID have the same columns,
		// and column widths, etc
		TableStructureEventDispatcher.getInstance(tableID).addListener(this);

		MessageText.addListener(this);
	}

	FrequencyLimitedDispatcher vbarDispatcher = new 
			FrequencyLimitedDispatcher(
				AERunnable.create(()->{
					Utils.execSWTThread(()->{
						if ( cTable == null || cTable.isDisposed()){
							return;
						}
						if (DEBUG_SELECTION) {
							debug("vBar changed " + vBar.getSelection() + " via " + Debug.getCompressedStackTrace());
						}
						swt_calculateClientArea();
						cTable.update();
					});
				}), 100 );
				
	protected void swt_vBarChanged() {
		vbarDispatcher.dispatch();
	}

	protected void
	rowCreated()
	{
		mutationCount.incrementAndGet();
	}
	
	public void
	tableMutated()
	{
		super.tableMutated();
		
		mutationCount.incrementAndGet();
	}
	
	@Override
	public void tableStructureChanged(final boolean columnAddedOrRemoved,
			final Class forPluginDataSourceType) {

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				TableViewPainted.super.tableStructureChanged(columnAddedOrRemoved, forPluginDataSourceType);
				if (header != null) {
					header.redraw();
				}

				redrawTable();
			}
		});
	}

	protected void swt_paintComposite(PaintEvent e) {
		swt_calculateClientArea();
		if (canvasImage == null) {
			swt_paintCanvasImage(e.gc, e.gc.getClipping());
			return;
		}

		//System.out.println(e.count + " paint " + e.gc.getClipping() + ";" + e.x + "," + e.y + "," + e.width + "," + e.height + " via " + Debug.getCompressedStackTrace());

		e.gc.drawImage(canvasImage, -clientArea.x, 0);

		// test line
		//e.gc.drawLine(0, 0, cTable.getSize().x, canvasImage.getBounds().height);
	}

	protected void swt_paintCanvasImage(GC gc, Rectangle drawBounds) {
		if (cTable == null || cTable.isDisposed()) {
			return;
		}
		
		int mut = mutationCount.get();
		
		if ( mut != lastMC ){	
			
			boolean changed = numberAllVisibleRows();
			/* rather than just not needing this it actually screws up cell refresh
			 * Not sure why but if you enable it and then, for example, go to All Peers view and
			 * enable the connected_time column and sort on it you will see stuttering
			 * cell updates as the table mutates due to automatic re-sorting...
			 * 
			if ( changed ){
					// not actually sure we need this any more
				if ( canvasImage != null ){
					drawBounds = canvasImage.getBounds();
				}
			}
			*/
			
			synchronized( visibleRows_sync ){
				
				for ( TableColumnPainted tcp: paintedColumns.values()){
					
					tcp.sync();
				}
			}
		}
		
		try{
			int end = drawBounds.y + drawBounds.height;
	
			gc.setFont(cTable.getFont());
			Utils.setClipping(gc, drawBounds);
			TableRowCore oldRow = null;
			int pos = -1;
			Set<TableRowPainted> visibleRows = this.visibleRows;
	
			boolean isTableSelected = isTableSelected();
			boolean isTableEnabled = cTable.isEnabled();
			
			for (TableRowPainted row : visibleRows) {
				TableRowPainted paintedRow = row;
				int rowHeight = paintedRow.getHeight();
				
				if ( rowHeight > 0 ){
					if (pos == -1) {
						pos	= row.getVisibleRowIndex();
					} else {
						pos++;
					}
					Point drawOffset = paintedRow.getDrawOffset();
					int rowStartX = 0;
					if (DIRECT_DRAW) {
						rowStartX = -drawOffset.x;
					}
					int rowStartY = drawOffset.y - clientArea.y;
					
					//debug("Paint " + drawBounds.x + "x" + drawBounds.y + " " + drawBounds.width + "x" + drawBounds.height + "; Row=" +row.getIndex() + ";clip=" + gc.getClipping() +";drawOffset=" + drawOffset);
					if (drawBounds.intersects(rowStartX, rowStartY, 9999, rowHeight)) {
						// ensure full row height
						int diffY2 = (rowStartY + rowHeight) - (drawBounds.y + drawBounds.height);
						if (diffY2 > 0 ) {
							drawBounds.height += diffY2;
							Utils.setClipping(gc, drawBounds);
						}
						paintedRow.swt_paintGC(gc, drawBounds, rowStartX, rowStartY, pos,
								isTableSelected, isTableEnabled);
					}
				}
				oldRow = row;
			}
	
			int h;
			int yDirty;
			if (oldRow == null) {
				yDirty = drawBounds.y;
				h = drawBounds.height;
			} else {
				yDirty = ((TableRowPainted) oldRow).getDrawOffset().y
						+ ((TableRowPainted) oldRow).getFullHeight();
				h = (drawBounds.y + drawBounds.height) - yDirty;
			}
			if (h > 0) {
				int rowHeight = getRowDefaultHeight();
				if (extendedErase && cTable.isEnabled()) {
					while (yDirty < end) {
						pos++;
						Color color = Colors.alternatingColors[pos % 2];
						if (color != null) {
							gc.setBackground(color);
						}
						if (color == null) {
							gc.setBackground(TablePaintedUtils.getColour(gc, SWT.COLOR_LIST_BACKGROUND));
						}
						gc.fillRectangle(drawBounds.x, yDirty, drawBounds.width, rowHeight);
						yDirty += rowHeight;
					}
				} else {
					gc.setBackground(TablePaintedUtils.getColour(gc,cTable.isEnabled() ?
							SWT.COLOR_LIST_BACKGROUND : SWT.COLOR_WIDGET_BACKGROUND));
					gc.fillRectangle(drawBounds.x, yDirty, drawBounds.width, h);
				}
			}
	
			//gc.setForeground(getColorLine());
			Utils.setClipping(gc, drawBounds);
			TableColumnCore[] visibleColumns = getVisibleColumns();
			int x = DIRECT_DRAW ? -clientArea.x : 0;
			if ( TablePaintedUtils.isDark()){
				gc.setAlpha(120);
				gc.setForeground( TablePaintedUtils.getColour(gc,SWT.COLOR_WIDGET_BACKGROUND ));
			}else{
				gc.setAlpha(20);
			}
			for (TableColumnCore column : visibleColumns) {
				x += column.getWidth();
	
				// Vertical lines between columns
				gc.drawLine(x - 1, drawBounds.y, x - 1, drawBounds.y + drawBounds.height);
			}
			gc.setAlpha(255);
		}finally{
			
			lastMC = mut;
		}
	}

	private Color getColorLine() {
		if (colorLine == null) {
			colorLine = TablePaintedUtils.getColour(cTable.getDisplay(), SWT.COLOR_LIST_BACKGROUND);
			HSLColor hslColor = new HSLColor();
			hslColor.initHSLbyRGB(colorLine.getRed(), colorLine.getGreen(),
					colorLine.getBlue());

			int lum = hslColor.getLuminence();
			if (lum > 127)
				lum -= 25;
			else
				lum += 40;
			hslColor.setLuminence(lum);

			colorLine = new Color(cTable.getDisplay(), hslColor.getRed(),
					hslColor.getGreen(), hslColor.getBlue());
		}

		return colorLine;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#obfuscatedImage(org.eclipse.swt.graphics.Image)
	 */
	@Override
	public Image obfuscatedImage(Image image) {
		TableColumnCore[] visibleColumns = getVisibleColumns();
		TableRowPainted[] visibleRows = this.visibleRows.toArray(new TableRowPainted[0]);

		for (TableRowPainted row : visibleRows) {
			if (row == null || row.isRowDisposed()) {
				continue;
			}

			for (TableColumnCore tc : visibleColumns) {
				if (tc == null || !tc.isObfuscated()) {
					continue;
				}

				TableCellPainted cell = (TableCellPainted) row.getTableCell(tc.getName());
				if (cell == null) {
					continue;
				}

				String text = cell.getObfuscatedText();

				if (text != null) {

					final Rectangle cellBounds = cell.getBoundsOnDisplay();
					Point ptDisplay = cTable.getShell().getLocation();
					cellBounds.x -= ptDisplay.x;
					cellBounds.y -= ptDisplay.y;
					Rectangle boundsRaw = cell.getBoundsRaw();
					if (boundsRaw.y + cellBounds.height > clientArea.y
							+ clientArea.height) {
						cellBounds.height -= (boundsRaw.y + cellBounds.height)
								- (clientArea.y + clientArea.height);
					}
					int tableWidth = cTable.getClientArea().width;
					if (boundsRaw.x + cellBounds.width > clientArea.x
							+ tableWidth) {
						cellBounds.width -= (boundsRaw.x + cellBounds.width)
								- (clientArea.x + tableWidth);
					}

					UIDebugGenerator.obfuscateArea(image, cellBounds, text);
				}

			}
		}

		if (tvTabsCommon != null) {
			tvTabsCommon.obfuscatedImage(image);
		}
		return image;
	}

	protected TableViewSWTPanelCreator getMainPanelCreator() {
		return mainPanelCreator;
	}

	@Override
	public TableViewCreator getTableViewCreator() {
		// TODO Auto-generated method stub
		return mainPanelCreator;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#setMainPanelCreator(com.biglybt.ui.swt.views.table.TableViewSWTPanelCreator)
	 */
	@Override
	public void setMainPanelCreator(TableViewSWTPanelCreator mainPanelCreator) {
		this.mainPanelCreator = mainPanelCreator;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#getTableCell(int, int)
	 */
	@Override
	public TableCellCore getTableCell(int x, int y) {
		TableRowSWT row = getTableRow(x, y, true);
		if (row == null) {
			return null;
		}

		TableColumnCore column = getTableColumnByOffset(x);
		if (column == null) {
			return null;
		}

		return row.getTableCellCore(column.getName());
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#getTableCellMouseOffset(com.biglybt.ui.swt.views.table.TableCellSWT)
	 */
	@Override
	public Point getTableCellMouseOffset(TableCellSWT tableCell) {
		if (tableCell == null) {
			return null;
		}
		Point pt = cTable.getDisplay().getCursorLocation();
		pt = cTable.toControl(pt);

		Rectangle bounds = tableCell.getBounds();
		int x = pt.x - bounds.x;
		if (x < 0 || x > bounds.width) {
			return null;
		}
		int y = pt.y - bounds.y;
		if (y < 0 || y > bounds.height) {
			return null;
		}
		return new Point(x, y);
	}

	@Override
	public void enableFilterCheck(Text txtFilter, TableViewFilterCheck<Object> filterCheck) {
		Object o = txtFilter.getData("BubbleTextBox");
		if (o instanceof BubbleTextBox) {
			enableFilterCheck((BubbleTextBox) o, filterCheck, false);
		}
	}

	@Override
	public void 
	enableFilterCheck(BubbleTextBox txtFilter, TableViewFilterCheck<Object> filterCheck) {
		enableFilterCheck( txtFilter, filterCheck, false );
	}
	@Override
	public void 
	enableFilterCheck(
		BubbleTextBox filterBox,
		TableViewFilterCheck<Object> filterCheck,
		boolean						 filterSubRows ) 
	{
		this.filterSubRows = filterSubRows;

		TableViewSWTFilter<?> filter = getSWTFilter();
		if (filter != null) {
			if (filter.filterBox != null && !filter.filterBox.isDisposed()) {
				filter.filterBox.setKeyListener(null);
				filter.filterBox.removeBubbleTextBoxChangeListenener(this);
			}
		} else {
			this.filter = filter = new TableViewSWTFilter();
		}
		filter.filterBox = filterBox;
		if (filterBox != null) {
			
			Class<?> cla = getDataSourceType();
			
			String historyKey = "";
			
			if ( cla != null ){
				if ( cla == DownloadTypeComplete.class || cla == DownloadTypeIncomplete.class || cla == Download.class ){
					// default, leave blank
				}else{
					historyKey = "." + cla.getName();	// different history for different table types
				}
			}
			
				// must create this before adding other listeners so it gets priority over key events
			
			TextWithHistory twh = new TextWithHistory(
					"tableviewpainted.search" + historyKey, "table.filter.history",
					filterBox.getTextWidget());
			
				// disable as interferes with key-down into search results feature 
			
			twh.setKeDownShowsHistory( false );
			
			filterBox.getTextWidget().addListener( SWT.FocusOut, (ev)->{
				String text = filterBox.getText().trim();
				if ( !text.isEmpty()){
					twh.addHistory( text );
				}
			});
			
			filterBox.setKeyListener(tvSWTCommon);

			if (filterBox.getText().length() == 0) {
				filterBox.setText(filter.text);
			}

			filterBox.setAllowRegex(true);
			filterBox.addBubbleTextBoxChangeListener(this);
		} else {
			filter.text = filter.nextText = "";
		}

		filter.checker = filterCheck;

		filter.checker.filterSet(filter.text);
		refilter();
	}

	protected boolean
	getFilterSubRows()
	{
		return( filterSubRows );
	}
	
	@Override
	public boolean
	hasFilterControl()
	{
		TableViewSWTFilter<?> filter = getSWTFilter();

		return( filter != null && filter.filterBox != null && !filter.filterBox.isDisposed() );
	}

	private Map<TableColumnCore,TableColumnPainted>	paintedColumns = new HashMap<>();
	
	protected TableColumnPainted
	getColumnPainted(
		TableColumnCore		c )
	{
		TableColumnPainted tcp = paintedColumns.get( c );
		
		if ( tcp == null ){
				
				// force a sync next draw to pick up initial state
			
			mutationCount.incrementAndGet();
			
			tcp = new TableColumnPainted( c );
			
			paintedColumns.put( c, tcp );
		}
		
		return( tcp );
	}
	
	@Override
	public void disableFilterCheck() {
		TableViewSWTFilter<?> filter = getSWTFilter();
		if ( filter == null ){
			return;
		}

		if (filter.filterBox != null && !filter.filterBox.isDisposed()) {
			filter.filterBox.setKeyListener(null);
			filter.filterBox.removeBubbleTextBoxChangeListenener(this);
		}
		this.filter = null;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#setFilterText(java.lang.String)
	 */
	@Override
	public void setFilterText(String s, boolean force) {
		if (tvSWTCommon != null) {
			tvSWTCommon.setFilterText(s, force);
		}
	}

	@Override
	public boolean enableSizeSlider(Composite composite, int min, int max) {
		return false;
	}

	@Override
	public void setRowHeight(int newHeight) {
		if (newHeight == defaultRowHeight) {
			return;
		}

		// Note: Various library views use the same tableID (tags torrent list)
		// Changing one will affect the rest on table re-initialization (restart or closing&re-opening view)
		// We'll need a sub-id at some point to store settings for specific tables
		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map tableConfigMap = tcManager.getTableConfigMap(tableID);
		tableConfigMap.put("RowHeight", newHeight);
		tcManager.setTableConfigMap(tableID, tableConfigMap);

		setRowDefaultHeightPX(newHeight);
		TableRowCore[] rows = getRowsAndSubRows(true);
		if (rows.length == 0) {
				// still want table to redraw (especially when using alternate line colours...)
			Utils.execSWTThreadLater(0,()->{
				swt_updateCanvasImage(false);
			});
			
			return;
		}
		for (TableRowCore row : rows) {
			((TableRowPainted) row).setHeight(newHeight, true);
		}

		// Bug: First row isn't being painted or something
		Utils.execSWTThreadLater(0, () -> rows[0].redraw());
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#addRowPaintListener(com.biglybt.ui.swt.views.table.TableRowSWTPaintListener)
	 */
	@Override
	public void addRowPaintListener(TableRowSWTPaintListener listener) {
		if (tvSWTCommon != null) {
			tvSWTCommon.addRowPaintListener(listener);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#removeRowPaintListener(com.biglybt.ui.swt.views.table.TableRowSWTPaintListener)
	 */
	@Override
	public void removeRowPaintListener(TableRowSWTPaintListener listener) {
		if (tvSWTCommon != null) {
			tvSWTCommon.removeRowPaintListener(listener);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#invokePaintListeners(org.eclipse.swt.graphics.GC, TableRowCore, TableColumnCore, org.eclipse.swt.graphics.Rectangle)
	 */
	@Override
	public void invokePaintListeners(GC gc, TableRowCore row,
	                                 TableColumnCore column, Rectangle cellArea) {
		if (tvSWTCommon != null) {
			tvSWTCommon.invokePaintListeners(gc, row, column, cellArea);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#addRowMouseListener(com.biglybt.pif.ui.tables.TableRowMouseListener)
	 */
	@Override
	public void addRowMouseListener(TableRowMouseListener listener) {
		if (tvSWTCommon != null) {
			tvSWTCommon.addRowMouseListener(listener);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#removeRowMouseListener(com.biglybt.pif.ui.tables.TableRowMouseListener)
	 */
	@Override
	public void removeRowMouseListener(TableRowMouseListener listener) {
		if (tvSWTCommon != null) {
			tvSWTCommon.removeRowMouseListener(listener);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#invokeRowMouseListener(com.biglybt.pif.ui.tables.TableRowMouseEvent)
	 */
	@Override
	public void invokeRowMouseListener(TableRowMouseEvent event) {
		if (tvSWTCommon != null) {
			tvSWTCommon.invokeRowMouseListener(event);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#packColumns()
	 */
	@Override
	public void packColumns() {
		// TODO
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.config.ParameterListener#parameterChanged(java.lang.String)
	 */
	@Override
	public void parameterChanged(String parameterName) {
		boolean invalidate = parameterName == null;
		if (parameterName == null || parameterName.equals("Graphics Update")) {
			graphicsUpdate = configMan.getIntParameter("Graphics Update");
		}
		if (parameterName == null || parameterName.equals("ReOrder Delay")) {
			reOrderDelay = configMan.getIntParameter("ReOrder Delay");
		}
		if (parameterName == null || parameterName.equals("Table.extendedErase")) {
			extendedErase = configMan.getBooleanParameter("Table.extendedErase");
			invalidate = true;
		}

		if (parameterName == null || parameterName.startsWith("Color")) {
			tableInvalidate();
		}
	}

	/* (non-Javadoc)
	 * @see TableViewImpl#createNewRow(java.lang.Object)
	 */
	@Override
	public TableRowCore createNewRow(Object object) {
		return new TableRowPainted(null, this, object, true);
	}

	@Override
	public TableRowSWT createFakeRow(Object object){
		return new TableRowPainted(null, this, object );
	}
	
	/* (non-Javadoc)
	 * @see TableViewImpl#visibleRowsChanged()
	 */
	@Override
	public void visibleRowsChanged() {
		if (Utils.isDisplayDisposed()) {
			return;
		}
		Utils.execSWTThread( this::swt_visibleRowsChanged );
	}

	private void swt_visibleRowsChanged() {
		final List<TableRowSWT> newlyVisibleRows = new ArrayList<>();
		final List<TableRowSWT> nowInVisibleRows;
		final ArrayList<TableRowSWT> rowsStayedVisibleButMoved = new ArrayList<>();
		List<TableRowSWT> newVisibleRows;
		if (isVisible()) {
			// this makes a copy.. slower
			TableRowCore[] rows = getRows();
			newVisibleRows = new ArrayList<>();
			recalculateVisibleRows(rows, 0, newVisibleRows,
					rowsStayedVisibleButMoved);

		} else {
			newVisibleRows = Collections.emptyList();
		}
		nowInVisibleRows = new ArrayList<>(0);
		synchronized (visibleRows_sync) {
			if (visibleRows != null) {
				nowInVisibleRows.addAll(visibleRows);
			}
		}

		LinkedHashSet<TableRowPainted> rows = new LinkedHashSet<>(newVisibleRows.size());
		for (TableRowSWT row : newVisibleRows) {
			rows.add((TableRowPainted) row);
			boolean removed = nowInVisibleRows.remove(row);
			if (!removed) {
				newlyVisibleRows.add(row);
			}
		}

		synchronized (visibleRows_sync) {
			visibleRows = rows;
		}

			// seems we need this otherwise when the last row of a table is removed we end
			// up with a blank row
		
		swt_fixupSize();
		
		if (DEBUG_ROWCHANGE) {
			int topIndex = visibleRows.size() > 0
					? indexOf(visibleRows.iterator().next()) : -1;
			debug(
					"visRowsChanged; isv=" + isVisible + "; top=" + topIndex + "; shown="
							+ visibleRows.size() + "; +" + newlyVisibleRows.size() + "/-"
							+ nowInVisibleRows.size() + "/" + rowsStayedVisibleButMoved.size()
							+ " via " + Debug.getCompressedStackTrace(8));
		}
		Utils.getOffOfSWTThread(new AERunnable() {

			@Override
			public void runSupport() {
				boolean bTableUpdate = false;

				for (TableRowSWT row : newlyVisibleRows) {
					// no need to refres, the redraw will do it
					//row.refresh(true, true);
 					row.setShown(true, false);
					rowsStayedVisibleButMoved.remove(row);
					if (Constants.isOSX) {
						bTableUpdate = true;
					}
				}

				for (TableRowSWT row : rowsStayedVisibleButMoved) {
					row.invalidate();
					redrawRow((TableRowPainted) row, false);
				}

				for (TableRowSWT row : nowInVisibleRows) {
					row.setShown(false, false);
				}

				if (bTableUpdate) {
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							if (cTable != null && !cTable.isDisposed()) {
								cTable.update();
							}
						}
					});
				}

			}
		});
	}

	private void recalculateVisibleRows(TableRowCore[] rows, int yStart,
			List<TableRowSWT> newVisibleRows,
			List<TableRowSWT> rowsStayedVisibleButMoved) {
		Rectangle bounds = clientArea;

		int y = yStart;
		String sDebug;
		if (DEBUG_ROWCHANGE) {
			sDebug = "Visible Rows: ";
		}
		for (TableRowCore row : rows) {
			if (row == null) {
				continue;
			}
			TableRowPainted rowSWT = ((TableRowPainted) row);
			int rowHeight = rowSWT.getHeight();
			int rowFullHeight = rowSWT.getFullHeight();

			if ((y < bounds.y + bounds.height) && (y + rowFullHeight > bounds.y)) {
				// this row or subrows are visible

				boolean offsetChanged = rowSWT.setDrawOffset(new Point(bounds.x, y));

				// check if this row
				if (y + rowHeight > bounds.y) {
					if (DEBUG_ROWCHANGE) {
						sDebug += (rowSWT.getParentRowCore() == null ? ""
								: rowSWT.getParentRowCore().getIndex() + ".")
								+ rowSWT.getIndex()
								+ "(ofs=" + (offsetChanged ? "*" : "")
								+ y
								+ ";rh="
								+ rowHeight + "/" + rowFullHeight + ";ds="
								+ rowSWT.getDataSource(true) + ")" + ", ";
					}

					if (offsetChanged) {
						rowsStayedVisibleButMoved.add(rowSWT);
					}
					newVisibleRows.add(rowSWT);
				}

				// check if subrows
				if (row.isExpanded()) {
					TableRowCore[] subRowsWithNull = row.getSubRowsWithNull();
					if (subRowsWithNull.length > 0) {
						recalculateVisibleRows(subRowsWithNull, y + rowHeight,
								newVisibleRows, rowsStayedVisibleButMoved);
					}
				}
			} else if (newVisibleRows.size() > 0) {
				if (DEBUG_ROWCHANGE) {
					sDebug += "break(ofs=" + y + ";bounds=" + bounds + ";rh=" + rowFullHeight + ")";
				}
				break;
			}
			y += rowFullHeight;
		}
		if (DEBUG_ROWCHANGE) {
			if (yStart == 0) {
				debug(sDebug);
			}
		}
	}

	@Override
	public int uiGuessMaxVisibleRows() {
		return (clientArea.height / defaultRowHeight) + 1;
	}

	@Override
	public void uiRemoveRows(TableRowCore[] rows, Integer[] rowIndexes) {
		if (focusedRow != null) {
  		for (TableRowCore row : rows) {
  			if (row == focusedRow) {
  				setFocusedRow(null);
  				break;
  			}
  		}
  	}
		int bottomIndex = getRowCount() - 1;
		if (bottomIndex < 0) {
			redrawTable();
		} else {
			TableRowCore rowBottom = getLastVisibleRow();
			if (rowBottom != null) {
				while (rowBottom.getParentRowCore() != null) {
					rowBottom = rowBottom.getParentRowCore();
				}

				if (indexOf(rowBottom) < 0) {
					redrawTable();
				}
			}
		}
	}

	private TableRowPainted getLastVisibleRow() {
		synchronized (visibleRows_sync) {
			if (visibleRows == null || visibleRows.size() == 0) {
				return null;
			}
			TableRowPainted rowBottom = null;
			for (TableRowPainted row : visibleRows) {
				rowBottom = row;
			}
			return rowBottom;
		}
	}


	@Override
	public void getOffUIThread(AERunnable runnable) {
		Utils.getOffOfSWTThread(runnable);
	}

	protected void swt_calculateClientArea() {
		if (cTable == null || cTable.isDisposed()) {
			return;
		}
		Rectangle oldClientArea = clientArea;
		Rectangle newClientArea = cTable.getClientArea();
		newClientArea.x = hBar.getSelection();
		newClientArea.y = vBar.getSelection();

		int w = 0;
		TableColumnCore[] visibleColumns = getVisibleColumns();
		for (TableColumnCore column : visibleColumns) {
			w += column.getWidth();
		}
		columnsWidth = w;
		w = newClientArea.width = Math.max(newClientArea.width, w);

		boolean refreshTable = false;
		boolean changedX;
		boolean changedY;
		//boolean changedW;
		boolean changedH;
		if (oldClientArea != null) {
			changedX = oldClientArea.x != newClientArea.x;
			changedY = oldClientArea.y != newClientArea.y;
			//changedW = oldClientArea.width != newClientArea.width;
			changedH = oldClientArea.height != newClientArea.height;
		} else {
			changedX = changedY = changedH = true;
			//changedX = changedY = changedW = changedH = true;
		}

		clientArea = newClientArea;
		if (tvSWTCommon != null) {
			tvSWTCommon.xAdj = -clientArea.x;
		}

		//System.out.println("CA=" + clientArea + " via " + Debug.getCompressedStackTrace());

		boolean needRedraw = false;
		if (changedY || changedH) {
			visibleRowsChanged();
			if (changedY && oldClientArea != null) {
				Set<TableRowPainted> visibleRows = this.visibleRows;
				if (visibleRows.size() > 0) {
					if (canvasImage != null && !canvasImage.isDisposed() && !changedH) {

						int yDiff = oldClientArea.y - newClientArea.y;
						if (Math.abs(yDiff) < clientArea.height) {
							boolean wasIn = in_swt_updateCanvasImage;
							in_swt_updateCanvasImage = true;
							try{
								GC gc = new GC(canvasImage);
								try{
									Rectangle bounds = canvasImage.getBounds();
									//System.out.println("moving y " + yDiff + ";cah=" + clientArea.height);
									if (yDiff > 0) {
										// User Scrolled up, Move Image Down
										if (Utils.isGTK3) {
											// Can't copyArea(x, y, w, h, dx, dy, paint) or drawImage(Image, x, y) downward without cheese
											gc.copyArea(canvasImage, 0, -yDiff);
										} else {
											gc.copyArea(0, 0, bounds.width, bounds.height, 0, yDiff, false);
										}
										swt_paintCanvasImage(gc, new Rectangle(0, 0, 9999, yDiff));
										Utils.setClipping(gc, (Rectangle) null);
									} else {
										// User scrolled down, move image up
										if (Utils.isGTK3) {
											//copyArea cheese on GTK3 SWT 4528/4608
											gc.drawImage(canvasImage, 0, yDiff);
										} else {
											gc.copyArea(0, -yDiff, bounds.width, bounds.height , 0, 0, false);
										}
										int h = -yDiff;
										TableRowPainted row = getLastVisibleRow();
										if (row != null) {
											//row.invalidate();
											h += row.getHeight();
										}
										swt_paintCanvasImage(gc, new Rectangle(0, bounds.height - h, 9999, h));
										Utils.setClipping(gc, (Rectangle) null);
									}
								}finally{
									gc.dispose();
								}
					  		if ( DEBUG_WITH_SHELL ){
					  			forceDebugShellRefresh(null);
					  		}

							}catch( Throwable e ){

									// seen an exception here caused, I think, by canvasImage already being
									// selected into a GC by code 'further down the stack'...

								refreshTable = true;

							}finally{

								in_swt_updateCanvasImage = wasIn;
							}

							needRedraw = true;
						} else {
							refreshTable = true;
						}
					} else if (canvasImage == null) {
						needRedraw = true;
					}

				}
			}
		}

		if (changedX) {
			if (header != null) {
				header.redraw();
			}
		}

		if (!DIRECT_DRAW) {
  		Image newImage = canvasImage;

  		//List<TableRowSWT> visibleRows = getVisibleRows();
  		int h = 0;
  		synchronized (visibleRows_sync) {
  			TableRowPainted lastRow = getLastVisibleRow();
  			if (lastRow != null) {
  				h = lastRow.getDrawOffset().y - clientArea.y + lastRow.getHeight();
  				if (h < clientArea.height && lastRow.isExpanded()) {
  					TableRowCore[] subRows = lastRow.getSubRowsWithNull();
  					for (TableRowCore subRow : subRows) {
  						if (subRow == null) {
  							continue;
  						}
  						TableRowPainted subRowP = (TableRowPainted) subRow;

  						h += subRowP.getFullHeight();
  						if (h >= clientArea.height) {
  							break;
  						}
  					}
  				}
  			}
  		}
  		if (h < clientArea.height) {
  			h = clientArea.height;
  		}

  		int oldH = canvasImage == null || canvasImage.isDisposed() ? 0
  				: canvasImage.getBounds().height;
  		int oldW = canvasImage == null || canvasImage.isDisposed() ? 0
  				: canvasImage.getBounds().width;

  		if (canvasImage == null || oldW != w || h > oldH) {
  			//System.out.println("oldW=" + oldW + ";" + w+ ";h=" + h + ";" + oldH);
  			if (h <= 0 || clientArea.width <= 0) {
  				if (newImage != null) {
  					newImage.dispose();
				  }
  				newImage = null;
  			} else {
  				newImage = new Image(shell.getDisplay(), w, h);
  			}
  		}
  		boolean canvasChanged = (canvasImage != newImage);
  		if (canvasChanged) {
  			Image oldImage = canvasImage;
  			canvasImage = newImage;

  			if (oldImage != null && !oldImage.isDisposed()) {
  				oldImage.dispose();
  			}
  		}



  		// paint event will handle any changedX or changedW
  		if (changedH || canvasChanged || refreshTable) {
  			//System.out.println(changedX + ";cY=" + changedY + ";cH=" + changedH + ";cC=" + canvasChanged + ";rT=" + refreshTable);
  			//System.out.println("Redraw " + Debug.getCompressedStackTrace());

  			// run refreshTable on SWT (this) thread to ensure rows have been
  			// refreshed for the updateCanvasImage call immediately after it
  			__refreshTable(false);
  			// refreshtable will call swt_updateCanvasImage for each visible row
  			if (canvasChanged) {
  				swt_updateCanvasImage(false);
  			}
  		}
		}

		//		System.out.println("imgBounds = " + canvasImage.getBounds() + ";ca="
		//				+ clientArea + ";" + composite.getClientArea() + ";h=" + h + ";oh="
		//				+ oldH + " via " + Debug.getCompressedStackTrace(3));

		if (needRedraw) {
			cTable.redraw();
		}
	}

	private void forceDebugShellRefresh(Rectangle bounds) {
		if (sCanvasImage == null || sCanvasImage.isDisposed()) {
			return;
		}

		if (canvasImage == null || canvasImage.isDisposed()) {
			sCanvasImage.getShell().setSize(0, 0);
			return;
		}
		Point size = sCanvasImage.getShell().computeSize(
			canvasImage.getBounds().width, canvasImage.getBounds().height);
		sCanvasImage.getShell().setSize(size);
		if (bounds == null) {
			sCanvasImage.redraw();
		} else {
			sCanvasImage.redraw(bounds.x, bounds.y, bounds.width, bounds.height,
				true);
		}
		sCanvasImage.update();
		//while (sCanvasImage.getDisplay().readAndDispatch()) {
		//}
	}

	public void swt_updateCanvasImage(boolean immediateRedraw) {
		if (canvasImage != null && !canvasImage.isDisposed()) {
			swt_updateCanvasImage(canvasImage.getBounds(), immediateRedraw);
		} else {
			cTable.redraw();
		}
	}

	private boolean in_swt_updateCanvasImage = false;
	protected void swt_updateCanvasImage(final Rectangle bounds, final boolean immediateRedraw) {
		// no need to sync around in_swt_updateCanvasImage, we are assumed to always
		// be on SWT thread and in_swt_updateCanvasImage is only used here
		if (in_swt_updateCanvasImage) {
			Utils.execSWTThreadLater(0, new SWTRunnable() {
				@Override
				public void runWithDisplay(Display display) {
					in_swt_updateCanvasImage = false;
					swt_updateCanvasImage(bounds, immediateRedraw);
				}
			});
			return;
		}
		in_swt_updateCanvasImage = true;
		try {
			int x;
			if (!DIRECT_DRAW) {
				if (canvasImage == null || canvasImage.isDisposed() || bounds == null) {
					return;
				}
				//System.out.println("UpdateCanvasImage " + bounds + "; via " + Debug.getCompressedStackTrace());
				GC gc = new GC(canvasImage);
				swt_paintCanvasImage(gc, bounds);
				gc.dispose();

				if (DEBUG_WITH_SHELL) {
					forceDebugShellRefresh(bounds);
				}
				x = bounds.x - clientArea.x;
			} else {
				x = bounds.x;
			}

			if (cTable != null && !cTable.isDisposed()) {
				if (DEBUG_REDRAW_CLIP) {
					GC gc = new GC(cTable);
					gc.setBackground(ColorCache.getRandomColor());
					gc.fillRectangle(x, bounds.y, bounds.width, bounds.height);
					gc.dispose();
				}
				cTable.redraw(x, bounds.y, bounds.width, bounds.height, false);
				if (immediateRedraw) {
					cTable.update();
				}
			}
		} finally {
			in_swt_updateCanvasImage = false;
		}
	}

	public Rectangle getClientArea() {
		return clientArea;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#isVisible()
	 */
	@Override
	public boolean isVisible() {
		if (!Utils.isThisThreadSWT()) {
			return isVisible;
		}
		boolean wasVisible = isVisible;
		isVisible = cTable != null && !cTable.isDisposed() && cTable.isVisible()
				&& !shell.getMinimized();
		if (isVisible != wasVisible) {
			visibleRowsChanged();
			MdiEntrySWT view = tvTabsCommon == null ? null
					: tvTabsCommon.getActiveSubView();
			if (isVisible) {
				loopFactor = 0;

				if (view != null) {
					view.getMDI().showEntry(view);
				}
			} else {
				if (view != null) {
					view.triggerEvent(UISWTViewEvent.TYPE_FOCUSLOST, null);
				}
			}
		}
		return isVisible;
	}

	@Override
	public void removeAllTableRows() {
		if (DEBUG_ROWCHANGE) {
			debug("RemoveAlLRows");
		}
		super.removeAllTableRows();
		synchronized (visibleRows_sync) {
			visibleRows = new LinkedHashSet<>();
		}
		setFocusedRow(null);
		totalHeight = 0;
		Utils.execSWTThread(new SWTRunnable() {
			@Override
			public void runWithDisplay(Display display) {
				if (cTable == null || cTable.isDisposed()) {
					return;
				}
				swt_fixupSize();
				swt_updateCanvasImage(false);
				if (DEBUG_ROWCHANGE) {
					debug("RemoveAllRows done");
				}
			}
		});
	}

	protected void swt_fixupSize() {
		//debug("Set minSize to " + columnsWidth + "x" + totalHeight + ";ca=" + clientArea + ";" + Debug.getCompressedStackTrace());
		boolean vBarValid = vBar != null && !vBar.isDisposed();
		if (vBarValid) {
			int tableSize = clientArea.height;
			int max = totalHeight;
			if (max < tableSize) {
				vBar.setSelection(0);
				vBar.setEnabled(false);
				vBar.setVisible(false);
			} else {
				if (!vBar.isVisible()) {
					vBar.setVisible(true);
					vBar.setEnabled(true);
				}
				if (vBar.getMaximum() != max) {
					vBar.setMaximum(max);
					swt_vBarChanged();
				}
				vBar.setThumb(tableSize);
				vBar.setPageIncrement(tableSize);
			}
		}
		if (hBar != null && !hBar.isDisposed()) {
			int tableSize = cTable.getSize().x;
			int max = columnsWidth;
			if (vBarValid && vBar.isVisible() && getScrollbarsMode() == SWT.NONE) {
				int vBarW = vBar.getSize().x;

				max += vBarW;
			}
			if (max < tableSize) {
				hBar.setSelection(0);
				hBar.setEnabled(false);
				hBar.setVisible(false);
			} else {
				if (!hBar.isVisible()) {
					hBar.setVisible(true);
					hBar.setEnabled(true);
				}
				hBar.setValues(hBar.getSelection(), 0, max, tableSize, 50, tableSize);
			}
			if (vBarValid && hBar.isVisible()) {
				int hBarW = getScrollbarsMode() == SWT.NONE ? hBar.getSize().y : 0;

				vBar.setThumb(clientArea.height - hBarW);
				vBar.setMaximum(totalHeight - hBarW);
				vBar.setPageIncrement(vBar.getPageIncrement() - hBarW);
			}

		}
	}

	private int getScrollbarsMode() {
		if (hasGetScrollBarMode) {
			return cTable.getScrollbarsMode();
		}
		return SWT.NONE;
	}

	@Override
	protected void uiChangeColumnIndicator() {
		Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {
				if (header != null) {
					header.redraw();
				}
			}
		});
	}

	@Override
	public TableColumnCore getTableColumnByOffset(int mouseX) {
		int x = -clientArea.x;
		TableColumnCore[] visibleColumns = getVisibleColumns();
		for (TableColumnCore column : visibleColumns) {
			int w = column.getWidth();

			if (mouseX >= x && mouseX < x + w) {
				return column;
			}

			x += w;
		}
		return null;
	}


	// @see com.biglybt.ui.swt.views.table.TableViewSWT#getTableRow(int, int, boolean)
	@Override
	public TableRowSWT getTableRow(int x, int y, boolean anyX) {
		return (TableRowSWT) getRow(anyX ? 2 : x, clientArea.y + y);
	}

	@Override
	public void setSelectedRows(TableRowCore[] newSelectionArray, boolean trigger) {
		super.setSelectedRows(newSelectionArray, trigger);

		boolean focusInSelection = false;
		for (TableRowCore row : newSelectionArray) {
			if (row == null) {
				continue;
			}
			if (row.equals(focusedRow)) {
				focusInSelection = true;
				break;
			}
		}
		if (focusInSelection) {
			reaffirmSelection();
		}else{
			setFocusedRow(newSelectionArray.length == 0 ? null : newSelectionArray[0]);
		}
	}

	@Override
	public void setRowSelected(final TableRowCore row, boolean selected,
	                           boolean trigger) {
		if (selected && !isSelected(row)) {
			setFocusedRow(row);
		}
		super.setRowSelected(row, selected, trigger);

		if (row instanceof TableRowSWT) {
			((TableRowSWT) row).setWidgetSelected(selected);
		}
	}

	@Override
	public void editCell(TableColumnCore column, int row) {
		//TODO
	}

	@Override
	public boolean isDragging() {
		return isDragging;
	}

	@Override
	public TableViewSWTFilter<?> getSWTFilter() {
		return (TableViewSWTFilter<?>) filter;
	}

	@Override
	public void openFilterDialog() {
		if (filter == null) {
			return;
		}
		SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow();
		String tableTitle = MessageText.getString(getTextPrefixID() + ".header", (String) null);
		// fallback to legacy
		if (tableTitle == null) {
			tableTitle = MessageText.getString(getTableID() + "View.header");
			if (Constants.isCVSVersion()) {
				tableTitle += " (Fallback Text)";
			}
		}
		entryWindow.initTexts("MyTorrentsView.dialog.setFilter.title", null,
				"MyTorrentsView.dialog.setFilter.text", new String[] {
					tableTitle
				});
		entryWindow.setPreenteredText(filter.text, false);
		entryWindow.prompt(results -> {
			if (!results.hasSubmittedInput()) {
				return;
			}

			String message = results.getSubmittedInput();
			setFilterText(message == null ? "" : message, false);
		});
	}

	@Override
	public boolean isSingleSelection() {
		return !isMultiSelect;
	}

	@Override
	public void expandColumns() {
		//TODO
	}

	@Override
	public void triggerTabViewsDataSourceChanged() {
		if (tvTabsCommon != null) {
			tvTabsCommon.triggerTabViewsDataSourceChanged(this);
		}
	}

	@Override
	public TableViewSWT_TabsCommon getTabsCommon() {
		return( tvTabsCommon );
	}

	@Override
	public void uiSelectionChanged(final TableRowCore[] newlySelectedRows,
			final TableRowCore[] deselectedRows) {
		//System.out.println("Redraw " + Debug.getCompressedStackTrace());
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				for (TableRowCore row : deselectedRows) {
					row.invalidate();
					redrawRow((TableRowPainted) row, false);
				}
				for (TableRowCore row : newlySelectedRows) {
					row.invalidate();
					redrawRow((TableRowPainted) row, false);
				}
			}
		});
	}

	@Override
	protected void triggerLifeCycleListener(int eventType) {
		super.triggerLifeCycleListener(eventType);

		if (eventType == TableLifeCycleListener.EVENT_TABLELIFECYCLE_DESTROYED) {
			Utils.execSWTThread(new SWTRunnable() {
				@Override
				public void runWithDisplay(Display display) {
					Utils.disposeSWTObjects(dragSource, dropTarget);
					dragSource = null;
					dropTarget = null;
				}
			});
		}
	}

	@Override
	public void delete() {
		destroying = true;
		triggerLifeCycleListener(TableLifeCycleListener.EVENT_TABLELIFECYCLE_DESTROYED);

		if (tvTabsCommon != null) {
			tvTabsCommon.delete();
			tvTabsCommon = null;
		}

		TableStructureEventDispatcher.getInstance(tableID).removeListener(this);

		if (!Utils.isDisplayDisposed()) {
			Utils.disposeSWTObjects(new Object[] {
				cTable
			});
			cTable = null;

			if (filter != null) {
				disableFilterCheck();
			}

			removeAllTableRows();
		}

		configMan.removeParameterListener("ReOrder Delay", this);
		configMan.removeParameterListener("Graphics Update", this);
		configMan.removeParameterListener("Table.extendedErase", this);
		Colors colorInstance = Colors.getInstance();
		if (colorInstance != null) {
			colorInstance.removeColorsChangedListener(this);
		}
		
		header.delete();
		header = null;

		super.delete();

		MessageText.removeListener(this);
		destroying = false;
	}

	@Override
	public void generate(IndentWriter writer) {
		super.generate(writer);

		if (tvTabsCommon != null) {
			tvTabsCommon.generate(writer);
		}
	}

	private Menu createMenu() {
		if (!isMenuEnabled()) {
			return null;
		}

		final Menu menu = new Menu(shell, SWT.POP_UP);
		cTable.addListener(SWT.MenuDetect, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Composite cHeaderArea = header == null ? null : header.getHeaderArea();
				if (event.widget == cHeaderArea) {
					menu.setData(MENUKEY_IN_BLANK_AREA, false);
					menu.setData(MENUKEY_IS_HEADER, true);

				} else {
					boolean noRow;
					if (event.detail == SWT.MENU_KEYBOARD) {
						noRow = getFocusedRow() == null;
					} else {
						TableRowCore row = getTableRowWithCursor();
						noRow = row == null;
	
						// If shell is not active, right clicking on a row will
						// result in a MenuDetect, but not a MouseDown or MouseUp
						if (!isSelected(row)) {
							setSelectedRows(new TableRowCore[] { row });
						}
					}

					menu.setData(MENUKEY_IN_BLANK_AREA, noRow);
					menu.setData(MENUKEY_IS_HEADER, false);
				}
				Point pt = cHeaderArea.toControl(event.x, event.y);
				menu.setData(TableContextMenuItemImpl.MENUKEY_TABLE_VIEW, TableViewPainted.this );
				menu.setData(MENUKEY_COLUMN, getTableColumnByOffset(pt.x));
			}
		});
		if (header != null) {
			header.createMenu(menu);
		}
		MenuBuildUtils.addMaintenanceListenerForMenu(menu,
				new MenuBuildUtils.MenuBuilder() {
					@Override
					public void buildMenu(Menu menu, MenuEvent menuEvent) {
						Object oIsHeader = menu.getData(MENUKEY_IS_HEADER);
						boolean isHeader = (oIsHeader instanceof Boolean)
								? ((Boolean) oIsHeader).booleanValue() : false;

						Object oInBlankArea = menu.getData(MENUKEY_IN_BLANK_AREA);
						boolean inBlankArea = (oInBlankArea instanceof Boolean)
								? ((Boolean) oInBlankArea).booleanValue() : false;

						TableColumnCore column = (TableColumnCore) menu.getData(MENUKEY_COLUMN);

						if (isHeader) {
							tvSWTCommon.fillColumnMenu(menu, column, false);
						} else if (inBlankArea) {
							tvSWTCommon.fillColumnMenu(menu, column, true);
						} else {
							tvSWTCommon.fillMenu(menu, column);
						}

					}
				});

		return menu;
	}

	@Override
	public TableRowCore getFocusedRow() {
		return focusedRow;
	}

	public void setFocusedRow(TableRowCore row) {
		TableRowPainted oldFocusedRow = focusedRow;
		if (!(row instanceof TableRowPainted)) {
			row = null;
		}
		focusedRow = (TableRowPainted) row;
		if (focusedRow != null) {
			if (focusedRow.isVisible()
					&& focusedRow.getDrawOffset().y + focusedRow.getHeight() <= clientArea.y + clientArea.height
					&& focusedRow.getDrawOffset().y >= clientArea.y) {
				// redraw for BG color change
				redrawRow(focusedRow, false);
			} else {

				showRow(focusedRow);
			}
		}
		if (oldFocusedRow != null) {
			redrawRow(oldFocusedRow, false);
		}
	}

	@Override
	public void showRow(final TableRowCore rowToShow) {
		// scrollto
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (isDisposed()) {
					return;
				}

				if (rowToShow.isVisible()) {
					// draw offset is valid, use that to scroll
					int y = ((TableRowPainted) rowToShow).getDrawOffset().y;
					if (y + rowToShow.getHeight() > clientArea.y + clientArea.height) {
						y -= (clientArea.height - rowToShow.getHeight());
					}
					vBar.setSelection(y);
					swt_vBarChanged();
				} else {
					TableRowCore parentFocusedRow = rowToShow;
					while (parentFocusedRow.getParentRowCore() != null) {
						parentFocusedRow = parentFocusedRow.getParentRowCore();
					}
					TableRowCore[] rows = getRows();
					int y = 0;
					for (TableRowCore row : rows) {
						if (row == parentFocusedRow) {
							if (parentFocusedRow != rowToShow) {
								y += row.getHeight();
								TableRowCore[] subRowsWithNull = parentFocusedRow.getSubRowsRecursive( false );
								for (TableRowCore subrow : subRowsWithNull) {
									if (subrow == rowToShow) {
										break;
									}
									y += ((TableRowPainted) subrow).getHeight();
								}
							}
							break;
						}
						y += ((TableRowPainted) row).getFullHeight();
					}

					if (y + rowToShow.getHeight() > clientArea.y + clientArea.height) {
						y -= (clientArea.height - rowToShow.getHeight());
					}
					// y now at top of focused row
					vBar.setSelection(y);
					swt_vBarChanged();
				}
			}
		});
	}

	@Override
	public void scrollVertically(int distance){
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (isDisposed()) {
					return;
				}
				int pos = vBar.getSelection();
				if ( distance > 0 ){
					pos += distance;
					pos = Math.min( pos,vBar.getMaximum());
					vBar.setSelection( pos );
				}else{
					pos += distance;
					pos = Math.max( pos,vBar.getMinimum());
					vBar.setSelection( pos );
				}
				swt_vBarChanged();
			}});
	}
		
	boolean qdRowHeightChanged = false;
	public void rowHeightChanged(final TableRowCore row, int oldHeight,
			int newHeight) {

		synchronized (heightChangeSync) {
			totalHeight += (newHeight - oldHeight);
			//System.out.println("Height delta: " + (newHeight - oldHeight) + ";ttl=" + totalHeight);
			if ( totalHeight < 0 ){
				Debug.out( "eh?" );
			}
			if (qdRowHeightChanged) {
				return;
			}
			qdRowHeightChanged = true;
		}
		Utils.execSWTThreadLater(0, new SWTRunnable() {
			@Override
			public void runWithDisplay(Display display) {
				synchronized (heightChangeSync) {
					qdRowHeightChanged = false;
				}
				// if moving visibleRowsChanged(), make sure subrows being resized on
				// add trigger work properly
				visibleRowsChanged();
				swt_fixupSize();
			}

			@Override
			public void runNoDisplay() {
				synchronized (heightChangeSync) {
					qdRowHeightChanged = false;
				}
			}
		});
	}

	@Override
	public void 
	setRedrawEnabled(
		boolean enabled)
	{
		synchronized (TableViewPainted.this) {
			
			if ( enabled ){
				
				redrawTableDisabled--;
				
			}else{
				
				redrawTableDisabled++;
			}
		}
		
		if ( enabled ){
			
			redrawTable();
		}
	}
	
	public void 
	redrawTable()
	{
		synchronized (TableViewPainted.this) {
			
			if ( redrawTableScheduled || redrawTableDisabled > 0 ){
				
				return;
			}
			
			redrawTableScheduled = true;
		}

		redraw_dispatcher.dispatch();
	}

	private String prettyIndex(TableRowCore row) {
		String s = "" + row.getIndex();
		if (row.getParentRowCore() != null) {
			s = row.getParentRowCore().getIndex() + "." + s;
		}
		return s;
	}

	private List<TableRowPainted>	pending_rows = new ArrayList<>();

	public void redrawRow( TableRowPainted row, final boolean immediateRedraw) {
		if (row == null) {
			return;
		}
		if (TableRowPainted.DEBUG_ROW_PAINT) {
			System.out.println(SystemTime.getCurrentTime() + "} redraw "
					+ prettyIndex(row) + " scheduled via " + Debug.getCompressedStackTrace());
		}

			// optimize multiple row withdraws (e.g. on view construction) so invalidate the
			// aggregate area

		synchronized( visibleRows_sync ){

			pending_rows.add( row );
		}

		Utils.execSWTThread(new SWTRunnable() {

			@Override
			public void runWithDisplay(Display display) {

				List<TableRowPainted>	rows;

				synchronized( visibleRows_sync ){

					if ( pending_rows.size() == 0 ){

						return;
					}

					rows = new ArrayList<>( pending_rows.size());

					for ( TableRowPainted row: pending_rows ){

						if ( row.isVisible()){

							rows.add( row );
						}
					}

					pending_rows.clear();

					if ( !isVisible || rows.size() == 0 ){

						return;
					}
				}

				Rectangle bounds = null;

				boolean	has_last = false;

				for ( TableRowPainted row: rows ){

					Rectangle b = row.getDrawBounds();

					if ( b != null ){

						if ( bounds == null ){

							bounds = b;

						}else{

								// could be smarter here and only aggregate contiguous areas but whatever

							bounds = bounds.union( b );
						}
					}

					if ( !has_last && isLastRow(row)){

						has_last = true;
					}
				}

				if (bounds != null) {
					Composite composite = getComposite();
					if (composite != null && !composite.isDisposed()) {
						int h = has_last ? composite.getSize().y - bounds.y
								: bounds.height;
						//row.debug("isLastRow?" + isLastRow(row) + ";" + bounds + ";" + h);
						swt_updateCanvasImage(new Rectangle(bounds.x, bounds.y, bounds.width, h), immediateRedraw);
					}
				}
			}
		/*
			public void runSupport() {
				if (!isVisible || !row.isVisible()) {
					return;
				}
				Rectangle bounds = row.getDrawBounds();
				if (TableRowPainted.DEBUG_ROW_PAINT) {
					System.out.println(SystemTime.getCurrentTime() + "] redraw "
							+ prettyIndex(row) + " @ " + bounds);
				}
				if (bounds != null) {
					Composite composite = getComposite();
					if (composite != null && !composite.isDisposed()) {
						int h = isLastRow(row) ? composite.getSize().y - bounds.y
								: bounds.height;
						//row.debug("isLastRow?" + isLastRow(row) + ";" + bounds + ";" + h);
						swt_updateCanvasImage(new Rectangle(bounds.x, bounds.y, bounds.width, h), immediateRedraw);
					}
				}
			}
			*/
		});
	}

	public Object getSyncObject() {
		return visibleRows_sync;
	}

	@Override
	public boolean isTableSelected() {
		TableView tv = SelectedContentManager.getCurrentlySelectedTableView();
		return tv == this || (tv == null && isFocused) || (tv != this && tv != null && tv.getSelectedRowsSize() == 0);
	}

	@Override
	protected boolean
	isTableFocused()
	{
		return( isFocused );
	}
	
	public boolean isEnabled() {
		return cTable.isEnabled();
	}

	@Override
	public void bubbleTextBoxChanged(BubbleTextBox bubbleTextBox) {
		boolean changed;
		if (filter != null) {
			changed = bubbleTextBox.isRegexEnabled() != filter.regex;
			if (changed) {
				filter.regex = !filter.regex;
			}
		} else {
			changed = false;
		}
		setFilterText(bubbleTextBox.getText(), changed);
	}
}
