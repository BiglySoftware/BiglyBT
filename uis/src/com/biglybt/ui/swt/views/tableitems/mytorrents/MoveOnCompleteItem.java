/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.io.File;
import java.util.List;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureFileLocation;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

public class MoveOnCompleteItem
extends CoreTableColumnSWT
implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "moveoncomplete";

	public MoveOnCompleteItem(String sTableID)
	{
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 120, sTableID);

		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_CONTENT
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void refresh(TableCell cell) {
		DownloadManager dm = (DownloadManager)cell.getDataSource();

		String text;
		String tt		= null;
		
		if ( dm == null ){
			
			text = null;
			
		}else{
		 
			text = dm.getDownloadState().getAttribute( DownloadManagerState.AT_MOVE_ON_COMPLETE_DIR );
			
			if ( text == null ){
								
				List<Tag> moc_tags = TagUtils.getActiveMoveOnCompleteTags( dm, true, (str)->{});
				
				String str_text = "";
				String str_tt	= "";
				
				if ( !moc_tags.isEmpty()){
					
					for ( Tag tag: moc_tags ){
						
						TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;
						
						File file = fl.getTagMoveOnCompleteFolder();
						
						if ( file != null ){
							
							str_text += (str_text.isEmpty()?"":", ") + 
										(moc_tags.size()==1?"":(tag.getTagName(true) + "->" )) + 
										file.getAbsolutePath();
							
							str_tt += 	(str_tt.isEmpty()?"":", ") + 
										tag.getTagName(true) + "->" + 
										file.getAbsolutePath();
						}
					}
				}
				
				if ( !str_text.isEmpty()){
					
					text	= "(" + str_text + ")";
					tt		= str_tt;
				}
			}
		}

		if ( text == null ){
			
			text = "";
		}
		
		cell.setToolTip(tt);
		
		if ( !cell.setSortValue(text) && cell.isValid()){
			return;
		}
		
		cell.setText( text );
	}
}
