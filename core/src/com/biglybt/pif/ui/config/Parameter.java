/*
 * File    : Parameter.java
 * Created : 30 nov. 2003
 * By      : Olivier
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

package com.biglybt.pif.ui.config;

import com.biglybt.pif.config.ConfigParameter;
import com.biglybt.pif.config.ConfigParameterListener;

/**
 * represents a generic parameter description
 * @author Olivier
 *
 */
public interface
Parameter
	extends ConfigParameter
{
	public static final int MODE_BEGINNER		= 0;
	public static final int MODE_INTERMEDIATE	= 1;
	public static final int MODE_ADVANCED		= 2;

	/**
	 * Sets whether the UI object for this parameter is enabled (changeable) or
	 * disabled (not changeable, and usually grayed out)
	 *
	 * @param enabled The new enabled state
	 *
	 * @since 2.3.0.0
	 */
	public void
	setEnabled(
		boolean	enabled );

	/**
	 * Retrieves the enabled state for the UI object for this parameter
	 *
	 * @return The enabled state
	 *
	 * @since 2.3.0.0
	 */
	public boolean
	isEnabled();

	/**
	 * Gets the lowest user mode required for this parameter to be displayed.
	 *
	 * @return MODE_ constants above
	 * @since 3.0.5.3
	 */
	public int
	getMinimumRequiredUserMode();

	/**
	 * Sets the lowest user mode required for this parameter to be displayed.
	 *
	 * @param mode see MODE_ constants defined above
	 * @since 3.0.5.3
	 */

	public void
	setMinimumRequiredUserMode(
		int		mode );

	/**
	 * Sets whether the UI object for this parameter is visible to the user
	 *
	 * @param visible The new visibility state
	 *
	 * @since 2.3.0.4
	 */
	public void
	setVisible(
		boolean	visible );

	/**
	 * Retrieves the visiblility state for the UI object for this parameter
	 *
	 * @return The visibility state
	 *
	 * @since 2.3.0.4
	 */
	public boolean
	isVisible();

	/**
	 * Controls whether or not 'parameter change' events are fired for each incremental value change
	 * @param b
	 *
	 * @since 3.0.5.1
	 */

	public void
	setGenerateIntermediateEvents(
		boolean	b );

	/**
	 *
	 * @return
	 *
	 * @since 3.0.5.1
	 */

	public boolean
	getGenerateIntermediateEvents();

	/**
	 * Adds a listener triggered when the parameter is changed by the user
	 *
	 * @param l Listener to add
	 *
	 * @note Don't assume Thread when ParameterListener is triggered, especially for SWT calls
	 *
	 * @since 2.1.0.2
	 */
	public void
	addListener(
		ParameterListener	l );

	public void
	addAndFireListener(
		ParameterListener	l );
	
	/**
	 * Removes a previously added listener
	 *
	 * @param l Listener to remove.
	 *
	 * @since 2.1.0.2
	 */
	public void
	removeListener(
		ParameterListener	l );

	/**
	 * Adds a validator to this parameter.  The Validator will be called
	 * when the user tries to change the value.
	 * <p/>
	 * For parameters that have a text field, the validator will be called on
	 * each edit of the field. This allows you to notify the user of a potential
	 * error while they type.
	 * <p/>
	 * Subclasses may have helper functions that cast the "toValue" for you.
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	public void addValidator(ParameterValidator validator);

	/**
	 * Retrieve the actual text of the label associated with this parameter.
	 * This is the text after it has been looked up in the language bundle.
	 *
	 * @return The label's text
	 *
	 * @since 2.3.0.6
	 */
	public String getLabelText();

	/**
	 * Set the text of the label associated to with this parameter to the literal
	 * text supplied.
	 *
	 * @param sText The actual text to assign to the label
	 *
	 * @since 2.3.0.6
	 */
	public void setLabelText(String sText);

	/**
	 * Retrieve the language bundle key for the label associated with this
	 * parameter.
	 *
	 * @return The language bundle key, or null if the label is using literal
	 *          text
	 *
	 * @since 2.3.0.6
	 */
	public String getLabelKey();

	/**
	 * Set the label to use the supplied language bundle key for the label
	 * associated with this parameter
	 *
	 * @param sLabelKey The language bundle key to use.
	 *
	 * @since 2.3.0.6
	 */
	public void setLabelKey(String sLabelKey);

	public String getConfigKeyName();

	/**
	 * Indicates if a value has been set for the parameter.  If no value has
	 * been set, the default value will be used.
	 * <p/>
	 * To capture a "reset to default" event, you can {@link #addListener(ParameterListener)} or
	 * {@link #addConfigParameterListener(ConfigParameterListener)}, and check
	 * if hasBeenSet is false.
	 */
	public boolean
	hasBeenSet();

	/**
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setIndent(int indent, boolean fancy);

	void setAllowedUiTypes(String... uiTypes);

	boolean isForUIType(String uiType);

	/**
	 * Resets the parameter to its default value
	 * 
	 * @return true - value was reset; false - already reset
	 */
	boolean resetToDefault();

	/**
	 * Retrieve the parameter's value as an Object.  Subclasses will usually have
	 * a getValue() that will return a specific type.
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	Object getValueObject();
}
