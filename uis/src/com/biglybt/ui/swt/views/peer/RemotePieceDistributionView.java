/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.ui.swt.views.peer;

import java.util.Arrays;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.impl.transport.PEPeerTransportProtocol;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.PieceDistributionView;

import com.biglybt.core.peermanager.piecepicker.util.BitFlags;

/**
 * @author Aaron Grunthal
 * @create 02.10.2007
 */
public class RemotePieceDistributionView extends PieceDistributionView {
	private PEPeer	peer	= null;

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.PieceDistributionView#dataSourceChanged(java.lang.Object)
	 */
	@Override
	public void dataSourceChanged(Object newDataSource) {
		if (newDataSource instanceof Object[]
				&& ((Object[]) newDataSource).length > 0) {
			newDataSource = ((Object[]) newDataSource)[0];
		}

		if (newDataSource instanceof PEPeer) {
			peer = (PEPeer) newDataSource;
			pem = peer.getManager();
		} else {
			peer = null;
			pem = null;
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				refresh();
			}
		});
	}

	@Override
	public void refresh() {
		if (pem == null)
			return;
		if (peer instanceof PEPeerTransportProtocol)
		{
			PEPeerTransportProtocol pet = (PEPeerTransportProtocol) peer;
			BitFlags avl = pet.getAvailable();
			if (avl == null)
				hasPieces = null;
			else
				hasPieces = avl.flags;
		} else
		{
			if (peer.isSeed())
			{
				hasPieces = new boolean[pem.getPieces().length];
				Arrays.fill(hasPieces, true);
			} else
				hasPieces = null;
		}
		super.refresh();
	}
}
