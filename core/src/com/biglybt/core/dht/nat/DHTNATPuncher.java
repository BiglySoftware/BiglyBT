/*
 * Created on 11-Aug-2005
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

package com.biglybt.core.dht.nat;

import java.net.InetSocketAddress;
import java.util.Map;

import com.biglybt.core.dht.transport.DHTTransportContact;

public interface
DHTNATPuncher
{
	public void
	start();

	public void
	setSuspended(
		boolean	susp );

	public void
	destroy();

		/**
		 * We're trying to run a rendezvous
		 * @return
		 */

	public boolean
	active();

	public void
	forceActive(
		boolean		force );

		/**
		 * Got a good running rendezvous
		 * @return
		 */

	public boolean
	operational();

	public DHTTransportContact
	getLocalContact();

	public DHTTransportContact
	getRendezvous();

	public DHTNATPuncher
	getSecondaryPuncher();

	public Map
	punch(
		String					reason,
		DHTTransportContact		target,
		DHTTransportContact[]	rendezvous_used,
		Map						client_data );

		/**
		 *
		 * @param target		input/output parameter for target of traversal
		 * @param client_data
		 * @return
		 */

	public Map
	punch(
		String					reason,
		InetSocketAddress[]		target,
		DHTTransportContact[]	rendezvous_used,
		Map						client_data );

		/**
		 * @param target
		 * @param rendezvous
		 */

	public void
	setRendezvous(
		DHTTransportContact		target,
		DHTTransportContact		rendezvous );

	public Map
	sendMessage(
		InetSocketAddress	rendezvous,
		InetSocketAddress	target,
		Map					message );

	public String
	getStats();

	public void
	addListener(
		DHTNATPuncherListener	listener );

	public void
	removeListener(
		DHTNATPuncherListener	listener );
}
