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

package com.biglybt.ui.swt.pif;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.util.Debug;

public abstract class
UISWTViewMultiInstance
	implements UISWTViewEventListener
{
	private Map<UISWTView,ViewInstance>		views		= new HashMap<>();
	private boolean							destroyed	= false;
	
	@Override
	public boolean
	eventOccurred(
		UISWTViewEvent event )
	{
		UISWTView view = event.getView();
		
		ViewInstance instance;
		
		synchronized( views ){
			
			instance = views.get( view );
		}
		
		int type = event.getType();
		
		if ( instance == null && type != UISWTViewEvent.TYPE_CREATE ){
			
			return( false );
		}
		
		switch( type ){

			case UISWTViewEvent.TYPE_CREATE:{
				
				if ( instance != null ){
					
					return( false );
				}
				
				instance = createInstance( view );
				
				boolean added = false;
			
				try{
					synchronized( views ){
						
						if ( destroyed ){
							
							return( false );
						}
						
						if ( views.containsKey( view )){
							
							return( false );
						}
						
						views.put( view, instance );
						
						added = true;
					}
				}finally{
					
					if ( !added ){
						
						try{
							instance.destroy();
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
				
				break;
			}
			case UISWTViewEvent.TYPE_INITIALIZE:{
								
				instance.initialize((Composite)event.getData());
				
				break;
			}
			case UISWTViewEvent.TYPE_REFRESH:{
									
				instance.refresh( event );
				
				break;
			}
			
			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:{
				
				instance.updateLanguage();
				
				break;
			}
			case UISWTViewEvent.TYPE_DESTROY:{
									
				instance.destroy();
				
				break;
			}
		}
		
		return true;
	}
	
	public void
	destroy()
	{
		List<ViewInstance>	to_destroy;
		
		synchronized( views ){
	
			if ( destroyed ){
				
				return;
			}

			destroyed = true;
			
			to_destroy = new ArrayList<>( views.values());
		}
		
		for ( ViewInstance view: to_destroy ){
		
			try{
				view.destroy();
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	public abstract ViewInstance
	createInstance(
		UISWTView		view );
	
	public interface
	ViewInstance
	{
		public void
		initialize(
			Composite composite );
		
		public default void
		refresh(
			UISWTViewEvent		event )
		{
		}
		
		public default void
		updateLanguage()
		{
		}
		
		public default void
		destroy()
		{
		}
	}
}
