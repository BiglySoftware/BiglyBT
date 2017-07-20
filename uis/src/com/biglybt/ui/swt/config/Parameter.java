/*
 * Created on 22-May-2004
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

package com.biglybt.ui.swt.config;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Control;
import com.biglybt.core.util.AEMonitor;

public abstract class
Parameter
	implements IParameter
{
	protected ConfigParameterAdapter config_adapter;

	protected  List	change_listeners;

	private static AEMonitor	class_mon	= new AEMonitor( "Parameter:class" );

	public Parameter(String sConfigID) {
		if (sConfigID != null) {
			config_adapter = new ConfigParameterAdapter( this, sConfigID );
		}
	}

	public boolean
	isInitialised()
	{
		return( true );
	}

	@Override
	public Control[]
	getControls()
	{
		return( new Control[]{ getControl() });
	}

 	public void
	addChangeListener(
		ParameterChangeListener	l )
	{
 		try{
 			class_mon.enter();

	 		if ( change_listeners == null ){

	 			change_listeners = new ArrayList(1);
	 		}

	  		change_listeners.add( l );

 		}finally{

 			class_mon.exit();
 		}
	}

	public void
	removeChangeListener(
		ParameterChangeListener	l )
	{
		try{
 			class_mon.enter();

 			change_listeners.remove(l);

		}finally{

 			class_mon.exit();
 		}
	}

	public void
	setEnabled(
		boolean	enabled )
	{
		for ( Control c: getControls()){

			c.setEnabled(enabled);
		}
	}

	public boolean
	isDisposed()
	{
		return( getControl().isDisposed());
	}

	public abstract void setValue(Object value);

	public Object getValueObject() {
		return null;
	}
}
