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

package com.biglybt.core.metasearch;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

public interface
MetaSearch
{
	public MetaSearchManager
	getManager();

	public Engine
	importFromBEncodedMap(
		Map<String,Object>		map )

		throws IOException;

	public Engine[]
	search(
		ResultListener 		listener,
		SearchParameter[] 	searchParameters,
		String				headers,
		int					max_per_engine );

	public Engine[]
  	search(
  		ResultListener 		listener,
  		SearchParameter[] 	searchParameters,
  		String				headers,
  		Map<String,String>	context,
  		int					max_per_engine );

	public Engine[]
	search(
		Engine[]			engine,
		ResultListener 		listener,
		SearchParameter[] 	searchParameters,
		String				headers,
		int					max_per_engine );

	public Engine[]
  	search(
  		Engine[]			engine,
  		ResultListener 		listener,
  		SearchParameter[] 	searchParameters,
  		String				headers,
  		Map<String,String>	context,
  		int					max_per_engine );

	public String
	getFUD();

	public Engine[]
	getEngines(
		boolean		active_only,
		boolean		ensure_up_to_date );

	public Engine
	getEngine(
		long		id );

	public Engine
	getEngineByUID(
		String		uid );

	public void
	addEngine(
		Engine 		engine );

	public Engine
	addEngine(
		long		id )

		throws MetaSearchException;

	public Engine
	createRSSEngine(
		String	name,
		URL		url )

		throws MetaSearchException;

	public void
	removeEngine(
		Engine 		engine );

	public int
	getEngineCount();

	public void
	enginePreferred(
		Engine		engine );

	public void
	exportEngines(
		File	to_file )

		throws MetaSearchException;

	public void
	addListener(
		MetaSearchListener		listener );

	public void
	removeListener(
		MetaSearchListener		listener );
}
