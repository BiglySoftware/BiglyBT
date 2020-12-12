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

package com.biglybt.ui.swt.shells;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.util.AERunnable;

public class ShellSlider
{

	private final boolean DEBUG = false;

	private int STEP = 8;

	private int PAUSE = 30;

	private Shell shell;

	private Rectangle shellBounds = null;

	private Rectangle endBounds;

	private final int direction;

	private final boolean slideIn;

	/**
	 * Slide In
	 *
	 * @param shell
	 * @param direction
	 * @param endBounds
	 */
	public ShellSlider(final Shell shell, int direction, final Rectangle endBounds) {
		this.shell = shell;
		this.endBounds = endBounds;
		this.slideIn = true;
		this.direction = direction;

		if (shell == null || shell.isDisposed())
			return;

		Display display = shell.getDisplay();
		display.syncExec(() -> {
			if (shell.isDisposed()) {
				return;
			}

			switch (direction) {
				case SWT.UP:
				default:
					shellBounds = new Rectangle(endBounds.x, endBounds.y
							+ endBounds.height, endBounds.width, 0);
					break;
			}
			shell.setBounds(shellBounds);
			shell.setVisible(true);

			if (DEBUG) {
				System.out.println("Slide In: " + shell.getText());
			}
		});
	}

	/**
	 * Slide Out
	 *
	 * @param shell
	 * @param direction
	 */
	public ShellSlider(final Shell shell, int direction) {
		this.shell = shell;
		this.slideIn = false;
		this.direction = direction;
		if (DEBUG && canContinue())
			shell.getDisplay().syncExec(new Runnable() {
				@Override
				public void run() {
					System.out.println("Slide Out: " + shell.getText());
				}
			});
	}

	private boolean canContinue() {
		if (shell == null || shell.isDisposed())
			return false;

		if (shellBounds == null)
			return true;

		//System.out.println((slideIn ? "In" : "Out") + ";" + direction + ";S:" + shellBounds + ";" + endBounds);
		if (slideIn) {
			if (direction == SWT.UP) {
				return shellBounds.y > endBounds.y;
			}
			// TODO: Other directions
		} else {
			if (direction == SWT.RIGHT) {
				// stop early, because some OSes have trim, and won't allow the window
				// to go smaller than it.
				return shellBounds.width > 10;
			}
		}
		return false;
	}

	public void run() {

		while (canContinue()) {
			long lStartedAt = System.currentTimeMillis();

			shell.getDisplay().syncExec(new AERunnable() {
				@Override
				public void runSupport() {
					if (shell == null || shell.isDisposed()) {
						return;
					}

					if (shellBounds == null) {
						shellBounds = shell.getBounds();
					}

					int delta;
					if (slideIn) {
						switch (direction) {
							case SWT.UP:
								delta = Math.min(endBounds.height - shellBounds.height, STEP);
								shellBounds.height += delta;
								delta = Math.min(shellBounds.y - endBounds.y, STEP);
								shellBounds.y -= delta;
								break;

							default:
								break;
						}
					} else {
						switch (direction) {
							case SWT.RIGHT:
								delta = Math.min(shellBounds.width, STEP);
								shellBounds.width -= delta;
								shellBounds.x += delta;

								if (shellBounds.width == 0) {
									shell.dispose();
									return;
								}
								break;

							default:
								break;
						}
					}

					shell.setBounds(shellBounds);
					shell.update();
				}
			});

			try {
				long lDrawTime = System.currentTimeMillis() - lStartedAt;
				long lSleepTime = PAUSE - lDrawTime;
				if (lSleepTime < 15) {
					double d = (lDrawTime + 15.0) / PAUSE;
					PAUSE *= d;
					STEP *= d;
					lSleepTime = 15;
				}
				Thread.sleep(lSleepTime);
			} catch (Exception e) {
			}
		}
	}
}
