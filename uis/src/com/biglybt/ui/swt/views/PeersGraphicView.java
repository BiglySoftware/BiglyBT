/*
 * Created on 19 nov. 2004
 * Created by Olivier Chalouhi
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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
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
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.ui.swt.components.graphics.PieUtils;
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

/**
 * This is the "Swarm" View
 *
 * @author Olivier Chalouhi
 *
 */
public class PeersGraphicView
implements UIPluginViewToolBarListener, UISWTViewCoreEventListener
{
	public static String MSGID_PREFIX = "PeersGraphicView";

		// on OSX/Linux optimising arrow repaint doesn't work as the underlying canvas image is trashed between paint events
	
	private static final boolean FORCE_FULL_REPAINT = !Constants.isWindows;
	
	private final Object	DM_DATA_CACHE_KEY 		= new Object();	// not static as we want per-view data
	private final Object	PEER_DATA_KEY 			= new Object();	// not static as we want per-view data

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

	private TimerEventPeriodic	arrow_refresher;
	private boolean				arrow_redraw_pending;
	private boolean				full_redraw_pending;

	private class
	ManagerData
	implements DownloadManagerPeerListener
	{
		private AEMonitor peers_mon = new AEMonitor( "PeersGraphicView:peers" );

		private final long			add_time = SystemTime.getMonotonousTime();

		private DownloadManager		manager;

		private Point 				oldSize;
		private List<PEPeer> 			peers;
		private Map<PEPeer,int[]>		peer_hit_map = new HashMap<>();
		private int					me_hit_x;
		private int					me_hit_y;

		private
		ManagerData(
				DownloadManager	_manager )
		{
			manager	= _manager;

			peers = new ArrayList<>();

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
		public void peerManagerWillBeAdded(PEPeerManager	peer_manager ){}
		@Override
		public void peerManagerAdded(PEPeerManager manager) {}
		@Override
		public void peerManagerRemoved(PEPeerManager manager) {}

		@Override
		public void peerAdded(PEPeer peer) {
			try {
				peers_mon.enter();
				peers.add(peer);
			} finally {
				peers_mon.exit();
			}
		}

		@Override
		public void peerRemoved(PEPeer peer) {
			try {
				peers_mon.enter();
				peers.remove(peer);
			} finally {
				peers_mon.exit();
			}
		}
	}

	private Object			dm_data_lock = new Object();
	private ManagerData[]		dm_data = {};

	private boolean			always_show_dm_name;

	private final PeerFilter	peer_filter;

	public PeersGraphicView() {
		this( new PeerFilter() { public boolean acceptPeer(PEPeer peer) { return( true );}});
	}

	public PeersGraphicView(
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

	protected void dataSourceChanged(Object newDataSource) {
		if ( !comp_focused ){
			focus_pending_ds = newDataSource;
			return;
		}

		// defer this util here so that a blocking call to get the IP doesn't hang UI construction

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

		List<DownloadManager> newManagers = ViewUtils.getDownloadManagersFromDataSource( newDataSource, existing );

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

	protected Composite getComposite() {
		return canvas;
	}

	private String getData() {
		return "PeersGraphicView.title.full";
	}

	protected void initialize(Composite composite) {
		display = composite.getDisplay();

		canvas = new Canvas(composite,SWT.NO_BACKGROUND);

		canvas.addListener(SWT.MouseHover, new Listener() {
			@Override
			public void handleEvent(Event event) {

				int	x = event.x;
				int y = event.y;

				String tt = "";

				synchronized( dm_data_lock ){

					for ( ManagerData data: dm_data ){

						DownloadManager manager 	= data.manager;

						if ( 	x >= data.me_hit_x && x <= data.me_hit_x+OWN_SIZE &&
								y >= data.me_hit_y && y <= data.me_hit_y+OWN_SIZE ){

							if ( always_show_dm_name || dm_data.length > 1 ){

								tt = manager.getDisplayName() + "\r\n";
							}

							tt += DisplayFormatters.formatDownloadStatus( manager ) + ", " +
									DisplayFormatters.formatPercentFromThousands(manager.getStats().getCompleted());

							break;

						}else{

							PEPeer target = null;

							for ( Map.Entry<PEPeer,int[]> entry: data.peer_hit_map.entrySet()){

								int[] loc = entry.getValue();

								int	loc_x = loc[0];
								int loc_y = loc[1];

								if ( 	x >= loc_x && x <= loc_x+PEER_SIZE &&
										y >= loc_y && y <= loc_y+PEER_SIZE ){

									target = entry.getKey();

									break;
								}
							}

							if ( target != null ){

								PEPeerStats stats = target.getStats();

								String[] details = PeerUtils.getCountryDetails( target );

								String dstr = (details==null||details.length<2)?"":(" - " + details[0] + "/" + details[1]);
								/*
							if ( dm_map.size() > 1 ){

								tt = manager.getDisplayName() + "\r\n";
							}
								 */

								tt = target.getIp() + dstr + ", " +
										DisplayFormatters.formatPercentFromThousands(target.getPercentDoneInThousandNotation()) + "\r\n" +
										"Up=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( stats.getDataSendRate() + stats.getProtocolSendRate()) + ", " +
										"Down=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( stats.getDataReceiveRate() + stats.getProtocolReceiveRate());

								break;
							}
						}
					}
				}

				Utils.setTT(canvas, tt );
			}
		});

		canvas.addMouseListener(
				new MouseAdapter()
				{
					@Override
					public void
					mouseUp(
							MouseEvent event)
					{
						if ( event.button == 3 ){

							int	x = event.x;
							int y = event.y;

							PEPeer 			target 			= null;
							DownloadManager	target_manager 	= null;

							synchronized( dm_data_lock ){

								for ( ManagerData data: dm_data ){

									DownloadManager manager 	= data.manager;

									for( Map.Entry<PEPeer,int[]> entry: data.peer_hit_map.entrySet()){

										int[] loc = entry.getValue();

										int	loc_x = loc[0];
										int loc_y = loc[1];

										if ( 	x >= loc_x && x <= loc_x+PEER_SIZE &&
												y >= loc_y && y <= loc_y+PEER_SIZE ){

											target = entry.getKey();

											target_manager	= manager;

											break;
										}
									}

									if ( target != null ){

										break;
									}
								}
							}

							if ( target == null ){

								return;
							}

							Menu menu = canvas.getMenu();

							if ( menu != null && !menu.isDisposed()){

								menu.dispose();
							}

							menu = new Menu( canvas );

							PeersViewBase.fillMenu( menu, target, target_manager );

							final Point cursorLocation = Display.getCurrent().getCursorLocation();

							menu.setLocation( cursorLocation.x, cursorLocation.y );

							menu.setVisible( true );
						}
					}

					@Override
					public void
					mouseDoubleClick(
							MouseEvent event )
					{
						int	x = event.x;
						int y = event.y;

						synchronized( dm_data_lock ){

							for ( ManagerData data: dm_data ){

								DownloadManager manager 	= data.manager;

								if ( 	x >= data.me_hit_x && x <= data.me_hit_x+OWN_SIZE &&
										y >= data.me_hit_y && y <= data.me_hit_y+OWN_SIZE ){

									UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

									if (uiFunctions != null) {

										uiFunctions.getMDI().showEntryByID(
												MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS,
												manager);
									}
								}else{

									for( Map.Entry<PEPeer,int[]> entry: data.peer_hit_map.entrySet()){

										int[] loc = entry.getValue();

										int	loc_x = loc[0];
										int loc_y = loc[1];

										if ( 	x >= loc_x && x <= loc_x+PEER_SIZE &&
												y >= loc_y && y <= loc_y+PEER_SIZE ){

											PEPeer target = entry.getKey();

											// ugly code to locate any associated 'PeersView' that we can locate the peer in

											try{

												MdiEntry mdi_entry = UIFunctionsManager.getUIFunctions().getMDI().getEntry( MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, manager );

												if ( mdi_entry != null ){

													mdi_entry.setDatasource(new Object[] { manager });
												}

												Composite comp = canvas.getParent();

												while( comp != null ){

													if ( comp instanceof CTabFolder ){

														CTabFolder tf = (CTabFolder)comp;

														CTabItem[] items = tf.getItems();

														for ( CTabItem item: items ){

															UISWTViewCore view = (UISWTViewCore)item.getData("TabbedEntry");

															UISWTViewEventListener listener = view.getEventListener();

															if ( listener instanceof PeersView ){

																tf.setSelection( item );

																Event ev = new Event();

																ev.item = item;

																// manual setSelection doesn't file selection event - derp

																tf.notifyListeners( SWT.Selection, ev );

																((PeersView)listener).selectPeer( target );

																return;
															}
														}
													}

													comp = comp.getParent();
												}
											}catch( Throwable e ){

											}

											break;
										}
									}
								}
							}
						}
					}
				});

		// without this we get a transient blank when mousing in and out of the tab folder on OSX :(

		canvas.addPaintListener(
				new PaintListener(){
					@Override
					public void paintControl(PaintEvent e) {
						
						if (img != null && !img.isDisposed()){
							
							Rectangle bounds = img.getBounds();
							
							if ( bounds.width >= ( e.width + e.x ) && bounds.height >= ( e.height + e.y )){
								
								if ( full_redraw_pending || FORCE_FULL_REPAINT ){
									
									e.gc.drawImage(img, e.x, e.y, e.width, e.height, e.x, e.y, e.width,	e.height);
											
									full_redraw_pending = false;
									
									arrow_redraw_pending = FORCE_FULL_REPAINT;
								}
								
								if ( arrow_redraw_pending ){
	
									refreshArrows( e.gc, e.x, e.y, e.width, e.height );
	
									arrow_redraw_pending = false;
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
	}

	protected void refresh() 
	{
		synchronized( dm_data_lock ){

			if (canvas == null || canvas.isDisposed()){
				return;
			}

			Rectangle bounds = canvas.getClientArea();

			if ( bounds.width <= 0 || bounds.height <= 0 ){

				return;
			}

			if ( arrow_refresher == null ){

				arrow_refresher = 
						SimpleTimer.addPeriodicEvent(
								"PGV:AR",
								100,
								(ev)->{
									Utils.execSWTThread(()->{
										if ( canvas.isDisposed()){
											synchronized( dm_data_lock ){
												if ( arrow_refresher != null ){

													arrow_refresher.cancel();

													arrow_refresher = null;
												}
											}

											return;
										}
										arrow_redraw_pending = true;
										canvas.redraw();
									});
								});
			}
			Point panelSize = canvas.getSize();

			boolean clearImage = img == null || img.isDisposed()
					|| img.getBounds().width != bounds.width
					|| img.getBounds().height != bounds.height;
			if (clearImage) {
				if (img != null && !img.isDisposed()) {
					img.dispose();
				}
				//System.out.println("clear " + img);
				img = new Image(canvas.getDisplay(), bounds.width, bounds.height);
			}

			GC gc = new GC(img);

			try{
				int	pw = panelSize.x;
				int	ph = panelSize.y;

				int	num_dms = dm_data.length;

				if ( num_dms == 0  || pw == 0 || ph == 0 ){

					gc.setBackground(Colors.white);
					gc.fillRectangle(bounds);

					return;
				}

				int	h_cells;
				int v_cells;

				if ( ph <= pw ){

					v_cells = 1;
					h_cells = pw/ph;

					double f = Math.sqrt(((double)num_dms)/(v_cells*h_cells));

					int factor = (int)Math.ceil(f);

					h_cells *= factor;
					v_cells = factor;

				}else{

					v_cells = ph/pw;
					h_cells = 1;


					double f = Math.sqrt(((double)num_dms)/(v_cells*h_cells));

					int factor = (int)Math.ceil(f);

					v_cells *= factor;
					h_cells = factor;
				}

				ph = h_cells==1?(ph/num_dms):(ph/v_cells);
				pw = v_cells==1?(pw/num_dms):(pw/h_cells);

				//System.out.println( h_cells + "*" + v_cells + ": " + pw + "*" + ph );

				Point mySize 	= new Point( pw, ph );

				mySizeCache	= mySize;

				int	num = 0;

				Point lastOffset = null;

				for ( ManagerData data: dm_data ){

					DownloadManager manager 	= data.manager;

					PEPeer[] sortedPeers;
					try {
						data.peers_mon.enter();
						List<PEPeerTransport> connectedPeers = new ArrayList<>();
						for (PEPeer peer : data.peers) {
							if ( peer_filter.acceptPeer(peer)) {
								if (peer instanceof PEPeerTransport) {
									PEPeerTransport peerTransport = (PEPeerTransport) peer;
									if(peerTransport.getConnectionState() == PEPeerTransport.CONNECTION_FULLY_ESTABLISHED)
										connectedPeers.add(peerTransport);
								}
							}
						}

						sortedPeers = connectedPeers.toArray(new PEPeer[connectedPeers.size()]);
					} finally {
						data.peers_mon.exit();
					}

					if(sortedPeers == null) return;

					for (int i=0;i<3;i++){
						try{

							Arrays.sort(sortedPeers,peerComparator);

							break;

						}catch( IllegalArgumentException e ){

							// can happen as peer data can change during sort and result in 'comparison method violates its general contract' error
						}
					}

					int h = num%h_cells;
					int v = num/h_cells;

					Point myOffset	= new Point(h*pw,v*ph);

					manager.setUserData( DM_DATA_CACHE_KEY, new Object[]{ myOffset, sortedPeers });

					render( manager, data, gc, sortedPeers, mySize, myOffset);

					num++;

					lastOffset = myOffset;
				}

				int	rem_x = panelSize.x - (lastOffset.x + mySize.x );

				if ( rem_x > 0 ){

					gc.setBackground(Colors.white);
					gc.fillRectangle(lastOffset.x + mySize.x,lastOffset.y,rem_x,mySize.y);

				}

				int	rem_y = panelSize.y - (lastOffset.y + mySize.y );

				if ( rem_y > 0 ){

					gc.setBackground(Colors.white);
					gc.fillRectangle(0, lastOffset.y + mySize.y, panelSize.x, rem_y);

				}
			}finally{
				gc.dispose();

				full_redraw_pending = true;
				canvas.redraw();
			}
		}
	}

	private void
	render(
		DownloadManager		manager,
		ManagerData			data,
		GC					gc,
		PEPeer[] 			sortedPeers,
		Point				panelSize,
		Point				panelOffset )
	{
		data.peer_hit_map.clear();

		int	min_dim = Math.min( panelSize.x, panelSize.y );

		if ( min_dim <= 100 ){
			OWN_SIZE = OWN_SIZE_MIN;
		}else if ( min_dim >= 400 ){
			OWN_SIZE = OWN_SIZE_DEFAULT;
		}else{
			int s_diff = OWN_SIZE_MAX - OWN_SIZE_MIN;
			float rat = (min_dim - 100.0f)/(400-100);

			OWN_SIZE = OWN_SIZE_MIN + (int)(s_diff * rat );
		}


		int x0 = panelSize.x / 2;
		int y0 = panelSize.y / 2;
		int a = x0 - 20;
		int b = y0 - 20;
		if(a < 10 || b < 10){

			gc.setBackground(Colors.white);
			gc.fillRectangle(panelOffset.x,panelOffset.y,panelSize.x,panelSize.y);

			return;
		}

		if(data.oldSize == null || !data.oldSize.equals(panelSize)) {
			data.oldSize = panelSize;
			perimeter = 0;
			for(int i = 0 ; i < NB_ANGLES ; i++) {
				rs[i] = Math.sqrt(1/(deltaYXs[i] * deltaYXs[i] / (a*a) + deltaYYs[i] * deltaYYs[i] / (b * b)));
				perimeter += rs[i];
			}
		}
		Image buffer = new Image(display,panelSize.x,panelSize.y);
		GC gcBuffer = new GC(buffer);
		gcBuffer.setBackground(Colors.white);
		gcBuffer.setForeground(Colors.blue);
		gcBuffer.fillRectangle(0,0,panelSize.x,panelSize.y);

		try {
			gcBuffer.setTextAntialias(SWT.ON);
			gcBuffer.setAntialias(SWT.ON);
				// fix for bug https://stackoverflow.com/questions/23495420/swt-transformation-bug
			gcBuffer.setLineAttributes(new LineAttributes(1, SWT.CAP_FLAT, SWT.JOIN_MITER));
		} catch(Exception e) {
		}

		gcBuffer.setBackground(Colors.blues[Colors.BLUES_MIDLIGHT]);

		int nbPeers = sortedPeers.length;

		int iAngle = 0;
		double currentPerimeter = 0;
		//double angle;
		double r;

		for(int i = 0 ; i < nbPeers ; i++) {
			PEPeer peer = sortedPeers[i];
			do {
				//angle = angles[iAngle];
				r     = rs[iAngle];
				currentPerimeter += r;
				if(iAngle + 1 < NB_ANGLES) iAngle++;
			} while( currentPerimeter < i * perimeter / nbPeers);

			//angle = (4 * i - nbPeers) * Math.PI  / (2 * nbPeers) - Math.PI / 2;

			int percent_received 	= peer.getPercentDoneOfCurrentIncomingRequest();
			int percent_sent 		= peer.getPercentDoneOfCurrentOutgoingRequest();

			// set up base line state


			boolean	drawLine = false;

			// unchoked

			if ( !peer.isChokingMe() || percent_received >= 0 ){
				gcBuffer.setForeground(Colors.blues[1] );
				drawLine = true;
			}

			// unchoking

			if ( !peer.isChokedByMe() || percent_sent >= 0 ){
				gcBuffer.setForeground(Colors.blues[3]);
				drawLine = true;
			}

			// receiving from choked peer (fast request in)

			if ( !peer.isChokingMe() && peer.isUnchokeOverride() && peer.isInteresting()){
				gcBuffer.setForeground(Colors.green);
				drawLine = true;
			}

			// sending to choked peer (fast request out)

			if ( peer.isChokedByMe() && percent_sent >= 0 ){
				gcBuffer.setForeground(Colors.green);
				drawLine = true;
			}

			int x1 = x0 + (int) (r * deltaYXs[iAngle]);
			int y1 = y0 + (int) (r * deltaYYs[iAngle]);

			PeerData peer_data = (PeerData)peer.getUserData( PEER_DATA_KEY );

			if ( peer_data == null ){

				peer_data = new PeerData();

				peer.setUserData( PEER_DATA_KEY, peer_data );
			}
			
			peer_data.line_colour = drawLine?gcBuffer.getForeground():Colors.white;

			drawArrows( peer, gcBuffer, drawLine, x0, y0, x1, y1, r, iAngle, FORCE_FULL_REPAINT );
			
			gcBuffer.setBackground(Colors.blues[Colors.BLUES_MIDDARK]);
			if(peer.isSnubbed()) {
				gcBuffer.setBackground(Colors.grey);
			}

			int peer_x = x1 - PEER_SIZE / 2;
			int peer_y = y1 - PEER_SIZE / 2;

			data.peer_hit_map.put( peer, new int[]{ peer_x + panelOffset.x, peer_y + panelOffset.y });

			Image flag = ImageRepository.getCountryFlag( peer, false );
			if ( flag != null ){
				PieUtils.drawPie(gcBuffer, flag, peer_x, peer_y,PEER_SIZE,PEER_SIZE,peer.getPercentDoneInThousandNotation() / 10, true );
			}else{

				PieUtils.drawPie(gcBuffer, peer_x, peer_y,PEER_SIZE,PEER_SIZE,peer.getPercentDoneInThousandNotation() / 10);
			}
		}

		gcBuffer.setBackground(Colors.blues[Colors.BLUES_MIDDARK]);

		data.me_hit_x = x0 - OWN_SIZE / 2;
		data.me_hit_y = y0 - OWN_SIZE / 2;

		PieUtils.drawPie(gcBuffer, data.me_hit_x, data.me_hit_y,OWN_SIZE,OWN_SIZE,manager.getStats().getCompleted() / 10);

		if ( my_flag != null ){
			Image img = my_flag;

			String[] nets = data.manager.getDownloadState().getNetworks();

			for ( String net: nets ){
				if ( net == AENetworkClassifier.AT_PUBLIC ){
					img = my_flag;
					break;
				}else{
					img = ImageRepository.getCountryFlag( AENetworkClassifier.AT_I2P, false );
					if ( img == null ){
						img = my_flag;
					}
				}
			}
			PieUtils.drawPie(gcBuffer, img, data.me_hit_x, data.me_hit_y,OWN_SIZE,OWN_SIZE,manager.getStats().getCompleted() / 10, false );
		}
		
		data.me_hit_x += panelOffset.x;
		data.me_hit_y += panelOffset.y;

		gcBuffer.dispose();

		gc.drawImage(buffer,panelOffset.x,panelOffset.y);
		
		buffer.dispose();
	}

	static final int BUCKET_LENGTH 				= 1000;
	static final int BUCKET_CAPACITY 			= DiskManager.BLOCK_SIZE;
	static final int BIG_BUCKET_CAPACITY_MULT	= 20;
	static final int BIG_BUCKET_CAPACITY 		= BIG_BUCKET_CAPACITY_MULT*BUCKET_CAPACITY;
	static final int MAX_BUCKETS				= 10;

	static final int BLOB_R = 3;

	private void
	drawArrows(
		PEPeer	peer,
		GC		gc,
		boolean	drawLine,
		int		x0,
		int		y0,
		int		x1,
		int		y1,
		double	r,
		int		iAngle,
		boolean	lineOnly )
	{
		PeerData peer_data = (PeerData)peer.getUserData( PEER_DATA_KEY );

		if ( peer_data == null ){

			return;
		}
	
		PEPeerStats stats = peer.getStats();

		if ( stats == null ){
			
			return;
		}
				
		peer_data.update( stats );
		
		Transform trans = new Transform( gc.getDevice());

		try{
			float degrees = (float)( 180*(angles[iAngle])/Math.PI)+90;
	
			trans.translate( x0,  y0 );
	
			trans.rotate(degrees);
	
			gc.setTransform(trans);
		
			if ( drawLine ){
								
				gc.setForeground( peer_data.line_colour );
				
				gc.drawLine( 0, 0,(int)r, 0 );
				
				if ( lineOnly ){
					
					return;
				}
			}
		
			List<BucketData>	down_buckets 		= peer_data.down_data.buckets;
			List<BucketData>	down_dead_buckets 	= peer_data.down_data.dead_buckets;
			List<BucketData>	up_buckets 			= peer_data.up_data.buckets;
			List<BucketData>	up_dead_buckets 	= peer_data.up_data.dead_buckets;
	
			if ( 	down_buckets.isEmpty() && 
					down_dead_buckets.isEmpty() &&
					up_buckets.isEmpty() && 
					up_dead_buckets.isEmpty()){
				
				return;
			}

			if ( !FORCE_FULL_REPAINT ){
				
				gc.setBackground( Colors.white );
	
				List[] all_buckets = { down_buckets, down_dead_buckets, up_buckets, up_dead_buckets };
				
				for ( List buckets: all_buckets ){
					
					for ( BucketData bd: (List<BucketData>)buckets ){
					
						double old_cx = bd.cx;
						double cy = 0;
						
						if ( old_cx >= 0 ){
						
							boolean is_down = buckets == down_buckets;
							boolean is_up	= buckets == up_buckets;
							
							boolean skip = false;
							
							if ( is_down || is_up ){
								
								int percent = ( bd.bytes * 100 ) / bd.capacity;
								
								if ( is_down ){
									
									percent = 100 - percent;
								}
							
								double new_cx = OWN_SIZE/2 + BLOB_R + 2 + ( r - PEER_SIZE/2 - OWN_SIZE/2 - BLOB_R*2 -4 ) * percent / 100.0;	
								
								if ( old_cx == new_cx ){
									
									skip = true;
								}
							}
							
							if ( !skip ){
								
								gc.fillOval( (int)( old_cx-BLOB_R-1 ), (int)( cy-BLOB_R-1), BLOB_R*2+1+2, BLOB_R*2+1+2 );
								
								gc.setForeground( peer_data.line_colour );
								
								gc.drawLine((int)( old_cx-BLOB_R-1), 0, (int)( old_cx+BLOB_R+1 ), 0 );
							}
						}
					}
				}
			}
			
			down_dead_buckets.clear();
			up_dead_buckets.clear();

			List[] data_buckets = { down_buckets, up_buckets };
			
			for ( List buckets: data_buckets ){
				
				boolean is_down = buckets == down_buckets;
												
				for ( BucketData bd: (List<BucketData>)buckets ){
				
					Color bg;
					
					boolean is_big = bd.capacity == BIG_BUCKET_CAPACITY;
					
					if ( is_down ){
						bg=is_big?Colors.maroon:Colors.blues[Colors.BLUES_MIDDARK];
					}else{
						bg=is_big?Colors.fadedGreen:Colors.green;
					}
					
					gc.setBackground( bg );

					int percent = ( bd.bytes * 100 ) / bd.capacity;
					
					if ( is_down ){
						
						percent = 100 - percent;
					}
					
					double cx = OWN_SIZE/2 + BLOB_R + 2 + ( r - PEER_SIZE/2 - OWN_SIZE/2 - BLOB_R*2 -4 ) * percent / 100.0;	
					double cy = 0;							
	
					gc.fillOval( (int)( cx-BLOB_R ), (int)( cy-BLOB_R), BLOB_R*2+1, BLOB_R*2+1 );
		
					bd.cx = cx;
				}
			}
		}finally{
		
			trans.dispose();

			gc.setTransform( null );
		}
	}
	
	static class
	PeerData
	{
		Color line_colour;
		
		PeerSubData	up_data 	= new PeerSubData();
		PeerSubData	down_data 	= new PeerSubData();
		
		PeerData()
		{
			
		}

		void
		update(
			PEPeerStats	stats )
		{
			up_data.update( stats.getDataSendRate());
			
			down_data.update( stats.getDataReceiveRate());
		}
	}
	
	static class
	PeerSubData
	{
		int					bucket_capacity = BUCKET_CAPACITY;
		
		long						last_update = -1;
		
		long						bytes	= 0;
		LinkedList<BucketData>		buckets = new LinkedList<>();
		
		long				last_tbc_update;
		long				last_tbc_bytes	= -1;
		
		int					target_bucket_count;
		
		List<BucketData>	dead_buckets = new ArrayList<>();

		Color line_colour;
		
		PeerSubData()
		{
			
		}

		void
		update(
			long	rate_bytes_per_sec )
		{
			long now = SystemTime.getMonotonousTime();
			
			if ( last_update == -1 ){
				
				last_update = now;
				
				return;
			}
			
			long time_diff = now - last_update;
			
			if ( time_diff == 0 ){
				
				return;
			}
			
			last_update = now;

			long bytes_diff = ( rate_bytes_per_sec * time_diff )/1000;
			
			if ( bytes_diff == 0 && rate_bytes_per_sec > 0 ){
				
				bytes_diff = 1;
			}
						
			bytes += bytes_diff;
									
			if ( now - last_tbc_update > 1000 ){
				
				if ( rate_bytes_per_sec > MAX_BUCKETS*BUCKET_CAPACITY ){
					
					bucket_capacity = BIG_BUCKET_CAPACITY;
					
				}else if ( rate_bytes_per_sec < MAX_BUCKETS*BUCKET_CAPACITY / 2 ){
					
					bucket_capacity = BUCKET_CAPACITY;
				}
				
				last_tbc_update = now;
				
				long tbc_bytes_diff;
				
				if ( last_tbc_bytes == -1 ){
					
					tbc_bytes_diff = bytes;
					
				}else{
					
					tbc_bytes_diff = bytes - last_tbc_bytes;
				}
				
				last_tbc_bytes = bytes;
			
				if ( tbc_bytes_diff == 0 ){
					
					target_bucket_count = 0;
					
					dead_buckets.addAll( buckets );
					
					buckets.clear();
					
				}else{
					
					target_bucket_count = (int)((tbc_bytes_diff + bucket_capacity-1 )/ bucket_capacity ); 
												
					target_bucket_count = Math.min( target_bucket_count, MAX_BUCKETS );
					
					int bucket_diff = target_bucket_count - buckets.size();
					
					if ( bucket_diff > 0 ){
										
						for ( int i=0;i<bucket_diff;i++){
								
							buckets.add( new BucketData(bucket_capacity));
						}
					}
				}
			}
			
			if ( bytes_diff > 0 && target_bucket_count > 0 ){
							
				if ( buckets.size() > 1 ){
					
					long space_between_buckets = bucket_capacity / ( target_bucket_count + 1 );
					
					int	last_b = -1;

					for ( BucketData bd: buckets ){
						
						int b = bd.bytes;
								
						if ( last_b == -1 ){
							
							last_b = b;
							
							bd.offset = 0;
							
						}else{
						
							int diff = b - last_b;
								
							if ( diff > 0 && diff < space_between_buckets ){
									
								int max;
								
								if ( bd.capacity == BIG_BUCKET_CAPACITY ){
									
									max = 256*BIG_BUCKET_CAPACITY_MULT;
									
								}else{
									
									max = 256;
								}
								
								bd.offset = Math.min( max, diff );
								
							}else{
								
								bd.offset = 0;
							}
						}
					}
				}else{
					
					buckets.getFirst().offset = 0;
				}
				
				long bytes_per_bucket = Math.max( 1, bytes_diff / target_bucket_count );
			
				ListIterator<BucketData> it = null;
				
				while( bytes_diff > 0 ){
					
					if ( it == null || !it.hasPrevious()){
						
						 it = buckets.listIterator( buckets.size());
					}
					
					BucketData bd = it.previous();

					long avail = Math.min( bytes_diff , bytes_per_bucket + bd.offset );					
				
					bd.offset = 0;
					
					long bucket_bytes = bd.bytes;
					
					long rem = bd.capacity - bucket_bytes;
					
					if ( rem > avail ){
						
						bd.bytes += avail;
						
						bytes_diff -= avail;
						
					}else{
							
						bytes_diff -= rem;
						
						bd.bytes = 0;
							
						it.remove();
							
						if ( buckets.size() < target_bucket_count ){
						
							bd.capacity = bucket_capacity;
							
							buckets.addFirst( bd );
						}
						
						it = null;
					}
				}
			}
		}
	}

	static class
	BucketData
	{
		int capacity;
		
		int		bytes;
		int		offset;
		double	cx	= -1;
		
		BucketData(
			int		_capacity )
		{
			capacity = _capacity;
		}
	}

	protected void 
	refreshArrows(
		GC		gc,
		int		x,
		int		y,
		int		width,
		int		height )
	{
		//long start = SystemTime.getMonotonousTime();
		synchronized( dm_data_lock ){

			if ( canvas == null || canvas.isDisposed()){

				return;
			}

			if ( img == null || img.isDisposed()){

				return;
			}

			if ( mySizeCache == null ){

				return;
			}

			try {
				gc.setAntialias(SWT.ON);
				gc.setLineAttributes(new LineAttributes(1, SWT.CAP_FLAT, SWT.JOIN_MITER));
			} catch(Exception e) {
			}

			gc.setBackground( Colors.white );

			for ( ManagerData data: dm_data ){

				DownloadManager manager 	= data.manager;

				Object[] cache = (Object[])manager.getUserData( DM_DATA_CACHE_KEY);

				if ( cache != null ){

					renderArrows( manager, data, gc, (PEPeer[])cache[1], mySizeCache, (Point)cache[0]);
				}
			}
		}
		//System.out.println("ref: " + (SystemTime.getMonotonousTime() - start ));
	}

	private void
	renderArrows(
		DownloadManager		manager,
		ManagerData			data,
		GC					gc,
		PEPeer[] 			sortedPeers,
		Point				panelSize,
		Point				panelOffset )
	{
		int x0 = panelOffset.x + panelSize.x / 2;
		int y0 = panelOffset.y + panelSize.y / 2;

		int nbPeers = sortedPeers.length;

		int iAngle = 0;
		double currentPerimeter = 0;
		//double angle;
		double r;

		for(int i = 0 ; i < nbPeers ; i++) {
			PEPeer peer = sortedPeers[i];
			do {
				//angle = angles[iAngle];
				r     = rs[iAngle];
				currentPerimeter += r;
				if(iAngle + 1 < NB_ANGLES) iAngle++;
			} while( currentPerimeter < i * perimeter / nbPeers);

			int x1 = x0 + (int) (r * deltaYXs[iAngle]);
			int y1 = y0 + (int) (r * deltaYYs[iAngle]);

			drawArrows( peer, gc, false, x0, y0, x1, y1, r, iAngle, false );
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

		case UISWTViewEvent.TYPE_FOCUSGAINED:
			String id = "DMDetails_Swarm";

			setFocused( true );	// do this before next code as it can pick up the corrent 'manager' ref

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
		case UISWTViewEvent.TYPE_FOCUSLOST:
			setFocused( false );
			SelectedContentManager.clearCurrentlySelectedContent();
			break;
		case UISWTViewEvent.TYPE_REFRESH:
			refresh();
			break;
		}

		return true;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.toolbar.UIToolBarActivationListener#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	 */
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
			Object datasource) {
		return false; // default handler will handle it
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	 */
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
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
}
