/*
 * Created on Apr 19, 2005
 * Created by Alon Rohter
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

import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.NetworkManager;

/**
 *
 */
public class NetworkManagerUtilities {

  /**
   * Translate the group speed limit to a proper real rate.
   * @param group to use
   * @return rate real limit in bytes per second
   */
  public static int getGroupRateLimit( LimitedRateGroup group ) {
    int limit = group.getRateLimitBytesPerSecond();
    if( limit == 0 ) {  //unlimited
      limit = NetworkManager.UNLIMITED_RATE;
    }
    else if( limit < 0 ) {  //disabled
      limit = 0;
    }
    return limit;
  }


}
