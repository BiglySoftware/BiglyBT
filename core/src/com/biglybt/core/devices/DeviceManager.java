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

import java.io.File;
import java.net.InetAddress;

import com.biglybt.net.upnp.UPnPDevice;

public interface
DeviceManager
{
		// not the best place for these, but it'll do for the moment

	public static final String CONFIG_VIEW_HIDE_REND_GENERIC	= "device.sidebar.ui.rend.hidegeneric";
	public static final String CONFIG_VIEW_SHOW_ONLY_TAGGED		= "device.sidebar.ui.rend.showonlytagged";

	public DeviceTemplate[]
	getDeviceTemplates(
		int		device_type );

	public DeviceManufacturer[]
	getDeviceManufacturers(
		int		device_type );

	public Device[]
	getDevices();

	public Device
	addVirtualDevice(
		int					type,
		String				uid,
		String				classification,
		String				name )

		throws DeviceManagerException;

	Device addInetDevice(
		int					type,
		String					uid,
		String					classification,
		String					name,
		InetAddress					address )

		throws DeviceManagerException;

	public void
	search(
		int						max_millis,
		DeviceSearchListener	listener );

	public boolean
	getAutoSearch();

	public void
	setAutoSearch(
		boolean	auto );

	public int
	getAutoHideOldDevicesDays();

	public void
	setAutoHideOldDevicesDays(
		int		days );

	public boolean
	isRSSPublishEnabled();

	public void
	setRSSPublishEnabled(
		boolean		enabled );

	public String
	getRSSLink();

	public UnassociatedDevice[]
	getUnassociatedDevices();

	public TranscodeManager
	getTranscodeManager();

	public File
	getDefaultWorkingDirectory();

	public void
	setDefaultWorkingDirectory(
		File		dir );

	public boolean
	isBusy(
		int	device_type );

	public boolean
	isTiVoEnabled();

	public void
	setTiVoEnabled(
		boolean	enabled );

	public boolean
	getDisableSleep();

	public void
	setDisableSleep(
		boolean		b );

	public String
	getLocalServiceName();

	public void
	addDiscoveryListener(
		DeviceManagerDiscoveryListener	listener );

	public void
	removeDiscoveryListener(
		DeviceManagerDiscoveryListener	listener );

	public void
	addListener(
		DeviceManagerListener		listener );

	public void
	removeListener(
		DeviceManagerListener		listener );

	boolean isTranscodeManagerInitialized();

	public interface
	UnassociatedDevice
	{
		public InetAddress
		getAddress();

		public String
		getDescription();
	}

	public interface
	DeviceManufacturer
	{
		public String
		getName();

		public DeviceTemplate[]
		getDeviceTemplates();
	}

	/**
	 * @param upnpDevice
	 * @return
	 *
	 * @since 5.0.0.1
	 */
	Device
	findDevice(
			UPnPDevice upnpDevice);
}
