/*
 * Created on Jan 27, 2009
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.devices;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;

import com.biglybt.core.vuzefile.VuzeFile;

public interface
Device
{
	public static final int DT_UNKNOWN				= 0;
	public static final int DT_INTERNET_GATEWAY		= 1;
	public static final int DT_CONTENT_DIRECTORY	= 2;
	public static final int DT_MEDIA_RENDERER		= 3;
	public static final int DT_INTERNET				= 4;
	public static final int DT_DISK_OPS				= 5;

	public int
	getType();

	public String
	getID();

	public String
	getName();

	public void
	setName(
		String		name,
		boolean isAutomaticName);

	public String
	getClassification();

	public String
	getShortDescription();

	public void
	alive();

	public boolean
	isAlive();

	public boolean
	isLivenessDetectable();

	public boolean
	isBusy();

	public boolean
	isManual();

	public void
	setHidden(
		boolean		is_hidden );

	public boolean
	isHidden();

	public void
	setTagged(
		boolean	is_tagged );

	public boolean
	isTagged();

	public boolean
	isBrowsable();

	public browseLocation[]
	getBrowseLocations();

	public InetAddress
	getAddress();

	public void
	setAddress(
		InetAddress	address );

	public void
	setTransientProperty(
		Object		key,
		Object		value );

	public Object
	getTransientProperty(
		Object		key );

		/**
		 * Array of resource strings and their associated values
		 * @return
		 */

	public String[][]
	getDisplayProperties();

	public void
	requestAttention();

	public void
	remove();

	public void
	setCanRemove(
		boolean	can );

	public boolean
	canRemove();

	public String
	getInfo();

	public String
	getError();

	public String
	getStatus();

	public void
	addListener(
		DeviceListener		listener );

	public void
	removeListener(
		DeviceListener		listener );

	public String
	getString();

	interface
	browseLocation
	{
		public String
		getName();

		public URL
		getURL();
	}

	public boolean
	isGenericUSB();

	public void
	setGenericUSB(boolean b);

	public String
	getImageID();

	public List<String>
	getImageIDs();

	public void
	setImageID(String id);

	public boolean
	isNameAutomatic();

	public void
	setExportable(
		boolean		b );

	public boolean
	isExportable();

	public URL
	getWikiURL();

	public VuzeFile
	getVuzeFile()

		throws IOException;

	public TranscodeProfile[]
	getDirectTranscodeProfiles();
}
