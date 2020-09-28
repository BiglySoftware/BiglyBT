/*
 * File    : IPFilter.java
 * Created : 02-Mar-2004
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

package com.biglybt.pif.ipfilter;

/**
 * @author parg
 *
 */

import java.io.File;

public interface
IPFilter
{
		/**
		 * Gets the file name used for persistent ranges
		 * @return
		 */

	public File
	getFile();

		/**
		 * creates a new range but *doesn't* add it to the list. Use the add method
		 * to add it
		 * @param address_type - 1= ipv4, 2=ipv6
		 * @param this_session_only	// not persisted if "this_session_only" is true
		 * @return
		 */

	public IPRange
	createRange(
		int		address_type,
		boolean this_session_only );

		/**
		 * Adds a range. Only ranges created with "create" above can be added
		 * @param range
		 */

	public void
	addRange(
		IPRange		range );

		/**
		 * Creates, validates and adds a range
		 * @param description
		 * @param start_ip
		 * @param end_ip
		 * @param this_session_only	not persisted if true
		 * @return new range or null if invalid
		 */

	public IPRange
	createAndAddRange(
		String		description,
		String		start_ip,
		String		end_ip,
		boolean		this_session_only );

	/**
	 * Remove a range
	 * @param range
	 */

	public void
	removeRange(
		IPRange		range );

		/**
		 * Reloads the ip filter from the config file (obtainable using "getFile")
		 * @throws IPFilterException
		 */

	public void
	reload()

		throws IPFilterException;

		/**
		 * Gets the current set of defined IP ranges
		 * @return
		 */

	public IPRange[]
	getRanges();

		/**
		 * returns length of ranges list
		 * @return
		 */

	public int
	getNumberOfRanges();

		/**
		 * Checks an address to see if its in an allowed range
		 * @param IPAddress
		 * @return
		 */

	public boolean
	isInRange(
		String IPAddress );

		/**
		 * Gets the current list of blocked addresses
		 * @return
		 */

	public IPBlocked[]
	getBlockedIPs();

		/**
		 * returns length of blocked ip list
		 * @return
		 */

	public int
	getNumberOfBlockedIPs();

		/**
		 * Explicitly blocks an address
		 * @param IPAddress
		 */

	public void
	block(
		String IPAddress);

	/**
	 * Gets the current list of b addresses
	 * @since 2.5.0.2
	 * @return
	 */

	public IPBanned[]
	getBannedIPs();

	/**
	 * returns length of banned ip list
	 * @since 2.5.0.2
	 * @return
	 */

	public int
    getNumberOfBannedIPs();

	/**
	 * Explicitly bans an address
	 * @param IPAddress
	 * @since 2.5.0.2
	 */

	public void
    ban(
	    String IPAddress,
	    String text);

	/**
	 * Removes a ban on an address
	 * @param IPAddress
	 * @since 2.5.0.2
	 */

	public void
    unban(
	    String IPAddress);

		/**
		 * The filter either allows defined ranges to be allowed or denied access
		 * @return
		 */

	public boolean
	getInRangeAddressesAreAllowed();

		/**
		 * Set the behaviour of the filter to either allow or deny access for defined ranges
		 * @param b
		 */

	public void
	setInRangeAddressesAreAllowed(
		boolean	b );


		/**
		 * Test if ipfilter is enabled or not
		 * @return
		 */

	public boolean
	isEnabled();

		/**
		 * change the enabled status
		 * @param enabled
		 */

	public void
	setEnabled(
		boolean	enabled );

		/**
		 * saves current setting to file given by getFile
		 * @throws IPFilterException
		 */

	public void
	save()

		throws IPFilterException;

		/**
		 * Gets the last time the filter set was updated or marked as up to date
		 * @return
		 */

	public long
	getLastUpdateTime();
}
