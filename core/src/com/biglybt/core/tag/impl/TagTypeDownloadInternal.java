/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.tag.impl;

import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.tag.*;

public class
TagTypeDownloadInternal
	extends TagTypeWithState
{
	private static final int[] color_default = { 200, 200, 200 };

	private final AtomicInteger	next_tag_id = new AtomicInteger(0);

	protected
	TagTypeDownloadInternal(
		TaggableResolver		resolver )
	{
		super( TagType.TT_DOWNLOAD_INTERNAL, resolver, TagDownload.FEATURES, "tag.type.internal" );

		addTagType();
	}

	@Override
	public boolean
	isTagTypePersistent()
	{
		return( false );
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
	public Tag
	createTag(
		String		name,
		boolean		auto_add )

		throws TagException
	{
		TagDownloadWithState new_tag = new TagDownloadWithState( this, next_tag_id.incrementAndGet(), name, true, true, true, true, TagFeatureRunState.RSC_START_STOP_PAUSE );

		new_tag.setSupportsTagTranscode( true );
		new_tag.setSupportsFileLocation( true );

		if ( auto_add ){

			addTag( new_tag );
		}

		return( new_tag );
	}
}
