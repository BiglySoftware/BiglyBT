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
import java.util.concurrent.atomic.AtomicBoolean;

import com.biglybt.core.Core;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.torrent.TOTorrentListener;
import com.biglybt.core.util.TorrentUtils;

public class
TagPropertyTrackerHandler
	implements TagFeatureProperties.TagPropertyListener, TOTorrentListener
{
	private final Core core;
	private final TagManagerImpl	tag_manager;

	private Set<TagProperty>		properties = new HashSet<>();
	
	private final Map<String,List<Tag>>	tracker_host_map = new HashMap<>();

	private final AtomicBoolean	sync_required = new AtomicBoolean(false);
	
	protected
	TagPropertyTrackerHandler(
		Core _core,
		TagManagerImpl	_tm )
	{
		core	= _core;
		tag_manager		= _tm;

		TOTorrentFactory.addTorrentListener( this );
		
		tag_manager.addTaggableLifecycleListener(
			Taggable.TT_DOWNLOAD,
			new TaggableLifecycleAdapter()
			{
				@Override
				public void
				initialised(
					List<Taggable>	current_taggables )
				{
					TagType[] tts = { 
						tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ), 
						tag_manager.getTagType( TagType.TT_DOWNLOAD_INTERNAL ) };

					for ( TagType tt: tts ){
						
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
	
										if ( prop.getName( false ).equals( TagFeatureProperties.PR_TRACKERS )){
	
											hookTagProperty( prop );
	
											break;
										}
									}
								}
							},
							true );
					}
				}

				@Override
				public void
				taggableCreated(
					Taggable		taggable )
				{
					handleDownload( (DownloadManager)taggable );
				}
			});
	}

	private void
	hookTagProperty(
		TagProperty		property )
	{
		synchronized( properties ){
		
			properties.add( property );
		}
		
		property.addListener( this );

		handleProperty( property, true );
	}

	@Override
	public void
	propertyChanged(
		TagProperty		property )
	{
		handleProperty( property, false );
	}

	@Override
	public void
	propertySync(
		TagProperty		property )
	{
	}

	private void
	handleProperty(
		TagProperty		property,
		boolean			start_of_day )
	{
		String[] trackers = property.getStringList();

		Set<String>	tag_hosts = new HashSet<>();
		
		for ( String tracker: trackers ){
			
			tag_hosts.add( tracker.toLowerCase( Locale.US ));
		}

		Tag tag = property.getTag();
		
		synchronized( tracker_host_map ){

			for ( Map.Entry<String,List<Tag>> entry: tracker_host_map.entrySet()){

				List<Tag> tags = entry.getValue();

				if ( tags.contains( tag )){

					if ( !tag_hosts.contains( entry.getKey())){

						tags.remove( tag );
					}
				}
			}

			for ( String host: tag_hosts ){
				
				List<Tag> tags = tracker_host_map.get( host );

				if ( tags == null ){

					tags = new ArrayList<>();

					tracker_host_map.put( host, tags );

				}else if ( tags.contains( tag )){

					continue;
				}

				tags.add( tag );
			}
		}

		if ( start_of_day ){

			return;
		}

		Set<Taggable> tag_dls = tag.getTagged();

		for ( Taggable tag_dl: tag_dls ){

			DownloadManager dm = (DownloadManager)tag_dl;

			Set<String> hosts = getAugmentedHosts( dm );

			boolean	hit = false;

			for ( String host: hosts ){

				if ( tag_hosts.contains( host )){

					hit = true;

					break;
				}
			}

			if ( !hit ){

				tag.removeTaggable( tag_dl );
			}
		}

		List<DownloadManager> managers = core.getGlobalManager().getDownloadManagers();

		for ( DownloadManager dm: managers ){

			if ( tag.hasTaggable( dm )){

				continue;
			}

			Set<String> hosts = getAugmentedHosts( dm );

			boolean	hit = false;

			for ( String host: hosts ){

				if ( tag_hosts.contains( host )){

					hit = true;

					break;
				}
			}

			if ( hit ){

				tag.addTaggable( dm );
			}
		}
	}

	private Set<String>
	getAugmentedHosts(
		DownloadManager		dm )
	{
		Set<String>	hosts = TorrentUtils.getUniqueTrackerHosts( dm.getTorrent(), true );

		Set<String>	result = new HashSet<>();

			// we do suffix matching (i.e. x.domain matches 'domain') by generating the suffix set

		for ( String host: hosts ){

			result.add( host );

			String[]	bits = host.split( "\\." );

			String	suffix = "";

			for (int i=bits.length-1;i>0;i--){

				String bit = bits[i];

				if ( suffix == "" ){

					suffix = bit;

				}else{

					suffix = bit + "." + suffix;
				}

				result.add( suffix );
			}
		}

		return( result );
	}

	protected List<Tag>
	getTagsForDownload(
		DownloadManager		dm )
	{
		List<Tag>	result = new ArrayList<>();

		synchronized( tracker_host_map ){

			if ( tracker_host_map.size() > 0 ){

				Set<String> hosts = getAugmentedHosts( dm );

				for ( String host: hosts ){

					List<Tag> tags = tracker_host_map.get( host );

					if ( tags != null ){

						result.addAll( tags );
					}
				}
			}
		}

		return( result );
	}

	private void
	handleDownload(
		DownloadManager		dm )
	{
		List<Tag> applicable_tags = getTagsForDownload( dm );

		for ( Tag tag: applicable_tags ){

			if ( !tag.hasTaggable( dm )){

				tag.addTaggable( dm );
			}
		}
	}
	
	protected void
	sync()
	{
		if ( sync_required.getAndSet( false )){
			
			List<TagProperty>	to_do;
			
			synchronized( properties ){
				
				to_do = new ArrayList<>( properties );
			}
			
			for ( TagProperty tp: to_do ){
				
					// only active properties require syncing - otherwise tags with no trackers defined
					// will end up with all their taggables being removed...
				
				if ( tp.getStringList().length > 0 ){
				
					handleProperty( tp, false );
				}
			}
		}
	}
	
	@Override
	public void
	torrentChanged(
		TOTorrent		torrent,
		int				change_type,
		Object			data )
	{
		if ( change_type == TOTorrentListener.CT_ANNOUNCE_URLS ){
			
			sync_required.set( true );
		}
	}
}
