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

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.content.ContentDownload;
import com.biglybt.core.content.ContentFile;
import com.biglybt.core.content.PlatformContentDirectory;
import com.biglybt.core.devices.*;
import com.biglybt.core.devices.DeviceManager.UnassociatedDevice;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.core.util.UUIDGenerator;
import com.biglybt.net.upnp.UPnPDevice;
import com.biglybt.net.upnp.UPnPDeviceImage;
import com.biglybt.net.upnp.UPnPRootDevice;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAttributeListener;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.util.PlayUtils;

public abstract class
DeviceUPnPImpl
	extends DeviceImpl
	implements DeviceUPnP, TranscodeTargetListener
{
	private static final Object UPNPAV_FILE_KEY = new Object();

	private static final Map<String,ContentFile>	acf_map = new WeakHashMap<>();

	protected static String
	getDisplayName(
		UPnPDevice		device )
	{
		UPnPDevice	root = device.getRootDevice().getDevice();

		String fn = root.getFriendlyName();

		if ( fn == null || fn.length() == 0 ){

			fn = device.getFriendlyName();
		}

		String	dn = root.getModelName();

		if ( dn == null || dn.length() == 0 ){

			dn = device.getModelName();
		}

		if ( dn != null && dn.length() > 0 ){

			if ( !fn.contains( dn ) ){

				fn += " (" + dn + ")";
			}
		}

		return( fn );
	}

	final String MY_ACF_KEY;


	final DeviceManagerUPnPImpl	upnp_manager;
	private volatile UPnPDevice		device_may_be_null;

	private IPCInterface		upnpav_ipc;
	private TranscodeProfile	dynamic_transcode_profile;
	private Map<String,ContentFile>	dynamic_xcode_map;


	protected
	DeviceUPnPImpl(
		DeviceManagerImpl		_manager,
		UPnPDevice				_device,
		int						_type )
	{
		super( _manager, _type, _type + "/" + _device.getRootDevice().getUSN(), getDisplayName( _device ), false );

		upnp_manager		= _manager.getUPnPManager();
		setUPnPDevice(_device);

		MY_ACF_KEY = getACFKey();
	}

	protected
	DeviceUPnPImpl(
		DeviceManagerImpl	_manager,
		int					_type,
		String				_classification )

	{
		super( _manager, _type, UUIDGenerator.generateUUIDString(), _classification, true );

		upnp_manager		= _manager.getUPnPManager();

		MY_ACF_KEY = getACFKey();
	}

	protected
	DeviceUPnPImpl(
		DeviceManagerImpl	_manager,
		int					_type,
		String				_uuid,
		String				_classification,
		boolean				_manual,
		String				_name )

	{
		super( _manager, _type, _uuid==null?UUIDGenerator.generateUUIDString():_uuid, _classification, _manual, _name );

		upnp_manager		= _manager.getUPnPManager();

		MY_ACF_KEY = getACFKey();
	}

	protected
	DeviceUPnPImpl(
		DeviceManagerImpl	_manager,
		int					_type,
		String				_uuid,
		String				_classification,
		boolean				_manual )

	{
		super( _manager, _type, _uuid, _classification, _manual );

		upnp_manager		= _manager.getUPnPManager();

		MY_ACF_KEY = getACFKey();
	}

	protected
	DeviceUPnPImpl(
		DeviceManagerImpl	_manager,
		Map					_map )

		throws IOException
	{
		super(_manager, _map );

		upnp_manager		= _manager.getUPnPManager();

		MY_ACF_KEY = getACFKey();
	}

	protected String
	getACFKey()
	{
		return( "DeviceUPnPImpl:device:" + getID());
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

		if ( !( _other instanceof DeviceUPnPImpl )){

			Debug.out( "Inconsistent" );

			return( false );
		}

		DeviceUPnPImpl other = (DeviceUPnPImpl)_other;

		setUPnPDevice(other.device_may_be_null);

		return( true );
	}

	@Override
	protected void
	initialise()
	{
		super.initialise();
	}

	protected void
	UPnPInitialised()
	{
	}

	@Override
	protected void
	destroy()
	{
		super.destroy();
	}

	protected DeviceManagerUPnPImpl
	getUPnPDeviceManager()
	{
		return( upnp_manager );
	}

	@Override
	public UPnPDevice
	getUPnPDevice()
	{
		return( device_may_be_null );
	}

	protected void
	setUPnPDevice(
			UPnPDevice device)
	{
		device_may_be_null = device;
		if (device != null) {
			// triggers any address change logic
			setAddress(getAddress());
		}
		setDirty(false);
	}

	@Override
	public boolean
	isBrowsable()
	{
		return( true );
	}

	@Override
	public browseLocation[]
	getBrowseLocations()
	{
		List<browseLocation>	locs = new ArrayList<>();

		UPnPDevice device = device_may_be_null;

		if ( device != null ){

			URL		presentation = getPresentationURL( device );

			if ( presentation != null ){

				locs.add( new browseLocationImpl( "device.upnp.present_url", presentation ));
			}

			int userMode = COConfigurationManager.getIntParameter("User Mode");

			if ( userMode > 1 ){

				locs.add( new browseLocationImpl( "device.upnp.desc_url", device.getRootDevice().getLocation()));
			}
		}

		return( locs.toArray( new browseLocation[ locs.size() ]));
	}

	public boolean
	canFilterFilesView()
	{
		return( true );
	}

	public void
	setFilterFilesView(
		boolean	filter )
	{
		boolean	existing = getFilterFilesView();

		if ( existing != filter ){

			setPersistentBooleanProperty( PP_FILTER_FILES, filter );

			IPCInterface ipc = upnpav_ipc;

			if ( ipc != null ){

				try{
					ipc.invoke( "invalidateDirectory", new Object[]{});

				}catch( Throwable e ){
				}
			}
		}
	}

	public boolean
	getFilterFilesView()
	{
		return( getPersistentBooleanProperty( PP_FILTER_FILES, true ));
	}

	@Override
	public boolean
	isLivenessDetectable()
	{
		return( true );
	}

	protected URL
	getLocation()
	{
		UPnPDevice device = device_may_be_null;

		if ( device != null ){

			UPnPRootDevice root = device.getRootDevice();

			return( root.getLocation());
		}

		return( null );
	}

	public boolean
	canAssociate()
	{
		return( true );
	}

	public void
	associate(
		UnassociatedDevice	assoc )
	{
		if ( isAlive()){

			return;
		}

		setAddress( assoc.getAddress());

		alive();
	}

	@Override
	public InetAddress
	getAddress()
	{
		try{

			UPnPDevice device = device_may_be_null;

			if ( device != null ){

				UPnPRootDevice root = device.getRootDevice();

				URL location = root.getLocation();

				return( InetAddress.getByName( location.getHost() ));

			}else{

				InetAddress address = (InetAddress)getTransientProperty( TP_IP_ADDRESS );

				if ( address != null ){

					return( address );
				}

				String last = getPersistentStringProperty( PP_IP_ADDRESS );

				if ( last != null && last.length() > 0 ){

					return( InetAddress.getByName( last ));
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);

		}

		return( null );
	}

	@Override
	public void
	setAddress(
		InetAddress	address )
	{
		setTransientProperty( TP_IP_ADDRESS, address );

		setPersistentStringProperty( PP_IP_ADDRESS, address.getHostAddress());
	}

	public boolean
	canRestrictAccess()
	{
		return( true );
	}

	public String
	getAccessRestriction()
	{
		return( getPersistentStringProperty( PP_RESTRICT_ACCESS, "" ));
	}

	public void
	setAccessRestriction(
		String		str )
	{
		setPersistentStringProperty( PP_RESTRICT_ACCESS, str );
	}

	protected URL
	getStreamURL(
		TranscodeFileImpl		file )
	{
		return( getStreamURL( file, null ));
	}

	@Override
	protected URL
	getStreamURL(
		TranscodeFileImpl		file,
		String					host )
	{
		browseReceived();

		return( super.getStreamURL( file, host ));
	}

	@Override
	protected String
	getMimeType(
		TranscodeFileImpl		file )
	{
		browseReceived();

		return( super.getMimeType(file));
	}

	protected void
	browseReceived()
	{
		IPCInterface ipc = upnp_manager.getUPnPAVIPC();

		if ( ipc == null ){

			return;
		}

		TranscodeProfile default_profile = getDefaultTranscodeProfile();

		if ( default_profile == null ){

			TranscodeProfile[] profiles = getTranscodeProfiles();

			for ( TranscodeProfile p: profiles ){

				if ( p.isStreamable()){

					default_profile = p;

					break;
				}
			}
		}

		synchronized( this ){

			if ( upnpav_ipc != null ){

				return;
			}

			upnpav_ipc = ipc;

			if ( default_profile != null && default_profile.isStreamable()){

				dynamic_transcode_profile	= default_profile;
			}
		}

		addListener( this );

		TranscodeFile[]	transcode_files = getFiles();

		for ( TranscodeFile file: transcode_files ){

			fileAdded( file, false );
		}
	}

	protected void
	resetUPNPAV()
	{
		Set<String>	to_remove = new HashSet<>();

		synchronized( this ){

			if ( upnpav_ipc == null ){

				return;
			}

			upnpav_ipc = null;

			dynamic_transcode_profile = null;

			dynamic_xcode_map = null;

			removeListener( this );

			TranscodeFileImpl[]	transcode_files = getFiles();

			for ( TranscodeFileImpl file: transcode_files ){

				file.setTransientProperty( UPNPAV_FILE_KEY, null );

				to_remove.add( file.getKey());
			}
		}

		synchronized( acf_map ){

			for (String key: to_remove ){

				acf_map.remove( key );
			}
		}
	}


	protected boolean
	setupStreamXCode(
		TranscodeFileImpl		transcode_file )
	{
		final TranscodeJobImpl	job = transcode_file.getJob();

		if ( job == null ){

				// may have just completed, say things are OK as caller can continue

			return( transcode_file.isComplete());
		}

		final String tf_key = transcode_file.getKey();

		ContentFile acf;

		synchronized( acf_map ){

			acf = acf_map.get( tf_key );
		}

		if ( acf != null ){

			return( true );
		}

		IPCInterface			ipc	= upnpav_ipc;

		if ( ipc == null ){

			return( false );
		}

		if ( transcode_file.getDurationMillis() == 0 ){

			return( false );
		}

		try{
			final DiskManagerFileInfo stream_file =
				new TranscodeJobOutputLeecher( job, transcode_file );

			acf =	new ContentFile()
					{
					   	@Override
					    public DiskManagerFileInfo
					   	getFile()
					   	{
					   		return( stream_file );
					   	}

						@Override
						public Object
						getProperty(
							String		name )
						{
								// TODO: duration etc

							if ( name.equals( MY_ACF_KEY )){

								return( new Object[]{ DeviceUPnPImpl.this, tf_key });

							}else if ( name.equals( PT_PERCENT_DONE )){

								return( new Long(1000));

							}else if ( name.equals( PT_ETA )){

								return( new Long(0));
							}

							return( null );
						}
					};

			synchronized( acf_map ){

				acf_map.put( tf_key, acf );
			}

			ipc.invoke( "addContent", new Object[]{ acf });

			log( "Set up stream-xcode for " + transcode_file.getName());

			return( true );

		}catch( Throwable e ){

			return( false );
		}
	}

	protected boolean
	isVisible(
		ContentDownload file )
	{
		if ( getFilterFilesView() || file == null ){

			return false;
		}

		Download download = file.getDownload();

		if ( download == null){

			return false;
		}

		if ( download.isComplete()){

			return true;
		}

		int numFiles = download.getDiskManagerFileCount();

		for ( int i = 0; i < numFiles; i++){

			DiskManagerFileInfo fileInfo = download.getDiskManagerFileInfo(i);

			if ( fileInfo == null || fileInfo.isDeleted() || fileInfo.isSkipped()){

				continue;
			}

			if ( fileInfo.getLength() == fileInfo.getDownloaded()){

				return true;

			}else if ( PlayUtils.canUseEMP( fileInfo )){

				return( true );
			}
		}

		return false;
	}

	protected boolean
	isVisible(
		ContentFile file )
	{
		if ( getFilterFilesView()){

			Object[] x = (Object[])file.getProperty( MY_ACF_KEY );

			if ( x != null && x[0] == this ){

				String	tf_key = (String)x[1];

				return( getTranscodeFile( tf_key ) != null );

			}else{

				return( false );
			}
		}else{

			if ( file == null ){

				return( false );
			}

			DiskManagerFileInfo fileInfo = file.getFile();

			if ( fileInfo == null || fileInfo.isDeleted() || fileInfo.isSkipped()){

				return( false );
			}

			if ( fileInfo.getLength() == fileInfo.getDownloaded()){

				return( true );

			}else if ( PlayUtils.canUseEMP( fileInfo )){

				return( true );
			}
		}

		return( false );
	}

	@Override
	public void
	fileAdded(
		TranscodeFile		_transcode_file )
	{
		fileAdded( _transcode_file, true );
	}

	public void
	fileAdded(
		TranscodeFile		_transcode_file,
		boolean				_new_file )
	{
		TranscodeFileImpl	transcode_file = (TranscodeFileImpl)_transcode_file;

		IPCInterface ipc = upnpav_ipc;

		synchronized( this ){

			if ( ipc == null ){

				return;
			}

			if ( !transcode_file.isComplete()){

				syncCategoriesAndTags( transcode_file, _new_file );

				return;
			}

			ContentFile acf = (ContentFile)transcode_file.getTransientProperty( UPNPAV_FILE_KEY );

			if ( acf != null ){

				return;
			}

			final String tf_key	= transcode_file.getKey();

			synchronized( acf_map ){

				acf = acf_map.get( tf_key );
			}

			if ( acf != null ){

				return;
			}

			try{
				final DiskManagerFileInfo 	f 		= transcode_file.getTargetFile();

				acf =
					new ContentFile()
					{
						@Override
						public DiskManagerFileInfo
					    getFile()
						{
							return( f );
						}

						@Override
						public Object
						getProperty(
							String		name )
						{
							if(  name.equals( MY_ACF_KEY )){

								return( new Object[]{ DeviceUPnPImpl.this, tf_key });

							}else if ( name.equals( PT_CATEGORIES )){

								TranscodeFileImpl	tf = getTranscodeFile( tf_key );

								if ( tf != null ){

									return( tf.getCategories());
								}

								return( new String[0] );

							}else if ( name.equals( PT_TAGS )){

								TranscodeFileImpl	tf = getTranscodeFile( tf_key );

								if ( tf != null ){

									return( tf.getTags( true ));
								}

								return( new String[0] );

							} else if (name.equals(PT_TITLE)) {

								TranscodeFileImpl	tf = getTranscodeFile( tf_key );

								if ( tf != null ){

									return( tf.getName());
								}
							}else{

								TranscodeFileImpl	tf = getTranscodeFile( tf_key );

								if ( tf != null ){

									long	res = 0;

									if ( name.equals( PT_DURATION )){

										res = tf.getDurationMillis();

									}else if ( name.equals( PT_VIDEO_WIDTH )){

										res = tf.getVideoWidth();

									}else if ( name.equals( PT_VIDEO_HEIGHT )){

										res = tf.getVideoHeight();

									}else if ( name.equals( PT_DATE )){

										res = tf.getCreationDateMillis();

									}else if ( name.equals( PT_PERCENT_DONE )){

										if ( tf.isComplete()){

											res = 1000;

										}else{

											TranscodeJob job = tf.getJob();

											if ( job == null ){

												res = 0;

											}else{

												res = 10*job.getPercentComplete();
											}
										}

										return( res );

									}else if ( name.equals( PT_ETA )){

										if ( tf.isComplete()){

											res = 0;

										}else{

											TranscodeJob job = tf.getJob();

											if ( job == null ){

												res = Long.MAX_VALUE;

											}else{

												res = job.getETASecs();
											}
										}

										return( res );
									}

									if ( res > 0 ){

										return( new Long( res ));
									}
								}
							}

							return( null );
						}
					};

				transcode_file.setTransientProperty( UPNPAV_FILE_KEY, acf );

				synchronized( acf_map ){

					acf_map.put( tf_key, acf );
				}

				syncCategoriesAndTags( transcode_file, _new_file );

				try{
					ipc.invoke( "addContent", new Object[]{ acf });

				}catch( Throwable e ){

					Debug.out( e );
				}
			}catch( TranscodeException e ){

				// file deleted
			}
		}
	}

	protected void
	syncCategoriesAndTags(
		TranscodeFileImpl		tf,
		boolean					inherit_from_download )
	{
		try{
			final Download dl = tf.getSourceFile().getDownload();

			if ( dl != null ){

					// only overwrite categories with the downloads ones if none already set

				if ( inherit_from_download ){

					setCategories( tf, dl );
					setTags( tf, dl );
				}

				final String tf_key = tf.getKey();

				dl.addAttributeListener(
					new DownloadAttributeListener()
					{
						@Override
						public void
						attributeEventOccurred(
							Download 			download,
							TorrentAttribute 	attribute,
							int 				eventType)
						{
							TranscodeFileImpl tf = getTranscodeFile( tf_key );

							if ( tf != null ){

								setCategories( tf, download );

							}else{

								dl.removeAttributeListener( this, upnp_manager.getCategoryAttibute(), DownloadAttributeListener.WRITTEN );
							}
						}
					},
					upnp_manager.getCategoryAttibute(),
					DownloadAttributeListener.WRITTEN );

				TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).addTagListener(
						PluginCoreUtils.unwrap( dl ),
						new TagListener()
						{
							@Override
							public void
							taggableSync(
								Tag tag)
							{
							}

							@Override
							public void
							taggableRemoved(
								Tag 		tag,
								Taggable 	tagged )
							{
								update( tagged );
							}

							@Override
							public void
							taggableAdded(
								Tag 		tag,
								Taggable 	tagged )
							{
								update( tagged );
							}

							private void
							update(
								Taggable	tagged )
							{
								TranscodeFileImpl tf = getTranscodeFile( tf_key );

								if ( tf != null ){

									setTags( tf, dl );

								}else{

									TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).removeTagListener( tagged, this );
								}
							}
						});
			}
		}catch( Throwable e ){

		}
	}

	protected void
	setCategories(
		TranscodeFileImpl		tf,
		Download				dl )
	{
		String	cat = dl.getCategoryName();

		if ( cat != null && cat.length() > 0 && !cat.equals( "Categories.uncategorized" )){

			tf.setCategories( new String[]{ cat });

		}else{

			tf.setCategories( new String[0] );
		}
	}

	protected void
	setTags(
		TranscodeFileImpl		tf,
		Download				dl )
	{
		List<Tag> tags = TagManagerFactory.getTagManager().getTagsForTaggable( PluginCoreUtils.unwrap( dl ));

		List<String>	tag_names = new ArrayList<>();

		for ( Tag tag: tags ){

			if ( tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

				tag_names.add( String.valueOf( tag.getTagUID()));
			}
		}

		tf.setTags( tag_names.toArray( new String[ tag_names.size()] ));
	}

	@Override
	public void
	fileChanged(
		TranscodeFile		file,
		int					type,
		Object				data )
	{
		if ( file.isComplete()){

			fileAdded( file, false );
		}

		if ( type == TranscodeTargetListener.CT_PROPERTY ){

			if ( data == TranscodeFile.PT_CATEGORY || data == TranscodeFile.PT_TAGS ){

				ContentFile acf;

				synchronized( acf_map ){

					acf = acf_map.get(((TranscodeFileImpl)file).getKey());
				}

				if ( acf != null ){

					if ( data == TranscodeFile.PT_TAGS ){

						PlatformContentDirectory.fireTagsChanged( acf );

					}else{

						PlatformContentDirectory.fireCatsChanged( acf );
					}
				}
			}
		}
	}

	@Override
	public void
	fileRemoved(
		TranscodeFile		file )
	{
		IPCInterface ipc = upnp_manager.getUPnPAVIPC();

		if ( ipc == null ){

			return;
		}

		synchronized( this ){

			ContentFile acf = (ContentFile)file.getTransientProperty( UPNPAV_FILE_KEY );

			if ( acf == null ){

				return;
			}

			file.setTransientProperty( UPNPAV_FILE_KEY, null );

			try{
				ipc.invoke( "removeContent", new Object[]{ acf });


			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		synchronized( acf_map ){

			acf_map.remove( ((TranscodeFileImpl)file).getKey());
		}
	}

	protected URL
	getPresentationURL(
		UPnPDevice		device )
	{
		String	presentation = device.getRootDevice().getDevice().getPresentation();

		if ( presentation != null ){

			try{
				URL url = new URL( presentation );

				return( url );

			}catch( Throwable e ){
			}
		}

		return( null );
	}

	@Override
	protected void
	getDisplayProperties(
		List<String[]>	dp )
	{
		super.getDisplayProperties( dp );

		UPnPDevice device = device_may_be_null;

		if ( device != null ){

			UPnPRootDevice root = device.getRootDevice();

			URL location = root.getLocation();

			addDP( dp, "dht.reseed.ip", location.getHost() + ":" + location.getPort());

			String	model_details 	= device.getModelName();
			String	model_url		= device.getModelURL();

			if ( model_url != null && model_url.length() > 0 ){
				model_details += " (" + model_url + ")";
			}

			String	manu_details 	= device.getManufacturer();
			String	manu_url		= device.getManufacturerURL();

			if ( manu_url != null && manu_url.length() > 0 ){
				manu_details += " (" + manu_url + ")";
			}

			addDP( dp, "device.model.desc", device.getModelDescription());
			addDP( dp, "device.model.name", model_details );
			addDP( dp, "device.model.num", device.getModelNumber());
			addDP( dp, "device.manu.desc", manu_details );

		}else{

			InetAddress ia = getAddress();

			if ( ia != null ){

				addDP( dp, "dht.reseed.ip", ia.getHostAddress());
			}
		}
		addDP( dp, "!Is Liveness Detectable!", isLivenessDetectable());
		if ( isManual() ){

			addDP( dp, "azbuddy.ui.table.online",  isAlive() );

			addDP( dp, "device.lastseen", getLastSeen()==0?"":new SimpleDateFormat().format(new Date( getLastSeen() )));
		}
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		super.generate( writer );

		try{
			writer.indent();

			UPnPDevice device = device_may_be_null;

			if ( device == null ){

				writer.println( "upnp_device=null" );

			}else{

				writer.println( "upnp_device=" + device.getFriendlyName());
			}

			writer.println( "dyn_xcode=" + (dynamic_transcode_profile==null?"null":dynamic_transcode_profile.getName()));
		}finally{

			writer.exdent();
		}
	}

	@Override
	public String getImageID() {
		String imageID = super.getImageID();
		// commented out existing imageid check so upnp device image overrides
		if (/*imageID == null && */ device_may_be_null != null && isAlive()) {
			UPnPDeviceImage[] images = device_may_be_null.getImages();
			if (images.length > 0) {
				URL location = getLocation();
				if (location != null) {
					String url = "http://" + location.getHost() + ":" + location.getPort();
					String imageUrl = images[0].getLocation();
					for (UPnPDeviceImage imageInfo : images) {
						String mime = imageInfo.getLocation();
						if (mime != null && mime.contains("png")) {
							imageUrl = imageInfo.getLocation();
							break;
						}
					}
					if (!imageUrl.startsWith("/")) {
						url += "/";
					}
					url += imageUrl;
					return url;
				}
			}
		}
		return imageID;
	}
	
	@Override
	public List<String> getImageIDs() {
		List<String> imageIDs = super.getImageIDs();
		// commented out existing imageid check so upnp device image overrides
		if (/*imageID == null && */ device_may_be_null != null && isAlive()) {
			UPnPDeviceImage[] images = device_may_be_null.getImages();
			if (images.length > 0) {
								
				URL location = getLocation();
				
				if (location != null) {
				
					List<String> urls = new ArrayList<>();

					String url = "http://" + location.getHost() + ":" + location.getPort();
					String imageUrl = images[0].getLocation();
					for (UPnPDeviceImage imageInfo : images) {
						String mime = imageInfo.getLocation();
						if (mime != null && mime.contains("png")) {
							imageUrl = imageInfo.getLocation();
							break;
						}
					}
					if (!imageUrl.startsWith("/")) {
						url += "/";
					}
					url += imageUrl;
					
					urls.add( url );
					
					// bug in BT smart hub that returns absolute URL that should be relative to add that in
					
					String loc_str = location.toExternalForm();
					
					int pos = loc_str.lastIndexOf( '/' );
					
					if ( pos != -1 ){
					
						loc_str = loc_str.substring( 0, pos + 1 );
					
						if ( imageUrl.startsWith( "/" )){
							
							imageUrl = imageUrl.substring( 1 );
						}
						
						loc_str += imageUrl;
						
						if ( !urls.contains( loc_str )){
						
							urls.add( loc_str );
						}
					}
					
					return urls;
				}
			}
		}
		return imageIDs;
	}
}
