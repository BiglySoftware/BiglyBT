/*
 * Created on 28-Mar-2006
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

import com.biglybt.core.internat.MessageText;

public class
UUIDGenerator
{
		/**
		 * 128 bit UUID using random method
		 * @return
		 */

	public static synchronized byte[]
	generateUUID()
	{
		byte[]	bytes = new byte[16];

		RandomUtils.SECURE_RANDOM.nextBytes( bytes );

		return( bytes );
	}

		/**
		 * 129 byte random UUID formatted as standard 36 char hex and hyphen string
		 * @return
		 */

	public static String
	generateUUIDString()
	{
		byte[]	bytes = generateUUID();

		String	res = ByteFormatter.encodeString( bytes ).toLowerCase( MessageText.LOCALE_ENGLISH );

		return( res.substring(0,8) + "-" + res.substring(8,12) +
				"-" + res.substring(12,16) + "-" + res.substring(16,20) + "-" + res.substring( 20 ));
	}

	public static void
	main(
			String[]	args )
	{
		for (int i=0;i<100;i++){
			System.out.println( generateUUIDString());
		}
	}
}
