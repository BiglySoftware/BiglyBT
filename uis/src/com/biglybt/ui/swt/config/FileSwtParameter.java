/*
 * Created on 20-Nov-2006
 * Created by Allan Crooks
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pifimpl.local.ui.config.FileParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SWT Parameter representing a File (String) value. 
 * Displays Textbox and browse button.
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 * <p/>
 * Note: Currently doesn't validate if file is valid or exists.  This allows
 * for URL entries.
 */
public class FileSwtParameter
	extends BaseSwtStringParameter<FileSwtParameter>
{
	private final Button browse;

	private final Composite area;

	protected String keyDialogTitle = null;

	protected String[] extension_list;

	private String filenameHint;

	public FileSwtParameter(Composite parent, FileParameterImpl param) {
		this(parent, param.getConfigKeyName(), param.getLabelKey(),
				param.getFileExtensions(), null);
		setFilenameHint(param.getFileNameHint());
		keyDialogTitle = param.getKeyDialogTitle();
		String hintKey = param.getHintKey();
		if (hintKey != null) {
			setHintKey(hintKey);
		}
	}

	/**
	 * Make a File selecting ui
	 * <p/>
	 * When parent is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Composite is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the text shown before the color button
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public FileSwtParameter(Composite composite, String paramID, String labelKey,
			String[] extension_list,
			SwtParameterValueProcessor<FileSwtParameter, String> valueProcessor) {
		super(new Composite(composite, SWT.NULL), paramID, labelKey, null,
				valueProcessor);
		this.extension_list = extension_list;

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

	@Override
	public Control[] getControls() {
		List<Control> list = new ArrayList<>(Arrays.asList(super.getControls()));
		list.add(browse);
		list.add(area);
		return list.toArray(new Control[0]);
	}

	public void setFilenameHint(String filenameHint) {
		this.filenameHint = filenameHint;
	}

	private String openDialog(Shell shell, String old_value) {
		FileDialog dialog = new FileDialog(shell, SWT.APPLICATION_MODAL);
		if (keyDialogTitle != null) {
			dialog.setText(MessageText.getString(keyDialogTitle));
		}
		if (filenameHint != null) {
			dialog.setFileName(filenameHint);
		}
		dialog.setFilterPath(old_value);
		if (this.extension_list != null) {
			dialog.setFilterExtensions(this.extension_list);
		}
		return dialog.open();
	}
}
