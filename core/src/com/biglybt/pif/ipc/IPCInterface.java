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

package com.biglybt.pif.ipc;

/**
 * @author Damokles
 *
 */
public interface IPCInterface {

	/**
	 * This function will call the given method on the plugin.
	 *
	 * This function allows direct method calls to the plugin
	 * using Java Reflection API.
	 *
	 * Primitives like <code>int</code>, <code>boolean</code> need to be wrapped in their
	 * Objects (int -> Integer).</p>
	 *
	 * Results will be returned as Object and can be classcasted.
	 *
	 * <p>
	 *
	 * <b>WARNING</b>: only call Methods that use Java or client Classes
	 * 			the use of custom classes may cause problems.
	 *
	 * <p>
	 *
	 * Examples:
	 * <p>
	 *
	 * 1.
	 * Plugin has method
	 * <code>int add (int x, int y);</code>
	 *
	 * <pre>
	 * int result = ((Integer)invoke ("add", new Object[] {Integer.valueOf(10),Integer.valueOf(5)}).intValue();
	 * //result (15)
	 * </pre>
	 *
	 * 2. Plugin has method
	 * <code>String randomize (String x);</code>
	 *
	 * <pre>
	 * String result = (String)invoke("randomize", new Object[]{"foobar"});
	 * //result ("bfaoro")
	 * </pre>
	 *
	 *
	 * @param methodName the name of the Methods to be called
	 * @param params Parameters of the Method
	 * @return returns the result of the method
	 */
	public Object invoke (String methodName, Object[] params) throws IPCException;

	/**
	 * Test for existance of IPC method - params as above
	 * @param methodName
	 * @param params
	 * @return
	 */

	public boolean canInvoke( String methodName, Object[] params );
}
