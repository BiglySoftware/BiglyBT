/*
 * Created on Oct 7, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.ui.swt.search;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.*;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.StandardButtonsArea;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.PropertiesWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.MetaSearchManagerFactory;
import com.biglybt.core.metasearch.impl.plugin.PluginEngine;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionException;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.util.JSONUtils;

public class
SearchUtils
{
	public static void
	addMenus(
		Menu		menu )
	{
		final Menu template_menu = new Menu( menu.getShell(), SWT.DROP_DOWN );

		MenuItem template_menu_item = new MenuItem( menu, SWT.CASCADE );

		template_menu_item.setMenu( template_menu );

		Messages.setLanguageText( template_menu_item, "Search.menu.engines" );

		template_menu.addMenuListener(
			new MenuAdapter()
			{
				@Override
				public void
				menuShown(
					MenuEvent e)
				{
					for ( MenuItem mi: template_menu.getItems()){

						mi.dispose();
					}

					MenuItem import_mi = new MenuItem( template_menu, SWT.PUSH );

					Messages.setLanguageText( import_mi, "menu.import.json.from.clipboard" );

					import_mi.addSelectionListener(new SelectionAdapter(){
						@Override
						public void widgetSelected(SelectionEvent e) {
							importFromClipboard();
						}});

					new MenuItem( template_menu, SWT.SEPARATOR );

					Engine[] engines = MetaSearchManagerFactory.getSingleton().getMetaSearch().getEngines( true, false );

					Arrays.sort(
						engines,
						new Comparator<Engine>()
						{
							@Override
							public int
							compare(
								Engine o1,
								Engine o2)
							{
								return( o1.getName().compareToIgnoreCase( o2.getName()));
							}
						});

					for (int i=0;i<engines.length;i++){

						final Engine engine = engines[i];

						final Menu engine_menu = new Menu( template_menu.getShell(), SWT.DROP_DOWN );

						MenuItem engine_menu_item = new MenuItem( template_menu, SWT.CASCADE );

						engine_menu_item.setMenu(engine_menu);

						engine_menu_item.setText( engine.getName());

						addMenus( engine_menu, engine, false );
					}
				}
			});

		MenuBuildUtils.addChatMenu( menu, "menu.discuss.searchtemplates", "Search Templates" );

		MenuItem itemExport = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(itemExport, "search.export.all");

		itemExport.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				exportAll();
			}
		});
	}

	public static void
	addMenus(
		Menu				engine_menu,
		final Engine		engine,
		boolean				separator_required )
	{
		if ( separator_required ){

			new MenuItem( engine_menu, SWT.SEPARATOR );

			separator_required = false;
		}

		if ( !( engine instanceof PluginEngine )){

			MenuItem export_json = new MenuItem( engine_menu, SWT.PUSH );

			Messages.setLanguageText( export_json, "menu.export.json.to.clipboard" );

			export_json.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					final Shell shell = Utils.findAnyShell();

					shell.getDisplay().asyncExec(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								try{
									ClipboardCopy.copyToClipBoard( engine.exportToVuzeFile().exportToJSON());

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						});
				}});


			final Subscription subs = engine.getSubscription();

			if ( subs != null ){

				MenuItem export_uri = new MenuItem( engine_menu, SWT.PUSH );

				Messages.setLanguageText( export_uri, "label.copy.uri.to.clip" );

				export_uri.addSelectionListener(new SelectionAdapter(){
					@Override
					public void widgetSelected(SelectionEvent e) {
						final Shell shell = Utils.findAnyShell();

						shell.getDisplay().asyncExec(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									try{
										ClipboardCopy.copyToClipBoard( subs.getURI());

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							});
					}});
			}

			new MenuItem( engine_menu, SWT.SEPARATOR );

			MenuItem remove_item = new MenuItem( engine_menu, SWT.PUSH );

			Messages.setLanguageText( remove_item, "Button.remove" );

			Utils.setMenuItemImage( remove_item, "delete" );

			remove_item.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					engine.setSelectionState( Engine.SEL_STATE_FORCE_DESELECTED );
				}
			});

			separator_required = true;
		}

		if ( separator_required ){

			new MenuItem( engine_menu, SWT.SEPARATOR );

			separator_required = false;
		}

		MenuItem show_props = new MenuItem( engine_menu, SWT.PUSH );

		Messages.setLanguageText( show_props, "Subscription.menu.properties" );

		show_props.addSelectionListener(new SelectionAdapter(){
			@Override
			public void widgetSelected(SelectionEvent e) {
				showProperties(engine);
			}});
	}

	public static void
	addMenus(
		final MenuManager		menuManager )
	{
		final com.biglybt.pif.ui.menus.MenuItem template_menu = menuManager.addMenuItem("sidebar.Search","Search.menu.engines");
		template_menu.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		template_menu.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU );

		template_menu.addFillListener(
			new MenuItemFillListener()
			{
				@Override
				public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {

					template_menu.removeAllChildItems();

					Engine[] engines = MetaSearchManagerFactory.getSingleton().getMetaSearch().getEngines( true, false );

					Arrays.sort(
							engines,
							new Comparator<Engine>()
							{
								@Override
								public int
								compare(
									Engine o1,
									Engine o2)
								{
									return( o1.getName().compareToIgnoreCase( o2.getName()));
								}
							});

					com.biglybt.pif.ui.menus.MenuItem import_menu = menuManager.addMenuItem( template_menu, "menu.import.json.from.clipboard" );

					import_menu.addListener(
						new MenuItemListener()
						{
							@Override
							public void
							selected(
								com.biglybt.pif.ui.menus.MenuItem 	menu,
								Object 											target)
							{
								importFromClipboard();
							}
						});

					com.biglybt.pif.ui.menus.MenuItem sep = menuManager.addMenuItem( template_menu, "!sep!" );

					sep.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );

					for (int i=0;i<engines.length;i++){

						final Engine engine = engines[i];

						com.biglybt.pif.ui.menus.MenuItem engine_menu = menuManager.addMenuItem( template_menu, "!" + engine.getName() + "!" );

						engine_menu.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_MENU );

						if (  !( engine instanceof PluginEngine )){

							com.biglybt.pif.ui.menus.MenuItem mi = menuManager.addMenuItem( engine_menu, "MyTorrentsView.menu.exportmenu" );

							mi.addListener(
								new MenuItemListener()
								{
									@Override
									public void
									selected(
										com.biglybt.pif.ui.menus.MenuItem 	menu,
										Object 											target)
									{
										final Shell shell = Utils.findAnyShell();

										shell.getDisplay().asyncExec(
											new AERunnable()
											{
												@Override
												public void
												runSupport()
												{
													FileDialog dialog =
														new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.SAVE );

													dialog.setFilterPath( TorrentOpener.getFilterPathData() );

													dialog.setText(MessageText.getString("metasearch.export.select.template.file"));

													dialog.setFilterExtensions( VuzeFileHandler.getVuzeFileFilterExtensions());

													dialog.setFilterNames( VuzeFileHandler.getVuzeFileFilterExtensions());

													String path = TorrentOpener.setFilterPathData( dialog.open());

													if ( path != null ){

														if ( !VuzeFileHandler.isAcceptedVuzeFileName( path )){

															path = VuzeFileHandler.getVuzeFileName( path );
														}

														try{
															engine.exportToVuzeFile( new File( path ));

														}catch( Throwable e ){

															Debug.out( e );
														}
													}
												}
											});
									}
								});

							com.biglybt.pif.ui.menus.MenuItem copy_mi = menuManager.addMenuItem( engine_menu, "menu.export.json.to.clipboard" );

							copy_mi.addListener(
								new MenuItemListener()
								{
									@Override
									public void
									selected(
										com.biglybt.pif.ui.menus.MenuItem 	menu,
										Object 											target)
									{
										final Shell shell = Utils.findAnyShell();

										shell.getDisplay().asyncExec(
											new AERunnable()
											{
												@Override
												public void
												runSupport()
												{
													try{
														ClipboardCopy.copyToClipBoard( engine.exportToVuzeFile().exportToJSON());

													}catch( Throwable e ){

														Debug.out( e );
													}
												}
											});
									}
								});

							final Subscription subs = engine.getSubscription();

							if ( subs != null ){

								com.biglybt.pif.ui.menus.MenuItem copy_uri = menuManager.addMenuItem( engine_menu, "label.copy.uri.to.clip" );

								copy_uri.addListener(
									new MenuItemListener()
									{
										@Override
										public void
										selected(
											com.biglybt.pif.ui.menus.MenuItem 	menu,
											Object 											target)
										{
											final Shell shell = Utils.findAnyShell();

											shell.getDisplay().asyncExec(
												new AERunnable()
												{
													@Override
													public void
													runSupport()
													{
														try{
															ClipboardCopy.copyToClipBoard( subs.getURI());

														}catch( Throwable e ){

															Debug.out( e );
														}
													}
												});
										}
									});
							}

							if ( engine instanceof WebEngine ){

								final WebEngine we = (WebEngine)engine;

								if ( we.isNeedsAuth()){

									String cookies = we.getCookies();

									if ( cookies != null && cookies.length() > 0 ){

										mi = menuManager.addMenuItem( engine_menu, "Subscription.menu.resetauth" );

										mi.addListener(
											new MenuItemListener()
											{
												@Override
												public void
												selected(
														com.biglybt.pif.ui.menus.MenuItem menu,
													Object target)
												{
													we.setCookies( null );
												}
											});
									}
								}
							}
						}

						if (  !( engine instanceof PluginEngine )){

							if ( engine_menu.getItems().length > 0 ){

								com.biglybt.pif.ui.menus.MenuItem mi = menuManager.addMenuItem( engine_menu, "Subscription.menu.sep" );

								mi.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );
							}

							com.biglybt.pif.ui.menus.MenuItem mi = menuManager.addMenuItem( engine_menu, "Button.remove" );

							mi.addListener(
								new MenuItemListener()
								{
									@Override
									public void
									selected(
											com.biglybt.pif.ui.menus.MenuItem menu,
										Object target)
									{
										engine.setSelectionState( Engine.SEL_STATE_FORCE_DESELECTED );
									}
								});

							mi = menuManager.addMenuItem( engine_menu, "Subscription.menu.sep2" );

							mi.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );
						}

						if ( engine_menu.getItems().length > 0 ){

							com.biglybt.pif.ui.menus.MenuItem mi = menuManager.addMenuItem( engine_menu, "Subscription.menu.sep2" );

							mi.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_SEPARATOR );
						}

						com.biglybt.pif.ui.menus.MenuItem mi = menuManager.addMenuItem( engine_menu, "Subscription.menu.properties" );

						mi.addListener(
							new MenuItemListener()
							{
								@Override
								public void
								selected(
									com.biglybt.pif.ui.menus.MenuItem menu,
									Object target)
								{
									showProperties( engine );
								}
							});
					}
				}
			});

		com.biglybt.pif.ui.menus.MenuItem chat_menu = menuManager.addMenuItem("sidebar.Search","label.chat");
		chat_menu.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		MenuBuildUtils.addChatMenu(
			menuManager,
			chat_menu,
			new MenuBuildUtils.ChatKeyResolver() {

				@Override
				public String getResourceKey(){
					return( "menu.discuss.searchtemplates" );
				}
				
				@Override
				public String getChatKey(Object object) {
					return( "Search Templates" );
				}
			});

		com.biglybt.pif.ui.menus.MenuItem export_menu = menuManager.addMenuItem("sidebar.Search","search.export.all");
		export_menu.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		export_menu.setStyle( com.biglybt.pif.ui.menus.MenuItem.STYLE_PUSH );

		export_menu.addListener(
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					com.biglybt.pif.ui.menus.MenuItem 	menu,
					Object 											target)
				{
					exportAll();
				}
			});
	}

	public static void showCreateSubscriptionDialog(final long engineID,
			final String searchTerm, final Map optionalFilters) {
		final SkinnedDialog dialog = new SkinnedDialog(
				"skin3_dlg_create_search_subscription", "shell", SWT.DIALOG_TRIM);
		SWTSkin skin = dialog.getSkin();

		final SWTSkinObjectTextbox tb = (SWTSkinObjectTextbox) skin.getSkinObject(
				"sub-name");
		final SWTSkinObjectCheckbox cbShare = (SWTSkinObjectCheckbox) skin.getSkinObject(
				"sub-share");

		final SWTSkinObjectCheckbox cbAutoDL = (SWTSkinObjectCheckbox) skin.getSkinObject(
				"sub-autodl");

		SWTSkinObject soEngineArea = skin.getSkinObject("sub-engine-area");
		final SWTSkinObjectCombo soEngines = (SWTSkinObjectCombo) skin.getSkinObject("sub-engine");

		if (tb == null || cbShare == null || cbAutoDL == null) {
			return;
		}

		boolean hasEngineID = engineID >= 0;
		soEngineArea.setVisible(!hasEngineID);

		final Map<Integer, Engine> mapEngines = new HashMap<>();
		if (!hasEngineID) {
			Engine[] engines = MetaSearchManagerFactory.getSingleton().getMetaSearch().getEngines(true, false);
			List<String> list = new ArrayList<>();
			int pos = 0;

			for (Engine engine : engines) {
				mapEngines.put(pos++, engine);
				list.add(engine.getName());
			}
			soEngines.setList(list.toArray(new String[list.size()]));
		}

		cbShare.setChecked(COConfigurationManager.getBooleanParameter(
				"sub.sharing.default.checked"));
		cbAutoDL.setChecked(COConfigurationManager.getBooleanParameter(
				"sub.autodl.default.checked"));

		SWTSkinObject soButtonArea = skin.getSkinObject("bottom-area");
		if (soButtonArea instanceof SWTSkinObjectContainer) {
			StandardButtonsArea buttonsArea = new StandardButtonsArea() {
				// @see StandardButtonsArea#clicked(int)
				@Override
				protected void clicked(int buttonValue) {
					if (buttonValue == SWT.OK) {

						String name = tb.getText().trim();
						boolean isShared = cbShare.isChecked();
						boolean autoDL = cbAutoDL.isChecked();

						long realEngineID = engineID;
						if (engineID <= 0) {
							int engineIndex = soEngines.getComboControl().getSelectionIndex();
							if (engineIndex < 0) {
								// TODO: Flicker combobox
								return;
							}
							realEngineID = mapEngines.get(engineIndex).getId();
						}

						Map<String, Object> payload = new HashMap<>();
						payload.put("engine_id", realEngineID);
						payload.put("search_term", searchTerm);

						Map<String, Object> mapSchedule = new HashMap<>();
						mapSchedule.put("days", Collections.EMPTY_LIST);
						mapSchedule.put("interval", new Long(-1)); 
						payload.put("schedule", mapSchedule);

						Map<String, Object> mapOptions = new HashMap<>();
						mapOptions.put("auto_dl", autoDL);
						payload.put("options", mapOptions);

						Map<String, Object> mapFilters = new HashMap<>();
						if (optionalFilters != null) {
							mapFilters.putAll(optionalFilters);
						}

						payload.put("filters", mapFilters);

						try {
							Subscription subs;
							subs = SubscriptionManagerFactory.getSingleton().create(name,
									isShared, JSONUtils.encodeToJSON(payload));

							subs.getHistory().setDetails(true, autoDL);

							subs.requestAttention();
						} catch (SubscriptionException e) {
						}

					}

					dialog.close();
				}
			};
			buttonsArea.setButtonIDs(new String[] {
				MessageText.getString("Button.add"),
				MessageText.getString("Button.cancel")
			});
			buttonsArea.setButtonVals(new Integer[] {
				SWT.OK,
				SWT.CANCEL
			});
			buttonsArea.swt_createButtons(
					((SWTSkinObjectContainer) soButtonArea).getComposite());
		}

		dialog.open();
	}

	private static void
	importFromClipboard()
	{
		final Shell shell = Utils.findAnyShell();

		shell.getDisplay().asyncExec(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					try{
						Clipboard clipboard = new Clipboard(Display.getDefault());

						String text = (String)clipboard.getContents(TextTransfer.getInstance());

						clipboard.dispose();

						if ( text != null ){

							InputStream is = new ByteArrayInputStream( text.getBytes( "UTF-8" ));

							try{
								VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

								VuzeFile vf = vfh.loadVuzeFile( is );

								if ( vf != null ){

									vfh.handleFiles( new VuzeFile[]{ vf }, VuzeFileComponent.COMP_TYPE_NONE );
								}
							}finally{

								is.close();
							}
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			});
	}

	private static void
	exportAll()
	{
		final Shell shell = Utils.findAnyShell();

		shell.getDisplay().asyncExec(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					FileDialog dialog =
						new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.SAVE );

					dialog.setFilterPath( TorrentOpener.getFilterPathData() );

					dialog.setText(MessageText.getString("metasearch.export.select.template.file"));

					dialog.setFilterExtensions(VuzeFileHandler.getVuzeFileFilterExtensions());

					dialog.setFilterNames(VuzeFileHandler.getVuzeFileFilterExtensions());

					String path = TorrentOpener.setFilterPathData( dialog.open());

					if ( path != null ){

						if ( !VuzeFileHandler.isAcceptedVuzeFileName( path )){

							path = VuzeFileHandler.getVuzeFileName( path );
						}

						try{
							MetaSearchManagerFactory.getSingleton().getMetaSearch().exportEngines(  new File( path ));

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			});
	}

	private static void
	showProperties(
		Engine		engine )
	{
		String	engine_str;
		String	auth_str	= String.valueOf(false);

		engine_str = engine.getNameEx();

		String url_str = null;

		if ( engine instanceof WebEngine ){

			WebEngine web_engine = (WebEngine)engine;

			if ( web_engine.isNeedsAuth()){

				auth_str = String.valueOf(true) + ": cookies=" + toString( web_engine.getRequiredCookies());
			}

			url_str = web_engine.getSearchUrl();
		}

		String[] keys = {
				"subs.prop.template",
				"subs.prop.auth",
				"subs.prop.query",
				"label.anon",
				"subs.prop.version",
			};

		String[] values = {
				engine_str,
				auth_str,
				url_str,
				String.valueOf( engine.isAnonymous()),
				String.valueOf( engine.getVersion()),
			};

		new PropertiesWindow( engine.getName(), keys, values );
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
}
