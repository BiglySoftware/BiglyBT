/*
 * Created on Jul 15, 2006
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



/**
 *
 */
public class UploadSlot {

	protected static final int TYPE_NORMAL		 = 0;
	protected static final int TYPE_OPTIMISTIC = 1;

	private final int slot_type;
	private long expire_round = 0;  //slot is expired by default
	private UploadSession session;



	protected UploadSlot( int _slot_type ) {
		this.slot_type = _slot_type;
	}

	protected int getSlotType() {  return slot_type;  }


	protected void setSession( UploadSession _session ) {  this.session = _session; }
	protected UploadSession getSession() {  return session;  }

	protected void setExpireRound( long round ) {  this.expire_round = round;  }
	protected long getExpireRound(){  return expire_round;  }





}
