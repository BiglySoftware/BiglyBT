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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerPieceListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerManagerListener;
import com.biglybt.core.peer.PEPeerManagerListenerAdapter;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.pif.disk.DiskManager;
import com.biglybt.pif.peers.PeerReadRequest;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;


public class 
PieceBlocksView
	implements UIPluginViewToolBarListener, UISWTViewCoreEventListener
{
	public static String MSGID_PREFIX = "PieceBlocksView";

	
	private static final boolean FORCE_FULL_REPAINT = true; // !Constants.isWindows;
	
	private final Object	DM_DATA_CACHE_KEY 		= new Object();	// not static as we want per-view data
	private final Object	PEER_DATA_KEY 			= new Object();	// not static as we want per-view data
	
	private final static int BLOCKCOLOR_DOWN_SMALL 		= 0;
	private final static int BLOCKCOLOR_DOWN_BIG		= 1;
	private final static int BLOCKCOLOR_UP_SMALL 		= 2;
	private final static int BLOCKCOLOR_UP_BIG 			= 3;

	private final static Color[] blockColors = {
			Colors.bluesFixed[Colors.BLUES_MIDDARK],
			Colors.maroon,
			Colors.fadedGreen,
			Colors.orange,
		};

	private final static String[] legendKeys = {
			"SwarmView.block.downsmall",
			"SwarmView.block.downbig",
			"SwarmView.block.upsmall",
			"SwarmView.block.upbig",
	};
		
	private static final int PEER_SIZE = 18;
	//private static final int PACKET_SIZE = 10;
	private static final int OWN_SIZE_DEFAULT = 75;
	private static final int OWN_SIZE_MIN		= 30;
	private static final int OWN_SIZE_MAX 	= 75;
	private static int OWN_SIZE = OWN_SIZE_DEFAULT;

	private static final int NB_ANGLES = 1000;

	private static final double[] angles;
	private static final double[] deltaXXs;
	private static final double[] deltaXYs;
	private static final double[] deltaYXs;
	private static final double[] deltaYYs;

	static{
		angles = new double[NB_ANGLES];

		deltaXXs = new double[NB_ANGLES];
		deltaXYs = new double[NB_ANGLES];
		deltaYXs = new double[NB_ANGLES];
		deltaYYs = new double[NB_ANGLES];

		for(int i = 0 ; i < NB_ANGLES ; i++) {
			angles[i] = 2 * i * Math.PI / NB_ANGLES - Math.PI;
			deltaXXs[i] = Math.cos(angles[i]);
			deltaXYs[i] = Math.sin(angles[i]);
			deltaYXs[i] = Math.cos(angles[i]+Math.PI / 2);
			deltaYYs[i] = Math.sin(angles[i]+Math.PI / 2);
		}
	}

	private double perimeter;
	private double[] rs = new double[NB_ANGLES];

	//Comparator Class
	//Note: this comparator imposes orderings that are inconsistent with equals.
	private static class
	PeerComparator
		implements Comparator<PEPeer>
	{
		@Override
		public int compare(PEPeer peer0, PEPeer peer1) {

			int percent0 = peer0.getPercentDoneInThousandNotation();
			int percent1 = peer1.getPercentDoneInThousandNotation();

			int result = percent0 - percent1;

			if ( result == 0 ){

				long l = peer0.getTimeSinceConnectionEstablished() - peer1.getTimeSinceConnectionEstablished();

				if ( l < 0 ){
					result = -1;
				}else if ( l > 0 ){
					result = 1;
				}
			}

			return( result );
		}
	}

	private PeerComparator peerComparator;


	private Image					my_flag;

	//UI Stuff
	private Display 	display;
	private Canvas 	canvas;
	private Image 	img;

	private Point		mySizeCache;

	private TimerEventPeriodic	block_refresher;



	private Object				dm_data_lock = new Object();
	private ManagerData[]		dm_data = {};

	private volatile boolean	all_blocks_view;
	
	private boolean			always_show_dm_name;

	private final PeerFilter	peer_filter;

	public 
	PieceBlocksView() 
	{
		this( new PeerFilter() { public boolean acceptPeer(PEPeer peer) { return( true );}});
	}

	public 
	PieceBlocksView(
		PeerFilter	_pf ) 
	{
		peer_filter = _pf;

		this.peerComparator = new PeerComparator();
	}

	public void
	setAlwaysShowDownloadName(
			boolean	b )
	{
		always_show_dm_name = b;
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

			// defer this until here so that a blocking call to get the IP doesn't hang UI construction

		if ( my_flag == null ){

			InetAddress ia = NetworkAdmin.getSingleton().getDefaultPublicAddress();

			if ( ia != null ){

				my_flag = ImageRepository.getCountryFlag( ia, false );
			}
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
		Composite 	parent,
		boolean		showLegend )
	{
		display = parent.getDisplay();

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
			
		comp.setBackground( Colors.white );
		
		canvas = new Canvas(comp,SWT.NO_BACKGROUND);

		canvas.setLayoutData( new GridData(GridData.FILL_BOTH ));

		canvas.addPaintListener(
			new PaintListener(){
				@Override
				public void paintControl(PaintEvent e) {
					
					if ( img != null && !img.isDisposed()){
						
						Rectangle bounds = img.getBounds();
						
						GC gc = e.gc;
						
						int	x		= e.x;
						int y		= e.y;
						int width 	= e.width;
						int height	= e.height;
						
						if ( bounds.width >= ( width + x ) && bounds.height >= ( height + y )){
							
							Image full_image = new Image(canvas.getDisplay(), bounds.width, bounds.height);
							
							try{
								GC full_gc = new GC( full_image );

								try{
									full_gc.drawImage(img, x, y, width, height, x, y, width, height);
							
									full_gc.setClipping( x, y, width, height);
									
									//refreshArrows( full_gc );
									
									gc.drawImage(full_image, x, y, width, height, x, y, width, height);
									
								}finally{
									
									full_gc.dispose();
								}
								
							}finally{
								
								full_image.dispose();
							}
																
							return;
						}
					}
				
					e.gc.setBackground(Colors.white);
					e.gc.fillRectangle(e.x, e.y, e.width, e.height);
				}
			});

		canvas.addListener( SWT.Dispose, (ev)->{
			if ( img != null && !img.isDisposed()){
				img.dispose();
				img = null;
			}
		});
		
		if ( showLegend ){
			Legend.createLegendComposite(	
					comp, blockColors, legendKeys, new GridData(SWT.FILL, SWT.DEFAULT, true, false, 1, 1));
		}
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
			
			gc.setBackground(Colors.white);
		
			gc.fillRectangle(0,0,width,height);

			try {
				gc.setTextAntialias(SWT.ON);
				gc.setAntialias(SWT.ON);
					// fix for bug https://stackoverflow.com/questions/23495420/swt-transformation-bug
				gc.setLineAttributes(new LineAttributes(1, SWT.CAP_FLAT, SWT.JOIN_MITER));
			} catch(Exception e) {
			}

			try{
				List<PEPiece>	pieces = new ArrayList<>(1024);
				
				for ( ManagerData data: dm_data ){

					DownloadManager manager 	= data.manager;

					synchronized( data.lock ){
					
						pieces.addAll( data.pieces );
						
						for ( PEPeer peer: data.peers ){
							
							
						}
					}
				}

				int num_pieces = pieces.size();

				if ( num_pieces == 0 ){
					
					return;
				}
				
				float piece_space 	= height / 3;
				
				float piece_height 	= piece_space / num_pieces;
				
				if ( piece_height > 10 ){
					
					piece_height = 10;
				}
				
				float y_pos = height - piece_height;
				
				for ( PEPiece piece: pieces ){
					
					gc.setBackground( Colors.blue );
					
					boolean[] downloaded = piece.getDownloaded();
					
					float	block_width = (float)width/downloaded.length;
					
					float	x_pos = 0;
					
					for ( int i=0;i<downloaded.length;i++ ){
						
						if ( downloaded[i] ){
							
							gc.fillRectangle((int)x_pos,(int)y_pos, (int)block_width, (int)piece_height );
						}
						x_pos += block_width;
					}
					
					y_pos -= piece_height;
				}
			}finally{
				
				gc.dispose();

				canvas.redraw();
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
			initialize((Composite)event.getData(), true);
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

	protected interface
	PeerFilter
	{
		public boolean
		acceptPeer(
				PEPeer	peer );
	}
	
	private class
	ManagerData
		implements DownloadManagerPeerListener
	{
		final Object lock = this;
		
		private final long			add_time = SystemTime.getMonotonousTime();

		private DownloadManager		manager;

		private Point 				oldSize;
		private List<PEPeer> 		peers			= new ArrayList<>();
		private List<PEPiece>		pieces			= new ArrayList<>();
		private Map<PEPeer,int[]>	peer_hit_map = new HashMap<>();
		private int					me_hit_x;
		private int					me_hit_y;

		private
		ManagerData(
			DownloadManager	_manager )
		{
			manager	= _manager;

			manager.addPeerListener(this);
		}

		protected long
		getAddTime()
		{
			return( add_time );
		}

		private void
		delete()
		{
			manager.removePeerListener(this);
			
			manager.setUserData( DM_DATA_CACHE_KEY, null);

			peer_hit_map.clear();
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
			manager.addListener(
				new PEPeerManagerListenerAdapter(){
					
					
					@Override
					public void 
					pieceAdded(
						PEPeerManager 	manager, 
						PEPiece 		piece, 
						PEPeer 			for_peer )
					{
						synchronized( lock ){

							if ( !pieces.contains( piece )){

								pieces.add( piece );
							}
						}
					}
					
					@Override
					public void 
					pieceRemoved(
						PEPeerManager 	manager, 
						PEPiece 		piece ) 
					{
						synchronized( lock ){

							pieces.remove( piece );
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
						System.out.println( "req added: " + piece + "/" + peer + "/" + request );
						
						synchronized( lock ){
							
							if ( !pieces.contains( piece )){
								
								pieces.add( piece );
							}
						}
					}
				});
		}
		
		@Override
		public void 
		peerManagerRemoved(
			PEPeerManager manager )
		{
		}

		@Override
		public void 
		peerAdded(
			PEPeer peer) 
		{
			synchronized( lock ){
				
				peers.add( peer );
			}
		}

		@Override
		public void 
		peerRemoved(
			PEPeer peer ) 
		{
			synchronized( lock ){
				
				peers.remove( peer );
			}
		}
	}
}
