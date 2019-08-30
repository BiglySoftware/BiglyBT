/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.launcher;

import java.lang.reflect.Method;

public class Launcher {

	private static boolean done;
	
	public static void launch(Class MainClass,String[] args)
	{
		done = true;
		
		try{
			Method main = MainClass.getDeclaredMethod( "main", String[].class );
			
			main.setAccessible(true);
			
			main.invoke(null, new Object[]{ args });
			
		}catch ( Exception e ){
			
			System.err.println("Launch failed");
			
			e.printStackTrace();
			
			System.exit(1);
		}
	}


	public static boolean checkAndLaunch(Class MainClass,String[] args)
	{
		if( done ){
			
			return false;
		}
		
		launch(MainClass, args);
		
		return true;
	}
}
