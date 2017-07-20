/*
 * File    : ConfigSection.java
 * Created : 23 jan. 2004
 * By      : Paper
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

/**
 * Base class for adding "ConfigSection"s.<p>
 * This class does nothing.  Extend the subinterfaces to add a section to a
 * particular type of view (currently only SWT is supported).
 */
public interface ConfigSection {
  /**
   * Configuration panel will be added to main configuration view area
   */
  public static final String SECTION_ROOT = "root";
  /**
   * Configuration panel will be added to the plugins view area.
   */
  public static final String SECTION_PLUGINS = "plugins";
  public static final String SECTION_TRACKER = "tracker";
  public static final String SECTION_FILES = "files";
  public static final String SECTION_INTERFACE = "style";
  public static final String SECTION_CONNECTION = "server";
  public static final String SECTION_TRANSFER = "transfer";

  /**
   * Returns section you want your configuration panel to be under.
   * See SECTION_* constants.  To add a subsection to your own ConfigSection,
   * return the configSectionGetName result of your parent.<br>
   */
  public String configSectionGetParentSection();

  /**
   * In order for the plugin to display its section correctly, a key in the
   * Plugin language file will need to contain
   * <TT>ConfigView.section.<i>&lt;configSectionGetName() result&gt;</i>=The Section name.</TT><br>
   *
   * @return The name of the configuration section
   */
  public String configSectionGetName();

  /**
   * User selected Save.
   * All saving of non-plugin tabs have been completed, as well as
   * saving of plugins that implement com.biglybt.pif.ui.config
   * parameters.
   */
  public void configSectionSave();

  /**
   * Config view is closing
   */
  public void configSectionDelete();
}
