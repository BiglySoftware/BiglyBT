/*
 * Created on Aug 4, 2006 9:18:52 AM
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
package com.biglybt.ui.swt.skin;

import org.eclipse.swt.graphics.Color;

/**
 * @author TuxPaper
 * @created Aug 4, 2006
 *
 */
public interface SWTSkinObjectText
	extends SWTSkinObject
{
	public void setText(String text);

	public void setTextID(String id);

	public void setTextID(String id, String[] params);

	/**
	 * @return
	 *
	 * @since 1.0.0.0
	 */
	int getStyle();

	/**
	 * @param style
	 *
	 * @since 1.0.0.0
	 */
	void setStyle(int style);

	/**
	 * @return
	 *
	 * @since 1.0.0.0
	 */
	public String getText();

	/**
	 * @param l
	 *
	 * @since 1.0.0.0
	 */
	void addUrlClickedListener(SWTSkinObjectText_UrlClickedListener l);

	/**
	 * @param l
	 *
	 * @since 1.0.0.0
	 */
	void removeUrlClickedListener(SWTSkinObjectText_UrlClickedListener l);

	public void setTextColor(Color color);
}
