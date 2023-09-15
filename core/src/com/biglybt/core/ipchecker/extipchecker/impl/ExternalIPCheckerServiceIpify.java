/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.ipchecker.extipchecker.impl;

import java.net.URL;

class ExternalIPCheckerServiceIpify
	extends ExternalIPCheckerServiceSimple
{
	private static final URL CHECKER_URL = url("https://api.ipify.org/");

	private static final String SERVICE_NAME = "Ipify";

	private static final String SERVICE_URL = "https://www.ipify.org/";

	ExternalIPCheckerServiceIpify() {
		super(CHECKER_URL, SERVICE_NAME, SERVICE_URL, () -> SERVICE_NAME);
	}
}
