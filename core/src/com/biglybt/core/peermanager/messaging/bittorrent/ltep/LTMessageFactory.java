/*
 * Created on 17 Sep 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.core.peermanager.messaging.bittorrent.ltep;

import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessageManager;

/**
 * @author Allan Crooks
 *
 */
public class LTMessageFactory {

  public static final byte MESSAGE_VERSION_INITIAL				= 1;
  public static final byte MESSAGE_VERSION_SUPPORTS_PADDING		= 2;	// most of these messages are also used by AZ code

	public static void init() {
		try {
			MessageManager.getSingleton().registerMessageType(new LTHandshake(null, MESSAGE_VERSION_SUPPORTS_PADDING));
			MessageManager.getSingleton().registerMessageType(new UTPeerExchange(null, null, null, MESSAGE_VERSION_SUPPORTS_PADDING));
			MessageManager.getSingleton().registerMessageType(new UTMetaData(null, null, MESSAGE_VERSION_SUPPORTS_PADDING));
			MessageManager.getSingleton().registerMessageType(new UTUploadOnly(false, MESSAGE_VERSION_SUPPORTS_PADDING));
			MessageManager.getSingleton().registerMessageType(new UTHolePunch((byte)0,null,0,0, MESSAGE_VERSION_SUPPORTS_PADDING));
		}
	    catch( MessageException me ) {  me.printStackTrace();  }
	}
}
