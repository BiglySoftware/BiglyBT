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


package com.biglybt.core.devices.impl;

import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryListener;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.category.CategoryManagerListener;
import com.biglybt.core.devices.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginEvent;
import com.biglybt.pif.PluginEventListener;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;

public class
TranscodeManagerImpl
	implements TranscodeManager
{
	private DeviceManagerImpl		device_manager;
	private Core core;

	private volatile TranscodeProviderVuze	vuzexcode_provider;

	private CopyOnWriteList<TranscodeManagerListener>	listeners = new CopyOnWriteList<>();

	private TranscodeQueueImpl		queue = new TranscodeQueueImpl( this );

	AESemaphore	init_sem 	= new AESemaphore( "TM:init" );

	private boolean hooked_categories;

	Map<Category,Object[]> 	category_map = new HashMap<>();
	private CategoryListener		category_listener;
	private GlobalManagerListener	category_dl_listener;
	private TorrentAttribute		category_ta;

	private boolean 				hooked_tags;

	Map<Tag,Object[]> 		tag_map = new HashMap<>();
	private TagListener				tag_listener;

	private TorrentAttribute		tag_ta;

	protected
	TranscodeManagerImpl(
		DeviceManagerImpl		_dm )
	{
		device_manager	= _dm;

		core = CoreFactory.getSingleton();

		PluginInterface default_pi = PluginInitializer.getDefaultInterface();

		category_ta = default_pi.getTorrentManager().getPluginAttribute( "xcode.cat.done" );
		tag_ta 		= default_pi.getTorrentManager().getPluginAttribute( "xcode.tag.done" );

		final AESemaphore	plugin_sem 	= new AESemaphore( "TM:plugin" );

		default_pi.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{
					try{
						PluginInterface default_pi = PluginInitializer.getDefaultInterface();

						default_pi.addEventListener(
							new PluginEventListener()
							{
								@Override
								public void
								handleEvent(
									PluginEvent ev )
								{
									int	type = ev.getType();

									if ( type == PluginEvent.PEV_PLUGIN_OPERATIONAL ){

										pluginAdded((PluginInterface)ev.getValue());
									}
									if ( type == PluginEvent.PEV_PLUGIN_NOT_OPERATIONAL ){

										pluginRemoved((PluginInterface)ev.getValue());
									}
								}
							});

						PluginInterface[] plugins = default_pi.getPluginManager().getPlugins();

						for ( PluginInterface pi: plugins ){

							if ( pi.getPluginState().isOperational()){

								pluginAdded( pi );
							}
						}
					}finally{

						plugin_sem.releaseForever();
					}
				}

				@Override
				public void
				closedownInitiated()
				{
					plugin_sem.releaseForever();

						// we don't want things hanging around for init if we're closing

					init_sem.releaseForever();
				}

				@Override
				public void
				closedownComplete()
				{
				}
			});

		if ( !plugin_sem.reserve( 30*1000 )){

			Debug.out( "Timeout waiting for init" );

			AEDiagnostics.dumpThreads();
		}
	}

	protected void
	initialise()
	{
		queue.initialise();

		init_sem.releaseForever();
	}

	protected void
	pluginAdded(
		PluginInterface		pi )
	{
		if ( pi.getPluginState().isBuiltIn()){

			return;
		}

		String plugin_id = pi.getPluginID();

		if ( plugin_id.equals( "vuzexcode" )){

			boolean		added		= false;
			boolean		updated		= false;

			TranscodeProviderVuze provider	= null;

			synchronized( this ){

				if ( vuzexcode_provider == null ){

					vuzexcode_provider = new TranscodeProviderVuze( this, pi );

					added = true;

				}else if ( pi != vuzexcode_provider.getPluginInterface()){

					vuzexcode_provider.update( pi );

					updated = true;
				}

				provider = vuzexcode_provider;
			}

			if ( added ){

				for ( TranscodeManagerListener listener: listeners ){

					try{
						listener.providerAdded( provider );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}else if ( updated ){

				for ( TranscodeManagerListener listener: listeners ){

					try{
						listener.providerUpdated( provider );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}
	}

	protected void
	pluginRemoved(
		PluginInterface		pi )
	{
		String plugin_id = pi.getPluginID();

		if ( plugin_id.equals( "vuzexcode" )){

			TranscodeProviderVuze provider	= null;

			synchronized( this ){

				if ( vuzexcode_provider != null ){

					provider = vuzexcode_provider;

					vuzexcode_provider.destroy();

					vuzexcode_provider = null;
				}
			}

			if ( provider != null ){

				for ( TranscodeManagerListener listener: listeners ){

					try{
						listener.providerRemoved( provider );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}
	}

	protected void
	updateStatus(
		int	tick_count )
	{
		if ( queue != null ){

			queue.updateStatus( tick_count );

			if ( !hooked_categories ){

				hooked_categories = true;

				CategoryManager.addCategoryManagerListener(
					new CategoryManagerListener()
					{
						@Override
						public void
						categoryAdded(
							Category category )
						{
						}

						@Override
						public void
						categoryRemoved(
							Category category )
						{
						}

						@Override
						public void
						categoryChanged(
							Category category )
						{
							checkCategories();
						}
					});

				checkCategories();
			}

			if ( !hooked_tags ){

				hooked_tags = true;

				TagManagerFactory.getTagManager().addTagFeatureListener(
					TagFeature.TF_XCODE,
					new TagFeatureListener()
					{
						@Override
						public void
						tagFeatureChanged(
							Tag tag,
							int feature )
						{
							checkTags();
						}
					});


				checkTags();
			}
		}
	}

	void
	checkCategories()
	{
		Category[] cats = CategoryManager.getCategories();

		Map<Category,Object[]> active_map = new HashMap<>();

		for ( Category cat: cats ){

			String target = cat.getStringAttribute( Category.AT_AUTO_TRANSCODE_TARGET );

			if ( target != null ){

				String device_id = null;

				if ( target.endsWith( "/blank" )){

					device_id = target.substring( 0, target.length() - 6 );
				}

				DeviceMediaRenderer		target_dmr			= null;
				TranscodeProfile		target_profile 		= null;

				for ( DeviceImpl device: device_manager.getDevices()){

					if ( !( device instanceof DeviceMediaRenderer )){

						continue;
					}

					DeviceMediaRenderer dmr = (DeviceMediaRenderer)device;

					if ( device_id != null ){

						if ( device.getID().equals( device_id )){

							target_dmr		 	= dmr;
							target_profile		= device.getBlankProfile();

							break;
						}
					}else{

						TranscodeProfile[] profs = device.getTranscodeProfiles();

						for ( TranscodeProfile prof: profs ){

							if ( prof.getUID().equals( target )){

								target_dmr	= dmr;
								target_profile	= prof;

								break;
							}
						}
					}
				}

				if ( target_dmr != null ){

					active_map.put( cat, new Object[]{ target_dmr, target_profile });
				}
			}
		}

		Map<Category,Object[]> to_process = new HashMap<>();

		synchronized( category_map ){

			if ( category_listener == null ){

				category_listener =
					new CategoryListener()
					{
						@Override
						public void
						downloadManagerAdded(
							Category 			cat,
							DownloadManager 	manager )
						{
							Object[]	details;

							synchronized( category_map ){

								details = category_map.get( cat );
							}

							if ( details != null ){

								processCategory( cat, details, manager );
							}
						}

						@Override
						public void
						downloadManagerRemoved(
							Category 			cat,
							DownloadManager 	removed )
						{
						}
					};
			}

			Iterator<Category>	it = category_map.keySet().iterator();

			while( it.hasNext()){

				Category c = it.next();

				if ( !active_map.containsKey( c )){

					c.removeCategoryListener( category_listener );

					it.remove();
				}
			}

			for ( final Category c: active_map.keySet()){

				if ( !category_map.containsKey( c )){

					to_process.put( c, active_map.get(c));

					c.addCategoryListener( category_listener );

					category_map.put( c, active_map.get(c));

					if ( c.getType() == Category.TYPE_UNCATEGORIZED ){

						if ( category_dl_listener == null ){

								// new downloads don't get a category-change event fired when added
								// we also want to delay things a bit to allow other components
								// to set an initial category. there's no hurry anyways

							category_dl_listener =
								new GlobalManagerAdapter()
								{
									@Override
									public void
									downloadManagerAdded(
										final DownloadManager	dm )
									{
										new DelayedEvent(
											"TM:cat-check",
											10*1000,
											new AERunnable()
											{
												@Override
												public void
												runSupport()
												{
													Category dm_c = dm.getDownloadState().getCategory();

													if ( dm_c == null || dm_c == c ){

															// still uncategorised

														Object[]	details;

														synchronized( category_map ){

															details = category_map.get( c );
														}

														if ( details != null ){

															processCategory( c, details, dm );
														}
													}
												}
											});
									}
									@Override
									public void
									downloadManagerRemoved(
										DownloadManager	dm )
									{
									}
								};


							core.getGlobalManager().addListener( category_dl_listener, false );
						}
					}
				}
			}
		}

		if ( to_process.size() > 0 ){

			List<DownloadManager> downloads = core.getGlobalManager().getDownloadManagers();

			for ( Map.Entry<Category, Object[]> entry: to_process.entrySet()){

				Category 	c 		= entry.getKey();
				Object[]	details = entry.getValue();

				List<DownloadManager> list = c.getDownloadManagers( downloads );

				for( DownloadManager dm: list ){

					processCategory( c, details, dm );
				}
			}
		}
	}

	void
	processCategory(
		Category		cat,
		Object[]		details,
		DownloadManager	dm )
	{
		Download download = PluginCoreUtils.wrap( dm );

		if ( download == null ){

			return;
		}

		if ( download.getFlag( Download.FLAG_LOW_NOISE )){

			return;
		}

		String str = download.getAttribute( category_ta );

		String cat_name = cat.getName();

		if ( cat.getType() == Category.TYPE_UNCATEGORIZED ){

			cat_name = "<none>";
		}

		String	cat_tag = cat_name + ";";

		if ( str != null && str.contains( cat_tag )){

			return;
		}

		try{
			DeviceMediaRenderer		device 	= (DeviceMediaRenderer)details[0];
			TranscodeProfile		profile	= (TranscodeProfile)details[1];

			log( "Category " + cat_name + " - adding " + download.getName() + " to " + device.getName() + "/" + profile.getName());

			DiskManagerFileInfo[] dm_files = download.getDiskManagerFileInfo();

			int	num_added = 0;

			for ( DiskManagerFileInfo dm_file: dm_files ){

					// limit number of files we can add to avoid crazyness

				if ( num_added > 64 ){

					break;
				}

					// could be smarter here and check extension or whatever

				if ( dm_files.length == 1 || dm_file.getLength() >= 128*1024 ){

					try{
						queue.add( device, profile, dm_file, false  );

						num_added++;

					}catch( Throwable e ){

						log( "    add failed", e );
					}
				}
			}
		}finally{

			download.setAttribute( category_ta, str==null?cat_tag:(str+cat_tag));
		}
	}



	void
	checkTags()
	{
		TagManager tm = TagManagerFactory.getTagManager();

		Map<Tag,Object[]> active_map = new HashMap<>();

		for ( TagType tt: tm.getTagTypes()){

			if ( !tt.hasTagTypeFeature( TagFeature.TF_XCODE )){

				continue;
			}


			for ( Tag tag: tt.getTags()){

				TagFeatureTranscode tfx = (TagFeatureTranscode)tag;

				if ( !tfx.supportsTagTranscode()){

					continue;
				}

				String[] target_details = tfx.getTagTranscodeTarget();

				if ( target_details != null ){

					String	target = target_details[0];

					String device_id = null;

					if ( target.endsWith( "/blank" )){

						device_id = target.substring( 0, target.length() - 6 );
					}

					DeviceMediaRenderer		target_dmr			= null;
					TranscodeProfile		target_profile 		= null;

					for ( DeviceImpl device: device_manager.getDevices()){

						if ( !( device instanceof DeviceMediaRenderer )){

							continue;
						}

						DeviceMediaRenderer dmr = (DeviceMediaRenderer)device;

						if ( device_id != null ){

							if ( device.getID().equals( device_id )){

								target_dmr		 	= dmr;
								target_profile		= device.getBlankProfile();

								break;
							}
						}else{

							TranscodeProfile[] profs = device.getTranscodeProfiles();

							for ( TranscodeProfile prof: profs ){

								if ( prof.getUID().equals( target )){

									target_dmr	= dmr;
									target_profile	= prof;

									break;
								}
							}
						}
					}

					if ( target_dmr != null ){

						active_map.put( tag, new Object[]{ target_dmr, target_profile });
					}
				}
			}
		}

		Map<Tag,Object[]> to_process = new HashMap<>();

		synchronized( tag_map ){

			if ( tag_listener == null ){

				tag_listener =
					new TagListener()
					{
						@Override
						public void
						taggableAdded(
							Tag			tag,
							Taggable	tagged )
						{
							Object[]	details;

							synchronized( tag_map ){

								details = tag_map.get( tag );
							}

							if ( details != null ){

								processTag( tag, details, (DownloadManager)tagged );
							}
						}

						@Override
						public void
						taggableSync(
							Tag			tag )
						{

						}

						@Override
						public void
						taggableRemoved(
							Tag			tag,
							Taggable	tagged )
						{

						}
					};
			}

			Iterator<Tag>	it = tag_map.keySet().iterator();

			while( it.hasNext()){

				Tag t = it.next();

				if ( !active_map.containsKey( t )){

					t.removeTagListener( tag_listener );

					it.remove();
				}
			}

			for ( final Tag tag: active_map.keySet()){

				if ( !tag_map.containsKey( tag )){

					to_process.put( tag, active_map.get( tag ));

					tag.addTagListener( tag_listener, false );

					tag_map.put( tag, active_map.get( tag ));
				}
			}
		}

		if ( to_process.size() > 0 ){

			for ( Map.Entry<Tag, Object[]> entry: to_process.entrySet()){

				Tag		 	tag 	= entry.getKey();
				Object[]	details = entry.getValue();

				Set<DownloadManager> list = ((TagDownload)tag).getTaggedDownloads();

				for( DownloadManager dm: list ){

					processTag( tag, details, dm );
				}
			}
		}
	}



	void
	processTag(
		Tag				tag,
		Object[]		details,
		DownloadManager	dm )
	{
		Download download = PluginCoreUtils.wrap( dm );

		if ( download == null ){

			return;
		}

		if ( download.getFlag( Download.FLAG_LOW_NOISE )){

			return;
		}

		String str = download.getAttribute( tag_ta );

		String tag_name = tag.getTagName( true );

		String	tag_tag = tag.getTagType().getTagType() + "." + tag.getTagID() + ";";

		if ( str != null && str.contains( tag_tag )){

			return;
		}

		try{
			DeviceMediaRenderer		device 	= (DeviceMediaRenderer)details[0];
			TranscodeProfile		profile	= (TranscodeProfile)details[1];

			log( "Tag " + tag_name + " - adding " + download.getName() + " to " + device.getName() + "/" + profile.getName());

			DiskManagerFileInfo[] dm_files = download.getDiskManagerFileInfo();

			int	num_added = 0;

			for ( DiskManagerFileInfo dm_file: dm_files ){

					// limit number of files we can add to avoid crazyness

				if ( num_added > 64 ){

					break;
				}

					// could be smarter here and check extension or whatever

				if ( dm_files.length == 1 || dm_file.getLength() >= 128*1024 ){

					try{
						queue.add( device, profile, dm_file, false  );

						num_added++;

					}catch( Throwable e ){

						log( "    add failed", e );
					}
				}
			}
		}finally{

			download.setAttribute( tag_ta, str==null?tag_tag:(str+tag_tag));
		}
	}

	@Override
	public TranscodeProvider[]
	getProviders()
	{
		TranscodeProviderVuze	vp = vuzexcode_provider;

		if ( vp == null ){

			return( new TranscodeProvider[0] );
		}

		return( new TranscodeProvider[]{ vp });
	}

	protected TranscodeProvider
	getProvider(
		int		p_id )

		throws TranscodeException
	{
		TranscodeProviderVuze	vp = vuzexcode_provider;

		if ( p_id == TranscodeProvider.TP_VUZE && vp != null ){

			return( vp );
		}

		throw( new TranscodeException( "Transcode provider not registered" ));
	}

	protected TranscodeProfile
	getProfileFromUID(
		String		uid )
	{
		for ( TranscodeProvider provider: getProviders()){

			TranscodeProfile profile = provider.getProfile( uid );

			if ( profile != null ){

				return( profile );
			}
		}

		return( null );
	}

	@Override
	public TranscodeQueueImpl
	getQueue()
	{
		if ( !init_sem.reserve(30*1000)){

			Debug.out( "Timeout waiting for init" );

			AEDiagnostics.dumpThreads();
		}

		return( queue );
	}

	protected DeviceManagerImpl
	getManager()
	{
		return( device_manager );
	}

	protected TranscodeTarget
	lookupTarget(
		String		target_id )

		throws TranscodeException
	{
		Device device = device_manager.getDevice( target_id );

		if ( device instanceof TranscodeTarget ){

			return((TranscodeTarget)device);
		}

		throw( new TranscodeException( "Transcode target with id " + target_id + " not found" ));
	}

	protected DiskManagerFileInfo
	lookupFile(
		byte[]		hash,
		int			index )

		throws TranscodeException
	{
		try{
			Download download = PluginInitializer.getDefaultInterface().getDownloadManager().getDownload( hash );

			if ( download == null ){

				throw( new TranscodeException( "Download with hash " + ByteFormatter.encodeString( hash ) + " not found" ));
			}

			return( download.getDiskManagerFileInfo()[index]);

		}catch( Throwable e ){

			throw( new TranscodeException( "Download with hash " + ByteFormatter.encodeString( hash ) + " not found", e ));

		}
	}

	protected void
	close()
	{
		queue.close();
	}

	@Override
	public void
	addListener(
		TranscodeManagerListener		listener )
	{
		listeners.add( listener );
	}

	@Override
	public void
	removeListener(
		TranscodeManagerListener		listener )
	{
		listeners.remove( listener );
	}

	protected void
	log(
		String	str )
	{
		device_manager.log( "Trans: " + str );
	}

	protected void
	log(
		String		str,
		Throwable	e )
	{
		device_manager.log( "Trans: " + str, e );
	}

	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Transcode Manager: vuze provider=" + vuzexcode_provider );

		queue.generate( writer );
	}
}
