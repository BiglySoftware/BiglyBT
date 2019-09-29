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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.views.table.TableViewSWT;


/**
 * AllPeersView
 *
 * @author Olivier
 * @author TuxPaper
 *         2004/Apr/20: Use TableRowImpl instead of PeerRow
 *         2004/Apr/20: Remove need for tableItemToObject
 *         2004/Apr/21: extends TableView instead of IAbstractView
 * @author MjrTom
 *			2005/Oct/08: Add PieceItem
 */

public class PeersSuperView
	extends PeersViewBase
	implements GlobalManagerListener, DownloadManagerPeerListener
{
	public static final String VIEW_ID = "AllPeersView";

	private boolean active_listener = true;


	public PeersSuperView() {
		super( VIEW_ID, true );
	}

	@Override
	public TableViewSWT<PEPeer> initYourTableView()
	{
		initYourTableView( TableManager.TABLE_ALL_PEERS );

		return( tv );
	}

	@Override
	public void 
	tableLifeCycleEventOccurred(
		TableView tv, int eventType, Map<String, Object> data) 
	{	
		super.tableLifeCycleEventOccurred(tv, eventType, data);
		
		switch (eventType) {
		case EVENT_TABLELIFECYCLE_INITIALIZED:
			
			CoreFactory.addCoreRunningListener(new CoreRunningListener() {

				@Override
				public void coreRunning(Core core) {
					registerGlobalManagerListener(core);
				}
			});
			break;

		case EVENT_TABLELIFECYCLE_DESTROYED:
			unregisterListeners();
			break;
		}
	}


	/* DownloadManagerPeerListener implementation */
	
	@Override
	public void peerAdded(PEPeer created) {
		addPeer( created );
	}

	@Override
	public void peerRemoved(PEPeer removed) {
		removePeer( removed );
	}

	/**
	 * Add datasources already in existance before we called addListener.
	 * Faster than allowing addListener to call us one datasource at a time.
	 * @param core
	 */
	private void addExistingDatasources(Core core) {
		if (tv.isDisposed()) {
			return;
		}

		ArrayList<PEPeer> sources = new ArrayList<>();
		Iterator<?> itr = core.getGlobalManager().getDownloadManagers().iterator();
		while (itr.hasNext()) {
			PEPeer[] peers = ((DownloadManager)itr.next()).getCurrentPeers();
			if (peers != null) {
				sources.addAll(Arrays.asList(peers));
			}
		}
		if (sources.isEmpty()) {
			return;
		}

		addPeers(sources.toArray(new PEPeer[sources.size()]));
		
		tv.processDataSourceQueue();
	}

	private void registerGlobalManagerListener(Core core) {
		this.active_listener = false;
		try {
			core.getGlobalManager().addListener(this);
		} finally {
			this.active_listener = true;
		}
		addExistingDatasources(core);
	}

	private void unregisterListeners() {
		try {
			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
			gm.removeListener(this);
			Iterator<?> itr = gm.getDownloadManagers().iterator();
			while(itr.hasNext()) {
				DownloadManager dm = (DownloadManager)itr.next();
				downloadManagerRemoved(dm);
			}
		} catch (Exception e) {
		}
	}

	@Override
	public void	downloadManagerAdded(DownloadManager dm) {
		dm.addPeerListener(this, !this.active_listener);
	}
	@Override
	public void	downloadManagerRemoved(DownloadManager dm) {
		dm.removePeerListener(this);
	}

	// Methods I have to implement but have no need for...
	@Override
	public void	destroyInitiated() {}
	@Override
	public void destroyed() {}
	@Override
	public void seedingStatusChanged(boolean seeding_only_mode, boolean b) {}
	@Override
	public void peerManagerAdded(PEPeerManager manager){}
	@Override
	public void peerManagerRemoved(PEPeerManager manager) {}
	@Override
	public void peerManagerWillBeAdded(PEPeerManager manager) {}
	
	protected void
	updateSelectedContent(){}
}
