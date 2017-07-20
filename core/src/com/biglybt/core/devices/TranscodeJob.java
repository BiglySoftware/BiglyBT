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

import com.biglybt.pif.disk.DiskManagerFileInfo;

public interface
TranscodeJob
{
	public static final int	ST_QUEUED		= 0;
	public static final int	ST_RUNNING		= 1;
	public static final int	ST_PAUSED		= 2;
	public static final int	ST_COMPLETE		= 3;
	public static final int	ST_CANCELLED	= 4;
	public static final int	ST_FAILED		= 5;
	public static final int	ST_STOPPED		= 6;
	public static final int	ST_REMOVED		= 7;

	public String
	getName();

	public TranscodeTarget
	getTarget();

	public TranscodeProfile
	getProfile();

	public DiskManagerFileInfo
	getFile();

	public TranscodeFile
	getTranscodeFile();

	public int
	getTranscodeRequirement();

	public int
	getIndex();

	public int
	getState();

	public long
	getDownloadETA();

	public int
	getPercentComplete();

	public long
	getETASecs();

	public String
	getETA();

	public String
	getError();

	public void
	setEnableAutoRetry(
		boolean		enabled );

	public boolean
	getEnableAutoRetry();

	public void
	setPreferDirectInput(
		boolean		prefer );

	public boolean
	getPreferDirectInput();

	public boolean
	canPause();

	public void
	pause();

	public void
	resume();

	public void
	queue();

	public void
	stop();

	public void
	remove()

		throws TranscodeActionVetoException;

	public void
	removeForce();

	public void
	moveUp();

	public void
	moveDown();

	public long
	getProcessTime();

	public void
	analyseNow(
		TranscodeAnalysisListener	listener )

		throws TranscodeException;
}
