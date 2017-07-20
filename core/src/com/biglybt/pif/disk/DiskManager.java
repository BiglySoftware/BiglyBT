/*
 * File    : DiskManager.java
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

package com.biglybt.pif.disk;

import com.biglybt.pif.utils.PooledByteBuffer;

/**
 * @author parg
 *
 */

public interface
DiskManager
{
	public static final int	BLOCK_SIZE	= 16384;

	public DiskManagerReadRequest
	read(
		int								piece_number,
		int								offset,
		int								length,
		DiskManagerReadRequestListener	listener )

		throws DiskManagerException;

		/**
		 * Data length has to be consistent with block layout of the piece and piece size
		 * @param piece_number
		 * @param offset
		 * @param data
		 * @param listener
		 * @return
		 * @throws DiskManagerException
		 */

	public DiskManagerWriteRequest
	write(
		int								piece_number,
		int								offset,
		PooledByteBuffer				data,
		DiskManagerWriteRequestListener	listener )

		throws DiskManagerException;
}
