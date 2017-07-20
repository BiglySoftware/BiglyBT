/*
 * File    : TRTrackerServerException.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.tracker.server;

import java.util.Map;

public class
TRTrackerServerException
	extends Exception
{
	private int		response_code	= -1;
	private String	response_text;
	private Map		response_headers;

	private boolean	user_message;
	private Map		error_map;

	public
	TRTrackerServerException(
		int		_response_code,
		String	_response_text,
		Map		_response_headers )
	{
		response_code		= _response_code;
		response_text		= _response_text;
		response_headers	= _response_headers;
	}

	public
	TRTrackerServerException(
		String		str )
	{
		super(str);
	}

	public
	TRTrackerServerException(
		String		str,
		Throwable	e )
	{
		super(str,e);
	}

	public int
	getResponseCode()
	{
		return( response_code );
	}

	public String
	getResponseText()
	{
		return( response_text );
	}

	public Map
	getResponseHeaders()
	{
		return( response_headers );
	}


	public void
	setUserMessage(
		boolean	b )
	{
		user_message	= b;
	}

	public boolean
	isUserMessage()
	{
		return( user_message );
	}

	public void
	setErrorEntries(
		Map		map )
	{
		error_map	= map;
	}

	public Map
	getErrorEntries()
	{
		return( error_map );
	}
}
