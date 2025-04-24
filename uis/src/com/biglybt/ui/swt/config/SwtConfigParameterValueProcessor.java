/*
 * Created on 16-Jan-2006
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

import java.util.Arrays;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.Debug;

/**
 * A {@link SwtParameterValueProcessor} that stores/retrieves one setting from
 * {@link COConfigurationManager}
 */
public class SwtConfigParameterValueProcessor<PARAMTYPE extends SwtParameter<VALUETYPE>, VALUETYPE>
	implements SwtParameterValueProcessor<PARAMTYPE, VALUETYPE>, ParameterListener
{

	private static final int CHANGINGCOUNT_BREAKER = 5;

	private final Class<VALUETYPE> valueType;

	@SuppressWarnings("rawtypes")
	private final SwtParameter owner;

	private int changingCount = 0;

	private boolean changedExternally = false;

	private static final Class<?>[] VALID_CLASSES = new Class[] {
		String.class,
		Integer.class,
		Long.class,
		Boolean.class,
		byte[].class,
		Float.class
	};

	@SuppressWarnings("rawtypes")
	protected SwtConfigParameterValueProcessor(SwtParameter owner,
			String configID, Class<VALUETYPE> valueType) {
		this.owner = owner;

		boolean ok = false;
		for (Class<?> validClass : VALID_CLASSES) {
			if (validClass.equals(valueType)) {
				ok = true;
				break;
			}
		}
		if (!ok) {
			Debug.out("Invalid valueType of " + valueType + "; Must be one of "
					+ Arrays.toString(VALID_CLASSES));
			this.valueType = null;
			return;
		}

		this.valueType = valueType;
		COConfigurationManager.addWeakParameterListener(this, false, configID);
	}

	@Override
	public void parameterChanged(String parameterName) {
		try {
			if (owner.isDisposed()) {
				COConfigurationManager.removeParameterListener(parameterName, this);
				return;
			}
			if (owner.DEBUG) {
				owner.debug("changed via " + Debug.getCompressedStackTrace());
			}

			owner.informChanged();
		} catch (Exception e) {
			Debug.out(
					"parameterChanged trigger from SwtConfigParameterValueProcessor "
							+ parameterName,
					e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public VALUETYPE getValue(PARAMTYPE p) {
		String key = p.getParamID();
		if (String.class.equals(valueType)) {
			return (VALUETYPE) COConfigurationManager.getStringParameter(key);
		} else if (Integer.class.equals(valueType)) {
			return (VALUETYPE) (Integer) COConfigurationManager.getIntParameter(key);
		} else if (Float.class.equals(valueType)) {
			return (VALUETYPE) (Float) COConfigurationManager.getFloatParameter(key);
		} else if (Boolean.class.equals(valueType)) {
			return (VALUETYPE) (Boolean) COConfigurationManager.getBooleanParameter(key);
		} else if (Long.class.equals(valueType)) {
			return (VALUETYPE) (Long) COConfigurationManager.getLongParameter(key);
		} else if (byte[].class.equals(valueType)) {
			return (VALUETYPE) COConfigurationManager.getByteParameter(key);
		}
		return null;
	}

	@Override
	public boolean setValue(PARAMTYPE p, VALUETYPE value) {
		boolean changed = false;
		if (changingCount == 0) {
			changedExternally = false;
		}

		changingCount++;
		try {
			String key = p.getParamID();
			VALUETYPE oldValue = getValue(p);
			if (oldValue==value||(oldValue!=null&&value!=null&&oldValue.equals(value))) {
				if (COConfigurationManager.doesParameterNonDefaultExist(key)
						|| COConfigurationManager.doesParameterDefaultExist(key)) {
					changedExternally = true;
					return false;
				} else {
					owner.debug(
							"WARNING. setValue: writing default value to config. Already has non-default value? "
									+ COConfigurationManager.doesParameterNonDefaultExist(key)
									+ ".  Has a default defined? "
									+ COConfigurationManager.doesParameterDefaultExist(key));
				}
			}

			if (changingCount > CHANGINGCOUNT_BREAKER) {
				Debug.out("Preventing StackOverflow on setting " + key + " to " + value
						+ " (was " + oldValue + ") via " + Debug.getCompressedStackTrace());
				changingCount = 1;
			} else {
				if (!changedExternally) {

					if (String.class.equals(valueType)) {
						changed = COConfigurationManager.setParameter(key, (String) value);
					} else if (Integer.class.equals(valueType)) {
						changed = COConfigurationManager.setParameter(key, (Integer) value);
					} else if (Float.class.equals(valueType)) {
						changed = COConfigurationManager.setParameter(key, (Float) value);
					} else if (Boolean.class.equals(valueType)) {
						changed = COConfigurationManager.setParameter(key, (Boolean) value);
					} else if (Long.class.equals(valueType)) {
						changed = COConfigurationManager.setParameter(key, (Long) value);
					} else if (byte[].class.equals(valueType)) {
						changed = COConfigurationManager.setParameter(key, (byte[]) value);
					}

					if (owner.DEBUG) {
						owner.debug("setValue to '" + value + "' from '" + oldValue
								+ "' -> " + (changed ? "CHANGED" : "NO CHANGE"));
					}
					changedExternally = true;
				}
			}

		} finally {
			changingCount--;
		}
		return changed;
	}

	@Override
	public boolean isDefaultValue(PARAMTYPE p) {
		return !COConfigurationManager.doesParameterNonDefaultExist(p.getParamID());
	}

	@Override
	public VALUETYPE getDefaultValue(PARAMTYPE p) {
		// Hope and pray the default is of the correct type
		// .. or we could go through the valueType and use
		// ConfigurationDefaults.getInstance().getXxxParameter()
		return (VALUETYPE) COConfigurationManager.getDefault(p.getParamID());
	}

	@Override
	public boolean resetToDefault(PARAMTYPE p) {
		return COConfigurationManager.removeParameter(p.getParamID());
	}

	@Override
	public void dispose(PARAMTYPE p) {
		COConfigurationManager.removeParameterListener(p.getParamID(), this);
	}
}
