/*
 * Created on 05-May-2004
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

package com.biglybt.pifimpl.remote.ipfilter;

/**
 * @author parg
 *
 */

import com.biglybt.pif.ipfilter.IPRange;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;


public class
RPIPRange
	extends		RPObject
	implements 	IPRange
{
	protected transient IPRange		delegate;

		// don't change these field names as they are visible on XML serialisation

	public String			description;
	public String			start_ip;
	public String			end_ip;

	public static RPIPRange
	create(
		IPRange		_delegate )
	{
		RPIPRange	res =(RPIPRange)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPIPRange( _delegate );
		}

		return( res );
	}

	protected
	RPIPRange(
		IPRange		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (IPRange)_delegate;

		description		= delegate.getDescription();
		start_ip		= delegate.getStartIP();
		end_ip			= delegate.getEndIP();
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		return( _fixupLocal());
	}

	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String	method = request.getMethod();

		if ( method.equals( "delete")){

			delegate.delete();

			return( null );
		}

		throw( new RPException( "Unknown method: " + method ));
	}


		// ***************************************************

	@Override
	public String
	getDescription()
	{
		return( description );
	}

	@Override
	public void
	setDescription(
		String	str )
	{
		notSupported();
	}

	@Override
	public void
	checkValid()
	{
		notSupported();
	}

	@Override
	public boolean
	isValid()
	{
		notSupported();

		return( false );
	}

	@Override
	public boolean
	isSessionOnly()
	{
		notSupported();

		return( false );
	}

	@Override
	public String
	getStartIP()
	{
		return( start_ip );
	}

	@Override
	public void
	setStartIP(
		String	str )
	{
		notSupported();
	}

	@Override
	public String
	getEndIP()
	{
		return( end_ip );
	}

	@Override
	public void
	setEndIP(
		String	str )
	{
		notSupported();
	}

	@Override
	public void
	setSessionOnly(
		boolean sessionOnly )
	{
		notSupported();
	}

	@Override
	public boolean
	isInRange(
		String ipAddress )
	{
		notSupported();

		return( false );
	}

	@Override
	public void
	delete()
	{
		_dispatcher.dispatch( new RPRequest( this, "delete", null )).getResponse();
	}

	@Override
	public int
	compareTo(
		Object	other )
	{
		notSupported();

		return( -1 );
	}
}