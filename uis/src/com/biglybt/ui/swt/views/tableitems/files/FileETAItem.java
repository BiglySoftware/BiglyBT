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

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.views.ViewUtils;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class FileETAItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	private ViewUtils.CustomDateFormat cdf;
	private final MyParameterListener myParameterListener;
	private boolean eta_absolute;

	public
	FileETAItem()
	{
		super( "file_eta", ALIGN_TRAIL, POSITION_INVISIBLE, 60, TableManager.TABLE_TORRENT_FILES);

		setRefreshInterval( INTERVAL_LIVE );

		myParameterListener = new MyParameterListener();
		COConfigurationManager.addWeakParameterListener(
				myParameterListener, true, "mtv.eta.show_absolute");

		cdf = ViewUtils.addCustomDateFormat( this );
	}

	@Override
	public void remove() {
		super.remove();

		COConfigurationManager.removeWeakParameterListener(myParameterListener,
				"mtv.eta.show_absolute");
	}

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info )
	{
		info.addCategories( new String[]{
			CAT_PROGRESS,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE );
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();

		long eta = -1;

		if ( fileInfo != null ){

			eta = fileInfo.getETA();
		}

		if (!cell.setSortValue(eta) && cell.isValid()) {

			return;
		}

		cell.setText( cdf.formatETA( eta, eta_absolute ));
	}

	@Override
	public void
	postConfigLoad()
	{
		super.postConfigLoad();

		cdf.update();
	}

	private class MyParameterListener implements ParameterListener {
		@Override
		public void
		parameterChanged(
				String name) {
			eta_absolute = COConfigurationManager.getBooleanParameter("mtv.eta.show_absolute", false);
		}
	}
}
