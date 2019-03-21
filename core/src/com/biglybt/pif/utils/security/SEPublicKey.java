/*
 * Created on 19 Jun 2006
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

package com.biglybt.pif.utils.security;

public interface
SEPublicKey
{
	public static final int KEY_TYPE_ECC_192		= 1;

	public int
	getType();

	public int
	getInstance();
	
		/**
		 * Gets a generic encoded form that includes type identification information. So
		 * don't treat this as a raw encoding as it can only sensibly be used to later
		 * import via SESecurityManager.decodePublicKey
		 *
		 * @return
		 */

	public byte[]
	encodePublicKey();

		/**
		 * Raw encoding of the specific key type
		 * @return
		 */

	public byte[]
	encodeRawPublicKey();

		/**
		 * Overridden to perform equality based on public key
		 * @param other
		 * @return
		 */

	public boolean
	equals(
		Object	other );

	public int
	hashCode();
}
