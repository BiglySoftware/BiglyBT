/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.core.dht.router;

/**
 * Observer interface to allow monitoring of contacts in the routing table.
 *
 * @author Michael Parker
 */
public interface DHTRouterObserver {
	/**
	 * Observer method invoked when a contact is added to the routing table.
	 *
	 * @param contact
	 * the added contact
	 */
	public void added(DHTRouterContact contact);

	/**
	 * Observer method invoked when a contact is removed from the routing table.
	 *
	 * @param contact
	 * the removed contact
	 */
	public void removed(DHTRouterContact contact);

	/**
	 * Observer method invoked when a contact changes between a bucket entry and a
	 * replacement in the routing table.
	 *
	 * @param contact
	 * the contact that changed location
	 */
	public void locationChanged(DHTRouterContact contact);

	/**
	 * Observer method invoked when a contact is found to be alive.
	 *
	 * @param contact
	 * the contact now alive
	 */
	public void nowAlive(DHTRouterContact contact);

	/**
	 * Observer method invoked when a contact is found to be failing.
	 *
	 * @param contact
	 * the contact now failing
	 */
	public void nowFailing(DHTRouterContact contact);

	/**
	 * Router is not longer in use
	 * @param router
	 */

	public void
	destroyed(
		DHTRouter	router );
}
