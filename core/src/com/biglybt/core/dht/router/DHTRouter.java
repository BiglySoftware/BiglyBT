/*
 * Created on 11-Jan-2005
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

package com.biglybt.core.dht.router;

/**
 * @author parg
 *
 */

import java.util.List;

public interface
DHTRouter
{
	public int
	getK();

	public byte[]
	getID();

	public boolean
	isID(
		byte[]	node_id );

	public DHTRouterContact
	getLocalContact();

	public void
	setAdapter(
		DHTRouterAdapter	_adapter );

		/**
		 * Tells the router to perform its "start of day" functions required to integrate
		 * it into the DHT (search for itself, refresh buckets)
		 */

	public void
	seed();

		/**
		 * Adds a contact to the router. The contact is not known to be alive (e.g.
		 * we've been returned the contact by someone but we've not either got a reply
		 * from it, nor has it invoked us.
		 * @param node_id
		 * @param attachment
		 * @return
		 */

	public void
	contactKnown(
		byte[]						node_id,
		DHTRouterContactAttachment	attachment,
		boolean						force );

		/**
		 * Adds a contact to the router and marks it as "known to be alive"
		 * @param node_id
		 * @param attachment
		 * @return
		 */

	public void
	contactAlive(
		byte[]						node_id,
		DHTRouterContactAttachment	attachment );

		/**
		 * Informs the router that an attempt to interact with the contact failed
		 * @param node_id
		 * @param attachment
		 * @return
		 */

	public DHTRouterContact
	contactDead(
		byte[]						node_id,
		boolean						force );

	public DHTRouterContact
	findContact(
		byte[]	node_id );

		/**
		 * Returns num_to_return or a few more closest contacts, unordered
		 */

	public List<DHTRouterContact>
	findClosestContacts(
		byte[]		node_id,
		int			num_to_return,
		boolean		live_only );

	public void
	recordLookup(
		byte[]	node_id );

	public boolean
	requestPing(
		byte[]	node_id );

	public void
	refreshIdleLeaves(
		long	idle_max );

	public byte[]
	refreshRandom();

		/**
		 * returns a list of best contacts in terms of uptime, best first
		 * @param max
		 * @return
		 */

	public List<DHTRouterContact>
	findBestContacts(
		int		max );

		/**
		 * Returns a list of DHTRouterContact objects
		 * @return
		 */

	public List<DHTRouterContact>
	getAllContacts();

	public DHTRouterStats
	getStats();

	public void
	setSleeping(
		boolean	sleeping );

	public void
	setSuspended(
		boolean			susp );

	public void
	destroy();

	public void
	print();

	/**
	 * Adds a routing table observer if it is not already observing.
	 *
	 * @param rto
	 * the observer to add
	 * @return <code>true</code> if now observing, <code>false</code> otherwise
	 */
	public boolean addObserver(DHTRouterObserver rto);

	/**
	 * Returns whether the given observer is already observing.
	 *
	 * @param rto
	 * the observer to query as observing
	 * @return <code>true</code> if observing, <code>false</code> otherwise
	 */
	public boolean containsObserver(DHTRouterObserver rto);

	/**
	 * Removes the observer if it is already observing.
	 *
	 * @param rto
	 * the observer to remove
	 * @return <code>true</code> if no longer observing, <code>false</code> otherwise
	 */
	public boolean removeObserver(DHTRouterObserver rto);
}
