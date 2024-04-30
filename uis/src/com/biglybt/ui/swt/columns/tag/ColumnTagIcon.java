/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.columns.tag;

import java.io.File;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;

import com.biglybt.core.tag.Tag;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

public class ColumnTagIcon
	implements TableCellRefreshListener, TableCellSWTPaintListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "tag.icon";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnTagIcon(TableColumn column) {
		column.setWidth(30);
		column.addListeners(this);
	}

	@Override
	public void refresh(TableCell cell) {
		Tag tag = (Tag) cell.getDataSource();
		if (tag == null) {
			return;
		}
		String file = tag.getImageFile();

		if (file == null ){
			file = "";
		}

		if (!cell.setSortValue(file) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}
	}

	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		Tag tag = (Tag) cell.getDataSource();
		if (tag == null) {
			return;
		}
		String file = tag.getImageFile();

		if ( file != null ){
			try{
				Point size = cell.getSize();
				
				size.x -= 2;
				size.y -= 2;
				
				ImageLoader.getInstance().getFileImage(
						 new File( file ), 
						size,
						(image, key, returnedImmediately) -> {

							if ( image != null && returnedImmediately ){

								if ( !gc.isDisposed()){
								
									Utils.drawImageCenterScaleDown(gc, image, cell.getBounds());
								}

								ImageLoader.getInstance().releaseImage( key );
							}
						});
				
			}catch( Throwable e ){
				
			}
		}
	}
}
