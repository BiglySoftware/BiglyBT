/*
 * Created on 18 Sep 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.core.peermanager.messaging.azureus;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.peerdb.PeerItem;

/**
 * Represents a class which supports AZ style peer exchange (with a list of
 * added peers and a list of dropped peers).
 *
 * @author Allan Crooks
 */
public interface AZStylePeerExchange extends Message {
	  public PeerItem[] getAddedPeers();
	  public PeerItem[] getDroppedPeers();
	  public int getMaxAllowedPeersPerVolley(boolean initial, boolean added);
}
