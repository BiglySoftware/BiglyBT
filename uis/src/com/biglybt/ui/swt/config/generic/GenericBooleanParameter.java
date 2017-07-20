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
package com.biglybt.ui.swt.config.generic;

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.IAdditionalActionPerformer;

/**
 * @author Olivier
 *
 */
public class GenericBooleanParameter
{
	protected static final boolean DEBUG = false;

	GenericParameterAdapter adapter;

	String name;

	Button checkBox;

	Boolean defaultValue;

	List performers = new ArrayList();

	public GenericBooleanParameter(GenericParameterAdapter adapter,
			Composite composite, final String name) {
		this(adapter, composite, name, adapter.getBooleanValue(name), null, null);
	}

	public GenericBooleanParameter(GenericParameterAdapter adapter,
			Composite composite, final String name, String textKey) {
		this(adapter, composite, name, adapter.getBooleanValue(name), textKey, null);
	}

	public GenericBooleanParameter(GenericParameterAdapter adapter,
			Composite composite, final String name, Boolean defaultValue,
			String textKey) {
		this(adapter, composite, name, defaultValue, textKey, null);
	}

	public GenericBooleanParameter(GenericParameterAdapter adapter,
			Composite composite, final String name, Boolean defaultValue) {
		this(adapter, composite, name, defaultValue, null, null);
	}

	public GenericBooleanParameter(GenericParameterAdapter _adapter,
			Composite composite, final String _name, Boolean _defaultValue,
			String textKey, IAdditionalActionPerformer actionPerformer) {
		adapter = _adapter;
		name = _name;
		defaultValue = _defaultValue;
		if (actionPerformer != null) {
			performers.add(actionPerformer);
		}
		Boolean value = adapter.getBooleanValue(name, defaultValue);
		checkBox = new Button(composite, SWT.CHECK);
		if (textKey != null)
			Messages.setLanguageText(checkBox, textKey);
		if (value != null) {
			checkBox.setGrayed(false);
			checkBox.setSelection(value);
		} else {
			checkBox.setGrayed(true);
			checkBox.setSelection(true);
		}

		checkBox.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				setSelected(checkBox.getSelection(), true);
			}
		});
	}

	public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
		checkBox.setLayoutData(layoutData);
	}

	public void setAdditionalActionPerformer(
			IAdditionalActionPerformer actionPerformer) {
		performers.add(actionPerformer);
		Boolean selected = isSelected();
		if (selected != null) {
			actionPerformer.setSelected(selected);
		}
		actionPerformer.performAction();
	}

	public Control getControl() {
		return checkBox;
	}

	public String getName() {
		return name;
	}

	public void setName(String newName) {
		name = newName;
	}

	public Boolean isSelected() {
		return adapter.getBooleanValue(name);
	}

	public void
	setEnabled(
		final boolean	enabled )
	{
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (!checkBox.isDisposed()) {
					checkBox.setEnabled(enabled);
				}
			}
		});
	}

	public void setSelected(final boolean selected) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (!checkBox.isDisposed()) {
					if (checkBox.getSelection() != selected) {
						if (DEBUG) {
							debug("bool.setSelection(" + selected + ")");
						}
						checkBox.setSelection(selected);
					}
					if (DEBUG) {
						debug("setBooleanValue to " + checkBox.getSelection()
								+ " via setValue(int)");
					}
					adapter.setBooleanValue(name, checkBox.getSelection());
				} else {
					adapter.setBooleanValue(name, selected);
				}

				if (performers.size() > 0) {

					for (int i = 0; i < performers.size(); i++) {

						IAdditionalActionPerformer performer = (IAdditionalActionPerformer) performers.get(i);

						performer.setSelected(selected);

						performer.performAction();
					}
				}

				adapter.informChanged(false);
			}
		});
	}

	protected void setSelected(final boolean selected, boolean force) {
		if (force) {
			setSelected(selected);
		} else {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (checkBox.getSelection() != selected) {
						checkBox.setSelection(selected);
					}
				}
			});
		}
	}

	public void refresh() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				Boolean selected = isSelected();
				if (selected == null) {
					checkBox.setGrayed(true);
					checkBox.setSelection(true);
				} else {
					checkBox.setGrayed(false);
  				if (checkBox.getSelection() != selected) {
  					checkBox.setSelection(selected);
  				}
				}
			}
		});
	}

	private void debug(String string) {
		System.out.println("[GenericBooleanParameter:" + name + "] " + string);
	}
}
