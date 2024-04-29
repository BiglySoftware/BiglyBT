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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.biglybt.core.util.Debug;
import com.biglybt.core.vuzefile.VuzeFile;


public interface
Tag
	extends com.biglybt.pif.tag.Tag
{
	public static final String	TP_SETTINGS_REQUESTED	= "Settings Requested";	// Boolean
	public static final String	TP_CONSTRAINT_ERROR		= "Constraint Error";	// String

	public static final long	FL_NONE					= 0x00000000;
	public static final long	FL_IS_FILTER			= 0x00000001;
	public static final long	FL_IS_HIDDEN_WHEN_EMPTY	= 0x00000002;
	
		/**
		 * Unique type denoting this species of tag
		 * @return
		 */

	public TagType
	getTagType();

		/**
		 * Unique ID within this tag type
		 * @return
		 */

	public int
	getTagID();

		/**
		 * Unique across tag types and can be used to lookup by TagManager::lookuptagByUID
		 * @return
		 */

	public long
	getTagUID();

	public String
	getTagName(
		boolean	localize );

	public void
	setTagName(
		String		name )

		throws TagException;

	public int
	getTaggableTypes();

	public void
	setCanBePublic(
		boolean	can_be_public );

	public boolean
	canBePublic();

	public boolean
	isPublic();

	public void
	setPublic(
		boolean	pub );

	/**
	 * @return [ 
	 *     auto_add, 
	 *     auto_remove, 
	 *     auto_new_download_only (tag constraint)
	 * ]
	 */

	public boolean[]
	isTagAuto();

	public boolean
	isVisible();

	public void
	setVisible(
		boolean		visible );

	public default boolean
	isHiddenWhenEmpty()
	{
		return( getFlag( FL_IS_HIDDEN_WHEN_EMPTY ));
	}	
	
	public default void
	setHiddenWhenEmpty(
		boolean b )
	{
		setFlag( FL_IS_HIDDEN_WHEN_EMPTY, b );
	}
	
	public default void
	setFlag(
		long		flag,
		boolean		value )
	{	
		Debug.out( "Not supported" );
	}
	
	public default boolean
	getFlag(
		long		flag )
	{
		return( false );
	}
	
	public String
	getGroup();

	public void
	setGroup(
		String		group );

	public TagGroup
	getGroupContainer();
	
	public String
	getImageID();

	public void
	setImageID(
		String		id );

	public String
	getImageFile();

	public void
	setImageFile(
		String		id );
	
	public int
	getImageSortOrder();
	
	public void
	setImageSortOrder(
		int		order );
		
	public int[]
	getColor();

	public void
	setColor(
		int[]		rgb );

	public boolean
	isColorDefault();
	
	public void
	setColors(
		long[]		colors );
	
	public long[]
	getColors();
	
	public void
	addTaggableBatch(
		boolean		starts );
	
	public void
	addTaggable(
		Taggable	t );

	public void
	removeTaggable(
		Taggable	t );

	public int
	getTaggedCount();

	public Set<Taggable>
	getTagged();

	public boolean
	hasTaggable(
		Taggable	t );

	public default List<Tag>
	dependsOnTags()
	{
		return( Collections.emptyList());
	}
	
	public default String
	getStatus()
	{
		return( "" );
	}
	
	public void
	removeTag();

	public String
	getDescription();

	public void
	setDescription(
		String		desc );

	public void
	setTransientProperty(
		String		property,
		Object		value );

	public Object
	getTransientProperty(
		String		property );

	public long
	getTaggableAddedTime(
		Taggable	taggble );

	public void
	requestAttention();

	public VuzeFile
	getVuzeFile();
	
	public void
	addTagListener(
		TagListener	listener,
		boolean		fire_for_existing );

	public void
	removeTagListener(
		TagListener	listener );
}
