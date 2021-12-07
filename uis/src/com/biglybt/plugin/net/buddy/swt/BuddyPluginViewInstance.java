/*
 * Created on Apr 2, 2008
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

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.security.CryptoHandler;
import com.biglybt.core.security.CryptoManager;
import com.biglybt.core.security.CryptoManagerFactory;
import com.biglybt.core.security.CryptoManagerKeyListener;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.*;
import com.biglybt.plugin.I2PHelpers;
import com.biglybt.plugin.net.buddy.*;
import com.biglybt.plugin.net.buddy.tracker.BuddyPluginTracker;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.IntSwtParameter;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;

import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.utils.LocaleUtilities;

public class
BuddyPluginViewInstance
	implements BuddyPluginListener, PartialBuddyListener
{
	private static final int LOG_NORMAL 	= 1;
	private static final int LOG_SUCCESS 	= 2;
	private static final int LOG_ERROR 		= 3;

	private final BuddyPluginView		view;
	private final BuddyPlugin 			plugin;
	private final UIInstance			ui_instance;
	private final LocaleUtilities		lu;
	private final BuddyPluginTracker	tracker;

	private Composite			composite;

	private Table 				public_buddy_table;
	private Table 				anon_buddy_table;

	private Table 				partial_buddy_table;
	private StyledText 			log;

	private CTabFolder  		tab_folder;
	
	private CTabItem 			classic_item;

	private boolean				classic_enabled;
	private boolean				beta_enabled;
	
	
	private Text 	public_nickname;
	private Text 	anon_nickname;

	private List<BuddyPluginBuddy>	public_buddies 	= new ArrayList<>();
	private List<BuddyPluginBuddy>	anon_buddies 	= new ArrayList<>();
	private List<PartialBuddy>		partial_buddies = new ArrayList<>();

	private Button	plugin_install_button;

	private boolean	init_complete;
	private CryptoManagerKeyListener cryptoManagerKeyListener;

	protected
	BuddyPluginViewInstance(
		BuddyPluginView	_view,
		BuddyPlugin		_plugin,
		UIInstance		_ui_instance,
		Composite		_composite )
	{
		view		= _view;
		plugin		= _plugin;
		ui_instance	= _ui_instance;
		composite	= _composite;

		tracker = plugin.getTracker();

		lu = plugin.getPluginInterface().getUtilities().getLocaleUtilities();

		tab_folder = new CTabFolder(composite, SWT.LEFT);

		tab_folder.setBorderVisible(true);
		tab_folder.setTabHeight(20);
		GridData grid_data = new GridData(GridData.FILL_BOTH);
		tab_folder.setLayoutData(grid_data);

		CTabItem beta_item = new CTabItem(tab_folder, SWT.NULL);

		beta_item.setText( lu.getLocalisedMessageText( "azbuddy.dchat.decentralized" ));

		ScrolledComposite beta_area = new ScrolledComposite( tab_folder, SWT.V_SCROLL | SWT.H_SCROLL );
		
		beta_area.setExpandHorizontal(true);
		beta_area.setExpandVertical(true);

		beta_area.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		beta_item.setControl( beta_area );
				
		Composite beta_area_comp = new Composite( beta_area, SWT.NULL );
				
		beta_area.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Utils.updateScrolledComposite(beta_area);
			}
		});
		
		beta_area.setContent( beta_area_comp );
		
		createBeta( beta_area_comp );

		classic_item = new CTabItem(tab_folder, SWT.NULL);

		classic_item.setText( lu.getLocalisedMessageText(  "label.friends" ));

		Composite classic_area = new Composite( tab_folder, SWT.NULL );
		classic_item.setControl( classic_area );

		createClassic( classic_area );

		plugin.addListener(
			new BuddyPluginAdapter()
			{
				@Override
				public void enabledStateChanged(boolean _classic_enabled, boolean _beta_enabled){
					
					if ( composite == null || composite.isDisposed()){
						
						plugin.removeListener( this );
						
					}else{
						
						Utils.execSWTThread(
							new Runnable()
							{
								@Override
								public void
								run()
								{
									if ( _classic_enabled != classic_enabled ){
										
										Utils.disposeComposite( classic_area, false );
										
										createClassic( classic_area );
										
										classic_area.layout( true, true );
									}
									
								if ( _beta_enabled != beta_enabled ){
										
										Utils.disposeComposite( beta_area_comp, false );
										
										createBeta( beta_area_comp );
										
										beta_area_comp.layout( true, true );
									}
								}
							});
					}
				}
			});
		
		int sel = COConfigurationManager.getIntParameter( "buddy.plugin.ui.selected.tab", 0 );
		
		tab_folder.setSelection(sel==0?beta_item:classic_item);
		
		tab_folder.addSelectionListener(
			new SelectionAdapter(){
			
				@Override
				public void widgetSelected(SelectionEvent arg0){
					int	index = tab_folder.getSelectionIndex();
					
					COConfigurationManager.setParameter( "buddy.plugin.ui.selected.tab", index );
				}
			});

	}

	protected void
	selectClassicTab()
	{
		Utils.execSWTThread(
			new Runnable() {

				@Override
				public void run() {
					tab_folder.setSelection( classic_item );
				}
			});
	}

	private void
	createBeta(
		Composite main )
	{
		Utils.disposeComposite( main, false );
		
		final BuddyPluginBeta plugin_beta = plugin.getBeta();

		GridLayout layout = new GridLayout();
		layout.numColumns = 3;

		main.setLayout(layout);
		GridData grid_data = new GridData(GridData.FILL_BOTH );
		main.setLayoutData(grid_data);

		beta_enabled = plugin.isBetaEnabled();
		
		if ( !beta_enabled ){

			Label control_label = new Label( main, SWT.NULL );
			control_label.setText( lu.getLocalisedMessageText( "azbuddy.disabled" ));

			Label label = new Label( main, SWT.NULL );
			grid_data = new GridData(GridData.FILL_HORIZONTAL );
			label.setLayoutData( grid_data); 
			
			final Button config_button = new Button( main, SWT.NULL );
			config_button.setText( lu.getLocalisedMessageText( "plugins.basicview.config" ));

			config_button.addSelectionListener(
					new SelectionAdapter()
					{
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							plugin.showConfig();
						}
					});
			
			return;
		}

		final BuddyPluginBeta beta = plugin.getBeta();

		boolean i2p_enabled = plugin_beta.isI2PAvailable();

			// info

		Composite info_area = new Composite( main, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 4;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		info_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		info_area.setLayoutData(grid_data);

		Label label = new Label( info_area, SWT.WRAP );
		grid_data = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL );
		label.setLayoutData( grid_data);

		label.setText(  lu.getLocalisedMessageText( "azbuddy.dchat.info" ));

		new LinkLabel(info_area, "ConfigView.label.please.visit.here", Wiki.DECENTRALIZED_CHAT);

		label = new Label( info_area, SWT.NULL );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		label.setLayoutData( grid_data); 
		
		final Button config_button = new Button( info_area, SWT.NULL );
		config_button.setText( lu.getLocalisedMessageText( "plugins.basicview.config" ));

		config_button.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent e )
					{
						plugin.showConfig();
					}
				});
		
			// install plugin

		label = new Label( info_area, SWT.NULL );

		label.setText(  MessageText.getString( "azmsgsync.install.text" ));

		plugin_install_button = new Button( info_area, SWT.NULL );

		plugin_install_button.setText( MessageText.getString( "UpdateWindow.columns.install" ));

		plugin_install_button.addSelectionListener(
			new SelectionAdapter() {

				@Override
				public void
				widgetSelected(SelectionEvent e)
				{
					plugin_install_button.setEnabled( false );

					new AEThread2( "installer" )
					{
						@Override
						public void
						run()
						{
							boolean	ok = false;

							String	msg;

							try{
								installMsgSyncPlugin();

								msg = MessageText.getString( "azmsgsync.install.ok.msg" );

								ok = true;

							}catch( Throwable e ){

								msg = MessageText.getString(
											"azmsgsync.install.fail.msg",
											new String[]{ Debug.getNestedExceptionMessage( e )});

							}finally{

								if ( !checkMsgSyncPlugin()){

									if ( ok ){

											// something weird happened

										ok = false;

										msg = MessageText.getString(
												"azmsgsync.install.fail.msg",
												new String[]{ "Unexpected error, check logs" });
									}
								}
							}

							plugin.getPluginInterface().getUIManager().showMessageBox(
								ok?"aztorplugin.browser.install.ok":"aztorplugin.browser.install.fail",
								"!" + msg + "!",
								UIManagerEvent.MT_OK );
						}
					}.start();
				}
			});

		label = new Label( info_area, SWT.NULL );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		label.setLayoutData( grid_data); 
		
		checkMsgSyncPlugin();

			// UI

		final Group ui_area = new Group( main, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 3;
		ui_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		ui_area.setLayoutData(grid_data);

		ui_area.setText( lu.getLocalisedMessageText( "ConfigView.section.style" ));

			// shared public nick

		label = new Label( ui_area, SWT.NULL );
		label.setText( lu.getLocalisedMessageText( "azbuddy.dchat.public.nick" ));

		public_nickname = new Text( ui_area, SWT.BORDER );
		grid_data = new GridData();
		grid_data.widthHint = 200;
		public_nickname.setLayoutData(grid_data);

		public_nickname.setText( plugin_beta.getSharedPublicNickname());
		public_nickname.addListener(SWT.FocusOut, new Listener() {
	        @Override
	        public void handleEvent(Event event) {
	        	plugin_beta.setSharedPublicNickname( public_nickname.getText().trim());
	        }
	    });

		label = new Label( ui_area, SWT.NULL );

			// shared anon nick

		label = new Label( ui_area, SWT.NULL );
		label.setText( lu.getLocalisedMessageText( "azbuddy.dchat.anon.nick" ) );

		anon_nickname = new Text( ui_area, SWT.BORDER );
		grid_data = new GridData();
		grid_data.widthHint = 200;
		anon_nickname.setLayoutData(grid_data);

		anon_nickname.setText( plugin_beta.getSharedAnonNickname());
		anon_nickname.addListener(SWT.FocusOut, new Listener() {
	        @Override
	        public void handleEvent(Event event) {
	        	plugin_beta.setSharedAnonNickname( anon_nickname.getText().trim());
	        }
	    });

		label = new Label( ui_area, SWT.NULL );

			// max lines

		IntSwtParameter max_lines = new IntSwtParameter(ui_area,
				"azbuddy.chat.temp.ui.max.lines", "azbuddy.dchat.ui.max.lines", null,
				128, Integer.MAX_VALUE, null);

		max_lines.setValue( beta.getMaxUILines());

		max_lines.addChangeListener(p -> beta.setMaxUILines( p.getValue()));

		label = new Label( ui_area, SWT.NULL );

			// max chars

		IntSwtParameter max_chars = new IntSwtParameter(ui_area,
				"azbuddy.chat.temp.ui.max.chars", "azbuddy.dchat.ui.max.kb", null, 1,
				512, null);

		max_chars.setValue( beta.getMaxUICharsKB());

		max_chars.addChangeListener(p -> beta.setMaxUICharsKB( p.getValue()));

		label = new Label( ui_area, SWT.NULL );

			// custom date format
		
		Button cd_enable = new Button( ui_area, SWT.CHECK );

		cd_enable.setText( lu.getLocalisedMessageText( "ConfigView.section.style.customDateFormat" ));

		String cdf =  plugin_beta.getCustomDateFormat();
		
		cd_enable.setSelection( !cdf.isEmpty());

		Text cd_format = new Text( ui_area, SWT.BORDER );
		grid_data = new GridData();
		grid_data.widthHint = 200;
		cd_format.setText( cdf );
		cd_format.setLayoutData(grid_data);
	
		cd_enable.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						if ( cd_enable.getSelection()){
							
							cd_format.setEnabled( true );
							
							cd_format.setText(plugin_beta.getCustomDateFormat());
							
						}else{
							
							cd_format.setText( "" );
							
							plugin_beta.setCustomDateFormat( "" );
							
							cd_format.setEnabled( false );
						}
					}
				});
		
		cd_format.setEnabled( !cdf.isEmpty());
		
		cd_format.addListener(SWT.FocusOut, ev->{
			plugin_beta.setCustomDateFormat( cd_format.getText().trim());
	    });
	   
		label = new Label( ui_area, SWT.NULL );

			// checks
		
		final Composite ui_area_checks = new Composite( ui_area, SWT.NULL );
		layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		layout.numColumns = 1;
		ui_area_checks.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		ui_area_checks.setLayoutData(grid_data);

			// generate and look for dl-specific messages
		
		final Button do_dl_chats = new Button( ui_area_checks, SWT.CHECK );

		do_dl_chats.setText( lu.getLocalisedMessageText( "azbuddy.dchat.ui.enable.auto.dl.chat" ));

		do_dl_chats.setSelection( plugin_beta.getEnableAutoDownloadChats());

		do_dl_chats.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setEnableAutoDownloadChats( do_dl_chats.getSelection());
					}
				});	
		
			// hide ratings

		final Button hide_ratings = new Button( ui_area_checks, SWT.CHECK );

		hide_ratings.setText( lu.getLocalisedMessageText( "azbuddy.dchat.ui.hide.ratings" ));

		hide_ratings.setSelection( plugin_beta.getHideRatings());

		hide_ratings.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setHideRatings( hide_ratings.getSelection());
					}
				});

			// hide search/subcriptions

		final Button hide_search_subs = new Button( ui_area_checks, SWT.CHECK );

		hide_search_subs.setText( lu.getLocalisedMessageText( "azbuddy.dchat.ui.hide.search_subs" ));

		hide_search_subs.setSelection( plugin_beta.getHideSearchSubs());

		hide_search_subs.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setHideSearchSubs( hide_search_subs.getSelection());
					}
				});

			// standalone windows

		final Button stand_alone = new Button( ui_area_checks, SWT.CHECK );

		stand_alone.setText( lu.getLocalisedMessageText( "azbuddy.dchat.ui.standalone.windows" ));

		stand_alone.setSelection( plugin_beta.getStandAloneWindows());

		stand_alone.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setStandAloneWindows( stand_alone.getSelection());
					}
				});

			// popout windows -> sidebar

		final Button windows_to_sidebar = new Button( ui_area_checks, SWT.CHECK );

		windows_to_sidebar.setText( lu.getLocalisedMessageText( "azbuddy.dchat.ui.windows.to.sidebar" ));

		windows_to_sidebar.setSelection( plugin_beta.getWindowsToSidebar());

		windows_to_sidebar.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setWindowsToSidebar( windows_to_sidebar.getSelection());
					}
				});

			// use IP Filter
	
		final Button use_ip_filter = new Button( ui_area_checks, SWT.CHECK );
	
		use_ip_filter.setText( lu.getLocalisedMessageText( "azbuddy.dchat.ui.ipfilter.enable" ));
	
		use_ip_filter.setSelection( plugin_beta.getUseIPFilter());
	
		use_ip_filter.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setUseIPFilter( use_ip_filter.getSelection());
					}
				});
	
			// notifications

		final Group noti_area = new Group( main, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 4;
		noti_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		noti_area.setLayoutData(grid_data);

		noti_area.setText( lu.getLocalisedMessageText( "v3.MainWindow.tab.events" ));

		final Button sound_enable = new Button( noti_area, SWT.CHECK );

		sound_enable.setText( lu.getLocalisedMessageText( "azbuddy.dchat.noti.sound" ));

		boolean sound_enabled =  plugin_beta.getSoundEnabled();
		
		sound_enable.setSelection( sound_enabled );

		sound_enable.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setSoundEnabled( sound_enable.getSelection());
					}
				});

		final Text noti_file = new Text( noti_area, SWT.BORDER );
		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.widthHint = 400;
		noti_file.setLayoutData(grid_data);

		String sound_file = plugin_beta.getSoundFile();

		if ( sound_file.length() == 0 ){

			sound_file = "<default>";
		}

		noti_file.setText( sound_file );

		noti_file.addListener(
			SWT.FocusOut,
			new Listener() {

				@Override
				public void handleEvent(Event event){
					String val = noti_file.getText().trim();

					if ( val.length() == 0 || val.startsWith( "<" )){

						noti_file.setText( "<default>" );

						val = "";
					}

					if ( !val.equals( plugin_beta.getSoundFile())){

						plugin_beta.setSoundFile( val );
					}
				}
			});

		final Button noti_browse = new Button(noti_area, SWT.PUSH);

		final ImageLoader imageLoader = ImageLoader.getInstance();

		final Image imgOpenFolder = imageLoader.getImage( "openFolderButton" );

		noti_area.addDisposeListener(
			new DisposeListener() {

				@Override
				public void widgetDisposed(DisposeEvent e) {
					imageLoader.releaseImage( "openFolderButton" );
				}
			});

		noti_browse.setImage( imgOpenFolder );

		imgOpenFolder.setBackground(noti_browse.getBackground());

		Utils.setTT(noti_browse,MessageText.getString("ConfigView.button.browse"));

		noti_browse.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				FileDialog dialog = new FileDialog(noti_area.getShell(),
						SWT.APPLICATION_MODAL);
				dialog.setFilterExtensions(new String[] {
					"*.wav"
				});
				dialog.setFilterNames(new String[] {
					"*.wav"
				});

				dialog.setText(MessageText.getString( "ConfigView.section.interface.wavlocation" ));

				String path = dialog.open();

				if ( path != null ){

					path = path.trim();

					if ( path.startsWith( "<" )){

						path = "";
					}

					plugin_beta.setSoundFile( path.trim());
				}

				view.playSound();
			}
		});

		label = new Label( noti_area, SWT.WRAP );

		label.setText( MessageText.getString("ConfigView.section.interface.wavlocation.info"));

		if ( !sound_enabled ){

			noti_file.setEnabled( false );
			noti_browse.setEnabled( false );
		}
		
		final Button flash_enable = new Button( noti_area, SWT.CHECK );
	
		flash_enable.setText( lu.getLocalisedMessageText( "azbuddy.dchat.noti.flash" ));
	
		flash_enable.setSelection( plugin_beta.getFlashEnabled());

		flash_enable.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setFlashEnabled( flash_enable.getSelection());
					}
				});
	
			// private chats

		Group private_chat_area = new Group( main, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 1;
		//layout.marginHeight = 0;
		//layout.marginWidth = 0;
		private_chat_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		private_chat_area.setLayoutData(grid_data);

		private_chat_area.setText( lu.getLocalisedMessageText( "label.private.chat" ));

		final Button private_chat_enable = new Button( private_chat_area, SWT.CHECK );
		Messages.setLanguageText(private_chat_enable, "azbuddy.dchat.pc.enable");

		private_chat_enable.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setPrivateChatState( private_chat_enable.getSelection()?BuddyPluginBeta.PRIVATE_CHAT_ENABLED:BuddyPluginBeta.PRIVATE_CHAT_DISABLED );
					}
				});

		final Button private_chat_pinned = new Button( private_chat_area, SWT.CHECK );
		Messages.setLanguageText(private_chat_pinned, "azbuddy.dchat.pc.pinned.only");

		private_chat_pinned.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setPrivateChatState( private_chat_pinned.getSelection()?BuddyPluginBeta.PRIVATE_CHAT_PINNED_ONLY:BuddyPluginBeta.PRIVATE_CHAT_ENABLED );
					}
				});

		int pc_state = plugin_beta.getPrivateChatState();

		private_chat_enable.setSelection( pc_state != BuddyPluginBeta.PRIVATE_CHAT_DISABLED );
		private_chat_pinned.setSelection( pc_state == BuddyPluginBeta.PRIVATE_CHAT_PINNED_ONLY );

		private_chat_pinned.setEnabled( pc_state != BuddyPluginBeta.PRIVATE_CHAT_DISABLED );

			// integration

		Group integ_area = new Group( main, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 1;
		integ_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		integ_area.setLayoutData(grid_data);

		integ_area.setText( lu.getLocalisedMessageText( "label.integration" ));

		Label lab;

		Button integ_fk_enable = new Button( integ_area, SWT.CHECK );
		Messages.setLanguageText(integ_fk_enable, "azbuddy.dchat.send.friend.key");

		integ_fk_enable.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setPostFriendKey( integ_fk_enable.getSelection());
					}
				});

		integ_fk_enable.setSelection( plugin_beta.getPostFriendKey());

		lab = new Label( integ_area, SWT.WRAP );

		lab.setText( lu.getLocalisedMessageText( "azbuddy.dchat.send.friend.key.info" ));
		grid_data = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL );
		lab.setLayoutData(grid_data);

			// import

		Group import_area = new Group( main, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 3;
		import_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		import_area.setLayoutData(grid_data);

		import_area.setText( lu.getLocalisedMessageText( "azbuddy.dchat.cannel.import" ));

		label = new Label( import_area, SWT.NULL );

		label.setText( lu.getLocalisedMessageText( "azbuddy.dchat.import.data" ));

		final Text import_data = new Text( import_area, SWT.BORDER );
		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.widthHint = 400;
		import_data.setLayoutData(grid_data);

		final Button import_button = new Button( import_area, SWT.NULL );

		import_button.setText( lu.getLocalisedMessageText( "br.restore" ));

		import_button.addSelectionListener(
				new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent ev )
				{
					import_button.setEnabled( false );

					final Display display = composite.getDisplay();

					final String data		= import_data.getText().trim();

					new AEThread2( "async" )
					{
						@Override
						public void
						run()
						{
							if ( display.isDisposed()){

								return;
							}

							try{
								final BuddyPluginBeta.ChatInstance inst = plugin_beta.importChat( data );

								display.asyncExec(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											if ( !display.isDisposed()){

												BuddyPluginViewBetaChat.createChatWindow( view, plugin, inst );

												import_button.setEnabled( true );
											}
										}
									});

							}catch( Throwable e){

								display.asyncExec(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											if ( !import_button.isDisposed()){

												import_button.setEnabled( true );
											}
										}
									});

								Debug.out( e );
							}
						}
					}.start();
				}
			});

			// Advanced

		Group adv_area = new Group( main, SWT.NULL );
		adv_area.setText( lu.getLocalisedMessageText( "MyTorrentsView.menu.advancedmenu" ));
		layout = new GridLayout();
		layout.numColumns = 1;
		adv_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		adv_area.setLayoutData(grid_data);


			// shared endpoint

		final Button shared_endpoint = new Button( adv_area, SWT.CHECK );
		Messages.setLanguageText(shared_endpoint, "azbuddy.dchat.anon.share.endpoint" );

		shared_endpoint.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						plugin_beta.setSharedAnonEndpoint( shared_endpoint.getSelection());
					}
				});

		shared_endpoint.setSelection( plugin_beta.getSharedAnonEndpoint());

		label = new Label( adv_area, SWT.NULL );
		label.setText( lu.getLocalisedMessageText( "azbuddy.dchat.anon.share.endpoint.info" ));

		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalIndent = 25;
		label.setLayoutData(grid_data);


			// testing

		Group test_area = new Group( main, SWT.NULL );
		test_area.setText( lu.getLocalisedMessageText( "br.test" ));
		layout = new GridLayout();
		layout.numColumns = 4;
		test_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		test_area.setLayoutData(grid_data);


			// public beta channel

		label = new Label( test_area, SWT.NULL );
		label.setText( lu.getLocalisedMessageText( "azbuddy.dchat.public.beta" ));

		Button beta_button = new Button( test_area, SWT.NULL );

		setupButton( beta_button, lu.getLocalisedMessageText( "Button.open" ), AENetworkClassifier.AT_PUBLIC, new String[] { BuddyPluginBeta.BETA_CHAT_KEY }); // dead BuddyPluginBeta.LEGACY_BETA_CHAT_KEY });

		label = new Label( test_area, SWT.NULL );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		label.setLayoutData(grid_data);

			// anonymous beta channel

		label = new Label( test_area, SWT.NULL );

		label.setText(  lu.getLocalisedMessageText( "azbuddy.dchat.anon.beta" ));

		Button beta_i2p_button = new Button( test_area, SWT.NULL );

		setupButton( beta_i2p_button, lu.getLocalisedMessageText( "Button.open" ), AENetworkClassifier.AT_I2P,new String[] { BuddyPluginBeta.BETA_CHAT_KEY }); // dead BuddyPluginBeta.LEGACY_BETA_CHAT_KEY });

		beta_i2p_button.setEnabled( i2p_enabled );

		label = new Label( test_area, SWT.NULL );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		label.setLayoutData(grid_data);

			// create custom channel

		label = new Label( test_area, SWT.NULL );
		label.setText( lu.getLocalisedMessageText( "azbuddy.dchat.create.join.key" ));

		final Text channel_key = new Text( test_area, SWT.BORDER );
		grid_data = new GridData();
		grid_data.widthHint = 200;
		channel_key.setLayoutData(grid_data);

		final Button create_i2p_button = new Button( test_area, SWT.CHECK );

		create_i2p_button.setText( lu.getLocalisedMessageText( "label.anon.i2p" ));

		create_i2p_button.setEnabled( i2p_enabled );

		final Button create_button = new Button( test_area, SWT.NULL );

		create_button.setText( lu.getLocalisedMessageText( "Button.open" ));

		create_button.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						create_button.setEnabled( false );

						final Display display = composite.getDisplay();

						final String network 	= create_i2p_button.getSelection()?AENetworkClassifier.AT_I2P:AENetworkClassifier.AT_PUBLIC;
						final String key		= channel_key.getText().trim();

						new AEThread2( "async" )
						{
							@Override
							public void
							run()
							{
								if ( display.isDisposed()){

									return;
								}

								try{
									final BuddyPluginBeta.ChatInstance inst = plugin_beta.getChat( network, key );

									display.asyncExec(
										new Runnable()
										{
											@Override
											public void
											run()
											{
												if ( !display.isDisposed()){

													BuddyPluginViewBetaChat.createChatWindow( view, plugin, inst );

													create_button.setEnabled( true );
												}
											}
										});

								}catch( Throwable e){

									display.asyncExec(
										new Runnable()
										{
											@Override
											public void
											run()
											{
												if ( !create_button.isDisposed()){

													create_button.setEnabled( true );
												}
											}
										});

									Debug.out( e );
								}
							}
						}.start();
					}
				});


			// end of UI

		List<Button>	buttons = new ArrayList<>();

		buttons.add( create_button );
		buttons.add( beta_button );
		buttons.add( beta_i2p_button );
		buttons.add( import_button );

		Utils.makeButtonsEqualWidth( buttons );

		plugin.addListener(
				new BuddyPluginAdapter()
				{
					@Override
					public void updated()
					{
						if ( public_nickname.isDisposed()){

							plugin.removeListener( this );

						}else{

							public_nickname.getDisplay().asyncExec(
								new Runnable()
								{
									@Override
									public void
									run()
									{
										if ( public_nickname.isDisposed()){

											return;
										}

										String nick = plugin_beta.getSharedPublicNickname();

										if ( !public_nickname.getText().equals( nick )){

											public_nickname.setText( nick );
										}

										nick = plugin_beta.getSharedAnonNickname();

										if ( !anon_nickname.getText().equals( nick )){

											anon_nickname.setText( nick );
										}

										shared_endpoint.setSelection( plugin_beta.getSharedAnonEndpoint());

										int pc_state = plugin_beta.getPrivateChatState();

										private_chat_enable.setSelection( pc_state != BuddyPluginBeta.PRIVATE_CHAT_DISABLED );
										private_chat_pinned.setSelection( pc_state == BuddyPluginBeta.PRIVATE_CHAT_PINNED_ONLY );
										private_chat_pinned.setEnabled( pc_state != BuddyPluginBeta.PRIVATE_CHAT_DISABLED );

										integ_fk_enable.setSelection( plugin_beta.getPostFriendKey());

										String str = plugin_beta.getSoundFile();

										if ( str.length() == 0 ){

											noti_file.setText("<default>");

										}else{


											noti_file.setText( str );
										}

										boolean se = plugin_beta.getSoundEnabled();

										noti_file.setEnabled( se );
										noti_browse.setEnabled( se );
									}
								});
						}
					}
				});
	}

	private boolean
	isMsgSyncPluginInstalled()
	{
		PluginInterface pi = plugin.getPluginInterface().getPluginManager().getPluginInterfaceByID( "azmsgsync" );

		return( pi != null ); // && pi.getPluginState().isOperational());
	}

	private boolean
	checkMsgSyncPlugin()
	{
		if ( plugin_install_button == null ){

			return( false );
		}

		final boolean installed = isMsgSyncPluginInstalled();

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					plugin_install_button.setEnabled( !installed );
				}
			});

		return( installed );
	}

	private void
	installMsgSyncPlugin()

		throws Throwable
	{
		UIFunctions uif = UIFunctionsManager.getUIFunctions();

		if ( uif == null ){

			throw( new Exception( "UIFunctions unavailable - can't install plugin" ));
		}


		final AESemaphore sem = new AESemaphore( "installer_wait" );

		final Throwable[] error = { null };

		uif.installPlugin(
				"azmsgsync",
				"azmsgsync.install",
				new UIFunctions.actionListener()
				{
					@Override
					public void
					actionComplete(
						Object		result )
					{
						try{
							if ( result instanceof Boolean ){

							}else{

								error[0] = (Throwable)result;
							}
						}finally{

							sem.release();
						}
					}
				});

		sem.reserve();

		if ( error[0] instanceof Throwable ){

			throw((Throwable)error[0] );
		}
	}

	private void
	setupButton(
		final Button			button,
		final String			title,
		final String			network,
		final String[]			keys )
	{
		button.setText( title );

		button.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent ev )
				{
					button.setEnabled( false );

					final Display display = composite.getDisplay();

					final int[] done = {0};

					new AEThread2( "async" )
					{
						@Override
						public void
						run()
						{							
							for ( String key: keys ){
								
								try{
									final BuddyPluginBeta.ChatInstance inst = plugin.getBeta().getChat( network, key );
	
									display.asyncExec(
										new Runnable()
										{
											@Override
											public void
											run()
											{
												if ( !display.isDisposed()){
	
													BuddyPluginViewBetaChat.createChatWindow( view, plugin, inst );
													
													done();
												}
											}
										});
	
								}catch( Throwable e){
	
									display.asyncExec(
										new Runnable()
										{
											@Override
											public void
											run()
											{
												if ( !button.isDisposed()){
	
													done();
												}
											}
										});
	
									Debug.out( e );
								}
							}
						}
							
						private void
						done()
						{
							done[0]++;
							
							if ( done[0] == keys.length ) {
								
								button.setEnabled( true );
							}
						}
					}.start();
				}
			});
	}
	
	private void
	createClassic(
		Composite main )
	{
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;

		main.setLayout(layout);
		GridData grid_data = new GridData(GridData.FILL_BOTH );
		main.setLayoutData(grid_data);

			// info

		Composite info_area = new Composite( main, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 4;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		info_area.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 3;
		info_area.setLayoutData(grid_data);

		Label label = new Label( info_area, SWT.NULL );

		label.setText(  lu.getLocalisedMessageText( "azbuddy.classic.info" ));

		new LinkLabel(info_area, "ConfigView.label.please.visit.here", Wiki.FRIENDS);

		label = new Label( info_area, SWT.NULL );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		label.setLayoutData( grid_data );
		
		final Button config_button = new Button( info_area, SWT.NULL );
		config_button.setText( lu.getLocalisedMessageText( "plugins.basicview.config" ));

		config_button.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent e )
					{
						plugin.showConfig();
					}
				});

		classic_enabled = plugin.isClassicEnabled();
		
		if ( !classic_enabled ){

			Label control_label = new Label( main, SWT.NULL );
			control_label.setText( lu.getLocalisedMessageText( "azbuddy.disabled" ));

			return;
		}

		final Composite form = new Composite(main, SWT.NONE);
		FormLayout flayout = new FormLayout();
		flayout.marginHeight = 0;
		flayout.marginWidth = 0;
		form.setLayout(flayout);
		GridData gridData = new GridData(GridData.FILL_BOTH);
		form.setLayoutData(gridData);


		final Composite child1 = new Composite(form,SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		child1.setLayout(layout);


		CTabFolder tab_folder = new CTabFolder(child1, SWT.LEFT);

		tab_folder.setBorderVisible(true);
		tab_folder.setTabHeight(20);
		grid_data = new GridData(GridData.FILL_BOTH);
		tab_folder.setLayoutData(grid_data);

		CTabItem 	public_item = null;
		Composite	public_area	= null;
		CTabItem 	anon_item 	= null;
		Composite	anon_area	= null;

		for ( int i=0;i<2;i++){

			boolean	is_pub_tab = i==0;

			CTabItem 	tab_item = new CTabItem(tab_folder, SWT.NULL);
			Composite 	tab_area = new Composite( tab_folder, SWT.NULL );

			if ( is_pub_tab ){
				public_item = tab_item;
				public_area	= tab_area;
			}else{
				anon_item 	= tab_item;
				anon_area	= tab_area;
			}

			tab_item.setText( lu.getLocalisedMessageText( is_pub_tab?"label.public":"label.anon" ));

			tab_item.setControl( tab_area );

			layout = new GridLayout();
			layout.numColumns = 1;
			layout.horizontalSpacing = 0;
			layout.verticalSpacing = 0;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			tab_area.setLayout(layout);


			final Composite controls = new Composite(tab_area, SWT.NONE);
			layout = new GridLayout();
			layout.numColumns = 5;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			controls.setLayout(layout);

			grid_data = new GridData( GridData.FILL_HORIZONTAL );
			controls.setLayoutData( grid_data );;

			Label control_label = new Label( controls, SWT.NULL );
			control_label.setText( lu.getLocalisedMessageText( "azbuddy.ui.new_buddy" ) + " " );

			final Text control_text = new Text( controls, SWT.BORDER );
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			control_text.setLayoutData(gridData);

			final Button control_button = new Button( controls, SWT.NULL );
			control_button.setText( lu.getLocalisedMessageText( "Button.add" ));

			control_button.setEnabled( false );

			control_text.addModifyListener(
				new ModifyListener() {
		        	@Override
			        public void
		        	modifyText(
		        		ModifyEvent e )
		        	{
						control_button.setEnabled( plugin.verifyPublicKey( is_pub_tab, control_text.getText().trim()));
		        	}
		        });

			control_button.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent e )
					{
						plugin.addBuddy( is_pub_tab, control_text.getText().trim(), BuddyPluginNetwork.SUBSYSTEM_AZ2 );

						control_text.setText( "" );
					}
				});

			final Label control_lab_pk = new Label( controls, SWT.NULL );
			control_lab_pk.setText( lu.getLocalisedMessageText( "azbuddy.ui.mykey" ) + " ");

			final Label control_val_pk = new Label( controls, SWT.NULL );
			gridData = new GridData();
			control_val_pk.setLayoutData(gridData);

			ClipboardCopy.addCopyToClipMenu(
					control_val_pk,
					new ClipboardCopy.copyToClipProvider(){

						@Override
						public String getText(){
							return((String)control_val_pk.getData("key"));
						}
					});

			control_val_pk.setData( "key", "" );

	    	final CryptoManager crypt_man = CryptoManagerFactory.getSingleton();

	    	int ecc_inst = is_pub_tab?1:2;

			byte[]	public_key = crypt_man.getECCHandler( ecc_inst ).peekPublicKey();

			if ( public_key == null ){

			    Messages.setLanguageText(control_val_pk, "ConfigView.section.security.publickey.undef");

			}else{
				String str = Base32.encode( public_key );

				control_val_pk.setData( "key", str );
				control_val_pk.setText( truncate( str ));
			}

		    Messages.setLanguageText(control_val_pk, "ConfigView.copy.to.clipboard.tooltip", true);

		    control_val_pk.setCursor(main.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		    control_val_pk.setForeground(main.getDisplay().getSystemColor(SWT.COLOR_LINK_FOREGROUND));
		    control_val_pk.addMouseListener(new MouseAdapter() {
		    	@Override
			    public void mouseDoubleClick(MouseEvent arg0) {
		    		copyToClipboard();
		    	}
		    	@Override
			    public void mouseDown(MouseEvent arg0) {
		    		copyToClipboard();
		    	}
		    	protected void
		    	copyToClipboard()
		    	{
	    			new Clipboard(control_val_pk.getDisplay()).setContents(new Object[] {control_val_pk.getData( "key" )}, new Transfer[] {TextTransfer.getInstance()});
		    	}
		    });

			cryptoManagerKeyListener = new CryptoManagerKeyListener() {
				@Override
				public void
				keyChanged(
					CryptoHandler handler )
				{
					if ( control_val_pk.isDisposed()){

						crypt_man.removeKeyListener( this );

					}else if ( handler.getType() == CryptoManager.HANDLER_ECC ){

						if ( handler.getInstance() == ecc_inst ){

							control_val_pk.getDisplay().asyncExec(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											byte[]	public_key = handler.peekPublicKey();

											if ( public_key == null ){

												Messages.setLanguageText(control_val_pk, "ConfigView.section.security.publickey.undef");
												control_val_pk.setData( "key", "" );
											}else{

												String str =  Base32.encode( public_key );

												control_val_pk.setText( truncate( str ));
												control_val_pk.setData( "key", str );
											}

											controls.layout();
										}
									});
						}
					}
				}

				@Override
				public void
				keyLockStatusChanged(
						CryptoHandler handler)
				{
				}
			};

			crypt_man.addKeyListener(cryptoManagerKeyListener);

			main.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					if (cryptoManagerKeyListener != null) {
						CryptoManagerFactory.getSingleton().removeKeyListener(cryptoManagerKeyListener);
					}
				}
			});
		}




		final Sash sash = new Sash(form, SWT.HORIZONTAL);

		final Composite child2 = new Composite(form,SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		child2.setLayout(layout);

		FormData formData;

			// child1

		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.top = new FormAttachment(0, 0);
		child1.setLayoutData(formData);

		final FormData child1Data = formData;

		final int SASH_WIDTH = 4;

			// sash

		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.top = new FormAttachment(child1);
		formData.height = SASH_WIDTH;
		sash.setLayoutData(formData);

			// child2

		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.bottom = new FormAttachment(100, 0);
		formData.top = new FormAttachment(sash);
		child2.setLayoutData(formData);

		final PluginConfig pc = plugin.getPluginInterface().getPluginconfig();

		sash.setData( "PCT", new Float( pc.getPluginFloatParameter( "swt.sash.position", 0.7f )));

		sash.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e )
				{
					if (e.detail == SWT.DRAG){
						return;
					}

					child1Data.height = e.y + e.height - SASH_WIDTH;

					form.layout();

					Float l = new Float((double)child1.getBounds().height / form.getBounds().height);

					sash.setData( "PCT", l );

					pc.setPluginParameter( "swt.sash.position", l.floatValue());
				}
			});

		form.addListener(
			SWT.Resize,
			new Listener()
			{
				@Override
				public void
				handleEvent(Event e)
				{
					Float l = (Float) sash.getData( "PCT" );

					if ( l != null ){

						child1Data.height = (int) (form.getBounds().height * l.doubleValue());

						form.layout();
					}
				}
			});

			// table

		Comparator<BuddyPluginBuddy> comparator = addBuddyTable( public_area, true );

		addBuddyTable( anon_area, false );

		Utils.setEnabled( anon_area, I2PHelpers.isI2PInstalled());

		Label pblab = new Label( child1, SWT.NULL );
		
		pblab.setText( lu.getLocalisedMessageText( "label.partial.friends" ));
		
		Comparator<PartialBuddy> pbcomparator = addPartialBuddyTable( child1 );
		
			// log area

		log = new StyledText(child2,SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		grid_data = new GridData(GridData.FILL_BOTH);
		grid_data.horizontalSpan = 1;
		log.setLayoutData(grid_data);
		log.setIndent( 4 );

		public_buddies.clear();
		anon_buddies.clear();

		List<BuddyPluginBuddy> buddies = plugin.getBuddies();

		for ( BuddyPluginBuddy buddy: buddies ){

			if ( buddy.isPublicNetwork()){

				public_buddies.add( buddy );

			}else{

				anon_buddies.add( buddy );
			}

			buddyAdded( buddy );
		}

		Collections.sort( public_buddies, comparator );
		Collections.sort( anon_buddies, comparator );

		partial_buddies = plugin.getPartialBuddies();

		for (int i=0;i<partial_buddies.size();i++){

			partialBuddyAdded( partial_buddies.get(i));
		}

		Collections.sort( partial_buddies, pbcomparator );
		
		plugin.addListener( this );

		plugin.addPartialBuddyListener( this );
		
		tab_folder.setSelection( public_item );

		init_complete	= true;

		updateTable( true );
		updateTable( false );
		
		updatePartialBuddyTable();
	}
	
	private Comparator<BuddyPluginBuddy>
	addBuddyTable(
		Composite	child1,
		boolean		is_public )
	{
		Table buddy_table = new Table(child1, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);

		List<BuddyPluginBuddy>	buddies;

		if ( is_public ){
			public_buddy_table	= buddy_table;
			buddies				= public_buddies;
		}else{
			anon_buddy_table	= buddy_table;
			buddies				= anon_buddies;
		}

		final String[] headers = {
				"azbuddy.ui.table.name",
				"azbuddy.ui.table.online",
				"azbuddy.ui.table.lastseen",
				"azbuddy.ui.table.last_ygm",
				"azbuddy.ui.table.last_msg",
				"azbuddy.ui.table.loc_cat",
				"azbuddy.ui.table.rem_cat",
				"azbuddy.ui.table.read_cat",
				"azbuddy.ui.table.con",
				"azbuddy.ui.table.track",
				"azbuddy.ui.table.msg_in",
				"azbuddy.ui.table.msg_out",
				"azbuddy.ui.table.msg_queued",
				"label.bytesIn",
				"label.bytesOut",
				"azbuddy.ui.table.ss" };

		int[] sizes = {
			250, 100, 100, 100, 200,
			100, 100, 100, 75, 75,
			75, 75, 75, 75, 75,
			40 };

		int[] aligns = {
				SWT.LEFT, SWT.CENTER, SWT.CENTER, SWT.CENTER, SWT.CENTER,
				SWT.LEFT, SWT.LEFT, SWT.LEFT, SWT.LEFT, SWT.CENTER,
				SWT.CENTER, SWT.CENTER, SWT.CENTER, SWT.CENTER, SWT.CENTER,
				SWT.CENTER };

		for (int i = 0; i < headers.length; i++){

			TableColumn tc = new TableColumn(buddy_table, aligns[i]);

			tc.setWidth(sizes[i]);

			Messages.setLanguageText(tc, headers[i]);
		}

	    buddy_table.setHeaderVisible(true);

	    TableColumn[] columns = buddy_table.getColumns();
	    columns[0].setData(new Integer(FilterComparator.FIELD_NAME));
	    columns[1].setData(new Integer(FilterComparator.FIELD_ONLINE));
	    columns[2].setData(new Integer(FilterComparator.FIELD_LAST_SEEN));
	    columns[3].setData(new Integer(FilterComparator.FIELD_YGM));
	    columns[4].setData(new Integer(FilterComparator.FIELD_LAST_MSG));
	    columns[5].setData(new Integer(FilterComparator.FIELD_LOC_CAT));
	    columns[6].setData(new Integer(FilterComparator.FIELD_REM_CAT));
	    columns[7].setData(new Integer(FilterComparator.FIELD_READ_CAT));
	    columns[8].setData(new Integer(FilterComparator.FIELD_CON));
	    columns[9].setData(new Integer(FilterComparator.FIELD_TRACK));
	    columns[10].setData(new Integer(FilterComparator.FIELD_MSG_IN));
	    columns[11].setData(new Integer(FilterComparator.FIELD_MSG_OUT));
	    columns[12].setData(new Integer(FilterComparator.FIELD_QUEUED));
	    columns[13].setData(new Integer(FilterComparator.FIELD_BYTES_IN));
	    columns[14].setData(new Integer(FilterComparator.FIELD_BYTES_OUT));
	    columns[15].setData(new Integer(FilterComparator.FIELD_SS));


	    final FilterComparator comparator = new FilterComparator();

	    Listener sort_listener =
	    	new Listener()
	    	{
		    	@Override
			    public void
		    	handleEvent(
		    		Event e )
		    	{
		    		TableColumn tc = (TableColumn) e.widget;

		    		int field = ((Integer) tc.getData()).intValue();

		    		comparator.setField( field );

		    		Collections.sort( buddies, comparator);

		    		updateTable( is_public );
		    	}
	    	};

	    for (int i=0;i<columns.length;i++){

	    	columns[i].addListener(SWT.Selection,sort_listener);
	    }

	    GridData gridData = new GridData(GridData.FILL_BOTH);
	    gridData.heightHint = buddy_table.getHeaderHeight() * 3;
		buddy_table.setLayoutData(gridData);


		buddy_table.addListener(
			SWT.SetData,
			new Listener()
			{
				@Override
				public void
				handleEvent(
					Event event)
				{
					TableItem item = (TableItem)event.item;

					int index = buddy_table.indexOf(item);

					if ( index < 0 || index >= buddies.size()){

						return;
					}

					BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(index);

					item.setText(0, buddy.getName());

					int	os;

					if ( buddy.isOnline( false )){

						os = buddy.getOnlineStatus();

					}else{

						os = BuddyPlugin.STATUS_APPEAR_OFFLINE;
					}

					if ( os == BuddyPlugin.STATUS_APPEAR_OFFLINE ){

						item.setText( 1, "" );

					}else{

						item.setText(1, plugin.getOnlineStatus( os ));
					}

					long lo = buddy.getLastTimeOnline();

					item.setText(2, lo==0?"":new SimpleDateFormat().format(new Date( lo )));

					long	last_ygm = buddy.getLastMessagePending();

					item.setText(3, last_ygm==0?"":new SimpleDateFormat().format(new Date( last_ygm )));

					String	lm = buddy.getLastMessageReceived();

					item.setText(4, lm==null?"":lm);

					String loc_cat = buddy.getLocalAuthorisedRSSTagsOrCategoriesAsString();
					if ( loc_cat == null ){
						loc_cat = "";
					}
					item.setText(5, "" + loc_cat);

					String rem_cat = buddy.getRemoteAuthorisedRSSTagsOrCategoriesAsString();
					if ( rem_cat == null ){
						rem_cat = "";
					}
					item.setText(6, "" + rem_cat);

					String read_cat = buddy.getLocalReadTagsOrCategoriesAsString();
					if ( read_cat == null ){
						read_cat = "";
					}
					item.setText(7, "" + read_cat);

					item.setText(8, "" + buddy.getConnectionsString());

					item.setText(9, "" + tracker.getTrackingStatus( buddy ));

					String in_frag = buddy.getMessageInFragmentDetails();

					item.setText(10, "" + buddy.getMessageInCount() + (in_frag.length()==0?"":("+" + in_frag )));
					item.setText(11, "" + buddy.getMessageOutCount());
					item.setText(12, "" + buddy.getMessageHandler().getMessageCount());
					item.setText(13, "" + DisplayFormatters.formatByteCountToKiBEtc(buddy.getBytesInCount()));
					item.setText(14, "" + DisplayFormatters.formatByteCountToKiBEtc(buddy.getBytesOutCount()));

					item.setText(15, "" + buddy.getSubsystem() + " v" + buddy.getVersion());

					item.setData( buddy );
				}
			});

		final Listener tt_label_listener =
			new Listener()
			{
				@Override
				public void handleEvent(Event event) {
					Label label = (Label) event.widget;
					Shell shell = label.getShell();
					switch (event.type) {
					case SWT.MouseDown:
						Event e = new Event();
						e.item = (TableItem) label.getData("_TABLEITEM");
						buddy_table.setSelection(new TableItem[] { (TableItem) e.item });
						buddy_table.notifyListeners(SWT.Selection, e);
						// fall through
					case SWT.MouseExit:
						shell.dispose();
						break;
					}
				}
			};


		Listener	tt_table_listener =
			new Listener()
			{
				private Shell tip = null;

				private Label label = null;

				@Override
				public void
				handleEvent(
					Event event )
				{
					switch (event.type){
						case SWT.Dispose:
						case SWT.KeyDown:
						case SWT.MouseMove: {
							if (tip == null)
								break;
							tip.dispose();
							tip = null;
							label = null;
							break;
						}
						case SWT.MouseHover:
						{
							Point mouse_position = new Point(event.x, event.y);

							TableItem item = buddy_table.getItem( mouse_position );

							if (item != null) {

								if (tip != null && !tip.isDisposed()){

									tip.dispose();

									tip = null;
								}

								int index = buddy_table.indexOf(item);

								if ( index < 0 || index >= buddies.size()){

									return;
								}

								BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(index);

								int	item_index = 0;

								for (int i=0;i<headers.length;i++){

									Rectangle bounds = item.getBounds(i);

									if ( bounds.contains( mouse_position )){

										item_index = i;

										break;
									}
								}

								if( item_index != 0 ){

									return;
								}

								tip = new Shell(buddy_table.getShell(), SWT.ON_TOP | SWT.TOOL);
								tip.setLayout(new FillLayout());
								label = new Label(tip, SWT.NONE);
								label.setForeground(buddy_table.getDisplay()
										.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
								label.setBackground(buddy_table.getDisplay()
										.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
								label.setData("_TABLEITEM", item);

								label.setText( getToolTip( buddy ));

								label.addListener(SWT.MouseExit, tt_label_listener);
								label.addListener(SWT.MouseDown, tt_label_listener);
								Point size = tip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
								Rectangle rect = item.getBounds(item_index);
								Point pt = buddy_table.toDisplay(rect.x, rect.y);
								tip.setBounds(pt.x, pt.y, size.x, size.y);
								tip.setVisible(true);
							}
						}
					}
				}

				protected String
				getToolTip(
					BuddyPluginBuddy	buddy )
				{
					List<InetSocketAddress> addresses = buddy.getAdjustedIPs();

					InetSocketAddress	ip	= buddy.getIP();

					InetSocketAddress 	adj = buddy.getAdjustedIP();

					InetSocketAddress latest_v4 = buddy.getLatestIP( true );
					InetSocketAddress latest_v6 = buddy.getLatestIP( false );
					
					Set<InetSocketAddress>	all_ips = new HashSet<>();
					
					all_ips.addAll( addresses );
					all_ips.add( ip );
					all_ips.add( adj );
					all_ips.add( latest_v4 );
					all_ips.add( latest_v6 );
					
					all_ips.remove( null );
					
					String	str = "";

					if ( all_ips.isEmpty()){

						str = "<none>";

					}else{

						Set<String>	done = new HashSet<>();
						
						for ( InetSocketAddress a: all_ips ){

							String host = AddressUtils.getHostAddress( a );
							
							if ( !done.contains( host )){
							
								done.add( host );
							
								str += (str.isEmpty()?"":", ") + host;
							}
						}
					}

					return(  "ip=" + str + ",tcp=" + buddy.getTCPPort() + ",udp=" + buddy.getUDPPort());
				}
			};

		buddy_table.addListener(SWT.Dispose, tt_table_listener);
		buddy_table.addListener(SWT.KeyDown, tt_table_listener);
		buddy_table.addListener(SWT.MouseMove, tt_table_listener);
		buddy_table.addListener(SWT.MouseHover, tt_table_listener);

		buddy_table.addKeyListener(
			new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent event) {
					if (event.stateMask == SWT.MOD1) {
						int key = event.character;
						if (key <= 26 && key > 0) {
							key += 'a' - 1;
						}
						if ( key == 'a' ){
							buddy_table.selectAll();
							event.doit = false;
						}
					}else if ( event.stateMask == 0 && event.keyCode == SWT.DEL ){

						TableItem[] selection = buddy_table.getSelection();

						String str = "";
						
						for (int i=0;i<selection.length;i++){
							
							BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();
							
							str += (str.isEmpty()?"":", ") + buddy.getName();
						}
						
						MessageBoxShell mb =
								new MessageBoxShell(
									MessageText.getString("message.confirm.delete.title"),
									MessageText.getString("message.confirm.delete.text",
											new String[] { str	}),
									new String[] {
										MessageText.getString("Button.yes"),
										MessageText.getString("Button.no")
									},
									1 );

							mb.open(new UserPrompterResultListener() {
								@Override
								public void prompterClosed(int result) {
									if (result == 0) {
										for (int i=0;i<selection.length;i++){
				
											BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();
				
											buddy.remove();
										}
										
										buddy_table.setSelection( new int[0] );
									}
								}});
						
						event.doit = false;
					}
				}
			});

		final Menu menu = new Menu(buddy_table);

			// send message

		final  MenuItem send_msg_item = new MenuItem(menu, SWT.PUSH);

		send_msg_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.send" ) );

		send_msg_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					final TableItem[] selection = buddy_table.getSelection();

					UIInputReceiver prompter = ui_instance.getInputReceiver();

					prompter.setLocalisedTitle( lu.getLocalisedMessageText( "azbuddy.ui.menu.send" ));
					prompter.setLocalisedMessage( lu.getLocalisedMessageText( "azbuddy.ui.menu.send_msg" ) );

					try{
						prompter.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver prompter) {
								String text = prompter.getSubmittedInput();

								if ( text != null ){

									for (int i=0;i<selection.length;i++){

										BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();

										buddy.getPluginNetwork().getAZ2Handler().sendAZ2Message( buddy, text );
									}
								}
							}
						});

					}catch( Throwable e ){

					}
				}
			});

			// chat

		final  MenuItem chat_item = new MenuItem(menu, SWT.PUSH);

		chat_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.chat" ) );

		chat_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					TableItem[] selection = buddy_table.getSelection();

					BuddyPluginBuddy[] buddies = new BuddyPluginBuddy[selection.length];

					for (int i=0;i<selection.length;i++){

						BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();

						buddies[i] = buddy;
					}

					plugin.createChat( buddies );
				}
			});

			// ping

		final MenuItem ping_item = new MenuItem(menu, SWT.PUSH);

		ping_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.ping" ) );

		ping_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					TableItem[] selection = buddy_table.getSelection();

					for (int i=0;i<selection.length;i++){

						BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();

						try{
							buddy.ping();

						}catch( Throwable e ){

							print( "Ping failed", e );
						}
					}
				}
			});

			// cats
	
		Menu cat_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
		MenuItem cat_item = new MenuItem(menu, SWT.CASCADE);
		Messages.setLanguageText(cat_item, "azbuddy.ui.menu.cat" );
		cat_item.setMenu(cat_menu);
	
			// cats - share
	
		final MenuItem cat_share_item = new MenuItem(cat_menu, SWT.PUSH);
	
		cat_share_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.cat.share" ) );
	
		cat_share_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					TableItem[] selection = buddy_table.getSelection();
	
					List<BuddyPluginBuddy>	buddies = new ArrayList<>();
	
					Set<String> enabled_tags = new HashSet<>();
	
					for (int i=0;i<selection.length;i++){
	
						BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();
	
						buddies.add( buddy );
	
						Set<String> et = buddy.getLocalAuthorisedRSSTagsOrCategories();
	
						if ( et == null ){
	
							enabled_tags.clear();
	
							break;
	
						}else{
	
							if ( i == 0 ){
	
								enabled_tags.addAll( et );
	
							}else{
	
								enabled_tags.retainAll( et );
	
								if ( enabled_tags.isEmpty()){
	
									break;
								}
							}
						}
					}
	
					TagManager tm = TagManagerFactory.getTagManager();
	
					List<Tag> all_tags = tm.getTagType( TagType.TT_DOWNLOAD_CATEGORY ).getTags();
	
					all_tags.addAll( tm.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTags());
	
					Map<String,Tag>	tag_map = new HashMap<>();
	
					for ( Tag t: all_tags ){
	
						tag_map.put( t.getTagName( true ), t );
					}
	
					List<Tag> selected_tags = new ArrayList<>();
	
					for ( String s: enabled_tags ){
	
						Tag t = tag_map.get( s );
	
						if ( t != null ){
	
							selected_tags.add( t );
						}
					}
	
					TagUIUtilsV3.showTagSelectionDialog(
						all_tags,
						selected_tags,
						false,
						(tags)->{
	
							Set<String>	tag_names = new HashSet<>();
	
							for ( Tag t: tags ){
	
								tag_names.add( t.getTagName( true ));
							}
	
							for ( BuddyPluginBuddy buddy: buddies ){
	
								buddy.setLocalAuthorisedRSSTagsOrCategories( tag_names );
							}
						});
				}
			});
	
			// cats - subscribe
	
		final Menu cat_subs_menu = new Menu(cat_menu.getShell(), SWT.DROP_DOWN);
		final MenuItem cat_subs_item = new MenuItem(cat_menu, SWT.CASCADE);
		Messages.setLanguageText(cat_subs_item, "azbuddy.ui.menu.cat_subs" );
		cat_subs_item.setMenu(cat_subs_menu);
	
		cat_subs_menu.addMenuListener(
			new MenuListener()
			{
				@Override
				public void
				menuShown(
					MenuEvent arg0 )
				{
					MenuItem[] items = cat_subs_menu.getItems();
	
					for (int i = 0; i < items.length; i++){
	
						items[i].dispose();
					}
	
					final TableItem[] selection = buddy_table.getSelection();
	
					List<BuddyPluginBuddy>	buddies = new ArrayList<>();
	
					Set<String> avail_cats = new TreeSet<>();
	
					for (int i=0;i<selection.length;i++){
	
						BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();
	
						buddies.add( buddy );
	
						Set<String> cats = buddy.getRemoteAuthorisedRSSTagsOrCategories();
	
						if ( cats != null ){
	
							avail_cats.addAll( cats );
						}
					}
	
					for ( final String cat: avail_cats ){
	
						final MenuItem subs_item = new MenuItem( cat_subs_menu, SWT.CHECK );
	
						subs_item.setText( cat );
	
						boolean	all_subs 	= true;
						boolean some_subs 	= false;
	
						for ( BuddyPluginBuddy buddy: buddies ){
	
							if ( buddy.isSubscribedToCategory( cat )){
	
								some_subs = true;
	
							}else{
	
								all_subs = false;
							}
						}
	
						subs_item.setSelection( all_subs );
	
						subs_item.addSelectionListener(
							new SelectionAdapter()
							{
								@Override
								public void
								widgetSelected(
									SelectionEvent event )
								{
									for (int i=0;i<selection.length;i++){
	
										BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();
	
										if ( buddy.isRemoteRSSTagOrCategoryAuthorised( cat )){
	
											try{
												buddy.subscribeToCategory( cat );
	
											}catch( Throwable e ){
	
												print( "Failed", e );
											}
										}
									}
								}
							});
						}
				}
	
				@Override
				public void
				menuHidden(
					MenuEvent arg0 )
				{
				}
			});
		
			// set local name
		
		MenuItem set_name_item = new MenuItem(menu, SWT.PUSH);

		set_name_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.setname" ) );

		set_name_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					final TableItem[] selection = buddy_table.getSelection();

					UIInputReceiver prompter = ui_instance.getInputReceiver();

					prompter.setLocalisedTitle( lu.getLocalisedMessageText( "azbuddy.ui.menu.setname" ));
					prompter.setLocalisedMessage( lu.getLocalisedMessageText( "azbuddy.ui.menu.setname_msg" ) );

					try{
						prompter.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver prompter) {
								String text = prompter.getSubmittedInput();

								if ( text != null ){

									text = text.trim();
									
									for (int i=0;i<selection.length;i++){

										BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();

										buddy.setMyName( text );
									}
								}
							}
						});

					}catch( Throwable e ){

					}
				}
			});

		MenuItem ygm_item;
		
		if ( Constants.isCVSVersion()){
			
				// ygm
	
			ygm_item = new MenuItem(menu, SWT.PUSH);
	
			ygm_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.ygm" ) );
	
			ygm_item.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent event )
					{
						TableItem[] selection = buddy_table.getSelection();
	
						for (int i=0;i<selection.length;i++){
	
							BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();
	
							try{
								buddy.setMessagePending();
	
							}catch( Throwable e ){
	
								print( "YGM failed", e );
							}
						}
					}
				});
		}else{
			
			ygm_item = null;
		}
		
		new MenuItem( menu, SWT.SEPARATOR );

			// get public key
	
		final MenuItem get_pk_item = new MenuItem(menu, SWT.PUSH);
	
		get_pk_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.copypk" ) );
	
		get_pk_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					TableItem[] selection = buddy_table.getSelection();
	
					StringBuilder sb = new StringBuilder();
	
					for (int i=0;i<selection.length;i++){
	
						BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();
	
						sb.append(buddy.getPublicKey()).append("\r\n");
					}
	
					if ( sb.length() > 0 ){
	
						writeToClipboard( sb.toString());
					}
				}
			});

			// encrypt

		final MenuItem encrypt_item = new MenuItem(menu, SWT.PUSH);

		encrypt_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.enc" ) );

		encrypt_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					TableItem[] selection = buddy_table.getSelection();

					String	str = readFromClipboard();

					if( str != null ){

						StringBuilder sb = new StringBuilder();

						for (int i=0;i<selection.length;i++){

							BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();

							try{
								byte[]	contents = str.getBytes( "UTF-8" );

								BuddyPlugin.CryptoResult result = buddy.encrypt( contents );

								sb.append( "key: " );
								sb.append( plugin.getPublicKey( is_public ));
								sb.append( "\r\n" );

								sb.append( "hash: " );
								sb.append( Base32.encode( result.getChallenge()));
								sb.append( "\r\n" );

								sb.append( "payload: " );
								sb.append( Base32.encode( result.getPayload()));
								sb.append( "\r\n\r\n" );

							}catch( Throwable e ){

								print( "YGM failed", e );
							}
						}

						writeToClipboard( sb.toString());
					}
				}
			});

			// decrypt

		final MenuItem decrypt_item = new MenuItem(menu, SWT.PUSH);

		decrypt_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.dec" ) );

		decrypt_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					String	str = readFromClipboard();

					if ( str != null ){

						String[] 	bits = str.split( "\n" );

						StringBuilder sb = new StringBuilder();

						BuddyPluginBuddy	buddy 	= null;
						byte[]				hash	= null;

						for (int i=0;i<bits.length;i++){

							String	bit = bits[i].trim();

							if ( bit.length() > 0 ){

								int	pos = bit.indexOf( ':' );

								if ( pos == -1 ){

									continue;
								}

								String	lhs = bit.substring( 0, pos ).trim();
								String	rhs	= bit.substring( pos+1 ).trim();

								if ( lhs.equals( "key" )){

									buddy = plugin.getBuddyFromPublicKey( rhs );

								}else if ( lhs.equals( "hash" )){

									hash	= Base32.decode( rhs );

								}else if ( lhs.equals( "payload" )){

									byte[]	payload = Base32.decode( rhs );

									if ( buddy != null ){

										try{
											BuddyPlugin.CryptoResult result = buddy.decrypt( payload );

											byte[] sha1 = new SHA1Simple().calculateHash( result.getChallenge());

											sb.append( "key: " );
											sb.append( buddy.getPublicKey());
											sb.append( "\r\n" );

											sb.append("hash_ok: ").append(Arrays.equals(hash, sha1));
											sb.append( "\r\n" );

											sb.append( "payload: " );
											sb.append( new String( result.getPayload(), "UTF-8" ));
											sb.append( "\r\n\r\n" );

										}catch( Throwable e ){

											print( "decrypt failed", e );
										}
									}
								}
							}
						}

						if ( sb.length() > 0 ){

							writeToClipboard( sb.toString());
						}
					}
				}
			});

			// sign

		final MenuItem sign_item = new MenuItem(menu, SWT.PUSH);

		sign_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.sign" ) );

		sign_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					String	str = readFromClipboard();

					if ( str != null ){

						StringBuilder sb = new StringBuilder();

						try{
							sb.append( "key: " );
							sb.append( plugin.getPublicKey( is_public ));
							sb.append( "\r\n" );

							byte[] payload = str.getBytes( "UTF-8" );

							sb.append( "data: " );
							sb.append( Base32.encode( payload ));
							sb.append( "\r\n" );

							byte[]	sig = plugin.sign( is_public, payload );

							sb.append( "sig: " );
							sb.append( Base32.encode( sig ));
							sb.append( "\r\n" );

						}catch( Throwable e ){

							print( "sign failed", e );
						}

						if ( sb.length() > 0 ){

							writeToClipboard( sb.toString());
						}
					}
				}
			});

			// verify

		final MenuItem verify_item = new MenuItem(menu, SWT.PUSH);

		verify_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.verify" ) );

		verify_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent event )
				{
					String	str = readFromClipboard();

					if ( str != null ){

						String[] 	bits = str.split( "\n" );

						StringBuilder sb = new StringBuilder();

						String				pk 		= null;
						byte[]				data	= null;

						for (int i=0;i<bits.length;i++){

							String	bit = bits[i].trim();

							if ( bit.length() > 0 ){

								int	pos = bit.indexOf( ':' );

								if ( pos == -1 ){

									continue;
								}

								String	lhs = bit.substring( 0, pos ).trim();
								String	rhs	= bit.substring( pos+1 ).trim();

								if ( lhs.equals( "key" )){

									pk = rhs;

								}else if ( lhs.equals( "data" )){

									data	= Base32.decode( rhs );

								}else if ( lhs.equals( "sig" )){

									byte[]	sig = Base32.decode( rhs );

									if ( pk != null && data != null ){

										try{

											sb.append( "key: " );
											sb.append( pk );
											sb.append( "\r\n" );

											boolean ok = plugin.verify( is_public, pk, data, sig );

											sb.append("sig_ok: ").append(ok);
											sb.append( "\r\n" );

											sb.append( "data: " );
											sb.append( new String( data, "UTF-8" ));
											sb.append( "\r\n\r\n" );

										}catch( Throwable e ){

											print( "decrypt failed", e );
										}
									}
								}
							}
						}

						if ( sb.length() > 0 ){

							writeToClipboard( sb.toString());
						}
					}
				}
			});

		new MenuItem( menu, SWT.SEPARATOR );

			// disconnect message
	
		final  MenuItem disconnect_msg_item;
		
		if ( Constants.isCVSVersion()){
			disconnect_msg_item = new MenuItem(menu, SWT.PUSH);
	
			disconnect_msg_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.disconnect" ) );
	
			disconnect_msg_item.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent event )
					{
						TableItem[] selection = buddy_table.getSelection();
	
						for (int i=0;i<selection.length;i++){
	
							BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();
	
							buddy.disconnect();
						}
					}
				});
		}else{
			
			disconnect_msg_item = null;
		}
		
		final MenuItem remove_item = new MenuItem(menu, SWT.PUSH);

		remove_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.remove" ));
		
		Utils.setMenuItemImage( remove_item, "delete" );
		
		remove_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e)
				{
					TableItem[] selection = buddy_table.getSelection();

					for (int i=0;i<selection.length;i++){

						BuddyPluginBuddy buddy = (BuddyPluginBuddy)selection[i].getData();

						buddy.remove();
					}
				}
			});


			// done with menus

		buddy_table.setMenu( menu );

		menu.addMenuListener(
			new MenuListener()
			{
				@Override
				public void
				menuShown(
					MenuEvent arg0 )
				{
					boolean	available = plugin.isAvailable();

					TableItem[] selection = buddy_table.getSelection();

					remove_item.setEnabled( selection.length > 0 );
					get_pk_item.setEnabled( available && selection.length > 0 );
					if (disconnect_msg_item != null ){
						disconnect_msg_item.setEnabled( selection.length > 0 );
					}
					send_msg_item.setEnabled(available && selection.length > 0);
					chat_item.setEnabled(available && selection.length > 0);
					ping_item.setEnabled(available && selection.length > 0);
					if (ygm_item!=null ){
						ygm_item.setEnabled(available && selection.length > 0);
					}
					encrypt_item.setEnabled(selection.length > 0);
					decrypt_item.setEnabled(true);
					sign_item.setEnabled(true);
					verify_item.setEnabled(true);
					cat_item.setEnabled(selection.length > 0);
					set_name_item.setEnabled( selection.length == 1);
				}

				@Override
				public void
				menuHidden(
					MenuEvent arg0 )
				{
				}
			});
		
		return( comparator );
	}

	private Comparator<PartialBuddy>
	addPartialBuddyTable(
		Composite	child1 )
	{
		partial_buddy_table = new Table(child1, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);

		final String[] headers = {
				"azbuddy.ui.pbtable.peer",
				"azbuddy.ui.pbtable.downloads" };

		int[] sizes = {
			250, 400  };

		int[] aligns = {
				SWT.LEFT, SWT.LEFT };

		for (int i = 0; i < headers.length; i++){

			TableColumn tc = new TableColumn(partial_buddy_table, aligns[i]);

			tc.setWidth(sizes[i]);

			Messages.setLanguageText(tc, headers[i]);
		}

	    partial_buddy_table.setHeaderVisible(true);

	    TableColumn[] columns = partial_buddy_table.getColumns();
	    columns[0].setData(new Integer(PBFilterComparator.FIELD_PEER));
	    columns[1].setData(new Integer(PBFilterComparator.FIELD_DOWNLOADS));

	    final PBFilterComparator comparator = new PBFilterComparator();

	    Listener sort_listener =
	    	new Listener()
	    	{
		    	@Override
			    public void
		    	handleEvent(
		    		Event e )
		    	{
		    		TableColumn tc = (TableColumn) e.widget;

		    		int field = ((Integer) tc.getData()).intValue();

		    		comparator.setField( field );

		    		Collections.sort( partial_buddies,comparator);

		    		updatePartialBuddyTable();
		    	}
	    	};

	    for (int i=0;i<columns.length;i++){

	    	columns[i].addListener(SWT.Selection,sort_listener);
	    }

		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.heightHint = partial_buddy_table.getHeaderHeight() * 3;
		partial_buddy_table.setLayoutData(gridData);

		partial_buddy_table.addListener(
			SWT.SetData,
			new Listener()
			{
				@Override
				public void
				handleEvent(
					Event event)
				{
					TableItem item = (TableItem)event.item;

					int index = partial_buddy_table.indexOf(item);

					if ( index < 0 || index >= partial_buddies.size()){

						return;
					}

					PartialBuddy	buddy = partial_buddies.get(index);

					item.setText(0, buddy.getName());

					item.setText(1, buddy.getDownloadsSummary());

					item.setData( buddy );
				}
			});




		final Menu menu = new Menu(partial_buddy_table);

		final MenuItem remove_item = new MenuItem(menu, SWT.PUSH);

		Utils.setMenuItemImage( remove_item, "delete" );

		remove_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.remove" ));

		remove_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e)
				{
					TableItem[] selection = partial_buddy_table.getSelection();

					for (int i=0;i<selection.length;i++){

						PartialBuddy buddy = (PartialBuddy)selection[i].getData();

						if ( buddy != null ){
							
							buddy.remove();
						}
					}
				}
			});
		
		partial_buddy_table.setMenu( menu );

		partial_buddy_table.addKeyListener(
				new KeyAdapter()
				{
					@Override
					public void keyPressed(KeyEvent event) {
						if (event.stateMask == SWT.MOD1) {
							int key = event.character;
							if (key <= 26 && key > 0) {
								key += 'a' - 1;
							}
							if ( key == 'a' ){
								partial_buddy_table.selectAll();
								event.doit = false;
							}
						}else if ( event.stateMask == 0 && event.keyCode == SWT.DEL ){

							TableItem[] selection = partial_buddy_table.getSelection();

							String str = "";
							
							for (int i=0;i<selection.length;i++){
								
								PartialBuddy buddy = (PartialBuddy)selection[i].getData();
								
								str += (str.isEmpty()?"":", ") + buddy.getName();
							}
							
							MessageBoxShell mb =
									new MessageBoxShell(
										MessageText.getString("message.confirm.delete.title"),
										MessageText.getString("message.confirm.delete.text",
												new String[] { str	}),
										new String[] {
											MessageText.getString("Button.yes"),
											MessageText.getString("Button.no")
										},
										1 );

								mb.open(new UserPrompterResultListener() {
									@Override
									public void prompterClosed(int result) {
										if (result == 0) {
											for (int i=0;i<selection.length;i++){
					
												PartialBuddy buddy = (PartialBuddy)selection[i].getData();
					
												buddy.remove();
											}
											
											partial_buddy_table.setSelection( new int[0] );
										}
									}});
							
							event.doit = false;
						}
					}
				});
		
		menu.addMenuListener(
			new MenuListener()
			{
				@Override
				public void
				menuShown(
					MenuEvent arg0 )
				{
					TableItem[] selection = partial_buddy_table.getSelection();

					remove_item.setEnabled( selection.length > 0 );
				}

				@Override
				public void
				menuHidden(
					MenuEvent arg0 )
				{
				}
			});
		
		return( comparator );
	}
	
	protected String
	readFromClipboard()
	{
		 Object o =
			 new Clipboard(Utils.getDisplay()).getContents(TextTransfer.getInstance());

		 if ( o instanceof String ){

			 return((String)o);
		 }

		 return( null );
	}

	protected void
	writeToClipboard(
		String	str )
	{
		 new Clipboard(Utils.getDisplay()).setContents(
			      new Object[] {str },
			      new Transfer[] {TextTransfer.getInstance()});
	}

	private String
	truncate(
		String	str )
	{
		if ( str.length() > 23 ){
			return( str.substring( 0, 20 ) + "..." );
		}else {
			return( str );
		}
	}
	protected void
	updateTable(
		boolean		is_pub )
	{
		if ( init_complete ){

			if ( is_pub ){
				public_buddy_table.setItemCount( public_buddies.size());
				public_buddy_table.clearAll();
				public_buddy_table.redraw();
			}else{
				anon_buddy_table.setItemCount( anon_buddies.size());
				anon_buddy_table.clearAll();
				anon_buddy_table.redraw();
			}
		}
	}

	protected void
	updatePartialBuddyTable()
	{
		if ( init_complete ){

			partial_buddy_table.setItemCount( partial_buddies.size());
			partial_buddy_table.clearAll();
			partial_buddy_table.redraw();
		}
	}
	
	@Override
	public void
	initialised(
		boolean available )
	{
		print( "Initialisation complete: available=" + available );
	}

	@Override
	public void
	buddyAdded(
		final BuddyPluginBuddy	buddy )
	{
		if ( public_buddy_table.isDisposed()){

			return;
		}

		boolean is_pub = buddy.isPublicNetwork();

		buddy.getMessageHandler().addListener(
			new BuddyPluginBuddyMessageListener()
			{
				@Override
				public void
				messageQueued(
					BuddyPluginBuddyMessage message )
				{
					print( message.getBuddy().getName() + ": message queued, id=" + message.getID());

					update();
				}

				@Override
				public void
				messageDeleted(
					BuddyPluginBuddyMessage		message )
				{
					print( message.getBuddy().getName() + ": message deleted, id=" + message.getID());

					update();
				}

				@Override
				public boolean
				deliverySucceeded(
					BuddyPluginBuddyMessage		message,
					Map							reply )
				{
					print( message.getBuddy().getName() + ": message delivered, id=" + message.getID() + ", reply=" + reply );

					update();

					return( true );
				}

				@Override
				public void
				deliveryFailed(
					BuddyPluginBuddyMessage		message,
					BuddyPluginException cause )
				{
					print( message.getBuddy().getName() + ": message failed, id=" + message.getID(), cause );

					update();
				}

				protected void
				update()
				{
					if ( !public_buddy_table.isDisposed()){

						public_buddy_table.getDisplay().asyncExec(
							new Runnable()
							{
								@Override
								public void
								run()
								{
									if ( !public_buddy_table.isDisposed()){

										updateTable( is_pub );
									}
								}
							});
					}
				}
			});

		List<BuddyPluginBuddy> 	buddies 	= is_pub?public_buddies:anon_buddies;
		Table					buddy_table = is_pub?public_buddy_table:anon_buddy_table;

		if ( !buddies.contains( buddy )){

			buddy_table.getDisplay().asyncExec(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( buddy.isTransient()){

								return;
							}

							if ( !buddy_table.isDisposed()){

								if ( !buddies.contains( buddy )){

									buddies.add( buddy );

									updateTable( is_pub );
								}
							}
						}
					});
		}
	}

	@Override
	public void
	buddyRemoved(
		final BuddyPluginBuddy	buddy )
	{
		boolean is_pub = buddy.isPublicNetwork();

		List<BuddyPluginBuddy> 	buddies 	= is_pub?public_buddies:anon_buddies;
		Table					buddy_table = is_pub?public_buddy_table:anon_buddy_table;

		if ( !buddy_table.isDisposed()){

			buddy_table.getDisplay().asyncExec(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( !buddy_table.isDisposed()){

								if ( buddies.remove( buddy )){

									updateTable( is_pub );
								}
							}
						}
					});
		}
	}

	@Override
	public void
	buddyChanged(
		final BuddyPluginBuddy	buddy )
	{
		boolean is_pub = buddy.isPublicNetwork();

		Table					buddy_table = is_pub?public_buddy_table:anon_buddy_table;

		if ( !buddy_table.isDisposed()){

			buddy_table.getDisplay().asyncExec(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( !buddy_table.isDisposed()){

								updateTable( is_pub );
							}
						}
					});
		}
	}

	@Override
	public void
	partialBuddyAdded(
		final PartialBuddy	buddy )
	{
		if ( partial_buddy_table.isDisposed()){

			return;
		}


		if ( !partial_buddies.contains( buddy )){

			partial_buddy_table.getDisplay().asyncExec(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( !partial_buddy_table.isDisposed()){

								if ( !partial_buddies.contains( buddy )){

									partial_buddies.add( buddy );

									updatePartialBuddyTable();
								}
							}
						}
					});
		}
	}

	@Override
	public void
	partialBuddyRemoved(
		final PartialBuddy	buddy )
	{
		if ( !partial_buddy_table.isDisposed()){

			partial_buddy_table.getDisplay().asyncExec(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( !partial_buddy_table.isDisposed()){

								if ( partial_buddies.remove( buddy )){

									updatePartialBuddyTable();
								}
							}
						}
					});
		}
	}

	@Override
	public void
	partialBuddyChanged(
		final PartialBuddy	buddy )
	{
		if ( !partial_buddy_table.isDisposed()){

			partial_buddy_table.getDisplay().asyncExec(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( !partial_buddy_table.isDisposed()){

								updatePartialBuddyTable();
							}
						}
					});
		}
	}
	
	@Override
	public void
	messageLogged(
		String		str,
		boolean		error )
	{
		print( str, error?LOG_ERROR:LOG_NORMAL, false, false );
	}

	@Override
	public void
	enabledStateChanged(
		boolean claassic_enabled, boolean beta_enabled )
	{
	}

	@Override
	public void
	updated()
	{
	}

	protected void
	print(
		String		str,
		Throwable	e )
	{
		print( str + ": " + Debug.getNestedExceptionMessage( e ));
	}

	protected void
	print(
		String		str )
	{
		print( str, LOG_NORMAL, false, true );
	}

	protected void
	print(
		final String		str,
		final int			log_type,
		final boolean		clear_first,
		boolean				log_to_plugin )
	{
		if ( log_to_plugin ){

			plugin.log( null, str );
		}

		if ( !log.isDisposed()){

			final int f_log_type = log_type;

			log.getDisplay().asyncExec(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( log.isDisposed()){

								return;
							}

							int	start;

							if ( clear_first ){

								start	= 0;

								log.setText( str + "\n" );

							}else{

								String	text = log.getText();

								start = text.length();

								if ( start > 32000 ){

									log.replaceTextRange( 0, 1024, "" );

									start = log.getText().length();
								}

								log.append( str + "\n" );
							}

							Color 	color;

							if ( f_log_type == LOG_NORMAL ){

								color = Colors.black;

							}else if ( f_log_type == LOG_SUCCESS ){

								color = Colors.green;

							}else{

								color = Colors.red;
							}

							if ( color != Colors.black ){

								StyleRange styleRange = new StyleRange();
								styleRange.start = start;
								styleRange.length = str.length();
								styleRange.foreground = color;
								log.setStyleRange(styleRange);
							}

							log.setSelection( log.getText().length());
						}
					});
		}
	}

	protected void
	destroy()
	{
		composite = null;

		plugin.removeListener( this );

		plugin.removePartialBuddyListener( this );
	}

	protected class
	FilterComparator
		implements Comparator<BuddyPluginBuddy>
	{
		boolean ascending = false;

		static final int FIELD_NAME			= 0;
		static final int FIELD_ONLINE 		= 1;
		static final int FIELD_LAST_SEEN 	= 2;
		static final int FIELD_YGM		 	= 3;
		static final int FIELD_LAST_MSG 	= 4;
		static final int FIELD_LOC_CAT	 	= 5;
		static final int FIELD_REM_CAT 		= 6;
		static final int FIELD_READ_CAT 	= 7;
		static final int FIELD_CON		 	= 8;
		static final int FIELD_TRACK	 	= 9;
		static final int FIELD_MSG_IN	 	= 10;
		static final int FIELD_MSG_OUT	 	= 11;
		static final int FIELD_QUEUED	 	= 12;
		static final int FIELD_BYTES_IN 	= 13;
		static final int FIELD_BYTES_OUT 	= 14;
		static final int FIELD_SS		 	= 15;

		int field = FIELD_NAME;

		@Override
		public int
		compare(
			BuddyPluginBuddy b1,
			BuddyPluginBuddy b2)
		{
			int	res = 0;

			if(field == FIELD_NAME){
				 res = b1.getName().compareTo( b2.getName());
			}else if(field == FIELD_ONLINE){
				res = ( b1.isOnline( false )?1:0 ) - ( b2.isOnline( false )?1:0 );
			}else if(field == FIELD_LAST_SEEN){
				res = sortInt( b1.getLastTimeOnline() - b2.getLastTimeOnline());
			}else if(field == FIELD_YGM){
				res = sortInt( b1.getLastMessagePending() - b2.getLastMessagePending());
			}else if(field == FIELD_LAST_MSG){
				res = b1.getLastMessageReceived().compareTo( b2.getLastMessageReceived());
			}else if(field == FIELD_LOC_CAT){
				res = compareStrings( b1.getLocalAuthorisedRSSTagsOrCategoriesAsString(), b2.getLocalAuthorisedRSSTagsOrCategoriesAsString());
			}else if(field == FIELD_REM_CAT){
				res = compareStrings( b1.getRemoteAuthorisedRSSTagsOrCategoriesAsString(), b2.getRemoteAuthorisedRSSTagsOrCategoriesAsString());
			}else if(field == FIELD_READ_CAT){
				res = compareStrings( b1.getLocalReadTagsOrCategoriesAsString(), b2.getLocalReadTagsOrCategoriesAsString());
			}else if(field == FIELD_CON){
				res = b1.getConnectionsString().compareTo( b2.getConnectionsString());
			}else if(field == FIELD_TRACK){
				res = tracker.getTrackingStatus( b1 ).compareTo( tracker.getTrackingStatus( b2 ));
			}else if(field == FIELD_MSG_IN){
				res = b1.getMessageInCount() - b2.getMessageInCount();
			}else if(field == FIELD_MSG_OUT){
				res = b1.getMessageOutCount() - b2.getMessageOutCount();
			}else if(field == FIELD_QUEUED){
				res = b1.getMessageHandler().getMessageCount() - b2.getMessageHandler().getMessageCount();
			}else if(field == FIELD_BYTES_IN){
				res = b1.getBytesInCount() - b2.getBytesInCount();
			}else if(field == FIELD_BYTES_OUT){
				res = b1.getBytesOutCount() - b2.getBytesOutCount();
			}else if(field == FIELD_SS){
				res =  b1.getSubsystem() - b2.getSubsystem();
			}

			return(( ascending ? 1 : -1) * res );
		}

		protected int
		compareStrings(
			String	s1,
			String	s2 )
		{
			if ( s1 == null && s2 == null ){
				return(0);
			}else if ( s1 == null ){
				return(-1);
			}else if ( s2 == null ){
				return( 1 );
			}else{
				return( s1.compareTo(s2));
			}
		}

		protected int
		sortInt(
			long	l )
		{
			if ( l < 0 ){
				return( -1 );
			}else if ( l > 0 ){
				return( 1 );
			}else{
				return( 0 );
			}
		}
		public void
		setField(
			int newField )
		{
			if(field == newField) ascending = ! ascending;

			field = newField;
		}
	}
	
	protected class
	PBFilterComparator
		implements Comparator<PartialBuddy>
	{
		boolean ascending = false;

		static final int FIELD_PEER				= 0;
		static final int FIELD_DOWNLOADS 		= 1;
		

		int field = FIELD_PEER;

		@Override
		public int
		compare(
			PartialBuddy	 b1,
			PartialBuddy	 b2)
		{
			int	res = 0;

			if(field == FIELD_PEER){
				 res = b1.getName().compareTo( b2.getName());
			}

			return(( ascending ? 1 : -1) * res );
		}

		public void
		setField(
			int newField )
		{
			if (field == newField) ascending = ! ascending;

			field = newField;
		}
	}
}
