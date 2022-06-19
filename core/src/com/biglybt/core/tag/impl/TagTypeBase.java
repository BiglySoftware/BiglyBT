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


package com.biglybt.core.tag.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.*;
import com.biglybt.util.MapUtils;

public abstract class
TagTypeBase
	implements TagType, TagListener
{
	protected static final String	AT_COLOR_ID			= "col.rgb";

	private final int		tag_type;
	private final int		tag_type_features;
	private final String	tag_type_name;

	private static final int TTL_ADD 					= 1;
	private static final int TTL_TAG_MEMBERHIP_CHANGE 	= 2; 
	private static final int TTL_TAG_METADATA_CHANGE 	= 3; 
	private static final int TTL_REMOVE 				= 4;
	private static final int TTL_TYPE_CHANGE 			= 5;
	private static final int TTL_ATTENTION_REQUESTED 	= 6;

	private static final TagManagerImpl manager = TagManagerImpl.getSingleton();

	private final ListenerManager<TagTypeListener>	tt_listeners 	=
		ListenerManager.createManager(
			"TagTypeListeners",
			new ListenerManagerDispatcher<TagTypeListener>()
			{
				@Override
				public void
				dispatch(
					TagTypeListener		listener,
					int					type,
					Object				value )
				{
					if ( type == TTL_TYPE_CHANGE ){

						listener.tagTypeChanged( TagTypeBase.this );

					}else{

						final Tag		tag			= (Tag)value;
						final int		event_type;

						if ( type == TTL_ADD ){

							event_type	= TagTypeListener.TagEvent.ET_TAG_ADDED;

						}else if ( type == TTL_TAG_METADATA_CHANGE ){

							event_type	= TagTypeListener.TagEvent.ET_TAG_METADATA_CHANGED;

						}else if ( type == TTL_TAG_MEMBERHIP_CHANGE ){

							event_type	= TagTypeListener.TagEvent.ET_TAG_MEMBERSHIP_CHANGED;

						}else if ( type == TTL_REMOVE ){

							event_type	= TagTypeListener.TagEvent.ET_TAG_REMOVED;

						}else if ( type == TTL_ATTENTION_REQUESTED ){

							event_type	= TagTypeListener.TagEvent.ET_TAG_ATTENTION_REQUESTED;

						}else{

							return;
						}

						listener.tagEventOccurred(
							new TagTypeListener.TagEvent()
							{
								@Override
								public Tag getTag() {
									return( tag );
								}

								@Override
								public int getEventType() {
									return( event_type );
								}
							});
					}
				}
			});

	private final Map<Taggable,List<TagListener>>	tag_listeners = new HashMap<>();

	private Map<String,TagGroupImpl>	tag_groups = new HashMap<>();

	protected
	TagTypeBase(
		int			_tag_type,
		int			_tag_features,
		String		_tag_name )
	{
		tag_type			= _tag_type;
		tag_type_features	= _tag_features;
		tag_type_name		= _tag_name;
	}

	protected void
	addTagType()
	{
		if ( manager.isEnabled()){

			manager.addTagType( this );
		}
	}

	@Override
	public TagManagerImpl
	getTagManager()
	{
		return( manager );
	}

	protected TaggableResolver
	getResolver()
	{
		return( manager.getResolver( tag_type ));
	}
	
	protected Taggable
	resolveTaggable(
		String		id )
	{
		return( null );
	}

	protected void
	removeTaggable(
		TaggableResolver	resolver,
		Taggable			taggable )
	{
		synchronized( tag_listeners ){

			tag_listeners.remove( taggable );
		}
	}

	@Override
	public int
	getTagType()
	{
		return( tag_type );
	}

	@Override
	public String
	getTagTypeName(
		boolean	localize )
	{
		if ( localize ){

			if ( tag_type_name.startsWith( "tag." )){

				return( MessageText.getString( tag_type_name ));

			}else{

				return( tag_type_name );
			}
		}else{

			if ( tag_type_name.startsWith( "tag." )){

				return( tag_type_name );

			}else{

				return( "!" + tag_type_name + "!" );
			}
		}
	}

	@Override
	public boolean
	isTagTypeAuto()
	{
		return( true );
	}

	@Override
	public boolean
	isTagTypePersistent()
	{
		return( false );
	}

	@Override
	public long
	getTagTypeFeatures()
	{
		return( tag_type_features );
	}

	@Override
	public boolean
	hasTagTypeFeature(
		long feature )
	{
		return((tag_type_features&feature) != 0 );
	}

	protected void
	fireChanged()
	{
		tt_listeners.dispatch( TTL_TYPE_CHANGE, null );
	}

	@Override
	public Tag
	createTag(
		String 	name,
		boolean	auto_add )

		throws TagException
	{
		throw( new TagException( "Not supported" ));
	}

	@Override
	public void
	addTag(
		Tag	t )
	{
		((TagBase)t).initialized();

		tt_listeners.dispatch( TTL_ADD, t );
	}

	@Override
	public void
	removeTag(
		Tag	t )
	{
		((TagBase)t).destroy();

		tt_listeners.dispatch( TTL_REMOVE, t );

		manager.removeConfig( t );
	}

	public void
	requestAttention(
		Tag	t )
	{
		tt_listeners.dispatch( TTL_ATTENTION_REQUESTED, t );
	}

	@Override
	public int[]
	getColorDefault()
	{
		return( null );
	}

	protected void
	sync()
	{
		List<Tag>	tags = getTags();

		for ( Tag t: tags ){

			((TagBase)t).sync();
		}
	}

	protected void
	closing()
	{
		List<Tag>	tags = getTags();

		for ( Tag t: tags ){

			((TagBase)t).closing();
		}
	}

	@Override
	public Tag
	getTag(
		int	tag_id )
	{
		for ( Tag t: getTags()){

			if ( t.getTagID() == tag_id ){

				return( t );
			}
		}

		return( null );
	}

	@Override
	public Tag
	getTag(
		String	tag_name,
		boolean	is_localized )
	{
		for ( Tag t: getTags()){

			if ( t.getTagName( is_localized ).equals( tag_name )){

				return( t );
			}
		}

		return( null );
	}

	@Override
	public List<Tag>
	getTagsForTaggable(
		Taggable	taggable )
	{
		List<Tag>	result = new ArrayList<>();

		int taggable_type = taggable.getTaggableType();

		for ( Tag t: getTags()){

			if ( t.getTaggableTypes() == taggable_type ){

				if ( t.hasTaggable( taggable )){

					result.add( t );
				}
			}
		}

		return( result );
	}

	protected void
	fireMembershipChanged(
		Tag	t )
	{
		tt_listeners.dispatch( TTL_TAG_MEMBERHIP_CHANGE, t );
	}
	
	protected void
	fireMetadataChanged(
		Tag	t )
	{
		tt_listeners.dispatch( TTL_TAG_METADATA_CHANGE, t );
	}

	@Override
	public void
	removeTagType()
	{
		manager.removeTagType( this );
	}

	@Override
	public void
	addTagTypeListener(
		TagTypeListener	listener,
		boolean			fire_for_existing )
	{
		tt_listeners.addListener( listener );

		if ( fire_for_existing ){

			for ( final Tag t: getTags()){

				try{
					listener.tagEventOccurred(
						new TagTypeListener.TagEvent() {

							@Override
							public Tag getTag() {
								return( t );
							}

							@Override
							public int getEventType() {
								return( ET_TAG_ADDED );
							}
						});

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}

	@Override
	public void
	removeTagTypeListener(
		TagTypeListener	listener )
	{
		tt_listeners.removeListener( listener );
	}

	@Override
	public void
	taggableAdded(
		Tag			tag,
		Taggable	tagged )
	{
		List<TagListener> listeners;

		synchronized( tag_listeners ){

			listeners = tag_listeners.get( tagged );
		}

		if ( listeners != null ){

			for ( TagListener l: listeners ){

				try{
					l.taggableAdded(tag, tagged);

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}

		manager.taggableAdded( this, tag, tagged );
		
		TagGroup tg = tag.getGroupContainer();
		
		if ( tg != null && tg.getName() != null && tg.isExclusive()){
			
			List<Tag> tags = tg.getTags();
			
			for ( Tag t: tags ){
				
				if ( t != tag && t.hasTaggable( tagged )){
					
					boolean[] auto = t.isTagAuto();
					
					if ( !auto[0] ){
						
						t.removeTaggable( tagged );
					}
				}
			}
		}
	}

	@Override
	public void
	taggableSync(
		Tag			tag )
	{
		List<List<TagListener>> all_listeners = new ArrayList<>();

		synchronized( tag_listeners ){

			all_listeners.addAll( tag_listeners.values());
		}

		for ( List<TagListener> listeners: all_listeners ){

			for ( TagListener listener: listeners ){

				try{
					listener.taggableSync(tag);

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}

	@Override
	public void
	taggableRemoved(
		Tag			tag,
		Taggable	tagged )
	{
		List<TagListener> listeners;

		synchronized( tag_listeners ){

			listeners = tag_listeners.get( tagged );
		}

		if ( listeners != null ){

			for ( TagListener l: listeners ){

				try{
					l.taggableRemoved(tag, tagged);

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}

		manager.taggableRemoved( this, tag, tagged );
	}

	@Override
	public void
	addTagListener(
		Taggable		taggable,
		TagListener		listener )
	{
		synchronized( tag_listeners ){

			List<TagListener> listeners = tag_listeners.get( taggable );

			if ( listeners == null ){

				listeners = new ArrayList<>();

			}else{

				listeners = new ArrayList<>(listeners);
			}

			listeners.add( listener );

			tag_listeners.put( taggable, listeners );
		}
	}

	@Override
	public void
	removeTagListener(
		Taggable		taggable,
		TagListener		listener )
	{
		synchronized( tag_listeners ){

			List<TagListener> listeners = tag_listeners.get( taggable );

			if ( listeners != null ){

				listeners = new ArrayList<>(listeners);

				listeners.remove( listener );

				if ( listeners.size() == 0 ){

					tag_listeners.remove( taggable );

				}else{

					tag_listeners.put( taggable, listeners );
				}
			}
		}
	}

	protected Boolean
	readBooleanAttribute(
		TagBase		tag,
		String		attr,
		Boolean		def )
	{
		return( manager.readBooleanAttribute( this, tag, attr, def ));
	}

	protected boolean
	writeBooleanAttribute(
		TagBase	tag,
		String	attr,
		Boolean	value )
	{
		return( manager.writeBooleanAttribute( this, tag, attr, value ));
	}

	protected Long
	readLongAttribute(
		TagBase	tag,
		String	attr,
		Long	def )
	{
		return( manager.readLongAttribute( this, tag, attr, def ));
	}

	protected boolean
	writeLongAttribute(
		TagBase	tag,
		String	attr,
		Long	value )
	{
		return( manager.writeLongAttribute( this, tag, attr, value ));
	}

	protected String
	readStringAttribute(
		TagBase	tag,
		String	attr,
		String	def )
	{
		return( manager.readStringAttribute( this, tag, attr, def ));
	}

	/**
	 * @return Whether attribute was changed from existing value
	 */
	protected boolean
	writeStringAttribute(
		TagBase	tag,
		String	attr,
		String	value )
	{
		return manager.writeStringAttribute( this, tag, attr, value );
	}

	protected Map<String,Object>
	readMapAttribute(
		TagBase				tag,
		String				attr,
		Map<String,Object>	def )
	{
		return( manager.readMapAttribute( this, tag, attr, def ));
	}

	protected void
	writeMapAttribute(
		TagBase				tag,
		String				attr,
		Map<String,Object>	value )
	{
		manager.writeMapAttribute( this, tag, attr, value );
	}
	
	protected String[]
	readStringListAttribute(
		TagBase		tag,
		String		attr,
		String[]	def )
	{
		return( manager.readStringListAttribute( this, tag, attr, def ));
	}

	protected boolean
	writeStringListAttribute(
		TagBase		tag,
		String		attr,
		String[]	value )
	{
		return( manager.writeStringListAttribute( this, tag, attr, value ));
	}

	protected long[]
	readLongListAttribute(
		TagBase		tag,
		String		attr,
		long[]		def )
	{
		return( manager.readLongListAttribute( this, tag, attr, def ));
	}

	protected boolean
	writeLongListAttribute(
		TagBase		tag,
		String		attr,
		long[]	value )
	{
		return( manager.writeLongListAttribute( this, tag, attr, value ));
	}
	
 	protected class
 	TagGroupImpl
 		implements TagGroup
 	{
 		private final String name;
 		
 		private boolean		exclusive;
 		private File		ass_root;
 		private int[]		group_colour;
 		
 		private CopyOnWriteList<Tag>	tags = new CopyOnWriteList<>();
 		
 		private CopyOnWriteList<TagGroupListener>	listeners = new CopyOnWriteList<>();
 		
 		private Map<Object,Object>		user_data = new HashMap<>();
 		
 		private
 		TagGroupImpl(
 			String		_name )
 		{
 			name	= _name;
 		}
 		
 		private
 		TagGroupImpl(
 			String			_name,
 			TagGroupImpl	_basis )
 		{
 			name	= _name;
 			
			exclusive		= _basis.exclusive;
			ass_root		= _basis.ass_root;
			group_colour	= _basis.group_colour;
 		}
 		
 		protected String
 		getGroupID()
 		{
 			return( name==null?"<null>":Base32.encode( name.getBytes()));
 		}
 		
 		protected void
 		importState(
 			Map<String,Object>		map )
 		{
 			exclusive = MapUtils.getMapBoolean( map, "x", false );
 			
 			String ar = MapUtils.getMapString(map, "ar", null );
 			
 			if ( ar != null ){
 				
 				ass_root = FileUtil.newFile( ar );
 			}
 			
 			List<Number>	gc = (List<Number>)map.get( "gc" );
 			
 			if ( gc != null ){
 				
 				group_colour = new int[gc.size()];
 				
 				for ( int i=0;i<group_colour.length;i++){
 					
 					group_colour[i] = gc.get(i).intValue();
 				}
 			}
 		}
 		
 		protected Map<String,Object>
 		exportState()
 		{
 			Map<String,Object>	map = new HashMap<>();
 			
 			if ( exclusive ){
 				
 				map.put( "x", new Long(1));
 			}
 			
 			if ( ass_root != null ){
 				
 				map.put( "ar", ass_root.getAbsolutePath());
 			}
 			
 			if ( group_colour != null ){
 				
 				List<Integer> list = new ArrayList<Integer>( group_colour.length );
 				for ( int i: group_colour ){
 					list.add(i);
 				}
 				
 				map.put( "gc", list );
 			}
 			
 			return( map );
 		}
 		
 		public String
 		getName()
 		{
 			return( name );
 		}
 		
 		@Override
 		public void 
 		setName(
 			String name)
 		{
 			setTagGroupName( this, name );
 		}
 		
 		public boolean
 		isExclusive()
 		{
 			return( exclusive );
 		}
 		
 		public void
 		setExclusive(
 			boolean		b )
 		{
 			if ( b != exclusive ){
 				
 				exclusive = b;
 				
 				manager.tagGroupUpdated( TagTypeBase.this, this );
 				
 				groupChanged();
 			}
 		}
 		
		public File
		getRootMoveOnAssignLocation()
 		{
 			return( ass_root );
 		}
 		
 		public void
 		setRootMoveOnAssignLocation(
 			File		f )
 		{
 			if ( f == ass_root ){
 				
 				return;
 			}
 			
 			if ( f == null || ass_root == null || !f.equals( ass_root )){
 				
 				ass_root = f;
 				
 		 		for ( Tag tag: tags ){
 		 			
	 				if ( hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )){

	 					TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;
	 					
	 					if ( fl.supportsTagMoveOnAssign()){
	 						
	 						fl.setTagMoveOnAssignFolder( ass_root==null?null:FileUtil.newFile( ass_root, tag.getTagName( true )));
	 					}
	 				}
 				}
 				
 				manager.tagGroupUpdated( TagTypeBase.this, this );
 				
 				groupChanged();
 			}
 		}
 		
 		@Override
 		public int[] 
 		getColor()
 		{
 			return( group_colour );
 		}
 		
 		@Override
 		public void 
 		setColor(
 			int[] rgb)
 		{
 			group_colour = rgb;
 			
 			for ( Tag tag: tags ){
 				
 				if ( tag.isColorDefault()){
 				
 					tag.setColor( rgb );
 				}
 			}
 			
 			manager.tagGroupUpdated( TagTypeBase.this, this );
 			
 			groupChanged();
 		}
 		
 		@Override
 		public TagType 
 		getTagType()
 		{
 			return( TagTypeBase.this );
 		}
 		
 		public List<Tag>
 		getTags()
 		{
 			return( tags.getList());
 		}
 		
 		protected void
 		addTag(
 			Tag	tag )
 		{
 			if ( !tags.contains( tag )){
 				
	 			tags.add( tag );
	 			
	 			if ( ass_root != null ){
	 				
	 				if ( hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )){

	 					TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;
	 					
	 					if ( fl.supportsTagMoveOnAssign()){
	 						
	 						fl.setTagMoveOnAssignFolder( ass_root==null?null:FileUtil.newFile( ass_root, tag.getTagName( true )));
	 					}
	 				}
	 			}
	 			
	 			if ( tag.isColorDefault()){
	 				
	 				if ( group_colour != null ){
	 					
	 					tag.setColor( group_colour );
	 				}
	 			}
	 			
	 			for( TagGroupListener l: listeners ){
	 				
	 				try{
	 					l.tagAdded(this,tag);
	 					
	 				}catch( Throwable e ){
	 					
	 					Debug.out( e );
	 				}
	 			}
 			}
 		}
 		
 		protected void
 		removeTag(
 			Tag	tag )
 		{
 			if ( tags.contains( tag )){
 				
	 			tags.remove( tag );
	 			
	 			for( TagGroupListener l: listeners ){
	 				
	 				try{
	 					l.tagRemoved(this,tag);
	 					
	 				}catch( Throwable e ){
	 					
	 					Debug.out( e );
	 				}
	 			}
 			}
 		}
 		private void
 		groupChanged()
 		{
 			for( TagGroupListener l: listeners ){
 				
 				try{
 					l.groupChanged(this);
 					
 				}catch( Throwable e ){
 					
 					Debug.out( e );
 				}
 			}
 		}
 		
 		public void
 		addListener(
 			TagGroupListener	l,
 			boolean				fire_for_existing )
 		{
 			listeners.add( l );
 			
 			if ( fire_for_existing ){
 				
 				for ( Tag t: tags ){
 					
 					l.tagAdded( this, t );
 				}
 			}
 		}
 		
 		public void
 		removeListener(
 			TagGroupListener	l )
 		{
 			listeners.remove( l );
 		}
 		
 		public void
 		setUserData(
 			Object		key,
 			Object		data )
 		{
 			synchronized( user_data ){
				
 				user_data.put( key, data );
			}
 		}
 		
		public Object
 		getUserData(
 			Object		key )
 		{
 			synchronized( user_data ){
				
 				return( user_data.get( key ));
			}
 		}
 	}
 	
 	private void
 	setTagGroupName(
 		TagGroupImpl	old_group,
 		String			new_name )
 	{
 		String	old_name = old_group.getName();
 		
 		if ( old_name == new_name || ( old_name != null && old_name.equals( new_name ))){
 			
 			return;
 		}
 		
		List<Tag> tags;

		synchronized( this ){
 			
			tags = old_group.getTags();
			
 			if ( old_name != null ){
 				
 				tag_groups.remove( old_name );
 			}
 			
 			for ( Tag t: tags ){
 					
 				old_group.removeTag(t);
 			}
 			
 			if ( new_name != null ){
 				
 				TagGroupImpl new_group = tag_groups.get( new_name );
 				
 				if ( new_group == null ){
 					
 					new_group = new TagGroupImpl( new_name, old_group );
 										
 					tag_groups.put( new_name, new_group );

 					manager.tagGroupRenamed( this, old_group, new_group );
  				}
 			}
		}
		
			// need to do this outside the sync block as we don't want to be invoking listeners with lock held...
		
		if ( new_name != null ){
			
 			for ( Tag t: tags ){
 				 			
 					// this will add the tag to the group
 					
 				t.setGroup( new_name );
 			}
 		}
 	}
 	
 	protected void
 	setTagGroup(
 		Tag		tag,
 		String	old_name,
 		String	new_name )
 	{
 		if ( old_name == new_name || ( old_name != null && old_name.equals( new_name ))){
 			
 			return;
 		}
 		
 		synchronized( this ){
 			
 			if ( old_name != null ){
 				
 				TagGroupImpl tg = tag_groups.get( old_name );
 				
 				if ( tg != null ){
 					
 					tg.removeTag( tag );
 				}
 			}
 			
 			if ( new_name != null ){
 				
 				TagGroupImpl tg = tag_groups.get( new_name );
 				
 				if ( tg == null ){
 					
 					tg = new TagGroupImpl( new_name );
 										
 					tag_groups.put( new_name, tg );

 					manager.tagGroupCreated( this, tg, null );
  				}
 				
 				tg.addTag( tag );
 			}
 		}
 	}
 	
 	protected TagGroup
 	getTagGroup(
 		String		name )
 	{
 		if ( name == null ){
 			
 			return( new TagGroupImpl( null ));
 			
 		}else{
 			
 			synchronized( this ){
 				
 				TagGroupImpl result = tag_groups.get( name );
 				
 				if ( result == null ){
 					
 					result = new TagGroupImpl( name ); 					
 					
 					tag_groups.put( name, result );

 					manager.tagGroupCreated( this, result, null );
 				}
 				
 				return( result );
 			}
 		}	
 	}
 	
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( tag_type_name );

		try{
			writer.indent();

			manager.generate( writer, this );

			List<Tag>	tags = getTags();

			for ( Tag t: tags ){

				((TagBase)t).generate( writer );
			}

		}finally{

			writer.exdent();
		}
	}

	protected void
	generateConfig(
		IndentWriter		writer,
		TagBase				tag )
	{
		manager.generate( writer, this, tag );
	}
}
