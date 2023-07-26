/*
 * Created on Feb 11, 2005
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

package com.biglybt.pif.network;

import com.biglybt.pif.messaging.Message;



/**
 * Listener for incoming message queue.
 */
public interface IncomingMessageQueueListener {

  /**
   * A message has been read from the connection.
   * @param message received
   * @return true if this message was accepted, false if not handled
   */
  public boolean messageReceived( Message message );

  /**
   * The given number of bytes read from the connection.
   * @param byte_count number of protocol bytes
   */
  public void bytesReceived( int byte_count );
}
