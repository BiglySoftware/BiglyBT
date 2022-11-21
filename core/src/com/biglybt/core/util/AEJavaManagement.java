/*
 * Created on Jan 15, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class
AEJavaManagement
{
	private static ThreadStuff	thread_stuff;
	private static MemoryStuff	memory_stuff;

	public static void
	initialise()
	{
		String thread_prop = System.getProperty( SystemProperties.SYSPROP_THREAD_MON_ENABLE, null );
		
		if ( thread_prop == null ){
			
			// not seeing any progress on https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8245768 
			// so setting linux default to disabled for the moment
			
			// thread_prop = ( Constants.isWindows || Constants.isOSX )?"1":"0";
			
			// Seems that using user-time on Linux (see SYSPROP_THREAD_MON_USERONLY) works around the bug so we switch
			// from disabling entirely on Linux to defaulting to user-time
			
			thread_prop = "1";
		}
		
		if ( thread_prop.equals( "0" )){
			
			Debug.outNoStack( "Thread monitoring disabled" );
			
		}else{
			
			try{
				thread_stuff = (ThreadStuff)Class.forName("com.biglybt.core.util.jman.AEThreadMonitor").newInstance();
	
			}catch( ClassNotFoundException e ){
	
			}catch( Throwable e ){
	
				e.printStackTrace();
			}
		}
		
		if ( System.getProperty( SystemProperties.SYSPROP_MEMORY_MON_ENABLE, "1" ).equals( "0" )){

			Debug.outNoStack( "Memory monitoring disabled" );

		}else{
			
			try{
				memory_stuff = (MemoryStuff)Class.forName("com.biglybt.core.util.jman.AEMemoryMonitor").newInstance();
	
			}catch( ClassNotFoundException e ){
	
			}catch( Throwable e ){
	
				e.printStackTrace();
			}
		}
	}

	public static long
	getThreadCPUTime()
	{
		if ( thread_stuff == null ){

			return( 0 );
		}

		return( thread_stuff.getThreadCPUTime());
	}

	public static List<String>
	getMemoryHistory()
	{
		if ( thread_stuff == null ){

			return( Collections.emptyList());
		}

		return( thread_stuff.getMemoryHistory());
	}
	
	public static void
	dumpThreads()
	{
		if ( thread_stuff == null ){

			return;
		}

		thread_stuff.dumpThreads();
	}

	public static void
	dumpThreads(
		IndentWriter writer )
	{
		if ( thread_stuff == null ){

			return;
		}

		thread_stuff.dumpThreads( writer );
	}

	public static String
	getThreadInfo(
		Thread		thread )
	{
		if ( thread_stuff == null ){

			return( null );
		}

		return( thread_stuff.getThreadInfo( thread ));
	}
	
	public static long
	getMaxHeapMB()
	{
		if ( memory_stuff == null ){

			return( 0 );
		}

		return( memory_stuff.getMaxHeapMB());
	}

	public static  long
	getJVMLongOption(
		String[]	options,
		String		prefix )
	{
		long	value = -1;

		for ( String option: options ){

			try{
				if ( option.startsWith( prefix )){

					String	val = option.substring( prefix.length());

					value = decodeJVMLong( val );
				}
			}catch( Throwable e ){

				Debug.out( "Failed to process option '" + option + "'", e );
			}
		}

		return( value );
	}

	public static String[]
	setJVMLongOption(
		String[]	options,
		String		prefix,
		long		val )
	{
		String new_option = prefix + encodeJVMLong( val );

		for (int i=0;i<options.length;i++){

			String option = options[i];

			if ( option.startsWith( prefix )){

				options[i] = new_option;

				new_option = null;
			}
		}

		if ( new_option != null ){

			String[] new_options = new String[options.length+1];

			System.arraycopy( options, 0, new_options, 0, options.length );

			new_options[options.length] = new_option;

			options = new_options;
		}

		return( options );
	}

	public static String[]
	removeJVMOption(
		String[]	options,
		String		prefix )
	{
		List<String> result = new ArrayList<>();
		
		for (int i=0;i<options.length;i++){

			String option = options[i];

			if ( !option.startsWith( prefix )){

				result.add( option );
			}
		}

		if ( result.size() < options.length ){
		
			return( result.toArray( new String[0] ));
			
		}else{

			return( options );
		}
	}
	
	public static  long
	decodeJVMLong(
		String		val )

		throws Exception
	{
		long	 mult = 1;

		char last_char = Character.toLowerCase( val.charAt( val.length()-1 ));

		if ( !Character.isDigit( last_char )){

			val = val.substring( 0, val.length()-1 );

			if ( last_char == 'k' ){

				mult	= 1024;

			}else if ( last_char == 'm' ){

				mult	= 1024*1024;

			}else if ( last_char == 'g' ){

				mult	= 1024*1024*1024;

			}else{

				throw( new Exception( "Invalid size unit '" + last_char + "'" ));
			}
		}

		return( Long.parseLong( val ) * mult );
	}

	public static String
	encodeJVMLong(
		long	val )
	{
		if ( val < 1024 ){

			return( String.valueOf( val ));
		}

		val = val/1024;

		if ( val < 1024 ){

			return( String.valueOf( val ) + "k" );
		}

		val = val/1024;

		if ( val < 1024 ){

			return( String.valueOf( val ) + "m" );
		}

		if ( val % 1024 == 0 ){
			
			val = val/1024;
	
			return( String.valueOf( val ) + "g" );
			
		}else{
			
			return( String.valueOf( val ) + "m" );
		}
	}

	public interface
	ThreadStuff
	{
		public long
		getThreadCPUTime();

		public List<String>
		getMemoryHistory();
		
		public String
		getThreadInfo(
			Thread		thread );
		
		public void
		dumpThreads();
		
		public void
		dumpThreads(
			IndentWriter	writer );
	}

	public interface
	MemoryStuff
	{
		public long
		getMaxHeapMB();
	}
}
