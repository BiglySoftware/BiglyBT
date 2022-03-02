package com.biglybt.ui.swt.views;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.speedmanager.SpeedLimitHandler;
import com.biglybt.core.tag.TagGroup;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.peer.PeerFilesView;
import com.biglybt.ui.swt.views.peer.PeerInfoView;
import com.biglybt.ui.swt.views.peer.RemotePieceDistributionView;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.peers.*;

import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.tables.TableManager;

public abstract class 
PeersViewBase
	extends TableViewTab<PEPeer>
	implements UISWTViewCoreEventListener, TableLifeCycleListener, TableViewSWTMenuFillListener, TableSelectionListener
{

	public static final Class<Peer> PLUGIN_DS_TYPE = Peer.class;

	static TableColumnCore[] getBasicColumnItems(String table_id) {
		return new TableColumnCore[] {
				new IpItem(table_id),
				new ClientItem(table_id),
				new TypeItem(table_id),
				new MessagingItem(table_id),
				new EncryptionItem(table_id),
				new ProtocolItem(table_id),
				new PiecesItem(table_id),
				new PercentItem(table_id),
				new PercentHaveWeNeedItem(table_id),
				new DownSpeedItem(table_id),
				new UpSpeedItem(table_id),
				new PeerSourceItem(table_id),
				new HostNameItem(table_id),
				new PortItem(table_id),
				new InterestedItem(table_id),
				new ChokedItem(table_id),
				new DownItem(table_id),
				new InterestingItem(table_id),
				new ChokingItem(table_id),
				new OptimisticUnchokeItem(table_id),
				new UpItem(table_id),
				new UpDownRatioItem(table_id),
				new GainItem(table_id),
				new StatUpItem(table_id),
				new SnubbedItem(table_id),
				new TotalDownSpeedItem(table_id),
				new TimeUntilCompleteItem(table_id),
				new DiscardedItem(table_id),
				new UniquePieceItem(table_id),
				new TimeToSendPieceItem(table_id),
				new DLedFromOthersItem(table_id),
				new UpRatioItem(table_id),
				new StateItem(table_id),
				new ConnectedTimeItem(table_id),
				new LatencyItem(table_id),
				new PieceItem(table_id),
				new IncomingRequestCountItem(table_id),
				new OutgoingRequestCountItem(table_id),
				new UpSpeedLimitItem(table_id),
				new DownSpeedLimitItem(table_id),
				new LANItem(table_id),
				new PeerIDItem(table_id),
				new PeerByteIDItem(table_id),
				new HandshakeReservedBytesItem(table_id),
				new ClientIdentificationItem(table_id),
				new ASItem(table_id),
				new IndexItem(table_id),
				new ColumnPeerNetwork(table_id),
				new LocalInterfaceItem(table_id),
		};
	}

	private static final TableColumnCore[] basicItems = getBasicColumnItems(TableManager.TABLE_TORRENT_PEERS);

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TableManager.TABLE_TORRENT_PEERS, basicItems );
	}
	
	protected TableViewSWT<PEPeer> tv;
	
	protected Shell shell;

	private boolean				swarm_view_enable;
	private boolean				local_peer_enable = true;
	
	private PeersGraphicView 	swarm_view;
	private Set<PEPeer>			swarm_peers = new HashSet<>();
	private volatile boolean	peers_changed;

	private volatile boolean	show_local_peer;

	protected
	PeersViewBase(
		String		id,
		boolean		enable_swarm_view )
	{
		super( id );
		
		swarm_view_enable = enable_swarm_view;
	}

	@Override
	public Composite 
	initComposite(
		Composite composite) 
	{
		if ( swarm_view_enable ) {
			Composite parent = new Composite(composite, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.marginHeight = layout.marginWidth = 0;
			parent.setLayout(layout);
	
			Layout compositeLayout = composite.getLayout();
			if (compositeLayout instanceof GridLayout) {
				parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			} else if (compositeLayout instanceof FormLayout) {
				parent.setLayoutData(Utils.getFilledFormData());
			}	
			
			final CTabFolder tab_folder = new CTabFolder( parent, SWT.NONE );
			tab_folder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			final CTabItem tab1 = new CTabItem(tab_folder, SWT.NONE);
			
			Messages.setLanguageText( tab1, "label.table" );
			
			Composite tableParent = new Composite(tab_folder, SWT.NONE);
	
			tableParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			GridLayout gridLayout = new GridLayout();
			gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			tableParent.setLayout(gridLayout);
	
			tab1.setControl( tableParent );
			
			
			final CTabItem tab2 = new CTabItem(tab_folder, SWT.NONE);
			
			Messages.setLanguageText( tab2, "label.swarms" );
			
			final Composite swarmParent = new Composite(tab_folder, SWT.NONE);
	
			swarmParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			gridLayout = new GridLayout();
			gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			swarmParent.setLayout(gridLayout);
	
			tab2.setControl( swarmParent );
			
			tab_folder.setSelection( tab1 );
			
			tab_folder.addSelectionListener(
				new SelectionListener(){
					
					@Override
					public void widgetSelected(SelectionEvent arg0){
						if ( tab_folder.getSelection() == tab2 ) {
							Utils.disposeComposite(swarmParent,false);
							createSwarmsView( swarmParent );
						}else{
							if ( swarm_view != null ) {
								
								swarm_view.delete();
							}
							
							Utils.disposeComposite(swarmParent,false);
						}
					}
					
					@Override
					public void widgetDefaultSelected(SelectionEvent arg0){
						// TODO Auto-generated method stub
						
					}
				});
			
			return tableParent;
			
		}else if ( local_peer_enable ){
			
			Composite parent = new Composite(composite, SWT.NONE);
			GridLayout layout = new GridLayout(1,true);
			layout.marginHeight = layout.marginWidth = 0;
			layout.horizontalSpacing = layout.verticalSpacing = 0;
			parent.setLayout(layout);
	
			Layout compositeLayout = composite.getLayout();
			if (compositeLayout instanceof GridLayout) {
				parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			} else if (compositeLayout instanceof FormLayout) {
				parent.setLayoutData(Utils.getFilledFormData());
			}
			
			Composite header = new Composite(parent, SWT.NONE);
			layout = new GridLayout(1,true);
			layout.marginHeight = layout.marginWidth = 0;
			header.setLayout(layout);
			
			header.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

			Button lp_enable = new Button( header, SWT.CHECK );
			lp_enable.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));
			
			Messages.setLanguageText( lp_enable, "label.local.peer.show" );
			lp_enable.addListener( SWT.Selection, (ev)->{
				COConfigurationManager.setParameter( "Peers View Show Local Peer", lp_enable.getSelection());
			});
			
			COConfigurationManager.addAndFireParameterListener(
				"Peers View Show Local Peer",
				new ParameterListener(){
					public void
					parameterChanged(
						String n )
					{
						if ( lp_enable.isDisposed()){
							
							COConfigurationManager.removeParameterListener( n, this );
							
							return;
						}
						
						boolean enabled = COConfigurationManager.getBooleanParameter( n );
						
						lp_enable.setSelection( enabled );
						
						setShowLocalPeer( enabled );
					}
				});
			
			Composite tableParent = new Composite(parent, SWT.NONE);
			
			tableParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			layout = new GridLayout();
			layout.horizontalSpacing = layout.verticalSpacing = 0;
			layout.marginHeight = layout.marginWidth = 0;
			tableParent.setLayout(layout);

			return( tableParent );
			
		}else {
			
			return( super.initComposite(composite));
		}
	}

	protected boolean
	getShowLocalPeer()
	{
		return( show_local_peer );
	}
	
	protected void
	setShowLocalPeer(
		boolean		b )
	{	
		show_local_peer = b;
	}
	
	protected TableViewSWT<PEPeer> 
	initYourTableView(
		String table_id) 
	{
		if ( table_id == TableManager.TABLE_TORRENT_PEERS ){
			
			tv = TableViewFactory.createTableViewSWT(PLUGIN_DS_TYPE,
					TableManager.TABLE_TORRENT_PEERS, getPropertiesPrefix(), basicItems,
					"pieces", SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
			
		}else{
			
		  	TableColumnCore[] items = PeersView.getBasicColumnItems(TableManager.TABLE_ALL_PEERS);
		  	TableColumnCore[] basicItems = new TableColumnCore[items.length + 1];
		  	System.arraycopy(items, 0, basicItems, 0, items.length);
		  	basicItems[items.length] = new DownloadNameItem(TableManager.TABLE_ALL_PEERS);

			TableColumnManager tcManager = TableColumnManager.getInstance();

			tcManager.setDefaultColumnNames( TableManager.TABLE_ALL_PEERS, basicItems );

			tv = TableViewFactory.createTableViewSWT(PLUGIN_DS_TYPE,
					TableManager.TABLE_ALL_PEERS, getPropertiesPrefix(), basicItems,
					"connected_time", SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		}
		
		tv.setRowDefaultHeightEM(1);
		
		registerPluginViews();
		
		tv.addLifeCycleListener(this);
		
		tv.addMenuFillListener(this);
		
		tv.addSelectionListener(this, false);
		
		return tv;
	}	
	
	@Override
	public void
	tableViewTabInitComplete()
	{
		if ( tv.getParentDataSource() instanceof TagGroup ){
		
			tv.setEnabled( false );
		}
	}
	
	private static void registerPluginViews() {
		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		if (vm.areCoreViewsRegistered(PLUGIN_DS_TYPE)) {
			return;
		}

		vm.registerView(PLUGIN_DS_TYPE, new UISWTViewBuilderCore(
				"PeerInfoView", null, PeerInfoView.class));

		vm.registerView(PLUGIN_DS_TYPE,
				new UISWTViewBuilderCore("RemotePieceDistributionView", null,
						RemotePieceDistributionView.class));

		vm.registerView(PLUGIN_DS_TYPE, new UISWTViewBuilderCore(
				PeerFilesView.MSGID_PREFIX, null, PeerFilesView.class));

		vm.registerView(PLUGIN_DS_TYPE,
				new UISWTViewBuilderCore(LoggerView.VIEW_ID, null,
						LoggerView.class).setInitialDatasource(true));

		vm.setCoreViewsRegistered(PLUGIN_DS_TYPE);
	}	
	
	
	@Override
	public void 
	tableLifeCycleEventOccurred(
		TableView tv, int eventType,
		Map<String, Object> data) 
	{
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:{
				shell = this.tv.getComposite().getShell();
				break;
			}
		}
	}
	
	protected void
	addPeer(
		PEPeer		peer )
	{
		peers_changed = true;
		
		tv.addDataSource( peer );
	}
	
	protected void
	addPeers(
		PEPeer[]	peers )
	{
		peers_changed = true;
		
		tv.addDataSources( peers );
	}
	
	protected void
	removePeer(
		PEPeer		peer )
	{
		peers_changed = true;
		
		tv.removeDataSource( peer );
	}
	
	private void
	createSwarmsView(
		Composite	parent )
	{
		if ( swarm_view != null ) {
			
			swarm_view.delete();
		}
		
		swarm_view =
			new PeersGraphicView(
				new PeersGraphicView.PeerFilter()
				{
					@Override
					public boolean acceptPeer(PEPeer peer){
						return( swarm_peers.contains( peer ));
					}
				});
		
		swarm_view.setAlwaysShowDownloadName( true );
		
		swarm_view.initialize( parent );
		
		swarm_view.getComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		parent.getParent().layout( true, true );
		
		swarm_view.setFocused( true );
		
		peers_changed = true;
		
		updateSwarmPeers();
		
		swarm_view.refresh();
	}
	
	private void
	updateSwarmPeers()
	{
		if ( peers_changed ) {
			
			Utils.execSWTThread(
				new Runnable()
				{
					public void
					run()
					{
						peers_changed = false;
						
						GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
						
						swarm_peers = tv.getDataSources();
						
						final Map<PEPeerManager,int[]> done_pms = new HashMap<>();
						
						List<DownloadManager>	dms = new ArrayList<>();
						
						for ( PEPeer peer: swarm_peers ){
							
							PEPeerManager pm = peer.getManager();
							
							int[]	count = done_pms.get( pm );
							
							if  ( count == null ){
							
								done_pms.put( pm, new int[]{1} );
											
								byte[] hash = pm.getHash();
								
								DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash ));
								
								if ( dm != null ){
									
									dms.add( dm );
								}
							}else{
								
								count[0]++;
							}
						}
						
						Collections.sort(
							dms,
							new Comparator<DownloadManager>()
							{
								@Override
								public int 
								compare(
									DownloadManager o1, 
									DownloadManager o2)
								{
									PEPeerManager pm1 = o1.getPeerManager();
									PEPeerManager pm2 = o2.getPeerManager();
									
									int[] c1 = done_pms.get( pm1 );
									int[] c2 = done_pms.get( pm2 );
									
									int n1 = c1==null?0:c1[0];
									int n2 = c2==null?0:c2[0];
									
									return( n2 - n1);
								}
							});
							
						swarm_view.dataSourceChanged( dms.toArray( new DownloadManager[ dms.size()]));
					}
				});
		}
	}
	
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		
		switch( event.getType()){
			case UISWTViewEvent.TYPE_REFRESH:{
				if ( swarm_view != null ) {
					
					updateSwarmPeers();
					
					swarm_view.refresh();
				}
				break;
			}
			case UISWTViewEvent.TYPE_SHOWN:{
				if ( swarm_view != null ) {
					swarm_view.setFocused( true );
				}
				break;
			}				
			case UISWTViewEvent.TYPE_HIDDEN:{
				if ( swarm_view != null ) {
					swarm_view.setFocused( false );
				}
				break;
			}	
		}
		
		return( super.eventOccurred(event));
	}
	
		// Menu Stuff
	
	public static void
	fillMenu(
		Menu				menu,
		PEPeer				peer,
		DownloadManager 	download_specific )
	{
		PEPeer[] peers = {peer};

		fillMenu( menu, peers, menu.getShell(), download_specific );
	}

	public static void
	fillMenu(
		Menu 				menu,
		TableView<PEPeer> 	tv,
		Shell 				shell,
		DownloadManager		 download_specific )
	{
		List<PEPeer>	o_peers = (List<PEPeer>)(Object)tv.getSelectedDataSources();

		PEPeer[]	peers	= o_peers.toArray( new PEPeer[o_peers.size()]);

		fillMenu( menu, peers, shell, download_specific );

	}
	
	private static void
	fillMenu(
		final Menu 				menu,
		final PEPeer[]			peers,
		final Shell 			shell,
		DownloadManager 		download_specific )
	{
		boolean hasSelection = (peers.length > 0);


		final IdentityHashSet<DownloadManager>	download_managers = new IdentityHashSet<>();

		Map<PEPeer,DownloadManager>	peer_dm_map = new HashMap<>();
		
		boolean downSpeedDisabled	= false;
		boolean	downSpeedUnlimited	= false;
		long	totalDownSpeed		= 0;
		long	downSpeedSetMax		= 0;
		long	maxDown				= 0;
		boolean upSpeedDisabled		= false;
		boolean upSpeedUnlimited	= false;
		long	totalUpSpeed		= 0;
		long	upSpeedSetMax		= 0;
		long	maxUp				= 0;

		boolean onlyMyPeer = true;
		boolean hasIPv4 = false;
		boolean hasIPv6 = false;
		
		if ( hasSelection ){
			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();

			for (int i = 0; i < peers.length; i++) {
				PEPeer peer = peers[i];

				if ( !peer.isMyPeer()){
					onlyMyPeer = false;
					
					String ip = peer.getIp();
					
					if ( ip.indexOf( "." ) != -1 ){
						hasIPv4 = true;
					}else{
						hasIPv6 = true;
					}
					
					InetAddress alt = peer.getAlternativeIPv6();
					
					if ( alt != null ){
						
						if ( alt instanceof Inet4Address ){
							
							hasIPv4 = true;
							
						}else{
							
							hasIPv6 = true;
						}
					}
				}
				PEPeerManager m = peer.getManager();

				if ( m != null ){
					if ( gm != null ){

						DownloadManager dm = gm.getDownloadManager( new HashWrapper( m.getHash()));

						if ( dm != null ){

							peer_dm_map.put( peer, dm );
							
							download_managers.add( dm );
						}
					}
				}

				try {
					int maxul = peer.getStats().getUploadRateLimitBytesPerSecond();

					maxUp += maxul * 4;

					if (maxul == 0) {
						upSpeedUnlimited = true;
					}else{
						if ( maxul > upSpeedSetMax ){
							upSpeedSetMax	= maxul;
						}
					}
					if (maxul == -1) {
						maxul = 0;
						upSpeedDisabled = true;
					}
					totalUpSpeed += maxul;

					int maxdl = peer.getStats().getDownloadRateLimitBytesPerSecond();

					maxDown += maxdl * 4;

					if (maxdl == 0) {
						downSpeedUnlimited = true;
					}else{
						if ( maxdl > downSpeedSetMax ){
							downSpeedSetMax	= maxdl;
						}
					}
					if (maxdl == -1) {
						maxdl = 0;
						downSpeedDisabled = true;
					}
					totalDownSpeed += maxdl;

				} catch (Exception ex) {
					Debug.printStackTrace(ex);
				}
			}
		}
		

		if (download_specific != null) {
			final MenuItem block_item = new MenuItem(menu, SWT.CHECK);
			PEPeer peer = peers.length==0?null:peers[0];

			if ( onlyMyPeer || peer == null || peer.getManager().getDiskManager().getRemainingExcludingDND() > 0 ){
				// disallow peer upload blocking when downloading
				block_item.setSelection(false);
				block_item.setEnabled(false);
			}
			else {
				block_item.setEnabled(true);
				block_item.setSelection(peer.isSnubbed());
			}

			if (peer != null) {
				final boolean newSnubbedValue = !peer.isSnubbed();

				Messages.setLanguageText(block_item, "PeersView.menu.blockupload");
				block_item.addListener(SWT.Selection, new PeersRunner(peers) {
					@Override
					public void run(PEPeer peer) {
						peer.setSnubbed(newSnubbedValue);
					}
				});
			}
		}else{

			if ( download_managers.size() > 0 ){

				MenuItem itemDetails = new MenuItem(menu, SWT.PUSH);

				Messages.setLanguageText(itemDetails, "PeersView.menu.showdownload");

				Utils.setMenuItemImage(itemDetails, "details");

				itemDetails.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
						if (uiFunctions != null) {
							for (DownloadManager dm : download_managers) {
								uiFunctions.getMDI().showEntryByID(
										MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS,
										dm);
							}
						}
					}
				});

				new MenuItem(menu, SWT.SEPARATOR);
			}
		}

		BuddyPlugin bp = BuddyPluginUtils.getPlugin();
		
		if ( bp != null ){
							
			boolean has_pb = false;
			
			boolean has_pb_potential = false;
			
			for ( PEPeer peer: peers ){
						
				if ( peer.isMyPeer()){
					
					continue;
				}
				
				DownloadManager dm = peer_dm_map.get( peer );
				
				Peer p_peer = PluginCoreUtils.wrap( peer );
				
				if ( p_peer.getState() == Peer.TRANSFERING && !bp.isFullBuddy( p_peer )){
					
					has_pb_potential = true;
					
					if ( dm != null && bp.isPartialBuddy( PluginCoreUtils.wrap( dm ), PluginCoreUtils.wrap( peer ))){
						
						has_pb = true;
					}
				}
			}
			
			MenuItem boost_item = new MenuItem( menu, SWT.CHECK );
			Messages.setLanguageText(boost_item, "PeersView.menu.boost");
			boost_item.setSelection( has_pb );
			
			boost_item.setEnabled( has_pb_potential );
			
			boost_item.addListener(SWT.Selection, new PeersRunner(peers) {
				@Override
				public void run(PEPeer peer) {
					
					Peer p_peer = PluginCoreUtils.wrap( peer );
					
					if ( !bp.isFullBuddy( p_peer )){
						
						boolean sel = boost_item.getSelection();
												
						DownloadManager dm = peer_dm_map.get( peer );
		
						if ( dm != null ){
							
							bp.setPartialBuddy( PluginCoreUtils.wrap( dm ), p_peer, sel, true );
						}
					}
				}
			});
		}
		
		
		{

			
			Map<String,Object> menu_properties = new HashMap<>();
			menu_properties.put( ViewUtils.SM_PROP_PERMIT_UPLOAD_DISABLE, true );
			menu_properties.put( ViewUtils.SM_PROP_PERMIT_DOWNLOAD_DISABLE, true );

			ViewUtils.addSpeedMenu(
					shell,
					menu, true, true,
					false,
					hasSelection,
					downSpeedDisabled,
					downSpeedUnlimited,
					totalDownSpeed,
					downSpeedSetMax,
					maxDown,
					upSpeedDisabled,
					upSpeedUnlimited,
					totalUpSpeed,
					upSpeedSetMax,
					maxUp,
					peers.length,
					menu_properties,
					new ViewUtils.SpeedAdapter()
					{
						@Override
						public void
						setDownSpeed(
								int speed )
						{
							if(peers.length > 0) {
								for (int i = 0; i < peers.length; i++) {
									try {
										PEPeer peer = (PEPeer)peers[i];
										peer.getStats().setDownloadRateLimitBytesPerSecond(speed);
									} catch (Exception e) {
										Debug.printStackTrace( e );
									}
								}
							}
						}

						@Override
						public void
						setUpSpeed(
								int speed )
						{

							if(peers.length > 0) {
								for (int i = 0; i < peers.length; i++) {
									try {
										PEPeer peer = (PEPeer)peers[i];
										peer.getStats().setUploadRateLimitBytesPerSecond(speed);
									} catch (Exception e) {
										Debug.printStackTrace( e );
									}
								}
							}
						}
					});

		}
		
		final MenuItem kick_item = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(kick_item, "PeersView.menu.kick");
		kick_item.addListener(SWT.Selection, new PeersRunner(peers) {
			@Override
			public void run(PEPeer peer) {
				if ( !peer.isMyPeer()){
					peer.getManager().removePeer(peer,"Peer kicked" );
				}
			}
		});
		
		kick_item.setEnabled( !onlyMyPeer );

		final MenuItem ban_item;
		
		if ( hasIPv4 && hasIPv6 ){
		
			final Menu ban_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

			ban_item = new MenuItem( menu, SWT.CASCADE);

			ban_item.setMenu( ban_menu );
			
			Messages.setLanguageText( ban_item, "PeersView.menu.kickandban" );

			MenuItem ban_v4_item = new MenuItem( ban_menu, SWT.PUSH );
			
			ban_v4_item.setText( "IPv4" );
			
			MenuItem ban_v6_item = new MenuItem( ban_menu, SWT.PUSH );
			
			ban_v6_item.setText( "IPv6" );
			
			MenuItem ban_v4v6_item = new MenuItem( ban_menu, SWT.PUSH );
			
			ban_v4v6_item.setText( "IPv4 + IPv6" );
			
			Listener l = new PeersRunner(peers) {
				@Override
				public void run( Event e, PEPeer peer) {
					if ( !peer.isMyPeer()){
						String msg = MessageText.getString("PeersView.menu.kickandban.reason");
						
						boolean v4 = e.widget==ban_v4_item||e.widget==ban_v4v6_item;
						boolean v6 = e.widget==ban_v6_item||e.widget==ban_v4v6_item;
						
						String ip = peer.getIp();
						
						InetAddress ia = peer.getAlternativeIPv6();
						
						boolean do_ip;
						
						if ( ip.indexOf( '.' ) != -1 ){
															
							do_ip = v4;
							
						}else{
							
							do_ip = v6;
						}
						
						if ( do_ip ){
											
							IpFilterManagerFactory.getSingleton().getIPFilter().ban( ip, msg, true );
						}
						
						if ( ia != null ){
							
							boolean do_ia;
							
							if ( ia instanceof Inet4Address ){
																
								do_ia = v4;
								
							}else{
								
								do_ia = v6;
							}
							
							if ( do_ia ){
												
								IpFilterManagerFactory.getSingleton().getIPFilter().ban( ia.getHostAddress(), msg, true );
							}
						}
							
						peer.getManager().removePeer(peer, "Peer kicked and banned");
					}
				}
			};
			
			ban_v4_item.addListener( SWT.Selection, l );
			ban_v6_item.addListener( SWT.Selection, l );
			ban_v4v6_item.addListener( SWT.Selection, l );
		}else{
			
			ban_item = new MenuItem(menu, SWT.PUSH);

			Messages.setLanguageText(ban_item, "PeersView.menu.kickandban");
			ban_item.addListener(SWT.Selection, new PeersRunner(peers) {
				@Override
				public void run(PEPeer peer) {
					if ( !peer.isMyPeer()){
						String msg = MessageText.getString("PeersView.menu.kickandban.reason");
						IpFilterManagerFactory.getSingleton().getIPFilter().ban(peer.getIp(),
								msg, true );
						peer.getManager().removePeer(peer, "Peer kicked and banned");
					}
				}
			});
		}

		ban_item.setEnabled( !onlyMyPeer );

		final MenuItem ban_for_item = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(ban_for_item, "PeersView.menu.kickandbanfor");
		ban_for_item.addListener(SWT.Selection, new PeersRunner(peers) {
			@Override
			public boolean run(final PEPeer[] peers){

				String text = MessageText.getString("dialog.ban.for.period.text");

				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"dialog.ban.for.period.title", "!" + text + "!");

				int def = COConfigurationManager.getIntParameter(
						"ban.for.period.default", 60);

				entryWindow.setPreenteredText(String.valueOf(def), false);

				entryWindow.prompt(new UIInputReceiverListener() {
					@Override
					public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
						if (!entryWindow.hasSubmittedInput()) {

							return;
						}

						String sReturn = entryWindow.getSubmittedInput();

						if (sReturn == null) {

							return;
						}

						int mins = -1;

						try {

							mins = Integer.valueOf(sReturn).intValue();

						} catch (NumberFormatException er) {
							// Ignore
						}

						if (mins <= 0) {

							MessageBox mb = new MessageBox(Utils.findAnyShell(), SWT.ICON_ERROR
									| SWT.OK);

							mb.setText(MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
							mb.setMessage(MessageText.getString("MyTorrentsView.dialog.NumberError.text"));

							mb.open();

							return;
						}

						COConfigurationManager.setParameter("ban.for.period.default", mins);

						IpFilter filter = IpFilterManagerFactory.getSingleton().getIPFilter();

						for ( PEPeer peer: peers ){

							if ( !peer.isMyPeer()){
								
								String msg = MessageText.getString("PeersView.menu.kickandbanfor.reason", new String[]{ String.valueOf( mins )});
	
								filter.ban( peer.getIp(), msg, true, mins );
	
								peer.getManager().removePeer(peer, "Peer kicked and banned");
							}
						}
					}
				});

				return( true );
			}
		});
		
		ban_for_item.setEnabled( !onlyMyPeer );

		addPeersMenu( download_specific, "", menu, peers );
	}
	
	private static String
	getMyPeerDetails(
		DownloadManager		dm )
	{
		InetAddress ip = NetworkAdmin.getSingleton().getDefaultPublicAddress();

		InetAddress ip_v6 = NetworkAdmin.getSingleton().getDefaultPublicAddressV6();
		
		int port;
		
		if ( dm == null ){
			
			port = TCPNetworkManager.getSingleton().getDefaultTCPListeningPortNumber();
			
		}else{
			
			port = dm.getTCPListeningPortNumber();
		}
		
		String	str = "";
			
		if ( port > 0 ){
			
			if ( ip != null ){
				
				str = ip.getHostAddress() + ":" + port;
			}
			
			if ( ip_v6 != null ){
				
				str += (str.isEmpty()?"":",") + ip_v6.getHostAddress() + ":" + port;
			}
		}
		
		return( str );
	}
	
	protected static boolean
	addPeersMenu(
		DownloadManager 	man,
		String				column_name,
		Menu				menu,
		PEPeer[]			peers )
	{
		MenuBuildUtils.addSeparator( menu );
		
		MenuItem copy_me_item = new MenuItem( menu, SWT.PUSH );

		Messages.setLanguageText( copy_me_item, "menu.copy.my.peer");

		copy_me_item.addListener(
			SWT.Selection,
			new Listener()
			{
				@Override
				public void
				handleEvent(
						Event event)
				{
					String str = getMyPeerDetails( man );
					
					if ( str.isEmpty()){
						
						str = "<no usable peers>";
					}
					
					ClipboardCopy.copyToClipBoard( str );
				}
			});	
		
		if ( man != null && !TorrentUtils.isReallyPrivate(man.getTorrent())){

			PEPeerManager pm = man.getPeerManager();

			if ( pm != null ){
		
			MenuItem copy_all_peers= new MenuItem( menu, SWT.PUSH );
	
			Messages.setLanguageText( copy_all_peers, "menu.copy.all.peers");
	
			copy_all_peers.addListener(
				SWT.Selection,
				new Listener()
				{
					@Override
					public void
					handleEvent(
							Event event)
					{
						List<PEPeer> peers = pm.getPeers();
						
						String str = getMyPeerDetails( man );
						
						for ( PEPeer peer: peers ){
							
							int port = peer.getTCPListenPort();
							
							if ( port > 0 ){
								
								String address = peer.getIp() + ":" + port;
								
								str += (str.isEmpty()?"":",") + address;
							}
						}
						
						if ( str.isEmpty()){
							
							str = "<no usable peers>";
						}
						
						ClipboardCopy.copyToClipBoard( str );
					}
				});
			
			MenuItem add_peers_item = new MenuItem( menu, SWT.PUSH );
	
			Messages.setLanguageText( add_peers_item, "menu.add.peers");
	
			add_peers_item.addListener(
					SWT.Selection,
					new Listener()
					{
						@Override
						public void
						handleEvent(
								Event event)
						{
							SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
									"dialog.add.peers.title",
									"dialog.add.peers.msg");
	
							String def = COConfigurationManager.getStringParameter( "add.peers.default", "" );
	
							entryWindow.setPreenteredText( String.valueOf( def ), false );
							
							entryWindow.addVerifyListener(
						    		new VerifyListener(){
										
										@Override
										public void verifyText(VerifyEvent e){
											String str = e.text.replaceAll( "[\\r\\n]+", "," );
											
											if ( !str.equals(e.text )){
												
													// tidy up from multi-line flattening
												
												while( str.contains( ",," )){
													str = str.replace( ",,", "," );
												}
												
												str = str.trim();
												
												while( str.endsWith( "," )){
													str = str.substring( 0, str.length()-1).trim();
												}
												
												while ( str.startsWith( "," )){
													str = str.substring(1).trim();
												}
											}
											
											e.text = str;
										}
									});
							
							entryWindow.prompt(
									new UIInputReceiverListener()
									{
										@Override
										public void
										UIInputReceiverClosed(
												UIInputReceiver entryWindow)
										{
											if ( !entryWindow.hasSubmittedInput()){
	
												return;
											}
	
											String sReturn = entryWindow.getSubmittedInput();
	
											if ( sReturn == null ){
	
												return;
											}
	
											COConfigurationManager.setParameter( "add.peers.default", sReturn );
	
											PEPeerManager pm = man.getPeerManager();
	
											if ( pm == null ){
	
												return;
											}
	
											Utils.getOffOfSWTThread(
												new AERunnable(){
													
													@Override
													public void runSupport()
													{
														String[] bits = sReturn.replace(';', ',' ).split( "," );
				
														for  ( String bit: bits ){
				
															bit = bit.trim();
				
															if ( bit.isEmpty()){
																
																continue;
															}
															
															int	pos = bit.lastIndexOf( ':' );
				
															if ( pos != -1 ){
				
																String host = bit.substring( 0, pos ).trim();
																String port = bit.substring( pos+1 ).trim();
				
																try{
																	int	i_port = Integer.parseInt( port );
				
																	pm.addPeer( host, i_port, 0, NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE ), null );
				
																}catch( Throwable e ){
				
																}
															}else{
				
																pm.addPeer( bit, 6881, 0, NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE ), null );
															}
														}
													}
												});
										}
									});
						}
					});
			}
		}
		
		addPeerSetMenu( menu, peers );
		
		return( true );
	}
	
	public static void
	addPeerSetMenu(
		Menu		menu,
		PEPeer[]	peers )
	{
		String	peer_cc = null;
		
		if ( peers.length == 1 ){
			
			String[] details = PeerUtils.getCountryDetails( peers[0] );
			
			if ( details != null && details.length > 0 ){
		
				peer_cc = details[0];
				
			}else{
				
				peer_cc = PeerUtils.CC_UNKNOWN;
			}
		}
		
		addPeerSetMenu( menu, true, peer_cc );
	}
	
	public static void
	addPeerSetMenu(
		Menu		menu,
		boolean		do_auto_cat,
		String		peer_cc )
	{
		SpeedLimitHandler slh = SpeedLimitHandler.getSingleton(CoreFactory.getSingleton());
		
		List<SpeedLimitHandler.PeerSet> peer_sets = slh.getPeerSets();
		
		boolean	has_auto_cat 	= false;
		boolean	has_cc_peer_set = false;
		
		String peer_cc_set_name = peer_cc==null?null:( peer_cc + " " + MessageText.getString( "TableColumn.header.peers" ));

		for ( SpeedLimitHandler.PeerSet peer_set: peer_sets ){
					
			if ( do_auto_cat ){
		
				Pattern pattern = peer_set.getClientPattern();

				if ( pattern != null && pattern.pattern().equals( "auto" )){
		
					has_auto_cat = true;
				}
			}
			
			if ( peer_cc_set_name != null ){
				
				if ( peer_set.getName().equals( peer_cc_set_name )){
					
					has_cc_peer_set = true;
				}
			}
		}
		
		if ( do_auto_cat && !has_auto_cat ){
						
			MenuItem auto_cat_item = new MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText( auto_cat_item, "menu.add.auto.client.peerset");
	
			auto_cat_item.addListener(
				SWT.Selection,
				(e)->{
					slh.addConfigLine( "peer_set Auto=all,client=auto", true );
				});
		}
		
		if ( peer_cc_set_name != null && !has_cc_peer_set ){
			
			MenuItem auto_cat_item = new MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText( auto_cat_item, "menu.add.peerset.for.cc", Utils.getCCString( peer_cc ));
	
			auto_cat_item.addListener(
				SWT.Selection,
				(e)->{
					slh.addConfigLine( "peer_set " + peer_cc_set_name + "=" + peer_cc + ",group=" + MessageText.getString( "TableColumn.header.Country" ) , true );
				});
		}
		
		if ( has_auto_cat || has_cc_peer_set ){
			
			MenuItem edit_slh_item = new MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText( edit_slh_item, "menu.edit.peer.set.config");
	
			edit_slh_item.addListener(
				SWT.Selection,
				(e)->{
					Utils.editSpeedLimitHandlerConfig( slh );
				});
		}
	}
	
	@Override
	public void
	fillMenu(
		String 		sColumnName,
		Menu 		menu )
	{
		fillMenu( menu, tv, shell, null );
		
		new MenuItem (menu, SWT.SEPARATOR);
	}
	
	@Override
	public void 
	addThisColumnSubMenu(
		String 	sColumnName, 
		Menu 	menuThisColumn)
	{
		if ( addPeersMenu( null, sColumnName, menuThisColumn, new PEPeer[0] )){

			new MenuItem( menuThisColumn, SWT.SEPARATOR );
		}
	}
	
	protected abstract void
	updateSelectedContent();
	
	@Override
	public void deselected(TableRowCore[] rows) {
		updateSelectedContent();
	}

	@Override
	public void focusChanged(TableRowCore focus) {
	}

	@Override
	public void selected(TableRowCore[] rows) {
		updateSelectedContent();
	}
	
	@Override
	public void defaultSelected(TableRowCore[] rows, int stateMask){
	}
	
	@Override
	public void mouseEnter(TableRowCore row){
	}

	@Override
	public void mouseExit(TableRowCore row){
	}
	
	private static abstract class
	PeersRunner
		implements Listener
	{
		private PEPeer[]		peers;

		private
		PeersRunner(
				PEPeer[]	_peers )
		{
			peers = _peers;
		}

		@Override
		public void
		handleEvent(
			Event e)
		{
			if ( !run( e, peers )){

				for ( PEPeer peer: peers ){

					run( e, peer );
				}
			}
		}

		public void
		run(
			Event	e,
			PEPeer peer)
		{
			run( peer );
		}

		public boolean
		run(
			Event		e,
			PEPeer[]	peers )
		{
			return( run( peers ));
		}
		
		public void
		run(
			PEPeer peer)
		{
		}

		public boolean
		run(
			PEPeer[]	peers )
		{
			return( false );
		}
	}
}
