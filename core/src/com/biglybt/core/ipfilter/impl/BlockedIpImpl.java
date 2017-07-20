/*
 * File    : BlockedIpImpl.java
 * Created : 12 d�c. 2003}
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
package com.biglybt.core.ipfilter.impl;

import com.biglybt.core.ipfilter.BlockedIp;
import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.core.util.SystemTime;

/**
 * @author Olivier
 *
 */
public class BlockedIpImpl implements BlockedIp {

  private final String ip;
  private final long time;
  private final IpRange range;
  private final String torrentname;
  private final boolean	loggable;

  public BlockedIpImpl(String ip,IpRange range, String torrent_name,boolean _loggable) {
    this.ip = ip;
    this.range = range;
    this.time = SystemTime.getCurrentTime();
    this.torrentname = torrent_name;
    loggable = _loggable;
  }

  @Override
  public String getBlockedIp() {
    return this.ip;
  }

  @Override
  public IpRange getBlockingRange() {
    return this.range;
  }

  @Override
  public long getBlockedTime() {
    return time;
  }

  @Override
  public String getTorrentName() {
    return torrentname;
  }

  @Override
  public boolean
  isLoggable()
  {
	  return( loggable );
  }
}
