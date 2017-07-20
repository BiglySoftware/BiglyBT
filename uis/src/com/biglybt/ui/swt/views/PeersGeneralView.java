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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagListener;
import com.biglybt.core.tag.Taggable;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableLifeCycleListener;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.peers.DownloadNameItem;


public class
PeersGeneralView
	extends TableViewTab<PEPeer>
	implements TagListener, TableLifeCycleListener, TableViewSWTMenuFillListener, UISWTViewCoreEventListenerEx
{
	private TableViewSWT<PEPeer> tv;

	private Shell shell;

	private Tag	tag;

	public
	PeersGeneralView(
		Tag	_tag )
	{
		super( "AllPeersView" );

		tag = _tag;
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
		return( new PeersGeneralView( tag ));
	}

	@Override
	public String
	getFullTitle()
	{
		return( tag.getTagName( true ));
	}

	@Override
	public TableViewSWT<PEPeer>
	initYourTableView()
	{
		TableColumnCore[] items = PeersView.getBasicColumnItems(TableManager.TABLE_ALL_PEERS);
		TableColumnCore[] basicItems = new TableColumnCore[items.length + 1];
		System.arraycopy(items, 0, basicItems, 0, items.length);
		basicItems[items.length] = new DownloadNameItem(TableManager.TABLE_ALL_PEERS);

		tv = TableViewFactory.createTableViewSWT(Peer.class, TableManager.TABLE_ALL_PEERS,
				getPropertiesPrefix(), basicItems, "connected_time", SWT.MULTI
				| SWT.FULL_SELECTION | SWT.VIRTUAL);

		tv.setRowDefaultHeightEM(1);
		tv.setEnableTabViews(true,true,null);

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();

		if (uiFunctions != null){

			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			PeersSuperView.registerPluginViews(pluginUI);
		}

		tv.addLifeCycleListener(this);
		tv.addMenuFillListener(this);

		return tv;
	}

	@Override
	public void
	taggableAdded(
		Tag			tag,
		Taggable	tagged )
	{
		 tv.addDataSource((PEPeer)tagged);
	}

	@Override
	public void
	taggableSync(
		Tag 		tag )
	{
		if ( tv.getRowCount() != tag.getTaggedCount()){

			Set<PEPeer>	peers_in_table 	= new HashSet<>( tv.getDataSources());

			Set<PEPeer> peers_in_tag	= new HashSet<>((Set)tag.getTagged());

			for ( PEPeer peer: peers_in_table ){

				if ( !peers_in_tag.contains( peer )){

					tv.removeDataSource( peer );
				}
			}

			for ( PEPeer peer: peers_in_tag ){

				if ( !peers_in_table.contains( peer )){

					tv.addDataSource( peer );
				}
			}
		}
	}

	@Override
	public void
	taggableRemoved(
		Tag			tag,
		Taggable	tagged )
	{
		 tv.removeDataSource((PEPeer)tagged);
	}

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType,
			Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				shell = this.tv.getComposite().getShell();

				tag.addTagListener(this, true);
				break;

			case EVENT_TABLELIFECYCLE_DESTROYED:
				tag.removeTagListener(this);
				break;
		}
	}

	@Override
	public void
	fillMenu(
		String 		sColumnName,
		Menu 		menu )
	{
		PeersView.fillMenu( menu, tv, shell, null );
	}

	@Override
	public void
	addThisColumnSubMenu(
			String 	columnName,
			Menu 	menuThisColumn )
	{
	}
}
