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

package com.biglybt.ui.swt.columns.torrent;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerEnhancer;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.EnhancedDownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinProperties;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.ViewUtils;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

/**
 * @author TuxPaper
 * @created Jun 13, 2006
 *
 */
public class ColumnProgressETAOld
	extends CoreTableColumnSWT
	implements TableCellAddedListener, TableCellMouseListener,
	TableCellRefreshListener, TableCellSWTPaintListener
{
	public static final Class DATASOURCE_TYPE = DownloadTypeIncomplete.class;

	public static final String COLUMN_ID = "ProgressETAOld";
	public static final long SHOW_ETA_AFTER_MS = 30000;
	private static final String CFG_SHOWETA = "ColumnProgressETA.showETA";
	private static final String CFG_SHOWSPEED = "ColumnProgressETA.showSpeed";
	private static final String CFG_SHOW3D = "ColumnProgressETA.show3D";
	private static final int borderWidth = 1;
	private static final int COLUMN_WIDTH = 200;
	private final static Object CLICK_KEY = new Object();
	private static Font fontText = null;
	private final MyParameterListener myParameterListener;
	private boolean showETA;
	private boolean showSpeed;
	private boolean show3D;
	Display display;
	Color textColor;
	private Color cBGdl;
	private Color cBGcd;
	private Color cBorder;
	private Color cText;
	private Image imgBGTorrent;
	private Color cTextDrop;
	private ViewUtils.CustomDateFormat cdf;
	private ColumnTorrentFileProgress fileProgress;
	private boolean progress_eta_absolute;

	/**
	 *
	 */
	public ColumnProgressETAOld(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, COLUMN_WIDTH, sTableID);
		addDataSourceType(DiskManagerFileInfo.class);
		initializeAsGraphic(COLUMN_WIDTH);
		setAlignment(ALIGN_LEAD);
		setPosition(POSITION_INVISIBLE);
		setMinWidth(100);

		myParameterListener = new MyParameterListener();
		COConfigurationManager.addWeakParameterListener(myParameterListener, true,
				"mtv.progress_eta.show_absolute");

		display = Utils.getDisplay();

		SWTSkinProperties skinProperties = SWTSkinFactory.getInstance().getSkinProperties();
		cBGdl = skinProperties.getColor("color.progress.bg.dl");
		if (cBGdl == null) {
			cBGdl = Colors.blues[Colors.BLUES_DARKEST];
		}
		cBGcd = skinProperties.getColor("color.progress.bg.cd");
		if (cBGcd == null) {
			cBGcd = Colors.green;
		}
		cBorder = skinProperties.getColor("color.progress.border");
		if (cBorder == null) {
			cBorder = Colors.grey;
		}
		cText = skinProperties.getColor("color.progress.text");
		if (cText == null) {
			cText = Colors.black;
		}
		cTextDrop = skinProperties.getColor("color.progress.text.drop");

		cdf = ViewUtils.addCustomDateFormat(this);

		ImageLoader imageLoader = ImageLoader.getInstance();
		imgBGTorrent = imageLoader.getImage("image.progress.bg.torrent");

		fileProgress = new ColumnTorrentFileProgress(display);

		TableContextMenuItem menuShowETA = addContextMenuItem(
				"ColumnProgressETA.showETA", MENU_STYLE_HEADER);
		menuShowETA.setStyle(TableContextMenuItem.STYLE_CHECK);
		menuShowETA.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setData(Boolean.valueOf(showETA));
			}
		});
		menuShowETA.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				showETA = ((Boolean) menu.getData()).booleanValue();
				setUserData(CFG_SHOWETA, showETA ? 1 : 0);
				invalidateCells();
			}
		});

		TableContextMenuItem menuShowSpeed = addContextMenuItem(
				"ColumnProgressETA.showSpeed", MENU_STYLE_HEADER);
		menuShowSpeed.setStyle(TableContextMenuItem.STYLE_CHECK);
		menuShowSpeed.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setData(Boolean.valueOf(showSpeed));
			}
		});
		menuShowSpeed.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				showSpeed = ((Boolean) menu.getData()).booleanValue();
				setUserData(CFG_SHOWSPEED, showSpeed ? 1 : 0);
				invalidateCells();
			}
		});

		TableContextMenuItem menuShow3D = addContextMenuItem(
				"ColumnProgressETA.show3D", MENU_STYLE_HEADER);
		menuShow3D.setStyle(TableContextMenuItem.STYLE_CHECK);
		menuShow3D.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setData(Boolean.valueOf(show3D));
			}
		});
		menuShow3D.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				show3D = ((Boolean) menu.getData()).booleanValue();
				setUserData(CFG_SHOW3D, show3D ? 1 : 0);
				invalidateCells();
			}
		});
	}

	@Override
	public void remove() {
		super.remove();

		COConfigurationManager.removeWeakParameterListener(myParameterListener,
				"mtv.progress_eta.show_absolute");
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
			CAT_ESSENTIAL,
			CAT_TIME,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void cellAdded(TableCell cell) {
		cell.setMarginHeight(3);
		cell.setMarginWidth(2);
	}

	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {

		Object ds = event.cell.getDataSource();
		if (ds instanceof DiskManagerFileInfo) {
			fileProgress.fileInfoMouseTrigger(event);
			return;
		}

		DownloadManager dm = (DownloadManager) ds;
		if (dm == null) {
			return;
		}

		String clickable = (String) dm.getUserData(CLICK_KEY);

		if (clickable == null) {

			return;
		}

		event.skipCoreFunctionality = true;

		if (event.eventType == TableCellMouseEvent.EVENT_MOUSEUP) {

			String url = UrlUtils.getURL(clickable);

			if (url != null) {

				Utils.launch(url);
			}
		}
	}

	@Override
	public void refresh(TableCell cell) {
		Object ds = cell.getDataSource();

		// needs to be long so we can shift it for sortVal
		long percentDone = getPercentDone(ds);

		long sortValue = 0;

		if (ds instanceof DownloadManager) {
			DownloadManager dm = (DownloadManager) cell.getDataSource();
			// close enough to unique with abs..
			int hashCode = Math.abs(
					DisplayFormatters.formatDownloadStatus(dm).hashCode());

			long completedTime = dm.getDownloadState().getLongParameter(
					DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
			if (completedTime <= 0 || !dm.isDownloadComplete(false)) {
				sortValue = (percentDone << 31) + hashCode;
			} else {
				sortValue = ((completedTime / 1000) << 31) + hashCode;
			}

		} else if (ds instanceof DiskManagerFileInfo) {
			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
			int st = fileInfo.getStorageType();
			if ((st == DiskManagerFileInfo.ST_COMPACT
					|| st == DiskManagerFileInfo.ST_REORDER_COMPACT)
					&& fileInfo.isSkipped()) {
				sortValue = 1;
			} else if (fileInfo.isSkipped()) {
				sortValue = 2;
			} else if (fileInfo.getPriority() > 0) {

				int pri = fileInfo.getPriority();
				sortValue = 4;

				if (pri > 1) {
					sortValue += pri;
				}
			} else {
				sortValue = 3;
			}
			sortValue = (fileInfo.getDownloadManager().getState() * 10000)
					+ percentDone + sortValue;
		}

		long eta = showETA ? getETA(cell) : 0;
		long speed = showSpeed ? getSpeed(ds) : 0;

		//System.out.println("REFRESH " + sortValue + ";" + ds);
		Comparable old = cell.getSortValue();
		boolean sortChanged = cell.setSortValue(sortValue);

		if (sortChanged && old != null && !(old instanceof String)) {
			UIFunctionsManagerSWT.getUIFunctionsSWT().refreshIconBar();
		}

		long lastETA = 0;
		long lastSpeed = 0;
		TableRow row = cell.getTableRow();
		if (row != null) {
			if (showETA) {
				Object data = row.getData("lastETAOld");
				if (data instanceof Number) {
					lastETA = ((Number) data).longValue();
				}
				row.setData("lastETAOld", new Long(eta));
			}
			if (showSpeed) {
				Object data = row.getData("lastSpeedOld");
				if (data instanceof Number) {
					lastSpeed = ((Number) data).longValue();
				}
				row.setData("lastSpeedOld", new Long(speed));
			}
		}

		if (!sortChanged && (lastETA != eta || lastSpeed != speed)) {
			cell.invalidate();
		}
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.ui.swt.views.table.TableCellSWT)
	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		Object ds = cell.getDataSource();
		if (ds instanceof DiskManagerFileInfo) {
			TableRowCore row = cell.getTableRowCore();
			if (row != null) {
				fileProgress.fillInfoProgressETA(row, gc, (DiskManagerFileInfo) ds,
						cell.getBounds());
			}
			return;
		}

		if (!(ds instanceof DownloadManager)) {
			return;
		}

		DownloadManager dm = (DownloadManager)ds;

		String tooltip = null;
		
		int dm_state = dm.getState();
		
		if ( dm_state == DownloadManager.STATE_QUEUED ){
			
			tooltip = MessageText.getString( "ManagerItem.queued.tooltip" );
		}
		
		int percentDone = getPercentDone(ds);
		long eta = showETA ? getETA(cell) : 0;

		//Compute bounds ...
		int newWidth = cell.getWidth();
		if (newWidth <= 0) {
			return;
		}
		int newHeight = cell.getHeight();

		Color fgFirst = gc.getForeground();

		final Color fgOriginal = fgFirst;

		Rectangle cellBounds = cell.getBounds();

		int xStart = cellBounds.x;
		int yStart = cellBounds.y;

		int xRelProgressFillStart = borderWidth;
		int yRelProgressFillStart = borderWidth;
		int xRelProgressFillEnd = newWidth - xRelProgressFillStart - borderWidth;
		int yRelProgressFillEnd = yRelProgressFillStart + 13;
		boolean showSecondLine = yRelProgressFillEnd + 10 < newHeight;

		if (xRelProgressFillEnd < 10) {
			return;
		}
		String sStatusLine = null;

		// Draw Progress bar
		// ImageLoader imageLoader = ImageLoader.getInstance();

		Rectangle boundsImgBG;

		if (!ImageLoader.isRealImage(imgBGTorrent)) {
			boundsImgBG = new Rectangle(0, 0, 0, 13);
		} else {
			boundsImgBG = imgBGTorrent.getBounds();
		}

		if (fontText == null) {
			int wantHeightPX = boundsImgBG.height - 3;
			fontText = FontUtils.getFontWithHeight(gc.getFont(), wantHeightPX, SWT.DEFAULT);
		}

		if (!showSecondLine) {
			yRelProgressFillStart = (cellBounds.height / 2)
					- ((boundsImgBG.height) / 2);
		}

		yRelProgressFillEnd = yRelProgressFillStart + boundsImgBG.height;

		int progressWidth = newWidth - 1;
		gc.setForeground(cBorder);
		gc.drawRectangle(xStart + xRelProgressFillStart - 1,
				yStart + yRelProgressFillStart - 1, progressWidth + 1,
				boundsImgBG.height + 1);

		int pctWidth = (int) (percentDone * (progressWidth) / 1000);
		if ( dm_state == DownloadManager.STATE_DOWNLOADING || dm_state == DownloadManager.STATE_SEEDING ){
		
			gc.setBackground(
				percentDone == 1000 || dm.isDownloadComplete(false) ? cBGcd : cBGdl);
		}else{
			gc.setBackground( Colors.light_grey );
		}
		gc.fillRectangle(xStart + xRelProgressFillStart,
				yStart + yRelProgressFillStart, pctWidth, boundsImgBG.height);
		if (progressWidth > pctWidth) {
			gc.setBackground(Colors.white);
			gc.fillRectangle(xStart + xRelProgressFillStart + pctWidth,
					yStart + yRelProgressFillStart, progressWidth - pctWidth,
					boundsImgBG.height);
		}

		if (boundsImgBG.width > 0 && show3D) {
			gc.drawImage(imgBGTorrent, 0, 0, boundsImgBG.width, boundsImgBG.height,
					xStart + xRelProgressFillStart, yStart + yRelProgressFillStart,
					progressWidth, boundsImgBG.height);
		}

		if (sStatusLine == null) {
			if (dm.isUnauthorisedOnTracker()) {
				sStatusLine = dm.getTrackerStatus();
				// fgFirst = Colors.colorError;	pftt, no colours allowed apparently
			} else {
				if (showETA && eta > 0) {
					String sETA = cdf.formatETA( eta, progress_eta_absolute );
					
					sStatusLine = MessageText.getString(
							"MyTorrents.column.ColumnProgressETA.2ndLine", new String[] {
								sETA
							});
				} else {
					sStatusLine = DisplayFormatters.formatDownloadStatus(
							dm).toUpperCase();
				}
			}

			int cursor_id;

			if (sStatusLine != null && !sStatusLine.contains("http://")) {

				dm.setUserData(CLICK_KEY, null);

				cursor_id = SWT.CURSOR_ARROW;

			} else {

				dm.setUserData(CLICK_KEY, sStatusLine);

				cursor_id = SWT.CURSOR_HAND;

				if (!cell.getTableRow().isSelected()) {

					fgFirst = Colors.blue;
				}
			}

			((TableCellSWT) cell).setCursorID(cursor_id);
		}

		gc.setTextAntialias(SWT.ON);
		gc.setFont(fontText);
		if (showSecondLine && sStatusLine != null) {
			gc.setForeground(fgFirst);
			boolean fit = GCStringPrinter.printString(gc, sStatusLine,
					new Rectangle(cellBounds.x, yStart + yRelProgressFillEnd,
							cellBounds.width, newHeight - yRelProgressFillEnd),
					true, false, SWT.CENTER);
			if ( !fit ){
				if ( tooltip == null ){
					tooltip = sStatusLine;
				}else{
					tooltip = sStatusLine + ": " + tooltip;
				}
			}
			gc.setForeground(fgOriginal);
		}

		String sSpeed = "";
		if (showSpeed) {
			long lSpeed = getSpeed(ds);
			if (lSpeed > 0) {
				sSpeed = " ("
						+ DisplayFormatters.formatByteCountToKiBEtcPerSec(lSpeed, true)
						+ ")";
			}
		}

		String sPercent = DisplayFormatters.formatPercentFromThousands(percentDone);

		Rectangle area = new Rectangle(xStart + xRelProgressFillStart + 3,
				yStart + yRelProgressFillStart,
				xRelProgressFillEnd - xRelProgressFillStart - 6,
				yRelProgressFillEnd - yRelProgressFillStart);
		GCStringPrinter sp = new GCStringPrinter(gc, sPercent + sSpeed, area, true,
				false, SWT.LEFT);
		if (cTextDrop != null) {
			area.x++;
			area.y++;
			gc.setForeground(cTextDrop);
			sp.printString();
			area.x--;
			area.y--;
		}
		gc.setForeground(cText);
		sp.printString();
		Point pctExtent = sp.getCalculatedSize();

		area.width -= (pctExtent.x + 3);
		area.x += (pctExtent.x + 3);

		if (!showSecondLine && sStatusLine != null) {
			boolean fit = GCStringPrinter.printString(gc, sStatusLine,
					area.intersection(cellBounds), true, false, SWT.RIGHT);
						
			if ( !fit ){
				if ( tooltip == null ){
					tooltip = sStatusLine;
				}else{
					tooltip = sStatusLine + ": " + tooltip;
				}
			}
		}

		cell.setToolTip( tooltip );
		
		gc.setFont(null);
	}

	private int getPercentDone(Object ds) {
		if (ds instanceof DownloadManager) {
			return ((DownloadManager) ds).getStats().getPercentDoneExcludingDND();
		} else if (ds instanceof DiskManagerFileInfo) {
			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
			long length = fileInfo.getLength();
			if (length == 0) {
				return 1000;
			}
			return (int) (fileInfo.getDownloaded() * 1000 / length);
		}
		return 0;
	}

	private long getETA(TableCell cell) {
		Object ds = cell.getDataSource();
		if (ds instanceof DiskManagerFileInfo) {
			return 0;
		}
		DownloadManager dm = (DownloadManager) cell.getDataSource();

		long diff = SystemTime.getCurrentTime() - dm.getStats().getTimeStarted();
		if (diff > SHOW_ETA_AFTER_MS) {
			return dm.getStats().getSmoothedETA();
		}
		return 0;
	}

	private int getState(TableCell cell) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		if (dm == null) {
			return DownloadManager.STATE_ERROR;
		}
		return dm.getState();
	}

	private long getSpeed(Object ds) {
		if (!(ds instanceof DownloadManager)) {
			return 0;
		}

		return ((DownloadManager) ds).getStats().getDataReceiveRate();
	}

	public EnhancedDownloadManager getEDM(DownloadManager dm) {
		DownloadManagerEnhancer dmEnhancer = DownloadManagerEnhancer.getSingleton();
		if (dmEnhancer == null) {
			return null;
		}
		return dmEnhancer.getEnhancedDownload(dm);
	}

	private void log(TableCell cell, String s) {
		System.out.println(((TableRowCore) cell.getTableRow()).getIndex() + ":"
				+ System.currentTimeMillis() + ": " + s);
	}

	@Override
	public void postConfigLoad() {
		super.postConfigLoad();

		Object oShowETA = getUserData(CFG_SHOWETA);
		if (oShowETA == null) {
			showETA = false; // we could read a global default from somewhere
		} else if (oShowETA instanceof Number) {
			showETA = ((Number) oShowETA).intValue() == 1;
		}

		Object oShowSpeed = getUserData(CFG_SHOWSPEED);
		if (oShowSpeed == null) {
			showSpeed = false; // we could read a global default from somewhere
		} else if (oShowSpeed instanceof Number) {
			showSpeed = ((Number) oShowSpeed).intValue() == 1;
		}

		Object oShow3D = getUserData(CFG_SHOW3D);
		if (oShow3D == null) {
			show3D = true; // we could read a global default from somewhere
		} else if (oShow3D instanceof Number) {
			show3D = ((Number) oShow3D).intValue() == 1;
		}
		
		cdf.update();
	}

	private class MyParameterListener
		implements ParameterListener
	{
		@Override
		public void parameterChanged(String name) {
			progress_eta_absolute = COConfigurationManager.getBooleanParameter(
					"mtv.progress_eta.show_absolute", false);
		}
	}
}
