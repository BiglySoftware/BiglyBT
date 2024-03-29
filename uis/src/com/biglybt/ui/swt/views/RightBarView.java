/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views;

import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.pif.UISWTInstance;

public class RightBarView
	extends BarViewParent
{
	public
	RightBarView()
	{
		super(	SkinConstants.VIEWID_RIGHTBAR,
				UISWTInstance.VIEW_RIGHTBAR_AREA,
				"rightbar-area-plugins",
				"rightbar-area-plugin",
				"rightbar-line",
				"v3.rightbar.width",
				true );
	}
}