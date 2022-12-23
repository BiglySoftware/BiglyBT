/*
 * Created on Apr 26, 2008
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

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FrequencyLimitedDispatcher;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.core.util.Wiki;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginUI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.subs.SubscriptionResult;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.sharing.ShareResourceDir;
import com.biglybt.pif.sharing.ShareResourceFile;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.pif.utils.search.SearchResult;
import com.biglybt.pif.utils.subscriptions.SubscriptionManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.PropertiesWindow;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.swt.FixedURLTransfer;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.pif.UISWTInputReceiver;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;
import com.biglybt.ui.swt.utils.TooltipShell;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.*;
import com.biglybt.plugin.net.buddy.BuddyPluginBuddy;
import com.biglybt.plugin.net.buddy.BuddyPluginNetwork;
import com.biglybt.ui.swt.imageloader.ImageLoader;

public class
BuddyPluginViewBetaChat
	implements ChatListener
{
	private static final boolean TEST_LOOPBACK_CHAT = System.getProperty( "az.chat.loopback.enable", "0" ).equals( "1" );
	private static final boolean DEBUG_ENABLED		= BuddyPluginBeta.DEBUG_ENABLED;

	private static final int	MAX_MSG_CHUNK_ENABLE	= 500;	// won't chunk if not exceeded
	private static final int	MAX_MSG_CHUNK_LENGTH	= 400;	// chunk size
	private static final int	MAX_MSG_OVERALL_LENGTH	= 2048;	// max user can enter in input field
	
	private static final String	TI_MSG_COUNT = "bpvbc:mc";
	

	private static final Set<BuddyPluginViewBetaChat>	active_windows = new HashSet<>();

	private static boolean auto_ftux_popout_done	= false;

	private static Map<String,String>	text_cache = new HashMap<>();
	
	protected static void
	createChatWindow(
		BuddyPluginView	view,
		BuddyPlugin plugin,
		ChatInstance	chat )
	{
		createChatWindow( view, plugin, chat, false );
	}

	protected static void
	createChatWindow(
		BuddyPluginView	view,
		BuddyPlugin		plugin,
		ChatInstance	chat,
		boolean			force_popout )
	{
		for ( BuddyPluginViewBetaChat win: active_windows ){

			if ( win.getChat() == chat ){

				Shell existing = win.getShell();

				if ( existing.isVisible()){

					existing.setActive();
				}

				chat.destroy();

				return;
			}
		}

		if ( !force_popout ){

			if ( plugin.getBeta().getWindowsToSidebar()){

					if ( BuddyPluginUI.openChat( chat.getNetwork(), chat.getKey())){

						chat.destroy();

						return;
					}

			}
		}

			// using this approach to create the windows gives us the ability to persist the pop-outs over restart
			
		BuddyPluginUI.popOutChat( chat, true );
		// new BuddyPluginViewBetaChat( view, plugin, chat );
	}

	private final BuddyPluginView		view;
	private final BuddyPlugin			plugin;
	private final BuddyPluginBeta		beta;
	private final ChatInstance			chat;

	private boolean						chat_available;

	private final LocaleUtilities		lu;

	private Shell 					shell;

	private Composite 				ftux_stack;
	
	private StyledText 				log;
	private StyleRange[]			log_styles = new StyleRange[0];

	private BufferedLabel			table_header_left;
	private Table					buddy_table;
	private int						bt_col_offset;
	private BufferedLabel		 	status;

	private Button 					shared_nick_button;
	private Text 					nickname;

	private Text					input_area;

	private DropTarget[]			drop_targets;

	private LinkedHashMap<ChatMessage,Integer>	messages		= new LinkedHashMap<>();
	private List<ChatParticipant>				participants 	= new ArrayList<>();

	private Map<ChatParticipant,ChatMessage>	participant_last_message_map = new HashMap<>();

	private boolean		table_resort_required;

	private Font	italic_font;
	private Font	bold_font;
	private Font	big_font;
	private Font	small_font;

	private Color	ftux_dark_bg;
	private Color	ftux_dark_fg;
	private Color	ftux_light_bg;

	private boolean	ftux_ok;
	private boolean	build_complete;

	private TimerEventPeriodic timer;
	
	private
	BuddyPluginViewBetaChat(
		BuddyPluginView	_view,
		BuddyPlugin		_plugin,
		ChatInstance	_chat )
	{
		view	= _view;
		plugin	= _plugin;
		chat	= _chat;
		beta	= plugin.getBeta();

		lu		= plugin.getPluginInterface().getUtilities().getLocaleUtilities();

		if ( beta.getStandAloneWindows()){

			shell = ShellFactory.createShell( (Shell)null, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX );

		}else{

			shell = ShellFactory.createMainShell( SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX );
		}

		shell.addListener(
			SWT.Show,
			new Listener() {
				@Override
				public void handleEvent(Event event) {
					activate();
				}
			});

		shell.setText( lu.getLocalisedMessageText( "label.chat" ) + ": " + chat.getName());

		Utils.setShellIcon(shell);

		build( shell );

		shell.addListener(
			SWT.Traverse,
			new Listener()
			{
				@Override
				public void
				handleEvent(
					Event e )
				{
					if ( e.character == SWT.ESC){

						close();
				}
			}
			});

		shell.addControlListener(
			new ControlListener()
			{
				private volatile Rectangle last_position;

				private FrequencyLimitedDispatcher disp =
					new FrequencyLimitedDispatcher(
						new AERunnable() {

							@Override
							public void
							runSupport()
							{
								Rectangle	pos = last_position;

								String str = pos.x+","+pos.y+","+pos.width+","+pos.height;

								COConfigurationManager.setParameter( "azbuddy.dchat.ui.last.win.pos", str );
							}
						},
					1000 );

				@Override
				public void
				controlResized(
					ControlEvent e)
				{
					handleChange();
				}

				@Override
				public void
				controlMoved(
					ControlEvent e)
				{
					handleChange();
				}

				private void
				handleChange()
				{
					last_position = shell.getBounds();

					disp.dispatch();
				}
			});


		int DEFAULT_WIDTH	= 500;
		int DEFAULT_HEIGHT	= 500;
		int MIN_WIDTH		= 300;
		int MIN_HEIGHT		= 150;


		String str_pos = COConfigurationManager.getStringParameter( "azbuddy.dchat.ui.last.win.pos", "" );

		Rectangle last_bounds = null;

		try{
			if ( str_pos != null && str_pos.length() > 0 ){

				String[] bits = str_pos.split( "," );

				if ( bits.length == 4 ){

					int[]	 i_bits = new int[4];

					for ( int i=0;i<bits.length;i++){

						i_bits[i] = Integer.parseInt( bits[i] );
					}

					last_bounds =
						new Rectangle(
							i_bits[0],
							i_bits[1],
							Math.max( MIN_WIDTH, i_bits[2] ),
							Math.max( MIN_HEIGHT, i_bits[3] ));
				}
			}
		}catch( Throwable e ){
		}

	    //Utils.createURLDropTarget(shell, input_area);

		if ( active_windows.size() > 0 ){

			int	max_x = 0;
			int max_y = 0;

			for ( BuddyPluginViewBetaChat window: active_windows ){

				if ( !window.shell.isDisposed()){

					Rectangle rect = window.shell.getBounds();

					max_x = Math.max( max_x, rect.x );
					max_y = Math.max( max_y, rect.y );
				}
			}

			Rectangle rect = new Rectangle( 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT );

			rect.x = max_x + 16;
			rect.y = max_y + 16;

			if ( last_bounds != null ){

				rect.width 	= last_bounds.width;
				rect.height	=  last_bounds.height;
			}

			shell.setBounds( rect );

			Utils.verifyShellRect( shell, true );

		}else{

			if ( last_bounds != null ){

				shell.setBounds( last_bounds );

				Utils.verifyShellRect( shell, true );

			}else{

			    shell.setSize( DEFAULT_WIDTH, DEFAULT_HEIGHT );

				Utils.centreWindow(shell);
			}
		}

	    active_windows.add( this );

	    shell.addDisposeListener(
	    	new DisposeListener(){
				@Override
				public void
				widgetDisposed(DisposeEvent e)
				{
					active_windows.remove( BuddyPluginViewBetaChat.this );
				}
			});

	    shell.open();

	    shell.forceActive();
	}

	protected
	BuddyPluginViewBetaChat(
		BuddyPluginView	_view,
		BuddyPlugin		_plugin,
		ChatInstance	_chat,
		Composite		_parent )
	{
		view	= _view;
		plugin	= _plugin;
		chat	= _chat;
		beta	= plugin.getBeta();

		lu		= plugin.getPluginInterface().getUtilities().getLocaleUtilities();

		build( _parent );
	}

	private Shell
	getShell()
	{
		return( shell );
	}

	private ChatInstance
	getChat()
	{
		return( chat );
	}

	private void
	build(
		Composite		parent )
	{
		view.registerUI( chat );
		
		try{
			
			chat_available	= chat.isAvailable();

			buildSupport( parent );
					
			chat.addListener( this );
			
		}catch( RuntimeException e ) {
			
			view.unregisterUI( chat );
			
			throw( e );
		}
		
		timer = 
			SimpleTimer.addPeriodicEvent( 
			"timer",
			15*1000,
			(ev)->{

				Utils.execSWTThread(
					()->{
						if (  buddy_table == null || buddy_table.isDisposed()){
							
							timer.cancel();
							
							return;
						}
						
						timerTick();
					});
			});
	}
	
	private void
	buildSupport(
		Composite		parent )
	{
		log					= null;
		log_styles 			= new StyleRange[0];
		table_header_left	= null;
		buddy_table			= null;
		shared_nick_button	= null;
		nickname			= null;
		input_area			= null;
			
		messages.clear();
		participants.clear();
		participant_last_message_map.clear();
		
		try {
			build_complete = false;
			
			Utils.disposeComposite( parent, false );
			
			buildSupport2( parent );
			
		}finally {
			
			build_complete = true;
		}
	}
	
	private void
	buildSupport2(
		Composite		parent )
	{
		boolean public_chat = !chat.isPrivateChat();

		try{
			String cdf = beta.getCustomDateFormat();
			
			if ( !cdf.isEmpty()){
			
				custom_date_format = new SimpleDateFormat( cdf );
			}		
		}catch( Throwable e ){
		}
		
		if ( chat.getViewType() == BuddyPluginBeta.VIEW_TYPE_DEFAULT || chat.isReadOnly()){
			
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			parent.setLayout(layout);
			GridData grid_data = new GridData(GridData.FILL_BOTH );
			parent.setLayoutData(grid_data);

			Composite sash_top_bottom = new Composite( parent, SWT.NONE );
			FormLayout flayout = new FormLayout();
			flayout.marginHeight = 0;
			flayout.marginWidth = 0;
			sash_top_bottom.setLayout(flayout);
	
			grid_data = new GridData(GridData.FILL_BOTH );
			sash_top_bottom.setLayoutData(grid_data);

			Utils.SashWrapper2 sash_tb = Utils.createSashWrapper2( sash_top_bottom, "bpvbc.sash.top.bottom" );

			Composite[] sash_tb_kids = sash_tb.getChildren();
			
			Composite top_area = sash_tb_kids[0];

			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			top_area.setLayout(layout);

			Composite sash_left_right = new Composite( top_area, SWT.NONE );
			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			sash_left_right.setLayout(layout);
	
			grid_data = new GridData(GridData.FILL_BOTH );
			grid_data.horizontalSpan = 2;
			sash_left_right.setLayoutData(grid_data);

			final SashForm sash_lr = new SashForm(sash_left_right,SWT.HORIZONTAL );
			grid_data = new GridData(GridData.FILL_BOTH );
			sash_lr.setLayoutData(grid_data);

			final Composite lhs = new Composite(sash_lr, SWT.NONE);
	
			layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.marginTop = 4;
			layout.marginLeft = 4;
			lhs.setLayout(layout);
			grid_data = new GridData(GridData.FILL_BOTH );
			grid_data.widthHint = 300;
			lhs.setLayoutData(grid_data);

			buildStatus( sash_top_bottom, lhs );
	
			Composite log_holder = buildFTUX( lhs, SWT.BORDER );
			
				// LOG panel
	
			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.marginLeft = 4;
			log_holder.setLayout(layout);
			//grid_data = new GridData(GridData.FILL_BOTH );
			//grid_data.horizontalSpan = 2;
			//Utils.setLayoutData(log_holder, grid_data);
	
			log = Utils.createStyledText( log_holder,SWT.READ_ONLY | SWT.V_SCROLL | SWT.WRAP | SWT.NO_FOCUS );
			
			grid_data = new GridData(GridData.FILL_BOTH);
			grid_data.horizontalSpan = 1;
			//grid_data.horizontalIndent = 4;
			log.setLayoutData(grid_data);
			//log.setIndent( 4 );
	
			log.setEditable( false );
	
			log_holder.setBackground( log.getBackground());
	
			final Menu log_menu = new Menu( log );
	
			log.setMenu(  log_menu );
			
			log.addMenuDetectListener(
				new MenuDetectListener() {
	
					@Override
					public void
					menuDetected(
						MenuDetectEvent e )
					{
						e.doit = false;
	
						boolean	handled = false;
	
						for ( MenuItem mi: log_menu.getItems()){
	
							mi.dispose();
						}
	
						try{
							Point mapped = log.getDisplay().map( null, log, new Point( e.x, e.y ));
	
							int offset = log.getOffsetAtLocation( mapped );
	
							final StyleRange sr = log.getStyleRangeAtOffset(  offset );
	
							if ( sr != null ){
	
								Object data = sr.data;
	
								if ( data instanceof ChatParticipant ){
	
									ChatParticipant cp = (ChatParticipant)data;
	
									List<ChatParticipant> cps = new ArrayList<>();
	
									cps.add( cp );
	
									buildParticipantMenu( log_menu, cps );
	
									handled = true;
	
								}else if ( data instanceof String ){
	
									String url_str = (String)sr.data;
	
									String str = url_str;
	
									if ( str.length() > 50 ){
	
										str = str.substring( 0, 50 ) + "...";
									}
	
										// magnet special case for anon chat
	
									if ( chat.isAnonymous() && url_str.toLowerCase( Locale.US ).startsWith( "magnet:" )){
	
										String[] magnet_uri = { url_str };
	
										Set<String> networks = UrlUtils.extractNetworks( magnet_uri );
	
										String i2p_only_uri = magnet_uri[0] + "&net=" + UrlUtils.encode( AENetworkClassifier.AT_I2P );
	
										String i2p_only_str = i2p_only_uri;
	
										if ( i2p_only_str.length() > 50 ){
	
											i2p_only_str = i2p_only_str.substring( 0, 50 ) + "...";
										}
	
										i2p_only_str = lu.getLocalisedMessageText( "azbuddy.dchat.open.i2p.magnet" ) + ": " + i2p_only_str;
	
										final MenuItem mi_open_i2p_vuze = new MenuItem( log_menu, SWT.PUSH );
	
										mi_open_i2p_vuze.setText( i2p_only_str);
										mi_open_i2p_vuze.setData( i2p_only_uri );
	
										mi_open_i2p_vuze.addSelectionListener(
											new SelectionAdapter() {
	
												@Override
												public void
												widgetSelected(
													SelectionEvent e )
												{
													String url_str = (String)mi_open_i2p_vuze.getData();
	
													if ( url_str != null ){
	
														TorrentOpener.openTorrent( url_str );
													}
												}
											});
	
										if ( networks.size() == 1 && networks.iterator().next() == AENetworkClassifier.AT_I2P ){
	
											// already done above
	
										}else{
	
											str = lu.getLocalisedMessageText( "azbuddy.dchat.open.magnet" ) + ": " + str;
	
											final MenuItem mi_open_vuze = new MenuItem( log_menu, SWT.PUSH );
	
											mi_open_vuze.setText( str);
											mi_open_vuze.setData( url_str );
	
											mi_open_vuze.addSelectionListener(
												new SelectionAdapter() {
	
													@Override
													public void
													widgetSelected(
														SelectionEvent e )
													{
														String url_str = (String)mi_open_vuze.getData();
	
														if ( url_str != null ){
	
															TorrentOpener.openTorrent( url_str );
														}
													}
												});
										}
									}else{
	
	
										str = lu.getLocalisedMessageText( "azbuddy.dchat.open.in.vuze" ) + ": " + str;
	
										final MenuItem mi_open_vuze = new MenuItem( log_menu, SWT.PUSH );
	
										mi_open_vuze.setText( str);
										mi_open_vuze.setData( url_str );
	
										mi_open_vuze.addSelectionListener(
											new SelectionAdapter() {
	
												@Override
												public void
												widgetSelected(
													SelectionEvent e )
												{
													String url_str = (String)mi_open_vuze.getData();
	
													if ( url_str != null ){
	
														String lc_url_str = url_str.toLowerCase( Locale.US );
	
														if ( lc_url_str.startsWith( "chat:" )){
	
															try{
																beta.handleURI( url_str, true );
	
															}catch( Throwable f ){
	
																Debug.out( f );
															}
	
														}else{
	
															TorrentOpener.openTorrent( url_str );
														}
													}
												}
											});
									}
	
									final MenuItem mi_open_ext = new MenuItem( log_menu, SWT.PUSH );
	
									mi_open_ext.setText( lu.getLocalisedMessageText( "azbuddy.dchat.open.in.browser" ));
	
									mi_open_ext.addSelectionListener(
										new SelectionAdapter() {
	
											@Override
											public void
											widgetSelected(
												SelectionEvent e )
											{
												String url_str = (String)mi_open_ext.getData();
	
												Utils.launch( url_str );
											}
										});
	
									new MenuItem( log_menu, SWT.SEPARATOR );
	
									if ( chat.isAnonymous() && url_str.toLowerCase( Locale.US ).startsWith( "magnet:" )){
	
										String[] magnet_uri = { url_str };
	
										Set<String> networks = UrlUtils.extractNetworks( magnet_uri );
	
										String i2p_only_uri = magnet_uri[0] + "&net=" + UrlUtils.encode( AENetworkClassifier.AT_I2P );
	
										final MenuItem mi_copy_i2p_clip = new MenuItem( log_menu, SWT.PUSH );
	
										mi_copy_i2p_clip.setText( lu.getLocalisedMessageText( "azbuddy.dchat.copy.i2p.magnet" ));
										mi_copy_i2p_clip.setData( i2p_only_uri );
	
										mi_copy_i2p_clip.addSelectionListener(
												new SelectionAdapter() {
	
													@Override
													public void
													widgetSelected(
														SelectionEvent e )
													{
														String url_str = (String)mi_copy_i2p_clip.getData();
	
														if ( url_str != null ){
	
															ClipboardCopy.copyToClipBoard( url_str );
														}
													}
												});
	
										if ( networks.size() == 1 && networks.iterator().next() == AENetworkClassifier.AT_I2P ){
	
											// already done above
	
										}else{
	
											final MenuItem mi_copy_clip = new MenuItem( log_menu, SWT.PUSH );
	
											mi_copy_clip.setText( lu.getLocalisedMessageText( "azbuddy.dchat.copy.magnet" ));
											mi_copy_clip.setData( url_str );
	
											mi_copy_clip.addSelectionListener(
													new SelectionAdapter() {
	
														@Override
														public void
														widgetSelected(
															SelectionEvent e )
														{
															String url_str = (String)mi_copy_clip.getData();
	
															if ( url_str != null ){
	
																ClipboardCopy.copyToClipBoard( url_str );
															}
														}
													});
	
										}
									}else{
	
										final MenuItem mi_copy_clip = new MenuItem( log_menu, SWT.PUSH );
	
										mi_copy_clip.setText( lu.getLocalisedMessageText( "label.copy.to.clipboard" ));
										mi_copy_clip.setData( url_str );
	
										mi_copy_clip.addSelectionListener(
												new SelectionAdapter() {
	
													@Override
													public void
													widgetSelected(
														SelectionEvent e )
													{
														String url_str = (String)mi_copy_clip.getData();
	
														if ( url_str != null ){
	
															ClipboardCopy.copyToClipBoard( url_str );
														}
													}
												});
									}
	
									if ( url_str.toLowerCase().startsWith( "http" )){
	
										mi_open_ext.setData( url_str );
	
										mi_open_ext.setEnabled( true );
	
									}else{
	
										mi_open_ext.setEnabled( false );
									}
	
									handled = true;
								}else{
	
									if ( Constants.isCVSVersion()){
	
										if ( sr instanceof MyStyleRange ){
	
											final MyStyleRange msr = (MyStyleRange)sr;
	
											MenuItem   item = new MenuItem( log_menu, SWT.NONE );
	
											item.setText( MessageText.getString( "label.copy.to.clipboard"));
	
											item.addSelectionListener(
												new SelectionAdapter()
												{
													@Override
													public void
													widgetSelected(
														SelectionEvent e )
													{
														ClipboardCopy.copyToClipBoard( msr.message.getMessage());
													}
												});
	
											handled = true;
										}
									}
								}
							}
						}catch( Throwable f ){
						}
	
						if ( !handled ){
	
							final String text = log.getSelectionText();
	
							if ( text != null && text.length() > 0 ){
	
								MenuItem   item = new MenuItem( log_menu, SWT.NONE );
	
								item.setText( MessageText.getString( "label.copy.to.clipboard"));
	
								item.addSelectionListener(
									new SelectionAdapter()
									{
										@Override
										public void
										widgetSelected(
											SelectionEvent e )
										{
											ClipboardCopy.copyToClipBoard( text );
										}
									});
	
								handled = true;
							}
						}
	
						if ( handled ){
	
							e.doit = true;
						}
					}
				});
	
			log.addListener(
				SWT.MouseDoubleClick,
				new Listener()
				{
					@Override
					public void
					handleEvent(
						Event e )
					{
						try{
							final int offset = log.getOffsetAtLocation( new Point( e.x, e.y ) );
	
							for ( int i=0;i<log_styles.length;i++){
	
								StyleRange sr = log_styles[i];
	
								Object data = sr.data;
	
								if ( data != null && offset >= sr.start && offset < sr.start + sr.length ){
	
									boolean anon_chat = chat.isAnonymous();
	
									if ( data instanceof String ){
	
										final String	url_str = (String)data;
	
										String lc_url_str = url_str.toLowerCase( Locale.US );
	
										if ( lc_url_str.startsWith( "chat:" )){
	
												// no double-click support for anon->public chats
	
											if ( anon_chat && !lc_url_str.startsWith( "chat:anon:" )){
	
												return;
											}
	
											try{
												beta.handleURI( url_str, true );
	
											}catch( Throwable f ){
	
												Debug.out( f );
											}
										}else{
	
												// no double-click support for anon->public urls
	
											if ( anon_chat ){
	
												try{
													String host = new URL( lc_url_str ).getHost();
	
														// note that magnet-uris are always decoded here as public, which is what we want mostly
	
													if ( AENetworkClassifier.categoriseAddress( host ) == AENetworkClassifier.AT_PUBLIC ){
	
														return;
	
													}
												}catch( Throwable f ){
	
													return;
												}
											}
	
											if ( 	lc_url_str.contains( ".torrent" ) ||
													UrlUtils.parseTextForMagnets( url_str ) != null ){
	
												TorrentOpener.openTorrent( url_str );
	
											}else{
	
												if ( url_str.toLowerCase( Locale.US ).startsWith( "http" )){
	
														// without this backoff we end up with the text widget
														// being left in a 'mouse down' state when returning to it :(
	
													Utils.execSWTThreadLater(
														100,
														new Runnable()
														{
															@Override
															public void
															run()
															{
																Utils.launch( url_str );
															}
														});
												}else{
	
													TorrentOpener.openTorrent( url_str );
												}
											}
										}
	
										log.setSelection( offset );
	
										e.doit = false;
	
									}else if ( data instanceof ChatParticipant ){
	
										ChatParticipant participant = (ChatParticipant)data;
	
										addNickString( participant );
									}
								}
							}
						}catch( Throwable f ){
	
						}
					}
				});
	
			log.addMouseTrackListener(
				new MouseTrackListener() {
	
					private StyleRange		old_range;
					private StyleRange		temp_range;
					private int				temp_index;
	
					@Override
					public void mouseHover(MouseEvent e) {
	
						boolean active = false;
	
						try{
							int offset = log.getOffsetAtLocation( new Point( e.x, e.y ) );
	
							for ( int i=0;i<log_styles.length;i++){
	
								StyleRange sr = log_styles[i];
	
								Object data = sr.data;
	
								if ( data != null && offset >= sr.start && offset < sr.start + sr.length ){
	
									if ( old_range != null  ){
	
										if ( 	temp_index < log_styles.length &&
												log_styles[temp_index] == temp_range ){
	
											log_styles[ temp_index ] = old_range;
	
											old_range	= null;
										}
									}
	
									sr = log_styles[i];
	
									String tt_extra = "";
	
									if ( data instanceof String ){
	
										try{
											URL url = new URL((String)data);
	
											String query = url.getQuery();
	
											if ( query != null ){
	
												String[] bits = query.split( "&" );
	
												int seeds 		= -1;
												int leechers	= -1;
	
												for ( String bit: bits ){
	
													String[] temp = bit.split( "=" );
	
													String lhs = temp[0];
	
													if ( lhs.equals( "_s" )){
	
														seeds = Integer.parseInt( temp[1] );
	
													}else if ( lhs.equals( "_l" )){
	
														leechers = Integer.parseInt( temp[1] );
													}
												}
	
												if ( seeds != -1 && leechers != -1){
	
													tt_extra = ": seeds=" + seeds +", leechers=" + leechers;
												}
											}
										}catch( Throwable f ){
										}
									}
	
									Utils.setTT(log, MessageText.getString( "label.right.click.for.options" ) + tt_extra );
	
	
									StyleRange derp;
	
									if ( sr instanceof MyStyleRange ){
	
										derp = new MyStyleRange((MyStyleRange)sr );
									}else{
	
										derp = new StyleRange( sr );
									}
	
									derp.start = sr.start;
									derp.length = sr.length;
	
									derp.borderStyle = SWT.BORDER_DASH;
	
									old_range	= sr;
									temp_range	= derp;
									temp_index	= i;
	
									log_styles[i] = derp;
	
									log.setStyleRanges( log_styles );
	
									active = true;
	
									break;
								}
							}
						}catch( Throwable f ){
	
						}
	
						if ( !active ){
	
							Utils.setTT(log, "" );
	
							if ( old_range != null ){
	
								if ( 	temp_index < log_styles.length &&
										log_styles[temp_index] == temp_range ){
	
									log_styles[ temp_index ] = old_range;
	
									old_range	= null;
	
									log.setStyleRanges( log_styles );
								}
							}
						}
					}
	
					@Override
					public void mouseExit(MouseEvent e) {
						// TODO Auto-generated method stub
	
					}
	
					@Override
					public void mouseEnter(MouseEvent e) {
						// TODO Auto-generated method stub
	
					}
				});
	
	
			log.addKeyListener(
				new KeyAdapter()
				{
					@Override
					public void
					keyPressed(
						KeyEvent event )
					{
						int key = event.character;
	
						if ( key <= 26 && key > 0 ){
	
							key += 'a' - 1;
						}
	
						if ( key == 'a' && event.stateMask == SWT.MOD1 ){
	
							event.doit = false;
	
							log.selectAll();
						}
					}
				});
	
			Composite rhs = new Composite(sash_lr, SWT.NONE);
			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.marginTop = 4;
			layout.marginRight = 4;
			rhs.setLayout(layout);
			grid_data = new GridData(GridData.FILL_VERTICAL );
			int rhs_width=Constants.isWindows?150:160;
			grid_data.widthHint = rhs_width;
			rhs.setLayoutData(grid_data);

			// options
	
			Composite top_right = buildHelp( rhs );
	
				// nick name
	
			Composite nick_area = new Composite(top_right, SWT.NONE);
			layout = new GridLayout();
			layout.numColumns = 4;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			if ( !Constants.isWindows ){
				layout.horizontalSpacing = 2;
				layout.verticalSpacing = 2;
			}
			nick_area.setLayout(layout);
			grid_data = new GridData(GridData.FILL_HORIZONTAL );
			grid_data.horizontalSpan=3;
			nick_area.setLayoutData(grid_data);

			Label label = new Label( nick_area, SWT.NULL );
			label.setText( lu.getLocalisedMessageText( "azbuddy.dchat.nick" ));
			grid_data = new GridData();
			//grid_data.horizontalIndent=4;
			label.setLayoutData(grid_data);

			nickname = new Text( nick_area, SWT.BORDER );
			grid_data = new GridData( GridData.FILL_HORIZONTAL );
			grid_data.horizontalSpan=1;
			nickname.setLayoutData(grid_data);

			nickname.setText( chat.getNickname( false ));
			nickname.setMessage( chat.getDefaultNickname());
	
			label = new Label( nick_area, SWT.NULL );
			label.setText( lu.getLocalisedMessageText( "label.shared" ));
			Utils.setTT(label, lu.getLocalisedMessageText( "azbuddy.dchat.shared.tooltip" ));
	
			shared_nick_button = new Button( nick_area, SWT.CHECK );
	
			shared_nick_button.setSelection( chat.isSharedNickname());
	
			shared_nick_button.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void widgetSelected(SelectionEvent arg0) {
	
						boolean shared = shared_nick_button.getSelection();
	
						chat.setSharedNickname( shared );
					}
				});
	
			nickname.addListener(SWT.FocusOut, new Listener() {
		        @Override
		        public void handleEvent(Event event) {
		        	String nick = nickname.getText().trim();
	
		        	if ( chat.isSharedNickname()){
	
		        		if ( chat.getNetwork() == AENetworkClassifier.AT_PUBLIC ){
	
		        			beta.setSharedPublicNickname( nick );
	
		        		}else{
	
		        			beta.setSharedAnonNickname( nick );
		        		}
		        	}else{
	
		        		chat.setInstanceNickname( nick );
		        	}
		        }
		    });
	
	
			table_header_left = new BufferedLabel( top_right, SWT.DOUBLE_BUFFERED );
			grid_data = new GridData( GridData.FILL_HORIZONTAL );
			grid_data.horizontalSpan=2;
			if ( !Constants.isWindows ){
				grid_data.horizontalIndent = 2;
			}
			table_header_left.setLayoutData(grid_data);
			table_header_left.setText(MessageText.getString( "PeersView.state.pending" ));
	
			LinkLabel link = 
				new LinkLabel(
					top_right, 
					"Views.plugins.azbuddy.title",
					new Runnable(){
						
						@Override
						public void run(){
					
							if ( !plugin.isClassicEnabled()){
								
								plugin.setClassicEnabled( true, false );
							}					

							beta.selectClassicTab();
						}
					});
			
				// table
	
			buddy_table = new Table(rhs, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);
	
			String[] headers = {
				"azbuddy.ui.table.name",
				"!#!",
				"label.friend",
				"" };
	
			int[] sizes = {
				rhs_width-10,
				30,
				50,
				20};
	
			int[] aligns = { 
				SWT.LEFT,
				SWT.CENTER,
				SWT.LEFT,
				SWT.CENTER };
	
			if ( Constants.isWindows ){
					
					// https://bugs.eclipse.org/bugs/show_bug.cgi?id=43910 
					// if any column has an image then the first column gets an indent on Windows :(
				
				TableColumn tc = new TableColumn(buddy_table, SWT.LEFT );
				tc.setWidth( 0 );
				tc.setResizable( false );
				
				bt_col_offset = 1;
			}
			
			for (int i = 0; i < headers.length; i++){
	
				TableColumn tc = new TableColumn(buddy_table, aligns[i]);

				tc.setWidth(sizes[i]);
	
				String key = headers[i];
				
				if ( !key.isEmpty()){
				
					Messages.setLanguageText(tc, key);
				}
			}
	
		    buddy_table.setHeaderVisible(true);
	
		    grid_data = new GridData(GridData.FILL_BOTH);
		    
			buddy_table.setLayoutData(grid_data);

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
	
						setItemData( item );
					}
				});
	
			final Menu menu = new Menu(buddy_table);
	
			buddy_table.setMenu( menu );
	
			menu.addMenuListener(
				new MenuListener()
				{
					@Override
					public void
					menuShown(
						MenuEvent e )
					{
						MenuItem[] items = menu.getItems();
	
						for (int i = 0; i < items.length; i++){
	
							items[i].dispose();
						}
	
						final TableItem[] selection = buddy_table.getSelection();
	
						List<ChatParticipant>	participants = new ArrayList<>( selection.length );
	
						for (int i=0;i<selection.length;i++){
	
							TableItem item = selection[i];
	
							ChatParticipant	participant = (ChatParticipant)item.getData();
	
							if ( participant == null ){
	
									// item data won't be set yet for items that haven't been
									// visible...
	
								participant = setItemData( item );
							}
	
							if ( participant != null ){
	
								participants.add( participant );
							}
						}
	
						buildParticipantMenu( menu, participants );
					}
	
					@Override
					public void menuHidden(MenuEvent e) {
					}
				});
	
			buddy_table.addKeyListener(
					new KeyAdapter()
					{
						@Override
						public void
						keyPressed(
							KeyEvent event )
						{
							int key = event.character;
	
							if ( key <= 26 && key > 0 ){
	
								key += 'a' - 1;
							}
	
							if ( key == 'a' && event.stateMask == SWT.MOD1 ){
	
								event.doit = false;
	
								buddy_table.selectAll();
							}
						}
					});
	
	
			new TooltipShell(
				new TooltipShell.TooltipProvider(){
					
					@Override
					public String 
					getTooltip(
						Point location )
					{
						TableItem item = buddy_table.getItem( location );
						
						String tt = null;
						
						if ( item == null ){
						
							return( null );
						}
						
						ChatParticipant	participant = (ChatParticipant)item.getData();
						
						if ( participant == null ){
							
							return( null );
						}
						
						Rectangle status_bounds = item.getBounds( bt_col_offset + 2 );
						
						if ( status_bounds.contains( location )){
						
							tt = MessageText.getString( "dchat.friend.status.tt" );
							
						}else{	
							
							String fk = participant.getFriendKey();
							
							if ( fk  != null ){
								
								BuddyPluginBuddy buddy = plugin.getBuddyFromPublicKey( fk );
								
								String nick = "";
								
								if ( buddy != null ){
									
									String n = buddy.getNickName();
									
									if ( n != null && !n.isEmpty()){
										
										nick = ": " + n;
									}
								}
								
								List<String> profile = participant.getProfileData();
								
								if ( profile == null ){
									
									tt = MessageText.getString( "label.profile.pending" );
									
								}else{
									
									tt = MessageText.getString( "label.profile" ) + nick;
									
									for ( String p: profile ){
										
										String[] bits = p.split( "=", 2 );
										
										if ( bits.length == 2 ){
											
											String val = bits[1];
											
											URL u = UrlUtils.getRawURL(val);
											
											if ( u != null ){
												
												val = UrlUtils.getFriendlyName( u, val );
											}
											
											p = bits[0] + "=" + val;
										}
										
										tt += "\n    " + p;
									}
								}
							}
							
							List<ChatMessage> messages = participant.getMessages();
							
							int	 num = messages.size();
							
							if ( num > 0 ){
								
								tt = ( tt==null?"":(tt+"\n")) +  MessageText.getString( "label.messages" );
								
								int	start = Math.max( num-6, 0 );
								
								if ( start > 0 ){
									
									tt += "\n    ...";
								}
								
								for ( int i=start;i<messages.size();i++){
									
									ChatMessage msg = messages.get(i);
									
									String str = "";
									
									if ( msg.isIPFiltered()){
										
										str = "<" + MessageText.getString( "label.ip.filter" ) + ">: ";	
									}										
										 
									str += msg.getMessage();
											
									if ( str.length() > 50 ){
										
										str = str.substring( 0,  47 ) + "...";
									}
									
									tt += "\n    " + str;
								}
							}
						}
						
						
						return( tt );
					}
				}, buddy_table );
			
			buddy_table.addMouseListener(
				new MouseAdapter()
				{
					@Override
					public void
					mouseDoubleClick(
						MouseEvent e )
					{
						TableItem[] selection = buddy_table.getSelection();
	
						if ( selection.length != 1 ){
	
							return;
						}
	
						TableItem item = selection[0];
	
						ChatParticipant	participant = (ChatParticipant)item.getData();
	
						addNickString( participant );
	
					}
				});
	
		    Utils.maintainSashPanelWidth( sash_lr, rhs, new int[]{ 700, 300 }, "azbuddy.dchat.ui.sash.pos" );
	
		    /*
		    Listener sash_listener=
		    	new Listener()
		    	{
		    		private int	lhs_weight;
		    		private int	lhs_width;
	
			    	public void
					handleEvent(
						Event ev )
					{
			    		if ( ev.widget == lhs ){
	
			    			int[] weights = sash.getWeights();
	
	
			    			if ( lhs_weight != weights[0] ){
	
			    					// sash has moved
	
			    				lhs_weight = weights[0];
	
			    					// keep track of the width
	
			    				lhs_width = lhs.getBounds().width;
			    			}
			    		}else{
	
			    				// resize
	
			    			if ( lhs_width > 0 ){
	
					            int width = sash.getClientArea().width;
	
					            double ratio = (double)lhs_width/width;
	
					            lhs_weight = (int)(ratio*1000 );
	
					            sash.setWeights( new int[]{ lhs_weight, 1000 - lhs_weight });
			    			}
			    		}
				    }
			    };
	
		    lhs.addListener(SWT.Resize, sash_listener );
		    sash.addListener(SWT.Resize, sash_listener );
		    */
	
		    	// bottom area
	
		    Composite bottom_area = sash_tb_kids[1];
		    
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.marginBottom = 2;
			bottom_area.setLayout(layout);
		
				// Text
		
			input_area = new Text( bottom_area, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.BORDER);
			grid_data = new GridData(GridData.FILL_BOTH );
			grid_data.horizontalSpan = 1;
			//grid_data.heightHint = 30;
			grid_data.horizontalIndent = 4;
			input_area.setLayoutData(grid_data);

			//input_area.setIndent( 4 );
			
			input_area.setTextLimit( MAX_MSG_OVERALL_LENGTH );
	
			input_area.addVerifyListener(
				new VerifyListener(){
					
					@Override
					public void verifyText(VerifyEvent ev){
							// ctrl+i by default maps to \t
						
						if ( ev.text.equals( "\t" )){
								
							ev.doit = false;
						}
					}
				});
			
			input_area.addKeyListener(
				new KeyListener()
				{
					private LinkedList<String>	history 	= new LinkedList<>();
					private int					history_pos	= -1;
	
					private String				buffered_message = "";
	
					@Override
					public void
					keyPressed(
						KeyEvent e)
					{
						if ( e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR){
	
							e.doit = false;
	
							if (( e.stateMask & ( SWT.SHIFT | SWT.CTRL | SWT.ALT )) != 0 ) {
								
								input_area.insert( "\n" );
								
								return;
							}
							
							String message = input_area.getText().trim();
	
							if ( message.length() > 0 ){
	
								sendMessage(  message, true );
	
								history.addFirst( message );
	
								if ( history.size() > 32 ){
	
									history.removeLast();
								}
	
								history_pos = -1;
	
								buffered_message = "";
	
								input_area.setText( "" );
								
								text_cache.put( chat.getNetAndKey(), "" );
							}
						}else if ( e.keyCode == SWT.ARROW_UP ){
	
							history_pos++;
	
							if ( history_pos < history.size()){
	
								if ( history_pos == 0 ){
	
									buffered_message = input_area.getText().trim();
								}
	
								String msg = history.get( history_pos );
	
								input_area.setText( msg );
	
								input_area.setSelection( msg.length());
	
							}else{
	
								history_pos = history.size() - 1;
							}
	
							e.doit = false;
	
						}else if ( e.keyCode == SWT.ARROW_DOWN ){
	
							history_pos--;
	
							if ( history_pos >= 0 ){
	
								String msg = history.get( history_pos );
	
								input_area.setText( msg );
	
								input_area.setSelection( msg.length());
	
							}else{
	
								if ( history_pos == -1 ){
	
									input_area.setText( buffered_message );
	
									if ( buffered_message.length() > 0 ){
	
										input_area.setSelection( buffered_message.length());
	
										buffered_message = "";
									}
								}else{
	
									history_pos = -1;
								}
							}
	
							e.doit = false;
	
						}else{
	
							if ( e.stateMask == SWT.MOD1 ){
	
								int key = e.character;
	
								if ( key <= 26 && key > 0 ){
	
									key += 'a'-1;
								}
	
								if ( key == 'a' ){
	
									input_area.selectAll();
									
								}else if ( key == 'b' || key == 'i' ){
									
									String emp = key == 'b'?"**":"*";
									
									String 	sel = input_area.getSelectionText();
									
									Point p = input_area.getSelection();

									// unfortunately double-click to select grabs trailing spaces so trim back
									
									while( sel.endsWith( " " )){
										
										sel = sel.substring( 0, sel.length() - 1 );
										
										p.y--;
									}
									
									if ( !sel.isEmpty()){
										
										/*
										int[] range = input_area.getSelectionRanges();
										
										int emp_len = emp.length();
										
										if ( sel.startsWith( emp ) && sel.endsWith( emp ) && sel.length() >= emp_len * 2 ){
											
											input_area.replaceTextRange( range[0], range[1], sel.substring(emp_len, sel.length() - emp_len ));
											
											input_area.setSelection( range[0], range[0] + range[1] - emp_len*2 );
											
										}else{
											
											input_area.replaceTextRange( range[0], range[1], emp + sel + emp );
											
											input_area.setSelection( range[0], range[0] + range[1] + emp_len*2 );
										}
										*/
																				
										int emp_len = emp.length();
										
										String text = input_area.getText();
																				
										if ( sel.startsWith( emp ) && sel.endsWith( emp ) && sel.length() >= emp_len * 2 ){
										
											input_area.setText( text.substring( 0,  p.x ) + sel.substring(emp_len, sel.length() - emp_len ) + text.substring( p.y ));
											
											p.y -= emp_len*2;
										}else{
											
											input_area.setText( text.substring( 0,  p.x ) + emp + sel+ emp  + text.substring( p.y ));
											
											p.y += emp_len*2;
										}
										
										input_area.setSelection( p );
									}
								}
							}
						}
					}
	
					@Override
					public void
					keyReleased(
						KeyEvent e )
					{
					}
				});
	
			input_area.addDisposeListener(
				new DisposeListener(){
					
					@Override
					public void widgetDisposed(DisposeEvent arg0){
						
						if ( input_area != null && !input_area.isDisposed()){
							
							String text = input_area.getText();
							
							text_cache.put( chat.getNetAndKey(), text );
						}
					}
				});
			
			String cached_text = text_cache.get( chat.getNetAndKey());
			
			if ( cached_text != null && !cached_text.isEmpty()){
				
				input_area.setText( cached_text );
				
				input_area.setSelection( cached_text.length());
			}
			
			Composite button_area = new Composite( bottom_area, SWT.NULL );
	
			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.marginRight = 4;
			button_area.setLayout(layout);
	
			buildRSSButton( button_area );
		
			hookFTUXListener();
			
			if ( chat.isReadOnly()){
	
				input_area.setText( MessageText.getString( "azbuddy.dchat.ro" ));
			}	
	
			setInputAvailability( true );
			
			if ( !chat.isReadOnly()){
	
				drop_targets = new DropTarget[]{
					new DropTarget(log, DND.DROP_COPY),
					new DropTarget(input_area, DND.DROP_COPY)
				};
	
				for ( DropTarget drop_target: drop_targets ){
	
					drop_target.setTransfer(new Transfer[] {
						FixedURLTransfer.getInstance(),
						FileTransfer.getInstance(),
						TextTransfer.getInstance(),
					});
	
					drop_target.addDropListener(new DropTargetAdapter() {
						@Override
						public void dropAccept(DropTargetEvent event) {
							event.currentDataType = FixedURLTransfer.pickBestType(event.dataTypes,
									event.currentDataType);
						}
	
						@Override
						public void dragEnter(DropTargetEvent event) {
						}
	
						@Override
						public void dragOperationChanged(DropTargetEvent event) {
						}
	
						@Override
						public void dragOver(DropTargetEvent event) {
	
							if ((event.operations & DND.DROP_LINK) > 0)
								event.detail = DND.DROP_LINK;
							else if ((event.operations & DND.DROP_COPY) > 0)
								event.detail = DND.DROP_COPY;
							else if ((event.operations & DND.DROP_DEFAULT) > 0)
								event.detail = DND.DROP_COPY;
	
	
							event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL | DND.FEEDBACK_EXPAND;
						}
	
						@Override
						public void dragLeave(DropTargetEvent event) {
						}
	
						@Override
						public void drop(DropTargetEvent event) {
						
							handleDrop( 
								event.data,
								new DropAccepter(){
									
									@Override
									public void accept(String link){
										
										input_area.setText( input_area.getText() + link );
									}
								});
						}
					});
				}
			}
			
			sash_tb.setDefaultBottomHeight( bottom_area.computeSize( SWT.DEFAULT,  SWT.DEFAULT ).y );
			
			Control[] focus_controls = { log, input_area, buddy_table, nickname, shared_nick_button };
	
			Listener focus_listener = new Listener() {
	
				@Override
				public void handleEvent(Event event) {
					activate();
				}
			};
	
			for ( Control c: focus_controls ){
	
				c.addListener( SWT.FocusIn, focus_listener );
			}
	
			BuddyPluginBeta.ChatParticipant[] existing_participants = chat.getParticipants();
	
			synchronized( participants ){
	
				participants.addAll( Arrays.asList( existing_participants ));
			}
	
			table_resort_required = true;
	
			updateTable( false );
	
			BuddyPluginBeta.ChatMessage[] history = chat.getHistory();
	
			logChatMessages( history );
	
			boolean	can_popout = shell == null && public_chat;

			if ( can_popout && !ftux_ok && !auto_ftux_popout_done ){
	
				auto_ftux_popout_done = true;
	
				try{
					createChatWindow( view, plugin, chat.getClone(), true );
	
				}catch( Throwable e ){
	
				}
			}
		}else{
			
			GridLayout layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			parent.setLayout(layout);
			GridData grid_data = new GridData(GridData.FILL_BOTH );
			parent.setLayoutData(grid_data);

			Composite status_area = new Composite( parent, SWT.NULL );
			grid_data = new GridData(GridData.FILL_HORIZONTAL );
			status_area.setLayoutData( grid_data );
			
			layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.marginTop = 4;
			layout.marginLeft = 4;
			status_area.setLayout(layout);
			
			buildStatus( parent, status_area );
			
			buildHelp( status_area );
			
			Composite ftux_parent = new Composite( parent, SWT.NULL );
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			ftux_parent.setLayout(layout );
			
			grid_data = new GridData(GridData.FILL_BOTH );
			grid_data.horizontalSpan = 2;
			ftux_parent.setLayoutData( grid_data);
			
			Composite share_area_holder = buildFTUX( ftux_parent, SWT.NULL );
			
			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			share_area_holder.setLayout(layout );
						
			Canvas share_area = new Canvas( share_area_holder, SWT.NO_BACKGROUND );
			grid_data = new GridData(GridData.FILL_BOTH );
			share_area.setLayoutData( grid_data);
			share_area.setBackground(Colors.white);
			
			share_area.addPaintListener(
				new PaintListener(){
					
					@Override
					public void paintControl(PaintEvent e){
						
						GC gc = e.gc;
						
						gc.setAdvanced(true);
						gc.setAntialias(SWT.ON);
						
						Rectangle bounds = share_area.getBounds();
						
						int	width 	= bounds.width;
						int height	= bounds.height;
						
						gc.setBackground( Colors.white );
						
						gc.fillRectangle( 0, 0, width, height );
						
						Rectangle text_area = new Rectangle(50,  50, width-100, height - 100 );
						
						gc.setLineWidth( 8 );
						gc.setLineStyle( SWT.LINE_DOT );
						
						gc.setForeground( Colors.light_grey );
						
						gc.drawRoundRectangle( 40, 40, width - 80, height - 80, 25, 25 );
						
						gc.setForeground( Colors.dark_grey );

						gc.setFont( big_font );
						
						String msg = 
							MessageText.getString(
								"dchat.share.dnd.info",
								new String[] {
									MessageText.getString(chat.getNetwork()==AENetworkClassifier.AT_PUBLIC?"label.publicly":"label.anonymously"),
									chat.getName()
								});
								
						GCStringPrinter p = new GCStringPrinter(gc, msg, text_area, 0, SWT.CENTER | SWT.WRAP );
						
						p.printString();
					}
				});
			
			input_area = new Text( share_area, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.BORDER);
			input_area.setVisible( false );
			
			hookFTUXListener();
			
			drop_targets = new DropTarget[]{
					new DropTarget(share_area, DND.DROP_COPY),
				};
	
			for ( DropTarget drop_target: drop_targets ){

				drop_target.setTransfer(new Transfer[] {
					FixedURLTransfer.getInstance(),
					FileTransfer.getInstance(),
					TextTransfer.getInstance(),
				});

				drop_target.addDropListener(new DropTargetAdapter() {
					@Override
					public void dropAccept(DropTargetEvent event) {
						event.currentDataType = FixedURLTransfer.pickBestType(event.dataTypes,
								event.currentDataType);
					}

					@Override
					public void dragEnter(DropTargetEvent event) {
					}

					@Override
					public void dragOperationChanged(DropTargetEvent event) {
					}

					@Override
					public void dragOver(DropTargetEvent event) {

						if ((event.operations & DND.DROP_LINK) > 0)
							event.detail = DND.DROP_LINK;
						else if ((event.operations & DND.DROP_COPY) > 0)
							event.detail = DND.DROP_COPY;
						else if ((event.operations & DND.DROP_DEFAULT) > 0)
							event.detail = DND.DROP_COPY;


						event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL | DND.FEEDBACK_EXPAND;
					}

					@Override
					public void dragLeave(DropTargetEvent event) {
					}

					@Override
					public void drop(DropTargetEvent event) {
						
						if ( !chat_available ){
							
							MessageBoxShell mb = new MessageBoxShell(
									MessageText.getString("dchat.share.dnd.wait.title"),
									MessageText.getString("dchat.share.dnd.wait.text" ));

							mb.setButtons(0, new String[] {
									MessageText.getString("Button.ok"),
								}, new Integer[] {
									0,
								});
							
							mb.open( null );
							
							return;
						}
						
						MessageBoxShell mb = new MessageBoxShell(
								MessageText.getString("dchat.share.dnd.prompt.title"),
								MessageText.getString("dchat.share.dnd.prompt.text", new String[] {
									MessageText.getString(chat.getNetwork()==AENetworkClassifier.AT_PUBLIC?"label.publicly":"label.anonymously"),
									chat.getName()	
								}));
						
						mb.setRemember(
								"chat.dnd." + chat.getKey(), false,
								MessageText.getString("MessageBoxWindow.nomoreprompting"));

						mb.setButtons(0, new String[] {
								MessageText.getString("Button.yes"),
								MessageText.getString("Button.no"),
							}, new Integer[] {
								0,
								1
							});
						mb.setRememberOnlyIfButton(0);

						mb.open(new UserPrompterResultListener() {
							@Override
							public void prompterClosed(int result) {	
								if ( result == 0 ) {
									handleDrop( 
										event.data,
										new DropAccepter(){
											
											@Override
											public void accept(String link){
															
												link = link.trim();
												
												sendMessage( link, false );
												
												String rendered = renderMessage( link );

												MessageBoxShell mb = new MessageBoxShell(
														MessageText.getString("dchat.share.dnd.shared.title"),
														MessageText.getString("dchat.share.dnd.shared.text", new String[] { rendered }));

												mb.setButtons(0, new String[] {
														MessageText.getString("Button.ok"),
													}, new Integer[] {
														0,
													});

												mb.open( null );
												
												checkSubscriptions( false );
											}
										});
								}
							}});
						
	
					}
				});
			}
		}
	}
	
	private Composite
	buildFTUX(
		Composite		parent,
		int				style )
	{
		ftux_stack = new Composite(parent, SWT.NONE);
		GridData grid_data = new GridData(GridData.FILL_BOTH );
		grid_data.horizontalSpan = 3;
		ftux_stack.setLayoutData(grid_data);

        final StackLayout stack_layout = new StackLayout();
        ftux_stack.setLayout(stack_layout);

		final Composite log_holder = new Composite(ftux_stack, style );

		final Composite ftux_holder = new Composite(ftux_stack, style );

			// FTUX panel

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		ftux_holder.setLayout(layout);

		ftux_holder.setBackground( ftux_light_bg );

			// top info

		Composite ftux_top_area = new Composite( ftux_holder, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 0;
		ftux_top_area.setLayout(layout);

		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		grid_data.heightHint = 30;
		ftux_top_area.setLayoutData(grid_data);
		ftux_top_area.setBackground( ftux_dark_bg );


		Label ftux_top = new Label( ftux_top_area, SWT.WRAP );
		grid_data = new GridData(SWT.LEFT, SWT.CENTER, true, true );
		grid_data.horizontalIndent = 8;
		ftux_top.setLayoutData(grid_data);

		ftux_top.setAlignment( SWT.LEFT );
		ftux_top.setBackground( ftux_dark_bg );
		ftux_top.setForeground( ftux_dark_fg );
		ftux_top.setFont( big_font );
		ftux_top.setText( MessageText.getString( "azbuddy.dchat.ftux.welcome" ));

			// middle info

		Label ftux_hack = new Label( ftux_holder, SWT.NULL );
		grid_data = new GridData();
		grid_data.heightHint=40;
		grid_data.widthHint=0;
		ftux_hack.setLayoutData(grid_data);

		final StyledText ftux_middle = new StyledText( ftux_holder, SWT.READ_ONLY | SWT.V_SCROLL | SWT.WRAP | SWT.NO_FOCUS );

		grid_data = new GridData(GridData.FILL_BOTH );
		grid_data.horizontalSpan = 1;
		grid_data.verticalIndent = 4;
		grid_data.horizontalIndent = 16;
		ftux_middle.setLayoutData(grid_data);

		ftux_middle.setBackground( ftux_light_bg );

		String info1_text =
		"Chat allows you to communicate with other users directly by sending and receiving messages.\n" +
		"It is a decentralized chat system - there are no central servers involved, all messages are passed directly between users.\n" +
		"Consequently " + Constants.APP_NAME + " has absolutely no control over message content. In particular no mechanism exists (nor is possible) for " + Constants.APP_NAME + " to moderate or otherwise control either messages or the users that send messages.";

		String info2_text =
		"I UNDERSTAND AND AGREE that " + Constants.APP_NAME + " has no responsibility whatsoever with my enabling this function and using chat.";

		String[] info_lines = info1_text.split( "\n" );

		for ( String line: info_lines ){

			ftux_middle.append( line );

			if ( line != info_lines[info_lines.length-1] ){

				ftux_middle.append( "\n" );

				int	pos = ftux_middle.getText().length();

					// zero width space in large font to get smaller paragraph spacing

				ftux_middle.append( "\u200B" );

				StyleRange styleRange = new StyleRange();
				styleRange.start = pos;
				styleRange.length = 1;
				styleRange.font = big_font;

				ftux_middle.setStyleRange( styleRange );
			}
		}

			// checkbox area

		Composite ftux_check_area = new Composite( ftux_holder, SWT.NULL );
		layout = new GridLayout();
		layout.marginLeft = 0;
		layout.marginWidth = 0;
		layout.numColumns = 2;
		ftux_check_area.setLayout(layout);

		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		ftux_check_area.setLayoutData(grid_data);
		ftux_check_area.setBackground(  ftux_light_bg );

		final Button ftux_check = new Button( ftux_check_area, SWT.CHECK );
		grid_data = new GridData();
		grid_data.horizontalIndent = 16;
		ftux_check.setLayoutData(grid_data);
		ftux_check.setBackground(  ftux_light_bg );

		Label ftux_check_test = new Label( ftux_check_area, SWT.WRAP );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		ftux_check_test.setLayoutData(grid_data);

		ftux_check_test.setBackground( ftux_light_bg );
		ftux_check_test.setText( info2_text );


			// bottom info

		final StyledText ftux_bottom = new StyledText( ftux_holder, SWT.READ_ONLY | SWT.WRAP | SWT.NO_FOCUS );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		grid_data.horizontalIndent = 16;
		ftux_bottom.setLayoutData(grid_data);

		ftux_bottom.setBackground( ftux_light_bg );
		ftux_bottom.setFont( bold_font );
		ftux_bottom.setText( MessageText.getString( "azbuddy.dchat.ftux.footer" ) + " " );

		{
			int	start	= ftux_bottom.getText().length();

			String url = Wiki.FAQ_LEGAL;
			String url_text	= MessageText.getString( "label.more.dot" );

			ftux_bottom.append( url_text );

			StyleRange styleRange = new StyleRange();
			styleRange.start = start;
			styleRange.length = url_text.length();
			styleRange.foreground = parent.getDisplay().getSystemColor(SWT.COLOR_LINK_FOREGROUND);
			styleRange.underline = true;

			styleRange.data = url;

			ftux_bottom.setStyleRange( styleRange );
		}

		ftux_bottom.addListener(
				SWT.MouseUp,
				new Listener()
				{
					@Override
					public void handleEvent(Event event) {
						int offset = ftux_bottom.getOffsetAtLocation(new Point (event.x, event.y));
						StyleRange style = ftux_bottom.getStyleRangeAtOffset(offset);

						if ( style != null ){

							String url = (String)style.data;

							try{
								Utils.launch( new URL( url ));

							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					}
				});

		Label ftux_line = new Label( ftux_holder, SWT.SEPARATOR | SWT.HORIZONTAL );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		grid_data.verticalIndent = 4;
		ftux_line.setLayoutData(grid_data);

		Composite ftux_button_area = new Composite( ftux_holder, SWT.NULL );
		layout = new GridLayout();
		layout.numColumns = 2;
		ftux_button_area.setLayout(layout);

		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		ftux_button_area.setLayoutData(grid_data);
		if ( !Utils.isDarkAppearanceNative()){
			ftux_button_area.setBackground( Colors.white );	
		}

		Label filler = new Label( ftux_button_area, SWT.NULL );
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		filler.setLayoutData(grid_data);
		if ( !Utils.isDarkAppearanceNative()){
			filler.setBackground( Colors.white );
		
		}
		final Button ftux_accept = new Button( ftux_button_area, SWT.PUSH );
		grid_data = new GridData();
		grid_data.horizontalAlignment = SWT.RIGHT;
		grid_data.widthHint = 60;
		ftux_accept.setLayoutData(grid_data);

		ftux_accept.setText( MessageText.getString( "label.accept" ));

		ftux_accept.setEnabled( false );

		ftux_accept.addSelectionListener(
			new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					beta.setFTUXAccepted( true );
				}
			});

		ftux_check.addSelectionListener(
			new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					ftux_accept.setEnabled( ftux_check.getSelection());
				}
		});
		

		return( log_holder );
	}
	
	private void
	hookFTUXListener()
	{
		final boolean was_ok = ftux_ok = beta.getFTUXAccepted();

		final boolean[] ftux_init_done = { false };

		beta.addFTUXStateChangeListener(
			new FTUXStateChangeListener()
			{
				@Override
				public void
				stateChanged(
					final boolean		_ftux_ok )
				{
					if ( ftux_stack.isDisposed()){

						beta.removeFTUXStateChangeListener( this );

					}else{

						Utils.execSWTThread(
							new Runnable()
							{

								@Override
								public void
								run()
								{
									ftux_ok = _ftux_ok;

									Control[] kids = ftux_stack.getChildren();
									
									((StackLayout)ftux_stack.getLayout()).topControl = ftux_ok?kids[0]:kids[1];

									if ( ftux_init_done[0]){

										ftux_stack.layout( true, true );
									}

									setInputAvailability( false );

									table_resort_required = true;

									updateTable( false );
									
									if ( ftux_ok && !was_ok ){
										
										checkSubscriptions( true );
									}
								}
							});
					}
				}
			});
		
		ftux_init_done[0] = true;
	}
	
	private Composite
	buildHelp(
		Composite		rhs )
	{
		boolean public_chat 	= !chat.isPrivateChat();
		boolean sharing_view	= chat.getViewType() == BuddyPluginBeta.VIEW_TYPE_SHARING;
		boolean	can_popout 		= shell == null && public_chat;

		Composite top_right = new Composite(rhs, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginHeight = 0;
		layout.marginWidth = 0;

		top_right.setLayout(layout);
		
		if ( !sharing_view ) {
			GridData grid_data = new GridData( GridData.FILL_HORIZONTAL );
			//grid_data.heightHint = 50;
			top_right.setLayoutData(grid_data);

			Label label = new Label( top_right, SWT.NULL );
			grid_data = new GridData( GridData.FILL_HORIZONTAL );
			grid_data.horizontalSpan=can_popout?1:2;
			label.setLayoutData(grid_data);
		}

		LinkLabel link = new LinkLabel( top_right, "label.help", Wiki.DECENTRALIZED_CHAT);
		//grid_data.horizontalAlignment = SWT.END;
		//link.getlabel().setLayoutData( grid_data );

		if ( sharing_view ){
			
			buildRSSButton( top_right );
		}
		
		if ( can_popout ){

			Label pop_out = new Label( top_right, SWT.NULL );
			Image image = ImageLoader.getInstance().getImage( "popout_window" );
			pop_out.setImage( image );
			GridData grid_data = new GridData();
			grid_data.widthHint=image.getBounds().width;
			grid_data.heightHint=image.getBounds().height;
			pop_out.setLayoutData(grid_data);

			pop_out.setCursor(pop_out.getDisplay().getSystemCursor(SWT.CURSOR_HAND));

			Utils.setTT(pop_out, MessageText.getString( "label.pop.out" ));

			pop_out.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent arg0) {
					try{
						createChatWindow( view, plugin, chat.getClone(), true );

					}catch( Throwable e ){

						Debug.out( e);
					}
				}
			});

		}
		
		return( top_right );
	}
	
	private void
	buildRSSButton(
		Composite	parent )
	{
		boolean sharing_view	= chat.getViewType() == BuddyPluginBeta.VIEW_TYPE_SHARING;

		Runnable create_it = 
			new Runnable()
			{
				public void
				run()
				{
					try{
						String url = encodeRSSURL( chat );

						SubscriptionManager sm = PluginInitializer.getDefaultInterface().getUtilities().getSubscriptionManager();

						Map<String,Object>	options = new HashMap<>();

						if ( chat.isAnonymous()){

							options.put( SubscriptionManager.SO_ANONYMOUS, true );
						}

						options.put( SubscriptionManager.SO_NAME, chat.getName());
						
						options.put( SubscriptionManager.SO_FREQUENCY, 10 );
						
						sm.requestSubscription( new URL( url ), options );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			};
		
		if ( sharing_view ) {
			
			Label rss_button = new Label(parent, SWT.NONE);
			final Image rss_image_normal 	= ImageLoader.getInstance().getImage("image.sidebar.subscriptions");
			final Image rss_image_gray		= ImageLoader.getInstance().getImage("image.sidebar.subscriptions-gray");
			rss_button.setImage(rss_image_gray);
			rss_button.setCursor(parent.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
			GridData grid_data = new GridData(GridData.FILL_HORIZONTAL );
			grid_data.widthHint = rss_image_gray.getBounds().width;
			grid_data.heightHint = rss_image_gray.getBounds().height;
			rss_button.setLayoutData(grid_data);
	
			Utils.setTT(rss_button, MessageText.getString( "azbuddy.dchat.rss.subscribe.info"));
	
			rss_button.addMouseTrackListener(
				new MouseTrackAdapter(){
				
					@Override
					public void mouseExit(MouseEvent arg0){
						rss_button.setImage(rss_image_gray);
						rss_button.redraw();
					}
					
					@Override
					public void mouseEnter(MouseEvent arg0){
						rss_button.setImage(rss_image_normal);
						rss_button.redraw();
					}
				});
				
			rss_button.addMouseListener(
				new MouseAdapter(){
	
					@Override
					public void mouseUp(MouseEvent e){
						create_it.run();
					}
				});
		}else{
			
			Button rss_button = new Button(parent, SWT.PUSH);
			Image rss_image = ImageLoader.getInstance().getImage("image.sidebar.subscriptions");
			rss_button.setImage(rss_image);
			GridData grid_data = new GridData(GridData.FILL_HORIZONTAL );
			grid_data.widthHint = rss_image.getBounds().width+ (Constants.isLinux?20:0);
			grid_data.heightHint = rss_image.getBounds().height;
			rss_button.setLayoutData(grid_data);
			//rss_button.setEnabled(false);
	
			Utils.setTT(rss_button, MessageText.getString( "azbuddy.dchat.rss.subscribe.info"));
	
			rss_button.addSelectionListener(
				new SelectionAdapter(){
	
					@Override
					public void
					widgetSelected(
						SelectionEvent ev )
					{
						create_it.run();
					}
				});
		}
	}
	
	private void
	buildStatus(
		Composite		main_component,
		Composite		component )
	{
		boolean public_chat = !chat.isPrivateChat();
		
		final Label menu_drop = new Label( component, SWT.NULL );
		
		Messages.setLanguageTooltip( menu_drop, "TagSettingsView.title" );
		
		FontData fontData = menu_drop.getFont().getFontData()[0];

		Display display = menu_drop.getDisplay();

		italic_font = new Font( display, new FontData( fontData.getName(), fontData.getHeight(), SWT.ITALIC ));
		bold_font 	= new Font( display, new FontData( fontData.getName(), fontData.getHeight(), SWT.BOLD ));
		big_font 	= new Font( display, new FontData( fontData.getName(), (int)(fontData.getHeight()*1.5), SWT.BOLD ));
		small_font 	= new Font( display, new FontData( fontData.getName(), (int)(fontData.getHeight()*0.5), SWT.BOLD ));

		boolean dark = Utils.isDarkAppearanceNative();
		
		if ( dark ){
			Color bg = Colors.getSystemColor(display, SWT.COLOR_WIDGET_BACKGROUND);
			Color fg = Colors.getSystemColor(display, SWT.COLOR_WIDGET_FOREGROUND);

			ftux_dark_bg = new Color( display, bg.getRGB());
			ftux_dark_fg = new Color( display, fg.getRGB());
			ftux_light_bg = new Color( display, bg.getRGB());
		}else{
	
			ftux_dark_bg 	= new Color( display, 183, 200, 212 );
			ftux_dark_fg 	= new Color( display, 0, 81, 134 );
			ftux_light_bg 	= new Color( display, 236, 242, 246 );
		}
		
		component.addDisposeListener(
			new DisposeListener()
			{
				@Override
				public void
				widgetDisposed(
					DisposeEvent arg0 )
				{
					Font[] fonts = { italic_font, bold_font, big_font, small_font };

					for ( Font f: fonts ){

						if ( f != null ){

							f.dispose();
						}
					}

					Color[] colours = { ftux_dark_bg, ftux_dark_fg, ftux_light_bg };

					for ( Color c: colours ){

						if ( c != null ){

							c.dispose();
						}
					}

					if ( drop_targets != null ){

						for ( DropTarget dt: drop_targets ){

							dt.dispose();
						}
					}

					closed();
				}
			});
		
		status = new BufferedLabel( component, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		GridData grid_data = new GridData(GridData.FILL_HORIZONTAL);

		status.setLayoutData(grid_data);
		status.setText( MessageText.getString( "PeersView.state.pending" ));

		BubbleTextBox bubbleTextBox = new BubbleTextBox(component, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.SINGLE);

		Text bubbleTextWidget = bubbleTextBox.getTextWidget();
		
		GridData gridData = new GridData();
		gridData.widthHint = 100;

		bubbleTextBox.setMessageAndLayout( MessageText.getString("Button.search") + "...", gridData );
		
		bubbleTextWidget.addModifyListener(
			(e)->{
				
				String text = bubbleTextWidget.getText();
				
				search( text.trim());
			});
		
		bubbleTextWidget.addKeyListener(
				new KeyAdapter()
				{
					public void 
					keyPressed(KeyEvent e)
					{
						if ( e.character == '\r' ){
						
							search( "\n" );
							
						}else if ( e.keyCode == SWT.ESC ){
							
							bubbleTextWidget.setText( "" );
						}
					}
				});
		
		Image image = ImageLoader.getInstance().getImage( "cog_down" );
		menu_drop.setImage( image );
		grid_data = new GridData();
		grid_data.widthHint=image.getBounds().width;
		grid_data.heightHint=image.getBounds().height;
		menu_drop.setLayoutData(grid_data);

		menu_drop.setCursor(menu_drop.getDisplay().getSystemCursor(SWT.CURSOR_HAND));

		Control status_control = status.getControl();

		final Menu status_menu = new Menu( status_control );

		status.getControl().setMenu( status_menu );
		menu_drop.setMenu( status_menu );

		menu_drop.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent event) {
				try{
					Point p = status_menu.getDisplay().map( menu_drop, null, event.x, event.y );

					status_menu.setLocation( p );

					status_menu.setVisible(true);

				}catch( Throwable e ){

					Debug.out( e);
				}
			}
		});

		if ( public_chat ){

			Menu status_clip_menu = new Menu(component.getShell(), SWT.DROP_DOWN);
			MenuItem status_clip_item = new MenuItem( status_menu, SWT.CASCADE);
			status_clip_item.setMenu(status_clip_menu);
			status_clip_item.setText(  MessageText.getString( "label.copy.to.clipboard" ));

			MenuItem status_mi = new MenuItem( status_clip_menu, SWT.PUSH );
			status_mi.setText( MessageText.getString( "azbuddy.dchat.copy.channel.key" ));

			status_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							ClipboardCopy.copyToClipBoard( chat.getKey());
						}
					});

			status_mi = new MenuItem( status_clip_menu, SWT.PUSH );
			status_mi.setText( MessageText.getString( "azbuddy.dchat.copy.channel.url" ));

			status_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							ClipboardCopy.copyToClipBoard( chat.getURL());
						}
					});

			status_mi = new MenuItem( status_clip_menu, SWT.PUSH );
			status_mi.setText( MessageText.getString( "azbuddy.dchat.copy.rss.url" ));

			status_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							ClipboardCopy.copyToClipBoard( "azplug:?id=azbuddy&arg=" + UrlUtils.encode( chat.getURL() + "&format=rss" ));
						}
					});

			status_mi = new MenuItem( status_clip_menu, SWT.PUSH );
			status_mi.setText( MessageText.getString( "azbuddy.dchat.copy.channel.pk" ));

			status_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							ClipboardCopy.copyToClipBoard( Base32.encode( chat.getPublicKey()));
						}
					});

			status_mi = new MenuItem( status_clip_menu, SWT.PUSH );
			status_mi.setText( MessageText.getString( "azbuddy.dchat.copy.channel.export" ));

			status_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							ClipboardCopy.copyToClipBoard( chat.export());
						}
					});

			if ( !chat.isManaged()){

				Menu status_channel_menu = new Menu(component.getShell(), SWT.DROP_DOWN);
				MenuItem status_channel_item = new MenuItem( status_menu, SWT.CASCADE);
				status_channel_item.setMenu(status_channel_menu);
				status_channel_item.setText(  MessageText.getString( "azbuddy.dchat.rchans" ));

					// Managed channel

				status_mi = new MenuItem( status_channel_menu, SWT.PUSH );
				status_mi.setText( MessageText.getString( "azbuddy.dchat.rchans.managed" ));

				status_mi.addSelectionListener(
						new SelectionAdapter() {
							@Override
							public void
							widgetSelected(
								SelectionEvent event )
							{
								try{
									ChatInstance inst = chat.getManagedChannel();

									BuddyPluginViewBetaChat.createChatWindow( view, plugin, inst );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						});

					// RO channel

				status_mi = new MenuItem( status_channel_menu, SWT.PUSH );
				status_mi.setText( MessageText.getString( "azbuddy.dchat.rchans.ro" ));

				status_mi.addSelectionListener(
						new SelectionAdapter() {
							@Override
							public void
							widgetSelected(
								SelectionEvent event )
							{
								try{
									ChatInstance inst = chat.getReadOnlyChannel();

									createChatWindow( view, plugin, inst );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						});

					// Random sub-channel

				status_mi = new MenuItem( status_channel_menu, SWT.PUSH );
				status_mi.setText( MessageText.getString( "azbuddy.dchat.rchans.rand" ));

				status_mi.addSelectionListener(
						new SelectionAdapter() {
							@Override
							public void
							widgetSelected(
								SelectionEvent event )
							{
								try{
									byte[]	rand = new byte[20];

									RandomUtils.nextSecureBytes( rand );

									ChatInstance inst = beta.getChat( chat.getNetwork(), chat.getKey() + " {" + Base32.encode( rand ) + "}" );

									createChatWindow( view, plugin, inst );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						});
				if ( beta.isI2PAvailable()){

					status_mi = new MenuItem( status_channel_menu, SWT.PUSH );
					status_mi.setText( MessageText.getString(  chat.getNetwork()==AENetworkClassifier.AT_I2P?"azbuddy.dchat.rchans.pub":"azbuddy.dchat.rchans.anon" ));

					status_mi.addSelectionListener(
							new SelectionAdapter() {
								@Override
								public void
								widgetSelected(
									SelectionEvent event )
								{
									try{
										ChatInstance inst = beta.getChat( chat.getNetwork()==AENetworkClassifier.AT_I2P?AENetworkClassifier.AT_PUBLIC:AENetworkClassifier.AT_I2P, chat.getKey());

										createChatWindow( view, plugin, inst );

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							});
				}
			}

			final MenuItem fave_mi = new MenuItem( status_menu, SWT.CHECK );
			fave_mi.setText( MessageText.getString( "label.fave" ));

			fave_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							chat.setFavourite( fave_mi.getSelection());
						}
					});

			final MenuItem sis_mi = new MenuItem( status_menu, SWT.PUSH );
			sis_mi.setText( MessageText.getString( Utils.isAZ2UI()?"label.show.in.tab":"label.show.in.sidebar" ));

			sis_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							BuddyPluginUI.openChat( chat.getNetwork(), chat.getKey());
						}
					});

			addFriendsMenu( status_menu );

				// advanced

			final Menu advanced_menu = new Menu(status_menu.getShell(), SWT.DROP_DOWN);
			MenuItem advanced_menu_item = new MenuItem( status_menu, SWT.CASCADE);
			advanced_menu_item.setMenu(advanced_menu);
			advanced_menu_item.setText(  MessageText.getString( "MyTorrentsView.menu.advancedmenu" ));

				// rename
			
			final MenuItem rename_mi = new MenuItem( advanced_menu, SWT.PUSH );
			rename_mi.setText( MessageText.getString( "MyTorrentsView.menu.rename" ));

			rename_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							String name = chat.getDisplayName();
							
							if ( name == null ) {
								name = chat.getName();
							}
							
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

										try{
											chat.setDisplayName( input );

										}catch( Throwable e ){

											Debug.printStackTrace(e);
										}
									}
								}
							});
						}
					});
			
				// view type
			
			final Menu vt_menu = new Menu(status_menu.getShell(), SWT.DROP_DOWN);
			
			MenuItem vt_menu_item = new MenuItem( advanced_menu, SWT.CASCADE);
			
			vt_menu_item.setMenu(vt_menu);
			
			vt_menu_item.setText(  MessageText.getString( "menu.view.options" ));

			SelectionAdapter listener =
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent e )
					{
						int	vt = -1;
						
						for ( MenuItem mi: vt_menu.getItems()){

							if ( mi.getSelection()) {
								
								vt  = (Integer)mi.getData();
							}
						}
						
						if ( vt != -1 ) {
							
							chat.setViewType( vt );
							
							buildSupport( main_component );
							
							main_component.layout( true, true );
						}
					}
				};

			MenuItem vt_mi = new MenuItem( vt_menu, SWT.RADIO );
			vt_mi.setText( MessageText.getString( "label.full" ));
			vt_mi.setData( BuddyPluginBeta.VIEW_TYPE_DEFAULT );

			vt_mi.addSelectionListener( listener );

			vt_mi = new MenuItem( vt_menu, SWT.RADIO );
			vt_mi.setText( MessageText.getString( "ConfigView.section.sharing" ));
			vt_mi.setData( BuddyPluginBeta.VIEW_TYPE_SHARING );

			if ( chat.isReadOnly()){
				
				vt_mi.setEnabled( false );
			}
			
			vt_mi.addSelectionListener( listener );


			vt_menu.addMenuListener(
				new MenuAdapter()
				{
					@Override
					public void
					menuShown(
						MenuEvent e )
					{
						int vt = chat.getViewType();
						
						for ( MenuItem mi: vt_menu.getItems()){

							mi.setSelection( vt == (Integer)mi.getData());
						}
					}
				});
			
			
				// persist messages
			
			final MenuItem persist_mi = new MenuItem( advanced_menu, SWT.CHECK );
			persist_mi.setText( MessageText.getString( "azbuddy.dchat.save.messages" ));

			persist_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							chat.setSaveMessages( persist_mi.getSelection());
						}
					});

			final MenuItem log_mi = new MenuItem( advanced_menu, SWT.CHECK );
			log_mi.setText( MessageText.getString( "azbuddy.dchat.log.messages" ));

			log_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							chat.setLogMessages( log_mi.getSelection());
						}
					});

			final MenuItem automute_mi = new MenuItem( advanced_menu, SWT.CHECK );
			automute_mi.setText( MessageText.getString( "azbuddy.dchat.auto.mute" ));

			automute_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							chat.setAutoMute( automute_mi.getSelection());
						}
					});

			final MenuItem postnotifications_mi = new MenuItem( advanced_menu, SWT.CHECK );
			postnotifications_mi.setText( MessageText.getString( "azbuddy.dchat.post.to.notifcations" ));

			postnotifications_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							chat.setEnableNotificationsPost( postnotifications_mi.getSelection());
						}
					});

			final MenuItem disableindicators_mi = new MenuItem( advanced_menu, SWT.CHECK );
			disableindicators_mi.setText( MessageText.getString( "azbuddy.dchat.disable.msg.indicators" ));

			disableindicators_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							chat.setDisableNewMsgIndications( disableindicators_mi.getSelection());
						}
					});

				// setup menu

			status_menu.addMenuListener(
					new MenuAdapter()
					{
						@Override
						public void
						menuShown(
							MenuEvent e )
						{
							fave_mi.setSelection( chat.isFavourite());
							persist_mi.setSelection( chat.getSaveMessages());
							log_mi.setSelection( chat.getLogMessages());
							automute_mi.setSelection( chat.getAutoMute());
							postnotifications_mi.setSelection( chat.getEnableNotificationsPost());
							boolean disable_indications = chat.getDisableNewMsgIndications();
							disableindicators_mi.setSelection( disable_indications );
							postnotifications_mi.setEnabled( !disable_indications );
						}
					});
		}else{

			final Menu status_priv_menu = new Menu(component.getShell(), SWT.DROP_DOWN);
			MenuItem status_priv_item = new MenuItem( status_menu, SWT.CASCADE);
			status_priv_item.setMenu(status_priv_menu);
			status_priv_item.setText(  MessageText.getString( "label.private.chat" ));

			SelectionAdapter listener =
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent e )
					{
						beta.setPrivateChatState((Integer)((MenuItem)e.widget).getData());
					}
				};

			MenuItem status_mi = new MenuItem( status_priv_menu, SWT.RADIO );
			status_mi.setText( MessageText.getString( "label.enabled" ));
			status_mi.setData( BuddyPluginBeta.PRIVATE_CHAT_ENABLED );

			status_mi.addSelectionListener( listener );

			status_mi = new MenuItem( status_priv_menu, SWT.RADIO );
			status_mi.setText( MessageText.getString( "label.pinned.only" ));
			status_mi.setData( BuddyPluginBeta.PRIVATE_CHAT_PINNED_ONLY );

			status_mi.addSelectionListener( listener );

			status_mi = new MenuItem( status_priv_menu, SWT.RADIO );
			status_mi.setText( MessageText.getString( "label.disabled" ));
			status_mi.setData( BuddyPluginBeta.PRIVATE_CHAT_DISABLED );

			status_mi.addSelectionListener( listener );


			status_priv_menu.addMenuListener(
				new MenuAdapter()
				{
					@Override
					public void
					menuShown(
						MenuEvent e )
					{
						int pc_state = beta.getPrivateChatState();

						for ( MenuItem mi: status_priv_menu.getItems()){

							mi.setSelection( pc_state == (Integer)mi.getData());
						}
					}
				});

			addFriendsMenu( status_menu );

			final MenuItem keep_alive_mi = new MenuItem( status_menu, SWT.CHECK );
			keep_alive_mi.setText( MessageText.getString( "label.keep.alive" ));

			status_menu.addMenuListener(
				new MenuAdapter()
				{
					@Override
					public void
					menuShown(
						MenuEvent e )
					{
						keep_alive_mi.setSelection( chat.getUserData( "AC:KeepAlive" ) != null );
					}
				});

			keep_alive_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							ChatInstance clone = (ChatInstance)chat.getUserData( "AC:KeepAlive" );

							if ( clone != null ){

								clone.destroy();

								clone = null;

							}else{

								try{
									clone = chat.getClone();

								}catch( Throwable f ){

								}
							}

							chat.setUserData( "AC:KeepAlive", clone );
						}
					});

			final MenuItem sis_mi = new MenuItem( status_menu, SWT.PUSH );
			sis_mi.setText( MessageText.getString( Utils.isAZ2UI()?"label.show.in.tab":"label.show.in.sidebar" ));

			sis_mi.addSelectionListener(
					new SelectionAdapter() {
						@Override
						public void
						widgetSelected(
							SelectionEvent e )
						{
							BuddyPluginUI.openChat( chat.getNetwork(), chat.getKey());
						}
					});
		}
	}
	
	private String	current_search = "";
	private int		current_search_index;
	
	private void
	search(
		String	text )
	{
		if ( text.isEmpty()){
			
			current_search			= "";
			current_search_index 	= 0;
			
			log.setSelection( log.getText().length());
			
		}else{
			if ( text.equals( "\n" )){
		
				if ( !current_search.isEmpty()){
					
					current_search_index++;
				}
			}else{
		
				current_search			= text.toLowerCase();
				current_search_index 	= 0;
			}
			
			String str = log.getText().toLowerCase();
			
			int pos = str.indexOf( current_search );
			
			if ( pos == -1 ){
				
				log.setSelection( log.getText().length());
				
			}else{
				for ( int i=1;i<=current_search_index;i++){
					
					int next_pos = str.indexOf( current_search, pos+1 );
					
					if ( next_pos == -1 ){
						
						break;
						
					}else{
						
						pos = next_pos;
					}
				}
				
				log.setSelection( pos, pos+ current_search.length());
			}
		}
	}
	
	private void
	addFriendsMenu(
		Menu		menu )
	{
		final Menu friends_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
		MenuItem friends_menu_item = new MenuItem( menu, SWT.CASCADE);
		friends_menu_item.setMenu(friends_menu);
		friends_menu_item.setText(  MessageText.getString( "Views.plugins.azbuddy.title" ));

		friends_menu.addMenuListener(
			new MenuAdapter()
			{
				@Override
				public void
				menuShown(
					MenuEvent e )
				{
					Utils.clearMenu( friends_menu );

					boolean	enabled = plugin.isClassicEnabled();

					if ( enabled ){

						MenuItem mi = new MenuItem( friends_menu, SWT.PUSH );
						mi.setText( MessageText.getString( "azbuddy.insert.friend.key" ));

						mi.addSelectionListener(
								new SelectionAdapter() {
									@Override
									public void
									widgetSelected(
										SelectionEvent event )
									{
										String uri = getFriendURI( !chat.isAnonymous());

										input_area.append( uri  );
									}
								});

						mi.setEnabled( !chat.isReadOnly());
						
						new MenuItem(friends_menu, SWT.SEPARATOR );

						mi = new MenuItem( friends_menu, SWT.PUSH );
						mi.setText( MessageText.getString( "azbuddy.view.friends" ));

						mi.addSelectionListener(
								new SelectionAdapter() {
									@Override
									public void
									widgetSelected(
										SelectionEvent event )
									{
										view.selectClassicTab();
									}
								});

					}else{

						MenuItem mi = new MenuItem( friends_menu, SWT.PUSH );
						mi.setText( MessageText.getString( "label.enable" ));

						mi.addSelectionListener(
								new SelectionAdapter() {
									@Override
									public void
									widgetSelected(
										SelectionEvent event )
									{
										plugin.setClassicEnabled( true, false );
									}
								});

					}
				}
			});
	}
	
	private String
	getFriendURI(
		boolean	is_pub )
	{
		String key = plugin.getPublicKey( is_pub );

		String uri = (is_pub?"chat":"chat:anon" ) + ":friend:?key=" + key;
		
		/* doesn't look great and there's some bug that sometimes doesn't render it as a link so rather confusing...
		 
		String my_nick = chat.getNickname( false );

		if ( my_nick.length() > 0 ){

			uri += "[[" + UrlUtils.encode( "Friend Key for " + my_nick ) + "]]";
		}
		*/
		return( uri );
	}

	private void
	buildParticipantMenu(
		final Menu					menu,
		final List<ChatParticipant>	participants )
	{
		boolean	can_ignore 	= false;
		boolean	can_listen	= false;
		boolean	can_pin		= false;
		boolean	can_unpin	= false;

		boolean	can_spam	= false;
		boolean	can_unspam	= false;

		for ( ChatParticipant participant: participants ){

			if ( DEBUG_ENABLED ){

				System.out.println( participant.getName() + "/" + participant.getAddress() + "/" + participant.getZoneOffset() + " - pk=" + Base32.encode( participant.getPublicKey()));

				List<ChatMessage>	messages = participant.getMessages();

				for ( ChatMessage msg: messages ){

					System.out.println( "    " + msg.getTimeStamp() + ", " + msg.getAddress() + "/" + msg.getZoneOffset() + " - " + msg.getMessage());
				}
			}

			if ( participant.isIgnored()){

				can_listen = true;

			}else{

				can_ignore = true;
			}

			if ( participant.isPinned()){

				can_unpin = true;

			}else{

				if ( !participant.isMe()){

					can_pin = true;
				}
			}

			if ( participant.isSpammer()){

				can_unspam = true;

			}else{

				can_spam |= participant.canSpammer();
			}
		}

		final MenuItem ignore_item = new MenuItem(menu, SWT.PUSH);

		ignore_item.setText( lu.getLocalisedMessageText( "label.mute" ) );

		ignore_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e)
				{
					boolean	changed = false;

					for ( ChatParticipant participant: participants ){

						if ( !participant.isIgnored()){

							participant.setIgnored( true );

							changed = true;
						}
					}

					if ( changed ){

						messagesChanged();
						
						participantsChanged();
					}
				}
			});

		ignore_item.setEnabled( can_ignore );

		final MenuItem listen_item = new MenuItem(menu, SWT.PUSH);

		listen_item.setText(lu.getLocalisedMessageText( "label.listen" ) );

		listen_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e)
				{
					boolean	changed = false;

					for ( ChatParticipant participant: participants ){

						if ( participant.isIgnored()){

							participant.setIgnored( false );

							changed = true;
						}
					}

					if ( changed ){

						messagesChanged();
						
						participantsChanged();
					}
				}
			});

		listen_item.setEnabled( can_listen );

			// spam

		final MenuItem spam_item = new MenuItem(menu, SWT.PUSH);

		spam_item.setText(lu.getLocalisedMessageText( "label.spam" ) );

		spam_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e)
				{
					boolean	changed = false;

					for ( ChatParticipant participant: participants ){

						if ( participant.canSpammer()){

							participant.setSpammer( true );

							changed = true;
						}
					}

					if ( changed ){

						messagesChanged();
						
						participantsChanged();
					}
				}
			});

		spam_item.setEnabled( can_spam );

		final MenuItem unspam_item = new MenuItem(menu, SWT.PUSH);

		unspam_item.setText(lu.getLocalisedMessageText( "label.not.spam" ) );

		unspam_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e)
				{
					boolean	changed = false;

					for ( ChatParticipant participant: participants ){

						if ( participant.isSpammer()){

							participant.setSpammer( false );

							changed = true;
						}
					}

					if ( changed ){

						messagesChanged();
						
						participantsChanged();
					}
				}
			});

		unspam_item.setEnabled( can_unspam );

			// pin

		new MenuItem(menu, SWT.SEPARATOR );

		final MenuItem pin_item = new MenuItem(menu, SWT.PUSH);

		pin_item.setText( lu.getLocalisedMessageText( "label.pin" ) );

		pin_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e)
				{
					boolean	changed = false;
					
					for ( ChatParticipant participant: participants ){

						if ( !participant.isPinned()){

							if ( !participant.isMe()){

								participant.setPinned( true );

								setProperties( participant );
								
								changed = true;
							}
						}
					}
					

					if ( changed ){

						messagesChanged();
					}
				}
			});

		pin_item.setEnabled( can_pin );

		final MenuItem unpin_item = new MenuItem(menu, SWT.PUSH);

		unpin_item.setText( lu.getLocalisedMessageText( "label.unpin" ) );

		unpin_item.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected(
					SelectionEvent e)
				{
					boolean	changed = false;
					
					for ( ChatParticipant participant: participants ){

						if ( participant.isPinned()){

							participant.setPinned( false );

							setProperties( participant );
							
							changed = true;
						}
					}
					

					if ( changed ){

						messagesChanged();
					}
				}
			});

		unpin_item.setEnabled( can_unpin );

		if ( !chat.isPrivateChat()){

			new MenuItem(menu, SWT.SEPARATOR );

			final MenuItem private_chat_item = new MenuItem(menu, SWT.PUSH);

			private_chat_item.setText( lu.getLocalisedMessageText( "label.private.chat" ) );

			final byte[]	chat_pk = chat.getPublicKey();

			private_chat_item.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected(
						SelectionEvent e)
					{
						for ( ChatParticipant participant: participants ){

							if ( TEST_LOOPBACK_CHAT || !Arrays.equals( participant.getPublicKey(), chat_pk )){

								try{
									ChatInstance chat = participant.createPrivateChat();

									createChatWindow( view, plugin, chat);

								}catch( Throwable f ){

									Debug.out( f );
								}
							}
						}
					}
				});

				// friends sub menu
			{
				boolean is_public_chat = !chat.isAnonymous();
				
				Menu friends_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);
				MenuItem friends_item = new MenuItem( menu, SWT.CASCADE);
				friends_item.setMenu(friends_menu);
				friends_item.setText(  MessageText.getString( "Views.plugins.azbuddy.title" ));

				final MenuItem send_fk_item = new MenuItem(friends_menu, SWT.PUSH);
	
				send_fk_item.setText( lu.getLocalisedMessageText( "label.send.friend.key" ) );
	
				send_fk_item.addSelectionListener(
					new SelectionAdapter()
					{
						@Override
						public void
						widgetSelected(
							SelectionEvent e)
						{
							if ( !plugin.isClassicEnabled()){
								
								plugin.setClassicEnabled( true, false );
							}
							
							for ( ChatParticipant participant: participants ){
	
								boolean is_me = Arrays.equals( participant.getPublicKey(), chat_pk );
								
								boolean is_friend = participant.getFriendStatus() != 0;
								
								if ( ! (is_friend || is_me )){
	
									try{
										ChatInstance chat = participant.createPrivateChat();
	
										createChatWindow( view, plugin, chat);
	
										String message = "!azbuddy.send.friend.key.msg[" + UrlUtils.encode( getFriendURI( is_public_chat )) + "]!";
																			
										chat.sendMessage(message, null);
										
									}catch( Throwable f ){
	
										Debug.out( f );
									}
								}
							}
						}
					});
	
				if ( participants.size() == 1 ){
					
					ChatParticipant participant = participants.get(0);
					
					boolean is_me = Arrays.equals( participant.getPublicKey(), chat_pk );
					
					String fk = participant.getFriendKey();
					
					boolean is_friend = participant.getFriendStatus() != 0;

					final MenuItem add_fk_item = new MenuItem(friends_menu, SWT.PUSH);
		
					add_fk_item.setText( lu.getLocalisedMessageText( "azbuddy.add.friend.key" ));
		
					add_fk_item.addSelectionListener(
						new SelectionAdapter()
						{
							@Override
							public void
							widgetSelected(
								SelectionEvent e)
							{
								if ( !plugin.isClassicEnabled()){
									
									plugin.setClassicEnabled( true, false );
								}
									
								plugin.addBuddy( is_public_chat, fk, BuddyPluginNetwork.SUBSYSTEM_AZ2 );
								
								try{
									ChatInstance chat = participant.createPrivateChat();
	
									createChatWindow( view, plugin, chat);
	
									String message = "!azbuddy.add.friend.key.msg[" + UrlUtils.encode( getFriendURI( is_public_chat )) + "]!";
																		
									chat.sendMessage(message, null);
									
								}catch( Throwable f ){
	
									Debug.out( f );
								}
							}
						});
					
					add_fk_item.setEnabled( fk != null && !is_me && !is_friend );
					
					if ( is_friend ){
						
						BuddyPluginBuddy buddy = plugin.getBuddyFromPublicKey( fk );
						
							// cats - share
						
						final MenuItem cat_share_item = new MenuItem(friends_menu, SWT.PUSH);

						cat_share_item.setText( lu.getLocalisedMessageText( "azbuddy.ui.menu.cat.share" ) );

						cat_share_item.addSelectionListener(
							new SelectionAdapter()
							{
								@Override
								public void
								widgetSelected(
									SelectionEvent event )
								{
									TagManager tm = TagManagerFactory.getTagManager();
									
									List<Tag> all_tags = tm.getTagType( TagType.TT_DOWNLOAD_CATEGORY ).getTags();
									
									all_tags.addAll( tm.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTags());
									
									Map<String,Tag>	tag_map = new HashMap<>();
									
									for ( Tag t: all_tags ){
										
										tag_map.put( t.getTagName( true ), t );
									}
									
									List<Tag> selected_tags = new ArrayList<>();
									
									Set<String> enabled_tags = buddy.getLocalAuthorisedRSSTagsOrCategories();
									
									if ( enabled_tags != null ){
										
										for ( String s: enabled_tags ){
										
											Tag t = tag_map.get( s );
												
											if ( t != null ){
												
												selected_tags.add( t );
											}
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
											
											buddy.setLocalAuthorisedRSSTagsOrCategories( tag_names );
										});
								}
							});

							// cats - subscribe

						final Menu cat_subs_menu = new Menu(friends_menu.getShell(), SWT.DROP_DOWN);
						final MenuItem cat_subs_item = new MenuItem(friends_menu, SWT.CASCADE);
						Messages.setLanguageText(cat_subs_item, "azbuddy.ui.menu.cat_subs" );
						cat_subs_item.setMenu(cat_subs_menu);

						Set<String> cats = buddy.getRemoteAuthorisedRSSTagsOrCategories();

						cat_subs_item.setEnabled( cats != null && !cats.isEmpty());
						
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

									if ( cats != null ){

										for ( final String cat: cats ){
	
											final MenuItem subs_item = new MenuItem( cat_subs_menu, SWT.CHECK );
	
											subs_item.setText( cat );
	
											subs_item.setSelection( buddy.isSubscribedToCategory( cat ));
											
											subs_item.addSelectionListener(
												new SelectionAdapter()
												{
													@Override
													public void
													widgetSelected(
														SelectionEvent event )
													{
														if ( buddy.isRemoteRSSTagOrCategoryAuthorised( cat )){
	
															try{
																buddy.subscribeToCategory( cat );
	
															}catch( Throwable e ){
	
																
															}
														}
													}
												});
											}
									}
								}

								@Override
								public void
								menuHidden(
									MenuEvent arg0 )
								{
								}
							});

					}
				}
				
				boolean	pc_enable 	= false;
				boolean sk_enable	= false;
				
				if ( chat_pk != null ){
	
					for ( ChatParticipant participant: participants ){
	
						boolean is_me = Arrays.equals( participant.getPublicKey(), chat_pk );
						
						boolean is_friend = participant.getFriendStatus() != 0;

						if ( !is_me ){
	
							if ( !is_friend ){
								
								sk_enable = true;
							}
							
							pc_enable = true;
						}
					}
				}
	
				private_chat_item.setEnabled( pc_enable  );
				
				send_fk_item.setEnabled( sk_enable );
				
				friends_item.setEnabled( !participants.isEmpty());
			}
		}
		
		
		if ( participants.size() == 1 ){

			new MenuItem(menu, SWT.SEPARATOR );

			ChatParticipant participant = participants.get(0);
						
			String fk = participant.getFriendKey();
			
			final MenuItem mi_profile = new MenuItem( menu, SWT.PUSH );

			mi_profile.setText( lu.getLocalisedMessageText( "label.profile" ) + "..." );

			mi_profile.addSelectionListener(
				new SelectionAdapter() {

					@Override
					public void
					widgetSelected(
						SelectionEvent e )
					{
						List<String> props = participant.getProfileData();
						
						List<String>	names 	= new ArrayList<String>();
						List<String>	values 	= new ArrayList<String>();
						
						names.add( "label.help" );
						values.add(Wiki.FRIENDS_PUBLIC_PROFILE);
						
						names.add( "" );
						values.add( "" );
												
						for ( String prop: props ){
							
							String[] bits = prop.split( "=", 2 );
							
							if ( bits.length == 2 ){
								
								names.add( "!" + bits[0] + "!" );
								values.add( bits[1] );
							}
						}
						
						BuddyPluginBuddy buddy = plugin.getBuddyFromPublicKey( fk );
						
						String nick = "";
						
						if ( buddy != null ){
							
							String n = buddy.getNickName();
							
							if ( n != null && !n.isEmpty()){
								
								nick = ": " + n;
							}
						}
						
						new PropertiesWindow( 
							lu.getLocalisedMessageText( "label.profile" ) + nick,
							names, values );
					}
				});
			
			mi_profile.setEnabled( fk != null && participant.getProfileData() != null );
			
			final MenuItem mi_properties = new MenuItem( menu, SWT.PUSH );

			mi_properties.setText( lu.getLocalisedMessageText( "Subscription.menu.properties" ));

			List<String>	names 	= new ArrayList<String>();
			List<String>	values 	= new ArrayList<String>();

			names.add( "label.zone.offset" );
			values.add( participant.getZoneOffset());
			
			mi_properties.addSelectionListener(
				new SelectionAdapter() {

					@Override
					public void
					widgetSelected(
						SelectionEvent e )
					{
						new PropertiesWindow( participant.getName(), names, values );
					}
				});
			
			mi_properties.setEnabled( !names.isEmpty());
			
			new MenuItem(menu, SWT.SEPARATOR );

			final MenuItem mi_copy_clip = new MenuItem( menu, SWT.PUSH );

			mi_copy_clip.setText( lu.getLocalisedMessageText( "label.copy.to.clipboard" ));

			mi_copy_clip.addSelectionListener(
				new SelectionAdapter() {

					@Override
					public void
					widgetSelected(
						SelectionEvent e )
					{
						StringBuffer sb = new StringBuffer();

						sb.append( participants.get(0).getName( true ));

						if ( Constants.isCVSVersion()){

							List<ChatMessage>	messages = participants.get(0).getMessages();

							for ( ChatMessage msg: messages ){

								sb.append( "\r\n" + msg.getMessage());
							}

						}

						ClipboardCopy.copyToClipBoard( sb.toString());
					}
				});
		}
	}

	private void
	timerTick()
	{
		if ( buddy_table == null || buddy_table.isDisposed()){
			
			return;
		}
		
		for ( TableItem ti: buddy_table.getItems()){
			
			ChatParticipant	participant = (ChatParticipant)ti.getData();
			
			if ( participant != null ){
	
				String status = getFriendStatus( participant );
				
				if ( !ti.getText( bt_col_offset + 2 ).equals( status )){
					
					ti.setText( bt_col_offset + 2, status );
				}
			}
		}
	}
	
	private String
	getFriendStatus(
		ChatParticipant		participant )
	{
		
		int friend_status = participant.getFriendStatus();
		
		String status;
		
		if ( friend_status == 0 ){
			
			status = participant.getFriendKey()!=null?"+":"";
			
		}else{
			
			status = friend_status==1?"*":"~";
		}
		
		return( status );
	}
	
	private ChatParticipant
	setItemData(
		TableItem		item )
	{
		int index = buddy_table.indexOf(item);

		if ( index < 0 || index >= participants.size()){

			return( null );
		}

		ChatParticipant	participant = (BuddyPluginBeta.ChatParticipant)participants.get(index);

		item.setData( participant );

		updateItem( item );
		
		return( participant );
	}
	
	private void
	updateItem(
		TableItem		item )
	{
		ChatParticipant	participant = (ChatParticipant)item.getData();
			
		if ( participant != null ){
			
			int msg_count = participant.getMessageCount( true );
			
			String[] values = {
				participant.getName( ftux_ok ),
				String.valueOf(msg_count),
				getFriendStatus( participant )
			};
			
			for ( int i=0;i<values.length;i++){
				
				if ( !values[i].equals( item.getText( bt_col_offset + i ))){
				
					item.setText( bt_col_offset + i, values[i] );
				}
			}
			
			item.setData( TI_MSG_COUNT, msg_count );
			
			List<String> profile = participant.getProfileData();
			
			if ( profile != null && !profile.isEmpty()){
				
				for ( String s: profile ){
					
					s = s.trim().toLowerCase( Locale.US );
					
					if ( s.startsWith( "country" )){
						
						String[] bits = s.split( "=" );
						
						if ( bits.length == 2 ){
							
							String cc = bits[1].trim();
							
							Image img = ImageRepository.getCountryFlag( cc, true );
							
							if ( img != null ){
								
								item.setImage( bt_col_offset + 3, img );
							}
						}
					}
				}
			}
			
			setProperties( item, participant );
		}
	}

	private void
	setProperties(
		ChatParticipant		p )
	{
		for ( TableItem ti: buddy_table.getItems()){

			if ( ti.getData() == p ){

				setProperties( ti, p );
			}
		}
	}

	private void
	setProperties(
		TableItem			item,
		ChatParticipant		p )
	{
		if ( p.isIgnored() || p.isSpammer()){

			item.setForeground( bt_col_offset + 0, Colors.grey );

		}else{

			if ( p.isPinned()){

				item.setForeground( bt_col_offset + 0, Colors.fadedGreen );

			}else{

				if ( p.isMe()){

					item.setForeground( bt_col_offset + 0, Colors.fadedGreen );

					item.setFont( bt_col_offset + 0, italic_font );

				}else if ( p.isNickClash( true )){

					item.setForeground( bt_col_offset + 0, Colors.red );

				}else{

					if ( p.hasNickname()){

						item.setForeground( bt_col_offset + 0, Colors.blues[Colors.FADED_DARKEST] );

					}else{

						item.setForeground( bt_col_offset + 0, Utils.isDarkAppearanceNative()?Colors.white:Colors.black );
					}
				}
			}
		}
	}

	private void
	checkSubscriptions(
		boolean ftux_change )
	{
		Subscription[] subs = SubscriptionManagerFactory.getSingleton().getSubscriptions();
		
		for ( Subscription sub: subs ) {
			
			try{
				Engine e = sub.getEngine();
				
				if ( e instanceof WebEngine ) {
				
					String url = ((WebEngine)e).getSearchUrl();
					
					if ( isRSSURL( url, chat )) {
							
						if ( ftux_change ){
						
							SubscriptionResult[] results = sub.getResults( false );
							
							for ( SubscriptionResult r: results ) {
								
								Map<Integer,Object>	properties = r.toPropertyMap();

								String name = (String)properties.get( SearchResult.PR_NAME );
								
								if ( name.equals( BuddyPluginBeta.RSS_ITEMS_UNAVAILABLE )) {
									
									r.delete();
								}
							}
						}else{
							
							sub.getManager().getScheduler().downloadAsync( sub, true );
						}
					}
				}
			}catch( Throwable e ) {
			}
		}
	}
	
	private String
	encodeRSSURL(
		ChatInstance	inst )
	{
		return( "azplug:?id=azbuddy&arg=" + UrlUtils.encode( chat.getURL() + "&format=rss" ));
	}
	
	private boolean
	isRSSURL(
		String			url,
		ChatInstance	chat )
	{
		if ( url.startsWith( "azplug:?id=azbuddy" )){
			
			String chat_url = encodeRSSURL( chat );
			
			if ( url.contains( chat_url )){
				
				return( true );
			}
		}
		
		return( false );
	}
	
	protected void
	addDisposeListener(
		final DisposeListener	listener )
	{
		if ( shell != null ){

			if ( shell.isDisposed()){

				listener.widgetDisposed( null );

			}else{

				shell.addDisposeListener( listener );
			}
		}
	}

	private void
	updateTableHeader()
	{
		if ( buddy_table == null || buddy_table.isDisposed()){
			
			return;
		}
		
		int	active 	= 0;
		
		for ( TableItem ti: buddy_table.getItems()){
			
			Integer mc = (Integer)ti.getData( TI_MSG_COUNT );
			
			if ( mc != null && mc > 0 ){
				
				active++;
			}
		}
		
		int online	= chat.getEstimatedNodes();

		String msg =
			lu.getLocalisedMessageText(
				"azbuddy.dchat.user.status",
					new String[]{
						online<0?"...":(online >=100?"100+":String.valueOf( online )),
						String.valueOf( active )
					});

		table_header_left.setText( msg );
	}

	protected void
	updateTable(
		boolean	async )
	{
		if ( buddy_table == null ) {
			
			return;
		}
		
		if ( async ){

			if ( !buddy_table.isDisposed()){

				Utils.execSWTThread(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( buddy_table.isDisposed()){

								return;
							}

							updateTable( false );

							updateTableHeader();
						}
					});
			}
		}else{

			if ( table_resort_required ){

				table_resort_required = false;

				sortParticipants();
			}

			buddy_table.setItemCount( participants.size());
			buddy_table.clearAll();
			buddy_table.redraw();
		}
	}

	public void
	handleExternalDrop(
		String	payload )
	{
		handleDrop( 
			payload,
			new DropAccepter(){
					
				@Override
				public void accept(String link){
					if ( input_area == null ) {
						Debug.out( "TODO" );
					}else {
						input_area.setText( input_area.getText() + link );
					}
				}
			});
	}

	private void
	handleDrop(
		Object			payload,
		DropAccepter	accepter )
	{
		if ( payload instanceof String[]){

			String[]	files = (String[])payload;

			if ( files.length == 0 ){

				Debug.out( "Nothing to drop" );

			}else{
				int hits = 0;

				for ( String file: files ){

					File f = new File( file );

					if ( f.exists()){

						dropFile( f, accepter );

						hits++;
					}
				}

				if ( hits == 0 ){

					Debug.out( "Nothing files found to drop" );
				}
			}
		}else if ( payload instanceof String ){

			String stuff = (String)payload;

			if ( stuff.startsWith( "DownloadManager\n" ) ||stuff.startsWith( "DiskManagerFileInfo\n" )){

				String[]	bits =  RegExUtil.PAT_SPLIT_SLASH_N.split(stuff);

				for (int i=1;i<bits.length;i++){

					String	hash_str = bits[i];

					int	pos = hash_str.indexOf(';');

					try{

						if ( pos == -1 ){

							byte[]	 hash = Base32.decode( bits[i] );

							Download download = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getShortCuts().getDownload(hash);

							dropDownload( download, accepter );

						}else{

							String[] files = hash_str.split(";");

							byte[]	 hash = Base32.decode( files[0].trim());

							DiskManagerFileInfo[] dm_files = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getShortCuts().getDownload(hash).getDiskManagerFileInfo();

							for (int j=1;j<files.length;j++){

								DiskManagerFileInfo dm_file = dm_files[Integer.parseInt(files[j].trim())];

								dropDownloadFile( dm_file, accepter );
							}
						}
					}catch( Throwable e ){

						Debug.out( "Failed to get download for hash " + bits[1] );
					}
				}
			}else if ( stuff.startsWith( "TranscodeFile\n" )){

				String[]	bits =  RegExUtil.PAT_SPLIT_SLASH_N.split(stuff);

				for (int i=1;i<bits.length;i++){

					File f = new File( bits[i] );

					if ( f.isFile()){

						dropFile( f, accepter );
					}
				}
			}else{

				File f = new File( stuff );

				if ( f.exists()){

					dropFile( f, accepter );

				}else{
					String lc_stuff = stuff.toLowerCase( Locale.US );

					if ( 	lc_stuff.startsWith( "http:" ) ||
							lc_stuff.startsWith( "https:" ) ||
							lc_stuff.startsWith( "magnet: ")){

						dropURL( stuff, accepter );

					}else{

						Debug.out( "Failed to handle drop for '" + stuff + "'" );
					}
				}
			}
		}else if ( payload instanceof FixedURLTransfer.URLType ){

			String url = ((FixedURLTransfer.URLType)payload).linkURL;

			if ( url != null ){

				dropURL( url, accepter );

			}else{

				Debug.out( "Failed to handle drop for '" + payload + "'" );
			}
		}
	}

	private void
	dropURL(
		String			str,
		DropAccepter	accepter )
	{
		accepter.accept( str );
	}

	private void
	dropFile(
		final File				file,
		final DropAccepter		accepter )
	{
		try{
			if ( file.exists() && file.canRead()){

				new AEThread2( "share async" )
				{
					@Override
					public void
					run()
					{
						PluginInterface pi = plugin.getPluginInterface();

						Map<String,String>	properties = new HashMap<>();

						String[]	networks;

						if ( chat.isAnonymous()){

							networks = AENetworkClassifier.AT_NON_PUBLIC;

						}else{

							networks = AENetworkClassifier.AT_NETWORKS;
						}

						String networks_str = "";

						for ( String net: networks ){

							networks_str += (networks_str.length()==0?"":",") + net;
						}
						
						Utils.setPeronalShare( properties );
						
						properties.put( ShareManager.PR_NETWORKS, networks_str );

						Tag tag = plugin.getBeta().getDownloadTag();

						if ( tag != null ){
							
							properties.put( ShareManager.PR_TAGS, String.valueOf( tag.getTagUID()));
						}
						
						Torrent 	torrent;

						try{
							if ( file.isFile()){

								ShareResourceFile srf = pi.getShareManager().addFile( file, properties );

								torrent = srf.getItem().getTorrent();

							}else{

								ShareResourceDir srd = pi.getShareManager().addDir( file, properties );

								torrent = srd.getItem().getTorrent();
							}

							final Download download = pi.getPluginManager().getDefaultPluginInterface().getShortCuts().getDownload( torrent.getHash());

							if ( download == null ){

								throw( new Exception( "Download no longer exists" ));

							}
							Utils.execSWTThread(
								new Runnable() {

									@Override
									public void
									run()
									{
										dropDownload( download, accepter );

									}
								});

						}catch( Throwable e ){

							dropFailed( file.getName(), e );
						}
					}
				}.start();

			}else{

				throw( new Exception( "File '" + file + "' does not exist or is not accessible" ));
			}

		}catch( Throwable e ){

			dropFailed( file.getName(), e );
		}
	}

	private void
	dropDownload(
		Download		download,
		DropAccepter	accepter )
	{
		String magnet = chat.getMagnet( download, MAX_MSG_CHUNK_LENGTH );

		plugin.getBeta().tagDownload( download );

		download.setForceStart( true );

		accepter.accept( magnet );
	}

	private void
	dropDownloadFile(
		DiskManagerFileInfo		file,
		DropAccepter			accepter )
	{
		try{
			Download download = file.getDownload();

			if ( download.getTorrent().isSimpleTorrent()){

				dropDownload( download, accepter );

				return;
			}

			File target = file.getFile( true );

			if (	 target.exists() &&
					( file.getDownloaded() == file.getLength() ||
					( download.isComplete() && !file.isSkipped()))){	// just in case cached file completion is borked

				dropFile( target, accepter );

			}else{

				throw( new Exception( "File is incomplete or missing" ));
			}
		}catch( Throwable e ){

			dropFailed( file.getFile(true).getName(), e );
		}
	}

	private void
	dropFailed(
		String		content,
		Throwable 	e )
	{
		UIManager ui_manager = plugin.getPluginInterface().getUIManager();

		String details =
			MessageText.getString(
				"azbuddy.dchat.share.fail.msg",
				new String[]{ content, Debug.getNestedExceptionMessage( e ) });

		ui_manager.showMessageBox(
				"azbuddy.dchat.share.fail.title",
				"!" + details + "!",
				UIManagerEvent.MT_OK );
	}

	protected void
	close()
	{
		if ( shell != null ){

			shell.dispose();
		}
	}

	protected void
	closed()
	{
		if ( build_complete ){
			
			chat.removeListener( this );
	
			chat.destroy();
	
			view.unregisterUI( chat );
		}
	}

	private void
	setInputAvailability(
		boolean		focus )
	{
		boolean		avail = false;
		
		if ( !chat.isReadOnly()){
			
			if ( ftux_ok ){
			
				avail = chat_available;
			}
		}
		
		if ( !input_area.isDisposed()){
		
			input_area.setEnabled( avail );
		
			if ( avail && focus ) {
			
				input_area.setFocus();
			}
		}
	}
	
	@Override
	public void
	stateChanged(
		final boolean avail )
	{
		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{		
					chat_available	= avail;

					setInputAvailability( false );

						// update as key may now be available

					if ( nickname != null && !nickname.isDisposed()) {
					
						nickname.setMessage( chat.getDefaultNickname());
					}
				}
			});
	}

	@Override
	public void
	updated()
	{
		if ( status.isDisposed()){

			return;
		}

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					if ( status.isDisposed()){

						return;
					}

					status.setText( chat.getStatus());

					if ( shared_nick_button != null ) {
						
						boolean	is_shared = chat.isSharedNickname();
	
						if ( is_shared != shared_nick_button.getSelection()){
	
							shared_nick_button.setSelection( is_shared );
						}
					}
					
					if ( nickname != null ) {
						
						if ( !nickname.isFocusControl()){
	
							String old_nick = nickname.getText().trim();
	
							String new_nick = chat.getNickname( false );
	
							if ( !new_nick.equals( old_nick )){
	
								nickname.setText( new_nick );
							}
						}
					}
					
					if ( buddy_table != null ){
						
						if ( table_resort_required ){
	
							updateTable( false );
						}
	
						updateTableHeader();
					}
				}
			});
	}

	@Override
	public void
	configChanged()
	{
		if ( status.isDisposed()){

			return;
		}

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{					
					boolean changed = false;
					
					String cdf = beta.getCustomDateFormat();
					
					if ( cdf.isEmpty()){
					
						if ( custom_date_format != null ){
						
							custom_date_format = null;
						
							changed = true;
						}
						
					}else if ( !cdf.isEmpty()){
						
						try{
							SimpleDateFormat new_format = new SimpleDateFormat( cdf );
							
							if ( custom_date_format == null || !custom_date_format.equals( new_format )){
								
								custom_date_format = new_format;
								
								changed = true;
							}
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
					
					if ( changed ){
					
						messagesChanged();
					}
				}
			});
	}
	
	private void
	sortParticipants()
	{
		synchronized( participants ){
			Collections.sort(
				participants,
				new Comparator<ChatParticipant>()
				{
					private Comparator<String> comp = new FormattersImpl().getAlphanumericComparator( true );

					@Override
					public int
					compare(
						ChatParticipant p1,
						ChatParticipant p2 )
					{
						boolean	b_p1 = p1.hasNickname();
						boolean	b_p2 = p2.hasNickname();

						if ( b_p1 == b_p2 ){

							return( comp.compare( p1.getName( ftux_ok ), p2.getName( ftux_ok )));

						}else if ( b_p1 ){

							return( -1 );

						}else{

							return( 1 );
						}
					}
				});
		}
	}

	@Override
	public void
	participantAdded(
		ChatParticipant		participant )
	{
		synchronized( participants ){

			participants.add( participant );

			table_resort_required = true;
		}

		updateTable( true );
	}

	@Override
	public void
	participantChanged(
		final ChatParticipant		participant )
	{
		if ( buddy_table != null && !buddy_table.isDisposed()){

			Utils.execSWTThread(
				new Runnable()
				{
					@Override
					public void
					run()
					{
						if ( buddy_table.isDisposed()){

							return;
						}

						TableItem[] items = buddy_table.getItems();

						String	name = participant.getName( ftux_ok );

						for ( TableItem item: items ){

							if ( item.getData() == participant ){
								
								String old_name = item.getText( bt_col_offset + 0);

								if ( !old_name.equals( name )){

									table_resort_required = true;
								}
								
								updateItem( item );
							}
						}
					}
				});
		}
	}

	@Override
	public void
	participantRemoved(
		ChatParticipant		participant )
	{
		synchronized( participants ){

			participants.remove( participant );

			participant_last_message_map.remove( participant );
		}

		updateTable( true );
	}

	private void
	participantsChanged()
	{
		for ( TableItem ti: buddy_table.getItems()){

			updateItem( ti );
		}
	}
	
	protected void
	sendMessage(
		String		text,
		boolean		do_chunking )
	{
		//logChatMessage( plugin.getNickname(), Colors.green, text );

		try{
				// decode escaped unicode chars

			Pattern p = Pattern.compile("(?i)\\\\u([\\dabcdef]{4})");

			Matcher m = p.matcher( text );

			boolean result = m.find();

			if ( result ){

				StringBuffer sb = new StringBuffer();

		    	while( result ){

		    		 String str = m.group(1);

		    		 int unicode = Integer.parseInt( str, 16 );

		    		 m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char)unicode)));

		    		 result = m.find();
		    	 }

				m.appendTail(sb);

				text = sb.toString();
			}
		}catch( Throwable e ){
		}
				
		if ( do_chunking && text.length() > MAX_MSG_CHUNK_ENABLE ){

				// we want to avoid splitting emphasized text such as <b>blah blah</b>

			boolean	hacked = false;

			Pattern p = getEmphasisPattern();
			
			Matcher m = p.matcher( text );
	
			boolean result = m.find();
	
			if ( result ){
	
				StringBuffer sb = new StringBuffer();
	
		    	while( result ){
		    				
		    		String match_start	= m.group(1);
		    		String emp_text 	= m.group(3);	    		
		    		String match_end 	= m.group(4);	    		
		    			    		
		    		if ( emp_text.contains( " " )){
		    		
			    		emp_text = emp_text.replaceAll( " ", "\\\\u00a0" );
	
		    			hacked = true;
		    		}
		    		
		    		m.appendReplacement(sb, Matcher.quoteReplacement( match_start + emp_text + match_end ));
	
		    		result = m.find();
		    	}   	
		    	
				m.appendTail(sb);
	
				text = sb.toString();
			}	
			
			while( text.length() > MAX_MSG_CHUNK_LENGTH ){
				
				char[]	chars = text.toCharArray();
				
				int	pos = MAX_MSG_CHUNK_LENGTH-1;
				
				boolean chunked = false;
				
					// don't allow chunks to get too small
				
				while( pos > MAX_MSG_CHUNK_LENGTH/2 ){
					
					if ( chars[pos] == ' ' ){
						
						String chunk = text.substring( 0, pos ).trim();
						
						if ( !chunk.isEmpty()){
							
							if ( hacked ){
								
								chunk = chunk.replaceAll( "\\\\u00a0", " " );
							}
							
							chat.sendMessage( chunk, new HashMap<String, Object>());
						}
						
						text = text.substring( pos ).trim();
						
						chunked = true;
						
						break;
					}
					
					pos--;
				}
				
				if ( !chunked ){
					
					String chunk = text.substring( 0, MAX_MSG_CHUNK_LENGTH ).trim();
					
					if ( !chunk.isEmpty()){
					
						if ( hacked ){
							
							chunk = chunk.replaceAll( "\\\\u00a0", " " );
						}
						
						chat.sendMessage( chunk, new HashMap<String, Object>());
					}
					
					text = text.substring( MAX_MSG_CHUNK_LENGTH ).trim();
				}
			}
			
			if ( text.length() > 0 ){
				
				if ( hacked ){
					
					text = text.replaceAll( "\\\\u00a0", " " );
				}
			}
		}
		
		if ( text.length() > 0 ){
			
			chat.sendMessage( text, new HashMap<String, Object>());
		}
	}

	private static String
	expand(
		Map<String,String>	params,
		String				str,
		boolean				url_decode )
	{
		int	pos = 0;

		String result = "";

		while( true ){

			int new_pos = str.indexOf('$', pos );

			if ( new_pos == -1 ){

				result += str.substring( pos );

				break;
			}

			result += str.substring( pos, new_pos );

			int end_pos = str.length();

			for ( int i=new_pos+1;i<end_pos;i++){

				char c = str.charAt( i );

				if ( !( Character.isLetterOrDigit(c) || c == '_' )){

					end_pos = i;

					break;
				}
			}

			String param = str.substring( new_pos+1, end_pos );

			String value = params.get( param );

			if ( value == null ){

				pos = new_pos + 1;

				result += "$";

			}else{

				if ( url_decode ){

					result += UrlUtils.decode( value );

				}else{

					result += value;
				}

				pos = end_pos;
			}
		}

		return( result );
	}

	@Override
	public void
	messageReceived(
		final ChatMessage	message,
		boolean				sort_outstanding )
	{
		if ( sort_outstanding ){

				// we'll pick things up on the 'messagesChanged' callback after the sort

			return;
		}

		if ( log != null && !log.isDisposed()){

			Utils.execSWTThread(
				new Runnable()
				{
					@Override
					public void
					run()
					{
						if ( log.isDisposed()){

							return;
						}

						logChatMessage( message );
					}
				});
		}
	}

	private boolean change_pending;
	
	@Override
	public void
	messagesChanged()
	{
		if ( log != null && !log.isDisposed()){

			synchronized( this ){
				
				if ( change_pending ){
					
					return;
				}
				
				change_pending = true;
			}
			
			Utils.execSWTThread(
				new Runnable()
				{
					@Override
					public void
					run()
					{
						synchronized( BuddyPluginViewBetaChat.this ){
							
							change_pending = false;
						}
						
						if ( log.isDisposed()){

							return;
						}

						try{
							resetChatMessages();

							BuddyPluginBeta.ChatMessage[] history = chat.getHistory();

							logChatMessages( history );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				});
		}
	}

	private String	previous_says 				= null;
	private int		previous_says_mt			= -1;
	private long	last_seen_message			= -1;
	private long	last_seen_message_pending	= -1;

	private void
	resetChatMessages()
	{
		log.setText( "" );

		messages.clear();

		previous_says	= null;

		synchronized( participants ){

			participant_last_message_map.clear();
		}
	}

	private void
	logChatMessage(
		ChatMessage		message )
	{
		logChatMessages( new ChatMessage[]{ message } );
	}

	private SimpleDateFormat custom_date_format;
	
	private final SimpleDateFormat time_format1 	= new SimpleDateFormat( "HH:mm" );
	private final SimpleDateFormat time_format2a 	= new SimpleDateFormat( "EE h" );
	private final SimpleDateFormat time_format2b 	= new SimpleDateFormat( "a" );
	private final SimpleDateFormat time_format3 	= new SimpleDateFormat( "dd/MM" );

	private String
	getChatTimestamp(
		long		now,
		long		time )
	{
		if ( custom_date_format != null ){
			
			return( custom_date_format.format( new Date( time )));
		}
		
		long 	age 	= now - time;
		Date	date	= new Date( time );

		if ( age < 24*60*60*1000L ){

			return( time_format1.format( date ));

		}else if ( age < 7*24*60*60*1000L ){

			return( time_format2a.format( date ) + time_format2b.format( date ).toLowerCase());

		}else{

			return( time_format3.format( date ));
		}
	}

	private void
	addNickString(
		ChatParticipant	participant )
	{
		String name = "@" + participant.getName( true );
		
		name = name.replaceAll( " ", "\\\\u00a0" );
		
		String existing = input_area.getText();

		int caret = input_area.getCaretPosition();
					
		if ( caret > 0 ){
			
			char prev = existing.charAt( caret-1 );
				
			if ( prev != ' ' ){
					
				name = " " + name;
			}
		}
		
		if ( caret < existing.length()){
				
			char next = existing.charAt( caret );
				
			if ( next != ' ' ){
					
				name = name + " ";
			}
		}
		
		input_area.setSelection( caret, caret );
		
		input_area.insert( name );
	}
	
	private void
	logChatMessages(
		ChatMessage[]		all_messages )
	{
		long	now = SystemTime.getCurrentTime();

		final int	initial_log_length = log.getText().length();

		StringBuilder appended = new StringBuilder( 2048 );

		List<StyleRange>	new_ranges = new ArrayList<>();

		ChatMessage	last_message_not_ours 		= null;

		boolean	ignore_ratings 		= beta.getHideRatings();
		boolean	ignore_search_subs 	= beta.getHideSearchSubs();

		for ( ChatMessage message: all_messages ){

			if ( messages.containsKey( message )){

				continue;
			}
			
			byte[] raw_message = message.getRawMessage();
			
			boolean use_raw_message = false;
			
			if ( raw_message != null && raw_message.length > 3 ){
				
				if ( raw_message[0] == 'd' && Character.isDigit( raw_message[1] ) && raw_message[raw_message.length-1] == 'e' ){
					
					try{
						Map m = BDecoder.decode( raw_message );
						
						use_raw_message = true;
						
					}catch( Throwable e ){
						
					}
				}
			}

			String original_msg;
			
			if ( use_raw_message ){
				
				original_msg = Base32.encode( raw_message );
				
				if ( original_msg.length() > 20 ){
					
					original_msg = original_msg.substring( 0, 20 ) + "...";
				}
			}else{
				
				original_msg = message.getMessage();
			}

			if ( !message.isIgnored() && original_msg.length() > 0 ){

				if ( ignore_ratings || ignore_search_subs ){

					int origin = message.getFlagOrigin();

					if ( origin == BuddyPluginBeta.FLAGS_MSG_ORIGIN_RATINGS && ignore_ratings ){

						continue;

					}else if ( origin == BuddyPluginBeta.FLAGS_MSG_ORIGIN_SUBS && ignore_search_subs ){

						continue;
					}
				}

				long time = message.getTimeStamp();

				final ChatParticipant participant = message.getParticipant();

				final boolean	is_me = participant.isMe();

				if ( !is_me ){

					last_message_not_ours		= message;
				}

				final int message_start_appended_length 	= appended.length();
				final int message_start_style_index			= new_ranges.size();

				String	nick 	= message.getNickName();

				int	message_type = message.getMessageType();

				Font 	default_font 	= null;
				Color	default_colour	= null;

				Font 	info_font 	= null;
				Color	info_colour	= Colors.grey;

				Color colour = Colors.blues[Colors.FADED_DARKEST];

				if ( message_type ==  ChatMessage.MT_INFO ){

					if ( original_msg.startsWith( "*" ) && original_msg.endsWith( "*" )){

						original_msg = original_msg.substring( 1, original_msg.length()-1 );

						info_colour = Utils.isDarkAppearanceNative()?Colors.light_grey:Colors.black;
						info_font	= bold_font;

					}else{

						colour = Colors.grey;
					}
				}else if ( message_type ==  ChatMessage.MT_ERROR ){

					colour = Colors.red;

				}else if ( participant.isPinned() || is_me ){

					colour = Colors.fadedGreen;

				}else if ( message.isNickClash( true )){

					colour = Colors.red;
				}

				String stamp = getChatTimestamp( now, time );

				ChatMessage	last_message;

				synchronized( participants ){

					last_message = participant_last_message_map.get( participant );

					participant_last_message_map.put( participant, message );
				}

				boolean is_me_msg = message.getFlagType() == BuddyPluginBeta.FLAGS_MSG_TYPE_ME;

				String 	says;
				int		stamp_len;

				int	was_len = 0;

				if ( message_type != ChatMessage.MT_NORMAL ){

					says = "[" + stamp + "]";

					stamp_len = says.length();

				}else{

					says = "[" + stamp + "] " + (nick.length()>20?(nick.substring(0,16) + "..."):nick);

					stamp_len = stamp.length() + 3;

					if ( last_message != null && !is_me ){

						String last_nick = last_message.getNickName();

						if ( !nick.equals(last_nick)){

							String was = " (was " + (last_nick.length()>20?(last_nick.substring(0,16) + "..."):last_nick) + ")";

							says += was;

							was_len = was.length();
						}
					}
				}

				if ( message_type == ChatMessage.MT_NORMAL ){

					if ( is_me_msg ){

						says += " ";

						default_colour	= colour;

						if ( is_me ){

							default_font = italic_font;
						}

					}else{

						says += "\n";
					}
				}else{

					says += " ";

					if ( message_type ==  ChatMessage.MT_ERROR ){

						default_colour = colour;
					}
				}


				if ( 	previous_says == null ||
						previous_says_mt != message_type ||
						is_me_msg ||
						!previous_says.equals( says )){

					previous_says 		= says;
					previous_says_mt	= message_type;

					int	start = initial_log_length + appended.length();

					appended.append( says );

					{
						StyleRange styleRange = new MyStyleRange(message);
						styleRange.start = start;
						styleRange.length = stamp_len;
						styleRange.foreground = Colors.grey;

						if ( is_me ){

							styleRange.font = italic_font;
						}

						new_ranges.add( styleRange);
					}

					if ( colour != Colors.black ){

						int rem = says.length() - stamp_len;

						if ( rem > 0 ){

							StyleRange styleRange = new MyStyleRange(message);
							styleRange.start = start + stamp_len;
							styleRange.length = rem - was_len;
							styleRange.foreground = colour;
							styleRange.data = participant;

							if ( is_me ){

								styleRange.font = italic_font;
							}

							new_ranges.add( styleRange);
						}
					}
				}

				final int start = initial_log_length + appended.length();

				String rendered_msg = renderMessage( beta, chat, message, original_msg, message_type, start, new_ranges, info_font, info_colour, bold_font, italic_font );

				appended.append( rendered_msg );

					// apply any default styles

				if ( default_font != null || default_colour != null ){

					final int message_start_log_length 	= initial_log_length + message_start_appended_length;

					int	pos = message_start_log_length;

					for ( int i=message_start_style_index;i<new_ranges.size();i++){

						StyleRange style = new_ranges.get(i);

						int style_start 	= style.start;
						int style_length	= style.length;

						if ( style_start > pos ){

							//System.out.println( "    " + pos + "-" + (style_start-1 ));

							StyleRange styleRange 	= new MyStyleRange(message);
							styleRange.start 		= pos;
							styleRange.length 		= style_start - pos;

							if ( default_colour != null ){
								styleRange.foreground 	= default_colour;
							}
							if ( default_font != null ){
								styleRange.font = default_font;
							}

							new_ranges.add( i, styleRange);

							i++;
						}

						pos = style_start + style_length;
					}

					int message_end_log_length	= initial_log_length + appended.length();

					if ( pos < message_end_log_length ){

						//System.out.println( "    " + pos + "-" + (message_end_log_length-1) );

						StyleRange styleRange 	= new MyStyleRange(message);
						styleRange.start 		= pos;
						styleRange.length 		= message_end_log_length - pos;

						if ( default_colour != null ){
							styleRange.foreground 	= default_colour;
						}
						if ( default_font != null ){
							styleRange.font = default_font;
						}

						new_ranges.add( styleRange);
					}
				}

				appended.append( "\n" );

				int	actual_length = appended.length() - message_start_appended_length;

				messages.put( message, actual_length );
			}
		}

		if ( appended.length() > 0 ){

			try{
				log.setVisible( false );

				log.append( appended.toString());

				if ( new_ranges.size() > 0 ){

					List<StyleRange> existing_ranges = Arrays.asList( log.getStyleRanges());

					List<StyleRange> all_ranges = new ArrayList<>( existing_ranges.size() + new_ranges.size());

					all_ranges.addAll( existing_ranges );

					all_ranges.addAll( new_ranges );

					StyleRange[] ranges = all_ranges.toArray( new StyleRange[ all_ranges.size()]);

					for ( StyleRange sr: ranges ){

						sr.borderStyle = SWT.NONE;
					}

					log.setStyleRanges( ranges );

					log_styles = ranges;
				}


				Iterator<Integer> it = null;

				int max_lines 	= beta.getMaxUILines();
				int max_chars	= beta.getMaxUICharsKB() * 1024;

				int	total_to_remove = 0;
				
				while ( messages.size() > max_lines || log.getText().length() - total_to_remove > max_chars ){

					if ( it == null ){

						it = messages.values().iterator();
					}

					if ( !it.hasNext()){

						break;
					}

					int to_remove = it.next();

					it.remove();

					total_to_remove += to_remove;
				}
				
				if ( total_to_remove > 0 ){
					
					log.replaceTextRange( 0,  total_to_remove, "" );

					log_styles = log.getStyleRanges();
				}

				log.setSelection( log.getText().length());

			}finally{

				log.setVisible( true );
			}

			log.redraw();	// needed as sometimes new styleranges are not rendered without it :(
			
			if ( last_message_not_ours != null ){

				long last_message_not_ours_time = last_message_not_ours.getTimeStamp();

				boolean	mesages_seen = true;

				if ( build_complete ){

					if ( 	( !log.isVisible()) ||
							( shell != null && shell.getMinimized()) ||
							log.getDisplay().getFocusControl() == null ){

						if ( last_message_not_ours_time > last_seen_message ){

							last_seen_message_pending = last_message_not_ours_time;

							view.betaMessagePending( chat, log, last_message_not_ours );

							mesages_seen = false;
						}
					}else{

						last_seen_message = last_message_not_ours_time;
					}
				}else{

						// assume that during construction the messages will be seen

					if ( last_message_not_ours_time > last_seen_message ){

						last_seen_message = last_message_not_ours_time;
					}
				}

				if ( mesages_seen ){

					for ( ChatMessage msg: all_messages ){

						msg.setSeen( true );
					}
				}
			}
		}
	}

	private String
	renderMessage(
		String		str )
	{
		List<StyleRange>	ranges = new ArrayList<>();

		String msg = renderMessage(null, chat, null,str,  ChatMessage.MT_NORMAL, 0, ranges, null, null, null, null );
		
		return( msg );

	}

	private static String
	expandResources(
		String		text )
	{
			// resource ids are of the form !key.x...[arg1,arg2...]!
		
		if ( !text.contains( "!" )) {
			
			return( text );
		}
		
		try{	
			Pattern p = RegExUtil.getCachedPattern( "BPVBC:resource", "!([^\\s]+)!");
	
			Matcher m = p.matcher( text );
	
			boolean result = m.find();
	
			if ( result ){
	
				StringBuffer sb = new StringBuffer();
	
		    	while( result ){
	
		    		 String str = m.group(1);
	
		    		 if ( str.contains( "." )){
		    			 
		    			 int pos = str.indexOf( '[' );
		    			 
		    			 String 	resource;
		    			 String[]	params;
		    			 
		    			 if ( pos != -1 && str.endsWith( "]" )){
		    				 
		    				 resource = str.substring( 0, pos );
		    				 		    				 
		    				 String rem = str.substring( pos+1, str.length()-1 );
		    				 
		    				 params = rem.split( "," );
		    				 
		    				 for ( int i=0;i<params.length;i++){
		    					 
		    					 params[0] = UrlUtils.decode( params[0]);
		    				 }
		    			 }else{
		    				 
		    				 resource 	= str;
		    				 params		= null;
		    			 }
	
		    			 if ( params == null ){
		    			 
		    				 str = MessageText.getString( resource );
		    				 
		    			 }else{
		    				 
		    				 str = MessageText.getString( resource, params );
		    			 }
		    		 }
		    		 
		    		 m.appendReplacement(sb, Matcher.quoteReplacement( str ));
	
		    		 result = m.find();
		    	 }
	
				m.appendTail(sb);
	
				text = sb.toString();
			}
		}catch( Throwable e ){
		}
		
		return( text );
	}
	
	private static Pattern
	getEmphasisPattern()
	{
		return( RegExUtil.getCachedPattern( "BPVBC:emphasis1", "(?i)([\\*_]{1,2}|(?:[<\\[]([bin])[>\\]]))([^\\n]+?)(\\1|(?:[<\\[]/\\2[>\\]]))" ));
	}
	
	private static String
	expandEmphasis(
		String		text )
	{
			// *dadasd*
		
		if ( !(text.contains( "*" ) || text.contains( "_" ) || text.contains( "<" ) || text.contains( "[" ))){
			
			return( text );
		}
		
		try{	
			Pattern p = getEmphasisPattern();
	
			Matcher m = p.matcher( text );
	
			boolean result = m.find();
	
			if ( result ){
	
				StringBuffer sb = new StringBuffer();
	
		    	while( result ){
	
		    		String existing = sb.toString();
		    		
		    		int start = m.start(1);
		    		
		    		boolean pad = false;
		    		
		    		if ( start > 0 && !Character.isWhitespace( text.charAt( start-1 ))){
		    		
		    			pad = true;
		    		}
		    		
		    		if ( existing.endsWith( "]]" )){
		    			
		    			sb.append( "\ufeff" );
		    		}
		    				
		    		String match	= m.group(1).toLowerCase(Locale.US);
		    		
		    		String type;
		    		
		    		if ( match.contains( "n" )){
		    		
		    			type = "normal";
		    			
		    		}else if ( match.length()==1 || match.contains( "i" )){
		    			
		    			type = "italic";
		    			
		    		}else{
		    			
		    			type = "bold";
		    		}
		    		
		    		String str 		= m.group(3);
		    		 
		    		m.appendReplacement(sb, Matcher.quoteReplacement( (pad?"\ufeff":"") + "chat:" + type + "[[" + UrlUtils.encode( str ) + "]]"));
	
		    		result = m.find();
		    	}
	
		    	if ( sb.toString().endsWith( "]]" )){
	    			
	    			sb.append( "\ufeff" );
	    		}
		    	
				m.appendTail(sb);
	
				text = sb.toString();
			}
		}catch( Throwable e ){
		}
		
		return( text );
	}
	
	protected static String
	renderMessage(
		BuddyPluginBeta		beta,
		ChatInstance		chat,
		ChatMessage			message,
		String				original_msg,
		int					message_type,
		int					start,
		List<StyleRange>	new_ranges,
		Font				info_font,
		Color				info_colour,
		Font				bold_font,
		Font				italic_font )
	{	
		String msg = original_msg;

		try{
			{
				List<Object>		segments = new ArrayList<>();

				int	pos = 0;

				while( true ){

					int old_pos = pos;

					pos = original_msg.indexOf( ':', old_pos );

					if ( pos == -1 ){

						String tail = original_msg.substring( old_pos );

						if ( tail.length() > 0 ){

							segments.add( tail );
						}

						break;
					}

					boolean	was_url = false;

					String	protocol = "";

					for (int i=pos-1; i>=0; i-- ){

						char c = original_msg.charAt(i);

						if ( !Character.isLetterOrDigit( c )){

							if ( c == '"' ){

								protocol = c + protocol;
							}

							break;
						}

						protocol = c + protocol;
					}

					if ( protocol.length() > 0 ){

						char term_char = ' ';

						if ( protocol.startsWith( "\"" )){

							term_char = '"';
						}

						int	url_start 	= pos - protocol.length();
						int	url_end 	= original_msg.length();

						for ( int i=pos+1;i<url_end;i++){

							char c = original_msg.charAt( i );

								// some of the unsafe chars from https://tools.ietf.org/html/rfc1738 
								// we use [[..]] internally so generally allow them
							
							if ( "<>{}\\^~`".indexOf( c ) != -1 ){
								
								url_end = i;
								
								break;
							}
							
							if ( c == term_char || ( term_char == ' ' && ( c == '\ufeff' || Character.isWhitespace( c )))){

								url_end = term_char==' '?i:(i+1);

								break;
							}
						}

						if ( url_end > pos+1 && !Character.isDigit( protocol.charAt(0))){

							try{
								String url_str = protocol + original_msg.substring( pos, url_end );

								if ( url_str.startsWith( "\"" ) && url_str.endsWith( "\"" )){

									url_str = url_str.substring( 1, url_str.length()-1 );

									protocol = protocol.substring(1);
								}

								URL	url = new URL( url_str );

								if ( url_start > old_pos ){

									segments.add( original_msg.substring( old_pos, url_start ));
								}

								segments.add( url );

								was_url = true;

								pos	= url_end;

							}catch( Throwable e ){

							}
						}
					}

					if ( !was_url ){

						pos++;

						segments.add( original_msg.substring( old_pos, pos ) );
					}
				}

				if ( segments.size() > 1 ){

					List<Object>	temp = new ArrayList<>( segments.size());

					String	str = "";

					for ( Object obj: segments ){

						if ( obj instanceof String ){

							str += obj;

						}else{

							if ( str.length() > 0 ){

								temp.add( str );
							}

							str = "";

							temp.add( obj );
						}
					}

					if ( str.length() > 0 ){

						temp.add( str );
					}

					segments = temp;
				}

				Map<String,String>	params = new HashMap<>();

				for ( int i=0;i<segments.size(); i++ ){

					Object obj = segments.get(i);

					if ( obj instanceof URL ){

						params.clear();

						String str = ((URL)obj).toExternalForm();

						int	qpos = str.indexOf( '?' );

						if ( qpos > 0 ){

							int	hpos = str.lastIndexOf( "[[" );

							if ( hpos < qpos ){
								
								hpos = -1;
							}
							
							String[]	bits = str.substring( qpos+1, hpos==-1?str.length():hpos ).split( "&" );

							for ( String bit: bits ){

								String[] temp = bit.split( "=", 2 );

								if ( temp.length == 2 ){

									params.put( temp[0], temp[1] );
								}
							}

							if ( hpos > 0 && str.endsWith( "]]" )){

								str = 	str.substring( 0, hpos ) +
										"[[" +
										expand( params, str.substring( hpos+2, str.length()-2 ), false ) +
										"]]";

								try{
									segments.set( i, new URL( str ));

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}else{

							int	hpos = str.lastIndexOf( "[[" );

							if ( hpos > 0 && str.endsWith( "]]" )){

								str = 	str.substring( 0, hpos ) +
										"[[" +
										str.substring( hpos+2, str.length()-2 ) +
										"]]";

								try{
									segments.set( i, new URL( str ));

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					}else{

						String str = (String)obj;

						if ( message_type == ChatMessage.MT_NORMAL ){
							
							str = expandResources( str );
							
							if ( bold_font != null && message != null ){
							
								if ( message.getFlagOrigin() == BuddyPluginBeta.FLAGS_MSG_ORIGIN_USER ){
								
									str = expandEmphasis( str );
								}
							}
						}
						
						if ( params.size() > 0 ){

							str = expand( params, str, true );
						}
						
						segments.set( i, str );
					}
				}

				StringBuilder sb = new StringBuilder( 1024 );

				for ( Object obj: segments ){

					if ( obj instanceof URL ){

						sb.append("\"").append(((URL) obj).toExternalForm()).append("\"");

					}else{

						String segment_str = (String)obj;

						try{
							String my_nick = chat.getNickname( true );

							my_nick = my_nick.replaceAll( " ", "\u00a0" );
							
							if ( 	my_nick.length() > 0 &&
									segment_str.contains( my_nick ) &&
									message_type ==  ChatMessage.MT_NORMAL ){

								StringBuilder temp = new StringBuilder( segment_str.length() + 1024 );

								int	nick_len = my_nick.length();

								int	segment_len = segment_str.length();

								int	segment_pos = 0;

								while( segment_pos < segment_len ){

									int next_pos = segment_str.indexOf( my_nick, segment_pos );

									if ( next_pos >= 0 ){

										temp.append( segment_str.substring( segment_pos, next_pos ));

										boolean	match = true;

										if ( next_pos > 0 ){

											if ( Character.isLetterOrDigit( segment_str.charAt( next_pos-1 ))){

												match = false;
											}
										}

										int nick_end = next_pos + nick_len;

										if ( nick_end < segment_len ){

											if ( Character.isLetterOrDigit( segment_str.charAt(nick_end ))){

												match = false;
											}
										}

										if ( match ){

											temp.append("\"chat:nick[[").append(UrlUtils.encode(my_nick)).append("]]\"");

										}else{

											temp.append( my_nick );
										}

										segment_pos = next_pos + nick_len;

									}else{

										temp.append( segment_str.substring(segment_pos));

										break;
									}
								}

								segment_str = temp.toString();
							}
						}catch( Throwable e ){

							Debug.out( e );
						}

						sb.append( segment_str );
					}
				}

				msg = sb.toString();
			}

			{
					// should rewrite this one day to use the segments above directly... We'd need to handle URLs in expansions though

				int	next_style_start = start;

				int	pos = 0;

				while( pos < msg.length()){

					pos = msg.indexOf( ':', pos );

					if ( pos == -1 ){

						break;
					}

					String	protocol = "";

					for (int i=pos-1; i>=0; i-- ){

						char c = msg.charAt(i);

						if ( !Character.isLetterOrDigit( c )){

							if ( c == '"' ){

								protocol = c + protocol;
							}

							break;
						}

						protocol = c + protocol;
					}

					if ( protocol.length() > 0 ){

						char term_char = ' ';

						if ( protocol.startsWith( "\"" )){

							term_char = '"';
						}

						int	url_start 	= pos - protocol.length();
						int	url_end 	= msg.length();

						for ( int i=pos+1;i<url_end;i++){

							char c = msg.charAt( i );

							if ( c == term_char || ( term_char == ' ' && ( c == '\ufeff' || Character.isWhitespace( c )))){

								url_end = term_char==' '?i:(i+1);

								break;
							}
						}

						if ( url_end > pos+1 && !Character.isDigit( protocol.charAt(0))){

							try{
								String url_str = protocol + msg.substring( pos, url_end );

								if ( url_str.startsWith( "\"" ) && url_str.endsWith( "\"" )){

									url_str = url_str.substring( 1, url_str.length()-1 );

									protocol = protocol.substring(1);
								}

								if ( protocol.equalsIgnoreCase( "chat" )){

									if ( url_str.toLowerCase( Locale.US ).startsWith( "chat:anon" )){

										if ( beta != null && !beta.isI2PAvailable()){

											throw( new Exception( "Anonymous chat unavailable" ));
										}
									}
								}else{

										// test that it is a valid URL

									URL	url = new URL( url_str );
								}

								String original_url_str = url_str;

								String display_url = UrlUtils.decode( url_str );

									// support a lame way of naming links - just append [[<url-encoded desc>]] to the URL

								int hack_pos = url_str.lastIndexOf( "[[" );

								if ( hack_pos > 0 && url_str.endsWith( "]]" )){

									String substitution = url_str.substring( hack_pos + 2, url_str.length() - 2  ).trim();

									url_str = url_str.substring( 0, hack_pos );

										// prevent anything that looks like a URL from being used as the display
										// text to avoid 'confusion' but be lenient for safe protocols

									boolean safe = protocol.equals( "azplug" ) || protocol.equals( "chat" );

									if ( safe || UrlUtils.parseTextForURL( substitution, true ) == null ){

										display_url = UrlUtils.decode( substitution );

									}else{

										display_url = UrlUtils.decode( url_str );
									}
								}

								if ( term_char != ' ' || !display_url.equals( original_url_str )){

									int	old_len = msg.length();

									msg = msg.substring( 0, url_start ) + display_url + msg.substring( url_end );

										// msg has probably changed length, update the end-pointer accordingly

									url_end += (msg.length() - old_len );
								}

								int	this_style_start 	= start + url_start;
								int this_style_length	= display_url.length();

								if ( this_style_start > next_style_start ){

									if ( message_type ==  ChatMessage.MT_INFO ){

										StyleRange styleRange 	= new MyStyleRange(message);
										styleRange.start 		= next_style_start;
										styleRange.length 		= this_style_start - next_style_start;
										styleRange.foreground 	= info_colour;
										styleRange.font			= info_font;

										new_ranges.add( styleRange);

										next_style_start = this_style_start + this_style_length;
									}
								}

									/* Check that the URL is actually going to be useful. IN particular, if it is a magnet URI with
									 * no hash and no &fl links then it ain't gonna work
									 */

								boolean	will_work = true;

								Font	fail_font = bold_font;
								
								try{

									String lc_url = url_str.toLowerCase( Locale.US );

									if ( lc_url.startsWith( "magnet" )){

										if ( UrlUtils.getTruncatedHashFromMagnetURI( lc_url ) == null ){

												// no hash

											if ( !lc_url.contains( "&fl=" )){

													// no direct link

												will_work = false;
											}
										}
									}else if ( lc_url.startsWith( "chat:nick" ) || lc_url.startsWith( "chat:bold" )){

										will_work = false;
										
									}else if ( lc_url.startsWith( "chat:italic" )){
										
										fail_font = italic_font;
										
										will_work = false;
										
									}else if ( lc_url.startsWith( "chat:normal" )){
										
										fail_font = null;
										
										will_work = false;
									}
								}catch( Throwable e ){

								}

								if ( will_work ){

									StyleRange styleRange 	= new MyStyleRange( message );
									styleRange.start 		= this_style_start;
									styleRange.length 		= this_style_length;
									styleRange.foreground 	= Utils.getDisplay().getSystemColor(SWT.COLOR_LINK_FOREGROUND);
									styleRange.underline 	= true;

										// DON'T store the URL object because in their wisdom SWT invokes the .equals method
										// on data objects when trying to find 'similar' ones, and for URLs this causes
										// a name service lookup...

									styleRange.data = url_str;

									new_ranges.add( styleRange);

								}else{

									StyleRange styleRange 	= new MyStyleRange( message );
									styleRange.start 		= this_style_start;
									styleRange.length 		= this_style_length;
									styleRange.font 		= fail_font;

									new_ranges.add( styleRange);
								}
							}catch( Throwable e ){

								//e.printStackTrace();
							}
						}

						pos = url_end;

					}else{

						pos = pos+1;
					}
				}

				if ( next_style_start < start + msg.length() ){

					if ( message_type ==  ChatMessage.MT_INFO ){

						StyleRange styleRange 	= new MyStyleRange( message );
						styleRange.start 		= next_style_start;
						styleRange.length 		= start + msg.length() - next_style_start;
						styleRange.foreground 	= info_colour;
						styleRange.font			= info_font;

						new_ranges.add( styleRange);
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		return( msg );
	}

	public void
	activate()
	{
		if ( last_seen_message_pending > last_seen_message ){

			last_seen_message = last_seen_message_pending;
		}

		view.betaMessagePending( chat, log, null );

		List<ChatMessage>	unseen = chat.getUnseenMessages();

		for ( ChatMessage msg: unseen ){

			msg.setSeen( true );
		}
	}

	private static class
	MyStyleRange
		extends StyleRange
	{
		private ChatMessage	message;

		MyStyleRange(
			ChatMessage		_msg )
		{
			message = _msg;

			data = message;
		}

		MyStyleRange(
			MyStyleRange	other )
		{
			super( other );

			message = other.message;
		}
	}
	
	private interface
	DropAccepter
	{
		public void
		accept(
			String		link );
	}
}
