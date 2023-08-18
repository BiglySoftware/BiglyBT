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

package com.biglybt.ui.swt;

import static com.biglybt.pif.ui.menus.MenuItem.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import com.biglybt.ui.swt.mainwindow.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.speedmanager.SpeedLimitHandler;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.GraphicURI;
import com.biglybt.pif.ui.menus.MenuBuilder;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.ui.menus.MenuItemImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableStructureEventDispatcher;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.exporttorrent.wizard.ExportTorrentWizard;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.minibar.DownloadBar;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.sharing.ShareUtils;
import com.biglybt.ui.swt.views.FilesViewMenuUtil;
import com.biglybt.ui.swt.views.columnsetup.TableColumnSetupWindow;
import com.biglybt.ui.swt.views.table.TableSelectedRowsListener;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.common.table.impl.TableContextMenuManager;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.core.torrent.PlatformTorrentUtils;

/**
 * @author TuxPaper
 * @created Jan 20, 2015
 *
 */
public class TorrentMenuFancy
{
	private static final String HEADER_MSG_PREFIX = "FancyMenu.Header.";

	private static class HeaderInfo
	{
		private Runnable runnable;

		private Composite composite;

		private String id;

		public HeaderInfo(String id, Runnable runnable, Composite composite) {
			this.id = id;
			this.runnable = runnable;
			this.composite = composite;
		}
	}

	private static class FancyRowInfo
	{
		private Listener listener;

		private Label lblText;

		private Label lblRight;

		private Label lblIcon;

		private Label lblCheck;

		private Composite cRow;

		private boolean keepMenu;

		private boolean isSelected;

		private boolean hasSubMenu;

		public void setEnabled(boolean enabled) {
			cRow.setEnabled(enabled);
		}

		public Label getRightLabel() {
			if (lblRight == null) {
				lblRight = new Label(cRow, SWT.NONE);
				GridData gd = new GridData();
				gd.horizontalIndent = 10;
				lblRight.setLayoutData(gd);
				lblRight.setEnabled(false);
			}
			return lblRight;
		}

		public Listener getListener() {
			return listener;
		}

		public void setListener(Listener listener) {
			this.listener = listener;
		}

		public Label getText() {
			return lblText;
		}

		public void setText(Label lblText) {
			this.lblText = lblText;
		}

		public void setRightLabel(Label lblRight) {
			this.lblRight = lblRight;
		}

		public void setRightLabelText(String s) {
			getRightLabel().setText(s);
		}

		public Label getIconLabel() {
			return lblIcon;
		}

		public void setIconLabel(Label lblIcon) {
			this.lblIcon = lblIcon;
		}

		public Composite getRow() {
			return cRow;
		}

		public void setRow(Composite cRow) {
			this.cRow = cRow;
		}

		public boolean keepMenu() {
			return keepMenu;
		}

		public void setKeepMenu(boolean keepMenu) {
			this.keepMenu = keepMenu;
		}

		public void setSelection(boolean isSelected) {
			this.isSelected = isSelected;
			ImageLoader.getInstance().setLabelImage(lblCheck,
					isSelected ? "check_yes" : "check_no");
		}

		public boolean isSelected() {
			return isSelected;
		}

		public void setCheckLabel(Label lblCheck) {
			this.lblCheck = lblCheck;
		}

		public boolean hasSubMenu() {
			return hasSubMenu;
		}

		public void setHasSubMenu(boolean hasSubMenu) {
			this.hasSubMenu = hasSubMenu;
		}

		public void redraw() {
			if (cRow == null || cRow.isDisposed()) {
				return;
			}
			Rectangle bounds = cRow.getBounds();
			cRow.redraw(0, 0, bounds.width, bounds.height, true);
		}
	}

	private static class FancyMenuRowInfo
		extends FancyRowInfo
	{
		private Menu menu;

		public Menu getMenu() {
			return menu;
		}

		public void setMenu(Menu menu) {
			this.menu = menu;
		}
	}

	private interface FancyMenuRowInfoListener
	{
		public void buildMenu(Menu menu);
	}

	protected static final boolean DEBUG_MENU = false;

	private static final int SHELL_MARGIN = 1;

	private List<FancyRowInfo> listRowInfos = new ArrayList<>();

	private List<HeaderInfo> listHeaders = new ArrayList<>();

	private Composite topArea;

	private Composite detailArea;

	private Listener headerListener;

	private TableViewSWT<DownloadManager> tv;

	private boolean isSeedingView;

	private Shell parentShell;

	private DownloadManager[] dms;

	private String tableID;

	private boolean hasSelection;

	private Shell shell;

	private Listener listenerForTrigger;

	private Listener listenerRow;

	private PaintListener listenerRowPaint;

	private TableColumnCore column;

	private HeaderInfo activatedHeader;

	private Menu currentMenu;

	private FancyRowInfo currentRowInfo;

	private Point originalShellLocation;

	private boolean subMenuVisible;

	private PaintListener paintListenerArrow;

	public TorrentMenuFancy(final TableViewSWT<DownloadManager> tv,
			final boolean isSeedingView, Shell parentShell,
			final DownloadManager[] dms, final String tableID) {
		this.tv = tv;
		this.isSeedingView = isSeedingView;
		this.parentShell = parentShell;
		this.dms = dms;
		this.tableID = tableID;
		hasSelection = dms.length > 0;

		listenerForTrigger = new Listener() {
			@Override
			public void handleEvent(Event event) {
				FancyRowInfo rowInfo = findRowInfo(event.widget);
				if (rowInfo != null) {
				
					Rectangle row_bounds =  rowInfo.getRow().getBounds();
					
					if (!rowInfo.keepMenu()) {
						shell.dispose();
					}

					if ( rowInfo.getListener() != null) {
						
							// make sure we are still in the area of the row 
						
						row_bounds.x = row_bounds.y = 0;
						
						if ( row_bounds.contains( event.x, event.y )){

							rowInfo.getListener().handleEvent(event);
							
						}
					}
				} else {
					shell.dispose();
				}
			}
		};

		paintListenerArrow = new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				Control c = (Control) e.widget;
				Point size = c.getSize();
				int arrowSize = 8;
				int xStart = size.x - arrowSize;
				int yStart = size.y - (size.y + arrowSize) / 2;
				e.gc.setBackground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_FOREGROUND));
				e.gc.setAntialias(SWT.ON);
				e.gc.fillPolygon(new int[] {
					xStart,
					yStart,
					xStart + arrowSize,
					yStart + (arrowSize / 2),
					xStart,
					yStart + arrowSize,
				});
			}
		};

		listenerRow = event -> {
			FancyRowInfo lastRowInfo = currentRowInfo;
			if (event.type == SWT.MouseExit) {
				currentRowInfo = null;
			} else if (event.type == SWT.MouseEnter) {
				currentRowInfo = findRowInfo(event.widget);
			}
			if (lastRowInfo != null) {
				lastRowInfo.redraw();
			}
			if (currentRowInfo != null && currentRowInfo != lastRowInfo) {
				currentRowInfo.redraw();
			}
		};

		listenerRowPaint = new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				FancyRowInfo rowInfo = findRowInfo(e.widget);
				if (rowInfo == null) {
					return;
				}

				boolean isSelected = currentRowInfo == rowInfo;

				if (!isSelected) {
					for (Control control : ((Composite) e.widget).getChildren()) {
						control.setBackground(null);
						if ( !Utils.hasSkinnedForeground(control)){
							control.setForeground(null);
						}
					}
					//System.out.println("bounds=" + bounds + "/" + cursorLocation + "/" + cursorLocationRel + "; clip=" + e.gc.getClipping());
					return;
				}
				Rectangle bounds = ((Control) e.widget).getBounds();

				Color bg = Colors.getSystemColor(e.display, Utils.isDarkAppearanceNativeWindows()?SWT.COLOR_WIDGET_LIGHT_SHADOW:SWT.COLOR_LIST_BACKGROUND);
				int arc = bounds.height / 3;
				e.gc.setBackground(bg);
				e.gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_DARK_SHADOW));
				e.gc.setAntialias(SWT.ON);
				//System.out.println("clip=" + e.gc.getClipping());
				e.gc.fillRoundRectangle(0, 0, bounds.width - 1, bounds.height - 1, arc,
						arc);
				e.gc.setAlpha(100);
				e.gc.drawRoundRectangle(0, 0, bounds.width - 1, bounds.height - 1, arc,
						arc);

				Color fg = Colors.getSystemColor(e.display, SWT.COLOR_LIST_FOREGROUND);
				for (Control control : ((Composite) e.widget).getChildren()) {
					control.setBackground(bg);
					if ( !Utils.hasSkinnedForeground(control)){
						control.setForeground(fg);
					}
				}
			}
		};

	}

	public void showMenu(Point locationOnDiplay, TableColumnCore acolumn, final Menu fallbackMenu ) {
		this.column = acolumn;
		Display d = parentShell.getDisplay();

		// We don't get mouse down notifications on trim or borders..
		shell = new Shell(parentShell, SWT.NO_TRIM | SWT.DOUBLE_BUFFERED) {
			@Override
			protected void checkSubclass() {
			}

			@Override
			public void dispose() {
				if (DEBUG_MENU) {
					System.out.println("Dispose via " + Debug.getCompressedStackTrace());
				}
				super.dispose();
			}
		};

		//FormLayout shellLayout = new FormLayout();
		RowLayout shellLayout = new RowLayout(SWT.VERTICAL);
		shellLayout.fill = true;
		shellLayout.marginBottom = shellLayout.marginLeft = shellLayout.marginRight = shellLayout.marginTop = 0;
		shellLayout.marginWidth = shellLayout.marginHeight = SHELL_MARGIN;

		shell.setLayout(shellLayout);

		topArea = new Composite(shell, SWT.DOUBLE_BUFFERED);
		detailArea = new Composite(shell, SWT.DOUBLE_BUFFERED);

		topArea.setBackground(Colors.getSystemColor(d, SWT.COLOR_LIST_BACKGROUND));
		topArea.setForeground(Colors.getSystemColor(d, SWT.COLOR_LIST_FOREGROUND));

		FormData fd = Utils.getFilledFormData();
		fd.bottom = null;
		RowLayout topLayout = new RowLayout(SWT.HORIZONTAL);
		topLayout.spacing = 0;
		topLayout.pack = true;
		topLayout.marginBottom = topLayout.marginTop = topLayout.marginLeft = topLayout.marginRight = 0;
		topArea.setLayout(topLayout);

		//detailArea.setBackground(ColorCache.getRandomColor());
		fd = Utils.getFilledFormData();
		fd.top = new FormAttachment(topArea, 0, SWT.BOTTOM);
		FormLayout layoutDetailsArea = new FormLayout();
		layoutDetailsArea.marginWidth = 2;
		layoutDetailsArea.marginBottom = 2;
		detailArea.setLayout(layoutDetailsArea);

		headerListener = new Listener() {
			// @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
			@Override
			public void handleEvent(Event e) {
				Control control = (Control) e.widget;
				if (e.type == SWT.Paint) {
					Rectangle bounds = control.getBounds();
					int y = bounds.height - 2;
					e.gc.drawLine(0, y, bounds.width, y);
				} else if (e.type == SWT.MouseEnter || e.type == SWT.Touch) {
					Object data = e.widget.getData("ID");

					if (data instanceof HeaderInfo) {
						HeaderInfo header = (HeaderInfo) data;
						if (DEBUG_MENU) {
							System.out.println("enter : " + data);
						}

						activateHeader(header);
					}
				}
			}
		};

		HeaderInfo firstHeader = addHeader(HEADER_CONTROL, HEADER_MSG_PREFIX
				+ HEADER_CONTROL, new AERunnable() {
			@Override
			public void runSupport() {
				buildTorrentCustomMenu_Control(detailArea, dms);
			}
		});
		addHeader(HEADER_CONTENT, HEADER_MSG_PREFIX + HEADER_CONTENT,
				new AERunnable() {
					@Override
					public void runSupport() {
						buildTorrentCustomMenu_Content(detailArea, dms);
					}
				});
		addHeader(HEADER_ORGANIZE, HEADER_MSG_PREFIX + HEADER_ORGANIZE,
				new AERunnable() {
					@Override
					public void runSupport() {
						buildTorrentCustomMenu_Organize(detailArea, dms);
					}
				});
		addHeader(HEADER_SOCIAL, HEADER_MSG_PREFIX + HEADER_SOCIAL,
				new AERunnable() {
					@Override
					public void runSupport() {
						buildTorrentCustomMenu_Social(detailArea);
					}
				});

		// Add table specific items
		final List<com.biglybt.pif.ui.menus.MenuItem> listOtherItems = getPluginItems(HEADER_OTHER);

		if (listOtherItems.size() > 0) {
			addHeader(HEADER_OTHER, HEADER_MSG_PREFIX + HEADER_OTHER, null);
		}

		originalShellLocation = locationOnDiplay;

		shell.setLocation(originalShellLocation);

		shell.addPaintListener(new PaintListener() {

			@Override
			public void paintControl(PaintEvent e) {
				e.gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_BORDER));
				Rectangle clientArea = shell.getClientArea();
				e.gc.drawRectangle(0, 0, clientArea.width - 1, clientArea.height - 1);
			}
		});

		shell.addListener(SWT.KeyDown, e -> {
			FancyRowInfo lastRowInfo = currentRowInfo;
			switch (e.keyCode) {
				case SWT.ARROW_DOWN:
					if (currentRowInfo == null) {
						currentRowInfo = listRowInfos.get(0);
					} else {
						boolean next = false;
						for (FancyRowInfo rowInfo : listRowInfos) {
							if (next) {
								currentRowInfo = rowInfo;
								next = false;
								break;
							}
							if (rowInfo == currentRowInfo) {
								next = true;
							}
						}
						if (next) {
							currentRowInfo = listRowInfos.get(0);
						}
					}
					break;
				case SWT.ARROW_UP:
					if (currentRowInfo == null) {
						currentRowInfo = listRowInfos.get(listRowInfos.size() - 1);
					} else {
						FancyRowInfo previous = listRowInfos.get(listRowInfos.size() - 1);
						for (FancyRowInfo rowInfo : listRowInfos) {
							if (rowInfo == currentRowInfo) {
								currentRowInfo = previous;
								break;
							}
							previous = rowInfo;
						}
					}
					break;
				case SWT.ARROW_LEFT:
					HeaderInfo previous = listHeaders.get(listHeaders.size() - 1);
					for (HeaderInfo header : listHeaders) {
						if (header == activatedHeader) {
							activateHeader(previous);
							break;
						}
						previous = header;
					}
					break;
				case SWT.ARROW_RIGHT:
					if (currentRowInfo != null && currentRowInfo.hasSubMenu()) {
						Event event = new Event();
						event.display = e.display;
						event.widget = currentRowInfo.cRow;
						listenerForTrigger.handleEvent(event);
					} else {
						boolean next = false;
						for (HeaderInfo header : listHeaders) {
							if (next) {
								activateHeader(header);
								next = false;
								break;
							}
							if (header == activatedHeader) {
								next = true;
							}
						}
						if (next) {
							activateHeader(listHeaders.get(0));
						}
					}
					break;
			}

			if (lastRowInfo != null) {
				lastRowInfo.redraw();
			}
			if (currentRowInfo != null && lastRowInfo != currentRowInfo) {
				currentRowInfo.redraw();
			}
		});

		if ( fallbackMenu != null ){

			firstHeader.composite.addMenuDetectListener(
					new MenuDetectListener() {

						@Override
						public void menuDetected(MenuDetectEvent e) {
							shell.dispose();
							fallbackMenu.setVisible( true );
						}
					});
		}

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					if (DEBUG_MENU) {
						System.out.println("Dispose via ESCAPE");
					}
					shell.dispose();
				} else if (e.detail == SWT.TRAVERSE_RETURN) {
					if (currentRowInfo != null) {
						Event event = new Event();
						event.display = e.display;
						event.widget = currentRowInfo.cRow;
						listenerForTrigger.handleEvent(event);
					}
				}
			}
		});

		shell.addShellListener(new ShellListener() {
			@Override
			public void shellIconified(ShellEvent e) {
			}

			@Override
			public void shellDeiconified(ShellEvent e) {
			}

			@Override
			public void shellDeactivated(ShellEvent e) {
				// Must do later, so clicks go to wherever
				Utils.execSWTThreadLater(0, new AERunnable() {
					@Override
					public void runSupport() {
						if (subMenuVisible) {
							return;
						}
						if (shell.isDisposed()) {
							return;
						}
						Shell[] shells = shell.getShells();
						if (shells != null && shells.length > 0) {
							for (Shell aShell : shells) {
								if (!aShell.isDisposed()) {
									return;
								}
							}
						}
						shell.dispose();
					}
				});
			}

			@Override
			public void shellClosed(ShellEvent e) {
			}

			@Override
			public void shellActivated(ShellEvent e) {
			}
		});

		activateHeader(firstHeader);

		shell.open();
	}

	private List<com.biglybt.pif.ui.menus.MenuItem> getPluginItems(String headerID) {
		List<com.biglybt.pif.ui.menus.MenuItem> listPluginItems = new ArrayList<>();

		com.biglybt.pif.ui.menus.MenuItem[] items = TableContextMenuManager.getInstance().getAllAsArray(
				Utils.getBaseViewID(tableID));
		listPluginItems.addAll(Arrays.asList(items));
		items = MenuItemManager.getInstance().getAllAsArray(
				MenuManager.MENU_DOWNLOAD_CONTEXT);
		listPluginItems.addAll(Arrays.asList(items));
		if (column != null) {
			items = column.getContextMenuItems(TableColumnCore.MENU_STYLE_COLUMN_DATA);
			listPluginItems.addAll(Arrays.asList(items));
		}

		boolean isOther = headerID.equals(HEADER_OTHER);
		List<com.biglybt.pif.ui.menus.MenuItem> result = new ArrayList<>();
		int userMode = COConfigurationManager.getIntParameter("User Mode");
		for (com.biglybt.pif.ui.menus.MenuItem item : listPluginItems) {
			String headerCategory = item.getHeaderCategory();
			if (headerID.equals(headerCategory) || (isOther && headerCategory == null)) {
				int minUserMode = item.getMinUserMode();
				if (userMode >= minUserMode) {
					result.add(item);
				}
			}
		}

		return result;
	}

	protected void activateHeader(HeaderInfo header) {
		if (header == null || activatedHeader == header) {
			return;
		}

		if (currentMenu != null && !currentMenu.isDisposed()) {
			currentMenu.setVisible(false);
		}
		Display d = header.composite.getDisplay();
		header.composite.setBackground(Colors.getSystemColor(d, SWT.COLOR_WIDGET_BACKGROUND));
		header.composite.setForeground(Colors.getSystemColor(d, SWT.COLOR_WIDGET_FOREGROUND));

		Utils.disposeSWTObjects(detailArea.getChildren());
		listRowInfos.clear();
		currentRowInfo = null;

		if (header.runnable != null) {
			header.runnable.run();
		}

		List<com.biglybt.pif.ui.menus.MenuItem> pluginItems = getPluginItems(header.id);
		for (com.biglybt.pif.ui.menus.MenuItem item : pluginItems) {
			addPluginItem(detailArea, item);
		}

		Control lastControl = null;
		for (Control child : detailArea.getChildren()) {
			FormData fd = new FormData();
			if (lastControl == null) {
				fd.top = new FormAttachment(0);
			} else {
				fd.top = new FormAttachment(lastControl);
			}
			fd.left = new FormAttachment(0, 0);
			fd.right = new FormAttachment(100, 0);
			child.setLayoutData(fd);
			lastControl = child;
		}

		shell.setLocation(shell.getLocation().x, originalShellLocation.y);
		detailArea.moveBelow(null);
		shell.pack(true);
		detailArea.layout(true, true);

		Point shellSize = shell.getSize();
		Point ptBottomRight = shell.toDisplay(shellSize);
		Rectangle monitorArea = shell.getMonitor().getClientArea();
		if (ptBottomRight.x > monitorArea.x + monitorArea.width) {
			shell.setLocation(monitorArea.x + monitorArea.width - shellSize.x,
					shell.getLocation().y);
		}

		if (ptBottomRight.y > monitorArea.y + monitorArea.height) {
			// Bottom-Up
			if (shell.getChildren()[0] != detailArea) {
				shell.setLocation(shell.getLocation().x, originalShellLocation.y
						- detailArea.getSize().y - 3);
				detailArea.moveAbove(null);
				lastControl = null;
				Control[] children = detailArea.getChildren();
				for (int i = 0; i < children.length; i++) {
					Control child = children[children.length - i - 1];
					FormData fd = new FormData();
					if (lastControl == null) {
						fd.top = new FormAttachment(0);
					} else {
						fd.top = new FormAttachment(lastControl);
					}
					fd.left = new FormAttachment(0, 0);
					fd.right = new FormAttachment(100, 0);
					child.setLayoutData(fd);
					lastControl = child;
				}
				shell.layout(true, true);
			}
		}

		if (activatedHeader != null) {
			activatedHeader.composite.setBackground(Colors.getSystemColor(d, SWT.COLOR_LIST_BACKGROUND));
			activatedHeader.composite.setForeground(Colors.getSystemColor(d, SWT.COLOR_LIST_FOREGROUND));
		}

		activatedHeader = header;
	}

	public void buildTorrentCustomMenu_Control(final Composite cParent,
			final DownloadManager[] dms) {
		final int userMode = COConfigurationManager.getIntParameter("User Mode");

		boolean start = false;
		boolean stop = false;
		boolean pause = false;
		boolean recheck = false;
		boolean barsOpened = true;
		boolean bChangeDir = hasSelection;

		for (int i = 0; i < dms.length; i++) {
			DownloadManager dm = dms[i];

			if (barsOpened && !DownloadBar.getManager().isOpen(dm)) {
				barsOpened = false;
			}
			stop = stop || ManagerUtils.isStopable(dm);

			start = start || ManagerUtils.isStartable(dm,true);

			pause = pause || ManagerUtils.isPauseable( dm );
			
			recheck = recheck || dm.canForceRecheck();

			int state = dm.getState();
			bChangeDir &= (state == DownloadManager.STATE_ERROR
					|| state == DownloadManager.STATE_STOPPED || state == DownloadManager.STATE_QUEUED);
			/**
			 * Only perform a test on disk if:
			 *    1) We are currently set to allow the "Change Data Directory" option, and
			 *    2) We've only got one item selected - otherwise, we may potentially end up checking massive
			 *       amounts of files across multiple torrents before we generate a menu.
			 */
			if (bChangeDir && dms.length == 1) {
				bChangeDir = dm.isDataAlreadyAllocated();
				if (bChangeDir && state == DownloadManager.STATE_ERROR) {
					// filesExist is way too slow!
					bChangeDir = !dm.filesExist(true);
				} else {
					DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
					bChangeDir = false;
					for (DiskManagerFileInfo info : files) {
						if (info.isSkipped()) {
							continue;
						}
						bChangeDir = !info.getFile(true).exists();
						break;
					}
				}
			}
		}
		Composite cQuickCommands = new Composite(cParent, SWT.NONE);
		//cQuickCommands.setBackground(ColorCache.getRandomColor());
		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		rowLayout.justify = true;
		rowLayout.marginLeft = 0;
		rowLayout.marginRight = 0;
		cQuickCommands.setLayout(rowLayout);
		GridData gd = new GridData();
		gd.grabExcessHorizontalSpace = true;
		gd.horizontalAlignment = SWT.FILL;
		cQuickCommands.setLayoutData(gd);

		// Queue
		createActionButton(dms, cQuickCommands, "MyTorrentsView.menu.queue",
				"start", start,
				(ListenerGetOffSWT) event -> TorrentUtil.queueDataSources(dms, false));
		
		// Force Start
		
		if ( TorrentUtil.isForceStartVisible( dms )) {
			
			boolean forceStart = false;
			boolean forceStartEnabled = false;

			for (int i = 0; i < dms.length; i++) {
				DownloadManager dm = dms[i];

				forceStartEnabled = forceStartEnabled
						|| ManagerUtils.isForceStartable(dm, true );

				forceStart = forceStart || dm.isForceStart();
			}

			final boolean newForceStart = !forceStart;

			createActionButton(dms, cQuickCommands, "MyTorrentsView.menu.forceStart",
					"forcestart", forceStartEnabled, !newForceStart,
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager dm) {
							if (ManagerUtils.isForceStartable(dm, true )) {
								dm.setForceStart(newForceStart);
							}
						}
					});
		}

		// Pause
		if (userMode > 0) {
			createActionButton(dms, cQuickCommands, "v3.MainWindow.button.pause",
					"pause", pause,
					(ListenerGetOffSWT) event -> TorrentUtil.pauseDataSources(dms));
		}

		// Stop
		createActionButton(dms, cQuickCommands, "MyTorrentsView.menu.stop", "stop",
				stop, (ListenerGetOffSWT) event -> TorrentUtil.stopDataSources(dms));

		// Force Recheck
		createActionButton(dms, cQuickCommands, "MyTorrentsView.menu.recheck",
				"recheck", recheck, new ListenerDMTask(dms) {
					@Override
					public void run(DownloadManager dm) {
						if (dm.canForceRecheck()) {
							dm.forceRecheck();
						}
					}
				});

		// Delete
		createActionButton(dms, cQuickCommands, "menu.delete.options", "delete",
				hasSelection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						TorrentUtil.removeDownloads(dms, null, true);
					}
				});

		///////////////////////////////////////////////////////////////////////////

		if (bChangeDir) {
			createRow(cParent, "MyTorrentsView.menu.changeDirectory", null,
					new Listener() {
						@Override
						public void handleEvent(Event e) {
							TorrentUtil.changeDirSelectedTorrents(dms, parentShell);
						}
					});
		}

		// Open Details
		if (hasSelection) {
			createRow(cParent, "MyTorrentsView.menu.showdetails", "details",
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager dm) {
							UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
							if (uiFunctions != null) {
								uiFunctions.getMDI().showEntryByID(
										MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, dm);
							}
						}
					});
		}

		// Open Bar
		if (hasSelection) {
			FancyRowInfo row = createRow(cParent,
					"MyTorrentsView.menu.showdownloadbar", "downloadBar",
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager dm) {
							if (DownloadBar.getManager().isOpen(dm)) {
								DownloadBar.close(dm);
							} else {
								DownloadBar.open(dm, parentShell);
							}
						} // run
					});
			row.setSelection(barsOpened);
		}

		//////////////////////////////////////

		if (hasSelection) {
			FancyRowInfo rowSpeedDL = createRow(cParent,
					"MyTorrentsView.menu.downSpeedLimit", "image.torrentspeed.down",
					false, new Listener() {
						@Override
						public void handleEvent(Event e) {
							Event event = new Event();
							event.type = SWT.MouseUp;
							event.widget = e.widget;
							event.stateMask = e.stateMask;
							event.button = e.button;
							e.display.post(event);

							Core core = CoreFactory.getSingleton();
							SelectableSpeedMenu.invokeSlider((Control) event.widget, core,
									dms, false, shell);
							if (e.display.getActiveShell() != shell) {
								if (!shell.isDisposed()) {
									shell.dispose();
								}
								return;
							}
							FancyRowInfo rowInfo = findRowInfo(event.widget);
							if (rowInfo != null) {
								updateRowSpeed(rowInfo, false);
							}

						}
					});
			rowSpeedDL.keepMenu = true;

			updateRowSpeed(rowSpeedDL, false);
		}

		if (hasSelection) {
			FancyRowInfo rowSpeedUL = createRow(cParent,
					"MyTorrentsView.menu.upSpeedLimit", "image.torrentspeed.up", false,
					new Listener() {
						@Override
						public void handleEvent(Event e) {
							Event event = new Event();
							event.type = SWT.MouseUp;
							event.widget = e.widget;
							event.stateMask = e.stateMask;
							event.button = e.button;
							e.display.post(event);

							Core core = CoreFactory.getSingleton();
							SelectableSpeedMenu.invokeSlider((Control) e.widget, core, dms,
									true, shell);
							if (e.display.getActiveShell() != shell) {
								if (!shell.isDisposed()) {
									shell.dispose();
								}
								return;
							}
							FancyRowInfo rowInfo = findRowInfo(event.widget);
							if (rowInfo != null) {
								updateRowSpeed(rowInfo, true);
							}
						}
					});
			rowSpeedUL.keepMenu = true;

			updateRowSpeed(rowSpeedUL, true);
		}

		//////////////////////////////////////

		if (hasSelection && userMode > 0) {
			createMenuRow(cParent, "MyTorrentsView.menu.tracker", null,
					new FancyMenuRowInfoListener() {
						@Override
						public void buildMenu(Menu menu) {
							boolean changeUrl = hasSelection;
							boolean manualUpdate = true;
							boolean allStopped = true;
							boolean canMove = true;
									
							int userMode = COConfigurationManager.getIntParameter("User Mode");
							final boolean use_open_containing_folder = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");

							for (DownloadManager dm : dms) {
								boolean stopped = ManagerUtils.isStopped(dm);

								allStopped &= stopped;

								canMove = canMove && dm.canMoveDataFiles();
								if (userMode < 2) {
									TRTrackerAnnouncer trackerClient = dm.getTrackerClient();

									if (trackerClient != null) {
										boolean update_state = ((SystemTime.getCurrentTime() / 1000
												- trackerClient.getLastUpdateTime() >= TRTrackerAnnouncer.REFRESH_MINIMUM_SECS));
										manualUpdate = manualUpdate & update_state;
									}
								}

							}

							TorrentUtil.addTrackerTorrentMenu(menu, dms, changeUrl,
									manualUpdate, allStopped, use_open_containing_folder, canMove );
						}

					});
		}

		if (hasSelection) {
			Core core = CoreFactory.getSingleton();

			SpeedLimitHandler slh = SpeedLimitHandler.getSingleton(core);

			if (slh.hasAnyProfiles()) {

				createMenuRow(cParent, IMenuConstants.MENU_ID_SPEED_LIMITS, null,
						new FancyMenuRowInfoListener() {
							@Override
							public void buildMenu(Menu menu) {
								TorrentUtil.addSpeedLimitsMenu(dms, menu);
							}
						});
			}
		}

		if (userMode > 0 && hasSelection) {

			boolean can_pause_for = false;

			for (int i = 0; i < dms.length; i++) {
				DownloadManager dm = dms[i];
				if (dm.isPaused() || ManagerUtils.isPauseable(dm)) {
					can_pause_for = true;
					break;
				}
			}

			if ( can_pause_for ){

				createRow(detailArea, "MainWindow.menu.transfers.pausetransfersfor",
					null, new Listener() {
						@Override
						public void handleEvent(Event event) {
							TorrentUtil.pauseDownloadsFor(dms);
						}
					});
			}
		}

		// === advanced > options ===
		// ===========================

		if (userMode > 0 && dms.length > 1) {
			createRow(cParent, "label.options.and.info", null,
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager[] dms) {
							UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
							if (uiFunctions != null) {
								uiFunctions.getMDI().showEntryByID(
										MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_OPTIONS,
										dms);
							}
						}
					});
		}

		// === advanced > peer sources ===
		// ===============================

		if (userMode > 0) {
			createMenuRow(cParent, "MyTorrentsView.menu.peersource", null,
					new FancyMenuRowInfoListener() {
						@Override
						public void buildMenu(Menu menu) {
							TorrentUtil.addPeerSourceSubMenu(dms, menu);
						}
					});
		}
		
		// Sequential download
		
		{
			boolean allSeq		= true;
			boolean AllNonSeq 	= true;

			for (int j = 0; j < dms.length; j++) {
				DownloadManager dm = dms[j];

				boolean seq = dm.getDownloadState().getFlag( DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD);

				if (seq) {
					AllNonSeq = false;
				} else {
					allSeq = false;
				}
			}

			boolean bChecked;

			if (allSeq) {
				bChecked = true;
			} else if (AllNonSeq) {
				bChecked = false;
			} else {
				bChecked = false;
			}

			final boolean newSeq = !bChecked;

			FancyRowInfo row = createRow(cParent, "menu.sequential.download",
					null, new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager dm) {
							dm.getDownloadState().setFlag(
									DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD, newSeq );
						}
					});

			row.setSelection(bChecked);
		}

		// IP Filter Enable
		if (userMode > 0
				&& IpFilterManagerFactory.getSingleton().getIPFilter().isEnabled()) {

			boolean allEnabled = true;
			boolean allDisabled = true;

			for (int j = 0; j < dms.length; j++) {
				DownloadManager dm = dms[j];

				boolean filterDisabled = dm.getDownloadState().getFlag(
						DownloadManagerState.FLAG_DISABLE_IP_FILTER);

				if (filterDisabled) {
					allEnabled = false;
				} else {
					allDisabled = false;
				}
			}

			boolean bChecked;

			if (allEnabled) {
				bChecked = true;
			} else if (allDisabled) {
				bChecked = false;
			} else {
				bChecked = false;
			}

			final boolean newDisable = bChecked;

			FancyRowInfo row = createRow(cParent, "MyTorrentsView.menu.ipf_enable",
					null, new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager dm) {
							dm.getDownloadState().setFlag(
									DownloadManagerState.FLAG_DISABLE_IP_FILTER, newDisable);
						}
					});

			row.setSelection(bChecked);
		}

		// === advanced > networks ===
		// ===========================

		if (userMode > 1) {
			createMenuRow(cParent, "MyTorrentsView.menu.networks", null,
					new FancyMenuRowInfoListener() {
						@Override
						public void buildMenu(Menu menu) {
							TorrentUtil.addNetworksSubMenu(dms, menu);
						}
					});
		}

		// Advanced menu with stuff I don't know where to put
		if (userMode > 0) {
			createMenuRow(cParent, "MyTorrentsView.menu.advancedmenu", null,
					new FancyMenuRowInfoListener() {
						@Override
						public void buildMenu(Menu menu) {

							boolean allStopped 			= true;
							boolean allScanSelected 	= true;
							boolean allScanNotSelected 	= true;
							boolean fileMove 			= true;
							boolean allResumeIncomplete = true;
							boolean	hasClearableLinks 	= false;
							boolean hasRevertableFiles	= false;
							boolean lrrecheck			= false;
							boolean allAllocatable		= true;
							boolean allMaskDC 			= true;
							
							boolean globalMask = COConfigurationManager.getBooleanParameter( ConfigKeys.Transfer.BCFG_PEERCONTROL_HIDE_PIECE );

							for (DownloadManager dm : dms) {
								boolean stopped = ManagerUtils.isStopped(dm);

								allStopped &= stopped;

								fileMove = fileMove && dm.canMoveDataFiles();

								boolean scan = dm.getDownloadState().getFlag(
										DownloadManagerState.FLAG_SCAN_INCOMPLETE_PIECES);

								// include DND files in incomplete stat, since a recheck may
								// find those files have been completed
								boolean incomplete = !dm.isDownloadComplete(true);

								allScanSelected = incomplete && allScanSelected && scan;
								allScanNotSelected = incomplete && allScanNotSelected && !scan;

								DownloadManagerState dms = dm.getDownloadState();

								if (dms.isResumeDataComplete()){
									allResumeIncomplete = false;
								}
								if ( stopped && !hasClearableLinks ){
									TOTorrent torrent = dm.getTorrent();
									if ( torrent != null && !torrent.isSimpleTorrent()){
										if ( dms.getFileLinks().hasLinks()){
											hasClearableLinks = true;
										}
									}
								}

								if ( dm.getDownloadState().getFileLinks().size() > 0 ){

									hasRevertableFiles = true;
								}
								
								lrrecheck = lrrecheck || ManagerUtils.canLowResourceRecheck(dm);
																
								allAllocatable &= stopped && !dm.isDataAlreadyAllocated() && !dm.isDownloadComplete( false );	
								
								Boolean dmmask = dms.getOptionalBooleanAttribute( DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL );
								
								boolean mask = dmmask==null?globalMask:dmmask;
								
								allMaskDC = allMaskDC && mask;
							}

							boolean fileRescan = allScanSelected || allScanNotSelected;

							final MenuItem itemFileRescan = new MenuItem(menu, SWT.CHECK);
							Messages.setLanguageText(itemFileRescan,
									"MyTorrentsView.menu.rescanfile");
							itemFileRescan.addListener(SWT.Selection,
									new ListenerDMTask(dms) {
										@Override
										public void run(DownloadManager dm) {
											dm.getDownloadState().setFlag(
													DownloadManagerState.FLAG_SCAN_INCOMPLETE_PIECES,
													itemFileRescan.getSelection());
										}
									});
							itemFileRescan.setSelection(allScanSelected);
							itemFileRescan.setEnabled(fileRescan);
						
							// low resource recheck

							final MenuItem itemLowResourceRecheck = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(itemLowResourceRecheck, "MyTorrentsView.menu.lowresourcerecheck");
							itemLowResourceRecheck.addListener(SWT.Selection, new ListenerDMTask(dms) {
								@Override
								public void run(DownloadManager dm) {
									
									ManagerUtils.lowResourceRecheck( dm );
								}
							});
							
							itemLowResourceRecheck.setEnabled(lrrecheck);
							
							
								// revert

							final MenuItem itemRevertFiles = new MenuItem(menu, SWT.PUSH);
							itemRevertFiles.setText( MessageText.getString( "MyTorrentsView.menu.revertfiles") + "..." );
							itemRevertFiles.addListener(SWT.Selection, new ListenerDMTask(dms) {
								@Override
								public void run(DownloadManager[] dms)
								{
									FilesViewMenuUtil.revertFiles( tv, dms );
								}
							});

							itemRevertFiles.setEnabled(hasRevertableFiles);
							
								// view links
	
							final MenuItem itemViewLinks = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(itemViewLinks, "menu.view.links");
							itemViewLinks.addListener(SWT.Selection, new ListenerDMTask(dms) {
								@Override
								public void 
								run(
									DownloadManager[] dms)
								{
									ManagerUtils.viewLinks( dms );
								}
							});
						
								// clear links

							final MenuItem itemClearLinks = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(itemClearLinks, "FilesView.menu.clear.links");
							itemClearLinks.addListener(SWT.Selection, new ListenerDMTask(dms) {
								@Override
								public void run(DownloadManager dm)
								{
									if ( 	ManagerUtils.isStopped(dm) &&
											dm.getDownloadState().getFileLinks().hasLinks()){

										DiskManagerFileInfoSet fis = dm.getDiskManagerFileInfoSet();

										TOTorrent torrent = dm.getTorrent();
										
										if ( torrent != null && !torrent.isSimpleTorrent()){

											DiskManagerFileInfo[] files = fis.getFiles();

											for ( DiskManagerFileInfo file_info: files ){

												File file_link 		= file_info.getFile( true );
												File file_nolink 	= file_info.getFile( false );

												if ( !file_nolink.getAbsolutePath().equals( file_link.getAbsolutePath())){

													file_info.setLink( null, true );
												}
											}
										}
									}
								}
							});

							itemClearLinks.setEnabled(hasClearableLinks);
							
								// allocate

							MenuItem itemFileAlloc = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(itemFileAlloc,
									"label.allocate");
							itemFileAlloc.addListener(SWT.Selection, new ListenerDMTask(
									dms) {
								@Override
								public void run(DownloadManager[] dms) {
									
									ManagerUtils.allocate( dms );
								}
							});
	
							itemFileAlloc.setEnabled(allAllocatable);
					
								// clear allocation

							MenuItem itemFileClearAlloc = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(itemFileClearAlloc,
									"MyTorrentsView.menu.clear_alloc_data");
							itemFileClearAlloc.addListener(SWT.Selection, new ListenerDMTask(
									dms) {
								@Override
								public void run(DownloadManager dm) {
									dm.setDataAlreadyAllocated(false);
								}
							});

							itemFileClearAlloc.setEnabled(allStopped);

							// clear resume

							MenuItem itemFileClearResume = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(itemFileClearResume,
									"MyTorrentsView.menu.clear_resume_data");
							itemFileClearResume.addListener(SWT.Selection,
									new ListenerDMTask(dms) {
										@Override
										public void run(DownloadManager dm) {
											dm.getDownloadState().clearResumeData();
										}
									});
							itemFileClearResume.setEnabled(allStopped);

							// set resume complete

							MenuItem itemFileSetResumeComplete = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(itemFileSetResumeComplete,
									"MyTorrentsView.menu.set.resume.complete");
							itemFileSetResumeComplete.addListener(SWT.Selection, new ListenerDMTask(dms) {
								@Override
								public void run(DownloadManager dm) {
									TorrentUtils.setResumeDataCompletelyValid( dm.getDownloadState());
								}
							});
							itemFileSetResumeComplete.setEnabled(allStopped&&allResumeIncomplete);

							
								// restore resume
							
							final Menu restore_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);
							
							MenuItem itemRestoreResume = new MenuItem(menu, SWT.CASCADE);
							Messages.setLanguageText(itemRestoreResume,	"MyTorrentsView.menu.restore.resume.data");
							
							itemRestoreResume.setMenu( restore_menu );
							
							boolean restoreEnabled = false;
							
							if ( dms.length==1 && allStopped ){
								DownloadManagerState dmState = dms[0].getDownloadState();
								
								List<DownloadManagerState.ResumeHistory> history = dmState.getResumeDataHistory();
								
								if ( !history.isEmpty()){
									restoreEnabled = true;
								
									for ( DownloadManagerState.ResumeHistory h: history ){
										MenuItem itemHistory = new MenuItem(restore_menu, SWT.PUSH);
										itemHistory.setText( new SimpleDateFormat().format( new Date(h.getDate())));
										
										itemHistory.addListener(SWT.Selection,(ev)->{
											dmState.restoreResumeData( h );;
										});
									}
								}
							}
							
							itemRestoreResume.setEnabled( restoreEnabled);
			
								// mask dl comp
														
							MenuItem itemMaskDLComp = new MenuItem(menu, SWT.CHECK);
							
							if ( dms.length > 0 ){
								itemMaskDLComp.setSelection( allMaskDC );
							}
							
							Messages.setLanguageText(itemMaskDLComp,"ConfigView.label.hap");
							itemMaskDLComp.addListener(SWT.Selection, new ListenerDMTask(dms) {
								@Override
								public void run(DownloadManager dm) {
									dm.getDownloadState().setOptionalBooleanAttribute( DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL, itemMaskDLComp.getSelection());
								}
							});
							
							itemMaskDLComp.setEnabled( dms.length > 0 );

							if (userMode > 1 && isSeedingView) {

								boolean canSetSuperSeed = false;
								boolean superSeedAllYes = true;
								boolean superSeedAllNo = true;
								for (DownloadManager dm : dms) {
									PEPeerManager pm = dm.getPeerManager();

									if (pm != null) {

										if (pm.canToggleSuperSeedMode()) {

											canSetSuperSeed = true;
										}

										if (pm.isSuperSeedMode()) {

											superSeedAllYes = false;

										} else {

											superSeedAllNo = false;
										}
									} else {
										superSeedAllYes = false;
										superSeedAllNo = false;
									}
								}

								final MenuItem itemSuperSeed = new MenuItem(menu, SWT.CHECK);

								Messages.setLanguageText(itemSuperSeed,
										"ManagerItem.superseeding");

								boolean enabled = canSetSuperSeed
										&& (superSeedAllNo || superSeedAllYes);

								itemSuperSeed.setEnabled(enabled);

								final boolean selected = superSeedAllNo;

								if (enabled) {

									itemSuperSeed.setSelection(selected);

									itemSuperSeed.addListener(SWT.Selection, new ListenerDMTask(
											dms) {
										@Override
										public void run(DownloadManager dm) {
											PEPeerManager pm = dm.getPeerManager();

											if (pm != null) {

												if (pm.isSuperSeedMode() == selected
														&& pm.canToggleSuperSeedMode()) {

													pm.setSuperSeedMode(!selected);
												}
											}
										}
									});
								}
							}

							if ( userMode > 1 ){
									
									// view debug
									
								MenuItem itemViewDebug = new MenuItem(menu, SWT.PUSH);
								itemViewDebug.setText( MessageText.getString("StartStopRules.menu.viewDebug") + "..." );
								itemViewDebug.addListener(SWT.Selection, new ListenerDMTask(dms) {
									@Override
									public void 
									run(
										DownloadManager[] dms)
									{
										ManagerUtils.viewDebug( dms );
									}
								});
							}
						}
					});
		}
	}

	private void updateRowSpeed(FancyRowInfo row, boolean isUpload) {
		int dlRate = isUpload
				? dms[0].getStats().getUploadRateLimitBytesPerSecond()
				: dms[0].getStats().getDownloadRateLimitBytesPerSecond();
		for (DownloadManager dm : dms) {
			int dlRate2 = isUpload ? dm.getStats().getUploadRateLimitBytesPerSecond()
					: dm.getStats().getDownloadRateLimitBytesPerSecond();
			if (dlRate != dlRate2) {
				dlRate = -2;
				break;
			}
		}
		if (dlRate != -2) {
			String currentSpeed;
			if (dlRate == 0) {
				currentSpeed = MessageText.getString("MyTorrentsView.menu.setSpeed.unlimited");
			} else if (dlRate < 0) {
				currentSpeed = MessageText.getString("label.disabled");
			} else {
				currentSpeed = DisplayFormatters.formatByteCountToKiBEtcPerSec(dlRate);
			}
			row.setRightLabelText(currentSpeed);
			row.cRow.layout();
		}
	}

	private FancyMenuRowInfo createMenuRow(Composite cParent, String keyTitle,
			String keyImage, final FancyMenuRowInfoListener listener) {

		Listener showSWTMenuListener = new Listener() {
			int lastX = 0;

			int lastY = 0;

			@Override
			public void handleEvent(final Event event) {
				if (event.type == SWT.MouseHover && lastX == event.x
						&& lastY == event.y) {
					return;
				}
				lastX = event.x;
				lastY = event.y;

				FancyMenuRowInfo rowInfo;

				FancyRowInfo findRowInfo = findRowInfo(event.widget);
				if (!(findRowInfo instanceof FancyMenuRowInfo)) {
					return;
				}

				rowInfo = (FancyMenuRowInfo) findRowInfo;
				currentMenu = rowInfo.getMenu();
				if (currentMenu != null && !currentMenu.isDisposed()) {
					return;
				}

				currentMenu = new Menu(parentShell, SWT.POP_UP);
				rowInfo.setMenu(currentMenu);

				currentMenu.addMenuListener(new MenuListener() {

					@Override
					public void menuShown(MenuEvent arg0) {
						subMenuVisible = true;
					}

					@Override
					public void menuHidden(final MenuEvent arg0) {
						subMenuVisible = false;
						currentMenu = null;
						Utils.execSWTThreadLater(0, new Runnable() {

							@Override
							public void run() {
								arg0.widget.dispose();
							}
						});
					}
				});
				listener.buildMenu(currentMenu);

				Composite rowComposite = rowInfo.getRow();

				if (rowComposite != null) {
					Point size = rowComposite.getSize();
					Point menuLocation = rowComposite.toDisplay(size.x - 3, -3);
					currentMenu.setLocation(menuLocation);
				}
				if (currentMenu.getItemCount() > 0) {
					currentMenu.setVisible(true);

					addMenuItemListener(currentMenu, listenerForTrigger);

					final FancyMenuRowInfo currentRow = rowInfo;
					final Point currentMousePos = event.display.getCursorLocation();
					// Once the menu is visible, we don't get mouse events (even with addFilter)
					Utils.execSWTThreadLater(300, new Runnable() {
						@Override
						public void run() {
							Point cursorLocation = event.display.getCursorLocation();
							if (currentMousePos.equals(cursorLocation)) {
								Utils.execSWTThreadLater(300, this);
								return;
							}

							Control control = Utils.getCursorControl();

							if (control != null) {
								Object data = control.getData("ID");
								if (data instanceof HeaderInfo) {
									HeaderInfo header = (HeaderInfo) data;
									activateHeader(header);
								}
							}

							Menu submenu = currentRow.getMenu();
							if (submenu == null || submenu.isDisposed()
									|| !submenu.isVisible()) {
								return;
							}
							FancyRowInfo rowInfo = findRowInfo(control);
							if (rowInfo != null && rowInfo != currentRow) {
								submenu.setVisible(false);
								return;
							}
							Utils.execSWTThreadLater(300, this);
						}
					});
				} else {
					currentMenu.dispose();
					currentMenu = null;
				}
			}
		};

		FancyMenuRowInfo row = new FancyMenuRowInfo();
		createRow(cParent, keyTitle, keyImage, true, showSWTMenuListener, row);
		row.setHasSubMenu(true);

		Composite cRow = row.getRow();
		Utils.addListenerAndChildren(cRow, SWT.MouseHover, showSWTMenuListener);

		row.setKeepMenu(true);
		//row.getRightLabel().setText("\u25B6");
		Label rightLabel = row.getRightLabel();
		GridData gd = new GridData(12, SWT.DEFAULT);
		rightLabel.setLayoutData(gd);
		row.getRightLabel().addPaintListener(paintListenerArrow);

		return row;
	}

	protected void addMenuItemListener(Menu menu, Listener l) {
		for (MenuItem item : menu.getItems()) {
			if (item.getStyle() == SWT.CASCADE) {
				addMenuItemListener(item.getMenu(), l);
			} else {
				item.addListener(SWT.Selection, l);
			}
		}
	}

	private FancyRowInfo createRow(Composite cParent, String keyTitle,
			final String keyImage, final Listener triggerListener) {
		return createRow(cParent, keyTitle, keyImage, true, triggerListener,
				new FancyRowInfo());
	}

	private FancyRowInfo createRow(Composite cParent, String keyTitle,
			String keyImage, boolean triggerOnUp, Listener triggerListener) {
		return createRow(cParent, keyTitle, keyImage, triggerOnUp, triggerListener,
				new FancyRowInfo());
	}

	private FancyRowInfo createRow(Composite cParent, String keyTitle,
			String keyImage, boolean triggerOnUp, Listener triggerListener,
			FancyRowInfo rowInfo) {

		Composite cRow = new Composite(cParent, SWT.NONE);
		//cRow.setBackground(ColorCache.getRandomColor());

		cRow.setData("ID", rowInfo);
		GridLayout gridLayout = new GridLayout(4, false);
		gridLayout.marginWidth = 1;
		gridLayout.marginHeight = 3;
		gridLayout.marginRight = 4;
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 0;
		cRow.setLayout(gridLayout);

		GridData gridData;

		Label lblIcon = new Label(cRow, SWT.CENTER | SWT.NONE);
		gridData = new GridData();
		gridData.widthHint = 20;
		lblIcon.setLayoutData(gridData);
		if (keyImage != null) {
			ImageLoader.getInstance().setLabelImage(lblIcon, keyImage);
		}

		Label item = new Label(cRow, SWT.NONE);
		gridData = new GridData();
		gridData.grabExcessHorizontalSpace = true;
		gridData.horizontalIndent = 2;
		item.setLayoutData(gridData);
		Messages.setLanguageText(item, keyTitle);

		Utils.setSkinnedControlType(item, Utils.SCT_MENU_ITEM );
		
		Label lblCheck = new Label(cRow, SWT.CENTER);
		gridData = new GridData();
		gridData.widthHint = 13;
		lblCheck.setLayoutData(gridData);

		if (triggerListener != null) {
			Utils.addListenerAndChildren(cRow, triggerOnUp ? SWT.MouseUp
					: SWT.MouseDown, listenerForTrigger);
		}

		Utils.addListenerAndChildren(cRow, SWT.MouseEnter, listenerRow);
		Utils.addListenerAndChildren(cRow, SWT.MouseExit, listenerRow);

		cRow.addPaintListener(listenerRowPaint);

		rowInfo.setListener(triggerListener);
		rowInfo.setRow(cRow);
		rowInfo.setIconLabel(lblIcon);
		rowInfo.setText(item);
		rowInfo.setRightLabel(null);
		rowInfo.setCheckLabel(lblCheck);

		listRowInfos.add(rowInfo);
		return rowInfo;
	}

	private FancyRowInfo findRowInfo(Widget widget) {
		Object findData = findData(widget, "ID");
		if (findData instanceof FancyRowInfo) {
			return (FancyRowInfo) findData;

		}
		return null;
	}

	protected Object findData(Widget widget, String id) {
		if (widget == null || widget.isDisposed()) {
			return null;
		}
		Object o = widget.getData(id);
		if (o != null) {
			return o;
		}
		if (widget instanceof Control) {
			Control control = ((Control) widget).getParent();
			while (control != null) {
				o = control.getData(id);
				if (o != null) {
					return o;
				}
				control = control.getParent();
			}
		}
		return null;
	}

	private Control createActionButton(final DownloadManager[] dms,
			Composite cParent, String keyToolTip, String keyImage, boolean enable,
			Listener listener) {
		return createActionButton(dms, cParent, keyToolTip, keyImage, enable,
				false, listener);
	}

	private Control createActionButton(final DownloadManager[] dms,
			Composite cParent, String keyToolTip, final String keyImage,
			boolean enable, boolean selected, final Listener listener) {
		final Canvas item = new Canvas(cParent, SWT.NO_BACKGROUND
				| SWT.DOUBLE_BUFFERED);

		Listener l = new Listener() {
			private boolean inWidget;

			@Override
			public void handleEvent(Event e) {
				Control c = (Control) e.widget;
				if (e.type == SWT.Paint) {
					Point size = c.getSize();
					
					GC gc = e.gc;
					
					gc.fillRectangle(0, 0, size.x, size.y );
					if (inWidget) {
						gc.setBackground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
					} else {
						gc.setBackground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_LIGHT_SHADOW));
					}
					gc.setAdvanced(true);
					gc.setAntialias(SWT.ON);
					
					gc.fillRoundRectangle(0, 0, size.x - 1, size.y - 1, 6, 6);
					gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_DARK_SHADOW));
					gc.drawRoundRectangle(0, 0, size.x - 1, size.y - 1, 6, 6);
					gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
					gc.drawRoundRectangle(1, 1, size.x - 3, size.y - 3, 6, 6);

					Image image = ImageLoader.getInstance().getImage(
							c.isEnabled() ? keyImage : keyImage + "-disabled");
					Rectangle bounds = image.getBounds();
					int x = size.x / 2 - bounds.width / 2;
					int y = size.y / 2 - bounds.height / 2;

					gc.drawImage(image, x, y);
					
					if ( selected && c.isEnabled()){
						
						image = ImageLoader.getInstance().getImage( "blacktick" );
						
						bounds = image.getBounds();
						
						x = 2;
						y = size.y / 2 - bounds.height / 2;
						
						gc.drawImage(image, x, y);
					}
				} else if (e.type == SWT.MouseEnter) {
					inWidget = true;
					c.redraw();
				} else if (e.type == SWT.MouseExit) {
					inWidget = false;
					c.redraw();
				}
			}
		};

		item.addListener(SWT.MouseEnter, l);
		item.addListener(SWT.MouseExit, l);
		item.addListener(SWT.Paint, l);

		Messages.setLanguageTooltip(item, keyToolTip);
		item.addListener(SWT.MouseUp, new Listener() {
			@Override
			public void handleEvent(Event event) {
				listener.handleEvent(event);
				shell.dispose();
			}
		});
		item.setEnabled(enable);

		RowData rowData = new RowData(30, 21);
		item.setLayoutData(rowData);

		return item;
	}

	public void buildTorrentCustomMenu_Organize(final Composite detailArea,
			final DownloadManager[] dms) {

		if (!hasSelection) {
			return;
		}

			// assign cats - generally cats come before tags in the UI

		createMenuRow(detailArea, "MyTorrentsView.menu.setCategory",
				"image.sidebar.library", new FancyMenuRowInfoListener() {
					@Override
					public void buildMenu(Menu menu) {
						TorrentUtil.addCategorySubMenu(dms, menu);
					}
				});
		
			// assign tags

		createMenuRow(detailArea, "label.tags", "image.sidebar.tag-overview",
				new FancyMenuRowInfoListener() {
					@Override
					public void buildMenu(Menu menu) {
						TagUIUtils.addLibraryViewTagsSubMenu(dms, menu);
					}
				});

			// Archive

		final List<Download>	ar_dms = new ArrayList<>();

		for ( DownloadManager dm: dms ){

			Download stub = PluginCoreUtils.wrap(dm);

			if ( !stub.canStubbify()){

				continue;
			}

			ar_dms.add( stub );
		}

		if ( ar_dms.size() > 0 ){

			createRow(
				detailArea,
				"MyTorrentsView.menu.archive", "image.sidebar.archive",
				new Listener()
				{
					@Override
					public void handleEvent(Event event) {

						ManagerUtils.moveToArchive( ar_dms, null );
					}
				});
		}


			// Advanced - > Rename

		createRow(detailArea, "MyTorrentsView.menu.rename", null,
				event -> ManagerUtils.advancedRename(dms));

			// Reposition

		createRow(detailArea, "MyTorrentsView.menu.reposition.manual", null,
				new Listener() {
					@Override
					public void handleEvent(Event event) {
						TorrentUtil.repositionManual(tv, dms, parentShell, isSeedingView);
					}
				});

		createRow(detailArea, "Button.selectAll", null, new Listener() {
			@Override
			public void handleEvent(Event event) {
				tv.selectAll();
			}
		});
		
			// Filter
		
		if (tv.getSWTFilter() != null) {
			createRow(detailArea, "MyTorrentsView.menu.filter", null, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tv.openFilterDialog();
				}
			});
		}

		createRow(detailArea, "MyTorrentsView.menu.editTableColumns", "columns", new Listener() {
			@Override
			public void handleEvent(Event event) {
				TableRowCore focusedRow = tv.getFocusedRow();
				if (focusedRow == null || focusedRow.isRowDisposed()) {
					focusedRow = tv.getRow(0);
				}
				String tableID = tv.getTableID();
				new TableColumnSetupWindow(tv.getDataSourceType(), tableID, column, focusedRow,
						TableStructureEventDispatcher.getInstance(tableID)).open();
			}
		});
	}

	public void buildTorrentCustomMenu_Social(Composite detailArea) {

		boolean isTrackerOn = TRTrackerUtils.isTrackerEnabled();
		int userMode = COConfigurationManager.getIntParameter("User Mode");

		if (hasSelection) {
			createMenuRow(detailArea, "ConfigView.section.interface.alerts", null,
					new FancyMenuRowInfoListener() {
						@Override
						public void buildMenu(Menu menu) {
							MenuFactory.addAlertsMenu(menu, false, dms);
						}
					});
		}

		if (userMode > 0 && isTrackerOn && hasSelection) {
			// Host
			createRow(detailArea, "MyTorrentsView.menu.host", "host", new Listener() {
				@Override
				public void handleEvent(Event event) {
					TorrentUtil.hostTorrents(dms);
				}
			});

			// Publish
			createRow(detailArea, "MyTorrentsView.menu.publish", "publish",
					new Listener() {
						@Override
						public void handleEvent(Event event) {
							TorrentUtil.publishTorrents(dms);
						}
					});
		}

		if (userMode > 0) {
			// Advanced > Export > Export XML
			if (dms.length == 1) {
				String title = MessageText.getString("MyTorrentsView.menu.exportmenu")
						+ ": " + MessageText.getString("MyTorrentsView.menu.export");
				FancyRowInfo row = createRow(detailArea, null, null,
						new ListenerDMTask(dms) {
							@Override
							public void run(DownloadManager dm) {
								if (dm != null) {
									new ExportTorrentWizard(parentShell.getDisplay(), dm);
								}
							}
						});
				row.getText().setText(title);
				
				TOTorrent torrent = dms[0].getTorrent();
				
				row.setEnabled( torrent != null && torrent.isExportable());
			}

			// Advanced > Export > Export Torrent
			String title = MessageText.getString("MyTorrentsView.menu.exportmenu")
					+ ": " + MessageText.getString("MyTorrentsView.menu.exporttorrent");
			FancyRowInfo row = createRow(detailArea, null, null, new ListenerDMTask(
					dms) {
				@Override
				public void run(DownloadManager[] dms) {
					TorrentUtil.exportTorrent(dms, parentShell);
				}
			});
			row.getText().setText(title);

			boolean canExport = true;
			for ( DownloadManager dm: dms ){
				if ( !dm.getTorrent().isExportable()){
					canExport = false;
				}
			}
			
			row.setEnabled( canExport );
			
			// Advanced > Export > WebSeed URL
			createRow(detailArea, "MyTorrentsView.menu.exporthttpseeds", null,
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager[] dms) {
							TorrentUtil.exportHTTPSeeds(dms);
						}
					});
		}

		// personal share
		if (isSeedingView) {
			boolean	can_share_pers = dms.length > 0;

			for ( DownloadManager dm: dms ){

				File file = dm.getSaveLocation();

				if ( !file.exists()){
					
					can_share_pers = false;
					
					break;
				}
			}
			
			if ( can_share_pers ){
				
				createRow(detailArea, "MyTorrentsView.menu.create_personal_share", null,
						new ListenerDMTask(dms, false) {
							@Override
							public void run(DownloadManager dm) {
								File file = dm.getSaveLocation();
	
								Map<String, String> properties = new HashMap<>();
		
								if ( Utils.setPeronalShare( properties )){
								
									if (file.isFile()) {
		
										ShareUtils.shareFile(file.getAbsolutePath(), properties);
		
									} else if (file.isDirectory()) {
		
										ShareUtils.shareDir(file.getAbsolutePath(), properties);
									}
								}
							}
						});
			}
		}

	}

	private Object[] getTarget(com.biglybt.pif.ui.menus.MenuItem item) {
		if (MenuManager.MENU_TABLE.equals(item.getMenuID())) {
			TableRowCore[] rows = tv.getSelectedRows();
			
			List<TableRowCore> result = new ArrayList<TableRowCore>(rows.length);
			
			for (TableRowCore row: rows ){
				if ( row.getDataSource( false ) instanceof Download ){
					result.add( row );
				}
			}
			
			return( result.toArray( new TableRowCore[result.size()]));
		}else{
			Object[] dataSources = tv.getSelectedDataSources(false);
			
			List<Download> result = new ArrayList<>(dataSources.length);
			
			for ( Object ds: dataSources ){
				if ( ds instanceof Download ){
			
					result.add((Download)ds );
				}
			}
			
			return( result.toArray( new Download[result.size()]));
		}
	}

	private void addPluginItem(Composite detailArea,
			final com.biglybt.pif.ui.menus.MenuItem item) {

		// menuWillBeShown listeners might change the visibility, so run before check
		MenuItemImpl menuImpl = ((MenuItemImpl) item);
		menuImpl.invokeMenuWillBeShownListeners(getTarget(item));

		if (!item.isVisible()) {
			if (DEBUG_MENU) {
				System.out.println("Menu Not Visible: " + item.getText() + ": "
						+ item.getMenuID());
			}
			return;
		}
		
			// Disabled items are removed from fancy menus - if you want to change this then you will need to
			// code up a disabled look for the rows as currently they just show as normal but aren't selectable
			// which ain't good
		
		if (!item.isEnabled()) {
			if (DEBUG_MENU) {
				System.out.println("Menu Not enabled: " + item.getText() + ": "
						+ item.getMenuID());
			}
			return;
		}
		

		Graphic graphic = item.getGraphic();
		FancyRowInfo row;

		if (DEBUG_MENU) {
			System.out.println("Menu " + item.getText() + ": " + item.getMenuID());
		}

		int style = item.getStyle();
		
		if ( style == com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU) {

			row = createMenuRow(detailArea, item.getResourceKey(), null,
					new FancyMenuRowInfoListener() {
						@Override
						public void buildMenu(Menu menu) {
							if (dms.length != 0) {
								MenuBuilder submenuBuilder = ((MenuItemImpl) item).getSubmenuBuilder();
								if (submenuBuilder != null) {
									try {
										item.removeAllChildItems();
										submenuBuilder.buildSubmenu(item, getTarget(item));
									} catch (Throwable t) {
										Debug.out(t);
									}
								}

								MenuBuildUtils.addPluginMenuItems(item.getItems(), menu, false,
										true, new MenuBuildUtils.PluginMenuController() {

											@Override
											public Listener makeSelectionListener(
													final com.biglybt.pif.ui.menus.MenuItem plugin_menu_item) {
												return new TableSelectedRowsListener(tv, false) {
													@Override
													public boolean run(TableRowCore[] rows) {
														if (rows.length != 0) {
															((MenuItemImpl) plugin_menu_item).invokeListenersMulti(getTarget(item));
														}
														return true;
													}
												};
											}

											@Override
											public void notifyFillListeners(
													com.biglybt.pif.ui.menus.MenuItem menu_item) {
												((MenuItemImpl) menu_item).invokeMenuWillBeShownListeners(getTarget(item));
											}

											// @see com.biglybt.ui.swt.MenuBuildUtils.PluginMenuController#buildSubmenu(com.biglybt.pif.ui.menus.MenuItem)
											@Override
											public void buildSubmenu(
													com.biglybt.pif.ui.menus.MenuItem parent) {
												com.biglybt.pif.ui.menus.MenuBuilder submenuBuilder = ((MenuItemImpl) parent).getSubmenuBuilder();
												if (submenuBuilder != null) {
													try {
														parent.removeAllChildItems();
														submenuBuilder.buildSubmenu(parent, getTarget(item));
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
												addMenuItemListener(menu, listenerForTrigger);
											}
										});
							}
						}

					});
		} else {
			row = createRow(detailArea, item.getResourceKey(), null,
					new TableSelectedRowsListener(tv, false) {

						@Override
						public boolean run(TableRowCore[] rows) {
							
							if ( style == com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK ){
								
								Boolean b = (Boolean)item.getData();
								
								boolean newSel = !(b != null && b);
								
								item.setData( newSel );
							}
							
							if (rows.length != 0) {
								((MenuItemImpl) item).invokeListenersMulti(getTarget(item));
							}
							return true;
						}

					});
			
			if ( style == com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK ){
				
				Boolean b = (Boolean)item.getData();
									
				row.setSelection(  b != null && b );
			}
		}

		row.setEnabled(item.isEnabled());
		if (graphic instanceof UISWTGraphic) {
			row.getIconLabel().setImage(((UISWTGraphic) graphic).getImage());
		} else if (graphic instanceof GraphicURI) {
			ImageLoader.getInstance().setLabelImage(row.getIconLabel(),
					((GraphicURI) graphic).getURI().toString());
		}
	}

	protected void buildTorrentCustomMenu_Content(final Composite detailArea,
			final DownloadManager[] dms) {

		// Run Data File
		if (hasSelection) {
			createRow(detailArea, "MyTorrentsView.menu.open", "run",
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager[] dms) {
							TorrentUtil.runDataSources(dms);
						}
					});
		}

		if (hasSelection && MenuBuildUtils.hasOpenWithMenu( dms )) {
			createMenuRow(
				detailArea,
				"menu.open.with", null,
				new FancyMenuRowInfoListener()
				{
					@Override
					public void
					buildMenu(
						Menu menuOpenWith )
					{
						MenuBuildUtils.addOpenWithMenu( menuOpenWith, true, dms );
					}
				});
		}
		
		// Explore (or open containing folder)
		if (hasSelection) {
			final boolean use_open_containing_folder = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");
			createRow(detailArea, "MyTorrentsView.menu."
					+ (use_open_containing_folder ? "open_parent_folder" : "explore"),
					null, new ListenerDMTask(dms, false) {
						@Override
						public void run(DownloadManager dm) {
							ManagerUtils.open(dm, use_open_containing_folder);
						}
					});
		}

		// Open In Browser

		if (hasSelection) {
			createMenuRow(
				detailArea,
				"MyTorrentsView.menu.browse",
				null,
				new FancyMenuRowInfoListener()
				{
					@Override
					public void
					buildMenu(
						Menu menuBrowse )
					{
						final MenuItem itemBrowsePublic = new MenuItem(menuBrowse, SWT.PUSH);
						itemBrowsePublic.setText( MessageText.getString( "label.public" ) + "..." );
						itemBrowsePublic.addListener(
							SWT.Selection,
							new ListenerDMTask(dms, false) {
								@Override
								public void run(DownloadManager dm) {
									ManagerUtils.browse( dm, false, true );
								}
							});

						final MenuItem itemBrowseAnon = new MenuItem(menuBrowse, SWT.PUSH);
						itemBrowseAnon.setText( MessageText.getString( "label.anon" ) + "..." );
						itemBrowseAnon.addListener(
							SWT.Selection,
							new ListenerDMTask(dms, false) {
								@Override
								public void run(DownloadManager dm) {
									ManagerUtils.browse( dm, true, true );
								}
							});

						new MenuItem(menuBrowse, SWT.SEPARATOR);

						final MenuItem itemBrowseURL = new MenuItem(menuBrowse, SWT.PUSH);
						Messages.setLanguageText(itemBrowseURL, "label.copy.url.to.clip" );
						itemBrowseURL.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event){
								Utils.getOffOfSWTThread(
									new AERunnable() {
										@Override
										public void runSupport() {
											String url = ManagerUtils.browse(dms[0], true, false );
											if ( url != null ){
												ClipboardCopy.copyToClipBoard( url );
											}
										}
									});
							}});

						itemBrowseURL.setEnabled( dms.length == 1 );

						new MenuItem(menuBrowse, SWT.SEPARATOR);

						final MenuItem itemBrowseDir = new MenuItem(menuBrowse, SWT.CHECK);
						Messages.setLanguageText(itemBrowseDir, "library.launch.web.in.browser.dir.list");
						itemBrowseDir.setSelection(COConfigurationManager.getBooleanParameter( "Library.LaunchWebsiteInBrowserDirList"));
						itemBrowseDir.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event) {
								COConfigurationManager.setParameter( "Library.LaunchWebsiteInBrowserDirList", itemBrowseDir.getSelection());
							}
						});
					}
				});
		}

			// set thumbnail

		createRow(detailArea, "MyTorrentsView.menu.torrent.set.thumb", null,
				new ListenerDMTask(dms) {
					@Override
					public void run(DownloadManager[] dms) {
						FileDialog fDialog = new FileDialog(parentShell, SWT.OPEN | SWT.MULTI);

						fDialog.setText(MessageText.getString("MainWindow.dialog.choose.thumb"));
						String path = fDialog.open();
						if (path == null)
							return;

						File file = new File( path );

						try{
							byte[] thumbnail = FileUtil.readFileAsByteArray( file );

							String name = file.getName();

							int	pos = name.lastIndexOf( "." );

							String ext;

							if ( pos != -1 ){

								ext = name.substring( pos+1 );

							}else{

								ext = "";
							}

							String type = HTTPUtils.guessContentTypeFromFileType( ext );

							for ( DownloadManager dm: dms ){

								try{
									TOTorrent torrent = dm.getTorrent();

									PlatformTorrentUtils.setContentThumbnail( torrent, thumbnail, type );

								}catch( Throwable e ){

								}
							}
						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				});

		boolean fileMove 		= true;
		boolean locateFiles 	= false;
		boolean	exportFiles		= true;
		boolean	canSetMOC		= dms.length > 0;
		boolean canClearMOC		= false;
		
		for (int i = 0; i < dms.length; i++) {
			DownloadManager dm = dms[i];
			if (!dm.canMoveDataFiles()) {
				fileMove = false;
			}
			if ( !dm.canExportDownload()){
				exportFiles = false;
			}
			int state = dm.getState();
			
			if ( 	!dm.isDownloadComplete( false ) ||  
					state == DownloadManager.STATE_ERROR || 
					state == DownloadManager.STATE_STOPPED ){
				
				locateFiles = true;
			}
			
			boolean incomplete = !dm.isDownloadComplete(true);

			DownloadManagerState dm_state = dm.getDownloadState();

			String moc_dir = dm_state.getAttribute( DownloadManagerState.AT_MOVE_ON_COMPLETE_DIR );
			
			canSetMOC &= incomplete;
			canClearMOC |= (moc_dir != null && moc_dir.length() > 0 );
		}
		
		if (fileMove) {
			createRow(detailArea, "MyTorrentsView.menu.movedata", null,
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager[] dms) {
							TorrentUtil.moveDataFiles(parentShell, dms,false);
						}
					});
			
			int userMode = COConfigurationManager.getIntParameter("User Mode");
			
			if ( userMode > 0 ){
				createRow(detailArea, "MyTorrentsView.menu.movedata.batch", null,
						new ListenerDMTask(dms) {
							@Override
							public void run(DownloadManager[] dms) {
								TorrentUtil.moveDataFiles(parentShell, dms, true);
							}
						});
			}
		}
		
		if ( canSetMOC || canClearMOC ){
			
			boolean f_canSetMOC = canSetMOC;
			boolean f_canClearMOC = canClearMOC;
			
			createMenuRow(
					detailArea,
					"label.move.on.comp",
					null,
					new FancyMenuRowInfoListener()
					{
						@Override
						public void
						buildMenu(
							Menu moc_menu )
						{
							MenuItem clear_item = new MenuItem( moc_menu, SWT.PUSH);

							Messages.setLanguageText( clear_item, "Button.clear" );

							clear_item.addListener(SWT.Selection, new ListenerDMTask(dms) {
								@Override
								public void run(DownloadManager[] dms) {
									TorrentUtil.clearMOC(dms);
								}
							});

							clear_item.setEnabled( f_canClearMOC );
							
							MenuItem set_item = new MenuItem( moc_menu, SWT.PUSH);

							Messages.setLanguageText( set_item, "label.set" );
							
							set_item.addListener(SWT.Selection, new ListenerDMTask(dms) {
								@Override
								public void run(DownloadManager[] dms) {
									TorrentUtil.setMOC(parentShell, dms);
								}
							});
							
							set_item.setEnabled( f_canSetMOC );
						}
					});
		}
		
		if (exportFiles) {
			createRow(detailArea, "MyTorrentsView.menu.exportdownload", null,
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager[] dms) {
							TorrentUtil.exportDownloads(parentShell, dms);
						}
					});
		}
		createRow(detailArea, "MyTorrentsView.menu.checkfilesexist", null,
				new ListenerDMTask(dms) {
					@Override
					public void run(DownloadManager dm) {
						dm.filesExist(true);
					}
				});

		if ( locateFiles ){
			createRow(detailArea, "MyTorrentsView.menu.locatefiles", null,
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager[] dms) {
							ManagerUtils.locateFiles(dms,parentShell);
						}
					});
		}

		if ( dms.length == 1 && ManagerUtils.canFindMoreLikeThis()){
			createRow(detailArea, "MyTorrentsView.menu.findmorelikethis", null,
					new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager[] dms) {
							ManagerUtils.findMoreLikeThis(dms[0],parentShell);
						}
					});
		}

		createRow(detailArea, "MyTorrentsView.menu.thisColumn.toClipboard", null,
				new Listener() {
					@Override
					public void handleEvent(Event event) {
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

	private HeaderInfo addHeader(String id, String title, AERunnable runnable) {
		Composite composite = new Composite(topArea, SWT.NONE);

		HeaderInfo headerInfo = new HeaderInfo(id, runnable, composite);

		FillLayout fillLayout = new FillLayout();
		fillLayout.marginWidth = 6;
		fillLayout.marginHeight = 2;
		composite.setLayout(fillLayout);
		Display d = composite.getDisplay();
		composite.setBackground(Colors.getSystemColor(d, SWT.COLOR_LIST_BACKGROUND));
		composite.setForeground(Colors.getSystemColor(d, SWT.COLOR_LIST_FOREGROUND));

		Label control = new Label(composite, SWT.NONE);
		Messages.setLanguageText(control, title);
		control.setData("ID", headerInfo);

		control.addListener(SWT.MouseEnter, headerListener);
		control.addListener(SWT.Touch, headerListener);
		control.addListener(SWT.MouseExit, headerListener);
		control.addListener(SWT.Paint, headerListener);

		listHeaders.add(headerInfo);
		return headerInfo;
	}

}
