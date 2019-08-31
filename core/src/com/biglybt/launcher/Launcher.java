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

	private final static String  OSName 		= System.getProperty("os.name");
	private final static boolean isOSX			= OSName.toLowerCase().startsWith("mac os");

	private static volatile boolean done;
	
	public static void launch(Class MainClass,String[] args)
	{
		done = true;
		
			// don't change name of this thread, it is used in CoreImpl during closedown to detect
			// 
		Thread.currentThread().setName( "Launcher::bootstrap" );
		
		Runnable runner = 
			new Runnable()
			{
				public void
				run()
				{
			
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
			};
			
		if ( isOSX ){
			
				// have to stay on the initial thread
			
			runner.run();
			
		}else{
			
				// have to get off the initial thread, at least on Win 10, due to launcher thread remaining alive
			
			new Thread( runner ).start();
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
