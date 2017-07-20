/*
 * Created on Jun 30, 2009
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


package com.biglybt.pif.utils.search;

import java.util.Map;

public interface
SearchInitiator
{
	public static final String PR_SEARCH_TERM	= "search_term";	// search expression
	public static final String PR_MATURE		= "mature";			// "true" or "false"

	public SearchProvider[]
	getProviders();

	public Search
	createSearch(
		SearchProvider[]	providers,
		Map<String,String>	properties,
		SearchListener		listener )

		throws SearchException;

		/**
		 * Convenience method for remote invocation
		 * @param provider_ids
		 * @param properties
		 * @return
		 * @throws SearchException
		 */

	public Search
	createSearch(
		String				provider_ids,	// comma separated list
		String				properties )	// name=value, comma separated

		throws SearchException;
}
