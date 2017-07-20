/*
 * Created on Jan 9, 2012
 * Created by Paul Gardner
 *
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


package com.biglybt.ui.swt.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.shells.GCStringPrinter;

public class
DoubleBufferedLabel
	extends Canvas implements PaintListener
{
	private String text = "";

	public DoubleBufferedLabel(
		Composite 	parent,
		int 		style )
	{
		super( parent, style | SWT.DOUBLE_BUFFERED );

			// only support GridLayout I'm afraid...

		GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_CENTER | GridData.VERTICAL_ALIGN_FILL);

		setLayoutData(gridData);

		addPaintListener(this);
	}

	@Override
	public void
	setLayoutData(
		Object	ld )
	{
		if ( ld instanceof GridData ){

			GridData gd = (GridData)ld;

				// need to ensure we have the right vertical align

			gd.verticalAlignment	= 4;
		}

		super.setLayoutData( ld );
	}

	@Override
	public void
	paintControl(
		PaintEvent e)
	{
		e.gc.setAdvanced(true);

		Rectangle clientArea = getClientArea();

		GCStringPrinter sp =
			new GCStringPrinter(e.gc, getText(), clientArea, true, true, SWT.LEFT);

		sp.printString(e.gc, clientArea, SWT.LEFT);
	}


	@Override
	public Point
	computeSize(
		int wHint,
		int hHint)
	{
		return computeSize(wHint, hHint, true);
	}

	@Override
	public Point
	computeSize(
		int 	wHint,
		int 	hHint,
		boolean changed )
	{
		try {
			Point pt = computeSize(wHint, hHint, changed, false);

			return pt;

		} catch (Throwable t){

			Debug.out("Error while computing size for DoubleBufferedLabel with text:"
					+ getText() + "; " + t.toString());

			return new Point(0, 0);
		}
	}

	public Point
	computeSize(
		int 	wHint,
		int 	hHint,
		boolean changed,
		boolean realWidth )
	{
		/* Can't do this as things are often layed out when invisible and
		 * don't get redone on visibility change :(

		if (!isVisible()){

			return (new Point(0, 0));
		}
		*/

		if (wHint != SWT.DEFAULT && hHint != SWT.DEFAULT) {
			return new Point(wHint, hHint);
		}
		Point pt = new Point(wHint, hHint);

		Point lastSize = new Point(0, 0);

		GC gc = new GC(this);

		GCStringPrinter sp = new GCStringPrinter(gc, getText(), new Rectangle(0,
				0, 10000, 20), true, true, SWT.LEFT);

		sp.calculateMetrics();

		Point lastTextSize = sp.getCalculatedSize();

		gc.dispose();

		lastSize.x += lastTextSize.x + 10;
		lastSize.y = Math.max(lastSize.y, lastTextSize.y);

		if (wHint == SWT.DEFAULT) {
			pt.x = lastSize.x;
		}
		if (hHint == SWT.DEFAULT) {
			pt.y = lastSize.y;
		}

		return pt;
	}

	public String
	getText()
	{
		return text;
	}

	public void
	setText(
		String text)
	{
		if (text == null){
			text = "";
		}

		if (text.equals(getText())){
			return;
		}

		this.text = text;

		redraw();
	}
}
