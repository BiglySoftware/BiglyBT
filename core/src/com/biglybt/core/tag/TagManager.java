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

import com.biglybt.core.vuzefile.VuzeFile;

public interface
TagManager
{
	public boolean
	isEnabled();

	public void
	setProcessingEnabled(
		boolean	enabled );

	public TagType
	getTagType(
		int			tag_type );

	public List<TagType>
	getTagTypes();

	public List<Tag>
	getTagsForTaggable(
		Taggable	taggable );

	public List<Tag>
	getTagsForTaggable(
		int			tag_type,
		Taggable	taggable );

	public List<Tag>
	getTagsForTaggable(
		int[]		tag_types,
		Taggable	taggable );
	
	public List<Tag>
	getTagsByName(
		String		name,
		boolean		is_localized );
	
	public void
	setTagPublicDefault(
		boolean	pub );

	public boolean
	getTagPublicDefault();

	public Tag
	lookupTagByUID(
		long	tag_uid );

	public List<Tag>
	lookupTagsByName(
		String		tag_name );
	
	public TaggableLifecycleHandler
	registerTaggableResolver(
		TaggableResolver	resolver );

	public TagConstraint
	compileConstraint(
		String		expression );
	
	public Tag
	duplicate(
		Tag				tag );
	
	public VuzeFile
	exportTags(
		List<Tag>		tags );
	
	public void
	addTagManagerListener(
		TagManagerListener		listener,
		boolean					fire_for_existing );

	public void
	removeTagManagerListener(
		TagManagerListener		listener );

	public void
	addTagFeatureListener(
		int						features,
		TagFeatureListener		listener );

	public void
	removeTagFeatureListener(
		TagFeatureListener		listener );

	public void
	addTaggableLifecycleListener(
		long						taggable_type,
		TaggableLifecycleListener	listener );

	public void
	removeTaggableLifecycleListener(
		long						taggable_type,
		TaggableLifecycleListener	listener );
}
