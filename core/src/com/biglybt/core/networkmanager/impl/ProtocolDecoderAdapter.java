/*
 * Created on 18-Jan-2006
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

package com.biglybt.core.networkmanager.impl;

import java.nio.ByteBuffer;

public interface
ProtocolDecoderAdapter
{
	public static final int MATCH_NONE							= 1;
	public static final int MATCH_CRYPTO_NO_AUTO_FALLBACK		= 2;
	public static final int MATCH_CRYPTO_AUTO_FALLBACK			= 3;

	public int
	getMaximumPlainHeaderLength();

	public int
	matchPlainHeader(
		ByteBuffer			buffer );

	public void
	gotSecret(
		byte[]				session_secret );

	public void
	decodeComplete(
		ProtocolDecoder		decoder,
		ByteBuffer			remaining_initial_data );

	public void
	decodeFailed(
		ProtocolDecoder		decoder,
		Throwable			cause );
}
