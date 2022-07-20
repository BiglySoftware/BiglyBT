/*
 * File    : GenericParameter.java
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.config.ConfigParameterListener;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;

/**
 * @author epall
 *
 */
public abstract class
ParameterImpl
	implements EnablerParameter, com.biglybt.core.config.ParameterListener
{
	final protected  	String configKey;
	private 	String 			labelKey;
	private 	String 			label;
	private		int				mode = MODE_BEGINNER;

	private	Boolean	enabled							= null;
	private boolean	visible							= true;
	private boolean generate_intermediate_events	= false;

	private List<Parameter> toDisable;
	private List<Parameter> toEnable;

	private List change_listeners;
	private List<ParameterImplListener> impl_listeners;
	private List<ParameterValidator> validator_listeners = new ArrayList<>();

	private ParameterGroupImpl	parameter_group;
	private int indent;
	private boolean fancyIndent;
	private String refID;
	private String[] allowedUiTypes;

	public
	ParameterImpl(
			String coreConfigKey,
			String _labelKey)
	{
		this.configKey = coreConfigKey;
		labelKey 	= _labelKey;
		if ("_blank".equals(labelKey)) {
			labelKey = "!!";
		}
	}
	/**
	 * @deprecated(forRemoval=true) Use {@link #getConfigKeyName()}
	 *
	 * @note XXX Advanced Statistics still uses this
	 */
	@Deprecated
	public String getKey()
	{
		return configKey;
	}

	@Override
	public void addDisabledOnSelection(Parameter parameter) {
		if (toDisable == null) {
			toDisable = new ArrayList<>(1);
		}
		if (parameter instanceof ParameterGroupImpl) {
			ParameterImpl[] parameters = ((ParameterGroupImpl) parameter).getParameters();
			for (ParameterImpl p : parameters) {
				addDisabledOnSelection(p);
			}
		}
		toDisable.add(parameter);
	}

	@Override
	public void addDisabledOnSelection(Parameter... parameters) {
		for (Parameter parameter : parameters) {
			addDisabledOnSelection(parameter);
		}
	}

	@Override
	public void addEnabledOnSelection(Parameter paramToEnable) {
		if (toEnable == null) {
			toEnable = new ArrayList<>(1);
		}
		if (paramToEnable instanceof ParameterGroupImpl) {
			ParameterImpl[] parameters = ((ParameterGroupImpl) paramToEnable).getParameters();
			for (ParameterImpl p : parameters) {
				addEnabledOnSelection(p);
			}
		}
		toEnable.add(paramToEnable);
	}

	@Override
	public void addEnabledOnSelection(Parameter... parameters) {
		for (Parameter parameter : parameters) {
			addEnabledOnSelection(parameter);
		}
	}

	public List<Parameter> getDisabledOnSelectionParameters() {
		return toDisable == null ? Collections.emptyList(): toDisable;
	}

	public List<Parameter> getEnabledOnSelectionParameters() {
		return toEnable == null ? Collections.emptyList() : toEnable;
	}

	@Override
	public void
	parameterChanged(
		String		key )
	{
		fireParameterChanged();
	}

	public void
	fireParameterChanged()
	{
		if (change_listeners == null) {
			return;
		}
		// toArray() since listener trigger may remove listeners
		Object[] listenerArray = change_listeners.toArray();
		for (int i = 0; i < listenerArray.length; i++) {
			try {
				Object o = listenerArray[i];
				if (o instanceof ParameterListener) {

					((ParameterListener) o).parameterChanged(this);

				} else {

					((ConfigParameterListener) o).configParameterChanged(this);
				}
			} catch (Throwable f) {

				Debug.printStackTrace(f);
			}
		}
	}

	@Override
	public void
	setEnabled(
		boolean	e )
	{
		if (enabled != null && enabled == e) {
			return;
		}
		enabled = e;

		if (impl_listeners == null) {
			return;
		}
		// toArray() since listener trigger may remove listeners
		Object[] listenersArray = impl_listeners.toArray();
		for (int i = 0; i < listenersArray.length; i++) {
			try {
				ParameterImplListener l = (ParameterImplListener) listenersArray[i];
				l.enabledChanged(this);
			} catch (Throwable f) {

				Debug.printStackTrace(f);
			}
		}
	}

	@Override
	public boolean
	isEnabled()
	{
		return( enabled == null ? true : enabled );
	}

	@Override
	public int
	getMinimumRequiredUserMode()
	{
		return( mode );
	}


	@Override
	public void
	setMinimumRequiredUserMode(
		//@org.intellij.lang.annotations.MagicConstant(intValues = {com.biglybt.pif.ui.config.Parameter.MODE_BEGINNER, com.biglybt.pif.ui.config.Parameter.MODE_INTERMEDIATE, com.biglybt.pif.ui.config.Parameter.MODE_ADVANCED})
		int 	_mode )
	{
		mode	= _mode;
	}

	@Override
	public void
	setVisible(
		boolean	_visible )
	{
		visible	= _visible;
		refreshControl();
	}

	@Override
	public boolean
	isVisible()
	{
		return( visible );
	}

	@Override
	public void
	setGenerateIntermediateEvents(
		boolean		b )
	{
		generate_intermediate_events = b;
	}

	@Override
	public boolean
	getGenerateIntermediateEvents()
	{
		return( generate_intermediate_events );
	}

	public void
	setGroup(
		ParameterGroupImpl	_group )
	{
		parameter_group = _group;
	}

	public ParameterGroupImpl
	getGroup()
	{
		return( parameter_group );
	}

	@Override
	public void
	addListener(
		ParameterListener	l )
	{
		if (change_listeners == null) {
			change_listeners = new ArrayList(1);
		}
		change_listeners.add(l);

		if ( configKey != null && change_listeners.size() == 1 ){

			COConfigurationManager.addWeakParameterListener(this,  false, configKey);
		}
	}
	
	@Override
	public void
	addAndFireListener(
		ParameterListener	l )
	{
		addListener( l );
		
		l.parameterChanged( this );
	}

	@Override
	public void
	removeListener(
		ParameterListener	l )
	{
		if (change_listeners == null) {
			return;
		}
		change_listeners.remove(l);

		if ( configKey != null && change_listeners.size() == 0 ){

			COConfigurationManager.removeParameterListener(configKey, this );
		}
	}

	public void
	addImplListener(
		ParameterImplListener	l )
	{
		if (impl_listeners == null) {
			impl_listeners = new ArrayList<>(1);
		}
		impl_listeners.add(l);
	}

	public void
	removeImplListener(
		ParameterImplListener	l )
	{
		if (impl_listeners == null) {
			return;
		}
		impl_listeners.remove(l);
	}

	@Override
	public void
	addConfigParameterListener(
		ConfigParameterListener	l )
	{
		if (change_listeners == null) {
			change_listeners = new ArrayList(1);
		}
		change_listeners.add(l);

		if ( configKey != null && change_listeners.size() == 1 ){

			COConfigurationManager.addWeakParameterListener(this,  false, configKey);
		}
	}

	@Override
	public void
	removeConfigParameterListener(
		ConfigParameterListener	l )
	{
		if (change_listeners == null) {
			return;
		}
		change_listeners.remove(l);

		if ( configKey != null && change_listeners.size() == 0 ){

			COConfigurationManager.removeParameterListener(configKey, this );
		}
	}

	@Override
	public void addValidator(ParameterValidator validator) {
		validator_listeners.add(validator);
	}

	public ValidationInfo validate(Object newValue) {
		ValidationInfo resultValidation = new ValidationInfo(true);

		ParameterValidator[] validators = validator_listeners.toArray(new ParameterValidator[0]);
		for (ParameterValidator validator : validators) {
			ValidationInfo validationInfo = validator.isValidParameterValue(this, newValue);
			if (validationInfo == null) {
				continue;
			}
			if (!validationInfo.valid) {
				resultValidation = validationInfo;
				break;
			} else if (validationInfo.info != null) {
				if (resultValidation.info == null) {
					resultValidation.info = validationInfo.info;
				} else {
					resultValidation.info += "\n" + validationInfo.info;
				}
			}
		}
		return resultValidation;
	}

	@Override
	public String getLabelText() {
		if (label == null && labelKey != null) {
			label = MessageText.getString(labelKey);
		}
		return label;
	}

	@Override
	public void setLabelText(String sText) {
		labelKey = null;
		label = sText;

		triggerLabelChanged(sText, false);
	}

	@Override
	public String getLabelKey() {
		return labelKey == null ? label == null ? null : "!" + label + "!" : labelKey;
	}

	@Override
	public void setLabelKey(String sLabelKey) {
		labelKey = sLabelKey;
		label = null;

		triggerLabelChanged(labelKey, true);
	}

	@Override
	public final String
	getConfigKeyName()
	{
		return(configKey);
	}

	@Override
	public boolean
	hasBeenSet()
	{
		return configKey != null && COConfigurationManager.doesParameterNonDefaultExist(configKey);
	}

	private void triggerLabelChanged(String text, boolean isKey) {
		if (impl_listeners == null) {
			return;
		}
		// toArray() since listener trigger may remove listeners
		Object[] listenersArray = impl_listeners.toArray();
		for (int i = 0; i < listenersArray.length; i++) {
			try {
				ParameterImplListener l = (ParameterImplListener) listenersArray[i];
				l.labelChanged(this, text, isKey);

			} catch (Throwable f) {

				Debug.printStackTrace(f);
			}
		}
	}

	public void refreshControl() {
		if (impl_listeners == null) {
			return;
		}
		// toArray() since listener trigger may remove listeners
		Object[] listenersArray = impl_listeners.toArray();
		for (int i = 0; i < listenersArray.length; i++) {
			try {
				ParameterImplListener l = (ParameterImplListener) listenersArray[i];
				l.refreshControl(this);

			} catch (Throwable f) {

				Debug.printStackTrace(f);
			}
		}
	}

	public void
	destroy()
	{
		change_listeners = null;
		impl_listeners = null;
		toDisable = null;
		toEnable = null;

		if (configKey != null) {
			COConfigurationManager.removeParameterListener(configKey, this );
		}
	}

	@Override
	public void setIndent(int indent, boolean fancy) {
		this.indent = indent;
		this.fancyIndent = fancy;
	}

	public int getIndent() {
		return indent;
	}

	public boolean isIndentFancy() {
		return fancyIndent;
	}

	public void setReferenceID(String refID) {
		this.refID = refID;
	}

	public String getReferenceID() {
		return refID;
	}

	@Override
	public void setAllowedUiTypes(String... uiTypes) {
		if (uiTypes != null) {
			Arrays.sort(uiTypes);
		}
		this.allowedUiTypes = uiTypes;
	}

	@Override
	public boolean isForUIType(String uiType) {
		if (allowedUiTypes == null) {
			return true;
		}
		return Arrays.binarySearch(allowedUiTypes, uiType) >= 0;
	}

	@Override
	public boolean resetToDefault() {
		if (configKey == null) {
			return false;
		}
		return COConfigurationManager.removeParameter(configKey);
	}

	@Override
	public Object getValueObject() {
		if (configKey == null) {
			return null;
		}
		return COConfigurationManager.getParameter(configKey);
	}

	public boolean search(Pattern regex) {
		
		if (configKey != null && regex.matcher(configKey).find()) {
			return true;
		}
		String labelText = getLabelText();
		if (labelText != null && regex.matcher(labelText).find()) {
			return true;
		}
		if (this instanceof ParameterWithSuffix) {
			String suffixLabelKey = ((ParameterWithSuffix) this).getSuffixLabelKey();
			if (suffixLabelKey != null && !suffixLabelKey.isEmpty()) {
				String suffix = MessageText.getString(suffixLabelKey);
				if (regex.matcher(suffix).find()) {
					return true;
				}
			}
		}
		return false;
	}
}
