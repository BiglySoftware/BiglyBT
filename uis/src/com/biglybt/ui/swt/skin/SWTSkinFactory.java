/*
 * Created on Jun 1, 2006 2:06:48 PM
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
package com.biglybt.ui.swt.skin;

import java.util.*;

/**
 * @author TuxPaper
 * @created Jun 1, 2006
 *
 */
public class SWTSkinFactory
{
	private static Map<String,SWTSkin>		skin_map = new HashMap<>();
	
	public static SWTSkin
	getInstance()
	{
		SWTSkin	result =  SWTSkin.getDefaultInstance();
		
		synchronized( skin_map ) {
			
			skin_map.put( result.getSkinID(), result );
		}
		
		return( result );
	}

	public static SWTSkin
	getNonPersistentInstance(
		ClassLoader classLoader,
		String 		skinPath,
		String 		mainSkinFile)
	{
		SWTSkin	result =  new SWTSkin(classLoader, skinPath, mainSkinFile);
		
		synchronized( skin_map ) {
			
			skin_map.put( result.getSkinID(), result );
		}
		
		return( result );
	}
	
	public static SWTSkin
	lookupSkin(
		String		id )
	{
		synchronized( skin_map ) {
			
			return( skin_map.get( id ));
		}
	}
}
