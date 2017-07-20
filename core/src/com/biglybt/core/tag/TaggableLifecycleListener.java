/*
 * Created on Sep 4, 2013
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
TaggableLifecycleListener
{
	public void
	initialised(
		List<Taggable>	current_taggables );

	public void
	taggableCreated(
		Taggable		taggable );

	public void
	taggableDestroyed(
		Taggable		taggable );

	/**
	 * Currently only implemented for manual_download tag changes
	 * @param tag_type
	 * @param tag
	 * @param taggable
	 */
	public void
	taggableTagged(
		TagType			tag_type,
		Tag				tag,
		Taggable		taggable );

	/**
	 * Currently only implemented for manual_download tag changes
	 * @param tag_type
	 * @param tag
	 * @param taggable
	 */
	public void
	taggableUntagged(
		TagType			tag_type,
		Tag				tag,
		Taggable		taggable );
}
