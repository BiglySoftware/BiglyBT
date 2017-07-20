/*
 * Created on Mar 20, 2006
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

import java.util.ArrayList;

import com.biglybt.core.peer.PEPeer;

/**
 *
 */
public interface UploadHelper {

	public static final int PRIORITY_DISABLED = 0;
	public static final int PRIORITY_LOWEST   = 1;
	public static final int PRIORITY_LOW      = 2;
	public static final int PRIORITY_NORMAL   = 4;
	public static final int PRIORITY_HIGH     = 8;
	public static final int PRIORITY_HIGHEST  = 16;


	/**
	 * Get download opt unchoke priority.
	 * @return
	 */
	public int getPriority();


	/**
	 * Get all (PEPeerTransport) peers for this download.
	 * @return non-mutable list of peers
	 */
	public ArrayList<PEPeer> getAllPeers();


	/**
	 * Is this download in seeding mode.
	 * @return true if seeding, false if downloading
	 */
	public boolean isSeeding();

}
