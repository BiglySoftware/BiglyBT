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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pifimpl.local.utils.FormattersImpl;

public class TagUtils{

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
		}

		if ( str.startsWith( "\r\n" )){

			str = str.substring(2);
		}

		return( str );
	}

}
