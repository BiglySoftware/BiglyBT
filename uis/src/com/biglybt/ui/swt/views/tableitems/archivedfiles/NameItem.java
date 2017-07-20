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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.tableitems.archivedfiles;

import java.io.File;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.download.DownloadStub.DownloadStubFile;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellLightRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;



public class
NameItem
	extends CoreTableColumnSWT
	implements TableCellLightRefreshListener, ObfuscateCellText
{
	private final ParameterListener configShowFullPathListener;
	private boolean show_full_path;

	public
	NameItem(
		String tableID )
	{
		super(	"name", ALIGN_LEAD, POSITION_LAST, 400, tableID );

		configShowFullPathListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				show_full_path = COConfigurationManager.getBooleanParameter( "ArchivedFilesView.show.full.path" );
			}
		};
		COConfigurationManager.addWeakParameterListener(configShowFullPathListener,
				true, "ArchivedFilesView.show.full.path");

		setType(TableColumn.TYPE_TEXT);
	}

	@Override
	public void remove() {
		super.remove();

		COConfigurationManager.removeWeakParameterListener(
				configShowFullPathListener, "ArchivedFilesView.show.full.path");
	}

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info )
	{
		info.addCategories(new String[] {
			CAT_CONTENT,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void
	refresh(
		TableCell 	cell,
		boolean 	sortOnlyRefresh)
	{
		DownloadStubFile fileInfo = (DownloadStubFile) cell.getDataSource();

		String name;

		if ( fileInfo == null ){

			name = "";

		}else{

			File f = fileInfo.getFile();

			if ( show_full_path ){

				name = f.getAbsolutePath();
			}else{

				name = f.getName();
			}
		}

		cell.setText(name);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		refresh(cell, false);
	}

	@Override
	public String
	getObfuscatedText(
		TableCell cell)
	{
		DownloadStubFile fileInfo = (DownloadStubFile) cell.getDataSource();

		String name = (fileInfo == null) ? "" : Debug.secretFileName(fileInfo.getFile().getName());

		return( name );
	}
}
