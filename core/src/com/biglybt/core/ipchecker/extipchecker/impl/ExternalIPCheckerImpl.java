/*
 * File    : ExternalIPCheckerImpl.java
 * Created : 09-Nov-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.ipchecker.extipchecker.impl;

import com.biglybt.core.ipchecker.extipchecker.ExternalIPChecker;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPCheckerService;

public class
ExternalIPCheckerImpl
	implements ExternalIPChecker
{
	private static final ExternalIPCheckerService[] services = {
		new ExternalIPCheckerServiceIpify(),
		new ExternalIPCheckerServiceDynDNS(),
		// new ExternalIPCheckerServiceDiscoveryVIP(), dead? noticed 2019/06/02
		new ExternalIPCheckerServiceNoLookup(),
	};

	@Override
	public ExternalIPCheckerService[]
	getServices()
	{
		return( services );
	}
}
