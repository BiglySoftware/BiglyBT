/*
 * Created on 01-Feb-2006
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
DebugLight
{
	/**
	 * Used in environments where full debug may not be available
	 * @param e
	 */

	public static void
	printStackTrace(
		Throwable e )
	{
		try{
			Debug.printStackTrace( e );

		}catch( Throwable f ){

			e.printStackTrace();
		}
	}

	public static void
	out(
		String	str )
	{
		try{
			Debug.out( str );

		}catch( Throwable f ){

			System.out.println( str );
		}
	}
}
