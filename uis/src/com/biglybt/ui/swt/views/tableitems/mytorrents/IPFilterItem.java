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
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

import com.biglybt.ui.swt.imageloader.ImageLoader;

/** Display Category torrent belongs to.
 *
 * @author TuxPaper
 */
public class IPFilterItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	private static IpFilter ipfilter = null;

	public static final Class DATASOURCE_TYPE = Download.class;

	private static UISWTGraphic tick_icon;

	private static UISWTGraphic cross_icon;

  public static final String COLUMN_ID = "ipfilter";

	/** Default Constructor */
  public IPFilterItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 100, sTableID);
    setRefreshInterval(INTERVAL_LIVE);
	initializeAsGraphic(POSITION_INVISIBLE, 100);
	setMinWidth(20);
	
	tick_icon = new UISWTGraphicImpl(ImageLoader.getInstance().getImage("tick_mark_s"));
	cross_icon = new UISWTGraphicImpl(ImageLoader.getInstance().getImage("cross_mark_s"));

  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONNECTION,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE );
	}

  @Override
  public void refresh(TableCell cell) {
    //String state = "";

    UISWTGraphic	icon 	= null;
    int				sort	= 0;

    if (ipfilter == null) {
    	ipfilter = IpFilterManagerFactory.getSingleton().getIPFilter();
    }

    if( ipfilter.isEnabled()){
	    DownloadManager dm = (DownloadManager)cell.getDataSource();
	    if (dm != null) {
	       boolean excluded = dm.getDownloadState().getFlag( DownloadManagerState.FLAG_DISABLE_IP_FILTER );

	       if ( excluded ){

	    	   icon = cross_icon;
	    	   sort	=1;
	    	   //state = "\u2718";

	       }else{

	    	   icon = tick_icon;
	    	   sort	= 2;
	    	  // state = "\u2714";
	       }
	    }
    }

    cell.setSortValue( sort );

    if ( cell.getGraphic() != icon ){

    	cell.setGraphic( icon );
    }
  }
}
