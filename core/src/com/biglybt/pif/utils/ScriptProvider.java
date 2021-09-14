/*
 * Created on Nov 17, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.pif.utils;

import java.util.Map;

public interface
ScriptProvider
{
	public static final String	ST_JAVASCRIPT	= "javascript";
	public static final String	ST_PLUGIN		= "plugin";

	public String
	getProviderName();

	public String
	getScriptType();

	public default boolean
	canEvalBatch(
		String		script )
	{
		return( false );
	}
	
	public Object
	eval(
		String					script,
		Map<String,Object>		bindings )

		throws Exception;

	public interface
	ScriptProviderListener
	{
		public void
		scriptProviderAdded(
			ScriptProvider 	provider );

		public void
		scriptProviderRemoved(
			ScriptProvider 	provider );
	}
}
