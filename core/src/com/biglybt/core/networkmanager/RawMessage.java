/*
 * Created on Apr 30, 2004
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

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.util.DirectByteBuffer;



/**
 * A raw data message designed for advanced queueing.
 */
public interface RawMessage extends Message {

  public static final int PRIORITY_LOW    = 0;
  public static final int PRIORITY_NORMAL = 1;
  public static final int PRIORITY_HIGH   = 2;


  /**
   * Get the message's raw data payload.
   * @return data payload buffers
   */
  public DirectByteBuffer[] getRawData();

  /**
   * Get the message's queue priority.
   * @return priority
   */
  public int getPriority();

  /**
   * Is this a no-delay message.
   * No-delay messages are transmitted immediately,
   * i.e. force-flushed out the transport.
   * @return true if a no-delay message
   */
  public boolean isNoDelay();

  /**
   * Set no-delay for this message
   * @param no_delay
   */

  public void
  setNoDelay();

  /**
   * Get the yet-unsent message types that should be removed
   * before queueing this message for sending.
   * @return message types; null if no types
   */
  public Message[] messagesToRemove();


  /**
   * Get the message this raw message is based upon.
   * @return original message
   */
  public Message getBaseMessage();
}
