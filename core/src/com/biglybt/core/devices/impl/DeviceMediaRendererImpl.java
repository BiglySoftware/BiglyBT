/*
 * Created on Jan 28, 2009
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
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.devices.Device;
import com.biglybt.core.devices.DeviceManagerException;
import com.biglybt.core.devices.DeviceMediaRenderer;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.*;
import com.biglybt.net.upnp.UPnPDevice;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.sharing.ShareResourceFile;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;

public class
DeviceMediaRendererImpl
	extends DeviceUPnPImpl
	implements DeviceMediaRenderer
{
	private static final int INSTALL_CHECK_PERIOD	= 60*1000;
	private static final int TAG_SHARE_CHECK_TICKS	= INSTALL_CHECK_PERIOD / DeviceManagerImpl.DEVICE_UPDATE_PERIOD;

	public
	DeviceMediaRendererImpl(
		DeviceManagerImpl	_manager,
		UPnPDevice			_device )
	{
		super( _manager, _device, Device.DT_MEDIA_RENDERER );
	}

	public
	DeviceMediaRendererImpl(
		DeviceManagerImpl	_manager,
		String				_classification )
	{
		super( _manager, Device.DT_MEDIA_RENDERER, _classification );
	}

	public
	DeviceMediaRendererImpl(
		DeviceManagerImpl	_manager,
		String				_uuid,
		String				_classification,
		boolean				_manual,
		String				_name )
	{
		super( _manager, Device.DT_MEDIA_RENDERER, _uuid, _classification, _manual, _name );
	}

	public
	DeviceMediaRendererImpl(
		DeviceManagerImpl	_manager,
		String				_uuid,
		String				_classification,
		boolean				_manual )
	{
		super( _manager, Device.DT_MEDIA_RENDERER, _uuid, _classification, _manual );
	}

	protected
	DeviceMediaRendererImpl(
		DeviceManagerImpl	_manager,
		Map					_map )

		throws IOException
	{
		super(_manager, _map );
	}

	@Override
	public void setAddress(InetAddress address) {
		super.setAddress(address);

		if ( getType() == DT_MEDIA_RENDERER) {
			//System.out.println("Set Address " + address.getHostAddress() + "; " + getName() + "/" + getClassification());

			boolean hasUPnPDevice = getUPnPDevice() != null;
			DeviceImpl[] devices = getManager().getDevices();
			for (DeviceImpl device : devices) {
				if (device == this || device.getID().equals(getID())
						|| !((device instanceof DeviceUPnPImpl))) {
					continue;
				}
				DeviceUPnPImpl deviceUPnP = ((DeviceUPnPImpl) device);
				if (!address.equals(device.getAddress()) || !device.isAlive()) {
					continue;
				}


				if (hasUPnPDevice) {
					boolean no_auto_hide = device.getPersistentBooleanProperty( DeviceImpl.PP_DONT_AUTO_HIDE, false );

					if (device.getType() == DT_MEDIA_RENDERER && !no_auto_hide ) {
						// prefer UPnP Device over Manual one added by a Browse event
						if (deviceUPnP.getUPnPDevice() != null) {
							int fileCount = deviceUPnP.getFileCount();
							if (fileCount == 0 && !device.isHidden()) {
								log("Hiding " + device.getName() + "/"
										+ device.getClassification() + "/" + device.getID()
										+ " due to " + getName() + "/" + getClassification() + "/"
										+ getID());
  							device.setHidden(true);
							}
						}
					}
				} else {
					if (device.getType() == DT_MEDIA_RENDERER ){
						boolean no_auto_hide = getPersistentBooleanProperty( DeviceImpl.PP_DONT_AUTO_HIDE, false );

						if ( !no_auto_hide ){
							int fileCount = getFileCount();
							// prefer UPnP Device over Manual one added by a Browse event
							if (fileCount == 0 && !isHidden()) {
								log("hiding " + getName() + "/" + getClassification() + "/"
										+ getID() + " due to " + device.getName() + "/"
										+ device.getClassification() + "/" + device.getID());
								setHidden(true);
							} else if (fileCount > 0 && Constants.IS_CVS_VERSION && isHidden()) {
								// Fix beta bug where we hid devices that had files.  Remove after 4605
								setHidden(false);
							}
						}
					} else {
						// Device has UPnP stuff, but did not register itself as
						// renderer.
  					UPnPDevice upnpDevice = deviceUPnP.getUPnPDevice();
  					if (upnpDevice != null) {
    					String manufacturer = upnpDevice.getManufacturer();
    					if (manufacturer == null || !( manufacturer.startsWith("Vuze") || manufacturer.startsWith("BiglyBT"))){
    						log("Linked " + getName() + " to UPnP Device " + device.getName());
      					setUPnPDevice(upnpDevice);
      					setDirty();
    					}
  					}
					}
				}
				break;
			}
		}
	}

	@Override
	protected boolean
	updateFrom(
		DeviceImpl		_other,
		boolean			_is_alive )
	{
		if ( !super.updateFrom( _other, _is_alive )){

			return( false );
		}

		if ( !( _other instanceof DeviceMediaRendererImpl )){

			Debug.out( "Inconsistent" );

			return( false );
		}

		DeviceMediaRendererImpl other = (DeviceMediaRendererImpl)_other;

		return( true );
	}

	@Override
	protected void
	initialise()
	{
		super.initialise();
	}

	static TorrentAttribute	share_ta;
	static List<Object[]>	share_requests		= new ArrayList<>();
	private static AsyncDispatcher	share_dispatcher 	= new AsyncDispatcher();

	@Override
	protected void
	updateStatus(
		int		tick_count )
	{
		super.updateStatus(tick_count);

		if ( 	tick_count > 0 &&
				tick_count % TAG_SHARE_CHECK_TICKS == 0 ){

			long	tag_id = getAutoShareToTagID();

			if ( tag_id != -1 ){

				synchronized( DeviceMediaRendererImpl.class ){

					if ( share_ta == null ){

						share_ta = PluginInitializer.getDefaultInterface().getTorrentManager().getPluginAttribute( "DeviceMediaRendererImpl:tag_share" );
					}
				}

				TagManager tm = TagManagerFactory.getTagManager();

				Tag assigned_tag = tm.lookupTagByUID( tag_id );

				if ( assigned_tag != null ){

					assigned_tag.setPublic( false );	// not going to want this to be public

					synchronized( share_requests ){

						if ( share_requests.size() == 0 ){

							Set<Taggable> taggables = assigned_tag.getTagged();

							Set<String>	done_files = new HashSet<>();

							for ( Taggable temp: taggables ){

								if ( !( temp instanceof DownloadManager )){

									continue;
								}

								DownloadManager dm = (DownloadManager)temp;

								Download download = PluginCoreUtils.wrap( dm );

								String attr = download.getAttribute( share_ta );

								if ( attr != null ){

									done_files.add( attr );
								}
							}

							TranscodeFileImpl[] files = getFiles();

							for ( TranscodeFileImpl file: files ){

								if ( file.isComplete()){

									try{
										File target_file = file.getTargetFile().getFile( true );

										long size = target_file.length();

										if ( target_file.exists() && size > 0 ){

											String suffix = " (" + file.getProfileName() + " - " + DisplayFormatters.formatByteCountToKiBEtc( size ) + ")";

											String share_name	= file.getName() + suffix;
											String key 			= target_file.getName() + suffix;

											if ( !done_files.contains( key )){

												share_requests.add( new Object[]{ key, target_file, share_name, assigned_tag });
											}
										}
									}catch( Throwable e ){

									}
								}
							}

							if ( share_requests.size() > 0 ){

								shareRequestAdded();
							}
						}
					}
				}
			}
		}
	}

	private void
	shareRequestAdded()
	{
		share_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					List<Object[]>	to_process;

					synchronized( share_requests ){

						to_process = new ArrayList<>(share_requests);
					}

					for ( Object[] entry: to_process ){

						try{
							String	key 	= (String)entry[0];
							File	file	= (File)entry[1];
							String	name 	= (String)entry[2];
							Tag		tag 	= (Tag)entry[3];

							log( "Auto sharing " + name + " (" + file + ") to tag " + tag.getTagName( true ));

							Map<String,String>	properties = new HashMap<>();

							properties.put( ShareManager.PR_USER_DATA, "device:autoshare" );

								// currently no way for user to explicitly specify the networks to use so use defaults

							String[] networks = AENetworkClassifier.getDefaultNetworks();

							String networks_str = "";

							for ( String net: networks ){

								networks_str += (networks_str.length()==0?"":",") + net;
							}

							properties.put( ShareManager.PR_NETWORKS, networks_str );

							properties.put( ShareManager.PR_TAGS, String.valueOf( tag.getTagUID()));

							PluginInterface pi = PluginInitializer.getDefaultInterface();

							ShareResourceFile srf = pi.getShareManager().addFile( file, properties );

							Torrent torrent = srf.getItem().getTorrent();

							final Download download = pi.getPluginManager().getDefaultPluginInterface().getShortCuts().getDownload( torrent.getHash());

							if ( download == null ){

								throw( new Exception( "Download no longer exists" ));
							}

							DownloadManager dm = PluginCoreUtils.unwrap( download );

							dm.getDownloadState().setDisplayName( name );

							download.setAttribute( share_ta, key );

						}catch( Throwable e ){

							log( "Auto sharing failed", e );
						}
					}

					synchronized( share_requests ){

						share_requests.removeAll( to_process );
					}
				}
			});
	}

	@Override
	protected void
	destroy()
	{
		super.destroy();
	}

	@Override
	public boolean
	canCopyToDevice()
	{
		return( false );
	}

	@Override
	public boolean
	getAutoCopyToDevice()
	{
		return( false );
	}

	@Override
	public void
	setAutoCopyToDevice(
		boolean		auto )
	{
	}

	@Override
	public int
	getCopyToDevicePending()
	{
		return( 0 );
	}

	@Override
	public boolean
	canAutoStartDevice()
	{
		return( false );
	}

	@Override
	public boolean
	getAutoStartDevice()
	{
		return( false );
	}

	@Override
	public void
	setAutoStartDevice(
		boolean		auto )
	{
	}

	@Override
	public boolean
	canCopyToFolder()
	{
		return( false );
	}

	@Override
	public void
	setCanCopyToFolder(
		boolean		can )
	{
		// nothing to do
	}

	@Override
	public File
	getCopyToFolder()
	{
		return( null );
	}

	@Override
	public void
	setCopyToFolder(
		File		file )
	{
	}

	@Override
	public int
	getCopyToFolderPending()
	{
		return( 0 );
	}

	@Override
	public boolean
	getAutoCopyToFolder()
	{
		return( false );
	}

	@Override
	public void
	setAutoCopyToFolder(
		boolean		auto )
	{
	}

	@Override
	public void
	manualCopy()

		throws DeviceManagerException
	{
		throw( new DeviceManagerException( "Unsupported" ));
	}

	@Override
	public boolean
	canShowCategories()
	{
		return( false );
	}

	@Override
	public void
	setShowCategories(
		boolean	b )
	{
		setPersistentBooleanProperty( PP_REND_SHOW_CAT, b );
	}

	@Override
	public boolean
	getShowCategories()
	{
		return( getPersistentBooleanProperty( PP_REND_SHOW_CAT, getShowCategoriesDefault()));
	}

	protected boolean
	getShowCategoriesDefault()
	{
		return( false );
	}

	@Override
	protected void
	getDisplayProperties(
		List<String[]>	dp )
	{
		super.getDisplayProperties( dp );

		if ( canCopyToFolder()){

			addDP( dp, "devices.copy.folder.auto", getAutoCopyToFolder());
			addDP( dp, "devices.copy.folder.dest", getCopyToFolder());
		}

		if ( canCopyToDevice()){
			addDP( dp, "devices.copy.device.auto", getAutoCopyToDevice());
		}

		if ( canShowCategories()){

			addDP( dp, "devices.cat.show", getShowCategories());

		}
		super.getTTDisplayProperties( dp );
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		super.generate( writer );

		try{
			writer.indent();

			generateTT( writer );

		}finally{

			writer.exdent();
		}
	}
}
