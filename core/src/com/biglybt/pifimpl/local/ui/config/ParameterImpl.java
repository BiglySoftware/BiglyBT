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
import java.util.Collections;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.config.ConfigParameterListener;
import com.biglybt.pif.ui.config.EnablerParameter;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;
import com.biglybt.pifimpl.local.PluginConfigImpl;

/**
 * @author epall
 *
 */
public class
ParameterImpl
	implements EnablerParameter, com.biglybt.core.config.ParameterListener
{
	protected 	PluginConfigImpl	config;
	private 	String 			key;
	private 	String 			labelKey;
	private 	String 			label;
	private		int				mode = MODE_BEGINNER;

	private	boolean	enabled							= true;
	private boolean	visible							= true;
	private boolean generate_intermediate_events	= true;

	private List<Parameter> toDisable;
	private List<Parameter> toEnable;

	private List listeners;
	private List<ParameterImplListener> impl_listeners;

	private ParameterGroupImpl	parameter_group;

	public
	ParameterImpl(
		PluginConfigImpl	_config,
		String 			_key,
		String 			_labelKey )
	{
		config	= _config;
		key		= _key;
		labelKey 	= _labelKey;
		if ("_blank".equals(labelKey)) {
			labelKey = "!!";
		}
	}
	/**
	 * @return Returns the key.
	 */
	public String getKey()
	{
		return key;
	}

	@Override
	public void addDisabledOnSelection(Parameter parameter) {
		if (toDisable == null) {
			toDisable = new ArrayList<>(1);
		}
		if (parameter instanceof ParameterGroupImpl) {
			ParameterImpl[] parameters = ((ParameterGroupImpl) parameter).getParameters();
			Collections.addAll(toDisable, parameters);
			return;
		}
		toDisable.add(parameter);
	}

	@Override
	public void addEnabledOnSelection(Parameter parameter) {
		if (toEnable == null) {
			toEnable = new ArrayList<>(1);
		}
		if (parameter instanceof ParameterGroupImpl) {
			ParameterImpl[] parameters = ((ParameterGroupImpl) parameter).getParameters();
			Collections.addAll(toEnable, parameters);
			return;
		}
		toEnable.add(parameter);
	}

	public List getDisabledOnSelectionParameters() {
		return toDisable == null ? Collections.EMPTY_LIST : toDisable;
	}

	public List getEnabledOnSelectionParameters() {
		return toEnable == null ? Collections.EMPTY_LIST : toEnable;
	}

	@Override
	public void
	parameterChanged(
		String		key )
	{
		fireParameterChanged();
	}

	protected void
	fireParameterChanged()
	{
		if (listeners == null) {
			return;
		}
		// toArray() since listener trigger may remove listeners
		Object[] listenerArray = listeners.toArray();
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
		return( enabled );
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
		if (listeners == null) {
			listeners = new ArrayList(1);
		}
		listeners.add(l);

		if ( listeners.size() == 1 ){

			COConfigurationManager.addParameterListener( key, this );
		}
	}

	@Override
	public void
	removeListener(
		ParameterListener	l )
	{
		if (listeners == null) {
			return;
		}
		listeners.remove(l);

		if ( listeners.size() == 0 ){

			COConfigurationManager.removeParameterListener( key, this );
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
		if (listeners == null) {
			listeners = new ArrayList(1);
		}
		listeners.add(l);

		if ( listeners.size() == 1 ){

			COConfigurationManager.addParameterListener( key, this );
		}
	}

	@Override
	public void
	removeConfigParameterListener(
		ConfigParameterListener	l )
	{
		if (listeners == null) {
			return;
		}
		listeners.remove(l);

		if ( listeners.size() == 0 ){

			COConfigurationManager.removeParameterListener( key, this );
		}
	}

	@Override
	public String getLabelText() {
		if (label == null) {
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
		return labelKey;
	}

	@Override
	public void setLabelKey(String sLabelKey) {
		labelKey = sLabelKey;
		label = null;

		triggerLabelChanged(labelKey, true);
	}

	@Override
	public String
	getConfigKeyName()
	{
		return( key );
	}

	@Override
	public boolean
	hasBeenSet()
	{
		return( COConfigurationManager.doesParameterNonDefaultExist( key ));
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

	public void
	destroy()
	{
		listeners = null;
		impl_listeners = null;
		toDisable = null;
		toEnable = null;

		COConfigurationManager.removeParameterListener( key, this );
	}
}
