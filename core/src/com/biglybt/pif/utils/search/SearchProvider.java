/*
 * Created on Jun 20, 2008
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
SearchProvider
{
		// properties

	public static final int PR_ID							= 0;	// getProperty only; Long
	public static final int PR_NAME							= 1;	// mandatory; String
	public static final int PR_ICON_URL						= 2;	// optional; String
	public static final int PR_DOWNLOAD_LINK_LOCATOR		= 3;	// optional; String
	public static final int PR_REFERER						= 4;	// optional; String
	public static final int PR_SUPPORTS_RESULT_FIELDS		= 5;	// optional; int[]
	public static final int PR_USE_ACCURACY_FOR_RANK		= 6;	// optional; Boolean

		// search parameters

	public static final String	SP_SEARCH_NAME			 	= "t";	// String; title of search for display purposes
	public static final String	SP_SEARCH_TERM			 	= "s";	// String
	public static final String	SP_MATURE				 	= "m";	// Boolean
	public static final String	SP_NETWORKS				 	= "n";	// String[]
	public static final String	SP_MIN_SEEDS				= "z";	// Long
	public static final String	SP_MIN_LEECHERS				= "l";	// Long
	public static final String	SP_MAX_AGE_SECS				= "a";	// Long

	public SearchInstance
	search(
		Map<String,Object>	search_parameters,
		SearchObserver		observer )

		throws SearchException;

	public Object
	getProperty(
		int			property );

	public void
	setProperty(
		int			property,
		Object		value );
}
