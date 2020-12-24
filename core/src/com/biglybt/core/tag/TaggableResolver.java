/*
 * Created on Mar 20, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.tag;

import java.util.List;

public interface
TaggableResolver
{
	public long
	getResolverTaggableType();

	public List<Taggable>
	getResolvedTaggables();

	public Taggable
	resolveTaggable(
		String		id );

	public String
	getDisplayName(
		Taggable	taggable );

	public void
	requestAttention(
		String		id );
	
	public void
	addLifecycleControlListener(
		LifecycleControlListener		l );
	
	public void
	removeLifecycleControlListener(
		LifecycleControlListener		l );

	interface
	LifecycleControlListener
	{
		public void
		canTaggableBeRemoved(
			Taggable		taggble )
		
			throws Exception;
	}
}
