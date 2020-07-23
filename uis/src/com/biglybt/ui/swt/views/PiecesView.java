/*
 * Created on 2 juil. 2003
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.ui.swt.views;

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerPieceListener;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo2;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.piece.MyPieceDistributionView;
import com.biglybt.ui.swt.views.piece.PieceInfoView;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.pieces.*;
import com.biglybt.util.DataSourceUtils;

import com.biglybt.pif.ui.tables.TableManager;

/**
 * Pieces List View
 * <p/>
 * Features:<br/>
 * <li>List of partial pieces</li>
 * <li>double-click to show on Piece Map</li>
 */

public class PiecesView
	extends TableViewTab<PEPiece>
	implements DownloadManagerPeerListener,
	DownloadManagerPieceListener,
	TableDataSourceChangedListener,
	TableLifeCycleListener,
	TableViewSWTMenuFillListener,
	TableSelectionListener,
	UISWTViewCoreEventListener,
	ViewTitleInfo2
{
	public static final Class<PEPiece> PLUGIN_DS_TYPE = PEPiece.class;

	private final static TableColumnCore[] basicItems = {
		new PieceNumberItem(),
		new SizeItem(),
		new BlockCountItem(),
		new BlocksItem(),
		new CompletedItem(),
		new AvailabilityItem(),
		new TypeItem(),
		new ReservedByItem(),
		new WritersItem(),
		new PriorityItem(),
		new SpeedItem(),
		new RequestedItem(),
		new FilesItem(),
	};

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TableManager.TABLE_TORRENT_PIECES, basicItems );
	}

	public static final String MSGID_PREFIX = "PiecesView";

	private static String[] legendKeys = { 
			"PiecesView.legend.requested",
			"PiecesView.legend.written",
			"PiecesView.legend.downloaded",
			"PiecesView.legend.incache",
			"label.end.game.mode",
			"label.uploading"
		};
	
	public static Color
	getLegendColor(
		String		key )
	{
		return( Legend.getLegendColor( key, legendKeys, BlocksItem.colors ));
	}
	
	private DownloadManager 		manager;
	private TableViewSWT<PEPiece> 	tv;

	private Composite legendComposite;
	private MultipleDocumentInterfaceSWT mdi;

	private Map<Integer,PEPieceUploading>	uploading_pieces = new HashMap<>();
	private boolean							show_uploading;
	
	/**
	 * Initialize
	 *
	 */
	public PiecesView() {
		super(MSGID_PREFIX);
	}

	@Override
	public Composite 
	initComposite(
		Composite composite) 
	{
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
		
		Messages.setLanguageText( lp_enable, "label.show.uploading.pieces" );
		lp_enable.addListener( SWT.Selection, (ev)->{
			COConfigurationManager.setParameter( "Pieces View Show Uploading", lp_enable.getSelection());
		});
		
		COConfigurationManager.addAndFireParameterListener(
				"Pieces View Show Uploading",
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
					
					setShowUploading( enabled );
				}
			});
		
		Composite tableParent = new Composite(parent, SWT.NONE);
		
		tableParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		layout = new GridLayout();
		layout.horizontalSpacing = layout.verticalSpacing = 0;
		layout.marginHeight = layout.marginWidth = 0;
		tableParent.setLayout(layout);

		return( tableParent );
	}
	
	// @see com.biglybt.ui.swt.views.table.impl.TableViewTab#initYourTableView()
	@Override
	public TableViewSWT<PEPiece> initYourTableView() {
		registerPluginViews();

		tv = TableViewFactory.createTableViewSWT(PLUGIN_DS_TYPE,
				TableManager.TABLE_TORRENT_PIECES, getPropertiesPrefix(), basicItems,
				basicItems[0].getName(), SWT.SINGLE | SWT.FULL_SELECTION | SWT.VIRTUAL);

		tv.addTableDataSourceChangedListener(this, true);
		tv.addMenuFillListener(this);
		tv.addLifeCycleListener(this);
		tv.addSelectionListener(this, false);

		return tv;
	}

	private void
	setShowUploading(
		boolean		enabled )
	{
		if ( enabled == show_uploading ){
			
			return;
		}
		
		show_uploading = enabled;
		
		updateUploadingPieces( true );
	}
	
	private static void registerPluginViews() {
		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		if (vm.areCoreViewsRegistered(PLUGIN_DS_TYPE)) {
			return;
		}

		vm.registerView(PLUGIN_DS_TYPE, new UISWTViewBuilderCore(
			PieceInfoView.MSGID_PREFIX, null, PieceInfoView.class));

		vm.registerView(PLUGIN_DS_TYPE, new UISWTViewBuilderCore(
			"MyPieceDistributionView", null, MyPieceDistributionView.class));

		vm.setCoreViewsRegistered(PLUGIN_DS_TYPE);
	}

	@Override
	public void
	fillMenu(
		String 	sColumnName,
		Menu 	menu )
	{
		final List<Object>	selected = tv.getSelectedDataSources();

		if ( selected.size() == 0 ){

			return;
		}

		if ( manager == null ){

			return;
		}

		PEPeerManager pm = manager.getPeerManager();

		if ( pm == null ){

			return;
		}

		final PiecePicker picker = pm.getPiecePicker();

		boolean	has_undone	 	= false;
		boolean	has_unforced	= false;

		for ( Object obj: selected ){

			PEPiece piece = (PEPiece)obj;

			if ( !piece.getDMPiece().isDone()){

				has_undone = true;

				if ( picker.isForcePiece( piece.getPieceNumber())){

					has_unforced = true;
				}
			}
		}

		final MenuItem force_piece = new MenuItem( menu, SWT.CHECK );

		Messages.setLanguageText( force_piece, "label.force.piece" );

		force_piece.setEnabled( has_undone );

		if ( has_undone ){

			force_piece.setSelection( has_unforced );

			force_piece.addSelectionListener(
	    		new SelectionAdapter()
	    		{
	    			@Override
				    public void
	    			widgetSelected(
	    				SelectionEvent e)
	    			{
	    				boolean	forced = force_piece.getSelection();

	    				for ( Object obj: selected ){

	    					PEPiece piece = (PEPiece)obj;

	    					if ( !piece.getDMPiece().isDone()){

	    						picker.setForcePiece( piece.getPieceNumber(), forced );
	    					}
	    				}
	    			}
	    		});
		}

		final MenuItem cancel_reqs_piece = new MenuItem( menu, SWT.PUSH );

		Messages.setLanguageText( cancel_reqs_piece, "label.rerequest.blocks" );

		cancel_reqs_piece.addSelectionListener(
    		new SelectionAdapter()
    		{
    			@Override
			    public void
    			widgetSelected(
    				SelectionEvent e)
    			{
     				for ( Object obj: selected ){

    					PEPiece piece = (PEPiece)obj;

    					for ( int i=0;i<piece.getNbBlocks();i++){

    						if ( piece.isRequested( i )){

    							piece.clearRequested( i );
    						}
    					}
    				}
    			}
    		});

		final MenuItem reset_piece = new MenuItem( menu, SWT.PUSH );

		Messages.setLanguageText( reset_piece, "label.reset.piece" );

		reset_piece.addSelectionListener(
    		new SelectionAdapter()
    		{
    			@Override
			    public void
    			widgetSelected(
    				SelectionEvent e)
    			{
     				for ( Object obj: selected ){

    					PEPiece piece = (PEPiece)obj;

    					piece.reset();
    				}
    			}
    		});

		new MenuItem( menu, SWT.SEPARATOR );
	}

	@Override
	public void
	addThisColumnSubMenu(
		String 	sColumnName,
		Menu 	menuThisColumn )
	{

	}

	// @see TableDataSourceChangedListener#tableDataSourceChanged(java.lang.Object)
	@Override
	public void tableDataSourceChanged(Object newDataSource) {

		DownloadManager newManager = DataSourceUtils.getDM(newDataSource);

		if (newManager == manager) {
			return;
		}

		if (manager != null) {
			manager.removePeerListener(this);
			manager.removePieceListener(this);
		}

		manager = newManager;

		synchronized( uploading_pieces ){
		
			uploading_pieces.clear();
		}
		
		if (tv == null || tv.isDisposed()) {
			return;
		}

		tv.removeAllTableRows();

		if (manager != null) {
			manager.addPeerListener(this, false);
			manager.addPieceListener(this, false);
			addExistingDatasources();
		}
	}

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				tableViewInitialized();
				break;
			case EVENT_TABLELIFECYCLE_DESTROYED:
				tableViewDestroyed();
				break;
		}
	}

	private void tableViewInitialized() {
		if ((legendComposite == null || legendComposite.isDisposed()) && tv != null) {
			Composite composite = tv.getTableComposite();

			legendComposite = Legend.createLegendComposite(composite.getParent(),
					BlocksItem.colors, legendKeys );
		}
	}

	private void tableViewDestroyed() {
		if (legendComposite != null && legendComposite.isDisposed()) {
			legendComposite.dispose();
		}

		if (manager != null) {
			manager.removePeerListener(this);
			manager.removePieceListener(this);
			manager = null;
		}
		
		synchronized( uploading_pieces ){
			
			uploading_pieces.clear();
		}
	}

	/* DownloadManagerPeerListener implementation */
	@Override
	public void pieceAdded(PEPiece created) {
    tv.addDataSource(created);
	}

	@Override
	public void pieceRemoved(PEPiece removed) {
    tv.removeDataSource(removed);
	}

	@Override
	public void peerAdded(PEPeer peer) {  }
	@Override
	public void peerRemoved(PEPeer peer) {  }
	@Override
	public void peerManagerWillBeAdded(PEPeerManager	peer_manager ){}
	@Override
	public void peerManagerAdded(PEPeerManager manager) {	}
	@Override
	public void peerManagerRemoved(PEPeerManager	manager) {
		tv.removeAllTableRows();
		synchronized( uploading_pieces ){
			uploading_pieces.clear();
		}
	}

	/**
	 * Add datasources already in existance before we called addListener.
	 * Faster than allowing addListener to call us one datasource at a time.
	 */
	private void addExistingDatasources() {
		if (manager == null || tv == null || tv.isDisposed()) {
			return;
		}

		boolean process = false;
		
		PEPiece[] dataSources = manager.getCurrentPieces();
		if (dataSources.length > 0) {
			
			tv.addDataSources(dataSources);
			
			process = true;
		}
		
		updateUploadingPieces( false );

		if ( !uploading_pieces.isEmpty()){
			
			process = true;
		}
		
		if ( process ){
			
			tv.processDataSourceQueue();
		}
		
		// For this view the tab datasource isn't driven by table row selection so we
		// need to update it with the primary data source

		TableViewSWT_TabsCommon tabs = tv.getTabsCommon();

		if ( tabs != null ){

			tabs.triggerTabViewsDataSourceChanged(tv);
		}

	}
	
	private void
	updateUploadingPieces(
		boolean	process_queue )
	{
		DownloadManager dm = manager;
		
		if ( dm == null ){
			
			return;
		}
		
		PEPeerManager pm = manager.getPeerManager();

		if ( pm == null ){

			return;
		}

		boolean	changed = false;
		
		if ( show_uploading ){
			
			DiskManagerPiece[] dm_pieces = pm.getDiskManager().getPieces();
	
			Map<Integer,boolean[]>	up_map = new HashMap<>();
			
			for ( PEPeer peer: pm.getPeers()){
				
				if ( !peer.isChokedByMe()){
					
					//System.out.println( "peer " + peer.getIp());
					
					DiskManagerReadRequest[] pieces = peer.getRecentPiecesSent();
					
					for ( DiskManagerReadRequest piece: pieces ){
					
						long sent = piece.getTimeSent();
						
						if ( sent <= 0 || SystemTime.getMonotonousTime() - sent > 10*1000 ){
							
							continue;
						}
						
						//System.out.println( "    Uploading " + piece.getPieceNumber() + "/" + piece.getOffset());
						
						int	piece_number = piece.getPieceNumber();
								
						boolean[] blocks = up_map.get( piece_number );
					
						if ( blocks == null ){
						
							DiskManagerPiece dm_piece = dm_pieces[piece.getPieceNumber()];
						
							blocks = new boolean[dm_piece.getNbBlocks()];
							
							up_map.put( piece_number, blocks );
						}
						
						blocks[piece.getOffset()/DiskManager.BLOCK_SIZE] = true;
					}
				}
			}
			
			List<PEPiece>	to_add 		= new ArrayList<PEPiece>();
			Set<PEPiece>	to_remove 	= new HashSet<PEPiece>();
			
			synchronized( uploading_pieces ){
				
				to_remove.addAll( uploading_pieces.values());
				
				for ( Map.Entry<Integer,boolean[]> entry: up_map.entrySet()){
					
					int 		pn 		= entry.getKey();
					boolean[]	blocks 	= entry.getValue();
					
					PEPieceUploading piece = uploading_pieces.get( pn );
					
					if ( piece == null ){
						
						piece = new PEPieceUploading( pm, dm_pieces[pn], pn );
						
						to_add.add( piece );
						
						uploading_pieces.put( pn, piece );
						
					}else{
						
						to_remove.remove( piece );
					}
					
					piece.addUploading( blocks );
				}
				
				for ( PEPiece p: to_remove ){
					
					uploading_pieces.remove( p.getPieceNumber());
				}
			}
			
			if ( !to_add.isEmpty()){
				
				tv.addDataSources( to_add.toArray( new PEPiece[to_add.size()]));
				
				changed = true;
			}
			
			if ( !to_remove.isEmpty()){
				
				tv.removeDataSources( to_remove.toArray( new PEPiece[to_remove.size()]));
				
				changed = true;
			}
		}else{
			
			Set<PEPiece>	to_remove 	= new HashSet<PEPiece>();
		
			synchronized( uploading_pieces ){
			
				to_remove.addAll( uploading_pieces.values());
				
				uploading_pieces.clear();
			}
			
			if ( !to_remove.isEmpty()){
				
				tv.removeDataSources( to_remove.toArray( new PEPiece[to_remove.size()]));
				
				changed = true;
			}
		}
		
		if ( process_queue && changed ){
			
			tv.processDataSourceQueue();
		}
	}
	
	  @Override
	public void defaultSelected(TableRowCore[] rows, int keyMask, int origin) {
	  // Show piece in PieceInfoView

	  // Show subtab if we have one
	  TableViewSWT_TabsCommon tabsCommon = tv.getTabsCommon();
		if (tabsCommon != null) {
			MultipleDocumentInterfaceSWT mdi = tabsCommon.getMDI();
			if (mdi != null) {
				mdi.showEntryByID(PieceInfoView.MSGID_PREFIX);
				return;
			}
		}
		
		if (rows.length != 1) {
			return;
		}

		// Show in sister tab
		if (mdi != null) {
			mdi.showEntryByID(PieceInfoView.MSGID_PREFIX, rows[0].getDataSource());
		}
	}

	protected void
	updateSelectedContent()
	{
		Object[] dataSources = tv.getSelectedDataSources(true);

		if ( dataSources.length == 0 ){

	      	String id = "DMDetails_Pieces";
	      	
	      	if (manager != null) {
	      		if (manager.getTorrent() != null) {
	      			id += "." + manager.getInternalName();
	      		} else {
	      			id += ":" + manager.getSize();
	      		}
	      		SelectedContentManager.changeCurrentlySelectedContent(id,
	      				new SelectedContent[] {
	      						new SelectedContent(manager)
	      		});
	      	} else {
	      		SelectedContentManager.changeCurrentlySelectedContent(id, null);
	      	}
		}else{
			
			SelectedContent[] sc = new SelectedContent[dataSources.length];
			
			for ( int i=0;i<sc.length;i++){
				Object ds = dataSources[i];
				if (ds instanceof PEPiece) {
					sc[i] = new SelectedContent( "piece: " + ((PEPiece)ds).getPieceNumber());
				}else{
					sc[i] = new SelectedContent( "piece: "  + ds );
				}
			}
			
			SelectedContentManager.changeCurrentlySelectedContent(tv.getTableID(),
					sc, tv);
		}

	}
	
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
	public void mouseEnter(TableRowCore row){
	}

	@Override
	public void mouseExit(TableRowCore row){
	}
	
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		int type = event.getType();
		if (type == UISWTViewEvent.TYPE_CREATE) {
			event.getView().setDestroyOnDeactivate(true);
		}else if ( type == UISWTViewEvent.TYPE_REFRESH ){
			updateUploadingPieces( true );
		}

		return super.eventOccurred(event);
	}

	@Override
	public void titleInfoLinked(MultipleDocumentInterface mdi, MdiEntry mdiEntry) {
		if (mdi instanceof MultipleDocumentInterfaceSWT) {
			this.mdi = (MultipleDocumentInterfaceSWT) mdi;
		}
	}

	@Override
	public Object getTitleInfoProperty(int propertyID) {
		return null;
	}
	
	public class
	PEPieceUploading
		implements PEPiece
	{
			// 'fake' class to denote an uploading piece for the UI
		
		private final PEPeerManager		pm;
		private final DiskManagerPiece	dm_piece;
		private final int				piece_number;
		
		private final boolean[]			blocks;
		
		private boolean 	complete;
		
		private
		PEPieceUploading(
			PEPeerManager			_pm,
			DiskManagerPiece		_dm_piece,
			int						_piece_number )
		{
			pm				= _pm;
			dm_piece		= _dm_piece;
			piece_number	= _piece_number;
			
			blocks = new boolean[dm_piece.getNbBlocks()];
		}
			
		public PiecePicker		getPiecePicker(){ return( pm.getPiecePicker()); }
		public PEPeerManager	getManager(){ return( pm ); }
	    public DiskManagerPiece getDMPiece(){ return( dm_piece ); }
	    public int         		getPieceNumber(){ return( piece_number ); };
		public int				getLength(){ return( dm_piece.getLength()); }
		public int				getNbBlocks(){ return( dm_piece.getNbBlocks()); }
	    public int         		getBlockNumber(int offset){ return( offset/DiskManager.BLOCK_SIZE ); }
		public int				getBlockSize( int block_index ){ return( dm_piece.getBlockSize(block_index)); }

		
		private void
		addUploading(
			boolean[]		b )
		{
			boolean done = true;
			
			for ( int i=0;i<b.length;i++){
				if ( b[i] ){
					blocks[i] = true;
				}else if ( !blocks[i] ){					
					done = false;
				}
			}
			
			complete = done;
		}
		
	    public long         	getCreationTime(){ return( 0 ); };

	    public long         	getTimeSinceLastActivity(){ return( 0 ); }

	    public long         	getLastDownloadTime( long now ){ return( 0 ); }

		public void
		addWrite( int blockNumber, String sender, byte[] hash,boolean correct	){}

		public int			getNbWritten(){ return( 0 ); }

		public int			getAvailability(){ return( 0 ); }

		public boolean		hasUnrequestedBlock(){ return( false ); }
		public int[]		getAndMarkBlocks(PEPeer peer, int nbWanted, int[] request_hint, boolean reverse_order ){ return( null ); }

		public void 		getAndMarkBlock(PEPeer peer, int index){}
		public Object		getRealTimeData(){ return( null ); }
		public void			setRealTimeData( Object	o ){};

		public boolean		setRequested(PEPeer peer, int blockNumber){ return( false ); }
		public void			clearRequested(int blocNumber){}
	    public boolean      isRequested(int blockNumber){ return( false ); }

	    public boolean      isRequested(){ return( false ); }
	    public void			setRequested(){};
	    public boolean		isRequestable(){ return( false ); }

		public int			getNbRequests(){ return( 0 ); }
		public int			getNbUnrequested(){ return( 0 ); }

		public boolean		isDownloaded(int blockNumber){ return( blocks[ blockNumber] ); }
	    public void         setDownloaded(int offset){};
	    public void         clearDownloaded(int offset){}
		public boolean		isDownloaded(){ return( complete ); }
		public boolean[]	getDownloaded(){ return( blocks ); }
		public boolean		hasUndownloadedBlock(){ return( false ); }

		public String		getReservedBy(){ return( "" ); }
		public void			setReservedBy(String peer){}

		public int			getResumePriority(){ return( 0 ); }
		public void			setResumePriority(int p){}

		public String[] 	getWriters(){ return( null ); }
		public void			setWritten(String peer, int blockNumber){}
		public boolean 		isWritten(){ return( false ); }
		public boolean 		isWritten( int blockNumber){ return( true ); }

		public int 			getSpeed(){ return( 0 ); }
		public void			setSpeed(int speed){}

		public void
		setLastRequestedPeerSpeed(
			int		speed ){}

		public void			reset(){}

		public String
		getString(){ return( "" ); }
	}
}
