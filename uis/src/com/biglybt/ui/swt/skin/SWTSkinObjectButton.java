/*
 * Created on Sep 21, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.skin;

import java.util.ArrayList;

import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.Utils;

/**
 * Native button.  For non-native, use SWTSkinButtonUtility on any SWTSkinObject
 *
 * @author TuxPaper
 * @created Sep 21, 2008
 *
 */
public class SWTSkinObjectButton
	extends SWTSkinObjectBasic
{
	private Button button;
	private ArrayList<ButtonListenerAdapter> buttonListeners = new ArrayList<>(1);
	private boolean textOverride;
	private String imageID;

	public SWTSkinObjectButton(SWTSkin skin, final SWTSkinProperties properties,
			String id, String configID, SWTSkinObject parentSkinObject) {
		super(skin, properties, id, configID, "button", parentSkinObject);

		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}

		button = new Button(createOn, SWT.PUSH);

		button.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Object[] listeners = buttonListeners.toArray();
				for (int i = 0; i < listeners.length; i++) {
					ButtonListenerAdapter l = (ButtonListenerAdapter) listeners[i];
					l.pressed(null, SWTSkinObjectButton.this, 1, e.stateMask);
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		setControl(button);
	}

	// @see SWTSkinObjectBasic#switchSuffix(java.lang.String, int, boolean)
	@Override
	public String switchSuffix(String suffix, int level, boolean walkUp, boolean walkDown) {
		suffix = super.switchSuffix(suffix, level, walkUp, walkDown);

		if (suffix == null) {
			return null;
		}

		String sPrefix = sConfigID + ".text";
		String text = properties.getStringValue(sPrefix + suffix);
		if (text != null) {
			setText(text, true);
		}

		final String fSuffix = suffix;
		String oldImageID = imageID;
		imageID = sConfigID + ".image" + fSuffix;
		final String imageVal = properties.getStringValue(imageID);
		if (imageVal != null) {
			if (oldImageID != null) {
				final ImageLoader imageLoader = skin.getImageLoader(properties);
				imageLoader.releaseImage(oldImageID);
			}
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (button != null && !button.isDisposed() && imageID != null) {
						final ImageLoader imageLoader = skin.getImageLoader(properties);
						Image image = imageLoader.getImage(imageID);
						if (ImageLoader.isRealImage(image)) {
							button.setImage(image);
						} else {
							button.setImage(null);
						}
					}
				}
			});
		}

		return suffix;
	}

	public void addSelectionListener(ButtonListenerAdapter listener) {
		if (buttonListeners.contains(listener)) {
			return;
		}
		buttonListeners.add(listener);
	}

	public void setText(final String text) {
		setText(text, false);
	}

	/**
	 * @param text
	 *
	 * @since 3.1.1.1
	 */
	private void setText(final String text, boolean auto) {
		if (!auto) {
			textOverride = true;
		} else if (textOverride) {
			return;
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (button != null && !button.isDisposed()) {
					button.setText(text);
					int width = properties.getIntValue(sConfigID + ".width", -1);
					if (width == -1) {
  					int minWidth = properties.getIntValue(sConfigID + ".minwidth", -1);
  					if (minWidth >= 0) {
    					FormData fd = (FormData) button.getLayoutData();
    					if (fd == null) {
    						fd = new FormData();
    					}
    					Point size = button.computeSize(SWT.DEFAULT, SWT.DEFAULT);
    					if (size.x < minWidth) {
    						fd.width = minWidth;
    					} else {
    						fd.width = -1;
    					}
    					button.setLayoutData(fd);
    					Utils.relayout(control);
  					}
					}
				}
			}
		});

	}

	public Button getButton() {
		return button;
	}
}
