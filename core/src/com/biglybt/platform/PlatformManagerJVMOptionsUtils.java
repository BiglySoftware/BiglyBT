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

import java.util.*;

import com.biglybt.pif.platform.PlatformManagerException;

public class 
PlatformManagerJVMOptionsUtils
{
	final private static PlatformManager platform = PlatformManagerFactory.getPlatformManager();
	
	public static boolean
	canAccessOptions()
	{
		return( platform.hasCapability(	PlatformManagerCapabilities.AccessExplicitVMOptions));
	}
	
	public static String
	getProperty(
		String		name )
	
		throws PlatformManagerException
	{
		String[] options = platform.getExplicitVMOptions();
		
		String prefix = "-D" + name;
		
		for ( String option: options ){
			
			if ( option.startsWith( prefix )){
				
				int pos = option.indexOf( "=" );
				
				if ( pos != -1 ){
					
					String value = option.substring( pos+1 ).trim();
					
					return( value );
				}
			}
		}
		
		return( null );
	}
	
	public static String
	removeProperty(
		String		name )
	
		throws PlatformManagerException
	{
		String[] _options = platform.getExplicitVMOptions();
		
		List<String> options = new ArrayList<>( Arrays.asList( _options ));
		
		String prefix = "-D" + name;
		
		Iterator<String> it = options.iterator();
		
		String value = null;
		
		while( it.hasNext()){
			
			String option = it.next();
			
			if ( option.startsWith( prefix )){
				
				int pos = option.indexOf( "=" );
				
				if ( pos != -1 ){
					
					it.remove();
					
					value = option.substring( pos+1 ).trim();
				}
			}
		}
		
		if ( value != null ){
			
			platform.setExplicitVMOptions( options.toArray( new String[ options.size()]));
		}
		
		return( value );
	}
	
	public static String
	setProperty(
		String		name,
		String		value )
	
		throws PlatformManagerException
	{
		String existing = removeProperty( name );
		
		String[] _options = platform.getExplicitVMOptions();
		
		List<String> options = new ArrayList<>( Arrays.asList( _options ));
		
		String prefix = "-D" + name;

		options.add( prefix + "=" + value );
		
		platform.setExplicitVMOptions( options.toArray( new String[ options.size()]));
		
		return( existing );
	}
	
	public static Boolean
	getBooleanProperty(
		String		name )
	
		throws PlatformManagerException
	{
		String value = getProperty( name );
		
		if ( value == null ){
			
			return( null );
		}
		
		return( value.equalsIgnoreCase( "true" ));
	}
	
	public static Boolean
	setBooleanProperty(
		String		name,
		boolean		value )
	
		throws PlatformManagerException
	{
		String existing = setProperty( name, value?"true":"false" );
		
		if ( existing == null ){
			
			return( null );
		}
		
		return( existing.equalsIgnoreCase( "true" ));
	}
}
