/*
 * Created on 22-Apr-2004
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

/**
 * @author parg
 *
 */

import java.net.PasswordAuthentication;
import java.net.URL;

public interface
SEPasswordListener
{
	public PasswordAuthentication
	getAuthentication(
		String		realm,
		URL			tracker );

	public void
	setAuthenticationOutcome(
		String		realm,
		URL			tracker,
		boolean		success );

	public void
	clearPasswords();
}
