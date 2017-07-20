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

package com.biglybt.ui.swt.views.tableitems.pieces;

import java.util.*;

import com.biglybt.core.peer.PEPiece;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class WritersItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public WritersItem() {
    super("writers", ALIGN_LEAD, POSITION_INVISIBLE, 80, TableManager.TABLE_TORRENT_PIECES);
    setObfuscation(true);
    setRefreshInterval(4);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SWARM,
		});
	}

  @Override
  public void refresh(TableCell cell) {
    PEPiece piece = (PEPiece)cell.getDataSource();
    String[] core_writers = piece.getWriters();
    String[] my_writers = new String[core_writers.length];
    int writer_count = 0;
    Map map = new HashMap();

    for(int i = 0 ; ; ) {
	String this_writer = null;

	int start;
	for (start = i ; start < core_writers.length ; start++ ) {
	    this_writer = core_writers[start];
	    if (this_writer != null)
		break;
	}
	if (this_writer == null)
	    break;

	int end;
	for (end = start + 1; end < core_writers.length; end++) {
	    if (! this_writer.equals(core_writers[end]))
		break;
	}

	StringBuffer pieces = (StringBuffer) map.get(this_writer);
	if (pieces == null) {
	    pieces = new StringBuffer();
	    map.put(this_writer, pieces);
	    my_writers[writer_count++] = this_writer;
	} else {
	    pieces.append(',');
	}

	pieces.append(start);
	if (end-1 > start)
	    pieces.append('-').append(end-1);

	i=end;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0 ; i < writer_count ; i++) {
	String writer = my_writers[i];
	StringBuffer pieces = (StringBuffer) map.get(writer);
	if (i > 0)
	    sb.append(';');
	sb.append(writer).append('[').append(pieces).append(']');
    }

    String value = sb.toString();
    if( !cell.setSortValue( value ) && cell.isValid() ) {
      return;
    }

    cell.setText(value);
  }
}