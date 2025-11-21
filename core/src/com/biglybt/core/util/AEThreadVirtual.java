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

public class 
AEThreadVirtual
{
	private static final boolean	available;
	
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
			
			name		= ThreadBuilder.getMethod( "name", String.class );
			start		= ThreadBuilder.getMethod( "start", Runnable.class );
			
			Object thread = ofv.invoke( null );
			
			name.invoke( thread, "VirtualThreadTest" );
			
			AESemaphore	sem = new AESemaphore( "VirtualThreadTest" );
			
			start.invoke( 
				thread,
				new Runnable()
				{
					public void
					run()
					{
						sem.release();
					}
				});
			
			if ( !sem.reserve( 5000 )){
				
				Debug.out("Virtual thread didn't seem to run");
				
				throw( new Exception());
			}
			
		}catch( Throwable e ){
			
			ofv			= null;
			name		= null;
			start		= null;
		}
		
		ofVirtual				= ofv;
		ThreadBuilder_name		= name;
		ThreadBuilder_start		= start;
		
		available = ofVirtual != null;
	}
	
	public static boolean
	areVirtualThreadsAvailable()
	{
		return( available );
	}
	
	public static void
	run(
		String		name,
		Runnable	r )
	{
		new AEThreadVirtual(name).start(r);
	}
	
	private String 	name;
	private volatile Object	thread;
	
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
	setName(
		String		_name )
	{
		name	= _name;
		
		if ( thread != null ){
			
			if ( thread instanceof AEThread2 ){
				
				((AEThread2)thread).setName(_name);
				
			}else{
				
				try{
					
					ThreadBuilder_name.invoke( thread, _name );
					
				}catch( Throwable e ){
					
				}
			}
		}
	}
	
	public void
	start(
		Runnable	runnable )
	{
		if ( ofVirtual != null ){
						
			try{
				
				thread = ofVirtual.invoke( null );
				
				ThreadBuilder_name.invoke( thread, name );
				
				ThreadBuilder_start.invoke( thread, runnable );
				
				return;
				
			}catch( Throwable e ){
				
			}
		}

		AEThread2 t = 
			new AEThread2( name, true )
			{
				public void
				run()
				{
					runnable.run();
				}
			};
			
		thread = t;
		
		t.start();
	}
	
	public void
	interrupt()
	{
		if ( thread != null ){
			
			if ( thread instanceof AEThread2 ){
				
				((AEThread2)thread).interrupt();
				
			}else{
				
				((Thread)thread).interrupt();
			}
		}
	}
}
