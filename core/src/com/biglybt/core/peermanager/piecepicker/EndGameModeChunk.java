/*
 * File    : EndGameModeChunk.java
 * Created : 4 d�c. 2003}
 * By      : Olivier
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
package com.biglybt.core.peermanager.piecepicker;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.peer.PEPiece;

/**
 * @author Olivier
 * @author MjrTom
 * 			2006/Jan/06 Refactoring
 */
public class EndGameModeChunk
{
	private final int	pieceNumber;
	private final int	blockNumber;

	private final int	offset;
	private final int	length;

	private int request_count = 1;	// when we enter egm everything is already requested
	
	public EndGameModeChunk(PEPiece pePiece, int blockNum)
	{
		//this.piece = piece;
		pieceNumber =pePiece.getPieceNumber();
		blockNumber =blockNum;
		length =pePiece.getBlockSize(blockNumber);
		offset =blockNumber *DiskManager.BLOCK_SIZE;
	}

	public boolean equals(int pieceNum, int os)
    {
        return ((pieceNumber ==pieceNum) &&(this.offset ==os));
    }

	/**
	 * @return int Returns the pieceNumber.
	 */
	public int getPieceNumber()
	{
		return pieceNumber;
	}

	/**
	 * @return int Returns the blockNumber.
	 */
	public int getBlockNumber()
	{
		return blockNumber;
	}

	public int getOffset()
	{
		return offset;
	}

	public int getLength()
	{
		return length;
	}
	
	public void
	requested()
	{
		request_count++;
	}
	
	public int
	getRequestCount()
	{
		return( request_count );
	}
}
