/*
 * File    : CategoryItem.java
 * Created : 01 feb. 2004
 * By      : TuxPaper
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

package com.biglybt.ui.swt.columns.tag;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnExtraInfoListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureRateLimit;
import com.biglybt.core.util.DisplayFormatters;



public class
ColumnTagAggregateSR
implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "aggregate_sr";

	@Override
	public void
	fillTableColumnInfo(
			TableColumnInfo info)
	{
		info.addCategories(new String[] {
				TableColumn.CAT_SHARING,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	public
	ColumnTagAggregateSR(
			TableColumn column)
	{
		column.setWidth(60);
		column.setRefreshInterval(TableColumn.INTERVAL_LIVE);
		column.setAlignment(TableColumn.ALIGN_TRAIL);
		column.addListeners(this);
	}

	@Override
	public void refresh(TableCell cell) {
		Tag tag = (Tag) cell.getDataSource();
		if ( tag instanceof TagFeatureRateLimit ){
			TagFeatureRateLimit rl = (TagFeatureRateLimit)tag;

			int	sr = rl.getTagAggregateShareRatio();

			if ( sr >= 0 ){

				if (!cell.setSortValue(sr) && cell.isValid()) {
					return;
				}

				if (!cell.isShown()) {
					return;
				}

				cell.setText( sr==0?"":DisplayFormatters.formatDecimal( sr/1000.0d, 3 ));
			}
		}
	}
}