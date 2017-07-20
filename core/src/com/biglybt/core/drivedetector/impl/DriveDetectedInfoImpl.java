/*
 * Created on Jul 26, 2009 5:36:43 PM
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
package com.biglybt.core.drivedetector.impl;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import com.biglybt.core.drivedetector.DriveDetectedInfo;

/**
 * @author TuxPaper
 * @created Jul 26, 2009
 *
 */
public class DriveDetectedInfoImpl
	implements DriveDetectedInfo
{
	final File location;
	private final Map info;

	public DriveDetectedInfoImpl(File location, Map info) {
		this.location = location;
		this.info = info;
	}

	@Override
	public File getLocation() {
		return location;
	}

	@Override
	public Object getInfo(String key) {
		return info.get(key);
	}

	@Override
	public Map<String, Object> getInfoMap() {
		return Collections.unmodifiableMap(info);
	}

}
