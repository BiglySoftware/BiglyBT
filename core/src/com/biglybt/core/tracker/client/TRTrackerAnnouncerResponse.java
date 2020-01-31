/*
 * File    : TRTrackerResponse.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.tracker.client;

import java.net.URL;
import java.util.Map;

import com.biglybt.core.util.HashWrapper;

public interface
TRTrackerAnnouncerResponse
{
	public static final int	ST_OFFLINE			= 0;
	public static final int ST_REPORTED_ERROR	= 1;
	public static final int	ST_ONLINE			= 2;

	/**
	 * Returns the current status of the tracker
	 * @return	see above ST_ set
	 */

	public int
	getStatus();

	public String
	getStatusString();

	public HashWrapper
	getHash();

	public TRTrackerAnnouncerRequest
	getRequest();
	
	/**
	 * This value is always available
	 * @return time to wait before requerying tracker
	 */

	public long
	getTimeToWait();

	/**
	 * Returns any additional textual information associated with reponse.
	 * If the status is ST_REPORTED_ERROR, this will return the error description
	 * (possibly directly from the tracker).
	 *
	 * @return	Additional information
	 */

	public String
	getAdditionalInfo();

	/**
	 *
	 * @return	peers reported by tracker. this will include the local peer as well
	 */

	public TRTrackerAnnouncerResponsePeer[]
	getPeers();

	public void
	setPeers(
		TRTrackerAnnouncerResponsePeer[]	peers );

	public Map
	getExtensions();

	public URL
	getURL();

	public int
	getScrapeCompleteCount();

	public int
	getScrapeIncompleteCount();

	public int
	getScrapeDownloadedCount();

	public void
	print();
}
