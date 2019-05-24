/*
 * File    : NameItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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

package com.biglybt.ui.swt.columns.torrent;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.TorrentUIUtilsV3;
import com.biglybt.ui.swt.utils.TorrentUIUtilsV3.ContentImageLoadedListener;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

/** Torrent name cell for My Torrents.
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class ColumnThumbAndName
	extends CoreTableColumnSWT
	implements TableCellLightRefreshListener, ObfuscateCellText,
	TableCellDisposeListener, TableCellSWTPaintListener,
	TableCellClipboardListener, TableCellMouseMoveListener
{
	public static final Class<?>[] DATASOURCE_TYPES = {
		Download.class,
		com.biglybt.pif.disk.DiskManagerFileInfo.class
	};

	public static final String COLUMN_ID = "name";

	private static final String ID_EXPANDOHITAREASHOW = "expandoHitAreaShow";

	private static final boolean NEVER_SHOW_TWISTY =
		!COConfigurationManager.getBooleanParameter("Table.useTree");
	private final ParameterListener configShowProgramIconListener;

	private boolean showIcon;

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
			CAT_CONTENT
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/**
	 *
	 * @param sTableID
	 */
	public ColumnThumbAndName(String sTableID) {
		super(COLUMN_ID, 250, sTableID);
		setAlignment(ALIGN_LEAD);
		addDataSourceTypes(DATASOURCE_TYPES);
		setObfuscation(true);
		setRefreshInterval(INTERVAL_LIVE);
		initializeAsGraphic(250);
		setMinWidth(100);

		TableContextMenuItem menuItem = addContextMenuItem("MyTorrentsView.menu.rename.displayed");
		menuItem.setHeaderCategory(MenuItem.HEADER_CONTROL);
		menuItem.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				if (target == null) {
					return;
				}
				Object[] o = (Object[]) target;
				for (Object object : o) {
					if (object instanceof TableRowCore) {
						TableRowCore row = (TableRowCore) object;
						object = row.getDataSource(true);
					}
					if (object instanceof DownloadManager) {
						final DownloadManager dm = (DownloadManager) object;
						String msg_key_prefix = "MyTorrentsView.menu.rename.displayed.enter.";

						SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
								msg_key_prefix + "title", msg_key_prefix + "message");
						entryWindow.setPreenteredText(dm.getDisplayName(), false);
						entryWindow.maintainWhitespace( true );	// apparently users want to be able to prefix with spaces
						entryWindow.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
								if (!entryWindow.hasSubmittedInput()) {
									return;
								}
								String value = entryWindow.getSubmittedInput();
								if (value != null && value.length() > 0) {
									dm.getDownloadState().setDisplayName(value);
								}
							}
						});
					}
				}
			}
		});

		TableContextMenuItem menuShowIcon = addContextMenuItem(
				"ConfigView.section.style.showProgramIcon", MENU_STYLE_HEADER);
		menuShowIcon.setStyle(TableContextMenuItem.STYLE_CHECK);
		menuShowIcon.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setData(Boolean.valueOf(showIcon));
			}
		});
		final String CFG_SHOWPROGRAMICON = "NameColumn.showProgramIcon."
				+ getTableID();
		menuShowIcon.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				COConfigurationManager.setParameter(CFG_SHOWPROGRAMICON,
						((Boolean) menu.getData()).booleanValue());
			}
		});

		configShowProgramIconListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				setShowIcon(COConfigurationManager.getBooleanParameter(
						CFG_SHOWPROGRAMICON,
						COConfigurationManager.getBooleanParameter("NameColumn.showProgramIcon")));
			}
		};
		COConfigurationManager.addWeakParameterListener(
				configShowProgramIconListener, true, CFG_SHOWPROGRAMICON);
	}

	@Override
	public void reset() {
		super.reset();

		COConfigurationManager.removeParameter("NameColumn.showProgramIcon."
				+ getTableID());
	}

	@Override
	public void remove() {
		super.remove();

		final String CFG_SHOWPROGRAMICON = "NameColumn.showProgramIcon."
				+ getTableID();
		COConfigurationManager.removeWeakParameterListener(
				configShowProgramIconListener, CFG_SHOWPROGRAMICON);
	}

	@Override
	public void refresh(TableCell cell) {
		refresh(cell, false);
	}

	@Override
	public void refresh(TableCell cell, boolean sortOnlyRefresh) {
		String name = null;
		Object ds = cell.getDataSource();
		if (ds instanceof DiskManagerFileInfo) {
			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
			if (fileInfo.isSkipped()
					&& (fileInfo.getStorageType() == DiskManagerFileInfo.ST_COMPACT || fileInfo.getStorageType() == DiskManagerFileInfo.ST_REORDER_COMPACT)) {
				TableRowCore row = (TableRowCore) cell.getTableRow();
				if (row != null) {
					row.getParentRowCore().removeSubRow(ds);
				}
			}
			return;
		}
		DownloadManager dm = (DownloadManager) ds;
		if (dm != null) {
			name = dm.getDisplayName();
		}
		if (name == null) {
			name = "";
		}

		cell.setSortValue(name);
	}

	@Override
	public void cellPaint(GC gc, final TableCellSWT cell) {
		Object ds = cell.getDataSource();
		if (ds instanceof DiskManagerFileInfo) {
			cellPaintFileInfo(gc, cell, (DiskManagerFileInfo) ds);
			return;
		}

		Rectangle cellBounds = cell.getBounds();

		int	originalBoundxsX = cellBounds.x;
		
		int textX = originalBoundxsX;

		TableRowCore rowCore = cell.getTableRowCore();
		if (rowCore != null) {
			int numSubItems = rowCore.getSubItemCount();
			int paddingX = 3;
			int width = 7;

			boolean	show_twisty;

			if ( NEVER_SHOW_TWISTY ){

				show_twisty = false;

			}else if (numSubItems > 1 ){

				show_twisty = true;
			}else{

				Boolean show = (Boolean)rowCore.getData( ID_EXPANDOHITAREASHOW );

				if ( show == null ){

					DownloadManager dm = (DownloadManager)ds;

					TOTorrent torrent = dm.getTorrent();

					show_twisty = torrent != null && !dm.getTorrent().isSimpleTorrent();

					rowCore.setData( ID_EXPANDOHITAREASHOW, Boolean.valueOf(show_twisty));
					
				}else{
					show_twisty = show;
				}
			}

			if (show_twisty) {
				int middleY = cellBounds.y + (cellBounds.height / 2) - 1;
				int startX = cellBounds.x + paddingX;
				int halfHeight = 2;
				Color bg = gc.getBackground();
				gc.setBackground(gc.getForeground());
				gc.setAntialias(SWT.ON);
				gc.setAdvanced(true);
				if (rowCore.isExpanded()) {
					gc.fillPolygon(new int[] {
						startX,
						middleY - halfHeight,
						startX + width,
						middleY - halfHeight,
						startX + (width / 2),
						middleY + (halfHeight * 2) + 1
					});
				} else {
					gc.fillPolygon(new int[] {
						startX,
						middleY - halfHeight,
						startX + width,
						middleY + halfHeight,
						startX,
						middleY + (halfHeight * 2) + 1
					});
				}
				gc.setBackground(bg);
				//Rectangle hitArea = new Rectangle(paddingX, middleY - halfHeight
				//		- cellBounds.y, width, (halfHeight * 4) + 1);
				// expando is quite small, make it easier to hit
				Rectangle hitArea = new Rectangle(0, 0, width + paddingX * 2, cellBounds.height);
				rowCore.setData( TableRowCore.ID_EXPANDOHITAREA, hitArea);
				rowCore.setData( TableRowCore.ID_EXPANDOHITCOLUMN, getName());
			}

			if (!NEVER_SHOW_TWISTY) {
				cellBounds.x += paddingX * 2 + width;
				cellBounds.width -= paddingX * 2 + width;
			}
		}

		if (!showIcon) {
			cellBounds.x += 2;
			cellBounds.width -= 4;
			cellPaintName(cell, gc, cellBounds, cellBounds.x, originalBoundxsX);
			return;
		}

		Image[] imgThumbnail = TorrentUIUtilsV3.getContentImage(ds,
				cellBounds.height >= 20, new ContentImageLoadedListener() {
					@Override
					public void contentImageLoaded(Image image, boolean wasReturned) {
						if (!wasReturned) {
							// this may be triggered many times, so only invalidate and don't
							// force a refresh()
							cell.invalidate();
						}
					}
				});

		if (imgThumbnail != null && ImageLoader.isRealImage(imgThumbnail[0])) {
			try {

				if (cellBounds.height > 30) {
					cellBounds.y += 1;
					cellBounds.height -= 3;
				}
				Rectangle imgBounds = imgThumbnail[0].getBounds();

				int dstWidth;
				int dstHeight;
				if (imgBounds.height > cellBounds.height) {
					dstHeight = cellBounds.height;
					dstWidth = imgBounds.width * cellBounds.height / imgBounds.height;
				} else if (imgBounds.width > cellBounds.width) {
					dstWidth = cellBounds.width - 4;
					dstHeight = imgBounds.height * cellBounds.width / imgBounds.width;
				} else {
					dstWidth = imgBounds.width;
					dstHeight = imgBounds.height;
				}

				if (cellBounds.height <= 18) {
					dstWidth = Math.min(dstWidth, cellBounds.height);
					dstHeight = Math.min(dstHeight, cellBounds.height);
					if (imgBounds.width > 16) {
						cellBounds.y++;
						dstHeight -= 2;
					}
				}

				try {
					gc.setAdvanced(true);
					gc.setInterpolation(SWT.HIGH);
				} catch (Exception e) {
				}
				int x = cellBounds.x;
				textX = x + dstWidth + 3;
				int minWidth = dstHeight * 7 / 4;
				int imgPad = 0;
				if (dstHeight > 25) {
					if (dstWidth < minWidth) {
						imgPad = ((minWidth - dstWidth + 1) / 2);
						x = cellBounds.x + imgPad;
						textX = cellBounds.x + minWidth + 3;
					}
				}
				if (cellBounds.width - dstWidth - (imgPad * 2) < 100 && dstHeight > 18) {
					dstWidth = Math.min(32, dstHeight);
					x = cellBounds.x + ((32 - dstWidth + 1) / 2);
					dstHeight = imgBounds.height * dstWidth / imgBounds.width;
					textX = cellBounds.x + dstWidth + 3;
				}
				int y = cellBounds.y + ((cellBounds.height - dstHeight + 1) / 2);
				if (dstWidth > 0 && dstHeight > 0 && !imgBounds.isEmpty()) {
					//Rectangle dst = new Rectangle(x, y, dstWidth, dstHeight);
					Rectangle lastClipping = gc.getClipping();
					try {
						Utils.setClipping(gc, cellBounds);

						boolean hack_adv = Constants.isWindows8OrHigher && gc.getAdvanced();

						if ( hack_adv ){
								// problem with icon transparency on win8
							gc.setAdvanced( false );
						}

						for (int i = 0; i < imgThumbnail.length; i++) {
							Image image = imgThumbnail[i];
							if (image == null || image.isDisposed()) {
								continue;
							}
							Rectangle srcBounds = image.getBounds();
							if (i == 0) {
								int w = dstWidth;
								int h = dstHeight;
								if (imgThumbnail.length > 1) {
									w = w * 9 / 10;
									h = h * 9 / 10;
								}
								gc.drawImage(image, srcBounds.x, srcBounds.y, srcBounds.width,
										srcBounds.height, x, y, w, h);
							} else {
								int w = dstWidth * 3 / 8;
								int h = dstHeight * 3 / 8;
								gc.drawImage(image, srcBounds.x, srcBounds.y, srcBounds.width,
										srcBounds.height, x + dstWidth - w, y + dstHeight - h, w, h);
							}
						}

						if ( hack_adv ){
							gc.setAdvanced( true );
						}
					} catch (Exception e) {
						Debug.out(e);
					} finally {
						Utils.setClipping(gc, lastClipping);
					}
				}

				TorrentUIUtilsV3.releaseContentImage(ds);
			} catch (Throwable t) {
				Debug.out(t);
			}
		}

		cellPaintName(cell, gc, cellBounds, textX, originalBoundxsX);
	}

	private void cellPaintFileInfo(GC gc, final TableCellSWT cell,
			DiskManagerFileInfo fileInfo) {
		Rectangle cellBounds = cell.getBounds();
		
		int	originalBoundxsX = cellBounds.x;
		
		//System.out.println(cellArea);
		int padding = 5 + (true ? cellBounds.height : 0);
		cellBounds.x += padding;
		cellBounds.width -= padding;

		int textX = cellBounds.x;

		Image[] imgThumbnail = { ImageRepository.getPathIcon(fileInfo.getFile(true).getPath(),
				cellBounds.height >= 20, false) };

		if (imgThumbnail != null && ImageLoader.isRealImage(imgThumbnail[0])) {
			try {

				if (cellBounds.height > 30) {
					cellBounds.y += 1;
					cellBounds.height -= 3;
				}
				Rectangle imgBounds = imgThumbnail[0].getBounds();

				int dstWidth;
				int dstHeight;
				if (imgBounds.height > cellBounds.height) {
					dstHeight = cellBounds.height;
					dstWidth = imgBounds.width * cellBounds.height / imgBounds.height;
				} else if (imgBounds.width > cellBounds.width) {
					dstWidth = cellBounds.width - 4;
					dstHeight = imgBounds.height * cellBounds.width / imgBounds.width;
				} else {
					dstWidth = imgBounds.width;
					dstHeight = imgBounds.height;
				}

				if (cellBounds.height <= 18) {
					dstWidth = Math.min(dstWidth, cellBounds.height);
					dstHeight = Math.min(dstHeight, cellBounds.height);
					if (imgBounds.width > 16) {
						cellBounds.y++;
						dstHeight -= 2;
					}
				}

				try {
					gc.setAdvanced(true);
					gc.setInterpolation(SWT.HIGH);
				} catch (Exception e) {
				}
				int x = cellBounds.x;
				textX = x + dstWidth + 3;
				int minWidth = dstHeight;
				int imgPad = 0;
				if (dstHeight > 25) {
					if (dstWidth < minWidth) {
						imgPad = ((minWidth - dstWidth + 1) / 2);
						x = cellBounds.x + imgPad;
						textX = cellBounds.x + minWidth + 3;
					}
				}
				if (cellBounds.width - dstWidth - (imgPad * 2) < 100 && dstHeight > 18) {
					dstWidth = Math.min(32, dstHeight);
					x = cellBounds.x + ((32 - dstWidth + 1) / 2);
					dstHeight = imgBounds.height * dstWidth / imgBounds.width;
					textX = cellBounds.x + dstWidth + 3;
				}
				int y = cellBounds.y + ((cellBounds.height - dstHeight + 1) / 2);
				if (dstWidth > 0 && dstHeight > 0 && !imgBounds.isEmpty()) {
					//Rectangle dst = new Rectangle(x, y, dstWidth, dstHeight);
					Rectangle lastClipping = gc.getClipping();
					try {
						Utils.setClipping(gc, cellBounds);

						boolean hack_adv = Constants.isWindows8OrHigher && gc.getAdvanced();

						if ( hack_adv ){
								// problem with icon transparency on win8
							gc.setAdvanced( false );
						}

						for (int i = 0; i < imgThumbnail.length; i++) {
							Image image = imgThumbnail[i];
							if (image == null || image.isDisposed()) {
								continue;
							}
							Rectangle srcBounds = image.getBounds();
							if (i == 0) {
								int w = dstWidth;
								int h = dstHeight;
								if (imgThumbnail.length > 1) {
									w = w * 9 / 10;
									h = h * 9 / 10;
								}
								gc.drawImage(image, srcBounds.x, srcBounds.y, srcBounds.width,
										srcBounds.height, x, y, w, h);
							} else {
								int w = dstWidth * 3 / 8;
								int h = dstHeight * 3 / 8;
								gc.drawImage(image, srcBounds.x, srcBounds.y, srcBounds.width,
										srcBounds.height, x + dstWidth - w, y + dstHeight - h, w, h);
							}
						}

						if ( hack_adv ){
							gc.setAdvanced( true );
						}
					} catch (Exception e) {
						Debug.out(e);
					} finally {
						Utils.setClipping(gc, lastClipping);
					}
				}

			} catch (Throwable t) {
				Debug.out(t);
			}
		}



		String prefix = fileInfo.getDownloadManager().getSaveLocation().toString() + File.separator;
		String s = fileInfo.getFile(true).toString();
		if (s.startsWith(prefix)) {
			s = s.substring(prefix.length());
		}
		if ( fileInfo.isSkipped()){

	    	String dnd_sf = fileInfo.getDownloadManager().getDownloadState().getAttribute( DownloadManagerState.AT_DND_SUBFOLDER );

	    	if ( dnd_sf != null ){

	    		dnd_sf = dnd_sf.trim();

	    		if ( dnd_sf.length() > 0 ){

	    			dnd_sf += File.separatorChar;

	    			int pos = s.indexOf( dnd_sf );

	    			if ( pos != -1 ){

	    				s = s.substring( 0, pos ) + s.substring( pos + dnd_sf.length());
	    			}
	    		}
	    	}
		}

		cellBounds.width -= (textX - cellBounds.x);
		cellBounds.x = textX;

		GCStringPrinter sp = new GCStringPrinter(gc, s, cellBounds, true, false,
				SWT.LEFT | SWT.WRAP);
		
		boolean over = sp.printString();
		
		Point p = sp.getCalculatedPreferredSize();
		
		int pref = ( textX - originalBoundxsX ) +  p.x + 10;
		
		TableColumn tableColumn = cell.getTableColumn();
		if (tableColumn != null && tableColumn.getPreferredWidth() < pref) {
			tableColumn.setPreferredWidth(pref);
		}
		
		cell.setToolTip(over ? null : s);
	}

	private void cellPaintName(TableCell cell, GC gc, Rectangle cellBounds,
			int textX, int originalBoundxsX ) {
		String name = null;
		Object ds = cell.getDataSource();
		if (ds instanceof DiskManagerFileInfo) {
			return;
		}
		DownloadManager dm = (DownloadManager) ds;
		if (dm != null)
			name = dm.getDisplayName();
		if (name == null)
			name = "";

		GCStringPrinter sp = new GCStringPrinter(gc, name, new Rectangle(textX,
				cellBounds.y, cellBounds.x + cellBounds.width - textX,
				cellBounds.height), true, true, getTableID().endsWith( ".big" )?SWT.WRAP:SWT.NULL );
		
		boolean fit = sp.printString();

		Point p = sp.getCalculatedPreferredSize();
			
		int pref = ( textX - originalBoundxsX ) +  p.x + 10;
		
		TableColumn tableColumn = cell.getTableColumn();
		if (tableColumn != null && tableColumn.getPreferredWidth() < pref) {
			tableColumn.setPreferredWidth(pref);
		}
		
		String tooltip = fit?"":name;

		if (dm != null) {
			try{
				String desc = PlatformTorrentUtils.getContentDescription( dm.getTorrent());

				if ( desc != null && desc.length() > 0 ){
					tooltip += (tooltip.length()==0?"":"\r\n") + desc;
				}
			}catch( Throwable e ){
			}
		}
		cell.setToolTip(tooltip.length()==0?null:tooltip);
	}

	@Override
	public String getObfuscatedText(TableCell cell) {
		Object ds = cell.getDataSource();
		if (ds instanceof DiskManagerFileInfo) {
			return( UIDebugGenerator.obfuscateFileName((DiskManagerFileInfo)ds ));
		}else{
			return( UIDebugGenerator.obfuscateDownloadName( ds ));
		}
	}

	@Override
	public void dispose(TableCell cell) {

	}

	/**
	 * @param showIcon the showIcon to set
	 */
	public void setShowIcon(boolean showIcon) {
		this.showIcon = showIcon;
		invalidateCells();
	}

	/**
	 * @return the showIcon
	 */
	public boolean isShowIcon() {
		return showIcon;
	}

	@Override
	public String getClipboardText(TableCell cell) {
		String name = null;
		Object ds = cell.getDataSource();
		if (ds instanceof DiskManagerFileInfo) {
			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)ds;
			
			return( fileInfo.getFile(true).getName());
		}
		DownloadManager dm = (DownloadManager) ds;
		if (dm != null)
			name = dm.getDisplayName();
		if (name == null)
			name = "";
		return name;
	}

	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {
		if (event.eventType == TableCellMouseEvent.EVENT_MOUSEMOVE
				|| event.eventType == TableRowMouseEvent.EVENT_MOUSEDOWN) {
			TableRow row = event.cell.getTableRow();
			if (row == null) {
				return;
			}
			Object data = row.getData(TableRowCore.ID_EXPANDOHITAREA);
			if (data instanceof Rectangle) {
				Rectangle hitArea = (Rectangle) data;
				boolean inExpando = hitArea.contains(event.x, event.y);

				if (event.eventType == TableCellMouseEvent.EVENT_MOUSEMOVE) {
					((TableCellCore) event.cell).setCursorID(inExpando ? SWT.CURSOR_HAND
							: SWT.CURSOR_ARROW);
				} else if (inExpando) { // mousedown
					if (row instanceof TableRowCore) {
						TableRowCore rowCore = (TableRowCore) row;
						rowCore.setExpanded(!rowCore.isExpanded());
					}
				}
			}
		}
	}
}
