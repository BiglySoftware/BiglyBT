/*
 * File    : PEPeerControlFactory.java
 * Created : 21-Oct-2003
 * By      : stuff
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

package com.biglybt.core.peer.impl;

/**
 * @author parg
 *
 */

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.peer.PEPeerManagerAdapter;
import com.biglybt.core.peer.impl.control.PEPeerControlImpl;

public class
PEPeerControlFactory 
{
	public static PEPeerControl
	create(
		byte[]					peer_id,
		PEPeerManagerAdapter 	adapter,
		DiskManager 			diskManager,
		int						id )
	{
		return( new PEPeerControlImpl( peer_id, adapter, diskManager, id ));
	}
}
