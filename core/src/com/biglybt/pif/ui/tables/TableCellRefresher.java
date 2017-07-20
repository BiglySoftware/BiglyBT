/*
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

package com.biglybt.pif.ui.tables;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AEThread2;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.impl.TableColumnImpl;


/**
 *
 * Provides a simple way to get a TableCell refreshed more often than the normal GUI refresh cycle
 * It always clocks at 100ms
 * as well as time synchronization methods for cells showing animated icons
 * @author olivier
 *
 */
public class TableCellRefresher {

	private static TableCellRefresher instance = null;

	private  AEThread2 refresher;

	private Map<TableCell, TableColumn> mapCellsToColumn = new HashMap<>();

	private  long iterationNumber;

	private volatile boolean inProgress = false;

	private AERunnable runnable;

	private TableCellRefresher() {
		runnable = new AERunnable() {
			@Override
			public void runSupport() {
				try {
					Map<TableCell, TableColumn> cellsCopy;
  				synchronized (mapCellsToColumn) {
  					cellsCopy = new HashMap<>(mapCellsToColumn);
  					mapCellsToColumn.clear();
  				}

  				for (TableCell cell : cellsCopy.keySet()) {
  					TableColumn column = (TableColumn) cellsCopy.get(cell);

  					try {
  						//cc.cell.invalidate();
  						if (column instanceof TableCellRefreshListener) {
  							((TableCellRefreshListener) column).refresh(cell);
  						}else if ( column instanceof TableColumnImpl ){
  							List<TableCellRefreshListener> listeners =((TableColumnImpl)column).getCellRefreshListeners();
  							for ( TableCellRefreshListener listener: listeners ){
  								listener.refresh(cell);
  							}
  						}

  					} catch (Throwable t) {
  						t.printStackTrace();
  					}
  				}
				} finally {
					inProgress = false;
				}
			}
		};

		refresher = new AEThread2("Cell Refresher",true) {
			@Override
			public void run() {
				try {

					iterationNumber = 0;

					UIFunctions uif = UIFunctionsManager.getUIFunctions();

					while (true) {

						if ( uif != null ){

							int size;

							synchronized (mapCellsToColumn){

								size = mapCellsToColumn.size();
							}

							if ( size > 0 && !inProgress ){

								inProgress = true;

									// this whole class shouldn't be here, but as it is and
									// it is used by plugins then keep it around

								uif.runOnUIThread( UIInstance.UIT_SWT, runnable );
							}
						}

						Thread.sleep(100);

						iterationNumber++;
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		refresher.start();
	}


	private void _addColumnCell(TableColumn column,TableCell cell) {
		synchronized (mapCellsToColumn) {
			if (mapCellsToColumn.containsKey(cell)) {
				return;
			}
			mapCellsToColumn.put(cell, column);
		}
	}

	private int _getRefreshIndex(int refreshEvery100ms, int nbIndices) {
		if(refreshEvery100ms <= 0) return 1;
		if(nbIndices <= 0) return 1;

		return (int) ( (iterationNumber / refreshEvery100ms) % nbIndices);
	}

	private static synchronized TableCellRefresher getInstance() {
		if(instance == null) {
			instance = new TableCellRefresher();
		}
		return instance;
	}

	//Add a cell to be refreshed within the next iteration
	//The cell will only get refreshed once
	public static void addCell(TableColumn column,TableCell cell) {

		getInstance()._addColumnCell(column,cell);
	}



	public static int getRefreshIndex(int refreshEvery100ms, int nbIndices) {
		return getInstance()._getRefreshIndex(refreshEvery100ms, nbIndices);
	}

}
