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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;


public class 
NetworksIconsItem
	extends CoreTableColumnSWT
    implements TableCellRefreshListener, TableCellSWTPaintListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "networks.icons";

	private static Image[] images;
	
	static{
		ImageLoader imageLoader = ImageLoader.getInstance();

		String[] networks = AENetworkClassifier.AT_NETWORKS;

		images = new Image[networks.length];
		
		for ( int i=0;i<networks.length;i++ ){
		
			images[i] = imageLoader.getImage("net_" + networks[i] + "_b");
		}
	}
	
	public NetworksIconsItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 70, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_TRACKER,
				CAT_SWARM
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED);
	}

	@Override
	public void refresh(TableCell cell) {
		long sort = 0;
		
		DownloadManager dm = (DownloadManager)cell.getDataSource();
		if (dm != null) {
			String[] nets = dm.getDownloadState().getNetworks();

			String[] order = AENetworkClassifier.AT_NETWORKS;

			for ( int i=0; i<order.length; i++ ){
				for ( int j=0;j<nets.length;j++){
					if ( order[i] == nets[j] ){
						sort = ( sort << 4 ) + i+1;
						break;
					}
				}
			}
		}
		cell.setSortValue(sort);
	}
	
	@Override
	public void 
	cellPaint(
		GC				gc, 
		TableCellSWT	cell ) 
	{
		DownloadManager dm = (DownloadManager)cell.getDataSource();
	
		if  (dm != null ){
			
			String[] nets = dm.getDownloadState().getNetworks();

			Rectangle bounds = cell.getBounds();
			
			String[] order = AENetworkClassifier.AT_NETWORKS;

			List<Image>	list = new ArrayList<>();
			
			int pad = -10;
			
			int total_width = -pad;
			
				// these icons have a lot of space around them...
			
			for ( int i=0;i<order.length;i++){
				for ( String str: nets ){
					if ( str == order[i] ){
						Image img = images[i];
						list.add( img );						
						
						total_width += img.getBounds().width+pad;
					}
				}
			}
			
			TableColumn tableColumn = cell.getTableColumn();
			
			if (tableColumn != null && tableColumn.getPreferredWidth() < total_width) {
				
				tableColumn.setPreferredWidth(total_width);
			}
			
			int x = bounds.x;
			
			if ( bounds.width > total_width ){
				
				x += (bounds.width - total_width ) / 2;
			}

			for ( Image img: list ){
				
				gc.drawImage(img, x, bounds.y );
				
				x += img.getBounds().width + pad;
			}
		}
	}
}
