/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.skin;

import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectAdapter;
import com.biglybt.ui.swt.skin.SWTSkinObjectListener;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;

/**
 * Converts {@link SWTSkinObjectListener} events to method calls, and
 * ensures we only "show" (initialize) once.
 * <p>
 * Available SkinViews are managed by {@link SkinViewManager}
 *
 * @author TuxPaper
 * @created Sep 30, 2006
 *
 */
public abstract class SkinView
	extends SWTSkinObjectAdapter
{
	private boolean shownOnce;

	private boolean visible;

	protected SWTSkinObject soMain;

	protected SWTSkin skin;

	private boolean disposed = false;

	/**
	 *
	 */
	public SkinView() {
		shownOnce = false;

		if (this instanceof UIUpdatable) {
			UIUpdatable updateable = (UIUpdatable) this;
			try {
				UIUpdater updater = UIUpdaterSWT.getInstance();
				if (updater != null) {
					updater.addUpdater(updateable);
				}
			} catch ( Throwable e) {
				Debug.out(e);
			}
		}
	}

	/**
	 * @return the visible
	 */
	public boolean isVisible() {
		return visible;
	}

	// @see {SWTSkinObjectAdapter#skinObjectShown(SWTSkinObject, java.lang.Object)}
	@Override
	public Object skinObjectShown(SWTSkinObject skinObject, Object params) {
		setMainSkinObject(skinObject);

		visible = true;

		if (shownOnce) {
			return null;
		}

		shownOnce = true;
		try {
			return skinObjectInitialShow(skinObject, params);
		} catch (Exception e) {
			Debug.out(e);
		}
		return null;
	}

	// @see SWTSkinObjectAdapter#skinObjectHidden(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {
		visible = false;
		return super.skinObjectHidden(skinObject, params);
	}

	// @see SWTSkinObjectAdapter#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		disposed = true;

		SkinViewManager.remove(this);
		if (this instanceof UIUpdatable) {
			UIUpdatable updateable = (UIUpdatable) this;
			try {
				UIUpdater updater = UIUpdaterSWT.getInstance();
				if (updater != null) {
					updater.removeUpdater(updateable);
				}
			} catch (Exception e) {
				Debug.out(e);
			}
		}
		return null;
	}

	public boolean isDisposed() {
		return disposed;
	}

	/**
	 * @param skinObject
	 * @param params
	 * @return
	 */
	public abstract Object skinObjectInitialShow(SWTSkinObject skinObject, Object params);

	public SWTSkinObject getMainSkinObject() {
		return soMain;
	}

	@Override
	public Object skinObjectCreated(SWTSkinObject skinObject, Object params) {
		SkinViewManager.add(this);

		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();
		if (mdi != null) {
			MdiEntry entry = mdi.getEntryFromSkinObject(skinObject);
			if (entry != null && (this instanceof UIPluginViewToolBarListener)) {
				entry.addToolbarEnabler((UIPluginViewToolBarListener) this);
			}
		}
		return super.skinObjectCreated(skinObject, params);
	}

	final public void setMainSkinObject(SWTSkinObject main) {
		if (soMain != null) {
			return;
		}
		soMain = main;
		if (soMain != null) {
			skin = soMain.getSkin();
			soMain.setSkinView(this);
		}
	}

	final public SWTSkin getSkin() {
		return skin;
	}

	final public SWTSkinObject getSkinObject(String viewID) {
		return skin.getSkinObject(viewID, soMain);
	}
}
