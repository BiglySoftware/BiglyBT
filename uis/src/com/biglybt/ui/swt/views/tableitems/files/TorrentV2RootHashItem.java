/*
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

import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import java.math.BigInteger;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.ByteFormatter;


public class TorrentV2RootHashItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{


  /** Default Constructor */
  public TorrentV2RootHashItem() {
    super("torrentroothash", ALIGN_LEAD, POSITION_INVISIBLE, 200, TableManager.TABLE_TORRENT_FILES);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  @Override
  public void refresh(TableCell cell) {
    DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();
    TOTorrentFile torrentFile = fileInfo==null?null:fileInfo.getTorrentFile();
    String text;
    BigInteger sort;
    if ( torrentFile != null ){
    	byte[] hash = torrentFile.getRootHash();
    	if ( hash == null ){
    		text = "";
    	}else{
    		text = ByteFormatter.encodeString( hash );
    	}
		sort = hash==null?new BigInteger(-1,new byte[0]):new BigInteger(1,hash);

    }else{
    	text = "";
    	sort = new BigInteger(-1,new byte[0]);
    }
    cell.setSortValue(sort);
    cell.setText(text);
  }
}
