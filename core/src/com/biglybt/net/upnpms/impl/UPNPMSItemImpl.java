/*
 * Created on Dec 20, 2012
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.net.upnpms.impl;

import java.net.URL;

import com.biglybt.net.upnpms.UPNPMSItem;

public class
UPNPMSItemImpl
	implements UPNPMSItem
{
	private String					id;
	private String					title;
	private String					item_class;
	private long					size;
	private URL						url;

	protected
	UPNPMSItemImpl(
		String				_id,
		String				_title,
		String				_class,
		long				_size,
		URL					_url )
	{
		id 			= _id;
		title		= _title;
		item_class	= _class;
		size		= _size;
		url			= _url;
	}

	@Override
	public String
	getID()
	{
		return( id );
	}

	@Override
	public String
	getTitle()
	{
		return( title );
	}

	@Override
	public String
	getItemClass()
	{
		return( item_class );
	}

	@Override
	public long
	getSize()
	{
		return( size );
	}

	@Override
	public URL
	getURL()
	{
		return( url );
	}
}
