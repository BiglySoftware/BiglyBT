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

package com.biglybt.ui.swt.views.tableitems.files;

import java.io.File;
import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;

/** Torrent name cell for My Torrents.
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class NameItem extends CoreTableColumnSWT implements
		TableCellLightRefreshListener, ObfuscateCellText, TableCellDisposeListener, TableCellInplaceEditorListener
{
	private static boolean bShowIcon;

	private ParameterListener configShowProgramIconListener;

	final TableContextMenuItem menuItem;

	/** Default Constructor */
	public NameItem() {
		super("name", ALIGN_LEAD, POSITION_LAST, 300,
				TableManager.TABLE_TORRENT_FILES);
		setObfuscation(true);
		setInplaceEditorListener(this);
		setType(TableColumn.TYPE_TEXT);

		configShowProgramIconListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				bShowIcon = COConfigurationManager.getBooleanParameter("NameColumn.showProgramIcon");
			}
		};
		COConfigurationManager.addWeakParameterListener(configShowProgramIconListener, true,
				"NameColumn.showProgramIcon");

		menuItem = addContextMenuItem("Files.column.name.fastRename", MENU_STYLE_HEADER);

		menuItem.setStyle(MenuItem.STYLE_CHECK);
		menuItem.setData(Boolean.valueOf(hasInplaceEditorListener()));

		menuItem.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				menu.setData(Boolean.valueOf(!hasInplaceEditorListener()));
				setInplaceEditorListener(hasInplaceEditorListener() ? null : NameItem.this);
			}
		});
	}

	@Override
	public void remove() {
		super.remove();
		COConfigurationManager.removeWeakParameterListener(configShowProgramIconListener,
				"NameColumn.showProgramIcon");
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void postConfigLoad() {
		setInplaceEditorListener(getUserData("noInplaceEdit") == null ? null : this);
		menuItem.setData(Boolean.valueOf(hasInplaceEditorListener()));
	}

	@Override
	public void preConfigSave() {
		if(hasInplaceEditorListener())
			removeUserData("noInplaceEdit");
		else
			setUserData("noInplaceEdit", new Integer(1));
	}

	@Override
	public void refresh(TableCell cell, boolean sortOnlyRefresh)
	{
		final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		String name = (fileInfo == null) ? "" : fileInfo.getFile(true).getName();
		if (name == null)
			name = "";
		//setText returns true only if the text is updated
		if (cell.setText(name) || !cell.isValid()) {
			if (bShowIcon && !sortOnlyRefresh) {
				Image icon = null;

				final TableCellSWT _cell = (TableCellSWT)cell;

				if (fileInfo == null) {
					icon = null;
				} else {

					// Don't ever dispose of PathIcon, it's cached and may be used elsewhere

					if ( Utils.isSWTThread()){

						icon = ImageRepository.getPathIcon(fileInfo.getFile(true).getPath(),
								cell.getHeight() > 32, false);
					}else{
							// happens rarely (seen of filtering of file-view rows
							// when a new row is added )

						Utils.execSWTThread(
							new Runnable()
							{
								@Override
								public void
								run()
								{
									Image icon = ImageRepository.getPathIcon(fileInfo.getFile(true).getPath(),
											_cell.getHeight() > 32, false);

									_cell.setIcon(icon);

									_cell.redraw();
								}
							});
					}
				}

				// cheat for core, since we really know it's a TabeCellImpl and want to use
				// those special functions not available to Plugins

				if ( icon != null ){
					_cell.setIcon(icon);
				}
			}
		}
	}

	@Override
	public void refresh(TableCell cell)
	{
		refresh(cell, false);
	}

	@Override
	public String getObfuscatedText(TableCell cell) {
		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		String name = (fileInfo == null) ? "" : fileInfo.getIndex() + ": "
				+ Debug.secretFileName(fileInfo.getFile(true).getName());
		return name;
	}

	@Override
	public void dispose(TableCell cell) {
	}

	private void disposeCellIcon(TableCell cell) {
		final Image img = ((TableCellSWT) cell).getIcon();
		if (img != null) {
			((TableCellSWT) cell).setIcon(null);
			if (!img.isDisposed()) {
				img.dispose();
			}
		}
	}

	@Override
	public boolean inplaceValueSet(TableCell cell, String value, boolean finalEdit) {
		if (value.equalsIgnoreCase(cell.getText()) || "".equals(value) || "".equals(cell.getText()))
			return true;
		final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		final File target;

		try
		{
			target = new File(fileInfo.getFile(true).getParentFile(), value).getCanonicalFile();
		} catch (IOException e)
		{
			return false;
		}

		if(!finalEdit)
			return !target.exists();


		if(target.exists())
			return false;


		// code stolen from FilesView
		final boolean[] result = { false };
		boolean paused = fileInfo.getDownloadManager().pause();
		FileUtil.runAsTask(new CoreOperationTask()
		{
			@Override
			public void run(CoreOperation operation) {
				result[0] = fileInfo.setLink(target);
			}
		});
		if(paused)
			fileInfo.getDownloadManager().resume();

		if (!result[0])
		{
			new MessageBoxShell(SWT.ICON_ERROR | SWT.OK,
					MessageText.getString("FilesView.rename.failed.title"),
					MessageText.getString("FilesView.rename.failed.text")).open(null);
		}

		return true;
	}

}
