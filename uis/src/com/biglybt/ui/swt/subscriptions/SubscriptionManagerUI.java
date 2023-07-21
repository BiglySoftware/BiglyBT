/*
 * Created on Jul 29, 2008
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


package com.biglybt.ui.swt.subscriptions;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;

import com.biglybt.activities.LocalActivityManager;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.metasearch.impl.web.rss.RSSEngine;
import com.biglybt.core.subs.*;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.*;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctions.TagReturner;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pif.*;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.utils.CategoryUIUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;

import com.biglybt.pif.PluginConfigListener;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.ui.*;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.menus.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pif.utils.Utilities;

public class
SubscriptionManagerUI
{
	private static final String CONFIG_SECTION_ID 	= "Subscriptions";
	public static final Object	SUB_ENTRYINFO_KEY 	= new Object();
	public static final Object	SUB_EDIT_MODE_KEY 	= new Object();

	private static final String ALERT_IMAGE_ID	= "image.sidebar.vitality.alert";
	private static final String INFO_IMAGE_ID	= "image.sidebar.vitality.info";


	static final String EDIT_MODE_MARKER	= "&editMode=1";

	private Graphic	icon_rss_big;
	private Graphic	icon_rss_small;
	private Graphic	icon_rss_all_add_small;
	private Graphic	icon_rss_all_add_big;
	private Graphic	icon_rss_some_add_small;
	private Graphic	icon_rss_some_add_big;
	private final List<Image>	icon_list	= new ArrayList<>();

	private final List<TableColumn> columns = new ArrayList<>();
	protected UISWTInstance swt;
	private UIManager ui_manager;
	private PluginInterface default_pi;
	private MdiEntry mdiEntryOverview;

	private boolean	sidebar_setup_done;

	private Map<String,MdiEntry>		parent_views = new TreeMap<>( new FormattersImpl().getAlphanumericComparator( true ));
	private SubscriptionManagerListener subman_listener_quick;
	private SubscriptionManagerListener subman_listener_delayed;
	private TableColumnCreationListener columnCreationSubs;
	private TableColumnCreationListener columnCreationSubsLink;
	private PluginConfigListener pluginConfigListener;
	private BasicPluginConfigModel configModel;


	public
	SubscriptionManagerUI()
	{
		default_pi = PluginInitializer.getDefaultInterface();

		final TableManager	table_manager = default_pi.getUIManager().getTableManager();

		Utils.getOffOfSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				SubscriptionManagerFactory.getSingleton();
			}
		});

		if ( Constants.isCVSVersion()){

				// check assoc

			{
				final TableContextMenuItem menu_item_itorrents =
					table_manager.addContextMenuItem(TableManager.TABLE_MYTORRENTS_INCOMPLETE, "azsubs.contextmenu.lookupassoc");
				final TableContextMenuItem menu_item_ctorrents 	=
					table_manager.addContextMenuItem(TableManager.TABLE_MYTORRENTS_COMPLETE, "azsubs.contextmenu.lookupassoc");

				menu_item_itorrents.setStyle(TableContextMenuItem.STYLE_PUSH);
				menu_item_itorrents.setHeaderCategory(MenuItem.HEADER_SOCIAL);
				menu_item_itorrents.setDisposeWithUIDetach(UIInstance.UIT_SWT);
				menu_item_ctorrents.setStyle(TableContextMenuItem.STYLE_PUSH);
				menu_item_ctorrents.setHeaderCategory(MenuItem.HEADER_SOCIAL);
				menu_item_ctorrents.setDisposeWithUIDetach(UIInstance.UIT_SWT);

				MenuItemListener listener =
					new MenuItemListener()
					{
						@Override
						public void
						selected(
							MenuItem 	menu,
							Object 		target)
						{
							TableRow[]	rows = (TableRow[])target;

							if ( rows.length > 0 ){

								Download download = (Download)rows[0].getDataSource();

								String[] networks = PluginCoreUtils.unwrap( download ).getDownloadState().getListAttribute( DownloadManagerState.AT_NETWORKS );

								new SubscriptionListWindow(
									UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell(),
									download.getName(),
									download.getTorrentHash(),
									networks,
									false);
							}
							/*
							for (int i=0;i<rows.length;i++){

								Download download = (Download)rows[i].getDataSource();

								Torrent t = download.getTorrent();

								if ( t != null ){

									try{
										lookupAssociations(
											t.getHash(),
											new SubscriptionLookupListener()
											{
												public void
												found(
													byte[]					hash,
													Subscription			subscription )
												{
													log( "    lookup: found " + ByteFormatter.encodeString( hash ) + " -> " + subscription.getName());
												}

												public void
												complete(
													byte[]					hash,
													Subscription[]			subscriptions )
												{
													log( "    lookup: complete " + ByteFormatter.encodeString( hash ) + " -> " +subscriptions.length );

												}

												public void
												failed(
													byte[]					hash,
													SubscriptionException	error )
												{
													log( "    lookup: failed", error );
												}
											});

									}catch( Throwable e ){

										log( "Lookup failed", e );
									}
								}
							}*/
						}
					};

				menu_item_itorrents.addMultiListener( listener );
				menu_item_ctorrents.addMultiListener( listener );
			}

				// make assoc - CVS only as for testing purposes

			if ( false ){

				final TableContextMenuItem menu_item_itorrents =
					table_manager.addContextMenuItem(TableManager.TABLE_MYTORRENTS_INCOMPLETE, "azsubs.contextmenu.addassoc");
				final TableContextMenuItem menu_item_ctorrents 	=
					table_manager.addContextMenuItem(TableManager.TABLE_MYTORRENTS_COMPLETE, "azsubs.contextmenu.addassoc");

				menu_item_itorrents.setStyle(TableContextMenuItem.STYLE_MENU);
				menu_item_itorrents.setDisposeWithUIDetach(UIInstance.UIT_SWT);
				menu_item_ctorrents.setStyle(TableContextMenuItem.STYLE_MENU);
				menu_item_ctorrents.setDisposeWithUIDetach(UIInstance.UIT_SWT);

				MenuItemFillListener	menu_fill_listener =
					new MenuItemFillListener()
					{
						@Override
						public void
						menuWillBeShown(
							MenuItem	menu,
							Object		target )
						{
							SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

							if ( subs_man == null ){

								return;
							}

							TableRow[]	rows;

							if ( target instanceof TableRow[] ){

								rows = (TableRow[])target;

							}else{

								rows = new TableRow[]{ (TableRow)target };
							}

							final List<byte[]>	hashes = new ArrayList<>();

							for (int i=0;i<rows.length;i++){

								Download	download = (Download)rows[i].getDataSource();

								if ( download != null ){

									Torrent torrent = download.getTorrent();

									if ( torrent != null ){

										hashes.add( torrent.getHash());
									}
								}
							}

							menu.removeAllChildItems();

							boolean enabled = hashes.size() > 0;

							if ( enabled ){

								Subscription[] subs = subs_man.getSubscriptions( true );

								boolean	incomplete = ((TableContextMenuItem)menu).getTableID() == TableManager.TABLE_MYTORRENTS_INCOMPLETE;

								TableContextMenuItem parent = incomplete?menu_item_itorrents:menu_item_ctorrents;

								for (int i=0;i<subs.length;i++){

									final Subscription	sub = subs[i];

									TableContextMenuItem item =
										table_manager.addContextMenuItem(
											parent,
											"!" + sub.getName() + "!");

									item.addListener(
										new MenuItemListener()
										{
											@Override
											public void
											selected(
												MenuItem 	menu,
												Object 		target )
											{
												for (int i=0;i<hashes.size();i++){

													sub.addAssociation( hashes.get(i));
												}
											}
										});
								}
							}

							menu.setEnabled( enabled );
						}
					};

				menu_item_itorrents.addFillListener( menu_fill_listener );
				menu_item_ctorrents.addFillListener( menu_fill_listener );
			}
		}

		createSubsColumns( table_manager );

		ui_manager = default_pi.getUIManager();

		ui_manager.addUIListener(
				new UIManagerListener()
				{
					@Override
					public void
					UIAttached(
						UIInstance		instance )
					{
						if (!( instance instanceof UISWTInstance )){
							return;

						}

						swt = (UISWTInstance)instance;

						uiQuickInit();

						Utilities utilities = default_pi.getUtilities();

						final DelayedTask dt = utilities.createDelayedTask(new Runnable()
						{
							@Override
							public void
							run()
							{
								Utils.execSWTThread(new AERunnable() {

									@Override
									public void
									runSupport()
									{
										delayedInit();
									}
								});
							}
						});

						dt.queue();

					}

					@Override
					public void UIDetached(UIInstance instance) {
						if (!( instance instanceof UISWTInstance )){
							return;
						}

						uiDestroy();
						ui_manager.removeUIListener(this);
					}
				});
	}

	void uiQuickInit() {

		final MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();

		if ( mdi == null ){

				// closing down

			return;
		}

		icon_rss_small			= loadGraphic( swt, "subscription_icon.png" );
		icon_rss_big			= icon_rss_small;

		icon_rss_all_add_small	= loadGraphic( swt, "subscription_icon_inactive.png" );
		icon_rss_all_add_big	= icon_rss_all_add_small;

		icon_rss_some_add_small	= icon_rss_all_add_small;
		icon_rss_some_add_big	= icon_rss_some_add_small;

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_SUBSCRIPTIONS,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						setupSideBar( swt );
						return mdiEntryOverview;
					}
				});

		mdi.registerEntry("Subscription_.*", new MdiEntryCreationListener2() {
			// @see MdiEntryCreationListener2#createMDiEntry(MultipleDocumentInterface, java.lang.String, java.lang.Object, java.util.Map)
			@Override
			public MdiEntry createMDiEntry(MultipleDocumentInterface mdi, String id,
			                               Object datasource, Map<?, ?> params) {
				Subscription sub = null;
				if (datasource instanceof Subscription) {
					sub = (Subscription) datasource;
				} else if (id.length() > 13) {
					String publicKey = id.substring(13);
					byte[] decodedPublicKey = ByteFormatter.decodeString(publicKey);
					SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

					Subscription[] subscriptions = subs_man.getSubscriptions();
					for (Subscription subscription : subscriptions) {
						if (Arrays.equals(subscription.getPublicKey(), decodedPublicKey)) {
							sub = subscription;
							break;
						}
					}
				}
				// hack to hide useless entries
				if (sub != null && sub.isSearchTemplate()) {
					return null;
				}
				return sub == null ? null : createSubscriptionMdiEntry(sub);
			}
		});

		SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

		subman_listener_quick = new SubscriptionManagerListener() {
			@Override
			public void
			subscriptionAdded(
				Subscription 		subscription )
			{
			}

			@Override
			public void
			subscriptionChanged(
				Subscription		sub,
				int					reason )
			{
				changeSubscription( sub, reason );
			}

			@Override
			public void
			subscriptionSelected(
				Subscription sub )
			{
				showSubscriptionMDI( sub );
			}

			@Override
			public void
			subscriptionRemoved(
				Subscription 		subscription )
			{
				removeSubscription( subscription );
			}

			@Override
			public void
			associationsChanged(
				byte[]		association_hash )
			{
			}

			@Override
			public void
			subscriptionRequested(
				URL					url,
				Map<String,Object>	options )
			{
			}
		};
		subs_man.addListener(subman_listener_quick);
	}

	/**
	 * Destroy what was created in uiQuickInit and delayedInit
	 */
	void uiDestroy() {

		for (Image image : icon_list) {
			image.dispose();
		}
		icon_list.clear();

		SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();
		if (subs_man != null) {
			if (subman_listener_delayed != null) {
				subs_man.removeListener(subman_listener_delayed);
			}
			if (subman_listener_quick != null) {
				subs_man.removeListener(subman_listener_quick);
			}
		}
		subman_listener_quick = null;
		subman_listener_delayed = null;

		try {
			TableManager tableManager = PluginInitializer.getDefaultInterface().getUIManager().getTableManager();
			if (columnCreationSubs != null) {
				tableManager.unregisterColumn(Download.class, "azsubs.ui.column.subs" );
						
				columnCreationSubs = null;
			}
			if (columnCreationSubsLink != null) {
				tableManager.unregisterColumn(Download.class, "azsubs.ui.column.subs_link" );
						
				columnCreationSubsLink = null;
			}
		} catch (Throwable ignore) {

		}

		if (pluginConfigListener != null) {
			default_pi.getPluginconfig().removeListener(pluginConfigListener);
			pluginConfigListener = null;
		}

		if (configModel != null) {
			configModel.destroy();
			configModel = null;
		}
	}

	void
	delayedInit()
	{
		if (swt == null) {
			return;
		}

		SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

		subman_listener_delayed = new SubscriptionManagerListener() {
			@Override
			public void
			subscriptionAdded(
				Subscription subscription )
			{
				checkSubscriptionForStuff( subscription );
			}

			@Override
			public void
			subscriptionChanged(
				Subscription		subscription,
				int					reason )
			{
				changeSubscription(subscription, reason);
			}

			@Override
			public void
			subscriptionSelected(
				Subscription subscription )
			{
			}

			@Override
			public void
			subscriptionRemoved(
				Subscription subscription )
			{
			}

			@Override
			public void
			associationsChanged(
				byte[] hash )
			{
				refreshColumns();
			}

			@Override
			public void
			subscriptionRequested(
				final URL					url,
				final Map<String,Object>	options )
			{
				Utils.execSWTThread(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								new SubscriptionWizard( url, options );
							}
						});
			}
		};
		subs_man.addListener(subman_listener_delayed);

		Subscription[] subs = subs_man.getSubscriptions();

		for ( Subscription sub: subs ){

			checkSubscriptionForStuff( sub );
		}

		createConfigModel();
	}

	private void
	checkSubscriptionForStuff(
		Subscription	sub )
	{
		if ( sub.isSearchTemplate()){

			return;
		}

		if ( sub.isSubscribed() && sub.getAddType() != Subscription.ADD_TYPE_IMPORT ){

			return;
		}

		try{
			Engine engine = sub.getEngine();

			if ( engine instanceof RSSEngine ){

				RSSEngine re = (RSSEngine)engine;

				String url_str = re.getSearchUrl( true );

				URL url = new URL( url_str );

				String prot = url.getProtocol();

				if ( prot.equals( "azplug" )){

					String q = url.getQuery();

					Map<String,String>	args = UrlUtils.decodeArgs( q );

					String id = args.get( "id" );

					if ( id.equals( "azbuddy" )){

						String arg = args.get( "arg" );

						String[] bits = arg.split( ":", 2 );

						String chat_protocol = bits[0];

						if ( chat_protocol.startsWith( "chat" )){

							Map<String,String> chat_args = UrlUtils.decodeArgs( bits[1]);

							String	chat_key = chat_args.get( "" );

							int pos = chat_key.toLowerCase( Locale.US ).indexOf( "website[pk=" );

							if ( pos != -1 ){

								Map<String,String>	cb_data = new HashMap<>();

								cb_data.put( "subname", sub.getName());

								cb_data.put( "subid", sub.getID());

								LocalActivityManager.addLocalActivity(
									"Website:" + sub.getID(),
									"rss",
									MessageText.getString(
										"subs.activity.website.found",
										new String[]{ sub.getName() }),
									new String[]{ MessageText.getString( "subscriptions.listwindow.subscribe" )},
									ActivityCallback.class,
									cb_data );
							}
						}
					}
				}
			}
		}catch( Throwable e ){
			// ignore, nothing to see!
		}
	}

	public static class
	ActivityCallback
		implements LocalActivityManager.LocalActivityCallback
	{
		@Override
		public void
		actionSelected(
			String action, Map<String, String> data)
		{
			SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

			String sub_id = (String)data.get( "subid" );

			final Subscription sub = subs_man.getSubscriptionByID( sub_id );

			if ( sub != null ){

				TagManager tag_man = TagManagerFactory.getTagManager();

				if ( tag_man != null && tag_man.isEnabled()){

					try{
						String tag_name = sub.getName() + ": " + MessageText.getString( "label.versions" );

						TagType tt = tag_man.getTagType( TagType.TT_DOWNLOAD_MANUAL );

						Tag tag = tt.getTag( tag_name, true );

						if ( tag == null ){

							tag = tt.createTag( tag_name, true );
						}

						TagFeatureLimits tfl = (TagFeatureLimits)tag;

						tfl.setMaximumTaggables( 5 );

						tfl.setRemovalStrategy( TagFeatureLimits.RS_DELETE_FROM_COMPUTER );

						sub.setTagID( tag.getTagUID());

					}catch( Throwable e ){

						Debug.out( e );
					}
				}

				SubscriptionHistory history = sub.getHistory();

				history.setCheckFrequencyMins( 10 );

				history.setMaxNonDeletedResults( 20 );

				history.setAutoDownload( true );

				SubscriptionResult[] results = sub.getResults( false );

				if ( results.length > 0 ){

					results[results.length-1].setRead( false );

					for ( int i=0;i<results.length-1;i++){

						results[i].setRead( true );
					}
				}else{

					sub.addListener(
						new SubscriptionListener()
						{
							@Override
							public void
							subscriptionDownloaded(
								Subscription subs )
							{
								SubscriptionResult[] results = subs.getResults( false );

								if ( results.length > 0 ){

									sub.removeListener( this );

									results[results.length-1].setRead( false );

									for ( int i=0;i<results.length-1;i++){

										results[i].setRead( true );
									}
								}
							}

							@Override
							public void
							subscriptionChanged(
								Subscription 	subs,
								int				reason )
							{
							}
						});
				}

				sub.setSubscribed( true );

				sub.requestAttention();

			}else{

				MessageBoxShell mb =
						new MessageBoxShell(
							MessageText.getString("subs.deleted.title"),
							MessageText.getString("subs.deleted.msg",
								new String[]{
									(String)data.get( "subname" )
								}),
							new String[] {
								MessageText.getString("Button.ok"),
							},
							0 );

					mb.open(new UserPrompterResultListener() {
						@Override
						public void prompterClosed(int result) {

						}
					});
			}
		}
	}

	private void createConfigModel() {
		final SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

		configModel = ui_manager.createBasicPluginConfigModel(
				ConfigSection.SECTION_ROOT, CONFIG_SECTION_ID);

		final IntParameter max_results =
			configModel.addIntParameter2(
				"subscriptions.config.maxresults",
				"subscriptions.config.maxresults",
				subs_man.getMaxNonDeletedResults());

			// search

		final BooleanParameter search_enable =
			configModel.addBooleanParameter2(
				"subscriptions.search.enable", "subscriptions.search.enable",
				subs_man.isSearchEnabled());

		search_enable.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param)
				{
					subs_man.setSearchEnabled( search_enable.getValue());
				}
			});

			// download subs enable

		final BooleanParameter download_subs_enable =
			configModel.addBooleanParameter2(
				"subscriptions.dl_subs.enable", "subscriptions.dl_subs.enable",
				subs_man.isSubsDownloadEnabled());

		download_subs_enable.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param)
				{
					subs_man.setSubsDownloadEnabled( download_subs_enable.getValue());
				}
			});

			// download subs enable

		final BooleanParameter activate_subs_enable =
			configModel.addBooleanParameter2(
				"subscriptions.activate.on.change.enable", "subscriptions.activate.on.change.enable",
				subs_man.getActivateSubscriptionOnChange());

		activate_subs_enable.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param)
				{
					subs_man.setActivateSubscriptionOnChange( activate_subs_enable.getValue());
				}
			});

			// mark results in library read
	
		final BooleanParameter mark_lib_results_read =
			configModel.addBooleanParameter2(
				"subscriptions.mark_results_in_lib.enable", "subscriptions.mark_results_in_lib.enable",
				subs_man.getMarkResultsInLibraryRead());
	
		mark_lib_results_read.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param)
				{
					subs_man.setMarkResultsInLibraryRead( mark_lib_results_read.getValue());
				}
			});		
		
		final IntParameter def_check =
				configModel.addIntParameter2(
					"subscriptions.config.def.check",
					"subscriptions.config.def.check",
					subs_man.getDefaultCheckFrequencyMins());

		def_check.setMinValue( 5 );

		def_check.addListener(
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter param )
					{
						subs_man.setDefaultCheckFrequencyMins(def_check.getValue());
					}
				});
		
			// rate limits

		final StringParameter rate_limits = configModel.addStringParameter2(
				"subscriptions.config.ratelimits",
				"subscriptions.config.ratelimits",
				subs_man.getRateLimits());

		rate_limits.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param )
				{
					subs_man.setRateLimits(rate_limits.getValue());
				}
			});

			// auto

		final BooleanParameter auto_start = configModel.addBooleanParameter2(
				"subscriptions.config.autostartdls",
				"subscriptions.config.autostartdls",
				subs_man.getAutoStartDownloads());

		auto_start.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param )
				{
					subs_man.setAutoStartDownloads( auto_start.getValue());
				}
			});

		final IntParameter min_auto_start_size =
			configModel.addIntParameter2(
				"subscriptions.config.autostart.min",
				"subscriptions.config.autostart.min",
				subs_man.getAutoStartMinMB());

		final IntParameter max_auto_start_size =
			configModel.addIntParameter2(
				"subscriptions.config.autostart.max",
				"subscriptions.config.autostart.max",
				subs_man.getAutoStartMaxMB());

		auto_start.addEnabledOnSelection( min_auto_start_size );
		auto_start.addEnabledOnSelection( max_auto_start_size );

		final IntParameter mark_as_read_after =
				configModel.addIntParameter2(
					"subscriptions.config.mark.read.after",
					"subscriptions.config.mark.read.after",
					subs_man.getAutoDownloadMarkReadAfterDays());

		final BooleanParameter hash_dirs = configModel.addBooleanParameter2(
				"subscriptions.config.addhashdirs",
				"subscriptions.config.addhashdirs",
				subs_man.getAddHashDirs());

		hash_dirs.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param )
				{
					subs_man.setAddHashDirs( hash_dirs.getValue());
				}
			});
		
		configModel.createGroup(
			"subscriptions.config.auto",
			new Parameter[]{
					auto_start,
					min_auto_start_size,
					max_auto_start_size,
					mark_as_read_after,
					hash_dirs,
			});

			// int param fires intermediate events so we have to rely on the save :(

		pluginConfigListener = new PluginConfigListener() {
			@Override
			public void
			configSaved() {
				subs_man.setMaxNonDeletedResults(max_results.getValue());
				subs_man.setAutoStartMinMB(min_auto_start_size.getValue());
				subs_man.setAutoStartMaxMB(max_auto_start_size.getValue());
				subs_man.setAutoDownloadMarkReadAfterDays(mark_as_read_after.getValue());
			}
		};
		default_pi.getPluginconfig().addListener(pluginConfigListener);


			// rss

		final BooleanParameter rss_enable =
			configModel.addBooleanParameter2(
				"subscriptions.rss.enable", "subscriptions.rss.enable",
				subs_man.isRSSPublishEnabled());

		rss_enable.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param)
				{
					subs_man.setRSSPublishEnabled( rss_enable.getValue());
				}
			});

		HyperlinkParameter rss_view =
			configModel.addHyperlinkParameter2(
				"device.rss.view", subs_man.getRSSLink());

		rss_enable.addEnabledOnSelection( rss_view );

		configModel.createGroup(
			"device.rss.group",
			new Parameter[]
			{
					rss_enable, rss_view,
			});
	}

	private void
	createSubsColumns(
		TableManager table_manager )
	{
		final TableCellRefreshListener	subs_refresh_listener =
			new TableCellRefreshListener()
			{
				@Override
				public void
				refresh(
					TableCell _cell )
				{
					TableCellSWT cell = (TableCellSWT)_cell;

					SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

					Download	dl = (Download)cell.getDataSource();

					if ( dl == null ){

						return;
					}

					Torrent	torrent = dl.getTorrent();

					if ( torrent != null ){

						Subscription[] subs = subs_man.getKnownSubscriptions( torrent.getHash());

						int	num_subscribed		= 0;
						int	num_unsubscribed	= 0;

						for (int i=0;i<subs.length;i++){

							if ( subs[i].isSubscribed()){

								num_subscribed++;

							}else{

								num_unsubscribed++;
							}
						}

						Graphic graphic;
						String	tooltip;

						int height = cell.getHeight();

						int	sort_order = 0;

						if ( subs.length == 0 ){

							graphic = null;
							tooltip	= null;

						}else{

							if ( num_subscribed == subs.length ){

								graphic = height >= 22?icon_rss_all_add_big:icon_rss_all_add_small;

								tooltip = MessageText.getString( "subscript.all.subscribed" );

							}else if ( num_subscribed > 0 ){

								graphic = height >= 22?icon_rss_some_add_big:icon_rss_some_add_small;

								tooltip = MessageText.getString( "subscript.some.subscribed" );

								sort_order	= 10000;

							}else{

								graphic = height >= 22?icon_rss_big:icon_rss_small;

								tooltip = MessageText.getString( "subscript.none.subscribed" );

								sort_order	= 1000000;
							}
						}

						sort_order += 1000*num_unsubscribed + num_subscribed;

						cell.setMarginHeight(0);
						cell.setGraphic( graphic );
						cell.setToolTip( tooltip );

						cell.setSortValue( sort_order );

						cell.setCursorID( graphic==null?SWT.CURSOR_ARROW:SWT.CURSOR_HAND );

					}else{

						cell.setCursorID( SWT.CURSOR_ARROW );

						cell.setSortValue( 0 );
					}
				}
			};

		final TableCellMouseListener	subs_mouse_listener =
			new TableCellMouseListener()
			{
				@Override
				public void
				cellMouseTrigger(
					TableCellMouseEvent event )
				{
					if ( event.eventType == TableCellMouseEvent.EVENT_MOUSEDOWN ){


						TableCell cell = event.cell;

						Download	dl = (Download)cell.getDataSource();

						Torrent	torrent = dl.getTorrent();

						if ( torrent != null ){

							SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();
							Subscription[] subs = subs_man.getKnownSubscriptions( torrent.getHash());

							if ( subs.length > 0 ){

								event.skipCoreFunctionality	= true;

								new SubscriptionWizard(PluginCoreUtils.unwrap(dl));

								refreshTitles( mdiEntryOverview );

								//new SubscriptionListWindow(PluginCoreUtils.unwrap(dl),true);
							}
						}
					}
				}
			};

		columnCreationSubs = new TableColumnCreationListener() {
			@Override
			public void tableColumnCreated(TableColumn result) {
				result.setAlignment(TableColumn.ALIGN_CENTER);
				result.setPosition(TableColumn.POSITION_LAST);
				result.setWidth(32);
				result.setRefreshInterval(TableColumn.INTERVAL_INVALID_ONLY);
				result.setType(TableColumn.TYPE_GRAPHIC);

				result.addCellRefreshListener(subs_refresh_listener);
				result.addCellMouseListener(subs_mouse_listener);
				result.setIconReference("image.subscription.column", true);

				synchronized (columns) {
					columns.add(result);
				}
			}
		};
		table_manager.registerColumn(Download.class, "azsubs.ui.column.subs",
				columnCreationSubs);

		final TableCellRefreshListener	link_refresh_listener =
			new TableCellRefreshListener()
			{
				@Override
				public void
				refresh(
					TableCell _cell )
				{
					TableCellSWT cell = (TableCellSWT)_cell;

					SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

					Download	dl = (Download)cell.getDataSource();

					if ( dl == null ){

						return;
					}

					String	str 		= "";

					Torrent	torrent = dl.getTorrent();

					if ( torrent != null ){

						byte[]	hash = torrent.getHash();

						Subscription[] subs = subs_man.getKnownSubscriptions( hash );

						for (int i=0;i<subs.length;i++){

							Subscription sub = subs[i];

							if ( sub.hasAssociation( hash )){

								str += (str.length()==0?"":"; ") + sub.getName();
							}
						}
					}

					cell.setCursorID( str.length() > 0?SWT.CURSOR_HAND:SWT.CURSOR_ARROW );

					cell.setText( str );
				}
			};

			final TableCellMouseListener	link_mouse_listener =
				new TableCellMouseListener()
				{
					@Override
					public void
					cellMouseTrigger(
						TableCellMouseEvent event )
					{
						if ( event.eventType == TableCellMouseEvent.EVENT_MOUSEDOWN ){

							TableCell cell = event.cell;

							Download	dl = (Download)cell.getDataSource();

							Torrent	torrent = dl.getTorrent();

							SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

							if ( torrent != null ){

								byte[]	hash = torrent.getHash();

								Subscription[] subs = subs_man.getKnownSubscriptions( hash );

								for (int i=0;i<subs.length;i++){

									Subscription sub = subs[i];

									if ( sub.hasAssociation( hash )){

										showSubscriptionMDI( sub );

										break;
									}
								}
							}
						}
					}
				};

		columnCreationSubsLink = new TableColumnCreationListener() {
			@Override
			public void tableColumnCreated(TableColumn result) {
				result.setAlignment(TableColumn.ALIGN_LEAD);
				result.setPosition(TableColumn.POSITION_INVISIBLE);
				result.setWidth(85);
				result.setRefreshInterval(TableColumn.INTERVAL_INVALID_ONLY);
				result.setType(TableColumn.TYPE_TEXT_ONLY);

				result.addCellRefreshListener(link_refresh_listener);
				result.addCellMouseListener(link_mouse_listener);
				result.setMinimumRequiredUserMode(Parameter.MODE_INTERMEDIATE);

				synchronized (columns) {
					columns.add(result);
				}
			}
		};
		table_manager.registerColumn(Download.class, "azsubs.ui.column.subs_link",
				columnCreationSubsLink);
	}

	protected void
	setupSideBar(
		final UISWTInstance		swt_ui )
	{
		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();

		if (mdi == null) {
			return;
		}

		if (sidebar_setup_done
				&& (mdiEntryOverview == null || mdiEntryOverview.isEntryDisposed())) {
			sidebar_setup_done = false;
		}

		mdiEntryOverview = mdi.createEntry(new UISWTViewBuilderCore(
				MultipleDocumentInterface.SIDEBAR_SECTION_SUBSCRIPTIONS, null,
				SubscriptionsView.class).setParentEntryID(
						MultipleDocumentInterface.SIDEBAR_HEADER_DISCOVERY).setPreferredAfterID( "~" + MultipleDocumentInterface.SIDEBAR_SECTION_CHAT),
				false);

		if (mdiEntryOverview == null) {
			return;
		}

		mdiEntryOverview.setDefaultExpanded(true);

		synchronized( this ){
				// seen double add buttons in the sidebar, not sure of cause but it would imply we are coming through here
				// twice which can't be good - protect against that

			if( sidebar_setup_done ){

				return;
			}

			sidebar_setup_done = true;
		}

		mdiEntryOverview.setImageLeftID("image.sidebar.subscriptions");

		setupHeader(mdi, mdiEntryOverview);

		String parentID = "sidebar." + MultipleDocumentInterface.SIDEBAR_SECTION_SUBSCRIPTIONS;

		MenuManager menu_manager = ui_manager.getMenuManager();

		MenuItem mi = menu_manager.addMenuItem( parentID, "menu.update.all.now" );
		mi.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		mi.addListener(
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					MenuItem menu, Object target )
				{
					SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

					SubscriptionScheduler sched = subs_man.getScheduler();

					Subscription[] subs = subs_man.getSubscriptions( true );

					for ( Subscription sub: subs ){

						if ( !sub.isSearchTemplate()){

							// manual update, ignore enabled state as this controls auto-updates
							// if ( sub.getHistory().isEnabled()){

								try{
									sched.downloadAsync( sub, false );

								}catch( Throwable e ){

									Debug.out( e );
								}
							// }
						}
					}
				}
			});

		mi = menu_manager.addMenuItem( parentID, "Subscription.menu.clearall" );
		mi.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		mi.addListener(
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					MenuItem menu, Object target )
				{
					SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

					Subscription[] subs = subs_man.getSubscriptions( true );

					for ( Subscription sub: subs ){

						if ( !sub.isSearchTemplate()){

							sub.getHistory().markAllResultsRead();
						}
					}
				}
			});
		
		mi = menu_manager.addMenuItem( parentID, "sep1" );
		mi.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		mi.setStyle( MenuItem.STYLE_SEPARATOR );

		mi = menu_manager.addMenuItem( parentID, "MainWindow.menu.view.configuration" );
		mi.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		mi.addListener(
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					MenuItem menu, Object target )
				{
			      	 UIFunctions uif = UIFunctionsManager.getUIFunctions();

			      	 if ( uif != null ){

			      		 uif.getMDI().showEntryByID(
			      				 MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
			      				 CONFIG_SECTION_ID);
			      	 }
				}
			});
	}

	private void setupHeader(MultipleDocumentInterface mdi,
			final MdiEntry headerEntry) {

		MdiEntryVitalityImage addSub = headerEntry.addVitalityImage("image.sidebar.subs.add");

		if (addSub != null) {
			addSub.setToolTip(MessageText.getString("subscriptions.add.tooltip"));

			addSub.addListener(new MdiEntryVitalityImageListener() {
				@Override
				public void mdiEntryVitalityImage_clicked(int x, int y) {
					new SubscriptionWizard();

					refreshTitles( mdiEntryOverview );
				}
			});
		}

		final MdiEntryVitalityImage warnSub = headerEntry.addVitalityImage(ALERT_IMAGE_ID);
		if (warnSub != null) {
			warnSub.setVisible(false);
		}

		final MdiEntryVitalityImage infoSub = headerEntry.addVitalityImage(INFO_IMAGE_ID);
		if (infoSub != null) {
			infoSub.setVisible(false);
		}


		headerEntry.setViewTitleInfo(
			new ViewTitleInfo()
			{
				private long	last_avail_calc = -1;
				private int		last_avail;

				@Override
				public Object
				getTitleInfoProperty(
					int propertyID)
				{
					Object result = null;

					if (propertyID == TITLE_INDICATOR_TEXT) {

						//boolean expanded = headerEntry.isExpanded();

							// always treat as collapsed - due to various sidebar restructuring this had effectively been
							// the case for quite a while and people complained when it reverted to the original way of working

						boolean expanded = false;

						SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

						Subscription[] subs = subs_man.getSubscriptions(true);

						if ( expanded ){

							if (warnSub != null) {
								warnSub.setVisible(false);
							}

						}else{

							int total = 0;

							boolean warn = false;

							String error_str = "";

							for (Subscription s : subs) {

								SubscriptionHistory history = s.getHistory();

								total += history.getNumUnread();

								String last_error = history.getLastError();

								if (last_error != null && last_error.length() > 0) {

									boolean auth_fail = history.isAuthFail();

									if (history.getConsecFails() >= 3 || auth_fail) {

										warn = true;

										if (error_str.length() > 128) {

											if (!error_str.endsWith(", ...")) {

												error_str += ", ...";
											}
										} else {

											error_str += (error_str.length() == 0 ? "" : ", ")
													+ last_error;
										}
									}
								}
							}

							if (warnSub != null) {
								warnSub.setVisible(warn);
								warnSub.setToolTip(error_str);
							}

							if (total > 0) {

								result = String.valueOf( total );
							}
						}

						if (infoSub != null) {
  						if ( subs.length == 0 && !COConfigurationManager.getBooleanParameter( "subscriptions.wizard.shown", false )){

  							long now = SystemTime.getMonotonousTime();

  							if ( 	last_avail_calc == -1 ||
  									now - last_avail_calc > 60*1000 ){

  								last_avail = subs_man.getKnownSubscriptionCount();

  								last_avail_calc = now;
  							}

  							if ( last_avail > 0 ){

  								infoSub.setVisible( true );

  								infoSub.setToolTip(
  									MessageText.getString(
  										"subscriptions.info.avail",
  										new String[]{
  											String.valueOf( last_avail )
  										}));
  							}
  						}else{

  							infoSub.setVisible( false );
  						}
						}
					}

					return( result );
				}
			});
	}

	protected void
	changeSubscription(
		final Subscription	subs,
		int					reason )
	{
		refreshTitles( mdiEntryOverview );

		if ( subs.isSubscribed()){

			if ( SubscriptionManagerFactory.getSingleton().getActivateSubscriptionOnChange()){

				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();

				if ( mdi != null ){

					mdi.loadEntryByID( getKey( subs ), true, true, subs );
				}
			}

			SubscriptionMDIEntry data = (SubscriptionMDIEntry)subs.getUserData( SUB_ENTRYINFO_KEY );

			if ( data != null && !data.isDisposed()){

				String cp = data.getCurrentParent();

				String parent = subs.getParent();

				if ( 	cp == parent ||
						( cp != null && parent != null && cp.equals( parent ))){

					// no change

				}else{

					reloadSubscriptionMDI( subs );
				}
			}
		}else{

			removeSubscription( subs);
		}
	}


	private MdiEntry
	createSubscriptionMdiEntry(
			final Subscription subs)
	{
		if (!subs.isSubscribed()){

				// user may have deleted subscrtipion, but our register is staill there

			return null;
		}

		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();

		if ( mdi == null ){

				// closing down

			return( null );
		}

		String parent_name = subs.getParent();

		if ( parent_name != null && parent_name.length() == 0 ){

			parent_name = null;
		}

		final String key = getKey( subs );

		String subs_name = subs.getName();

		TreeMap<String,String>	name_map = new TreeMap<String,String>( new FormattersImpl().getAlphanumericComparator( true ));

		name_map.put( subs_name, key );

		MdiEntry[] existing = mdi.getEntries();

		for ( MdiEntry e: existing ){

			String id = e.getViewID();

			if ( id.startsWith( "Subscription_" )){

				Object ds = e.getDataSource();

				if ( ds instanceof Subscription ){

					String sp = ((Subscription)ds).getParent();

					if ( sp != null && sp.length() == 0 ){

						sp = null;
					}

					if ( 	sp == parent_name ||
							( sp != null && parent_name != null && sp.equals( parent_name ))){

						name_map.put( e.getTitle(), id );
					}
				}
			}
		}

		String	prev_id = null;

		for ( String this_id: name_map.values()){

			if ( this_id == key ){

				break;
			}

			prev_id = this_id;
		}

		if ( prev_id == null && name_map.size() > 1 ){

			Iterator<String>	it = name_map.values().iterator();

			it.next();

			prev_id = "~" + it.next();
		}

		MdiEntry entry;

		if ( parent_name == null || parent_name.length() == 0 ){

			entry = mdi.createEntry(new UISWTViewBuilderCore(key, null,
					SubscriptionView.class).setParentEntryID(
							MultipleDocumentInterface.SIDEBAR_SECTION_SUBSCRIPTIONS).setInitialDatasource(
									subs).setPreferredAfterID(prev_id),
					true);

		}else{

			MdiEntry parent_entry;

			synchronized( parent_views ){

				parent_entry = parent_views.get( parent_name );

				if ( parent_entry == null ){

					SubsParentView parent = new SubsParentView( parent_name );

					String parent_key = getParentKey( parent_name );

					String parent_prev_id = null;

					parent_views.put( parent_name, parent_entry );

					String	parent_prev = null;

					for ( String pn: parent_views.keySet()){

						if ( pn == parent_name ){

							break;
						}

						parent_prev = pn;
					}

					boolean	is_before;

					if ( parent_prev == null && parent_views.size() > 1 ){

						Iterator<String>	it = parent_views.keySet().iterator();

						it.next();

						parent_prev = it.next();

						is_before = true;

					}else{

						is_before = false;
					}

					if ( parent_prev != null ){

						parent_prev_id = getParentKey( parent_prev );

						if ( is_before ){

							parent_prev_id = "~" + parent_prev_id;
						}
					}

					parent_entry =
						mdi.createEntryFromSkinRef(
							MultipleDocumentInterface.SIDEBAR_SECTION_SUBSCRIPTIONS,
							parent_key,
							null,
							parent_name,
							parent, null, false, parent_prev_id );
				}
			}

			entry = mdi.createEntry(new UISWTViewBuilderCore(key, null,
					SubscriptionView.class).setParentEntryID(
							parent_entry.getViewID()).setInitialDatasource(subs),
					true);
		}

			// This sets up the entry (menu, etc)

		SubscriptionMDIEntry entryInfo = new SubscriptionMDIEntry(subs, entry);

		subs.setUserData(SUB_ENTRYINFO_KEY, entryInfo);
		entry.addListener(new MdiCloseListener() {
			@Override
			public void mdiEntryClosed(MdiEntry entry, boolean userClosed) {
				subs.setUserData(SUB_ENTRYINFO_KEY, null);
			}
		});

		return entry;
	}

	private String
	getKey(
		Subscription		subs )
	{
		String key = "Subscription_" + ByteFormatter.encodeString(subs.getPublicKey());

		return( key );
	}

	private void
	showSubscriptionMDI(
		Subscription	sub )
	{
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();

		if ( mdi != null ){

			String key = getKey( sub );

			mdi.showEntryByID( key, sub) ;
		}
	}

	private void
	reloadSubscriptionMDI(
		Subscription	sub )
	{
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();

		if ( mdi != null ){

			String key = getKey( sub );

			mdi.closeEntryByID( key );

			mdi.showEntryByID( key, sub );
		}
	}

	private void
	removeSubscriptionMDI(
		Subscription	sub )
	{
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();

		if ( mdi != null ){

			String key = getKey( sub );

			mdi.closeEntryByID( key );
		}
	}

	private String
	getParentKey(
		String		parent_name )
	{
		byte[] bytes;

		try{
			bytes =  parent_name.getBytes("UTF-8" );

		}catch( Throwable e ){

			bytes = parent_name.getBytes();
		}

		return(	"SubscriptionParent_" + ByteFormatter.encodeString( bytes ));
	}

	protected void
	refreshTitles(
		MdiEntry		entry )
	{
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();

		if ( mdi == null ){

			return;
		}

		while( entry != null ){

			entry.redraw();
			ViewTitleInfoManager.refreshTitleInfo(entry.getViewTitleInfo());

			String key = entry.getParentID();

			if ( key == null ){

				return;
			}

			entry = mdi.getEntry( key );
		}
	}

	protected void
	removeSubscription(
		final Subscription	subs )
	{
		removeSubscriptionMDI( subs );

		refreshColumns();
	}

	protected void
	refreshColumns()
	{
		synchronized( columns ){

			for (TableColumn column : columns) {

				column.invalidateCells();
			}
		}
	}

	protected Graphic
	loadGraphic(
		UISWTInstance	swt,
		String			name )
	{
		Image	image = swt.loadImage( "com/biglybt/ui/images/" + name );

		Graphic graphic = swt.createGraphic(image );

		icon_list.add(image);

		return( graphic );
	}

	protected interface
	MenuCreator
	{
		public MenuItem
		createMenu(
			String 	resource_id );

		public void
		refreshView();
	}

	protected static void
	createMenus(
		final MenuManager		menu_manager,
		final MenuCreator		menu_creator,
		final Subscription[]	all_subs )
	{
		if ( all_subs.length > 1 ){

			boolean all_search_templates = true;
			
			for ( Subscription sub: all_subs ){
			
				if ( !sub.isSearchTemplate()){
					
					all_search_templates = false;
					
					break;
				}
			}
			
			if ( !all_search_templates ){
				
				MenuItem menuItem = menu_creator.createMenu( "Subscription.menu.forcecheck" );
				menuItem.setText(MessageText.getString("Subscription.menu.forcecheck"));
				menuItem.addMultiListener(new SubsMenuItemListener() {
					@Override
					public void selected(Subscription[] subs) {
						for ( Subscription sub: subs ){
							if ( sub.isSearchTemplate()){
								continue;
							}
							try {
								sub.getManager().getScheduler().downloadAsync( sub, true );
							} catch (SubscriptionException e) {
								Debug.out(e);
							}
						}
					}
				});
	
				menuItem = menu_creator.createMenu( "Subscription.menu.clearall");
				menuItem.addMultiListener(new SubsMenuItemListener() {
					@Override
					public void selected(Subscription[] subs) {
						for ( Subscription sub: subs ){
							if ( sub.isSearchTemplate()){
								continue;
							}
							sub.getHistory().markAllResultsRead();
						}
						menu_creator.refreshView();
					}
				});
				
				menu_creator.createMenu( "s1").setStyle( MenuItem.STYLE_SEPARATOR );

				menuItem = menu_creator.createMenu( "menu.set.parent");
	
				menuItem.addMultiListener(new SubsMenuItemListener() {
					@Override
					public void selected(final Subscription[] subs) {
						UISWTInputReceiver entry = new SimpleTextEntryWindow();
						if ( subs.length==1 ){
							entry.setPreenteredText(subs[0].getParent(), false );
						}
						entry.maintainWhitespace(false);
						entry.allowEmptyInput( true );
						entry.setLocalisedTitle(MessageText.getString("label.set.parent"));
	
						entry.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver entry) {
								if (!entry.hasSubmittedInput()){
	
									return;
								}
	
								String input = entry.getSubmittedInput().trim();
	
								if ( input.length() == 0 ){
	
									input = null;
								}
	
								for ( Subscription sub: subs ){
	
									if ( sub.isSearchTemplate()){
										continue;
									}
									sub.setParent( input );
								}
							}
						});
					}
				});
			}
			
			addExecOnNewResultSubMenu( menu_manager, menu_creator, all_subs );
			
			addDependsOnSubMenu( menu_manager, menu_creator, all_subs );
			
			menu_creator.createMenu( "s2").setStyle( MenuItem.STYLE_SEPARATOR );
			
				// refresh period
	
			MenuItem menuItem = menu_creator.createMenu(  "subs.prop.update_period" );
		
			menuItem.setText( menuItem.getText() + "..." );
			
			menuItem.addMultiListener(new SubsMenuItemListener() {
				@Override
				public void selected(final Subscription[] subs) {
					UISWTInputReceiver entry = new SimpleTextEntryWindow();
					entry.maintainWhitespace(false);
					entry.allowEmptyInput( false );
		
					entry.setLocalisedTitle(MessageText.getString("subscriptions.enter.freq"));
	
					entry.prompt(new UIInputReceiverListener() {
						@Override
						public void UIInputReceiverClosed(UIInputReceiver entry) {
							if (!entry.hasSubmittedInput()) {
								return;
							}
							String input = entry.getSubmittedInput().trim();
	
							if ( input.length() > 0 ){
	
								try{
									int num = Integer.parseInt( input );
										
									for ( Subscription sub: subs ){
									
										sub.getHistory().setCheckFrequencyMins( num);

									}
								}catch( Throwable e ){
	
									Debug.out( e );
								}
							}
						}
					});
				}
			});
			
			menu_creator.createMenu( "s3").setStyle( MenuItem.STYLE_SEPARATOR );
			
			return;
		}

		final Subscription subs = all_subs[0];

		boolean is_search_template = subs.isSearchTemplate();

		if ( !is_search_template ){

			MenuItem menuItem = menu_creator.createMenu( "Subscription.menu.forcecheck" );
			menuItem.setText(MessageText.getString("Subscription.menu.forcecheck"));
			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					try {
						subs.getManager().getScheduler().downloadAsync( subs, true );
					} catch (SubscriptionException e) {
						Debug.out(e);
					}
				}
			});

			menuItem = menu_creator.createMenu( "Subscription.menu.clearall");
			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					subs.getHistory().markAllResultsRead();
					menu_creator.refreshView();
				}
			});

			menuItem = menu_creator.createMenu( "Subscription.menu.dirtyall");
			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					subs.getHistory().markAllResultsUnread();
					menu_creator.refreshView();
				}
			});

			menuItem = menu_creator.createMenu( "Subscription.menu.deleteall");
			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					subs.getHistory().deleteAllResults();
					menu_creator.refreshView();
				}
			});

			menuItem = menu_creator.createMenu( "Subscription.menu.reset");
			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					subs.getHistory().reset();
					try{
						subs.getEngine().reset();
					}catch( Throwable e ){
						Debug.printStackTrace(e);
					}
					try{
						subs.getManager().getScheduler().downloadAsync(subs, true);

					}catch( Throwable e ){

						Debug.out(e);
					}
				}
			});

			try{
				Engine engine = subs.getEngine();

				if ( engine instanceof WebEngine ){

					if (((WebEngine)engine).isNeedsAuth()){

						menuItem = menu_creator.createMenu( "Subscription.menu.resetauth");
						menuItem.addListener(new SubsMenuItemListener() {
							@Override
							public void selected(Subscription subs) {
								try{
									Engine engine = subs.getEngine();

									if ( engine instanceof WebEngine ){

										((WebEngine)engine).setCookies( null );
									}
								}catch( Throwable e ){

									Debug.printStackTrace(e);
								}

								try{
									subs.getManager().getScheduler().downloadAsync(subs, true);

								}catch( Throwable e ){

									Debug.out(e);
								}
							}
						});

						menuItem = menu_creator.createMenu( "Subscription.menu.setcookies");
						menuItem.addListener(new SubsMenuItemListener() {
							@Override
							public void selected(final Subscription subs) {
								try{
									Engine engine = subs.getEngine();

									if ( engine instanceof WebEngine ){

										final WebEngine we = (WebEngine)engine;

										UISWTInputReceiver entry = new SimpleTextEntryWindow();

										String[] req = we.getRequiredCookies();

										String	req_str = "";

										for ( String r:req ){

											req_str += (req_str.length()==0?"":";") + r + "=?";
										}
										entry.setPreenteredText( req_str, true );
										entry.maintainWhitespace(false);
										entry.allowEmptyInput( false );
										entry.setTitle("general.enter.cookies");
										entry.prompt(new UIInputReceiverListener() {
											@Override
											public void UIInputReceiverClosed(UIInputReceiver entry) {
												if (!entry.hasSubmittedInput()){

													return;
												}

												try {
			  									String input = entry.getSubmittedInput().trim();

			  									if ( input.length() > 0 ){

			  										we.setCookies( input );

			  										subs.getManager().getScheduler().downloadAsync(subs, true);
			  									}
												}catch( Throwable e ){

													Debug.printStackTrace(e);
												}
											}
										});
									}
								}catch( Throwable e ){

									Debug.printStackTrace(e);
								}
							}
						});
					}
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}

				// sep

			menu_creator.createMenu( "s4").setStyle( MenuItem.STYLE_SEPARATOR );

				// category

			menuItem = menu_creator.createMenu( "MyTorrentsView.menu.setCategory");
			menuItem.setStyle( MenuItem.STYLE_MENU );

			menuItem.addFillListener(
				new MenuItemFillListener()
				{
					@Override
					public void
					menuWillBeShown(
						MenuItem 	menu,
						Object 		data )
					{
						addCategorySubMenu( menu_manager, menu, subs );
					}
				});

				// tag

			menuItem = menu_creator.createMenu( "label.tag");

			menuItem.setStyle( MenuItem.STYLE_MENU );

			menuItem.addFillListener(
				new MenuItemFillListener()
				{
					@Override
					public void
					menuWillBeShown(
						MenuItem 	menu,
						Object 		data )
					{
						addTagSubMenu( menu_manager, menu, subs );
					}
				});

				// parent

			menuItem = menu_creator.createMenu( "menu.set.parent");

			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(final Subscription subs) {
					UISWTInputReceiver entry = new SimpleTextEntryWindow();
					entry.setPreenteredText(subs.getParent(), false );
					entry.maintainWhitespace(false);
					entry.allowEmptyInput( true );
					entry.setLocalisedTitle(MessageText.getString("label.set.parent",
							new String[] {
								subs.getName()
							}));
					entry.prompt(new UIInputReceiverListener() {
						@Override
						public void UIInputReceiverClosed(UIInputReceiver entry) {
							if (!entry.hasSubmittedInput()){

								return;
							}

							String input = entry.getSubmittedInput().trim();

							if ( input.length() == 0 ){

								input = null;
							}

							subs.setParent( input );

						}
					});
				}
			});

			addExecOnNewResultSubMenu( menu_manager, menu_creator, all_subs );

			addDependsOnSubMenu( menu_manager, menu_creator, all_subs );
			
				// view options
			
			menuItem = menu_creator.createMenu( "menu.view.options");

			menuItem.setStyle( MenuItem.STYLE_MENU );

			menuItem.addFillListener(
				new MenuItemFillListener()
				{
					@Override
					public void
					menuWillBeShown(
						MenuItem 	menu,
						Object 		data )
					{
						menu.removeAllChildItems();

						int vo = subs.getViewOptions();
						
						MenuItem m = menu_manager.addMenuItem( menu, "label.full" );

						m.setStyle( MenuItem.STYLE_RADIO );

						m.setData(Boolean.valueOf( vo == Subscription.VO_FULL ));

						m.addListener(new SubsMenuItemListener() {
							@Override
							public void selected(final Subscription subs) {
								subs.setViewOptions( Subscription.VO_FULL);
							}});
						
						m = menu_manager.addMenuItem( menu, "label.no.header" );

						m.setStyle( MenuItem.STYLE_RADIO );

						m.setData(Boolean.valueOf( vo == Subscription.VO_HIDE_HEADER ));
						
						m.addListener(new SubsMenuItemListener() {
							@Override
							public void selected(final Subscription subs) {
								subs.setViewOptions( Subscription.VO_HIDE_HEADER);
							}});
					}
				});
			
				// chat

			final String key = SubscriptionUtils.getSubscriptionChatKey( subs );

			if ( key != null ){

				menuItem = menu_creator.createMenu( "label.chat");
				menuItem.setHeaderCategory(MenuItem.HEADER_SOCIAL);

				MenuBuildUtils.addChatMenu(
					menu_manager,
					menuItem,
					new MenuBuildUtils.ChatKeyResolver()
					{
						@Override
						public String getResourceKey(){
							return( "menu.discuss.subs" );
						}
						
						@Override
						public String getChatKey(Object object) {

							return( key );
						}
					});
			}

			if ( subs.isUpdateable()){

				menuItem = menu_creator.createMenu( "MyTorrentsView.menu.rename");
				menuItem.addListener(new SubsMenuItemListener() {
					@Override
					public void selected(final Subscription subs) {
						UISWTInputReceiver entry = new SimpleTextEntryWindow();
						entry.setPreenteredText(subs.getName(), false );
						entry.maintainWhitespace(false);
						entry.allowEmptyInput( false );
						entry.setLocalisedTitle(MessageText.getString("label.rename",
								new String[] {
									subs.getName()
								}));
						entry.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver entry) {
								if (!entry.hasSubmittedInput()){

									return;
								}

								String input = entry.getSubmittedInput().trim();

								if ( input.length() > 0 ){

									try{
										subs.setName( input );

									}catch( Throwable e ){

										Debug.printStackTrace(e);
									}
								}
							}
						});
					}
				});
			}

			menuItem = menu_creator.createMenu( "Subscription.menu.upgrade");
			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					subs.resetHighestVersion();
				}
			});

			menuItem.addFillListener(
				new MenuItemFillListener()
				{
					@Override
					public void
					menuWillBeShown(
						MenuItem 	menu,
						Object 		data )
					{
						menu.setVisible( subs.getHighestVersion() > subs.getVersion());
					}
				});
		}

		MenuItem menuItem = menu_creator.createMenu( "label.copy.uri.to.clip");
		menuItem.addListener(new SubsMenuItemListener() {
			@Override
			public void selected(Subscription subs) {
				ClipboardCopy.copyToClipBoard( subs.getURI());
			}
		});

		menuItem = menu_creator.createMenu( "Subscription.menu.export");
		menuItem.addListener(new SubsMenuItemListener() {
			@Override
			public void selected(Subscription subs) {
				export( subs );
			}
		});

			// sep

		menu_creator.createMenu( "s2").setStyle( MenuItem.STYLE_SEPARATOR );

		if ( !is_search_template ){
				// change url

			try{
				Engine engine = subs.getEngine();

				if ( engine instanceof WebEngine ){

					menuItem = menu_creator.createMenu( "menu.change.url");
					menuItem.addListener(new SubsMenuItemListener() {
						@Override
						public void selected(final Subscription subs) {
							UISWTInputReceiver entry = new SimpleTextEntryWindow();

							try{
								WebEngine web_engine = (WebEngine)subs.getEngine();

								entry.setPreenteredText(web_engine.getSearchUrl( true ), false );
								entry.maintainWhitespace(false);
								entry.allowEmptyInput( false );
								entry.setLocalisedTitle(MessageText.getString("change.url.msg.title",
										new String[] {
											subs.getName()
										}));
								entry.setMessage( "change.url.msg.desc" );
								entry.prompt(new UIInputReceiverListener() {
									@Override
									public void UIInputReceiverClosed(UIInputReceiver entry) {
										if (!entry.hasSubmittedInput()){

											return;
										}

										String input = entry.getSubmittedInput().trim();

										if ( input.length() > 0 ){

											try{
												WebEngine web_engine = (WebEngine)subs.getEngine();

												web_engine.setSearchUrl( input );

												subs.cloneWithNewEngine( web_engine );

											}catch( Throwable e ){

												Debug.out(e);
											}
										}
									}
								});
							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					});

				}
			}catch( Throwable e ){
				Debug.out( e );
			}

				// public

			menuItem = menu_creator.createMenu( "subs.prop.is_public");
			menuItem.setStyle( MenuItem.STYLE_CHECK );

			menuItem.addFillListener( new MenuItemFillListener(){
				@Override
				public void menuWillBeShown(MenuItem menu, Object data ){
					menu.setData( subs.isPublic());
				}});

			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					try{
						subs.setPublic( !subs.isPublic());
					}catch( Throwable e ){
						Debug.out(e);
					}
				}
			});

				// enabled

			menuItem = menu_creator.createMenu( "subs.prop.enabled");
			menuItem.setStyle( MenuItem.STYLE_CHECK );

			menuItem.addFillListener( new MenuItemFillListener(){
				@Override
				public void menuWillBeShown(MenuItem menu, Object data ){
					menu.setData( subs.getHistory().isEnabled());
				}});

			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					try{
						subs.getHistory().setEnabled( !subs.getHistory().isEnabled());
					}catch( Throwable e ){
						Debug.out(e);
					}
				}
			});

			if ( subs.isAutoDownloadSupported()){

					// auto-dl

				menuItem = menu_creator.createMenu( "subs.prop.is_auto");
				menuItem.setStyle( MenuItem.STYLE_CHECK );

				menuItem.addFillListener( new MenuItemFillListener(){
					@Override
					public void menuWillBeShown(MenuItem menu, Object data ){
						menu.setData( subs.getHistory().isAutoDownload());
					}});

				menuItem.addListener(new SubsMenuItemListener() {
					@Override
					public void selected(Subscription subs) {
						try{
							subs.getHistory().setAutoDownload(!subs.getHistory().isAutoDownload());
						}catch( Throwable e ){
							Debug.out(e);
						}
					}
				});
			}

				// refresh period

			menuItem = menu_creator.createMenu(  "subs.prop.update_period" );

			menuItem.addFillListener( new MenuItemFillListener(){
				@Override
				public void menuWillBeShown(MenuItem menu, Object data ){
					int check_freq = subs.getHistory().getCheckFrequencyMins();

					String text = MessageText.getString( "subs.prop.update_period" );

					if ( check_freq!=Integer.MAX_VALUE ){

						text += " (" +  check_freq + " " + MessageText.getString( "ConfigView.text.minutes") + ")";
					}

					menu.setText( text + "..." );
				}});


			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(final Subscription subs) {
					UISWTInputReceiver entry = new SimpleTextEntryWindow();
					entry.maintainWhitespace(false);
					entry.allowEmptyInput( false );

					int check_freq = subs.getHistory().getCheckFrequencyMins();

					entry.setPreenteredText( check_freq==Integer.MAX_VALUE?"":String.valueOf( check_freq ), false );

					entry.setLocalisedTitle(MessageText.getString("subscriptions.enter.freq"));

					entry.prompt(new UIInputReceiverListener() {
						@Override
						public void UIInputReceiverClosed(UIInputReceiver entry) {
							if (!entry.hasSubmittedInput()) {
								return;
							}
							String input = entry.getSubmittedInput().trim();

							if ( input.length() > 0 ){

								try{
									subs.getHistory().setCheckFrequencyMins( Integer.parseInt( input ));

								}catch( Throwable e ){

								}
							}
						}
					});
				}
			});

			// dl is anon

			menuItem = menu_creator.createMenu( "subs.prop.is_dl_anon");
			menuItem.setStyle( MenuItem.STYLE_CHECK );

			menuItem.addFillListener( new MenuItemFillListener(){
				@Override
				public void menuWillBeShown(MenuItem menu, Object data ){
					menu.setData( subs.getHistory().getDownloadNetworks()!=null);
				}});

			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					try{
						boolean is_anon = subs.getHistory().getDownloadNetworks()!=null;

						subs.getHistory().setDownloadNetworks(is_anon?null:AENetworkClassifier.AT_NON_PUBLIC);
					}catch( Throwable e ){
						Debug.out(e);
					}
				}
			});

				// post notification

			menuItem = menu_creator.createMenu( "subs.noti.post");
			menuItem.setStyle( MenuItem.STYLE_CHECK );

			menuItem.addFillListener( new MenuItemFillListener(){
				@Override
				public void menuWillBeShown(MenuItem menu, Object data ){
					menu.setData( subs.getHistory().getNotificationPostEnabled());
				}});

			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(Subscription subs) {
					try{
						subs.getHistory().setNotificationPostEnabled(!subs.getHistory().getNotificationPostEnabled());
					}catch( Throwable e ){
						Debug.out(e);
					}
				}
			});
				// max results

			menuItem = menu_creator.createMenu( "label.set.max.results" );

			menuItem.addFillListener( new MenuItemFillListener(){
				@Override
				public void menuWillBeShown(MenuItem menu, Object data ){
					int max_results = subs.getHistory().getMaxNonDeletedResults();

					if ( max_results < 0 ){

						SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

						max_results = subs_man.getMaxNonDeletedResults();
					}

					String max_results_str = (max_results==0?MessageText.getString( "ConfigView.unlimited" ):String.valueOf( max_results ));

					String text = MessageText.getString( "label.set.max.results" );

					text += " (" + max_results_str + ")";

					menu.setText( text + "..." );
				}});


			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(final Subscription subs) {
					UISWTInputReceiver entry = new SimpleTextEntryWindow();
					entry.maintainWhitespace(false);
					entry.allowEmptyInput( true );

					int max_results = subs.getHistory().getMaxNonDeletedResults();

					if ( max_results < 0 ){

						SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

						max_results = subs_man.getMaxNonDeletedResults();
					}

					if ( max_results > 0 ){

						entry.setPreenteredText( String.valueOf( max_results ), false );
					}

					entry.setLocalisedTitle(MessageText.getString("subscriptions.enter.max.results"));

					entry.prompt(new UIInputReceiverListener() {
						@Override
						public void UIInputReceiverClosed(UIInputReceiver entry) {
							if (!entry.hasSubmittedInput()) {
								return;
							}
							String input = entry.getSubmittedInput().trim();

							try{
								subs.getHistory().setMaxNonDeletedResults( input.length()==0?-1:Math.abs( Integer.parseInt( input )));

							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					});
				}
			});

				// rename

			menuItem = menu_creator.createMenu( "MyTorrentsView.menu.rename" );
			menuItem.addListener(new SubsMenuItemListener() {
				@Override
				public void selected(final Subscription subs) {
					UISWTInputReceiver entry = new SimpleTextEntryWindow();
					entry.maintainWhitespace(false);
					entry.allowEmptyInput( false );

					entry.setPreenteredText(subs.getName(), false );

					entry.maintainWhitespace(false);

					entry.allowEmptyInput( false );

					entry.setLocalisedTitle(MessageText.getString("label.rename",
							new String[] {
							subs.getName()
					}));

					entry.prompt(new UIInputReceiverListener() {
						@Override
						public void UIInputReceiverClosed(UIInputReceiver entry) {
							if (!entry.hasSubmittedInput()) {
								return;
							}
							String input = entry.getSubmittedInput().trim();

							if ( input.length() > 0 ){

								subs.setLocalName( input );
							}
						}
					});
				}
			});
		}


		menuItem = menu_creator.createMenu( "Subscription.menu.remove");

		Utils.setMenuItemImage(menuItem, "delete");

		menuItem.addListener(new SubsMenuItemListener() {
			@Override
			public void selected(Subscription subs) {
				removeWithConfirm( subs );
			}
		});

		menu_creator.createMenu( "s3").setStyle( MenuItem.STYLE_SEPARATOR );

		menuItem = menu_creator.createMenu( "Subscription.menu.properties");
		menuItem.addListener(new SubsMenuItemListener() {
			@Override
			public void selected(Subscription subs){
				showProperties( subs );
			}
		});
	}

	private static void
	addExecOnNewResultSubMenu(
		MenuManager		menu_manager,
		MenuCreator		menu_creator,
		Subscription[]	menu_subs )	
	{
		if ( menu_subs.length != 1 ){
			
			return;
		}
		
		Subscription subs = menu_subs[0];
		
		MenuItem menuItem = menu_creator.createMenu( "menu.exec.on.new.result");

		menuItem.setStyle( MenuItem.STYLE_MENU );	
		
		menuItem.addFillListener(
			new MenuItemFillListener()
			{
				@Override
				public void
				menuWillBeShown(
					MenuItem 	menu,
					Object 		data )
				{
					menu.removeAllChildItems();
		
					MenuItem mi = menu_manager.addMenuItem( menu, "label.script" );

					String script = subs.getExecuteOnNewResult();

					if ( script == null ){
						
						script = "";
					}
					
					String f_script = script;
					
					if ( script.length() > 30 ){
						script = script.substring( 0, 30);
					}

					String msg = MessageText.getString( "label.script" );

					if ( script.length() > 0 ){

						msg += ": " + script;
					}

					msg += "...";

					mi.setText( msg );

					mi.addListener((m,ev)->{
						String msg2 = MessageText.getString( "UpdateScript.message" );
		
						SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow( "UpdateScript.title", "!" + msg2 + "!" );
		
						entryWindow.setPreenteredText( f_script, false );
						
						entryWindow.selectPreenteredText( true );
		
						entryWindow.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
								if ( entryWindow.hasSubmittedInput()){
		
									String text = entryWindow.getSubmittedInput().trim();
		
									subs.setExecuteOnNewResult( text );
								}
							}
						});
					});
				}});

	}
	
	private static void
	addDependsOnSubMenu(
		MenuManager		menu_manager,
		MenuCreator		menu_creator,
		Subscription[]	menu_subs )	
	{
		MenuItem menuItem = menu_creator.createMenu( "menu.depends.on");

		menuItem.setStyle( MenuItem.STYLE_MENU );

		menuItem.addFillListener(
			new MenuItemFillListener()
			{
				@Override
				public void
				menuWillBeShown(
					MenuItem 	menu,
					Object 		data )
				{
					menu.removeAllChildItems();

					SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

					Subscription[] all_subs = subs_man.getSubscriptions( true );

					List<Subscription>	templates = new ArrayList<>();
					
					for ( Subscription sub: all_subs ){
						
						if ( sub.isSubscriptionTemplate()){
							
							templates.add( sub );
						}
					}
					
					if ( templates.isEmpty()){
						
						MenuItem mi = menu_manager.addMenuItem( menu, "menu.no.subs.templates" );

						mi.setStyle( MenuItem.STYLE_RADIO );
						
						mi.setData( true );
						
						mi.setEnabled( false );
						
						mi = menu_manager.addMenuItem( menu, "sep" );

						mi.setStyle( MenuItem.STYLE_SEPARATOR );
						
						mi = menu_manager.addMenuItem( 
								menu, 
								"!" + MessageText.getString( "Wizard.Subscription.template.title" ) + "...!" );

						mi.addListener((m,ev)->{
														
							new SubscriptionWizard( SubscriptionWizard.MODE_CREATE_TEMPLATE );
						});
						
					}else{
						
						Set<Subscription>	enabled = new HashSet<>();
						
						List<Subscription> menu_templates 	= new ArrayList<>();
						List<Subscription> menu_search		= new ArrayList<>();
						
						for ( Subscription subs: menu_subs ){
							
							if ( subs.isSearchTemplate()){
								
								menu_search.add( subs );
								
								continue;
								
							}else if ( subs.isSubscriptionTemplate()){
								
								menu_templates.add( subs );
							}								
							
							List<Subscription> depends_on = subs.getDependsOn();
							
							if ( depends_on.isEmpty()){
								
								enabled.clear();
								
								break;
								
							}else{
								
								if ( enabled.isEmpty()){
									
									enabled.addAll( depends_on );
									
								}else{
									
									enabled.retainAll( depends_on );
									
									if ( enabled.isEmpty()){
										
										break;
									}
								}
							}
						}
						
						if ( menu_search.size() == menu_subs.length){
							
							return;
						}
						
						Collections.sort( templates,(t1,t2)->t1.getName().compareTo(t2.getName()));
						
						for ( Subscription sub: templates ){
							
							if ( menu_templates.contains( sub )){
								
								continue;
							}
							
							MenuItem mi = menu_manager.addMenuItem( menu, "!" + sub.getName() + "!" );

							mi.setStyle( MenuItem.STYLE_CHECK );
							
							boolean enable = enabled.contains( sub );
							
							mi.setData( enable );

							mi.addMultiListener((m,target)->{							
								for ( Subscription s: menu_subs ){
									
									if ( s.isSearchTemplate()){
										
										continue;
									}
									
									List<Subscription> deps = s.getDependsOn();
									
									if ( enable ){
										
										deps.remove( sub );
										
									}else{
										
										deps.add( sub );
									}
									
									s.setDependsOn( deps );
								}
							});
						}
					}
				}
			});
	}
	
	private static void
	addCategorySubMenu(
		MenuManager				menu_manager,
		MenuItem				menu,
		final Subscription		subs )
	{
		menu.removeAllChildItems();

		Category[] categories = CategoryManager.getCategories();

		Arrays.sort( categories );

		MenuItem m;

		if ( categories.length > 0 ){

			String	assigned_category = subs.getCategory();

			final Category uncat = CategoryManager.getCategory( Category.TYPE_UNCATEGORIZED );

			if ( uncat != null ){

				m = menu_manager.addMenuItem( menu, uncat.getName());

				m.setStyle( MenuItem.STYLE_RADIO );

				m.setData(Boolean.valueOf(assigned_category == null));

				m.addListener(
					new MenuItemListener()
					{
						@Override
						public void
						selected(
							MenuItem			menu,
							Object 				target )
						{
							assignSelectedToCategory( subs, uncat );
						}
					});


				m = menu_manager.addMenuItem( menu, "sep1" );

				m.setStyle( MenuItem.STYLE_SEPARATOR );
			}

			for ( int i=0; i<categories.length; i++ ){

				final Category cat = categories[i];

				if ( cat.getType() == Category.TYPE_USER) {

					m = menu_manager.addMenuItem( menu, "!" + cat.getName() + "!" );

					m.setStyle( MenuItem.STYLE_RADIO );

					m.setData(Boolean.valueOf(assigned_category != null && assigned_category.equals(cat.getName())));

					TagUIUtils.setMenuIcon( m, cat );
							
					m.addListener(
						new MenuItemListener()
						{
							@Override
							public void
							selected(
								MenuItem			menu,
								Object 				target )
							{
								assignSelectedToCategory( subs, cat );
							}
						});
				}
			}

			m = menu_manager.addMenuItem( menu, "sep2" );

			m.setStyle( MenuItem.STYLE_SEPARATOR );
		}

		m = menu_manager.addMenuItem( menu, "MyTorrentsView.menu.setCategory.add" );

		m.addListener(
				new MenuItemListener()
				{
					@Override
					public void
					selected(
						MenuItem			menu,
						Object 				target )
					{
						addCategory( subs );
					}
				});

	}

	private static void
	addCategory(
		Subscription			subs )
	{
		CategoryUIUtils.showCreateCategoryDialog(new TagReturner() {
			@Override
			public void returnedTags(Tag[] tags) {
				if (tags.length == 1 && tags[0] instanceof Category) {
					assignSelectedToCategory(subs, (Category) tags[0]);
				}
			}
		});
	}

	private static void
	assignSelectedToCategory(
		Subscription		subs,
		Category 			category )
	{
		if ( category.getType() == Category.TYPE_UNCATEGORIZED ){

			subs.setCategory( null );

		}else{

			subs.setCategory( category.getName());
		}
	}

	private static void
	addTagSubMenu(
		MenuManager				menu_manager,
		MenuItem				menu,
		final Subscription		subs )
	{
		menu.removeAllChildItems();

		TagManager tm = TagManagerFactory.getTagManager();

		List<Tag> tags = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTags();

		tags = TagUtils.sortTags( tags );

		long	tag_id = subs.getTagID();

		Tag assigned_tag = tm.lookupTagByUID( tag_id );

		MenuItem m = menu_manager.addMenuItem( menu, "label.no.tag" );

		m.setStyle( MenuItem.STYLE_RADIO );

		m.setData(Boolean.valueOf(assigned_tag == null));

		m.addListener(
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					MenuItem			menu,
					Object 				target )
				{
					subs.setTagID( -1 );
				}
			});


		m = menu_manager.addMenuItem( menu, "sep1" );

		m.setStyle( MenuItem.STYLE_SEPARATOR );


		List<String>	menu_names 		= new ArrayList<>();
		Map<String,Tag>	menu_name_map 	= new IdentityHashMap<>();

		for ( Tag t: tags ){

			if ( !t.isTagAuto()[0]){

				String name = t.getTagName( true );

				menu_names.add( name );
				menu_name_map.put( name, t );
			}
		}

		List<Object>	menu_structure = MenuBuildUtils.splitLongMenuListIntoHierarchy( menu_names, TagUIUtils.MAX_TOP_LEVEL_TAGS_IN_MENU );

		for ( Object obj: menu_structure ){

			List<Tag>	bucket_tags = new ArrayList<>();

			MenuItem parent_menu;

			if ( obj instanceof String ){

				parent_menu = menu;

				bucket_tags.add( menu_name_map.get((String)obj));

			}else{

				Object[]	entry = (Object[])obj;

				List<String>	tag_names = (List<String>)entry[1];

				boolean	has_selected = false;

				for ( String name: tag_names ){

					Tag tag = menu_name_map.get( name );

					bucket_tags.add( tag );

					if ( assigned_tag == tag ){

						has_selected = true;
					}
				}

				parent_menu = menu_manager.addMenuItem (menu, "!" + (String)entry[0] + (has_selected?" (*)":"") + "!" );

				parent_menu.setStyle( MenuItem.STYLE_MENU );
			}

			for ( final Tag tag: bucket_tags ){

				m = menu_manager.addMenuItem( parent_menu, tag.getTagName( false ));

				m.setStyle( MenuItem.STYLE_RADIO );

				m.setData(Boolean.valueOf(assigned_tag == tag));

				TagUIUtils.setMenuIcon( m, tag );
				
				m.addListener(
					new MenuItemListener()
					{
						@Override
						public void
						selected(
							MenuItem			menu,
							Object 				target )
						{
							subs.setTagID( tag.getTagUID());
						}
					});
			}
		}

		m = menu_manager.addMenuItem( menu, "sep2" );

		m.setStyle( MenuItem.STYLE_SEPARATOR );

		m = menu_manager.addMenuItem( menu, "label.add.tag" );

		m.addListener(
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					MenuItem			menu,
					Object 				target )
				{
					addTag( subs );
				}
			});
	}

	private static void
	addTag(
		final Subscription			subs )
	{
		TagUIUtilsV3.showCreateTagDialog(new UIFunctions.TagReturner() {
			@Override
			public void returnedTags(Tag[] tags) {
				if ( tags != null ){
					for (Tag new_tag : tags) {
						subs.setTagID( new_tag.getTagUID());
					}
				}
			}
		});
	}


	protected static void
	export(
		final Subscription			subs )
	{
		Utils.execSWTThread(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					FileDialog dialog =
						new FileDialog( Utils.findAnyShell(), SWT.SYSTEM_MODAL | SWT.SAVE );

					dialog.setFilterPath( TorrentOpener.getFilterPathData() );

					dialog.setText(MessageText.getString("subscript.export.select.template.file"));

					dialog.setFilterExtensions(VuzeFileHandler.getVuzeFileFilterExtensions());

					dialog.setFilterNames(VuzeFileHandler.getVuzeFileFilterExtensions());

					String path = TorrentOpener.setFilterPathData( dialog.open());

					if ( path != null ){

						if ( !VuzeFileHandler.isAcceptedVuzeFileName( path )){

							path = VuzeFileHandler.getVuzeFileName( path );
						}

						try{
							VuzeFile vf = subs.getVuzeFile();

							List<Subscription> deps = SubscriptionUtils.getDependsOnClosure(subs);
							
							if ( !deps.isEmpty()){
								
								for ( Subscription dep: deps ){
									
									vf.addComponents( dep.getVuzeFile());
								}
							}
							vf.write( new File( path ));

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			});
	}

	protected static void
	removeWithConfirm(
		final Subscription		subs )
	{
		MessageBoxShell mb =
			new MessageBoxShell(
				MessageText.getString("message.confirm.delete.title"),
				MessageText.getString("message.confirm.delete.text",
						new String[] {
							subs.getName()
						}),
				new String[] {
					MessageText.getString("Button.yes"),
					MessageText.getString("Button.no")
				},
				1 );

		mb.open(new UserPrompterResultListener() {
			@Override
			public void prompterClosed(int result) {
				if (result == 0) {
					subs.remove();
				}
			}
		});
	}

	protected static void
	showProperties(
		Subscription			subs )
	{
		SubscriptionHistory history = subs.getHistory();

		SimpleDateFormat df = new SimpleDateFormat();

		String last_error = history.getLastError();

		if ( last_error == null ){
			last_error = "";
		}

		String	engine_str;
		String	auth_str	= String.valueOf(false);

		try{
			Engine engine = subs.getEngine();

			engine_str = engine.getNameEx();

			if ( engine instanceof WebEngine ){

				WebEngine web_engine = (WebEngine)engine;

				if ( web_engine.isNeedsAuth()){

					auth_str = String.valueOf(true) + ": cookies=" + toString( web_engine.getRequiredCookies());
				}
			}

			engine_str +=  ", eid=" + engine.getId();

		}catch( Throwable e ){

			engine_str 	= "Unknown";
			auth_str	= "";
		}

		String[] keys = {
				"subs.prop.enabled",
				"subs.prop.is_public",
				"subs.prop.is_auto",
				"subs.prop.is_auto_ok",
				"subs.prop.is_dl_anon",
				"subs.prop.update_period",
				"subs.prop.last_scan",
				"subs.prop.last_result",
				"subs.prop.next_scan",
				"subs.prop.last_error",
				"subs.prop.num_read",
				"subs.prop.num_unread",
				"label.max.results",
				"subs.prop.assoc",
				"subs.prop.version",
				"subs.prop.high_version",
				"subscriptions.listwindow.popularity",
				"subs.prop.template",
				"subs.prop.auth",
				"TableColumn.header.category",
				"TableColumn.header.tag.name",
				"subs.prop.query"
			};

		String	category_str;

		String category = subs.getCategory();

		if ( category == null ){

			category_str = MessageText.getString( "Categories.uncategorized" );

		}else{

			category_str = category;
		}

		Tag tag = TagManagerFactory.getTagManager().lookupTagByUID( subs.getTagID() );

		String tag_str = tag==null?"":tag.getTagName( true );

		int	 check_freq			= history.getCheckFrequencyMins();
		long last_new_result 	= history.getLastNewResultTime();
		long next_scan 			= history.getNextScanTime();

		int max_results = history.getMaxNonDeletedResults();

		if ( max_results < 0 ){

			SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

			max_results = subs_man.getMaxNonDeletedResults();
		}

		String max_results_str = (max_results==0?MessageText.getString( "ConfigView.unlimited" ):String.valueOf( max_results ));

		String[] values = {
				String.valueOf( history.isEnabled()),
				String.valueOf( subs.isPublic()) + "/" + (!subs.isAnonymous()),
				String.valueOf( history.isAutoDownload()),
				String.valueOf( subs.isAutoDownloadSupported()),
				String.valueOf( history.getDownloadNetworks() != null ),
				(check_freq==Integer.MAX_VALUE?"":(String.valueOf( history.getCheckFrequencyMins() + " " + MessageText.getString( "ConfigView.text.minutes")))),
				df.format(new Date( history.getLastScanTime())),
				( last_new_result==0?"":df.format(new Date( last_new_result ))),
				( next_scan == Long.MAX_VALUE?"":df.format(new Date( next_scan ))),
				(last_error.length()==0?MessageText.getString("label.none"):last_error),
				String.valueOf( history.getNumRead()),
				String.valueOf( history.getNumUnread()),
				max_results_str,
				String.valueOf( subs.getAssociationCount()),
				String.valueOf( subs.getVersion()),
				subs.getHighestVersion() > subs.getVersion()?String.valueOf( subs.getHighestVersion()):null,
				subs.getCachedPopularity()<=1?"-":String.valueOf( subs.getCachedPopularity()),
				engine_str + ", sid=" + subs.getID(),
				auth_str,
				category_str,
				tag_str,
				subs.getQueryKey(),
			};

		final PropertiesWindow pw = new PropertiesWindow( subs.getName(), keys, values );

		try{
			// kick off a popularity update

			subs.getPopularity(
				new SubscriptionPopularityListener()
				{
					@Override
					public void
					gotPopularity(
						long						popularity )
					{
						pw.updateProperty(
							"subscriptions.listwindow.popularity",
							String.valueOf( popularity ));
					}

					@Override
					public void
					failed(
						SubscriptionException		error )
					{
					}
				});

		}catch( Throwable e ){
		}
	}

	private static String
	toString(
		String[]	strs )
	{
		String	res = "";

		for(int i=0;i<strs.length;i++){
			res += (i==0?"":",") + strs[i];
		}

		return( res );
	}

	private abstract static class SubsMenuItemListener implements MenuItemListener {
		@Override
		public final void selected(MenuItem menu, Object target) {
			if (target instanceof MdiEntry) {
				MdiEntry info = (MdiEntry) target;
				Subscription subs = (Subscription) info.getDataSource();

				try {
					selected( subs);
				} catch (Throwable t) {
					Debug.out(t);
				}
			}else if ( target instanceof TableRow ){

				Object ds = ((TableRow)target).getDataSource();

				if ( ds instanceof Subscription ){

					try {
						selected((Subscription)ds);

					} catch (Throwable t) {
						Debug.out(t);
					}
				}
			}else if ( target instanceof TableRow[] ){
				TableRow[] rows = (TableRow[] )target;
				List<Subscription>	subs = new ArrayList<>();
				for ( TableRow row: rows ){
					Object ds = row.getDataSource();

					if ( ds instanceof Subscription ){
						subs.add((Subscription)ds);
					}
				}
				selected(subs.toArray( new Subscription[0]));
			}else if ( target instanceof Subscription ){
				selected((Subscription)target);
			}else{
				Debug.out( "target " + target + " not handled" );
			}
		}

		public void selected(Subscription subs){ Debug.out( "Missing override?");}
		public void selected(Subscription subs[]){
			for ( Subscription s: subs ){
				selected( s );
			}
		}
	}

	protected static class
	SubsParentView
		implements 	ViewTitleInfo, UISWTViewEventListener
	{
		private UISWTView 	swtView;
		private String 		title;

		private Composite	parent_composite;
		private Composite	composite;

		private
		SubsParentView(
			String		_title )
		{
			title = _title;
		}

		public String
		getTitle()
		{
			return( title );
		}

		@Override
		public Object
		getTitleInfoProperty(
			int propertyID )
		{
			if ( propertyID == TITLE_TEXT ){

				return( getTitle());
			}

			return( null );
		}

		public void
		initialize(
			Composite _parent_composite )
		{
			parent_composite	= _parent_composite;

			composite = new Composite( parent_composite, SWT.NULL );
		}

		public Composite
		getComposite()
		{
			return( composite );
		}

		private void
		delete()
		{
		}

		@Override
		public boolean eventOccurred(UISWTViewEvent event) {
		    switch (event.getType()) {
		      case UISWTViewEvent.TYPE_CREATE:
		      	swtView = (UISWTView)event.getData();
		      	swtView.setTitle(getTitle());
		        break;

		      case UISWTViewEvent.TYPE_DESTROY:
		        delete();
		        break;

		      case UISWTViewEvent.TYPE_INITIALIZE:
		        initialize((Composite)event.getData());
		        break;

		      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
		      	Messages.updateLanguageForControl(getComposite());
		      	swtView.setTitle(getTitle());
		        break;

		      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
		        break;

		      case UISWTViewEvent.TYPE_FOCUSGAINED:
		      	break;

		      case UISWTViewEvent.TYPE_REFRESH:
		        break;
		    }

		    return true;
		  }
	}
}
