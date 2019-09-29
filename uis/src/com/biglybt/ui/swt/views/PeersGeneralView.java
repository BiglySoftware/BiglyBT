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

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagListener;
import com.biglybt.core.tag.Taggable;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.util.DataSourceUtils;


/**
 * Peers based on Tags (Peer Set)
 */
public class
PeersGeneralView
	extends PeersViewBase
	implements TagListener
{
	private Tag	tag;

	
	public
	PeersGeneralView()
	{
		super( "AllPeersView", true );
	}
	
	@Override
	public String
	getFullTitle()
	{
		return(  tag == null ? "" :tag.getTagName( true ));
	}
	
	@Override
	public TableViewSWT<PEPeer> initYourTableView()
	{
		initYourTableView( TableManager.TABLE_ALL_PEERS );

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
	public boolean eventOccurred(UISWTViewEvent event) {
		if (event.getType() == UISWTViewEvent.TYPE_CREATE) {
			Tag[] tags = DataSourceUtils.getTags(event.getView().getInitialDataSource());
			if (tags.length > 0) {
				tag = tags[0];
			}
		}
		return super.eventOccurred(event); 
	}

	protected void
	updateSelectedContent(){}
}
