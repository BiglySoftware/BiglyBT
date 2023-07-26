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

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Utils;

/**
 * Simple encapsulation of SWTSkinObjectContainer that provides typical button
 * functionality
 *
 */
public class SWTSkinButtonUtility
{
	ArrayList<ButtonListenerAdapter> listeners = new ArrayList<>();

	private final SWTSkinObject skinObject;

	private final String imageViewID;

	public static class ButtonListenerAdapter
	{
		public void pressed(SWTSkinButtonUtility buttonUtility,
				SWTSkinObject skinObject, int stateMask) {
		}

		public void pressed(SWTSkinButtonUtility buttonUtility,
				SWTSkinObject skinObject, int button, int stateMask) {
			pressed( buttonUtility, skinObject, stateMask );
		}

		public boolean held(SWTSkinButtonUtility buttonUtility) {
			return false;
		}

		public void disabledStateChanged(SWTSkinButtonUtility buttonUtility,
				boolean disabled) {
		}
		
		public void entered( SWTSkinButtonUtility buttonUtility, SWTSkinObject skinObject, int stateMask ){
		}
	}

	public SWTSkinButtonUtility(SWTSkinObject skinObject) {
		this(skinObject, null);
	}

	public SWTSkinButtonUtility(SWTSkinObject skinObject, String imageViewID) {
		this.skinObject = skinObject;
		this.imageViewID = imageViewID;

		if (skinObject == null) {
			Debug.out("Can't make button out of null skinObject");
			return;
		}
		if (skinObject.getControl() == null) {
			Debug.out("Can't make button out of null skinObject control");
			return;
		}

		if (skinObject instanceof SWTSkinObjectButton) {
			return;
		}

		Listener l = new Listener() {
			boolean bDownPressed;

			private TimerEvent timerEvent;

			@Override
			public void handleEvent(Event event) {
				int et = event.type;
				
				if ( et == SWT.MouseDown || et == SWT.MouseUp ){
					if ( event.button == 1 ){
						if (event.type == SWT.MouseDown) {
							if (timerEvent == null) {
								timerEvent = 
									SimpleTimer.addEvent(
										"MouseHold",
										SystemTime.getOffsetTime(1000), 
										(ev)->{
											Utils.execSWTThread(()->{
												timerEvent = null;
		
												if (!bDownPressed) {
													return;
												}
												bDownPressed = false;
		
												boolean stillPressed = true;
												
												if ( Utils.getCursorControl() == event.widget ){
													
													for (ButtonListenerAdapter l : listeners) {
														stillPressed &= !l.held(SWTSkinButtonUtility.this);
													}
												}
												bDownPressed = stillPressed;
											});
										});
							}
							
							bDownPressed = true;
							return;
						} else {
							if (timerEvent != null) {
								timerEvent.cancel();
								timerEvent = null;
							}
							if (!bDownPressed) {
								return;
							}
						}
		
						bDownPressed = false;
					}
					
					if (isDisabled()) {
						return;
					}
	
					if ( et == SWT.MouseUp && Utils.getCursorControl() != event.widget ){
						
						return;
					}
					
					for (ButtonListenerAdapter l : listeners) {
						l.pressed(SWTSkinButtonUtility.this,
								SWTSkinButtonUtility.this.skinObject, event.button, event.stateMask);
					}
				}else{
					
					for (ButtonListenerAdapter l : listeners) {
						l.entered(SWTSkinButtonUtility.this,
								SWTSkinButtonUtility.this.skinObject, event.stateMask);
					}
					
				}
			}
		};
		if (skinObject instanceof SWTSkinObjectContainer) {
			Utils.addListenerAndChildren((Composite) skinObject.getControl(), SWT.MouseUp, l);
			Utils.addListenerAndChildren((Composite) skinObject.getControl(), SWT.MouseDown, l);
			Utils.addListenerAndChildren((Composite) skinObject.getControl(), SWT.MouseEnter, l);
		} else {
			skinObject.getControl().addListener(SWT.MouseUp, l);
			skinObject.getControl().addListener(SWT.MouseDown, l);
			skinObject.getControl().addListener(SWT.MouseEnter, l);
		}
	}

	public boolean isDisabled() {
		return skinObject == null ? true : skinObject.getSuffix()
			.contains("-disabled");
	}

	private boolean inSetDisabled = false;

	private boolean lastDisabledState = false;

	public void setDisabled(final boolean disabled) {
		if (inSetDisabled || skinObject == null) {
			return;
		}
		inSetDisabled = true;
		try {
			if (disabled == isDisabled()) {
				return;
			}
			if (skinObject instanceof SWTSkinObjectButton) {
				lastDisabledState = disabled;
				Utils.execSWTThreadLater(100, new AERunnable() {
					@Override
					public void runSupport() {
						((SWTSkinObjectButton) skinObject).getButton().setEnabled(
								!lastDisabledState);
					}
				});
			}
			String suffix = disabled ? "-disabled" : "";
			skinObject.switchSuffix(suffix, 1, false);

			for (ButtonListenerAdapter l : listeners) {
				l.disabledStateChanged(SWTSkinButtonUtility.this, disabled);
			}
		} finally {
			inSetDisabled = false;
		}
	}

	public void addSelectionListener(ButtonListenerAdapter listener) {
		if (skinObject instanceof SWTSkinObjectButton) {
			((SWTSkinObjectButton) skinObject).addSelectionListener(listener);
			return;
		}

		if (listeners.contains(listener)) {
			return;
		}
		listeners.add(listener);
	}

	public SWTSkinObject getSkinObject() {
		return skinObject;
	}

	public void setTextID(final String id) {
		if (skinObject == null) {
			return;
		}
		if (skinObject instanceof SWTSkinObjectButton) {
			((SWTSkinObjectButton) skinObject).setText(MessageText.getString(id));
			return;
		}
		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				if (skinObject instanceof SWTSkinObjectText) {
					SWTSkinObjectText skinTextObject = (SWTSkinObjectText) skinObject;
					skinTextObject.setTextID(id);
				} else if (skinObject instanceof SWTSkinObjectContainer) {
					SWTSkinObject[] children = ((SWTSkinObjectContainer) skinObject).getChildren();
					if (children.length > 0 && children[0] instanceof SWTSkinObjectText) {
						SWTSkinObjectText skinTextObject = (SWTSkinObjectText) children[0];
						skinTextObject.setTextID(id);
					}
				}
				Utils.relayout(skinObject.getControl());
			}
		});
	}

	public void setImage(final String id) {
		if (skinObject == null) {
			return;
		}
		if (skinObject instanceof SWTSkinObjectButton) {
			// TODO implement
			return;
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (imageViewID != null) {
					SWTSkinObject skinImageObject = skinObject.getSkin().getSkinObject(
							imageViewID, skinObject);
					if (skinImageObject instanceof SWTSkinObjectImage) {
						((SWTSkinObjectImage) skinImageObject).setImageByID(id, null);
						return;
					}
				}
				if (skinObject instanceof SWTSkinObjectImage) {
					SWTSkinObjectImage skinImageObject = (SWTSkinObjectImage) skinObject;
					skinImageObject.setImageByID(id, null);
				} else if (skinObject instanceof SWTSkinObjectContainer) {
					SWTSkinObject[] children = ((SWTSkinObjectContainer) skinObject).getChildren();
					if (children.length > 0 && children[0] instanceof SWTSkinObjectImage) {
						SWTSkinObjectImage skinImageObject = (SWTSkinObjectImage) children[0];
						skinImageObject.setImageByID(id, null);
					}
				}
			}
		});
	}

	public void setTooltipID(final String id) {
		if (skinObject == null) {
			return;
		}
		if (skinObject instanceof SWTSkinObjectButton) {
			// TODO implement
			return;
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (imageViewID != null) {
					SWTSkinObject skinImageObject = skinObject.getSkin().getSkinObject(
							imageViewID, skinObject);
					if (skinImageObject instanceof SWTSkinObjectImage) {
						((SWTSkinObjectImage) skinImageObject).setTooltipID(id);
						return;
					}
				}
				if (skinObject instanceof SWTSkinObjectImage) {
					SWTSkinObjectImage skinImageObject = (SWTSkinObjectImage) skinObject;
					skinImageObject.setTooltipID(id);
				} else if (skinObject instanceof SWTSkinObjectContainer) {
					SWTSkinObject[] children = ((SWTSkinObjectContainer) skinObject).getChildren();
					if (children.length > 0 && children[0] instanceof SWTSkinObjectImage) {
						SWTSkinObjectImage skinImageObject = (SWTSkinObjectImage) children[0];
						skinImageObject.setTooltipID(id);
					}
				}
			}
		});
	}
}
