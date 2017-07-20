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
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.common.table.TableView;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.views.peer.PeerInfoView;
import com.biglybt.ui.swt.views.peer.RemotePieceDistributionView;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.peers.DownloadNameItem;

import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableLifeCycleListener;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;

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
	extends TableViewTab<PEPeer>
	implements GlobalManagerListener, DownloadManagerPeerListener,
	TableLifeCycleListener, TableViewSWTMenuFillListener, UISWTViewCoreEventListenerEx
{
	public static final String VIEW_ID = "AllPeersView";

	private TableViewSWT<PEPeer> tv;
	private Shell shell;
	private boolean active_listener = true;

	protected static boolean registeredCoreSubViews = false;


  /**
   * Initialize
   *
   */
  public PeersSuperView() {
  	super( VIEW_ID );
	}

	@Override
	public boolean
	isCloneable()
	{
		return( true );
	}

	@Override
	public UISWTViewCoreEventListener
	getClone()
	{
		return( new PeersSuperView());
	}


  // @see com.biglybt.ui.swt.views.table.impl.TableViewTab#initYourTableView()
  @Override
  public TableViewSWT<PEPeer> initYourTableView() {
  	TableColumnCore[] items = PeersView.getBasicColumnItems(TableManager.TABLE_ALL_PEERS);
  	TableColumnCore[] basicItems = new TableColumnCore[items.length + 1];
  	System.arraycopy(items, 0, basicItems, 0, items.length);
  	basicItems[items.length] = new DownloadNameItem(TableManager.TABLE_ALL_PEERS);

	TableColumnManager tcManager = TableColumnManager.getInstance();

	tcManager.setDefaultColumnNames( TableManager.TABLE_ALL_PEERS, basicItems );


  	tv = TableViewFactory.createTableViewSWT(Peer.class, TableManager.TABLE_ALL_PEERS,
				getPropertiesPrefix(), basicItems, "connected_time", SWT.MULTI
						| SWT.FULL_SELECTION | SWT.VIRTUAL);
		tv.setRowDefaultHeightEM(1);
		tv.setEnableTabViews(true,true,null);

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {
			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			registerPluginViews(pluginUI);
		}

		tv.addLifeCycleListener(this);
		tv.addMenuFillListener(this);

  	return tv;
  }

	public static void registerPluginViews(final UISWTInstance pluginUI) {
		if (pluginUI == null || PeersSuperView.registeredCoreSubViews) {
			return;
		}

		pluginUI.addView(TableManager.TABLE_ALL_PEERS, "PeerInfoView",
				PeerInfoView.class, null);
		pluginUI.addView(TableManager.TABLE_ALL_PEERS,
				"RemotePieceDistributionView", RemotePieceDistributionView.class,
				null);
		pluginUI.addView(TableManager.TABLE_ALL_PEERS, "LoggerView",
				LoggerView.class, true);

		PeersSuperView.registeredCoreSubViews = true;
		final UIManager uiManager = PluginInitializer.getDefaultInterface().getUIManager();
		uiManager.addUIListener(new UIManagerListener() {
			@Override
			public void UIAttached(UIInstance instance) {
			}

			@Override
			public void UIDetached(UIInstance instance) {
				if (!(instance instanceof UISWTInstance)) {
					return;
				}

				PeersSuperView.registeredCoreSubViews = false;
				pluginUI.removeViews(TableManager.TABLE_ALL_PEERS, "PeerInfoView");
				pluginUI.removeViews(TableManager.TABLE_ALL_PEERS, "RemotePieceDistributionView");
				pluginUI.removeViews(TableManager.TABLE_ALL_PEERS, "LoggerView");

				uiManager.removeUIListener(this);
			}
		});
	}


	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				shell = this.tv.getComposite().getShell();
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


	@Override
	public void fillMenu(String sColumnName, final Menu menu) {
		PeersView.fillMenu(menu, tv, shell, null );
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

		tv.addDataSources(sources.toArray(new PEPeer[sources.size()]));
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
	public void addThisColumnSubMenu(String columnName, Menu menuThisColumn) {}
	@Override
	public void peerManagerAdded(PEPeerManager manager){}
	@Override
	public void peerManagerRemoved(PEPeerManager manager) {}
	@Override
	public void peerManagerWillBeAdded(PEPeerManager manager) {}
}
