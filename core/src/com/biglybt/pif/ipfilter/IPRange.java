/*
 * File    : IPRange.java
 * Created : 05-Mar-2004
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
 * IPRange instances implement Comparable. Rules are identical start+end IPs
 */

public interface
IPRange
	extends Comparable
{
	public String
	getDescription();

	public void
	setDescription(
		String	str );

		/**
		 * For a range to be usable it has to be valid. To make it valid you have
		 * to call checkValid. Failure to do so will leave newly created ranges
		 * as invalid and therefore checks won't be made against it!!!!
		 */

	public void
	checkValid();

	public boolean
	isValid();

	public boolean
	isSessionOnly();

	public String
	getStartIP();

	public void
	setStartIP(
		String	str );

	public String
	getEndIP();

	public void
	setEndIP(
		String	str );

	public void
	setSessionOnly(
		boolean sessionOnly );

	public boolean
	isInRange(
		String ipAddress );

	public void
	delete();
}
