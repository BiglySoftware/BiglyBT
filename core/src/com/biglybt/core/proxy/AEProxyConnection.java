/*
 * Created on 08-Dec-2004
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

package com.biglybt.core.proxy;

import java.nio.channels.SocketChannel;

/**
 * @author parg
 *
 */

public interface
AEProxyConnection
{
	public String
	getName();

		/**
		 * returns the non-blocking channel associated with the initiator of this proxy
		 * connection
		 * @return
		 */

	public SocketChannel
	getSourceChannel();

		// state manipulation methods

	public void
	setReadState(
		AEProxyState	state );

	public void
	setWriteState(
		AEProxyState	state );

	public void
	setConnectState(
		AEProxyState	state );

		// selector manipulation

	public void
	requestReadSelect(
		SocketChannel	channel );

	public void
	cancelReadSelect(
		SocketChannel	channel );

	public void
	requestWriteSelect(
		SocketChannel	channel );

	public void
	cancelWriteSelect(
		SocketChannel	channel );

	public void
	requestConnectSelect(
		SocketChannel	channel );

	public void
	cancelConnectSelect(
		SocketChannel	channel );

		/**
		 * marks the transition between connecting and connected
		 *
		 */

	public void
	setConnected();

		/**
		 * marks the last time that something happened on the connection for read timeout
		 * purposes
		 */

	public void
	setTimeStamp();

		/**
		 * indicate that the connection has failed
		 * @param cause
		 */

	public void
	failed(
		Throwable	cause );

		/**
		 * close the connection
		 *
		 */

	public void
	close();

	public boolean
	isClosed();

	public void
	addListener(
		AEProxyConnectionListener	l );

	public void
	removeListener(
		AEProxyConnectionListener	l );
}
