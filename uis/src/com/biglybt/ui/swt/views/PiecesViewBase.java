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
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.util.CopyOnWriteSet;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.IdentityHashSet;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo2;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.piece.MyPieceDistributionView;
import com.biglybt.ui.swt.views.piece.PieceMapView;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.pieces.*;


import com.biglybt.pif.ui.tables.TableManager;

/**
 * Pieces List View
 * <p/>
 * Features:<br/>
 * <li>List of partial pieces</li>
 * <li>double-click to show on Piece Map</li>
 */

public abstract class PiecesViewBase
	extends TableViewTab<PEPiece>
	implements 
		TableLifeCycleListener,
		TableViewSWTMenuFillListener,
		TableSelectionListener,
		UISWTViewCoreEventListener,
		TableViewFilterCheck<PEPiece>,
		ViewTitleInfo2
{
	public static final Class<PEPiece> PLUGIN_DS_TYPE = PEPiece.class;

	static TableColumnCore[] getBasicColumnItems(String table_id) {
		return( new TableColumnCore[] {
			new PieceNumberItem(table_id),
			new SizeItem(table_id),
			new BlockCountItem(table_id),
			new BlocksItem(table_id),
			new CompletedItem(table_id),
			new AvailabilityItem(table_id),
			new TypeItem(table_id),
			new ReservedByItem(table_id),
			new WritersItem(table_id),
			new PriorityItem(table_id),
			new SpeedItem(table_id),
			new RequestedItem(table_id),
			new FilesItem(table_id),
		});
	}

	private static final TableColumnCore[] basicItems = getBasicColumnItems(TableManager.TABLE_TORRENT_PIECES);

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TableManager.TABLE_TORRENT_PIECES, basicItems );
	}

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
	
	private BubbleTextBox bubbleTextBox;
	
	protected TableViewSWT<PEPiece> 	tv;

	private Composite legendComposite;
	private MultipleDocumentInterfaceSWT mdi;

	private boolean shown;
	
	private Map<Long,PEPieceUploading>		uploading_pieces = new HashMap<>();
	
	private boolean							show_uploading;

	protected
	PiecesViewBase(
		String		id )
	{
		super( id );
	}
	
	protected abstract String
	getTableID();

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
		
		boolean hasFilter = getTableID() == TableManager.TABLE_ALL_PIECES;

		Composite header = new Composite(parent, SWT.NONE);
		
		header.setLayout( new FormLayout());
		
		
		header.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

		Button lp_enable = new Button( header, SWT.CHECK );
		
		FormData fd = Utils.getFilledFormData();
		fd.left = new FormAttachment(0, 10);
		fd.right = null;
		lp_enable.setLayoutData( fd );
		
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

		if ( hasFilter ){
			bubbleTextBox = new BubbleTextBox(header, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.SINGLE);
			
			fd = Utils.getFilledFormData();
			fd.width = 150;
			fd.top = null;
			fd.right = new FormAttachment(100, -10);
			fd.left = null;
			
			bubbleTextBox.setMessageAndLayout( "" , fd);

			bubbleTextBox.setAllowRegex( true );
			
			if ( tv != null ){
				
				tv.enableFilterCheck(bubbleTextBox, this );
			}
		}
		
		Composite tableParent = new Composite(parent, SWT.NONE);
		
		tableParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		layout = new GridLayout();
		layout.horizontalSpacing = layout.verticalSpacing = 0;
		layout.marginHeight = layout.marginWidth = 0;
		tableParent.setLayout(layout);

		return( tableParent );
	}
	
	protected TableViewSWT<PEPiece> initYourTableView( String table_id){
		
		if ( table_id == TableManager.TABLE_TORRENT_PIECES ){

			tv = TableViewFactory.createTableViewSWT(PLUGIN_DS_TYPE,
					TableManager.TABLE_TORRENT_PIECES, getTextPrefixID(), basicItems,
					basicItems[0].getName(), SWT.SINGLE | SWT.FULL_SELECTION | SWT.VIRTUAL);
		}else{
		
		  	TableColumnCore[] items = getBasicColumnItems(TableManager.TABLE_ALL_PIECES );
		  	TableColumnCore[] basicItems = new TableColumnCore[items.length + 1];
		  	System.arraycopy(items, 0, basicItems, 0, items.length);
		  	
		  	basicItems[items.length] = new DownloadNameItem(TableManager.TABLE_ALL_PIECES );

			TableColumnManager tcManager = TableColumnManager.getInstance();

			tcManager.setDefaultColumnNames( TableManager.TABLE_ALL_PIECES, basicItems );

			tv = TableViewFactory.createTableViewSWT(PEPiece[].class,	// PEPiece subviews assume single download
					TableManager.TABLE_ALL_PIECES, getTextPrefixID(), basicItems,
					basicItems[0].getName(), SWT.SINGLE | SWT.FULL_SELECTION | SWT.VIRTUAL);
		}

		if ( bubbleTextBox != null ){
			
			tv.enableFilterCheck(bubbleTextBox, this );
		}
		
		registerPluginViews();

		tv.addMenuFillListener(this);
		tv.addLifeCycleListener(this);
		tv.addSelectionListener(this, false);

		return tv;
	}
	
	protected abstract List<PEPeerManager>
	getPeerManagers();

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
			PieceMapView.MSGID_PREFIX, null, PieceMapView.class));

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

		List<PEPeerManager> pms = getPeerManagers();

		if ( pms.isEmpty()){

			return;
		}

		boolean	has_undone	 	= false;
		boolean	has_unforced	= false;

		boolean all_uploading	= true;
		
		IdentityHashSet<DownloadManager>	download_managers = new IdentityHashSet<>();

		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();

		for ( Object obj: selected ){

			PEPiece piece = (PEPiece)obj;

			PEPeerManager pm = piece.getManager();

			if ( pm != null && gm != null ){

				DownloadManager dm = gm.getDownloadManager( new HashWrapper( pm.getHash()));

				if ( dm != null ){
					
					download_managers.add( dm );
				}
			}
			
			if ( !( piece instanceof PEPieceUploading )){

				all_uploading = false;
				
				if ( !piece.getDMPiece().isDone()){
	
					has_undone = true;
	
					if ( piece.getPiecePicker().isForcePiece( piece.getPieceNumber())){
	
						has_unforced = true;
					}		
				}
			}
		}
		
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
		
		if ( all_uploading ){
			
			if ( selected.size() > 0 ){

				PEPieceUploading piece = (PEPieceUploading)selected.get(0);

				CopyOnWriteSet<String> uploaders = piece.getUploadPeers();

				PEPeer peer = null;

				if ( uploaders.size() == 1 ){

					String ip = uploaders.iterator().next();

					List<PEPeer> peers = piece.getManager().getPeers( ip );

					if ( peers.size() == 1 ){

						peer = peers.get(0);
					}
				}

				if ( mdi != null && mdi.getEntry( PeersView.MSGID_PREFIX ) != null ){

					MenuItem show_peer = new MenuItem( menu, SWT.PUSH );
	
					Messages.setLanguageText( show_peer, "menu.show.peer" );
	
					final PEPeer f_peer = peer;
	
					show_peer.addSelectionListener(
						new SelectionAdapter()
						{
							@Override
							public void
							widgetSelected(
									SelectionEvent e)
							{
								if ( mdi != null ){
	
									MdiEntrySWT entry = mdi.getEntry( PeersView.MSGID_PREFIX );
	
									if ( entry != null ){
	
										UISWTViewEventListener listener = entry.getEventListener();
	
										if ( listener instanceof PeersView ){
	
											((PeersView)listener).selectPeer( f_peer );
	
											mdi.showEntryByID( PeersView.MSGID_PREFIX );
										}			    					
									}
								}
							}
						});
	
					show_peer.setEnabled( peer != null );
				}
			}
		}else{
			
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
	
		    						piece.getPiecePicker().setForcePiece( piece.getPieceNumber(), forced );
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
	}

	@Override
	public void
	addThisColumnSubMenu(
		String 	sColumnName,
		Menu 	menuThisColumn )
	{

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

	protected void tableViewInitialized() {
		if ((legendComposite == null || legendComposite.isDisposed()) && tv != null) {
			Composite composite = tv.getTableComposite();

			legendComposite = Legend.createLegendComposite(composite.getParent(),
					BlocksItem.colors, legendKeys );
		}
	}

	protected void tableViewDestroyed() {
		if (legendComposite != null && legendComposite.isDisposed()) {
			legendComposite.dispose();
		}
		
		if ( tv != null ){
			
			tv.removeAllTableRows();
		}
		
		synchronized( uploading_pieces ){
			
			uploading_pieces.clear();
		}
	}
	
	@Override
	public void 
	filterSet(String filter)
	{	
	}
	
	@Override
	public boolean
	filterCheck(
		PEPiece 	piece, 
		String 		filter, 
		boolean 	regex,
		boolean		confusable )
	{
		if ( confusable ){
			
			return( false );
		}
		
		if ( filter.isEmpty()){
			
			return( true );
		}
		
		List<String>	names = new ArrayList<>();
		
		PEPeerManager manager = piece.getManager();
		
		if ( manager != null ){
			
			names.add( manager.getDisplayName());
		}
		
        DiskManagerPiece dmp = piece.getDMPiece();
        
        if ( dmp != null ){
       	 
        	DMPieceList l = dmp.getManager().getPieceList( piece.getPieceNumber());

        	for ( int i=0;i<l.size();i++) {

        		DMPieceMapEntry entry = l.get( i );

        		String name = entry.getFile().getTorrentFile().getRelativePath();
        		
        		names.add( name );
        	}
        }

		String s = regex ? filter : RegExUtil.splitAndQuote( filter, "\\s*[|;]\\s*" );

		boolean	match_result = true;

		if ( regex && s.startsWith( "!" )){

			s = s.substring(1);

			match_result = false;
		}

		Pattern pattern = RegExUtil.getCachedPattern( "piecesview:search", s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

		boolean result = !match_result;
		
		for ( String name: names ){
		
			if ( pattern.matcher(name).find()){
			
				result = match_result;
				
				break;
			}
		}
		
		return( result );
	}
	
	protected boolean
	updateUploadingPieces(
		boolean				process_queue )
	{
		List<PEPeerManager> pms = getPeerManagers();
			
		if ( pms.isEmpty()){
			
			return( false );
		}

		boolean	changed = false;
		
		boolean has_uploading_pieces = false;
		
		if ( show_uploading ){
			
			long now = SystemTime.getMonotonousTime();
		
			Map<Long,Object[]>	up_map = new HashMap<>();

			for ( PEPeerManager pm: pms ){
				
				long pm_id_mask = ((long)pm.getUID()) << 32;
								
				DiskManagerPiece[] dm_pieces = pm.getDiskManager().getPieces();
						
				for ( PEPeer peer: pm.getPeers()){
					
					if ( !peer.isChokedByMe()){
						
						int[] peerRequestedPieces = peer.getIncomingRequestedPieceNumbers();
						
							// prepare the next few pieces
						
						if ( peerRequestedPieces != null && peerRequestedPieces.length > 0 ){
							
							int	pieces_added = 0;
							
							for ( int i=0;i<peerRequestedPieces.length;i++){
								
								int	raw_piece_number = peerRequestedPieces[i];
								
								long masked_piece_number = pm_id_mask | raw_piece_number;
								
								Object[] entry = up_map.get( masked_piece_number );
							
								Set<String>	peers;
								
								if ( entry == null ){
								
									DiskManagerPiece dm_piece = dm_pieces[ raw_piece_number ];
								
									boolean[] blocks = new boolean[dm_piece.getNbBlocks()];
									
									peers = new HashSet<>();
									
									up_map.put( masked_piece_number, new Object[]{ blocks, peers, pm });
									
									pieces_added++;
									
								}else{
									
									peers	= (Set<String>)entry[1];
								}
														
								peers.add( peer.getIp());
								
								if ( pieces_added >= 2 ){
									
									break;
								}
							}
						}
						
						DiskManagerReadRequest[] pieces = peer.getRecentPiecesSent();
	
						for ( DiskManagerReadRequest piece: pieces ){
						
							long sent = piece.getTimeSent();
							
							if ( sent < 0 || ( sent > 0 && now - sent > 10*1000 )){
								
								continue;
							}
													
							int	raw_piece_number = piece.getPieceNumber();
									
							long masked_piece_number = pm_id_mask | raw_piece_number;
							
							Object[] entry = up_map.get( masked_piece_number );
						
							boolean[] 	blocks;
							Set<String>	peers;
							
							if ( entry == null ){
							
								DiskManagerPiece dm_piece = dm_pieces[ raw_piece_number ];
							
								blocks = new boolean[dm_piece.getNbBlocks()];
								
								peers = new HashSet<>();
								
								up_map.put( masked_piece_number, new Object[]{ blocks, peers, pm });
								
							}else{
								
								blocks 	= (boolean[])entry[0];
								peers	= (Set<String>)entry[1];
							}
							
							blocks[piece.getOffset()/DiskManager.BLOCK_SIZE] = true;
							
							peers.add( peer.getIp());
						}
					}
				}
			}
			
			List<PEPieceUploading>	to_add 		= new ArrayList<>();
			Set<PEPieceUploading>	to_remove 	= new HashSet<>();
			
			synchronized( uploading_pieces ){
								
				to_remove.addAll( uploading_pieces.values());
				
				for ( Map.Entry<Long,Object[]> up_entry: up_map.entrySet()){
					
					long 		masked_piece_number 	= up_entry.getKey();
					Object[]	entry					= up_entry.getValue();
					
					boolean[]	blocks 	= (boolean[])entry[0];
					Set<String>	peers	= (Set<String>)entry[1];
					
					PEPieceUploading piece = uploading_pieces.get( masked_piece_number );
					
					if ( piece == null ){
						
						PEPeerManager pm = (PEPeerManager)entry[2];
						
						int raw_piece_number = (int)( masked_piece_number & 0x00000000ffffffff );
						
						piece = new PEPieceUploading( pm, pm.getDiskManager().getPieces()[raw_piece_number], raw_piece_number );
						
						to_add.add( piece );
						
						uploading_pieces.put( masked_piece_number, piece );
						
					}else{
						
						to_remove.remove( piece );
					}
					
					piece.addUploading( blocks, peers );
				}
				
				if ( !to_remove.isEmpty()){
					
					int active_pieces = uploading_pieces.size();

					Iterator<PEPieceUploading> it = to_remove.iterator();
					
					while( it.hasNext()){
						
						PEPieceUploading piece = it.next();
						
						if ( piece.readyToRemove() || active_pieces > 50 ){
						
							int raw_piece_number = piece.getPieceNumber();
							
							long masked_piece_number = ((long)piece.getManager().getUID()) << 32 | raw_piece_number;
							
							uploading_pieces.remove(  masked_piece_number );
							
							active_pieces--;
							
						}else{
						
							it.remove();
						}
					}
				}
				
				has_uploading_pieces |= !uploading_pieces.isEmpty();
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
		
		return( has_uploading_pieces );
	}
	
	public int
	getUploadingPieceCount()
	{
		if ( shown && show_uploading ){
			
			synchronized( uploading_pieces ){
				
				return( uploading_pieces.size());
			}
		}else{
			
				// only accurate when view is shown as calculated on refresh events
			
			return( -1 );
		}
	}
	
	protected void
	clearUploadingPieces()
	{
		synchronized( uploading_pieces ){
			
			uploading_pieces.clear();
		}
	}
	
	  @Override
	public void defaultSelected(TableRowCore[] rows, int keyMask, int origin) {
	  // Show piece in PieceMapView

	  // Show subtab if we have one
	  TableViewSWT_TabsCommon tabsCommon = tv.getTabsCommon();
		if (tabsCommon != null) {
			MultipleDocumentInterfaceSWT mdi = tabsCommon.getMDI();
			if (mdi != null) {
				mdi.showEntryByID(PieceMapView.MSGID_PREFIX);
				return;
			}
		}
		
		if (rows.length != 1) {
			return;
		}

		// Show in sister tab
		if (mdi != null && mdi.getEntry(PieceMapView.MSGID_PREFIX ) != null ){
			mdi.showEntryByID(PieceMapView.MSGID_PREFIX, rows[0].getDataSource());
		}else{
			UIFunctions uif = UIFunctionsManager.getUIFunctions();
			if (uif != null) {
				GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();

				PEPeerManager pm = ((PEPiece)rows[0].getDataSource()).getManager();
				
				DownloadManager dm = pm==null?null:gm.getDownloadManager( new HashWrapper( pm.getHash()));

				if ( dm != null ){
				
					uif.getMDI().showEntryByID(
						MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, dm);
				}
			}
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
		}else if ( type == UISWTViewEvent.TYPE_SHOWN ){
			shown = true;
		}else if ( type == UISWTViewEvent.TYPE_HIDDEN ){
			shown = false;
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
		
		private final boolean[]					blocks;
		private final CopyOnWriteSet<String>	peers = new CopyOnWriteSet<String>( false );
		
		private boolean 	complete;
		
		private volatile long	last_active = SystemTime.getMonotonousTime();
		
		private
		PEPieceUploading(
			PEPeerManager			_pm,
			DiskManagerPiece		_dm_piece,
			int						_piece_number )
		{
			pm				= _pm;
			dm_piece		= _dm_piece;
			piece_number	= _piece_number;
			
			blocks 	= new boolean[dm_piece.getNbBlocks()];
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
			boolean[]		b,
			Set<String>		latest_peers )
		{
			last_active = SystemTime.getMonotonousTime();
			
			synchronized( this ){
				
				boolean done = true;
				
				for ( int i=0;i<b.length;i++){
					if ( b[i] ){
						blocks[i] = true;
					}else if ( !blocks[i] ){					
						done = false;
					}
				}
				
				complete = done;
				
				peers.addAll( latest_peers );
			}
		}
		
		private boolean
		readyToRemove()
		{
			return( SystemTime.getMonotonousTime() - last_active > 5000 );
		}
		
		public CopyOnWriteSet<String>
		getUploadPeers()
		{
			return( peers );
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
