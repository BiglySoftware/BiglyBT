/*
 * Created on 9 juil. 2003
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

import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pifimpl.local.ui.config.InfoParameterImpl;
import com.biglybt.pifimpl.local.ui.config.LabelParameterImpl;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.pif.ui.config.InfoParameter;
import com.biglybt.pif.ui.config.LabelParameter;

/**
 * Displays a message stored in {@link COConfigurationManager},
 * or just a label,
 * or a label and a value
 *
 * @author parg
 */
public class InfoSwtParameter
	extends BaseSwtParameter<InfoSwtParameter, String>
{
	final private Control control;

	private String labelKey;

	private String infoVal;

	public InfoSwtParameter(Composite parent, LabelParameterImpl labelParameter) {
		this(parent, null, null,
				MessageText.getString(labelParameter.getLabelKey()), false);
		setPluginParameter(labelParameter);
	}

	public InfoSwtParameter(Composite parent, InfoParameterImpl infoParameter) {
		this(parent, infoParameter.getConfigKeyName(), infoParameter.getLabelKey(),
				infoParameter.getValue(), infoParameter.isTextSelectable());
		setPluginParameter(infoParameter);
		infoParameter.addListener((n)->{
			setInfoVal(infoParameter.getValue());
		});
	}

	/**
	 * If configKey != null:<br>
	 *   [label][config value]<br>
	 * If config == null:<br>
	 *   [label][infoVal]<br>
	 *
	 * @param parent
	 * @param configID
	 * @param labelKey
	 * @param infoVal
	 */
	public InfoSwtParameter(Composite parent, String configID, String labelKey,
			String infoVal, boolean isSelectable) {
		super(configID);
		this.labelKey = labelKey;

		this.infoVal = infoVal;
		boolean doLabel = labelKey != null && (configID != null || infoVal != null);
		if (doLabel) {
			createStandardLabel(parent, labelKey);
		}

		if (isSelectable) {
			if (infoVal != null && infoVal.toLowerCase( Locale.US ).contains("<a ")) {
				StyledText textWidget = new StyledText(parent, SWT.READ_ONLY | SWT.MULTI);
				control = textWidget;
				textWidget.setTabs(8);
				textWidget.setEditable( false );
				textWidget.setBackground( parent.getBackground());
			}else{
				Text textWidget = new Text(parent, SWT.READ_ONLY | SWT.MULTI);
				control = textWidget;
				textWidget.setTabs(8);
			}
		} else {
			if (infoVal != null && infoVal.toLowerCase( Locale.US ).contains("<a ")) {
				Link link = new Link(parent, SWT.WRAP);
				control = link;
				link.addListener(SWT.Selection, event -> Utils.launch(event.text));
			} else {
				control = new Label(parent, SWT.WRAP);
				
				ClipboardCopy.addCopyToClipMenu(control);
			};
		}
		setMainControl(control);

		if (doGridData(parent)) {
			if (isSelectable) {
				GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = doLabel ? 1 : 2;
				control.setLayoutData(gridData);
			} else {
				GridLayout parentLayout = (GridLayout) parent.getLayout();
				control.setLayoutData(parentLayout.numColumns > 2
						? Utils.getHSpanGridData(doLabel ? 1 : 2, 0)
						: Utils.getWrappableLabelGridData(doLabel ? 1 : 2,
								GridData.FILL_HORIZONTAL));
			}
		}

		if (configID != null) {
			setConfigValueProcessor(String.class);
		} else {
			refreshControl();
		}
	}

	@Override
	protected boolean doGridData(Composite composite) {
		Layout layout = composite.getLayout();
		return (layout instanceof GridLayout);
	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		Utils.execSWTThread(() -> {
			if (control.isDisposed()) {
				return;
			}
			if (pluginParam instanceof LabelParameter) {
				infoVal = MessageText.getString(pluginParam.getLabelKey());
			} else if (pluginParam instanceof InfoParameter) {
				infoVal = ((InfoParameter) pluginParam).getValue();
				labelKey = pluginParam.getLabelKey();
			}
			String value = getValue();
			if (value == null) {
				return;
			}
			if (control instanceof Label) {
				((Label) control).setText(value);
			} else if (control instanceof Link) {
				((Link) control).setText(value);
			} else if (control instanceof Text) {
				((Text) control).setText(value);
			} else if (control instanceof StyledText) {
				
				Utils.setTextWithURLs((StyledText)control, value, true );
			}

			if (value.contains("\n") && !(control instanceof Text)) {
				Utils.relayoutUp(control.getParent());
			}
		});
	}

	@Override
	public String getValue() {
		return paramID == null
				? infoVal == null ? MessageText.getString(labelKey) : infoVal
				: super.getValue();
	}

	private void setInfoVal(String infoVal) {
		this.infoVal = infoVal;
		refreshControl();
	}
}
