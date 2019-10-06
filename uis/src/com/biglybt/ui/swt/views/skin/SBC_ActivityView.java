/*
 * Created on Jul 2, 2008
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

package com.biglybt.ui.swt.views.skin;

import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.ui.swt.Utils;

import com.biglybt.activities.ActivitiesEntry;
import com.biglybt.activities.ActivitiesManager;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility;
import com.biglybt.ui.swt.skin.SWTSkinObject;

/**
 * @author TuxPaper
 * @created Jul 2, 2008
 *
 */
public class SBC_ActivityView
	extends SkinView
{
	public final static String ID = "activity-list";

	public final static int MODE_BIGTABLE = -1;

	public final static int MODE_SMALLTABLE = 0;

	public final static int MODE_DEFAULT = MODE_SMALLTABLE;

	private final static String[] modeViewIDs = {
		//SkinConstants.VIEWID_SIDEBAR_ACTIVITY_BIG,
		SkinConstants.VIEWID_SIDEBAR_ACTIVITY_SMALL,
	};

	private final static String[] modeIDs = {
		//"activity.table.big",
		"activity.table.small",
	};

	private int viewMode = -1;

	private SWTSkinButtonUtility btnSmallTable;

	private SWTSkinButtonUtility btnBigTable;

	private SWTSkinObject soListArea;

	// @see SkinView#showSupport(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {

		soListArea = getSkinObject(ID + "-area");

		SWTSkinObject so;
		so = getSkinObject(ID + "-button-smalltable");
		if (so != null) {
			btnSmallTable = new SWTSkinButtonUtility(so);
			btnSmallTable.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					setViewMode(MODE_SMALLTABLE, true);
				}
			});
		}

		so = getSkinObject(ID + "-button-bigtable");
		if (so != null) {
			btnBigTable = new SWTSkinButtonUtility(so);
			btnBigTable.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					setViewMode(MODE_BIGTABLE, true);
				}
			});
		}

		so = getSkinObject(ID + "-button-right");
		if (so != null) {
			so.setVisible(true);
			SWTSkinButtonUtility btnReadAll = new SWTSkinButtonUtility(so);
			btnReadAll.setTextID("v3.activity.button.readall");
			btnReadAll.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					List<ActivitiesEntry> allEntries = ActivitiesManager.getAllEntries();
					for (ActivitiesEntry entry: allEntries ){
						entry.setRead(true);
					}
				}
			});
		}

		setViewMode(COConfigurationManager.getIntParameter(ID + ".viewmode",
				MODE_DEFAULT), false);

		return null;
	}

	@Override
	public Object skinObjectShown(SWTSkinObject skinObject, Object params) {
		ActivitiesManager.pullActivitiesNow(0, "shown", true);
		return super.skinObjectShown(skinObject, params);
	}

	public int getViewMode() {
		return viewMode;
	}

	public void setViewMode(int viewMode, boolean save) {
		if (viewMode >= modeViewIDs.length || viewMode < 0) {
			viewMode = MODE_DEFAULT;
		}

		if (viewMode == this.viewMode) {
			return;
		}

		int oldViewMode = this.viewMode;

		this.viewMode = viewMode;

		soListArea = getSkinObject(ID + "-area");

		soListArea.getControl().setData("ViewMode", new Long(viewMode));

		if (oldViewMode >= 0 && oldViewMode < modeViewIDs.length) {
			SWTSkinObject soOldViewArea = getSkinObject(modeViewIDs[oldViewMode]);
			if (soOldViewArea != null) {
				soOldViewArea.setVisible(false);
			}
		}

		SWTSkinObject soViewArea = getSkinObject(modeViewIDs[viewMode]);
		if (soViewArea == null) {
			soViewArea = skin.createSkinObject(modeIDs[viewMode], modeIDs[viewMode],
					soListArea);
			skin.layout();
			soViewArea.setVisible(true);
			soViewArea.getControl().setLayoutData(Utils.getFilledFormData());
		} else {
			soViewArea.setVisible(true);
		}

		if (btnSmallTable != null) {
			btnSmallTable.getSkinObject().switchSuffix(
					viewMode == MODE_SMALLTABLE ? "-selected" : "");
		}
		if (btnBigTable != null) {
			btnBigTable.getSkinObject().switchSuffix(
					viewMode == MODE_BIGTABLE ? "-selected" : "");
		}

		if (save) {
			COConfigurationManager.setParameter(ID + ".viewmode", viewMode);
		}
	}

	protected void removeSelected() {
		SBC_ActivityTableView tv = (SBC_ActivityTableView) SkinViewManager.getBySkinObjectID(modeIDs[viewMode]);
		if (tv != null) {
			tv.removeSelected();
		}
	}

	public int getNumSelected() {
		SBC_ActivityTableView tv = (SBC_ActivityTableView) SkinViewManager.getBySkinObjectID(modeIDs[viewMode]);
		if (tv != null) {
			return tv.getView().getSelectedRowsSize();
		}
		return 0;
	}
}
