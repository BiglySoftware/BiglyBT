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
import java.time.Duration;
import java.util.function.Consumer;

public class 
AEThreadVirtual
{
	private static final boolean DISABLE_VIRTUAL_THREADS = false;

	private static final boolean	available;
		
	private static final boolean	less_pinning = Constants.isJava24OrHigher;
	
		// before Java24 we can use -Djdk.tracePinnedThreads=full to get logging
	
	private static final boolean TRACE_PINNING	= false && Constants.isCVSVersion() && less_pinning;
	
	
	private static final Method ofVirtual;
	private static final Method ThreadBuilder_name;
	private static final Method ThreadBuilder_start;
	
	static{
		
		Method ofv;
		Method name;
		Method start;

		if ( DISABLE_VIRTUAL_THREADS ){
			
			ofv			= null;
			name		= null;
			start		= null;
			
		}else{
			
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
				
				if ( TRACE_PINNING ){
				
					AEThread2.createAndStartDaemon("",()->
						{
							try{
								Class<?> RecordingStream	= Class.forName( "jdk.jfr.consumer.RecordingStream" );
								Class<?> RecordedEvent		= Class.forName( "jdk.jfr.consumer.RecordedEvent" );
								Class<?> EventSettings		= Class.forName( "jdk.jfr.EventSettings" );
				
								Object recording_stream = RecordingStream.newInstance();
								
								Object event_settings = RecordingStream.getMethod( "enable", String.class ).invoke(recording_stream, "jdk.VirtualThreadPinned" );
								
								EventSettings.getMethod( "withStackTrace" ).invoke(event_settings );
								
								Method RecordedEvent_getStackTrace = RecordedEvent.getMethod( "getStackTrace" );
								
								Consumer<Object> cons = (ev)->{
									try{
										System.out.println( "Thread Pinned: " + RecordedEvent_getStackTrace.invoke( ev  ));
										
									}catch( Throwable e ){
										
										Debug.out( e );
									}
								};
								
								RecordingStream.getMethod( "onEvent", String.class, Consumer.class ).invoke(
										recording_stream, "jdk.VirtualThreadPinned", cons );
								
								
								RecordingStream.getMethod( "setMaxAge", Duration.class ).invoke(recording_stream, Duration.ofSeconds( 5 ));
								
								RecordingStream.getMethod( "startAsync" ).invoke(recording_stream );
								
							}catch( Throwable e ){
								
								
								Debug.out( e );
							}
						});
				}

			}catch( Throwable e ){
				
				ofv			= null;
				name		= null;
				start		= null;
			}
		}
		
		ofVirtual				= ofv;
		ThreadBuilder_name		= name;
		ThreadBuilder_start		= start;
		
		available = ofVirtual != null;
	}
	
	/**
	 * Use if internal code where pinning caused by synchronized methods in absent/limited
	 * @return
	 */
	
	public static boolean
	areBasicVirtualThreadsAvailable()
	{
		return( available );
	}
	
	/**
	 * From Java 24 the issues with pinning caused by synchronized methods are very much reduced so
	 * use this in more liberal circumstances
	 * @return
	 */
	
	public static boolean
	areBetterVirtualThreadsAvailable()
	{
		return( available && less_pinning );
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
	
	public static void
	main(
		String[]	args )
	{
		while( true ){
			try{
				Thread.sleep(1000);
			}catch( Throwable e ){
				
			}
		}
	}
}
