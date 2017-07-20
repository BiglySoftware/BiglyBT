/*
 * Created on Feb 26, 2009
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.devices.columns;

import java.util.Locale;

import com.biglybt.core.devices.Device;
import com.biglybt.core.devices.DeviceMediaRenderer;
import com.biglybt.core.devices.TranscodeFile;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.internat.MessageText.MessageTextListener;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.tables.*;

/**
 * @author TuxPaper
 * @created Feb 26, 2009
 *
 */
public class ColumnTJ_CopiedToDevice
implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "copied";

	private String	na_text;

	public ColumnTJ_CopiedToDevice(final TableColumn column) {
		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_LAST, 50);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
		column.setType(TableColumn.TYPE_TEXT_ONLY);

		MessageText.addAndFireListener(new MessageTextListener() {
			@Override
			public void localeChanged(Locale old_locale, Locale new_locale) {

				na_text = MessageText.getString( "general.na.short" );

				column.invalidateCells();
			}
		});
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}


	@Override
	public void refresh(TableCell cell) {
		TranscodeFile tf = (TranscodeFile) cell.getDataSource();
		if (tf == null) {
			return;
		}

		Device d = tf.getDevice();

		String value = null;

		if ( d instanceof DeviceMediaRenderer ){

			DeviceMediaRenderer	dmr = (DeviceMediaRenderer)d;

			if (!(dmr.canCopyToDevice()|| dmr.canCopyToFolder())){

				value = na_text;
			}
		}

		if ( value == null ){
			value = DisplayFormatters.getYesNo( tf.isCopiedToDevice());
		}

		if (cell.setSortValue(value) || !cell.isValid()) {
			cell.setText(value);
		}
	}
}
