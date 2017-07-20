/*
 * Created on 20-Jan-2005
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

package com.biglybt.core.dht.control.impl;

import com.biglybt.core.dht.control.DHTControlContact;
import com.biglybt.core.dht.router.DHTRouterContact;
import com.biglybt.core.dht.router.DHTRouterContactAttachment;
import com.biglybt.core.dht.transport.DHTTransportContact;

/**
 * @author parg
 *
 */

public class
DHTControlContactImpl
	implements DHTControlContact, DHTRouterContactAttachment
{
	private final DHTTransportContact		t_contact;
	private DHTRouterContact		r_contact;

	protected
	DHTControlContactImpl(
		DHTTransportContact		_t_contact )
	{
		t_contact	= _t_contact;
	}

	public
	DHTControlContactImpl(
		DHTTransportContact		_t_contact,
		DHTRouterContact		_r_contact )
	{
		t_contact	= _t_contact;
		r_contact	= _r_contact;
	}

	@Override
	public void
	setRouterContact(
		DHTRouterContact		_r_contact )
	{
		r_contact	= _r_contact;
	}

	@Override
	public int
	getMaxFailForLiveCount()
	{
		return( t_contact.getMaxFailForLiveCount());
	}

	@Override
	public int
	getMaxFailForUnknownCount()
	{
		return( t_contact.getMaxFailForUnknownCount());
	}

	@Override
	public int
	getInstanceID()
	{
		return( t_contact.getInstanceID());
	}

	@Override
	public DHTTransportContact
	getTransportContact()
	{
		return( t_contact );
	}

	@Override
	public DHTRouterContact
	getRouterContact()
	{
		return( r_contact );
	}

	@Override
	public boolean
	isSleeping()
	{
		return( t_contact.isSleeping());
	}
}
