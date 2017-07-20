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

package com.biglybt.core.peermanager.messaging.azureus;


import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;



/**
 *
 *
 */

public class
AZMessageEncoder
implements
	MessageStreamEncoder
{
	public static final int PADDING_MODE_NONE			= 0;
	public static final int PADDING_MODE_NORMAL			= 1;
	public static final int PADDING_MODE_MINIMAL		= 2;

	private final int padding_mode;

	public
	AZMessageEncoder(
		int _padding_mode )
	{
		padding_mode = _padding_mode;
	}



	@Override
	public RawMessage[] encodeMessage(Message message ) {
		return new RawMessage[]{ AZMessageFactory.createAZRawMessage( message, padding_mode  )};
	}


}
