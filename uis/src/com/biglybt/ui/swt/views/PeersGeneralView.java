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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagListener;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.swt.Messages;

import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.table.TableViewSWT;



public class
PeersGeneralView
	extends PeersViewBase
	implements TagListener
{
	private Tag	tag;

	
	public
	PeersGeneralView(
		Tag	_tag )
	{
		super( "AllPeersView", true );

		tag = _tag;
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
	public TableViewSWT<PEPeer> initYourTableView()
	{
		initYourTableView( TableManager.TABLE_ALL_PEERS, true );

		return( tv );
	}

	
	@Override
	public void
	taggableAdded(
		Tag			tag,
		Taggable	tagged )
	{
		addPeer((PEPeer)tagged);
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

					removePeer( peer );
				}
			}

			for ( PEPeer peer: peers_in_tag ){

				if ( !peers_in_table.contains( peer )){

					addPeer( peer );
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
		removePeer((PEPeer)tagged);
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

				tag.addTagListener(this, true);
				break;

			case EVENT_TABLELIFECYCLE_DESTROYED:
				tag.removeTagListener(this);
				break;
		}
	}

	@Override
	public void
	addThisColumnSubMenu(
			String 	columnName,
			Menu 	menuThisColumn )
	{
	}
}
