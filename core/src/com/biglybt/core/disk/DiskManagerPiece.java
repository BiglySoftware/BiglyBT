/*
 * Created on 08-Oct-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.core.disk;

import com.biglybt.core.disk.impl.piecemapper.DMPieceList;

/**
 * Represents a DiskManager Piece
 *
 * @author parg
 * @author MjrTom
 *			2005/Oct/08: priority handling
 *			2006/Jan/2: refactoring, mostly to base Piece interface
 */

public interface
DiskManagerPiece
{
	public DiskManager	getManager();

	public int			getLength();
	public int         	getPieceNumber();
	public int			getNbBlocks();
	public int			getBlockSize( int block_index );

	public short		getReadCount();
	public void			setReadCount(short c);

	public boolean		calcNeeded();
	public void			clearNeeded();

	/** @return true if any file the piece covers is neither Do Not Download nor Delete.
	 * This is not a real-time indicator.  Also, the results are not reliable for pieces that are Done.
	 * Use calcNeeded() for guaranteed correct and up to date results
	 * @see calcNeeded(), clearNeeded(), setNeeded(), setNeeded(boolean)
	 */
	public boolean		isNeeded();
	public void			setNeeded();
	public void			setNeeded(boolean b);

	// a piece is Written if data has been written to storage for every block (without concern for if it's checked)
    public boolean      isWritten();
	public int			getNbWritten();
	public boolean[] 	getWritten();

	/**
	 * @param blockNumber int
	 * @return true if the given blockNumber has already been written to disk
	 */
	public boolean		isWritten(int blockNumber);
	public void			setWritten(int blockNumber);
	public void			clearWritten( int blockNumber );

	// a piece is Checking if a hash check has been setup and the hash check hasn't finalized the result yet
	// this flag is asynch, so be careful, and it's also transitory (comapared to most of the others being kinda sticky)

	public void			setChecking();
    public boolean 		isChecking();

    public boolean		isNeedsCheck();

    public boolean		spansFiles();

    public DMPieceList	getPieceList();
    
	public boolean		calcDone();
	/** @return true when the hash check has passed and the DiskManager has asynchronously updated the Done status.
	 * There is nothing further to be done regarding downloading for pieces that are Done.
	 */
	public boolean		isDone();
	public void			setDone(boolean b);

    /**
     * @return true if a piece is Needed and not Done
     */
    public boolean      isInteresting();

    /** This must not be used to qualify pieces in End Game Mode.
	 * @return true if a piece is Needed but is not fully; Requested, Downloaded, Written, Checking, or Done.
	 */
	public boolean		isDownloadable();
	public void 		setDownloadable();

     /**
     * returns true if all the files that the piece spans are skipped
     * @return
     */
    public boolean	    isSkipped();

    public int			getRemaining();
    
    public void 		reDownloadBlock(int blockNumber);
    public void			reset();

    public void
    setMergeRead();
    
    public boolean
    isMergeRead();
    
    public void
    setMergeWrite();
    
    public boolean
    isMergeWrite();
    
    public String
    getString();
}
