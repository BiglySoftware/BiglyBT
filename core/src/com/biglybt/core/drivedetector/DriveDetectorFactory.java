/*
 * Created on Jul 26, 2009 5:17:41 PM
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
package com.biglybt.core.drivedetector;

import com.biglybt.core.drivedetector.impl.DriveDetectorImpl;


/**
 * @author TuxPaper
 * @created Jul 26, 2009
 *
 */
public class DriveDetectorFactory
{
	private static DriveDetector dd;

	public static DriveDetector getDeviceDetector() {
		if (dd == null) {
			dd = new DriveDetectorImpl();
		}
		return dd;
	}
}
