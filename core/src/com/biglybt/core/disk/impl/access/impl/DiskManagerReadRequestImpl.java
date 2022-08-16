/*
 * File    : DiskManagerRequestImpl.java
 * Created : 18-Oct-2003
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

package com.biglybt.core.disk.impl.access.impl;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.util.SystemTime;

/**
 *
 * This class represents a Bittorrent Request.
 * and a time stamp to know when it was created.
 *
 * Request may expire after some time, which is used to determine who is snubbed.
 *
 * @author Olivier
 *
 *
 */
public class
DiskManagerReadRequestImpl
	extends DiskManagerRequestImpl
	implements DiskManagerReadRequest
{
  //60 secs of expiration for any request.
  private static final int EXPIRATION_TIME = 1000 * 60;

  private final int pieceNumber;
  private final int offset;
  private final int length;
  private final int hashcode;

  private long      timeCreatedMono;
  private long		timeSent;
  private boolean	flush;
  private boolean	cancelled;
  private boolean	use_cache	= true;
  private boolean	latency_test;


  /**
   * Parameters correspond to bittorrent parameters
   * @param pieceNumber
   * @param offset
   * @param length
   */
  public DiskManagerReadRequestImpl(final int _pieceNumber, final int _offset, final int _length)
  {
    pieceNumber = _pieceNumber;
    offset = _offset;
    length = _length;

    timeCreatedMono = SystemTime.getMonotonousTime();

    hashcode = pieceNumber + offset + length;
  }

	@Override
	protected String
	getName()
	{
		return( "Read: " + pieceNumber + ",off=" + offset +",len=" + length + ",fl=" + flush + ",uc=" + use_cache );
	}

  /**
   * Method to determine if a Request has expired
   * @return true if the request is expired
   */
  @Override
  public boolean isExpired()
  {
      final long mono_now = SystemTime.getMonotonousTime();
 
      return (mono_now -this.timeCreatedMono) >EXPIRATION_TIME;
  }

  /**
   * Allow some more time to the request.
   * Typically used on peers that have just sent some data, we reset all
   * other requests to give them extra time.
   */
  @Override
  public void resetTimeMono(final long mono_now)
  {
	  timeCreatedMono = mono_now;
  }

  //Getters
  @Override
  public int getPieceNumber()
  {
    return this.pieceNumber;
  }

  @Override
  public int getOffset()
  {
    return this.offset;
  }

  @Override
  public int getLength()
  {
    return this.length;
  }

	@Override
	public void
	setFlush(
		boolean	_flush )
	{
		flush	= _flush;
	}

	@Override
	public boolean
	getFlush()
	{
		return( flush );
	}

	@Override
	public void
	setUseCache(
		boolean	cache )
	{
		use_cache	= cache;
	}

	@Override
	public boolean
	getUseCache()
	{
		return( use_cache );
	}

	@Override
	public void
	cancel()
	{
		cancelled	= true;
	}

	@Override
	public boolean
	isCancelled()
	{
		return( cancelled );
	}

  /**
   * We override the equals method
   * 2 requests are equals if
   * all their bt fields (piece number, offset, length) are equal
   */
  public boolean equals(Object o)
  {
    if(! (o instanceof DiskManagerReadRequestImpl))
      return false;
	DiskManagerReadRequestImpl otherRequest = (DiskManagerReadRequestImpl) o;
    if(otherRequest.pieceNumber != this.pieceNumber)
      return false;
    if(otherRequest.offset != this.offset)
      return false;
    if(otherRequest.length != this.length)
      return false;

    return true;
  }

  public int hashCode() {
    return hashcode;
  }


  @Override
  public long 
  getTimeCreatedMono()
  {
    return timeCreatedMono;
  }

	@Override
	public void setTimeSent(long time ){
		timeSent = time;
	}

	@Override
	public long getTimeSent(){
		return( timeSent );
	}

	@Override
	public void
	setLatencyTest()
	{
		latency_test = true;
	}

	@Override
	public boolean
	isLatencyTest()
	{
		return( latency_test );
	}

}
