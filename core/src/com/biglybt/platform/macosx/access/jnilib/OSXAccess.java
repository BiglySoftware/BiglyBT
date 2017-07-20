/*
 * Created on Jul 21, 2006 3:19:03 PM
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
package com.biglybt.platform.macosx.access.jnilib;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.biglybt.core.drivedetector.DriveDetectedInfo;
import com.biglybt.core.drivedetector.DriveDetectorFactory;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.util.MapUtils;

/**
 * @author TuxPaper
 * @created Jul 21, 2006
 *
 * javah -d . -classpath ../../../../../../../../bin com.biglybt.platform.macosx.access.jnilib.OSXAccess
 */
public class OSXAccess
{
	private static boolean bLoaded = false;

	private static boolean DEBUG = false;

	private static List<String> parameters = new ArrayList<>(1);

	static {
		loadLibrary("OSXAccess_10.5");
	}


	private static boolean loadLibrary(String lib) {
		try {
			SystemLoadLibrary(lib);
			//System.out.println(lib + " v" + getVersion() + " Load complete!");
			bLoaded = true;
			initialize();
		} catch (Throwable e1) {
			System.err.println("Could not find lib" + lib + ".jnilib; " + e1.toString());
		}

		return bLoaded;
	}

	private static void SystemLoadLibrary(String lib) throws Throwable {
		try {
			System.loadLibrary(lib);
		} catch (Throwable t) {
			try {
				File f = new File(
						new File(SystemProperties.getApplicationPath(), "dll"),
						"lib" + lib + ".jnilib");
				System.load(f.getAbsolutePath());
			} catch (Throwable t2) {
				throw t;
			}
		}
	}

	public static void passParameter(String s) {
		if (DEBUG) {
			System.err.println("passing Parameter " + s);
		}
		if (s != null) {
			parameters.add(s);
		}
	}

	public static String[] runLight(String[] args) {
		// initialize will have been called by now (via static constructor), hooking
		// some callbacks
		if (args != null) {
			Collections.addAll(parameters, args);
		}
		return parameters.toArray(new String[0]);
	}

	private static void initialize() {
		try {

			if (System.getProperty("osxaccess.light", "0").equals("1")) {
				initializeLight();

				Class<?> claOSXFileOpen = Class.forName("com.biglybt.ui.swt.osx.OSXFileOpen");
				if (claOSXFileOpen != null) {
					Method method = claOSXFileOpen.getMethod("initLight", new Class[0]);
					method.invoke(null, new Object[0]);
				}
				return;
			}

			initializeDriveDetection(new OSXDriveDetectListener() {
				@Override
				public void driveRemoved(File mount, Map driveInfo) {
					if (DEBUG) {
						System.out.println("UNMounted " + mount);
						for (Object key : driveInfo.keySet()) {
							Object val = driveInfo.get(key);
							System.out.println("\t" + key + "\t:\t" + val);
						}
					}
					DriveDetectorFactory.getDeviceDetector().driveRemoved(mount);
				}

				@Override
				public void driveDetected(File mount, Map driveInfo) {
					if (DEBUG) {
						System.out.println("Mounted " + mount);
						for (Object key : driveInfo.keySet()) {
							Object val = driveInfo.get(key);
							System.out.println("\t" + key + "\t:\t" + val);
						}
					}

					boolean isOptical = MapUtils.getMapLong(driveInfo, "isOptical", 0) != 0;
					boolean isRemovable = MapUtils.getMapLong(driveInfo, "Removable", 0) != 0;
					boolean isWritable = MapUtils.getMapLong(driveInfo, "Writable", 0) != 0;

					boolean isWritableUSB = (isRemovable && isWritable && !isOptical);
					driveInfo.put("isWritableUSB", isWritableUSB);

					DriveDetectorFactory.getDeviceDetector().driveDetected(mount, driveInfo);
				}
			});
		} catch (Throwable t) {
		}
	}

	public static native int AEGetParamDesc(int theAppleEvent,
	                                        int theAEKeyword, int desiredType, Object result); //AEDesc result

	public static native String getVersion();

	// 1.02
	public static native String getDocDir();

	// 1.03
	public static native void memmove(byte[] dest, int src, int size);

	// 1.04
	public static native void initializeDriveDetection(
			OSXDriveDetectListener d);

	// 1.11
	public static native void initializeLight();

	// 1.11
	public static native boolean setDefaultAppForExt(String bundleID, String ext);
	// 1.11
	public static native boolean setDefaultAppForMime(String bundleID, String mime);
	// 1.11
	public static native boolean setDefaultAppForScheme(String bundleID, String scheme);

	// 1.11
	public static native String getDefaultAppForExt(String ext);
	// 1.11
	public static native String getDefaultAppForMime(String mime);
	// 1.11
	public static native String getDefaultAppForScheme(String scheme);
	// 1.11
	public static native boolean canSetDefaultApp();


	public static boolean isLoaded() {
		return bLoaded;
	}

	public static void main(String[] args) {
		DriveDetectedInfo[] infos = DriveDetectorFactory.getDeviceDetector().getDetectedDriveInfo();
		for (DriveDetectedInfo info : infos) {
			System.out.println(info.getLocation());

			Map<String, Object> infoMap = info.getInfoMap();
			for (String key : infoMap.keySet()) {
				Object val = infoMap.get(key);
				System.out.println("\t" + key + ": " + val);
			}
		}
	}
}
