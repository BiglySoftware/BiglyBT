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

package com.biglybt.ui.swt.views.tableitems.files;

import java.io.File;

import org.eclipse.swt.graphics.Image;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;



public class PathNameItem extends CoreTableColumnSWT implements
		TableCellLightRefreshListener, ObfuscateCellText, TableCellDisposeListener
{
	private final ParamListenerShowProgramIcon paramListenerShowProgramIcon = new ParamListenerShowProgramIcon();
	private final ParameterListener configShowFullPathListener;
	private boolean show_full_path;
	private boolean bShowIcon;

	/** Default Constructor */
	public PathNameItem() {
		super("pathname", ALIGN_LEAD, POSITION_INVISIBLE, 500,
				TableManager.TABLE_TORRENT_FILES);
		setObfuscation(true);
		setType(TableColumn.TYPE_TEXT);

		COConfigurationManager.addWeakParameterListener(
				paramListenerShowProgramIcon, true, "NameColumn.showProgramIcon");
		configShowFullPathListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				show_full_path = COConfigurationManager.getBooleanParameter( "FilesView.show.full.path" );
			}
		};
		COConfigurationManager.addWeakParameterListener(configShowFullPathListener,
				true, "FilesView.show.full.path");
	}

	@Override
	public void remove() {
		super.remove();

		COConfigurationManager.removeWeakParameterListener(
				paramListenerShowProgramIcon, "NameColumn.showProgramIcon");
		COConfigurationManager.removeWeakParameterListener(
				configShowFullPathListener, "FilesView.show.full.path");
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void refresh(TableCell cell, boolean sortOnlyRefresh)
	{
		final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		String file_name = (fileInfo == null) ? "" : fileInfo.getFile(true).getName();
		if (file_name == null)
			file_name = "";
		String file_path = PathItem.determinePath(fileInfo, show_full_path);

		if ( !file_path.isEmpty()){

			if ( !file_path.endsWith( File.separator )){

				file_path += File.separator;
			}

			file_name = file_path + file_name;
		}
		//setText returns true only if the text is updated
		if (cell.setText(file_name) || !cell.isValid()) {
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
		return( UIDebugGenerator.obfuscateFileName((DiskManagerFileInfo) cell.getDataSource()));
	}

	@Override
	public void dispose(TableCell cell) {
	}

	private class ParamListenerShowProgramIcon implements ParameterListener {
		@Override
		public void parameterChanged(String parameterName) {
			bShowIcon = COConfigurationManager.getBooleanParameter("NameColumn.showProgramIcon");
		}
	}
}
