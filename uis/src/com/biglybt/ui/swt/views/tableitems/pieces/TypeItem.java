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

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.PiecesView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 * @author MjrTom
 *			2005/Oct/08: fast/slow changed to graduated, so >2 = fast now
 */

public class TypeItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public TypeItem(String table_id) {
    super("type", ALIGN_TRAIL, POSITION_LAST, 80, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

  @Override
  public void refresh(TableCell cell) {
    PEPiece piece = (PEPiece)cell.getDataSource();
    
    boolean is_uploading = piece instanceof PiecesView.PEPieceUploading;

	if ( is_uploading ){
		cell.setText( MessageText.getString( "label.uploading" ));
		return;
	}
	
    long value = (piece == null) ? 0 : piece.getSpeed() >2 ? 1 : 0;

    if( !cell.setSortValue( value ) && cell.isValid() ) {
      return;
    }

    cell.setText(MessageText.getString("PiecesView.typeItem." + value));
  }
}