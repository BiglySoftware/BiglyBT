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


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerPieceListener;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.util.DataSourceUtils;


/**
 * Pieces List View
 * <p/>
 * Features:<br/>
 * <li>List of partial pieces</li>
 * <li>double-click to show on Piece Map</li>
 */

public class PiecesView
	extends PiecesViewBase
	implements DownloadManagerPeerListener, DownloadManagerPieceListener, TableDataSourceChangedListener
{
	public static final String MSGID_PREFIX = "PiecesView";

	
	private DownloadManager 		manager;
		
	/**
	 * Initialize
	 *
	 */
	public PiecesView() {
		super(MSGID_PREFIX);
	}
	
	@Override
	protected List<PEPeerManager> 
	getPeerManagers()
	{
		DownloadManager dm = manager;
		
		PEPeerManager pm = dm==null?null:dm.getPeerManager();
		
		if ( pm == null ){
			
			return( Collections.EMPTY_LIST );
			
		}else{
		
			return( Arrays.asList( pm ));
		}
	}
	
	@Override
	public TableViewSWT<PEPiece> 
	initYourTableView() 
	{
		TableViewSWT<PEPiece>  tv = initYourTableView( TableManager.TABLE_TORRENT_PIECES );
		
		tv.addTableDataSourceChangedListener(this, true);

		return( tv );
	}

	@Override
	public void 
	tableDataSourceChanged(
		Object newDataSource ) 
	{
		DownloadManager newManager = DataSourceUtils.getDM(newDataSource);

		if (newManager == manager) {
			return;
		}

		if (manager != null) {
			manager.removePeerListener(this);
			manager.removePieceListener(this);
		}

		manager = newManager;

		clearUploadingPieces();
		
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

	protected void
	updateSelectedContent()
	{
		Object[] dataSources = tv.getSelectedDataSources(true);

		if ( dataSources.length == 0 ){

	      	String id = "DMDetails_Pieces";
	      	
	      	DownloadManager manager = this.manager;
	      	
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
	
	protected void tableViewInitialized() {
	}

	protected void tableViewDestroyed() {

		if (manager != null) {
			manager.removePeerListener(this);
			manager.removePieceListener(this);
			manager = null;
		}
	}

	@Override
	public void pieceAdded(PEPiece created) {
		tv.addDataSource(created);
	}

	@Override
	public void pieceRemoved(PEPiece removed) {
		tv.removeDataSource(removed);
	}

	@Override
	public void 
	peerAdded(
		PEPeer peer )
	{	
	}
	
	@Override
	public void 
	peerRemoved(
		PEPeer peer )
	{
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
	}
	
	@Override
	public void 
	peerManagerRemoved(
		PEPeerManager	manager )
	{
		tv.removeAllTableRows();
		
		clearUploadingPieces();
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
		
		if ( updateUploadingPieces( false )){
			
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
}
