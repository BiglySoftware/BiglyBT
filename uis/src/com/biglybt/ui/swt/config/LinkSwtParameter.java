/*
 * Created on 17-Jun-2004
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.ui.swt.config;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.ui.config.ActionParameterImpl;
import com.biglybt.pifimpl.local.ui.config.HyperlinkParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.HyperlinkParameter;

/**
 * Same as {@link ButtonSwtParameter} but as a link
 */
public class LinkSwtParameter
	extends BaseSwtParameter<LinkSwtParameter, Object>
{
	Label link_label;

	private String url;

	public LinkSwtParameter(Composite parent,
			HyperlinkParameterImpl pluginParam) {
		this(parent, pluginParam.getLinkTextKey(), pluginParam.getLabelKey(),
				pluginParam.getHyperlink());
		setPluginParameter(pluginParam);
	}

	public LinkSwtParameter(Composite parent, ActionParameterImpl pluginParam) {
		this(parent, pluginParam.getActionResource(), pluginParam.getLabelKey(),
				null);
		setPluginParameter(pluginParam);
		addChangeListener(p -> {
			try {
				((ActionParameterImpl) this.pluginParam).parameterChanged("");
			} catch (Throwable t) {
				Debug.out(t);
			}
		});
	}

	public LinkSwtParameter(Composite composite, String name_resource,
			String labelKey, String url) {
		super(name_resource);

		if (labelKey != null) {
			Label label = new Label(composite, SWT.NONE);
			Messages.setLanguageText(label, labelKey);
			setRelatedControl(label);
		}

		link_label = new Label(composite, SWT.WRAP);
		setMainControl(link_label);
		this.url = url;
		Messages.setLanguageText(link_label, name_resource);
		Display display = link_label.getDisplay();
		link_label.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
		Utils.setLinkForeground( link_label );
		link_label.addListener(SWT.MouseUp, e -> {
			if (e.button == 1) {
				triggerChangeListeners(false);
			} else {
				e.doit = true;
			}
		});
		link_label.addListener(SWT.MouseDoubleClick,
				e -> triggerChangeListeners(false));
		link_label.setData(url);
		ClipboardCopy.addCopyToClipMenu(link_label);

		if (doGridData(composite) && labelKey == null) {
			GridLayout parentLayout = (GridLayout) composite.getLayout();
			if (parentLayout.numColumns >= 2) {
				GridData gridData = Utils.getWrappableLabelGridData(2,
						GridData.FILL_HORIZONTAL);
				link_label.setLayoutData(gridData);
			} else {
				link_label.setLayoutData(new GridData());
			}
		}

		if (url != null) {
			Utils.setTT(link_label, url);
		}
	}

	@Override
	protected void triggerSubClassChangeListeners() {
		if (url != null) {
			Utils.launch(url);
		}
	}

	@Override
	public void refreshControl() {
		super.refreshControl();

		if (pluginParam == null) {
			return;
		}

		Utils.execSWTThread(() -> {
			if (link_label.isDisposed()) {
				return;
			}
			if (pluginParam instanceof HyperlinkParameterImpl) {
				url = ((HyperlinkParameter) pluginParam).getHyperlink();
				Utils.setTT(link_label, url);

				Messages.setLanguageText(link_label,
						((HyperlinkParameterImpl) pluginParam).getLinkTextKey());
			} else if (pluginParam instanceof ActionParameter) {
				Messages.setLanguageText(link_label,
						((ActionParameter) pluginParam).getActionResource());
			}
		});
	}
}