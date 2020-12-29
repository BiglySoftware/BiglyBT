/*
 * Created on 1 Nov 2006
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

package com.biglybt.core.networkmanager;

public interface
NetworkConnectionBase
{
	public ConnectionEndpoint
	getEndpoint();

	 /**
	   * Inform connection of a thrown exception.
	   * @param error exception
	   */

	public void notifyOfException( Throwable error );

	  /**
	   * Get the connection's outgoing message queue.
	   * @return outbound message queue
	   */
	public OutgoingMessageQueue getOutgoingMessageQueue();


	  /**
	   * Get the connection's incoming message queue.
	   * @return inbound message queue
	   */
	public IncomingMessageQueue getIncomingMessageQueue();

	 /**
	   * Get the connection's data transport interface.
	   * @return the transport - MAY BE NULL if not yet fully connected
	   */

	public TransportBase getTransportBase();

	public int
	getMssSize();

	public boolean
	isIncoming();

	 /**
	   * Is the connection within the local LAN network.
	   * @return true if within LAN, false of outside the LAN segment
	   */

	public boolean isLANLocal();

	public void resetLANLocalStatus();
	
	public boolean
	isClosed();
	
	public void
	setUploadLimit(
		int		limit );

	public int
	getUploadLimit();

	public void
	setDownloadLimit(
		int		limit );

	public int
	getDownloadLimit();

	public LimitedRateGroup[]
	getRateLimiters(
		boolean	upload );

	public void
	addRateLimiter(
		LimitedRateGroup	limiter,
		boolean				upload );

	public void
	removeRateLimiter(
		LimitedRateGroup	limiter,
		boolean				upload );

	public String
	getString();
}
