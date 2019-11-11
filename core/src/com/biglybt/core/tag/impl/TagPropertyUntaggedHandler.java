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


package com.biglybt.core.tag.impl;

import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.tag.TagFeatureProperties.TagPropertyListener;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.IdentityHashSet;
import com.biglybt.core.util.SystemTime;


public class
TagPropertyUntaggedHandler
	implements TagTypeListener
{
	private final Core core;
	final TagManagerImpl	tag_manager;

	private boolean	is_initialised;
	private boolean	is_enabled;

	final Set<Tag>	untagged_tags = new HashSet<>();

	final Map<Taggable,int[]>		taggable_counts = new IdentityHashMap<>();


	protected
	TagPropertyUntaggedHandler(
		Core _core,
		TagManagerImpl	_tm )
	{
		core	= _core;
		tag_manager		= _tm;

		tag_manager.addTaggableLifecycleListener(
			Taggable.TT_DOWNLOAD,
			new TaggableLifecycleAdapter()
			{
				@Override
				public void
				initialised(
					List<Taggable>	current_taggables )
				{
					try{
						TagType tt = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL );

						tt.addTagTypeListener(
							new TagTypeAdapter()
							{
								@Override
								public void
								tagAdded(
									Tag			tag )
								{
									TagFeatureProperties tfp = (TagFeatureProperties)tag;

									TagProperty[] props = tfp.getSupportedProperties();

									for ( TagProperty prop: props ){

										if ( prop.getName( false ).equals( TagFeatureProperties.PR_UNTAGGED )){

											prop.addListener(
												new TagPropertyListener()
												{
													@Override
													public void
													propertyChanged(
														TagProperty		property )
													{
														handleProperty( property );
													}

													@Override
													public void
													propertySync(
														TagProperty		property )
													{
													}
												});

											handleProperty( prop );
										}
									}
								}
							},
							true );

					}finally{

						CoreFactory.addCoreRunningListener(
							new CoreRunningListener()
							{
								@Override
								public void
								coreRunning(
									Core core )
								{
									synchronized( taggable_counts ){

										is_initialised = true;

										if ( is_enabled ){

											enable();
										}
									}
								}
							});
					}
				}

				@Override
				public void
				taggableCreated(
					Taggable		taggable )
				{
					addDownloads( Collections.singletonList( (DownloadManager)taggable));
				}
			});
	}

	@Override
	public void
	tagTypeChanged(
		TagType		tag_type )
	{

	}

	@Override
	public void tagEventOccurred(TagEvent event ) {
		int	type = event.getEventType();
		Tag	tag = event.getTag();
		if ( type == TagEvent.ET_TAG_ADDED ){
			tagAdded( tag );
		}else if ( type == TagEvent.ET_TAG_REMOVED ){
			tagRemoved( tag );
		}
	}

	public void
	tagAdded(
		Tag			tag )
	{
		tag.addTagListener(
			new TagListener()
			{
				Set<Taggable>		added 			= new IdentityHashSet<>();
				Map<Taggable,Long>	pending_removal = new IdentityHashMap<Taggable, Long>();
				
				@Override
				public void
				taggableAdded(
					Tag			tag,
					Taggable	tagged )
				{
					synchronized( taggable_counts ){

							// seeing counts get inconsistent occasionally so added some additional logic to see if
							// additions and removals are getting messed up
						
						if ( added.contains( tagged )){
							
							if ( Constants.IS_CVS_VERSION ){

								Debug.out( "Taggable '" + tagged.getTaggableName() + "' added twice to '" + tag.getTagName( true ) + "'" );
							}
							
							return;
							
						}else{
							
							int num_pending = pending_removal.size();
							
							if ( num_pending > 0 ){
								
								Long time = pending_removal.remove( tagged );
								
								if ( time != null && SystemTime.getMonotonousTime() - time < 5000 ){
									
									Debug.out( "Addition ignored as pending removal" );
									
									return;
								}
								
								if ( num_pending > 10 ){
																		
									Iterator<Long> it = pending_removal.values().iterator();
									
									long now = SystemTime.getMonotonousTime();

									while( it.hasNext()){
										
										if ( now - it.next() > 10*1000 ){
											
											it.remove();
										}
									}
								}
							}
						}
						
						added.add( tagged );
						
						if ( untagged_tags.contains( tag )){

							return;
						}

						int[] num = taggable_counts.get( tagged );

						if ( num == null ){

							num = new int[1];

							taggable_counts.put( tagged, num );
						}

						if ( num[0]++ == 0 ){

							//System.out.println( "tagged: " + tagged.getTaggableID());

							for ( Tag t: untagged_tags ){

								if ( t.hasTaggable( tagged )){
								
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

				}

				@Override
				public void
				taggableRemoved(
					Tag			tag,
					Taggable	tagged )
				{
					synchronized( taggable_counts ){

						if ( !added.remove( tagged )){
							
							if ( Constants.IS_CVS_VERSION ){
							
								Debug.out( "Taggable '" + tagged.getTaggableName() + "' remove from '" + tag.getTagName( true ) + "' but not added" );
							}
							
							pending_removal.put( tagged, SystemTime.getMonotonousTime());
							
							return;
						}
												
						if ( untagged_tags.contains( tag )){

							return;
						}

						int[] num = taggable_counts.get( tagged );

						if ( num != null ){

							if ( num[0]-- == 1 ){

								//System.out.println( "untagged: " + tagged.getTaggableID());

								taggable_counts.remove( tagged );

								DownloadManager dm = (DownloadManager)tagged;

								if ( !dm.isDestroyed()){

									for ( Tag t: untagged_tags ){

										t.addTaggable( tagged );
									}
								}
							}
						}
					}
				}
			}, true );
	}

	public void
	tagRemoved(
		Tag			tag )
	{
		synchronized( taggable_counts ){

			boolean was_untagged = untagged_tags.remove( tag );

			if ( was_untagged ){

				if ( untagged_tags.size() == 0 ){

					setEnabled( tag, false );
				}
			}
		}
	}

	private void
	enable()
	{
		TagType tt = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL );

		tt.addTagTypeListener( this, false );

		for ( Tag tag: tt.getTags()){

			tagAdded( tag );
		}

		List<DownloadManager> existing = core.getGlobalManager().getDownloadManagers();

		addDownloads( existing );
	}

	private void
	disable()
	{
		TagType tt = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL );

		tt.removeTagTypeListener( this );

		taggable_counts.clear();
	}

	private void
	setEnabled(
		Tag			current_tag,
		boolean		enabled )
	{
		if ( enabled == is_enabled ){

			if ( is_enabled ){

				if ( untagged_tags.size() < 2 ){

					Debug.out( "eh?" );

					return;
				}

				Set<Taggable> existing = current_tag.getTagged();

				for ( Taggable t: existing ){

					current_tag.removeTaggable( t );
				}

				Tag[] temp = untagged_tags.toArray(new Tag[untagged_tags.size()]);

				Tag copy_from = temp[0]==current_tag?temp[1]:temp[0];

				for ( Taggable t: copy_from.getTagged()){

					current_tag.addTaggable( t );
				}
			}

			return;
		}

		is_enabled = enabled;

		if ( enabled ){

			if ( is_initialised ){

				enable();
			}
		}else{

			disable();
		}
	}

	private void
	handleProperty(
		TagProperty		property )
	{
		Tag	tag = property.getTag();

		synchronized( taggable_counts ){

			Boolean val = property.getBoolean();

			if ( val != null && val ){

				untagged_tags.add( tag );

				setEnabled( tag, true );

			}else{

				boolean was_untagged = untagged_tags.remove( tag );

				if ( untagged_tags.size() == 0 ){

					setEnabled( tag, false );
				}

				if ( was_untagged ){

					Set<Taggable> existing = tag.getTagged();

					for ( Taggable t: existing ){

						tag.removeTaggable( t );
					}
				}
			}
		}
	}

	private void
	addDownloads(
		List<DownloadManager>		dms )
	{
		synchronized( taggable_counts ){

			if ( !is_enabled ){

				return;
			}

			for ( DownloadManager dm: dms ){

				if ( !dm.isPersistent()){

					continue;
				}

				if ( !taggable_counts.containsKey( dm )){

					for ( Tag t: untagged_tags ){

						if ( !t.hasTaggable( dm )){
						
							t.addTaggable( dm );
						}
					}
				}
			}
		}
	}

	protected List<Tag>
	getUntaggedTags()
	{
		synchronized( taggable_counts ){

			return(new ArrayList<>(untagged_tags));
		}
	}
}
