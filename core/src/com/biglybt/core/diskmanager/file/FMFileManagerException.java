/*
 * File    : FMFileManagerException.java
 * Created : 12-Feb-2004
 * By      : parg
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

package com.biglybt.core.diskmanager.file;

/**
 * @author parg
 *
 */

public class
FMFileManagerException
	extends Exception
{
	public static final int	OP_OPEN					= 1;
	public static final int	OP_CLOSE				= 2;
	public static final int	OP_READ					= 3;
	public static final int	OP_WRITE				= 4;
	public static final int	OP_OTHER				= 5;
	
	public static final int ET_OTHER				= 0;
	public static final int ET_FILE_OR_DIR_MISSING	= 1;
	
	private final int		operation;
		
	private int		type		= ET_OTHER;
	private boolean recoverable = true;

	public
	FMFileManagerException(
		int			op,
		String		str )
	{
		super(str);
		
		operation = op;
	}

	public
	FMFileManagerException(
		int			op,
		String		str,
		Throwable	cause )
	{
		super( str, cause );
		
		operation = op;
	}

	public int
	getOperation()
	{
		return( operation );
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
	
	public void
	setRecoverable(
		boolean		_recoverable )
	{
		recoverable	= _recoverable;
	}

	public boolean
	isRecoverable()
	{
		return( recoverable );
	}
}
