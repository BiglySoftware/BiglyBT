/*
 * Created on Jun 15, 2006 1:05:17 PM
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
 * {@link UIUpdater}
 *
 * @author TuxPaper
 */
public interface UIUpdatable
{
	/**
	 * Update your UI!
	 *
	 * @since 3.1.1.1
	 */
	public void updateUI();

	/**
	 * A name for this UIUpdatable so we can track who's being bad
	 *
	 * @return some name
	 *
	 * @since 3.1.1.1
	 */
	public String getUpdateUIName();
}
