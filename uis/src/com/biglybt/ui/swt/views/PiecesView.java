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

import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerPieceListener;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo2;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
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

	private DownloadManager 		manager;
	private TableViewSWT<PEPiece> 	tv;

	private Composite legendComposite;
	private MultipleDocumentInterfaceSWT mdi;

	/**
	 * Initialize
	 *
	 */
	public PiecesView() {
		super(MSGID_PREFIX);
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
					BlocksItem.colors, new String[] {
						"PiecesView.legend.requested",
						"PiecesView.legend.written",
						"PiecesView.legend.downloaded",
						"PiecesView.legend.incache",
						"label.end.game.mode"
					});
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
	}

	/**
	 * Add datasources already in existance before we called addListener.
	 * Faster than allowing addListener to call us one datasource at a time.
	 */
	private void addExistingDatasources() {
		if (manager == null || tv == null || tv.isDisposed()) {
			return;
		}

		PEPiece[] dataSources = manager.getCurrentPieces();
		if (dataSources.length > 0) {
  		tv.addDataSources(dataSources);
    	tv.processDataSourceQueue();
		}

		// For this view the tab datasource isn't driven by table row selection so we
		// need to update it with the primary data source

		TableViewSWT_TabsCommon tabs = tv.getTabsCommon();

		if ( tabs != null ){

			tabs.triggerTabViewsDataSourceChanged(tv);
		}

	}
	
	  @Override
	public void defaultSelected(TableRowCore[] rows, int keyMask, int origin) {

		if (rows.length != 1) {
			return;
		}

		PEPiece piece = (PEPiece) rows[0].getDataSource();

		// Show piece in PieceInfoView, which is either a subtab of this view,
		// or a sister tab
		MdiEntrySWT entry = null;
		MultipleDocumentInterfaceSWT mdiToUse = null;

		TableViewSWT_TabsCommon tabsCommon = tv.getTabsCommon();
		if (tabsCommon != null) {
			mdiToUse = tabsCommon.getMDI();
			if (mdiToUse != null) {
				entry = mdiToUse.getEntry(PieceInfoView.MSGID_PREFIX);
			}
		}
		if (entry == null && mdi != null) {
			mdiToUse = mdi;
			entry = mdiToUse.getEntry(PieceInfoView.MSGID_PREFIX);
		}

		if (entry != null) {
			UISWTViewEventListener eventListener = entry.getEventListener();
			if (eventListener instanceof PieceInfoView) {
				mdiToUse.showEntry(entry);
				((PieceInfoView) eventListener).selectPiece(piece);
			}
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
				
				sc[i] = new SelectedContent();
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
		if (event.getType() == UISWTViewEvent.TYPE_CREATE) {
			event.getView().setDestroyOnDeactivate(true);
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
}
