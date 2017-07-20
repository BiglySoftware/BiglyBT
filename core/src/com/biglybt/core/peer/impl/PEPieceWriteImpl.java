/*
 * File    : PEPieceWriteImpl.java
 * Created : 27-Dec-2003
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

package com.biglybt.core.peer.impl;

/**
 * @author parg
 *
 */


public class
PEPieceWriteImpl
{
	protected final int blockNumber;
	protected final String sender;
	protected final byte[] hash;
	protected final boolean correct;

	public PEPieceWriteImpl(int blockNumber,String sender, byte[] hash,boolean correct) {
		this.blockNumber = blockNumber;
		this.sender = sender;
		this.hash = hash;
		this.correct = correct;
	}

	public String
	getSender()
	{
		return( sender );
	}

	public int
	getBlockNumber()
	{
		return( blockNumber );
	}

	public byte[]
	getHash()
	{
		return( hash );
	}

	public boolean
	isCorrect()
	{
		return( correct );
	}
}
