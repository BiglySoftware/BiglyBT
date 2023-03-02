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

public interface
TagFeatureProperties
{
	public static final String	PR_TRACKERS 			= "trackers";			// string list
	public static final String	PR_UNTAGGED 			= "untagged";			// boolean
	public static final String	PR_TRACKER_TEMPLATES 	= "tracker_templates";	// string list
	public static final String	PR_CONSTRAINT		 	= "constraint";			// string list

	public static final int		PT_STRING_LIST	= 1;
	public static final int		PT_BOOLEAN		= 2;
	public static final int		PT_LONG			= 3;

	public TagProperty[]
	getSupportedProperties();

	public TagProperty
	getProperty(
		String		name );

	public String
	getPropertiesString();
	
	public interface
	TagProperty
	{
		public Tag
		getTag();

		public int
		getType();

		public String
		getName(
			boolean	localize );

		public void
		setStringList(
			String[]	value );

		public String[]
		getStringList();

		public void
		setBoolean(
			Boolean		value );

		public Boolean
		getBoolean();

		public void
		setLong(
			Long		value );

		public Long
		getLong();

		public boolean
		isEnabled();
		
		public void
		setEnabled(
			boolean	enabled );
		
		public String
		getString();

		public void
		addListener(
			TagPropertyListener		listener );

		public void
		removeListener(
			TagPropertyListener		listener );

		public void
		syncListeners();
		
		public String[]
		explainTaggable(
			Taggable	taggable );
	}

	public interface
	TagPropertyListener
	{
		public void
		propertyChanged(
			TagProperty		property );

		public void
		propertySync(
			TagProperty		property );
	}
}
