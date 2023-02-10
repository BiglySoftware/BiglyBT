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


package com.biglybt.core.tag;

import java.util.Set;

import com.biglybt.core.download.DownloadManager;

public interface
TagDownload
	extends Tag, TagFeatureRateLimit, TagFeatureRSSFeed, TagFeatureRunState, TagFeatureTranscode, TagFeatureFileLocation, TagFeatureProperties, TagFeatureExecOnAssign, TagFeatureLimits, TagFeatureNotifications
{
	public static final int FEATURES 	= TF_RATE_LIMIT | TF_RSS_FEED | TF_RUN_STATE | TF_XCODE | TF_FILE_LOCATION | TF_PROPERTIES | TagFeature.TF_EXEC_ON_ASSIGN | TagFeature.TF_LIMITS | TagFeature.TF_NOTIFICATIONS;

	public Set<DownloadManager>
	getTaggedDownloads();
	
	public void
	applySort();
	
	public int
	getAutoApplySortInterval();
	
	public void
	setAutoApplySortInterval(
		int		secs );
}
