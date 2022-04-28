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


import java.io.IOException;
import java.nio.ByteBuffer;

import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.messaging.MessageStreamDecoder;
import com.biglybt.pifimpl.local.network.TransportImpl;


/**
 *
 */
public class MessageStreamDecoderAdapter implements com.biglybt.core.peermanager.messaging.MessageStreamDecoder {
  private final MessageStreamDecoder plug_decoder;


  public MessageStreamDecoderAdapter( MessageStreamDecoder plug_decoder ) {
    this.plug_decoder = plug_decoder;
  }


  @Override
  public int performStreamDecode(com.biglybt.core.networkmanager.Transport transport, int max_bytes ) throws IOException {
    return plug_decoder.performStreamDecode( new TransportImpl( transport ), max_bytes );
  }


  @Override
  public int[] getCurrentMessageProgress() {
    return( null );
  }


  @Override
  public com.biglybt.core.peermanager.messaging.Message[] removeDecodedMessages() {
    Message[] plug_msgs = plug_decoder.removeDecodedMessages();

    if( plug_msgs == null || plug_msgs.length < 1 ) {
      return null;
    }

    com.biglybt.core.peermanager.messaging.Message[] core_msgs = new com.biglybt.core.peermanager.messaging.Message[ plug_msgs.length ];

    for( int i=0; i < plug_msgs.length; i++ ) {
      core_msgs[i] = new MessageAdapter( plug_msgs[i] );
    }

    return core_msgs;
  }

  @Override
  public int getProtocolBytesDecoded() {  return plug_decoder.getProtocolBytesDecoded();  }

  @Override
  public int getDataBytesDecoded() {  return plug_decoder.getDataBytesDecoded();  }

  @Override
  public void pauseDecoding() {  plug_decoder.pauseDecoding();  }

  @Override
  public void resumeDecoding() {  plug_decoder.resumeDecoding();  }

  @Override
  public ByteBuffer destroy() {  return plug_decoder.destroy();  }

}
