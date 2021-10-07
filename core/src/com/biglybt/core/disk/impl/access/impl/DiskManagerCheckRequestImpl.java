/*
 * Created on 12-Dec-2005
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

package com.biglybt.core.disk.impl.access.impl;

import com.biglybt.core.disk.DiskManagerCheckRequest;

public class
DiskManagerCheckRequestImpl
	extends DiskManagerRequestImpl
	implements DiskManagerCheckRequest
{
	private final int		piece_number;
	private final Object	user_data;
	
	private boolean	low_priority;
	private boolean	ad_hoc		= true;
	private boolean	explicit;
	
	private byte[]	hash;

	public
	DiskManagerCheckRequestImpl(
		int		_piece_number,
		Object	_user_data )
	{
		piece_number	= _piece_number;
		user_data		= _user_data;
	}

	@Override
	protected String
	getName()
	{
		return( "Check: " + piece_number + ",lp=" + low_priority + ",ah=" + ad_hoc );
	}

	@Override
	public int
	getPieceNumber()
	{
		return( piece_number );
	}

	@Override
	public Object
	getUserData()
	{
		return( user_data );
	}

	@Override
	public void
	setLowPriority(
		boolean	low )
	{
		low_priority	= low;
	}

	@Override
	public boolean
	isLowPriority()
	{
		return( low_priority );
	}

	@Override
	public void
	setAdHoc(
		boolean	_ad_hoc )
	{
		ad_hoc	= _ad_hoc;
	}

	@Override
	public boolean
	isAdHoc()
	{
		return( ad_hoc );
	}

	@Override
	public boolean 
	isExplicit()
	{
		return( explicit );
	}
	
	@Override
	public void 
	setExplicit(
		boolean	_explicit )
	{
		explicit = _explicit;
	}
	
	@Override
	public void
	setHash(
		byte[]		_hash )
	{
		hash	= _hash;
	}

	@Override
	public byte[]
	getHash()
	{
		return( hash );
	}
}
