/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.mainwindow;

import org.eclipse.swt.graphics.Rectangle;

public interface IMainWindow
{

	public static final int WINDOW_ELEMENT_MENU = 1;

	public static final int WINDOW_ELEMENT_TOOLBAR = 2;

	public static final int WINDOW_ELEMENT_STATUSBAR = 3;

	public static final int WINDOW_ELEMENT_TOPBAR = 4;

	public static final int WINDOW_CLIENT_AREA = 6;

	public static final int WINDOW_CONTENT_DISPLAY_AREA = 7;

	public static final int WINDOW_ELEMENT_QUICK_LINKS = 8;

	public static final int WINDOW_ELEMENT_RIGHTBAR = 9;

	public boolean isVisible(int windowElement);

	public void setVisible(int windowElement, boolean value);

	public Rectangle getMetrics(int windowElement);

}
