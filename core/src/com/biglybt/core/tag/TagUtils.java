/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.tag;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pifimpl.local.utils.FormattersImpl;

public class 
TagUtils
{
	private static final Object MOC_CACHE = new Object();
	
	private static final Comparator<String> comp = new FormattersImpl().getAlphanumericComparator( true );

	private static final Comparator<Tag> tag_comparator = 
		(o1,o2)->{
		
			String	g1 = o1.getGroup();
			String	g2 = o2.getGroup();

			if ( g1 != g2 ){
				if ( g1 == null ){
					return( 1 );
				}else if ( g2 == null ){
					return( -1 );
				}else{

					int	res = comp.compare( g1,  g2 );

					if ( res != 0 ){
						return( res );
					}
				}
			}
			if (o1 instanceof Category && o2 instanceof Category) {
				int type1 = ((Category) o1).getType();
				int type2 = ((Category) o2).getType();
				if (type1 != type2) {
					if (type1 == Category.TYPE_ALL) {
						return -1; 
					}
					if (type1 == Category.TYPE_UNCATEGORIZED) {
						return 1;
					}
					return type2 == Category.TYPE_ALL ? 1 : -1;
				}
			}
			return( comp.compare( o1.getTagName(true), o2.getTagName(true)));
		};
		
		
	private static final Comparator<Tag> tag_icon_comparator = 
		(o1,o2)->{
		
			int i1 = o1.getImageSortOrder();
			int i2 = o2.getImageSortOrder();
			
			int result = Integer.compare( i1,  i2 );
			
			if ( result == 0 ){
			
				result = comp.compare( o1.getTagName(true), o2.getTagName(true));
			}
			
			return( result );
		};
			
	private static Comparator<TagGroup> tag_group_comparator = 
		(o1,o2)->{
			
			return( comp.compare( o1.getName(),  o2.getName()));
		};

		
	public static Comparator<Tag>
	getTagComparator()
	{
		return( tag_comparator );
	}
	
	public static Comparator<TagGroup>
	getTagGroupComparator()
	{
		return( tag_group_comparator );
	}
		
	public static List<TagType>
	sortTagTypes(
		Collection<TagType>	_tag_types )
	{
		List<TagType>	tag_types = new ArrayList<>( _tag_types );

		Collections.sort(
			tag_types,
			new Comparator<TagType>()
			{
				final Comparator<String> comp = new FormattersImpl().getAlphanumericComparator( true );

				@Override
				public int
				compare(
					TagType o1, TagType o2)
				{
					return( comp.compare( o1.getTagTypeName(true), o2.getTagTypeName(true)));
				}
			});

		return( tag_types );
	}

	public static List<Tag>
	sortTags(
		Collection<Tag>	_tags )
	{
		List<Tag>	tags = new ArrayList<>( _tags );

		if ( tags.size() < 2 ){

			return( tags );
		}

		Collections.sort( tags, tag_comparator);

		return( tags );
	}

	public static List<TagGroup>
	sortTagGroups(
		Collection<TagGroup>	_groups )
	{
		List<TagGroup>	groups = new ArrayList<>( _groups );

		if ( groups.size() < 2 ){

			return( groups );
		}

		Collections.sort( groups, getTagGroupComparator());

		return( groups );
	}

	public static List<String>
	sortTagGroups(
		List<String>	_groups )
	{
		List<String>	groups = new ArrayList<>( _groups );

		if ( groups.size() < 2 ){

			return( groups );
		}

		Collections.sort( groups, comp );

		return( groups );
	}
	
	public static List<Tag>
	sortTagIcons(
		Collection<Tag>	_tags )
	{
		List<Tag>	tags = new ArrayList<>( _tags );

		if ( tags.size() < 2 ){

			return( tags );
		}

		Collections.sort( tags, tag_icon_comparator);

		return( tags );
	}

	
	public static String
	getTagTooltip(
		Tag		tag )
	{
		return( getTagTooltip( tag, false ));
	}

	public static String
	getTagTooltip(
		Tag			tag,
		boolean		skip_name )
	{
		if (tag instanceof Category) {
			return getCategoryTooltip((Category) tag, skip_name);
		}
		TagType tag_type = tag.getTagType();

		String 	str = skip_name?"":(tag_type.getTagTypeName( true ) + ": " + tag.getTagName( true ));

		String desc = tag.getDescription();

		if ( desc != null ){

			if ( str.length() > 0 ){

				str += "\r\n";
			}

			str += desc;
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_RATE_LIMIT )){

			TagFeatureRateLimit rl = (TagFeatureRateLimit)tag;

			String 	up_str 		= "";
			String	down_str 	= "";

			int	limit_up = rl.getTagUploadLimit();

			if ( limit_up > 0 ){

				up_str += MessageText.getString( "label.limit" ) + "=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( limit_up );
			}

			int current_up 		= rl.getTagCurrentUploadRate();

			if ( current_up >= 0 ){

				up_str += (up_str.length()==0?"":", " ) + MessageText.getString( "label.current" ) + "=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( current_up);
			}

			int	limit_down = rl.getTagDownloadLimit();

			if ( limit_down > 0 ){

				down_str += MessageText.getString( "label.limit" ) + "=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( limit_down );
			}

			int current_down 		= rl.getTagCurrentDownloadRate();

			if ( current_down >= 0 ){

				down_str += (down_str.length()==0?"":", " ) + MessageText.getString( "label.current" ) + "=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( current_down);
			}


			if ( up_str.length() > 0 ){

				str += "\r\n    " + MessageText.getString("iconBar.up") + ": " + up_str;
			}

			if ( down_str.length() > 0 ){

				str += "\r\n    " + MessageText.getString("iconBar.down") + ": " + down_str;
			}


			int up_pri = rl.getTagUploadPriority();

			if ( up_pri > 0 ){

				str += "\r\n    " + MessageText.getString("cat.upload.priority");
			}
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )) {

			TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;

			if ( fl.supportsTagInitialSaveFolder()){

				File init_loc = fl.getTagInitialSaveFolder();

				if ( init_loc != null ){

					str += "\r\n    " + MessageText.getString("label.init.save.loc") + "=" + init_loc.getAbsolutePath();
				}
			}

			if ( fl.supportsTagMoveOnComplete()){

				File move_on_comp = fl.getTagMoveOnCompleteFolder();

				if ( move_on_comp != null ){

					str += "\r\n    " + MessageText.getString("label.move.on.comp") + "=" + move_on_comp.getAbsolutePath();
				}
			}
			if ( fl.supportsTagCopyOnComplete()){

				File copy_on_comp = fl.getTagCopyOnCompleteFolder();

				if ( copy_on_comp != null ){

					str += "\r\n    " + MessageText.getString("label.copy.on.comp") + "=" + copy_on_comp.getAbsolutePath();
				}
			}
			if ( fl.supportsTagMoveOnRemove()){

				File mor = fl.getTagMoveOnRemoveFolder();

				if ( mor != null ){

					str += "\r\n    " + MessageText.getString("label.move.on.rem") + "=" + mor.getAbsolutePath();
				}
			}
			if ( fl.supportsTagMoveOnAssign()){

				File mor = fl.getTagMoveOnAssignFolder();

				if ( mor != null ){

					str += "\r\n    " + MessageText.getString("label.move.on.assign") + "=" + mor.getAbsolutePath();
				}
			}
		}

		if ( str.startsWith( "\r\n" )){

			str = str.substring(2);
		}

		return( str );
	}

	// Originally from MyTorrentsView
	private static String getCategoryTooltip(Category category, boolean skip_name) {
		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
		List<DownloadManager> dms = category.getDownloadManagers(
				gm.getDownloadManagers());

		long ttlActive = 0;
		long ttlSize = 0;
		long ttlRSpeed = 0;
		long ttlSSpeed = 0;
		int count = 0;
		for (DownloadManager dm : dms) {

			if (!category.hasTaggable(dm)) {
				continue;
			}

			count++;
			if (dm.getState() == DownloadManager.STATE_DOWNLOADING
				|| dm.getState() == DownloadManager.STATE_SEEDING) {
				ttlActive++;
			}
			DownloadManagerStats stats = dm.getStats();
			ttlSize += stats.getSizeExcludingDND();
			ttlRSpeed += stats.getDataReceiveRate();
			ttlSSpeed += stats.getDataSendRate();
		}

		String up_details = "";
		String down_details = "";

		if (category.getType() != Category.TYPE_ALL) {

			String up_str = MessageText.getString(
				"GeneralView.label.maxuploadspeed");
			String down_str = MessageText.getString(
				"GeneralView.label.maxdownloadspeed");
			String unlimited_str = MessageText.getString(
				"MyTorrentsView.menu.setSpeed.unlimited");

			int up_speed = category.getUploadSpeed();
			int down_speed = category.getDownloadSpeed();

			up_details = up_str + ": " + (up_speed == 0 ? unlimited_str
				: DisplayFormatters.formatByteCountToKiBEtc(up_speed));
			down_details = down_str + ": " + (down_speed == 0 ? unlimited_str
				: DisplayFormatters.formatByteCountToKiBEtc(down_speed));
		}

		if (count == 0) {
			return down_details + "\n" + up_details + "\nTotal: 0";
		}

		return (up_details.length() == 0 ? ""
			: (down_details + "\n" + up_details + "\n")) + "Total: " + count
			+ "\n" + "Downloading/Seeding: " + ttlActive + "\n" + "\n"
			+ "Total Speed: "
			+ DisplayFormatters.formatByteCountToKiBEtcPerSec(ttlRSpeed)
			+ " / "
			+ DisplayFormatters.formatByteCountToKiBEtcPerSec(ttlSSpeed)
			+ "\n" + "Average Speed: "
			+ DisplayFormatters.formatByteCountToKiBEtcPerSec(
			ttlRSpeed / (ttlActive == 0 ? 1 : ttlActive))
			+ " / "
			+ DisplayFormatters.formatByteCountToKiBEtcPerSec(
			ttlSSpeed / (ttlActive == 0 ? 1 : ttlActive))
			+ "\n" + "Size: "
			+ DisplayFormatters.formatByteCountToKiBEtc(ttlSize);
	}

	public static TagFeatureFileLocation
	selectInitialDownloadLocation(
		Collection<Tag>		tags )
	{
		if ( tags.size() > 0 ){

			List<Tag>	sl_tags = new ArrayList<>( tags.size());

			for ( Tag tag: tags ){

				if ( tag instanceof TagFeatureFileLocation ){
					
					TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;
	
					if ( fl.supportsTagInitialSaveFolder()){
	
						File save_loc = fl.getTagInitialSaveFolder();
	
						if ( save_loc != null ){
	
							sl_tags.add( tag );
						}
					}
				}
			}

			if ( sl_tags.size() > 0 ){

				if ( sl_tags.size() > 1 ){

					Collections.sort(
						sl_tags,
						new Comparator<Tag>()
						{
							@Override
							public int
							compare(
								Tag o1, Tag o2)
							{
								return( o1.getTagID() - o2.getTagID());
							}
						});
				}

				return((TagFeatureFileLocation)sl_tags.get(0));
			}
		}
		
		return( null );
	}
	
	public static List<Tag>
	getActiveMoveOnCompleteTags(
		DownloadManager			dm,
		boolean					allow_caching,
		Consumer<String>		logger )
	{
		if ( allow_caching ){
			
			Object[] cache = (Object[])dm.getUserData( MOC_CACHE );
			
			if ( cache != null ){
				
				if ( SystemTime.getMonotonousTime() - (Long)cache[0] < 10*1000 ){
					
					return((List<Tag>)cache[1]);
				}
			}
		}
				
    	List<Tag> dm_tags = TagManagerFactory.getTagManager().getTagsForTaggable( dm );

    	List<Tag>	applicable_tags = new ArrayList<>();

    	if ( dm_tags != null ){
	
	    	for ( Tag tag: dm_tags ){
	
	    		if ( tag.getTagType().hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )){
	
	    			TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;
	
	    			if ( fl.supportsTagMoveOnComplete()){
	
		    			File move_to = fl.getTagMoveOnCompleteFolder();
	
		    			if ( move_to != null ){
	
		    				if ( !move_to.exists()){
	
		    					move_to.mkdirs();
		    				}
	
		    				if ( move_to.isDirectory() && move_to.canWrite()){
	
		    					applicable_tags.add( tag );
	
		    				}else{
			    						
	    						logger.accept( "Ignoring invalid tag move-to location: " + move_to );
		    				}
		    			}
	    			}
	    		}
	    	}	    	

	    	if ( !applicable_tags.isEmpty()){
	
		    	if ( applicable_tags.size() > 1 ){
		
		    		Collections.sort(
		    			applicable_tags,
		    			new Comparator<Tag>()
		    			{
		    				@Override
						    public int
		    				compare(
		    					Tag o1,
		    					Tag o2)
		    				{
		    					return( o1.getTagID() - o2.getTagID());
		    				}
		    			});
		
		    		String str = "";
		
		    		for ( Tag tag: applicable_tags ){
		
		    			str += (str.length()==0?"":", ") + tag.getTagName( true );
		    		}
				    		
		   			logger.accept( "Multiple applicable tags found: " + str + " - selecting first" );
		    	}
	    	}
    	}

    	if ( allow_caching ){
			
			dm.setUserData( MOC_CACHE, new Object[]{ SystemTime.getMonotonousTime(), applicable_tags });
    	}
    	
    	return( applicable_tags );
	}
	
	public static boolean
	isInternalTagName(
		String		tag )
	{
		return( tag.startsWith("_") && tag.endsWith( "_" ));
	}
}
