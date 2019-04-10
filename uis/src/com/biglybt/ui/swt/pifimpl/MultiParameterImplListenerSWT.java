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

package com.biglybt.ui.swt.pifimpl;

import java.util.Map;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterImplListener;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.BaseSwtParameter;

public class MultiParameterImplListenerSWT
	implements ParameterImplListener
{

	private final Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam;

	public MultiParameterImplListenerSWT(
			Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam) {
		this.mapParamToSwtParam = mapParamToSwtParam;
	}

	@Override
	public void enabledChanged(final ParameterImpl p) {
		BaseSwtParameter parameter = mapParamToSwtParam.get(p);

		if (parameter == null) {
			return;
		}

		if (parameter.isDisposed()) {
			// lazy tidyup
			p.removeImplListener(this);
			return;
		}

		parameter.setEnabled(p.isEnabled());
	}

	@Override
	public void labelChanged(final ParameterImpl p, final String text,
			final boolean bIsKey) {
		BaseSwtParameter parameter = mapParamToSwtParam.get(p);

		if (parameter == null) {
			return;
		}

		if (parameter.isDisposed()) {
			// lazy tidyup
			p.removeImplListener(this);
			return;
		}

		parameter.refreshControl();

		Control lbl = parameter.getRelatedControl();
		if (!(lbl instanceof Label)) {
			return;
		}

		Utils.execSWTThread(() -> {
			if (bIsKey) {
				Messages.setLanguageText(lbl, text);
			} else {
				lbl.setData("");
				((Label) lbl).setText(text);
			}
			lbl.getParent().layout(true);
		});
	}

	@Override
	public void refreshControl(ParameterImpl p) {
		BaseSwtParameter parameter = mapParamToSwtParam.get(p);

		if (parameter == null) {
			return;
		}
		parameter.refreshControl();
	}

}
