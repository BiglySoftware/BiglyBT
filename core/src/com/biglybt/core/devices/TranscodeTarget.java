/*
 * Created on Feb 4, 2009
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


public interface
TranscodeTarget
{
	public static final int TRANSCODE_UNKNOWN			= -1;
	public static final int TRANSCODE_NEVER				= 1;
	public static final int TRANSCODE_WHEN_REQUIRED		= 2;
	public static final int TRANSCODE_ALWAYS			= 3;

	public String
	getID();

	public Device
	getDevice();

	public TranscodeFile[]
	getFiles();

	public File
	getWorkingDirectory();

	public void
	setWorkingDirectory(
		File		directory );

	public TranscodeProfile[]
	getTranscodeProfiles();

	public TranscodeProfile
	getDefaultTranscodeProfile()

		throws TranscodeException;

	public void
	setDefaultTranscodeProfile(
		TranscodeProfile		profile );

	public TranscodeProfile
	getBlankProfile();

	public int
	getTranscodeRequirement();

	public void
	setTranscodeRequirement(
		int		req );

	public boolean
	getAlwaysCacheFiles();

	public void
	setAlwaysCacheFiles(
		boolean		always_cache );

	public boolean
	isTranscoding();

	public boolean
	isNonSimple();

	public boolean
	isAudioCompatible(
		TranscodeFile		file );

	public void
	addListener(
		TranscodeTargetListener		listener );

	public void
	removeListener(
		TranscodeTargetListener		listener );
}
