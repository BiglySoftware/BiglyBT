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

package com.biglybt.core.metasearch.impl.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FieldMapping
{
	private final String postFilter;

	private final boolean postFilterRequiresSearchQuery;

	private String name;

	private int field;

	private Pattern postFilterPattern;

	public FieldMapping(String name, int field, String postFilter) {
		this.name = name;
		this.field = field;
		this.postFilter = postFilter;
		postFilterRequiresSearchQuery = postFilter != null
				&& postFilter.contains("%s");
	}

	public String getName() {
		return name;
	}

	public int getField() {
		return field;
	}

	public Pattern getPostFilterPattern(String searchQuery) {
		if (postFilter == null) {
			return null;
		}
		if (postFilterPattern == null || (postFilterRequiresSearchQuery
				&& !postFilterPattern.pattern().contains(
						"\\\\Q" + searchQuery + "\\\\E"))) {
			postFilterPattern = Pattern.compile(postFilterRequiresSearchQuery
					? postFilter.replaceAll("%s", Pattern.quote(searchQuery))
					: postFilter, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		}
		return postFilterPattern;
	}

	public String getPostFilter() {
		return postFilter;
	}
}
