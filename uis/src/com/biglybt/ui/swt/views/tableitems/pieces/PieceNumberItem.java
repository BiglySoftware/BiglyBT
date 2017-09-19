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

import com.biglybt.core.peer.PEPiece;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 1.0.0.0
 */
public class PieceNumberItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public PieceNumberItem() {
    super("#", ALIGN_TRAIL, POSITION_LAST, 50, TableManager.TABLE_TORRENT_PIECES);
  }

  @Override
  public void refresh(TableCell cell) {
    PEPiece piece = (PEPiece)cell.getDataSource();
    long value = (piece == null) ? 0 : piece.getPieceNumber();

    if( !cell.setSortValue( value ) && cell.isValid() ) {
      return;
    }

    cell.setText(""+value);
  }
}
