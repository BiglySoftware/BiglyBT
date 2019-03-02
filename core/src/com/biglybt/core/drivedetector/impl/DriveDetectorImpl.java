/*
 * Created on Jul 26, 2009 5:18:54 PM
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.drivedetector.DriveDetectedInfo;
import com.biglybt.core.drivedetector.DriveDetectedListener;
import com.biglybt.core.drivedetector.DriveDetector;
import com.biglybt.core.drivedetector.DriveDetectorFactory;
import com.biglybt.core.util.*;

/**
 * @author TuxPaper
 * @created Jul 26, 2009
 *
 */
public class DriveDetectorImpl
	implements DriveDetector,
	AEDiagnosticsEvidenceGenerator
{
	final AEMonitor mon_driveDetector = new AEMonitor("driveDetector");

	final CopyOnWriteList<DriveDetectedListener> listListeners = new CopyOnWriteList<>(1);

	final Map<File, Map> mapDrives = new HashMap<>(1);

	private final AsyncDispatcher	dispatcher = new AsyncDispatcher( "DriveDetector" );

	public DriveDetectorImpl() {
		AEDiagnostics.addWeakEvidenceGenerator(this);
	}

	@Override
	public DriveDetectedInfo[] getDetectedDriveInfo() {
		mon_driveDetector.enter();
		try {
			int i = 0;
			DriveDetectedInfo[] ddi = new DriveDetectedInfo[mapDrives.size()];
			for (File key : mapDrives.keySet()) {
				ddi[i++] = new DriveDetectedInfoImpl(key, mapDrives.get(key));
			}
			return ddi;
		} finally {
			mon_driveDetector.exit();
		}
	}

	@Override
	public void addListener(DriveDetectedListener l) {
		mon_driveDetector.enter();
		try {
			if (!listListeners.contains(l)) {
				listListeners.add(l);
			} else {
				// already added, skip trigger
				return;
			}


			for (File key : mapDrives.keySet()) {
				try {
					l.driveDetected(new DriveDetectedInfoImpl(key, mapDrives.get(key)));
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		} finally {
			mon_driveDetector.exit();
		}
	}

	@Override
	public void removeListener(DriveDetectedListener l) {
		listListeners.remove(l);
	}

	@Override
	public void driveDetected(final File _location, final Map info) {

		// seen the file-normalization hang on OSX (at least) and this is invoked on the SWT
		// thread and thus borks the UI - make it async

		dispatcher.dispatch(
			new AERunnable() {

				@Override
				public void runSupport()
				{
					File location = normaliseFile( _location );
					mon_driveDetector.enter();
					try {
						if (!mapDrives.containsKey(location)) {
							info.put("File", location);
							mapDrives.put(location, info);
						} else {
							// already there, no trigger
							return;
						}

					} finally {
						mon_driveDetector.exit();
					}

					for (DriveDetectedListener l : listListeners) {
						try {
							l.driveDetected(new DriveDetectedInfoImpl(location, info));
			 			} catch (Throwable e) {
			 				Debug.out(e);
						}
					}
				}
			});
	}

	@Override
	public void driveRemoved(final File _location) {
		dispatcher.dispatch(
				new AERunnable() {

					@Override
					public void runSupport()
					{
						File location = normaliseFile( _location );
						Map map;
						mon_driveDetector.enter();
						try {
							map = mapDrives.remove(location);
							if (map == null) {
								// not there, no trigger
								return;
							}
						} finally {
							mon_driveDetector.exit();
						}

						for (DriveDetectedListener l : listListeners) {
							try {
								l.driveRemoved(new DriveDetectedInfoImpl(location, map));
							} catch (Throwable e) {
								Debug.out(e);
							}
						}
					}
				});
	}

	File
	normaliseFile(
		File		f )
	{
		try{
			return( f.getCanonicalFile());

		}catch( Throwable e ){

			Debug.out( e );

			return( f );
		}
	}

	// @see com.biglybt.core.util.AEDiagnosticsEvidenceGenerator#generate(com.biglybt.core.util.IndentWriter)
	@Override
	public void generate(IndentWriter writer) {
		synchronized (mapDrives) {
			writer.println("DriveDetector: " + mapDrives.size() + " drives");
			for (File file : mapDrives.keySet()) {
				try {
					writer.indent();
					writer.println(file.getPath());

					try {
						writer.indent();

  					Map driveInfo = mapDrives.get(file);
  					for (Iterator iter = driveInfo.keySet().iterator(); iter.hasNext();) {
  						Object key = (Object) iter.next();
  						Object val = driveInfo.get(key);
  						writer.println(key + ": " + val);
  					}
					} finally {
						writer.exdent();
					}
				} catch (Throwable e) {
					Debug.out(e);
				} finally {
					writer.exdent();
				}
			}
		}
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
