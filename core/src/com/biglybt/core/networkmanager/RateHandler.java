/*
 * Created on Oct 6, 2004
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

package com.biglybt.core.networkmanager;

/**
 * Handler to allow external control of an entity's byte processing rate.
 */
public interface RateHandler {
  /**
   * Get the current number of bytes allowed to be processed by the entity and protocol_is_free if [1] > 0
   * @return number of bytes allowed
   */
  public int[] getCurrentNumBytesAllowed();

  /**
   * Notification of any bytes processed by the entity.
   * @param num_bytes_processed
   */
  public void bytesProcessed( int data_bytes, int protocol_bytes );
}
