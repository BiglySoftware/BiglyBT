/*
 * Created on 05-Jan-2006
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

package com.biglybt.net.udp.mc;

import java.net.InetSocketAddress;

public interface
MCGroup
{
	public int
	getControlPort();

	public void
	sendToGroup(
		byte[]	data );

		/**
		 * Sends to the group but will replace any occurrence of %AZINTERFACE% in the string with the
		 * interface being used for the send
		 * @param param_data
		 */

	public void
	sendToGroup(
		String	param_data );

	public void
	sendToMember(
		InetSocketAddress	address,
		byte[]				data )

		throws MCGroupException;
}
