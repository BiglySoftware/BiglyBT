/*
 * Created on Jul 31, 2009
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


package com.biglybt.core.devices.impl;

import java.io.File;
import java.util.*;

import com.biglybt.core.devices.Device;
import com.biglybt.core.devices.DeviceManagerListener;
import com.biglybt.core.devices.DeviceMediaRenderer;
import com.biglybt.core.devices.DeviceTemplate;
import com.biglybt.core.drivedetector.DriveDetectedInfo;
import com.biglybt.core.drivedetector.DriveDetectedListener;
import com.biglybt.core.drivedetector.DriveDetectorFactory;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.util.MapUtils;

public class
DeviceDriveManager
	implements DriveDetectedListener
{
	private DeviceManagerImpl		manager;

	Map<String,DeviceMediaRendererManual>	device_map = new HashMap<>();

	private AsyncDispatcher	async_dispatcher = new AsyncDispatcher();

	boolean	listener_added;

	protected
	DeviceDriveManager(
		DeviceManagerImpl		_manager )
	{
		manager = _manager;

		manager.addListener(
			new DeviceManagerListener()
			{
				@Override
				public void
				deviceAdded(
					Device		device )
				{
				}

				@Override
				public void
				deviceChanged(
					Device		device )
				{
				}

				@Override
				public void
				deviceAttentionRequest(
					Device		device )
				{
				}

				@Override
				public void
				deviceRemoved(
					Device		device )
				{
					synchronized( device_map ){

						Iterator<Map.Entry<String,DeviceMediaRendererManual>> it = device_map.entrySet().iterator();

						while( it.hasNext()){

							Map.Entry<String,DeviceMediaRendererManual> entry = it.next();

							if ( entry.getValue() == device ){

								it.remove();
							}
						}
					}
				}

				@Override
				public void
				deviceManagerLoaded()
				{
				}
			});

		if ( manager.getAutoSearch()){

			listener_added = true;

			DriveDetectorFactory.getDeviceDetector().addListener( this );
		}
	}

	protected void
	search()
	{
		async_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					if ( listener_added ){

						DriveDetectedInfo[] info = DriveDetectorFactory.getDeviceDetector().getDetectedDriveInfo();

						for ( DriveDetectedInfo i: info ){

							driveRemoved( i );

							driveDetected( i );
						}

						return;
					}

					try{
							// this should synchronously first any discovered drives

						DriveDetectorFactory.getDeviceDetector().addListener( DeviceDriveManager.this );

					}finally{

						DriveDetectorFactory.getDeviceDetector().removeListener( DeviceDriveManager.this );
					}
				}
			});
	}

	@Override
	public void
	driveDetected(
		final DriveDetectedInfo info )
 {
		//System.out.println("DD " + info.getLocation() + " via " + Debug.getCompressedStackTrace());
		async_dispatcher.dispatch(new AERunnable() {
			@Override
			public void runSupport() {

				Map<String, Object> infoMap = info.getInfoMap();

				boolean isWritableUSB = MapUtils.getMapBoolean(infoMap, "isWritableUSB", false);

				File root = info.getLocation();

				String sProdID = MapUtils.getMapString(infoMap, "ProductID",
						MapUtils.getMapString(infoMap, "Product Name", "")).trim();
				String sVendor = MapUtils.getMapString(infoMap, "VendorID",
						MapUtils.getMapString(infoMap, "Vendor Name", "")).trim();

				// Historically, we gave IDs to Motorola, Samsung, and HTC phones
				// based on their vendor and product id only.  We need to maintain
				// this ID in order to not create duplicates.
				// Fortunately, both Motorola and Samsung include their model id in the
				// sProdID.  HTC doesn't, however, their PID doesn't identify unique
				// models anyway, so including that wouldn't have helped anyway
				if ((sVendor.equalsIgnoreCase("htc") && sProdID.equalsIgnoreCase("android phone"))
						|| (sVendor.toLowerCase().contains("motorola") && sProdID.length() > 0)
						|| sVendor.equalsIgnoreCase("samsung")) {

					if (isWritableUSB && sVendor.equalsIgnoreCase("samsung")) {
						// Samsungs that start with Y are MP3 players
						// Samsungs that don't have a dash aren't smart phones (none that we know of anyway..)
						// Fake not writable so we remove the device instead of adding it
						isWritableUSB = !sProdID.startsWith("Y")
								&& sProdID.matches(".*[A-Z]-.*");
					}

					String name = sProdID.startsWith(sVendor) ? "" : sVendor;
					if (sVendor.length() > 0) {
						name += " ";
					}
					name += sProdID;

					String id = "android.";
					id += sProdID.replaceAll(" ", ".").toLowerCase();
					if (sVendor.length() > 0) {
						id += "." + sVendor.replaceAll(" ", ".").toLowerCase();
					}

					if (isWritableUSB) {
						addDevice(name, id, root, new File(root, "videos"), true);
					} else {
						//Fixup old bug where we were adding Samsung hard drives as devices
						Device existingDevice = getDeviceMediaRendererByClassification(id);
						if (existingDevice != null) {
							existingDevice.remove();
						}
					}
					return;
				} else if (isWritableUSB && sVendor.toLowerCase().equals("rim")) {
					String name = sVendor;
  				if (name.length() > 0) {
  					name += " ";
  				}
  				name += sProdID;
					String id = "";
					id += sProdID.replaceAll(" ", ".").toLowerCase();
					if (sVendor.length() > 0) {
						id += "." + sVendor.replaceAll(" ", ".").toLowerCase();
					}
					DeviceMediaRendererManual device = addDevice(name, id, root, new File(root, "videos"), false);
					if (device != null) {
						device.setImageID("bb");
					}
					return;
				}

				if (!isWritableUSB) {
					return;
				}

				if (root.exists()) {

					File[] folders = root.listFiles();

					if (folders != null) {

						Set<String> names = new HashSet<>();

						for (File file : folders) {

							names.add(file.getName().toLowerCase());
						}

						if (names.contains("psp") && names.contains("video")) {
							addDevice("PSP", "sony.PSP", root, new File(root, "VIDEO"), false);
							return;
						}
					}
				}

				String pid = MapUtils.getMapString(infoMap, "PID", null);
				String vid = MapUtils.getMapString(infoMap, "VID", null);
				if (pid != null && vid != null) {
					String name = sProdID.startsWith(sVendor) ? "" : sVendor;
  				if (name.length() > 0) {
  					name += " ";
  				}
  				name += sProdID;

  				String id = "";
					id += sProdID.replaceAll(" ", ".").toLowerCase();
					id += "." + pid.toLowerCase();
					if (sVendor.length() > 0) {
						id += "." + sVendor.replaceAll(" ", ".").toLowerCase();
					}
					id += "." + vid.toLowerCase();

					// cheap hack to detect the PSP when it has no psp or video dir
					if (id.equals("\"psp\".ms.02d2.sony.054c")
							|| id.equals("\"psp\".ms.0381.sony.054c")) {
						if (addDevice("PSP", "sony.PSP", root, new File(root, "VIDEO"), false) != null) {
							return;
						}
					}

					addDevice(name, id, root, new File(root, "video"), true);
				}
			}
		});
	}

	protected DeviceMediaRenderer getDeviceMediaRendererByClassification(String target_classification) {
		DeviceImpl[] devices = manager.getDevices();

		for ( DeviceImpl device: devices ){

			if ( device instanceof DeviceMediaRenderer ){

				DeviceMediaRenderer renderer = (DeviceMediaRenderer)device;

				String classification = renderer.getClassification();

				if ( classification.equalsIgnoreCase( target_classification )){

					return renderer;
				}
			}
		}

		return null;
	}

	protected DeviceMediaRendererManual addDevice(
			String target_name,
			String target_classification,
			File root,
			File target_directory,
			boolean generic)
	{

		DeviceMediaRenderer existingDevice = getDeviceMediaRendererByClassification(target_classification);
		if (existingDevice instanceof DeviceMediaRendererManual ) {
			mapDevice( (DeviceMediaRendererManual) existingDevice, root, target_directory );

			existingDevice.setGenericUSB(generic);
			return (DeviceMediaRendererManual) existingDevice;
		}

		DeviceTemplate[] templates = manager.getDeviceTemplates( Device.DT_MEDIA_RENDERER );

		DeviceMediaRendererManual	renderer = null;

		for ( DeviceTemplate template: templates ){

			if ( template.getClassification().equalsIgnoreCase( target_classification )){

				try{
					renderer = (DeviceMediaRendererManual)template.createInstance( target_name );

					break;

				}catch( Throwable e ){

					log( "Failed to add device", e );
				}
			}
		}

		if ( renderer == null ){

				// damn, the above doesn't work until devices is turned on...

			try{
				renderer = (DeviceMediaRendererManual)manager.createDevice( Device.DT_MEDIA_RENDERER, null, target_classification, target_name, true );

			}catch( Throwable e ){

				log( "Failed to add device", e );
			}
		}

		if ( renderer != null ){

			try{
				renderer.setAutoCopyToFolder( true );
				// This will cause a change event
				renderer.setGenericUSB(generic);

				mapDevice( renderer, root, target_directory );

				return renderer;

			}catch( Throwable e ){

				log( "Failed to add device", e );
			}
		}
		return renderer;
	}

	@Override
	public void
	driveRemoved(
		final DriveDetectedInfo info )
	{
		async_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					unMapDevice( info.getLocation());
				}
			});
	}

	protected void
	mapDevice(
		DeviceMediaRendererManual		renderer,
		File							root,
		File							copy_to )
	{
		DeviceMediaRendererManual	existing;

		synchronized( device_map ){

			existing = device_map.put( root.getAbsolutePath(), renderer );
		}

		if ( existing != null && existing != renderer ){

			log( "Unmapped " + existing.getName() + " from " + root );

			existing.setCopyToFolder( null );
		}

		log( "Mapped " + renderer.getName() + " to " + root );

		renderer.setCopyToFolder( copy_to );

		renderer.setLivenessDetectable( true );

		renderer.alive();
	}

	protected void
	unMapDevice(
		File							root )
	{
		DeviceMediaRendererManual existing;

		synchronized( device_map ){

			existing = device_map.remove( root.getAbsolutePath());
		}

		if ( existing != null ){

			log( "Unmapped " + existing.getName() + " from " + root );

			existing.setCopyToFolder( null );

			existing.dead();
		}
	}

	protected void
	log(
		String str )
	{
		manager.log( "DriveMan: " + str );
	}

	protected void
	log(
		String 		str,
		Throwable 	e )
	{
		manager.log( "DriveMan: " + str, e );
	}
}
