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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.ui.swt.Utils;
import com.biglybt.core.util.Constants;

public class TwistieSection
	extends Composite
	implements ITwistieConstants
{
	private TwistieContentPanel content = null;

	private TwistieLabel label = null;

	/**
	 * Create a TwistieSection with the given style bit.
	 * <p>Style bit can be one or more of:</p>
	 * <ul>
	 * <li> TwistieLabel.NONE</li> -- The default; does not show description and separator, and is collapsed
	 * <li> TwistieLabel.SHOW_DESCRIPTION</li> -- Show the description below the separator (or title if separator s not shown)
	 * <li> TwistieLabel.SHOW_SEPARATOR</li> -- Show a separator below the title
	 * <li> TwistieLabel.SHOW_EXPANDED</li> -- Show a separator below the title
	 * </ul>
		*/
	public TwistieSection(Composite parent, int style) {
		super(parent, SWT.NONE);
		if (Constants.isWindows) {
			// Windows SWT Bug: button and label BGs won't draw properly without INHERIT FORCE
			setBackgroundMode(SWT.INHERIT_FORCE);
		}
		GridLayout gLayout = new GridLayout();
		gLayout.marginHeight = 0;
		gLayout.marginWidth = 0;
		gLayout.verticalSpacing = 0;
		setLayout(gLayout);

		label = new TwistieLabel(this, style);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		content = new TwistieContentPanel(this, SWT.NONE);
		final GridData gDataExpanded = new GridData(SWT.FILL, SWT.FILL, true, true);
		gDataExpanded.horizontalIndent = 10;
		final GridData gDataCollapsed = new GridData(SWT.FILL, SWT.FILL, true,
				false);
		gDataCollapsed.heightHint = 0;

		content._setLayoutData((label.isCollapsed()) ? gDataCollapsed
				: gDataExpanded);

		label.addTwistieListener(new ITwistieListener() {
			@Override
			public void isCollapsed(boolean value) {
				content._setLayoutData((value) ? gDataCollapsed : gDataExpanded);
				layout(true, true);

			}

		});

	}

	/**
	 * Returns the main body of the section.  Callers can add custom controls onto the returned
	 * <code>Composite</code>
	 * @return
	 */
	public Composite getContent() {
		return content;
	}

	@Override
	public void setBackground(Color color) {
		if (null != label && !label.isDisposed()) {
			label.setBackground(color);
		}

		if (null != content && !content.isDisposed()) {
			content.setBackground(color);
		}

		super.setBackground(color);
	}

	@Override
	public void setForeground(Color color) {
		if (null != label && !label.isDisposed()) {
			label.setForeground(color);
		}

		if (null != content && !content.isDisposed()) {
			content.setForeground(color);
		}
		super.setForeground(color);
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (null != label && !label.isDisposed()) {
			label.setEnabled(enabled);
		}
		super.setEnabled(enabled);
	}

	/**
	 * Delegating to the <code>TwistieLabel</code>
	 * @param listener
	 */
	public void addTwistieListener(ITwistieListener listener) {
		if (null != label && !label.isDisposed()) {
			label.addTwistieListener(listener);
		}
	}

	/**
	 * Delegating to the <code>TwistieLabel</code>
	 * @param listener
	 */
	public void removeTwistieListener(ITwistieListener listener) {
		if (null != label && !label.isDisposed()) {
			label.removeTwistieListener(listener);
		}
	}

	/**
	 * Delegating to the <code>TwistieLabel</code>
	 * @param string
	 */

	public void setDescription(String string) {
		if (null != label && !label.isDisposed()) {
			label.setDescription(string);
		}
	}

	/**
	 * Delegating to the <code>TwistieLabel</code>
	 * @param string
	 */

	public void setTitle(String string) {
		if (null != label && !label.isDisposed()) {
			label.setTitle(string);
		}
	}

	/**
	 * Delegating to the <code>TwistieLabel</code>
	 * @param string
	 */

	@Override
	public void setToolTipText(String string) {
		if (null != label && !label.isDisposed()) {
			Utils.setTT(label,string);
		}
	}

	/**
	 * Delegating to the <code>TwistieLabel</code>
	 * @param color
	 */

	public void setTwistieForeground(Color color) {
		if (null != label && !label.isDisposed()) {
			label.setTwistieForeground(color);
		}
	}

	public boolean
	isCollapsed()
	{
		return( label.isCollapsed());
	}

	public void
	setCollapsed(
		boolean	c )
	{
		label.setCollapsed( c );
	}

	/**
	 * A simple extension of <code>Composite</code> that disallow modifying its layout data
	 * @author knguyen
	 *
	 */
	private static class TwistieContentPanel
		extends Composite
	{

		public TwistieContentPanel(Composite parent, int style) {
			super(parent, style);
			if (Constants.isWindows) {
				// Windows SWT Bug: button and label BGs won't draw properly without INHERIT FORCE
				setBackgroundMode(SWT.INHERIT_FORCE);
			}
		}

		private void _setLayoutData(GridData gData) {
			super.setLayoutData(gData);
		}

		@Override
		public void setLayoutData(Object layoutData) {
			throw new IllegalArgumentException(
					"This is a managed class therefore overriding its LayoutData is an illegal operation");
		}

	}

}
