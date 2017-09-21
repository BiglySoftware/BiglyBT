/* *
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.skin.*;

/**
 * @author TuxPaper
 */
public class SBC_DashboardView
	extends SkinView
	implements UIUpdatable
{

	private static final String UI_NAME = "Dashboard";

	@Override
	public void updateUI() {
	}

	@Override
	public String getUpdateUIName() {
		return UI_NAME;
	}

	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {

		return null;
	}


	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {

		return super.skinObjectHidden(skinObject, params);
	}

	@Override
	public Object skinObjectShown(SWTSkinObject skinObject, Object params) {
		
		SWTSkinObject so_area = getSkinObject("dashboard-area");

		Composite area = (Composite)so_area.getControl();
		
		area.setLayout( new GridLayout());
		
		Utils.disposeComposite( area, false );
		
		Label lab = new Label( area, SWT.NULL );
		lab.setLayoutData( new GridData(GridData.FILL_BOTH ));
		
		lab.setText( "What to do!!!!");
		
		area.getParent().layout( true, true );
		
		return( super.skinObjectShown(skinObject, params));
	}

	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		return super.skinObjectDestroyed(skinObject, params);
	}
}
