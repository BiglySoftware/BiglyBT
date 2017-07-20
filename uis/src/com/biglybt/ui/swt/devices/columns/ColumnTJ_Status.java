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

import java.util.Locale;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.internat.MessageText.MessageTextListener;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TimeFormatter;

import com.biglybt.core.devices.TranscodeFile;
import com.biglybt.core.devices.TranscodeJob;

import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.Utils;

/**
 * @author TuxPaper
 * @created Feb 26, 2009
 *
 */
public class ColumnTJ_Status
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "transcode_status";

	private static final String[] js_resource_keys = {
		"ManagerItem.queued",
		"devices.converting",
		"ManagerItem.paused",
		"sidebar.LibraryCD",
		"Progress.reporting.status.canceled",
		"ManagerItem.error",		// 5
		"ManagerItem.stopped",
		"devices.copy.fail",		// 7
		"devices.on.demand",		// 8
		"devices.ready",			// 9
		"devices.downloading",		// 10
		"devices.copying", // 11
	};

	private static String[] js_resources;

	private static String	eta_text;

	public ColumnTJ_Status(final TableColumn column) {
		column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 160);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
		column.setType(TableColumn.TYPE_TEXT_ONLY);

		MessageText.addAndFireListener(new MessageTextListener() {
			@Override
			public void localeChanged(Locale old_locale, Locale new_locale) {
				js_resources = new String[js_resource_keys.length];

				for (int i = 0; i < js_resources.length; i++) {
					js_resources[i] = MessageText.getString(js_resource_keys[i]);
				}

				eta_text = MessageText.getString( "TableColumn.header.eta" );

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


	// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void refresh(TableCell cell) {
		TranscodeFile tf = (TranscodeFile) cell.getDataSource();
		if (tf == null || tf.isDeleted()){
			return;
		}
		TranscodeJob job = tf.getJob();

		String 	tooltip = null;
		String	text	= null;
		boolean	error	= false;

		if ( job == null ){

			try{
				if ( tf.isComplete() && !tf.getTargetFile().getFile(true).exists()){

					tooltip = "File '" + tf.getTargetFile().getFile().getAbsolutePath() + "' not found";

					text = js_resources[5] + ": File not found";

					error = true;
				}
			}catch( Throwable e ){
			}

			if ( text == null ){

				if ( tf.isCopyingToDevice() ) {

					text = js_resources[11];

				} else if ( tf.getCopyToDeviceFails() > 0 ){

					text = js_resources[7];

					error = true;

				}else if ( tf.isTemplate() && !tf.isComplete()){

					text = js_resources[8];

				}else{

					text = js_resources[9];
				}
			}
		}else{

			int state = job.getState();

			text = js_resources[state];

			if ( state == TranscodeJob.ST_QUEUED ){

				long eta = job.getDownloadETA();

				if ( eta > 0 ){

					text = js_resources[10] + ": " + eta_text + " " + ( eta==Long.MAX_VALUE?Constants.INFINITY_STRING:TimeFormatter.format( eta ));
				}
			}else{

				text = js_resources[state];

				if ( state == TranscodeJob.ST_FAILED ) {

					String	error_msg = job.getError();

					if ( error_msg != null ){

							// error message can be very large and technical as it includes output
							// from ffmpeg error etc. So trim it back for user consumption.
							// currently we try to ensure that tech info appears after second
							// comma

						try{
							int	pos = error_msg.indexOf( '\n' );

							if ( pos >= 0 ){

								error_msg = error_msg.substring( 0, pos );
							}

							pos = error_msg.indexOf( ',' );

							if ( pos >= 0 ){

								pos = error_msg.indexOf( ',', pos+1 );

								if ( pos >= 0 ){

									error_msg = error_msg.substring( 0, pos );
								}
							}

							text += ": " + error_msg.trim();

						}catch( Throwable e ){
						}
					}

					tooltip = "See transcode log for more details";

					error = true;
				}
			}
		}



		cell.setText( text );
		cell.setToolTip(tooltip);

		if ( error){

			cell.setForegroundToErrorColor();

		}else{

			cell.setForeground(Utils.colorToIntArray(null));
		}
	}
}
