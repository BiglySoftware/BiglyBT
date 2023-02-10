/*
 * Created on Mar 22, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.tag.impl;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.tag.*;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatMessage;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;

public class
TagDownloadWithState
	extends TagWithState
	implements TagDownload, TaggableResolver.LifecycleControlListener
{
	private static Object	FP_DL_KEY = new Object();
	
	private int upload_rate_limit;
	private int download_rate_limit;

	private int	upload_rate		= -1;
	private int	download_rate	= -1;

	private int	aggregate_sr;

	private long session_up;
	private long session_down;

	private long last_rate_update;

	final Object	UPLOAD_PRIORITY_ADDED_KEY = new Object();
	private int		upload_priority;
	private int		min_share_ratio;
	private int		max_share_ratio;
	private int		max_share_ratio_action;
	private int		max_aggregate_share_ratio;
	private int		max_aggregate_share_ratio_action;
	private boolean	max_aggregate_share_ratio_priority;
	private boolean	fp_seeding;
	private boolean fp_seeding_ever;
	
	private int		auto_sort_period;
	private long	last_auto_sort	= -1;
	
	private boolean	supports_xcode;
	private boolean	supports_file_location;

	private boolean	prevent_delete;
	
	private String	notification_pub = "";
		
	final Object	rate_lock = new Object();

	private final LimitedRateGroup upload_limiter =
		new LimitedRateGroup()
		{
			@Override
			public String
			getName()
			{
				String str = "tag_up: " + getTagName( true );

				if ( upload_rate_limit < 0 ){

					str += ": disabled";
				}

				return( str );
			}

			@Override
			public int
			getRateLimitBytesPerSecond()
			{
				int	res = upload_rate_limit;

				if ( res < 0 ){

					res = 0;	// disabled upload handled by per-peer limits
				}

				return( res );
			}
			@Override
			public boolean
			isDisabled()
			{
				return( upload_rate_limit < 0 );
			}
			@Override
			public void
			updateBytesUsed(
				int	used )
			{
				session_up += used;
			}
		};

	private final LimitedRateGroup download_limiter =
		new LimitedRateGroup()
		{
			@Override
			public String
			getName()
			{
				String str = "tag_down: " + getTagName( true );

				if ( download_rate_limit < 0 ){

					str += ": disabled";
				}

				return( str );
			}

			@Override
			public int
			getRateLimitBytesPerSecond()
			{
				int	res = download_rate_limit;

				if ( res < 0 ){

					res = 0;	// disabled upload handled by per-peer limits
				}

				return( res );
			}

			@Override
			public boolean
			isDisabled()
			{
				return( download_rate_limit < 0 );
			}

			@Override
			public void
			updateBytesUsed(
					int	used )
			{
				session_down += used;
			}
		};

	private boolean	do_rates;
	private boolean	do_up;
	private boolean	do_down;
	private boolean	do_bytes;

	private int		run_states;

	private static final AsyncDispatcher rs_async = new AsyncDispatcher(2000);

	private final TagProperty[] tag_properties =
		new TagProperty[]{
			createTagProperty( TagFeatureProperties.PR_TRACKERS, TagFeatureProperties.PT_STRING_LIST ),
			createTagProperty( TagFeatureProperties.PR_UNTAGGED, TagFeatureProperties.PT_BOOLEAN ),
			createTagProperty( TagFeatureProperties.PR_TRACKER_TEMPLATES, TagFeatureProperties.PT_STRING_LIST ),
			createTagProperty( TagFeatureProperties.PR_CONSTRAINT, TagFeatureProperties.PT_STRING_LIST )
		};

	public
	TagDownloadWithState(
		TagTypeBase		tt,
		int				tag_id,
		String			name,
		boolean			do_rates,
		boolean			do_up,
		boolean			do_down,
		boolean			do_bytes,
		int				run_states )
	{
		super( tt, tag_id, name );

		init( do_rates, do_up, do_down, do_bytes, run_states );
	}

	protected
	TagDownloadWithState(
		TagTypeBase		tt,
		int				tag_id,
		Map				details,
		boolean			do_rates,
		boolean			do_up,
		boolean			do_down,
		boolean			do_bytes,
		int				run_states )
	{
		super( tt, tag_id, details );

		init( do_rates, do_up, do_down, do_bytes, run_states );
	}

	private void
	init(
		boolean		_do_rates,
		boolean		_do_up,
		boolean		_do_down,
		boolean		_do_bytes,
		int			_run_states )
	{
		do_rates	= _do_rates;
		do_up		= _do_up;
		do_down		= _do_down;
		do_bytes	= _do_bytes;

		run_states	= _run_states;

		if ( do_up ){

			setRateLimit(readLongAttribute( AT_RATELIMIT_UP, 0L ).intValue(), true );
		}

		if ( do_down ){

			setRateLimit(readLongAttribute( AT_RATELIMIT_DOWN, 0L ).intValue(), false );
		}

		upload_priority		= readLongAttribute( AT_RATELIMIT_UP_PRI, 0L ).intValue();

		min_share_ratio				= readLongAttribute( AT_RATELIMIT_MIN_SR, 0L ).intValue();
		max_share_ratio				= readLongAttribute( AT_RATELIMIT_MAX_SR, 0L ).intValue();
		max_share_ratio_action		= readLongAttribute( AT_RATELIMIT_MAX_SR_ACTION, (long)TagFeatureRateLimit.SR_INDIVIDUAL_ACTION_DEFAULT ).intValue();

		max_aggregate_share_ratio			= readLongAttribute( AT_RATELIMIT_MAX_AGGREGATE_SR, 0L ).intValue();
		max_aggregate_share_ratio_action	= readLongAttribute( AT_RATELIMIT_MAX_AGGREGATE_SR_ACTION, (long)TagFeatureRateLimit.SR_AGGREGATE_ACTION_DEFAULT ).intValue();
		max_aggregate_share_ratio_priority	= readBooleanAttribute( AT_RATELIMIT_MAX_AGGREGATE_SR_PRIORITY, TagFeatureRateLimit.AT_RATELIMIT_MAX_AGGREGATE_SR_PRIORITY_DEFAULT );
		fp_seeding							= readBooleanAttribute( AT_RATELIMIT_FP_SEEDING, false );

		auto_sort_period					= getAutoApplySortInterval();
		
		notification_pub					= getNotifyMessageChannel();
		
		prevent_delete = getPreventDelete();
		
		if ( prevent_delete ){
			
			TaggableResolver resolver = getTagType().getResolver();
			
			if ( resolver != null ){
			
				resolver.addLifecycleControlListener( this );
			}
		}
		
		addTagListener(
			new TagListener()
			{
				@Override
				public void
				taggableAdded(
					Tag			tag,
					Taggable	tagged )
				{
					DownloadManager manager = (DownloadManager)tagged;

					setRateLimit( manager, true );

					if ( upload_priority > 0 ){

						manager.updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, true );
					}

					if ( min_share_ratio > 0 ){

						updateMinShareRatio( manager, min_share_ratio );
					}

					if ( max_share_ratio > 0 ){

						updateMaxShareRatio( manager, max_share_ratio );
					}
					
					if ( fp_seeding ){
						
						updateFPSeeding( manager, true );
					}
										
					ncp_pub_list_mutate_index.incrementAndGet();
				}

				@Override
				public void
				taggableSync(
					Tag 		tag )
				{
				}

				@Override
				public void
				taggableRemoved(
					Tag			tag,
					Taggable	tagged )
				{
					DownloadManager manager = (DownloadManager)tagged;

					setRateLimit( manager, false );

					if ( upload_priority > 0 ){

						manager.updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, false );
					}

					if ( min_share_ratio > 0 ){

						updateMinShareRatio( manager, 0 );
					}

					if ( max_share_ratio > 0 ){

						updateMaxShareRatio( manager, 0 );
					}
					
					if ( fp_seeding ){
						
						updateFPSeeding( manager, false );
					}
					
					ncp_pub_list_mutate_index.incrementAndGet();
				}

				private void
				updateMinShareRatio(
					DownloadManager	manager,
					int				sr )
				{
					List<Tag> dm_tags = getTagType().getTagsForTaggable( manager );

					for ( Tag t: dm_tags ){

						if ( t == TagDownloadWithState.this ){

							continue;
						}

						if ( t instanceof TagFeatureRateLimit ){

							int o_sr = ((TagFeatureRateLimit)t).getTagMinShareRatio();

							if ( o_sr > sr ){

								sr = o_sr;
							}
						}
					}

					manager.getDownloadState().setIntParameter( DownloadManagerState.PARAM_MIN_SHARE_RATIO, sr );
				}

				private void
				updateMaxShareRatio(
					DownloadManager	manager,
					int				sr )
				{
					List<Tag> dm_tags = getTagType().getTagsForTaggable( manager );

					for ( Tag t: dm_tags ){

						if ( t == TagDownloadWithState.this ){

							continue;
						}

						if ( t instanceof TagFeatureRateLimit ){

							int o_sr = ((TagFeatureRateLimit)t).getTagMaxShareRatio();

							if ( o_sr > sr ){

								sr = o_sr;
							}
						}
					}

					manager.getDownloadState().setIntParameter( DownloadManagerState.PARAM_MAX_SHARE_RATIO, sr );
				}
			},
			true );
	}

	@Override
	public void
	removeTag()
	{
		for ( DownloadManager dm: getTaggedDownloads()){

			setRateLimit( dm, false );

			if ( upload_priority > 0 ){

				dm.updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, false );
			}
			
			if ( fp_seeding ){
				
				updateFPSeeding( dm, false );
			}
		}

		super.removeTag();
	}

	private static final AsyncDispatcher move_dispatcher = new AsyncDispatcher( "tag:eos_move" );

	private static void
	moveDownload(
		DownloadManager			dm,
		TagFeatureFileLocation	fl )
	{
		move_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					File save_loc = fl.getTagInitialSaveFolder();
					
					long	options = fl.getTagInitialSaveOptions();

					boolean set_data 	= (options&TagFeatureFileLocation.FL_DATA) != 0;
					boolean set_torrent = (options&TagFeatureFileLocation.FL_TORRENT) != 0;

					if ( set_data ){

						File existing_save_loc = dm.getSaveLocation();

						if ( dm.getTorrent().isSimpleTorrent()){
							
							existing_save_loc = existing_save_loc.getParentFile();
						}
						
						if ( ! ( existing_save_loc.equals( save_loc ))){

							try{
								dm.moveDataFilesLive( save_loc );

							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					}

					if ( set_torrent ){

						File old_torrent_file = FileUtil.newFile( dm.getTorrentFileName());

						if ( old_torrent_file.exists()){

							try{
								dm.setTorrentFile( save_loc, old_torrent_file.getName());

							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					}
				}
			});
	}
	
	private List<DownloadManager>	batch 		= new ArrayList<DownloadManager>();
	private int						batch_depth	= 0;
	
	@Override
	public void
	addTaggableBatch(
		boolean		starts )
	{
		List<DownloadManager> to_do = new ArrayList<>();
		
		synchronized( batch ){
	
			if ( starts ){
				
				batch_depth++;
				
			}else{
				
				batch_depth--;
				
				if ( batch_depth == 0 ){
					
					to_do.addAll( batch );
					
					batch.clear();
				}
			}
		}
		
		if ( !to_do.isEmpty()){
			
			String script = getActionScript();

			if ( script.length() > 0 ){
				
				rs_async.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								TagManagerImpl.getSingleton().evalScript(
									TagDownloadWithState.this,
									script,
									to_do,
									"execAssign" );
							}
						});
			}
		}
	}
	
	@Override
	public void
	addTaggable(
		Taggable	t )
	{
		if ( t instanceof DownloadManager ){

			final DownloadManager dm = (DownloadManager)t;

			if ( dm.isDestroyed()){

				// There's a race condition when stopping and removing a torrent that isn't easy to avoid in terms
				// of a download being added to the 'stopped' tag and concurrently removed.
				// There will be a subseqent 'downloadRemoved' action triggered that should tidy up any
				// inconsistency left due to this

				//Debug.out( "Invalid Taggable added - download is destroyed: " + dm.getDisplayName());

			}else{

				super.addTaggable( t );

				int actions = getSupportedActions();

				if ( actions != TagFeatureExecOnAssign.ACTION_NONE ){

					if ( 	isActionEnabled( TagFeatureExecOnAssign.ACTION_START ) ||
							isActionEnabled( TagFeatureExecOnAssign.ACTION_RESUME )){

						int	dm_state = dm.getState();

						if ( 	dm_state == DownloadManager.STATE_STOPPED ||
								dm_state == DownloadManager.STATE_ERROR ){

							rs_async.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_START )){

											dm.setStateQueued();

										}else{

											dm.resume();
										}
									}
								});
						}

					}else if ( 	isActionEnabled( TagFeatureExecOnAssign.ACTION_STOP ) ||
								isActionEnabled( TagFeatureExecOnAssign.ACTION_PAUSE ) ||
								isActionEnabled( TagFeatureExecOnAssign.ACTION_QUEUE )){

						int	dm_state = dm.getState();

						if ( 	dm_state != DownloadManager.STATE_STOPPED &&
								dm_state != DownloadManager.STATE_STOPPING &&
								dm_state != DownloadManager.STATE_ERROR ){

							rs_async.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_STOP )){

											dm.stopIt( DownloadManager.STATE_STOPPED, false, false );

										}else if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_QUEUE )){

											dm.stopIt( DownloadManager.STATE_QUEUED, false, false );

										}else{

											dm.pause( true );
										}

										// recheck here in case it is an 'archive' action that requires
										// download to be stopped

										checkMaximumTaggables();

									}
								});
						}
					}

					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_FORCE_START )){

						rs_async.dispatch(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									dm.setForceStart( true);
								}
							});

					}else if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_NOT_FORCE_START )){

						rs_async.dispatch(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									dm.setForceStart( false );
								}
							});
					}

					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_SCRIPT )){

						final String script = getActionScript();

						if ( script.length() > 0 ){

							synchronized( batch ){
								
								if ( batch_depth > 0 ){
							
									batch.add( dm );
									
								}else{
							
									rs_async.dispatch(
										new AERunnable()
										{
											@Override
											public void
											runSupport()
											{
												TagManagerImpl.getSingleton().evalScript(
													TagDownloadWithState.this,
													script,
													Arrays.asList( dm ),
													"execAssign" );
											}
										});
								}
							}
						}
					}

					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_POST_MAGNET_URI )){

						String chat = getPostMessageChannel();

						if ( chat.length() > 0 ){

							rs_async.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										String[] bits = chat.split( ":", 2 );
										
										String net = bits[0].startsWith( "Public")?AENetworkClassifier.AT_PUBLIC:AENetworkClassifier.AT_I2P;
										
										String key = bits[1].trim();
										
										SimpleTimer.addEvent(
											"EOS:PM",
											SystemTime.getOffsetTime( 250 ),
											new TimerEventPerformer(){
											
												final private long start = SystemTime.getMonotonousTime();
												
												@Override
												public void perform(TimerEvent event){
														
													ChatInstance chat = BuddyPluginUtils.getChat(net, key);
													
													if ( chat != null ){
														
														try{
															if ( chat.isAvailable()){
															
																chat.sendMessage(  PluginCoreUtils.wrap( dm ));
																
															}else{
														
																if ( SystemTime.getMonotonousTime() - start >= 10*60*1000 ){
																	
																	Debug.out( "EOS:PM Abandoned sending of magnet to " + chat );
																	
																}else{
																
																	SimpleTimer.addEvent( "EOS:PM", SystemTime.getOffsetTime( 5000 ), this );
																}
															}
														}finally{
															
															if ( chat.getReferenceCount() > 1 ){
															
																chat.destroy();
															}
														}
													}
												}
											});
										
									}
								});
						}
					}
				
					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_APPLY_OPTIONS_TEMPLATE )){
	
						OptionsTemplateHandler handler = getOptionsTemplateHandler();
	
						if ( handler.isActive()){
							rs_async.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										handler.applyTo( dm );
									}
								});
						}
					}
					
					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_MOVE_INIT_SAVE_LOC )){
					
						if ( getTagType().hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )){
							
							TagFeatureFileLocation fl = (TagFeatureFileLocation)this;
							
							if ( fl.supportsTagInitialSaveFolder()){
								
								File f = fl.getTagInitialSaveFolder();
								
								if ( f != null ){
									
									moveDownload( dm, fl );
								}
							}
						}
					}
					
					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS )){
						
						List<Tag> tags = getTagAssigns();
						
						if ( !tags.isEmpty()){

							rs_async.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										for ( Tag t: tags ){
										
											if ( !t.hasTaggable( dm )){
											
												t.addTaggable( dm );
											}
										}
									}
								});
						}
					}
					
					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_REMOVE_TAGS )){
						
						List<Tag> tags = getTagRemoves();
						
						if ( !tags.isEmpty()){

							rs_async.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										for ( Tag t: tags ){
										
											if ( t.hasTaggable( dm )){
											
												t.removeTaggable( dm );
											}
										}
									}
								});
						}
					}

					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_HOST )){

						rs_async.dispatch(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									try{
										CoreFactory.getSingleton().getTrackerHost().hostTorrent( dm.getTorrent(), true, false);
										
									}catch( Throwable e ){
										
										Debug.out( e );
									}
								}
							});
					}
					
					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_PUBLISH )){

						rs_async.dispatch(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									try{
										CoreFactory.getSingleton().getTrackerHost().publishTorrent( dm.getTorrent());
										
									}catch( Throwable e ){
										
										Debug.out( e );
									}
								}
							});
					}
				}
			}
		}else{

			Debug.out( "Invalid Taggable added: " + t );
		}
	}

	@Override
	public int
	getTaggableTypes()
	{
		return( Taggable.TT_DOWNLOAD );
	}

	@Override
	public Set<DownloadManager>
	getTaggedDownloads()
	{
		return((Set<DownloadManager>)(Object)getTagged());
	}

	private final DownloadManagerPeerListener
		peer_listener =
			new DownloadManagerPeerListener()
			{
				@Override
				public void
				peerManagerWillBeAdded(
					PEPeerManager	manager )
				{
				}

				@Override
				public void
				peerManagerAdded(
					PEPeerManager	manager )
				{
				}

				@Override
				public void
				peerManagerRemoved(
					PEPeerManager	manager )
				{
				}

				@Override
				public void
				peerAdded(
					PEPeer 			peer )
				{
					synchronized( rate_lock ){

						if ( upload_rate_limit < 0 ){

							peer.setUploadDisabled( this, true );
						}

						if ( download_rate_limit < 0 ){

							peer.setDownloadDisabled( this, true );
						}
					}
				}

				@Override
				public void
				peerRemoved(
					PEPeer			peer )
				{
				}
			};

	private void
	setRateLimit(
		DownloadManager	manager,
		boolean			added )
	{
		synchronized( rate_lock ){

			if ( added ){

				if ( manager.getUserData( rate_lock ) == null ){

					manager.setUserData( rate_lock, "" );

					manager.addPeerListener( peer_listener, true );

					manager.addRateLimiter( upload_limiter, true );

					manager.addRateLimiter( download_limiter, false );
				}
			}else{

				if ( manager.getUserData( rate_lock ) != null ){

					manager.setUserData( rate_lock, null );

					manager.removeRateLimiter( upload_limiter, true );

					manager.removeRateLimiter( download_limiter, false );

					manager.removePeerListener( peer_listener );

					PEPeerManager pm = manager.getPeerManager();

					if ( pm != null ){

						List<PEPeer> peers = pm.getPeers();

						if ( upload_rate_limit < 0 || download_rate_limit < 0 ){

							for ( PEPeer peer: peers ){

								if ( upload_rate_limit < 0 ){

									peer.setUploadDisabled( peer_listener, false );
								}

								if ( download_rate_limit < 0 ){

									peer.setDownloadDisabled( peer_listener, false );
								}
							}
						}
					}
				}
			}
		}
	}

	private void
	setRateLimit(
		int			limit,
		boolean		is_up )
	{
		if ( limit < 0 ){

			limit = -1;
		}

		synchronized( rate_lock ){

			if ( is_up ){

				if ( limit == upload_rate_limit ){

					return;
				}

				if ( limit < 0 || upload_rate_limit < 0 ){

					Set<DownloadManager> downloads = getTaggedDownloads();

					for ( DownloadManager dm: downloads ){

						PEPeerManager pm = dm.getPeerManager();

						if ( pm != null ){

							List<PEPeer> peers = pm.getPeers();

							for ( PEPeer peer: peers ){

								peer.setUploadDisabled( peer_listener, limit < 0 );
							}
						}
					}
				}

				upload_rate_limit = limit;

			}else{

				if ( limit == download_rate_limit ){

					return;
				}

				if ( limit < 0 || download_rate_limit < 0 ){

					Set<DownloadManager> downloads = getTaggedDownloads();

					for ( DownloadManager dm: downloads ){

						PEPeerManager pm = dm.getPeerManager();

						if ( pm != null ){

							List<PEPeer> peers = pm.getPeers();

							for ( PEPeer peer: peers ){

								peer.setDownloadDisabled( peer_listener, limit < 0 );
							}
						}
					}
				}

				download_rate_limit = limit;
			}
		}
	}

	@Override
	public boolean
	supportsTagRates()
	{
		return( do_rates );
	}

	@Override
	public boolean
	supportsTagUploadLimit()
	{
		return( do_up );
	}

	@Override
	public boolean
	supportsTagDownloadLimit()
	{
		return( do_down );
	}

	@Override
	public int
	getTagUploadLimit()
	{
		return( upload_rate_limit );
	}

	@Override
	public void
	setTagUploadLimit(
		int		bps )
	{
		if ( upload_rate_limit == bps ){

			return;
		}

		if ( !do_up ){

			Debug.out( "Not supported" );

			return;
		}

		setRateLimit( bps, true );

		writeLongAttribute( AT_RATELIMIT_UP, upload_rate_limit );

		getTagType().fireMetadataChanged( this );
	}

	@Override
	public int
	getTagCurrentUploadRate()
	{
		updateStuff();

		return( upload_rate );
	}

	@Override
	public int
	getTagDownloadLimit()
	{
		return( download_rate_limit );
	}

	@Override
	public void
	setTagDownloadLimit(
		int		bps )
	{
		if ( download_rate_limit == bps ){

			return;
		}

		if ( !do_down ){

			Debug.out( "Not supported" );

			return;
		}

		setRateLimit( bps, false );

		writeLongAttribute( AT_RATELIMIT_DOWN, download_rate_limit );

		getTagType().fireMetadataChanged( this );
	}

	@Override
	public int
	getTagCurrentDownloadRate()
	{
		updateStuff();

		return( download_rate );
	}

	@Override
	public int
	getTagUploadPriority()
	{
		return( upload_priority );
	}

	@Override
	protected long[]
	getTagSessionUploadTotalCurrent()
	{
		if ( do_bytes && do_up ){

			return( new long[]{ session_up });

		}else{

			return( null );
		}
	}

	@Override
	protected long[]
	getTagSessionDownloadTotalCurrent()
	{
		if ( do_bytes && do_down ){

			return( new long[]{ session_down });

		}else{

			return( null );
		}
	}

	@Override
	public void
	setTagUploadPriority(
		int		priority )
	{
		if ( priority < 0 ){

			priority = 0;
		}

		if ( priority == upload_priority ){

			return;
		}

		int	old_up = upload_priority;

		upload_priority	= priority;

		writeLongAttribute( AT_RATELIMIT_UP_PRI, priority );

		if ( old_up == 0 || priority == 0 ){

			Set<DownloadManager> dms = getTaggedDownloads();

			for ( DownloadManager dm: dms ){

				dm.updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, priority>0 );
			}
		}

		getTagType().fireMetadataChanged( this );
	}

	@Override
	public int
	getTagMinShareRatio()
	{
		return( min_share_ratio );
	}

	@Override
	public void
	setTagMinShareRatio(
		int		sr )
	{
		if ( sr < 0 ){

			sr = 0;
		}

		if ( sr == min_share_ratio ){

			return;
		}

		min_share_ratio	= sr;

		writeLongAttribute( AT_RATELIMIT_MIN_SR, sr );

		Set<DownloadManager> dms = getTaggedDownloads();

		for ( DownloadManager dm: dms ){

			List<Tag> dm_tags = getTagType().getTagsForTaggable( dm );

			for ( Tag t: dm_tags ){

				if ( t == this ){

					continue;
				}

				if ( t instanceof TagFeatureRateLimit ){

					int o_sr = ((TagFeatureRateLimit)t).getTagMinShareRatio();

					if ( o_sr > sr ){

						sr = o_sr;
					}
				}
			}

			dm.getDownloadState().setIntParameter( DownloadManagerState.PARAM_MIN_SHARE_RATIO, sr );
		}

		getTagType().fireMetadataChanged( this );
	}

	@Override
	public int
	getTagMaxShareRatio()
	{
		return( max_share_ratio );
	}

	@Override
	public void
	setTagMaxShareRatio(
		int		sr )
	{
		if ( sr < 0 ){

			sr = 0;
		}

		if ( sr == max_share_ratio ){

			return;
		}

		max_share_ratio	= sr;

		writeLongAttribute( AT_RATELIMIT_MAX_SR, sr );

		Set<DownloadManager> dms = getTaggedDownloads();

		for ( DownloadManager dm: dms ){

			List<Tag> dm_tags = getTagType().getTagsForTaggable( dm );

			for ( Tag t: dm_tags ){

				if ( t == this ){

					continue;
				}

				if ( t instanceof TagFeatureRateLimit ){

					int o_sr = ((TagFeatureRateLimit)t).getTagMaxShareRatio();

					if ( o_sr > sr ){

						sr = o_sr;
					}
				}
			}

			dm.getDownloadState().setIntParameter( DownloadManagerState.PARAM_MAX_SHARE_RATIO, sr );
		}

		getTagType().fireMetadataChanged( this );

		checkIndividualShareRatio();
	}

	@Override
	public int
	getTagMaxShareRatioAction()
	{
		return( max_share_ratio_action );
	}

	@Override
	public void
	setTagMaxShareRatioAction(
		int		action )
	{
		if ( action == max_share_ratio_action ){

			return;
		}

		max_share_ratio_action	= action;

		writeLongAttribute( AT_RATELIMIT_MAX_SR_ACTION, action );

		getTagType().fireMetadataChanged( this );

		checkIndividualShareRatio();
	}

	@Override
	public int
	getTagAggregateShareRatio()
	{
		updateStuff();

		return( aggregate_sr );
	}

	@Override
	public int
	getTagMaxAggregateShareRatio()
	{
		return( max_aggregate_share_ratio );
	}

	@Override
	public void
	setTagMaxAggregateShareRatio(
		int		sr )
	{
		if ( sr < 0 ){

			sr = 0;
		}

		if ( sr == max_aggregate_share_ratio ){

			return;
		}

		max_aggregate_share_ratio	= sr;

		writeLongAttribute( AT_RATELIMIT_MAX_AGGREGATE_SR, sr );

		getTagType().fireMetadataChanged( this );

		checkAggregateShareRatio();
	}

	@Override
	public int
	getTagMaxAggregateShareRatioAction()
	{
		return( max_aggregate_share_ratio_action );
	}

	@Override
	public void
	setTagMaxAggregateShareRatioAction(
		int		action )
	{
		if ( action == max_aggregate_share_ratio_action ){

			return;
		}

		max_aggregate_share_ratio_action	= action;

		writeLongAttribute( AT_RATELIMIT_MAX_AGGREGATE_SR_ACTION, action );

		getTagType().fireMetadataChanged( this );

		checkAggregateShareRatio();
	}

	@Override
	public boolean
	getTagMaxAggregateShareRatioHasPriority()
	{
		return( max_aggregate_share_ratio_priority );
	}

	@Override
	public void
	setTagMaxAggregateShareRatioHasPriority(
		boolean		priority )
	{
		if ( priority == max_aggregate_share_ratio_priority ){

			return;
		}

		max_aggregate_share_ratio_priority	= priority;

		writeBooleanAttribute( AT_RATELIMIT_MAX_AGGREGATE_SR_PRIORITY, priority );

		getTagType().fireMetadataChanged( this );

		checkIndividualShareRatio();

		checkAggregateShareRatio();
	}

	@Override
	public boolean
	getFirstPrioritySeeding()
	{
		return( fp_seeding );
	}

	@Override
	public void
	setFirstPrioritySeeding(
		boolean		b )
	{
		if ( b == fp_seeding ){

			return;
		}

		fp_seeding	= b;

		writeBooleanAttribute( AT_RATELIMIT_FP_SEEDING, b );

		getTagType().fireMetadataChanged( this );

		checkFPSeeding();
	}
	
	public void
	setPreventDelete(
		boolean		b )
	{
		super.setPreventDelete( b );
		
		if ( prevent_delete != b ){
			
			prevent_delete = b;
				
			TaggableResolver resolver = getTagType().getResolver();
			
			if ( prevent_delete && resolver != null ){
				
				resolver.addLifecycleControlListener( this );
				
			}else{
				
				resolver.removeLifecycleControlListener( this );
			}
		}
	}
	
	public void
	canTaggableBeRemoved(
		Taggable		taggable )
	
		throws Exception
	{
		if ( prevent_delete && hasTaggable( taggable )){
			
			
			throw( new Exception( "Download removal prevented by Tag '" + getTagName() + "'" ));
		}
	}
	
	@Override
	public void
	setNotifyMessageChannel(
		String		channel )
	{
		super.setNotifyMessageChannel(channel);
		
		notification_pub = getNotifyMessageChannel();
	}
	
	private void
	updateStuff()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now - last_rate_update > 2500 ){

			int	new_up		= 0;
			int new_down	= 0;

			long new_agg_up		= 0;
			long new_agg_down	= 0;


			Set<DownloadManager> dms = getTaggedDownloads();

			if ( dms.size() == 0 ){

				new_up		= -1;
				new_down	= -1;

			}else{

				new_up		= 0;
				new_down	= 0;

				for ( DownloadManager dm: dms ){

					DownloadManagerStats stats = dm.getStats();

					new_up 		+= stats.getDataSendRate() + stats.getProtocolSendRate();
					new_down 	+= stats.getDataReceiveRate() + stats.getProtocolReceiveRate();

					long downloaded	= stats.getTotalGoodDataBytesReceived();
					long uploaded	= stats.getTotalDataBytesSent();

					if ( downloaded > 0 ){

						new_agg_down += downloaded;
					}

					if ( uploaded > 0 ){

						new_agg_up += uploaded;
					}

				}
			}

			upload_rate			= new_up;
			download_rate		= new_down;

			aggregate_sr = new_agg_down<=0?0:(int) ((1000 * new_agg_up) / new_agg_down);


			last_rate_update 	= now;
		}
	}

	private void
	checkIndividualShareRatio()
	{
		if ( max_share_ratio <= 0 ){

				// not enabled

			return;
		}

		if ( max_share_ratio_action == TagFeatureRateLimit.SR_ACTION_QUEUE ){

				// handled by start/stop rules

			return;
		}

		if ( max_aggregate_share_ratio_priority && max_aggregate_share_ratio > 0 ){

			updateStuff();

			if ( aggregate_sr < max_aggregate_share_ratio ){

					// aggregate has priority, is enabled and not met so bail until it is

				return;
			}
		}

		Set<DownloadManager> dms = getTaggedDownloads();

		Set<DownloadManager>	to_action = new HashSet<>();

		for ( DownloadManager dm: dms ){

			if ( dm.isDownloadComplete( false ) && !dm.isForceStart()){

				int state = dm.getState();

				if ( state == DownloadManager.STATE_QUEUED || state == DownloadManager.STATE_SEEDING ){

					int sr = dm.getStats().getShareRatio();

					if ( sr >= max_share_ratio ){

						to_action.add( dm );
					}
				}
			}
		}

		if ( to_action.size() > 0 ){

			int	operation;
			
			switch( max_share_ratio_action ){
				case SR_ACTION_PAUSE:{
					operation = TagFeatureRunState.RSC_PAUSE;
					break;
				}
				case SR_ACTION_STOP:{
					operation = TagFeatureRunState.RSC_STOP;
					break;
				}
				case SR_ACTION_ARCHIVE:{
					operation = TagFeatureRunState.RSC_ARCHIVE;
					break;
				}
				case SR_ACTION_REMOVE_FROM_LIBRARY:{
					operation = TagFeatureRunState.RSC_REMOVE_FROM_LIBRARY;
					break;	
				}
				case SR_ACTION_REMOVE_FROM_COMPUTER:{
					operation = TagFeatureRunState.RSC_REMOVE_FROM_COMPUTER;
					break;	
				}
				default:{
					Debug.out( "Invalid share ratio action" );
					
					return;
				}
			}
			
			performOperation( operation, to_action.stream());
		}
	}

	private boolean
	isAggregateShareRatioMet()
	{
		if ( max_aggregate_share_ratio == 0 ){

			return( true );
		}

		updateStuff();

		return( aggregate_sr >= max_aggregate_share_ratio );
	}

	private void
	checkAggregateShareRatio()
	{
		if ( max_aggregate_share_ratio > 0 ){

				// don't do anything if we are in the process of deleting torrents - in particular we
				// want to avoid resuming a download that is about to be deleted along with others
				// that contribute to the same ratio. We'll come back here and recheck anyway

			if ( 	TorrentUtils.isTorrentDeleting() ||
					TorrentUtils.getMillisecondsSinceLastTorrentDelete() < 10*1000 ){

				return;
			}

			updateStuff();

			if ( aggregate_sr >= max_aggregate_share_ratio ){

				Set<DownloadManager> dms = new HashSet<>(getTaggedDownloads());

				Iterator<DownloadManager> it = dms.iterator();

					// don't pause incomplete downloads!

				while( it.hasNext()){

					DownloadManager dm = it.next();

					if ( dm.isForceStart() || !dm.isDownloadComplete( false )){

						it.remove();

					}else{

						if ( ( !max_aggregate_share_ratio_priority ) && max_share_ratio > 0 ){

							int sr = dm.getStats().getShareRatio();

							if ( sr < max_share_ratio ){

									// individual has priority over aggregate and this download hasn't met
									// its ratio yet

								it.remove();

								continue;
							}
						}

						List<Tag> all_tags = getTagType().getTagManager().getTagsForTaggable( dm );

						for ( Tag tag: all_tags ){

							if ( tag != this && tag instanceof TagDownloadWithState ){

								TagDownloadWithState other_tag = (TagDownloadWithState)tag;

								if ( !other_tag.isAggregateShareRatioMet()){

									it.remove();

									break;
								}
							}
						}
					}
				}

				performOperation(
					max_aggregate_share_ratio_action == TagFeatureRateLimit.SR_ACTION_PAUSE?
						TagFeatureRunState.RSC_PAUSE:TagFeatureRunState.RSC_STOP,
					dms.stream());

			}else{

				performOperation(
						max_aggregate_share_ratio_action == TagFeatureRateLimit.SR_ACTION_PAUSE?
							TagFeatureRunState.RSC_RESUME:TagFeatureRunState.RSC_START );
			}
		}
	}

	private void
	checkFPSeeding()
	{
		if ( fp_seeding ){
							
			fp_seeding_ever = true;
		}

		if ( !fp_seeding_ever ){
			
			return;
		}
		
		Set<DownloadManager> dms = new HashSet<>(getTaggedDownloads());

		Iterator<DownloadManager> it = dms.iterator();

			// don't pause incomplete downloads!

		while( it.hasNext()){

			DownloadManager dm = it.next();

			updateFPSeeding( dm, fp_seeding );
		}
	}
	
	private void
	updateFPSeeding(
		DownloadManager		dm,
		boolean				fp_seed )
	
	{
		if ( fp_seed ){
			
			fp_seeding_ever = true;
		}
		
		synchronized( FP_DL_KEY ){
			
			Map<DownloadManager,String> map = (Map<DownloadManager,String>)dm.getUserData( FP_DL_KEY );
			
			if ( fp_seed ){
				
				if ( map == null ){
					
					map = new IdentityHashMap<>();
					
					dm.setUserData( FP_DL_KEY, map );
					
					dm.getDownloadState().setTransientFlag( DownloadManagerState.TRANSIENT_FLAG_TAG_FP, true );
				}
				
				map.put( dm, "" );
				
			}else{
				
				if ( map != null ){
					
					map.remove( dm );
					
					if ( map.isEmpty()){
						
						dm.setUserData( FP_DL_KEY, null );
						
						dm.getDownloadState().setTransientFlag( DownloadManagerState.TRANSIENT_FLAG_TAG_FP, false );
					}
				}
			}
		}
	}
	
	private ChatInstance 	notification_pub_channel;
	private String			notification_pub_channel_key;
	private long			npc_initialised_time;
	private boolean			npc_chat_ready;
	
	private AtomicLong		ncp_pub_list_mutate_index = new AtomicLong();
	
	private LinkedList<NPCState>	ncp_pub_list;
	private long					ncp_pub_list_mut;
	
	private static final String NPC_ATTRIBUTE_NAME = "_tm::npc";
	
	private void
	checkNotifyPublish()
	{
		String channel = notification_pub;
				
		if ( channel.isEmpty()){
			
			if ( notification_pub_channel != null ){
				
				notification_pub_channel.destroy();
				
				notification_pub_channel = null;
				
				ncp_pub_list = null;
			}
		}else{
			
			if ( notification_pub_channel_key != null ){
				
				if ( notification_pub_channel != null ){
					
					if ( !notification_pub_channel.getNetAndKey().equals( notification_pub_channel_key )){
						
						notification_pub_channel.destroy();
						
						notification_pub_channel = null;
						
						ncp_pub_list = null;
					}
				}
			}
			
			if ( notification_pub_channel == null ){
				
				String[] bits = channel.split( ":", 2 );
				
				String net = bits[0].startsWith( "Public")?AENetworkClassifier.AT_PUBLIC:AENetworkClassifier.AT_I2P;
				
				String key = bits[1].trim();
		
					// stop stuff going to our channels
				
				if ( key.startsWith( Constants.APP_NAME )){
					
					return;
				}
				
				notification_pub_channel 		= BuddyPluginUtils.getChat(net, key);
				
				if ( notification_pub_channel == null ){
					
					return;
				}
				
				notification_pub_channel_key	= channel;
				npc_initialised_time			= 0;
				npc_chat_ready					= false;
			}
			
			if ( !npc_chat_ready ){
				
				long now = SystemTime.getMonotonousTime();
				
				if ( npc_initialised_time == 0 ){
					
					if ( notification_pub_channel.isInitialised()){
						
						npc_initialised_time = now;
					}
				}else{
					
					if ( now - npc_initialised_time > 60*1000 && notification_pub_channel.getIncomingSyncState() == 0 ){
						
						npc_chat_ready = true;
					}
				}
			}else{
												
				Set<DownloadManager>	dms = getTaggedDownloads();
				
				String state_key = getTagUID() + ":" + notification_pub_channel.getDefaultNickname();	// should be good enough
				
				LinkedList<NPCState>	pub_list = ncp_pub_list;
				
				long mut = ncp_pub_list_mutate_index.get();
				
				if ( pub_list == null || ncp_pub_list_mut != mut ){
							
					ncp_pub_list_mut 	= mut;
						
					List<NPCState>	new_pub_list = new ArrayList<>( dms.size());
					
					for ( DownloadManager dm: dms ){
						
						synchronized( NPC_ATTRIBUTE_NAME ){
						
							Map<String,Object> state = (Map<String,Object>)dm.getDownloadState().getMapAttribute( NPC_ATTRIBUTE_NAME );
							
							long last_pub;
							
							if ( state == null ){
																
								last_pub = 0;
								
							}else{
							
								Long l_last_pub = (Long)state.get( state_key );
							
								if ( l_last_pub != null ){
									
									last_pub = l_last_pub;
									
								}else{
									
									last_pub = 0;
								}
							}
														
							new_pub_list.add( new NPCState( last_pub, dm ));
						}
					}
					
					Collections.sort( 
						new_pub_list,
						new Comparator<NPCState>(){
							@Override
							public int 
							compare(
								NPCState o1, 
								NPCState o2 )
							{
								long	l1 = o1.last_pub;
								long	l2 = o2.last_pub;
								
								if ( l1 == l2 ){	// generally never published if same
									
									DownloadManager dm1 = o1.dm;
									DownloadManager dm2 = o2.dm;
									
									long a1 = dm1.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );
									long a2 = dm2.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );
									
										// we want the newest added first as given other things we want to publish newer before older
									
									if ( a1 == a2 ){
										return(0);
									}else if ( a1 < a2 ){
										return( 1 );
									}else{
										return( -1 );
									}
								}else{
										// we want the oldest published first as that is the first to re-publish
									if ( l1 < l2 ){
										return( -1 );
									}else{
										return( 1 );
									}
								}
							}
						});
						
					/*
					System.out.println( "built pub list" );
					
					for ( NPCState x: new_pub_list ){
						
						System.out.println( x.dm.getDisplayName());
					}
					*/
					
					pub_list = new LinkedList<>( new_pub_list );
					
					ncp_pub_list = pub_list;	
				}
				
				if ( !pub_list.isEmpty()){
					
					int num_to_publish = Math.min( 1, pub_list.size());
					
					for ( int num=0; num < num_to_publish; num++ ){
						
						NPCState next = pub_list.removeFirst();
						
							// continually cycle through them, even if we don't actually publish them
						
						DownloadManager dm = next.dm;
						
						pub_list.addLast( new NPCState( SystemTime.getCurrentTime(), dm ));
		
						boolean	already_published = false;
	
							// only 128 normal messages are actually replicated, rest are only in UI buffer

						int	max_msg_to_check = 128;
						
						if ( pub_list.size() <= max_msg_to_check ){
						
							List<ChatMessage> messages = notification_pub_channel.getMessages();
								
							int	normal_count = 0;
							
							try{
								TOTorrent torrent = next.dm.getTorrent();
								
								if ( torrent == null ){
									
									already_published = true;	// borked, avoid publish
									
								}else{
																		
									String magnet = UrlUtils.getMagnetURI( torrent.getHash());
									
									for ( int i=messages.size()-1;i>=0;i--){
										
										ChatMessage message = messages.get(i);
										
										if ( message.getMessageType() == ChatMessage.MT_NORMAL ){
											
											normal_count++;
											
											String text = message.getMessage();
											
											if ( text.contains( magnet )){
												
												already_published = true;
												
												break;
											}
											
											if ( normal_count == max_msg_to_check ){
												
												break;
											}
										}
									}
								}
							}catch( Throwable e ){
								
								already_published = true;	// borked, avoid publish
								
								Debug.out( e );
							}
						}
						
						if ( !already_published ){
														
							try{					
								notification_pub_channel.sendMessage(  PluginCoreUtils.wrap( dm ));
								
								synchronized( NPC_ATTRIBUTE_NAME ){
									
									Map<String,Object> state = (Map<String,Object>)dm.getDownloadState().getMapAttribute( NPC_ATTRIBUTE_NAME );
								
									if ( state == null ){
										
										state = new HashMap<>();
										
									}else{
										
										state = BEncoder.cloneMap( state );
									}
									
									state.put( state_key, SystemTime.getCurrentTime());
									
									dm.getDownloadState().setMapAttribute( NPC_ATTRIBUTE_NAME, state );
								}
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					}
				}
			}
		}
	}
	
	private class
	NPCState
	{
		final DownloadManager		dm;
		final long					last_pub;
		
		NPCState(
			long				t,
			DownloadManager		d )
		{
			last_pub	= t;
			dm			= d;
		}
	}
	
	@Override
	protected void
	sync()
	{
		checkIndividualShareRatio();

		checkAggregateShareRatio();

		checkMaximumTaggables();

		checkFPSeeding();
		
		checkNotifyPublish();
		
		checkSort();
		
		super.sync();
	}

	@Override
	public int
	getRunStateCapabilities()
	{
		return( run_states );
	}

	@Override
	public boolean
	hasRunStateCapability(
		int		capability )
	{
		return((run_states & capability ) != 0 );
	}

	@Override
	public boolean[]
   	getPerformableOperations(
      	int[]					ops )
   	{
		return( getPerformableOperations( ops, (t)->true ));
   	}

	@Override
	public boolean[]
   	getPerformableOperations(
      	int[]					ops,
      	Predicate<Taggable>		filter )
   	{
   		boolean[] result = new boolean[ ops.length];

		Set<DownloadManager> dms = getTaggedDownloads();

		for ( DownloadManager dm: dms ){

			if ( !filter.test(dm)){
				
				continue;
			}
			
			int	dm_state = dm.getState();

			for ( int i=0;i<ops.length;i++){

				if ( result[i]){

					continue;
				}

				int	op = ops[i];

				if (( op & TagFeatureRunState.RSC_START ) != 0 ){

					if ( 	dm_state == DownloadManager.STATE_STOPPED ||
							dm_state == DownloadManager.STATE_ERROR ){

						result[i] = true;
						
					}else if ( dm.isForceStart()){
						
						result[i] = true;	// transition force-start -> start (queue)
					}
				}
				
				if (( op & TagFeatureRunState.RSC_FORCE_START ) != 0 ){

					if ( !dm.isForceStart()){
						
						result[i] = true;
					}
				}


				if (( op & TagFeatureRunState.RSC_STOP ) != 0 ){

					if ( 	dm_state != DownloadManager.STATE_STOPPED &&
							dm_state != DownloadManager.STATE_STOPPING &&
							dm_state != DownloadManager.STATE_ERROR ){

						result[i] = true;
					}
				}

				if (( op & TagFeatureRunState.RSC_PAUSE ) != 0 ){

					if ( 	dm_state != DownloadManager.STATE_STOPPED &&
							dm_state != DownloadManager.STATE_STOPPING &&
							dm_state != DownloadManager.STATE_ERROR ){

						if ( !dm.isPaused()){

							result[i] = true;
						}
					}
				}

				if (( op & TagFeatureRunState.RSC_RESUME ) != 0 ){

					if ( dm.isPaused()){

						result[i] = true;
					}
				}
			}
		}

		return( result );
   	}

	@Override
	public void
	performOperation(
		int		op )
	{
		performOperation( op, (t)->true );
	}
	
	@Override
	public void
	performOperation(
		int						op,
		Predicate<Taggable>		filter )
	{
		Set<DownloadManager> dms = getTaggedDownloads();

		performOperation( op, dms.stream().filter(filter) );
	}

	private void
	performOperation(
		int					op,
		Stream<DownloadManager> dms )
	{
		dms.forEach((dm)->{
			int	dm_state = dm.getState();

			if ( op == TagFeatureRunState.RSC_START ){

				if ( 	dm_state == DownloadManager.STATE_STOPPED ||
						dm_state == DownloadManager.STATE_ERROR ){

					rs_async.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								dm.setStateQueued();
							}
						});
				}else if ( dm.isForceStart()){
					
					rs_async.dispatch(()->{ dm.setForceStart( false ); });
				}
			}else if ( op == TagFeatureRunState.RSC_FORCE_START ){

				if ( !dm.isForceStart()){

					rs_async.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								dm.setForceStart( true );
							}
						});
				}
			}else if ( op == TagFeatureRunState.RSC_STOP ){

				if ( 	dm_state != DownloadManager.STATE_STOPPED &&
						dm_state != DownloadManager.STATE_STOPPING &&
						dm_state != DownloadManager.STATE_ERROR ){

					rs_async.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								dm.stopIt( DownloadManager.STATE_STOPPED, false, false );

									// recheck here in case it is an 'archive' action that requires
									// download to be stopped

								checkMaximumTaggables();
							}
						});
				}
			}else if ( op == TagFeatureRunState.RSC_PAUSE ){

				if ( 	dm_state != DownloadManager.STATE_STOPPED &&
						dm_state != DownloadManager.STATE_STOPPING &&
						dm_state != DownloadManager.STATE_ERROR ){

					rs_async.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								dm.pause( true );
							}
						});
				}
			}else if ( op == TagFeatureRunState.RSC_RESUME ){

				if ( dm.isPaused()){

					rs_async.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								dm.resume();
							}
						});
				}
			}else{
				
				rs_async.dispatch(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							int	dm_state = dm.getState();
						
							if ( 	dm_state != DownloadManager.STATE_STOPPED &&
									dm_state != DownloadManager.STATE_ERROR ){
								
								dm.stopIt( DownloadManager.STATE_STOPPED, false, false );
							}
			
							try{
								if ( op == TagFeatureRunState.RSC_ARCHIVE ){
	
									Download download = PluginCoreUtils.wrap( dm );
				
									if ( download.canStubbify()){
							
											// have to remove from tag otherwise when it is restored it will no doubt get re-archived!

										removeTaggable( dm );

										download.stubbify();
									}
								}else if ( op == TagFeatureRunState.RSC_REMOVE_FROM_LIBRARY ){
				
									dm.getGlobalManager().removeDownloadManager( dm, false, false );
				
								}else if ( op == TagFeatureRunState.RSC_REMOVE_FROM_COMPUTER ){
				
									boolean reallyDeleteData =  !dm.getDownloadState().getFlag(	Download.FLAG_DO_NOT_DELETE_DATA_ON_REMOVE );
				
									dm.getGlobalManager().removeDownloadManager( dm, true, reallyDeleteData);
								}
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					});
			}
		});
	}

	@Override
	public int
	getSupportedActions()
	{
		if ( getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

			return( TagFeatureExecOnAssign.ACTION_START |
					TagFeatureExecOnAssign.ACTION_RESUME |
					TagFeatureExecOnAssign.ACTION_FORCE_START |
					TagFeatureExecOnAssign.ACTION_NOT_FORCE_START |
					TagFeatureExecOnAssign.ACTION_STOP |
					TagFeatureExecOnAssign.ACTION_QUEUE |
					TagFeatureExecOnAssign.ACTION_PAUSE |
					TagFeatureExecOnAssign.ACTION_SCRIPT |
					TagFeatureExecOnAssign.ACTION_APPLY_OPTIONS_TEMPLATE |
					TagFeatureExecOnAssign.ACTION_POST_MAGNET_URI |
					TagFeatureExecOnAssign.ACTION_MOVE_INIT_SAVE_LOC |
					TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS |
					TagFeatureExecOnAssign.ACTION_HOST |
					TagFeatureExecOnAssign.ACTION_PUBLISH |
					TagFeatureExecOnAssign.ACTION_REMOVE_TAGS );

		}else if ( getTagType().getTagType() == TagType.TT_DOWNLOAD_STATE ){

			return( TagFeatureExecOnAssign.ACTION_SCRIPT |
					TagFeatureExecOnAssign.ACTION_POST_MAGNET_URI );

		}else{

			return( TagFeatureExecOnAssign.ACTION_NONE );
		}
	}

	protected void
	setSupportsTagTranscode(
		boolean	sup )
	{
		supports_xcode = sup;
	}

	@Override
	public boolean
	supportsTagTranscode()
	{
		return( supports_xcode );
	}

	@Override
	public String[]
	getTagTranscodeTarget()
	{
		String temp = readStringAttribute( AT_XCODE_TARGET, null );

		if ( temp == null ){

			return( null );
		}

		String[] bits = temp.split( "\n" );

		if ( bits.length != 2 ){

			return( null );
		}

		return( bits );
	}

	@Override
	public void
	setTagTranscodeTarget(
		String		uid,
		String		name )
	{
		writeStringAttribute( AT_XCODE_TARGET, uid==null?null:(uid + "\n" + name ));

		getTagType().fireMetadataChanged( this );

		getManager().featureChanged( this, TagFeature.TF_XCODE );
	}

	protected void
	setSupportsFileLocation(
		boolean		sup )
	{
		supports_file_location = sup;
	}

	@Override
	public boolean
	supportsTagInitialSaveFolder()
	{
		return( supports_file_location );
	}

	@Override
	public boolean
	supportsTagMoveOnComplete()
	{
		return( supports_file_location );
	}

	@Override
	public boolean
	supportsTagCopyOnComplete()
	{
		return( supports_file_location );
	}

	@Override
	public boolean
	supportsTagMoveOnRemove()
	{
		return( supports_file_location );
	}
	
	@Override
	public boolean
	supportsTagMoveOnAssign()
	{
		return( supports_file_location );
	}
	
	@Override
	public TagProperty[]
	getSupportedProperties()
	{
		return( getTagType().isTagTypeAuto()?new TagProperty[0]:tag_properties );
	}

	private static final boolean[] AUTO_BOTH = {true,true,false};
	private static final boolean[] AUTO_NONE = {false,false,false};

	@Override
	public boolean[]
	isTagAuto()
	{
		TagProperty[]	props = getSupportedProperties();

		for ( TagProperty prop: props ){

			String name = prop.getName( false );

			if ( name.equals( TagFeatureProperties.PR_TRACKER_TEMPLATES )){

				continue;
			}

			int	type =  prop.getType();

			if ( type == TagFeatureProperties.PT_BOOLEAN ){

					// PR_UNTAGGED
				
				Boolean b = prop.getBoolean();

				if ( b != null && b ){

					return( AUTO_BOTH );
				}
			}else if ( type == TagFeatureProperties.PT_LONG ){

				Long l = prop.getLong();

				if ( l != null && l != Long.MIN_VALUE ){

					return( AUTO_BOTH );
				}
			}else if ( type == TagFeatureProperties.PT_STRING_LIST ){

				String[] vals = prop.getStringList();

				if ( vals != null && vals.length > 0 ){

					if ( name.equals( TagFeatureProperties.PR_CONSTRAINT )){
						
							// PR_CONSTRAINT
						
						if ( !prop.isEnabled()){
							
							return( AUTO_NONE );	// currently only constraints can be disabled
						}
						
						String constraint	= vals[0];						
						
						if ( constraint == null || constraint.trim().isEmpty()){
							
							return( AUTO_NONE );
						}
						
						if ( vals.length > 1 ){

							String options 		= vals[1];
		
							if ( options != null ){
	
								if ( options.contains( "am=1;" )){
	
									return( new boolean[]{ true, false, false });
	
								}else if ( options.contains( "am=2;" )){
	
									return( new boolean[]{ false, true, false });
									
								}else if ( options.contains( "am=3;" )){
									
									return( new boolean[]{ false, false, true });
								}
							}
						}
	
						return( AUTO_BOTH );
						
					}else{
						
							// PR_TRACKERS
						
						return( AUTO_BOTH );
					}
				}
			}
		}

		return( AUTO_NONE );
	}

	@Override
	public int
	getMaximumTaggables()
	{
		if ( getTagType().getTagType() != TagType.TT_DOWNLOAD_MANUAL ){

			return( -1 );
		}

		return( super.getMaximumTaggables());
	}

	@Override
	protected void
	checkMaximumTaggables()
	{
		if ( getTagType().getTagType() != TagType.TT_DOWNLOAD_MANUAL ){

			return;
		}

		int max = getMaximumTaggables();

		if ( max <= 0 ){

			return;
		}

		if ( max == 999999 ){

			max = 0;
		}

		int removal_strategy = getRemovalStrategy();

		if ( removal_strategy == RS_NONE ){

			return;
		}

		if ( getTaggedCount() > max ){

			Set<DownloadManager> dms = getTaggedDownloads();

			List<DownloadManager>	sorted_dms = new ArrayList<>(dms);

			final int	order = getOrdering();

			Collections.sort(
				sorted_dms,
				new Comparator<DownloadManager>()
				{
					@Override
					public int
					compare(
						DownloadManager dm1,
						DownloadManager dm2)
					{
						if ( order == OP_ADDED_TO_VUZE ){

							long t1 = dm1.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );
							long t2 = dm2.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );

							if ( t1 < t2 ){

								return( -1 );

							}else if ( t1 > t2 ){

								return( 1 );

							}else{

								return( dm1.getInternalName().compareTo( dm2.getInternalName()));
							}
						}else{

							long t1 = getTaggableAddedTime( dm1 );
							long t2 = getTaggableAddedTime( dm2 );

							if ( t1 < t2 ){

								return( -1 );

							}else if ( t1 > t2 ){

								return( 1 );

							}else{

								return( dm1.getInternalName().compareTo( dm2.getInternalName()));
							}
						}
					}
				});

			Iterator<DownloadManager> it = sorted_dms.iterator();

			while( it.hasNext() && sorted_dms.size() > max ){

				DownloadManager dm = it.next();

				if ( dm.isPersistent()){

					it.remove();

					try{
						if ( removal_strategy == RS_ARCHIVE ){

							Download download = PluginCoreUtils.wrap( dm );

							if ( download.canStubbify()){

									// have to remove from tag otherwise when it is restored it will no doubt get re-archived!

								removeTaggable( dm );

								download.stubbify();
							}
						}else if ( removal_strategy == RS_REMOVE_FROM_LIBRARY ){

							dm.getGlobalManager().removeDownloadManager( dm, false, false );

						}else if ( removal_strategy == RS_DELETE_FROM_COMPUTER ){

							boolean reallyDeleteData =  !dm.getDownloadState().getFlag(	Download.FLAG_DO_NOT_DELETE_DATA_ON_REMOVE );

							dm.getGlobalManager().removeDownloadManager( dm, true, reallyDeleteData);

						}else if ( removal_strategy == RS_MOVE_TO_OLD_TAG ){

							String old_tag = getTagName( true ) + "_";

							if ( Character.isUpperCase( old_tag.charAt(0))){

								old_tag += "Old";
							}else{

								old_tag += "old";
							}

							Tag ot = getTagType().getTag( old_tag, true );

							if ( ot == null ){

								ot = getTagType().createTag( old_tag, true );
							}

							ot.addTaggable( dm );

							removeTaggable( dm );
						}

					}catch( Throwable e ){

						Debug.out( e );
					}
				}else{

					// can't remove/archive non-persistent downloads here so just ignore them

					Logger.log(
						new LogAlert(
							LogAlert.UNREPEATABLE,
							LogAlert.AT_WARNING,
							"Non-persistent downloads (e.g. shares) can't be automatically deleted or archived. Maximum entries not enforced for Tag '" + getTagName( true ) + "'" ));
				}
			}
		}
	}
	
	@Override
	public List<Tag>
	dependsOnTags()
	{
		return( new ArrayList<Tag>( getManager().getDependsOnTags( this )));
	}
	
	@Override
	public String
	getStatus()
	{
		String result = "";
		
		String error = (String)getTransientProperty( Tag.TP_CONSTRAINT_ERROR );
		
		if ( error != null ){
			
			result += "Error: " + error;
		}
		
		String other = getManager().getTagStatus( this );
		
		if ( other != null ){
			
			result += (result.isEmpty()?"":"; ") + other;
		}
		
		return( result );
	}
	
	@Override
	public void 
	setAutoApplySortInterval(
		int secs)
	{
		super.setAutoApplySortInterval( secs );
		
		auto_sort_period = secs;
	}
	
	private void
	checkSort()
	{
		if ( auto_sort_period == 0 ){
			
			return;
		}
		
		long now = SystemTime.getMonotonousTime();
		
		if ( last_auto_sort == -1 || now - last_auto_sort >= auto_sort_period*1000 ){
			
			if ( applySortSupport()){
				
				last_auto_sort = now;
			}
		}
	}
	
	private boolean
	applySortSupport()
	{
		try{
			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
			
			List<DownloadManager> downloads = new ArrayList<>( getTaggedDownloads());
			
			Collections.sort( downloads, (d1,d2)->{
				return( Integer.compare( d1.getPosition(), d2.getPosition()));
			});
				
			Long uid = getTagUID();
			
			List<Integer>	download_positions_comp 	= new ArrayList<>(downloads.size());
			List<Integer>	download_positions_incomp 	= new ArrayList<>(downloads.size());
			
			List<Object[]>	tag_sort_comp 	= new ArrayList<>(downloads.size());
			List<Object[]>	tag_sort_incomp = new ArrayList<>(downloads.size());
			
			for ( DownloadManager download: downloads ){
				
				Map<Long,Object[]> map = (Map<Long,Object[]>)download.getDownloadState().getTransientAttribute( DownloadManagerState.AT_TRANSIENT_TAG_SORT );
				
				Object[] entry = (Object[])map.get( uid );
				
				if ( entry == null ){
					
					System.out.println( "tag sort missing for " + download.getDisplayName());
					
					return( false );
				}
				
				int pos = download.getPosition();
								
				if ( download.isDownloadComplete(false)){
				
					download_positions_comp.add( pos );
				
					tag_sort_comp.add( new Object[]{ download, entry[1], pos });

				}else{
					
					download_positions_incomp.add( pos );
					
					tag_sort_incomp.add( new Object[]{ download, entry[1], pos });
				}
			}
			
			Collections.sort( download_positions_comp );
			
			Collections.sort( download_positions_incomp );
			
			Collections.sort( tag_sort_comp, (e1,e2)->{
				return(Long.compare((Long)e1[1],(Long)e2[1]));
			});
			
			Collections.sort( tag_sort_incomp, (e1,e2)->{
				return(Long.compare((Long)e1[1],(Long)e2[1]));
			});
			
			for ( int i=0;i<download_positions_comp.size();i++){
				
				int dl_pos = download_positions_comp.get(i);
				
				Object[] entry = tag_sort_comp.get(i);
				
				int current_pos = (Integer)entry[2];
				
				if ( current_pos != dl_pos ){
					
					gm.moveTo((DownloadManager)entry[0], dl_pos );
				}
			}
			
			for ( int i=0;i<download_positions_incomp.size();i++){
				
				int dl_pos = download_positions_incomp.get(i);
				
				Object[] entry = tag_sort_incomp.get(i);
				
				int current_pos = (Integer)entry[2];
				
				if ( current_pos != dl_pos ){
					
					gm.moveTo((DownloadManager)entry[0], dl_pos );
				}
			}
			
			return( true );
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( false );
		}
	}
	
	@Override
	public void 
	applySort()
	{
		applySortSupport();
	}
}
