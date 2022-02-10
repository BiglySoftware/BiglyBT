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

package com.biglybt.ui.swt.skin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;

/**
 * @author TuxPaper
 * @created Jun 8, 2006
 *
 */
public class SWTSkinImageChanger
	implements Listener
{
	private final static boolean DEBUG = false;

	private final String suffix;

	private final int eventOn;

	//private final int eventOff;

	private final int button;
	
	private Control lastControl;

	/**
	 * Default Constructor
	 *
	 * @param suffix
	 * @param eventOff
	 * @param eventOn
	 */
	public SWTSkinImageChanger(String suffix, int eventOn, int eventOff, int button ) {
		this.suffix = suffix;
		this.eventOn = eventOn;
		//this.eventOff = eventOff;
		this.button = button;
	}

	@Override
	public void handleEvent(Event event) {
		Control control = (Control) event.widget;

		if (control == null) {
			return;
		}
		
		if ( button != -1 && event.button != button ){
			return;
		}
		
		//System.out.println("event " + event.type + ";" + control.handle);

		try {
			boolean isExit = event.type == SWT.MouseExit
					|| event.type == SWT.Deactivate;
			if (isExit && lastControl != null) {
				if (control.getParent() == lastControl) {
					if (DEBUG) {
						System.out.println("skip because parent is last control");
					}
					return;
				}
			}

			SWTSkinObject skinObject = (SWTSkinObject) control.getData("SkinObject");
			if (skinObject != null && (DEBUG || skinObject.isDebug())) {
				System.out.println("exit " + skinObject);
			}

			if (isExit && (skinObject instanceof SWTSkinObjectContainer)) {
				// check if exiting and going into child
				SWTSkinObjectContainer soContainer = (SWTSkinObjectContainer) skinObject;
				if (soContainer.getPropogation()) {
					Point pt = control.toDisplay(event.x, event.y);
					Composite composite = soContainer.getComposite();
					Point relPt = composite.toControl(pt);
					// mouse exit and enter happens on client area (not full widget area)
					Rectangle bounds = composite.getClientArea();
					if (bounds.contains(relPt)
							&& composite.getDisplay().getActiveShell() != null) {
						if (skinObject != null && (DEBUG || skinObject.isDebug())) {
							System.out.println("skip " + skinObject
									+ " because going into child");
						}
						return;
					}
				}
			}

			if (isExit && control.getParent() != null) {
				// check if exiting and going into parent
				Composite parent = control.getParent();
				SWTSkinObject soParent = (SWTSkinObject) parent.getData("SkinObject");
				if (soParent != null && (soParent instanceof SWTSkinObjectContainer)) {
					SWTSkinObjectContainer container = (SWTSkinObjectContainer) soParent;
					if (container.getPropogation()) {
						Point pt = control.toDisplay(event.x, event.y);
						Point relPt = container.getComposite().toControl(pt);
						Rectangle bounds = parent.getClientArea();
						if (bounds.contains(relPt)
								&& parent.getDisplay().getActiveShell() != null) {
							if (skinObject != null && (DEBUG || skinObject.isDebug())) {
								System.out.println("skip " + skinObject
										+ " because going into parent " + bounds + ";" + relPt + ";" + parent.getDisplay().getActiveShell());
							}
							// XXX Disabled for now as it doesn't always work
							//return;
						}
					}
				}
			}

			if (skinObject != null) {
				String sSuffix = (event.type == eventOn) ? suffix : "";
				if (skinObject != null && (DEBUG || skinObject.isDebug())) {
					System.out.println(System.currentTimeMillis() + ": " + skinObject
							+ "--" + sSuffix);
				}

				Point ptMouse = control.toDisplay(0, 0);
				while (skinObject != null) {
					Rectangle bounds = skinObject.getControl().getBounds();
					Point pt = skinObject.getControl().toDisplay(0, 0);
					bounds.x = pt.x;
					bounds.y = pt.y;


					if (bounds.contains(ptMouse)) {
						skinObject.switchSuffix(sSuffix, 2, false, false);
						skinObject = skinObject.getParent();
					} else {
						break;
					}
				}
			}
		} finally {
			lastControl = control;
		}
	}
}
