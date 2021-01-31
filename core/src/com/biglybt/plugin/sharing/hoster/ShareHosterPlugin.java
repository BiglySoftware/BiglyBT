/*
 * File    : ShareHosterPlugin.java
 * Created : 05-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.plugin.sharing.hoster;

/**
 * @author parg
 *
 */

import java.io.File;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagListener;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.TagTypeListener;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginEvent;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.download.*;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.sharing.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.tracker.TrackerTorrentRemovalVetoException;
import com.biglybt.pif.tracker.TrackerTorrentWillBeRemovedListener;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pifimpl.local.PluginCoreUtils;

public class
ShareHosterPlugin
	implements Plugin, PluginListener, ShareManagerListener
{
	private PluginInterface plugin_interface;
	private LoggerChannel		log;
	private Tracker			tracker;
	private ShareManager		share_manager;
	private DownloadManager	download_manager;
	
	private final TagManager tag_manager = TagManagerFactory.getTagManager();
	

	private Map<ShareResource,Download>				resource_dl_map = new HashMap<>();
	private Map<ShareResource,TrackerTorrent>		resource_tt_map = new HashMap<>();
	private Map<Taggable,ShareResource>				taggable_map 	= new IdentityHashMap<>();
	
	private Download			download_being_removed;
	private TrackerTorrent		torrent_being_removed;

	public static void
	load(
		PluginInterface		plugin_interface )
	{
		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		"Share Hoster" );
	}

	@Override
	public void
	initialize(
		PluginInterface _plugin_interface )
	{
		plugin_interface = _plugin_interface;

		log	= plugin_interface.getLogger().getChannel("ShareHosterPlugin");

		log.log( LoggerChannel.LT_INFORMATION, "ShareHosterPlugin: initialisation starts");

		plugin_interface.addListener( this );
	}

	@Override
	public void
	initializationComplete()
	{
		final DelayedTask dt = plugin_interface.getUtilities().createDelayedTask(new Runnable()
			{
				@Override
				public void
				run()
				{
					initialise();
				}
			});

		dt.queue();
	}

	protected void
	initialise()
	{
		log.log( LoggerChannel.LT_INFORMATION, "ShareHosterPlugin: initialisation complete");

		Thread.currentThread().setPriority( Thread.MIN_PRIORITY );

		try{
			tracker	=  plugin_interface.getTracker();

			download_manager = plugin_interface.getDownloadManager();

			share_manager = plugin_interface.getShareManager();

			share_manager.addListener( this );

			share_manager.initialise();

			tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ).addTagTypeListener(
				new TagTypeListener()
				{
					public void
					tagTypeChanged(
						TagType		tag_type )
					{
					}

					public void
					tagEventOccurred(
						TagEvent			event )
					{
						if ( event.getEventType() == TagTypeListener.TagEvent.ET_TAG_ADDED ){
							
							event.getTag().addTagListener(
								new TagListener(){
									
									@Override
									public void
									taggableSync(Tag tag)
									{
									}
									
									@Override
									public void 
									taggableAdded(
										Tag 		tag, 
										Taggable 	tagged )
									{
										update( tag, tagged, true );
									}
									
									@Override
									public void 
									taggableRemoved(
										Tag 		tag, 
										Taggable 	tagged)
									{
										update( tag, tagged, false );
									}	
									
									private void
									update(
										Tag			tag,
										Taggable	tagged,
										boolean		added )
									{
										ShareResource resource;
										
										boolean[] auto = tag.isTagAuto();
										
										if ( auto[0] ){
											
											return;
										}
										
										synchronized( taggable_map ){
											
											resource = taggable_map.get( tagged );
											
											if ( resource != null && resource.isDeleted()){
												
												taggable_map.remove( tagged );
												
												resource = null;
											}
										}
										
										if ( resource != null ){
											
											Map<String,String>	properties  = resource.getProperties();
											
											if ( properties == null ){
												
												properties = new HashMap<>();
											}
											
											String tags_str = properties.get( ShareManager.PR_TAGS );
											
											Set<Tag> existing = decodeTags( tags_str );
											
											List<Tag> tags = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTagsForTaggable( tagged );
											
											Set<Tag> current = new HashSet<>();
											
											for ( Tag t: tags ){
												
												if ( !t.isTagAuto()[0]){
													
													current.add( t );
												}
											}
											
											if ( !existing.equals( current )){
												
												String new_str = "";
												
												for ( Tag t: current ){
													
													new_str += (new_str.isEmpty()?"":",") + t.getTagUID();
												}
												
												properties = new HashMap<>( properties );
												
												properties.put( ShareManager.PR_TAGS, new_str );
												
												resource.setProperties( properties, true );
											}
										}
									}
								}, false );
						}
					}
				}, true );
			
			download_manager.addListener(
				new DownloadManagerListener(){
										
					@Override
					public void 
					downloadAdded(
						Download download)
					{
					}
					
					@Override
					public void 
					downloadRemoved(
						Download download )
					{
						com.biglybt.core.download.DownloadManager core_dm = PluginCoreUtils.unwrap( download );
						
						ShareResource resource;
						
						synchronized( taggable_map ){
							
							resource = taggable_map.remove( core_dm );
						}
						
						if ( resource != null ){
							
								// non-persistent shares will have this handled by the consistency mechanism (well, might 
								// make sense to do this for them too but I'm currently working on persistent stuff so woreva)
							
							if ( resource.isPersistent()){
								
								try{
									resource.delete( true );
									
								}catch( Throwable e ){
									
									Debug.out( e );
								}
							}
						}
					}

				}, false );
			
		}catch( ShareException e ){

			Debug.printStackTrace( e );

			log.log( e );

		}finally{

			plugin_interface.getPluginManager().firePluginEvent( PluginEvent.PEV_INITIAL_SHARING_COMPLETE );
		}
	}

	@Override
	public void
	closedownInitiated()
	{
	}

	@Override
	public void
	closedownComplete()
	{
	}

	@Override
	public void
	resourceAdded(
		final ShareResource		resource )
	{
		log.log( LoggerChannel.LT_INFORMATION, "Resource added:".concat(resource.getName()));

		try{

			resource.addDeletionListener(
				new ShareResourceWillBeDeletedListener()
				{
					@Override
					public void
					resourceWillBeDeleted(
						ShareResource	resource )

						throws ShareResourceDeletionVetoException
					{
						canResourceBeDeleted( resource );
					}
				});

			Download	old_download	= null;
			Download	new_download 	= null;

			int	type = resource.getType();

			if ( type == ShareResource.ST_FILE ){

				ShareResourceFile	file_resource = (ShareResourceFile)resource;

				ShareItem	item = file_resource.getItem();

				Torrent torrent = item.getTorrent();

				old_download = download_manager.getDownload( torrent );

				if ( old_download == null ){

					new_download = addDownload( resource, torrent, item.getTorrentFile(), file_resource.getFile());
				}
			}else if ( type == ShareResource.ST_DIR ){

				ShareResourceDir	dir_resource = (ShareResourceDir)resource;

				ShareItem	item = dir_resource.getItem();

				Torrent torrent = item.getTorrent();

				old_download = download_manager.getDownload( torrent );

				if ( old_download == null ){

					new_download = addDownload( resource, torrent, item.getTorrentFile(), dir_resource.getDir());
				}
			}

			final Download	f_old_download = old_download;
			final Download	f_new_download = new_download;
			
			ShareResourceListener resource_listneer = 
				new ShareResourceListener()
				{
					@Override
					public void
					shareResourceChanged(
						ShareResource			resource,
						ShareResourceEvent		event )
					{
						Download download = f_old_download==null?f_new_download:f_old_download;
						
						int	type = event.getType();
						
						if ( type == ShareResourceEvent.ET_ATTRIBUTE_CHANGED ){
	
							TorrentAttribute	attribute = (TorrentAttribute)event.getData();
	
								// System.out.println( "sh: res -> ds: " + attribute.getName() + "/" + resource.getAttribute( attribute ));
									
							download.setAttribute( attribute, resource.getAttribute( attribute ));
							
						}else if ( type == ShareResourceEvent.ET_PROPERTY_CHANGED ){
							
							String[] entry = (String[])event.getData();
							
							String key = entry[0];
							
							if ( key.equals( ShareManager.PR_TAGS )){
							
								if ( !event.isInternal()){
									
									String new_tags_str = entry[2];
									
									Set<Tag> new_tags = decodeTags( new_tags_str );
									
									com.biglybt.core.download.DownloadManager core_dm = PluginCoreUtils.unwrap( download );
									
									Set<Tag> existing = new HashSet<>( tag_manager.getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, core_dm ));
									
									for ( Tag tag: new_tags ){
																								
										existing.remove( tag );
												
										if ( !tag.hasTaggable( core_dm )){
													
											tag.addTaggable( core_dm );
										}
									}
									
									for ( Tag tag: existing ){
										
										boolean[] auto = tag.isTagAuto();
										
										if ( !auto[0] ){
											
											tag.removeTaggable( core_dm );
										}
									}
								}
							}
						}
					}
				};
			
			DownloadAttributeListener	attribute_listener = 
				new DownloadAttributeListener() {
					@Override
					public void attributeEventOccurred(Download d, TorrentAttribute attr, int event_type){
						resource.setAttribute(attr, d.getAttribute(attr));
					}
				};
								
			if ( old_download != null ){
				
				resource.addChangeListener( resource_listneer );

				old_download.addAttributeListener(
						attribute_listener,
						plugin_interface.getTorrentManager().getAttribute(TorrentAttribute.TA_CATEGORY),
						DownloadAttributeListener.WRITTEN );

				synchronized( taggable_map ){
					
					taggable_map.put( PluginCoreUtils.unwrap( old_download ), resource );
				}
			}else if ( new_download != null ){

				resource_dl_map.put( resource, new_download );

				resource.addChangeListener( resource_listneer );

				TorrentAttribute[]	attributes = resource.getAttributes();

				for (int i=0;i<attributes.length;i++){

					TorrentAttribute	ta = attributes[i];

					new_download.setAttribute( ta,	resource.getAttribute( ta ));
				}

				new_download.addAttributeListener(
					attribute_listener,
					plugin_interface.getTorrentManager().getAttribute(TorrentAttribute.TA_CATEGORY),
					DownloadAttributeListener.WRITTEN );
				
				synchronized( taggable_map ){
					
					taggable_map.put( PluginCoreUtils.unwrap( new_download ), resource );
				}

				boolean persistent = resource.isPersistent();

				Torrent	dl_torrent = new_download.getTorrent();

				if ( dl_torrent != null ){

					TrackerTorrent	tt = tracker.host(dl_torrent, persistent );

					if ( !persistent ){

						tt.addRemovalListener(
								new TrackerTorrentWillBeRemovedListener()
								{
									@Override
									public void
									torrentWillBeRemoved(
										TrackerTorrent	tt )

										throws TrackerTorrentRemovalVetoException
									{
										if ( tt != torrent_being_removed ){

											throw( new TrackerTorrentRemovalVetoException(
													MessageText.getString("plugin.sharing.torrent.remove.veto")));
										}
									}
								});
					}

					resource_tt_map.put( resource, tt );
				}

				if ( !persistent ){

					new_download.addDownloadWillBeRemovedListener(
							new DownloadWillBeRemovedListener()
							{
								@Override
								public void
								downloadWillBeRemoved(
									Download	dl )

									throws DownloadRemovalVetoException
								{
									if ( dl != download_being_removed ){

										throw( new DownloadRemovalVetoException(
													MessageText.getString("plugin.sharing.download.remove.veto")));
									}
								}
							});
				}
			}

		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	private Download
	addDownload(
		ShareResource		resource,
		final Torrent		torrent,
		File				torrent_file,
		File				data_file )

		throws DownloadException
	{
		Map<String,String>	properties  = resource.getProperties();

		final List<String>	networks 	= new ArrayList<>();
		final Set<Tag>		tags;

		boolean force_networks = false;
		
		if ( !COConfigurationManager.getBooleanParameter( "Sharing Network Selection Global" )){

			force_networks = true;
			
			for ( String net: AENetworkClassifier.AT_NETWORKS ){
				
				String config_name = "Sharing Network Selection Default." + net;
				
				if ( COConfigurationManager.getBooleanParameter( config_name )){
					
					networks.add( net );
				}
			}
		}
		
		if ( properties != null ){

			String nets = properties.get( ShareManager.PR_NETWORKS );

			if ( nets != null ){

				force_networks = true;
				
				networks.clear();
				
				String[] bits = nets.split( "," );

				for ( String bit: bits ){

					bit = AENetworkClassifier.internalise( bit.trim());

					if ( bit != null ){

						networks.add( bit );
					}
				}
			}

			String tags_str = properties.get( ShareManager.PR_TAGS );

			tags = decodeTags( tags_str );
			
		}else{
			
			tags = null;
		}

		DownloadWillBeAddedListener dwbal = null;

		if ( networks.size() > 0 || force_networks ){

			dwbal =
				new DownloadWillBeAddedListener()
				{
					@Override
					public void
					initialised(
						Download download )
					{
						if ( Arrays.equals( download.getTorrentHash(), torrent.getHash())){

							PluginCoreUtils.unwrap( download ).getDownloadState().setNetworks( networks.toArray( new String[networks.size()]));
						}
					}
				};

			download_manager.addDownloadWillBeAddedListener( dwbal );
		}

		try{
			Download download;

			if ( resource.isPersistent()){

				DownloadStub stub = download_manager.lookupDownloadStub( torrent.getHash());

				if ( stub != null ){

						// already exists in archive

					return( null );
				}

				try{
					torrent.setComplete( data_file );

				}catch( Throwable e ){

					Debug.out( e );
				}

				download = download_manager.addDownload( torrent, torrent_file, data_file );

			}else{

				download = download_manager.addNonPersistentDownload( torrent, torrent_file, data_file );
			}

			if ( tags.size() > 0 ){

				com.biglybt.core.download.DownloadManager dm = PluginCoreUtils.unwrap( download );

				for ( Tag tag: tags ){

					tag.addTaggable( dm );
				}
			}

			return( download );

		}finally{

			if ( dwbal != null ){

				download_manager.removeDownloadWillBeAddedListener( dwbal );
			}
		}
	}

	private Set<Tag>
	decodeTags(
		String	tags_str )
	{				
		Set<Tag> tags = new HashSet<>();
		
		if ( tags_str != null ){
			
			String[] bits = tags_str.split( "," );
		
			for ( String bit: bits ){
				
				bit = bit.trim();
				
				if ( !bit.isEmpty()){
					
					try{
						Tag tag = tag_manager.lookupTagByUID( Long.parseLong( bit ));
						
						if ( tag != null ){
							
							tags.add( tag );
						}
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			}
		}				
		
		return( tags );
	}
	
	protected void
	canResourceBeDeleted(
		ShareResource	resource )

		throws ShareResourceDeletionVetoException
	{
		Download	dl = (Download)resource_dl_map.get(resource);

		if ( dl != null ){

			try{
				download_being_removed	= dl;

				dl.canBeRemoved();

			}catch( DownloadRemovalVetoException e ){

				throw( new ShareResourceDeletionVetoException( e.getMessage()));

			}finally{

				download_being_removed	= null;
			}
		}

		TrackerTorrent	tt = (TrackerTorrent)resource_tt_map.get(resource);

		if ( tt != null ){

			try{
				torrent_being_removed	= tt;

				tt.canBeRemoved();

			}catch( TrackerTorrentRemovalVetoException e ){

				throw( new ShareResourceDeletionVetoException( e.getMessage()));

			}finally{

				torrent_being_removed	= null;
			}
		}
	}

	@Override
	public void
	resourceModified(
		ShareResource		old_resource,
		ShareResource		new_resource )
	{
		log.log( LoggerChannel.LT_INFORMATION, "Resource modified:".concat(old_resource.getName()));

		resourceDeleted( old_resource );

		resourceAdded( new_resource );
	}

	@Override
	public void
	resourceDeleted(
		ShareResource		resource )
	{
		log.log( LoggerChannel.LT_INFORMATION, "Resource deleted:".concat(resource.getName()));

		Download	dl = (Download)resource_dl_map.get(resource);

		if ( dl != null ){

			try{
				download_being_removed	= dl;

				dl.stopAndRemove(false, false);

			}catch( Throwable e ){

				Debug.printStackTrace( e );

			}finally{

				download_being_removed	= null;
			}

			resource_dl_map.remove( resource );
		}

		TrackerTorrent	tt = (TrackerTorrent)resource_tt_map.get(resource);

		if ( tt != null ){

			try{
				torrent_being_removed	= tt;

				tt.remove();

			}catch( Throwable e ){

				Debug.printStackTrace( e );

			}finally{

				torrent_being_removed	= null;
			}

			resource_tt_map.remove( resource );
		}
	}

	@Override
	public void
	reportProgress(
		int		percent_complete )
	{
	}

	@Override
	public void
	reportCurrentTask(
		String	task_description )
	{
		log.log( LoggerChannel.LT_INFORMATION, "Current Task:".concat(task_description) );
	}
}
