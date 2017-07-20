/*
 * File    : AvailabilityItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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

package com.biglybt.ui.swt.views.tableitems.files;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.peer.PEPeerManager;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;



/** Availability/"Seeing Copies" Column
 *
 * @author TuxPaper
 */
public class FileAvailabilityItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  // If you want more decimals, just add a zero
  private static final String zeros = "0000";
  // # decimal places == numZeros - 1
  private static final int numZeros = zeros.length();

  private int iTimesBy;

  /** Default Constructor */
  public FileAvailabilityItem(){
	super("availability", ALIGN_LEAD, POSITION_INVISIBLE, 60, TableManager.TABLE_TORRENT_FILES);
    setRefreshInterval(INTERVAL_LIVE);
    setMinWidthAuto(true);

    iTimesBy = 1;
    for (int i = 1; i < numZeros; i++)
      iTimesBy *= 10;
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SWARM,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  @Override
  public void refresh(TableCell cell) {
    String sText = "";
    DiskManagerFileInfo file = (DiskManagerFileInfo)cell.getDataSource();
    if (file == null)
      return;

    if ( file.getLength() == 0 ){
    	sText = "-";
    	cell.setSortValue(Long.MAX_VALUE);
    }else{
	    PEPeerManager pm = file.getDownloadManager().getPeerManager();
	    if (pm != null) {
	      float f = pm.getMinAvailability( file.getIndex());
	      if (!cell.setSortValue((long)(f * 1000)) && cell.isValid())
	        return;

	        sText = String.valueOf((int)(f * iTimesBy));
	        if (numZeros - sText.length() > 0)
	          sText = zeros.substring(0, numZeros - sText.length()) + sText;
	        sText = sText.substring(0, sText.length() - numZeros + 1) + "." +
	                sText.substring(sText.length() - numZeros + 1);

	    } else {
	      cell.setSortValue(0);
	    }
    }
    cell.setText(sText);
  }
}
