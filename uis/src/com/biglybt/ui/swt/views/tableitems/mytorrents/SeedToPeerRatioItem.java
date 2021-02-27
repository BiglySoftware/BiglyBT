/*
 * Created : 11 nov. 2004
 * By      : Alon Rohter
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
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.DisplayFormatters;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;



public class SeedToPeerRatioItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;


  public static final String COLUMN_ID = "seed_to_peer_ratio";

	public SeedToPeerRatioItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SWARM,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED);
	}

  @Override
  public void refresh(TableCell cell) {
    float ratio = -1;

    DownloadManager dm = (DownloadManager)cell.getDataSource();

    Download download = null;

    if ( cell instanceof TableCellCore && dm != null ){
    	
    	TableRowCore row = ((TableCellCore)cell).getTableRowCore();
    	
    	if ( row != null ){
    	
    		download = (Download)row.getDataSource( false );
    	}
    }

    if( dm != null && download != null) {
      DownloadScrapeResult response = download.getAggregatedScrapeResult();
      int seeds;
      int peers;

      if( response.getResponseType() == DownloadScrapeResult.RT_SUCCESS) {
        seeds = Math.max( dm.getNbSeeds(), response.getSeedCount());

        int trackerPeerCount = response.getNonSeedCount();
        peers = dm.getNbPeers();
        if (peers == 0 || trackerPeerCount > peers) {
        	if (trackerPeerCount <= 0) {
        		//peers = dm.getActivationCount(); crap info
        	} else {
        		peers = trackerPeerCount;
        	}
        }
      }
      else {
        seeds = dm.getNbSeeds();
        peers = dm.getNbPeers();
      }

      if (peers < 0 || seeds < 0) {
    	  ratio = 0;
      } else {
    	  if (peers == 0) {
    		  if (seeds == 0){
    			  ratio = 0;
    		  }else{
    			  ratio = Float.POSITIVE_INFINITY;
    		  }
    	  } else {
    		  ratio = (float)seeds / peers;
    	  }
      }
    }

    if( !cell.setSortValue( ratio ) && cell.isValid() ) {
      return;
    }

    if (ratio == -1) {
    	cell.setText("");
    } else if (ratio == 0) {
    	cell.setText("??");
    } else {
    	cell.setText(DisplayFormatters.formatDecimal(ratio, 3));
    }
  }

}
