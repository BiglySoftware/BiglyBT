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

package com.biglybt.ui.swt.columns.peer;


import com.biglybt.pif.download.Download;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.ui.swt.columns.ColumnCheckBox;

public class
ColumnPeerBoost
	extends ColumnCheckBox
{
	public static String COLUMN_ID = "boost";

	private static volatile	BuddyPlugin buddy_plugin;

	public
	ColumnPeerBoost(
		TableColumn column )
	{
		super( column );
	}

	@Override
	protected Boolean
	getCheckBoxState(
		Object datasource )
	{
		BuddyPlugin bp = getBuddyPlugin();

		Peer peer = (Peer)datasource;

		if ( peer != null && bp != null ){
				
			if ( bp.isFullBuddy( peer )){
				
				return( true );
			}
			
			try{
				Download download = peer.getManager().getDownload();
				
				if ( download != null ){
															
					return( bp.isPartialBuddy( download, peer ));
				}
			}catch( Throwable e ){
				
			}
		}

		return( null );
	}

	@Override
	protected boolean 
	isReadOnly(Object datasource)
	{
		BuddyPlugin bp = getBuddyPlugin();

		Peer peer = (Peer)datasource;

		if ( peer != null ){
			
			if ( peer.isMyPeer()){
			
				return( true );
				
			}else if ( bp != null ){
				
				if ( peer.getState() == Peer.TRANSFERING ){
			
					return( bp.isFullBuddy( peer ));
				
				}else{
					
					return( true );
				}
			}
		}
		
		return ( false );
	}
	
	@Override
	protected void
	setCheckBoxState(
		Object 	datasource,
		boolean set )
	{
		BuddyPlugin bp = getBuddyPlugin();
		
		Peer peer = (Peer)datasource;
		
		if ( peer != null && bp != null ){
				
			try{
				Download download = peer.getManager().getDownload();
				
				if ( download != null ){
															
					bp.setPartialBuddy( download, peer, set, true );
				}
			}catch( Throwable e ){
				
			}
		}
	}
	
	private static BuddyPlugin
	getBuddyPlugin()
	{
		if ( buddy_plugin == null ){
			
			buddy_plugin = BuddyPluginUtils.getPlugin();
		}
		
		return( buddy_plugin );
	}
}