/*
 * Created on 13 Jun 2006
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

package com.biglybt.core.util;

public class
AEVerifierException
	extends Exception
{

	public static final int FT_SIGNATURE_MISSING	= 1;
	public static final int FT_SIGNATURE_BAD		= 2;

	private final int	failure_type;

	public
	AEVerifierException(
		int		type,
		String	str )
	{
		super( str );

		failure_type	= type;
	}

	public int
	getFailureType()
	{
		return( failure_type );
	}
}
