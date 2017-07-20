/*
 * Created on 20 Jun 2006
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

package com.biglybt.core.security;

import java.nio.ByteBuffer;

public interface
CryptoSTSEngine
{
	public void
	getKeys(
		ByteBuffer		message )

		throws CryptoManagerException;

	public void
	putKeys(
		ByteBuffer		message )

		throws CryptoManagerException;

	public void
	getAuth(
		ByteBuffer		message )

		throws CryptoManagerException;

	public void
	putAuth(
		ByteBuffer		message )

		throws CryptoManagerException;


	public byte[]
	getSharedSecret()

		throws CryptoManagerException;

	public byte[]
	getRemotePublicKey()

		throws CryptoManagerException;
}
