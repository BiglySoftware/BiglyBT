/*
 * Created on Feb 8, 2005
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

package com.biglybt.core.peermanager.messaging.bittorrent;


import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;


/**
 * Creates legacy (i.e. traditional BitTorrent wire protocol) raw messages.
 * NOTE: wire format: [total message length] + [message id byte] + [payload bytes]
 */
public class BTMessageEncoder implements MessageStreamEncoder {


  public BTMessageEncoder() {
    /*nothing*/
  }



  @Override
  public RawMessage[] encodeMessage(Message message ) {
    return new RawMessage[]{ BTMessageFactory.createBTRawMessage( message )};
  }







}
