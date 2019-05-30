/*
 * File    : CategoryItem.java
 * Created : 01 feb. 2004
 * By      : TuxPaper
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
		Peer peer = (Peer)datasource;

		if ( peer != null ){
			
			BuddyPlugin bp = BuddyPluginUtils.getPlugin();

			if ( bp != null ){
				
				try{
					Download download = peer.getManager().getDownload();
					
					if ( download != null ){
																
						return( bp.isPartialBuddy( download, peer ));
					}
				}catch( Throwable e ){
					
				}
			}
			
		}

		return( null );
	}

	@Override
	protected void
	setCheckBoxState(
		Object 	datasource,
		boolean set )
	{
		Peer peer = (Peer)datasource;
		
		if ( peer != null ){

			BuddyPlugin bp = BuddyPluginUtils.getPlugin();

			if ( bp != null ){
				
				try{
					Download download = peer.getManager().getDownload();
					
					if ( download != null ){
																
						bp.setPartialBuddy( download, peer, set );
					}
				}catch( Throwable e ){
					
				}
			}
		}
	}
}