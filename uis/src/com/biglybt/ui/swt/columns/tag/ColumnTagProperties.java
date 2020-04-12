/*
 * File    : CategoryItem.java
 * Created : 01 feb. 2004
 * By      : TuxPaper
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

package com.biglybt.ui.swt.columns.tag;

import java.util.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnExtraInfoListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureExecOnAssign;
import com.biglybt.core.tag.TagFeatureProperties;
import com.biglybt.core.tag.TagType;


public class
ColumnTagProperties
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "tag.properties";

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info )
	{
		info.addCategories (new String[]{ TableColumn.CAT_SETTINGS });

		info.setProficiency( TableColumnInfo.PROFICIENCY_INTERMEDIATE );
	}

	public
	ColumnTagProperties(
		TableColumn column )
	{
		column.setWidth(160);
		column.setRefreshInterval(TableColumn.INTERVAL_LIVE);
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell)
	{
		Tag tag = (Tag)cell.getDataSource();

		String text = "";

		if ( tag instanceof TagFeatureProperties ){

			TagFeatureProperties tp = (TagFeatureProperties)tag;

			TagFeatureProperties.TagProperty[] props = tp.getSupportedProperties();

			if ( props.length > 0 ){

				for ( TagFeatureProperties.TagProperty prop: props ){

					String prop_str = prop.getString();

					if ( prop_str.length() > 0 ){

						text += (text.length()==0?"":"; ") + prop_str;
					}
				}
			}
		}

		if ( tag instanceof TagFeatureExecOnAssign ){

			TagFeatureExecOnAssign eoa = (TagFeatureExecOnAssign)tag;

			int	actions = eoa.getSupportedActions();

			if ( actions != TagFeatureExecOnAssign.ACTION_NONE ){

				String actions_str = "";

				boolean is_peer_set = tag.getTagType().getTagType() == TagType.TT_PEER_IPSET;
				
				int[]	action_ids =
					{	TagFeatureExecOnAssign.ACTION_APPLY_OPTIONS_TEMPLATE,
					 	TagFeatureExecOnAssign.ACTION_DESTROY,
						TagFeatureExecOnAssign.ACTION_START,
						TagFeatureExecOnAssign.ACTION_FORCE_START,
						TagFeatureExecOnAssign.ACTION_NOT_FORCE_START,
						TagFeatureExecOnAssign.ACTION_STOP,
						TagFeatureExecOnAssign.ACTION_SCRIPT,
						TagFeatureExecOnAssign.ACTION_PAUSE,
						TagFeatureExecOnAssign.ACTION_RESUME,
						TagFeatureExecOnAssign.ACTION_POST_MAGNET_URI,
						TagFeatureExecOnAssign.ACTION_MOVE_INIT_SAVE_LOC,
						TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS,
						TagFeatureExecOnAssign.ACTION_HOST,
						TagFeatureExecOnAssign.ACTION_PUBLISH };

				String[] action_keys =
					{ 	"label.apply.options.template",
						is_peer_set?"azbuddy.ui.menu.disconnect":"v3.MainWindow.button.delete",
						"v3.MainWindow.button.start",
						"v3.MainWindow.button.forcestart",
						"v3.MainWindow.button.notforcestart",
						"v3.MainWindow.button.stop",
						"label.script",
						"v3.MainWindow.button.pause",
						"v3.MainWindow.button.resume",
						"label.post.magnet.to.chat",
						"label.init.save.loc.move",
						"label.assign.tags",
						"menu.host.on.tracker",
						"menu.publish.on.tracker"};

				for ( int i=0; i<action_ids.length;i++ ){

					int	action_id = action_ids[i];

					if ( eoa.supportsAction( action_id)){

						boolean enabled = eoa.isActionEnabled( action_id );

						if ( enabled ){

							if ( action_id == TagFeatureExecOnAssign.ACTION_SCRIPT ){

								String script = eoa.getActionScript();

								if ( script.length() > 63 ){
									script = script.substring( 0, 60 ) + "...";
								}

								actions_str += (actions_str.length()==0?"":",") +
										MessageText.getString( action_keys[i]) + "=" + script;
								
							}else if ( action_id == TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS ){
								
								List<Tag> tags = eoa.getTagAssigns();
								
								String tag_str = "";
								
								for ( Tag t: tags ){
									
									tag_str += (tag_str==""?"":";") + t.getTagName( true );
								}
								
								actions_str += (actions_str.length()==0?"":",") +
										MessageText.getString( action_keys[i]) + "=" + tag_str ;
								
							}else{

								actions_str += (actions_str.length()==0?"":",") +
												MessageText.getString( action_keys[i]) + "=Y";
							}
						}
					}
				}

				if ( actions_str.length() > 0 ){

					text += (text.length()==0?"":"; ") +  MessageText.getString( "label.exec.on.assign" ) + ": ";

					text += actions_str;
				}
			}
		}


		if ( !cell.setSortValue( text ) && cell.isValid()){

			return;
		}

		if ( !cell.isShown()){

			return;
		}

		cell.setText( text );
	}
}
