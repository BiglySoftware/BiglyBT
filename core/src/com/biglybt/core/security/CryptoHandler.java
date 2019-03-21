/*
 * Created on 15 Jun 2006
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

import java.security.PrivateKey;
import java.security.PublicKey;

public interface
CryptoHandler
{
	public int
	getType();

	public int
	getInstance();
	
		/**
		 * Explicit unlock request
		 * @throws CryptoManagerException
		 */

	public void
	unlock()

		throws CryptoManagerException;

		/**
		 * Puts the handler back into a state where password will be required to access private stuff
		 */

	public void
	lock();

	public boolean
	isUnlocked();

	public int
	getUnlockTimeoutSeconds();

		/**
		 *
		 * @param secs		0-> infinite
		 */

	public void
	setUnlockTimeoutSeconds(
		int		secs );


	public byte[]
   	sign(
   		byte[]		data,
		String		reason )

		throws CryptoManagerException;

	public boolean
	verify(
		byte[]		public_key,
		byte[]		data,
		byte[]		signature )

		throws CryptoManagerException;

	public byte[]
	encrypt(
		byte[]		other_public_key,
		byte[]		data,
		String		reason )

		throws CryptoManagerException;

	public byte[]
	decrypt(
		byte[]		other_public_key,
		byte[]		data,
		String		reason )

		throws CryptoManagerException;

	public CryptoSTSEngine
	getSTSEngine(
		String		reason )

		throws CryptoManagerException;

	public CryptoSTSEngine
	getSTSEngine(
		PublicKey		public_key,
		PrivateKey		private_key )

		throws CryptoManagerException;

	public byte[]
	peekPublicKey();

	public byte[]
	getPublicKey(
		String		reason )

		throws CryptoManagerException;

	public byte[]
	getEncryptedPrivateKey(
		String		reason )

		throws CryptoManagerException;

	public boolean
	verifyPublicKey(
		byte[]	encoded );

	public void
	recoverKeys(
		byte[]		public_key,
		byte[]		encrypted_private_key )

		throws CryptoManagerException;

	public void
	resetKeys(
		String		reason )

		throws CryptoManagerException;

	public String
	exportKeys()

		throws CryptoManagerException;

	public int
	getDefaultPasswordHandlerType();

	public void
	setDefaultPasswordHandlerType(
		int		new_type )

		throws CryptoManagerException;

		/**
		 *
		 * @param str
		 * @return true if a client restart is required
		 * @throws CryptoManagerException
		 */

	public boolean
	importKeys(
		String	str )

		throws CryptoManagerException;
}
