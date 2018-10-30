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

public interface
TagTypeListener
{
		/**
		 * General change not specific to one tag
		 */

	public void
	tagTypeChanged(
		TagType		tag_type );

	public void
	tagEventOccurred(
		TagEvent			event );

	public interface
	TagEvent
	{
		public int ET_TAG_ADDED						= 0;
		public int ET_TAG_MEMBERSHIP_CHANGED		= 1;
		public int ET_TAG_REMOVED					= 2;
		public int ET_TAG_ATTENTION_REQUESTED		= 3;
		public int ET_TAG_METADATA_CHANGED			= 4;
		
		public Tag
		getTag();

		public int
		getEventType();
	}
}
