/*
 * Created on 8 september 2003
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

import java.security.MessageDigest;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SHA1Hasher;
import com.biglybt.pifimpl.local.ui.config.PasswordParameterImpl;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.utils.FontUtils;

import com.biglybt.pif.ui.config.PasswordParameter;

/**
 * @author Olivier
 *
 */
public class PasswordSwtParameter
	extends BaseSwtParameter<PasswordSwtParameter, byte[]>
{
	Text inputField;

	private final int encoding;

	/**
	 * Creates a {@link PasswordSwtParameter} with SHA1 encoding
	 */
	public PasswordSwtParameter(Composite composite, String configID,
			String labelKey) {
		this(composite, configID, labelKey, PasswordParameter.ET_SHA1);
	}

	/**
	 * @param encoding See {@link PasswordParameter}
	 */
	public PasswordSwtParameter(Composite parent, String configID,
			String labelKey, int encoding) {
		super(configID);
		if (configID != null) {
			setConfigValueProcessor(byte[].class);
		}
		this.encoding = encoding;

		createStandardLabel(parent, labelKey);

		inputField = new Text(parent, SWT.BORDER);
		setMainControl(inputField);
		inputField.setEchoChar('*');
		byte[] value = getByteArrayValue();
		inputField.setMessage(value.length > 0
				? MessageText.getString("ConfigView.password.isset") : "");

		if (doGridData(parent)) {
			GridData gridData = new GridData();
			gridData.widthHint = 150;
			inputField.setLayoutData(gridData);
		}

		// don't inputField.setText here, we don't need to show ***, we want to show message
		inputField.addListener(SWT.Modify, event -> {
			try {
				String password_string = inputField.getText();

				byte[] password = password_string.getBytes();
				byte[] encoded;
				if (password.length > 0) {
					switch (encoding) {
						case PasswordParameter.ET_PLAIN:

							encoded = password;

							break;
						case PasswordParameter.ET_SHA1:

							SHA1Hasher hasher = new SHA1Hasher();

							encoded = hasher.calculateHash(password);

							break;
						default:

							// newly added, might as well go for UTF-8

							encoded = MessageDigest.getInstance("md5").digest(
									password_string.getBytes("UTF-8"));
							break;
					}
				} else {
					encoded = password;
				}

				setValue(encoded);
			} catch (Exception e) {
				Debug.printStackTrace(e);
			}
		});

		if (doGridData(parent)) {
			GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.widthHint = 150;
			inputField.setLayoutData(gridData);
		}
	}

	public PasswordSwtParameter(Composite parent, PasswordParameterImpl param) {
		this(parent, param.getConfigKeyName(), param.getLabelKey(),
				param.getEncodingType());
		setPluginParameter(param);
		int characters = param.getWidthInCharacters();
		if (characters > 0) {
			setWidthInCharacters(characters);
		}
	}

	private byte[] getByteArrayValue() {
		if (valueProcessor == null) {
			return "".getBytes();
		}
		byte[] value = valueProcessor.getValue(this);
		return value == null ? "".getBytes() : value;
	}

	private void setUiValue(final String value) {
		Utils.execSWTThread(() -> {
			if (inputField.isDisposed()) {
				return;
			}

			inputField.setMessage(value.length() > 0
					? MessageText.getString("ConfigView.password.isset") : "");
			if (!inputField.getText().equals(value)) {
				inputField.setText(value);
			}
		});
	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		if (encoding == PasswordParameter.ET_PLAIN) {
			byte[] value = getValue();
			if (value == null) {
				return;
			}
			setUiValue(new String(value, Constants.UTF_8));
		}
	}

	@Override
	public byte[] getValue() {
		// called by SwtConfigParameterValueProcessor via BaseSwtParameter.informChanged
		if (encoding == PasswordParameter.ET_PLAIN) {
			if (valueProcessor != null) {
				return valueProcessor.getValue(this);
			}
		}
		return null;
	}

	public void setWidthInCharacters(int i) {
		Object data = inputField.getLayoutData();
		if (data instanceof GridData) {
			((GridData) data).widthHint = (int) Math.ceil(
					i * FontUtils.getCharacterWidth(inputField.getFont()));
			inputField.setLayoutData(data);
		}
	}
}
