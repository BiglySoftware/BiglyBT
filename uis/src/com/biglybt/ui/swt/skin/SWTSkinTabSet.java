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
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

/**
 * @author TuxPaper
 * @created Jun 8, 2006
 */
public class SWTSkinTabSet
{
	private Listener tabMouseListener;

	private final SWTSkin skin;

	private final String sID;

	private SWTSkinObjectTab activeTab;

	/** List of SWTSKinObjectTab */
	private List tabs;

	private ArrayList listeners = new ArrayList();

	// XXX Do we need to pass in SkinProperties in case of cloning?
	public SWTSkinTabSet(SWTSkin skin, String sID) {
		this.sID = sID;
		this.skin = skin;
		tabs = new ArrayList();
	}

	public void addTab(final SWTSkinObjectTab tab) {
		tabs.add(tab);
		tab.setTabset(this);

		//System.out.println("AddTab for " + sID + ": " + tab.getSkinObjectID());
		addMouseListener(tab, tab.getControl());

		skin.addListener(new SWTSkinLayoutCompleteListener() {
			@Override
			public void skinLayoutCompleted() {
				setTabVisible(tab, activeTab == tab, null);
			}
		});
	}

	public SWTSkinObjectTab getActiveTab() {
		return activeTab;
	}

	public SWTSkinObjectTab[] getTabs() {
		return (SWTSkinObjectTab[]) tabs.toArray(new SWTSkinObjectTab[0]);
	}

	public SWTSkinObjectTab getTabByID(String sID) {
		for (int i = 0; i < tabs.size(); i++) {
			SWTSkinObjectTab tab = (SWTSkinObjectTab) tabs.get(i);
			String sTabID = tab.getSkinObjectID();

			if (sTabID.equals(sID)) {
				return tab;
			}
		}

		return null;
	}

	public SWTSkinObjectTab getTab(String sViewID) {
		for (int i = 0; i < tabs.size(); i++) {
			SWTSkinObjectTab tab = (SWTSkinObjectTab) tabs.get(i);
			String sTabViewID = tab.getViewID();

			if (sTabViewID.equals(sViewID)) {
				return tab;
			}
		}

		return null;
	}

	public boolean setActiveTab(String viewID) {
		SWTSkinObject skinObject = skin.getSkinObject(viewID);

		if (skinObject == null) {
			return false;
		}

		return skin.activateTab(skinObject) != null;
	}


	public void setActiveTab(final SWTSkinObjectTab newTab) {
		setActiveTab(newTab, false);
	}

	private void setActiveTab(final SWTSkinObjectTab newTab, final boolean evenIfSame) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				Composite shell = skin.getShell();
				Cursor cursor = shell.getCursor();
				try {
					shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
					swtSetActiveTab(newTab, evenIfSame);
				} finally {
					shell.setCursor(cursor);
				}
			}
		});
	}

	protected void swtSetActiveTab(SWTSkinObjectTab newTab, boolean evenIfSame) {
		// Don't exit early if we are already on tab.  We want to be notified if
		// the user clicks on the tab again (for example, for page refreshing)
		if (!tabs.contains(newTab)) {
			System.err.println("Not contain in " + sID + ": " + newTab);
			return;
		}

		String sOldID = activeTab == null ? "" : activeTab.getSkinObjectID();

		if (newTab != activeTab) {
			SWTSkinObject[] objects = setTabVisible(newTab, true, null);
			if (activeTab != null) {
				setTabVisible(activeTab, false, objects);
			}

			activeTab = newTab;
		} else if (!evenIfSame) {
			return;
		}

		String sConfigID = activeTab.getConfigID();
		String sNewID = activeTab.getSkinObjectID();

		SWTSkinObject parent = skin.getSkinObject(activeTab.getProperties().getStringValue(sConfigID
				+ ".activate"));
		if (parent != null) {
			parent.getControl().setFocus();
		}

		triggerChangeListener(sOldID, sNewID);
	}

	/**
	 * @param oldID
	 * @param newID
	 */
	private void triggerChangeListener(String oldID, String newID) {
		for (Iterator iter = listeners.iterator(); iter.hasNext();) {
			try {
				SWTSkinTabSetListener l = (SWTSkinTabSetListener) iter.next();
				l.tabChanged(this, oldID, newID);
			} catch (Exception e) {
				Debug.printStackTrace(e);
			}
		}
	}

	private void addMouseListener(SWTSkinObject tab, Control control) {
		if (tabMouseListener == null) {
			tabMouseListener = new Listener() {
				boolean bDownPressed = false;

				@Override
				public void handleEvent(Event event) {
					if (event.type == SWT.MouseDown) {
						bDownPressed = true;
						return;
					} else if (!bDownPressed) {
						return;
					}

					bDownPressed = false;

					Control control = (Control) event.widget;
					setActiveTab((SWTSkinObjectTab) control.getData("Tab"), true);
				}
			};
		}

		control.setData("Tab", tab);
		control.addListener(SWT.MouseUp, tabMouseListener);
		control.addListener(SWT.MouseDown, tabMouseListener);

		if (control instanceof Composite) {
			Control[] children = ((Composite) control).getChildren();
			for (int i = 0; i < children.length; i++) {
				addMouseListener(tab, children[i]);
			}
		}
	}

	private SWTSkinObject[] setTabVisible(SWTSkinObjectTab tab, boolean visible,
			SWTSkinObject[] skipObjects) {
		String sSkinID = tab.getSkinObjectID();

		SWTSkinObject soTabContent = skin.getSkinObjectByID(sSkinID);
		if (soTabContent == null) {
			return null;
		}

		String suffix = visible ? "-selected" : "";

		soTabContent.switchSuffix(suffix, 1, true);

		SWTSkinObject[] activeWidgets = tab.getActiveWidgets(visible);

		for (int i = 0; i < activeWidgets.length; i++) {
			SWTSkinObject skinObject = activeWidgets[i];
			boolean ok = true;
			if (skipObjects != null) {
				for (int j = 0; j < skipObjects.length; j++) {
					if (skinObject.equals(skipObjects[j])) {
						ok = false;
						break;
					}
				}
			}

			if (ok) {
				if (visible) {
  				skinObject.setDefaultVisibility();
				} else {
					skinObject.setVisible(visible);
				}
				//System.out.println(((visible ? "show" : "hide") + " " + skinObject) + Debug.getCompressedStackTrace());
			}
		}
		tab.triggerListeners(SWTSkinObjectListener.EVENT_SELECT);

		return activeWidgets;
	}

	public void addListener(SWTSkinTabSetListener listener) {
		listeners.add(listener);
	}

	public String getID() {
		return sID;
	}

	protected static String[] getTemplateInfo(SWTSkin skin,
			SWTSkinObject skinObject, String sTemplateKey) {
		SWTSkinProperties skinProperties = skin.getSkinProperties();
		String sID = skinObject.getConfigID() + ".view.template." + sTemplateKey;
		return skinProperties.getStringArray(sID);
	}

	protected static String getTemplateID(SWTSkin skin, SWTSkinObject skinObject,
			String sTemplateKey) {
		String[] templateInfo = getTemplateInfo(skin, skinObject, sTemplateKey);
		if (templateInfo != null) {
			return templateInfo[0];
		}
		return null;
	}

	public static String getTabSetID(SWTSkin skin, SWTSkinObject skinObject,
			String sTemplateKey) {
		String[] templateInfo = getTemplateInfo(skin, skinObject, sTemplateKey);
		if (templateInfo != null && templateInfo.length > 1) {
			return templateInfo[1];
		}
		return null;
	}
}
