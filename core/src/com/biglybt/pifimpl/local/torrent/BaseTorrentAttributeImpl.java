/*
 * Created on 19-Jul-2006
 * Created by Allan Crooks
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
package com.biglybt.pifimpl.local.torrent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentAttributeEvent;
import com.biglybt.pif.torrent.TorrentAttributeListener;

abstract class BaseTorrentAttributeImpl implements TorrentAttribute {

		private List listeners;

		protected BaseTorrentAttributeImpl() {
			listeners = new ArrayList();
		}

		@Override
		public abstract String getName();
		@Override
		public String[] getDefinedValues() {
			return new String[0];
		}

		@Override
		public void addDefinedValue(String name) {
			throw new RuntimeException("not supported");
		}

		@Override
		public void	removeDefinedValue(String name) {
			throw new RuntimeException("not supported");
		}

		@Override
		public void	addTorrentAttributeListener(TorrentAttributeListener l) {
			this.listeners.add(l);
		}

		@Override
		public void removeTorrentAttributeListener(TorrentAttributeListener	l) {
			this.listeners.remove(l);
		}

		protected List getTorrentAttributeListeners() {
			return this.listeners;
		}

		protected void notifyListeners(TorrentAttributeEvent ev) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {
					((TorrentAttributeListener)itr.next()).event(ev);
				}
				catch (Throwable t) { // Does it need to be Throwable?
					Debug.printStackTrace(t);
				}
			}
		}

}
