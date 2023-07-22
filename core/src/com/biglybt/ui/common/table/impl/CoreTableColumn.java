/*
 * File    : CoreTableColumn.java
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.common.table.impl;


import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnExtraInfoListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableManager;

/** This class  provides constructors for setting most of
 * the common column attributes and sets the column as a 'core' column.<p>
 *
 * @author TuxPaper
 */

/*
 * OK, this really shouldn't be here, we should create a CoreTableColumnSWT that implements this
 * listener logic, but I'm not sure exactly
 */


public abstract class CoreTableColumn
	extends TableColumnImpl
	implements TableColumnExtraInfoListener
{
  /** Construct a new CoreTableColumn
   * Type will be TYPE_TEXT, Update Interval will be INTERVAL_INVALID_ONLY
   * <p>
   * TableCell listeners (Added, Refresh, Dispose, ToolTip) are added based on
   * whether the class is an instance of them.
   *
   * @param sName Unique ID for column
   * @param iAlignment See {@link #getAlignment()}
   * @param iPosition See {@link TableColumn#setPosition(int)}
   * @param iWidth See {@link TableColumn#setWidth(int)}
   * @param sTableID See {@link TableManager}_TABLE*
   */
  public CoreTableColumn(String sName, int iAlignment,
                         int iPosition, int iWidth,
                         String sTableID) {
    super(sTableID, sName);
    super.initialize(iAlignment, iPosition, iWidth);
    setUseCoreDataSource(true);
    addListeners(this);
  }

  public CoreTableColumn(Class forDataSourceType, String sName, int iAlignment,
			int iWidth, String sTableID) {
		super(sTableID, sName);
		addDataSourceType(forDataSourceType);
		super.initialize(iAlignment, TableColumn.POSITION_INVISIBLE, iWidth);
		setUseCoreDataSource(true);
		addListeners(this);
	}

  /** Construct a new CoreTableColumn.<p>
   * Alignment will be ALIGN_LEAD, Type will be TYPE_TEXT,
   * Update Interval will be INTERVAL_INVALID_ONLY
   * <p>
   * TableCell listeners (Added, Refresh, Dispose, ToolTip) are added based on
   * whether the class is an instance of them.
   *
   * @param sName Unique ID for column
   * @param iPosition See {@link TableColumn#setPosition(int)}
   * @param iWidth See {@link TableColumn#setWidth(int)}
   * @param sTableID See {@link TableManager}_TABLE*
   */
  public CoreTableColumn(String sName, int iPosition, int iWidth,
                         String sTableID) {
    super(sTableID, sName);
    setPosition(iPosition);
    setWidth(iWidth);
    setUseCoreDataSource(true);
    addListeners(this);
  }

  /** Construct a new CoreTableColumn.<p>
   * Alignment will be ALIGN_LEAD, Type will be TYPE_TEXT, Position will be
   * POSITION_INVISIBLE, Update Interval will be INTERVAL_INVALID_ONLY
   * <p>
   * TableCell listeners (Added, Refresh, Dispose, ToolTip) are added based on
   * whether the class is an instance of them.
   *
   * @param sName Unique ID for column
   * @param iWidth See {@link TableColumn#setWidth(int)}
   * @param sTableID See {@link TableManager}_TABLE*
   */
  public CoreTableColumn(String sName, int iWidth, String sTableID) {
    super(sTableID, sName);
    setWidth(iWidth);
    setUseCoreDataSource(true);
    addListeners(this);
  }

  /** Construct a new CoreTableColumn.<p>
   * Alignment will be ALIGN_LEAD, Type will be TYPE_TEXT, Position will be
   * POSITION_INVISIBLE, Width will be 50, Update Interval will be
   * INTERVAL_INVALID_ONLY
   * <p>
   * TableCell listeners (Added, Refresh, Dispose, ToolTip) are added based on
   * whether the class is an instance of them.
   *
   * @param sName Unique ID for column
   * @param sTableID See {@link TableManager}_TABLE*
   */
  public CoreTableColumn(String sName, String sTableID) {
    super(sTableID, sName);
    setUseCoreDataSource(true);
    addListeners(this);
  }

  public void initializeAsGraphic(int iWidth) {
    setWidth(iWidth);
    setType(TableColumn.TYPE_GRAPHIC);
    setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
    setAlignment(TableColumn.ALIGN_CENTER);
  }


  public void initializeAsGraphic(int iPosition, int iWidth) {
    setPosition(iPosition);
    setWidth(iWidth);
    setType(TableColumn.TYPE_GRAPHIC);
    setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
    setAlignment(TableColumn.ALIGN_CENTER);
  }

  @Override
  public void fillTableColumnInfo(TableColumnInfo info) {
  }
}
