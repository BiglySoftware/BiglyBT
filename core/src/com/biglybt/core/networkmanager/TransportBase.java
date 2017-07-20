/*
 * Created on 1 Nov 2006
 * Created by Paul Gardner
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
 */

package com.biglybt.core.networkmanager;

public interface
TransportBase
{
	  /**
	   * Is the transport ready to write,
	   * i.e. will a write request result in >0 bytes written.
	   * @return true if the transport is write ready, false if not yet ready
	   */

	public boolean isReadyForWrite( EventWaiter waiter );


	  /**
	   * Is the transport ready to read,
	   * i.e. will a read request result in >0 bytes read.
	   * @return 0 if the transport is read ready, ms since last ready or created if never ready
	   */

	public long isReadyForRead( EventWaiter waiter );

	public boolean isTCP();

	  /**
	   * Get a textual description for this transport.
	   * @return description
	   */

	public String getDescription();
}
