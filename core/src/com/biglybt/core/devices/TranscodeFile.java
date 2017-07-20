/*
 * Created on Feb 13, 2009
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

import java.io.File;
import java.net.URL;

import com.biglybt.pif.disk.DiskManagerFileInfo;

public interface
TranscodeFile
{
		// don't change these, they are serialised

	public static final String PT_COMPLETE		= "comp";
	public static final String PT_COPIED		= "copied";
	public static final String PT_COPY_FAILED	= "copy_fail";
	public static final String PT_CATEGORY		= "cat";
	public static final String PT_TAGS			= "tags";

	public String
	getName();

	public DiskManagerFileInfo
	getSourceFile()

		throws TranscodeException;

	public DiskManagerFileInfo
	getTargetFile()

		throws TranscodeException;

	public String
	getProfileName();

	public long
	getCreationDateMillis();

	public boolean
	isComplete();

	public boolean
	getTranscodeRequired();

	public boolean
	isCopiedToDevice();

	public long
	getCopyToDeviceFails();

	public void
	retryCopyToDevice();

	public boolean
	isTemplate();

	public long
	getDurationMillis();

	public long
	getVideoWidth();

	public long
	getVideoHeight();

	public long
	getEstimatedTranscodeSize();

	public String[]
	getCategories();

	public void
	setCategories(
		String[]	cats );

	public String[]
	getTags(
		boolean	localize );

	public void
	setTags(
		String[]	tags );

	public Device
	getDevice();

	public File
	getCacheFileIfExists();

		/**
		 * Will return null unless there is a job in existance for this file
		 * @return
		 */

	public TranscodeJob
	getJob();

	public URL
	getStreamURL();

	public URL
	getStreamURL(
		String	host );

	public void
	delete(
		boolean	delete_cache_file )

		throws TranscodeException;

	public void
	setTransientProperty(
		Object		key,
		Object		value );

	public Object
	getTransientProperty(
		Object		key );

	public boolean
	isDeleted();

	public boolean
	isCopyingToDevice();
}
