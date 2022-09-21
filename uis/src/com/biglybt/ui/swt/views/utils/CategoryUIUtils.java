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

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.biglybt.core.download.DownloadManagerState;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginBuddy;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.ViewUtils;
import com.biglybt.ui.swt.views.ViewUtils.SpeedAdapter;

/**
 * @author TuxPaper
 * @created Nov 15, 2010
 *
 */
public class CategoryUIUtils
{
	public static void setupCategoryMenu(final Menu menu, final Category category, Predicate<Taggable> filter ) {
		menu.addMenuListener(new MenuListener() {
			boolean bShown = false;

			@Override
			public void menuHidden(MenuEvent e) {
				bShown = false;

				if (Constants.isOSX || e.display == null) {
					return;
				}

				// Must dispose in an asyncExec, otherwise SWT.Selection doesn't
				// get fired (async workaround provided by Eclipse Bug #87678)

				e.display.asyncExec(new AERunnable() {
					@Override
					public void runSupport() {
						if (bShown || menu.isDisposed())
							return;
						Utils.disposeSWTObjects((Object[]) menu.getItems());
					}
				});
			}

			@Override
			public void menuShown(MenuEvent e) {
				Utils.disposeSWTObjects((Object[]) menu.getItems());

				bShown = true;

				createMenuItems(menu, category, filter );
			}
		});
	}

	public static void 
	createMenuItems(
		Menu 							menu, 
		Category 						category )
	{
		createMenuItems( menu, category, (dm)->true );
	}
	
	private static List<DownloadManager>
	getDownloads(
		Category			category,
		Predicate<Taggable>	filter )
	{
		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
		
		List<DownloadManager> managers = category.getDownloadManagers(gm.getDownloadManagers());
		
		return( managers.stream().filter(filter).collect( Collectors.toList()));
	}
	
	public static void 
	createMenuItems(
		Menu 						menu, 
		Category 					category, 
		Predicate<Taggable> 		filter ) 
	{
		if (category.getType() == Category.TYPE_USER) {

			final MenuItem itemDelete = new MenuItem(menu, SWT.PUSH);

			Messages.setLanguageText(itemDelete,
					"MyTorrentsView.menu.category.delete");

			itemDelete.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					List<DownloadManager> dms = getDownloads( category, filter );
					for (DownloadManager dm : dms) {
						DownloadManagerState state = dm.getDownloadState();
						if (state != null) {
							state.setCategory(null);
						}
					}
					CategoryManager.removeCategory(category);
				}
			});
		}

		if (category.getType() != Category.TYPE_ALL) {

			long kInB = DisplayFormatters.getKinB();

			long maxDownload = COConfigurationManager.getIntParameter(
					"Max Download Speed KBs", 0) * kInB;
			long maxUpload = COConfigurationManager.getIntParameter(
					"Max Upload Speed KBs", 0) * kInB;

			int down_speed = category.getDownloadSpeed();
			int up_speed = category.getUploadSpeed();

			ViewUtils.addSpeedMenu(menu.getShell(), menu, true, true, true, true, false,
					down_speed == 0, down_speed, down_speed, maxDownload, false,
					up_speed == 0, up_speed, up_speed, maxUpload, 1, null,
					new SpeedAdapter() {
						@Override
						public void setDownSpeed(int val) {
							category.setDownloadSpeed(val);
						}

						@Override
						public void setUpSpeed(int val) {
							category.setUploadSpeed(val);

						}
					});
		}

		List<DownloadManager> dms = getDownloads( category, filter );

		boolean start = false;
		boolean stop = false;

		for (DownloadManager dm : dms) {

			stop = stop || ManagerUtils.isStopable(dm);

			start = start || ManagerUtils.isStartable(dm);

		}

		// Queue

		final MenuItem itemQueue = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemQueue, "MyTorrentsView.menu.queue");
		Utils.setMenuItemImage(itemQueue, "start");
		itemQueue.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				List<DownloadManager> dms = getDownloads( category, filter );
				TorrentUtil.queueDataSources(dms.toArray(), false);
			}
		});
		itemQueue.setEnabled(start);

		// Stop

		final MenuItem itemStop = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemStop, "MyTorrentsView.menu.stop");
		Utils.setMenuItemImage(itemStop, "stop");
		itemStop.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				List<DownloadManager> dms = getDownloads( category, filter );
				TorrentUtil.stopDataSources(dms.toArray());
			}
		});
		itemStop.setEnabled(stop);

		if ( category.canBePublic()){

			new MenuItem( menu, SWT.SEPARATOR);

			final MenuItem itemPublic = new MenuItem(menu, SWT.CHECK );

			itemPublic.setSelection( category.isPublic());

			Messages.setLanguageText(itemPublic, "cat.share");

			itemPublic.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {

					category.setPublic( itemPublic.getSelection());
				}});
		}

		// share with friends

		PluginInterface bpi = PluginInitializer.getDefaultInterface().getPluginManager().getPluginInterfaceByClass(
				BuddyPlugin.class);

		int cat_type = category.getType();

		if (bpi != null && cat_type != Category.TYPE_UNCATEGORIZED) {

			final BuddyPlugin buddy_plugin = (BuddyPlugin) bpi.getPlugin();

			if ( buddy_plugin.isClassicEnabled()){

				final Menu share_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
				final MenuItem share_item = new MenuItem(menu, SWT.CASCADE);
				Messages.setLanguageText(share_item, "azbuddy.ui.menu.cat.share");
				share_item.setMenu(share_menu);

				List<BuddyPluginBuddy> buddies = buddy_plugin.getBuddies();

				if (buddies.size() == 0) {

					final MenuItem item = new MenuItem(share_menu, SWT.CHECK);

					item.setText(MessageText.getString("general.add.friends"));

					item.setEnabled(false);

				} else {
					final String cname;

					if (cat_type == Category.TYPE_ALL) {

						cname = "All";

					} else {

						cname = category.getName();
					}

					final boolean is_public = buddy_plugin.isPublicTagOrCategory(cname);

					final MenuItem itemPubCat = new MenuItem(share_menu, SWT.CHECK);

					Messages.setLanguageText(itemPubCat, "general.all.friends");

					itemPubCat.setSelection(is_public);

					itemPubCat.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
							if (is_public) {

								buddy_plugin.removePublicTagOrCategory(cname);

							} else {

								buddy_plugin.addPublicTagOrCategory(cname);
							}
						}
					});

					new MenuItem(share_menu, SWT.SEPARATOR);

					for (final BuddyPluginBuddy buddy : buddies) {

						if (buddy.getNickName() == null) {

							continue;
						}

						final boolean auth = buddy.isLocalRSSTagOrCategoryAuthorised(cname);

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

									buddy.removeLocalAuthorisedRSSTagOrCategory(cname);

								} else {

									buddy.addLocalAuthorisedRSSTagOrCategory(cname);
								}
							}
						});

					}
				}
			}
		}

		// auto-transcode

		if (category.getType() != Category.TYPE_ALL) {

			TrancodeUIUtils.TranscodeTarget[] tts = TrancodeUIUtils.getTranscodeTargets();

			if (tts.length > 0) {

				final Menu t_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
				final MenuItem t_item = new MenuItem(menu, SWT.CASCADE);
				Messages.setLanguageText(t_item, "cat.autoxcode");
				t_item.setMenu(t_menu);

				String existing = category.getStringAttribute(Category.AT_AUTO_TRANSCODE_TARGET);

				for (TrancodeUIUtils.TranscodeTarget tt : tts) {

					TrancodeUIUtils.TranscodeProfile[] profiles = tt.getProfiles();

					if (profiles.length > 0) {

						final Menu tt_menu = new Menu(t_menu.getShell(), SWT.DROP_DOWN);
						final MenuItem tt_item = new MenuItem(t_menu, SWT.CASCADE);
						tt_item.setText(tt.getName());
						tt_item.setMenu(tt_menu);

						for (final TrancodeUIUtils.TranscodeProfile tp : profiles) {

							final MenuItem p_item = new MenuItem(tt_menu, SWT.CHECK);

							p_item.setText(tp.getName());

							boolean	selected = existing != null	&& existing.equals(tp.getUID());

							if ( selected ){

								Utils.setMenuItemImage(tt_item, "blacktick");
							}
							p_item.setSelection(selected );

							p_item.addListener(SWT.Selection, new Listener() {
								@Override
								public void handleEvent(Event event) {
									category.setStringAttribute(
											Category.AT_AUTO_TRANSCODE_TARGET, p_item.getSelection()
													? tp.getUID() : null);
								}
							});
						}
					}
				}
			}
		}

		// rss feed

		final MenuItem rssOption = new MenuItem(menu, SWT.CHECK );

		rssOption.setSelection( category.getBooleanAttribute( Category.AT_RSS_GEN ));

		Messages.setLanguageText(rssOption, "cat.rss.gen");
		rssOption.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				boolean set = rssOption.getSelection();
				category.setBooleanAttribute( Category.AT_RSS_GEN, set );
			}
		});

		// upload priority

		if ( 	cat_type != Category.TYPE_UNCATEGORIZED &&
				cat_type != Category.TYPE_ALL ){

			final MenuItem upPriority = new MenuItem(menu, SWT.CHECK );

			upPriority.setSelection( category.getIntAttribute( Category.AT_UPLOAD_PRIORITY ) > 0 );

			Messages.setLanguageText(upPriority, "cat.upload.priority");
			upPriority.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					boolean set = upPriority.getSelection();
					category.setIntAttribute( Category.AT_UPLOAD_PRIORITY, set?1:0 );
				}
			});
		}

		// options

		MenuItem itemOptions = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemOptions, "cat.options");
		itemOptions.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					MultipleDocumentInterface mdi = uiFunctions.getMDI();
					if (mdi != null) {
						mdi.showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_OPTIONS, dms);
					}
				}
			}
		});

		if (dms.isEmpty()){

			itemOptions.setEnabled(false);
		}
	}

	public static void showCreateCategoryDialog(final UIFunctions.TagReturner tagReturner) {
		SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
				"CategoryAddWindow.title", "CategoryAddWindow.message");
		entryWindow.setParentShell( Utils.findAnyShell());
		entryWindow.prompt(new UIInputReceiverListener() {
			@Override
			public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
				if (entryWindow.hasSubmittedInput()) {

					TagUIUtils.checkTagSharing( false );

					Category newCategory = CategoryManager.createCategory(entryWindow.getSubmittedInput());
					if (tagReturner != null) {
						tagReturner.returnedTags(new Tag[] { newCategory });
					}
				}
			}
		});
	}

}
