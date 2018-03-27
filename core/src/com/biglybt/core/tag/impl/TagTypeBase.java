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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.core.util.ListenerManager;
import com.biglybt.core.util.ListenerManagerDispatcher;

public abstract class
TagTypeBase
	implements TagType, TagListener
{
	protected static final String	AT_COLOR_ID			= "col.rgb";

	private final int		tag_type;
	private final int		tag_type_features;
	private final String	tag_type_name;

	private static final int TTL_ADD 					= 1;
	private static final int TTL_CHANGE 				= 2;
	private static final int TTL_REMOVE 				= 3;
	private static final int TTL_TYPE_CHANGE 			= 4;
	private static final int TTL_ATTENTION_REQUESTED 	= 5;

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

						}else if ( type == TTL_CHANGE ){

							event_type	= TagTypeListener.TagEvent.ET_TAG_CHANGED;

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
	fireChanged(
		Tag	t )
	{
		tt_listeners.dispatch( TTL_CHANGE, t );
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

	protected void
	writeStringAttribute(
		TagBase	tag,
		String	attr,
		String	value )
	{
		manager.writeStringAttribute( this, tag, attr, value );
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
