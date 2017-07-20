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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import org.eclipse.swt.graphics.Image;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;

/** Torrent name cell for My Torrents.
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class NameItem extends CoreTableColumnSWT implements
		TableCellLightRefreshListener, ObfuscateCellText, TableCellDisposeListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "name";
	private final ParameterListener configShowProgramIconListener;

	private boolean showIcon;

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_ESSENTIAL, CAT_CONTENT });
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/**
	 *
	 * @param sTableID
	 */
	public NameItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 250, sTableID);
		setObfuscation(true);
		setRefreshInterval(INTERVAL_LIVE);
		setType(TableColumn.TYPE_TEXT);
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
						object = ((TableRowCore) object).getDataSource(true);
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


		configShowProgramIconListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				setShowIcon(COConfigurationManager.getBooleanParameter("NameColumn.showProgramIcon"));
			}
		};
		COConfigurationManager.addWeakParameterListener(
				configShowProgramIconListener, true, "NameColumn.showProgramIcon");
	}

	@Override
	public void remove() {
		super.remove();

		COConfigurationManager.removeWeakParameterListener(
				configShowProgramIconListener, "NameColumn.showProgramIcon");
	}

	@Override
	public void reset() {
		super.reset();
		COConfigurationManager.removeParameter("NameColumn.showProgramIcon");
	}

	@Override
	public void refresh(TableCell cell)
	{
		refresh(cell, false);
	}

	@Override
	public void refresh(TableCell cell, boolean sortOnlyRefresh)
	{
		String name = null;
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		if (dm != null)
			name = dm.getDisplayName();
		if (name == null)
			name = "";

		//setText returns true only if the text is updated
		if ((cell.setText(name) || !cell.isValid())) {
			if (dm != null && isShowIcon() && !sortOnlyRefresh
					&& (cell instanceof TableCellSWT)) {
				DiskManagerFileInfo fileInfo = dm.getDownloadState().getPrimaryFile();
				if (fileInfo != null) {
					// Don't ever dispose of PathIcon, it's cached and may be used elsewhere
					TOTorrent torrent = dm.getTorrent();
					Image icon = ImageRepository.getPathIcon(
							fileInfo.getFile(false).getName(), false, torrent != null
									&& !torrent.isSimpleTorrent());
					((TableCellSWT) cell).setIcon(icon);
				}
			}
		}
	}

	@Override
	public String getObfuscatedText(TableCell cell) {
		String name = null;
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		if (dm != null) {
			name = dm.toString();
			int i = name.indexOf('#');
			if (i > 0) {
				name = name.substring(i + 1);
			}
		}

		if (name == null)
			name = "";
		return name;
	}

	@Override
	public void dispose(TableCell cell) {

	}

	private void disposeCellIcon(TableCell cell) {
		if (!(cell instanceof TableCellSWT)) {
			return;
		}
		final Image img = ((TableCellSWT) cell).getIcon();
		if (img != null) {
			((TableCellSWT) cell).setIcon(null);
			if (!img.isDisposed()) {
				img.dispose();
			}
		}
	}


	/**
	 * @param showIcon the showIcon to set
	 */
	public void setShowIcon(boolean showIcon) {
		this.showIcon = showIcon;
	}


	/**
	 * @return the showIcon
	 */
	public boolean isShowIcon() {
		return showIcon;
	}

}
