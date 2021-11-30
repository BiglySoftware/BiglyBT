/*
 * File    : IpFilter.java
 * Created : 1 oct. 2003 12:27:26
 * By      : Olivier
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

package com.biglybt.core.ipfilter;



/**
 * @author Olivier
 *
 */

import java.io.File;
import java.net.InetAddress;


public interface
IpFilter
{
	public File getFile();

	public void save() throws Exception;

	public void
	reload()

		throws Exception;

	public IpRange[]
	getRanges();

	public boolean
	isInRange(
		String ipAddress);

	public boolean
	isInRange(
		String 	ipAddress,
		String 	torrent_name,
		byte[]	torrent_hash );

	public boolean
	isInRange(
		String 	ipAddress,
		String 	torrent_name,
		byte[]	torrent_hash,
		boolean	loggable );

	public boolean
	isInRange(
		InetAddress 	ipAddress,
		String 			torrent_name,
		byte[]			torrent_hash,
		boolean			loggable );

	public IpRange
	createRange(
		int		addressType,
		boolean sessionOnly);

	public void
	addRange(
		IpRange	range );

	public void
	removeRange(
		IpRange	range );

	public int
	getNbRanges();

	public int
	getNbIpsBlocked();

	public int
	getNbIpsBlockedAndLoggable();

	public BlockedIp[]
	getBlockedIps();

	public void
	clearBlockedIPs();

	public boolean
	ban(
		String 	ipAddress,
		String	torrent_name,
		boolean	manual );

	public boolean
	ban(
		String 	ipAddress,
		String	torrent_name,
		boolean	manual,
		int		ban_for_mins );

	public boolean
	unban(String ipAddress);

	public boolean
	unban(String ipAddress, boolean block);

	public int
	getNbBannedIps();

	public BannedIp[]
	getBannedIps();

	public void
	clearBannedIps();

	public void
	addExcludedHash(
		byte[]		hash );

	public void
	removeExcludedHash(
		byte[]		hash );

	public boolean
	isEnabled();

	public void
	setEnabled(
		boolean	enabled );

	public boolean
	getInRangeAddressesAreAllowed();

	public void
	setInRangeAddressesAreAllowed(
		boolean	b );

	public long
	getLastUpdateTime();

	public void
	addListener(
		IPFilterListener	l );

	public void
	removeListener(
		IPFilterListener	l );

	public void
	addExternalHandler(
		IpFilterExternalHandler	handler );

	public void
	removeExternalHandler(
		IpFilterExternalHandler	handler );

	void reloadSync()
			throws Exception;
}
