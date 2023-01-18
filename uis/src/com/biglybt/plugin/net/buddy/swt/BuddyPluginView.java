/*
 * Created on Mar 19, 2008
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


package com.biglybt.plugin.net.buddy.swt;

import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.security.*;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.plugin.I2PHelpers;
import com.biglybt.plugin.net.buddy.*;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatMessage;
import com.biglybt.plugin.net.buddy.tracker.BuddyPluginTracker;
import com.biglybt.plugin.net.buddy.tracker.BuddyPluginTrackerListener;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.minibar.AllTransfersBar;
import com.biglybt.ui.swt.pif.*;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.utils.TagUIUtils;

import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.*;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.*;


/**
 * Setup the SWT UI for Friends/Buddy
 * 
 * <p/>
 * TODO: Mebbe Rename to BuddyPluginSWTUI now that View Listener stuff is moved to new class
 */
public class
BuddyPluginView
	implements BuddyPluginViewInterface
{
	public static final String VIEWID_CHAT = "azbuddy.ui.menu.chat";
	
	private static final Object TT_KEY = new Object();
	
	private  TimerEvent 			buddyStatusInit;
	private BuddyPluginAZ2Listener buddyPluginAZ2Listener;
	
	private BuddyPlugin		plugin;
	private UISWTInstance	ui_instance;

	private Image iconNLI;
	private Image iconIDLE;
	private Image iconIN;
	private Image iconOUT;
	private Image iconINOUT;

	private TimerEventPeriodic periodicEventMsgCheck;
	private statusUpdater statusUpdater;
	private TaggableLifecycleAdapter taggableLifecycleAdapter;

	private TableColumnCreationListener		columnMessagePending;
	private final List<TableColumn> columns = new ArrayList<>();

	/**
	 * Called via reflection in {@link BuddyPlugin}
	 */
	public
	BuddyPluginView(
		BuddyPlugin		_plugin,
		UIInstance		_ui_instance)
	{
		init( _plugin, _ui_instance );
	}

	private void
	init(
		BuddyPlugin		_plugin,
		UIInstance		_ui_instance )
	{
		plugin			= _plugin;
		ui_instance		= (UISWTInstance)_ui_instance;

		buddyPluginAZ2Listener = new BuddyPluginAZ2Listener() {
			@Override
			public void
			chatCreated(
					final BuddyPluginAZ2.chatInstance chat )
			{
				final Display display = ui_instance.getDisplay();

				if ( display.isDisposed()){
					return;
				}

				display.asyncExec(() -> {
						if ( !display.isDisposed()){

							new BuddyPluginViewChat( plugin, display, chat );
						}
					});
			}

			@Override
			public void
			chatDestroyed(
					BuddyPluginAZ2.chatInstance		chat )
			{
			}
		};
		
		for ( BuddyPluginNetwork pn: plugin.getPluginNetworks()){
			
			pn.getAZ2Handler().addListener(buddyPluginAZ2Listener);
		}
	
		buddyStatusInit = SimpleTimer.addEvent("BuddyStatusInit", SystemTime.getOffsetTime(1000),
			event -> statusUpdater = new statusUpdater(ui_instance));

		Utils.execSWTThread(() -> {
			ImageLoader imageLoader = ImageLoader.getInstance();

			iconNLI 	= imageLoader.getImage( "bbb_nli" );
			iconIDLE 	= imageLoader.getImage( "bbb_idle" );
			iconIN 		= imageLoader.getImage( "bbb_in" );
			iconOUT 	= imageLoader.getImage( "bbb_out" );
			iconINOUT 	= imageLoader.getImage( "bbb_inout" );
		});
		
		ui_instance.registerView(UISWTInstance.VIEW_MAIN,
				ui_instance.createViewBuilder(
						FriendsView.VIEW_ID).setListenerInstantiator(
							new UISWTViewBuilder.UISWTViewEventListenerInstantiator()
							{
								@Override
								public boolean
								supportsMultipleViews()
								{
									return( true );
								}
								
								public UISWTViewEventListener 
								createNewInstance(UISWTViewBuilder Builder, UISWTView forView) throws Exception
								{
									return( new FriendsView(BuddyPluginView.this, plugin, ui_instance));
								}
								@Override
								public String 
								getUID()
								{
									return( FriendsView.VIEW_ID );
								}
							}));
					

		checkBetaInit();
	}
	
	@Override
	public void
	selectClassicTab()
	{
		if (ui_instance == null) {
			return;
		}
		ui_instance.openView(UISWTInstance.VIEW_MAIN, FriendsView.VIEW_ID,
				FriendsView.DS_SELECT_CLASSIC_TAB);
	}

	@Override
	public void
	openChat(
		final ChatInstance chat )
	{
		final Display display = Display.getDefault();

		if ( display.isDisposed()){

			return;
		}

		display.asyncExec(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					if ( display.isDisposed()){

						return;
					}

					BuddyPluginViewBetaChat.createChatWindow( BuddyPluginView.this, plugin, chat );
				}
			});
	}

	protected class
	statusUpdater
		implements BuddyPluginTrackerListener
	{
		private final BuddyPluginAdapter buddyPluginAdapter;
		private final CryptoManagerKeyListener cryptoManagerKeyListener;
		private UISWTStatusEntry	label;
		private String				label_text;
		private UISWTStatusEntry	status;
		private BuddyPluginTracker	tracker;

		private TimerEventPeriodic	tick_event;
		
		private TimerEventPeriodic	update_event;

		private CryptoManager	crypto;
		private boolean			crypto_ok;
		private boolean			has_buddies;

		protected
		statusUpdater(
			final UISWTInstance		instance )
		{
			status	= ui_instance.createStatusEntry();
			label 	= ui_instance.createStatusEntry();

			label.setText( label_text = MessageText.getString( "azbuddy.tracker.bbb.status.title" ));
			
			Utils.setTT(label, MessageText.getString( "azbuddy.tracker.bbb.status.title.tooltip" ));

			tracker = plugin.getTracker();

			status.setText( "" );

			status.setImageEnabled( true );

			tracker.addListener( this );

			has_buddies = plugin.getBuddies().size() > 0;

			status.setVisible( tracker.isEnabled() && has_buddies);
			label.setVisible( tracker.isEnabled() && has_buddies);

			for ( UISWTStatusEntry entry: new UISWTStatusEntry[]{ label, status }){

				MenuItem mi =
					plugin.getPluginInterface().getUIManager().getMenuManager().addMenuItem(
							entry.getMenuContext(),
							"azbuddy.view.friends" );

				mi.addListener((menu, target) -> selectClassicTab());
			}


			UISWTStatusEntryListener click_listener =
				new UISWTStatusEntryListener()
			{
					@Override
					public void
					entryClicked(
						UISWTStatusEntry entry )
					{
						try{
							plugin.getPluginInterface().getUIManager().openURL(
									UrlUtils.getRawURL(Wiki.FRIENDS));

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				};

			status.setListener( click_listener );
			label.setListener( click_listener );


			buddyPluginAdapter = new BuddyPluginAdapter() {


				@Override
				public void
				buddyAdded(
						BuddyPluginBuddy	buddy )
				{
					if ( !has_buddies ){

						has_buddies = true;

						updateStatus();
					}
				}

				@Override
				public void
				buddyRemoved(
						BuddyPluginBuddy	buddy )
				{
					has_buddies	= plugin.getBuddies().size() > 0;

					if ( !has_buddies ){

						updateStatus();
					}
				}
			};
			
			plugin.addListener(buddyPluginAdapter);

			crypto = CryptoManagerFactory.getSingleton();

			cryptoManagerKeyListener = new CryptoManagerKeyListener() {
				@Override
				public void
				keyChanged(
						CryptoHandler		handler )
				{
				}

				@Override
				public void
				keyLockStatusChanged(
						CryptoHandler		handler )
				{
					boolean	ok = crypto.getECCHandler().isUnlocked();

					if ( ok != crypto_ok ){

						crypto_ok = ok;

						updateStatus();
					}
				}
			};
			crypto.addKeyListener(cryptoManagerKeyListener);

			crypto_ok = crypto.getECCHandler().isUnlocked();

			updateStatus();
		}

		public void dispose() {
			if (buddyPluginAdapter != null && plugin != null) {
				plugin.removeListener(buddyPluginAdapter);
			}
			if (tracker != null) {
				tracker.removeListener(this);
			}

			if (status != null) {
				status.destroy();
			}

			if (label != null) {
				label.destroy();
			}

			if (cryptoManagerKeyListener != null) {
				CryptoManagerFactory.getSingleton().removeKeyListener(cryptoManagerKeyListener);
			}
		}

		@Override
		public void
		networkStatusChanged(
			BuddyPluginTracker	tracker,
			int					new_status )
		{
			updateStatus();
		}

		protected synchronized void
		updateStatus()
		{
			if ( tracker.isEnabled() && has_buddies ){

				status.setVisible( true );
				label.setVisible( true );

				if ( tick_event == null ){
					
					tick_event = SimpleTimer.addPeriodicEvent(
							"Buddy:GuiUpdater2",
							10*1000,
							new TimerEventPerformer()
							{
								@Override
								public void
								perform(
									TimerEvent event )
								{
									updateStatus();
								}
							});
				}
				
				List<BuddyPluginBuddy> buddies = plugin.getBuddies();
				
				int	num_online 		= 0;
				int num_connected	= 0;
				
				for ( BuddyPluginBuddy b: buddies ){
					
					if ( b.isTransient()){
						
						continue;
					}
					
					if ( b.isOnline( false )){
						num_online++;
					}
					if (b.isConnected()){
						num_connected++;
					}
				}
				
				String new_label_text = MessageText.getString( "azbuddy.tracker.bbb.status.title" ) + " [" + num_connected + "/" + num_online + "]";
				
				if ( !new_label_text.equals( label_text )){
					
					label.setText( new_label_text); 
					
					label_text = new_label_text;
				}
				
				if ( has_buddies && !crypto_ok ){

					status.setImage( iconNLI );

					Utils.setTT(status, MessageText.getString( "azbuddy.tracker.bbb.status.nli" ));

					disableUpdates();

				}else{

					int	network_status = tracker.getNetworkStatus();

					if ( network_status != BuddyPluginTracker.BUDDY_NETWORK_IDLE ){

						long rates = tracker.getNetworkReceiveBytesPerSecond() + tracker.getNetworkSendBytesPerSecond();

						if ( rates <= 0 ){

								// defer switch until we're actually transferring
								// we'll come back through here soon due to the updater timer

							return;
						}
					}

					if ( network_status == BuddyPluginTracker.BUDDY_NETWORK_IDLE ){

						status.setImage( iconIDLE );

						Utils.setTT(status, MessageText.getString( "azbuddy.tracker.bbb.status.idle" ));

						disableUpdates();

					}else if ( network_status == BuddyPluginTracker.BUDDY_NETWORK_INBOUND ){

						status.setImage( iconIN );

						enableUpdates();

					}else if ( network_status == BuddyPluginTracker.BUDDY_NETWORK_OUTBOUND ){

						status.setImage( iconOUT );

						enableUpdates();

					}else{

						status.setImage( iconINOUT );

						enableUpdates();
					}
				}
			}else{

				disableUpdates();

				if ( tick_event != null ){
					
					tick_event.cancel();
					
					tick_event = null;
				}
				
				status.setVisible( false );
				label.setVisible( false );
			}
		}

		protected void
		enableUpdates()
		{
			if ( update_event == null ){

				update_event = SimpleTimer.addPeriodicEvent(
					"Buddy:GuiUpdater",
					2500,
					new TimerEventPerformer()
					{
						@Override
						public void
						perform(
							TimerEvent event )
						{
							synchronized( statusUpdater.this ){

								if ( tracker.isEnabled() && ( crypto_ok || !has_buddies )){

									String	tt;

									int ns = tracker.getNetworkStatus();

									if ( ns == BuddyPluginTracker.BUDDY_NETWORK_IDLE ){

										tt = MessageText.getString( "azbuddy.tracker.bbb.status.idle" );

									}else if ( ns == BuddyPluginTracker.BUDDY_NETWORK_INBOUND ){

										tt = MessageText.getString( "azbuddy.tracker.bbb.status.in" ) + ": " + DisplayFormatters.formatByteCountToKiBEtcPerSec( tracker.getNetworkReceiveBytesPerSecond());

									}else if ( ns == BuddyPluginTracker.BUDDY_NETWORK_OUTBOUND ){

										tt = MessageText.getString( "azbuddy.tracker.bbb.status.out" ) + ": " + DisplayFormatters.formatByteCountToKiBEtcPerSec( tracker.getNetworkSendBytesPerSecond());

									}else{

										tt = MessageText.getString( "azbuddy.tracker.bbb.status.inout" ) + ": " + DisplayFormatters.formatByteCountToKiBEtcPerSec( tracker.getNetworkReceiveBytesPerSecond()) + "/" + DisplayFormatters.formatByteCountToKiBEtcPerSec( tracker.getNetworkSendBytesPerSecond());
									}

									Utils.setTT(status, tt );
								}
							}
						}
					});
			}
		}

		protected void
		disableUpdates()
		{
			if ( update_event != null ){

				update_event.cancel();

				update_event = null;
			}
		}

		@Override
		public void
		enabledStateChanged(
			BuddyPluginTracker 		tracker,
			boolean 				enabled )
		{
			updateStatus();
		}
	}

	private boolean	beta_init_done;

	private static Object	CHAT_LM_KEY		= new Object();

	private HashMap<UISWTView,BetaSubViewHolder> beta_subviews = new HashMap<>();

	private Map<ChatInstance,Integer>	chat_uis = new HashMap<>();

	private UISWTStatusEntry	beta_status;
	private Image				bs_chat_gray;
	private Image				bs_chat_gray_text;
	private Image				bs_chat_swarm_merge;
	private Image				bs_chat_green;
	private Image				bs_chat_red;

	private void
	checkBetaInit()
	{
		if ( plugin.isBetaEnabled() && plugin.getBeta().isAvailable()){

			synchronized( this ){

				if ( beta_init_done ){

					return;
				}

				beta_init_done = true;
			}

			MenuManager menu_manager = plugin.getPluginInterface().getUIManager().getMenuManager();

			MenuItem chat_item = menu_manager.addMenuItem( MenuManager.MENU_DOWNLOAD_CONTEXT, "label.chat" );
			chat_item.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			chat_item.setHeaderCategory(MenuItem.HEADER_SOCIAL);

			final MenuItem mi_chat =
				MenuBuildUtils.addChatMenu(
					menu_manager,
					chat_item,
					new MenuBuildUtils.ChatKeyResolver()
					{
						@Override
						public String getResourceKey(){
							return( "menu.discuss.download" );
						}
						
						@Override
						public String
						getChatKey(
							Object object )
						{
							return( BuddyPluginUtils.getChatKey((Download)object ));
						}
						
						@Override
						public boolean 
						canShareMessage()
						{
							return( true );
						}
						
						@Override
						public void
						shareMessage(
							Object				target,
							ChatInstance		chat )
						{
							if ( target instanceof Download ){
								
								Download dl = (Download)target;
							
								chat.sendMessage( dl );
								
							}else{
								
								Download[] dls = (Download[])target;
								
								for ( Download dl: dls ){
									
									chat.sendMessage( dl );
								}
							}
						}
					});

			addBetaSubviews( true );

			beta_status	= ui_instance.createStatusEntry();

			beta_status.setImageEnabled( true );

			beta_status.setVisible( true );

			updateIdleTT( false );

			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					ImageLoader imageLoader = ImageLoader.getInstance();

					bs_chat_gray		= imageLoader.getImage( "dchat_gray" );
					bs_chat_gray_text 	= imageLoader.getImage( "dchat_gray_text" );
					bs_chat_green 		= imageLoader.getImage( "dchat_green" );
					bs_chat_red 		= imageLoader.getImage( "dchat_red" );
					bs_chat_swarm_merge	= imageLoader.getImage( "dchat_swarm_merge" );
							
					setBetaStatus( bs_chat_gray );

					mi_chat.setGraphic( ui_instance.createGraphic( bs_chat_gray ));
				}
			});

			beta_status.setListener(
				new UISWTStatusEntryListener() {

					@Override
					public void
					entryClicked(
						UISWTStatusEntry entry )
					{
						Set<ChatInstance> current_instances = menu_latest_instances;

							// might be a lot of chats, just pick first 10
						
						int rem = 10;
						
						for ( ChatInstance chat: current_instances ){

							if ( chat.getMessageOutstanding()){

								try{
									openChat( chat.getClone());
									
									rem--;
									
									if ( rem == 0 ){
										
										break;
									}

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					}
				});

			periodicEventMsgCheck = SimpleTimer.addPeriodicEvent(
				"msgcheck",
				30*1000,
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent event)
					{
						List<ChatInstance>	chats = plugin.getBeta().getChats();

						Map<ChatInstance,ChatMessage>	pending = new HashMap<>();
						
						synchronized( pending_msg_map ){

							for ( ChatInstance chat: chats ){

								if ( chat.isInvisible()){

									continue;
								}

								if ( !chat_uis.containsKey( chat )){

									if ( 	chat.isFavourite() ||
											chat.isAutoNotify() ||
											chat.isInteresting()){

										if ( !chat.isStatistics()){

											ChatMessage last_msg = chat.getLastMessageRequiringAttention();

											if ( last_msg != null ){

												ChatMessage last_handled = (ChatMessage)chat.getUserData( CHAT_LM_KEY );

												long last_msg_time = last_msg.getTimeStamp();

												if ( 	last_handled == null ||
														last_msg_time > last_handled.getTimeStamp()){

													chat.setUserData( CHAT_LM_KEY, last_msg );

													pending.put( chat, last_msg );
												}
											}
										}
									}
								}
							}

							if ( !pending.isEmpty()){
								
								betaMessagesPending( pending );
							}
							
							updateIdleTT( false );
						}
					}
				});
		}
	}

	@Override
	public void destroy() {
		// there's probably a lot more to do here
		addBetaSubviews(false);

		try {
			if (pending_msg_event != null) {
				pending_msg_event.cancel();
				pending_msg_event = null;
			}

			if (periodicEventMsgCheck != null) {
				periodicEventMsgCheck.cancel();
				periodicEventMsgCheck = null;
			}

			if (buddyStatusInit != null) {
				buddyStatusInit.cancel();
			}

			if (statusUpdater != null) {
				statusUpdater.dispose();
				statusUpdater = null;
			}

			if (buddyPluginAZ2Listener != null) {
				
				for ( BuddyPluginNetwork pn: plugin.getPluginNetworks()){
					
					pn.getAZ2Handler().removeListener(buddyPluginAZ2Listener);
				}
			}

			if (beta_status != null) {
				beta_status.destroy();
				beta_status = null;
			}
		} catch (Throwable t) {
			Debug.out(t);
		}

		ui_instance.removeViews(UISWTInstance.VIEW_MAIN, FriendsView.VIEW_ID);
	}

	private void
	addBetaSubviews(
		boolean	enable )
	{
		Class[] datasourceTypes = {
			// TODO: Use com.biglybt.pif.tag.Tag?
			Tag.class,
			Download.class,
		};

		if ( enable ){

			taggableLifecycleAdapter = new TaggableLifecycleAdapter() {
				@Override
				public void
				taggableTagged(
						TagType			tag_type,
						Tag				tag,
						Taggable		taggable )
				{
					if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

						DownloadManager dm  = (DownloadManager)taggable;

						for ( BetaSubViewHolder h: beta_subviews.values()){

							h.tagsUpdated( dm );
						}
					}
				}

				@Override
				public void
				taggableUntagged(
						TagType			tag_type,
						Tag				tag,
						Taggable		taggable )
				{
					if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

						DownloadManager dm  = (DownloadManager)taggable;

						for ( BetaSubViewHolder h: beta_subviews.values()){

							h.tagsUpdated( dm );
						}
					}
				}
			};
			TagManagerFactory.getTagManager().addTaggableLifecycleListener(
				Taggable.TT_DOWNLOAD, taggableLifecycleAdapter);

			UISWTViewEventListener listener =
				new UISWTViewEventListener()
				{
					@Override
					public boolean
					eventOccurred(
						UISWTViewEvent event )
					{
						UISWTView 	currentView = event.getView();

						switch (event.getType()) {
							case UISWTViewEvent.TYPE_CREATE:{

								beta_subviews.put(currentView, new BetaSubViewHolder());
								currentView.setDestroyOnDeactivate(false);

								break;
							}
							case UISWTViewEvent.TYPE_INITIALIZE:{

								BetaSubViewHolder subview = beta_subviews.get(currentView);

								if ( subview != null ){

									subview.initialise(event.getView(), (Composite)event.getData());
								}

								break;
							}
							case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:{

								BetaSubViewHolder subview = beta_subviews.get(currentView);

								if ( subview != null ){

									subview.setDataSource( event.getData());
								}

								break;
							}
							case UISWTViewEvent.TYPE_FOCUSGAINED:{

								BetaSubViewHolder subview = beta_subviews.get(currentView);

								if ( subview != null ){

									subview.gotFocus();
								}

								break;
							}
							case UISWTViewEvent.TYPE_FOCUSLOST:{

								BetaSubViewHolder subview = beta_subviews.get(currentView);

								if ( subview != null ){

									subview.lostFocus();
								}

								break;
							}
							case UISWTViewEvent.TYPE_DESTROY:{

								BetaSubViewHolder subview = beta_subviews.remove(currentView);

								if ( subview != null ){

									subview.destroy();
								}

								break;
							}
						}
						return true;
					}
				};

			// Our one listener instance can handle multiple views, so we
			// return same listener for every instantiation
				
			UISWTViewBuilder viewBuilder = 
				ui_instance.createViewBuilder(
					VIEWID_CHAT).setListenerInstantiator(
						new UISWTViewBuilder.UISWTViewEventListenerInstantiator()
						{
							@Override
							public boolean
							supportsMultipleViews()
							{
								return( true );
							}
							
							@Override
							public UISWTViewEventListener createNewInstance(UISWTViewBuilder Builder, UISWTView forView)
									throws Exception{
								
								return listener;
							}
							
							@Override
							public String getUID(){
								return( VIEWID_CHAT );
							}
						});
			
			for (Class datasourceType : datasourceTypes) {
				ui_instance.registerView(datasourceType, viewBuilder);
			}

			TableManager	table_manager = plugin.getPluginInterface().getUIManager().getTableManager();
			
			TableCellRefreshListener	msg_refresh_listener =
					new TableCellRefreshListener()
					{
						@Override
						public void
						refresh(
							TableCell _cell )
						{
							TableCellSWT cell = (TableCellSWT)_cell;

							Download	dl = (Download)cell.getDataSource();

							if ( dl == null ){

								return;
							}
							
							List<ChatInstance> instances = BuddyPluginUtils.peekChatInstances( dl );

							boolean	is_pending 	= false;
							boolean	is_sm		= false;
							
							String msg_history = "";
							
							for ( ChatInstance instance: instances ){
								
								if ( instance.getMessageOutstanding()){
									
									is_pending = true;
								
									List<ChatMessage> messages = instance.getMessages();
									
									for ( ChatMessage msg: messages ){
										
										if ( msg.getFlagOrigin() == BuddyPluginBeta.FLAGS_MSG_ORIGIN_RATINGS ){
											
											if ( msg.getMessage().contains( "Swarm_Merging" )){
												
												is_sm = true;
											}
										}
									}
								}
								
								if ( plugin.getBeta().getFTUXAccepted()){
																		
									Object[] entry = (Object[])dl.getUserData( TT_KEY );
									
									long now = SystemTime.getCurrentTime();
									
									if ( 	entry == null ||
											now - (Long)entry[0] > 30*1000 ){
										
										List<ChatMessage> msgs = instance.getMessages();
										
										int num = msgs.size();
										
										if ( num > 0 ){
																				
											String msgs_str = "";
										
											int added = 0;
											
											for ( int i=msgs.size()-1;i>=0;i--){
												
												ChatMessage msg = msgs.get(i);
												
												if ( msg.isIgnored()){
													
													continue;
													
												}else if ( msg.getMessageType() == ChatMessage.MT_NORMAL ){
													
													String str = msg.getMessage();
															
													if ( str.length() > 50 ){
														
														str = str.substring( 0,  47 ) + "...";
													}
													
													msgs_str = str + "\n" + msgs_str;
													
													added++;
													
													if ( added == 5 ){
														
														break;
													}
												}
											}
											
											if ( !msgs_str.isEmpty()){
												
												if ( !msg_history.isEmpty()){
													
													msg_history += "\n\n";
												}
		
												msg_history += msgs_str;
											}
										}
										
										dl.setUserData( TT_KEY, new Object[]{ now, msg_history });
										
									}else{
										
										msg_history = (String)entry[1];	
									}
								}
							}

							Image	graphic;
							String	tooltip;
							int		sort_order;

							if ( is_pending ){
								
								if ( is_sm ){
									
									graphic 	= bs_chat_swarm_merge;
									tooltip		= MessageText.getString( "label.swarm.merge.available" );
									sort_order	= 2;
									
								}else{
									
									graphic 	= bs_chat_gray_text;
									tooltip		= MessageText.getString( "TableColumn.header.chat.msg.out" );
									sort_order	= 1;

								}
							}else{
								
								graphic 	= null;
								tooltip		= MessageText.getString( "label.no.messages" );
								sort_order	= 0;
							}
							
							if ( !msg_history.isEmpty()){
								
								tooltip += "\n\n---- " + MessageText.getString( "label.history" ) + " ----\n" + msg_history.trim();
							}

							cell.setMarginHeight(0);
							cell.setGraphic( graphic );
							cell.setToolTip( tooltip );

							cell.setSortValue( sort_order );

							cell.setCursorID( graphic==null?SWT.CURSOR_ARROW:SWT.CURSOR_HAND );
						}
					};

				TableCellMouseListener	msg_mouse_listener =
					new TableCellMouseListener()
					{
						@Override
						public void
						cellMouseTrigger(
							TableCellMouseEvent event )
						{					
							if ( event.eventType == TableCellMouseEvent.EVENT_MOUSEUP ){

								TableCell cell = event.cell;

								Download	dl = (Download)cell.getDataSource();

								if ( dl != null ){
									
									List<ChatInstance> instances = BuddyPluginUtils.peekChatInstances( dl );
									
									for ( ChatInstance instance: instances ){
										
										if ( instance.getMessageOutstanding()){
											
											try{
												BuddyPluginUtils.getBetaPlugin().showChat( instance  );
												
											}catch( Throwable e ){
												
												Debug.out( e );
											}
										}
									}
								}
							}else if ( event.eventType == TableCellMouseEvent.EVENT_MOUSEDOUBLECLICK ){
								
								TableCell cell = event.cell;

								Download	dl = (Download)cell.getDataSource();

								if ( dl != null ){
									
									List<ChatInstance> instances = BuddyPluginUtils.peekChatInstances( dl );
									
									for ( ChatInstance instance: instances ){
										
										try{
											BuddyPluginUtils.getBetaPlugin().showChat( instance  );
												
										}catch( Throwable e ){
												
											Debug.out( e );
										}
									}
								}
								
								event.skipCoreFunctionality = true;
							}
						}
					};
			
			
			
			columnMessagePending = new TableColumnCreationListener() {
				@Override
				public void tableColumnCreated(TableColumn result) {
					result.setAlignment(TableColumn.ALIGN_CENTER);
					result.setPosition(TableColumn.POSITION_LAST);
					result.setWidth(32);
					result.setRefreshInterval(TableColumn.INTERVAL_INVALID_ONLY);
					result.setType(TableColumn.TYPE_GRAPHIC);

					result.addCellRefreshListener(msg_refresh_listener);
					result.addCellMouseListener(msg_mouse_listener);
					result.setIconReference("dchat_gray", true);
					
					synchronized( columns ){
						
						columns.add(result);
					}
				}
			};
			
			table_manager.registerColumn( Download.class, "azbuddy.ui.column.msgpending", columnMessagePending );
			
		}else{

			for (Class datasourceType : datasourceTypes) {
				ui_instance.unregisterView(datasourceType, VIEWID_CHAT);
			}

			for ( UISWTView entry: new ArrayList<>(beta_subviews.keySet())){

				entry.closeView();
			}

			if (taggableLifecycleAdapter != null) {
				TagManagerFactory.getTagManager().removeTaggableLifecycleListener(
						Taggable.TT_DOWNLOAD, taggableLifecycleAdapter);
				taggableLifecycleAdapter = null;
			}

			beta_subviews.clear();
			
			if ( columnMessagePending != null) {
				
				TableManager	table_manager = plugin.getPluginInterface().getUIManager().getTableManager();
				
				table_manager.unregisterColumn(Download.class, "azbuddy.ui.column.msgpending");
				
				columnMessagePending = null;
				
				synchronized( columns ){
					
					columns.clear();
				}
			}
		}
	}

	private static Map<String,Object[]>	pending_msg_map = new HashMap<>();
	private static TimerEventPeriodic	pending_msg_event;

	protected void
	registerUI(
		ChatInstance		chat )
	{
		chat.setHasBeenViewed();
		
		synchronized( pending_msg_map ){

			Integer num = chat_uis.get( chat );

			if ( num == null ){

				num = 1;

			}else{

				num++;
			}

			chat_uis.put( chat, num );

			if ( num == 1 ){

				updateIdleTT( false );
			}
		}
	}

	protected void
	unregisterUI(
		ChatInstance		chat )
	{
		synchronized( pending_msg_map ){

			Integer num = chat_uis.get( chat );

			if ( num == null ){

				//eh

			}else{

				num--;
			}

			if ( num == 0 ){

				chat_uis.remove( chat );

				updateIdleTT( false );

			}else{

				chat_uis.put( chat, num );
			}
		}
	}

	private List<ChatInstance>
	sortChats(
		Collection<ChatInstance>	chats )
	{
		List<ChatInstance>	result = new ArrayList<>(chats);

		Collections.sort(
			result,
			new Comparator<ChatInstance>()
			{
				@Override
				public int
				compare(
					ChatInstance o1,
					ChatInstance o2)
				{
					int res = o1.getNetAndKey().compareTo( o2.getNetAndKey());

					return( res );
				}
			});

		return( result );
	}

	private void
	updateIdleTT(
		boolean	known_to_be_idle )
	{
		Iterator<Map.Entry<String,Object[]>> it = pending_msg_map.entrySet().iterator();

		boolean	has_pending = false;

		if ( !known_to_be_idle ){

			while( it.hasNext()){

				Map.Entry<String,Object[]> map_entry = it.next();

				Object[] entry = map_entry.getValue();

				ChatInstance chat = (ChatInstance)entry[2];

				if ( !chat.getDisableNewMsgIndications()){

					has_pending = true;

					break;
				}
			}
		}

		if ( !has_pending ){

			Set<ChatInstance>	instances = new HashSet<>();

			if ( chat_uis.size() > 0 ){

				for ( ChatInstance chat: chat_uis.keySet()){

					instances.add( chat );
				}
			}

			List<ChatInstance>	chats = plugin.getBeta().getChats();

			for ( ChatInstance chat: chats ){

				if ( !chat_uis.containsKey( chat )){

					if ( chat.isFavourite() || chat.isPrivateChat()){

						instances.add( chat );
					}
				}
			}

			String text = MessageText.getString( "label.no.messages" );

			for ( ChatInstance chat: sortChats( instances )){

				text += "\n  " + chat.getShortName();
			}

			if ( beta_status != null ){

				Utils.setTT(beta_status, text );
			}

			buildMenu( instances, false );

			setBetaStatus( bs_chat_gray );
		}
	}

	protected void
	playSound()
	{
		if ( plugin.getBeta().getSoundEnabled()){

			String sound_file = plugin.getBeta().getSoundFile();

			GeneralUtils.playSound( sound_file );
		}
	}

	private void
	betaMessagesPending(
		Map<ChatInstance,ChatMessage>	pending )
	{
		synchronized( columns ){

			for ( TableColumn column : columns ){

				column.invalidateCells();
			}
		}

		for ( Map.Entry<ChatInstance,ChatMessage> entry: pending.entrySet()){
			
			betaMessagePendingSupport( entry.getKey(), null, entry.getValue());
		}
	}
	
	protected void
	betaMessagePending(
		ChatInstance		chat,
		Control				comp_maybe_null,
		ChatMessage			pending_message )
	{
		synchronized( columns ){

			for ( TableColumn column : columns ){

				column.invalidateCells();
			}
		}
		
		betaMessagePendingSupport( chat, comp_maybe_null, pending_message );
	}
	
	private void
	betaMessagePendingSupport(
		ChatInstance		chat,
		Control				comp_maybe_null,
		ChatMessage			pending_message )
	{		
		synchronized( pending_msg_map ){

			String key = chat.getNetAndKey();

			Object[] entry = pending_msg_map.get( key );

			if ( pending_message != null ){

				if ( chat.isOldOutstandingMessage( pending_message )){

					return;
				}

				chat.setMessageOutstanding( pending_message );

				if ( entry == null ){

					entry = new Object[]{ 1, new HashSet<Control>(), chat };

					pending_msg_map.put( key, entry );

				}else{

					entry[0] = ((Integer)entry[0]) + 1;
				}

				HashSet<Control> controls = (HashSet<Control>)entry[1];

				if ( controls.contains( comp_maybe_null )){

					return;
				}

				controls.add( comp_maybe_null );

				if ( pending_msg_event == null ){

					pending_msg_event =
						SimpleTimer.addPeriodicEvent(
							"BPPM",
							2500,
							new TimerEventPerformer()
							{
								private int	tick_count = 0;

								private Set<ChatInstance>	prev_instances = new HashSet<>();

								@Override
								public void
								perform(
									TimerEvent event )
								{
									tick_count++;

									synchronized( pending_msg_map ){

										Set<ChatInstance>			current_instances 	= new HashSet<>();
										Map<ChatInstance,Object>	instance_map 		= new HashMap<>();

										Iterator<Map.Entry<String,Object[]>> it = pending_msg_map.entrySet().iterator();

										boolean	has_new 	= false;
										boolean has_mine	= false;
										
										while( it.hasNext()){

											Map.Entry<String,Object[]> map_entry = it.next();

											Object[] entry = map_entry.getValue();

											ChatInstance chat = (ChatInstance)entry[2];

											if ( chat.isDestroyed()){

												it.remove();

											}else{

												if ( chat.hasUnseenMessageWithNick()){
													
													has_mine = true;
												}
												
												HashSet<Control> comps = ((HashSet<Control>)entry[1]);

												Iterator<Control>	control_it = comps.iterator();

												while( control_it.hasNext()){

													Control c = control_it.next();

													if ( c != null && c.isDisposed()){

														it.remove();
													}
												}

												if ( comps.size() == 0 ){

													it.remove();

												}else{

													if ( !chat.getDisableNewMsgIndications()){

														current_instances.add( chat );

														if ( !prev_instances.contains( chat )){

															has_new = true;
														}

														instance_map.put( chat, entry[0] );
													}
												}
											}
										}


										if ( pending_msg_map.size() == 0 ){

											pending_msg_event.cancel();

											pending_msg_event = null;
										}

										if ( current_instances.size() == 0 ){

											updateIdleTT( true );

										}else{

											String tt_text = "";

											for ( ChatInstance chat: sortChats( current_instances )){

												String short_name = chat.getShortName();

												tt_text += (tt_text.length()==0?"":"\n") + instance_map.get( chat ) + " - " + short_name;
											}

											buildMenu( current_instances, true );

											if ( has_new ){

												playSound();
											}

											Utils.setTT(beta_status, tt_text );

											Image image = has_mine?bs_chat_red:bs_chat_green;
											
											if ( plugin.getBeta().getFlashEnabled() && tick_count%2==0 ){
											
												image = bs_chat_gray_text;
											}
											
											setBetaStatus( image );
										}

										prev_instances = current_instances;
									}
								}
							});
				}
			}else{

				chat.setUserData( CHAT_LM_KEY, chat.getLastMessageRequiringAttention());

				chat.setMessageOutstanding( null );

				if ( entry != null ){

					pending_msg_map.remove( key );

					if ( pending_msg_event == null ){

						Debug.out( "eh?" );
					}
				}
			}
		}
	}

	private void
	setBetaStatus(
		final Image		image )
	{
		beta_status.setImage( image );

		final AllTransfersBar bar = AllTransfersBar.getBarIfOpen(CoreFactory.getSingleton().getGlobalManager());

		if ( bar != null ){

			Utils.execSWTThread(
				new Runnable()
				{
					@Override
					public void
					run()
					{
						bar.setIconImage( image==bs_chat_gray?null:image );
					}
				});
		}
	}

	@Override
	public String
	renderMessage(
		ChatInstance	chat,
		ChatMessage		message )
	{
		List<StyleRange>	ranges = new ArrayList<>();

		String msg = BuddyPluginViewBetaChat.renderMessage(null, chat, message, message.getMessage(), message.getMessageType(), 0, ranges, null, null, null, null );

		StringBuilder new_msg = new StringBuilder();

		int	pos = 0;

		for ( StyleRange range: ranges ){

			Object data = range.data;

			if ( range.underline && data instanceof String ){

				int start 	= range.start;
				int	length	= range.length;

				String link_text 	= msg.substring( start, start+length );
				String link_url		= (String)data;

				if ( start > pos ){

					new_msg.append( msg, pos, start );
				}

				new_msg.append( "<A HREF=\"" + link_url + "\">" + link_text + "</A>" );

				pos = start+length;
			}
		}

		if ( pos == 0 ){

			return( msg );

		}else{

			if ( pos < msg.length()){

				new_msg.append( msg.substring( pos ));
			}

			return( new_msg.toString());
		}
	}

	private List<MenuItem>		menu_items = new ArrayList<>();
	private Set<ChatInstance>	menu_latest_instances = new HashSet<>();

	private void
	buildMenu(
		Set<ChatInstance>		current_instances,
		boolean					is_pending_messages )
	{
		if ( menu_items.size() == 0 || !menu_latest_instances.equals( current_instances )){

			for ( MenuItem mi: menu_items ){

				mi.remove();
			}

			menu_items.clear();

			final MenuManager menu_manager = plugin.getPluginInterface().getUIManager().getMenuManager();

			MenuContext mc = beta_status.getMenuContext();

			MenuItem mi;
			
			if ( is_pending_messages ){
				
				mi = menu_manager.addMenuItem( mc, "![" + MessageText.getString( "TableColumn.header.chat.msg.out" ) + "]!" );

				mi.setEnabled( false );
			
				menu_items.add( mi );
			}
			
			for ( final ChatInstance chat: sortChats( current_instances )){

				String	short_name = chat.getShortName();

				mi = menu_manager.addMenuItem( mc, "!" + Utils.escapeAccelerators(short_name) + "!" );

				mi.addListener(
					new MenuItemListener() {

						@Override
						public void selected(MenuItem menu, Object target) {

							try{
								openChat( chat.getClone());

							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					});

				menu_items.add( mi );
			}
			
			if ( is_pending_messages ){
				
				List<ChatInstance>	chats = plugin.getBeta().getChats();

				Set<ChatInstance>	faves = new HashSet<>();
				
				for ( ChatInstance chat: chats ){

					if ( !current_instances.contains( chat )){

						if ( chat.isFavourite()){

							faves.add( chat );
						}
					}
				}
				
				if ( !faves.isEmpty()){
				
					mi = menu_manager.addMenuItem( mc, "![" + MessageText.getString( "label.favorites" ) + "]!" );

					mi.setEnabled( false );
			
					menu_items.add( mi );

					for ( final ChatInstance chat: sortChats( faves )){

						String	short_name = chat.getShortName();

						mi = menu_manager.addMenuItem( mc, "!" + Utils.escapeAccelerators(short_name) + "!" );

						mi.addListener(
							new MenuItemListener() {

								@Override
								public void selected(MenuItem menu, Object target) {

									try{
										openChat( chat.getClone());

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							});

						menu_items.add( mi );
					}
				}
			}

			boolean	need_sep = true;

				// open all

			if ( current_instances.size() > 1 ){

				mi = menu_manager.addMenuItem( mc, "sep1" );

				need_sep = false;

				mi.setStyle( MenuItem.STYLE_SEPARATOR );

				menu_items.add( mi );

				mi = menu_manager.addMenuItem( mc, "label.open.all" );

				mi.addListener(
					new MenuItemListener() {

						@Override
						public void selected(MenuItem menu, Object target) {

							for ( ChatInstance chat: current_instances ){

								try{
									openChat( chat.getClone());

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					});

				menu_items.add( mi );
			}

			if ( need_sep ){

				mi = menu_manager.addMenuItem( mc, "sep2" );

				mi.setStyle( MenuItem.STYLE_SEPARATOR );

				menu_items.add( mi );
			}

				// create channel

			mi = menu_manager.addMenuItem( mc, "chat.view.create.chat" );

			mi.setStyle( MenuItem.STYLE_MENU );

			menu_items.add( mi );
			
			BuddyUIUtils.createChat( menu_manager, mi, false, new BuddyUIUtils.ChatCreationListener(){
				
				@Override
				public void chatCreated(Object target, String name){
				}
				
				@Override
				public void chatAvailable(Object target,ChatInstance chat){
				}
			});

				// chat overview

			mi = menu_manager.addMenuItem( mc, "!" + MessageText.getString( "chats.view.heading" ) + "...!" );

			mi.addListener(
				new MenuItemListener()
				{
					@Override
					public void
					selected(
						MenuItem			menu,
						Object 				target )
					{
						UIFunctions uif = UIFunctionsManager.getUIFunctions();

						if ( uif != null ){

							uif.getMDI().showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_CHAT);
						}
					}
				});

			menu_items.add( mi );

			mi = menu_manager.addMenuItem( mc, "sep3" );

			mi.setStyle( MenuItem.STYLE_SEPARATOR );

			menu_items.add( mi );

				// options

			mi = menu_manager.addMenuItem( mc, "MainWindow.menu.view.configuration" );

			mi.addListener((menu, target) -> ui_instance.openView(
					UISWTInstance.VIEW_MAIN, FriendsView.VIEW_ID, null));

			menu_items.add( mi );

			menu_latest_instances = current_instances;
		}
	}

	private static AsyncDispatcher	public_dispatcher 	= new AsyncDispatcher();
	private static AsyncDispatcher	anon_dispatcher 	= new AsyncDispatcher();

	private static AtomicInteger	public_done = new AtomicInteger();
	private static AtomicInteger	anon_done 	= new AtomicInteger();


	private static final Object	adapter_key = new Object();

	public DownloadAdapter
	getDownloadAdapter(
		final Download			download )
	{
		synchronized( adapter_key ){

			DownloadAdapter adapter = (DownloadAdapter)download.getUserData( adapter_key );

			if ( adapter == null ){

				adapter =
					new DownloadAdapter()
					{
						@Override
						public DownloadManager
						getCoreDownload()
						{
							return( PluginCoreUtils.unwrap( download ));
						}

						@Override
						public String[]
						getNetworks()
						{
							DownloadManager dm = getCoreDownload();

							if ( dm == null ){

								return( new String[0]);

							}else{

								return( dm.getDownloadState().getNetworks());
							}
						}

						@Override
						public String
						getChatKey()
						{
							return( BuddyPluginUtils.getChatKey( download ));
						}
					};

				download.setUserData( adapter_key,adapter );
			}

			return( adapter );
		}
	}

	@Override
	public View
	buildView(
		Map<String,Object>		properties,
		ViewListener			listener )
	{
		boolean is_swt = Utils.isSWTThread();
		
		AERunnableObject runnable = 
			new AERunnableObject()
			{
				public Object
				runSupport()
				{
					Composite	swt_composite = (Composite)properties.get( BuddyPluginViewInterface.VP_SWT_COMPOSITE );

					try{				
						ChatInstance	chat = (ChatInstance)properties.get( BuddyPluginViewInterface.VP_CHAT );
				
						if ( chat != null ){
				
							final BuddyPluginViewBetaChat view = new BuddyPluginViewBetaChat( BuddyPluginView.this, plugin, chat, swt_composite );
				
							return(
								new View()
								{
									@Override
									public void
									activate()
									{
										view.activate();
									}
				
									@Override
									public void
									handleDrop(
										String drop)
									{
										view.handleExternalDrop( drop );
									}
				
									@Override
									public void
									destroy()
									{
										view.close();
									}
								});
						}else{
							BetaSubViewHolder view = new BetaSubViewHolder();
				
							DownloadAdapter	download = (DownloadAdapter)properties.get( BuddyPluginViewInterface.VP_DOWNLOAD );
				
							view.initialise( swt_composite, download, listener );
				
							return( view );
						}
					}finally{
						
						if ( !is_swt ){
							
							Utils.relayout( swt_composite );
						}
					}
				}
			};
			
		Object result = is_swt?runnable.runSupport():Utils.execSWTThreadWithObject( "chatbuild", runnable, 10*1000 );
		
		return((View)result);
	}

	private class
	BetaSubViewHolder
		implements View
	{
		private int CHAT_DOWNLOAD 		= 0;
		private int CHAT_TRACKERS 		= 1;
		private int CHAT_TAG	 		= 2;
		private int CHAT_GENERAL		= 3;
		private int CHAT_FAVOURITES		= 4;

		private boolean			download_only_mode;

		private ViewListener	view_listener;

		private Composite[]		chat_composites;

		private List<Button>	mode_buttons = new ArrayList<>();

		private Group			middle;

		private	CTabFolder  	tab_folder;
		private CTabItem 		public_item;
		private CTabItem 		anon_item;
		private CTabItem 		neither_item;

		private int				last_build_chat_mode	= -1;
		private int				chat_mode				= CHAT_DOWNLOAD;

		private String				last_selected_network;

		private DownloadAdapter		current_download;
		private String				current_tracker;
		private Tag					current_tag;
		private String				current_general;

		private String			current_favourite_net;
		private String			current_favourite_key;

		private Tag				current_ds_tag;

		private boolean			have_focus;
		private boolean			rebuild_outstanding	= true;
		private Group lhs;

		private
		BetaSubViewHolder()
		{
			checkBetaInit();
		}

		private void
		initialise(
			Composite			parent,
			DownloadAdapter		download,
			ViewListener		listener )
		{
			view_listener 		= listener;
			current_download	= download;
			download_only_mode	= true;

			initialiseSupport( parent );

			String[] nets = current_download.getNetworks();

			if ( nets.length > 0 ){

				String	net_to_activate = nets[0];

				for ( String net: nets ){

					if ( net == AENetworkClassifier.AT_PUBLIC ){

						net_to_activate = net;

						break;
					}
				}

				activateNetwork( net_to_activate, true );
			}
		}

		private void
		initialise(
			UISWTView		view,
			Composite		parent )
		{
			UISWTView parent_view = view.getParentView();

			if ( parent_view != null ){

				Object initial_ds = parent_view.getInitialDataSource();

				if ( initial_ds instanceof Tag ){

					current_ds_tag = (Tag)initial_ds;
				}
			}

			initialiseSupport( parent );
		}

		private void
		initialiseSupport(
			Composite		parent )
		{
			final Composite composite	= parent;

			GridLayout layout = new GridLayout();
			layout.numColumns = download_only_mode?1:3;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.marginTop = 4;
			layout.marginRight = 4;
			composite.setLayout(layout);

			GridData grid_data = new GridData(GridData.FILL_BOTH );
			composite.setLayoutData(grid_data);

			if ( !download_only_mode ){

				// left

				lhs = Utils.createSkinnedGroup( composite, SWT.NULL );
				lhs.setText( MessageText.getString( "label.chat.type" ));
				layout = new GridLayout();
				layout.numColumns = 1;
				layout.horizontalSpacing = 1;
				layout.verticalSpacing = 1;
				lhs.setLayout(layout);
				grid_data = new GridData(GridData.FILL_VERTICAL );
				//grid_data.widthHint = 200;
				lhs.setLayoutData(grid_data);

				Button downloads = new Button( lhs, SWT.TOGGLE );
				downloads.setText( MessageText.getString( "v3.MainWindow.button.download" ));
				downloads.setData( CHAT_DOWNLOAD );

				Button trackers = new Button( lhs, SWT.TOGGLE );
				trackers.setText( MessageText.getString( "label.trackers" ));
				trackers.setData( CHAT_TRACKERS );

				Button tags = new Button( lhs, SWT.TOGGLE );
				tags.setText( MessageText.getString( "label.tags" ));
				tags.setData( CHAT_TAG );

				Button general = new Button( lhs, SWT.TOGGLE );
				general.setText( MessageText.getString( "ConfigView.section.global" ));
				general.setData( CHAT_GENERAL );

				Button favourites = new Button( lhs, SWT.TOGGLE );
				favourites.setText( MessageText.getString( "label.favorites" ));
				favourites.setData( CHAT_FAVOURITES );

				if ( download_only_mode ){

					lhs.setVisible( false );
				}

					// middle

				middle = new Group( composite, SWT.NULL );
				layout = new GridLayout();
				layout.numColumns = 1;
				middle.setLayout(layout);
				grid_data = new GridData(GridData.FILL_VERTICAL );
				grid_data.widthHint = 0;
				middle.setLayoutData(grid_data);

				middle.setText( "" );

				middle.setVisible( false );

				downloads.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						buildChatMode( CHAT_DOWNLOAD, true );
					}});

				trackers.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						buildChatMode( CHAT_TRACKERS, true );
					}});

				tags.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						buildChatMode( CHAT_TAG, true );
					}});

				general.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						buildChatMode( CHAT_GENERAL, true );
					}});

				favourites.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						buildChatMode( CHAT_FAVOURITES, true );
					}});

				downloads.setSelection( true );

				mode_buttons.add( downloads );
				mode_buttons.add( trackers );
				mode_buttons.add( tags );
				mode_buttons.add( general );
				mode_buttons.add( favourites );

				setupButtonGroup( mode_buttons );
			}

				// chat tab area

			tab_folder = new CTabFolder(composite, SWT.LEFT);

			tab_folder.setTabHeight(20);
			grid_data = new GridData(GridData.FILL_BOTH);
			tab_folder.setLayoutData(grid_data);

			// public

			public_item = new CTabItem(tab_folder, SWT.NULL);

			public_item.setText( MessageText.getString( "label.public.chat" ));
			public_item.setData( AENetworkClassifier.AT_PUBLIC );

			Composite public_composite = new Composite( tab_folder, SWT.NULL );

			public_item.setControl( public_composite );

			grid_data = new GridData(GridData.FILL_BOTH );
			public_composite.setLayoutData(grid_data);
			public_composite.setData( "tabitem", public_item );

				// anon

			Composite anon_composite = null;

			{
				anon_item = new CTabItem(tab_folder, SWT.NULL);

				anon_item.setText( MessageText.getString( "label.anon.chat" ));
				anon_item.setData( AENetworkClassifier.AT_I2P );

				anon_composite = new Composite( tab_folder, SWT.NULL );

				anon_item.setControl( anon_composite );

				grid_data = new GridData(GridData.FILL_BOTH );
				anon_composite.setLayoutData(grid_data);
				anon_composite.setData( "tabitem", anon_item );
			}

				// neither

			Composite neither_composite = null;

			{
				neither_item = new CTabItem(tab_folder, SWT.NULL);

				neither_composite = new Composite( tab_folder, SWT.NULL );

				neither_item.setControl( neither_composite );

				grid_data = new GridData(GridData.FILL_BOTH );
				neither_composite.setLayoutData(grid_data);
				neither_composite.setData( "tabitem", neither_item );

				layout = new GridLayout();
				layout.numColumns = 1;
				neither_composite.setLayout(layout);

				Label info = new Label( neither_composite, SWT.NULL );
				info.setText( MessageText.getString( "dchat.select.network" ));
			}

			chat_composites = new Composite[]{ public_composite, anon_composite, neither_composite };

			tab_folder.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					CTabItem item = (CTabItem) e.item;

					String network = (String)item.getData();

					activateNetwork( network );
				}
			});
		}

		private void
		tagsUpdated(
			DownloadManager	dm )
		{
			DownloadAdapter download = current_download;

			if ( download == null ){

				return;
			}

			if ( chat_mode == CHAT_TAG ){

				if ( dm == download.getCoreDownload()){

					Utils.execSWTThread(
						new Runnable()
						{
							@Override
							public void
							run()
							{
								rebuild_outstanding = true;

								activate();
							}
						});
				}
			}
		}

		private void
		setupButtonGroup(
			final List<Button>		buttons )
		{
			for ( final Button b: buttons ){

				b.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						if ( !b.getSelection()){

							b.setSelection( true );
						}
						for ( Button b2: buttons ){

							if ( b2 != b ){
								b2.setSelection( false );
							}
						}
					}});
			}

			Utils.makeButtonsEqualWidth( buttons );
		}

		private void
		selectButtonGroup(
			List<Button>		buttons,
			int					data )
		{
			for ( Button b: buttons ){

				b.setSelection( (Integer)b.getData() == data );
			}
		}

		private void
		selectButtonGroup(
			List<Button>		buttons,
			String				data )
		{
			for ( Button b: buttons ){

				String str = (String)b.getData();

				b.setSelection( str != null && str.endsWith( data ));
			}
		}

		private void
		setChatMode(
			int		mode )
		{
			if ( chat_mode == mode ){

				return;
			}

			chat_mode = mode;

			selectButtonGroup( mode_buttons, mode );
		}

		private void
		buildChatMode(
			int				mode,
			boolean			activate )
		{
			DownloadAdapter	download = current_download;

			chat_mode = mode;

			if ( mode == CHAT_GENERAL && last_build_chat_mode == mode ){

					// doesn't change so no rebuild required
			}else{

				if ( !download_only_mode ){

					for ( Control c: middle.getChildren()){

						c.dispose();
					}

					if ( mode == CHAT_DOWNLOAD ||(( mode == CHAT_TRACKERS || mode == CHAT_TAG ) &&  download == null && current_ds_tag == null )){

						middle.setVisible( false );
						middle.setText( "" );

						GridData grid_data = new GridData(GridData.FILL_VERTICAL );
						grid_data.widthHint = 0;
						middle.setLayoutData(grid_data);

					}else if ( mode == CHAT_TRACKERS ){

						middle.setVisible( true );
						middle.setText( MessageText.getString( "label.tracker.selection" ));

						Set<String> trackers = new HashSet<>();

						if ( download != null ){

							DownloadManager core_dm = download.getCoreDownload();

							if ( core_dm != null ){

								trackers = TorrentUtils.getUniqueTrackerHosts( core_dm.getTorrent());
							}
						}

						GridLayout layout = new GridLayout();
						layout.horizontalSpacing = 1;
						layout.verticalSpacing = 1;

						layout.numColumns = 1;
						middle.setLayout(layout);
						GridData grid_data = new GridData(GridData.FILL_VERTICAL );
						middle.setLayoutData(grid_data);

						Set<String>	reduced_trackers = new HashSet<>();

						for ( String tracker: trackers ){

							tracker = DNSUtils.getInterestingHostSuffix( tracker );

							if ( tracker != null ){

								reduced_trackers.add( tracker );
							}
						}

						int	num_trackers = reduced_trackers.size();

						if ( num_trackers == 0 ){

							current_tracker = null;
							Label label = new Label( middle, SWT.NULL );
							label.setText( MessageText.getString( "label.none.assigned" ));
							label.setEnabled( false );

						}else{

							Composite	tracker_area;

							if ( num_trackers > 4 ){

								tracker_area = createScrolledComposite( middle );

							}else{

								tracker_area = middle;
							}

							List<String>sorted_trackers = new ArrayList<>( reduced_trackers );

							Collections.sort( sorted_trackers );

							if ( !sorted_trackers.contains( current_tracker )){

								current_tracker = sorted_trackers.get(0);
							}

							final List<Button>	buttons = new ArrayList<>();

							for ( final String tracker: sorted_trackers ){

								Button button = new Button( tracker_area, SWT.TOGGLE );

								button.setText( tracker );
								button.setData( tracker );

								button.addSelectionListener(new SelectionAdapter() {
									@Override
									public void widgetSelected(SelectionEvent e) {
										current_tracker = tracker;
										activate();
									}});
								buttons.add( button );
							}

							setupButtonGroup( buttons );

							selectButtonGroup( buttons, current_tracker );
						}
					}else if ( mode == CHAT_TAG ){

						lhs.setVisible(download != null);
						GridData grid_data = new GridData(GridData.FILL_VERTICAL );
						if (download == null) {
							grid_data.exclude = true;
						}
						lhs.setLayoutData(grid_data);


						middle.setVisible( true );
						middle.setText( MessageText.getString( "label.tag.selection" ));

						List<Tag> tags;

						if ( download == null ){

							tags = new ArrayList<>();

						}else{

							DownloadManager core_dm = download.getCoreDownload();

							if ( core_dm == null ){

								tags = new ArrayList<>();

							}else{

								tags = TagManagerFactory.getTagManager().getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, core_dm );
							}
						}

						if ( current_ds_tag != null && !tags.contains( current_ds_tag )){

							tags.add( current_ds_tag );
						}

						GridLayout layout = new GridLayout();
						layout.horizontalSpacing = 1;
						layout.verticalSpacing = 1;

						layout.numColumns = 1;
						middle.setLayout(layout);
						grid_data = new GridData(GridData.FILL_VERTICAL );
						middle.setLayoutData(grid_data);

						int	num_tags = tags.size();

						if ( num_tags == 0 ){

							current_tag = null;
							Label label = new Label( middle, SWT.NULL );
							label.setText( MessageText.getString( "label.none.assigned" ));
							label.setEnabled( false );

						}else{

							Composite	tag_area;

							if ( num_tags > 4 ){

								tag_area = createScrolledComposite( middle );

							}else{

								tag_area = middle;
							}

							tags = TagUtils.sortTags( tags );

							if ( !tags.contains( current_tag )){

								current_tag = tags.get(0);
							}

							final List<Button>	buttons = new ArrayList<>();

							for ( final Tag tag: tags ){

								Button button = new Button( tag_area, SWT.TOGGLE );

								String tag_name = tag.getTagName( true );

								button.setText( tag_name );
								button.setData( tag_name );

								button.addSelectionListener(new SelectionAdapter() {
									@Override
									public void widgetSelected(SelectionEvent e) {
										current_tag = tag;
										activate();
									}});
								buttons.add( button );
							}

							setupButtonGroup( buttons );

							selectButtonGroup( buttons, current_tag.getTagName( true ));
						}
					}else if ( mode == CHAT_GENERAL ){

						middle.setVisible( true );
						middle.setText( MessageText.getString( "azbuddy.dchat.general.chats" ));

						GridLayout layout = new GridLayout();
						layout.horizontalSpacing = 1;
						layout.verticalSpacing = 1;

						layout.numColumns = 1;
						middle.setLayout(layout);
						GridData grid_data = new GridData(GridData.FILL_VERTICAL );
						middle.setLayoutData(grid_data);

						final List<Button>	buttons = new ArrayList<>();

						String[][] general_data = {
								{ "label.help", 		BuddyPluginBeta.COMMUNITY_CHAT_KEY },
								{ "label.announce", 	BuddyPluginBeta.ANNOUNCE_CHAT_KEY },
								{ "label.beta",			BuddyPluginBeta.BETA_CHAT_KEY },
						};

						for ( String[] entry: general_data ){

							final	String key = entry[1];

							if ( key == BuddyPluginBeta.BETA_CHAT_KEY && !Constants.isCVSVersion()){

								continue;
							}

							Button button = new Button( middle, SWT.TOGGLE );

							button.setText( MessageText.getString( entry[0] ) );
							button.setData( key );

							button.addSelectionListener(new SelectionAdapter() {
								@Override
								public void widgetSelected(SelectionEvent e) {
									current_general = key;
									activate();
								}});

							buttons.add( button );
						}

						setupButtonGroup( buttons );

						if ( current_general != null ){

							selectButtonGroup( buttons, current_general );
						}
					}else{

						middle.setVisible( true );
						middle.setText( MessageText.getString( "azbuddy.dchat.fave.chats" ));

						GridLayout layout = new GridLayout();
						layout.horizontalSpacing 	= 1;
						layout.verticalSpacing 		= 1;

						layout.numColumns = 1;
						middle.setLayout(layout);
						GridData grid_data = new GridData(GridData.FILL_VERTICAL );
						middle.setLayoutData(grid_data);

						List<String[]>	list = plugin.getBeta().getFavourites();

						int	num_faves = list.size();

						if ( num_faves == 0 ){

							Label label = new Label( middle, SWT.NULL );
							label.setText( MessageText.getString( "label.none.assigned" ));
							label.setEnabled( false );

						}else{

							Composite	fave_area;

							if ( num_faves > 4 ){

								fave_area = createScrolledComposite( middle );

							}else{

								fave_area = middle;
							}

							final List<Button>	buttons = new ArrayList<>();

							Collections.sort(
								list,
								new Comparator<String[]>()
								{
									Comparator<String> c = new FormattersImpl().getAlphanumericComparator( true );

									@Override
									public int compare(String[] o1, String[] o2) {

										int result = o1[0].compareTo( o2[0] );

										if ( result == 0 ){

											result = c.compare( o1[1], o2[1] );
										}

										return( result );
									}
								});

							for ( String[] entry: list ){

								final	String net = entry[0];
								final	String key = entry[1];

								Button button = new Button( fave_area, SWT.TOGGLE );

								String	short_name = "(" + MessageText.getString( net==AENetworkClassifier.AT_PUBLIC?"label.public.short":"label.anon.short" ) + ")";

								short_name += " " + key;

								if ( short_name.length() > 30 ){

									short_name = short_name.substring( 0, 30 ) + "...";
								}

								String	long_name = "(" + MessageText.getString( net==AENetworkClassifier.AT_PUBLIC?"label.public":"label.anon" ) + ")";

								long_name += " " + key;

								button.setText( short_name );
								button.setAlignment( SWT.LEFT );
								Utils.setTT(button, long_name );

								button.setData( net + ":" + key );

								button.addSelectionListener(new SelectionAdapter() {
									@Override
									public void widgetSelected(SelectionEvent e) {
										current_favourite_net = net;
										current_favourite_key = key;
										activate();
									}});

								buttons.add( button );
							}

							setupButtonGroup( buttons );

							if ( current_favourite_key != null ){

								selectButtonGroup( buttons, current_favourite_net + ":" + current_favourite_key );
							}
						}
					}

					middle.getParent().layout( true, true );
				}

				last_build_chat_mode	= mode;
			}

			if ( activate ){

				activate();
			}
		}

		private Composite
		createScrolledComposite(
			final Composite		parent )
		{
			final ScrolledComposite scrollable =
				new ScrolledComposite(parent, SWT.V_SCROLL)
				{
						// this code required to show/hide scroll bar when visible or not

				    private final Point 	bar_size;
				    private int				x_adjust;
				    private boolean			first_time	= true;
				    private boolean			hacking;

				    {
				        Composite composite = new Composite(parent, SWT.H_SCROLL | SWT.V_SCROLL);

				        composite.setSize(1, 1);

				        bar_size = composite.computeSize(0, 0);

				        composite.dispose();
				    }

				    @Override
				    public Point
				    computeSize(
				    	int 		wHint,
				    	int 		hHint,
				    	boolean 	changed )
				    {
				        Point point = super.computeSize(wHint, hHint, changed);

				        if ( !hacking ){

					        final boolean was_visible = getVerticalBar().isVisible();

					        Utils.execSWTThreadLater(
					        	0,
					        	new Runnable()
					        	{
					        		@Override
							        public void
					        		run()
					        		{
					        			if ( isDisposed()){

					        				return;
					        			}

					        			boolean is_visible = getVerticalBar().isVisible();

					        			if ( first_time || was_visible != is_visible ){

					        				x_adjust = is_visible?0:-bar_size.x;

					        				try{
					        					hacking = true;

					        					parent.getParent().layout( true, true );

					        				}finally{

					        					hacking = false;
					        				}
					        			}
					        		}
						        });
				        }

				        point.x += x_adjust;

				        return point;
				    }
				};

			scrollable.setLayoutData(new GridData(GridData.FILL_VERTICAL ));

			final Composite scrollChild = new Composite( scrollable, SWT.NONE );

			GridLayout gLayoutChild = new GridLayout();
			gLayoutChild.numColumns = 1;

			gLayoutChild.horizontalSpacing 	= 1;
			gLayoutChild.verticalSpacing 	= 1;
			gLayoutChild.marginWidth 		= 0;
			gLayoutChild.marginHeight		= 0;
			scrollChild.setLayout(gLayoutChild);
			scrollChild.setLayoutData(new GridData(GridData.FILL_VERTICAL ));

			scrollable.setContent(scrollChild);
			scrollable.setExpandVertical(true);
			scrollable.setExpandHorizontal(true);
			scrollable.setAlwaysShowScrollBars( false );

			scrollable.setMinSize(scrollChild.computeSize(SWT.DEFAULT, SWT.DEFAULT));

			scrollable.addControlListener(new ControlAdapter() {
				@Override
				public void controlResized(ControlEvent e) {
					Rectangle r = scrollable.getClientArea();
					scrollable.setMinSize(scrollChild.computeSize(SWT.DEFAULT, SWT.DEFAULT));
				}
			});

			return( scrollChild );
		}

		@Override
		public void
		activate()
		{
			if ( rebuild_outstanding ){

				rebuild_outstanding = false;

				if ( current_download == null ){

						setChatMode( current_ds_tag==null?CHAT_GENERAL:CHAT_TAG );
				}

				buildChatMode( chat_mode, false );
			}

			activateNetwork( null );
		}

		@Override
		public void
		handleDrop(
			String drop)
		{
			Debug.out( "not supported" );
		}

		private void
		activateNetwork(
			String		network  )
		{
			if ( network != null ){

					// explicit network is only set when we're switching public/anon tabs so
					// we use it directly and don't need to reselect the tab coz we're on it

				last_selected_network = network;

				activateNetwork( network, false );

			}else{
					// network == null -> select most appropriate one

				if ( chat_mode == CHAT_FAVOURITES && current_favourite_net != null ){

					activateNetwork( current_favourite_net, true );

				}else{

					DownloadAdapter	download 	= current_download;

					if ( download == null ){

							// no current download to guide us

						activateNetwork( null, true );

					}else{

						String[] nets = download.getNetworks();

						boolean	pub 	= false;
						boolean	anon	= false;

						for ( String net: nets ){

							if ( net == AENetworkClassifier.AT_PUBLIC ){

								pub = true;

							}else if ( net == AENetworkClassifier.AT_I2P ){

								anon = true;
							}
						}

						if ( pub && anon ){
							activateNetwork( AENetworkClassifier.AT_PUBLIC, true );
							activateNetwork( AENetworkClassifier.AT_I2P, false );	// warm it up
						}else if ( pub ){
							activateNetwork( AENetworkClassifier.AT_PUBLIC, true );
						}else if ( anon ){
							activateNetwork( AENetworkClassifier.AT_I2P, true );
						}
					}
				}
			}
		}

		private void
		activateNetwork(
			String			network,
			boolean			select_tab )
		{
			String key;

			if ( chat_mode == CHAT_DOWNLOAD ){

				DownloadAdapter	download 	= current_download;

				if ( download == null ){

					key = null;

				}else{

					key = download.getChatKey();
				}
			}else if ( chat_mode == CHAT_TRACKERS ){

				String tracker = current_tracker;

				if ( tracker == null ){

					key = null;

				}else{

					key = "Tracker: " + tracker;
				}
			}else if ( chat_mode == CHAT_TAG ){

				Tag	tag = current_tag;

				if ( tag == null ){

					key = null;

				}else{

					key = TagUIUtils.getChatKey( tag );
				}
			}else if ( chat_mode == CHAT_GENERAL ){

				key = current_general;

			}else{

				key	= current_favourite_key;
			}

			activateChat( network, key, select_tab );
		}

		private void
		activateChat(
			String				_network,
			final String		key,
			boolean				select_tab )
		{
			if ( _network == null ){

				if ( last_selected_network != null ){

					_network = last_selected_network;

				}else{
					if ( select_tab ){

						tab_folder.setSelection( neither_item );

						neither_item.setText( MessageText.getString( "GeneralView.section.info" ));
					}

					return;
				}
			}

			final String network = _network;

			neither_item.setText( "" );

			final Composite chat_composite = chat_composites[network==AENetworkClassifier.AT_PUBLIC?0:1];

			if ( chat_composite == null ){

				return;
			}

			final String comp_key = network + ":" + key;

			String existing_comp_key = (String)chat_composite.getData();

			if ( existing_comp_key == null || !existing_comp_key.equals( comp_key )){

				for ( Control c: chat_composite.getChildren()){

					c.dispose();
				}

				if ( key == null ){

					chat_composite.setData( comp_key );

					GridLayout layout = new GridLayout();
					layout.numColumns = 1;
					chat_composite.setLayout(layout);

					Label label = new Label( chat_composite, SWT.NULL );

					label.setText( MessageText.getString( "azbuddy.os_not_avail" ));
					
					GridData grid_data = new GridData(GridData.FILL_BOTH );
					
					label.setLayoutData(grid_data);
					
					chat_composite.layout( true, true );
					
					return;
				}

				AsyncDispatcher disp 		= network==AENetworkClassifier.AT_PUBLIC?public_dispatcher:anon_dispatcher;

				final AtomicInteger	counter 	= network==AENetworkClassifier.AT_PUBLIC?public_done:anon_done;

				disp.dispatch(
					new AERunnable(){
						@Override
						public void
						runSupport()
						{
							if ( chat_composite.isDisposed()){

								return;
							}

							try{
								final ChatInstance chat = (network == AENetworkClassifier.AT_I2P && !plugin.getBeta().isI2PAvailable())?null:plugin.getBeta().getChat( network, key );

								counter.incrementAndGet();

								Utils.execSWTThread(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											if ( chat_composite.isDisposed()){

												return;
											}

											for ( Control c: chat_composite.getChildren()){

												c.dispose();
											}

											if ( chat == null ){

												GridLayout layout = new GridLayout();
												//layout.horizontalSpacing = 1;
												//layout.verticalSpacing = 1;
												layout.numColumns = 3;

												chat_composite.setLayout( layout );

												Label label = new Label( chat_composite, SWT.NULL );

												label.setText( MessageText.getString( "azbuddy.dchat.not.installed" ));

												final Button install_button = new Button( chat_composite, SWT.NULL );

												install_button.setText( MessageText.getString( "UpdateWindow.columns.install" ));

												install_button.addSelectionListener(
													new SelectionAdapter() {

														@Override
														public void widgetSelected(SelectionEvent e) {

															final boolean[] result = { false };

															I2PHelpers.installI2PHelper(
																null, result,
																new Runnable() {

																	private long				start = SystemTime.getMonotonousTime();
																	private TimerEventPeriodic 	timer;

																	@Override
																	public void
																	run()
																	{
																		if ( result[0] ){

																			Utils.execSWTThread(
																					new Runnable()
																					{
																						@Override
																						public void
																						run()
																						{
																							install_button.setEnabled( false );
																						}
																					});

																			timer =
																				SimpleTimer.addPeriodicEvent(
																					"install-waiter",
																					1000,
																					new TimerEventPerformer()
																					{
																						@Override
																						public void
																						perform(
																							TimerEvent event)
																						{
																							if ( plugin.getBeta().isI2PAvailable()){

																								timer.cancel();

																								Utils.execSWTThread(
																									new Runnable()
																									{
																										@Override
																										public void
																										run()
																										{
																											String existing_comp_key = (String)chat_composite.getData();

																											if ( existing_comp_key == null || existing_comp_key.equals( comp_key )){

																												counter.set( 0 );

																												chat_composite.setData( null );

																												activateChat( network, key, true );
																											}
																										}
																									});
																							}else{

																								if ( SystemTime.getMonotonousTime() - start > 5*60*1000 ){

																									timer.cancel();
																								}
																							}

																						}
																					});

																		}
																	}
																});
														}
													});

												List<Button> buttons = new ArrayList<>();

												buttons.add( install_button );

												Utils.makeButtonsEqualWidth( buttons );

												chat_composite.layout( true, true );

											}else{

												BuddyPluginViewBetaChat view = new BuddyPluginViewBetaChat( BuddyPluginView.this, plugin, chat, chat_composite );

												Utils.setTT(((CTabItem)chat_composite.getData("tabitem")),key );

												chat_composite.layout( true, true );

												chat_composite.setData( comp_key );

												chat_composite.setData( "viewitem", view );

												if ( view_listener != null ){

													try{
														view_listener.chatActivated(chat);

													}catch( Throwable e){

														Debug.out( e );
													}
												}
											}
										}
									});

							}catch( Throwable e ){

								e.printStackTrace();
							}

						}
					});

				if ( counter.get() == 0 ){

					GridLayout layout = new GridLayout();
					layout.numColumns = 1;
					chat_composite.setLayout(layout);

					Label label = new Label( chat_composite, SWT.NULL );

					label.setText( MessageText.getString( "v3.MainWindow.view.wait" ));
					GridData grid_data = new GridData(GridData.FILL_BOTH );
					label.setLayoutData(grid_data);

				}

				chat_composite.layout( true, true );

			}else{

				BuddyPluginViewBetaChat existing_chat =  (BuddyPluginViewBetaChat)chat_composite.getData( "viewitem" );

				if ( existing_chat != null ){

					existing_chat.activate();
				}
			}

			if ( select_tab ){

				tab_folder.setSelection( network==AENetworkClassifier.AT_PUBLIC?public_item:anon_item );
			}
		}

		private void
		setDataSource(
			Object		obj )
		{
			Download 			dl 		= null;
			DiskManagerFileInfo	dl_file = null;
			Tag					tag		= null;

			if ( obj instanceof Object[]){

				Object[] ds = (Object[])obj;

				if ( ds.length > 0 ){

					if ( ds[0] instanceof Download ){

						dl = (Download)ds[0];

					}else if ( ds[0] instanceof DiskManagerFileInfo ){

						dl_file = (DiskManagerFileInfo)ds[0];

					}else if ( ds[0] instanceof Tag ) {

						tag = (Tag) ds[0];
					}
				}
			}else{

				if ( obj instanceof Download ){

					dl = (Download)obj;

				}else if ( obj instanceof DiskManagerFileInfo ){

					dl_file = (DiskManagerFileInfo)obj;

				}else if ( obj instanceof Tag ){

					tag = (Tag)obj;
				}
			}

			if ( dl_file != null ){

				try{
					dl = dl_file.getDownload();

				}catch( Throwable e ){
				}
			}

			synchronized( this ){

				if ( dl == current_download && tag == current_ds_tag ){

					return;
				}

				last_selected_network = null;

				current_download 	= dl==null?null:getDownloadAdapter( dl );
				current_ds_tag		= tag;

				if (current_download != null) {
					setChatMode(CHAT_DOWNLOAD);
				}

				rebuild_outstanding = true;

				if ( have_focus ){

					activate();
				}
			}
		}

		private void
		gotFocus()
		{
			synchronized( this ){

				have_focus = true;

				activate();
			}
		}

		private void
		lostFocus()
		{
			synchronized( this ){

				have_focus = false;
			}
		}

		@Override
		public void
		destroy()
		{
			//System.out.println( "Destroyed" );
		}
	}
}
