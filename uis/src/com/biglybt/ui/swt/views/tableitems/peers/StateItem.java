/*
 * File    : ClientItem.java
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

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class StateItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public StateItem(String table_id) {
    super("state", POSITION_LAST, 65, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROTOCOL,
			CAT_CONNECTION,
		});
	}

  @Override
  public void refresh(TableCell cell) {
	Object ds = cell.getDataSource();
	
	int state = -1;
	
	boolean	reconnect;
	
	if ( ds instanceof PEPeerTransport ){
    
		PEPeerTransport peer = (PEPeerTransport)ds;
		
		state = peer.getConnectionState();
	
		reconnect = peer.isReconnect();
		
	}else{
		
		if ( ds != null ){
			
			state = PEPeerTransport.CONNECTION_FULLY_ESTABLISHED;	// assume other peer types (e.g. MyPeer) are connected
		}
		
		reconnect = false;
	}
	    
    if( !cell.setSortValue( state ) && cell.isValid() ) {
       return;
    }

    String state_text = "";

    switch( state ) {
	    case PEPeerTransport.CONNECTION_PENDING :
	    	state_text = MessageText.getString( "PeersView.state.pending" );
	    	break;
	    case PEPeerTransport.CONNECTION_CONNECTING :
	    	state_text = MessageText.getString( "PeersView.state.connecting" );
	    	break;
	    case PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE :
	    	state_text = MessageText.getString( "PeersView.state.handshake" );
	    	break;
	    case PEPeerTransport.CONNECTION_FULLY_ESTABLISHED :
	    	state_text = MessageText.getString( "PeersView.state.established" );
	    	break;
    }

    if ( state != PEPeerTransport.CONNECTION_FULLY_ESTABLISHED ){
    
    	if ( reconnect ){
    	
    		state_text += " *";
    	}
    }
    
    cell.setText( state_text );
  }
}
