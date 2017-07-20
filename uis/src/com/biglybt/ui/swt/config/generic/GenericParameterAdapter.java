/*
 * Created on 16-Jan-2006
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

package com.biglybt.ui.swt.config.generic;

public class
GenericParameterAdapter
{
	public int
	getIntValue(
		String	key )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public int
	getIntValue(
		String	key,
		int		def )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public void
	setIntValue(
		String	key,
		int		value )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public boolean
	resetIntDefault(
		String	key )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public Boolean
	getBooleanValue(
		String	key )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public Boolean
	getBooleanValue(
		String		key,
		Boolean		def )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public void
	setBooleanValue(
		String		key,
		boolean		value )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public float
	getFloatValue(
		String		key )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public void
	setFloatValue(
		String		key,
		float		value )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public String
	getStringListValue(
		String		key )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public String
	getStringListValue(
		String		key,
		String		def )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public void
	setStringListValue(
		String		key,
		String		value )
	{
		throw( new RuntimeException( "Not implemented" ));
	}
	public void
	informChanged(
		boolean	value_is_changing_internally )
	{
	}
}
