/*
 * Created on Oct 13, 2008
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


package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProviderAdapter;

public class
SpeedManagerLogger
{
	private static String									prefix;
	private static SpeedManagerAlgorithmProviderAdapter		adapter;

	protected static void
	setAdapter(
		String										_prefix,
		SpeedManagerAlgorithmProviderAdapter		_adapter )
	{
		prefix	= _prefix;
		adapter = _adapter;
	}

	public static void
	log(
		String	str )
	{
		if ( adapter != null ){

			adapter.log( prefix + ": " + str );
		}
	}

	public static void
	trace(
		String	str )
	{
	}
}
