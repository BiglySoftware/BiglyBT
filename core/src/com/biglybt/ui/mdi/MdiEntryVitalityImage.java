/*
 * Created on Sep 15, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.mdi;


/**
 * @author TuxPaper
 * @created Sep 15, 2008
 */
public interface MdiEntryVitalityImage
{
	public String getImageID();

	public void setImageID(String id );

	public MdiEntry getMdiEntry();

	public void addListener(MdiEntryVitalityImageListener l);

	// Should really be ID
	public void setToolTip(String tooltip);

	public void setVisible(boolean visible);

	public boolean isVisible();

	public void triggerClickedListeners(int x, int y);

	public int getAlignment();

	public void setAlignment(int a);

	/**
	 * Whether the Image is shown outside of the entry.  ie. TabbedMDI has
	 * a section to the right of all tabs that can display actions.
	 */
	boolean getShowOutsideOfEntry();

	/**
	 * Whether the Image is shown outside of the entry.  ie. TabbedMDI has
	 * a section to the right of all tabs that can display actions.
	 */
	void setShowOutsideOfEntry(boolean showOutsideOfEntry);
}
