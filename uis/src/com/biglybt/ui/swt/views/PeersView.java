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

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;


import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;

import com.biglybt.pif.ui.tables.TableManager;

import com.biglybt.ui.common.table.*;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventImpl;
import com.biglybt.ui.swt.views.table.TableViewSWT;

/**
 * @author Olivier
 * @author TuxPaper
 *         2004/Apr/20: Use TableRowImpl instead of PeerRow
 *         2004/Apr/20: Remove need for tableItemToObject
 *         2004/Apr/21: extends TableView instead of IAbstractView
 * @author MjrTom
 *			2005/Oct/08: Add PieceItem
 */

public class PeersView
	extends PeersViewBase
	implements DownloadManagerPeerListener, TableDataSourceChangedListener 
{
	public static final String MSGID_PREFIX = "PeersView";

	private DownloadManager manager;

	private boolean enable_tabs = true;


	private boolean 	comp_focused;
	private Object 		focus_pending_ds;
	private PEPeer		select_peer_pending;


	public PeersView() {
		super(MSGID_PREFIX, false);
	}

	@Override
	public UISWTViewCoreEventListenerEx
	getClone()
	{
		return( new PeersView());
	}

	@Override
	public CloneConstructor
	getCloneConstructor()
	{
		return( 
			new CloneConstructor()
			{
				public Class<? extends UISWTViewCoreEventListenerEx>
				getCloneClass()
				{
					return( PeersView.class );
				}
				
				public java.util.List<Object>
				getParameters()
				{
					return( null );
				}
			});
	}
	
	public TableViewSWT<PEPeer> 
	initYourTableView()
	{
		initYourTableView( TableManager.TABLE_TORRENT_PEERS, enable_tabs );

		tv.addTableDataSourceChangedListener(this, true);

		return( tv );
	}



	private void
	setFocused( boolean foc )
	{
		if ( foc ){

			comp_focused = true;

			dataSourceChanged( focus_pending_ds );

		}else{

			focus_pending_ds = manager;

			dataSourceChanged( null );

			comp_focused = false;
		}
	}

	@Override
	public void tableDataSourceChanged(Object newDataSource) {
		if ( !comp_focused ){
			focus_pending_ds = newDataSource;
			return;
		}

		DownloadManager newManager = ViewUtils.getDownloadManagerFromDataSource( newDataSource );

		if (newManager == manager) {
			tv.setEnabled(manager != null);
			return;
		}

		if (manager != null) {
			manager.removePeerListener(this);
		}

		manager = newManager;

		if (tv.isDisposed()) {
			return;
		}

		tv.removeAllTableRows();
		tv.setEnabled(manager != null);

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

		case EVENT_TABLELIFECYCLE_DESTROYED:
			if (manager != null) {
				manager.removePeerListener(this);
			}

			select_peer_pending = null;
			break;
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

		if ( addPeersMenu( manager, menuThisColumn )){

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
		showPeer( peer, 0 );
	}

	private void
	showPeer(
			final PEPeer		peer,
			final int			attempt )
	{
		if ( attempt > 10 || tv == null ){

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
						TableRowCore row = tv.getRow( peer );

						if ( row == null ){

							if ( attempt == 0 ){

								select_peer_pending = peer;

								return;
							}
						}else{

							tv.setSelectedRows( new TableRowCore[]{ row } );

							tv.showRow( row );

							if ( row.isVisible()){

								return;
							}
						}

						showPeer( peer, attempt+1 );
					}
				});
	}

	@Override
	public void peerManagerWillBeAdded(PEPeerManager	peer_manager ){}
	@Override
	public void peerManagerAdded(PEPeerManager manager) {	}
	@Override
	public void peerManagerRemoved(PEPeerManager manager) {
		tv.removeAllTableRows();
	}

	/**
	 * Add datasources already in existance before we called addListener.
	 * Faster than allowing addListener to call us one datasource at a time.
	 */
	private void addExistingDatasources() {
		if (manager == null || tv.isDisposed()) {
			return;
		}

		PEPeer[] dataSources = manager.getCurrentPeers();
		if (dataSources != null && dataSources.length > 0) {

			tv.addDataSources(dataSources);
			tv.processDataSourceQueue();
		}

		if ( select_peer_pending != null ){

			showPeer( select_peer_pending, 1 );

			select_peer_pending = null;
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {

		case UISWTViewEvent.TYPE_CREATE:{

			if ( event instanceof UISWTViewEventImpl ){

				String parent = ((UISWTViewEventImpl)event).getParentID();

				enable_tabs = parent != null && parent.equals( UISWTInstance.VIEW_TORRENT_DETAILS );
			}
			break;
		}
		case UISWTViewEvent.TYPE_FOCUSGAINED:
			String id = "DMDetails_Peers";

			setFocused( true );	// do this here to pick up corrent manager before rest of code

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

			break;
		case UISWTViewEvent.TYPE_FOCUSLOST:
			setFocused( false );
			SelectedContentManager.clearCurrentlySelectedContent();
			break;
		}

		return( super.eventOccurred(event));
	}
}
