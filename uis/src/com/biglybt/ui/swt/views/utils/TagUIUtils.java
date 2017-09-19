/*
 * Created on Nov 15, 2010
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.utils;

import java.io.File;
import java.util.*;
import java.util.List;

import com.biglybt.pif.ui.UIInstance;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginBuddy;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.mainwindow.MenuFactory;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.maketorrent.MultiTrackerEditor;
import com.biglybt.ui.swt.maketorrent.TrackerEditorListener;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.views.FilesView;
import com.biglybt.ui.swt.views.ViewUtils;
import com.biglybt.ui.swt.views.ViewUtils.SpeedAdapter;
import com.biglybt.ui.swt.views.stats.StatsView;

/**
 * @author TuxPaper
 * @created Nov 15, 2010
 *
 */
public class TagUIUtils
{
	public static final int MAX_TOP_LEVEL_TAGS_IN_MENU	= 20;

	public static String
	getChatKey(
		Tag		tag )
	{
		return( "Tag: " + tag.getTagName( true ));
	}

	public static void
	setupSideBarMenus(
		final MenuManager	menuManager )
	{
		com.biglybt.pif.ui.menus.MenuItem menuItem = menuManager.addMenuItem("sidebar."
				+ MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
				"ConfigView.section.style.TagInSidebar");
		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		menuItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK);

		menuItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
			@Override
			public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
				boolean b = COConfigurationManager.getBooleanParameter("Library.TagInSideBar");
				COConfigurationManager.setParameter("Library.TagInSideBar", !b);
			}
		});

		menuItem.addFillListener(new com.biglybt.pif.ui.menus.MenuItemFillListener() {
			@Override
			public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {
				menu.setData(Boolean.valueOf(COConfigurationManager.getBooleanParameter("Library.TagInSideBar")));
			}
		});

			// tag options

		menuItem = menuManager.addMenuItem("sidebar."
				+ MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
				"label.tags");
		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		menuItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);

		menuItem.addFillListener(new com.biglybt.pif.ui.menus.MenuItemFillListener() {
			@Override
			public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {
				menu.removeAllChildItems();


					// manual

				final TagType manual_tt = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL );

				com.biglybt.pif.ui.menus.MenuItem menuItem = menuManager.addMenuItem( menu, manual_tt.getTagTypeName( false ));

				menuItem.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU );

				menuItem.addFillListener(new com.biglybt.pif.ui.menus.MenuItemFillListener() {
					@Override
					public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {
						menu.removeAllChildItems();

						final List<Tag> all_tags = manual_tt.getTags();

						List<String>	menu_names 		= new ArrayList<>();
						Map<String,Tag>	menu_name_map 	= new IdentityHashMap<>();

						boolean	all_visible 	= true;
						boolean all_invisible 	= true;

						boolean	has_ut	= false;

						for ( Tag t: all_tags ){

							String name = t.getTagName( true );

							menu_names.add( name );
							menu_name_map.put( name, t );

							if ( t.isVisible()){
								all_invisible = false;
							}else{
								all_visible = false;
							}

							TagFeatureProperties props = (TagFeatureProperties)t;

							TagProperty prop = props.getProperty( TagFeatureProperties.PR_UNTAGGED );

							if ( prop != null ){

								Boolean b = prop.getBoolean();

								if ( b != null && b ){

									has_ut = true;
								}
							}
						}

						com.biglybt.pif.ui.menus.MenuItem showAllItem = menuManager.addMenuItem( menu, "label.show.all" );
						showAllItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

						showAllItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
							@Override
							public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
								for ( Tag t: all_tags ){
									t.setVisible( true );
								}
							}
						});

						com.biglybt.pif.ui.menus.MenuItem hideAllItem = menuManager.addMenuItem( menu, "popup.error.hideall" );
						hideAllItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

						hideAllItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
							@Override
							public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
								for ( Tag t: all_tags ){
									t.setVisible( false );
								}
							}
						});

						com.biglybt.pif.ui.menus.MenuItem sepItem = menuManager.addMenuItem( menu, "sepm" );

						sepItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );

						showAllItem.setEnabled( !all_visible );
						hideAllItem.setEnabled( !all_invisible );

						List<Object>	menu_structure = MenuBuildUtils.splitLongMenuListIntoHierarchy( menu_names, TagUIUtils.MAX_TOP_LEVEL_TAGS_IN_MENU );

						for ( Object obj: menu_structure ){

							List<Tag>	bucket_tags = new ArrayList<>();

							com.biglybt.pif.ui.menus.MenuItem parent_menu;

							if ( obj instanceof String ){

								parent_menu = menu;

								bucket_tags.add( menu_name_map.get((String)obj));

							}else{

								Object[]	entry = (Object[])obj;

								List<String>	tag_names = (List<String>)entry[1];

								boolean	sub_all_visible 	= true;
								boolean sub_some_visible	= false;

								for ( String name: tag_names ){

									Tag tag = menu_name_map.get( name );

									if ( tag.isVisible()){

										sub_some_visible = true;

									}else{

										sub_all_visible = false;
									}

									bucket_tags.add( tag );
								}

								String mod;

								if ( sub_all_visible ){

									mod = " (*)";

								}else if ( sub_some_visible ){

									mod = " (+)";

								}else{

									mod = "";
								}

								parent_menu = menuManager.addMenuItem (menu, "!" + (String)entry[0] + mod + "!" );

								parent_menu.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU );


							}

							for ( final Tag tag: bucket_tags ){

								com.biglybt.pif.ui.menus.MenuItem m = menuManager.addMenuItem( parent_menu, tag.getTagName( false ));

								m.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK );

								m.setData(Boolean.valueOf(tag.isVisible()));

								m.addListener(
									new MenuItemListener()
									{
										@Override
										public void
										selected(
											com.biglybt.pif.ui.menus.MenuItem			menu,
											Object 												target )
										{
											tag.setVisible( !tag.isVisible());
										}
									});
							}
						}

						if ( !has_ut ){

							sepItem = menuManager.addMenuItem( menu, "sepu" );

							sepItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );


							com.biglybt.pif.ui.menus.MenuItem m = menuManager.addMenuItem( menu, "label.untagged" );

							m.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

							m.addListener(
								new MenuItemListener()
								{
									@Override
									public void
									selected(
										com.biglybt.pif.ui.menus.MenuItem			menu,
										Object 												target )
									{
										try{
											String tag_name = MessageText.getString( "label.untagged" );

											Tag ut_tag = manual_tt.getTag( tag_name, true );

											if ( ut_tag == null ){


												ut_tag = manual_tt.createTag( tag_name, true );
											}

											TagFeatureProperties tp = (TagFeatureProperties)ut_tag;

											tp.getProperty( TagFeatureProperties.PR_UNTAGGED ).setBoolean( true );

										}catch( TagException e ){

											Debug.out( e );
										}
									}
								});
						}
					}
				});

				menuItem = menuManager.addMenuItem( menu, "label.add.tag");

				menuItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
					@Override
					public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
						createManualTag(null);
					}
				});

				com.biglybt.pif.ui.menus.MenuItem sepItem = menuManager.addMenuItem( menu, "sep1" );

				sepItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );


					// auto

				menuItem = menuManager.addMenuItem( menu, "wizard.maketorrent.auto" );

				menuItem.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU );

				menuItem.addFillListener(new com.biglybt.pif.ui.menus.MenuItemFillListener() {
					@Override
					public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {
						menu.removeAllChildItems();

							// autos


						List<TagType> tag_types = TagManagerFactory.getTagManager().getTagTypes();

						for ( final TagType tag_type: tag_types ){

							if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_CATEGORY ){

								continue;
							}

							if ( !tag_type.isTagTypeAuto()){

								continue;
							}

							if ( tag_type.getTags().size() == 0 ){

								continue;
							}

							com.biglybt.pif.ui.menus.MenuItem menuItem = menuManager.addMenuItem( menu, tag_type.getTagTypeName( false ));

							menuItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);

							menuItem.addFillListener(new com.biglybt.pif.ui.menus.MenuItemFillListener() {
								@Override
								public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {
									menu.removeAllChildItems();

									final List<Tag> tags = tag_type.getTags();

									com.biglybt.pif.ui.menus.MenuItem showAllItem = menuManager.addMenuItem( menu, "label.show.all" );
									showAllItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

									showAllItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
										@Override
										public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
											for ( Tag t: tags ){
												t.setVisible( true );
											}
										}
									});

									com.biglybt.pif.ui.menus.MenuItem hideAllItem = menuManager.addMenuItem( menu, "popup.error.hideall" );
									hideAllItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

									hideAllItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
										@Override
										public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
											for ( Tag t: tags ){
												t.setVisible( false );
											}
										}
									});

									boolean	all_visible 	= true;
									boolean all_invisible 	= true;

									for ( Tag t: tags ){
										if ( t.isVisible()){
											all_invisible = false;
										}else{
											all_visible = false;
										}
									}

									showAllItem.setEnabled( !all_visible );
									hideAllItem.setEnabled( !all_invisible );

									com.biglybt.pif.ui.menus.MenuItem sepItem = menuManager.addMenuItem( menu, "sep2" );

									sepItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );

									for ( final Tag t: tags ){

										com.biglybt.pif.ui.menus.MenuItem  menuItem = menuManager.addMenuItem( menu, t.getTagName( false ));

										menuItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK );

										menuItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
											@Override
											public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
												t.setVisible( menu.isSelected());
											}
										});
										menuItem.addFillListener(new com.biglybt.pif.ui.menus.MenuItemFillListener() {
											@Override
											public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {
												menu.setData( t.isVisible());
											}
										});
									}
								}
							});
						}
					}
				});

				sepItem = menuManager.addMenuItem( menu, "sep3" );

				sepItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );


				menuItem = menuManager.addMenuItem( menu, "tag.show.stats");

				menuItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
					@Override
					public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
						UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
						uiFunctions.getMDI().showEntryByID(StatsView.VIEW_ID, "TagStatsView");

					}
				});

				menuItem = menuManager.addMenuItem( menu, "tag.show.overview");

				menuItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
					@Override
					public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
						UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
						uiFunctions.getMDI().showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_TAGS);
					}
				});
			}
		});

		CoreFactory.addCoreRunningListener(
			new CoreRunningListener()
			{
				@Override
				public void
				coreRunning(
					Core core)
				{
					checkTagSharing( true );
				}
			});

	}

	public static void
	checkTagSharing(
		boolean		start_of_day )
	{
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

		if ( uiFunctions != null ){

			TagManager tm = TagManagerFactory.getTagManager();

			if ( start_of_day ){

				if ( COConfigurationManager.getBooleanParameter( "tag.sharing.default.checked", false )){

					return;
				}

				COConfigurationManager.setParameter( "tag.sharing.default.checked", true );

				List<TagType> tag_types = tm.getTagTypes();

				boolean	prompt_required = false;

				for ( TagType tag_type: tag_types ){

					List<Tag> tags = tag_type.getTags();

					for ( Tag tag: tags ){

						if ( tag.isPublic()){

							prompt_required = true;
						}
					}
				}

				if ( !prompt_required ){

					return;
				}
			}

			String title = MessageText.getString("tag.sharing.enable.title");

			String text = MessageText.getString("tag.sharing.enable.text" );

			UIFunctionsUserPrompter prompter = uiFunctions.getUserPrompter(title, text, new String[] {
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no")
			}, 0);

			prompter.setRemember( "tag.share.default", true,
					MessageText.getString("MessageBoxWindow.nomoreprompting"));

			prompter.setAutoCloseInMS(0);

			prompter.open(null);

			boolean	share = prompter.waitUntilClosed() == 0;

			tm.setTagPublicDefault( share );
		}
	}

	public static void
	createManualTag(
		UIFunctions.TagReturner tagReturner)
	{
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null) {
			uiFunctions.showCreateTagDialog(tagReturner);
		}
	}

	public static void
	createSideBarMenuItems(
		final Menu menu, final Tag tag )
	{
	    int userMode = COConfigurationManager.getIntParameter("User Mode");

		final TagType	tag_type = tag.getTagType();

		boolean	needs_separator_next = false;

		int countBefore = menu.getItemCount();

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_RATE_LIMIT )) {
			createTF_RateLimitMenuItems(menu, tag, tag_type, userMode);
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_RUN_STATE )) {
			createTF_RunState(menu, tag);
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )) {
			createTF_FileLocationMenuItems(menu, tag);
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_EXEC_ON_ASSIGN )){

			final TagFeatureExecOnAssign	tf_eoa = (TagFeatureExecOnAssign)tag;

			int	supported_actions = tf_eoa.getSupportedActions();

			if ( supported_actions != TagFeatureExecOnAssign.ACTION_NONE ){

				final Menu eoa_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

				MenuItem eoa_item = new MenuItem( menu, SWT.CASCADE);

				Messages.setLanguageText( eoa_item, "label.exec.on.assign" );

				eoa_item.setMenu( eoa_menu );

				int[]	action_ids =
					{ 	TagFeatureExecOnAssign.ACTION_DESTROY,
						TagFeatureExecOnAssign.ACTION_START,
						TagFeatureExecOnAssign.ACTION_FORCE_START,
						TagFeatureExecOnAssign.ACTION_NOT_FORCE_START,
						TagFeatureExecOnAssign.ACTION_STOP,
						TagFeatureExecOnAssign.ACTION_PAUSE,
						TagFeatureExecOnAssign.ACTION_RESUME,
						TagFeatureExecOnAssign.ACTION_SCRIPT };


				String[] action_keys =
					{ 	"v3.MainWindow.button.delete",
						"v3.MainWindow.button.start",
						"v3.MainWindow.button.forcestart",
						"v3.MainWindow.button.notforcestart",
						"v3.MainWindow.button.stop",
						"v3.MainWindow.button.pause",
						"v3.MainWindow.button.resume",
						"label.script" };

				for ( int i=0;i<action_ids.length;i++ ){

					final int action_id = action_ids[i];

					if ( tf_eoa.supportsAction( action_id )){

						if ( action_id == TagFeatureExecOnAssign.ACTION_SCRIPT ){

							final MenuItem action_item = new MenuItem( eoa_menu, SWT.PUSH);

							String script = tf_eoa.getActionScript();

							if ( script.length() > 23 ){
								script = script.substring( 0, 20) + "...";
							}

							String msg = MessageText.getString( action_keys[i] );

							if ( script.length() > 0 ){

								msg += ": " + script;
							}

							msg += "...";

							action_item.setText( msg );

							action_item.addListener( SWT.Selection, new Listener(){
								@Override
								public void
								handleEvent(Event event)
								{
									String msg = MessageText.getString( "UpdateScript.message" );

									SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow( "UpdateScript.title", "!" + msg + "!" );

									entryWindow.setPreenteredText( tf_eoa.getActionScript(), false );
									entryWindow.selectPreenteredText( true );

									entryWindow.prompt(new UIInputReceiverListener() {
										@Override
										public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
											if ( entryWindow.hasSubmittedInput()){

												String text = entryWindow.getSubmittedInput().trim();

												tf_eoa.setActionScript( text );
											}
										}
									});
								}});

						}else{

							final MenuItem action_item = new MenuItem( eoa_menu, SWT.CHECK);

							Messages.setLanguageText( action_item, action_keys[i] );

							action_item.setSelection( tf_eoa.isActionEnabled( action_id ));

							action_item.addListener( SWT.Selection, new Listener(){
									@Override
									public void
									handleEvent(Event event)
									{
										tf_eoa.setActionEnabled( action_id, action_item.getSelection());
									}
								});
						}
					}
				}
			}
		}

		// options

		if ( tag instanceof TagDownload ){

			needs_separator_next = true;

			MenuItem itemOptions = new MenuItem(menu, SWT.PUSH);

			final Set<DownloadManager> dms = ((TagDownload)tag).getTaggedDownloads();

			Messages.setLanguageText(itemOptions, "cat.options");
			itemOptions.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
					if (uiFunctions != null) {
						uiFunctions.getMDI().showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_OPTIONS,
								dms.toArray(new DownloadManager[dms.size()]));
					}
				}
			});

			if (dms.size() == 0) {

				itemOptions.setEnabled(false);
			}
		}

		if ( userMode > 0 ){

			if ( tag_type.hasTagTypeFeature( TagFeature.TF_PROPERTIES )){

				createTFProperitesMenuItems(menu, tag);
			}
		}

		if (menu.getItemCount() > countBefore) {
			needs_separator_next = true;
		}

		if ( needs_separator_next ){

			new MenuItem( menu, SWT.SEPARATOR);

			needs_separator_next = false;
		}


		/* Seldom Used: Can be set in Tags Overview
		// sharing
		if ( tag.canBePublic()){

			needs_separator_next = true;

			final MenuItem itemPublic = new MenuItem(menu, SWT.CHECK );

			itemPublic.setSelection( tag.isPublic());

			Messages.setLanguageText(itemPublic, "tag.share");

			itemPublic.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {

					tag.setPublic( itemPublic.getSelection());
				}});
		}
		*/

		if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

			needs_separator_next = true;

			MenuItem search = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(search, "tag.search");
			search.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event){
					UIFunctionsManager.getUIFunctions().doSearch( "tag:" + tag.getTagName( true ));
				}});
		}

			// share with friends
		addShareWithFriendsMenuItems(menu, tag, tag_type);


			// rss feed

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_RSS_FEED )) {

			final TagFeatureRSSFeed tfrss = (TagFeatureRSSFeed)tag;

			// rss feed

			final MenuItem rssOption = new MenuItem(menu, SWT.CHECK );

			rssOption.setSelection( tfrss.isTagRSSFeedEnabled());

			Messages.setLanguageText(rssOption, "cat.rss.gen");
			rssOption.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					boolean set = rssOption.getSelection();
					tfrss.setTagRSSFeedEnabled( set );
				}
			});
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_XCODE )) {
			createXCodeMenuItems(menu, tag);
		}

		needs_separator_next = true;

		if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

			MenuBuildUtils.addChatMenu( menu, "label.chat", getChatKey( tag ));
		}

		MenuItem itemShowStats = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemShowStats, "tag.show.stats");
		itemShowStats.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event ){
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				uiFunctions.getMDI().loadEntryByID(StatsView.VIEW_ID, true, false, "TagStatsView");
			}
		});

		if ( tag.getTaggableTypes() == Taggable.TT_DOWNLOAD ){

			MenuItem itemShowFiles = new MenuItem(menu, SWT.PUSH);

			Messages.setLanguageText(itemShowFiles, "menu.show.files");
			itemShowFiles.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event ){
					showFilesView( (TagDownload)tag );
				}
			});
		}

		if ( needs_separator_next ){

			new MenuItem( menu, SWT.SEPARATOR);

			needs_separator_next = false;
		}

		boolean	auto = tag_type.isTagTypeAuto();

		boolean	closable = auto;

		if ( tag.getTaggableTypes() == Taggable.TT_DOWNLOAD ){

			closable = true;	// extended closable tags to include manual ones due to user request
		}

		Menu[] menuShowHide = { null };

		if ( closable ){
			createCloseableMenuItems(menu, tag, tag_type, menuShowHide, needs_separator_next);
		}

		if ( !auto ){
			createNonAutoMenuItems(menu, tag, tag_type, menuShowHide);
		}

		MenuItem menuSettings = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(menuSettings, "TagSettingsView.title");
		menuSettings.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				uiFunctions.getMDI().showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_TAGS, tag);
			}
		});

		com.biglybt.pif.ui.menus.MenuItem[] items = MenuItemManager.getInstance().getAllAsArray(
				MenuManager.MENU_TAG_CONTEXT);

		if (items.length > 0) {
			MenuFactory.addSeparatorMenuItem(menu);

			// TODO: Don't send Tag.. send a yet-to-be-created plugin interface version of Tag
			MenuBuildUtils.addPluginMenuItems(items, menu, true, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(new Tag[] { tag }));
		}
	}

	private static void createTF_RunState(Menu menu, Tag tag) {

		final TagFeatureRunState	tf_run_state = (TagFeatureRunState)tag;

		int caps = tf_run_state.getRunStateCapabilities();

		int[] op_set = {
				TagFeatureRunState.RSC_START, TagFeatureRunState.RSC_STOP,
				TagFeatureRunState.RSC_PAUSE, TagFeatureRunState.RSC_RESUME };

		boolean[] can_ops_set = tf_run_state.getPerformableOperations( op_set );

		if ((caps & TagFeatureRunState.RSC_START ) != 0 ){

			final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemOp, "MyTorrentsView.menu.queue");
			Utils.setMenuItemImage(itemOp, "start");
			itemOp.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tf_run_state.performOperation( TagFeatureRunState.RSC_START );
				}
			});
			itemOp.setEnabled(can_ops_set[0]);
		}

		if ((caps & TagFeatureRunState.RSC_STOP ) != 0 ){

			final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemOp, "MyTorrentsView.menu.stop");
			Utils.setMenuItemImage(itemOp, "stop");
			itemOp.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tf_run_state.performOperation( TagFeatureRunState.RSC_STOP );
				}
			});
			itemOp.setEnabled(can_ops_set[1]);
		}

		if ((caps & TagFeatureRunState.RSC_PAUSE ) != 0 ){

			final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemOp, "v3.MainWindow.button.pause");
			Utils.setMenuItemImage(itemOp, "pause");
			itemOp.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tf_run_state.performOperation( TagFeatureRunState.RSC_PAUSE );
				}
			});
			itemOp.setEnabled(can_ops_set[2]);
		}

		if ((caps & TagFeatureRunState.RSC_RESUME ) != 0 ){

			final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemOp, "v3.MainWindow.button.resume");
			Utils.setMenuItemImage(itemOp, "start");
			itemOp.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tf_run_state.performOperation( TagFeatureRunState.RSC_RESUME );
				}
			});
			itemOp.setEnabled(can_ops_set[3]);
		}
	}

	private static void
		createTF_RateLimitMenuItems(
				Menu menu,
				Tag tag,
				TagType tag_type,
				int userMode)
	{

		final TagFeatureRateLimit	tf_rate_limit = (TagFeatureRateLimit)tag;

		boolean	has_up 		= tf_rate_limit.supportsTagUploadLimit();
		boolean	has_down 	= tf_rate_limit.supportsTagDownloadLimit();

		if ( has_up || has_down ){

			long kInB = DisplayFormatters.getKinB();

			long maxDownload = COConfigurationManager.getIntParameter(
					"Max Download Speed KBs", 0) * kInB;
			long maxUpload = COConfigurationManager.getIntParameter(
					"Max Upload Speed KBs", 0) * kInB;

			int down_speed 	= tf_rate_limit.getTagDownloadLimit();
			int up_speed 	= tf_rate_limit.getTagUploadLimit();

			Map<String,Object> menu_properties = new HashMap<>();

			if ( tag_type.getTagType() == TagType.TT_PEER_IPSET || tag_type.getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

				if ( has_up ){
					menu_properties.put( ViewUtils.SM_PROP_PERMIT_UPLOAD_DISABLE, true );
				}
				if ( has_down ){
					menu_properties.put( ViewUtils.SM_PROP_PERMIT_DOWNLOAD_DISABLE, true );
				}
			}

			ViewUtils.addSpeedMenu(
					menu.getShell(), menu, has_up, has_down, true, true,
					down_speed == -1, down_speed == 0, down_speed, down_speed, maxDownload,
					up_speed == -1,	up_speed == 0, up_speed, up_speed, maxUpload,
					1,	menu_properties,
					new SpeedAdapter() {
						@Override
						public void setDownSpeed(int val) {
							tf_rate_limit.setTagDownloadLimit(val);
						}

						@Override
						public void setUpSpeed(int val) {
							tf_rate_limit.setTagUploadLimit(val);
						}
					});
		}

		if ( userMode > 0 ){

			if ( tf_rate_limit.getTagUploadPriority() >= 0 ){


				final MenuItem upPriority = new MenuItem(menu, SWT.CHECK );

				upPriority.setSelection( tf_rate_limit.getTagUploadPriority() > 0 );

				Messages.setLanguageText(upPriority, "cat.upload.priority");
				upPriority.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						boolean set = upPriority.getSelection();
						tf_rate_limit.setTagUploadPriority( set?1:0 );
					}
				});
			}

			/* Usually set once: Can be set in Tags Overview*/
			if ( tf_rate_limit.getTagMinShareRatio() >= 0 ){

				MenuItem itemSR = new MenuItem(menu, SWT.PUSH);

				final String existing = String.valueOf( tf_rate_limit.getTagMinShareRatio()/1000.0f);

				Messages.setLanguageText(itemSR, "menu.min.share.ratio", new String[]{existing} );

				itemSR.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
								"min.sr.window.title", "min.sr.window.message");

						entryWindow.setPreenteredText( existing, false );
						entryWindow.selectPreenteredText( true );

						entryWindow.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver receiver) {
								if (!receiver.hasSubmittedInput()) {
									return;
								}

								try{
									String text = receiver.getSubmittedInput().trim();

									int	sr = 0;

									if ( text.length() > 0 ){

										try{
											float f = Float.parseFloat( text );

											sr = (int)(f * 1000 );

											if ( sr < 0 ){

												sr = 0;

											}else if ( sr == 0 && f > 0 ){

												sr = 1;
											}

										}catch( Throwable e ){

											Debug.out( e );
										}

										tf_rate_limit.setTagMinShareRatio( sr );

									}
								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						});
					}
				});
			}

			if ( tf_rate_limit.getTagMaxShareRatio() >= 0 ){

				MenuItem itemSR = new MenuItem(menu, SWT.PUSH);

				final String existing = String.valueOf( tf_rate_limit.getTagMaxShareRatio()/1000.0f);

				Messages.setLanguageText(itemSR, "menu.max.share.ratio", new String[]{existing} );

				itemSR.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
								"max.sr.window.title", "max.sr.window.message");

						entryWindow.setPreenteredText( existing, false );
						entryWindow.selectPreenteredText( true );

						entryWindow.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver receiver) {
								if (!receiver.hasSubmittedInput()) {
									return;
								}

								try{
									String text = receiver.getSubmittedInput().trim();

									int	sr = 0;

									if ( text.length() > 0 ){

										try{
											float f = Float.parseFloat( text );

											sr = (int)(f * 1000 );

											if ( sr < 0 ){

												sr = 0;

											}else if ( sr == 0 && f > 0 ){

												sr = 1;
											}

										}catch( Throwable e ){

											Debug.out( e );
										}

										tf_rate_limit.setTagMaxShareRatio( sr );

									}
								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						});

					}
				});
			}
			/**/
		}
	}

	private static void createTF_FileLocationMenuItems(Menu menu, final Tag tag) {

		final TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;

		if ( fl.supportsTagInitialSaveFolder() || fl.supportsTagMoveOnComplete() || fl.supportsTagCopyOnComplete()){

			Menu files_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

			MenuItem files_item = new MenuItem( menu, SWT.CASCADE);

			Messages.setLanguageText( files_item, "ConfigView.section.files" );

			files_item.setMenu( files_menu );

			if ( fl.supportsTagInitialSaveFolder()){

				final Menu moc_menu = new Menu( files_menu.getShell(), SWT.DROP_DOWN);

				MenuItem isl_item = new MenuItem( files_menu, SWT.CASCADE);

				Messages.setLanguageText( isl_item, "label.init.save.loc" );

				isl_item.setMenu( moc_menu );

				MenuItem clear_item = new MenuItem( moc_menu, SWT.CASCADE);

				Messages.setLanguageText( clear_item, "Button.clear" );

				clear_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						fl.setTagInitialSaveFolder( null );
					}});

					// apply

				final File existing = fl.getTagInitialSaveFolder();

				MenuItem apply_item = new MenuItem( moc_menu, SWT.CASCADE);

				Messages.setLanguageText( apply_item, "apply.to.current" );

				apply_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						applyLocationToCurrent( tag, existing, false );
					}});

				new MenuItem( moc_menu, SWT.SEPARATOR);

				if ( existing != null ){

					MenuItem current_item = new MenuItem( moc_menu, SWT.RADIO );
					current_item.setSelection( true );

					current_item.setText( existing.getAbsolutePath());

					new MenuItem( moc_menu, SWT.SEPARATOR);

				}else{

					apply_item.setEnabled( false );
					clear_item.setEnabled( false );
				}

				MenuItem set_item = new MenuItem( moc_menu, SWT.CASCADE);

				Messages.setLanguageText( set_item, "label.set" );

				set_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event){
						DirectoryDialog dd = new DirectoryDialog(moc_menu.getShell());

						dd.setFilterPath( TorrentOpener.getFilterPathData());

						dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

						String path = dd.open();

						if ( path != null ){

							TorrentOpener.setFilterPathData( path );

							fl.setTagInitialSaveFolder( new File( path ));
						}
					}});
			}

			if ( fl.supportsTagMoveOnComplete()){

				final Menu moc_menu = new Menu( files_menu.getShell(), SWT.DROP_DOWN);

				MenuItem moc_item = new MenuItem( files_menu, SWT.CASCADE);

				Messages.setLanguageText( moc_item, "label.move.on.comp" );

				moc_item.setMenu( moc_menu );

				MenuItem clear_item = new MenuItem( moc_menu, SWT.CASCADE);

				Messages.setLanguageText( clear_item, "Button.clear" );

				clear_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						fl.setTagMoveOnCompleteFolder( null );
					}});


					// apply

				final File existing = fl.getTagMoveOnCompleteFolder();

				MenuItem apply_item = new MenuItem( moc_menu, SWT.CASCADE);

				Messages.setLanguageText( apply_item, "apply.to.current" );

				apply_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						applyLocationToCurrent( tag, existing, true );
					}});

				new MenuItem( moc_menu, SWT.SEPARATOR);

				if ( existing != null ){

					MenuItem current_item = new MenuItem( moc_menu, SWT.RADIO );
					current_item.setSelection( true );

					current_item.setText( existing.getAbsolutePath());

					new MenuItem( moc_menu, SWT.SEPARATOR);

				}else{

					apply_item.setEnabled( false );
					clear_item.setEnabled( false );
				}

				MenuItem set_item = new MenuItem( moc_menu, SWT.CASCADE);

				Messages.setLanguageText( set_item, "label.set" );

				set_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event){
						DirectoryDialog dd = new DirectoryDialog(moc_menu.getShell());

						dd.setFilterPath( TorrentOpener.getFilterPathData());

						dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

						String path = dd.open();

						if ( path != null ){

							TorrentOpener.setFilterPathData( path );

							fl.setTagMoveOnCompleteFolder( new File( path ));
						}
					}});
			}

			if ( fl.supportsTagCopyOnComplete()){

				final Menu moc_menu = new Menu( files_menu.getShell(), SWT.DROP_DOWN);

				MenuItem moc_item = new MenuItem( files_menu, SWT.CASCADE);

				Messages.setLanguageText( moc_item, "label.copy.on.comp" );

				moc_item.setMenu( moc_menu );

				MenuItem clear_item = new MenuItem( moc_menu, SWT.CASCADE);

				Messages.setLanguageText( clear_item, "Button.clear" );

				clear_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						fl.setTagCopyOnCompleteFolder( null );
					}});

				new MenuItem( moc_menu, SWT.SEPARATOR);

				File existing = fl.getTagCopyOnCompleteFolder();

				if ( existing != null ){

					MenuItem current_item = new MenuItem( moc_menu, SWT.RADIO );
					current_item.setSelection( true );

					current_item.setText( existing.getAbsolutePath());

					new MenuItem( moc_menu, SWT.SEPARATOR);

				}else{

					clear_item.setEnabled( false );
				}

				MenuItem set_item = new MenuItem( moc_menu, SWT.CASCADE);

				Messages.setLanguageText( set_item, "label.set" );

				set_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event){
						DirectoryDialog dd = new DirectoryDialog(moc_menu.getShell());

						dd.setFilterPath( TorrentOpener.getFilterPathData());

						dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

						String path = dd.open();

						if ( path != null ){

							TorrentOpener.setFilterPathData( path );

							fl.setTagCopyOnCompleteFolder( new File( path ));
						}
					}});
			}
		}
	}

	private static void createTFProperitesMenuItems(Menu menu, Tag tag) {

		TagFeatureProperties props = (TagFeatureProperties)tag;

		TagProperty[] tps = props.getSupportedProperties();

		if ( tps.length > 0 ){

			final Menu props_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

			MenuItem props_item = new MenuItem( menu, SWT.CASCADE);

			Messages.setLanguageText( props_item, "label.properties" );

			props_item.setMenu( props_menu );

			for ( final TagProperty tp: tps ){

				if ( tp.getType() == TagFeatureProperties.PT_STRING_LIST ){

					String tp_name = tp.getName( false );

					if ( tp_name.equals( TagFeatureProperties.PR_CONSTRAINT )){

						MenuItem const_item = new MenuItem( props_menu, SWT.PUSH);

						Messages.setLanguageText( const_item, "label.contraints" );

						const_item.addListener(SWT.Selection, new Listener() {
							@Override
							public void
							handleEvent(Event event)
							{
								final String[] old_value = tp.getStringList();

								String def_val;

								if ( old_value != null && old_value.length > 0 ){

									def_val = old_value[0];

								}else{

									def_val = "";
								}

								String msg = MessageText.getString( "UpdateConstraint.message" );

								SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow( "UpdateConstraint.title", "!" + msg + "!" );

								entryWindow.setPreenteredText( def_val, false );
								entryWindow.selectPreenteredText( true );

								entryWindow.prompt(new UIInputReceiverListener() {
									@Override
									public void UIInputReceiverClosed(UIInputReceiver receiver) {
										if (!receiver.hasSubmittedInput()) {
											return;
										}

										try{
											String text = receiver.getSubmittedInput().trim();

											if ( text.length() ==  0 ){

												tp.setStringList( null );

											}else{

												String old_options = old_value.length>1?old_value[1]:"";

												tp.setStringList( new String[]{ text, old_options });
											}
										}catch( Throwable e ){

											Debug.out( e );
										}
									}
								});

							}});

					}else if ( tp_name.equals( TagFeatureProperties.PR_TRACKER_TEMPLATES )){

						final TrackersUtil tut = TrackersUtil.getInstance();

						List<String> templates = new ArrayList<>( tut.getMultiTrackers().keySet());

						String str_merge 	= MessageText.getString("label.merge" );
						String str_replace 	= MessageText.getString("label.replace" );
						String str_remove 	= MessageText.getString("Button.remove" );

						String[] val = tp.getStringList();

						String def_str;

						final List<String> selected = new ArrayList<>();

						if ( val == null || val.length == 0 ){

							def_str = "";

						}else{

							def_str = "";

							for ( String v: val ){

								String[] bits = v.split( ":" );

								if ( bits.length == 2 ){

									String tn = bits[1];

									if ( templates.contains( tn )){

										String type = bits[0];

										if ( type.equals( "m" )){

											tn += ": " + str_merge;

										}else if ( type.equals( "r" )){

											tn += ": " + str_replace;

										}else{
											tn += ": " + str_remove;
										}

										selected.add( v );

										def_str += (def_str.length()==0?"":", ") + tn;
									}
								}
							}
						}

						Collections.sort( templates );

							// deliberately hanging this off the main menu, not properties...

						Menu ttemp_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

						MenuItem ttemp_item = new MenuItem( menu, SWT.CASCADE);

						ttemp_item.setText( MessageText.getString( "label.tracker.templates" ) + (def_str.length()==0?"":(" (" + def_str + ")  ")));

						ttemp_item.setMenu( ttemp_menu );

						MenuItem new_item = new MenuItem( ttemp_menu, SWT.PUSH);

						Messages.setLanguageText( new_item, "wizard.multitracker.new" );

						new_item.addListener(SWT.Selection, new Listener() {
							@Override
							public void
							handleEvent(Event event)
							{
								List<List<String>> group = new ArrayList<>();
								List<String> tracker = new ArrayList<>();
								group.add(tracker);

								new MultiTrackerEditor(
									props_menu.getShell(),
									null,
									group,
									new TrackerEditorListener() {

										@Override
										public void
										trackersChanged(
											String 				oldName,
											String 				newName,
											List<List<String>> 	trackers)
										{
											if ( trackers != null ){

												tut.addMultiTracker(newName , trackers );
											}
										}
									});
							}});

						MenuItem reapply_item = new MenuItem( ttemp_menu, SWT.PUSH);

						Messages.setLanguageText( reapply_item, "label.reapply" );

						reapply_item.addListener(SWT.Selection, new Listener() {
							@Override
							public void
							handleEvent(Event event)
							{
								tp.syncListeners();
							}});

						reapply_item.setEnabled( def_str.length() > 0 );

						if ( templates.size() > 0 ){

							new MenuItem( ttemp_menu, SWT.SEPARATOR);

							for ( final String template_name: templates ){

								Menu t_menu = new Menu( ttemp_menu.getShell(), SWT.DROP_DOWN);

								MenuItem t_item = new MenuItem( ttemp_menu, SWT.CASCADE);

								t_item.setText( template_name );

								t_item.setMenu( t_menu );

								boolean	r_selected = false;

								for ( int i=0;i<3;i++){

									final MenuItem sel_item = new MenuItem( t_menu, SWT.CHECK);

									final String key = (i==0?"m":(i==1?"r":"x")) + ":" + template_name;

									sel_item.setText( i==0?str_merge:(i==1?str_replace:str_remove));

									boolean is_sel = selected.contains( key );

									r_selected |= is_sel;

									sel_item.setSelection( is_sel );

									sel_item.addListener(SWT.Selection, new Listener() {
										@Override
										public void
										handleEvent(Event event)
										{
											if ( sel_item.getSelection()){

												selected.add( key );

											}else{

												selected.remove( key );
											}

											Utils.getOffOfSWTThread(new AERunnable() {

												@Override
												public void runSupport() {
													tp.setStringList( selected.toArray( new String[ selected.size()]));
												}
											});

										}});
								}

								if ( r_selected ){

									Utils.setMenuItemImage( t_item, "graytick" );
								}

								new MenuItem( t_menu, SWT.SEPARATOR);

								MenuItem edit_item = new MenuItem( t_menu, SWT.PUSH);

								Messages.setLanguageText( edit_item, "wizard.multitracker.edit" );

								edit_item.addListener(SWT.Selection, new Listener() {
									@Override
									public void
									handleEvent(Event event)
									{
										new MultiTrackerEditor(
											props_menu.getShell(),
											template_name,
											tut.getMultiTrackers().get( template_name ),
											new TrackerEditorListener() {

												@Override
												public void
												trackersChanged(
													String 				oldName,
													String 				newName,
													List<List<String>> 	trackers)
												{
													if  ( oldName != null && !oldName.equals( newName )){

														tut.removeMultiTracker( oldName );
												    }

													tut.addMultiTracker(newName , trackers );
												}
											});
									}});

								MenuItem del_item = new MenuItem( t_menu, SWT.PUSH);

								Messages.setLanguageText( del_item, "FileItem.delete" );

								Utils.setMenuItemImage( del_item, "delete" );

								del_item.addListener(SWT.Selection, new Listener() {
									@Override
									public void
									handleEvent(Event event)
									{
										MessageBoxShell mb =
												new MessageBoxShell(
													MessageText.getString("message.confirm.delete.title"),
													MessageText.getString("message.confirm.delete.text",
															new String[] { template_name	}),
													new String[] {
														MessageText.getString("Button.yes"),
														MessageText.getString("Button.no")
													},
													1 );

											mb.open(new UserPrompterResultListener() {
												@Override
												public void prompterClosed(int result) {
													if (result == 0) {
														tut.removeMultiTracker( template_name );
													}
												}
											});
									}});
							}
						}
					}else{
						String[] val = tp.getStringList();

						String def_str;

						if ( val == null || val.length == 0 ){

							def_str = "";

						}else{

							def_str = "";

							for ( String v: val ){

								def_str += (def_str.length()==0?"":", ") + v;
							}
						}

						MenuItem set_item = new MenuItem( props_menu, SWT.PUSH);

						set_item.setText( tp.getName( true ) + (def_str.length()==0?"":(" (" + def_str + ") ")) + "..." );

						final String f_def_str = def_str;

						set_item.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event){

								String msg = MessageText.getString( "UpdateProperty.list.message", new String[]{ tp.getName( true ) } );

								SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow( "UpdateProperty.title", "!" + msg + "!" );

								entryWindow.setPreenteredText( f_def_str, false );
								entryWindow.selectPreenteredText( true );

								entryWindow.prompt(new UIInputReceiverListener() {
									@Override
									public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
										if (!entryWindow.hasSubmittedInput()) {
											return;
										}

										try{
											String text = entryWindow.getSubmittedInput().trim();

											if ( text.length() ==  0 ){

												tp.setStringList( null );

											}else{
												text = text.replace( ';', ',');
												text = text.replace( ' ', ',');
												text = text.replaceAll( "[,]+", "," );

												String[] bits = text.split( "," );

												List<String> vals = new ArrayList<>();

												for ( String bit: bits ){

													bit = bit.trim();

													if ( bit.length() > 0 ){

														vals.add( bit );
													}
												}

												if ( vals.size() == 0 ){

													tp.setStringList( null );
												}else{

													tp.setStringList( vals.toArray( new String[ vals.size()]));
												}
											}
										}catch( Throwable e ){

											Debug.out( e );
										}
									}
								});

							}});
					}
				}else if ( tp.getType() == TagFeatureProperties.PT_BOOLEAN ){

					final MenuItem set_item = new MenuItem( props_menu, SWT.CHECK);

					set_item.setText( tp.getName( true ));

					Boolean val = tp.getBoolean();

					set_item.setSelection( val != null && val );

					set_item.addListener(
						SWT.Selection,
						new Listener()
						{
							@Override
							public void
							handleEvent(
								Event event)
							{
								tp.setBoolean( set_item.getSelection());
							}
						});

				}else{

					Debug.out( "Unknown property" );
				}
			}
		}
	}

	private static void addShareWithFriendsMenuItems(Menu menu, Tag tag,
			TagType tag_type) {
		PluginInterface bpi = PluginInitializer.getDefaultInterface().getPluginManager().getPluginInterfaceByClass(
				BuddyPlugin.class);

		if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_MANUAL && bpi != null ){

			TagFeatureProperties props = (TagFeatureProperties)tag;

			TagProperty tp = props.getProperty( TagFeatureProperties.PR_UNTAGGED );

			Boolean is_ut = tp==null?null:tp.getBoolean();

			if ( is_ut == null || !is_ut ){

				final BuddyPlugin buddy_plugin = (BuddyPlugin) bpi.getPlugin();

				if (buddy_plugin.isClassicEnabled()) {

					final Menu share_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
					final MenuItem share_item = new MenuItem(menu, SWT.CASCADE);
					Messages.setLanguageText(share_item, "azbuddy.ui.menu.cat.share");
					share_item.setText( share_item.getText() + "  " );	// nasty hack to fix nastyness on windows
					share_item.setMenu(share_menu);

					List<BuddyPluginBuddy> buddies = buddy_plugin.getBuddies();

					if (buddies.size() == 0) {

						final MenuItem item = new MenuItem(share_menu, SWT.CHECK);

						item.setText(MessageText.getString("general.add.friends"));

						item.setEnabled(false);

					} else {
						final String tag_name = tag.getTagName( true );

						final boolean is_public = buddy_plugin.isPublicTagOrCategory( tag_name );

						final MenuItem itemPubCat = new MenuItem(share_menu, SWT.CHECK);

						Messages.setLanguageText(itemPubCat, "general.all.friends");

						itemPubCat.setSelection(is_public);

						itemPubCat.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event) {
								if (is_public) {

									buddy_plugin.removePublicTagOrCategory( tag_name );

								} else {

									buddy_plugin.addPublicTagOrCategory( tag_name );
								}
							}
						});

						new MenuItem(share_menu, SWT.SEPARATOR);

						for (final BuddyPluginBuddy buddy : buddies) {

							if (buddy.getNickName() == null) {

								continue;
							}

							final boolean auth = buddy.isLocalRSSTagOrCategoryAuthorised(tag_name);

							final MenuItem itemShare = new MenuItem(share_menu, SWT.CHECK);

							itemShare.setText(buddy.getName());

							itemShare.setSelection(auth || is_public);

							if (is_public) {

								itemShare.setEnabled(false);
							}

							itemShare.addListener(SWT.Selection, new Listener() {
								@Override
								public void handleEvent(Event event) {
									if (auth) {

										buddy.removeLocalAuthorisedRSSTagOrCategory(tag_name);

									} else {

										buddy.addLocalAuthorisedRSSTagOrCategory(tag_name);
									}
								}
							});

						}
					}
				}
			}
		}
	}

	private static void createXCodeMenuItems(Menu menu, Tag tag) {


		final TagFeatureTranscode tf_xcode = (TagFeatureTranscode)tag;

		if ( tf_xcode.supportsTagTranscode()){

			TrancodeUIUtils.TranscodeTarget[] tts = TrancodeUIUtils.getTranscodeTargets();

			if (tts.length > 0) {

				final Menu t_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);

				final MenuItem t_item = new MenuItem(menu, SWT.CASCADE);

				Messages.setLanguageText( t_item, "cat.autoxcode" );

				t_item.setMenu(t_menu);

				String[] existing = tf_xcode.getTagTranscodeTarget();

				for ( final TrancodeUIUtils.TranscodeTarget tt : tts ){

					TrancodeUIUtils.TranscodeProfile[] profiles = tt.getProfiles();

					if ( profiles.length > 0 ){

						final Menu tt_menu = new Menu(t_menu.getShell(), SWT.DROP_DOWN);

						final MenuItem tt_item = new MenuItem(t_menu, SWT.CASCADE);

						tt_item.setText(tt.getName());

						tt_item.setMenu(tt_menu);

						for (final TrancodeUIUtils.TranscodeProfile tp : profiles) {

							final MenuItem p_item = new MenuItem(tt_menu, SWT.CHECK);

							p_item.setText(tp.getName());

							boolean	selected = existing != null	&& existing[0].equals(tp.getUID());

							if ( selected ){

								Utils.setMenuItemImage(tt_item, "graytick");
							}

							p_item.setSelection(selected );

							p_item.addListener(SWT.Selection, new Listener(){
								@Override
								public void handleEvent(Event event) {

									String name = tt.getName() + " - " + tp.getName();

									if ( p_item.getSelection()){

										tf_xcode.setTagTranscodeTarget( tp.getUID(), name );

									}else{

										tf_xcode.setTagTranscodeTarget( null, null );
									}
								}
							});
						}

						new MenuItem(tt_menu, SWT.SEPARATOR );

						final MenuItem no_xcode_item = new MenuItem(tt_menu, SWT.CHECK);

						final String never_str = MessageText.getString( "v3.menu.device.defaultprofile.never" );

						no_xcode_item.setText( never_str );

						final String never_uid = tt.getID() + "/blank";

						boolean	selected = existing != null	&& existing[0].equals(never_uid);

						if ( selected ){

							Utils.setMenuItemImage(tt_item, "graytick");
						}

						no_xcode_item.setSelection(selected );

						no_xcode_item.addListener(SWT.Selection, new Listener(){

							@Override
							public void handleEvent(Event event) {

								String name = tt.getName() + " - " + never_str;

								if ( no_xcode_item.getSelection()){

									tf_xcode.setTagTranscodeTarget( never_uid, name );

								}else{

									tf_xcode.setTagTranscodeTarget( null, null );
								}
							}
						});
					}
				}
			}
		}
	}

	private static void
		createCloseableMenuItems(
				Menu menu,
				Tag tag,
				TagType tag_type,
				Menu[] menuShowHide,
				boolean needs_separator_next)
	{

		final List<Tag>	tags = tag_type.getTags();

		int	visible_count 	= 0;
		int	invisible_count = 0;

		for ( Tag t: tags ){

			if ( t.isVisible()){
				visible_count++;
			}else{
				invisible_count++;
			}
		}

		menuShowHide[0] = new Menu(menu.getShell(), SWT.DROP_DOWN);

		final MenuItem showhideitem = new MenuItem(menu, SWT.CASCADE);
		showhideitem.setText( MessageText.getString( "label.showhide.tag" ));
		showhideitem.setMenu(menuShowHide[0]);

		MenuItem title = new MenuItem(menuShowHide[0], SWT.PUSH);
		title.setText( "[" + tag_type.getTagTypeName( true ) + "]" );
		title.setEnabled( false );
		new MenuItem( menuShowHide[0], SWT.SEPARATOR);

		if ( invisible_count > 0 ){
			MenuItem showAll = new MenuItem(menuShowHide[0], SWT.PUSH);
			Messages.setLanguageText(showAll, "label.show.all");
			showAll.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event){
					for ( Tag t: tags ){

						if ( !t.isVisible()){
							t.setVisible( true );
						}
					}
				}});

			needs_separator_next = true;
		}

		if ( visible_count > 0 ){
			MenuItem hideAll = new MenuItem(menuShowHide[0], SWT.PUSH);
			Messages.setLanguageText(hideAll, "popup.error.hideall");
			hideAll.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event){
					for ( Tag t: tags ){

						if ( t.isVisible()){
							t.setVisible( false );
						}
					}
				}});

			needs_separator_next = true;
		}

		if ( tags.size() > 0 ){

			if ( needs_separator_next ){

				new MenuItem( menuShowHide[0], SWT.SEPARATOR);

				needs_separator_next = false;
			}

			List<String>	menu_names 		= new ArrayList<>();
			Map<String,Tag>	menu_name_map 	= new IdentityHashMap<>();

			for ( Tag t: tags ){

				String name = t.getTagName( true );

				menu_names.add( name );
				menu_name_map.put( name, t );
			}

			List<Object>	menu_structure = MenuBuildUtils.splitLongMenuListIntoHierarchy( menu_names, MAX_TOP_LEVEL_TAGS_IN_MENU );

			for ( Object obj: menu_structure ){

				List<Tag>	bucket_tags = new ArrayList<>();

				Menu parent_menu;

				if ( obj instanceof String ){

					parent_menu = menuShowHide[0];

					bucket_tags.add( menu_name_map.get((String)obj));

				}else{

					Object[]	entry = (Object[])obj;

					List<String>	tag_names = (List<String>)entry[1];

					boolean	sub_all_visible 	= true;
					boolean sub_some_visible	= false;

					for ( String name: tag_names ){

						Tag sub_tag = menu_name_map.get( name );

						if ( sub_tag.isVisible()){

							sub_some_visible = true;

						}else{

							sub_all_visible = false;
						}

						bucket_tags.add( sub_tag );
					}

					String mod;

					if ( sub_all_visible ){

						mod = " (*)";

					}else if ( sub_some_visible ){

						mod = " (+)";

					}else{

						mod = "";
					}

					Menu menu_bucket = new Menu( menuShowHide[0].getShell(), SWT.DROP_DOWN );

					MenuItem bucket_item = new MenuItem( menuShowHide[0], SWT.CASCADE );

					bucket_item.setText((String)entry[0] + mod);

					bucket_item.setMenu( menu_bucket );

					parent_menu = menu_bucket;
				}

				for ( final Tag t: bucket_tags ){

					MenuItem showTag = new MenuItem( parent_menu, SWT.CHECK );

					showTag.setSelection( t.isVisible());

					Messages.setLanguageText(showTag, t.getTagName( false ));

					showTag.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event){
							t.setVisible( !t.isVisible());
						}});
				}
			}
		}

		showhideitem.setEnabled( true );
	}

	private static void
		createNonAutoMenuItems(
				final Menu menu,
				final Tag tag,
				final TagType tag_type,
				Menu[] menuShowHide)
	{

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_PROPERTIES )){

			TagFeatureProperties props = (TagFeatureProperties)tag;

			boolean has_ut = props.getProperty( TagFeatureProperties.PR_UNTAGGED ) != null;

			if ( has_ut ){

				has_ut = false;

				for ( Tag t: tag_type.getTags()){

					props = (TagFeatureProperties)t;

					TagProperty prop = props.getProperty( TagFeatureProperties.PR_UNTAGGED );

					if ( prop != null ){

						Boolean b = prop.getBoolean();

						if ( b != null && b ){

							has_ut = true;

							break;
						}
					}
				}

				if  ( !has_ut ){

					if ( menuShowHide[0] == null ){

						menuShowHide[0] = new Menu(menu.getShell(), SWT.DROP_DOWN);

						MenuItem showhideitem = new MenuItem(menu, SWT.CASCADE);
						showhideitem.setText( MessageText.getString( "label.showhide.tag" ));
						showhideitem.setMenu(menuShowHide[0]);

					}else{

						new MenuItem( menuShowHide[0], SWT.SEPARATOR );
					}

					MenuItem showAll = new MenuItem(menuShowHide[0], SWT.PUSH);
					Messages.setLanguageText(showAll, "label.untagged");
					showAll.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event){
							try{
								String tag_name = MessageText.getString( "label.untagged" );

								Tag ut_tag = tag_type.getTag( tag_name, true );

								if ( ut_tag == null ){


									ut_tag = tag_type.createTag( tag_name, true );
								}

								TagFeatureProperties tp = (TagFeatureProperties)ut_tag;

								tp.getProperty( TagFeatureProperties.PR_UNTAGGED ).setBoolean( true );

							}catch( TagException e ){

								Debug.out( e );
							}
						}});
				}
			}
		}

		/*
		MenuItem item_create = new MenuItem( menu, SWT.PUSH);

		Messages.setLanguageText(item_create, "label.add.tag");
		item_create.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {

				createManualTag(null);
			}
		});
		*/

		/* Seldom Used: Color can be set in Tags Overview
		MenuItem itemSetColor = new MenuItem(menu, SWT.PUSH);

		itemSetColor.setText( MessageText.getString( "label.color") + "..." );

		itemSetColor.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {

				int[] existing = tag.getColor();

				RGB e_rg = existing==null?null:new RGB(existing[0],existing[1],existing[2]);

				RGB rgb = Utils.showColorDialog( menu.getShell(), e_rg );

				if ( rgb != null ){

					tag.setColor( new int[]{ rgb.red, rgb.green, rgb.blue });
				}
			}
		});
	 */

		MenuItem itemGroup = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemGroup, "MyTorrentsView.menu.group");
		itemGroup.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"TagGroupWindow.title", "TagGroupWindow.message");

				String group = tag.getGroup();

				if ( group == null ){
					group = "";
				}
				entryWindow.setPreenteredText( group, false );
				entryWindow.selectPreenteredText( true );

				entryWindow.prompt(new UIInputReceiverListener() {
					@Override
					public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
						if (!entryWindow.hasSubmittedInput()) {
							return;
						}

						try {
							String group = entryWindow.getSubmittedInput().trim();

							if (group.length() == 0) {
								group = null;
							}
							tag.setGroup(group);

						} catch (Throwable e) {

							Debug.out(e);
						}
					}
				});
			}
		});

		MenuItem itemDelete = new MenuItem(menu, SWT.PUSH);

		Utils.setMenuItemImage(itemDelete, "delete");

		Messages.setLanguageText(itemDelete, "FileItem.delete");
		itemDelete.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				tag.removeTag();
			}
		});
	}

	public static void
	createSideBarMenuItems(
		final Menu 			menu,
		final List<Tag>	 	_tags )
	{
		final List<Tag> tags = new ArrayList<>( _tags );

		Iterator<Tag> it = tags.iterator();

		boolean	can_show 	= false;
		boolean	can_hide	= false;

		while( it.hasNext()){

			Tag tag = it.next();

			if ( tag.getTagType().getTagType() != TagType.TT_DOWNLOAD_MANUAL ){

				it.remove();
			}else{

				if ( tag.isVisible()){

					can_hide = true;

				}else{

					can_show = true;
				}
			}
		}

		if ( tags.size() == 0 ){

			return;
		}

		MenuItem itemShow = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemShow, "Button.bar.show");
		itemShow.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				for ( Tag tag: tags ){
					tag.setVisible( true );
				}
			}
		});

		itemShow.setEnabled( can_show );

		MenuItem itemHide = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemHide, "Button.bar.hide");
		itemHide.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				for ( Tag tag: tags ){
					tag.setVisible( false );
				}
			}
		});

		itemHide.setEnabled( can_hide );

		MenuItem itemGroup = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemGroup, "MyTorrentsView.menu.group");
		itemGroup.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"TagGroupWindow.title", "TagGroupWindow.message");


				entryWindow.setPreenteredText( "", false );
				entryWindow.selectPreenteredText( true );

				entryWindow.prompt(new UIInputReceiverListener() {
					@Override
					public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
						if ( !entryWindow.hasSubmittedInput()) {
							return;
						}

						try{
							String group = entryWindow.getSubmittedInput().trim();

							if ( group.length() == 0 ){
								group = null;
							}

							for ( Tag tag: tags ){

								tag.setGroup( group );
							}
						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				});

			}
		});

		com.biglybt.pif.ui.menus.MenuItem[] items = MenuItemManager.getInstance().getAllAsArray(
				MenuManager.MENU_TAG_CONTEXT);

		if (items.length > 0) {
			MenuFactory.addSeparatorMenuItem(menu);

			// TODO: Don't send Tag.. send a yet-to-be-created plugin interface version of Tag
			MenuBuildUtils.addPluginMenuItems(items, menu, true, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(tags.toArray(new Tag[0])));
		}
	}

	private static final AsyncDispatcher move_dispatcher = new AsyncDispatcher( "tag:applytocurrent" );

	private static void
	applyLocationToCurrent(
		final Tag			tag,
		final File			location,
		final boolean		complete_only )
	{
		move_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					Set<DownloadManager>	downloads = ((TagDownload)tag).getTaggedDownloads();

					for ( DownloadManager download: downloads ){

						boolean dl_is_complete = download.isDownloadComplete( false );

						if ( complete_only && !dl_is_complete){

								// applying move-on-complete so ignore incomplete

							continue;
						}

						if ( dl_is_complete && !complete_only ){

								// applying initial-save-folder, ignore completed files
								// that have been moved somewhere already

							if ( download.getDownloadState().getFlag( DownloadManagerState.FLAG_MOVE_ON_COMPLETION_DONE )){

								continue;
							}
						}

						try{
							download.moveDataFilesLive( location );

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			});
	}

	public static void
	addLibraryViewTagsSubMenu(
		final DownloadManager[] 	dms,
		Menu 						menu_tags)
	{
		MenuItem[] items = menu_tags.getItems();

		for ( MenuItem item: items ){

			item.dispose();
		}

		final TagManager tm = TagManagerFactory.getTagManager();

		Map<TagType,List<Tag>>	auto_map = new HashMap<>();

		TagType manual_tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );

		Map<Tag,Integer>	manual_map = new HashMap<>();

		for ( DownloadManager dm: dms ){

			List<Tag> tags = tm.getTagsForTaggable( dm );

			for ( Tag t: tags ){

				TagType tt = t.getTagType();

				if ( tt.isTagTypeAuto() || t.isTagAuto()[0] || t.isTagAuto()[1]){

					List<Tag> x = auto_map.get( tt );

					if ( x == null ){

						x = new ArrayList<>();

						auto_map.put( tt, x );
					}

					x.add( t );

				}else if ( tt == manual_tt ){

					Integer i = manual_map.get( t );

					manual_map.put( t, i==null?1:i+1 );
				}
			}
		}

		if ( auto_map.size() > 0 ){

			final Menu menuAuto = new Menu(menu_tags.getShell(), SWT.DROP_DOWN);
			final MenuItem autoItem = new MenuItem(menu_tags, SWT.CASCADE);
			Messages.setLanguageText(autoItem, "wizard.maketorrent.auto" );
			autoItem.setMenu(menuAuto);

			List<TagType>	auto_tags = sortTagTypes( auto_map.keySet());

			for ( TagType tt: auto_tags ){

				MenuItem tt_i = new MenuItem(menuAuto, Constants.isOSX?SWT.CHECK:SWT.PUSH);

				String tt_str = tt.getTagTypeName( true ) + ": ";

				List<Tag> tags = auto_map.get( tt );

				Map<Tag,Integer>	tag_counts = new HashMap<>();

				for ( Tag t: tags ){

					Integer i = tag_counts.get( t );

					tag_counts.put( t, i==null?1:i+1 );
				}

				tags = sortTags( tag_counts.keySet());

				int	 num = 0;

				for ( Tag t: tags ){

					tt_str += (num==0?"":", " ) + t.getTagName( true );

					num++;

					if ( dms.length > 1 ){

						tt_str += " (" + tag_counts.get( t ) + ")";
					}
				}

				tt_i.setText( tt_str );
				if ( Constants.isOSX ){
					tt_i.setSelection(true);
				}else{
					Utils.setMenuItemImage( tt_i, "graytick" );
				}

				//tt_i.setEnabled(false);
			}
		}

		List<Tag>	manual_t = manual_tt.getTags();

		if ( manual_t.size() > 0 ){

			if ( auto_map.size() > 0 ){

				new MenuItem( menu_tags, SWT.SEPARATOR );
			}

			List<String>	menu_names 		= new ArrayList<>();
			Map<String,Tag>	menu_name_map 	= new IdentityHashMap<>();

			for ( Tag t: manual_t ){

					// don't allow manual adding of taggables to auto-tags

				if ( !t.isTagAuto()[0]){

					String name = t.getTagName( true );

					menu_names.add( name );
					menu_name_map.put( name, t );
				}
			}

			List<Object>	menu_structure = MenuBuildUtils.splitLongMenuListIntoHierarchy( menu_names, MAX_TOP_LEVEL_TAGS_IN_MENU );

			for ( Object obj: menu_structure ){

				List<Tag>	bucket_tags = new ArrayList<>();

				Menu	 parent_menu;

				if ( obj instanceof String ){

					parent_menu = menu_tags;

					bucket_tags.add( menu_name_map.get((String)obj));

				}else{

					Object[]	entry = (Object[])obj;

					List<String>	tag_names = (List<String>)entry[1];

					boolean	sub_all_selected 	= true;
					boolean sub_some_selected	= false;

					for ( String name: tag_names ){

						Tag sub_tag = menu_name_map.get( name );

						Integer c = manual_map.get( sub_tag );

						if ( c != null && c == dms.length ){

							sub_some_selected = true;

						}else{

							sub_all_selected = false;
						}

						bucket_tags.add( sub_tag );
					}

					String mod;

					if ( sub_all_selected ){

						mod = " (*)";

					}else if ( sub_some_selected ){

						mod = " (+)";

					}else{

						mod = "";
					}

					Menu menu_bucket = new Menu( menu_tags.getShell(), SWT.DROP_DOWN );

					MenuItem bucket_item = new MenuItem( menu_tags, SWT.CASCADE );

					bucket_item.setText((String)entry[0] + mod);

					bucket_item.setMenu( menu_bucket );

					parent_menu = menu_bucket;
				}

				for ( final Tag t: bucket_tags ){

					final MenuItem t_i = new MenuItem( parent_menu, SWT.CHECK );

					String tag_name = t.getTagName( true );

					Integer c = manual_map.get( t );

					if ( c != null ){

						if ( c == dms.length ){

							t_i.setSelection( true );

							t_i.setText( tag_name );

						}else{

							t_i.setText( tag_name + " (" + c + ")" );
						}
					}else{

						t_i.setText( tag_name );
					}

					t_i.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {

							boolean	selected = t_i.getSelection();

							for ( DownloadManager dm: dms ){

								if ( selected ){

									t.addTaggable( dm );

								}else{

									t.removeTaggable( dm );
								}
							}
						}
					});
				}
			}
		}

		new MenuItem( menu_tags, SWT.SEPARATOR );

		MenuItem item_create = new MenuItem( menu_tags, SWT.PUSH);

		Messages.setLanguageText(item_create, "label.add.tag");
		item_create.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {

				createManualTag(new UIFunctions.TagReturner() {
					@Override
					public void returnedTags(Tag[] tags) {
						if ( tags != null ){
							for (Tag new_tag : tags) {
								for ( DownloadManager dm: dms ){

									new_tag.addTaggable( dm );
								}

								COConfigurationManager.setParameter( "Library.TagInSideBar", true );
							}
						}
					}
				});
			}
		});
	}

	public static List<TagType>
	sortTagTypes(
		Collection<TagType>	_tag_types )
	{
		List<TagType>	tag_types = new ArrayList<>( _tag_types );

		Collections.sort(
			tag_types,
			new Comparator<TagType>()
			{
				final Comparator<String> comp = new FormattersImpl().getAlphanumericComparator( true );

				@Override
				public int
				compare(
					TagType o1, TagType o2)
				{
					return( comp.compare( o1.getTagTypeName(true), o2.getTagTypeName(true)));
				}
			});

		return( tag_types );
	}

	public static List<Tag>
	sortTags(
		Collection<Tag>	_tags )
	{
		List<Tag>	tags = new ArrayList<>( _tags );

		if ( tags.size() < 2 ){

			return( tags );
		}

		Collections.sort( tags, getTagComparator());

		return( tags );
	}

	public static Comparator<Tag>
	getTagComparator()
	{
		return( new Comparator<Tag>()
		{
			final Comparator<String> comp = new FormattersImpl().getAlphanumericComparator( true );

			@Override
			public int
			compare(
				Tag o1, Tag o2)
			{
				String	g1 = o1.getGroup();
				String	g2 = o2.getGroup();

				if ( g1 != g2 ){
					if ( g1 == null ){
						return( 1 );
					}else if ( g2 == null ){
						return( -1 );
					}else{

						int	res = comp.compare( g1,  g2 );

						if ( res != 0 ){
							return( res );
						}
					}
				}
				return( comp.compare( o1.getTagName(true), o2.getTagName(true)));
			}
		});
	}

	public static String
	getTagTooltip(
		Tag		tag )
	{
		return( getTagTooltip( tag, false ));
	}

	public static String
	getTagTooltip(
		Tag			tag,
		boolean		skip_name )
	{
		TagType tag_type = tag.getTagType();

		String 	str = skip_name?"":(tag_type.getTagTypeName( true ) + ": " + tag.getTagName( true ));

		String desc = tag.getDescription();

		if ( desc != null ){

			if ( str.length() > 0 ){

				str += "\r\n";
			}

			str += desc;
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_RATE_LIMIT )){

			TagFeatureRateLimit rl = (TagFeatureRateLimit)tag;

			String 	up_str 		= "";
			String	down_str 	= "";

			int	limit_up = rl.getTagUploadLimit();

			if ( limit_up > 0 ){

				up_str += MessageText.getString( "label.limit" ) + "=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( limit_up );
			}

			int current_up 		= rl.getTagCurrentUploadRate();

			if ( current_up >= 0 ){

				up_str += (up_str.length()==0?"":", " ) + MessageText.getString( "label.current" ) + "=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( current_up);
			}

			int	limit_down = rl.getTagDownloadLimit();

			if ( limit_down > 0 ){

				down_str += MessageText.getString( "label.limit" ) + "=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( limit_down );
			}

			int current_down 		= rl.getTagCurrentDownloadRate();

			if ( current_down >= 0 ){

				down_str += (down_str.length()==0?"":", " ) + MessageText.getString( "label.current" ) + "=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( current_down);
			}


			if ( up_str.length() > 0 ){

				str += "\r\n    " + MessageText.getString("iconBar.up") + ": " + up_str;
			}

			if ( down_str.length() > 0 ){

				str += "\r\n    " + MessageText.getString("iconBar.down") + ": " + down_str;
			}


			int up_pri = rl.getTagUploadPriority();

			if ( up_pri > 0 ){

				str += "\r\n    " + MessageText.getString("cat.upload.priority");
			}
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )) {

			TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;

			if ( fl.supportsTagInitialSaveFolder()){

				File init_loc = fl.getTagInitialSaveFolder();

				if ( init_loc != null ){

					str += "\r\n    " + MessageText.getString("label.init.save.loc") + "=" + init_loc.getAbsolutePath();
				}
			}

			if ( fl.supportsTagMoveOnComplete()){

				File move_on_comp = fl.getTagMoveOnCompleteFolder();

				if ( move_on_comp != null ){

					str += "\r\n    " + MessageText.getString("label.move.on.comp") + "=" + move_on_comp.getAbsolutePath();
				}
			}
			if ( fl.supportsTagCopyOnComplete()){

				File copy_on_comp = fl.getTagCopyOnCompleteFolder();

				if ( copy_on_comp != null ){

					str += "\r\n    " + MessageText.getString("label.copy.on.comp") + "=" + copy_on_comp.getAbsolutePath();
				}
			}
		}

		if ( str.startsWith( "\r\n" )){

			str = str.substring(2);
		}

		return( str );
	}

	private static void
	showFilesView(
		final TagDownload		tag )
	{
		Shell shell = ShellFactory.createShell(Utils.findAnyShell(), SWT.SHELL_TRIM);

		FillLayout fillLayout = new FillLayout();
		fillLayout.marginHeight = 2;
		fillLayout.marginWidth = 2;
		shell.setLayout(fillLayout);

		final FilesView view = new FilesView(false);

		view.setDisableWhenEmpty( false );

		Set<DownloadManager>	dms = tag.getTaggedDownloads();

		view.dataSourceChanged( dms.toArray());

		view.initialize(shell);

		view.viewActivated();
		view.refresh();

		final UIUpdatable viewUpdater = new UIUpdatable() {
			@Override
			public void updateUI() {
				view.refresh();
			}

			@Override
			public String getUpdateUIName() {
				return view.getFullTitle();
			}
		};

		UIUpdaterSWT.getInstance().addUpdater(viewUpdater);

		final TagListener tag_listener =
			new TagListener() {

				@Override
				public void taggableSync(Tag tag) {
				}

				@Override
				public void
				taggableRemoved(
					Tag t, Taggable tagged)
				{
					Set<DownloadManager>	dms = tag.getTaggedDownloads();

					view.dataSourceChanged( dms.toArray());
				}

				@Override
				public void
				taggableAdded(
					Tag t, Taggable tagged)
				{
					Set<DownloadManager>	dms = tag.getTaggedDownloads();

					view.dataSourceChanged( dms.toArray());
				}
			};

		tag.addTagListener( tag_listener, false );

		shell.addDisposeListener(
			new DisposeListener()
			{
				@Override
				public void
				widgetDisposed(
					DisposeEvent e)
				{
					tag.removeTagListener( tag_listener );

					UIUpdaterSWT.getInstance().removeUpdater(viewUpdater);
					view.delete();
				}
			});

		shell.layout(true, true);


		shell.setText( tag.getTagName(true));

		shell.open();
	}
}
