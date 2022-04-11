/*
 * Created on Mar 9, 2017
 * Created by Paul Gardner
 *
 * Copyright 2017 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.subs;

import java.util.List;

import com.biglybt.core.metasearch.FilterableResult;
import com.biglybt.core.subs.impl.SubscriptionResultFilterImpl;

public interface
SubscriptionResultFilter
{
	public static SubscriptionResultFilter
	getTransientFilter()
	{
		return( new SubscriptionResultFilterImpl());
	}
	
	public long
	getMinSize();

	public void
	setMinSize(
		long	size );
	
	public long
	getMaxSize();

	public void
	setMaxSize(
		long	size );
	
	public long
	getMinSeeds();
	
	public void
	setMinSeeds(
		long	min );
	
	public long
	getMaxAgeSecs();
	
	public void
	setMaxAgeSecs(
		long	secs );
	
	public String[]
	getWithWords();

	public void
	setWithWords(
		String[]	words );
	
	public String[]
	getWithoutWords();

	public void
	setWithoutWords(
		String[]	words );
	
	public List<Subscription>
	getDependsOn();
	
	public long
	getDependenciesVersion();
	
	public void
	save()

		throws SubscriptionException;
	
	public boolean
	isActive();
	
	public boolean
	isFiltered(
		FilterableResult	result );
	
	public String
	getString();
	
}
