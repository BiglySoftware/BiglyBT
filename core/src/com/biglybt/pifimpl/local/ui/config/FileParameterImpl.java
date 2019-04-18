/*
 * Created : Nov 21, 2003
 * By      : epall
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.ui.config;


/**
 * @author epall
 *
 */

import com.biglybt.core.config.COConfigurationManager;

import com.biglybt.pif.ui.config.FileParameter;


public class FileParameterImpl
	extends ParameterImpl
	implements FileParameter
{
	private String fileNameHint;
	private String[] file_extensions;
	private String keyDialogTitle;
	private String hintKey;

	public FileParameterImpl(String key, String labelKey,
			String... file_extensions) {
		super(key, labelKey);
		this.fileNameHint = fileNameHint;

		this.file_extensions = file_extensions;
	}

	@Override
	public String getValue() {
		return COConfigurationManager.getStringParameter( getConfigKeyName());
	}

	@Override
	public Object getValueObject() {
		return getValue();
	}

	public String getFileNameHint() {
		return fileNameHint;
	}

	public void setFileNameHint(String fileNameHint) {
		this.fileNameHint = fileNameHint;
		refreshControl();
	}

	public String[] getFileExtensions() {
		return this.file_extensions;
	}

	@Override
	public void setDialogTitleKey(String key) {
		keyDialogTitle = key;
	}

	public String getKeyDialogTitle() {
		return keyDialogTitle;
	}

	public String getFileName() {
		return getValue();
	}

	public void setFileName(String filename) {
		COConfigurationManager.setParameter(getConfigKeyName(), filename);
	}


	@Override
	public String getHintKey() {
		return hintKey;
	}

	@Override
	public void setHintKey(String hintKey) {
		this.hintKey = hintKey;
		refreshControl();
	}

	@Override
	public void setHintText(String text) {
		this.hintKey = text == null ? null : "!" + text + "!";
		refreshControl();
	}
}
