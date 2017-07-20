/*
 * Created on Feb 21, 2005
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

package com.biglybt.pifimpl.local.messaging;

import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.messaging.MessageStreamEncoder;
import com.biglybt.pif.network.RawMessage;
import com.biglybt.pifimpl.local.network.RawMessageAdapter;


/**
 *
 */
public class MessageStreamEncoderAdapter implements com.biglybt.core.peermanager.messaging.MessageStreamEncoder {

  private final MessageStreamEncoder plug_encoder;


  public MessageStreamEncoderAdapter( MessageStreamEncoder plug_encoder ) {
    this.plug_encoder = plug_encoder;
  }

  @Override
  public com.biglybt.core.networkmanager.RawMessage[] encodeMessage(com.biglybt.core.peermanager.messaging.Message message ) {
    Message plug_msg;

    if( message instanceof MessageAdapter ) {  //original message created by plugin, unwrap
      plug_msg = ((MessageAdapter)message).getPluginMessage();
    }
    else {
      plug_msg = new MessageAdapter( message );  //core created
    }

    RawMessage raw_plug = plug_encoder.encodeMessage( plug_msg );
    return new com.biglybt.core.networkmanager.RawMessage[]{ new RawMessageAdapter( raw_plug )};
  }


}
