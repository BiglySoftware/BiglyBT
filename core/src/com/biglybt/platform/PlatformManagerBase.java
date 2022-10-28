/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.platform;

import java.util.Locale;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.platform.PlatformManagerException;

public class 
PlatformManagerBase
{
	protected void
	checkCanUseJVMOptions()
	
		throws PlatformManagerException
	{
		String vendor = System.getProperty( "java.vendor", "<unknown>" );

		String lc_vendor = vendor.toLowerCase( Locale.US );
		
		if (	!lc_vendor.startsWith( "sun " ) &&
				!lc_vendor.startsWith( "oracle " ) &&
				!lc_vendor.contains( "openjdk" ) &&
				!lc_vendor.startsWith( "azul ") && 
				!lc_vendor.startsWith( "Eclipse Adoptium" )){
			
			throw( new PlatformManagerException(
					MessageText.getString(
						"platform.jvmopt.sunonly",
						new String[]{ vendor })));
		}
	}
}
