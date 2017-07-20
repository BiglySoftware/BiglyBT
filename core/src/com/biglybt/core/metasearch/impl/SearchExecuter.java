/*
 * Created on May 6, 2008
 * Created by Paul Gardner
 *
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
 */

package com.biglybt.core.metasearch.impl;

import java.util.Map;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.ResultListener;
import com.biglybt.core.metasearch.SearchException;
import com.biglybt.core.metasearch.SearchParameter;
import com.biglybt.core.util.AEThread2;


public class
SearchExecuter
{
	Map				context;
	ResultListener 	listener;

	public
	SearchExecuter(
		Map				_context,
		ResultListener	_listener )
	{
		context		= _context;
		listener 	= _listener;
	}

	public void
	search(
		final Engine 			engine,
		final SearchParameter[] searchParameters,
		final String 			headers,
		final int				desired_max_matches )
	{
		new AEThread2( "MetaSearch: " + engine.getName() + " runner", true )
		{
			@Override
			public void
			run()
			{
				try{
					engine.search( searchParameters, context, desired_max_matches, -1, headers, listener );

				}catch( SearchException e ){
				}
			}
		}.start();
	}
}
