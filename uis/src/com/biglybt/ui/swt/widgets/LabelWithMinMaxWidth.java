/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.widgets;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class LabelWithMinMaxWidth
	extends Label
{
	private final int minWidth;
	private final int maxWidth;
	
	public LabelWithMinMaxWidth(Composite parent, int style, int minWidth, int maxWidth ) {
		super(parent, style);
		this.minWidth = minWidth;
		this.maxWidth = maxWidth;
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	protected void checkSubclass() {
	}

	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		Point point = super.computeSize(wHint, hHint, changed);
		if (point.x > 0 && minWidth > 0 && point.x < minWidth) {
			point.x = minWidth;
		}
		if (point.x > 0 && maxWidth > 0 && point.x > maxWidth) {
			point.x = maxWidth;
		}
		return point;
	}
}
