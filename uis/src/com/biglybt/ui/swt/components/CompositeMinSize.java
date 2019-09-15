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

package com.biglybt.ui.swt.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.imageloader.ImageLoader;

public class CompositeMinSize
	extends Composite
{
	int minWidth = SWT.DEFAULT;
	int minHeight = SWT.DEFAULT;

	public CompositeMinSize(Composite parent, int style) {
		super(parent, style);
	}

	public void setMinSize(Point pt) {
		minWidth = pt.x;
		minHeight = pt.y;
	}

	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		try {
			Point size = super.computeSize(wHint, hHint, changed);
			return betterComputeSize(this, size, wHint, hHint, changed);
		} catch (Throwable t) {
			if ( !( t instanceof NullPointerException )){
				// getting NPEs in CTabFolder.computeTrim :(
				String details = ImageLoader.getBadDisposalDetails(t, this);
				Debug.out(details, t);
			}
			return new Point(wHint == -1 ? 10 : wHint, hHint == -1 ? 10
					: hHint);
		}
	}

	@Override
	public Point computeSize(int wHint, int hHint) {
		try {
			Point size = super.computeSize(wHint, hHint);
			return betterComputeSize(this, size, wHint, hHint);
		} catch (Throwable t) {
			Debug.out(t);
			return new Point(wHint == -1 ? 10 : wHint, hHint == -1 ? 10
					: hHint);
		}
	}

	protected Point betterComputeSize(Composite c, Point size, int wHint,
			int hHint) {
		if (c.getChildren().length == 0 && (size.x == 64 || size.y == 64)) {
			Object ld = c.getLayoutData();
			if (ld instanceof FormData) {
				FormData fd = (FormData) ld;
				if (fd.width != 0 && fd.height != 0) {
					Rectangle trim = c.computeTrim (0, 0, fd.width, fd.height);
					return new Point(trim.width, trim.height);
				}
			}
			return new Point(1, 1);
		}
		if (size.x == 0 || size.y == 0) {
			return size;
		}
		if (minWidth > 0 && size.x < minWidth) {
			size.x = minWidth;
		}
		if (minHeight > 0 && size.y < minHeight) {
			size.y = minHeight;
		}
		return size;
	}

	protected Point betterComputeSize(Composite c, Point size, int wHint, int hHint, boolean changed) {
		if (c.getChildren().length == 0 && (size.x == 64 || size.y == 64)) {
			Object ld = c.getLayoutData();
			if (ld instanceof FormData) {
				FormData fd = (FormData) ld;
				if (fd.width != 0 && fd.height != 0) {
					Rectangle trim = c.computeTrim (0, 0, fd.width, fd.height);
					return new Point(trim.width, trim.height);
				}
			}
			return new Point(1, 1);
		}
		if (size.x == 0 || size.y == 0) {
			return size;
		}
		if (minWidth > 0 && size.x < minWidth) {
			size.x = minWidth;
		}
		if (minHeight > 0 && size.y < minHeight) {
			size.y = minHeight;
		}
		return size;
	}
}
