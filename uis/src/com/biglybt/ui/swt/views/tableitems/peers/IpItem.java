/*
 * File    : IpItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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

package com.biglybt.ui.swt.views.tableitems.peers;

import java.net.InetAddress;


import com.biglybt.core.peer.PEPeer;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableCellToolTipListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/19: modified to TableCellAdapter)
 */
public class IpItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, ObfuscateCellText, TableCellToolTipListener
{
	public static final String COLUMN_ID = "ip";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PEER_IDENTIFICATION,
			CAT_CONNECTION
		});
	}

  /** Default Constructor */
  public IpItem(String table_id) {
    super(COLUMN_ID, POSITION_LAST, 100, table_id);
    setRefreshInterval(INTERVAL_LIVE);	// can change for peers as can be updated due to AZ handshake
    setObfuscation(true);
   }

  @Override
  public void refresh(TableCell cell) {
    PEPeer peer = (PEPeer)cell.getDataSource();
    String sText = (peer == null) ? "" : peer.getIp();

    if (cell.setText(sText) || !cell.isValid()) {
    	// vague attempt at sorting, only supports ipv4 :(
    	
    	if ( !sText.contains( ";" )){	// handle 'my-peer' which can have multiple entries sep by ;
    		String[] sBlocks = sText.split("\\.");
    		if (sBlocks.length == 4) {
    			try {
    				long l = (Long.parseLong(sBlocks[0]) << 24) +
    						(Long.parseLong(sBlocks[1]) << 16) +
    						(Long.parseLong(sBlocks[2]) << 8) +
    						Long.parseLong(sBlocks[3]);
    				cell.setSortValue(l);
    			} catch (Exception e) { e.printStackTrace(); /* ignore */ }
    		}
      }
    }
  }

	@Override
	public void 
	cellHover(
		TableCell cell) 
	{
		PEPeer peer = (PEPeer)cell.getDataSource();
		
		String str;
		
		if ( peer == null ){
			str = "";
		}else{
			String ip = peer.getIp();
			
			InetAddress ia = peer.getAlternativeIPv6();
			
			if ( ia != null ){
				String alt = ia.getHostAddress();
				
				if (ip.equals( alt )){
					str = ip;
				}else{
					str = ip + " (" + alt + ")";
				}
			}else{
				str = ip;
			}
		}
		cell.setToolTip( str );
	}

	@Override
	public void 
	cellHoverComplete(
		TableCell cell) 
	{	
	}
	
	@Override
	public String getObfuscatedText(TableCell cell) {
		String text = cell.getText();
		return text.length() > 3?text.substring(0, 3):text;
	}
}
