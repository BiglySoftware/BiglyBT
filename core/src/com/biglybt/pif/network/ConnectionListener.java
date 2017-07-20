/*
 * Created on Feb 9, 2005
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

/**
 * Listener for notification of connection events.
 */
public interface ConnectionListener {
  /**
   * The connection establishment process has started,
   * i.e. the connection is actively being attempted.
   */
  public void connectStarted();

  /**
   * The connection attempt succeeded.
   * The connection is now established.
   * NOTE: Called only during initial connect attempt.
   */
  public void connectSuccess();

  /**
   * The connection attempt failed.
   * NOTE: Called only during initial connect attempt.
   * @param failure_msg failure reason
   */
  public void connectFailure( Throwable failure_msg );

  /**
   * Handle exception thrown by this connection.
   * NOTE: Can be called at any time during connection lifetime.
   * @param error exception
   */
  public void exceptionThrown( Throwable error );
}
