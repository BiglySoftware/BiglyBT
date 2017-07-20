/*
 * Created on 16 Jan 2008
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
package com.biglybt.pifimpl.local.dht.mainline;

import com.biglybt.core.Core;
import com.biglybt.core.peermanager.messaging.bittorrent.BTHandshake;
import com.biglybt.pif.dht.mainline.MainlineDHTManager;
import com.biglybt.pif.dht.mainline.MainlineDHTProvider;


/**
 * @author Allan Crooks
 *
 */
public class MainlineDHTManagerImpl implements MainlineDHTManager {

	private Core core;

	public MainlineDHTManagerImpl(Core core) {
		this.core = core;
	}

	@Override
	public void setProvider(MainlineDHTProvider provider) {
		MainlineDHTProvider old_provider = core.getGlobalManager().getMainlineDHTProvider();
		core.getGlobalManager().setMainlineDHTProvider(provider);

		// Registering new provider, so enable global DHT support.
		if (old_provider == null && provider != null) {
			BTHandshake.setMainlineDHTEnabled(true);

			// We no longer dynamically register and unregister the message type.
			//
			// This is because if the message type is tied to the BT protocol itself,
			// which we don't support dynamic registering / unregistering of.
			//try {MessageManager.getSingleton().registerMessageType(new BTDHTPort(-1));}
			//catch (MessageException me) {Debug.printStackTrace(me);}
		}

		// Deregistering existing provider, so disable global DHT support.
		else if (old_provider != null && provider == null) {
			BTHandshake.setMainlineDHTEnabled(false);
			//MessageManager.getSingleton().deregisterMessageType(new BTDHTPort(-1));
		}

	}

	@Override
	public MainlineDHTProvider getProvider() {
		return core.getGlobalManager().getMainlineDHTProvider();
	}

}
