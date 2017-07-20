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

package com.biglybt.ui.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ProgressBar;

/**
 * A ProgressBar implementation that allows the on-the-fly switching between determinate and indeterminate modes.
 * @author knguyen
 *
 */
public class AZProgressBar
	extends Composite
{

	private ProgressBar incrementalProgressBar = null;

	private ProgressBar indeterminateProgressBar = null;

	private boolean isIndeterminate = false;

	private StackLayout stack = null;

	/**
	 * Construct a progress bar initialized as incremental and no input button
	 * @param parent
	 */
	public AZProgressBar(Composite parent) {
		this(parent, false);
	}

	/**
	 *
	 * @param parent
	 * @param isIndeterminate
	 * @param useInputButton determines whether the <code>inputButton</code> is available or not
	 * @param image an <code>Image</code> to display; may be null
	 */
	public AZProgressBar(Composite parent, boolean isIndeterminate) {
		super(parent, SWT.NULL);

		incrementalProgressBar = new ProgressBar(this, SWT.HORIZONTAL);
		indeterminateProgressBar = new ProgressBar(this, SWT.HORIZONTAL
				| SWT.INDETERMINATE);

		stack = new StackLayout();
		setLayout(stack);
		pack();

		setIndeterminate(isIndeterminate);
	}

	public void setIndeterminate(boolean isIndeterminate) {
		if (this.isIndeterminate != isIndeterminate || null == stack.topControl) {
			this.isIndeterminate = isIndeterminate;
			if (isIndeterminate) {
				stack.topControl = indeterminateProgressBar;
			} else {
				incrementalProgressBar.setMinimum(0);
				incrementalProgressBar.setMaximum(100);
				incrementalProgressBar.setSelection(0);
				stack.topControl = incrementalProgressBar;
			}
			layout();
		}
	}

	public void done() {
		incrementalProgressBar.setSelection(incrementalProgressBar.getMaximum());
		stack.topControl = null;
		layout();
	}

	public void setSelection(int value) {
		if (incrementalProgressBar.getMaximum() < value) {
			done();
		} else {
			incrementalProgressBar.setSelection(value);
		}
	}

	public void setPercentage(final int percentage) {
		if (percentage > 0 && percentage < 101) {

			int range = incrementalProgressBar.getMaximum()
					- incrementalProgressBar.getMinimum();
			setSelection(incrementalProgressBar.getMinimum()
					+ (range * percentage / 100));

		}

	}

	public int getMaximum() {
		return incrementalProgressBar.getMaximum();
	}

	public int getMinimum() {
		return incrementalProgressBar.getMinimum();
	}

	public int getSelection() {
		return incrementalProgressBar.getSelection();
	}

	public void setMaximum(int value) {
		incrementalProgressBar.setMaximum(value);
	}

	public void setMinimum(int value) {
		incrementalProgressBar.setMinimum(value);
	}

	public boolean isIndeterminate() {
		return isIndeterminate;
	}

}
