/**
* Created on Apr 17, 2007
* Created by Alan Snyder
* Copyright (C) Azureus Software, Inc, All Rights Reserved.
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*
*/


package com.biglybt.core.networkmanager.admin;


public interface NetworkAdminSpeedTestScheduler
{
	public static final int TEST_TYPE_BT	= 1;

    /**
     * If system crashes on start-up, then speed tests torrents need to be
     * cleaned on start-up etc - call this method on start to allow this
     */
    public void initialise();

    /**
     * returns the currently scheduled test, null if none
     * @return - NetworkAdminSpeedTestScheduledTest
     */
    public NetworkAdminSpeedTestScheduledTest getCurrentTest();

    /**
     * Request a test using the testing service.
     * @param type - ID for the type of test - use above constants
     * @return boolean - true if a success, otherwise false.
     * @throws NetworkAdminException -
     */
    public NetworkAdminSpeedTestScheduledTest scheduleTest( int type ) throws NetworkAdminException;

    /**
     * Get the most recent result for a given test type, null if none
     * @param type - ID for the type of test - use above constants
     * @return - Result
     */
    public NetworkAdminSpeedTesterResult getLastResult( int type );
}
