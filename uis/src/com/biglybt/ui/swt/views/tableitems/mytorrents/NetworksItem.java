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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


/** Display Category torrent belongs to.
 *
 * @author TuxPaper
 */
public class NetworksItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "networks";

	/** Default Constructor */
  public NetworksItem(String sTableID) {
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
    String	networks = "";
    long	sort = 0;
    
    DownloadManager dm = (DownloadManager)cell.getDataSource();
    if (dm != null) {
		String[] nets = dm.getDownloadState().getNetworks();

		String[] order = AENetworkClassifier.AT_NETWORKS;

		for ( int i=0; i<order.length; i++ ){
			for ( int j=0;j<nets.length;j++){
				if ( order[i] == nets[j] ){
					sort = ( sort << 4 ) + i+1;
					networks += (networks.isEmpty()?"":",") + order[i];
					break;
				}
			}
		}
    }
    cell.setSortValue(sort);
    cell.setText(networks);
  }
}
