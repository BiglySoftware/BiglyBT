

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.TableViewCreator;
import com.biglybt.ui.swt.views.MyTorrentsView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;



public class TagSortItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;
	
	public static final String COLUMN_ID = "tag_sort";
	
	public 
	TagSortItem(
		String sTableID) 
	{
		super( DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
		
		setRefreshInterval( INTERVAL_LIVE );
	}


	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_SETTINGS
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void 
	refresh(
		TableCell cell )
	{
		long sort = -1;
		
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		TableView<?> tv =  cell.getTableRow().getView();
		TableViewCreator tvc =  tv==null?null:cell.getTableRow().getView().getTableViewCreator();
		if ( dm != null && tvc instanceof MyTorrentsView ){
			Map<Long,Object[]> tag_sort = (Map<Long,Object[]>)dm.getDownloadState().getTransientAttribute( DownloadManagerState.AT_TRANSIENT_TAG_SORT );
			if ( tag_sort != null ){
				MyTorrentsView mtv = (MyTorrentsView)tvc;
				Tag[] tags = mtv.getCurrentTags();
				if ( tags != null && tags.length == 1 ){
					Object[] entry = tag_sort.get(tags[0].getTagUID());
					if ( entry != null ){
						sort = (Long)entry[1];
					}
				}
			}
		}
		
		if ( !cell.setSortValue( sort) && cell.isValid()){
			
			return;
		}
		
		cell.setSortValue( sort );
		
		cell.setText( sort==-1?"":(sort==Long.MAX_VALUE?Constants.INFINITY_STRING:String.valueOf( sort )));
	}
}
