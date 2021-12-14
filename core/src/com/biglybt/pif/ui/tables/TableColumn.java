/*
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

package com.biglybt.pif.ui.tables;

/**
 * This interface provides access to an Azureus table column.
 */
public interface TableColumn {
	public static final int MENU_STYLE_HEADER = 1;

	public static final int MENU_STYLE_COLUMN_DATA = 2;

  /** The cells in this column display textual information. */
  public static final int TYPE_TEXT = 1;
  /** The graphic type, providing access to graphic specific functions in
   * {@link TableCell}.
   */
  public static final int TYPE_GRAPHIC = 2;
  /**
   * The cells in this column display only textual information, and does not
   * set any other visible properties of cell (background, foreground, icon,
   * etc).
   *
   * Using this type allows us to call refresh less, and saves on CPU.
   */
  public static final int TYPE_TEXT_ONLY = 3;

  /** leading alignment */
  public static final int ALIGN_LEAD = 1;
  /** trailing alignment */
  public static final int ALIGN_TRAIL = 2;
  /** center alignment */
  public static final int ALIGN_CENTER = 3;
  /** top align */
  public static final int ALIGN_TOP = 4;
  /** bottom align */
  public static final int ALIGN_BOTTOM = 8;

  /** For {@link #setPosition(int)}. Make column invisible initially. */
  public static final int POSITION_INVISIBLE = -1;
  /** For {@link #setPosition(int)}. Make column the last column initially. */
  public static final int POSITION_LAST = -2;

  /** Trigger refresh listeners every time a graphic cycle occurs (set by user) */
  public static final int INTERVAL_GRAPHIC = -1;
  /** Trigger refresh listeners every time a GUI update cycle occurs (set by user) */
  public static final int INTERVAL_LIVE = -2;
  /** Trigger refresh only when the cell/row becomes invalid */
  public static final int INTERVAL_INVALID_ONLY = -3;

  public static final String CAT_ESSENTIAL = "essential";
  public static final String CAT_SHARING = "sharing";
  public static final String CAT_TRACKER = "tracker";
  public static final String CAT_TIME = "time";
  public static final String CAT_SWARM = "swarm";
  public static final String CAT_CONTENT = "content";
  public static final String CAT_PEER_IDENTIFICATION = "identification";
  public static final String CAT_PROTOCOL = "protocol";
  public static final String CAT_BYTES = "bytes";
  public static final String CAT_SETTINGS = "settings";
  public static final String CAT_CONNECTION = "connection";
  public static final String CAT_PROGRESS = "progress";

  	// user-data properties

  public static final String UD_FORCE_VISIBLE = "ud_fv";	// Long

  /** Initialize a group of variables all at once.  Saves on individual setXxx.
   *
   * @param iAlignment See {@link #setAlignment(int)}
   * @param iPosition See {@link #setPosition(int)}
   * @param iWidth See {@link #setWidth(int)}
   * @param iInterval See {@link #setRefreshInterval(int)}
   *
   * @since 2.1.0.0
   */
  public void initialize(int iAlignment, int iPosition,
                         int iWidth, int iInterval);


  /** Initialize a group of variables all at once.  Saves on individual setXxx.
   *
   * @param iAlignment See {@link #setAlignment(int)}
   * @param iPosition See {@link #setPosition(int)}
   * @param iWidth See {@link #setWidth(int)}
   *
   * @since 2.1.0.0
   */
  public void initialize(int iAlignment, int iPosition, int iWidth);

  /**
   * The logical name of the column. This was set via
   * {@link TableManager#createColumn} and can not be changed.
   *
   * @return the column name (identification)
   *
   * @since 2.1.0.0
   */
  public String getName();

  /**
   * Returns the user's column name override if it exists
   * @return
   * @since 5.0.0.1
   */

  public String getNameOverride();

  public void setNameOverride( String name );

  /** Which table the column will be visible in.  This was set via
   * {@link TableManager#createColumn} and can not be changed.
   *
   * @return {@link TableManager}.TABLE_* constant(s)
   *
   * @since 2.1.0.0
   */
  public String getTableID();

  /** The type of the contained data.<br>
   * Current supported types are long, string, and graphic.
   * <P>
   * NOTE: This MUST be set BEFORE adding the column to a table.
   * <br>
   * The default type is {@link #TYPE_TEXT_ONLY}.
   *
   * @param type {@link #TYPE_TEXT}, {@link #TYPE_TEXT_ONLY}, {@link #TYPE_GRAPHIC}
   *
   * @since 2.1.0.0
   */
  public void setType(int type);

  /** Returns the type of the contained data.
   *
   * @return type TYPE_TEXT, or TYPE_GRAPHIC
   *
   * @since 2.1.0.0
   */
  public int getType();

  /** The column size.
   * <P>
   * NOTE: This MUST be set BEFORE adding the column to a table.
   *
   * @param width the size in pixels, adjusting for DPI
   *
   * @since 2.1.0.0
   */
  public void setWidth(int unadjustedWidth);

  public void setWidthPX(int realPixelWidth);

  /** Returns the column's size
   *
   * @return width in pixels
   *
   * @since 2.1.0.0
   */
  public int getWidth();

  /** Location to put the column.  When set before being added to the UI
   * (see {@link TableManager#addColumn}), the supplied value will be used
   * as the default position.  If the user has moved the column previously,
   * the new position will be used, and the default position will be ignored.
   *
   * This function cannot be called after you have added the column to a UI
   * table.  In the future, setting the position after adding the column to the
   * UI table will result in the column being moved.
   *
   * @param position Column Number (0 based), POSITION_INVISIBLE or POSITION_LAST
   *
   * @since 2.1.0.0
   */
  public void setPosition(int position);


  /** Returns the position of the column
   *
   * @return Column Number (0 based), POSITION_INVISIBLE or POSITION_LAST
   *
   * @since 2.1.0.0
   */
  public int getPosition();

  /** Orientation of the columns text and header.
   * <P>
   * NOTE: This MUST be set BEFORE adding the column to a table.
   *
   * @param alignment ALIGN_TRAIL, ALIGN_LEAD, or ALIGN_CENTER
   *
   * @since 2.1.0.0
   */
  public void setAlignment(int alignment);

  /** Returns the alignment of the column
   *
   * @return ALIGN_TRAIL, ALIGN_LEAD, or ALIGN_CENTER
   *
   * @since 2.1.0.0
   */
  public int getAlignment();

  /** Set how often the cell receives a refresh() trigger
   *
   * @param interval INTERVAL_GRAPHIC, INTERVAL_LIVE, INTERVAL_INVALID_ONLY
   *                 constants, or an integer based on the user-configurable
   *                 "GUI refresh interval".  For example, specifying 4 will
   *                 result in a refresh trigger every 4 "GUI refresh intervals"
   *
   * @since 2.1.0.0
   */
  public void setRefreshInterval(int interval);

  /** Returns the refresh interval of the column.
   * The default is INTERVAL_INVALID_ONLY
   *
   * @return INTERVAL_* constant, or a number representing the # of GUI refresh
   *         cycles between each cell refresh call.
   *
   * @since 2.1.0.0
   */
  public int getRefreshInterval();

  /**
   * Sets the minimum width that the column can be before other columns
   * start collapsing.  This may not prevent the user from resizing the column
   * smaller than specified.
   * <p>
   * If not set, the width specified on initialize will be the minimum width
   * <p>
   * Not all UIs may have this feature implemented.
   *
   * @param minwidth new minumum width
   *
   * @since 3.0.0.7
   */
  public void setMinWidth(int minwidth);

  /**
   * Gets the minimum width that the column can be before other columns
   * start collapsing.
   * <p>
   * If not set, the width specified on initialize will be the minimum width
   * <p>
   * Not all UIs may have this feature implemented.
   *
   * @return minumum width of the column
   *
   * @since 3.0.0.7
   */
  public int getMinWidth();

  /**
   * Sets the maximum width that the column can be
   * <p>
   * Not all UIs may have this feature implemented.
   *
   * @param maxwidth new maximum width
   *
   * @since 3.0.0.7
   */
  public void setMaxWidth(int maxwidth);

  /**
   * Gets the maximum width the column can be
   * <p>
   * Not all UIs may have this feature implemented.
   *
   * @return maximum width of column
   *
   * @since 3.0.0.7
   */
  public int getMaxWidth();

  /**
   * Sets the minimum and maximum widths in one call
   * <p>
   * Not all UIs may have this min and max limits implemented.
   *
   * @param min New minimum column width
   * @param max New maximum column width
   *
   * @since 3.0.0.7
   */
  public void setWidthLimits(int min, int max);

 
  /**
   * Sets the preferred width of the column.  When the UI is in auto-expand
   * mode and space is made available, the columns will first fill to their
   * preferred width, then to their maximum width.
   *
   * @param width New preferred width
   *
   * @since 3.0.0.7
   */
  public void setPreferredWidth(int width);

  /**
   * Gets the preferred width of the coloumn.
   *
   * @return preferred width
   *
   * @since 3.0.0.7
   */
  public int getPreferredWidth();

  /**
   * Retrieves whether the preferred width is automatically calculated.
   *
   * @return preferred width auto calculation state
   *
   * @since 3.0.0.7
   */
  public boolean isPreferredWidthAuto();

  /**
   * Sets whether the preferred with is automatically calculated.  An
   * automatically calculated preferred width will be set to the largest
   * text width known to that column
   *
   * @param auto Preferred Width Auto State
   *
   * @since 3.0.0.7
   */
  public void setPreferredWidthAuto(boolean auto);

  /**
   * Gets the visibility of the column
   * <p>
   * Not all UIs may have this feature implemented.
   *
   * @return Column visibility
   *
   * @since 3.0.0.7
   */
  public boolean isVisible();

  /**
   * Associates custom data with the column, usually meant for column-specific settings and stores it across sessions
   * @param key the key under which the value will be stored and serialized
   * @param value should be BEncodable, otherwise it won't be serialized
   */
  public void setUserData(String key, Object value);
  public void removeUserData(String key);


  	/**
	 * implement this method if you want to be notified when the stored column
	 * configuration such as user data or GUI-adjustable properties have been
	 * loaded
	 */
	public void postConfigLoad();

	/**
	 * implement this method if you want to be notified when the column
	 * configuration is about to be serialized
	 */
	public void preConfigSave();

  /**
   *
   * @param key
   * @return data set via setUserData()
   */
  public Object getUserData(String key);


  public String getUserDataString(String key);

  /**
   * Sets the visibility of the column
   *
   * @param visible New visibility state
   *
   * @since 3.0.0.7
   */
  public void setVisible(boolean visible);

  /** Adds a listener that triggers when a TableCell that belongs to this column
   * needs refreshing.
   *
   * @param listener Listener Object to be called when refresh is needed.
   *
   * @since 2.1.0.0
   */
  public void addCellRefreshListener(TableCellRefreshListener listener);
  /** Removed a previously added TableCellRefreshListener
   *
   * @param listener Previously added listener
   *
   * @since 2.1.0.0
   */
  public void removeCellRefreshListener(TableCellRefreshListener listener);


  /** Adds a listener that triggers when a TableCell that belongs to this column
   * is being added.
   *
   * @param listener Listener Object to be called when refresh is needed.
   *
   * @since 2.1.0.0
   */
  public void addCellAddedListener(TableCellAddedListener listener);
  public void removeCellAddedListener(TableCellAddedListener listener);

  /** Adds a listener that triggers when a TableCell that belongs to this column
   * is being disposed.
   *
   * @param listener Listener Object to be called when refresh is needed.
   *
   * @since 2.1.0.0
   */
  public void addCellDisposeListener(TableCellDisposeListener listener);
  public void removeCellDisposeListener(TableCellDisposeListener listener);

  /** Adds a listener that triggers when a TableCell that belongs to this column
   * has a tooltip action
   *
   * @param listener Listener Object to be called when refresh is needed.
   *
   * @since 2.1.0.2
   */
  public void addCellToolTipListener(TableCellToolTipListener listener);
  public void removeCellToolTipListener(TableCellToolTipListener listener);

  /**
   * Adds a listener that triggers when a TableCell that belongs to this column
   * has a mouse event.
   *
   * @param listener
   *
   * @since 2.3.0.7
   */
  public void addCellMouseListener(TableCellMouseListener listener);
  /** Remove a previously added TableCellMouseListener
  *
  * @param listener Previously added listener
   * @since 2.3.0.7
  */
  public void removeCellMouseListener(TableCellMouseListener listener);

  public void addCellMenuListener(TableCellMenuListener listener);
 
  public void removeCellMenuListener(TableCellMenuListener listener);

  /**
   * A listener is added for every type of cell listener the supplied object
   * implements
   *
   * @param listenerObject Object implementing some cell listeneters
   *
   * @since 2.4.0.0
   */
  public void addListeners(Object listenerObject);


  /** Invalidate all cells in this column.  The cells will be forced to
   * update on the next refresh.
   *
   * @since 2.1.0.0
   */
  public void invalidateCells();

  /**
   * Invalidates any cells which are linked to the given data source object.
   *
   * @since 3.0.1.5
   */
  public void invalidateCell(Object data_source);


  /** Adds a Context Menu item to the column
	 *
	 * @param resourceKey ID of the context menu, which is also used to retreieve
	 *                    the textual name from the plugin language file.
	 * @param menuStyle See MENU_STYLE_* constants (header or data)
	 *
	 * @return a newly created menu item
	 *
	 * @since 4.2.0.5
	 */
	public TableContextMenuItem addContextMenuItem(String resourceKey,
			int menuStyle);

	/** Adds a Context Menu item to data section of the column
	 *
	 * @param resourceKey ID of the context menu, which is also used to retreieve
	 *                    the textual name from the plugin language file.
	 *
	 * @return a newly created menu item
	 *
	 * @since 2.4.0.0
	 */
	public TableContextMenuItem addContextMenuItem(String resourceKey);


	/**
	 * Returns whether the column's data will be obfuscated when screen
	 * capturing (for bug reports, etc).
	 * <p>
	 * Currently not fully implemented for plugins
	 *
	 * @return Obfuscated value
	 *
	 * @since 2.4.0.3
	 */
	boolean isObfuscated();

	/**
	 * Sets whether the column's data will be obfuscated during a screen
	 * capture (for bug reports, etc).
	 *
	 * @param hideData new state of obfuscation
	 *
	 * @since 2.4.0.3
	 */
	void setObfuscation(boolean hideData);

	/**
	 * @since 4005
	 */

	public void
	remove();


	/**
	 * @param listener
	 *
	 * @since 4.0.0.5
	 */
	void addColumnExtraInfoListener(TableColumnExtraInfoListener listener);


	/**
	 * @param listener
	 *
	 * @since 4.0.0.5
	 */
	void removeColumnExtraInfoListener(TableColumnExtraInfoListener listener);


	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	Class getForDataSourceType();

	/**
	 *
	 * @since 4.4.0.7
	 */
	public void setIconReference(String iconID, boolean showOnlyIcon);

	/**
	 *
	 * @since 4.4.0.7
	 */
	public String getIconReference();

	/**
	 * *since 4501
	 * @param mode from Parameter. constants
	 */

	public void
	setMinimumRequiredUserMode(
		int		mode );
}