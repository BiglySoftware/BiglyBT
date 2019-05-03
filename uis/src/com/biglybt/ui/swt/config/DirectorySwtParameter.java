/*
 * Created on 08-Jun-2004
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pifimpl.local.ui.config.DirectoryParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

/**
 * SWT Parameter representing a Directory (String) value. 
 * Displays Textbox and browse button.
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 * <p/>
 * Note: Currently doesn't validate if directory is valid or exists
 */
public class DirectorySwtParameter
	extends BaseSwtStringParameter<DirectorySwtParameter>
{
	private final Button browse;

	private final Composite area;

	protected String keyDialogTitle = null;

	protected String keyDialogMessage = null;

	public DirectorySwtParameter(Composite composite,
			DirectoryParameterImpl param) {
		this(composite, param.getConfigKeyName(), param.getLabelKey(), null);
		keyDialogTitle = param.getKeyDialogTitle();
		keyDialogMessage = param.getKeyDialogMessage();
	}

	/**
	 * Make a directory selecting ui
	 * <p/>
	 * When parent is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Composite is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the text shown before the color button
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public DirectorySwtParameter(Composite composite, String paramID,
			String labelKey,
			SwtParameterValueProcessor<DirectorySwtParameter, String> valueProcessor) {
		super(new Composite(composite, SWT.NULL), paramID, labelKey, null,
				valueProcessor);

		area = getMainControl().getParent();
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		area.setLayout(layout);

		if (doGridData(composite)) {
			GridData gridData = new GridData(
					GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			area.setLayoutData(gridData);

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.widthHint = 150;
			inputField.setLayoutData(gridData);
		}

		browse = new Button(area, SWT.PUSH);
		ImageLoader.getInstance().setButtonImage(browse, "openFolderButton");
		Utils.setTT(browse, MessageText.getString("Button.browse"));

		browse.addListener(SWT.Selection, event -> {
			String path = openDialog(composite.getShell(), getValue());
			if (path != null) {
				setValue(path);
			}
		});
	}

	@Override
	public void setLayoutData(Object layoutData) {
		// don't set layout data
	}

	@Override
	public Control[] getControls() {
		List<Control> list = new ArrayList<>(Arrays.asList(super.getControls()));
		list.add(browse);
		list.add(area);
		return list.toArray(new Control[0]);
	}

	@Override
	protected void addLabelContextMenus(Control curControl, Menu menu) {
		super.addLabelContextMenus(curControl, menu);
		String value = getValue();
		if (value == null || value.isEmpty()) {
			return;
		}
		File file = new File(value);
		if (file.exists()
				|| (file.getParentFile() != null && file.getParentFile().exists())) {
			MenuItem itemShowInExplorer = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemShowInExplorer,
					"MyTorrentsView.menu.explore");
			itemShowInExplorer.addListener(SWT.Selection, event -> {
				String curVal = getValue();
				if (curVal != null && !curVal.isEmpty()) {
					ManagerUtils.open(new File(curVal));
				}
			});
		}
	}

	private String openDialog(Shell shell, String old_value) {
		DirectoryDialog dialog = new DirectoryDialog(shell, SWT.APPLICATION_MODAL);
		if (keyDialogMessage != null) {
			dialog.setMessage(MessageText.getString(keyDialogMessage));
		}
		if (keyDialogTitle != null) {
			dialog.setText(MessageText.getString(keyDialogTitle));
		}
		dialog.setFilterPath(old_value);
		return dialog.open();
	}

	public void setKeyDialogTitle(String keyDialogTitle) {
		this.keyDialogTitle = keyDialogTitle;
	}

	public void setKeyDialogMessage(String keyDialogMessage) {
		this.keyDialogMessage = keyDialogMessage;
	}
}
