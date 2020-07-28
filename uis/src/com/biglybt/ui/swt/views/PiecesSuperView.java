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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerPieceListener;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.views.table.TableViewSWT;




public class PiecesSuperView
	extends PiecesViewBase
	implements GlobalManagerListener, DownloadManagerPieceListener
{
	public static final String VIEW_ID = MultipleDocumentInterface.SIDEBAR_SECTION_ALLPIECES;

	private boolean active_listener = true;


	public PiecesSuperView() {
		super( VIEW_ID );
	}

	@Override
	public TableViewSWT<PEPiece> initYourTableView()
	{
		return( initYourTableView( TableManager.TABLE_ALL_PIECES ));
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

	private void 
	registerGlobalManagerListener(
		Core core ) 
	{
		this.active_listener = false;
		
		try{
			
			core.getGlobalManager().addListener(this);
			
		}finally{
			
			this.active_listener = true;
		}
		
		tv.processDataSourceQueue();
	}

	private void 
	unregisterListeners() 
	{
		try {
			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
			
			gm.removeListener(this);
			
			Iterator<?> itr = gm.getDownloadManagers().iterator();
			
			while(itr.hasNext()){
				
				DownloadManager dm = (DownloadManager)itr.next();
				
				downloadManagerRemoved(dm);
			}
		} catch (Exception e) {
		}
	}

	@Override
	public void	
	downloadManagerAdded(
		DownloadManager dm )
	{
		dm.addPieceListener(this, !active_listener );
	}
	
	@Override
	public void	
	downloadManagerRemoved(
		DownloadManager dm ) 
	{
		dm.removePieceListener(this);
	}

	@Override
	protected DownloadManager 
	getDownloadManager()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void	
	destroyInitiated() 
	{
	}
	
	@Override
	public void 
	destroyed()
	{
	}
	
	@Override
	public void 
	seedingStatusChanged(
		boolean seeding_only_mode, boolean b) 
	{
	}
	
	
	@Override
	public void 
	pieceAdded(
		PEPiece created )
	{
		tv.addDataSource( created );
	}

	@Override
	public void 
	pieceRemoved(
		PEPiece removed )
	{
		tv.removeDataSource(removed);
	}
}
