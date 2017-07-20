/*
 * File    : GeneralStats.java
 * Created : 15 d�c. 2003}
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
package com.biglybt.core.stats.transfer;

/**
 * @author Olivier
 *
 */
public interface GeneralStats {

  /**
   * @return the number of downloaded bytes
   */
  public long getDownloadedBytes();

  /**
   * @return the number of uploaded bytes
   */
  public long getUploadedBytes();

  /**
   * @return the total lifetime 'up time' in seconds
   */
  public long getTotalUpTime();

  /**
   * Method variants that can return values relative to the last mark
   * @param since_mark
   * @return
   */
  public long getDownloadedBytes( boolean since_mark );
  public long getUploadedBytes( boolean since_mark );
  public long getTotalUpTime( boolean since_mark );
  public int getAverageDownloadSpeed( boolean since_mark );
  public int getAverageUploadSpeed( boolean since_mark );

  public long getMarkTime();
  /**
   * Sets a mark against the overall up/down/uptime so that the methods that report relative to
   * the mark return values relative to it
   */

  public void
  setMark();

  public void
  clearMark();

  /**
   * @return this session uptime in seconds
   */
  public long getSessionUpTime();

  /**
   * @return the average download speed in bytes per second
   */
  public int getAverageDownloadSpeed();

  /**
   * @return the average upload speed in bytes per second
   */
  public int getAverageUploadSpeed();
}
