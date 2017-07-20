/*
 * Created on 02-May-2006
 * Created by Damokles
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

package com.biglybt.pifimpl.local.ipc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pifimpl.local.PluginInitializer;

/**
 * @author Damokles
 *
 */

public class
IPCInterfaceImpl
	implements IPCInterface
{
	private Object 				target_use_accessor;
	private String				plugin_class;
	private PluginInitializer	plugin_initializer;

		/**
		 * Constructor for real plugin based providers. Supports plugin unloading
		 *
		 * @param _plugin_initializer
		 * @param _target
		 */

	public
	IPCInterfaceImpl(
		PluginInitializer 	_plugin_initializer,
		Plugin 				_target )
	{
		plugin_initializer	= _plugin_initializer;

		target_use_accessor = _target;

		plugin_class		= _target.getClass().getName();
	}

		/**
		 * Constructor for non-plugin providers
		 * @param _target
		 */

	public
	IPCInterfaceImpl(
		Object 				_target )
	{
		target_use_accessor = _target;

		plugin_class		= _target.getClass().getName();
	}

	@Override
	public boolean
	canInvoke(
		String 		methodName,
		Object[] 	params )
	{
		try{
			Object	target = getTarget();

			Method mtd = getMethod( target, methodName, params );

			mtd.setAccessible( true );

			if ( mtd != null ){

				return( true );
			}
		}catch( Throwable e ){
		}

		return( false );
	}

	public boolean
	canInvoke(
		String 		methodName,
		Class<?>[] 	params )
	{
		try{
			Object	target = getTarget();

			Method mtd = getMethod( target, methodName, params );

			mtd.setAccessible( true );

			if ( mtd != null ){

				return( true );
			}
		}catch( Throwable e ){
		}

		return( false );
	}

	@Override
	public Object
	invoke(
		String 		methodName,
		Object[] 	params )

		throws IPCException
	{
		Object	target = getTarget();

		try{
			Method mtd = getMethod( target, methodName, params );

			mtd.setAccessible( true );

			return mtd.invoke(target, params);

		}catch( Throwable e ){

			if ( e instanceof InvocationTargetException ){

				if ( e.getCause() != null ){

					e = e.getCause();
				}
			}

			throw new IPCException(e);
		}
	}

	protected Method
	getMethod(
		Object		target,
		String 		methodName,
		Object[] 	params )

		throws Throwable
	{
		if ( params == null ){

			params = new Object[0];
		}

		Class[] paramTypes = new Class[params.length];

		for (int i=0;i<params.length;i++) {
			if (params[i] instanceof Boolean) {
				paramTypes[i] = boolean.class;
			} else if (params[i] instanceof Integer) {
				paramTypes[i] = int.class;
			} else if (params[i] instanceof Long) {
				paramTypes[i] = long.class;
			} else if (params[i] instanceof Float) {
				paramTypes[i] = float.class;
			} else if (params[i] instanceof Double) {
				paramTypes[i] = double.class;
			} else if (params[i] instanceof Byte) {
				paramTypes[i] = byte.class;
			} else if (params[i] instanceof Character) {
				paramTypes[i] = char.class;
			} else if (params[i] instanceof Short) {
				paramTypes[i] = short.class;
			} else
				paramTypes[i] = params[i].getClass();
		}

		Method mtd	= null;

		try{
			mtd = target.getClass().getDeclaredMethod( methodName,paramTypes );

		}catch( NoSuchMethodException e ){

			Method[]	methods = target.getClass().getMethods();

			for (int i=0;i<methods.length;i++){

				Method	method = methods[i];

				Class[] method_params = method.getParameterTypes();

				if ( method.getName().equals( methodName ) && method_params.length == paramTypes.length ){

					boolean	ok = true;

					for (int j=0;j<method_params.length;j++){

						Class	declared 	= method_params[j];
						Class	supplied	= paramTypes[j];

						if ( !declared.isAssignableFrom( supplied )){

							ok	= false;

							break;
						}
					}

					if ( ok ){

						mtd = method;

						break;
					}
				}
			}

			if ( mtd == null ){

				throw( e );
			}
		}

		return( mtd );
	}

	protected Method
	getMethod(
		Object		target,
		String 		methodName,
		Class<?>[] 	paramTypes )

		throws Throwable
	{
		if ( paramTypes == null ){

			paramTypes = new Class<?>[0];
		}

		Method mtd	= null;

		try{
			mtd = target.getClass().getDeclaredMethod( methodName,paramTypes );

		}catch( NoSuchMethodException e ){

			Method[]	methods = target.getClass().getMethods();

			for (int i=0;i<methods.length;i++){

				Method	method = methods[i];

				Class[] method_params = method.getParameterTypes();

				if ( method.getName().equals( methodName ) && method_params.length == paramTypes.length ){

					boolean	ok = true;

					for (int j=0;j<method_params.length;j++){

						Class	declared 	= method_params[j];
						Class	supplied	= paramTypes[j];

						if ( !declared.isAssignableFrom( supplied )){

							ok	= false;

							break;
						}
					}

					if ( ok ){

						mtd = method;

						break;
					}
				}
			}

			if ( mtd == null ){

				throw( e );
			}
		}

		return( mtd );
	}


	protected Object
	getTarget()

		throws IPCException
	{
		synchronized( this ){

			if ( target_use_accessor == null ){

				PluginInterface[] pis = plugin_initializer.getPlugins();

				for (int i=0;i<pis.length;i++){

					PluginInterface pi = pis[i];

					if ( pi.getPlugin().getClass().getName().equals( plugin_class )){

						target_use_accessor = pi.getPlugin();

						break;
					}
				}
			}

			if ( target_use_accessor == null ){

				throw( new IPCException( "Plugin has been unloaded" ));
			}

			return( target_use_accessor );
		}
	}

	public void
	unload()
	{
		synchronized( this ){

			target_use_accessor	= null;
		}
	}
}
