/*
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
package com.biglybt.ui.swt.mdi;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.ui.swt.pifimpl.UISWTViewCore;

import com.biglybt.ui.mdi.MdiEntry;

/**
 * @author TuxPaper
 * @created Jan 29, 2010
 *
 */
public interface MdiEntrySWT
	extends MdiEntry, UISWTViewCore
{
	public void addListener(MdiSWTMenuHackListener l);

	public void removeListener(MdiSWTMenuHackListener l);

	void setImageLeft(Image imageLeft);

	void redraw(Rectangle hitArea);
}
