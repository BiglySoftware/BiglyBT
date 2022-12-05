/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views;


import java.util.*;

import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.Legend;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerManagerListenerAdapter;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.pif.disk.DiskManager;
import com.biglybt.pif.peers.PeerReadRequest;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.ui.common.ToolBarItem;

import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;


public class 
PieceBlocksView
	implements UIPluginViewToolBarListener, UISWTViewCoreEventListener, ParameterListener
{
	public static String MSGID_PREFIX = "PieceBlocksView";
		
	private final static Color[] block_colours = {
			Colors.bluesFixed[Colors.BLUES_DARKEST],
			Colors.bluesFixed[Colors.BLUES_MIDLIGHT],
			Colors.green,
			Colors.fadedGreen,
		};

	private final static String[] legend_keys = {
			"PieceBlocksView.block.done1",
			"PieceBlocksView.block.active1",
			"PieceBlocksView.block.done2",
			"PieceBlocksView.block.active2",
	};
	
	private int MAX_ACTIVE_BLOCKS;
	
	private Canvas 	canvas;
	private Image 	img;

	private TimerEventPeriodic	block_refresher;
	
	private Object				dm_data_lock = new Object();
	private ManagerData[]		dm_data = {};

	private volatile boolean	all_blocks_view;
	
	public 
	PieceBlocksView() 
	{
		COConfigurationManager.addAndFireParameterListener( "blocks.view.max.active", this );
	}
	
	@Override
	public void 
	parameterChanged(
		String parameterName)
	{
		MAX_ACTIVE_BLOCKS = COConfigurationManager.getIntParameter( "blocks.view.max.active" );
	}
	
	private boolean comp_focused;
	private Object focus_pending_ds;

	protected void
	setFocused( boolean foc )
	{
		if ( foc ){

			comp_focused = true;

			dataSourceChanged( focus_pending_ds );

		}else{

			synchronized( dm_data_lock ){

				DownloadManager[] temp = new DownloadManager[ dm_data.length ];

				for ( int i=0;i<temp.length;i++ ) {

					temp[i] = dm_data[i].manager;
				}

				focus_pending_ds = temp;
			}

			dataSourceChanged( null );

			comp_focused = false;
		}
	}

	protected void 
	dataSourceChanged(
		Object newDataSource ) 
	{
		if ( newDataSource instanceof String ){
			
			synchronized( dm_data_lock ){
			
				if ( !all_blocks_view ){
				
					all_blocks_view = true;
			
					swtView.setTitle(MessageText.getString(getData()));
				}
			}
			
			return;
		}
		
		if ( !comp_focused ){
			
			focus_pending_ds = newDataSource;
			
			return;
		}

		List<DownloadManager> existing = new ArrayList<>();

		synchronized( dm_data_lock ){
			
			for ( ManagerData data: dm_data ){

				existing.add( data.manager );
			}
		}

		List<DownloadManager> newManagers;
		
		if ( newDataSource instanceof List ){
			
			newManagers = (List<DownloadManager>)newDataSource;
			
		}else{
			
			newManagers = ViewUtils.getDownloadManagersFromDataSource( newDataSource, existing );
		}

		synchronized( dm_data_lock ){

			Map<DownloadManager,ManagerData>	map = new IdentityHashMap<>();

			for ( ManagerData data: dm_data ) {

				map.put( data.manager, data );
			}

			ManagerData[] new_data = new ManagerData[newManagers.size()];

			boolean	changed = new_data.length != dm_data.length;

			for ( int i=0;i<new_data.length;i++) {

				DownloadManager dm = newManagers.get(i);

				ManagerData existing_data = map.remove( dm );

				if ( existing_data != null ) {

					new_data[i] = existing_data;

					if ( i < dm_data.length && dm_data[i] != existing_data ){

						changed = true;
					}
				}else{

					new_data[i] = new ManagerData( dm );

					changed = true;
				}
			}

			if ( map.size() > 0 ){

				changed = true;

				for ( ManagerData data: map.values()) {

					data.delete();
				}
			}

			if ( !changed ){

				return;
			}

			Arrays.sort(
					new_data,
					(a,b)->{
						return( Long.compare( a.getAddTime(), b.getAddTime()));
					});

			dm_data = new_data;

			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					synchronized( dm_data_lock ){
						if ( dm_data.length > 0 ){
							Utils.disposeComposite(canvas, false);
						} else {
							ViewUtils.setViewRequiresOneDownload(canvas);
						}
					}
				}
			});
		}
	}

	protected void
	delete()
	{
		COConfigurationManager.removeParameterListener( "blocks.view.max.active", this );
		
		synchronized( dm_data_lock ){

			for ( ManagerData data: dm_data ) {

				data.delete();
			}

			dm_data = new ManagerData[0];
		}
	}

	protected Composite 
	getComposite() 
	{
		return( canvas );
	}

	private String 
	getData()
	{
		return( all_blocks_view?"MainWindow.menu.view.allblocks":"PieceBlocksView.title.full" );
	}

	protected void 
	initialize(
		Composite 	parent )
	{
		Composite comp = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		comp.setLayout(layout);
		GridData gridData = new GridData(GridData.FILL, GridData.FILL, true, true);
		comp.setLayoutData(gridData);
			
		if ( !Utils.isDarkAppearanceNative()){
			
			comp.setBackground( Colors.white );
		}
		
		canvas = new Canvas(comp,SWT.NO_BACKGROUND);

		canvas.setLayoutData( new GridData(GridData.FILL_BOTH ));

		canvas.addPaintListener(
			new PaintListener(){
				@Override
				public void 
				paintControl(
					PaintEvent e ) 
				{		
					Rectangle client_bounds = canvas.getClientArea();

					int	x		= e.x;
					int y		= e.y;
					int width 	= e.width;
					int height	= e.height;
						
					if ( client_bounds.width > 0 && client_bounds.height > 0 ){
												
						updateImage();
							
						e.gc.drawImage(img, x, y, width, height, x, y, width, height);			
					}
				}
			});

		canvas.addListener( SWT.Dispose, (ev)->{
			if ( img != null && !img.isDisposed()){
				img.dispose();
				img = null;
			}
		});
		
		String[] legend_texts = {
				MessageText.getString( "FileView.BlockView.Done" ) + " 1",
				MessageText.getString( "FileView.BlockView.Active" ) + " 1",
				MessageText.getString( "FileView.BlockView.Done" ) + " 2",
				MessageText.getString( "FileView.BlockView.Active" ) + " 2",
				
		};
		
		Legend.createLegendComposite(	
				comp, block_colours, legend_keys, legend_texts, new GridData(SWT.FILL, SWT.DEFAULT, true, false, 1, 1), true );
	}

	private List<DownloadManager>	abv_last_active = Collections.emptyList();
	
	protected void 
	refresh() 
	{
		if ( all_blocks_view ){
			
			List<DownloadManager> new_dms = null;
			
			synchronized( dm_data_lock ){

				List<DownloadManager> dms = CoreFactory.getSingleton().getGlobalManager().getDownloadManagers();
				
				List<DownloadManager> active = new ArrayList<>( dms.size());
				
				for ( DownloadManager dm: dms ){
					
					if ( dm.getState() == DownloadManager.STATE_DOWNLOADING ){
						
						active.add( dm );
					}
				}
				
				boolean changed = abv_last_active.size() != active.size();
				
				if ( !changed ){
					
					for ( int i=0;i<active.size();i++){
						
						if ( abv_last_active.get(i) != active.get( i )){
						
							changed = true;
							
							break;
						}
					}
				}
				
				if ( changed ){
				
					new_dms = active;
					
					abv_last_active = active;
				}
			}
			
			if ( new_dms != null ){
				
				dataSourceChanged( new_dms );
			}
		}

		synchronized( dm_data_lock ){

			if ( canvas == null || canvas.isDisposed()){
				
				return;
			}

			if ( block_refresher == null ){

				block_refresher = 
						SimpleTimer.addPeriodicEvent(
								"PBV:AR",
								100,
								(ev)->{
									Utils.execSWTThread(()->{
										if ( canvas.isDisposed()){
											synchronized( dm_data_lock ){
												if ( block_refresher != null ){

													block_refresher.cancel();

													block_refresher = null;
												}
											}

											return;
										}
										
										canvas.redraw();
									});
								});
			}
		}
	}

	private void
	updateImage()
	{
		synchronized( dm_data_lock ){

			if ( canvas == null || canvas.isDisposed()){
				
				return;
			}

			Rectangle bounds = canvas.getClientArea();

			int	width 	= bounds.width;
			int height	= bounds.height;
			
			if ( width <= 0 || height <= 0 ){

				return;
			}

			if ( block_refresher == null ){

				block_refresher = 
						SimpleTimer.addPeriodicEvent(
								"PBV:AR",
								100,
								(ev)->{
									Utils.execSWTThread(()->{
										if ( canvas.isDisposed()){
											synchronized( dm_data_lock ){
												if ( block_refresher != null ){

													block_refresher.cancel();

													block_refresher = null;
												}
											}

											return;
										}
										
										canvas.redraw();
									});
								});
			}

			boolean clearImage = 
					img == null || 
					img.isDisposed() ||
					img.getBounds().width != bounds.width ||
					img.getBounds().height != bounds.height;
			
			if (clearImage){
				
				if ( img != null && !img.isDisposed()){
					
					img.dispose();
				}
				
				img = new Image(canvas.getDisplay(), width, height);
			}

			GC gc = new GC(img);
			
			gc.setBackground( Utils.isDarkAppearanceNative()?canvas.getBackground():Colors.white);
		
			gc.fillRectangle(0,0,width,height);

			try {
				gc.setTextAntialias(SWT.ON);
				gc.setAntialias(SWT.ON);
					// fix for bug https://stackoverflow.com/questions/23495420/swt-transformation-bug
				//gc.setLineAttributes(new LineAttributes(1, SWT.CAP_FLAT, SWT.JOIN_MITER));
			} catch(Exception e) {
			}

			try{
				int[] piece_counts = new int[dm_data.length];
				
				int blocks_rem = MAX_ACTIVE_BLOCKS;
				
				int	total_pieces 	= 0;
				int	dms_with_pieces	= 0;
								
				for ( int i=0;i<dm_data.length&&blocks_rem>0;i++ ){

					ManagerData md = dm_data[i];
					
					int[] piece_data = md.update();
						
					int pieces = 0;
					
					for ( int active: piece_data ){
					
						if ( active > 0 ){
							
							pieces++;
							
							if ( active >= blocks_rem ){
								
								blocks_rem = 0;
								
								break;
							}
						
							blocks_rem -= active;
						}
					}
										
					if ( pieces > 0 ){
						
						piece_counts[i] = pieces;
						
						total_pieces += pieces;
						
						dms_with_pieces++;
					}
				}

				if ( total_pieces == 0 ){
					
					return;
				}
				
				int piece_space 	= height / 3;
				int block_space		= height - piece_space;
				
				int piece_height 	= piece_space / total_pieces;
				
				if ( piece_height > 10 ){
					
					piece_height = 10;
					
				}else if ( piece_height < 1 ){
					
					piece_height = 1;
				}
				
				int max_pieces = (int)( piece_space / piece_height );
				
				if ( total_pieces > max_pieces ){
					
					int	pieces_per_dm = max_pieces / dms_with_pieces;
					
					if ( pieces_per_dm == 0 ){
						
						pieces_per_dm = 1;
					}
					
					int	used_pieces 				= 0;
					int dms_with_too_many_pieces 	= 0;
					
					for ( int i=0;i<dm_data.length;i++ ){
						
						int pieces = piece_counts[i];
						
						if ( pieces > 0 ){
							
							if ( pieces <= pieces_per_dm ){
								
								used_pieces += pieces;
								
							}else{
								
								dms_with_too_many_pieces++;
							}
						}
					}
					
					if ( dms_with_too_many_pieces > 0 ){
						
						int rem_pieces = max_pieces - used_pieces;
						
						pieces_per_dm = rem_pieces / dms_with_too_many_pieces;
						
						if ( pieces_per_dm == 0 ){
							
							pieces_per_dm = 1;
						}
						
						for ( int i=0;i<dm_data.length;i++ ){
							
							int pieces = piece_counts[i];
							
							if ( pieces > 0 ){
								
								if ( pieces > pieces_per_dm ){
								
									piece_counts[i] = pieces_per_dm;
								}		
							}
						}
					}
				}
				
				
				int y_pos = height - piece_height;
				
				int dm_num = 0;
			
outer:
				for ( int i=0;i<dm_data.length;i++ ){
					
					int piece_count = piece_counts[i];
					
					if ( piece_count == 0 ){
						
						continue;
					}
				
					dm_num++;
					
					synchronized( dm_data[i].lock ){
						
						for ( PieceDetails piece_details: dm_data[i].piece_map.values()){
								
							if ( y_pos < block_space ){
								
								break outer;
							}
							
							if ( piece_count == 0 ){
								
								break;
							}
							
							piece_count--;
							
							boolean odd = dm_num%2==1;
							
							boolean left_to_right = piece_details.left_to_right;
							
							boolean[] downloaded = piece_details.getDownloaded();
							
							float	overall_block_width = (float)width/downloaded.length;
							
							gc.setBackground( block_colours[odd?1:3] );
							
							gc.setAlpha( piece_details.alpha );
							
							for ( Iterator<BlockDetails> it=piece_details.blocks.iterator();it.hasNext();){
								
								BlockDetails block = it.next();
								
								int block_number = block.block_number;
								
								if ( downloaded[block_number] || block.request.isCancelled()){
									
									continue;
								}
								
								int block_x = (int)( overall_block_width*block_number );
								
								int block_y;
								
								if ( odd ){
									
									block_y = y_pos * block.done / block.size;
									
								}else{
									
									block_y = ( y_pos - piece_height ) * block.done / block.size + piece_height;
								}
								
								int block_width = (int)( overall_block_width*( block_number+1)) - block_x;
								
								if ( !left_to_right ){
									
									// think I prefer all blocks to be going the same way...
									// block_x = width - block_x - block_width;
								}
								
								gc.fillRectangle( block_x, block_y, block_width, piece_height );	
							}
		
							gc.setBackground( block_colours[odd?0:2] );
				
							boolean[] missing = null;
							
							if ( overall_block_width < 1.0 ){
								
								missing = new boolean[(int)( overall_block_width*downloaded.length )];
								
								for ( int j=0;j<downloaded.length;j++ ){
									
									if ( !downloaded[j] ){
										
										int block_x = (int)( overall_block_width*j );
										
										missing[block_x] = true;
									}
								}
							}
							
							for ( int j=0;j<downloaded.length;j++ ){
								
								if ( downloaded[j] ){
									
									int block_x = (int)( overall_block_width*j );

									if ( missing != null && missing[block_x]){
										
										continue;
									}
									
									int block_width = (int)( overall_block_width*( j+1)) - block_x;
									
									if ( !left_to_right ){
										
										//block_x = width - block_x - block_width;
									}
									
									gc.fillRectangle( block_x, y_pos, block_width, piece_height );
								}
							}
									
							gc.setAlpha( 255 );
							
							if ( piece_height >= 3 ){
		
								gc.setBackground( Colors.light_grey );

								gc.drawRectangle( 0, y_pos-1, width-1, piece_height );
							}
							
							
							y_pos -= piece_height;
						}
					}
				}
			}finally{
				
				gc.dispose();
			}
		}
	}
	
	private UISWTView swtView;

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
		case UISWTViewEvent.TYPE_CREATE:
			swtView = event.getView();
			swtView.setTitle(MessageText.getString(getData()));
			swtView.setToolBarListener(this);
			break;

		case UISWTViewEvent.TYPE_DESTROY:
			delete();
			break;

		case UISWTViewEvent.TYPE_INITIALIZE:
			initialize((Composite)event.getData());
			break;

		case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
			Messages.updateLanguageForControl(getComposite());
			swtView.setTitle(MessageText.getString(getData()));
			break;

		case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
			dataSourceChanged(event.getData());
			break;

		case UISWTViewEvent.TYPE_SHOWN:
			String id = "DMDetails_Swarm";

			setFocused( true );	// do this before next code as it can pick up the correct 'manager' ref

			synchronized( dm_data_lock ){

				if ( dm_data.length == 0 ){

					SelectedContentManager.changeCurrentlySelectedContent(id, null);

				}else{

					DownloadManager manager 	= dm_data[0].manager;

					if (manager.getTorrent() != null) {
						id += "." + manager.getInternalName();
					} else {
						id += ":" + manager.getSize();
					}

					SelectedContentManager.changeCurrentlySelectedContent(id,
							new SelectedContent[] {
									new SelectedContent(manager)
					});
				}
			}
			break;
		case UISWTViewEvent.TYPE_HIDDEN:
			setFocused( false );
			SelectedContentManager.clearCurrentlySelectedContent();
			break;
		case UISWTViewEvent.TYPE_REFRESH:
			refresh();
			break;
		}

		return true;
	}


	@Override
	public boolean 
	toolBarItemActivated(
		ToolBarItem 		item, 
		long 				activationType,
		Object 				datasource ) 
	{
		return( false );
	}


	@Override
	public void 
	refreshToolBarItems(
		Map<String, Long> list) 
	{
		Map<String, Long> states = TorrentUtil.calculateToolbarStates(
				SelectedContentManager.getCurrentlySelectedContent(), null);
		
		list.putAll(states);
	}
	
	private class
	ManagerData
		extends PEPeerManagerListenerAdapter
		implements DownloadManagerPeerListener
	{
		final Object lock = this;
		
		private final long			add_time = SystemTime.getMonotonousTime();

		private final DownloadManager		manager;
		private final int					blocks_per_piece;
		
		private PEPeerManager		peer_manager;		
			
		private Map<PEPeer, PeerDetails>	peer_map 	= new HashMap<>();
		private Map<Integer,PieceDetails>	piece_map	= new LinkedHashMap<>();
		
		private boolean	deleted;
		
		private
		ManagerData(
			DownloadManager	_manager )
		{
			manager	= _manager;

			int bpp = 1;
			
			try{
				bpp = (int)( manager.getTorrent().getPieceLength() / DiskManager.BLOCK_SIZE );
				
			}catch( Throwable e ){
			}
			
			blocks_per_piece = bpp;
			
			manager.addPeerListener(this);
		}

		int
		getBlocksPerPiece()
		{
			return( blocks_per_piece );
		}
		
		long
		getAddTime()
		{
			return( add_time );
		}

		void
		delete()
		{
			PEPeerManager pm;
		
			synchronized( lock ){

				pm = peer_manager;
					
				peer_map.clear();
				piece_map.clear();
				
				deleted = true;
			}

			if ( pm != null ){
				
				pm.removeListener( this );
			}
			
			manager.removePeerListener(this);
		}

		@Override
		public void 
		peerManagerWillBeAdded(
			PEPeerManager	peer_manager )
		{
		}
		
		@Override
		public void 
		peerManagerAdded(
			PEPeerManager manager )
		{	
			PEPeerManager old_pm;
		
			manager.addListener( this );

			List<PEPiece>	initial_pieces = new ArrayList<>(manager.getNbPieces());
			
			synchronized( lock ){
				
				if ( deleted ){
					
					return;
				}
				
				old_pm	= peer_manager;
			
				peer_manager = manager;
				
				for ( PEPiece piece: manager.getPieces()){
					
					if ( piece != null ){
						
						initial_pieces.add( piece );
					}
				}
			}
			
			for ( PEPiece piece: initial_pieces ){
				
				pieceAdded( manager, piece, null );
			}
			
			if ( old_pm != null ){
				
				old_pm.removeListener( this );
			}
		}
		
		@Override
		public void 
		peerManagerRemoved(
			PEPeerManager manager )
		{
			synchronized( lock ){
				
				if ( manager == peer_manager ){
					
					peer_manager = null;
					
					peer_map.clear();
					piece_map.clear();
				}
			}
		}
		
		@Override
		public void 
		pieceAdded(
			PEPeerManager 	manager, 
			PEPiece 		piece, 
			PEPeer 			for_peer )
		{
			if ( for_peer == null ){
				
					// only add pieces at this point during initial piece recovery, others will be
					// added when requests are created for peers
				
				int pn = piece.getPieceNumber();
						
				synchronized( lock ){
	
					if ( deleted ){
						
						return;
					}
	
					if ( !piece_map.containsKey( pn )){
						
						piece_map.put( pn, new PieceDetails( piece, piece_map.size()%2==0 ));
					}
				}
			}
		}
		
		@Override
		public void 
		pieceRemoved(
			PEPeerManager 	manager, 
			PEPiece 		piece ) 
		{
			int piece_number = piece.getPieceNumber();
			
			synchronized( lock ){
				
				PieceDetails details = piece_map.get( piece_number );
				
				if ( details != null ){
					
					if ( details.remove()){
						
						piece_map.remove( piece_number );
					}
				}
			}
		}
		
		@Override
		public void 
		requestAdded(
			PEPeerManager 		manager, 
			PEPiece 			piece, 
			PEPeer 				peer, 
			PeerReadRequest 	request)
		{
			synchronized( lock ){
		
				if ( deleted ){
					
					return;
				}

				PeerDetails peer_details = peer_map.get( peer );
				
				if ( peer_details == null ){
					
					peer_details = new PeerDetails( peer );
					
					peer_map.put( peer, peer_details) ;
				}
				
				int pn = piece.getPieceNumber();
				
				PieceDetails piece_details = piece_map.get( pn);
				
				if ( piece_details == null ){
										
					piece_details = new PieceDetails( piece, peer_details.left_to_right );
					
					piece_map.put( pn, piece_details );
				}
								
				BlockDetails block = peer_details.addRequest( piece_details, request );
				
				piece_details.addRequest( block );
			}
		}

		@Override
		public void 
		peerAdded(
			PEPeer peer) 
		{
		}

		@Override
		public void 
		peerRemoved(
			PEPeer peer ) 
		{
			synchronized( lock ){
								
				PeerDetails details = peer_map.remove( peer );
				
				if ( details != null ){
					
					details.removePeer();
				}
			}
		}
		
		int[]
		update()
		{
			synchronized( lock ){
				
				for ( PeerDetails peer: peer_map.values()){
					
					peer.update();
				}
				
				int[] result = new int[piece_map.size()];
				
				int pos = 0;
				
				for ( Iterator<PieceDetails> it = piece_map.values().iterator(); it.hasNext();){
				
					PieceDetails piece = it.next();
					
					int active = piece.update();
					
					if ( piece.isDone()){
						
						it.remove();
						
						result[pos] = 0;
						
					}else{
						
						result[pos] = active;
					}
					
					pos++;
				}
				
				return( result );
			}
		}
	}
	
	private boolean last_left_to_right = true;
	
	private class
	PeerDetails
	{
		final PEPeer		peer;
		final PEPeerStats	stats;
		
		final boolean		left_to_right;
		
		final List<BlockDetails>	blocks = new ArrayList<>();
		
		long last_update		= SystemTime.getMonotonousTime();
		long stall_time			= -1;
		
		PeerDetails(
			PEPeer		_peer )
		{
			peer	= _peer;
			stats	= peer.getStats();
			
			left_to_right = last_left_to_right;
			
			last_left_to_right = !last_left_to_right;
		}
		
		BlockDetails
		addRequest(
			PieceDetails		piece,
			PeerReadRequest		request )
		{
			int	block_number = request.getOffset()/DiskManager.BLOCK_SIZE;
						
			BlockDetails block = new BlockDetails( piece, request, block_number );
			
			blocks.add( block );
			
			return( block );
		}
		
		void
		removePeer()
		{
			for ( BlockDetails block: blocks ){
				
				block.peerRemoved();
			}
		}
		
		void
		update()
		{
			long now = SystemTime.getMonotonousTime();
									
			long time_diff = now - last_update;
			
			if ( time_diff == 0 ){
				
				return;
			}
			
			last_update = now;

			long receive_rate = stats.getDataReceiveRate();

			if ( receive_rate == 0 ){
				
				if ( stall_time == -1 ){
					
					stall_time = now;
				}
			}else{
				
				stall_time = -1;
			}
								
			int num_blocks = blocks.size();
			
			if ( num_blocks == 0 ){
				
				return;
			}
			
			int active_blocks = (int)( receive_rate + DiskManager.BLOCK_SIZE -1 )/DiskManager.BLOCK_SIZE;
			
			if ( num_blocks < active_blocks ){
				
				active_blocks = num_blocks;
			}
				
			long bytes_diff = (( receive_rate + 999 ) * time_diff )/1000;

			for ( Iterator<BlockDetails> it=blocks.iterator();it.hasNext();){
				
				BlockDetails block = it.next();
				
				if ( block.checkDone()){
										
					block.remove();
					
					it.remove();
					
				}else if ( stall_time >= 0 && now - stall_time > 10*1000 && block.isActuallyDone()){
					
					block.setDone();
					
					block.remove();
					
					it.remove();
					
				}else{
					
					if ( bytes_diff <= 0 || active_blocks == 0 ){
						
						continue;
					}
					
					long bytes_per_block = ( bytes_diff + active_blocks - 1 ) / active_blocks;

					int rem = block.size - block.done;
					
					if ( rem > bytes_per_block ){
						
						block.done += bytes_per_block;
						
						bytes_diff -= bytes_per_block;
						
					}else{
					
						block.done = block.size;
						
						bytes_diff -= rem;
					}
				}
			}
		}
	}
	
	private int last_alpha = 255;
	
	private class
	PieceDetails
	{
		final PEPiece		piece;
		final int			block_num;
		
		final int[]			block_states;
		final boolean[]		blocks_done;
		
		final List<BlockDetails>	blocks = new LinkedList<>();
		
		final boolean	left_to_right;
		final int		alpha;
		
		int		blocks_done_num;
		
		long 	complete_time	= -1;
		
		PieceDetails(
			PEPiece		_piece,
			boolean		_left_to_right )
		{
			piece 			= _piece;
			left_to_right	= _left_to_right;
			
			block_num	= piece.getNbBlocks();
			
			block_states	= new int[ block_num ];
			blocks_done		= new boolean[ block_num ];   
			
			alpha = last_alpha==255?180:255;
			
			last_alpha = alpha;
			
			boolean[] downloaded = piece.getDownloaded();
			
			for ( int i=0;i<block_num;i++){
				
				if ( downloaded[i]){
					
					blocks_done[i] = true;
					blocks_done_num++;
				}
			}
		}
		
		int
		update()
		{
			int active = 0;
			
			for ( int i=0;i<block_num;i++){
				
				if ( !blocks_done[i]){
							
					int state = block_states[i];
					
					if ( state == 2 ){
						
						blocks_done[i] = true;
						
						blocks_done_num++;
						
					}else if ( state == 1 ){
						
						active++;
					}
				}
			}
			
			return( active );
		}
		
		void
		addRequest(
			BlockDetails		block )
		{
			int block_number = block.block_number;
			
			blocks.add( block );
			
			if ( block_states[block_number] == 0 ){
				
				block_states[block_number] = 1;	// has had at least one request added
			}
		}
		
		void
		removeRequest(
			BlockDetails		block )
		{
			int block_number = block.block_number;
			
			blocks.remove( block );
			
			if ( block.done == block.size ){
				
				block_states[block_number] = 2; // has had a least one request complete
			}
		}
		
		boolean[]
		getDownloaded()
		{
			return( blocks_done );
		}
		
		void
		setDone(
			BlockDetails	block )
		{
			block_states[ block.block_number] = 2;
		}
		
		boolean
		isDone()
		{
			if ( blocks_done_num == block_num && piece.isDownloaded()){
				
				return( true );
			}
			
			return( complete_time >= 0 && SystemTime.getMonotonousTime() - complete_time > 10*1000 );
		}
		
		boolean
		remove()
		{
			if ( piece.isDownloaded()){
				
				complete_time = SystemTime.getMonotonousTime();
				
					// give chance for blocks to complete their movement
				
				return( false );
				
			}else{
				
				return( true );
			}
		}
	}
	
	private class
	BlockDetails
	{
		final PieceDetails		piece_details;
		final PeerReadRequest	request;
		final PEPiece			piece;
		final int				block_number;
		final int				size;
		
		int					done;
		
		BlockDetails(
			PieceDetails	_piece,
			PeerReadRequest	_request,
			int				_bn )
		{
			piece_details	= _piece;
			request			= _request;
			block_number	= _bn;
			
			piece 	= piece_details.piece;
			size	= piece.getBlockSize( block_number );
		}
		
		boolean
		isActuallyDone()
		{
			return( piece.isDownloaded( block_number ));
		}
		
		boolean
		checkDone()
		{
			if ( done == size ){
				
				piece_details.setDone( this );
				
				return( true );
			}
			
			if ( request.isCancelled()){
				
				return( true );
			}
			
			return( piece_details.isDone());
		}
		
		void
		setDone()
		{
			done	= size;
			
			piece_details.setDone( this );
		}
		
		void
		peerRemoved()
		{
			if ( isActuallyDone()){
				
				done = size;
			}
			
			remove();
		}
		
		void
		remove()
		{
			piece_details.removeRequest( this );
		}
	}
}
