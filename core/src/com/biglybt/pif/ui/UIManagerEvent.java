/*
 * Created on 11-Sep-2005
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 *
 */

package com.biglybt.pif.ui;

import com.biglybt.pif.PluginInterface;

public interface
UIManagerEvent
{
	public static final int MT_NONE			= 0x00000000;
	public static final int MT_OK			= 0x00000001;
	public static final int MT_CANCEL		= 0x00000002;
	public static final int MT_YES			= 0x00000004;
	public static final int MT_NO			= 0x00000008;
	public static final int MT_YES_DEFAULT	= 0x00000010;	// as for YES but makes it the default selection
	public static final int MT_NO_DEFAULT	= 0x00000020;	// as for NO but makes it the default selection
	public static final int MT_OK_DEFAULT	= 0x00000040;	// as for OK but makes it the default selection


	public static final int ET_SHOW_TEXT_MESSAGE				= 1;		// data is String[] - title, message, text
	public static final int ET_OPEN_TORRENT_VIA_FILE			= 2;		// data is File
	public static final int ET_OPEN_TORRENT_VIA_URL				= 3;		// data is Object[]{URL,URL,Boolean} - { torrent_url, referrer url, auto_download, Map request_properties}
	public static final int ET_PLUGIN_VIEW_MODEL_CREATED		= 4;		// data is PluginViewModel (or subtype)
	public static final int ET_COPY_TO_CLIPBOARD				= 6;		// data is String
	public static final int ET_PLUGIN_VIEW_MODEL_DESTROYED		= 7;		// data is PluginViewModel (or subtype)
	public static final int ET_OPEN_URL							= 9;		// data is URL
	public static final int ET_SHOW_CONFIG_SECTION		        = 13;		// data is String - section id
	public static final int ET_SHOW_MSG_BOX						= 21;		// data is Object[]{ String,String,Long} - title, message, MT options
	public static final int ET_OPEN_TORRENT_VIA_TORRENT			= 22;		// data is Torrent
	public static final int ET_FILE_SHOW                        = 23;       // data is File
	public static final int ET_FILE_OPEN                        = 24;       // data is File
	public static final int ET_HIDE_ALL			                = 27;       // data is Boolean
	public static final int ET_HIDE_ALL_TOGGLE	                = 28;       // no data


	public static final int ET_CALLBACK_MSG_SELECTION			= 100;		// data is Long - MT_OK etc

	public PluginInterface
	getPluginInterface();
	
	public int
	getType();

	public Object
	getData();

	public void
	setResult(
		Object	result );

	public Object
	getResult();
}
