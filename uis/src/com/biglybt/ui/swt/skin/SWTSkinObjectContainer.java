/*
 * Created on Jun 26, 2006 11:38:47 AM
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
package com.biglybt.ui.swt.skin;

import java.util.ArrayList;

import com.biglybt.ui.swt.debug.ObfuscateImage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AERunnableObject;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.CompositeMinSize;


/**
 * A SWTSkinObject that contains other SWTSkinObjects
 *
 * @author TuxPaper
 * @created Jun 26, 2006
 *
 */
public class SWTSkinObjectContainer
	extends SWTSkinObjectBasic
{
	boolean bPropogate = false;

	boolean bPropogateDown = false;

	private String[] sTypeParams = null;

	private int minWidth;

	private int minHeight;

	public SWTSkinObjectContainer(SWTSkin skin, SWTSkinProperties properties,
			String sID, String sConfigID, String[] sTypeParams, SWTSkinObject parent) {
		super(skin, properties, sID, sConfigID, "container", parent);
		this.sTypeParams = sTypeParams;
		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}
		createComposite(createOn);
	}

	public SWTSkinObjectContainer(SWTSkin skin, SWTSkinProperties properties,
			String sID, String sConfigID, SWTSkinObject parent) {
		super(skin, properties, sID, sConfigID, "container", parent);
		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}
		createComposite(createOn);
	}

	public SWTSkinObjectContainer(SWTSkin skin, SWTSkinProperties properties,
			Control control, String sID, String sConfigID, String type,
			SWTSkinObject parent) {
		super(skin, properties, sID, sConfigID, type, parent);

		if (control != null) {
			triggerListeners(SWTSkinObjectListener.EVENT_CREATED);
			setControl(control);
		}
	}

	protected Composite createComposite(Composite createOn) {
		int style = SWT.NONE;
		if (properties.getIntValue(sConfigID + ".border", 0) == 1) {
			style = SWT.BORDER;
		}
		if (properties.getBooleanValue(sConfigID + ".doublebuffer", false)) {
			style |= SWT.DOUBLE_BUFFERED;
		}

		minWidth = properties.getPxValue(sConfigID + ".minwidth", -1);
		minHeight = properties.getPxValue(sConfigID + ".minheight", -1);

		final Composite parentComposite;
		if (skin.DEBUGLAYOUT) {
			System.out.println("linkIDtoParent: Create Composite " + sID + " on "
					+ createOn);
			parentComposite = Utils.createSkinnedGroup(createOn, style);
			((Group) parentComposite).setText(sConfigID == null ? sID : sConfigID);
			parentComposite.setData("DEBUG", "1");
		} else {
			if (sTypeParams == null || sTypeParams.length < 2
					|| !sTypeParams[1].equalsIgnoreCase("group")) {
  			// Lovely SWT has a default size of 64x64 if no children have sizes.
  			// Let's fix that..
  			parentComposite = new CompositeMinSize(createOn, style);
  			((CompositeMinSize) parentComposite).setMinSize(new Point(minWidth, minHeight));
			} else {
  			parentComposite = Utils.createSkinnedGroup(createOn, style);
			}
		}

		parentComposite.setLayout(new FormLayout());

		control = parentComposite;

		if ( properties.getBooleanValue(sConfigID + ".auto.defer.layout", false)) {

			Listener show_hide_listener =
				new Listener()
				{
					@Override
					public void
					handleEvent(
						Event event )
					{
						parentComposite.setLayoutDeferred( event.type == SWT.Hide );
					}
				};

			parentComposite.addListener( SWT.Show, show_hide_listener );
			parentComposite.addListener( SWT.Hide, show_hide_listener );
		}

		setControl(control);

		return parentComposite;
	}

	// @see SWTSkinObjectBasic#setControl(org.eclipse.swt.widgets.Control)
	@Override
	public void setControl(Control control) {
		bPropogateDown = properties.getIntValue(sConfigID + ".propogateDown", 1) == 1;

		super.setControl(control);
	}

	@Override
	protected void setViewID(String viewID) {
		super.setViewID(viewID);
		if (skin.DEBUGLAYOUT && control != null) {
			((Group) control).setText("[" + viewID + "]");
		}
	}

	public SWTSkinObject[] getChildren() {
		if (isDisposed()) {
			return new SWTSkinObject[0];
		}
		SWTSkinObject[] so = (SWTSkinObject[]) Utils.execSWTThreadWithObject(
				"getChildren", new AERunnableObject() {

					@Override
					public Object runSupport() {
						if (control.isDisposed()) {
							return new SWTSkinObject[0];
						}
						Control[] swtChildren = ((Composite) control).getChildren();
						ArrayList<SWTSkinObject> list = new ArrayList<>(swtChildren.length);
						for (int i = 0; i < swtChildren.length; i++) {
							Control childControl = swtChildren[i];
							SWTSkinObject so = (SWTSkinObject) childControl.getData("SkinObject");
							if (so != null) {
								list.add(so);
							}
						}

						return list.toArray(new SWTSkinObject[list.size()]);
					}
				}, 2000);
		if (so == null) {
			System.err.println("Tell Tux to fix this " + Debug.getCompressedStackTrace());
			return oldgetChildren();
		}
		return so;
	}

	// TODO: Need find child(view id)
	public SWTSkinObject[] oldgetChildren() {
		String[] widgets = properties.getStringArray(sConfigID + ".widgets");
		if (widgets == null) {
			return new SWTSkinObject[0];
		}

		ArrayList list = new ArrayList();
		for (int i = 0; i < widgets.length; i++) {
			String id = widgets[i];
			SWTSkinObject skinObject = skin.getSkinObjectByID(id, this);
			if (skinObject != null) {
				list.add(skinObject);
			}
		}

		SWTSkinObject[] objects = new SWTSkinObject[list.size()];
		objects = (SWTSkinObject[]) list.toArray(objects);

		return objects;
	}

	public Composite getComposite() {
		return (Composite) control;
	}

	// @see SWTSkinObjectBasic#switchSuffix(java.lang.String)
	@Override
	public String switchSuffix(final String suffix, final int level, boolean walkUp, boolean walkDown) {
		String sFullsuffix = super.switchSuffix(suffix, level, walkUp, walkDown);

		if (bPropogateDown && walkDown && suffix != null && control != null
				&& !control.isDisposed()) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					SWTSkinObject[] children = getChildren();
					for (int i = 0; i < children.length; i++) {
						children[i].switchSuffix(suffix, level, false);
					}
				}
			});
		}
		return sFullsuffix;
	}

	public void setPropogation(boolean propogate) {
		bPropogate = propogate;
		if (skin.DEBUGLAYOUT) {
			((Group) control).setText(((Group) control).getText()
					+ (bPropogate ? ";P" : ""));
		}
	}

	public boolean getPropogation() {
		return bPropogate;
	}

	public void setDebugAndChildren(boolean b) {
		setDebug(true);
		SWTSkinObject[] children = getChildren();
		for (int i = 0; i < children.length; i++) {
			if (children[i] instanceof SWTSkinObjectContainer) {
				((SWTSkinObjectContainer)children[i]).setDebugAndChildren(b);
			} else {
				children[i].setDebug(b);
			}
		}
	}

	protected boolean superSetIsVisible(boolean visible, boolean walkup) {
		boolean changed = super.setIsVisible(visible, walkup);
		return changed;
	}

	// @see SWTSkinObjectBasic#setIsVisible(boolean)
	@Override
	protected boolean setIsVisible(boolean visible, boolean walkup) {
		if (Utils.isThisThreadSWT() && !control.isDisposed()
				&& !control.getShell().isVisible()) {
			return false;
		}
		boolean changed = super.setIsVisible(visible, walkup && visible);

		if (!changed) {
			return false;
		}

		// Currently we ignore "changed" and set visibility on children to ensure
		// things display
		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				SWTSkinObject[] children = getChildren();
				if (children.length == 0) {
					return;
				}
				if (SWTSkin.DEBUG_VISIBILITIES) {
					System.out.println(">> setIsVisible for " + children.length
							+ " children of " + SWTSkinObjectContainer.this);
				}
				for (int i = 0; i < children.length; i++) {
					if (children[i] instanceof SWTSkinObjectBasic) {
						SWTSkinObjectBasic child = ((SWTSkinObjectBasic) children[i]);
						Control childControl = child.getControl();
						if (childControl != null && !childControl.isDisposed()) {
							//child.setIsVisible(visible, false);
							//System.out.println("child control " + child + " is " + (childControl.isVisible() ? "visible" : "invisible"));
							child.setIsVisible(childControl.isVisible(), false);
						}
					}
				}
				getComposite().layout();
				if (SWTSkin.DEBUG_VISIBILITIES) {
					System.out.println("<< setIsVisible for " + children.length
							+ " children");
				}
			}
		});
		return changed;
	}

	public void childAdded(SWTSkinObject soChild) {
	}

	// @see SWTSkinObjectBasic#obfuscatedImage(org.eclipse.swt.graphics.Image, org.eclipse.swt.graphics.Point)
	@Override
	public Image obfuscatedImage(Image image) {
		if (!isVisible()) {
			return image;
		}

		if (getSkinView() instanceof ObfuscateImage) {
			image = ((ObfuscateImage) getSkinView()).obfuscatedImage(image);
		}

		Control[] swtChildren = ((Composite) control).getChildren();
		for (int i = 0; i < swtChildren.length; i++) {
			Control childControl = swtChildren[i];

			SWTSkinObject so = (SWTSkinObject) childControl.getData("SkinObject");
			if (so instanceof ObfuscateImage) {
				ObfuscateImage oi = (ObfuscateImage) so;
				oi.obfuscatedImage(image);
			} else if (so == null) {
				ObfuscateImage oi = (ObfuscateImage) childControl.getData("ObfuscateImage");
				if (oi != null) {
					oi.obfuscatedImage(image);
					continue;
				}
				if (childControl instanceof Composite) {
					obfuscatedImage((Composite) childControl, image);
				}
			}
		}

		return super.obfuscatedImage(image);
	}

	private void obfuscatedImage(Composite c, Image image) {
		if (c == null || c.isDisposed() || !c.isVisible()) {
			return;
		}
		Control[] children = c.getChildren();
		for (Control childControl : children) {
			if (!childControl.isVisible()) {
				continue;
			}
			ObfuscateImage oi = (ObfuscateImage) childControl.getData("ObfuscateImage");
			if (oi != null) {
				oi.obfuscatedImage(image);
				continue;
			}
			if (childControl instanceof Composite) {
				obfuscatedImage((Composite) childControl, image);
			}
		}
	}
}
