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
TagType
{
	public static final int TT_DOWNLOAD_CATEGORY	= 1;
	public static final int TT_DOWNLOAD_STATE		= 2;
	public static final int TT_DOWNLOAD_MANUAL		= 3;
	public static final int TT_PEER_IPSET			= 4;
	public static final int TT_DOWNLOAD_INTERNAL	= 5;
	public static final int TT_TAG_SUGGESTION 		= 6;
	public static final int TT_SWARM_TAG	 		= 7;

		/**
		 * Unique type denoting this species of tag
		 * @return
		 */

	public int
	getTagType();

	public String
	getTagTypeName(
		boolean		localize );

	public boolean
	isTagTypeAuto();

	public boolean
	isTagTypePersistent();

	public long
	getTagTypeFeatures();

	public boolean
	hasTagTypeFeature(
		long		feature );

	public Tag
	createTag(
		String		name,
		boolean		auto_add )

		throws TagException;

	public void
	addTag(
		Tag	t );

	public void
	removeTag(
		Tag	t );

	public Tag
	getTag(
		int	tag_id );

	public Tag
	getTag(
		String		tag_name,
		boolean		is_localized );

	public int
	getTagCount();
	
	public List<Tag>
	getTags();

	public List<Tag>
	getTagsForTaggable(
		Taggable	taggable );

	public void
	removeTagType();

	public TagManager
	getTagManager();

	public int[]
	getColorDefault();

	public void
	addTagTypeListener(
		TagTypeListener		listener,
		boolean				fire_for_existing );

	public void
	removeTagTypeListener(
		TagTypeListener		listener );

		/**
		 * taggable-specific listneer for this tag-type
		 * @param taggable
		 * @param listener
		 */

	public void
	addTagListener(
		Taggable		taggable,
		TagListener		listener );

	public void
	removeTagListener(
		Taggable		taggable,
		TagListener		listener );
}
