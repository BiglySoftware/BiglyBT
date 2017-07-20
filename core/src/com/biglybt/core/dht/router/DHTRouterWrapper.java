/*
 * Created on Aug 26, 2014
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

import java.util.List;

public class
DHTRouterWrapper
	implements DHTRouter
{
	private final DHTRouter		delegate;

	public
	DHTRouterWrapper(
		DHTRouter		_delegate )
	{
		delegate	= _delegate;
	}

	protected DHTRouter
	getDelegate()
	{
		return( delegate );
	}

	@Override
	public int
	getK()
	{
		return( delegate.getK());
	}

	@Override
	public byte[]
	getID()
	{
		return( delegate.getID());
	}

	@Override
	public boolean
	isID(
		byte[]	node_id )
	{
		return( delegate.isID(node_id));
	}

	@Override
	public DHTRouterContact
	getLocalContact()
	{
		return( delegate.getLocalContact());
	}

	@Override
	public void
	setAdapter(
		DHTRouterAdapter	_adapter )
	{
		delegate.setAdapter(_adapter);
	}

	@Override
	public void
	seed()
	{
		delegate.seed();
	}

	@Override
	public void
	contactKnown(
		byte[]						node_id,
		DHTRouterContactAttachment	attachment,
		boolean						force )
	{
		delegate.contactKnown(node_id, attachment, force);
	}

	@Override
	public void
	contactAlive(
		byte[]						node_id,
		DHTRouterContactAttachment	attachment )
	{
		delegate.contactAlive(node_id, attachment);
	}

	@Override
	public DHTRouterContact
	contactDead(
		byte[]						node_id,
		boolean						force )
	{
		return( delegate.contactDead(node_id, force));
	}

	@Override
	public DHTRouterContact
	findContact(
		byte[]	node_id )
	{
		return( delegate.findContact(node_id));
	}

	@Override
	public List<DHTRouterContact>
	findClosestContacts(
		byte[]		node_id,
		int			num_to_return,
		boolean		live_only )
	{
		return( delegate.findClosestContacts(node_id, num_to_return, live_only));
	}

	@Override
	public void
	recordLookup(
		byte[]	node_id )
	{
		delegate.recordLookup(node_id);
	}

	@Override
	public boolean
	requestPing(
		byte[]	node_id )
	{
		return( delegate.requestPing(node_id));
	}

	@Override
	public void
	refreshIdleLeaves(
		long	idle_max )
	{
		delegate.refreshIdleLeaves(idle_max);
	}

	@Override
	public byte[]
	refreshRandom()
	{
		return( delegate.refreshRandom());
	}

	@Override
	public List<DHTRouterContact>
	findBestContacts(
		int		max )
	{
		return( delegate.findBestContacts(max));
	}

	@Override
	public List<DHTRouterContact>
	getAllContacts()
	{
		return( delegate.getAllContacts());
	}

	@Override
	public DHTRouterStats
	getStats()
	{
		return( delegate.getStats());
	}

	@Override
	public void
	setSleeping(
		boolean	sleeping )
	{
		delegate.setSleeping(sleeping);
	}

	@Override
	public void
	setSuspended(
		boolean			susp )
	{
		delegate.setSuspended(susp);
	}

	@Override
	public void
	destroy()
	{
		delegate.destroy();
	}

	@Override
	public void
	print()
	{
		delegate.print();
	}

	@Override
	public boolean addObserver(DHTRouterObserver rto)
	{
		return( delegate.addObserver(rto));
	}

	@Override
	public boolean containsObserver(DHTRouterObserver rto)
	{
		return( delegate.containsObserver(rto));
	}

	@Override
	public boolean removeObserver(DHTRouterObserver rto)
	{
		return( delegate.removeObserver(rto));
	}
}
