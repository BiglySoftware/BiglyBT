/*
 * File    : PluginEvent.java
 * Created : 06-Feb-2004
 * By      : parg
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

package com.biglybt.pif;

/** allow config wizard progress to be determined
 *
 * @author parg
 * @since 2.0.8.0
 */
public interface
PluginEvent
{
	/**
	 * Not guaranteed to trigger.  Used to be triggered at startup
	 */
	public static final int	PEV_CONFIGURATION_WIZARD_STARTS			= 1;
	/**
	 * Not guaranteed to trigger.  Used to be triggered at startup
	 */
	public static final int	PEV_CONFIGURATION_WIZARD_COMPLETES		= 2;
	/**
	 * No longer triggered.  Used to be core tasks
	 */
	public static final int	PEV_INITIALISATION_PROGRESS_TASK		= 3;
	/**
	 * No longer triggered.  Used to be core tasks
	 */
	public static final int	PEV_INITIALISATION_PROGRESS_PERCENT		= 4;
		/**
		 * @since 2403
		 */
	public static final int	PEV_INITIAL_SHARING_COMPLETE			= 5;

	/**
	 * Triggered when UI Initialization is complete.  This is after the UI
	 * is attached.  This trigger is helpful if you need access to an UI
	 * element from a plugin after you in the UI attachment order.
	 *
	 * @since 2403
	 */
	public static final int	PEV_INITIALISATION_UI_COMPLETES		= 6;

	/**
	 * This event is triggered when all plugins have had their 'initialize' method called
	 * on startup. This differs from the 'initialisationComplete' callback on PluginListener
	 * in that it is *guranteed* to be triggered before any other initialisation actions
	 * take place.
	 */

	public static final int	PEV_ALL_PLUGINS_INITIALISED			= 7;

		/**
		 * Data is the PluginInterface of installed plugin
		 * @since 4.1.0.1
		 */
	public static final int	PEV_PLUGIN_OPERATIONAL				= 8;

	public static final int	PEV_PLUGIN_NOT_OPERATIONAL			= 9;

	public static final int	PEV_PLUGIN_INSTALLED				= 10;	// value is String plugin_id
	public static final int	PEV_PLUGIN_UPDATED					= 11;	// value is String plugin_id
	public static final int	PEV_PLUGIN_UNINSTALLED				= 12;	// value is String plugin_id

		/**
		 * Plugin specific events can be raised by a plugin to communicate with
		 * other components. The event type must start from the number below
		 */

	public static final int	PEV_FIRST_USER_EVENT					= 1024;

	public int
	getType();

	public Object
	getValue();
}
