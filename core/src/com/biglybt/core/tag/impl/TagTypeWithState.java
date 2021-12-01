/*
 * Created on Mar 22, 2013
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


package com.biglybt.core.tag.impl;

import java.util.List;
import java.util.Map;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagException;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.tag.TaggableResolver;
import com.biglybt.core.util.CopyOnWriteList;

public class
TagTypeWithState
	extends TagTypeBase
{
	private final CopyOnWriteList<Tag>	tags = new CopyOnWriteList<>();

	private TaggableResolver		resolver;

	protected
	TagTypeWithState(
		int			tag_type,
		int			tag_features,
		String		tag_name )
	{
		super( tag_type, tag_features, tag_name );
	}

	protected
	TagTypeWithState(
		int					tag_type,
		TaggableResolver	_resolver,
		int					tag_features,
		String				tag_name )
	{
		super( tag_type, tag_features, tag_name );

		resolver = _resolver;
	}

	@Override
	protected Taggable
	resolveTaggable(
		String		id )
	{
		if ( resolver == null ){

			return( super.resolveTaggable( id ));
		}

		return( resolver.resolveTaggable( id ));
	}

	protected TaggableResolver
	getResolver()
	{
		return( resolver );
	}

	@Override
	protected void
	removeTaggable(
		TaggableResolver	_resolver,
		Taggable			taggable )
	{
		if ( resolver == _resolver ){

			for ( Tag t: tags ){

				if ( t.hasTaggable( taggable )){
				
					t.removeTaggable( taggable );
				}
			}
		}

		super.removeTaggable(_resolver, taggable );
	}

	protected Tag
	createTag(
		int					id,
		Map<String,Object>	state )

		throws TagException
	{
		throw( new TagException( "Not supported" ));
	}
	
	@Override
	public void
	addTag(
		Tag		t )
	{
		tags.add( t );

		if ( t instanceof TagWithState ){

			getTagManager().tagCreated((TagWithState)t );
		}

		super.addTag( t );
	}

	@Override
	public void
	removeTag(
		Tag		t )
	{
		tags.remove( t );

		if ( t instanceof TagWithState ){

			getTagManager().tagRemoved((TagWithState)t );
		}

		super.removeTag( t );
	}

	@Override
	public List<Tag>
	getTags()
	{
		return( tags.getList());
	}
	
	@Override
	public int getTagCount(){
		return( tags.size());
	}
}
