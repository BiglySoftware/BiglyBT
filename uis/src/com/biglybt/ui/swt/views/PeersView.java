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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.util.*;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo2;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.StringCompareUtils;

import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;

/**
 * View showing list of peers for {@link DownloadManager}
 * <p/>
 * TODO: Support multiple DS
 */

public class PeersView
	extends PeersViewBase
	implements DownloadManagerPeerListener, TableDataSourceChangedListener,
	ViewTitleInfo2, UIPluginViewToolBarListener
{
	public static final String MSGID_PREFIX = "PeersView";

	private DownloadManager manager;

	private List<Reference<PEPeer>> select_peers_pending = new ArrayList<>();
	
	private TimerEventPeriodic timerPeerCountUI;
	private String textIndicator;
	private long countWentToZeroTime = -1;

	public PeersView() {
		super(MSGID_PREFIX, false);
	}

	@Override
	public TableViewSWT<PEPeer> 
	initYourTableView()
	{
		// Clear manager that was set by parentDataSourceChanged (for title ui)
		// no need to removeListeners, as they weren't added
		manager = null;
	
		tv = initYourTableView( TableManager.TABLE_TORRENT_PEERS);

		// no need to trigger, super will
		tv.addTableDataSourceChangedListener(this, false);

		return( tv );
	}



	@Override
	public void parentDataSourceChanged(Object newParentDataSource) {
		super.parentDataSourceChanged(newParentDataSource);

		if (tv != null && !tv.isDisposed()) {
			return;
		}
		DownloadManager newManager = DataSourceUtils.getDM(newParentDataSource);
		if (newManager != manager) {
			manager = newManager;
			buildTitleInfoTimer();
		}
	}
	
	private void buildTitleInfoTimer() {
		if (manager == null) {
			if (timerPeerCountUI != null) {
				timerPeerCountUI.cancel();
				timerPeerCountUI = null;
			}
			// one last update to clear indicator when users still on tab
			updateTitle( true );
			
		} else if (timerPeerCountUI == null) {
			
			timerPeerCountUI = SimpleTimer.addPeriodicEvent("PeerSumUI", 1000, e -> {
				
				updateTitle( false);
			});
		}
	}

	private void updateTitle( boolean force ) {
		int count = 0;
		if (manager != null) {
			PEPeerManager peerManager = manager.getPeerManager();
			if (peerManager != null) {
				count = peerManager.getNbPeers() + peerManager.getNbSeeds();
			}
		}
		
		if ( force ){
			
			if ( count == 0 ){
				
				count = -1;
			}
		}else if ( count == 0 ){
			
			if ( countWentToZeroTime== -1 ){
				
				count = -1;
				
			}else if ( countWentToZeroTime == 0 ){
				
				countWentToZeroTime = SystemTime.getMonotonousTime();
				
			}else{
				if ( countWentToZeroTime > 0 && SystemTime.getMonotonousTime() - countWentToZeroTime > 30*1000 ){
					
					count = -1;
				}
			}
		}else{
			
			countWentToZeroTime = 0;
		}
		
		String newTextIndicator = count == -1 ? null : "" + count;
		if (!StringCompareUtils.equals(textIndicator, newTextIndicator)) {
			textIndicator = newTextIndicator;
			ViewTitleInfoManager.refreshTitleInfo(PeersView.this);
		}
	}

	@Override
	public void tableDataSourceChanged(Object newDataSource) {
		DownloadManager newManager = DataSourceUtils.getDM(newDataSource);

		if (newManager == manager) {
			return;
		}

		if (manager != null) {
			manager.removePeerListener(this);
		}

		manager = newManager;

		buildTitleInfoTimer();

		if (tv == null || tv.isDisposed()) {
			return;
		}

		tv.removeAllTableRows();

		if (manager != null ){
			manager.addPeerListener(this, false);
			addExistingDatasources();
		}
	}


	@Override
	public void 
	tableLifeCycleEventOccurred(
			TableView tv, int eventType,
			Map<String, Object> data) 
	{
		super.tableLifeCycleEventOccurred(tv, eventType, data);
		
		switch (eventType) {
		case EVENT_TABLELIFECYCLE_INITIALIZED:
			if (manager != null) {
				manager.removePeerListener(this);
				manager.addPeerListener(this, false);
			}
			addExistingDatasources();
			break;

			case EVENT_TABLELIFECYCLE_DESTROYED: {
				if (manager != null) {
					manager.removePeerListener(this);
					// don't clear manager, we still use it for title
					buildTitleInfoTimer();
				}

				Object[] selected = tv.getSelectedDataSources( true );
					
				synchronized( select_peers_pending ){
					
					select_peers_pending.clear();
					
					for ( Object ds: selected ){
					
						if ( ds instanceof PEPeer ){
						
							select_peers_pending.add( new WeakReference<>((PEPeer)ds ));
						}
					}
				}
				
				break;
			}
		}
	}

	@Override
	public void 
	fillMenu(
		String sColumnName, Menu menu) 
	{
		fillMenu(menu, tv, shell, manager);
		
		new MenuItem (menu, SWT.SEPARATOR);
	}

	@Override
	public void addThisColumnSubMenu(String columnName, Menu menuThisColumn) {
		
		if ( addPeersMenu( manager, columnName, menuThisColumn, new PEPeer[0] )){

			new MenuItem( menuThisColumn, SWT.SEPARATOR );
		}
	}



	/* DownloadManagerPeerListener implementation */
	@Override
	public void peerAdded(PEPeer created) {
		tv.addDataSource(created);
	}

	@Override
	public void peerRemoved(PEPeer removed) {
		tv.removeDataSource(removed);
	}

	public void
	selectPeer(
		PEPeer		peer )
	{
		List<PEPeer> peers = new ArrayList<>();
		
		peers.add( peer );
		
		showPeers( peers, 0 );
	}

	private void
	showPeers(
		List<PEPeer>	peers,
		int				attempt )
	{
		
		if ( attempt > 10 || peers.isEmpty()){

			return;
		}

		if ( tv == null ){
			
				// view not yet constructed
	
			synchronized( select_peers_pending ){
				
				select_peers_pending.clear();
				
				for ( PEPeer peer: peers ){
				
					select_peers_pending.add( new WeakReference<>(peer));
				}
			}
			
			return;
		}
		
		// need to insert an async here as if we are in the process of switching to this view the
		// selection sometimes get lost. grrr
		// also, due to the way things work, as the table is building it is possible to select the entry
		// only to have the selection get lost due to the table re-calculating stuff, so we keep trying for
		// a while until we get an affirmation that it really is visible

		Utils.execSWTThreadLater(
				attempt==0?1:10,
				new Runnable()
				{
					@Override
					public void
					run()
					{
						TableRowCore row1 = tv.getRow( peers.get(0));

						if ( row1 == null ){

							if ( attempt == 0 ){
								
								synchronized( select_peers_pending ){
									
									select_peers_pending.clear();
	
									for ( PEPeer peer: peers ){
									
										select_peers_pending.add( new WeakReference<>(peer));
									}
								}
								
								return;
							}
						}else{
							
							List<TableRowCore> rows = new ArrayList<>();
							
							rows.add( row1 );
							
							for ( PEPeer peer: peers.subList( 1, peers.size())){
								
								TableRowCore row = tv.getRow( peer );
								
								if ( row != null ){
									
									rows.add( row );
								}
							}
							
							tv.setSelectedRows( rows.toArray( new TableRowCore[ rows.size() ]));

							tv.showRow( row1 );

							if ( row1.isVisible()){
								
								return;
							}
						}

						showPeers( peers, attempt+1 );
					}
				});
	}

	@Override
	public void peerManagerWillBeAdded(PEPeerManager	peer_manager ){}
	@Override
	public void peerManagerAdded(PEPeerManager manager)
	{
		if ( getShowLocalPeer()){
		
			tv.addDataSource( manager.getMyPeer());
		}
	}
	@Override
	public void 
	peerManagerRemoved(PEPeerManager manager) 
	{
		tv.removeAllTableRows();
	}
	
	@Override
	protected void
	setShowLocalPeer(
		boolean		b )
	{	
		super.setShowLocalPeer(b);
		
		if (manager == null || tv == null || tv.isDisposed()) {
			
			return;
		}
		
		PEPeerManager pm = manager.getPeerManager();
		
		if ( pm != null ){
			
			PEPeer my_peer = pm.getMyPeer();
			
			if ( b ){
				
				tv.addDataSource( my_peer );
			}else{
				
				tv.removeDataSource( my_peer );
			}
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

		PEPeer[] dataSources = manager.getCurrentPeers();
		
		if ( dataSources != null && dataSources.length > 0) {
			
			tv.addDataSources(dataSources);
		}

		if ( getShowLocalPeer()){
			
			PEPeerManager pm = manager.getPeerManager();
			
			if ( pm != null ){
			
				tv.addDataSource( pm.getMyPeer());
			}
		}
		
		List<PEPeer> to_show = new ArrayList<>();
		
		synchronized( select_peers_pending ){
			
			for( Reference<PEPeer> ref: select_peers_pending ){
				
				PEPeer peer = ref.get();

				if ( peer != null ){
					
					to_show.add( peer );
				}
			}

			select_peers_pending.clear();
		}
		
		if ( !to_show.isEmpty()){
			
			showPeers( to_show, 1 );
		}
	}

	@Override
	protected void
	updateSelectedContent()
	{
		Object[] dataSources = tv.getSelectedDataSources(true);

		if ( dataSources.length == 0 ){
			
			String id = "DMDetails_Peers";
	
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
					sc[i] = new SelectedContent( "peer: " + ((PEPeer)ds).getIp());
				}else{
					sc[i] = new SelectedContent( "peer: "  + ds );
				}
			}

			SelectedContentManager.changeCurrentlySelectedContent(tv.getTableID(),
					sc, tv);
		}
	}
	
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		if (event.getType() == UISWTViewEvent.TYPE_CREATE) {
			event.getView().setDestroyOnDeactivate(true);
		} else if (event.getType() == UISWTViewEvent.TYPE_DESTROY) {
			buildTitleInfoTimer();
		}

		return( super.eventOccurred(event));
	}

	@Override
	public void titleInfoLinked(MultipleDocumentInterface mdi, MdiEntry mdiEntry) {
		
	}

	@Override
	public Object getTitleInfoProperty(int propertyID) {
		if (propertyID == ViewTitleInfo2.TITLE_INDICATOR_TEXT) {
			return textIndicator;
		}
		return null;
	}

	@Override
	public boolean
	isActive()
	{
		if (tv == null || !tv.isVisible()) {
			return( false );
		}

		return( !tv.getSelectedDataSources().isEmpty());
	}
	
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		boolean hasPeer = tv != null && tv.getSelectedRowsSize() > 0;
		
		if ( hasPeer ){
			List<Object> selectedDataSources = tv.getSelectedDataSources();
			if ( selectedDataSources.size() == 1 ){
				Object ds = selectedDataSources.get(0);
				if ( ds instanceof PEPeer && ((PEPeer)ds).isMyPeer()){
					hasPeer = false;
				}
			}
		}
		list.put("remove", hasPeer ? UIToolBarItem.STATE_ENABLED : 0);
	}

	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType, Object datasource) {
		if (item.getID().equals("remove")) {
			List<Object> selectedDataSources = tv.getSelectedDataSources();
			for (Object dataSource : selectedDataSources) {
				if (dataSource instanceof PEPeer) {
					PEPeer peer = (PEPeer) dataSource;
					if ( !peer.isMyPeer()){
						peer.getManager().removePeer(peer,"Peer kicked", Transport.CR_PEER_CHURN );
					}
				}
			}
			return true;
		}
		return false;
	}
}
