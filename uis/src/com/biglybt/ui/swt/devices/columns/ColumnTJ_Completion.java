/*
 * Created on Feb 26, 2009
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.devices.columns;

import java.text.NumberFormat;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;

import com.biglybt.core.devices.TranscodeFile;
import com.biglybt.core.devices.TranscodeJob;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.internat.MessageText.MessageTextListener;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

/**
 * @author TuxPaper
 * @created Feb 26, 2009
 *
 */
public class ColumnTJ_Completion
	implements TableCellAddedListener, TableCellRefreshListener,
	TableCellSWTPaintListener, TableColumnExtraInfoListener
{
	private static final int borderWidth = 1;

	public static final String COLUMN_ID = "trancode_completion";

	private static Font fontText;

	String	na_text;

	Color textColor;

	NumberFormat percentage_format = NumberFormat.getPercentInstance();
	{
		percentage_format.setMinimumFractionDigits(0);
		percentage_format.setMaximumFractionDigits(0);
	}

	public ColumnTJ_Completion(final TableColumn column) {
		column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 145);
		column.addListeners(this);
		column.setType(TableColumn.TYPE_GRAPHIC);
		column.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);

		MessageText.addAndFireListener(new MessageTextListener() {
			@Override
			public void localeChanged(Locale old_locale, Locale new_locale) {

				na_text = MessageText.getString( "general.na.short" );

				column.invalidateCells();
			}
		});
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void cellAdded(TableCell cell) {
		cell.setMarginHeight(2);
	}

	@Override
	public void refresh(TableCell cell) {
		TranscodeFile tf =  (TranscodeFile) cell.getDataSource();

		int percentDone = getPerThouDone(tf);

		cell.setSortValue(percentDone);
	}

	@Override
	public void cellPaint(GC gcImage, TableCellSWT cell) {
		TranscodeFile tf =  (TranscodeFile) cell.getDataSource();

		int perThouDone = getPerThouDone(tf);

		Rectangle bounds = cell.getBounds();

		int yOfs = (bounds.height - 13) / 2 ;
		int x1 = bounds.width - borderWidth - 2;
		int y1 = bounds.height - 3 - yOfs;

		if (x1 < 10 || y1 < 3) {
			return;
		}

		ImageLoader imageLoader = ImageLoader.getInstance();
		Image imgEnd = imageLoader.getImage("tc_bar_end");
		Image img0 = imageLoader.getImage("tc_bar_0");
		Image img1 = imageLoader.getImage("tc_bar_1");

		//draw beginning and end
		if (!imgEnd.isDisposed()) {
			gcImage.drawImage(imgEnd, bounds.x , bounds.y + yOfs);
			gcImage.drawImage(imgEnd, bounds.x + x1 + 1, bounds.y + yOfs);
		}



		int limit = (x1 * perThouDone) / 1000;

		if (!img1.isDisposed() && limit > 0) {
			Rectangle imgBounds = img1.getBounds();
			gcImage.drawImage(img1, 0, 0, imgBounds.width, imgBounds.height,
					bounds.x + 1, bounds.y + yOfs, limit, imgBounds.height);
		}
		if (perThouDone < 1000 && !img0.isDisposed()) {
			Rectangle imgBounds = img0.getBounds();
			gcImage.drawImage(img0, 0, 0, imgBounds.width, imgBounds.height, bounds.x
					+ limit + 1, bounds.y + yOfs, x1 - limit, imgBounds.height);
		}

		imageLoader.releaseImage("tc_bar_end");
		imageLoader.releaseImage("tc_bar_0");
		imageLoader.releaseImage("tc_bar_1");

		if(textColor == null) {
			textColor = ColorCache.getColor(gcImage.getDevice(), "#006600" );
		}

		gcImage.setForeground(textColor);

		if (fontText == null) {
			fontText = FontUtils.getFontWithHeight(gcImage.getFont(), 10, SWT.DEFAULT);
		}

		gcImage.setFont(fontText);

		String sText;

		if ( tf != null && perThouDone == 1000 && !tf.getTranscodeRequired()){

			sText = na_text;

		}else{

			sText = percentage_format.format( perThouDone/1000.0 ); // DisplayFormatters.formatPercentFromThousands(perThouDone);

			if ( tf != null && perThouDone < 1000 ){

				String eta = getETA(tf);

				if ( eta != null ){

					sText += " - " + eta;
				}
			}
		}

		GCStringPrinter.printString(gcImage, sText, new Rectangle(bounds.x + 4,
				bounds.y + yOfs, bounds.width - 4,13), true,
				false, SWT.CENTER);
	}

	private int getPerThouDone(TranscodeFile tf) {
		if (tf == null) {
			return 0;
		}
		TranscodeJob job = tf.getJob();
		if (job == null) {
			return tf.isComplete()?1000:0;
		}
		return job.getPercentComplete()*10;
	}

	private String getETA(TranscodeFile tf) {
		if (tf == null) {
			return null;
		}
		TranscodeJob job = tf.getJob();
		if (job == null) {
			return null;
		}
		return job.getETA();
	}
}
