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

package com.biglybt.ui.swt.twistie;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.biglybt.ui.swt.Utils;

/**
 * A Label with a twistie graphic at the beginning; every time this label is clicked the
 * twistie graphic toggles between pointing to the right and pointing down.
 *
 * @author knguyen
 *
 */
public class TwistieLabel
	extends Composite
	implements ITwistieConstants
{

	private int style = NONE;

	/**
	 * An array of points for a triangle pointing downward
	 */
	static final int[] points_for_expanded = {
		0,
		2,
		8,
		2,
		4,
		6
	};

	/**
	 * An array of points for a triangle pointing to the right
	 *
	 */
	static final int[] points_for_collapsed = {
		2,
		-1,
		2,
		8,
		6,
		4
	};

	/**
	 * <code>Label</code> to display the text for this twistie
	 */
	private Label titleLabel = null;

	/**
	 * The <code>Color</code> to use for the twistie graphic itself;
	 * defaults to the same as the foreground color of the titleLabel
	 */
	private Color twistieColor = null;

	/**
	 * The state of the control; callers can check this state by calling {@link #isCollapsed()}
	 */
	private boolean isCollapsed = true;

	/**
	 * An optional Label to display the description
	 */
	private Label descriptionLabel = null;

	private List listeners = new ArrayList();

	/**
	 * Create a twistie Label with the given style bit.
	 * <p>Style bit can be one or more of:</p>
	 * <ul>
	 * <li> TwistieLabel.NONE</li> -- The default; does not show description and separator, and is collapsed
	 * <li> TwistieLabel.SHOW_DESCRIPTION</li> -- Show the description below the separator (or title if separator s not shown)
	 * <li> TwistieLabel.SHOW_SEPARATOR</li> -- Show a separator below the title
	 * <li> TwistieLabel.SHOW_EXPANDED</li> -- Show a separator below the title
	 * </ul>
	 *
	 * @param parent
	 * @param style
	 */
	public TwistieLabel(Composite parent, int style) {
		super(parent, SWT.NONE);
		this.style = style;

		GridLayout gLayout = new GridLayout();
		gLayout.marginHeight = 0;
		gLayout.marginWidth = 0;
		gLayout.verticalSpacing = 0;
		gLayout.horizontalSpacing = 0;
		setLayout(gLayout);

		titleLabel = new Label(this, SWT.NONE);

		if ((this.style & SHOW_SEPARATOR) != 0) {
			Control separator = Utils.createSkinnedLabelSeparator(this, SWT.HORIZONTAL);
			GridData labelData = new GridData(SWT.FILL, SWT.CENTER, true, false);
			labelData.horizontalIndent = 10;
			separator.setLayoutData(labelData);
		}

		if ((this.style & SHOW_DESCRIPTION) != 0) {
			descriptionLabel = new Label(this, SWT.WRAP);
			GridData labelData = new GridData(SWT.FILL, SWT.FILL, true, false);
			labelData.horizontalIndent = 10;
			descriptionLabel.setLayoutData(labelData);

			/*
			 * Change the font to be italic for the description
			 */
			Font initialFont = descriptionLabel.getFont();
			FontData[] fontData = initialFont.getFontData();
			for (int i = 0; i < fontData.length; i++) {
				fontData[i].setStyle(fontData[i].getStyle() | SWT.ITALIC);
			}
			descriptionLabel.setFont(new Font(getDisplay(), fontData));

		}

		if ((this.style & SHOW_EXPANDED) != 0) {
			isCollapsed = false;
		}

		/*
		 * Leaving a little margin on the left; this is where we draw the twistie graphic
		 */
		GridData labelData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		labelData.horizontalIndent = 10;
		titleLabel.setLayoutData(labelData);

		/*
		 * Add our mouse interceptor to the control and the title label
		 */
		MouseInterceptor interceptor = new MouseInterceptor();
		super.addMouseListener(interceptor);
		titleLabel.addMouseListener(interceptor);

		/*
		 * Listens to the paint event and do the drawing here
		 */
		addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {

				/*
				 * The graphic is drawn to the left of the titleLabel so define the offset from the titleLabel
				 */
				int offsetX = titleLabel.getBounds().x - 10;
				int offsetY = titleLabel.getBounds().y + 3;

				if (null != twistieColor) {
					e.gc.setBackground(twistieColor);
				} else {
					e.gc.setBackground(getForeground());
				}

				if (isCollapsed) {
					e.gc.fillPolygon(translate(points_for_collapsed, offsetX, offsetY));
				} else {
					e.gc.fillPolygon(translate(points_for_expanded, offsetX, offsetY));
				}
			}
		});
	}

	/**
	 * Translates the twistie points array to compensate for the given x and y offset
	 * @param data
	 * @param x
	 * @param y
	 * @return
	 */
	private int[] translate(int[] data, int x, int y) {

		int[] target = new int[data.length];
		for (int i = 0; i < data.length; i += 2) {
			target[i] = data[i] + x;
		}
		for (int i = 1; i < data.length; i += 2) {
			target[i] = data[i] + y;
		}
		return target;
	}

	/**
	 * Add a mouse listener to the control and also the <code>titleLabel</code>
	 */
	@Override
	public void addMouseListener(MouseListener listener) {
		if (null != titleLabel) {
			titleLabel.addMouseListener(listener);
		}
		super.addMouseListener(listener);
	}

	/**
	 * Remove the mouse listener from the control and also the <code>titleLabel</code>
	 */
	@Override
	public void removeMouseListener(MouseListener listener) {
		if (null != titleLabel) {
			titleLabel.removeMouseListener(listener);
		}
		super.removeMouseListener(listener);
	}

	/**
	 * Sets the color to be used for drawing the twistie graphic
	 * @param color
	 */
	public void setTwistieForeground(Color color) {
		twistieColor = color;
	}

	/**
	 * Sets the foreground color for the control and also all the text-base children
	 */
	@Override
	public void setForeground(Color color) {
		if (null != titleLabel && !titleLabel.isDisposed()) {
			titleLabel.setForeground(color);
		}
		if (null != descriptionLabel && !descriptionLabel.isDisposed()) {
			descriptionLabel.setForeground(color);
		}

		if (null == twistieColor) {
			twistieColor = color;
		}

		super.setForeground(color);
	}

	/**
	 * Sets the background color for the control and also all the text-base children
	 */
	@Override
	public void setBackground(Color color) {
		if (null != titleLabel) {
			titleLabel.setBackground(color);
		}
		if (null != descriptionLabel) {
			descriptionLabel.setBackground(color);
		}

		super.setBackground(color);
	}

	/**
	 * Sets the text to display as the title
	 * @param string
	 */
	public void setTitle(String string) {
		if (null != titleLabel) {
			titleLabel.setText(string);
		}
	}

	/**
	 * Sets the text to display as the description; this is not in effect unless the {@link #SHOW_DESCRIPTION} flag is also set
	 * @param string
	 */
	public void setDescription(String string) {
		if (null != descriptionLabel) {
			descriptionLabel.setText(string);
		}
	}

	/**
	 * Sets the tooltip for the control and also all the text-base children
	 */
	@Override
	public void setToolTipText(String string) {
		if (null != titleLabel) {
			Utils.setTT(titleLabel,string);
		}

		if (null != descriptionLabel) {
			Utils.setTT(descriptionLabel,string);
		}

		if ( Utils.getTTEnabled()){
			super.setToolTipText(string);
		}
	}

	/**
	 * Sets the enablement for the control and also all the text-base children
	 */
	@Override
	public void setEnabled(boolean enabled) {
		if (null != titleLabel) {
			titleLabel.setEnabled(enabled);
		}

		super.setEnabled(enabled);
	}

	/**
	 * Returns whether this control is in a collapsed state
	 * @return
	 */
	public boolean isCollapsed() {
		return isCollapsed;
	}

	public void
	setCollapsed(
		boolean	c )
	{
		if ( c != isCollapsed ){
			isCollapsed = c;
			redraw();
			notifyTwistieListeners();
		}
	}

	/**
	 * Add a listener to be notified whenever this control is collapsed or expanded; listeners
	 * can check the collapsed/expanded state on the control and perform layout changes if need be.
	 * @param listener
	 */
	public void addTwistieListener(ITwistieListener listener) {
		listeners.add(listener);
	}

	public void removeTwistieListener(ITwistieListener listener) {
		listeners.remove(listener);
	}

	private void notifyTwistieListeners() {
		for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
			((ITwistieListener) iterator.next()).isCollapsed(isCollapsed());

		}
	}

	/**
	 * A listener that intercepts mouseDown events from the control and the title label so we can
	 * fire a single event to the listener to signal that the control has been collapsed or expanded.
	 *
	 *
	 * @author knguyen
	 *
	 */
	private class MouseInterceptor
		extends MouseAdapter
	{

		/*
		 * Listens to the mouse click and toggle the isCollapsed flag then force a redraw to update the control
		 */
		@Override
		public void mouseDown(MouseEvent e) {
			isCollapsed = !isCollapsed;
			redraw();
			notifyTwistieListeners();
		}

	}
}