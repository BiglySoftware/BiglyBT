/*
 * Created on Mar 21, 2013
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
import java.util.*;

import com.biglybt.activities.LocalActivityManager;
import com.biglybt.activities.LocalActivityManager.LocalActivityCallback;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.util.*;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.util.MapUtils;

public abstract class
TagWithState
	extends TagBase
{
	private static final String TP_KEY = "TagWithState:tp_key";

	private final CopyOnWriteSet<Taggable>	objects = new CopyOnWriteSet<>(true);

	private final String	TP_KEY_TAG_ADDED_TIME;

	private TagFeatureNotifications	tag_notifications;

	private boolean	removed;

	public
	TagWithState(
		TagTypeBase			tt,
		int					tag_id,
		String				name )
	{
		super( tt, tag_id, name );

		TP_KEY_TAG_ADDED_TIME = "ta:" + getTagUID();

		if ( tt.hasTagTypeFeature( TagFeature.TF_NOTIFICATIONS )){

			tag_notifications = (TagFeatureNotifications)this;
		}
	}

	protected
	TagWithState(
		TagTypeBase			tt,
		int					tag_id,
		Map					map )
	{
		super( tt, tag_id, MapUtils.getMapString( map, "n", "" ));

		TP_KEY_TAG_ADDED_TIME = "ta:" + getTagUID();

		if ( tt.hasTagTypeFeature( TagFeature.TF_NOTIFICATIONS )){

			tag_notifications = (TagFeatureNotifications)this;
		}

		if ( map != null ){

			List<byte[]> 	list 	= (List<byte[]>)map.get( "o" );
			List<Map> 		props 	= (List<Map>)map.get( "p" );

			if ( list != null ){

				int	pos = 0;

				for ( byte[] b: list ){

					try{
						String id = new String( b, "UTF-8" );

						Taggable taggable = tt.resolveTaggable( id );

						if ( taggable != null ){

							if ( props != null ){

								Long time_added = (Long)props.get(pos).get("a");

								if ( time_added != null ){

									synchronized( TP_KEY ){

										Map all_props = (Map)taggable.getTaggableTransientProperty( TP_KEY );

										if ( all_props == null ){

											all_props = new HashMap();
										}

										all_props.put( TP_KEY_TAG_ADDED_TIME, time_added );

										taggable.setTaggableTransientProperty( TP_KEY, all_props );
									}
								}
							}

							objects.add( taggable );
						}
					}catch( Throwable e ){

						Debug.out( e );
					}

					pos++;
				}
			}
		}
	}

	protected void
	exportDetails(
		VuzeFile	vf,
		Map			map,
		boolean		do_contents )
	{
		exportDetails( map, do_contents );
		
		if ( getTagType().hasTagTypeFeature( TagFeature.TF_PROPERTIES )){
			
			TagFeatureProperties tfp = (TagFeatureProperties)this;
	
			TagProperty prop = tfp.getProperty( TagFeatureProperties.PR_TRACKER_TEMPLATES );
	
			if ( prop != null ){
	
				String[] bits = prop.getStringList();
	
				if ( bits != null && bits.length > 0 ){
	
					Map<String,List<List<String>>> templates = TrackersUtil.getInstance().getMultiTrackers();
					
					for ( String bit: bits ){
						
						String[] temp = bit.split( ":" );
	
						String t_name = temp[1];
	
						List<List<String>> template_trackers = templates.get( t_name );
						
						if ( template_trackers != null ){
							
							Map tt_map = new HashMap();
							
							tt_map.put( "name", t_name );
							
							tt_map.put( "template", template_trackers );
							
							vf.addComponent( VuzeFileComponent.COMP_TYPE_TRACKER_TEMPLATE, tt_map );
						}
					}
				}
			}
		}
		
		String image_file = getImageFile();
		
		if ( image_file != null ){
		
			try{
				
				File file = FileUtil.newFile( image_file );
				 
				byte[] bytes = FileUtil.readFileAsByteArray( file );
				
				if ( bytes != null ){
					
					map.put( "_img_file", file.getName());
					
					map.put( "_img_bytes", bytes );
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	protected void
	exportDetails(
		Map			map,
		boolean		do_contents )
	{
		MapUtils.setMapString( map, "n", getTagNameRaw());

		if ( do_contents ){

			Iterator<Taggable> it = objects.iterator();

			List<byte[]> 	l = new ArrayList<>(objects.size());
			List<Map> 		p = new ArrayList<>(objects.size());

			while( it.hasNext()){

				try{
					Taggable taggable = it.next();

					String id = taggable.getTaggableID();

					if ( id != null ){

						l.add( id.getBytes( "UTF-8" ));

						Map all_props = (Map)taggable.getTaggableTransientProperty( TP_KEY );

						Map props = new HashMap();

						if ( all_props != null ){

							Long time_added = (Long)all_props.get( TP_KEY_TAG_ADDED_TIME );

							if ( time_added != null ){

								props.put( "a", time_added );
							}
						}

						p.add( props );

					}else{

						// Get this when the taggable is a download that has lost its torrent
						// Debug.out( "No taggable ID for " + taggable );
					}
				}catch( Throwable e ){

					Debug.out( e );
				}
			}

			map.put( "o", l );
			map.put( "p", p );
		}
	}

	@Override
	public void
	setTagName(
		String name )

		throws TagException
	{
		super.setTagName( name );

		getManager().tagChanged( this );
	}

	@Override
	public long
	getTaggableAddedTime(
		Taggable taggble )
	{
		Map all_props = (Map)taggble.getTaggableTransientProperty( TP_KEY );

		if ( all_props != null ){

			Long added_time = (Long)all_props.get( TP_KEY_TAG_ADDED_TIME );

			if ( added_time != null ){

				return( added_time*1000 );
			}
		}

		return( -1 );
	}

	@Override
	public void
	addTaggable(
		Taggable	t )
	{
		if ( removed ){

			Debug.out( "Tag has been removed" );

			return;
		}

		boolean added = objects.add( t );

		if ( added ){

			if ( getTagType().isTagTypePersistent()){

					// do this before calling super.addTaggable so that the addition time is
					// available to any actions that result from it

				synchronized( TP_KEY ){

					Map all_props = (Map)t.getTaggableTransientProperty( TP_KEY );

					if ( all_props == null ){

						all_props = new HashMap();
					}

					all_props.put( TP_KEY_TAG_ADDED_TIME, SystemTime.getCurrentTime()/1000);

					t.setTaggableTransientProperty( TP_KEY, all_props );
				}
			}
		}

		super.addTaggable( t );

		if ( added ){

			getManager().tagContentsChanged( this );

			if ( tag_notifications != null ){

				checkNotifications( t, true );
			}
		}
	}

	@Override
	public void
	removeTaggable(
		Taggable	t )
	{
		boolean removed = objects.remove( t );

		super.removeTaggable( t );

		if ( removed ){

			getManager().tagContentsChanged( this );

			if ( tag_notifications != null ){

				checkNotifications( t, false );
			}
		}
	}

	protected void
	checkNotifications(
		Taggable		taggable,
		boolean			is_add )
	{
		int flags = getPostingNotifications();

		if ( flags != 0 ){

			boolean	add = ( flags & TagFeatureNotifications.NOTIFY_ON_ADD ) != 0;
			boolean	rem = ( flags & TagFeatureNotifications.NOTIFY_ON_REMOVE ) != 0;

			if ( add == is_add || rem == !is_add ){


				String name;

				TaggableResolver resolver = taggable.getTaggableResolver();

				if ( resolver != null ){

					name = resolver.getDisplayName( taggable );

				}else{

					name = taggable.toString();
				}

				name = MessageText.getString(
							is_add?"tag.notification.added":"tag.notification.removed",
							new String[]{
								name,
								getTagName( true ),
							});

				Map<String,String>	cb_data = new HashMap<>();

				cb_data.put( "allowReAdd", "true" );
				cb_data.put( "taguid", String.valueOf( getTagUID() ));
				cb_data.put( "id", String.valueOf( taggable.getTaggableID()));

				String icon_id = "image.sidebar.tag-green";

				int[] color = getColor();

				if ( color != null && color.length == 3 ){

					long rgb = (color[0]<<16)|(color[1]<<8)|color[2];

					String hex = Long.toHexString( rgb );

					while( hex.length() < 6 ){

						hex = "0"+ hex;
					}

					icon_id += "#" + hex;
				}

				LocalActivityManager.addLocalActivity(
					getTagUID() + ":" + taggable.getTaggableID() + ":" + is_add,
					icon_id,
					name,
					new String[]{ MessageText.getString( "label.view" )},
					ActivityCallback.class,
					cb_data );
			}
		}
	}

	public static class
	ActivityCallback
		implements LocalActivityCallback
	{
		@Override
		public void
		actionSelected(
			String action, Map<String, String> data)
		{
			String	taguid 	= data.get( "taguid" );

			final String	id 		= data.get( "id" );

			if ( taguid != null && id != null ){

				try{
					Tag tag = TagManagerFactory.getTagManager().lookupTagByUID( Long.parseLong( taguid ));

					if ( tag != null ){

						TagType tt = tag.getTagType();

						if ( tt instanceof TagTypeWithState ){

							final TaggableResolver resolver = ((TagTypeWithState)tt).getResolver();

							if ( resolver != null ){

								if ( !tag.isVisible()){

									tag.setVisible( true );
								}

								tag.requestAttention();

									// things are somewhat async - too much effort to try and add some callback
									// structure to ensure tag is vsible before showing download...

								SimpleTimer.addEvent(
									"async",
									SystemTime.getOffsetTime( 500 ),
									new TimerEventPerformer() {

										@Override
										public void perform(TimerEvent event) {
											resolver.requestAttention( id );
										}
									});

							}
						}
					}
				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}

	@Override
	public void
	removeTag()
	{
		super.removeTag();

		removed = true;
	}

	protected boolean
	isRemoved()
	{
		return( removed );
	}

	@Override
	public int
	getTaggedCount()
	{
		return( objects.size());
	}

	@Override
	public boolean
	hasTaggable(
		Taggable	t )
	{
		return( objects.contains( t ));
	}

	@Override
	public Set<Taggable>
	getTagged()
	{
		return(objects.getSet());
	}
}
