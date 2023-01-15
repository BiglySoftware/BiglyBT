/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.*;
import java.util.List;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.util.Wiki;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.utils.SWTRunnable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.download.impl.DownloadManagerController;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLGroup;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.proxy.AEProxySelector;
import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.plugin.I2PHelpers;
import com.biglybt.plugin.extseed.ExternalSeedPlugin;
import com.biglybt.plugin.extseed.ExternalSeedReader;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;



public class PrivacyView
	implements UISWTViewCoreEventListener, DownloadManagerStateAttributeListener
{
	public static final String MSGID_PREFIX = "PrivacyView";

	private static boolean	KEEP_VIEW			= true;
	private static int		MAX_LOOKUP_HISTORY	= 16;
	
	private UISWTView swtView;

	private Composite cMainComposite;

	private ScrolledComposite sc;

	private Composite 	parent;

	private static final int PL_PUBLIC		= 0;
	private static final int PL_MIX			= 1;
	private static final int PL_ANONYMOUS	= 2;
	private static final int PL_INVALID		= 3;

	private int			privacy_level;
	private Scale 		privacy_scale;

	private boolean		i2p_install_prompted;



	private Button[]	network_buttons;
	private Button[]	source_buttons;

	private Button		ipfilter_enabled;

	private BufferedLabel	peer_info;

	private BufferedLabel	torrent_info;
	private BufferedLabel	tracker_info;
	private BufferedLabel	webseed_info;

	private BufferedLabel	vpn_info;

	private BufferedLabel	socks_state;
	private BufferedLabel 	socks_current, socks_fails;
	private Label			socks_more;

	private DownloadManager	current_dm;

	private Set<String>		enabled_networks 	= new HashSet<>();
	private Set<String>		enabled_sources 	= new HashSet<>();

	private	I2PStackItem	current_i2p_entry;
	private Group 			i2p_stack_group;
    private StackLayout 	stack_layout;

	public
	PrivacyView()
	{
	}

	private String
	getFullTitle()
	{
		return( MessageText.getString("label.privacy"));
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				swtView = (UISWTView) event.getData();
				swtView.setTitle(getFullTitle());
				if ( KEEP_VIEW ){
					swtView.setDestroyOnDeactivate(false);
				}
				
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				delete();
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				parent = (Composite) event.getData();
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				Messages.updateLanguageForControl(cMainComposite);
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				Object ds = event.getData();
				dataSourceChanged(ds);
				break;

			case UISWTViewEvent.TYPE_FOCUSGAINED:
				initialize();
				if (current_dm == null) {
					dataSourceChanged(swtView.getDataSource());
				}
				break;

			case UISWTViewEvent.TYPE_FOCUSLOST:
				if ( !KEEP_VIEW ){
					delete();
				}
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;
		}

		return true;
	}

	private void
	delete()
	{
		Utils.disposeComposite( sc );

		dataSourceChanged( null );
	}

	private void
	refresh()
	{
		updatePeersEtc( current_dm );
	}

	private void
	dataSourceChanged(
		Object newDataSource )
	{
		synchronized( this ){

			DownloadManager new_dm = ViewUtils.getDownloadManagerFromDataSource( newDataSource, current_dm );

			if ( new_dm == current_dm ){

				return;
			}

			final DownloadManager f_old_dm = current_dm;

			current_dm = new_dm;

			final DownloadManager f_new_dm = current_dm;

			Utils.execSWTThread(new SWTRunnable() {
				@Override
				public void runWithDisplay(Display display) {
					swt_updateFields( f_old_dm, f_new_dm );
				}
			});
		}
	}

	private void
	initialize()
	{
		if (cMainComposite == null || cMainComposite.isDisposed()){

			if ( parent == null || parent.isDisposed()){
				return;
			}

			sc = new ScrolledComposite(parent, SWT.V_SCROLL);
			sc.setExpandHorizontal(true);
			sc.setExpandVertical(true);
			sc.getVerticalBar().setIncrement(16);

			Layout parentLayout = parent.getLayout();

			if ( parentLayout instanceof GridLayout ){

				GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);

				sc.setLayoutData(gd);

			}else if ( parentLayout instanceof FormLayout ){

				sc.setLayoutData(Utils.getFilledFormData());
			}

			cMainComposite = new Composite(sc, SWT.NONE);

			sc.setContent(cMainComposite);

		}else{

			if ( KEEP_VIEW ){
				
				return;
			}
			
			Utils.disposeComposite(cMainComposite, false);
		}

		GridLayout layout = new GridLayout(1, false);
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		cMainComposite.setLayout(layout);

		GridData gd;

			// overview

		Composite overview_comp = new Composite( cMainComposite, SWT.NULL );
		overview_comp.setLayout(  new GridLayout(3, false ));

		gd = new GridData( GridData.FILL_HORIZONTAL );
		overview_comp.setLayoutData(gd);

		Label label = new Label( overview_comp, SWT.NULL );
		label.setText( MessageText.getString( "privacy.view.intro" ));

		new LinkLabel( overview_comp, "label.read.more", Wiki.PRIVACY_VIEW);

			// slider component

		Composite slider_comp = new Composite( cMainComposite, SWT.NULL );
		layout = new GridLayout(3, false );
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		//layout.marginWidth = 0;
		slider_comp.setLayout( layout);

		gd = new GridData( GridData.FILL_HORIZONTAL );
		slider_comp.setLayoutData(gd);

		label = new Label( slider_comp, SWT.NULL );
		label.setText(  MessageText.getString( "privacy.view.level" ) + ":" );

		Composite slider2_comp = new Composite( slider_comp, SWT.NULL );
		slider2_comp.setLayout( new GridLayout(6, true ));
		gd = new GridData( GridData.FILL_HORIZONTAL );
		slider2_comp.setLayoutData(gd);

		label = new Label( slider2_comp, SWT.NULL );
		label.setText( MessageText.getString( "privacy.view.public.only" ));

		label = new Label( slider2_comp, SWT.NULL );
		label.setText( MessageText.getString( "privacy.view.public.anon" ));
		label.setAlignment( SWT.CENTER );
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		label.setLayoutData(gd);

		label = new Label( slider2_comp, SWT.NULL );
		label.setText( MessageText.getString( "privacy.view.anon.only" ));
		label.setAlignment( SWT.CENTER );
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		label.setLayoutData(gd);

		label = new Label( slider2_comp, SWT.NULL );
		label.setText(  MessageText.getString( "label.invalid" ));
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalAlignment = SWT.END;
		label.setLayoutData(gd);

		privacy_scale = new Scale(slider2_comp, SWT.HORIZONTAL);

		gd = new GridData( GridData.FILL_HORIZONTAL );
		gd.horizontalSpan = 6;
		privacy_scale.setLayoutData(gd);

		privacy_scale.setMinimum( 0 );
		privacy_scale.setMaximum( 30 );



		final boolean[] slider_mouse_down = { false };

		privacy_scale.addMouseListener(
			new MouseAdapter()
			{
				@Override
				public void
				mouseUp(
					MouseEvent e )
				{
					int	pos = privacy_scale.getSelection();

					int level = ((pos+5)/10);

					if ( level*10 != pos ){

						privacy_scale.setSelection( level*10 );
					}

					setPrivacyLevel( level );

					slider_mouse_down[0] = false;
				}

				@Override
				public void
				mouseDown(
					MouseEvent e )
				{
					slider_mouse_down[0] = true;
				}
			});

		privacy_scale.addListener(
			SWT.Selection,
			new Listener()
			{
				@Override
				public void
				handleEvent(Event event)
				{
					if ( !slider_mouse_down[0]){

						int pos = privacy_scale.getSelection();

						int level = ((pos+5)/10);

						setPrivacyLevel( level );
					}
				}
		    });

			// network selection

		Composite network_comp = new Composite( slider_comp, SWT.NULL );

		gd = new GridData();
		network_comp.setLayoutData(gd);

		network_buttons = new Button[AENetworkClassifier.AT_NETWORKS.length];

		network_comp.setLayout( new GridLayout( 1, false ));

		label = new Label( network_comp, SWT.NULL );
		label.setText( MessageText.getString( "ConfigView.section.connection.group.networks") + ":" );

		for ( int i=0; i<network_buttons.length; i++){

			final String nn = AENetworkClassifier.AT_NETWORKS[i];

			String msg_text = "ConfigView.section.connection.networks." + nn;

			Button button = new Button(network_comp, SWT.CHECK);
			Messages.setLanguageText(button, msg_text);

			network_buttons[i] = button;

			button.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean selected = ((Button)e.widget).getSelection();

					if ( current_dm != null ){
						current_dm.getDownloadState().setNetworkEnabled(nn, selected);
					}
				}
			});

			GridData gridData = new GridData();
			button.setLayoutData(gridData);
		}


		label = new Label( slider_comp, SWT.NULL );

		final Composite tracker_webseed_comp = new Composite( slider_comp, SWT.NULL );

		layout = new GridLayout( 2, true );
		layout.marginTop = layout.marginBottom = layout.marginLeft = layout.marginRight = 1;
		tracker_webseed_comp.setLayout( layout);
		gd = new GridData( GridData.FILL_HORIZONTAL );
		gd.horizontalSpan = 2;
		tracker_webseed_comp.setLayoutData(gd);

		tracker_webseed_comp.addPaintListener(
			new PaintListener(){
				@Override
				public void
				paintControl(PaintEvent e)
				{
					Rectangle client_area = tracker_webseed_comp.getClientArea();

					Rectangle rect = new Rectangle(0,0, client_area.width-1, client_area.height-1);

					e.gc.setAlpha(50);

	                e.gc.drawRectangle(rect);
				}
			});

			// Tracker Info

		Composite tracker_comp = new Composite( tracker_webseed_comp, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		tracker_comp.setLayoutData(gd);
		tracker_comp.setLayout( new GridLayout( 2, false ));

		label = new Label( tracker_comp, SWT.NULL );
		label.setText( MessageText.getString( "label.trackers" ) + ":" );

		tracker_info = new BufferedLabel(tracker_comp,SWT.DOUBLE_BUFFERED);
		gd = new GridData( GridData.FILL_HORIZONTAL );
		tracker_info.setLayoutData(gd);

		// Webseed Info

		Composite webseed_comp = new Composite( tracker_webseed_comp, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		webseed_comp.setLayoutData(gd);

		webseed_comp.setLayout( new GridLayout( 2, false ));

		label = new Label( webseed_comp, SWT.NULL );
		label.setText( MessageText.getString( "label.webseeds" ) + ":" );

		webseed_info = new BufferedLabel(webseed_comp,SWT.DOUBLE_BUFFERED);
		gd = new GridData( GridData.FILL_HORIZONTAL );
		webseed_info.setLayoutData(gd);

			// Peer Info

		//label = new Label( slider_comp, SWT.NULL );

		Composite peer_comp = new Composite( tracker_webseed_comp, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		peer_comp.setLayoutData(gd);
		peer_comp.setLayout( new GridLayout( 2, false ));

		label = new Label( peer_comp, SWT.NULL );
		label.setText( MessageText.getString( "TableColumn.header.peers" ) + ":" );

		peer_info = new BufferedLabel(peer_comp,SWT.DOUBLE_BUFFERED);
		gd = new GridData( GridData.FILL_HORIZONTAL );
		peer_info.setLayoutData(gd);


			// I2P install state

		i2p_stack_group = new Group( cMainComposite, SWT.NULL );
		i2p_stack_group.setText( "I2P" );

		
        stack_layout = new StackLayout();
        i2p_stack_group.setLayout(stack_layout);
		gd = new GridData( GridData.FILL_HORIZONTAL );
		i2p_stack_group.setLayoutData(gd);

		current_i2p_entry= new I2PStackItem( i2p_stack_group, null );
        
		stack_layout.topControl = current_i2p_entry.getComposite();
		
		Group bottom_comp = new Group( cMainComposite, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		bottom_comp.setLayoutData(gd);

		bottom_comp.setLayout( new GridLayout( 2, false ));

			// Torrent Info

		label = new Label( bottom_comp, SWT.NULL );
		label.setText( MessageText.getString( "authenticator.torrent" ) + ":" );

		Composite torrent_comp = new Composite( bottom_comp, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		torrent_comp.setLayoutData(gd);
		torrent_comp.setLayout( removeMarginsAndSpacing( new GridLayout( 2, false )));

		torrent_info = new BufferedLabel(torrent_comp,SWT.DOUBLE_BUFFERED);
		gd = new GridData( GridData.FILL_HORIZONTAL );
		torrent_info.setLayoutData(gd);

			// source selection

		label = new Label( bottom_comp, SWT.NULL );
		label.setText( MessageText.getString( "ConfigView.section.connection.group.peersources" ) + ":" );

		Composite sources_comp = new Composite( bottom_comp, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		sources_comp.setLayoutData(gd);

		source_buttons = new Button[PEPeerSource.PS_SOURCES.length];

		sources_comp.setLayout( removeMargins( new GridLayout( source_buttons.length + 1, false )));


		for ( int i=0; i<source_buttons.length; i++){

			final String src = PEPeerSource.PS_SOURCES[i];

			String msg_text = "ConfigView.section.connection.peersource." + src;

			Button button = new Button(sources_comp, SWT.CHECK);
			Messages.setLanguageText(button, msg_text);

			source_buttons[i] = button;

			button.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean selected = ((Button)e.widget).getSelection();

					if ( current_dm != null ){
						current_dm.getDownloadState().setPeerSourceEnabled(src,selected);
					}
				}
			});

			GridData gridData = new GridData();
			button.setLayoutData(gridData);
		}

			// IP Filter

		label = new Label( bottom_comp, SWT.NULL );
		label.setText( MessageText.getString( "label.ip.filter" ) + ":");

		Composite ipfilter_comp = new Composite( bottom_comp, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		ipfilter_comp.setLayoutData(gd);
		ipfilter_comp.setLayout( removeMargins( new GridLayout( 2, false )));


		ipfilter_enabled = new Button( ipfilter_comp, SWT.CHECK );
		ipfilter_enabled.setText( MessageText.getString( "label.enabled" ));

		ipfilter_enabled.addSelectionListener(
			new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					current_dm.getDownloadState().setFlag( DownloadManagerState.FLAG_DISABLE_IP_FILTER, !ipfilter_enabled.getSelection());
				}
			});

		gd = new GridData( GridData.FILL_HORIZONTAL );
		ipfilter_enabled.setLayoutData(gd);

		// VPN Info

		label = new Label( bottom_comp, SWT.NULL );
		label.setText( MessageText.getString( "label.vpn.status" ) + ":" );

		Composite vpn_comp = new Composite( bottom_comp, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		vpn_comp.setLayoutData(gd);
		vpn_comp.setLayout( removeMargins( new GridLayout( 2, false )));

		vpn_info = new BufferedLabel(vpn_comp,SWT.DOUBLE_BUFFERED);
		gd = new GridData( GridData.FILL_HORIZONTAL );
		vpn_info.setLayoutData(gd);

			// SOCKS Info

		label = new Label( bottom_comp, SWT.NULL );
		label.setText( MessageText.getString(  "label.socks.status" ) + ":" );

		Composite socks_comp = new Composite( bottom_comp, SWT.NULL );

		gd = new GridData( GridData.FILL_HORIZONTAL );
		socks_comp.setLayoutData(gd);
		socks_comp.setLayout( removeMargins( new GridLayout( 10, false )));

		label = new Label(socks_comp,SWT.NULL);
		label.setText( MessageText.getString( "label.proxy" ) + ":" );

		socks_state =  new BufferedLabel(socks_comp,SWT.DOUBLE_BUFFERED);
		gd = new GridData();
		gd.widthHint = 120;
		socks_state.setLayoutData(gd);

		// current details

		label = new Label(socks_comp,SWT.NULL);
		label.setText( MessageText.getString( "PeersView.state" ) + ":" );

		socks_current =  new BufferedLabel(socks_comp,SWT.DOUBLE_BUFFERED);
		gd = new GridData();
		gd.widthHint = 120;
		socks_current.setLayoutData(gd);

		// fail details

		label = new Label(socks_comp,SWT.NULL);
		label.setText( MessageText.getString( "label.fails" ) + ":" );

		socks_fails =  new BufferedLabel(socks_comp,SWT.DOUBLE_BUFFERED);
		gd = new GridData();
		gd.widthHint = 120;
		socks_fails.setLayoutData(gd);

		// more info

		label = new Label(socks_comp,SWT.NULL);

		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalAlignment = GridData.HORIZONTAL_ALIGN_BEGINNING;
		socks_more  =  new Label(socks_comp, SWT.NULL );
		socks_more.setText( MessageText.getString( "label.more") + "..." );
		socks_more.setLayoutData(gd);
		socks_more.setCursor(socks_more.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		Utils.setLinkForeground(socks_more);
		socks_more.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent arg0) {
				showSOCKSInfo();
			}
			@Override
			public void mouseUp(MouseEvent arg0) {
				showSOCKSInfo();
			}
		});

			// the rest

		sc.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = sc.getClientArea();
				Point size = cMainComposite.computeSize(r.width, SWT.DEFAULT);
				sc.setMinSize(size);
			}
		});

		swt_updateFields( null, current_dm );

		updatePeersEtc( current_dm );

		updateVPNSocks();

		Rectangle r = sc.getClientArea();
		Point size = cMainComposite.computeSize(r.width, SWT.DEFAULT);
		sc.setMinSize(size);

		Utils.relayout(cMainComposite);
	}

	private static void
	injectResult(
		Download		download,
		int[]			result )
	{
		DownloadScrapeResult scrape = download.getAggregatedScrapeResult();
		
		if ( scrape.getResponseType() == DownloadScrapeResult.RT_SUCCESS ){
			
			int total = scrape.getNonSeedCount() + scrape.getSeedCount();
			
			if ( total >= result[0] + result[1] ){
				
				return;
			}
		}
		
		download.setScrapeResult(
			new DownloadScrapeResult()
			{
				@Override
				public Download
				getDownload()
				{
					return( null );
				}
				
				@Override
				public int
				getResponseType()
				{
					return( RT_SUCCESS );
				}
				
				@Override
				public int
				getSeedCount()
				{
					return( result[0] );
				}
				
				@Override
				public int
				getNonSeedCount()
				{
					return( result[1] );
				}

				@Override
				public long
				getScrapeStartTime()
				{
					return( SystemTime.getCurrentTime());
				}
					
				@Override
				public void
				setNextScrapeStartTime(
					long nextScrapeStartTime)
				{
				}
				
				@Override
				public long
				getNextScrapeStartTime()
				{
					return( 0 );
				}
				
				@Override
				public String
				getStatus()
				{
					return( "OK" );
				}

				@Override
				public URL
				getURL()
				{
					try{
						return( new URL( "dht:" ));
						
					}catch( Throwable e ){
						
						return( null );
					}
				}
			});
	}
	
	private void
	setPrivacyLevel(
		final int		level )
	{
		if ( level != privacy_level ){

			Utils.execSWTThread(new AERunnable() {
				@Override
				public void
				runSupport()
				{
					if ( level == privacy_level ){

						return;
					}

					privacy_level = level;

					DownloadManager dm = current_dm;

					if ( dm == null ){

						return;
					}

					DownloadManagerState state = dm.getDownloadState();

					String[] new_nets;

					if ( level == PL_PUBLIC ){

						new_nets = new String[]{ AENetworkClassifier.AT_PUBLIC };

					}else if ( level == PL_MIX ){

						new_nets = AENetworkClassifier.AT_NETWORKS;

					}else if ( level == PL_ANONYMOUS ){

						new_nets = AENetworkClassifier.AT_NON_PUBLIC;

					}else{

						new_nets = new String[0];
					}

						// this will result in setupNetworksAndSources being called

					state.setNetworks( new_nets );

					if ( level != PL_PUBLIC ){

						if ( !I2PHelpers.isI2PInstalled()){

							if ( !i2p_install_prompted ){

								i2p_install_prompted = true;

								I2PHelpers.installI2PHelper(
									null, null,
									new Runnable()
									{
										@Override
										public void
										run()
										{
											Utils.execSWTThread(new AERunnable(){
												@Override
												public void runSupport()
												{
													current_i2p_entry.updateState();
												}});
										}
									});
							}
						}
					}
				}
			});
		}
	}

	private void
	swt_updateFields(
		DownloadManager		old_dm,
		DownloadManager		new_dm )
	{
		if ( cMainComposite == null || cMainComposite.isDisposed()){

			return;
		}

		byte[] hash = null;

		if ( new_dm != null ){

			TOTorrent torrent = new_dm.getTorrent();

			if ( torrent != null ){

				try{
					hash = torrent.getHash();

				}catch( Throwable e ){

				}
			}
		}

		current_i2p_entry.reset( hash );

		if ( old_dm != null ){

			DownloadManagerState state = old_dm.getDownloadState();

			state.removeListener( this, DownloadManagerState.AT_NETWORKS, DownloadManagerStateAttributeListener.WRITTEN );
			state.removeListener( this, DownloadManagerState.AT_PEER_SOURCES, DownloadManagerStateAttributeListener.WRITTEN );
			state.removeListener( this, DownloadManagerState.AT_FLAGS, DownloadManagerStateAttributeListener.WRITTEN );
		}

		if ( new_dm != null ){

			DownloadManagerState state = new_dm.getDownloadState();

			state.addListener( this, DownloadManagerState.AT_NETWORKS, DownloadManagerStateAttributeListener.WRITTEN );
			state.addListener( this, DownloadManagerState.AT_PEER_SOURCES, DownloadManagerStateAttributeListener.WRITTEN );
			state.addListener( this, DownloadManagerState.AT_FLAGS, DownloadManagerStateAttributeListener.WRITTEN );

			setupNetworksAndSources( new_dm );

			setupTorrentTracker( new_dm );

		}else{

			setupNetworksAndSources( null );

			setupTorrentTracker( null );
		}
	}

	private void
	setupNetworksAndSources(
		final DownloadManager	dm )
	{
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport(){

				enabled_networks.clear();
				enabled_sources.clear();

				if ( network_buttons == null || network_buttons[0].isDisposed()){

					return;
				}

				DownloadManagerState state	= null;

				String[]	networks 	= null;
				String[]	sources		= null;

				if ( dm != null ){

					state = dm.getDownloadState();

					networks 	= state.getNetworks();
					sources		= state.getPeerSources();
				}

				privacy_scale.setEnabled( networks != null );

				if ( networks != null ){

					enabled_networks.addAll( Arrays.asList( networks ));

					int pl;

					if ( enabled_networks.contains( AENetworkClassifier.AT_PUBLIC )){

						if ( enabled_networks.size() == 1 ){

							pl = PL_PUBLIC;
						}else{

							pl = PL_MIX;
						}
					}else{

						if ( enabled_networks.size() == 0 ){

							pl = PL_INVALID;

						}else{

							pl = PL_ANONYMOUS;
						}
					}

					privacy_level	= pl;

					privacy_scale.setSelection( pl*10 );
				}

				for ( int i=0; i<AENetworkClassifier.AT_NETWORKS.length; i++){

					final String net = AENetworkClassifier.AT_NETWORKS[i];

					network_buttons[i].setEnabled( networks != null );

					network_buttons[i].setSelection( enabled_networks.contains ( net ));
				}


				if ( sources != null ){

					enabled_sources.addAll( Arrays.asList( sources ));
				}

				for ( int i=0; i<PEPeerSource.PS_SOURCES.length; i++){

					final String source = PEPeerSource.PS_SOURCES[i];

					source_buttons[i].setEnabled( sources != null && state.isPeerSourcePermitted( source ));

					source_buttons[i].setSelection( enabled_sources.contains ( source ));
				}

				if ( state != null ){

					ipfilter_enabled.setEnabled( true );

					ipfilter_enabled.setSelection( !state.getFlag( DownloadManagerState.FLAG_DISABLE_IP_FILTER ));

				}else{

					ipfilter_enabled.setEnabled( false );
				}
					// update info about which trackers etc are enabled

				setupTorrentTracker( dm );
			}
		});
	}

	private void
	setupTorrentTracker(
		final DownloadManager	dm  )
	{
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport(){
				if ( torrent_info == null || torrent_info.isDisposed()){

					return;
				}

				TOTorrent torrent = dm==null?null:dm.getTorrent();

				if ( torrent == null ){

					torrent_info.setText( "" );
					tracker_info.setText( "" );
					webseed_info.setText( "" );

					return;
				}

				boolean private_torrent = torrent.getPrivate();

				torrent_info.setText( MessageText.getString( private_torrent?"label.private":"subs.prop.is_public" ));

				boolean		decentralised 	= false;

				Set<String>	tracker_nets	= new HashSet<>();

				URL	announce_url = torrent.getAnnounceURL();

				if ( announce_url != null ){

					if ( TorrentUtils.isDecentralised(announce_url)){

						decentralised = true;

					}else{

						String net = AENetworkClassifier.categoriseAddress( announce_url.getHost());

						tracker_nets.add( net );
					}
				}

				TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

				TOTorrentAnnounceURLSet[]	sets = group.getAnnounceURLSets();

				for ( TOTorrentAnnounceURLSet set: sets ){

					URL[]	urls = set.getAnnounceURLs();

					for ( URL u: urls ){

						if ( TorrentUtils.isDecentralised( u)){

							decentralised = true;

						}else{

							String net = AENetworkClassifier.categoriseAddress( u.getHost());

							tracker_nets.add( net );
						}
					}
				}

				boolean	tracker_source_enabled 	= enabled_sources.contains( PEPeerSource.PS_BT_TRACKER );
				boolean	dht_source_enabled 		= enabled_sources.contains( PEPeerSource.PS_DHT );

				String tracker_str = "";

				tracker_str = MessageText.getString( "label.decentralised" );

				String disabled_str = MessageText.getString("label.disabled");

				String net_string = "";

				if ( dht_source_enabled && !private_torrent ){

						// dht only applicable to non-private torrents

					for ( String net: new String[]{ AENetworkClassifier.AT_PUBLIC, AENetworkClassifier.AT_I2P }){

						if ( enabled_networks.contains( net )){

							net_string += (net_string.length()==0?"":", ") + net;
						}
					}
				}

				if ( net_string.length() == 0 ){

					tracker_str += " (" + disabled_str + ")";

				}else{

					tracker_str += " [" + net_string + "]";
				}

				for ( String net: tracker_nets ){

					if ( !tracker_source_enabled || !enabled_networks.contains( net )){

						net += " (" + disabled_str + ")";
					}

					tracker_str += (tracker_str.length()==0?"":", " ) + net;
				}

				tracker_info.setText( tracker_str );

					// web seeds

				Set<String>	webseed_nets	= new HashSet<>();

				ExternalSeedPlugin esp = DownloadManagerController.getExternalSeedPlugin();

				if ( esp != null ){

					ExternalSeedReader[] seeds = esp.getManualWebSeeds( PluginCoreUtils.wrap( torrent ));

					if ( seeds != null ){

						for ( ExternalSeedReader seed: seeds ){

							URL u = seed.getURL();

							String net = AENetworkClassifier.categoriseAddress( u.getHost());

							webseed_nets.add( net );
						}
					}
				}

				String webseeds_str = "";

				if ( webseed_nets.isEmpty()){

					webseeds_str = MessageText.getString("label.none");

				}else{

					for ( String net: webseed_nets ){

						if ( !enabled_networks.contains( net )){

							net += " (" + disabled_str + ")";
						}

						webseeds_str += (webseeds_str.length()==0?"":", " ) + net;
					}
				}

				webseed_info.setText( webseeds_str );
			}
		});
	}

	private void
	updatePeersEtc(
		final DownloadManager		dm )
	{
		final PEPeerManager pm;

		if ( dm != null ){

			pm = dm.getPeerManager();

		}else{

			pm = null;
		}

		Utils.execSWTThread(new AERunnable(){
			@Override
			public void runSupport()
			{
				if ( peer_info == null || peer_info.isDisposed()){

					return;
				}

				if ( pm == null ){

					peer_info.setText( dm==null?"":MessageText.getString( "privacy.view.dl.not.running" ));

				}else{

				    AEProxySelector proxy_selector = AEProxySelectorFactory.getSelector();

				    Proxy proxy = proxy_selector.getActiveProxy();

				    boolean socks_bad_incoming = false;

					List<PEPeer> peers = pm.getPeers();

					String[] all_nets = AENetworkClassifier.AT_NETWORKS;

					int[]	 counts = new int[ all_nets.length];

					int	incoming 			= 0;
					int outgoing			= 0;
					int outgoing_connected	= 0;

					for ( PEPeer peer: peers ){

						String net = PeerUtils.getNetwork( peer );

						for ( int i=0;i<all_nets.length;i++ ){

							if ( all_nets[i] == net ){

								counts[i]++;

								break;
							}
						}

						boolean is_incoming = peer.isIncoming();

						if ( is_incoming ){

							incoming++;

						}else{

							outgoing++;

							if ( peer.getPeerState() == PEPeer.TRANSFERING ){

								outgoing_connected++;
							}
						}

						if ( proxy != null ){

							if ( is_incoming ){

								if ( !peer.isLANLocal()){

									try{
										if ( InetAddress.getByAddress( HostNameToIPResolver.hostAddressToBytes( peer.getIp())).isLoopbackAddress()){

											continue;
										}
									}catch( Throwable e ){
									}

									socks_bad_incoming = true;

									break;
								}
							}
						}
					}

					String str = "";

					for ( int i=0;i<all_nets.length;i++ ){

						int num = counts[i];

						if ( num > 0 ){

							str += (str.length()==0?"":", ") + all_nets[i] + "=" + num;
						}
					}

					if ( str.length() == 0 ){

						str = MessageText.getString( "privacy.view.no.peers" );

					}else{

						str += 	", " + MessageText.getString( "label.incoming" ) + "=" + incoming +
								", " + MessageText.getString( "label.outgoing" ) + "=" + outgoing_connected + "/" + outgoing;
					}

					if ( socks_bad_incoming ){

						str += " (" + MessageText.getString( "privacy.view.non.local.peer" ) + ")";
					}

					peer_info.setText( str );
				}

				updateVPNSocks();
			}
		});
	}

	private void
	updateVPNSocks()
	{
	    AEProxySelector proxy_selector = AEProxySelectorFactory.getSelector();

	    Proxy proxy = proxy_selector.getActiveProxy();

	    socks_more.setEnabled( proxy != null );

	    if ( Constants.isOSX ){

	    	socks_more.setForeground(proxy==null?Colors.light_grey:Colors.blue);
	    }

	    socks_state.setText( proxy==null?MessageText.getString( "label.inactive" ): ((InetSocketAddress)proxy.address()).getHostName());

	    if ( proxy == null ){

	    	socks_current.setText( "" );

	    	socks_fails.setText( "" );

	    }else{

		    long	last_con 	= proxy_selector.getLastConnectionTime();
		    long	last_fail 	= proxy_selector.getLastFailTime();
		    int		total_cons	= proxy_selector.getConnectionCount();
		    int		total_fails	= proxy_selector.getFailCount();

		    long	now = SystemTime.getMonotonousTime();

		    long	con_ago		= now - last_con;
		    long	fail_ago 	= now - last_fail;

		    String	state_str;

		    if ( last_fail < 0 ){

		    	state_str = "PeerManager.status.ok";

		    }else{

			    if ( fail_ago > 60*1000 ){

			    	if ( con_ago < fail_ago ){

			    		state_str = "PeerManager.status.ok";

			    	}else{

			    		state_str = "SpeedView.stats.unknown";
			    	}
			    }else{

			    	state_str = "ManagerItem.error";
			    }
		    }

		    socks_current.setText( MessageText.getString( state_str ) + ", con=" + total_cons );

		    long	fail_ago_secs = fail_ago/1000;

		    if ( fail_ago_secs == 0 ){

		    	fail_ago_secs = 1;
		    }

		    socks_fails.setText( last_fail<0?"":(DisplayFormatters.formatETA( fail_ago_secs, false ) + " " + MessageText.getString( "label.ago" ) + ", tot=" + total_fails ));
	    }

	    String info = NetworkAdmin.getSingleton().getBindStatus();
	    
	    String[] lines = info.split( "\n" );
	    
	    vpn_info.setText( lines.length==0?"":lines[0].trim());
	}

	@Override
	public void
	attributeEventOccurred(
		DownloadManager 	download,
		String 				attribute,
		int 				event_type )
	{
		setupNetworksAndSources( download );
	}

	private void
	showSOCKSInfo()
	{
		AEProxySelector proxy_selector = AEProxySelectorFactory.getSelector();

		String	info = proxy_selector.getInfo();

		TextViewerWindow viewer = new TextViewerWindow(
			MessageText.getString( "proxy.info.title" ),
			null,
			info, false  );

	}

	private GridLayout
	removeMarginsAndSpacing(
		GridLayout	layout )
	{
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;

		return( layout );
	}

	private GridLayout
	removeMargins(
		GridLayout	layout )
	{
		layout.marginHeight = 0;
		layout.marginWidth = 0;

		return( layout );
	}
	
	private class
	I2PStackItem
	{
		private Composite	i2p_group;
		
		private Composite	i2p_lookup_comp;
		private Button 		i2p_install_button;
		private Button 		i2p_lookup_button;
		private Label 		i2p_options_link;

		private BufferedLabel 	i2p_result_summary;
		private Text			i2p_result_list;
		
		private byte[]	current_hash;
		private boolean	lookup_performed;
		private boolean	lookup_active;
		
		private long	last_used		= SystemTime.getMonotonousTime();
		
		I2PStackItem(
			Composite		parent,
			byte[]			hash )
		{
			current_hash = hash;
			
			i2p_group = new Composite( parent, SWT.NULL );
			
			GridData gd = new GridData( GridData.FILL_HORIZONTAL );
			i2p_group.setLayoutData(gd);

			i2p_group.setLayout( new GridLayout(4, false ));

			i2p_group.setData( "item", this );
			
			Label label = new Label( i2p_group, SWT.NULL );
			label.setText( MessageText.getString( "privacy.view.lookup.info" ));
			gd = new GridData();
			gd.horizontalSpan = 2;
			label.setLayoutData(gd);

			label = new Label( i2p_group, SWT.NULL );
			label.setText( MessageText.getString( "label.lookup.status" ) + ":" );


			i2p_result_summary = new BufferedLabel(i2p_group,SWT.DOUBLE_BUFFERED);
			gd = new GridData( GridData.FILL_HORIZONTAL );
			//gd.horizontalIndent = 4;
			i2p_result_summary.setLayoutData(gd);

			Composite i2p_button_comp = new Composite( i2p_group, SWT.NULL );
			i2p_button_comp.setLayout( new GridLayout(2, false ));

			gd = new GridData( GridData.FILL_VERTICAL );
			i2p_button_comp.setLayoutData(gd);

			label = new Label( i2p_button_comp, SWT.NULL );
			label.setText( MessageText.getString( "GeneralView.section.availability" ));

			i2p_install_button = new Button( i2p_button_comp, SWT.PUSH );

			i2p_install_button.addSelectionListener(
				new SelectionAdapter() {

					@Override
					public void
					widgetSelected(
						SelectionEvent event )
					{
						final boolean[] result = { false };

						I2PHelpers.installI2PHelper(
							null, result,
							new Runnable()
							{
								@Override
								public void
								run()
								{
									Utils.execSWTThread(
										new Runnable()
										{
											@Override
											public void
											run()
											{
												current_i2p_entry.updateState();
											}
										});
								}
							});
					}
				});

				// I2P peer lookup

			i2p_lookup_comp = new Composite( i2p_group, SWT.BORDER );

			gd = new GridData();
			gd.widthHint = 300;
			gd.heightHint = 150;
			i2p_lookup_comp.setLayoutData(gd);

			i2p_lookup_comp.setBackground( Colors.white );

				// i2p results


			i2p_result_list = new Text( i2p_group, SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL | SWT.WRAP | SWT.NO_FOCUS );
			gd = new GridData( GridData.FILL_BOTH );
			gd.horizontalSpan = 2;
			i2p_result_list.setLayoutData(gd);

			i2p_result_list.setEditable( false );

				// i2p lookup button

			label = new Label( i2p_button_comp, SWT.NULL );
			label.setText( MessageText.getString( "button.lookup.peers" ));

			i2p_lookup_button = new Button( i2p_button_comp, SWT.PUSH );

			i2p_lookup_button.setText( MessageText.getString( "button.search.dht" ));

			i2p_lookup_button.addSelectionListener(
				new SelectionAdapter(){

					private int	search_count;

					@Override
					public void
					widgetSelected(
						SelectionEvent event )
					{
						Utils.disposeComposite( i2p_lookup_comp, false );

						i2p_result_summary.setText( "" );

						i2p_result_list.setText( "" );

						PluginInterface i2p_pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "azneti2phelper", true );

						if ( i2p_pi != null ){

							IPCInterface ipc = i2p_pi.getIPC();

							Map<String,Object>	options = new HashMap<>();

							options.put( "server_id", "Scraper" );
							options.put( "server_id_transient", true );
							options.put( "ui_composite", i2p_lookup_comp );

							final byte[] hash = current_hash;

							search_count++;

							final int	search_id = search_count;
							
							IPCInterface callback =
								new IPCInterface()
								{
									final int[] result = { -1, -1 };

									@Override
									public Object
									invoke(
										String 			methodName,
										final Object[] 	params)

										throws IPCException
									{
										if ( search_id != search_count ){

											return( null );
										}

										if ( methodName.equals( "statusUpdate" )){

											final int status = (Integer)params[0];

											if ( 	status != TrackerPeerSource.ST_INITIALISING &&
													status != TrackerPeerSource.ST_UPDATING ){

												Utils.execSWTThread(
													new Runnable()
													{
														@Override
														public void
														run()
														{
															if ( i2p_group.isDisposed() || !Arrays.equals( hash, current_hash )){

																return;
															}

															setLookupActive( false );

															synchronized( result ){
															
																if ( result[0] != -1 ){
															
																	try{
																	
																		Download d = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getDownloadManager().getDownload( hash );
																		
																		if ( d != null ){
																			
																			injectResult( d, result );
																		}
																	}catch( Throwable e ){
																		
																	}
																}
															}
															
															if ( 	i2p_result_list.getText().length() == 0 &&
																	status != TrackerPeerSource.ST_UNAVAILABLE){

																i2p_result_summary.setText( MessageText.getString( "label.no.peers.found" ));
															}
														}
													});
											}

											if ( params.length == 4 ){

												Utils.execSWTThread(
													new Runnable()
													{
														@Override
														public void
														run()
														{
															if ( i2p_group.isDisposed() || !Arrays.equals( hash, current_hash )){

																return;
															}

															int	seeds		= (Integer)params[1];
															int	leechers	= (Integer)params[2];
															int	peers		= (Integer)params[3];

															synchronized( result ){
																
																result[0] = seeds;
																result[1] = leechers;
															}
															
															i2p_result_summary.setText(
																MessageText.getString(
																	"privacy.view.lookup.msg",
																	new String[]{
																		String.valueOf( seeds ),
																		String.valueOf( leechers ),
																		String.valueOf( peers )}));
														}
													});
											}

										}else if ( methodName.equals( "msgUpdate" )){

											Utils.execSWTThread(
												new Runnable()
												{
													@Override
													public void
													run()
													{
														if ( i2p_group.isDisposed() || !Arrays.equals( hash, current_hash )){

															return;
														}

														String	msg		= (String)params[0];

														i2p_result_summary.setText( msg );
													}
												});

										}else if ( methodName.equals( "peerFound")){

											Utils.execSWTThread(
												new Runnable()
												{
													@Override
													public void
													run()
													{
														if ( i2p_group.isDisposed() || !Arrays.equals( hash, current_hash )){

															return;
														}

														String 	host		= (String)params[0];
														int		peer_type 	= (Integer)params[1];

														i2p_result_list.append( host + "\r\n" );
													}
												});

										}

										return( null );
									}

									@Override
									public boolean
									canInvoke(
										String methodName,
										Object[] params )
									{
										return( true );
									}
								};


							setLookupActive( true );

							i2p_result_summary.setText( MessageText.getString( "label.searching" ));

							try{
								ipc.invoke(
									"lookupTorrent",
									new Object[]{
										"",
										hash,
										options,
										callback
									});

							}catch( Throwable e ){

								setLookupActive( false );

								e.printStackTrace();
							}
						}
					}
				});

			Label i2p_options_info = new Label( i2p_button_comp, SWT.WRAP );
			gd = new GridData( GridData.FILL_HORIZONTAL );
			gd.horizontalSpan = 2;
			gd.widthHint = 150;
			i2p_options_info.setLayoutData(gd);

			i2p_options_info.setText( MessageText.getString( "privacy.view.check.bw.info" ));

			if ( !COConfigurationManager.getBooleanParameter( "privacy.view.check.bw.clicked", false )){

				FontData fontData = i2p_options_info.getFont().getFontData()[0];
				final Font bold_font = new Font( i2p_options_info.getDisplay(), new FontData( fontData.getName(), fontData.getHeight(), SWT.BOLD ));
				i2p_options_info.setFont( bold_font);

				i2p_options_info.addDisposeListener(
					new DisposeListener() {
						@Override
						public void widgetDisposed(DisposeEvent e) {
							bold_font.dispose();
						}
					});
			}

			i2p_options_link = new Label( i2p_button_comp, SWT.NULL );
			gd = new GridData( GridData.FILL_HORIZONTAL );
			gd.horizontalSpan = 2;
			i2p_options_link.setLayoutData(gd);
			i2p_options_link.setText( MessageText.getString( "privacy.view.check.bw" ));

			i2p_options_link.setCursor(i2p_options_link.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
			Utils.setLinkForeground(i2p_options_link);
			i2p_options_link.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent arg0) {
					openOptions();
				}
				@Override
				public void mouseUp(MouseEvent arg0) {
					openOptions();
				}

				private void
				openOptions()
				{
					COConfigurationManager.setParameter( "privacy.view.check.bw.clicked", true );

					UIFunctions uif = UIFunctionsManager.getUIFunctions();

					if ( uif != null ){

						uif.getMDI().showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
								"azi2phelper.name" );
					}
				}
			});

			updateState();

			Utils.makeButtonsEqualWidth( Arrays.asList( i2p_install_button, i2p_lookup_button));

			label = new Label( i2p_button_comp, SWT.NULL );
			gd = new GridData( GridData.FILL_BOTH );
			gd.horizontalSpan = 2;
			label.setLayoutData(gd);

		}
		
		private Composite
		getComposite()
		{
			return( i2p_group );
		}
		
		private void
		setLookupActive(
			boolean		active )
		{
			lookup_active = active;
			
			if ( active ){
				
				lookup_performed = true;
			}
			
			i2p_lookup_button.setEnabled( !active );
		}
		
		private void
		reset(
			byte[]		hash )
		{
			if ( Arrays.equals( hash,  current_hash )){
								
				updateState();
				
				return;
			}
			
				
			if ( hash != null ){
				
					// see if we have cached state
				
				Control[] kids = i2p_stack_group.getChildren();
				
				for ( Control c: kids ){
					
					I2PStackItem item = (I2PStackItem)c.getData( "item" );
					
					if ( item  != null && Arrays.equals( item.current_hash,  hash )){
						
						current_i2p_entry = item;
						
						stack_layout.topControl = c;
						
						i2p_stack_group.layout( true, true );
						
						item.updateState();
						
						return;
					}
				}
			}
				
				
			if ( lookup_performed ){
					
					// save this one's state by using a new one
				
				Control[] kids = i2p_stack_group.getChildren();
				
				long	oldest_time = SystemTime.getMonotonousTime();
				Control	oldest_comp	= null;
				
				int	num = 0;
				
				for ( Control c: kids ){
					
					I2PStackItem item = (I2PStackItem)c.getData( "item" );
					
					if ( item != null ){
						
						num++;
						
						if ( item.last_used < oldest_time ){
							
							oldest_time		= item.last_used;
							oldest_comp		= c;
						}
					}
				}
				
				if ( num > MAX_LOOKUP_HISTORY ){
					
					oldest_comp.dispose();
				}
				
				current_i2p_entry = new I2PStackItem( i2p_stack_group, hash );
		        
				stack_layout.topControl = current_i2p_entry.getComposite();

				i2p_stack_group.layout( true, true );
				
			}else{
				
				current_hash = hash;
					
				updateState();
	
				Utils.disposeComposite( i2p_lookup_comp, false );
	
				i2p_result_summary.setText( "" );
				
				i2p_result_list.setText( "" );
			}
		}
		
		private void
		updateState()
		{
			last_used = SystemTime.getMonotonousTime();
			
			boolean i2p_installed = I2PHelpers.isI2PInstalled();

			i2p_install_button.setText( MessageText.getString( i2p_installed?"devices.installed":"privacy.view.install.i2p" ));

			i2p_install_button.setEnabled( !i2p_installed );

			i2p_lookup_button.setEnabled( i2p_installed && current_hash != null && !lookup_active );

			i2p_options_link.setEnabled( i2p_installed );
		}
	}
}
