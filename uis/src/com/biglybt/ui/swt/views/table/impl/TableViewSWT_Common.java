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

package com.biglybt.ui.swt.views.table.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.ui.menus.MenuItemImpl;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.table.impl.TableContextMenuManager;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.shells.SpeedScaleShell;
import com.biglybt.ui.swt.views.columnsetup.TableColumnSetupWindow;
import com.biglybt.ui.swt.views.table.*;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pif.ui.tables.*;

public abstract class TableViewSWT_Common
	implements MouseListener, MouseMoveListener, SelectionListener, KeyListener, MenuDetectListener
{

	TableViewSWT<?> tv;
	private long lCancelSelectionTriggeredOn = -1;
	private long lastSelectionTriggeredOn = -1;

	private static final int ASYOUTYPE_UPDATEDELAY = 300;

	private List<TableViewSWTMenuFillListener> listenersMenuFill = new ArrayList<>(
			1);

	private List<KeyListener> listenersKey = new ArrayList<>(1);

	private ArrayList<TableRowMouseListener> rowMouseListeners;

  private static AEMonitor mon_RowMouseListener = new AEMonitor( "rml" );

	private static AEMonitor mon_RowPaintListener = new AEMonitor("rpl");

	public int xAdj = 0;
	public int yAdj = 0;

	private ArrayList<TableRowSWTPaintListener> rowPaintListeners;


	public TableViewSWT_Common(TableViewSWT<?> tv) {
		this.tv = tv;
	}

	long lastMouseDblClkEventTime = 0;
	@Override
	public void mouseDoubleClick(MouseEvent e) {
		long time = e.time & 0xFFFFFFFFL;
		long diff = time - lastMouseDblClkEventTime;
		// We fake a double click on MouseUp.. this traps 2 double clicks
		// in quick succession and ignores the 2nd.
		if (diff <= e.display.getDoubleClickTime() && diff >= 0) {
			return;
		}
		lastMouseDblClkEventTime = time;

		TableColumnCore tc = tv.getTableColumnByOffset(e.x);
		TableCellCore cell = tv.getTableCell(e.x, e.y);
		if (cell != null && tc != null) {
			TableCellMouseEvent event = createMouseEvent(cell, e,
					TableCellMouseEvent.EVENT_MOUSEDOUBLECLICK, false);
			if (event != null) {
				tc.invokeCellMouseListeners(event);
				cell.invokeMouseListeners(event);
				if (event.skipCoreFunctionality) {
					lCancelSelectionTriggeredOn = System.currentTimeMillis();
				}
			}
		}
	}
	
	public void 
	menuDetected(
		MenuDetectEvent e )
	{
		Composite comp = tv.getTableComposite();
		
		Point mapped = comp.getDisplay().map( null, comp, new Point( e.x, e.y ));
		
		TableColumnCore tc = tv.getTableColumnByOffset(mapped.x);
		TableCellCore cell = tv.getTableCell( mapped.x, mapped.y );
		
		if ( tc != null && cell instanceof TableCellSWT ){
			
			TableCellMenuEvent event = new TableCellMenuEvent( cell, e );
			
			Rectangle clientArea = tv.getClientArea();
						
			event.x		= clientArea.x + mapped.x;
			event.y		= mapped.y;
			
			tc.invokeCellMenuListeners( event );
			
			if ( !event.skipCoreFunctionality){
				
				cell.invokeMenuListeners(event);
			}
			
			if ( event.skipCoreFunctionality ){
					
				e.doit = false;
			}
		}
	}

	private boolean
	isInExpando(
		TableRowSWT		row,
		TableCellCore	cell,
		TableColumnCore	tc,
		MouseEvent		e )
	{
		if ( row != null && cell instanceof TableCellSWT ){
			
			Rectangle expando_rect = (Rectangle)row.getData( TableRowCore.ID_EXPANDOHITAREA );
			
			if ( expando_rect != null ){
				
				String expando_column = (String)row.getData( TableRowCore.ID_EXPANDOHITCOLUMN );
				
				if ( expando_column != null && tc != null &&expando_column.equals( tc.getName())){
					
					Rectangle cell_bounds = ((TableCellSWT)cell).getBounds();
					
					expando_rect = new Rectangle( expando_rect.x + cell_bounds.x, expando_rect.y + cell_bounds.y, expando_rect.width, expando_rect.height );
					
					return( expando_rect.contains( e.x, e.y ));
				}
			}
		}
		
		return( false );
	}
	
	long lastMouseUpEventTime = 0;
	Point lastMouseUpPos = new Point(0, 0);
	boolean mouseDown = false;
	TableRowSWT mouseDownOnRow = null;
	@Override
	public void mouseUp(MouseEvent e) {
		// SWT OSX Bug: two mouseup events when app not in focus and user
		// clicks on the table.  Only one mousedown, so track that and ignore
		if (!mouseDown) {
			return;
		}
		mouseDown = false;

		TableColumnCore tc = tv.getTableColumnByOffset(e.x);
		TableCellCore cell = tv.getTableCell(e.x, e.y);
		//TableRowCore row = tv.getTableRow(e.x, e.y, true);
		
		boolean	in_expando = isInExpando( mouseDownOnRow, cell, tc, e );

		if ( in_expando ){
			
			TableCellMouseEvent event = createMouseEvent(cell, e,
					TableCellMouseEvent.EVENT_MOUSEUP, false);
			if (event != null) {
				tc.invokeCellMouseListeners(event);
				cell.invokeMouseListeners(event);
			}
		}else{
			mouseUp(mouseDownOnRow, cell, e.button, e.stateMask);
	
			if (e.button == 1) {
				long time = e.time & 0xFFFFFFFFL;
				long diff = time - lastMouseUpEventTime;
				if (diff <= e.display.getDoubleClickTime() && diff >= 0
						&& lastMouseUpPos.x == e.x && lastMouseUpPos.y == e.y) {
					// Fake double click because Cocoa SWT 3650 doesn't always trigger
					// DefaultSelection listener on a Tree on dblclick (works find in Table)
					
					// parg, 07/02/20 - not sure if this is still an issue but we're sometimes running the default action even though it
					// has been cancelled - reset the cancel indicator if we've had a recent real double click and cancel. obviously crap...
					
					long now = System.currentTimeMillis();
					
					if (	time  - lastMouseDblClkEventTime < 1000 &&
							now - lCancelSelectionTriggeredOn < 1000 ){
						
						lCancelSelectionTriggeredOn = now;
					}
					
					runDefaultAction(e.stateMask, 0 );
					return;
				}
				lastMouseUpEventTime = time;
				lastMouseUpPos = new Point(e.x, e.y);
			}
	
			if (cell != null && tc != null) {
				TableCellMouseEvent event = createMouseEvent(cell, e,
						TableCellMouseEvent.EVENT_MOUSEUP, false);
				if (event != null) {
					tc.invokeCellMouseListeners(event);
					cell.invokeMouseListeners(event);
					if (event.skipCoreFunctionality) {
						lCancelSelectionTriggeredOn = System.currentTimeMillis();
					}
				}
			}
		}
	}

	TableRowCore lastClickRow;

	@Override
	public void mouseDown(MouseEvent e) {
		mouseDown = true;
		// we need to fill the selected row indexes here because the
		// dragstart event can occur before the SWT.SELECTION event and
		// our drag code needs to know the selected rows..
		TableRowSWT row = mouseDownOnRow = tv.getTableRow(e.x, e.y, false);
		TableCellCore cell = tv.getTableCell(e.x, e.y);
		TableColumnCore tc = cell == null ? null : cell.getTableColumnCore();

		boolean	in_expando = isInExpando( row, cell, tc, e );
		
		if ( in_expando ){
		
			TableCellMouseEvent event = createMouseEvent(cell, e,
					TableCellMouseEvent.EVENT_MOUSEDOWN, false);
			if (event != null) {
				tc.invokeCellMouseListeners(event);
				cell.invokeMouseListeners(event);
			}
		}else{
			
			mouseDown(row, cell, e.button, e.stateMask);
	
			if (row == null) {
				tv.setSelectedRows(new TableRowCore[0]);
			}
	
			tv.editCell(null, -1); // clear out current cell editor
	
			if (cell != null && tc != null) {
				TableCellMouseEvent event = createMouseEvent(cell, e,
						TableCellMouseEvent.EVENT_MOUSEDOWN, false);
				if (event != null) {
					tc.invokeCellMouseListeners(event);
					cell.invokeMouseListeners(event);
					tv.invokeRowMouseListener(event);
					if (event.skipCoreFunctionality) {
						lCancelSelectionTriggeredOn = System.currentTimeMillis();
					}
				}
				if (tc.hasInplaceEditorListener() && e.button == 1
						&& lastClickRow == cell.getTableRowCore()) {
					tv.editCell(tv.getTableColumnByOffset(e.x), cell.getTableRowCore().getIndex());
				}
				if (e.button == 1) {
					lastClickRow = cell.getTableRowCore();
				}
			} else if (row != null) {
				TableRowMouseEvent event = createMouseEvent(row, e,
						TableCellMouseEvent.EVENT_MOUSEDOWN, false);
				if (event != null) {
					tv.invokeRowMouseListener(event);
				}
			}
		}
	}

	public void mouseDown(TableRowSWT row, TableCellCore cell, int button,
			int stateMask) {
	}

	public void mouseUp(TableRowCore row, TableCellCore cell, int button,
			int stateMask) {
	}

	private TableCellMouseEvent createMouseEvent(TableCellCore cell, MouseEvent e,
			int type, boolean allowOOB) {
		TableCellMouseEvent event = new TableCellMouseEvent( e );
		event.cell = cell;
		if (cell != null) {
			event.row = cell.getTableRow();
		}
		event.eventType = type;
		event.button = e.button;
		// TODO: Change to not use SWT masks
		event.keyboardState = e.stateMask;
		event.skipCoreFunctionality = false;
		if (cell instanceof TableCellSWT) {
			Rectangle r = ((TableCellSWT) cell).getBounds();
			if (r == null) {
				return event;
			}
			event.x = e.x - r.x - xAdj;
			if (!allowOOB && event.x < 0) {
				return null;
			}
			event.y = e.y - r.y - yAdj;
			if (!allowOOB && event.y < 0) {
				return null;
			}
		}

		return event;
	}

	private TableRowMouseEvent createMouseEvent(TableRowSWT row, MouseEvent e,
			int type, boolean allowOOB) {
		TableCellMouseEvent event = new TableCellMouseEvent( e );
		event.row = row;
		event.eventType = type;
		event.button = e.button;
		// TODO: Change to not use SWT masks
		event.keyboardState = e.stateMask;
		event.skipCoreFunctionality = false;
		if (row != null) {
			Rectangle r = row.getBounds();
			event.x = e.x - r.x - xAdj;
			if (!allowOOB && event.x < 0) {
				return null;
			}
			event.y = e.y - r.y - yAdj;
			if (!allowOOB && event.y < 0) {
				return null;
			}
		}

		return event;
	}



	TableCellCore lastCell = null;

	int lastCursorID = 0;

	@Override
	public void mouseMove(MouseEvent e) {
		lCancelSelectionTriggeredOn = -1;
		if (tv.isDragging()) {
			return;
		}
		try {
			TableCellCore cell = tv.getTableCell(e.x, e.y);

			if (cell != lastCell) {
  			if (lastCell != null && !lastCell.isDisposed()) {
  				TableCellMouseEvent event = createMouseEvent(lastCell, e,
  						TableCellMouseEvent.EVENT_MOUSEEXIT, true);
  				if (event != null) {
  					((TableCellSWT) lastCell).setMouseOver(false);
  					TableColumnCore tc = ((TableColumnCore) lastCell.getTableColumn());
  					if (tc != null) {
  						tc.invokeCellMouseListeners(event);
  					}
  					lastCell.invokeMouseListeners(event);
  				}
  			}

  			if (cell != null && !cell.isDisposed()) {
  				TableCellMouseEvent event = createMouseEvent(cell, e,
  						TableCellMouseEvent.EVENT_MOUSEENTER, false);
  				if (event != null) {
  					((TableCellSWT) cell).setMouseOver(true);
  					TableColumnCore tc = ((TableColumnCore) cell.getTableColumn());
  					if (tc != null) {
  						tc.invokeCellMouseListeners(event);
  					}
  					cell.invokeMouseListeners(event);
  				}
  			}
			}

			int iCursorID = 0;
			if (cell == null) {
				lastCell = null;
			} else if (cell == lastCell) {
				iCursorID = lastCursorID;
			} else {
				iCursorID = cell.getCursorID();
				lastCell = cell;
			}

			if (iCursorID < 0) {
				iCursorID = 0;
			}

			if (iCursorID != lastCursorID) {
				lastCursorID = iCursorID;

				if (iCursorID >= 0) {
					tv.getComposite().setCursor(tv.getComposite().getDisplay().getSystemCursor(iCursorID));
				} else {
					tv.getComposite().setCursor(null);
				}
			}

			if (cell != null) {
				TableCellMouseEvent event = createMouseEvent(cell, e,
						TableCellMouseEvent.EVENT_MOUSEMOVE, false);
				if (event != null) {
					TableColumnCore tc = ((TableColumnCore) cell.getTableColumn());
						// seen a null tc here before
					if (tc != null && tc.hasCellMouseMoveListener()) {
						((TableColumnCore) cell.getTableColumn()).invokeCellMouseListeners(event);
					}
					cell.invokeMouseListeners(event);

					// listener might have changed it

					iCursorID = cell.getCursorID();
					if (iCursorID != lastCursorID) {
						lastCursorID = iCursorID;

						if (iCursorID >= 0) {
							tv.getComposite().setCursor(tv.getComposite().getDisplay().getSystemCursor(iCursorID));
						} else {
							tv.getComposite().setCursor(null);
						}
					}
				}
			}
		} catch (Exception ex) {
			Debug.out(ex);
		}
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		if (lCancelSelectionTriggeredOn > 0
				&& System.currentTimeMillis() - lCancelSelectionTriggeredOn < 200) {
			e.doit = false;
		} else {
			runDefaultAction(e.stateMask, 0);
		}
	}

	@Override
	public void keyPressed(KeyEvent event) {
		// Note: Both table key presses and txtFilter keypresses go through this
		//       method.

		TableViewSWTFilter<?> filter = tv.getSWTFilter();
		boolean inFilterBox = filter != null && filter.filterBox != null
				&& filter.filterBox.isOurWidget(event.widget);
		if (inFilterBox) {
			if (event.character == SWT.DEL || event.character == SWT.BS) {
				handleSearchKeyPress(event);
				return;
			}
		}

		KeyListener[] listeners = tv.getKeyListeners();
		for (KeyListener l : listeners) {
			l.keyPressed(event);
			if (!event.doit) {
				lCancelSelectionTriggeredOn = SystemTime.getCurrentTime();
				return;
			}
		}

		if (event.keyCode == SWT.F5) {
			if ((event.stateMask & SWT.SHIFT) != 0) {
				tv.runForSelectedRows(new TableGroupRowRunner() {
					@Override
					public void run(TableRowCore row) {
						row.invalidate();
						row.refresh(true);
					}
				});
			} else if ((event.stateMask & SWT.CONTROL) != 0) {
				tv.runForAllRows(new TableGroupRowRunner() {
					@Override
					public void run(TableRowCore row) {
						row.invalidate();
						row.refresh(true);
					}
				});
			} else {
				tv.sortRows(true,false);
			}
			event.doit = false;
			return;
		}

		int key = event.character;
		if (key <= 26 && key > 0) {
			key += 'a' - 1;
		}

		if (event.stateMask == SWT.MOD1) {
			switch (key) {
				case 'a': { // CTRL+A select all Torrents
					if (filter == null || !inFilterBox) {
						if (!tv.isSingleSelection()) {
							tv.selectAll();
							event.doit = false;
						}
					}
					break;
				}
				case 'c': { // CTRL+C
				
					tv.clipboardSelected();
					event.doit = false;
					
					break;
				}
				case '+': {
					if (Constants.isUnix) {
						tv.expandColumns();
						event.doit = false;
					}
					break;
				}
				case 'f': // CTRL+F Find/Filter
					tv.openFilterDialog();
					event.doit = false;
					break;
				case 'g':
					System.out.println("force sort");
					tv.resetLastSortedOn();
					tv.sortRows(true,false);
					break;
				case 'v':
					if ( filter == null || !inFilterBox){
						
						Clipboard clipboard = new Clipboard(Display.getDefault());
						
						try{
							String text = (String) clipboard.getContents(TextTransfer.getInstance());
	
							if (text != null && text.length() <= 2048) {
								
								TorrentOpener.openTorrentsFromClipboard(text);
							}
						}finally{
							
							clipboard.dispose();
						}
					}
					break;
			}

		}

		if (event.stateMask == 0) {
			if (filter != null && inFilterBox) {
				if (event.keyCode == SWT.ARROW_DOWN) {
					tv.requestFocus( 2 );
						// for detailed library views we want to give the seeding section a chance to grab
						// focus if the incomplete section is empty - code below allows the 'other' view a chance
						// to do this
					int rows = tv.getRowCount();
					if ( rows > 0 ){
						event.doit = false;
					}
				} else if (event.character == 13) {
					tv.refilter();
				}
			}else{
				if ( event.keyCode == SWT.CR || event.keyCode == SWT.KEYPAD_CR){
					
					runDefaultAction( 0, 1 );
					
					return;
				}
			}
		}

		if (!event.doit) {
			return;
		}

		handleSearchKeyPress(event);
	}

	private void handleSearchKeyPress(KeyEvent e) {
		TableViewSWTFilter<?> filter = tv.getSWTFilter();
		if (filter == null || (filter.filterBox != null
				&& filter.filterBox.isOurWidget(e.widget))) {
			return;
		}

		String newText = null;

		if (e.keyCode == SWT.BS) {
			if (e.stateMask == SWT.CONTROL) {
				newText = "";
			} else if (filter.nextText.length() > 0) {
				newText = filter.nextText.substring(0, filter.nextText.length() - 1);
			}
		} else if ((e.stateMask & ~SWT.SHIFT) == 0 && e.character > 32 && e.character != SWT.DEL) {
			newText = filter.nextText + String.valueOf(e.character);
		}

		if (newText == null) {
			return;
		}

		if (filter.filterBox != null && !filter.filterBox.isDisposed()) {
			filter.filterBox.setFocus();
		}
		tv.setFilterText(newText, false);
		e.doit = false;
	}

	public void setFilterText(String s, boolean force) {
		TableViewSWTFilter<?> filter = tv.getSWTFilter();
		if (filter == null) {
			return;
		}
		filter.nextText = s;
		if (filter.filterBox != null && !filter.filterBox.isDisposed()) {
			if (!filter.nextText.equals(filter.filterBox.getText())) {
				filter.filterBox.setText(filter.nextText);
				filter.filterBox.setSelection(filter.nextText.length());
			}
		}

		if (filter.eventUpdate != null) {
			filter.eventUpdate.cancel();
		}
		filter.eventUpdate = SimpleTimer.addEvent("SearchUpdate",
				SystemTime.getOffsetTime(ASYOUTYPE_UPDATEDELAY),
				new TimerEventPerformer() {
					@Override
					public void perform(TimerEvent event) {
						TableViewSWTFilter<?> filter = tv.getSWTFilter();

						if (filter == null) {
							return;
						}
						if (filter.eventUpdate == null || filter.eventUpdate.isCancelled()) {
							filter.eventUpdate = null;
							return;
						}
						filter.eventUpdate = null;
						if (filter.nextText != null
								&& (force || !filter.nextText.equals(filter.text))) {
							filter.text = filter.nextText;
							filter.checker.filterSet(filter.text);
							tv.refilter();
						}
					}
				});
	}

	public void runDefaultAction(int stateMask, int origin ) {
		// Don't allow mutliple run defaults in quick succession
		if (lastSelectionTriggeredOn > 0
				&& System.currentTimeMillis() - lastSelectionTriggeredOn < 200) {
			return;
		}

		// plugin may have cancelled the default action
		if (System.currentTimeMillis() - lCancelSelectionTriggeredOn > 200) {
			lastSelectionTriggeredOn = System.currentTimeMillis();
			TableRowCore[] selectedRows = tv.getSelectedRows();
			tv.triggerDefaultSelectedListeners(selectedRows, stateMask, origin );
		}
	}

	@Override
	public void keyReleased(KeyEvent event) {
		KeyListener[] listeners = tv.getKeyListeners();
		for (KeyListener l : listeners) {
			l.keyReleased(event);
			if (!event.doit) {
				return;
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#addKeyListener(org.eclipse.swt.events.KeyListener)
	 */
	public void addKeyListener(KeyListener listener) {
		if (listenersKey.contains(listener)) {
			return;
		}

		listenersKey.add(listener);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#removeKeyListener(org.eclipse.swt.events.KeyListener)
	 */
	public void removeKeyListener(KeyListener listener) {
		listenersKey.remove(listener);
	}

	public KeyListener[] getKeyListeners() {
		return listenersKey.toArray(new KeyListener[0]);
	}

	public void addRowMouseListener(TableRowMouseListener listener) {
		try {
			mon_RowMouseListener.enter();

			if (rowMouseListeners == null)
				rowMouseListeners = new ArrayList<>(1);

			rowMouseListeners.add(listener);

		} finally {
			mon_RowMouseListener.exit();
		}
	}

	public void removeRowMouseListener(TableRowMouseListener listener) {
		try {
			mon_RowMouseListener.enter();

			if (rowMouseListeners == null)
				return;

			rowMouseListeners.remove(listener);

		} finally {
			mon_RowMouseListener.exit();
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#invokeRowMouseListener(com.biglybt.pif.ui.tables.TableRowMouseEvent)
	 */
	public void invokeRowMouseListener(TableRowMouseEvent event) {
		if (rowMouseListeners == null) {
			return;
		}
		ArrayList<TableRowMouseListener> listeners = new ArrayList<>(
				rowMouseListeners);

		for (int i = 0; i < listeners.size(); i++) {
			try {
				TableRowMouseListener l = (listeners.get(i));

				l.rowMouseTrigger(event);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#addRowPaintListener(com.biglybt.ui.swt.views.table.TableRowSWTPaintListener)
	 */
	public void addRowPaintListener(TableRowSWTPaintListener listener) {
		try {
			mon_RowPaintListener.enter();

			if (rowPaintListeners == null)
				rowPaintListeners = new ArrayList<>(1);

			rowPaintListeners.add(listener);

		} finally {
			mon_RowPaintListener.exit();
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableViewSWT#removeRowPaintListener(com.biglybt.ui.swt.views.table.TableRowSWTPaintListener)
	 */
	public void removeRowPaintListener(TableRowSWTPaintListener listener) {
		try {
			mon_RowPaintListener.enter();

			if (rowPaintListeners == null)
				return;

			rowPaintListeners.remove(listener);

		} finally {
			mon_RowPaintListener.exit();
		}
	}

	public void invokePaintListeners(GC gc, TableRowCore row,
			TableColumnCore column, Rectangle cellArea) {

		if (rowPaintListeners == null) {
			return;
		}
		ArrayList<TableRowSWTPaintListener> listeners = new ArrayList<>(
				rowPaintListeners);

		for (int i = 0; i < listeners.size(); i++) {
			try {
				TableRowSWTPaintListener l = (listeners.get(i));

				l.rowPaint(gc, row, column, cellArea);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	/** Fill the Context Menu with items.  Called when menu is about to be shown.
	 *
	 * By default, a "Edit Columns" menu and a Column specific menu is set up.
	 *
	 * @param menu Menu to fill
	 * @param tcColumn
	 */
	public void fillMenu(final Menu menu, final TableColumnCore column) {
		String columnName = column == null ? null : column.getName();

		Object[] listeners = listenersMenuFill.toArray();
		for (int i = 0; i < listeners.length; i++) {
			TableViewSWTMenuFillListener l = (TableViewSWTMenuFillListener) listeners[i];
			l.fillMenu(columnName, menu);
		}

		boolean hasLevel1 = false;
		boolean hasLevel2 = false;
		// quick hack so we don't show plugin menus on selections of subitems
		TableRowCore[] selectedRows = tv.getSelectedRows();
		for (TableRowCore row : selectedRows) {
			if (row.getParentRowCore() != null) {
				hasLevel2 = true;
			} else {
				hasLevel1 = true;
			}
		}

		String tableID = tv.getTableID();
		String sMenuID = hasLevel1 ? tableID : TableManager.TABLE_TORRENT_FILES;

		// We'll add download-context specific menu items - if the table is download specific.
		// We need a better way to determine this...
		boolean isDownloadContext;
		com.biglybt.pif.ui.menus.MenuItem[] menu_items = null;
		if (Download.class.isAssignableFrom( tv.getDataSourceType()) && !hasLevel2) {
			menu_items = MenuItemManager.getInstance().getAllAsArray(
					MenuManager.MENU_DOWNLOAD_CONTEXT);
			isDownloadContext = true;
		} else {
			menu_items = MenuItemManager.getInstance().getAllAsArray((String) null);
			isDownloadContext = false;
		}

		if (columnName == null) {
			MenuItem itemChangeTable = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemChangeTable,
					"MyTorrentsView.menu.editTableColumns");
			Utils.setMenuItemImage(itemChangeTable, "columns");

			itemChangeTable.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					showColumnEditor( column );
				}
			});

		} else {

 		MenuItem item = new MenuItem(menu, SWT.PUSH);
 		Messages.setLanguageText(item, "MyTorrentsView.menu.thisColumn.toClipboard");
 		item.addListener(SWT.Selection, new Listener() {
 			@Override
		  public void handleEvent(Event e) {
 				String sToClipboard = "";
 				if (column == null) {
 					return;
 				}
 				String columnName = column.getName();
 				if (columnName == null) {
 					return;
 				}
 				TableRowCore[] rows = tv.getSelectedRows();
 				for (TableRowCore row : rows) {
 					if (row != rows[0]) {
 						sToClipboard += "\n";
 					}
 					TableCellCore cell = row.getTableCellCore(columnName);
 					if (cell != null) {
 						sToClipboard += cell.getClipboardText();
 					}
 				}
 				if (sToClipboard.length() == 0) {
 					return;
 				}
 				new Clipboard(Display.getDefault()).setContents(new Object[] {
 					sToClipboard
 				}, new Transfer[] {
 					TextTransfer.getInstance()
 				});
 			}
 		});
		}


		// Add Plugin Context menus..
		boolean enable_items = selectedRows.length > 0;

		TableContextMenuItem[] items = TableContextMenuManager.getInstance().getAllAsArray(
				Utils.getBaseViewID(sMenuID));

		if (items.length > 0 || menu_items.length > 0) {
			new org.eclipse.swt.widgets.MenuItem(menu, SWT.SEPARATOR);

			// Add download context menu items.
			if (menu_items != null) {
				// getSelectedDataSources(false) returns us plugin items.
				Object[] target;
				if (isDownloadContext) {
					Object[] dataSources = tv.getSelectedDataSources(false);
					target = new Download[dataSources.length];
					System.arraycopy(dataSources, 0, target, 0, target.length);
				} else {
					target = selectedRows;
				}
				MenuBuildUtils.addPluginMenuItems(menu_items, menu, true, true,
						new MenuBuildUtils.MenuItemPluginMenuControllerImpl(target));
			}

			if (items.length > 0) {
				MenuBuildUtils.addPluginMenuItems(items, menu, true, enable_items,
						new MenuBuildUtils.PluginMenuController() {
							@Override
							public Listener makeSelectionListener(
									final com.biglybt.pif.ui.menus.MenuItem plugin_menu_item) {
								return new TableSelectedRowsListener(tv, false) {
									@Override
									public boolean run(TableRowCore[] rows) {
										if (rows.length != 0) {
											((MenuItemImpl) plugin_menu_item).invokeListenersMulti(rows);
										}
										return true;
									}
								};
							}

							@Override
							public void notifyFillListeners(
									com.biglybt.pif.ui.menus.MenuItem menu_item) {
								((MenuItemImpl)menu_item).invokeMenuWillBeShownListeners(tv.getSelectedRows());
							}

							// @see com.biglybt.ui.swt.MenuBuildUtils.PluginMenuController#buildSubmenu(com.biglybt.pif.ui.menus.MenuItem)
							@Override
							public void buildSubmenu(
									com.biglybt.pif.ui.menus.MenuItem parent) {
								com.biglybt.pif.ui.menus.MenuBuilder submenuBuilder = ((MenuItemImpl) parent).getSubmenuBuilder();
								if (submenuBuilder != null) {
									try {
										parent.removeAllChildItems();
										submenuBuilder.buildSubmenu(parent, tv.getSelectedRows());
									} catch (Throwable t) {
										Debug.out(t);
									}
								}
							}
							
							@Override
							public void buildStarts(Menu menu){
							}
							
							@Override
							public void buildComplete(Menu menu){
							}
						});
			}
		}

		if (hasLevel1) {
			// Add Plugin Context menus..
			if (column != null) {
				TableContextMenuItem[] columnItems = column.getContextMenuItems(TableColumnCore.MENU_STYLE_COLUMN_DATA);
				if (columnItems.length > 0) {
					new MenuItem(menu, SWT.SEPARATOR);

					MenuBuildUtils.addPluginMenuItems(
							columnItems,
							menu,
							true,
							true,
							new MenuBuildUtils.MenuItemPluginMenuControllerImpl(
									tv.getSelectedDataSources(true)));

				}
			}

			final MenuItem itemSelectAll = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemSelectAll, "Button.selectAll");
			itemSelectAll.addListener(SWT.Selection, (ev)->{
				tv.selectAll();
			});
			
			if (tv.getSWTFilter() != null) {
				final MenuItem itemFilter = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(itemFilter, "MyTorrentsView.menu.filter");
				itemFilter.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						tv.openFilterDialog();
					}
				});
			}
			
			MenuItem itemChangeTable = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemChangeTable,
					"MyTorrentsView.menu.editTableColumns");
			Utils.setMenuItemImage(itemChangeTable, "columns");

			itemChangeTable.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					showColumnEditor( column );
				}
			});
		}
	}

	public void showColumnEditor(TableColumnCore column) {
		TableRowCore focusedRow = tv.getFocusedRow();
		if (focusedRow == null || focusedRow.isRowDisposed()) {
			focusedRow = tv.getRow(0);
		}
		String tableID = tv.getTableID();
		new TableColumnSetupWindow(tv.getDataSourceType(), tableID, column, focusedRow,
				TableStructureEventDispatcher.getInstance(tableID)).open();
	}


	/**
	 * SubMenu for column specific tasks.
	 *
	 * @param iColumn Column # that tasks apply to.
	 */
	public void fillColumnMenu(final Menu menu, final TableColumnCore column,
			boolean isBlankArea) {
		if (column != null) {

			String sColumnName = column.getName();
			if (sColumnName != null) {
				Object[] listeners = listenersMenuFill.toArray();
				for (int i = 0; i < listeners.length; i++) {
					TableViewSWTMenuFillListener l = (TableViewSWTMenuFillListener) listeners[i];
					l.addThisColumnSubMenu(sColumnName, menu);
				}
			}

			// Add Plugin Context menus..
			TableContextMenuItem[] items = column.getContextMenuItems(TableColumnCore.MENU_STYLE_HEADER);
			if (items.length > 0) {
				MenuBuildUtils.addSeparator( menu );

				MenuBuildUtils.addPluginMenuItems(items, menu, true, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(
						tv.getSelectedDataSources(true)));

			}

			MenuBuildUtils.addSeparator( menu );

			TableColumnCore[] sortColumns = tv.getSortColumns();
			boolean columnIsSort = false;
			for (TableColumnCore sortColumn : sortColumns) {
				if (sortColumn == column) {
					columnIsSort = true;
					break;
				}
			}
			MenuItem menuSortByColumn = new MenuItem(menu, SWT.PUSH);
			if (columnIsSort) {
				Messages.setLanguageText(menuSortByColumn, "TableColumn.sort.reverse");
				menuSortByColumn.addListener(SWT.Selection, e -> {
					TableColumnCore tcc = (TableColumnCore) menu.getData("column");
					tv.addSortColumn( tcc ); // this flips sort if already added and does history...
				});
				if (sortColumns.length > 1) {
					MenuItem menuRemoveSort = new MenuItem(menu, SWT.PUSH);
					Messages.setLanguageText(menuRemoveSort, "TableColumn.sort.remove");
					menuRemoveSort.addListener(SWT.Selection, e -> {
						TableColumnCore tcc = (TableColumnCore) menu.getData("column");
						List<TableColumnCore> list = new ArrayList<>(
								Arrays.asList(tv.getSortColumns()));
						list.remove(tcc);
	
						tv.setSortColumns(list.toArray(new TableColumnCore[0]), false);
					});
				}
			} else {
				Messages.setLanguageText(menuSortByColumn, "TableColumn.sort.bythiscolumn");
				menuSortByColumn.addListener(SWT.Selection, e -> {
					TableColumnCore tcc = (TableColumnCore) menu.getData("column");
					tv.setSortColumns(new TableColumnCore[] { tcc }, true);
				});
				MenuItem menuSortAddColumn = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(menuSortAddColumn, "TableColumn.sort.addcolumn");
				menuSortAddColumn.addListener(SWT.Selection, e -> {
					TableColumnCore tcc = (TableColumnCore) menu.getData("column");
					tv.addSortColumn(tcc);
				});
			}

			new MenuItem(menu, SWT.SEPARATOR);

			if (TableTooltips.tooltips_disabled) {
				MenuItem at_item = new MenuItem(menu, SWT.CHECK);
				Messages.setLanguageText(at_item,
					"MyTorrentsView.menu.thisColumn.autoTooltip");
				at_item.addListener(SWT.Selection, e -> {
					TableColumnCore tcc = (TableColumnCore) menu.getData("column");
					tcc.setAutoTooltip(at_item.getSelection());
					tcc.invalidateCells();
				});
				at_item.setSelection(column.doesAutoTooltip());
			}

			final MenuItem renameColumn = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(renameColumn,
					"MyTorrentsView.menu.renameColumn");

			renameColumn.addListener(SWT.Selection, e -> {
				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"ColumnRenameWindow.title", "ColumnRenameWindow.message");

				String	existing_name = column.getNameOverride();
				if ( existing_name == null ){
					existing_name = "";
				}
				entryWindow.setPreenteredText( existing_name, false );
				entryWindow.selectPreenteredText( true );

				entryWindow.prompt(ew -> {
					if (!ew.hasSubmittedInput()) {
						return;
					}

					String name = ew.getSubmittedInput().trim();

					if (name.length() == 0) {
						name = null;
					}
					column.setNameOverride(name);
					TableColumnManager tcm = TableColumnManager.getInstance();
					String tableID = tv.getTableID();
					tcm.saveTableColumns(tv.getDataSourceType(), tableID);
					TableStructureEventDispatcher.getInstance(
							tableID).tableStructureChanged(true, null);
				});

			});
			
			final MenuItem itemPrefSize = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemPrefSize, "table.columns.pref.size");
			itemPrefSize.addListener(SWT.Selection, e -> Utils.execSWTThread(() -> {
				column.setPreferredWidth(-1);

				tv.runForAllRows(new TableGroupRowRunner() {
					@Override
					public void run(TableRowCore row) {
						row.fakeRedraw( column.getName());
					}
				});

				int pref = column.getPreferredWidth();

				if ( pref != -1 ){

					column.setWidth( pref );
				}
			}));

			new MenuItem(menu, SWT.SEPARATOR);

			MenuItem menuHideColumn = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(menuHideColumn, "TableColumn.hide");
			menuHideColumn.addListener(SWT.Selection,
					e -> TableColumnSWTUtils.changeColumnVisiblity(tv, column, false));
		}

		final MenuItem itemResetColumns = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemResetColumns, "table.columns.reset");
		itemResetColumns.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				
				MessageBoxShell mb =
						new MessageBoxShell(
							MessageText.getString("table.columns.reset.dialog.title"),
							MessageText.getString("table.columns.reset.dialog.text"),
							new String[] {
								MessageText.getString("Button.yes"),
								MessageText.getString("Button.no")
							},
							1 );

				mb.open(new UserPrompterResultListener() {
					@Override
					public void prompterClosed(int result) {
						if (result == 0) {
							String tableID = tv.getTableID();
							TableColumnManager tcm = TableColumnManager.getInstance();
							tcm.resetColumns(tv.getDataSourceType(), tableID);
						}
					}});
			}
		});

		final MenuItem itemChangeTable = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemChangeTable,
				"MyTorrentsView.menu.editTableColumns");
		Utils.setMenuItemImage(itemChangeTable, "columns");

		itemChangeTable.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				showColumnEditor( column );
			}
		});

		menu.setData("column", column);

		new MenuItem(menu, SWT.SEPARATOR);
		final MenuItem itemSetRowHeight = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemSetRowHeight, "Table.menu.set.height");
		itemSetRowHeight.addListener(SWT.Selection, event -> showRowHeightDialog());
	}

	private void showRowHeightDialog() {
		int originalHeight = tv.getRowDefaultHeight();
		SpeedScaleShell optionShell = new SpeedScaleShell() {
			@Override
			public void setValue(int value) {
				super.setValue(value);
				Utils.execSWTThreadLater(100, () -> tv.setRowHeight(getValue()));
			}

			@Override
			public String getStringValue(int value, String sValue) {
				if (sValue != null) {
					return sValue;
				}
				return "" + value + " px";
			}
		};
		optionShell.setMinValue(tv.getRowMinHeight());
		int lineHeight = tv.getLineHeight();
		optionShell.setMaxValue(lineHeight * 8 + 2);
		for (int i = 1; i < 5; i++) {
			optionShell.addOption(i == 1 ? MessageText.getString("Table.line.one")
					: MessageText.getString("Table.line.many", new String[] {
						String.valueOf(i)
					}), i * lineHeight + 2);
		}
		if (optionShell.open(null, tv.getRowDefaultHeight(), false)) {
			tv.setRowHeight(optionShell.getValue());
		} else {
			tv.setRowHeight(originalHeight);
		}
	}

	public void addMenuFillListener(TableViewSWTMenuFillListener l) {
		listenersMenuFill.add(l);
	}

}
