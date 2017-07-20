/*
 * Created on May 1, 2007
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


package com.biglybt.core.networkmanager.admin;

public interface
NetworkAdminSpeedTestScheduledTest
{
	public NetworkAdminSpeedTester
	getTester();

		/**
		 * These are the limits up to which the test can run, not the result of the test
		 * @return
		 */

	public long
	getMaxUpBytePerSec();

	public long
	getMaxDownBytePerSec();

	public boolean
	start();

	public void
	abort();

	public void
	addListener(
		NetworkAdminSpeedTestScheduledTestListener	listener );

	public void
	removeListener(
		NetworkAdminSpeedTestScheduledTestListener	listener );
}
