/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.networkmanager.impl;

import com.biglybt.core.networkmanager.NetworkConnectionBase;

public abstract class 
RateControlledSingleEntity
	implements RateControlledEntity
{
	private final NetworkConnectionBase		connection;
	
	protected
	RateControlledSingleEntity(
		NetworkConnectionBase		_connection )
	{
		connection = _connection;
	}
	
	public boolean
	isReadControllerActive(
			int		partition )
	{
		return( connection.isReadControllerActive(partition));
	}

	public void
	setReadControllerInactive()
	{
		connection.setReadControllerInactive();
	}

	public void
	setTargetReadControllerPartition(
		int		partition )
	{
		connection.setTargetReadControllerPartition(partition);
	}

	public void
	activeReadControllerRelease(
		boolean		added )
	{
		connection.activeReadControllerRelease(added);
	}

	public boolean
	isWriteControllerActive(
		int		partition )
	{
		return( connection.isWriteControllerActive(partition));
	}

	public void
	setWriteControllerInactive()
	{
		connection.setWriteControllerInactive();
	}

	public void
	setTargetWriteControllerPartition(
			int		partition )
	{
		connection.setTargetWriteControllerPartition(partition);
	}

	public void
	activeWriteControllerRelease(
			boolean		added )
	{
		connection.activeWriteControllerRelease(added);
	}
}
