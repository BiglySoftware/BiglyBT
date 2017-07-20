/*
 * Created on 26-Jul-2004
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

package com.biglybt.core.internat;

/**
 * @author parg
 *
 */

public class
LocaleUtilEncodingException
	extends Exception
{
	protected String[]		valid_charsets;
	protected String[]		valid_names;

	protected boolean		abandoned;

	public
	LocaleUtilEncodingException(
		String[]		charsets,
		String[]		names )
	{
		valid_charsets	= charsets;
		valid_names		= names;
	}

	public
	LocaleUtilEncodingException(
		Throwable	cause )
	{
		super( cause );
	}

	public
	LocaleUtilEncodingException(
		boolean		_abandoned )
	{
		super( "Locale selection abandoned" );

		abandoned	= _abandoned;
	}

	public boolean
	getAbandoned()
	{
		return( abandoned );
	}

	public String[]
	getValidCharsets()
	{
		return( valid_charsets );
	}

	public String[]
	getValidTorrentNames()
	{
		return( valid_names );
	}
}
