/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.piece;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.views.PieceDistributionView;
import com.biglybt.util.DataSourceUtils;


/**
 * @author The8472
 * @created Jun 28, 2007
 *
 */
public class MyPieceDistributionView
	extends PieceDistributionView
{
	public MyPieceDistributionView()
	{
		isMe = true;
	}

	@Override
	public void dataSourceChanged(Object newDataSource) {
		if (newDataSource instanceof Object[]
				&& ((Object[]) newDataSource).length > 0) {
			newDataSource = ((Object[]) newDataSource)[0];
		}
		if (newDataSource == null && swtView != null) {
			// can be placed in PiecesView
			UISWTView parentView = swtView.getParentView();
			if (parentView != null) {
				newDataSource = DataSourceUtils.getDM(parentView.getDataSource());
			}
		}

		if (newDataSource instanceof DownloadManager) {
			pem = ((DownloadManager) newDataSource).getPeerManager();
		} else if (newDataSource instanceof PEPiece) {
			PEPiece piece = (PEPiece) newDataSource;
			pem = piece.getManager();
		} else {
			pem = null;
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				refresh();
			}
		});
	}
}
