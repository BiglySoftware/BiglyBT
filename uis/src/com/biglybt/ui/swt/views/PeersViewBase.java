package com.biglybt.ui.swt.views;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.speedmanager.SpeedLimitHandler;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.IdentityHashSet;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableLifeCycleListener;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableSelectionListener;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.views.peer.PeerFilesView;
import com.biglybt.ui.swt.views.peer.PeerInfoView;
import com.biglybt.ui.swt.views.peer.RemotePieceDistributionView;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.peers.ASItem;
import com.biglybt.ui.swt.views.tableitems.peers.ChokedItem;
import com.biglybt.ui.swt.views.tableitems.peers.ChokingItem;
import com.biglybt.ui.swt.views.tableitems.peers.ClientIdentificationItem;
import com.biglybt.ui.swt.views.tableitems.peers.ClientItem;
import com.biglybt.ui.swt.views.tableitems.peers.ColumnPeerNetwork;
import com.biglybt.ui.swt.views.tableitems.peers.ConnectedTimeItem;
import com.biglybt.ui.swt.views.tableitems.peers.DLedFromOthersItem;
import com.biglybt.ui.swt.views.tableitems.peers.DiscardedItem;
import com.biglybt.ui.swt.views.tableitems.peers.DownItem;
import com.biglybt.ui.swt.views.tableitems.peers.DownSpeedItem;
import com.biglybt.ui.swt.views.tableitems.peers.DownSpeedLimitItem;
import com.biglybt.ui.swt.views.tableitems.peers.DownloadNameItem;
import com.biglybt.ui.swt.views.tableitems.peers.EncryptionItem;
import com.biglybt.ui.swt.views.tableitems.peers.GainItem;
import com.biglybt.ui.swt.views.tableitems.peers.HandshakeReservedBytesItem;
import com.biglybt.ui.swt.views.tableitems.peers.HostNameItem;
import com.biglybt.ui.swt.views.tableitems.peers.IncomingRequestCountItem;
import com.biglybt.ui.swt.views.tableitems.peers.IndexItem;
import com.biglybt.ui.swt.views.tableitems.peers.InterestedItem;
import com.biglybt.ui.swt.views.tableitems.peers.InterestingItem;
import com.biglybt.ui.swt.views.tableitems.peers.IpItem;
import com.biglybt.ui.swt.views.tableitems.peers.LANItem;
import com.biglybt.ui.swt.views.tableitems.peers.LatencyItem;
import com.biglybt.ui.swt.views.tableitems.peers.MessagingItem;
import com.biglybt.ui.swt.views.tableitems.peers.OptimisticUnchokeItem;
import com.biglybt.ui.swt.views.tableitems.peers.OutgoingRequestCountItem;
import com.biglybt.ui.swt.views.tableitems.peers.PeerByteIDItem;
import com.biglybt.ui.swt.views.tableitems.peers.PeerIDItem;
import com.biglybt.ui.swt.views.tableitems.peers.PeerSourceItem;
import com.biglybt.ui.swt.views.tableitems.peers.PercentItem;
import com.biglybt.ui.swt.views.tableitems.peers.PieceItem;
import com.biglybt.ui.swt.views.tableitems.peers.PiecesItem;
import com.biglybt.ui.swt.views.tableitems.peers.PortItem;
import com.biglybt.ui.swt.views.tableitems.peers.ProtocolItem;
import com.biglybt.ui.swt.views.tableitems.peers.SnubbedItem;
import com.biglybt.ui.swt.views.tableitems.peers.StatUpItem;
import com.biglybt.ui.swt.views.tableitems.peers.StateItem;
import com.biglybt.ui.swt.views.tableitems.peers.TimeToSendPieceItem;
import com.biglybt.ui.swt.views.tableitems.peers.TimeUntilCompleteItem;
import com.biglybt.ui.swt.views.tableitems.peers.TotalDownSpeedItem;
import com.biglybt.ui.swt.views.tableitems.peers.TypeItem;
import com.biglybt.ui.swt.views.tableitems.peers.UniquePieceItem;
import com.biglybt.ui.swt.views.tableitems.peers.UpDownRatioItem;
import com.biglybt.ui.swt.views.tableitems.peers.UpItem;
import com.biglybt.ui.swt.views.tableitems.peers.UpRatioItem;
import com.biglybt.ui.swt.views.tableitems.peers.UpSpeedItem;
import com.biglybt.ui.swt.views.tableitems.peers.UpSpeedLimitItem;

public abstract class 
PeersViewBase
	extends TableViewTab<PEPeer>
	implements UISWTViewCoreEventListenerEx, TableLifeCycleListener, TableViewSWTMenuFillListener, TableSelectionListener
{
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
		};
	}

	private static final TableColumnCore[] basicItems = getBasicColumnItems(TableManager.TABLE_TORRENT_PEERS);

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TableManager.TABLE_TORRENT_PEERS, basicItems );
	}
	
	private static boolean[] registeredCoreSubViews = { false, false };

	protected TableViewSWT<PEPeer> tv;
	
	protected Shell shell;

	private boolean				swarm_view_enable;
	
	private PeersGraphicView 	swarm_view;
	private Set<PEPeer>			swarm_peers = new HashSet<>();
	private volatile boolean	peers_changed;


	protected
	PeersViewBase(
		String		id,
		boolean		enable_swarm_view )
	{
		super( id );
		
		swarm_view_enable = enable_swarm_view;
	}

	@Override
	public boolean
	isCloneable()
	{
		return( true );
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
			
		}else {
			
			return( super.initComposite(composite));
		}
	}

	
	protected TableViewSWT<PEPeer> 
	initYourTableView( 
		String		table_id,
		boolean 	enable_tabs ) 
	{
		if ( table_id == TableManager.TABLE_TORRENT_PEERS ){
			
			tv = TableViewFactory.createTableViewSWT(Peer.class, TableManager.TABLE_TORRENT_PEERS,
					getPropertiesPrefix(), basicItems, "pieces", SWT.MULTI | SWT.FULL_SELECTION	| SWT.VIRTUAL);
			
		}else{
			
		  	TableColumnCore[] items = PeersView.getBasicColumnItems(TableManager.TABLE_ALL_PEERS);
		  	TableColumnCore[] basicItems = new TableColumnCore[items.length + 1];
		  	System.arraycopy(items, 0, basicItems, 0, items.length);
		  	basicItems[items.length] = new DownloadNameItem(TableManager.TABLE_ALL_PEERS);

			TableColumnManager tcManager = TableColumnManager.getInstance();

			tcManager.setDefaultColumnNames( TableManager.TABLE_ALL_PEERS, basicItems );


		  	tv = TableViewFactory.createTableViewSWT(Peer.class, TableManager.TABLE_ALL_PEERS,
						getPropertiesPrefix(), basicItems, "connected_time", SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		}
		
		tv.setRowDefaultHeightEM(1);
		
		tv.setEnableTabViews(enable_tabs,true,null);

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		
		if (uiFunctions != null) {
			
			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			registerPluginViews( table_id, pluginUI );
		}
		
		tv.addLifeCycleListener(this);
		
		tv.addMenuFillListener(this);
		
		tv.addSelectionListener(this, false);
		
		return tv;
	}	
	
	protected void 
	registerPluginViews(
		String				table_id,
		final UISWTInstance pluginUI ) 
	{
		final int table_index =  table_id == TableManager.TABLE_TORRENT_PEERS?0:1;
		
		if ( pluginUI == null || registeredCoreSubViews[table_index]){
			
			return;
		}

		pluginUI.addView(table_id, "PeerInfoView",PeerInfoView.class, null);
		pluginUI.addView(table_id, "RemotePieceDistributionView", RemotePieceDistributionView.class, null);
		
		if ( table_index == 0 ) {
			pluginUI.addView(table_id, "PeerFilesView",	PeerFilesView.class, null);
		}
		
		pluginUI.addView(table_id, "LoggerView", LoggerView.class, true);

		registeredCoreSubViews[table_index] = true;

		final UIManager uiManager = PluginInitializer.getDefaultInterface().getUIManager();
		
		uiManager.addUIListener(new UIManagerListener() {
			@Override
			public void UIAttached(UIInstance instance) {
			}

			@Override
			public void UIDetached(UIInstance instance) {
				if (!(instance instanceof UISWTInstance)) {
					return;
				}

				registeredCoreSubViews[table_index] = false;
				
				pluginUI.removeViews(table_id, "PeerInfoView");
				pluginUI.removeViews(table_id, "RemotePieceDistributionView");
				if ( table_index == 0 ) {
					pluginUI.removeViews(table_id, "PeerFilesView");
				}
				pluginUI.removeViews(table_id, "LoggerView");

				uiManager.removeUIListener(this);
			}
		});
	}	
	
	
	@Override
	public void 
	tableLifeCycleEventOccurred(
		TableView tv, int eventType,
		Map<String, Object> data) 
	{
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				shell = this.tv.getComposite().getShell();
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
						
						List<PEPeer> peers = tv.getDataSources();
						
						swarm_peers = new HashSet<>( peers );
						
						final Map<PEPeerManager,int[]> done_pms = new HashMap<>();
						
						List<DownloadManager>	dms = new ArrayList<>();
						
						for ( PEPeer peer: peers ){
							
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
	
	public boolean eventOccurred(UISWTViewEvent event) {
		
		switch( event.getType()){
			case UISWTViewEvent.TYPE_REFRESH:{
				if ( swarm_view != null ) {
					
					updateSwarmPeers();
					
					swarm_view.refresh();
				}
				break;
			}
			case UISWTViewEvent.TYPE_FOCUSGAINED:{
				if ( swarm_view != null ) {
					swarm_view.setFocused( true );
				}
				break;
			}				
			case UISWTViewEvent.TYPE_FOCUSLOST:{
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

		if ( hasSelection ){
			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();

			for (int i = 0; i < peers.length; i++) {
				PEPeer peer = peers[i];

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

			if ( peer == null || peer.getManager().getDiskManager().getRemainingExcludingDND() > 0 ){
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
							
							bp.setPartialBuddy( PluginCoreUtils.wrap( dm ), p_peer, sel );
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
				peer.getManager().removePeer(peer,"Peer kicked" );
			}
		});

		final MenuItem ban_item = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(ban_item, "PeersView.menu.kickandban");
		ban_item.addListener(SWT.Selection, new PeersRunner(peers) {
			@Override
			public void run(PEPeer peer) {
				String msg = MessageText.getString("PeersView.menu.kickandban.reason");
				IpFilterManagerFactory.getSingleton().getIPFilter().ban(peer.getIp(),
						msg, true );
				peer.getManager().removePeer(peer, "Peer kicked and banned");
			}
		});

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

							String msg = MessageText.getString("PeersView.menu.kickandbanfor.reason", new String[]{ String.valueOf( mins )});

							filter.ban( peer.getIp(), msg, true, mins );

							peer.getManager().removePeer(peer, "Peer kicked and banned");
						}
					}
				});

				return( true );
			}
		});
		
		addPeersMenu( download_specific, "", menu );
	}
	
	private static String
	getMyPeerDetails(
		DownloadManager		dm )
	{
		InetAddress ip = NetworkAdmin.getSingleton().getDefaultPublicAddress();

		InetAddress ip_v6 = NetworkAdmin.getSingleton().getDefaultPublicAddressV6();
		
		int port = dm.getTCPListeningPortNumber();
		
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
		final DownloadManager 	man,
		String					column_name,
		Menu					menu )
	{
		new MenuItem( menu, SWT.SEPARATOR);

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
		
		SpeedLimitHandler slh = SpeedLimitHandler.getSingleton(CoreFactory.getSingleton());
		
		List<SpeedLimitHandler.PeerSet> peer_sets = slh.getPeerSets();
		
		boolean	has_auto = false;
		
		for ( SpeedLimitHandler.PeerSet peer_set: peer_sets ){
		
			Pattern pattern = peer_set.getClientPattern();
			
			if ( pattern != null ){
				
				if ( pattern.pattern().equals( "auto" )){
		
					has_auto = true;
				}
			}
		}
		
		if ( has_auto ){
			
			MenuItem edit_slh_item = new MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText( edit_slh_item, "menu.edit.peer.set.config");
	
			edit_slh_item.addListener(
				SWT.Selection,
				(e)->{
					Utils.editSpeedLimitHandlerConfig( slh );
				});
			
		}else{
			
			MenuItem auto_cat_item = new MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText( auto_cat_item, "menu.add.auto.client.peerset");
	
			auto_cat_item.addListener(
				SWT.Selection,
				(e)->{
					slh.addConfigLine( "peer_set Auto=all,client=auto", true );
				});
		}
		
		return( true );
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
		if ( addPeersMenu( null, sColumnName, menuThisColumn )){

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
			if ( !run( peers )){

				for ( PEPeer peer: peers ){

					run( peer );
				}
			}
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
