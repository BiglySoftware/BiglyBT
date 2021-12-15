/*
 * Created on Mar 23, 2013
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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.download.DownloadManagerStateFactory;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

public class
TagTypeSwarmTag
	extends TagTypeWithState
{
	private static final int[] color_default = { 205, 200, 240 };

	private final AtomicInteger	next_tag_id = new AtomicInteger(0);

	protected
	TagTypeSwarmTag()
	{
		super( TagType.TT_SWARM_TAG, TagFeature.TF_NONE, "tag.type.swarm" );

		addTagType();
		
		DownloadManagerStateFactory.addGlobalListener(
			new DownloadManagerStateAttributeListener() {

				@Override
				public void
				attributeEventOccurred(
					DownloadManager 	dm,
					String 				attribute,
					int 				event_type )
				{
					String[] tags = dm.getDownloadState().getListAttribute( DownloadManagerState.AT_SWARM_TAGS );
					
					if ( tags != null && tags.length > 0 ){
						
						for ( String tag: tags ){
							
							if ( TagUtils.isInternalTagName(tag)){
								
								continue;
							}
							
							TagSwarmTagImpl st = (TagSwarmTagImpl)getTag( tag, true );
							
							if ( st == null ){
								
								try{
									st = createTag( tag, true );
									
								}catch( Throwable e ){
									
									Debug.out( e );
								}
							}else{
								
								st.setLastSeenTime();
							}
						}
					}
				}
			}, DownloadManagerState.AT_SWARM_TAGS, DownloadManagerStateAttributeListener.WRITTEN );
	}

	@Override
	public boolean
	isTagTypeAuto()
	{
		return( false );
	}

	@Override
	public int[]
    getColorDefault()
	{
		return( color_default );
	}
	
	@Override
	public boolean 
	isTagTypePersistent()
	{
		return( true );
	}
	
	@Override
	public TagSwarmTagImpl
	createTag(
		String		name,
		boolean		auto_add )

		throws TagException
	{
		TagSwarmTagImpl new_tag = new TagSwarmTagImpl( this, next_tag_id.incrementAndGet(), name );

		if ( auto_add ){

			addTag( new_tag );
		}

		return( new_tag );
	}
	
	@Override
	protected Tag
	createTag(
		int						tag_id,
		Map<String,Object>		details )
	{
		TagSwarmTag new_tag = new TagSwarmTagImpl( this, tag_id, details );

		next_tag_id.set( Math.max( next_tag_id.get(), tag_id+1 ));

		return( new_tag );
	}
	
	static class
	TagSwarmTagImpl
		extends TagWithState
		implements TagSwarmTag
	{
		private static final String AT_SWARM_TAG_LAST_SEEN	= "swarmtag:last.seen";
		
		private 
		TagSwarmTagImpl(
			TagTypeBase		tag_type,
			int				id,
			String			name )
		{
			super( tag_type, id, name );
			
			writeLongAttribute( AT_SWARM_TAG_LAST_SEEN, SystemTime.getCurrentTime());
		}
		
		private 
		TagSwarmTagImpl(
			TagTypeBase				tag_type,
			int						id,
			Map<String,Object>		details )
		{
			super( tag_type, id, details );
		}
		
		@Override
		public int 
		getTaggableTypes() 
		{
			return( Taggable.TT_NONE );
		}
		
		@Override
		public void
		addTaggable(
			Taggable	t )
		{
			// not supported
		}
		
		@Override
		public void 
		removeTaggable(
			Taggable t)
		{
			// not supported
		}
		
		@Override
		protected boolean
		getCanBePublicDefault()
		{
			return( false );
		}
		
		@Override
		public long 
		getLastSeenTime()
		{
			return( readLongAttribute( AT_SWARM_TAG_LAST_SEEN, 0L ));
		}
		
		protected void
		setLastSeenTime()
		{
			writeLongAttribute( AT_SWARM_TAG_LAST_SEEN, SystemTime.getCurrentTime());
		}
	}
}
