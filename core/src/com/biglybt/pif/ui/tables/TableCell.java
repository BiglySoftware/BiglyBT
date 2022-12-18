/*
 * File    : TableCell.java
 * Created : 29 nov. 2003
 * By      : Olivier
 * Adapted to MyTorrents by TuxPaper 2004/02/16
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

package com.biglybt.pif.ui.tables;

import com.biglybt.pif.ui.Graphic;
import com.biglybt.ui.common.table.TableView;

/** This interface provides access to an Azureus table cell.
 *
 * @see TableManager
 *
 * @author Oliver (Original PeerTableItem Code)
 * @author TuxPaper (Generic-izing)
 *
 * @since 2.0.8.5
 */
// Modified from MyTorrentsTableItem/PeerTableItem
public interface TableCell {

  /** Retrieve the data object associated with the current table row and cell.
   * The results of this method MUST NOT BE CACHED.
   * The link between a table cell and a DataSource is not persistent and can
   * change from call to call (for example when the table is re-ordered, the
   * link may be modified)
   *
   * @return The return type is dependent upon which table the cell is for:<br>
   *   TABLE_MYTORRENTS_*: {@link com.biglybt.pif.download.Download}
   *                       object for the current row<br>
   *   TABLE_TORRENT_PEERS: {@link com.biglybt.pif.peers.Peer}
   *                        object for the current row<br>
   *   TABLE_TORRENT_FILES: {@link com.biglybt.pif.disk.DiskManagerFileInfo}
   *                        object for the current row<br>
   *   TABLE_MYTRACKER: {@link com.biglybt.pif.tracker.TrackerTorrent}
   *                    object for the current row<br>
   *   TABLE_MYSHARES: {@link com.biglybt.pif.sharing.ShareResource}
   *                    object for the current row<br>
   *   remaining TABLE_* constants: undefined or null<br>
   */
  Object getDataSource();

  /** Retreive the TableColumn that this cell belongs to
   *
   * @return this cell's TableColumn
   */
  TableColumn getTableColumn();

  /** Retrieve the TableRow that this cell belongs to
   *
   * @return this cell's TableRow
   */
  TableRow getTableRow();

  /** Returns which table the cell is being displayed in.
   *
   * @return {@link TableManager}.TABLE_* constant
   */
  String getTableID();

  /**
   * This method is called to set the cell's text.
   * Caching is done, so that if same text is used several times,
   * there won't be any 'flickering' effect. Ie the text is only updated if
   * it's different from current value.
   * <p>
   * This function must be called from the same thread that the GUI is running
   * under.  Listeners like {@link TableCellAddedListener} do not always get
   * called on the GUI thread.
   * <p>
   * If you wish to set the text and not worry about changing to the GUI thread,
   * use {@link #invalidate()}, and set the text in the
   * {@link TableCellRefreshListener}
   *
   * @param text the text to be set
   * @return True - the text was updated.<br>
   *         False - the text was the same and not modified.
   */
  boolean setText(String text);

  /** Retrieve the Cell's text
   *
   * @return Cell's text
   */
  String getText();

  /**
   * For image based cells should return a textual equivalent. This is useful for filtering, for example.
   * @return the text value to be used or null if it should be ignored
   */
  String getTextEquivalent();

  void setTextEquivalent( String str );
  
  /**
   * Set the numeric value of the cell. This is not used for formating, sort order, display, it is
   * purely to allow the numeric value of a cell be set and retrieved
   * @param d
   */
  
  void setNumeric( double d );
  
  /**
   * 
   * @return numeric value of the cell or Double.NaN if undefined 
   */
  
  double getNumeric();
  
  /** Change the cell's foreground color.
   * <p>
   * pass -1 to return color back to default
   *
   * @param red red value (0 - 255)
   * @param green green value (0 - 255)
   * @param blue blue value (0 - 255)
   * @return True - Color changed. <br>
   *         False - Color was already set.
   */
  boolean setForeground(int red, int green, int blue);

  /**
   * Change the cell's foreground color
   *
   * @param rgb int array containing red, green, and blue values, respectively.
   *            null to return color back to default
   * @return
   *
   * @since 3.0.4.3
   */
  boolean setForeground(int[] rgb);

  /**
   * Change the cell's foreground color to the user's defined "error" color.
   *
   * @since 3.0.3.5
   * @return True - Color changed. <br>
   *         False - Color was already set.
   */
  boolean setForegroundToErrorColor();

  /**
   * Get the foreground color of the cell
   *
   * @return array containing red, green, and blue color
   *
   * @since 2.5.0.1
   */
  int[] getForeground();

	/**
	 * Get the background color of the cell
	 *
	 * @return array containing red, green, and blue color.  Might be null
	 *
	 * @since 3.0.3.5
	 */
	int[] getBackground();

  /** Sets a Comparable object that column sorting will act on.  If you never
   * call setSortValue, your column will be sorted by the cell's text.
   *
   * @param valueToSort the object that will be used when the column cell's
   *                    are compared to each other
   * @return True - Sort Value changed. <br>
   *         False - Sort Value was already set to object supplied.
   */
  public boolean setSortValue(Comparable valueToSort);

  /** Sets a long value that the column sorting will act on.
   *
   * @param valueToSort sorting value.
   * @return True - Sort Value changed. <br>
   *         False - Sort Value was already set to value supplied.
   */
  public boolean setSortValue(long valueToSort);

  /**
   * Sets a float value that the column sorting will act upon.
   * @param valueToSort float sort value
   * @return true if sort value changed, or false if sort value already set to value supplied
   */
  public boolean setSortValue( float valueToSort );


  /** Retrieves the sorting value
   *
   * @return Object that will be sorted on
   */
  public Comparable getSortValue();

  /**
   * When false the cell's sort value should be set to the simple sort value without any implicit secondary
   * sort being applied. Default implementation is based on whether the table is in multi-sort mode where the user
   * is explicitly selecting secondary, tertiary... sorting  
   * @return
   * @since 2.2.0.3
   */
  
  public default boolean isSecondarySortEnabled()
  {
	  TableView<?> view = getTableRow().getView();
	  
	  if ( view != null ){
		  
		  return( view.getSortColumns().length < 2 );
		  
	  }else{
		  
		  return( true );
	  }
  }
  
  /** Determines if the user has chosen to display the cell
  *
  * @return True - User has chosen to display cell
  */

  boolean isShown();

  /** Validility of the cell's text.
   *
   * @return True - Text is the same as last call.  You do not need to update
   *                unless you have new text to display. <br>
   *         False - Cell-to-Datasource link has changed, and the text is
   *                 definitely not valid.
   */
  boolean isValid();

  /** Sets the cell to invalid. This will result in a refresh on the next
   * scheduled interval.
   *
   * @since 2.3.0.7
   */
  public void invalidate();

  /**
   * Set the cell's tooltip display.
   *
   *
   * @param tooltip Object to display.  Currently, only String is supported
   *
   * @see #addToolTipListener(TableCellToolTipListener)
   * @since 2.1.0.2
   */
  public void setToolTip(Object tooltip);
  /**
   * Retrieve the tooltip object assigned to this cell
   *
   * @return tooltip object
   *
   * @see #addToolTipListener(TableCellToolTipListener)
   * @since 2.1.0.2
   */
  public Object getToolTip();


  /**
   * Retrieve whether the cell has been disposed.  This will return true after
   * the {@link TableCellDisposeListener} is triggered.
   *
   * @return disposal state
   * @since 2.3.0.7
   */
  public boolean isDisposed();

  /**
   * Retrieves the number of lines available for setting text
   *
   * @return # of lines available, -1 if unknown
   *
   * @since 3.0.1.1
   */
  public int getMaxLines();

  //////////////////////////////////
  // Start TYPE_GRAPHIC functions //
  //////////////////////////////////

  /** Retrieve the width of the cell's drawing area (excluding any margin) for
   * TableColumn objects of TYPE_GRAPHIC only.
   *
   * @return if you are filling the cell, this is the width your image should be
   */
  public int getWidth();

  /** Retrieve the height of the cell's drawing area (excluding any margin) for
   * TableColumn objects of TYPE_GRAPHIC only.
   *
   * @return if you are filling the cell, this is the height your image should be
   */
  public int getHeight();

  /** Sets the image to be drawn.
   * <p>
   * From 3.0.1.1, setting the graphic to the same Graphic object will not
   * redraw the image.  You need to {@link #invalidate()} the cell if you
   * know the image bits have changed (or you could pass a new Graphic object
   * in each time a new image is generated)
   * <p>
   * Previously, setting the graphic to the same object resulted in a repaint.
   * Plugins were naughty and would do this on every refresh, causing horrible
   * repaint slowdowns.
   *
   * @param img image to be stored & drawn
   * @return true - image was changed.<br>
   *         false = image was the same
   */
  public boolean setGraphic(Graphic img);

  /** Retrieve the SWT graphic related to this table item for
   * TableColumn objects of TYPE_GRAPHIC only.
   *
   * @return the Image that is draw in the cell, or null if there is none.
   */
  public Graphic getGraphic();

  /** TODO:
  /** Sets the image to be drawn to the file specified for
   * TableColumn objects of TYPE_GRAPHIC only.
   *
   * @param imageLocation URI of image
   * @return true - image was changed.<br>
   *         false = image was the same
   *
  public boolean setGraphic(String imageLocation);
  */


  /** Sets whether the graphic fills the whole cell for
   * TableColumn objects of TYPE_GRAPHIC only. This may effect how often
   * a refresh of the cell is needed, and effects alignment.
   *
   * @param bFillCell true - the whole cell is filled by the graphic
   */
  public void setFillCell(boolean bFillCell);

	/**
	 * @return
	 *
	 * @since 3.0.5.3
	 */
	int getMarginHeight();

  /**
   * Specifies the number of pixels of vertical margin that will
   * be placed along the top and bottom edges of the layout for
   * TableColumn objects of TYPE_GRAPHIC only.
   * <p>
   * The default is 1.
   *
   * @param height new margin height
   */
  public void setMarginHeight(int height);

	/**
	 * @return
	 *
	 * @since 3.0.5.3
	 */
	int getMarginWidth();

  /**
   * Specifies the number of pixels of horizontal margin that will
   * be placed along the left and right edges of the layout for
   * TableColumn object of TYPE_GRAPHIC only.
   * <p>
   * The default is 1.
   *
   * @param width new margin width
   */
  public void setMarginWidth(int width);

  // End TYPE_GRAPHIC functions

  /** Adds a listener that triggers when the TableCell needs refreshing
   *
   * @param listener Listener Object to be called when refresh is needed.
   */
  public void addRefreshListener(TableCellRefreshListener listener);
  /** Remove a previously added TableCellRefreshListener
   *
   * @param listener Previously added listener
   */
  public void removeRefreshListener(TableCellRefreshListener listener);

  /** Adds a listener that triggers when the TableCell has been disposed
   *
   * @param listener listener object to be called
   */
  public void addDisposeListener(TableCellDisposeListener listener);
  /** Remove a previously added TableCellDisposeListener
   *
   * @param listener Previously added listener
   */
  public void removeDisposeListener(TableCellDisposeListener listener);

  /** Adds a listener related to tooltip actions
   *
   * @param listener listener object to be called
   */
  public void addToolTipListener(TableCellToolTipListener listener);
  /** Remove a previously added TableCellToolTipListener
   *
   * @param listener Previously added listener
   */
  public void removeToolTipListener(TableCellToolTipListener listener);

  /**
   * Adds a listener that triggers when a TableCell that belongs to this column
   * has a mouse event.
   *
   * @param listener
   *
   * @since 2.3.0.7
   */
  public void addMouseListener(TableCellMouseListener listener);
  /** Remove a previously added TableCellMouseListener
  *
  * @param listener Previously added listener
   * @since 2.3.0.7
  */
  public void removeMouseListener(TableCellMouseListener listener);

  public void addMenuListener(TableCellMenuListener listener);
  
  public void removeMenuListener(TableCellMenuListener listener);
  
  /**
   * A listener is added for every type of cell listener the supplied object
   * implements
   *
   * @param listenerObject Object implementing some cell listeners
   */
  public void addListeners(Object listenerObject);

  /**
   * Returns a Graphic of what's behind the cell
   *
   * @return
   *
   * @since 3.0.3.5
   */
  public Graphic getBackgroundGraphic();

	/**
	 * Return the position of the mouse relative to the cell.
	 *
	 * @return array of 2 containing x and y position position relative to cell.
	 * 				null if cell doesn't have mouse.
	 *
	 * @since 3.0.4.3
	 */
	public int[] getMouseOffset();

	/**
	 * Returns text that's meant for the clipboard
	 *
	 * @since 4.3.1.5
	 */
	public String getClipboardText();

	/**
	 * When true, cell is part of a multi-column sort, and you should set 
	 * the cell's sort value based solely on your column's data.
	 * <p/>
	 * Common scenario:<br/>
	 * User sorts just by your column, you may want to sort by the main value,
	 * and subsort by several secondary values.
	 * When user sorts by multiple columns (including yours), your secondary 
	 * values can prevent other sort columns from being applied.
	 * <p/>
	 * Example:<br/> 
	 * When sorted by itself, the "unopened" column would look better if sorted
	 * by last opened date. However, if 
	 * useSimpleSortValue is not taken into account, and the user sorts by 
	 * unopened + name, the order would not necessarily change. The end
	 * result would be a sort by unopened, last opened, and then
	 * 'bytes downloaded'.
	 */
	boolean useSimpleSortValue();
	
	public void setData( Object key, Object data );
	
	public Object getData( Object key );
}
