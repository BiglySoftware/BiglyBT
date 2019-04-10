/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.ui.swt.config.actionperformer;

import com.biglybt.ui.swt.config.BooleanSwtParameter;
import com.biglybt.ui.swt.config.SwtParameter;

/**
 * Action performer which enables a group of controls, and disables another
 * group of controls, based on state of the {@link BooleanSwtParameter} it's
 * assigned to.
 *
 * @author Olivier
 *
 */
public class DualChangeSelectionActionPerformer
	implements IAdditionalActionPerformer<Boolean>
{

	final ChangeSelectionActionPerformer enabler;

	final ChangeSelectionActionPerformer disabler;

	public DualChangeSelectionActionPerformer(SwtParameter[] paramsToEnable,
			SwtParameter[] paramsToDisable) {
		if (paramsToEnable != null && paramsToEnable.length > 0) {
			enabler = new ChangeSelectionActionPerformer(paramsToEnable);
		} else {
			enabler = null;
		}
		if (paramsToDisable != null && paramsToDisable.length > 0) {
			disabler = new ChangeSelectionActionPerformer(paramsToDisable);
			disabler.setReverseSense(true);
		} else {
			disabler = null;
		}
	}

	@Override
	public void performAction() {
		if (enabler != null) {
			enabler.performAction();
		}
		if (disabler != null) {
			disabler.performAction();
		}
	}

	@Override
	public void valueChanged(Boolean newValue) {
		if (enabler != null) {
			enabler.valueChanged(newValue);
		}
		if (disabler != null) {
			disabler.valueChanged(newValue);
		}
	}

}
