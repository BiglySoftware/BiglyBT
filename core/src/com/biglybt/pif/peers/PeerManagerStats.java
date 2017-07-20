/*
 * Created on 13-Jul-2004
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

package com.biglybt.pif.peers;

/**
 * @author parg
 *
 */

public interface
PeerManagerStats
{
	public int
	getConnectedSeeds();

	public int
	getConnectedLeechers();

		// session stats

	public long
	getDownloaded();

	public long
	getUploaded();

	public long
	getDownloadAverage();

	public long
	getUploadAverage();

	public long
	getDiscarded();

	public long
	getHashFailBytes();

		// rate controls

	/**
	 * For an external process receiving bytes on behalf of this download this gives the current
	 * rate-limited number of bytes that can be received. Update with actual send using 'received' below.
	 * @since 4.4.0.7
	 * @return
	 */

	public int getPermittedBytesToReceive();

	/**
	 * The given number of data (payload) bytes have been received.
	 * This number gets added to the total and is used to calculate the rate.
	 * <p>
	 * Use this if you are talking to stuff outside of Azureus' API, and
	 * want your stats added into Azureus'
	 *
	 * @param bytes
	 *
	 *@since 4.4.0.7
	 */

	public void permittedReceiveBytesUsed(int bytes);

	/**
	 * For an external process sending bytes on behalf of this download this gives the current
	 * rate-limited number of bytes that can be sent. Update with actual send using 'sent' below.
	 * @since 4.4.0.7
	 * @return
	 */

	public int getPermittedBytesToSend();

	/**
	 * The given number of data (payload) bytes have been sent.
	 * This number gets added to the total and is used to calculate the rate.
	 * <p>
	 * Use this if you are talking to stuff outside of Azureus' API, and
	 * want your stats added into Azureus'
	 *
	 * @param bytes
	 *
	 * @since 4.4.0.7
	 */

	public void permittedSendBytesUsed(int bytes);
}
