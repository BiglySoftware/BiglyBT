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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.Core;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.core.util.TrackersUtil;

public class
TagPropertyTrackerTemplateHandler
	implements TagFeatureProperties.TagPropertyListener, TagListener
{
	final TagManagerImpl	tag_manager;

	protected
	TagPropertyTrackerTemplateHandler(
		Core _core,
		TagManagerImpl	_tm )
	{
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

								TagProperty prop = tfp.getProperty( TagFeatureProperties.PR_TRACKER_TEMPLATES );

								if ( prop != null ){

									prop.addListener( TagPropertyTrackerTemplateHandler.this );

									tag.addTagListener( TagPropertyTrackerTemplateHandler.this, false );
								}
							}
						},
						true );
				}
			});
	}

	private String[]
	getPropertyBits(
		TagProperty		prop )
	{
		String[] bits = prop.getStringList();

		if ( bits == null || bits.length == 0 ){

			return( null );
		}

		return( bits );
	}

	private void
	handleStuff(
		String[]			bits,
		Set<Taggable>		taggables )
	{
		Map<String,List<List<String>>> templates = TrackersUtil.getInstance().getMultiTrackers();

		for ( String bit: bits ){

			String[] temp = bit.split( ":" );

			String t_name = temp[1];

			List<List<String>> template_trackers = templates.get( t_name );

			if ( template_trackers == null ){

				Debug.out( "Tracker template '" + t_name + "' not found" );

				continue;
			}

			String type = temp[0];

			for ( Taggable t: taggables ){

				DownloadManager dm = (DownloadManager)t;

				TOTorrent torrent = dm.getTorrent();

				if ( torrent != null ){

					List<List<String>> existing_trackers = TorrentUtils.announceGroupsToList( torrent );
					
					List<List<String>> new_trackers;
					
					if ( type.equals( "m" )){

						new_trackers = TorrentUtils.mergeAnnounceURLs( existing_trackers, template_trackers );

					}else if ( type.equals( "r" )){

						new_trackers = template_trackers;

					}else{

						new_trackers = TorrentUtils.removeAnnounceURLs( existing_trackers, template_trackers, true );
					}

					if ( !TorrentUtils.areIdentical( new_trackers, existing_trackers )){
					
						TorrentUtils.listToAnnounceGroups( new_trackers, torrent );
						
						try{
							TorrentUtils.writeToFile(torrent);
							
						}catch( Throwable e ){
							
							try{
								DownloadManagerState dms = dm.getDownloadState();
								
									// might have already been removed if metadata download
								
								if ( !dms.getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
	
									Debug.out( e );
								}
							}catch( Throwable f ){
							}
						}
					}
				}
			 }
		 }
	}

	@Override
	public void
	propertyChanged(
		TagProperty		property )
	{
		String[] bits = getPropertyBits( property );

		if ( bits == null ){

			return;
		}

		handleStuff( bits, property.getTag().getTagged());
	}

	@Override
	public void
	propertySync(
		TagProperty		property )
	{
		propertyChanged( property );
	}

	@Override
	public void
	taggableAdded(
		Tag			tag,
		Taggable	tagged )
	{
		TagFeatureProperties tfp = (TagFeatureProperties)tag;

		TagProperty prop = tfp.getProperty( TagFeatureProperties.PR_TRACKER_TEMPLATES );

		if ( prop != null ){

			String[] bits = getPropertyBits( prop );

			if ( bits == null ){

				return;
			}

			Set<Taggable> taggables = new HashSet<>();

			taggables.add( tagged );

			handleStuff(  bits, taggables );
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
	}
}
