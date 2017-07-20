/*
 * Created on Jul 17, 2006
 * Created by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */
package com.biglybt.core.peermanager.uploadslots;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peermanager.unchoker.UnchokerUtil;
import com.biglybt.core.util.DisplayFormatters;

/**
 *
 */
public class UploadSession {

	protected static final int TYPE_DOWNLOAD = 0;
	protected static final int TYPE_SEED		 = 1;


	private final PEPeer peer;
	private final int session_type;


	protected UploadSession( PEPeer _peer, int _session_type ) {
		this.peer = _peer;
		this.session_type = _session_type;
	}


	protected int getSessionType() {  return session_type;  }


	protected void start() {
		UnchokerUtil.performChokeUnchoke( null, peer );
	}


	protected void stop() {
		UnchokerUtil.performChokeUnchoke( peer, null );
	}


	protected boolean isSameSession( UploadSession session ) {
		if( session == null )  return false;
		return this.peer == session.peer;
	}


	protected String getStatsTrace() {
		String n = peer.getManager().getDisplayName();
		String t = session_type == TYPE_DOWNLOAD ? "DOWNLOADING" : "SEEDING";
		String p = " : [" +peer.getClient()+ "] " +peer.getIp()+ " :" +peer.getPort();
		String s = " || (D: " +DisplayFormatters.formatByteCountToKiBEtcPerSec( peer.getStats().getDataReceiveRate() )+
							  ") (U: " +DisplayFormatters.formatByteCountToKiBEtcPerSec( peer.getStats().getDataSendRate() )+ ")";
		return "[" +n+ "] " + t + p + s;
	}


}
