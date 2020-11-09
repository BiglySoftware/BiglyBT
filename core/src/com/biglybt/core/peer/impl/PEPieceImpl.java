/*
 * File    : PEPieceImpl.java
 * Created : 15-Oct-2003
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

package com.biglybt.core.peer.impl;

/**
 * @author parg
 * @author MjrTom
 *			2005/Oct/08: numerous changes for new piece-picking
 *			2006/Jan/02: refactoring piece picking to elsewhere, and consolidations
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.SystemTime;


public class PEPieceImpl
    implements PEPiece
{
	private static final LogIDs LOGID = LogIDs.PIECES;

	private final DiskManagerPiece	dmPiece;
	private final PiecePicker		piecePicker;

	private final int       nbBlocks;       // number of blocks in this piece
    private long            creationTime;


	private final String[]	requested;
	private boolean			fully_requested;

	private final boolean[]	downloaded;
	private boolean			fully_downloaded;
	private long        	time_last_download;

	private final String[] 	writers;
	private final List 			writes;

	private String			reservedBy;	// using address for when they send bad/disconnect/reconnect

	//In end game mode, this limitation isn't used
    private int             speed;      //slower peers dont slow down fast pieces too much

    private int             resumePriority;

    private Object			real_time_data;

	// experimental class level lock
	protected static final AEMonitor 	class_mon	= new AEMonitor( "PEPiece:class");

    /** piece for tracking partially downloaded pieces
     * @param _manager the PEPeerManager
     * @param _dm_piece the backing dmPiece
     * @param _pieceSpeed the speed threshold for potential new requesters
     */
	public PEPieceImpl(
		PiecePicker 		_picker,
		DiskManagerPiece	_dm_piece,
        int                 _pieceSpeed)
	{
        creationTime =SystemTime.getCurrentTime();
        piecePicker =_picker;
		dmPiece =_dm_piece;
        speed =_pieceSpeed;

		nbBlocks =dmPiece.getNbBlocks();

		requested =new String[nbBlocks];

		fixupPadFile();

		final boolean[] written =dmPiece.getWritten();
		
		if ( written == null ){
			
			downloaded = new boolean[nbBlocks];
		}else{
			downloaded =(boolean[])written.clone();
		}
		
        writers =new String[nbBlocks];
		writes =new ArrayList(0);
	}

    @Override
    public DiskManagerPiece getDMPiece()
    {
        return dmPiece;
    }

    @Override
    public long getCreationTime()
    {
        final long now =SystemTime.getCurrentTime();
        if (now >=creationTime &&creationTime >0){
            return creationTime;
        }
        creationTime =now;
        return now;
    }

    @Override
    public long getTimeSinceLastActivity()
    {
        final long now =SystemTime.getCurrentTime();
        final long lastWriteTime =getLastDownloadTime(now);
        if (lastWriteTime >0){
            return now -lastWriteTime;
        }
        final long lastCreateTime = creationTime;
        if (lastCreateTime > 0 && now >=lastCreateTime ){
            return now -lastCreateTime;
        }
        creationTime = now;
        return 0;
    }

	@Override
	public long getLastDownloadTime(final long now)
	{
		if (time_last_download <=now)
			return time_last_download;
		return time_last_download =now;
	}

	/** Tells if a block has been requested
	 * @param blockNumber the block in question
	 * @return true if the block is Requested already
	 */
	@Override
	public boolean isRequested(int blockNumber)
	{
		return requested[blockNumber] !=null;
	}

	/** Tells if a block has been downloaded
	 * @param blockNumber the block in question
	 * @return true if the block is downloaded already
	 */
	@Override
	public boolean isDownloaded(int blockNumber)
	{
		return downloaded[blockNumber];
	}

	/** This flags the block at the given offset as having been downloaded
     * If all blocks are now downloaed, sets the dmPiece as downloaded
	 * @param blockNumber
	 */
	@Override
	public void setDownloaded(int offset)
	{
		time_last_download =SystemTime.getCurrentTime();
		downloaded[offset /DiskManager.BLOCK_SIZE] =true;
        for (int i =0; i <nbBlocks; i++)
        {
            if (!downloaded[i])
                return;
        }

        fully_downloaded	= true;
        fully_requested		= false;
	}

    /** This flags the block at the given offset as NOT having been downloaded
     * and the whole piece as not having been fully downloaded
     * @param blockNumber
     */
    @Override
    public void clearDownloaded(int offset)
    {
        downloaded[offset /DiskManager.BLOCK_SIZE] =false;

        fully_downloaded	= false;
    }

    @Override
    public boolean
    isDownloaded()
    {
    	return( fully_downloaded );
    }

    @Override
    public boolean[]
    getDownloaded()
    {
    	return( downloaded );
    }

    @Override
    public boolean
    hasUndownloadedBlock()
    {
    	for (int i =0; i <nbBlocks; i++ ){

			if (!downloaded[i]){

				return( true );
			}
		}

    	return( false );
    }

	/** This marks a given block as having been written by the given peer
	 * @param peer the peer that sent the data
	 * @param blockNumber the block we're operating on
	 */
	@Override
	public void setWritten(String peer, int blockNumber)
	{
		writers[blockNumber] =peer;
		dmPiece.setWritten(blockNumber);
	}

	/** This method clears the requested information for the given block
     * unless the block has already been downloaded, in which case the writer's
     * IP is recorded as a request for the block.
	 */
	@Override
	public void clearRequested(int blockNumber)
	{
		requested[blockNumber] =downloaded[blockNumber] ?writers[blockNumber] :null;

		fully_requested = false;
	}

	@Override
	public boolean
	isRequested()
	{
		return( fully_requested );
	}

	@Override
	public void
	setRequested()
	{
		fully_requested	= true;
	}

	/** This will scan each block looking for requested blocks. For each one, it'll verify
	 * if the PEPeer for it still exists and is still willing and able to upload data.
	 * If not, it'll unmark the block as requested.
	 * @return int of how many were cleared (0 to nbBlocks)
	 */
    /*
	public int checkRequests()
	{
        if (getTimeSinceLastActivity() <30 *1000)
            return 0;
		int cleared =0;
		boolean nullPeer =false;
		for (int i =0; i <nbBlocks; i++)
		{
			if (!downloaded[i] &&!dmPiece.isWritten(i))
			{
				final String			requester =requested[i];
				final PEPeerTransport	pt;
				if (requester !=null)
				{
					pt =manager.getTransportFromAddress(requester);
					if (pt !=null)
					{
						pt.setSnubbed(true);
						if (!pt.isDownloadPossible())
						{
                            clearRequested(i);
							cleared++;
						}
					} else
					{
						nullPeer =true;
                        clearRequested(i);
						cleared++;
					}
				}
			}
		}
		if (cleared >0)
		{
			dmPiece.clearRequested();
            if (Logger.isEnabled())
                Logger.log(new LogEvent(dmPiece.getManager().getTorrent(), LOGID, LogEvent.LT_WARNING,
                        "checkRequests(): piece #" +getPieceNumber()+" cleared " +cleared +" requests."
                        + (nullPeer ?" Null peer was detected." :"")));
		}
		return cleared;
	}
	*/

		/*
		 * Parg: replaced above commented out checking with one that verifies that the
		 * requests still exist. As piece-picker activity and peer disconnect logic is multi-threaded
		 * and full of holes, this is a stop-gap measure to prevent a piece from being left with
		 * requests that no longer exist
		 */

	public void
	checkRequests()
	{
        if ( getTimeSinceLastActivity() < 30*1000 ){

            return;
        }

		int cleared = 0;

		PEPeerManager manager = piecePicker.getPeerManager();
		
		for (int i=0; i<nbBlocks; i++){

			if (!downloaded[i] &&!dmPiece.isWritten(i)){

				final String			requester = requested[i];

				if ( requester != null ){

					if ( !manager.requestExists(
							requester,
							getPieceNumber(),
							i *DiskManager.BLOCK_SIZE,
							getBlockSize( i ))){

                        clearRequested(i);

						cleared++;
					}
				}
			}
		}

		if ( cleared > 0 ){

            if (Logger.isEnabled())
                Logger.log(new LogEvent(dmPiece.getManager().getTorrent(), LOGID, LogEvent.LT_WARNING,
                        "checkRequests(): piece #" +getPieceNumber()+" cleared " +cleared +" requests" ));
		}else{

			if ( fully_requested && getNbUnrequested() > 0 ){

		          if (Logger.isEnabled())
		                Logger.log(new LogEvent(dmPiece.getManager().getTorrent(), LOGID, LogEvent.LT_WARNING,
		                        "checkRequests(): piece #" +getPieceNumber()+" reset fully requested" ));

				fully_requested = false;
			}
		}
	}


	/** @return true if the piece has any blocks that are not;
	 *  Downloaded, Requested, or Written
	 */
	@Override
	public boolean hasUnrequestedBlock()
	{
		final boolean[] written =dmPiece.getWritten();
		for (int i =0; i <nbBlocks; i++ )
		{
			if (!downloaded[i] &&requested[i] ==null &&(written ==null ||!written[i]))
				return true;
		}
		return false;
	}

	/**
	 * This method scans a piece for the first unrequested block.  Upon finding it,
	 * it counts how many are unrequested up to nbWanted.
	 * The blocks are marked as requested by the PEPeer
	 * Assumption - single threaded access to this
	 * TODO: this should return the largest span equal or smaller than nbWanted
	 * OR, probably a different method should do that, so this one can support 'more sequential' picking
	 */
	@Override
	public int[]
	getAndMarkBlocks(
		PEPeer 		peer,
		int 		nbWanted,
		int[]		request_hint,
		boolean 	reverse_order )
	{
		final String ip = peer.getIp();

        final boolean[] written = dmPiece.getWritten();

		if ( request_hint != null ){

				// try to honour the hint first

			int	hint_block_start 	= request_hint[1] / DiskManager.BLOCK_SIZE;
			int	hint_block_end	 	= ( request_hint[1] + request_hint[2] -1 )/ DiskManager.BLOCK_SIZE;

			if ( reverse_order ){

				for ( int i = Math.min( nbBlocks-1, hint_block_end ); i >= hint_block_start; i--){

					int blocksFound = 0;
					int	block_index	= i;

					while ( blocksFound < nbWanted &&
							block_index < nbBlocks &&
							!downloaded[ block_index ] &&
							requested[block_index] == null &&
							( written == null || !written[block_index] )){

						requested[ block_index ] = ip;
						blocksFound++;
						block_index--;
					}
					if ( blocksFound > 0 ){
						return new int[] {block_index+1, blocksFound};
					}
				}
			}else{
				for (int i = hint_block_start; i < nbBlocks && i <= hint_block_end; i++){

					int blocksFound = 0;
					int	block_index	= i;

					while ( blocksFound < nbWanted &&
							block_index < nbBlocks &&
							!downloaded[ block_index ] &&
							requested[block_index] == null &&
							( written == null || !written[block_index] )){

						requested[ block_index ] = ip;
						blocksFound++;
						block_index++;
					}
					if ( blocksFound > 0 ){
						return new int[] {i, blocksFound};
					}
				}
			}
		}

			// scan piece to find first free block

		if ( reverse_order ){

			for (int i=nbBlocks-1; i >= 0; i-- ){

				int blocksFound = 0;
				int	block_index = i;

				while (	blocksFound < nbWanted &&
						block_index >= 0 &&
						!downloaded[block_index] &&
						requested[block_index] == null &&
						( written == null || !written[block_index] )){

					requested[block_index] = ip;
					blocksFound++;
					block_index--;
				}
				if ( blocksFound > 0 ){
					return new int[] {block_index+1, blocksFound};
				}
			}
		}else{

			for (int i =0; i <nbBlocks; i++){

				int blocksFound = 0;
				int	block_index = i;

				while (	blocksFound < nbWanted &&
						block_index < nbBlocks &&
						!downloaded[ block_index ] &&
						requested[ block_index ] == null &&
						( written == null || !written[block_index] )){

					requested[block_index] = ip;
					blocksFound++;
					block_index++;
				}
				if ( blocksFound > 0 ){
					return new int[] {i, blocksFound};
				}
			}
		}
		return new int[] {-1, 0};
	}

	@Override
	public void getAndMarkBlock(PEPeer peer, int index )
	{
		requested[index] = peer.getIp();

		if ( getNbUnrequested() <= 0 ){

			setRequested();
		}
	}

    @Override
    public int getNbRequests()
    {
        int result =0;
        for (int i =0; i <nbBlocks; i++)
        {
            if (!downloaded[i] &&requested[i] !=null)
                result++;
        }
        return result;
    }

    @Override
    public int getNbUnrequested()
    {
        int result =0;
        final boolean[] written =dmPiece.getWritten();
        for (int i =0; i <nbBlocks; i++ )
        {
            if (!downloaded[i] &&requested[i] ==null &&(written ==null ||!written[i]))
                result++;
        }
        return result;
    }

	/**
	 * Assumption - single threaded with getAndMarkBlock
	 */
	@Override
	public boolean setRequested(PEPeer peer, int blockNumber)
	{
		if (!downloaded[blockNumber])
		{
			requested[blockNumber] =peer.getIp();
			return true;
		}
		return false;
	}

	@Override
	public boolean
	isRequestable()
	{
		return( dmPiece.isDownloadable() && !( fully_downloaded || fully_requested ));
	}

	@Override
	public int
	getBlockSize(
		int blockNumber)
	{
		if ( blockNumber == (nbBlocks - 1)){

			int	length = dmPiece.getLength();

			if ((length % DiskManager.BLOCK_SIZE) != 0){

				return( length % DiskManager.BLOCK_SIZE );
			}
		}

		return DiskManager.BLOCK_SIZE;
	}

    @Override
    public int getBlockNumber(int offset)
    {
        return offset /DiskManager.BLOCK_SIZE;
    }

	@Override
	public int getNbBlocks()
	{
		return nbBlocks;
	}

	public List getPieceWrites()
	{
		List result;
		try{
			class_mon.enter();

			result = new ArrayList(writes);
		}finally{

			class_mon.exit();
		}
		return result;
	}


	public List getPieceWrites(int blockNumber) {
		final List result;
		try{
			class_mon.enter();

			result = new ArrayList(writes);

		}finally{

			class_mon.exit();
		}
		final Iterator iter = result.iterator();
		while(iter.hasNext()) {
			final PEPieceWriteImpl write = (PEPieceWriteImpl) iter.next();
			if(write.getBlockNumber() != blockNumber)
				iter.remove();
		}
		return result;
	}


	public List getPieceWrites(PEPeer peer) {
		final List result;
		try{
			class_mon.enter();

			result = new ArrayList(writes);
		}finally{
			class_mon.exit();
		}
		final Iterator iter = result.iterator();
		while(iter.hasNext()) {
			PEPieceWriteImpl write = (PEPieceWriteImpl) iter.next();
			if(peer == null || ! peer.getIp().equals(write.getSender()))
				iter.remove();
		}
		return result;
	}

	public List
	getPieceWrites(
		String	ip )
	{
		final List result;

		try{
			class_mon.enter();

			result = new ArrayList(writes);

		}finally{

			class_mon.exit();
		}

		final Iterator iter = result.iterator();

		while(iter.hasNext()) {

			final PEPieceWriteImpl write = (PEPieceWriteImpl) iter.next();

			if ( !write.getSender().equals( ip )){

				iter.remove();
			}
		}

		return result;
	}
	
	private void
	fixupPadFile()
	{
		// easiest way to deal with the fact that pad files are effectively already downloaded (contents is just array of zero bytes) is to wait until
		// a piece is allocated for downloading and then set the pad blocks as written. If you try and do this via presetting resume data then you end
		// up with potentially a load of partial pieces on startup

		DMPieceList pl = dmPiece.getPieceList();

		if ( pl.size() == 2 ){

			DMPieceMapEntry pme2 = pl.get( 1 );

			if ( pme2.getFile().getTorrentFile().isPadFile()){

				DMPieceMapEntry pme1 = pl.get( 0 );

				int	len1 = pme1.getLength();

				int pad_block_start = ( len1 + DiskManager.BLOCK_SIZE - 1 ) / DiskManager.BLOCK_SIZE;

				for ( int i=pad_block_start; i<nbBlocks; i++ ){

					dmPiece.setWritten( i );
				}
			}
		}
	}

	@Override
	public void reset()
	{
		dmPiece.reset();
		
		fixupPadFile();
		
		boolean[] written = dmPiece.getWritten();
		
		for (int i =0; i <nbBlocks; i++){
		
            requested[i] =null;
			downloaded[i] = written==null?false:written[i];
			writers[i] =null;
		}
		
		fully_downloaded = false;
		time_last_download = 0;
		reservedBy =null;
		real_time_data=null;
	}

	@Override
	public Object
	getRealTimeData()
	{
		return( real_time_data );
	}

	@Override
	public void
	setRealTimeData(
		Object	o )
	{
		real_time_data = o;
	}

	protected void addWrite(PEPieceWriteImpl write) {
		try{
			class_mon.enter();

			writes.add(write);

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public void
	addWrite(
		int blockNumber,
		String sender,
		byte[] hash,
		boolean correct	)
	{
		addWrite( new PEPieceWriteImpl( blockNumber, sender, hash, correct ));
	}

	@Override
	public String[] getWriters()
	{
		return writers;
	}

	@Override
	public int getSpeed()
	{
		return speed;
	}

	@Override
	public void setSpeed(int newSpeed)
	{
		speed =newSpeed;
	}

	@Override
	public void
	setLastRequestedPeerSpeed(
		int		peerSpeed )
	{
		// Up the speed on this piece?
		if (peerSpeed > speed ){
			speed++;
		}
	}

	@Override
	public PiecePicker getPiecePicker(){
		return( piecePicker );
	}
	/**
	 * @return Returns the manager.
	 */
	@Override
	public PEPeerManager getManager()
	{
		return piecePicker.getPeerManager();
	}

	@Override
	public void setReservedBy(String peer)
	{
		reservedBy =peer;
	}

	@Override
	public String getReservedBy()
	{
		return reservedBy;
	}

	/** for a block that's already downloadedt, mark up the piece
	 * so that the block will get downloaded again.  This is used
	 * when the piece fails hash-checking.
	 */
	public void reDownloadBlock(int blockNumber)
	{
		downloaded[blockNumber] =false;
		requested[blockNumber] =null;
		fully_downloaded = false;
		writers[blockNumber] = null;
		dmPiece.reDownloadBlock(blockNumber);
	}

	/** finds all blocks downloaded by the given address
	 * and marks them up for re-downloading
	 * @param address String
	 */
	public void reDownloadBlocks(String address)
	{
		for (int i =0; i <writers.length; i++ )
		{
			final String writer =writers[i];

			if (writer !=null &&writer.equals(address))
				reDownloadBlock(i);
		}
	}

	@Override
	public void setResumePriority(int p)
	{
		resumePriority =p;
	}

	@Override
	public int getResumePriority()
	{
		return resumePriority;
	}

    /**
     * @return int of availability in the swarm for this piece
     * @see com.biglybt.core.peer.PEPeerManager.getAvailability(int pieceNumber)
     */
    @Override
    public int getAvailability()
    {
        return piecePicker.getPeerManager().getAvailability(dmPiece.getPieceNumber());
    }

    /** This support method returns how many blocks have already been
     * written from the dmPiece
     * @return int from dmPiece.getNbWritten()
     * @see com.biglybt.core.disk.DiskManagerPiece.getNbWritten()
     */
    @Override
    public int getNbWritten()
    {
        return dmPiece.getNbWritten();
    }

    /** This support method returns the dmPiece's written array
     * @return boolean[] from the dmPiece
     * @see com.biglybt.core.disk.DiskManagerPiece.getWritten()
     */
    public boolean[] getWritten()
    {
        return dmPiece.getWritten();
    }
    @Override
    public boolean isWritten()
    {
        return dmPiece.isWritten();
    }

    @Override
    public boolean isWritten(int block)
    {
        return dmPiece.isWritten( block );
    }
	@Override
	public int getPieceNumber()
	{
		return dmPiece.getPieceNumber();
	}

	@Override
	public int getLength()
	{
		return dmPiece.getLength();
	}

	public void setRequestable()
	{
		fully_downloaded	= false;
		fully_requested		= false;

		dmPiece.setDownloadable();
	}

	@Override
	public String
	getString()
	{
		String	text  = "";

		text	+= ( isRequestable()?"reqable,":"" );
		text	+= "req=" + getNbRequests() + ",";
		text	+= ( isRequested()?"reqstd,":"" );
		text	+= ( isDownloaded()?"downed,":"" );
		text	+= ( getReservedBy()!=null?"resrv,":"" );
		text	+= "speed=" + getSpeed() + ",";
		text	+= ( piecePicker==null?("pri=" + getResumePriority()):piecePicker.getPieceString(dmPiece.getPieceNumber()));

		if ( text.endsWith(",")){
			text = text.substring(0,text.length()-1);
		}

		return( text );
	}
}