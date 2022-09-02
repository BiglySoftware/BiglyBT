/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.table.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;

import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.swt.utils.ColorCache;

import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl;
import com.biglybt.pifimpl.local.download.DownloadManagerImpl;
import com.biglybt.pifimpl.local.peers.PeerManagerImpl;
import com.biglybt.pifimpl.local.tracker.TrackerTorrentImpl;

/**
 * @author TuxPaper
 * @created Aug 29, 2007
 *
 */
public class FakeTableCell
	implements TableCellSWT, PaintListener, MouseListener, MouseMoveListener,
	MouseTrackListener
{
	private AEMonitor this_mon = new AEMonitor("FakeTableCell");

	private ArrayList refreshListeners;

	private ArrayList disposeListeners;

	private ArrayList tooltipListeners;

	private ArrayList cellMouseListeners;

	private ArrayList cellMouseMoveListeners;

	private ArrayList cellVisibilityListeners;

	private ArrayList<TableCellClipboardListener> cellClipboardListeners;

	private Image image;

	private Rectangle imageBounds;

	private int marginHeight;

	private int orientation;

	private int marginWidth;

	private Comparable sortValue;

	private Object coreDataSource;

	private Composite composite;

	private final TableColumnCore tableColumn;

	private Graphic graphic;

	private String text;

	private Object pluginDataSource;

	private Object tooltip;
	private Object default_tooltip;

	private Rectangle cellArea;

	private boolean hadMore;

	private boolean wrapText	= true;

	private ArrayList cellSWTPaintListeners;

	private boolean valid;

	private TableRow fakeRow = null;

	private Map<Object,Object>	userData;
	
	/**
	 * @param columnRateUpDown
	 */
	public FakeTableCell(TableColumn column, Object ds) {
		valid = false;
		coreDataSource = ds;
		this.tableColumn = (TableColumnCore) column;
		setOrientationViaColumn();
		tableColumn.invokeCellAddedListeners(this);
	}

	public FakeTableCell(TableColumnCore column, Object ds) {
		valid = false;
		coreDataSource = ds;
		this.tableColumn = column;
		setOrientationViaColumn();
		tableColumn.invokeCellAddedListeners(this);
	}

	@Override
	public void addRefreshListener(TableCellRefreshListener listener) {
		try {
			this_mon.enter();

			if (refreshListeners == null)
				refreshListeners = new ArrayList(1);

			refreshListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void removeRefreshListener(TableCellRefreshListener listener) {
		try {
			this_mon.enter();

			if (refreshListeners == null)
				return;

			refreshListeners.remove(listener);
		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void addDisposeListener(TableCellDisposeListener listener) {
		try {
			this_mon.enter();

			if (disposeListeners == null) {
				disposeListeners = new ArrayList(1);
			}
			disposeListeners.add(listener);
		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void removeDisposeListener(TableCellDisposeListener listener) {
		try {
			this_mon.enter();

			if (disposeListeners == null)
				return;

			disposeListeners.remove(listener);

		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void addToolTipListener(TableCellToolTipListener listener) {
		try {
			this_mon.enter();

			if (tooltipListeners == null) {
				tooltipListeners = new ArrayList(1);
			}
			tooltipListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void removeToolTipListener(TableCellToolTipListener listener) {
		try {
			this_mon.enter();

			if (tooltipListeners == null)
				return;

			tooltipListeners.remove(listener);
		} finally {

			this_mon.exit();
		}
	}

	@Override
	public void addMouseListener(TableCellMouseListener listener) {
		try {
			this_mon.enter();

			if (cellMouseListeners == null)
				cellMouseListeners = new ArrayList(1);

			cellMouseListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void removeMouseListener(TableCellMouseListener listener) {
		try {
			this_mon.enter();

			if (cellMouseListeners == null)
				return;

			cellMouseListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public void 
	addMenuListener(
		TableCellMenuListener listener)
	{
	}
	
	@Override
	public void
	removeMenuListener(
		TableCellMenuListener listener)
	{
	}
	
	public void addMouseMoveListener(TableCellMouseMoveListener listener) {
		try {
			this_mon.enter();

			if (cellMouseMoveListeners == null)
				cellMouseMoveListeners = new ArrayList(1);

			cellMouseMoveListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void removeMouseMoveListener(TableCellMouseMoveListener listener) {
		try {
			this_mon.enter();

			if (cellMouseMoveListeners == null)
				return;

			cellMouseMoveListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void addVisibilityListener(TableCellVisibilityListener listener) {
		try {
			this_mon.enter();

			if (cellVisibilityListeners == null)
				cellVisibilityListeners = new ArrayList(1);

			cellVisibilityListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void removeVisibilityListener(TableCellVisibilityListener listener) {
		try {
			this_mon.enter();

			if (cellVisibilityListeners == null)
				return;

			cellVisibilityListeners.remove(listener);

		} finally {
			this_mon.exit();
		}
	}

	/**
	 * @param listenerObject
	 *
	 * @since 4.0.0.1
	 */
	private void addSWTPaintListener(TableCellSWTPaintListener listener) {
		try {
			this_mon.enter();

			if (cellSWTPaintListeners == null)
				cellSWTPaintListeners = new ArrayList(1);

			cellSWTPaintListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	public void invokeSWTPaintListeners(GC gc) {
		if (getBounds().isEmpty()) {
			return;
		}
  	if (tableColumn != null) {
			Object[] swtPaintListeners = tableColumn.getCellOtherListeners("SWTPaint");
			if (swtPaintListeners != null) {
  			for (int i = 0; i < swtPaintListeners.length; i++) {
  				try {
  					TableCellSWTPaintListener l = (TableCellSWTPaintListener) swtPaintListeners[i];

  					l.cellPaint(gc, this);

  				} catch (Throwable e) {
  					Debug.printStackTrace(e);
  				}
  			}
			}
		}

		if (cellSWTPaintListeners == null) {
			return;
		}


		for (int i = 0; i < cellSWTPaintListeners.size(); i++) {
			try {
				TableCellSWTPaintListener l = (TableCellSWTPaintListener) (cellSWTPaintListeners.get(i));

				l.cellPaint(gc, this);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	private void addCellClipboardListener(TableCellClipboardListener listener) {
		try {
			this_mon.enter();

			if (cellClipboardListeners == null)
				cellClipboardListeners = new ArrayList<>(1);

			cellClipboardListeners.add(listener);

		} finally {
			this_mon.exit();
		}
	}

	@Override
	public String getClipboardText() {
		String text = null;
		try {
			this_mon.enter();

			if (cellClipboardListeners != null) {
				for (TableCellClipboardListener l : cellClipboardListeners) {
					try {
						text = l.getClipboardText(this);
					} catch (Exception e) {
						Debug.out(e);
					}
					if (text != null) {
						break;
					}
				}
			}
		} finally {
			this_mon.exit();
		}
		if (text == null) {
			text = this.getText();
		}
		return text;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#addListeners(java.lang.Object)
	@Override
	public void addListeners(Object listenerObject) {
		if (listenerObject instanceof TableCellDisposeListener)
			addDisposeListener((TableCellDisposeListener) listenerObject);

		if (listenerObject instanceof TableCellRefreshListener)
			addRefreshListener((TableCellRefreshListener) listenerObject);

		if (listenerObject instanceof TableCellToolTipListener)
			addToolTipListener((TableCellToolTipListener) listenerObject);

		if (listenerObject instanceof TableCellMouseMoveListener) {
			addMouseMoveListener((TableCellMouseMoveListener) listenerObject);
		}

		if (listenerObject instanceof TableCellMouseListener) {
			addMouseListener((TableCellMouseListener) listenerObject);
		}

		if (listenerObject instanceof TableCellMenuListener) {
			addMenuListener((TableCellMenuListener) listenerObject);
		}

		if (listenerObject instanceof TableCellVisibilityListener)
			addVisibilityListener((TableCellVisibilityListener) listenerObject);

		if (listenerObject instanceof TableCellSWTPaintListener) {
			addSWTPaintListener((TableCellSWTPaintListener) listenerObject);
		}

		if (listenerObject instanceof TableCellClipboardListener) {
			addCellClipboardListener((TableCellClipboardListener) listenerObject);
		}
	}

	@Override
	public void invokeMouseListeners(TableCellMouseEvent event) {
		if (event.cell != null && event.row == null) {
			event.row = event.cell.getTableRow();
		}

		try {
			tableColumn.invokeCellMouseListeners(event);
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}

		ArrayList listeners = event.eventType == TableCellMouseEvent.EVENT_MOUSEMOVE
				? cellMouseMoveListeners : cellMouseListeners;

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
	public void 
	invokeMenuListeners(
		TableCellMenuEvent event )
	{
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getDataSource()
	@Override
	public Object getDataSource() {
		boolean bCoreObject = tableColumn != null &&  tableColumn.getUseCoreDataSource();
		if (bCoreObject) {
			return coreDataSource;
		}

		if (pluginDataSource != null) {
			return pluginDataSource;
		}

		if (coreDataSource instanceof DownloadManager) {
			DownloadManager dm = (DownloadManager) coreDataSource;
			if (dm != null) {
				try {
					pluginDataSource = DownloadManagerImpl.getDownloadStatic(dm);
				} catch (DownloadException e) { /* Ignore */
				}
			}
		}
		if (coreDataSource instanceof PEPeer) {
			PEPeer peer = (PEPeer) coreDataSource;
			if (peer != null) {
				pluginDataSource = PeerManagerImpl.getPeerForPEPeer(peer);
			}
		}

		if (coreDataSource instanceof PEPiece) {
			// XXX There is no Piece object for plugins yet
			PEPiece piece = (PEPiece) coreDataSource;
			if (piece != null) {
				pluginDataSource = null;
			}
		}

		if (coreDataSource instanceof DiskManagerFileInfo) {
			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) coreDataSource;
			if (fileInfo != null) {
				try {
					pluginDataSource = new DiskManagerFileInfoImpl(
							DownloadManagerImpl.getDownloadStatic(fileInfo.getDownloadManager()),
							fileInfo);
				} catch (DownloadException e) { /* Ignore */
				}
			}
		}

		if (coreDataSource instanceof TRHostTorrent) {
			TRHostTorrent item = (TRHostTorrent) coreDataSource;
			if (item != null) {
				pluginDataSource = new TrackerTorrentImpl(item);
			}
		}

		if (pluginDataSource == null) {
			// No translation available, make pluginDataSource the same as core
			pluginDataSource = coreDataSource;
		}

		return pluginDataSource;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getForeground()
	@Override
	public int[] getForeground() {
		if (composite == null || composite.isDisposed()) {
			return null;
		}
		Color fg = composite.getForeground();
		return new int[] {
			fg.getRed(),
			fg.getGreen(),
			fg.getBlue()
		};
	}

	@Override
	public int[] getBackground() {
		// until we can make sure composite.getBackground is being used
		// (background image might superceed), return 0
		if (true) {
			return new int[] {
				0,
				0,
				0
			};
		}
		if (composite == null || composite.isDisposed()) {
			return null;
		}
		Color bg = composite.getBackground();
		return new int[] {
			bg.getRed(),
			bg.getGreen(),
			bg.getBlue()
		};
	}

	@Override
	public Graphic getBackgroundGraphic() {
		// TODO handle cellArea

		if (composite == null || composite.isDisposed()) {
			return null;
		}

		try {
			Rectangle bounds = composite.getBounds();

			if (bounds.isEmpty()) {
				return null;
			}

			Image imgCap = new Image(composite.getDisplay(), bounds.width,
					bounds.height);

			// will walk up tree until it gets an image
			Control bgControl = Utils.findBackgroundImageControl(composite);
			Image imgBG = composite.getBackgroundImage();

			GC gc = new GC(imgCap);
			try {
				if (imgBG == null) { // || imgBG has alpha..
					gc.setBackground(composite.getBackground());
					gc.fillRectangle(0, 0, bounds.width, bounds.height);
				}

				if (imgBG != null) {
					Point controlPos = new Point(0, 0);
					if (bgControl instanceof Composite) {
						Rectangle compArea = ((Composite) bgControl).getClientArea();
						controlPos.x = compArea.x;
						controlPos.y = compArea.y;
					}
					Point absControlLoc = bgControl.toDisplay(controlPos.x, controlPos.y);

					Rectangle shellClientArea = composite.getShell().getClientArea();
					Point absShellLoc = composite.getParent().toDisplay(
							shellClientArea.x, shellClientArea.y);

					Point ofs = new Point(absControlLoc.x - absShellLoc.x,
							absControlLoc.y - absShellLoc.y);
					Rectangle imgBGBounds = imgBG.getBounds();
					ofs.x = (ofs.x % imgBGBounds.width);
					ofs.y = (ofs.y % imgBGBounds.height);

					gc.drawImage(imgBG, ofs.x, ofs.y);
				}
			} finally {
				gc.dispose();
			}

			return new UISWTGraphicImpl(imgCap);
		} catch (Exception e) {
			Debug.out(e);
		}
		return null;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getGraphic()
	@Override
	public Graphic getGraphic() {
		return graphic;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getHeight()
	@Override
	public int getHeight() {
		if (composite != null && !composite.isDisposed()) {
			if (cellArea != null) {
				return cellArea.height;
			}
			return composite.getSize().y;
		}
		return 0;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getMaxLines()
	@Override
	public int getMaxLines() {
		return -1;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getSortValue()
	@Override
	public Comparable getSortValue() {
		if ( sortValue == null ){
			return( "" );
		}
		return sortValue;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getTableColumn()
	@Override
	public TableColumn getTableColumn() {
		return tableColumn;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getTableID()
	@Override
	public String getTableID() {
		return tableColumn == null ? null : tableColumn.getTableID();
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getTableRow()
	@Override
	public TableRow getTableRow() {
		if (fakeRow == null) {
			fakeRow = new TableRow() {
				Map<String, Object> data = new LightHashMap<>(1);

				@Override
				public void removeMouseListener(TableRowMouseListener listener) {
				}

				@Override
				public int getIndex() {
					return 1;
				}

				@Override
				public boolean isValid() {
					return FakeTableCell.this.isValid();
				}

				@Override
				public boolean isSelected() {
					return false;
				}

				@Override
				public String getTableID() {
					return FakeTableCell.this.getTableID();
				}

				@Override
				public TableView<?> getView() {
					return null;
				}
				@Override
				public TableCell getTableCell(String columnName) {
					return null;
				}

				@Override
				public TableCell getTableCell(TableColumn column){
					return null;
				}
				@Override
				public Object getDataSource() {
					return FakeTableCell.this.getDataSource();
				}

				@Override
				public void addMouseListener(TableRowMouseListener listener) {
				}

				@Override
				public Object getData(String id) {
					synchronized (data) {
						return data.get(id);
					}
				}

				@Override
				public void setData(String id, Object val) {
					synchronized (data) {
						data.put(id, val);
					}
				}
			};
		}
		return fakeRow;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getText()
	@Override
	public String getText() {
		return text;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getToolTip()
	@Override
	public Object getToolTip() {
		if (tooltip == null && hadMore) {
			return text;
		}
		return tooltip;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getWidth()
	@Override
	public int getWidth() {
		if (!isDisposed()) {
			if (cellArea != null) {
				return cellArea.width - 2;
			}
			return composite.getSize().x;
		}
		return 0;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#invalidate()
	@Override
	public void invalidate() {
		valid = false;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#isDisposed()
	@Override
	public boolean isDisposed() {
		return composite == null || composite.isDisposed();
	}

	// @see com.biglybt.pif.ui.tables.TableCell#isShown()
	@Override
	public boolean isShown() {
		return true;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#isValid()
	@Override
	public boolean isValid() {
		return valid;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setFillCell(boolean)
	@Override
	public void setFillCell(boolean fillCell) {
		// TODO Auto-generated method stub

	}

	public void setWrapText( boolean wrap ){
		wrapText = wrap;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setForeground(int, int, int)
	@Override
	public boolean setForeground(int red, int green, int blue) {
		if (isDisposed()) {
			return false;
		}
		if (red < 0 || green < 0 || blue < 0) {
			composite.setForeground(null);
		} else {
			composite.setForeground(ColorCache.getColor(composite.getDisplay(), red,
					green, blue));
		}
		return true;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setForeground(int[])
	@Override
	public boolean setForeground(int[] rgb) {
		if (rgb == null || rgb.length < 3) {
			return setForeground(-1, -1, -1);
		}
		return setForeground(rgb[0], rgb[1], rgb[2]);
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setForegroundToErrorColor()
	@Override
	public boolean setForegroundToErrorColor() {
		if (isDisposed()) {
			return false;
		}
		composite.setForeground(Colors.colorError);
		return true;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setGraphic(com.biglybt.pif.ui.Graphic)
	@Override
	public boolean setGraphic(Graphic img) {
		Image imgSWT = null;
		if (img instanceof UISWTGraphic) {
			imgSWT = ((UISWTGraphic) img).getImage();
		}

		if (imgSWT != null && imgSWT.isDisposed()) {
			return false;
		}

		if (image == imgSWT) {
			return false;
		}

		//System.out.println("setGraphic " + image);

		image = imgSWT;
		if (image != null) {
			imageBounds = image.getBounds();
		}

		if (composite != null && !composite.isDisposed()) {
			redraw();
		}

		graphic = img;
		return true;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setMarginHeight(int)
	@Override
	public void setMarginHeight(int height) {
		// TODO Auto-generated method stub

	}

	// @see com.biglybt.pif.ui.tables.TableCell#setMarginWidth(int)
	@Override
	public void setMarginWidth(int width) {
		// TODO Auto-generated method stub

	}

	// @see com.biglybt.pif.ui.tables.TableCell#setSortValue(java.lang.Comparable)
	@Override
	public boolean setSortValue(Comparable valueToSort) {
		return _setSortValue(valueToSort);
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setSortValue(float)
	@Override
	public boolean setSortValue(float valueToSort) {
		return _setSortValue(Float.valueOf(valueToSort));
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setText(java.lang.String)
	@Override
	public boolean setText(String text) {
		if (text != null && text.equals(this.text)) {
			return false;
		}
		this.text = text;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (!isDisposed()) {
					composite.redraw();
				}
			}
		});
		return true;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#setToolTip(java.lang.Object)
	@Override
	public void setToolTip(Object tooltip) {
		this.tooltip = tooltip;
		updateTooltip();
	}

	@Override
	public void
	setDefaultToolTip(
		Object	o )
	{
		default_tooltip = o;
	}

	@Override
	public Object
	getDefaultToolTip()
	{
		return( default_tooltip );
	}

	private void
	updateTooltip()
	{
		if ( cellArea != null ){
			return;	// can't update tooltip as only applies to an area of the canvas! 
		}
		
		if (!isDisposed()) {
			Object	target = tooltip==null?default_tooltip:tooltip;

			Utils.setTT(composite,target == null ? null : target.toString());
		}
	}

	private boolean _setSortValue(Comparable valueToSort) {
		if (sortValue == valueToSort)
			return false;

		if ((valueToSort instanceof String) && (sortValue instanceof String)
				&& sortValue.equals(valueToSort)) {
			return false;
		}

		if ((valueToSort instanceof Number) && (sortValue instanceof Number)
				&& sortValue.equals(valueToSort)) {
			return false;
		}

		sortValue = valueToSort;

		return true;
	}

	@Override
	public boolean setSortValue(long valueToSort) {
		if ((sortValue instanceof Long)
				&& ((Long) sortValue).longValue() == valueToSort)
			return false;

		return _setSortValue(new Long(valueToSort));
	}

	public void doPaint(GC gc, Rectangle bounds) {
		if (isDisposed()) {
			return;
		}
		// TODO: Cleanup and stop calling me so often!

		//gc.setBackground(getBackgroundSWT());
		//if (DEBUG_COLORCELL) {
		//	gc.setBackground(Display.getDefault().getSystemColor(
		//			(int) (Math.random() * 16)));
		//}
		if (bounds == null) {
			return;
		}
		//gc.fillRectangle(bounds);
		if (!bounds.intersects(gc.getClipping())) {
			return;
		}


		if (image != null && !image.isDisposed()) {
			Point size = new Point(bounds.width, bounds.height);

			int x;

			int y = marginHeight;
			y += (size.y - imageBounds.height) / 2;

			if (orientation == SWT.CENTER) {
				x = marginWidth;
				x += (size.x - (marginWidth * 2) - imageBounds.width) / 2;
			} else if (orientation == SWT.RIGHT) {
				x = bounds.width - marginWidth - imageBounds.width;
			} else {
				x = marginWidth;
			}

			int width = Math.min(bounds.width - x - marginWidth, imageBounds.width);
			int height = Math.min(bounds.height - y - marginHeight,
					imageBounds.height);

			if (width >= 0 && height >= 0) {
				gc.drawImage(image, 0, 0, width, height, bounds.x + x, bounds.y + y,
						width, height);
			}
		}

		if (text != null && text.length() > 0) {
			GCStringPrinter sp = new GCStringPrinter(gc, text, bounds, true, false,
					wrapText?( orientation | SWT.WRAP ):orientation );
			sp.printString();
			hadMore = sp.isCutoff();
		}

		invokeSWTPaintListeners(gc);
	}

	@Override
	public boolean refresh() {
		//System.out.println("refresh");
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				boolean wasValid = valid;
				try {
					tableColumn.invokeCellRefreshListeners(FakeTableCell.this, false);
				} catch (Throwable e) {
				}
				if (refreshListeners != null) {
					for (int i = 0; i < refreshListeners.size(); i++) {
						((TableCellRefreshListener) (refreshListeners.get(i))).refresh(FakeTableCell.this);
					}
				}
				if (!wasValid) {
					valid = true;
				}
			}
		});
		return true;
	}

	public void setDataSource(Object _coreDataSource) {
		coreDataSource = _coreDataSource;
		if (_coreDataSource != null && !isDisposed()) {
			invokeVisibilityListeners(TableCellVisibilityListener.VISIBILITY_SHOWN,
					true);
		}
	}

	public void setControl(final Composite composite) {
		setControl(composite, null, true);
	}

	public void setControl(final Composite composite, Rectangle cellArea, boolean addListeners) {
		if (composite == null) {
			dispose();
			this.composite = null;
			return;
		}

		this.composite = composite;
		this.cellArea = cellArea;

		if (addListeners) {
  		composite.addPaintListener(this);
  		composite.addMouseListener(this);
  		composite.addMouseMoveListener(this);
  		composite.addMouseTrackListener(this);
		}

		setForeground(-1, -1, -1);
		setText(null);
		setToolTip(null);

		composite.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				dispose();
			}
		});
		if (coreDataSource != null && !isDisposed()) {
			invokeVisibilityListeners(TableCellVisibilityListener.VISIBILITY_SHOWN,
					true);
		}
	}

	@Override
	public void paintControl(PaintEvent e) {
		doPaint(e.gc, cellArea == null ? composite.getClientArea() : cellArea);
	}

	@Override
	public void mouseUp(MouseEvent e) {
		invokeMouseListeners(buildMouseEvent(e, TableCellMouseEvent.EVENT_MOUSEUP));
	}

	@Override
	public void mouseDown(MouseEvent e) {
		try{
			if ( 	composite == null || composite.getMenu() != null ||
					( cellMouseListeners != null && cellMouseListeners.size() > 0 ) ||
					text == null || text.length() == 0 ){

				return;
			}

			if (!(e.button == 3 || (e.button == 1 && e.stateMask == SWT.CONTROL))){

				return;
			}

			Menu menu = new Menu(composite.getShell(),SWT.POP_UP);

			MenuItem   item = new MenuItem( menu,SWT.NONE );

			item.setText( MessageText.getString( "ConfigView.copy.to.clipboard.tooltip"));

			item.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent arg0)
					{
						if ( !composite.isDisposed() && text != null && text.length() > 0 ){

							new Clipboard(composite.getDisplay()).setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
						}
					}
				});

			composite.setMenu( menu );

			menu.addMenuListener(
				new MenuAdapter()
				{
					@Override
					public void
					menuHidden(
						MenuEvent arg0 )
					{
						if ( !composite.isDisposed()){

							composite.setMenu( null );
						}
					}
				});

			menu.setVisible( true );

		}finally{

			invokeMouseListeners(buildMouseEvent(e, TableCellMouseEvent.EVENT_MOUSEDOWN));
		}
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
		invokeMouseListeners(buildMouseEvent(e,
				TableCellMouseEvent.EVENT_MOUSEDOUBLECLICK));
	}

	@Override
	public void mouseMove(MouseEvent e) {
		invokeMouseListeners(buildMouseEvent(e, TableCellMouseEvent.EVENT_MOUSEMOVE));
	}

	@Override
	public void mouseHover(MouseEvent e) {
		invokeToolTipListeners(TOOLTIPLISTENER_HOVER);
	}

	@Override
	public void mouseExit(MouseEvent e) {
		invokeMouseListeners(buildMouseEvent(e, TableCellMouseEvent.EVENT_MOUSEEXIT));
	}

	@Override
	public void mouseEnter(MouseEvent e) {
		invokeMouseListeners(buildMouseEvent(e,
				TableCellMouseEvent.EVENT_MOUSEENTER));
	}

	/**
	 * @param e
	 * @return
	 *
	 * @since 3.0.2.1
	 */
	protected TableCellMouseEvent buildMouseEvent(MouseEvent e, int eventType) {
		if (isDisposed()) {
			return null;
		}
		TableCellMouseEvent event = new TableCellMouseEvent( e );
		event.cell = this;
		event.button = e.button;
		event.keyboardState = e.stateMask;
		event.eventType = eventType;

		Rectangle r = composite.getBounds();
		//		int align = tableColumn.getAlignment();
		//		if (align == TableColumn.ALIGN_CENTER) {
		//			r.x = marginWidth;
		//			r.x += (r.width - (marginWidth * 2) - imageBounds.width) / 2;
		//		}

		if (cellArea != null) {
			r = new Rectangle(r.x + cellArea.x, r.y + cellArea.y, cellArea.width,
					cellArea.height);
		}

		event.x = e.x - r.x;
		event.y = e.y - r.y;

		return event;
	}

	private void setOrientationViaColumn() {
		orientation = TableColumnSWTUtils.convertColumnAlignmentToSWT(tableColumn.getAlignment());
	}

	// @see TableCellCore#dispose()
	@Override
	public void dispose() {
		if (composite != null && !composite.isDisposed()) {
			composite.removePaintListener(this);
			composite.removeMouseListener(this);
			composite.removeMouseMoveListener(this);
			composite.removeMouseTrackListener(this);
		}

		if (disposeListeners != null) {
			for (Iterator iter = disposeListeners.iterator(); iter.hasNext();) {
				TableCellDisposeListener listener = (TableCellDisposeListener) iter.next();
				try {
					listener.dispose(this);
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
			disposeListeners = null;
		}
		tableColumn.invokeCellDisposeListeners(this);
		tableColumn.invalidateCells();
	}

	// @see TableCellCore#getCursorID()
	@Override
	public int getCursorID() {
		// TODO Auto-generated method stub
		return 0;
	}

	// @see TableCellCore#getObfuscatedText()
	@Override
	public String getObfuscatedText() {
		return text;
	}

	// @see TableCellCore#getTableRowCore()
	@Override
	public TableRowCore getTableRowCore() {
		return null;
	}

	// @see TableCellCore#getVisuallyChangedSinceRefresh()
	@Override
	public boolean getVisuallyChangedSinceRefresh() {
		return true;
	}

	// @see TableCellCore#invalidate(boolean)
	@Override
	public void invalidate(boolean mustRefresh) {
		valid = false;
	}

	// @see TableCellCore#invokeToolTipListeners(int)
	@Override
	public void invokeToolTipListeners(int type) {
		if (tableColumn == null)
			return;

		tableColumn.invokeCellToolTipListeners(this, type);

		if (tooltipListeners == null)
			return;

		try {
			if (type == TOOLTIPLISTENER_HOVER) {
				for (int i = 0; i < tooltipListeners.size(); i++)
					((TableCellToolTipListener) (tooltipListeners.get(i))).cellHover(this);
			} else {
				for (int i = 0; i < tooltipListeners.size(); i++)
					((TableCellToolTipListener) (tooltipListeners.get(i))).cellHoverComplete(this);
			}
		} catch (Throwable e) {
			Debug.out(e);
		}
	}

	// @see TableCellCore#invokeVisibilityListeners(int, boolean)
	@Override
	public void invokeVisibilityListeners(int visibility,
	                                      boolean invokeColumnListeners) {
		if (invokeColumnListeners) {
			tableColumn.invokeCellVisibilityListeners(this, visibility);
		}

		if (cellVisibilityListeners == null)
			return;

		for (int i = 0; i < cellVisibilityListeners.size(); i++) {
			try {
				TableCellVisibilityListener l = (TableCellVisibilityListener) (cellVisibilityListeners.get(i));

				l.cellVisibilityChanged(this, visibility);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	// @see TableCellCore#isMouseOver()
	@Override
	public boolean isMouseOver() {
		if (isDisposed()) {
			return false;
		}
		Rectangle r = composite.getBounds();
		if (cellArea != null) {
			r = new Rectangle(r.x + cellArea.x, r.y + cellArea.y, cellArea.width,
					cellArea.height);
		}
		Point ptStart = composite.toDisplay(r.x, r.y);
		r.x = ptStart.x;
		r.y = ptStart.y;
		Point ptCursor = composite.getDisplay().getCursorLocation();
		return r.contains(ptCursor);
	}

	@Override
	public void setMouseOver(boolean b) {
		// ignored, we calc mouseover on the fly
	}

	// @see TableCellCore#isUpToDate()
	@Override
	public boolean isUpToDate() {
		return false;
	}

	// @see TableCellCore#locationChanged()
	@Override
	public void locationChanged() {
		// TODO Auto-generated method stub

	}

	// @see TableCellCore#needsPainting()
	@Override
	public boolean needsPainting() {
		return true;
	}

	// @see TableCellCore#refresh(boolean)
	@Override
	public boolean refresh(boolean doGraphics) {
		return refresh();
	}

	// @see TableCellCore#refresh(boolean, boolean, boolean)
	@Override
	public boolean refresh(boolean doGraphics, boolean rowVisible,
	                       boolean cellVisible) {
		return refresh();
	}

	// @see TableCellCore#refresh(boolean, boolean)
	@Override
	public boolean refresh(boolean doGraphics, boolean rowVisible) {
		return refresh();
	}

	// @see TableCellCore#setCursorID(int)
	@Override
	public boolean setCursorID(int cursorID) {
		// TODO Auto-generated method stub
		return false;
	}

	// @see TableCellCore#setUpToDate(boolean)
	@Override
	public void setUpToDate(boolean upToDate) {
		// TODO Auto-generated method stub

	}

	// @see java.lang.Comparable#compareTo(java.lang.Object)
	@Override
	public int compareTo(Object arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	public void setOrentation(int o) {
		orientation = o;
	}

	public Rectangle getCellArea() {
		return cellArea;
	}

	public void setCellArea(Rectangle cellArea) {
		//System.out.println("SCA " + cellArea + ";" + Debug.getCompressedStackTrace());
		this.cellArea = cellArea;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getMouseOffset()
	@Override
	public int[] getMouseOffset() {
		if (isDisposed()) {
			return null;
		}
		Rectangle r = composite.getBounds();
		if (cellArea != null) {
			r = new Rectangle(r.x + cellArea.x, r.y + cellArea.y, cellArea.width,
					cellArea.height);
		}
		Point ptStart = composite.toDisplay(r.x, r.y);
		r.x = ptStart.x;
		r.y = ptStart.y;
		Point ptCursor = composite.getDisplay().getCursorLocation();
		if (!r.contains(ptCursor)) {
			return null;
		}
		return new int[] { ptCursor.x - r.x, ptCursor.y - r.y };
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getMarginHeight()
	@Override
	public int getMarginHeight() {
		return marginHeight;
	}

	// @see com.biglybt.pif.ui.tables.TableCell#getMarginWidth()
	@Override
	public int getMarginWidth() {
		return marginWidth;
	}

	// @see TableCellCore#refreshAsync()
	@Override
	public void refreshAsync() {
		refresh();
	}

	// @see TableCellCore#redraw()
	@Override
	public void redraw() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (!isDisposed()) {
					composite.redraw();
				}
			}
		});
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#doPaint(org.eclipse.swt.graphics.GC)
	@Override
	public void doPaint(GC gc) {
		doPaint(gc, cellArea == null ? composite.getClientArea() : cellArea);
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getBackgroundImage()
	@Override
	public Image getBackgroundImage() {
		// TODO Auto-generated method stub
		return null;
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getBackgroundSWT()
	@Override
	public Color getBackgroundSWT() {
		// TODO Auto-generated method stub
		return composite.getBackground();
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getBounds()
	@Override
	public Rectangle getBounds() {
		return cellArea == null ? composite.getClientArea() : new Rectangle(
				cellArea.x, cellArea.y, cellArea.width, cellArea.height);
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getForegroundSWT()
	@Override
	public Color getForegroundSWT() {
		return composite.getForeground();
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getGraphicSWT()
	@Override
	public Image getGraphicSWT() {
		// TODO Auto-generated method stub
		return null;
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getIcon()
	@Override
	public Image getIcon() {
		// TODO Auto-generated method stub
		return null;
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getSize()
	@Override
	public Point getSize() {
		Rectangle bounds = getBounds();
		if (bounds == null) {
			return null;
		}
		return new Point(bounds.width, bounds.height);
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getTableRowSWT()
	@Override
	public TableRowSWT getTableRowSWT() {
		// TODO Auto-generated method stub
		return null;
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#getTextAlpha()
	@Override
	public int getTextAlpha() {
		// TODO Auto-generated method stub
		return 0;
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#setForeground(org.eclipse.swt.graphics.Color)
	@Override
	public boolean setForeground(Color color) {
		// TODO Auto-generated method stub
		return false;
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#setGraphic(org.eclipse.swt.graphics.Image)
	@Override
	public boolean setGraphic(Image img) {
		graphic = null;

		image = img;
		if (image != null) {
			imageBounds = image.getBounds();
		}

		if (composite != null && !composite.isDisposed()) {
			redraw();
		}

		return true;
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#setIcon(org.eclipse.swt.graphics.Image)
	@Override
	public boolean setIcon(Image img) {
		// TODO Auto-generated method stub
		return false;
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWT#setTextAlpha(int)
	@Override
	public void setTextAlpha(int textOpacity) {
		// TODO Auto-generated method stub

	}

	@Override
	public Rectangle getBoundsOnDisplay() {
		Rectangle bounds = getBounds();
		Point pt = composite.toDisplay(bounds.x, bounds.y);
		bounds.x = pt.x;
		bounds.y = pt.y;
		return bounds;
	}

	@Override
	public TableColumnCore getTableColumnCore() {
		return tableColumn;
	}

	@Override
	public boolean useSimpleSortValue() {
		return false;
	}
	
	@Override
	public Object getData(Object key){
		synchronized( this ){
			if ( userData == null ){
				return( null );
			}else{
				return( userData.get( key ));
			}
		}
	}
	
	@Override
	public void setData(Object key, Object data){
		synchronized( this ){
			if ( userData == null ){
				if ( data == null ){
					return;
				}else{
					userData = new HashMap<>();
				}
			}
			if ( data == null ){
				userData.remove( key );
				if ( userData.isEmpty()){
					userData = null;
				}
			}else{
				userData.put(key, data);
			}
		}
	}
}
