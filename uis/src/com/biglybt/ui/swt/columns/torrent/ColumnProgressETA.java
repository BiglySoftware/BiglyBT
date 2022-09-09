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
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinProperties;
import com.biglybt.ui.swt.utils.ColorCache;
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
public class ColumnProgressETA
	extends CoreTableColumnSWT
	implements TableCellAddedListener, TableCellMouseListener,
	TableCellRefreshListener, TableCellSWTPaintListener
{
	public static final Class DATASOURCE_TYPE = DownloadTypeIncomplete.class;

	public static final String COLUMN_ID = "ProgressETA";
	public static final long SHOW_ETA_AFTER_MS = 30000;
	private static final String CFG_SHOWETA = "ColumnProgressETA.showETA";
	private static final String CFG_SHOWSPEED = "ColumnProgressETA.showSpeed";
	private static final String CFG_SHOW3D = "ColumnProgressETA.show3D";
	private static final int borderWidth = 1;
	private static final int COLUMN_WIDTH = 200;
	private final static Object CLICK_KEY = new Object();
	private static Font fontText = null;
	private static int textHeightPX = 0;
	private static Font fontSecondLine = null;
	private static int secondLineHeightPX = 0;
	private final MyParameterListener myParameterListener;
	private boolean showETA;
	private boolean showSpeed;
	private boolean show3D;
	Display display;
	private Color cBase;
	
	
	private final int COLOR_DL	= 0;
	private final int COLOR_CD	= 1;
	private final int COLOR_QU	= 2;
	private final int COLOR_OFF	= 3;
	
	private Color[] cDefaults	= new Color[4];
	private Color[] cExplicits	= new Color[4];

	private Color cBorder;
	private Color cText;
	private Color cTextDrop;
	private Color cLinks;
	private ViewUtils.CustomDateFormat cdf;
	private ColumnTorrentFileProgress fileProgress;
	private boolean progress_eta_absolute;

	/**
	 *
	 */
	public ColumnProgressETA(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, COLUMN_WIDTH, sTableID);
		addDataSourceType(DiskManagerFileInfo.class);
		initializeAsGraphic(COLUMN_WIDTH);
		setAlignment(ALIGN_LEAD);
		setMinWidth(100);

		myParameterListener = new MyParameterListener();
		
		COConfigurationManager.addWeakParameterListener(myParameterListener, false,	"mtv.progress_eta.show_absolute");

		display = Utils.getDisplay();

		SWTSkinProperties skinProperties = SWTSkinFactory.getInstance().getSkinProperties();
		cBase = skinProperties.getColor("color.progress.bar");
		if (cBase == null) {
			cBase = Colors.white;
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
		cLinks = skinProperties.getColor("color.links");
		if (cLinks == null) {
			cLinks = Colors.blue;
		}
		// Inactive progress bar
		cDefaults[COLOR_OFF] = skinProperties.getColor("color.progress.bg.inactive");
		if (cDefaults[COLOR_OFF] == null) {
			cDefaults[COLOR_OFF] = Colors.light_grey;
		}
		cDefaults[COLOR_QU] = cDefaults[COLOR_OFF];
		// Progress bar for downloading torrent
		cDefaults[COLOR_DL] = skinProperties.getColor("color.progress.bg.dl");
		if (cDefaults[COLOR_DL] == null) {
			cDefaults[COLOR_DL] = Colors.blues[Colors.BLUES_DARKEST];
		}
		// Progress bar for seeding torrent
		cDefaults[COLOR_CD] = skinProperties.getColor("color.progress.bg.cd");
		if (cDefaults[COLOR_CD] == null) {
			cDefaults[COLOR_CD] = Colors.green;
		}
		
		for ( int i=0; i<cExplicits.length; i++ ){
			
			COConfigurationManager.addWeakParameterListener(myParameterListener, false,	"ColumnProgressETA.color." + i );
		}
		
		cdf = ViewUtils.addCustomDateFormat(this);

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
		
		TableContextMenuItem menuSetColours = addContextMenuItem(
				"ColumnProgressETA.setColours", MENU_STYLE_HEADER);
		menuSetColours.setStyle(TableContextMenuItem.STYLE_MENU);
		menuSetColours.addFillListener(new MenuItemFillListener() {
			@Override
			public void 
			menuWillBeShown(
				MenuItem menu, 
				Object data) 
			{
				menu.removeAllChildItems();
				
				PluginInterface pi = PluginInitializer.getDefaultInterface();
				
				UIManager uim = pi.getUIManager();
				
				MenuManager menuManager = uim.getMenuManager();
				
				for ( int i=0;i<cExplicits.length;i++){

					int f_i = i;
										
					MenuItem item =	menuManager.addMenuItem( menu, "!" + MessageText.getString( "ColumnProgressETA.color." + i ) + "...!" );

					item.addMultiListener(
						new MenuItemListener()
						{
							@Override
							public void 
							selected(
								MenuItem menu, 
								Object target )
							{
								Color c = cExplicits[f_i];

								if ( c == null ){
									
									c = cDefaults[f_i];
								}
								
								RGB result = Utils.showColorDialog(display.getActiveShell(), c==null?null:c.getRGB());
								
								if ( result != null ){
									
									COConfigurationManager.setRGBParameter(
										"ColumnProgressETA.color." + f_i, 
										new int[]{ result.red, result.green, result.blue }, null );
								}
							}
						});
				}
				
				MenuItem itemSep =	menuManager.addMenuItem( menu, "sep" );
				
				itemSep.setStyle(MenuItem.STYLE_SEPARATOR);
				
				MenuItem itemReset =	menuManager.addMenuItem( menu, "Button.reset" );

				itemReset.addMultiListener(
					new MenuItemListener()
					{
						@Override
						public void selected(MenuItem menu, Object target){
							for ( int i=0;i<cExplicits.length;i++){
								
								COConfigurationManager.setRGBParameter(	"ColumnProgressETA.color." + i, null, null );
							}
						}
					});
			}
		});
		
		myParameterListener.parameterChanged( null );
	}

	@Override
	public void remove() {
		super.remove();

		COConfigurationManager.removeWeakParameterListener(myParameterListener,	"mtv.progress_eta.show_absolute");
		
		for ( int i=0; i<cExplicits.length; i++ ){
			
			COConfigurationManager.removeWeakParameterListener(myParameterListener, "ColumnProgressETA.color." + i );
		}
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
		cell.setMarginHeight(1);
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
				Object data = row.getData("lastETA");
				if (data instanceof Number) {
					lastETA = ((Number) data).longValue();
				}
				row.setData("lastETA", new Long(eta));
			}
			if (showSpeed) {
				Object data = row.getData("lastSpeed");
				if (data instanceof Number) {
					lastSpeed = ((Number) data).longValue();
				}
				row.setData("lastSpeed", new Long(speed));
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

		String tooltip = null;
		
		DownloadManager dm = (DownloadManager)ds;
		int dm_state = dm.getState();
		if ( dm_state == DownloadManager.STATE_QUEUED ){
			tooltip = MessageText.getString( "ManagerItem.queued.tooltip" );
		}
		
		int percentDone = getPercentDone(ds);
		long eta = showETA ? getETA(cell) : 0;

		Color fgFirst = gc.getForeground();

		// Size constraints
		final int minCellWidth = 14;
		final int minProgressHeight = 18;
		final int secondLineHeight = 16;
		final int minTwoLineHeight = minProgressHeight + secondLineHeight;
		final int maxTextHeightPX = 20;

		final Rectangle cellBounds = cell.getBounds();

		cellBounds.width--; // adjust as includes pixel for column line :(
		
		if (cellBounds.width < minCellWidth) {
			return;
		}

		int lineHeight = cell.getTableRow().getView().getLineHeight();
		final boolean showSecondLine;
		int alignSecondLine;
		Color fgSecondLine;
		Rectangle boundsSecondLine;

		Rectangle boundsProgressBar = cell.getBounds();

		boundsProgressBar.width--;	// adjust as includes pixel for column line :(
		
		if (cellBounds.height >= Math.min( minTwoLineHeight, lineHeight*2 )) {
			showSecondLine = true;
			boundsProgressBar.height -= secondLineHeight;
			boundsSecondLine = new Rectangle(
					boundsProgressBar.x,
					boundsProgressBar.y + boundsProgressBar.height + 1,
					boundsProgressBar.width, secondLineHeight);

			alignSecondLine = SWT.CENTER;
			fgSecondLine = fgFirst;

		} else {
			showSecondLine = false;
			boundsSecondLine = boundsProgressBar;
			alignSecondLine = SWT.RIGHT;
			fgSecondLine = cText;
		}

		// Draw Progress bar
		int fillWidth = (int) (percentDone * cellBounds.width / 1000);
		Rectangle pctFillRect = new Rectangle(
				cellBounds.x, cellBounds.y, fillWidth, cellBounds.height);
		pctFillRect.intersect(boundsProgressBar);

		Rectangle pctFillRectNot = new Rectangle(pctFillRect.x+pctFillRect.width,pctFillRect.y,boundsProgressBar.width-pctFillRect.width,pctFillRect.height);
		
		gc.setBackground(cBase);
		gc.fillRectangle(boundsProgressBar);

		int cBGIndex;
		
		if ( dm_state == DownloadManager.STATE_DOWNLOADING || dm_state == DownloadManager.STATE_SEEDING ){
			
			if (percentDone == 1000 || dm.isDownloadComplete(false)){
				
				cBGIndex = COLOR_CD;
				
			}else{
				
				cBGIndex = COLOR_DL;
			}
		}else{
			
			if ( dm_state == DownloadManager.STATE_QUEUED ){
				
				cBGIndex = COLOR_QU;
				
			}else{
			
				cBGIndex = COLOR_OFF;
			}
		}

		Color cBG = cExplicits[cBGIndex];
		
		boolean bgIsCustom = true;
		
		if ( cBG == null ){
			
			cBG = cDefaults[cBGIndex];
			
			bgIsCustom = false;
		}

		gc.setBackground(cBG);
		gc.fillRectangle(pctFillRect);

		// Draw "3D" gradients at top/bottom edges of filled progress bar area
		if (show3D) {
			final int edgeHeight = boundsProgressBar.height > 40 ? 5 : 3;
			final Color highlight = Colors.getInstance().getLighterColor(cBG, 50);
			final Color lowlight = Colors.getInstance().getLighterColor(cBG, -40);
			gc.setForeground(highlight);
			gc.fillGradientRectangle(
				pctFillRect.x, pctFillRect.y,
				pctFillRect.width, edgeHeight, true);
			gc.setForeground(cBG);
			gc.setBackground(lowlight);
			gc.fillGradientRectangle(
				pctFillRect.x,
				pctFillRect.y + pctFillRect.height - edgeHeight,
				pctFillRect.width, edgeHeight, true);
		}
		gc.setBackground(cBase);

		// Outline the bar
		final int originalWidth = gc.getLineWidth();
		gc.setForeground(cBorder);
		gc.setLineWidth(borderWidth);
		gc.drawRectangle(boundsProgressBar.x,boundsProgressBar.y,boundsProgressBar.width-1,boundsProgressBar.height-1);
		gc.setLineWidth(originalWidth);

		String sStatusLine = null;

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

		boolean haveLink = false;

		if (sStatusLine != null &&
				sStatusLine.contains("http://") || sStatusLine.contains("https://"))
		{
			dm.setUserData(CLICK_KEY, sStatusLine);
			haveLink = true;
			if(showSecondLine) {
				fgSecondLine = cLinks;
			}
		} else {
			dm.setUserData(CLICK_KEY, null);
		}

		((TableCellSWT) cell).setCursorID(
			haveLink ? SWT.CURSOR_HAND : SWT.CURSOR_ARROW);

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

		final int xOffset = 3;
		int yOffset = boundsProgressBar.height > 30 ? 2 : 1;
		int textWidthPX = boundsProgressBar.width - (2 * xOffset);
		int newTextHeightPX = Math.min(
				showSecondLine ? maxTextHeightPX : 16,
				boundsProgressBar.height - (2 * yOffset));
		yOffset = (boundsProgressBar.height - newTextHeightPX+1) / 2;

		if (fontText == null || newTextHeightPX != textHeightPX) {
			if (fontText != null) {
				fontText.dispose();
			}
			textHeightPX = newTextHeightPX;
			fontText = FontUtils.getFontWithHeight(
				gc.getFont(), textHeightPX, SWT.DEFAULT);
		}

		final boolean wantShadow = cTextDrop != null && show3D;
		Rectangle area = new Rectangle(
			boundsProgressBar.x + xOffset + (wantShadow ? 1 : 0),
			boundsProgressBar.y + yOffset + (wantShadow ? 1 : 0),
			textWidthPX,
			textHeightPX);

		gc.setTextAntialias(SWT.ON);
		gc.setFont(fontText);

		GCStringPrinter sp = new GCStringPrinter(
			gc, sPercent + sSpeed, area, true, false, SWT.LEFT);
		if (wantShadow) {
			gc.setForeground(cTextDrop);
			sp.printString();
			area.x--;
			area.y--;
		}
		
		Color cFirstLine = cText;
		
		if ( bgIsCustom && !Colors.isColorContrastOk(cText,cBG)){
			cFirstLine = Colors.isColorContrastOk(Colors.white,cBG)?Colors.white:Colors.black;
		}
		
		gc.setForeground(cFirstLine);
		
		if ( cFirstLine == Colors.white ){
			Rectangle old = gc.getClipping();
			
			Utils.setClipping( gc, pctFillRect );
			
			sp.printString();
			Utils.setClipping( gc, pctFillRectNot );
			gc.setForeground(Colors.black);
			sp.printString();
		
			Utils.setClipping( gc, old );
		}else{
			sp.printString();
		}
		
		if (sStatusLine != null) {
			if (showSecondLine) {
				int newHeight = boundsSecondLine.height;
				if (fontSecondLine == null || newHeight != secondLineHeightPX) {
					if (fontSecondLine != null) {
						fontSecondLine.dispose();
					}
					secondLineHeightPX = newHeight;
					fontSecondLine = FontUtils.getFontWithHeight(
						gc.getFont(), secondLineHeightPX, SWT.DEFAULT);
				}
			} else {
				Point pctExtent = sp.getCalculatedSize();
				boundsSecondLine = area;
				boundsSecondLine.x += (pctExtent.x + xOffset);
				boundsSecondLine.width -= (pctExtent.x + xOffset);
				if (wantShadow) {
					boundsSecondLine.x++;
					boundsSecondLine.y++;
				}
				if (fontSecondLine != null) {
					fontSecondLine.dispose();
					fontSecondLine = null;
				}
				// Force a call to getFont...() when the second line reappears
				secondLineHeightPX = -1;

			}

			gc.setFont(showSecondLine ? fontSecondLine : fontText);
			
			GCStringPrinter sp2 = new GCStringPrinter( gc, sStatusLine, boundsSecondLine, true, false, alignSecondLine);

			if (!showSecondLine && wantShadow) {
				gc.setForeground(cTextDrop);
				sp2.printString();
				boundsSecondLine.x--;
				boundsSecondLine.y--;
			}
				
			Color cSecondLine = showSecondLine?fgSecondLine:cFirstLine;
			
			gc.setForeground(cSecondLine);
			
			boolean fit;
			
			if ( cSecondLine == Colors.white ){
				
				Rectangle old = gc.getClipping();
				
				Utils.setClipping( gc, pctFillRect );
			
				sp2.printString();
				
				Utils.setClipping( gc, pctFillRectNot );
				
				gc.setForeground(Colors.black);
				
				fit = sp2.printString();
			
				Utils.setClipping( gc, old );
			}else{
				
				fit = sp2.printString();
			}
			
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
			show3D = false; // we could read a global default from somewhere
		} else if (oShow3D instanceof Number) {
			show3D = ((Number) oShow3D).intValue() == 1;
		}
		
		cdf.update();
	}

	private class MyParameterListener
		implements ParameterListener
	{
		@Override
		public void 
		parameterChanged(
			String unused) 
		{
			progress_eta_absolute = COConfigurationManager.getBooleanParameter(	"mtv.progress_eta.show_absolute", false);
			
			for ( int i=0; i<cExplicits.length; i++ ){
				
				int[] rgb = COConfigurationManager.getRGBParameter(	"ColumnProgressETA.color." + i );
				
				if ( rgb != null && rgb.length == 3 ){
					
					cExplicits[i] = ColorCache.getColor(display, rgb );
					
				}else{
					
					cExplicits[i] = null;
				}
			}
			
			ColumnProgressETA.this.invalidateCells();
		}
	}
}
