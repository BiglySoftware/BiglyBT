/*
 * File    : DiskManagerRequest.java
 * Created : 22-Mar-2004
 * By      : parg
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

package com.biglybt.pif.peers;

import com.biglybt.pif.disk.DiskManager;

/**
 * @author parg
 *
 * @since 2307
 */

public interface
PeerReadRequest
{
	public static final int	NORMAL_REQUEST_SIZE	= DiskManager.BLOCK_SIZE;

	public int
	getPieceNumber();

	public int
	getOffset();

	public int
	getLength();

	public void
	resetTimeMono( long mono_now);

	public boolean
	isExpired();

	public void
	cancel();

	public boolean
	isCancelled();
}
