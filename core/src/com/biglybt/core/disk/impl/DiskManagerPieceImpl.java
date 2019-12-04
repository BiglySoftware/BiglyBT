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

package com.biglybt.core.disk.impl;

/**
 * @author parg
 * @author MjrTom
 *			2005/Oct/08: startPriority/resumePriority handling and minor clock fixes
 *			2006/Jan/02: refactoring, change booleans to statusFlags
 */

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;

public class DiskManagerPieceImpl
	implements DiskManagerPiece
{
    //private static final LogIDs LOGID = LogIDs.PIECES;

	private static final byte	PIECE_STATUS_NEEDED		= 0x01;	//want to have the piece
	private static final byte	PIECE_STATUS_WRITTEN	= 0x02;	//piece fully written to storage
	private static final byte	PIECE_STATUS_CHECKING	= 0x04;	//piece is being hash checked

	private static final byte	PIECE_STATUS2_MERGE_READ	= 0x01;	
	private static final byte	PIECE_STATUS2_MERGE_WRITE	= 0x02;	

	
    private static final byte PIECE_STATUS_MASK_DOWNLOADABLE	=
    	PIECE_STATUS_CHECKING | PIECE_STATUS_WRITTEN | PIECE_STATUS_NEEDED;

    										// 0x65;    // Needed IS once again included in this

	private static final byte PIECE_STATUS_MASK_NEEDS_CHECK 	= PIECE_STATUS_CHECKING | PIECE_STATUS_WRITTEN;

    //private static boolean statusTested =false;

    private final DiskManagerHelper	diskManager;
	private final int				pieceNumber;

		/** the number of blocks in this piece: can be short as this gives up to .5GB piece sizes with 16K blocks */

	private final short				nbBlocks;

	// to save memory the "written" field is only maintained for pieces that are
	// downloading. A value of "null" means that either the piece hasn't started
	// download or that it is complete.
	// access to "written" is single-threaded (by the peer manager) apart from when
	// the disk manager is saving resume data.
	// actually this is not longer strictly true, as setDone is called asynchronously
	// however, this issue can be worked around by working on a reference to the written data
	// as problems only occur when switching from all-written to done=true, both of which signify
	// the same state of affairs.

	protected volatile boolean[]	written;

    private byte         statusFlags;
    private byte         statusFlags2;

	/** it's *very* important to accurately maintain the "done" state of a piece. Currently the statusFlags
	 * are updated in a non-thread-safe manner so a 'done' field is maintained separately.  Synchronizing
	 * access to statusFlags or done would cause a tremendous performance hit.
	 */
    private short		read_count;

    private boolean		done;

	public DiskManagerPieceImpl(final DiskManagerHelper _disk_manager, final int pieceIndex, int length )
	{
		diskManager =_disk_manager;
		pieceNumber = pieceIndex;

		nbBlocks =(short)((length +DiskManager.BLOCK_SIZE -1) /DiskManager.BLOCK_SIZE);

		statusFlags = PIECE_STATUS_NEEDED;
	}

	@Override
	public DiskManager getManager()
	{
		return diskManager;
	}

	@Override
	public int getPieceNumber()
	{
		return pieceNumber;
	}

	/**
	 * @return int number of bytes in the piece
	 */
	@Override
	public int getLength()
	{
		return( diskManager.getPieceLength( pieceNumber ));
	}

	@Override
	public int getNbBlocks()
	{
		return nbBlocks;
	}

	@Override
	public short
	getReadCount()
	{
		return( read_count );
	}

	@Override
	public void
	setReadCount(
		short	c )
	{
		read_count	= c;
	}

    @Override
    public int getBlockSize(final int blockNumber)
    {
        if ( blockNumber == nbBlocks -1 ){

        	int	len = getLength() % DiskManager.BLOCK_SIZE;

        	if ( len != 0 ){

        		return( len );
        	}
        }

        return DiskManager.BLOCK_SIZE;
    }

    @Override
    public boolean
    isSkipped()
    {
		final DMPieceList pieceList =diskManager.getPieceList(pieceNumber);
		for (int i =0; i <pieceList.size(); i++){
			final DiskManagerFileInfo file =pieceList.get(i).getFile();
			if ( file == null ){
				return( false );	// can be null during diskmanager startup
			}
			if ( !file.isSkipped()){
				return( false );
			}
		}
		return( true );
    }

	@Override
	public boolean isNeeded()
	{
		return (statusFlags &PIECE_STATUS_NEEDED) !=0;
	}

	@Override
	public boolean calcNeeded()
	{
		boolean filesNeeded =false;
		final DMPieceList pieceList =diskManager.getPieceList(pieceNumber);
		for (int i =0; i <pieceList.size(); i++)
		{
			final DiskManagerFileInfo file =pieceList.get(i).getFile();
			final long fileLength =file.getLength();
			filesNeeded |=fileLength >0 &&file.getDownloaded() <fileLength &&!file.isSkipped();
		}
		if (filesNeeded)
		{
			statusFlags |=PIECE_STATUS_NEEDED;
			return true;
		}
		statusFlags &=~PIECE_STATUS_NEEDED;
		return false;
	}

	@Override
	public boolean
	spansFiles()
	{
		DMPieceList pieceList = diskManager.getPieceList(pieceNumber);

		return( pieceList.size() > 1 );
	}

	public DMPieceList
	getPieceList()
	{
		return( diskManager.getPieceList(pieceNumber));
	}
	
	@Override
	public void clearNeeded()
	{
		statusFlags &=~PIECE_STATUS_NEEDED;
	}

	@Override
	public void setNeeded()
	{
		statusFlags |=PIECE_STATUS_NEEDED;
	}

	@Override
	public void setNeeded(boolean b)
	{
		if (b)
			statusFlags |=PIECE_STATUS_NEEDED;
		else
			statusFlags &=~PIECE_STATUS_NEEDED;
	}

	@Override
	public boolean isWritten()
	{
		return (statusFlags &PIECE_STATUS_WRITTEN) !=0;
	}


	/** written[] can be null, in which case if the piece is Done,
	*  all blocks are complete otherwise no blocks are complete
	*/
	@Override
	public boolean[] getWritten()
	{
		return written;
	}

	@Override
	public boolean isWritten(final int blockNumber)
	{
		if (done)
			return true;
		final boolean[] writtenRef =written;
		if (writtenRef ==null)
			return false;
		return writtenRef[blockNumber];
	}

	@Override
	public int getNbWritten()
	{
		if (done)
			return nbBlocks;
		final boolean[] writtenRef =written;
		if (writtenRef ==null)
			return 0;
		int res =0;
		for (int i =0; i <nbBlocks; i++ )
		{
			if (writtenRef[i])
				res++;
		}
		return res;
	}

	@Override
	public void setWritten(final int blockNumber)
	{
		if (written ==null)
			written =new boolean[nbBlocks];
		final boolean[] written_ref =written;

		written_ref[blockNumber] =true;
		for (int i =0; i <nbBlocks; i++)
		{
			if (!written_ref[i])
				return;
		}
		statusFlags |=PIECE_STATUS_WRITTEN;
	}

	@Override
	public void clearWritten(final int blockNumber)
	{
		boolean[] written_ref =written;

		if ( written_ref != null ){
			written_ref[blockNumber] = false;
		
			statusFlags &= ~PIECE_STATUS_WRITTEN;
		}
	}
	
	@Override
	public boolean isChecking()
	{
		return (statusFlags &PIECE_STATUS_CHECKING) !=0;
	}

	@Override
	public void setChecking()
	{
		statusFlags |=PIECE_STATUS_CHECKING;
	}

    @Override
    public boolean isNeedsCheck()
    {
    	return !done &&(statusFlags &PIECE_STATUS_MASK_NEEDS_CHECK) ==PIECE_STATUS_WRITTEN;
    }


    // this cannot be implemented the same as others could be
	// because the true state of Done is only determined by
	// having gone through setDoneSupport()
	@Override
	public boolean calcDone()
	{
		return done;
	}

	@Override
	public boolean isDone()
	{
		return done;
	}

	@Override
	public void setDone(boolean b)
	{
		// we delegate this operation to the disk manager so it can synchronise the activity
        if (b !=done)
        {
            diskManager.setPieceDone(this, b);
        }
	}

	/** this is ONLY used by the disk manager to update the done state while synchronized
	 *i.e. don't use it else where!
	 * @param b
	 */

	public void setDoneSupport(final boolean b)
	{
        done =b;
        if (done)
            written =null;
	}

	@Override
	public void setDownloadable()
	{
		setDone(false);
		statusFlags &=~(PIECE_STATUS_MASK_DOWNLOADABLE);
		calcNeeded();	// Needed wouldn't have been calced before if couldn't download more
	}

	@Override
	public boolean isDownloadable()
	{
		return !done &&(statusFlags &PIECE_STATUS_MASK_DOWNLOADABLE) == PIECE_STATUS_NEEDED;
	}

	/**
	 * @return true if the piece is Needed and not Done
	 */
	@Override
	public boolean isInteresting()
	{
		return !done &&(statusFlags &PIECE_STATUS_NEEDED) != 0;
	}
	
	@Override
	public int getRemaining(){
		if ( done || !isNeeded()){
			return(0);
		}
		boolean[] w = written;
		if ( w == null ){
			return( getLength());
		}
		int size = 0;
		for ( int i=0;i<w.length;i++){
			if ( !w[i] ){
				size += getBlockSize( i );
			}
		}
		return( size );
	}

	@Override
	public void reset()
	{
		setDownloadable();
		written =null;
	}

	@Override
	public void reDownloadBlock(int blockNumber)
	{
		final boolean[] written_ref = written;
		if (written_ref !=null)
		{
			written_ref[blockNumber] =false;
			setDownloadable();
		}
	}

	public void
	setMergeRead()
	{
		statusFlags2 |= PIECE_STATUS2_MERGE_READ;
	}

	public boolean
	isMergeRead()
	{
		return((statusFlags2 & PIECE_STATUS2_MERGE_READ ) != 0 );
	}

	public void
	setMergeWrite()
	{
		statusFlags2 |= PIECE_STATUS2_MERGE_WRITE;
	}

	public boolean
	isMergeWrite()
	{
		return((statusFlags2 & PIECE_STATUS2_MERGE_WRITE ) != 0 );
	}
	    

    /*
    public static final void testStatus()
    {
        if (statusTested)
            return;

        statusTested =true;
        int originalStatus =statusFlags;

        for (int i =0; i <0x100; i++)
        {
            statusFlags =i;
            Logger.log(new LogEvent(this, LOGID, LogEvent.LT_INFORMATION,
                "Done:" +isDone()
                +"  Checking:" +isChecking()
                +"  Written:" +isWritten()
                +"  Downloaded:" +isDownloaded()
                +"  Requested:" +isRequested()
                +"  Needed:" +isNeeded()
                +"  Interesting:" +isInteresting()
                +"  Requestable:" +isRequestable()
                +"  EGMActive:" +isEGMActive()
                +"  EGMIgnored:" +isEGMIgnored()
            ));
        }
        statusFlags =originalStatus;
    }
    */

	@Override
	public String
	getString()
	{
		String	text = "";

		text += ( isNeeded()?"needed,":"" );
		text += ( isDone()?"done,":"" );

		if ( !isDone()){
			text += ( isDownloadable()?"downable,":"" );
			text += ( isWritten()?"written":("written " + getNbWritten())) + ",";
			text += ( isChecking()?"checking":"" );
		}

		if ( text.endsWith(",")){
			text = text.substring(0,text.length()-1);
		}
		return( text );
	}
}
