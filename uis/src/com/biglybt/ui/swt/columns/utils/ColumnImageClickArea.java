/*
 * Created on April 29, 2007
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

package com.biglybt.ui.swt.columns.utils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;

import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.utils.ColorCache;

import com.biglybt.pif.ui.tables.*;

/**
 * @author TuxPaper
 * @created Apr 29, 2007
 *
 */
public class ColumnImageClickArea
	implements TableCellMouseMoveListener, TableRowMouseListener
{
	private static final boolean DEBUG = false;

	private String imageID;

	private final String columnID;

	private Rectangle area;

	private String id;

	private Image image;

	private Rectangle imageArea;

	private Image imgOnRow;

	private Image imgOver;

	private Image imgOffRow;

	private boolean mouseDownOn;

	private boolean cellContainsMouse;

	private TableRow rowContainingMouse;

	private float scale = 1.0f;

	private String tooltip;

	private boolean isVisible = true;

	/**
	 * @param id
	 */
	public ColumnImageClickArea(String columnID, String id, String imageID) {
		this.columnID = columnID;
		this.id = id;

		setImageID(imageID);
	}

	/**
	 * @param imageID2
	 *
	 * @since 3.0.1.5
	 */
	public void setImageID(String imageID) {
		ImageLoader imageLoader = ImageLoader.getInstance();
		if (imgOver != null) {
			imageLoader.releaseImage(this.imageID + "-over");
		}
		if (imgOnRow != null) {
			imageLoader.releaseImage(this.imageID + "-mouseonrow");
		}
		if (imgOffRow != null) {
			imageLoader.releaseImage(this.imageID);
		}

		this.imageID = imageID;
		if (imageID == null) {
			imgOffRow = null;
			imgOnRow = null;
		} else {
			imgOnRow = imageLoader.getImage(imageID + "-mouseonrow");
			imgOver = imageLoader.getImage(imageID + "-over");
			imgOffRow = imageLoader.getImage(imageID);
			if (!ImageLoader.isRealImage(imgOnRow)) {
				imgOnRow = imgOffRow;
			}
			if (!ImageLoader.isRealImage(imgOver)) {
				imgOver = imgOffRow;
			}
		}
		this.image = null;
	}

	public void addCell(TableCell cell) {
		cell.addListeners(this);
		TableRow row = cell.getTableRow();
		if (row != null) {
			row.addMouseListener(this);
		}
	}

	/**
	 * @return the area
	 */
	public Rectangle getArea() {
		if (area == null) {
			area = new Rectangle(0, 0, 0, 0);
		}
		return area;
	}

	/**
	 * @param area the area to set
	 */
	public void setArea(Rectangle area) {
		this.area = area;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the image
	 */
	public Image getImage() {
		return image;
	}

	/**
	 * @param image the image to set
	 */
	public void setImage(Image image) {
		if (!ImageLoader.isRealImage(image)) {
			this.image = null;
			imageArea = new Rectangle(0, 0, 0, 0);
		} else {
			this.image = image;
			imageArea = image.getBounds();
		}

		if (area == null) {
			area = new Rectangle(imageArea.x, imageArea.y, imageArea.width,
					imageArea.height);
			return;
		}
		area.width = (int) (imageArea.width * scale);
		area.height = (int) (imageArea.height * scale);
		//System.out.println("setImage scale " + scale + ";" + area + ";" + imageArea);
	}

	public void setPosition(int x, int y) {
		if (area == null) {
			area = new Rectangle(x, y, 0, 0);
			return;
		}
		area.x = x;
		area.y = y;
	}

	/**
	 * @param gcImage
	 *
	 * @since 3.0.1.7
	 */
	public void drawImage(TableCell cell, GC gcImage) {
		if (!isVisible) {
			return;
		}

		Image image = this.image;
		if (image == null) {
  		if (cellContainsMouse && ImageLoader.isRealImage(imgOver)) {
  			image = imgOver;
  		} else if (rowContainingMouse == cell.getTableRow()
  				&& ImageLoader.isRealImage(imgOnRow)) {
  			image = imgOnRow;
  		} else {
  			image = imgOffRow;
  		}
		}

		if (DEBUG && cellContainsMouse) {
			gcImage.setBackground(ColorCache.getColor(gcImage.getDevice(),
					mouseDownOn ? "#ffff00" : "#ff0000"));
			gcImage.fillRectangle(getArea());
		}

		//image = imgOnRow;

		if (ImageLoader.isRealImage(image)) {
			imageArea = image.getBounds();

			Rectangle area = getArea();
			area.width = (int) (imageArea.width * scale);
			area.height = (int) (imageArea.height * scale);

			gcImage.drawImage(image, imageArea.x, imageArea.y, imageArea.width,
					imageArea.height, area.x, area.y, area.width, area.height);
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellMouseListener#cellMouseTrigger(com.biglybt.pif.ui.tables.TableCellMouseEvent)
	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {
		if (!isVisible) {
			return;
		}
		//		System.out.println(event.cell + ": " + event.eventType + ";" + event.x + "x" + event.y + "; b"
		//				+ event.button + "; " + event.keyboardState);

		if (event.eventType == TableCellMouseEvent.EVENT_MOUSEDOWN) {
			mouseDownOn = false;
			Point pt = new Point(event.x, event.y);
			mouseDownOn = getArea().contains(pt);
			TableCellCore cell = (TableCellCore) event.row.getTableCell(columnID);
			if (cell != null) {
				cell.invalidate();
				cell.refreshAsync();
			}
		} else if (event.eventType == TableCellMouseEvent.EVENT_MOUSEUP
				&& mouseDownOn) {
			mouseDownOn = false;
			TableCellMouseEvent mouseEvent = new TableCellMouseEvent( event.baseEvent );
			mouseEvent.button = event.button;
			mouseEvent.cell = event.cell;
			mouseEvent.eventType = TableCellMouseEvent.EVENT_MOUSEUP; // EVENT_MOUSECLICK would be nice..
			mouseEvent.keyboardState = event.keyboardState;
			mouseEvent.skipCoreFunctionality = event.skipCoreFunctionality;
			mouseEvent.x = event.x; // TODO: Convert to coord relative to image?
			mouseEvent.y = event.y;
			mouseEvent.data = this;
			((TableColumnCore) event.cell.getTableColumn()).invokeCellMouseListeners(mouseEvent);
			((TableCellCore) event.cell).invokeMouseListeners(mouseEvent);
		} else if (event.eventType == TableCellMouseEvent.EVENT_MOUSEMOVE) {
			boolean contains = getArea().contains(event.x, event.y);
			setContainsMouse(event.cell, contains);
		} else if (event.eventType == TableCellMouseEvent.EVENT_MOUSEEXIT) {
			setContainsMouse(event.cell, false);
		} else if (event.eventType == TableCellMouseEvent.EVENT_MOUSEDOUBLECLICK) {
			event.skipCoreFunctionality = true;
		}
	}

	// @see com.biglybt.pif.ui.tables.TableRowMouseListener#rowMouseTrigger(com.biglybt.pif.ui.tables.TableRowMouseEvent)
	@Override
	public void rowMouseTrigger(TableRowMouseEvent event) {
		if (!isVisible) {
			return;
		}
		if (event.eventType == TableCellMouseEvent.EVENT_MOUSEEXIT) {
			if (rowContainingMouse == event.row) {
				rowContainingMouse = null;
			}
			setContainsMouse(null, false);
			//System.out.println("d=" + image);
			TableCellCore cell = (TableCellCore) event.row.getTableCell(columnID);
			if (cell != null) {
				cell.invalidate();
				cell.refreshAsync();
			}
		} else if (event.eventType == TableCellMouseEvent.EVENT_MOUSEENTER) {
			rowContainingMouse = event.row;

			//System.out.println("e=" + image);
			TableCellCore cell = (TableCellCore) event.row.getTableCell(columnID);
			if (cell != null) {
				cell.invalidate();
				cell.refreshAsync();
			}
		}
	}

	private void setContainsMouse(TableCell cell, boolean contains) {
		if (cellContainsMouse != contains) {
			cellContainsMouse = contains;

			if (cell != null) {
				TableCellCore cellCore = (TableCellCore) cell;
				cellCore.invalidate();
				cellCore.refreshAsync();
				cellCore.setCursorID(cellContainsMouse ? SWT.CURSOR_HAND
						: SWT.CURSOR_ARROW);
				if (tooltip != null) {
					if (cellContainsMouse) {
						cellCore.setToolTip(tooltip);
					} else {
						Object oldTT = cellCore.getToolTip();
						if (tooltip.equals(oldTT)) {
							cellCore.setToolTip(null);
						}
					}
				}
			}
		}
	}

	public float getScale() {
		return scale;
	}

	public void setScale(float scale) {
		this.scale = scale;
		setImage(image);
	}

	public Rectangle getImageArea() {
		return new Rectangle(imageArea.x, imageArea.y, imageArea.width,
				imageArea.height);
	}

	public String getTooltip() {
		return tooltip;
	}

	public void setTooltip(String tooltip) {
		this.tooltip = tooltip;
	}

	public boolean isVisible() {
		return isVisible;
	}

	public void setVisible(boolean isVisible) {
		this.isVisible = isVisible;
	}
}
