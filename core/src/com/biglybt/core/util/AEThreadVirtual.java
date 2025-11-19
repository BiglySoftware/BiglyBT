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

package com.biglybt.core.util;

import java.lang.reflect.Method;

public abstract class 
AEThreadVirtual
	implements Runnable
{
	private static final Method ofVirtual;
	private static final Method ThreadBuilder_name;
	private static final Method ThreadBuilder_start;
	
	static{
		Method ofv;
		Method name;
		Method start;
		
		try{
			ofv = Thread.class.getMethod( "ofVirtual", new Class[0] );
			
			Class<?> ThreadBuilder = Class.forName( "java.lang.Thread$Builder" );
			
			name	= ThreadBuilder.getMethod( "name", String.class );
			start	= ThreadBuilder.getMethod( "start", Runnable.class );
			
		}catch( Throwable e ){
			
			ofv		= null;
			name	= null;
			start	= null;
		}
		
		ofVirtual			= ofv;
		ThreadBuilder_name	= name;
		ThreadBuilder_start	= start;
	}
	
	private final String name;
	
	public
	AEThreadVirtual(
		String		_name )
	{
		name = _name;
	}
	
	public
	AEThreadVirtual(
		String		_name,
		boolean		_daemon )
	{
		name = _name;
		
		if ( !_daemon ){
			
			Debug.out( "Virtual threads are always daemon" );
		}
	}
	
	public void
	start()
	{
		if ( ofVirtual != null ){
						
			try{
				
				Object thread = ofVirtual.invoke( null );
				
				ThreadBuilder_name.invoke( thread, name );
				
				ThreadBuilder_start.invoke( thread, this );
				
				return;
				
			}catch( Throwable e ){
				
			}
		}

		new AEThread2( name, true )
			{
				public void
				run()
				{
					AEThreadVirtual.this.run();
				}
			}.start();
	}
	
	public abstract void
	run();
	
	
	public static void
	main(
		String[]	 args )
	{
		
	}
}
