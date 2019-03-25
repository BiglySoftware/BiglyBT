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

package com.biglybt.ui.swt;

import java.util.*;

import org.eclipse.swt.SWTError;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;

public class 
BrowserWrapperSWTFactory
{
	public static String BROWSER_KEY	= "BrowserWrapperSWTFactory.browser";

	private static CopyOnWriteList<BrowserProvider>	providers = new CopyOnWriteList<>();
	
	public static BrowserWrapper
	create(
		Composite		composite,
		int				style )
	{
		try{
			Map<String,Object> properties = new HashMap<>();
					
			for ( BrowserProvider provider: providers ){
				
				try{
					BrowserWrapper browser = provider.create( composite, style, properties);
					
					if ( browser != null ){
						
						return( browser );
					}
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
				
				Utils.disposeComposite( composite, false );
			}
			
			return( new BrowserWrapperSWT( composite, style ));

		}catch( SWTError error ){
			Control[] children = composite.getChildren();
			for (Control child : children) {
				if ( child.getData( BROWSER_KEY ) != null ){
					try {
						child.dispose();
					} catch (Throwable t) {

					}
				}
			}

			return( new BrowserWrapperFake( composite, style, error ));
		}
	}
	
	public static void
	registerProvider(
		BrowserProvider		provider )
	{
		synchronized( providers ){
			
			providers.add( provider );
		}
	}
	
	public static void
	unregisterProvider(
		BrowserProvider		provider )
	{
		synchronized( providers ){
			
			providers.remove( provider );
		}
	}
	
	public interface
	BrowserProvider
	{
		public BrowserWrapper
		create(
			Composite			composite,
			int					style,
			Map<String,Object>	properties );	
	}
}
