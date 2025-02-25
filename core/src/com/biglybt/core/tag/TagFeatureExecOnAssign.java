/*
 * Created on Apr 18, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.tag;

import java.util.List;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerOptionsHandler;

public interface
TagFeatureExecOnAssign
	extends TagFeature
{
	public static final int ACTION_NONE						= 0x00000;
	public static final int ACTION_DESTROY					= 0x00001;
	public static final int ACTION_START					= 0x00002;
	public static final int ACTION_STOP						= 0x00004;
	public static final int ACTION_FORCE_START				= 0x00008;
	public static final int ACTION_NOT_FORCE_START			= 0x00010;
	public static final int ACTION_SCRIPT					= 0x00020;
	public static final int ACTION_PAUSE					= 0x00040;
	public static final int ACTION_RESUME					= 0x00080;
	public static final int ACTION_APPLY_OPTIONS_TEMPLATE	= 0x00100;
	public static final int ACTION_POST_MAGNET_URI			= 0x00200;
	public static final int ACTION_MOVE_INIT_SAVE_LOC		= 0x00400;
	public static final int ACTION_ASSIGN_TAGS				= 0x00800;
	public static final int ACTION_HOST						= 0x01000;
	public static final int ACTION_PUBLISH					= 0x02000;
	public static final int ACTION_REMOVE_TAGS				= 0x04000;
	public static final int ACTION_QUEUE					= 0x08000;
	public static final int ACTION_BAN						= 0x10000;

	public static final int[] ACTIONS = {
			ACTION_DESTROY,
			ACTION_START,
			ACTION_STOP,
			ACTION_FORCE_START,
			ACTION_NOT_FORCE_START,
			ACTION_SCRIPT,
			ACTION_PAUSE,
			ACTION_RESUME,
			ACTION_APPLY_OPTIONS_TEMPLATE,
			ACTION_POST_MAGNET_URI,
			ACTION_MOVE_INIT_SAVE_LOC,
			ACTION_ASSIGN_TAGS,
			ACTION_HOST,
			ACTION_PUBLISH,
			ACTION_REMOVE_TAGS,
			ACTION_QUEUE,
			ACTION_BAN,
	};
	
	public int
	getSupportedActions();

	public boolean
	supportsAction(
		int		action );

	public boolean
	isAnyActionEnabled();
	
	public boolean
	isActionEnabled(
		int		action );

	public void
	setActionEnabled(
		int			action,
		boolean		enabled );

	public String
	getActionScript();

	public void
	setActionScript(
		String		script );
	
	public OptionsTemplateHandler
	getOptionsTemplateHandler();
	
	public List<Tag>
	getTagAssigns();
	
	public void
	setTagAssigns(
		List<Tag>	tags );
	
	public List<Tag>
	getTagRemoves();
	
	public void
	setTagRemoves(
		List<Tag>	tags );

	public interface
	OptionsTemplateHandler
		extends DownloadManagerOptionsHandler
	{
		public boolean
		isActive();
		
		public void
		applyTo(
			DownloadManager		dm );
	}
	
	public String
	getPostMessageChannel();

	public void
	setPostMessageChannel(
		String		chat );
	
	public String
	getEOAString();
	
}
