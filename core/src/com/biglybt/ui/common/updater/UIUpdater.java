/*
 * Created on Jun 15, 2006 1:41:18 PM
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
package com.biglybt.ui.common.updater;

/**
 * @author TuxPaper
 * @created Jun 15, 2006
 *
 */
public interface UIUpdater
{

	public void addUpdater(UIUpdatable updateable );

	public boolean isAdded( UIUpdatable updateable );

	public void removeUpdater(UIUpdatable updateable );

	/**
	 * Trigger {@link UIUpdatable#updateUI()} every second
	 */
	public void addPeriodicUpdater(UIUpdatable updateable );

	public void removePeriodicUpdater(UIUpdatable updateable );

	
	public void stopIt();

	public void start();

	public void
	addListener(
		UIUpdaterListener		listener );

	public void
	removeListener(
		UIUpdaterListener		listener );

	public interface
	UIUpdaterListener
	{
		public void
		updateComplete(
			int		count );
	}
}
