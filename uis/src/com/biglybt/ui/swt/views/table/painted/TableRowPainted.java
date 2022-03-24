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

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.util.*;
import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.utils.SWTRunnable;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableRowSWTChildController;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableCellSWTBase;
import com.biglybt.ui.swt.views.table.impl.TableRowSWTBase;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;
import com.biglybt.ui.swt.views.table.TableViewSWT.ColorRequester;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumn;

public class TableRowPainted
	extends TableRowSWTBase
{
	private static final boolean DEBUG_SUBS = false;

	private Point drawOffset = new Point(0, 0);

	private int numSubItems;

	private Object[] subDataSources;

	private TableRowPainted[] subRows;

	private final Object subRows_sync;

	private int subRowsHeightUseAccessors;

	private TableCellSWTBase[] sortCells;

	private int 	heightUseAccessors = 0;
	private boolean	isHidden;
	
	private boolean initializing = true;

	private boolean inPaintItem;
	
	private Color colorFG = null;

	private Object			colorLock = new Object();
	
	private CopyOnWriteList<Object[]>	FGRequesters = new CopyOnWriteList<>(1);
	private CopyOnWriteList<Object[]>	BGRequesters = new CopyOnWriteList<>(1);
	
	public TableRowPainted(TableRowCore parentRow, TableViewPainted tv,
			Object dataSource, boolean triggerHeightChange) {
		// in theory, TableRowPainted could have its own sync
		// but in practice, I end up calling code within the sync which inevitably
		// calls the TableView and causes locks.  So, use the TableView's sync!

		super(tv.getSyncObject(), parentRow, tv, dataSource);
		subRows_sync = tv.getSyncObject();

		TableColumnCore[] sortColumns = tv.getSortColumns();
		String[] sortColumnNames = new String[sortColumns.length];
		for (int i = 0; i < sortColumnNames.length; i++) {
			sortColumnNames[i] = sortColumns[i].getName();
		}
		setSortColumn(sortColumnNames);

		isHidden = parentRow != null && tv.getFilterSubRows() && !tv.isFiltered( dataSource );
		
		setHeight(tv.getRowDefaultHeight(), false);
		
		if ( dataSource instanceof TableRowSWTChildController ){
			
			TableRowSWTChildController c = (TableRowSWTChildController)dataSource;
			
			setExpanded( c.isExpanded(), false );
			
			Object[] kids = c.getChildDataSources();
			
			if ( kids != null && kids.length > 0 ){
				
				setSubItems( kids, false );
			}
		}
		
		initializing = false;
		if (triggerHeightChange) {
			heightChanged(0, getFullHeight());
		}
		
		tv.rowCreated();
	}

	public boolean
	refilter()
	{		
		boolean changed = false;
		
		synchronized( subRows_sync ){
				
			if ( subRows != null ){
				
				for ( TableRowPainted subrow : subRows) {
					
					if ( subrow.refilter()){
						
						changed = true;
					}
				}
			}
			
			Object ds = getDataSource( true );
			
			boolean newHidden = !getViewPainted().isFiltered( ds );
		
			if ( newHidden != isHidden ){
			
				TableRowCore row = this;
				
				boolean	expanded = true;
				
				while( expanded ){
					
					row = row.getParentRowCore();
					
					if ( row == null ){
						
						break;
					}
					
					expanded = row.isExpanded();
				}
				
				int	old_height = getHeight();
				
				isHidden = newHidden;

				int	new_height = getHeight();

				if ( expanded ){										
					
					heightChanged( old_height, new_height);
				
					changed = true;
					
				}else{
				
					row = getParentRowCore();
					
					if ( row instanceof TableRowPainted){
						((TableRowPainted) row).subRowHeightChanged( old_height, new_height);
					}
				}
			}
		}
		
		return( changed );
	}
	
	public boolean
	isHidden()
	{
		return( isHidden );
	}
	
	@Override
	public void invalidate(boolean mustRefersh) {
		super.invalidate(mustRefersh);
		synchronized (lock) {
			if (sortCells != null) {
				for (TableCellSWTBase sortCell : sortCells) {
					sortCell.invalidate(mustRefersh);
				}
			}
		}
	}
	
	private void buildCells() {
		//debug("buildCells " + Debug.getCompressedStackTrace());
		TableColumnCore[] visibleColumns = getView().getVisibleColumns();
		if (visibleColumns == null) {
			return;
		}
		
		TableViewPainted table = getViewPainted();
		
		synchronized (lock) {
			mTableCells = new LinkedHashMap<>(visibleColumns.length, 1);

			TableRowCore parentRow = getParentRowCore();
			// create all the cells for the column
			for (int i = 0; i < visibleColumns.length; i++) {
				
				TableColumnCore coreColumn = visibleColumns[i];
				
				if ( coreColumn == null) {
					continue;
				}

				if (parentRow != null
						&& !coreColumn.handlesDataSourceType(getDataSource(false).getClass())) {
					mTableCells.put(coreColumn.getName(), null);
					continue;
				}

				//System.out.println(dataSource + ": " + tableColumns[i].getName() + ": " + tableColumns[i].getPosition());
				TableCellSWTBase cell = null;
				if (sortCells != null) {
					for (TableCellSWTBase sortCell : sortCells) {
						if (coreColumn.equals(sortCell.getTableColumnCore())) {
							cell = sortCell;
						}
					}
				}
				if (cell == null) {
					cell = new TableCellPainted(this, table.getColumnPainted( coreColumn ), i);
				}

				mTableCells.put(coreColumn.getName(), cell);
				//if (i == 10) cell.bDebug = true;
			}
		}
	}

	private void destroyCells() {
		synchronized (lock) {
			if (mTableCells == null) {
				return;
			}

			outer:
			for (TableCellCore cell : mTableCells.values()) {
				if (cell == null) {
					continue;
				}
				if (sortCells != null) {
					for (TableCellCore sortCell : sortCells) {
						if (cell == sortCell) {
							continue outer;
						}
					}
				}

				if (!cell.isDisposed()){
					cell.dispose();
				}
			}
			mTableCells = null;
		}
	}

	public TableViewPainted getViewPainted() {
		return (TableViewPainted) getView();
	}

	/**
	 * @param gc GC to draw to
	 * @param drawBounds Area that needs redrawing
	 * @param rowStartX where in the GC this row's x-axis starts
	 * @param rowStartY where in the GC this row's y-axis starts
	 * @param pos
	 */
	public void swt_paintGC(GC gc, Rectangle drawBounds, int rowStartX,
			int rowStartY, int pos, boolean isTableSelected, boolean isTableEnabled) {
		if (isRowDisposed() || gc == null || gc.isDisposed() || drawBounds == null || isHidden ) {
			return;
		}
		// done by caller
		//if (!drawBounds.intersects(rowStartX, rowStartY, 9999, getHeight())) {
		//	return;
		//}

		TableColumnCore[] visibleColumns = getView().getVisibleColumns();
		if (visibleColumns == null || visibleColumns.length == 0) {
			return;
		}

		boolean isAttention = isRequestAttention();
		boolean isSelected = isSelected();
		boolean isSelectedNotFocused = isSelected && !isTableSelected;

		Color origBG = gc.getBackground();
		Color origFG = gc.getForeground();

		Color fg = getForeground();
		Color shadowColor = null;

		Color altColor;
		Color bg;
		
		boolean isAltColor 			= false;
		boolean rowHasBackground	= false;
		boolean rowHasForeground	= fg != null;
		
		if (isTableEnabled) {
			int altIndex = pos >= 0 ? pos % 2 : 0;
			
	  		altColor = Colors.alternatingColors[altIndex];
	  		if (altColor == null) {
	  			altColor = TablePaintedUtils.getColour( gc, SWT.COLOR_LIST_BACKGROUND );
	  		}else{
	  			isAltColor = altIndex==1;
	  		}
	  		if (isSelected) {
	  			Color color;
	  			color = TablePaintedUtils.getColour(gc, SWT.COLOR_LIST_SELECTION);
	  			gc.setBackground(color);
	  		} else {
	  			gc.setBackground(altColor);
	  		}
	
	  		bg = getBackground();
	  		if (bg == null) {
	  			bg = gc.getBackground();
	  		} else {
	  			rowHasBackground = true;
	  			gc.setBackground(bg);
	  		}
	
	  		if (isSelected) {
	  			shadowColor = fg;
	  			fg = TablePaintedUtils.getColour(gc,SWT.COLOR_LIST_SELECTION_TEXT);
	  		} else {
	  			if (fg == null) {
	  				fg = TablePaintedUtils.getColour(gc,SWT.COLOR_LIST_FOREGROUND);
	  			}
	  		}
		} else {
			Device device = gc.getDevice();
			altColor = TablePaintedUtils.getColour(device, SWT.COLOR_WIDGET_BACKGROUND);
			if (isSelected) {
				bg = TablePaintedUtils.getColour(device, SWT.COLOR_WIDGET_LIGHT_SHADOW);
			} else {
				bg = altColor;
			}
			gc.setBackground(bg);

			fg = TablePaintedUtils.getColour(device, SWT.COLOR_WIDGET_NORMAL_SHADOW);
		}
		
		if ( isAttention ){
			bg = Colors.fadedRed;
			gc.setBackground( bg );
		}
		
		gc.setForeground(fg);

		int rowAlpha = getAlpha();
		Font font = gc.getFont();
		Rectangle clipping = gc.getClipping();

		int x = rowStartX;
		//boolean paintedRow = false;
		synchronized (lock) {
			if (mTableCells == null) {
				// not sure if this is wise, but visibleRows seems to keep up to date.. so, it must be ok!
				setShown(true, true);
			}
			if (mTableCells != null) {
				for (TableColumn tc : visibleColumns) {
					
					TableCellSWTBase cell = mTableCells.get(tc.getName());
					
					int w = tc.getWidth();
					
					Rectangle r = new Rectangle(x, rowStartY, w, getHeight());

					TableCellPainted cellSWT = null;
					
					if ( cell instanceof TableCellPainted && !cell.isDisposed()) {
						
						cellSWT = (TableCellPainted) cell;
						
						cellSWT.setBoundsRaw(r);
					}
					if (drawBounds.intersects(r)) {
						//paintedRow = true;
						gc.setAlpha(255);
						if (isSelectedNotFocused) {
							gc.setBackground(altColor);
							gc.fillRectangle(r);
							gc.setAlpha(100);
							gc.setBackground(bg);
							gc.fillRectangle(r);
						} else {
							gc.setBackground(bg);
							gc.fillRectangle(r);
							if (isSelected) {
  							gc.setAlpha(80);
  							gc.setForeground(altColor);
  							gc.fillGradientRectangle(r.x, r.y, r.width, r.height, true);
  							gc.setForeground(fg);
							}
						}
						gc.setAlpha(rowAlpha);
						
						if ( cellSWT == null ){
							
							x += w;
							
							continue;
						}
						
						if (swt_paintCell(gc, cellSWT.getBounds(), cellSWT, shadowColor, !( isSelected || rowHasBackground), !rowHasForeground, isAltColor)) {
							// row color may have changed; this would update the color
							// for all new cells.  However, setting color triggers a
							// row redraw that will fix up the UI
							//Color fgNew = getForeground();
							//if (fgNew != null && fgNew != fg) {
							//	fg = fgNew;
							//}
							gc.setBackground(bg);
							gc.setForeground(fg);
							gc.setFont(font);
							Utils.setClipping(gc, clipping);
						}
						if (DEBUG_ROW_PAINT) {
							((TableCellSWTBase) cell).debug("painted "
									+ (cell.getVisuallyChangedSinceRefresh() ? "VC" : "!P")
									+ " @ " + r);
						}
					} else {
						if (DEBUG_ROW_PAINT) {
							((TableCellSWTBase) cell).debug("Skip paintItem; no intersects; r="
									+ r
									+ ";dB="
									+ drawBounds
									+ " from "
									+ Debug.getCompressedStackTrace(4));
						}
					}

					x += w;
				}
			}
			int w = drawBounds.width - x;
			if (w > 0) {
				Rectangle r = new Rectangle(x, rowStartY, w, getHeight());
				if (clipping.intersects(r)) {
  				gc.setAlpha(255);
  				if (isSelectedNotFocused) {
  					gc.setBackground(altColor);
  					gc.fillRectangle(r);
  					gc.setAlpha(100);
  					gc.setBackground(bg);
  					gc.fillRectangle(r);
  				} else {
  					gc.fillRectangle(r);
  					if (isSelected) {
  						gc.setAlpha(80);
  						gc.setForeground(altColor);
  						gc.fillGradientRectangle(r.x, r.y, r.width, r.height, true);
  						gc.setForeground(fg);
  					}
  				}
  				gc.setAlpha(rowAlpha);
				}
			}

		} //synchronized (lock)

		//if (paintedRow) {
		//	debug("Paint " + e.x + "x" + e.y + " " + e.width + "x" + e.height + ".." + e.count + ";clip=" + e.gc.getClipping() +";drawOffset=" + drawOffset + " via " + Debug.getCompressedStackTrace());
		//}

		if (isFocused()) {
			gc.setAlpha(40);
			gc.setForeground(origFG);
			gc.setLineDash(new int[] { 1, 2 });
			gc.drawRectangle(rowStartX, rowStartY,
					getViewPainted().getClientArea().width - 1, getHeight() - 1);
			gc.setLineStyle(SWT.LINE_SOLID);
		}

		gc.setAlpha(255);
		gc.setBackground(origBG);
		gc.setForeground(origFG);
	}

	public void fakeRedraw( String col_name ) {
		
		if ( !Utils.isSWTThread()) {
			Utils.execSWTThread(
				new Runnable()
				{
					public void
					run()
					{
						swt_fakeRedraw( col_name );
					}
				});
		}else{
			swt_fakeRedraw( col_name );
		}
	}
	
	private void swt_fakeRedraw( String col_name ) {
		
		setShown( true, true );
		
		TableCellPainted cell = (TableCellPainted)getTableCellCore( col_name  );

		if ( cell != null ){
			if ( cell.getBoundsRaw() == null ){
				
				cell.setBoundsRaw( new Rectangle( 0, 0, 1000, getHeight() ));
			}
			
			cell.refresh( true, true, true );
			
			Image image = new Image(Utils.getDisplay(), 1000, getHeight());
			
			GC gc = new GC(image);
			
			try{
				swt_paintCell( gc, image.getBounds(), (TableCellSWTBase)cell, null, false, false, false );
			
				if ( isExpanded()){
					
					synchronized (subRows_sync) {
						
						if (subRows != null) {
							
							for ( TableRowPainted subrow : subRows) {
															
								subrow.swt_fakeRedraw( col_name );
							}
						}
					}
				}
			}finally{
				
				gc.dispose();
			
				image.dispose();
			}
		}
	}
	
	@Override
	public boolean 
	isInPaintItem() 
	{
		return inPaintItem;
	}
	
	private boolean swt_paintCell(GC gc, Rectangle cellBounds,
			TableCellSWTBase cell, Color shadowColor, boolean enableColumnBG, boolean enableColumnFG, boolean isAltColor ) {
		// Only called from swt_PaintGC, so we can assume GC, cell are valid
		if (cellBounds == null) {
			return false;
		}

		boolean gcChanged = false;
		try {
			if ( inPaintItem ){
				Debug.out( "hmm");
				return( false );
			}
			inPaintItem = true;
			gc.setTextAntialias(SWT.DEFAULT);

			TableViewSWT<?> view = (TableViewSWT<?>) getView();

			TableColumnCore column = (TableColumnCore) cell.getTableColumn();
			view.invokePaintListeners(gc, this, column, cellBounds);

			int fontStyle = getFontStyle();
			Font oldFont = null;
			if (fontStyle == SWT.BOLD) {
				oldFont = gc.getFont();
				gc.setFont(FontUtils.getAnyFontBold(gc));
				gcChanged = true;
			}else if (fontStyle == SWT.ITALIC) {
				oldFont = gc.getFont();
					// On Windows 10 for some reason some people get pixelated italic fonts as it seems text antialias default is off :( 
				if ( Constants.isWindows ){
					gc.setTextAntialias(SWT.ON);
				}
				gc.setFont(FontUtils.getAnyFontItalic(gc));
				gcChanged = true;
			}

			if (!cell.isUpToDate()) {
				//System.out.println("R " + rowIndex + ":" + iColumnNo);
				cell.refresh(true, true);
				//return;
			}

			String text = cell.getText();

			Color fg = cell.getForegroundSWT();
			if ( fg == null && enableColumnFG ){
				fg = cell.getTableColumnSWT().getForeground();
			}
			if (fg != null) {
				gcChanged = true;
				if (isSelected()) {
					shadowColor = fg;
				} else {
					gc.setForeground(fg);
				}
			}
			
			
			Color bg = cell.getBackgroundSWT();
			if ( bg == null && enableColumnBG ){
				bg = cell.getTableColumnSWT().getBackground();
			}
			if (bg != null) {
				gcChanged = true;
				gc.setBackground(bg);
				if ( !isAltColor ){
					int alpha = gc.getAlpha();
					gc.setAlpha(200 );
					gc.fillRectangle(cellBounds);
					gc.setAlpha( alpha );
				}else{
					gc.fillRectangle(cellBounds);
				}
			}

			//if (cell.getTableColumn().getClass().getSimpleName().equals("ColumnUnopened")) {
			//	System.out.println("FOOO" + cell.needsPainting());
			//}
			if (cell.needsPainting()) {
				Image graphicSWT = cell.getGraphicSWT();
				if (graphicSWT != null && !graphicSWT.isDisposed()) {
					Rectangle imageBounds = graphicSWT.getBounds();
					Rectangle graphicBounds = new Rectangle(cellBounds.x, cellBounds.y,
							cellBounds.width, cellBounds.height);
					if (cell.getFillCell()) {
						if (!graphicBounds.isEmpty()) {
							gc.setAdvanced(true);
							//System.out.println(imageBounds + ";" + graphicBounds);
							gc.drawImage(graphicSWT, 0, 0, imageBounds.width,
									imageBounds.height, graphicBounds.x, graphicBounds.y,
									graphicBounds.width, graphicBounds.height);
						}
					} else {

						if (imageBounds.width < graphicBounds.width) {
							int alignment = column.getAlignment();
							if ((alignment & TableColumn.ALIGN_CENTER) > 0) {
								graphicBounds.x += (graphicBounds.width - imageBounds.width) / 2;
							} else if ((alignment & TableColumn.ALIGN_TRAIL) > 0) {
								graphicBounds.x = (graphicBounds.x + graphicBounds.width)
										- imageBounds.width;
							}
						}

						if (imageBounds.height < graphicBounds.height) {
							graphicBounds.y += (graphicBounds.height - imageBounds.height) / 2;
						}

						gc.drawImage(graphicSWT, graphicBounds.x, graphicBounds.y);
					}

				}
				cell.doPaint(gc);
				gcChanged = true;
			}
			if (text.length() > 0) {
				int ofsx = 0;
				Image image = cell.getIcon();
				Rectangle imageBounds = null;
				if (image != null && !image.isDisposed()) {
					imageBounds = image.getBounds();
					int ofs = imageBounds.width;
					ofsx += ofs;
					cellBounds.x += ofs;
					cellBounds.width -= ofs;
				}
				//System.out.println("PS " + getIndex() + ";" + cellBounds + ";" + cell.getText());
				int style = TableColumnSWTUtils.convertColumnAlignmentToSWT(column.getAlignment());
				if (cellBounds.height > 20) {
					style |= SWT.WRAP;
				}
				int textOpacity = cell.getTextAlpha();
				//gc.setFont(getRandomFont());
				//textOpacity = 130;
				if (textOpacity < 255) {
					//gc.setTextAntialias(SWT.ON);
					gc.setAlpha(textOpacity);
					gcChanged = true;
				} else if (textOpacity > 255) {
					boolean is_italic = ( gc.getFont().getFontData()[0].getStyle() & SWT.ITALIC ) != 0; 
					if ( is_italic ){
						gc.setFont(FontUtils.getAnyFontBoldItalic(gc));
					}else{
						gc.setFont(FontUtils.getAnyFontBold(gc));
					}
					
					//gc.setTextAntialias(SWT.ON);
					//gc.setAlpha(textOpacity & 255);
					gcChanged = true;
				}
				// put some padding on text
				ofsx += 6;
				cellBounds.x += 3;
				cellBounds.width -= 6;
				cellBounds.y += 2;
				cellBounds.height -= 4;
				if (!cellBounds.isEmpty()) {
					GCStringPrinter sp = new GCStringPrinter(gc, text, cellBounds, true,
							cellBounds.height > 20, style);

					if (shadowColor != null) {
						Color oldFG = gc.getForeground();
						gc.setForeground(shadowColor);

						cellBounds.x += 1;
						cellBounds.y += 1;
						int alpha = gc.getAlpha();
						gc.setAlpha(0x40);
						sp.printString(gc, cellBounds, style);
						gc.setAlpha(alpha);
						gc.setForeground(oldFG);

						cellBounds.x -= 1;
						cellBounds.y -= 1;
						sp.printString2(gc, cellBounds, style);
					} else {
						sp.printString();
					}

					if ( !sp.isTruncated()) {

						cell.setDefaultToolTip(null);
					} else {

						cell.setDefaultToolTip(text);
					}

					Point psize = sp.getCalculatedPreferredSize();
					psize.x += ofsx;

					TableColumn tableColumn = cell.getTableColumn();
					if (tableColumn != null && tableColumn.getPreferredWidth() < psize.x) {
						tableColumn.setPreferredWidth(psize.x);
					}

					if (imageBounds != null) {
						int drawToY = cellBounds.y + (cellBounds.height / 2)
								- (imageBounds.height / 2);

						boolean hack_adv = Constants.isWindows8OrHigher && gc.getAdvanced();

						if ( hack_adv ){
								// problem with icon transparency on win8
							gc.setAdvanced( false );
						}
						if ((style & SWT.RIGHT) != 0) {
							Point size = sp.getCalculatedSize();
							size.x += ofsx;

							int drawToX = cellBounds.x + cellBounds.width - size.x;
							gc.drawImage(image, drawToX, drawToY);
						} else {
							if (imageBounds.height > cellBounds.height) {
								float pct = cellBounds.height / (float) imageBounds.height;
  							gc.drawImage(image, 0, 0, imageBounds.width, imageBounds.height,
  									cellBounds.x - imageBounds.width - 3, cellBounds.y,
  									(int) (imageBounds.width * pct),
  									(int) (imageBounds.height * pct));
							} else {
  							gc.drawImage(image, cellBounds.x - imageBounds.width - 3, drawToY);
							}
						}
						if ( hack_adv ){
							gc.setAdvanced( true );
						}
					}
				} else {
					cell.setDefaultToolTip(null);
				}
			}
			cell.clearVisuallyChangedSinceRefresh();

			if (oldFont != null) {
				gc.setFont(oldFont);
			}
		} catch (Exception e) {
			Debug.out(cell.getTableID() + ":" + cell.getTableColumn().getName(), e );
		}finally{
			inPaintItem = false;
		}

		return gcChanged;
	}

	private Font getRandomFont() {
		FontData[] fontList = Display.getDefault().getFontList(null, (Math.random() > 0.5));
		FontData fontData = fontList[(int)(Math.random() * fontList.length)];
		fontData.setStyle((int)(Math.random() * 4));
		fontData.height = (float) (Math.random() * 50f);
		return new Font(Display.getDefault(), fontData);
	}

	@Override
	public List<TableCellCore> refresh(boolean bDoGraphics, boolean bVisible) {
		final List<TableCellCore> invalidCells = super.refresh(bDoGraphics,
				bVisible);
		//System.out.print(SystemTime.getCurrentTime() + "] InvalidCells: ");
		if (invalidCells.size() > 0) {
			//for (TableCellCore cell : invalidCells) {
			//	System.out.print(cell.getTableColumn().getName() + ", ");
			//}
			//System.out.println();
			Utils.execSWTThread(new SWTRunnable() {
				@Override
				public void runWithDisplay(Display display) {
					Composite composite = getViewPainted().getComposite();
					if (composite == null || composite.isDisposed() || !isVisible()) {
						return;
					}
					boolean allCells;
					synchronized( lock ){
						allCells = (mTableCells != null)&& invalidCells.size() == mTableCells.size();
					}

					if (allCells) {
						getViewPainted().swt_updateCanvasImage(getDrawBounds(), false);
					} else {
						Rectangle drawBounds = getDrawBounds();
						for (Object o : invalidCells) {
							if (o instanceof TableCellPainted) {
								TableCellPainted cell = (TableCellPainted) o;
								Rectangle bounds = cell.getBoundsRaw();
								if (bounds != null) {
									// cell's rawbounds yPos can get out of date
									// cell's rawbounds gets updated via swt_updateCanvasImage, via swt_paintGC
									bounds.y = drawBounds.y;
									bounds.height = drawBounds.height;
									cell.setBoundsRaw(bounds);
									getViewPainted().swt_updateCanvasImage(bounds, false);
								}
							}
						}
					}
				}
			});
			//} else {
			//System.out.println("NONE");
		}
		return invalidCells;
	}

	@Override
	public void redraw(boolean doChildren) {
		redraw(doChildren, false);
	}

	public void redraw(boolean doChildren, boolean immediateRedraw) {
		if (isRowDisposed()) {
			return;
		}
		getViewPainted().redrawRow(this, immediateRedraw);

		if (!doChildren) {
			return;
		}
		synchronized (subRows_sync) {
			if (subRows != null) {
				for (TableRowPainted subrow : subRows) {
					subrow.redraw();
				}
			}
		}
	}

	protected void debug(String s) {
		AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger("table");
		String prefix = SystemTime.getCurrentTime() + ":" + getTableID() + ": r"
				+ getIndex();
		if (getParentRowCore() != null) {
			prefix += "of" + getParentRowCore().getIndex();
		}
		prefix += ": ";
		diag_logger.log(prefix + s);

		System.out.println(prefix + s);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.impl.TableRowSWTBase#getBounds()
	 */
	@Override
	public Rectangle getBounds() {
		//TableViewPainted view = (TableViewPainted) getView();
		//Rectangle clientArea = view.getClientArea();
		return new Rectangle(0, drawOffset.y, 9990, getHeight());
	}

	public Rectangle getDrawBounds() {
		TableViewPainted view = (TableViewPainted) getView();
		Rectangle clientArea = view.getClientArea();
		int offsetX = TableViewPainted.DIRECT_DRAW ? -clientArea.x : 0;
		Rectangle bounds = new Rectangle(offsetX, drawOffset.y - clientArea.y, 9990,
				getHeight());
		return bounds;
	}

	@Override
	public int getFullHeight() {
		if ( isHidden ){
			return( 0 );
		}
		int h = getHeight();
		if (numSubItems > 0 && isExpanded()) {
			h += subRowsHeightUseAccessors;
		}
		return h;
	}

	public Point getDrawOffset() {
		return drawOffset;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.impl.TableRowSWTBase#heightChanged(int, int)
	 */
	public void heightChanged(int oldHeight, int newHeight) {
		getViewPainted().rowHeightChanged(this, oldHeight, newHeight);
		TableRowCore row = getParentRowCore();
		if (row instanceof TableRowPainted) {
			((TableRowPainted) row).subRowHeightChanged( oldHeight, newHeight);
		}
	}

	private void
	setSubRowsHeight(
		int		h )
	{
		subRowsHeightUseAccessors = h;
	}
	
	protected void subRowHeightChanged( int oldHeight, int newHeight) {
		int old = subRowsHeightUseAccessors;
		subRowsHeightUseAccessors += (newHeight - oldHeight);
		
		if ( old != subRowsHeightUseAccessors && isExpanded() ){
			TableRowCore row = getParentRowCore();
			if (row instanceof TableRowPainted) {
				((TableRowPainted) row).subRowHeightChanged( old, subRowsHeightUseAccessors);
			}
		}
	}

	public boolean setDrawOffset(Point drawOffset) {
		if (drawOffset.equals(this.drawOffset)) {
			return false;
		}
		this.drawOffset = drawOffset;

		return true;
	}

	@Override
	public void setWidgetSelected(boolean selected) {
		redraw(false, true);
	}

	@Override
	public boolean setShown(boolean b, boolean force) {
		if (b == wasShown && !force) {
			return false;
		}

		synchronized (lock) {
			if (b && mTableCells == null) {
				buildCells();
			}
		}

		boolean ret = super.setShown(b, force);

		if (b) {
			invalidate();
			redraw(false, false);
		}

		synchronized (lock) {
			if (!b && mTableCells != null) {
				destroyCells();
			}
		}

		return ret;
	}

	@Override
	public void delete() {
		super.delete();

		synchronized (lock) {
			if (sortCells != null) {
				for (TableCellCore sortCell : sortCells) {
					if (!sortCell.isDisposed()) {
						sortCell.dispose();
					}
				}
				sortCells = null;
			}
		}

		deleteExistingSubRows();
	}

	private void deleteExistingSubRows() {
		synchronized (subRows_sync) {
			if (subRows != null) {
				for (TableRowPainted subrow : subRows) {
					subrow.delete();
				}
			}
			subRows = null;
		}
	}

	@Override
	public void setSubItemCount(int length) {
		setSubItemCount( length, true );
	}
	
	private void setSubItemCount(int length, boolean triggerHeightListener ) {

		numSubItems = length;
		if (isExpanded() && subDataSources.length == length) {
			if (DEBUG_SUBS) {
				debug("setSubItemCount to " + length);
			}

			deleteExistingSubRows();
			TableRowPainted[] newSubRows = new TableRowPainted[length];
			TableViewPainted tv = getViewPainted();
			int h = 0;
			for (int i = 0; i < newSubRows.length; i++) {
				newSubRows[i] = new TableRowPainted(this, tv, subDataSources[i], false);
				newSubRows[i].setTableItem(i);
				h += newSubRows[i].getFullHeight();
			}

			int oldHeight = getFullHeight();
			
			setSubRowsHeight( h );
			
			int newHeight = getFullHeight();
			
			TableRowCore row = getParentRowCore();
			if (row instanceof TableRowPainted) {
				((TableRowPainted) row).subRowHeightChanged( oldHeight, newHeight);
			}
			
			TableViewPainted tvp = getViewPainted();

			if ( triggerHeightListener ){
					
				tvp.rowHeightChanged(this, oldHeight, newHeight );
			}
			
			tvp.triggerListenerRowAdded(newSubRows);

			subRows = newSubRows;
		}
	}

	@Override
	public int getSubItemCount() {
		return numSubItems;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#linkSubItem(int)
	 */
	@Override
	public TableRowCore linkSubItem(int indexOf) {
		// Not used by TableViewPainted
		return null;
	}

	@Override
	public void setSubItems(Object[] datasources) {
		setSubItems( datasources, true );
		getViewPainted().sortRows(true,true);
	}
	
	private void setSubItems(Object[] datasources, boolean triggerHeightListeners) {
		deleteExistingSubRows();
		synchronized (subRows_sync) {
			subDataSources = datasources;
			setSubRowsHeight( 0 );
			setSubItemCount(datasources.length, triggerHeightListeners);
		}
	}

	@Override
	public TableRowCore[] getSubRowsWithNull() {
		synchronized (subRows_sync) {
			return subRows == null ? new TableRowCore[0] : subRows;
		}
	}

	@Override
	public TableRowCore[] getSubRowsRecursive(boolean includeHidden){
		synchronized (subRows_sync) {
			
			List<TableRowCore>	result = new ArrayList<>();
			
			getSubRowsRecursive( result, getSubRowsWithNull(), includeHidden );
			
			return( result.toArray( new TableRowCore[ result.size()]));
		}
	}
	
	private void
	getSubRowsRecursive(
		List<TableRowCore>	result,
		TableRowCore[]		rows,
		boolean 			includeHidden )
	{
		for ( TableRowCore row: rows ){
			
			if ( includeHidden || !row.isHidden()){
			
				result.add( row );
			
				if ( includeHidden || row.isExpanded()){
				
					getSubRowsRecursive( result, row.getSubRowsWithNull(), includeHidden);
				}
			}
		}
	}
	
	@Override
	public void removeSubRow(Object datasource) {
		synchronized (subRows_sync) {

			for (int i = 0; i < subDataSources.length; i++) {
				Object ds = subDataSources[i];
				if (ds == datasource) { // use .equals instead?
					TableRowPainted rowToDel = subRows[i];
					TableRowPainted[] newSubRows = new TableRowPainted[subRows.length - 1];
					System.arraycopy(subRows, 0, newSubRows, 0, i);
					System.arraycopy(subRows, i + 1, newSubRows, i, subRows.length - i
							- 1);
					subRows = newSubRows;

					Object[] newDatasources = new Object[subRows.length];
					System.arraycopy(subDataSources, 0, newDatasources, 0, i);
					System.arraycopy(subDataSources, i + 1, newDatasources, i,
							subDataSources.length - i - 1);
					subDataSources = newDatasources;

					rowToDel.delete();

					setSubItemCount(subRows.length);

					break;
				}
			}
		}
	}

	@Override
	public void setExpanded(boolean b) {
		setExpanded( b, true );
	}
	
	private void setExpanded(boolean b, boolean triggerHeightChange ) {
		if ( canExpand() ){
			int oldHeight = getFullHeight();
			super.setExpanded(b);
			synchronized (subRows_sync) {
				TableRowPainted[] newSubRows = null;
				if (	b &&
						(subRows == null || subRows.length != numSubItems) &&
						subDataSources != null && 
						subDataSources.length == numSubItems) 
				{
					if (DEBUG_SUBS) {
						debug("building subrows " + numSubItems);
					}

					deleteExistingSubRows();
					newSubRows = new TableRowPainted[numSubItems];
					TableViewPainted tv = getViewPainted();
					int h = 0;
					for (int i = 0; i < newSubRows.length; i++) {
						newSubRows[i] = new TableRowPainted(this, tv, subDataSources[i],
								false);
						newSubRows[i].setTableItem(i);
						h += newSubRows[i].getFullHeight();
					}

					setSubRowsHeight( h );

					subRows = newSubRows;
				}

				int newHeight = getFullHeight();
				
				TableRowCore row = getParentRowCore();
				if (row instanceof TableRowPainted) {
					((TableRowPainted) row).subRowHeightChanged( oldHeight, newHeight);
				}
				
				if ( triggerHeightChange ){
					
					getViewPainted().rowHeightChanged(this, oldHeight, newHeight);
				}
				
				if (newSubRows != null) {
					getViewPainted().triggerListenerRowAdded(newSubRows);
				}

			}
			
			Object ds = getDataSource( true );
			
			if ( ds instanceof TableRowSWTChildController ){
				
				((TableRowSWTChildController)ds).setExpanded( b );
			}
			
			TableViewPainted tvp = getViewPainted();

			if ( triggerHeightChange ){
			
				tvp.tableMutated();
			}
			
			if (isVisible()) {

				tvp.visibleRowsChanged();
				tvp.redrawTable();
			}
		}
	}

	@Override
	public TableRowCore getSubRow(int pos) {
		synchronized (subRows_sync) {
			if (subRows == null) {
				return null;
			}
			if (pos >= 0 && pos < subRows.length) {
				return subRows[pos];
			}
			return null;
		}
	}


	@Override
	public Color getForeground() {
		if ( colorFG != null ){
			
			return( colorFG );
		}
		
		List<Object[]> list = FGRequesters.getList();
		
		if ( !list.isEmpty()){
			
			return((Color)list.get(0)[1]);
		}
		
		return( null );
	}

	@Override
	public Color 
	getBackground() 
	{
		List<Object[]> list = BGRequesters.getList();
		
		if ( !list.isEmpty()){
			
			return((Color)list.get(0)[1]);
		}
		
		return( null );	
	}

	@Override
	public void 
	requestForegroundColor(
		ColorRequester 	requester,
		Color			color )
	{
		requestColor( FGRequesters, requester, color );
	}
	
	@Override
	public void 
	requestBackgroundColor(
		ColorRequester	requester,
		Color			color )
	{
		requestColor( BGRequesters, requester, color );
	}
	
	private void
	requestColor(
		CopyOnWriteList<Object[]>	cow,
		ColorRequester				requester,
		Color						color )
	{
		boolean	changed = false;
		
		try{
			synchronized( colorLock ){
				
				int index 		= 0;
				int	insert_at	= -1;
				
				for ( Object[] entry: cow ){
				
					ColorRequester r = (ColorRequester)entry[0];
					
					if ( r == requester ){
						
						if ( color == null ){
					
							cow.remove( entry );
							
							changed = true;
							
						}else{
							
							if ( !color.equals( (Color)entry[1])){
								
								entry[1] = color;
								
								changed = true;
							}
						}
						
						return;
					}
					
					if ( insert_at == -1 && r.getPriority() < requester.getPriority()){
						
						insert_at = index;
					}
					
					index++;
				}
				
				if ( color != null ){
					
					Object[] new_entry = { requester, color };
					
					if ( insert_at >= 0 ){
						
						cow.add( insert_at, new_entry );
						
					}else{
						
						cow.add( new_entry );
					}
					
					changed = true;
				}
			}
		}finally{
			
			if ( changed ){
				
				Utils.getOffOfSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						redraw(false, false);
					}
				});
			}
		}
	}
	
	@Override
	public void setBackgroundImage(Image image) {
		//TODO
	}

	@Override
	public boolean setIconSize(Point pt) {
		//TODO
		return false;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#getHeight()
	 */
	@Override
	public int getHeight() {
		if ( isHidden ){
			return( 0 );
		}
		return heightUseAccessors == 0 ? getView().getRowDefaultHeight() : heightUseAccessors;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#setHeight(int)
	 */
	@Override
	public boolean setHeight(int newHeight) {
		TableRowCore parentRowCore = getParentRowCore();
		boolean trigger;
		
		if ( parentRowCore == null ){
			trigger = true;
			
		}else{
			boolean expanded = parentRowCore.isExpanded();
			
			while( expanded ){
				
				parentRowCore = parentRowCore.getParentRowCore();
				
				if ( parentRowCore == null ){
					
					break;
					
				}else{
					
					expanded = parentRowCore.isExpanded();
				}
			}
			
			trigger = expanded;
		}

		return setHeight(newHeight, trigger);
	}

	public boolean setHeight(int newHeight, boolean trigger) {
		if (heightUseAccessors == newHeight) {
			return false;
		}
		int oldHeightToReport = isHidden?0:heightUseAccessors;
		heightUseAccessors = newHeight;
		if (trigger && !initializing) {
			int heightToReport = isHidden?0:newHeight;
			if ( oldHeightToReport != heightToReport ){
				heightChanged(oldHeightToReport, heightToReport);
			}
		}

		return true;
	}

	@Override
	public int getLineHeight() {
		return getViewPainted().getLineHeight();
	}

	@Override
	public TableCellCore getTableCellCore(String name) {
		if (isRowDisposed()) {
			return null;
		}
	
		synchronized (lock) {
			if (mTableCells == null) {
				if (sortCells == null) {
					return null;
				}
				for (TableCellCore sortCell : sortCells) {
					if (!sortCell.isDisposed()
							&& name.equals(sortCell.getTableColumn().getName())) {
						return sortCell;
					}
				}
				return null;
			}
			return mTableCells.get(name);
		}
	}

	@Override
	public TableCellSWT getTableCellSWT(String name) {
		TableCellCore cell = getTableCellCore(name);
		return (cell instanceof TableCellSWT) ? (TableCellSWT) cell : null;
	}

	@Override
	public TableCell getTableCell(String field) {
		return getTableCellCore(field);
	}

	@Override
	public TableCellCore[] getSortColumnCells(String hint) {
		synchronized (lock) {
			return sortCells == null ? new TableCellCore[0] : sortCells;
		}
	}

	@Override
	public void setSortColumn(String... columnIDs) {
		synchronized (lock) {

			List<TableCellSWTBase> list = new ArrayList<>();

			if (mTableCells == null) {
				if (sortCells != null) {
					for (TableCellCore sortCell : sortCells) {
						if (!sortCell.isDisposed()) {
							sortCell.dispose();
						}
					}
				}

				TableViewPainted table = getViewPainted();

				for (String columnID : columnIDs) {
					TableColumnCore sortColumn = (TableColumnCore) getView().getTableColumn(
							columnID);
					if (sortColumn == null) {
						continue;
					}
					if (getParentRowCore() == null || sortColumn.handlesDataSourceType(
							getDataSource(false).getClass())) {
						list.add(new TableCellPainted(TableRowPainted.this, table.getColumnPainted( sortColumn ),
								sortColumn.getPosition()));
					}
				}
			} else {
				for (String columnID : columnIDs) {
					TableCellSWTBase cell = mTableCells.get(columnID);
					if (cell != null) {
						list.add(cell);
					}
				}
			}

			sortCells = list.toArray(new TableCellSWTBase[0]);
		}
		
		synchronized (subRows_sync) {
			
			if ( subRows != null ){
				
				for ( TableRowCore r: subRows ){
					
					r.setSortColumn(columnIDs);
				}
			}
		}
	}
	
	@Override
	public boolean sortSubRows(TableColumnCore col){
		synchronized (subRows_sync) {
			if ( subRows == null ){
				return( false );
			}
			
			boolean changed = false;
			boolean	sorted	= true;
					
			TableRowCore prev = null;
			
			for ( TableRowCore r: subRows ){
				
				if ( sorted ){
					
					if ( prev != null ){
					
						if ( col.compare( prev,  r ) > 0 ){
							
							sorted = false;
						}
					}
					
					prev = r;
				}
				
				if ( r.sortSubRows(col)){
					
					changed = true;
				}
			}
			
			if ( !sorted ){
			
				Arrays.sort( subRows, col );
				
				for ( int i=0; i<subRows.length;i++){
					subRows[i].setTableItem(i);
				}
				
				changed = true;
			}

			return( changed );
		}
	}
}
