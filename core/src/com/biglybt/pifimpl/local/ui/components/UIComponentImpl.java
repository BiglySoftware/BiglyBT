/*
 * Created on 27-Apr-2004
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.pifimpl.local.ui.components;

/**
 * @author parg
 *
 */

import java.util.Properties;

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.pif.ui.components.UIComponent;
import com.biglybt.pif.ui.components.UIPropertyChangeEvent;
import com.biglybt.pif.ui.components.UIPropertyChangeListener;
import com.biglybt.pif.ui.components.UIComponent.RefreshListener;

public class
UIComponentImpl
	implements UIComponent
{
	private Properties	properties 	= new Properties();

	private CopyOnWriteList<UIPropertyChangeListener>	property_listeners	= new CopyOnWriteList<>();
	private CopyOnWriteList<RefreshListener>			refresh_listeners	= new CopyOnWriteList<>();


	protected
	UIComponentImpl()
	{
		properties.put( PT_ENABLED, Boolean.TRUE);
		properties.put( PT_VISIBLE, Boolean.TRUE);
	}

	@Override
	public void
	setEnabled(
		boolean		enabled )
	{
		setProperty( PT_ENABLED, Boolean.valueOf(enabled));
	}

	@Override
	public boolean
	getEnabled()
	{
		return(((Boolean)getProperty( PT_ENABLED )).booleanValue());
	}

	@Override
	public void
	setVisible(
		boolean		visible )
	{
		setProperty( PT_VISIBLE, Boolean.valueOf(visible));
	}

	@Override
	public boolean
	getVisible()
	{
		return(((Boolean)getProperty( PT_VISIBLE )).booleanValue());
	}

	@Override
	public void
	setProperty(
		final String	property_type,
		final Object	property_value )
	{
		final Object	old_value = property_type==PT_SELECTED?false:properties.get( property_type );

		properties.put( property_type, property_value );

		UIPropertyChangeEvent	ev = new
			UIPropertyChangeEvent()
			{
				@Override
				public UIComponent
				getSource()
				{
					return( UIComponentImpl.this );
				}

				@Override
				public String
				getPropertyType()
				{
					return( property_type );
				}

				@Override
				public Object
				getNewPropertyValue()
				{
					return( property_value );
				}

				@Override
				public Object
				getOldPropertyValue()
				{
					return( old_value );
				}
			};

		for ( UIPropertyChangeListener listener: property_listeners ){

			listener.propertyChanged( ev );
		}
	}

	@Override
	public Object
	getProperty(
		String		property_type )
	{
		return( properties.get( property_type ));
	}

	@Override
	public void
	addPropertyChangeListener(
		UIPropertyChangeListener	l )
	{
		property_listeners.add( l );
	}

	@Override
	public void
	removePropertyChangeListener(
		UIPropertyChangeListener	l )
	{
		property_listeners.remove(l);
	}
	
	public void
	refresh()
	{
		for ( RefreshListener l: refresh_listeners ){
			
			l.refresh();
		}
	}
	
	public void
	addRefreshListener(
		RefreshListener	l )
	{
		refresh_listeners.add( l );
	}
	
	public void
	removeRefreshListener(
		RefreshListener	l )
	{
		refresh_listeners.remove( l );
	}
}
