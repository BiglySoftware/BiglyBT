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

import java.util.ArrayList;
import java.util.List;

import com.biglybt.ui.swt.config.BooleanSwtParameter;
import com.biglybt.ui.swt.config.SwtParameter;

/**
 * Action performer which enables a group of controls based on state of {@link BooleanSwtParameter}
 *
 * @author Olivier
 *
 */
@SuppressWarnings("rawtypes")
public class ChangeSelectionActionPerformer
	implements IAdditionalActionPerformer<Boolean>
{

	private boolean selected = false;

	private boolean reverse_sense = false;

	private SwtParameter[] params;

	public ChangeSelectionActionPerformer(SwtParameter... params) {
		this.params = params;
	}

	public void setReverseSense(boolean b) {
		reverse_sense = b;
	}

	@Override
	public void performAction() {
		boolean enabled = reverse_sense != selected;

		List<BooleanSwtParameter> listMoreTriggers = new ArrayList<>();
		for (SwtParameter param : params) {
			if (param == null || param.isDisposed()) {
				continue;
			}
			if (param instanceof BooleanSwtParameter) {
				BooleanSwtParameter bparam = (BooleanSwtParameter) param;
				listMoreTriggers.add(bparam);
			}
			param.setEnabled(enabled);
		}

		for (BooleanSwtParameter bparam : listMoreTriggers) {
			bparam.triggerActionPerformers();
		}
	}

	@Override
	public void valueChanged(Boolean newValue) {
		this.selected = newValue == null ? false : newValue;
	}

}
