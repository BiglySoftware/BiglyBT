/*
 * Created on Aug 27, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.dht.router;

public class
DHTRouterContactWrapper
	implements DHTRouterContact
{
	private final DHTRouterContact		delegate;

	public
	DHTRouterContactWrapper(
		DHTRouterContact		_contact )
	{
		delegate	= _contact;
	}

	protected DHTRouterContact
	getDelegate()
	{
		return( delegate );
	}

	@Override
	public byte[]
	getID()
	{
		return( delegate.getID());
	}

	@Override
	public DHTRouterContactAttachment
	getAttachment()
	{
		return( delegate.getAttachment());
	}

	@Override
	public boolean
	hasBeenAlive()
	{
		return( delegate.hasBeenAlive());
	}

	@Override
	public boolean
	isFailing()
	{
		return( delegate.isFailing());
	}

	@Override
	public boolean
	isAlive()
	{
		return( delegate.isAlive());
	}

	@Override
	public long
	getTimeAlive()
	{
		return( delegate.getTimeAlive());
	}

	@Override
	public String
	getString()
	{
		return( delegate.getString());
	}

	@Override
	public boolean
	isBucketEntry()
	{
		return( delegate.isBucketEntry());
	}

	@Override
	public boolean
	isReplacement()
	{
		return( delegate.isReplacement());
	}
}
