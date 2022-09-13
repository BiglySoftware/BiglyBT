/*
 * Created on 03-Aug-2004
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

package com.biglybt.core.diskmanager.cache;

/**
 * @author parg
 *
 */
public class
CacheFileManagerException
	extends Exception
{
	public static final int ET_OTHER				= 0;
	public static final int ET_FILE_OR_DIR_MISSING	= 1;
		
	private final CacheFile		file;
	private int					fail_index;

	private int		type		= ET_OTHER;

	public
	CacheFileManagerException(
		CacheFile	_file,
		String		_str )
	{
		super(_str);

		file		= _file;
	}

	public
	CacheFileManagerException(
		CacheFile	_file,
		String		_str,
		Throwable	_cause )
	{
		super( _str, _cause );

		file	= _file;
	}

	public
	CacheFileManagerException(
		CacheFile	_file,
		String		_str,
		Throwable	_cause,
		int			_fail_index )
	{
		super( _str, _cause );

		file		= _file;
		fail_index	= _fail_index;
	}

	public void
	setType(
		int		_type )
	{
		type = _type;
	}
	
	public int
	getType()
	{
		return( type );
	}
	
	public CacheFile
	getFile()
	{
		return( file );
	}

	public int
	getFailIndex()
	{
		return( fail_index );
	}
}
