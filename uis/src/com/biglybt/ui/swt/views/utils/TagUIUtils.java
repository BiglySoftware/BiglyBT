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
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.util.*;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginBuddy;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.MenuFactory;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.maketorrent.MultiTrackerEditor;
import com.biglybt.ui.swt.maketorrent.TrackerEditorListener;
import com.biglybt.ui.swt.pif.UISWTInputReceiver;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.shells.PopOutManager;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;
import com.biglybt.ui.swt.views.FilesView;
import com.biglybt.ui.swt.views.ViewUtils;
import com.biglybt.ui.swt.views.ViewUtils.SpeedAdapter;
import com.biglybt.ui.swt.views.stats.StatsView;

import static com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR;

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
			// show tags in sidebar
		
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
		}
		
			// show tag groups
		
		{
			com.biglybt.pif.ui.menus.MenuItem menuItem = menuManager.addMenuItem("sidebar."
					+ MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
					"!    " + MessageText.getString("ConfigView.section.style.TagGroupsInSidebar") + "!" );
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
	
			menuItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK);
	
			menuItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
				@Override
				public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object target) {
					boolean b = COConfigurationManager.getBooleanParameter("Library.TagGroupsInSideBar");
					COConfigurationManager.setParameter("Library.TagGroupsInSideBar", !b);
				}
			});
	
			menuItem.addFillListener(new com.biglybt.pif.ui.menus.MenuItemFillListener() {
				@Override
				public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {
					menu.setData(Boolean.valueOf(COConfigurationManager.getBooleanParameter("Library.TagGroupsInSideBar")));
					menu.setEnabled( COConfigurationManager.getBooleanParameter("Library.TagInSideBar"));
				}
			});
		}
		
		
			// tag options

		com.biglybt.pif.ui.menus.MenuItem menuTags = menuManager.addMenuItem("sidebar."
				+ MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
				"label.tags");
		menuTags.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		menuTags.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);

		menuTags.addFillListener((menuItemTags, data) -> {

			menuItemTags.removeAllChildItems();

			// manual

			final TagType manual_tt = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL );

			com.biglybt.pif.ui.menus.MenuItem menuManualTags = menuManager.addMenuItem( menuItemTags, manual_tt.getTagTypeName( false ));

			menuManualTags.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU );
			menuManualTags.addFillListener((menu, data1) -> {
				menu.removeAllChildItems();

				final List<Tag> all_tags = manual_tt.getTags();
				final Map<Tag, Integer> mapTaggableCount = new HashMap<>();

				boolean	has_ut	= false;

				for ( Tag t: all_tags ){

					if ( t.isVisible()){
						mapTaggableCount.put(t, 1);
					}

					if (has_ut || !(t instanceof TagFeatureProperties)) {
						continue;
					}

					TagFeatureProperties props = (TagFeatureProperties)t;
					TagProperty prop = props.getProperty( TagFeatureProperties.PR_UNTAGGED );
					if (prop == null) {
						continue;
					}

					Boolean b = prop.getBoolean();
					has_ut = b != null && b;
				}

				com.biglybt.pif.ui.menus.MenuItem showAllItem = menuManager.addMenuItem( menu, "label.show.all" );
				showAllItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );
				showAllItem.addListener((menu12, target) -> {
					for ( Tag t: all_tags ){
						t.setVisible( true );
					}
				});
				showAllItem.setEnabled(mapTaggableCount.size() != all_tags.size());

				com.biglybt.pif.ui.menus.MenuItem hideAllItem = menuManager.addMenuItem( menu, "popup.error.hideall" );
				hideAllItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );
				hideAllItem.addListener((menu13, target) -> {
					for ( Tag t: all_tags ){
						t.setVisible( false );
					}
				});
				hideAllItem.setEnabled(!mapTaggableCount.isEmpty());

				menuManager.addMenuItem(menu, "sepm").setStyle(STYLE_SEPARATOR);

				TagMenuOptions tagMenuOptions = TagMenuOptions.Builder()
					.setParentPluginMenuItem(menu)
					.setMenuManager(menuManager)
					.setMapTaggableCount(mapTaggableCount, 1)
					.setTagSelectionListener(Tag::setVisible)
					.setMenuForAutoTags(false)
					.setShowAddMenu(false)
					.setTagMenuFilter(TagMenuOptions.FILTER_SHOW_ALL)
					.build();
				createTagMenu(tagMenuOptions);


				if ( !has_ut ){

					menuManager.addMenuItem(menu, "sepu").setStyle(STYLE_SEPARATOR);

					com.biglybt.pif.ui.menus.MenuItem m = menuManager.addMenuItem( menu, "label.untagged" );

					m.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

					m.addListener((menu1, target) -> {
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
					});
				}
			});

			com.biglybt.pif.ui.menus.MenuItem menuItem = menuManager.addMenuItem( menuItemTags, "label.add.tag");

			menuItem.addListener((menu, target) -> createManualTag(null));

			menuManager.addMenuItem( menuItemTags, "sep1" ).setStyle(STYLE_SEPARATOR );


				// auto

			menuItem = menuManager.addMenuItem( menuItemTags, "wizard.maketorrent.auto" );

			menuItem.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU );

			menuItem.addFillListener((menu, data12) -> {
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

					com.biglybt.pif.ui.menus.MenuItem menuItemTT = menuManager.addMenuItem( menu, tag_type.getTagTypeName( false ));

					menuItemTT.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);

					menuItemTT.addFillListener((menuTT, data1) -> {
						menuTT.removeAllChildItems();

						final List<Tag> tags = tag_type.getTags();

						com.biglybt.pif.ui.menus.MenuItem showAllItem = menuManager.addMenuItem(menuTT, "label.show.all" );
						showAllItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

						showAllItem.addListener((menu141, target) -> {
							for ( Tag t: tags ){
								t.setVisible( true );
							}
						});

						com.biglybt.pif.ui.menus.MenuItem hideAllItem = menuManager.addMenuItem(menuTT, "popup.error.hideall" );
						hideAllItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

						hideAllItem.addListener((menu1412, target) -> {
							for ( Tag t: tags ){
								t.setVisible( false );
							}
						});

						boolean	all_visible 	= true;
						boolean all_invisible 	= true;

						for ( Tag t: tags ){
							if ( t.isVisible()){
								all_invisible = false;
								if (!all_visible) {
									break;
								}
							}else{
								all_visible = false;
								if (!all_invisible) {
									break;
								}
							}
						}

						showAllItem.setEnabled( !all_visible );
						hideAllItem.setEnabled( !all_invisible );

						menuManager.addMenuItem(menuTT, "sep2").setStyle(STYLE_SEPARATOR);

						for ( final Tag t: tags ){

							com.biglybt.pif.ui.menus.MenuItem menu_tag = menuManager.addMenuItem(
									menuTT, t.getTagName(false));

							menu_tag.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK);

							menu_tag.addListener((menuTag, target) -> t.setVisible(menuTag.isSelected()));
							menu_tag.addFillListener((menuTag, data2) -> menuTag.setData(t.isVisible()));
						}
					});
				}
			});

			menuManager.addMenuItem( menuItemTags, "sep3" ).setStyle(STYLE_SEPARATOR );


			menuItem = menuManager.addMenuItem( menuItemTags, "tag.show.stats");

			menuItem.addListener((menu, target) -> {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				uiFunctions.getMDI().showEntryByID(StatsView.VIEW_ID, "TagStatsView");

			});

			menuItem = menuManager.addMenuItem( menuItemTags, "tag.show.overview");

			menuItem.addListener((menu, target) -> {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				uiFunctions.getMDI().showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_TAGS);
			});
		});

		CoreFactory.addCoreRunningListener(core -> checkTagSharing( true ));

	}

	public static void
	setMenuIcon(
		com.biglybt.pif.ui.menus.MenuItem		m,
		Tag										tag )
	{
		String image_file = tag.getImageFile();
		
		if ( image_file != null ){
			
			try{
				ImageLoader.getInstance().getFileImage(
						new File( image_file ), 
						new Point( 16, 16 ),
						new ImageLoader.ImageDownloaderListener(){
		
							  @Override
							  public void imageDownloaded(Image image, String key, boolean returnedImmediately){
								  							  
								 if ( image != null && returnedImmediately ){
									 															 
									 m.setGraphic( new UISWTGraphicImpl( image ));
									 
									 // dunno when to :( ImageLoader.getInstance().releaseImage( key );
								 }
							  }
						  });
				
			}catch( Throwable e ){
				
			}
		}
	}
	
	public static void
	setMenuIcon(
		MenuItem		m,
		Tag				tag )
	{
		String image_file = tag.getImageFile();
		
		if ( image_file != null ){
			
			try{
				ImageLoader.getInstance().getFileImage(
						new File( image_file ), 
						new Point( 16, 16 ),
						new ImageLoader.ImageDownloaderListener(){
		
							  @Override
							  public void imageDownloaded(Image image, String key, boolean returnedImmediately){
								  							  
								 if ( image != null && returnedImmediately ){
									 															 
									m.setImage( image );
									 
									m.addDisposeListener(
										new DisposeListener(){
											
											@Override
											public void widgetDisposed(DisposeEvent e){
												ImageLoader.getInstance().releaseImage( key );
											}
										});
								 }
							  }
						  });
				
			}catch( Throwable e ){
				
			}
		}
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
	createSideBarMenuItemsDelayed(
		final Menu menu, final Tag tag, Predicate<Taggable> filter )
	{
		menu.addMenuListener(new MenuListener() {
			@Override
			public void 
			menuShown(
				MenuEvent e)
			{
				Utils.disposeSWTObjects((Object[]) menu.getItems());
				createSideBarMenuItems( menu, tag, filter );
			}
			@Override
			public void menuHidden(MenuEvent e){
			
			}
		});
	}
	
	public static void
	createSideBarMenuItems(
		Menu 		menu, 
		TagGroup 	tag_group )
	{
		if ( tag_group == null || tag_group.getName() == null ){
			
			return;
		}
		
		if ( addTagGroupMenu( menu, tag_group)){
		
			new MenuItem( menu, SWT.SEPARATOR );
		}
		
		boolean can_clear = false;
		
		for (Tag tag : tag_group.getTags()) {
			if ( !tag.isColorDefault()){
				can_clear = true;
			}
		}
		addColourChooser(
			menu,
			"label.color",
			can_clear,
			tag_group,
			(selected)->{
				for (Tag tag : tag_group.getTags()) {
					tag.setColor(selected==null?null:new int[] { selected.red, selected.green, selected.blue});
				}
			});
	}
	
	public static void
	addColourChooser(
		Menu			menu,
		String			item_resource,
		boolean			can_clear,
		Tag				tag,
		Consumer<RGB>	receiver )
	{
		List<RGB>	existing = new ArrayList<>();
		
		int[] color = tag.getColor();
		if (color != null) {
			RGB rgb = new RGB(color[0], color[1], color[2]);
			existing.add(rgb);
		}
		
		MenuBuildUtils.addColourChooser(menu, item_resource, can_clear, existing, receiver);
	}

	public static void
	addColourChooser(
		Menu			menu,
		String			item_resource,
		boolean			can_clear,
		TagGroup		tag_group,
		Consumer<RGB>	receiver )
	{
		List<RGB>	existing = new ArrayList<>();
		
		for ( Tag tag: tag_group.getTags()){
			int[] color = tag.getColor();
			if (color != null) {
				RGB rgb = new RGB(color[0], color[1], color[2]);
				existing.add(rgb);
			}
		}
		
		MenuBuildUtils.addColourChooser(menu, item_resource, can_clear, existing, receiver);
	}
	
	public static void
	createSideBarMenuItems(
		Menu 				menu, 
		Tag					tag )
	{
		createSideBarMenuItems( menu, tag, (t)->true );
	}
	
	public static void
	createSideBarMenuItems(
		Menu 				menu, 
		Tag					tag,
		Predicate<Taggable> filter )
	{
		if ( tag instanceof Category ){
			
			CategoryUIUtils.createMenuItems( menu, (Category) tag, filter );
			
			return;
		}
		
	    int userMode = COConfigurationManager.getIntParameter("User Mode");

		final TagType	tag_type = tag.getTagType();

		boolean	needs_separator_next = false;

		int countBefore = menu.getItemCount();

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_RATE_LIMIT )) {
			createTF_RateLimitMenuItems(menu, tag, tag_type, userMode);
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_RUN_STATE )) {
			createTF_RunState(menu, tag, filter, userMode);
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )) {
			createTF_FileLocationMenuItems (menu, tag, filter );
		}

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_EXEC_ON_ASSIGN )){

			final TagFeatureExecOnAssign	tf_eoa = (TagFeatureExecOnAssign)tag;

			int	supported_actions = tf_eoa.getSupportedActions();

			if ( supported_actions != TagFeatureExecOnAssign.ACTION_NONE ){

				final Menu eoa_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

				MenuItem eoa_item = new MenuItem( menu, SWT.CASCADE);

				Messages.setLanguageText( eoa_item, "label.exec.on.assign" );

				eoa_item.setMenu( eoa_menu );

				boolean is_peer_set = tag.getTagType().getTagType() == TagType.TT_PEER_IPSET;

				int[]	action_ids =
					{ 	TagFeatureExecOnAssign.ACTION_APPLY_OPTIONS_TEMPLATE,
						TagFeatureExecOnAssign.ACTION_DESTROY,
						TagFeatureExecOnAssign.ACTION_START,
						TagFeatureExecOnAssign.ACTION_FORCE_START,
						TagFeatureExecOnAssign.ACTION_NOT_FORCE_START,
						TagFeatureExecOnAssign.ACTION_STOP,
						TagFeatureExecOnAssign.ACTION_QUEUE,
						TagFeatureExecOnAssign.ACTION_PAUSE,
						TagFeatureExecOnAssign.ACTION_RESUME,
						TagFeatureExecOnAssign.ACTION_SCRIPT,
						TagFeatureExecOnAssign.ACTION_POST_MAGNET_URI,
						TagFeatureExecOnAssign.ACTION_MOVE_INIT_SAVE_LOC,
						TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS,
						TagFeatureExecOnAssign.ACTION_REMOVE_TAGS,
						TagFeatureExecOnAssign.ACTION_HOST,
						TagFeatureExecOnAssign.ACTION_PUBLISH };

				String[] action_keys =
					{ 	"label.apply.options.template",
						is_peer_set?"azbuddy.ui.menu.disconnect":"v3.MainWindow.button.delete",
						"v3.MainWindow.button.start",
						"v3.MainWindow.button.forcestart",
						"v3.MainWindow.button.notforcestart",
						"v3.MainWindow.button.stop",
						"ConfigView.section.queue",
						"v3.MainWindow.button.pause",
						"v3.MainWindow.button.resume",
						"label.script",
						"label.post.magnet.to.chat",
						"label.init.save.loc.move",
						"label.assign.tags",
						"label.remove.tags",
						"menu.host.on.tracker",
						"menu.publish.on.tracker"};

				for ( int i=0;i<action_ids.length;i++ ){

					final int action_id = action_ids[i];

					if ( tf_eoa.supportsAction( action_id )){

						if ( action_id == TagFeatureExecOnAssign.ACTION_APPLY_OPTIONS_TEMPLATE ){
							
							final MenuItem opts_item = new MenuItem( eoa_menu, SWT.CHECK);
							
							opts_item.setText( 
								MessageText.getString( action_keys[i] ) + "..." );
							
							opts_item.setSelection( tf_eoa.getOptionsTemplateHandler().isActive());
							
							opts_item.addListener( SWT.Selection, new Listener(){
								@Override
								public void
								handleEvent(Event event)
								{
									UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
									
									if (uiFunctions != null){
										
										uiFunctions.getMDI().showEntryByID(
												
											MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_OPTIONS,
												tf_eoa.getOptionsTemplateHandler());
									}
								}});
							
							new MenuItem( eoa_menu, SWT.SEPARATOR);
							
						}else if ( action_id == TagFeatureExecOnAssign.ACTION_SCRIPT ){

							new MenuItem( eoa_menu, SWT.SEPARATOR);
							
							final MenuItem action_item = new MenuItem( eoa_menu, SWT.PUSH);

							String script = tf_eoa.getActionScript();

							if ( script.length() > 30 ){
								script = script.substring( 0, 30);
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

						}else if ( action_id == TagFeatureExecOnAssign.ACTION_POST_MAGNET_URI ){

							String chat_str = tf_eoa.getPostMessageChannel();
							
							MenuBuildUtils.addChatSelectionMenu( 
								eoa_menu,
								"label.post.magnet.to.chat",
								chat_str,
								new MenuBuildUtils.ChatSelectionListener(){
									
									@Override
									public void chatSelected( Object target, String chat ){
										tf_eoa.setPostMessageChannel( chat );
									}
									
									@Override
									public void chatAvailable(Object target, ChatInstance chat){
									}
								});
							
						}else if ( 	action_id == TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS ||
									action_id == TagFeatureExecOnAssign.ACTION_REMOVE_TAGS){
							
							boolean is_assign = action_id == TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS;
							
							final MenuItem action_item = new MenuItem( eoa_menu, SWT.PUSH);

							List<Tag> tags = is_assign?tf_eoa.getTagAssigns():tf_eoa.getTagRemoves();

							String msg = MessageText.getString( action_keys[i] );

							String tag_str = "";
							
							for ( Tag t: tags ){
								
								tag_str += (tag_str==""?"":",") + t.getTagName( true );
							}
							
							if ( !tag_str.isEmpty()){

								msg += ": " + tag_str;
							}

							msg += "...";

							action_item.setText( msg );

							action_item.addListener( SWT.Selection, new Listener(){
								@Override
								public void
								handleEvent(Event event)
								{
									TagManager tagManager = TagManagerFactory.getTagManager();
									
									TagType tt = tagManager.getTagType(TagType.TT_DOWNLOAD_CATEGORY);
									
									List<Tag> all_tags = new ArrayList<>( tt.getTags());

									tt = tagManager.getTagType(TagType.TT_DOWNLOAD_MANUAL);

									all_tags.addAll( tt.getTags());
									
									all_tags.remove( tag );
									
									TagUIUtilsV3.showTagSelectionDialog( 
										all_tags, 
										tags,
										new TagUIUtilsV3.TagSelectionListener()
										{
											@Override
											public void selected(List<Tag> tags){
												
												if ( is_assign ){
													tf_eoa.setTagAssigns( tags );
												}else{
													tf_eoa.setTagRemoves( tags );
												}
											}
										});
								}
							});
							
						}else{

							if ( action_id == TagFeatureExecOnAssign.ACTION_HOST ){
								
								new MenuItem( eoa_menu, SWT.SEPARATOR);
							}
							
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
							
							if ( action_id == TagFeatureExecOnAssign.ACTION_MOVE_INIT_SAVE_LOC ){
								
								TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;
								
								boolean enable = false;
								
								if ( fl.supportsTagInitialSaveFolder()){
									
									File f = fl.getTagInitialSaveFolder();
									
									enable = f != null;
								}
								
								action_item.setEnabled( enable );
							}
						}
					}
				}
			}
		}

		// options

		if ( tag instanceof TagDownload ){

			needs_separator_next = true;

			MenuItem itemOptions = new MenuItem(menu, SWT.PUSH);

			Set<DownloadManager> all_dms = ((TagDownload)tag).getTaggedDownloads();

			List<DownloadManager> dms = all_dms.stream().filter(filter).collect(Collectors.toList());
			
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

			createTFProperitesMenuItems(menu, tag);
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

		int tt = tag_type.getTagType();
		
		if ( tt == TagType.TT_DOWNLOAD_MANUAL || tt == TagType.TT_SWARM_TAG ){

			needs_separator_next = true;

			MenuItem search = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(search, "tag.search");
			search.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event){
					UIFunctionsManager.getUIFunctions().doSearch( "tag:" + tag.getTagName( true ).replace( ' ', '+' ));
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

		if ( tag_type.hasTagTypeFeature( TagFeature.TF_NOTIFICATIONS )) {
			
			TagFeatureNotifications tfn = (TagFeatureNotifications)tag;
			
			String chat_str = tfn.getNotifyMessageChannel();
			
			MenuBuildUtils.addChatSelectionMenu( 
				menu,
				"label.notify.magnets.to.chat",
				chat_str,
				new MenuBuildUtils.ChatSelectionListener(){
					
					@Override
					public void chatSelected( Object target, String chat ){
						tfn.setNotifyMessageChannel( chat );
					}
					
					@Override
					public void chatAvailable(Object target, ChatInstance chat){
					}
				});			
		}
		
		needs_separator_next = true;

		if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

			MenuBuildUtils.addChatMenu( menu, "menu.discuss.tag", getChatKey( tag ));
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
			createCloseableMenuItems(menu, tag_type, menuShowHide, needs_separator_next);
		}

		if ( !auto ){
			createNonAutoMenuItems(menu, tag, tag_type, menuShowHide);
		}

		if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_MANUAL ){

			MenuItem itemDuplicate = new MenuItem(menu, SWT.PUSH);
			
			Messages.setLanguageText(itemDuplicate,"Subscription.menu.duplicate");
			itemDuplicate.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					duplicate( Collections.singletonList(tag));
				}
			});
			
			MenuItem itemExport = new MenuItem(menu, SWT.PUSH);
			
			Messages.setLanguageText(itemExport,"Subscription.menu.export");
			itemExport.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					export( Collections.singletonList(tag));
				}
			});
		}
		
		MenuItem menuSettings = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(menuSettings, "TagSettingsView.title");
		menuSettings.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				tag.setTransientProperty( Tag.TP_SETTINGS_REQUESTED, true );
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

	private static void 
	createTF_RunState(
		Menu 				menu, 
		Tag 				tag,
		Predicate<Taggable>	filter,
		int					userMode )
	{

		final TagFeatureRunState	tf_run_state = (TagFeatureRunState)tag;

		int caps = tf_run_state.getRunStateCapabilities();

		int[] op_set = {
				TagFeatureRunState.RSC_START, TagFeatureRunState.RSC_FORCE_START, 
				TagFeatureRunState.RSC_STOP,
				TagFeatureRunState.RSC_PAUSE, TagFeatureRunState.RSC_RESUME };

		boolean[] can_ops_set = tf_run_state.getPerformableOperations( op_set, filter );

		if ((caps & TagFeatureRunState.RSC_START ) != 0 ){

			final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemOp, "MyTorrentsView.menu.queue");
			Utils.setMenuItemImage(itemOp, "start");
			itemOp.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tf_run_state.performOperation( TagFeatureRunState.RSC_START, filter );
				}
			});
			itemOp.setEnabled(can_ops_set[0]);
		}
		
		if ( userMode > 0 ){
			
			if ((caps & TagFeatureRunState.RSC_FORCE_START ) != 0 ){
	
				final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(itemOp, "MyTorrentsView.menu.forceStart");
				Utils.setMenuItemImage(itemOp, "forcestart" );
				itemOp.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						tf_run_state.performOperation( TagFeatureRunState.RSC_FORCE_START, filter );
					}
				});
				itemOp.setEnabled(can_ops_set[1]);
			}
		}

		if ((caps & TagFeatureRunState.RSC_STOP ) != 0 ){

			final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemOp, "MyTorrentsView.menu.stop");
			Utils.setMenuItemImage(itemOp, "stop");
			itemOp.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tf_run_state.performOperation( TagFeatureRunState.RSC_STOP, filter );
				}
			});
			itemOp.setEnabled(can_ops_set[2]);
		}

		if ((caps & TagFeatureRunState.RSC_PAUSE ) != 0 ){

			final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemOp, "v3.MainWindow.button.pause");
			Utils.setMenuItemImage(itemOp, "pause");
			itemOp.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tf_run_state.performOperation( TagFeatureRunState.RSC_PAUSE, filter );
				}
			});
			itemOp.setEnabled(can_ops_set[3]);
		}

		if ((caps & TagFeatureRunState.RSC_RESUME ) != 0 ){

			final MenuItem itemOp = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemOp, "v3.MainWindow.button.resume");
			Utils.setMenuItemImage(itemOp, "start");
			itemOp.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					tf_run_state.performOperation( TagFeatureRunState.RSC_RESUME, filter );
				}
			});
			itemOp.setEnabled(can_ops_set[4]);
		}
	}

	private static void
	createTF_RateLimitMenuItems(
		Menu 		menu,
		Tag 		tag,
		TagType 	tag_type,
		int 		userMode )
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

				DecimalFormat df = new DecimalFormat( "0.000");
				df.setGroupingUsed(false);
				df.setMaximumFractionDigits(3);

				final String existing = df.format( tf_rate_limit.getTagMinShareRatio()/1000.0f);

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

								String text = receiver.getSubmittedInput().trim();

								int	sr = 0;

								if ( text.length() > 0 ){

									try{
										float f = DisplayFormatters.parseFloat( df, text );

										sr = (int)(f * 1000 );

										if ( sr < 0 ){

											sr = 0;

										}else if ( sr == 0 && f > 0 ){

											sr = 1;
										}

										tf_rate_limit.setTagMinShareRatio( sr );

									}catch( Throwable e ){

										MessageBox mb = new MessageBox(Utils.findAnyShell(), SWT.ICON_ERROR | SWT.OK);

										mb.setText(MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
										mb.setMessage(MessageText.getString("MyTorrentsView.dialog.NumberError.text"));

										mb.open();

										Debug.out( e );
									}
								}
							}
						});
					}
				});
			}

			if ( tf_rate_limit.getTagMaxShareRatio() >= 0 ){

				MenuItem itemSR = new MenuItem(menu, SWT.PUSH);

				DecimalFormat df = new DecimalFormat( "0.000");
				df.setGroupingUsed(false);
				df.setMaximumFractionDigits(3);

				final String existing = df.format( tf_rate_limit.getTagMaxShareRatio()/1000.0f);

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

								String text = receiver.getSubmittedInput().trim();

								int	sr = 0;

								if ( text.length() > 0 ){

									try{
										float f = DisplayFormatters.parseFloat( df, text );

										sr = (int)(f * 1000 );

										if ( sr < 0 ){

											sr = 0;

										}else if ( sr == 0 && f > 0 ){

											sr = 1;
										}

										tf_rate_limit.setTagMaxShareRatio( sr );

									}catch( Throwable e ){

										MessageBox mb = new MessageBox(Utils.findAnyShell(), SWT.ICON_ERROR | SWT.OK);

										mb.setText(MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
										mb.setMessage(MessageText.getString("MyTorrentsView.dialog.NumberError.text"));

										mb.open();

										Debug.out( e );
									}
								}
							}
						});
					}
				});
			}
			/**/
		}
	}

	private static void 
	createTF_FileLocationMenuItems(
		Menu 				menu, 
		Tag 				tag,
		Predicate<Taggable>	filter )
	{

		final TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;

		if ( 	fl.supportsTagInitialSaveFolder() || 
				fl.supportsTagMoveOnComplete() || 
				fl.supportsTagCopyOnComplete() ||
				fl.supportsTagMoveOnRemove() ||
				fl.supportsTagMoveOnAssign()){

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
						applyLocationToCurrent( tag, existing, fl.getTagInitialSaveOptions(), 1, filter );
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

				final File existing = fl.getTagMoveOnCompleteFolder();

				if ( existing != null ){
					
					MenuItem current_item = new MenuItem( moc_menu, SWT.PUSH );
					
					current_item.setSelection( true );

					current_item.setText( "[" + existing.getAbsolutePath() + "]" );
					
					current_item.setEnabled(false);
					
					new MenuItem( moc_menu, SWT.SEPARATOR);
				}
				
				MenuItem clear_item = new MenuItem( moc_menu, SWT.PUSH );

				Messages.setLanguageText( clear_item, "Button.clear" );

				clear_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						fl.setTagMoveOnCompleteFolder( null );
					}});


					// apply

				MenuItem apply_item = new MenuItem( moc_menu, SWT.PUSH);

				Messages.setLanguageText( apply_item, "apply.to.current" );

				apply_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						applyLocationToCurrent( tag, existing, fl.getTagMoveOnCompleteOptions(), 2, filter );
					}});

				new MenuItem( moc_menu, SWT.SEPARATOR);

				if ( existing == null ){

					apply_item.setEnabled( false );
					clear_item.setEnabled( false );
				}

					// set
				
				Consumer<String> moc_setter = (path)->{
					
					MenuBuildUtils.addToMOCHistory( path );

					TorrentOpener.setFilterPathData( path );

					fl.setTagMoveOnCompleteFolder( new File( path ));
				};
				
				MenuItem set_item = new MenuItem( moc_menu, SWT.PUSH);

				Messages.setLanguageText( set_item, "label.set" );

				set_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event){
						DirectoryDialog dd = new DirectoryDialog(moc_menu.getShell());

						dd.setFilterPath( TorrentOpener.getFilterPathData());

						dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

						String path = dd.open();

						if ( path != null ){

							moc_setter.accept( path );
						}
					}});
				
				MenuBuildUtils.addMOCHistory( moc_menu, moc_setter );
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
			
			if ( fl.supportsTagMoveOnRemove()){

				final Menu mor_menu = new Menu( files_menu.getShell(), SWT.DROP_DOWN);

				MenuItem mor_item = new MenuItem( files_menu, SWT.CASCADE);

				Messages.setLanguageText( mor_item, "label.move.on.rem" );

				mor_item.setMenu( mor_menu );

				MenuItem clear_item = new MenuItem( mor_menu, SWT.CASCADE);

				Messages.setLanguageText( clear_item, "Button.clear" );

				clear_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						fl.setTagMoveOnRemoveFolder( null );
					}});

				new MenuItem( mor_menu, SWT.SEPARATOR);

				File existing = fl.getTagMoveOnRemoveFolder();

				if ( existing != null ){

					MenuItem current_item = new MenuItem( mor_menu, SWT.RADIO );
					current_item.setSelection( true );

					current_item.setText( existing.getAbsolutePath());

					new MenuItem( mor_menu, SWT.SEPARATOR);

				}else{

					clear_item.setEnabled( false );
				}

				MenuItem set_item = new MenuItem( mor_menu, SWT.CASCADE);

				Messages.setLanguageText( set_item, "label.set" );

				set_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event){
						DirectoryDialog dd = new DirectoryDialog(mor_menu.getShell());

						dd.setFilterPath( TorrentOpener.getFilterPathData());

						dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

						String path = dd.open();

						if ( path != null ){

							TorrentOpener.setFilterPathData( path );

							fl.setTagMoveOnRemoveFolder( new File( path ));
						}
					}});
			}
			
			if ( fl.supportsTagMoveOnAssign()){

				final Menu mor_menu = new Menu( files_menu.getShell(), SWT.DROP_DOWN);

				MenuItem mor_item = new MenuItem( files_menu, SWT.CASCADE);

				Messages.setLanguageText( mor_item, "label.move.on.assign" );

				mor_item.setMenu( mor_menu );

				MenuItem clear_item = new MenuItem( mor_menu, SWT.CASCADE);

				Messages.setLanguageText( clear_item, "Button.clear" );

				clear_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						fl.setTagMoveOnAssignFolder( null );
					}});

					// apply
	
				File existing = fl.getTagMoveOnAssignFolder();
	
				MenuItem apply_item = new MenuItem( mor_menu, SWT.CASCADE);
	
				Messages.setLanguageText( apply_item, "apply.to.current" );
	
				apply_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						applyLocationToCurrent( tag, existing, fl.getTagMoveOnAssignOptions(), 3, filter );
					}});

				new MenuItem( mor_menu, SWT.SEPARATOR);

				if ( existing != null ){

					MenuItem current_item = new MenuItem( mor_menu, SWT.RADIO );
					current_item.setSelection( true );

					current_item.setText( existing.getAbsolutePath());

					new MenuItem( mor_menu, SWT.SEPARATOR);

				}else{
					
					clear_item.setEnabled( false );
					
					apply_item.setEnabled( false );
				}

				MenuItem set_item = new MenuItem( mor_menu, SWT.CASCADE);

				Messages.setLanguageText( set_item, "label.set" );

				set_item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event){
						DirectoryDialog dd = new DirectoryDialog(mor_menu.getShell());

						dd.setFilterPath( TorrentOpener.getFilterPathData());

						dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

						String path = dd.open();

						if ( path != null ){

							TorrentOpener.setFilterPathData( path );

							fl.setTagMoveOnAssignFolder( new File( path ));
						}
					}});
			}
			
			int userMode = COConfigurationManager.getIntParameter("User Mode");
			
			if ( userMode > 0 && tag instanceof TagDownload){
				
				Set<DownloadManager>	downloads = ((TagDownload)tag).getTaggedDownloads();
				
				DownloadManager[] dms = downloads.stream().filter(filter).collect(Collectors.toList()).toArray( new DownloadManager[0] );
				
				Comparator<String> comp = new FormattersImpl().getAlphanumericComparator( true );
				
				Arrays.sort( dms, (d1,d2)->{
				
					return( comp.compare( d1.getDisplayName(), d2.getDisplayName()));
				});
				
				MenuItem itemFileMoveDataBatch = new MenuItem(files_menu, SWT.PUSH);
				
				Messages.setLanguageText(itemFileMoveDataBatch, "MyTorrentsView.menu.movedata.batch");
				
				itemFileMoveDataBatch.addListener(SWT.Selection, new ListenerDMTask(dms) {
					@Override
					public void run(DownloadManager[] dms) {
						TorrentUtil.moveDataFiles(menu.getShell(), dms, true);
					}
				});
				
				itemFileMoveDataBatch.setEnabled( dms.length > 0 );
			}
		}
	}

	private static void 
	createTFProperitesMenuItems(
		Menu menu, Tag tag) 
	{
		final Menu props_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

		MenuItem props_item = new MenuItem( menu, SWT.CASCADE);

		Messages.setLanguageText( props_item, "label.properties" );

		props_item.setMenu( props_menu );

		addColourChooser(
				props_menu,
			"label.color",
			!tag.isColorDefault(),
			tag,
			(selected)->{
				tag.setColor(selected==null?null:new int[] { selected.red, selected.green, selected.blue});
			});
		
		if ( !tag.getTagType().hasTagTypeFeature( TagFeature.TF_PROPERTIES )){

			return;
		}
		
		TagFeatureProperties props = (TagFeatureProperties)tag;

		TagProperty[] tps = props.getSupportedProperties();

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

					reapply_item.addListener(SWT.Selection,
							(ListenerGetOffSWT) event -> tp.syncListeners());

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

					String 	def_str;
					String	msg_str = null;
					
					if ( val == null || val.length == 0 ){

						def_str = "";

					}else{

						def_str = "";

						for ( String v: val ){

							if ( def_str.length() > 100 && msg_str == null ){
								
								msg_str = def_str + "...";
							}

							def_str += (def_str.length()==0?"":", ") + v;								
						}
					}

					if ( msg_str == null ){
						
						msg_str = def_str;
					}
					
					MenuItem set_item = new MenuItem( props_menu, SWT.PUSH);

					set_item.setText( tp.getName( true ) + (msg_str.length()==0?"":(" (" + msg_str + ") ")) + "..." );

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

							itemShare.setText(buddy.getName() + ( buddy.isPublicNetwork()?"":(" (" + MessageText.getString( "label.anon.medium" ) + ")")));

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

				
				t_menu.addMenuListener(
					new MenuListener(){
						
						@Override
						public void menuShown(MenuEvent e){
							for ( MenuItem mi: t_menu.getItems()){
								mi.dispose();
							}
							
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
						
						@Override
						public void menuHidden(MenuEvent e){
							MenuItem[] items = t_menu.getItems();
							
							Utils.execSWTThreadLater(
								1,
								new Runnable(){
									
									@Override
									public void run(){
										for ( MenuItem mi: items){
											mi.dispose();
										}
									}
								});
							
						}
					});
				
			}
		}
	}

	private static void
		createCloseableMenuItems(
				Menu menu,
				TagType tag_type,
				Menu[] menuShowHide,
				boolean needs_separator_next)
	{

		final List<Tag>	tags = tag_type.getTags();

		final Map<Tag, Integer> mapTaggableCount = new HashMap<>();

		for ( Tag t: tags ){
			if ( t.isVisible()){
				mapTaggableCount.put(t, 1);
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

		if (mapTaggableCount.size() != tags.size()){
			MenuItem showAll = new MenuItem(menuShowHide[0], SWT.PUSH);
			Messages.setLanguageText(showAll, "label.show.all");
			showAll.addListener(SWT.Selection, (ListenerGetOffSWT) event -> {
				for ( Tag t: tags ){

					if ( !t.isVisible()){
						t.setVisible( true );
					}
				}
			});

			needs_separator_next = true;
		}

		if (!mapTaggableCount.isEmpty()){
			MenuItem hideAll = new MenuItem(menuShowHide[0], SWT.PUSH);
			Messages.setLanguageText(hideAll, "popup.error.hideall");
			hideAll.addListener(SWT.Selection, (ListenerGetOffSWT) event -> {
				for ( Tag t: tags ){

					if ( t.isVisible()){
						t.setVisible( false );
					}
				}
			});

			needs_separator_next = true;
		}

		showhideitem.setEnabled( true );

		if (tags.isEmpty()) {
			return;
		}

		if ( needs_separator_next ){

			new MenuItem( menuShowHide[0], SWT.SEPARATOR);
		}

		TagMenuOptions.Builder builder = TagMenuOptions.Builder()
			.setMapTaggableCount(mapTaggableCount, 1)
			.setTagMenuFilter(TagMenuOptions.FILTER_NO_AUTOADDREMOVE)
			.setTagSelectionListener(Tag::setVisible)
			.setMenuForAutoTags(false)
			.setShowAddMenu(false)
			.setTagType(tag_type.getTagType());
		createTagSelectionMenu(builder, menuShowHide[0]);
	}

	private static void
	createNonAutoMenuItems(
		Menu 		menu,
		Tag 		tag,
		TagType 	tag_type,
		Menu[] 		menuShowHide )
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
		
		List<Tag> tags = new ArrayList<>();
		
		tags.add( tag );

		createTagGroupMenu( menu, tag_type, tags );
		
		MenuItem itemDelete = new MenuItem(menu, SWT.PUSH);

		Utils.setMenuItemImage(itemDelete, "delete");

		Messages.setLanguageText(itemDelete, "FileItem.delete");
		itemDelete.addListener(SWT.Selection, event -> removeTags(tag));
	}

	private static void
	createTagGroupMenu(
		Menu			menu,
		TagType			tag_type,
		List<Tag>		tagz )
	{
		if ( tag_type.getTagType() != TagType.TT_DOWNLOAD_MANUAL || tagz.isEmpty()){
			
			return;
		}
		
		List<TagGroup>	groups = new ArrayList<>();
		
		List<Tag> all_tags = tag_type.getTags();
		
		for ( Tag t : all_tags ){
			
			TagGroup group = t.getGroupContainer();
			
			if ( group != null && group.getName() != null  && !groups.contains(group)){
				
				groups.add( group );
			}
		}

		groups = TagUtils.sortTagGroups( groups );
		
		Menu groups_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
		MenuItem groups_item = new MenuItem(menu, SWT.CASCADE );
		Messages.setLanguageText(groups_item, "TableColumn.header.tag.group" );
		groups_item.setMenu(groups_menu);
		
		String existing_group = tagz.get(0).getGroup();
		
		if ( existing_group == null ){
		
			existing_group = "";
		}
		
		for ( Tag t: tagz ){
			
			String g = t.getGroup();
			
			if ( g == null ){
				
				g = "";
			}
			
			if ( !g.equals( existing_group )){
				
				existing_group = null;
				
				break;
			}
		}
		
		MenuItem item_none = new MenuItem(groups_menu, SWT.RADIO);
		Messages.setLanguageText(item_none, "label.none" );
		item_none.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event){
				for ( Tag t: tagz ){
					t.setGroup( null );
				}
			}
		});
		
		item_none.setSelection( existing_group != null && existing_group.isEmpty() );
		
		new MenuItem( groups_menu, SWT.SEPARATOR );
		
		if ( !groups.isEmpty()){
			
			for ( TagGroup g: groups ){
				
				MenuItem item_group = new MenuItem(groups_menu, SWT.RADIO);
				
				item_group.setText( g.getName());
				
				item_group.setSelection( g.getName().equals( existing_group ));
				
				item_group.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event){
						for ( Tag t: tagz ){
							t.setGroup( g.getName());
						}
					}
				});
			}
		
			new MenuItem( groups_menu, SWT.SEPARATOR );
		}
		
		MenuItem item_add = new MenuItem(groups_menu, SWT.PUSH);

		final String f_existing_group = existing_group;
				
		Messages.setLanguageText(item_add, "menu.add.group");
		item_add.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"TagGroupWindow.title", "TagGroupWindow.message");

				if ( f_existing_group != null ){
					
					entryWindow.setPreenteredText( f_existing_group, false );
				}
				
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
							for ( Tag t: tagz ){
								t.setGroup(group);
							}

						} catch (Throwable e) {

							Debug.out(e);
						}
					}
				});
			}
		});
		
		new MenuItem( groups_menu, SWT.SEPARATOR );

		Menu settings_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
		MenuItem settings_item = new MenuItem(groups_menu, SWT.CASCADE );
		Messages.setLanguageText(settings_item, "TagSettingsView.title" );
		settings_item.setMenu(settings_menu);
		
		if ( groups.isEmpty()){
			
			settings_menu.setEnabled( false );
			
		}else{
			
			for ( TagGroup g: groups ){
				
				Menu tg_settings_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
				MenuItem tg_settings_item = new MenuItem(settings_menu, SWT.CASCADE );
				tg_settings_item.setText(g.getName());
				tg_settings_item.setMenu(tg_settings_menu);

				addTagGroupMenu( tg_settings_menu, g );
			}
		}
	}
	
	
	private static boolean
	addTagGroupMenu(
		Menu		menu,
		TagGroup	group )
	{
		int tt = group.getTagType().getTagType();
		
		boolean need_sep = false;
		
		if ( tt == TagType.TT_DOWNLOAD_MANUAL ){
			
				// exclusive 
			
			MenuItem exclusive_item = new MenuItem( menu, SWT.CHECK);
			
			exclusive_item.setText( MessageText.getString( "label.exclusive" ));
			
			exclusive_item.setSelection( group.isExclusive());
			
			exclusive_item.addListener( 
				SWT.Selection,
				(e)->{
					group.setExclusive(exclusive_item.getSelection());
				});
			
				// move on assign root
			
			File existing = group.getRootMoveOnAssignLocation();
			
			final Menu moa_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);
	
			MenuItem isl_item = new MenuItem( menu, SWT.CASCADE);
	
			Messages.setLanguageText( isl_item, "label.tag.group.moa.root" );
	
			isl_item.setMenu( moa_menu );
	
			MenuItem clear_item = new MenuItem( moa_menu, SWT.CASCADE);
	
			Messages.setLanguageText( clear_item, "Button.clear" );
	
			clear_item.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					group.setRootMoveOnAssignLocation( null );
				}});
	
	
	
			if ( existing != null ){
	
				MenuItem current_item = new MenuItem( moa_menu, SWT.RADIO );
				current_item.setSelection( true );
	
				current_item.setText( existing.getAbsolutePath());
	
				new MenuItem( moa_menu, SWT.SEPARATOR);
	
			}else{
	
				clear_item.setEnabled( false );
			}
	
			MenuItem set_item = new MenuItem( moa_menu, SWT.CASCADE);
	
			Messages.setLanguageText( set_item, "label.set" );
	
			set_item.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event){
					DirectoryDialog dd = new DirectoryDialog(moa_menu.getShell());
	
					dd.setFilterPath( TorrentOpener.getFilterPathData());
	
					dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));
	
					String path = dd.open();
	
					if ( path != null ){
	
						TorrentOpener.setFilterPathData( path );
	
						group.setRootMoveOnAssignLocation( new File( path ));
					}
				}});
			
			MenuItem rename_item = new MenuItem( menu, SWT.CASCADE);
			
			Messages.setLanguageText( rename_item, "MyTorrentsView.menu.rename" );
	
			rename_item.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							String name = group.getName();
							
							UISWTInputReceiver entry = new SimpleTextEntryWindow();
							entry.setPreenteredText(name, false );
							entry.maintainWhitespace(false);
							entry.allowEmptyInput( false );
							
							entry.setLocalisedTitle(MessageText.getString("label.rename",
									new String[] {
										name
									}));
							
							entry.prompt(new UIInputReceiverListener() {
								@Override
								public void UIInputReceiverClosed(UIInputReceiver entry) {
									if (!entry.hasSubmittedInput()){

										return;
									}

									String input = entry.getSubmittedInput().trim();

									if ( input.length() > 0 ){

										Utils.getOffOfSWTThread(()->{
											group.setName( input );
										});
									}
								}
							});
						}
					});
			
			need_sep = true;
		}
		
		if ( tt == TagType.TT_DOWNLOAD_MANUAL || tt == TagType.TT_PEER_IPSET ){
			
			addColourChooser(
					menu,
					"TagGroup.menu.defaultcolor",
					group.getColor() != null,
					group,
					(selected)->{
						group.setColor( selected==null?null:new int[]{ selected.red, selected.green, selected.blue } );
					});
			
			need_sep = true;
		}	
		
		return( need_sep );
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

		TagType tag_type = tags.get(0).getTagType();
		
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

		createTagGroupMenu( menu, tag_type, tags );

		MenuItem itemDuplicate = new MenuItem(menu, SWT.PUSH);
		
		Messages.setLanguageText(itemDuplicate,"Subscription.menu.duplicate");
		itemDuplicate.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				duplicate(tags);
			}
		});
		
		MenuItem itemExport = new MenuItem(menu, SWT.PUSH);
		
		Messages.setLanguageText(itemExport,"Subscription.menu.export");
		itemExport.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				export(tags);
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
		Tag					tag,
		File				location,
		long				options,
		int					type,
		Predicate<Taggable>	filter )
	{
		move_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					boolean set_data 	= (options&TagFeatureFileLocation.FL_DATA) != 0;
					boolean set_torrent = (options&TagFeatureFileLocation.FL_TORRENT) != 0;

					Set<DownloadManager>	all_downloads = ((TagDownload)tag).getTaggedDownloads();

					List<DownloadManager> downloads = all_downloads.stream().filter(filter).collect(Collectors.toList());

					for ( DownloadManager download: downloads ){

						boolean dl_is_complete = download.isDownloadComplete( false );

						if ( type == 1 && dl_is_complete ){

								// applying initial-save-folder, ignore completed files
								// that have been moved somewhere already
	
							if ( download.getDownloadState().getFlag( DownloadManagerState.FLAG_MOVE_ON_COMPLETION_DONE )){
	
								continue;
							}
						}
						
						if ( type == 2 && !dl_is_complete){

								// applying move-on-complete so ignore incomplete

							continue;
						}

						if ( set_data ){
							
							try{
								File existing_save_loc = download.getSaveLocation();
								
								if ( existing_save_loc.isFile()){
									
									existing_save_loc = existing_save_loc.getParentFile();
								}
								
								if ( ! existing_save_loc.equals( location )){

									download.moveDataFilesLive( location );
								}
							}catch( Throwable e ){
	
								Debug.out( e );
							}
						}
						
						if ( set_torrent ){
							
							File old_torrent_file = new File( download.getTorrentFileName());
				
							if ( old_torrent_file.exists()){
				
								try{
									download.setTorrentFile( location, old_torrent_file.getName());
				
								}catch( Throwable e ){
				
									Debug.out( e );
								}
							}
						}
					}
				}
			});
	}

	public static void
	addLibraryViewTagsSubMenu(
		DownloadManager[] 		dms,
		Menu 					menu_tags)
	{
		TagMenuOptions.Builder builder = TagMenuOptions.Builder()
			.setTaggables(dms)
			.setTagMenuFilter(TagMenuOptions.FILTER_NO_AUTOADDREMOVE)
			.setTagSelectionListener((tag, checked) -> {
				try {
					tag.addTaggableBatch(true);

					for (DownloadManager dm : dms) {
						if (checked) {
							tag.addTaggable(dm);
						} else {
							tag.removeTaggable(dm);
						}
					}

				} finally {
					tag.addTaggableBatch(false);
				}
			});
		createTagSelectionMenu(builder, menu_tags);
	}

	public interface TagMenuFilter {
		List<Tag> filterTags(Tag[] tagsToFilter);
	}

	public static class TagMenuOptions
	{
		public static final TagMenuFilter FILTER_NO_AUTOADDREMOVE = tagsToFilter -> {
			List<Tag> list = new ArrayList<>();
			for (Tag tag : tagsToFilter) {
				if (tag.isTagAuto()[0] && tag.isTagAuto()[1]) {
					continue;
				}
				list.add(tag);
			}
			return list;
		};

		public static final TagMenuFilter FILTER_NO_AUTOADD = tagsToFilter -> {
			List<Tag> list = new ArrayList<>();
			for (Tag tag : tagsToFilter) {
				if (tag.isTagAuto()[0]) {
					continue;
				}
				list.add(tag);
			}
			return list;
		};

		public static final TagMenuFilter FILTER_SHOW_ALL = null;

		private final Taggable[] taggables;

		private final TagMenuFilter tagMenuFilter;

		private final MenuManager menuManager;

		private final com.biglybt.pif.ui.menus.MenuItem parent;

		private final TagSelectionListener tagSelectionListener;

		private final boolean showAddMenu;

		private final boolean menuForAutoTags;

		private final int tagType;

		private final Map<Tag, Integer> mapTaggableCount;

		private final int numTaggables;

		private TagMenuOptions(Builder builder) {
			taggables = builder.taggables;
			tagMenuFilter = builder.tagMenuFilter;
			menuManager = builder.menuManager == null
					? PluginInitializer.getDefaultInterface().getUIManager().getMenuManager()
					: builder.menuManager;
			parent = builder.parent;
			tagSelectionListener = builder.tagSelectionListener;
			showAddMenu = builder.showAddMenu;
			menuForAutoTags = builder.menuForAutoTags;
			tagType = builder.tagType;
			mapTaggableCount = builder.mapTaggableCount;
			numTaggables = builder.numTaggables;
		}

		public static Builder Builder() {
			return new Builder();
		}

		public static final class Builder
		{
			private Taggable[] taggables;

			private TagMenuFilter tagMenuFilter;

			private MenuManager menuManager;

			private com.biglybt.pif.ui.menus.MenuItem parent;

			private TagSelectionListener tagSelectionListener;

			private boolean showAddMenu = true;

			private boolean menuForAutoTags = true;

			private int tagType = TagType.TT_DOWNLOAD_MANUAL;

			private Map<Tag, Integer> mapTaggableCount;

			private int numTaggables;

			public Builder() {
			}

			public TagMenuOptions build() {
				return new TagMenuOptions(this);
			}

			/**
			 * @param parent Append MenuItems to this parent
			 */
			public Builder setParentPluginMenuItem(
					com.biglybt.pif.ui.menus.MenuItem parent) {
				this.parent = parent;
				return this;
			}

			public Builder setTagSelectionListener(
					TagSelectionListener tagSelectionListener) {
				this.tagSelectionListener = tagSelectionListener;
				return this;
			}

			/**
			 * {@link Taggable}s will be scanned for tag selection.
			 * <br>
			 * {@link #setMapTaggableCount(Map, int)} is not needed when taggables is set
			 */
			public Builder setTaggables(Taggable[] taggables) {
				this.taggables = taggables;
				return this;
			}

			/**
			 * Filters the tags before being added as menu items
			 * <p>
			 * See {@link #FILTER_NO_AUTOADDREMOVE}, {@link #FILTER_NO_AUTOADD}, {@link #FILTER_SHOW_ALL}
			 */
			public Builder setTagMenuFilter(TagMenuFilter tagMenuFilter) {
				this.tagMenuFilter = tagMenuFilter;
				return this;
			}

			public Builder setMenuManager(MenuManager menuManager) {
				this.menuManager = menuManager;
				return this;
			}

			public Builder setShowAddMenu(boolean showAddMenu) {
				this.showAddMenu = showAddMenu;
				return this;
			}

			/**
			 * Whether to place Auto tags in their own menu (unselectable),
			 * or to include them with the rest of the tags.
			 * <p></p>
			 * Default is true
			 */
			public Builder setMenuForAutoTags(boolean menuForAutoTags) {
				this.menuForAutoTags = menuForAutoTags;
				return this;
			}

			/**
			 * See {@link TagType}'s TT_* constants
			 * <p></p>
			 * Default is {@link TagType#TT_DOWNLOAD_MANUAL}
			 */
			public Builder setTagType(int tagType) {
				this.tagType = tagType;
				return this;
			}

			/**
			 * @param mapTaggableCount selected tags -> the # of taggables that have the tag selected
			 */
			public Builder setMapTaggableCount(Map<Tag, Integer> mapTaggableCount,
					int numTaggables) {
				this.mapTaggableCount = mapTaggableCount;
				this.numTaggables = numTaggables;
				return this;
			}
		}
	}

	/**
	 * @param options See {@link TagMenuOptions#Builder()}
	 */
	public static void
	createTagMenu(TagMenuOptions options)
	{

		final TagManager tm = TagManagerFactory.getTagManager();

		boolean hasTaggables = options.taggables != null && options.taggables.length > 0;

		final MenuManager mm = options.menuManager;

		boolean needsSeparator = false;

		if (options.showAddMenu) {
			mm.addMenuItem(options.parent, "label.add.tag").addMultiListener(
					(menu, target) -> createManualTag(tags -> {
						for (Tag new_tag : tags) {
							options.tagSelectionListener.selected(new_tag, true);
						}
					}));
			needsSeparator = true;
		}

			// tagging view 

		if (hasTaggables && options.taggables[0] instanceof DownloadManager) {
			DownloadManager[] dms = (DownloadManager[]) options.taggables;

			com.biglybt.pif.ui.menus.MenuItem itemPop = mm.addMenuItem(options.parent, "menu.tagging.view");

			itemPop.addMultiListener((menu, target) -> { 
								
				String data = "{\"mdi\":\"tabbed\",\"event_listener\":{\"name\":\"com.biglybt.ui.swt.views.TaggingView\"},\"id\":\"TaggingView\",\"control_type\":0,\"skin_id\":\"com.biglybt.ui.skin.skin3\"}";
				
				Map<String,Object> map = BDecoder.decodeStrings( BDecoder.decodeFromJSON( data ));

				Map<String,Object> dms_exports = DataSourceResolver.exportDataSource( dms );

				map.put( "data_source", dms_exports );
				
				String title;
				
				if ( dms.length == 1 ){
					
					title = MessageText.getString( "authenticator.torrent" ) + " : " + dms[0].getDisplayName().replaceAll("&", "&&");
					
				}else{
					String	str = "";

					for (int i=0;i<Math.min( 3, dms.length ); i ++ ){

						str += (i==0?"":", ") + dms[i].getDisplayName().replaceAll("&", "&&");
					}

					if ( dms.length > 3 ){

						str += "...";
					}

					title = dms.length + " " + MessageText.getString( "ConfigView.section.torrents" ) + " : " + str;
				}
				
				PopOutManager.popOutStandAlone( MessageText.getString( "label.tags" ) + " - " + title, map, "TagUIUtils:TaggingView" );
			});

			needsSeparator = true;
		}

		if (needsSeparator) {
			mm.addMenuItem(options.parent, "s1").setStyle(STYLE_SEPARATOR);
			needsSeparator = false;
		}

			// auto tags
		
		Map<TagType,List<Tag>>	auto_map = new HashMap<>();

		TagType tt = tm.getTagType(options.tagType);

		boolean buildTaggableCount = options.mapTaggableCount == null;
		Map<Tag,Integer>	mapTaggableCount = buildTaggableCount ? new HashMap<>() : options.mapTaggableCount;
		
		if (hasTaggables && (buildTaggableCount || options.menuForAutoTags)) {

			for ( Taggable taggable : options.taggables ){

				List<Tag> tags = tm.getTagsForTaggable( taggable );

				for ( Tag t: tags ){

					TagType ttTag = t.getTagType();

					if (options.menuForAutoTags && (ttTag.isTagTypeAuto()
							|| (t.isTagAuto()[0] && t.isTagAuto()[1]))) {

						auto_map.computeIfAbsent(ttTag, k -> new ArrayList<>()).add(t);

					}
					if ( buildTaggableCount && ttTag == tt ){

						mapTaggableCount.compute(t, (t2, num) -> num == null ? 1 : num + 1);
					}
				}
			}
		}

		if (!auto_map.isEmpty()){
			// Make a menu called "Auto", with an item for each tag type
			// each item displays the tag type name, the tags selected 
			// and the # of times the tag has been added (ie multiple taggables have it selected)

			com.biglybt.pif.ui.menus.MenuItem autoItem = mm.addMenuItem(options.parent, "wizard.maketorrent.auto");
			autoItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);

			List<TagType>	auto_tags = TagUtils.sortTagTypes( auto_map.keySet());

			for ( TagType ttAutoTags : auto_tags ){

				String tt_str = ttAutoTags.getTagTypeName( true ) + ": ";

				List<Tag> tags = auto_map.get( ttAutoTags );

				Map<Tag,Integer>	tag_counts = new HashMap<>();

				for ( Tag t: tags ){

					Integer i = tag_counts.get( t );

					tag_counts.put( t, i==null?1:i+1 );
				}

				tags = TagUtils.sortTags( tag_counts.keySet());

				int	 num = 0;

				for ( Tag t: tags ){

					tt_str += (num==0?"":", " ) + t.getTagName( true );

					num++;

					if ( hasTaggables && options.taggables.length > 1 ){

						tt_str += " (" + tag_counts.get( t ) + ")";
					}
				}

				com.biglybt.pif.ui.menus.MenuItem tt_i = mm.addMenuItem(autoItem, tt_str);
				tt_i.setText( tt_str );
				if ( Constants.isOSX ){
					tt_i.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK);
					tt_i.setData(true);
				}else{
					Utils.setMenuItemImage( tt_i, "graytick" );
				}
			}

			needsSeparator = true;
		}

		List<Tag>	all_tags = tt.getTags();
		if (options.tagMenuFilter != null) {
			all_tags = options.tagMenuFilter.filterTags(all_tags.toArray(new Tag[0]));
		}

		if (all_tags.isEmpty()) {
			return;
		}

		if (needsSeparator){
			needsSeparator = false;
			mm.addMenuItem(options.parent, "s2").setStyle(STYLE_SEPARATOR);
		}

		Collection<TagGroup>	tag_groups 		= new HashSet<>();
		List<Tag>		tag_no_group	= new ArrayList<>();

		for ( Tag t: all_tags ){

			TagGroup tg = t.getGroupContainer();

			if ( tg == null || tg.getName() == null ){

				tag_no_group.add( t );

			}else{

				tag_groups.add( tg );
			}
		}

		int numTaggables = hasTaggables ? options.taggables.length : options.numTaggables;

		if ( !tag_groups.isEmpty()){
			List<TagGroup> l_tg = TagUtils.sortTagGroups( tag_groups );

			for ( TagGroup tg: l_tg ){

				com.biglybt.pif.ui.menus.MenuItem groupItem = mm.addMenuItem(options.parent, tg.getName());
				groupItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);

				// Note: If tg.isExclusive() and only one numTaggables, we could
				//       use a radio button
				
				List<Tag> groupTags = tg.getTags();
				
				if (options.tagMenuFilter != null) {
					groupTags = options.tagMenuFilter.filterTags(groupTags.toArray(new Tag[0]));
				}
				
				int numChecked = build(mm, groupItem, groupTags, numTaggables,
						mapTaggableCount, options.tagSelectionListener);

				groupItem.setText(addCountToTagMenuLabel(tg.getName(), numChecked,
						groupItem.getItemCount()));
			}

			if ( !tag_no_group.isEmpty()){

				com.biglybt.pif.ui.menus.MenuItem groupItem = mm.addMenuItem(options.parent, "menu.no.group");
				groupItem.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);

				int numChecked = build(mm, groupItem, tag_no_group, numTaggables,
						mapTaggableCount, options.tagSelectionListener);
				if (numChecked > 0) {
					groupItem.setText(addCountToTagMenuLabel(groupItem.getText(),
							numChecked, groupItem.getItemCount()));
				}
			}

			mm.addMenuItem(options.parent, "s3").setStyle(STYLE_SEPARATOR);
		}

		// Put all the tags in a "By Alphabet" menu if we have enough tags groups and tags 
		com.biglybt.pif.ui.menus.MenuItem menuForAll = options.parent;
		if (tag_groups.size() > 2 && all_tags.size() > 5) {
			menuForAll = mm.addMenuItem(options.parent, "menu.by.alphabet");
			menuForAll.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);
		}

		build(mm, menuForAll, all_tags, numTaggables, mapTaggableCount,
				options.tagSelectionListener);

	}

	private static String addCountToTagMenuLabel(String name, int numChecked,
			int numItems) {
		String label = name;
		if (numChecked > 0) {
			if (numChecked != numItems) {
				label += "\t" + numChecked + "/" + numItems;
			} else {
				label += "\t\u25C9";
			}
		}
		return label;
	}

	/**
	 * @param menuManager
	 * @param menu_tags Menu to put MenuItems in
	 * @param all_tags All tags to display
	 * @param numTaggables # of items (Taggables) we are setting this menu up for
	 * @param mapTaggableCount
	 * @param tagSelectionListener
	 * @return # of items checked
	 */
	private static int
	build(
		MenuManager menuManager,
		com.biglybt.pif.ui.menus.MenuItem menu_tags,
		List<Tag> all_tags, 
		int numTaggables,
		Map<Tag, Integer> mapTaggableCount,
		TagSelectionListener tagSelectionListener)
	{
		int numChecked = 0;
		List<String>	menu_names 		= new ArrayList<>();
		Map<String,Tag>	menu_name_map 	= new IdentityHashMap<>();

		for ( Tag t: all_tags ){

			String name = t.getTagName( true );

			menu_names.add( name );
			menu_name_map.put( name, t );
		}

		List<Object>	menu_structure = MenuBuildUtils.splitLongMenuListIntoHierarchy( menu_names, MAX_TOP_LEVEL_TAGS_IN_MENU );

		for ( Object obj: menu_structure ){

			List<Tag>	bucket_tags = new ArrayList<>();

			com.biglybt.pif.ui.menus.MenuItem	 parent_menu;

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

					Integer count = mapTaggableCount.get(sub_tag);
					if (count != null && count > 0) {

						sub_some_selected = true;

					}else{

						sub_all_selected = false;
					}

					bucket_tags.add( sub_tag );
				}

				String mod;

				if ( sub_all_selected ){

					mod = "\t\u25C9";

				}else if ( sub_some_selected ){

					mod = "\t\u25D4";

				}else{

					mod = "";
				}

				String title = entry[0] + mod;

				parent_menu = menuManager.addMenuItem(menu_tags, title);
				parent_menu.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);
				parent_menu.setText(title);
			}

			for ( final Tag t: bucket_tags ){
				String title = t.getTagName(true);

				Integer count = mapTaggableCount.get(t);
				int c = count == null ? 0 : count;

				boolean checked = c > 0;
				boolean partial = checked && c != numTaggables;

				if (checked){
					numChecked++;
					if (partial) {
						title += "\t" + c + "/" + numTaggables;
					}
				}

				com.biglybt.pif.ui.menus.MenuItem t_i = menuManager.addMenuItem(parent_menu, title);
				t_i.setText(title);
				t_i.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_CHECK);
				t_i.setData(checked);

				TagUIUtils.setMenuIcon( t_i, t );
				
				// When some, but not all, taggables are selected (partial), default
				// to selected
				t_i.addListener((menu, target) -> tagSelectionListener.selected(t,
						partial || menu.isSelected()));
			}
		}
		return numChecked;
	}

	/**
	 * Creates a single-select tag menu
	 * 
	 * @param swtTopMenu SWT menu to add tag menu items to.
	 */
	public static void createTagSelectionMenu(TagMenuOptions.Builder builder,
			Menu swtTopMenu) {

		MenuManager mm = builder.menuManager == null
				? PluginInitializer.getDefaultInterface().getUIManager().getMenuManager()
				: builder.menuManager;

		String menuId = "TagsMenu." + SystemTime.getCurrentTime();
		com.biglybt.pif.ui.menus.MenuItem parent = mm.addMenuItem(menuId, "Tags");
		parent.setStyle(com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU);

		TagMenuOptions tagMenuOptions = builder
			.setParentPluginMenuItem(parent)
			.setMenuManager(mm)
			.build();
		createTagMenu(tagMenuOptions);

		MenuBuildUtils.addPluginMenuItems(parent.getItems(), swtTopMenu, true, true,
				new MenuBuildUtils.MenuItemPluginMenuControllerImpl(
						tagMenuOptions.taggables));

		swtTopMenu.addDisposeListener(e -> parent.remove());
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

		final FilesView view = new FilesView();

		view.setDisableWhenEmpty( false );

		view.initialize(shell);

		view.dataSourceChanged( tag);

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

		shell.addDisposeListener(
			e -> {
				UIUpdaterSWT.getInstance().removeUpdater(viewUpdater);
				view.delete();
			});

		shell.layout(true, true);


		shell.setText( tag.getTagName(true));

		shell.open();
	}
	
	protected static void
	export(
		final List<Tag>			tags )
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

					dialog.setText(MessageText.getString("tag.export.select.template.file"));

					dialog.setFilterExtensions(VuzeFileHandler.getVuzeFileFilterExtensions());

					dialog.setFilterNames(VuzeFileHandler.getVuzeFileFilterExtensions());

					String path = TorrentOpener.setFilterPathData( dialog.open());

					if ( path != null ){

						if ( !VuzeFileHandler.isAcceptedVuzeFileName( path )){

							path = VuzeFileHandler.getVuzeFileName( path );
						}

						try{
							VuzeFile vf = TagManagerFactory.getTagManager().exportTags( tags );

							vf.write( new File( path ));

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			});
	}

	protected static void
	duplicate(
		final List<Tag>			tags )
	{
		TagManager tm = TagManagerFactory.getTagManager();
		
		for ( Tag tag: tags ){
			
			tm.duplicate( tag );
		}
	}
	
	public static void openRenameTagDialog(Tag tag) {
		if (tag == null || tag.getTagType().isTagTypeAuto()) {
			return;
		}

		UIInputReceiver entry = new SimpleTextEntryWindow();
		String tagName = tag.getTagName(true);
		entry.setPreenteredText(tagName, false);
		entry.maintainWhitespace(false);
		entry.allowEmptyInput(false);
		entry.setLocalisedTitle(MessageText.getString("label.rename", new String[] {
			tagName
		}));
		entry.prompt(result -> {
			if (!result.hasSubmittedInput()) {
				return;
			}

			String input = result.getSubmittedInput().trim();

			if (input.length() > 0) {
				try {
					tag.setTagName(input);
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
		});
	}

	public static boolean canDeleteTag(Tag tag) {
		if (tag == null) {
			return false;
		}
		
		int tt = tag.getTagType().getTagType();
		
		if ( tt == TagType.TT_DOWNLOAD_MANUAL || tt == TagType.TT_SWARM_TAG ){
			
			return( true );
		}
		
		return((tag instanceof Category) && ((Category) tag).getType() == Category.TYPE_USER );
	}


	public static boolean removeTags(Tag... tags) {
		return removeTags(new ArrayList<Tag>(Arrays.asList(tags)));
	}

	public static boolean removeTags(List<Tag> tags) {
		Tag tagToRemove = null;

		while (tagToRemove == null && tags.size() > 0) {
			tagToRemove = tags.get(0);
			if (!canDeleteTag(tagToRemove)) {
				tags.remove(0);
				tagToRemove = null;
			}
		}
		int numLeft = tags.size();
		if (tagToRemove == null || numLeft == 0) {
			return true;
		}

		MessageBoxShell mb = new MessageBoxShell(
			MessageText.getString("message.confirm.delete.title"),
			MessageText.getString("message.confirm.delete.tag.text", new String[] {
				tagToRemove.getTagName(true),
				"" + tagToRemove.getTaggedCount()
			}), new String[] {
			MessageText.getString("Button.yes"),
			MessageText.getString("Button.no")
		}, 1);

		if (numLeft > 1) {
			String sDeleteAll = MessageText.getString("v3.deleteContent.applyToAll",
				new String[] {
					"" + numLeft
				});
			mb.addCheckBox("!" + sDeleteAll + "!", Parameter.MODE_BEGINNER, false);
		}
		Tag finalTagToRemove = tagToRemove;
		mb.open(result -> {
			if (result == -1) {
				// cancel
				return;
			}
			boolean remove = result == 0;
			boolean doAll = mb.getCheckBoxEnabled();
			if (doAll) {
				if (remove) {
					for (Tag tag : tags) {
						if (canDeleteTag(tag)) {
							tag.removeTag();
						}
					}
				}
			} else {
				if (remove) {
					finalTagToRemove.removeTag();
				}
				// Loop with remaining tags to be removed
				tags.remove(0);
				removeTags(tags);
			}
		});

		return true;
	}

	public interface
	TagSelectionListener
	{
		void selected(Tag tag, boolean checked);
	}
}
