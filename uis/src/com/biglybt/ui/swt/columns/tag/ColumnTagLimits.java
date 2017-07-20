/*
 * Copyright (C) 2013 Azureus Software, Inc. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.columns.tag;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureLimits;

public class ColumnTagLimits
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "tag.limit";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_SETTINGS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	/** Default Constructor */
	public ColumnTagLimits(TableColumn column) {
		column.setWidth(200);
		column.addListeners(this);
	}

	@Override
	public void refresh(TableCell cell) {
		Tag tag = (Tag) cell.getDataSource();
		String tag_limits = "";
		if (tag instanceof TagFeatureLimits ){

			TagFeatureLimits tfl = (TagFeatureLimits)tag;

			int max = tfl.getMaximumTaggables();

			if ( max > 0 ){
				tag_limits = String.valueOf( max );

				String policy = null;

				switch( tfl.getRemovalStrategy()){
					case TagFeatureLimits.RS_NONE:
						policy = "label.none.assigned";
						break;
					case TagFeatureLimits.RS_ARCHIVE:
						policy = "MyTorrentsView.menu.archive";
						break;
					case TagFeatureLimits.RS_REMOVE_FROM_LIBRARY:
						policy = "Button.deleteContent.fromLibrary";
						break;
					case TagFeatureLimits.RS_DELETE_FROM_COMPUTER:
						policy = "Button.deleteContent.fromComputer";
						break;
					case TagFeatureLimits.RS_MOVE_TO_OLD_TAG:
						policy = "label.move.to.old.tag";
						break;
				}

				if ( policy != null ){
					tag_limits += "; " + MessageText.getString( policy );
				}

				String order = null;
				switch( tfl.getOrdering()){
					case TagFeatureLimits.OP_ADDED_TO_VUZE:
						order = "label.time.added.to.vuze";
						break;
					case TagFeatureLimits.OP_ADED_TO_TAG:
						order = "label.time.added.to.tag";
						break;
				}

				if ( order != null ){
					tag_limits += "; " + MessageText.getString( order );
				}
			}
		}

		if (!cell.setSortValue(tag_limits) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		cell.setText(tag_limits);
	}
}
