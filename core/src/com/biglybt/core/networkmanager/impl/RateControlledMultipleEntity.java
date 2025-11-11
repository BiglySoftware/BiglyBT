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

import com.biglybt.core.util.Debug;

public abstract class 
RateControlledMultipleEntity
	implements RateControlledEntity
{	
		// multi-peer uploader/downloaders are singletons and never get removed from their controllers
		// the internally manage multiple connections that have their controller allocations handled there
	
	public boolean
	isReadControllerActive(
		int		partition )
	{
		return( true );
	}

	public void
	setReadControllerInactive()
	{
		Debug.out( "eh?" );
	}

	public void
	setTargetReadControllerPartition(
		int		partition )
	{
	}

	public void
	activeReadControllerRelease(
		boolean		added )
	{
		Debug.out( "eh?" );
	}

	public boolean
	isWriteControllerActive(
		int		partition )
	{
		return( true );
	}

	public void
	setWriteControllerInactive()
	{
		Debug.out( "eh?" );
	}

	public void
	setTargetWriteControllerPartition(
		int		partition )
	{
	}

	public void
	activeWriteControllerRelease(
		boolean		added )
	{
		Debug.out( "eh?" );
	}
}
